package com.fliperror.web

import com.fliperror.core.DeviceOrientation
import com.fliperror.core.GateReason
import com.fliperror.core.Game
import com.fliperror.core.GameState
import com.fliperror.core.Level1
import com.fliperror.core.ViewportProbe
import com.fliperror.core.blocksPlay
import com.fliperror.core.decideGate
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.min

/** Seconds the rotate screen may stay up before it offers a way past itself. */
private const val ESCAPE_HATCH_DELAY_MS = 2500

/**
 * Ask the device what it is, and settle for UNKNOWN rather than guessing.
 * `screen` reports the physical display even from inside an embedded frame,
 * which is exactly the signal a viewport measurement cannot give us.
 */
private fun readDeviceOrientation(): DeviceOrientation {
    val type = window.screen.asDynamic().orientation?.type as? String
    if (type != null) {
        if (type.startsWith("landscape")) return DeviceOrientation.LANDSCAPE
        if (type.startsWith("portrait")) return DeviceOrientation.PORTRAIT
    }
    val sw = window.screen.width
    val sh = window.screen.height
    if (sw <= 0 || sh <= 0) return DeviceOrientation.UNKNOWN
    return if (sw > sh) DeviceOrientation.LANDSCAPE else DeviceOrientation.PORTRAIT
}

/** A phone is coarse-pointered *and* multi-touch; a touchscreen laptop is not. */
private fun readHandheldPointer(): Boolean {
    val coarse = window.matchMedia("(pointer: coarse)").matches
    val points = (window.navigator.asDynamic().maxTouchPoints as? Int) ?: 0
    return coarse && points > 0
}

/** Development and automation only — never a control the player is shown. */
private fun readDevOverride(): Boolean {
    if (window.asDynamic().FLIP_FORCE_PLAY == true) return true
    val loc = window.location.search + window.location.hash
    return loc.contains("play=1") || loc.contains("#play")
}

fun main() {
    val canvas = document.getElementById("c") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val rotate = document.getElementById("rotate") as HTMLElement
    val escape = document.getElementById("escape") as HTMLElement
    val renderer = Renderer(ctx)

    val game = Game(Level1.build())
    var lastAttempt = game.attempts

    // The play area, not the frame around it: a host that pads the page for a
    // notch gives the canvas a different box than the window, and the gate must
    // judge the box the game is actually drawn into.
    fun viewW() = if (canvas.clientWidth > 0) canvas.clientWidth else window.innerWidth
    fun viewH() = if (canvas.clientHeight > 0) canvas.clientHeight else window.innerHeight

    fun resize() {
        val dpr = min(window.devicePixelRatio, 2.0)
        val cssW = viewW().toDouble()
        val cssH = viewH().toDouble()
        canvas.width = (cssW * dpr).toInt()
        canvas.height = (cssH * dpr).toInt()
        ctx.setTransform(dpr, 0.0, 0.0, dpr, 0.0, 0.0)
        renderer.w = cssW
        renderer.h = cssH
    }

    // --- the orientation gate ------------------------------------------------
    var playerOverride = false
    var reason = GateReason.VIEWPORT_UNMEASURED
    var gated = false
    var gatedSince = 0.0
    var last = 0.0

    fun probe() = ViewportProbe(
        width = viewW(),
        height = viewH(),
        device = readDeviceOrientation(),
        handheldPointer = readHandheldPointer(),
        override = playerOverride || readDevOverride(),
    )

    fun evaluateGate() {
        reason = decideGate(probe())
        val blocked = reason.blocksPlay
        if (blocked == gated) {
            // Still gated: offer the way out once waiting stops looking temporary.
            if (blocked && window.performance.now() - gatedSince > ESCAPE_HATCH_DELAY_MS) {
                escape.hidden = false
            }
            return
        }
        gated = blocked
        rotate.hidden = !blocked
        if (blocked) {
            gatedSince = window.performance.now()
            escape.hidden = true
        } else {
            // The run that was paused behind the screen is not the player's;
            // they start the level, not the middle of a corpse.
            escape.hidden = true
            resize()
            game.restart()
            renderer.resetRun()
            last = 0.0
        }
    }

    resize()
    evaluateGate()

    window.addEventListener("resize", { resize(); evaluateGate() })
    window.addEventListener("orientationchange", { resize(); evaluateGate() })
    window.screen.asDynamic().orientation?.addEventListener("change", { resize(); evaluateGate() })
    // Belt and braces: some embedders resize their frame without firing anything
    // we can subscribe to, and a gate that cannot re-check is a gate that sticks.
    window.setInterval({ evaluateGate() }, 500)

    escape.addEventListener("click", { e ->
        e.preventDefault()
        playerOverride = true
        evaluateGate()
    })

    // --- input: one handler, every pointer, no delay and no gesture recognition
    val tap: (Event) -> Unit = { e ->
        e.preventDefault()
        if (!gated) {
            if (game.state == GameState.COMPLETE) game.restart() else game.onTap()
            Audio.resume()
        }
    }
    canvas.addEventListener("pointerdown", tap)
    canvas.addEventListener("touchstart", tap)
    window.addEventListener("keydown", { e ->
        val k = e as KeyboardEvent
        if (k.code == "Space" || k.code == "ArrowUp" || k.code == "Enter") {
            e.preventDefault()
            if (!gated) {
                if (game.state == GameState.COMPLETE) game.restart() else game.onTap()
                Audio.resume()
            }
        }
    })

    var prevState = game.state
    fun frame(now: Double) {
        if (!gated) {
            val dt = if (last == 0.0) 1.0 / 60.0 else ((now - last) / 1000.0).coerceIn(0.0, 0.1)
            last = now

            val jumpedBefore = game.grounded
            game.update(dt)
            if (jumpedBefore && !game.grounded && game.state == GameState.RUNNING) Audio.jump()
            if (prevState == GameState.RUNNING && game.state == GameState.DEAD) Audio.death()
            if (prevState == GameState.RUNNING && game.state == GameState.COMPLETE) Audio.finish()
            prevState = game.state

            if (game.attempts != lastAttempt) { lastAttempt = game.attempts; renderer.resetRun() }

            renderer.update(game, dt)
            renderer.draw(game)
        }
        window.requestAnimationFrame(::frame)
    }
    window.requestAnimationFrame(::frame)

    // Exposed so the automated playtest can drive and inspect a real run.
    window.asDynamic().FLIP = js("({})")
    val api = window.asDynamic().FLIP
    api.tap = { game.onTap() }
    api.state = { game.state.name }
    api.progress = { game.progress }
    api.x = { game.x }
    api.y = { game.y }
    api.vy = { game.vy }
    api.grounded = { game.grounded }
    api.face = { game.face.name }
    api.cause = { game.deathCause.name }
    api.attempts = { game.attempts }
    api.taps = { game.taps }
    api.restart = { game.restart() }
    api.elapsed = { game.elapsed }
    api.stateTime = { game.stateTime }
    api.finishX = { game.level.finishX }
    api.gated = { gated }
    api.gateReason = { reason.name }
    api.recheckGate = { evaluateGate() }
    api.escapeVisible = { !escape.hidden }
    api.viewUnits = { renderer.visibleWorldWidth }
    api.uiHeight = { renderer.uiHeight }
    api.probe = {
        val p = probe()
        val o = js("({})")
        o.width = p.width
        o.height = p.height
        o.device = p.device.name
        o.handheldPointer = p.handheldPointer
        o.override = p.override
        o
    }
}
