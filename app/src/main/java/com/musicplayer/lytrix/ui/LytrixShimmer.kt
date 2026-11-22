package com.musicplayer.lytrix.ui

import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 🔥 Lytrix Premium Shimmer Engine
 * SimpMusic ও Meld এর মতো ফ্লুইড, স্মুথ লাইট-ওয়েভ অ্যানিমেশন।
 */
fun Modifier.shimmer(
    baseColor: Color = Color(0xFF1E1E1E),
    highlightColor: Color = Color(0xFF333333),
    durationMillis: Int = 1100
): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "LytrixShimmer")

    val startOffsetX by transition.animateFloat(
        initialValue = -2f * (size.width.toFloat().coerceAtLeast(100f)),
        targetValue = 2f * (size.width.toFloat().coerceAtLeast(100f)),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis)
        ),
        label = "LytrixShimmerAnim"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat().coerceAtLeast(100f), size.height.toFloat().coerceAtLeast(100f))
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

/**
 * 🎵 Lytrix ১:১ গানের রো শিমার (Song Item Skeleton)
 * PremiumOnlineSongItem, VerticalYtSongListSection এবং Search Result-এর মাপ অনুযায়ী হুবহু ৫৬dp।
 */
@Composable
fun LytrixSongItemShimmer(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF1E1E1E),
    highlightColor: Color = Color(0xFF333333)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail: 56.dp with 10.dp rounded corner
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .shimmer(baseColor, highlightColor)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Title placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer(baseColor, highlightColor)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle/Artist placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .shimmer(baseColor, highlightColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // More options button placeholder
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .shimmer(baseColor, highlightColor)
        )
    }
}

/**
 * 📑 গানের লিস্ট ভিউ শিমার (Vertical List Shimmer)
 * সার্চ রেজাল্ট, পডকাস্ট, অডিওবুক ইত্যাদিতে স্পিনারের বদলে দেখাবে।
 */
@Composable
fun LytrixSongListShimmer(
    itemCount: Int = 8,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        repeat(itemCount) {
            LytrixSongItemShimmer()
        }
    }
}

/**
 * 🎴 অনুভূমিক কার্ড শিমার (Horizontal Card Section Shimmer)
 * Lytrix-এর HorizontalSongSection-এর হুবহু ১৩৫dp কার্ড অনুযায়ী নির্মিত।
 */
@Composable
fun LytrixHorizontalCardSectionShimmer(
    cardCount: Int = 5,
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF1E1E1E),
    highlightColor: Color = Color(0xFF333333)
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        // Section Title Placeholder
        Box(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 12.dp)
                .width(160.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer(baseColor, highlightColor)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) {
            items(cardCount) {
                Column(modifier = Modifier.width(135.dp)) {
                    // Card Box: 135.dp with 12.dp corner radius
                    Box(
                        modifier = Modifier
                            .size(135.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmer(baseColor, highlightColor)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .shimmer(baseColor, highlightColor)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmer(baseColor, highlightColor)
                    )
                }
            }
        }
    }
}

/**
 * 🏠 অনলাইন হোম স্ক্রিনের ফুল শিমার (Online Home Skeleton)
 * হোম পেজ লোড হওয়ার সময় কোনো স্পিনার ছাড়া সম্পূর্ণ পেজের প্রিমিয়াম স্কেলেটন দেখাবে।
 */
@Composable
fun LytrixOnlineHomeShimmer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Top Pills / Chips placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) { idx ->
                Box(
                    modifier = Modifier
                        .width(if (idx == 0) 70.dp else if (idx == 1) 95.dp else 80.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .shimmer()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Row 1 (e.g. Mixed for you / Quick Picks)
        LytrixHorizontalCardSectionShimmer()

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Row 2 (e.g. Trending Playlists)
        LytrixHorizontalCardSectionShimmer()

        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Quick Songs Section
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 8.dp)
                    .width(140.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer()
            )
            repeat(3) {
                LytrixSongItemShimmer()
            }
        }
    }
}

/**
 * 💿 ইউটিউব প্লেলিস্ট / অ্যালবাম ডিটেইল স্ক্রিনের ফুল শিমার
 * YtPlaylistDetailScreen লোড হওয়ার সময় দেখাবে।
 */
@Composable
fun LytrixPlaylistDetailShimmer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Big Cover Image placeholder (200.dp x 200.dp)
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .shimmer()
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Playlist Title Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Playlist Subtitle Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(14.dp)
                .clip(RoundedCornerShape(5.dp))
                .shimmer()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Play and Shuffle Button placeholders
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .shimmer()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Songs inside playlist
        repeat(5) {
            LytrixSongItemShimmer(modifier = Modifier.padding(horizontal = 0.dp))
        }
    }
}

/**
 * 🎤 আর্টিস্ট প্রোফাইল স্ক্রিনের ফুল শিমার
 * ArtistProfileScreen লোড হওয়ার সময় দেখাবে।
 */
@Composable
fun LytrixArtistProfileShimmer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Hero image shimmer (350.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .shimmer()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Play & Subscribe Buttons Shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .shimmer()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Song item rows placeholder
        repeat(5) {
            LytrixSongItemShimmer()
        }
    }
}

