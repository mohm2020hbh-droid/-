package com.carom.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.carom.core.level.Block
import com.carom.core.level.ControlZone
import com.carom.core.level.LevelData
import com.carom.core.level.Obstacle
import com.carom.core.level.Wall
import com.carom.core.math.Vec2
import com.carom.game.ui.Palette
import com.carom.game.ui.WorldPalette
import kotlin.math.hypot
import kotlin.math.max

/**
 * Draws a level in the world's two colours: obstacles in the main colour; ball, goal and guides in
 * the contrast colour. The level's edges are never drawn: they are the edges of the screen.
 *
 * The look is flat, quiet and geometric: one plain ground, obstacles in one flat tone with clean rounded
 * corners, a plain ball and a goal that is a ring with a fainter ring round it. No gradient, no shadow, no
 * glow, no gloss. What is drawn is exactly what the ball bounces off: round ends and round corners are
 * physics too.
 *
 * The ground and the obstacles never move, so they are painted once per layout into a bitmap;
 * a frame draws that bitmap and then the few moving things.
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

    private var backdrop: Bitmap? = null
    private var viewWidth = 0
    private var viewHeight = 0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPath = Path()
    private val arcBox = RectF()
    private var dash: DashPathEffect? = null

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val numberBounds = Rect()
    private val numbers = Array(100) { it.toString() }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Lays the level out at [scale] with its top-left corner at (originX, originY), on a view of
     * [width]×[height] pixels, and paints the still parts.
     */
    fun layout(scale: Float, originX: Float, originY: Float, width: Int, height: Int) {
        this.scale = scale
        this.originX = originX
        this.originY = originY
        viewWidth = width
        viewHeight = height
        dash = DashPathEffect(floatArrayOf(6f * scale, 10f * scale), 0f)

        val r = ballScreenRadius
        bladePx.set(blade)
        bladePx.transform(Matrix().apply { setScale(r, r) })
        outlines.clear()
        ballPaint.shader = null
        ballPaint.color = palette.accent
        zonePaint.shader = null
        zonePaint.color = Palette.withAlpha(palette.accent, 0.05f)

        backdrop?.recycle()
        backdrop = null
        if (width > 0 && height > 0) {
            backdrop = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { paintBackdrop(Canvas(it)) }
            } catch (e: OutOfMemoryError) {
                null // drawn live instead
            }
        }
    }

    /** Frees the painted bitmap; the renderer lays itself out again before drawing. */
    fun release() {
        backdrop?.recycle()
        backdrop = null
    }

    fun x(worldX: Double): Float = originX + worldX.toFloat() * scale
    fun y(worldY: Double): Float = originY + worldY.toFloat() * scale
    fun worldX(screenX: Float): Double = ((screenX - originX) / scale).toDouble()
    fun worldY(screenY: Float): Double = ((screenY - originY) / scale).toDouble()

    /** The ground and all obstacles. */
    fun drawBackdrop(canvas: Canvas) {
        val bitmap = backdrop
        if (bitmap != null && !bitmap.isRecycled) canvas.drawBitmap(bitmap, 0f, 0f, null) else paintBackdrop(canvas)
    }

    // ---------------------------------------------------------------- still parts

    private fun paintBackdrop(canvas: Canvas) {
        val w = viewWidth.toFloat()
        val h = viewHeight.toFloat()
        // The ground: one plain colour.
        canvas.drawColor(palette.background)

        // On screens shaped differently from the level, the space outside it is a deeper shade.
        val left = originX
        val top = originY
        val right = originX + level.width.toFloat() * scale
        val bottom = originY + level.height.toFloat() * scale
        val outside = Paint().apply { color = palette.void }
        if (top > 0.5f) {
            canvas.drawRect(0f, 0f, w, top, outside)
            canvas.drawRect(0f, bottom, w, h, outside)
        }
        if (left > 0.5f) {
            canvas.drawRect(0f, top, left, bottom, outside)
            canvas.drawRect(right, top, w, bottom, outside)
        }

        val tone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        for (o in level.obstacles) {
            path.reset()
            when (o) {
                is Wall -> {
                    // A bar with round ends: one flat tone.
                    addPolyline(path, o.points, o.closed)
                    tone.style = Paint.Style.STROKE
                    tone.strokeWidth = o.thickness.toFloat() * scale
                    canvas.drawPath(path, tone)
                }
                is Block -> {
                    // A panel with clean rounded corners: its core grown by the rounding, in one flat tone.
                    addPolyline(path, o.core, closed = true)
                    tone.style = if (o.radius > 0.0) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
                    tone.strokeWidth = (o.radius * 2).toFloat() * scale
                    canvas.drawPath(path, tone)
                }
            }
        }
    }

    /** The colour at the screen's edge: what shows if the board is nudged by a shake. */
    val edgeColor: Int get() = palette.void

    // ---------------------------------------------------------------- moving parts

    /**
     * The goal: a ring with a fainter ring round it and a small centre point, no glow. [pulse] (0..1) animates the scoring
     * ripple; [beat] (0..1) is the goal's brief "yes" when a ball of several enters (it never pulses with the music).
     * An exit that needs several balls shows small pips under it, [entered] of them filled.
     */
    fun drawGoal(canvas: Canvas, pulse: Float, beat: Float = 0f, entered: Int = 0, needed: Int = 1) {
        val cx = x(level.goal.x)
        val cy = y(level.goal.y)
        val r = level.goalRadius.toFloat() * scale
        if (needed > 1) {
            val gap = r * 0.34f
            val startX = cx - gap * (needed - 1) / 2f
            for (i in 0 until needed) {
                fillPaint.color = if (i < entered) palette.accent else Palette.withAlpha(palette.accent, 0.25f)
                canvas.drawCircle(startX + i * gap, cy + r * 1.5f, r * 0.09f, fillPaint)
            }
        }
        val ring = r * 0.14f
        linePaint.pathEffect = null
        linePaint.shader = null
        // The outer ring: fine and faint, so the goal reads as a target and not as a wall.
        linePaint.color = Palette.withAlpha(palette.accent, 0.3f + 0.4f * beat)
        linePaint.strokeWidth = max(1f, r * 0.035f)
        canvas.drawCircle(cx, cy, r * OUTER_RING, linePaint)
        linePaint.color = palette.accent
        linePaint.strokeWidth = ring
        canvas.drawCircle(cx, cy, r - ring / 2, linePaint)
        fillPaint.color = palette.accent
        canvas.drawCircle(cx, cy, r * 0.14f, fillPaint)
        if (pulse > 0f) {
            linePaint.color = Palette.withAlpha(palette.accent, 1f - pulse)
            linePaint.strokeWidth = ring * (1f - pulse * 0.6f)
            canvas.drawCircle(cx, cy, r * (1f + pulse * 1.2f), linePaint)
        }
    }

    /**
     * The control zone's line: a quiet dashed outline of where the player has the ball. It is only a picture of where
     * control stops, not a wall: nothing here touches the physics. [alpha] (0..1) is how much of it shows.
     */
    fun drawControlZone(canvas: Canvas, unit: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val zone = level.zone
        linePaint.shader = null
        linePaint.color = Palette.withAlpha(palette.accent, ZONE_LINE_ALPHA * alpha)
        linePaint.strokeWidth = 1.7f * unit
        if (zoneDashUnit != unit) {
            zoneDashUnit = unit
            zoneDash = DashPathEffect(floatArrayOf(5f * unit, 9f * unit), 0f)
        }
        linePaint.pathEffect = zoneDash
        when (zone) {
            is ControlZone.Box -> {
                val inset = 0.85f * unit // keeps the line whole where the zone runs to the screen's edge
                val left = max(x(zone.x), inset)
                val top = max(y(zone.y), inset)
                val right = kotlin.math.min(x(zone.right), viewWidth - inset)
                val bottom = kotlin.math.min(y(zone.bottom), viewHeight - inset)
                val corner = ballScreenRadius * ZONE_CORNER
                canvas.drawRoundRect(left, top, right, bottom, corner, corner, linePaint)
            }
            is ControlZone.Circle -> canvas.drawCircle(x(zone.x), y(zone.y), zone.radius.toFloat() * scale, linePaint)
        }
        linePaint.pathEffect = null
    }

    private var zoneDash: DashPathEffect? = null
    private var zoneDashUnit = 0f

    /** Radius of the ball on screen, in pixels. */
    val ballScreenRadius: Float get() = level.ballRadius.toFloat() * scale

    /**
     * The ball at screen position (sx, sy): a plain disc with the bounces it has left written in its centre.
     * The number is part of the ball: same position, same scale.
     */
    fun drawBall(
        canvas: Canvas, sx: Float, sy: Float, sizeFactor: Float, style: BallStyle, bouncesLeft: Int, alpha: Float = 1f,
        squash: Float = 0f, squashAngle: Float = 0f,
    ) {
        if (squash > 0f) {
            // Just after a hit the ball is pressed flat against the wall: shorter along the wall's normal (at
            // [squashAngle] degrees), a little wider across it. It is only the picture; the ball's body does not change.
            canvas.save()
            canvas.rotate(squashAngle, sx, sy)
            canvas.scale(1f - squash, 1f + 0.6f * squash, sx, sy)
            canvas.rotate(-squashAngle, sx, sy)
            drawBall(canvas, sx, sy, sizeFactor, style, bouncesLeft, alpha)
            canvas.restore()
            return
        }
        val r = ballScreenRadius * sizeFactor
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        if (style == BallStyle.HOLLOW) {
            val ring = r * 0.12f
            linePaint.pathEffect = null
            linePaint.shader = null
            linePaint.color = Palette.withAlpha(palette.accent, alpha)
            linePaint.strokeWidth = ring
            canvas.drawCircle(sx, sy, r - ring / 2, linePaint)
            numberPaint.color = Palette.withAlpha(palette.accent, alpha)
        } else {
            ballPaint.alpha = a
            canvas.drawCircle(sx, sy, r, ballPaint)
            numberPaint.color = Palette.withAlpha(palette.background, alpha)
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
        linePaint.shader = null
        linePaint.color = Palette.withAlpha(palette.background, 0.85f)
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
     * The swipe control's dial: a fine ring round the ball at (cx, cy) that shows the ball can be swiped, and, while a
     * swipe is under way ([active]), a thin arc filling it clockwise from the top with the swipe's [power] — like a
     * clock, so it never points anywhere.
     */
    fun drawSwipeDial(canvas: Canvas, cx: Float, cy: Float, unit: Float, active: Boolean, power: Float, ready: Boolean, alpha: Float = 1f) {
        val radius = ballScreenRadius * DIAL_RADIUS
        canvas.save()
        canvas.translate(cx, cy)
        zonePaint.alpha = (255 * alpha).toInt()
        canvas.drawCircle(0f, 0f, radius, zonePaint)
        zonePaint.alpha = 255
        canvas.restore()
        linePaint.pathEffect = null
        linePaint.shader = null
        linePaint.color = Palette.withAlpha(palette.accent, (if (active) 0.45f else 0.26f) * alpha)
        linePaint.strokeWidth = 1.5f * unit
        canvas.drawCircle(cx, cy, radius, linePaint)
        if (!active || power <= 0f) return
        val color = if (ready) palette.accent else Palette.withAlpha(palette.accent, 0.45f)
        linePaint.color = color
        linePaint.strokeWidth = 3f * unit
        arcBox.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f * power.coerceIn(0f, 1f)
        canvas.drawArc(arcBox, -90f, sweep, false, linePaint)
        val end = Math.toRadians((sweep - 90f).toDouble())
        fillPaint.color = color
        canvas.drawCircle(cx + radius * Math.cos(end).toFloat(), cy + radius * Math.sin(end).toFloat(), 3.4f * unit, fillPaint)
    }

    /**
     * The ball turned into a small fan at (cx, cy): a hub and four curved blades. [grow] (0..1) is
     * how far the blades have come out of the ball (the ball's number fades as they do); [angle]
     * is its turn in degrees; [blur] (0..1) is how fast it spins, which smears the blades into a
     * faint disc with a bright rim.
     */
    fun drawFan(canvas: Canvas, cx: Float, cy: Float, grow: Float, angle: Float, blur: Float, bouncesLeft: Int) {
        val r = ballScreenRadius
        val g = grow.coerceIn(0f, 1f)
        if (blur > 0f) {
            fillPaint.color = Palette.withAlpha(palette.accent, 0.12f * blur)
            canvas.drawCircle(cx, cy, r * 1.2f, fillPaint)
            linePaint.pathEffect = null
            linePaint.shader = null
            linePaint.color = Palette.withAlpha(palette.accent, 0.55f * blur)
            linePaint.strokeWidth = r * 0.05f
            canvas.drawCircle(cx, cy, r * 1.2f, linePaint)
        }
        canvas.save()
        canvas.translate(cx, cy)
        // Motion blur: fainter copies trailing behind the blades.
        val ghosts = if (blur > 0.05f) 3 else 1
        for (k in ghosts - 1 downTo 0) {
            canvas.save()
            canvas.rotate(angle - k * 22f * blur)
            ballPaint.alpha = if (k == 0) 255 else (255 * (0.4f / k) * blur).toInt()
            for (b in 0 until 4) {
                canvas.save()
                canvas.rotate(b * 90f)
                canvas.scale(g, g)
                canvas.drawPath(bladePx, ballPaint)
                canvas.restore()
            }
            canvas.restore()
        }
        ballPaint.alpha = 255
        canvas.drawCircle(0f, 0f, r * (1f - 0.58f * g), ballPaint)
        fillPaint.color = palette.background
        canvas.drawCircle(0f, 0f, r * 0.1f * g, fillPaint)
        canvas.restore()
        if (g < 1f) {
            val text = numbers[bouncesLeft.coerceIn(0, numbers.size - 1)]
            val size = r * (1f - 0.58f * g)
            numberPaint.color = Palette.withAlpha(palette.background, 1f - g)
            numberPaint.textSize = size * if (text.length == 1) 1.1f else 0.85f
            numberPaint.getTextBounds(text, 0, text.length, numberBounds)
            canvas.drawText(text, cx, cy - numberBounds.exactCenterY(), numberPaint)
        }
    }

    /** One fan blade in units of the ball's radius, pointing along +x: a curved petal. */
    private val blade = Path().apply {
        moveTo(0.25f, -0.16f)
        cubicTo(0.6f, -0.36f, 1.05f, -0.32f, 1.2f, -0.05f)
        cubicTo(1.12f, 0.12f, 0.62f, 0.2f, 0.25f, 0.14f)
        close()
    }

    /** The blade at the ball's size on screen. */
    private val bladePx = Path()

    private val outlines = HashMap<Int, Path>()

    /**
     * A thin glowing outline, a little outside obstacle [index], to point it out; [strength] (0..1)
     * sets how bright. The outline follows the obstacle's exact rounded shape.
     */
    fun drawObstacleOutline(canvas: Canvas, index: Int, unit: Float, strength: Float) {
        val path = outlines.getOrPut(index) { outlineOf(level.obstacles[index], gap = 5f * unit) }
        linePaint.pathEffect = null
        linePaint.shader = null
        linePaint.color = Palette.withAlpha(palette.accent, 0.2f * strength)
        linePaint.strokeWidth = 7f * unit
        canvas.drawPath(path, linePaint)
        linePaint.color = Palette.withAlpha(palette.accent, 0.85f * strength)
        linePaint.strokeWidth = 2f * unit
        canvas.drawPath(path, linePaint)
    }

    private fun outlineOf(obstacle: Obstacle, gap: Float): Path {
        val src = Path()
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val core = Path()
        when (obstacle) {
            is Wall -> {
                addPolyline(src, obstacle.points, obstacle.closed)
                stroke.strokeWidth = obstacle.thickness.toFloat() * scale + 2 * gap
            }
            is Block -> {
                addPolyline(src, obstacle.core, closed = true)
                addPolyline(core, obstacle.core, closed = true)
                stroke.strokeWidth = (obstacle.radius * 2).toFloat() * scale + 2 * gap
            }
        }
        val region = Path()
        @Suppress("DEPRECATION")
        stroke.getFillPath(src, region)
        // Merging with the block's own shape keeps only the outer edge.
        val out = Path()
        return if (out.op(region, core, Path.Op.UNION)) out else region
    }

    /** A shot's path through [points], optionally continued to the ball's current screen position. */
    fun drawShotPath(canvas: Canvas, points: List<Vec2>, endX: Float, endY: Float, color: Int, dashed: Boolean) {
        if (points.isEmpty()) return
        trailPath.reset()
        var pen = false // false: the next point starts a new stroke (the first, or the one after a portal jump)
        for (p in points) {
            if (p.x.isNaN()) {
                pen = false
                continue
            }
            if (pen) trailPath.lineTo(x(p.x), y(p.y)) else trailPath.moveTo(x(p.x), y(p.y))
            pen = true
        }
        if (!endX.isNaN() && pen) trailPath.lineTo(endX, endY)
        linePaint.shader = null
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
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        /** The swipe dial's radius, in ball radii. */
        const val DIAL_RADIUS = 1.7f

        /** How strong the control zone's line is at full visibility, and how round its corners are (in ball radii). */
        const val ZONE_LINE_ALPHA = 0.34f

        /** The goal's fine outer ring, in goal radii. */
        const val OUTER_RING = 1.22f
        const val ZONE_CORNER = 1f
    }
}
