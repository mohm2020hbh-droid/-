package com.fliperror.web

import com.fliperror.core.*
import org.w3c.dom.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dark neon geometric renderer. GDD 11: readability outranks graphics.
 *
 * Everything decorative here obeys one rule: it lives BEHIND the runner. Trail
 * ghosts, sparks and dust are all culled at the runner's leading edge, so no
 * amount of effect can ever sit on top of a spike the player has not reached
 * yet. The effects say how fast and how high; the level says what will kill you.
 */
class Renderer(private val ctx: CanvasRenderingContext2D) {

    // GDD 11.2: hot colours are reserved for death, everywhere, always.
    private val hazard = Palette.HAZARD
    private val hazardDim = Palette.HAZARD_DIM
    private val safe = Palette.SAFE
    private val safeFill = Palette.SAFE_FILL
    private val gold = Palette.COIN
    private val boost = Palette.BOOST
    private val finish = Palette.FINISH

    /**
     * What the player is wearing. Set by the shell from saved progress.
     *
     * Named Skin rather than Look because the core now has a Look of its own -
     * what a HAZARD is made of - and a nested class quietly shadowing an imported
     * enum is the kind of collision that produces ten unresolved references and
     * no clue which of the two names is wrong.
     */
    class Skin {
        var shape = "shape.square"
        var colour = "color.yellow"
        var trail = "trail.basic"
        var face = "face.classic"
    }
    val look = Skin()
    private val player get() = Palette.player(look.colour)

    /** Visible world height in units. Keeps ~2.2s of track ahead of the runner. */
    private val viewHeight = 13.0
    /** Floor on the world width shown, so a narrow frame cannot hide the track ahead. */
    private val minViewWidth = 18.0
    private val playerScreenFraction = 0.22

    private var camY = 0.0
    private var groundRefY = 0.0

    // [x, y, life, maxLife, rotation]
    private val trail = ArrayList<DoubleArray>()
    // [x, y, vx, vy, life, maxLife, size, kind]  kind 0 = chip, 1 = dot, 2 = streak
    private val parts = ArrayList<DoubleArray>()
    // [x, y, life, maxLife]
    private val rings = ArrayList<DoubleArray>()
    private val shards = ArrayList<DoubleArray>()
    private var shardsSpawned = false

    // --- feel state -------------------------------------------------------
    /** Positive stretches the runner tall and thin, negative squashes it wide. */
    private var stretch = 0.0
    private var runPhase = 0.0
    private var kickY = 0.0
    private var emitTimer = 0.0
    private var trailTimer = 0.0
    /** Screen tint after a jump: [flash] is its life, [flashBoost] picks the colour. */
    private var flash = 0.0
    private var flashMax = 0.12
    private var flashBoost = false
    private var prevStars = 0
    private var prevNear = 0
    /** Counts down after a boost; while it runs the trail is longer and brighter. */
    private var boostGlow = 0.0

    private var prevGrounded = true
    private var prevDoubles = 0
    private var wasRunning = true

    var w = 0.0; var h = 0.0
    /** Banked coins, for the in-run counter. Star coins picked up now add to it live. */
    var coins = 0
    /** Set once by the shell; the tint and the spinner both sit this one out. */
    var reducedMotion = false
    /**
     * Whether the world underfoot is sand. Set from the level's own theme, and
     * read by the parts of the renderer that should behave differently in it -
     * dust that is heavier than neon, a trail that carries grit, a shadow lit by
     * a sun rather than by signage. It changes how things look and move, never
     * what they mean: danger is the same red in both worlds.
     */
    private val sandy get() = theme.scene == Scene.DESERT
    private val deep get() = theme.scene == Scene.ABYSS

    /** Player choice: drops reflections, windows and half the particles. */
    var reduceEffects = false
    /** Hazards get a white-hot core that no kind of colour vision can miss. */
    var colorblind = false
    var theme: Theme = Theme.forLevel(1)

    private var scale = 60.0
    /** The height the camera scale corresponds to. Equals h in landscape; in a
     *  frame the width floor has zoomed out, it is what the UI must size against,
     *  or a tall frame blows the HUD up until the labels collide. */
    private var uiH = 0.0
    private var originX = 0.0

    fun resetRun() {
        trail.clear(); parts.clear(); rings.clear(); shards.clear()
        shardsSpawned = false
        stretch = 0.0; kickY = 0.0; boostGlow = 0.0
        emitTimer = 0.0; trailTimer = 0.0; flash = 0.0; prevStars = 0; prevNear = 0
        prevGrounded = true; prevDoubles = 0; wasRunning = true
    }

    /** World units visible across the frame. Asserted by the playtest. */
    val visibleWorldWidth: Double get() = if (scale > 0.0) w / scale else 0.0

    /**
     * How far ahead of the runner the player can actually SEE, in world units.
     *
     * This is the number a jump has to fit inside. A take-off whose landing is
     * past this edge is a blind jump: the player is asked to commit to geometry
     * that is not on the screen yet, which is indistinguishable from an unfair
     * level no matter how wide the solver says the window is.
     */
    val aheadUnits: Double get() = if (scale > 0.0) (w - originX) / scale else 0.0

    /** The height the UI is sized against. Asserted by the playtest. */
    val uiHeight: Double get() = uiH

    /** Live decoration count. The playtest watches this to prove the trail moves. */
    val effectCount: Int get() = trail.size + parts.size + rings.size

    /** Newest and oldest ghost. The playtest reads these to prove the trail is a
     *  spread of moving copies rather than one sprite pinned to the runner. */
    val trailHeadX: Double get() = if (trail.isEmpty()) Double.NaN else trail[trail.size - 1][0]
    val trailTailX: Double get() = if (trail.isEmpty()) Double.NaN else trail[0][0]

    // --- emission ---------------------------------------------------------

    private fun rnd(a: Double, b: Double) = a + Random.nextDouble() * (b - a)

    private fun spark(x: Double, y: Double, vx: Double, vy: Double, life: Double, size: Double, kind: Int) {
        if (parts.size >= 240) return          // a hard ceiling keeps the frame budget honest
        parts.add(doubleArrayOf(x, y, vx, vy, life, life, size, kind.toDouble()))
    }

    private fun burst(g: Game, count: Int, power: Double, spread: Double, up: Double) {
        val cx = g.x + 0.5
        val cy = g.y + 0.16
        for (i in 0 until count) {
            val a = PI + rnd(-spread, spread)          // backwards, always
            val sp = power * rnd(0.55, 1.0)
            spark(cx, cy, cos(a) * sp, sin(a) * sp + up, rnd(0.24, 0.50),
                rnd(0.15, 0.32), if (i % 3 == 0) 1 else 0)
        }
    }

    fun update(g: Game, dt: Double) {
        if (g.grounded) groundRefY = g.y
        // The camera tracks the floor, not the runner, so a jump never moves the
        // frame during a precision beat (GDD 1.5).
        val target = if (g.y < groundRefY - 4.0) g.y else groundRefY
        camY += (target - camY) * min(1.0, dt * 9.0)

        // --- transitions, read from the sim rather than pushed into us ------
        val justJumped = prevGrounded && !g.grounded && g.vy > 0.0
        val justDoubled = g.doubleJumps != prevDoubles
        val justLanded = !prevGrounded && g.grounded
        val restarted = !wasRunning && g.state == GameState.RUNNING

        if (restarted) resetRun()
        if (justJumped) {
            stretch = 0.22; kickY = 1.6; burst(g, 9, 4.5, 0.85, 1.2)
            flash = 0.085; flashMax = 0.085; flashBoost = false
        }
        if (justDoubled) {
            stretch = 0.42
            kickY = 3.4
            boostGlow = 0.42
            burst(g, 20, 7.5, 1.25, 2.4)
            rings.add(doubleArrayOf(g.x + 0.5, g.y + 0.5, 0.26, 0.26))
            flash = 0.115; flashMax = 0.115; flashBoost = true
            // a few long streaks so the second jump reads even in a still frame
            repeat(5) { spark(g.x + 0.5, g.y + 0.4, rnd(-9.0, -4.0), rnd(-1.0, 2.5), rnd(0.26, 0.42), 0.30, 2) }
        }
        if (justLanded) { stretch = -0.30; kickY = 2.2; burst(g, 8, 4.0, 0.55, 0.5) }

        // A close pass throws a spark off the side the runner nearly clipped. It
        // is the only decoration in the game that is INFORMATION: it says "that
        // was the margin", and a player who sees it twice in a row knows they are
        // reading the level correctly and cutting it fine, which is exactly the
        // state this game wants them in.
        if (g.nearMisses != prevNear) {
            prevNear = g.nearMisses
            repeat(10) {
                val a = PI * (0.85 + rnd(-0.30, 0.30))
                val sp = rnd(4.0, 9.0)
                spark(g.x + 0.5, g.y + 0.35, cos(a) * sp, sin(a) * sp + rnd(0.5, 3.0),
                    rnd(0.18, 0.34), rnd(0.10, 0.20), 2)
            }
        }

        if (g.starsCollected != prevStars) {
            prevStars = g.starsCollected
            rings.add(doubleArrayOf(g.x + 0.5, g.y + 0.5, 0.30, 0.30))
            for (i in 0 until 16) {
                val a = i * PI * 2 / 16
                spark(g.x + 0.5, g.y + 0.5, cos(a) * 5.5, sin(a) * 5.5 + 1.5, rnd(0.30, 0.55), 0.20, 1)
            }
        }
        prevGrounded = g.grounded
        prevDoubles = g.doubleJumps
        wasRunning = g.state == GameState.RUNNING

        // --- decay ----------------------------------------------------------
        stretch += (0.0 - stretch) * min(1.0, dt * 13.0)
        kickY += (0.0 - kickY) * min(1.0, dt * 11.0)
        flash = max(0.0, flash - dt)
        boostGlow = max(0.0, boostGlow - dt)
        if (g.grounded && g.state == GameState.RUNNING) runPhase += dt * Tuning.RUN_SPEED / 1.35

        if (g.state == GameState.RUNNING) {
            // Ghosts are sampled on a clock, not per frame, so the spacing reads
            // the same whether the device is running at 60 or 120.
            trailTimer -= dt
            if (trailTimer <= 0.0) {
                // Spaced far enough apart to read as separate copies. Sampled any
                // tighter and the ghosts smear into one dark smudge behind the
                // runner, which is the opposite of what a motion trail is for.
                trailTimer = 0.030
                val life = if (boostGlow > 0.0) 0.50 else 0.34
                trail.add(doubleArrayOf(g.x, g.y, life, life, g.rotationDeg))
                if (trail.size > 40) trail.removeAt(0)
            }
            // Ground dust while running, thinner air-dust while flying.
            emitTimer -= dt
            if (emitTimer <= 0.0) {
                emitTimer = if (g.grounded) 0.042 else 0.075
                val y = if (g.grounded) g.y + 0.06 else g.y + rnd(0.15, 0.8)
                if (sandy && g.grounded) {
                    // A kicked-up sheet of sand: thrown higher, spread wider, and
                    // it falls back rather than streaking away.
                    spark(g.x + rnd(0.0, 0.5), y, rnd(-5.5, -2.0), rnd(1.4, 4.2),
                        rnd(0.30, 0.55), rnd(0.09, 0.17), 3)
                    if (Random.nextInt(3) == 0)
                        spark(g.x + rnd(0.0, 0.5), y, rnd(-7.0, -4.0), rnd(0.4, 1.6),
                            rnd(0.22, 0.38), rnd(0.07, 0.13), 3)
                } else {
                    spark(g.x + rnd(0.05, 0.4), y, rnd(-6.5, -3.0), rnd(0.2, 1.9),
                        rnd(0.22, 0.40), rnd(0.10, 0.20), if (Random.nextInt(4) == 0) 2 else 1)
                }
            }
            shardsSpawned = false
        } else if (g.state == GameState.DEAD && !shardsSpawned) {
            shardsSpawned = true
            // The trail does not fade on death, it BREAKS. Every ghost still
            // behind the runner becomes a fragment thrown from where it was
            // standing, so the last thing on screen is the shape of the run that
            // just ended rather than a shape quietly evaporating.
            trail.forEachIndexed { k, t ->
                val a = PI * (0.7 + (k % 7) * 0.09)
                shards.add(doubleArrayOf(t[0] + 0.5, t[1] + 0.5,
                    cos(a) * (2.0 + k % 4), sin(a) * (2.0 + k % 3) + 2.2, 0.55))
            }
            trail.clear()
            for (k in 0 until 26) {
                val a = k * 0.2417 * PI * 2
                val sp = 3.0 + (k % 5)
                shards.add(doubleArrayOf(g.deathX + 0.5, g.deathY + 0.5, cos(a) * sp, sin(a) * sp + 3.0, 0.7))
            }
        }

        // --- integrate the decoration ---------------------------------------
        var i = 0
        while (i < trail.size) {
            trail[i][2] -= dt
            if (trail[i][2] <= 0) trail.removeAt(i) else i++
        }
        i = 0
        while (i < parts.size) {
            val p = parts[i]
            p[0] += p[2] * dt
            p[1] += p[3] * dt
            // Sand has weight. Neon dust hangs in the air and drifts; grit thrown
            // up off a dune arcs and comes back down, and making the two obey the
            // same number was the quickest way to make the desert look like the
            // city with a filter on it.
            p[3] -= (if (sandy) 17.0 else 9.0) * dt
            p[2] *= 1.0 - min(1.0, dt * 2.2)
            p[4] -= dt
            if (p[4] <= 0) parts.removeAt(i) else i++
        }
        i = 0
        while (i < rings.size) {
            rings[i][2] -= dt
            if (rings[i][2] <= 0) rings.removeAt(i) else i++
        }
        i = 0
        while (i < shards.size) {
            val s = shards[i]
            s[0] += s[2] * dt; s[1] += s[3] * dt; s[3] -= 22.0 * dt; s[4] -= dt
            if (s[4] <= 0) shards.removeAt(i) else i++
        }
    }

