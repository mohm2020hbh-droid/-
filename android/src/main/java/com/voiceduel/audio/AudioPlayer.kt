package com.voiceduel.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Plays a recording that arrived over the socket.
 *
 * [MediaPlayer] cannot read a byte array directly, so the bytes are spilled to
 * a cache file first and deleted as soon as playback finishes.
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    /**
     * Plays [audio] from the start.
     *
     * @param onCompletion invoked when playback finishes or fails, so the UI can
     *   always re-enable its controls.
     */
    fun play(audio: ByteArray, onCompletion: () -> Unit = {}): Boolean {
        stop()

        val file = File(context.cacheDir, "incoming_${System.currentTimeMillis()}.m4a")
        return try {
            file.writeBytes(audio)
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    cleanUp()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "playback error what=$what extra=$extra")
                    cleanUp()
                    onCompletion()
                    true
                }
                prepare()
                start()
            }
            player = mediaPlayer
            currentFile = file
            true
        } catch (e: IOException) {
            Log.e(TAG, "failed to play recording", e)
            file.delete()
            onCompletion()
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "player in a bad state", e)
            file.delete()
            onCompletion()
            false
        }
    }

    fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }
        player = null
        currentFile?.delete()
        currentFile = null
    }

    private fun cleanUp() {
        player?.let { runCatching { it.release() } }
        player = null
        currentFile?.delete()
        currentFile = null
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
