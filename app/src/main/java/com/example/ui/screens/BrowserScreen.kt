package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.ReaderTheme
import com.example.data.SearchEngine
import com.example.data.UserSettings
import com.example.data.browser.BrowserAdShield
import com.example.data.browser.DetectedPageMedia
import com.example.data.browser.SafetyWarningLevel
import com.example.data.browser.WebViewJsBridge
import com.example.data.browser.WebViewScripts
import com.example.data.local.BookmarkEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.SiteRuleEntity
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
import com.example.viewmodel.BrowserTab
import com.example.viewmodel.BrowserUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class PendingSslPrompt(
    val tabId: Long,
    val summary: String,
    val handler: SslErrorHandler,
)

@Composable
fun BrowserScreen(
    uiState: BrowserUiState,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    siteRules: List<SiteRuleEntity>,
    settings: UserSettings,
    onRegisterWebView: (Long, WebView) -> Unit,
    onGetWebView: (Long) -> WebView?,
    onNewTab: (Boolean, String) -> Unit,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onCloseOtherTabs: (Int) -> Unit,
    onCloseTabsToRight: (Int) -> Unit,
    onSetTabGroup: (Long, String) -> Unit,
    onRestoreLastClosed: () -> Unit,
    onNavigate: (String, SearchEngine) -> Unit,
    onGoHome: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddSpeedDialTile: (String, String, String, Long) -> Unit,
    onMoveSpeedDialTile: (String, Int) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onExportBookmarksJson: () -> String,
    onImportBookmarksJson: (String) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: (Int?) -> Unit,
    onUpdateSiteRule: (String, (SiteRuleEntity) -> SiteRuleEntity) -> Unit,
    onResetSiteRule: (String) -> Unit,
    onClearBrowsingData: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onWebViewStateChanged: (Long, String?, String?, Boolean, Boolean, Int, Boolean) -> Unit,
    onSslErrorReceived: (Long, String) -> Unit,
    onTrackerBlocked: (Long) -> Unit,
    onTabMediaStateChanged: (Long, Boolean, Boolean, String) -> Unit,
    onToggleTabPlayback: (Long) -> Unit,
    onSniffActiveTab: () -> Unit,
    onSniffedMediaReceived: (Long, List<DetectedPageMedia>) -> Unit,
    onOpenReaderMode: () -> Unit,
    onReaderTextExtracted: (String, String) -> Unit,
    onDismissReaderModal: () -> Unit,
    onChangeReaderFontSize: (Int) -> Unit,
    onChangeReaderLineHeight: (Float) -> Unit,
    onChangeReaderTheme: (ReaderTheme) -> Unit,
    onSaveReaderArticle: (String, String) -> Unit,
    onSummarizeArticleWithAi: (String, String) -> Unit,
    onSendToDownloader: (url: String, title: String?) -> Unit,
    onOpenUniversalDownloader: (initialUrl: String) -> Unit,
    onOpenDownloadsSection: () -> Unit,
    onAnalyzeWithAi: (url: String, title: String?) -> Unit,
    onOpenWallpaperSettings: () -> Unit,
    onToggleDesktopUa: (Boolean) -> Unit,
    onToggleAdBlocker: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isSectionActive: Boolean = true,
) {
    val activeTab = uiState.activeTab
    val accent = LocalPrismColors.current
    val focusManager = LocalFocusManager.current

    var omniText by remember(activeTab.id, activeTab.url, activeTab.query) {
        mutableStateOf(
            when {
                activeTab.url.isNotBlank() -> activeTab.displayAddress
                activeTab.query.isNotBlank() -> activeTab.query
                else -> ""
            }
        )
    }
    var omniFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showSnifferSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSecuritySheet by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAddTileDialog by remember { mutableStateOf(false) }
    var pendingSslPrompt by remember { mutableStateOf<PendingSslPrompt?>(null) }

    val activeHost = remember(activeTab.url) { BrowserAdShield.extractHost(activeTab.url) }
    val activeSiteRule = remember(siteRules, activeHost) {
        activeHost?.let { h -> siteRules.firstOrNull { it.host.equals(h, ignoreCase = true) } }
    }
    val safetyReport = remember(activeTab.url, activeTab.sslErrorWarning) {
        BrowserAdShield.inspectUrlSafety(activeTab.url, activeTab.sslErrorWarning)
    }
    val isBookmarked = remember(bookmarks, activeTab.url) {
        activeTab.url.isNotBlank() && bookmarks.any { it.url == activeTab.url }
    }

    BackHandler(enabled = isSectionActive && !activeTab.isHome) {
        val wv = onGetWebView(activeTab.id)
        if (wv != null && wv.canGoBack()) wv.goBack() else onGoHome()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        val isCompactWidth = maxWidth < 560.dp

        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 1100.dp).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Prism .chrome.glass header
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                borderColor = if (activeTab.isPrivate) accent.secondary else GlassEdgeLo,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            itemsIndexed(items = uiState.tabs, key = { _, tab -> tab.id }) { index, tab ->
                                PrismTabPill(
                                    tab = tab,
                                    selected = index == uiState.activeTabIndex,
                                    onSelect = { onSelectTab(index) },
                                    onToggleMedia = { onToggleTabPlayback(tab.id) },
                                    onClose = { onCloseTab(index) },
                                )
                            }
                            item(key = "add_tab") {
                                IconButton(
                                    onClick = { onNewTab(false, "General") },
                                    modifier = Modifier.size(38.dp).testTag("add_tab_button"),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add_tab), tint = TextPrimary, modifier = Modifier.size(19.dp))
                                }
                            }
                        }

                        val anyPlaying = uiState.tabs.any { it.isPlayingMedia }
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (anyPlaying) accent.orb3.copy(alpha = 0.22f) else GlassBase)
                                .border(1.dp, if (anyPlaying) accent.orb3 else GlassEdgeLo, CircleShape)
                                .clickable { showTabsSheet = true }
                                .testTag("tabs_menu_button")
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (anyPlaying) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = "Media playing", tint = accent.orb3, modifier = Modifier.size(14.dp))
                            }
                            Text("${uiState.tabs.size}", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Navigation & Omnibox bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        IconButton(
                            onClick = {
                                val wv = onGetWebView(activeTab.id)
                                if (wv != null && wv.canGoBack()) wv.goBack() else if (!activeTab.isHome) onGoHome()
                            },
                            enabled = !activeTab.isHome,
                            modifier = Modifier.size(36.dp).testTag("browser_back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = if (!activeTab.isHome) TextPrimary else TextMuted.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                        }

                        if (!isCompactWidth) {
                            IconButton(
                                onClick = { onGetWebView(activeTab.id)?.goForward() },
                                enabled = activeTab.canGoForward,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.cd_forward), tint = if (activeTab.canGoForward) TextPrimary else TextMuted.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                            }
                        }

                        IconButton(
                            onClick = { onReload() },
                            modifier = Modifier.size(36.dp).testTag("browser_reload_button"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_reload), tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }

                        // .omni pill
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(CircleShape)
                                .background(if (omniFocused) Color.Black.copy(alpha = 0.16f) else OmniInsetBg, CircleShape)
                                .border(
                                    width = if (omniFocused) 1.5.dp else 1.dp,
                                    color = if (omniFocused) accent.primary else GlassEdgeLo,
                                    shape = CircleShape,
                                )
                                .padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Interactive Security Indicator Pill
                            IconButton(
                                onClick = { showSecuritySheet = true },
                                modifier = Modifier.size(26.dp).testTag("browser_security_button"),
                            ) {
                                Icon(
                                    imageVector = when {
                                        activeTab.isPrivate -> Icons.Outlined.VisibilityOff
                                        safetyReport.level == SafetyWarningLevel.CERT_ERROR || safetyReport.level == SafetyWarningLevel.SUSPICIOUS_URL -> Icons.Rounded.Warning
                                        settings.adBlockerEnabled && activeTab.blockedTrackers > 0 -> Icons.Outlined.Shield
                                        else -> Icons.Rounded.Lock
                                    },
                                    contentDescription = "Site security and permissions",
                                    tint = when {
                                        activeTab.isPrivate -> accent.secondary
                                        safetyReport.level == SafetyWarningLevel.CERT_ERROR || safetyReport.level == SafetyWarningLevel.SUSPICIOUS_URL -> StatusError
                                        safetyReport.level == SafetyWarningLevel.HTTP_UNENCRYPTED && !activeTab.isHome -> StatusWarning
                                        activeTab.blockedTrackers > 0 -> StatusSuccess
                                        activeTab.url.startsWith("https://") -> accent.orb3
                                        else -> TextMuted
                                    },
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (omniText.isEmpty()) {
                                    Text(
                                        text = if (activeTab.isPrivate) "Private search (${settings.searchEngine.label})" else "Search ${settings.searchEngine.label} or URL",
                                        color = TextMuted,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = omniText,
                                    onValueChange = { omniText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Medium),
                                    cursorBrush = SolidColor(accent.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); onNavigate(omniText, settings.searchEngine) }),
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { omniFocused = it.isFocused }.testTag("browser_omni_input"),
                                )
                            }

                            // Dedicated Native Universal Downloader Button!
                            IconButton(
                                onClick = { onOpenUniversalDownloader(activeTab.url) },
                                modifier = Modifier.size(32.dp).testTag("browser_universal_download_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDownload,
                                    contentDescription = stringResource(R.string.cd_download_page),
                                    tint = accent.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            if (!activeTab.isHome) {
                                Box {
                                    IconButton(
                                        onClick = { onSniffActiveTab(); showSnifferSheet = true },
                                        modifier = Modifier.size(32.dp).testTag("browser_download_url_button"),
                                    ) {
                                        Icon(Icons.Outlined.Download, contentDescription = "Sniff page media", tint = accent.orb3, modifier = Modifier.size(17.dp))
                                    }
                                    if (activeTab.detectedMedia.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier.align(Alignment.TopEnd).size(13.dp).clip(CircleShape).background(accent.secondary),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(activeTab.detectedMedia.size.coerceAtMost(9).toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = onToggleBookmark,
                                modifier = Modifier.size(32.dp).testTag("browser_bookmark_button"),
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(R.string.cd_bookmark),
                                    tint = if (isBookmarked) accent.orb4 else TextMuted,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp).testTag("browser_menu_button"),
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_menu), tint = TextPrimary, modifier = Modifier.size(19.dp))
                            }

                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, containerColor = SurfaceElevated) {
                                DropdownMenuItem(
                                    text = { Text("Universal Media Downloader", color = accent.orb3, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = accent.orb3) },
                                    onClick = { menuExpanded = false; onOpenUniversalDownloader(activeTab.url) },
                                )
                                DropdownMenuItem(
                                    text = { Text("New Tab Home", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null, tint = TextPrimary) },
                                    onClick = { menuExpanded = false; onGoHome() },
                                )
                                DropdownMenuItem(
                                    text = { Text("New Private Glass Tab", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = accent.secondary) },
                                    onClick = { menuExpanded = false; onNewTab(true, "General") },
                                )
                                DropdownMenuItem(
                                    text = { Text("Tabs Manager & Audio Controls", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent.orb3) },
                                    onClick = { menuExpanded = false; showTabsSheet = true },
                                )
                                if (!activeTab.isHome) {
                                    DropdownMenuItem(
                                        text = { Text("Reader Mode", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Outlined.ChromeReaderMode, contentDescription = null, tint = accent.orb4) },
                                        onClick = { menuExpanded = false; onOpenReaderMode() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sniff Dynamic Media", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null, tint = accent.orb3) },
                                        onClick = { menuExpanded = false; onSniffActiveTab(); showSnifferSheet = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Site Security & Permissions", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = StatusSuccess) },
                                        onClick = { menuExpanded = false; showSecuritySheet = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Analyze Page with Gemini AI", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = accent.secondary) },
                                        onClick = { menuExpanded = false; onAnalyzeWithAi(activeTab.url, activeTab.title) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Bookmarks Manager", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.Bookmarks, contentDescription = null, tint = accent.orb4) },
                                    onClick = { menuExpanded = false; showBookmarksSheet = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Browsing History", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, tint = TextMuted) },
                                    onClick = { menuExpanded = false; showHistorySheet = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (settings.adBlockerEnabled) "Ad Shield: ON (${activeTab.blockedTrackers} blocked)" else "Ad Shield: OFF", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = StatusSuccess) },
                                    onClick = { menuExpanded = false; onToggleAdBlocker(!settings.adBlockerEnabled) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (settings.desktopUserAgent) "Desktop Mode: ON" else "Desktop Mode: OFF", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.Laptop, contentDescription = null, tint = TextMuted) },
                                    onClick = { menuExpanded = false; onToggleDesktopUa(!settings.desktopUserAgent) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Browser Wallpaper", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, tint = accent.primary) },
                                    onClick = { menuExpanded = false; onOpenWallpaperSettings() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Browsing Data", color = StatusError) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = StatusError) },
                                    onClick = { menuExpanded = false; showClearDataDialog = true },
                                )
                            }
                        }
                    }

                    // Bookmark strip (.marks)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(items = bookmarks, key = { it.url }) { mk ->
                            Row(
                                modifier = Modifier.clip(CircleShape).clickable { onNavigate(mk.url, settings.searchEngine) }.padding(start = 6.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(mk.badgeColorHex)), contentAlignment = Alignment.Center) {
                                    Text(mk.badgeLetter, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(mk.title, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 2. Main .view.glass container
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(28.dp),
                borderColor = if (activeTab.isPrivate) accent.secondary.copy(alpha = 0.6f) else GlassEdgeLo,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    uiState.tabs.forEach { tab ->
                        if (!tab.isHome) {
                            key(tab.id) {
                                val isTabVisible = isSectionActive && tab.id == activeTab.id
                                val tabHost = BrowserAdShield.extractHost(tab.url)
                                val tabRule = tabHost?.let { h -> siteRules.firstOrNull { it.host.equals(h, true) } }
                                PersistentTabWebView(
                                    tab = tab,
                                    isActive = isTabVisible,
                                    desktopUa = tabRule?.desktopMode == true || settings.desktopUserAgent,
                                    allowJavascript = tabRule?.allowJavascript ?: true,
                                    adBlockerEnabled = settings.adBlockerEnabled,
                                    blockThirdPartyTrackers = settings.blockThirdPartyTrackers,
                                    autofillEnabled = settings.autofillEnabled,
                                    siteRule = tabRule,
                                    onRegisterWebView = onRegisterWebView,
                                    onStateChanged = onWebViewStateChanged,
                                    onSslErrorPrompt = { tabId, summary, handler ->
                                        onSslErrorReceived(tabId, summary)
                                        pendingSslPrompt = PendingSslPrompt(tabId, summary, handler)
                                    },
                                    onTrackerBlocked = onTrackerBlocked,
                                    onMediaUpdate = onTabMediaStateChanged,
                                    onSniffedMedia = onSniffedMediaReceived,
                                    onReaderExtracted = { _, title, text -> onReaderTextExtracted(title, text) },
                                    onDownloadRequest = onSendToDownloader,
                                    modifier = Modifier.fillMaxSize().zIndex(if (isTabVisible) 1f else 0f),
                                )
                            }
                        }
                    }

                    if (activeTab.isHome) {
                        Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                            PrismHomeTabView(
                                bookmarks = bookmarks,
                                recentHistory = if (activeTab.isPrivate) emptyList() else history.take(6),
                                searchEngine = settings.searchEngine,
                                isPrivate = activeTab.isPrivate,
                                isCompact = isCompactWidth,
                                onSearchSubmit = { q -> focusManager.clearFocus(); onNavigate(q, settings.searchEngine) },
                                onSiteClick = { u -> onNavigate(u, settings.searchEngine) },
                                onAddTileClick = { showAddTileDialog = true },
                                onMoveTile = onMoveSpeedDialTile,
                                onRemoveTile = onRemoveBookmark,
                                onOpenUniversalDownloader = { onOpenUniversalDownloader("") },
                                onOpenDownloads = onOpenDownloadsSection,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTabsSheet) {
        TabsManagerSheet(
            tabs = uiState.tabs,
            activeIndex = uiState.activeTabIndex,
            recentlyClosedCount = uiState.recentlyClosedCount,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onCloseOtherTabs = onCloseOtherTabs,
            onCloseTabsToRight = onCloseTabsToRight,
            onSetTabGroup = onSetTabGroup,
            onRestoreLastClosed = onRestoreLastClosed,
            onToggleTabPlayback = onToggleTabPlayback,
            onNewTab = onNewTab,
            onDismiss = { showTabsSheet = false },
        )
    }

    if (showBookmarksSheet) {
        BookmarksManagerSheet(
            bookmarks = bookmarks,
            onNavigate = { u -> onNavigate(u, settings.searchEngine) },
            onRemoveBookmark = onRemoveBookmark,
            onExportJson = onExportBookmarksJson,
            onImportJson = onImportBookmarksJson,
            onDismiss = { showBookmarksSheet = false },
        )
    }

    if (showSnifferSheet) {
        MediaSnifferSheet(
            items = activeTab.detectedMedia,
            pageUrl = activeTab.url,
            onDownloadItem = { url, title -> onSendToDownloader(url, title) },
            onDismiss = { showSnifferSheet = false },
        )
    }

    if (showHistorySheet) {
        HistorySheet(
            history = history,
            onNavigate = { u -> onNavigate(u, settings.searchEngine) },
            onDeleteItem = onDeleteHistoryItem,
            onClearHistory = onClearHistory,
            onDismiss = { showHistorySheet = false },
        )
    }

    if (showSecuritySheet) {
        BrowserSecuritySheet(
            pageUrl = activeTab.url,
            sslErrorWarning = activeTab.sslErrorWarning,
            blockedTrackers = activeTab.blockedTrackers,
            siteRule = activeSiteRule,
            onUpdateSiteRule = onUpdateSiteRule,
            onResetSiteRule = onResetSiteRule,
            onOpenClearBrowsingData = { showClearDataDialog = true },
            onDismiss = { showSecuritySheet = false },
        )
    }

    if (showClearDataDialog) {
        ClearBrowsingDataDialog(
            onConfirmClear = onClearBrowsingData,
            onDismiss = { showClearDataDialog = false },
        )
    }

    if (showAddTileDialog) {
        AddSpeedDialDialog(
            onAdd = onAddSpeedDialTile,
            onDismiss = { showAddTileDialog = false },
        )
    }

    pendingSslPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { prompt.handler.cancel(); pendingSslPrompt = null },
            containerColor = SurfaceElevated,
            title = { Text("Certificate Security Warning", color = StatusError) },
            text = {
                Text(
                    text = "This site failed TLS certificate verification (${prompt.summary}). Attackers could intercept your connection.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                PrismGradientButton(
                    text = "Go Back Safely",
                    height = 36.dp,
                    onClick = { prompt.handler.cancel(); pendingSslPrompt = null },
                )
            },
            dismissButton = {
                TextButton(onClick = { prompt.handler.proceed(); pendingSslPrompt = null }) {
                    Text("Proceed Unsafe", color = StatusError)
                }
            },
        )
    }

    ReaderModeDialog(
        state = uiState.readerModal,
        settings = settings,
        onChangeFontSize = onChangeReaderFontSize,
        onChangeLineHeight = onChangeReaderLineHeight,
        onChangeTheme = onChangeReaderTheme,
        onSaveArticle = onSaveReaderArticle,
        onSummarizeWithAi = onSummarizeArticleWithAi,
        onDismiss = onDismissReaderModal,
    )
}

@Composable
private fun PrismTabPill(
    tab: BrowserTab,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleMedia: () -> Unit,
    onClose: () -> Unit,
) {
    val accent = LocalPrismColors.current
    Box(
        modifier = Modifier
            .widthIn(min = 112.dp, max = 210.dp)
            .height(38.dp)
            .clip(CircleShape)
            .background(if (selected) GlassHi else Color.Transparent, CircleShape)
            .border(1.dp, when { tab.isPrivate && selected -> accent.secondary; selected -> GlassEdgeLo; else -> Color.Transparent }, CircleShape)
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tab.isPrivate) {
                Icon(Icons.Outlined.VisibilityOff, contentDescription = "Private tab", tint = accent.secondary, modifier = Modifier.size(13.dp))
            } else {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(
                        Brush.sweepGradient(listOf(accent.primary, accent.secondary, accent.orb4, accent.orb3, accent.primary))
                    )
                )
            }
            Text(
                text = tab.title,
                color = if (selected) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (tab.hasMedia || tab.isPlayingMedia) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(accent.orb3.copy(alpha = 0.25f)).clickable(onClick = onToggleMedia).testTag("tab_pill_media_${tab.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (tab.isPlayingMedia) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Toggle tab audio", tint = accent.orb3, modifier = Modifier.size(14.dp))
                }
            }
            Box(modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_tab), tint = TextMuted, modifier = Modifier.size(13.dp))
            }
        }
        if (selected) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).height(2.dp)
                    .background(Brush.horizontalGradient(if (tab.isPrivate) listOf(accent.secondary, accent.primary) else listOf(accent.primary, accent.secondary)))
            )
        }
    }
}

