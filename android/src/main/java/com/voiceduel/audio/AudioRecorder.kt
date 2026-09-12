package com.voiceduel.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Records one short take to a file in the app cache and hands back the bytes.
 *
 * Recordings are AAC in an MP4 container — small enough to send in one frame
 * (C4) and playable by [AudioPlayer] without any decoding work.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    /** Starts recording. Returns false if the microphone could not be opened. */
    fun start(): Boolean {
        if (isRecording) return true

        val file = File(context.cacheDir, "take_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = createRecorder()

        return try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(ENCODING_BIT_RATE)
                setAudioSamplingRate(SAMPLING_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            true
        } catch (e: IOException) {
            Log.e(TAG, "failed to start recording", e)
            releaseQuietly(mediaRecorder)
            file.delete()
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "recorder in a bad state", e)
            releaseQuietly(mediaRecorder)
            file.delete()
            false
        } catch (e: RuntimeException) {
            // MediaRecorder.start() throws a bare RuntimeException when the mic
            // is busy or the permission was revoked mid-session.
            Log.e(TAG, "microphone unavailable", e)
            releaseQuietly(mediaRecorder)
            file.delete()
            false
        }
    }

    /**
     * Stops recording and returns the encoded bytes, or `null` if the take was
     * unusable (stopped too early, or the mic failed).
     */
    fun stop(): ByteArray? {
        val mediaRecorder = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null

        try {
            mediaRecorder.stop()
        } catch (e: RuntimeException) {
            // Thrown when stop() lands before any frame was written.
            Log.w(TAG, "recording stopped with no data", e)
            file?.delete()
            releaseQuietly(mediaRecorder)
            return null
        }
        releaseQuietly(mediaRecorder)

        val bytes = file?.takeIf { it.exists() }?.readBytes()
        file?.delete()
        return bytes?.takeIf { it.isNotEmpty() }
    }

    /** Abandons the current take without producing any bytes. */
    fun cancel() {
        val mediaRecorder = recorder ?: return
        recorder = null
        runCatching { mediaRecorder.stop() }
        releaseQuietly(mediaRecorder)
        outputFile?.delete()
        outputFile = null
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun releaseQuietly(mediaRecorder: MediaRecorder) {
        runCatching { mediaRecorder.reset() }
        runCatching { mediaRecorder.release() }
    }

    companion object {
        private const val TAG = "AudioRecorder"
        private const val ENCODING_BIT_RATE = 64_000
        private const val SAMPLING_RATE = 44_100
    }
}
