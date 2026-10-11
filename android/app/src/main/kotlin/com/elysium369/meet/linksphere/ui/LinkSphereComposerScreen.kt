package com.elysium369.meet.linksphere.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.linksphere.domain.*
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Pantalla de Creación de Cápsula: Elysium LinkSphere Composer.
 * Permite pegar enlaces universales, validar su estructura de seguridad
 * y categorizarlos en la red social con comentarios técnicos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSphereComposerScreen(
    viewModel: LinkSphereViewModel,
    onNavigateBack: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember { mutableStateOf("") }
    var captionInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LinkCategory.AUTOMOTIVE) }
    var tagsInput by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }

    val resolvedLink = remember(urlInput) {
        if (urlInput.isNotBlank()) SafeLinkResolver.resolve(urlInput) else null
    }

    Scaffold(
        containerColor = Color(0xFF040A12),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Crear Cápsula de Enlace", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
                        Text("Elysium LinkSphere · Capa Social", fontSize = 10.sp, color = MeetColors.cyberCyan)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07121F))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // URL Input Card
            Surface(
                color = Color(0xFF091626),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔗 Enlace Externo:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        TextButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    urlInput = clipText
                                }
                            }
                        ) {
                            Text("Pegar Portapapeles", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://youtube.com/..., spotify.com/..., x.com/...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                        )
                    )
                }
            }

            // Real-Time Resolved Link Card Preview
            resolvedLink?.let { link ->
                Surface(
                    color = Color(0xFF06101D),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (link.resolution == LinkResolution.INVALID) MeetColors.error
                        else MeetColors.neonGreen.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(link.provider.iconEmoji, fontSize = 14.sp)
                                Text(
                                    text = link.provider.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "(${link.contentType.label})",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = link.resolution.label,
                                color = if (link.resolution.allowsInlinePlay) MeetColors.neonGreen else MeetColors.warning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        link.uncertaintyLabel?.let { err ->
                            Text(text = "Aviso: $err", color = MeetColors.error, fontSize = 9.sp)
                        }

                        Text(
                            text = "Destino canónico: ${link.canonicalUrl}",
                            color = MeetColors.textMuted,
                            fontSize = 8.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // User Commentary / Caption
            OutlinedTextField(
                value = captionInput,
                onValueChange = { captionInput = it },
                label = { Text("Comentario, análisis técnico o recomendación") },
                placeholder = { Text("Explica por qué es útil o relevante esta información...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                )
            )

            // Category Selection
            Text("Categoría:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinkCategory.values().filterNot { it == LinkCategory.ALL }.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        onClick = { selectedCategory = cat },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.25f) else Color(0xFF0C1E30),
                        border = BorderStroke(1.dp, if (isSelected) MeetColors.neonGreen else Color(0x1FFFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(cat.iconEmoji, fontSize = 12.sp)
                            Text(
                                text = cat.title,
                                color = if (isSelected) MeetColors.neonGreen else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Tags Input
            OutlinedTextField(
                value = tagsInput,
                onValueChange = { tagsInput = it },
                label = { Text("Etiquetas (separadas por coma)") },
                placeholder = { Text("Ej: OBD2, Peritaje, Electrónica, Frenos") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                )
            )

            Spacer(Modifier.height(10.dp))

            // Publish Button
            val canPublish = resolvedLink != null && resolvedLink.resolution != LinkResolution.INVALID && !isPublishing
            Button(
                onClick = {
                    if (canPublish && resolvedLink != null) {
                        isPublishing = true
                        val tags = tagsInput.split(',').map { it.trim() }.filter { it.isNotBlank() }
                        viewModel.publishCapsule(
                            url = urlInput,
                            caption = captionInput,
                            category = selectedCategory,
                            tags = tags,
                            onSuccess = {
                                isPublishing = false
                                onNavigateBack()
                            }
                        )
                    }
                },
                enabled = canPublish,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (isPublishing) "PUBLICANDO..." else "PUBLICAR CÁPSULA SOCIAL",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
