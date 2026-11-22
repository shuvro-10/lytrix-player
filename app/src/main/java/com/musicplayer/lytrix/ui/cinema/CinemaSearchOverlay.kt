package com.musicplayer.lytrix.ui.cinema

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val QUICK_GENRE_TAGS = listOf(
    "Action", "Sci-Fi", "Horror", "Comedy", "Bollywood",
    "K-Drama", "Romance", "Mystery", "Animation", "Thriller",
    "Marvel", "Anime"
)

/** Live search: debounced 300 ms, filter chips, 3-column poster grid. */
@Composable
fun CinemaSearchOverlay(
    repository: CinemaRepository,
    onClose: () -> Unit,
    onItemClick: (CinemaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val accent = cinemaAccent()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CinemaFilter.ALL) }
    var results by remember { mutableStateOf<List<CinemaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(query) {
        failed = false
        if (query.isBlank()) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(300) // debounce: a new keystroke cancels this effect
        cinemaCatching { repository.search(query.trim()) }
            .onSuccess { results = it }
            .onFailure {
                failed = true
                results = emptyList()
            }
        isLoading = false
    }

    val visible = remember(results, filter) { results.filter { filter.matches(it) } }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier
            .fillMaxSize()
            .background(CinemaPalette.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
                placeholder = { Text("Search movies, series, anime", color = CinemaPalette.textSecondary) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = CinemaPalette.textSecondary)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = CinemaPalette.textSecondary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CinemaPalette.surface,
                    unfocusedContainerColor = CinemaPalette.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accent,
                ),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            items(CinemaFilter.entries, key = { it.name }) { f ->
                CinemaChip(label = f.searchLabel, selected = f == filter, onClick = { filter = f })
            }
        }

        // Quick Tag / Genre Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(QUICK_GENRE_TAGS) { tag ->
                val isSelected = query.equals(tag, ignoreCase = true)
                Surface(
                    onClick = {
                        query = if (isSelected) "" else tag
                        focusManager.clearFocus()
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) accent.copy(alpha = 0.25f) else CinemaPalette.surface,
                    border = BorderStroke(1.dp, if (isSelected) accent else CinemaPalette.border),
                ) {
                    Text(
                        text = "#$tag",
                        color = if (isSelected) accent else CinemaPalette.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                query.isBlank() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Explore Popular Tags & Genres",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap any tag below to find top-rated movies & series",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaPalette.textSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(QUICK_GENRE_TAGS) { tag ->
                            Surface(
                                onClick = {
                                    query = tag
                                    focusManager.clearFocus()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = CinemaPalette.surface,
                                border = BorderStroke(1.dp, CinemaPalette.border),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#$tag",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accent,
                )

                failed -> CinemaMessage(
                    text = "Search failed. Check your connection and try again.",
                    modifier = Modifier.align(Alignment.Center),
                )

                visible.isEmpty() -> CinemaMessage(
                    text = "No results for “${query.trim()}”.",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(visible, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            onClick = {
                                focusManager.clearFocus()
                                onItemClick(item)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
