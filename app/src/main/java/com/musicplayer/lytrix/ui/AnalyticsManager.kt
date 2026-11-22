package com.musicplayer.lytrix.ui

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    // অ্যাপ ওপেন হওয়ার সময় ইনিশিয়ালাইজ করার জন্য
    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }

    // ১. স্ক্রিন ট্র্যাক করার ফাংশন
    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    // ২. গান প্লে ট্র্যাক করার কাস্টম ইভেন্ট
    fun logSongPlayed(songTitle: String, artist: String) {
        val bundle = Bundle().apply {
            putString("song_title", songTitle)
            putString("artist", artist)
        }
        firebaseAnalytics?.logEvent("track_song_played", bundle)
    }

    // ৩. থিম চেঞ্জ ট্র্যাক করার কাস্টম ইভেন্ট
    fun logThemeChanged(themeName: String) {
        val bundle = Bundle().apply {
            putString("theme_name", themeName)
        }
        firebaseAnalytics?.logEvent("theme_changed", bundle)
    }

    // ৪. রিওয়ার্ডেড অ্যাড ক্লিক ট্র্যাক
    fun logAdFreeRewardClick(currentCount: Int) {
        val bundle = Bundle().apply {
            putInt("ads_watched_count", currentCount)
        }
        firebaseAnalytics?.logEvent("ad_free_gift_clicked", bundle)
    }
}