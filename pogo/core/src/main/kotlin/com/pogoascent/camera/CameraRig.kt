package com.pogoascent.camera

import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.render.Mat4
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Third-person camera for vertical climbing. Pure maths: feed it the (interpolated) player pose each render frame,
 * read back eye/target/fov/roll or the finished matrices. Components: smooth follow with dead-zone, separate
 * vertical/horizontal tracking, velocity look-ahead, landing dip, trauma-based shake, boost kick, fall zoom, user zoom.
 */
class CameraRig(var config: CameraConfig = CameraConfig()) {
  // outputs (world space)
  var eyeX = 0.0; private set
  var eyeY = 0.0; private set
  var eyeZ = 0.0; private set
  var targetX = 0.0; private set
  var targetY = 0.0; private set
  var targetZ = 0.0; private set
  var fovDeg = config.fovDeg; private set
  var rollRad = 0.0; private set
  var distance = config.distance; private set

  // internal state
  private var focusX = 0.0
  private var focusY = 0.0
  private var velX = 0.0
  private var velY = 0.0
  private var lookX = 0.0
  private var lookY = 0.0
  private var lookVelX = 0.0
  private var lookVelY = 0.0
  private var zoomMul = 1.0
  private var zoomVel = 0.0
  private var dip = 0.0
  private var trauma = 0.0
  private var shakeTime = 0.0
  private var boostTimer = 0.0
  private var initialised = false

  val shakeTrauma: Double get() = trauma

  /** Teleport the camera (level start / respawn) with no smoothing. */
  fun snapTo(px: Double, py: Double) {
    focusX = px; focusY = py
    velX = 0.0; velY = 0.0; lookX = 0.0; lookY = 0.0; lookVelX = 0.0; lookVelY = 0.0
    zoomMul = 1.0; zoomVel = 0.0; dip = 0.0; trauma = 0.0; boostTimer = 0.0
    initialised = true
    applyOutputs(0.0, 0.0, 0.0, 1.0, CameraUserSettings())
  }

  fun onEvent(e: GameEvent) {
    when (e.type) {
      EventType.LAND -> dip = min(config.landingDipMax, max(dip, e.magnitude * config.landingDipPerSpeed))
      EventType.HARD_COLLISION, EventType.SLIP ->
        trauma = min(config.shakeTraumaMax, trauma + e.magnitude * config.shakeTraumaPerSpeed)
      EventType.BOOST -> boostTimer = config.boostDuration
      else -> {}
    }
  }

  /**
   * @param px,py player position (interpolated)  @param vx,vy player velocity
   */
  fun update(dt: Double, px: Double, py: Double, vx: Double, vy: Double, user: CameraUserSettings = CameraUserSettings()) {
    if (!initialised) snapTo(px, py)
    if (!(dt > 0.0)) return
    val c = config

    // --- look-ahead (smoothed so a collision does not jerk the view) ---
    val aheadX = (vx * c.lookAheadXFactor).coerceIn(-c.lookAheadXMax, c.lookAheadXMax)
    val aheadY = if (vy >= 0.0) min(vy * c.lookAheadUpFactor, c.lookAheadUpMax) else max(vy * c.lookAheadDownFactor, -c.lookAheadDownMax)
    lookX = smoothDamp(lookX, aheadX, true, c.lookAheadSmoothTime, dt)
    lookY = smoothDamp(lookY, aheadY, false, c.lookAheadSmoothTime, dt)

    // --- follow with dead-zone: the camera only moves when the player leaves the box ---
    val wantX = deadZone(focusX, px + 0.0, c.deadZoneX)
    val wantY = deadZone(focusY, py, c.deadZoneY)
    focusX = smoothDampFocus(focusX, wantX, true, c.followTimeX, dt)
    focusY = smoothDampFocus(focusY, wantY, false, c.followTimeY, dt)

    // --- zoom: user × fall × boost, smoothed ---
    val fall = if (vy < -c.fallZoomStartSpeed) min(1.0, (-vy - c.fallZoomStartSpeed) / (c.fallZoomFullSpeed - c.fallZoomStartSpeed)) else 0.0
    boostTimer = max(0.0, boostTimer - dt)
    val boost = boostTimer / c.boostDuration
    val targetZoom = 1.0 + c.fallZoomOut * fall + c.boostZoomOut * boost
    zoomMul = smoothDampZoom(zoomMul, targetZoom, c.zoomSmoothTime, dt)

    // --- landing dip recovers; trauma decays; shake clock runs ---
    dip *= exp(-dt / c.landingRecoverTime)
    if (dip < 1e-4) dip = 0.0
    trauma = max(0.0, trauma - c.shakeDecayPerSecond * dt)
    shakeTime += dt

    val intensity = user.shakeIntensity.coerceIn(0.0, 1.0)
    val amp = trauma * trauma * intensity
    val sx = amp * c.shakeMaxOffset * noise(shakeTime * c.shakeFrequencyHz, 0)
    val sy = amp * c.shakeMaxOffset * noise(shakeTime * c.shakeFrequencyHz, 1)
    val roll = Math.toRadians(amp * c.shakeMaxRollDeg * noise(shakeTime * c.shakeFrequencyHz, 2))
    applyOutputs(sx, sy, roll, user.zoom.coerceIn(c.zoomMin, c.zoomMax), user)
  }

