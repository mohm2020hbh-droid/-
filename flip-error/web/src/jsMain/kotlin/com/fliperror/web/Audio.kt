package com.fliperror.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.random.Random

/**
 * FLIP ERROR's audio, and its one rule: THE PACK IS THE ONLY SOURCE.
 *
 * Every sound this game makes is a recording from the FLIP ERROR sound pack -
 * see AudioMap for the file behind each one. There is no oscillator in this
 * file, no noise buffer, no filter sweep and no synthesised voice of any kind.
 * The engine that used to generate all of that has been deleted rather than
 * demoted: a fallback that quietly makes its own sounds is exactly the thing a
 * "these files only" rule exists to stop, and a fallback nobody can hear firing
 * is worse than none at all.
 *
 * THERE IS NO MUSIC AND THERE IS NO AMBIENCE. Not a track, not a bed, not a
 * drone, not a loop of any kind. Every sound this engine can make has a CAUSE -
 * the player did something, the interface did something, or an obstacle did
 * something - and the engine has no clock of its own to fire anything from.
 *
 * The room that used to be here is gone, and gone rather than muted: the five
 * layers per world, the streamed <audio> elements, the ambience bus, the five
 * tension bands, the crossfades and the scheduler that fired events on its own
 * timer were all deleted. Left in place and switched off, that machinery would
 * still fetch, decode and loop audio nobody asked for, and a silent system that
 * is still running is not the same thing as a system that is not there.
 *
 * Twenty recordings from the pack are therefore untouched on disk and referenced
 * nowhere - AudioMap lists them by name so the decision stays visible.
 *
 * IF THE PACK CANNOT BE FETCHED, THE GAME IS SILENT. That is the honest
 * consequence of the rule: the game stays completely playable, and nothing
 * invents a sound to cover the gap.
 */
object Audio {
    private var ctx: dynamic = null
    private var master: dynamic = null
    private var sfxBus: dynamic = null

    private val buffers = HashMap<String, dynamic>()
    private var ext = ""

    // --- what the settings screen controls -----------------------------------
    //
    // There is no MUSIC control, because there is no music. Three volumes and two
    // switches, and every one of them means something audible.

    var masterVolume = 0.85
        set(v) { field = v; master?.gain?.value = v }
    var sfxVolume = 1.0
        set(v) { field = v; applyBuses() }
    var sfxEnabled = true
        set(v) { field = v; applyBuses() }

    private fun applyBuses() {
        sfxBus?.gain?.value = if (sfxEnabled) sfxVolume else 0.0
    }

    /** Which world is being played. Only decides which hazard cues can fire. */
    var world = 1

    /** True once the pack is in. False means the game is silent, by design. */
    var samplesReady = false
        private set
    /** How many recordings decoded. */
    var samplesLoaded = 0
        private set
    /** How many the map expects. */
    val cueCount: Int get() = AudioMap.oneShots.size
    /** Which container the library is playing from. */
    val format: String get() = ext

    // --- the graph ------------------------------------------------------------

    private fun ensure(): Boolean {
        if (ctx == null) {
            val C = window.asDynamic().AudioContext ?: window.asDynamic().webkitAudioContext
            if (C == null) return false
            ctx = js("new C()")
            master = ctx.createGain()
            master.gain.value = masterVolume
            // A limiter, not a loudness trick: it lets the mix sit high enough for
            // a phone speaker while a landing on top of a riser cannot clip. It
            // shapes the sum of the pack; it does not alter any file's contents.
            val comp = ctx.createDynamicsCompressor()
            comp.threshold.value = -14.0
            comp.knee.value = 26.0
            comp.ratio.value = 14.0
            comp.attack.value = 0.003
            comp.release.value = 0.14
            master.connect(comp)
            comp.connect(ctx.destination)

            // One bus. There is no ambience bus, because there is no ambience.
            sfxBus = ctx.createGain()
            sfxBus.connect(master)
            applyBuses()
        }
        return ctx != null
    }

    fun resume() {
        if (!ensure()) return
        if (ctx.state == "suspended") ctx.resume()
    }

    // --- loading the pack -----------------------------------------------------

    /**
     * Opus first, MP3 second, silence third.
     *
     * Every recording ships in both containers. Asking canPlayType rather than
     * guessing from the user agent is the only version of this that stays true.
     */
    private fun pickFormat(): String {
        val probe = document.createElement("audio").asDynamic()
        val opus = probe.canPlayType("audio/webm; codecs=opus") as String
        if (opus.isNotEmpty() && opus != "no") return ".webm"
        val mp3 = probe.canPlayType("audio/mpeg") as String
        if (mp3.isNotEmpty() && mp3 != "no") return ".mp3"
        return ""
    }

