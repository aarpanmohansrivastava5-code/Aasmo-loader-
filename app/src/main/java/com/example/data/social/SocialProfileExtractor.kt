package com.example.data.social

import com.example.data.media.DownloadEligibility
import com.example.data.media.SocialExtractorManager
import com.example.data.media.SupportedPlatform

enum class SocialPlatform(
    val id: String,
    val label: String,
    val baseHost: String,
    val badgeColorHex: Long,
    val mappedPlatform: SupportedPlatform,
) {
    YOUTUBE("yt", "YouTube", "youtube.com", 0xFFFF4D4D, SupportedPlatform.YOUTUBE),
    INSTAGRAM("ig", "Instagram", "instagram.com", 0xFFFF6FA8, SupportedPlatform.INSTAGRAM),
    REDDIT("rd", "Reddit", "reddit.com", 0xFFFF6A2B, SupportedPlatform.REDDIT),
    TWITTER("x", "X / Twitter", "x.com", 0xFF27D3C3, SupportedPlatform.TWITTER),
    TIKTOK("tt", "TikTok", "tiktok.com", 0xFF00F2EA, SupportedPlatform.TIKTOK),
    FACEBOOK("fb", "Facebook", "facebook.com", 0xFF5B6CFF, SupportedPlatform.FACEBOOK),
    PINTEREST("pin", "Pinterest", "pinterest.com", 0xFFE60023, SupportedPlatform.PINTEREST),
    THREADS("th", "Threads", "threads.net", 0xFF9C8CFF, SupportedPlatform.THREADS),
}

enum class SocialMediaCategory(val label: String) {
    ALL("All"),
    POSTS("Posts"),
    REELS("Reels"),
    STORIES("Stories"),
    HIGHLIGHTS("Highlights"),
}

data class ExtractedSocialMedia(
    val id: String,
    val platform: SocialPlatform,
    val category: SocialMediaCategory,
    val handle: String,
    val title: String,
    val downloadUrl: String,
    val previewInfo: String,
    val estimatedSizeLabel: String,
    val formatBadge: String,
    val isLiveResolved: Boolean,
    val isDirectlyDownloadable: Boolean = false,
)

class SocialProfileExtractor(
    private val manager: SocialExtractorManager = SocialExtractorManager(),
) {
    suspend fun inspectInput(
        platform: SocialPlatform,
        rawInput: String,
        category: SocialMediaCategory,
    ): Result<List<ExtractedSocialMedia>> {
        return manager.analyze(
            rawInput = rawInput,
            preferredPlatform = platform.mappedPlatform,
        ).map { extraction ->
            val resolvedPlatform = SocialPlatform.entries.firstOrNull {
                it.mappedPlatform == extraction.platform
            } ?: platform

            extraction.items.map { item ->
                ExtractedSocialMedia(
                    id = item.id,
                    platform = resolvedPlatform,
                    category = category,
                    handle = item.creator,
                    title = item.title,
                    downloadUrl = item.downloadUrl ?: item.originalUrl,
                    previewInfo = item.accessStatus,
                    estimatedSizeLabel = item.fileSizeLabel ?: item.mimeHint,
                    formatBadge = item.formatBadge,
                    isLiveResolved = item.eligibility != DownloadEligibility.UNSUPPORTED,
                    isDirectlyDownloadable = item.isDownloadable,
                )
            }
        }
    }
}
