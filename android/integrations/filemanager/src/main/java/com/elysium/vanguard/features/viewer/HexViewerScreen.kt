package com.elysium.vanguard.features.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.elysium.vanguard.ui.theme.TitanColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UNIVERSAL FILE VIEWER
 *
 * The native in-app viewer used for every file type the specialised viewers do
 * not own. It renders readable content as text when the payload is text, and as
 * a classic hex dump when it is binary. Nothing is ever lost and the user is
 * never forced out of the file manager.
 */
@Composable
fun HexViewerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val file = remember(filePath) { File(filePath) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isText by remember { mutableStateOf(false) }
    var sizeLabel by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.isFile) throw IllegalStateException("FILE NOT FOUND")
                val head = ByteArray(MAX_READ)
                val read = file.inputStream().use { it.read(head) }
                val payload = if (read > 0) head.copyOf(read) else ByteArray(0)
                val textual = payload.isNotEmpty() && payload.all { b ->
                    val v = b.toInt() and 0xFF
                    v == 0x09 || v == 0x0A || v == 0x0D || (v in 0x20..0x7E) || v >= 0x80
                } && payload.none { (it.toInt() and 0xFF) == 0 }
                val rendered = if (textual) {
                    payload.toString(Charsets.UTF_8).lines()
                } else {
                    payload.toHexDump()
                }
                Triple(rendered, textual, file.length())
            }
        }
        result
            .onSuccess { (rendered, textual, total) ->
                lines = rendered
                isText = textual
                sizeLabel = formatViewerSize(total)
            }
            .onFailure { error -> failure = error.message ?: "UNABLE TO READ FILE" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitanColors.CarbonGray.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                Text(
                    text = if (failure != null) "UNREADABLE" else "$sizeLabel · ${if (isText) "TEXT" else "BINARY"} VIEW",
                    color = if (failure != null) TitanColors.NeonRed else TitanColors.NeonCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = {
                    runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.filemanager.provider",
                            file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Open with")
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, TitanColors.NeonCyan)
            ) {
                Text("OTHER APP", color = TitanColors.NeonCyan, fontSize = 10.sp)
            }
        }

        if (failure != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = failure ?: "",
                    color = TitanColors.NeonRed,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else if (lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "EMPTY FILE",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { index, line ->
                    Text(
                        text = line,
                        color = if (isText) Color.White.copy(alpha = 0.85f) else TitanColors.NeonCyan.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private const val MAX_READ = 256 * 1024

/**
 * Renders a classic hex dump: offset, hex bytes and the printable ASCII column.
 */
private fun ByteArray.toHexDump(): List<String> {
    val out = ArrayList<String>(size / 16 + 1)
    var offset = 0
    while (offset < size) {
        val chunk = copyOfRange(offset, minOf(offset + 16, size))
        val hex = chunk.joinToString(" ") { "%02X".format(it) }.padEnd(47, ' ')
        val ascii = chunk.map { b ->
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7E) v.toChar() else '.'
        }.joinToString("")
        out += "%08X  %s  |%s|".format(offset, hex, ascii)
        offset += 16
    }
    return out
}

private fun formatViewerSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024L * 1024 * 1024)} GB"
}
