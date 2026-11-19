package com.example.data.media

import com.example.data.local.DownloadSource
import com.example.data.local.ExtractionHistoryDao
import com.example.data.local.ExtractionHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SocialExtractorManager(
    private val historyDao: ExtractionHistoryDao? = null,
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) {
    private val adapters: Map<SupportedPlatform, PlatformAdapter> = mapOf(
        SupportedPlatform.YOUTUBE to YouTubeAdapter(securityPolicy),
        SupportedPlatform.REDDIT to RedditAdapter(securityPolicy),
        SupportedPlatform.INSTAGRAM to SocialOpenGraphPlatformAdapter(
            platform = SupportedPlatform.INSTAGRAM,
            securityPolicy = securityPolicy,
        ),
        SupportedPlatform.TWITTER to SocialOpenGraphPlatformAdapter(
            platform = SupportedPlatform.TWITTER,
            oEmbedEndpoint = "https://publish.twitter.com/oembed?url=",
            securityPolicy = securityPolicy,
        ),
        SupportedPlatform.FACEBOOK to SocialOpenGraphPlatformAdapter(
            platform = SupportedPlatform.FACEBOOK,
            securityPolicy = securityPolicy,
        ),
        SupportedPlatform.PINTEREST to SocialOpenGraphPlatformAdapter(
            platform = SupportedPlatform.PINTEREST,
            securityPolicy = securityPolicy,
        ),
        SupportedPlatform.TIKTOK to SocialOpenGraphPlatformAdapter(
            platform = SupportedPlatform.TIKTOK,
            oEmbedEndpoint = "https://www.tiktok.com/oembed?url=",
            securityPolicy = securityPolicy,
        ),
        SupportedPlatform.THREADS to SocialOpenGraphPlatformAdapter(
            platform = SupportedPlatform.THREADS,
            securityPolicy = securityPolicy,
        ),
        SupportedPlatform.DIRECT_MEDIA to DirectMediaAdapter(securityPolicy),
        SupportedPlatform.WEB_GENERIC to WebGenericAdapter(securityPolicy),
    )

    suspend fun analyze(
        rawInput: String,
        preferredPlatform: SupportedPlatform? = null,
        pageCursor: String? = null,
    ): Result<UniversalExtractionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val target = PlatformDetector.detect(
                rawInput = rawInput,
                preferredPlatform = preferredPlatform,
                resolveRedirects = true,
                securityPolicy = securityPolicy,
            )
            val adapter = adapters[target.platform] ?: WebGenericAdapter(securityPolicy)
            val result = adapter.extract(target, pageCursor)
            if (pageCursor == null && historyDao != null) {
                runCatching {
                    historyDao.insert(
                        ExtractionHistoryEntity(
                            inputQuery = target.normalizedInput,
                            resolvedUrl = result.resolvedFinalUrl,
                            platformId = result.platform.id,
                            title = result.profile?.displayName
                                ?: result.items.firstOrNull()?.title
                                ?: result.resolvedFinalUrl,
                            itemCount = result.items.size,
                        )
                    )
                }
            }
            result
        }
    }
}

object BatchExtractionManager {
    data class BatchQueueEntry(
        val url: String,
        val title: String,
        val source: DownloadSource,
        val batchId: String,
    )

    fun prepareEligibleBatch(items: List<ResolvedMediaItem>): List<BatchQueueEntry> {
        val eligible = items.filter { it.isDownloadable }
        if (eligible.isEmpty()) return emptyList()
        val batchId = "batch_${UUID.randomUUID().toString().take(8)}"
        return eligible.mapNotNull { item ->
            val dlUrl = item.downloadUrl ?: return@mapNotNull null
            val src = when (item.platform) {
                SupportedPlatform.DIRECT_MEDIA -> DownloadSource.DIRECT
                SupportedPlatform.WEB_GENERIC -> DownloadSource.BROWSER
                else -> DownloadSource.SOCIAL
            }
            BatchQueueEntry(
                url = dlUrl,
                title = item.title,
                source = src,
                batchId = batchId,
            )
        }
    }
}
