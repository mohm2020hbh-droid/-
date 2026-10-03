package com.pogoascent.app

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import com.pogoascent.levels.JumpStep
import kotlinx.serialization.Serializable

@Serializable
data class DemoStep(val lean: Double, val charge: Double, val fromTime: Double = 0.0)

@Serializable
data class DemoRouteFile(val levelId: String, val steps: List<DemoStep>)

/** The route the main-menu backdrop plays. Generated from the validator (`./gradlew :devtools:generateDemoRoute`) and verified by a test. */
object DemoRoute {
  fun path(levelId: String) = "data/demo_$levelId.json"

  fun load(levelId: String): List<JumpStep> {
    val f = GameJson.pretty.decodeFromString(DemoRouteFile.serializer(), Resources.readText(path(levelId)))
    return f.steps.map { JumpStep(0.0, 0.0, it.lean, it.charge, 0.0, 0.0, it.fromTime) }
  }
}
