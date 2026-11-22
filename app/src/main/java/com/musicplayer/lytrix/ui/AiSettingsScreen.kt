package com.musicplayer.lytrix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.lyrics.ai.AiConstants
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    val prefs = remember { AiConstants.getPrefs(context) }

    var aiProvider by remember { mutableStateOf(AiConstants.getProvider(context)) }
    var apiKey by remember { mutableStateOf(AiConstants.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(AiConstants.getBaseUrl(context)) }
    var model by remember { mutableStateOf(AiConstants.getModel(context)) }
    var targetLanguage by remember { mutableStateOf(AiConstants.getTranslateLanguage(context)) }
    var translateMode by remember { mutableStateOf(AiConstants.getTranslateMode(context)) }
    var deeplApiKey by remember { mutableStateOf(AiConstants.getDeeplApiKey(context)) }
    var deeplFormality by remember { mutableStateOf(AiConstants.getDeeplFormality(context)) }
    var systemPrompt by remember { mutableStateOf(AiConstants.getSystemPrompt(context)) }

    fun savePref(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    val appBg = Color(0xFF070504)
    val cardBg = Color(0xFF161210)
    val iconBg = Color(0xFF2A1F1B)
    val iconTint = Color(0xFFD6B5A6)
    val textColor = Color(0xFFF7EBE5)
    val headerColor = Color(0xFFD6B5A6)
    val accentRed = Color(0xFFD32F2F)

    var showProviderDialog by remember { mutableStateOf(false) }
    var showProviderHelpDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showCustomModelInput by remember { mutableStateOf(false) }
    var showTranslateModeDialog by remember { mutableStateOf(false) }
    var showTranslateModeHelpDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    var showDeeplApiKeyDialog by remember { mutableStateOf(false) }
    var showDeeplFormalityDialog by remember { mutableStateOf(false) }

    val commonModels = AiConstants.modelsByProvider[aiProvider] ?: emptyList()

    // ── 1. Provider Help Dialog ─────────────────────────────────────────────
    if (showProviderHelpDialog) {
        AlertDialog(
            onDismissRequest = { showProviderHelpDialog = false },
            title = { Text("Get API Keys", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AiConstants.providerHelpText.forEach { (provider, help) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(provider, color = headerColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val urlRegex = "https?://[^\\s]+".toRegex()
                            val match = urlRegex.find(help)
                            if (match != null) {
                                val beforeUrl = help.substring(0, match.range.first)
                                val url = match.value
                                val afterUrl = help.substring(match.range.last + 1)

                                val annotated = buildAnnotatedString {
                                    append(beforeUrl)
                                    val start = length
                                    append(url)
                                    val end = length
                                    append(afterUrl)
                                    addStyle(
                                        SpanStyle(
                                            color = Color(0xFF64B5F6),
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        start,
                                        end
                                    )
                                    addStringAnnotation("URL", url, start, end)
                                }

                                ClickableText(
                                    text = annotated,
                                    style = LocalTextStyle.current.copy(color = textColor.copy(alpha = 0.8f), fontSize = 13.sp),
                                    onClick = { offset ->
                                        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                                            } catch (e: Exception) {}
                                        }
                                    }
                                )
                            } else {
                                Text(help, color = textColor.copy(alpha = 0.8f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderHelpDialog = false }) {
                    Text("OK", color = headerColor)
                }
            },
            containerColor = cardBg
        )
    }

    // ── 2. Translate Mode Help Dialog ───────────────────────────────────────
    if (showTranslateModeHelpDialog) {
        AlertDialog(
            onDismissRequest = { showTranslateModeHelpDialog = false },
            title = { Text("Translation Mode", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Translation (Literal):", color = headerColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Translates the meaning of the song lyrics into your chosen target language.", color = textColor.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Transcription (Pronunciation):", color = headerColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Converts the phonetic pronunciation into the script of your chosen language without changing the words.", color = textColor.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTranslateModeHelpDialog = false }) {
                    Text("OK", color = headerColor)
                }
            },
            containerColor = cardBg
        )
    }

    // ── 3. Provider Selection Dialog ─────────────────────────────────────────
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("Select AI Provider", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AiConstants.aiProviders.keys.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    aiProvider = p
                                    savePref(AiConstants.KEY_AI_PROVIDER, p)
                                    val defaultEndpoint = AiConstants.aiProviders[p].orEmpty()
                                    if (p != "Custom" && p != "DeepL") {
                                        baseUrl = defaultEndpoint
                                        savePref(AiConstants.KEY_BASE_URL, defaultEndpoint)
                                    }
                                    val defaultModels = AiConstants.modelsByProvider[p]
                                    if (!defaultModels.isNullOrEmpty()) {
                                        model = defaultModels[0]
                                        savePref(AiConstants.KEY_MODEL, model)
                                    }
                                    showProviderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = aiProvider == p,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = accentRed, unselectedColor = textColor.copy(alpha = 0.5f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(p, color = textColor, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 4. API Key Dialog ───────────────────────────────────────────────────
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        var isVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("$aiProvider API Key", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your API key below. It is stored safely on your phone.", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isVisible = !isVisible }) {
                                Icon(
                                    imageVector = if (isVisible) TablerIcons.EyeOff else TablerIcons.Eye,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.7f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = headerColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        apiKey = tempKey.trim()
                        savePref(AiConstants.KEY_API_KEY, apiKey)
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentRed)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 5. DeepL API Key Dialog ─────────────────────────────────────────────
    if (showDeeplApiKeyDialog) {
        var tempKey by remember { mutableStateOf(deeplApiKey) }
        var isVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDeeplApiKeyDialog = false },
            title = { Text("DeepL API Key", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your DeepL API key (Free or Pro).", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isVisible = !isVisible }) {
                                Icon(
                                    imageVector = if (isVisible) TablerIcons.EyeOff else TablerIcons.Eye,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.7f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = headerColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deeplApiKey = tempKey.trim()
                        savePref(AiConstants.KEY_DEEPL_API_KEY, deeplApiKey)
                        showDeeplApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentRed)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeeplApiKeyDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 6. Base URL Dialog (For Custom) ─────────────────────────────────────
    if (showBaseUrlDialog) {
        var tempUrl by remember { mutableStateOf(baseUrl) }
        AlertDialog(
            onDismissRequest = { showBaseUrlDialog = false },
            title = { Text("Custom Base URL", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your custom OpenAI-compatible completions URL:", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://your-server/v1/chat/completions", color = textColor.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = headerColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        baseUrl = tempUrl.trim()
                        savePref(AiConstants.KEY_BASE_URL, baseUrl)
                        showBaseUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentRed)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showBaseUrlDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 7. Model Selection Dialog ───────────────────────────────────────────
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Select Model", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    commonModels.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    model = m
                                    savePref(AiConstants.KEY_MODEL, m)
                                    showModelDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = model == m,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = accentRed, unselectedColor = textColor.copy(alpha = 0.5f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(m, color = textColor, fontSize = 14.sp)
                        }
                    }

                    // Custom model option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showModelDialog = false
                                showCustomModelInput = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = model !in commonModels && model.isNotBlank(),
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = accentRed, unselectedColor = textColor.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Custom Model...", color = headerColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 8. Custom Model Input Dialog ────────────────────────────────────────
    if (showCustomModelInput) {
        var tempModel by remember { mutableStateOf(model) }
        AlertDialog(
            onDismissRequest = { showCustomModelInput = false },
            title = { Text("Custom Model Name", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempModel,
                    onValueChange = { tempModel = it },
                    placeholder = { Text("e.g. gpt-4o, mistral-large", color = textColor.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = headerColor,
                        unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        model = tempModel.trim()
                        savePref(AiConstants.KEY_MODEL, model)
                        showCustomModelInput = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentRed)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomModelInput = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 9. Translation Mode Dialog ──────────────────────────────────────────
    if (showTranslateModeDialog) {
        AlertDialog(
            onDismissRequest = { showTranslateModeDialog = false },
            title = { Text("Translation Mode", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Literal", "Transcribed").forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    translateMode = m
                                    savePref(AiConstants.KEY_TRANSLATE_MODE, m)
                                    showTranslateModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = translateMode == m,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = accentRed, unselectedColor = textColor.copy(alpha = 0.5f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(if (m == "Literal") "Translation (Literal)" else "Transcription (Pronunciation)", color = textColor, fontSize = 15.sp)
                                Text(
                                    if (m == "Literal") "Translates meaning into target language" else "Converts pronunciation into target script",
                                    color = textColor.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTranslateModeDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 10. Language Selection Dialog ───────────────────────────────────────
    if (showLanguageDialog) {
        var query by remember { mutableStateOf("") }
        val sortedLanguages = remember {
            AiConstants.LanguageCodeToName.entries.sortedBy { it.value }
        }
        val filtered = sortedLanguages.filter {
            it.value.contains(query, ignoreCase = true) || it.key.contains(query, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Target Language", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search language...", color = textColor.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = headerColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        )
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered) { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        targetLanguage = code
                                        savePref(AiConstants.KEY_TRANSLATE_LANGUAGE, code)
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = targetLanguage == code,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = accentRed, unselectedColor = textColor.copy(alpha = 0.5f))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(name, color = textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text(code.uppercase(), color = textColor.copy(alpha = 0.4f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 11. System Prompt Dialog ────────────────────────────────────────────
    if (showSystemPromptDialog) {
        var tempPrompt by remember { mutableStateOf(systemPrompt.ifBlank { AiConstants.DEFAULT_AI_SYSTEM_PROMPT }) }
        AlertDialog(
            onDismissRequest = { showSystemPromptDialog = false },
            title = { Text("AI System Prompt", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Custom instructions sent to the AI. Use {lineCount} as placeholder for line count.",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPrompt,
                        onValueChange = { tempPrompt = it },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        maxLines = 15,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = headerColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f)
                        )
                    )
                    if (systemPrompt.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                tempPrompt = AiConstants.DEFAULT_AI_SYSTEM_PROMPT
                                systemPrompt = ""
                                savePref(AiConstants.KEY_SYSTEM_PROMPT, "")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Reset to default", color = accentRed, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toSave = if (tempPrompt.trim() == AiConstants.DEFAULT_AI_SYSTEM_PROMPT.trim()) "" else tempPrompt.trim()
                        systemPrompt = toSave
                        savePref(AiConstants.KEY_SYSTEM_PROMPT, toSave)
                        showSystemPromptDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentRed)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSystemPromptDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── 12. DeepL Formality Dialog ──────────────────────────────────────────
    if (showDeeplFormalityDialog) {
        AlertDialog(
            onDismissRequest = { showDeeplFormalityDialog = false },
            title = { Text("DeepL Formality", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("default" to "Default", "more" to "More formal", "less" to "Less formal").forEach { (f, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    deeplFormality = f
                                    savePref(AiConstants.KEY_DEEPL_FORMALITY, f)
                                    showDeeplFormalityDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = deeplFormality == f,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = accentRed, unselectedColor = textColor.copy(alpha = 0.5f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = textColor, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeeplFormalityDialog = false }) { Text("Cancel", color = textColor) }
            },
            containerColor = cardBg
        )
    }

    // ── MAIN UI ─────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI lyrics translation",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBg)
            )
        },
        containerColor = appBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Section 1: AI Provider ──────────────────────────────────────
            item {
                Text(
                    text = "AI Provider",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProviderDialog = true }
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(TablerIcons.Compass, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Provider", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(aiProvider, color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                            IconButton(onClick = { showProviderHelpDialog = true }) {
                                Icon(TablerIcons.InfoCircle, contentDescription = "Help", tint = headerColor, modifier = Modifier.size(22.dp))
                            }
                        }

                        if (aiProvider == "Custom") {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                            AiSettingsRowItem(
                                icon = TablerIcons.ExternalLink,
                                title = "Base URL",
                                subtitle = baseUrl.ifBlank { "Not set" },
                                iconBg = iconBg,
                                iconTint = iconTint,
                                textColor = textColor,
                                onClick = { showBaseUrlDialog = true }
                            )
                        }
                    }
                }
            }

            // ── Section 2: API credentials ──────────────────────────────────
            item {
                Text(
                    text = "API credentials",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (aiProvider == "DeepL") {
                            AiSettingsRowItem(
                                icon = TablerIcons.Key,
                                title = "DeepL API key",
                                subtitle = if (deeplApiKey.isNotBlank()) "•".repeat(minOf(deeplApiKey.length, 10)) else "Not set",
                                iconBg = iconBg,
                                iconTint = iconTint,
                                textColor = textColor,
                                onClick = { showDeeplApiKeyDialog = true }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                            AiSettingsRowItem(
                                icon = TablerIcons.Adjustments,
                                title = "Formality",
                                subtitle = when (deeplFormality) {
                                    "more" -> "More formal"
                                    "less" -> "Less formal"
                                    else -> "Default"
                                },
                                iconBg = iconBg,
                                iconTint = iconTint,
                                textColor = textColor,
                                onClick = { showDeeplFormalityDialog = true }
                            )
                        } else {
                            AiSettingsRowItem(
                                icon = TablerIcons.Key,
                                title = "API key",
                                subtitle = if (apiKey.isNotBlank()) "•".repeat(minOf(apiKey.length, 10)) else "Not set",
                                iconBg = iconBg,
                                iconTint = iconTint,
                                textColor = textColor,
                                onClick = { showApiKeyDialog = true }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                            AiSettingsRowItem(
                                icon = TablerIcons.Bolt,
                                title = "Model",
                                subtitle = model.ifBlank { "Not set" },
                                iconBg = iconBg,
                                iconTint = iconTint,
                                textColor = textColor,
                                onClick = { showModelDialog = true }
                            )
                        }
                    }
                }
            }

            // ── Section 3: Translation mode ─────────────────────────────────
            item {
                Text(
                    text = "Translation mode",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (aiProvider != "DeepL") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTranslateModeDialog = true }
                                    .padding(vertical = 16.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp).clip(CircleShape).background(iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(TablerIcons.Language, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Translation mode", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (translateMode == "Literal") "Translation (Meaning)" else "Transcription (Pronunciation)",
                                        color = textColor.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                }
                                IconButton(onClick = { showTranslateModeHelpDialog = true }) {
                                    Icon(TablerIcons.InfoCircle, contentDescription = "Help", tint = headerColor, modifier = Modifier.size(22.dp))
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                            AiSettingsRowItem(
                                icon = TablerIcons.Edit,
                                title = "System prompt",
                                subtitle = if (systemPrompt.isNotBlank()) "Custom (${systemPrompt.take(25)}...)" else "Default",
                                iconBg = iconBg,
                                iconTint = iconTint,
                                textColor = textColor,
                                onClick = { showSystemPromptDialog = true }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        AiSettingsRowItem(
                            icon = TablerIcons.World,
                            title = "Target language",
                            subtitle = AiConstants.LanguageCodeToName[targetLanguage] ?: targetLanguage,
                            iconBg = iconBg,
                            iconTint = iconTint,
                            textColor = textColor,
                            onClick = { showLanguageDialog = true }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun AiSettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconBg: Color,
    iconTint: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = textColor.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = textColor.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
}
