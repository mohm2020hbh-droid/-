package com.pogoascent.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** A procedural music style: tempo, scale, chord progression and instrument waves. All values are original creative choices. */
class MusicStyle(
  val id: String,
  val bpm: Double,
  val rootMidi: Int,
  /** Scale degrees as semitone offsets from the root. */
  val scale: IntArray,
  /** Chord roots as scale-degree indexes, one per bar. */
  val progression: IntArray,
  val leadWave: String,
  val padWave: String,
  val bassWave: String,
  val leadOctave: Int,
  val leadDensity: Double,
  val gain: Double,
)

object MusicStyles {
  val all: List<MusicStyle> = listOf(
    MusicStyle("pastoral_major", 96.0, 60, intArrayOf(0, 2, 4, 5, 7, 9, 11), intArrayOf(0, 4, 5, 3, 0, 4, 3, 4), "triangle", "sine", "sine", 1, 0.6, 0.55),
    MusicStyle("crystal_minor", 84.0, 62, intArrayOf(0, 2, 3, 5, 7, 8, 10), intArrayOf(0, 5, 2, 6, 0, 5, 6, 4), "sine", "triangle", "sine", 2, 0.5, 0.5),
    MusicStyle("synth_pulse", 118.0, 57, intArrayOf(0, 3, 5, 7, 10), intArrayOf(0, 0, 4, 3, 0, 0, 4, 2), "saw", "square", "square", 1, 0.8, 0.4),
    MusicStyle("storm_drone", 72.0, 52, intArrayOf(0, 1, 4, 5, 7, 8, 11), intArrayOf(0, 0, 1, 0, 0, 6, 1, 0), "saw", "saw", "triangle", 0, 0.35, 0.4),
  )
  fun get(id: String): MusicStyle = all.firstOrNull { it.id == id } ?: all.first()
}

