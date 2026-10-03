package com.pogoascent.tools

import com.pogoascent.camera.CameraConfig
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.PhysicsParams
import java.io.File

/**
 * Renders PHYSICS_MASTER.md from the live code registry so the document can never drift from the code
 * (a unit test fails when the checked-in file differs). Regenerate with `./gradlew :core:generateDocs`.
 */
object PhysicsDocs {
  fun renderMarkdown(config: PhysicsConfig): String {
    val sb = StringBuilder()
    sb.append("# PHYSICS_MASTER\n\n")
    sb.append("> **Generated file – do not edit by hand.** Source of truth: `PhysicsParams.kt` (metadata) and\n")
    sb.append("> `core/src/main/resources/data/physics_config.json` (values). Regenerate: `./gradlew :core:generateDocs`.\n\n")
    sb.append("## Evidence statement (read this first)\n\n")
    sb.append("The task asked for values from a *Physics Master Table* built from Pogostuck analysis data. **That table, and every\n")
    sb.append("other analysis file, is absent from this repository** (see `DECISIONS.md` D-001). Therefore:\n\n")
    sb.append("* **Every value below is grade D** (unconfirmed design default). No value is grade A, B or C.\n")
    sb.append("* \"Source\" says where the number came from; none comes from a measurement. Nothing here is a claim\n")
    sb.append("  that the game matches Pogostuck.\n")
    sb.append("* All values live in one file (`physics_config.json`) and can be edited without touching movement code.\n")
    sb.append("  When real data is available: replace the value, change `source`/`confidence` in `PhysicsParams.kt`,\n")
    sb.append("  regenerate this file, re-tune, and re-run `:core:test` (level reachability is re-validated automatically).\n\n")
    sb.append("Grades: **A** game files / extracted data · **B** developer / documentation · **C** gameplay measurement ·\n")
    sb.append("**D** unconfirmed.\n\n")

    sb.append("## Parameters\n\n")
    sb.append("| Category | Parameter | Value | Unit | Source | Confidence | Editable | Notes |\n")
    sb.append("|---|---|---|---|---|---|---|---|\n")
    for (spec in PhysicsParams.all) {
      sb.append("| ").append(spec.category)
        .append(" | `").append(spec.key).append('`')
        .append(" | ").append(PhysicsParams.format(spec.read(config)))
        .append(" | ").append(spec.unit)
        .append(" | ").append(spec.source)
        .append(" | ").append(spec.confidence.name)
        .append(" | ").append(spec.editable)
        .append(" | ").append(spec.notes.replace("|", "\\|"))
        .append(" |\n")
    }

    sb.append("\n## Coverage of the categories required by the task\n\n")
    sb.append("| Required category | Parameters |\n|---|---|\n")
    for (cat in PhysicsParams.requiredCategories) {
      val keys = PhysicsParams.all.filter { it.category == cat }.joinToString(", ") { "`${it.key}`" }
      sb.append("| ").append(cat).append(" | ").append(keys).append(" |\n")
    }

    sb.append("\n## Derived quantities (computed, not stored)\n\n")
    sb.append("| Quantity | Formula | Value with current config |\n|---|---|---|\n")
    val apex = config.jumpPower * config.jumpPower / (2 * config.gravity)
    val apexMin = config.jumpPowerMin * config.jumpPowerMin / (2 * config.gravity)
    val flight = 2 * config.jumpPower / config.gravity
    sb.append("| Full-charge vertical apex height | v²/2g | ${PhysicsParams.format(Math.round(apex * 100) / 100.0)} m |\n")
    sb.append("| Tap (no charge) vertical apex height | v²/2g | ${PhysicsParams.format(Math.round(apexMin * 100) / 100.0)} m |\n")
    sb.append("| Full-charge flight time (same-height landing) | 2v/g | ${PhysicsParams.format(Math.round(flight * 100) / 100.0)} s |\n")
    sb.append("| Moment of inertia (mass 1) | inertiaFactor·tipOffset² | ${PhysicsParams.format(Math.round(config.inertia * 1000) / 1000.0)} |\n")
    sb.append("| Physics tick | 1 / fixedTimestepHz | ${PhysicsParams.format(Math.round(config.fixedDt * 1e6) / 1e3)} ms |\n")
    return sb.toString()
  }

  /** Entry point of the `generateDocs` Gradle task. args[0] = path of the `pogo` directory. */
  @JvmStatic
  fun main(args: Array<String>) {
    val root = File(args.getOrElse(0) { ".." })
    val config = PhysicsConfig()
    File(root, "core/src/main/resources/data/physics_config.json").apply {
      parentFile.mkdirs()
      writeText(PhysicsConfig.toJson(config) + "\n")
    }
    File(root, "core/src/main/resources/data/camera_config.json").writeText(CameraConfig.toJson(CameraConfig()) + "\n")
    File(root, "PHYSICS_MASTER.md").writeText(renderMarkdown(config))
    println("Wrote PHYSICS_MASTER.md, physics_config.json and camera_config.json")
  }
}
