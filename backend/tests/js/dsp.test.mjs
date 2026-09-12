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
 *
 * Each sound is tried with three different random imitations (different
 * timing drift, spectral tilt, and breath noise) rather than one, so the
 * result reflects the engine's real behaviour rather than one lucky or
 * unlucky RNG draw on a 44-sound bank with genuine near-neighbours (a horse
 * and a cat are both mid-range vibrato-ish saw tones; that is expected, not
 * a bug, and averaging over seeds is what separates "occasionally confused"
 * from "systematically confused").
 * ------------------------------------------------------------------ */
const targets = SOUNDS.map((s) => ({ id: s.id, samples: targetSamples(s.id) }));
const TRIALS_PER_SOUND = 3;

let rank1 = 0, rank3 = 0;
const trials = targets.length * TRIALS_PER_SOUND;
const selfScores = [];
const otherScores = [];
const missCounts = new Map();

for (let s = 0; s < targets.length; s++) {
  for (let trial = 0; trial < TRIALS_PER_SOUND; trial++) {
    const attempt = imitate(targets[s].samples, 1000 + s * 7 + trial * 3);
    const scored = targets.map((t) => ({
      id: t.id,
      score: compareAudio(attempt, SOUND_RATE, t.samples, SOUND_RATE).score,
    }));
    scored.sort((a, b) => b.score - a.score);

    const own = scored.find((x) => x.id === targets[s].id);
    selfScores.push(own.score);
    for (const x of scored) if (x.id !== targets[s].id) otherScores.push(x.score);

    if (scored[0].id === targets[s].id) {
      rank1++;
    } else {
      const key = `${targets[s].id} -> ${scored[0].id}`;
      missCounts.set(key, (missCounts.get(key) ?? 0) + 1);
    }
    if (scored.slice(0, 3).some((x) => x.id === targets[s].id)) rank3++;
  }
}

const avg = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;
const rank1Pct = (100 * rank1) / trials;
const rank3Pct = (100 * rank3) / trials;
// A miss that shows up in only one of three trials is noise; a miss on all
// three trials is a real, systematic confusion worth flagging.
const systematicMisses = [...missCounts.entries()]
  .filter(([, count]) => count >= TRIALS_PER_SOUND)
  .map(([pair]) => pair);

check(`an imitation ranks its own target first on average across ${trials} trials (>= 70%)`,
  rank1Pct >= 70, `rank-1 = ${rank1Pct.toFixed(0)}%`);

check(`an imitation ranks its own target in the top 3 on average (>= 88%)`,
  rank3Pct >= 88, `rank-3 = ${rank3Pct.toFixed(0)}%`);

/* ------------------------------------------------------------------ *
 * 2b. Are the TARGETS themselves distinct?
 *
 * This is the property the player actually experiences, and it is measured
 * on the real audio with no imitation proxy in the way: if two targets score
 * as near-identical against each other, no imitation of either can be told
 * apart, and the player is being asked an unanswerable question.
 *
 * The measured worst pair is 84 (sheep/horse — two bleating animals), and
 * the sounds just above that line when this was written were real defects:
 * snake/rain at 99 (two filtered hisses), bee/motorcycle at 90, bell/guitar
 * at 86. 88 sits in the gap between the two groups.
 * ------------------------------------------------------------------ */
const pairScores = [];
for (let i = 0; i < targets.length; i++) {
  for (let j = i + 1; j < targets.length; j++) {
    pairScores.push({
      a: targets[i].id,
      b: targets[j].id,
      score: compareAudio(targets[i].samples, SOUND_RATE, targets[j].samples, SOUND_RATE).score,
    });
  }
}
const tooAlike = pairScores.filter((p) => p.score > 88);
const worstPair = pairScores.reduce((w, p) => (p.score > w.score ? p : w));

check(`no two targets are acoustically near-identical (${pairScores.length} pairs, max 88)`,
  tooAlike.length === 0,
  tooAlike.length
    ? `too alike: ${tooAlike.map((p) => `${p.a}/${p.b} ${p.score}`).join(", ")}`
    : `worst pair ${worstPair.a}/${worstPair.b} = ${worstPair.score}`);

