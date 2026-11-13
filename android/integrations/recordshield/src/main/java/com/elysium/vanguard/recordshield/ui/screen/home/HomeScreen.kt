package com.elysium.vanguard.recordshield.ui.screen.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.camera.view.PreviewView
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium.vanguard.recordshield.service.RecordingService
import com.elysium.vanguard.recordshield.service.UploadWorker
import com.elysium.vanguard.recordshield.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium.vanguard.recordshield.domain.model.RecordingType

/**
 * ============================================================================
 * HomeScreen — The Command Center
 * ============================================================================
 *
 * Design: Neo-futuristic dark interface with:
 *   - Subtle Matrix Rain background animation
 *   - Massive pulsing recording button with animated glow
 *   - Glassmorphism status cards
 *   - "Breathing" interface that feels alive
 * ============================================================================
 */
@Composable
fun HomeScreen(
    onNavigateToGallery: () -> Unit,
    onNavigateToCloudSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRecording by RecordingService.isRecording.collectAsState()
    val elapsedSeconds by RecordingService.elapsedSeconds.collectAsState()
    val currentType by RecordingService.currentRecordingType.collectAsState()
    val isDualCamera by RecordingService.isDualCamera.collectAsState()
    val dualCameraStatus by RecordingService.dualCameraStatus.collectAsState()
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp > 600
    val screenWidth = configuration.screenWidthDp.dp
    val scrollState = rememberScrollState()

    // Design Tokens - Scaled for Foldables
    val mainPadding = if (isLargeScreen) 32.dp else 12.dp
    val buttonSize = if (isLargeScreen) 220.dp else 150.dp
    val previewWidthFraction = if (isLargeScreen) 0.6f else 0.5f // Drastically reduced for narrow screens
    val gridColumns = if (isLargeScreen) 4 else 2

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    // Upload success animation state
    var uploadSuccessMessage by remember { mutableStateOf<String?>(null) }
    val uploadSuccessAlpha = remember { Animatable(0f) }
    val uploadSuccessSlideY = remember { Animatable(-100f) }
    val uploadCoroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        UploadWorker.uploadSuccessEvents.collect { message ->
            uploadSuccessMessage = message
            uploadCoroutineScope.launch {
                uploadSuccessSlideY.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                uploadSuccessAlpha.animateTo(1f, tween(300))
            }
            delay(2500)
            uploadCoroutineScope.launch {
                uploadSuccessAlpha.animateTo(0f, tween(400))
                uploadSuccessSlideY.animateTo(-100f, tween(400, easing = FastOutSlowInEasing))
            }
            uploadSuccessMessage = null
        }
    }

    // Strict OS-level back button interception
    BackHandler(enabled = isRecording) {
        // Prevent backing out of the app when actively recording for evidence integrity
        Toast.makeText(context, "Cannot exit while recording evidence.", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // Layer 1: Matrix Rain Background (subtle, opacated)
        MatrixRainBackground()

        // Layer 2: Upload Success Banner
        if (uploadSuccessMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .graphicsLayer {
                        alpha = uploadSuccessAlpha.value
                        translationY = uploadSuccessSlideY.value
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MatrixGreen.copy(alpha = 0.15f))
                    .border(1.dp, MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MatrixGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = uploadSuccessMessage ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(mainPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            TopBar(
                onNavigateToGallery = onNavigateToGallery,
                onNavigateToCloudSettings = onNavigateToCloudSettings
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Battery Optimization Warning Card
            if (!isIgnoringBatteryOptimizations) {
                BatteryOptimizationCard(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                })
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recording Timer (visible during recording)
            if (isRecording) {
                RecordingTimer(elapsedSeconds = elapsedSeconds, isLargeScreen = isLargeScreen)
                Spacer(modifier = Modifier.height(32.dp))
            }

            // LIVELY STATUS TEXT
            Text(
                text = if (isRecording) "RECORDING IN PROGRESS" else "READY TO RECORD",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                color = if (isRecording) RecordingRed else MatrixGreen
            )

            Spacer(modifier = Modifier.height(16.dp))

            RecordButton(
                isRecording = isRecording,
                buttonSize = buttonSize,
                onToggle = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isRecording) {
                        RecordingService.stopRecording(context)
                    } else {
                        RecordingService.startVideoRecording(context)
                    }
                }
            )

            if (!isRecording) {
                Spacer(modifier = Modifier.height(16.dp))
                ModeSelector(
                    isLargeScreen = isLargeScreen,
                    onVideoSelected = { RecordingService.startVideoRecording(context) },
                    onAudioSelected = { RecordingService.startAudioRecording(context) },
                    onDualVideoSelected = { RecordingService.startDualVideoRecording(context) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // LIVE EVIDENCE PREVIEW (Controlled Height to prevent overlap)
            LivePreview(
                isRecording = isRecording,
                isDualCamera = isDualCamera,
                dualCameraStatus = dualCameraStatus,
                recordingType = currentType,
                modifier = Modifier
                    .fillMaxWidth(previewWidthFraction)
                    .height(if (isLargeScreen) 300.dp else 160.dp) // Fixed height is safer than aspectRatio here
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Responsive Status Grid
            val statusItems = remember {
                listOf(
                    StatusItem("ENCRYPTION", "AES-256", Icons.Default.Shield, MatrixGreen),
                    StatusItem("LOCATION", "PROTECTED", Icons.Default.GpsFixed, MatrixGreen),
                    StatusItem("STORAGE", "ENCRYPTED", Icons.Default.Folder, MatrixGreen),
                    StatusItem("SIGNAL", "SECURE", Icons.Default.Wifi, MatrixGreen)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                items(statusItems) { item ->
                    StatusCardUi(item, isLargeScreen)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LivePreview(
    isRecording: Boolean,
    recordingType: RecordingType?,
    isDualCamera: Boolean = false,
    dualCameraStatus: String? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                previewView?.let {
                    RecordingService.previewSurfaceProvider = it.surfaceProvider
                }
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                // When UI goes to background, detach surface so Service falls back to MockSurfaceProvider
                RecordingService.previewSurfaceProvider = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            RecordingService.previewSurfaceProvider = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurface)
            .border(1.dp, if (isRecording) RecordingRed.copy(alpha = 0.5f) else MatrixGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // ALWAYS keep AndroidView in the hierarchy so the surface provider survives transitions
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                    // Initial setting will be handled by the Lifecycle Observer if already RESUMED
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isRecording) {
            // DUAL CAMERA: the front and the rear are being recorded at the
            // same time, each into its own file. There is no single preview
            // surface in this mode, so the state is stated plainly.
            if (isDualCamera) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MatrixGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "GRABACION SIMULTANEA",
                            color = MatrixGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "CAMARA DELANTERA + TRASERA",
                            color = MatrixGreen.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        if (dualCameraStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                dualCameraStatus,
                                color = RecordingRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (recordingType == RecordingType.AUDIO) {
                // Dim the camera if it's Audio mode to make it clear we are only recording audio
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = RecordingRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "AUDIO RECORDING ACTIVE",
                            color = RecordingRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Top-left LIVE badge
            val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(RecordingRed.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                        .clip(CircleShape)
                        .background(Color.White)
                    )
                    Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Standby Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MatrixGreen.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "PREVIEW STANDBY",
                        color = MatrixGreen.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

data class StatusItem(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@Composable
fun StatusCardUi(item: StatusItem, isLargeScreen: Boolean) {
    val iconSize = if (isLargeScreen) 18.dp else 14.dp
    val labelSize = if (isLargeScreen) 11.sp else 9.sp
    val valueSize = if (isLargeScreen) 13.sp else 11.sp

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GlassSurface)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(if (isLargeScreen) 12.dp else 10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(iconSize))
                Text(item.label, color = item.color, fontSize = labelSize, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.value, color = TextSecondary, fontSize = valueSize, fontWeight = FontWeight.Light)
        }
    }
}

// ============================================================================
// MASSIVE RECORDING BUTTON with Animated Glow
// ============================================================================

@Composable
fun RecordButton(
    isRecording: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit
) {
    val ringSize = buttonSize * 0.85f
    val innerSize = buttonSize * 0.7f
    val iconSize = if (buttonSize > 180.dp) 56.dp else 40.dp
    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )

    val buttonColor = if (isRecording) RecordingRed else MatrixGreen
    val glowColor = if (isRecording) RecordingRedGlow else MatrixGreenGlow

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(buttonSize)
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(buttonSize)
                .drawBehind {
                    // Core internal glow
                    drawCircle(
                        color = buttonColor.copy(alpha = glowAlpha),
                        radius = size.minDimension / 2 + glowRadius
                    )
                    // Structural aura
                    drawCircle(
                        color = buttonColor.copy(alpha = glowAlpha * 0.4f),
                        radius = size.minDimension / 2 + glowRadius * 2f
                    )
                    // High-energy particles aura
                    drawCircle(
                        color = buttonColor.copy(alpha = glowAlpha * 0.15f),
                        radius = size.minDimension / 2 + glowRadius * 3.5f
                    )
                }
        )

        // Glass border ring
        Box(
            modifier = Modifier
                .size(ringSize)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            buttonColor.copy(alpha = 0.6f),
                            buttonColor.copy(alpha = 0.1f),
                            buttonColor.copy(alpha = 0.4f)
                        )
                    ),
                    shape = CircleShape
                )
                .background(GlassSurface, CircleShape)
        )

        // Inner button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(innerSize)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = buttonColor,
                    spotColor = buttonColor
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            buttonColor,
                            buttonColor.copy(alpha = 0.7f)
                        )
                    )
                )
                .clickable(onClick = onToggle)
        ) {
            if (isRecording) {
                // Stop icon (square)
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Recording",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                // Shield icon
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Start Recording",
                    tint = DeepBlack,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

// ============================================================================
// MATRIX RAIN BACKGROUND (Subtle, opacated)
// ============================================================================

@Composable
fun MatrixRainBackground() {
    val matrixChars = remember { "01ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ" }
    val columnCount = 20

    // Core neon palette for a more vibrant, cyberpunk/hacker feel
    val neonColors = remember {
        listOf(
            Color(0xFF00FF00), // Matrix Green
            Color(0xFF00FFFF), // Cyan
            Color(0xFFFF00FF), // Magenta
            Color(0xFFFFFF00)  // Yellow
        )
    }

    // Breathing pulsing background effect
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val bgGlow by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg_breathe"
    )

    // Drop state: pair of (current Y position, speed), and a randomly assigned color index
    val drops = remember {
        List(columnCount) {
            Triple(
                mutableStateOf(Random.nextFloat() * -50f), // Y
                1f + Random.nextFloat() * 3f,             // Speed
                Random.nextInt(neonColors.size)           // Color Index
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30)
            drops.forEach { drop ->
                val (yState, speed, _) = drop
                val currentY = yState.value
                yState.value = if (currentY > 110f) -10f else currentY + speed
            }
        }
    }

    // Add a pulsing background gradient for the "breathing" effect
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MatrixGreen.copy(alpha = bgGlow),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.maxDimension
                    )
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val charSize = size.width / columnCount
            val paint = android.graphics.Paint().apply {
                textSize = charSize * 0.8f
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            drops.forEachIndexed { index, dropState ->
                val x = index * charSize + charSize / 2
                val (yState, _, colorIndex) = dropState
                val currentYPercent = yState.value
                val currentY = (currentYPercent / 100f) * size.height

                val dropColor = neonColors[colorIndex]

                // Draw a trail of 15 characters
                for (i in 0 until 18) {
                    val trailY = currentY - (i * charSize * 1.2f)
                    if (trailY < 0 || trailY > size.height) continue

                    val alpha = (1f - i / 18f).coerceIn(0f, 1f)

                    // Head is brighter white
                    if (i == 0) {
                        paint.color = Color.White.toArgb()
                        paint.setShadowLayer(20f, 0f, 0f, dropColor.toArgb())
                    } else {
                        // Body takes the drop's assigned neon color
                        paint.color = dropColor.copy(alpha = alpha * 0.8f).toArgb()
                        // Keep a subtle shadow for the body for extra glow
                        paint.setShadowLayer(8f, 0f, 0f, dropColor.copy(alpha = alpha * 0.5f).toArgb())
                    }

                    // Randomly change characters occasionally for "life"
                    val char = matrixChars[Random.nextInt(matrixChars.length)].toString()
                    drawContext.canvas.nativeCanvas.drawText(char, x, trailY, paint)
                }
            }
        }
    }
}

// ============================================================================
// TOP BAR
// ============================================================================

@Composable
fun TopBar(onNavigateToGallery: () -> Unit, onNavigateToCloudSettings: () -> Unit = {}) {
    val context = LocalContext.current
    var showQualityMenu by remember { mutableStateOf(false) }
    val secureStorage = remember {
        com.elysium.vanguard.recordshield.data.local.SecureStorage(context)
    }
    var currentQuality by remember { mutableStateOf(secureStorage.selectedVideoQuality) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "RECORD SHIELD",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 4.sp,
                modifier = Modifier.drawBehind {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = MatrixGreen.toArgb()
                            maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawText(
                            "RECORD SHIELD",
                            0f,
                            0f,
                            paint
                        )
                    }
                }
            )
            Text(
                text = "ELYSIUM VANGUARD",
                style = MaterialTheme.typography.labelSmall,
                color = MatrixGreen,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Video quality selector
            Box {
                IconButton(
                    onClick = { showQualityMenu = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = "Video Quality",
                        tint = ElectricBlue
                    )
                }
                DropdownMenu(
                    expanded = showQualityMenu,
                    onDismissRequest = { showQualityMenu = false }
                ) {
                    com.elysium.vanguard.recordshield.data.cloud.VideoQualityPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (currentQuality == preset.name) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentQuality == preset.name) MatrixGreen else TextPrimary
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            },
                            onClick = {
                                secureStorage.selectedVideoQuality = preset.name
                                currentQuality = preset.name
                                showQualityMenu = false
                            },
                            leadingIcon = if (currentQuality == preset.name) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = MatrixGreen) }
                            } else null
                        )
                    }
                }
            }

            // Cloud settings button
            IconButton(
                onClick = onNavigateToCloudSettings,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Cloud Settings",
                    tint = ElectricBlue
                )
            }

            // Gallery button
            IconButton(
                onClick = onNavigateToGallery,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Gallery",
                    tint = ElectricBlue
                )
            }
        }
    }
}

