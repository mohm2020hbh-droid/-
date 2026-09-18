package com.fliperror.web

import kotlinx.browser.window
import kotlin.math.pow
import kotlin.random.Random

/**
 * Synthesised with WebAudio so the game stays asset-free and offline.
 *
 * THERE IS NO MUSIC HERE. No track, no melody, no loop, no beat - not quietly
 * underneath, not at low volume. What used to be a drum-and-bass engine is gone,
 * and what replaced it is a place: wind, air, rumble, hum, and things happening
 * somewhere out of sight. The difference is not decoration. A loop tells the
 * player they are inside a product; an environment tells them they are somewhere,
 * and somewhere is what a runner this hard needs, because the thing that gets
 * someone through a fortieth attempt is atmosphere, not a chorus.
 *
 * Three rules hold the bed together:
 *
 *  NOTHING IS ON A GRID. Every event is scheduled at a randomised distance from
 *  the last one, drawn from a range that only narrows as the level gets tense.
 *  The moment two sounds land a fixed interval apart the ear hears a beat, and
 *  a beat is a song.
 *
 *  THE ROOM ANSWERS THE LEVEL. Tension is read from the runner's own progress,
 *  and it moves the beds, the event rate and which palette the events come from.
 *  At the moment the level gets hardest the room is at its loudest and lowest.
 *
 *  SILENCE IS A SOUND. Crossing into a tenser stretch ducks everything to almost
 *  nothing for a beat before the room comes back heavier. The drop-out is what
 *  makes the arrival land; without it the build is just a volume knob.
 *
 * The player's own cues - jump, double jump, land, death, collect, near miss,
 * finish - are untouched and stay short, bright and punchy. They sit on their
 * own bus above the ambience, so the room can never be in front of a retry.
 */
object Audio {
    private var ctx: dynamic = null
    private var master: dynamic = null
    private var sfxBus: dynamic = null
    private var ambBus: dynamic = null
    private var echoIn: dynamic = null
    private var noise: dynamic = null
    private var started = false
    private var streak = 0

    // --- what the settings screen controls -----------------------------------
    //
    // There is no MUSIC control, because there is no music. Three volumes and
    // two switches, and every one of them means something audible.

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

    /** Which world's room we are standing in. The palettes are not variations of
     *  one another - a neon city and a buried desert share no material. */
    var world = 1

    /** The level's own tempo. Kept because the LEVELS are laid out on it; the
     *  audio deliberately ignores it, because anything locked to a tempo is a
     *  beat and a beat is the thing this engine exists not to have. */
    var bpm = 140.0

    /** 0..1, how tense the room is. Driven by the runner's progress. */
    var tension = 0.0
        private set

    // --- graph ---------------------------------------------------------------

    private fun ensure(): Boolean {
        if (ctx == null) {
            val C = window.asDynamic().AudioContext ?: window.asDynamic().webkitAudioContext
            if (C == null) return false
            ctx = js("new C()")
            master = ctx.createGain()
            master.gain.value = masterVolume
            // A limiter, not a loudness trick: it lets the mix sit high enough for
            // a phone speaker while a landing on top of a rumble cannot clip.
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

            // A long, soft echo hung off the ambience bus alone. It is what makes a
            // distant impact read as distant rather than quiet, and it is the whole
            // reason the desert sounds like a space with walls somewhere in it.
            val delay = ctx.createDelay(1.5)
            delay.delayTime.value = 0.38
            val fb = ctx.createGain()
            fb.gain.value = 0.34
            val tame = ctx.createBiquadFilter()
            tame.type = "lowpass"
            tame.frequency.value = 1800.0
            echoIn = ctx.createGain()
            echoIn.gain.value = 1.0
            echoIn.connect(delay)
            delay.connect(tame); tame.connect(fb); fb.connect(delay)
            delay.connect(ambBus)
        }
        return ctx != null
    }

    fun resume() {
        if (!ensure()) return
        if (ctx.state == "suspended") ctx.resume()
        if (!started) { started = true; startBeds(); runRoom() }
    }

    /**
     * Four seconds of noise, not one. A one-second loop under a filter is audible
     * as a loop within about twenty seconds of listening, and a loop is the one
     * thing the bed is not allowed to be.
     */
    private fun noiseBuffer(): dynamic {
        if (noise == null) {
            val rate = ctx.sampleRate as Double
            val len = (rate * 4.0).toInt()
            val buf = ctx.createBuffer(1, len, rate)
            val data = buf.getChannelData(0)
            for (i in 0 until len) data[i] = (Random.nextDouble() * 2.0 - 1.0).toFloat()
            noise = buf
        }
        return noise
    }

