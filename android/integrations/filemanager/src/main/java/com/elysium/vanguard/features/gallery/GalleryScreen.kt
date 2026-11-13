package com.elysium.vanguard.features.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import com.elysium.vanguard.ui.components.TitanLogo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.neonGlass
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import com.elysium.vanguard.ui.components.NeonGlowIcon
import com.elysium.vanguard.ui.components.SovereignLifeWrapper
import com.elysium.vanguard.ui.components.SovereignCard
import com.elysium.vanguard.ui.components.GlassPillBadge
import com.elysium.vanguard.ui.components.SovereignGridScrollbar
import com.elysium.vanguard.ui.components.AnimatedEmptyState
import com.elysium.vanguard.ui.components.MatrixRain
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.elysium.vanguard.ui.components.TitanLogoStyle
import com.elysium.vanguard.ui.components.TitanHeader
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.components.ColorCustomizerIcon
import com.elysium.vanguard.ui.components.ColorSelectionDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 4 }
    var showColorDialog by remember { mutableStateOf(false) }
    val accentColor = SectionColorManager.galleryAccent

    val selectedAlbum by viewModel.selectedAlbum.collectAsState()

    val tabs = listOf("PHOTOS", "ALBUMS", "RECENT", "FAVORITES")
    val tabIcons = listOf(Icons.Default.Photo, Icons.Default.PhotoAlbum, Icons.Default.History, Icons.Default.Favorite)
    val tabColors = listOf(accentColor, accentColor, accentColor, accentColor)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TitanHeader(
                title = if (selectedAlbum != null) selectedAlbum!!.name else tabs[pagerState.currentPage],
                onBack = {
                    if (selectedAlbum != null) viewModel.setAlbum(null)
                    else onBack()
                },
                sectionName = "GALLERY"
            )
        },
        bottomBar = {
            // ── HOLOGRAPHIC TAB BAR ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlass(cornerRadius = 0.dp, glowColor = TitanColors.NeonCyan.copy(alpha = 0.1f))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        val color = tabColors[index]

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                .then(
                                    if (isSelected) Modifier
                                        .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                tabIcons[index],
                                contentDescription = null,
                                tint = if (isSelected) color else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = title,
                                color = if (isSelected) color else Color.White.copy(alpha = 0.3f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(TitanColors.DeepVoidGradient).padding(padding)) {
            MatrixRain(
                color = accentColor,
                alpha = 0.15f,
                speed = 50L,
                isMulticolor = SectionColorManager.isMulticolor
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PhotosTab(viewModel)
                    1 -> AlbumsTab(viewModel, onNavigateToAlbum)
                    2 -> RecentTab(viewModel)
                    3 -> FavoritesTab(viewModel)
                }
            }

            // ── COLOR CUSTOMIZER TRIGGER ──


            if (showColorDialog) {
                ColorSelectionDialog(
                    sectionName = "GALLERY",
                    onColorSelected = { color, applyToAll ->
                        SectionColorManager.updateAccent(SectionColorManager.SECTION_GALLERY, color, applyToAll)
                    },
                    onDismiss = { showColorDialog = false }
                )
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════
// TABS
// ══════════════════════════════════════════════════════════════

@Composable
private fun PhotosTab(viewModel: GalleryViewModel) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val groupedMedia = remember(mediaFiles) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        mediaFiles.groupBy { sdf.format(Date(it.dateModified * 1000L)) }
    }

    if (isLoading && mediaFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TitanColors.NeonCyan, strokeWidth = 2.dp)
        }
    } else if (mediaFiles.isEmpty()) {
        AnimatedEmptyState(
            icon = Icons.Default.PhotoCamera,
            message = "NO PHOTOS DETECTED",
            color = TitanColors.NeonCyan
        )
    } else {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 90.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((date, files) in groupedMedia) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GlassPillBadge(
                            text = date.uppercase(),
                            color = TitanColors.NeonCyan,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                    }
                    items(
                        count = files.size,
                        key = { index -> files[index].id },
                        span = { GridItemSpan(1) }
                    ) { index ->
                        val media = files[index]
                        MediaThumbnail(
                            media = media,
                            onClick = { viewModel.openMedia(media) },
                            onOptionClick = { action ->
                                when (action) {
                                    "FAVORITE" -> viewModel.toggleFavorite(media.id)
                                    "DELETE" -> viewModel.deleteMedia(media)
                                    "SHARE" -> viewModel.shareMedia(media)
                                    "EDIT" -> viewModel.editMedia(media)
                                    "WALLPAPER" -> viewModel.setWallpaper(media)
                                }
                            }
                        )
                    }
                }
            }
            SovereignGridScrollbar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun AlbumsTab(viewModel: GalleryViewModel, onAlbumClick: (String) -> Unit) {
    val albums by viewModel.albums.collectAsState()

    if (albums.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Default.PhotoAlbum, message = "NO ALBUMS FOUND", color = TitanColors.QuantumPink)
    } else {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums) { album ->
                    SovereignLifeWrapper {
                        AlbumItem(album, onClick = { onAlbumClick(album.name) })
                    }
                }
            }
            SovereignGridScrollbar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun AlbumItem(album: GalleryAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        SovereignCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 16.dp,
            glassAlpha = 0.35f,
            glowRadius = 20.dp
        ) {
            if (album.media.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.media.first().path)
                        .crossfade(true)
                        .size(400)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TitanColors.QuantumPink.copy(alpha = 0.2f)), // Vivid fallback
                    contentScale = ContentScale.Crop
                )
            }
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )
            // Count badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(TitanColors.QuantumPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, TitanColors.QuantumPink.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("${album.media.size}", color = TitanColors.QuantumPink, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(album.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            "${album.media.size} items",
            color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RecentTab(viewModel: GalleryViewModel) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val recent = mediaFiles.take(50)

    if (recent.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Default.History, message = "NO RECENT MEDIA", color = TitanColors.RadioactiveGreen)
    } else {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 90.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    count = recent.size,
                    key = { index -> recent[index].id }
                ) { index ->
                    val media = recent[index]
                    MediaThumbnail(
                        media = media,
                        onClick = { viewModel.openMedia(media) },
                        onOptionClick = { action ->
                            if (action == "FAVORITE") viewModel.toggleFavorite(media.id)
                        }
                    )
                }
            }
            SovereignGridScrollbar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun FavoritesTab(viewModel: GalleryViewModel) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val favorites = mediaFiles.filter { it.isFavorite }

    if (favorites.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Default.FavoriteBorder, message = "NO FAVORITES YET", color = TitanColors.NeonRed)
    } else {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 90.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    count = favorites.size,
                    key = { index -> favorites[index].id }
                ) { index ->
                    val media = favorites[index]
                    MediaThumbnail(
                        media = media,
                        onClick = { viewModel.openMedia(media) },
                        onOptionClick = { action ->
                            if (action == "FAVORITE") viewModel.toggleFavorite(media.id)
                        }
                    )
                }
            }
            SovereignGridScrollbar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// MEDIA THUMBNAIL
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    media: GalleryMedia,
    onClick: () -> Unit,
    onOptionClick: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showDetails) {
        MediaMetadataDialog(media = media, onDismiss = { showDetails = false })
    }

    SovereignCard(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        cornerRadius = 10.dp,
        glassAlpha = 0.12f,
        glowRadius = 10.dp
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.path)
                .crossfade(true)
                .size(300)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(TitanColors.NeonCyan.copy(alpha = 0.2f)), // Vivid fallback
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                        startY = 120f
                    )
                )
        )

        // Favorite indicator
        if (media.isFavorite) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = TitanColors.NeonRed,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(14.dp)
            )
        }

        // Options menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier
                .background(TitanColors.CarbonGray)
                .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.3f))
        ) {
            DropdownMenuItem(
                text = { Text("Favorite", color = TitanColors.NeonCyan, fontSize = 12.sp) },
                onClick = { showMenu = false; onOptionClick("FAVORITE") },
                leadingIcon = { Icon(Icons.Default.Favorite, null, tint = TitanColors.NeonRed, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Details", color = Color.White, fontSize = 12.sp) },
                onClick = { showMenu = false; showDetails = true },
                leadingIcon = { Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Set Wallpaper", color = Color.White, fontSize = 12.sp) },
                onClick = { showMenu = false; onOptionClick("WALLPAPER") },
                leadingIcon = { Icon(Icons.Default.Wallpaper, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Edit", color = Color.White, fontSize = 12.sp) },
                onClick = { showMenu = false; onOptionClick("EDIT") },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Share", color = Color.White, fontSize = 12.sp) },
                onClick = { showMenu = false; onOptionClick("SHARE") },
                leadingIcon = { Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = TitanColors.NeonRed, fontSize = 12.sp) },
                onClick = { showMenu = false; onOptionClick("DELETE") },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = TitanColors.NeonRed, modifier = Modifier.size(16.dp)) }
            )
        }

        // Video play button and badge
        val isVideo = media.mimeType.startsWith("video/")
        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TitanColors.NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Duration badge (top end)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        "VID",
                        color = TitanColors.NeonCyan,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// DETAILS DIALOG
// ══════════════════════════════════════════════════════════════

@Composable
private fun MediaMetadataDialog(
    media: GalleryMedia,
    onDismiss: () -> Unit
) {
    val accentColor = SectionColorManager.galleryAccent
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MEDIA METADATA", color = accentColor, fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GalleryDetailRow("Name", media.name, accentColor)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.05f))
                GalleryDetailRow("Path", media.path, accentColor)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.Transparent)
                GalleryDetailRow("MIME Type", media.mimeType, accentColor)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.Transparent)
                GalleryDetailRow("Date Modified", dateFormat.format(Date(media.dateModified * 1000)), accentColor)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = TitanColors.AbsoluteBlack.copy(alpha = 0.95f)
    )
}

@Composable
private fun GalleryDetailRow(label: String, value: String, accentColor: Color) {
    Column {
        Text(label, color = accentColor.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}