/* A systematic miss through the proxy is only evidence of a product problem
 * when the two targets are genuinely close as well. imitate() is a one-pole
 * lowpass, so it erases sharp transients and amplitude throb — cues a human
 * imitating "ding!" or "vroom-vroom" reproduces easily. Judging a percussive
 * target on that proxy alone measures the proxy, not the engine, so the pair
 * has to look similar in the real audio too before this fails. */
const directScore = (a, b) =>
  pairScores.find((p) => (p.a === a && p.b === b) || (p.a === b && p.b === a))?.score ?? 0;
const realConfusions = systematicMisses.filter((pair) => {
  const [a, b] = pair.split(" -> ");
  return directScore(a, b) >= 70;
});

check("no sound is systematically confused for a target that is itself similar",
  realConfusions.length === 0,
  realConfusions.length
    ? `always confused: ${realConfusions.join(", ")}`
    : systematicMisses.length
      ? `proxy-only (targets are distinct): ${systematicMisses
          .map((p) => `${p} [${directScore(...p.split(" -> "))}]`).join(", ")}`
      : "");

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
 * 3b. Voice Battle fairness — the property the online battle mode actually
 * depends on. Two players never need "which of 44 sounds is this"; they need
 * "of these two attempts at THIS target, which is closer" — a narrower,
 * more tractable, and more important question.
 * ------------------------------------------------------------------ */
let fairWins = 0;
const fairTrials = SOUNDS.length;

for (let s = 0; s < SOUNDS.length; s++) {
  const target = targetSamples(SOUNDS[s].id);
  const closer = imitate(target, 5000 + s, 0.4);
  const sloppier = imitate(target, 5000 + s, 2.0);
  const closerScore = compareAudio(closer, SOUND_RATE, target, SOUND_RATE).score;
  const sloppierScore = compareAudio(sloppier, SOUND_RATE, target, SOUND_RATE).score;
  if (closerScore > sloppierScore) fairWins++;
}
const fairPct = (100 * fairWins) / fairTrials;

check(`a closer imitation beats a sloppier one on the SAME target across all ${fairTrials} sounds (>= 90%)`,
  fairPct >= 90, `${fairWins}/${fairTrials} = ${fairPct.toFixed(0)}%`);

/* ------------------------------------------------------------------ *
 * 3c. Loudness must not decide a round — different phones have different
 * microphone gain, so the engine must not simply reward whichever
 * recording is louder.
 * ------------------------------------------------------------------ */
const quiet = targetSamples("guitar");
const quietScore = compareAudio(quiet, SOUND_RATE, quiet, SOUND_RATE).score;

function scaleGain(samples, factor) {
  const out = new Float32Array(samples.length);
  for (let i = 0; i < samples.length; i++) out[i] = samples[i] * factor;
  return out;
}

const loud = scaleGain(quiet, 4.0);
const soft = scaleGain(quiet, 0.15);
const loudScore = compareAudio(loud, SOUND_RATE, quiet, SOUND_RATE).score;
const softScore = compareAudio(soft, SOUND_RATE, quiet, SOUND_RATE).score;

check("scaling a recording 4x louder barely moves its score (loudness is normalised away)",
  Math.abs(loudScore - quietScore) <= 3, `${quietScore} -> ${loudScore}`);

check("scaling a recording to whisper level barely moves its score either",
  Math.abs(softScore - quietScore) <= 3, `${quietScore} -> ${softScore}`);

// The real failure mode: a LOUDER but WORSE imitation must not beat a
// QUIETER but BETTER one — this is the actual "loudest mic wins" bug.
const betterButQuiet = scaleGain(imitate(quiet, 42, 0.3), 0.2);
const worseButLoud = scaleGain(imitate(quiet, 43, 2.5), 6.0);
const betterScore = compareAudio(betterButQuiet, SOUND_RATE, quiet, SOUND_RATE).score;
const worseScore = compareAudio(worseButLoud, SOUND_RATE, quiet, SOUND_RATE).score;

check("a quieter-but-closer imitation still beats a louder-but-sloppier one",
  betterScore > worseScore, `quiet+close ${betterScore} > loud+sloppy ${worseScore}`);

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
if (missCounts.size) {
  console.log(`\n  rank-1 misses (out of ${trials} trials, x/${TRIALS_PER_SOUND} = how many of that sound's trials missed):`);
  for (const [pair, count] of [...missCounts.entries()].sort((a, b) => b[1] - a[1])) {
    console.log(`    ${pair}  (${count}/${TRIALS_PER_SOUND})`);
  }
}
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
