package com.example.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ExtractionHistoryEntity
import com.example.data.media.BatchExtractionManager
import com.example.data.media.ResolvedMediaItem
import com.example.data.media.SocialExtractorManager
import com.example.data.media.SupportedPlatform
import com.example.data.media.UniversalExtractionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExtractionUiState {
    data object Idle : ExtractionUiState
    data object Loading : ExtractionUiState
    data class Loaded(val result: UniversalExtractionResult) : ExtractionUiState
    data class Error(val message: String) : ExtractionUiState
}

class UniversalDownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val historyDao = db.extractionHistoryDao()

    @VisibleForTesting
    internal var manager = SocialExtractorManager(historyDao)

    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _selectionNotice = MutableStateFlow<String?>(null)
    val selectionNotice: StateFlow<String?> = _selectionNotice.asStateFlow()

    private val _previewItem = MutableStateFlow<ResolvedMediaItem?>(null)
    val previewItem: StateFlow<ResolvedMediaItem?> = _previewItem.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val recentAnalyses: StateFlow<List<ExtractionHistoryEntity>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun analyze(rawInput: String, preferredPlatform: SupportedPlatform? = null) {
        val clean = rawInput.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _extractionState.value = ExtractionUiState.Loading
            _selectedIds.value = emptySet()
            _selectionNotice.value = null
            val res = manager.analyze(clean, preferredPlatform)
            res.onSuccess { extraction ->
                val defaultSelected = extraction.items.filter { it.isDownloadable }.map { it.id }.toSet()
                _selectedIds.value = defaultSelected
                _extractionState.value = ExtractionUiState.Loaded(extraction)
            }.onFailure { err ->
                _extractionState.value = ExtractionUiState.Error(
                    err.localizedMessage ?: "Could not analyze URL or profile."
                )
            }
        }
    }

    @VisibleForTesting
    internal fun setLoadedResultForTesting(
        result: UniversalExtractionResult,
        initialSelectedIds: Set<String> = result.items.filter { it.isDownloadable }.map { it.id }.toSet(),
    ) {
        _selectionNotice.value = null
        _extractionState.value = ExtractionUiState.Loaded(result)
        val validEligibleIds = result.items.filter { it.isDownloadable }.map { it.id }.toSet()
        _selectedIds.value = initialSelectedIds.intersect(validEligibleIds)
    }

    fun loadMorePage() {
        val current = (_extractionState.value as? ExtractionUiState.Loaded)?.result ?: return
        val cursor = current.nextPageCursor ?: return
        if (_isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val res = manager.analyze(
                rawInput = current.resolvedFinalUrl,
                preferredPlatform = current.platform,
                pageCursor = cursor,
            )
            res.onSuccess { nextPage ->
                val mergedItems = (current.items + nextPage.items).distinctBy { it.id }
                val mergedResult = current.copy(
                    items = mergedItems,
                    nextPageCursor = nextPage.nextPageCursor,
                    profile = current.profile?.copy(
                        retrievedCount = mergedItems.size,
                        nextPageCursor = nextPage.nextPageCursor,
                        isPartialSubset = nextPage.nextPageCursor != null,
                    ),
                )
                val validIds = mergedItems.filter { it.isDownloadable }.map { it.id }.toSet()
                _selectedIds.update { it.intersect(validIds) }
                _extractionState.value = ExtractionUiState.Loaded(mergedResult)
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleSelection(id: String) {
        val current = (_extractionState.value as? ExtractionUiState.Loaded)?.result ?: return
        val target = current.items.firstOrNull { it.id == id }
        if (target == null || !target.isDownloadable) {
            // Ineligible media must never enter or stay in selectedIds
            _selectedIds.update { it - id }
            return
        }
        _selectionNotice.value = null
        _selectedIds.update { set ->
            if (id in set) set - id else set + id
        }
    }

    fun selectAllEligible() {
        val current = (_extractionState.value as? ExtractionUiState.Loaded)?.result ?: return
        _selectionNotice.value = null
        _selectedIds.value = current.items.filter { it.isDownloadable }.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectionNotice.value = null
        _selectedIds.value = emptySet()
    }

    fun dismissSelectionNotice() {
        _selectionNotice.value = null
    }

    fun selectedDownloadableCount(): Int {
        val current = (_extractionState.value as? ExtractionUiState.Loaded)?.result ?: return 0
        val chosen = _selectedIds.value
        return current.items.count { it.isDownloadable && it.id in chosen }
    }

    fun openPreview(item: ResolvedMediaItem) {
        _previewItem.value = item
    }

    fun dismissPreview() {
        _previewItem.value = null
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    /**
     * Queues ONLY explicitly selected & verified downloadable items.
     * Empty selection NEVER falls back to downloading all items; instead sets a clear selection notice.
     */
    fun prepareSelectedBatch(): List<BatchExtractionManager.BatchQueueEntry> {
        val current = (_extractionState.value as? ExtractionUiState.Loaded)?.result ?: return emptyList()
        val chosen = _selectedIds.value
        val targets = current.items.filter { it.isDownloadable && it.id in chosen }
        if (targets.isEmpty()) {
            _selectionNotice.value = if (current.downloadableCount > 0) {
                "No items selected. Select verified downloadable item(s) first, or tap Download All."
            } else {
                "No verified downloadable items are available for this source."
            }
            return emptyList()
        }
        _selectionNotice.value = null
        return BatchExtractionManager.prepareEligibleBatch(targets)
    }

    /**
     * Explicit action to queue all verified downloadable items in the current result.
     */
    fun prepareAllEligibleBatch(): List<BatchExtractionManager.BatchQueueEntry> {
        val current = (_extractionState.value as? ExtractionUiState.Loaded)?.result ?: return emptyList()
        val targets = current.items.filter { it.isDownloadable }
        if (targets.isEmpty()) {
            _selectionNotice.value = "No verified downloadable items are available for this source."
            return emptyList()
        }
        _selectionNotice.value = null
        return BatchExtractionManager.prepareEligibleBatch(targets)
    }

    fun prepareSingleItem(item: ResolvedMediaItem): BatchExtractionManager.BatchQueueEntry? {
        return BatchExtractionManager.prepareEligibleBatch(listOf(item)).firstOrNull()
    }
}
