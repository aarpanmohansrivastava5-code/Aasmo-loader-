package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.files.AasmoFileManager
import com.example.data.files.FileCategory
import com.example.data.files.FileDetailReport
import com.example.data.files.FileSortMode
import com.example.data.files.ManagedFileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val fileManager = AasmoFileManager(application)

    private val _files = MutableStateFlow<List<ManagedFileItem>>(emptyList())
    val files: StateFlow<List<ManagedFileItem>> = _files.asStateFlow()

    private val _folders = MutableStateFlow<List<File>>(emptyList())
    val folders: StateFlow<List<File>> = _folders.asStateFlow()

    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    private val _category = MutableStateFlow(FileCategory.ALL)
    val category: StateFlow<FileCategory> = _category.asStateFlow()

    private val _sortMode = MutableStateFlow(FileSortMode.DATE_DESC)
    val sortMode: StateFlow<FileSortMode> = _sortMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _detailReport = MutableStateFlow<FileDetailReport?>(null)
    val detailReport: StateFlow<FileDetailReport?> = _detailReport.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _files.value = fileManager.listFiles(
                currentFolderPath = _currentFolder.value,
                category = _category.value,
                sortMode = _sortMode.value,
                query = _searchQuery.value,
            )
            _folders.value = fileManager.listAvailableFolders()
        }
    }

    fun selectCategory(cat: FileCategory) {
        _category.value = cat
        if (cat != FileCategory.ALL) _currentFolder.value = null
        refresh()
    }

    fun selectSort(sort: FileSortMode) {
        _sortMode.value = sort
        refresh()
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        refresh()
    }

    fun openFolder(path: String) {
        _category.value = FileCategory.ALL
        _currentFolder.value = path
        refresh()
    }

    fun goUpFolder() {
        val current = _currentFolder.value ?: return
        val parent = File(current).parentFile
        _currentFolder.value = if (parent != null && parent.absolutePath.startsWith(fileManager.rootDir.absolutePath)) {
            if (parent.absolutePath == fileManager.rootDir.absolutePath) null else parent.absolutePath
        } else null
        refresh()
    }

    fun createFolder(name: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            fileManager.createFolder(_currentFolder.value, name)
                .onSuccess { onMessage("Created folder ${it.name}"); refresh() }
                .onFailure { onMessage(it.localizedMessage ?: "Failed to create folder") }
        }
    }

    fun renameFile(path: String, newName: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            fileManager.renameFile(path, newName)
                .onSuccess { onMessage("Renamed to ${it.name}"); refresh() }
                .onFailure { onMessage(it.localizedMessage ?: "Rename failed") }
        }
    }

    fun copyOrMove(path: String, targetFolder: String, move: Boolean, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            fileManager.copyOrMoveFile(path, targetFolder, move)
                .onSuccess {
                    onMessage(if (move) "Moved to ${it.parentFile?.name}" else "Copied to ${it.parentFile?.name}")
                    refresh()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Operation failed") }
        }
    }

    fun deleteFile(path: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val ok = fileManager.deletePath(path)
            onMessage(if (ok) "Deleted" else "Delete failed")
            refresh()
        }
    }

    fun inspectFile(item: ManagedFileItem) {
        viewModelScope.launch {
            _detailReport.value = fileManager.inspectDetails(item)
        }
    }

    fun dismissDetails() {
        _detailReport.value = null
    }

    fun saveReaderArticle(title: String, body: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            fileManager.saveReaderDocument(title, body)
                .onSuccess { onMessage("Saved article: ${it.name}"); refresh() }
                .onFailure { onMessage(it.localizedMessage ?: "Could not save article") }
        }
    }

    fun buildOpenIntent(item: ManagedFileItem): Intent? = fileManager.buildOpenIntent(item)
    fun buildShareIntent(item: ManagedFileItem): Intent? = fileManager.buildShareIntent(item)
    fun rootPath(): String = fileManager.rootDir.absolutePath
}
