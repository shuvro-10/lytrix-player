package com.musicplayer.lytrix.utils

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.aod.AodModeTileService
import com.musicplayer.lytrix.shazam.MusicRecognizerTileService

/**
 * Prompts Android 13+ (API 33) users to pin Lytrix Quick Settings tiles directly via system API,
 * avoiding OEM notification shade Edit drawer quirks (such as Transsion HiOS / XOS).
 */
object QuickSettingsTileHelper {

    fun requestRecognizeSongTile(context: Context) {
        requestAddTile(
            context = context,
            serviceClass = MusicRecognizerTileService::class.java,
            label = "Recognize Song",
            iconResId = R.drawable.ic_music_recognition
        )
    }

    fun requestAodTile(context: Context) {
        requestAddTile(
            context = context,
            serviceClass = AodModeTileService::class.java,
            label = "Always-On Player",
            iconResId = R.drawable.ic_aod_moon
        )
    }

    private fun requestAddTile(
        context: Context,
        serviceClass: Class<*>,
        label: String,
        iconResId: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                context,
                "Pull down Quick Settings → tap Edit → drag \"$label\" to add",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            Toast.makeText(context, "Quick Settings manager unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            statusBarManager.requestAddTileService(
                ComponentName(context, serviceClass),
                label,
                Icon.createWithResource(context, iconResId),
                context.mainExecutor
            ) { resultCode ->
                val msg = when (resultCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        "\"$label\" added to Quick Settings!"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "\"$label\" is already in your Quick Settings"
                    else ->
                        "Tile not added — you can also add it from Quick Settings Edit menu"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(
                context,
                "Open Quick Settings → Edit tiles → add \"$label\"",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
