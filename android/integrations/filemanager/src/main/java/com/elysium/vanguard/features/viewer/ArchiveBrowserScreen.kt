package com.elysium.vanguard.features.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.core.archive.ArchiveEngine
import com.elysium.vanguard.core.archive.ArchiveEntry
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.archive.ArchiveManifest
import com.elysium.vanguard.core.archive.ArchiveSetResolver
import com.elysium.vanguard.ui.theme.TitanColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ARCHIVE BROWSER
 *
 * Opens any archive natively inside the file manager: the catalogue is read
 * without extracting anything, and the user can browse the tree and extract
 * the whole set with one action.
 */
@Composable
fun ArchiveBrowserScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val file = remember(filePath) { File(filePath) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var manifest by remember { mutableStateOf<ArchiveManifest?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var selectedEntries by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun load(withPassword: String?) {
        val result = withContext(Dispatchers.IO) {
            ArchiveEngine.inspect(file, withPassword)
        }
        result
            .onSuccess {
                manifest = it
                failure = null
                needsPassword = false
            }
            .onFailure { error ->
                when (error) {
                    is ArchiveException.PasswordRequired -> {
                        needsPassword = true
                        showPasswordPrompt = true
                    }
                    is ArchiveException.WrongPassword -> {
                        failure = "WRONG PASSWORD"
                        showPasswordPrompt = true
                    }
                    else -> failure = error.message ?: "UNABLE TO READ ARCHIVE"
                }
            }
    }

    LaunchedEffect(filePath) { load(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack)
    ) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitanColors.CarbonGray.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                manifest?.let { m ->
                    Text(
                        text = "${m.format.displayName} · ${m.fileCount} FILES · ${m.directoryCount} FOLDERS",
                        color = TitanColors.NeonCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (manifest != null) {
                Button(
                    onClick = {
                        scope.launch {
                            val set = withContext(Dispatchers.IO) { ArchiveSetResolver.resolve(file) }
                            // The engine creates one folder per archive inside the parent.
                            val destination = file.parentFile
                            val intent = android.content.Intent(
                                appContext,
                                com.elysium.vanguard.core.services.ArchiveService::class.java
                            )
                            intent.action = com.elysium.vanguard.core.services.ArchiveService.ACTION_EXTRACT
                            intent.putExtra(
                                com.elysium.vanguard.core.services.ArchiveService.EXTRA_FILES,
                                set.parts.ifEmpty { listOf(file) }.map { it.absolutePath }.toTypedArray()
                            )
                            intent.putExtra(
                                com.elysium.vanguard.core.services.ArchiveService.EXTRA_DESTINATION,
                                destination.absolutePath
                            )
                            if (selectedEntries.isNotEmpty()) {
                                intent.putExtra(
                                    com.elysium.vanguard.core.services.ArchiveService.EXTRA_INCLUDE,
                                    selectedEntries.toTypedArray()
                                )
                            }
                            if (password.isNotEmpty()) {
                                intent.putExtra(
                                    com.elysium.vanguard.core.services.ArchiveService.EXTRA_PASSWORD,
                                    password
                                )
                            }
                            appContext.startForegroundService(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TitanColors.RadioactiveGreen.copy(alpha = 0.2f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TitanColors.RadioactiveGreen)
                ) {
                    Text(
                        if (selectedEntries.isEmpty()) "EXTRACT" else "EXTRACT ${selectedEntries.size}",
                        color = TitanColors.RadioactiveGreen,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // CONTENT
        when {
            showPasswordPrompt -> {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = TitanColors.NeonYellow,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = failure ?: "PASSWORD REQUIRED",
                        color = TitanColors.NeonYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("PASSWORD") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TitanColors.NeonCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = TitanColors.NeonCyan
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { scope.launch { load(password) } },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TitanColors.NeonCyan)
                        ) {
                            Text("UNLOCK", color = TitanColors.NeonCyan, fontSize = 12.sp)
                        }
                        androidx.compose.material3.TextButton(onClick = onBack) {
                            Text("BACK", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }
            }

            manifest == null && failure != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = failure ?: "",
                        color = TitanColors.NeonRed,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            manifest == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "READING ARCHIVE CATALOGUE...",
                        color = TitanColors.NeonCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            else -> {
                val entries = manifest!!.entries.sortedWith(
                    compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.path.lowercase() }
                )
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "ARCHIVE IS EMPTY",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            val isSelected = selectedEntries.contains(entry.path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) TitanColors.NeonCyan.copy(alpha = 0.12f) else Color.Transparent
                                    )
                                    .clickable {
                                        selectedEntries = if (isSelected) {
                                            selectedEntries - entry.path
                                        } else {
                                            selectedEntries + entry.path
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) TitanColors.NeonCyan else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = entry.path,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = formatSize(entry.size),
                                    color = TitanColors.NeonCyan.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TitanColors.RadioactiveGreen,
                                        modifier = Modifier.size(16.dp)
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

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024L * 1024 * 1024)} GB"
}
