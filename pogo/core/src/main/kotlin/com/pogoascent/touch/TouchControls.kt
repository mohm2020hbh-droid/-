package com.pogoascent.touch

import com.pogoascent.player.PlayerInput
import com.pogoascent.settings.ControlLayout
import com.pogoascent.settings.ControlSettings
import kotlin.math.abs
import kotlin.math.hypot

/** Screen-space rectangle/circle the Android view draws. All in pixels. */
class ControlRect(var cx: Float = 0f, var cy: Float = 0f, var radius: Float = 0f)

/**
 * Converts raw multi-touch pointer events into [PlayerInput] for the three layouts. Pure maths, no Android types, so
 * every layout/sensitivity/deadzone combination is unit-tested. Feed it `down/move/up` per pointer id.
 *
 * Lean uses a *floating origin*: wherever the lean thumb lands first is "centre", dragging sideways leans; lifting the
 * thumb returns the stick upright. The jump button is a big circle (or the right half / second finger).
 */
class TouchControlMapper(var settings: ControlSettings = ControlSettings()) {
  var screenW = 1920f; private set
  var screenH = 1080f; private set

  private var leanPointer = -1
  private var leanOriginX = 0f
  private var leanX = 0f
  private var jumpPointer = -1

  /** Where the HUD should draw the jump button (SLIDER layout) – recomputed on resize/settings change. */
  val jumpButton = ControlRect()
  /** Lean origin/knob for drawing the slider while a thumb is down. */
  val leanOrigin = ControlRect()
  val leanKnob = ControlRect()
  val leanActive: Boolean get() = leanPointer >= 0
  val jumpActive: Boolean get() = jumpPointer >= 0

  /** Called once for a UI haptic "click" when a control is first pressed. */
  var onPress: (() -> Unit)? = null

  fun resize(w: Int, h: Int) {
    screenW = w.toFloat(); screenH = h.toFloat()
    layout()
  }

  fun apply(s: ControlSettings) { settings = s.sanitized(); layout(); releaseAll() }

  fun releaseAll() { leanPointer = -1; jumpPointer = -1 }

  private fun layout() {
    val r = (minOf(screenW, screenH) * 0.13f * settings.buttonSize.toFloat())
    val margin = r * 0.55f
    val x = if (settings.leftHanded) margin + r else screenW - margin - r
    jumpButton.cx = x; jumpButton.cy = screenH - margin - r; jumpButton.radius = r
  }

  private val travel: Float get() = (screenW * 0.16f / settings.sensitivity.toFloat()).coerceAtLeast(40f)

  private fun inLeanZone(x: Float): Boolean = if (settings.leftHanded) x > screenW * 0.5f else x < screenW * 0.5f

  private fun hitsJumpButton(x: Float, y: Float): Boolean {
    val dx = x - jumpButton.cx
    val dy = y - jumpButton.cy
    // generous hit area (1.35×) – thumbs are imprecise
    return hypot(dx, dy) <= jumpButton.radius * 1.35f
  }

  fun pointerDown(id: Int, x: Float, y: Float) {
    when (settings.layout) {
      ControlLayout.SLIDER -> {
        if (jumpPointer < 0 && hitsJumpButton(x, y)) { jumpPointer = id; onPress?.invoke() }
        else if (leanPointer < 0 && inLeanZone(x)) beginLean(id, x, y)
      }
      ControlLayout.SWIPE -> {
        if (inLeanZone(x)) { if (leanPointer < 0) beginLean(id, x, y) }
        else if (jumpPointer < 0) { jumpPointer = id; onPress?.invoke() }
      }
      ControlLayout.FULLSCREEN -> {
        if (leanPointer < 0) beginLean(id, x, y) else if (jumpPointer < 0) { jumpPointer = id; onPress?.invoke() }
      }
    }
  }

  private fun beginLean(id: Int, x: Float, y: Float) {
    leanPointer = id; leanOriginX = x; leanX = x
    leanOrigin.cx = x; leanOrigin.cy = y; leanOrigin.radius = travel
    leanKnob.cx = x; leanKnob.cy = y; leanKnob.radius = travel * 0.28f
  }

  fun pointerMove(id: Int, x: Float, @Suppress("UNUSED_PARAMETER") y: Float) {
    if (id == leanPointer) {
      leanX = x
      leanKnob.cx = x.coerceIn(leanOriginX - travel, leanOriginX + travel)
    }
  }

  fun pointerUp(id: Int) {
    if (id == leanPointer) leanPointer = -1
    if (id == jumpPointer) jumpPointer = -1
  }

  /** The current intent. Lean is dead-zoned and scaled so the full range is still reachable. */
  fun read(out: PlayerInput) {
    var lean = 0.0
    if (leanPointer >= 0) {
      val raw = ((leanX - leanOriginX) / travel).toDouble().coerceIn(-1.0, 1.0)
      val dz = settings.deadzone
      lean = if (abs(raw) <= dz) 0.0 else Math.signum(raw) * (abs(raw) - dz) / (1.0 - dz)
    }
    out.set(lean, jumpPointer >= 0)
  }
}
