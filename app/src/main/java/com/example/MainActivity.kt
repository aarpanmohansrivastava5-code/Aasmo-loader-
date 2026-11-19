package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.configureAppCheck
import com.example.data.local.DownloadSource
import com.example.data.local.DownloadStatus
import com.example.ui.components.AppSection
import com.example.ui.components.PrismAtmosphere
import com.example.ui.components.PrismBottomDock
import com.example.ui.components.liquidGlass
import com.example.ui.screens.AiAndSettingsScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DownloaderScreen
import com.example.ui.screens.HubsScreen
import com.example.ui.screens.UniversalDownloaderSheet
import com.example.ui.theme.AasmoLoaderTheme
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.AiSettingsViewModel
import com.example.viewmodel.BrowserViewModel
import com.example.viewmodel.DownloaderViewModel
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.HubsViewModel
import com.example.viewmodel.UniversalDownloaderViewModel
import kotlinx.coroutines.launch

private data class PendingConfirmDownload(
    val url: String,
    val title: String?,
    val source: DownloadSource,
    val chunks: Int,
)

class MainActivity : ComponentActivity() {
    private val sharedTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        extractSharedText(intent)?.let { sharedTextState.value = it }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )

        setContent {
            AasmoLoaderApp(
                sharedInput = sharedTextState.value,
                onConsumeSharedInput = { sharedTextState.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)?.let { sharedTextState.value = it }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val urlMatch = Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(raw)
        return urlMatch?.value ?: raw
    }
}

