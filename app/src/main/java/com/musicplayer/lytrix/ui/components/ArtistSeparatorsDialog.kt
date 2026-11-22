package com.musicplayer.lytrix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.lytrix.utils.ArtistSeparatorManager
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Users
import compose.icons.tablericons.X

/**
 * Lytrix-style Artist Separators editor dialog allowing custom delimiters like `,`, `&`,
 * `/`, `•`, `feat.`, `ft.` for multi-artist parsing and clickable artist navigation.
 */
@Composable
fun ArtistSeparatorsDialog(
    accentColor: Color,
    onDismiss: () -> Unit,
    onSaved: (List<String>) -> Unit = {}
) {
    val context = LocalContext.current
    val separatorsList = remember {
        mutableStateListOf<String>().apply {
            addAll(ArtistSeparatorManager.loadSeparators(context))
        }
    }
    var showAddSymbolDialog by remember { mutableStateOf(false) }
    var newDelimiterInput by remember { mutableStateOf("") }

    if (showAddSymbolDialog) {
        AlertDialog(
            onDismissRequest = { showAddSymbolDialog = false },
            title = { Text("Add Artist Separator", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newDelimiterInput,
                    onValueChange = { if (it.length <= 10) newDelimiterInput = it },
                    label = { Text("Symbol or keyword (e.g. ;, x, feat.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newDelimiterInput.trim().isNotEmpty(),
                    onClick = {
                        val trimmed = newDelimiterInput.trim()
                        if (trimmed.isNotEmpty() && trimmed !in separatorsList) {
                            separatorsList.add(trimmed)
                        }
                        newDelimiterInput = ""
                        showAddSymbolDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newDelimiterInput = ""
                    showAddSymbolDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TablerIcons.Users, contentDescription = null, tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Artist Separators",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Used to split collaborating artists across player & stats",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val chipItems = remember(separatorsList.toList()) {
                    separatorsList.map { it to true } + ("+" to false)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chipItems.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowItems.forEach { (token, isToken) ->
                                if (isToken) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "\"$token\"",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { separatorsList.remove(token) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    TablerIcons.X,
                                                    contentDescription = "Remove",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { showAddSymbolDialog = true },
                                        shape = RoundedCornerShape(20.dp),
                                        color = accentColor.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                TablerIcons.Plus,
                                                contentDescription = "Add separator",
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Add",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accentColor
                                            )
                                        }
                                    }
                                }
                            }
                            repeat((3 - rowItems.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            separatorsList.clear()
                            separatorsList.addAll(ArtistSeparatorManager.DEFAULT_SEPARATORS)
                        }
                    ) {
                        Text("Reset Defaults", color = accentColor)
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val finalList = separatorsList.toList()
                                ArtistSeparatorManager.saveSeparators(context, finalList)
                                onSaved(finalList)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}
