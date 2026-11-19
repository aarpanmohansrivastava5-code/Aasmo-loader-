package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.data.download.ArchiveAndMediaInspector
import com.example.data.download.PacketDownloadEngine
import com.example.data.download.ZipEntrySummary
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadSource
import com.example.data.local.DownloadStatus
import com.example.data.media.BatchExtractionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    QUEUED("Queued"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
}

data class FileInspectorState(
    val entity: DownloadEntity,
    val zipEntries: List<ZipEntrySummary> = emptyList(),
    val textPreview: String? = null,
    val computedSha256: String = "",
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val downloadDao = db.downloadDao()
    private val settingsRepo = SettingsRepository(application)
    private val engine = PacketDownloadEngine(application, downloadDao)

    private val _filter = MutableStateFlow(DownloadFilter.ALL)
    val filter: StateFlow<DownloadFilter> = _filter.asStateFlow()

    private val _inspectorState = MutableStateFlow<FileInspectorState?>(null)
    val inspectorState: StateFlow<FileInspectorState?> = _inspectorState.asStateFlow()

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredDownloads: StateFlow<List<DownloadEntity>> = combine(allDownloads, _filter) { list, f ->
        when (f) {
            DownloadFilter.ALL -> list
            DownloadFilter.ACTIVE -> list.filter { it.status == DownloadStatus.DOWNLOADING.name }
            DownloadFilter.QUEUED -> list.filter { it.status == DownloadStatus.QUEUED.name }
            DownloadFilter.PAUSED -> list.filter { it.status == DownloadStatus.PAUSED.name }
            DownloadFilter.COMPLETED -> list.filter { it.status == DownloadStatus.COMPLETED.name }
            DownloadFilter.FAILED -> list.filter { it.status == DownloadStatus.FAILED.name }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: DownloadFilter) {
        _filter.value = filter
    }

    fun startDownload(
        url: String,
        title: String? = null,
        source: DownloadSource = DownloadSource.DIRECT,
        chunkOverride: Int? = null,
        batchId: String = "",
        onNotice: ((String) -> Unit)? = null,
    ) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            engine.enqueueDownload(
                rawUrl = url,
                title = title,
                source = source,
                chunkCount = chunkOverride ?: settings.defaultChunkCount,
                notificationsEnabled = settings.notificationsEnabled,
                wifiOnly = settings.wifiOnlyDownloads,
                maxConcurrent = settings.maxConcurrentDownloads,
                duplicateBehavior = settings.duplicateBehavior,
                autoRetry = settings.autoRetryDownloads,
                batchId = batchId,
                onDuplicateSkipped = onNotice,
            )
        }
    }

    fun queueBatch(
        entries: List<BatchExtractionManager.BatchQueueEntry>,
        onNotice: ((String) -> Unit)? = null,
    ) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            entries.forEach { entry ->
                engine.enqueueDownload(
                    rawUrl = entry.url,
                    title = entry.title,
                    source = entry.source,
                    chunkCount = settings.defaultChunkCount,
                    notificationsEnabled = settings.notificationsEnabled,
                    wifiOnly = settings.wifiOnlyDownloads,
                    maxConcurrent = settings.maxConcurrentDownloads,
                    duplicateBehavior = settings.duplicateBehavior,
                    autoRetry = settings.autoRetryDownloads,
                    batchId = entry.batchId,
                    onDuplicateSkipped = onNotice,
                )
            }
        }
    }

    fun pauseDownload(id: Long) = engine.pauseDownload(id)
    fun cancelDownload(id: Long) = engine.cancelAndFailDownload(id)

    fun resumeDownload(id: Long) {
        viewModelScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            engine.resumeDownload(
                id = id,
                notificationsEnabled = settings.notificationsEnabled,
                wifiOnly = settings.wifiOnlyDownloads,
                maxConcurrent = settings.maxConcurrentDownloads,
            )
        }
    }

    fun deleteDownload(id: Long) = engine.deleteDownload(id)

    fun clearCompleted() {
        viewModelScope.launch {
            downloadDao.clearCompleted()
        }
    }

    fun exportToPublicDownloads(entity: DownloadEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = ArchiveAndMediaInspector.exportToPublicDownloads(getApplication(), entity)
            result.onSuccess { uriStr ->
                downloadDao.update(entity.copy(exportedUri = uriStr))
                onResult("Saved to Downloads/AasmoLoader/${entity.fileName}")
            }.onFailure { err ->
                onResult(err.localizedMessage ?: "Export failed")
            }
        }
    }

    fun buildOpenIntent(entity: DownloadEntity): Intent? =
        ArchiveAndMediaInspector.buildOpenIntent(getApplication(), entity)

    fun buildShareIntent(entity: DownloadEntity): Intent? =
        ArchiveAndMediaInspector.buildShareIntent(getApplication(), entity)

    fun openFileInspector(entity: DownloadEntity) {
        viewModelScope.launch {
            val file = File(entity.localPath)
            val hash = entity.sha256.ifBlank { ArchiveAndMediaInspector.computeSha256(file) }
            val zipEntries = if (entity.fileName.endsWith(".zip", ignoreCase = true)) {
                ArchiveAndMediaInspector.listZipEntries(file)
            } else {
                emptyList()
            }
            val textPreview = if (zipEntries.isEmpty() && !entity.mimeType.startsWith("image/") && !entity.mimeType.startsWith("video/")) {
                ArchiveAndMediaInspector.readTextPreview(file)
            } else {
                null
            }
            _inspectorState.value = FileInspectorState(
                entity = entity,
                zipEntries = zipEntries,
                textPreview = textPreview,
                computedSha256 = hash,
            )
        }
    }

    fun dismissFileInspector() {
        _inspectorState.value = null
    }
}