// ============================================================================
// RECORDING TIMER
// ============================================================================

@Composable
fun RecordingTimer(elapsedSeconds: Long, isLargeScreen: Boolean) {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60

    // Blinking dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Blinking red dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(RecordingRed.copy(alpha = dotAlpha))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
            fontSize = if (isLargeScreen) 56.sp else 36.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = if (isLargeScreen) 4.sp else 2.sp
        )
    }
}

// ============================================================================
// MODE SELECTOR (Video/Audio)
// ============================================================================

@Composable
fun ModeSelector(
    isLargeScreen: Boolean,
    onVideoSelected: () -> Unit,
    onAudioSelected: () -> Unit,
    onDualVideoSelected: () -> Unit = {}
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (isLargeScreen) 16.dp else 8.dp)
    ) {
        // Video mode
        GlassButton(
            icon = Icons.Default.Videocam,
            label = "VIDEO",
            accentColor = ElectricBlue,
            isLargeScreen = isLargeScreen,
            onClick = onVideoSelected
        )

        // Audio mode
        GlassButton(
            icon = Icons.Default.Mic,
            label = "AUDIO",
            accentColor = DeepPurple,
            isLargeScreen = isLargeScreen,
            onClick = onAudioSelected
        )

        // Dual Camera mode
        GlassButton(
            icon = Icons.Default.CameraAlt,
            label = "DUAL",
            accentColor = MatrixGreen,
            isLargeScreen = isLargeScreen,
            onClick = onDualVideoSelected
        )
    }
}

