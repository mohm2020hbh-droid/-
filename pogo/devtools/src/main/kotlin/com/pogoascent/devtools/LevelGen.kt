package com.pogoascent.devtools

import com.pogoascent.levels.CheckpointDef
import com.pogoascent.levels.DecorDef
import com.pogoascent.levels.GoalDef
import com.pogoascent.levels.LevelData
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelValidator
import com.pogoascent.levels.MoverDef
import com.pogoascent.levels.PointDef
import com.pogoascent.levels.SolidDef
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import java.util.Random

/** Recipe for one generated level. Every number is a design choice, not a measurement. */
class GenProfile(
  val id: String,
  val name: String,
  val world: String,
  val theme: String,
  val difficulty: Int,
  val seed: Long,
  val platforms: Int,
  val riseMin: Double, val riseMax: Double,
  val gapMin: Double, val gapMax: Double,
  val widthMin: Double, val widthMax: Double,
  /** surface id → weight for ordinary platforms. */
  val surfaces: Map<String, Double> = mapOf("stone" to 1.0),
  val oneWayChance: Double = 0.0,
  val movingChance: Double = 0.0,
  val bounceChance: Double = 0.0,
  val ceilingHazardChance: Double = 0.0,
  val checkpointEvery: Int = 5,
  val parTimeSec: Double = 120.0,
  val notes: String = "",
  val decorKinds: List<String> = listOf("cloud"),
  val wallX: Double = 14.5,
  /** Two consecutive rises must sum to at least this, so the platform two steps up never forms a low ceiling over the launch zone. */
  val minPairRise: Double = 5.2,
)

/**
 * Constructive level generator: each new platform is only kept if the real-physics validator can robustly reach it
 * from the start through the geometry placed so far. The output is plain LevelData (JSON) that designers then edit by hand.
 */
