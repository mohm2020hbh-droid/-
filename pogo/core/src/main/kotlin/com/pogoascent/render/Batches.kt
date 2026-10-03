package com.pogoascent.render

/**
 * Pre-allocated per-mesh instance buffers. Each instance = 16 floats (column-major model matrix) + 4 floats (RGBA).
 * Filled by the scene builder every frame without allocating; uploaded as-is by the GL renderer.
 */
class InstanceBatch(val capacity: Int) {
  val data = FloatArray(capacity * STRIDE)
  var count = 0; private set
  private val tmp = FloatArray(16)

  fun clear() { count = 0 }

  /** Appends the first [n] instances of [src] (bounded by this batch's remaining capacity). */
  fun copyFrom(src: InstanceBatch, n: Int = src.count) {
    val k = minOf(n, src.count, capacity - count)
    if (k <= 0) return
    System.arraycopy(src.data, 0, data, count * STRIDE, k * STRIDE)
    count += k
  }

  /** Adds translate · rotateZ · scale. Returns false (and drops it) when the batch is full. */
  fun add(tx: Float, ty: Float, tz: Float, rz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, a: Float = 1f): Boolean {
    if (count >= capacity) return false
    Mat4.trs(tmp, tx, ty, tz, rz, sx, sy, sz)
    val o = count * STRIDE
    System.arraycopy(tmp, 0, data, o, 16)
    data[o + 16] = r; data[o + 17] = g; data[o + 18] = b; data[o + 19] = a
    count++
    return true
  }

  fun add(tx: Double, ty: Double, tz: Double, rz: Double, sx: Double, sy: Double, sz: Double, c: FloatArray, a: Float = 1f): Boolean =
    add(tx.toFloat(), ty.toFloat(), tz.toFloat(), rz.toFloat(), sx.toFloat(), sy.toFloat(), sz.toFloat(), c[0], c[1], c[2], a)

  companion object { const val STRIDE = 20 }
}

class RenderBatches(cubeCap: Int = 1024, cylinderCap: Int = 512, sphereCap: Int = 512) {
  val cube = InstanceBatch(cubeCap)
  val cylinder = InstanceBatch(cylinderCap)
  val sphere = InstanceBatch(sphereCap)

  fun clear() { cube.clear(); cylinder.clear(); sphere.clear() }
  fun batch(kind: Int): InstanceBatch = when (kind) { Meshes.CUBE -> cube; Meshes.CYLINDER -> cylinder; else -> sphere }
  val totalInstances: Int get() = cube.count + cylinder.count + sphere.count
}

/** Interleaved static geometry: x y z  nx ny nz  r g b per vertex. */
class StaticMesh(val vertices: FloatArray, val indices: ShortArray) {
  val vertexCount: Int get() = vertices.size / FLOATS_PER_VERTEX
  val triangleCount: Int get() = indices.size / 3
  companion object { const val FLOATS_PER_VERTEX = 9 }
}