    // --- one-shot voices -------------------------------------------------------

    /** [to] slides the pitch across the note, which is what makes a blip a direction. */
    private fun tone(
        from: Double, to: Double, dur: Double, type: String, gain: Double,
        delay: Double = 0.0, bus: dynamic = null, attack: Double = 0.005,
    ) {
        if (!ensure()) return
        val t = (ctx.currentTime as Double) + delay
        val osc = ctx.createOscillator()
        val g = ctx.createGain()
        osc.type = type
        osc.frequency.setValueAtTime(from, t)
        if (to != from) osc.frequency.exponentialRampToValueAtTime(to, t + dur)
        g.gain.setValueAtTime(0.0001, t)
        g.gain.exponentialRampToValueAtTime(gain, t + attack)
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
        osc.connect(g); g.connect(bus ?: master)
        osc.start(t); osc.stop(t + dur + 0.02)
    }

    private fun noiseHit(
        at: Double, dur: Double, gain: Double, centre: Double, q: Double,
        type: String = "bandpass", bus: dynamic = null, sweepTo: Double = 0.0,
    ) {
        val src = ctx.createBufferSource()
        src.buffer = noiseBuffer()
        val f = ctx.createBiquadFilter()
        f.type = type
        f.frequency.setValueAtTime(centre, at)
        if (sweepTo > 0.0) f.frequency.exponentialRampToValueAtTime(sweepTo, at + dur)
        f.Q.value = q
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(gain, at + 0.004)
        g.gain.exponentialRampToValueAtTime(0.0001, at + dur)
        src.connect(f); f.connect(g); g.connect(bus ?: master)
        src.start(at); src.stop(at + dur + 0.02)
    }

    // --- the room ---------------------------------------------------------------
    //
    // Four continuous beds, started once and never restarted, whose gains and
    // filters are moved by tension. Between them they are the difference between
    // "a quiet game" and "a place that happens to be quiet right now".

    private var windGain: dynamic = null
    private var windFilter: dynamic = null
    private var rumbleGain: dynamic = null
    private var humGain: dynamic = null
    private var humA: dynamic = null
    private var humB: dynamic = null
    private var airGain: dynamic = null
    private var airFilter: dynamic = null

    private fun loopNoise(): dynamic {
        val src = ctx.createBufferSource()
        src.buffer = noiseBuffer()
        src.loop = true
        src.start(ctx.currentTime as Double)
        return src
    }

    private fun startBeds() {
        if (!ensure()) return

        // WIND. Noise under a bandpass that wanders. The wander is the whole
        // effect: a static filter on noise is a hiss, and a moving one is weather.
        windFilter = ctx.createBiquadFilter()
        windFilter.type = "bandpass"
        windFilter.frequency.value = 420.0
        windFilter.Q.value = 0.8
        windGain = ctx.createGain()
        windGain.gain.value = 0.0
        loopNoise().connect(windFilter)
        windFilter.connect(windGain)
        windGain.connect(ambBus)

        // RUMBLE. Everything below 90Hz, which a phone speaker barely reproduces
        // and a person feels anyway. It is the layer that carries dread.
        val lp = ctx.createBiquadFilter()
        lp.type = "lowpass"
        lp.frequency.value = 90.0
        lp.Q.value = 0.7
        rumbleGain = ctx.createGain()
        rumbleGain.gain.value = 0.0
        loopNoise().connect(lp)
        lp.connect(rumbleGain)
        rumbleGain.connect(ambBus)

        // HUM. Two detuned oscillators - mains hum for the city, something older
        // and slower for the desert. This is the layer that says "machinery".
        humGain = ctx.createGain()
        humGain.gain.value = 0.0
        humGain.connect(ambBus)
        humA = ctx.createOscillator()
        humB = ctx.createOscillator()
        humA.type = "sawtooth"; humB.type = "sine"
        val tame = ctx.createBiquadFilter()
        tame.type = "lowpass"
        tame.frequency.value = 340.0
        humA.connect(tame); humB.connect(tame); tame.connect(humGain)
        humA.start(ctx.currentTime as Double); humB.start(ctx.currentTime as Double)

        // AIR. The top end: room tone in the city, sand moving in the desert.
        airFilter = ctx.createBiquadFilter()
        airFilter.type = "highpass"
        airFilter.frequency.value = 2200.0
        airGain = ctx.createGain()
        airGain.gain.value = 0.0
        loopNoise().connect(airFilter)
        airFilter.connect(airGain)
        airGain.connect(ambBus)

        applyWorldToBeds()
    }

