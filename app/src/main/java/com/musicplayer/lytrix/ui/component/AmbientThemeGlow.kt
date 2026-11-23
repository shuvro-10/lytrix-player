package com.musicplayer.lytrix.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 🌌 AmbientThemeGlow — SimpMusic আর্কিটেকচারে তৈরি টপ-লেফট ২৫° অ্যাম্বিয়েন্ট ব্যাকগ্রাউন্ড গ্লো।
 * 
 * ১. ওপরের বাম কোণা (Top-Left) থেকে ২৫ ডিগ্রি কোণে রঙটি সফট ডায়াগোনাল গ্রেডিয়েন্টে ছড়িয়ে পড়ে।
 * ২. নিচের অংশে নন-লিনিয়ার কিউবিক স্মুথ স্ক্রিম কার্ভের মাধ্যমে পিচ ব্ল্যাক ওলেড ব্যাকগ্রাউন্ডে বিলীন হয়।
 * ৩. কালার পরিবর্তন হলে ৫০০ মিলিসেকেন্ডে মসৃণভাবে নিঃশ্বাসের মতো (Breathe-in effect) রঙ বদলায়।
 * ৪. ইউজার রিকোয়ারমেন্ট অনুযায়ী সিম্পমিউজিকের চেয়ে আরও হালকা ও মার্জিত (নরম) অপাসিটি রাখা হয়েছে।
 */
@Composable
fun AmbientThemeGlow(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    glowHeight: Dp = 340.dp,
    intensity: Float = 0.22f // সফট এবং মৃদু গ্লো (অতিরিক্ত গাঢ় নয়)
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightTheme = backgroundColor.luminance() > 0.5f

    val targetGlow = when {
        tint == null -> backgroundColor
        isLightTheme -> tint.copy(alpha = 0.16f)
        else -> tint.copy(alpha = intensity)
    }

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlow,
        animationSpec = tween(durationMillis = 500),
        label = "ambientGlowAnimation"
    )

    val fadeOutColor = remember(animatedGlowColor) {
        animatedGlowColor.copy(alpha = 0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(glowHeight)
            .drawBehind {
                // ২৫ ডিগ্রি ডায়াগোনাল অ্যাঙ্গেল: টপ-লেফট থেকে বটম-রাইট
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(animatedGlowColor, fadeOutColor),
                        start = Offset(-size.width * 0.12f, -size.height * 0.12f),
                        end = Offset(size.width * 0.88f, size.height * 0.95f)
                    ),
                    size = size
                )
            }
    ) {
        val transparentBackground = remember(backgroundColor) {
            backgroundColor.copy(alpha = 0f)
        }
        // নিচের অংশে স্মুথ স্ক্রিম ফেড-আউট যাতে নিচে কোনো শক্ত বর্ডার লাইন না থাকে
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeight * 0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = Array(17) { i ->
                            val t = i / 16f
                            // কিউবিক হারমাইট ইন্টারপোলেশন (Cubic hermite curve)
                            val easedT = t * t * (3f - 2f * t)
                            t to lerp(transparentBackground, backgroundColor, easedT)
                        }
                    )
                )
        )
    }
}
