package com.pogoascent.levels

import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.player.PlayerInput
import com.pogoascent.player.PogoPlayer
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class JumpStep(val fromX: Double, val fromY: Double, val lean: Double, val charge: Double, val toX: Double, val toY: Double)

class ValidationReport(
  val levelId: String,
  val errors: List<String>,
  val warnings: List<String>,
  /** null = reachability not run; true/false = result of the simulated search. */
  val goalReachable: Boolean?,
  val nodesExplored: Int,
  val trials: Int,
  /** Shortest found chain of jumps start → goal (empty when unreachable / not run). */
  val path: List<JumpStep>,
  /** Every landing spot the search discovered as (tipX, tipY) – for debugging / level-design overlays. */
  val landingSpots: List<DoubleArray> = emptyList(),
  /** Ids of every solid the search could robustly land on (used by the level generator). */
  val reachedSolids: Set<String> = emptySet(),
) {
  val ok: Boolean get() = errors.isEmpty() && goalReachable != false
  override fun toString(): String = buildString {
    append("Level $levelId: ").append(if (ok) "OK" else "FAILED")
    append(" (reachable=").append(goalReachable).append(", nodes=").append(nodesExplored).append(", trials=").append(trials)
    if (path.isNotEmpty()) append(", jumps-to-goal=").append(path.size)
    append(")")
    errors.forEach { append("\n  ERROR: ").append(it) }
    warnings.forEach { append("\n  warn:  ").append(it) }
  }
}

/**
 * Structural checks + **reachability by simulation**: starting at the start surface, the validator performs real
 * jumps with the current [PhysicsConfig] (a grid of lean angles × charge levels, steering the stick upright in the
 * air like a competent player) and records where they land. It then searches the graph of landing spots for the goal.
 * Because it uses the real physics, retuning gravity or jump power re-validates every level automatically.
 */
object LevelValidator {
  private val LEANS = doubleArrayOf(-1.0, -0.7, -0.4, 0.0, 0.4, 0.7, 1.0)
  private val CHARGES = doubleArrayOf(0.3, 0.55, 0.8, 1.0)
  private const val BIN_WIDTH = 1.25
  private val VARIANTS = arrayOf(doubleArrayOf(0.05, 0.0), doubleArrayOf(-0.05, 0.0), doubleArrayOf(0.0, 0.04), doubleArrayOf(0.0, -0.04))
  private const val MAX_NODES = 1500
  private const val EDGE_MARGIN = 0.35
  private const val MIN_THICKNESS = 0.3

  fun validateStructure(data: LevelData, surfaces: SurfaceCatalog, physics: PhysicsConfig): Pair<MutableList<String>, MutableList<String>> {
    val errors = ArrayList<String>()
    val warnings = ArrayList<String>()
    if (data.id.isBlank()) errors += "id is empty"
    if (data.difficulty !in 1..10) errors += "difficulty ${data.difficulty} outside 1..10"
    if (data.goal.y <= data.start.y) errors += "goal (y=${data.goal.y}) must be above the start (y=${data.start.y})"
    val level = try {
      LevelLoader.load(data, surfaces)
    } catch (e: Exception) {
      errors += "cannot load: ${e.message}"
      return Pair(errors, warnings)
    }
    for (s in data.allSolids()) {
      if (s.kind == "box" && min(s.w, s.h) < MIN_THICKNESS) errors += "solid '${s.id}' is thinner than $MIN_THICKNESS m (tunnelling risk)"
      if (s.w <= 0 || s.h <= 0) errors += "solid '${s.id}' has non-positive size"
    }
    val lowest = level.colliders.minOf { it.minY }
    if (data.killY >= lowest) errors += "killY (${data.killY}) must be below the lowest solid ($lowest)"

    fun standsOn(x: Double, y: Double): Boolean {
      val cap = StringBuilder()
      val p = PogoPlayer(physics, level.world) { cap.append(it.type) }
      level.world.reset()
      p.spawn(x, y)
      return p.grounded
    }
    if (!standsOn(data.start.x, data.start.y)) errors += "start (${data.start.x}, ${data.start.y}) is not on a surface"
    for (c in data.checkpoints) {
      if (!standsOn(c.respawnX, c.respawnY)) errors += "checkpoint '${c.id}' respawn (${c.respawnX}, ${c.respawnY}) is not on a surface"
    }
    val ids = HashSet<String>()
    for (c in data.checkpoints) if (!ids.add(c.id)) errors += "duplicate checkpoint id '${c.id}'"
    if (data.parTimeSec <= 0) warnings += "parTimeSec should be positive"
    if (data.checkpoints.isEmpty() && data.goal.y - data.start.y > 80) warnings += "long level without checkpoints"
    return Pair(errors, warnings)
  }