@Composable
fun AasmoLoaderApp(
    sharedInput: String? = null,
    onConsumeSharedInput: () -> Unit = {},
) {
    val aiSettingsVm: AiSettingsViewModel = viewModel()
    val browserVm: BrowserViewModel = viewModel()
    val downloaderVm: DownloaderViewModel = viewModel()
    val hubsVm: HubsViewModel = viewModel()
    val universalVm: UniversalDownloaderViewModel = viewModel()
    val fileManagerVm: FileManagerViewModel = viewModel()

    val settings by aiSettingsVm.settings.collectAsStateWithLifecycle()
    val browserState by browserVm.uiState.collectAsStateWithLifecycle()
    val bookmarks by browserVm.bookmarks.collectAsStateWithLifecycle()
    val history by browserVm.history.collectAsStateWithLifecycle()
    val siteRules by browserVm.siteRules.collectAsStateWithLifecycle()
    val filterRules by browserVm.filterRules.collectAsStateWithLifecycle()

    val filteredDownloads by downloaderVm.filteredDownloads.collectAsStateWithLifecycle()
    val allDownloads by downloaderVm.allDownloads.collectAsStateWithLifecycle()
    val downloadFilter by downloaderVm.filter.collectAsStateWithLifecycle()
    val inspectorState by downloaderVm.inspectorState.collectAsStateWithLifecycle()

    val managedFiles by fileManagerVm.files.collectAsStateWithLifecycle()
    val managedFolders by fileManagerVm.folders.collectAsStateWithLifecycle()
    val currentFolderPath by fileManagerVm.currentFolder.collectAsStateWithLifecycle()
    val fileCategory by fileManagerVm.category.collectAsStateWithLifecycle()
    val fileSortMode by fileManagerVm.sortMode.collectAsStateWithLifecycle()
    val fileSearchQuery by fileManagerVm.searchQuery.collectAsStateWithLifecycle()
    val fileDetailReport by fileManagerVm.detailReport.collectAsStateWithLifecycle()

    val gitHubState by hubsVm.gitHubState.collectAsStateWithLifecycle()
    val rateLimitState by hubsVm.rateLimitState.collectAsStateWithLifecycle()
    val repoDetailState by hubsVm.repoDetailState.collectAsStateWithLifecycle()
    val socialState by hubsVm.socialState.collectAsStateWithLifecycle()

    val aiMessages by aiSettingsVm.messages.collectAsStateWithLifecycle()
    val aiStreaming by aiSettingsVm.isStreaming.collectAsStateWithLifecycle()
    val aiError by aiSettingsVm.aiError.collectAsStateWithLifecycle()
    val insightState by aiSettingsVm.insightState.collectAsStateWithLifecycle()
    val linkExtractorState by aiSettingsVm.linkExtractorState.collectAsStateWithLifecycle()

    val extractionState by universalVm.extractionState.collectAsStateWithLifecycle()
    val selectedUniversalIds by universalVm.selectedIds.collectAsStateWithLifecycle()
    val universalSelectionNotice by universalVm.selectionNotice.collectAsStateWithLifecycle()
    val previewUniversalItem by universalVm.previewItem.collectAsStateWithLifecycle()
    val recentAnalyses by universalVm.recentAnalyses.collectAsStateWithLifecycle()
    val isLoadingMoreUniversal by universalVm.isLoadingMore.collectAsStateWithLifecycle()

    var currentSection by rememberSaveable { mutableStateOf(AppSection.BROWSER) }
    var hubsInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var aiInitialSubTab by rememberSaveable { mutableIntStateOf(0) }

    var showUniversalSheet by remember { mutableStateOf(false) }
    var universalInitialInput by remember { mutableStateOf("") }
    var pendingConfirmDownload by remember { mutableStateOf<PendingConfirmDownload?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sharedInput) {
        if (!sharedInput.isNullOrBlank()) {
            universalInitialInput = sharedInput
            showUniversalSheet = true
            universalVm.analyze(sharedInput)
            onConsumeSharedInput()
        }
    }

    val completedDownloadCount = remember(allDownloads) {
        allDownloads.count { it.status == DownloadStatus.COMPLETED.name }
    }
    LaunchedEffect(allDownloads.size, completedDownloadCount) {
        fileManagerVm.refresh()
    }

    val activeDownloadCount = remember(allDownloads) {
        allDownloads.count {
            it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name
        }
    }

    fun requestDownload(url: String, title: String?, source: DownloadSource, chunks: Int) {
        if (settings.askBeforeDownload) {
            pendingConfirmDownload = PendingConfirmDownload(url, title, source, chunks)
        } else {
            downloaderVm.startDownload(
                url = url,
                title = title,
                source = source,
                chunkOverride = chunks,
                onNotice = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            )
            scope.launch {
                snackbarHostState.showSnackbar("Queued in Aasmo Loader (${chunks}x packets)")
            }
        }
    }

    BackHandler(enabled = currentSection != AppSection.BROWSER) {
        currentSection = AppSection.BROWSER
    }

    AasmoLoaderTheme(
        accentPreset = settings.accentPreset,
        glassAlphaMultiplier = settings.glassIntensity,
    ) {
        PrismAtmosphere(settings = settings) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 840.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = if (isWideScreen) 16.dp else 80.dp),
                        ) { data ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SurfaceElevated.copy(alpha = 0.95f))
                                    .border(1.dp, LocalPrismColors.current.primary, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = data.visuals.message,
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (!isWideScreen) {
                            PrismBottomDock(
                                currentSection = currentSection,
                                activeDownloadCount = activeDownloadCount,
                                onSelectSection = { section ->
                                    if (section == AppSection.AI_MORE) aiInitialSubTab = 0
                                    currentSection = section
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding(),
                    ) {
                        if (isWideScreen) {
                            PrismSideRail(
                                currentSection = currentSection,
                                activeDownloadCount = activeDownloadCount,
                                onSelectSection = { section ->
                                    if (section == AppSection.AI_MORE) aiInitialSubTab = 0
                                    currentSection = section
                                },
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            val isBrowserActive = currentSection == AppSection.BROWSER

                            // Keep BrowserScreen mounted so persistent tab WebViews stay attached and keep playing audio in background across sections
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (isBrowserActive) 2f else 0f)
                                    .graphicsLayer {
                                        alpha = if (isBrowserActive) 1f else 0f
                                    },
                            ) {
                                BrowserScreen(
                                    uiState = browserState,
                                    bookmarks = bookmarks,
                                    history = history,
                                    siteRules = siteRules,
                                    settings = settings,
                                    onRegisterWebView = browserVm::registerWebView,
                                    onGetWebView = browserVm::getWebView,
                                    onNewTab = browserVm::newTab,
                                    onSelectTab = browserVm::selectTab,
                                    onCloseTab = browserVm::closeTab,
                                    onCloseOtherTabs = browserVm::closeOtherTabs,
                                    onCloseTabsToRight = browserVm::closeTabsToRight,
                                    onSetTabGroup = browserVm::setTabGroup,
                                    onRestoreLastClosed = browserVm::restoreLastClosedTab,
                                    onNavigate = browserVm::navigateToInput,
                                    onGoHome = browserVm::goHomeInActiveTab,
                                    onReload = browserVm::reloadActiveTab,
                                    onToggleBookmark = { browserVm.toggleBookmarkForActiveTab() },
                                    onAddSpeedDialTile = browserVm::addCustomSpeedDialTile,
                                    onMoveSpeedDialTile = browserVm::moveSpeedDialTile,
                                    onRemoveBookmark = browserVm::removeBookmark,
                                    onExportBookmarksJson = browserVm::exportBookmarksJson,
                                    onImportBookmarksJson = { json ->
                                        browserVm.importBookmarksJson(json) { importedCount ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Imported $importedCount bookmarks")
                                            }
                                        }
                                    },
                                    onDeleteHistoryItem = browserVm::deleteHistoryItem,
                                    onClearHistory = browserVm::clearHistory,
                                    onUpdateSiteRule = browserVm::updateSiteRule,
                                    onResetSiteRule = browserVm::resetSiteRule,
                                    onClearBrowsingData = { clearH, clearC, clearCa, clearS ->
                                        browserVm.clearBrowsingData(clearH, clearC, clearCa, clearS)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Selected browsing data cleared")
                                        }
                                    },
                                    onWebViewStateChanged = browserVm::onWebViewStateChanged,
                                    onSslErrorReceived = browserVm::onSslErrorReceived,
                                    onTrackerBlocked = browserVm::onTrackerBlocked,
                                    onTabMediaStateChanged = browserVm::onTabMediaStateChanged,
                                    onToggleTabPlayback = browserVm::toggleTabPlayback,
                                    onSniffActiveTab = browserVm::triggerSniffActiveTab,
                                    onSniffedMediaReceived = browserVm::onSniffedMediaReceived,
                                    onOpenReaderMode = browserVm::openReaderModeForActiveTab,
                                    onReaderTextExtracted = browserVm::onReaderTextExtracted,
                                    onDismissReaderModal = browserVm::dismissReaderModal,
                                    onChangeReaderFontSize = aiSettingsVm::setReaderFontSize,
                                    onChangeReaderLineHeight = aiSettingsVm::setReaderLineHeight,
                                    onChangeReaderTheme = aiSettingsVm::setReaderTheme,
                                    onSaveReaderArticle = { title, body ->
                                        fileManagerVm.saveReaderArticle(title, body) { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    },
                                    onSummarizeArticleWithAi = { title, text ->
                                        browserVm.dismissReaderModal()
                                        aiInitialSubTab = 0
                                        currentSection = AppSection.AI_MORE
                                        aiSettingsVm.summarizeArticle(title, text)
                                    },
                                    onSendToDownloader = { url, title ->
                                        requestDownload(url, title, DownloadSource.BROWSER, settings.defaultChunkCount)
                                    },
                                    onOpenUniversalDownloader = { initialUrl ->
                                        universalInitialInput = initialUrl
                                        showUniversalSheet = true
                                    },
                                    onOpenDownloadsSection = { currentSection = AppSection.LOADER },
                                    onAnalyzeWithAi = { url, title ->
                                        aiInitialSubTab = 0
                                        currentSection = AppSection.AI_MORE
                                        aiSettingsVm.inspectTarget(url, title.orEmpty())
                                    },
                                    onOpenWallpaperSettings = {
                                        aiInitialSubTab = 1
                                        currentSection = AppSection.AI_MORE
                                    },
                                    onToggleDesktopUa = aiSettingsVm::setDesktopUserAgent,
                                    onToggleAdBlocker = aiSettingsVm::setAdBlockerEnabled,
                                    isSectionActive = isBrowserActive,
                                )
                            }

                            if (!isBrowserActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(1f),
                                ) {
                                    Crossfade(
                                        targetState = currentSection,
                                        label = "section_crossfade",
                                    ) { section ->
                                        when (section) {
                                            AppSection.BROWSER -> Unit

                                            AppSection.LOADER -> DownloaderScreen(
                                                downloads = filteredDownloads,
                                                allDownloadsCount = allDownloads.size,
                                                filter = downloadFilter,
                                                defaultChunks = settings.defaultChunkCount,
                                                wifiOnlyDownloads = settings.wifiOnlyDownloads,
                                                inspectorState = inspectorState,
                                                managedFiles = managedFiles,
                                                managedFolders = managedFolders,
                                                currentFolderPath = currentFolderPath,
                                                fileCategory = fileCategory,
                                                fileSortMode = fileSortMode,
                                                fileSearchQuery = fileSearchQuery,
                                                fileDetailReport = fileDetailReport,
                                                onFilterChange = downloaderVm::setFilter,
                                                onStartDownload = { url, chunks ->
                                                    requestDownload(url, null, DownloadSource.DIRECT, chunks)
                                                },
                                                onPause = downloaderVm::pauseDownload,
                                                onCancel = downloaderVm::cancelDownload,
                                                onResume = downloaderVm::resumeDownload,
                                                onDelete = downloaderVm::deleteDownload,
                                                onClearCompleted = downloaderVm::clearCompleted,
                                                onExportToDownloads = { entity ->
                                                    downloaderVm.exportToPublicDownloads(entity) { message ->
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(message)
                                                        }
                                                    }
                                                },
                                                onBuildOpenIntent = downloaderVm::buildOpenIntent,
                                                onBuildShareIntent = downloaderVm::buildShareIntent,
                                                onOpenInspector = downloaderVm::openFileInspector,
                                                onDismissInspector = downloaderVm::dismissFileInspector,
                                                onAnalyzeItem = { item ->
                                                    aiInitialSubTab = 0
                                                    currentSection = AppSection.AI_MORE
                                                    aiSettingsVm.inspectTarget(item.url, "File: ${item.fileName}, Source: ${item.source}")
                                                },
                                                onOpenUniversalExtractor = {
                                                    universalInitialInput = browserState.activeTab.url
                                                    showUniversalSheet = true
                                                },
                                                onOpenGitHubHub = {
                                                    hubsInitialTab = 0
                                                    currentSection = AppSection.HUBS
                                                },
                                                onOpenSocialHub = {
                                                    hubsInitialTab = 1
                                                    currentSection = AppSection.HUBS
                                                },
                                                onOpenBrowser = { currentSection = AppSection.BROWSER },
                                                onRefreshFiles = fileManagerVm::refresh,
                                                onSelectFileCategory = fileManagerVm::selectCategory,
                                                onSelectFileSort = fileManagerVm::selectSort,
                                                onFileSearchChange = fileManagerVm::setSearchQuery,
                                                onOpenFolder = fileManagerVm::openFolder,
                                                onGoUpFolder = fileManagerVm::goUpFolder,
                                                onCreateFolder = { name ->
                                                    fileManagerVm.createFolder(name) { msg ->
                                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                                    }
                                                },
                                                onRenameFile = { path, newName ->
                                                    fileManagerVm.renameFile(path, newName) { msg ->
                                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                                    }
                                                },
                                                onCopyOrMoveFile = { path, target, move ->
                                                    fileManagerVm.copyOrMove(path, target, move) { msg ->
                                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                                    }
                                                },
                                                onDeleteFilePath = { path ->
                                                    fileManagerVm.deleteFile(path) { msg ->
                                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                                    }
                                                },
                                                onInspectManagedFile = fileManagerVm::inspectFile,
                                                onDismissManagedFileDetails = fileManagerVm::dismissDetails,
                                                onBuildManagedOpenIntent = fileManagerVm::buildOpenIntent,
                                                onBuildManagedShareIntent = fileManagerVm::buildShareIntent,
                                            )

                                            AppSection.HUBS -> HubsScreen(
                                                initialTab = hubsInitialTab,
                                                gitHubState = gitHubState,
                                                rateLimitState = rateLimitState,
                                                repoDetailState = repoDetailState,
                                                socialState = socialState,
                                                onSearchGitHub = hubsVm::fetchGitHub,
                                                onOpenRepoDetail = hubsVm::openRepoInspector,
                                                onDismissRepoDetail = hubsVm::dismissRepoSheet,
                                                onFetchSocial = hubsVm::fetchSocialMedia,
                                                onDownloadUrl = { url, title, source ->
                                                    requestDownload(url, title, source, settings.defaultChunkCount)
                                                },
                                                onOpenInBrowser = { url ->
                                                    browserVm.navigateToInput(url, settings.searchEngine)
                                                    currentSection = AppSection.BROWSER
                                                },
                                                onAnalyzeRepoWithAi = { repo ->
                                                    aiInitialSubTab = 0
                                                    currentSection = AppSection.AI_MORE
                                                    aiSettingsVm.inspectTarget(
                                                        repo.htmlUrl,
                                                        "GitHub Repo ${repo.fullName} (${repo.language ?: "Code"}, ${repo.stars} stars): ${repo.description.orEmpty()}",
                                                    )
                                                },
                                            )

                                            AppSection.AI_MORE -> AiAndSettingsScreen(
                                                initialSubTab = aiInitialSubTab,
                                                messages = aiMessages,
                                                isStreaming = aiStreaming,
                                                aiError = aiError,
                                                insightState = insightState,
                                                linkExtractorState = linkExtractorState,
                                                activeBrowserUrl = browserState.activeTab.url,
                                                settings = settings,
                                                filterRules = filterRules,
                                                onSendPrompt = aiSettingsVm::sendPrompt,
                                                onAnalyzeImage = aiSettingsVm::analyzeImage,
                                                onExtractLinks = aiSettingsVm::extractLinksFromText,
                                                onClearLinkExtractor = aiSettingsVm::clearLinkExtractor,
                                                onQueueExtractedLinks = { items ->
                                                    items.forEach { link ->
                                                        downloaderVm.startDownload(
                                                            url = link.url,
                                                            title = link.label,
                                                            source = DownloadSource.DIRECT,
                                                        )
                                                    }
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Queued ${items.size} extracted links")
                                                    }
                                                },
                                                onInspectActiveUrl = { url ->
                                                    aiSettingsVm.inspectTarget(url, browserState.activeTab.title)
                                                },
                                                onClearInsight = aiSettingsVm::clearInsight,
                                                onClearChat = aiSettingsVm::clearChat,
                                                onSearchEngineChange = aiSettingsVm::setSearchEngine,
                                                onHomepageSave = aiSettingsVm::setHomepageUrl,
                                                onDesktopUaChange = aiSettingsVm::setDesktopUserAgent,
                                                onDefaultChunksChange = aiSettingsVm::setDefaultChunkCount,
                                                onMaxConcurrentChange = aiSettingsVm::setMaxConcurrentDownloads,
                                                onWifiOnlyChange = aiSettingsVm::setWifiOnlyDownloads,
                                                onAskBeforeDownloadChange = aiSettingsVm::setAskBeforeDownload,
                                                onDuplicateBehaviorChange = aiSettingsVm::setDuplicateBehavior,
                                                onAutoRetryChange = aiSettingsVm::setAutoRetryDownloads,
                                                onDownloadFolderSelected = { label, uri ->
                                                    aiSettingsVm.setDownloadFolder(label, uri)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Download folder set to $label")
                                                    }
                                                },
                                                onNotificationsChange = aiSettingsVm::setNotificationsEnabled,
                                                onAdBlockerChange = aiSettingsVm::setAdBlockerEnabled,
                                                onThirdPartyTrackerChange = aiSettingsVm::setBlockThirdPartyTrackers,
                                                onAddFilterRule = browserVm::addFilterRule,
                                                onDeleteFilterRule = browserVm::deleteFilterRule,
                                                onClearBrowsingData = { clearH, clearC, clearCa, clearS ->
                                                    browserVm.clearBrowsingData(clearH, clearC, clearCa, clearS)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Browsing data cleared")
                                                    }
                                                },
                                                onUnsafeProtectionChange = aiSettingsVm::setUnsafeSiteProtection,
                                                onCredentialManagerChange = aiSettingsVm::setCredentialManagerEnabled,
                                                onAutofillChange = aiSettingsVm::setAutofillEnabled,
                                                onAccentSelected = aiSettingsVm::setAccentPreset,
                                                onWallpaperSelected = aiSettingsVm::setWallpaperMode,
                                                onGlassIntensityChange = aiSettingsVm::setGlassIntensity,
                                                onGithubTokenSave = { token ->
                                                    aiSettingsVm.setGithubToken(token)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("GitHub token saved")
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showUniversalSheet) {
                UniversalDownloaderSheet(
                    initialInput = universalInitialInput,
                    extractionState = extractionState,
                    selectedIds = selectedUniversalIds,
                    selectionNotice = universalSelectionNotice,
                    previewItem = previewUniversalItem,
                    recentAnalyses = recentAnalyses,
                    isLoadingMore = isLoadingMoreUniversal,
                    onAnalyze = universalVm::analyze,
                    onLoadMore = universalVm::loadMorePage,
                    onToggleSelect = universalVm::toggleSelection,
                    onSelectAllEligible = universalVm::selectAllEligible,
                    onClearSelection = universalVm::clearSelection,
                    onDismissSelectionNotice = universalVm::dismissSelectionNotice,
                    onDownloadSingle = { item ->
                        val entry = universalVm.prepareSingleItem(item)
                        if (entry != null) {
                            downloaderVm.queueBatch(listOf(entry)) { notice ->
                                scope.launch { snackbarHostState.showSnackbar(notice) }
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("Queued ${entry.title}")
                            }
                            showUniversalSheet = false
                            currentSection = AppSection.LOADER
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("This item is not verified downloadable.")
                            }
                        }
                    },
                    onDownloadSelected = {
                        val batch = universalVm.prepareSelectedBatch()
                        if (batch.isNotEmpty()) {
                            downloaderVm.queueBatch(batch) { notice ->
                                scope.launch { snackbarHostState.showSnackbar(notice) }
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("Queued ${batch.size} selected item(s) into Loader")
                            }
                            showUniversalSheet = false
                            currentSection = AppSection.LOADER
                        }
                    },
                    onDownloadAllEligible = {
                        val batch = universalVm.prepareAllEligibleBatch()
                        if (batch.isNotEmpty()) {
                            downloaderVm.queueBatch(batch) { notice ->
                                scope.launch { snackbarHostState.showSnackbar(notice) }
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("Queued all ${batch.size} verified item(s) into Loader")
                            }
                            showUniversalSheet = false
                            currentSection = AppSection.LOADER
                        }
                    },
                    onOpenPreview = universalVm::openPreview,
                    onDismissPreview = universalVm::dismissPreview,
                    onOpenInBrowser = { url ->
                        browserVm.navigateToInput(url, settings.searchEngine)
                        showUniversalSheet = false
                        currentSection = AppSection.BROWSER
                    },
                    onClearHistory = universalVm::clearHistory,
                    onDismiss = { showUniversalSheet = false },
                )
            }

            pendingConfirmDownload?.let { req ->
                AlertDialog(
                    onDismissRequest = { pendingConfirmDownload = null },
                    containerColor = SurfaceElevated,
                    titleContentColor = TextPrimary,
                    textContentColor = TextMuted,
                    title = { Text("Confirm Download", fontWeight = FontWeight.SemiBold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                req.title?.takeIf { it.isNotBlank() } ?: req.url.substringAfterLast('/').ifBlank { "Download target" },
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                            Text(req.url, fontSize = 11.5.sp, color = TextMuted)
                            Text("Chunks: ${req.chunks}x • Duplicate rule: ${settings.duplicateBehavior.label}", fontSize = 11.5.sp, color = LocalPrismColors.current.primary)
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val target = req
                                pendingConfirmDownload = null
                                downloaderVm.startDownload(
                                    url = target.url,
                                    title = target.title,
                                    source = target.source,
                                    chunkOverride = target.chunks,
                                    onNotice = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                )
                                scope.launch { snackbarHostState.showSnackbar("Download queued") }
                            },
                        ) {
                            Text("Queue Download", color = StatusSuccess)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingConfirmDownload = null }) {
                            Text("Cancel", color = TextMuted)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PrismSideRail(
    currentSection: AppSection,
    activeDownloadCount: Int,
    onSelectSection: (AppSection) -> Unit,
) {
    val accent = LocalPrismColors.current
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 12.dp, top = 8.dp, bottom = 12.dp)
            .width(88.dp)
            .fillMaxHeight()
            .liquidGlass(shape = RoundedCornerShape(28.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppSection.entries.forEach { section ->
            val selected = section == currentSection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            accent.primary.copy(alpha = 0.30f),
                                            accent.secondary.copy(alpha = 0.22f),
                                        )
                                    )
                                )
                                .border(1.dp, accent.primary, RoundedCornerShape(20.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectSection(section) }
                    .testTag("rail_${section.id}")
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(
                        imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                        contentDescription = section.label,
                        tint = if (selected) TextPrimary else TextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                    if (section == AppSection.LOADER && activeDownloadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.orb3),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = section.label,
                    color = if (selected) TextPrimary else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
