package com.musicplayer.lytrix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

object WidgetConstants {
    const val ACTION_OPEN_INSIGHTS = "com.musicplayer.lytrix.action.OPEN_INSIGHTS"
    const val ACTION_OPEN_PLAYLIST = "com.musicplayer.lytrix.action.OPEN_PLAYLIST"
    const val EXTRA_PLAYLIST_TYPE = "extra_playlist_type"
    const val EXTRA_PLAYLIST_ID = "extra_playlist_id"

    internal const val ACTION_WIDGET_PLAY_PAUSE = "com.musicplayer.lytrix.widget.PLAY_PAUSE"
    internal const val ACTION_WIDGET_NEXT = "com.musicplayer.lytrix.widget.NEXT"
    internal const val ACTION_WIDGET_PREV = "com.musicplayer.lytrix.widget.PREV"
    internal const val ACTION_WIDGET_TOGGLE_LIKE = "com.musicplayer.lytrix.widget.TOGGLE_LIKE"

    internal const val CMD_PLAY_PAUSE = "PLAY_PAUSE"
    internal const val CMD_NEXT = "NEXT"
    internal const val CMD_PREV = "PREV"
    internal const val CMD_TOGGLE_LIKE = "TOGGLE_LIKE"
}

/**
 * Shared AppWidgetProvider base handling launcher updates, widget resizing, and broadcast actions.
 */
abstract class BaseLytrixWidgetReceiver : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LytrixWidgetUpdater.scheduleUpdate(context, immediate = true)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LytrixWidgetUpdater.scheduleUpdate(context, immediate = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        LytrixWidgetUpdater.scheduleUpdate(context, immediate = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val cmd = when (intent.action) {
            WidgetConstants.ACTION_WIDGET_PLAY_PAUSE -> WidgetConstants.CMD_PLAY_PAUSE
            WidgetConstants.ACTION_WIDGET_NEXT -> WidgetConstants.CMD_NEXT
            WidgetConstants.ACTION_WIDGET_PREV -> WidgetConstants.CMD_PREV
            WidgetConstants.ACTION_WIDGET_TOGGLE_LIKE -> WidgetConstants.CMD_TOGGLE_LIKE
            else -> return
        }

        val pendingResult = goAsync()
        actionScope.launch {
            try {
                LytrixWidgetUpdater.handleCommand(context.applicationContext, cmd)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

internal object WidgetBitmapHelper {

    fun scaleCenterCrop(src: Bitmap, targetSize: Int): Bitmap {
        val cropSize = minOf(src.width, src.height).coerceAtLeast(1)
        val xOff = (src.width - cropSize) / 2
        val yOff = (src.height - cropSize) / 2
        val square = if (src.width == cropSize && src.height == cropSize) {
            src
        } else {
            Bitmap.createBitmap(src, xOff, yOff, cropSize, cropSize)
        }
        val scaled = if (square.width == targetSize && square.height == targetSize) {
            square
        } else {
            Bitmap.createScaledBitmap(square, targetSize, targetSize, true)
        }
        if (square !== src && square !== scaled) square.recycle()
        return scaled
    }

    fun createRoundedBitmap(src: Bitmap, cornerRadiusPx: Float, targetSize: Int = 240): Bitmap {
        val prepared = scaleCenterCrop(src, targetSize)
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(prepared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat()),
            cornerRadiusPx,
            cornerRadiusPx,
            paint
        )
        if (prepared !== src) prepared.recycle()
        return out
    }

    fun createCircularVinylBitmap(src: Bitmap, targetSize: Int = 300): Bitmap {
        val prepared = scaleCenterCrop(src, targetSize)
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = targetSize / 2f
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(prepared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(radius, radius, radius - 1.5f, artPaint)

        // Hairline specular bezel so the circular disc stands out cleanly against bright or dark wallpaper
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x33FFFFFF
        }
        canvas.drawCircle(radius, radius, radius - 2f, rimPaint)
        if (prepared !== src) prepared.recycle()
        return out
    }

    fun createRoundedAppIcon(context: Context, cornerRadiusPx: Float, targetSize: Int = 220): Bitmap {
        val raw = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        icon.setBounds(0, 0, targetSize, targetSize)
        icon.draw(c)
        val rounded = createRoundedBitmap(raw, cornerRadiusPx, targetSize)
        raw.recycle()
        return rounded
    }

    fun createPlaylistTileBitmap(
        context: Context,
        title: String,
        type: String,
        coverBitmap: Bitmap?,
        targetSize: Int = 168,
        cornerRadiusPx: Float = 30f
    ): Bitmap {
        if (coverBitmap != null) {
            return createRoundedBitmap(coverBitmap, cornerRadiusPx, targetSize)
        }

        val (startColor, endColor) = when (type) {
            "liked" -> 0xFFEC407A.toInt() to 0xFF8E24AA.toInt()
            "most_played" -> 0xFFFF7043.toInt() to 0xFFD84315.toInt()
            "recent" -> 0xFF26A69A.toInt() to 0xFF00695C.toInt()
            else -> {
                val palettes = listOf(
                    0xFF5C6BC0.toInt() to 0xFF283593.toInt(),
                    0xFF7E57C2.toInt() to 0xFF4527A0.toInt(),
                    0xFF42A5F5.toInt() to 0xFF1565C0.toInt(),
                    0xFFAB47BC.toInt() to 0xFF6A1B9A.toInt()
                )
                palettes[abs(title.hashCode()) % palettes.size]
            }
        }

        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, targetSize.toFloat(), targetSize.toFloat(),
                startColor, endColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // Subtle highlight orb
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
        canvas.drawCircle(targetSize * 0.82f, targetSize * 0.2f, targetSize * 0.34f, orbPaint)

        val iconRes = when (type) {
            "liked" -> R.drawable.ic_widget_heart_nav
            "most_played" -> R.drawable.ic_widget_chart
            else -> R.drawable.ic_widget_music_note
        }
        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (type == "custom" || type == "online") {
            val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = targetSize * 0.40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val y = (targetSize / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(initial, targetSize / 2f, y, textPaint)
        } else if (drawable != null) {
            DrawableCompat.setTint(drawable, Color.WHITE)
            val pad = (targetSize * 0.28f).toInt()
            drawable.setBounds(pad, pad, targetSize - pad, targetSize - pad)
            drawable.draw(canvas)
        }
        return out
    }

    fun openMainPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun openInsightsPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            101,
            Intent(context, MainActivity::class.java).apply {
                action = WidgetConstants.ACTION_OPEN_INSIGHTS
                data = Uri.parse("lytrix://widget/insights")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun openPlaylistPendingIntent(
        context: Context,
        requestCode: Int,
        type: String,
        id: String
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                action = WidgetConstants.ACTION_OPEN_PLAYLIST
                data = Uri.parse("lytrix://widget/playlist/$type/${Uri.encode(id)}")
                putExtra(WidgetConstants.EXTRA_PLAYLIST_TYPE, type)
                putExtra(WidgetConstants.EXTRA_PLAYLIST_ID, id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun broadcastPendingIntent(
        context: Context,
        requestCode: Int,
        actionName: String
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, CapsuleDeckWidgetReceiver::class.java).apply {
                action = actionName
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
