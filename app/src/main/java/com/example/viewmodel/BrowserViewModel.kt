package com.example.viewmodel

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SearchEngine
import com.example.data.SettingsRepository
import com.example.data.browser.BrowserAdShield
import com.example.data.browser.DetectedPageMedia
import com.example.data.browser.WebViewScripts
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkEntity
import com.example.data.local.FilterRuleEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.SiteRuleEntity
import com.example.service.MediaControllerBus
import com.example.service.MediaPlaybackService
import com.example.service.MediaRemoteCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class BrowserTab(
    val id: Long,
    val title: String = "New tab",
    val url: String = "",
    val query: String = "",
    val groupName: String = "General",
    val isPrivate: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val reloadNonce: Int = 0,
    val hasMedia: Boolean = false,
    val isPlayingMedia: Boolean = false,
    val mediaTitle: String = "",
    val blockedTrackers: Int = 0,
    val sslErrorWarning: String? = null,
    val detectedMedia: List<DetectedPageMedia> = emptyList(),
) {
    val isHome: Boolean get() = url.isBlank() && query.isBlank()
    val displayAddress: String get() = url.removePrefix("https://").removePrefix("http://")
}

data class ReaderModalState(
    val visible: Boolean = false,
    val title: String = "",
    val text: String = "",
)

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = 1L)),
    val activeTabIndex: Int = 0,
    val recentlyClosedCount: Int = 0,
    val readerModal: ReaderModalState = ReaderModalState(),
) {
    val activeTab: BrowserTab get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val bookmarkDao = db.bookmarkDao()
    private val historyDao = db.historyDao()
    private val siteRuleDao = db.siteRuleDao()
    private val filterRuleDao = db.filterRuleDao()
    private val settingsRepo = SettingsRepository(application)

    private var tabIdCounter = 1L
    private val webViews = ConcurrentHashMap<Long, WebView>()
    private var activeNotificationTabId: Long = -1L
    private val closedTabStack = ArrayDeque<BrowserTab>()

    private val defaultBookmarks = listOf(
        BookmarkEntity(title = "YouTube", url = "https://youtube.com", badgeLetter = "Y", badgeColorHex = 0xFFFF4D4D, isSpeedDial = true, folderName = "Media", sortOrder = 0),
        BookmarkEntity(title = "Reddit", url = "https://reddit.com", badgeLetter = "R", badgeColorHex = 0xFFFF6A2B, isSpeedDial = true, folderName = "Social", sortOrder = 1),
        BookmarkEntity(title = "GitHub", url = "https://github.com", badgeLetter = "H", badgeColorHex = 0xFF6B5BFF, isSpeedDial = true, folderName = "Dev", sortOrder = 2),
        BookmarkEntity(title = "Wikipedia", url = "https://wikipedia.org", badgeLetter = "W", badgeColorHex = 0xFF27A8B8, isSpeedDial = true, folderName = "General", sortOrder = 3),
        BookmarkEntity(title = "Instagram", url = "https://instagram.com", badgeLetter = "I", badgeColorHex = 0xFFFF6FA8, isSpeedDial = true, folderName = "Social", sortOrder = 4),
        BookmarkEntity(title = "X", url = "https://x.com", badgeLetter = "X", badgeColorHex = 0xFF27D3C3, isSpeedDial = true, folderName = "Social", sortOrder = 5),
        BookmarkEntity(title = "Pinterest", url = "https://pinterest.com", badgeLetter = "P", badgeColorHex = 0xFFE60023, isSpeedDial = true, folderName = "Media", sortOrder = 6),
        BookmarkEntity(title = "AI Studio", url = "https://aistudio.google.com", badgeLetter = "A", badgeColorHex = 0xFF4B7BFF, isSpeedDial = true, folderName = "Dev", sortOrder = 7),
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val siteRules: StateFlow<List<SiteRuleEntity>> = siteRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filterRules: StateFlow<List<FilterRuleEntity>> = filterRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (bookmarkDao.count() == 0) {
                bookmarkDao.insertAll(defaultBookmarks)
            }
        }
        viewModelScope.launch {
            siteRuleDao.observeAll().collect { BrowserAdShield.updateSiteRules(it) }
        }
        viewModelScope.launch {
            filterRuleDao.observeAll().collect { BrowserAdShield.updateCustomRules(it) }
        }
        viewModelScope.launch {
            MediaControllerBus.commands.collect { cmd ->
                when (cmd) {
                    is MediaRemoteCommand.TogglePlayPause -> toggleTabPlayback(cmd.tabId)
                    is MediaRemoteCommand.SeekBy -> seekTabMedia(cmd.tabId, cmd.seconds)
                    is MediaRemoteCommand.Stop -> pauseTabPlayback(cmd.tabId)
                }
            }
        }
    }

    fun registerWebView(tabId: Long, webView: WebView) {
        webViews[tabId] = webView
    }

    fun getWebView(tabId: Long): WebView? = webViews[tabId]

    fun newTab(isPrivate: Boolean = false, groupName: String = "General") {
        val nextId = ++tabIdCounter
        val title = if (isPrivate) "Private tab" else "New tab"
        _uiState.update { state ->
            val updated = state.tabs + BrowserTab(id = nextId, title = title, isPrivate = isPrivate, groupName = groupName)
            state.copy(tabs = updated, activeTabIndex = updated.lastIndex)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            if (index in state.tabs.indices) state.copy(activeTabIndex = index) else state
        }
    }

    fun setTabGroup(tabId: Long, groupName: String) {
        _uiState.update { state ->
            val updated = state.tabs.map { if (it.id == tabId) it.copy(groupName = groupName) else it }
            state.copy(tabs = updated)
        }
    }

    fun closeTab(index: Int) {
        val closingTab = _uiState.value.tabs.getOrNull(index)
        if (closingTab != null) {
            if (!closingTab.isPrivate && !closingTab.isHome) {
                closedTabStack.addLast(closingTab.copy(isPlayingMedia = false, hasMedia = false))
                if (closedTabStack.size > 12) closedTabStack.removeFirst()
            }
            destroyTabWebView(closingTab.id)
        }
        _uiState.update { state ->
            if (state.tabs.size <= 1) {
                val freshId = ++tabIdCounter
                state.copy(
                    tabs = listOf(BrowserTab(id = freshId)),
                    activeTabIndex = 0,
                    recentlyClosedCount = closedTabStack.size,
                )
            } else {
                val updated = state.tabs.toMutableList().apply { removeAt(index) }
                val newActive = when {
                    state.activeTabIndex > index -> state.activeTabIndex - 1
                    state.activeTabIndex >= updated.size -> updated.lastIndex
                    else -> state.activeTabIndex
                }
                state.copy(tabs = updated, activeTabIndex = newActive, recentlyClosedCount = closedTabStack.size)
            }
        }
    }

    fun closeOtherTabs(keepIndex: Int) {
        val tabs = _uiState.value.tabs
        val keep = tabs.getOrNull(keepIndex) ?: return
        tabs.forEachIndexed { idx, tab ->
            if (idx != keepIndex) {
                if (!tab.isPrivate && !tab.isHome) closedTabStack.addLast(tab)
                destroyTabWebView(tab.id)
            }
        }
        _uiState.update {
            it.copy(tabs = listOf(keep), activeTabIndex = 0, recentlyClosedCount = closedTabStack.size)
        }
    }

    fun closeTabsToRight(fromIndex: Int) {
        val tabs = _uiState.value.tabs
        if (fromIndex !in tabs.indices || fromIndex >= tabs.lastIndex) return
        for (i in (fromIndex + 1)..tabs.lastIndex) {
            val tab = tabs[i]
            if (!tab.isPrivate && !tab.isHome) closedTabStack.addLast(tab)
            destroyTabWebView(tab.id)
        }
        val kept = tabs.take(fromIndex + 1)
        _uiState.update {
            it.copy(
                tabs = kept,
                activeTabIndex = it.activeTabIndex.coerceAtMost(kept.lastIndex),
                recentlyClosedCount = closedTabStack.size,
            )
        }
    }

    fun restoreLastClosedTab() {
        val restored = closedTabStack.removeLastOrNull() ?: return
        val freshId = ++tabIdCounter
        val tab = restored.copy(id = freshId, reloadNonce = 0)
        _uiState.update { state ->
            val updated = state.tabs + tab
            state.copy(tabs = updated, activeTabIndex = updated.lastIndex, recentlyClosedCount = closedTabStack.size)
        }
    }

    private fun destroyTabWebView(tabId: Long) {
        webViews.remove(tabId)?.let { wv ->
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.destroy()
        }
        if (activeNotificationTabId == tabId) {
            activeNotificationTabId = -1L
            MediaPlaybackService.stopNotification(getApplication())
        }
    }

    fun navigateToInput(raw: String, searchEngine: SearchEngine = SearchEngine.GOOGLE) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return

        val looksLikeUrl = Regex("^(https?://)?([\\w-]+\\.)+[a-z]{2,}(/.*)?$", RegexOption.IGNORE_CASE)
            .matches(trimmed) || trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)

        val targetUrl = if (looksLikeUrl) {
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed
            } else {
                "https://$trimmed"
            }
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            searchEngine.searchUrlTemplate.replace("%s", encoded)
        }

        val initialTitle = if (looksLikeUrl) {
            targetUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
        } else {
            trimmed
        }

        val tabId = _uiState.value.activeTab.id
        updateActiveTab {
            it.copy(
                url = targetUrl,
                query = if (looksLikeUrl) "" else trimmed,
                title = initialTitle,
                isLoading = true,
                loadProgress = 12,
                blockedTrackers = 0,
                sslErrorWarning = null,
                detectedMedia = emptyList(),
            )
        }
        webViews[tabId]?.loadUrl(targetUrl)
    }

    fun goHomeInActiveTab() {
        val tab = _uiState.value.activeTab
        webViews[tab.id]?.stopLoading()
        if (activeNotificationTabId == tab.id) {
            activeNotificationTabId = -1L
            MediaPlaybackService.stopNotification(getApplication())
        }
        updateActiveTab {
            it.copy(
                title = if (it.isPrivate) "Private tab" else "New tab",
                url = "",
                query = "",
                canGoBack = false,
                canGoForward = false,
                isLoading = false,
                loadProgress = 0,
                hasMedia = false,
                isPlayingMedia = false,
                mediaTitle = "",
                sslErrorWarning = null,
                detectedMedia = emptyList(),
            )
        }
    }

    fun reloadActiveTab() {
        val tab = _uiState.value.activeTab
        if (!tab.isHome) {
            webViews[tab.id]?.reload()
            updateActiveTab { it.copy(reloadNonce = it.reloadNonce + 1, isLoading = true, sslErrorWarning = null) }
        }
    }

    fun onWebViewStateChanged(
        tabId: Long,
        url: String?,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        progress: Int,
        recordHistory: Boolean = false,
    ) {
        var resolvedTitle = ""
        var resolvedUrl = ""
        var isPrivateTab = false

        _uiState.update { state ->
            val updated = state.tabs.map { tab ->
                if (tab.id != tabId || tab.isHome) {
                    tab
                } else {
                    val nextTitle = title?.takeIf { it.isNotBlank() } ?: tab.title
                    val nextUrl = url ?: tab.url
                    resolvedTitle = nextTitle
                    resolvedUrl = nextUrl
                    isPrivateTab = tab.isPrivate
                    tab.copy(
                        url = nextUrl,
                        title = nextTitle,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isLoading = progress in 1..99,
                        loadProgress = progress,
                    )
                }
            }
            state.copy(tabs = updated)
        }

        if (recordHistory && !isPrivateTab && resolvedUrl.startsWith("http")) {
            val domain = BrowserAdShield.extractHost(resolvedUrl).orEmpty()
            viewModelScope.launch {
                historyDao.insert(
                    HistoryEntity(
                        title = resolvedTitle.ifBlank { resolvedUrl },
                        url = resolvedUrl,
                        domain = domain,
                    )
                )
            }
        }
    }

    fun onSslErrorReceived(tabId: Long, errorSummary: String) {
        _uiState.update { state ->
            val updated = state.tabs.map { if (it.id == tabId) it.copy(sslErrorWarning = errorSummary) else it }
            state.copy(tabs = updated)
        }
    }

    fun onTrackerBlocked(tabId: Long) {
        _uiState.update { state ->
            val updated = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(blockedTrackers = tab.blockedTrackers + 1) else tab
            }
            state.copy(tabs = updated)
        }
    }

    fun onTabMediaStateChanged(tabId: Long, hasMedia: Boolean, isPlaying: Boolean, mediaTitle: String) {
        var tabHost = "Browser Tab"
        _uiState.update { state ->
            val updated = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tabHost = tab.url.removePrefix("https://").removePrefix("http://").substringBefore("/")
                        .ifBlank { tab.title }
                    tab.copy(
                        hasMedia = hasMedia,
                        isPlayingMedia = isPlaying,
                        mediaTitle = mediaTitle.ifBlank { tab.title },
                    )
                } else tab
            }
            state.copy(tabs = updated)
        }

        if (isPlaying || (hasMedia && activeNotificationTabId == tabId)) {
            activeNotificationTabId = tabId
            MediaPlaybackService.updateNotification(
                context = getApplication(),
                tabId = tabId,
                title = mediaTitle.ifBlank { "Browser Audio" },
                subtitle = "$tabHost • Background Playback",
                isPlaying = isPlaying,
            )
        } else if (!hasMedia && activeNotificationTabId == tabId) {
            activeNotificationTabId = -1L
            MediaPlaybackService.stopNotification(getApplication())
        }
    }

    fun toggleTabPlayback(tabId: Long) {
        val wv = webViews[tabId] ?: return
        wv.post { wv.evaluateJavascript(WebViewScripts.togglePlaybackJs(), null) }
    }

    fun pauseTabPlayback(tabId: Long) {
        val wv = webViews[tabId] ?: return
        wv.post { wv.evaluateJavascript(WebViewScripts.setPlaybackJs(false), null) }
    }

    fun seekTabMedia(tabId: Long, seconds: Int) {
        val wv = webViews[tabId] ?: return
        wv.post { wv.evaluateJavascript(WebViewScripts.seekByJs(seconds), null) }
    }

    fun triggerSniffActiveTab() {
        val tab = _uiState.value.activeTab
        val wv = webViews[tab.id] ?: return
        wv.post { wv.evaluateJavascript(WebViewScripts.sniffMediaJs, null) }
    }

    fun onSniffedMediaReceived(tabId: Long, items: List<DetectedPageMedia>) {
        _uiState.update { state ->
            val updated = state.tabs.map { tab -> if (tab.id == tabId) tab.copy(detectedMedia = items) else tab }
            state.copy(tabs = updated)
        }
    }

    fun openReaderModeForActiveTab() {
        val tab = _uiState.value.activeTab
        val wv = webViews[tab.id] ?: return
        wv.post { wv.evaluateJavascript(WebViewScripts.extractReaderJs, null) }
    }

    fun onReaderTextExtracted(title: String, text: String) {
        _uiState.update {
            it.copy(
                readerModal = ReaderModalState(
                    visible = true,
                    title = title,
                    text = text.ifBlank { "No readable article content found on this page." },
                )
            )
        }
    }

    fun dismissReaderModal() {
        _uiState.update { it.copy(readerModal = ReaderModalState(visible = false)) }
    }

    fun toggleBookmarkForActiveTab(folderName: String = "General") {
        val tab = _uiState.value.activeTab
        if (tab.url.isBlank()) return
        val existing = bookmarks.value.firstOrNull { it.url == tab.url }
        viewModelScope.launch {
            if (existing != null) {
                bookmarkDao.deleteByUrl(tab.url)
            } else {
                val letter = tab.title.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                bookmarkDao.insert(
                    BookmarkEntity(
                        title = tab.title.take(18),
                        url = tab.url,
                        badgeLetter = letter,
                        badgeColorHex = 0xFF5B6CFF,
                        isSpeedDial = true,
                        folderName = folderName,
                        sortOrder = bookmarks.value.size,
                    )
                )
            }
        }
    }

    fun addCustomSpeedDialTile(
        title: String,
        rawUrl: String,
        folderName: String = "General",
        colorHex: Long = 0xFF27D3C3,
    ) {
        val cleanUrl = if (rawUrl.startsWith("http")) rawUrl.trim() else "https://${rawUrl.trim()}"
        val cleanTitle = title.trim().ifBlank { cleanUrl.removePrefix("https://").substringBefore("/") }
        val letter = cleanTitle.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
        viewModelScope.launch {
            bookmarkDao.insert(
                BookmarkEntity(
                    title = cleanTitle.take(18),
                    url = cleanUrl,
                    badgeLetter = letter,
                    badgeColorHex = colorHex,
                    isSpeedDial = true,
                    folderName = folderName,
                    sortOrder = bookmarks.value.size,
                )
            )
        }
    }

    fun moveSpeedDialTile(url: String, delta: Int) {
        val list = bookmarks.value.toMutableList()
        val idx = list.indexOfFirst { it.url == url }
        val target = idx + delta
        if (idx == -1 || target !in list.indices) return
        val current = list[idx]
        val swap = list[target]
        viewModelScope.launch {
            bookmarkDao.update(current.copy(sortOrder = target))
            bookmarkDao.update(swap.copy(sortOrder = idx))
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { bookmarkDao.deleteByUrl(url) }
    }

    fun exportBookmarksJson(): String {
        val arr = JSONArray()
        bookmarks.value.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("title", b.title)
                    put("url", b.url)
                    put("folder", b.folderName)
                    put("color", b.badgeColorHex)
                }
            )
        }
        return arr.toString(2)
    }

    fun importBookmarksJson(rawJson: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var count = 0
            runCatching {
                val arr = JSONArray(rawJson)
                val toInsert = mutableListOf<BookmarkEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url")
                    val title = obj.optString("title", url)
                    if (url.startsWith("http")) {
                        toInsert.add(
                            BookmarkEntity(
                                title = title.take(18),
                                url = url,
                                badgeLetter = title.firstOrNull()?.uppercaseChar()?.toString() ?: "B",
                                badgeColorHex = obj.optLong("color", 0xFF5B6CFF),
                                isSpeedDial = true,
                                folderName = obj.optString("folder", "Imported"),
                                sortOrder = bookmarks.value.size + i,
                            )
                        )
                        count++
                    }
                }
                if (toInsert.isNotEmpty()) bookmarkDao.insertAll(toInsert)
            }
            onDone(count)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }

    fun clearHistory(hoursBack: Int? = null) {
        viewModelScope.launch {
            if (hoursBack == null) {
                historyDao.clearAll()
            } else {
                val since = System.currentTimeMillis() - hoursBack * 3_600_000L
                historyDao.deleteSince(since)
            }
        }
    }

    fun updateSiteRule(host: String, transform: (SiteRuleEntity) -> SiteRuleEntity) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val current = siteRuleDao.getByHost(host) ?: SiteRuleEntity(host = host)
            siteRuleDao.upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun resetSiteRule(host: String) {
        viewModelScope.launch { siteRuleDao.deleteHost(host) }
    }

    fun addFilterRule(pattern: String, isAllowRule: Boolean) {
        val clean = pattern.trim().lowercase()
        if (clean.isBlank()) return
        viewModelScope.launch {
            filterRuleDao.insert(FilterRuleEntity(pattern = clean, isAllowRule = isAllowRule))
        }
    }

    fun deleteFilterRule(id: Long) {
        viewModelScope.launch { filterRuleDao.deleteById(id) }
    }

    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearSiteData: Boolean,
    ) {
        viewModelScope.launch {
            if (clearHistory) historyDao.clearAll()
            if (clearSiteData) {
                siteRuleDao.clearAll()
                runCatching { WebStorage.getInstance().deleteAllData() }
            }
            if (clearCookies) {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            }
            if (clearCache) {
                webViews.values.forEach { wv ->
                    wv.post { wv.clearCache(true) }
                }
            }
        }
    }

    private fun updateActiveTab(transform: (BrowserTab) -> BrowserTab) {
        _uiState.update { state ->
            val idx = state.activeTabIndex
            if (idx !in state.tabs.indices) return@update state
            val updated = state.tabs.toMutableList()
            updated[idx] = transform(updated[idx])
            state.copy(tabs = updated)
        }
    }

    override fun onCleared() {
        webViews.values.forEach { wv -> runCatching { wv.destroy() } }
        webViews.clear()
        MediaPlaybackService.stopNotification(getApplication())
        super.onCleared()
    }
}
