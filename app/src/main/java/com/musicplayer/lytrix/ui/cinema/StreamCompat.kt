package com.musicplayer.lytrix.ui.cinema

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * What THIS phone can decode in hardware (queried once, lazily).
 * Software decoders (OMX.google / c2.android) are ignored on purpose:
 * they are too slow for 1080p HEVC on mid-range chips.
 * If the query itself fails we assume "supported" so we never wrongly hide a stream.
 */
object DeviceCodecSupport {
    val h264: Boolean by lazy { hasHardwareDecoder("video/avc") }
    val hevc: Boolean by lazy { hasHardwareDecoder("video/hevc") }
    val av1: Boolean by lazy { hasHardwareDecoder("video/av01") }
    val hevcMain10: Boolean by lazy {
        hasHardwareDecoder("video/hevc") { p ->
            p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
        }
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            info.isHardwareAccelerated
        } else {
            !(info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android."))
        }

    private fun hasHardwareDecoder(mime: String, profileOk: (Int) -> Boolean = { true }): Boolean = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            runCatching {
                !info.isEncoder &&
                    isHardware(info) &&
                    info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                    info.getCapabilitiesForType(mime).profileLevels.any { profileOk(it.profile) }
            }.getOrDefault(false)
        }
    } catch (_: Throwable) {
        true
    }
}

/** [playableInApp]: true = should work, false = will fail, null = can't tell from the title. */
data class StreamCompat(val playableInApp: Boolean?, val badge: String?) {
    /** Higher = show earlier in the list. */
    val rank: Int
        get() = when (playableInApp) {
            true -> 2
            null -> 1
            false -> 0
        }
}

private val HEVC_RE = Regex("""(?<![a-z0-9])(x265|h\.?265|hevc)(?![a-z0-9])""", RegexOption.IGNORE_CASE)
private val H264_RE = Regex("""(?<![a-z0-9])(x264|h\.?264|avc)(?![a-z0-9])""", RegexOption.IGNORE_CASE)
private val AV1_RE = Regex("""(?<![a-z0-9])av1(?![a-z0-9])""", RegexOption.IGNORE_CASE)
private val TEN_BIT_RE = Regex(
    """(10[\s._-]?bit|hi10|main[\s._-]?10|(?<![a-z0-9])hdr(10)?\+?(?![a-z0-9])|dolby[\s._-]?vision|(?<![a-z0-9])dv(?![a-z0-9]))""",
    RegexOption.IGNORE_CASE
)

/** Guesses the video codec from the release name and compares it with this device. */
fun TorrentStream.compat(): StreamCompat {
    val text = "$name $title $filename"
    val isHevc = HEVC_RE.containsMatchIn(text)
    val isAv1 = AV1_RE.containsMatchIn(text)
    val isH264 = H264_RE.containsMatchIn(text)
    val tenBit = TEN_BIT_RE.containsMatchIn(text)
    val hw = DeviceCodecSupport

    return when {
        isAv1 -> if (hw.av1) StreamCompat(true, "AV1 • In-App ✅")
        else StreamCompat(false, "AV1 (Use VLC ↗)")

        isHevc && tenBit -> if (hw.hevcMain10) StreamCompat(true, "HEVC 10-bit • In-App ✅")
        else StreamCompat(false, "10-bit HEVC (Use VLC ↗)")

        // x265 without a bit-depth tag: many 1080p x265 releases are secretly 10-bit
        isHevc -> when {
            hw.hevcMain10 -> StreamCompat(true, "HEVC • In-App ✅")
            hw.hevc -> StreamCompat(null, "HEVC (may need VLC ↗)")
            else -> StreamCompat(false, "HEVC (Use VLC ↗)")
        }

        isH264 -> StreamCompat(true, "H.264 • In-App ✅")
        else -> StreamCompat(null, null)
    }
}

/**
 * Opens [url] in VLC if installed, otherwise lets the user pick any video player.
 * Works for the local http://127.0.0.1 torrent stream.
 */
fun openInExternalPlayer(context: Context, url: String, title: String) {
    val base = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        putExtra("title", title)
    }
    try {
        context.startActivity(
            Intent(base).setPackage("org.videolan.vlc").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent.createChooser(base, "Play with...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, "No video player found (install VLC)", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Checks for countries with strict ISP copyright tracking and legal fines.
 * In these countries, P2P torrent streaming is auto-locked to protect users.
 * Uses dual-layer check: Public IP geolocation (detects VPNs/Wi-Fi) + SIM Country ISO.
 */
object GeoSafety {
    private val STRICT_COUNTRIES = setOf("DE", "AT", "JP", "FR", "IT", "US", "GB", "CA", "AU")

    @Volatile
    var cachedIpCountry: String? = null
        private set

    private val ipClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    suspend fun refreshIpCountry(context: Context? = null) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://1.1.1.1/cdn-cgi/trace")
                .header("User-Agent", "curl/7.68.0")
                .build()
            val resp = ipClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            val loc = Regex("""loc=([A-Z]{2})""").find(text)?.groupValues?.get(1)
            if (!loc.isNullOrBlank()) {
                val upper = loc.uppercase()
                cachedIpCountry = upper
                context?.let { ctx ->
                    ctx.getSharedPreferences("lytrix_cinema_geo", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_ip_country", upper)
                        .apply()
                }
            }
        } catch (_: Throwable) {
            // Silently fall back to cached or SIM
        }
    }

    fun getDeviceCountry(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.trim()?.uppercase()
            if (!simCountry.isNullOrBlank()) return simCountry

            val networkCountry = tm?.networkCountryIso?.trim()?.uppercase()
            if (!networkCountry.isNullOrBlank()) return networkCountry

            Locale.getDefault().country.trim().uppercase()
        } catch (_: Throwable) {
            Locale.getDefault().country.trim().uppercase()
        }
    }

    fun getDetectedCountry(context: Context): String {
        cachedIpCountry?.let { return it }
        val pref = context.getSharedPreferences("lytrix_cinema_geo", Context.MODE_PRIVATE)
            .getString("cached_ip_country", null)
        if (!pref.isNullOrBlank()) return pref
        return getDeviceCountry(context)
    }

    fun isStrictCountry(context: Context): Boolean {
        val ip = cachedIpCountry ?: context.getSharedPreferences("lytrix_cinema_geo", Context.MODE_PRIVATE)
            .getString("cached_ip_country", null)
        if (ip != null && STRICT_COUNTRIES.contains(ip)) {
            return true
        }
        val sim = getDeviceCountry(context)
        return STRICT_COUNTRIES.contains(sim)
    }

    fun getCountryName(countryCode: String): String {
        return when (countryCode) {
            "DE" -> "Germany"
            "AT" -> "Austria"
            "JP" -> "Japan"
            "FR" -> "France"
            "IT" -> "Italy"
            "US" -> "United States"
            "GB" -> "United Kingdom"
            "CA" -> "Canada"
            "AU" -> "Australia"
            else -> countryCode
        }
    }
}

/**
 * Checks whether user is on Cellular/Mobile Data or Wi-Fi.
 */
object NetworkUtils {
    fun isCellular(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo ?: return false
                @Suppress("DEPRECATION")
                info.type == ConnectivityManager.TYPE_MOBILE
            }
        } catch (_: Throwable) {
            false
        }
    }
}