    private fun sx(x: Double) = originX + (x - camX) * scale
    private fun sy(y: Double) = h * 0.62 - (y - camY) * scale
    private var camX = 0.0

    fun draw(g: Game) {
        // Height sets the scale, as it always has: every landscape aspect from 3:2
        // up is wide enough that the floor below never binds. It binds only in a
        // frame narrow for its height, where scaling off height alone would leave
        // the runner barely a second of visible track and make deaths unreadable.
        scale = min(h / viewHeight, w / minViewWidth)
        uiH = scale * viewHeight
        takenStars = g.takenStarIndices()
        levelTime = g.elapsed
        originX = w * playerScreenFraction
        camX = g.x

        drawBackground(g)
        // Between the scenery and the level, and nowhere else: the storm veils
        // the world and never the play.
        drawStorm(g.level, g)
        ctx.save()
        ctx.translate(0.0, kickY)
        drawLevel(g.level)
        drawFloorMirror(g)
        drawReflection(g)
        drawShadow(g)
        drawTrail(g)
        drawParticles(g)
        drawRings(g)
        if (g.state != GameState.DEAD) drawPlayer(g)
        drawShards()
        ctx.restore()
        drawFlash()
        drawHud(g)
        if (g.state == GameState.DEAD) drawDeath(g)
        if (g.state == GameState.COMPLETE) drawComplete(g)
    }

    /** Stable pseudo-random per index: the skyline is the same every run. */
    private fun hash(i: Int): Double {
        var x = i * 374761393
        x = (x xor (x shr 13)) * 1274126177
        return ((x xor (x shr 16)) and 0x7fffffff) / 2147483647.0
    }

    /**
     * One band of city. Parallax sets how fast it slides, which is the whole
     * depth cue; everything else is there to make the band read as buildings
     * rather than as a bar chart.
     */
    private fun skyline(
        g: Game, parallax: Double, spanUnits: Double, base: Double, rise: Double,
        colour: String, alpha: Double, windows: Boolean, seed: Int,
    ) {
        val horizon = h * 0.645
        val span = spanUnits * scale
        if (span < 1.0) return
        val shift = (g.x * parallax * scale) % span
        var i = -1
        ctx.globalAlpha = alpha
        while (i * span - shift < w + span) {
            val bx = i * span - shift
            val r = hash(i + seed)
            val bh = h * (base + rise * r)
            val bw = span * (0.60 + 0.28 * hash(i + seed + 977))
            ctx.fillStyle = colour
            ctx.fillRect(bx, horizon - bh, bw, bh)
            if (windows && !reduceEffects && bw > 10) {
                // Lit windows, on a grid, skipped pseudo-randomly. Cheap, and it
                // is what stops a silhouette looking like a cardboard cut-out.
                ctx.fillStyle = theme.horizon
                val step = maxOf(6.0, bw / 4.0)
                var wy = horizon - bh + step
                var k = 0
                while (wy < horizon - step * 0.6) {
                    var wx = bx + step * 0.45
                    while (wx < bx + bw - step * 0.5) {
                        val lit = hash(i * 733 + k + seed)
                        if (lit > 0.58) {
                            // A city is not lit in one colour. Most windows take the
                            // world's own light, a few take the sign colour.
                            ctx.fillStyle = if (lit > 0.93) theme.accent else theme.horizon
                            ctx.globalAlpha = alpha * (0.45 + 1.0 * hash(k * 31 + i))
                            ctx.fillRect(wx, wy, step * 0.28, step * 0.34)
                        }
                        wx += step; k++
                    }
                    wy += step
                }
                ctx.globalAlpha = alpha
            }
            i++
        }
        ctx.globalAlpha = 1.0
    }

    /** Neon signage. A few per screen, far enough apart to read as landmarks. */
    private fun billboards(g: Game, parallax: Double, seed: Int) {
        val horizon = h * 0.645
        val span = 26.0 * scale
        if (span < 1.0) return
        val shift = (g.x * parallax * scale) % span
        var i = -1
        while (i * span - shift < w + span) {
            val bx = i * span - shift
            val r = hash(i + seed)
            val bw = span * (0.14 + 0.08 * r)
            val bh = bw * (1.1 + 0.9 * hash(i + seed + 41))
            val by = horizon - h * (0.20 + 0.30 * hash(i + seed + 83))
            val tint = when {
                r > 0.66 -> theme.billboard
                r > 0.33 -> theme.accent
                else -> theme.horizon
            }
            ctx.globalAlpha = 0.30
            ctx.fillStyle = tint
            ctx.fillRect(bx, by, bw, bh)
            ctx.globalAlpha = 0.85
            ctx.strokeStyle = tint
            ctx.lineWidth = 2.0
            ctx.shadowBlur = if (reduceEffects) 0.0 else 14.0
            ctx.shadowColor = tint
            ctx.strokeRect(bx, by, bw, bh)
            ctx.shadowBlur = 0.0
            i++
        }
        ctx.globalAlpha = 1.0
    }

    private fun drawBackground(g: Game) {
        val grad = ctx.createLinearGradient(0.0, 0.0, 0.0, h)
        grad.addColorStop(0.0, theme.skyTop)
        grad.addColorStop(0.52, theme.skyMid)
        grad.addColorStop(1.0, theme.skyLow)
        ctx.fillStyle = grad
        ctx.fillRect(0.0, 0.0, w, h)

        when (theme.scene) {
            Scene.DESERT -> drawDesert(g)
            Scene.ABYSS -> drawAbyss(levelTime)
            Scene.CITY -> drawCity(g)
        }

        // Readability vignette behind the play line. Everything above is scenery;
        // from here down the only things allowed to be bright are the level.
        val v = ctx.createLinearGradient(0.0, h * 0.16, 0.0, h * 0.95)
        v.addColorStop(0.0, "rgba(5,6,15,0)")
        v.addColorStop(0.38, "rgba(5,6,15,0.62)")
        v.addColorStop(0.62, "rgba(5,6,15,0.90)")
        v.addColorStop(1.0, "rgba(5,6,15,0.97)")
        ctx.fillStyle = v
        ctx.fillRect(0.0, h * 0.16, w, h * 0.84)
    }

