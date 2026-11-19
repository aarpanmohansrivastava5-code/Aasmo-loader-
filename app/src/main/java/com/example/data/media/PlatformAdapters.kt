package com.example.data.media

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

interface PlatformAdapter {
    val platform: SupportedPlatform
    fun extract(target: DetectedTarget, pageCursor: String? = null): UniversalExtractionResult
}

internal fun ProbeVerdict.toEligibility(): DownloadEligibility = when (this) {
    ProbeVerdict.VERIFIED_MEDIA -> DownloadEligibility.VERIFIED_DOWNLOADABLE
    ProbeVerdict.HTML_PAGE -> DownloadEligibility.PREVIEW_ONLY
    ProbeVerdict.REQUIRES_AUTH -> DownloadEligibility.REQUIRES_AUTHENTICATION
    ProbeVerdict.UNSUPPORTED_TYPE -> DownloadEligibility.UNSUPPORTED
    ProbeVerdict.VERIFICATION_FAILED -> DownloadEligibility.VERIFICATION_FAILED
}

class YouTubeAdapter(
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) : PlatformAdapter {
    override val platform = SupportedPlatform.YOUTUBE

    override fun extract(target: DetectedTarget, pageCursor: String?): UniversalExtractionResult {
        val url = target.resolvedUrl
        if (target.initialClassification == MediaKind.PROFILE || target.isHandleOnly) {
            val og = MediaMetadataExtractor.fetchOpenGraphMetadata(url, securityPolicy)
            val channelId = og?.channelId
            val items = mutableListOf<ResolvedMediaItem>()
            val displayName = og?.title ?: (target.handle ?: "YouTube Channel")
            val avatarUrl = og?.imageUrl

            if (!avatarUrl.isNullOrBlank()) {
                val avatarProbe = MediaMetadataExtractor.probeMediaTarget(avatarUrl, securityPolicy)
                val avatarVerified = avatarProbe.isVerifiedDownloadable && avatarProbe.mimeType.startsWith("image/")
                items.add(
                    ResolvedMediaItem(
                        id = "yt_avatar_${avatarUrl.hashCode()}",
                        platform = SupportedPlatform.YOUTUBE,
                        kind = MediaKind.THUMBNAIL,
                        eligibility = if (avatarVerified) DownloadEligibility.VERIFIED_DOWNLOADABLE else avatarProbe.verdict.toEligibility(),
                        accessStatus = if (avatarVerified) {
                            "Verified channel avatar (${avatarProbe.mimeType})"
                        } else {
                            avatarProbe.errorDetail ?: "Avatar verification failed"
                        },
                        title = "$displayName — Channel Avatar",
                        creator = target.handle ?: displayName,
                        thumbnailUrl = if (avatarVerified) avatarProbe.finalUrl else null,
                        originalUrl = og.finalUrl,
                        downloadUrl = if (avatarVerified) avatarProbe.finalUrl else null,
                        mimeHint = avatarProbe.mimeType.ifBlank { "image/jpeg" },
                        formatBadge = "AVATAR",
                        fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(avatarProbe.contentLength),
                    )
                )
            }

            if (!channelId.isNullOrBlank()) {
                val rssItems = fetchYouTubeRssFeed(channelId)
                items.addAll(rssItems)
            }

            val profile = CreatorProfileSummary(
                platform = SupportedPlatform.YOUTUBE,
                handle = target.handle ?: displayName,
                displayName = displayName,
                avatarUrl = avatarUrl,
                profileUrl = url,
                retrievedCount = items.size,
                isPartialSubset = true,
                limitationNotice = when {
                    og == null -> "Channel page could not be verified."
                    og.httpCode in setOf(401, 403, 407) -> "Channel returned HTTP ${og.httpCode} authorization requirement."
                    channelId != null -> "Public YouTube RSS returns the latest ${items.count { it.kind == MediaKind.POST }} uploads + verified avatar. Video playback uses browser; verified thumbnails are downloadable."
                    else -> "Public metadata retrieved; full channel archive requires YouTube Data API."
                },
            )
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = og?.finalUrl ?: url,
                platform = SupportedPlatform.YOUTUBE,
                classification = MediaKind.PROFILE,
                profile = profile,
                items = items,
                statusBanner = profile.limitationNotice,
            )
        }

        val oembed = MediaMetadataExtractor.fetchOEmbed(
            endpointBase = "https://www.youtube.com/oembed?format=json&url=",
            targetUrl = url,
            securityPolicy = securityPolicy,
        )
        val og = if (oembed == null) MediaMetadataExtractor.fetchOpenGraphMetadata(url, securityPolicy) else null
        val resolvedPageUrl = og?.finalUrl ?: url

        if (oembed == null && (og == null || og.httpCode !in 200..299)) {
            val code = og?.httpCode ?: 0
            val failureEligibility = when (code) {
                401, 403, 407 -> DownloadEligibility.REQUIRES_AUTHENTICATION
                else -> DownloadEligibility.VERIFICATION_FAILED
            }
            val reason = if (code > 0) "YouTube returned HTTP $code" else "Unable to verify YouTube target"
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = resolvedPageUrl,
                platform = SupportedPlatform.YOUTUBE,
                classification = MediaKind.POST,
                items = listOf(
                    ResolvedMediaItem(
                        id = "yt_err_${url.hashCode()}",
                        platform = SupportedPlatform.YOUTUBE,
                        kind = MediaKind.POST,
                        eligibility = failureEligibility,
                        accessStatus = reason,
                        title = "YouTube Video",
                        creator = target.handle ?: "YouTube Creator",
                        thumbnailUrl = null,
                        originalUrl = resolvedPageUrl,
                        downloadUrl = null,
                        mimeHint = "text/html",
                        formatBadge = "YOUTUBE",
                    )
                ),
                statusBanner = reason,
            )
        }

        val title = oembed?.optString("title")?.takeIf { it.isNotBlank() }
            ?: og?.title ?: "YouTube Video"
        val creator = oembed?.optString("author_name")?.takeIf { it.isNotBlank() }
            ?: target.handle ?: "YouTube Creator"
        val rawThumb = oembed?.optString("thumbnail_url")?.takeIf { it.isNotBlank() } ?: og?.imageUrl
        val isShort = resolvedPageUrl.contains("/shorts/", ignoreCase = true)

        val thumbProbe = rawThumb
            ?.takeIf { UrlSecurityValidator.validateSyntaxAndHost(it, securityPolicy).isValid }
            ?.let { MediaMetadataExtractor.probeMediaTarget(it, securityPolicy) }
        val verifiedThumb = thumbProbe?.takeIf { it.isVerifiedDownloadable && it.mimeType.startsWith("image/") }

        val items = mutableListOf<ResolvedMediaItem>()
        items.add(
            ResolvedMediaItem(
                id = "yt_page_${resolvedPageUrl.hashCode()}",
                platform = SupportedPlatform.YOUTUBE,
                kind = MediaKind.POST,
                eligibility = DownloadEligibility.PREVIEW_ONLY,
                accessStatus = "Metadata preview only — YouTube does not expose unauthenticated direct MP4 streams",
                title = title,
                creator = creator,
                thumbnailUrl = verifiedThumb?.finalUrl,
                originalUrl = resolvedPageUrl,
                downloadUrl = null,
                mimeHint = "text/html",
                formatBadge = if (isShort) "SHORTS" else "WATCH",
            )
        )
        if (verifiedThumb != null) {
            items.add(
                ResolvedMediaItem(
                    id = "yt_thumb_${verifiedThumb.finalUrl.hashCode()}",
                    platform = SupportedPlatform.YOUTUBE,
                    kind = MediaKind.THUMBNAIL,
                    eligibility = DownloadEligibility.VERIFIED_DOWNLOADABLE,
                    accessStatus = "Verified cover thumbnail (${verifiedThumb.mimeType})",
                    title = "$title — Cover Thumbnail",
                    creator = creator,
                    thumbnailUrl = verifiedThumb.finalUrl,
                    originalUrl = resolvedPageUrl,
                    downloadUrl = verifiedThumb.finalUrl,
                    mimeHint = verifiedThumb.mimeType,
                    formatBadge = "THUMB",
                    fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(verifiedThumb.contentLength),
                )
            )
        }

        return UniversalExtractionResult(
            inputQuery = target.normalizedInput,
            resolvedFinalUrl = resolvedPageUrl,
            platform = SupportedPlatform.YOUTUBE,
            classification = MediaKind.POST,
            items = items,
            statusBanner = "YouTube metadata retrieved (${items.count { it.isDownloadable }} verified thumbnail asset).",
        )
    }

    private fun fetchYouTubeRssFeed(channelId: String): List<ResolvedMediaItem> {
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val client = UrlSecurityValidator.createSafeHttpClient(securityPolicy)
        val xml = runCatching {
            val req = Request.Builder().url(feedUrl).build()
            UrlSecurityValidator.executeWithSafeRedirects(client, req, securityPolicy).use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
        if (xml.isBlank()) return emptyList()

        val items = mutableListOf<ResolvedMediaItem>()
        var verifiedThumbBudget = 4
        runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            var inEntry = false
            var title = ""
            var videoUrl = ""
            var thumbUrl = ""
            var author = ""
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT && items.size < 15) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        when {
                            tag == "entry" -> {
                                inEntry = true; title = ""; videoUrl = ""; thumbUrl = ""
                            }
                            !inEntry && tag == "name" -> author = parser.nextText()
                            inEntry && tag == "title" -> title = parser.nextText()
                            inEntry && tag == "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) videoUrl = href
                            }
                            inEntry && tag == "thumbnail" -> {
                                val u = parser.getAttributeValue(null, "url")
                                if (!u.isNullOrBlank()) thumbUrl = u
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "entry" && inEntry) {
                            inEntry = false
                            if (videoUrl.isNotBlank() && UrlSecurityValidator.validateSyntaxAndHost(videoUrl, securityPolicy).isValid) {
                                items.add(
                                    ResolvedMediaItem(
                                        id = "yt_rss_${videoUrl.hashCode()}",
                                        platform = SupportedPlatform.YOUTUBE,
                                        kind = MediaKind.POST,
                                        eligibility = DownloadEligibility.PREVIEW_ONLY,
                                        accessStatus = "Channel RSS feed entry (open in browser to watch)",
                                        title = title.ifBlank { "Channel Video" },
                                        creator = author.ifBlank { channelId },
                                        thumbnailUrl = thumbUrl.takeIf { UrlSecurityValidator.validateSyntaxAndHost(it, securityPolicy).isValid },
                                        originalUrl = videoUrl,
                                        downloadUrl = null,
                                        mimeHint = "text/html",
                                        formatBadge = "VIDEO POST",
                                    )
                                )
                                if (thumbUrl.isNotBlank() && verifiedThumbBudget > 0) {
                                    verifiedThumbBudget--
                                    val tProbe = MediaMetadataExtractor.probeMediaTarget(thumbUrl, securityPolicy)
                                    if (tProbe.isVerifiedDownloadable && tProbe.mimeType.startsWith("image/")) {
                                        items.add(
                                            ResolvedMediaItem(
                                                id = "yt_rss_thumb_${tProbe.finalUrl.hashCode()}",
                                                platform = SupportedPlatform.YOUTUBE,
                                                kind = MediaKind.THUMBNAIL,
                                                eligibility = DownloadEligibility.VERIFIED_DOWNLOADABLE,
                                                accessStatus = "Verified thumbnail (${tProbe.mimeType})",
                                                title = "${title.take(36)} — Thumbnail",
                                                creator = author.ifBlank { channelId },
                                                thumbnailUrl = tProbe.finalUrl,
                                                originalUrl = videoUrl,
                                                downloadUrl = tProbe.finalUrl,
                                                mimeHint = tProbe.mimeType,
                                                formatBadge = "THUMB",
                                                fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(tProbe.contentLength),
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return items
    }
}

class RedditAdapter(
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) : PlatformAdapter {
    override val platform = SupportedPlatform.REDDIT

    override fun extract(target: DetectedTarget, pageCursor: String?): UniversalExtractionResult {
        val baseUrl = target.resolvedUrl.substringBefore('?').trimEnd('/')
        val jsonUrl = buildString {
            append(if (baseUrl.endsWith(".json")) baseUrl else "$baseUrl.json")
            append("?raw_json=1&limit=15")
            if (!pageCursor.isNullOrBlank()) append("&after=$pageCursor")
        }

        val client = UrlSecurityValidator.createSafeHttpClient(securityPolicy)
        var httpCode = 0
        val body = runCatching {
            val req = Request.Builder()
                .url(jsonUrl)
                .header("Accept", "application/json")
                .build()
            UrlSecurityValidator.executeWithSafeRedirects(client, req, securityPolicy).use { resp ->
                httpCode = resp.code
                if (!resp.isSuccessful) throw IllegalStateException("Reddit returned HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }.getOrElse { err ->
            val eligibility = if (httpCode in setOf(401, 403, 407)) {
                DownloadEligibility.REQUIRES_AUTHENTICATION
            } else {
                DownloadEligibility.VERIFICATION_FAILED
            }
            val msg = "Reddit API error: ${err.localizedMessage ?: "Access blocked"}"
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = target.resolvedUrl,
                platform = SupportedPlatform.REDDIT,
                classification = target.initialClassification,
                items = listOf(
                    ResolvedMediaItem(
                        id = "rd_err_${target.resolvedUrl.hashCode()}",
                        platform = SupportedPlatform.REDDIT,
                        kind = target.initialClassification,
                        eligibility = eligibility,
                        accessStatus = msg,
                        title = target.handle ?: "Reddit Target",
                        creator = target.handle ?: "Reddit",
                        thumbnailUrl = null,
                        originalUrl = target.resolvedUrl,
                        downloadUrl = null,
                        mimeHint = "application/json",
                        formatBadge = if (eligibility == DownloadEligibility.REQUIRES_AUTHENTICATION) "AUTH" else "FAILED",
                    )
                ),
                statusBanner = msg,
            )
        }

        val resolvedItems = mutableListOf<ResolvedMediaItem>()
        var nextAfter: String? = null
        var probeBudget = 8

        runCatching {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                val listings = JSONArray(trimmed)
                val postData = listings.optJSONObject(0)
                    ?.optJSONObject("data")
                    ?.optJSONArray("children")
                if (postData != null) {
                    for (i in 0 until postData.length()) {
                        postData.optJSONObject(i)?.optJSONObject("data")?.let {
                            probeBudget = extractPostMedia(it, resolvedItems, probeBudget)
                        }
                    }
                }
            } else {
                val rootData = JSONObject(trimmed).optJSONObject("data")
                nextAfter = rootData?.optString("after")?.takeIf { it.isNotBlank() && it != "null" }
                val children = rootData?.optJSONArray("children")
                if (children != null) {
                    for (i in 0 until children.length()) {
                        children.optJSONObject(i)?.optJSONObject("data")?.let {
                            probeBudget = extractPostMedia(it, resolvedItems, probeBudget)
                        }
                    }
                }
            }
        }

        val profile = if (target.initialClassification == MediaKind.PROFILE || target.isHandleOnly) {
            CreatorProfileSummary(
                platform = SupportedPlatform.REDDIT,
                handle = target.handle ?: "Reddit Feed",
                displayName = target.handle ?: baseUrl.substringAfterLast('/'),
                avatarUrl = null,
                profileUrl = target.resolvedUrl,
                retrievedCount = resolvedItems.size,
                isPartialSubset = nextAfter != null,
                limitationNotice = "Retrieved ${resolvedItems.size} entries via Reddit public JSON API (${resolvedItems.count { it.isDownloadable }} verified downloadable).",
                nextPageCursor = nextAfter,
            )
        } else null

        return UniversalExtractionResult(
            inputQuery = target.normalizedInput,
            resolvedFinalUrl = target.resolvedUrl,
            platform = SupportedPlatform.REDDIT,
            classification = target.initialClassification,
            profile = profile,
            items = resolvedItems,
            nextPageCursor = nextAfter,
            statusBanner = if (resolvedItems.isNotEmpty()) {
                "Found ${resolvedItems.size} Reddit media/post entries (${resolvedItems.count { it.isDownloadable }} verified downloadable)."
            } else {
                "No downloadable media attachments found in this Reddit target."
            },
        )
    }

    private fun extractPostMedia(
        data: JSONObject,
        out: MutableList<ResolvedMediaItem>,
        initialBudget: Int,
    ): Int {
        var budget = initialBudget
        val title = data.optString("title", "Reddit Post")
        val author = data.optString("author", "redditor").let { "u/$it" }
        val subreddit = data.optString("subreddit_name_prefixed", "")
        val permalink = "https://www.reddit.com${data.optString("permalink", "")}"
        val thumb = data.optString("thumbnail")
            .takeIf { UrlSecurityValidator.validateSyntaxAndHost(it, securityPolicy).isValid }
        val destUrl = data.optString("url_overridden_by_dest").ifBlank { data.optString("url") }
            .replace("&amp;", "&")
        val postHint = data.optString("post_hint", "")
        val id = data.optString("id", title.hashCode().toString())

        val redditVideo = data.optJSONObject("secure_media")?.optJSONObject("reddit_video")
            ?: data.optJSONObject("media")?.optJSONObject("reddit_video")
        if (redditVideo != null) {
            val fallbackMp4 = redditVideo.optString("fallback_url").substringBefore('?')
            val durationSec = redditVideo.optInt("duration", 0)
            if (UrlSecurityValidator.validateSyntaxAndHost(fallbackMp4, securityPolicy).isValid && budget > 0) {
                budget--
                val probe = MediaMetadataExtractor.probeMediaTarget(fallbackMp4, securityPolicy)
                val eligibility = probe.verdict.toEligibility()
                val verified = eligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE
                out.add(
                    ResolvedMediaItem(
                        id = "rd_vid_$id",
                        platform = SupportedPlatform.REDDIT,
                        kind = if (verified) MediaKind.DIRECT_MEDIA else MediaKind.POST,
                        eligibility = eligibility,
                        accessStatus = if (verified) {
                            "Verified v.redd.it video stream ($subreddit)"
                        } else {
                            probe.errorDetail ?: "Video verification failed"
                        },
                        title = title,
                        creator = "$author • $subreddit",
                        thumbnailUrl = thumb,
                        originalUrl = permalink,
                        downloadUrl = if (verified) probe.finalUrl else null,
                        mimeHint = probe.mimeType.ifBlank { "video/mp4" },
                        formatBadge = "MP4",
                        durationLabel = if (durationSec > 0) "${durationSec}s" else null,
                        fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(probe.contentLength),
                    )
                )
                return budget
            }
        }

        val isDirectImgOrVideoCandidate = (postHint == "image" || postHint == "rich:video" ||
            destUrl.endsWith(".jpg", true) ||
            destUrl.endsWith(".jpeg", true) ||
            destUrl.endsWith(".png", true) ||
            destUrl.endsWith(".gif", true) ||
            destUrl.endsWith(".webp", true) ||
            destUrl.endsWith(".mp4", true)) &&
            UrlSecurityValidator.validateSyntaxAndHost(destUrl, securityPolicy).isValid

        if (isDirectImgOrVideoCandidate && budget > 0) {
            budget--
            val probe = MediaMetadataExtractor.probeMediaTarget(destUrl, securityPolicy)
            val eligibility = probe.verdict.toEligibility()
            val verified = eligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE
            val ext = (probe.dispositionFileName ?: probe.finalUrl)
                .substringBefore('?').substringAfterLast('.', "FILE").uppercase().take(5)
            out.add(
                ResolvedMediaItem(
                    id = "rd_img_$id",
                    platform = SupportedPlatform.REDDIT,
                    kind = if (verified) MediaKind.DIRECT_MEDIA else MediaKind.WEBPAGE,
                    eligibility = eligibility,
                    accessStatus = if (verified) {
                        "Verified Reddit media (${probe.mimeType})"
                    } else {
                        probe.errorDetail ?: "Not directly downloadable"
                    },
                    title = title,
                    creator = "$author • $subreddit",
                    thumbnailUrl = if (verified && probe.mimeType.startsWith("image/")) probe.finalUrl else thumb,
                    originalUrl = permalink,
                    downloadUrl = if (verified) probe.finalUrl else null,
                    mimeHint = probe.mimeType.ifBlank { "text/html" },
                    formatBadge = ext,
                    fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(probe.contentLength),
                )
            )
            return budget
        }

        val mediaMeta = data.optJSONObject("media_metadata")
        if (mediaMeta != null) {
            val keys = mediaMeta.keys()
            var idx = 0
            while (keys.hasNext() && budget > 0) {
                val k = keys.next()
                val rawImg = mediaMeta.optJSONObject(k)?.optJSONObject("s")?.optString("u")?.replace("&amp;", "&")
                if (!rawImg.isNullOrBlank() && UrlSecurityValidator.validateSyntaxAndHost(rawImg, securityPolicy).isValid) {
                    idx++
                    budget--
                    val probe = MediaMetadataExtractor.probeMediaTarget(rawImg, securityPolicy)
                    val eligibility = probe.verdict.toEligibility()
                    val verified = eligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE
                    out.add(
                        ResolvedMediaItem(
                            id = "rd_gal_${id}_$idx",
                            platform = SupportedPlatform.REDDIT,
                            kind = if (verified) MediaKind.DIRECT_MEDIA else MediaKind.POST,
                            eligibility = eligibility,
                            accessStatus = if (verified) "Verified gallery item #$idx (${probe.mimeType})" else (probe.errorDetail ?: "Verification failed"),
                            title = "$title (#$idx)",
                            creator = "$author • $subreddit",
                            thumbnailUrl = if (verified) probe.finalUrl else thumb,
                            originalUrl = permalink,
                            downloadUrl = if (verified) probe.finalUrl else null,
                            mimeHint = probe.mimeType.ifBlank { "image/jpeg" },
                            formatBadge = "GALLERY",
                            fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(probe.contentLength),
                        )
                    )
                }
            }
            if (idx > 0) return budget
        }

        out.add(
            ResolvedMediaItem(
                id = "rd_post_$id",
                platform = SupportedPlatform.REDDIT,
                kind = MediaKind.POST,
                eligibility = DownloadEligibility.PREVIEW_ONLY,
                accessStatus = "Text or external link post ($subreddit)",
                title = title,
                creator = "$author • $subreddit",
                thumbnailUrl = thumb,
                originalUrl = permalink,
                downloadUrl = null,
                mimeHint = "text/html",
                formatBadge = "POST",
            )
        )
        return budget
    }
}

class SocialOpenGraphPlatformAdapter(
    override val platform: SupportedPlatform,
    private val oEmbedEndpoint: String? = null,
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) : PlatformAdapter {

    override fun extract(target: DetectedTarget, pageCursor: String?): UniversalExtractionResult {
        val url = target.resolvedUrl
        val oembed = oEmbedEndpoint?.let { MediaMetadataExtractor.fetchOEmbed(it, url, securityPolicy) }
        val og = MediaMetadataExtractor.fetchOpenGraphMetadata(url, securityPolicy)

        if (og == null) {
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = url,
                platform = platform,
                classification = target.initialClassification,
                items = listOf(
                    ResolvedMediaItem(
                        id = "${platform.id}_fail_${url.hashCode()}",
                        platform = platform,
                        kind = target.initialClassification,
                        eligibility = DownloadEligibility.VERIFICATION_FAILED,
                        accessStatus = "Verification failed — endpoint unreachable or blocked.",
                        title = "${platform.label} Target",
                        creator = target.handle ?: platform.label,
                        thumbnailUrl = null,
                        originalUrl = url,
                        downloadUrl = null,
                        mimeHint = "",
                        formatBadge = "FAILED",
                    )
                ),
                statusBanner = "Endpoint unreachable or blocked from device.",
            )
        }

        if (og.httpCode in setOf(401, 403, 407)) {
            val authMsg = "Authentication required (HTTP ${og.httpCode}). Open in Prism Browser while signed in."
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = og.finalUrl,
                platform = platform,
                classification = target.initialClassification,
                items = listOf(
                    ResolvedMediaItem(
                        id = "${platform.id}_auth_${og.finalUrl.hashCode()}",
                        platform = platform,
                        kind = target.initialClassification,
                        eligibility = DownloadEligibility.REQUIRES_AUTHENTICATION,
                        accessStatus = authMsg,
                        title = og.title.ifBlank { "${platform.label} Protected Content" },
                        creator = og.author ?: target.handle ?: platform.label,
                        thumbnailUrl = null,
                        originalUrl = og.finalUrl,
                        downloadUrl = null,
                        mimeHint = og.contentType.ifBlank { "text/html" },
                        formatBadge = "AUTH ${og.httpCode}",
                    )
                ),
                statusBanner = authMsg,
            )
        }

        if (og.httpCode !in 200..299) {
            val errMsg = "Verification failed with HTTP ${og.httpCode}."
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = og.finalUrl,
                platform = platform,
                classification = target.initialClassification,
                items = listOf(
                    ResolvedMediaItem(
                        id = "${platform.id}_err_${og.finalUrl.hashCode()}",
                        platform = platform,
                        kind = target.initialClassification,
                        eligibility = DownloadEligibility.VERIFICATION_FAILED,
                        accessStatus = errMsg,
                        title = og.title,
                        creator = og.author ?: target.handle ?: platform.label,
                        thumbnailUrl = null,
                        originalUrl = og.finalUrl,
                        downloadUrl = null,
                        mimeHint = og.contentType,
                        formatBadge = "HTTP ${og.httpCode}",
                    )
                ),
                statusBanner = errMsg,
            )
        }

        val items = mutableListOf<ResolvedMediaItem>()
        val title = oembed?.optString("title")?.takeIf { it.isNotBlank() }
            ?: og.title.takeIf { it.isNotBlank() }
            ?: "${platform.label} ${target.initialClassification.label}"
        val creator = oembed?.optString("author_name")?.takeIf { it.isNotBlank() }
            ?: og.author?.takeIf { it.isNotBlank() }
            ?: target.handle ?: platform.label
        val rawImageUrl = oembed?.optString("thumbnail_url")
            ?.takeIf { UrlSecurityValidator.validateSyntaxAndHost(it, securityPolicy).isValid }
            ?: og.imageUrl
        val rawVideoUrl = og.videoUrl

        // Verify og:image first so verified thumbnail can be reused safely on video preview cards
        val imageProbe = rawImageUrl?.let { MediaMetadataExtractor.probeMediaTarget(it, securityPolicy) }
        val verifiedThumbUrl = imageProbe
            ?.takeIf { it.isVerifiedDownloadable && it.mimeType.startsWith("image/") }
            ?.finalUrl

        if (!rawVideoUrl.isNullOrBlank()) {
            val videoProbe = MediaMetadataExtractor.probeMediaTarget(rawVideoUrl, securityPolicy)
            val eligibility = videoProbe.verdict.toEligibility()
            val isVerifiedMedia = eligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE
            items.add(
                ResolvedMediaItem(
                    id = "${platform.id}_vid_${rawVideoUrl.hashCode()}",
                    platform = platform,
                    kind = if (isVerifiedMedia) MediaKind.DIRECT_MEDIA else MediaKind.EMBEDDED_MEDIA,
                    eligibility = eligibility,
                    accessStatus = when (eligibility) {
                        DownloadEligibility.VERIFIED_DOWNLOADABLE -> "Verified stream (${videoProbe.mimeType})"
                        DownloadEligibility.PREVIEW_ONLY -> "Embedded HTML player — open in browser"
                        DownloadEligibility.REQUIRES_AUTHENTICATION -> "Video stream requires authentication (HTTP ${videoProbe.httpCode})"
                        DownloadEligibility.UNSUPPORTED -> videoProbe.errorDetail ?: "Unsupported video format"
                        DownloadEligibility.VERIFICATION_FAILED -> videoProbe.errorDetail ?: "Video target verification failed"
                    },
                    title = title,
                    creator = creator,
                    thumbnailUrl = verifiedThumbUrl,
                    originalUrl = og.finalUrl,
                    downloadUrl = if (isVerifiedMedia) videoProbe.finalUrl else null,
                    mimeHint = videoProbe.mimeType.ifBlank { "text/html" },
                    formatBadge = "VIDEO",
                    fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(videoProbe.contentLength),
                )
            )
        }

        if (imageProbe != null) {
            val isVerifiedImage = imageProbe.isVerifiedDownloadable && imageProbe.mimeType.startsWith("image/")
            val imgEligibility = if (isVerifiedImage) {
                DownloadEligibility.VERIFIED_DOWNLOADABLE
            } else if (imageProbe.verdict == ProbeVerdict.VERIFIED_MEDIA) {
                DownloadEligibility.UNSUPPORTED
            } else {
                imageProbe.verdict.toEligibility()
            }
            items.add(
                ResolvedMediaItem(
                    id = "${platform.id}_img_${rawImageUrl.hashCode()}",
                    platform = platform,
                    kind = if (target.initialClassification == MediaKind.PROFILE) MediaKind.THUMBNAIL else MediaKind.THUMBNAIL,
                    eligibility = imgEligibility,
                    accessStatus = if (isVerifiedImage) {
                        "Verified preview image (${imageProbe.mimeType})"
                    } else {
                        imageProbe.errorDetail ?: "Preview image could not be verified as direct media"
                    },
                    title = if (target.initialClassification == MediaKind.PROFILE) "$title — Profile Thumbnail" else "$title — Cover Image",
                    creator = creator,
                    thumbnailUrl = if (isVerifiedImage) imageProbe.finalUrl else null,
                    originalUrl = og.finalUrl,
                    downloadUrl = if (isVerifiedImage) imageProbe.finalUrl else null,
                    mimeHint = imageProbe.mimeType.ifBlank { "text/html" },
                    formatBadge = if (target.initialClassification == MediaKind.PROFILE) "AVATAR" else "THUMB",
                    fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(imageProbe.contentLength),
                )
            )
        }

        val banner = if (items.isEmpty()) {
            "No public direct media streams exposed without login. Open original page in Prism Browser."
        } else {
            platform.capabilities.accessNote
        }

        if (items.isEmpty()) {
            items.add(
                ResolvedMediaItem(
                    id = "${platform.id}_page_${og.finalUrl.hashCode()}",
                    platform = platform,
                    kind = target.initialClassification,
                    eligibility = DownloadEligibility.PREVIEW_ONLY,
                    accessStatus = banner,
                    title = title,
                    creator = creator,
                    thumbnailUrl = null,
                    originalUrl = og.finalUrl,
                    downloadUrl = null,
                    mimeHint = "text/html",
                    formatBadge = target.initialClassification.label.uppercase(),
                )
            )
        }

        val profile = if (target.initialClassification == MediaKind.PROFILE || target.isHandleOnly) {
            CreatorProfileSummary(
                platform = platform,
                handle = target.handle ?: creator,
                displayName = title,
                avatarUrl = verifiedThumbUrl,
                profileUrl = og.finalUrl,
                retrievedCount = items.size,
                isPartialSubset = true,
                limitationNotice = banner,
            )
        } else null

        return UniversalExtractionResult(
            inputQuery = target.normalizedInput,
            resolvedFinalUrl = og.finalUrl,
            platform = platform,
            classification = target.initialClassification,
            profile = profile,
            items = items,
            statusBanner = banner,
        )
    }
}

class DirectMediaAdapter(
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) : PlatformAdapter {
    override val platform = SupportedPlatform.DIRECT_MEDIA

    override fun extract(target: DetectedTarget, pageCursor: String?): UniversalExtractionResult {
        val probe = MediaMetadataExtractor.probeMediaTarget(target.resolvedUrl, securityPolicy)
        return buildFromProbe(target, probe)
    }

    internal fun buildFromProbe(
        target: DetectedTarget,
        probe: MediaProbeResult,
    ): UniversalExtractionResult {
        if (probe.verdict == ProbeVerdict.HTML_PAGE) {
            return WebGenericAdapter(securityPolicy).extractHtmlPage(
                target = target.copy(platform = SupportedPlatform.WEB_GENERIC, resolvedUrl = probe.finalUrl),
            )
        }

        val parsedUrl = probe.finalUrl.toHttpUrlOrNull()
        val urlFileName = parsedUrl?.encodedPath?.substringAfterLast('/', "")?.takeIf { it.isNotBlank() }
        val fileName = probe.dispositionFileName?.takeIf { it.isNotBlank() }
            ?: urlFileName
            ?: "direct_asset"
        val hostLabel = parsedUrl?.host ?: "Direct Host"

        val eligibility = probe.verdict.toEligibility()
        val isVerified = eligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE
        val ext = fileName.substringAfterLast('.', "").uppercase().take(6).ifBlank {
            probe.mimeType.substringAfter('/', "FILE").uppercase().take(6)
        }

        val status = when (eligibility) {
            DownloadEligibility.VERIFIED_DOWNLOADABLE -> buildString {
                append("Verified ${probe.mimeType}")
                if (probe.acceptsRanges) append(" • Multi-chunk Range OK")
                if (probe.usedRangeFallback) append(" • GET Range verified")
            }
            DownloadEligibility.REQUIRES_AUTHENTICATION ->
                "Authorization required (HTTP ${probe.httpCode}). Open in Browser to sign in."
            DownloadEligibility.UNSUPPORTED ->
                probe.errorDetail ?: "Unsupported content type (${probe.mimeType})"
            DownloadEligibility.VERIFICATION_FAILED ->
                probe.errorDetail ?: "Could not verify remote file (HTTP ${probe.httpCode})"
            DownloadEligibility.PREVIEW_ONLY ->
                "Webpage preview only — not directly downloadable"
        }

        val item = ResolvedMediaItem(
            id = "direct_${probe.finalUrl.hashCode()}",
            platform = SupportedPlatform.DIRECT_MEDIA,
            kind = if (isVerified) MediaKind.DIRECT_MEDIA else MediaKind.UNSUPPORTED,
            eligibility = eligibility,
            accessStatus = status,
            title = fileName,
            creator = hostLabel,
            thumbnailUrl = if (isVerified && probe.mimeType.startsWith("image/")) probe.finalUrl else null,
            originalUrl = probe.finalUrl,
            downloadUrl = if (isVerified) probe.finalUrl else null,
            mimeHint = probe.mimeType.ifBlank { "application/octet-stream" },
            formatBadge = ext,
            fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(probe.contentLength),
        )

        return UniversalExtractionResult(
            inputQuery = target.normalizedInput,
            resolvedFinalUrl = probe.finalUrl,
            platform = SupportedPlatform.DIRECT_MEDIA,
            classification = if (isVerified) MediaKind.DIRECT_MEDIA else MediaKind.UNSUPPORTED,
            items = listOf(item),
            statusBanner = status,
        )
    }
}

class WebGenericAdapter(
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) : PlatformAdapter {
    override val platform = SupportedPlatform.WEB_GENERIC

    override fun extract(target: DetectedTarget, pageCursor: String?): UniversalExtractionResult {
        val probe = MediaMetadataExtractor.probeMediaTarget(target.resolvedUrl, securityPolicy)

        return when (probe.verdict) {
            ProbeVerdict.VERIFIED_MEDIA -> {
                DirectMediaAdapter(securityPolicy).buildFromProbe(
                    target = target.copy(platform = SupportedPlatform.DIRECT_MEDIA),
                    probe = probe,
                )
            }
            ProbeVerdict.REQUIRES_AUTH,
            ProbeVerdict.UNSUPPORTED_TYPE,
            ProbeVerdict.VERIFICATION_FAILED -> {
                val host = probe.finalUrl.toHttpUrlOrNull()?.host ?: "Website"
                val eligibility = probe.verdict.toEligibility()
                val msg = probe.errorDetail ?: "Unable to verify target (${eligibility.label})"
                UniversalExtractionResult(
                    inputQuery = target.normalizedInput,
                    resolvedFinalUrl = probe.finalUrl,
                    platform = SupportedPlatform.WEB_GENERIC,
                    classification = MediaKind.WEBPAGE,
                    items = listOf(
                        ResolvedMediaItem(
                            id = "web_status_${probe.finalUrl.hashCode()}",
                            platform = SupportedPlatform.WEB_GENERIC,
                            kind = MediaKind.WEBPAGE,
                            eligibility = eligibility,
                            accessStatus = msg,
                            title = host,
                            creator = host,
                            thumbnailUrl = null,
                            originalUrl = probe.finalUrl,
                            downloadUrl = null,
                            mimeHint = probe.mimeType,
                            formatBadge = eligibility.label.uppercase().take(8),
                        )
                    ),
                    statusBanner = msg,
                )
            }
            ProbeVerdict.HTML_PAGE -> extractHtmlPage(target.copy(resolvedUrl = probe.finalUrl))
        }
    }

    internal fun extractHtmlPage(target: DetectedTarget): UniversalExtractionResult {
        val og = MediaMetadataExtractor.fetchOpenGraphMetadata(target.resolvedUrl, securityPolicy)
        val resolvedPageUrl = og?.finalUrl ?: target.resolvedUrl
        val host = resolvedPageUrl.toHttpUrlOrNull()?.host ?: "Website"

        if (og == null) {
            val msg = "Failed to fetch webpage metadata."
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = resolvedPageUrl,
                platform = SupportedPlatform.WEB_GENERIC,
                classification = MediaKind.WEBPAGE,
                items = listOf(
                    ResolvedMediaItem(
                        id = "web_fail_${resolvedPageUrl.hashCode()}",
                        platform = SupportedPlatform.WEB_GENERIC,
                        kind = MediaKind.WEBPAGE,
                        eligibility = DownloadEligibility.VERIFICATION_FAILED,
                        accessStatus = msg,
                        title = host,
                        creator = host,
                        thumbnailUrl = null,
                        originalUrl = resolvedPageUrl,
                        downloadUrl = null,
                        mimeHint = "text/html",
                        formatBadge = "FAILED",
                    )
                ),
                statusBanner = msg,
            )
        }

        if (og.httpCode in setOf(401, 403, 407)) {
            val msg = "Authentication required (HTTP ${og.httpCode}). Open page in Prism Browser to sign in."
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = resolvedPageUrl,
                platform = SupportedPlatform.WEB_GENERIC,
                classification = MediaKind.WEBPAGE,
                items = listOf(
                    ResolvedMediaItem(
                        id = "web_auth_${resolvedPageUrl.hashCode()}",
                        platform = SupportedPlatform.WEB_GENERIC,
                        kind = MediaKind.WEBPAGE,
                        eligibility = DownloadEligibility.REQUIRES_AUTHENTICATION,
                        accessStatus = msg,
                        title = og.title,
                        creator = host,
                        thumbnailUrl = null,
                        originalUrl = resolvedPageUrl,
                        downloadUrl = null,
                        mimeHint = og.contentType.ifBlank { "text/html" },
                        formatBadge = "AUTH ${og.httpCode}",
                    )
                ),
                statusBanner = msg,
            )
        }

        if (og.httpCode !in 200..299) {
            val msg = "Webpage verification failed (HTTP ${og.httpCode})."
            return UniversalExtractionResult(
                inputQuery = target.normalizedInput,
                resolvedFinalUrl = resolvedPageUrl,
                platform = SupportedPlatform.WEB_GENERIC,
                classification = MediaKind.WEBPAGE,
                items = listOf(
                    ResolvedMediaItem(
                        id = "web_err_${resolvedPageUrl.hashCode()}",
                        platform = SupportedPlatform.WEB_GENERIC,
                        kind = MediaKind.WEBPAGE,
                        eligibility = DownloadEligibility.VERIFICATION_FAILED,
                        accessStatus = msg,
                        title = og.title,
                        creator = host,
                        thumbnailUrl = null,
                        originalUrl = resolvedPageUrl,
                        downloadUrl = null,
                        mimeHint = og.contentType,
                        formatBadge = "HTTP ${og.httpCode}",
                    )
                ),
                statusBanner = msg,
            )
        }

        val items = mutableListOf<ResolvedMediaItem>()
        val title = og.title

        // Verify og:image before trusting it as downloadable or preview thumbnail
        val imageProbe = og.imageUrl?.let { MediaMetadataExtractor.probeMediaTarget(it, securityPolicy) }
        val verifiedThumbUrl = imageProbe
            ?.takeIf { it.isVerifiedDownloadable && it.mimeType.startsWith("image/") }
            ?.finalUrl

        og.videoUrl?.let { vUrl ->
            val vProbe = MediaMetadataExtractor.probeMediaTarget(vUrl, securityPolicy)
            val vEligibility = vProbe.verdict.toEligibility()
            val vVerified = vEligibility == DownloadEligibility.VERIFIED_DOWNLOADABLE
            items.add(
                ResolvedMediaItem(
                    id = "web_vid_${vUrl.hashCode()}",
                    platform = SupportedPlatform.WEB_GENERIC,
                    kind = if (vVerified) MediaKind.DIRECT_MEDIA else MediaKind.EMBEDDED_MEDIA,
                    eligibility = vEligibility,
                    accessStatus = when (vEligibility) {
                        DownloadEligibility.VERIFIED_DOWNLOADABLE -> "Verified video asset (${vProbe.mimeType})"
                        DownloadEligibility.PREVIEW_ONLY -> "Embedded video player — webpage preview only"
                        DownloadEligibility.REQUIRES_AUTHENTICATION -> "Video stream requires authentication (HTTP ${vProbe.httpCode})"
                        DownloadEligibility.UNSUPPORTED -> vProbe.errorDetail ?: "Unsupported video target"
                        DownloadEligibility.VERIFICATION_FAILED -> vProbe.errorDetail ?: "Video verification failed"
                    },
                    title = "$title — Video",
                    creator = host,
                    thumbnailUrl = verifiedThumbUrl,
                    originalUrl = resolvedPageUrl,
                    downloadUrl = if (vVerified) vProbe.finalUrl else null,
                    mimeHint = vProbe.mimeType.ifBlank { "text/html" },
                    formatBadge = "VIDEO",
                    fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(vProbe.contentLength),
                )
            )
        }

        if (imageProbe != null) {
            val isVerifiedImage = imageProbe.isVerifiedDownloadable && imageProbe.mimeType.startsWith("image/")
            val imgEligibility = if (isVerifiedImage) {
                DownloadEligibility.VERIFIED_DOWNLOADABLE
            } else if (imageProbe.verdict == ProbeVerdict.VERIFIED_MEDIA) {
                DownloadEligibility.UNSUPPORTED
            } else {
                imageProbe.verdict.toEligibility()
            }
            items.add(
                ResolvedMediaItem(
                    id = "web_img_${og.imageUrl.hashCode()}",
                    platform = SupportedPlatform.WEB_GENERIC,
                    kind = MediaKind.THUMBNAIL,
                    eligibility = imgEligibility,
                    accessStatus = if (isVerifiedImage) {
                        "Verified webpage cover image (${imageProbe.mimeType})"
                    } else {
                        imageProbe.errorDetail ?: "OpenGraph image is not directly downloadable"
                    },
                    title = "$title — Cover Thumbnail",
                    creator = host,
                    thumbnailUrl = if (isVerifiedImage) imageProbe.finalUrl else null,
                    originalUrl = resolvedPageUrl,
                    downloadUrl = if (isVerifiedImage) imageProbe.finalUrl else null,
                    mimeHint = imageProbe.mimeType.ifBlank { "text/html" },
                    formatBadge = "THUMB",
                    fileSizeLabel = MediaMetadataExtractor.formatBytesLabel(imageProbe.contentLength),
                )
            )
        }

        if (items.isEmpty()) {
            items.add(
                ResolvedMediaItem(
                    id = "web_page_${resolvedPageUrl.hashCode()}",
                    platform = SupportedPlatform.WEB_GENERIC,
                    kind = MediaKind.WEBPAGE,
                    eligibility = DownloadEligibility.PREVIEW_ONLY,
                    accessStatus = "HTML webpage preview — open in Browser Media Sniffer for dynamic streams",
                    title = title,
                    creator = host,
                    thumbnailUrl = null,
                    originalUrl = resolvedPageUrl,
                    downloadUrl = null,
                    mimeHint = "text/html",
                    formatBadge = "WEBPAGE",
                )
            )
        }

        return UniversalExtractionResult(
            inputQuery = target.normalizedInput,
            resolvedFinalUrl = resolvedPageUrl,
            platform = SupportedPlatform.WEB_GENERIC,
            classification = MediaKind.WEBPAGE,
            items = items,
            statusBanner = "Inspected webpage ($host) — ${items.count { it.isDownloadable }} verified downloadable asset(s).",
        )
    }
}