  fun validate(data: LevelData, surfaces: SurfaceCatalog, physics: PhysicsConfig, deep: Boolean = true): ValidationReport {
    val (errors, warnings) = validateStructure(data, surfaces, physics)
    if (errors.isNotEmpty() || !deep) {
      return ValidationReport(data.id, errors, warnings, null, 0, 0, emptyList())
    }
    val level = LevelLoader.load(data, surfaces)
    val search = Search(level, physics)
    search.run()
    if (!search.found) warnings += "goal not reached by any simulated jump chain (explored ${search.nodes.size} landing spots)"
    return ValidationReport(data.id, errors, warnings, search.found, search.nodes.size, search.trials, search.path(), search.nodes.map { doubleArrayOf(it.spot.tipX, it.spot.tipY) }, search.reachedIds)
  }

  // ---------------------------------------------------------------------------------------------

  /** A standing position: the pinned tip-circle centre and the world time (matters for moving platforms). */
  class Spot(val tipX: Double, val tipY: Double, val time: Double)

  /** Where a simulated jump ended: robustly landed on solid [solidId] at [spot] (or at the goal). */
  class Landing(val solidId: String, val spot: Spot, val atGoal: Boolean, val lean: Double, val charge: Double)

  private class Node(val spot: Spot, val parent: Node?, val lean: Double, val charge: Double, val depth: Int)

  /**
   * The jump simulator shared by the validator and the level generator. [hops] answers: "from this standing position, which
   * platforms can a competent player *robustly* reach with one jump?"
   */
  class JumpSearch(val level: LoadedLevel, val physics: PhysicsConfig) {
    var trials = 0; private set
    private val hz = physics.fixedTimestepHz

    private class Outcome(val tipX: Double, val tipY: Double, val time: Double, val goal: Boolean, val colliderIdx: Int, val localX: Double, val phase: Int)

    fun startSpot(): Spot? {
      val p = PogoPlayer(physics, level.world) { }
      level.world.reset()
      p.spawn(level.startX, level.startY)
      return if (p.grounded) Spot(p.tipCenterX, p.tipCenterY, 0.0) else null
    }

    /** All robust single-jump landings from [from], one per (lean, charge) that passes the robustness test. */
    fun hops(from: Spot): List<Landing> {
      val out = ArrayList<Landing>()
      for (lean in LEANS) for (charge in CHARGES) {
        trials++
        val o = simulate(from, lean, charge) ?: continue
        if (!robust(from, lean, charge, o)) continue
        val col = level.colliders[o.colliderIdx]
        out += Landing(col.id, Spot(o.tipX, o.tipY, o.time), o.goal, lean, charge)
      }
      return out
    }

    /** Distinct landing key (collider + 2.5 m bin + mover phase) used to de-duplicate nodes. */
    fun keyOf(l: Landing): Long {
      val idx = level.colliders.indexOfFirst { it.id == l.solidId }
      val col = level.colliders[idx]
      val phase = col.mover?.let { ((l.spot.time / it.period) % 1.0 * 4).toInt() } ?: 0
      return (idx.toLong() shl 40) xor ((Math.floor((l.spot.tipX - col.ox) / BIN_WIDTH).toLong() and 0xFFFFFF) shl 8) xor phase.toLong()
    }

    /**
     * A human cannot hit the exact same charge time every attempt, so a jump only counts when small timing/aim errors
     * (lean ±0.05, charge ±4 %) still end on the same platform.
     */
    private fun robust(from: Spot, lean: Double, charge: Double, base: Outcome): Boolean {
      for (v in VARIANTS) {
        trials++
        val o = simulate(from, (lean + v[0]).coerceIn(-1.0, 1.0), (charge + v[1]).coerceIn(0.05, 1.0)) ?: return false
        if (base.colliderIdx != o.colliderIdx) return false
      }
      return true
    }

    /** The tip must stand well inside a surface (not on a corner): ground exists EDGE_MARGIN to both sides. */
    private fun insideSurface(tx: Double, ty: Double): Boolean {
      val surfaceY = ty - physics.tipRadius
      for (dx in doubleArrayOf(-EDGE_MARGIN, EDGE_MARGIN)) {
        val g = level.world.groundBelow(tx + dx, surfaceY + 0.5, 1.5)
        if (g.isNaN() || abs(g - surfaceY) > 0.45) return false
      }
      return true
    }

