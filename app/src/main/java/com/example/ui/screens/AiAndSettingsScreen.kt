package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DuplicateBehavior
import com.example.data.SearchEngine
import com.example.data.UserSettings
import com.example.data.WallpaperMode
import com.example.data.ai.ExtractedLinkItem
import com.example.data.local.FilterRuleEntity
import com.example.data.security.CredentialVaultHelper
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.PrismGradientButton
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.GlassBase
import com.example.ui.theme.GlassEdgeLo
import com.example.ui.theme.GlassHi
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.OmniInsetBg
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.AiChatMessage
import com.example.viewmodel.InsightUiState
import com.example.viewmodel.LinkExtractorUiState
import kotlinx.coroutines.launch

private enum class SettingsCategory(val label: String) {
    ALL("All"),
    BROWSER("Browser"),
    DOWNLOADS("Downloads"),
    PRIVACY("Privacy"),
    SECURITY("Security"),
    CUSTOMIZATION("Custom"),
}

@Composable
fun AiAndSettingsScreen(
    initialSubTab: Int,
    messages: List<AiChatMessage>,
    isStreaming: Boolean,
    aiError: String?,
    insightState: InsightUiState,
    linkExtractorState: LinkExtractorUiState,
    activeBrowserUrl: String,
    settings: UserSettings,
    filterRules: List<FilterRuleEntity>,
    onSendPrompt: (String) -> Unit,
    onAnalyzeImage: (Bitmap, String) -> Unit,
    onExtractLinks: (String) -> Unit,
    onClearLinkExtractor: () -> Unit,
    onQueueExtractedLinks: (List<ExtractedLinkItem>) -> Unit,
    onInspectActiveUrl: (String) -> Unit,
    onClearInsight: () -> Unit,
    onClearChat: () -> Unit,
    // Browser Settings
    onSearchEngineChange: (SearchEngine) -> Unit,
    onHomepageSave: (String) -> Unit,
    onDesktopUaChange: (Boolean) -> Unit,
    // Download Settings
    onDefaultChunksChange: (Int) -> Unit,
    onMaxConcurrentChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAskBeforeDownloadChange: (Boolean) -> Unit,
    onDuplicateBehaviorChange: (DuplicateBehavior) -> Unit,
    onAutoRetryChange: (Boolean) -> Unit,
    onDownloadFolderSelected: (String, String?) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    // Privacy & Security
    onAdBlockerChange: (Boolean) -> Unit,
    onThirdPartyTrackerChange: (Boolean) -> Unit,
    onAddFilterRule: (pattern: String, isAllowRule: Boolean) -> Unit,
    onDeleteFilterRule: (Long) -> Unit,
    onClearBrowsingData: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUnsafeProtectionChange: (Boolean) -> Unit,
    onCredentialManagerChange: (Boolean) -> Unit,
    onAutofillChange: (Boolean) -> Unit,
    // Customization & Integrations
    onAccentSelected: (AccentPreset) -> Unit,
    onWallpaperSelected: (WallpaperMode, Uri?) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onGithubTokenSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    var selectedSubTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 1000.dp).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                Row(modifier = Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Gemini AI", "Settings").forEachIndexed { idx, label ->
                        val active = selectedSubTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) Brush.linearGradient(listOf(accent.primary, accent.secondary))
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                    CircleShape,
                                )
                                .clickable { selectedSubTab = idx }
                                .testTag("ai_settings_tab_$idx"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (active) Color.White else TextMuted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (selectedSubTab == 0) {
                GeminiAssistantContent(
                    messages = messages,
                    isStreaming = isStreaming,
                    aiError = aiError,
                    insightState = insightState,
                    linkExtractorState = linkExtractorState,
                    activeBrowserUrl = activeBrowserUrl,
                    onSendPrompt = onSendPrompt,
                    onAnalyzeImage = onAnalyzeImage,
                    onExtractLinks = onExtractLinks,
                    onClearLinkExtractor = onClearLinkExtractor,
                    onQueueExtractedLinks = onQueueExtractedLinks,
                    onInspectActiveUrl = onInspectActiveUrl,
                    onClearInsight = onClearInsight,
                    onClearChat = onClearChat,
                    modifier = Modifier.weight(1f),
                )
            } else {
                AppSettingsContent(
                    settings = settings,
                    filterRules = filterRules,
                    onSearchEngineChange = onSearchEngineChange,
                    onHomepageSave = onHomepageSave,
                    onDesktopUaChange = onDesktopUaChange,
                    onDefaultChunksChange = onDefaultChunksChange,
                    onMaxConcurrentChange = onMaxConcurrentChange,
                    onWifiOnlyChange = onWifiOnlyChange,
                    onAskBeforeDownloadChange = onAskBeforeDownloadChange,
                    onDuplicateBehaviorChange = onDuplicateBehaviorChange,
                    onAutoRetryChange = onAutoRetryChange,
                    onDownloadFolderSelected = onDownloadFolderSelected,
                    onNotificationsChange = onNotificationsChange,
                    onAdBlockerChange = onAdBlockerChange,
                    onThirdPartyTrackerChange = onThirdPartyTrackerChange,
                    onAddFilterRule = onAddFilterRule,
                    onDeleteFilterRule = onDeleteFilterRule,
                    onClearBrowsingData = onClearBrowsingData,
                    onUnsafeProtectionChange = onUnsafeProtectionChange,
                    onCredentialManagerChange = onCredentialManagerChange,
                    onAutofillChange = onAutofillChange,
                    onAccentSelected = onAccentSelected,
                    onWallpaperSelected = onWallpaperSelected,
                    onGlassIntensityChange = onGlassIntensityChange,
                    onGithubTokenSave = onGithubTokenSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeminiAssistantContent(
    messages: List<AiChatMessage>,
    isStreaming: Boolean,
    aiError: String?,
    insightState: InsightUiState,
    linkExtractorState: LinkExtractorUiState,
    activeBrowserUrl: String,
    onSendPrompt: (String) -> Unit,
    onAnalyzeImage: (Bitmap, String) -> Unit,
    onExtractLinks: (String) -> Unit,
    onClearLinkExtractor: () -> Unit,
    onQueueExtractedLinks: (List<ExtractedLinkItem>) -> Unit,
    onInspectActiveUrl: (String) -> Unit,
    onClearInsight: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var input by remember { mutableStateOf("") }
    var showLinkExtractorDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val src = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                            val maxDim = maxOf(info.size.width, info.size.height)
                            if (maxDim > 1024) {
                                val scale = 1024f / maxDim
                                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                            }
                        }
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    onAnalyzeImage(bmp, input)
                    input = ""
                }
            }
        },
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (activeBrowserUrl.isNotBlank()) item { AiQuickChip("Inspect Active Tab") { onInspectActiveUrl(activeBrowserUrl) } }
            item { AiQuickChip("Smart Link Extractor") { showLinkExtractorDialog = true } }
            item { AiQuickChip("Analyze Screenshot") { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
            item { AiQuickChip("Best Video Format?") { onSendPrompt("What is the best balance between video quality and file size for mobile downloads?") } }
            item {
                IconButton(onClick = onClearChat, modifier = Modifier.size(38.dp).clip(CircleShape).background(GlassBase)) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear AI chat", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        when (insightState) {
            InsightUiState.Idle -> Unit
            InsightUiState.Loading -> {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent.secondary, strokeWidth = 2.dp)
                        Text("Gemini AI is analyzing target metadata...", color = TextPrimary, fontSize = 13.5.sp)
                    }
                }
            }
            is InsightUiState.Error -> {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), borderColor = StatusError) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(insightState.message, color = StatusError, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearInsight) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextMuted) }
                    }
                }
            }
            is InsightUiState.Success -> {
                val insight = insightState.insight
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), borderColor = accent.secondary) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = accent.secondary, modifier = Modifier.size(18.dp))
                                Text(insight.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            IconButton(onClick = onClearInsight, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close insight", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text("${insight.category} • Recommended: ${insight.recommendedFormat}", color = accent.orb3, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily)
                        Text(insight.summary, color = TextPrimary, fontSize = 13.5.sp)
                        if (insight.tags.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                insight.tags.forEach { tag ->
                                    Box(modifier = Modifier.clip(CircleShape).background(accent.primary.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                        Text("#$tag", color = TextPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Text("Safety: ${insight.safetyNote}", color = accent.orb4, fontSize = 12.sp)
                    }
                }
            }
        }

        if (aiError != null) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), borderColor = StatusError) {
                Text(aiError, color = StatusError, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(26.dp)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(messages, key = { it.id }) { msg ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
                        Box(
                            modifier = Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(20.dp)).background(
                                if (msg.isUser) Brush.linearGradient(listOf(accent.primary.copy(alpha = 0.42f), accent.secondary.copy(alpha = 0.34f)))
                                else Brush.linearGradient(listOf(GlassHi, GlassBase))
                            ).border(1.dp, if (msg.isUser) accent.primary.copy(alpha = 0.6f) else GlassEdgeLo, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(if (msg.text.isEmpty() && msg.isStreaming) "Thinking..." else msg.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isStreaming,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Attach image for Gemini Vision", tint = accent.orb3, modifier = Modifier.size(20.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Ask Gemini or attach screenshot...", color = TextMuted, fontSize = 14.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily),
                    cursorBrush = SolidColor(accent.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() && !isStreaming) {
                            focusManager.clearFocus(); onSendPrompt(input); input = ""
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input"),
                )
            }
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !isStreaming) {
                        focusManager.clearFocus(); onSendPrompt(input); input = ""
                    }
                },
                enabled = input.isNotBlank() && !isStreaming,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent.primary, accent.secondary))).testTag("ai_send_button"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send prompt", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showLinkExtractorDialog) {
        SmartLinkExtractorDialog(
            state = linkExtractorState,
            onExtract = onExtractLinks,
            onQueueAll = { links -> onQueueExtractedLinks(links); onClearLinkExtractor(); showLinkExtractorDialog = false },
            onDismiss = { onClearLinkExtractor(); showLinkExtractorDialog = false },
        )
    }
}