    /**
     * A sun going down behind broken ruins, over dunes that never stop moving.
     *
     * Nothing about this is the city with different colours: the silhouette is
     * curves instead of rectangles, the light comes from one huge low source
     * instead of a thousand windows, and the air itself is full of sand. That is
     * the point - a world the player can name from one frame.
     */
    private fun drawDesert(g: Game) {
        val horizon = h * 0.645

        // The sun. Low, enormous, and banded the way a heat-hazed one looks.
        if (!reduceEffects) {
            val cx = w * 0.66 - (g.x * 0.012 * scale) % (w * 2.2)
            val cy = horizon - h * 0.16
            val r = h * 0.30
            val halo = ctx.createRadialGradient(cx, cy, r * 0.6, cx, cy, r * 2.1)
            halo.addColorStop(0.0, theme.sun + "55")
            halo.addColorStop(1.0, "rgba(0,0,0,0)")
            ctx.fillStyle = halo
            ctx.fillRect(0.0, 0.0, w, horizon)

            ctx.save()
            ctx.beginPath(); ctx.rect(0.0, 0.0, w, horizon); ctx.clip()
            val disc = ctx.createLinearGradient(0.0, cy - r, 0.0, cy + r)
            disc.addColorStop(0.0, theme.sunCore)
            disc.addColorStop(0.55, theme.sun)
            disc.addColorStop(1.0, theme.billboard)
            ctx.fillStyle = disc
            ctx.beginPath(); ctx.arc(cx, cy, r, 0.0, PI * 2); ctx.fill()
            // the bands: cut the lower half with sky-coloured slices
            ctx.fillStyle = theme.skyMid
            var band = 0
            while (band < 7) {
                val by = cy + r * (0.12 + band * 0.13)
                val bh = r * (0.012 + band * 0.010)
                ctx.globalAlpha = 0.85
                ctx.fillRect(cx - r, by, r * 2, bh)
                band++
            }
            ctx.globalAlpha = 1.0
            ctx.restore()
        }

        // Far ruins: broken verticals, leaning, nothing square.
        ruins(g, 0.07, 7.0, 0.10, 0.16, theme.far, 0.60, 13)
        ruins(g, 0.15, 4.6, 0.13, 0.20, theme.mid, 0.68, 401)

        // Dunes. Two layers of slow curves, the near one darker and faster.
        dune(g, 0.10, 0.052, 1.6, theme.near, 0.55, 0.0)
        dune(g, 0.22, 0.040, 2.7, theme.skyLow, 0.85, 1.7)

        // Blowing sand: fast, low, and always moving, so the air is never still.
        if (!reduceEffects) {
            ctx.globalAlpha = 0.20
            ctx.fillStyle = theme.billboard
            val span = 9.0 * scale
            if (span > 1.0) {
                val shift = (g.x * 1.9 * scale) % span
                var i = -1
                while (i * span - shift < w + span) {
                    val bx = i * span - shift
                    val r0 = hash(i + 77)
                    val by = horizon - h * (0.02 + 0.20 * r0)
                    ctx.fillRect(bx, by, span * (0.18 + 0.30 * r0), 1.5)
                    i++
                }
            }
            ctx.globalAlpha = 1.0
        }
    }

    /** Broken columns and arches. Leaning, snapped off, never a clean rectangle. */
    private fun ruins(
        g: Game, parallax: Double, spanUnits: Double, base: Double, rise: Double,
        colour: String, alpha: Double, seed: Int,
    ) {
        val horizon = h * 0.645
        val span = spanUnits * scale
        if (span < 1.0) return
        val shift = (g.x * parallax * scale) % span
        ctx.globalAlpha = alpha
        ctx.fillStyle = colour
        var i = -1
        while (i * span - shift < w + span) {
            val bx = i * span - shift
            val r = hash(i + seed)
            val r2 = hash(i + seed + 555)
            val bh = h * (base + rise * r)
            val bw = span * (0.14 + 0.16 * r2)
            val lean = (r2 - 0.5) * bw * 0.5
            ctx.beginPath()
            ctx.moveTo(bx, horizon)
            ctx.lineTo(bx + lean * 0.4, horizon - bh)
            ctx.lineTo(bx + lean + bw * 0.62, horizon - bh * (0.72 + 0.22 * r))   // snapped top
            ctx.lineTo(bx + bw, horizon - bh * 0.34)
            ctx.lineTo(bx + bw, horizon)
            ctx.closePath()
            ctx.fill()
            // an arch, sometimes: two legs and a broken span
            if (r > 0.72) {
                val ax = bx + span * 0.45
                val aw = span * 0.30
                val ah = h * (0.10 + 0.08 * r2)
                ctx.beginPath()
                ctx.moveTo(ax, horizon)
                ctx.lineTo(ax, horizon - ah)
                ctx.quadraticCurveTo(ax + aw / 2, horizon - ah * 1.75, ax + aw, horizon - ah)
                ctx.lineTo(ax + aw, horizon)
                ctx.lineTo(ax + aw - span * 0.05, horizon)
                ctx.lineTo(ax + aw - span * 0.05, horizon - ah * 0.95)
                ctx.quadraticCurveTo(ax + aw / 2, horizon - ah * 1.45, ax + span * 0.05, horizon - ah * 0.95)
                ctx.lineTo(ax + span * 0.05, horizon)
                ctx.closePath()
                ctx.fill()
            }
            i++
        }
        ctx.globalAlpha = 1.0
    }

    /** One rolling dune line, filled down to the bottom of the screen. */
    private fun dune(
        g: Game, parallax: Double, amp: Double, waves: Double,
        colour: String, alpha: Double, phase: Double,
    ) {
        val horizon = h * 0.645
        ctx.globalAlpha = alpha
        ctx.fillStyle = colour
        ctx.beginPath()
        ctx.moveTo(0.0, h)
        val shift = g.x * parallax
        var px = 0.0
        while (px <= w + 8.0) {
            val u = (px / w) * waves + shift * 0.05 + phase
            val y = horizon - h * amp * (sin(u * PI * 2) * 0.6 + sin(u * PI * 3.7 + 1.3) * 0.4)
            ctx.lineTo(px, y)
            px += 8.0
        }
        ctx.lineTo(w, h)
        ctx.closePath()
        ctx.fill()
        ctx.globalAlpha = 1.0
    }

    private fun drawCity(g: Game) {
        // The ring on the horizon. One big soft light source gives the whole
        // scene somewhere for its glow to come from.
        if (!reduceEffects) {
            val cx = w * 0.62 - (g.x * 0.02 * scale) % (w * 1.6)
            val cy = h * 0.40
            val r = h * 0.30
            val ring = ctx.createRadialGradient(cx, cy, r * 0.55, cx, cy, r)
            ring.addColorStop(0.0, "rgba(0,0,0,0)")
            ring.addColorStop(0.72, theme.horizon + "33")
            ring.addColorStop(1.0, "rgba(0,0,0,0)")
            ctx.fillStyle = ring
            ctx.fillRect(0.0, 0.0, w, h * 0.72)
        }

        skyline(g, 0.06, 5.2, 0.10, 0.13, theme.far, 0.55, windows = false, seed = 11)
        skyline(g, 0.16, 3.6, 0.14, 0.20, theme.mid, 0.60, windows = true, seed = 307)
        billboards(g, 0.26, 613)
        skyline(g, 0.34, 2.6, 0.06, 0.12, theme.near, 0.70, windows = false, seed = 929)

        // Thin streaks, much faster than anything behind them. This is where the
        // sense of speed comes from for free.
        ctx.globalAlpha = 0.16
        ctx.fillStyle = theme.horizon
        val fast = g.x * 0.62
        var k = -2
        while (k < 30) {
            val bx = w - ((k * 41.0 - fast % 41.0) * (scale / 60.0)) % (w + 120.0)
            val by = h * (0.05 + 0.040 * ((k * 11) % 8))
            ctx.fillRect(bx, by, 24.0 * (scale / 60.0), 2.0)
            k++
        }
        ctx.globalAlpha = 1.0
    }

    private var takenStars: Set<Int> = emptySet()
    /** The sim clock, so a moving hazard is drawn where the collision says it is. */
    private var levelTime = 0.0

    /**
     * A timed hazard in its quiet half: the vent a geyser will come out of, the
     * scorch mark a beam will land on, the block of masonry still up in the air.
     *
     * Drawing it costs almost nothing and buys the thing world 2 promises - that
     * nothing here arrives without having been somewhere the player could see it
     * first. It is deliberately drawn in the SCENERY colours, never in red, so a
     * dormant hazard never reads as a live one.
     */
    private fun drawDormant(hz: Hazard, b: Box) {
        ctx.globalAlpha = 0.22
        ctx.strokeStyle = theme.sun
        ctx.lineWidth = 2.0
        when (hz.look) {
            Look.GEYSER -> {
                ctx.beginPath()
                ctx.moveTo(sx(b.x0), sy(b.y0)); ctx.lineTo(sx(b.x1), sy(b.y0))
                ctx.stroke()
                ctx.globalAlpha = 0.14
                ctx.fillStyle = theme.sun
                ctx.fillRect(sx(b.x0), sy(b.y0) - 3.0, sx(b.x1) - sx(b.x0), 3.0)
            }
            Look.LASER -> {
                ctx.beginPath()
                ctx.moveTo(sx(b.x0), sy(b.y1)); ctx.lineTo(sx(b.x1), sy(b.y1))
                ctx.stroke()
            }
            Look.TENTACLE -> {
                // the seam in the floor an arm will come up through, so its
                // arrival is somewhere the player has already been looking
                ctx.beginPath()
                ctx.moveTo(sx(b.x0), sy(b.y0)); ctx.lineTo(sx(b.x1), sy(b.y0))
                ctx.stroke()
                ctx.globalAlpha = 0.12
                ctx.fillStyle = theme.sun
                ctx.fillRect(sx(b.x0), sy(b.y0) - 3.0, sx(b.x1) - sx(b.x0), 3.0)
            }
            Look.JELLY -> {
                // a shut jelly is drawn small and dim, sitting where it will swell
                val r = (sx(b.x1) - sx(b.x0)) * 0.30
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                ctx.beginPath(); ctx.arc((sx(b.x0) + sx(b.x1)) / 2, cy, r, 0.0, PI * 2)
                ctx.stroke()
            }
            Look.RUIN -> {
                ctx.globalAlpha = 0.20
                ctx.strokeStyle = theme.sunCore
                ctx.strokeRect(sx(b.x0), sy(b.y1) - scale * 2.2,
                    sx(b.x1) - sx(b.x0), scale * 0.5)
            }
            else -> {}
        }
        ctx.lineWidth = 2.5
        ctx.globalAlpha = 1.0
    }

