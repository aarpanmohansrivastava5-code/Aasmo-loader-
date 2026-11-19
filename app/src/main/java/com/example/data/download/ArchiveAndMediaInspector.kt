package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class ZipEntrySummary(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
)

object ArchiveAndMediaInspector {

    fun computeSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                while (fis.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    suspend fun exportToPublicDownloads(
        context: Context,
        entity: DownloadEntity,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(entity.localPath)
            require(src.exists()) { "Downloaded file no longer exists on disk." }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, entity.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, entity.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AasmoLoader")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create MediaStore Downloads entry.")
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(src).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Failed to open output stream.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    src,
                )
                uri.toString()
            }
        }
    }

    fun buildOpenIntent(context: Context, entity: DownloadEntity): Intent? {
        val file = File(entity.localPath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entity.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildShareIntent(context: Context, entity: DownloadEntity): Intent? {
        val file = File(entity.localPath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = entity.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, entity.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun listZipEntries(file: File): List<ZipEntrySummary> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val entries = mutableListOf<ZipEntrySummary>()
        runCatching {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null && entries.size < 120) {
                    entries.add(
                        ZipEntrySummary(
                            name = entry.name,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory,
                        )
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        entries
    }

    suspend fun readTextPreview(file: File, maxChars: Int = 8000): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() > 512 * 1024L) return@withContext null
        runCatching { file.readText().take(maxChars) }.getOrNull()
    }
}
