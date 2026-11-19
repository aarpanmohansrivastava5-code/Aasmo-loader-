package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DuplicateBehavior
import com.example.data.ReaderTheme
import com.example.data.SearchEngine
import com.example.data.SettingsRepository
import com.example.data.UserSettings
import com.example.data.WallpaperMode
import com.example.data.ai.ExtractedLinkItem
import com.example.data.ai.GeminiMediaRepository
import com.example.data.ai.MediaInsight
import com.example.ui.theme.AccentPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
)

sealed interface InsightUiState {
    data object Idle : InsightUiState
    data object Loading : InsightUiState
    data class Success(val insight: MediaInsight) : InsightUiState
    data class Error(val message: String) : InsightUiState
}

sealed interface LinkExtractorUiState {
    data object Idle : LinkExtractorUiState
    data object Loading : LinkExtractorUiState
    data class Loaded(val links: List<ExtractedLinkItem>) : LinkExtractorUiState
    data class Error(val message: String) : LinkExtractorUiState
}

class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val aiRepo = GeminiMediaRepository()
    private var msgIdCounter = 0L

    val settings: StateFlow<UserSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _messages = MutableStateFlow(
        listOf(
            AiChatMessage(
                id = ++msgIdCounter,
                isUser = false,
                text = "Aasmo Intelligence is ready. Ask about any URL, GitHub repo, upload a screenshot, or extract links from release notes.",
            )
        )
    )
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _insightState = MutableStateFlow<InsightUiState>(InsightUiState.Idle)
    val insightState: StateFlow<InsightUiState> = _insightState.asStateFlow()

    private val _linkExtractorState = MutableStateFlow<LinkExtractorUiState>(LinkExtractorUiState.Idle)
    val linkExtractorState: StateFlow<LinkExtractorUiState> = _linkExtractorState.asStateFlow()

    fun sendPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isStreaming.value) return

        _aiError.value = null
        val historyPairs = _messages.value.filter { !it.isStreaming }.takeLast(10).map { it.isUser to it.text }
        val userMsg = AiChatMessage(id = ++msgIdCounter, isUser = true, text = clean)
        val botId = ++msgIdCounter
        _messages.update { it + userMsg + AiChatMessage(id = botId, isUser = false, text = "", isStreaming = true) }
        _isStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepo.streamPrompt(clean, historyPairs).collect { accumulated ->
                    _messages.update { list -> list.map { m -> if (m.id == botId) m.copy(text = accumulated, isStreaming = true) else m } }
                }
                _messages.update { list -> list.map { m -> if (m.id == botId) m.copy(isStreaming = false) else m } }
            } catch (e: TimeoutCancellationException) {
                _aiError.value = "Gemini request timed out after 60 seconds. Please retry."
                _messages.update { list -> list.filterNot { it.id == botId && it.text.isBlank() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiError.value = e.localizedMessage ?: "Gemini AI error occurred."
                _messages.update { list -> list.filterNot { it.id == botId && it.text.isBlank() } }
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun analyzeImage(bitmap: Bitmap, prompt: String) {
        if (_isStreaming.value) return
        _aiError.value = null
        val userMsg = AiChatMessage(
            id = ++msgIdCounter,
            isUser = true,
            text = "[Image attached] ${prompt.ifBlank { "Analyze this screenshot / image" }}",
        )
        val botId = ++msgIdCounter
        _messages.update { it + userMsg + AiChatMessage(id = botId, isUser = false, text = "", isStreaming = true) }
        _isStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepo.analyzeImage(bitmap, prompt).collect { accumulated ->
                    _messages.update { list -> list.map { m -> if (m.id == botId) m.copy(text = accumulated, isStreaming = true) else m } }
                }
                _messages.update { list -> list.map { m -> if (m.id == botId) m.copy(isStreaming = false) else m } }
            } catch (e: TimeoutCancellationException) {
                _aiError.value = "Image analysis timed out."
                _messages.update { list -> list.filterNot { it.id == botId && it.text.isBlank() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiError.value = e.localizedMessage ?: "Multimodal analysis failed."
                _messages.update { list -> list.filterNot { it.id == botId && it.text.isBlank() } }
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun summarizeArticle(title: String, articleText: String) {
        if (_isStreaming.value) return
        _aiError.value = null
        val userMsg = AiChatMessage(id = ++msgIdCounter, isUser = true, text = "TL;DR Summary: $title")
        val botId = ++msgIdCounter
        _messages.update { it + userMsg + AiChatMessage(id = botId, isUser = false, text = "", isStreaming = true) }
        _isStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepo.summarizeArticle(title, articleText).collect { accumulated ->
                    _messages.update { list -> list.map { m -> if (m.id == botId) m.copy(text = accumulated, isStreaming = true) else m } }
                }
                _messages.update { list -> list.map { m -> if (m.id == botId) m.copy(isStreaming = false) else m } }
            } catch (e: TimeoutCancellationException) {
                _aiError.value = "Summary timed out."
                _messages.update { list -> list.filterNot { it.id == botId && it.text.isBlank() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiError.value = e.localizedMessage ?: "Summary failed."
                _messages.update { list -> list.filterNot { it.id == botId && it.text.isBlank() } }
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun extractLinksFromText(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _linkExtractorState.value = LinkExtractorUiState.Loading
            try {
                val links = aiRepo.extractLinksFromText(rawText)
                _linkExtractorState.value = LinkExtractorUiState.Loaded(links)
            } catch (e: TimeoutCancellationException) {
                _linkExtractorState.value = LinkExtractorUiState.Error("Link extraction timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _linkExtractorState.value = LinkExtractorUiState.Error(e.localizedMessage ?: "Link extraction failed.")
            }
        }
    }

    fun clearLinkExtractor() { _linkExtractorState.value = LinkExtractorUiState.Idle }

    fun inspectTarget(target: String, contextInfo: String = "") {
        if (target.isBlank()) return
        viewModelScope.launch {
            _insightState.value = InsightUiState.Loading
            try {
                val insight = aiRepo.analyzeTarget(target, contextInfo)
                _insightState.value = InsightUiState.Success(insight)
            } catch (e: TimeoutCancellationException) {
                _insightState.value = InsightUiState.Error("Analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _insightState.value = InsightUiState.Error(e.localizedMessage ?: "AI analysis failed.")
            }
        }
    }

    fun clearInsight() { _insightState.value = InsightUiState.Idle }

    fun clearChat() {
        _aiError.value = null
        _messages.value = listOf(
            AiChatMessage(
                id = ++msgIdCounter,
                isUser = false,
                text = "Conversation cleared. Ready for your next URL, screenshot, or media question.",
            )
        )
    }

    fun setAccentPreset(preset: AccentPreset) { viewModelScope.launch { settingsRepo.setAccentPreset(preset) } }
    fun setWallpaperMode(mode: WallpaperMode, customUri: Uri? = null) { viewModelScope.launch { settingsRepo.setWallpaperMode(mode, customUri?.toString()) } }
    fun setGlassIntensity(value: Float) { viewModelScope.launch { settingsRepo.setGlassIntensity(value) } }
    fun setHomepageUrl(url: String) { viewModelScope.launch { settingsRepo.setHomepageUrl(url) } }
    fun setSearchEngine(engine: SearchEngine) { viewModelScope.launch { settingsRepo.setSearchEngine(engine) } }
    fun setDesktopUserAgent(enabled: Boolean) { viewModelScope.launch { settingsRepo.setDesktopUserAgent(enabled) } }
    fun setReaderFontSize(sp: Int) { viewModelScope.launch { settingsRepo.setReaderFontSize(sp) } }
    fun setReaderLineHeight(mult: Float) { viewModelScope.launch { settingsRepo.setReaderLineHeight(mult) } }
    fun setReaderTheme(theme: ReaderTheme) { viewModelScope.launch { settingsRepo.setReaderTheme(theme) } }
    fun setDefaultChunkCount(chunks: Int) { viewModelScope.launch { settingsRepo.setDefaultChunkCount(chunks) } }
    fun setMaxConcurrentDownloads(max: Int) { viewModelScope.launch { settingsRepo.setMaxConcurrentDownloads(max) } }
    fun setWifiOnlyDownloads(enabled: Boolean) { viewModelScope.launch { settingsRepo.setWifiOnlyDownloads(enabled) } }
    fun setAskBeforeDownload(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAskBeforeDownload(enabled) } }
    fun setDuplicateBehavior(b: DuplicateBehavior) { viewModelScope.launch { settingsRepo.setDuplicateBehavior(b) } }
    fun setAutoRetryDownloads(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutoRetryDownloads(enabled) } }
    fun setDownloadFolder(label: String, safUri: String?) { viewModelScope.launch { settingsRepo.setDownloadFolder(label, safUri) } }
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setNotificationsEnabled(enabled) } }
    fun setAdBlockerEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAdBlockerEnabled(enabled) } }
    fun setBlockThirdPartyTrackers(enabled: Boolean) { viewModelScope.launch { settingsRepo.setBlockThirdPartyTrackers(enabled) } }
    fun setUnsafeSiteProtection(enabled: Boolean) { viewModelScope.launch { settingsRepo.setUnsafeSiteProtection(enabled) } }
    fun setCredentialManagerEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setCredentialManagerEnabled(enabled) } }
    fun setAutofillEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutofillEnabled(enabled) } }
    fun setGithubToken(token: String) { viewModelScope.launch { settingsRepo.setGithubToken(token) } }
}
