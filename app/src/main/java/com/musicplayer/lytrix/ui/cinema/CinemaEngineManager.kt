package com.musicplayer.lytrix.ui.cinema

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed interface EngineDownloadState {
    data object Idle : EngineDownloadState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedStr: String
    ) : EngineDownloadState
    data object Extracting : EngineDownloadState
    data object Ready : EngineDownloadState
    data class Error(val message: String) : EngineDownloadState
}

object CinemaEngineManager {

    private const val TAG = "CinemaEngineMgr"
    private const val DEFAULT_REPO = "shuvro-10/lytrix-player"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _downloadState = MutableStateFlow<EngineDownloadState>(EngineDownloadState.Idle)
    val downloadState: StateFlow<EngineDownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var nativeLoaded = false

    fun getEngineDir(context: Context): File {
        val dir = File(context.filesDir, "cinema_engine")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getEngineFile(context: Context): File {
        return File(getEngineDir(context), "libtorrent4j.so")
    }

    fun isEngineReady(context: Context): Boolean {
        val file = getEngineFile(context)
        return file.exists() && file.length() > 5_000_000L
    }

    fun getArchitectureName(): String {
        val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return if (primary.contains("arm64")) "arm64-v8a (64-bit)" else "armeabi-v7a (32-bit)"
    }

    fun getRecommendedAssetName(): String {
        val isArm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") }
        return if (isArm64) "cinema-engine-arm64.zip" else "cinema-engine-armv7a.zip"
    }

    fun getExpectedDownloadSize(): String {
        val isArm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") }
        return if (isArm64) "6.2 MB" else "5.3 MB"
    }

    fun getDownloadUrl(repo: String = DEFAULT_REPO): String {
        val asset = getRecommendedAssetName()
        return "https://github.com/$repo/releases/latest/download/$asset"
    }

    @Synchronized
    fun ensureNativeLoaded(context: Context): Boolean {
        if (nativeLoaded) return true
        val file = getEngineFile(context)
        if (!file.exists() || file.length() < 1_000_000L) {
            Log.w(TAG, "libtorrent4j.so not present in private storage")
            return false
        }
        return try {
            file.setReadOnly()
            System.load(file.absolutePath)
            nativeLoaded = true
            Log.i(TAG, "Successfully loaded dynamic libtorrent4j.so from ${file.absolutePath}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load dynamic libtorrent4j.so", t)
            false
        }
    }

    suspend fun downloadAndInstallEngine(
        context: Context,
        customUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getEngineFile(context)
        val tempZip = File(getEngineDir(context), "engine_temp.zip")

        _downloadState.value = EngineDownloadState.Downloading(0f, 0L, 0L, "Connecting...")

        val url = customUrl ?: getDownloadUrl()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LytrixPlayer-EngineDownloader/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Download failed (HTTP ${response.code})"
                _downloadState.value = EngineDownloadState.Error(err)
                return@withContext false
            }

            val body = response.body ?: run {
                _downloadState.value = EngineDownloadState.Error("Empty response body")
                return@withContext false
            }

            val contentLength = body.contentLength()
            var downloaded = 0L
            var lastTime = System.currentTimeMillis()
            var lastDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 400) {
                            val timeDiffSec = (now - lastTime) / 1000f
                            val speedBytesSec = if (timeDiffSec > 0) ((downloaded - lastDownloaded) / timeDiffSec).toLong() else 0L
                            val speedStr = formatSpeed(speedBytesSec)
                            val progress = if (contentLength > 0) downloaded.toFloat() / contentLength.toFloat() else 0f

                            _downloadState.value = EngineDownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = contentLength,
                                speedStr = speedStr
                            )
                            lastTime = now
                            lastDownloaded = downloaded
                        }
                    }
                    output.flush()
                }
            }

            // Extract the .so file from downloaded zip
            _downloadState.value = EngineDownloadState.Extracting
            val extracted = extractSoFromZip(tempZip, targetFile)
            tempZip.delete()

            if (!extracted || !targetFile.exists() || targetFile.length() < 1_000_000L) {
                _downloadState.value = EngineDownloadState.Error("Failed to extract libtorrent4j.so")
                return@withContext false
            }

            // Mark read-only and load into JVM
            ensureNativeLoaded(context)

            _downloadState.value = EngineDownloadState.Ready
            true
        } catch (e: Exception) {
            Log.e(TAG, "Engine download failed", e)
            tempZip.delete()
            _downloadState.value = EngineDownloadState.Error(e.localizedMessage ?: "Network error")
            false
        }
    }

    /**
     * Installs from a local zip file (useful for manual or offline staging).
     */
    fun installFromLocalZip(context: Context, zipFile: File): Boolean {
        if (!zipFile.exists()) return false
        val targetFile = getEngineFile(context)
        val extracted = extractSoFromZip(zipFile, targetFile)
        if (extracted && targetFile.exists()) {
            ensureNativeLoaded(context)
            _downloadState.value = EngineDownloadState.Ready
            return true
        }
        return false
    }

    fun deleteEngine(context: Context): Boolean {
        val file = getEngineFile(context)
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            _downloadState.value = EngineDownloadState.Idle
        }
        return deleted
    }

    private fun extractSoFromZip(zipFile: File, outputFile: File): Boolean {
        return try {
            zipFile.inputStream().use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith("libtorrent4j.so")) {
                            if (outputFile.exists()) outputFile.delete()
                            outputFile.outputStream().use { fos ->
                                zis.copyTo(fos)
                            }
                            return true
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unzip error", e)
            false
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%d KB/s", bytesPerSec / 1024)
            else -> "$bytesPerSec B/s"
        }
    }
}