    fun loadPack() {
        if (!ensure() || ext.isNotEmpty()) return
        ext = pickFormat()
        if (ext.isEmpty()) return
        var pending = AudioMap.oneShots.size
        AudioMap.oneShots.forEach { name ->
            window.fetch(AudioMap.DIR + name + ext).then { r ->
                if (r.ok) r.arrayBuffer() else null
            }.then { data ->
                if (data == null) { if (--pending <= 0) finishLoad(); return@then null }
                ctx.decodeAudioData(data, { buf: dynamic ->
                    buffers[name] = buf
                    samplesLoaded++
                    if (--pending <= 0) finishLoad()
                }, { _: dynamic -> if (--pending <= 0) finishLoad() })
                null
            }.catch { _: dynamic -> if (--pending <= 0) finishLoad(); null }
        }
    }

    private fun finishLoad() {
        // Half a library is worse than none: a game that plays its jump and not
        // its death has a bug the player will read as their own mistake.
        if (samplesLoaded < AudioMap.oneShots.size / 2) return
        samplesReady = true
    }

    /** Fire one recording. False when the pack has not got it. */
    fun play(name: String, gain: Double = 1.0): Boolean {
        if (!ensure()) return false
        val buf = buffers[name] ?: return false
        val src = ctx.createBufferSource()
        src.buffer = buf
        val g = ctx.createGain()
        g.gain.value = gain
        src.connect(g); g.connect(sfxBus)
        src.start(ctx.currentTime as Double)
        return true
    }

    /**
     * Called when a level starts. It fires the start cue and nothing else - there
     * is no room to bring up and no arrangement to reset.
     */
    fun levelStarted() { play(AudioMap.LEVEL_START, 0.7) }

    // --- the cues -------------------------------------------------------------
    //
    // One line each, and each line names the recording that does that job. There
    // is nothing underneath any of them.

    fun jump() { play(AudioMap.JUMP, 0.85) }

    /** Louder and unmistakably a different recording, because the second jump is
     *  a different decision and the ear has to be told which one just happened. */
    fun doubleJump() { play(AudioMap.DOUBLE_JUMP, 1.0) }

    fun land() { play(AudioMap.LAND, 0.55) }

    fun nearMiss() { play(AudioMap.NEAR_MISS, 0.8) }

    fun star() { play(AudioMap.COLLECT, 0.9) }

    /**
     * Death is two recordings in order: what hit you, then losing. The player
     * hears the cause before the verdict, which is what the death screen already
     * says in words. Pit and wall deaths are the level taking you, so they get
     * the loss alone.
     */
    fun death(byHazard: Boolean = true) {
        if (byHazard) play(AudioMap.HAZARD_HIT, 0.9)
        window.setTimeout({ play(AudioMap.STRONG_LOSS, 1.0) }, 90)
    }

    /** [perfect] is a clear with every coin, and the pack has its own sound for it. */
    fun finish(perfect: Boolean = false) {
        play(if (perfect) AudioMap.PERFECT_FINISH else AudioMap.LEVEL_COMPLETE, 1.0)
    }

    fun gameEnter() { play(AudioMap.GAME_ENTER, 0.8) }

    fun worldTransition() { play(AudioMap.WORLD_TRANSITION, 0.85) }

    fun unlocked() { play(AudioMap.SECRET_UNLOCK, 0.85) }

    fun uiConfirm() { play(AudioMap.UI_CONFIRM, 0.7) }

    /**
     * A refused purchase. The pack has no cancel sound - 06_ui_cancel is one of
     * the three names its README lists and does not contain - so this makes no
     * sound at all. Inventing one here is precisely what the pack-only rule
     * forbids, and the modal already says no on screen.
     */
    fun uiDenied() = Unit

    /**
     * The trail's voice, kept sparse. 17_trail_spark is also absent from the
     * pack, so the speed whoosh - which the pack does ship, and which the brief
     * lists under SPEED - carries it, at a fraction of its volume. A tick under
     * every stride would be a metronome, and this game does not have one.
     */
    fun trailSpark(amount: Double) {
        if (!sfxEnabled) return
        play(AudioMap.SPEED_WHOOSH, 0.05 + 0.07 * amount)
    }

    /** World 2's obstacles, each announced by its own recording - see AudioCues. */
    fun hazardCue(name: String, amount: Double = 1.0) { play(name, amount) }

}
