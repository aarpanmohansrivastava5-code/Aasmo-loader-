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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.files.FileCategory
import com.example.data.files.FileDetailReport
import com.example.data.files.FileSortMode
import com.example.data.files.ManagedFileItem
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
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerContent(
    files: List<ManagedFileItem>,
    folders: List<File>,
    currentFolderPath: String?,
    category: FileCategory,
    sortMode: FileSortMode,
    searchQuery: String,
    detailReport: FileDetailReport?,
    onRefresh: () -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onSelectSort: (FileSortMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onGoUpFolder: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onCopyOrMove: (path: String, targetFolder: String, move: Boolean) -> Unit,
    onDeleteFile: (String) -> Unit,
    onInspectFile: (ManagedFileItem) -> Unit,
    onDismissDetails: () -> Unit,
    onBuildOpenIntent: (ManagedFileItem) -> Intent?,
    onBuildShareIntent: (ManagedFileItem) -> Intent?,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    val context = LocalContext.current

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ManagedFileItem?>(null) }
    var transferTarget by remember { mutableStateOf<Pair<ManagedFileItem, Boolean>?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<ManagedFileItem?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Search + Actions bar
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        if (currentFolderPath != null) {
                            IconButton(onClick = onGoUpFolder, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up folder", tint = accent.orb3)
                            }
                        }
                        Text(
                            text = if (currentFolderPath != null) File(currentFolderPath).name else "Aasmo File Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create folder", tint = accent.orb3, modifier = Modifier.size(20.dp))
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort files", tint = TextPrimary, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, containerColor = SurfaceElevated) {
                                FileSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (mode == sortMode) "• ${mode.label}" else mode.label,
                                                color = if (mode == sortMode) accent.orb3 else TextPrimary,
                                            )
                                        },
                                        onClick = { sortMenuExpanded = false; onSelectSort(mode) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh files", tint = TextMuted, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                // Search field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(CircleShape)
                        .background(OmniInsetBg, CircleShape)
                        .border(1.dp, GlassEdgeLo, CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) Text("Search downloaded files & folders...", color = TextMuted, fontSize = 13.sp)
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, fontFamily = FigtreeFontFamily),
                            cursorBrush = SolidColor(accent.primary),
                            modifier = Modifier.fillMaxWidth().testTag("file_manager_search_input"),
                        )
                    }
                }

                // Category Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FileCategory.entries) { cat ->
                        val active = cat == category
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 34.dp)
                                .clip(CircleShape)
                                .background(if (active) GlassHi else GlassBase)
                                .border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape)
                                .clickable { onSelectCategory(cat) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cat.label, color = if (active) TextPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // File List
        if (files.isEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = accent.primary, modifier = Modifier.size(36.dp))
                    Text("No files in this view", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text("Completed downloads, folders, and saved Reader Mode articles appear here.", color = TextMuted, fontSize = 12.5.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.path }) { item ->
                    ManagedFileRow(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                onOpenFolder(item.path)
                            } else {
                                onBuildOpenIntent(item)?.let { intent -> runCatching { context.startActivity(intent) } }
                                    ?: onInspectFile(item)
                            }
                        },
                        onShare = {
                            onBuildShareIntent(item)?.let { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, "Share ${item.name}")) }
                            }
                        },
                        onInspect = { onInspectFile(item) },
                        onRename = { renameTarget = item },
                        onCopy = { transferTarget = item to false },
                        onMove = { transferTarget = item to true },
                        onDelete = { deleteConfirmTarget = item },
                    )
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = SurfaceElevated,
            title = { Text("Create Folder", color = TextPrimary) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(OmniInsetBg)
                        .border(1.dp, GlassEdgeLo, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (name.isEmpty()) Text("Folder name...", color = TextMuted, fontSize = 13.5.sp)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily),
                        cursorBrush = SolidColor(accent.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                PrismGradientButton(
                    text = "Create",
                    height = 36.dp,
                    enabled = name.isNotBlank(),
                    onClick = {
                        onCreateFolder(name)
                        showCreateFolderDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel", color = TextMuted) }
            },
        )
    }

    renameTarget?.let { target ->
        var updatedName by remember(target.path) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = SurfaceElevated,
            title = { Text("Rename Item", color = TextPrimary) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(OmniInsetBg)
                        .border(1.dp, GlassEdgeLo, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = updatedName,
                        onValueChange = { updatedName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily),
                        cursorBrush = SolidColor(accent.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                PrismGradientButton(
                    text = "Rename",
                    height = 36.dp,
                    enabled = updatedName.isNotBlank(),
                    onClick = {
                        onRenameFile(target.path, updatedName)
                        renameTarget = null
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = TextMuted) }
            },
        )
    }

    transferTarget?.let { (item, move) ->
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            containerColor = SurfaceElevated,
            title = { Text(if (move) "Move '${item.name}' to" else "Copy '${item.name}' to", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.height(240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassBase)
                                .border(1.dp, GlassEdgeLo, RoundedCornerShape(14.dp))
                                .clickable {
                                    onCopyOrMove(item.path, folder.absolutePath, move)
                                    transferTarget = null
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = accent.orb4, modifier = Modifier.size(18.dp))
                            Text(folder.name, color = TextPrimary, fontSize = 13.5.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { transferTarget = null }) { Text("Cancel", color = TextMuted) }
            },
        )
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = SurfaceElevated,
            title = { Text("Delete '${target.name}'?", color = TextPrimary) },
            text = { Text("This permanently removes the file or folder from Aasmo storage.", color = TextMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFile(target.path)
                        deleteConfirmTarget = null
                    }
                ) {
                    Text("Delete", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) { Text("Cancel", color = TextMuted) }
            },
        )
    }

    if (detailReport != null) {
        AlertDialog(
            onDismissRequest = onDismissDetails,
            containerColor = SurfaceElevated,
            title = { Text(detailReport.item.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("MIME: ${detailReport.item.mimeType}", color = accent.orb3, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily)
                    Text("Size: ${formatManagedBytes(detailReport.item.sizeBytes)} • Folder: ${detailReport.item.relativeFolder}", color = TextMuted, fontSize = 12.sp)
                    if (detailReport.sha256.isNotBlank()) {
                        Text("SHA-256", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        Text(detailReport.sha256, color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMonoFamily)
                    }
                    if (detailReport.zipEntries.isNotEmpty()) {
                        Text("ZIP Contents (${detailReport.zipEntries.size} entries)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        detailReport.zipEntries.take(40).forEach { e ->
                            Text(
                                text = "${if (e.isDirectory) "[DIR] " else ""}${e.name} (${e.sizeBytes / 1024} KB)",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFamily,
                            )
                        }
                    } else if (!detailReport.textPreview.isNullOrBlank()) {
                        Text("Text Preview", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        Text(detailReport.textPreview, color = TextMuted, fontSize = 11.5.sp, fontFamily = JetBrainsMonoFamily)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissDetails) { Text("Close", color = accent.orb3) }
            },
        )
    }
}

@Composable
private fun ManagedFileRow(
    item: ManagedFileItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInspect: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalPrismColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    val icon = when {
        item.isDirectory -> Icons.Outlined.Folder
        item.mimeType.startsWith("image/") -> Icons.Outlined.Image
        item.mimeType.startsWith("video/") -> Icons.Outlined.VideoFile
        item.mimeType.startsWith("audio/") -> Icons.Outlined.MusicNote
        item.extension in setOf("zip", "tar", "gz", "apk") -> Icons.Outlined.FolderZip
        else -> Icons.Outlined.Description
    }
    val iconTint = when {
        item.isDirectory -> accent.orb4
        item.mimeType.startsWith("image/") -> accent.secondary
        item.mimeType.startsWith("video/") -> accent.primary
        item.mimeType.startsWith("audio/") -> accent.orb3
        else -> TextMuted
    }
    val dateLabel = remember(item.lastModified) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
    }

    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sizeStr = if (item.isDirectory) "${item.sizeBytes} items" else formatManagedBytes(item.sizeBytes)
                Text("${item.relativeFolder} • $sizeStr • $dateLabel", color = TextMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "File options", tint = TextMuted)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, containerColor = SurfaceElevated) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(text = { Text("Open", color = TextPrimary) }, onClick = { menuExpanded = false; onClick() })
                        DropdownMenuItem(text = { Text("Share", color = TextPrimary) }, onClick = { menuExpanded = false; onShare() })
                        DropdownMenuItem(text = { Text("Details & SHA-256", color = TextPrimary) }, onClick = { menuExpanded = false; onInspect() })
                        DropdownMenuItem(text = { Text("Copy to...", color = TextPrimary) }, onClick = { menuExpanded = false; onCopy() })
                        DropdownMenuItem(text = { Text("Move to...", color = TextPrimary) }, onClick = { menuExpanded = false; onMove() })
                    }
                    DropdownMenuItem(text = { Text("Rename", color = TextPrimary) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete", color = StatusError) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

private fun formatManagedBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
