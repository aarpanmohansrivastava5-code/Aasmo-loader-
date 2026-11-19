package com.example.data.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.DuplicateBehavior
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadSource
import com.example.data.local.DownloadStatus
import com.example.data.media.MediaMetadataExtractor
import com.example.data.media.ProbeVerdict
import com.example.data.media.UrlSecurityPolicy
import com.example.data.media.UrlSecurityValidator
import com.example.data.media.UrlValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray

class PacketDownloadEngine(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val securityPolicy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(UrlSecurityValidator.SafeDns(securityPolicy))
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "AasmoLoader-Android/2.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val downloadDir: File by lazy {
        File(context.filesDir, "downloads").apply { mkdirs() }
    }

    init {
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aasmo Transfers",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background packet-division download progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isOnUnmeteredWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun enqueueDownload(
        rawUrl: String,
        title: String? = null,
        source: DownloadSource = DownloadSource.DIRECT,
        chunkCount: Int = 8,
        notificationsEnabled: Boolean = true,
        wifiOnly: Boolean = false,
        maxConcurrent: Int = 2,
        duplicateBehavior: DuplicateBehavior = DuplicateBehavior.RENAME,
        autoRetry: Boolean = true,
        batchId: String = "",
        onDuplicateSkipped: ((String) -> Unit)? = null,
    ) {
        val normalizedUrl = normalizeUrl(rawUrl)
        val urlValidation = UrlSecurityValidator.validateSyntaxAndHost(normalizedUrl, securityPolicy)
        val rawFileName = deriveFileName(normalizedUrl, title)
        val displayTitle = title?.takeIf { it.isNotBlank() } ?: rawFileName
        val safeChunks = chunkCount.coerceIn(2, 16)
        val initialCsv = List(safeChunks) { "0" }.joinToString(",")

        scope.launch {
            if (urlValidation is UrlValidationResult.Rejected) {
                val failedEntity = DownloadEntity(
                    title = displayTitle,
                    url = rawUrl.trim(),
                    originalUrl = rawUrl.trim(),
                    fileName = rawFileName,
                    source = source.name,
                    mimeType = guessMimeType(rawFileName),
                    chunkCount = safeChunks,
                    chunkProgressCsv = initialCsv,
                    status = DownloadStatus.FAILED.name,
                    errorMessage = urlValidation.reason,
                    batchId = batchId,
                )
                downloadDao.insert(failedEntity)
                onDuplicateSkipped?.invoke(urlValidation.reason)
                return@launch
            }

            val existing = downloadDao.findByUrl(normalizedUrl)
            if (existing != null && duplicateBehavior == DuplicateBehavior.SKIP) {
                onDuplicateSkipped?.invoke("Skipped duplicate: ${existing.fileName}")
                return@launch
            }
            if (existing != null && duplicateBehavior == DuplicateBehavior.REPLACE) {
                activeJobs.remove(existing.id)?.cancel()
                runCatching { File(existing.localPath).delete() }
                downloadDao.deleteById(existing.id)
            }

            val finalFileName = resolveUniqueFileName(rawFileName, duplicateBehavior == DuplicateBehavior.REPLACE)
            val blockedForWifi = wifiOnly && !isOnUnmeteredWifi()
            val entity = DownloadEntity(
                title = displayTitle,
                url = normalizedUrl,
                originalUrl = rawUrl.trim(),
                fileName = finalFileName,
                source = source.name,
                mimeType = guessMimeType(finalFileName),
                chunkCount = safeChunks,
                chunkProgressCsv = initialCsv,
                status = if (blockedForWifi) DownloadStatus.PAUSED.name else DownloadStatus.QUEUED.name,
                errorMessage = if (blockedForWifi) "Waiting for Wi-Fi (Wi-Fi Only enabled)" else null,
                localPath = File(downloadDir, finalFileName).absolutePath,
                batchId = batchId,
                maxRetries = if (autoRetry) 2 else 0,
            )
            downloadDao.insert(entity)
            pumpQueue(notificationsEnabled, wifiOnly, maxConcurrent)
        }
    }

    fun pauseDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        scope.launch {
            val existing = downloadDao.getById(id) ?: return@launch
            if (existing.status == DownloadStatus.DOWNLOADING.name || existing.status == DownloadStatus.QUEUED.name) {
                downloadDao.update(
                    existing.copy(
                        status = DownloadStatus.PAUSED.name,
                        speedBytesPerSec = 0L,
                    )
                )
                notificationManager.cancel(id.toInt())
            }
        }
    }

    fun cancelAndFailDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        notificationManager.cancel(id.toInt())
        scope.launch {
            val existing = downloadDao.getById(id) ?: return@launch
            downloadDao.update(
                existing.copy(
                    status = DownloadStatus.FAILED.name,
                    speedBytesPerSec = 0L,
                    errorMessage = "Cancelled by user",
                )
            )
        }
    }

    fun resumeDownload(
        id: Long,
        notificationsEnabled: Boolean = true,
        wifiOnly: Boolean = false,
        maxConcurrent: Int = 2,
    ) {
        scope.launch {
            val existing = downloadDao.getById(id) ?: return@launch
            downloadDao.update(
                existing.copy(
                    status = DownloadStatus.QUEUED.name,
                    errorMessage = null,
                    retryCount = 0,
                )
            )
            pumpQueue(notificationsEnabled, wifiOnly, maxConcurrent)
        }
    }

    fun deleteDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        notificationManager.cancel(id.toInt())
        scope.launch {
            val existing = downloadDao.getById(id)
            if (existing != null && existing.localPath.isNotBlank()) {
                runCatching { File(existing.localPath).delete() }
            }
            downloadDao.deleteById(id)
        }
    }

    private suspend fun pumpQueue(notificationsEnabled: Boolean, wifiOnly: Boolean, maxConcurrent: Int) {
        val activeCount = downloadDao.countActive()
        val slots = (maxConcurrent.coerceIn(1, 5) - activeCount).coerceAtLeast(0)
        repeat(slots) {
            val next = downloadDao.nextQueued() ?: return
            if (!activeJobs.containsKey(next.id)) {
                startTransfer(next.id, notificationsEnabled, wifiOnly, maxConcurrent)
            }
        }
    }

    private fun startTransfer(
        id: Long,
        notificationsEnabled: Boolean,
        wifiOnly: Boolean,
        maxConcurrent: Int,
    ) {
        activeJobs[id]?.cancel()
        val job = scope.launch {
            val initial = downloadDao.getById(id) ?: return@launch
            if (wifiOnly && !isOnUnmeteredWifi()) {
                downloadDao.update(
                    initial.copy(
                        status = DownloadStatus.PAUSED.name,
                        speedBytesPerSec = 0L,
                        errorMessage = "Paused: Waiting for Wi-Fi",
                    )
                )
                return@launch
            }

            val outFile = File(initial.localPath.ifBlank { File(downloadDir, initial.fileName).absolutePath })
            outFile.parentFile?.mkdirs()
            val chunks = initial.chunkCount.coerceIn(2, 16)

            try {
                downloadDao.update(
                    initial.copy(
                        status = DownloadStatus.DOWNLOADING.name,
                        errorMessage = null,
                        localPath = outFile.absolutePath,
                    )
                )

                val probe = probeRemoteFile(initial.url)
                if (probe.isBlockedSecurity) {
                    throw SecurityException(probe.errorDetail ?: "Blocked unsafe URL or redirect target.")
                }
                if (probe.isHtml && expectsBinaryOrMedia(initial.fileName)) {
                    throw IllegalStateException("Endpoint returned HTML webpage instead of direct media (${probe.mimeType}).")
                }

                val transferUrl = probe.finalUrl.ifBlank { initial.url }
                val totalBytes = probe.contentLength
                val supportsRanges = probe.acceptRanges && totalBytes > 256 * 1024L && verifyRangeSupport(transferUrl)

                if (supportsRanges) {
                    downloadMultiChunk(
                        id = id,
                        url = transferUrl,
                        outFile = outFile,
                        totalBytes = totalBytes,
                        chunkCount = chunks,
                        notificationsEnabled = notificationsEnabled,
                    )
                } else {
                    downloadSingleStreamWithPacketTelemetry(
                        id = id,
                        url = transferUrl,
                        outFile = outFile,
                        knownTotalBytes = totalBytes,
                        chunkCount = chunks,
                        notificationsEnabled = notificationsEnabled,
                    )
                }

                val latest = downloadDao.getById(id) ?: return@launch
                val finalSize = outFile.length()
                if (finalSize <= 0L) {
                    throw IllegalStateException("Downloaded file was empty (0 bytes).")
                }
                val fullCsv = List(chunks) { "100" }.joinToString(",")
                val hash = ArchiveAndMediaInspector.computeSha256(outFile)

                downloadDao.update(
                    latest.copy(
                        totalBytes = if (latest.totalBytes > 0) latest.totalBytes else finalSize,
                        downloadedBytes = finalSize,
                        speedBytesPerSec = 0L,
                        chunkProgressCsv = fullCsv,
                        status = DownloadStatus.COMPLETED.name,
                        localPath = outFile.absolutePath,
                        mimeType = probe.mimeType.ifBlank { latest.mimeType },
                        sha256 = hash,
                        errorMessage = null,
                    )
                )
                postNotification(
                    id = id,
                    title = latest.title,
                    progress = 100,
                    indeterminate = false,
                    completed = true,
                    enabled = notificationsEnabled,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latest = downloadDao.getById(id)
                if (latest != null) {
                    val canRetry = latest.retryCount < latest.maxRetries &&
                        e !is SecurityException &&
                        !e.message.orEmpty().contains("HTML webpage")
                    if (canRetry) {
                        downloadDao.update(
                            latest.copy(
                                retryCount = latest.retryCount + 1,
                                status = DownloadStatus.QUEUED.name,
                                speedBytesPerSec = 0L,
                                errorMessage = "Retrying (${latest.retryCount + 1}/${latest.maxRetries})...",
                            )
                        )
                        delay(1500L)
                    } else {
                        downloadDao.update(
                            latest.copy(
                                status = DownloadStatus.FAILED.name,
                                speedBytesPerSec = 0L,
                                errorMessage = e.localizedMessage ?: "Transfer interrupted",
                            )
                        )
                        notificationManager.cancel(id.toInt())
                    }
                }
            } finally {
                activeJobs.remove(id)
                pumpQueue(notificationsEnabled, wifiOnly, maxConcurrent)
            }
        }
        activeJobs[id] = job
    }

    private data class RemoteProbe(
        val finalUrl: String,
        val contentLength: Long,
        val acceptRanges: Boolean,
        val mimeType: String,
        val isHtml: Boolean,
        val isBlockedSecurity: Boolean = false,
        val errorDetail: String? = null,
    )

    private fun probeRemoteFile(url: String): RemoteProbe {
        val result = MediaMetadataExtractor.probeMediaTarget(url, securityPolicy)
        val blockedSecurity = result.verdict == ProbeVerdict.VERIFICATION_FAILED &&
            result.errorDetail.orEmpty().contains("Blocked", ignoreCase = true)
        return RemoteProbe(
            finalUrl = result.finalUrl,
            contentLength = result.contentLength,
            acceptRanges = result.acceptsRanges,
            mimeType = result.mimeType,
            isHtml = result.isHtml,
            isBlockedSecurity = blockedSecurity,
            errorDetail = result.errorDetail,
        )
    }

    private fun verifyRangeSupport(url: String): Boolean {
        return runCatching {
            val req = Request.Builder().url(url).header("Range", "bytes=0-1023").build()
            UrlSecurityValidator.executeWithSafeRedirects(httpClient, req, securityPolicy).use { resp ->
                resp.code == 206
            }
        }.getOrDefault(false)
    }

    private suspend fun downloadMultiChunk(
        id: Long,
        url: String,
        outFile: File,
        totalBytes: Long,
        chunkCount: Int,
        notificationsEnabled: Boolean,
    ) = coroutineScope {
        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(totalBytes)
        }
        val chunkBytes = AtomicLongArray(chunkCount)
        val chunkLengths = LongArray(chunkCount)
        val baseSize = totalBytes / chunkCount

        val startedAt = System.currentTimeMillis()
        var lastUiUpdate = startedAt
        var lastBytesSnapshot = 0L

        val updater = launch {
            while (true) {
                delay(350)
                val now = System.currentTimeMillis()
                var sum = 0L
                val pctList = ArrayList<String>(chunkCount)
                for (i in 0 until chunkCount) {
                    val b = chunkBytes.get(i)
                    sum += b
                    val target = chunkLengths[i].coerceAtLeast(1L)
                    pctList.add(((b * 100L) / target).coerceIn(0L, 100L).toString())
                }
                val dtMs = (now - lastUiUpdate).coerceAtLeast(1L)
                val speed = ((sum - lastBytesSnapshot) * 1000L) / dtMs
                lastUiUpdate = now
                lastBytesSnapshot = sum

                val current = downloadDao.getById(id) ?: break
                downloadDao.update(
                    current.copy(
                        totalBytes = totalBytes,
                        downloadedBytes = sum,
                        speedBytesPerSec = speed.coerceAtLeast(0L),
                        chunkProgressCsv = pctList.joinToString(","),
                    )
                )
                val totalPct = ((sum * 100L) / totalBytes.coerceAtLeast(1L)).toInt().coerceIn(0, 99)
                postNotification(id, current.title, totalPct, indeterminate = false, completed = false, enabled = notificationsEnabled)
            }
        }

        try {
            val jobs = (0 until chunkCount).map { idx ->
                val start = idx * baseSize
                val end = if (idx == chunkCount - 1) totalBytes - 1 else (start + baseSize - 1)
                chunkLengths[idx] = (end - start + 1)

                async(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url(url)
                        .header("Range", "bytes=$start-$end")
                        .build()
                    UrlSecurityValidator.executeWithSafeRedirects(httpClient, req, securityPolicy).use { resp ->
                        if (resp.code != 206 && !resp.isSuccessful) {
                            throw IllegalStateException("HTTP ${resp.code} on chunk $idx")
                        }
                        val body = resp.body ?: throw IllegalStateException("Empty body on chunk $idx")
                        body.byteStream().use { input ->
                            RandomAccessFile(outFile, "rw").use { raf ->
                                raf.seek(start)
                                val buf = ByteArray(16 * 1024)
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    raf.write(buf, 0, read)
                                    chunkBytes.addAndGet(idx, read.toLong())
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        } finally {
            updater.cancel()
        }
    }

    private suspend fun downloadSingleStreamWithPacketTelemetry(
        id: Long,
        url: String,
        outFile: File,
        knownTotalBytes: Long,
        chunkCount: Int,
        notificationsEnabled: Boolean,
    ) {
        val existingBytes = if (outFile.exists()) outFile.length() else 0L
        val reqBuilder = Request.Builder().url(url)
        val resumeRequested = existingBytes > 0L && knownTotalBytes > existingBytes
        if (resumeRequested) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        UrlSecurityValidator.executeWithSafeRedirects(httpClient, reqBuilder.build(), securityPolicy).use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            val respMime = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (respMime.contains("text/html") && expectsBinaryOrMedia(outFile.name)) {
                throw IllegalStateException("Endpoint returned HTML instead of downloadable media ($respMime).")
            }

            val isPartial = resp.code == 206
            val append = resumeRequested && isPartial
            val contentLen = resp.body?.contentLength() ?: -1L
            val total = when {
                isPartial && contentLen > 0 -> existingBytes + contentLen
                contentLen > 0 -> contentLen
                knownTotalBytes > 0 -> knownTotalBytes
                else -> -1L
            }

            val body = resp.body ?: throw IllegalStateException("Empty response body")
            var downloaded = if (append) existingBytes else 0L
            var lastTick = System.currentTimeMillis()
            var lastTickBytes = downloaded

            body.byteStream().use { input ->
                java.io.FileOutputStream(outFile, append).use { fos ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        fos.write(buf, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 350L) {
                            val speed = ((downloaded - lastTickBytes) * 1000L) / (now - lastTick).coerceAtLeast(1L)
                            lastTick = now
                            lastTickBytes = downloaded
                            val pct = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 99) else 0
                            val chunkCsv = buildDistributedChunkCsv(pct, chunkCount)
                            val current = downloadDao.getById(id)
                            if (current != null) {
                                downloadDao.update(
                                    current.copy(
                                        totalBytes = if (total > 0) total else 0L,
                                        downloadedBytes = downloaded,
                                        speedBytesPerSec = speed.coerceAtLeast(0L),
                                        chunkProgressCsv = chunkCsv,
                                    )
                                )
                                postNotification(
                                    id = id,
                                    title = current.title,
                                    progress = pct,
                                    indeterminate = total <= 0,
                                    completed = false,
                                    enabled = notificationsEnabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildDistributedChunkCsv(overallPct: Int, chunkCount: Int): String {
        val totalUnits = overallPct * chunkCount
        return (0 until chunkCount).joinToString(",") { idx ->
            ((totalUnits - idx * 100).coerceIn(0, 100)).toString()
        }
    }

    private fun postNotification(
        id: Long,
        title: String,
        progress: Int,
        indeterminate: Boolean,
        completed: Boolean,
        enabled: Boolean,
    ) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (completed) "Download Complete" else title)
            .setContentText(
                when {
                    completed -> "$title saved"
                    indeterminate -> "Downloading • Real byte stream"
                    else -> "$progress% • Packet transfer"
                }
            )
            .setOnlyAlertOnce(true)
            .setOngoing(!completed)
            .setAutoCancel(completed)

        if (!completed) {
            builder.setProgress(100, progress, indeterminate)
        }
        notificationManager.notify(id.toInt(), builder.build())
    }

    private fun expectsBinaryOrMedia(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "webm", "m4v", "mp3", "wav", "ogg", "jpg", "jpeg", "png", "webp", "gif", "zip", "apk", "pdf")
    }

    private fun resolveUniqueFileName(raw: String, overwrite: Boolean): String {
        val target = File(downloadDir, raw)
        if (overwrite || !target.exists()) return raw
        val dot = raw.lastIndexOf('.')
        val base = if (dot > 0) raw.substring(0, dot) else raw
        val ext = if (dot > 0) raw.substring(dot) else ""
        var counter = 1
        while (counter < 999) {
            val candidate = "${base}_($counter)$ext"
            if (!File(downloadDir, candidate).exists()) return candidate
            counter++
        }
        return "${System.currentTimeMillis()}_$raw"
    }

    companion object {
        private const val CHANNEL_ID = "aasmo_downloads"

        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
            val hasExplicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*").matches(trimmed)
            return if (hasExplicitScheme) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        fun deriveFileName(url: String, title: String?): String {
            val cleanPath = url.substringBefore('?').substringBefore('#')
            val urlCandidate = cleanPath.substringAfterLast('/').trim()
            val urlExt = urlCandidate.substringAfterLast('.', "")
                .takeIf { it.isNotBlank() && it.length <= 5 && urlCandidate.contains('.') }

            if (!title.isNullOrBlank()) {
                val safeTitle = title.trim()
                    .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
                    .trim('_')
                    .take(52)
                if (safeTitle.isNotBlank()) {
                    val hasExt = safeTitle.substringAfterLast('.', "").length in 2..5 && safeTitle.contains('.')
                    return when {
                        hasExt -> safeTitle
                        urlExt != null -> "$safeTitle.$urlExt"
                        else -> "$safeTitle.bin"
                    }
                }
            }

            if (urlCandidate.isNotBlank() && urlCandidate.contains('.')) {
                return urlCandidate.take(64).replace(Regex("[^a-zA-Z0-9._-]"), "_")
            }
            return "aasmo_${System.currentTimeMillis() % 100000}.bin"
        }

        fun guessMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
