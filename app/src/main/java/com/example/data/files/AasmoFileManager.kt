package com.example.data.files

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.download.ArchiveAndMediaInspector
import com.example.data.download.ZipEntrySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FileCategory(val label: String) {
    ALL("All Files"),
    RECENT("Recent"),
    DOWNLOADS("Downloads"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    OTHER("Other"),
}

enum class FileSortMode(val label: String) {
    DATE_DESC("Newest"),
    NAME_ASC("Name A-Z"),
    SIZE_DESC("Largest"),
    TYPE_ASC("Type"),
}

data class ManagedFileItem(
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val relativeFolder: String,
)

data class FileDetailReport(
    val item: ManagedFileItem,
    val sha256: String,
    val zipEntries: List<ZipEntrySummary>,
    val textPreview: String?,
)

class AasmoFileManager(private val context: Context) {

    val rootDir: File by lazy {
        File(context.filesDir, "downloads").apply { mkdirs() }
    }

    val documentsDir: File by lazy {
        File(rootDir, "SavedArticles").apply { mkdirs() }
    }

    suspend fun listFiles(
        currentFolderPath: String? = null,
        category: FileCategory = FileCategory.ALL,
        sortMode: FileSortMode = FileSortMode.DATE_DESC,
        query: String = "",
    ): List<ManagedFileItem> = withContext(Dispatchers.IO) {
        val baseDir = currentFolderPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory } ?: rootDir

        val rawFiles: List<File> = if (category == FileCategory.ALL && currentFolderPath != null) {
            baseDir.listFiles()?.toList().orEmpty()
        } else {
            rootDir.walkTopDown().filter { it != rootDir }.toList()
        }

        val mapped = rawFiles.mapNotNull { f ->
            if (!f.exists()) return@mapNotNull null
            val ext = if (f.isDirectory) "" else f.extension.lowercase()
            val mime = if (f.isDirectory) "inode/directory" else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
            val rel = f.parentFile?.absolutePath?.removePrefix(rootDir.absolutePath)?.trim('/') ?: ""
            ManagedFileItem(
                path = f.absolutePath,
                name = f.name,
                extension = ext,
                mimeType = mime,
                sizeBytes = if (f.isDirectory) (f.listFiles()?.size?.toLong() ?: 0L) else f.length(),
                lastModified = f.lastModified(),
                isDirectory = f.isDirectory,
                relativeFolder = rel.ifBlank { "Downloads" },
            )
        }

        val filtered = mapped.filter { item ->
            val catMatch = when (category) {
                FileCategory.ALL -> true
                FileCategory.RECENT -> !item.isDirectory
                FileCategory.DOWNLOADS -> item.relativeFolder == "Downloads"
                FileCategory.IMAGES -> item.mimeType.startsWith("image/")
                FileCategory.VIDEOS -> item.mimeType.startsWith("video/")
                FileCategory.AUDIO -> item.mimeType.startsWith("audio/")
                FileCategory.DOCUMENTS -> item.extension in setOf("pdf", "txt", "md", "doc", "docx", "csv", "json", "html")
                FileCategory.ARCHIVES -> item.extension in setOf("zip", "tar", "gz", "rar", "7z", "apk")
                FileCategory.OTHER -> !item.isDirectory &&
                    !item.mimeType.startsWith("image/") &&
                    !item.mimeType.startsWith("video/") &&
                    !item.mimeType.startsWith("audio/") &&
                    item.extension !in setOf("pdf", "txt", "md", "doc", "docx", "csv", "json", "html", "zip", "tar", "gz", "rar", "7z", "apk")
            }
            val q = query.trim()
            val queryMatch = q.isBlank() ||
                item.name.contains(q, ignoreCase = true) ||
                item.name.replace('_', ' ').replace('-', ' ').contains(q, ignoreCase = true)
            catMatch && queryMatch
        }

        val sorted = when (sortMode) {
            FileSortMode.DATE_DESC -> filtered.sortedWith(compareByDescending<ManagedFileItem> { it.isDirectory }.thenByDescending { it.lastModified })
            FileSortMode.NAME_ASC -> filtered.sortedWith(compareByDescending<ManagedFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
            FileSortMode.SIZE_DESC -> filtered.sortedWith(compareByDescending<ManagedFileItem> { it.isDirectory }.thenByDescending { it.sizeBytes })
            FileSortMode.TYPE_ASC -> filtered.sortedWith(compareByDescending<ManagedFileItem> { it.isDirectory }.thenBy { it.extension })
        }

        if (category == FileCategory.RECENT) sorted.take(25) else sorted
    }

    suspend fun listAvailableFolders(): List<File> = withContext(Dispatchers.IO) {
        listOf(rootDir) + rootDir.walkTopDown().filter { it.isDirectory && it != rootDir }.toList()
    }

    suspend fun createFolder(parentPath: String?, folderName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = folderName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            require(clean.isNotBlank()) { "Folder name cannot be empty." }
            val parent = parentPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory } ?: rootDir
            val created = File(parent, clean)
            require(!created.exists()) { "Folder already exists." }
            check(created.mkdirs()) { "Could not create folder." }
            created
        }
    }

    suspend fun renameFile(path: String, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(path)
            require(src.exists()) { "File not found." }
            val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            require(clean.isNotBlank()) { "Name cannot be empty." }
            val dest = File(src.parentFile ?: rootDir, clean)
            require(!dest.exists()) { "Target name already exists." }
            check(src.renameTo(dest)) { "Rename failed." }
            dest
        }
    }

    suspend fun copyOrMoveFile(path: String, targetFolderPath: String, move: Boolean): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(path)
            require(src.exists() && src.isFile) { "Source file unavailable." }
            val folder = File(targetFolderPath).also { it.mkdirs() }
            val dest = File(folder, src.name)
            require(src.absolutePath != dest.absolutePath) { "Source and destination are identical." }
            src.copyTo(dest, overwrite = false)
            if (move) src.delete()
            dest
        }
    }

    suspend fun deletePath(path: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(path)
        if (!target.exists() || target == rootDir) return@withContext false
        target.deleteRecursively()
    }

    suspend fun inspectDetails(item: ManagedFileItem): FileDetailReport = withContext(Dispatchers.IO) {
        val file = File(item.path)
        val sha = if (file.isFile) ArchiveAndMediaInspector.computeSha256(file) else ""
        val zips = if (file.isFile && item.extension == "zip") {
            ArchiveAndMediaInspector.listZipEntries(file)
        } else {
            emptyList()
        }
        val text = if (file.isFile && zips.isEmpty() && item.extension in setOf("txt", "md", "json", "csv", "log", "html")) {
            ArchiveAndMediaInspector.readTextPreview(file)
        } else null
        FileDetailReport(item = item, sha256 = sha, zipEntries = zips, textPreview = text)
    }

    suspend fun saveReaderDocument(title: String, body: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safe = title.trim().replace(Regex("[^a-zA-Z0-9._-]+"), "_").take(45).ifBlank { "Article" }
            val target = File(documentsDir, "${safe}_${System.currentTimeMillis() % 10000}.txt")
            target.writeText("$title\n\n$body")
            target
        }
    }

    fun buildOpenIntent(item: ManagedFileItem): Intent? {
        val file = File(item.path)
        if (!file.exists() || file.isDirectory) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildShareIntent(item: ManagedFileItem): Intent? {
        val file = File(item.path)
        if (!file.exists() || file.isDirectory) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
