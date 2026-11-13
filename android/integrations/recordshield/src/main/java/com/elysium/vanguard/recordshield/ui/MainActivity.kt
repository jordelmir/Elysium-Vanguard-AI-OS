package com.elysium.vanguard.recordshield.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elysium.vanguard.recordshield.R
import com.elysium.vanguard.recordshield.service.UploadWorker
import com.elysium.vanguard.recordshield.ui.auth.GoogleDriveAuth
import com.elysium.vanguard.recordshield.ui.screen.gallery.GalleryScreen
import com.elysium.vanguard.recordshield.ui.screen.home.HomeScreen
import com.elysium.vanguard.recordshield.ui.screen.home.MatrixRainBackground
import com.elysium.vanguard.recordshield.ui.screen.pin.PinScreen
import com.elysium.vanguard.recordshield.ui.screen.player.PlayerScreen
import com.elysium.vanguard.recordshield.ui.screen.setup.SetupScreen
import com.elysium.vanguard.recordshield.ui.screen.cloud.CloudSelectionScreen
import com.elysium.vanguard.recordshield.ui.screen.consent.ConsentScreen
import com.elysium.vanguard.recordshield.ui.theme.DeepBlack
import com.elysium.vanguard.recordshield.ui.theme.MatrixGreen
import com.elysium.vanguard.recordshield.ui.theme.RecordShieldTheme
import com.elysium.vanguard.recordshield.data.share.SharingManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ============================================================================
 * MainActivity — Sentinel Security Interface
 * ============================================================================
 *
 * Design: Neo-futuristic, high-contrast, security-focused.
 * Features: PIN lockdown, encrypted storage, and world-class animations.
 * ============================================================================
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var principalAtOpen: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        principalAtOpen = com.elysium.vanguard.recordshield.RecordShieldIdentity.principalId()
        if (principalAtOpen == null) {
            setContent {
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick = { finish() }) { Text("← Volver a Elysium") }
                    Text("Inicia sesión para abrir tus grabaciones privadas.")
                }
            }
            return
        }
        com.elysium.vanguard.recordshield.service.StealthNotificationManager.createStealthChannel(this)
        enableEdgeToEdge()
        UploadWorker.schedulePeriodicUpload(this)

        setContent {
            // The whole experience lives in RecordShieldSection, which the host
            // app can also embed as a native section. One implementation.
            RecordShieldSection(onBack = { finish() })
        }
    }

    override fun onResume() {
        super.onResume()
    }

}

/**
 * Full navigation graph with PIN gating and Global Security Lockdown.
 */
