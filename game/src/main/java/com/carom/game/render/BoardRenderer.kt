package com.carom.game.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.carom.core.level.Block
import com.carom.core.level.LevelData
import com.carom.core.level.Wall
import com.carom.core.math.Vec2
import com.carom.game.ui.Palette
import com.carom.game.ui.WorldPalette

/**
 * Draws a level: walls, blocks, goal, ball, launch zone and shot paths, in the world's two
 * colours (obstacles in the main colour; ball, goal and guides in the contrast colour). The
 * level's edges are never drawn: they are the edges of the screen.
 *
 * Obstacles have a calm, classic look: solid bars and panels with rounded ends and corners, a
 * fine light inlay line running inside each one, small pearls finishing the ends of the bars, and
 * a soft shadow that lifts them off the ground. What is drawn is exactly what the ball bounces
 * off: a wall's round ends and a block's round corners are part of its physics too.
 *
 * Level geometry is converted to screen-space paths once per layout, so drawing a frame is a
 * handful of draw calls with no allocation.
 */
class BoardRenderer(private val level: LevelData, private val palette: WorldPalette) {

    enum class BallStyle {
        /** A full ball. */
        SOLID,

        /** A ring: the attempt ended with the ball resting. */
        HOLLOW,

        /** Cracked round its rim: no bounces left, so the next wall breaks it. */
        CRACKED,
    }

    var scale = 1f
        private set
    var originX = 0f
        private set
    var originY = 0f
        private set

    /** Wall centre lines, one path per thickness since stroke width is per draw call. */
    private val wallPaths = LinkedHashMap<Double, Path>()

    /** Block cores (outlines pulled in by their rounding), one path per rounding. */
    private val blockPaths = LinkedHashMap<Double, Path>()

    /** Pearls at the open ends of walls: x, y, radius (screen) in sequence. */
    private var pearls = FloatArray(0)

    /** Small lozenges at the centre of the larger blocks: x, y, half-size (screen). */
    private var ornaments = FloatArray(0)
    private val trailPath = Path()
    private val ornamentPath = Path()
    private val arcBox = RectF()

    private val bodyColor = palette.primary
    private val inlayColor = Palette.blend(palette.primary, 0xFFFFFFFF.toInt(), 0.42f)
    private val pearlColor = Palette.blend(palette.primary, 0xFFFFFFFF.toInt(), 0.62f)
    private val shadowColor = Palette.blend(palette.background, 0xFF000000.toInt(), 0.5f)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var dash: DashPathEffect? = null

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val numberBounds = Rect()
    private val numbers = Array(100) { it.toString() }

    fun layout(scale: Float, originX: Float, originY: Float) {
        this.scale = scale
        this.originX = originX
        this.originY = originY
        dash = DashPathEffect(floatArrayOf(6f * scale, 10f * scale), 0f)

        wallPaths.clear()
        blockPaths.clear()
        val pearlList = ArrayList<Float>()
        val ornamentList = ArrayList<Float>()
        for (o in level.obstacles) {
            when (o) {
                is Wall -> {
                    addPolyline(wallPaths.getOrPut(o.thickness) { Path() }, o.points, o.closed)
                    if (!o.closed) {
                        for (p in listOf(o.points.first(), o.points.last())) {
                            pearlList += x(p.x)
                            pearlList += y(p.y)
                            pearlList += (o.thickness * PEARL).toFloat() * scale
                        }
                    }
                }
                is Block -> {
                    addPolyline(blockPaths.getOrPut(o.radius) { Path() }, o.core, closed = true)
                    val minX = o.core.minOf { it.x }
                    val maxX = o.core.maxOf { it.x }
                    val minY = o.core.minOf { it.y }
                    val maxY = o.core.maxOf { it.y }
                    // Free-standing panels carry an ornament; pieces filling a corner or an edge don't.
                    val edge = o.points.any { it.x <= 0.5 || it.y <= 0.5 || it.x >= level.width - 0.5 || it.y >= level.height - 0.5 }
                    if (o.radius > 0 && !edge && minOf(maxX - minX, maxY - minY) >= ORNAMENT_MIN_CORE) {
                        ornamentList += x(o.core.sumOf { it.x } / o.core.size)
                        ornamentList += y(o.core.sumOf { it.y } / o.core.size)
                        ornamentList += (level.wallThickness * 0.36).toFloat() * scale
                    }
                }
            }
        }
        pearls = pearlList.toFloatArray()
        ornaments = ornamentList.toFloatArray()
    }

