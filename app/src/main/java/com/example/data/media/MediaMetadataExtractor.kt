package com.example.data.media

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

enum class ProbeVerdict {
    VERIFIED_MEDIA,
    HTML_PAGE,
    REQUIRES_AUTH,
    UNSUPPORTED_TYPE,
    VERIFICATION_FAILED,
}

data class OpenGraphResult(
    val finalUrl: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val videoUrl: String?,
    val author: String?,
    val channelId: String?,
    val httpCode: Int,
    val contentType: String = "",
)

data class MediaProbeResult(
    val requestedUrl: String,
    val finalUrl: String,
    val httpCode: Int,
    val mimeType: String,
    val contentLength: Long,
    val acceptsRanges: Boolean,
    val contentDisposition: String?,
    val dispositionFileName: String?,
    val isDispositionAttachment: Boolean,
    val isHtml: Boolean,
    val verdict: ProbeVerdict,
    val usedRangeFallback: Boolean = false,
    val errorDetail: String? = null,
) {
    val isVerifiedDownloadable: Boolean
        get() = verdict == ProbeVerdict.VERIFIED_MEDIA
}

object MediaMetadataExtractor {

    private const val SNIFF_LIMIT_BYTES = 1024
    private const val MAX_HTML_READ_BYTES = 262_144 // 256 KB bounded HTML cap

