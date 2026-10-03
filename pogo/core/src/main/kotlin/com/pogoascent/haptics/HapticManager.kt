package com.pogoascent.haptics

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import kotlinx.serialization.Serializable

/** Platform vibrator. [amplitude] is 1..255. */
interface HapticBackend {
  val supported: Boolean
  fun vibrate(durationMs: Int, amplitude: Int)
  fun cancel()
}

@Serializable
data class HapticEventDef(val id: String, val durationMs: Int, val intensity: Double, val cooldownMs: Int)

@Serializable
data class HapticFile(val events: List<HapticEventDef>)

/**
 * Event-driven haptics with intensity, duration and cooldown per event – never per frame. A global switch and intensity
 * scale come from the Controls settings.
 */
class HapticManager(
  events: List<HapticEventDef>,
  private val backend: HapticBackend,
  private val clockMs: () -> Long,
) {
  private val defs = events.associateBy { it.id }
  private val lastMs = HashMap<String, Long>()
  var enabled = true
  var intensityScale = 1.0
    set(v) { field = v.coerceIn(0.0, 1.0) }
  var triggered = 0; private set
  var droppedByCooldown = 0; private set

  /** @param scale 0..1+ extra scaling, e.g. impact speed. Returns true when a pulse was sent. */
  @Synchronized
  fun trigger(id: String, scale: Double = 1.0): Boolean {
    if (!enabled || !backend.supported || intensityScale <= 0.0) return false
    val d = defs[id] ?: return false
    val now = clockMs()
    val last = lastMs[id]
    if (last != null && now - last < d.cooldownMs) { droppedByCooldown++; return false }
    val strength = (d.intensity * scale.coerceIn(0.0, 1.5) * intensityScale).coerceIn(0.0, 1.0)
    val amp = (strength * 255).toInt()
    if (amp < 8) return false
    lastMs[id] = now
    backend.vibrate(d.durationMs, amp.coerceIn(1, 255))
    triggered++
    return true
  }

  fun cancel() = backend.cancel()
  fun has(id: String) = id in defs

  companion object {
    const val RESOURCE_PATH = "data/haptics.json"
    fun loadDefs(): List<HapticEventDef> = GameJson.pretty.decodeFromString(HapticFile.serializer(), Resources.readText(RESOURCE_PATH)).events
  }
}
