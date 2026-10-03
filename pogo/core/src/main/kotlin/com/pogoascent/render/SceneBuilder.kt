package com.pogoascent.render

import com.pogoascent.levels.DecorDef
import com.pogoascent.levels.LoadedLevel
import com.pogoascent.physics.Collider

/**
 * Turns a [LoadedLevel] + [Palette] into renderable data: one merged static mesh for all fixed geometry and a static
 * batch of scenery. Dynamic things (movers, rider, flags, particles) are added per frame by [DynamicScene].
 */
class SceneBuilder(val level: LoadedLevel, val palette: Palette) {
  val staticMesh: StaticMesh = buildStaticMesh()
  val scenery: RenderBatches = buildScenery()

  private fun buildStaticMesh(): StaticMesh {
    val verts = ArrayList<Float>(4096)
    val idx = ArrayList<Short>(4096)
    val zFront = DEPTH / 2f
    val zBack = -DEPTH / 2f

    for (c in level.colliders) {
      if (c.mover != null) continue // movers are drawn as instances
      val base = palette.role(c.surface.colorRole)
      val v = 0.94f + 0.12f * hash01(c.id)
      val r = (base[0] * v).coerceIn(0f, 1f); val g = (base[1] * v).coerceIn(0f, 1f); val b = (base[2] * v).coerceIn(0f, 1f)

      fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, lighten: Float): Short {
        verts += x; verts += y; verts += z; verts += nx; verts += ny; verts += nz
        verts += r + (1f - r) * lighten; verts += g + (1f - g) * lighten; verts += b + (1f - b) * lighten
        return (verts.size / StaticMesh.FLOATS_PER_VERTEX - 1).toShort()
      }

      // front face: triangle fan (convex polygon, CCW)
      val first = vertex(c.worldX(0).toFloat(), c.worldY(0).toFloat(), zFront, 0f, 0f, 1f, 0f)
      var prev = vertex(c.worldX(1).toFloat(), c.worldY(1).toFloat(), zFront, 0f, 0f, 1f, 0f)
      for (i in 2 until c.n) {
        val cur = vertex(c.worldX(i).toFloat(), c.worldY(i).toFloat(), zFront, 0f, 0f, 1f, 0f)
        idx += first; idx += prev; idx += cur
        prev = cur
      }
      // side walls (outward normals); top-facing walls are lightened (grass-top / frost-top look)
      for (i in 0 until c.n) {
        val j = (i + 1) % c.n
        val x0 = c.worldX(i).toFloat(); val y0 = c.worldY(i).toFloat()
        val x1 = c.worldX(j).toFloat(); val y1 = c.worldY(j).toFloat()
        val ex = x1 - x0; val ey = y1 - y0
        val len = kotlin.math.sqrt(ex * ex + ey * ey)
        val nx = ey / len; val ny = -ex / len
        val lighten = if (ny > 0.6f) 0.22f else 0f
        val a = vertex(x0, y0, zFront, nx, ny, 0f, lighten)
        val bV = vertex(x1, y1, zFront, nx, ny, 0f, lighten)
        val cV = vertex(x1, y1, zBack, nx, ny, 0f, lighten)
        val dV = vertex(x0, y0, zBack, nx, ny, 0f, lighten)
        idx += a; idx += dV; idx += cV
        idx += a; idx += cV; idx += bV
      }
    }
    require(verts.size / StaticMesh.FLOATS_PER_VERTEX < 65535) { "static level mesh exceeds 65535 vertices" }
    return StaticMesh(verts.toFloatArray(), idx.toShortArray())
  }

  private fun buildScenery(): RenderBatches {
    val out = RenderBatches(512, 256, 512)
    for (d in level.data.decor) addDecor(out, d)
    // goal beacon: a pole with a flag above the goal platform
    val g = level.data.goal
    val gold = palette.role("goal")
    out.cylinder.add(g.x.toDouble(), g.y + 2.2, 0.0, 0.0, 0.18, 4.4, 0.18, palette.role("wall"))
    out.cube.add(g.x + 0.9, g.y + 3.7, 0.0, 0.0, 1.6, 1.0, 0.1, gold)
    return out
  }

  private fun addDecor(out: RenderBatches, d: DecorDef) {
    val col = palette.role(d.role)
    val dark = floatArrayOf(col[0] * 0.8f, col[1] * 0.8f, col[2] * 0.8f)
    when (d.kind) {
      "cloud" -> {
        val white = floatArrayOf(0.97f, 0.98f, 1f)
        out.sphere.add(d.x, d.y, d.z, 0.0, d.w, d.h, d.w * 0.6, white, 0.92f)
        out.sphere.add(d.x - d.w * 0.32, d.y - d.h * 0.12, d.z, 0.0, d.w * 0.6, d.h * 0.8, d.w * 0.5, white, 0.92f)
        out.sphere.add(d.x + d.w * 0.34, d.y - d.h * 0.1, d.z, 0.0, d.w * 0.65, d.h * 0.85, d.w * 0.5, white, 0.92f)
      }
      "tree" -> {
        val trunk = floatArrayOf(0.45f, 0.3f, 0.18f)
        val leaf = palette.role("ground")
        out.cylinder.add(d.x, d.y + d.h * 0.25, d.z, 0.0, d.w * 0.3, d.h * 0.5, d.w * 0.3, trunk)
        out.sphere.add(d.x, d.y + d.h * 0.7, d.z, 0.0, d.w * 1.6, d.h * 0.8, d.w * 1.6, leaf)
      }
      "pillar" -> out.cube.add(d.x, d.y + d.h / 2, d.z, 0.0, d.w, d.h, d.w, dark)
      "crystal" -> {
        out.cube.add(d.x, d.y + d.h / 2, d.z, 0.5, d.w, d.h, d.w * 0.6, col)
        out.cube.add(d.x + d.w * 0.6, d.y + d.h * 0.3, d.z + 0.5, -0.35, d.w * 0.6, d.h * 0.6, d.w * 0.4, dark)
      }
      else -> out.cube.add(d.x, d.y + d.h / 2, d.z, 0.0, d.w, d.h, d.w, col)
    }
  }

  companion object {
    /** Z extent of the extruded gameplay plane (metres). */
    const val DEPTH = 2.4f

    fun hash01(s: String): Float {
      var h = 1125899906842597L
      for (ch in s) h = 31 * h + ch.code
      return ((h ushr 8) and 0xFFFF).toFloat() / 65535f
    }
  }
}
