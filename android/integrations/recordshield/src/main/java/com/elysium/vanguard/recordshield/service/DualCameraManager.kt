package com.elysium.vanguard.recordshield.service

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.elysium.vanguard.recordshield.data.cloud.VideoQualityPreset
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ============================================================================
 * DualCameraManager — Simultaneous Front + Rear Camera Recording
 * ============================================================================
 *
 * Why Camera2 over CameraX:
 *   CameraX can only bind one camera per lifecycle owner. For simultaneous
 *   dual recording, we need Camera2 to open both camera devices independently,
 *   each with its own capture session, surface, and MediaRecorder.
 *
 * Architecture:
 *   - CameraManager opens front + rear cameras
 *   - Each camera gets its own CaptureSession with a Surface from MediaRecorder
 *   - Each camera records to a separate MP4 file
 *   - Both recordings start/stop synchronously
 *
 * HARDWARE CONTRACT (Android):
 *   1. The microphone is an EXCLUSIVE resource. Two MediaRecorders cannot both
 *      capture audio: the second one fails. So the REAR recorder carries the
 *      audio and the FRONT recorder is video only.
 *   2. Opening two cameras at the same time is only allowed for the camera
 *      pairs the device declares as concurrent. That is asked to the platform
 *      with [CameraManager.getConcurrentCameraIds]; when the front + rear pair
 *      is not supported the caller is told instead of failing silently.
 *
 * Thread Safety:
 *   - Camera2 operations require a dedicated HandlerThread
 *   - MediaRecorder must be created/started/stopped on the correct thread
 *   - Semaphore ensures exclusive access to camera open/close
 * ============================================================================
 */
class DualCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "DualCameraManager"

        /** How long to wait for both cameras to be ready. */
        private const val OPEN_TIMEOUT_MS = 5_000L
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var rearCamera: CameraDevice? = null
    private var frontCamera: CameraDevice? = null
    private var rearSession: CameraCaptureSession? = null
    private var frontSession: CameraCaptureSession? = null
    private var rearRecorder: MediaRecorder? = null
    private var frontRecorder: MediaRecorder? = null
    private var rearSurface: Surface? = null
    private var frontSurface: Surface? = null

    private val cameraThread = HandlerThread("DualCameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val openLock = Semaphore(1, true)
    private var isRecording = false

    /**
     * What the platform says about this device: can the front and the rear
     * cameras really be open at the same time.
     */
    data class ConcurrentSupport(
        val supported: Boolean,
        val rearCameraId: String?,
        val frontCameraId: String?,
        /** Human readable explanation, shown to the client when unsupported. */
        val reason: String
    )

    /**
     * Asks the platform for the concurrent camera support. Never guesses.
     */
    fun queryConcurrentSupport(): ConcurrentSupport {
        return try {
            val backId = getCameraId(CameraCharacteristics.LENS_FACING_BACK)
            val frontId = getCameraId(CameraCharacteristics.LENS_FACING_FRONT)
            if (backId == null || frontId == null) {
                return ConcurrentSupport(
                    false, null, null,
                    "Este dispositivo no expone camara frontal y trasera"
                )
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return ConcurrentSupport(
                    false, backId, frontId,
                    "La camara concurrente requiere Android 11 o superior"
                )
            }
            val pairs = cameraManager.concurrentCameraIds
            if (pairs.isNullOrEmpty()) {
                return ConcurrentSupport(
                    false, backId, frontId,
                    "El dispositivo no declara pares de camara concurrente"
                )
            }
            val match = pairs.firstOrNull { set ->
                set.contains(backId) && set.contains(frontId)
            }
            if (match == null) {
                ConcurrentSupport(
                    false, backId, frontId,
                    "Este dispositivo no admite camara frontal y trasera simultaneas"
                )
            } else {
                ConcurrentSupport(true, backId, frontId, "OK")
            }
        } catch (e: Exception) {
            ConcurrentSupport(false, null, null, "No se pudo consultar el soporte de camara: ${e.message}")
        }
    }

    /**
     * Callback for preview frame updates.
     */
    interface PreviewCallback {
        fun onRearPreview(surface: Surface)
        fun onFrontPreview(surface: Surface)
    }

    /**
     * Open both cameras and start recording.
     *
     * @param preset Quality preset for recording
     * @param rearOutput File for rear camera output (carries the audio)
     * @param frontOutput File for front camera output (video only)
     * @param onReady Called when both cameras are ready
     * @param onError Called on error with exception
     */
    @SuppressLint("MissingPermission")
    fun startDualRecording(
        preset: VideoQualityPreset,
        rearOutput: File,
        frontOutput: File,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isRecording) {
            onError(IllegalStateException("Already recording"))
            return
        }

        // HARDWARE CONTRACT: the device must declare the front + rear pair as
        // concurrent. Otherwise we say so instead of failing obscurely.
        val support = queryConcurrentSupport()
        if (!support.supported) {
            Log.e(TAG, "Concurrent camera not supported: ${support.reason}")
            onError(IllegalStateException(support.reason))
            return
        }
        val rearCameraId = support.rearCameraId!!
        val frontCameraId = support.frontCameraId!!

        try {
            // The microphone can only be taken once: the REAR recorder gets the
            // audio, the FRONT recorder is video only.
            rearRecorder = createMediaRecorder(preset, rearOutput, withAudio = true)
            frontRecorder = createMediaRecorder(preset, frontOutput, withAudio = false)

            rearSurface = rearRecorder!!.surface
            frontSurface = frontRecorder!!.surface

            if (rearSurface == null || frontSurface == null) {
                onError(IllegalStateException("Failed to get surfaces from MediaRecorder"))
                return
            }

            isRecording = true

            val opened = AtomicInteger(0)
            val onCameraOpened = {
                if (opened.incrementAndGet() == 2) {
                    // Both cameras opened, create sessions and start recording
                    createCaptureSessions(preset, onReady, onError)
                }
            }

            openCamera(rearCameraId, { device ->
                rearCamera = device
                onCameraOpened()
            }, onError)

            openCamera(frontCameraId, { device ->
                frontCamera = device
                onCameraOpened()
            }, onError)

        } catch (e: Exception) {
            isRecording = false
            cleanup()
            onError(e)
        }
    }

    /**
     * Stop both recordings.
     */
    fun stopDualRecording(onComplete: () -> Unit) {
        if (!isRecording) {
            onComplete()
            return
        }

        isRecording = false

        try {
            // Stop sessions
            rearSession?.stopRepeating()
            frontSession?.stopRepeating()

            // Stop recorders
            rearRecorder?.stop()
            frontRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            rearRecorder?.release()
            frontRecorder?.release()
            rearRecorder = null
            frontRecorder = null

            rearCamera?.close()
            frontCamera?.close()
            rearCamera = null
            frontCamera = null

            rearSession?.close()
            frontSession?.close()
            rearSession = null
            frontSession = null

            rearSurface?.release()
            frontSurface = null
            rearSurface?.release()
            frontSurface = null

            onComplete()
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        stopDualRecording { }
        cameraThread.quitSafely()
    }

    // ========================================================================
    // INTERNAL METHODS
    // ========================================================================

    /**
     * @param withAudio false for the front camera: the microphone is an
     * exclusive resource and only one recorder can own it.
     */
    private fun createMediaRecorder(
        preset: VideoQualityPreset,
        outputFile: File,
        withAudio: Boolean
    ): MediaRecorder {
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (withAudio) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            setVideoSize(preset.width, preset.height)
            setVideoEncodingBitRate(preset.videoBitrate)
            setVideoFrameRate(30)
            setMaxDuration(10_000) // 10 seconds per chunk
            setOutputFile(outputFile.absolutePath)
            prepare()
        }

        return recorder
    }

    private fun getCameraId(facing: Int): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == facing
            }
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(
        cameraId: String,
        onOpened: (CameraDevice) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Exclusive access to the camera open sequence.
        val acquired = openLock.tryAcquire(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!acquired) {
            onError(IllegalStateException("Timeout opening camera $cameraId"))
            return
        }
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    openLock.release()
                    onOpened(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    openLock.release()
                    onError(IllegalStateException("Camera $cameraId disconnected"))
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    openLock.release()
                    onError(IllegalStateException("Camera $cameraId error: $error"))
                    camera.close()
                }
            }, cameraHandler)
        } catch (e: Exception) {
            openLock.release()
            onError(e)
        }
    }

    private fun createCaptureSessions(
        preset: VideoQualityPreset,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val sessionsCreated = AtomicInteger(0)
        val onSessionCreated = {
            if (sessionsCreated.incrementAndGet() == 2) {
                // Both sessions created, start recording
                startRecording()
                onReady()
            }
        }

        // Rear camera session
        rearCamera?.let { camera ->
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                rearSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            camera.createCaptureSession(
                listOf(rearSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        rearSession = session
                        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
                        onSessionCreated()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(IllegalStateException("Rear camera session config failed"))
                    }
                },
                cameraHandler
            )
        }

        // Front camera session
        frontCamera?.let { camera ->
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                frontSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            camera.createCaptureSession(
                listOf(frontSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        frontSession = session
                        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
                        onSessionCreated()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(IllegalStateException("Front camera session config failed"))
                    }
                },
                cameraHandler
            )
        }
    }

    private fun startRecording() {
        try {
            rearRecorder?.start()
            frontRecorder?.start()
            Log.i(TAG, "Dual recording started (rear = video + audio, front = video only)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            throw e
        }
    }

    private fun cleanup() {
        rearRecorder?.release()
        frontRecorder?.release()
        rearRecorder = null
        frontRecorder = null

        rearCamera?.close()
        frontCamera?.close()
        rearCamera = null
        frontCamera = null

        rearSession?.close()
        frontSession?.close()
        rearSession = null
        frontSession = null

        rearSurface?.release()
        frontSurface?.release()
        rearSurface = null
        frontSurface = null
    }
}
