package com.musicplayer.lytrix.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.musicplayer.lytrix.ui.generateBackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AutoBackupInterval(val displayName: String, val intervalMs: Long) {
    OFF("Off", 0L),
    DAILY("Daily", AlarmManager.INTERVAL_DAY),
    EVERY_3_DAYS("3 Days", AlarmManager.INTERVAL_DAY * 3L),
    WEEKLY("Weekly", AlarmManager.INTERVAL_DAY * 7L);

    companion object {
        fun fromStored(value: String?): AutoBackupInterval =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

data class AutoBackupStatus(
    val interval: AutoBackupInterval,
    val treeUriString: String?,
    val lastBackupAtMs: Long,
    val lastLocationLabel: String
)

/**
 * Lytrix-inspired scheduled local JSON+Room snapshot backup manager.
 */
object ScheduledBackupManager {
    private const val PREF_NAME = "scheduled_backup_prefs"
    private const val KEY_INTERVAL = "backup_interval"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_LAST_AT = "last_backup_at"
    private const val KEY_LAST_DEST = "last_backup_dest"

    const val ACTION_RUN_AUTO_BACKUP = "com.musicplayer.lytrix.action.RUN_AUTO_BACKUP"

    fun getStatus(context: Context): AutoBackupStatus {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return AutoBackupStatus(
            interval = AutoBackupInterval.fromStored(prefs.getString(KEY_INTERVAL, AutoBackupInterval.OFF.name)),
            treeUriString = prefs.getString(KEY_TREE_URI, null),
            lastBackupAtMs = prefs.getLong(KEY_LAST_AT, 0L),
            lastLocationLabel = prefs.getString(KEY_LAST_DEST, "") ?: ""
        )
    }

    fun setInterval(context: Context, interval: AutoBackupInterval) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INTERVAL, interval.name)
            .apply()
        if (interval == AutoBackupInterval.OFF) cancelSchedule(context) else schedule(context, interval)
    }

    fun setTreeUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, uri?.toString())
            .apply()
    }

    fun rescheduleIfEnabled(context: Context) {
        val status = getStatus(context)
        if (status.interval != AutoBackupInterval.OFF) {
            schedule(context, status.interval)
        }
    }

    fun schedule(context: Context, interval: AutoBackupInterval) {
        if (interval == AutoBackupInterval.OFF) {
            cancelSchedule(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmPendingIntent(context)
        val firstTrigger = System.currentTimeMillis() + interval.intervalMs
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTrigger,
            interval.intervalMs,
            pi
        )
    }

    fun cancelSchedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduledBackupReceiver::class.java).apply {
            action = ACTION_RUN_AUTO_BACKUP
        }
        return PendingIntent.getBroadcast(
            context,
            45_200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun runAutoBackupNow(context: Context): String = withContext(Dispatchers.IO) {
        val payload = generateBackupData(context, includeRoom = true).toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Lytrix_AutoBackup_$stamp.json"
        val status = getStatus(context)

        val savedLocation = if (!status.treeUriString.isNullOrBlank()) {
            val savedToTree = runCatching {
                val treeUri = Uri.parse(status.treeUriString)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val destUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    "application/json",
                    fileName
                ) ?: error("Unable to create SAF document")
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                "Selected Folder/$fileName"
            }.getOrNull()
            savedToTree ?: writeRollingAppBackup(context, fileName, payload)
        } else {
            writeRollingAppBackup(context, fileName, payload)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putString(KEY_LAST_DEST, savedLocation)
            .apply()

        savedLocation
    }

    private fun writeRollingAppBackup(context: Context, fileName: String, content: String): String {
        val dir = context.getExternalFilesDir("Backups") ?: File(context.filesDir, "Backups")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        outFile.writeText(content, Charsets.UTF_8)

        // Retain at most 5 rolling auto-backups in internal/app storage.
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("Lytrix_AutoBackup_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { runCatching { it.delete() } }

        return "App Backups/$fileName"
    }

    suspend fun loadLatestAutoBackupJson(context: Context): Pair<String, org.json.JSONObject>? = withContext(Dispatchers.IO) {
        val status = getStatus(context)
        if (!status.treeUriString.isNullOrBlank()) {
            val fromSaf = runCatching {
                val treeUri = Uri.parse(status.treeUriString)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                var latestName: String? = null
                var latestUri: Uri? = null
                var latestModified = -1L
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        val docName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        if (!docName.startsWith("Lytrix_AutoBackup_") || !docName.endsWith(".json")) continue
                        val mod = if (modCol >= 0) cursor.getLong(modCol) else 0L
                        if (mod >= latestModified && idCol >= 0) {
                            latestModified = mod
                            latestName = docName
                            latestUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                        }
                    }
                }
                val targetUri = latestUri ?: return@runCatching null
                val raw = context.contentResolver.openInputStream(targetUri)?.bufferedReader()?.use { it.readText() }
                    ?: return@runCatching null
                (latestName ?: "AutoBackup.json") to org.json.JSONObject(raw)
            }.getOrNull()
            if (fromSaf != null) return@withContext fromSaf
        }

        val dirs = listOfNotNull(
            context.getExternalFilesDir("Backups"),
            File(context.filesDir, "Backups")
        )
        val latestFile = dirs.flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && it.name.startsWith("Lytrix_AutoBackup_") && it.name.endsWith(".json") }
            .maxByOrNull { it.lastModified() }
            ?: return@withContext null

        runCatching { latestFile.name to org.json.JSONObject(latestFile.readText(Charsets.UTF_8)) }.getOrNull()
    }
}
