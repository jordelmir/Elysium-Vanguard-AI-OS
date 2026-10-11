package com.elysium369.meet.linksphere.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.linksphere.domain.*
import com.elysium369.meet.linksphere.platform.ProviderEmbedWebView
import com.elysium369.meet.linksphere.platform.SafeLinkLauncher
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Pantalla Principal de Feed: Elysium LinkSphere.
 * Red social universal de enlaces integrada en Elysium Vanguard AI OS.
 * Estilo visual Cyberpunk 3D con colores neón de alto impacto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSphereFeedScreen(
    viewModel: LinkSphereViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToComposer: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val posts by viewModel.feedPosts.collectAsState()

    var activeCommentPostId by remember { mutableStateOf<String?>(null) }
    var inlinePlayerPostId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color(0xFF040A12),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF07121F))
                    .padding(bottom = 6.dp)
            ) {
                // Barra superior de navegación y acciones
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🪐", fontSize = 18.sp)
                                Text(
                                    text = "ELYSIUM LINKSPHERE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                text = "Red Social Universal de Enlaces",
                                color = MeetColors.cyberCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = MeetColors.textSecondary)
                        }
                        IconButton(
                            onClick = onNavigateToComposer,
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(MeetColors.neonGreen, MeetColors.cyberCyan)
                                    ),
                                    shape = CircleShape
                                )
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Nueva Cápsula", tint = Color.Black)
                        }
                    }
                }

                // Buscador de contenido
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar enlaces, temas, autores...", fontSize = 11.sp, color = MeetColors.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = Color(0xFF0A192B),
                        unfocusedContainerColor = Color(0xFF081423),
                    )
                )

                // Tabs de Feed (Para Ti, Siguiendo, Tendencias, Categorías)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinkFeedTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            onClick = { viewModel.setTab(tab) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else Color(0xFF0A1726),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MeetColors.neonGreen else Color(0x22FFFFFF)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) MeetColors.neonGreen else MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Fila de Categorías (scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinkCategory.values().forEach { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            onClick = { viewModel.setCategory(category) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.25f) else Color(0xFF0C1D30),
                            border = BorderStroke(1.dp, if (isSelected) MeetColors.cyberCyan else Color(0x1FFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(category.iconEmoji, fontSize = 11.sp)
                                Text(
                                    text = category.title,
                                    color = if (isSelected) Color.White else MeetColors.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToComposer,
                containerColor = MeetColors.neonGreen,
                contentColor = Color.Black,
                shape = RoundedCornerShape(14.dp),
                icon = { Icon(Icons.Default.AddLink, contentDescription = null) },
                text = { Text("CREAR CÁPSULA", fontWeight = FontWeight.Black, fontSize = 11.sp) }
            )
        }
    ) { padding ->
        if (posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("🪐", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No hay cápsulas en esta sección",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "¡Sé el primero en compartir un enlace técnico o de interés!",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onNavigateToComposer,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("COMPARTIR ENLACE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    LinkCapsuleCard(
                        post = post,
                        isPlayerActive = inlinePlayerPostId == post.id,
                        onTogglePlayer = {
                            inlinePlayerPostId = if (inlinePlayerPostId == post.id) null else post.id
                        },
                        onReaction = { type -> viewModel.toggleReaction(post.id, type) },
                        onSave = { viewModel.toggleSave(post.id) },
                        onFollow = { viewModel.toggleFollow(post.authorId) },
                        onOpenExternal = {
                            SafeLinkLauncher.open(context, post.link)
                        },
                        onOpenComments = { activeCommentPostId = post.id },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, post.metadata.title)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${post.metadata.title}\n${post.link.canonicalUrl}\nCompartido desde Elysium LinkSphere"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir cápsula"))
                        }
                    )
                }
            }
        }
    }

    // Modal de comentarios si está seleccionado
    activeCommentPostId?.let { postId ->
        val post = posts.firstOrNull { it.id == postId }
        if (post != null) {
            CommentsDialog(
                post = post,
                onDismiss = { activeCommentPostId = null },
                onAddComment = { text -> viewModel.addComment(postId, text) }
            )
        }
    }
}

/**
 * Tarjeta de Cápsula de Enlace (Link Capsule Card) con estética Cyber Neon 3D.
 */