    private fun wrap(a: Double): Double {
      var w = a % (2 * PI)
      if (w > PI) w -= 2 * PI
      if (w < -PI) w += 2 * PI
      return w
    }

    /** One scripted jump: lean to [lean], charge for [charge]·chargeTime, release, steer upright, land, settle. */
    private fun simulate(from: Spot, lean: Double, charge: Double): Outcome? {
      var died = false
      val p = PogoPlayer(physics, level.world) { e: GameEvent ->
        if (e.type == EventType.KILL_FLOOR || e.type == EventType.HAZARD) died = true
      }
      level.world.advanceTo(from.time)
      p.spawnPinned(from.tipX, from.tipY)
      if (!p.grounded) return null
      val input = PlayerInput()
      // 1. lean to the target angle (max 1 s)
      val target = lean * physics.maxLeanAngleRad
      input.lean = lean
      var t = 0
      while (t < hz && abs(p.angle - target) > 0.01) { p.tick(input); t++ }
      // 2. charge and release
      input.jumpHeld = true
      val chargeTicks = (charge * physics.jumpChargeTime * hz).roundToInt().coerceAtLeast(1)
      for (i in 0 until chargeTicks) p.tick(input)
      input.jumpHeld = false
      p.tick(input)
      if (p.grounded) return null
      // 3. flight with upright steering
      var guard = 0
      val maxTicks = (9.0 * hz).toInt()
      while (!p.grounded && !died && !p.killed && guard++ < maxTicks) {
        input.lean = (-(wrap(p.angle) * 4.0 + p.omega) / physics.rotationSpeed).coerceIn(-1.0, 1.0)
        p.tick(input)
      }
      if (!p.grounded || died) return null
      // let the landing slide die out (a player waits for the stick to settle before the next jump)
      input.lean = 0.0; input.jumpHeld = false
      var settle = 0
      while (p.grounded && settle++ < (1.5 * hz).toInt() && hypot(p.vx, p.vy) > 0.05) p.tick(input)
      if (!p.grounded || p.killed || died) return null
      val col = p.groundColliderRef ?: return null
      if (!insideSurface(p.tipCenterX, p.tipCenterY)) return null
      // never treat a hazard landing as a safe node
      if (col.surface.hazard) return null
      val idx = level.colliders.indexOf(col)
      val phase = col.mover?.let { ((level.world.time / it.period) % 1.0 * 4).toInt() } ?: 0
      val g = level.data.goal
      val atGoal = abs(p.tipCenterX - g.x) <= g.w / 2 - 0.3 && abs(p.tipCenterY - physics.tipRadius - g.y) < 0.3
      return Outcome(p.tipCenterX, p.tipCenterY, level.world.time, atGoal, idx, p.tipCenterX - col.ox, phase)
    }
  }

  private class Search(val level: LoadedLevel, val physics: PhysicsConfig) {
    val nodes = ArrayList<Node>()
    var found = false
    val reachedIds = HashSet<String>()
    private var goalNode: Node? = null
    private val seen = HashSet<Long>()
    private val queue = ArrayDeque<Node>()
    private var maxY = -1e9
    private val js = JumpSearch(level, physics)
    val trials: Int get() = js.trials

    fun run() {
      val start = js.startSpot() ?: return
      enqueue(Node(start, null, 0.0, 0.0, 0))
      while (queue.isNotEmpty() && nodes.size < MAX_NODES && !found) {
        val n = queue.poll()
        if (n.spot.tipY < maxY - 18.0) continue // fell far behind the best progress – skip exploring from here
        for (l in js.hops(n.spot)) {
          reachedIds += l.solidId
          if (l.atGoal) {
            goalNode = Node(l.spot, n, l.lean, l.charge, n.depth + 1)
            nodes += goalNode!!
            found = true
            return
          }
          if (!seen.add(js.keyOf(l))) continue
          enqueue(Node(l.spot, n, l.lean, l.charge, n.depth + 1))
        }
      }
    }

    private fun enqueue(n: Node) {
      nodes += n
      if (n.spot.tipY > maxY) maxY = n.spot.tipY
      queue.add(n)
    }

    fun path(): List<JumpStep> {
      var n = goalNode ?: return emptyList()
      val out = ArrayList<JumpStep>()
      while (n.parent != null) {
        val p = n.parent!!
        out += JumpStep(p.spot.tipX, p.spot.tipY, n.lean, n.charge, n.spot.tipX, n.spot.tipY)
        n = p
      }
      out.reverse()
      return out
    }
  }
}
