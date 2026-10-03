package com.pogoascent.debug

import com.pogoascent.levels.GameSession
import com.pogoascent.levels.JumpStep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A scripted player: settle → (wait for the planned time) → lean → charge → release → steer upright → land, repeated for each
 * [JumpStep]. It mirrors the tick-by-tick sequence of the level validator, so a route proven by the validator replays
 * identically – including timing against moving platforms. Used by tests, the level renderer and the menu backdrop; it drives a
 * real [GameSession] through the same input path a human uses.
 */
class AutoPilot(private val session: GameSession, private val steps: List<JumpStep>) {
  private enum class Phase { SETTLE, WAIT, LEAN, CHARGE, FLY, DONE }

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
    val hz = cfg.fixedTimestepHz
    if (index >= steps.size) { phase = Phase.DONE; input.clear(); return }
    val step = steps[index]
    when (phase) {
      Phase.SETTLE -> {
        input.clear()
        // same rule as the validator: wait for the landing slide to die out (check before each tick)
        if (p.grounded && (hypot(p.vx, p.vy) <= 0.05 || counter >= (1.5 * hz).toInt())) { phase = Phase.WAIT; counter = 0; drive() } else counter++
      }
      Phase.WAIT -> {
        input.clear()
        if (session.level.world.time >= step.fromTime - 1e-9) { phase = Phase.LEAN; counter = 0; drive() }
      }
      Phase.LEAN -> {
        val target = step.lean * cfg.maxLeanAngleRad
        if (counter < hz && abs(p.angle - target) > 0.01) { input.lean = step.lean; input.jumpHeld = false; counter++ }
        else {
          chargeTicks = (step.charge * cfg.jumpChargeTime * hz).roundToInt().coerceAtLeast(1)
          phase = Phase.CHARGE; counter = 0; drive()
        }
      }
      Phase.CHARGE -> {
        if (counter < chargeTicks) { input.lean = step.lean; input.jumpHeld = true; counter++ }
        else { input.lean = step.lean; input.jumpHeld = false; phase = Phase.FLY; counter = 0 } // the release tick
      }
      Phase.FLY -> {
        input.jumpHeld = false
        if (p.grounded && counter > 0) { index++; phase = Phase.SETTLE; counter = 0; drive() }
        else {
          input.lean = (-(wrap(p.angle) * 4.0 + p.omega) / cfg.rotationSpeed).coerceIn(-1.0, 1.0)
          counter++
        }
      }
      Phase.DONE -> input.clear()
    }
  }
}
