package com.elysium369.meet.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    private var startedAtMs: Long = 0L

    /**
     * Start recording audio and save to a temporary file.
     * Returns the File where the recording is stored, or null if starting failed.
     */
    fun startRecording(): File? {
        val outputDir = context.cacheDir
        val tempFile = try {
            File.createTempFile("meet_voice_note_", ".m4a", outputDir)
        } catch (e: IOException) {
            android.util.Log.e("VoiceRecorder", "Failed to create temp audio file", e)
            return null
        }

        currentOutputFile = tempFile
        startedAtMs = System.currentTimeMillis()

        // Initialize MediaRecorder according to API version
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(tempFile.absolutePath)
            
            try {
                prepare()
                start()
                android.util.Log.i("VoiceRecorder", "Audio recording started: ${tempFile.name}")
            } catch (e: Exception) {
                android.util.Log.e("VoiceRecorder", "Failed to start MediaRecorder", e)
                releaseRecorder()
                return null
            }
        }

        return tempFile
    }

    /**
     * Stop the current recording and return the recorded File.
     */
    fun stopRecording(): File? {
        val elapsed = System.currentTimeMillis() - startedAtMs
        if (elapsed < 500) {
            try {
                Thread.sleep(500 - elapsed)
            } catch (ignored: Exception) {}
        }
        var stoppedCleanly = false
        try {
            mediaRecorder?.let {
                it.stop()
                stoppedCleanly = true
                android.util.Log.i("VoiceRecorder", "Audio recording stopped: ${currentOutputFile?.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "Failed to stop MediaRecorder gracefully", e)
            stoppedCleanly = false
        } finally {
            releaseRecorder()
        }

        val file = currentOutputFile
        if (stoppedCleanly && file != null && file.exists() && file.length() > 200) {
            return file
        } else {
            // Descartar archivo inválido o corrupto
            file?.let { if (it.exists()) it.delete() }
            currentOutputFile = null
            return null
        }
    }

    /**
     * Cancel and delete the temporary recording file.
     */
    fun cancelRecording() {
        releaseRecorder()
        currentOutputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        currentOutputFile = null
    }

    private fun releaseRecorder() {
        mediaRecorder?.let {
            try {
                it.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
        mediaRecorder = null
    }
}
