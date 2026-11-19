package com.example.data.media

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

data class DetectedTarget(
    val normalizedInput: String,
    val resolvedUrl: String,
    val isHandleOnly: Boolean,
    val handle: String?,
    val platform: SupportedPlatform,
    val initialClassification: MediaKind,
)

object PlatformDetector {
    private val shortenerHosts = setOf(
        "t.co",
        "bit.ly",
        "tinyurl.com",
        "youtu.be",
        "redd.it",
        "pin.it",
        "vm.tiktok.com",
        "vt.tiktok.com",
        "fb.watch",
        "goo.gl",
        "ow.ly",
        "is.gd",
    )

    private val directMediaExtensions = setOf(
        "mp4", "webm", "m4v", "mov", "mkv", "avi",
        "mp3", "wav", "ogg", "m4a", "flac", "aac",
        "jpg", "jpeg", "png", "webp", "gif", "svg",
        "pdf", "zip", "apk", "tar", "gz", "txt", "json", "csv",
    )

    private val youtubeDomains = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val redditDomains = setOf("reddit.com", "redd.it", "v.redd.it", "i.redd.it")
    private val instagramDomains = setOf("instagram.com", "cdninstagram.com", "instagr.am")
    private val twitterDomains = setOf("x.com", "twitter.com", "t.co", "twimg.com")
    private val facebookDomains = setOf("facebook.com", "fb.watch", "fb.com", "fbcdn.net")
    private val pinterestFixedDomains = setOf("pinterest.com", "pin.it", "pinimg.com")
    private val pinterestCcTldRegex = Regex("""^([a-z0-9-]+\.)*pinterest\.(co\.[a-z]{2}|com\.[a-z]{2}|[a-z]{2,3})$""")
    private val tiktokDomains = setOf("tiktok.com")
    private val threadsDomains = setOf("threads.net", "threads.com")

    fun isValidHttpUrl(
        raw: String,
        securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): Boolean {
        return UrlSecurityValidator.validateSyntaxAndHost(raw, securityPolicy).isValid
    }

