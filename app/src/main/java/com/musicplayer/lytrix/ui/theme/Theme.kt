package com.musicplayer.lytrix.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import com.musicplayer.lytrix.db.ThemePreferencesManager
import com.musicplayer.lytrix.ui.AppLanguage
import com.musicplayer.lytrix.ui.LocalAppLanguage

val LocalAccentColor = staticCompositionLocalOf { Color(0xFF3182CE) }
val LocalCustomBackgroundUri = staticCompositionLocalOf { "" }
val LocalGridViewSize = staticCompositionLocalOf { 1 }
val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalIsPureBlack = staticCompositionLocalOf { false }
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/**
 * 🌊 SimpMusic-স্টাইল সফট অ্যান্ড ভেলভেটি রিপল কনফিগারেশন।
 * সাধারণ Material 3-র কর্কশ ধূসর ফ্ল্যাশ সরিয়ে অত্যন্ত মোলায়েম, প্রিমিয়াম টাচ রেসপন্স তৈরি করে।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
val SoftRippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.06f,
        focusedAlpha = 0.04f,
        hoveredAlpha = 0.03f,
        pressedAlpha = 0.04f,
    )
)

/**
 * 🎵 অ্যালবাম আর্ট বা যেকোনো বিটম্যাপ থেকে স্বয়ংক্রিয়ভাবে মেইন থিম কালার এক্সট্র্যাক্ট করে (Meld/Lytrix মেথড)।
 */
fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.firstOrNull() ?: 0xFF3182CE.toInt())
}

/**
 * 🖤 AMOLED ট্রু পিওর ব্ল্যাক (Pure Black) সাপোর্ট।
 * ব্যাকগ্রাউন্ড এবং সারফেস ১০০% কালো (#000000) করে, আর কন্টেইনারগুলোকে ডার্ক কনট্রাস্টে রাখে।
 */
fun ColorScheme.pureBlack(apply: Boolean = true): ColorScheme =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceContainer = Color(0xFF101010),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808),
        surfaceContainerHigh = Color(0xFF161616),
        surfaceContainerHighest = Color(0xFF202020),
    ) else this

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themePrefs = remember { ThemePreferencesManager(context) }

    val appThemeMode by themePrefs.appThemeMode.collectAsState(initial = "Dark")
    val accentColorHex by themePrefs.accentColor.collectAsState(initial = "0xFF3182CE")
    val customBgUri by themePrefs.customBackgroundUri.collectAsState(initial = "")
    val gridViewSize by themePrefs.gridViewSize.collectAsState(initial = 1)
    val languageCode by themePrefs.languageCode.collectAsState(initial = "en")
    val playerTheme by themePrefs.playerTheme.collectAsState(initial = "Solid Default")
    val isPureBlackPref by themePrefs.isPureBlack.collectAsState(initial = false)
    val isDynamicThemePref by themePrefs.isDynamicTheme.collectAsState(initial = false)

    val appLanguage = remember(languageCode) {
        AppLanguage.values().firstOrNull { it.code == languageCode } ?: AppLanguage.ENGLISH
    }

    val darkTheme = when (appThemeMode) {
        "Dark", "AMOLED", "Pure Black" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val isPureBlack = isPureBlackPref || appThemeMode == "AMOLED" || appThemeMode == "Pure Black"

    val accentColor = remember(accentColorHex) {
        try {
            Color(android.graphics.Color.parseColor(accentColorHex.replace("0xFF", "#")))
        } catch (e: Exception) {
            Color(0xFF3182CE)
        }
    }

    val useDynamicSystemColor = (dynamicColor || isDynamicThemePref) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 🎨 গুগলের লেটেস্ট Material 3 টোনাল কালার স্কিম জেনারেটর (MaterialKolor আর্কিটেকচার)
    val baseColorScheme = if (useDynamicSystemColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = accentColor,
            isDark = darkTheme,
            isAmoled = isPureBlack && darkTheme,
            style = PaletteStyle.TonalSpot
        )
    }

    val colorScheme = remember(baseColorScheme, isPureBlack, darkTheme) {
        if (darkTheme) {
            val bg = if (isPureBlack) Color.Black else Color(0xFF070707)
            val surf = if (isPureBlack) Color.Black else Color(0xFF111111)
            baseColorScheme.copy(
                background = bg,
                surface = surf,
                surfaceContainerLowest = if (isPureBlack) Color.Black else Color(0xFF080808),
                surfaceContainerLow = if (isPureBlack) Color(0xFF080808) else Color(0xFF0D0D0D),
                surfaceContainer = if (isPureBlack) Color(0xFF101010) else Color(0xFF141414),
                surfaceContainerHigh = if (isPureBlack) Color(0xFF161616) else Color(0xFF1A1A1A),
                surfaceContainerHighest = if (isPureBlack) Color(0xFF202020) else Color(0xFF222222),
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFB4B4B4),
                outlineVariant = Color.White.copy(alpha = 0.10f),
            )
        } else {
            baseColorScheme
        }
    }

    val isVideoThemeActive = playerTheme != "Solid Default"

    // 🔥 স্ট্যাটাস বার এবং নেভিগেশন বার স্বচ্ছ করার ম্যাজিক কোড 🔥
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            WindowCompat.setDecorFitsSystemWindows(window, false)

            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAppLanguage provides appLanguage,
        LocalAccentColor provides accentColor,
        LocalCustomBackgroundUri provides customBgUri,
        LocalGridViewSize provides gridViewSize,
        LocalIsDarkTheme provides darkTheme,
        LocalIsPureBlack provides isPureBlack,
        LocalLiquidGlassEnabled provides isVideoThemeActive,
        LocalRippleConfiguration provides SoftRippleConfiguration,
    ) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = Typography,
            content = content
        )
    }
}
