package com.pogoascent.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.pogoascent.app.AppCore
import com.pogoascent.player.PlayerInput
import com.pogoascent.settings.ControlLayout
import com.pogoascent.ui.ScreenId
import kotlin.math.min

/**
 * Transparent view over the game surface: HUD (progress, timer, jumps, boosts, charge ring, hints, optional FPS), the touch
 * controls, and the Physics Test overlay. It also owns multi-touch handling and feeds [core.touch] → [InputBridge].
 */
class HudOverlay(context: Context, private val core: AppCore, private val input: InputBridge, private val onPause: () -> Unit) : View(context) {
  private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
  private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.WHITE }
  private val rect = RectF()
  private val tmp = PlayerInput()
  private val density = resources.displayMetrics.density
  private var pauseRect = RectF()
  private var pausePointer = -1
  var screen: ScreenId = ScreenId.MAIN_MENU

  private val isPlaying: Boolean get() = screen == ScreenId.PLAYING
  private val isTest: Boolean get() = screen == ScreenId.PHYSICS_TEST

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    core.touch.resize(w, h)
    val s = 52f * density
    pauseRect = RectF(w - s - 14 * density, 14 * density, w - 14 * density, 14 * density + s)
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    if (!(isPlaying || isTest)) return false
    val t = core.touch
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
        val i = ev.actionIndex
        val id = ev.getPointerId(i)
        if (pauseRect.contains(ev.getX(i), ev.getY(i))) { pausePointer = id } else t.pointerDown(id, ev.getX(i), ev.getY(i))
      }
      MotionEvent.ACTION_MOVE -> for (i in 0 until ev.pointerCount) t.pointerMove(ev.getPointerId(i), ev.getX(i), ev.getY(i))
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
        val i = ev.actionIndex
        val id = ev.getPointerId(i)
        if (id == pausePointer) { pausePointer = -1; if (pauseRect.contains(ev.getX(i), ev.getY(i))) onPause() } else t.pointerUp(id)
      }
      MotionEvent.ACTION_CANCEL -> { t.releaseAll(); pausePointer = -1 }
    }
    t.read(tmp)
    input.lean = tmp.lean
    input.jump = tmp.jumpHeld
    return true
  }

  /** Called when the screen changes so a finger left on a button cannot leave the jump latched. */
  fun resetTouches() {
    core.touch.releaseAll(); input.lean = 0.0; input.jump = false; pausePointer = -1
  }

  override fun onDraw(c: Canvas) {
    super.onDraw(c)
    val hud = core.controller.hud
    if (hud != null && hud.visible && (isPlaying || isTest || screen == ScreenId.PAUSE || screen == ScreenId.LEVEL_COMPLETE)) drawHud(c, hud)
    if (isPlaying || isTest) drawControls(c)
    if (isTest) drawDebug(c)
    if (isPlaying || isTest) postInvalidateOnAnimation()
  }

  private fun drawHud(c: Canvas, hud: com.pogoascent.ui.HudState) {
    val w = width.toFloat(); val h = height.toFloat()
    val d = density
    // climb progress bar (left edge)
    val barX = 14 * d; val barTop = h * 0.2f; val barBottom = h * 0.78f; val barW = 10 * d
    p.style = Paint.Style.FILL; p.color = Color.argb(110, 0, 0, 0)
    rect.set(barX, barTop, barX + barW, barBottom); c.drawRoundRect(rect, barW / 2, barW / 2, p)
    val fillTop = barBottom - (barBottom - barTop) * hud.progress.toFloat()
    p.color = Color.rgb(0xFF, 0xB7, 0x03)
    rect.set(barX, fillTop, barX + barW, barBottom); c.drawRoundRect(rect, barW / 2, barW / 2, p)
    p.color = Color.WHITE
    c.drawCircle(barX + barW / 2, fillTop, barW * 0.9f, p)
    text.textSize = 13 * d; text.textAlign = Paint.Align.LEFT
    c.drawText(hud.progressText, barX - 2 * d, barTop - 8 * d, text)

    // timer + counters (top centre)
    text.textAlign = Paint.Align.CENTER
    if (hud.timer.isNotEmpty()) { text.textSize = 26 * d; c.drawText(hud.timer, w / 2, 36 * d, text) }
    text.textSize = 14 * d
    c.drawText("${core.str("jumps")} ${hud.jumps}   ${core.str("boosts")} ${hud.boosts}", w / 2, 58 * d, text)
    if (hud.fps.isNotEmpty()) { text.textAlign = Paint.Align.LEFT; text.textSize = 12 * d; c.drawText(hud.fps, 40 * d, 24 * d, text) }

    if (hud.boostReady) {
      text.textAlign = Paint.Align.CENTER; text.textSize = 24 * d; text.color = Color.rgb(0xFF, 0xD2, 0x66)
      c.drawText("BOOST!", w / 2, h * 0.3f, text); text.color = Color.WHITE
    }
    if (hud.hint.isNotEmpty() && isPlaying) {
      text.textAlign = Paint.Align.CENTER; text.textSize = 16 * d
      val msg = core.str(hud.hint)
      p.color = Color.argb(130, 0, 0, 0)
      val tw = text.measureText(msg)
      rect.set(w / 2 - tw / 2 - 14 * d, h * 0.86f - 24 * d, w / 2 + tw / 2 + 14 * d, h * 0.86f + 12 * d)
      c.drawRoundRect(rect, 12 * d, 12 * d, p)
      c.drawText(msg, w / 2, h * 0.86f, text)
    }
    // pause button
    if (isPlaying || isTest) {
      p.color = Color.argb(140, 16, 24, 44)
      c.drawRoundRect(pauseRect, 14 * d, 14 * d, p)
      p.color = Color.WHITE
      val cx = pauseRect.centerX(); val cy = pauseRect.centerY()
      rect.set(cx - 9 * d, cy - 11 * d, cx - 3 * d, cy + 11 * d); c.drawRect(rect, p)
      rect.set(cx + 3 * d, cy - 11 * d, cx + 9 * d, cy + 11 * d); c.drawRect(rect, p)
    }
  }

  private fun drawControls(c: Canvas) {
    val t = core.touch
    val s = core.save.data.settings.controls
    val alpha = (s.buttonOpacity * 255).toInt().coerceIn(40, 255)
    val d = density
    val hud = core.controller.hud
    // jump button (always visible in SLIDER layout; a faint hint in the others)
    val showButton = s.layout == ControlLayout.SLIDER
    val b = t.jumpButton
    if (showButton) {
      p.style = Paint.Style.FILL
      p.color = if (t.jumpActive) Color.argb(alpha, 0xFF, 0xB7, 0x03) else Color.argb((alpha * 0.7f).toInt(), 0x2A, 0x3B, 0x66)
      c.drawCircle(b.cx, b.cy, b.radius, p)
      p.style = Paint.Style.STROKE; p.strokeWidth = 4 * d
      p.color = Color.argb(alpha, 255, 255, 255)
      c.drawCircle(b.cx, b.cy, b.radius, p)
      // charge ring
      val charge = (hud?.charge ?: 0.0).toFloat()
      if (charge > 0.01f) {
        p.strokeWidth = 8 * d; p.color = Color.argb(255, 0x6B, 0xD9, 0x7B)
        rect.set(b.cx - b.radius - 8 * d, b.cy - b.radius - 8 * d, b.cx + b.radius + 8 * d, b.cy + b.radius + 8 * d)
        c.drawArc(rect, -90f, 360f * charge, false, p)
      }
      p.style = Paint.Style.FILL
      text.textAlign = Paint.Align.CENTER; text.textSize = min(22f * d, b.radius * 0.45f); text.color = Color.WHITE
      c.drawText("JUMP", b.cx, b.cy + text.textSize * 0.35f, text)
    } else if ((hud?.charge ?: 0.0) > 0.01) {
      p.style = Paint.Style.STROKE; p.strokeWidth = 8 * d; p.color = Color.argb(220, 0x6B, 0xD9, 0x7B)
      val cx = if (s.leftHanded) 70 * d else width - 70 * d
      rect.set(cx - 40 * d, height - 110 * d, cx + 40 * d, height - 30 * d)
      c.drawArc(rect, -90f, 360f * (hud?.charge ?: 0.0).toFloat(), false, p)
      p.style = Paint.Style.FILL
    }
    // lean slider
    p.style = Paint.Style.STROKE; p.strokeWidth = 5 * d
    if (t.leanActive) {
      val o = t.leanOrigin; val k = t.leanKnob
      p.color = Color.argb(alpha / 2, 255, 255, 255)
      c.drawLine(o.cx - o.radius, o.cy, o.cx + o.radius, o.cy, p)
      p.style = Paint.Style.FILL; p.color = Color.argb(alpha, 0xFF, 0xB7, 0x03)
      c.drawCircle(k.cx, o.cy, 22 * d, p)
    } else if (s.layout != ControlLayout.FULLSCREEN) {
      // idle hint: where the lean thumb goes
      val cx = if (s.leftHanded) width * 0.75f else width * 0.25f
      p.color = Color.argb(alpha / 3, 255, 255, 255)
      c.drawLine(cx - 70 * d, height * 0.82f, cx + 70 * d, height * 0.82f, p)
      p.style = Paint.Style.FILL; c.drawCircle(cx, height * 0.82f, 16 * d, p)
    }
    p.style = Paint.Style.FILL
  }

  private fun drawDebug(c: Canvas) {
    val d = core.controller.debug ?: return
    val dn = density
    mono.textSize = 12 * dn
    val lines = listOf(
      "state ${d.state}  grounded ${d.grounded}",
      "velocity   %.2f, %.2f  (%.2f m/s)".format(d.vx, d.vy, d.speed),
      "horiz/vert %.2f / %.2f m/s".format(d.horizontalSpeed, d.verticalSpeed),
      "angular    %.2f rad/s   angle %.1f deg".format(d.angularVelocity, d.angleDeg),
      "jump power %.2f m/s  charge %.0f%%".format(d.jumpPower, d.charge * 100),
      "gravity    %.1f m/s2   air control %.2f".format(d.gravity, d.airControl),
      "collision n (%.2f, %.2f)".format(d.collisionNx, d.collisionNy),
      "boost angle %.0f deg  power x%.2f  %s".format(d.boostAngleDeg, d.boostPower, if (d.boostArmed) "ARMED" else ""),
      "height     %.2f m".format(d.height),
      "status: ${core.controller.debugStatus}",
    )
    p.style = Paint.Style.FILL; p.color = Color.argb(150, 0, 0, 0)
    val boxW = 330 * dn
    rect.set(40 * dn, 70 * dn, 40 * dn + boxW, 70 * dn + (lines.size + 1) * 16 * dn)
    c.drawRoundRect(rect, 10 * dn, 10 * dn, p)
    var y = 90 * dn
    for (l in lines) { c.drawText(l, 50 * dn, y, mono); y += 16 * dn }
    val res = core.controller.lastScenarioText
    if (res.isNotEmpty()) {
      var yy = 90 * dn
      mono.textAlign = Paint.Align.RIGHT
      p.color = Color.argb(150, 0, 0, 0)
      val rl = res.split("\n")
      rect.set(width - 300 * dn, 70 * dn + 60 * dn, width - 20 * dn, 70 * dn + 60 * dn + (rl.size + 1) * 16 * dn)
      c.drawRoundRect(rect, 10 * dn, 10 * dn, p)
      yy = 70 * dn + 82 * dn
      for (l in rl) { c.drawText(l, width - 30 * dn, yy, mono); yy += 16 * dn }
      mono.textAlign = Paint.Align.LEFT
    }
  }
}
