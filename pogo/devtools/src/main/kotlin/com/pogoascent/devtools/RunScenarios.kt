package com.pogoascent.devtools

import com.pogoascent.debug.PhysicsTestScene
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog

/** Runs the Physics Test Scene scenarios headlessly and prints the measurements (compare with analysis data once available). */
fun main() {
  val scene = PhysicsTestScene(SurfaceCatalog.load(), PhysicsConfig.load())
  println("Physics scenarios with the bundled PhysicsConfig (all grade-D design defaults)")
  for (k in ScenarioKind.values()) println(scene.runToCompletion(k))
}
