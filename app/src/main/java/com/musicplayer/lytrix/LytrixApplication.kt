package com.musicplayer.lytrix

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class LytrixApplication : Application(), ImageLoaderFactory {
    companion object {
        lateinit var instance: LytrixApplication
            private set
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(com.musicplayer.lytrix.utils.VideoArtworkFallbackInterceptor())
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Plant Timber DebugTree for Logcat
        timber.log.Timber.plant(timber.log.Timber.DebugTree())

        // 🔥 আমাদের বানানো Downloader দিয়ে NewPipe চালু করা হলো
        NewPipe.init(LytrixDownloader())

        // Initialize Spotify preferences cache
        com.musicplayer.lytrix.spotify.SpotifyPreferencesManager.getInstance(this)

        // Spotify GQL hashes sync
        CoroutineScope(Dispatchers.IO).launch {
            com.musicplayer.lytrix.spotify.sync.SpotifyHashSync.sync(this@LytrixApplication)
        }

        // Phase 6 background schedulers: Music Alarms, Scheduled Auto-Backup, Artist New Releases
        runCatching { com.musicplayer.lytrix.alarm.MusicAlarmScheduler.rescheduleAll(this) }
        runCatching { com.musicplayer.lytrix.backup.ScheduledBackupManager.rescheduleIfEnabled(this) }
        if (com.musicplayer.lytrix.sync.NewReleaseChecker.isEnabled(this)) {
            runCatching { com.musicplayer.lytrix.sync.NewReleaseChecker.schedulePeriodicChecks(this) }
            val lastCheck = com.musicplayer.lytrix.sync.NewReleaseChecker.getLastCheckMs(this)
            if (System.currentTimeMillis() - lastCheck > 12 * 3600_000L) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { com.musicplayer.lytrix.sync.NewReleaseChecker.checkForNewReleases(this@LytrixApplication, pushNotification = true) }
                }
            }
        }
    }
}
