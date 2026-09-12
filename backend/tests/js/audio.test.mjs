/**
 * Target audio validation.
 *
 * "It sounds recognisable" cannot be asserted by a script, but the
 * properties that make a synthesised sound read as a *generic electronic
 * tone* are all measurable, and this file checks them:
 *
 *   - a raw oscillator has essentially zero cycle-to-cycle jitter, while
 *     any real voice has a few percent;
 *   - a voice's identity lives in its formants, so the spectrum must
 *     actually show a peak where the recipe declares one;
 *   - a real bell is inharmonic, which a harmonic series cannot be;
 *   - and every target must be plainly *unlike* a plain oscillator at the
 *     same pitch, measured with the game's own scoring engine.
 *
 * Run with:  node backend/tests/js/audio.test.mjs
 */

import { compareAudio } from "../../app/static/js/dsp.js";
import { SOUNDS, SOUNDS_BY_ID, SOUND_RATE, targetSamples } from "../../app/static/js/sounds.js";

let passed = 0, failed = 0;
const results = [];

function check(name, condition, detail = "") {
  if (condition) { passed++; results.push(`  ok   ${name}${detail ? "  " + detail : ""}`); }
  else { failed++; results.push(`  FAIL ${name}${detail ? "  " + detail : ""}`); }
}

/* ------------------------------------------------------------------ *
 * Measurement helpers
 * ------------------------------------------------------------------ */

function statsOf(samples) {
  let peak = 0, sum = 0;
  for (let i = 0; i < samples.length; i++) {
    peak = Math.max(peak, Math.abs(samples[i]));
    sum += samples[i] * samples[i];
  }
  return { peak, rms: Math.sqrt(sum / samples.length), seconds: samples.length / SOUND_RATE };
}

/** Naive DFT magnitude at one frequency — enough to test for a peak. */
function magnitudeAt(samples, freq, start, length) {
  let re = 0, im = 0;
  const omega = (2 * Math.PI * freq) / SOUND_RATE;
  for (let n = 0; n < length; n++) {
    const x = samples[start + n];
    re += x * Math.cos(omega * n);
    im += x * Math.sin(omega * n);
  }
  return Math.hypot(re, im) / length;
}

/**
 * Cycle-to-cycle jitter, as a fraction.
 *
 * Measured from intervals between positive-going zero crossings of the
 * low-passed signal. A mathematical oscillator gives ~0; a voice gives a
 * few percent, and that irregularity is what stops it sounding synthetic.
 */
function cycleJitter(samples) {
  // Gentle one-pole low pass so formant ripple does not create extra
  // crossings that would fake jitter.
  const smoothed = new Float32Array(samples.length);
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    previous = previous * 0.82 + samples[i] * 0.18;
    smoothed[i] = previous;
  }

  const crossings = [];
  for (let i = 1; i < smoothed.length; i++) {
    if (smoothed[i - 1] <= 0 && smoothed[i] > 0) crossings.push(i);
  }
  if (crossings.length < 12) return null;

  const intervals = [];
  for (let i = 1; i < crossings.length; i++) intervals.push(crossings[i] - crossings[i - 1]);

  // Use the middle of the sound: attack and release skew the edges.
  const middle = intervals.slice(
    Math.floor(intervals.length * 0.2),
    Math.ceil(intervals.length * 0.8),
  );
  if (middle.length < 6) return null;

  const mean = middle.reduce((a, b) => a + b, 0) / middle.length;
  if (mean <= 0) return null;
  const variance = middle.reduce((a, b) => a + (b - mean) ** 2, 0) / middle.length;
  return Math.sqrt(variance) / mean;
}

/** A plain oscillator, for "is this just a tone?" comparisons. */
function plainOscillator(freq, seconds, wave = "saw") {
  const length = Math.round(seconds * SOUND_RATE);
  const out = new Float32Array(length);
  for (let n = 0; n < length; n++) {
    const phase = (freq * n) / SOUND_RATE;
    const frac = phase - Math.floor(phase);
    out[n] = wave === "saw" ? 2 * frac - 1 : Math.sin(2 * Math.PI * phase);
    out[n] *= 0.5;
  }
  return out;
}

const firstSpec = (sound) => sound.spec ?? sound.parts[0];
const kindOf = (sound) => firstSpec(sound).kind;

/* ------------------------------------------------------------------ *
 * 1. Every target must actually produce usable audio
 * ------------------------------------------------------------------ */

const unusable = [];
const levels = [];

