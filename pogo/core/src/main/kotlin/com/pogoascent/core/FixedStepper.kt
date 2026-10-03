package com.pogoascent.core

/**
 * Fixed-timestep accumulator. Feed it real frame deltas; it tells how many physics ticks to run.
 * [alpha] (0..1) is the fraction of a tick left over – used by the renderer to interpolate.
 */
class FixedStepper(hz: Double, private val maxFrameDelta: Double, private val maxStepsPerFrame: Int) {
  var dt: Double = 1.0 / hz
    private set
  private var accumulator = 0.0

  /** Interpolation factor between the previous and the current physics state. */
  val alpha: Double get() = (accumulator / dt).coerceIn(0.0, 1.0)

  fun setHz(hz: Double) {
    dt = 1.0 / hz
  }

  fun reset() {
    accumulator = 0.0
  }

  /** Returns the number of fixed ticks to simulate for this frame. */
  fun advance(frameDelta: Double): Int {
    if (!(frameDelta > 0.0)) return 0 // also rejects NaN
    accumulator += frameDelta.coerceAtMost(maxFrameDelta)
    var steps = 0
    while (accumulator >= dt && steps < maxStepsPerFrame) {
      accumulator -= dt
      steps++
    }
    // Spiral-of-death guard: if we hit the cap, drop the backlog instead of carrying it forever.
    if (steps == maxStepsPerFrame && accumulator >= dt) accumulator = 0.0
    return steps
  }
}