    private fun applyWorldToBeds() {
        if (humA == null) return
        val t = (ctx.currentTime as Double)
        if (world >= 2) {
            // A buried machine turning over somewhere under the sand: lower,
            // slower, and slightly out of tune with itself.
            humA.frequency.setTargetAtTime(41.0, t, 0.6)
            humB.frequency.setTargetAtTime(61.5, t, 0.6)
        } else {
            // Mains hum and the buzz of a sign that needs replacing.
            humA.frequency.setTargetAtTime(50.0, t, 0.6)
            humB.frequency.setTargetAtTime(100.0, t, 0.6)
        }
    }

    /**
     * Tension, held as a number rather than a tier, so the room breathes between
     * the anchors instead of stepping between them. The anchors are the brief:
     * calm to halfway, a lift at 50%, the low rumble arriving at 70%, real
     * pressure by 85%, and everything the level has for the last twentieth.
     */
    private fun tensionFor(p: Double) = when {
        p >= 0.95 -> 1.00
        p >= 0.85 -> 0.72 + (p - 0.85) / 0.10 * 0.28
        p >= 0.70 -> 0.50 + (p - 0.70) / 0.15 * 0.22
        p >= 0.50 -> 0.28 + (p - 0.50) / 0.20 * 0.22
        else -> 0.10 + p / 0.50 * 0.18
    }

    private var tier = 0
    private var duckUntil = 0.0

    /** Where in the level the room is. Called every frame by the shell. */
    fun setProgress(p: Double) {
        tension = tensionFor(p)
        val t = when {
            p >= 0.95 -> 4
            p >= 0.85 -> 3
            p >= 0.70 -> 2
            p >= 0.50 -> 1
            else -> 0
        }
        if (t > tier) { tier = t; duck() }
        else if (t < tier) tier = t
    }

    /**
     * The drop-out before a harder stretch. Everything goes to almost nothing for
     * a third of a second, then the room comes back at its new weight. This is
     * the only moment in the game that is deliberately near-silent, and it is
     * what makes the stretch after it land - a build with no hole in front of it
     * is a volume knob, not a moment.
     */
    private fun duck() {
        if (!ensure()) return
        duckUntil = (ctx.currentTime as Double) + 0.42
        val t = ctx.currentTime as Double
        listOf(windGain, rumbleGain, humGain, airGain).forEach { g ->
            g?.gain?.cancelScheduledValues(t)
            g?.gain?.setTargetAtTime(0.004, t, 0.05)
        }
    }

    fun restartRoom() {
        tier = 0
        tension = 0.0
        duckUntil = 0.0
        applyWorldToBeds()
    }

    /**
     * The room's own clock. It does two jobs on a slow timer: it walks the beds
     * toward where tension says they should be, and it decides when the next
     * thing happens somewhere out of sight.
     *
     * The gap between events is randomised every single time, and the range it is
     * drawn from only narrows with tension. Nothing here can land on a grid, and
     * nothing repeats at an interval the ear can learn.
     */
    private var nextEvent = 0.0

    private fun runRoom() {
        fun tick() {
            if (ctx != null && ctx.state != "closed") {
                val now = ctx.currentTime as Double
                if (now > duckUntil) {
                    val ten = tension
                    val desert = world >= 2
                    // The desert is a windier, emptier, lower room than the city.
                    val wind = if (desert) 0.055 + 0.150 * ten else 0.022 + 0.055 * ten
                    val rumble = if (desert) 0.070 + 0.320 * ten.pow(1.4) else 0.040 + 0.210 * ten.pow(1.5)
                    val hum = if (desert) 0.014 + 0.036 * ten else 0.030 + 0.062 * ten
                    val air = if (desert) 0.020 + 0.055 * ten else 0.010 + 0.024 * ten
                    windGain?.gain?.setTargetAtTime(wind, now, 0.7)
                    rumbleGain?.gain?.setTargetAtTime(rumble, now, 0.9)
                    humGain?.gain?.setTargetAtTime(hum, now, 1.2)
                    airGain?.gain?.setTargetAtTime(air, now, 0.8)
                    // the wind wanders, faster and higher the tenser it gets
                    val centre = (if (desert) 300.0 else 520.0) * (0.72 + Random.nextDouble() * 0.62) *
                        (1.0 + 0.5 * ten)
                    windFilter?.frequency?.setTargetAtTime(centre, now, 1.4)
                    windFilter?.Q?.setTargetAtTime(0.6 + 1.9 * ten, now, 1.4)
                    airFilter?.frequency?.setTargetAtTime(
                        (if (desert) 1500.0 else 2600.0) * (0.85 + Random.nextDouble() * 0.4), now, 1.1)

                    if (now >= nextEvent) {
                        if (ambienceEnabled) event(now)
                        // Randomised every time, and only the RANGE moves with
                        // tension. A fixed cadence, however slow, is a pulse.
                        val busy = 1.0 - 0.55 * tension
                        nextEvent = now + (1.1 + Random.nextDouble() * 5.2) * busy
                    }
                }
            }
            window.setTimeout({ tick() }, 220)
        }
        nextEvent = (ctx.currentTime as Double) + 1.2
        tick()
    }

