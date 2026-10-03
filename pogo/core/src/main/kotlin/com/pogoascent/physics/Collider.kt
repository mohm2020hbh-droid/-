package com.pogoascent.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Reusable result holder (no allocation in the hot loop). Normal points out of the solid, towards the circle. */
class ContactResult {
  var nx = 0.0
  var ny = 1.0
  var depth = 0.0
  var px = 0.0
  var py = 0.0
  var collider: Collider? = null
  fun set(o: ContactResult) { nx = o.nx; ny = o.ny; depth = o.depth; px = o.px; py = o.py; collider = o.collider }
}

/** Kinematic motion of a platform: offset(t) = amplitude · sin(2π·t/period + phase). Smooth, analytic velocity. */
class Mover(val ax: Double, val ay: Double, val period: Double, val phase: Double = 0.0) {
  init { require(period > 0.0) { "mover period must be > 0" } }
  private val w = 2.0 * PI / period
  fun offsetX(t: Double) = ax * sin(w * t + phase)
  fun offsetY(t: Double) = ay * sin(w * t + phase)
  fun velX(t: Double) = ax * w * cos(w * t + phase)
  fun velY(t: Double) = ay * w * cos(w * t + phase)
}

/**
 * A static or kinematic convex polygon. Vertices are stored in local space; the world position is
 * local + (ox, oy). Winding is normalised to CCW and convexity is enforced at construction.
 */
