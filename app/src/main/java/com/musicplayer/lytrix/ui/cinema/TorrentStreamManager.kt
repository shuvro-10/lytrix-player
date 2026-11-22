package com.musicplayer.lytrix.ui.cinema

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

sealed class TorrentPlaybackState {
    object Idle : TorrentPlaybackState()
    data class Buffering(
        val streamTitle: String,
        val bufferPercent: Int,
        val seeds: Int,              // NOTE: now holds connected PEER count
        val speedBytesPerSec: Int,
        val statusMessage: String
    ) : TorrentPlaybackState()

    /** [streamUrl] is a local http://127.0.0.1 URL served by [TorrentHttpServer]. */
    data class Ready(
        val streamUrl: String,
        val streamTitle: String,
        val external: Boolean = false
    ) : TorrentPlaybackState()

    data class Error(val message: String) : TorrentPlaybackState()
}

/**
 * Torrent streaming engine.
 *
 * Design rules (these are what fix the native SIGSEGV crashes):
 *  1. ONE long-lived SessionManager. It is never stopped/aborted while the app runs;
 *     we only add/remove individual torrents.
 *  2. EVERY engine mutation (start, stop, remove) runs on ONE dedicated thread, so
 *     stop/start can never overlap.
 *  3. Teardown order is always: flag streams closed -> stop HTTP server -> remove torrent.
 *     ExoPlayer is released BEFORE this (see CinemaPlayerView patch).
 *  4. ExoPlayer never touches the sparse file. It talks HTTP Range to [TorrentHttpServer],
 *     which blocks until the needed pieces are really downloaded.
 */
object TorrentStreamManager {

    private const val METADATA_TIMEOUT_MS = 120_000L
    private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts")
    private val HASH_REGEX = Regex("btih:([0-9a-fA-F]{40})")

