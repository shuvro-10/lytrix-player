package com.musicplayer.lytrix.ui.cinema

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR
}

@Immutable
data class CinemaDownloadTask(
    val id: String,
    val magnetUri: String,
    val title: String,
    val posterUrl: String = "",
    val quality: String = "",
    val sizeStr: String = "",
    val filePath: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedBytesPerSec: Int = 0,
    val peersCount: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val addedTime: Long = System.currentTimeMillis()
)

object TorrentDownloadManager {

    private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts")
    private val HASH_REGEX = Regex("btih:([0-9a-fA-F]{40})")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    private val _tasks = MutableStateFlow<List<CinemaDownloadTask>>(emptyList())
    val tasks: StateFlow<List<CinemaDownloadTask>> = _tasks.asStateFlow()

    private val activeHandles = ConcurrentHashMap<String, TorrentHandle>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        CinemaEngineManager.ensureNativeLoaded(context)
        scope.launch {
            loadTasksFromDisk(context)
            startMonitoring(context.applicationContext)
        }
    }

    fun getDownloadDirectory(context: Context): File {
        return try {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lytrix")
            if (publicDir.exists() || publicDir.mkdirs()) {
                publicDir
            } else {
                val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Lytrix")
                appDir.mkdirs()
                appDir
            }
        } catch (_: Throwable) {
            val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Lytrix")
            appDir.mkdirs()
            appDir
        }
    }

    fun startDownload(
        context: Context,
        magnetUri: String,
        title: String,
        quality: String = "",
        sizeStr: String = "",
        posterUrl: String = "",
        fileIdx: Int = -1
    ) {
        val hash = HASH_REGEX.find(magnetUri)?.groupValues?.get(1)?.lowercase()
            ?: (title + magnetUri.hashCode()).hashCode().toString()

        val existing = _tasks.value.find { it.id == hash }
        if (existing != null) {
            if (existing.status == DownloadStatus.COMPLETED && File(existing.filePath).exists()) {
                return
            }
            if (existing.status == DownloadStatus.PAUSED) {
                resumeDownload(context, hash)
                return
            }
        }

        val newTask = CinemaDownloadTask(
            id = hash,
            magnetUri = magnetUri,
            title = title,
            posterUrl = posterUrl,
            quality = quality,
            sizeStr = sizeStr,
            status = DownloadStatus.QUEUED
        )

        updateTaskList { list ->
            val filtered = list.filterNot { it.id == hash }
            listOf(newTask) + filtered
        }
        saveTasksToDisk(context)

        scope.launch {
            runDownloadEngine(context.applicationContext, newTask, fileIdx)
        }
    }

    fun pauseDownload(taskId: String) {
        val handle = activeHandles[taskId]
        if (handle != null && handle.isValid) {
            handle.pause()
        }
        updateTask(taskId) { it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0) }
    }

    fun resumeDownload(context: Context, taskId: String) {
        val handle = activeHandles[taskId]
        if (handle != null && handle.isValid) {
            handle.resume()
            updateTask(taskId) { it.copy(status = DownloadStatus.DOWNLOADING) }
        } else {
            val task = _tasks.value.find { it.id == taskId } ?: return
            scope.launch {
                runDownloadEngine(context.applicationContext, task)
            }
        }
    }

    fun cancelDownload(context: Context, taskId: String) {
        val handle = activeHandles.remove(taskId)
        if (handle != null && handle.isValid) {
            runCatching {
                TorrentEngine.getSession().remove(handle, SessionHandle.DELETE_FILES)
            }
        }
        updateTaskList { list -> list.filterNot { it.id == taskId } }
        saveTasksToDisk(context)
    }

    fun deleteCompleted(context: Context, taskId: String) {
        val task = _tasks.value.find { it.id == taskId }
        if (task != null && task.filePath.isNotBlank()) {
            runCatching {
                val file = File(task.filePath)
                if (file.exists()) file.delete()
            }
        }
        val handle = activeHandles.remove(taskId)
        if (handle != null && handle.isValid) {
            runCatching {
                TorrentEngine.getSession().remove(handle, SessionHandle.DELETE_FILES)
            }
        }
        updateTaskList { list -> list.filterNot { it.id == taskId } }
        saveTasksToDisk(context)
    }

    // ----------------------------------------------------------------------
    // Engine Execution
    // ----------------------------------------------------------------------

    private suspend fun runDownloadEngine(
        ctx: Context,
        task: CinemaDownloadTask,
        specifiedFileIdx: Int = -1
    ) = withContext(Dispatchers.IO) {
        val hash = task.id
        try {
            val sm = TorrentEngine.getSession()
            val saveDir = getDownloadDirectory(ctx)

            updateTask(hash) { it.copy(status = DownloadStatus.DOWNLOADING) }

            sm.download(task.magnetUri, saveDir, TorrentFlags.SEQUENTIAL_DOWNLOAD)

            val sha = Sha1Hash.parseHex(hash)
            var h: TorrentHandle? = null
            var info: org.libtorrent4j.TorrentInfo? = null
            val startWait = System.currentTimeMillis()

            while (info == null) {
                if (h == null || !h.isValid) h = sm.find(sha)
                if (h != null) {
                    activeHandles[hash] = h
                    info = h.torrentFile()
                }
                if (info == null) {
                    if (System.currentTimeMillis() - startWait > 90_000L) {
                        throw IllegalStateException("Timed out waiting for torrent metadata")
                    }
                    delay(1000)
                }
            }

            val th = h!!
            activeHandles[hash] = th

            val fs = info.files()
            val n = fs.numFiles()
            fun extOf(i: Int) = fs.filePath(i).substringAfterLast('.', "").lowercase()
            fun isVideo(i: Int) = extOf(i) in VIDEO_EXT

            val idx = if (specifiedFileIdx in 0 until n && isVideo(specifiedFileIdx)) {
                specifiedFileIdx
            } else {
                (0 until n).filter(::isVideo).maxByOrNull { fs.fileSize(it) } ?: 0
            }

            val prios = Array(n) { Priority.IGNORE }
            prios[idx] = Priority.DEFAULT
            th.prioritizeFiles(prios)

            val finalFile = File(saveDir, fs.filePath(idx))
            val totalBytes = fs.fileSize(idx)

            updateTask(hash) {
                it.copy(
                    filePath = finalFile.absolutePath,
                    totalBytes = totalBytes
                )
            }
            saveTasksToDisk(ctx)

        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            updateTask(hash) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = t.message ?: "Download failed"
                )
            }
            saveTasksToDisk(ctx)
        }
    }

    private fun startMonitoring(context: Context) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (true) {
                delay(1000)
                val currentTasks = _tasks.value
                var hasChanges = false

                currentTasks.forEach { task ->
                    val handle = activeHandles[task.id]
                    if (handle != null && handle.isValid && task.status == DownloadStatus.DOWNLOADING) {
                        val st = handle.status()
                        val progress = (st.progress() * 100).toInt().coerceIn(0, 100)
                        val rate = st.downloadRate()
                        val peers = st.numPeers()
                        val done = st.totalDone()
                        val isFinished = st.isFinished || (progress >= 100)

                        if (isFinished) {
                            activeHandles.remove(task.id)
                            updateTask(task.id) {
                                it.copy(
                                    status = DownloadStatus.COMPLETED,
                                    progressPercent = 100,
                                    speedBytesPerSec = 0,
                                    downloadedBytes = it.totalBytes
                                )
                            }
                            hasChanges = true
                            // Trigger system media scan so VLC, Gallery & MX Player immediately index the video
                            if (task.filePath.isNotBlank()) {
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(task.filePath),
                                    null,
                                    null
                                )
                            }
                        } else {
                            updateTask(task.id) {
                                it.copy(
                                    progressPercent = progress,
                                    speedBytesPerSec = rate,
                                    peersCount = peers,
                                    downloadedBytes = done
                                )
                            }
                        }
                    }
                }

                if (hasChanges) {
                    saveTasksToDisk(context)
                }
            }
        }
    }

    private fun updateTask(taskId: String, transform: (CinemaDownloadTask) -> CinemaDownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == taskId) transform(it) else it }
    }

    private fun updateTaskList(transform: (List<CinemaDownloadTask>) -> List<CinemaDownloadTask>) {
        _tasks.value = transform(_tasks.value)
    }

    // ----------------------------------------------------------------------
    // Disk Persistence
    // ----------------------------------------------------------------------

    private fun saveTasksToDisk(context: Context) {
        try {
            val file = File(context.filesDir, "cinema_downloads.json")
            val array = JSONArray()
            _tasks.value.forEach { task ->
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("magnetUri", task.magnetUri)
                    put("title", task.title)
                    put("posterUrl", task.posterUrl)
                    put("quality", task.quality)
                    put("sizeStr", task.sizeStr)
                    put("filePath", task.filePath)
                    put("totalBytes", task.totalBytes)
                    put("downloadedBytes", task.downloadedBytes)
                    put("progressPercent", task.progressPercent)
                    put("status", task.status.name)
                    put("addedTime", task.addedTime)
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Throwable) {}
    }

    private fun loadTasksFromDisk(context: Context) {
        try {
            val file = File(context.filesDir, "cinema_downloads.json")
            if (!file.exists()) return

            val text = file.readText()
            val array = JSONArray(text)
            val list = mutableListOf<CinemaDownloadTask>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isBlank()) continue

                val filePath = obj.optString("filePath", "")
                val savedStatus = obj.optString("status", DownloadStatus.QUEUED.name)
                var status = runCatching { DownloadStatus.valueOf(savedStatus) }.getOrDefault(DownloadStatus.QUEUED)

                if (status == DownloadStatus.COMPLETED) {
                    // Check if file still exists on disk
                    if (filePath.isBlank() || !File(filePath).exists()) {
                        continue // Removed from disk externally
                    }
                } else if (status == DownloadStatus.DOWNLOADING) {
                    status = DownloadStatus.PAUSED // Pause on restart so user can resume
                }

                list.add(
                    CinemaDownloadTask(
                        id = id,
                        magnetUri = obj.optString("magnetUri", ""),
                        title = obj.optString("title", ""),
                        posterUrl = obj.optString("posterUrl", ""),
                        quality = obj.optString("quality", ""),
                        sizeStr = obj.optString("sizeStr", ""),
                        filePath = filePath,
                        totalBytes = obj.optLong("totalBytes", 0L),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        progressPercent = obj.optInt("progressPercent", 0),
                        status = status,
                        addedTime = obj.optLong("addedTime", System.currentTimeMillis())
                    )
                )
            }
            _tasks.value = list
        } catch (_: Throwable) {}
    }

    fun formatSpeed(bytesPerSec: Int): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}
