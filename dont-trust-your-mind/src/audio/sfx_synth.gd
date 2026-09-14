class_name SfxSynth
extends RefCounted

## Builds the game's sound effects in memory at boot.
##
## The whole palette is nine short sounds, so synthesising them costs a few
## milliseconds and under 100 KB of RAM while keeping the APK free of audio
## assets and the game fully offline.
##
## Design intent: this is a sound set for an intelligent puzzle game, not a
## children's app. That ruled out raw SQUARE waves entirely — their harsh
## harmonics are the single most "toy-like, chiptune" texture a game can make
## — in favour of SINE/TRIANGLE layers softened by a one-pole low-pass filter
## on every sound (see [method _lowpass]), which rounds off the digital edge
## that makes procedural tones sound thin and cheap. Every sound stays under
## 700ms; most are under 250ms. Each of the eight distinct moments the game
## marks with sound (tap, button, correct, wrong, stage complete, hint,
## unlock, transition) has its own register and shape so they read as
## different feelings, not the same blip pitched up or down.

const MIX_RATE := 22050

enum Wave { SINE, SQUARE, TRIANGLE, NOISE }

class Voice extends RefCounted:
	var wave: Wave = Wave.SINE
	var freq_start: float = 440.0
	var freq_end: float = 440.0
	var gain: float = 0.5
	var detune: float = 0.0  ## Second oscillator offset in Hz; 0 disables it.
	var delay: float = 0.0   ## Fraction of the clip's length before this voice starts.

	func _init(w: Wave, f0: float, f1: float, g: float = 0.5, det: float = 0.0,
			d: float = 0.0) -> void:
		wave = w
		freq_start = f0
		freq_end = f1
		gain = g
		detune = det
		delay = d

static func _osc(wave: Wave, phase: float, rng: RandomNumberGenerator) -> float:
	match wave:
		Wave.SINE:
			return sin(phase * TAU)
		Wave.SQUARE:
			return 1.0 if fmod(phase, 1.0) < 0.5 else -1.0
		Wave.TRIANGLE:
			var t := fmod(phase, 1.0)
			return 4.0 * absf(t - 0.5) - 1.0
		Wave.NOISE:
			return rng.randf_range(-1.0, 1.0)
	return 0.0

## [param attack]/[param release] are fractions of the total length.
## [param cutoff_hz] softens harsh digital edges; 0 disables filtering (used
## sparingly, for sounds that want to stay crisp rather than warm).
static func render(voices: Array, seconds: float, attack: float = 0.02,
		release: float = 0.6, seed_value: int = 1337, cutoff_hz: float = 5200.0,
		out_gain: float = 1.0) -> AudioStreamWAV:
	var frames := int(MIX_RATE * seconds)
	var raw := PackedFloat32Array()
	raw.resize(frames)
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value

	var phases := PackedFloat32Array()
	var phases_b := PackedFloat32Array()
	phases.resize(voices.size())
	phases_b.resize(voices.size())

	for i in range(frames):
		var t := float(i) / float(frames)
		var env := _envelope(t, attack, release)
		var sample := 0.0
		for v_index in range(voices.size()):
			var v: Voice = voices[v_index]
			if t < v.delay:
				continue
			var local_t := (t - v.delay) / maxf(1.0 - v.delay, 0.0001)
			var f: float = lerpf(v.freq_start, v.freq_end, local_t)
			phases[v_index] += f / float(MIX_RATE)
			sample += _osc(v.wave, phases[v_index], rng) * v.gain
			if v.detune != 0.0:
				phases_b[v_index] += (f + v.detune) / float(MIX_RATE)
				sample += _osc(v.wave, phases_b[v_index], rng) * v.gain * 0.6
		raw[i] = sample * env

	if cutoff_hz > 0.0:
		raw = _lowpass(raw, cutoff_hz)

	var bytes := PackedByteArray()
	bytes.resize(frames * 2)
	for i in range(frames):
		var sample: float = clampf(raw[i] * out_gain, -1.0, 1.0)
		var word := int(sample * 32000.0)
		bytes[i * 2] = word & 0xFF
		bytes[i * 2 + 1] = (word >> 8) & 0xFF

	var stream := AudioStreamWAV.new()
	stream.format = AudioStreamWAV.FORMAT_16_BITS
	stream.mix_rate = MIX_RATE
	stream.stereo = false
	stream.data = bytes
	return stream

static func _envelope(t: float, attack: float, release: float) -> float:
	if t < attack:
		return t / maxf(attack, 0.0001)
	var decay_span := 1.0 - attack
	var x := (t - attack) / maxf(decay_span, 0.0001)
	return pow(1.0 - x, 1.0 + release * 4.0)

