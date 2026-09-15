package com.fliperror.web

import com.fliperror.core.*
import org.w3c.dom.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Dark neon geometric renderer. GDD 11: readability outranks graphics.
 * The gameplay layer is drawn crisp and unblurred over a dim background, and
 * nothing decorative is ever drawn inside the runner's forward corridor.
 */
class Renderer(private val ctx: CanvasRenderingContext2D) {

    // GDD 11.2: hot colours are reserved for death, everywhere, always.
    private val hazard = "#ff2e63"
    private val hazardDim = "#5c0f24"
    private val safe = "#31d4f2"
    private val safeFill = "#0a1420"
    private val player = "#ffc93c"
    private val gold = "#ffd166"
    private val finish = "#4ade80"
    private val ink = "#05060f"

    /** Visible world height in units. Keeps ~2.2s of track ahead of the runner. */
    private val viewHeight = 13.0
    private val playerScreenFraction = 0.22

    private var camY = 0.0
    private var groundRefY = 0.0
    private val trail = ArrayList<DoubleArray>()
    private val shards = ArrayList<DoubleArray>()
    private var shardsSpawned = false

    var w = 0.0; var h = 0.0

    private var scale = 60.0
    private var originX = 0.0

    fun resetRun() { trail.clear(); shards.clear(); shardsSpawned = false }