    // --- things happening somewhere out of sight ---------------------------------
    //
    // Every one of these is quiet, long and unresolved. None of them is a
    // jumpscare: a sound that makes a player flinch during a precision jump is a
    // death the game caused, which is the one thing this project does not ship.
    // They are here to make the place feel inhabited by something, not to startle.

    private fun event(now: Double) {
        val ten = tension
        val desert = world >= 2
        // Which palette a moment comes from is itself a function of tension: the
        // strange, low, unresolved things only start turning up once the level has
        // begun to squeeze.
        val roll = Random.nextDouble()
        if (desert) when {
            roll < 0.24 -> sandGust(now, 0.6 + 0.9 * ten)
            roll < 0.42 -> distantImpact(now, 0.5 + 1.0 * ten)
            roll < 0.58 -> stoneGroan(now, 0.5 + 0.9 * ten)
            roll < 0.70 -> templeEcho(now, 0.5 + 0.8 * ten)
            roll < 0.82 -> ancientMachine(now, 0.4 + 1.0 * ten)
            roll < 0.92 && ten > 0.45 -> unease(now, ten)
            else -> sandGust(now, 0.4 + 0.7 * ten)
        } else when {
            roll < 0.26 -> electricalCrackle(now, 0.5 + 0.9 * ten)
            roll < 0.44 -> distantMachinery(now, 0.5 + 0.9 * ten)
            roll < 0.60 -> distantImpact(now, 0.4 + 0.9 * ten)
            roll < 0.74 -> metallicCreak(now, 0.4 + 0.8 * ten)
            roll < 0.86 -> airMove(now, 0.5 + 0.8 * ten)
            roll < 0.94 && ten > 0.45 -> unease(now, ten)
            else -> electricalCrackle(now, 0.4 + 0.6 * ten)
        }
    }