## A one-pole low-pass, applied post-synthesis. This is what takes a raw
## generated tone from "digital buzz" to something that could pass for a
## sampled UI sound — it is the single biggest lever this synth has over how
## premium a sound feels, cheaper than adding more oscillators.
static func _lowpass(raw: PackedFloat32Array, cutoff_hz: float) -> PackedFloat32Array:
	var rc := 1.0 / (TAU * cutoff_hz)
	var dt := 1.0 / float(MIX_RATE)
	var alpha := dt / (rc + dt)
	var out := PackedFloat32Array()
	out.resize(raw.size())
	var prev := 0.0
	for i in range(raw.size()):
		prev += alpha * (raw[i] - prev)
		out[i] = prev
	return out

# --- The palette ----------------------------------------------------------
#
# Tap        board interaction   — light, neutral, instant
# Button     UI chrome           — a clean click, lower and drier than Tap
# Correct    a right step        — brief bright rise
# Wrong      a wrong step        — a soft low dip, never harsh or buzzy
# Stage      full puzzle solved  — a short triumphant three-note rise
# Complete
# Hint       a nudge offered     — warm, unhurried, single tone
# Unlock     achievement earned  — a sparkling higher chime, rarer-feeling
# Transition moving to next      — a quick soft sweep, almost a whoosh
# Reveal     an element appears  — a light upward glint
# Tick       countdown pulse     — a quiet, dry pulse, easy to hear many times

static func tap() -> AudioStreamWAV:
	return render([Voice.new(Wave.SINE, 920.0, 760.0, 0.30)], 0.05, 0.005, 0.85, 11, 6000.0)

static func button() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.TRIANGLE, 340.0, 260.0, 0.28),
		Voice.new(Wave.SINE, 680.0, 520.0, 0.10),
	], 0.07, 0.005, 0.8, 17, 4200.0)

static func correct() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 700.0, 1050.0, 0.28, 2.0),
		Voice.new(Wave.TRIANGLE, 1400.0, 1750.0, 0.10),
	], 0.20, 0.01, 0.55, 31, 6500.0)

## A soft dip, not a buzzer: two close, slightly clashing low tones falling
## together read as "no" without the harshness a square wave would add.
static func wrong() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 260.0, 150.0, 0.26),
		Voice.new(Wave.TRIANGLE, 245.0, 140.0, 0.16),
	], 0.22, 0.005, 0.75, 23, 2600.0)

static func stage_complete() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 523.0, 523.0, 0.05, 0.0, 0.0),    # C5, arrives first
		Voice.new(Wave.SINE, 659.0, 659.0, 0.05, 0.0, 0.14),   # E5
		Voice.new(Wave.SINE, 784.0, 1046.0, 0.30, 2.0, 0.28),  # G5 rising to C6, the payoff
		Voice.new(Wave.TRIANGLE, 1046.0, 1568.0, 0.10, 0.0, 0.28),
	], 0.62, 0.01, 0.5, 67, 7000.0)

static func hint() -> AudioStreamWAV:
	return render([Voice.new(Wave.SINE, 500.0, 660.0, 0.24, 1.2)], 0.30, 0.08, 0.85, 53, 5000.0)

## A shimmer above the register everything else lives in, so an unlock
## always reads as rarer than a routine correct answer.
static func unlock() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 880.0, 1318.0, 0.22, 5.0),
		Voice.new(Wave.SINE, 1318.0, 1760.0, 0.16, 3.0, 0.12),
		Voice.new(Wave.TRIANGLE, 1760.0, 2217.0, 0.08, 0.0, 0.22),
	], 0.55, 0.02, 0.6, 79, 8000.0)

## A quick pitch sweep through filtered noise — the closest this synth gets
## to a "whoosh" without a real noise-shaping filter, kept short so it never
## overstays between one puzzle and the next.
static func transition() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 300.0, 900.0, 0.16),
		Voice.new(Wave.NOISE, 1.0, 1.0, 0.05),
	], 0.18, 0.02, 0.7, 83, 3200.0)

static func reveal() -> AudioStreamWAV:
	return render([Voice.new(Wave.TRIANGLE, 340.0, 1150.0, 0.20)], 0.26, 0.05, 0.8, 41, 6000.0)

## Deliberately dry (no low-pass) and very short: this plays several times a
## second during the last seconds of a timer, so it needs to stay small and
## unobtrusive rather than warm.
static func tick() -> AudioStreamWAV:
	return render([Voice.new(Wave.TRIANGLE, 1600.0, 1500.0, 0.08)], 0.025, 0.01, 1.0, 71, 0.0)