@Composable
fun RecordShieldApp(
    onGoogleDriveSetup: () -> Unit = {}
) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current

    val recordings by viewModel.recordings.collectAsState()
    val isPinVerified by viewModel.isPinVerified.collectAsState()
    val selectedChunks by viewModel.selectedRecordingChunks.collectAsState()

    // SharingManager instance
    val sharingManager = remember { SharingManager(context) }

    // Professional Configuration: API Integration (Ready for secure configuration)
    LaunchedEffect(Unit) {
        // viewModel.setVercelApiKey("KEY_HIDDEN")
    }

    // Global Security Observer: Lockdown on Backgrounding
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.resetPinVerification()
                val currentRoute = navController.currentDestination?.route
                if (currentRoute == "gallery" || currentRoute?.startsWith("player") == true || currentRoute == "pin_gate") {
                    navController.popBackStack("home", inclusive = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen {
                val destination = when {
                    !viewModel.isPinSet() -> "setup"
                    !viewModel.isConsentGiven() -> "consent"
                    else -> "home"
                }
                navController.navigate(destination) {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }

        composable("setup") {
            SetupScreen(
                onPinSet = { pin ->
                    viewModel.setPin(pin)
                    navController.navigate("consent") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }

        composable("consent") {
            ConsentScreen(
                onConsentGiven = {
                    viewModel.setConsentGiven()
                    navController.navigate("home") {
                        popUpTo("consent") { inclusive = true }
                    }
                },
                onDecline = {
                    (context as? ComponentActivity)?.finish()
                }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToGallery = {
                    if (viewModel.isPinSet()) {
                        viewModel.resetPinVerification()
                        navController.navigate("pin_gate")
                    } else {
                        navController.navigate("gallery")
                    }
                },
                onNavigateToCloudSettings = {
                    navController.navigate("cloud_settings")
                }
            )
        }

        composable("pin_gate") {
            PinScreen(
                title = "ACCESS VAULT",
                subtitle = "Enter PIN to view evidence",
                onPinVerified = {
                    navController.navigate("gallery") {
                        popUpTo("pin_gate") { inclusive = true }
                    }
                },
                onVerifyPin = { pin -> viewModel.verifyPin(pin) }
            )
        }

        composable("gallery") {
            GalleryScreen(
                recordings = recordings,
                onRecordingClick = { recording ->
                    viewModel.loadChunksForRecording(recording.id)
                    navController.navigate("player/${recording.id}")
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onDeleteRecording = { recording ->
                    viewModel.deleteRecording(recording)
                },
                onShareRecording = { recording ->
                    sharingManager.shareRecording(recording)
                }
            )
        }

        composable("player/{recordingId}") { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId") ?: return@composable
            PlayerScreen(
                recordingId = recordingId,
                chunks = selectedChunks,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("cloud_settings") {
            val cloudManager = hiltViewModel<MainViewModel>().getCloudStorageManager()
            CloudSelectionScreen(
                cloudManager = cloudManager,
                onBackClick = {
                    navController.popBackStack()
                },
                onGoogleDriveSetup = onGoogleDriveSetup,
                onSupabaseSetup = {
                    // TODO: Launch Supabase registration flow
                }
            )
        }
    }
}

/**
 * PREMIUM SENTINEL SPLASH SCREEN
 * Design: Highly animated scan-line grid with pulsing central branding.
 */
@Composable
fun SplashScreen(onAnimationFinish: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sentinel")

    // Cyber-Grid Scan Animation
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    // Breathing Pulse Effect
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Entry Animations
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        val alphaJob = launch { alphaAnim.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
        val scaleJob = launch {
            scaleAnim.animateTo(1.1f, tween(1200, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)))
            scaleAnim.animateTo(1.0f, tween(800, easing = FastOutSlowInEasing))
        }

        joinAll(alphaJob, scaleJob)
        delay(2000L) // Professional dwell time
        onAnimationFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010101)),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic breathing Matrix Rain background
        MatrixRainBackground()

        // 1. DYNAMIC CYBER GRID
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = MatrixGreen.copy(alpha = 0.05f)
            val step = 50.dp.toPx()

            // Grid Lines
            for (x in 0..size.width.toInt() step step.toInt()) {
                drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 0.5.dp.toPx())
            }
            for (y in 0..size.height.toInt() step step.toInt()) {
                drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 0.5.dp.toPx())
            }

            // Scanning Beam
            val scanY = scanProgress * size.height
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to MatrixGreen.copy(alpha = 0.12f),
                    1f to Color.Transparent
                ),
                topLeft = Offset(0f, scanY - 80.dp.toPx()),
                size = ComposeSize(size.width, 160.dp.toPx())
            )
            drawLine(
                color = MatrixGreen.copy(alpha = 0.4f),
                start = Offset(0f, scanY),
                end = Offset(size.width, scanY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 2. BRANDING STACK
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .graphicsLayer {
                            scaleX = pulseScale * 1.4f
                            scaleY = pulseScale * 1.4f
                            alpha = alphaAnim.value * 0.2f
                        }
                        .background(MatrixGreen, CircleShape)
                        .blur(100.dp)
                )

                // The Sentinel Icon
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            scaleX = scaleAnim.value * (1f + (pulseScale - 1f) * 0.3f)
                            scaleY = scaleAnim.value * (1f + (pulseScale - 1f) * 0.3f)
                            alpha = alphaAnim.value
                        }
                        .shadow(40.dp, CircleShape, spotColor = MatrixGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Sentinel Icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Primary Logo Text
            Text(
                text = "ELYSIUM VANGUARD",
                style = MaterialTheme.typography.headlineSmall,
                color = MatrixGreen,
                fontWeight = FontWeight.Black,
                letterSpacing = 12.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = alphaAnim.value
                    translationY = (1f - alphaAnim.value) * 30f
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Protocol Version Subtitle
            Text(
                text = "SECURE SENTINEL LOGIC v2.0",
                style = MaterialTheme.typography.labelSmall,
                color = MatrixGreen.copy(alpha = alphaAnim.value * 0.4f),
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = alphaAnim.value
                }
            )

            // Minimalist Progress Bar
            Spacer(modifier = Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MatrixGreen.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(alphaAnim.value)
                        .fillMaxHeight()
                        .background(MatrixGreen)
                )
            }
        }
    }
}