    private val engineExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "torrent-engine").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + engineExecutor.asCoroutineDispatcher())

    private val _playbackState = MutableStateFlow<TorrentPlaybackState>(TorrentPlaybackState.Idle)
    val playbackState: StateFlow<TorrentPlaybackState> = _playbackState.asStateFlow()

    /** Used only to make stopStream() idempotent from any thread. */
    @Volatile
    private var active = false

    // ---- touched ONLY on the engine thread --------------------------------
    private var session: SessionManager? = null
    private var runJob: Job? = null
    private var handle: TorrentHandle? = null
    private var source: TorrentSource? = null
    private var server: TorrentHttpServer? = null
    // -----------------------------------------------------------------------

    /**
     * @param fileIdx index from Torrentio (-1 = unknown -> largest video file is used)
     * @param external true = once ready, CinemaPlayerView opens the URL in VLC/MX instead of ExoPlayer
     */
    fun startStream(
        context: Context,
        magnetUri: String,
        title: String,
        fileIdx: Int = -1,
        external: Boolean = false
    ) {
        val appContext = context.applicationContext
        active = true
        _playbackState.value = TorrentPlaybackState.Buffering(
            streamTitle = title,
            bufferPercent = 0,
            seeds = 0,
            speedBytesPerSec = 0,
            statusMessage = "Starting torrent engine..."
        )
        scope.launch {
            stopInternal()
            CinemaEngineManager.ensureNativeLoaded(appContext)
            runJob = launch { runStream(appContext, magnetUri, title, fileIdx, external) }
        }
    }

    /** Safe to call any number of times, from any thread (UI included). */
    fun stopStream() {
        if (!active) return
        active = false
        _playbackState.value = TorrentPlaybackState.Idle
        scope.launch { stopInternal() }
    }

    // ----------------------------------------------------------------------

    private fun ensureSession(): SessionManager = TorrentEngine.getSession()

    private suspend fun stopInternal() {
        runJob?.cancelAndJoin()
        runJob = null
        releaseResources()
    }

    private suspend fun releaseResources() = withContext(NonCancellable) {
        // 1) make every blocked reader bail out
        source?.close()
        source = null
        // 2) stop accepting / drop connections
        val srv = server
        server = null
        runCatching { srv?.stop() }
        // 3) only now remove the torrent (and its files) from the session
        val h = handle
        handle = null
        if (h != null) {
            delay(300) // let reader threads leave their JNI calls
            runCatching {
                if (h.isValid) TorrentEngine.getSession().remove(h, SessionHandle.DELETE_FILES)
            }
        }
    }

    private suspend fun runStream(
        ctx: Context,
        magnet: String,
        title: String,
        fileIdx: Int,
        external: Boolean
    ) {
        try {
            val sm = ensureSession()
            val hashHex = HASH_REGEX.find(magnet)?.groupValues?.get(1)
                ?: throw IllegalStateException("Unsupported magnet link (no btih hash)")
            val dir = File(ctx.externalCacheDir ?: ctx.cacheDir, "cinema_torrents").apply { mkdirs() }

            _playbackState.value = TorrentPlaybackState.Buffering(
                title, 5, 0, 0, "Connecting to P2P network..."
            )
            sm.download(magnet, dir, TorrentFlags.SEQUENTIAL_DOWNLOAD)

            // ---- 1. wait for metadata ------------------------------------
            val sha = Sha1Hash.parseHex(hashHex)
            val startedAt = System.currentTimeMillis()
            var h: TorrentHandle? = null
            var info: org.libtorrent4j.TorrentInfo? = null
            while (info == null) {
                coroutineContext.ensureActive()
                if (h == null || !h.isValid) h = sm.find(sha)
                if (h != null) {
                    handle = h
                    val st = h.status()
                    info = h.torrentFile()
                    if (info == null) {
                        _playbackState.value = TorrentPlaybackState.Buffering(
                            title, 10, st.numPeers(), st.downloadRate(),
                            "Fetching metadata... ${st.numPeers()} peers"
                        )
                    }
                }
                if (info == null) {
                    if (System.currentTimeMillis() - startedAt > METADATA_TIMEOUT_MS) {
                        throw IllegalStateException(
                            "No peers reachable (timed out fetching metadata). Try another stream or use Wi-Fi."
                        )
                    }
                    delay(500)
                }
            }
            val th = h!!

            // ---- 2. choose the video file --------------------------------
            val fs = info.files()
            val n = fs.numFiles()
            fun extOf(i: Int) = fs.filePath(i).substringAfterLast('.', "").lowercase()
            fun isVideo(i: Int) = extOf(i) in VIDEO_EXT
            val idx = if (fileIdx in 0 until n && isVideo(fileIdx)) {
                fileIdx
            } else {
                (0 until n).filter(::isVideo).maxByOrNull { fs.fileSize(it) }
                    ?: throw IllegalStateException("No video file found in this torrent")
            }

            // download ONLY that file
            val prios = Array(n) { Priority.IGNORE }
            prios[idx] = Priority.DEFAULT
            th.prioritizeFiles(prios)

            val ext = extOf(idx).ifBlank { "mkv" }
            val src = TorrentSource(
                handle = th,
                file = File(dir, fs.filePath(idx)),
                fileOffset = fs.fileOffset(idx),
                fileSize = fs.fileSize(idx),
                pieceLength = info.pieceLength()
            )
            source = src
            src.prioritize(0L, reset = true) // header + tail first

            // ---- 3. local HTTP server ------------------------------------
            val token = UUID.randomUUID().toString().replace("-", "")
            val srv = TorrentHttpServer(src, mimeFor(ext), token)
            srv.start(5_000, true)
            server = srv
            val url = "http://127.0.0.1:${srv.listeningPort}/$token/video.$ext"

            // ---- 4. buffer header + tail, then hand over to the player ---
            val need = src.initialPieces()
            while (true) {
                coroutineContext.ensureActive()
                val have = need.count { src.have(it) }
                val st = th.status()
                val pct = if (need.isEmpty()) 100 else have * 100 / need.size
                _playbackState.value = TorrentPlaybackState.Buffering(
                    streamTitle = title,
                    bufferPercent = pct.coerceIn(0, 100),
                    seeds = st.numPeers(),
                    speedBytesPerSec = st.downloadRate(),
                    statusMessage = "Buffering $pct% • ${st.numPeers()} peers"
                )
                if (have >= need.size) break
                delay(500)
            }
            _playbackState.value = TorrentPlaybackState.Ready(url, title, external)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) { // Throwable: also UnsatisfiedLinkError
            _playbackState.value = TorrentPlaybackState.Error(t.message ?: t.javaClass.simpleName)
            active = false
            releaseResources()
        }
    }

    private fun mimeFor(ext: String) = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "video/*"
    }
}