    fun detect(
        rawInput: String,
        preferredPlatform: SupportedPlatform? = null,
        resolveRedirects: Boolean = true,
        securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): DetectedTarget {
        val trimmed = rawInput.trim()
        require(trimmed.isNotBlank()) { "Enter a URL or @username handle." }

        val hasExplicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*").matches(trimmed)
        if (hasExplicitScheme &&
            !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Invalid or unsafe URL scheme. Only http/https URLs are permitted.")
        }

        val isIpLiteralOrBracketed = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d+)?(/.*)?$""").matches(trimmed) ||
            trimmed.startsWith("[") ||
            trimmed.equals("localhost", ignoreCase = true) ||
            trimmed.startsWith("localhost:", ignoreCase = true) ||
            trimmed.startsWith("localhost/", ignoreCase = true)

        val looksLikeUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            isIpLiteralOrBracketed ||
            (!trimmed.startsWith("@") && !trimmed.contains(" ") && trimmed.contains("."))

        if (!looksLikeUrl) {
            val cleanHandle = trimmed.removePrefix("@").removePrefix("u/").removePrefix("/u/").trim()
            require(cleanHandle.isNotBlank() && !cleanHandle.contains(":")) {
                "Invalid URL or @username handle."
            }
            val targetPlatform = preferredPlatform ?: SupportedPlatform.YOUTUBE
            val profileUrl = when (targetPlatform) {
                SupportedPlatform.YOUTUBE -> "https://www.youtube.com/@$cleanHandle"
                SupportedPlatform.REDDIT -> "https://www.reddit.com/user/$cleanHandle"
                SupportedPlatform.INSTAGRAM -> "https://www.instagram.com/$cleanHandle/"
                SupportedPlatform.TWITTER -> "https://x.com/$cleanHandle"
                SupportedPlatform.FACEBOOK -> "https://www.facebook.com/$cleanHandle"
                SupportedPlatform.PINTEREST -> "https://www.pinterest.com/$cleanHandle/"
                SupportedPlatform.TIKTOK -> "https://www.tiktok.com/@$cleanHandle"
                SupportedPlatform.THREADS -> "https://www.threads.net/@$cleanHandle"
                else -> "https://www.youtube.com/@$cleanHandle"
            }
            return DetectedTarget(
                normalizedInput = "@$cleanHandle",
                resolvedUrl = profileUrl,
                isHandleOnly = true,
                handle = "@$cleanHandle",
                platform = targetPlatform,
                initialClassification = MediaKind.PROFILE,
            )
        }

        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }

        when (val validation = UrlSecurityValidator.validateSyntaxAndHost(withScheme, securityPolicy)) {
            is UrlValidationResult.Rejected -> throw IllegalArgumentException(validation.reason)
            is UrlValidationResult.Valid -> Unit
        }

        val finalUrl = if (resolveRedirects && shouldResolveRedirects(withScheme)) {
            resolveRedirectSafely(withScheme, securityPolicy)
        } else {
            withScheme
        }

        val platform = identifyPlatformByHost(finalUrl)
        val kind = classifyUrlKind(finalUrl, platform)

        return DetectedTarget(
            normalizedInput = trimmed,
            resolvedUrl = finalUrl,
            isHandleOnly = false,
            handle = extractHandleFromUrl(finalUrl, platform),
            platform = platform,
            initialClassification = kind,
        )
    }

    fun matchesDomain(host: String, domain: String): Boolean {
        val cleanHost = host.trimEnd('.').lowercase()
        val cleanDomain = domain.trimEnd('.').lowercase()
        if (cleanHost.isBlank() || cleanDomain.isBlank()) return false
        return cleanHost == cleanDomain || cleanHost.endsWith(".$cleanDomain")
    }

    fun matchesAnyDomain(host: String, domains: Set<String>): Boolean {
        return domains.any { matchesDomain(host, it) }
    }

    fun identifyPlatformByHost(url: String): SupportedPlatform {
        val parsed = url.toHttpUrlOrNull() ?: return SupportedPlatform.WEB_GENERIC
        val host = parsed.host.trimEnd('.').lowercase()
        val ext = parsed.encodedPath.substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .lowercase()

        return when {
            matchesAnyDomain(host, youtubeDomains) -> SupportedPlatform.YOUTUBE
            matchesAnyDomain(host, redditDomains) -> SupportedPlatform.REDDIT
            matchesAnyDomain(host, instagramDomains) -> SupportedPlatform.INSTAGRAM
            matchesAnyDomain(host, twitterDomains) -> SupportedPlatform.TWITTER
            matchesAnyDomain(host, facebookDomains) -> SupportedPlatform.FACEBOOK
            matchesAnyDomain(host, pinterestFixedDomains) || pinterestCcTldRegex.matches(host) -> SupportedPlatform.PINTEREST
            matchesAnyDomain(host, tiktokDomains) -> SupportedPlatform.TIKTOK
            matchesAnyDomain(host, threadsDomains) -> SupportedPlatform.THREADS
            ext in directMediaExtensions -> SupportedPlatform.DIRECT_MEDIA
            else -> SupportedPlatform.WEB_GENERIC
        }
    }

    fun classifyUrlKind(url: String, platform: SupportedPlatform): MediaKind {
        val parsed = url.toHttpUrlOrNull()
        val path = parsed?.encodedPath?.lowercase().orEmpty()
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
        if (ext in directMediaExtensions && platform == SupportedPlatform.DIRECT_MEDIA) {
            return MediaKind.DIRECT_MEDIA
        }
        if (platform == SupportedPlatform.DIRECT_MEDIA) return MediaKind.DIRECT_MEDIA

        val host = parsed?.host?.lowercase().orEmpty()
        return when (platform) {
            SupportedPlatform.YOUTUBE -> when {
                path.contains("/watch") || path.contains("/shorts/") || path.contains("/live/") -> MediaKind.POST
                matchesDomain(host, "youtu.be") && path.length > 1 -> MediaKind.POST
                path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/") -> MediaKind.PROFILE
                else -> MediaKind.WEBPAGE
            }
            SupportedPlatform.REDDIT -> when {
                path.contains("/comments/") -> MediaKind.POST
                path.startsWith("/r/") && !path.endsWith("/comments") -> MediaKind.PROFILE
                path.startsWith("/user/") || path.startsWith("/u/") -> MediaKind.PROFILE
                matchesDomain(host, "v.redd.it") || matchesDomain(host, "i.redd.it") -> MediaKind.DIRECT_MEDIA
                else -> MediaKind.WEBPAGE
            }
            SupportedPlatform.INSTAGRAM -> when {
                path.contains("/p/") || path.contains("/reel/") || path.contains("/tv/") -> MediaKind.POST
                path.count { it == '/' } <= 2 && path.length > 1 -> MediaKind.PROFILE
                else -> MediaKind.WEBPAGE
            }
            SupportedPlatform.TWITTER -> when {
                path.contains("/status/") -> MediaKind.POST
                path.count { it == '/' } <= 2 && path.length > 1 -> MediaKind.PROFILE
                else -> MediaKind.WEBPAGE
            }
            SupportedPlatform.TIKTOK -> when {
                path.contains("/video/") -> MediaKind.POST
                path.startsWith("/@") -> MediaKind.PROFILE
                else -> MediaKind.WEBPAGE
            }
            SupportedPlatform.THREADS -> when {
                path.contains("/post/") -> MediaKind.POST
                path.startsWith("/@") -> MediaKind.PROFILE
                else -> MediaKind.WEBPAGE
            }
            SupportedPlatform.PINTEREST -> if (path.contains("/pin/")) MediaKind.POST else MediaKind.PROFILE
            SupportedPlatform.FACEBOOK -> if (path.contains("/videos/") || path.contains("/posts/") || path.contains("/reel/")) MediaKind.POST else MediaKind.PROFILE
            SupportedPlatform.DIRECT_MEDIA -> MediaKind.DIRECT_MEDIA
            SupportedPlatform.WEB_GENERIC -> MediaKind.WEBPAGE
        }
    }

    private fun extractHandleFromUrl(url: String, platform: SupportedPlatform): String? {
        val segs = url.toHttpUrlOrNull()?.pathSegments?.filter { it.isNotBlank() }.orEmpty()
        val first = segs.firstOrNull() ?: return null
        return when (platform) {
            SupportedPlatform.REDDIT -> if (first == "r" || first == "u" || first == "user") {
                segs.getOrNull(1)?.let { if (first == "r") "r/$it" else "@$it" }
            } else null
            SupportedPlatform.YOUTUBE, SupportedPlatform.TIKTOK, SupportedPlatform.THREADS ->
                if (first.startsWith("@")) first else "@$first"
            else -> if (first !in setOf("watch", "p", "reel", "pin", "status", "videos", "posts")) "@$first" else null
        }
    }

    private fun shouldResolveRedirects(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        return matchesAnyDomain(host, shortenerHosts)
    }

    fun resolveRedirectSafely(
        url: String,
        securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): String {
        val client = UrlSecurityValidator.createSafeHttpClient(securityPolicy)
        return try {
            val req = Request.Builder().url(url).head().build()
            UrlSecurityValidator.executeWithSafeRedirects(client, req, securityPolicy).use { resp ->
                resp.request.url.toString()
            }
        } catch (sec: SecurityException) {
            throw IllegalArgumentException(sec.message ?: "Blocked unsafe redirect target.", sec)
        } catch (_: Exception) {
            // Fall back cleanly if HEAD on shortener failed, still validated initially
            url
        }
    }
}
