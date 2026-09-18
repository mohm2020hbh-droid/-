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
 * THERE IS NO MUSIC. No track, no melody, no loop, no beat. The five
 * sixty-second beds per world are environments, played as layers and crossfaded
 * by how far through a level the runner is. They are never sequenced and never
 * put on a grid.
 *
 * TWO PLAYBACK PATHS, on purpose. The long beds STREAM through <audio> elements,
 * because decoding eleven minutes of stereo into memory is over two hundred
 * megabytes and a phone will not thank you for it; the short cues are decoded
 * once into buffers, where they fire with no latency at all.
 *
 * IF THE PACK CANNOT BE FETCHED, THE GAME IS SILENT. That is the honest
 * consequence of the rule and it is deliberate: the game stays completely
 * playable, and nothing invents a sound to cover the gap.
 */
object Audio {
    private var ctx: dynamic = null
    private var master: dynamic = null
    private var sfxBus: dynamic = null
    private var ambBus: dynamic = null

    private val buffers = HashMap<String, dynamic>()
    private val beds = HashMap<String, dynamic>()
    private var ext = ""
    private var started = false

    // --- what the settings screen controls -----------------------------------
    //
    // There is no MUSIC control, because there is no music. Three volumes and two
    // switches, and every one of them means something audible.

    var masterVolume = 0.85
        set(v) { field = v; master?.gain?.value = v }
    var sfxVolume = 1.0
        set(v) { field = v; applyBuses() }
    var ambienceVolume = 1.0
        set(v) { field = v; applyBuses() }
    var sfxEnabled = true
        set(v) { field = v; applyBuses() }
    var ambienceEnabled = true
        set(v) { field = v; applyBuses() }

    private fun applyBuses() {
        sfxBus?.gain?.value = if (sfxEnabled) sfxVolume else 0.0
        ambBus?.gain?.value = if (ambienceEnabled) ambienceVolume * AMBIENCE_LEVEL else 0.0
    }

    private const val AMBIENCE_LEVEL = 0.70

    /** Which world's room is playing. The two share no recording. */
    var world = 1

    /** 0..1, how tense the room is. Read by the harness. */
    var tension = 0.0
        private set

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

