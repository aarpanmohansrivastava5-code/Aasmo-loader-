package com.example.data.media

enum class MediaKind(val label: String) {
    DIRECT_MEDIA("Direct Media"),
    WEBPAGE("Webpage"),
    PROFILE("Profile"),
    POST("Post"),
    THUMBNAIL("Thumbnail"),
    EMBEDDED_MEDIA("Embedded Media"),
    UNSUPPORTED("Unsupported"),
}

enum class DownloadEligibility(val label: String) {
    VERIFIED_DOWNLOADABLE("Verified"),
    PREVIEW_ONLY("Preview Only"),
    REQUIRES_AUTHENTICATION("Auth Required"),
    UNSUPPORTED("Unsupported"),
    VERIFICATION_FAILED("Failed"),
}

data class PlatformCapabilities(
    val supportsPublicOEmbed: Boolean = false,
    val supportsPublicJsonFeed: Boolean = false,
    val supportsDirectStreamDetection: Boolean = false,
    val supportsProfileDiscovery: Boolean = false,
    val supportsPagination: Boolean = false,
    val accessNote: String,
)

enum class SupportedPlatform(
    val id: String,
    val label: String,
    val baseHost: String,
    val badgeColorHex: Long,
    val capabilities: PlatformCapabilities,
) {
    YOUTUBE(
        id = "yt",
        label = "YouTube",
        baseHost = "youtube.com",
        badgeColorHex = 0xFFFF4D4D,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = true,
            supportsPublicJsonFeed = true,
            supportsDirectStreamDetection = false,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "oEmbed & public RSS channel metadata; thumbnails downloadable directly, video playback via browser.",
        ),
    ),
    REDDIT(
        id = "rd",
        label = "Reddit",
        baseHost = "reddit.com",
        badgeColorHex = 0xFFFF6A2B,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = false,
            supportsPublicJsonFeed = true,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = true,
            accessNote = "Public JSON feed supports posts, direct images, v.redd.it video streams, and cursor pagination.",
        ),
    ),
    INSTAGRAM(
        id = "ig",
        label = "Instagram",
        baseHost = "instagram.com",
        badgeColorHex = 0xFFFF6FA8,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = false,
            supportsPublicJsonFeed = false,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "Public post/reel metadata & OpenGraph previews; private/login-gated feeds require browser or Graph API.",
        ),
    ),
    TWITTER(
        id = "x",
        label = "X / Twitter",
        baseHost = "x.com",
        badgeColorHex = 0xFF27D3C3,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = true,
            supportsPublicJsonFeed = false,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "Public oEmbed & OpenGraph media previews; authenticated timelines require official API access.",
        ),
    ),
    FACEBOOK(
        id = "fb",
        label = "Facebook",
        baseHost = "facebook.com",
        badgeColorHex = 0xFF5B6CFF,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = false,
            supportsPublicJsonFeed = false,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "Public post previews & OpenGraph metadata where accessible without login wall.",
        ),
    ),
    PINTEREST(
        id = "pin",
        label = "Pinterest",
        baseHost = "pinterest.com",
        badgeColorHex = 0xFFE60023,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = false,
            supportsPublicJsonFeed = false,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "Public pin images & accessible video previews via OpenGraph metadata.",
        ),
    ),
    TIKTOK(
        id = "tt",
        label = "TikTok",
        baseHost = "tiktok.com",
        badgeColorHex = 0xFF00F2EA,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = true,
            supportsPublicJsonFeed = false,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "Official public TikTok oEmbed metadata & cover thumbnails; restricted videos open in browser.",
        ),
    ),
    THREADS(
        id = "th",
        label = "Threads",
        baseHost = "threads.net",
        badgeColorHex = 0xFF9C8CFF,
        capabilities = PlatformCapabilities(
            supportsPublicOEmbed = false,
            supportsPublicJsonFeed = false,
            supportsDirectStreamDetection = true,
            supportsProfileDiscovery = true,
            supportsPagination = false,
            accessNote = "Public post OpenGraph metadata & accessible attachments.",
        ),
    ),
    DIRECT_MEDIA(
        id = "direct",
        label = "Direct File",
        baseHost = "",
        badgeColorHex = 0xFF2FBF71,
        capabilities = PlatformCapabilities(
            supportsDirectStreamDetection = true,
            accessNote = "Verified direct file URL with MIME type and byte-range inspection.",
        ),
    ),
    WEB_GENERIC(
        id = "web",
        label = "Webpage",
        baseHost = "",
        badgeColorHex = 0xFF7B8CA6,
        capabilities = PlatformCapabilities(
            supportsDirectStreamDetection = true,
            accessNote = "Inspects webpage OpenGraph & embedded media tags without spoofing HTML as video.",
        ),
    );
}

data class ResolvedMediaItem(
    val id: String,
    val platform: SupportedPlatform,
    val kind: MediaKind,
    val eligibility: DownloadEligibility,
    val accessStatus: String,
    val title: String,
    val creator: String,
    val thumbnailUrl: String?,
    val originalUrl: String,
    val downloadUrl: String?, // Non-null ONLY when verified direct asset exists
    val mimeHint: String,
    val formatBadge: String,
    val durationLabel: String? = null,
    val fileSizeLabel: String? = null,
) {
    val isDownloadable: Boolean
        get() = eligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE && !downloadUrl.isNullOrBlank()
}

data class CreatorProfileSummary(
    val platform: SupportedPlatform,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    val profileUrl: String,
    val retrievedCount: Int,
    val isPartialSubset: Boolean,
    val limitationNotice: String,
    val nextPageCursor: String? = null,
)

data class UniversalExtractionResult(
    val inputQuery: String,
    val resolvedFinalUrl: String,
    val platform: SupportedPlatform,
    val classification: MediaKind,
    val profile: CreatorProfileSummary? = null,
    val items: List<ResolvedMediaItem>,
    val statusBanner: String,
    val nextPageCursor: String? = null,
    val extractedAt: Long = System.currentTimeMillis(),
) {
    val downloadableCount: Int get() = items.count { it.isDownloadable }
}