@Composable
private fun PrismHomeTabView(
    bookmarks: List<BookmarkEntity>,
    recentHistory: List<HistoryEntity>,
    searchEngine: SearchEngine,
    isPrivate: Boolean,
    isCompact: Boolean,
    onSearchSubmit: (String) -> Unit,
    onSiteClick: (String) -> Unit,
    onAddTileClick: () -> Unit,
    onMoveTile: (String, Int) -> Unit,
    onRemoveTile: (String) -> Unit,
    onOpenUniversalDownloader: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val accent = LocalPrismColors.current
    var searchText by remember { mutableStateOf("") }
    var clockMillis by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        while (true) {
            clockMillis++
            delay(15_000L)
        }
    }

    val (formattedTime, greeting) = remember(clockMillis, isPrivate) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val timeStr = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
        val hello = if (isPrivate) {
            "Private glass session — history & cookies isolated"
        } else {
            when {
                hour < 5 -> "Still up?"
                hour < 12 -> "Good morning"
                hour < 18 -> "Good afternoon"
                else -> "Good evening"
            } + " — what are you looking for?"
        }
        timeStr to hello
    }

    val dialFolders = remember(bookmarks) { listOf("All") + bookmarks.filter { it.isSpeedDial }.map { it.folderName }.distinct() }
    val dialItems = remember(bookmarks, selectedFolder) {
        bookmarks.filter { it.isSpeedDial && (selectedFolder == "All" || it.folderName == selectedFolder) }.take(12)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayLarge.copy(
                        brush = Brush.verticalGradient(listOf(TextPrimary, TextMuted)),
                        fontSize = if (isCompact) 64.sp else 88.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = greeting, color = if (isPrivate) accent.secondary else TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
            }

            // .search.glass pill
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp).liquidGlass(shape = CircleShape).padding(start = 20.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchText.isEmpty()) Text("Search ${searchEngine.label} or enter URL...", color = TextMuted, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.5.sp, fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(accent.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(searchText) }),
                        modifier = Modifier.fillMaxWidth().testTag("home_search_input"),
                    )
                }
                PrismGradientButton(
                    text = stringResource(R.string.browser_search_cta),
                    onClick = { onSearchSubmit(searchText) },
                    height = 40.dp,
                    modifier = Modifier.testTag("home_search_button"),
                )
            }

            // Quick Actions row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(
                        modifier = Modifier.clip(CircleShape).background(accent.primary.copy(alpha = 0.20f)).border(1.dp, accent.primary, CircleShape).clickable(onClick = onOpenUniversalDownloader).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(16.dp))
                        Text("Universal Extractor", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.clip(CircleShape).background(GlassBase).border(1.dp, GlassEdgeLo, CircleShape).clickable(onClick = onOpenDownloads).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Text("Downloads & Files", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Speed Dial Folders
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(dialFolders) { folder ->
                    val active = folder == selectedFolder
                    Box(
                        modifier = Modifier.clip(CircleShape).background(if (active) GlassHi else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable { selectedFolder = folder }.padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(folder, color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp)
                    }
                }
            }

            val cols = if (isCompact) 2 else 4
            val rows = dialItems.chunked(cols)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowSites ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowSites.forEach { site ->
                            SpeedDialTile(
                                title = site.title,
                                letter = site.badgeLetter,
                                color = Color(site.badgeColorHex),
                                onClick = { onSiteClick(site.url) },
                                onMoveLeft = { onMoveTile(site.url, -1) },
                                onMoveRight = { onMoveTile(site.url, 1) },
                                onUnpin = { onRemoveTile(site.url) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(cols - rowSites.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(
                    modifier = Modifier.clip(CircleShape).background(GlassBase).border(1.dp, GlassEdgeLo, CircleShape).clickable(onClick = onAddTileClick).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(16.dp))
                    Text("Pin Shortcut (long-press to reorder or unpin)", color = TextMuted, fontSize = 11.5.sp)
                }
            }

            if (recentHistory.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recently Visited", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentHistory, key = { it.id }) { h ->
                            Box(
                                modifier = Modifier.clip(CircleShape).background(OmniInsetBg).border(1.dp, GlassEdgeLo, CircleShape).clickable { onSiteClick(h.url) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(h.title.take(22), color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
    title: String,
    letter: String,
    color: Color,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tileMenuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(24.dp))
                .combinedClickable(onClick = onClick, onLongClick = { tileMenuOpen = true })
                .testTag("dial_${title.lowercase()}")
                .padding(vertical = 15.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = letter, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = title, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = tileMenuOpen, onDismissRequest = { tileMenuOpen = false }, containerColor = SurfaceElevated) {
            DropdownMenuItem(text = { Text("Move Earlier", color = TextPrimary) }, onClick = { tileMenuOpen = false; onMoveLeft() })
            DropdownMenuItem(text = { Text("Move Later", color = TextPrimary) }, onClick = { tileMenuOpen = false; onMoveRight() })
            DropdownMenuItem(text = { Text("Unpin Shortcut", color = StatusError) }, onClick = { tileMenuOpen = false; onUnpin() })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun PersistentTabWebView(
    tab: BrowserTab,
    isActive: Boolean,
    desktopUa: Boolean,
    allowJavascript: Boolean,
    adBlockerEnabled: Boolean,
    blockThirdPartyTrackers: Boolean,
    autofillEnabled: Boolean,
    siteRule: SiteRuleEntity?,
    onRegisterWebView: (Long, WebView) -> Unit,
    onStateChanged: (Long, String?, String?, Boolean, Boolean, Int, Boolean) -> Unit,
    onSslErrorPrompt: (Long, String, SslErrorHandler) -> Unit,
    onTrackerBlocked: (Long) -> Unit,
    onMediaUpdate: (Long, Boolean, Boolean, String) -> Unit,
    onSniffedMedia: (Long, List<DetectedPageMedia>) -> Unit,
    onReaderExtracted: (Long, String, String) -> Unit,
    onDownloadRequest: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    val animatedProgress by animateFloatAsState(targetValue = tab.loadProgress / 100f, label = "wv_progress")

    Column(modifier = modifier) {
        if (isActive) {
            AnimatedVisibility(visible = tab.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(2.5.dp).background(Color.White.copy(alpha = 0.08f))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0.05f, 1f))
                            .height(2.5.dp)
                            .background(Brush.horizontalGradient(listOf(accent.primary, accent.secondary))),
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        importantForAutofill = if (autofillEnabled) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    }
                    settings.javaScriptEnabled = allowJavascript
                    settings.domStorageEnabled = !tab.isPrivate
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    if (tab.isPrivate) {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    }
                    addJavascriptInterface(
                        WebViewJsBridge(
                            tabId = tab.id,
                            onMediaUpdate = onMediaUpdate,
                            onSniffedMedia = onSniffedMedia,
                            onReaderExtracted = onReaderExtracted,
                        ),
                        "AasmoBridge",
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString()
                            if (adBlockerEnabled && BrowserAdShield.shouldBlock(
                                    requestUrl = reqUrl,
                                    pageUrl = tab.url,
                                    blockAds = adBlockerEnabled,
                                    blockThirdPartyTrackers = blockThirdPartyTrackers,
                                )
                            ) {
                                onTrackerBlocked(tab.id)
                                return BrowserAdShield.emptyResponse()
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            if (handler != null) {
                                val summary = "SSL primary error code ${error?.primaryError ?: -1} on ${error?.url ?: tab.url}"
                                onSslErrorPrompt(tab.id, summary, handler)
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            onStateChanged(tab.id, url, view?.title, view?.canGoBack() ?: false, view?.canGoForward() ?: false, 15, false)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            onStateChanged(tab.id, url, view?.title, view?.canGoBack() ?: false, view?.canGoForward() ?: false, 100, true)
                            view?.evaluateJavascript(WebViewScripts.installMediaObserverJs, null)
                            view?.evaluateJavascript(WebViewScripts.sniffMediaJs, null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onStateChanged(tab.id, view?.url, view?.title, view?.canGoBack() ?: false, view?.canGoForward() ?: false, newProgress, false)
                        }

                        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                            val allowed = siteRule?.allowLocation == true
                            callback?.invoke(origin, allowed, false)
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            val resources = request?.resources ?: return
                            val grantable = mutableListOf<String>()
                            resources.forEach { res ->
                                when (res) {
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> if (siteRule?.allowCamera == true) grantable.add(res)
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> if (siteRule?.allowMicrophone == true) grantable.add(res)
                                }
                            }
                            if (grantable.isNotEmpty()) request.grant(grantable.toTypedArray()) else request.deny()
                        }
                    }

                    setDownloadListener { dlUrl, _, contentDisposition, _, _ ->
                        val name = contentDisposition?.substringAfter("filename=", "")?.trim('"', ' ')?.takeIf { it.isNotBlank() }
                        onDownloadRequest(dlUrl, name)
                    }

                    onRegisterWebView(tab.id, this)
                    if (tab.url.isNotBlank()) loadUrl(tab.url)
                }
            },
            update = { wv ->
                wv.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
                if (wv.settings.javaScriptEnabled != allowJavascript) wv.settings.javaScriptEnabled = allowJavascript
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    wv.importantForAutofill = if (autofillEnabled) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }
                if (desktopUa) {
                    val desktop = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    if (wv.settings.userAgentString != desktop) wv.settings.userAgentString = desktop
                }
                if (tab.url.isNotBlank() && wv.url.isNullOrBlank()) wv.loadUrl(tab.url)
                onRegisterWebView(tab.id, wv)
            },
        )
    }
}
