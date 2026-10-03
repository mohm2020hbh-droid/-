package com.pogoascent.devtools

import com.pogoascent.debug.AutoPilot
import com.pogoascent.levels.GameSession
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelValidator
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.render.Palette
import com.pogoascent.render.SceneBuilder
import java.io.File
import javax.imageio.ImageIO

/** Renders a few frames of every level (start, mid-climb along the validated route, goal) to build/debug-frames/. */
fun main(args: Array<String>) {
  val root = File(args.getOrElse(0) { ".." })
  val outDir = File(root, "devtools/build/debug-frames").apply { mkdirs() }
  val only = args.drop(1).toSet()
  val physics = PhysicsConfig.load()
  val surfaces = SurfaceCatalog.load()
  val worlds = WorldCatalog.load()
  for (id in worlds.levelOrder.filter { only.isEmpty() || it in only }) {
    val data = LevelLoader.loadData(id)
    val report = LevelValidator.validate(data, surfaces, physics)
    val world = worlds.worldOfLevel(id) ?: worlds.worlds.first()
    val palette = Palette(world.palette)
    val loaded = LevelLoader.load(data, surfaces)
    val session = GameSession(loaded, physics)
    val renderer = DebugRenderer(SceneBuilder(loaded, palette), palette)
    fun shot(name: String) {
      val img = renderer.render(session, 960, 540, time = session.timeSec)
      ImageIO.write(img, "png", File(outDir, "${id}_$name.png"))
    }
    shot("start")
    val pilot = AutoPilot(session, report.path)
    var tick = 0
    val half = report.path.size / 2
    var shotMid = false
    val dt = physics.fixedDt
    val maxTicks = (physics.fixedTimestepHz * 400).toInt()
    while (!pilot.finished && session.phase != com.pogoascent.levels.SessionPhase.COMPLETE && tick < maxTicks) {
      val before = pilot.stepIndex
      pilot.drive()
      session.update(dt)
      if (System.getProperty("trace") != null && pilot.stepIndex != before && before < report.path.size) {
        val st = report.path[before]
        println("  step $before done: planned (%.2f, %.2f) actual tip (%.2f, %.2f) angle %.3f".format(st.toX, st.toY, session.player.tipCenterX, session.player.tipCenterY, session.player.angle))
      }
      tick++
      if (!shotMid && pilot.stepIndex >= half && session.player.grounded) { shot("mid"); shotMid = true }
      if (tick % (physics.fixedTimestepHz.toInt() * 2) == 0 && session.player.vy > 5) { /* airborne action frame */ }
    }
    shot("end")
    println("$id: pilot ${if (session.phase == com.pogoascent.levels.SessionPhase.COMPLETE) "completed" else "did NOT complete"} the level in %.1fs, %d jumps, %d falls, %d resets".format(session.timeSec, session.player.jumpCount, session.falls, session.resets))
  }
  println("frames in ${outDir.absolutePath}")
}
