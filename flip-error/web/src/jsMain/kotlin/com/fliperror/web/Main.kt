package com.fliperror.web

import com.fliperror.core.Category
import com.fliperror.core.DeviceOrientation
import com.fliperror.core.GateReason
import com.fliperror.core.DeathCause
import com.fliperror.core.Game
import com.fliperror.core.GameState
import com.fliperror.core.Level
import com.fliperror.core.Level1
import com.fliperror.core.Tuning
import com.fliperror.core.Level2
import com.fliperror.core.Level3
import com.fliperror.core.Level4
import com.fliperror.core.Level5
import com.fliperror.core.Level6
import com.fliperror.core.Level7
import com.fliperror.core.Level8
import com.fliperror.core.Level9
import com.fliperror.core.Level10
import com.fliperror.core.Level11
import com.fliperror.core.Level12
import com.fliperror.core.Lang
import com.fliperror.core.Progress
import com.fliperror.core.Settings
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
private const val SETTINGS_KEY = "flip-error.settings.v1"

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
private fun read(key: String): String? = try { localStorage[key] } catch (e: Throwable) { null }
private fun write(key: String, v: String) { try { localStorage[key] = v } catch (e: Throwable) { } }
private fun loadSave(): String? = read(SAVE_KEY)
private fun writeSave(v: String) = write(SAVE_KEY, v)

