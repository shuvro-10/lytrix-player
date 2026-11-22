package com.musicplayer.lytrix.ui

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    val pattern = Regex("\\[(\\d{2,}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    fun isSynced(lrcContent: String): Boolean {
        return lrcContent.lines().any { pattern.find(it.trim()) != null }
    }

    fun parse(lrcContent: String): List<LrcLine> {
        val result = mutableListOf<LrcLine>()
        // Match [mm:ss.xx] or [mm:ss.xxx]
        
        lrcContent.lines().forEach { line ->
            val match = pattern.find(line.trim())
            if (match != null) {
                val mins = match.groupValues[1].toLong()
                val secs = match.groupValues[2].toLong()
                val milliStr = match.groupValues[3]
                val millis = if (milliStr.length == 2) milliStr.toLong() * 10 else milliStr.toLong()
                val timeMs = mins * 60000 + secs * 1000 + millis
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    result.add(LrcLine(timeMs, text))
                }
            } else if (line.trim().isNotBlank() && !line.trim().startsWith("[")) {
                 // For text that doesn't have [mm:ss.xx], pretend it is just an unknown timestamp
                 result.add(LrcLine(-1L, line.trim()))
            }
        }
        
        // Return sorted by time, put -1 at start so it acts as early text
        return result.sortedBy { it.timeMs }
    }
}