    /** A swell of sand moving across the world, front to back. */
    private fun sandGust(at: Double, amount: Double) {
        val src = ctx.createBufferSource()
        src.buffer = noiseBuffer(); src.loop = true
        val f = ctx.createBiquadFilter()
        f.type = "bandpass"
        f.frequency.setValueAtTime(700.0, at)
        f.frequency.exponentialRampToValueAtTime(2600.0, at + 1.6)
        f.frequency.exponentialRampToValueAtTime(500.0, at + 3.2)
        f.Q.value = 0.9
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.055 * amount, at + 1.3)
        g.gain.linearRampToValueAtTime(0.0001, at + 3.2)
        src.connect(f); f.connect(g); g.connect(ambBus)
        src.start(at); src.stop(at + 3.3)
    }

    /** Something heavy landing a long way off. Sent to the echo, because distance
     *  is reverberation and not simply quietness. */
    private fun distantImpact(at: Double, amount: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "sine"
        o.frequency.setValueAtTime(74.0, at)
        o.frequency.exponentialRampToValueAtTime(31.0, at + 0.85)
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(0.16 * amount, at + 0.05)
        g.gain.exponentialRampToValueAtTime(0.0001, at + 1.1)
        o.connect(g); g.connect(ambBus); g.connect(echoIn)
        o.start(at); o.stop(at + 1.2)
        val n = ctx.createBufferSource(); n.buffer = noiseBuffer()
        val f = ctx.createBiquadFilter(); f.type = "lowpass"; f.frequency.value = 260.0
        val ng = ctx.createGain()
        ng.gain.setValueAtTime(0.0001, at)
        ng.gain.exponentialRampToValueAtTime(0.07 * amount, at + 0.03)
        ng.gain.exponentialRampToValueAtTime(0.0001, at + 0.8)
        n.connect(f); f.connect(ng); ng.connect(ambBus); ng.connect(echoIn)
        n.start(at); n.stop(at + 0.9)
    }

    /** Rock shifting against rock. Slow, tonal, and it never quite resolves. */
    private fun stoneGroan(at: Double, amount: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "sawtooth"
        o.frequency.setValueAtTime(52.0, at)
        o.frequency.linearRampToValueAtTime(44.0, at + 2.4)
        val f = ctx.createBiquadFilter(); f.type = "lowpass"
        f.frequency.setValueAtTime(190.0, at)
        f.frequency.linearRampToValueAtTime(95.0, at + 2.4)
        f.Q.value = 3.0
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.085 * amount, at + 0.9)
        g.gain.linearRampToValueAtTime(0.0001, at + 2.5)
        o.connect(f); f.connect(g); g.connect(ambBus); g.connect(echoIn)
        o.start(at); o.stop(at + 2.6)
    }

    /** A struck stone in a big empty room. Mostly echo by the time it is heard. */
    private fun templeEcho(at: Double, amount: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "triangle"
        o.frequency.setValueAtTime(196.0, at)
        o.frequency.exponentialRampToValueAtTime(131.0, at + 0.4)
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(0.045 * amount, at + 0.02)
        g.gain.exponentialRampToValueAtTime(0.0001, at + 0.55)
        o.connect(g); g.connect(echoIn)
        o.start(at); o.stop(at + 0.6)
    }

    /** Something older than the temple, still turning over under the sand. */
    private fun ancientMachine(at: Double, amount: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "square"
        o.frequency.setValueAtTime(29.0, at)
        val f = ctx.createBiquadFilter(); f.type = "lowpass"; f.frequency.value = 150.0
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.075 * amount, at + 1.1)
        g.gain.linearRampToValueAtTime(0.0001, at + 3.0)
        o.connect(f); f.connect(g); g.connect(ambBus)
        o.start(at); o.stop(at + 3.1)
        // the beat frequency of two things that were never in time with each other
        val o2 = ctx.createOscillator()
        o2.type = "square"; o2.frequency.setValueAtTime(30.4, at)
        o2.connect(f); o2.start(at); o2.stop(at + 3.1)
    }

    /** A bad connection in a sign that has been on too long. */
    private fun electricalCrackle(at: Double, amount: Double) {
        val bursts = 2 + Random.nextInt(4)
        var t = at
        for (i in 0 until bursts) {
            noiseHit(t, 0.020 + Random.nextDouble() * 0.03, 0.035 * amount,
                3200.0 + Random.nextDouble() * 4200.0, 2.5, "bandpass", ambBus)
            t += 0.03 + Random.nextDouble() * 0.16
        }
    }

    /** A machine floor, several blocks away, through a wall. */
    private fun distantMachinery(at: Double, amount: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "sawtooth"
        o.frequency.setValueAtTime(63.0, at)
        val f = ctx.createBiquadFilter(); f.type = "lowpass"
        f.frequency.setValueAtTime(220.0, at)
        f.Q.value = 2.0
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.055 * amount, at + 0.8)
        g.gain.linearRampToValueAtTime(0.0001, at + 2.2)
        o.connect(f); f.connect(g); g.connect(ambBus); g.connect(echoIn)
        o.start(at); o.stop(at + 2.3)
    }

    /** Metal taking a load it was not built for. */
    private fun metallicCreak(at: Double, amount: Double) {
        val src = ctx.createBufferSource(); src.buffer = noiseBuffer()
        val f = ctx.createBiquadFilter()
        f.type = "bandpass"
        f.frequency.setValueAtTime(900.0, at)
        f.frequency.exponentialRampToValueAtTime(280.0, at + 1.3)
        f.Q.value = 14.0
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.055 * amount, at + 0.35)
        g.gain.linearRampToValueAtTime(0.0001, at + 1.4)
        src.connect(f); f.connect(g); g.connect(ambBus); g.connect(echoIn)
        src.start(at); src.stop(at + 1.5)
    }

    /** Air moving through somewhere it has to squeeze to get out of. */
    private fun airMove(at: Double, amount: Double) {
        val src = ctx.createBufferSource(); src.buffer = noiseBuffer(); src.loop = true
        val f = ctx.createBiquadFilter()
        f.type = "bandpass"
        f.frequency.setValueAtTime(1100.0, at)
        f.frequency.linearRampToValueAtTime(2400.0, at + 1.8)
        f.Q.value = 1.6
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.030 * amount, at + 0.9)
        g.gain.linearRampToValueAtTime(0.0001, at + 2.0)
        src.connect(f); f.connect(g); g.connect(ambBus)
        src.start(at); src.stop(at + 2.1)
    }

    /**
     * The one that is meant to be unsettling.
     *
     * It is deliberately quiet and deliberately long: a low shape that almost
     * resolves into a voice and then does not, a long way off, drenched in the
     * echo. It only exists past halfway through a level, and it is never loud,
     * because a sound that makes someone flinch mid-jump is a death the game
     * caused - and the brief asks for unease, not for a noise.
     */
    private fun unease(at: Double, ten: Double) {
        val src = ctx.createBufferSource(); src.buffer = noiseBuffer(); src.loop = true
        val f = ctx.createBiquadFilter()
        f.type = "bandpass"
        f.frequency.setValueAtTime(330.0 + Random.nextDouble() * 200.0, at)
        f.frequency.linearRampToValueAtTime(190.0, at + 2.6)
        f.Q.value = 9.0
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.linearRampToValueAtTime(0.030 * ten, at + 1.3)
        g.gain.linearRampToValueAtTime(0.0001, at + 2.8)
        src.connect(f); f.connect(g); g.connect(echoIn); g.connect(ambBus)
        src.start(at); src.stop(at + 2.9)
        val o = ctx.createOscillator(); val og = ctx.createGain()
        o.type = "sine"
        o.frequency.setValueAtTime(38.0, at)
        o.frequency.linearRampToValueAtTime(33.0, at + 2.6)
        og.gain.setValueAtTime(0.0001, at)
        og.gain.linearRampToValueAtTime(0.055 * ten, at + 1.1)
        og.gain.linearRampToValueAtTime(0.0001, at + 2.8)
        o.connect(og); og.connect(ambBus)
        o.start(at); o.stop(at + 2.9)
    }

    // --- the cues ----------------------------------------------------------------

    private fun jumpRoot(): Double {
        val s = if (streak > 4) 4 else streak
        return 440.0 * 2.0.pow(s / 12.0)
    }

    fun jump() {
        val root = jumpRoot()
        tone(root * 0.75, root * 1.32, 0.080, "triangle", 0.40)
        tone(root * 1.5, root * 2.5, 0.055, "sine", 0.16)
        streak++
    }

    /** A fifth above the first jump and split in two, so it reads as "up again". */
    fun doubleJump() {
        val root = jumpRoot() * 1.5
        tone(root, root * 1.20, 0.055, "triangle", 0.40)
        tone(root * 1.34, root * 1.95, 0.095, "triangle", 0.42, delay = 0.045)
        tone(root * 3.0, root * 3.7, 0.075, "sine", 0.18, delay = 0.045)
        if (sfxEnabled && ensure()) noiseHit((ctx.currentTime as Double), 0.09, 0.18, 5200.0, 1.2, "highpass")
    }

    fun death() {
        streak = 0
        tone(520.0, 88.0, 0.18, "triangle", 0.50)
        tone(260.0, 60.0, 0.20, "sine", 0.32, delay = 0.012)
        if (sfxEnabled && ensure()) noiseHit((ctx.currentTime as Double), 0.13, 0.26, 1200.0, 0.8, "bandpass", sweepTo = 200.0)
    }

    fun land() = tone(185.0, 95.0, 0.065, "sine", 0.24)

    fun nearMiss() {
        if (!sfxEnabled || !ensure()) return
        noiseHit((ctx.currentTime as Double), 0.11, 0.20, 2600.0, 6.0, "bandpass", sweepTo = 1100.0)
    }

    fun star() {
        tone(1318.5, 1318.5, 0.070, "triangle", 0.34)
        tone(1975.5, 1975.5, 0.130, "triangle", 0.32, delay = 0.055)
        tone(2637.0, 2637.0, 0.090, "sine", 0.16, delay = 0.055)
    }

    fun finish() {
        streak = 0
        listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f ->
            tone(f, f, 0.20, "triangle", 0.42, delay = i * 0.080)
            tone(f * 2, f * 2, 0.14, "sine", 0.16, delay = i * 0.080)
        }
        tone(1046.5, 1046.5, 0.50, "triangle", 0.34, delay = 0.34)
    }

    fun uiConfirm() {
        tone(700.0, 1050.0, 0.070, "triangle", 0.32)
        tone(1400.0, 1760.0, 0.090, "sine", 0.16, delay = 0.05)
    }

    fun uiDenied() = tone(220.0, 165.0, 0.10, "triangle", 0.28)
}