    private val unsupportedExactMimes = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "application/dash+xml",
        "application/javascript",
        "text/javascript",
        "application/ecmascript",
        "text/css",
        "application/wasm",
        "application/x-msdownload",
        "application/x-dosexec",
        "application/x-executable",
        "application/x-sh",
        "application/x-httpd-php",
        "application/x-shockwave-flash",
    )

    private val blockedExtensions = setOf(
        "html", "htm", "xhtml", "php", "asp", "aspx", "jsp",
        "js", "mjs", "css", "wasm", "m3u8", "mpd",
        "exe", "dll", "bat", "cmd", "msi", "ps1", "vbs", "sh",
    )

    private val supportedDocumentMimes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/rtf",
        "application/epub+zip",
        "text/plain",
        "text/csv",
        "text/markdown",
        "application/json",
    )

    private val supportedArchiveMimes = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-tar",
        "application/gzip",
        "application/x-gzip",
        "application/x-7z-compressed",
        "application/vnd.rar",
        "application/x-rar-compressed",
        "application/x-bzip2",
        "application/x-xz",
        "application/vnd.android.package-archive",
    )

    fun probeMediaTarget(
        url: String,
        securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): MediaProbeResult {
        when (val check = UrlSecurityValidator.validateSyntaxAndHost(url, securityPolicy)) {
            is UrlValidationResult.Rejected -> {
                return MediaProbeResult(
                    requestedUrl = url,
                    finalUrl = url,
                    httpCode = 0,
                    mimeType = "",
                    contentLength = -1L,
                    acceptsRanges = false,
                    contentDisposition = null,
                    dispositionFileName = null,
                    isDispositionAttachment = false,
                    isHtml = false,
                    verdict = ProbeVerdict.VERIFICATION_FAILED,
                    errorDetail = check.reason,
                )
            }
            is UrlValidationResult.Valid -> Unit
        }

        val client = UrlSecurityValidator.createSafeHttpClient(securityPolicy)

        var headCode = 0
        var headFinalUrl = url
        var headMime = ""
        var headLength = -1L
        var headAcceptRanges = false
        var headDisposition: String? = null
        var headNeedsGetFallback = false
        var headSecurityError: String? = null

        try {
            val headReq = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .head()
                .build()
            UrlSecurityValidator.executeWithSafeRedirects(client, headReq, securityPolicy).use { resp ->
                headCode = resp.code
                headFinalUrl = resp.request.url.toString()
                headMime = extractCleanMime(resp)
                headLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                headAcceptRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                headDisposition = resp.header("Content-Disposition")

                headNeedsGetFallback = when {
                    headCode == 400 || headCode == 403 || headCode == 405 || headCode == 406 ||
                        headCode == 415 || headCode == 501 -> true
                    resp.isSuccessful && (headMime.isBlank() || isGenericBinaryMime(headMime)) -> true
                    else -> false
                }
            }
        } catch (sec: SecurityException) {
            headSecurityError = sec.message ?: "Blocked unsafe URL or redirect."
        } catch (_: Exception) {
            // HEAD rejected at transport/protocol level -> attempt bounded GET Range fallback
            headNeedsGetFallback = true
        }

        if (headSecurityError != null) {
            return MediaProbeResult(
                requestedUrl = url,
                finalUrl = url,
                httpCode = 0,
                mimeType = "",
                contentLength = -1L,
                acceptsRanges = false,
                contentDisposition = null,
                dispositionFileName = null,
                isDispositionAttachment = false,
                isHtml = false,
                verdict = ProbeVerdict.VERIFICATION_FAILED,
                errorDetail = headSecurityError,
            )
        }

        if (!headNeedsGetFallback && headCode in 200..299) {
            return evaluateResponse(
                requestedUrl = url,
                finalUrl = headFinalUrl,
                httpCode = headCode,
                rawMime = headMime,
                contentLength = headLength,
                acceptsRanges = headAcceptRanges,
                disposition = headDisposition,
                sniffedBytes = ByteArray(0),
                usedRangeFallback = false,
            )
        }

        if (!headNeedsGetFallback && headCode != 0) {
            return evaluateResponse(
                requestedUrl = url,
                finalUrl = headFinalUrl,
                httpCode = headCode,
                rawMime = headMime,
                contentLength = headLength,
                acceptsRanges = headAcceptRanges,
                disposition = headDisposition,
                sniffedBytes = ByteArray(0),
                usedRangeFallback = false,
            )
        }

        // Bounded GET with Range: bytes=0-1023 fallback
        return try {
            val getReq = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .header("Range", "bytes=0-${SNIFF_LIMIT_BYTES - 1}")
                .get()
                .build()
            UrlSecurityValidator.executeWithSafeRedirects(client, getReq, securityPolicy).use { resp ->
                val code = resp.code
                val finalUrl = resp.request.url.toString()
                val rawMime = extractCleanMime(resp)
                val contentRangeTotal = parseContentRangeTotal(resp.header("Content-Range"))
                val headerLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val effectiveLength = when {
                    contentRangeTotal > 0L -> contentRangeTotal
                    code == 200 && headerLen >= 0L -> headerLen
                    headLength > 0L -> headLength
                    else -> -1L
                }
                val acceptRanges = code == 206 ||
                    resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true ||
                    headAcceptRanges
                val disposition = resp.header("Content-Disposition") ?: headDisposition
                val sniffed = if (code in 200..299) readBoundedBytes(resp, SNIFF_LIMIT_BYTES) else ByteArray(0)

                evaluateResponse(
                    requestedUrl = url,
                    finalUrl = finalUrl,
                    httpCode = code,
                    rawMime = rawMime,
                    contentLength = effectiveLength,
                    acceptsRanges = acceptRanges,
                    disposition = disposition,
                    sniffedBytes = sniffed,
                    usedRangeFallback = true,
                )
            }
        } catch (sec: SecurityException) {
            MediaProbeResult(
                requestedUrl = url,
                finalUrl = url,
                httpCode = 0,
                mimeType = "",
                contentLength = -1L,
                acceptsRanges = false,
                contentDisposition = null,
                dispositionFileName = null,
                isDispositionAttachment = false,
                isHtml = false,
                verdict = ProbeVerdict.VERIFICATION_FAILED,
                usedRangeFallback = true,
                errorDetail = sec.message ?: "Blocked unsafe redirect.",
            )
        } catch (e: Exception) {
            MediaProbeResult(
                requestedUrl = url,
                finalUrl = headFinalUrl,
                httpCode = headCode,
                mimeType = headMime,
                contentLength = headLength,
                acceptsRanges = headAcceptRanges,
                contentDisposition = headDisposition,
                dispositionFileName = null,
                isDispositionAttachment = false,
                isHtml = false,
                verdict = ProbeVerdict.VERIFICATION_FAILED,
                usedRangeFallback = true,
                errorDetail = e.localizedMessage ?: "Verification request failed",
            )
        }
    }

    private fun evaluateResponse(
        requestedUrl: String,
        finalUrl: String,
        httpCode: Int,
        rawMime: String,
        contentLength: Long,
        acceptsRanges: Boolean,
        disposition: String?,
        sniffedBytes: ByteArray,
        usedRangeFallback: Boolean,
    ): MediaProbeResult {
        val dispInfo = parseContentDisposition(disposition)

        if (httpCode == 401 || httpCode == 403 || httpCode == 407) {
            return MediaProbeResult(
                requestedUrl = requestedUrl,
                finalUrl = finalUrl,
                httpCode = httpCode,
                mimeType = rawMime,
                contentLength = contentLength,
                acceptsRanges = acceptsRanges,
                contentDisposition = disposition,
                dispositionFileName = dispInfo.fileName,
                isDispositionAttachment = dispInfo.isAttachment,
                isHtml = false,
                verdict = ProbeVerdict.REQUIRES_AUTH,
                usedRangeFallback = usedRangeFallback,
                errorDetail = "HTTP $httpCode authorization required",
            )
        }

        if (httpCode !in 200..299) {
            return MediaProbeResult(
                requestedUrl = requestedUrl,
                finalUrl = finalUrl,
                httpCode = httpCode,
                mimeType = rawMime,
                contentLength = contentLength,
                acceptsRanges = acceptsRanges,
                contentDisposition = disposition,
                dispositionFileName = dispInfo.fileName,
                isDispositionAttachment = dispInfo.isAttachment,
                isHtml = false,
                verdict = ProbeVerdict.VERIFICATION_FAILED,
                usedRangeFallback = usedRangeFallback,
                errorDetail = if (httpCode > 0) "HTTP $httpCode response" else "No valid HTTP response",
            )
        }

        val sniffedHtml = looksLikeHtml(sniffedBytes)
        val isHtml = rawMime == "text/html" || rawMime == "application/xhtml+xml" || sniffedHtml
        if (isHtml) {
            return MediaProbeResult(
                requestedUrl = requestedUrl,
                finalUrl = finalUrl,
                httpCode = httpCode,
                mimeType = if (rawMime.isNotBlank()) rawMime else "text/html",
                contentLength = contentLength,
                acceptsRanges = acceptsRanges,
                contentDisposition = disposition,
                dispositionFileName = dispInfo.fileName,
                isDispositionAttachment = dispInfo.isAttachment,
                isHtml = true,
                verdict = ProbeVerdict.HTML_PAGE,
                usedRangeFallback = usedRangeFallback,
            )
        }

        val magicMime = sniffMagicMime(sniffedBytes)
        val effectiveMime = when {
            rawMime.isNotBlank() && !isGenericBinaryMime(rawMime) -> rawMime
            magicMime != null -> magicMime
            rawMime.isNotBlank() -> rawMime
            else -> ""
        }

        val targetExt = (dispInfo.fileName ?: finalUrl)
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/', dispInfo.fileName ?: finalUrl)
            .substringAfterLast('.', "")
            .lowercase()

        if (effectiveMime in unsupportedExactMimes || targetExt in blockedExtensions && !dispInfo.isAttachment) {
            return MediaProbeResult(
                requestedUrl = requestedUrl,
                finalUrl = finalUrl,
                httpCode = httpCode,
                mimeType = effectiveMime.ifBlank { "application/octet-stream" },
                contentLength = contentLength,
                acceptsRanges = acceptsRanges,
                contentDisposition = disposition,
                dispositionFileName = dispInfo.fileName,
                isDispositionAttachment = dispInfo.isAttachment,
                isHtml = false,
                verdict = ProbeVerdict.UNSUPPORTED_TYPE,
                usedRangeFallback = usedRangeFallback,
                errorDetail = "Unsupported content type (${effectiveMime.ifBlank { targetExt }})",
            )
        }

        val isSupportedExplicitMime = isSupportedConcreteMime(effectiveMime)
        val isVerifiedGenericBinary = isGenericBinaryMime(effectiveMime) && (
            magicMime != null ||
                (dispInfo.isAttachment && targetExt !in blockedExtensions) ||
                (sniffedBytes.isNotEmpty() && targetExt !in blockedExtensions)
            )

        val verdict = when {
            isSupportedExplicitMime -> ProbeVerdict.VERIFIED_MEDIA
            isVerifiedGenericBinary -> ProbeVerdict.VERIFIED_MEDIA
            effectiveMime.isBlank() && magicMime != null -> ProbeVerdict.VERIFIED_MEDIA
            effectiveMime.isBlank() && dispInfo.isAttachment && targetExt !in blockedExtensions -> ProbeVerdict.VERIFIED_MEDIA
            effectiveMime.isBlank() -> ProbeVerdict.VERIFICATION_FAILED
            else -> ProbeVerdict.UNSUPPORTED_TYPE
        }

        return MediaProbeResult(
            requestedUrl = requestedUrl,
            finalUrl = finalUrl,
            httpCode = httpCode,
            mimeType = effectiveMime.ifBlank { "application/octet-stream" },
            contentLength = contentLength,
            acceptsRanges = acceptsRanges,
            contentDisposition = disposition,
            dispositionFileName = dispInfo.fileName,
            isDispositionAttachment = dispInfo.isAttachment,
            isHtml = false,
            verdict = verdict,
            usedRangeFallback = usedRangeFallback,
            errorDetail = when (verdict) {
                ProbeVerdict.UNSUPPORTED_TYPE -> "Unsupported content type: $effectiveMime"
                ProbeVerdict.VERIFICATION_FAILED -> "Inconclusive media verification headers"
                else -> null
            },
        )
    }

    fun fetchOEmbed(
        endpointBase: String,
        targetUrl: String,
        securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): JSONObject? {
        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
        val fullUrl = "$endpointBase$encoded"
        val client = UrlSecurityValidator.createSafeHttpClient(securityPolicy)
        return runCatching {
            val req = Request.Builder()
                .url(fullUrl)
                .header("Accept", "application/json")
                .build()
            UrlSecurityValidator.executeWithSafeRedirects(client, req, securityPolicy).use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = readBoundedText(resp, MAX_HTML_READ_BYTES)
                JSONObject(bodyStr)
            }
        }.getOrNull()
    }

    fun fetchOpenGraphMetadata(
        url: String,
        securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): OpenGraphResult? {
        if (!UrlSecurityValidator.validateSyntaxAndHost(url, securityPolicy).isValid) {
            return null
        }
        val client = UrlSecurityValidator.createSafeHttpClient(securityPolicy)
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            UrlSecurityValidator.executeWithSafeRedirects(client, req, securityPolicy).use { resp ->
                val code = resp.code
                val resolvedUrl = resp.request.url.toString()
                val cType = extractCleanMime(resp)

                if (!resp.isSuccessful) {
                    return OpenGraphResult(
                        finalUrl = resolvedUrl,
                        title = url,
                        description = null,
                        imageUrl = null,
                        videoUrl = null,
                        author = null,
                        channelId = null,
                        httpCode = code,
                        contentType = cType,
                    )
                }

                if (cType.isNotBlank() && !cType.contains("html") && !cType.contains("xml")) {
                    return OpenGraphResult(
                        finalUrl = resolvedUrl,
                        title = resolvedUrl.substringAfterLast('/').substringBefore('?').ifBlank { resolvedUrl },
                        description = cType,
                        imageUrl = if (cType.startsWith("image/")) resolvedUrl else null,
                        videoUrl = if (cType.startsWith("video/")) resolvedUrl else null,
                        author = null,
                        channelId = null,
                        httpCode = code,
                        contentType = cType,
                    )
                }

                val html = readBoundedText(resp, MAX_HTML_READ_BYTES)
                val baseHttpUrl = resolvedUrl.toHttpUrlOrNull()

                val title = extractMeta(html, "og:title")
                    ?: extractMeta(html, "twitter:title")
                    ?: extractTitle(html)
                    ?: resolvedUrl
                val desc = extractMeta(html, "og:description") ?: extractMeta(html, "description")
                val rawImage = extractMeta(html, "og:image:secure_url")
                    ?: extractMeta(html, "og:image")
                    ?: extractMeta(html, "twitter:image")
                val rawVideo = extractMeta(html, "og:video:secure_url")
                    ?: extractMeta(html, "og:video:url")
                    ?: extractMeta(html, "og:video")
                    ?: extractMeta(html, "twitter:player:stream")
                val author = extractMeta(html, "author") ?: extractMeta(html, "twitter:creator")
                val channelId = Regex(""""channelId"\s*:\s*"(UC[a-zA-Z0-9_-]{20,})"""")
                    .find(html)?.groupValues?.getOrNull(1)
                    ?: Regex("""youtube\.com/channel/(UC[a-zA-Z0-9_-]{20,})""")
                        .find(html)?.groupValues?.getOrNull(1)

                val safeImage = resolveAndValidateSubUrl(baseHttpUrl, rawImage, securityPolicy)
                val safeVideo = resolveAndValidateSubUrl(baseHttpUrl, rawVideo, securityPolicy)

                OpenGraphResult(
                    finalUrl = resolvedUrl,
                    title = decodeHtmlEntities(title),
                    description = desc?.let(::decodeHtmlEntities),
                    imageUrl = safeImage,
                    videoUrl = safeVideo,
                    author = author,
                    channelId = channelId,
                    httpCode = code,
                    contentType = cType.ifBlank { "text/html" },
                )
            }
        }.getOrNull()
    }

    private fun resolveAndValidateSubUrl(
        baseUrl: okhttp3.HttpUrl?,
        rawTarget: String?,
        securityPolicy: UrlSecurityPolicy,
    ): String? {
        val candidate = rawTarget?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val decoded = decodeHtmlEntities(candidate)
        val resolved = baseUrl?.resolve(decoded)?.toString() ?: decoded
        return if (UrlSecurityValidator.validateSyntaxAndHost(resolved, securityPolicy).isValid) {
            resolved
        } else {
            null
        }
    }

    fun formatBytesLabel(bytes: Long): String? {
        if (bytes <= 0L) return null
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun guessMimeFromUrl(url: String): String {
        return when (url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "gz", "tgz" -> "application/gzip"
            "tar" -> "application/x-tar"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private data class DispositionInfo(
        val isAttachment: Boolean,
        val fileName: String?,
    )

    private fun parseContentDisposition(header: String?): DispositionInfo {
        if (header.isNullOrBlank()) return DispositionInfo(false, null)
        val isAttachment = header.contains("attachment", ignoreCase = true)
        val extStarMatch = Regex("""filename\*\s*=\s*(?:UTF-8''|utf-8'')?([^;\r\n]+)""")
            .find(header)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')
        val plainMatch = Regex("""filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;\r\n]+)""")
            .find(header)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }
            ?.trim()?.trim('"', '\'')
        val fileName = (extStarMatch ?: plainMatch)?.takeIf { it.isNotBlank() }
        return DispositionInfo(isAttachment = isAttachment, fileName = fileName)
    }

    private fun parseContentRangeTotal(header: String?): Long {
        if (header.isNullOrBlank()) return -1L
        val totalStr = header.substringAfterLast('/', "").trim()
        return if (totalStr == "*") -1L else (totalStr.toLongOrNull() ?: -1L)
    }

    private fun extractCleanMime(resp: Response): String {
        return resp.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
    }

    private fun isGenericBinaryMime(mime: String): Boolean {
        return mime == "application/octet-stream" || mime == "binary/octet-stream"
    }

    private fun isSupportedConcreteMime(mime: String): Boolean {
        if (mime.isBlank() || mime in unsupportedExactMimes) return false
        return mime.startsWith("video/") ||
            mime.startsWith("audio/") ||
            mime.startsWith("image/") ||
            mime in supportedDocumentMimes ||
            mime in supportedArchiveMimes ||
            mime.startsWith("application/vnd.openxmlformats-officedocument.")
    }

    private fun readBoundedBytes(resp: Response, limit: Int): ByteArray {
        val stream = resp.body?.byteStream() ?: return ByteArray(0)
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(512)
        var remaining = limit
        stream.use { input ->
            while (remaining > 0) {
                val read = input.read(chunk, 0, minOf(chunk.size, remaining))
                if (read == -1) break
                buffer.write(chunk, 0, read)
                remaining -= read
            }
        }
        return buffer.toByteArray()
    }

    private fun readBoundedText(resp: Response, limitBytes: Int): String {
        return String(readBoundedBytes(resp, limitBytes), Charsets.UTF_8)
    }

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val text = String(bytes, Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        return text.startsWith("<!doctype html") ||
            text.startsWith("<html") ||
            text.startsWith("<head") ||
            text.startsWith("<script") ||
            text.startsWith("<meta ")
    }

    private fun sniffMagicMime(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        // %PDF-
        if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) {
            return "application/pdf"
        }
        // PK\x03\x04 (ZIP)
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            return "application/zip"
        }
        // GZIP 1F 8B
        if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            return "application/gzip"
        }
        // PNG 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        // JPEG FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        // GIF87a / GIF89a
        if (bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }) {
            return "image/gif"
        }
        // RIFF....WEBP / WAVE / AVI
        if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") {
            return when (String(bytes, 8, 4, Charsets.US_ASCII)) {
                "WEBP" -> "image/webp"
                "WAVE" -> "audio/wav"
                "AVI " -> "video/x-msvideo"
                else -> null
            }
        }
        // MP4 / M4A / MOV ftyp
        if (bytes.size >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp") {
            return "video/mp4"
        }
        // WebM / Matroska 1A 45 DF A3
        if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()) {
            return "video/webm"
        }
        // ID3 or MP3 frame sync
        if ((bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) ||
            (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0)
        ) {
            return "audio/mpeg"
        }
        // OggS
        if (String(bytes, 0, 4, Charsets.US_ASCII) == "OggS") {
            return "audio/ogg"
        }
        // fLaC
        if (String(bytes, 0, 4, Charsets.US_ASCII) == "fLaC") {
            return "audio/flac"
        }
        return null
    }

    private fun extractMeta(html: String, property: String): String? {
        val pattern1 = Regex(
            """<meta[^>]+(?:property|name)=["']$property["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        val pattern2 = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$property["']""",
            RegexOption.IGNORE_CASE,
        )
        return (pattern1.find(html) ?: pattern2.find(html))?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractTitle(html: String): String? {
        return Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun decodeHtmlEntities(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
