package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** Starts MediaProjection only with the one-time grant returned by Android's system dialog. */
internal class NexusScreenCapturer(private val context: Context) {
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var source: VideoSource? = null
    private var track: VideoTrack? = null

    fun start(
        factory: PeerConnectionFactory,
        eglContext: EglBase.Context,
        projectionGrant: Intent,
        onProjectionStopped: () -> Unit
    ): VideoTrack {
        check(capturer == null) { "Android screen capture is already running" }
        val screenCapturer = ScreenCapturerAndroid(projectionGrant, object : MediaProjection.Callback() {
            override fun onStop() = onProjectionStopped()
        })
        val videoSource = factory.createVideoSource(true)
        val helper = SurfaceTextureHelper.create("NexusGlobalScreenCapture", eglContext)
            ?: error("No se pudo preparar el capturador WebRTC de Android.")
        try {
            screenCapturer.initialize(helper, context, videoSource.capturerObserver)
            screenCapturer.startCapture(1920, 1080, 30)
            val videoTrack = factory.createVideoTrack("NEXUS_ANDROID_SCREEN", videoSource)
            capturer = screenCapturer
            textureHelper = helper
            source = videoSource
            track = videoTrack
            Log.i(TAG, "Android MediaProjection track started at 1920x1080@30 after system consent")
            return videoTrack
        } catch (error: Exception) {
            runCatching { screenCapturer.stopCapture() }
            screenCapturer.dispose()
            helper.dispose()
            videoSource.dispose()
            throw error
        }
    }

    fun stop() {
        try {
            capturer?.stopCapture()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            Log.w(TAG, "Error stopping Android MediaProjection capture", error)
        }
        capturer?.dispose()
        capturer = null
        track?.dispose()
        track = null
        source?.dispose()
        source = null
        textureHelper?.dispose()
        textureHelper = null
    }

    private companion object {
        const val TAG = "NexusGlobalCapture"
    }
}
