package com.musicplayer.lytrix.ui

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.lytrix.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideosViewModel : ViewModel() {

    private val _videosList = MutableStateFlow<List<Video>>(emptyList())
    val videosList: StateFlow<List<Video>> = _videosList.asStateFlow()

    // 🔥 ম্যাজিক: hiddenIds এর ডিফল্ট ভ্যালু দিয়ে দিলাম, যাতে VideoPlayerFullscreen এর কোনো সমস্যা না হয়!
    fun loadVideos(context: Context, hiddenIds: List<Long> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Video>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_MODIFIED
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)

                    // হিডেন ভিডিওগুলো ফিল্টার করা হচ্ছে
                    if (!hiddenIds.contains(-id)) {
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val data = cursor.getString(dataColumn) ?: ""
                        val date = cursor.getLong(dateColumn)

                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        list.add(
                            Video(
                                id = id,
                                title = title,
                                durationText = formatDuration(duration),
                                sizeText = formatSize(size),
                                path = data,
                                uriString = uri.toString(),
                                dateModified = date
                            )
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _videosList.value = list
            }
        }
    }

    fun deleteVideo(context: Context, video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (com.musicplayer.lytrix.utils.TrashManager.moveToTrash(
                        context,
                        android.net.Uri.parse(video.uriString),
                        video.path,
                        true
                    )) {
                    val updated = _videosList.value.filter { it.id != video.id }
                    withContext(Dispatchers.Main) {
                        _videosList.value = updated
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 🔥 ম্যাজিক: VideoPlayerFullscreen এর জন্য পুরনো ফাংশন
    fun hideVideo(context: Context, video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            _videosList.value = _videosList.value.filter { it.id != video.id }
        }
    }

    // 🔥 ম্যাজিক: VideosScreen এর জন্য নতুন ফাংশন
    fun hideVideo(video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            _videosList.value = _videosList.value.filter { it.id != video.id }
        }
    }

    suspend fun renameVideo(context: android.content.Context, video: com.musicplayer.lytrix.model.Video, newName: String): android.content.IntentSender? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val contentUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    video.id
                )

                try {
                    val oldFile = java.io.File(video.path)
                    val extension = oldFile.extension
                    val newDisplayName = if (extension.isNotEmpty()) "$newName.$extension" else newName

                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                        put(android.provider.MediaStore.MediaColumns.TITLE, newName)
                    }
                    context.contentResolver.update(contentUri, values, null, null)

                    val rowsUpdated = context.contentResolver.update(
                        contentUri,
                        values,
                        null,
                        null
                    )

                    if (rowsUpdated > 0) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val updatedVideo = video.copy(
                                title = newName
                            )

                            _videosList.value =
                                _videosList.value.map {
                                    if (it.id == video.id) updatedVideo
                                    else it
                                }
                        }
                    }
                    return@withContext null
                } catch (e: SecurityException) {
                    val pendingIntent = android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(contentUri))
                    return@withContext pendingIntent.intentSender
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    val file = java.io.File(video.path)
                    if (file.exists()) {
                        val dir = file.parentFile
                        val ext = file.extension
                        val newTitle = newName.replace("/", "_")
                        val newFileName = if (ext.isNotEmpty()) "$newTitle.$ext" else newTitle
                        val newFile = java.io.File(dir, newFileName)
                        if (file.renameTo(newFile)) {
                            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath, newFile.absolutePath), null, null)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val updatedVideo = video.copy(
                                    title = newName,
                                    uriString = "file://" + newFile.absolutePath,
                                    path = newFile.absolutePath
                                )
                                _videosList.value = _videosList.value.map { if (it.id == video.id) updatedVideo else it }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return@withContext null
        }
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalSecs = (durationMillis / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}