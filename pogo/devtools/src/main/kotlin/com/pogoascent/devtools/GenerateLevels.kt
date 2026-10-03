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
    val level: LevelData = LevelGen.generate(profile, surfaces, physics) { if (System.getProperty("verbose") != null) println(it) }
    File(outDir, "${profile.id}.json").writeText(LevelData.toJson(level) + "\n")
    println("  wrote ${profile.id}.json in %.1fs (%d solids)".format((System.nanoTime() - t0) / 1e9, level.allSolids().size))
  }
}
