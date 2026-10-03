package com.pogoascent.audio

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import kotlinx.serialization.Serializable

/** The audio buckets from the task. MUSIC / AMBIENT+ENVIRONMENT / everything else map to the three user sliders. */
enum class AudioCategory { MUSIC, SFX, PLAYER, COLLISION, JUMP, LANDING, BOOST, FALL, UI, ENVIRONMENT, AMBIENT, GOAL, FAILURE }

/** One layer of a synthesised sound: an oscillator or noise with a pitch sweep and an envelope. */
@Serializable
data class SynthLayer(
  /** sine | square | saw | triangle | noise */
  val wave: String = "sine",
  val freqStart: Double = 440.0,
  val freqEnd: Double = 440.0,
  val gain: Double = 0.5,
  val attackMs: Double = 3.0,
  /** Time constant of the exponential decay (ms). */
  val decayMs: Double = 120.0,
  /** One-pole low-pass cutoff in Hz for noise layers (0 = unfiltered). */
  val cutoff: Double = 0.0,
  /** Start offset of this layer inside the sound (ms). */
  val delayMs: Double = 0.0,
  /** Vibrato / tremolo depth 0..1 and rate (Hz) – applied to amplitude. */
  val tremolo: Double = 0.0,
  val tremoloHz: Double = 0.0,
)

@Serializable
data class SoundRecipe(val id: String, val durationMs: Int, val layers: List<SynthLayer>)

/**
 * A game audio event. Every event supports volume, pitch, random variation, cooldown, spatial audio and priority.
 */
@Serializable
data class AudioEventDef(
  val id: String,
  val category: AudioCategory,
  val recipe: String,
  val volume: Double = 1.0,
  val pitch: Double = 1.0,
  /** ± fraction of random pitch variation per play (0.08 = ±8 %). */
  val pitchVariation: Double = 0.0,
  /** ± fraction of random volume variation per play. */
  val volumeVariation: Double = 0.0,
  /** Minimum seconds between two plays of this event. */
  val cooldown: Double = 0.0,
  /** Pan and distance-attenuate relative to the listener. */
  val spatial: Boolean = false,
  /** Higher wins when voices are scarce. */
  val priority: Int = 5,
  val loop: Boolean = false,
)

@Serializable
data class AudioFile(val recipes: List<SoundRecipe>, val events: List<AudioEventDef>)

class AudioCatalog(val recipes: Map<String, SoundRecipe>, val events: Map<String, AudioEventDef>) {
  companion object {
    const val RESOURCE_PATH = "data/audio_events.json"
    fun load(): AudioCatalog = from(GameJson.pretty.decodeFromString(AudioFile.serializer(), Resources.readText(RESOURCE_PATH)))
    fun from(f: AudioFile): AudioCatalog {
      val recipes = f.recipes.associateBy { it.id }
      val events = f.events.associateBy { it.id }
      for (e in f.events) require(e.recipe in recipes) { "audio event '${e.id}' uses unknown recipe '${e.recipe}'" }
      require(recipes.size == f.recipes.size) { "duplicate recipe id" }
      require(events.size == f.events.size) { "duplicate audio event id" }
      return AudioCatalog(recipes, events)
    }
  }
}
