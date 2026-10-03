package com.pogoascent.render

import com.pogoascent.levels.GameSession
import kotlin.math.sin

/**
 * Per-frame scene content: moving platforms, checkpoint flags, blob shadow and the rider. Writes into pre-allocated
 * batches – nothing is allocated per frame.
 */
class DynamicScene(private val builder: SceneBuilder) {
  val batches = RenderBatches(256, 256, 192)
  private val level = builder.level
  private val palette = builder.palette
  private val shadowColor = floatArrayOf(0f, 0f, 0f)
  private var boostGlow = 0f

  fun build(session: GameSession, style: CharacterStyle, timeSec: Double, dt: Double) {
    batches.clear()
    val alpha = session.renderAlpha
    val p = session.player
    val cfg = session.physics

    // movers
    for (c in level.colliders) {
      if (c.mover == null) continue
      val col = palette.role(c.surface.colorRole)
      val w = c.maxX - c.minX
      val h = c.maxY - c.minY
      batches.cube.add((c.minX + c.maxX) / 2, (c.minY + c.maxY) / 2, 0.0, 0.0, w, h, SceneBuilder.DEPTH.toDouble(), col)
      // light strip on the top edge
      batches.cube.add((c.minX + c.maxX) / 2, c.maxY + 0.02, SceneBuilder.DEPTH / 2.0 - 0.1, 0.0, w, 0.08, 0.2, floatArrayOf(1f, 1f, 1f), 0.5f)
    }

    // checkpoint flags
    val reached = HashSet<String>()
    for (cp in level.data.checkpoints) {
      if (session.checkpointReached(cp.id)) reached.add(cp.id)
      val on = cp.id in reached
      val flag = if (on) palette.role("goal") else floatArrayOf(0.7f, 0.7f, 0.72f)
      batches.cylinder.add(cp.respawnX.toDouble(), cp.respawnY + 1.1, -0.9, 0.0, 0.1, 2.2, 0.1, palette.role("wall"))
      val wave = if (on) sin(timeSec * 5.0 + cp.respawnY) * 0.08 else 0.0
      batches.cube.add(cp.respawnX + 0.55, cp.respawnY + 1.85 + wave, -0.9, 0.0, 1.0, 0.6, 0.06, flag)
    }

    // blob shadow
    val gy = session.level.world.groundBelow(p.x, p.tipWorldY + 0.2, 30.0)
    if (!gy.isNaN()) {
      val h = (p.tipWorldY - gy).coerceAtLeast(0.0)
      val size = 1.0 / (1.0 + h * 0.08)
      batches.cylinder.add(p.tipWorldX, gy + 0.03, 0.4, 0.0, 1.1 * size, 0.02, 1.1 * size, shadowColor, (0.35 / (1.0 + h * 0.1)).toFloat())
    }

    // rider
    boostGlow = if (p.boostArmed) 1f else (boostGlow - (dt * 2.0).toFloat()).coerceAtLeast(0f)
    CharacterModel.add(
      batches, cfg, p.renderX(alpha), p.renderY(alpha), p.renderAngle(alpha), p.charge, p.grounded,
      p.vx, p.vy, p.state, boostGlow, style, timeSec,
    )
  }

  /** Called by the game when a boost fires so the trail flares even though the armed flag just cleared. */
  fun flashBoost() { boostGlow = 1.4f }

}
