package com.pogoascent.devtools

import com.pogoascent.levels.LevelLoader
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.player.EventType
import com.pogoascent.player.PlayerInput
import com.pogoascent.player.PogoPlayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/** Level-design aid: `probeJumps <root> <levelId> <surfaceX> <surfaceY>` prints where each lean × charge jump from that spot ends. */
fun main(args: Array<String>) {
  require(args.size >= 4) { "usage: probeJumps <root> <levelId> <surfaceX> <surfaceY>" }
  val physics = PhysicsConfig.load()
  val level = LevelLoader.load(args[1], SurfaceCatalog.load())
  val sx = args[2].toDouble()
  val sy = args[3].toDouble()
  fun wrap(a: Double): Double { var w = a % (2 * PI); if (w > PI) w -= 2 * PI; if (w < -PI) w += 2 * PI; return w }
  println("jumps from ($sx, $sy) in ${args[1]} (steering upright in the air)")
  for (lean in doubleArrayOf(-1.0, -0.7, -0.4, 0.0, 0.4, 0.7, 1.0)) {
    for (charge in doubleArrayOf(0.3, 0.55, 0.8, 1.0)) {
      val evs = ArrayList<String>()
      val p = PogoPlayer(physics, level.world) { e ->
        if (e.type == EventType.COLLISION || e.type == EventType.HARD_COLLISION || e.type == EventType.GOAL || e.type == EventType.HAZARD)
          evs += "${e.type.name.take(4)}@(%.1f,%.1f)".format(e.x, e.y)
      }
      level.world.reset()
      p.spawn(sx, sy)
      val input = PlayerInput()
      val hz = physics.fixedTimestepHz
      input.lean = lean
      var t = 0
      while (t < hz && abs(p.angle - lean * physics.maxLeanAngleRad) > 0.01) { p.tick(input); t++ }
      input.jumpHeld = true
      repeat(((charge * physics.jumpChargeTime * hz).roundToInt()).coerceAtLeast(1)) { p.tick(input) }
      input.jumpHeld = false
      p.tick(input)
      var g = 0
      var apex = p.y
      while (!p.grounded && !p.killed && g++ < 9 * hz) {
        input.lean = (-(wrap(p.angle) * 4.0 + p.omega) / physics.rotationSpeed).coerceIn(-1.0, 1.0)
        p.tick(input)
        if (p.y > apex) apex = p.y
      }
      println("  lean %5.2f charge %.2f -> %s tip (%.2f, %.2f) apex %.1f  %s".format(lean, charge, if (p.grounded) "landed" else "DEAD  ", p.tipCenterX, p.tipCenterY, apex, evs.take(3).joinToString(" ")))
    }
  }
}