for (const sound of SOUNDS) {
  const samples = targetSamples(sound.id);
  const { peak, rms, seconds } = statsOf(samples);
  levels.push(rms);

  const problems = [];
  if (!Number.isFinite(rms) || !Number.isFinite(peak)) problems.push("not finite");
  if (peak < 0.2) problems.push(`too quiet (peak ${peak.toFixed(2)})`);
  if (peak > 1.0) problems.push(`clipping (peak ${peak.toFixed(2)})`);
  if (rms < 0.05) problems.push(`no body (rms ${rms.toFixed(3)})`);
  if (seconds < 0.08) problems.push(`too short (${seconds.toFixed(2)}s)`);
  if (seconds > 4.0) problems.push(`too long (${seconds.toFixed(2)}s)`);
  if (problems.length) unusable.push(`${sound.id}: ${problems.join(", ")}`);
}

check(`all ${SOUNDS.length} targets render as audible, unclipped, playable audio`,
  unusable.length === 0, unusable.join(" | "));

const minLevel = Math.min(...levels), maxLevel = Math.max(...levels);
check("playback loudness is consistent across the library (within 2.5x)",
  maxLevel / minLevel <= 2.5, `${minLevel.toFixed(3)}-${maxLevel.toFixed(3)} = ${(maxLevel / minLevel).toFixed(2)}x`);

/* ------------------------------------------------------------------ *
 * 2. Determinism — the scoring reference must never drift
 * ------------------------------------------------------------------ */

const drifted = SOUNDS.filter((sound) => {
  const a = Array.from(targetSamples(sound.id));
  const b = Array.from(targetSamples(sound.id));
  return a.length !== b.length || a.some((v, i) => v !== b[i]);
});
check("every target renders bit-identically every time", drifted.length === 0,
  drifted.map((s) => s.id).join(", "));

/* ------------------------------------------------------------------ *
 * 3. Each target is scoreable against itself
 * ------------------------------------------------------------------ */

const unscoreable = SOUNDS.filter((sound) => {
  const samples = targetSamples(sound.id);
  const result = compareAudio(samples, SOUND_RATE, samples, SOUND_RATE);
  return !result.ok || result.score < 95;
});
check("every target scores at the top of the scale against itself",
  unscoreable.length === 0, unscoreable.map((s) => s.id).join(", "));

/* ------------------------------------------------------------------ *
 * 4. Animal voices must have real vocal irregularity
 * ------------------------------------------------------------------ */

const voices = SOUNDS.filter((s) => kindOf(s) === "voice");
const tooPerfect = [];
const measured = [];

for (const sound of voices) {
  const samples = targetSamples(sound.id);
  // Measure inside one continuous voiced stretch: counting zero crossings
  // across silent gaps or across a multi-part boundary inflates the
  // variance and makes the number meaningless.
  const span = Math.min(samples.length, Math.round(firstSpec(sound).duration * SOUND_RATE * 0.7));
  const jitter = cycleJitter(samples.subarray(0, Math.max(2000, span)));
  if (jitter === null) continue;
  measured.push(`${sound.id} ${(jitter * 100).toFixed(1)}%`);
  if (jitter < 0.005) tooPerfect.push(`${sound.id} ${(jitter * 100).toFixed(2)}%`);
}

// The absolute figure from this crude estimator is not meaningful on its
// own, so it is anchored against a mathematical oscillator measured by the
// *same* function: that baseline is the "perfectly electronic" reading
// every one of these voices has to sit clearly above.
const oscillatorBaseline = cycleJitter(plainOscillator(200, 1.0, "saw")) ?? 0;
const aboveBaseline = measured.length > 0 && voices.every((sound) => {
  const samples = targetSamples(sound.id);
  const span = Math.min(samples.length, Math.round(firstSpec(sound).duration * SOUND_RATE * 0.7));
  const jitter = cycleJitter(samples.subarray(0, Math.max(2000, span)));
  return jitter === null || jitter > oscillatorBaseline + 0.01;
});

check(`all ${voices.length} animal voices are cycle-irregular, unlike a mathematical oscillator`,
  tooPerfect.length === 0 && aboveBaseline,
  `oscillator baseline ${(oscillatorBaseline * 100).toFixed(2)}% vs voices ${Math.min(...measured.map((m) => parseFloat(m.split(" ")[1]))).toFixed(0)}%+`);

/* ------------------------------------------------------------------ *
 * 5. Declared formants must actually be present in the spectrum
 *
 * This is the difference between a sound that *has* a vocal tract and one
 * that is a filtered buzz.
 * ------------------------------------------------------------------ */

const missingFormants = [];

/**
 * Find the loudest analysis window, searching only the span that belongs
 * to the segment whose formants we are checking. Repeated and multi-part
 * targets have silent gaps and later segments with *different* formants,
 * so sampling the middle of the whole buffer would measure the wrong
 * thing (or pure silence).
 */
