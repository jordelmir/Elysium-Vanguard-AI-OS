package com.elysium.vanguard.features.filemanager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.core.ai.DownloadState
import com.elysium.vanguard.core.ai.MediaPipeManager
import com.elysium.vanguard.core.ai.ModelDownloadManager
import com.elysium.vanguard.core.search.GlobalSearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.BroadcastReceiver
import android.content.IntentFilter
import javax.inject.Inject

import android.app.Application
import com.elysium.vanguard.core.util.StorageStats

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val repository: FileManagerRepository,
    private val modelManager: ModelDownloadManager,
    private val mediaPipeManager: MediaPipeManager,
    private val app: Application
) : ViewModel() {
    private val context get() = app.applicationContext

    private val _currentPath = MutableStateFlow<String>(android.os.Environment.getExternalStorageDirectory().absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _uiState = MutableStateFlow<FileManagerUiState>(FileManagerUiState.Loading)
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private val _files = MutableStateFlow<List<TitanFile>>(emptyList())
    val files: StateFlow<List<TitanFile>> = _files.asStateFlow()

    private val _aiState = MutableStateFlow<String>("") // Response text or empty
    val aiState: StateFlow<String> = _aiState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _operationProgress = MutableStateFlow<Float?>(null)
    val operationProgress: StateFlow<Float?> = _operationProgress.asStateFlow()

    private val _events = MutableSharedFlow<FileManagerEvent>()
    val events: SharedFlow<FileManagerEvent> = _events.asSharedFlow()

    private val _pendingOperation = MutableStateFlow<PendingFileOperation?>(null)
    val pendingOperation: StateFlow<PendingFileOperation?> = _pendingOperation.asStateFlow()

    private val _commonShortcuts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val commonShortcuts: StateFlow<List<Pair<String, String>>> = _commonShortcuts.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _compressionProgress = MutableStateFlow<CompressionState?>(null)
    val compressionProgress: StateFlow<CompressionState?> = _compressionProgress.asStateFlow()

    private val _extractionProgress = MutableStateFlow<CompressionState?>(null)
    val extractionProgress: StateFlow<CompressionState?> = _extractionProgress.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _viewMode = MutableStateFlow(FileViewMode.TACTICAL) // Default to Tactical as requested
    val viewMode: StateFlow<FileViewMode> = _viewMode.asStateFlow()

    // ── GLOBAL SEARCH ───────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHits = MutableStateFlow<List<GlobalSearchEngine.Hit>>(emptyList())
    val searchHits: StateFlow<List<GlobalSearchEngine.Hit>> = _searchHits.asStateFlow()

    private val _searchProgress = MutableStateFlow<GlobalSearchEngine.Progress?>(null)
    val searchProgress: StateFlow<GlobalSearchEngine.Progress?> = _searchProgress.asStateFlow()

    private val _searchScopeWholeDevice = MutableStateFlow(false)
    val searchScopeWholeDevice: StateFlow<Boolean> = _searchScopeWholeDevice.asStateFlow()

    private var searchJob: Job? = null

    // ── DELETION PROGRESS ───────────────────────────────────────────────────

    private val _deletionProgress = MutableStateFlow<DeletionState?>(null)
    val deletionProgress: StateFlow<DeletionState?> = _deletionProgress.asStateFlow()

    /** Live state of a copy or move run. */
    private val _transferProgress = MutableStateFlow<TransferState?>(null)
    val transferProgress: StateFlow<TransferState?> = _transferProgress.asStateFlow()

    /** Handle of the in-flight directory scan. Cancelled on every navigation. */
    private var loadJob: Job? = null

    /** Handle of the background folder-count enrichment pass. */
    private var countJob: Job? = null

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val percentage = intent?.getIntExtra("percentage", 0) ?: 0
            val currentFile = intent?.getStringExtra("currentFile") ?: ""
            val done = intent?.getBooleanExtra("done", false) ?: false

            if (done) {
                _compressionProgress.value = CompressionState(100, "COMPLETE", CompressionStatus.SUCCESS)
                updateStorageStats()
                loadDirectory(_currentPath.value)

                // Auto-dismiss after 2 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (_compressionProgress.value?.status == CompressionStatus.SUCCESS) {
                        _compressionProgress.value = null
                    }
                }
            } else {
                _compressionProgress.value = CompressionState(percentage, currentFile, CompressionStatus.RUNNING)
            }
        }
    }

    /**
     * Receives progress from the universal archive engine.
     */
    private val archiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val percentage = intent?.getIntExtra("percentage", 0) ?: 0
            val currentFile = intent?.getStringExtra("currentFile") ?: ""
            val done = intent?.getBooleanExtra("done", false) ?: false

            if (done) {
                if (percentage < 0) {
                    _extractionProgress.value = CompressionState(
                        0,
                        currentFile,
                        CompressionStatus.ERROR
                    )
                } else {
                    _extractionProgress.value = CompressionState(100, "COMPLETE", CompressionStatus.SUCCESS)
                }
                updateStorageStats()
                loadDirectory(_currentPath.value)
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2500)
                    if (_extractionProgress.value != null) _extractionProgress.value = null
                }
            } else {
                _extractionProgress.value = CompressionState(percentage, currentFile, CompressionStatus.RUNNING)
            }
        }
    }

    init {
        checkPermissionsAndLoad()
        updateStorageStats()
        loadShortcuts()

        try {
            ContextCompat.registerReceiver(
                context,
                progressReceiver,
                IntentFilter("com.elysium.vanguard.COMPRESSION_PROGRESS"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                archiveReceiver,
                IntentFilter(com.elysium.vanguard.core.services.ArchiveService.BROADCAST_PROGRESS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(progressReceiver)
        } catch (_: Exception) {}
        try {
            context.unregisterReceiver(archiveReceiver)
        } catch (_: Exception) {}
    }

    private fun loadShortcuts() {
        _commonShortcuts.value = repository.getCommonShortcuts()
    }

    fun updateStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun checkPermissionsAndLoad() {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true // Legacy handling usually done at Activity level
        }

        if (hasPermission) {
            loadDirectory(_currentPath.value)
        } else {
            _uiState.value = FileManagerUiState.PermissionRequired
        }
    }

    fun loadDirectory(path: String) {
        if (path.isEmpty()) return
        _currentPath.value = path
        _selectedFiles.value = emptySet() // Clear selection on navigate

        _uiState.value = FileManagerUiState.Loading

        // RACE FIX: a previous scan must never be allowed to win over a newer one.
        // Without this, navigating quickly left stale listings on screen.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                repository.getFiles(path).collect { list ->
                    if (path != _currentPath.value) return@collect // stale emission guard
                    _files.value = list
                    _uiState.value = if (list.isEmpty()) {
                        FileManagerUiState.Empty
                    } else {
                        FileManagerUiState.Success(list)
                    }
                    enrichFolderCounts(list)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = FileManagerUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    /**
     * Resolves folder child counts off the critical path so the first paint is
     * immediate and the counts stream in progressively.
     */
    private fun enrichFolderCounts(list: List<TitanFile>) {
        val folders = list.filter { it.isFolder }
        if (folders.isEmpty()) return
        countJob?.cancel()
        countJob = viewModelScope.launch(Dispatchers.IO) {
            val counts = HashMap<String, Int>(folders.size)
            val sizes = HashMap<String, Long>(folders.size)
            for (folder in folders) {
                val count = repository.countChildren(folder.path)
                if (count >= 0) counts[folder.path] = count
                // The real size of the folder, shown next to the date.
                sizes[folder.path] = repository.folderSizeBytes(folder.path)
                if (counts.size % 8 == 0) publishCounts(counts, sizes)
            }
            publishCounts(counts, sizes)
        }
    }

    private suspend fun publishCounts(counts: Map<String, Int>, sizes: Map<String, Long> = emptyMap()) {
        if (counts.isEmpty() && sizes.isEmpty()) return
        _files.update { current ->
            if (current.isEmpty()) return@update current
            var changed = false
            val updated = current.map { file ->
                val count = counts[file.path]
                val size = sizes[file.path]
                if (file.isFolder && (count != null || size != null)) {
                    val label = buildFolderLabel(count, size)
                    if (label != null && file.size != label) {
                        changed = true
                        file.copy(size = label)
                    } else {
                        file
                    }
                } else {
                    file
                }
            }
            if (changed) updated else current
        }
    }

    /** "24 items · 3.2 MB" for a folder, or null when nothing is known yet. */
    private fun buildFolderLabel(count: Int?, sizeBytes: Long?): String? {
        val parts = mutableListOf<String>()
        if (count != null && count >= 0) parts += if (count == 1) "1 item" else "$count items"
        if (sizeBytes != null && sizeBytes > 0L) {
            parts += android.text.format.Formatter.formatShortFileSize(context, sizeBytes)
        }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current == android.os.Environment.getExternalStorageDirectory().absolutePath || current == "/") {
            // At root, let the screen handle the exit (to Dashboard)
            return
        }
        val parent = java.io.File(current).parent ?: return
        loadDirectory(parent)
    }

    fun toggleSelection(path: String) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedFiles.value = current
    }

    /**
     * Enters the selection mode marking [path], the way a file manager does after
     * a long press. From here every short press marks another file.
     */
    fun beginSelection(path: String) {
        val current = _selectedFiles.value.toMutableSet()
        current.add(path)
        _selectedFiles.value = current
    }

    fun toggleKeepScreenOn() {
        _keepScreenOn.value = !_keepScreenOn.value
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == FileViewMode.TACTICAL) FileViewMode.AESTHETIC else FileViewMode.TACTICAL
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    /**
     * INDUSTRIAL OPERATIONS
     */
    fun deleteSelected() {
        viewModelScope.launch {
            val toDelete = _selectedFiles.value.toList()
            toDelete.forEach { path ->
                repository.deleteFile(path)
            }
            clearSelection()
            loadDirectory(_currentPath.value)
            updateStorageStats()
        }
    }

    fun copySelected(paths: List<String> = emptyList()) {
        val finalPaths = if (paths.isNotEmpty()) paths else _selectedFiles.value.toList()
        if (finalPaths.isEmpty()) return

        _pendingOperation.value = PendingFileOperation(
            sourcePaths = finalPaths,
            type = FileOperationType.COPY
        )
        clearSelection()
    }

    fun moveSelected(paths: List<String> = emptyList()) {
        val finalPaths = if (paths.isNotEmpty()) paths else _selectedFiles.value.toList()
        if (finalPaths.isEmpty()) return

        _pendingOperation.value = PendingFileOperation(
            sourcePaths = finalPaths,
            type = FileOperationType.MOVE
        )
        clearSelection()
    }

    fun cancelPendingOperation() {
        _pendingOperation.value = null
    }

    fun pasteSelected() {
        val op = _pendingOperation.value ?: return
        val targetDir = _currentPath.value
        val move = op.type == FileOperationType.MOVE
        _pendingOperation.value = null
        transferWithProgress(op.sourcePaths, File(targetDir), move)
    }

    fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            if (repository.renameFile(path, newName)) {
                loadDirectory(_currentPath.value)
            }
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            if (repository.deleteFile(path)) {
                loadDirectory(_currentPath.value)
                updateStorageStats()
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            modelManager.downloadModel(context).collect {
                _downloadState.value = it
            }
        }
    }

    fun performSemanticSearch(query: String) {
        viewModelScope.launch {
            _aiState.value = "Thinking..."
            val contextData = _files.value.take(100).joinToString("\n") { "${it.name} (${it.size})" }
            val response = mediaPipeManager.performSemanticSearch(query, contextData)
            _aiState.value = response
        }
    }

    fun openFile(file: TitanFile) {
        viewModelScope.launch {
            _events.emit(FileManagerEvent.OpenFile(file))
        }
    }

    /**
     * Opens a path coming from the global search.
     */
    fun openPath(path: String) {
        val file = File(path)
        if (!file.exists()) return
        // Opening a result must leave the search behind, otherwise the results
        // panel keeps covering the folder the user just opened.
        clearSearch()
        if (file.isDirectory) {
            loadDirectory(file.absolutePath)
            return
        }
        val category = com.elysium.vanguard.core.util.FileThematics.getCategory(file.name, false)
        openFile(
            TitanFile(
                name = file.name,
                isFolder = false,
                size = android.text.format.Formatter.formatFileSize(context, file.length()),
                path = file.absolutePath,
                mimeType = com.elysium.vanguard.core.archive.ArchiveFormat.fromExtension(file.name)
                    ?.let { mimeFor(it) }
                    ?: "application/octet-stream",
                category = category,
                thematicColor = com.elysium.vanguard.core.util.FileThematics.getCategoryColor(category),
                lastModified = file.lastModified()
            )
        )
    }

    private fun mimeFor(format: com.elysium.vanguard.core.archive.ArchiveFormat): String =
        when (format) {
            com.elysium.vanguard.core.archive.ArchiveFormat.ZIP -> "application/zip"
            com.elysium.vanguard.core.archive.ArchiveFormat.SEVEN_Z -> "application/x-7z-compressed"
            com.elysium.vanguard.core.archive.ArchiveFormat.RAR -> "application/vnd.rar"
            com.elysium.vanguard.core.archive.ArchiveFormat.GZIP -> "application/gzip"
            com.elysium.vanguard.core.archive.ArchiveFormat.BZIP2 -> "application/x-bzip2"
            com.elysium.vanguard.core.archive.ArchiveFormat.XZ -> "application/x-xz"
            com.elysium.vanguard.core.archive.ArchiveFormat.ZSTD -> "application/zstd"
            com.elysium.vanguard.core.archive.ArchiveFormat.TAR -> "application/x-tar"
            com.elysium.vanguard.core.archive.ArchiveFormat.CPIO -> "application/x-cpio"
            com.elysium.vanguard.core.archive.ArchiveFormat.AR -> "application/x-archive"
            com.elysium.vanguard.core.archive.ArchiveFormat.XAR -> "application/x-xar"
            com.elysium.vanguard.core.archive.ArchiveFormat.ISO -> "application/x-iso9660-image"
            else -> "application/octet-stream"
        }

    fun shareFile(file: TitanFile) {
        viewModelScope.launch {
            _events.emit(FileManagerEvent.ShareFile(file))
        }
    }

    fun calculateRecursiveSize(file: TitanFile) {
        viewModelScope.launch {
            val size = repository.getFolderSizeRecursive(java.io.File(file.path))
            val formattedSize = android.text.format.Formatter.formatFileSize(context, size)

            // Update the file in the list with the new size
            _files.value = _files.value.map {
                if (it.path == file.path) it.copy(size = formattedSize) else it
            }
        }
    }

    fun decompressFile(zipFile: File, outputDir: File) {
        val intent = Intent(context, com.elysium.vanguard.core.services.CompressionService::class.java).apply {
            action = com.elysium.vanguard.core.services.CompressionService.ACTION_DECOMPRESS
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_FILES, arrayOf(zipFile.absolutePath))
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_OUTPUT, outputDir.absolutePath)
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_KEEP_SCREEN_ON, _keepScreenOn.value)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _compressionProgress.value = CompressionState(0, "Initializing...", CompressionStatus.RUNNING)
    }

    fun cancelCompression() {
        val intent = Intent(context, com.elysium.vanguard.core.services.CompressionService::class.java).apply {
            action = com.elysium.vanguard.core.services.CompressionService.ACTION_CANCEL
        }
        context.startService(intent)
        _compressionProgress.value = null
    }

    fun dismissCompressionDialog() {
        _compressionProgress.value = null
    }

    fun decompressSelected(zipFile: File) {
        val targetDir = File(zipFile.parent, zipFile.nameWithoutExtension)
        decompressFile(zipFile, targetDir)
    }

    // ── UNIVERSAL EXTRACTION ────────────────────────────────────────────────

    /**
     * Extracts one archive - or a complete multi volume set - with the universal
     * engine. Password protected archives are supported.
     */
    fun extractArchives(
        files: List<File>,
        destination: File,
        password: String? = null,
        overwrite: Boolean = false
    ) {
        if (files.isEmpty()) return
        val intent = Intent(context, com.elysium.vanguard.core.services.ArchiveService::class.java).apply {
            action = com.elysium.vanguard.core.services.ArchiveService.ACTION_EXTRACT
            putExtra(
                com.elysium.vanguard.core.services.ArchiveService.EXTRA_FILES,
                files.map { it.absolutePath }.toTypedArray()
            )
            putExtra(
                com.elysium.vanguard.core.services.ArchiveService.EXTRA_DESTINATION,
                destination.absolutePath
            )
            putExtra(com.elysium.vanguard.core.services.ArchiveService.EXTRA_PASSWORD, password)
            putExtra(com.elysium.vanguard.core.services.ArchiveService.EXTRA_OVERWRITE, overwrite)
            putExtra(
                com.elysium.vanguard.core.services.ArchiveService.EXTRA_KEEP_SCREEN_ON,
                _keepScreenOn.value
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _extractionProgress.value = CompressionState(0, "Initializing...", CompressionStatus.RUNNING)
    }

    fun cancelExtraction() {
        val intent = Intent(context, com.elysium.vanguard.core.services.ArchiveService::class.java).apply {
            action = com.elysium.vanguard.core.services.ArchiveService.ACTION_CANCEL
        }
        context.startService(intent)
        _extractionProgress.value = null
    }

    fun dismissExtractionDialog() {
        _extractionProgress.value = null
    }

    // ── CONVERSIONS ─────────────────────────────────────────────────────────

    /**
     * Runs a conversion on the selected files.
     *
     * @param mode the conversion to perform.
     */
    fun convertFiles(files: List<File>, mode: ConversionMode) {
        if (files.isEmpty()) return
        _operationMessage.value = "CONVERTING..."

        viewModelScope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                when (mode) {
                    ConversionMode.TO_PDF -> convertToPdf(files)
                    ConversionMode.TO_IMAGES -> convertToImages(files)
                    ConversionMode.TO_WORD -> convertToWord(files)
                }
            }

            outcome
                .onSuccess { message -> _operationMessage.value = message }
                .onFailure { error -> _operationMessage.value = error.message ?: "CONVERSION FAILED" }

            withContext(Dispatchers.Main) {
                loadDirectory(_currentPath.value)
                updateStorageStats()
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3200)
                    _operationMessage.value = null
                }
            }
        }
    }

    /**
     * Builds one single PDF out of the selected images inside a brand new folder.
     *
     * A single file keeps the result next to the source (desktop behaviour).
     * A multi selection creates a new folder that holds one PDF with every
     * selected image inside it.
     */
    private fun convertToPdf(files: List<File>): String {
        val convertible = files.filter { file ->
            com.elysium.vanguard.core.util.ConversionEngine.isImage(file) ||
                file.extension.lowercase() in setOf("doc", "docx", "txt", "rtf", "csv", "md", "html", "htm", "json", "xml")
        }
        if (convertible.isEmpty()) {
            throw IllegalArgumentException("Nothing convertible was selected")
        }

        val anchor = convertible.first()
        val multiple = convertible.size > 1

        val outputFolder: File = if (multiple) {
            File(anchor.parentFile, "Converted_${stamp()}").also { it.mkdirs() }
        } else {
            anchor.parentFile ?: return "CONVERSION FAILED: no destination folder"
        }

        val allImages = convertible.all {
            com.elysium.vanguard.core.util.ConversionEngine.isImage(it)
        }
        val pdfName = when {
            multiple && allImages -> "Combined_${stamp()}.pdf"
            multiple -> "Combined_${stamp()}.pdf"
            else -> "${anchor.nameWithoutExtension}.pdf"
        }
        val target = File(outputFolder, pdfName)

        com.elysium.vanguard.core.util.ConversionEngine.filesToPdf(convertible, target).getOrThrow()
        return "PDF CREATED: ${target.absolutePath}"
    }

    /**
     * Renders every selected PDF into one image per page inside a new folder.
     */
    private fun convertToImages(files: List<File>): String {
        val pdfs = files.filter { it.extension.equals("pdf", ignoreCase = true) }
        if (pdfs.isEmpty()) throw IllegalArgumentException("Select one or more PDF files")

        var created = 0
        pdfs.forEach { pdf ->
            val outputFolder = File(pdf.parent, "Images_${pdf.nameWithoutExtension}_${stamp()}")
            val result = com.elysium.vanguard.core.util.ConversionEngine.pdfToImages(pdf, outputFolder)
            result.onSuccess { created += it.size }
                .onFailure { throw it }
        }
        return "$created IMAGES CREATED"
    }

    /**
     * Converts every selected PDF into an editable Word document.
     */
    private fun convertToWord(files: List<File>): String {
        val pdfs = files.filter { it.extension.equals("pdf", ignoreCase = true) }
        if (pdfs.isEmpty()) throw IllegalArgumentException("Select one or more PDF files")

        pdfs.forEach { pdf ->
            val target = File(pdf.parent, "${pdf.nameWithoutExtension}.docx")
            com.elysium.vanguard.core.util.ConversionEngine.pdfToWord(context, pdf, target).getOrThrow()
        }
        return "${pdfs.size} WORD DOCUMENT(S) CREATED"
    }

    private fun stamp(): String {
        val format = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        return format.format(java.util.Date())
    }

    // ── ARCHIVE INTEGRITY ───────────────────────────────────────────────────

    /**
     * Pre-flight check performed before an extraction starts.
     *
     * A multi volume game set can easily need tens of gigabytes, so the space
     * is verified before a single byte is written instead of filling the device.
     */
    data class ExtractionPreflight(
        val requiredBytes: Long,
        val availableBytes: Long,
        val entries: Int
    ) {
        val hasSpace: Boolean get() = requiredBytes <= availableBytes
        val shortfallBytes: Long get() = (requiredBytes - availableBytes).coerceAtLeast(0L)
    }

    fun preflightExtraction(files: List<File>): ExtractionPreflight? {
        var required = 0L
        var entries = 0
        var known = false

        files.forEach { file ->
            val manifest = com.elysium.vanguard.core.archive.ArchiveEngine.inspect(file).getOrNull()
            if (manifest != null) {
                required += manifest.totalSize
                entries += manifest.fileCount
                known = true
            }
        }
        if (!known) return null

        val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        return ExtractionPreflight(required, available, entries)
    }

    /**
     * Verifies the integrity of an archive without extracting it. Every member is
     * read end to end so the library validates its checksum.
     */
    fun verifyArchive(file: File) {
        _operationMessage.value = "VERIFYING ARCHIVE..."
        viewModelScope.launch(Dispatchers.IO) {
            com.elysium.vanguard.core.archive.ArchiveEngine.verify(file).fold(
                onSuccess = { report ->
                    _operationMessage.value = if (report.isIntact) {
                        "ARCHIVE IS INTACT · ${report.entriesTested} ENTRIES · ${report.bytesVerified / 1024} KB VERIFIED"
                    } else {
                        "ARCHIVE DAMAGED · ${report.failures.size} FAILED · ${report.failures.firstOrNull() ?: ""}"
                    }
                },
                onFailure = { error ->
                    _operationMessage.value = error.message ?: "VERIFICATION FAILED"
                }
            )
            withContext(Dispatchers.Main) {
                loadDirectory(_currentPath.value)
                viewModelScope.launch {
                    kotlinx.coroutines.delay(4000)
                    _operationMessage.value = null
                }
            }
        }
    }

    // ── BATCH RENAME ────────────────────────────────────────────────────────

    /**
     * Renames the whole selection with a pattern. The plan is computed first so
     * conflicts are resolved before anything touches the disk.
     */
    fun batchRename(pattern: com.elysium.vanguard.core.rename.BatchRenameEngine.Pattern): String {
        val files = _selectedFiles.value.map { File(it) }.sortedBy { it.name.lowercase() }
        if (files.isEmpty()) return "NOTHING SELECTED"

        val engine = com.elysium.vanguard.core.rename.BatchRenameEngine()
        val plan = engine.plan(files, pattern)
        if (plan.aborted) return "BATCH ABORTED: a name conflict was found"
        if (plan.renames.isEmpty()) return "NOTHING TO RENAME"

        val renamed = engine.execute(plan)
        loadDirectory(_currentPath.value)
        return buildString {
            append("$renamed FILE(S) RENAMED")
            if (plan.skipped.isNotEmpty()) append(" · ${plan.skipped.size} SKIPPED")
        }
    }

    /**
     * Previews a batch rename without touching the disk.
     */
    fun previewBatchRename(
        pattern: com.elysium.vanguard.core.rename.BatchRenameEngine.Pattern
    ): com.elysium.vanguard.core.rename.BatchRenameEngine.Plan {
        val files = _selectedFiles.value.map { File(it) }.sortedBy { it.name.lowercase() }
        return com.elysium.vanguard.core.rename.BatchRenameEngine().plan(files, pattern)
    }

    // ── GLOBAL SEARCH ───────────────────────────────────────────────────────

    /**
     * Runs a global search.
     *
     * The query is understood as a filter expression: the free text part is
     * matched fuzzily against every file name, while the known tokens
     * (`ext:`, `size:`, `type:`, `modified:`) narrow the result set.
     */
    fun search(query: String, scopeWholeDevice: Boolean = _searchScopeWholeDevice.value) {
        _searchQuery.value = query
        _searchScopeWholeDevice.value = scopeWholeDevice

        searchJob?.cancel()
        _searchHits.value = emptyList()

        if (query.isBlank()) {
            _searchProgress.value = null
            return
        }

        val roots = if (scopeWholeDevice) {
            listOf(android.os.Environment.getExternalStorageDirectory())
        } else {
            listOf(File(_currentPath.value))
        }

        // Debounce so a fast typist does not launch a walk per keystroke.
        // The walk touches thousands of files: it must never run on the main thread.
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(280)
            val engine = com.elysium.vanguard.core.search.GlobalSearchEngine(
                com.elysium.vanguard.core.search.FuzzySearchEngine(),
                com.elysium.vanguard.core.search.FileFilterParser()
            )
            val collected = mutableListOf<com.elysium.vanguard.core.search.GlobalSearchEngine.Hit>()
            engine.search(
                query = query,
                roots = roots,
                onHit = { hit ->
                    collected += hit
                    _searchHits.value = collected.toList()
                },
                onProgress = { progress -> _searchProgress.value = progress },
                isCancelled = { searchJob?.isActive == false }
            )
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        _searchProgress.value = null
    }

    fun clearSearch() {
        cancelSearch()
        _searchQuery.value = ""
        _searchHits.value = emptyList()
    }

    fun toggleSearchScope() {
        val next = !_searchScopeWholeDevice.value
        val query = _searchQuery.value
        if (query.isNotBlank()) search(query, next) else _searchScopeWholeDevice.value = next
    }

    // ── DELETION WITH PROGRESS ──────────────────────────────────────────────

    /**
     * Deletes the given paths showing a truthful progress percentage.
     */
    fun deleteWithProgress(paths: List<String>) {
        if (paths.isEmpty()) return
        _deletionProgress.value = DeletionState(0, 0, paths.size, "")
        viewModelScope.launch(Dispatchers.IO) {
            val report = repository.deleteWithProgress(paths) { percent, current ->
                _deletionProgress.value = DeletionState(
                    percentage = percent,
                    deleted = (percent * paths.size) / 100,
                    total = paths.size,
                    currentPath = current
                )
            }
            _deletionProgress.value = DeletionState(
                percentage = 100,
                deleted = report.deleted,
                total = paths.size,
                currentPath = "COMPLETE",
                failed = report.failed,
                bytesFreed = report.bytesFreed,
                finished = true
            )
            loadDirectory(_currentPath.value)
            updateStorageStats()
            kotlinx.coroutines.delay(1400)
            _deletionProgress.value = null
        }
    }

    fun dismissDeletionProgress() {
        _deletionProgress.value = null
    }

    // ── COPY / MOVE WITH PROGRESS ───────────────────────────────────────────

    /**
     * Copies or moves the given paths into [destination] showing a truthful
     * percentage while the transfer runs.
     */
    fun transferWithProgress(paths: List<String>, destination: File, move: Boolean) {
        if (paths.isEmpty()) return
        _transferProgress.value = TransferState(
            percentage = 0,
            moved = 0,
            total = paths.size,
            currentPath = "",
            mode = if (move) TransferMode.MOVE else TransferMode.COPY
        )
        viewModelScope.launch(Dispatchers.IO) {
            val report = repository.transferWithProgress(paths, destination, move) { percent, current ->
                _transferProgress.value = TransferState(
                    percentage = percent,
                    moved = (percent * paths.size) / 100,
                    total = paths.size,
                    currentPath = current,
                    mode = if (move) TransferMode.MOVE else TransferMode.COPY
                )
            }
            _transferProgress.value = TransferState(
                percentage = 100,
                moved = report.transferred,
                total = paths.size,
                currentPath = "COMPLETE",
                mode = if (move) TransferMode.MOVE else TransferMode.COPY,
                failed = report.failed,
                bytesTransferred = report.bytesTransferred,
                finished = true
            )
            loadDirectory(_currentPath.value)
            updateStorageStats()
            kotlinx.coroutines.delay(1400)
            _transferProgress.value = null
        }
    }

    fun dismissTransferProgress() {
        _transferProgress.value = null
    }

    // ── COMPRESSION ─────────────────────────────────────────────────────────

    /**
     * Compresses the selection into [format], optionally with a password.
     */
    fun compressFiles(
        files: List<File>,
        outputFile: File,
        format: com.elysium.vanguard.core.util.CompressionFormat,
        password: String? = null
    ) {
        if (files.isEmpty()) return
        val intent = Intent(context, com.elysium.vanguard.core.services.CompressionService::class.java).apply {
            action = com.elysium.vanguard.core.services.CompressionService.ACTION_COMPRESS
            putExtra(
                com.elysium.vanguard.core.services.CompressionService.EXTRA_FILES,
                files.map { it.absolutePath }.toTypedArray()
            )
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_OUTPUT, outputFile.absolutePath)
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_FORMAT, format.id)
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_PASSWORD, password)
            putExtra(
                com.elysium.vanguard.core.services.CompressionService.EXTRA_KEEP_SCREEN_ON,
                _keepScreenOn.value
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _compressionProgress.value = CompressionState(0, "Preparing $ {format.displayName}…", CompressionStatus.RUNNING)
    }

    // ── BATCH SELECTION ──────────────────────────────────────────────────────

    fun selectAll() {
        _selectedFiles.value = _files.value.map { it.path }.toSet()
    }

    fun deselectAll() {
        _selectedFiles.value = emptySet()
    }

    fun invertSelection() {
        val current = _selectedFiles.value
        _selectedFiles.value = _files.value.map { it.path }.filterNot { current.contains(it) }.toSet()
    }
}

