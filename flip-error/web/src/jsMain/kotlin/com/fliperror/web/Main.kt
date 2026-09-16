package com.fliperror.web

import com.fliperror.core.Category
import com.fliperror.core.DeviceOrientation
import com.fliperror.core.GateReason
import com.fliperror.core.Game
import com.fliperror.core.GameState
import com.fliperror.core.Level
import com.fliperror.core.Level1
import com.fliperror.core.Level2
import com.fliperror.core.Progress
import com.fliperror.core.ViewportProbe
import com.fliperror.core.blocksPlay
import com.fliperror.core.decideGate
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.get
import org.w3c.dom.set
import kotlin.math.min

/** Seconds the rotate screen may stay up before it offers a way past itself. */
private const val ESCAPE_HATCH_DELAY_MS = 2500
private const val SAVE_KEY = "flip-error.progress.v1"

private enum class Screen { MENU, PLAYING, SHOP, REWARD }

private class LevelDef(val card: LevelCard, val build: (() -> Level)?)

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

/** Development and automation only - never a control the player is shown. */
private fun readDevOverride(): Boolean {
    if (window.asDynamic().FLIP_FORCE_PLAY == true) return true
    val loc = window.location.search + window.location.hash
    return loc.contains("play=1") || loc.contains("#play")
}

/** Storage can be absent, full, or refuse to answer. None of that is fatal. */
private fun loadSave(): String? = try { localStorage[SAVE_KEY] } catch (e: Throwable) { null }
private fun writeSave(v: String) { try { localStorage[SAVE_KEY] = v } catch (e: Throwable) { } }