@Composable
private fun SmartLinkExtractorDialog(
    state: LinkExtractorUiState,
    onExtract: (String) -> Unit,
    onQueueAll: (List<ExtractedLinkItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    var rawText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = accent.orb3)
                Text("Gemini Smart Link Extractor", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Paste release notes or messy webpage text and Gemini will pull downloadable links.", color = TextMuted, fontSize = 12.5.sp)
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(OmniInsetBg).border(1.dp, GlassEdgeLo, RoundedCornerShape(16.dp)).padding(12.dp)) {
                    if (rawText.isEmpty()) Text("Paste text containing links...", color = TextMuted, fontSize = 13.sp)
                    BasicTextField(value = rawText, onValueChange = { rawText = it }, textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = FigtreeFontFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxSize())
                }
                when (state) {
                    LinkExtractorUiState.Idle -> Unit
                    LinkExtractorUiState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent.primary, strokeWidth = 2.dp)
                            Text("Extracting URLs...", color = TextMuted, fontSize = 12.5.sp)
                        }
                    }
                    is LinkExtractorUiState.Error -> Text(state.message, color = StatusError, fontSize = 12.5.sp)
                    is LinkExtractorUiState.Loaded -> {
                        Text("Found ${state.links.size} links:", color = accent.orb3, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.links.take(6).forEach { link ->
                                Text("• [${link.fileType}] ${link.label}", color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state is LinkExtractorUiState.Loaded && state.links.isNotEmpty()) {
                PrismGradientButton(text = "Queue All (${state.links.size})", icon = Icons.Rounded.Download, height = 38.dp, onClick = { onQueueAll(state.links) })
            } else {
                PrismGradientButton(text = "Extract", height = 38.dp, enabled = rawText.isNotBlank() && state !is LinkExtractorUiState.Loading, onClick = { onExtract(rawText) })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) } },
    )
}

