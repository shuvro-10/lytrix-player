package com.musicplayer.lytrix.eq.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.lytrix.eq.data.AutoEqEntry
import com.musicplayer.lytrix.eq.data.GitHubAutoEqSearch
import com.musicplayer.lytrix.eq.data.SavedEQProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AutoEqWizardStep {
    MODEL_SELECTION,
    VARIANT_SELECTION
}

private data class AutoEqVariantItem(
    val id: String,
    val entry: AutoEqEntry,
    val isAnc: Boolean,
    val isPadModified: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqAutoEqWizardDialog(
    accentColor: Color,
    onProfilesReady: (List<SavedEQProfile>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoEqSearch = remember(context) { GitHubAutoEqSearch(context) }

    var step by remember { mutableStateOf(AutoEqWizardStep.MODEL_SELECTION) }
    var isIndexReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var groupedModels by remember { mutableStateOf<Map<String, List<AutoEqEntry>>>(emptyMap()) }
    var selectedModelName by remember { mutableStateOf<String?>(null) }
    var variants by remember { mutableStateOf<List<AutoEqVariantItem>>(emptyList()) }
    var selectedVariantIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadDatabase(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            val ok = autoEqSearch.buildIndex(forceRefresh = forceRefresh)
            isLoading = false
            if (ok) {
                isIndexReady = true
                groupedModels = autoEqSearch.searchModels(searchQuery)
            } else {
                errorMessage = "Could not load AutoEq database. Check your internet connection and retry."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (autoEqSearch.isDatabaseCached()) {
            loadDatabase(false)
        }
    }

    LaunchedEffect(searchQuery, isIndexReady) {
        if (!isIndexReady) return@LaunchedEffect
        delay(220L)
        groupedModels = autoEqSearch.searchModels(searchQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = {
                            Text(
                                text = when (step) {
                                    AutoEqWizardStep.MODEL_SELECTION -> "AutoEq Headphone Search"
                                    AutoEqWizardStep.VARIANT_SELECTION -> selectedModelName ?: "Select Measurement"
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (step == AutoEqWizardStep.VARIANT_SELECTION) {
                                        step = AutoEqWizardStep.MODEL_SELECTION
                                        variants = emptyList()
                                        selectedVariantIds = emptySet()
                                    } else {
                                        onDismiss()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (step == AutoEqWizardStep.VARIANT_SELECTION) {
                                        Icons.AutoMirrored.Rounded.ArrowBack
                                    } else {
                                        Icons.Rounded.Close
                                    },
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            if (isIndexReady && step == AutoEqWizardStep.MODEL_SELECTION) {
                                IconButton(onClick = { loadDatabase(forceRefresh = true) }, enabled = !isLoading) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh index")
                                }
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AnimatedContent(targetState = step, label = "autoeq_step") { currentStep ->
                        when (currentStep) {
                            AutoEqWizardStep.MODEL_SELECTION -> {
                                if (!isIndexReady) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(color = accentColor)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Downloading AutoEq headphone database...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Headphones,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(56.dp)
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = "Tune for Your Headphones",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Download measurement presets from GitHub AutoEq (Sony, KZ, Moondrop, JBL, Samsung, AirPods & 4000+ more).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Button(
                                                onClick = { loadDatabase(false) },
                                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                            ) {
                                                Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Download Database")
                                            }
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            label = { Text("Search headphone / IEM model") },
                                            placeholder = { Text("e.g., Sony WH-1000XM4, KZ ZSN, Galaxy Buds") },
                                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                            trailingIcon = {
                                                if (searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchQuery = "" }) {
                                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (groupedModels.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (searchQuery.isBlank()) "Type a brand or model (e.g. Sony, KZ, Moondrop, JBL)" else "No matching headphones found — try shorter keywords or Import .txt",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else {
                                            if (searchQuery.isBlank()) {
                                                Text(
                                                    text = "Showing first 100 alphabetically — type above to search all 4,000+ AutoEq curves",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                            }
                                            val modelEntries = remember(groupedModels) { groupedModels.entries.toList() }
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                contentPadding = PaddingValues(bottom = 16.dp)
                                            ) {
                                                items(modelEntries, key = { it.key }) { (modelName, modelVariants) ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                selectedModelName = modelName
                                                                val allForModel = autoEqSearch.getVariantsForModel(modelName)
                                                                val mapped = allForModel.mapIndexed { idx, entry ->
                                                                    val lbl = entry.label
                                                                    AutoEqVariantItem(
                                                                        id = "${modelName.lowercase().replace(" ", "_")}_$idx",
                                                                        entry = entry,
                                                                        isAnc = lbl.contains("ANC", ignoreCase = true),
                                                                        isPadModified = lbl.contains("velour", ignoreCase = true) ||
                                                                            (lbl.contains("pad", ignoreCase = true) && !lbl.contains("(sample", ignoreCase = true))
                                                                    )
                                                                }
                                                                variants = mapped
                                                                selectedVariantIds = mapped.firstOrNull()?.let { setOf(it.id) } ?: emptySet()
                                                                step = AutoEqWizardStep.VARIANT_SELECTION
                                                            },
                                                        shape = RoundedCornerShape(14.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                        )
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.GraphicEq,
                                                                contentDescription = null,
                                                                tint = accentColor
                                                            )
                                                            Spacer(modifier = Modifier.width(14.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = modelName,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "${modelVariants.size} measurement${if (modelVariants.size > 1) "s" else ""} available",
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AutoEqWizardStep.VARIANT_SELECTION -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        items(variants, key = { it.id }) { item ->
                                            val checked = selectedVariantIds.contains(item.id)
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedVariantIds = if (checked) {
                                                            selectedVariantIds - item.id
                                                        } else {
                                                            selectedVariantIds + item.id
                                                        }
                                                    },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (checked) {
                                                        accentColor.copy(alpha = 0.16f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    }
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = {
                                                            selectedVariantIds = if (checked) {
                                                                selectedVariantIds - item.id
                                                            } else {
                                                                selectedVariantIds + item.id
                                                            }
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.entry.label,
                                                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "Source: ${item.entry.source} · Rig: ${item.entry.rig} (${item.entry.form})",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                errorMessage = null
                                                val chosen = variants.filter { selectedVariantIds.contains(it.id) }
                                                val savedList = mutableListOf<SavedEQProfile>()
                                                for (variant in chosen) {
                                                    val eq = autoEqSearch.loadEQ(variant.entry)
                                                    if (eq != null) {
                                                        savedList.add(
                                                            SavedEQProfile(
                                                                id = "autoeq_${variant.entry.source}_${variant.entry.formDirectory}_${variant.entry.label}".hashCode().toString(),
                                                                name = variant.entry.label,
                                                                deviceModel = variant.entry.label,
                                                                bands = eq.bands,
                                                                preamp = eq.preamp,
                                                                source = variant.entry.source,
                                                                rig = variant.entry.rig,
                                                                isCustom = false
                                                            )
                                                        )
                                                    }
                                                }
                                                isLoading = false
                                                if (savedList.isNotEmpty()) {
                                                    onProfilesReady(savedList)
                                                    onDismiss()
                                                } else {
                                                    errorMessage = "Failed to download the selected AutoEq profile."
                                                }
                                            }
                                        },
                                        enabled = selectedVariantIds.isNotEmpty() && !isLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .height(50.dp)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = Color.Black,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Save & Apply Profile", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Snackbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            action = {
                                TextButton(onClick = { errorMessage = null }) {
                                    Text("Dismiss")
                                }
                            }
                        ) {
                            Text(errorMessage!!)
                        }
                    }
                }
            }
        }
    }
}
