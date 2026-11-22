package com.musicplayer.lytrix.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.musicplayer.lytrix.R

data class AppIconOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val aliasClassName: String,
    val previewBg: Color,
    val previewFg: Color,
    @DrawableRes val previewDrawableRes: Int,
    val isDefault: Boolean = false
)

/**
 * Lytrix-inspired dynamic launcher icon switcher backed by `<activity-alias>` components
 * in `AndroidManifest.xml`.
 */
object AppIconManager {
    private const val PREF_NAME = "app_preferences"
    private const val KEY_SELECTED_ICON_ID = "selected_app_icon_id"

    val options: List<AppIconOption> = listOf(
        AppIconOption(
            id = "default",
            title = "Lytrix Classic",
            subtitle = "Signature adaptive icon",
            aliasClassName = "com.musicplayer.lytrix.launcher.DefaultIconAlias",
            previewBg = Color(0xFF1F2B24),
            previewFg = Color(0xFF3DDC84),
            previewDrawableRes = R.mipmap.ic_launcher,
            isDefault = true
        ),
        AppIconOption(
            id = "neon",
            title = "Neon Pulse",
            subtitle = "Electric cyberpunk wave",
            aliasClassName = "com.musicplayer.lytrix.launcher.NeonIconAlias",
            previewBg = Color(0xFF0E0B26),
            previewFg = Color(0xFF00E5FF),
            previewDrawableRes = R.drawable.ic_icon_neon_fg
        ),
        AppIconOption(
            id = "mono",
            title = "Obsidian Mono",
            subtitle = "Clean monochrome stealth",
            aliasClassName = "com.musicplayer.lytrix.launcher.MonoIconAlias",
            previewBg = Color(0xFF111114),
            previewFg = Color(0xFFF5F5F7),
            previewDrawableRes = R.drawable.ic_icon_mono_fg
        ),
        AppIconOption(
            id = "vinyl",
            title = "Vinyl Studio",
            subtitle = "Warm analog gold record",
            aliasClassName = "com.musicplayer.lytrix.launcher.VinylIconAlias",
            previewBg = Color(0xFF1D1510),
            previewFg = Color(0xFFFFC145),
            previewDrawableRes = R.drawable.ic_icon_vinyl_fg
        ),
        AppIconOption(
            id = "sakura",
            title = "Sakura Bloom",
            subtitle = "Soft blossom pastel aura",
            aliasClassName = "com.musicplayer.lytrix.launcher.SakuraIconAlias",
            previewBg = Color(0xFF281320),
            previewFg = Color(0xFFFF82B2),
            previewDrawableRes = R.drawable.ic_icon_sakura_fg
        )
    )

    fun getCurrentIcon(context: Context): AppIconOption {
        val pm = context.packageManager
        options.firstOrNull { opt ->
            pm.getComponentEnabledSetting(ComponentName(context.packageName, opt.aliasClassName)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }?.let { return it }

        val storedId = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_ICON_ID, "default")
        return options.firstOrNull { it.id == storedId } ?: options.first()
    }

    fun applyIcon(context: Context, iconId: String): Boolean {
        val target = options.firstOrNull { it.id == iconId } ?: return false
        val pm = context.packageManager
        val pkg = context.packageName

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.setComponentEnabledSettings(
                    options.map { option ->
                        PackageManager.ComponentEnabledSetting(
                            ComponentName(pkg, option.aliasClassName),
                            if (option.id == target.id) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            PackageManager.DONT_KILL_APP
                        )
                    }
                )
            } else {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, target.aliasClassName),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                options.filter { it.id != target.id }.forEach { other ->
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, other.aliasClassName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ICON_ID, target.id)
                .apply()
            true
        }.getOrDefault(false)
    }
}
