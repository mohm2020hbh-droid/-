package com.carom.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.carom.game.ui.Palette
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

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
 * The ball breaking after its last allowed bounce, centred at (cx, cy) with radius [r] (screen
 * pixels). It splits along its cracks and the pieces drift away from the wall it hit (the
 * direction (awayX, awayY)), turning a little and fading, with a faint ring and a few specks of
 * dust. Quick and restrained: it is over in [DURATION] seconds.
 */
class BallShatter(private val cx: Float, private val cy: Float, private val r: Float, awayX: Float, awayY: Float) {

    private class Shard(val path: Path, val x: Float, val y: Float, val vx: Float, val vy: Float, val spin: Float)

    private val shards = ArrayList<Shard>()

    /** Dust specks: x, y, vx, vy in sequence. */
    private val dust = FloatArray(DUST * 4)

    init {
        val random = Random(11)
        val ox = BallCracks.ORIGIN_X
        val oy = BallCracks.ORIGIN_Y
        for (i in 0 until BallCracks.COUNT) {
            val j = (i + 1) % BallCracks.COUNT
            // The wedge between crack i and crack j, in unit coordinates.
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            fun add(x: Float, y: Float) {
                xs += x
                ys += y
            }
            add(ox, oy)
            add(BallCracks.innerX[i], BallCracks.innerY[i])
            add(BallCracks.outerX[i], BallCracks.outerY[i])
            val a0 = BallCracks.angles[i]
            val a1 = BallCracks.angles[j] + if (j == 0) 360f else 0f
            val steps = ((a1 - a0) / 12f).toInt().coerceAtLeast(2)
            for (k in 0..steps) {
                val a = Math.toRadians((a0 + (a1 - a0) * k / steps).toDouble())
                add(cos(a).toFloat(), sin(a).toFloat())
            }
            add(BallCracks.outerX[j], BallCracks.outerY[j])
            add(BallCracks.innerX[j], BallCracks.innerY[j])

            val mx = xs.average().toFloat()
            val my = ys.average().toFloat()
            val path = Path()
            for (k in xs.indices) {
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
            val speed = r * (4.5f + 1.5f * random.nextFloat())
            val spin = (1.5f + 1.5f * random.nextFloat()) * if (random.nextBoolean()) 1f else -1f
            shards += Shard(path, cx + mx * r, cy + my * r, dx * speed, dy * speed, spin)
        }
        for (k in 0 until DUST) {
            val a = random.nextFloat() * 6.2832f
            var dx = cos(a) + awayX * 0.7f
            var dy = sin(a) + awayY * 0.7f
            val len = hypot(dx, dy).coerceAtLeast(1e-3f)
            dx /= len
            dy /= len
            val speed = r * (7f + 3f * random.nextFloat())
            dust[k * 4] = cx + cos(a) * r * 0.8f
            dust[k * 4 + 1] = cy + sin(a) * r * 0.8f
            dust[k * 4 + 2] = dx * speed
            dust[k * 4 + 3] = dy * speed
        }
    }

    /** Draws the break [t] seconds after it happened, in the ball's [color]. */
    fun draw(canvas: Canvas, t: Float, color: Int, fill: Paint, stroke: Paint) {
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
        for (s in shards) {
            canvas.save()
            canvas.translate(s.x + s.vx * travel, s.y + s.vy * travel)
            canvas.rotate(Math.toDegrees((s.spin * travel).toDouble()).toFloat())
            canvas.scale(size, size)
            canvas.drawPath(s.path, fill)
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
    }
}