@Composable
fun GlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    isLargeScreen: Boolean,
    onClick: () -> Unit
) {
    val hPadding = if (isLargeScreen) 32.dp else 24.dp
    val vPadding = if (isLargeScreen) 16.dp else 12.dp
    val iconSize = if (isLargeScreen) 28.dp else 22.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = hPadding, vertical = vPadding)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            letterSpacing = 2.sp
        )
    }
}

// ============================================================================
// STATUS CARD (Glassmorphism)
// ============================================================================

@Composable
fun StatusCard(isRecording: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurface)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(MatrixGreenSubtle, MatrixGreen, MatrixGreenSubtle)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusItem(
                label = "STATUS",
                value = if (isRecording) "ACTIVE" else "STANDBY",
                color = if (isRecording) RecordingRed else MatrixGreen
            )
            StatusItem(
                label = "SECURITY",
                value = "ARMED",
                color = MatrixGreen
            )
            StatusItem(
                label = "CLOUD",
                value = "SYNCED",
                color = ElectricBlue
            )
        }
    }
}

@Composable
fun StatusItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ============================================================================
// BATTERY OPTIMIZATION CARD
// ============================================================================
@Composable
fun BatteryOptimizationCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = RecordingRed.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RecordingRed)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = RecordingRed
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "BATTERY OPTIMIZATION ACTIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = RecordingRed
                )
                Text(
                    text = "Tap to disable. OS may kill recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
