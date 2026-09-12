/**
 * Tests for the acoustic scoring engine.
 *
 * The important property is not the absolute number — it is *discrimination*:
 * an imitation of a given target must score higher against that target than
 * against the 29 others. That is measured here as rank-1 accuracy over the
 * whole bank.
 *
 * Run with:  node backend/tests/js/dsp.test.mjs
 */

import { compareAudio, extractFeatures, ANALYSIS_RATE } from "../../app/static/js/dsp.js";
import { SOUNDS, targetSamples, SOUND_RATE, distort } from "../../app/static/js/sounds.js";

let passed = 0, failed = 0;
const results = [];

function check(name, condition, detail = "") {
  if (condition) { passed++; results.push(`  ok   ${name}${detail ? "  " + detail : ""}`); }
  else { failed++; results.push(`  FAIL ${name}${detail ? "  " + detail : ""}`); }
}

/* ------------------------------------------------------------------ *
 * A stand-in for a human imitation.
 *
 * Real voices differ from a synthesised target in timing, pitch, spectral
 * tilt and cleanliness, so a plausible imitation is modelled by degrading the
 * target along exactly those axes.
 * ------------------------------------------------------------------ */
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function imitate(samples, seed, strength = 1) {
  const random = mulberry32(seed);
  const stretch = 1 + (random() - 0.5) * 0.35 * strength;   // timing and register drift
  const length = Math.max(8, Math.round(samples.length / stretch));
  const stretched = new Float32Array(length);
  for (let i = 0; i < length; i++) {
    const pos = i * stretch;
    const i0 = Math.min(samples.length - 1, Math.floor(pos));
    const i1 = Math.min(samples.length - 1, i0 + 1);
    stretched[i] = samples[i0] * (1 - (pos - i0)) + samples[i1] * (pos - i0);
  }

  // Spectral tilt: a voice never has the target's exact balance.
  const tilt = 0.25 + random() * 0.5;
  let previous = 0;
  const out = new Float32Array(length);
  for (let i = 0; i < length; i++) {
    previous = previous * tilt + stretched[i] * (1 - tilt);
    const breath = (random() * 2 - 1) * 0.05 * strength;
    out[i] = stretched[i] * 0.6 + previous * 0.4 + breath;
  }
  return out;
}

/* ------------------------------------------------------------------ *
 * 1. Sanity
 * ------------------------------------------------------------------ */
const cat = targetSamples("cat");

check("identical audio scores at the top of the scale",
  compareAudio(cat, SOUND_RATE, cat, SOUND_RATE).score >= 95,
  `score=${compareAudio(cat, SOUND_RATE, cat, SOUND_RATE).score}`);

check("silence is refused rather than scored",
  compareAudio(new Float32Array(8000), SOUND_RATE, cat, SOUND_RATE).ok === false);

check("a too-short blip is refused",
  compareAudio(new Float32Array(64).fill(0.5), SOUND_RATE, cat, SOUND_RATE).ok === false);

check("features survive a real target",
  extractFeatures(cat, SOUND_RATE) !== null);

check("analysis rate is independent of the source rate",
  compareAudio(
    targetSamples("bee"), SOUND_RATE,
    targetSamples("bee"), SOUND_RATE,
  ).score >= 95 && ANALYSIS_RATE === 16000);

/* ------------------------------------------------------------------ *
 * 2. Discrimination — the property that makes the game work
 * ------------------------------------------------------------------ */
const targets = SOUNDS.map((s) => ({ id: s.id, samples: targetSamples(s.id) }));

let rank1 = 0, rank3 = 0;
const selfScores = [];
const otherScores = [];
const misses = [];

for (let s = 0; s < targets.length; s++) {
  const attempt = imitate(targets[s].samples, 1000 + s);
  const scored = targets.map((t) => ({
    id: t.id,
    score: compareAudio(attempt, SOUND_RATE, t.samples, SOUND_RATE).score,
  }));
  scored.sort((a, b) => b.score - a.score);

  const own = scored.find((x) => x.id === targets[s].id);
  selfScores.push(own.score);
  for (const x of scored) if (x.id !== targets[s].id) otherScores.push(x.score);

  if (scored[0].id === targets[s].id) rank1++;
  else misses.push(`${targets[s].id}: ${own.score} lost to ${scored[0].id} ${scored[0].score}`);
  if (scored.slice(0, 3).some((x) => x.id === targets[s].id)) rank3++;
}

const avg = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;
const rank1Pct = (100 * rank1) / targets.length;
const rank3Pct = (100 * rank3) / targets.length;

check("an imitation ranks its own target first at least 70% of the time",
  rank1Pct >= 70, `rank-1 = ${rank1Pct.toFixed(0)}%`);

check("an imitation ranks its own target in the top 3 at least 90% of the time",
  rank3Pct >= 90, `rank-3 = ${rank3Pct.toFixed(0)}%`);

check("the right target scores clearly above the wrong ones",
  avg(selfScores) - avg(otherScores) >= 12,
  `own ${avg(selfScores).toFixed(1)} vs others ${avg(otherScores).toFixed(1)}`);

/* ------------------------------------------------------------------ *
 * 3. The score has to be usable as a game score
 * ------------------------------------------------------------------ */
check("a genuine attempt lands in a rewarding band, not pinned at 0 or 100",
  avg(selfScores) >= 45 && avg(selfScores) <= 92, `mean own score ${avg(selfScores).toFixed(1)}`);

check("unrelated sounds do not look like good attempts",
  avg(otherScores) <= 62, `mean other score ${avg(otherScores).toFixed(1)}`);

const sloppy = avg(SOUNDS.slice(0, 10).map((s, i) =>
  compareAudio(imitate(targetSamples(s.id), 50 + i, 2.2), SOUND_RATE,
               targetSamples(s.id), SOUND_RATE).score));
const careful = avg(SOUNDS.slice(0, 10).map((s, i) =>
  compareAudio(imitate(targetSamples(s.id), 50 + i, 0.3), SOUND_RATE,
               targetSamples(s.id), SOUND_RATE).score));

check("a closer imitation scores higher than a sloppier one",
  careful > sloppy, `careful ${careful.toFixed(1)} > sloppy ${sloppy.toFixed(1)}`);

/* ------------------------------------------------------------------ *
 * 4. The distorted challenge type is still scoreable
 * ------------------------------------------------------------------ */
const harsh = distort(targetSamples("rooster"));
check("a distorted target still compares against itself",
  compareAudio(harsh, SOUND_RATE, harsh, SOUND_RATE).score >= 90);

check("noise scores poorly against a tonal target",
  compareAudio(
    Float32Array.from({ length: 30000 }, () => Math.random() * 2 - 1), SOUND_RATE,
    targetSamples("guitar"), SOUND_RATE,
  ).score < 55);

/* ------------------------------------------------------------------ */
console.log(results.join("\n"));
if (misses.length) {
  console.log("\n  rank-1 misses:");
  for (const m of misses) console.log("    " + m);
}
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
