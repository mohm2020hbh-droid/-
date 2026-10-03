package com.pogoascent

import com.pogoascent.physics.Collider
import com.pogoascent.physics.Mover
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.PhysicsWorld
import com.pogoascent.physics.Sensor
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.player.PlayerInput
import com.pogoascent.player.PogoPlayer
import kotlin.math.roundToInt

val surfaces: SurfaceCatalog by lazy { SurfaceCatalog.load() }

/** Test harness: a player in a world, driven by scripted input, collecting events. */
class Rig(
  val world: PhysicsWorld,
  val cfg: PhysicsConfig = PhysicsConfig(),
  spawnX: Double = 0.0,
  spawnY: Double = 0.0,
  spawn: Boolean = true,
) {
  val events = ArrayList<GameEvent>()
  val player = PogoPlayer(cfg, world) { events += it }
  val input = PlayerInput()

  init { if (spawn) player.spawn(spawnX, spawnY) }

  /** When true, the rig steers the stick back to upright while airborne (what a skilled player does before landing). */
  var autoLevel = false

  private fun wrap(a: Double): Double {
    var w = a % (2 * Math.PI)
    if (w > Math.PI) w -= 2 * Math.PI
    if (w < -Math.PI) w += 2 * Math.PI
    return w
  }

  private fun step() {
    if (autoLevel && !player.grounded) {
      input.lean = (-(wrap(player.angle) * 4.0 + player.omega * 1.0) / cfg.rotationSpeed).coerceIn(-1.0, 1.0)
    }
    player.tick(input)
  }

  fun tick(n: Int = 1) { repeat(n) { step() } }
  fun seconds(s: Double) = tick((s * cfg.fixedTimestepHz).roundToInt())

  /** Ticks until [cond] holds or [maxSeconds] pass; true if it held. */
  fun until(maxSeconds: Double, cond: () -> Boolean): Boolean {
    val max = (maxSeconds * cfg.fixedTimestepHz).roundToInt()
    for (i in 0 until max) { if (cond()) return true; step() }
    return cond()
  }

  fun count(type: EventType) = events.count { it.type == type }
  fun first(type: EventType) = events.firstOrNull { it.type == type }

  /** Hold jump for [chargeSeconds] then release (one tick), leaving lean as currently set. */
  fun chargeAndRelease(chargeSeconds: Double) {
    input.jumpHeld = true
    tick(maxOf(1, (chargeSeconds * cfg.fixedTimestepHz).roundToInt()))
    input.jumpHeld = false
    tick()
  }

  fun waitForLanding(maxSeconds: Double = 8.0): Boolean = until(maxSeconds) { player.grounded }
}

fun floorWorld(id: String = "stone", extra: List<Collider> = emptyList(), sensors: List<Sensor> = emptyList(), killY: Double = -200.0): PhysicsWorld =
  PhysicsWorld(listOf(Collider.box("floor", 0.0, -1.0, 400.0, 2.0, surfaces[id])) + extra, sensors, killY)

fun box(id: String, cx: Double, cy: Double, w: Double, h: Double, surface: String = "stone", rot: Double = 0.0, oneWay: Boolean = false, mover: Mover? = null) =
  Collider.box(id, cx, cy, w, h, surfaces[surface], rot, oneWay, mover)