    /**
     * Every hazard the game can draw, by what it is made of.
     *
     * [grow] is 0..1 while a timed hazard is charging, and the shape is built to
     * that fraction - a geyser rises out of its vent, a beam reaches down from the
     * sky - so the drawing IS the countdown rather than a decoration next to one.
     */
    private fun drawHazardShape(hz: Hazard, b: Box, grow: Double) {
        val x0 = sx(b.x0); val x1 = sx(b.x1)
        val mx = (x0 + x1) / 2
        when (hz.look) {
            // A crest of sand: a low rolling hump, not a blade. It is the one
            // hazard in the desert that is wider than it is tall, and it should
            // look like something the ground did rather than something built.
            Look.SAND_WAVE -> {
                val base = sy(b.y0); val top = sy(b.y0 + (b.y1 - b.y0) * grow)
                ctx.beginPath()
                ctx.moveTo(x0, base)
                ctx.bezierCurveTo(x0 + (x1 - x0) * 0.28, top, x0 + (x1 - x0) * 0.42, top, mx, top)
                ctx.bezierCurveTo(x0 + (x1 - x0) * 0.72, top, x0 + (x1 - x0) * 0.86, base, x1, base)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                // the lip of foam-sand it is throwing forward, which is also the
                // direction it is travelling: the player reads it without a HUD.
                ctx.globalAlpha *= 0.6
                ctx.beginPath()
                ctx.moveTo(x0, base); ctx.lineTo(x0 - (x1 - x0) * 0.22, base)
                ctx.stroke()
                ctx.globalAlpha /= 0.6
            }
            // Masonry. A slab with a broken corner, so it never reads as a platform.
            Look.RUIN -> {
                val top = sy(b.y1); val base = sy(b.y0)
                ctx.beginPath()
                ctx.moveTo(x0, base); ctx.lineTo(x0, top + (base - top) * 0.22)
                ctx.lineTo(x0 + (x1 - x0) * 0.26, top); ctx.lineTo(x1, top)
                ctx.lineTo(x1, base)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.55
                ctx.beginPath()
                ctx.moveTo(x0 + (x1 - x0) * 0.5, top); ctx.lineTo(x0 + (x1 - x0) * 0.5, base)
                ctx.stroke()
                ctx.globalAlpha /= 0.55
            }
            // A relic: a ring with a core, because the thing orbits and a circle
            // drawn as a circle is the clearest promise of where it goes next.
            Look.RELIC -> {
                val r = (x1 - x0) / 2
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                ctx.beginPath(); ctx.arc(mx, cy, r, 0.0, PI * 2); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.7
                ctx.beginPath(); ctx.arc(mx, cy, r * 0.45, 0.0, PI * 2); ctx.stroke()
                ctx.beginPath()
                for (k in 0 until 4) {
                    val a = levelTime * 1.6 + k * PI / 2
                    ctx.moveTo(mx + cos(a) * r * 0.5, cy + sin(a) * r * 0.5)
                    ctx.lineTo(mx + cos(a) * r * 0.98, cy + sin(a) * r * 0.98)
                }
                ctx.stroke()
                ctx.globalAlpha /= 0.7
            }
            // A geyser climbs out of its vent as it charges.
            Look.GEYSER -> {
                val base = sy(b.y0)
                val top = sy(b.y0 + (b.y1 - b.y0) * grow)
                ctx.beginPath()
                ctx.moveTo(x0, base)
                ctx.lineTo(x0 + (x1 - x0) * 0.22, top)
                ctx.lineTo(mx, top - (base - top) * 0.18)
                ctx.lineTo(x1 - (x1 - x0) * 0.22, top)
                ctx.lineTo(x1, base)
                ctx.closePath(); ctx.fill(); ctx.stroke()
            }
            // A beam reaches DOWN as it charges, so its tip is the countdown and
            // the player can see exactly how low it will come.
            //
            // The descent starts at the TOP OF THE SCREEN, not at the beam's own
            // y1. A beam is anchored nine units up in the sky, which is well off
            // the top of a 600px viewport - so charging from there spent the
            // entire warning above the player's head, where a warning is worth
            // nothing. Clamped to the visible edge, the tip is on screen from the
            // first frame of the charge to the last.
            Look.LASER -> {
                val top = max(sy(b.y1), 0.0)
                val floor = sy(b.y0)
                val tip = top + (floor - top) * grow
                val inset = (x1 - x0) * 0.22
                ctx.beginPath()
                ctx.moveTo(x0 + inset, top); ctx.lineTo(x1 - inset, top)
                ctx.lineTo(x1, tip); ctx.lineTo(x0, tip)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.75
                ctx.beginPath()
                ctx.moveTo(mx, top); ctx.lineTo(mx, tip)
                ctx.stroke()
                // the pool of light where it lands, which is the part the player
                // is actually judging their jump against.
                ctx.beginPath()
                ctx.ellipse(mx, tip, (x1 - x0) * 0.6, 4.0, 0.0, 0.0, PI * 2)
                ctx.stroke()
                ctx.globalAlpha /= 0.75
            }
            // A block of the mountain, rolling. Drawn as a rough polygon that
            // TURNS with its own travel, because a boulder that slides without
            // rotating reads as a box on a rail - and the rotation is also the
            // clearest possible statement of which way it is going.
            Look.BOULDER -> {
                val r = (x1 - x0) / 2
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                val spin = -(b.x0 * 2.0 / (r / scale).coerceAtLeast(0.1))
                ctx.save()
                ctx.translate(mx, cy)
                ctx.rotate(spin)
                ctx.beginPath()
                for (k in 0 until 9) {
                    val a = k * PI * 2 / 9
                    val rr = r * (0.82 + 0.18 * ((k * 7) % 5) / 4.0)
                    if (k == 0) ctx.moveTo(cos(a) * rr, sin(a) * rr)
                    else ctx.lineTo(cos(a) * rr, sin(a) * rr)
                }
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.55
                ctx.beginPath()
                ctx.moveTo(-r * 0.45, -r * 0.2); ctx.lineTo(r * 0.1, r * 0.35)
                ctx.moveTo(r * 0.5, -r * 0.4); ctx.lineTo(r * 0.15, -r * 0.05)
                ctx.stroke()
                ctx.globalAlpha /= 0.55
                ctx.restore()
            }
            // --- THE ABYSS ---------------------------------------------------
            // A bubble: a ring with a highlight, drawn as something with a
            // surface rather than a fill, because what makes a bubble read as a
            // bubble is that you can see through it.
            Look.BUBBLE -> {
                val r = (x1 - x0) / 2
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                ctx.beginPath(); ctx.arc(mx, cy, r * grow, 0.0, PI * 2)
                ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.7
                ctx.beginPath()
                ctx.arc(mx - r * 0.3, cy - r * 0.34, r * 0.26 * grow, 0.0, PI * 2)
                ctx.stroke()
                ctx.globalAlpha /= 0.7
            }
            // An orb: a core with a halo, the biggest single thing down here.
            Look.ORB -> {
                val r = (x1 - x0) / 2
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                ctx.globalAlpha *= 0.4
                ctx.beginPath(); ctx.arc(mx, cy, r * 1.35 * grow, 0.0, PI * 2); ctx.fill()
                ctx.globalAlpha /= 0.4
                ctx.beginPath(); ctx.arc(mx, cy, r * grow, 0.0, PI * 2); ctx.fill(); ctx.stroke()
            }
            // An arm reaching in: tapered, and it points the way it came from.
            Look.TENTACLE -> {
                val top = sy(b.y1); val base = sy(b.y0)
                ctx.beginPath()
                ctx.moveTo(x0, base)
                ctx.quadraticCurveTo(x0 + (x1 - x0) * 0.2, top, mx, top + (base - top) * 0.18)
                ctx.quadraticCurveTo(x1 - (x1 - x0) * 0.1, top, x1, base)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.5
                ctx.beginPath()
                ctx.moveTo(x0 + (x1 - x0) * 0.3, base); ctx.lineTo(mx, top + (base - top) * 0.4)
                ctx.stroke()
                ctx.globalAlpha /= 0.5
            }
            // A crystal: a faceted shard hanging point-down, with a stem going up
            // into the dark so the eye reads it as SUSPENDED rather than floating.
            // Every other abyss shape is round; this one is all angles, which is
            // what tells the player at a glance that it is the one that swings.
            Look.CRYSTAL -> {
                val top = sy(b.y1); val base = sy(b.y0)
                val w = (x1 - x0) / 2
                ctx.beginPath()
                ctx.moveTo(mx, base)
                ctx.lineTo(x0, top + (base - top) * 0.62)
                ctx.lineTo(mx - w * 0.45, top)
                ctx.lineTo(mx + w * 0.45, top)
                ctx.lineTo(x1, top + (base - top) * 0.62)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.55
                ctx.beginPath()
                ctx.moveTo(mx, base); ctx.lineTo(mx, top)
                ctx.moveTo(x0, top + (base - top) * 0.62); ctx.lineTo(x1, top + (base - top) * 0.62)
                ctx.moveTo(mx, top); ctx.lineTo(mx, 0.0)          // the stem into the dark
                ctx.stroke()
                ctx.globalAlpha /= 0.55
            }
            // A jelly: a dome that BREATHES. Its size is driven by [grow], which
            // the caller has already filled from the warm-up, so the swell the
            // player is asked to read is the same number the physics switches on.
            Look.JELLY -> {
                val top = sy(b.y1); val base = sy(b.y0)
                val w = (x1 - x0) / 2 * (0.7 + 0.3 * grow)
                val cy = top + (base - top) * 0.42
                ctx.beginPath()
                ctx.moveTo(mx - w, cy)
                ctx.quadraticCurveTo(mx - w, top, mx, top)
                ctx.quadraticCurveTo(mx + w, top, mx + w, cy)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.6
                ctx.beginPath()
                for (k in 0 until 4) {
                    val tx = mx - w * 0.7 + k * (w * 1.4 / 3)
                    ctx.moveTo(tx, cy)
                    ctx.quadraticCurveTo(tx + sin(levelTime * 3.0 + k) * w * 0.25,
                        (cy + base) / 2, tx, base)
                }
                ctx.stroke()
                ctx.globalAlpha /= 0.6
            }
            // A ring of pressure: two concentric ellipses, open in the middle, so
            // it reads as something travelling THROUGH the water rather than in it.
            Look.RING -> {
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                val rx = (x1 - x0) / 2 * grow
                val ry = (sy(b.y0) - sy(b.y1)) / 2 * grow
                ctx.beginPath(); ctx.ellipse(mx, cy, rx, ry, 0.0, 0.0, PI * 2); ctx.stroke()
                ctx.globalAlpha *= 0.65
                ctx.beginPath()
                ctx.ellipse(mx, cy, rx * 0.55, ry * 0.55, 0.0, 0.0, PI * 2); ctx.stroke()
                ctx.globalAlpha /= 0.65
            }
            // A swell: a crest with its own surface line, low and wide.
            Look.WAVE -> {
                val top = sy(b.y1); val base = sy(b.y0)
                ctx.beginPath()
                ctx.moveTo(x0, base)
                ctx.quadraticCurveTo(mx - (x1 - x0) * 0.2, top, mx, top)
                ctx.quadraticCurveTo(mx + (x1 - x0) * 0.2, top, x1, base)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.globalAlpha *= 0.5
                ctx.beginPath()
                ctx.moveTo(x0, base + (top - base) * 0.35)
                ctx.quadraticCurveTo(mx, top + (base - top) * 0.25, x1, base + (top - base) * 0.35)
                ctx.stroke()
                ctx.globalAlpha /= 0.5
            }
            // Drift: a small angular thing turning slowly, with nothing familiar
            // about its outline. It is the only shape here that is not symmetric.
            Look.SHARD -> {
                val r = (x1 - x0) / 2 * grow
                val cy = (sy(b.y0) + sy(b.y1)) / 2
                ctx.save(); ctx.translate(mx, cy); ctx.rotate(levelTime * 0.9 + b.x0)
                ctx.beginPath()
                ctx.moveTo(0.0, -r)
                ctx.lineTo(r * 0.75, -r * 0.15)
                ctx.lineTo(r * 0.35, r)
                ctx.lineTo(-r * 0.6, r * 0.5)
                ctx.lineTo(-r * 0.8, -r * 0.45)
                ctx.closePath(); ctx.fill(); ctx.stroke()
                ctx.restore()
            }
            // The city's spike, unchanged.
            Look.SPIKE -> {
                ctx.beginPath()
                if (hz.kind == HazardKind.SPIKE_UP) {
                    ctx.moveTo(x0, sy(b.y0)); ctx.lineTo(mx, sy(b.y1)); ctx.lineTo(x1, sy(b.y0))
                } else {
                    ctx.moveTo(x0, sy(b.y1)); ctx.lineTo(mx, sy(b.y0)); ctx.lineTo(x1, sy(b.y1))
                }
                ctx.closePath(); ctx.fill(); ctx.stroke()
            }
        }
    }