fun main() {
    val canvas = document.getElementById("c") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val rotate = document.getElementById("rotate") as HTMLElement
    val escape = document.getElementById("escape") as HTMLElement
    val backBtn = document.getElementById("back") as HTMLElement
    val renderer = Renderer(ctx)
    renderer.reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches

    val progress = Progress.parse(loadSave())
    val levels = listOf(
        LevelDef(LevelCard(1, "FIRST STEPS HURT", 3, true)) { Level1.build() },
        LevelDef(LevelCard(2, "GAP LOGIC", 3, true)) { Level2.build() },
        LevelDef(LevelCard(3, "NOT BUILT YET", 3, false), null),
    )

    var screen = Screen.MENU
    var game = Game(Level1.build())
    var currentLevel = 1
    var awarded = false
    var rewardAt = -1.0
    var lastAttempt = game.attempts

    fun save() = writeSave(progress.serialize())

    fun applyLook() {
        renderer.look.shape = progress.equipped(Category.SHAPE)
        renderer.look.colour = progress.equipped(Category.COLOR)
        renderer.look.trail = progress.equipped(Category.TRAIL)
        renderer.look.face = progress.equipped(Category.FACE)
        renderer.coins = progress.coins
    }
    applyLook()

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

    lateinit var ui: Ui

    fun showScreen(s: Screen) {
        screen = s
        backBtn.hidden = s != Screen.PLAYING
        when (s) {
            Screen.MENU -> ui.showMenu()
            Screen.SHOP -> ui.showShop()
            Screen.PLAYING -> ui.hideAll()
            Screen.REWARD -> Unit                 // the panel puts itself up
        }
    }

    fun startLevel(id: Int) {
        val def = levels.firstOrNull { it.card.id == id } ?: return
        val build = def.build ?: return
        if (!progress.unlocked(id)) return
        currentLevel = id
        game = Game(build())
        lastAttempt = game.attempts
        awarded = false
        rewardAt = -1.0
        renderer.resetRun()
        applyLook()
        last = 0.0
        showScreen(Screen.PLAYING)
        Audio.resume()
    }

    ui = Ui(progress, levels.map { it.card }, onPlay = ::startLevel, onSave = { save(); applyLook() })

    fun evaluateGate() {
        reason = decideGate(probe())
        val blocked = reason.blocksPlay
        if (blocked == gated) {
            if (blocked && window.performance.now() - gatedSince > ESCAPE_HATCH_DELAY_MS) escape.hidden = false
            return
        }
        gated = blocked
        rotate.hidden = !blocked
        if (blocked) {
            gatedSince = window.performance.now()
            escape.hidden = true
        } else {
            escape.hidden = true
            resize()
            if (screen == Screen.PLAYING) { game.restart(); renderer.resetRun() }
            last = 0.0
        }
    }

    resize()
    evaluateGate()
    showScreen(Screen.MENU)

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
    backBtn.addEventListener("click", { e ->
        e.preventDefault()
        e.stopPropagation()
        showScreen(Screen.MENU)
    })
    backBtn.addEventListener("pointerdown", { e -> e.stopPropagation() })

    // --- input: one handler, every pointer, no delay and no gesture recognition
    fun tapped() {
        if (gated || screen != Screen.PLAYING) return
        if (game.state == GameState.COMPLETE) return       // the reward panel owns this moment
        game.onTap()
        Audio.resume()
    }
    val tap: (Event) -> Unit = { e -> e.preventDefault(); tapped() }
    canvas.addEventListener("pointerdown", tap)
    canvas.addEventListener("touchstart", tap)
    window.addEventListener("keydown", { e ->
        val k = e as KeyboardEvent
        if (k.code == "Space" || k.code == "ArrowUp" || k.code == "Enter") { e.preventDefault(); tapped() }
        if (k.code == "Escape" && screen == Screen.PLAYING) showScreen(Screen.MENU)
    })

    var prevState = game.state
    var prevDoubles = game.doubleJumps
    var prevNear = game.nearMisses
    var prevStars = game.starsCollected

    fun frame(now: Double) {
        if (!gated && screen == Screen.PLAYING) {
            val dt = if (last == 0.0) 1.0 / 60.0 else ((now - last) / 1000.0).coerceIn(0.0, 0.1)
            last = now

            val groundedBefore = game.grounded
            game.update(dt)
            val running = game.state == GameState.RUNNING
            if (groundedBefore && !game.grounded && running) Audio.jump()
            if (!groundedBefore && game.grounded && running) Audio.land()
            if (game.doubleJumps != prevDoubles) { prevDoubles = game.doubleJumps; Audio.doubleJump() }
            if (game.nearMisses != prevNear) { prevNear = game.nearMisses; Audio.nearMiss() }
            if (game.starsCollected != prevStars) { prevStars = game.starsCollected; Audio.star() }
            if (prevState == GameState.RUNNING && game.state == GameState.DEAD) Audio.death()
            if (prevState == GameState.RUNNING && game.state == GameState.COMPLETE) {
                Audio.finish()
                rewardAt = now + 900.0
            }
            prevState = game.state

            if (game.attempts != lastAttempt) {
                lastAttempt = game.attempts
                renderer.resetRun()
                prevDoubles = game.doubleJumps
                prevNear = game.nearMisses
                prevStars = game.starsCollected
            }

            // Pay out once, after the finish has had a moment to land.
            if (game.state == GameState.COMPLETE && !awarded && rewardAt > 0 && now >= rewardAt) {
                awarded = true
                val def = levels.first { it.card.id == currentLevel }
                val award = progress.finish(currentLevel, game.takenStarIndices(), game.attempts)
                save()
                applyLook()
                showScreen(Screen.REWARD)
                ui.showReward(
                    currentLevel, award, game.starsCollected, def.card.coins,
                    nextBuilt = levels.firstOrNull { it.card.id == currentLevel + 1 }?.build != null,
                )
            }

            renderer.update(game, dt)
            renderer.draw(game)
        }
        window.requestAnimationFrame(::frame)
    }
    window.requestAnimationFrame(::frame)

    // Exposed so the automated playtest can drive and inspect a real run.
    window.asDynamic().FLIP = js("({})")
    val api = window.asDynamic().FLIP
    api.tap = { if (screen == Screen.PLAYING) game.onTap() }
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
    api.doubleJumps = { game.doubleJumps }
    api.canDouble = { game.canDoubleJump }
    api.nearMisses = { game.nearMisses }
    api.stars = { game.starsCollected }
    api.effects = { renderer.effectCount }
    api.trailHeadX = { renderer.trailHeadX }
    api.trailTailX = { renderer.trailTailX }
    api.viewUnits = { renderer.visibleWorldWidth }
    api.uiHeight = { renderer.uiHeight }
    api.gated = { gated }
    api.gateReason = { reason.name }
    api.recheckGate = { evaluateGate() }
    api.escapeVisible = { !escape.hidden }
    api.probe = {
        val p = probe()
        val o = js("({})")
        o.width = p.width; o.height = p.height
        o.device = p.device.name; o.handheldPointer = p.handheldPointer; o.override = p.override
        o
    }
    // --- meta game, for the progression harness ---------------------------
    api.screen = { screen.name }
    api.level = { currentLevel }
    api.coins = { progress.coins }
    api.unlocked = { id: Int -> progress.unlocked(id) }
    api.levelStars = { id: Int -> progress.starsIn(id).size }
    api.owns = { id: String -> progress.owns(id) }
    api.equippedOf = { cat: String -> progress.equipped(Category.valueOf(cat)) }
    api.play = { id: Int -> startLevel(id) }
    api.openShop = { showScreen(Screen.SHOP) }
    api.openMenu = { showScreen(Screen.MENU) }
    api.grant = { n: Int -> progress.coins += n; save(); applyLook() }
    api.saved = { loadSave() ?: "" }
    api.wipe = { try { localStorage.removeItem(SAVE_KEY) } catch (e: Throwable) {} }
}
