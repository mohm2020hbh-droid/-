package com.pogoascent.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Column-major 4×4 float matrices (OpenGL convention). Functions write into caller-provided arrays – no allocation. */
object Mat4 {
  fun identity(m: FloatArray) {
    for (i in 0 until 16) m[i] = 0f
    m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
  }

  /** out = a · b (apply b first, then a). `out` may alias neither input. */
  fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
    for (c in 0 until 4) {
      for (r in 0 until 4) {
        var s = 0f
        for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
        out[c * 4 + r] = s
      }
    }
  }

  fun perspective(m: FloatArray, fovYDeg: Float, aspect: Float, near: Float, far: Float) {
    val f = 1f / tan(Math.toRadians(fovYDeg.toDouble()) / 2.0).toFloat()
    for (i in 0 until 16) m[i] = 0f
    m[0] = f / aspect
    m[5] = f
    m[10] = (far + near) / (near - far)
    m[11] = -1f
    m[14] = 2f * far * near / (near - far)
  }

  fun lookAt(m: FloatArray, ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float, ux: Float, uy: Float, uz: Float) {
    var fx = tx - ex; var fy = ty - ey; var fz = tz - ez
    val fl = sqrt(fx * fx + fy * fy + fz * fz)
    fx /= fl; fy /= fl; fz /= fl
    var sx = fy * uz - fz * uy; var sy = fz * ux - fx * uz; var sz = fx * uy - fy * ux
    val sl = sqrt(sx * sx + sy * sy + sz * sz)
    sx /= sl; sy /= sl; sz /= sl
    val vx = sy * fz - sz * fy; val vy = sz * fx - sx * fz; val vz = sx * fy - sy * fx
    m[0] = sx; m[4] = sy; m[8] = sz; m[12] = -(sx * ex + sy * ey + sz * ez)
    m[1] = vx; m[5] = vy; m[9] = vz; m[13] = -(vx * ex + vy * ey + vz * ez)
    m[2] = -fx; m[6] = -fy; m[10] = -fz; m[14] = fx * ex + fy * ey + fz * ez
    m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f
  }

  /** Model matrix: translate(tx,ty,tz) · rotateZ(rz) · scale(sx,sy,sz). */
  fun trs(m: FloatArray, tx: Float, ty: Float, tz: Float, rz: Float, sx: Float, sy: Float, sz: Float) {
    val c = cos(rz); val s = sin(rz)
    m[0] = c * sx; m[1] = s * sx; m[2] = 0f; m[3] = 0f
    m[4] = -s * sy; m[5] = c * sy; m[6] = 0f; m[7] = 0f
    m[8] = 0f; m[9] = 0f; m[10] = sz; m[11] = 0f
    m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
  }

  /** Transforms (x,y,z,1) by [m] and writes clip-space x,y,z,w into [out]. */
  fun transform(out: FloatArray, m: FloatArray, x: Float, y: Float, z: Float) {
    out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
    out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
    out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    out[3] = m[3] * x + m[7] * y + m[11] * z + m[15]
  }
}
