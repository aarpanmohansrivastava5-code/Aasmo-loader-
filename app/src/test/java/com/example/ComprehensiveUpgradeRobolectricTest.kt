package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.DuplicateBehavior
import com.example.data.browser.WebViewJsBridge
import com.example.data.download.PacketDownloadEngine
import com.example.data.files.AasmoFileManager
import com.example.data.files.FileCategory
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadStatus
import com.example.data.media.BatchExtractionManager
import com.example.data.media.DirectMediaAdapter
import com.example.data.media.DownloadEligibility
import com.example.data.media.MediaKind
import com.example.data.media.PlatformDetector
import com.example.data.media.ResolvedMediaItem
import com.example.data.media.SocialOpenGraphPlatformAdapter
import com.example.data.media.SupportedPlatform
import com.example.data.media.UrlSecurityPolicy
import com.example.data.media.WebGenericAdapter
import com.example.data.security.CredentialVaultHelper
import com.example.viewmodel.BrowserViewModel
import com.example.viewmodel.HubsViewModel
import com.example.viewmodel.UniversalDownloaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComprehensiveUpgradeRobolectricTest {

    @Test
    fun `room migrations 1 to 2 to 3 preserve existing user downloads bookmarks and history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_verify_${System.currentTimeMillis()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create schema v1 manually and seed rows
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL(
            """
            CREATE TABLE downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                fileName TEXT NOT NULL,
                source TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                totalBytes INTEGER NOT NULL,
                downloadedBytes INTEGER NOT NULL,
                speedBytesPerSec INTEGER NOT NULL,
                chunkCount INTEGER NOT NULL,
                chunkProgressCsv TEXT NOT NULL,
                status TEXT NOT NULL,
                localPath TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                exportedUri TEXT NOT NULL,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                badgeLetter TEXT NOT NULL,
                badgeColorHex INTEGER NOT NULL,
                isSpeedDial INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                visitedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            "INSERT INTO downloads (title, url, fileName, source, mimeType, totalBytes, downloadedBytes, speedBytesPerSec, chunkCount, chunkProgressCsv, status, localPath, sha256, exportedUri, createdAt) VALUES ('Archive.zip', 'https://example.org/a.zip', 'Archive.zip', 'DIRECT', 'application/zip', 100, 100, 0, 4, '100,100,100,100', 'COMPLETED', '/tmp/a.zip', 'abc', '', 1000)"
        )
        rawDb.execSQL(
            "INSERT INTO bookmarks (title, url, badgeLetter, badgeColorHex, isSpeedDial, createdAt) VALUES ('Docs', 'https://kotlinlang.org', 'K', 12345, 1, 1000)"
        )
        rawDb.execSQL(
            "INSERT INTO history (title, url, visitedAt) VALUES ('Kotlin', 'https://kotlinlang.org', 1000)"
        )
        rawDb.version = 1
        rawDb.close()

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion < 2) AppDatabase.MIGRATION_1_2.migrate(db)
                    if (oldVersion < 3) AppDatabase.MIGRATION_2_3.migrate(db)
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val migrated = helper.writableDatabase

        // Verify preserved records + new columns & v3 tables
        migrated.query("SELECT title, batchId, retryCount FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Archive.zip", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
        }
        migrated.query("SELECT title, folderName FROM bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Docs", c.getString(0))
            assertEquals("General", c.getString(1))
        }
        migrated.query("SELECT title, domain FROM history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Kotlin", c.getString(0))
            assertEquals("", c.getString(1))
        }
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('site_rules','filter_rules','extraction_history')").use { c ->
            assertEquals(3, c.count)
        }
        helper.close()
        dbFile.delete()
    }

    @Test
    fun `browser viewmodel supports tab actions bookmarks json reader mode sniffing site rules and clear data`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = BrowserViewModel(app)

        vm.navigateToInput("kotlinlang.org")
        val tab1Id = vm.uiState.value.activeTab.id
        vm.onWebViewStateChanged(tab1Id, "https://kotlinlang.org", "Kotlin Lang", canGoBack = false, canGoForward = false, progress = 100, recordHistory = true)

        vm.toggleBookmarkForActiveTab("Dev")
        delay(120)
        val json = vm.exportBookmarksJson()
        assertTrue(json.contains("https://kotlinlang.org"))

        var importedCount = -1
        vm.importBookmarksJson("""[{"title":"MDN","url":"https://developer.mozilla.org","folder":"Reference","color":123}]""") {
            importedCount = it
        }
        delay(150)
        assertEquals(1, importedCount)

        // Media sniffing via JS bridge
        var capturedCount = 0
        val bridge = WebViewJsBridge(
            tabId = tab1Id,
            onMediaUpdate = { _, _, _, _ -> },
            onSniffedMedia = { tid, items ->
                capturedCount = items.size
                vm.onSniffedMediaReceived(tid, items)
            },
            onReaderExtracted = { _, title, text -> vm.onReaderTextExtracted(title, text) },
        )
        bridge.postSniffedMedia("""[{"url":"https://cdn.example.org/demo.mp4","title":"Demo Video","type":"VIDEO"}]""")
        assertEquals(1, capturedCount)
        assertEquals(1, vm.uiState.value.activeTab.detectedMedia.size)

        // Reader Mode extraction + dismiss
        bridge.postReaderText("Clean Article", "Paragraph content inside reader mode.")
        assertTrue(vm.uiState.value.readerModal.visible)
        assertEquals("Clean Article", vm.uiState.value.readerModal.title)
        vm.dismissReaderModal()
        assertFalse(vm.uiState.value.readerModal.visible)

        // Site permissions + Clear Browsing Data
        vm.updateSiteRule("kotlinlang.org") { it.copy(allowListed = true, allowLocation = true) }
        delay(100)
        assertTrue(vm.siteRules.first().any { it.host == "kotlinlang.org" && it.allowListed && it.allowLocation })

        vm.newTab()
        vm.newTab()
        vm.closeTabsToRight(1)
        assertEquals(2, vm.uiState.value.tabs.size)
        vm.closeOtherTabs(0)
        assertEquals(1, vm.uiState.value.tabs.size)

        vm.clearBrowsingData(clearHistory = true, clearCookies = false, clearCache = false, clearSiteData = true)
        delay(120)
        assertTrue(vm.history.first().isEmpty())
    }

    @Test
    fun `packet download engine executes range 206 non-range 200 retry html-error interruption pause resume cancel and duplicate skip`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        val dao = db.downloadDao()
        val engine = PacketDownloadEngine(app, dao, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)

        // Payload > 256 KB so multi-chunk Range 206 path triggers cleanly
        val rangePayload = ByteArray(300 * 1024) { idx -> ('A'.code + (idx % 26)).toByte() }
        val nonRangePayload = "NON_RANGE_SAMPLE_PAYLOAD_BYTES".toByteArray()
        val retryAttempts = AtomicInteger(0)

        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        client.use { s ->
                            val input = s.getInputStream().bufferedReader()
                            val requestLine = input.readLine() ?: return@use
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val line = input.readLine() ?: break
                                if (line.isBlank()) break
                                val colon = line.indexOf(':')
                                if (colon > 0) {
                                    headers[line.substring(0, colon).trim().lowercase()] =
                                        line.substring(colon + 1).trim()
                                }
                            }
                            val parts = requestLine.split(" ")
                            val method = parts.getOrElse(0) { "GET" }
                            val path = parts.getOrElse(1) { "/" }
                            val out = s.getOutputStream()

                            when (path) {
                                "/range.bin" -> {
                                    val rangeHeader = headers["range"]
                                    if (method == "HEAD") {
                                        val hdr = "HTTP/1.1 200 OK\r\nContent-Length: ${rangePayload.size}\r\nAccept-Ranges: bytes\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                                        out.write(hdr.toByteArray())
                                    } else if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                                        val span = rangeHeader.removePrefix("bytes=").split("-")
                                        val start = span[0].toInt()
                                        val end = span.getOrNull(1)?.toIntOrNull()?.coerceAtMost(rangePayload.lastIndex)
                                            ?: rangePayload.lastIndex
                                        val len = end - start + 1
                                        val hdr = "HTTP/1.1 206 Partial Content\r\nContent-Length: $len\r\nContent-Range: bytes $start-$end/${rangePayload.size}\r\nAccept-Ranges: bytes\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                                        out.write(hdr.toByteArray())
                                        out.write(rangePayload, start, len)
                                    } else {
                                        val hdr = "HTTP/1.1 200 OK\r\nContent-Length: ${rangePayload.size}\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                                        out.write(hdr.toByteArray())
                                        out.write(rangePayload)
                                    }
                                    out.flush()
                                }

                                "/nonrange.txt" -> {
                                    val hdr = "HTTP/1.1 200 OK\r\nContent-Length: ${nonRangePayload.size}\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n"
                                    out.write(hdr.toByteArray())
                                    if (method != "HEAD") out.write(nonRangePayload)
                                    out.flush()
                                }

                                "/flaky.txt" -> {
                                    if (method == "HEAD") {
                                        out.write("HTTP/1.1 200 OK\r\nContent-Length: ${nonRangePayload.size}\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n".toByteArray())
                                    } else {
                                        val attempt = retryAttempts.incrementAndGet()
                                        if (attempt == 1) {
                                            out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                        } else {
                                            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${nonRangePayload.size}\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n".toByteArray())
                                            out.write(nonRangePayload)
                                        }
                                    }
                                    out.flush()
                                }

                                "/fakevideo.mp4" -> {
                                    val html = "<html><body>Not a video</body></html>".toByteArray()
                                    val hdr = "HTTP/1.1 200 OK\r\nContent-Length: ${html.size}\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n"
                                    out.write(hdr.toByteArray())
                                    if (method != "HEAD") out.write(html)
                                    out.flush()
                                }

                                "/broken.bin" -> {
                                    if (method == "HEAD") {
                                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n".toByteArray())
                                        out.flush()
                                    } else {
                                        // Premature socket EOF mid-transfer triggers network interruption failure
                                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\nContent-Type: application/octet-stream\r\n\r\nPARTIAL".toByteArray())
                                        out.flush()
                                        s.close()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            // 1. Range-supported multi-chunk download
            val rangeUrl = "http://127.0.0.1:$port/range.bin"
            engine.enqueueDownload(rangeUrl, title = "RangePack.bin", chunkCount = 4, notificationsEnabled = false, batchId = "batch_test")

            // 2. Non-range direct download
            val nonRangeUrl = "http://127.0.0.1:$port/nonrange.txt"
            engine.enqueueDownload(nonRangeUrl, title = "PlainDoc.txt", chunkCount = 2, notificationsEnabled = false, batchId = "batch_test")

            // 3. Auto-retry recovery (500 -> 200)
            val flakyUrl = "http://127.0.0.1:$port/flaky.txt"
            engine.enqueueDownload(flakyUrl, title = "FlakyDoc.txt", chunkCount = 2, notificationsEnabled = false, autoRetry = true)

            // 4. Unsupported HTML masquerading as MP4
            val fakeVideoUrl = "http://127.0.0.1:$port/fakevideo.mp4"
            engine.enqueueDownload(fakeVideoUrl, title = "FakeVideo.mp4", chunkCount = 2, notificationsEnabled = false, autoRetry = false)

            // 5. Network interruption (premature EOF)
            val brokenUrl = "http://127.0.0.1:$port/broken.bin"
            engine.enqueueDownload(brokenUrl, title = "Broken.bin", chunkCount = 2, notificationsEnabled = false, autoRetry = false)

            // 6. Invalid URL rejection
            engine.enqueueDownload("javascript:alert(1)", notificationsEnabled = false)

            // Wait up to 6s for all local loopback transfers to settle
            var settled = false
            repeat(60) {
                val all = dao.observeAll().first()
                val rangeEntity = all.firstOrNull { it.url == rangeUrl }
                val nonRangeEntity = all.firstOrNull { it.url == nonRangeUrl }
                val flakyEntity = all.firstOrNull { it.url == flakyUrl }
                val fakeEntity = all.firstOrNull { it.url == fakeVideoUrl }
                val brokenEntity = all.firstOrNull { it.url == brokenUrl }
                val invalidEntity = all.firstOrNull { it.url == "javascript:alert(1)" }
                if (rangeEntity?.status == DownloadStatus.COMPLETED.name &&
                    nonRangeEntity?.status == DownloadStatus.COMPLETED.name &&
                    flakyEntity?.status == DownloadStatus.COMPLETED.name &&
                    fakeEntity?.status == DownloadStatus.FAILED.name &&
                    brokenEntity?.status == DownloadStatus.FAILED.name &&
                    invalidEntity?.status == DownloadStatus.FAILED.name
                ) {
                    settled = true
                    return@repeat
                }
                delay(100)
            }
            assertTrue("Expected loopback downloads to settle cleanly", settled)

            val allAfter = dao.observeAll().first()
            val rangeDone = allAfter.first { it.url == rangeUrl }
            assertEquals("batch_test", rangeDone.batchId)
            assertEquals(rangePayload.size.toLong(), File(rangeDone.localPath).length())
            assertTrue(File(rangeDone.localPath).readBytes().contentEquals(rangePayload))
            assertTrue(rangeDone.sha256.length == 64)

            val nonRangeDone = allAfter.first { it.url == nonRangeUrl }
            assertEquals("batch_test", nonRangeDone.batchId)
            assertEquals("NON_RANGE_SAMPLE_PAYLOAD_BYTES", File(nonRangeDone.localPath).readText())

            // Duplicate SKIP test
            var duplicateSkipped = false
            engine.enqueueDownload(
                rawUrl = nonRangeUrl,
                duplicateBehavior = DuplicateBehavior.SKIP,
                notificationsEnabled = false,
                onDuplicateSkipped = { duplicateSkipped = true },
            )
            delay(150)
            assertTrue(duplicateSkipped)

            // Pause -> Resume -> Cancel flow
            engine.pauseDownload(rangeDone.id)
            engine.resumeDownload(rangeDone.id, notificationsEnabled = false)
            delay(250)
            engine.cancelAndFailDownload(nonRangeDone.id)
            delay(120)
            assertEquals(DownloadStatus.FAILED.name, dao.getById(nonRangeDone.id)?.status)
        } finally {
            server.close()
            serverThread.interrupt()
        }
    }

    @Test
    fun `universal social extraction distinguishes direct downloadable media opengraph previews and auth walls`() {
        // Platform detection matrix
        assertEquals(SupportedPlatform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/reel/ABC123/", resolveRedirects = false).platform)
        assertEquals(SupportedPlatform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/watch/?v=123", resolveRedirects = false).platform)
        assertEquals(SupportedPlatform.TWITTER, PlatformDetector.detect("https://x.com/nasa/status/12345", resolveRedirects = false).platform)
        assertEquals(SupportedPlatform.PINTEREST, PlatformDetector.detect("https://www.pinterest.com/pin/98765/", resolveRedirects = false).platform)
        assertEquals(SupportedPlatform.TIKTOK, PlatformDetector.detect("https://www.tiktok.com/@creator/video/555", resolveRedirects = false).platform)
        assertEquals(SupportedPlatform.THREADS, PlatformDetector.detect("https://www.threads.net/@creator/post/abc", resolveRedirects = false).platform)

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
                            val method = line.split(" ").firstOrNull().orEmpty()
                            val path = line.split(" ").getOrElse(1) { "/" }
                            val out = s.getOutputStream()
                            when {
                                path == "/clip.mp4" -> {
                                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 4096\r\nAccept-Ranges: bytes\r\nContent-Type: video/mp4\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                                path == "/og-page" -> {
                                    val html = """<html><head><meta property="og:title" content="Sample Post"/><meta property="og:image" content="http://127.0.0.1:$port/thumb.jpg"/></head><body>Hi</body></html>""".toByteArray()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${html.size}\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n".toByteArray())
                                    if (method != "HEAD") out.write(html)
                                }
                                path == "/thumb.jpg" -> {
                                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 512\r\nContent-Type: image/jpeg\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                                path == "/private-wall" -> {
                                    out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                            }
                            out.flush()
                        }
                    }
                }
            }
        }

        try {
            val directRes = DirectMediaAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/clip.mp4", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertEquals(1, directRes.items.size)
            assertEquals(DownloadEligibility.VERIFIED_DOWNLOADABLE, directRes.items.first().eligibility)
            assertTrue(directRes.items.first().isDownloadable)

            val webOgRes = WebGenericAdapter(UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/og-page", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST)
            )
            assertTrue(webOgRes.items.any { it.isDownloadable && it.downloadUrl == "http://127.0.0.1:$port/thumb.jpg" })

            val authWallRes = SocialOpenGraphPlatformAdapter(SupportedPlatform.INSTAGRAM, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST).extract(
                PlatformDetector.detect("http://127.0.0.1:$port/private-wall", resolveRedirects = false, securityPolicy = UrlSecurityPolicy.LOOPBACK_TEST).copy(
                    platform = SupportedPlatform.INSTAGRAM,
                    initialClassification = MediaKind.POST,
                )
            )
            assertEquals(DownloadEligibility.REQUIRES_AUTHENTICATION, authWallRes.items.first().eligibility)
            assertFalse(authWallRes.items.first().isDownloadable)
            assertTrue(authWallRes.statusBanner.contains("403"))

            // Batch filtering excludes non-downloadable items cleanly
            val batch = BatchExtractionManager.prepareEligibleBatch(
                listOf(
                    directRes.items.first(),
                    ResolvedMediaItem(
                        id = "preview_only",
                        platform = SupportedPlatform.YOUTUBE,
                        kind = MediaKind.POST,
                        eligibility = DownloadEligibility.PREVIEW_ONLY,
                        accessStatus = "Metadata only",
                        title = "YouTube Video",
                        creator = "Creator",
                        thumbnailUrl = null,
                        originalUrl = "https://youtube.com/watch?v=xyz",
                        downloadUrl = null,
                        mimeHint = "text/html",
                        formatBadge = "WATCH",
                    ),
                )
            )
            assertEquals(1, batch.size)
        } finally {
            server.close()
            t.interrupt()
        }
    }

    @Test
    fun `file manager copies moves builds FileProvider intents and inspects real zip archives`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fm = AasmoFileManager(app)

        val folderRes = fm.createFolder(null, "ZipTestFolder")
        assertTrue(folderRes.isSuccess)
        val folder = folderRes.getOrThrow()

        val zipFile = File(fm.rootDir, "sample_archive.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("hello zip".toByteArray())
            zos.closeEntry()
        }

        val listed = fm.listFiles(category = FileCategory.ARCHIVES, query = "sample_archive")
        val item = listed.firstOrNull { it.path == zipFile.absolutePath }
        assertNotNull(item)

        val report = fm.inspectDetails(item!!)
        assertEquals(64, report.sha256.length)
        assertTrue(report.zipEntries.any { it.name == "readme.txt" })

        val openIntent = fm.buildOpenIntent(item)
        val shareIntent = fm.buildShareIntent(item)
        assertNotNull(openIntent)
        assertEquals(Intent.ACTION_VIEW, openIntent?.action)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent?.action)

        val copied = fm.copyOrMoveFile(zipFile.absolutePath, folder.absolutePath, move = false)
        assertTrue(copied.isSuccess)
        assertTrue(zipFile.exists())
        assertTrue(copied.getOrThrow().exists())

        val moved = fm.copyOrMoveFile(copied.getOrThrow().absolutePath, fm.documentsDir.absolutePath, move = true)
        assertTrue(moved.isSuccess)
        assertFalse(File(folder, "sample_archive.zip").exists())

        fm.deletePath(moved.getOrThrow().absolutePath)
        fm.deletePath(zipFile.absolutePath)
        fm.deletePath(folder.absolutePath)
    }

    @Test
    fun `credential manager builds password and passkey requests and reports system status`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val status = CredentialVaultHelper.inspectSystemCredentialStatus(app)
        assertTrue(status.credentialManagerSupported)

        val req = CredentialVaultHelper.buildGetCredentialRequest(
            passkeyChallengeJson = """{"challenge":"dGVzdA","rpId":"example.com","userVerification":"preferred"}"""
        )
        assertEquals(2, req.credentialOptions.size)
        assertTrue(req.credentialOptions.any { it is GetPasswordOption })
        assertTrue(req.credentialOptions.any { it is GetPublicKeyCredentialOption })

        val msg = CredentialVaultHelper.checkSavedCredentialPrompt(app)
        assertTrue(msg.contains("Credential Manager"))
    }

    @Test
    fun `hubs and universal downloader viewmodels initialize cleanly without vpn`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val hubsVm = HubsViewModel(app)
        val universalVm = UniversalDownloaderViewModel(app)

        assertNotNull(hubsVm.gitHubState.value)
        assertNotNull(hubsVm.socialState.value)
        assertNotNull(universalVm.extractionState.value)
        assertTrue(universalVm.selectedIds.value.isEmpty())
    }
}
