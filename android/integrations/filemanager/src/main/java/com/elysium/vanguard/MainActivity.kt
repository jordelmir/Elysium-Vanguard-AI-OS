@file:SuppressLint("UnsafeOptInUsageError")

package com.elysium.vanguard

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elysium.vanguard.features.filemanager.FileManagerViewModel
import com.elysium.vanguard.features.filemanager.FileManagerEvent
import com.elysium.vanguard.features.player.NativeMediaPlayer
import com.elysium.vanguard.features.viewer.HighFidelityImageViewer
import com.elysium.vanguard.features.viewer.IntegratedDocumentViewer
import com.elysium.vanguard.ui.theme.TitanTheme
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch
import com.elysium.vanguard.core.util.FileOpenerUtil

/**
 * PROJECT TITAN: ELYSIUM VANGUARD
 * The entry point for the next-generation sovereign file manager.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Routes that own the full screen: the global "back to Elysium" bar is
         * hidden so the video and the viewers are not obstructed.
         */
        private val IMMERSIVE_ROUTES = setOf(
            "player/{path}/{mimeType}",
            "viewer_image/{path}",
            "editor_image/{path}",
            "viewer_music/{path}",
            "viewer_pdf/{path}",
            "viewer_doc/{path}",
            "viewer_archive/{path}",
            "viewer_hex/{path}",
            "album_detail/{albumName}"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the saved accent selection (global theme + individual overrides)
        // so the colors the client chose really survive a restart.
        com.elysium.vanguard.ui.theme.SectionColorManager.init(this)

        checkAndRequestStorageRoot()

        setContent {
            val fileManagerViewModel: FileManagerViewModel = hiltViewModel()
            val galleryViewModel: com.elysium.vanguard.features.gallery.GalleryViewModel = hiltViewModel()
            val musicHubViewModel: com.elysium.vanguard.features.player.MusicHubViewModel = hiltViewModel()
            val navController = rememberNavController()

            TitanTheme {
                // IMMERSIVE ROUTES: the global "back to Elysium" bar is hidden so the
                // video and the document viewers own the whole screen.
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val isImmersive = currentRoute in IMMERSIVE_ROUTES

                Column(Modifier.fillMaxSize()) {
                    if (!isImmersive) {
                        // STATUS BAR INSET: the global back bar is drawn at the very
                        // top of the window, so it must respect the system bar
                        // inset or it renders underneath the clock and the battery.
                        TextButton(
                            onClick = { finish() },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                        ) { Text("← Volver a Elysium") }
                    }
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    modifier = Modifier.weight(1f),
                ) {
                    composable("splash") {
                        com.elysium.vanguard.features.splash.SplashScreen(
                            onNavigateToDashboard = {
                                navController.navigate("dashboard") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("dashboard") {
                        com.elysium.vanguard.features.dashboard.DashboardScreen(
                            onNavigateToStorage = { navController.navigate("file_manager") },
                            onNavigateToGallery = { navController.navigate("gallery") },
                            onNavigateToMusic = { navController.navigate("music_hub") }
                        )
                    }
                    composable("file_manager") {
                        com.elysium.vanguard.features.filemanager.FileManagerScreen(
                            viewModel = fileManagerViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("gallery") {
                        com.elysium.vanguard.features.gallery.GalleryScreen(
                            viewModel = galleryViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToAlbum = { albumName ->
                                val encodedName = URLEncoder.encode(albumName, StandardCharsets.UTF_8.toString())
                                navController.navigate("album_detail/$encodedName")
                            }
                        )
                    }
                    composable(
                        route = "player/{path}/{mimeType}",
                        arguments = listOf(
                            navArgument("path") { type = NavType.StringType },
                            navArgument("mimeType") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        val mimeType = URLDecoder.decode(backStackEntry.arguments?.getString("mimeType") ?: "", StandardCharsets.UTF_8.toString())
                        NativeMediaPlayer(
                            filePath = path,
                            mimeType = mimeType,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "viewer_image/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        HighFidelityImageViewer(
                            filePath = path,
                            onEdit = { navController.navigate("editor_image/${URLEncoder.encode(path, StandardCharsets.UTF_8.toString())}") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "editor_image/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        com.elysium.vanguard.features.viewer.BasicImageEditor(
                            filePath = path,
                            onSave = { navController.popBackStack() },
                            onCancel = { navController.popBackStack() }
                        )
                    }
                    composable("music_hub") {
                        com.elysium.vanguard.features.player.MusicHubScreen(
                            viewModel = musicHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "viewer_music/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        // We can transition to music_hub and auto-play the track
                        LaunchedEffect(path) {
                            val track = musicHubViewModel.songs.value.find { it.path == path }
                            if (track != null) {
                                musicHubViewModel.playTrack(track)
                            } else {
                                // Basic fallback for external files not yet in library
                                musicHubViewModel.playTrack(com.elysium.vanguard.features.player.MusicTrack(
                                    id = System.currentTimeMillis(),
                                    name = File(path).name,
                                    path = path,
                                    mimeType = "audio/*",
                                    album = "Unknown",
                                    artist = "Unknown",
                                    duration = 0,
                                    dateModified = 0
                                ))
                            }
                            navController.navigate("music_hub") {
                                popUpTo("viewer_music/{path}") { inclusive = true }
                            }
                        }
                    }
                    composable(
                        route = "viewer_pdf/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        com.elysium.vanguard.features.viewer.SovereignPdfViewer(
                            filePath = path,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "viewer_doc/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        IntegratedDocumentViewer(
                            filePath = path,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "viewer_archive/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        com.elysium.vanguard.features.viewer.ArchiveBrowserScreen(
                            filePath = path,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "viewer_hex/{path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", StandardCharsets.UTF_8.toString())
                        com.elysium.vanguard.features.viewer.HexViewerScreen(
                            filePath = path,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "album_detail/{albumName}",
                        arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val albumName = URLDecoder.decode(backStackEntry.arguments?.getString("albumName") ?: "", StandardCharsets.UTF_8.toString())
                        com.elysium.vanguard.features.gallery.AlbumDetailScreen(
                            albumName = albumName,
                            viewModel = galleryViewModel,
                            onBack = { navController.popBackStack() },
                            onMediaClick = { media ->
                                galleryViewModel.openMedia(media)
                            }
                        )
                    }
                }
                }

                LaunchedEffect(Unit) {
                    // Collect FileManager Events
                    launch {
                        fileManagerViewModel.events.collect { event ->
                            when (event) {
                                is FileManagerEvent.OpenFile -> {
                                    handleOpenFile(navController, event.file.path, event.file.mimeType)
                                }
                                is FileManagerEvent.ShareFile -> {
                                    FileOpenerUtil.shareFile(this@MainActivity, File(event.file.path))
                                }
                            }
                        }
                    }
                    // Collect Gallery Events
                    launch {
                        galleryViewModel.events.collect { event ->
                            when (event) {
                                is com.elysium.vanguard.features.gallery.GalleryEvent.OpenMedia -> {
                                    handleOpenFile(navController, event.media.path, event.media.mimeType)
                                }
                                is com.elysium.vanguard.features.gallery.GalleryEvent.ShareMedia -> {
                                    FileOpenerUtil.shareFile(this@MainActivity, File(event.media.path))
                                }
                                is com.elysium.vanguard.features.gallery.GalleryEvent.EditMedia -> {
                                    FileOpenerUtil.editFile(this@MainActivity, File(event.media.path))
                                }
                                is com.elysium.vanguard.features.gallery.GalleryEvent.SetWallpaper -> {
                                    FileOpenerUtil.setWallpaper(this@MainActivity, File(event.media.path))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleOpenFile(navController: androidx.navigation.NavController, path: String, mimeType: String) {
        val file = File(path)
        if (!file.exists()) return

        // RE-IDENTIFY MIME TYPE FOR ACCURACY (Fixes mislabeled WhatsApp documents)
        val detectedMime = if (mimeType == "application/octet-stream" || mimeType == "*/*" || mimeType == "resource/folder") {
             FileOpenerUtil.getMimeTypeFromMagicBytes(file)
        } else mimeType

        val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
        val encodedMime = URLEncoder.encode(detectedMime, StandardCharsets.UTF_8.toString())

        val fileName = path.substringAfterLast("/")

        when {
            detectedMime.startsWith("audio/") -> {
                navController.navigate("viewer_music/$encodedPath")
            }
            detectedMime.startsWith("video/") -> {
                navController.navigate("player/$encodedPath/$encodedMime")
            }
            detectedMime.startsWith("image/") -> {
                navController.navigate("viewer_image/$encodedPath")
            }
            detectedMime == "application/pdf" -> {
                navController.navigate("viewer_pdf/$encodedPath")
            }
            detectedMime.startsWith("text/") ||
            detectedMime == "application/vnd.oasis.opendocument.text" ||
            detectedMime == "text/csv" ||
            detectedMime == "application/rtf" ||
            detectedMime == "application/msword" ||
            detectedMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            detectedMime == "application/vnd.ms-excel" ||
            detectedMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            detectedMime == "application/vnd.ms-powerpoint" ||
            detectedMime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
            (detectedMime == "application/octet-stream" && fileName.startsWith("doc-", ignoreCase = true)) ||
            path.endsWith(".csv", ignoreCase = true) ||
            path.endsWith(".rtf", ignoreCase = true) ||
            path.endsWith(".html", ignoreCase = true) ||
            path.endsWith(".htm", ignoreCase = true) ||
            path.endsWith(".doc", ignoreCase = true) ||
            path.endsWith(".docx", ignoreCase = true) ||
            path.endsWith(".xls", ignoreCase = true) ||
            path.endsWith(".xlsx", ignoreCase = true) ||
            path.endsWith(".ppt", ignoreCase = true) ||
            path.endsWith(".pptx", ignoreCase = true) ||
            path.endsWith(".epub", ignoreCase = true) ||
            path.endsWith(".mobi", ignoreCase = true) ||
            path.endsWith(".txt", ignoreCase = true) -> {
                navController.navigate("viewer_doc/$encodedPath")
            }
            // Extension-based audio fallback
            path.endsWith(".mp3", true) || path.endsWith(".aac", true) ||
            path.endsWith(".m4p", true) || path.endsWith(".flac", true) ||
            path.endsWith(".wav", true) || path.endsWith(".ogg", true) -> {
                navController.navigate("viewer_music/$encodedPath")
            }
            else -> {
                // IN-APP FALLBACK: every remaining type opens natively in the file
                // manager instead of being handed to the system chooser.
                if (com.elysium.vanguard.core.archive.ArchiveEngine.isArchive(file)) {
                    navController.navigate("viewer_archive/$encodedPath")
                } else {
                    navController.navigate("viewer_hex/$encodedPath")
                }
            }
        }
    }

    private fun checkAndRequestStorageRoot() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            // Android 10 and below: Request standard permissions
            val permissions = mutableListOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val toRequest = permissions.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (toRequest.isNotEmpty()) {
                androidx.core.app.ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 1001)
            }
        }
    }
}