    fun x(worldX: Double): Float = originX + worldX.toFloat() * scale
    fun y(worldY: Double): Float = originY + worldY.toFloat() * scale
    fun worldX(screenX: Float): Double = ((screenX - originX) / scale).toDouble()
    fun worldY(screenY: Float): Double = ((screenY - originY) / scale).toDouble()

    fun drawGeometry(canvas: Canvas) {
        // A soft shadow just below each obstacle.
        canvas.save()
        canvas.translate(0f, (level.wallThickness * SHADOW_DROP).toFloat() * scale)
        drawBodies(canvas, shadowColor)
        canvas.restore()

        drawBodies(canvas, bodyColor)

        // The inlay: a fine light line inside each obstacle, the same distance in from its edge.
        strokePaint.color = inlayColor
        for ((thickness, path) in wallPaths) {
            strokePaint.strokeWidth = (thickness * INLAY).toFloat() * scale
            canvas.drawPath(path, strokePaint)
        }
        strokePaint.strokeWidth = (level.wallThickness * INLAY * 0.8).toFloat() * scale
        for ((radius, path) in blockPaths) if (radius > 0) canvas.drawPath(path, strokePaint)

        fillPaint.color = pearlColor
        for (i in pearls.indices step 3) canvas.drawCircle(pearls[i], pearls[i + 1], pearls[i + 2], fillPaint)
        for (i in ornaments.indices step 3) {
            val cx = ornaments[i]
            val cy = ornaments[i + 1]
            val h = ornaments[i + 2]
            ornamentPath.reset()
            ornamentPath.moveTo(cx, cy - h)
            ornamentPath.lineTo(cx + h * 0.7f, cy)
            ornamentPath.lineTo(cx, cy + h)
            ornamentPath.lineTo(cx - h * 0.7f, cy)
            ornamentPath.close()
            canvas.drawPath(ornamentPath, fillPaint)
        }
    }

