package com.pogoascent.levels

import com.pogoascent.physics.Collider
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.player.PlayerInput
import com.pogoascent.player.PogoPlayer
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** One planned jump. [fromTime] is the world time at which the route expects to start it (matters for moving platforms). */
class JumpStep(val fromX: Double, val fromY: Double, val lean: Double, val charge: Double, val toX: Double, val toY: Double, val fromTime: Double = 0.0)

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
  private const val BIN_WIDTH = 0.5
  private val VARIANTS = arrayOf(doubleArrayOf(0.05, 0.0), doubleArrayOf(-0.05, 0.0), doubleArrayOf(0.0, 0.04), doubleArrayOf(0.0, -0.04))
  private const val MAX_NODES = 4000
  /** With moving platforms the player may wait: launches are tried at this many moments across the longest platform cycle. */
  private const val WAITS = 8
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

  /**
   * A standing position: the pinned tip-circle centre and the world time of arrival. When the player stands on a moving
   * platform ([moverId]), [localX]/[localY] are the tip's coordinates relative to that platform, so the spot can be
   * re-projected to any later time (the player may wait on a platform for the right moment).
   */
  class Spot(val tipX: Double, val tipY: Double, val time: Double, val moverId: String? = null, val localX: Double = tipX, val localY: Double = tipY)

  /** Where a simulated jump ended: robustly landed on solid [solidId] at [spot] (or at the goal). [launchTime] = when it started. */
  class Landing(val solidId: String, val spot: Spot, val atGoal: Boolean, val lean: Double, val charge: Double, val launchTime: Double)

  private class Node(val spot: Spot, val parent: Node?, val lean: Double, val charge: Double, val depth: Int, val launchTime: Double, val key: Long)

  /**
   * The jump simulator shared by the validator and the level generator. [hops] answers: "from this standing position, which
   * platforms can a competent player *robustly* reach with one jump?" – trying several launch moments when the level has
   * moving platforms, because a player can simply wait for the right phase.
   */
  class JumpSearch(val level: LoadedLevel, val physics: PhysicsConfig) {
    var trials = 0; private set
    private val hz = physics.fixedTimestepHz
    private val byId: Map<String, Collider> = level.colliders.associateBy { it.id }
    private val indexOf: Map<String, Int> = level.colliders.withIndex().associate { it.value.id to it.index }
    private val waitStep: Double = level.colliders.mapNotNull { it.mover?.period }.maxOrNull()?.let { it / WAITS } ?: 0.0

    private class Outcome(val tipX: Double, val tipY: Double, val time: Double, val goal: Boolean, val colliderIdx: Int, val moverId: String?, val localX: Double, val localY: Double)

    fun startSpot(): Spot? {
      val p = PogoPlayer(physics, level.world) { }
      level.world.reset()
      p.spawn(level.startX, level.startY)
      return if (p.grounded) Spot(p.tipCenterX, p.tipCenterY, 0.0) else null
    }

    /** Where [from] is at world time [t] (moving platforms carry the player). */
    private fun at(from: Spot, t: Double): Spot {
      val m = from.moverId?.let { byId[it]?.mover } ?: return Spot(from.tipX, from.tipY, t)
      return Spot(from.localX + m.offsetX(t), from.localY + m.offsetY(t), t, from.moverId, from.localX, from.localY)
    }

    /** All robust single-jump landings from [from], one per (launch time, lean, charge) that passes the robustness test. */
    fun hops(from: Spot): List<Landing> {
      val out = ArrayList<Landing>()
      val launches = if (waitStep > 0.0) WAITS else 1
      for (w in 0 until launches) {
        val start = at(from, from.time + w * waitStep)
        for (lean in LEANS) for (charge in CHARGES) {
          trials++
          val o = simulate(start, lean, charge) ?: continue
          if (!robust(start, lean, charge, o)) continue
          val col = level.colliders[o.colliderIdx]
          out += Landing(col.id, Spot(o.tipX, o.tipY, o.time, o.moverId, o.localX, o.localY), o.goal, lean, charge, start.time)
        }
      }
      return out
    }

    /** De-duplication key: platform + position bin along it (arrival time is handled by "earliest arrival wins"). */
    fun keyOf(l: Landing): Long {
      val idx = indexOf.getValue(l.solidId)
      val col = level.colliders[idx]
      return (idx.toLong() shl 32) xor (Math.floor((l.spot.tipX - col.ox) / BIN_WIDTH).toLong() and 0xFFFFFFL)
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
      val idx = indexOf.getValue(col.id)
      val g = level.data.goal
      val atGoal = abs(p.tipCenterX - g.x) <= g.w / 2 - 0.3 && abs(p.tipCenterY - physics.tipRadius - g.y) < 0.3
      val moverId = if (col.mover != null) col.id else null
      return Outcome(p.tipCenterX, p.tipCenterY, level.world.time, atGoal, idx, moverId, p.tipCenterX - col.ox, p.tipCenterY - col.oy)
    }
  }

  private class Search(val level: LoadedLevel, val physics: PhysicsConfig) {
    val nodes = ArrayList<Node>()
    var found = false
    val reachedIds = HashSet<String>()
    private var goalNode: Node? = null
    private val arrival = HashMap<Long, Double>()
    // best-first: always expand the highest standing spot found so far (reaches the goal sooner than breadth-first)
    private val queue = PriorityQueue<Node>(compareByDescending<Node> { it.spot.tipY }.thenBy { it.depth })
    private val js = JumpSearch(level, physics)
    val trials: Int get() = js.trials

    fun run() {
      val start = js.startSpot() ?: return
      val first = Node(start, null, 0.0, 0.0, 0, 0.0, -1L)
      nodes += first; queue.add(first)
      while (queue.isNotEmpty() && nodes.size < MAX_NODES && !found) {
        val n = queue.poll()
        if (n.key != -1L && (arrival[n.key] ?: n.spot.time) < n.spot.time - 1e-9) continue // a faster way to this spot exists
        for (l in js.hops(n.spot)) {
          reachedIds += l.solidId
          if (l.atGoal) {
            goalNode = Node(l.spot, n, l.lean, l.charge, n.depth + 1, l.launchTime, -2L)
            nodes += goalNode!!
            found = true
            return
          }
          val k = js.keyOf(l)
          val prev = arrival[k]
          // static levels: the first representative of a spot wins; with movers an earlier arrival dominates (it can wait)
          if (prev != null && (!level.world.hasMovers || prev <= l.spot.time + 1e-9)) continue
          arrival[k] = l.spot.time
          val node = Node(l.spot, n, l.lean, l.charge, n.depth + 1, l.launchTime, k)
          nodes += node; queue.add(node)
        }
      }
    }

    fun path(): List<JumpStep> {
      var n = goalNode ?: return emptyList()
      val out = ArrayList<JumpStep>()
      while (n.parent != null) {
        val p = n.parent!!
        out += JumpStep(p.spot.tipX, p.spot.tipY, n.lean, n.charge, n.spot.tipX, n.spot.tipY, n.launchTime)
        n = p
      }
      out.reverse()
      return out
    }
  }
}
