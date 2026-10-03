package com.pogoascent.debug

import com.pogoascent.levels.GameSession
import com.pogoascent.levels.JumpStep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A scripted player: lean → charge → release → steer upright → land, repeated for each [JumpStep]. Used by tests, the
 * level renderer and demo mode to drive a real [GameSession] through the same input path a human uses.
 */
class AutoPilot(private val session: GameSession, private val steps: List<JumpStep>) {
  private enum class Phase { SETTLE, LEAN, CHARGE, RELEASE, FLY, DONE }

  private var index = 0
  private var phase = Phase.SETTLE
  private var counter = 0
  private var chargeTicks = 0

  val finished: Boolean get() = phase == Phase.DONE
  val stepIndex: Int get() = index

  private fun wrap(a: Double): Double {
    var w = a % (2 * PI)
    if (w > PI) w -= 2 * PI
    if (w < -PI) w += 2 * PI
    return w
  }

  /** Sets [GameSession.input] for the next physics tick. Call once per tick (before `session.update(fixedDt)`). */
  fun drive() {
    val input = session.input
    val p = session.player
    val cfg = session.physics
    if (index >= steps.size) { phase = Phase.DONE; input.clear(); return }
    val step = steps[index]
    when (phase) {
      Phase.SETTLE -> {
        input.clear()
        counter++
        if (p.grounded && (hypot(p.vx, p.vy) < 0.05 || counter > cfg.fixedTimestepHz * 1.5)) { phase = Phase.LEAN; counter = 0 }
      }
      Phase.LEAN -> {
        if (!p.grounded) { input.clear(); return }
        input.lean = step.lean; input.jumpHeld = false
        counter++
        if (abs(p.angle - step.lean * cfg.maxLeanAngleRad) < 0.01 || counter > cfg.fixedTimestepHz) {
          phase = Phase.CHARGE; counter = 0
          chargeTicks = (step.charge * cfg.jumpChargeTime * cfg.fixedTimestepHz).roundToInt().coerceAtLeast(1)
        }
      }
      Phase.CHARGE -> {
        input.lean = step.lean; input.jumpHeld = true
        if (++counter >= chargeTicks) phase = Phase.RELEASE
      }
      Phase.RELEASE -> {
        input.lean = step.lean; input.jumpHeld = false
        phase = Phase.FLY; counter = 0
      }
      Phase.FLY -> {
        input.jumpHeld = false
        input.lean = (-(wrap(p.angle) * 4.0 + p.omega) / cfg.rotationSpeed).coerceIn(-1.0, 1.0)
        counter++
        if (p.grounded && counter > 3) { index++; phase = Phase.SETTLE; counter = 0 }
      }
      Phase.DONE -> input.clear()
    }
  }
}
