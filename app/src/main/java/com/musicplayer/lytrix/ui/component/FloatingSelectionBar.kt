package com.musicplayer.lytrix.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🌟 FloatingSelectionBar — প্রিমিয়াম ফ্লোটিং পিল সিলেকশন বার।
 * ইউজার যখন কোনো গান বা ভিডিওতে লং প্রেস করে সিলেকশন মোডে প্রবেশ করে,
 * তখন স্ক্রিনের শীর্ষে এই ফ্লুয়েন্ট ক্যাপসুল বারটি স্মুথ অ্যানিমেশনের সাথে ভেসে ওঠে।
 */
@Composable
fun FloatingSelectionBar(
    selectedCount: Int,
    onCloseClick: () -> Unit,
    onRenameClick: (() -> Unit)? = null,
    onHideClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onAddToPlaylistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(52.dp)
            .shadow(elevation = 12.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF1B1E26))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ১. সার্কুলার ক্লোজ বাটন
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF282D38))
                .clickable(onClick = onCloseClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Selection",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // ২. সিলেকশন কাউন্ট টেক্সট
        Text(
            text = "$selectedCount selected",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.weight(1f))

        // ৩. প্রথম ডিভাইডার বার
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.20f))
        )

        Spacer(modifier = Modifier.width(2.dp))

        // ৪. এডিট / রিনেম আইকন (যদি ১টি আইটেম সিলেক্ট করা থাকে)
        if (onRenameClick != null && selectedCount == 1) {
            IconButton(
                onClick = onRenameClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Rename",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // ৫. হাইড আইকন
        IconButton(
            onClick = onHideClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = "Hide",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(19.dp)
            )
        }

        // ৬. ডিলিট আইকন (সফট রেড / কোরাল রঙে হাইলাইট করা)
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // ৭. দ্বিতীয় ডিভাইডার বার
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.20f))
        )

        Spacer(modifier = Modifier.width(2.dp))

        // ৮. শেয়ার আইকন
        IconButton(
            onClick = onShareClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(19.dp)
            )
        }

        // ৯. অ্যাড টু প্লেলিস্ট আইকন (যদি গান সেকশন হয়)
        if (onAddToPlaylistClick != null) {
            IconButton(
                onClick = onAddToPlaylistClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to playlist",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
