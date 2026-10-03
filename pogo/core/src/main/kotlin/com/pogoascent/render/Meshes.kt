package com.pogoascent.render

import kotlin.math.cos
import kotlin.math.sin

/** Indexed triangle mesh: positions (xyz) and normals (xyz) per vertex. */
class MeshData(val positions: FloatArray, val normals: FloatArray, val indices: ShortArray) {
  val vertexCount: Int get() = positions.size / 3
  val triangleCount: Int get() = indices.size / 3
}

/** Unit primitives centred on the origin; instances scale/rotate them. Low-poly on purpose (mobile). */
object Meshes {
  const val CUBE = 0
  const val CYLINDER = 1
  const val SPHERE = 2
  const val KINDS = 3

  /** Cube spanning -0.5..0.5 on all axes. */
  val cube: MeshData by lazy {
    val faces = arrayOf(
      floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f), floatArrayOf(1f, 0f, 0f),
      floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
    )
    val pos = FloatArray(24 * 3)
    val nor = FloatArray(24 * 3)
    val idx = ShortArray(36)
    for (f in faces.indices) {
      val n = faces[f]
      // two tangent axes
      val ax = if (n[0] != 0f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
      val bx = floatArrayOf(n[1] * ax[2] - n[2] * ax[1], n[2] * ax[0] - n[0] * ax[2], n[0] * ax[1] - n[1] * ax[0])
      val corners = arrayOf(floatArrayOf(-1f, -1f), floatArrayOf(1f, -1f), floatArrayOf(1f, 1f), floatArrayOf(-1f, 1f))
      for (c in 0 until 4) {
        val v = f * 4 + c
        for (k in 0 until 3) {
          pos[v * 3 + k] = 0.5f * (n[k] + corners[c][0] * ax[k] + corners[c][1] * bx[k])
          nor[v * 3 + k] = n[k]
        }
      }
      val b = (f * 4).toShort()
      val i = f * 6
      idx[i] = b; idx[i + 1] = (b + 1).toShort(); idx[i + 2] = (b + 2).toShort()
      idx[i + 3] = b; idx[i + 4] = (b + 2).toShort(); idx[i + 5] = (b + 3).toShort()
    }
    orientOutward(MeshData(pos, nor, idx))
  }

  /** Cylinder along Y, radius 0.5, height 1 (y -0.5..0.5), flat caps. */
  val cylinder: MeshData by lazy {
    val seg = 12
    val pos = ArrayList<Float>(); val nor = ArrayList<Float>(); val idx = ArrayList<Short>()
    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Short {
      pos += x; pos += y; pos += z; nor += nx; nor += ny; nor += nz
      return (pos.size / 3 - 1).toShort()
    }
    for (i in 0 until seg) {
      val a0 = (i.toDouble() / seg) * Math.PI * 2
      val a1 = ((i + 1).toDouble() / seg) * Math.PI * 2
      val c0 = cos(a0).toFloat(); val s0 = sin(a0).toFloat(); val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat()
      // side quad
      val q0 = v(0.5f * c0, -0.5f, 0.5f * s0, c0, 0f, s0)
      val q1 = v(0.5f * c1, -0.5f, 0.5f * s1, c1, 0f, s1)
      val q2 = v(0.5f * c1, 0.5f, 0.5f * s1, c1, 0f, s1)
      val q3 = v(0.5f * c0, 0.5f, 0.5f * s0, c0, 0f, s0)
      idx += q0; idx += q1; idx += q2; idx += q0; idx += q2; idx += q3
      // top / bottom caps
      val tc = v(0f, 0.5f, 0f, 0f, 1f, 0f); val t0 = v(0.5f * c0, 0.5f, 0.5f * s0, 0f, 1f, 0f); val t1 = v(0.5f * c1, 0.5f, 0.5f * s1, 0f, 1f, 0f)
      idx += tc; idx += t1; idx += t0
      val bc = v(0f, -0.5f, 0f, 0f, -1f, 0f); val b0 = v(0.5f * c0, -0.5f, 0.5f * s0, 0f, -1f, 0f); val b1 = v(0.5f * c1, -0.5f, 0.5f * s1, 0f, -1f, 0f)
      idx += bc; idx += b0; idx += b1
    }
    orientOutward(MeshData(pos.toFloatArray(), nor.toFloatArray(), idx.toShortArray()))
  }

  /** UV sphere, radius 0.5. */
  val sphere: MeshData by lazy {
    val rings = 7
    val seg = 12
    val pos = ArrayList<Float>(); val nor = ArrayList<Float>(); val idx = ArrayList<Short>()
    for (r in 0..rings) {
      val phi = Math.PI * r / rings
      for (s in 0..seg) {
        val th = 2 * Math.PI * s / seg
        val x = (sin(phi) * cos(th)).toFloat(); val y = cos(phi).toFloat(); val z = (sin(phi) * sin(th)).toFloat()
        pos += 0.5f * x; pos += 0.5f * y; pos += 0.5f * z; nor += x; nor += y; nor += z
      }
    }
    for (r in 0 until rings) for (s in 0 until seg) {
      val a = (r * (seg + 1) + s).toShort(); val b = (a + seg + 1).toShort()
      idx += a; idx += b; idx += (a + 1).toShort()
      idx += (a + 1).toShort(); idx += b; idx += (b + 1).toShort()
    }
    orientOutward(MeshData(pos.toFloatArray(), nor.toFloatArray(), idx.toShortArray()))
  }

  fun get(kind: Int): MeshData = when (kind) { CUBE -> cube; CYLINDER -> cylinder; else -> sphere }

  /** Flip any triangle whose winding disagrees with its vertex normals, so every mesh is counter-clockwise from outside. */
  private fun orientOutward(m: MeshData): MeshData {
    val idx = m.indices.copyOf()
    var i = 0
    while (i < idx.size) {
      val a = idx[i].toInt() * 3; val b = idx[i + 1].toInt() * 3; val c = idx[i + 2].toInt() * 3
      val ux = m.positions[b] - m.positions[a]; val uy = m.positions[b + 1] - m.positions[a + 1]; val uz = m.positions[b + 2] - m.positions[a + 2]
      val vx = m.positions[c] - m.positions[a]; val vy = m.positions[c + 1] - m.positions[a + 1]; val vz = m.positions[c + 2] - m.positions[a + 2]
      val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
      val dot = nx * m.normals[a] + ny * m.normals[a + 1] + nz * m.normals[a + 2]
      if (dot < 0f) { val t = idx[i + 1]; idx[i + 1] = idx[i + 2]; idx[i + 2] = t }
      i += 3
    }
    return MeshData(m.positions, m.normals, idx)
  }
}
