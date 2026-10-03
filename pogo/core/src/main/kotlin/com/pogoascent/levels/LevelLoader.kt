package com.pogoascent.levels

import com.pogoascent.core.Resources
import com.pogoascent.physics.Collider
import com.pogoascent.physics.Mover
import com.pogoascent.physics.PhysicsWorld
import com.pogoascent.physics.Sensor
import com.pogoascent.physics.SensorKind
import com.pogoascent.physics.SurfaceCatalog

/** A level turned into runtime objects. [world] is mutable simulation state – create one per play session. */
class LoadedLevel(
  val data: LevelData,
  val world: PhysicsWorld,
  val colliders: List<Collider>,
  val goalCenterX: Double,
  val goalCenterY: Double,
) {
  val startX: Double get() = data.start.x
  val startY: Double get() = data.start.y

  /** Fraction of the climb: 0 at the start surface, 1 at the goal. */
  fun progressAt(y: Double): Double {
    val span = data.goal.y - data.start.y
    return if (span <= 0.0) 0.0 else ((y - data.start.y) / span).coerceIn(0.0, 1.0)
  }
}

object LevelLoader {
  fun levelPath(id: String) = "data/levels/$id.json"

  fun loadData(id: String): LevelData = LevelData.fromJson(Resources.readText(levelPath(id)))

  fun load(data: LevelData, surfaces: SurfaceCatalog): LoadedLevel {
    val colliders = ArrayList<Collider>()
    val seen = HashSet<String>()
    for (s in data.allSolids()) {
      require(seen.add(s.id)) { "Level '${data.id}': duplicate solid id '${s.id}'" }
      colliders += buildCollider(data.id, s, surfaces)
    }
    val g = data.goal
    // The goal zone sits ON the goal platform: g.y is the platform's top surface, the zone extends g.h above it.
    colliders += Collider.box("goal", g.x, g.y + g.h / 2, g.w, g.h, surfaces["goal"])
    val sensors = ArrayList<Sensor>()
    for (c in data.checkpoints) {
      sensors += Sensor(c.id, SensorKind.CHECKPOINT, c.x - c.w / 2, c.y - c.h / 2, c.x + c.w / 2, c.y + c.h / 2)
    }
    val world = PhysicsWorld(colliders, sensors, data.killY)
    return LoadedLevel(data, world, colliders, g.x, g.y)
  }

  fun load(id: String, surfaces: SurfaceCatalog): LoadedLevel = load(loadData(id), surfaces)

  private fun buildCollider(levelId: String, s: SolidDef, surfaces: SurfaceCatalog): Collider {
    val surface = surfaces.find(s.surface) ?: error("Level '$levelId' solid '${s.id}': unknown surface '${s.surface}'")
    val mover = s.mover?.let { Mover(it.ax, it.ay, it.period, it.phase) }
    return when (s.kind) {
      "box" -> Collider.box(s.id, s.x, s.y, s.w, s.h, surface, Math.toRadians(s.rot), s.oneWay, mover)
      "poly" -> {
        require(s.points.size >= 3 && s.points.all { it.size == 2 }) { "Level '$levelId' solid '${s.id}': poly needs >= 3 [x,y] points" }
        Collider(s.id, DoubleArray(s.points.size) { s.points[it][0] }, DoubleArray(s.points.size) { s.points[it][1] }, surface, s.oneWay, mover)
      }
      else -> error("Level '$levelId' solid '${s.id}': unknown kind '${s.kind}' (use box or poly)")
    }
  }
}