/** Renders a seamless 8-bar loop of a [MusicStyle] into 16-bit mono PCM. Deterministic for a given style. */
object MusicGenerator {
  fun render(style: MusicStyle, bars: Int = 8): ShortArray {
    val sr = SoundSynth.SAMPLE_RATE
    val beat = 60.0 / style.bpm
    val barLen = beat * 4
    val total = (barLen * bars * sr).toInt()
    val mix = FloatArray(total)
    val rnd = Random(style.id.hashCode().toLong())
    fun midiHz(m: Double) = 440.0 * 2.0.pow((m - 69.0) / 12.0)
    fun degreeMidi(deg: Int, octaveShift: Int): Double {
      val n = style.scale.size
      val oct = Math.floorDiv(deg, n)
      return style.rootMidi + style.scale[Math.floorMod(deg, n)] + 12.0 * (oct + octaveShift)
    }

    for (bar in 0 until bars) {
      val chordDeg = style.progression[bar % style.progression.size]
      val t0 = bar * barLen
      // bass: root on beats 1 and 3
      for (b in intArrayOf(0, 2)) note(mix, sr, t0 + b * beat, beat * 1.8, midiHz(degreeMidi(chordDeg, -1)), style.bassWave, 0.22 * style.gain, 0.5 * beat)
      // pad: triad held for the bar
      for (k in intArrayOf(0, 2, 4)) note(mix, sr, t0, barLen * 0.98, midiHz(degreeMidi(chordDeg + k, 0)), style.padWave, 0.07 * style.gain, barLen * 0.45, attack = 0.25)
      // lead: eighth-note pattern over the chord tones / scale
      for (e in 0 until 8) {
        if (rnd.nextDouble() > style.leadDensity) continue
        val deg = chordDeg + intArrayOf(0, 2, 4, 5, 7, 4, 2, 1)[(e + bar) % 8] + (if (rnd.nextDouble() < 0.2) 1 else 0)
        note(mix, sr, t0 + e * beat / 2, beat * 0.9, midiHz(degreeMidi(deg, style.leadOctave)), style.leadWave, 0.12 * style.gain, 0.35 * beat)
      }
      if (style.id == "storm_drone") rumble(mix, sr, t0, barLen, rnd)
    }
    // seamless loop: crossfade the tail (notes ringing past the end) onto the head
    val fadeN = (sr * 0.12).toInt().coerceAtMost(total / 4)
    val out = ShortArray(total)
    for (i in 0 until total) {
      var v = mix[i]
      if (i < fadeN) {
        val w = i.toFloat() / fadeN
        v = v * w // head fades in; tail below fades out symmetrically
      }
      val fromEnd = total - 1 - i
      if (fromEnd < fadeN) v *= fromEnd.toFloat() / fadeN
      out[i] = (tanh(v.toDouble()) * 28000).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun note(mix: FloatArray, sr: Int, start: Double, dur: Double, hz: Double, wave: String, gain: Double, decaySec: Double, attack: Double = 0.01) {
    val s0 = (start * sr).toInt()
    val n = (dur * sr).toInt()
    var phase = 0.0
    val dphase = 2 * PI * hz / sr
    for (i in 0 until n) {
      val idx = s0 + i
      if (idx >= mix.size) break
      phase += dphase
      if (phase > 2 * PI) phase -= 2 * PI
      val s = when (wave) {
        "square" -> if (phase < PI) 0.6 else -0.6
        "saw" -> (phase / PI - 1.0) * 0.7
        "triangle" -> 2.0 * abs(phase / PI - 1.0) - 1.0
        else -> sin(phase)
      }
      val t = i.toDouble() / sr
      val env = (if (t < attack) t / attack else 1.0) * exp(-t / decaySec)
      mix[idx] += (s * env * gain).toFloat()
    }
  }

  private fun rumble(mix: FloatArray, sr: Int, start: Double, dur: Double, rnd: Random) {
    val s0 = (start * sr).toInt()
    val n = (dur * sr).toInt()
    var lp = 0.0
    for (i in 0 until n) {
      val idx = s0 + i
      if (idx >= mix.size) break
      lp += 0.02 * ((rnd.nextDouble() * 2 - 1) - lp)
      val swell = 0.5 + 0.5 * sin(2 * PI * i / n)
      mix[idx] += (lp * swell * 0.5).toFloat()
    }
  }
}

/** Renders a seamless ambience loop (wind, birds, drips, storm) into 16-bit mono PCM. */
object AmbientGenerator {
  val ids = listOf("wind_birds", "ice_wind", "cave_drip", "storm")

  fun render(id: String, seconds: Int = 10): ShortArray {
    val sr = SoundSynth.SAMPLE_RATE
    val n = sr * seconds
    val rnd = Random(id.hashCode().toLong())
    val mix = FloatArray(n)
    val (cut, gustHz, level) = when (id) {
      "ice_wind" -> Triple(0.10, 2, 0.5)
      "storm" -> Triple(0.05, 3, 0.8)
      "cave_drip" -> Triple(0.015, 1, 0.12)
      else -> Triple(0.04, 1, 0.35)
    }
    var lp = 0.0
    for (i in 0 until n) {
      lp += cut * ((rnd.nextDouble() * 2 - 1) - lp)
      val gust = 0.55 + 0.45 * sin(2 * PI * gustHz * i / n) // integer number of cycles → seamless
      mix[i] = (lp * gust * level * 3.0).toFloat()
    }
    fun ping(at: Double, hz0: Double, hz1: Double, gain: Double, decay: Double) {
      val s0 = (at * sr).toInt()
      val len = (decay * 5 * sr).toInt()
      var ph = 0.0
      for (k in 0 until len) {
        val idx = (s0 + k) % n // wrap so loop ends stay continuous
        val t = k.toDouble() / sr
        val hz = hz0 + (hz1 - hz0) * (k.toDouble() / len)
        ph += 2 * PI * hz / sr
        mix[idx] += (sin(ph) * exp(-t / decay) * gain).toFloat()
      }
    }
    when (id) {
      "wind_birds" -> repeat(6) { ping(rnd.nextDouble() * seconds, 2600.0, 3600.0, 0.05, 0.04); ping(rnd.nextDouble() * seconds, 3000.0, 2200.0, 0.04, 0.05) }
      "cave_drip" -> repeat(9) { val t = rnd.nextDouble() * seconds; ping(t, 1300.0, 850.0, 0.12, 0.09); ping(t + 0.18, 1300.0, 850.0, 0.05, 0.12) }
      "storm" -> { val t = 3.0 + rnd.nextDouble() * 3; var l2 = 0.0; val s0 = (t * sr).toInt(); for (k in 0 until sr * 2) { l2 += 0.01 * ((rnd.nextDouble() * 2 - 1) - l2); mix[(s0 + k) % n] += (l2 * exp(-k.toDouble() / sr / 0.6) * 2.2).toFloat() } }
      else -> {}
    }
    val fade = sr / 8
    return ShortArray(n) { i ->
      var v = mix[i]
      if (i < fade) v *= i.toFloat() / fade
      if (n - 1 - i < fade) v *= (n - 1 - i).toFloat() / fade
      (tanh(v.toDouble()) * 26000).toInt().coerceIn(-32767, 32767).toShort()
    }
  }
}

/** Fires sparse one-shot environment sounds (chirps, drips, gusts) on top of an ambience loop. */
class AmbientDirector(private val audio: AudioManager, private val random: Random = Random(99)) {
  private var event: String? = null
  private var meanInterval = 6.0
  private var timer = 3.0
  private var rumbles = false
  private var rumbleTimer = 14.0

  fun setAmbience(id: String) {
    when (id) {
      "wind_birds" -> { event = "env_chirp"; meanInterval = 3.5 }
      "ice_wind", "storm" -> { event = "env_gust"; meanInterval = 9.0 }
      "cave_drip" -> { event = "env_drip"; meanInterval = 2.5 }
      else -> event = null
    }
    timer = meanInterval * (0.5 + random.nextDouble())
    rumbles = id == "storm" || id == "cave_drip"
    rumbleTimer = 8.0 + 10.0 * random.nextDouble()
  }

  fun update(dt: Double, listenerX: Double, listenerY: Double) {
    if (rumbles) {
      rumbleTimer -= dt
      if (rumbleTimer <= 0.0) { rumbleTimer = 12.0 + 12.0 * random.nextDouble(); audio.trigger("ambient_rumble", 0.6 + 0.4 * random.nextDouble()) }
    }
    val e = event ?: return
    timer -= dt
    if (timer > 0.0) return
    timer = meanInterval * (0.5 + random.nextDouble())
    audio.trigger(e, 0.7 + 0.3 * random.nextDouble(), listenerX + (random.nextDouble() * 2 - 1) * 14.0, listenerY + (random.nextDouble() * 2 - 1) * 8.0)
  }
}
