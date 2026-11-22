package com.musicplayer.lytrix.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerThemeBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    var savedMainTheme by remember { mutableStateOf(sharedPrefs.getString("main_player_theme", "Solid Default") ?: "Solid Default") }
    var savedFloatingTheme by remember { mutableStateOf(sharedPrefs.getString("selected_theme", "Cartoon") ?: "Cartoon") }

    var previewMainTheme by remember { mutableStateOf(savedMainTheme) }
    var previewFloatingTheme by remember { mutableStateOf(savedFloatingTheme) }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Main Player", "Floating Player")
    val accentColor = Color(0xFF00E676)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxSize(), // 🔥 ফুল স্ক্রিন থিম হাব
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                width = 36.dp,
                height = 4.dp
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Personalization",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = accentColor,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = accentColor
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            val mainThemes = listOf("Solid Default", "Dynamic Blur", "Glassmorphism", "Vinyl Record", "Neon Gradient")
            val floatingThemes = listOf("None (Classic)", "Cartoon", "Ocean Wave", "Neon Gradient")
            val activeList = if (selectedTabIndex == 0) mainThemes else floatingThemes
            val currentPreview = if (selectedTabIndex == 0) previewMainTheme else previewFloatingTheme

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(activeList) { theme ->
                    ThemeGridItem(
                        themeName = theme,
                        isSelected = currentPreview == theme,
                        isMainPlayer = selectedTabIndex == 0,
                        onClick = {
                            if (selectedTabIndex == 0) previewMainTheme = theme
                            else previewFloatingTheme = theme
                        },
                        accentColor = accentColor
                    )
                }
            }

            // Apply Button (Floating style)
            val isChanged = if (selectedTabIndex == 0) previewMainTheme != savedMainTheme else previewFloatingTheme != savedFloatingTheme
            if (isChanged) {
                Button(
                    onClick = {
                        if (selectedTabIndex == 0) sharedPrefs.edit().putString("main_player_theme", previewMainTheme).apply()
                        else sharedPrefs.edit().putString("selected_theme", previewFloatingTheme).apply()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Apply Changes", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ThemeGridItem(themeName: String, isSelected: Boolean, isMainPlayer: Boolean, onClick: () -> Unit, accentColor: Color) {
    Column(
        modifier = Modifier.padding(10.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(20.dp))
                .border(if (isSelected) 3.dp else 1.dp, if (isSelected) accentColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            // 🔥 থিম প্রিভিউ মকআপ জেনারেটর
            if (isMainPlayer) {
                MainPlayerMockup(themeName, accentColor)
            } else {
                FloatingWidgetMockup(themeName, accentColor)
            }

            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(40.dp))
            }
        }
        Text(themeName, color = if (isSelected) accentColor else Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MainPlayerMockup(themeName: String, accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        when (themeName) {
            "Solid Default" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray))
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.width(60.dp).height(6.dp).background(Color.Gray))
                    Spacer(modifier = Modifier.height(20.dp))
                    Icon(Icons.Default.PlayArrow, null, tint = accentColor)
                }
            }
            "Dynamic Blur" -> {
                Box(modifier = Modifier.fillMaxSize().blur(10.dp).background(Brush.radialGradient(listOf(accentColor.copy(alpha=0.4f), Color.Black))))
                Icon(Icons.Default.PlayArrow, null, tint = accentColor, modifier = Modifier.align(Alignment.Center))
            }
            "Glassmorphism" -> {
                Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF6A11CB), Color(0xFF2575FC)))))
                Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=0.2f)).border(1.dp, Color.White.copy(alpha=0.3f), RoundedCornerShape(16.dp)))
            }
            "Vinyl Record" -> {
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawCircle(Color.Black)
                    drawCircle(Color.White.copy(alpha=0.1f), style = Stroke(2f))
                    drawCircle(accentColor, radius = 15.dp.toPx())
                }
            }
            "Neon Gradient" -> {
                Box(modifier = Modifier.fillMaxSize().background(Brush.sweepGradient(listOf(Color(0xFFFF007F), Color(0xFF00F2FE), Color(0xFFFF007F)))))
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun FloatingWidgetMockup(themeName: String, accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val widgetBg = when(themeName) {
            "Neon Gradient" -> Brush.linearGradient(listOf(Color(0xFF240046), Color(0xFFFF007F)))
            "Ocean Wave" -> Brush.verticalGradient(listOf(Color(0xFF0083B0), Color(0xFF00B4DB)))
            else -> Brush.linearGradient(listOf(Color.Black, Color.DarkGray))
        }

        Row(
            modifier = Modifier.width(120.dp).height(35.dp).clip(CircleShape).background(widgetBg).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Gray))
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.width(40.dp).height(4.dp).background(Color.White.copy(alpha=0.5f)))
        }
    }
}