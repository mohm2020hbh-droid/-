package com.pogoascent.particles

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import com.pogoascent.render.Palette
import com.pogoascent.render.RenderBatches
import kotlinx.serialization.Serializable
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

@Serializable
data class ParticlePreset(
  val id: String,
  val count: Int,
  val speedMin: Double,
  val speedMax: Double,
  /** Half-angle of the emission cone around the emit direction, degrees (180 = all directions). */
  val spreadDeg: Double,
  val lifeMin: Double,
  val lifeMax: Double,
  val size0: Double,
  val size1: Double,
  val color: String,
  val gravity: Double,
  val drag: Double,
  /** "sphere" or "cube". */
  val shape: String = "sphere",
)

@Serializable
data class ParticleFile(val presets: List<ParticlePreset>)

/**
 * Fixed-capacity particle pool (structure of arrays, ring allocation: no per-particle objects, no garbage). The density
 * scale comes from the Graphics quality settings so low-end phones emit fewer particles.
 */
class ParticleSystem(presets: List<ParticlePreset>, val capacity: Int = 384, private val random: Random = Random(7)) {
  private val presets = presets.associateBy { it.id }
  private val x = DoubleArray(capacity)
  private val y = DoubleArray(capacity)
  private val vx = DoubleArray(capacity)
  private val vy = DoubleArray(capacity)
  private val age = DoubleArray(capacity)
  private val life = DoubleArray(capacity)
  private val size0 = FloatArray(capacity)
  private val size1 = FloatArray(capacity)
  private val grav = FloatArray(capacity)
  private val drag = FloatArray(capacity)
  private val rgb = FloatArray(capacity * 3)
  private val shape = ByteArray(capacity)
  private var cursor = 0

  /** Particles alive right now. */
  var alive = 0; private set
  /** 0..1 share of each preset's count that is actually emitted. */
  var density = 1.0
  var enabled = true
  private val colorCache = HashMap<String, FloatArray>()

  fun clear() { for (i in 0 until capacity) life[i] = 0.0; alive = 0 }

  /** Emit preset [id] at (px,py) around direction (dx,dy). [intensity] scales the particle count (0..1.5). */
  fun emit(id: String, px: Double, py: Double, dx: Double = 0.0, dy: Double = 1.0, intensity: Double = 1.0) {
    if (!enabled || density <= 0.0) return
    val p = presets[id] ?: return
    val n = (p.count * density * intensity.coerceIn(0.0, 1.5)).toInt().coerceIn(if (density > 0.2) 1 else 0, capacity / 2)
    val base = atan2(dy, dx)
    val spread = Math.toRadians(p.spreadDeg)
    val col = colorCache.getOrPut(p.color) { Palette.parse(p.color) }
    for (k in 0 until n) {
      val i = cursor
      cursor = (cursor + 1) % capacity
      val ang = base + (random.nextDouble() * 2 - 1) * spread
      val sp = p.speedMin + random.nextDouble() * (p.speedMax - p.speedMin)
      x[i] = px; y[i] = py
      vx[i] = cos(ang) * sp; vy[i] = sin(ang) * sp
      age[i] = 0.0
      life[i] = p.lifeMin + random.nextDouble() * (p.lifeMax - p.lifeMin)
      size0[i] = p.size0.toFloat(); size1[i] = p.size1.toFloat()
      grav[i] = p.gravity.toFloat(); drag[i] = p.drag.toFloat()
      rgb[i * 3] = col[0]; rgb[i * 3 + 1] = col[1]; rgb[i * 3 + 2] = col[2]
      shape[i] = if (p.shape == "cube") 1 else 0
    }
  }

  fun update(dt: Double) {
    var count = 0
    val damp = { d: Float -> exp(-d * dt) }
    for (i in 0 until capacity) {
      if (life[i] <= 0.0) continue
      age[i] += dt
      if (age[i] >= life[i]) { life[i] = 0.0; continue }
      val dm = damp(drag[i])
      vx[i] *= dm; vy[i] = vy[i] * dm + grav[i] * dt
      x[i] += vx[i] * dt; y[i] += vy[i] * dt
      count++
    }
    alive = count
  }

  /** Writes all live particles into the cube / sphere instance batches (z slightly in front of the rider). */
  fun render(out: RenderBatches) {
    for (i in 0 until capacity) {
      if (life[i] <= 0.0) continue
      val t = (age[i] / life[i]).toFloat()
      val s = size0[i] + (size1[i] - size0[i]) * t
      val a = 1f - t * t
      val batch = if (shape[i].toInt() == 1) out.cube else out.sphere
      batch.add(x[i].toFloat(), y[i].toFloat(), 0.9f, age[i].toFloat() * 3f, s, s, s, rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2], a)
    }
  }

  fun has(id: String) = id in presets

  companion object {
    const val RESOURCE_PATH = "data/particles.json"
    fun loadPresets(): List<ParticlePreset> = GameJson.pretty.decodeFromString(ParticleFile.serializer(), Resources.readText(RESOURCE_PATH)).presets
  }
}
