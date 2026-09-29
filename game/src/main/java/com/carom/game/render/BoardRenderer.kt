package com.carom.game.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import com.carom.core.level.Block
import com.carom.core.level.LevelData
import com.carom.core.level.Wall
import com.carom.core.math.Vec2
import com.carom.game.ui.Palette

/**
 * Draws a level: walls, blocks, goal, ball, shot paths and the aiming guide.
 *
 * Level geometry is converted to screen-space paths once per layout, so drawing a frame is a
 * handful of draw calls with no allocation.
 */
class BoardRenderer(private val level: LevelData, private val wallColor: Int) {

    var scale = 1f
        private set
    var originX = 0f
        private set
    var originY = 0f
        private set

    /** One path per wall thickness, since stroke width is per draw call. */
    private val wallPaths = LinkedHashMap<Double, Path>()
    private val blockPath = Path()
    private val trailPath = Path()

    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = wallColor
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
        color = Palette.BACKGROUND
    }
    private val numberBounds = Rect()
    private val numbers = Array(100) { it.toString() }

    fun layout(scale: Float, originX: Float, originY: Float) {
        this.scale = scale
        this.originX = originX
        this.originY = originY
        dash = DashPathEffect(floatArrayOf(6f * scale, 10f * scale), 0f)

        wallPaths.clear()
        blockPath.reset()
        if (level.border) {
            val corners = listOf(
                Vec2(0.0, 0.0), Vec2(level.width, 0.0), Vec2(level.width, level.height), Vec2(0.0, level.height),
            )
            addPolyline(pathFor(level.wallThickness), corners, closed = true)
        }
        for (o in level.obstacles) {
            when (o) {
                is Wall -> addPolyline(pathFor(o.thickness), o.points, o.closed)
                is Block -> addPolyline(blockPath, o.points, closed = true)
            }
        }
    }

    fun x(worldX: Double): Float = originX + worldX.toFloat() * scale
    fun y(worldY: Double): Float = originY + worldY.toFloat() * scale
    fun worldX(screenX: Float): Double = ((screenX - originX) / scale).toDouble()
    fun worldY(screenY: Float): Double = ((screenY - originY) / scale).toDouble()

    fun drawGeometry(canvas: Canvas) {
        for ((thickness, path) in wallPaths) {
            wallPaint.strokeWidth = thickness.toFloat() * scale
            canvas.drawPath(path, wallPaint)
        }
        fillPaint.color = wallColor
        canvas.drawPath(blockPath, fillPaint)
    }

    /** The goal ring. [pulse] (0..1) animates the scoring ripple. */
    fun drawGoal(canvas: Canvas, pulse: Float) {
        val cx = x(level.goal.x)
        val cy = y(level.goal.y)
        val r = level.goalRadius.toFloat() * scale
        val ring = r * 0.18f
        linePaint.pathEffect = null
        linePaint.color = Palette.ACCENT
        linePaint.strokeWidth = ring
        canvas.drawCircle(cx, cy, r - ring / 2, linePaint)
        fillPaint.color = Palette.ACCENT
        canvas.drawCircle(cx, cy, r * 0.2f, fillPaint)
        if (pulse > 0f) {
            linePaint.color = Palette.withAlpha(Palette.ACCENT, 1f - pulse)
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
    fun drawBall(canvas: Canvas, sx: Float, sy: Float, sizeFactor: Float, color: Int, bouncesLeft: Int) {
        val r = ballScreenRadius * sizeFactor
        fillPaint.color = color
        canvas.drawCircle(sx, sy, r, fillPaint)
        val text = numbers[bouncesLeft.coerceIn(0, numbers.size - 1)]
        numberPaint.textSize = r * if (text.length == 1) 1.1f else 0.85f
        numberPaint.getTextBounds(text, 0, text.length, numberBounds)
        canvas.drawText(text, sx, sy - numberBounds.exactCenterY(), numberPaint)
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

    /**
     * The aiming guide: dots leading away from the ball in the launch direction, longer for more
     * power, ending in a chevron. It deliberately doesn't predict bounces — planning is the puzzle.
     */
    fun drawAim(canvas: Canvas, sx: Float, sy: Float, dirX: Float, dirY: Float, power: Float, ready: Boolean, unit: Float) {
        val color = if (ready) Palette.BALL else Palette.withAlpha(Palette.BALL, 0.35f)
        val start = level.ballRadius.toFloat() * scale + 6f * unit
        val length = 16f * unit + power * 100f * unit
        val spacing = 7f * unit
        fillPaint.color = color
        var d = start
        while (d < start + length) {
            val fade = 1f - (d - start) / (length + spacing)
            canvas.drawCircle(sx + dirX * d, sy + dirY * d, unit * (1.1f + 0.9f * fade), fillPaint)
            d += spacing
        }
        if (ready) {
            val tip = start + length + spacing * 0.5f
            val tx = sx + dirX * tip
            val ty = sy + dirY * tip
            val h = 5f * unit
            linePaint.color = color
            linePaint.strokeWidth = 1.8f * unit
            linePaint.pathEffect = null
            trailPath.reset()
            trailPath.moveTo(tx - dirX * h - dirY * h, ty - dirY * h + dirX * h)
            trailPath.lineTo(tx, ty)
            trailPath.lineTo(tx - dirX * h + dirY * h, ty - dirY * h - dirX * h)
            canvas.drawPath(trailPath, linePaint)
        }
    }

    private fun pathFor(thickness: Double): Path = wallPaths.getOrPut(thickness) { Path() }

    private fun addPolyline(path: Path, points: List<Vec2>, closed: Boolean) {
        path.moveTo(x(points[0].x), y(points[0].y))
        for (i in 1 until points.size) path.lineTo(x(points[i].x), y(points[i].y))
        if (closed) path.close()
    }
}
