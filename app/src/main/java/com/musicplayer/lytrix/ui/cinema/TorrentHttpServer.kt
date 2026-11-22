package com.musicplayer.lytrix.ui.cinema

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * One selected video file inside a torrent. Knows which pieces cover which bytes
 * and can ask libtorrent to fetch the pieces we need first (piece deadlines).
 */
class TorrentSource(
    private val handle: TorrentHandle,
    val file: File,
    private val fileOffset: Long,
    val fileSize: Long,
    private val pieceLength: Int
) {
    @Volatile
    var closed = false
        private set

    private val firstPiece = (fileOffset / pieceLength).toInt()
    private val lastPiece = ((fileOffset + fileSize - 1) / pieceLength).toInt()

    /** How many pieces ahead of the playhead we keep "urgent" (~16 MB worth). */
    val windowPieces: Int = (16L * 1024 * 1024 / pieceLength).toInt().coerceIn(4, 64)

    fun close() {
        closed = true
    }

    fun pieceOf(pos: Long): Int = ((fileOffset + pos) / pieceLength).toInt()

    fun have(piece: Int): Boolean = try {
        !closed && handle.havePiece(piece)
    } catch (_: Throwable) {
        false
    }

    /** Pieces needed before playback starts: first ~4 MB (headers) + last ~2 MB (moov / Cues). */
    fun initialPieces(): List<Int> {
        val headEnd = pieceOf(minOf(HEAD_BYTES, fileSize) - 1).coerceAtMost(lastPiece)
        val tailStart = pieceOf(maxOf(0L, fileSize - TAIL_BYTES)).coerceAtLeast(firstPiece)
        return ((firstPiece..headEnd) + (tailStart..lastPiece)).distinct().sorted()
    }

    /** File offset (exclusive) up to which contiguous data starting at [pos] is on disk. */
    fun availableUntil(pos: Long): Long {
        var q = pieceOf(pos)
        var scanned = 0
        while (q <= lastPiece && scanned < MAX_SCAN && have(q)) {
            q++
            scanned++
        }
        val endTorrent = minOf(q.toLong() * pieceLength, fileOffset + fileSize)
        return endTorrent - fileOffset
    }

    /**
     * Tell libtorrent which pieces are urgent. [reset] = true on a fresh request / seek:
     * old deadlines are dropped and header + tail pieces are re-queued first.
     */
    fun prioritize(pos: Long, reset: Boolean) {
        if (closed) return
        try {
            if (reset) handle.clearPieceDeadlines()
            val p = pieceOf(pos)
            var rank = 0
            for (i in 0 until windowPieces) {
                val q = p + i
                if (q > lastPiece) break
                if (!handle.havePiece(q)) handle.setPieceDeadline(q, 300 + rank++ * 250)
            }
            if (reset) {
                var k = 0
                for (q in initialPieces()) {
                    if (!handle.havePiece(q)) handle.setPieceDeadline(q, 100 + k++ * 50)
                }
            }
        } catch (_: Throwable) {
            // handle may be gone during teardown
        }
    }

    companion object {
        const val HEAD_BYTES = 4L * 1024 * 1024
        const val TAIL_BYTES = 2L * 1024 * 1024
        private const val MAX_SCAN = 64
    }
}

/**
 * Blocking InputStream over [start, endInclusive] of the torrent file.
 * read() only returns bytes whose pieces libtorrent has fully downloaded and verified,
 * so the player never sees zero-filled holes.
 */
class TorrentRangeStream(
    private val src: TorrentSource,
    start: Long,
    private val endInclusive: Long
) : InputStream() {

    private var pos = start
    private var knownUntil = start
    private var windowEndPiece: Int
    private var raf: RandomAccessFile? = null

    @Volatile
    private var closedByUs = false

    init {
        src.prioritize(start, reset = true)
        windowEndPiece = src.pieceOf(start) + src.windowPieces
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos > endInclusive) return -1
        awaitData()
        val n = minOf(len.toLong(), knownUntil - pos, endInclusive - pos + 1).toInt()
        val f = raf ?: RandomAccessFile(src.file, "r").also { raf = it }
        val read = f.channel.read(ByteBuffer.wrap(b, off, n), pos)
        if (read <= 0) throw IOException("Unexpected end of torrent file at $pos")
        pos += read
        return read
    }

    private fun awaitData() {
        // keep the urgent window rolling ahead of the playhead
        val piece = src.pieceOf(pos)
        if (piece + src.windowPieces / 2 >= windowEndPiece) {
            src.prioritize(pos, reset = false)
            windowEndPiece = piece + src.windowPieces
        }
        if (pos < knownUntil) return

        val t0 = System.nanoTime()
        while (true) {
            if (src.closed || closedByUs) throw IOException("Stream closed")
            if (src.have(piece)) {
                knownUntil = src.availableUntil(pos)
                if (knownUntil > pos) return
            }
            if ((System.nanoTime() - t0) / 1_000_000 > WAIT_TIMEOUT_MS) {
                throw IOException("Timed out waiting for torrent piece $piece")
            }
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        }
    }

    override fun available(): Int = (knownUntil - pos).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    override fun close() {
        closedByUs = true
        try {
            raf?.close()
        } catch (_: IOException) {
        }
        raf = null
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 100_000L
    }
}

/**
 * Loopback-only HTTP server with Range support (what ExoPlayer / VLC / MX Player expect).
 * The random [token] in the path stops other apps on the phone from reading the stream.
 */
class TorrentHttpServer(
    private val src: TorrentSource,
    private val mime: String,
    private val token: String
) : NanoHTTPD("127.0.0.1", 0) {

    override fun serve(session: IHTTPSession): Response {
        if (!session.uri.startsWith("/$token/")) {
            return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        val size = src.fileSize
        val rangeHeader = session.headers["range"]

        var start = 0L
        var end = size - 1
        val partial = rangeHeader != null && rangeHeader.startsWith("bytes=")
        if (partial) {
            val r = parseRange(rangeHeader, size)
            if (r == null) {
                val resp = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                resp.addHeader("Content-Range", "bytes */$size")
                return resp
            }
            start = r.first
            end = r.second
        }

        val stream = TorrentRangeStream(src, start, end)
        val resp = newFixedLengthResponse(
            if (partial) Status.PARTIAL_CONTENT else Status.OK,
            mime,
            stream,
            end - start + 1
        )
        resp.addHeader("Accept-Ranges", "bytes")
        resp.addHeader("Cache-Control", "no-store")
        if (partial) resp.addHeader("Content-Range", "bytes $start-$end/$size")
        return resp
    }

    /** Supports "bytes=a-b", "bytes=a-" and "bytes=-n". Returns null if unsatisfiable. */
    private fun parseRange(header: String, size: Long): Pair<Long, Long>? {
        val spec = header.removePrefix("bytes=").substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val a = spec.substring(0, dash)
        val b = spec.substring(dash + 1)
        return try {
            val s: Long
            val e: Long
            if (a.isEmpty()) {
                val n = b.toLong()
                if (n <= 0) return null
                s = maxOf(0L, size - n)
                e = size - 1
            } else {
                s = a.toLong()
                e = if (b.isEmpty()) size - 1 else minOf(b.toLong(), size - 1)
            }
            if (s < 0 || s >= size || e < s) null else s to e
        } catch (_: NumberFormatException) {
            null
        }
    }
}
