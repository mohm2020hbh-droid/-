package com.pogoascent.physics

/** Which contacts a query accepts, by the orientation of their normal. */
enum class ContactFilter { ANY, GROUND_ONLY, NON_GROUND_ONLY }

/**
 * The static + kinematic collision world of one level. Pure data + queries; the player owns all response.
 * [killY] is the world's bottom: falling below it counts as a reset.
 */
class PhysicsWorld(
  val colliders: List<Collider>,
  val sensors: List<Sensor> = emptyList(),
  val killY: Double = -1000.0,
) {
  var time = 0.0
    private set
  private val movers: List<Collider> = colliders.filter { it.mover != null }
  private val scratch = ContactResult()

  val hasMovers: Boolean get() = movers.isNotEmpty()

  /** Advance kinematic colliders to simulation time [t]. */
  fun advanceTo(t: Double) {
    time = t
    for (c in movers) c.update(t)
  }

  fun reset() {
    time = 0.0
    for (c in colliders) c.reset()
  }

  /**
   * Deepest contact of a circle with any solid collider (that passes [filter] and the one-way rule).
   * One-way platforms only count for the foot when it arrives from above ([prevBottomY] = foot bottom last sub-step).
   * Returns false when nothing touches.
   */
  fun deepestContact(
    cx: Double, cy: Double, r: Double,
    filter: ContactFilter, groundMinNy: Double,
    isFoot: Boolean, prevBottomY: Double,
    out: ContactResult,
  ): Boolean {
    var found = false
    var best = -1.0
    val cs = colliders
    for (i in cs.indices) {
      val c = cs[i]
      if (c.isSensorLike) continue
      if (!c.circleContact(cx, cy, r, scratch)) continue
      val ground = scratch.ny >= groundMinNy
      when (filter) {
        ContactFilter.GROUND_ONLY -> if (!ground) continue
        ContactFilter.NON_GROUND_ONLY -> if (ground) continue
        ContactFilter.ANY -> {}
      }
      // A one-way platform is solid only for a foot coming down onto its top face.
      if (c.oneWay && !(isFoot && scratch.ny >= 0.5 && prevBottomY >= c.maxY - 0.06)) continue
      if (scratch.depth > best) { best = scratch.depth; out.set(scratch); found = true }
    }
    return found
  }

  /** Is any solid within [r] of (cx,cy)? Used to keep the head clear while leaning. */
  fun anyPenetration(cx: Double, cy: Double, r: Double, slop: Double): Boolean {
    val cs = colliders
    for (i in cs.indices) {
      val c = cs[i]
      if (c.isSensorLike || c.oneWay) continue
      if (c.circleContact(cx, cy, r, scratch) && scratch.depth > slop) return true
    }
    return false
  }

  fun sensorAt(cx: Double, cy: Double, r: Double, kind: SensorKind): Sensor? {
    val ss = sensors
    for (i in ss.indices) {
      val s = ss[i]
      if (s.kind == kind && s.overlapsCircle(cx, cy, r)) return s
    }
    return null
  }

  /** Goal pads authored as GOAL-surface colliders act as sensors too. */
  fun goalColliderAt(cx: Double, cy: Double, r: Double): Collider? {
    val cs = colliders
    for (i in cs.indices) {
      val c = cs[i]
      if (c.isSensorLike && c.circleContact(cx, cy, r, scratch)) return c
    }
    return null
  }
}
