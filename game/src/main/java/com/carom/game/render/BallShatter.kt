package com.carom.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.carom.game.ui.Palette
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where a ball with no bounces left breaks, in unit-circle coordinates (y down): jagged crack
 * lines run from a point near the centre out to the rim, zig-zagging through two bends ("inner"
 * and "outer"). The flying ball shows the outer part of a few of them; when it breaks, the pieces
 * are the wedges between all of them.
 */
object BallCracks {
    const val COUNT = 6
    const val ORIGIN_X = -0.12f
    const val ORIGIN_Y = 0.08f

    /** Rim angle of each crack in degrees, clockwise from +x, in increasing order. */
    val angles = floatArrayOf(-105f, -38f, 18f, 82f, 148f, 205f)

    /** Cracks drawn on the fragile ball, and how far in each reaches (1 = to its inner bend, 2 = to its outer bend). */
    val visible = intArrayOf(0, 2, 4)
    val reach = intArrayOf(1, 2, 1)

    val rimX = FloatArray(COUNT)
    val rimY = FloatArray(COUNT)
    val outerX = FloatArray(COUNT)
    val outerY = FloatArray(COUNT)
    val innerX = FloatArray(COUNT)
    val innerY = FloatArray(COUNT)

    init {
        val outerBend = floatArrayOf(0.07f, -0.06f, 0.05f, -0.07f, 0.06f, -0.05f)
        for (i in 0 until COUNT) {
            val a = Math.toRadians(angles[i].toDouble())
            rimX[i] = cos(a).toFloat()
            rimY[i] = sin(a).toFloat()
            val dx = rimX[i] - ORIGIN_X
            val dy = rimY[i] - ORIGIN_Y
            val len = hypot(dx, dy)
            // Sideways offsets of opposite signs make each crack zig-zag.
            outerX[i] = ORIGIN_X + dx * 0.64f - dy / len * outerBend[i]
            outerY[i] = ORIGIN_Y + dy * 0.64f + dx / len * outerBend[i]
            innerX[i] = ORIGIN_X + dx * 0.34f + dy / len * outerBend[i] * 0.8f
            innerY[i] = ORIGIN_Y + dy * 0.34f - dx / len * outerBend[i] * 0.8f
        }
    }
}

/**
 * The ball breaking after its last allowed bounce (or dying in a deadly zone), centred at (cx, cy) with radius
 * r (screen pixels). It splits along its cracks and the pieces drift away from the wall it hit (the direction
 * (awayX, awayY)), turning a little and fading, with a faint ring and a few specks of dust. Quick and
 * restrained: it is over in [DURATION] seconds.
 *
 * One instance can play any number of breaks: [start] sets it up again in place, with nothing allocated,
 * so the screen keeps a small fixed set of them instead of making one for every ball that breaks.
 */
class BallShatter {

    private var cx = 0f
    private var cy = 0f
    private var r = 1f
    private var seed = 0

    private val paths = Array(BallCracks.COUNT) { Path() }
    private val shardX = FloatArray(BallCracks.COUNT)
    private val shardY = FloatArray(BallCracks.COUNT)
    private val shardVx = FloatArray(BallCracks.COUNT)
    private val shardVy = FloatArray(BallCracks.COUNT)
    private val shardSpin = FloatArray(BallCracks.COUNT)
    private val xs = FloatArray(MAX_POINTS)
    private val ys = FloatArray(MAX_POINTS)

    /** Dust specks: x, y, vx, vy in sequence. */
    private val dust = FloatArray(DUST * 4)

    /** Seconds since [start]; the break is over when it passes [DURATION]. */
    var age = DURATION
        private set

    val isPlaying: Boolean get() = age < DURATION

    fun advance(dt: Float) {
        if (age < DURATION) age += dt
    }

