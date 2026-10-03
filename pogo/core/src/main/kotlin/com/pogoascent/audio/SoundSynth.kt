package com.pogoascent.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Tiny offline synthesiser: turns a [SoundRecipe] into 16-bit mono PCM. Every sound in the game is generated from data, so
 * the APK ships no audio files (and none of the original game's audio is ever used).
 */
object SoundSynth {
  const val SAMPLE_RATE = 22050

  fun render(recipe: SoundRecipe, seed: Long = 0x5EED): ShortArray {
    val n = (SAMPLE_RATE * recipe.durationMs / 1000.0).toInt().coerceAtLeast(1)
    val mix = FloatArray(n)
    var layerIndex = 0
    for (layer in recipe.layers) {
      renderLayer(layer, mix, n, seed + layerIndex * 7919L)
      layerIndex++
    }
    return toPcm(mix)
  }

  private fun toPcm(mix: FloatArray): ShortArray {
    val out = ShortArray(mix.size)
    val fade = (SAMPLE_RATE * 0.004).toInt().coerceAtLeast(1) // 4 ms tail fade removes end clicks
    for (i in mix.indices) {
      var v = tanh(mix[i].toDouble()).toFloat() // soft clip: never exceeds ±1
      val fromEnd = mix.size - 1 - i
      if (fromEnd < fade) v *= fromEnd.toFloat() / fade
      out[i] = (v * 32000f).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun renderLayer(l: SynthLayer, mix: FloatArray, n: Int, seed: Long) {
    val start = (SAMPLE_RATE * l.delayMs / 1000.0).toInt()
    if (start >= n) return
    val len = n - start
    var phase = 0.0
    var rng = seed xor 0x9E3779B97F4A7C15uL.toLong()
    var lp = 0.0
    val alpha = if (l.cutoff > 0.0) 1.0 - exp(-2.0 * PI * l.cutoff / SAMPLE_RATE) else 1.0
    val attack = (SAMPLE_RATE * l.attackMs / 1000.0).coerceAtLeast(1.0)
    val decayTau = (SAMPLE_RATE * l.decayMs / 1000.0).coerceAtLeast(1.0)
    for (i in 0 until len) {
      val t = i.toDouble() / len
      val freq = l.freqStart * (l.freqEnd / l.freqStart).coerceAtLeast(1e-6).pow(t) // exponential sweep
      phase += 2.0 * PI * freq / SAMPLE_RATE
      if (phase > 2.0 * PI) phase -= 2.0 * PI
      var s = when (l.wave) {
        "square" -> if (phase < PI) 1.0 else -1.0
        "saw" -> phase / PI - 1.0
        "triangle" -> 2.0 * abs(phase / PI - 1.0) - 1.0
        "noise" -> {
          rng = rng * 6364136223846793005L + 1442695040888963407L
          ((rng ushr 33).toDouble() / (1L shl 31).toDouble()) * 2.0 - 1.0
        }
        else -> sin(phase)
      }
      if (l.wave == "noise" && l.cutoff > 0.0) { lp += alpha * (s - lp); s = lp * 2.0 }
      var env = (if (i < attack) i / attack else 1.0) * exp(-i / decayTau)
      if (l.tremolo > 0.0) env *= 1.0 - l.tremolo * 0.5 * (1.0 + sin(2.0 * PI * l.tremoloHz * i / SAMPLE_RATE))
      mix[start + i] += (s * env * l.gain).toFloat()
    }
  }

  fun peak(pcm: ShortArray): Int { var p = 0; for (s in pcm) p = maxOf(p, abs(s.toInt())); return p }
}