    /**
     * The sandstorm, drawn AFTER the background and BEFORE anything the player
     * has to judge.
     *
     * That ordering is the whole design. The storm is a veil over the sky, the
     * dunes and the ruins; the floor, the hazards, the coins and the runner are
     * painted on top of it at full strength. So the world closes in and becomes
     * hostile without one unit of readability being spent on it - which is the
     * only way "you cannot see" is allowed to exist in a game that promises the
     * player always knows why they died.
     *
     * Its density comes from the runner's own x, so it has edges you can watch
     * yourself run into rather than a wall that switches on.
     */
    /**
     * THE ABYSS: deep water lit from BELOW.
     *
     * That inversion is the whole scene. The city and the desert are both lit
     * from above - signage, a sun on the horizon - so drawing world 3 the same
     * way and changing the palette really would have been "world 2 in blue". Here
     * the light source is under the floor, the water above goes to black, and the
     * shapes in it are silhouettes against the glow rather than against the sky.
     */
    private fun drawAbyss(g: Double) {
        // the glow under everything, which is also the horizon
        val floorY = h * 0.78
        val lit = ctx.createRadialGradient(w * 0.5, floorY, 0.0, w * 0.5, floorY, w * 0.75)
        lit.addColorStop(0.0, theme.sun + "aa")
        lit.addColorStop(0.35, theme.near + "66")
        lit.addColorStop(1.0, "rgba(1,4,13,0)")
        ctx.fillStyle = lit
        ctx.fillRect(0.0, 0.0, w, h)

        // shafts of light rising out of it, slow and wide
        if (!reduceEffects) {
            ctx.globalAlpha = 0.16
            ctx.fillStyle = theme.horizon
            for (k in 0 until 7) {
                val base = w * (0.08 + 0.14 * k) - (g * 12.0 * (0.4 + k % 3 * 0.2)) % (w * 1.2)
                val sway = sin(g * 0.4 + k) * 18.0
                ctx.beginPath()
                ctx.moveTo(base + sway, floorY)
                ctx.lineTo(base + sway - 34.0, 0.0)
                ctx.lineTo(base + sway + 34.0, 0.0)
                ctx.closePath()
                ctx.fill()
            }
            ctx.globalAlpha = 1.0
        }

        // things in the water, far off: slabs of rock in silhouette, and growing
        // out of them the formations this world is named for - crystal, lit from
        // the same glow underneath, so they read as part of the floor rather than
        // as scenery stuck on top of it.
        ridge(g, 0.06, 6.0, 0.10, 0.20, theme.far, 0.75, 17)
        crystalField(g, 0.10, 3.4, 0.12, 0.30, 0.34, 733)
        ridge(g, 0.14, 4.0, 0.14, 0.26, theme.mid, 0.82, 409)
        crystalField(g, 0.20, 2.2, 0.08, 0.18, 0.22, 91)

        // Bubbles rising out of the dark - the one thing that says WATER faster
        // than any amount of blue. They drift up rather than across, so they read
        // against the runner's own direction instead of adding to it.
        if (!reduceEffects) {
            ctx.strokeStyle = theme.horizon
            ctx.lineWidth = 1.4
            for (k in 0 until 26) {
                val seed = k * 197 + 11
                val speed = 26.0 + seed % 34
                val bx = ((seed * 61) % w.toInt()).toDouble() -
                    (g * (2.0 + seed % 4)) % (w + 60.0)
                val rise = (g * speed + seed * 7) % (h * 1.15)
                val by = floorY + 30.0 - rise
                if (by < -10.0) continue
                val r = 1.6 + (seed % 7) * 0.8
                ctx.globalAlpha = 0.34 * (1.0 - rise / (h * 1.15)).coerceIn(0.0, 1.0) + 0.08
                ctx.beginPath()
                ctx.arc((bx + w * 2) % w, by + sin(g * 1.6 + k) * 4.0, r, 0.0, PI * 2)
                ctx.stroke()
            }
            ctx.globalAlpha = 1.0
            ctx.lineWidth = 2.5
        }

        // and the motes everything underwater has
        if (!reduceEffects) {
            ctx.globalAlpha = 0.34
            ctx.fillStyle = theme.sunCore
            for (k in 0 until 60) {
                val seed = k * 131
                val mx = (seed * 37 % w.toInt()).toDouble() - (g * (6.0 + seed % 9)) % (w + 40.0)
                val my = ((seed * 53) % h.toInt()).toDouble() +
                    sin(g * 0.6 + k) * 9.0
                val r = 0.7 + (seed % 5) * 0.4
                ctx.beginPath(); ctx.arc((mx + w) % w, my, r, 0.0, PI * 2); ctx.fill()
            }
            ctx.globalAlpha = 1.0
        }
    }

    /**
     * Crystal growing out of the rock: narrow, pointed, and drawn twice - a body
     * in silhouette and a brighter core inside it - so a formation a hundred
     * units away still reads as something with light in it rather than as a
     * black triangle. [alpha] is how much of the glow it keeps.
     */
    private fun crystalField(g: Double, speed: Double, step: Double, lo: Double, hi: Double,
                             alpha: Double, seed: Int) {
        val gy = h * 0.78
        val span = w / step
        val shift = (g * speed * scale) % span
        var i = -1
        var r = seed
        while (i * span - shift < w + span) {
            r = (r * 1103515245 + 12345) and 0x7fffffff
            val tall = h * (lo + (hi - lo) * ((r shr 9) % 100) / 100.0)
            val lean = (((r shr 5) % 40) - 20) / 100.0
            val bx = i * span - shift + span * 0.5
            val halfW = span * 0.10
            ctx.globalAlpha = alpha
            ctx.fillStyle = theme.billboard
            ctx.beginPath()
            ctx.moveTo(bx - halfW, gy)
            ctx.lineTo(bx + halfW * lean * 2.0, gy - tall)
            ctx.lineTo(bx + halfW, gy)
            ctx.closePath()
            ctx.fill()
            ctx.globalAlpha = alpha * 0.8
            ctx.fillStyle = theme.sun
            ctx.beginPath()
            ctx.moveTo(bx - halfW * 0.3, gy)
            ctx.lineTo(bx + halfW * lean * 2.0, gy - tall * 0.86)
            ctx.lineTo(bx + halfW * 0.3, gy)
            ctx.closePath()
            ctx.fill()
            i++
        }
        ctx.globalAlpha = 1.0
    }

    /** Rock in silhouette against the glow - flat-topped slabs, not a skyline. */
    private fun ridge(g: Double, speed: Double, step: Double, lo: Double, hi: Double,
                      colour: String, alpha: Double, seed: Int) {
        val gy = h * 0.78
        ctx.globalAlpha = alpha
        ctx.fillStyle = colour
        val span = w / step
        val shift = (g * speed * scale) % span
        var i = -1
        var r = seed
        while (i * span - shift < w + span) {
            r = (r * 1103515245 + 12345) and 0x7fffffff
            val tall = h * (lo + (hi - lo) * ((r shr 9) % 100) / 100.0)
            val bx = i * span - shift
            ctx.beginPath()
            ctx.moveTo(bx, gy)
            ctx.lineTo(bx + span * 0.18, gy - tall)
            ctx.lineTo(bx + span * 0.74, gy - tall * 0.82)
            ctx.lineTo(bx + span * 0.96, gy)
            ctx.closePath()
            ctx.fill()
            i++
        }
        ctx.globalAlpha = 1.0
    }

    private fun drawStorm(level: Level, g: Game) {
        if (level.storms.isEmpty()) return
        val strength = level.storms.sumOf { it.at(g.x) }.coerceIn(0.0, 0.85)
        if (strength <= 0.001) return
        val veil = if (reduceEffects) strength * 0.5 else strength
        // the air itself, in the world's own light
        // Sand in the air LIGHTENS what is behind it - it is lit by the same sun
        // everything else is. Veiling with the sky's own dark colours was the
        // first attempt and it read as nightfall rather than weather, which is a
        // different thing entirely and a much worse one to run through.
        ctx.globalAlpha = veil * 0.58
        val grad = ctx.createLinearGradient(0.0, 0.0, 0.0, h)
        grad.addColorStop(0.0, theme.sun)
        grad.addColorStop(0.48, theme.near)
        grad.addColorStop(1.0, theme.billboard)
        ctx.fillStyle = grad
        ctx.fillRect(0.0, 0.0, w, h)
        if (!reduceEffects) {
            // sand moving across the frame, fast and shallow-angled
            ctx.globalAlpha = veil * 0.5
            ctx.strokeStyle = theme.sunCore
            ctx.lineWidth = 1.4
            ctx.beginPath()
            val t = levelTime
            for (k in 0 until 46) {
                val seed = k * 97
                val speed = 520.0 + (seed % 340)
                val sxp = (w + 120.0) - ((t * speed + seed * 13.0) % (w + 240.0))
                val syp = ((seed * 31) % h.toInt()).toDouble()
                val len = 26.0 + (seed % 40)
                ctx.moveTo(sxp, syp)
                ctx.lineTo(sxp + len, syp + len * 0.22)
            }
            ctx.stroke()
            ctx.lineWidth = 2.5
        }
        ctx.globalAlpha = 1.0
    }

