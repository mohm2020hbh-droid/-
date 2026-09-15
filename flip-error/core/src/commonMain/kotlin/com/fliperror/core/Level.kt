package com.fliperror.core

/** Axis-aligned box in world units. y is up; y0 is the bottom edge, y1 the top. */
data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    fun overlaps(o: Box) = x0 < o.x1 && x1 > o.x0 && y0 < o.y1 && y1 > o.y0
    /** Shrink towards the centre by [scale] (1.0 = unchanged). */
    fun shrink(scale: Double): Box {
        val mx = (x1 - x0) * (1 - scale) / 2.0
        val my = (y1 - y0) * (1 - scale) / 2.0
        return Box(x0 + mx, y0 + my, x1 - mx, y1 - my)
    }
}

enum class HazardKind { SPIKE_UP, SPIKE_DOWN }

/** A solid block. Landable on top, lethal to run into from the side. */
data class Solid(val x0: Double, val x1: Double, val top: Double, val bottom: Double = -40.0) {
    val box get() = Box(x0, bottom, x1, top)
}

data class Hazard(val kind: HazardKind, val x0: Double, val x1: Double, val y0: Double, val y1: Double) {
    /** GDD fairness law 3: the killing box is 15% smaller than the drawing. */
    val hitBox: Box = Box(x0, y0, x1, y1).shrink(Tuning.HAZARD_HITBOX_SCALE)
    val drawBox: Box get() = Box(x0, y0, x1, y1)
}

data class Star(val x: Double, val y: Double) {
    val box get() = Box(x - 0.45, y - 0.45, x + 0.45, y + 0.45)
}

data class Level(
    val id: Int,
    val name: String,
    val subtitle: String,
    val bpm: Double,
    val solids: List<Solid>,
    val hazards: List<Hazard>,
    val stars: List<Star>,
    val finishX: Double,
    val startY: Double = 0.0,
) {
    /** Level length in seconds at the level's run speed. */
    val durationSeconds: Double get() = finishX / Tuning.RUN_SPEED

    @PublishedApi internal val solidsSorted = solids.sortedBy { it.x0 }
    @PublishedApi internal val hazardsSorted = hazards.sortedBy { it.x0 }

    /**
     * Visit every solid whose x-range can touch [x0,x1].
     * Callback form on purpose: this runs 240 times a second and a mobile
     * frame budget has no room for allocating a fresh list each step.
     */
    inline fun forEachSolidNear(x0: Double, x1: Double, action: (Solid) -> Unit) {
        for (i in solidsSorted.indices) {
            val s = solidsSorted[i]
            if (s.x0 > x1) break
            if (s.x1 >= x0) action(s)
        }
    }

    inline fun forEachHazardNear(x0: Double, x1: Double, action: (Hazard) -> Unit) {
        for (i in hazardsSorted.indices) {
            val h = hazardsSorted[i]
            if (h.x0 > x1) break
            if (h.x1 >= x0) action(h)
        }
    }
}
