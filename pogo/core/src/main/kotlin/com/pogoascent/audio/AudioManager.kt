package com.pogoascent.audio

import com.pogoascent.settings.AudioSettings
import java.util.Random

/** Platform sound output. The Android implementation wraps AudioTrack; tests use a recording fake. */
interface AudioBackend {
  /** Make [pcm] (16-bit mono, [sampleRate] Hz) available under [clipId]. */
  fun register(clipId: String, pcm: ShortArray, sampleRate: Int)

  /** Start a voice; returns a handle ≥ 0, or -1 when the backend could not play it. */
  fun play(clipId: String, volume: Float, rate: Float, pan: Float, loop: Boolean): Int
  fun setVolume(handle: Int, volume: Float)
  fun stop(handle: Int)
  fun stopAll()
  fun release()
}

/**
 * Pure-logic audio mixer: event lookup, cooldowns, random variation, spatialisation, priority-based voice stealing and the
 * Master/Music/SFX/Ambient volume tree. All timing uses an injected clock so it is deterministic under test.
 */
class AudioManager(
  private val catalog: AudioCatalog,
  private val backend: AudioBackend,
  private val clock: () -> Double,
  private val random: Random = Random(42),
  private val maxVoices: Int = 10,
) {
  private class Voice(val handle: Int, val eventId: String, val priority: Int, val endsAt: Double, val category: AudioCategory, val baseVolume: Float)

  var settings = AudioSettings()
    set(value) {
      field = value.sanitized()
      applyVolumes()
    }

  /** Listener position (usually the camera target) for spatial events. */
  var listenerX = 0.0
  var listenerY = 0.0
  var paused = false
    set(value) {
      field = value
      if (value) backend.stopAll().also { voices.clear(); musicHandle = -1; ambientHandle = -1 }
    }

  private val voices = ArrayList<Voice>()
  private val lastPlayed = HashMap<String, Double>()
  private val clipLengthSec = HashMap<String, Double>()
  private var musicHandle = -1
  private var ambientHandle = -1
  private var musicBase = 0f
  private var ambientBase = 0f
  var droppedByCooldown = 0; private set
  var droppedByPriority = 0; private set
  var played = 0; private set

  init {
    for ((id, recipe) in catalog.recipes) {
      val pcm = SoundSynth.render(recipe)
      backend.register(id, pcm, SoundSynth.SAMPLE_RATE)
      clipLengthSec[id] = pcm.size.toDouble() / SoundSynth.SAMPLE_RATE
    }
  }

  fun categoryVolume(c: AudioCategory): Double = when (c) {
    AudioCategory.MUSIC -> settings.music
    AudioCategory.AMBIENT, AudioCategory.ENVIRONMENT -> settings.ambient
    else -> settings.sfx
  } * settings.master

  /**
   * Play an event. [intensity] (0..1+) scales volume (e.g. impact speed); [x]/[y] are world coordinates used when the event
   * is spatial. Returns true when a voice was started.
   */
  fun trigger(eventId: String, intensity: Double = 1.0, x: Double = listenerX, y: Double = listenerY): Boolean {
    if (paused) return false
    val ev = catalog.events[eventId] ?: return false
    val now = clock()
    val last = lastPlayed[eventId]
    if (last != null && now - last < ev.cooldown) { droppedByCooldown++; return false }

    // expire finished voices
    voices.removeAll { it.endsAt <= now }
    if (voices.size >= maxVoices) {
      val weakest = voices.minByOrNull { it.priority * 1000.0 + (it.endsAt - now) * -1.0 } // lowest priority, then closest to ending
      if (weakest == null || weakest.priority >= ev.priority) { droppedByPriority++; return false }
      backend.stop(weakest.handle)
      voices.remove(weakest)
    }

    var pan = 0f
    var atten = 1.0
    if (ev.spatial) {
      val dx = x - listenerX
      val dy = y - listenerY
      pan = (dx / PAN_RANGE).coerceIn(-1.0, 1.0).toFloat()
      val dist2 = dx * dx + dy * dy
      atten = 1.0 / (1.0 + dist2 / (REF_DISTANCE * REF_DISTANCE))
    }
    val vVar = if (ev.volumeVariation > 0) 1.0 + (random.nextDouble() * 2 - 1) * ev.volumeVariation else 1.0
    val pVar = if (ev.pitchVariation > 0) 1.0 + (random.nextDouble() * 2 - 1) * ev.pitchVariation else 1.0
    val base = (ev.volume * intensity.coerceIn(0.0, 1.5) * vVar * atten).toFloat()
    val vol = (base * categoryVolume(ev.category)).toFloat().coerceIn(0f, 1f)
    val rate = (ev.pitch * pVar).toFloat().coerceIn(0.25f, 4f)
    val handle = backend.play(ev.recipe, vol, rate, pan, ev.loop)
    if (handle < 0) return false
    lastPlayed[eventId] = now
    val length = (clipLengthSec[ev.recipe] ?: 0.5) / rate
    voices += Voice(handle, eventId, ev.priority, if (ev.loop) Double.MAX_VALUE else now + length, ev.category, base)
    played++
    return true
  }

  /** Start (or switch) the looping music bed. [pcm] comes from [MusicGenerator]. */
  fun playMusic(clipId: String, pcm: ShortArray) {
    stopMusic()
    backend.register(clipId, pcm, SoundSynth.SAMPLE_RATE)
    musicBase = 0.8f
    musicHandle = if (paused) -1 else backend.play(clipId, (musicBase * categoryVolume(AudioCategory.MUSIC)).toFloat(), 1f, 0f, true)
  }

  fun playAmbient(clipId: String, pcm: ShortArray) {
    stopAmbient()
    backend.register(clipId, pcm, SoundSynth.SAMPLE_RATE)
    ambientBase = 0.7f
    ambientHandle = if (paused) -1 else backend.play(clipId, (ambientBase * categoryVolume(AudioCategory.AMBIENT)).toFloat(), 1f, 0f, true)
  }

  fun stopMusic() { if (musicHandle >= 0) backend.stop(musicHandle); musicHandle = -1 }
  fun stopAmbient() { if (ambientHandle >= 0) backend.stop(ambientHandle); ambientHandle = -1 }

  private fun applyVolumes() {
    if (musicHandle >= 0) backend.setVolume(musicHandle, (musicBase * categoryVolume(AudioCategory.MUSIC)).toFloat().coerceIn(0f, 1f))
    if (ambientHandle >= 0) backend.setVolume(ambientHandle, (ambientBase * categoryVolume(AudioCategory.AMBIENT)).toFloat().coerceIn(0f, 1f))
    for (v in voices) if (v.endsAt == Double.MAX_VALUE) backend.setVolume(v.handle, (v.baseVolume * categoryVolume(v.category)).toFloat().coerceIn(0f, 1f))
  }

  val activeVoices: Int get() = voices.count { it.endsAt > clock() }

  fun release() { backend.stopAll(); backend.release(); voices.clear() }

  companion object {
    const val PAN_RANGE = 12.0
    const val REF_DISTANCE = 18.0
  }

}