    fun update(g: Game, dt: Double) {
        if (g.grounded) groundRefY = g.y
        // The camera tracks the floor, not the runner, so a jump never moves the
        // frame during a precision beat (GDD 1.5).
        val target = if (g.y < groundRefY - 4.0) g.y else groundRefY
        camY += (target - camY) * min(1.0, dt * 9.0)

        if (g.state == GameState.RUNNING) {
            trail.add(doubleArrayOf(g.x, g.y, 0.28))
            var i = 0
            while (i < trail.size) {
                trail[i][2] -= dt
                if (trail[i][2] <= 0) trail.removeAt(i) else i++
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
        var i = 0
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
        scale = h / viewHeight
        originX = w * playerScreenFraction
        camX = g.x

        drawBackground(g)
        drawLevel(g.level)
        drawTrail()
        if (g.state != GameState.DEAD) drawPlayer(g)
        drawShards()
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

    private fun drawTrail() {
        for (t in trail) {
            val a = (t[2] / 0.28).coerceIn(0.0, 1.0)
            ctx.globalAlpha = a * 0.5
            ctx.fillStyle = player
            val s = scale * 0.22 * a
            ctx.fillRect(sx(t[0] + 0.5) - s / 2, sy(t[1] + 0.5) - s / 2, s, s)
        }
        ctx.globalAlpha = 1.0
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

    private fun drawPlayer(g: Game) {
        val cx = sx(g.x + 0.5); val cy = sy(g.y + 0.5)
        val s = scale * Tuning.PLAYER_SIZE
        ctx.save()
        ctx.translate(cx, cy)
        ctx.rotate(g.rotationDeg * PI / 180.0)
        ctx.shadowBlur = 16.0; ctx.shadowColor = player
        ctx.fillStyle = "#1a1405"
        ctx.strokeStyle = player; ctx.lineWidth = 3.0
        val r = s / 2
        ctx.beginPath()
        ctx.moveTo(-r + 4, -r); ctx.lineTo(r - 4, -r); ctx.quadraticCurveTo(r, -r, r, -r + 4)
        ctx.lineTo(r, r - 4); ctx.quadraticCurveTo(r, r, r - 4, r)
        ctx.lineTo(-r + 4, r); ctx.quadraticCurveTo(-r, r, -r, r - 4)
        ctx.lineTo(-r, -r + 4); ctx.quadraticCurveTo(-r, -r, -r + 4, -r)
        ctx.closePath(); ctx.fill(); ctx.stroke()
        ctx.shadowBlur = 0.0
        ctx.restore()
        // The body spins so the rotation reads as timing, but the face does not:
        // a sideways face cannot do the job GDD 2.2 gives it.
        ctx.save()
        ctx.translate(cx, cy)
        drawFace(g.face, s)
        ctx.restore()
    }

    /** GDD 2.2: the face is a readability element, so it must stay legible at phone size. */
    private fun drawFace(face: Face, s: Double) {
        ctx.fillStyle = player
        val eye = s * 0.13
        val ey = -s * 0.10
        when (face) {
            Face.RUN -> {
                ctx.fillRect(-s * 0.22 - eye / 2, ey - eye / 2, eye, eye)
                ctx.fillRect(s * 0.22 - eye / 2, ey - eye / 2, eye, eye)
                ctx.strokeStyle = player; ctx.lineWidth = s * 0.07
                ctx.beginPath(); ctx.arc(0.0, s * 0.06, s * 0.20, 0.15 * PI, 0.85 * PI); ctx.stroke()
            }
            Face.JUMP -> {
                ctx.fillRect(-s * 0.24 - eye / 2, ey - eye * 0.8, eye, eye * 1.5)
                ctx.fillRect(s * 0.24 - eye / 2, ey - eye * 0.8, eye, eye * 1.5)
                ctx.beginPath(); ctx.ellipse(0.0, s * 0.12, s * 0.15, s * 0.17, 0.0, 0.0, PI * 2); ctx.fill()
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
        val pad = h * 0.045
        ctx.textAlign = CanvasTextAlign.LEFT
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${h * 0.048}px system-ui, sans-serif"
        ctx.fillText("LEVEL ${g.level.id}", pad, pad + h * 0.045)
        ctx.fillStyle = "#8a7fd6"
        ctx.font = "600 ${h * 0.028}px system-ui, sans-serif"
        ctx.fillText(g.level.name, pad, pad + h * 0.082)

        val bw = w * 0.34; val bx = (w - bw) / 2; val by = pad + h * 0.01; val bh = h * 0.032
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
        ctx.font = "700 ${h * 0.042}px system-ui, sans-serif"
        ctx.textAlign = CanvasTextAlign.LEFT
        ctx.fillText("${(g.progress * 100).toInt()}%", bx + bw + h * 0.028, by + bh * 0.9)

        ctx.textAlign = CanvasTextAlign.RIGHT
        ctx.fillStyle = "#6a6a9a"
        ctx.font = "600 ${h * 0.026}px system-ui, sans-serif"
        ctx.fillText("ATTEMPT ${g.attempts}", w - pad, pad + h * 0.045)
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
        ctx.font = "800 ${h * 0.13}px system-ui, sans-serif"
        ctx.fillText("GAME OVER", w / 2, h * 0.36)
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${h * 0.042}px system-ui, sans-serif"
        ctx.fillText(reason(g.deathCause), w / 2, h * 0.47)
        ctx.fillStyle = "#8a7fd6"
        ctx.font = "600 ${h * 0.034}px system-ui, sans-serif"
        ctx.fillText("${(g.progress * 100).toInt()}%  ·  BEST ${(g.bestProgress * 100).toInt()}%", w / 2, h * 0.55)
        if (g.canRetry) {
            val pulse = 0.72 + 0.28 * abs(sin(g.stateTime * 3.0))
            ctx.globalAlpha = pulse
            ctx.fillStyle = "#e8e8ff"
            ctx.font = "800 ${h * 0.055}px system-ui, sans-serif"
            ctx.fillText("TAP ANYWHERE TO RETRY", w / 2, h * 0.70)
            ctx.globalAlpha = 1.0
        }
    }

    private fun drawComplete(g: Game) {
        ctx.fillStyle = "rgba(5,6,15,0.82)"
        ctx.fillRect(0.0, 0.0, w, h)
        ctx.textAlign = CanvasTextAlign.CENTER
        ctx.fillStyle = finish
        ctx.font = "800 ${h * 0.13}px system-ui, sans-serif"
        ctx.fillText("LEVEL COMPLETE", w / 2, h * 0.33)
        ctx.fillStyle = "#e8e8ff"
        ctx.font = "700 ${h * 0.040}px system-ui, sans-serif"
        val t = ((g.elapsed * 1000).toInt() / 1000.0)
        ctx.fillText("TIME ${t}s   ATTEMPTS ${g.attempts}   STAR ${g.starsCollected}/${g.level.stars.size}",
            w / 2, h * 0.45)
        val bw = w * 0.36; val bh = h * 0.13; val bx = (w - bw) / 2; val by = h * 0.56
        ctx.fillStyle = "rgba(74,222,128,0.16)"
        ctx.strokeStyle = finish; ctx.lineWidth = 3.0
        roundRect(bx, by, bw, bh, bh * 0.3); ctx.fill(); ctx.stroke()
        ctx.fillStyle = finish
        ctx.font = "800 ${h * 0.055}px system-ui, sans-serif"
        ctx.fillText("NEXT LEVEL", w / 2, by + bh * 0.64)
        ctx.fillStyle = "#6a6a9a"
        ctx.font = "600 ${h * 0.026}px system-ui, sans-serif"
        ctx.fillText("LEVEL 2 IS NOT BUILT YET — TAP TO REPLAY LEVEL 1", w / 2, by + bh + h * 0.07)
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