fun main() {
    val canvas = document.getElementById("c") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val rotate = document.getElementById("rotate") as HTMLElement
    val escape = document.getElementById("escape") as HTMLElement
    val backBtn = document.getElementById("back") as HTMLElement
    val renderer = Renderer(ctx)
    renderer.reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches

    val progress = Progress.parse(loadSave())
    val settings = Settings.parse(read(SETTINGS_KEY))
    val levels = listOf(
        // WORLD 1 - NEON CITY
        LevelDef(LevelCard(1, "FIRST STEPS HURT", 3, true)) { Level1.build() },
        LevelDef(LevelCard(2, "GAP LOGIC", 3, true)) { Level2.build() },
        LevelDef(LevelCard(3, "MOVING CHAOS", 3, true)) { Level3.build() },
        LevelDef(LevelCard(4, "TIGHT ROOM", 3, true)) { Level4.build() },
        LevelDef(LevelCard(5, "OVERDRIVE", 3, true)) { Level5.build() },
        LevelDef(LevelCard(6, "SYSTEM CRASH", 3, true)) { Level6.build() },
        // WORLD 2 - NEON DESERT
        LevelDef(LevelCard(7, "SAND RUN", 3, true)) { Level7.build() },
        LevelDef(LevelCard(8, "FALLING TEMPLE", 3, true)) { Level8.build() },
        LevelDef(LevelCard(9, "SUN STRIKE", 3, true)) { Level9.build() },
        LevelDef(LevelCard(10, "DESERT CHAOS", 3, true)) { Level10.build() },
        LevelDef(LevelCard(11, "COLLAPSE", 3, true)) { Level11.build() },
        LevelDef(LevelCard(12, "THE SUN CORE", 3, true)) { Level12.build() },
    )

    var screen = Screen.MENU
    var game = Game(Level1.build())
    val cues = AudioCues()
    var currentLevel = 1
    var awarded = false
    var rewardAt = -1.0
    var lastAttempt = game.attempts

    fun save() = writeSave(progress.serialize())

    /** Push every switch to the thing it controls. Called on load and on change. */
    fun applySettings() {
        write(SETTINGS_KEY, settings.serialize())
        Audio.masterVolume = settings.masterGain * 0.85
        Audio.sfxVolume = settings.sfxGain
        Audio.ambienceVolume = settings.ambienceGain
        Audio.sfxEnabled = settings.sfx > 0
        Audio.ambienceEnabled = settings.ambience > 0
        renderer.reduceEffects = settings.reduceEffects
        renderer.colorblind = settings.colorblind
        progress.unlockAllForTesting = settings.unlockAll
        document.documentElement?.setAttribute("dir", if (settings.lang == Lang.AR) "rtl" else "ltr")
        Strings.lang = settings.lang
    }

    /** A short pulse, where the device has one and the player wants it. */
    fun buzz(ms: Int) {
        if (!settings.vibration) return
        try { window.navigator.asDynamic().vibrate(ms) } catch (e: Throwable) { }
    }

    fun applyLook() {
        renderer.look.shape = progress.equipped(Category.SHAPE)
        renderer.look.colour = progress.equipped(Category.COLOR)
        renderer.look.trail = progress.equipped(Category.TRAIL)
        renderer.look.face = progress.equipped(Category.FACE)
        renderer.coins = progress.coins
    }
    applyLook()
    applySettings()

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
            Screen.MENU -> ui.showHome()
            Screen.SHOP -> ui.showShop()
            Screen.PLAYING -> ui.hideAll()
            Screen.REWARD -> Unit                 // the panel puts itself up
        }
        // Off the level, the menus have a room of their own rather than the
        // silence of a stopped file - and the level's five layers stand down.
        if (s != Screen.PLAYING) Audio.menuRoom()
    }

    fun startLevel(id: Int) {
        val def = levels.firstOrNull { it.card.id == id } ?: return
        val build = def.build ?: return
        if (!progress.unlocked(id)) return
        currentLevel = id
        renderer.theme = Theme.forLevel(id)
        game = Game(build())
        // The soundtrack runs at the level's own tempo and restarts its
        // arrangement, so every attempt opens on the same bar.
        Audio.bpm = game.level.bpm
        val nextWorld = Theme.worldOf(game.level.id)
        // Arriving in a world you were not in a moment ago is worth a sound.
        if (nextWorld != Audio.world) Audio.worldTransition()
        Audio.world = nextWorld
        cues.reset()
        Audio.restartRoom()
        lastAttempt = game.attempts
        awarded = false
        rewardAt = -1.0
        renderer.resetRun()
        applyLook()
        last = 0.0
        showScreen(Screen.PLAYING)
        Audio.resume()
        Audio.loadPack()
    }

    ui = Ui(
        progress, settings, levels.map { it.card },
        onPlay = ::startLevel,
        onSave = { save(); applyLook() },
        onSettingsChanged = { applySettings() },
        onWipe = {
            try { localStorage.removeItem(SAVE_KEY) } catch (e: Throwable) { }
            window.location.reload()
        },
    )

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
    // Browsers will not start audio before a gesture, so the library is fetched
    // on the first one - along with the hum that gives the game its first second.
    var greeted = false
    fun wakeAudio() {
        Audio.resume()
        Audio.loadPack()
        if (!greeted) {
            greeted = true
            Audio.gameEnter()
            window.setTimeout({ if (screen != Screen.PLAYING) Audio.menuRoom() }, 500)
        }
    }
    window.addEventListener("pointerdown", { wakeAudio() })
    window.addEventListener("keydown", { wakeAudio() })

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
            if (game.starsCollected != prevStars) {
                prevStars = game.starsCollected
                Audio.star()
                buzz(18)
                // Banked now, not at the finish: there are no checkpoints, and a
                // coin reached at 85% is still a coin the player reached.
                game.takenStarIndices().forEach { progress.collectStar(currentLevel, it) }
                save(); applyLook()
            }
            if (prevState == GameState.RUNNING && game.state == GameState.DEAD) {
                // Pit and wall are the level taking you; a spike is something
                // hitting you, and only that one gets the impact under the loss.
                Audio.death(byHazard = game.deathCause == DeathCause.SPIKE ||
                    game.deathCause == DeathCause.CEILING_SPIKE)
                buzz(45)
            }
            if (prevState == GameState.RUNNING && game.state == GameState.COMPLETE) {
                Audio.finish(perfect = game.starsCollected >= game.level.stars.size)
                rewardAt = now + 900.0
            }
            if (running) cues.frame(game)
            prevState = game.state

            if (game.attempts != lastAttempt) {
                lastAttempt = game.attempts
                Audio.restartRoom()
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

            // The arrangement follows the run: drums, then build, then the drop
            // at 70%, then everything for the last stretch.
            Audio.setProgress(game.progress)

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
    api.aheadUnits = { renderer.aheadUnits }
    /**
     * Where the ground the runner is committing to BEGINS, or -1 when they are
     * not committing to any.
     *
     * Only a gap counts. Hopping a spike on the platform you are already
     * standing on lands you back on it, and the far side of the level is not
     * something you needed to see; reporting that as a blind jump is how a
     * readability check turns into noise. So: find the ledge under the runner,
     * and if it ends inside the reach of a jump, the next ledge after it is what
     * they are being asked to aim at.
     */
    api.gapAheadX = {
        val t = game.elapsed
        val reach = Tuning.JUMP_DISTANCE * 2.0
        var under = -1.0
        game.level.solids.forEach { s ->
            if (game.x + 0.5 >= s.x0At(t) && game.x + 0.5 <= s.x1At(t) &&
                kotlin.math.abs(s.topAt(t) - game.y) < 0.2) {
                val x1 = s.x1At(t)
                if (x1 > under) under = x1
            }
        }
        if (under < 0.0 || under > game.x + reach) -1.0 else {
            var next = -1.0
            game.level.solids.forEach { s ->
                val x0 = s.x0At(t)
                if (x0 >= under - 1e-6 && x0 > game.x && (next < 0.0 || x0 < next)) next = x0
            }
            next
        }
    }
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
    api.wipe = {
        try { localStorage.removeItem(SAVE_KEY); localStorage.removeItem(SETTINGS_KEY) } catch (e: Throwable) {}
    }
    api.setting = { key: String, on: Boolean ->
        when (key) {
            "sfx" -> settings.sfx = if (on) 10 else 0
            "ambience" -> settings.ambience = if (on) 10 else 0
            "tryAllCosmetics" -> settings.tryAllCosmetics = on
            "vibration" -> settings.vibration = on
            "reduceEffects" -> settings.reduceEffects = on
            "colorblind" -> settings.colorblind = on
            "unlockAll" -> settings.unlockAll = on
        }
        applySettings()
    }
    api.settingOf = { key: String ->
        when (key) {
            "sfx" -> settings.sfx > 0
            "ambience" -> settings.ambience > 0
            "tryAllCosmetics" -> settings.tryAllCosmetics
            "vibration" -> settings.vibration
            "reduceEffects" -> settings.reduceEffects
            "colorblind" -> settings.colorblind
            "unlockAll" -> settings.unlockAll
            else -> false
        }
    }
    api.setLang = { code: String -> settings.lang = if (code == "AR") Lang.AR else Lang.EN; applySettings(); ui.refresh() }
    api.lang = { settings.lang.name }
    api.movers = { game.level.hazards.count { it.moves } }
    // --- what world we are in, for the desert harness ----------------------
    api.world = { Theme.worldOf(game.level.id) }
    api.scene = { Theme.forLevel(game.level.id).scene.name }
    api.bpm = { game.level.bpm }
    api.tension = { Audio.tension }
    // --- the sound pack, for the audio harness ------------------------------
    api.samplesReady = { Audio.samplesReady }
    api.samplesLoaded = { Audio.samplesLoaded }
    api.cueCount = { Audio.cueCount }
    api.audioFormat = { Audio.format }
    api.beds = { Audio.bedReport() }
    api.playCue = { name: String -> Audio.play(name, 0.8) }
    api.winds = { game.level.winds.size }
    api.storms = { game.level.storms.size }
    /** How thick the weather is where the runner is standing, 0..1. */
    api.storminess = { game.level.storms.sumOf { it.at(game.x) } }
    /** Every kind of obstacle this level is built out of, so a test can prove the
     *  desert is a new playground rather than the city with a filter on it. */
    api.looks = { game.level.hazards.map { it.look.name }.distinct().sorted().joinToString(",") }
    api.surfaces = { game.level.solids.map { it.surface.name }.distinct().sorted().joinToString(",") }
    /** Hazards that switch on and off, and how many are lethal this instant. */
    api.pulsing = { game.level.hazards.count { it.pulses } }
    api.pulsingLive = { game.level.hazards.count { it.pulses && it.activeAt(game.elapsed) } }
    api.pulsingWarm = { game.level.hazards.count { it.warmAt(game.elapsed) > 0.0 } }
    /** Where mover [i] is this instant, so a test can prove it moves and repeats. */
    api.moverX = { i: Int ->
        val h = game.level.hazards.filter { it.moves }.getOrNull(i)
        if (h == null) -1.0 else (h.drawBoxAt(game.elapsed).x0 + h.drawBoxAt(game.elapsed).x1) / 2.0
    }
}
