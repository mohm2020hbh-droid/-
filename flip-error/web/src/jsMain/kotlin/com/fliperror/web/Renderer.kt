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
    private val hazard = "#ff2e63"
    private val hazardDim = "#5c0f24"
    private val safe = "#31d4f2"
    private val safeFill = "#0a1420"
    private val player = "#ffc93c"
    private val gold = "#ffd166"
    private val boost = "#fff6d8"      // the second jump's own colour, nothing else uses it
    private val finish = "#4ade80"

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
    /** Counts down after a boost; while it runs the trail is longer and brighter. */
    private var boostGlow = 0.0

    private var prevGrounded = true
    private var prevDoubles = 0
    private var wasRunning = true

    var w = 0.0; var h = 0.0

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
        emitTimer = 0.0; trailTimer = 0.0
        prevGrounded = true; prevDoubles = 0; wasRunning = true
    }

    /** World units visible across the frame. Asserted by the playtest. */
    val visibleWorldWidth: Double get() = if (scale > 0.0) w / scale else 0.0

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
        if (justJumped) { stretch = 0.22; kickY = 1.6; burst(g, 9, 4.5, 0.85, 1.2) }
        if (justDoubled) {
            stretch = 0.42
            kickY = 3.4
            boostGlow = 0.42
            burst(g, 20, 7.5, 1.25, 2.4)
            rings.add(doubleArrayOf(g.x + 0.5, g.y + 0.5, 0.26, 0.26))
            // a few long streaks so the second jump reads even in a still frame
            repeat(5) { spark(g.x + 0.5, g.y + 0.4, rnd(-9.0, -4.0), rnd(-1.0, 2.5), rnd(0.26, 0.42), 0.30, 2) }
        }
        if (justLanded) { stretch = -0.30; kickY = 2.2; burst(g, 8, 4.0, 0.55, 0.5) }

        prevGrounded = g.grounded
        prevDoubles = g.doubleJumps
        wasRunning = g.state == GameState.RUNNING

        // --- decay ----------------------------------------------------------
        stretch += (0.0 - stretch) * min(1.0, dt * 13.0)
        kickY += (0.0 - kickY) * min(1.0, dt * 11.0)
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
                spark(g.x + rnd(0.05, 0.4), y, rnd(-6.5, -3.0), rnd(0.2, 1.9),
                    rnd(0.22, 0.40), rnd(0.10, 0.20), if (Random.nextInt(4) == 0) 2 else 1)
            }
            shardsSpawned = false
        } else if (g.state == GameState.DEAD && !shardsSpawned) {
            shardsSpawned = true
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
            p[3] -= 9.0 * dt                     // light gravity: dust settles, it does not plummet
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
        originX = w * playerScreenFraction
        camX = g.x

        drawBackground(g)
        ctx.save()
        ctx.translate(0.0, kickY)
        drawLevel(g.level)
        drawShadow(g)
        drawTrail(g)
        drawParticles(g)
        drawRings(g)
        if (g.state != GameState.DEAD) drawPlayer(g)
        drawShards()
        ctx.restore()
        drawHud(g)
        if (g.state == GameState.DEAD) drawDeath(g)
        if (g.state == GameState.COMPLETE) drawComplete(g)
    }

    private fun drawBackground(g: Game) {
        val grad = ctx.createLinearGradient(0.0, 0.0, 0.0, h)
        grad.addColorStop(0.0, "#0a0a1c")
        grad.addColorStop(0.55, "#120a24")
        grad.addColorStop(1.0, "#05060f")
        ctx.fillStyle = grad
        ctx.fillRect(0.0, 0.0, w, h)

        // Far skyline: dim, slow, and never bright enough to compete with a spike.
        ctx.globalAlpha = 0.20
        ctx.fillStyle = "#241a4a"
        val par = g.x * 0.15
        var i = -2
        while (i < 40) {
            val bx = (i * 34.0 - par % 34.0) * (scale / 60.0) * 1.6
            val bh = h * (0.12 + 0.07 * ((i * 7) % 5))
            ctx.fillRect(bx, h * 0.52 - bh, 26.0 * (scale / 60.0), bh + h)
            i++
        }

        // A nearer layer of thin streaks, moving much faster. This is the only
        // place the sense of speed comes from for free - it costs no readability
        // because it lives above the play line and stays under the vignette.
        ctx.globalAlpha = 0.13
        ctx.fillStyle = "#4a3a8c"
        val fast = g.x * 0.62
        var k = -2
        while (k < 30) {
            val bx = w - ((k * 41.0 - fast % 41.0) * (scale / 60.0)) % (w + 120.0)
            val by = h * (0.06 + 0.042 * ((k * 11) % 8))
            ctx.fillRect(bx, by, 22.0 * (scale / 60.0), 2.0)
            k++
        }
        ctx.globalAlpha = 1.0

        // Readability vignette behind the play line.
        val v = ctx.createLinearGradient(0.0, h * 0.18, 0.0, h * 0.95)
        v.addColorStop(0.0, "rgba(5,6,15,0)")
        v.addColorStop(0.45, "rgba(5,6,15,0.72)")
        v.addColorStop(1.0, "rgba(5,6,15,0.92)")
        ctx.fillStyle = v
        ctx.fillRect(0.0, h * 0.18, w, h * 0.82)
    }

    private fun drawLevel(level: Level) {
        val left = camX - originX / scale - 2.0
        val right = camX + (w - originX) / scale + 2.0

        ctx.lineWidth = 2.5
        level.forEachSolidNear(left, right) { s ->
            val x0 = sx(s.x0); val x1 = sx(s.x1); val yTop = sy(s.top)
            val bottom = sy(maxOf(s.bottom, camY - viewHeight))
            ctx.fillStyle = safeFill
            ctx.fillRect(x0, yTop, x1 - x0, bottom - yTop)
            ctx.shadowBlur = 10.0; ctx.shadowColor = safe
            ctx.strokeStyle = safe
            ctx.beginPath()
            ctx.moveTo(x0, yTop); ctx.lineTo(x1, yTop)
            ctx.stroke()
            ctx.shadowBlur = 0.0
            ctx.strokeStyle = "rgba(49,212,242,0.35)"
            ctx.beginPath()
            ctx.moveTo(x0, yTop); ctx.lineTo(x0, bottom)
            ctx.moveTo(x1, yTop); ctx.lineTo(x1, bottom)
            ctx.stroke()
        }

        level.forEachHazardNear(left, right) { hz ->
            val b = hz.drawBox
            ctx.fillStyle = hazardDim
            ctx.strokeStyle = hazard
            ctx.shadowBlur = 8.0; ctx.shadowColor = hazard
            ctx.beginPath()
            if (hz.kind == HazardKind.SPIKE_UP) {
                ctx.moveTo(sx(b.x0), sy(b.y0))
                ctx.lineTo(sx((b.x0 + b.x1) / 2), sy(b.y1))
                ctx.lineTo(sx(b.x1), sy(b.y0))
            } else {
                ctx.moveTo(sx(b.x0), sy(b.y1))
                ctx.lineTo(sx((b.x0 + b.x1) / 2), sy(b.y0))
                ctx.lineTo(sx(b.x1), sy(b.y1))
            }
            ctx.closePath(); ctx.fill(); ctx.stroke()
            ctx.shadowBlur = 0.0
        }

        for (st in level.stars) {
            if (st.x < left || st.x > right) continue
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

    /** Highest surface under the runner, or null over a pit. */
    private fun groundUnder(g: Game): Double? {
        var best: Double? = null
        g.level.forEachSolidNear(g.x, g.x + Tuning.PLAYER_SIZE) { s ->
            if (s.top <= g.y + 1e-6) { val b = best; if (b == null || s.top > b) best = s.top }
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
        ctx.fillStyle = "rgba(255,201,60,${0.05 + 0.17 * near})"
        ctx.fill()
        ctx.strokeStyle = "rgba(255,201,60,${0.16 + 0.34 * near})"
        ctx.lineWidth = 1.5
        ctx.stroke()
        ctx.lineWidth = 2.5
    }

    /** Ghost copies of the runner, each smaller and fainter than the last. */
    private fun drawTrail(g: Game) {
        val front = sx(g.x + Tuning.PLAYER_SIZE)
        ctx.lineWidth = 2.0
        for (t in trail) {
            val a = (t[2] / t[3]).coerceIn(0.0, 1.0)
            val px = sx(t[0] + 0.5)
            if (px > front) continue                      // never ahead of the runner
            val size = scale * Tuning.PLAYER_SIZE * (0.26 + 0.62 * a)
            ctx.globalAlpha = a.pow(1.3) * (if (boostGlow > 0.0) 0.88 else 0.64)
            ctx.lineWidth = 2.0 + a
            ctx.strokeStyle = if (boostGlow > 0.0) boost else player
            ctx.save()
            ctx.translate(px, sy(t[1] + 0.5))
            ctx.rotate(t[4] * PI / 180.0)
            roundSquare(size, size * 0.18)
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
            ctx.fillStyle = if (p[7] == 1.0) boost else gold
            val s = scale * p[6] * (0.4 + 0.6 * a)
            when (p[7].toInt()) {
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
        ctx.shadowBlur = 16.0; ctx.shadowColor = if (g.face == Face.DOUBLE) boost else player
        ctx.fillStyle = "#1a1405"
        ctx.strokeStyle = if (g.face == Face.DOUBLE) boost else player
        ctx.lineWidth = 3.0
        roundSquare(s, 4.0)
        ctx.fill(); ctx.stroke()
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
        val face = g.face
        ctx.fillStyle = if (face == Face.DOUBLE) boost else player
        val eye = s * 0.13
        val ey = -s * 0.10
        // A little life: the eyes lead the run, and look up on the way up.
        val look = if (g.grounded) sin(runPhase * PI * 2) * s * 0.022 else 0.0
        val lift = if (!g.grounded) (g.vy / Tuning.JUMP_VELOCITY).coerceIn(-1.0, 1.0) * s * 0.03 else 0.0
        when (face) {
            Face.RUN -> {
                ctx.fillRect(-s * 0.22 - eye / 2 + look, ey - eye / 2, eye, eye)
                ctx.fillRect(s * 0.22 - eye / 2 + look, ey - eye / 2, eye, eye)
                ctx.strokeStyle = player; ctx.lineWidth = s * 0.07
                ctx.beginPath(); ctx.arc(0.0, s * 0.06, s * 0.20, 0.15 * PI, 0.85 * PI); ctx.stroke()
            }
            Face.JUMP -> {
                ctx.fillRect(-s * 0.24 - eye / 2, ey - eye * 0.8 - lift, eye, eye * 1.5)
                ctx.fillRect(s * 0.24 - eye / 2, ey - eye * 0.8 - lift, eye, eye * 1.5)
                ctx.beginPath(); ctx.ellipse(0.0, s * 0.12, s * 0.15, s * 0.17, 0.0, 0.0, PI * 2); ctx.fill()
            }
            // The boost gets its own face for the moment it lasts: eyes wide,
            // mouth open. Even muted, the second jump is unmistakable.
            Face.DOUBLE -> {
                ctx.beginPath()
                ctx.arc(-s * 0.23, ey - s * 0.02, eye * 0.85, 0.0, PI * 2)
                ctx.arc(s * 0.23, ey - s * 0.02, eye * 0.85, 0.0, PI * 2)
                ctx.fill()
                ctx.beginPath(); ctx.ellipse(0.0, s * 0.15, s * 0.13, s * 0.20, 0.0, 0.0, PI * 2); ctx.fill()
            }
            Face.DEAD -> {
                ctx.strokeStyle = hazard; ctx.lineWidth = s * 0.07
                for (sgn in listOf(-1.0, 1.0)) {
                    val ox = sgn * s * 0.22
                    ctx.beginPath()
                    ctx.moveTo(ox - eye, ey - eye); ctx.lineTo(ox + eye, ey + eye)
                    ctx.moveTo(ox + eye, ey - eye); ctx.lineTo(ox - eye, ey + eye)
                    ctx.stroke()
                }
                ctx.beginPath(); ctx.arc(0.0, s * 0.24, s * 0.17, 1.15 * PI, 1.85 * PI); ctx.stroke()
            }
        }
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

    private fun drawComplete(g: Game) {
        ctx.fillStyle = "rgba(5,6,15,0.82)"
        ctx.fillRect(0.0, 0.0, w, h)
        ctx.textAlign = CanvasTextAlign.CENTER
        ctx.fillStyle = finish
        ctx.font = "800 ${uiH * 0.13}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("LEVEL COMPLETE", w / 2, h * 0.33)
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${uiH * 0.040}px 'Chakra Petch', system-ui, sans-serif"
        val t = ((g.elapsed * 1000).toInt() / 1000.0)
        ctx.fillText("TIME ${t}s   ATTEMPTS ${g.attempts}   STAR ${g.starsCollected}/${g.level.stars.size}",
            w / 2, h * 0.45)
        val bw = w * 0.36; val bh = uiH * 0.13; val bx = (w - bw) / 2; val by = h * 0.56
        ctx.fillStyle = "rgba(74,222,128,0.16)"
        ctx.strokeStyle = finish; ctx.lineWidth = 3.0
        roundRect(bx, by, bw, bh, bh * 0.3); ctx.fill(); ctx.stroke()
        ctx.fillStyle = finish
        ctx.font = "800 ${uiH * 0.055}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("NEXT LEVEL", w / 2, by + bh * 0.64)
        ctx.fillStyle = "#6a6a9a"
        ctx.font = "600 ${uiH * 0.026}px 'Chakra Petch', system-ui, sans-serif"
        ctx.fillText("LEVEL 2 IS NOT BUILT YET — TAP TO REPLAY LEVEL 1", w / 2, by + bh + uiH * 0.07)
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
