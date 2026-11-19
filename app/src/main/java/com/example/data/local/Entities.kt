package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

enum class DownloadSource {
    DIRECT,
    GITHUB,
    SOCIAL,
    BROWSER,
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val originalUrl: String = "",
    val fileName: String,
    val source: String = DownloadSource.DIRECT.name,
    val mimeType: String = "application/octet-stream",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val chunkCount: Int = 8,
    val chunkProgressCsv: String = "0,0,0,0,0,0,0,0",
    val status: String = DownloadStatus.QUEUED.name,
    val localPath: String = "",
    val sha256: String = "",
    val exportedUri: String = "",
    val batchId: String = "",
    val retryCount: Int = 0,
    val maxRetries: Int = 2,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val badgeLetter: String,
    val badgeColorHex: Long,
    val isSpeedDial: Boolean = true,
    val folderName: String = "General",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val domain: String = "",
    val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "site_rules")
data class SiteRuleEntity(
    @PrimaryKey val host: String,
    val adShieldEnabled: Boolean = true,
    val allowListed: Boolean = false,
    val desktopMode: Boolean = false,
    val allowJavascript: Boolean = true,
    val allowLocation: Boolean = false,
    val allowCamera: Boolean = false,
    val allowMicrophone: Boolean = false,
    val allowPopups: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "filter_rules")
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pattern: String,
    val isAllowRule: Boolean = false,
    val category: String = "CUSTOM",
    val enabled: Boolean = true,
)

@Entity(tableName = "extraction_history")
data class ExtractionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val inputQuery: String,
    val resolvedUrl: String,
    val platformId: String,
    val title: String,
    val itemCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
