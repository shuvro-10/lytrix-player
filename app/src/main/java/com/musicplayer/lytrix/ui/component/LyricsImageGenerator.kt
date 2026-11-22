package com.musicplayer.lytrix.ui.component

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicplayer.lytrix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object LyricsImageGenerator {

    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        sharedLines: List<SharedLyricLine> = emptyList(),
        lyrics: String = "",
        aspectRatio: LyricsShareAspectRatio = LyricsShareAspectRatio.Square,
        backgroundColor: Int = 0xFF121212.toInt(),
        backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
        textColor: Int = 0xFFFFFFFF.toInt(),
        secondaryTextColor: Int = 0xB3FFFFFF.toInt(),
    ): Bitmap = withContext(Dispatchers.Default) {
        val imageWidth = aspectRatio.exportWidth
        val imageHeight = aspectRatio.exportHeight

        val bitmap = createBitmap(imageWidth, imageHeight)
        val canvas = Canvas(bitmap)
        val backgroundRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())

        val scale = imageWidth / 360f
        val cornerRadius = 24f * scale
        val cardMargin = 16f * scale
        val cardRect = RectF(
            cardMargin,
            cardMargin,
            imageWidth.toFloat() - cardMargin,
            imageHeight.toFloat() - cardMargin
        )

        // Pre-load cover art if needed
        var coverArtBitmap: Bitmap? = null
        if (!coverArtUrl.isNullOrBlank()) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    coverArtBitmap = result.drawable.toBitmap()
                }
            } catch (_: Exception) {
            }
        }

        // Draw Base Background
        val basePaint = Paint().apply {
            isAntiAlias = true
            color = 0xFF0A0A0C.toInt()
        }
        canvas.drawRect(backgroundRect, basePaint)

        // Clip the inner rounded card
        val cardPath = Path().apply {
            addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(cardPath)

        // Draw Card Background (Solid, Blur, or Gradient)
        val bgPaint = Paint().apply { isAntiAlias = true }
        when (backgroundStyle) {
            LyricsBackgroundStyle.SOLID -> {
                bgPaint.color = backgroundColor
                canvas.drawRect(cardRect, bgPaint)
            }
            LyricsBackgroundStyle.BLUR -> {
                bgPaint.color = 0xFF121212.toInt()
                canvas.drawRect(cardRect, bgPaint)

                if (coverArtBitmap != null) {
                    try {
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            coverArtBitmap,
                            max(1, imageWidth / 10),
                            max(1, imageHeight / 10),
                            true
                        )
                        val blurredBitmap = fastBlur(scaledBitmap, 1f, 22)
                        if (blurredBitmap != null) {
                            canvas.drawBitmap(blurredBitmap, null, cardRect, null)
                            // Contrast scrim overlay
                            val overlayPaint = Paint().apply {
                                color = 0x55000000 // 33% dark overlay
                            }
                            canvas.drawRect(cardRect, overlayPaint)
                        } else {
                            bgPaint.color = backgroundColor
                            canvas.drawRect(cardRect, bgPaint)
                        }
                    } catch (_: Exception) {
                        bgPaint.color = backgroundColor
                        canvas.drawRect(cardRect, bgPaint)
                    }
                } else {
                    bgPaint.color = backgroundColor
                    canvas.drawRect(cardRect, bgPaint)
                }
            }
            LyricsBackgroundStyle.GRADIENT -> {
                if (coverArtBitmap != null) {
                    try {
                        val palette = Palette.from(coverArtBitmap).generate()
                        val vibrant = palette.getVibrantColor(backgroundColor)
                        val darkVibrant = palette.getDarkVibrantColor(0xFF0F0F12.toInt())
                        val gradient = LinearGradient(
                            cardRect.left,
                            cardRect.top,
                            cardRect.right,
                            cardRect.bottom,
                            intArrayOf(vibrant, darkVibrant),
                            null,
                            Shader.TileMode.CLAMP
                        )
                        bgPaint.shader = gradient
                        canvas.drawRect(cardRect, bgPaint)
                    } catch (_: Exception) {
                        bgPaint.color = backgroundColor
                        canvas.drawRect(cardRect, bgPaint)
                    }
                } else {
                    bgPaint.color = backgroundColor
                    canvas.drawRect(cardRect, bgPaint)
                }
            }
        }

        // Inner Card Border (subtle)
        val borderPaint = Paint().apply {
            color = textColor
            alpha = (255 * 0.12f).toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

        val innerPadding = 24f * scale

        // --- Header Section (Cover Art + Title + Artist) ---
        val coverArtSize = 54f * scale
        val coverRect = RectF(
            cardRect.left + innerPadding,
            cardRect.top + innerPadding,
            cardRect.left + innerPadding + coverArtSize,
            cardRect.top + innerPadding + coverArtSize
        )
        val coverRadius = 8f * scale

        if (coverArtBitmap != null) {
            val coverClipPath = Path().apply {
                addRoundRect(coverRect, coverRadius, coverRadius, Path.Direction.CW)
            }
            canvas.withClip(coverClipPath) {
                drawBitmap(coverArtBitmap, null, coverRect, null)
            }
            // Subtle cover border
            val coverBorderPaint = Paint().apply {
                color = textColor
                alpha = (255 * 0.18f).toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1f * scale
                isAntiAlias = true
            }
            canvas.drawRoundRect(coverRect, coverRadius, coverRadius, coverBorderPaint)
        }

        val textStartX = coverRect.right + (14f * scale)
        val textMaxWidth = (cardRect.right - innerPadding - textStartX).toInt().coerceAtLeast(10)

        val titlePaint = TextPaint().apply {
            color = textColor
            textSize = 17f * scale
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val artistPaint = TextPaint().apply {
            color = secondaryTextColor
            textSize = 13.5f * scale
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val titleLayout = StaticLayout.Builder
            .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val artistLayout = StaticLayout.Builder
            .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val headerTextHeight = titleLayout.height + artistLayout.height + (3f * scale)
        val headerCenterY = coverRect.centerY()
        val titleY = headerCenterY - headerTextHeight / 2f

        canvas.save()
        canvas.translate(textStartX, titleY)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height.toFloat() + (3f * scale))
        artistLayout.draw(canvas)
        canvas.restore()

        val headerBottomY = coverRect.bottom

        // --- Footer Section (Lytrix Logo Badge + "Lytrix Player" branding) ---
        val logoBoxSize = 26f * scale
        val logoIconSize = 18f * scale
        val footerY = cardRect.bottom - innerPadding - logoBoxSize

        // Logo circular badge background
        val logoBgPaint = Paint().apply {
            color = secondaryTextColor
            alpha = (255 * 0.18f).toInt()
            isAntiAlias = true
        }
        val logoBoxRect = RectF(
            cardRect.left + innerPadding,
            footerY,
            cardRect.left + innerPadding + logoBoxSize,
            footerY + logoBoxSize
        )
        canvas.drawRoundRect(logoBoxRect, logoBoxSize / 2f, logoBoxSize / 2f, logoBgPaint)

        // Draw Lytrix logo bitmap
        val rawLogo = ContextCompat.getDrawable(context, R.drawable.app_icon)?.toBitmap()
            ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)?.toBitmap()
        rawLogo?.let {
            val logoOffset = (logoBoxSize - logoIconSize) / 2f
            val logoRect = RectF(
                logoBoxRect.left + logoOffset,
                logoBoxRect.top + logoOffset,
                logoBoxRect.right - logoOffset,
                logoBoxRect.bottom - logoOffset
            )
            val logoClip = Path().apply {
                addOval(logoRect, Path.Direction.CW)
            }
            canvas.withClip(logoClip) {
                drawBitmap(it, null, logoRect, null)
            }
        }

        val appName = "Lytrix Player"
        val appNamePaint = TextPaint().apply {
            color = secondaryTextColor
            textSize = 12.5f * scale
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val appNameX = logoBoxRect.right + (8f * scale)
        val appNameY = footerY + logoBoxSize / 2f - (appNamePaint.descent() + appNamePaint.ascent()) / 2f
        canvas.drawText(appName, appNameX, appNameY, appNamePaint)

        // --- Middle Lyrics Section ---
        val lines = if (sharedLines.isNotEmpty()) sharedLines
            else lyrics.lines().filter { it.isNotBlank() }.map { SharedLyricLine(it, null) }

        val lyricsTop = headerBottomY + (16f * scale)
        val lyricsBottom = footerY - (16f * scale)
        val availableLyricsHeight = (lyricsBottom - lyricsTop).coerceAtLeast(10f)
        val availableLyricsWidth = (cardRect.width() - (innerPadding * 2)).toInt().coerceAtLeast(10)

        // Auto-fit font size
        val combinedLength = lines.sumOf { it.original.length + (it.translation?.length ?: 0) }
        var bestFontSize = when {
            combinedLength < 50 -> 24f * scale
            combinedLength < 100 -> 20f * scale
            combinedLength < 200 -> 17f * scale
            combinedLength < 350 -> 14.5f * scale
            else -> 12f * scale
        }
        val minFontSize = 10f * scale

        val lyricsPaint = TextPaint().apply {
            color = textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.005f
        }

        val translationPaint = TextPaint().apply {
            val alpha = (255 * 0.78f).toInt()
            color = (textColor and 0x00FFFFFF) or (alpha shl 24)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.005f
        }

        val lineGap = 8f * scale
        val transGap = 3f * scale
        val origLayouts = mutableListOf<StaticLayout>()
        val transLayouts = mutableListOf<StaticLayout?>()

        while (true) {
            lyricsPaint.textSize = bestFontSize
            translationPaint.textSize = bestFontSize * 0.72f

            origLayouts.clear()
            transLayouts.clear()
            var totalHeight = 0f

            for (i in lines.indices) {
                val line = lines[i]
                val origLayout = StaticLayout.Builder
                    .obtain(line.original, 0, line.original.length, lyricsPaint, availableLyricsWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.2f)
                    .build()
                origLayouts.add(origLayout)
                totalHeight += origLayout.height

                if (!line.translation.isNullOrBlank()) {
                    val transLayout = StaticLayout.Builder
                        .obtain(line.translation, 0, line.translation.length, translationPaint, availableLyricsWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.2f)
                        .build()
                    transLayouts.add(transLayout)
                    totalHeight += transGap + transLayout.height
                } else {
                    transLayouts.add(null)
                }

                if (i < lines.size - 1) {
                    totalHeight += lineGap
                }
            }

            if (totalHeight <= availableLyricsHeight || bestFontSize <= minFontSize) {
                break
            }
            bestFontSize -= 0.8f * scale
        }

        var totalCalculatedHeight = 0f
        for (i in origLayouts.indices) {
            totalCalculatedHeight += origLayouts[i].height
            val t = transLayouts[i]
            if (t != null) {
                totalCalculatedHeight += transGap + t.height
            }
            if (i < origLayouts.size - 1) {
                totalCalculatedHeight += lineGap
            }
        }

        val lyricsStartX = cardRect.left + innerPadding
        var currentY = lyricsTop + (availableLyricsHeight - totalCalculatedHeight) / 2f

        for (i in origLayouts.indices) {
            val origLayout = origLayouts[i]
            canvas.save()
            canvas.translate(lyricsStartX, currentY)
            origLayout.draw(canvas)
            canvas.restore()
            currentY += origLayout.height

            val transLayout = transLayouts[i]
            if (transLayout != null) {
                currentY += transGap
                canvas.save()
                canvas.translate(lyricsStartX, currentY)
                transLayout.draw(canvas)
                canvas.restore()
                currentY += transLayout.height
            }

            if (i < origLayouts.size - 1) {
                currentY += lineGap
            }
        }

        canvas.restore() // Restore after card clip

        bitmap
    }

    suspend fun saveBitmapAsFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lytrix Player")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create MediaStore entry in Pictures/Lytrix Player")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "lyrics_exports")
            cachePath.mkdirs()
            val file = File(cachePath, "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                file
            )
        }
    }

    private fun fastBlur(sentBitmap: Bitmap, scale: Float, radius: Int): Bitmap? {
        var width = (sentBitmap.width * scale).toInt()
        var height = (sentBitmap.height * scale).toInt()
        if (width < 1 || height < 1) return null

        val bitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        yi = 0
        yw = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                p = x + min(y + r1, hm) * w
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
