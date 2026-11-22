package com.musicplayer.lytrix.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import org.json.JSONObject

data class TrashedItem(
    val originalPath: String,
    val trashedPath: String,
    val timestamp: Long,
    val isVideo: Boolean,
    val title: String? = null,
    val dateExpires: Long? = null,
    val artist: String? = null
)

object TrashManager {
    
    private fun getTrashDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), ".recently_deleted")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getIndexFile(context: Context): File {
        return File(getTrashDir(context), "trash_index.json")
    }

    private fun loadIndex(context: Context): MutableList<TrashedItem> {
        val indexFile = getIndexFile(context)
        if (!indexFile.exists()) return mutableListOf()
        try {
            val jsonStr = indexFile.readText()
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<TrashedItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TrashedItem(
                        originalPath = obj.getString("originalPath"),
                        trashedPath = obj.getString("trashedPath"),
                        timestamp = obj.getLong("timestamp"),
                        isVideo = obj.optBoolean("isVideo", false),
                        title = obj.optString("title", null).takeIf { it.isNotEmpty() },
                        dateExpires = if (obj.has("dateExpires")) obj.getLong("dateExpires") else null,
                        artist = obj.optString("artist", null).takeIf { it.isNotEmpty() }
                    )
                )
            }
            return list
        } catch (e: Exception) {
            return mutableListOf()
        }
    }

    private fun saveIndex(context: Context, list: MutableList<TrashedItem>) {
        try {
            val array = org.json.JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("originalPath", item.originalPath)
                obj.put("trashedPath", item.trashedPath)
                obj.put("timestamp", item.timestamp)
                obj.put("isVideo", item.isVideo)
                if (item.title != null) obj.put("title", item.title)
                if (item.dateExpires != null) obj.put("dateExpires", item.dateExpires)
                if (item.artist != null) obj.put("artist", item.artist)
                array.put(obj)
            }
            getIndexFile(context).writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getTrashedItems(context: Context, isVideo: Boolean): List<TrashedItem> {
        val items = loadIndex(context).filter { it.isVideo == isVideo }.toMutableList()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projectionList = mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE)
                if (!isVideo) {
                    projectionList.add(MediaStore.Audio.Media.ARTIST)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    projectionList.add(MediaStore.MediaColumns.DATE_EXPIRES)
                }
                val projection = projectionList.toTypedArray()
                
                val bundle = android.os.Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, 1)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.IS_TRASHED} = 1")
                }
                
                context.contentResolver.query(collection, projection, bundle, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val titleCol = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
                    val artistCol = if (!isVideo) cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST) else -1
                    val expiresCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val path = cursor.getString(dataCol)
                        val date = cursor.getLong(dateCol) * 1000L
                        val nameStr = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else null
                        val titleStr = if (titleCol >= 0 && !cursor.isNull(titleCol)) cursor.getString(titleCol) else null
                        val name = titleStr ?: nameStr
                        val artist = if (artistCol >= 0 && !cursor.isNull(artistCol)) cursor.getString(artistCol) else null
                        val expires = if (expiresCol >= 0 && !cursor.isNull(expiresCol)) cursor.getLong(expiresCol) * 1000L else null
                        val uriStr = android.content.ContentUris.withAppendedId(collection, id).toString()
                        
                        items.add(TrashedItem(uriStr, path ?: uriStr, date, isVideo, name, expires, artist))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        return items
    }

    fun moveToTrash(context: Context, uri: Uri, filePath: String, isVideo: Boolean): Boolean {
        // Try IS_TRASHED (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val isTrashed = cursor.getInt(0)
                    cursor.close()
                    if (isTrashed == 1) return true // Already trashed via IntentSender!
                } else {
                    cursor?.close()
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 1)
                }
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated > 0) return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Fallback for Android 9 and below, OR if Scoped Storage blocks update
        var success = false
        try {
            val file = File(filePath)
            if (file.exists()) {
                val trashDir = getTrashDir(context)
                val trashingFile = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
                if (file.renameTo(trashingFile)) {
                    val list = loadIndex(context)
                    list.add(TrashedItem(filePath, trashingFile.absolutePath, System.currentTimeMillis(), isVideo))
                    saveIndex(context, list)
                    
                    // Trigger MediaScanner to remove the original
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    success = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Last resort: permanently delete if nothing else works (because Scoped Storage blocked rename and IS_TRASHED)
        if (!success) {
            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) success = true
            } catch (e: Exception) {
                e.printStackTrace()
                // If even this fails (requires activity result on Android 11+ for non-owned files), 
                // we have no non-activity based fallback left.
            }
        }
        return success
    }

    fun restoreItem(context: Context, item: TrashedItem): Boolean {
        try {
            val file = File(item.trashedPath)
            val dest = File(item.originalPath)
            if (file.exists() && file.renameTo(dest)) {
                val list = loadIndex(context)
                list.removeAll { it.trashedPath == item.trashedPath }
                saveIndex(context, list)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun deletePermanently(context: Context, item: TrashedItem): Boolean {
        try {
            val file = File(item.trashedPath)
            if (!file.exists() || file.delete()) {
                val list = loadIndex(context)
                list.removeAll { it.trashedPath == item.trashedPath }
                saveIndex(context, list)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
