package com.elysium.vanguard.features.filemanager

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import com.elysium.vanguard.ui.theme.neonGlass
import com.elysium.vanguard.ui.theme.holographicGlass
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import com.elysium.vanguard.ui.components.BreathingFolderIcon
import com.elysium.vanguard.ui.components.NeonGlowIcon
import com.elysium.vanguard.ui.components.MatrixRain
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import com.elysium.vanguard.core.util.CompressionEngine
import com.elysium.vanguard.core.util.ConversionEngine
import com.elysium.vanguard.core.util.FileCategory
import com.elysium.vanguard.core.archive.ArchiveEngine
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.rename.BatchRenameEngine
import com.elysium.vanguard.core.archive.ArchiveSetResolver
import com.elysium.vanguard.core.search.GlobalSearchEngine
import com.elysium.vanguard.core.search.SearchSort
import com.elysium.vanguard.ui.components.TitanLogo
import com.elysium.vanguard.ui.components.TitanLogoStyle
import com.elysium.vanguard.ui.components.TitanHeader
import com.elysium.vanguard.ui.components.SovereignLifeWrapper
import com.elysium.vanguard.ui.components.PulseContainer
import com.elysium.vanguard.ui.components.SovereignCard
import com.elysium.vanguard.core.util.FileOpenerUtil
import com.elysium.vanguard.core.util.StorageStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.components.ColorCustomizerIcon
import com.elysium.vanguard.ui.components.ColorSelectionDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * THE OMNI-CORE INTERFACE
 * The master orchestration layer for the Titan Glass File Manager.
 */
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isTerminalMode by remember { mutableStateOf(false) } // Terminal Mode State
    val files by viewModel.files.collectAsState()

    // GLOBAL SEARCH state
    val searchHits by viewModel.searchHits.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()
    val searchWholeDevice by viewModel.searchScopeWholeDevice.collectAsState()
    val searchProgressSnapshot = searchProgress
    val isSearching = searchProgressSnapshot != null && !searchProgressSnapshot.finished

    // Recovery Fix: Local filtering logic to restore search functionality
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isEmpty()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    var showColorDialog by remember { mutableStateOf(false) }
    val accentColor = SectionColorManager.fileAccent
    val currentPath by viewModel.currentPath.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()

    val isSelectionMode = selectedFiles.isNotEmpty()

    // Progress dialog state
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var progressFileName by remember { mutableStateOf("") }
    var progressTitle by remember { mutableStateOf("") }

    // Operation States
    var fileToRename by remember { mutableStateOf<TitanFile?>(null) }
    var fileForDetails by remember { mutableStateOf<TitanFile?>(null) }

    // Universal extraction: a pending extraction waiting for its password.
    var pendingExtraction by remember { mutableStateOf<PendingExtraction?>(null) }

    // Search by file type: the token appended to the query (null = every type).
    var searchTypeToken by remember { mutableStateOf<String?>(null) }

    // How the search results are ordered.
    var searchSort by remember { mutableStateOf(SearchSort.RELEVANCE) }

    // Batch rename of the current selection.
    var showBatchRenameDialog by remember { mutableStateOf(false) }

    // Options dialog for the item currently marked.
    var optionsForSelection by remember { mutableStateOf<TitanFile?>(null) }

    // Compression: the selection waiting for a format, and the one waiting for a password.
    var compressTarget by remember { mutableStateOf<List<String>?>(null) }
    var pendingPasswordCompress by remember { mutableStateOf<PendingPasswordCompress?>(null) }

    // Blocking storage warning raised by the extraction pre-flight check.
    var spaceWarning by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * Starts a universal extraction. When the archive is encrypted the user is
     * asked for the password before anything is written to disk.
     */
    val requestExtraction: (File) -> Unit = { sourceFile ->
        scope.launch {
            val set = ArchiveSetResolver.resolve(sourceFile)
            val parts = set.parts.ifEmpty { listOf(sourceFile) }
            // The engine creates one folder per archive inside the given parent.
            val destination = sourceFile.parentFile
            val needsPassword = withContext(Dispatchers.IO) {
                parts.any { part ->
                    // BUG FIX: a "password required" failure means the archive IS
                    // encrypted. Treating it as "not encrypted" would extract a
                    // 7z or RAR with a password without ever asking for it.
                    val result = ArchiveEngine.inspect(part)
                    val manifest = result.getOrNull()
                    if (manifest != null) {
                        manifest.encrypted
                    } else {
                        val error = result.exceptionOrNull()
                        error is ArchiveException.PasswordRequired ||
                            error is ArchiveException.WrongPassword
                    }
                }
            }
            val preflight = withContext(Dispatchers.IO) { viewModel.preflightExtraction(parts) }
            if (preflight != null && !preflight.hasSpace) {
                spaceWarning = "NOT ENOUGH SPACE\n" +
                    "This extraction needs ${formatBytes(preflight.requiredBytes)} but only " +
                    "${formatBytes(preflight.availableBytes)} are free."
            } else if (needsPassword) {
                pendingExtraction = PendingExtraction(parts, destination)
            } else {
                viewModel.extractArchives(parts, destination)
            }
        }
    }

    val requestConversion: (File, ConversionMode) -> Unit = { sourceFile, mode ->
        viewModel.convertFiles(listOf(sourceFile), mode)
    }

    /**
     * Runs the search with the free text plus the selected type filter.
     */
    val runSearch: (String) -> Unit = { text ->
        val token = searchTypeToken
        viewModel.search(
            listOfNotNull(text.takeIf { it.isNotBlank() }, token).joinToString(" ")
        )
    }

    /**
     * Verifies the integrity of an archive without extracting it.
     */
    val requestVerification: (File) -> Unit = { sourceFile ->
        viewModel.verifyArchive(sourceFile)
    }

    /**
     * Batch extraction: every selected archive becomes its own folder, and a
     * multi volume set is always resolved as a whole.
     */
    val batchExtract: () -> Unit = {
        val archives = selectedFiles.map { File(it) }.filter { ArchiveEngine.isArchive(it) }
        if (archives.isEmpty()) {
            Toast.makeText(context, "Select one or more archives to extract", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                val preflight = withContext(Dispatchers.IO) { viewModel.preflightExtraction(archives) }
                if (preflight != null && !preflight.hasSpace) {
                    spaceWarning = "NOT ENOUGH SPACE\n" +
                        "This extraction needs ${formatBytes(preflight.requiredBytes)} but only " +
                        "${formatBytes(preflight.availableBytes)} are free."
                    return@launch
                }
                val needsPassword = withContext(Dispatchers.IO) {
                    archives.any { archive ->
                        val result = ArchiveEngine.inspect(archive)
                        val manifest = result.getOrNull()
                        if (manifest != null) {
                            manifest.encrypted
                        } else {
                            val error = result.exceptionOrNull()
                            error is ArchiveException.PasswordRequired ||
                                error is ArchiveException.WrongPassword
                        }
                    }
                }
                val destination = File(currentPath)
                if (needsPassword) {
                    pendingExtraction = PendingExtraction(archives, destination)
                } else {
                    viewModel.extractArchives(archives, destination)
                }
            }
        }
    }

    /**
     * Batch conversion of the selection (images and documents to PDF).
     */
    val batchConvert: () -> Unit = {
        val files = selectedFiles.map { File(it) }
        if (files.isEmpty()) {
            Toast.makeText(context, "Select files to convert", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.convertFiles(files, ConversionMode.TO_PDF)
        }
    }


    val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionsAndLoad()
                viewModel.updateStorageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TitanColors.DeepVoidGradient)) {
        // ── MATRIX RAIN BACKGROUND ──
        // PERFORMANCE: throttled and shortened. The previous settings repainted the
        // whole window eleven times per second on top of the scrolling list.
        MatrixRain(
            color = TitanColors.RadioactiveGreen.copy(alpha = 0.3f), // Subtler rain
            columnSpacing = 34,
            speed = 140L,
            trailLength = 7,
            alpha = 0.12f,
            isMulticolor = SectionColorManager.isMulticolor
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (isSelectionMode) {
                    SelectionToolbar(
                        selectedCount = selectedFiles.size,
                        onClear = { viewModel.clearSelection() },
                        onDelete = { viewModel.deleteWithProgress(selectedFiles.toList()) },
                        onMove = { viewModel.moveSelected() },
                        onCopy = { viewModel.copySelected() },
                        onSelectAll = { viewModel.selectAll() },
                        onExtract = { batchExtract() },
                        onConvert = { batchConvert() },
                        onCompress = { compressTarget = selectedFiles.toList() },
                        onRename = { showBatchRenameDialog = true },
                        onInvert = { viewModel.invertSelection() },
                        onMore = {
                            optionsForSelection = files.firstOrNull { selectedFiles.contains(it.path) }
                        }
                    )
                } else {
                    val pendingOp by viewModel.pendingOperation.collectAsState()
                    TitanHeader(
                        title = if (pendingOp != null) {
                            if (pendingOp!!.type == FileOperationType.COPY) "SELECT DESTINATION TO COPY"
                            else "SELECT DESTINATION TO MOVE"
                        } else {
                            val lastPart = currentPath.split("/").last()
                            when {
                                currentPath == "/storage/emulated/0" -> "INTERNAL STORAGE"
                                lastPart == "0" -> "INTERNAL STORAGE"
                                else -> lastPart.ifEmpty { "FILE MANAGER" }
                            }
                        },
                        onBack = {
                            if (pendingOp != null) {
                                viewModel.cancelPendingOperation()
                            } else if (currentPath == android.os.Environment.getExternalStorageDirectory().absolutePath || currentPath == "/") {
                                onBack()
                            } else {
                                viewModel.navigateUp()
                            }
                        },
                        sectionName = "FILEMANAGER",
                        actions = {
                            val viewMode by viewModel.viewMode.collectAsState()
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (viewMode == FileViewMode.TACTICAL) Icons.Default.GridView else Icons.Default.ViewList,
                                    contentDescription = "Toggle View",
                                    tint = TitanColors.NeonCyan
                                )
                            }
                            IconButton(onClick = { isTerminalMode = !isTerminalMode }) {
                                Icon(
                                    imageVector = if (isTerminalMode) Icons.Default.Apps else Icons.Default.Terminal,
                                    contentDescription = "Switch Mode",
                                    tint = TitanColors.NeonCyan
                                )
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // ── COLOR CUSTOMIZER TRIGGER ──


                // BACK NAVIGATION MASTER SHIELD
                androidx.activity.compose.BackHandler(enabled = !isSelectionMode && currentPath != android.os.Environment.getExternalStorageDirectory().absolutePath && currentPath != "/") {
                    viewModel.navigateUp()
                }
                // The back gesture inside the selection mode clears the marks
                // instead of leaving the folder.
                androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
                    viewModel.clearSelection()
                }
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    // FLOATING PENDING OPERATION BAR
                    val pendingOp by viewModel.pendingOperation.collectAsState()
                    if (pendingOp != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .neonGlass(cornerRadius = 16.dp, glowColor = TitanColors.RadioactiveGreen)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pendingOp!!.type == FileOperationType.COPY) "COPYING ${pendingOp!!.sourcePaths.size} ITEMS"
                                    else "MOVING ${pendingOp!!.sourcePaths.size} ITEMS",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "NAVIGATE TO DESTINATION",
                                    color = TitanColors.RadioactiveGreen,
                                    fontSize = 10.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.cancelPendingOperation() }) {
                                    Text("CANCEL", color = TitanColors.NeonRed)
                                }
                                Button(
                                    onClick = { viewModel.pasteSelected() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TitanColors.RadioactiveGreen)
                                ) {
                                    Text("PASTE", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    storageStats?.let { stats ->
                        StorageMetricsBar(stats)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    NeuralSearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            runSearch(it)
                        },
                        onSearch = { runSearch(searchQuery) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val shortcuts by viewModel.commonShortcuts.collectAsState()
                    if (shortcuts.isNotEmpty()) {
                        ShortcutsQuickAccess(
                            shortcuts = shortcuts,
                            onShortcutClick = { viewModel.loadDirectory(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StorageCategoryRow(
                        onAddClick = { /* TODO */ },
                        onPathClick = { viewModel.loadDirectory(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TacticalBreadcrumbs(
                        path = currentPath,
                        onPathClick = { viewModel.loadDirectory(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (searchQuery.isNotEmpty()) {
                        // GLOBAL SEARCH: the results stream in while the walk runs.
                        SearchResultsPanel(
                            hits = searchHits,
                            progress = searchProgress,
                            wholeDevice = searchWholeDevice,
                            isSearching = isSearching,
                            typeToken = searchTypeToken,
                            onTypeToken = { token ->
                                searchTypeToken = if (searchTypeToken == token) null else token
                                runSearch(searchQuery)
                            },
                            sort = searchSort,
                            onSort = { searchSort = it },
                            onToggleScope = { viewModel.toggleSearchScope() },
                            onCancel = { viewModel.cancelSearch() },
                            onOpen = {
                                searchQuery = ""
                                viewModel.openPath(it)
                            }
                        )
                    } else {
                    when (uiState) {
                        is FileManagerUiState.Loading -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LoadingDiagnosticPulse(text = "QUANTUM SCANNING...")
                            }
                        }
                        is FileManagerUiState.PermissionRequired -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                StoragePermissionOverlay(context)
                            }
                        }
                        is FileManagerUiState.Error -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("CORE ERROR: ${(uiState as FileManagerUiState.Error).message}", color = TitanColors.NeonRed)
                            }
                        }
                        else -> {
                            // EMPTY or SUCCESS
                            if (isTerminalMode) {
                                TerminalList(
                                    files = filteredFiles,
                                    onFileClick = { file ->
                                        if (file.isFolder) viewModel.loadDirectory(file.path)
                                        else viewModel.openFile(file)
                                    },
                                    onCalculateSize = { viewModel.calculateRecursiveSize(it) }
                                )
                            } else {
                                val viewMode by viewModel.viewMode.collectAsState()
                                if (viewMode == FileViewMode.TACTICAL) {
                                    if (filteredFiles.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            RadarScanningPlaceholder("NO DATA NODES DETECTED")
                                        }
                                    } else {
                                        val listState = rememberLazyListState()
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(bottom = 100.dp)
                                            ) {
                                                item { TacticalHeaderRow() }
                                            items(
                                                count = filteredFiles.size,
                                                key = { index -> filteredFiles[index].path }
                                            ) { index ->
                                                val file = filteredFiles[index]
                                                var showOptions by remember { mutableStateOf(false) }
                                                TacticalFileRow(
                                                    file = file,
                                                    isSelected = selectedFiles.contains(file.path),
                                                    onSelectionToggle = { viewModel.toggleSelection(file.path) },
                                                    onClick = {
                                                        if (isSelectionMode) viewModel.toggleSelection(file.path)
                                                        else if (file.isFolder) viewModel.loadDirectory(file.path)
                                                        else viewModel.openFile(file)
                                                    },
                                                    onLongClick = {
                                                        // A long press enters the selection mode and
                                                        // marks the item; from here a short press
                                                        // marks the rest.
                                                        if (isSelectionMode) {
                                                            viewModel.toggleSelection(file.path)
                                                        } else {
                                                            viewModel.beginSelection(file.path)
                                                        }
                                                    }
                                                )
                                                if (showOptions) {
                                                    SovereignOptionsDialog(
                                                        file = file,
                                                        onDismiss = { showOptions = false },
                                                        onAction = { action ->
                                                            showOptions = false
                                                            if (action == "DETAILS") {
                                                                fileForDetails = file
                                                            } else {
                                                                handleFileAction(
                                                                    action = action,
                                                                    file = file,
                                                                    viewModel = viewModel,
                                                                    scope = scope,
                                                                    currentPath = currentPath,
                                                                    onRenameRequest = { f -> fileToRename = f },
                                                                    onExtract = { source -> requestExtraction(source) },
                                                                    onConvert = { source, mode -> requestConversion(source, mode) },
                                                                    onVerify = { source -> requestVerification(source) },
                                                                    onCompress = { source -> compressTarget = listOf(source.absolutePath) },
                                                                    onDelete = { source -> viewModel.deleteWithProgress(listOf(source.absolutePath)) }
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            }
                                            com.elysium.vanguard.ui.components.SovereignScrollbar(
                                                state = listState,
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }
                                } else {
                                    // AESTHETIC MODE (Grid)
                                    if (filteredFiles.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            RadarScanningPlaceholder("EMPTY DATA CLUSTER")
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                                            verticalArrangement = Arrangement.spacedBy(24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            items(
                                                count = filteredFiles.size,
                                                key = { index -> filteredFiles[index].path }
                                            ) { index ->
                                                val file = filteredFiles[index]
                                                var showOptions by remember { mutableStateOf(false) }
                                                ReactorFileCard(
                                                    file = file,
                                                    isSelected = selectedFiles.contains(file.path),
                                                    onSelectionToggle = { viewModel.toggleSelection(file.path) },
                                                    onClick = {
                                                        if (isSelectionMode) viewModel.toggleSelection(file.path)
                                                        else if (file.isFolder) viewModel.loadDirectory(file.path)
                                                        else viewModel.openFile(file)
                                                    },
                                                    onLongClick = {
                                                        // A long press enters the selection mode and
                                                        // marks the item; from here a short press
                                                        // marks the rest.
                                                        if (isSelectionMode) {
                                                            viewModel.toggleSelection(file.path)
                                                        } else {
                                                            viewModel.beginSelection(file.path)
                                                        }
                                                    }
                                                )
                                                if (showOptions) {
                                                    SovereignOptionsDialog(
                                                        file = file,
                                                        onDismiss = { showOptions = false },
                                                        onAction = { action ->
                                                            showOptions = false
                                                            if (action == "DETAILS") {
                                                                fileForDetails = file
                                                            } else {
                                                                handleFileAction(
                                                                    action = action,
                                                                    file = file,
                                                                    viewModel = viewModel,
                                                                    scope = scope,
                                                                    currentPath = currentPath,
                                                                    onRenameRequest = { f -> fileToRename = f },
                                                                    onExtract = { source -> requestExtraction(source) },
                                                                    onConvert = { source, mode -> requestConversion(source, mode) },
                                                                    onVerify = { source -> requestVerification(source) },
                                                                    onCompress = { source -> compressTarget = listOf(source.absolutePath) },
                                                                    onDelete = { source -> viewModel.deleteWithProgress(listOf(source.absolutePath)) }
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                        }
                }
            }

            // DIALOGS
            if (fileToRename != null) {
                RenameDialog(
                    file = fileToRename!!,
                    onDismiss = { fileToRename = null },
                    onConfirm = { newName ->
                        viewModel.renameFile(fileToRename!!.path, newName)
                        fileToRename = null
                    }
                )
            }

            // OPTIONS for the marked item, reachable from the selection toolbar.
            optionsForSelection?.let { target ->
                SovereignOptionsDialog(
                    file = target,
                    onDismiss = { optionsForSelection = null },
                    onAction = { action ->
                        optionsForSelection = null
                        if (action == "DETAILS") {
                            fileForDetails = target
                        } else {
                            handleFileAction(
                                action = action,
                                file = target,
                                viewModel = viewModel,
                                scope = scope,
                                currentPath = currentPath,
                                onRenameRequest = { f -> fileToRename = f },
                                onExtract = { source -> requestExtraction(source) },
                                onConvert = { source, mode -> requestConversion(source, mode) },
                                onVerify = { source -> requestVerification(source) },
                                onCompress = { source -> compressTarget = listOf(source.absolutePath) },
                                onDelete = { source -> viewModel.deleteWithProgress(listOf(source.absolutePath)) }
                            )
                        }
                    }
                )
            }

            if (fileForDetails != null) {
                FileDetailsDialog(
                    file = fileForDetails!!,
                    onDismiss = { fileForDetails = null }
                )
            }

            // DELETION PROGRESS
            val deletionState by viewModel.deletionProgress.collectAsState()
            deletionState?.let { state ->
                DeletionProgressDialog(state = state, onDismiss = { viewModel.dismissDeletionProgress() })
            }

            // COPY / MOVE PROGRESS
            val transferState by viewModel.transferProgress.collectAsState()
            transferState?.let { state ->
                TransferProgressDialog(state = state, onDismiss = { viewModel.dismissTransferProgress() })
            }

            // COMPRESSION FORMAT PICKER
            if (compressTarget != null) {
                CompressFormatDialog(
                    fileCount = compressTarget!!.size,
                    onPick = { format ->
                        val files = compressTarget!!.map { File(it) }
                        compressTarget = null
                        val base = files.firstOrNull()?.nameWithoutExtension ?: "archive"
                        val output = File(currentPath, format.outputNameFor(base))
                        if (format.supportsPassword) {
                            pendingPasswordCompress = PendingPasswordCompress(files, output, format)
                        } else {
                            viewModel.compressFiles(files, output, format, null)
                        }
                    },
                    onDismiss = { compressTarget = null }
                )
            }

            // PASSWORD for the encrypted compression formats
            pendingPasswordCompress?.let { request ->
                PasswordDialog(
                    title = "ENCRYPT ARCHIVE",
                    message = "${request.format.displayName} can be encrypted with a password. " +
                        "Leave it empty to compress without encryption.",
                    allowEmpty = true,
                    onDismiss = { pendingPasswordCompress = null },
                    onConfirm = { password ->
                        viewModel.compressFiles(
                            request.files,
                            request.output,
                            request.format,
                            password.ifBlank { null }
                        )
                        pendingPasswordCompress = null
                    }
                )
            }

            // PASSWORD PROMPT for encrypted archives.
            pendingExtraction?.let { request ->
                PasswordDialog(
                    title = "PASSWORD REQUIRED",
                    message = "This archive is encrypted. Enter the password to extract it.",
                    onDismiss = { pendingExtraction = null },
                    onConfirm = { password ->
                        viewModel.extractArchives(request.files, request.destination, password)
                        pendingExtraction = null
                    }
                )
            }

            // STORAGE WARNING raised by the extraction pre-flight check.
            spaceWarning?.let { warning ->
                AlertDialog(
                    onDismissRequest = { spaceWarning = null },
                    containerColor = TitanColors.AbsoluteBlack,
                    title = {
                        Text(
                            "NOT ENOUGH SPACE",
                            color = TitanColors.NeonRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        )
                    },
                    text = {
                        Text(
                            warning,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { spaceWarning = null }) {
                            Text("UNDERSTOOD", color = TitanColors.NeonCyan, fontSize = 12.sp)
                        }
                    }
                )
            }

            // BATCH RENAME of the current selection.
            if (showBatchRenameDialog) {
                BatchRenameDialog(
                    selectedCount = selectedFiles.size,
                    onPreview = { pattern -> viewModel.previewBatchRename(pattern) },
                    onExecute = { pattern ->
                        val result = viewModel.batchRename(pattern)
                        viewModel.clearSelection()
                        showBatchRenameDialog = false
                        result
                    },
                    onDismiss = { showBatchRenameDialog = false }
                )
            }

            val compressionState by viewModel.compressionProgress.collectAsState()
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()

            if (compressionState != null) {
                AdvancedProgressDialog(
                    state = compressionState!!,
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { viewModel.setKeepScreenOn(it) },
                    onCancel = { viewModel.cancelCompression() },
                    onBackground = { viewModel.dismissCompressionDialog() }
                )
            }

            // UNIVERSAL EXTRACTION progress.
            val extractionState by viewModel.extractionProgress.collectAsState()
            if (extractionState != null) {
                AdvancedProgressDialog(
                    state = extractionState!!,
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { viewModel.setKeepScreenOn(it) },
                    onCancel = { viewModel.cancelExtraction() },
                    onBackground = { viewModel.dismissExtractionDialog() },
                    title = "QUANTUM EXTRACTION"
                )
            }

            // CONVERSION result feedback.
            val operationMessage by viewModel.operationMessage.collectAsState()
            if (operationMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
                        .neonGlass(cornerRadius = 16.dp, glowColor = TitanColors.NeonCyan)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = operationMessage!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (showColorDialog) {
                ColorSelectionDialog(
                    sectionName = "FILEMANAGER",
                    onColorSelected = { color, applyToAll ->
                        SectionColorManager.updateAccent(SectionColorManager.SECTION_FILEMANAGER, color, applyToAll)
                    },
                    onDismiss = { showColorDialog = false }
                )
            }
        }
    }
}
@Composable
fun StorageMetricsBar(stats: StorageStats) {
    val infiniteTransition = rememberInfiniteTransition(label = "storage")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "storage_glow"
    )
    val accentColor = SectionColorManager.fileAccent
    val statusColor = if (stats.percentUsed > 90) TitanColors.NeonRed else accentColor

    SovereignCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 16.dp,
        glassAlpha = 0.2f,
        glowRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "STORAGE CENTRAL",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "${stats.percentUsed}% USED",
                color = statusColor.copy(alpha = glowAlpha),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = stats.percentUsed / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = statusColor,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${stats.usedLabel} USED OF ${stats.totalLabel}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            // FREE SPACE: the amount still available, in GB, or in MB when it is less.
            Text(
                text = formatFreeSpace(stats.availableBytes),
                color = if (stats.percentUsed > 90) TitanColors.NeonRed else TitanColors.RadioactiveGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
}

/**
 * Formats the free space the way the user asked: whole gigabytes, and megabytes
 * when there is not a full gigabyte left.
 */
private fun formatFreeSpace(bytes: Long): String =
    com.elysium.vanguard.core.util.StorageMetrics.formatFreeSpace(bytes)

@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit = {},
    onExtract: () -> Unit = {},
    onConvert: () -> Unit = {},
    onCompress: () -> Unit = {},
    onRename: () -> Unit = {},
    onInvert: () -> Unit = {},
    onMore: () -> Unit = {}
) {
    val accentColor = SectionColorManager.fileAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TitanColors.AbsoluteBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (selectedCount == 1) "1 ITEM ARMED" else "$selectedCount ITEMS ARMED",
                    color = TitanColors.NeonRed,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "BATCH PROTOCOL ACTIVE",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = accentColor)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = accentColor)
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = accentColor)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TitanColors.NeonRed)
            }
        }

        // BATCH ACTION STRIP: extraction and conversion for the whole selection.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatchActionChip(Icons.Default.FolderOpen, "EXTRACT", TitanColors.RadioactiveGreen, onExtract)
            BatchActionChip(Icons.Default.PictureAsPdf, "TO PDF", TitanColors.NeonRed, onConvert)
            BatchActionChip(Icons.Default.Archive, "COMPRESS", TitanColors.NeonCyan, onCompress)
            BatchActionChip(Icons.Default.Edit, "RENAME", TitanColors.QuantumPink, onRename)
            BatchActionChip(Icons.Default.SwapHoriz, "INVERT", TitanColors.NeonYellow, onInvert)
            BatchActionChip(Icons.Default.MoreVert, "MÁS", Color.White, onMore)
        }
    }
}

@Composable
private fun BatchActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TacticalBreadcrumbs(
    path: String,
    onPathClick: (String) -> Unit
) {
    val accentColor = SectionColorManager.fileAccent
    val parts = path.split("/").filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(scrollState)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "ROOT",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onPathClick("/storage/emulated/0") }
        )

        parts.forEachIndexed { index, part ->
            Text(" > ", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
            val fullPath = "/" + parts.take(index + 1).joinToString("/")
            Text(
                text = part.uppercase(),
                color = if (index == parts.size - 1) SectionColorManager.fileAccent else Color.White,
                fontSize = 10.sp,
                fontWeight = if (index == parts.size - 1) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onPathClick(fullPath) }
            )
        }
    }
}

@Composable
private fun ShortcutsQuickAccess(
    shortcuts: List<Pair<String, String>>,
    onShortcutClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(shortcuts) { (name, path) ->
            val icon = when (name.lowercase()) {
                "whatsapp" -> Icons.Default.Chat
                "documents" -> Icons.Default.Description
                "music" -> Icons.Default.MusicNote
                "pictures" -> Icons.Default.Image
                "downloads" -> Icons.Default.Download
                "videos" -> Icons.Default.Movie
                "dcim" -> Icons.Default.CameraAlt
                else -> Icons.Default.Folder
            }
            val color = when (name.lowercase()) {
                "whatsapp" -> Color(0xFF25D366)
                "documents" -> SectionColorManager.fileAccent
                else -> TitanColors.RadioactiveGreen
            }

            SovereignLifeWrapper {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.1f))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onShortcutClick(path) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name.uppercase(),
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun BreadcrumbItem(
    name: String,
    onClick: () -> Unit,
    isFirst: Boolean = false
) {
    val accentColor = SectionColorManager.fileAccent
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isFirst) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = name.uppercase(),
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 4.dp),
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SovereignAccessDeniedScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack),
        contentAlignment = Alignment.Center
    ) {
        // Matrix Rain background for denied screen
        MatrixRain(
            color = TitanColors.NeonRed.copy(alpha = 0.4f),
            speed = 100L,
            trailLength = 12,
            alpha = 0.15f
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeonGlowIcon(
                icon = Icons.Default.Lock,
                color = TitanColors.NeonRed,
                size = 64.dp,
                glowRadius = 32.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ACCESS DENIED",
                color = TitanColors.NeonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "SOVEREIGN DATA AUTHORIZATION REQUIRED",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, TitanColors.NeonCyan)
            ) {
                Text("GRANT ROOT ACCESS", color = TitanColors.NeonCyan)
            }
        }
    }
}

@Composable
private fun TitanTopBar(
    title: String,
    currentPath: String,
    isTerminalMode: Boolean,
    onTerminalClick: () -> Unit,
    onBack: () -> Unit,
    accentColor: Color = SectionColorManager.fileAccent
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .neonGlass(cornerRadius = 12.dp, glowColor = accentColor.copy(alpha = 0.5f))
                .size(48.dp)
        ) {
            NeonGlowIcon(
                icon = Icons.Default.ArrowBack,
                color = Color.White,
                size = 22.dp,
                glowRadius = 8.dp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        TitanLogo(style = TitanLogoStyle.ICON, size = 44.dp)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = if (currentPath == "/storage/emulated/0") "Internal Storage" else currentPath.split("/").last(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        NeonGlowIcon(
            icon = Icons.Default.Terminal,
            color = if (isTerminalMode) TitanColors.RadioactiveGreen else accentColor,
            size = 24.dp,
            glowRadius = if (isTerminalMode) 16.dp else 10.dp,
            modifier = Modifier.clickable { onTerminalClick() }
        )
    }
}

@Composable
private fun NeuralSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .neonGlass(cornerRadius = 12.dp, glowColor = SectionColorManager.fileAccent.copy(alpha = 0.4f))
    ) {
        val accentColor = SectionColorManager.fileAccent
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Text(
                    "Search files...",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = TitanColors.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = TitanColors.NeonCyan,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            singleLine = true
        )
    }
}

@Composable
private fun StorageCategoryRow(
    onAddClick: () -> Unit,
    onPathClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(TitanColors.CarbonGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TitanColors.NeonCyan)
        }

        StorageChip(
            title = "Internal Storage",
            icon = Icons.Default.SdStorage,
            onClick = { onPathClick("/storage/emulated/0") }
        )

        StorageChip(
            title = "Downloads",
            icon = Icons.Default.Download,
            onClick = { onPathClick("/storage/emulated/0/Download") }
        )

        // NEW SHORTCUT: the videos folder that lives inside Downloads.
        StorageChip(
            title = "Videos",
            icon = Icons.Default.Movie,
            onClick = { onPathClick("/storage/emulated/0/Download/videos") }
        )

        StorageChip(
            title = "Termux",
            icon = Icons.Default.Terminal,
            onClick = { onPathClick("/data/data/com.termux/files/home") }
        )

        StorageChip(
            title = "WhatsApp",
            icon = Icons.Default.Share, // Using Share icon as generic chat/social icon
            onClick = { onPathClick("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media") }
        )
    }
}

@Composable
private fun StorageChip(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SovereignCard(
        modifier = Modifier
            .height(48.dp)
            .clickable { onClick() },
        cornerRadius = 12.dp,
        glassAlpha = 0.2f,
        glowRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonGlowIcon(
                icon = icon,
                color = TitanColors.NeonCyan,
                size = 18.dp,
                glowRadius = 8.dp
            )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactorFileCard(
    file: TitanFile,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SovereignCard(
            modifier = Modifier
                .size(90.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, TitanColors.NeonRed, RoundedCornerShape(24.dp))
                    } else {
                        Modifier
                    }
                ),
            cornerRadius = 24.dp,
            glassAlpha = if (isSelected) 0.25f else 0.2f,
            glowRadius = if (isSelected) 32.dp else 24.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(file.thematicColor.copy(alpha = 0.15f)), // Global vivid tint
                contentAlignment = Alignment.Center
            ) {
                // Enhanced Preview Logic: Images, Videos, APKs
                if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") || file.category == com.elysium.vanguard.core.util.FileCategory.SYSTEM) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file.path)
                            .crossfade(true)
                            .size(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(file.thematicColor.copy(alpha = 0.2f))  // Vivid fallback
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (file.isFolder) {
                    // 3D BREATHING FOLDER
                    BreathingFolderIcon(baseColor = file.thematicColor)
                } else {
                    // FALLBACK ICON FROM THEMATICS
                    NeonGlowIcon(
                        icon = com.elysium.vanguard.core.util.FileThematics.getCategoryIcon(file.category),
                        color = file.thematicColor,
                        size = 42.dp,
                        glowRadius = 16.dp
                    )
                }
            }

            // SELECTION INDICATOR
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TitanColors.NeonRed,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = file.name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SovereignOptionsDialog(
    file: TitanFile,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val ext = file.name.substringAfterLast(".", "").lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heif", "heic")
    val isDocument = ext in listOf("doc", "docx", "txt", "rtf", "csv", "html", "htm", "epub", "mobi", "md", "json", "xml")
    val isPdf = ext == "pdf"

    // Archive detection reads the container signature, not just the extension.
    val isArchive = remember(file.path) {
        runCatching { com.elysium.vanguard.core.archive.ArchiveEngine.isArchive(File(file.path)) }
            .getOrDefault(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(
                text = "SOVEREIGN OPTIONS",
                color = TitanColors.NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = file.name, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Universal extraction: every archive layout the engine supports.
                if (isArchive) {
                    OptionItem(Icons.Default.FolderOpen, "Extract Here", TitanColors.RadioactiveGreen) { onAction("EXTRACT") }
                    OptionItem(Icons.Default.Verified, "Verify Integrity", TitanColors.NeonCyan) { onAction("VERIFY") }
                }

                // Image and document conversions.
                if (isImage || isDocument) {
                    OptionItem(Icons.Default.Description, "Convert to PDF", TitanColors.NeonRed) { onAction("PDF") }
                }

                // PDF conversions.
                if (isPdf) {
                    OptionItem(Icons.Default.Image, "Convert to Images", TitanColors.QuantumPink) { onAction("PDF_TO_IMAGES") }
                    OptionItem(Icons.Default.EditNote, "Convert to Word", TitanColors.NeonCyan) { onAction("PDF_TO_WORD") }
                }

                // Compress: every archive layout the engine can write.
                if (!isArchive) {
                    OptionItem(Icons.Default.Archive, "Compress to…", TitanColors.NeonCyan) { onAction("ZIP") }
                }

                OptionItem(Icons.Default.Edit, "Rename", Color.White) { onAction("RENAME") }
                OptionItem(Icons.Default.Info, "Details", Color.White) { onAction("DETAILS") }
                OptionItem(Icons.Default.Share, "Share", Color.White) { onAction("SHARE") }
                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                OptionItem(Icons.Default.Delete, "Delete", TitanColors.NeonRed) { onAction("DELETE") }

                OptionItem(Icons.Default.Lock, "Secure Vault", Color.Yellow) { onAction("VAULT") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DISMISS", color = TitanColors.NeonCyan)
            }
        },
        modifier = Modifier
            .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
    )
}

@Composable
private fun OptionItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun AdvancedProgressDialog(
    state: CompressionState,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
    title: String = "QUANTUM COMPRESSION"
) {
    val isError = state.status == CompressionStatus.ERROR
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .neonGlass(cornerRadius = 24.dp, glowColor = if (isError) TitanColors.NeonRed else TitanColors.NeonCyan)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = if (isError) TitanColors.NeonRed else TitanColors.NeonCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pulse the progress indicator
            PulseContainer(pulseEnabled = true) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.status == CompressionStatus.SUCCESS) {
                        NeonGlowIcon(
                            icon = Icons.Default.CheckCircle,
                            color = TitanColors.RadioactiveGreen,
                            size = 80.dp,
                            glowRadius = 40.dp
                        )
                    } else if (isError) {
                        NeonGlowIcon(
                            icon = Icons.Default.ErrorOutline,
                            color = TitanColors.NeonRed,
                            size = 80.dp,
                            glowRadius = 40.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = state.percentage / 100f,
                            modifier = Modifier.size(120.dp),
                            color = TitanColors.NeonCyan,
                            strokeWidth = 8.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                    Text(
                        when {
                            state.status == CompressionStatus.SUCCESS -> "COMPLETE"
                            isError -> "FAILED"
                            else -> "${state.percentage}%"
                        },
                        color = when {
                            state.status == CompressionStatus.SUCCESS -> TitanColors.RadioactiveGreen
                            isError -> TitanColors.NeonRed
                            else -> Color.White
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                state.currentFile,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onKeepScreenOnChange(!keepScreenOn) }
            ) {
                Checkbox(
                    checked = keepScreenOn,
                    onCheckedChange = { onKeepScreenOnChange(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = TitanColors.NeonCyan,
                        uncheckedColor = Color.White.copy(alpha = 0.5f),
                        checkmarkColor = TitanColors.AbsoluteBlack
                    )
                )
                Text(
                    "KEEP SCREEN ON",
                    color = if (keepScreenOn) TitanColors.NeonCyan else Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state.status != CompressionStatus.SUCCESS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonRed.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, TitanColors.NeonRed)
                    ) {
                        Text("CANCEL", color = TitanColors.NeonRed, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onBackground,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, TitanColors.NeonCyan)
                    ) {
                        Text("BACKGROUND", color = TitanColors.NeonCyan, fontSize = 12.sp)
                    }
                }
            } else {
                 Button(
                    onClick = onBackground, // Dismiss
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TitanColors.RadioactiveGreen.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, TitanColors.RadioactiveGreen)
                ) {
                    Text("CLOSE", color = TitanColors.RadioactiveGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * BATCH RENAME
 *
 * Renames the whole selection with a template. The plan is previewed live and
 * conflicts are resolved before anything is written to the disk.
 */
@Composable
fun BatchRenameDialog(
    selectedCount: Int,
    onPreview: (BatchRenameEngine.Pattern) -> BatchRenameEngine.Plan,
    onExecute: (BatchRenameEngine.Pattern) -> String,
    onDismiss: () -> Unit
) {
    var template by remember { mutableStateOf("{name}_{counter}") }
    var padding by remember { mutableStateOf("3") }
    var startAt by remember { mutableStateOf("1") }
    var dateFormat by remember { mutableStateOf("yyyy-MM-dd") }
    var uppercaseExt by remember { mutableStateOf(false) }
    var conflict by remember {
        mutableStateOf(BatchRenameEngine.ConflictResolution.APPEND_SUFFIX)
    }
    var result by remember { mutableStateOf<String?>(null) }

    val pattern = BatchRenameEngine.Pattern(
        template = template,
        counterPadding = padding.toIntOrNull()?.coerceIn(1, 10) ?: 3,
        startAt = startAt.toIntOrNull()?.coerceAtLeast(0) ?: 1,
        dateFormat = dateFormat.ifBlank { "yyyy-MM-dd" },
        uppercaseExt = uppercaseExt,
        onConflict = conflict
    )

    val plan = remember(pattern, selectedCount) { onPreview(pattern) }
    val accentColor = TitanColors.NeonCyan

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(
                "BATCH RENAME · $selectedCount ITEMS",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("TEMPLATE") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accentColor
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "{counter} {name} {ext} {date} {parent} {size}",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = padding,
                        onValueChange = { padding = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("PAD") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = accentColor
                        )
                    )
                    OutlinedTextField(
                        value = startAt,
                        onValueChange = { startAt = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("START") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = accentColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("CONFLICT POLICY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SKIP" to BatchRenameEngine.ConflictResolution.SKIP,
                        "APPEND" to BatchRenameEngine.ConflictResolution.APPEND_SUFFIX,
                        "ABORT" to BatchRenameEngine.ConflictResolution.ABORT
                    ).forEach { (label, resolution) ->
                        val selected = conflict == resolution
                        Text(
                            text = label,
                            color = if (selected) accentColor else Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (selected) accentColor else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { conflict = resolution }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uppercaseExt,
                        onCheckedChange = { uppercaseExt = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor,
                            uncheckedColor = Color.White.copy(alpha = 0.4f)
                        )
                    )
                    Text(
                        "UPPERCASE EXTENSION",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "PREVIEW (${plan.renames.size} will be renamed)",
                    color = accentColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                plan.renames.take(6).forEach { rename ->
                    Text(
                        text = "${rename.original.name}  →  ${rename.renamed.name}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (plan.renames.size > 6) {
                    Text(
                        text = "+ ${plan.renames.size - 6} more...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (plan.skipped.isNotEmpty()) {
                    Text(
                        text = "${plan.skipped.size} file(s) will be SKIPPED (conflict)",
                        color = TitanColors.NeonYellow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                result?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = TitanColors.RadioactiveGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { result = onExecute(pattern) },
                enabled = plan.renames.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TitanColors.RadioactiveGreen.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, TitanColors.RadioactiveGreen)
            ) {
                Text("EXECUTE", color = TitanColors.RadioactiveGreen, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    )
}

/**
 * Password prompt for encrypted archives.
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    allowEmpty: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    val accentColor = TitanColors.NeonCyan

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(title, color = accentColor, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        },
        text = {
            Column {
                Text(
                    message,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("PASSWORD") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accentColor
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = allowEmpty || password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, accentColor)
            ) {
                Text("EXTRACT", color = accentColor, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TacticalFileRow(
    file: TitanFile,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateStr = remember(file.lastModified) { dateFormatter.format(Date(file.lastModified)) }

    // PERFORMANCE: the "life pulse" animation is disabled for list rows.
    // It used to spin two infinite transitions plus a 3D layer for every visible
    // row, which is what made long directories crawl.
    SovereignLifeWrapper(enabled = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(if (isSelected) TitanColors.NeonRed.copy(alpha = 0.1f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview: the picture, the video frame or the category icon.
            com.elysium.vanguard.ui.components.FileThumbnail(
                file = File(file.path),
                category = file.category,
                tint = file.thematicColor,
                size = 40.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.permissions,
                        color = TitanColors.NeonCyan.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    // The size lives right next to the date, for files and folders.
                    if (file.size.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "· ${file.size}",
                            color = TitanColors.NeonCyan.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Size column (kept: the size also appears next to the date).
            Text(
                text = file.size,
                color = if (file.isFolder) TitanColors.NeonCyan else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun handleFileAction(
    action: String,
    file: TitanFile,
    viewModel: FileManagerViewModel,
    scope: CoroutineScope,
    currentPath: String,
    onRenameRequest: (TitanFile) -> Unit,
    onExtract: (File) -> Unit = {},
    onConvert: (File, ConversionMode) -> Unit = { _, _ -> },
    onVerify: (File) -> Unit = {},
    onCompress: (File) -> Unit = {},
    onDelete: (File) -> Unit = {}
) {
    val srcFile = File(file.path)
    when (action) {
        "RENAME" -> onRenameRequest(file)
        "DETAILS" -> { /* Handled at call site for UI state */ }
        "DELETE" -> onDelete(srcFile)
        "COPY" -> viewModel.copySelected(listOf(file.path))
        "MOVE" -> viewModel.moveSelected(listOf(file.path))
        "SHARE" -> {
            // Share logic
        }
        "PDF" -> onConvert(srcFile, ConversionMode.TO_PDF)
        "PDF_TO_IMAGES" -> onConvert(srcFile, ConversionMode.TO_IMAGES)
        "PDF_TO_WORD" -> onConvert(srcFile, ConversionMode.TO_WORD)
        "ZIP" -> onCompress(srcFile)
        "EXTRACT" -> onExtract(srcFile)
        "UNZIP" -> onExtract(srcFile)
        "VERIFY" -> onVerify(srcFile)
        "VAULT" -> { /* Secure Vault logic */ }
    }
}

@Composable
fun TerminalList(
    files: List<TitanFile>,
    onFileClick: (TitanFile) -> Unit,
    onCalculateSize: (TitanFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.CarbonGray.copy(alpha=0.9f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                text = "ELYSIUM TERMINAL v2.1 // SYSTEM READY",
                color = TitanColors.RadioactiveGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(color = TitanColors.RadioactiveGreen, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(files) { file ->
            val color = if (file.isFolder) TitanColors.NeonCyan else file.thematicColor
            val prefix = if (file.isFolder) "DIR" else "FIL"

            // PERFORMANCE: no per-row infinite animation in the terminal list.
            SovereignLifeWrapper(enabled = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFileClick(file) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> $ ",
                        color = TitanColors.RadioactiveGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )

                    // Icon Preview in Terminal Mode
                    Icon(
                        imageVector = if (file.isFolder) Icons.Default.Folder else com.elysium.vanguard.core.util.FileThematics.getCategoryIcon(file.category),
                        contentDescription = null,
                        tint = color.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                    )

                    Text(
                        text = "[$prefix] ",
                        color = TitanColors.NeonYellow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = file.name,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = file.size,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            if (file.isFolder) onCalculateSize(file)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    file: TitanFile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(file.name) }
    val accentColor = SectionColorManager.fileAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = { Text("RENAME PROTOCOL", color = accentColor, fontFamily = FontFamily.Monospace) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlass(cornerRadius = 16.dp, glowColor = accentColor.copy(alpha = 0.1f)),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accentColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = accentColor,
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.1f)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, TitanColors.NeonCyan)
            ) {
                Text("EXECUTE", color = TitanColors.NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ABORT", color = Color.White.copy(alpha = 0.5f)) }
        }
    )
}

@Composable
fun FileDetailsDialog(
    file: TitanFile,
    onDismiss: () -> Unit
) {
    val accentColor = SectionColorManager.fileAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = { Text("FILE INTEL", color = accentColor, fontFamily = FontFamily.Monospace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("NAME", file.name)
                DetailRow("SIZE", file.size)
                DetailRow("TYPE", file.mimeType)
                DetailRow("PATH", file.path)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = TitanColors.NeonCyan) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val accentColor = SectionColorManager.fileAccent
    Column {
        Text(label, color = accentColor.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun TacticalHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon spacer
        Box(modifier = Modifier.size(40.dp))

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "NAME / PERMS",
            modifier = Modifier.weight(1f),
            color = TitanColors.NeonCyan.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "SIZE",
            color = TitanColors.NeonCyan.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 8.dp)
        )


    }
}

@Composable
fun LoadingDiagnosticPulse(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(TitanColors.NeonCyan.copy(alpha = 0.1f * alpha), CircleShape)
                .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.5f * alpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Refresh,
                null,
                tint = TitanColors.NeonCyan,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text,
            color = TitanColors.NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
fun RadarScanningPlaceholder(mainText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scanAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            listOf(0.3f, 0.6f, 0.9f).forEach { radiusScale ->
                Box(
                    modifier = Modifier
                        .fillMaxSize(radiusScale)
                        .border(0.5.dp, TitanColors.NeonCyan.copy(alpha = 0.1f), CircleShape)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.25f to TitanColors.NeonCyan.copy(alpha = 0.2f),
                        0.5f to Color.Transparent,
                        center = center
                    ),
                    startAngle = scanAnim * 360f,
                    sweepAngle = 90f,
                    useCenter = true
                )
            }

            Icon(
                Icons.Default.Radar,
                null,
                tint = TitanColors.NeonCyan.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            mainText,
            color = TitanColors.NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "SYNCHRONIZING WITH STORAGE CLUSTER...",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun StoragePermissionOverlay(context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .background(TitanColors.DeepVoidGradient, RoundedCornerShape(16.dp), alpha = 0.9f)
            .border(1.dp, TitanColors.NeonRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Security,
            null,
            tint = TitanColors.NeonRed,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "STORAGE ACCESS DENIED",
            color = TitanColors.NeonRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "CORE PROTOCOLS REQUIRE ALL FILES ACCESS TO MANAGE SYSTEM DATA. IDENTITY VERIFICATION PENDING.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val intent = android.content.Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION").apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonRed),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("AUTHORIZE ACCESS", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * An extraction that is waiting for the archive password before it starts.
 */
data class PendingExtraction(
    val files: List<File>,
    val destination: File
)

/**
 * A compression that is waiting for the archive password.
 */
data class PendingPasswordCompress(
    val files: List<File>,
    val output: File,
    val format: com.elysium.vanguard.core.util.CompressionFormat
)

/**
 * Formats a byte count for the storage pre-flight message.
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
}

/**
 * Search filters by file type. The token is appended to the query and understood
 * by the filter parser.
 */
private val SEARCH_TYPE_FILTERS: List<Pair<String, String?>> = listOf(
    "TODO" to null,
    "IMÁGENES" to "type:image",
    "VIDEO" to "type:video",
    "AUDIO" to "type:audio",
    "DOCS" to "type:doc",
    "ARCHIVOS" to "type:archive",
    "APPS" to "type:app",
    "CÓDIGO" to "type:code"
)

/**
 * GLOBAL SEARCH RESULTS
 *
 * Shows the live counters of the filesystem walk and the ranked results with
 * the matched characters highlighted.
 */
@Composable
private fun SearchResultsPanel(
    hits: List<com.elysium.vanguard.core.search.GlobalSearchEngine.Hit>,
    progress: com.elysium.vanguard.core.search.GlobalSearchEngine.Progress?,
    wholeDevice: Boolean,
    isSearching: Boolean,
    typeToken: String?,
    onTypeToken: (String?) -> Unit,
    sort: SearchSort,
    onSort: (SearchSort) -> Unit,
    onToggleScope: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit
) {
    val accentColor = TitanColors.NeonCyan

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── STATUS BAR ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .neonGlass(cornerRadius = 12.dp, glowColor = accentColor.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isSearching -> "SEARCHING…"
                        progress == null -> "SEARCH READY"
                        else -> "${progress.hits} RESULT(S)"
                    },
                    color = if (isSearching) TitanColors.NeonYellow else accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = progress?.let {
                        "${it.scannedFiles} files · ${it.scannedDirectories} folders · ${it.elapsedMs} ms"
                    } ?: "Type to search",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // SCOPE TOGGLE
            Text(
                text = if (wholeDevice) "WHOLE DEVICE" else "THIS FOLDER",
                color = if (wholeDevice) TitanColors.RadioactiveGreen else Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (wholeDevice) TitanColors.RadioactiveGreen.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (wholeDevice) TitanColors.RadioactiveGreen else Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleScope() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )

            if (isSearching) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CANCEL",
                    color = TitanColors.NeonRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCancel() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        // ── TYPE FILTERS ──
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(SEARCH_TYPE_FILTERS) { (label, token) ->
                val selected = typeToken == token
                Text(
                    text = label,
                    color = if (selected) TitanColors.AbsoluteBlack else Color.White.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) TitanColors.RadioactiveGreen else Color.White.copy(alpha = 0.06f)
                        )
                        .border(
                            1.dp,
                            if (selected) TitanColors.RadioactiveGreen else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onTypeToken(token) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }

        // ── SORT CONTROL ──
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SORT",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            listOf(
                SearchSort.RELEVANCE to "RELEVANCIA",
                SearchSort.RECENT to "RECIENTE",
                SearchSort.ALPHABETICAL to "A-Z"
            ).forEach { (option, label) ->
                val selected = sort == option
                Text(
                    text = label,
                    color = if (selected) TitanColors.AbsoluteBlack else Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) TitanColors.NeonCyan else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (selected) TitanColors.NeonCyan else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSort(option) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }

        if (isSearching) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                color = TitanColors.NeonYellow,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val ordered = remember(hits, sort) {
            GlobalSearchEngine.sort(hits, sort)
        }

        if (ordered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSearching) "SCANNING THE CLUSTER…" else "NO MATCHES FOUND",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            return@Column
        }

        val resultsState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = resultsState,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ordered, key = { it.file.absolutePath }) { hit ->
                val highlighted = remember(hit.file.absolutePath, hit.matchedIndices.toList()) {
                    buildAnnotatedString {
                        val name = hit.file.name
                        name.forEachIndexed { index, ch ->
                            if (hit.matchedIndices.contains(index)) {
                                withStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        color = TitanColors.NeonYellow,
                                        fontWeight = FontWeight.Black
                                    )
                                ) { append(ch) }
                            } else {
                                append(ch)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(hit.file.absolutePath) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PREVIEW: the picture, the video frame or the category icon.
                    com.elysium.vanguard.ui.components.FileThumbnail(
                        file = hit.file,
                        category = com.elysium.vanguard.core.util.FileThematics
                            .getCategory(hit.file.name, hit.file.isDirectory),
                        tint = accentColor,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = highlighted,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val parentPath = hit.relativePath
                        .substringBeforeLast('/', "")
                        .trimEnd('/')
                    if (parentPath.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = parentPath,
                            color = accentColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = android.text.format.Formatter.formatFileSize(
                            LocalContext.current,
                            hit.file.length()
                        ) + " · " + java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(java.util.Date(hit.file.lastModified())),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }
        com.elysium.vanguard.ui.components.SovereignScrollbar(
            state = resultsState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        }
    }
}

/**
 * DELETION PROGRESS
 *
 * Truthful percentage with a progress bar while large trees are removed.
 */
@Composable
private fun DeletionProgressDialog(
    state: DeletionState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (state.finished) onDismiss() },
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(
                if (state.finished) "DELETION COMPLETE" else "DELETING…",
                color = if (state.failed > 0) TitanColors.NeonYellow else TitanColors.NeonRed,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${state.percentage}%",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (state.finished) "${state.deleted} removed" else "in progress",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (state.failed > 0) TitanColors.NeonYellow else TitanColors.NeonRed,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.currentPath,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.finished && state.bytesFreed > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Freed " + android.text.format.Formatter.formatFileSize(
                            LocalContext.current,
                            state.bytesFreed
                        ),
                        color = TitanColors.RadioactiveGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (state.failed > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.failed} item(s) could not be removed",
                        color = TitanColors.NeonYellow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            if (state.finished) {
                TextButton(onClick = onDismiss) {
                    Text("CLOSE", color = TitanColors.NeonCyan, fontSize = 12.sp)
                }
            }
        }
    )
}

/**
 * COMPRESSION FORMAT PICKER
 *
 * Every archive layout the engine can write, with the formats that accept a
 * password marked as such.
 */
@Composable
private fun CompressFormatDialog(
    fileCount: Int,
    onPick: (com.elysium.vanguard.core.util.CompressionFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = TitanColors.NeonCyan
    val formats = com.elysium.vanguard.core.util.CompressionFormat.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(
                "COMPRESS TO…",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "$fileCount item(s) selected",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                formats.forEach { format ->
                    val usable = format.supportsMultipleFiles || fileCount == 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (usable) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (usable) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = usable) { onPick(format) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (format.supportsPassword) Icons.Default.Lock else Icons.Default.Archive,
                            contentDescription = null,
                            tint = if (usable) accentColor else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = format.displayName,
                                color = if (usable) Color.White else Color.White.copy(alpha = 0.3f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = buildString {
                                    append("." + format.extension)
                                    if (format.supportsPassword) append(" · password")
                                    if (!format.supportsMultipleFiles) append(" · single file only")
                                },
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    )
}

/**
 * COPY / MOVE PROGRESS
 *
 * Same truthful percentage and progress bar as the deletion flow.
 */
@Composable
private fun TransferProgressDialog(
    state: TransferState,
    onDismiss: () -> Unit
) {
    val title = if (state.mode == TransferMode.MOVE) "MOVING…" else "COPYING…"
    AlertDialog(
        onDismissRequest = { if (state.finished) onDismiss() },
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(
                if (state.finished) "TRANSFER COMPLETE" else title,
                color = if (state.failed > 0) TitanColors.NeonYellow else TitanColors.NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${state.percentage}%",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (state.finished) "${state.moved} transferred" else "in progress",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (state.failed > 0) TitanColors.NeonYellow else TitanColors.NeonCyan,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.currentPath,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.finished && state.bytesTransferred > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Transferred " + android.text.format.Formatter.formatFileSize(
                            LocalContext.current,
                            state.bytesTransferred
                        ),
                        color = TitanColors.RadioactiveGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (state.failed > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.failed} item(s) could not be transferred",
                        color = TitanColors.NeonYellow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            if (state.finished) {
                TextButton(onClick = onDismiss) {
                    Text("CLOSE", color = TitanColors.NeonCyan, fontSize = 12.sp)
                }
            }
        }
    )
}
