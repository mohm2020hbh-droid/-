package com.pogoascent.devtools

import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelValidator
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val only = args.drop(1).toSet()
  val physics = PhysicsConfig.load()
  val surfaces = SurfaceCatalog.load()
  val ids = WorldCatalog.load().levelOrder.filter { only.isEmpty() || it in only }
  var failed = false
  for (id in ids) {
    val t0 = System.nanoTime()
    val report = LevelValidator.validate(LevelLoader.loadData(id), surfaces, physics)
    println(report.toString() + "  [%.1fs]".format((System.nanoTime() - t0) / 1e9))
    for (s in report.path) println("    jump from (%.1f, %.1f) lean %.1f charge %.2f -> (%.1f, %.1f)".format(s.fromX, s.fromY, s.lean, s.charge, s.toX, s.toY))
    if (System.getProperty("spots") != null) report.landingSpots.forEach { println("    spot (%.2f, %.2f)".format(it[0], it[1])) }
    if (!report.ok) failed = true
  }
  if (failed) exitProcess(1)
}