@Composable
private fun AiQuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.defaultMinSize(minHeight = 38.dp).clip(CircleShape).background(GlassHi).border(1.dp, GlassEdgeLo, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppSettingsContent(
    settings: UserSettings,
    filterRules: List<FilterRuleEntity>,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onHomepageSave: (String) -> Unit,
    onDesktopUaChange: (Boolean) -> Unit,
    onDefaultChunksChange: (Int) -> Unit,
    onMaxConcurrentChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAskBeforeDownloadChange: (Boolean) -> Unit,
    onDuplicateBehaviorChange: (DuplicateBehavior) -> Unit,
    onAutoRetryChange: (Boolean) -> Unit,
    onDownloadFolderSelected: (String, String?) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onAdBlockerChange: (Boolean) -> Unit,
    onThirdPartyTrackerChange: (Boolean) -> Unit,
    onAddFilterRule: (String, Boolean) -> Unit,
    onDeleteFilterRule: (Long) -> Unit,
    onClearBrowsingData: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUnsafeProtectionChange: (Boolean) -> Unit,
    onCredentialManagerChange: (Boolean) -> Unit,
    onAutofillChange: (Boolean) -> Unit,
    onAccentSelected: (AccentPreset) -> Unit,
    onWallpaperSelected: (WallpaperMode, Uri?) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onGithubTokenSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(SettingsCategory.ALL) }
    var tokenInput by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var homepageInput by remember(settings.homepageUrl) { mutableStateOf(settings.homepageUrl) }
    var newRulePattern by remember { mutableStateOf("") }
    var newRuleIsAllow by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var credentialCheckFeedback by remember { mutableStateOf<String?>(null) }

    val credStatus = remember(context) { CredentialVaultHelper.inspectSystemCredentialStatus(context) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) onWallpaperSelected(WallpaperMode.CUSTOM_PHOTO, uri) },
    )

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val label = uri.lastPathSegment?.substringAfterLast(':') ?: "Selected SAF Folder"
                onDownloadFolderSelected(label, uri.toString())
            }
        },
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> onNotificationsChange(granted) },
    )

    fun showCat(cat: SettingsCategory) = selectedCategory == SettingsCategory.ALL || selectedCategory == cat

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SettingsCategory.entries) { cat ->
                val active = cat == selectedCategory
                Box(
                    modifier = Modifier.clip(CircleShape).background(if (active) GlassHi else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { selectedCategory = cat }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(cat.label, color = if (active) TextPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showCat(SettingsCategory.BROWSER)) {
                item(key = "browser_prefs") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Browser Preferences", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Default Search Engine", color = TextMuted, fontSize = 12.5.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(SearchEngine.entries) { eng ->
                                    val active = eng == settings.searchEngine
                                    Box(
                                        modifier = Modifier.clip(CircleShape).background(if (active) accent.primary.copy(alpha = 0.25f) else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { onSearchEngineChange(eng) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(eng.label, color = if (active) TextPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(OmniInsetBg).border(1.dp, GlassEdgeLo, CircleShape).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (homepageInput.isEmpty()) Text("Custom Homepage URL (optional)", color = TextMuted, fontSize = 12.5.sp)
                                    BasicTextField(value = homepageInput, onValueChange = { homepageInput = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = FigtreeFontFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                                }
                                PrismGradientButton(text = "Save", height = 32.dp, onClick = { onHomepageSave(homepageInput) })
                            }

                            SettingsSwitchRow("Global Desktop Mode", "Request desktop layout on all websites", settings.desktopUserAgent, onDesktopUaChange)
                        }
                    }
                }
            }

            if (showCat(SettingsCategory.DOWNLOADS)) {
                item(key = "download_prefs") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Download Manager & Storage", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Default Parallel Chunks", color = TextMuted, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(4, 8, 16).forEach { c ->
                                        val active = settings.defaultChunkCount == c
                                        Box(modifier = Modifier.clip(CircleShape).background(if (active) accent.primary.copy(alpha = 0.28f) else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { onDefaultChunksChange(c) }.padding(horizontal = 11.dp, vertical = 5.dp)) {
                                            Text("${c}x", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily)
                                        }
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Max Concurrent Downloads", color = TextMuted, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1, 2, 3, 5).forEach { m ->
                                        val active = settings.maxConcurrentDownloads == m
                                        Box(modifier = Modifier.clip(CircleShape).background(if (active) accent.primary.copy(alpha = 0.28f) else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { onMaxConcurrentChange(m) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                                            Text("$m", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily)
                                        }
                                    }
                                }
                            }

                            Text("Duplicate File Policy", color = TextMuted, fontSize = 12.5.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DuplicateBehavior.entries) { b ->
                                    val active = b == settings.duplicateBehavior
                                    Box(modifier = Modifier.clip(CircleShape).background(if (active) GlassHi else GlassBase).border(1.dp, if (active) accent.orb3 else GlassEdgeLo, CircleShape).clickable { onDuplicateBehaviorChange(b) }.padding(horizontal = 11.dp, vertical = 6.dp)) {
                                        Text(b.label, color = if (active) TextPrimary else TextMuted, fontSize = 12.sp)
                                    }
                                }
                            }

                            SettingsSwitchRow("Wi-Fi Only Transfers", "Block metered mobile data downloads", settings.wifiOnlyDownloads, onWifiOnlyChange)
                            SettingsSwitchRow("Ask Before Download", "Show confirmation prompt for browser/extractor transfers", settings.askBeforeDownload, onAskBeforeDownloadChange)
                            SettingsSwitchRow("Auto-Retry Failed Transfers", "Retry transient network interruptions automatically", settings.autoRetryDownloads, onAutoRetryChange)
                            SettingsSwitchRow("Progress Notifications", "Show transfer status in Android system tray", settings.notificationsEnabled) { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else onNotificationsChange(enable)
                            }

                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassBase).clickable { safFolderLauncher.launch(null) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = accent.orb4)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Custom Export Location (SAF)", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                    Text(settings.downloadFolderLabel, color = accent.orb3, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (showCat(SettingsCategory.PRIVACY)) {
                item(key = "privacy_prefs") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Shield, contentDescription = null, tint = StatusSuccess)
                                Text("Ad Shield, Trackers & Filter Rules", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            SettingsSwitchRow("Ad & Malware Host Shield", "Block known advertising networks", settings.adBlockerEnabled, onAdBlockerChange)
                            SettingsSwitchRow("Third-Party Tracker Shield", "Block cross-site analytics & pixel scripts", settings.blockThirdPartyTrackers, onThirdPartyTrackerChange)

                            Row(modifier = Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(OmniInsetBg).border(1.dp, GlassEdgeLo, CircleShape).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (newRulePattern.isEmpty()) Text("Domain pattern (e.g. ads.example.com)", color = TextMuted, fontSize = 12.5.sp)
                                    BasicTextField(value = newRulePattern, onValueChange = { newRulePattern = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMonoFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                                }
                                Box(modifier = Modifier.clip(CircleShape).background(if (newRuleIsAllow) StatusSuccess.copy(alpha = 0.25f) else StatusError.copy(alpha = 0.25f)).clickable { newRuleIsAllow = !newRuleIsAllow }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(if (newRuleIsAllow) "ALLOW" else "BLOCK", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { if (newRulePattern.isNotBlank()) { onAddFilterRule(newRulePattern, newRuleIsAllow); newRulePattern = "" } }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add filter rule", tint = accent.orb3)
                                }
                            }

                            filterRules.take(8).forEach { rule ->
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OmniInsetBg).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${if (rule.isAllowRule) "[ALLOW]" else "[BLOCK]"} ${rule.pattern}", color = if (rule.isAllowRule) StatusSuccess else TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily)
                                    IconButton(onClick = { onDeleteFilterRule(rule.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete rule", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            PrismGradientButton(text = "Clear Browsing Data", icon = Icons.Outlined.DeleteSweep, onClick = { showClearDialog = true }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (showCat(SettingsCategory.SECURITY)) {
                item(key = "security_prefs") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Key, contentDescription = null, tint = accent.orb3)
                                Text("Passwords, Passkeys & Safety", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            SettingsSwitchRow("Unsafe URL & TLS Warnings", "Inspect punycode, HTTP logins & SSL cert errors", settings.unsafeSiteProtection, onUnsafeProtectionChange)
                            SettingsSwitchRow("Android Credential Manager", "Support system password managers & passkeys", settings.credentialManagerEnabled, onCredentialManagerChange)
                            SettingsSwitchRow("WebView System Autofill", "Allow OS password autofill on sign-in forms", settings.autofillEnabled, onAutofillChange)

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OmniInsetBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("System Provider Status", color = accent.orb3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(credStatus.providerSummary, color = TextMuted, fontSize = 12.sp)
                                credentialCheckFeedback?.let { Text(it, color = StatusSuccess, fontSize = 11.5.sp) }
                            }

                            PrismGradientButton(
                                text = "Test System Credential Provider",
                                onClick = { scope.launch { credentialCheckFeedback = CredentialVaultHelper.checkSavedCredentialPrompt(context) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (showCat(SettingsCategory.CUSTOMIZATION)) {
                item(key = "accents") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Liquid Glass Accent & Wallpaper", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(AccentPreset.entries) { preset ->
                                    val selected = preset == settings.accentPreset
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onAccentSelected(preset) }.padding(4.dp).testTag("accent_${preset.id}"),
                                    ) {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(preset.primary, preset.secondary))).border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else GlassEdgeLo, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                        Text(preset.label.substringBefore(" "), color = if (selected) TextPrimary else TextMuted, fontSize = 11.5.sp)
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(WallpaperMode.PRISM_ORBS, WallpaperMode.WALL_PRISM, WallpaperMode.WALL_AURORA)) { mode ->
                                    val selected = settings.wallpaperMode == mode
                                    Box(
                                        modifier = Modifier.defaultMinSize(minHeight = 38.dp).clip(CircleShape).background(if (selected) accent.primary.copy(alpha = 0.28f) else GlassBase).border(1.dp, if (selected) accent.primary else GlassEdgeLo, CircleShape).clickable { onWallpaperSelected(mode, null) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(mode.label, color = if (selected) TextPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            PrismGradientButton(
                                text = if (settings.wallpaperMode == WallpaperMode.CUSTOM_PHOTO) "Change Custom Photo" else "Pick Custom Wallpaper Photo",
                                icon = Icons.Rounded.PhotoLibrary,
                                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.fillMaxWidth().testTag("pick_wallpaper_button"),
                            )

                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Glass Frost Intensity", color = TextMuted, fontSize = 13.sp)
                                    Text("${(settings.glassIntensity * 100).toInt()}%", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMonoFamily)
                                }
                                Slider(value = settings.glassIntensity, onValueChange = onGlassIntensityChange, valueRange = 0.35f..1.0f, colors = SliderDefaults.colors(thumbColor = accent.primary, activeTrackColor = accent.primary))
                            }
                        }
                    }
                }

                item(key = "github_token") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("GitHub Account Token (Optional)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Increases GitHub API rate limit and accesses private repositories.", color = TextMuted, fontSize = 12.5.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(start = 14.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (tokenInput.isEmpty()) Text("ghp_...", color = TextMuted, fontSize = 13.5.sp, fontFamily = JetBrainsMonoFamily)
                                    BasicTextField(value = tokenInput, onValueChange = { tokenInput = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, fontFamily = JetBrainsMonoFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                                }
                                PrismGradientButton(text = "Save", height = 36.dp, onClick = { onGithubTokenSave(tokenInput) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ClearBrowsingDataDialog(
            onConfirmClear = onClearBrowsingData,
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalPrismColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 11.5.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent.primary))
    }
}
