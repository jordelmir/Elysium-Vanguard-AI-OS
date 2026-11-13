package com.elysium.vanguard.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.elysium.vanguard.core.util.FileCategory
import java.io.File

/**
 * Shared image loader that can decode video frames.
 *
 * Coil does not register the video decoder by default, so the thumbnails of
 * video results would stay empty without this.
 */
object TitanImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components { add(VideoFrameDecoder.Factory()) }
                .crossfade(true)
                .build()
                .also { instance = it }
        }
}

/**
 * FILE THUMBNAIL
 *
 * Renders a real preview whenever the format allows it:
 *  - images  → the picture itself
 *  - videos  → the first decoded frame
 *  - everything else → the icon of its category
 */
@Composable
fun FileThumbnail(
    file: File,
    category: FileCategory,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { TitanImageLoader.get(context) }
    val isImage = category == FileCategory.IMAGE
    val isVideo = category == FileCategory.VIDEO

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isImage || isVideo) {
            AsyncImage(
                model = file,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = iconFor(category),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** The icon that represents a category when no preview can be rendered. */
@Composable
fun iconFor(category: FileCategory): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    FileCategory.IMAGE -> Icons.Default.Image
    FileCategory.VIDEO -> Icons.Default.Movie
    FileCategory.AUDIO -> Icons.Default.Audiotrack
    FileCategory.DOCUMENT -> Icons.Default.Description
    FileCategory.FOLDER -> Icons.Default.Folder
    else -> Icons.Default.InsertDriveFile
}
