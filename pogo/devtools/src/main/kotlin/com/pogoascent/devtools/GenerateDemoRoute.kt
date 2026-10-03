package com.pogoascent.devtools

import com.pogoascent.app.DemoRoute
import com.pogoascent.app.DemoRouteFile
import com.pogoascent.app.DemoStep
import com.pogoascent.core.GameJson
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelValidator
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import java.io.File

/** Writes the autopilot route the main-menu backdrop plays (re-run after changing physics or the level). */
fun main(args: Array<String>) {
  val root = File(args.getOrElse(0) { ".." })
  val levelId = args.getOrElse(1) { "level_01" }
  val report = LevelValidator.validate(LevelLoader.loadData(levelId), SurfaceCatalog.load(), PhysicsConfig.load())
  check(report.ok && report.path.isNotEmpty()) { "cannot build a demo route: $report" }
  val file = DemoRouteFile(levelId, report.path.map { DemoStep(it.lean, it.charge) })
  File(root, "core/src/main/resources/${DemoRoute.path(levelId)}").writeText(GameJson.pretty.encodeToString(DemoRouteFile.serializer(), file) + "\n")
  println("demo route for $levelId: ${file.steps.size} jumps")
}