    /** Every obstacle's solid shape in one colour: walls as round-ended bars, blocks as round-cornered panels. */
    private fun drawBodies(canvas: Canvas, color: Int) {
        strokePaint.color = color
        for ((thickness, path) in wallPaths) {
            strokePaint.strokeWidth = thickness.toFloat() * scale
            canvas.drawPath(path, strokePaint)
        }
        fillPaint.color = color
        for ((radius, path) in blockPaths) {
            canvas.drawPath(path, fillPaint)
            if (radius > 0) {
                strokePaint.strokeWidth = (radius * 2).toFloat() * scale
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    /** The goal ring. [pulse] (0..1) animates the scoring ripple. */
    fun drawGoal(canvas: Canvas, pulse: Float) {
        val cx = x(level.goal.x)
        val cy = y(level.goal.y)
        val r = level.goalRadius.toFloat() * scale
        val ring = r * 0.18f
        linePaint.pathEffect = null
        linePaint.color = palette.accent
        linePaint.strokeWidth = ring
        canvas.drawCircle(cx, cy, r - ring / 2, linePaint)
        fillPaint.color = palette.accent
        canvas.drawCircle(cx, cy, r * 0.2f, fillPaint)
        if (pulse > 0f) {
            linePaint.color = Palette.withAlpha(palette.accent, 1f - pulse)
            linePaint.strokeWidth = ring * (1f - pulse * 0.6f)
            canvas.drawCircle(cx, cy, r * (1f + pulse * 1.2f), linePaint)
        }
    }

    /** Radius of the ball on screen, in pixels. */
    val ballScreenRadius: Float get() = level.ballRadius.toFloat() * scale

    /**
     * The ball at screen position (sx, sy), with the bounces it has left written in its centre.
     * The number is part of the ball: same position, same scale, always inside its edge.
     */
    fun drawBall(canvas: Canvas, sx: Float, sy: Float, sizeFactor: Float, style: BallStyle, bouncesLeft: Int) {
        val r = ballScreenRadius * sizeFactor
        if (style == BallStyle.HOLLOW) {
            val ring = r * 0.12f
            linePaint.pathEffect = null
            linePaint.color = palette.accent
            linePaint.strokeWidth = ring
            canvas.drawCircle(sx, sy, r - ring / 2, linePaint)
            numberPaint.color = palette.accent
        } else {
            fillPaint.color = palette.accent
            canvas.drawCircle(sx, sy, r, fillPaint)
            numberPaint.color = palette.background
            if (style == BallStyle.CRACKED) drawCracks(canvas, sx, sy, r)
        }
        val text = numbers[bouncesLeft.coerceIn(0, numbers.size - 1)]
        numberPaint.textSize = r * if (text.length == 1) 1.1f else 0.85f
        numberPaint.getTextBounds(text, 0, text.length, numberBounds)
        canvas.drawText(text, sx, sy - numberBounds.exactCenterY(), numberPaint)
    }

    /** Fine jagged cracks running in from the rim: the lines along which the ball will break. */
    private fun drawCracks(canvas: Canvas, sx: Float, sy: Float, r: Float) {
        linePaint.pathEffect = null
        linePaint.color = Palette.withAlpha(palette.background, 0.9f)
        for ((k, i) in BallCracks.visible.withIndex()) {
            // Wider where it meets the rim, finer as it runs inwards.
            linePaint.strokeWidth = r * 0.06f
            canvas.drawLine(
                sx + BallCracks.rimX[i] * r, sy + BallCracks.rimY[i] * r,
                sx + BallCracks.outerX[i] * r, sy + BallCracks.outerY[i] * r, linePaint,
            )
            if (BallCracks.reach[k] >= 2) continue
            linePaint.strokeWidth = r * 0.035f
            canvas.drawLine(
                sx + BallCracks.outerX[i] * r, sy + BallCracks.outerY[i] * r,
                sx + BallCracks.innerX[i] * r, sy + BallCracks.innerY[i] * r, linePaint,
            )
        }
    }

    /**
     * The launch zone around the ball at (sx, sy): a faint disc edged by a fine double ring,
     * showing where the ball can be held and dragged. While dragging ([active]), a thin arc fills
     * the ring clockwise from the top with the shot's [power], like a dial, so it never points
     * anywhere: the direction is the player's drag alone.
     */
    fun drawLaunchZone(canvas: Canvas, sx: Float, sy: Float, radius: Float, unit: Float, active: Boolean, power: Float, ready: Boolean) {
        fillPaint.color = Palette.withAlpha(palette.accent, if (active) 0.09f else 0.055f)
        canvas.drawCircle(sx, sy, radius, fillPaint)
        linePaint.pathEffect = null
        linePaint.color = Palette.withAlpha(palette.accent, if (active) 0.42f else 0.3f)
        linePaint.strokeWidth = 1.5f * unit
        canvas.drawCircle(sx, sy, radius, linePaint)
        linePaint.color = Palette.withAlpha(palette.accent, 0.13f)
        linePaint.strokeWidth = unit
        canvas.drawCircle(sx, sy, radius - 4.5f * unit, linePaint)
        if (!active || power <= 0f) return

        val color = if (ready) palette.accent else Palette.withAlpha(palette.accent, 0.45f)
        linePaint.color = color
        linePaint.strokeWidth = 3f * unit
        arcBox.set(sx - radius, sy - radius, sx + radius, sy + radius)
        val sweep = 360f * power.coerceIn(0f, 1f)
        canvas.drawArc(arcBox, -90f, sweep, false, linePaint)
        val end = Math.toRadians((sweep - 90f).toDouble())
        fillPaint.color = color
        canvas.drawCircle(sx + radius * Math.cos(end).toFloat(), sy + radius * Math.sin(end).toFloat(), 3.4f * unit, fillPaint)
    }

    /** A shot's path through [points], optionally continued to the ball's current screen position. */
    fun drawShotPath(canvas: Canvas, points: List<Vec2>, endX: Float, endY: Float, color: Int, dashed: Boolean) {
        if (points.isEmpty()) return
        trailPath.reset()
        trailPath.moveTo(x(points[0].x), y(points[0].y))
        for (i in 1 until points.size) trailPath.lineTo(x(points[i].x), y(points[i].y))
        if (!endX.isNaN()) trailPath.lineTo(endX, endY)
        linePaint.color = color
        linePaint.strokeWidth = 2.5f * scale
        linePaint.pathEffect = if (dashed) dash else null
        canvas.drawPath(trailPath, linePaint)
        linePaint.pathEffect = null
    }

    private fun addPolyline(path: Path, points: List<Vec2>, closed: Boolean) {
        path.moveTo(x(points[0].x), y(points[0].y))
        for (i in 1 until points.size) path.lineTo(x(points[i].x), y(points[i].y))
        if (closed) path.close()
    }

    private companion object {
        /** Inlay line width, as a fraction of the wall's thickness. */
        const val INLAY = 0.14

        /** Radius of the pearls on a wall's ends, as a fraction of its thickness. */
        const val PEARL = 0.17

        /** How far below an obstacle its shadow falls, as a fraction of the wall thickness. */
        const val SHADOW_DROP = 0.22

        /** Blocks whose core is at least this big (world units) carry a centre ornament. */
        const val ORNAMENT_MIN_CORE = 60.0
    }
}
