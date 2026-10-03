package com.pogoascent.devtools

import com.pogoascent.levels.GameSession
import com.pogoascent.render.CharacterStyle
import com.pogoascent.render.DynamicScene
import com.pogoascent.render.InstanceBatch
import com.pogoascent.render.Mat4
import com.pogoascent.render.MeshData
import com.pogoascent.render.Meshes
import com.pogoascent.render.Palette
import com.pogoascent.render.SceneBuilder
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.sqrt

/**
 * CPU painter's-algorithm renderer that consumes exactly the data the OpenGL renderer consumes (static mesh, instance
 * batches, camera matrices). It exists so the camera maths, scene content and winding can be checked headlessly.
 */
class DebugRenderer(private val builder: SceneBuilder, private val palette: Palette) {
  private class Tri(val x: FloatArray, val y: FloatArray, val depth: Float, val rgb: Int)

  private val dyn = DynamicScene(builder)
  private val view = FloatArray(16)
  private val proj = FloatArray(16)
  private val vp = FloatArray(16)
  private val clip = FloatArray(4)
  private val light = floatArrayOf(-0.35f, 0.75f, 0.55f).let { v -> val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / l, v[1] / l, v[2] / l) }

  fun render(session: GameSession, w: Int, h: Int, style: CharacterStyle = CharacterStyle(), time: Double = 0.0): BufferedImage {
    val aspect = w.toFloat() / h
    session.camera.viewMatrix(view)
    session.camera.projectionMatrix(proj, aspect)
    Mat4.multiply(vp, proj, view)
    dyn.build(session, style, time, 1.0 / 60)

    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.paint = GradientPaint(0f, 0f, awt(palette.skyTop), 0f, h.toFloat(), awt(palette.skyBottom))
    g.fillRect(0, 0, w, h)

    val tris = ArrayList<Tri>(8192)
    addStatic(tris, w, h)
    addBatch(tris, builder.scenery.cube, Meshes.cube, w, h)
    addBatch(tris, builder.scenery.cylinder, Meshes.cylinder, w, h)
    addBatch(tris, builder.scenery.sphere, Meshes.sphere, w, h)
    addBatch(tris, dyn.batches.cube, Meshes.cube, w, h)
    addBatch(tris, dyn.batches.cylinder, Meshes.cylinder, w, h)
    addBatch(tris, dyn.batches.sphere, Meshes.sphere, w, h)
    tris.sortByDescending { it.depth }
    for (t in tris) {
      g.color = Color(t.rgb)
      g.fillPolygon(intArrayOf(t.x[0].toInt(), t.x[1].toInt(), t.x[2].toInt()), intArrayOf(t.y[0].toInt(), t.y[1].toInt(), t.y[2].toInt()), 3)
    }
    g.dispose()
    return img
  }

  private fun project(x: Float, y: Float, z: Float, w: Int, h: Int, outX: FloatArray, outY: FloatArray, i: Int): Float {
    Mat4.transform(clip, vp, x, y, z)
    if (clip[3] <= 0.01f) return -1f
    outX[i] = (clip[0] / clip[3] * 0.5f + 0.5f) * w
    outY[i] = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * h
    return clip[3]
  }

  private fun shade(nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float, a: Float, depth: Float): Int {
    val d = (nx * light[0] + ny * light[1] + nz * light[2]).coerceAtLeast(0f)
    val k = 0.42f + 0.58f * d
    val fogT = ((depth - 25f) / 160f).coerceIn(0f, 0.6f)
    fun mix(c: Float, f: Float): Int {
      val lit = c * k
      val withFog = lit + (f - lit) * fogT
      val withAlpha = withFog * a + 0.6f * (1f - a) * 0f + withFog * 0f
      return (withAlpha.coerceIn(0f, 1f) * 255f).toInt()
    }
    return (mix(r, palette.fog[0]) shl 16) or (mix(g, palette.fog[1]) shl 8) or mix(b, palette.fog[2])
  }

  private fun addStatic(out: MutableList<Tri>, w: Int, h: Int) {
    val m = builder.staticMesh
    val v = m.vertices
    val s = com.pogoascent.render.StaticMesh.FLOATS_PER_VERTEX
    val xs = FloatArray(3); val ys = FloatArray(3)
    var i = 0
    while (i < m.indices.size) {
      var depth = 0f; var ok = true
      for (k in 0 until 3) {
        val o = m.indices[i + k].toInt() * s
        val d = project(v[o], v[o + 1], v[o + 2], w, h, xs, ys, k)
        if (d < 0f) ok = false
        depth += d / 3f
      }
      if (ok) {
        val o = m.indices[i].toInt() * s
        out += Tri(xs.copyOf(), ys.copyOf(), depth, shade(v[o + 3], v[o + 4], v[o + 5], v[o + 6], v[o + 7], v[o + 8], 1f, depth))
      }
      i += 3
    }
  }

  private fun addBatch(out: MutableList<Tri>, batch: InstanceBatch, mesh: MeshData, w: Int, h: Int) {
    val xs = FloatArray(3); val ys = FloatArray(3)
    for (n in 0 until batch.count) {
      val o = n * InstanceBatch.STRIDE
      val d = batch.data
      val alpha = d[o + 19]
      var i = 0
      while (i < mesh.indices.size) {
        var depth = 0f; var ok = true
        var wx0 = 0f; var wy0 = 0f; var wz0 = 0f
        for (k in 0 until 3) {
          val vi = mesh.indices[i + k].toInt() * 3
          val px = mesh.positions[vi]; val py = mesh.positions[vi + 1]; val pz = mesh.positions[vi + 2]
          val x = d[o] * px + d[o + 4] * py + d[o + 8] * pz + d[o + 12]
          val y = d[o + 1] * px + d[o + 5] * py + d[o + 9] * pz + d[o + 13]
          val z = d[o + 2] * px + d[o + 6] * py + d[o + 10] * pz + d[o + 14]
          if (k == 0) { wx0 = x; wy0 = y; wz0 = z }
          val dep = project(x, y, z, w, h, xs, ys, k)
          if (dep < 0f) ok = false
          depth += dep / 3f
        }
        if (ok) {
          val vi = mesh.indices[i].toInt() * 3
          val nxm = mesh.normals[vi]; val nym = mesh.normals[vi + 1]; val nzm = mesh.normals[vi + 2]
          var nx = d[o] * nxm + d[o + 4] * nym + d[o + 8] * nzm
          var ny = d[o + 1] * nxm + d[o + 5] * nym + d[o + 9] * nzm
          var nz = d[o + 2] * nxm + d[o + 6] * nym + d[o + 10] * nzm
          val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
          nx /= l; ny /= l; nz /= l
          @Suppress("UNUSED_VARIABLE") val unused = wx0 + wy0 + wz0
          // facing test: skip triangles facing away from the camera (uses the screen-space signed area)
          val area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
          if (area < 0f) {
            val rgb = shade(nx, ny, nz, d[o + 16], d[o + 17], d[o + 18], alpha, depth)
            out += Tri(xs.copyOf(), ys.copyOf(), depth, blend(rgb, alpha, xs, ys))
          }
        }
        i += 3
      }
    }
  }

  private fun blend(rgb: Int, alpha: Float, @Suppress("UNUSED_PARAMETER") xs: FloatArray, @Suppress("UNUSED_PARAMETER") ys: FloatArray): Int {
    if (alpha >= 0.99f) return rgb
    val sky = awt(palette.skyBottom)
    val r = (((rgb shr 16) and 0xFF) * alpha + sky.red * (1 - alpha)).toInt()
    val g = (((rgb shr 8) and 0xFF) * alpha + sky.green * (1 - alpha)).toInt()
    val b = ((rgb and 0xFF) * alpha + sky.blue * (1 - alpha)).toInt()
    return (r shl 16) or (g shl 8) or b
  }

  private fun awt(c: FloatArray) = Color(c[0], c[1], c[2])
}
