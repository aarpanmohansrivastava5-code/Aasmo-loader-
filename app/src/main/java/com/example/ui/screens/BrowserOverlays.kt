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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReaderTheme
import com.example.data.UserSettings
import com.example.data.browser.BrowserAdShield
import com.example.data.browser.DetectedPageMedia
import com.example.data.browser.SafetyWarningLevel
import com.example.data.local.BookmarkEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.SiteRuleEntity
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
import com.example.viewmodel.BrowserTab
import com.example.viewmodel.ReaderModalState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsManagerSheet(
    tabs: List<BrowserTab>,
    activeIndex: Int,
    recentlyClosedCount: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onCloseOtherTabs: (Int) -> Unit,
    onCloseTabsToRight: (Int) -> Unit,
    onSetTabGroup: (Long, String) -> Unit,
    onRestoreLastClosed: () -> Unit,
    onToggleTabPlayback: (Long) -> Unit,
    onNewTab: (Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isGridView by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf("All") }

    val availableGroups = remember(tabs) {
        listOf("All") + (listOf("General", "Media", "Work", "Research") + tabs.map { it.groupName }).distinct()
    }
    val visibleIndexedTabs = remember(tabs, selectedGroup) {
        tabs.mapIndexed { i, t -> i to t }.filter { selectedGroup == "All" || it.second.groupName == selectedGroup }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tabs (${tabs.size})", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (isGridView) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                            contentDescription = "Toggle grid/list tabs",
                            tint = accent.orb3,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrismGradientButton(
                        text = "Private",
                        icon = Icons.Outlined.VisibilityOff,
                        height = 36.dp,
                        onClick = {
                            onNewTab(true, if (selectedGroup == "All") "General" else selectedGroup)
                            onDismiss()
                        },
                    )
                    PrismGradientButton(
                        text = "New Tab",
                        icon = Icons.Rounded.Add,
                        height = 36.dp,
                        onClick = {
                            onNewTab(false, if (selectedGroup == "All") "General" else selectedGroup)
                            onDismiss()
                        },
                    )
                }
            }

            // Tab Groups + Restore Recently Closed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableGroups) { g ->
                        val active = g == selectedGroup
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (active) GlassHi else GlassBase)
                                .border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape)
                                .clickable { selectedGroup = g }
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                        ) {
                            Text(g, color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp)
                        }
                    }
                }

                if (recentlyClosedCount > 0) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.2f))
                            .border(1.dp, accent.primary, CircleShape)
                            .clickable(onClick = onRestoreLastClosed)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(14.dp))
                        Text("Restore ($recentlyClosedCount)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    itemsIndexed(visibleIndexedTabs, key = { _, pair -> pair.second.id }) { _, (origIdx, tab) ->
                        val selected = origIdx == activeIndex
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) accent.primary.copy(alpha = 0.22f) else GlassBase)
                                .border(
                                    1.dp,
                                    if (tab.isPlayingMedia) accent.orb3 else if (selected) accent.primary else GlassEdgeLo,
                                    RoundedCornerShape(18.dp),
                                )
                                .clickable { onSelectTab(origIdx); onDismiss() }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(tab.groupName.uppercase(), color = accent.orb4, fontSize = 9.5.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close tab",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp).clickable { onCloseTab(origIdx) },
                                )
                            }
                            Text(tab.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (tab.isHome) "Prism Home" else tab.displayAddress, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tab.hasMedia || tab.isPlayingMedia) {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(accent.orb3.copy(alpha = 0.2f))
                                        .clickable { onToggleTabPlayback(tab.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        if (tab.isPlayingMedia) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = accent.orb3,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(if (tab.isPlayingMedia) "Pause" else "Play", color = accent.orb3, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    itemsIndexed(visibleIndexedTabs, key = { _, pair -> pair.second.id }) { _, (origIdx, tab) ->
                        val selected = origIdx == activeIndex
                        var tabMenuOpen by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) accent.primary.copy(alpha = 0.22f) else GlassBase)
                                .border(
                                    1.dp,
                                    when {
                                        tab.isPlayingMedia -> accent.orb3
                                        tab.isPrivate -> accent.secondary
                                        selected -> accent.primary
                                        else -> GlassEdgeLo
                                    },
                                    RoundedCornerShape(20.dp),
                                )
                                .clickable { onSelectTab(origIdx); onDismiss() }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier.clip(CircleShape).background(GlassHi).padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(tab.groupName, color = accent.orb4, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (tab.isPrivate) {
                                        Box(
                                            modifier = Modifier.clip(CircleShape).background(accent.secondary.copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 2.dp),
                                        ) {
                                            Text("PRIVATE", color = accent.secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (tab.isPlayingMedia) {
                                        Icon(Icons.Rounded.GraphicEq, contentDescription = "Audio playing", tint = accent.orb3, modifier = Modifier.size(16.dp))
                                    }
                                    Text(tab.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = when {
                                        tab.hasMedia -> "${if (tab.isPlayingMedia) "Playing" else "Paused"}: ${tab.mediaTitle}"
                                        tab.url.isNotBlank() -> tab.displayAddress
                                        else -> "Prism Home"
                                    },
                                    color = if (tab.isPlayingMedia) accent.orb3 else TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (tab.hasMedia || tab.isPlayingMedia) {
                                IconButton(
                                    onClick = { onToggleTabPlayback(tab.id) },
                                    modifier = Modifier.size(38.dp).clip(CircleShape).background(accent.orb3.copy(alpha = 0.22f)).testTag("tab_menu_media_toggle_${tab.id}"),
                                ) {
                                    Icon(
                                        imageVector = if (tab.isPlayingMedia) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (tab.isPlayingMedia) "Pause tab audio" else "Play tab audio",
                                        tint = accent.orb3,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Box {
                                IconButton(onClick = { tabMenuOpen = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Tab options", tint = TextMuted, modifier = Modifier.size(17.dp))
                                }
                                DropdownMenu(expanded = tabMenuOpen, onDismissRequest = { tabMenuOpen = false }, containerColor = SurfaceElevated) {
                                    listOf("General", "Media", "Work", "Research").forEach { grp ->
                                        DropdownMenuItem(
                                            text = { Text("Move to group: $grp", color = TextPrimary) },
                                            onClick = { tabMenuOpen = false; onSetTabGroup(tab.id, grp) },
                                        )
                                    }
                                    if (tabs.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Close Other Tabs", color = TextPrimary) },
                                            onClick = { tabMenuOpen = false; onCloseOtherTabs(origIdx) },
                                        )
                                    }
                                    if (origIdx < tabs.lastIndex) {
                                        DropdownMenuItem(
                                            text = { Text("Close Tabs to Right", color = TextPrimary) },
                                            onClick = { tabMenuOpen = false; onCloseTabsToRight(origIdx) },
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { onCloseTab(origIdx) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close tab", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksManagerSheet(
    bookmarks: List<BookmarkEntity>,
    onNavigate: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }
    var sortAlpha by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val folders = remember(bookmarks) { listOf("All") + bookmarks.map { it.folderName }.distinct() }
    val filtered = remember(bookmarks, search, selectedFolder, sortAlpha) {
        bookmarks.filter {
            (selectedFolder == "All" || it.folderName == selectedFolder) &&
                (search.isBlank() || it.title.contains(search, true) || it.url.contains(search, true))
        }.let { list -> if (sortAlpha) list.sortedBy { it.title.lowercase() } else list }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SurfaceElevated, contentColor = TextPrimary) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = accent.orb4)
                    Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { sortAlpha = !sortAlpha }) { Text(if (sortAlpha) "A-Z" else "Custom", color = accent.orb3) }
                    TextButton(onClick = { showImportDialog = true }) { Text("Import", color = TextPrimary) }
                    TextButton(onClick = {
                        val json = onExportJson()
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Aasmo Bookmarks", json))
                        feedback = "Copied ${bookmarks.size} bookmarks JSON to clipboard."
                    }) { Text("Export", color = accent.orb3) }
                }
            }

            if (feedback != null) {
                Text(feedback!!, color = StatusSuccess, fontSize = 12.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (search.isEmpty()) Text("Search bookmarks...", color = TextMuted, fontSize = 13.sp)
                    BasicTextField(value = search, onValueChange = { search = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, fontFamily = FigtreeFontFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(folders) { f ->
                    val active = f == selectedFolder
                    Box(
                        modifier = Modifier.clip(CircleShape).background(if (active) GlassHi else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { selectedFolder = f }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(f, color = if (active) TextPrimary else TextMuted, fontSize = 12.sp)
                    }
                }
            }

            LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.url }) { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassBase).clickable { onNavigate(b.url); onDismiss() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(b.badgeColorHex)), contentAlignment = Alignment.Center) {
                            Text(b.badgeLetter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${b.title} • ${b.folderName}", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.url, color = TextMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemoveBookmark(b.url) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove bookmark", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var jsonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = SurfaceElevated,
            title = { Text("Import Bookmarks JSON", color = TextPrimary) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(OmniInsetBg).border(1.dp, GlassEdgeLo, RoundedCornerShape(14.dp)).padding(10.dp),
                ) {
                    if (jsonInput.isEmpty()) Text("[{\"title\":\"Site\",\"url\":\"https://...\"}]", color = TextMuted, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily)
                    BasicTextField(value = jsonInput, onValueChange = { jsonInput = it }, textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                PrismGradientButton(
                    text = "Import",
                    height = 36.dp,
                    enabled = jsonInput.isNotBlank(),
                    onClick = { onImportJson(jsonInput); showImportDialog = false },
                )
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("Cancel", color = TextMuted) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSnifferSheet(
    items: List<DetectedPageMedia>,
    pageUrl: String,
    onDownloadItem: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated, contentColor = TextPrimary) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Detected Page Media (${items.size})", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            if (items.isEmpty()) {
                Text("No direct video, audio, or high-res image elements detected yet. Try Universal Media Extractor for URL/social inspection.", color = TextMuted, fontSize = 13.sp)
                PrismGradientButton(text = "Download Page URL", icon = Icons.Rounded.Download, onClick = { onDownloadItem(pageUrl, "Page_Archive"); onDismiss() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(items, key = { it.url }) { media ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GlassBase).border(1.dp, GlassEdgeLo, RoundedCornerShape(18.dp)).clickable { onDownloadItem(media.url, media.title); onDismiss() }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.clip(CircleShape).background(accent.primary.copy(alpha = 0.24f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(media.mediaType, color = accent.orb3, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(media.title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(media.url, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Rounded.Download, contentDescription = "Download media", tint = accent.orb3)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: List<HistoryEntity>,
    onNavigate: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearHistory: (hoursBack: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    var groupByDomain by remember { mutableStateOf(false) }
    var clearMenuExpanded by remember { mutableStateOf(false) }

    val filtered = remember(history, search) {
        if (search.isBlank()) history
        else history.filter { it.title.contains(search, true) || it.url.contains(search, true) || it.domain.contains(search, true) }
    }
    val todayMidnight = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
    }
    val grouped = remember(filtered, groupByDomain, todayMidnight) {
        if (groupByDomain) {
            filtered.groupBy { it.domain.ifBlank { BrowserAdShield.extractHost(it.url) ?: "Other" } }
        } else {
            filtered.groupBy { if (it.visitedAt >= todayMidnight) "Today" else SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.visitedAt)) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SurfaceElevated, contentColor = TextPrimary) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = TextPrimary)
                    Text("History", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { groupByDomain = !groupByDomain }) {
                        Text(if (groupByDomain) "By Domain" else "By Date", color = accent.orb3)
                    }
                    if (history.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { clearMenuExpanded = true }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history range", tint = TextMuted)
                            }
                            DropdownMenu(expanded = clearMenuExpanded, onDismissRequest = { clearMenuExpanded = false }, containerColor = SurfaceElevated) {
                                DropdownMenuItem(text = { Text("Clear Last Hour", color = TextPrimary) }, onClick = { clearMenuExpanded = false; onClearHistory(1) })
                                DropdownMenuItem(text = { Text("Clear Last 24 Hours", color = TextPrimary) }, onClick = { clearMenuExpanded = false; onClearHistory(24) })
                                DropdownMenuItem(text = { Text("Clear All History", color = StatusError) }, onClick = { clearMenuExpanded = false; onClearHistory(null) })
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (search.isEmpty()) Text("Filter history by title or site...", color = TextMuted, fontSize = 13.sp)
                    BasicTextField(value = search, onValueChange = { search = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, fontFamily = FigtreeFontFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                }
            }

            if (filtered.isEmpty()) {
                Text("No matching history entries. Private tabs never log history.", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp))
            } else {
                LazyColumn(modifier = Modifier.height(350.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (sectionTitle, entries) ->
                        item(key = "hdr_$sectionTitle") {
                            Text(sectionTitle, color = accent.orb3, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                        }
                        items(entries, key = { it.id }) { h ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassBase).clickable { onNavigate(h.url); onDismiss() }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(h.title, color = TextPrimary, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(h.url, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onDeleteItem(h.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete history entry", tint = TextMuted, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSecuritySheet(
    pageUrl: String,
    sslErrorWarning: String?,
    blockedTrackers: Int,
    siteRule: SiteRuleEntity?,
    onUpdateSiteRule: (String, (SiteRuleEntity) -> SiteRuleEntity) -> Unit,
    onResetSiteRule: (String) -> Unit,
    onOpenClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    val host = remember(pageUrl) { BrowserAdShield.extractHost(pageUrl).orEmpty() }
    val safety = remember(pageUrl, sslErrorWarning) { BrowserAdShield.inspectUrlSafety(pageUrl, sslErrorWarning) }
    val safetyColor = when (safety.level) {
        SafetyWarningLevel.SAFE_HTTPS -> StatusSuccess
        SafetyWarningLevel.HTTP_UNENCRYPTED -> StatusWarning
        SafetyWarningLevel.CERT_ERROR, SafetyWarningLevel.SUSPICIOUS_URL -> StatusError
    }
    val rule = siteRule ?: SiteRuleEntity(host = host)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated, contentColor = TextPrimary) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = safetyColor)
                Column {
                    Text(host.ifBlank { "Browser Security" }, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(safety.summary, color = safetyColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(safety.detail, color = TextMuted, fontSize = 12.5.sp)

            if (host.isNotBlank()) {
                Text("Blocked Ads & 3P Trackers on Tab: $blockedTrackers", color = accent.orb3, fontSize = 12.5.sp, fontFamily = JetBrainsMonoFamily)

                SiteToggleRow("Site Ad Shield", "Block ads & trackers on $host", rule.adShieldEnabled) { v ->
                    onUpdateSiteRule(host) { it.copy(adShieldEnabled = v) }
                }
                SiteToggleRow("Allowlist Site", "Disable all blocking on $host", rule.allowListed) { v ->
                    onUpdateSiteRule(host) { it.copy(allowListed = v) }
                }
                SiteToggleRow("Site Desktop View", "Always request desktop layout on $host", rule.desktopMode) { v ->
                    onUpdateSiteRule(host) { it.copy(desktopMode = v) }
                }
                SiteToggleRow("Allow JavaScript", "Required by most modern web apps", rule.allowJavascript) { v ->
                    onUpdateSiteRule(host) { it.copy(allowJavascript = v) }
                }
                SiteToggleRow("Allow Location", "Geolocation permission for $host", rule.allowLocation) { v ->
                    onUpdateSiteRule(host) { it.copy(allowLocation = v) }
                }
                SiteToggleRow("Allow Camera", "WebRTC camera access for $host", rule.allowCamera) { v ->
                    onUpdateSiteRule(host) { it.copy(allowCamera = v) }
                }
                SiteToggleRow("Allow Microphone", "WebRTC microphone access for $host", rule.allowMicrophone) { v ->
                    onUpdateSiteRule(host) { it.copy(allowMicrophone = v) }
                }
                SiteToggleRow("Allow Pop-ups", "Permit windows opened automatically", rule.allowPopups) { v ->
                    onUpdateSiteRule(host) { it.copy(allowPopups = v) }
                }
                if (siteRule != null) {
                    TextButton(onClick = { onResetSiteRule(host) }) { Text("Reset $host Permissions", color = StatusWarning) }
                }
            }

            PrismGradientButton(
                text = "Clear Browsing Data",
                icon = Icons.Outlined.DeleteSweep,
                onClick = { onDismiss(); onOpenClearBrowsingData() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SiteToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalPrismColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassBase).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent.primary))
    }
}

@Composable
fun ClearBrowsingDataDialog(
    onConfirmClear: (history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearSiteData by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        title = { Text("Clear Browsing Data", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClearOptionRow("Browsing History", clearHistory) { clearHistory = it }
                ClearOptionRow("Cookies & Active Logins", clearCookies) { clearCookies = it }
                ClearOptionRow("Cached WebView Assets", clearCache) { clearCache = it }
                ClearOptionRow("Site Storage & Per-Site Rules", clearSiteData) { clearSiteData = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmClear(clearHistory, clearCookies, clearCache, clearSiteData); onDismiss() }) {
                Text("Clear Data", color = StatusError, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
    )
}

@Composable
private fun ClearOptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalPrismColors.current
    Row(modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = accent.primary))
        Text(label, color = TextPrimary, fontSize = 13.5.sp)
    }
}

@Composable
fun ReaderModeDialog(
    state: ReaderModalState,
    settings: UserSettings,
    onChangeFontSize: (Int) -> Unit,
    onChangeLineHeight: (Float) -> Unit,
    onChangeTheme: (ReaderTheme) -> Unit,
    onSaveArticle: (title: String, text: String) -> Unit,
    onSummarizeWithAi: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    if (!state.visible) return
    val theme = settings.readerTheme
    val bgColor = Color(theme.bgHex)
    val fgColor = Color(theme.textHex)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.title, color = fgColor, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { onChangeFontSize(settings.readerFontSizeSp - 1) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("A-", color = fgColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Text("${settings.readerFontSizeSp}sp", color = fgColor.copy(alpha = 0.75f), fontSize = 11.5.sp, fontFamily = JetBrainsMonoFamily)
                        Box(
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { onChangeFontSize(settings.readerFontSizeSp + 1) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("A+", color = fgColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable {
                                val next = when {
                                    settings.readerLineHeightMultiplier < 1.4f -> 1.55f
                                    settings.readerLineHeightMultiplier < 1.75f -> 1.85f
                                    else -> 1.35f
                                }
                                onChangeLineHeight(next)
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) { Text("Spacing", color = fgColor, fontSize = 11.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ReaderTheme.entries.forEach { rt ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(rt.bgHex))
                                    .border(if (rt == theme) 2.dp else 1.dp, if (rt == theme) accent.primary else GlassEdgeLo, CircleShape)
                                    .clickable { onChangeTheme(rt) },
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = state.text,
                    color = fgColor.copy(alpha = 0.90f),
                    fontSize = settings.readerFontSizeSp.sp,
                    lineHeight = (settings.readerFontSizeSp * settings.readerLineHeightMultiplier).sp,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onSaveArticle(state.title, state.text) }) {
                    Icon(Icons.Outlined.Save, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", color = accent.orb3)
                }
                PrismGradientButton(
                    text = "AI TL;DR",
                    icon = Icons.Outlined.AutoAwesome,
                    height = 36.dp,
                    onClick = { onDismiss(); onSummarizeWithAi(state.title, state.text) },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = fgColor.copy(alpha = 0.7f)) } },
    )
}

@Composable
fun AddSpeedDialDialog(
    onAdd: (title: String, url: String, folderName: String, colorHex: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalPrismColors.current
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("General") }
    var selectedColor by remember { mutableStateOf(0xFF27D3C3L) }
    val palette = listOf(0xFF27D3C3L, 0xFF5B6CFFL, 0xFFFF6FA8L, 0xFFFFB454L, 0xFFFF4D4DL, 0xFF10B981L)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        title = { Text("Add Speed Dial Tile", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.fillMaxWidth().clip(CircleShape).background(OmniInsetBg).border(1.dp, GlassEdgeLo, CircleShape).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (title.isEmpty()) Text("Label (e.g. Hacker News)", color = TextMuted, fontSize = 13.5.sp)
                    BasicTextField(value = title, onValueChange = { title = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                }
                Box(modifier = Modifier.fillMaxWidth().clip(CircleShape).background(OmniInsetBg).border(1.dp, GlassEdgeLo, CircleShape).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (url.isEmpty()) Text("URL (e.g. news.ycombinator.com)", color = TextMuted, fontSize = 13.5.sp)
                    BasicTextField(value = url, onValueChange = { url = it }, singleLine = true, textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily), cursorBrush = SolidColor(accent.primary), modifier = Modifier.fillMaxWidth())
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("General", "Media", "Social", "Dev")) { f ->
                        val active = f == folder
                        Box(modifier = Modifier.clip(CircleShape).background(if (active) GlassHi else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { folder = f }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(f, color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { hex ->
                        Box(
                            modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(hex)).border(if (hex == selectedColor) 2.dp else 1.dp, if (hex == selectedColor) Color.White else GlassEdgeLo, CircleShape).clickable { selectedColor = hex },
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrismGradientButton(
                text = "Pin Tile",
                height = 38.dp,
                enabled = url.isNotBlank(),
                onClick = { onAdd(title, url, folder, selectedColor); onDismiss() },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
    )
}
