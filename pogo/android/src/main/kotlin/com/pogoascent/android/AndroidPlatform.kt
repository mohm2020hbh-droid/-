package com.pogoascent.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pogoascent.audio.AudioBackend
import com.pogoascent.haptics.HapticBackend
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio output. One-shots go through [SoundPool] (mixing, pitch via playback rate, pan via left/right volume); the clips are
 * synthesised PCM written once to the cache directory as WAV. Loops (music, ambience) use a static [AudioTrack] with loop
 * points so they repeat gaplessly. Nothing here depends on bundled audio files.
 */
class AndroidAudioBackend(private val context: Context) : AudioBackend {
  private val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_GAME)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
  private val pool: SoundPool = SoundPool.Builder().setMaxStreams(14).setAudioAttributes(attrs).build()
  private val soundIds = HashMap<String, Int>()
  private val loaded = HashSet<Int>()
  private val clips = HashMap<String, ShortArray>()
  private val loops = HashMap<Int, AudioTrack>()
  private val streamToHandle = HashMap<Int, Int>()
  private var nextHandle = 1
  private val handleStream = HashMap<Int, Int>()
  private val dir = File(context.cacheDir, "pogo-audio-v1").apply { mkdirs() }

  init {
    pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded.add(sampleId) } }
  }

  @Synchronized
  override fun register(clipId: String, pcm: ShortArray, sampleRate: Int) {
    clips[clipId] = pcm
    if (pcm.size > 400_000) return // long loops are played with AudioTrack, not SoundPool (1 MB sample limit)
    val f = File(dir, "$clipId.wav")
    if (!f.exists() || f.length() != (44 + pcm.size * 2).toLong()) writeWav(f, pcm, sampleRate)
    soundIds[clipId] = pool.load(f.absolutePath, 1)
  }

  @Synchronized
  override fun play(clipId: String, volume: Float, rate: Float, pan: Float, loop: Boolean): Int {
    if (loop || (clips[clipId]?.size ?: 0) > 400_000) return playLoop(clipId, volume)
    val sid = soundIds[clipId] ?: return -1
    if (!synchronized(loaded) { loaded.contains(sid) }) return -1 // still decoding; skip this one-shot
    val left = (volume * (1f - pan.coerceAtLeast(0f) * 0.7f)).coerceIn(0f, 1f)
    val right = (volume * (1f + pan.coerceAtMost(0f) * 0.7f)).coerceIn(0f, 1f)
    val stream = pool.play(sid, left, right, 1, 0, rate.coerceIn(0.5f, 2f))
    if (stream == 0) return -1
    val h = nextHandle++
    handleStream[h] = stream
    streamToHandle[stream] = h
    return h
  }

  private fun playLoop(clipId: String, volume: Float): Int {
    val pcm = clips[clipId] ?: return -1
    return try {
      val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(22050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(pcm.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
      track.write(pcm, 0, pcm.size)
      track.setLoopPoints(0, pcm.size, -1)
      track.setVolume(volume.coerceIn(0f, 1f))
      track.play()
      val h = nextHandle++
      loops[h] = track
      h
    } catch (e: Exception) { -1 }
  }

  @Synchronized override fun setVolume(handle: Int, volume: Float) {
    loops[handle]?.setVolume(volume.coerceIn(0f, 1f))
    handleStream[handle]?.let { pool.setVolume(it, volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f)) }
  }

  @Synchronized override fun stop(handle: Int) {
    loops.remove(handle)?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
    handleStream.remove(handle)?.let { pool.stop(it); streamToHandle.remove(it) }
  }

  @Synchronized override fun stopAll() {
    for (t in loops.values) try { t.stop(); t.release() } catch (_: Exception) {}
    loops.clear()
    for (s in handleStream.values) pool.stop(s)
    handleStream.clear(); streamToHandle.clear()
  }

  @Synchronized override fun release() { stopAll(); pool.release() }

  private fun writeWav(f: File, pcm: ShortArray, sampleRate: Int) {
    val data = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    data.put("RIFF".toByteArray()); data.putInt(36 + pcm.size * 2); data.put("WAVE".toByteArray())
    data.put("fmt ".toByteArray()); data.putInt(16); data.putShort(1); data.putShort(1)
    data.putInt(sampleRate); data.putInt(sampleRate * 2); data.putShort(2); data.putShort(16)
    data.put("data".toByteArray()); data.putInt(pcm.size * 2)
    for (s in pcm) data.putShort(s)
    FileOutputStream(f).use { it.write(data.array()) }
  }
}

/** Vibration through the system [Vibrator] (VibratorManager on Android 12+, amplitude control on 8+). */
class AndroidHapticBackend(context: Context) : HapticBackend {
  private val vibrator: Vibrator? = try {
    if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
  } catch (e: Exception) { null }

  override val supported: Boolean get() = vibrator?.hasVibrator() == true

  override fun vibrate(durationMs: Int, amplitude: Int) {
    val v = vibrator ?: return
    try {
      if (Build.VERSION.SDK_INT >= 26) {
        val amp = if (v.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(durationMs.toLong().coerceAtLeast(1), amp))
      } else {
        @Suppress("DEPRECATION") v.vibrate(durationMs.toLong().coerceAtLeast(1))
      }
    } catch (_: Exception) { /* some OEM builds throw when the app is in the background */ }
  }

  override fun cancel() { try { vibrator?.cancel() } catch (_: Exception) {} }
}
