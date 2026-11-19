package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.files.FileCategory
import com.example.data.files.FileDetailReport
import com.example.data.files.FileSortMode
import com.example.data.files.ManagedFileItem
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.PrismGradientButton
import com.example.ui.components.liquidGlass
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
import com.example.viewmodel.DownloadFilter
import com.example.viewmodel.FileInspectorState
import java.io.File

@Composable
fun DownloaderScreen(
    downloads: List<DownloadEntity>,
    allDownloadsCount: Int,
    filter: DownloadFilter,
    defaultChunks: Int,
    wifiOnlyDownloads: Boolean,
    inspectorState: FileInspectorState?,
    // File Manager props
    managedFiles: List<ManagedFileItem>,
    managedFolders: List<File>,
    currentFolderPath: String?,
    fileCategory: FileCategory,
    fileSortMode: FileSortMode,
    fileSearchQuery: String,
    fileDetailReport: FileDetailReport?,
    onFilterChange: (DownloadFilter) -> Unit,
    onStartDownload: (url: String, chunks: Int) -> Unit,
    onPause: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    onExportToDownloads: (DownloadEntity) -> Unit,
    onBuildOpenIntent: (DownloadEntity) -> Intent?,
    onBuildShareIntent: (DownloadEntity) -> Intent?,
    onOpenInspector: (DownloadEntity) -> Unit,
    onDismissInspector: () -> Unit,
    onAnalyzeItem: (DownloadEntity) -> Unit,
    onOpenUniversalExtractor: () -> Unit,
    onOpenGitHubHub: () -> Unit,
    onOpenSocialHub: () -> Unit,
    onOpenBrowser: () -> Unit,
    // File Manager callbacks
    onRefreshFiles: () -> Unit,
    onSelectFileCategory: (FileCategory) -> Unit,
    onSelectFileSort: (FileSortMode) -> Unit,
    onFileSearchChange: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onGoUpFolder: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onCopyOrMoveFile: (String, String, Boolean) -> Unit,
    onDeleteFilePath: (String) -> Unit,
    onInspectManagedFile: (ManagedFileItem) -> Unit,
    onDismissManagedFileDetails: () -> Unit,
    onBuildManagedOpenIntent: (ManagedFileItem) -> Intent?,
    onBuildManagedShareIntent: (ManagedFileItem) -> Intent?,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Queue, 1 = Files
    var urlInput by remember { mutableStateOf("") }
    var selectedChunks by remember(defaultChunks) { mutableIntStateOf(defaultChunks) }

    val activeItems = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name }
    }
    val totalSpeed = remember(activeItems) { activeItems.sumOf { it.speedBytesPerSec } }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 1000.dp).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top Queue | File Manager Switcher
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                Row(modifier = Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Download Queue", "File Manager").forEachIndexed { idx, label ->
                        val active = selectedSubTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) Brush.linearGradient(listOf(accent.primary, accent.secondary))
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                    CircleShape,
                                )
                                .clickable {
                                    selectedSubTab = idx
                                    if (idx == 1) onRefreshFiles()
                                }
                                .testTag("loader_subtab_$idx"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (active) Color.White else TextMuted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (selectedSubTab == 1) {
                FileManagerContent(
                    files = managedFiles,
                    folders = managedFolders,
                    currentFolderPath = currentFolderPath,
                    category = fileCategory,
                    sortMode = fileSortMode,
                    searchQuery = fileSearchQuery,
                    detailReport = fileDetailReport,
                    onRefresh = onRefreshFiles,
                    onSelectCategory = onSelectFileCategory,
                    onSelectSort = onSelectFileSort,
                    onSearchChange = onFileSearchChange,
                    onOpenFolder = onOpenFolder,
                    onGoUpFolder = onGoUpFolder,
                    onCreateFolder = onCreateFolder,
                    onRenameFile = onRenameFile,
                    onCopyOrMove = onCopyOrMoveFile,
                    onDeleteFile = onDeleteFilePath,
                    onInspectFile = onInspectManagedFile,
                    onDismissDetails = onDismissManagedFileDetails,
                    onBuildOpenIntent = onBuildManagedOpenIntent,
                    onBuildShareIntent = onBuildManagedShareIntent,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 1. Dashboard Telemetry Header
                    item(key = "header") {
                        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Aasmo Loader", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Packet Division Engine", color = TextMuted, fontSize = 13.sp)
                                            if (wifiOnlyDownloads) {
                                                Icon(Icons.Rounded.Wifi, contentDescription = "Wi-Fi Only", tint = accent.orb3, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TelemetryMiniCard("ACTIVE", "${activeItems.size}", accent.primary, Modifier.weight(1f))
                                    TelemetryMiniCard("SPEED", formatSpeed(totalSpeed), accent.orb3, Modifier.weight(1.3f))
                                    TelemetryMiniCard("PACKETS", "${selectedChunks}x", accent.secondary, Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 2. Universal Extractor Banner + Direct URL Box
                    item(key = "url_box") {
                        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PrismGradientButton(
                                    text = "Launch Universal Media Extractor",
                                    icon = Icons.Outlined.CloudDownload,
                                    onClick = onOpenUniversalExtractor,
                                    modifier = Modifier.fillMaxWidth().testTag("loader_open_universal_button"),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, tint = accent.primary, modifier = Modifier.size(18.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (urlInput.isEmpty()) Text("Or paste direct file URL (APK, ZIP, MP4, PDF)...", color = TextMuted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        BasicTextField(
                                            value = urlInput,
                                            onValueChange = { urlInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Medium),
                                            cursorBrush = SolidColor(accent.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                            keyboardActions = KeyboardActions(onGo = {
                                                focusManager.clearFocus()
                                                if (urlInput.isNotBlank()) {
                                                    onStartDownload(urlInput, selectedChunks)
                                                    urlInput = ""
                                                }
                                            }),
                                            modifier = Modifier.fillMaxWidth().testTag("loader_url_input"),
                                        )
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = accent.orb4, modifier = Modifier.size(16.dp))
                                        listOf(4, 8, 16).forEach { count ->
                                            val chosen = selectedChunks == count
                                            Box(
                                                modifier = Modifier.defaultMinSize(minHeight = 36.dp).clip(CircleShape).background(if (chosen) accent.primary.copy(alpha = 0.28f) else GlassBase).border(1.dp, if (chosen) accent.primary else GlassEdgeLo, CircleShape).clickable { selectedChunks = count }.padding(horizontal = 12.dp, vertical = 7.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("${count}x", color = if (chosen) TextPrimary else TextMuted, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }

                                    PrismGradientButton(
                                        text = "Download",
                                        icon = Icons.Rounded.Download,
                                        enabled = urlInput.isNotBlank(),
                                        onClick = {
                                            focusManager.clearFocus()
                                            onStartDownload(urlInput, selectedChunks)
                                            urlInput = ""
                                        },
                                        modifier = Modifier.testTag("loader_start_button"),
                                    )
                                }
                            }
                        }
                    }

                    // 3. Quick Platform Access Shortcuts
                    item(key = "shortcuts") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { QuickPlatformChip("GitHub Releases", Icons.Rounded.Code, Color(0xFF6B5BFF), onOpenGitHubHub) }
                            item { QuickPlatformChip("Social @handle", Icons.Outlined.Hub, Color(0xFFFF6FA8), onOpenSocialHub) }
                            item { QuickPlatformChip("Prism Search", Icons.Outlined.Language, Color(0xFF27D3C3), onOpenBrowser) }
                        }
                    }

                    // 4. Filter Bar (All, Active, Queued, Paused, Completed, Failed)
                    item(key = "filters") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                items(DownloadFilter.entries) { f ->
                                    val selected = f == filter
                                    Box(
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp).clip(CircleShape).background(if (selected) GlassHi else GlassBase).border(1.dp, if (selected) accent.primary else GlassEdgeLo, CircleShape).clickable { onFilterChange(f) }.padding(horizontal = 13.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(f.label, color = if (selected) TextPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (allDownloadsCount > 0) {
                                Text(
                                    text = "Clear Done",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clip(CircleShape).clickable(onClick = onClearCompleted).padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    // 5. Downloads List
                    if (downloads.isEmpty()) {
                        item(key = "empty") {
                            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = accent.primary, modifier = Modifier.size(40.dp))
                                    Text("No transfers in this filter", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Use Universal Media Extractor, paste a direct URL above, or switch to File Manager.", color = TextMuted, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        items(items = downloads, key = { it.id }) { item ->
                            DownloadItemCard(
                                item = item,
                                onPause = { onPause(item.id) },
                                onCancel = { onCancel(item.id) },
                                onResume = { onResume(item.id) },
                                onDelete = { onDelete(item.id) },
                                onAnalyze = { onAnalyzeItem(item) },
                                onExport = { onExportToDownloads(item) },
                                onInspect = { onOpenInspector(item) },
                                onOpenFile = { onBuildOpenIntent(item)?.let { intent -> runCatching { context.startActivity(intent) } } },
                                onShareFile = { onBuildShareIntent(item)?.let { intent -> runCatching { context.startActivity(Intent.createChooser(intent, "Share ${item.fileName}")) } } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (inspectorState != null) {
        FileInspectorDialog(state = inspectorState, onDismiss = onDismissInspector)
    }
}

@Composable
private fun TelemetryMiniCard(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(OmniInsetBg).border(1.dp, GlassEdgeLo, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Text(value, color = tint, fontSize = 16.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun QuickPlatformChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.defaultMinSize(minHeight = 42.dp).liquidGlass(shape = CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
        Text(label, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onAnalyze: () -> Unit,
    onExport: () -> Unit,
    onInspect: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val status = runCatching { DownloadStatus.valueOf(item.status) }.getOrDefault(DownloadStatus.QUEUED)
    val chunkProgresses = remember(item.chunkProgressCsv, item.chunkCount) {
        val parsed = item.chunkProgressCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parsed.isEmpty()) List(item.chunkCount.coerceIn(2, 16)) { 0 } else parsed
    }
    val overallFraction = remember(item.downloadedBytes, item.totalBytes, status) {
        when {
            status == DownloadStatus.COMPLETED -> 1f
            item.totalBytes > 0 -> (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> chunkProgresses.average().toFloat() / 100f
        }
    }

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.clip(CircleShape).background(accent.primary.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(item.source, color = accent.orb3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (item.batchId.isNotBlank()) {
                            Box(modifier = Modifier.clip(CircleShape).background(accent.secondary.copy(alpha = 0.2f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("BATCH", color = accent.secondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("${item.chunkCount}x CHUNKS", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.url, color = TextMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (status == DownloadStatus.COMPLETED) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Completed", tint = StatusSuccess, modifier = Modifier.size(24.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                chunkProgresses.forEach { pct ->
                    val frac = (pct / 100f).coerceIn(0f, 1f)
                    Box(modifier = Modifier.weight(1f).height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
                        if (frac > 0f) {
                            Box(
                                modifier = Modifier.fillMaxWidth(frac).height(7.dp).clip(CircleShape).background(
                                    when (status) {
                                        DownloadStatus.COMPLETED -> Brush.horizontalGradient(listOf(StatusSuccess, accent.orb3))
                                        DownloadStatus.FAILED -> Brush.horizontalGradient(listOf(StatusError, StatusWarning))
                                        else -> Brush.horizontalGradient(listOf(accent.primary, accent.orb3))
                                    }
                                )
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val statusLabel = when (status) {
                    DownloadStatus.DOWNLOADING -> "${(overallFraction * 100).toInt()}% • ${formatSpeed(item.speedBytesPerSec)}"
                    DownloadStatus.COMPLETED -> "Saved • ${formatBytes(item.downloadedBytes)}"
                    DownloadStatus.PAUSED -> item.errorMessage ?: "Paused • ${formatBytes(item.downloadedBytes)}"
                    DownloadStatus.FAILED -> item.errorMessage ?: "Transfer failed"
                    DownloadStatus.QUEUED -> item.errorMessage ?: "Queued..."
                }
                Text(
                    text = statusLabel,
                    color = if (status == DownloadStatus.FAILED) StatusError else TextMuted,
                    fontSize = 12.sp,
                    fontFamily = JetBrainsMonoFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == DownloadStatus.COMPLETED) {
                        IconButton(onClick = onInspect, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Inspect ZIP/Checksum", tint = accent.orb4, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = "Save to Downloads", tint = accent.orb3, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onOpenFile, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open file", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onShareFile, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share file", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        IconButton(onClick = onAnalyze, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = "Inspect with AI", tint = accent.secondary, modifier = Modifier.size(18.dp))
                        }
                        if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED) {
                            IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Pause, contentDescription = "Pause", tint = TextPrimary, modifier = Modifier.size(19.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Cancel, contentDescription = "Cancel", tint = StatusWarning, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                                Icon(if (status == DownloadStatus.FAILED) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow, contentDescription = "Resume", tint = accent.orb3, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInspectorDialog(
    state: FileInspectorState,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    var expectedHash by remember { mutableStateOf("") }
    val hashMatches = expectedHash.isNotBlank() && expectedHash.trim().equals(state.computedSha256, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        title = { Text(state.entity.fileName, color = TextPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.height(340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(16.dp))
                    Text("SHA-256 Checksum", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(state.computedSha256.ifBlank { "Unavailable" }, color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMonoFamily)
                Box(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).background(OmniInsetBg).border(1.dp, when { expectedHash.isBlank() -> GlassEdgeLo; hashMatches -> StatusSuccess; else -> StatusError }, CircleShape).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (expectedHash.isEmpty()) Text("Paste expected SHA-256 to verify...", color = TextMuted, fontSize = 12.sp)
                    BasicTextField(value = expectedHash, onValueChange = { expectedHash = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                }
                if (expectedHash.isNotBlank()) {
                    Text(if (hashMatches) "Checksum VERIFIED (Exact Match)" else "Checksum MISMATCH", color = if (hashMatches) StatusSuccess else StatusError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (state.zipEntries.isNotEmpty()) {
                    Text("ZIP Archive Contents (${state.zipEntries.size} entries)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    state.zipEntries.forEach { entry ->
                        Text("${if (entry.isDirectory) "[DIR] " else ""}${entry.name} (${entry.sizeBytes / 1024} KB)", color = TextMuted, fontSize = 11.5.sp, fontFamily = JetBrainsMonoFamily)
                    }
                } else if (!state.textPreview.isNullOrBlank()) {
                    Text("Text Preview", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(state.textPreview, color = TextMuted, fontSize = 11.5.sp, fontFamily = JetBrainsMonoFamily)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = accent.orb3) } },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB/s", mb)
        else -> String.format("%.0f KB/s", kb)
    }
}