            sfxBus = ctx.createGain()
            sfxBus.connect(master)
            ambBus = ctx.createGain()
            ambBus.connect(master)
            applyBuses()
        }
        return ctx != null
    }

    fun resume() {
        if (!ensure()) return
        if (ctx.state == "suspended") ctx.resume()
        if (!started) { started = true; runRoom() }
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
        AudioMap.streamed.forEach { makeBed(it) }
        samplesReady = true
    }

    /**
     * A streamed layer. MediaElementSource rather than decodeAudioData because a
     * sixty-second stereo bed is twenty megabytes of float samples once decoded
     * and there are eleven of them. In the document rather than only in a
     * variable, so the browser manages its buffering and lifecycle; no controls,
     * so it draws nothing.
     */
    private fun makeBed(name: String) {
        if (beds.containsKey(name)) return
        val el = document.createElement("audio").asDynamic()
        el.src = AudioMap.DIR + name + ext
        el.loop = true
        el.preload = "auto"
        el.volume = 1.0
        el.hidden = true
        document.body?.appendChild(el as org.w3c.dom.Node)
        val src = ctx.createMediaElementSource(el)
        val g = ctx.createGain()
        g.gain.value = 0.0
        src.connect(g); g.connect(ambBus)
        val entry = js("({})")
        entry.el = el
        entry.gain = g
        entry.playing = false
        beds[name] = entry
    }

    private fun bedGain(name: String, to: Double, over: Double = 0.8) {
        val b = beds[name] ?: return
        if (!(b.playing as Boolean) && to > 0.001) {
            b.playing = true
            val p = b.el.play()
            if (p != null && p.catch != null) p.catch { _: dynamic -> null }
        }
        b.gain.gain.setTargetAtTime(to, ctx.currentTime as Double, over)
    }

    private fun bedsSilent(except: List<String>) {
        beds.keys.forEach { if (it !in except) bedGain(it, 0.0, 0.5) }
    }

    /** Fire one recording. False when the pack has not got it. */
    fun play(name: String, gain: Double = 1.0, bus: dynamic = null): Boolean {
        if (!ensure()) return false
        val buf = buffers[name] ?: return false
        val src = ctx.createBufferSource()
        src.buffer = buf
        val g = ctx.createGain()
        g.gain.value = gain
        src.connect(g); g.connect(bus ?: sfxBus)
        src.start(ctx.currentTime as Double)
        return true
    }

    // --- the tension system ---------------------------------------------------

    /**
     * The five bands the pack was cut for: 0-25, 25-50, 50-70, 70-90, 90-100.
     *
     * Returned as a fractional index so the room can sit BETWEEN two layers
     * rather than switch between them. Most of each band is its own layer at full
     * weight; the last stretch before a boundary crossfades into the next, so
     * nothing ever stops abruptly.
     */
    private fun bandIndex(p: Double): Double {
        val edges = doubleArrayOf(0.0, 0.25, 0.50, 0.70, 0.90, 1.01)
        var band = 0
        while (band < 4 && p >= edges[band + 1]) band++
        if (band >= 4) return 4.0
        val left = edges[band + 1] - p
        val fade = 0.06                       // about two seconds of a level
        return if (left >= fade) band.toDouble() else band + (1.0 - left / fade)
    }

    private var tier = 0
    private var duckUntil = 0.0

    /** Where in the level the room is. Called every frame by the shell. */
    fun setProgress(p: Double) {
        tension = (bandIndex(p) / 4.0).coerceIn(0.0, 1.0)
        val t = when {
            p >= 0.90 -> 4
            p >= 0.70 -> 3
            p >= 0.50 -> 2
            p >= 0.25 -> 1
            else -> 0
        }
        if (t > tier) {
            tier = t
            duck()
            // The riser IS the transition: one per step, at the step, and nowhere
            // else. On every bar it would be a rhythm, and a rhythm is a song.
            play(AudioMap.risers[(t - 1).coerceIn(0, AudioMap.risers.size - 1)], 0.55, ambBus)
        } else if (t < tier) tier = t
        if (samplesReady) layerBeds(p)
    }

    /**
     * The drop-out before a harder stretch. Everything falls to almost nothing
     * for a beat, then the room comes back at its new weight. It is the only
     * near-silence in the game and it is what makes the stretch after it land - a
     * build with no hole in front of it is a volume knob, not a moment.
     */
    private fun duck() {
        if (!ensure()) return
        val t = ctx.currentTime as Double
        duckUntil = t + 0.42
        beds.values.forEach { b -> b.gain.gain.setTargetAtTime(0.02, t, 0.05) }
    }

    /**
     * Crossfade the world's five layers to where the level is.
     *
     * Equal power (the square roots), because two beds mixed at 0.5 each are
     * quieter than one at 1.0 and the handover would audibly sag. Everything
     * outside the pair goes to silence over half a second rather than stopping,
     * which is the difference between a room changing and a file ending.
     */
    private fun layerBeds(p: Double) {
        val names = AudioMap.bedsFor(world)
        val idx = bandIndex(p)
        val lo = kotlin.math.floor(idx).toInt().coerceIn(0, names.size - 1)
        val hi = (lo + 1).coerceAtMost(names.size - 1)
        val mix = idx - lo
        val ducking = (ctx.currentTime as Double) < duckUntil
        val level = if (ducking) 0.02 else 1.0
        names.forEachIndexed { i, name ->
            val g = when (i) {
                lo -> kotlin.math.sqrt(1.0 - mix)
                hi -> kotlin.math.sqrt(mix)
                else -> 0.0
            }
            bedGain(name, g * level, if (ducking) 0.08 else 0.7)
        }
        bedsSilent(names)
    }

    /** The menus get their own room, and the level's layers stand down. */
    fun menuRoom() {
        if (!samplesReady) return
        bedGain(AudioMap.MENU_BED, 0.85, 1.0)
        bedsSilent(listOf(AudioMap.MENU_BED))
    }

    fun restartRoom() {
        tier = 0
        tension = 0.0
        duckUntil = 0.0
        if (samplesReady) { layerBeds(0.0); play(AudioMap.LEVEL_START, 0.7) }
    }

    /**
     * Things happening out of sight, from the pack's own one-shots: the city's
     * arc and machine hits, the desert's sand and falling stone, and the three
     * unease beds once a level has begun to squeeze.
     *
     * The gap between them is randomised every single time and only the RANGE
     * narrows with tension. Two sounds a fixed distance apart are a beat, and a
     * beat is a song.
     */
    private var nextEvent = 0.0

    private fun runRoom() {
        fun tick() {
            if (ctx != null && ctx.state != "closed") {
                val now = ctx.currentTime as Double
                if (samplesReady && ambienceEnabled && now >= nextEvent && now > duckUntil) {
                    val roll = Random.nextDouble()
                    val loud = 0.30 + 0.45 * tension
                    when {
                        tension > 0.45 && roll < 0.22 -> play(AudioMap.unease.random(), loud * 0.8, ambBus)
                        world >= 2 -> play(
                            if (roll < 0.5) AudioMap.SAND_WAVE else AudioMap.FALLING_RUIN,
                            loud * 0.5, ambBus)
                        else -> play(AudioMap.world1Events.random(), loud, ambBus)
                    }
                    nextEvent = now + (1.1 + Random.nextDouble() * 5.2) * (1.0 - 0.55 * tension)
                } else if (now >= nextEvent) {
                    nextEvent = now + 1.5
                }
            }
            window.setTimeout({ tick() }, 220)
        }
        nextEvent = (ctx.currentTime as Double) + 1.2
        tick()
    }

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

    /** The live gain of every bed, so a test can watch a crossfade happen. */
    fun bedReport(): String = AudioMap.bedsFor(world).joinToString(",") { name ->
        val b = beds[name]
        val g = if (b == null) 0.0 else (b.gain.gain.value as Double)
        ((g * 1000).toInt() / 1000.0).toString()
    }
}