class Collider(
  val id: String,
  xsIn: DoubleArray,
  ysIn: DoubleArray,
  val surface: SurfaceDef,
  val oneWay: Boolean = false,
  val mover: Mover? = null,
) {
  val n: Int = xsIn.size
  private val xs = xsIn.copyOf()
  private val ys = ysIn.copyOf()
  private val enx = DoubleArray(n)
  private val eny = DoubleArray(n)
  private val ed = DoubleArray(n)

  /** Current offset, last-tick displacement and velocity (all zero for static colliders). */
  var ox = 0.0; private set
  var oy = 0.0; private set
  var dx = 0.0; private set
  var dy = 0.0; private set
  var vx = 0.0; private set
  var vy = 0.0; private set

  var minX = 0.0; private set
  var maxX = 0.0; private set
  var minY = 0.0; private set
  var maxY = 0.0; private set
  private var lMinX = 0.0
  private var lMaxX = 0.0
  private var lMinY = 0.0
  private var lMaxY = 0.0

  val isSensorLike: Boolean get() = surface.type == SurfaceType.GOAL

  init {
    require(n >= 3) { "collider '$id' needs at least 3 vertices" }
    require(ysIn.size == n) { "collider '$id': xs/ys size mismatch" }
    var area = 0.0
    for (i in 0 until n) { val j = (i + 1) % n; area += xs[i] * ys[j] - xs[j] * ys[i] }
    require(area != 0.0) { "collider '$id' has zero area" }
    if (area < 0.0) { xs.reverse(); ys.reverse() }
    for (i in 0 until n) {
      val j = (i + 1) % n
      val ex = xs[j] - xs[i]
      val ey = ys[j] - ys[i]
      val len = hypot(ex, ey)
      require(len > 1e-9) { "collider '$id' has a degenerate edge" }
      enx[i] = ey / len
      eny[i] = -ex / len
      ed[i] = enx[i] * xs[i] + eny[i] * ys[i]
    }
    for (i in 0 until n) {
      val a = (i + 1) % n
      val b = (i + 2) % n
      val cross = (xs[a] - xs[i]) * (ys[b] - ys[a]) - (ys[a] - ys[i]) * (xs[b] - xs[a])
      require(cross >= -1e-9) { "collider '$id' is not convex (split it into convex pieces)" }
    }
    lMinX = xs.min(); lMaxX = xs.max(); lMinY = ys.min(); lMaxY = ys.max()
    update(0.0)
  }

  fun localX(i: Int) = xs[i]
  fun localY(i: Int) = ys[i]
  fun worldX(i: Int) = xs[i] + ox
  fun worldY(i: Int) = ys[i] + oy

  /** Move the collider to its pose at simulation time [t]. */
  fun update(t: Double) {
    val m = mover
    if (m != null) {
      val nox = m.offsetX(t)
      val noy = m.offsetY(t)
      dx = nox - ox; dy = noy - oy
      ox = nox; oy = noy
      vx = m.velX(t); vy = m.velY(t)
    }
    minX = lMinX + ox; maxX = lMaxX + ox; minY = lMinY + oy; maxY = lMaxY + oy
  }

  /** Snap back to the pose at t = 0 (used on level reset). */
  fun reset() {
    ox = 0.0; oy = 0.0; dx = 0.0; dy = 0.0; vx = 0.0; vy = 0.0
    update(0.0)
  }

  fun containsPoint(px: Double, py: Double): Boolean {
    val lx = px - ox
    val ly = py - oy
    for (i in 0 until n) if (enx[i] * lx + eny[i] * ly - ed[i] > 0.0) return false
    return true
  }

  /** Circle (centre in world space) vs this convex polygon. */
  fun circleContact(cx: Double, cy: Double, r: Double, out: ContactResult): Boolean {
    if (cx + r < minX || cx - r > maxX || cy + r < minY || cy - r > maxY) return false
    val lx = cx - ox
    val ly = cy - oy
    var maxSep = -Double.MAX_VALUE
    var idx = 0
    for (i in 0 until n) {
      val sep = enx[i] * lx + eny[i] * ly - ed[i]
      if (sep > r) return false
      if (sep > maxSep) { maxSep = sep; idx = i }
    }
    if (maxSep < 0.0) {
      // centre inside the polygon: push out through the nearest edge
      out.nx = enx[idx]; out.ny = eny[idx]
      out.depth = r - maxSep
      out.px = cx - out.nx * r; out.py = cy - out.ny * r
      out.collider = this
      return true
    }
    val j = (idx + 1) % n
    val ax = xs[idx]; val ay = ys[idx]
    val ex = xs[j] - ax; val ey = ys[j] - ay
    val len2 = ex * ex + ey * ey
    var t = ((lx - ax) * ex + (ly - ay) * ey) / len2
    if (t < 0.0) t = 0.0 else if (t > 1.0) t = 1.0
    val qx = ax + ex * t
    val qy = ay + ey * t
    val ddx = lx - qx
    val ddy = ly - qy
    val dist = hypot(ddx, ddy)
    if (dist > r) return false
    if (dist < 1e-12) { out.nx = enx[idx]; out.ny = eny[idx] } else { out.nx = ddx / dist; out.ny = ddy / dist }
    out.depth = r - dist
    out.px = qx + ox; out.py = qy + oy
    out.collider = this
    return true
  }

  companion object {
    fun box(id: String, cx: Double, cy: Double, w: Double, h: Double, surface: SurfaceDef, rotationRad: Double = 0.0, oneWay: Boolean = false, mover: Mover? = null): Collider {
      val hw = w / 2; val hh = h / 2
      val c = cos(rotationRad); val s = sin(rotationRad)
      val px = doubleArrayOf(-hw, hw, hw, -hw)
      val py = doubleArrayOf(-hh, -hh, hh, hh)
      val xs = DoubleArray(4) { cx + px[it] * c - py[it] * s }
      val ys = DoubleArray(4) { cy + px[it] * s + py[it] * c }
      return Collider(id, xs, ys, surface, oneWay, mover)
    }
  }
}

/** Non-solid trigger volume (axis aligned). */
enum class SensorKind { GOAL, CHECKPOINT, KILL }

class Sensor(val id: String, val kind: SensorKind, val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
  fun overlapsCircle(cx: Double, cy: Double, r: Double): Boolean {
    val qx = cx.coerceIn(minX, maxX)
    val qy = cy.coerceIn(minY, maxY)
    val dx = cx - qx
    val dy = cy - qy
    return dx * dx + dy * dy <= r * r
  }
}
