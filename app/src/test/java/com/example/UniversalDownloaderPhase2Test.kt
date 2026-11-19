package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.media.DirectMediaAdapter
import com.example.data.media.DownloadEligibility
import com.example.data.media.MediaKind
import com.example.data.media.MediaMetadataExtractor
import com.example.data.media.PlatformDetector
import com.example.data.media.ProbeVerdict
import com.example.data.media.ResolvedMediaItem
import com.example.data.media.SupportedPlatform
import com.example.data.media.UniversalExtractionResult
import com.example.data.media.UrlSecurityPolicy
import com.example.data.media.UrlSecurityValidator
import com.example.data.media.WebGenericAdapter
import com.example.viewmodel.UniversalDownloaderViewModel
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UniversalDownloaderPhase2Test {

    @Test
    fun `selection handling never falls back to all on empty selection, separates Download All, and blocks ineligible items`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = UniversalDownloaderViewModel(app)

        val items = listOf(
            ResolvedMediaItem(
                id = "dl_1",
                platform = SupportedPlatform.DIRECT_MEDIA,
                kind = MediaKind.DIRECT_MEDIA,
                eligibility = DownloadEligibility.VERIFIED_DOWNLOADABLE,
                accessStatus = "Verified video/mp4",
                title = "Clip 1",
                creator = "cdn.example.org",
                thumbnailUrl = null,
                originalUrl = "https://cdn.example.org/1.mp4",
                downloadUrl = "https://cdn.example.org/1.mp4",
                mimeHint = "video/mp4",
                formatBadge = "MP4",
            ),
            ResolvedMediaItem(
                id = "dl_2",
                platform = SupportedPlatform.DIRECT_MEDIA,
                kind = MediaKind.DIRECT_MEDIA,
                eligibility = DownloadEligibility.VERIFIED_DOWNLOADABLE,
                accessStatus = "Verified application/pdf",
                title = "Doc 2",
                creator = "cdn.example.org",
                thumbnailUrl = null,
                originalUrl = "https://cdn.example.org/2.pdf",
                downloadUrl = "https://cdn.example.org/2.pdf",
                mimeHint = "application/pdf",
                formatBadge = "PDF",
            ),
            ResolvedMediaItem(
                id = "prev_1",
                platform = SupportedPlatform.YOUTUBE,
                kind = MediaKind.POST,
                eligibility = DownloadEligibility.PREVIEW_ONLY,
                accessStatus = "Preview only",
                title = "Webpage Preview",
                creator = "YouTube",
                thumbnailUrl = null,
                originalUrl = "https://www.youtube.com/watch?v=abc",
                downloadUrl = null,
                mimeHint = "text/html",
                formatBadge = "WATCH",
            ),
            ResolvedMediaItem(
                id = "auth_1",
                platform = SupportedPlatform.INSTAGRAM,
                kind = MediaKind.POST,
                eligibility = DownloadEligibility.REQUIRES_AUTHENTICATION,
                accessStatus = "HTTP 403",
                title = "Private Post",
                creator = "Instagram",
                thumbnailUrl = null,
                originalUrl = "https://www.instagram.com/p/private/",
                downloadUrl = null,
                mimeHint = "text/html",
                formatBadge = "AUTH",
            ),
            ResolvedMediaItem(
                id = "unsup_1",
                platform = SupportedPlatform.DIRECT_MEDIA,
                kind = MediaKind.UNSUPPORTED,
                eligibility = DownloadEligibility.UNSUPPORTED,
                accessStatus = "Unsupported manifest",
                title = "playlist.m3u8",
                creator = "cdn.example.org",
                thumbnailUrl = null,
                originalUrl = "https://cdn.example.org/playlist.m3u8",
                downloadUrl = null,
                mimeHint = "application/vnd.apple.mpegurl",
                formatBadge = "M3U8",
            ),
            ResolvedMediaItem(
                id = "fail_1",
                platform = SupportedPlatform.DIRECT_MEDIA,
                kind = MediaKind.UNSUPPORTED,
                eligibility = DownloadEligibility.VERIFICATION_FAILED,
                accessStatus = "HTTP 404",
                title = "missing.bin",
                creator = "cdn.example.org",
                thumbnailUrl = null,
                originalUrl = "https://cdn.example.org/missing.bin",
                downloadUrl = null,
                mimeHint = "",
                formatBadge = "FAILED",
            ),
        )

        val result = UniversalExtractionResult(
            inputQuery = "https://cdn.example.org/bundle",
            resolvedFinalUrl = "https://cdn.example.org/bundle",
            platform = SupportedPlatform.WEB_GENERIC,
            classification = MediaKind.WEBPAGE,
            items = items,
            statusBanner = "Test bundle",
        )

        vm.setLoadedResultForTesting(result)
        // Initial eligible selection excludes all 4 ineligible items
        assertEquals(setOf("dl_1", "dl_2"), vm.selectedIds.value)
        assertEquals(2, vm.selectedDownloadableCount())

        // Attempting to toggle ineligible items must never add them
        vm.toggleSelection("prev_1")
        vm.toggleSelection("auth_1")
        vm.toggleSelection("unsup_1")
        vm.toggleSelection("fail_1")
        assertEquals(setOf("dl_1", "dl_2"), vm.selectedIds.value)

        // Clearing selection and calling Download Selected MUST return empty list + show notice
        vm.clearSelection()
        assertEquals(0, vm.selectedDownloadableCount())
        val emptyBatch = vm.prepareSelectedBatch()
        assertTrue("Empty selection must never download everything", emptyBatch.isEmpty())
        assertNotNull("Expected user-visible selection notice when nothing is selected", vm.selectionNotice.value)

        // Explicit Download All works even when nothing is checked
        val allBatch = vm.prepareAllEligibleBatch()
        assertEquals(2, allBatch.size)
        assertEquals(listOf("https://cdn.example.org/1.mp4", "https://cdn.example.org/2.pdf"), allBatch.map { it.url })

        // Explicit single item selection downloads ONLY that item
        vm.toggleSelection("dl_2")
        assertEquals(1, vm.selectedDownloadableCount())
        val selectedOnly = vm.prepareSelectedBatch()
        assertEquals(1, selectedOnly.size)
        assertEquals("https://cdn.example.org/2.pdf", selectedOnly.first().url)
    }

    @Test
    fun `platform detector rejects deceptive lookalike domains and accepts genuine subdomains`() {
        // Deceptive hostnames MUST NOT match official social platforms
        assertEquals(
            SupportedPlatform.WEB_GENERIC,
            PlatformDetector.identifyPlatformByHost("https://youtube.com.example.org/watch?v=123"),
        )
        assertEquals(
            SupportedPlatform.WEB_GENERIC,
            PlatformDetector.identifyPlatformByHost("https://fakeyoutube.com/watch?v=123"),
        )
        assertEquals(
            SupportedPlatform.WEB_GENERIC,
            PlatformDetector.identifyPlatformByHost("https://evilinstagram.com/reel/abc/"),
        )
        assertEquals(
            SupportedPlatform.WEB_GENERIC,
            PlatformDetector.identifyPlatformByHost("https://reddit.com.attacker.net/r/kotlin"),
        )
        assertEquals(
            SupportedPlatform.WEB_GENERIC,
            PlatformDetector.identifyPlatformByHost("https://pinterest.com.phish.org/pin/123"),
        )
        assertEquals(
            SupportedPlatform.WEB_GENERIC,
            PlatformDetector.identifyPlatformByHost("https://mytiktok.com/@user/video/9"),
        )

        // Genuine official domains and subdomains match cleanly
        assertEquals(SupportedPlatform.YOUTUBE, PlatformDetector.identifyPlatformByHost("https://m.youtube.com/watch?v=abc"))
        assertEquals(SupportedPlatform.YOUTUBE, PlatformDetector.identifyPlatformByHost("https://youtu.be/abc"))
        assertEquals(SupportedPlatform.REDDIT, PlatformDetector.identifyPlatformByHost("https://old.reddit.com/r/androiddev"))
        assertEquals(SupportedPlatform.REDDIT, PlatformDetector.identifyPlatformByHost("https://v.redd.it/xyz"))
        assertEquals(SupportedPlatform.PINTEREST, PlatformDetector.identifyPlatformByHost("https://www.pinterest.co.uk/pin/123"))
        assertEquals(SupportedPlatform.TWITTER, PlatformDetector.identifyPlatformByHost("https://mobile.twitter.com/user/status/1"))
        assertEquals(SupportedPlatform.TIKTOK, PlatformDetector.identifyPlatformByHost("https://vm.tiktok.com/ZM123"))
        assertEquals(SupportedPlatform.THREADS, PlatformDetector.identifyPlatformByHost("https://www.threads.com/@user/post/1"))
    }

    @Test
    fun `url security validator blocks invalid schemes, SSRF internal targets, and unsafe redirects`() {
        // Invalid schemes & malformed URLs
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("javascript:alert(1)").isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("file:///etc/passwd").isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("data:text/html,<script>").isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("https://user:pass@example.com/a.mp4").isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("not a valid url").isValid)

        // Private / localhost / metadata SSRF targets strictly rejected
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://localhost/admin", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://127.0.0.1:8080/clip.mp4", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://10.0.0.1/internal", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://172.16.5.4/internal", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://192.168.1.50/router", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://169.254.169.254/latest/meta-data/", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://[::1]/loopback", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://[fd00::1]/ula", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://0.0.0.0/any", UrlSecurityPolicy.STRICT).isValid)
        assertFalse(UrlSecurityValidator.validateSyntaxAndHost("http://db.internal/dump.zip", UrlSecurityPolicy.STRICT).isValid)

        // Valid public HTTPS host succeeds syntax validation
        assertTrue(UrlSecurityValidator.validateSyntaxAndHost("https://cdn.example.org/file.mp4", UrlSecurityPolicy.STRICT).isValid)

        // Unsafe redirects (redirect to 10.0.0.1 and redirect loop) blocked during execution
        val server = ServerSocket(0)
        val port = server.localPort
        val t = thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        client.use { s ->
                            val input = s.getInputStream().bufferedReader()
                            val line = input.readLine() ?: return@use
                            while (true) {
                                val h = input.readLine() ?: break
                                if (h.isBlank()) break
                            }
                            val path = line.split(" ").getOrElse(1) { "/" }
                            val out = s.getOutputStream()
                            when (path) {
                                "/to-private" -> {
                                    out.write("HTTP/1.1 302 Found\r\nLocation: http://10.0.0.1/secret.mp4\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                                "/loop-a" -> {
                                    out.write("HTTP/1.1 302 Found\r\nLocation: /loop-b\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                                "/loop-b" -> {
                                    out.write("HTTP/1.1 302 Found\r\nLocation: /loop-a\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                            }
                            out.flush()
                        }
                    }
                }
            }
        }

        try {
            val unsafeRedirectProbe = MediaMetadataExtractor.probeMediaTarget(
                "http://127.0.0.1:$port/to-private",
                securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST,
            )
            assertEquals(ProbeVerdict.VERIFICATION_FAILED, unsafeRedirectProbe.verdict)
            assertFalse(unsafeRedirectProbe.isVerifiedDownloadable)

            val loopProbe = MediaMetadataExtractor.probeMediaTarget(
                "http://127.0.0.1:$port/loop-a",
                securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST,
            )
            assertEquals(ProbeVerdict.VERIFICATION_FAILED, loopProbe.verdict)
            assertFalse(loopProbe.isVerifiedDownloadable)

            // Under STRICT policy, connecting to 127.0.0.1 itself is immediately rejected
            val strictDirectProbe = MediaMetadataExtractor.probeMediaTarget(
                "http://127.0.0.1:$port/to-private",
                securityPolicy = UrlSecurityPolicy.STRICT,
            )
            assertEquals(ProbeVerdict.VERIFICATION_FAILED, strictDirectProbe.verdict)
            assertTrue(strictDirectProbe.errorDetail.orEmpty().contains("Blocked", ignoreCase = true))

            // Authorization header must NOT be forwarded across cross-origin redirect hops
            var threwSafe = false
            val client = UrlSecurityValidator.createSafeHttpClient(UrlSecurityPolicy.LOOPBACK_TEST)
            try {
                UrlSecurityValidator.executeWithSafeRedirects(
                    client = client,
                    initialRequest = Request.Builder()
                        .url("http://127.0.0.1:$port/to-private")
                        .header("Authorization", "Bearer secret-token")
                        .build(),
                    policy = UrlSecurityPolicy.LOOPBACK_TEST,
                ).close()
            } catch (_: SecurityException) {
                threwSafe = true
            }
            assertTrue(threwSafe)
        } finally {
            server.close()
            t.interrupt()
        }
    }

    @Test
    fun `media verification handles HEAD fallback, HTML masquerading, Content-Disposition, OpenGraph, and all 5 eligibility states`() {
        val server = ServerSocket(0)
        val port = server.localPort
        val hugeChunkedCounter = byteArrayOf(
            0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37, // %PDF-1.7
            0x0A, 0x25, 0x42, 0x4F, 0x44, 0x59,
        )

        val t = thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        client.use { s ->
                            val input = s.getInputStream().bufferedReader()
                            val line = input.readLine() ?: return@use
                            while (true) {
                                val h = input.readLine() ?: break
                                if (h.isBlank()) break
                            }
                            val method = line.split(" ").firstOrNull().orEmpty()
                            val path = line.split(" ").getOrElse(1) { "/" }
                            val out = s.getOutputStream()

                            when (path) {
                                // Extensionless URL with real binary video MIME + Content-Disposition filename
                                "/download?id=42" -> {
                                    out.write(
                                        "HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Disposition: attachment; filename=\"annual_keynote.mp4\"\r\nContent-Length: 8192\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n"
                                            .toByteArray()
                                    )
                                }

                                // HTML page masquerading as .mp4 filename
                                "/fakevideo.mp4" -> {
                                    val html = "<!DOCTYPE html><html><head><title>Player Wrapper</title></head><body>Watch in browser</body></html>".toByteArray()
                                    out.write(
                                        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n"
                                            .toByteArray()
                                    )
                                    if (method != "HEAD") out.write(html)
                                }

                                // HTML masquerading even with application/octet-stream (caught via bounded GET sniff)
                                "/sneaky_html.mp4" -> {
                                    val html = "  <html><head><title>Not Media</title></head></html>".toByteArray()
                                    out.write(
                                        "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n"
                                            .toByteArray()
                                    )
                                    if (method != "HEAD") out.write(html)
                                }

                                // Server rejects HEAD with 405 -> fallback GET Range 206 succeeds
                                "/head_405.zip" -> {
                                    if (method == "HEAD") {
                                        out.write("HTTP/1.1 405 Method Not Allowed\r\nAllow: GET\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                    } else {
                                        val zipPrefix = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
                                        out.write(
                                            "HTTP/1.1 206 Partial Content\r\nContent-Type: application/zip\r\nContent-Range: bytes 0-7/16384\r\nContent-Length: ${zipPrefix.size}\r\nConnection: close\r\n\r\n"
                                                .toByteArray()
                                        )
                                        out.write(zipPrefix)
                                    }
                                }

                                // Server drops socket abruptly on HEAD -> fallback GET recovers PDF magic
                                "/head_drop.pdf" -> {
                                    if (method == "HEAD") {
                                        s.close()
                                    } else {
                                        out.write(
                                            "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${hugeChunkedCounter.size}\r\nConnection: close\r\n\r\n"
                                                .toByteArray()
                                        )
                                        out.write(hugeChunkedCounter)
                                    }
                                }

                                // Unsupported streaming manifest format (.m3u8)
                                "/stream.m3u8" -> {
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: 120\r\nConnection: close\r\n\r\n".toByteArray())
                                }

                                // 401 Authorization required
                                "/members_only.mp4" -> {
                                    out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                }

                                // 404 Not Found -> Verification Failed
                                "/gone.mp4" -> {
                                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                }

                                // OpenGraph page with BOTH verified og:image AND broken og:video + preview-only page check
                                "/og-mixed" -> {
                                    val html = """
                                        <html><head>
                                          <meta property="og:title" content="Mixed Article"/>
                                          <meta property="og:image" content="http://127.0.0.1:$port/cover.png"/>
                                          <meta property="og:video" content="http://127.0.0.1:$port/embed-player"/>
                                        </head><body>Content</body></html>
                                    """.trimIndent().toByteArray()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    if (method != "HEAD") out.write(html)
                                }

                                "/cover.png" -> {
                                    val pngSig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${pngSig.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    if (method != "HEAD") out.write(pngSig)
                                }

                                "/embed-player" -> {
                                    val html = "<html><body>Embedded Iframe Player</body></html>".toByteArray()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    if (method != "HEAD") out.write(html)
                                }

                                // OpenGraph page with broken 404 image fallback
                                "/og-broken-image" -> {
                                    val html = """<html><head><meta property="og:title" content="Broken Img Page"/><meta property="og:image" content="http://127.0.0.1:$port/missing.jpg"/></head><body>Body</body></html>""".toByteArray()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    if (method != "HEAD") out.write(html)
                                }
                            }
                            runCatching { out.flush() }
                        }
                    }
                }
            }
        }

        try {
            // 1. Extensionless download URL verified via Content-Type & Content-Disposition
            val extlessRes = WebGenericAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/download?id=42", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(1, extlessRes.items.size)
            assertEquals(DownloadEligibility.VERIFIED_DOWNLOADABLE, extlessRes.items.first().eligibility)
            assertEquals("annual_keynote.mp4", extlessRes.items.first().title)
            assertTrue(extlessRes.items.first().isDownloadable)

            // 2. HTML page masquerading as .mp4 MUST NOT be marked downloadable
            val fakeMp4Res = DirectMediaAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/fakevideo.mp4", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(1, fakeMp4Res.items.size)
            assertFalse(fakeMp4Res.items.first().isDownloadable)
            assertEquals(DownloadEligibility.PREVIEW_ONLY, fakeMp4Res.items.first().eligibility)
            assertNull(fakeMp4Res.items.first().downloadUrl)

            // 3. Sneaky octet-stream HTML caught via bounded GET sniff
            val sneakyHtmlRes = DirectMediaAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/sneaky_html.mp4", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertFalse(sneakyHtmlRes.items.first().isDownloadable)
            assertEquals(DownloadEligibility.PREVIEW_ONLY, sneakyHtmlRes.items.first().eligibility)
            assertNull(sneakyHtmlRes.items.first().downloadUrl)

            // 4. HEAD 405 -> GET Range fallback succeeds and preserves Content-Range total size
            val head405Probe = MediaMetadataExtractor.probeMediaTarget(
                "http://127.0.0.1:$port/head_405.zip",
                UrlSecurityPolicy.LOOPBACK_TEST,
            )
            assertTrue(head405Probe.usedRangeFallback)
            assertEquals(ProbeVerdict.VERIFIED_MEDIA, head405Probe.verdict)
            assertEquals(16384L, head405Probe.contentLength)
            assertTrue(head405Probe.acceptsRanges)

            // 5. HEAD socket drop -> GET fallback recovers PDF magic bytes
            val headDropRes = DirectMediaAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/head_drop.pdf", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(DownloadEligibility.VERIFIED_DOWNLOADABLE, headDropRes.items.first().eligibility)
            assertEquals("application/pdf", headDropRes.items.first().mimeHint)

            // 6. Unsupported streaming playlist MIME (.m3u8) -> UNSUPPORTED
            val m3u8Res = WebGenericAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/stream.m3u8", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(DownloadEligibility.UNSUPPORTED, m3u8Res.items.first().eligibility)
            assertFalse(m3u8Res.items.first().isDownloadable)

            // 7. HTTP 401 -> REQUIRES_AUTHENTICATION
            val authRes = DirectMediaAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/members_only.mp4", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(DownloadEligibility.REQUIRES_AUTHENTICATION, authRes.items.first().eligibility)
            assertFalse(authRes.items.first().isDownloadable)

            // 8. HTTP 404 -> VERIFICATION_FAILED
            val goneRes = DirectMediaAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/gone.mp4", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(DownloadEligibility.VERIFICATION_FAILED, goneRes.items.first().eligibility)
            assertFalse(goneRes.items.first().isDownloadable)

            // 9. OpenGraph mixed page: verified thumbnail stays separate from webpage URL; HTML og:video is PREVIEW_ONLY with null downloadUrl
            val ogMixedRes = WebGenericAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/og-mixed", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            val videoItem = ogMixedRes.items.first { it.kind == MediaKind.EMBEDDED_MEDIA }
            assertEquals(DownloadEligibility.PREVIEW_ONLY, videoItem.eligibility)
            assertNull(videoItem.downloadUrl)
            assertEquals("http://127.0.0.1:$port/og-mixed", videoItem.originalUrl)
            assertEquals("http://127.0.0.1:$port/cover.png", videoItem.thumbnailUrl)

            val thumbItem = ogMixedRes.items.first { it.kind == MediaKind.THUMBNAIL }
            assertEquals(DownloadEligibility.VERIFIED_DOWNLOADABLE, thumbItem.eligibility)
            assertEquals("http://127.0.0.1:$port/cover.png", thumbItem.downloadUrl)
            assertEquals("http://127.0.0.1:$port/og-mixed", thumbItem.originalUrl)

            // 10. OpenGraph page whose og:image 404s MUST NOT mark og:image downloadable or substitute webpage URL
            val ogBrokenRes = WebGenericAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/og-broken-image", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertFalse(ogBrokenRes.items.any { it.isDownloadable })
            assertEquals(DownloadEligibility.VERIFICATION_FAILED, ogBrokenRes.items.first().eligibility)
            assertNull(ogBrokenRes.items.first().downloadUrl)
        } finally {
            server.close()
            t.interrupt()
        }
    }
}
