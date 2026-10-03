package com.pogoascent.devtools

import com.pogoascent.levels.LevelData
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import java.io.File

/**
 * Authoring tool: (re)generates the bundled level JSON files from the profiles below, keeping only geometry that the
 * real-physics validator can robustly reach. Run: ./gradlew :devtools:generateLevels [-Plevels=level_01,level_02]
 * The JSON is the source of truth afterwards – edit it by hand freely, then run validateLevels.
 */
val PROFILES: List<GenProfile> = listOf(
  GenProfile(
    id = "level_01", name = "First Steps", world = "world_1", theme = "meadow", difficulty = 1, seed = 11, platforms = 15,
    riseMin = 2.2, riseMax = 3.2, gapMin = 3.0, gapMax = 4.5, widthMin = 7.0, widthMax = 9.0,
    surfaces = mapOf("stone" to 1.0), checkpointEvery = 5, parTimeSec = 120.0,
    notes = "Teaches: lean to aim, hold to charge, release to launch. Wide stone ledges, rises 2.2-3.2 m, checkpoints at 1/3 and 2/3.",
    decorKinds = listOf("cloud"),
  ),
  GenProfile(
    id = "level_02", name = "Zigzag Ridge", world = "world_1", theme = "meadow", difficulty = 2, seed = 22, platforms = 18,
    riseMin = 2.4, riseMax = 3.4, gapMin = 3.4, gapMax = 5.0, widthMin = 5.5, widthMax = 8.0,
    surfaces = mapOf("stone" to 0.6, "wood" to 0.4), oneWayChance = 0.15, checkpointEvery = 6, parTimeSec = 150.0,
    notes = "Teaches precision: narrower ledges, longer gaps, and one-way wooden platforms you can jump up through.",
    decorKinds = listOf("cloud"),
  ),
  GenProfile(
    id = "level_03", name = "Bounce Garden", world = "world_1", theme = "meadow", difficulty = 3, seed = 33, platforms = 20,
    riseMin = 2.4, riseMax = 3.4, gapMin = 3.6, gapMax = 5.2, widthMin = 4.8, widthMax = 7.0,
    surfaces = mapOf("stone" to 0.6, "wood" to 0.4), bounceChance = 0.12, movingChance = 0.12, ceilingHazardChance = 0.15,
    checkpointEvery = 6, parTimeSec = 200.0,
    notes = "Introduces bounce pads (they throw you upward), sliding moving platforms and spikes hanging under ledges.",
    decorKinds = listOf("cloud"),
  ),
  GenProfile(
    id = "level_04", name = "Frozen Steps", world = "world_2", theme = "ice", difficulty = 4, seed = 44, platforms = 20,
    riseMin = 2.4, riseMax = 3.2, gapMin = 3.4, gapMax = 4.8, widthMin = 7.5, widthMax = 10.0,
    surfaces = mapOf("ice" to 0.35, "stone" to 0.45, "wood" to 0.2), movingChance = 0.0, checkpointEvery = 6, parTimeSec = 240.0,
    notes = "Ice keeps you sliding after a landing – wait for the stick to settle or use the slide to cross gaps. Momentum and timing.",
    decorKinds = listOf("crystal", "cloud"),
  ),
  GenProfile(
    id = "level_05", name = "Crystal Depths", world = "world_3", theme = "cave", difficulty = 6, seed = 55, platforms = 22,
    riseMin = 2.6, riseMax = 3.6, gapMin = 3.8, gapMax = 5.4, widthMin = 4.6, widthMax = 6.6,
    surfaces = mapOf("stone" to 0.5, "sticky" to 0.25, "boost_pad" to 0.1, "ice" to 0.15), bounceChance = 0.12, movingChance = 0.1,
    ceilingHazardChance = 0.2, checkpointEvery = 6, parTimeSec = 300.0,
    notes = "Complex surfaces: sticky patches stop you dead, boost pads add launch speed, bounce pads and spikes punish sloppy landings. Spin for a Boost to shortcut.",
    decorKinds = listOf("crystal", "pillar"),
  ),
  GenProfile(
    id = "level_06", name = "Storm Spire", world = "world_4", theme = "storm", difficulty = 8, seed = 66, platforms = 24,
    riseMin = 2.8, riseMax = 3.6, gapMin = 4.0, gapMax = 5.6, widthMin = 4.2, widthMax = 5.8,
    surfaces = mapOf("stone" to 0.6, "ice" to 0.2, "sticky" to 0.2), oneWayChance = 0.1, movingChance = 0.25, bounceChance = 0.1,
    ceilingHazardChance = 0.3, checkpointEvery = 6, parTimeSec = 360.0,
    notes = "High-risk finale of the starter set: narrow ledges, many moving platforms and spikes under the route.",
    decorKinds = listOf("pillar", "cloud"),
  ),
)

fun main(args: Array<String>) {
  val root = File(args.getOrElse(0) { ".." })
  val only = args.drop(1).toSet()
  val physics = PhysicsConfig.load()
  val surfaces = SurfaceCatalog.load()
  val outDir = File(root, "core/src/main/resources/data/levels").apply { mkdirs() }
  for (profile in PROFILES.filter { only.isEmpty() || it.id in only }) {
    println("generating ${profile.id} (${profile.name})")
    val t0 = System.nanoTime()
    try {
      val level: LevelData = LevelGen.generate(profile, surfaces, physics) { if (System.getProperty("verbose") != null) println(it) }
      File(outDir, "${profile.id}.json").writeText(LevelData.toJson(level) + "\n")
      println("  wrote ${profile.id}.json in %.1fs (%d solids)".format((System.nanoTime() - t0) / 1e9, level.allSolids().size))
    } catch (e: IllegalStateException) {
      println("  FAILED ${profile.id}: ${e.message}")
    }
  }
}
