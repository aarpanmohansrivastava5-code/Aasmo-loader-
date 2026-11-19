package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ExtractionHistoryEntity
import com.example.data.media.DownloadEligibility
import com.example.data.media.ResolvedMediaItem
import com.example.data.media.SupportedPlatform
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.PrismGradientButton
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.GlassBase
import com.example.ui.theme.GlassEdgeLo
import com.example.ui.theme.GlassHi
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.OmniInsetBg
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.ExtractionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalDownloaderSheet(
    initialInput: String,
    extractionState: ExtractionUiState,
    selectedIds: Set<String>,
    selectionNotice: String?,
    previewItem: ResolvedMediaItem?,
    recentAnalyses: List<ExtractionHistoryEntity>,
    isLoadingMore: Boolean,
    onAnalyze: (String, SupportedPlatform?) -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAllEligible: () -> Unit,
    onClearSelection: () -> Unit,
    onDismissSelectionNotice: () -> Unit,
    onDownloadSingle: (ResolvedMediaItem) -> Unit,
    onDownloadSelected: () -> Unit,
    onDownloadAllEligible: () -> Unit,
    onOpenPreview: (ResolvedMediaItem) -> Unit,
    onDismissPreview: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var queryText by remember(initialInput) { mutableStateOf(initialInput) }
    var preferredPlatform by remember { mutableStateOf<SupportedPlatform?>(null) }

    val platformOptions = remember {
        SupportedPlatform.entries.filter {
            it != SupportedPlatform.DIRECT_MEDIA && it != SupportedPlatform.WEB_GENERIC
        }
    }

    LaunchedEffect(initialInput) {
        if (initialInput.isNotBlank()) {
            onAnalyze(initialInput, null)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceElevated,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Universal Media Extractor",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Paste URL, creator @handle, or inspect current tab",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // URL / Handle input box + explicit Paste + Analyze buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(OmniInsetBg, CircleShape)
                    .border(1.dp, GlassEdgeLo, CircleShape)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = accent.primary, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (queryText.isEmpty()) {
                        Text(
                            text = "Paste media URL or @username...",
                            color = TextMuted,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, fontFamily = FigtreeFontFamily),
                        cursorBrush = SolidColor(accent.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                onAnalyze(queryText, preferredPlatform)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("universal_url_input"),
                    )
                }

                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip: ClipData? = cm?.primaryClip
                        val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
                        if (!pasted.isNullOrBlank()) {
                            queryText = pasted
                        }
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste URL from clipboard", tint = accent.orb3, modifier = Modifier.size(18.dp))
                }

                PrismGradientButton(
                    text = "Analyze",
                    height = 36.dp,
                    enabled = queryText.isNotBlank() && extractionState !is ExtractionUiState.Loading,
                    onClick = {
                        focusManager.clearFocus()
                        onAnalyze(queryText, preferredPlatform)
                    },
                    modifier = Modifier.testTag("universal_analyze_button"),
                )
            }

            // Optional platform target pills (helps @username disambiguation)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    val active = preferredPlatform == null
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) GlassHi else GlassBase)
                            .border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape)
                            .clickable { preferredPlatform = null }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text("Auto Detect", color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(platformOptions, key = { it.id }) { p ->
                    val active = preferredPlatform == p
                    val tint = Color(p.badgeColorHex)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) tint.copy(alpha = 0.25f) else GlassBase)
                            .border(1.dp, if (active) tint else GlassEdgeLo, CircleShape)
                            .clickable { preferredPlatform = p }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(p.label, color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp)
                    }
                }
            }

            // Recent Analyses strip
            if (recentAnalyses.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(recentAnalyses.take(8), key = { it.id }) { hist ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(OmniInsetBg)
                                    .border(1.dp, GlassEdgeLo, CircleShape)
                                    .clickable {
                                        queryText = hist.inputQuery
                                        onAnalyze(hist.inputQuery, null)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "${hist.platformId.uppercase()}: ${hist.title.take(18)}",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onClearHistory, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear extraction history", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Results Container
            when (extractionState) {
                ExtractionUiState.Idle -> {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Supported Sources & Honest Inspection", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "• Direct video, audio, image, APK, and archive URLs are verified via HTTP HEAD/Range headers before queuing.\n• Reddit posts & feeds retrieve real images and v.redd.it video streams with pagination.\n• YouTube, TikTok, X, Instagram, Pinterest, Facebook & Threads retrieve public oEmbed/OpenGraph metadata without downloading HTML wrappers as fake videos.",
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                ExtractionUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = accent.primary)
                            Text("Resolving platform, redirects & media headers...", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }

                is ExtractionUiState.Error -> {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), borderColor = StatusError) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Analysis Failed", color = StatusError, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(extractionState.message, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }

                is ExtractionUiState.Loaded -> {
                    val res = extractionState.result
                    val downloadableItems = res.items.filter { it.isDownloadable }
                    val chosenDownloadableCount = downloadableItems.count { it.id in selectedIds }

                    // Status & Capability Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(res.platform.badgeColorHex).copy(alpha = 0.14f))
                            .border(1.dp, Color(res.platform.badgeColorHex).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(res.platform.badgeColorHex), modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${res.platform.label} • ${res.classification.label}",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(res.statusBanner, color = TextMuted, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (!selectionNotice.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(StatusWarning.copy(alpha = 0.16f))
                                .border(1.dp, StatusWarning.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("universal_selection_notice"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = selectionNotice,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = onDismissSelectionNotice,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss selection message", tint = StatusWarning, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Batch Selection & Explicit Action Bar
                    if (downloadableItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(CircleShape)
                                        .background(GlassBase)
                                        .border(1.dp, GlassEdgeLo, CircleShape)
                                        .clickable {
                                            if (chosenDownloadableCount == downloadableItems.size) onClearSelection() else onSelectAllEligible()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                        .testTag("universal_toggle_select_all"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(Icons.Rounded.SelectAll, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (chosenDownloadableCount == downloadableItems.size) {
                                            "Clear Selection"
                                        } else {
                                            "Select All (${downloadableItems.size})"
                                        },
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                Text(
                                    text = "$chosenDownloadableCount of ${downloadableItems.size} selected",
                                    color = if (chosenDownloadableCount > 0) accent.orb3 else TextMuted,
                                    fontSize = 12.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("universal_selection_count"),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PrismGradientButton(
                                    text = "Download Selected ($chosenDownloadableCount)",
                                    icon = Icons.Rounded.Download,
                                    height = 40.dp,
                                    enabled = downloadableItems.isNotEmpty(),
                                    onClick = onDownloadSelected,
                                    modifier = Modifier.weight(1f).testTag("universal_download_selected_button"),
                                )

                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(CircleShape)
                                        .background(GlassBase)
                                        .border(1.dp, accent.orb3.copy(alpha = 0.65f), CircleShape)
                                        .clickable(onClick = onDownloadAllEligible)
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                        .testTag("universal_download_all_button"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Download All (${downloadableItems.size})",
                                        color = accent.orb3,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        res.profile?.let { prof ->
                            item(key = "profile_summary") {
                                LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        if (!prof.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = prof.avatarUrl,
                                                contentDescription = prof.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(CircleShape).background(GlassHi),
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prof.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${prof.handle} • ${prof.retrievedCount} items indexed", color = accent.orb3, fontSize = 11.5.sp)
                                            Text(prof.limitationNotice, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        items(res.items, key = { it.id }) { item ->
                            UniversalMediaCard(
                                item = item,
                                selected = item.id in selectedIds,
                                onToggleSelect = { onToggleSelect(item.id) },
                                onPreview = { onOpenPreview(item) },
                                onOpenOriginal = {
                                    onDismiss()
                                    onOpenInBrowser(item.originalUrl)
                                },
                                onDownload = { onDownloadSingle(item) },
                            )
                        }

                        if (!res.nextPageCursor.isNullOrBlank()) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(color = accent.primary, modifier = Modifier.size(28.dp))
                                    } else {
                                        PrismGradientButton(
                                            text = "Load More Results",
                                            height = 38.dp,
                                            onClick = onLoadMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewItem != null) {
        AlertDialog(
            onDismissRequest = onDismissPreview,
            containerColor = SurfaceElevated,
            title = {
                Text(previewItem.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!previewItem.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = previewItem.thumbnailUrl,
                            contentDescription = previewItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)).background(GlassBase),
                        )
                    }
                    Text("Platform: ${previewItem.platform.label} (${previewItem.kind.label})", color = accent.orb3, fontSize = 12.sp)
                    Text("Creator: ${previewItem.creator}", color = TextPrimary, fontSize = 12.5.sp)
                    Text("Access Status: ${previewItem.accessStatus}", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = previewItem.downloadUrl ?: previewItem.originalUrl,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                if (previewItem.isDownloadable) {
                    PrismGradientButton(
                        text = "Download",
                        icon = Icons.Rounded.Download,
                        height = 38.dp,
                        onClick = {
                            onDownloadSingle(previewItem)
                            onDismissPreview()
                        },
                    )
                } else {
                    PrismGradientButton(
                        text = "Open in Browser",
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        height = 38.dp,
                        onClick = {
                            onDismissPreview()
                            onDismiss()
                            onOpenInBrowser(previewItem.originalUrl)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPreview) {
                    Text("Close", color = TextMuted)
                }
            },
        )
    }
}

@Composable
private fun UniversalMediaCard(
    item: ResolvedMediaItem,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit,
    onOpenOriginal: () -> Unit,
    onDownload: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val badgeColor = Color(item.platform.badgeColorHex)
    val eligibilityColor = when (item.eligibility) {
        DownloadEligibility.VERIFIED_DOWNLOADABLE -> StatusSuccess
        DownloadEligibility.PREVIEW_ONLY -> StatusWarning
        DownloadEligibility.REQUIRES_AUTHENTICATION -> accent.secondary
        DownloadEligibility.UNSUPPORTED -> StatusError
        DownloadEligibility.VERIFICATION_FAILED -> StatusError
    }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.isDownloadable) Modifier.clickable(onClick = onToggleSelect) else Modifier),
        shape = RoundedCornerShape(20.dp),
        borderColor = if (selected && item.isDownloadable) accent.orb3 else GlassEdgeLo,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.isDownloadable) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = "Select media item",
                        tint = if (selected) accent.orb3 else TextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(GlassBase),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(badgeColor.copy(alpha = 0.22f))
                            .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.formatBadge.take(5), color = badgeColor, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.clip(CircleShape).background(badgeColor.copy(alpha = 0.22f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(item.formatBadge, color = badgeColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.clip(CircleShape).background(eligibilityColor.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(item.eligibility.label, color = eligibilityColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!item.durationLabel.isNullOrBlank()) {
                            Text(item.durationLabel, color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily)
                        }
                        if (!item.fileSizeLabel.isNullOrBlank()) {
                            Text(item.fileSizeLabel, color = accent.orb3, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily)
                        }
                    }

                    Text(item.title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.creator} • ${item.accessStatus}", color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassBase)
                        .clickable(onClick = onPreview)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Text("Preview", color = TextMuted, fontSize = 11.5.sp)
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassBase)
                        .clickable(onClick = onOpenOriginal)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = accent.primary, modifier = Modifier.size(14.dp))
                    Text("Open Original", color = TextPrimary, fontSize = 11.5.sp)
                }
                if (item.isDownloadable) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.25f))
                            .border(1.dp, accent.primary, CircleShape)
                            .clickable(onClick = onDownload)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Download", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
