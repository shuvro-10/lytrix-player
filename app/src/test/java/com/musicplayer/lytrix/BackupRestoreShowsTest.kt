package com.musicplayer.lytrix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.musicplayer.lytrix.ui.generateBackupData
import com.musicplayer.lytrix.ui.gzipCompress
import com.musicplayer.lytrix.ui.gzipDecompress
import com.musicplayer.lytrix.ui.restoreBackupData
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreShowsTest {

    @Test
    fun testBackupAndRestoreForMusicAndShows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Setup Mock Local Data for Music
        val likedSongsSp = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
        likedSongsSp.edit()
            .putStringSet("liked_ids", setOf("song_101", "song_102"))
            .putString("song_data_song_101", """{"id":"song_101","title":"Song One","artist":"Artist A"}""")
            .putString("song_data_song_102", """{"id":"song_102","title":"Song Two","artist":"Artist B"}""")
            .commit()

        val musicPlMeta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
        musicPlMeta.edit().putStringSet("names", setOf("MyRock")).commit()
        val musicPlSp = context.getSharedPreferences("online_pl_MyRock", Context.MODE_PRIVATE)
        musicPlSp.edit().putString("track_1", """{"id":"track_1","name":"Rock Song 1"}""").commit()

        // 2. Setup Mock Local Data for Shows (Podcasts & Audiobooks)
        val likedPodSp = context.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE)
        likedPodSp.edit()
            .putStringSet("liked_ids", setOf("pod_ep_1"))
            .putString("podcast_data_pod_ep_1", """{"id":"pod_ep_1","title":"Tech Podcast Ep 1"}""")
            .commit()

        val likedAbSp = context.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE)
        likedAbSp.edit()
            .putStringSet("liked_ids", setOf("book_ch_1"))
            .putString("audiobook_data_book_ch_1", """{"id":"book_ch_1","title":"Sherlock Holmes Ch 1"}""")
            .commit()

        val showPlMeta = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
        showPlMeta.edit().putStringSet("names", setOf("MysteryShowPlaylist")).commit()
        val showPlSp = context.getSharedPreferences("online_show_pl_MysteryShowPlaylist", Context.MODE_PRIVATE)
        showPlSp.edit().putString("show_item_1", """{"id":"show_item_1","name":"Mystery Case"}""").commit()

        val podMeta = context.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE)
        podMeta.edit()
            .putStringSet("names", setOf("Sunday Suspense", "New episodes", "Continue Listening"))
            .putStringSet("episodes_for_later_ids", setOf("later_ep_99"))
            .commit()
        val laterEpSp = context.getSharedPreferences("online_podcast_Episodes for later", Context.MODE_PRIVATE)
        laterEpSp.edit().putString("later_ep_99", """{"id":"later_ep_99","title":"Saved Episode"}""").commit()

        val abMeta = context.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE)
        abMeta.edit()
            .putStringSet("names", setOf("Feluda", "Continue Listening"))
            .putStringSet("books_for_later_ids", setOf("later_bk_55"))
            .commit()
        val laterBkSp = context.getSharedPreferences("online_ab_Books for later", Context.MODE_PRIVATE)
        laterBkSp.edit().putString("later_bk_55", """{"id":"later_bk_55","title":"Saved Book"}""").commit()

        // 3. Generate Backup Data
        val backupJson = generateBackupData(context, includeRoom = false)

        // Verify Backup Separation & Content
        assertTrue("Music liked songs present", backupJson.has("online_liked_songs"))
        assertTrue("Shows liked podcasts present", backupJson.has("online_liked_podcasts"))
        assertTrue("Shows liked audiobooks present", backupJson.has("online_liked_audiobooks"))
        assertTrue("Custom Shows Playlists present", backupJson.has("online_shows_playlists_meta"))

        val pcMetaObj = backupJson.getJSONObject("online_podcasts_meta")
        val pcNamesArray = pcMetaObj.getJSONArray("names")
        val backedUpPcNames = (0 until pcNamesArray.length()).map { pcNamesArray.getString(it) }.toSet()
        assertTrue("Real podcast saved", backedUpPcNames.contains("Sunday Suspense"))
        assertFalse("New episodes filtered out", backedUpPcNames.contains("New episodes"))
        assertFalse("Continue Listening filtered out", backedUpPcNames.contains("Continue Listening"))

        val pcDataObj = backupJson.getJSONObject("online_podcasts_data")
        assertTrue("Episodes for later data preserved in backup", pcDataObj.has("Episodes for later"))

        val abDataObj = backupJson.getJSONObject("online_audiobooks_data")
        assertTrue("Books for later data preserved in backup", abDataObj.has("Books for later"))

        // Verify Gzip Compression & Decompression
        val rawJsonStr = backupJson.toString()
        val compressed = gzipCompress(rawJsonStr)
        val decompressed = gzipDecompress(compressed)
        assertEquals("Decompressed JSON matches original", rawJsonStr, decompressed)

        // 4. Wipe Local Preferences (Simulating fresh install on new device)
        likedSongsSp.edit().clear().commit()
        musicPlMeta.edit().clear().commit()
        musicPlSp.edit().clear().commit()
        likedPodSp.edit().clear().commit()
        likedAbSp.edit().clear().commit()
        showPlMeta.edit().clear().commit()
        showPlSp.edit().clear().commit()
        podMeta.edit().clear().commit()
        laterEpSp.edit().clear().commit()
        abMeta.edit().clear().commit()
        laterBkSp.edit().clear().commit()

        // Verify Wipe
        assertTrue(likedSongsSp.getStringSet("liked_ids", emptySet())!!.isEmpty())
        assertTrue(likedPodSp.getStringSet("liked_ids", emptySet())!!.isEmpty())
        assertTrue(showPlMeta.getStringSet("names", emptySet())!!.isEmpty())

        // 5. Restore from Backup Data
        restoreBackupData(context, JSONObject(decompressed), restoreRoom = false)

        // 6. Verify Restored Data
        val restoredMusicLikes = likedSongsSp.getStringSet("liked_ids", emptySet()) ?: emptySet()
        assertEquals(setOf("song_101", "song_102"), restoredMusicLikes)
        assertTrue(likedSongsSp.getString("song_data_song_101", "")!!.contains("Song One"))

        val restoredPodLikes = likedPodSp.getStringSet("liked_ids", emptySet()) ?: emptySet()
        assertEquals(setOf("pod_ep_1"), restoredPodLikes)

        val restoredAbLikes = likedAbSp.getStringSet("liked_ids", emptySet()) ?: emptySet()
        assertEquals(setOf("book_ch_1"), restoredAbLikes)

        val restoredShowPlNames = showPlMeta.getStringSet("names", emptySet()) ?: emptySet()
        assertEquals(setOf("MysteryShowPlaylist"), restoredShowPlNames)
        assertTrue(showPlSp.all.isNotEmpty())

        val restoredLaterEps = podMeta.getStringSet("episodes_for_later_ids", emptySet()) ?: emptySet()
        assertEquals(setOf("later_ep_99"), restoredLaterEps)
        assertTrue("Episodes for later restored", laterEpSp.all.containsKey("later_ep_99"))

        val restoredLaterBks = abMeta.getStringSet("books_for_later_ids", emptySet()) ?: emptySet()
        assertEquals(setOf("later_bk_55"), restoredLaterBks)
        assertTrue("Books for later restored", laterBkSp.all.containsKey("later_bk_55"))

        println("✅ BACKUP & RESTORE UNIT TEST PASSED 100% CLEANLY!")
    }
}