    private fun drawLevel(level: Level) {
        val left = camX - originX / scale - 2.0
        val right = camX + (w - originX) / scale + 2.0

        ctx.lineWidth = 2.5
        level.forEachSolidNear(left, right) { s ->
            if (!s.presentAt(levelTime)) return@forEachSolidNear
            val x0 = sx(s.x0At(levelTime)); val x1 = sx(s.x1At(levelTime))
            val yTop = sy(s.topAt(levelTime))
            val bottom = sy(maxOf(s.bottomAt(levelTime), camY - viewHeight))
            // A platform about to vanish flashes, so the player is told before it
            // happens rather than after they are already falling.
            val fading = s.blink?.strengthAt(levelTime) ?: 1.0
            // Every surface is drawn in the same two strokes - a filled body and a
            // bright lip - because the lip is the only line in the game the player
            // actually lands on, and it has to mean the same thing in every world.
            // What changes between surfaces is the SKIN, never the lip.
            // Plain ground is lit by whatever is in THAT world's sky, so a desert
            // level does not open on a strip of city cyan. The surface tags below
            // separate materials WITHIN a world; the scene decides what ordinary
            // ground is made of in the first place.
            val edge = when (s.surface) {
                Surface.SAND -> theme.sun
                Surface.TEMPLE -> theme.sunCore
                Surface.BRIDGE -> theme.accent
                Surface.MIRAGE -> theme.accent
                Surface.BUBBLE -> theme.billboard
                Surface.STONE -> if (sandy) theme.horizon else if (deep) theme.sun else safe
            }
            val skin = when (s.surface) {
                Surface.SAND -> "rgba(255,154,42,0.20)"
                Surface.TEMPLE -> "rgba(255,233,168,0.16)"
                Surface.BRIDGE -> "rgba(255,46,139,0.16)"
                Surface.MIRAGE -> "rgba(46,240,255,0.10)"
                Surface.BUBBLE -> "rgba(176,123,255,0.18)"
                Surface.STONE -> when {
                    sandy -> "rgba(184,72,31,0.26)"
                    deep -> "rgba(15,111,158,0.30)"
                    else -> safeFill
                }
            }
            // A mirage shimmers rather than fades: same honest machinery, dressed
            // as something you have to look twice at.
            val shimmer = if (s.surface == Surface.MIRAGE && !reduceEffects)
                0.72 + 0.28 * sin(levelTime * 9.0 + s.x0) else 1.0
            ctx.globalAlpha = (0.25 + 0.75 * fading) * shimmer
            ctx.fillStyle = skin
            ctx.fillRect(x0, yTop, x1 - x0, bottom - yTop)
            ctx.shadowBlur = if (reduceEffects) 0.0 else 22.0
            ctx.shadowColor = edge
            ctx.strokeStyle = edge
            ctx.lineWidth = 3.0
            ctx.beginPath()
            ctx.moveTo(x0, yTop); ctx.lineTo(x1, yTop)
            ctx.stroke()
            ctx.stroke()                       // twice: the surface is the anchor
            ctx.lineWidth = 2.5
            ctx.shadowBlur = 0.0
            // Sand gets a grain line just under its lip, temple stone gets courses
            // of masonry: enough for the eye to name the material at a glance.
            if (s.surface == Surface.SAND && !reduceEffects) {
                ctx.globalAlpha = 0.34 * shimmer
                ctx.strokeStyle = theme.sunCore
                ctx.lineWidth = 1.4
                ctx.beginPath()
                var gx = x0
                while (gx < x1) {
                    val dy = 3.0 * sin((gx / scale + camX) * 1.4 + levelTime * 2.0)
                    if (gx == x0) ctx.moveTo(gx, yTop + 7.0 + dy) else ctx.lineTo(gx, yTop + 7.0 + dy)
                    gx += 6.0
                }
                ctx.stroke()
                ctx.lineWidth = 2.5
            } else if (s.surface == Surface.TEMPLE && !reduceEffects) {
                ctx.globalAlpha = 0.28
                ctx.strokeStyle = theme.sunCore
                ctx.lineWidth = 1.2
                ctx.beginPath()
                var cy = yTop + scale * 0.5
                var row = 0
                while (cy < bottom && row < 6) {
                    ctx.moveTo(x0, cy); ctx.lineTo(x1, cy)
                    val off = if (row % 2 == 0) scale * 0.5 else 0.0
                    var bx = x0 + off
                    while (bx < x1) { ctx.moveTo(bx, cy); ctx.lineTo(bx, cy - scale * 0.5); bx += scale }
                    cy += scale * 0.5; row++
                }
                ctx.stroke()
                ctx.lineWidth = 2.5
            } else if (s.surface == Surface.BUBBLE && !reduceEffects) {
                // A floor of bubbles has to LOOK like one, or the bridge that
                // bursts later is just a platform that vanished. Domes along the
                // lip, breathing slightly, on a phase taken from the span's own x
                // so two bridges never pulse in unison.
                ctx.globalAlpha = 0.34 * shimmer
                ctx.strokeStyle = theme.billboard
                ctx.lineWidth = 1.6
                ctx.beginPath()
                var bx = x0 + scale * 0.35
                var k = 0
                while (bx < x1) {
                    val puff = 1.0 + 0.10 * sin(levelTime * 2.6 + s.x0 * 0.7 + k * 1.1)
                    val r = scale * 0.30 * puff
                    ctx.moveTo(bx + r, yTop + r * 0.35)
                    ctx.arc(bx, yTop + r * 0.35, r, 0.0, 6.2832)
                    bx += scale * 0.72; k++
                }
                ctx.stroke()
                ctx.lineWidth = 2.5
            }
            ctx.globalAlpha = (0.25 + 0.75 * fading) * shimmer
            ctx.shadowBlur = 0.0
            ctx.strokeStyle = if (sandy) "rgba(255,154,42,0.30)" else "rgba(49,212,242,0.35)"
            ctx.beginPath()
            ctx.moveTo(x0, yTop); ctx.lineTo(x0, bottom)
            ctx.moveTo(x1, yTop); ctx.lineTo(x1, bottom)
            ctx.stroke()
            ctx.globalAlpha = 1.0
        }

        // Columns of moving air, drawn as rising or falling motes. The player can
        // see which way a gust pushes before they are in it.
        for (wd in level.winds) {
            if (wd.x1 < left || wd.x0 > right) continue
            val wx0 = sx(wd.x0); val wx1 = sx(wd.x1)
            val up = wd.push > 0.0
            ctx.globalAlpha = 0.16
            ctx.fillStyle = if (up) theme.sunCore else hazardDim
            ctx.fillRect(wx0, 0.0, wx1 - wx0, h)
            ctx.globalAlpha = if (reduceEffects) 0.30 else 0.55
            ctx.strokeStyle = if (up) theme.sunCore else hazard
            ctx.lineWidth = 2.0
            ctx.beginPath()
            for (k in 0 until 7) {
                val lane = wx0 + (wx1 - wx0) * (0.12 + 0.13 * k)
                val drift = (levelTime * 210.0 * (if (up) -1.0 else 1.0) + k * 97.0) % (h + 60.0)
                val my = if (up) h - drift else drift - 60.0
                ctx.moveTo(lane, my); ctx.lineTo(lane, my + 26.0)
            }
            ctx.stroke()
            ctx.lineWidth = 2.5
            ctx.globalAlpha = 1.0
        }

        level.forEachHazardNear(left, right) { hz ->
            val b = hz.drawBoxAt(levelTime)
            val live = hz.activeAt(levelTime)
            val warm = hz.warmAt(levelTime)
            // A hazard that is off is scenery, and it is drawn as scenery. A hazard
            // that is ABOUT to be on is drawn charging, growing out of the floor or
            // down from the sky as it warms. This is the whole difference between a
            // timed hazard and a coin flip, and it is why the core exposes warmAt at
            // all: the physics ignores the charge entirely, the player does not.
            if (!live && warm <= 0.0) {
                if (!reduceEffects) drawDormant(hz, b)
                return@forEachHazardNear
            }
            ctx.globalAlpha = if (live) 1.0 else 0.35 + 0.5 * warm
            // Colour-blind mode does not recolour danger, it adds a second signal:
            // a white-hot core inside the same red shape, which reads at any kind of
            // colour vision and still says "hot" to everyone else.
            ctx.fillStyle = if (colorblind) "#fff0f4" else hazardDim
            ctx.strokeStyle = hazard
            ctx.shadowBlur = if (reduceEffects) 0.0 else 12.0
            ctx.shadowColor = hazard
            // A charging hazard is drawn at the size it has GROWN to, so the shape
            // itself is the countdown.
            val grow = if (live) 1.0 else warm
            drawHazardShape(hz, b, grow)
            ctx.shadowBlur = 0.0
            ctx.globalAlpha = 1.0
            // A mover gets a track line so its range is readable before it arrives.
            hz.motion?.let { m ->
                ctx.globalAlpha = 0.26
                ctx.strokeStyle = hazard
                ctx.lineWidth = 1.5
                ctx.beginPath()
                val cx = sx(hz.x0 + 0.5)
                val cy = sy((b.y0 + b.y1) / 2)
                if (m.reachX > 0.0) {
                    ctx.moveTo(sx(hz.x0 + 0.5 - m.reachX), cy)
                    ctx.lineTo(sx(hz.x0 + 0.5 + m.reachX), cy)
                }
                if (m.reachY > 0.0) {
                    ctx.moveTo(cx, sy((hz.y0 + hz.y1) / 2 - m.reachY))
                    ctx.lineTo(cx, sy((hz.y0 + hz.y1) / 2 + m.reachY))
                }
                ctx.stroke()
                ctx.globalAlpha = 1.0
                ctx.lineWidth = 2.5
            }
        }

        for ((i, st) in level.stars.withIndex()) {
            if (st.x < left || st.x > right) continue
            if (takenStars.contains(i)) continue
            ctx.strokeStyle = gold; ctx.fillStyle = "rgba(255,209,102,0.18)"
            ctx.shadowBlur = 12.0; ctx.shadowColor = gold
            ctx.beginPath()
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) 0.45 else 0.20
                val a = -PI / 2 + k * PI / 5
                val px = sx(st.x + cos(a) * r); val py = sy(st.y + sin(a) * r)
                if (k == 0) ctx.moveTo(px, py) else ctx.lineTo(px, py)
            }
            ctx.closePath(); ctx.fill(); ctx.stroke()
            ctx.shadowBlur = 0.0
        }

        // Finish gate.
        val fx = sx(level.finishX)
        if (fx > -40 && fx < w + 40) {
            val gw = scale * 0.9
            ctx.strokeStyle = finish; ctx.lineWidth = 3.0
            ctx.shadowBlur = 18.0; ctx.shadowColor = finish
            ctx.globalAlpha = 0.45
            ctx.beginPath(); ctx.moveTo(fx, 0.0); ctx.lineTo(fx, h); ctx.stroke()
            ctx.globalAlpha = 1.0
            ctx.lineWidth = 5.0
            ctx.beginPath()
            ctx.moveTo(fx - gw, h * 0.18); ctx.lineTo(fx + gw, h * 0.18)
            ctx.moveTo(fx - gw, h * 0.86); ctx.lineTo(fx + gw, h * 0.86)
            ctx.stroke()
            ctx.shadowBlur = 0.0; ctx.lineWidth = 2.5
        }
    }

    /**
     * The floor as a mirror. The city overhead, then the runner, flipped about
     * the surface they are standing over and heavily dimmed. It is the single
     * cheapest thing that turns a flat dark band into somewhere the game is
     * happening, and it is the first thing Reduce Effects turns off.
     */
    private fun drawFloorMirror(g: Game) {
        if (reduceEffects || g.state != GameState.RUNNING) return
        val ground = groundUnder(g) ?: return
        val gy = sy(ground)
        if (gy > h || gy < 0) return
        // What a wet floor under a neon strip actually does: it bleeds the
        // strip's own light downward and gives back nothing else. Mirroring the
        // skyline into it was tried and read as a wall of panels, which is worse
        // than no reflection at all - it put vertical edges under the play line.
        val depth = scale * 3.2
        ctx.save()
        ctx.beginPath()
        ctx.rect(0.0, gy, w, depth)
        ctx.clip()
        // The city bleeds its own cyan strip downward; the desert bleeds the sun,
        // because that is the only thing lighting it. Same effect, same depth,
        // same restraint - the light under the play line never gains an edge.
        val lit = if (sandy) theme.sun else safe
        val bleed = ctx.createLinearGradient(0.0, gy, 0.0, gy + depth)
        bleed.addColorStop(0.0, lit + "4d")
        bleed.addColorStop(0.22, lit + "1f")
        bleed.addColorStop(1.0, "rgba(5,6,15,0)")
        ctx.fillStyle = bleed
        ctx.fillRect(0.0, gy, w, depth)

        // Streaks sliding along the surface, so the floor is moving too.
        ctx.globalAlpha = 0.16
        ctx.fillStyle = theme.horizon
        val span = 7.0 * scale
        if (span > 1.0) {
            val shift = (g.x * 1.0 * scale) % span
            var i = -1
            while (i * span - shift < w + span) {
                val bx = i * span - shift
                ctx.fillRect(bx, gy + depth * 0.10, span * 0.30, 2.0)
                ctx.fillRect(bx + span * 0.5, gy + depth * 0.26, span * 0.16, 1.5)
                i++
            }
        }
        ctx.globalAlpha = 1.0
        ctx.restore()
    }

    /** Highest surface under the runner, or null over a pit. */
    private fun groundUnder(g: Game): Double? {
        var best: Double? = null
        val t = g.elapsed
        g.level.forEachSolidNear(g.x, g.x + Tuning.PLAYER_SIZE) { s ->
            if (!s.presentAt(t)) return@forEachSolidNear
            if (g.x + Tuning.PLAYER_SIZE <= s.x0At(t) || g.x >= s.x1At(t)) return@forEachSolidNear
            val top = s.topAt(t)
            if (top <= g.y + 1e-6) { val b = best; if (b == null || top > b) best = top }
        }
        return best
    }

    /**
     * A contact shadow, directly under the runner and never behind it. Its size
     * is the whole point: it is the only cue that says how far there is left to
     * fall, which is exactly what a second jump needs the player to judge.
     */
    private fun drawShadow(g: Game) {
        if (g.state != GameState.RUNNING) return
        val ground = groundUnder(g) ?: return
        val height = g.y - ground
        if (height > 5.4) return
        val t = (height / 5.4).coerceIn(0.0, 1.0)
        val rx = scale * (0.48 - 0.26 * t)
        val ry = rx * 0.30
        // Light, not darkness. A black contact shadow is invisible on a near-black
        // floor, so the runner drops a pool of its own colour instead: it tightens
        // and brightens as the ground comes up, which is the read a second jump
        // needs before it is spent.
        val near = 1.0 - t
        ctx.beginPath()
        ctx.ellipse(sx(g.x + 0.5), sy(ground) - ry * 0.5, rx, ry, 0.0, 0.0, PI * 2)
        // Lit by whatever is in that world's sky: signage in the city, the sun in
        // the desert. The shape and the timing are identical, because this is the
        // one cue a second jump is judged against and it must not change between
        // worlds - only its colour does.
        val lamp = if (sandy) "255,154,42" else "255,201,60"
        ctx.fillStyle = "rgba($lamp,${0.05 + 0.17 * near})"
        ctx.fill()
        ctx.strokeStyle = "rgba($lamp,${0.16 + 0.34 * near})"
        ctx.lineWidth = 1.5
        ctx.stroke()
        ctx.lineWidth = 2.5
    }

    /** Ghost copies of the runner, each smaller and fainter than the last. */
    private fun drawTrail(g: Game) {
        val front = sx(g.x + Tuning.PLAYER_SIZE)
        ctx.lineWidth = 2.0
        for ((i, t) in trail.withIndex()) {
            val a = (t[2] / t[3]).coerceIn(0.0, 1.0)
            val px = sx(t[0] + 0.5)
            if (px > front) continue                      // never ahead of the runner
            val size = scale * Tuning.PLAYER_SIZE * Art.trailScale(look.trail, a)
            ctx.globalAlpha = if (boostGlow > 0.0) a.pow(1.15) * 0.92
                              else Art.trailAlpha(look.trail, a.pow(1.15))
            ctx.lineWidth = 2.0 + a
            ctx.strokeStyle = if (boostGlow > 0.0) boost else Art.trailColour(look.trail, player, a, i)
            ctx.save()
            ctx.translate(px, sy(t[1] + 0.5))
            ctx.rotate(t[4] * PI / 180.0)
            Art.shapePath(ctx, look.shape, size)
            ctx.stroke()
            ctx.restore()
        }
        ctx.globalAlpha = 1.0
    }

    private fun drawParticles(g: Game) {
        val front = sx(g.x + Tuning.PLAYER_SIZE)
        for (p in parts) {
            val a = (p[4] / p[5]).coerceIn(0.0, 1.0)
            val px = sx(p[0])
            if (px > front) continue                      // readability outranks effects
            val py = sy(p[1])
            ctx.globalAlpha = a * 0.85
            ctx.fillStyle = when (p[7].toInt()) {
                1 -> boost
                3 -> if (a > 0.66) theme.sunCore else theme.sun     // grit, cooling as it falls
                else -> gold
            }
            val s = scale * p[6] * (0.4 + 0.6 * a)
            when (p[7].toInt()) {
                3 -> {                                               // a grain of sand
                    ctx.globalAlpha = a * a * 0.9
                    ctx.fillRect(px - s * 0.35, py - s * 0.35, s * 0.7, s * 0.7)
                }
                2 -> ctx.fillRect(px, py - s * 0.18, s * 3.2, s * 0.36)   // a streak of speed
                1 -> ctx.fillRect(px - s * 0.3, py - s * 0.3, s * 0.6, s * 0.6)
                else -> ctx.fillRect(px - s / 2, py - s / 2, s, s)
            }
        }
        ctx.globalAlpha = 1.0
    }

    /** The boost's own mark: one bright, quick ring, used nowhere else. */
    private fun drawRings(g: Game) {
        ctx.strokeStyle = boost
        for (r in rings) {
            val a = (r[2] / r[3]).coerceIn(0.0, 1.0)
            ctx.globalAlpha = a * a * 0.95
            ctx.lineWidth = 1.5 + 3.0 * a
            ctx.beginPath()
            ctx.ellipse(sx(r[0]), sy(r[1]), scale * (0.45 + 0.95 * (1 - a)),
                scale * (0.45 + 0.72 * (1 - a)), 0.0, 0.0, PI * 2)
            ctx.stroke()
        }
        ctx.globalAlpha = 1.0
        ctx.lineWidth = 2.5
    }

    private fun drawShards() {
        for (s in shards) {
            ctx.globalAlpha = (s[4] / 0.7).coerceIn(0.0, 1.0)
            ctx.fillStyle = if (s[4] > 0.35) player else hazard
            val sz = scale * 0.16
            ctx.fillRect(sx(s[0]) - sz / 2, sy(s[1]) - sz / 2, sz, sz)
        }
        ctx.globalAlpha = 1.0
    }

    /** The runner's silhouette, centred on the origin. */
    private fun roundSquare(size: Double, r: Double) {
        val h2 = size / 2
        ctx.beginPath()
        ctx.moveTo(-h2 + r, -h2); ctx.lineTo(h2 - r, -h2); ctx.quadraticCurveTo(h2, -h2, h2, -h2 + r)
        ctx.lineTo(h2, h2 - r); ctx.quadraticCurveTo(h2, h2, h2 - r, h2)
        ctx.lineTo(-h2 + r, h2); ctx.quadraticCurveTo(-h2, h2, -h2, h2 - r)
        ctx.lineTo(-h2, -h2 + r); ctx.quadraticCurveTo(-h2, -h2, -h2 + r, -h2)
        ctx.closePath()
    }

    private fun drawPlayer(g: Game) {
        // A light run bounce and a squash that answers every take-off and landing.
        // Both are drawing only: the hitbox never moves (GDD fairness law 3).
        val bounce = if (g.grounded) abs(sin(runPhase * PI)) * 0.055 else 0.0
        var sq = stretch
        // Compression just before touchdown, so the landing is anticipated.
        val ground = groundUnder(g)
        if (!g.grounded && g.vy < 0.0 && ground != null) {
            val gap = g.y - ground
            if (gap < 1.1) sq -= 0.13 * (1.0 - gap / 1.1)
        }
        val sy2 = 1.0 + sq
        val sx2 = 1.0 - sq * 0.72

        val cx = sx(g.x + 0.5)
        val cy = sy(g.y + 0.5 + bounce)
        val s = scale * Tuning.PLAYER_SIZE
        ctx.save()
        ctx.translate(cx, cy)
        ctx.rotate(g.rotationDeg * PI / 180.0)
        ctx.scale(sx2, sy2)
        val edge = if (g.face == Face.DOUBLE) boost else player
        ctx.shadowBlur = 22.0; ctx.shadowColor = edge
        ctx.fillStyle = Palette.playerFill(look.colour)
        ctx.strokeStyle = edge
        ctx.lineWidth = 3.0
        Art.shapePath(ctx, look.shape, s)
        ctx.fill(); ctx.stroke()
        ctx.stroke()                       // twice: the glow is the point
        ctx.shadowBlur = 0.0
        ctx.restore()
        // The body spins so the rotation reads as timing, but the face does not:
        // a sideways face cannot do the job GDD 2.2 gives it.
        ctx.save()
        ctx.translate(cx, cy)
        ctx.scale(sx2, sy2)
        drawFace(g, s)
        ctx.restore()
    }

    /** GDD 2.2: the face is a readability element, so it must stay legible at phone size. */
    private fun drawFace(g: Game, s: Double) {
        // A little life: the eyes lead the run, and lift on the way up.
        val lead = if (g.grounded) sin(runPhase * PI * 2) * s * 0.022 else 0.0
        val lift = if (!g.grounded) (g.vy / Tuning.JUMP_VELOCITY).coerceIn(-1.0, 1.0) * s * 0.03 else 0.0
        val fs = Art.faceScale(look.shape)
        if (fs <= 0.0) return
        ctx.save()
        ctx.translate(lead, Art.faceOffset(look.shape, s) - lift)
        Art.face(ctx, look.face, g.face, s * fs, if (g.face == Face.DOUBLE) boost else player)
        ctx.restore()
    }

    /**
     * The runner, upside down on the floor it is over - the same silhouette and
     * the same face, because a reflection that is only a blur says nothing about
     * which way up the player is. It fades and shrinks with height, so it is
     * also the read for how far there is left to fall.
     */
    private fun drawReflection(g: Game) {
        if (reduceEffects || g.state != GameState.RUNNING) return
        val ground = groundUnder(g) ?: return
        val height = g.y - ground
        if (height > 4.6) return
        val fade = (1.0 - height / 4.6).coerceIn(0.0, 1.0)
        val s = scale * Tuning.PLAYER_SIZE
        val gy = sy(ground)
        ctx.save()
        ctx.beginPath()
        ctx.rect(0.0, gy, w, h)              // never above the surface
        ctx.clip()
        // Compressed towards the surface rather than a true mirror distance: at
        // the top of a double jump a true reflection is off the bottom of the
        // screen, which tells the player nothing. Compressed, it stays visible
        // and still shrinks and fades the higher they are.
        ctx.translate(sx(g.x + 0.5), gy + (gy - sy(g.y + 0.5)) * 0.42)
        ctx.scale(1.0, -1.0)
        ctx.globalAlpha = 0.26 * fade
        ctx.save()
        ctx.rotate(g.rotationDeg * PI / 180.0)
        ctx.fillStyle = Palette.playerFill(look.colour)
        ctx.strokeStyle = player
        ctx.lineWidth = 2.5
        Art.shapePath(ctx, look.shape, s)
        ctx.fill(); ctx.stroke()
        ctx.restore()
        // The face does not spin in the mirror either.
        val fs = Art.faceScale(look.shape)
        if (fs > 0.0) {
            ctx.globalAlpha = 0.22 * fade
            ctx.translate(0.0, Art.faceOffset(look.shape, s))
            Art.face(ctx, look.face, g.face, s * fs, player)
        }
        ctx.restore()
        ctx.globalAlpha = 1.0
    }

    /**
     * A tint, not a flash. Under a tenth of a second and never past 14% opacity,
     * so a jump feels like it lit the room without hiding what is coming.
     */
    private fun drawFlash() {
        if (flash <= 0.0 || reducedMotion) return
        val a = (flash / flashMax).coerceIn(0.0, 1.0)
        val peak = if (flashBoost) 0.14 else 0.075
        ctx.globalAlpha = a * a * peak
        ctx.fillStyle = if (flashBoost) boost else player
        ctx.fillRect(0.0, 0.0, w, h)
        ctx.globalAlpha = 1.0
    }

    private fun drawHud(g: Game) {
        val pad = uiH * 0.045
        ctx.textAlign = CanvasTextAlign.LEFT
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${uiH * 0.048}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("LEVEL ${g.level.id}", pad, pad + uiH * 0.045)
        ctx.fillStyle = "#8a7fd6"
        ctx.font = "600 ${uiH * 0.028}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText(g.level.name, pad, pad + uiH * 0.082)

        val bw = w * 0.34; val bx = (w - bw) / 2; val by = pad + uiH * 0.01; val bh = uiH * 0.032
        ctx.strokeStyle = "#5ad6ff"; ctx.lineWidth = 2.0
        roundRect(bx, by, bw, bh, bh / 2); ctx.stroke()
        ctx.fillStyle = "#5ad6ff"
        if (g.progress > 0.004) { roundRect(bx + 2, by + 2, (bw - 4) * g.progress, bh - 4, (bh - 4) / 2); ctx.fill() }
        if (g.bestProgress > 0.01 && g.bestProgress < 0.999) {
            val mx = bx + 2 + (bw - 4) * g.bestProgress
            ctx.strokeStyle = "#ffd166"
            ctx.beginPath(); ctx.moveTo(mx, by - 3); ctx.lineTo(mx, by + bh + 3); ctx.stroke()
        }
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${uiH * 0.042}px 'Chakra Petch', system-ui, sans-serif"
        ctx.textAlign = CanvasTextAlign.LEFT
        ctx.fillText("${(g.progress * 100).toInt()}%", bx + bw + uiH * 0.028, by + bh * 0.9)

        ctx.textAlign = CanvasTextAlign.RIGHT
        ctx.fillStyle = "#6a6a9a"
        ctx.font = "600 ${uiH * 0.026}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("ATTEMPT ${g.attempts}", w - pad, pad + uiH * 0.045)
        // Small on purpose: during a run the counter is a reminder, not a score.
        ctx.fillStyle = gold
        ctx.font = "700 ${uiH * 0.030}px 'Chakra Petch', system-ui, sans-serif"
        val carried = coins + g.starsCollected
        ctx.fillText("★ $carried", w - pad, pad + uiH * 0.088)
    }

    private fun reason(c: DeathCause) = when (c) {
        DeathCause.SPIKE -> "YOU HIT A SPIKE"
        DeathCause.CEILING_SPIKE -> "YOU JUMPED INTO THE CEILING"
        DeathCause.PIT -> "YOU MISSED THE JUMP"
        DeathCause.WALL -> "YOU RAN INTO THE WALL"
        DeathCause.NONE -> ""
    }

    private fun drawDeath(g: Game) {
        val a = min(0.78, g.stateTime * 3.2)
        ctx.fillStyle = "rgba(5,6,15,$a)"
        ctx.fillRect(0.0, 0.0, w, h)
        ctx.textAlign = CanvasTextAlign.CENTER
        ctx.fillStyle = hazard
        ctx.font = "800 ${uiH * 0.13}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("GAME OVER", w / 2, h * 0.36)
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${uiH * 0.042}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText(reason(g.deathCause), w / 2, h * 0.47)
        ctx.fillStyle = "#8a7fd6"
        ctx.font = "600 ${uiH * 0.034}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("${(g.progress * 100).toInt()}%  ·  BEST ${(g.bestProgress * 100).toInt()}%", w / 2, h * 0.55)
        if (g.canRetry) {
            val pulse = 0.72 + 0.28 * abs(sin(g.stateTime * 3.0))
            ctx.globalAlpha = pulse
            ctx.fillStyle = "#e8e8ff"
            ctx.font = "800 ${uiH * 0.055}px 'Chakra Petch', system-ui, sans-serif"
            ctx.fillText("TAP ANYWHERE TO RETRY", w / 2, h * 0.70)
            ctx.globalAlpha = 1.0
        }
    }

    /**
     * The beat between crossing the gate and the reward screen coming up. It is
     * deliberately thin: the panel that follows owns the payout and the buttons,
     * and two of anything here would read through it.
     */
    private fun drawComplete(g: Game) {
        ctx.fillStyle = "rgba(5,6,15,0.80)"
        ctx.fillRect(0.0, 0.0, w, h)
        ctx.textAlign = CanvasTextAlign.CENTER
        ctx.fillStyle = finish
        ctx.font = "800 ${uiH * 0.12}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("LEVEL COMPLETE", w / 2, h * 0.46)
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${uiH * 0.038}px 'Chakra Petch', system-ui, sans-serif"
        val t = ((g.elapsed * 1000).toInt() / 1000.0)
        ctx.fillText("TIME ${t}s   ATTEMPTS ${g.attempts}", w / 2, h * 0.56)
    }

    private fun roundRect(x: Double, y: Double, rw: Double, rh: Double, r: Double) {
        ctx.beginPath()
        ctx.moveTo(x + r, y)
        ctx.lineTo(x + rw - r, y); ctx.quadraticCurveTo(x + rw, y, x + rw, y + r)
        ctx.lineTo(x + rw, y + rh - r); ctx.quadraticCurveTo(x + rw, y + rh, x + rw - r, y + rh)
        ctx.lineTo(x + r, y + rh); ctx.quadraticCurveTo(x, y + rh, x, y + rh - r)
        ctx.lineTo(x, y + r); ctx.quadraticCurveTo(x, y, x + r, y)
        ctx.closePath()
    }
}
