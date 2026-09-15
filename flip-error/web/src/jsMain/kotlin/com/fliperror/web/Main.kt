package com.fliperror.web

import com.fliperror.core.Game
import com.fliperror.core.GameState
import com.fliperror.core.Level1
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.min

/**
 * Browser playtest shell for the FLIP ERROR core.
 * The whole surface is the button; there is no separate jump control.
 */
fun main() {
    val canvas = document.getElementById("c") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val renderer = Renderer(ctx)

    val game = Game(Level1.build())
    var lastAttempt = game.attempts

    fun resize() {
        val dpr = min(window.devicePixelRatio, 2.0)
        val cssW = window.innerWidth.toDouble()
        val cssH = window.innerHeight.toDouble()
        canvas.width = (cssW * dpr).toInt()
        canvas.height = (cssH * dpr).toInt()
        ctx.setTransform(dpr, 0.0, 0.0, dpr, 0.0, 0.0)
        renderer.w = cssW
        renderer.h = cssH
    }
    resize()
    window.addEventListener("resize", { resize() })

    // Input: one handler, every pointer, no delay and no gesture recognition.
    val tap: (Event) -> Unit = { e ->
        e.preventDefault()
        if (game.state == GameState.COMPLETE) game.restart() else game.onTap()
        Audio.resume()
    }
    canvas.addEventListener("pointerdown", tap)
    canvas.addEventListener("touchstart", tap)
    window.addEventListener("keydown", { e ->
        val k = e as KeyboardEvent
        if (k.code == "Space" || k.code == "ArrowUp" || k.code == "Enter") {
            e.preventDefault()
            if (game.state == GameState.COMPLETE) game.restart() else game.onTap()
            Audio.resume()
        }
    })

    var last = 0.0
    var prevState = game.state
    fun frame(now: Double) {
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
}