enum class FileViewMode {
    AESTHETIC, TACTICAL
}

data class CompressionState(
    val percentage: Int,
    val currentFile: String,
    val status: CompressionStatus = CompressionStatus.RUNNING
)

enum class CompressionStatus {
    RUNNING, SUCCESS, ERROR
}

enum class ConversionMode {
    TO_PDF, TO_IMAGES, TO_WORD
}

/**
 * Live state of a deletion run.
 */
data class DeletionState(
    val percentage: Int,
    val deleted: Int,
    val total: Int,
    val currentPath: String,
    val failed: Int = 0,
    val bytesFreed: Long = 0L,
    val finished: Boolean = false
)

enum class TransferMode { COPY, MOVE }

/**
 * Live state of a copy or move run.
 */
data class TransferState(
    val percentage: Int,
    val moved: Int,
    val total: Int,
    val currentPath: String,
    val mode: TransferMode,
    val failed: Int = 0,
    val bytesTransferred: Long = 0L,
    val finished: Boolean = false
)

sealed class FileManagerEvent {
    data class OpenFile(val file: TitanFile) : FileManagerEvent()
    data class ShareFile(val file: TitanFile) : FileManagerEvent()
}

data class PendingFileOperation(
    val sourcePaths: List<String>,
    val type: FileOperationType
)

enum class FileOperationType {
    COPY, MOVE
}
