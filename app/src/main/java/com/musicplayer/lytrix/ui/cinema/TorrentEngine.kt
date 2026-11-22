package com.musicplayer.lytrix.ui.cinema

import android.content.Context
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack

/**
 * Shared singleton SessionManager for Lytrix Cinema.
 * Safely coordinates both on-demand streaming and native background downloading
 * without JNI concurrency conflicts or duplicate socket allocations.
 */
object TorrentEngine {
    private val lock = Any()
    @Volatile
    private var session: SessionManager? = null

    fun getSession(context: Context? = null): SessionManager {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            if (context != null) {
                CinemaEngineManager.ensureNativeLoaded(context)
            }
            val sp = SettingsPack().apply {
                connectionsLimit(250)
                setEnableDht(true)
                setEnableLsd(true)
                activeDownloads(10)
                activeSeeds(5)
                activeLimit(15)
            }
            val sm = SessionManager(false)
            sm.start(SessionParams(sp))
            session = sm
            return sm
        }
    }
}