    /** Begins a break at (cx, cy), the pieces drifting away along (awayX, awayY). */
    fun start(cx: Float, cy: Float, r: Float, awayX: Float, awayY: Float) {
        this.cx = cx
        this.cy = cy
        this.r = r
        seed = 11
        age = 0f
        val ox = BallCracks.ORIGIN_X
        val oy = BallCracks.ORIGIN_Y
        for (i in 0 until BallCracks.COUNT) {
            val j = (i + 1) % BallCracks.COUNT
            // The wedge between crack i and crack j, in unit coordinates.
            var n = 0
            fun add(x: Float, y: Float) {
                xs[n] = x
                ys[n] = y
                n++
            }
            add(ox, oy)
            add(BallCracks.innerX[i], BallCracks.innerY[i])
            add(BallCracks.outerX[i], BallCracks.outerY[i])
            val a0 = BallCracks.angles[i]
            val a1 = BallCracks.angles[j] + if (j == 0) 360f else 0f
            val steps = ((a1 - a0) / 12f).toInt().coerceIn(2, 6)
            for (k in 0..steps) {
                val a = Math.toRadians((a0 + (a1 - a0) * k / steps).toDouble())
                add(cos(a).toFloat(), sin(a).toFloat())
            }
            add(BallCracks.outerX[j], BallCracks.outerY[j])
            add(BallCracks.innerX[j], BallCracks.innerY[j])

            var mx = 0f
            var my = 0f
            for (k in 0 until n) {
                mx += xs[k]
                my += ys[k]
            }
            mx /= n
            my /= n
            val path = paths[i]
            path.rewind()
            for (k in 0 until n) {
                val px = (xs[k] - mx) * r
                val py = (ys[k] - my) * r
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            // Outwards from the break, and away from the wall.
            var dx = (mx - ox) + awayX * 0.9f
            var dy = (my - oy) + awayY * 0.9f
            val len = hypot(dx, dy).coerceAtLeast(1e-3f)
            dx /= len
            dy /= len
            val speed = r * (4.5f + 1.5f * next())
            shardSpin[i] = (1.5f + 1.5f * next()) * if (next() < 0.5f) 1f else -1f
            shardX[i] = cx + mx * r
            shardY[i] = cy + my * r
            shardVx[i] = dx * speed
            shardVy[i] = dy * speed
        }
        for (k in 0 until DUST) {
            val a = next() * 6.2832f
            var dx = cos(a) + awayX * 0.7f
            var dy = sin(a) + awayY * 0.7f
            val len = hypot(dx, dy).coerceAtLeast(1e-3f)
            dx /= len
            dy /= len
            val speed = r * (7f + 3f * next())
            dust[k * 4] = cx + cos(a) * r * 0.8f
            dust[k * 4 + 1] = cy + sin(a) * r * 0.8f
            dust[k * 4 + 2] = dx * speed
            dust[k * 4 + 3] = dy * speed
        }
    }

    /** A small deterministic generator (a break always looks the same), 0..1. */
    private fun next(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65536f
    }

    /** Draws the break in the ball's [color]. */
    fun draw(canvas: Canvas, color: Int, fill: Paint, stroke: Paint) {
        val t = age
        if (t >= DURATION) return
        // Everything eases out: fast apart at the moment of the break, then drifting to rest.
        val travel = (1f - exp(-EASE * t)) / EASE

        if (t < RING_TIME) {
            val k = t / RING_TIME
            stroke.color = Palette.withAlpha(color, 0.45f * (1f - k))
            stroke.strokeWidth = r * 0.06f
            canvas.drawCircle(cx, cy, r * (1f + 0.6f * k), stroke)
        }

        val dustAlpha = (1f - t / DUST_TIME).coerceIn(0f, 1f)
        if (dustAlpha > 0f) {
            fill.color = Palette.withAlpha(color, 0.8f * dustAlpha)
            for (k in 0 until DUST) {
                canvas.drawCircle(dust[k * 4] + dust[k * 4 + 2] * travel, dust[k * 4 + 1] + dust[k * 4 + 3] * travel, r * 0.05f, fill)
            }
        }

        val alpha = if (t < FADE_START) 1f else (1f - (t - FADE_START) / (DURATION - FADE_START)).coerceIn(0f, 1f)
        val size = 1f - 0.18f * (t / DURATION)
        fill.color = Palette.withAlpha(color, alpha)
        for (i in 0 until BallCracks.COUNT) {
            canvas.save()
            canvas.translate(shardX[i] + shardVx[i] * travel, shardY[i] + shardVy[i] * travel)
            canvas.rotate(Math.toDegrees((shardSpin[i] * travel).toDouble()).toFloat())
            canvas.scale(size, size)
            canvas.drawPath(paths[i], fill)
            canvas.restore()
        }
    }

    companion object {
        const val DURATION = 0.65f
        private const val EASE = 5f
        private const val FADE_START = 0.22f
        private const val RING_TIME = 0.3f
        private const val DUST_TIME = 0.4f
        private const val DUST = 7
        private const val MAX_POINTS = 16
    }
}
