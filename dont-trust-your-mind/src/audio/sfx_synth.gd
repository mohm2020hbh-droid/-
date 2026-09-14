class_name SfxSynth
extends RefCounted

## Builds the game's sound effects in memory at boot.
##
## The whole palette is six short blips, so synthesising them costs a few
## milliseconds and ~40 KB of RAM while keeping the APK free of audio assets
## and the game fully offline.

const MIX_RATE := 22050

enum Wave { SINE, SQUARE, TRIANGLE, NOISE }

class Voice extends RefCounted:
	var wave: Wave = Wave.SINE
	var freq_start: float = 440.0
	var freq_end: float = 440.0
	var gain: float = 0.5
	var detune: float = 0.0  ## Second oscillator offset in Hz; 0 disables it.

	func _init(w: Wave, f0: float, f1: float, g: float = 0.5, det: float = 0.0) -> void:
		wave = w
		freq_start = f0
		freq_end = f1
		gain = g
		detune = det

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
static func render(voices: Array, seconds: float, attack: float = 0.02,
		release: float = 0.6, seed_value: int = 1337) -> AudioStreamWAV:
	var frames := int(MIX_RATE * seconds)
	var bytes := PackedByteArray()
	bytes.resize(frames * 2)
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
			var f: float = lerpf(v.freq_start, v.freq_end, t)
			phases[v_index] += f / float(MIX_RATE)
			sample += _osc(v.wave, phases[v_index], rng) * v.gain
			if v.detune != 0.0:
				phases_b[v_index] += (f + v.detune) / float(MIX_RATE)
				sample += _osc(v.wave, phases_b[v_index], rng) * v.gain * 0.6
		sample = clampf(sample * env, -1.0, 1.0)
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

# --- The palette --------------------------------------------------------------

static func tap() -> AudioStreamWAV:
	return render([Voice.new(Wave.SINE, 880.0, 660.0, 0.35)], 0.06, 0.01, 0.9, 11)

static func wrong() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SQUARE, 180.0, 96.0, 0.22),
		Voice.new(Wave.SINE, 120.0, 70.0, 0.25),
	], 0.26, 0.005, 0.7, 23)

static func correct() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 660.0, 990.0, 0.30, 2.0),
		Voice.new(Wave.TRIANGLE, 1320.0, 1760.0, 0.12),
	], 0.34, 0.01, 0.55, 31)

static func reveal() -> AudioStreamWAV:
	return render([Voice.new(Wave.TRIANGLE, 300.0, 1200.0, 0.22)], 0.30, 0.05, 0.8, 41)

static func hint() -> AudioStreamWAV:
	return render([Voice.new(Wave.SINE, 523.0, 784.0, 0.26, 1.5)], 0.22, 0.02, 0.8, 53)

static func success() -> AudioStreamWAV:
	return render([
		Voice.new(Wave.SINE, 523.0, 1046.0, 0.26, 3.0),
		Voice.new(Wave.TRIANGLE, 784.0, 1568.0, 0.14),
		Voice.new(Wave.SINE, 1046.0, 2093.0, 0.07),
	], 0.70, 0.01, 0.45, 67)

static func tick() -> AudioStreamWAV:
	return render([Voice.new(Wave.SQUARE, 1400.0, 1400.0, 0.10)], 0.03, 0.01, 1.2, 71)
