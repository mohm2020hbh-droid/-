package com.carom.game.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.carom.core.level.Block
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
 * The look is soft and light, with a little depth: the ground is gently lit from the middle, the
 * obstacles are smooth rounded bars and panels, shaded as if lit from above with a crisp light edge
 * and a soft shadow, the ball is a small glossy sphere and the goal ring glows faintly. What is
 * drawn is exactly what the ball bounces off: round ends and round corners are physics too.
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

    // Shaders for the moving parts, built at the origin once per layout and moved into place.
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        ballPaint.shader = RadialGradient(
            -0.35f * r, -0.4f * r, 1.45f * r,
            intArrayOf(Palette.blend(palette.accent, WHITE, 0.75f), palette.accent, Palette.blend(palette.accent, BLACK, 0.22f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        ballShadowPaint.shader = RadialGradient(
            0f, 0f, 1.1f * r, intArrayOf(Palette.withAlpha(BLACK, 0.4f), Palette.withAlpha(BLACK, 0f)), null, Shader.TileMode.CLAMP,
        )
        val g = level.goalRadius.toFloat() * scale
        goalGlowPaint.shader = RadialGradient(
            0f, 0f, 1.8f * g,
            intArrayOf(Palette.withAlpha(palette.accent, 0.07f), Palette.withAlpha(palette.accent, 0.05f), Palette.withAlpha(palette.accent, 0.2f), Palette.withAlpha(palette.accent, 0f)),
            floatArrayOf(0f, 0.5f, 0.57f, 1f), Shader.TileMode.CLAMP,
        )
        val z = zoneScreenRadius
        zonePaint.shader = RadialGradient(
            0f, 0f, z,
            intArrayOf(Palette.withAlpha(palette.accent, 0.075f), Palette.withAlpha(palette.accent, 0.04f), Palette.withAlpha(palette.accent, 0.015f)),
            floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP,
        )

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
        // The ground: gently lighter in the middle, deepening towards the edges.
        val ground = Paint().apply {
            shader = RadialGradient(
                w / 2, h * 0.42f, hypot(w, h) * 0.62f,
                intArrayOf(Palette.blend(palette.background, WHITE, 0.07f), palette.background, Palette.blend(palette.background, BLACK, 0.32f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, ground)

        // On screens shaped differently from the level, the space outside it is a deeper shade.
        val left = originX
        val top = originY
        val right = originX + level.width.toFloat() * scale
        val bottom = originY + level.height.toFloat() * scale
        val outside = Paint().apply { color = Palette.withAlpha(palette.void, 0.85f) }
        if (top > 0.5f) {
            canvas.drawRect(0f, 0f, w, top, outside)
            canvas.drawRect(0f, bottom, w, h, outside)
        }
        if (left > 0.5f) {
            canvas.drawRect(0f, top, left, bottom, outside)
            canvas.drawRect(right, top, w, bottom, outside)
        }

        paintShadows(canvas)
        for (o in level.obstacles) {
            when (o) {
                is Wall -> paintWall(canvas, o)
                is Block -> paintBlock(canvas, o)
            }
        }
    }

    /** One soft shadow under everything, falling a little below. */
    private fun paintShadows(canvas: Canvas) {
        val t = level.wallThickness.toFloat() * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.withAlpha(BLACK, 0.42f)
            maskFilter = BlurMaskFilter(max(1f, t * 0.35f), BlurMaskFilter.Blur.NORMAL)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.save()
        canvas.translate(0f, t * 0.28f)
        val path = Path()
        for (o in level.obstacles) {
            path.reset()
            when (o) {
                is Wall -> {
                    addPolyline(path, o.points, o.closed)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = o.thickness.toFloat() * scale
                    canvas.drawPath(path, paint)
                }
                is Block -> {
                    addPolyline(path, o.core, closed = true)
                    paint.style = Paint.Style.FILL_AND_STROKE
                    paint.strokeWidth = (o.radius * 2).toFloat() * scale
                    canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restore()
    }

    /**
     * A wall: a smooth rod with round ends, shaded across its width as if lit from above (light on
     * top, deeper underneath), inside a crisp light edge, with a soft gloss along its top.
     */
    private fun paintWall(canvas: Canvas, wall: Wall) {
        val t = wall.thickness.toFloat() * scale
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = t
            color = rimColor
        }
        val path = Path()
        addPolyline(path, wall.points, wall.closed)
        canvas.drawPath(path, edge)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = max(1f, t - 2f * rimWidth)
        }
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = t * 0.12f
            color = Palette.withAlpha(WHITE, 0.22f)
        }
        val count = if (wall.closed) wall.points.size else wall.points.size - 1
        for (i in 0 until count) {
            val a = wall.points[i]
            val b = wall.points[(i + 1) % wall.points.size]
            val ax = x(a.x)
            val ay = y(a.y)
            val bx = x(b.x)
            val by = y(b.y)
            val len = hypot(bx - ax, by - ay)
            if (len < 1e-3f) continue
            val ux = (bx - ax) / len
            val uy = (by - ay) / len
            // The side facing the light (from above, slightly left).
            var nx = -uy
            var ny = ux
            if (nx * -0.3f + ny * -1f < 0f) {
                nx = -nx
                ny = -ny
            }
            val mx = (ax + bx) / 2
            val my = (ay + by) / 2
            body.shader = LinearGradient(
                mx + nx * t / 2, my + ny * t / 2, mx - nx * t / 2, my - ny * t / 2,
                intArrayOf(lightColor, palette.primary, darkColor), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawLine(ax, ay, bx, by, body)
            if (len > t) {
                val inset = t * 0.4f
                val gx = nx * t * 0.24f
                val gy = ny * t * 0.24f
                canvas.drawLine(ax + ux * inset + gx, ay + uy * inset + gy, bx - ux * inset + gx, by - uy * inset + gy, gloss)
            }
        }
    }

    /** A block: a round-cornered panel shaded from light at the top to deeper at the bottom, inside a crisp light edge. */
    private fun paintBlock(canvas: Canvas, block: Block) {
        val path = Path()
        addPolyline(path, block.core, closed = true)
        val r = block.radius.toFloat() * scale
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2 * r
            color = rimColor
        }
        canvas.drawPath(path, edge)
        val top = y(block.core.minOf { it.y }) - r
        val bottom = y(block.core.maxOf { it.y }) + r
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (r > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(0f, 2 * r - 2 * rimWidth)
            shader = LinearGradient(
                0f, top, 0f, bottom, intArrayOf(lightColor, palette.primary, darkColor), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
        if (r > rimWidth) {
            canvas.drawPath(path, body)
        } else {
            // Too small a rounding to inset the edge by; the shading covers the whole shape.
            body.strokeWidth = 2 * r
            canvas.drawPath(path, body)
        }
    }

    /** The ground's colour at the screen's edge: what shows if the board is nudged by a shake. */
    val edgeColor: Int = Palette.blend(palette.background, BLACK, 0.32f)

    private val lightColor = Palette.blend(palette.primary, WHITE, 0.26f)
    private val darkColor = Palette.blend(palette.primary, BLACK, 0.3f)
    private val rimColor = Palette.blend(palette.primary, WHITE, 0.42f)
    private val rimWidth: Float get() = max(1f, 1.4f * scale)

    // ---------------------------------------------------------------- moving parts

    /**
     * The goal: a ring with a faint glow and a centre point. [pulse] (0..1) animates the scoring ripple;
     * [beat] (0..1) is the music's pulse, which lifts the glow a little on each beat (it only ever affects the picture).
     * An exit that needs several balls shows small pips under it, [entered] of them filled.
     */
    fun drawGoal(canvas: Canvas, pulse: Float, beat: Float = 0f, entered: Int = 0, needed: Int = 1) {
        val cx = x(level.goal.x)
        val cy = y(level.goal.y)
        val r = level.goalRadius.toFloat() * scale
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(1f + 0.06f * beat, 1f + 0.06f * beat)
        goalGlowPaint.alpha = (255 * (0.85f + 0.15f * beat)).toInt()
        canvas.drawCircle(0f, 0f, 1.8f * r, goalGlowPaint)
        goalGlowPaint.alpha = 255
        canvas.restore()
        if (needed > 1) {
            val gap = r * 0.34f
            val startX = cx - gap * (needed - 1) / 2f
            for (i in 0 until needed) {
                fillPaint.color = if (i < entered) palette.accent else Palette.withAlpha(palette.accent, 0.25f)
                canvas.drawCircle(startX + i * gap, cy + r * 1.35f, r * 0.09f, fillPaint)
            }
        }
        val ring = r * 0.14f
        linePaint.pathEffect = null
        linePaint.shader = null
        linePaint.color = palette.accent
        linePaint.strokeWidth = ring
        canvas.drawCircle(cx, cy, r - ring / 2, linePaint)
        fillPaint.color = palette.accent
        canvas.drawCircle(cx, cy, r * 0.16f, fillPaint)
        if (pulse > 0f) {
            linePaint.color = Palette.withAlpha(palette.accent, 1f - pulse)
            linePaint.strokeWidth = ring * (1f - pulse * 0.6f)
            canvas.drawCircle(cx, cy, r * (1f + pulse * 1.2f), linePaint)
        }
    }

    /** Radius of the ball on screen, in pixels. */
    val ballScreenRadius: Float get() = level.ballRadius.toFloat() * scale

    /** Radius of the launch zone on screen: everywhere the ball itself can reach while being held. */
    val zoneScreenRadius: Float get() = ((level.launchZone + level.ballRadius) * 1.0).toFloat() * scale

    /**
     * The ball at screen position (sx, sy): a small glossy sphere with the bounces it has left
     * written in its centre. The number is part of the ball: same position, same scale.
     */
    fun drawBall(canvas: Canvas, sx: Float, sy: Float, sizeFactor: Float, style: BallStyle, bouncesLeft: Int, alpha: Float = 1f) {
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
            canvas.save()
            canvas.translate(sx, sy)
            canvas.scale(sizeFactor, sizeFactor)
            val base = ballScreenRadius
            ballShadowPaint.alpha = a
            canvas.drawCircle(base * 0.06f, base * 0.24f, base * 1.1f, ballShadowPaint)
            ballPaint.alpha = a
            canvas.drawCircle(0f, 0f, base, ballPaint)
            canvas.restore()
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
     * The launch zone: a soft disc with a fine ring, centred on the level's start, covering
     * everywhere the ball can be moved to before a throw, with a small mark at the start. While the
     * ball is held ([active]), the ring brightens and a thin arc fills it clockwise from the top
     * with the throw's [power], like a dial, so it never points anywhere.
     */
    fun drawLaunchZone(canvas: Canvas, unit: Float, active: Boolean, power: Float, ready: Boolean) {
        val cx = x(level.ball.x)
        val cy = y(level.ball.y)
        val radius = zoneScreenRadius
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, radius, zonePaint)
        canvas.restore()
        linePaint.pathEffect = null
        linePaint.shader = null
        linePaint.color = Palette.withAlpha(palette.accent, if (active) 0.45f else 0.26f)
        linePaint.strokeWidth = 1.5f * unit
        canvas.drawCircle(cx, cy, radius, linePaint)
        linePaint.color = Palette.withAlpha(palette.accent, 0.3f)
        linePaint.strokeWidth = unit
        canvas.drawCircle(cx, cy, 4f * unit, linePaint)
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
     * The swipe control's dial: a fine ring round the ball at (cx, cy) that shows the ball can be swiped, and, while a
     * swipe is under way ([active]), a thin arc filling it clockwise from the top with the swipe's [power] — like a
     * clock, so it never points anywhere.
     */
    fun drawSwipeDial(canvas: Canvas, cx: Float, cy: Float, unit: Float, active: Boolean, power: Float, ready: Boolean, alpha: Float = 1f) {
        val radius = ballScreenRadius * 1.7f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(radius / zoneScreenRadius, radius / zoneScreenRadius)
        zonePaint.alpha = (255 * alpha).toInt()
        canvas.drawCircle(0f, 0f, zoneScreenRadius, zonePaint)
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
    }
}