@Composable
fun LinkCapsuleCard(
    post: LinkPost,
    isPlayerActive: Boolean,
    onTogglePlayer: () -> Unit,
    onReaction: (LinkReactionType) -> Unit,
    onSave: () -> Unit,
    onFollow: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091421)),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Autor y Seguimiento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(MeetColors.cyberCyan, Color(0xFF06334F))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", fontSize = 14.sp)
                    }
                    Column {
                        Text(
                            text = post.authorName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = post.authorHandle,
                            color = MeetColors.cyberCyan,
                            fontSize = 9.sp
                        )
                    }
                }

                Surface(
                    onClick = onFollow,
                    shape = RoundedCornerShape(6.dp),
                    color = if (post.isAuthorFollowed) MeetColors.neonGreen.copy(alpha = 0.2f) else Color(0xFF0E2238),
                    border = BorderStroke(1.dp, if (post.isAuthorFollowed) MeetColors.neonGreen else Color(0x33FFFFFF))
                ) {
                    Text(
                        text = if (post.isAuthorFollowed) "SIGUIENDO ✓" else "+ SEGUIR",
                        color = if (post.isAuthorFollowed) MeetColors.neonGreen else Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Badge de Proveedor y Tipo de Contenido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0C1E33),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(post.link.provider.iconEmoji, fontSize = 10.sp)
                        Text(
                            text = "${post.link.provider.displayName} · ${post.link.contentType.label}",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = post.link.resolution.label,
                    color = if (post.link.resolution.allowsInlinePlay) MeetColors.cyberCyan else MeetColors.textMuted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Comentario / Explicación del usuario
            if (post.userCaption.isNotBlank()) {
                Text(
                    text = post.userCaption,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                )
            }

            // Contenedor Multimedia / Embed Player / Poster
            if (isPlayerActive && post.link.resolution.allowsInlinePlay) {
                ProviderEmbedWebView(
                    link = post.link,
                    onOpenExternal = onOpenExternal
                )
            } else {
                Surface(
                    onClick = {
                        if (post.link.resolution.allowsInlinePlay) onTogglePlayer()
                        else onOpenExternal()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF06101D),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = post.metadata.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (post.link.resolution.allowsInlinePlay) MeetColors.neonGreen else MeetColors.cyberCyan,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (post.link.resolution.allowsInlinePlay) Icons.Default.PlayArrow else Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        post.metadata.description?.let { desc ->
                            Text(
                                text = desc,
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "🔗 ${post.link.host}",
                            color = MeetColors.cyberCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tags
            if (post.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    post.tags.take(4).forEach { tag ->
                        Text(
                            text = "#$tag",
                            color = MeetColors.cyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Barra de Interacción Inferior (Reacciones, Comentarios, Guardados, Compartir)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reacciones rápidas
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { onReaction(LinkReactionType.FIRE) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (post.userReaction == LinkReactionType.FIRE) MeetColors.neonGreen.copy(alpha = 0.2f) else Color(0xFF0C1929),
                        border = BorderStroke(1.dp, if (post.userReaction == LinkReactionType.FIRE) MeetColors.neonGreen else Color(0x1FFFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("🔥", fontSize = 11.sp)
                            Text(
                                text = "${post.reactionsCount}",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Comentarios
                    Surface(
                        onClick = onOpenComments,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0C1929),
                        border = BorderStroke(1.dp, Color(0x1FFFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("💬", fontSize = 11.sp)
                            Text(
                                text = "${post.commentsCount}",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Guardar / Bookmark
                    Surface(
                        onClick = onSave,
                        shape = RoundedCornerShape(8.dp),
                        color = if (post.isSaved) MeetColors.cyberCyan.copy(alpha = 0.2f) else Color(0xFF0C1929),
                        border = BorderStroke(1.dp, if (post.isSaved) MeetColors.cyberCyan else Color(0x1FFFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(if (post.isSaved) "🔖" else "📑", fontSize = 11.sp)
                            Text(
                                text = "${post.savesCount}",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Salida Externa y Compartir
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MeetColors.textSecondary, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onOpenExternal, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Launch, contentDescription = "Abrir externo", tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de comentarios para una cápsula de enlace.
 */
@Composable
fun CommentsDialog(
    post: LinkPost,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
) {
    var commentText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Comentarios de la Cápsula", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                Text(post.metadata.title, fontSize = 10.sp, color = MeetColors.cyberCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Escribe una observación técnica...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (commentText.isNotBlank()) {
                        onAddComment(commentText)
                        commentText = ""
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
            ) {
                Text("PUBLICAR", fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR", color = Color.White, fontSize = 10.sp)
            }
        },
        containerColor = Color(0xFF081423),
        shape = RoundedCornerShape(16.dp)
    )
}