function loudestWindow(samples, limitSamples, window) {
  const end = Math.max(window, Math.min(limitSamples, samples.length));
  let bestStart = 0;
  let bestEnergy = -1;
  for (let start = 0; start + window <= end; start += Math.floor(window / 4)) {
    let energy = 0;
    for (let n = 0; n < window; n++) energy += samples[start + n] * samples[start + n];
    if (energy > bestEnergy) { bestEnergy = energy; bestStart = start; }
  }
  return bestStart;
}

for (const sound of voices) {
  const spec = firstSpec(sound);
  if (!spec.formants || !spec.formants.length) continue;

  const samples = targetSamples(sound.id);
  const window = Math.min(2048, Math.floor(samples.length / 2));
  // Only the first segment's own time span carries the formants declared
  // in the first spec.
  const limit = Math.round(spec.duration * SOUND_RATE);
  const start = loudestWindow(samples, limit, window);

  const valueOf = (f) => (Array.isArray(f) ? f[Math.floor(f.length / 2)][1] : f);
  const f1 = valueOf(spec.formants[0].f);
  const highest = Math.max(...spec.formants.map((formant) => valueOf(formant.f)));

  // Reference well clear of *every* declared formant, so the comparison
  // is against the spectral floor and not another resonance.
  const reference = Math.min(9500, Math.max(highest * 2.2, f1 * 4.5));
  const atFormant = magnitudeAt(samples, f1, start, window);
  const atFloor = magnitudeAt(samples, reference, start, window);

  if (!(atFormant > atFloor * 2)) {
    missingFormants.push(`${sound.id} F1=${Math.round(f1)}Hz vs floor ${Math.round(reference)}Hz (${atFormant.toExponential(1)} vs ${atFloor.toExponential(1)})`);
  }
}

check("every voiced target shows real energy at its declared first formant",
  missingFormants.length === 0, missingFormants.join(" | "));

/* ------------------------------------------------------------------ *
 * 6. A bell must be inharmonic
 * ------------------------------------------------------------------ */

const bell = SOUNDS_BY_ID.bell.spec;
const inharmonic = bell.partials.filter((p) => Math.abs(p.ratio - Math.round(p.ratio)) > 0.08);
check("the bell is built from inharmonic partials, as a real bell is",
  inharmonic.length >= 3, `${inharmonic.length} of ${bell.partials.length} partials are inharmonic`);

const bellSamples = targetSamples("bell");
const bellFundamental = magnitudeAt(bellSamples, bell.base, 2000, 2048);
const bellTierce = magnitudeAt(bellSamples, bell.base * 1.19, 2000, 2048);
check("the bell's inharmonic partial is audible alongside its fundamental",
  bellTierce > bellFundamental * 0.15,
  `tierce ${bellTierce.toExponential(1)} vs fundamental ${bellFundamental.toExponential(1)}`);

/* ------------------------------------------------------------------ *
 * 7. The headline test: targets must not simply *be* oscillator tones
 *
 * Scored with the game's own engine against a plain saw at the target's
 * own pitch. A high score would mean the target is acoustically just that
 * tone — exactly the complaint this work set out to fix.
 * ------------------------------------------------------------------ */

const tonelike = [];
const toneScores = [];

for (const sound of voices) {
  const spec = firstSpec(sound);
  const f0curve = spec.f0;
  const f0 = Array.isArray(f0curve) ? f0curve[Math.floor(f0curve.length / 2)][1] : f0curve;
  const samples = targetSamples(sound.id);
  const naive = plainOscillator(f0, samples.length / SOUND_RATE, "saw");

  const score = compareAudio(samples, SOUND_RATE, naive, SOUND_RATE).score;
  toneScores.push(score);
  if (score >= 75) tonelike.push(`${sound.id} ${score}`);
}

const meanToneScore = toneScores.reduce((a, b) => a + b, 0) / toneScores.length;
check("no animal voice is acoustically just a sawtooth at its own pitch",
  tonelike.length === 0,
  tonelike.length ? `tone-like: ${tonelike.join(", ")}` : `mean similarity to a plain saw: ${meanToneScore.toFixed(0)}/100`);

/* ------------------------------------------------------------------ *
 * 8. Every sound the game can name is renderable and bilingual
 * ------------------------------------------------------------------ */

const badNames = SOUNDS.filter((s) => !s.id || !s.name || !s.nameEn || !s.emoji);
check("every sound has a stable id, both names, and an icon",
  badNames.length === 0, badNames.map((s) => s.id || "?").join(", "));

const kinds = {};
for (const sound of SOUNDS) kinds[kindOf(sound)] = (kinds[kindOf(sound)] ?? 0) + 1;

/* ------------------------------------------------------------------ */
console.log(results.join("\n"));
console.log("\n  generator mix:", Object.entries(kinds).map(([k, n]) => `${k}=${n}`).join("  "));
if (measured.length) console.log("  voice jitter :", measured.slice(0, 8).join("  "), "...");
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