object LevelGen {
  fun generate(p: GenProfile, surfaces: SurfaceCatalog, physics: PhysicsConfig, log: (String) -> Unit = {}): LevelData {
    val rnd = Random(p.seed)
    var solids: List<SolidDef> = listOf(SolidDef("ground", x = 0.0, y = -1.0, w = 28.0, h = 2.0, surface = "stone"))
    var movers: List<SolidDef> = emptyList()
    var bounces: List<SolidDef> = emptyList()
    var hazards: List<SolidDef> = emptyList()
    var curLeft = -14.0
    var curRight = 14.0
    var curTop = 0.0
    var prevRise = 3.0
    var dir = 1
    var placed = 0
    var attempts = 0
    var top: List<DoubleArray> = emptyList() // [left,right,top] per placed platform (for checkpoints)

    fun level(goalX: Double, goalY: Double, goalW: Double, extra: SolidDef?, extraList: String, hz: List<SolidDef> = hazards): LevelData {
      val plats = solids + (if (extraList == "p" && extra != null) listOf(extra) else emptyList())
      val mv = movers + (if (extraList == "m" && extra != null) listOf(extra) else emptyList())
      val bn = bounces + (if (extraList == "b" && extra != null) listOf(extra) else emptyList())
      return LevelData(
        id = p.id, name = p.name, world = p.world, theme = p.theme, difficulty = p.difficulty,
        parTimeSec = p.parTimeSec, start = PointDef(0.0, 0.0), goal = GoalDef(goalX, goalY, goalW),
        killY = -12.0, platforms = plats, hazards = hz, movers = mv, bounceObjects = bn,
        obstacles = listOf(
          SolidDef("wall_l", x = -p.wallX - 1.5, y = (goalY + 6) / 2, w = 3.0, h = goalY + 14, surface = "wall"),
          SolidDef("wall_r", x = p.wallX + 1.5, y = (goalY + 6) / 2, w = 3.0, h = goalY + 14, surface = "wall"),
        ),
      )
    }

    /** Robust landings on [id] reachable in one jump from [frontier], widened by hops along the new platform itself. */
    fun landOn(trial: LevelData, id: String, frontier: List<LevelValidator.Spot>): List<LevelValidator.Spot> {
      val lvl = LevelLoader.load(trial, surfaces)
      val js = LevelValidator.JumpSearch(lvl, physics)
      val seen = HashSet<Long>()
      val spots = ArrayList<LevelValidator.Spot>()
      for (f in frontier) for (l in js.hops(f)) if (l.solidId == id && seen.add(js.keyOf(l))) spots += l.spot
      if (spots.isEmpty()) return spots
      var i = 0
      while (i < spots.size && spots.size < 10) {
        for (l in js.hops(spots[i])) if (l.solidId == id && seen.add(js.keyOf(l))) spots += l.spot
        i++
      }
      return spots
    }

    val base = level(0.0, 1.0, 1.0, null, "")
    val startSpot = checkNotNull(LevelValidator.JumpSearch(LevelLoader.load(base, surfaces), physics).startSpot()) { "start is not on the ground" }
    var frontier: List<LevelValidator.Spot> = listOf(startSpot)

    while (placed < p.platforms && attempts < p.platforms * 160) {
      attempts++
      val rise = (p.riseMin + rnd.nextDouble() * (p.riseMax - p.riseMin)).coerceAtLeast(p.minPairRise - prevRise)
      val gap = p.gapMin + rnd.nextDouble() * (p.gapMax - p.gapMin)
      val width = p.widthMin + rnd.nextDouble() * (p.widthMax - p.widthMin)
      var d = if (placed > 0 && rnd.nextDouble() < 0.25) -dir else dir
      var left: Double
      var right: Double
      if (placed == 0) {
        d = if (rnd.nextBoolean()) 1 else -1
        left = if (d > 0) 3.0 else -3.0 - width
        right = left + width
      } else {
        if (d > 0) { left = curRight + gap; right = left + width } else { right = curLeft - gap; left = right - width }
        if (right > p.wallX - 0.5 || left < -p.wallX + 0.5) {
          d = -d
          if (d > 0) { left = curRight + gap; right = left + width } else { right = curLeft - gap; left = right - width }
        }
        if (right > p.wallX - 0.5 || left < -p.wallX + 0.5) continue
      }
      val newTop = Math.round((curTop + rise) * 10) / 10.0
      val cx = (left + right) / 2
      val id = "p${placed + 1}"

      val roll = rnd.nextDouble()
      var kind = "p"
      val solid: SolidDef
      if (roll < p.movingChance && placed > 1) {
        kind = "m"
        solid = SolidDef(id, x = cx, y = newTop - 0.5, w = minOf(width, 5.0), h = 1.0, surface = "moving", mover = MoverDef(ax = 2.5, ay = 0.0, period = 6.0 + rnd.nextDouble() * 2, phase = rnd.nextDouble() * 6.28))
      } else if (roll < p.movingChance + p.bounceChance && placed > 1) {
        kind = "b"
        solid = SolidDef(id, x = cx, y = newTop - 0.3, w = minOf(width, 4.0), h = 0.6, surface = "bounce")
      } else {
        val oneWay = rnd.nextDouble() < p.oneWayChance
        solid = SolidDef(id, x = cx, y = newTop - 0.5, w = width, h = 1.0, surface = if (oneWay) "wood" else pick(p.surfaces, rnd), oneWay = oneWay)
      }

      val trial = level(cx, newTop, right - left, solid, kind)
      val spots = landOn(trial, id, frontier)
      if (spots.isEmpty()) continue
      // cheap filter passed – now prove the WHOLE route from the start still works with this platform in place
      if (id !in LevelValidator.validate(trial, surfaces, physics).reachedSolids) { log("  reject $id: breaks the route"); continue }

      when (kind) { "m" -> movers = movers + solid; "b" -> bounces = bounces + solid; else -> solids = solids + solid }
      if (kind == "p" && !solid.oneWay && rnd.nextDouble() < p.ceilingHazardChance) {
        val hz = SolidDef("spikes_$id", x = cx, y = newTop - 1.25, w = minOf(width * 0.4, 3.0), h = 0.5, surface = "hazard")
        val withHz = hazards + hz
        val lvlHz = level(cx, newTop, right - left, null, "", withHz)
        if (landOn(lvlHz, id, frontier).isNotEmpty() && id in LevelValidator.validate(lvlHz, surfaces, physics).reachedSolids) { hazards = withHz; log("  spikes under $id") }
      }
      top = top + doubleArrayOf(left, right, newTop)
      prevRise = newTop - curTop
      curLeft = left; curRight = right; curTop = newTop; dir = if (cx > 0) -1 else 1
      frontier = spots
      placed++
      log("  placed $id kind=$kind top=$newTop x=[%.1f, %.1f] (%d spots, attempt %d)".format(left, right, spots.size, attempts))

    }
    check(placed == p.platforms) { "generator only placed $placed/${p.platforms} platforms for ${p.id}" }

    // goal platform: a wide ledge on the open side of the last platform, validated like any other
    var goalLevel: LevelData? = null
    for (a in 0 until 60) {
      val w = 9.0
      val rise = (p.riseMin + rnd.nextDouble() * (p.riseMax - p.riseMin)).coerceAtLeast(p.minPairRise - prevRise)
      val toRight = curRight + p.gapMin + 0.5 + w < p.wallX - 0.5 && (curLeft < -p.wallX + 6 || rnd.nextBoolean())
      val left = if (toRight) curRight + p.gapMin + rnd.nextDouble() * (p.gapMax - p.gapMin) else curLeft - p.gapMin - rnd.nextDouble() * (p.gapMax - p.gapMin) - w
      if (left < -p.wallX + 0.5 || left + w > p.wallX - 0.5) continue
      val gTop = Math.round((curTop + rise) * 10) / 10.0
      val goal = SolidDef("goalbase", x = left + w / 2, y = gTop - 0.5, w = w, h = 1.0, surface = "stone")
      val trial = level(left + w / 2, gTop, w, goal, "p")
      if (landOn(trial, "goalbase", frontier).isEmpty()) continue
      val rep = LevelValidator.validate(trial, surfaces, physics)
      if (rep.ok && rep.goalReachable == true) { goalLevel = trial; log("  goal placed at top=$gTop"); break }
      log("  goal candidate failed the full validation")
    }
    val finished = checkNotNull(goalLevel) { "no valid goal placement for ${p.id}" }

    val cps = ArrayList<CheckpointDef>()
    var i = p.checkpointEvery
    while (i < top.size - 1) {
      val t = top[i - 1]
      val x = (t[0] + t[1]) / 2
      cps += CheckpointDef("cp${cps.size + 1}", x = x, y = t[2] + 1.5, w = minOf(6.0, t[1] - t[0]), h = 3.0, respawnX = x, respawnY = t[2])
      i += p.checkpointEvery
    }
    val decor = ArrayList<DecorDef>()
    val height = finished.goal.y
    var y = 4.0
    var k = 0
    while (y < height + 25) {
      val kind = p.decorKinds[k % p.decorKinds.size]
      val side = if (k % 2 == 0) -1 else 1
      val x = side * (8 + rnd.nextDouble() * 14)
      when (kind) {
        "cloud" -> decor += DecorDef("cloud", x, y + 4, -14.0 - rnd.nextDouble() * 6, 6.0 + rnd.nextDouble() * 3, 2.2, "accent")
        "crystal" -> decor += DecorDef("crystal", x, y, -7.0 - rnd.nextDouble() * 4, 1.6 + rnd.nextDouble(), 3.0 + rnd.nextDouble() * 3, "special")
        "pillar" -> decor += DecorDef("pillar", x, y - 3, -9.0 - rnd.nextDouble() * 5, 2.0, 8.0 + rnd.nextDouble() * 4, "wall")
        else -> decor += DecorDef("box", x, y, -10.0, 3.0, 3.0, "accent")
      }
      y += 7.0 + rnd.nextDouble() * 3.0
      k++
    }
    if (p.theme == "meadow") for (t in 0 until 7) decor += DecorDef("tree", -20.0 + t * 7.0, 0.0, -8.0 - (t % 2) * 3.0, 2.0, 4.0 + (t % 3), "ground")

    return finished.copy(checkpoints = cps, decor = decor, notes = p.notes)
  }

  private fun pick(weights: Map<String, Double>, rnd: Random): String {
    val total = weights.values.sum()
    var r = rnd.nextDouble() * total
    for ((k, w) in weights) { r -= w; if (r <= 0) return k }
    return weights.keys.first()
  }
}
