package com.pogoascent.render

import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.player.PlayerState
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class HatKind { NONE, CAP, TOPHAT, CROWN, BEANIE, HELMET, PROPELLER }

/** Everything the renderer needs to dress the rider (resolved from equipped items by the customization system). */
class CharacterStyle(
  val shirt: FloatArray = floatArrayOf(0.23f, 0.52f, 1.0f),
  val pants: FloatArray = floatArrayOf(0.18f, 0.2f, 0.32f),
  val skin: FloatArray = floatArrayOf(0.96f, 0.76f, 0.6f),
  val stick: FloatArray = floatArrayOf(0.72f, 0.74f, 0.78f),
  val spring: FloatArray = floatArrayOf(0.9f, 0.25f, 0.22f),
  val hat: HatKind = HatKind.CAP,
  val hatColor: FloatArray = floatArrayOf(0.95f, 0.35f, 0.25f),
  val trail: FloatArray = floatArrayOf(1f, 0.8f, 0.2f),
  val emote: String = "none",
)

/**
 * Builds the rider + pogo stick out of primitives (no meshes/textures to ship). Pose comes from the simulation; the
 * model is posed in the stick frame: `s` along the axis from the centre of mass towards the head, `l` lateral.
 */
object CharacterModel {
  private const val Z = 0.0

  fun add(
    out: RenderBatches, cfg: PhysicsConfig,
    px: Double, py: Double, angle: Double, charge: Double, grounded: Boolean,
    vx: Double, vy: Double, state: PlayerState, boostGlow: Float, style: CharacterStyle, time: Double,
  ) {
    val sinT = sin(angle)
    val cosT = cos(angle)
    val phi = -angle
    val tipDist = if (grounded) cfg.tipOffset - charge * cfg.chargeCompression else cfg.tipOffset

    fun wx(s: Double, l: Double) = px + s * sinT + l * cosT
    fun wy(s: Double, l: Double) = py + s * cosT - l * sinT

    fun part(batch: InstanceBatch, s: Double, l: Double, z: Double, sx: Double, sy: Double, sz: Double, c: FloatArray, rotExtra: Double = 0.0) {
      batch.add(wx(s, l), wy(s, l), z, phi + rotExtra, sx, sy, sz, c)
    }

    /** Cylinder between two stick-frame points. */
    fun limb(s0: Double, l0: Double, s1: Double, l1: Double, z: Double, thick: Double, c: FloatArray) {
      val ax = wx(s0, l0); val ay = wy(s0, l0); val bx = wx(s1, l1); val by = wy(s1, l1)
      val dx = bx - ax; val dy = by - ay
      val len = hypot(dx, dy)
      if (len < 1e-6) return
      out.cylinder.add((ax + bx) / 2, (ay + by) / 2, z, Math.atan2(-dx, dy), thick, len, thick, c)
    }

    val dark = floatArrayOf(0.12f, 0.12f, 0.14f)

    // ---- pogo stick -------------------------------------------------------------------------
    limb(-tipDist, 0.0, 0.66, 0.0, Z, 0.07, style.stick)
    part(out.sphere, -tipDist, 0.0, Z, 0.16, 0.16, 0.16, dark) // rubber foot
    val footRest = -0.45
    val springLen = (tipDist - 0.45).coerceAtLeast(0.02)
    val rings = 5
    for (i in 0 until rings) {
      val s = -tipDist + (i + 0.5) / rings * springLen
      part(out.cylinder, s, 0.0, Z, 0.17, springLen / rings * 0.6, 0.17, style.spring)
    }
    part(out.cube, footRest, 0.0, Z, 0.46, 0.07, 0.5, style.stick)
    part(out.cube, 0.66, 0.0, Z, 0.07, 0.07, 0.62, style.stick) // handlebar

    // ---- rider ------------------------------------------------------------------------------
    val squash = 1.0 - 0.06 * charge
    for (side in intArrayOf(-1, 1)) {
      limb(0.12, 0.0, -0.43, 0.10, side * 0.14, 0.12, style.pants) // legs
      limb(0.56, 0.02, 0.64, 0.22, side * 0.2, 0.1, style.skin) // arms to the handlebar
    }
    part(out.cube, 0.37, 0.0, Z, 0.34, 0.52 * squash, 0.36, style.shirt) // torso
    val headS = 0.86 * (1.0 - 0.02 * charge)
    val bob = if (style.emote == "happy" && state == PlayerState.GROUNDED) 0.03 * sin(time * 9.0) else 0.0
    part(out.sphere, headS + bob, 0.0, Z, 0.38, 0.38, 0.38, style.skin)
    part(out.cube, headS + 0.02 + bob, 0.15, 0.1, 0.05, 0.06, 0.06, dark) // eyes (face looks right)
    part(out.cube, headS + 0.02 + bob, 0.15, -0.1, 0.05, 0.06, 0.06, dark)

    // ---- hat --------------------------------------------------------------------------------
    val topS = headS + bob + 0.15
    val hc = style.hatColor
    when (style.hat) {
      HatKind.NONE -> {}
      HatKind.CAP -> {
        part(out.cylinder, topS - 0.03, 0.0, Z, 0.4, 0.14, 0.4, hc)
        part(out.cube, topS - 0.06, 0.24, Z, 0.2, 0.04, 0.34, hc)
      }
      HatKind.TOPHAT -> {
        part(out.cylinder, topS + 0.13, 0.0, Z, 0.3, 0.36, 0.3, hc)
        part(out.cylinder, topS - 0.03, 0.0, Z, 0.5, 0.05, 0.5, hc)
      }
      HatKind.CROWN -> {
        part(out.cylinder, topS, 0.0, Z, 0.4, 0.14, 0.4, hc)
        for (k in -1..1) part(out.cube, topS + 0.12, 0.0, k * 0.13, 0.07, 0.14, 0.07, hc, rotExtra = 0.0)
      }
      HatKind.BEANIE -> {
        part(out.sphere, topS - 0.04, 0.0, Z, 0.44, 0.3, 0.44, hc)
        part(out.sphere, topS + 0.14, 0.0, Z, 0.12, 0.12, 0.12, floatArrayOf(1f, 1f, 1f))
      }
      HatKind.HELMET -> part(out.sphere, topS - 0.12, 0.0, Z, 0.5, 0.46, 0.5, hc)
      HatKind.PROPELLER -> {
        part(out.cylinder, topS + 0.02, 0.0, Z, 0.4, 0.1, 0.4, hc)
        part(out.cube, topS + 0.12, 0.0, Z, 0.5, 0.02, 0.07, floatArrayOf(1f, 1f, 1f), rotExtra = time * 18.0)
      }
    }

    // ---- boost trail ------------------------------------------------------------------------
    if (boostGlow > 0.01f) {
      val sp = hypot(vx, vy)
      val dx = if (sp > 0.1) -vx / sp else 0.0
      val dy = if (sp > 0.1) -vy / sp else -1.0
      for (i in 1..6) {
        val f = i / 6.0
        val size = (0.5 * (1.0 - f) + 0.1) * boostGlow
        out.sphere.add(px + dx * f * 2.2, py + dy * f * 2.2, Z - 0.1, 0.0, size, size, size, style.trail, (1.0 - f).toFloat() * 0.8f)
      }
    }
  }
}