  private fun applyOutputs(shakeX: Double, shakeY: Double, roll: Double, userZoom: Double, @Suppress("UNUSED_PARAMETER") user: CameraUserSettings) {
    val c = config
    val tx = focusX + lookX + shakeX
    val ty = focusY + lookY + c.verticalBias - dip + shakeY
    distance = c.distance * userZoom * zoomMul
    val pitch = Math.toRadians(c.pitchDeg)
    targetX = tx; targetY = ty; targetZ = 0.0
    eyeX = tx
    eyeY = ty + sin(pitch) * distance
    eyeZ = Math.cos(pitch) * distance
    fovDeg = c.fovDeg + c.boostFovKickDeg * (boostTimer / c.boostDuration)
    rollRad = roll
  }

  /** Writes view (and, given an aspect ratio, projection and view-projection) matrices. */
  fun viewMatrix(out: FloatArray) {
    val upX = -sin(rollRad).toFloat()
    val upY = Math.cos(rollRad).toFloat()
    Mat4.lookAt(out, eyeX.toFloat(), eyeY.toFloat(), eyeZ.toFloat(), targetX.toFloat(), targetY.toFloat(), targetZ.toFloat(), upX, upY, 0f)
  }

  fun projectionMatrix(out: FloatArray, aspect: Float) {
    Mat4.perspective(out, fovDeg.toFloat(), aspect, NEAR, FAR)
  }

  /** Half of the visible world width/height on the gameplay plane (z = 0), for culling and level framing. */
  fun visibleHalfHeight(): Double = distance * Math.tan(Math.toRadians(fovDeg) / 2.0)
  fun visibleHalfWidth(aspect: Double): Double = visibleHalfHeight() * aspect

  // ---- helpers -------------------------------------------------------------------------------------

  private fun deadZone(current: Double, target: Double, half: Double): Double {
    val d = target - current
    return when {
      d > half -> target - half
      d < -half -> target + half
      else -> current
    }
  }

  private var sdVel = 0.0

  private fun smoothDampFocus(cur: Double, target: Double, horizontal: Boolean, smoothTime: Double, dt: Double): Double {
    val value = smoothDampCore(cur, target, if (horizontal) velX else velY, smoothTime, dt)
    if (horizontal) velX = sdVel else velY = sdVel
    return value
  }

  private fun smoothDamp(cur: Double, target: Double, horizontal: Boolean, smoothTime: Double, dt: Double): Double {
    val value = smoothDampCore(cur, target, if (horizontal) lookVelX else lookVelY, smoothTime, dt)
    if (horizontal) lookVelX = sdVel else lookVelY = sdVel
    return value
  }

  private fun smoothDampZoom(cur: Double, target: Double, smoothTime: Double, dt: Double): Double {
    val value = smoothDampCore(cur, target, zoomVel, smoothTime, dt)
    zoomVel = sdVel
    return value
  }

  /** Critically damped spring (Unity-style SmoothDamp). Returns the new position; the new velocity is left in [sdVel] (no allocation). */
  private fun smoothDampCore(cur: Double, target: Double, vel: Double, smoothTime: Double, dt: Double): Double {
    val st = max(0.0001, smoothTime)
    val omega = 2.0 / st
    val x = omega * dt
    val e = 1.0 / (1.0 + x + 0.48 * x * x + 0.235 * x * x * x)
    val change = cur - target
    val temp = (vel + omega * change) * dt
    var newVel = (vel - omega * temp) * e
    var out = target + (change + temp) * e
    if ((target - cur > 0.0) == (out > target)) { out = target; newVel = 0.0 }
    sdVel = newVel
    return out
  }

  /** Deterministic smooth noise in -1..1 (value noise on a hashed lattice). */
  private fun noise(t: Double, channel: Int): Double {
    val i = Math.floor(t).toLong()
    val f = t - i
    val a = hash(i, channel)
    val b = hash(i + 1, channel)
    val s = f * f * (3.0 - 2.0 * f)
    return a + (b - a) * s
  }

  private fun hash(i: Long, channel: Int): Double {
    var h = i * 0x9E3779B97F4A7C15uL.toLong() + channel * 0x632BE59BD9B4E019L
    h = h xor (h ushr 30); h *= -0x40a7b892e31b1a47L
    h = h xor (h ushr 27); h *= -0x6b2fb644ecceee15L
    h = h xor (h ushr 31)
    return ((h ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
  }

  /** Is world point (x,y) on the gameplay plane inside the view (with [margin] metres)? */
  fun isVisible(x: Double, y: Double, aspect: Double, margin: Double): Boolean =
    abs(x - targetX) <= visibleHalfWidth(aspect) + margin && abs(y - targetY) <= visibleHalfHeight() + margin

  companion object {
    const val NEAR = 0.5f
    const val FAR = 400f
  }
}
