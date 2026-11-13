package com.elysium.vanguard.recordshield.service

import android.app.*
import android.content.*
import android.os.*
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraMetadata
import com.elysium.vanguard.recordshield.R
import com.elysium.vanguard.recordshield.RecordShieldApplication
import com.elysium.vanguard.recordshield.RecordShieldIdentity
import com.elysium.vanguard.recordshield.domain.model.*
import com.elysium.vanguard.recordshield.domain.repository.ChunkRepository
import com.elysium.vanguard.recordshield.domain.repository.RecordingRepository
import com.elysium.vanguard.recordshield.data.cloud.VideoQualityPreset
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume
import androidx.lifecycle.LifecycleService
import androidx.annotation.CallSuper
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * ============================================================================
 * RecordingService — Anti-Sabotage Foreground Service
 * ============================================================================
 *
 * Why Foreground Service:
 *   Android kills background services after ~1 minute. A foreground service
 *   with FOREGROUND_SERVICE_TYPE_CAMERA | FOREGROUND_SERVICE_TYPE_MICROPHONE
 *   guarantees continuous recording even when:
 *   - Screen is off
 *   - Device is locked
 *   - User switches to another app
 *
 * Why WakeLock:
 *   Even foreground services can't prevent CPU deep sleep on some OEMs
 *   (Huawei, Xiaomi, Samsung). A PARTIAL_WAKE_LOCK keeps CPU active while
 *   allowing the screen to turn off — critical for evidence capture.
 *
 * Lifecycle:
 *   START_RECORDING → creates chunks every CHUNK_DURATION_MS
 *   STOP_RECORDING → finalizes last chunk, updates recording status
 *   Service destruction → marks recording as "interrupted"
 * ============================================================================
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var chunkRepository: ChunkRepository
    @Inject lateinit var cloudStorageManager: com.elysium.vanguard.recordshield.data.cloud.CloudStorageManager
    @Inject lateinit var secureStorage: SecureStorage

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Dedicated scope for uploads — survives serviceScope.cancel() so uploads complete
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUploads = mutableListOf<Job>()
    private val cameraReady = CompletableDeferred<Unit>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentRecording: com.elysium.vanguard.recordshield.domain.model.Recording? = null
    @Volatile private var recordingOwner: String? = null
    private var chunkIndex = 0
    private var timerJob: Job? = null
    private var chunkJob: Job? = null

    // Audio recording with MediaRecorder
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var isAudioMode = false
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var activeRecording: androidx.camera.video.Recording? = null
    private var isScreenOff = false
    private val currentType: RecordingType? get() = _currentRecordingType.value
    private var dualCameraManager: DualCameraManager? = null
    private var isDualCameraMode = false

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START_VIDEO = "com.elysium.vanguard.recordshield.ACTION_START_VIDEO"
        const val ACTION_START_AUDIO = "com.elysium.vanguard.recordshield.ACTION_START_AUDIO"
        const val ACTION_START_DUAL_VIDEO = "com.elysium.vanguard.recordshield.ACTION_START_DUAL_VIDEO"
        const val ACTION_STOP = "com.elysium.vanguard.recordshield.ACTION_STOP"
        const val ACTION_TOGGLE = "com.elysium.vanguard.recordshield.ACTION_TOGGLE"
        const val NOTIFICATION_ID = 1001
        const val CHUNK_DURATION_MS = 10_000L // 10 seconds per chunk

        // Singleton state for UI observation
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _currentRecordingId = MutableStateFlow<String?>(null)
        val currentRecordingId: StateFlow<String?> = _currentRecordingId.asStateFlow()

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        private val _currentRecordingType = MutableStateFlow<RecordingType?>(null)
        val currentRecordingType: StateFlow<RecordingType?> = _currentRecordingType.asStateFlow()

        /** True when the front and the rear camera are recording at the same time. */
        private val _isDualCamera = MutableStateFlow(false)
        val isDualCamera: StateFlow<Boolean> = _isDualCamera.asStateFlow()

        /** Honest status of the dual mode: why it failed, when it failed. */
        private val _dualCameraStatus = MutableStateFlow<String?>(null)
        val dualCameraStatus: StateFlow<String?> = _dualCameraStatus.asStateFlow()

        // Static provider for Live Preview (Reactive)
        private val _previewSurfaceProvider = MutableStateFlow<Preview.SurfaceProvider?>(null)
        var previewSurfaceProvider: Preview.SurfaceProvider?
            get() = _previewSurfaceProvider.value
            set(value) {
                _previewSurfaceProvider.value = value
            }

        fun startVideoRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_VIDEO
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startAudioRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_AUDIO
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startDualVideoRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_DUAL_VIDEO
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // Frame Sink for Zero-Interruption background recording
    // Why: If a Surface is provided but not consumed (no compositor), some SoCs (Exynos, Kirin, Tensor)
    // will detect the buffer pressure and pause the camera pipeline. We must "sink" the frames.
    private var backgroundFrameSink: android.media.ImageReader? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val frameSinkThread by lazy {
        android.os.HandlerThread("RecordShieldFrameSink").apply { start() }
    }
    private val frameSinkHandler by lazy {
        android.os.Handler(frameSinkThread.looper)
    }

    private val mockSurfaceProvider = Preview.SurfaceProvider { request ->
        val resolution = request.resolution

        // Cleanup old sink
        backgroundFrameSink?.close()

        // Create a new ImageReader to act as a frame consumer
        backgroundFrameSink = android.media.ImageReader.newInstance(
            resolution.width,
            resolution.height,
            android.graphics.ImageFormat.YUV_420_888,
            3
        ).apply {
            setOnImageAvailableListener({ reader ->
                try {
                    val image = reader?.acquireLatestImage()
                    image?.close() // Immediately consume and release
                } catch (e: Exception) {
                    Log.e(TAG, "Frame Sink failure", e)
                }
            }, frameSinkHandler)
        }

        request.provideSurface(backgroundFrameSink!!.surface, backgroundExecutor) {
            // No-op cleanup
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    updateCameraBinding()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    updateCameraBinding()
                }
            }
        }
    }

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        StealthNotificationManager.createStealthChannel(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        // Observe surface provider changes
        serviceScope.launch {
            _previewSurfaceProvider.collect {
                updateCameraBinding()
            }
        }
    }

    @CallSuper
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @Suppress("DEPRECATION")
    @CallSuper
    override fun onStart(intent: Intent?, startId: Int) {
        super.onStart(intent, startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_VIDEO -> startRecording(RecordingType.VIDEO)
            ACTION_START_AUDIO -> startRecording(RecordingType.AUDIO)
            ACTION_START_DUAL_VIDEO -> startRecording(RecordingType.VIDEO, dualCamera = true)
            ACTION_STOP -> stopRecordingInternal()
            ACTION_TOGGLE -> {
                if (_isRecording.value) {
                    stopRecordingInternal()
                } else {
                    startRecording(RecordingType.VIDEO)
                }
            }
        }
        return START_STICKY
    }

    private fun startRecording(type: RecordingType, dualCamera: Boolean = false) {
        if (_isRecording.value) {
            Log.w(TAG, "Recording already in progress, ignoring start request")
            return
        }

        recordingOwner = RecordShieldIdentity.principalId() ?: "local_sovereign_principal"

        isDualCameraMode = dualCamera
        _isDualCamera.value = dualCamera
        _dualCameraStatus.value = null
        Log.i(TAG, "Starting recording: type=$type, dualCamera=$dualCamera")

        // Start foreground immediately to avoid ANR on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Preparing recording..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Preparing recording..."))
        }

        // Acquire WakeLock
        acquireWakeLock()

        val recording = com.elysium.vanguard.recordshield.domain.model.Recording(
            id = UUID.randomUUID().toString(),
            deviceId = fetchDeviceId(),
            type = type
        )
        currentRecording = recording
        chunkIndex = 0
        isAudioMode = type == RecordingType.AUDIO
        _currentRecordingType.value = type

        serviceScope.launch {
            recordingRepository.createRecording(recording)
            _currentRecordingId.value = recording.id
            _isRecording.value = true
            _elapsedSeconds.value = 0

            // Start the timer
            startTimer()

            if (isDualCameraMode) {
                // Dual camera mode uses Camera2 directly
                startDualCameraChunking(recording)
            } else {
                // Single camera mode
                setupCamera()
                if (isAudioMode) {
                    startAudioChunking(recording)
                } else {
                    startVideoChunking(recording)
                }
            }
        }
    }

    private fun setupCamera() {
        if (cameraProvider != null) return

        // Read selected quality preset from SecureStorage
        val presetName = try {
            secureStorage.selectedVideoQuality
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read video quality, using default", e)
            VideoQualityPreset.DEFAULT.name
        }
        val preset = try {
            VideoQualityPreset.valueOf(presetName)
        } catch (e: Exception) {
            VideoQualityPreset.DEFAULT
        }
        Log.i(TAG, "Using video quality preset: ${preset.label} (${preset.resolution})")

        // For MediaRecorder-based presets (LOW/VLOW), skip CameraX setup
        if (preset.useMediaRecorder) {
            Log.i(TAG, "Preset ${preset.label} uses MediaRecorder, skipping CameraX setup")
            cameraReady.complete(Unit)
            return
        }

        serviceScope.launch(Dispatchers.Main) {
            cameraProvider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(this@RecordingService).get()
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(preset.cameraXQuality!!, FallbackStrategy.higherQualityOrLowerThan(preset.cameraXQuality)))
                .setExecutor(Executors.newSingleThreadExecutor())
                .build()

            videoCapture = VideoCapture.withOutput(recorder)
            Log.i(TAG, "Camera setup complete, videoCapture ready (${preset.label})")
            cameraReady.complete(Unit)
            updateCameraBinding()
        }
    }

    private fun updateCameraBinding() {
        val provider = cameraProvider ?: return
        if (!_isRecording.value && !isAudioMode) {
            // Service is active but not recording? Just clear if screen is off
            if (isScreenOff) provider.unbindAll()
            return
        }

        serviceScope.launch(Dispatchers.Main) {
            try {
                val uiSurfaceProvider = _previewSurfaceProvider.value
                val videoUseCase = if (isAudioMode) null else videoCapture

                // CRITICAL: Decide which surface provider to use.
                // We PREFER the UI surface, but fallback to Mock Surface if UI is gone, screen is off, or app is minimized.
                val activeSurfaceProvider = if (!isScreenOff && uiSurfaceProvider != null) {
                    uiSurfaceProvider
                } else {
                    Log.d(TAG, "Using MockSurfaceProvider for background recording persistence")
                    mockSurfaceProvider
                }

                // If we are already bound to the correct set, don't re-bind to avoid flicker or reset
                val currentLifecycleOwner = this@RecordingService

                // Re-prepare Preview with the chosen provider
                if (previewUseCase == null) {
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    previewUseCase = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                }
                // Dynamic surface switch — does not interrupt active recording
                previewUseCase?.setSurfaceProvider(activeSurfaceProvider)

                val useCasesToBind = mutableListOf<UseCase>()
                previewUseCase?.let { if (!provider.isBound(it)) useCasesToBind.add(it) }
                if (videoUseCase != null && !provider.isBound(videoUseCase)) {
                    useCasesToBind.add(videoUseCase)
                }

                if (useCasesToBind.isNotEmpty()) {
                    try {
                        val useCaseArray = useCasesToBind.toTypedArray()
                        provider.bindToLifecycle(
                            currentLifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            *useCaseArray
                        )
                        Log.d(TAG, "Camera Binding: ${if (isScreenOff) "BACKGROUND" else "UI"} mode bound")
                    } catch (e: Exception) {
                        Log.e(TAG, "Bind to lifecycle failed", e)
                        if (!_isRecording.value) {
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    currentLifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    *useCasesToBind.toTypedArray()
                                )
                            } catch (e2: Exception) {
                                Log.e(TAG, "Recovery bind failed", e2)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Camera use cases already bound, updated surface provider to ${if (isScreenOff || uiSurfaceProvider == null) "MOCK" else "UI"}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to update camera binding", e)
            }
        }
    }

    /**
     * Audio chunking: Records audio in CHUNK_DURATION_MS intervals.
     * Each chunk is a self-contained AAC file in internal storage.
     */
    private fun startAudioChunking(recording: com.elysium.vanguard.recordshield.domain.model.Recording) {
        chunkJob = serviceScope.launch {
            while (isActive && _isRecording.value) {
                val chunkFile = createChunkFile(recording.id, chunkIndex, "m4a")
                try {
                    recordAudioChunk(chunkFile)

                    // Professional Sync Pulse: Wait for OS to flush file
                    var finalSize = 0L
                    var retries = 0
                    while (retries < 5 && finalSize == 0L) {
                        delay(200)
                        finalSize = chunkFile.length()
                        retries++
                    }

                    if (chunkFile.exists() && finalSize > 0) {
                        val hash = computeSha256(chunkFile)
                        val chunk = EvidenceChunk(
                            recordingId = recording.id,
                            chunkIndex = chunkIndex,
                            localPath = chunkFile.absolutePath,
                            sizeBytes = finalSize,
                            durationMs = CHUNK_DURATION_MS.toInt(),
                            mimeType = "audio/mp4",
                            sha256Hash = hash
                        )
                        chunkRepository.insertChunk(chunk)
                        Log.i(TAG, "Audio chunk saved: ${finalSize} bytes, uploading directly...")

                        // Direct upload from service — no WorkManager delay
                        // Uses uploadScope (not serviceScope) so uploads survive service destroy
                        val uploadJob = uploadScope.launch {
                            uploadChunkToCloud(chunk)
                        }
                        synchronized(pendingUploads) { pendingUploads.add(uploadJob) }
                        uploadJob.invokeOnCompletion { synchronized(pendingUploads) { pendingUploads.remove(uploadJob) } }

                        chunkIndex++
                        updateNotification("Audio active")
                    } else {
                        Log.w(TAG, "Audio chunk empty after sync - discarding")
                        chunkFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Audio chunk $chunkIndex failed", e)
                }
            }
        }
    }

    /**
     * Dual camera chunking: Records both front and rear cameras simultaneously.
     * Each chunk creates a folder with rear.mp4 and front.mp4 inside.
     */
    private fun startDualCameraChunking(recording: com.elysium.vanguard.recordshield.domain.model.Recording) {
        val presetName = try {
            secureStorage.selectedVideoQuality
        } catch (e: Exception) {
            VideoQualityPreset.DEFAULT.name
        }
        val preset = try {
            VideoQualityPreset.valueOf(presetName)
        } catch (e: Exception) {
            VideoQualityPreset.DEFAULT
        }

        chunkJob = serviceScope.launch {
            Log.i(TAG, "Starting dual camera chunking with preset: ${preset.label}")

            while (isActive && _isRecording.value) {
                // Create chunk folder: recordings/{recordingId}/chunk_XXXXX/
                val chunkDir = File(filesDir, "recordings/${recording.id}/chunk_${String.format("%05d", chunkIndex)}")
                chunkDir.mkdirs()

                val rearFile = File(chunkDir, "rear.mp4")
                val frontFile = File(chunkDir, "front.mp4")

                try {
                    // Initialize DualCameraManager
                    dualCameraManager = DualCameraManager(this@RecordingService)
                    val readyDeferred = CompletableDeferred<Unit>()

                    dualCameraManager!!.startDualRecording(
                        preset = preset,
                        rearOutput = rearFile,
                        frontOutput = frontFile,
                        onReady = {
                            Log.i(TAG, "Dual cameras ready, recording started")
                            readyDeferred.complete(Unit)
                        },
                        onError = { e ->
                            Log.e(TAG, "Dual camera error", e)
                            readyDeferred.completeExceptionally(e)
                        }
                    )

                    // Wait for cameras to be ready
                    withTimeout(10_000L) { readyDeferred.await() }

                    // Record for CHUNK_DURATION_MS
                    delay(CHUNK_DURATION_MS)

                    // Stop recording
                    val stopDeferred = CompletableDeferred<Unit>()
                    dualCameraManager?.stopDualRecording {
                        stopDeferred.complete(Unit)
                    }
                    withTimeout(5_000L) { stopDeferred.await() }

                    // Sync pulse - wait for files to be written
                    var rearSize = 0L
                    var frontSize = 0L
                    var retries = 0
                    while (retries < 8 && (rearSize == 0L || frontSize == 0L)) {
                        delay(200)
                        rearSize = rearFile.length()
                        frontSize = frontFile.length()
                        retries++
                    }

                    if (rearFile.exists() && rearSize > 0 && frontFile.exists() && frontSize > 0) {
                        val rearHash = computeSha256(rearFile)
                        val frontHash = computeSha256(frontFile)

                        // Create chunks for both cameras
                        val rearChunk = EvidenceChunk(
                            recordingId = recording.id,
                            chunkIndex = chunkIndex,
                            localPath = rearFile.absolutePath,
                            sizeBytes = rearSize,
                            durationMs = CHUNK_DURATION_MS.toInt(),
                            mimeType = "video/mp4",
                            sha256Hash = rearHash
                        )

                        val frontChunk = EvidenceChunk(
                            recordingId = recording.id,
                            chunkIndex = chunkIndex,
                            localPath = frontFile.absolutePath,
                            sizeBytes = frontSize,
                            durationMs = CHUNK_DURATION_MS.toInt(),
                            mimeType = "video/mp4",
                            sha256Hash = frontHash
                        )

                        chunkRepository.insertChunk(rearChunk)
                        chunkRepository.insertChunk(frontChunk)
                        Log.i(TAG, "Dual chunks saved: rear=${rearSize}B, front=${frontSize}B, uploading...")

                        // Upload both chunks (grouped by chunk index in Drive)
                        val uploadJob = uploadScope.launch {
                            uploadDualChunksToCloud(rearChunk, frontChunk, chunkIndex)
                        }
                        synchronized(pendingUploads) { pendingUploads.add(uploadJob) }
                        uploadJob.invokeOnCompletion { synchronized(pendingUploads) { pendingUploads.remove(uploadJob) } }

                        chunkIndex++
                        updateNotification("Dual camera active")
                    } else {
                        Log.w(TAG, "Dual camera chunks empty after sync - discarding")
                        rearFile.delete()
                        frontFile.delete()
                        chunkDir.delete()
                    }

                    // Cleanup
                    dualCameraManager?.release()
                    dualCameraManager = null

                } catch (e: Exception) {
                    Log.e(TAG, "Dual camera chunk $chunkIndex failed", e)
                    // The client is told what happened instead of the recording
                    // silently producing nothing.
                    _dualCameraStatus.value = "FALLO DE CAMARA SIMULTANEA: ${e.message}"
                    dualCameraManager?.release()
                    dualCameraManager = null
                }
            }
        }
    }

    /**
     * Upload dual camera chunks to cloud, grouped in the same folder.
     */
    private suspend fun uploadDualChunksToCloud(
        rearChunk: EvidenceChunk,
        frontChunk: EvidenceChunk,
        chunkIdx: Int
    ) {
        com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== DUAL-UPLOAD-START chunk=$chunkIdx")
        Log.e(TAG, "=== DUAL-UPLOAD-START chunk=$chunkIdx")

        try {
            // Upload rear camera
            val rearFile = java.io.File(rearChunk.localPath)
            if (rearFile.exists()) {
                val rearData = rearFile.readBytes()
                val storagePath = cloudStorageManager.uploadFileToFolder(
                    recordingId = rearChunk.recordingId,
                    chunkIndex = chunkIdx,
                    fileName = "rear.mp4",
                    fileData = rearData,
                    mimeType = rearChunk.mimeType,
                    sha256Hash = rearChunk.sha256Hash
                )
                chunkRepository.updateChunkUploadStatus(
                    rearChunk.id,
                    com.elysium.vanguard.recordshield.domain.model.UploadStatus.UPLOADED,
                    storagePath
                )
                rearFile.delete()
                Log.i(TAG, "Rear chunk uploaded: $storagePath")
            }

            // Upload front camera
            val frontFile = java.io.File(frontChunk.localPath)
            if (frontFile.exists()) {
                val frontData = frontFile.readBytes()
                val storagePath = cloudStorageManager.uploadFileToFolder(
                    recordingId = frontChunk.recordingId,
                    chunkIndex = chunkIdx,
                    fileName = "front.mp4",
                    fileData = frontData,
                    mimeType = frontChunk.mimeType,
                    sha256Hash = frontChunk.sha256Hash
                )
                chunkRepository.updateChunkUploadStatus(
                    frontChunk.id,
                    com.elysium.vanguard.recordshield.domain.model.UploadStatus.UPLOADED,
                    storagePath
                )
                frontFile.delete()
                Log.i(TAG, "Front chunk uploaded: $storagePath")
            }

            // Clean up chunk folder
            val chunkDir = rearFile.parentFile
            chunkDir?.delete()

            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== DUAL-UPLOAD-DONE chunk=$chunkIdx")
            Log.e(TAG, "=== DUAL-UPLOAD-DONE chunk=$chunkIdx")

            com.elysium.vanguard.recordshield.service.UploadWorker.emitUploadSuccess("Dual camera chunk $chunkIdx uploaded")

        } catch (e: Exception) {
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== DUAL-UPLOAD-FAIL chunk=$chunkIdx ${e.message}")
            Log.e(TAG, "=== DUAL-UPLOAD-FAIL chunk=$chunkIdx ${e.message}", e)
            chunkRepository.updateChunkUploadStatus(
                rearChunk.id,
                com.elysium.vanguard.recordshield.domain.model.UploadStatus.FAILED
            )
            chunkRepository.updateChunkUploadStatus(
                frontChunk.id,
                com.elysium.vanguard.recordshield.domain.model.UploadStatus.FAILED
            )
        }
    }

    /**
     * Video chunking: Records video in CHUNK_DURATION_MS intervals.
     * Uses CameraX for FHD/HD/SD, MediaRecorder for LOW/VLOW.
     */
    private fun startVideoChunking(recording: com.elysium.vanguard.recordshield.domain.model.Recording) {
        val presetName = try {
            secureStorage.selectedVideoQuality
        } catch (e: Exception) {
            VideoQualityPreset.DEFAULT.name
        }
        val preset = try {
            VideoQualityPreset.valueOf(presetName)
        } catch (e: Exception) {
            VideoQualityPreset.DEFAULT
        }

        chunkJob = serviceScope.launch {
            // Wait for camera to be ready before recording chunks
            Log.i(TAG, "Waiting for camera to be ready...")
            try {
                withTimeout(10_000L) { cameraReady.await() }
                Log.i(TAG, "Camera ready, starting video chunk recording (${preset.label})")
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup timed out or failed", e)
                _isRecording.value = false
                return@launch
            }

            while (isActive && _isRecording.value) {
                val chunkFile = createChunkFile(recording.id, chunkIndex, "mp4")
                try {
                    if (preset.useMediaRecorder) {
                        recordVideoChunkMediaRecorder(chunkFile, preset)
                    } else {
                        recordVideoChunk(chunkFile)
                    }

                    // Professional Sync Pulse: Wait for OS to flush video file
                    var finalSize = 0L
                    var retries = 0
                    while (retries < 8 && finalSize == 0L) {
                        delay(200)
                        finalSize = chunkFile.length()
                        retries++
                    }

                    if (chunkFile.exists() && finalSize > 0) {
                        val hash = computeSha256(chunkFile)
                        val chunk = EvidenceChunk(
                            recordingId = recording.id,
                            chunkIndex = chunkIndex,
                            localPath = chunkFile.absolutePath,
                            sizeBytes = finalSize,
                            durationMs = CHUNK_DURATION_MS.toInt(),
                            mimeType = "video/mp4",
                            sha256Hash = hash
                        )
                        chunkRepository.insertChunk(chunk)
                        Log.i(TAG, "Video chunk saved: ${finalSize} bytes (${preset.label}), uploading directly...")

                        // Direct upload from service
                        val uploadJob = uploadScope.launch {
                            uploadChunkToCloud(chunk)
                        }
                        synchronized(pendingUploads) { pendingUploads.add(uploadJob) }
                        uploadJob.invokeOnCompletion { synchronized(pendingUploads) { pendingUploads.remove(uploadJob) } }

                        chunkIndex++
                        updateNotification("Video active (${preset.label})")
                    } else {
                        Log.w(TAG, "Video chunk empty after sync - discarding")
                        chunkFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Video chunk $chunkIndex failed", e)
                }
            }
        }
    }

    /**
     * Records a single audio chunk using MediaRecorder.
     * Suspends for CHUNK_DURATION_MS then stops recording.
     */
    private suspend fun recordAudioChunk(outputFile: File) {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.media.MediaRecorder(this@RecordingService)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(CHUNK_DURATION_MS.toInt())
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder failed to start", e)
            return
        }

        var timePassed = 0L
        while (timePassed < CHUNK_DURATION_MS && _isRecording.value && currentCoroutineContext().isActive) {
            delay(100)
            timePassed += 100
        }

        try { mediaRecorder?.stop() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }

    /**
     * Records a single video chunk using CameraX VideoCapture.
     * This method uses the CameraX recording API with a file output.
     */
    private suspend fun recordVideoChunk(outputFile: File) {
        val recorder = videoCapture?.output ?: throw IllegalStateException("Camera not setup")

        val finalizeDeferred = CompletableDeferred<Unit>()
        val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()
        var recordingBuilder = recorder.prepareRecording(this@RecordingService, fileOutputOptions)

        if (ActivityCompat.checkSelfPermission(this@RecordingService, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recordingBuilder = recordingBuilder.withAudioEnabled()
        }

        val activeRec = recordingBuilder.start(ContextCompat.getMainExecutor(this@RecordingService)) { event: VideoRecordEvent ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    Log.e(TAG, "Video chunk error: ${event.error}")
                }
                finalizeDeferred.complete(Unit)
            }
        }
        activeRecording = activeRec

        var timePassed = 0L
        while (timePassed < CHUNK_DURATION_MS && _isRecording.value && currentCoroutineContext().isActive) {
            delay(100)
            timePassed += 100
        }

        activeRec.stop()
        activeRecording = null
        finalizeDeferred.await()
    }

    /**
     * Records a single video chunk using MediaRecorder directly.
     * Used for LOW/VLOW presets where CameraX doesn't support custom resolutions.
     * Uses Camera2 API to get a surface for MediaRecorder.
     */
    private suspend fun recordVideoChunkMediaRecorder(outputFile: File, preset: VideoQualityPreset) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.media.MediaRecorder(this@RecordingService)
        } else {
            @Suppress("DEPRECATION")
            android.media.MediaRecorder()
        }.apply {
            setVideoSource(android.media.MediaRecorder.VideoSource.SURFACE)
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setVideoSize(preset.width, preset.height)
            setVideoEncodingBitRate(preset.videoBitrate)
            setVideoFrameRate(30)
            setMaxDuration(CHUNK_DURATION_MS.toInt())
            setOutputFile(outputFile.absolutePath)
            prepare()
        }

        // Use Camera2 to create a surface for MediaRecorder
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: throw IllegalStateException("No camera found")
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val previewSize = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888).firstOrNull()
            ?: android.util.Size(preset.width, preset.height)

        val surface = recorder.surface ?: throw IllegalStateException("MediaRecorder surface is null")

        val cameraDevice = CompletableDeferred<android.hardware.camera2.CameraDevice>()
        cameraManager.openCamera(cameraId, object : android.hardware.camera2.CameraDevice.StateCallback() {
            override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                cameraDevice.complete(camera)
            }
            override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
                cameraDevice.completeExceptionally(IllegalStateException("Camera disconnected"))
            }
            override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) {
                cameraDevice.completeExceptionally(IllegalStateException("Camera error: $error"))
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))

        try {
            val camera = cameraDevice.await()
            val captureRequest = camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)
                set(android.hardware.camera2.CaptureRequest.CONTROL_MODE, android.hardware.camera2.CameraMetadata.CONTROL_MODE_AUTO)
            }

            recorder.start()

            camera.createCaptureSession(
                listOf(surface),
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        Log.e(TAG, "Camera capture session config failed")
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )

            // Record for CHUNK_DURATION_MS
            var timePassed = 0L
            while (timePassed < CHUNK_DURATION_MS && _isRecording.value && currentCoroutineContext().isActive) {
                delay(100)
                timePassed += 100
            }

            try { recorder.stop() } catch (_: Exception) {}
            camera.close()
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder video chunk failed", e)
            try { recorder.release() } catch (_: Exception) {}
            throw e
        }

        try { recorder.release() } catch (_: Exception) {}
    }

    private fun stopRecordingInternal() {
        if (!_isRecording.value) return

        _isRecording.value = false
        _currentRecordingType.value = null
        isDualCameraMode = false
        timerJob?.cancel()

        // We do NOT cancel chunkJob immediately.
        // We set _isRecording.value to false, which signals the delay loops to finish, stops the recorder,
        // and safely waits for VideoRecordEvent.Finalize to complete.
        val recordingToFinish = currentRecording
        _currentRecordingId.value = null
        currentRecording = null

        uploadScope.launch {
            try {
                chunkJob?.join()
            } catch (e: Exception) {
                Log.w(TAG, "chunkJob join exception", e)
            }

            // Cleanup dual camera if active
            if (dualCameraManager != null) {
                try {
                    dualCameraManager?.release()
                    dualCameraManager = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing dual camera manager", e)
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    cameraProvider?.unbindAll()
                    Log.i(TAG, "Camera unbound successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unbinding camera", e)
                }
                cameraProvider = null
                videoCapture = null
                previewSurfaceProvider = null // Crucial: clear preview to avoid black screen on next run
            }

            if (recordingToFinish != null) {
                try {
                    recordingRepository.updateRecordingStatus(
                        recordingToFinish.id,
                        RecordingStatus.COMPLETED.value,
                        System.currentTimeMillis()
                    )
                    Log.i(TAG, "Recording ${recordingToFinish.id} marked COMPLETED in Room DB")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mark recording as COMPLETED", e)
                }
            }

            releaseWakeLock()

            // Cleanup Background Frame Sink
            backgroundFrameSink?.close()
            backgroundFrameSink = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @CallSuper
    override fun onDestroy() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        super.onDestroy()
        // If destroyed without proper stop, ensure recording is saved as completed or interrupted
        if (_isRecording.value) {
            _isRecording.value = false
            _currentRecordingType.value = null
            val orphan = currentRecording
            if (orphan != null) {
                uploadScope.launch {
                    try {
                        recordingRepository.updateRecordingStatus(
                            orphan.id,
                            RecordingStatus.COMPLETED.value,
                            System.currentTimeMillis()
                        )
                    } catch (_: Exception) {}
                }
            }
        }
        timerJob?.cancel()
        chunkJob?.cancel()

        // Stop CameraX
        try { activeRecording?.stop() } catch (_: Exception) {}
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}

        backgroundFrameSink?.close()
        backgroundFrameSink = null
        backgroundExecutor.shutdown()
        runCatching { frameSinkThread.quitSafely() }

        releaseWakeLock()

        // Cancel service scope (stops recording loops)
        serviceScope.cancel()

        // DO NOT cancel uploadScope here — let pending uploads complete naturally.
        // The process stays alive long enough (foreground service + WakeLock held
        // until releaseWakeLock above completes) for the HTTP calls to finish.
        // uploadScope uses SupervisorJob() so individual failures won't propagate.
        val pendingCount = synchronized(pendingUploads) { pendingUploads.size }
        if (pendingCount > 0) {
            Log.i(TAG, "$pendingCount upload(s) still in progress — will complete in background")
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private fun createChunkFile(recordingId: String, index: Int, ext: String): File {
        val dir = File(filesDir, "recordings/$recordingId")
        dir.mkdirs()
        return File(dir, "chunk_${String.format("%05d", index)}.$ext")
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetchDeviceId(): String {
        // Use Android Settings.Secure.ANDROID_ID as device fingerprint base
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                _elapsedSeconds.value++
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RecordShield::RecordingWakeLock"
        ).apply {
            // Why 4 hours: Maximum reasonable recording session.
            // Prevents battery drain from orphaned wake locks.
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun buildNotification(text: String): Notification {
        return StealthNotificationManager.buildStealthNotification(this, text)
    }

    private fun updateNotification(text: String) {
        StealthNotificationManager.updateStealthNotification(this, NOTIFICATION_ID, text)
    }

    /**
     * Direct upload from service — bypasses WorkManager entirely.
     * The service is already a foreground service with a WakeLock,
     * so it has all the resources needed for upload.
     */
    private suspend fun uploadChunkToCloud(chunk: EvidenceChunk) {
        com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== UPLOAD-START chunk=${chunk.chunkIndex} path=${chunk.localPath} mime=${chunk.mimeType} size=${chunk.sizeBytes}")
        Log.e(TAG, "=== UPLOAD-START chunk=${chunk.chunkIndex} path=${chunk.localPath} mime=${chunk.mimeType} size=${chunk.sizeBytes}")
        try {
            val chunkFile = java.io.File(chunk.localPath)
            if (!chunkFile.exists()) {
                com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== FILE MISSING: ${chunk.localPath}")
                Log.e(TAG, "=== UPLOAD-FAIL: file does NOT exist: ${chunk.localPath}")
                return
            }
            val chunkData = chunkFile.readBytes()
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== FILE-READ: ${chunkData.size} bytes")
            Log.e(TAG, "=== UPLOAD-READ: ${chunkData.size} bytes from ${chunk.localPath}")

            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== CALLING cloudStorageManager.uploadChunk...")
            Log.e(TAG, "=== UPLOAD-CALL: cloudStorageManager.uploadChunk...")
            val storagePath = cloudStorageManager.uploadChunk(
                recordingId = chunk.recordingId,
                chunkIndex = chunk.chunkIndex,
                chunkData = chunkData,
                mimeType = chunk.mimeType,
                sha256Hash = chunk.sha256Hash
            )
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== UPLOAD-OK storagePath=$storagePath")
            Log.e(TAG, "=== UPLOAD-OK: chunk=${chunk.chunkIndex} storagePath=$storagePath")

            chunkRepository.updateChunkUploadStatus(
                chunk.id,
                com.elysium.vanguard.recordshield.domain.model.UploadStatus.UPLOADED,
                storagePath
            )

            java.io.File(chunk.localPath).delete()
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== UPLOAD-DONE chunk=${chunk.chunkIndex}")
            Log.e(TAG, "=== UPLOAD-DONE: chunk=${chunk.chunkIndex} deleted local file")
            com.elysium.vanguard.recordshield.service.UploadWorker.emitUploadSuccess("Chunk ${chunk.chunkIndex} uploaded to Drive")

        } catch (e: com.elysium.vanguard.recordshield.data.cloud.CloudUploadException) {
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== UPLOAD-FAIL-Cloud: chunk=${chunk.chunkIndex} ${e.message}")
            Log.e(TAG, "=== UPLOAD-ERROR-Cloud: chunk=${chunk.chunkIndex} ${e.message}")
            chunkRepository.updateChunkUploadStatus(
                chunk.id,
                com.elysium.vanguard.recordshield.domain.model.UploadStatus.FAILED
            )
        } catch (e: Exception) {
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== UPLOAD-FAIL: chunk=${chunk.chunkIndex} ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "=== UPLOAD-ERROR: chunk=${chunk.chunkIndex} ${e.javaClass.simpleName}: ${e.message}", e)
            chunkRepository.updateChunkUploadStatus(
                chunk.id,
                com.elysium.vanguard.recordshield.domain.model.UploadStatus.FAILED
            )
        }
        com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== UPLOAD-END chunk=${chunk.chunkIndex}")
        Log.e(TAG, "=== UPLOAD-END: chunk=${chunk.chunkIndex}")
    }
}
