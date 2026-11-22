package com.musicplayer.lytrix.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.util.Log
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.provider.MediaStore
import java.io.FileInputStream
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.audio.mp4.Mp4FileReader
import org.jaudiotagger.audio.mp3.MP3FileReader
import org.jaudiotagger.tag.images.ArtworkFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.musicplayer.lytrix.utils.InnerTubeXPlayer
import com.musicplayer.lytrix.utils.YTPlayerUtils
import com.musicplayer.lytrix.constants.AudioQuality
import com.musicplayer.lytrix.ui.PlayerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.nio.ByteBuffer

// 🔥 ৩টি ক্যাটাগরি এবং স্ট্যাটাস মডেল
enum class DownloadSource { YOUTUBE, JIOSAAVN, OTHERS }
enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val providedUrl: String = "",
    val source: DownloadSource,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val progress: Int = 0,
    val downloadedMB: String = "0.00",
    val totalMB: String = "0.00",
    val errorMessage: String? = null
)

object OkHttpDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private var isReceiverRegistered = false

    // 🔥 লাইভ ডাউনলোড লিস্ট (মেমোরিতে থাকবে, স্ক্রিনে রিয়েল-টাইম দেখাবে)
    private val _downloadTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val downloadTasks = _downloadTasks.asStateFlow()

    fun deleteSongFiles(context: Context, title: String) {
        val safeName = title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return
        val mp3 = File(dir, "$safeName.mp3")
        val cover = File(dir, "${safeName}_cover.jpg")
        if (mp3.exists()) mp3.delete()
        if (cover.exists()) cover.delete()
    }

    // 🔥 স্ক্রিন থেকে ক্যানসেল করার ফাংশন (নোটিফিকেশন ও সব ফাইল সহ মুছে যাবে)
    fun cancelDownload(songId: String, context: Context? = null) {
        val task = _downloadTasks.value[songId]
        activeDownloads[songId]?.cancel()
        activeDownloads.remove(songId)
        if (context != null) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(songId.hashCode())
                task?.let { deleteSongFiles(context, it.title) }
            } catch (e: Exception) {}
        }
        _downloadTasks.update { it.filterKeys { k -> k != songId } }
    }

    private val downloadSlots = Semaphore(2)
    private val activeCount = AtomicInteger(0)
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    private const val CHUNK_SIZE = 1024L * 1024L
    private const val PARALLEL_CONNECTIONS = 3 // 🔥 ৩টি অপটিমাইজড কানেকশন

    private fun getTotalSize(url: String): Long {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code != 206) throw java.io.IOException("Range not supported, HTTP ${r.code}")
            return r.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
                ?: throw java.io.IOException("Unknown file size")
        }
    }

    private data class ResolvedStream(val url: String, val mime: String)

    private suspend fun resolveYoutubeStreamUrl(
        context: Context, videoId: String, tier: Int, wantM4a: Boolean
    ): ResolvedStream? {
        return try {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            when (tier) {
                1 -> {
                    if (connMgr == null) null else {
                        val d = InnerTubeXPlayer.playerResponseForPlayback(
                            videoId = videoId,
                            audioQuality = AudioQuality.HIGH,
                            connectivityManager = connMgr
                        ).getOrNull()
                        d?.takeIf { it.streamUrl.startsWith("http") }
                            ?.let { ResolvedStream(it.streamUrl, it.format.mimeType) }
                    }
                }
                2 -> {
                    val extractor = org.schabi.newpipe.extractor.ServiceList.YouTube
                        .getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                    extractor.fetchPage()
                    val streams = extractor.audioStreams.filter {
                        !wantM4a || it.format?.mimeType == "audio/mp4"
                    }
                    val best = streams.maxByOrNull { it.bitrate }
                    val link = best?.content
                    if (best != null && !link.isNullOrBlank() && link.startsWith("http"))
                        ResolvedStream(link, best.format?.mimeType ?: "") else null
                }
                3 -> {
                    if (connMgr == null) null else {
                        val d = YTPlayerUtils.playerResponseForPlayback(
                            videoId = videoId,
                            playlistId = null,
                            audioQuality = AudioQuality.HIGH,
                            connectivityManager = connMgr,
                            preferM4a = wantM4a
                        ).getOrNull()
                        d?.takeIf { it.streamUrl.startsWith("http") }
                            ?.let { ResolvedStream(it.streamUrl, it.format.mimeType) }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w("OkHttpDownloader", "Extractor tier $tier failed: ${e.message}")
            null
        }
    }

    private suspend fun downloadRange(
        urlRef: AtomicReference<String>,
        start: Long,
        end: Long,
        raf: RandomAccessFile,
        downloaded: AtomicLong,
        refreshUrlOn403: suspend (failedUrl: String) -> String?
    ) {
        var attempt = 0
        while (attempt < 4) {
            currentCoroutineContext().ensureActive()
            var got = 0L
            val currentUrl = urlRef.get()
            try {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=$start-$end")
                    .build()
                client.newCall(req).execute().use { resp ->
                    // 🔥 ৪০৩ আসলে তাৎক্ষণিক লিংক রিফ্রেশ করে নতুন লিংক দিয়ে চেষ্টা
                    if (resp.code == 403 || resp.code == 410) {
                        val freshUrl = refreshUrlOn403(currentUrl)
                        if (!freshUrl.isNullOrBlank()) {
                            attempt++
                            return@use
                        }
                    }
                    if (resp.code != 206) throw java.io.IOException("Bad code ${resp.code}")
                    val body = resp.body ?: throw java.io.IOException("Null body")
                    val buffer = ByteArray(32 * 1024)
                    body.byteStream().use { stream ->
                        var offset = start
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = stream.read(buffer)
                            if (read < 0) break
                            synchronized(raf) {
                                raf.seek(offset)
                                raf.write(buffer, 0, read)
                            }
                            offset += read
                            got += read
                            downloaded.addAndGet(read.toLong())
                        }
                    }
                }
                if (got != end - start + 1) throw java.io.IOException("Short chunk")
                return
            } catch (e: Exception) {
                downloaded.addAndGet(-got)
                if (e is CancellationException) throw e
                attempt++
                if (attempt >= 4) throw e
            }
        }
    }

    private fun registerReceiver(context: Context) {
        if (isReceiverRegistered) return
        val appContext = context.applicationContext
        val filter = IntentFilter("com.musicplayer.lytrix.CANCEL_DOWNLOAD")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val songId = intent?.getStringExtra("songId") ?: return
                val notificationId = intent.getIntExtra("notificationId", 0)

                cancelDownload(songId, ctx)

                if (notificationId != 0 && ctx != null) {
                    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(notificationId)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        isReceiverRegistered = true
    }

    fun startDownload(
        context: Context,
        songId: String,
        title: String,
        artist: String,
        imageUrl: String,
        providedUrl: String = "",
        source: DownloadSource = when {
            providedUrl.contains("saavn", ignoreCase = true) || imageUrl.contains("saavn", ignoreCase = true) -> DownloadSource.JIOSAAVN
            providedUrl.isNotBlank() && !providedUrl.contains("youtube") && !providedUrl.contains("googlevideo") -> DownloadSource.OTHERS
            else -> DownloadSource.YOUTUBE
        }
    ) {
        val fixedId = when {
            songId.isNotBlank() -> songId
            imageUrl.contains("ytimg.com/vi/") -> imageUrl.substringAfter("vi/").substringBefore("/")
            else -> ""
        }
        if (fixedId.isBlank()) {
            android.widget.Toast.makeText(context, "Song ID missing, cannot download", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        startDownloadInternal(context, fixedId, title, artist, imageUrl, providedUrl, source)
    }

    private fun startDownloadInternal(
        context: Context,
        songId: String,
        title: String,
        artist: String,
        imageUrl: String,
        providedUrl: String = "",
        source: DownloadSource = DownloadSource.YOUTUBE
    ) {
        registerReceiver(context)

        if (activeDownloads.containsKey(songId)) return

        val safeName = title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return
        val destFile = File(destDir, "$safeName.mp3")
        val coverFile = File(destDir, "${safeName}_cover.jpg")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lytrix_downloads_okhttp"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = songId.hashCode()

        val cancelIntent = Intent("com.musicplayer.lytrix.CANCEL_DOWNLOAD").apply {
            putExtra("songId", songId)
            putExtra("notificationId", notificationId)
        }
        val pendingCancelIntent = PendingIntent.getBroadcast(
            context, notificationId, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Preparing to download...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancelIntent)

        notificationManager.notify(notificationId, builder.build())

        // 🔥 লাইভ স্ক্রিনের তালিকায় গানটি যোগ করা
        _downloadTasks.update { map ->
            map + (songId to DownloadTask(
                id = songId,
                title = title,
                artist = artist,
                imageUrl = imageUrl,
                providedUrl = providedUrl,
                source = source,
                status = DownloadStatus.DOWNLOADING
            ))
        }

        activeCount.incrementAndGet()
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: Exception) { Log.e("OkHttpDownloader", "Service start failed", e) }

        var hasSlot = false
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadSlots.acquire()
                hasSlot = true
                var finalUrl = providedUrl
                val currentTier = AtomicInteger(1)
                var chosenMime = ""

                // 🔥 আগে M4A (AAC) খোঁজো, না পেলে যেকোনো ফরম্যাট
                if (finalUrl.isBlank() || !finalUrl.startsWith("http")) {
                    val passes = listOf(true to listOf(3, 2), false to listOf(1, 2, 3))
                    outer@ for ((wantM4a, tiers) in passes) {
                        for (tier in tiers) {
                            val cand = resolveYoutubeStreamUrl(context, songId, tier, wantM4a) ?: continue
                            if (wantM4a && !cand.mime.startsWith("audio/mp4")) continue
                            try {
                                if (getTotalSize(cand.url) > 0) {
                                    finalUrl = cand.url
                                    chosenMime = cand.mime
                                    currentTier.set(tier)
                                    Log.i("OkHttpDownloader", "Resolved tier=$tier mime=${cand.mime}")
                                    break@outer
                                }
                            } catch (e: Exception) {
                                Log.w("OkHttpDownloader", "Tier $tier URL check failed: ${e.message}")
                            }
                        }
                    }
                }
                val lockedMime = chosenMime.substringBefore(";").trim()

                if (finalUrl.isBlank()) {
                    builder.setProgress(0, 0, false).setContentText("Failed to resolve URL").setOngoing(false).clearActions()
                    notificationManager.notify(notificationId, builder.build())
                    _downloadTasks.update { map ->
                        map[songId]?.let { map + (songId to it.copy(status = DownloadStatus.FAILED, errorMessage = "Failed to resolve URL")) } ?: map
                    }
                    activeDownloads.remove(songId)
                    return@launch
                }

                if (destFile.exists()) destFile.delete()

                val totalSize = getTotalSize(finalUrl)
                val totalMB = String.format(Locale.US, "%.2f", totalSize / (1024f * 1024f))
                val chunkCount = ((totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                val nextChunk = AtomicInteger(0)
                val downloaded = AtomicLong(0)

                // 🔥 সাইজ জানার সাথে সাথে স্ক্রিনে আসল সাইজ আপডেট
                _downloadTasks.update { map ->
                    map[songId]?.let { map + (songId to it.copy(totalMB = totalMB)) } ?: map
                }

                RandomAccessFile(destFile, "rw").use { it.setLength(totalSize) }

                // 🔥 লাইভ ৪০৩ অটো-রিকভারি স্টেট
                val urlRef = AtomicReference(finalUrl)
                val refreshMutex = Mutex()

                val refreshUrlOn403: suspend (String) -> String? = { failedUrl ->
                    if (source != DownloadSource.YOUTUBE) {
                        null
                    } else {
                        refreshMutex.withLock {
                            if (urlRef.get() != failedUrl && urlRef.get().isNotBlank()) {
                                urlRef.get()
                            } else {
                                var freshUrl: String? = null
                                val tiers = listOf(1, 2, 3)
                                val curr = currentTier.get()
                                val nextTiers = tiers.filter { it != curr } + listOf(curr)
                                for (nextTier in nextTiers) {
                                    val cand = resolveYoutubeStreamUrl(
                                        context, songId, nextTier, lockedMime.startsWith("audio/mp4")
                                    ) ?: continue
                                    // ফরম্যাট বা সাইজ আলাদা হলে ফাইল করাপ্ট হবে, তাই বাদ
                                    if (lockedMime.isNotEmpty() &&
                                        cand.mime.substringBefore(";").trim() != lockedMime) continue
                                    val sameSize = try { getTotalSize(cand.url) == totalSize } catch (_: Exception) { false }
                                    if (!sameSize) continue
                                    freshUrl = cand.url
                                    currentTier.set(nextTier)
                                    urlRef.set(cand.url)
                                    Log.i("OkHttpDownloader", "Auto-recovered 403: switched to Tier $nextTier")
                                    break
                                }
                                freshUrl
                            }
                        }
                    }
                }

                try {
                    coroutineScope {
                        val ticker = launch {
                            while (isActive) {
                                delay(1000)
                                val done = downloaded.get()
                                val progress = (done * 100 / totalSize).toInt().coerceIn(0, 100)
                                val doneMB = String.format(Locale.US, "%.2f", done / (1024f * 1024f))
                                builder.setProgress(100, progress, false)
                                    .setContentText("$doneMB MB / $totalMB MB ($progress%)")
                                notificationManager.notify(notificationId, builder.build())

                                // 🔥 স্ক্রিনে লাইভ আপডেট পাঠানো
                                _downloadTasks.update { map ->
                                    map[songId]?.let { task ->
                                        map + (songId to task.copy(progress = progress, downloadedMB = doneMB, totalMB = totalMB))
                                    } ?: map
                                }
                            }
                        }

                        // 🔥 ৩টি প্যারালাল কানেকশন দিয়ে স্মুথ ডাউনলোড
                        val workers = (1..PARALLEL_CONNECTIONS).map {
                            async {
                                RandomAccessFile(destFile, "rw").use { raf ->
                                    while (isActive) {
                                        val idx = nextChunk.getAndIncrement()
                                        if (idx >= chunkCount) break
                                        val start = idx * CHUNK_SIZE
                                        val end = minOf(start + CHUNK_SIZE - 1, totalSize - 1)
                                        downloadRange(urlRef, start, end, raf, downloaded, refreshUrlOn403)
                                    }
                                }
                            }
                        }

                        try {
                            workers.awaitAll()
                        } finally {
                            ticker.cancel()
                        }
                    }
                } catch (e: Exception) {
                    if (destFile.exists()) destFile.delete()
                    if (coverFile.exists()) coverFile.delete()
                    throw e
                }

                if (isActive) {
                    // 🔥 গান পুরোপুরি সফলভাবে নামার পরেই কেবল কভার ছবি ডাউনলোড হবে
                    if (!coverFile.exists() && imageUrl.isNotBlank()) {
                        try {
                            URL(imageUrl).openStream().use { input ->
                                FileOutputStream(coverFile).use { output -> input.copyTo(output) }
                            }
                        } catch (e: Exception) {}
                    }

                    // গ্যালারিতে কভার ইমেজ যেন না দেখায়, তাই .nomedia ফাইল নিশ্চিত করা
                    try {
                        val noMedia = File(destDir, ".nomedia")
                        if (!noMedia.exists()) noMedia.createNewFile()
                    } catch (_: Exception) {}

                    // যদি সেটিংসে Auto Export চালু থাকে, তবে স্বয়ংক্রিয়ভাবে ডিভাইসে সেভ করা
                    val downloadPrefs = context.getSharedPreferences("download_settings", Context.MODE_PRIVATE)
                    if (downloadPrefs.getBoolean("auto_export_to_storage", false)) {
                        try {
                            exportSongToStorage(context, songId, title, artist)
                        } catch (_: Exception) {}
                    }

                    // 🔥 মেটাডাটা সেভ হবে শুধু সফল হলে
                    val prefs = context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE)
                    val jsonObj = org.json.JSONObject()
                    jsonObj.put("id", songId)
                    jsonObj.put("name", title)
                    jsonObj.put("artist", artist)
                    jsonObj.put("imageUrl", imageUrl)
                    jsonObj.put("downloadUrl", if (providedUrl.startsWith("http")) providedUrl else "")
                    prefs.edit().putString("song_$songId", jsonObj.toString()).apply()

                    builder.setProgress(0, 0, false)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentText("Download complete")
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .clearActions()
                    notificationManager.notify(notificationId, builder.build())

                    // 🔥 ফাইল থেকে আসল সাইজ চেক করা
                    val finalMB = if (destFile.exists() && destFile.length() > 0) {
                        String.format(Locale.US, "%.2f", destFile.length() / (1024f * 1024f))
                    } else totalMB

                    // 🔥 স্ক্রিনে স্ট্যাটাস কমপ্লিট করা
                    _downloadTasks.update { map ->
                        map[songId]?.let { task ->
                            map + (songId to task.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 100,
                                downloadedMB = finalMB,
                                totalMB = finalMB
                            ))
                        } ?: map
                    }
                } else {
                    if (destFile.exists()) destFile.delete()
                    if (coverFile.exists()) coverFile.delete()
                    context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit().remove("song_$songId").apply()
                    _downloadTasks.update { it.filterKeys { k -> k != songId } }
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    try {
                        notificationManager.cancel(notificationId)
                    } catch (ex: Exception) {}
                    if (destFile.exists()) destFile.delete()
                    if (coverFile.exists()) coverFile.delete()
                    context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit().remove("song_$songId").apply()
                    _downloadTasks.update { it.filterKeys { k -> k != songId } }
                } else {
                    Log.e("OkHttpDownloader", "Download Failed", e)
                    if (destFile.exists()) destFile.delete()
                    if (coverFile.exists()) coverFile.delete()
                    context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit().remove("song_$songId").apply()

                    builder.setProgress(0, 0, false)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentText("Download failed")
                        .setOngoing(false)
                        .clearActions()
                    notificationManager.notify(notificationId, builder.build())

                    // 🔥 স্ক্রিনে ফেইল্ড দেখানো
                    _downloadTasks.update { map ->
                        map[songId]?.let { task ->
                            map + (songId to task.copy(status = DownloadStatus.FAILED, errorMessage = e.message ?: "Failed"))
                        } ?: map
                    }
                }
            } finally {
                if (hasSlot) downloadSlots.release()
                activeDownloads.remove(songId)
                if (activeCount.decrementAndGet() <= 0) {
                    context.applicationContext.stopService(
                        Intent(context.applicationContext, DownloadService::class.java)
                    )
                }
            }
        }
        activeDownloads[songId] = job
    }

    fun isM4aContainer(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            FileInputStream(file).use { fis ->
                val buf = ByteArray(12)
                val read = fis.read(buf)
                if (read >= 8) {
                    val magic = String(buf, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1)
                    magic == "ftyp"
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun embedMetadataAndArtwork(file: File, coverFile: File?, title: String, artist: String, album: String = "") {
        if (!file.exists() || file.length() == 0L) return
        try {
            val isM4a = isM4aContainer(file)
            // AudioFileIO.read() ফাইলের extension (.m4a/.mp3) থেকে writer ঠিক করে দেয়
            val audioFile: org.jaudiotagger.audio.AudioFile = AudioFileIO.read(file)

            var tag = audioFile.tag
            if (tag == null) {
                tag = audioFile.tagOrCreateAndSetDefault
            }
            if (tag == null) {
                tag = if (isM4a) Mp4Tag() else ID3v23Tag()
                audioFile.tag = tag
            }

            tag.setField(FieldKey.TITLE, title)
            if (artist.isNotBlank()) {
                tag.setField(FieldKey.ARTIST, artist)
            }
            val resolvedAlbum = if (album.isNotBlank() && album != "Lytrix Music") {
                album
            } else if (artist.isNotBlank()) {
                "$title - $artist"
            } else {
                "$title (Single)"
            }
            tag.setField(FieldKey.ALBUM, resolvedAlbum)

            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                try {
                    val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                } catch (e: Exception) {
                    Log.w("OkHttpDownloader", "Failed to set artwork: ${e.message}")
                }
            }

            audioFile.commit()
            Log.d("OkHttpDownloader", "Successfully tagged $title (isM4a=$isM4a)")
        } catch (e: Exception) {
            Log.e("OkHttpDownloader", "Tagging error for $title: ${e.message}")
        }
    }

    fun isWebmContainer(file: File): Boolean = try {
        FileInputStream(file).use {
            val b = ByteArray(4)
            it.read(b) == 4 && b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() &&
                    b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte()
        }
    } catch (_: Exception) { false }

    // fragmented/DASH M4A -> সাধারণ M4A (AAC pass-through, কোনো re-encode নেই)
    private fun remuxToStandardM4a(src: File, dst: File): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i; format = f; break
                }
            }
            if (trackIndex < 0 || format == null) return false
            val mime = format.getString(MediaFormat.KEY_MIME)
            Log.d("OkHttpDownloader", "Remux source mime = $mime")
            if (mime != "audio/mp4a-latm") return false // শুধু AAC

            extractor.selectTrack(trackIndex)
            if (dst.exists()) dst.delete()
            muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxerStarted = false
            dst.exists() && dst.length() > 0
        } catch (e: Exception) {
            Log.e("OkHttpDownloader", "Remux failed: ${e.message}")
            false
        } finally {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    // WebM/Opus -> Ogg Opus (re-encode ছাড়া), API 29+
    private fun remuxWebmToOgg(src: File, dst: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i; format = f; break
                }
            }
            if (trackIndex < 0 || format == null) return false
            val mime = format.getString(MediaFormat.KEY_MIME)
            Log.d("OkHttpDownloader", "WebM remux source mime = $mime")
            if (mime != "audio/opus") return false

            extractor.selectTrack(trackIndex)
            if (dst.exists()) dst.delete()
            muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxerStarted = false
            dst.exists() && dst.length() > 0
        } catch (e: Exception) {
            Log.e("OkHttpDownloader", "WebM->Ogg remux failed: ${e.message}")
            false
        } finally {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun isPlayable(file: File): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (_: Exception) { false } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    // কভার -> স্কয়ার JPEG (সর্বোচ্চ 800px)
    private fun prepareCoverJpeg(coverFile: File, out: File): File? {
        if (!coverFile.exists() || coverFile.length() == 0L) return null
        return try {
            val bmp = BitmapFactory.decodeFile(coverFile.absolutePath) ?: return null
            val side = minOf(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val finalBmp = if (side > 800) Bitmap.createScaledBitmap(square, 800, 800, true) else square
            FileOutputStream(out).use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out
        } catch (e: Exception) { null }
    }

    // কপিতে ট্যাগ বসায়; ট্যাগের পর ফাইল নষ্ট হলে ট্যাগ ছাড়া কপি ফেরত দেয়
    private fun tagCopySafely(base: File, out: File, cover: File?, title: String, artist: String, album: String = "") {
        base.copyTo(out, overwrite = true)
        embedMetadataAndArtwork(out, cover, title, artist, album)
        if (!isPlayable(out)) {
            Log.w("OkHttpDownloader", "Tagged copy unplayable, using untagged copy")
            base.copyTo(out, overwrite = true)
        }
    }

    fun exportSongToStorage(context: Context, songId: String, title: String, artist: String = "", album: String = ""): Boolean {
        val tmpFiles = mutableListOf<File>()
        return try {
            val safeName = title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
            val srcDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return false
            val srcFile = File(srcDir, "$safeName.mp3")
            if (!srcFile.exists() || srcFile.length() == 0L) return false
            val coverFile = File(srcDir, "${safeName}_cover.jpg")

            var resolvedArtist = artist
            if (resolvedArtist.isBlank()) {
                try {
                    val metaStr = context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE)
                        .getString("song_$songId", null)
                    if (metaStr != null) resolvedArtist = org.json.JSONObject(metaStr).optString("artist", "")
                } catch (_: Exception) {}
            }

            var resolvedAlbum = album
            if (resolvedAlbum.isBlank() || resolvedAlbum == "Lytrix Music") {
                try {
                    val metaStr = context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE)
                        .getString("song_$songId", null)
                    if (metaStr != null) resolvedAlbum = org.json.JSONObject(metaStr).optString("album", "")
                } catch (_: Exception) {}
            }
            if (resolvedAlbum.isBlank() || resolvedAlbum == "Lytrix Music") {
                resolvedAlbum = if (resolvedArtist.isNotBlank()) "$title - $resolvedArtist" else "$title (Single)"
            }

            val isM4a = isM4aContainer(srcFile)
            val isWebm = !isM4a && isWebmContainer(srcFile)
            var ext = when { isM4a -> "m4a"; isWebm -> "webm"; else -> "mp3" }
            var mimeType = when { isM4a -> "audio/mp4"; isWebm -> "audio/webm"; else -> "audio/mpeg" }

            val cache = context.cacheDir
            val finalTmp = File(cache, "export_$safeName.$ext").also { tmpFiles += it }
            val coverJpg = prepareCoverJpeg(coverFile, File(cache, "cover_$safeName.jpg").also { tmpFiles += it })

            when {
                isM4a -> {
                    val remuxed = File(cache, "remux_$safeName.m4a").also { tmpFiles += it }
                    if (remuxToStandardM4a(srcFile, remuxed)) {
                        tagCopySafely(remuxed, finalTmp, coverJpg, title, resolvedArtist, resolvedAlbum)
                    } else {
                        srcFile.copyTo(finalTmp, overwrite = true) // ট্যাগ ছাড়া, কিন্তু করাপ্ট না
                    }
                }
                isWebm -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // MediaStore audio/webm নেয় না, তাই Ogg Opus বানাতে হবে
                        val oggTmp = File(cache, "export_$safeName.ogg").also { tmpFiles += it }
                        if (!remuxWebmToOgg(srcFile, oggTmp)) return false
                        oggTmp.copyTo(finalTmp, overwrite = true)
                        ext = "ogg"
                        mimeType = "audio/ogg"
                    } else {
                        srcFile.copyTo(finalTmp, overwrite = true)
                    }
                }
                else -> tagCopySafely(srcFile, finalTmp, coverJpg, title, resolvedArtist, resolvedAlbum)
            }
            if (!finalTmp.exists() || finalTmp.length() == 0L) return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relPath = "${Environment.DIRECTORY_MUSIC}/Lytrix/"
                // আগের একই নামের ফাইল থাকলে "(1)" না হয়ে রিপ্লেস হবে
                try {
                    resolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH}=?",
                        arrayOf("$safeName.$ext", relPath)
                    )
                } catch (_: Exception) {}

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.$ext")
                    put(MediaStore.Audio.Media.TITLE, title)
                    if (resolvedArtist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, resolvedArtist)
                    put(MediaStore.Audio.Media.ALBUM, resolvedAlbum)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relPath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(finalTmp).use { it.copyTo(out) }
                } ?: return false
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                try {
                    PlayerViewModel.activeInstance?.refreshLocalSongs(context)
                } catch (_: Exception) {}
                true
            } else {
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lytrix")
                if (!publicDir.exists()) publicDir.mkdirs()
                val targetFile = File(publicDir, "$safeName.$ext")
                finalTmp.copyTo(targetFile, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType)) { _, _ ->
                    try {
                        PlayerViewModel.activeInstance?.refreshLocalSongs(context)
                    } catch (_: Exception) {}
                }
                true
            }
        } catch (e: Exception) {
            Log.e("OkHttpDownloader", "Export failed: ${e.message}")
            false
        } finally {
            tmpFiles.forEach { try { if (it.exists()) it.delete() } catch (_: Exception) {} }
        }
    }
}