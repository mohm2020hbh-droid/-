import type { Rng } from '../types';
import { Biquad, PinkNoise, SweepOsc, TAU, decayTau, fadeEdges, makeLoop, normalize, removeDc, smoothstep, softClip, white } from './dsp';

/**
 * Procedural recipes — one per variant. Each renders MONO PCM once (seeded, deterministic) and is cached as a buffer.
 *
 * They are written from scratch. What they take from the original sounds are *measured descriptors* only (duration, spectral
 * balance, envelope shape — POGOSTUCK_AUDIO_ANALYSIS.md §2), never samples or spectra copied sample by sample: "timing copied,
 * not the file". `targets` records the descriptors a recipe is shaped by; `tests/audio/system/synth.test.ts` checks the rendered
 * result against them.
 */

export interface RecipeTargets {
  /** seconds, ± `durationTol` (relative) */
  durationSec: number;
  /** Hz, whole-buffer power-weighted spectral centroid */
  centroidHz?: number;
  /** ms: time from the envelope maximum until the envelope is 20 dB lower */
  decay20Ms?: number;
  /** ms: time from the first sample above −60 dB to the envelope maximum */
  attackMs?: number;
  /** a loop: the last sample must flow into the first one */
  loop?: boolean;
}

export interface Recipe {
  id: string;
  /** which original file the descriptors come from (provenance only) */
  analog: string;
  targets: RecipeTargets;
  /**
   * Highest rate the recipe needs, Hz. The bank renders at `min(context rate, renderRate)` and the platform resamples when it plays:
   * sounds whose content stays below a few kHz cost a fraction of the render time (matters on phones).
   */
  renderRate?: number;
  render(sr: number, rng: Rng): Float32Array;
}

/** The rate a recipe is actually rendered at for a context running at `ctxRate`. */
export const renderRateOf = (r: Recipe, ctxRate: number): number => Math.min(ctxRate, r.renderRate ?? ctxRate);

/** Common peak of every recipe, dBFS; relative loudness between events comes from the event gains. */
export const RECIPE_PEAK_DB = -3;

const finish = (x: Float32Array, sr: number, fadeOut = 0.004): Float32Array => {
  removeDc(x, sr);
  fadeEdges(x, sr, 0.0005, fadeOut);
  normalize(x, RECIPE_PEAK_DB);
  return x;
};

/** Short burst of band-passed noise (a "crack"): `len` seconds, centre `fc`, decaying envelope. Added into `out` at `t0`. */
function crack(out: Float32Array, sr: number, rng: Rng, t0: number, amp: number, fc: number, len: number, q = 0.9): void {
  const bp = new Biquad('bp', fc, q, sr);
  const n = Math.round(len * sr), i0 = Math.round(t0 * sr), tau = len / 3.2;
  for (let i = 0; i < n && i0 + i < out.length; i++) {
    const t = i / sr;
    out[i0 + i] += bp.process(white(rng)) * amp * Math.exp(-t / tau) * Math.min(1, t / 0.0004);
  }
}

// ── charge ───────────────────────────────────────────────────────────────────────────────────────────────────────
/** pogoLoad2 analogue: a 105 ms bright tick with a ≈ 440 Hz ring, 2.5 ms to its loudest moment, −20 dB after ≈ 66 ms. */
const chargeTick: Recipe = {
  id: 'charge_tick', analog: 'pogoLoad2',
  targets: { durationSec: 0.105, centroidHz: 4900, decay20Ms: 66, attackMs: 2.5 },
  render(sr, rng) {
    const n = Math.round(0.105 * sr), out = new Float32Array(n);
    const bp = new Biquad('bp', 4300, 0.38, sr), lp = new Biquad('lp', 10500, 0.7, sr);
    const tau = decayTau(0.066);
    for (let i = 0; i < n; i++) {
      const t = i / sr;
      const noise = lp.process(bp.process(white(rng))) * (1 - Math.exp(-t / 0.0006)) * Math.exp(-t / tau);
      const ring = Math.sin(TAU * 441 * t) * Math.exp(-t / 0.026) * 0.2 + Math.sin(TAU * 1320 * t) * Math.exp(-t / 0.016) * 0.07;
      out[i] = noise * 1.9 + ring;
    }
    return finish(out, sr, 0.012);
  },
};

// ── launch ───────────────────────────────────────────────────────────────────────────────────────────────────────
/** pogoLaunch2 analogue: noise that swells for ≈ 80 ms into a click, then dies in ≈ 40 ms; 4–8 kHz carries most of it. */
const launchPop: Recipe = {
  id: 'launch_pop', analog: 'pogoLaunch2',
  targets: { durationSec: 0.15, centroidHz: 4800, attackMs: 80 },
  render(sr, rng) {
    const n = Math.round(0.15 * sr), out = new Float32Array(n);
    const sw = new Biquad('bp', 4200, 0.5, sr), sw2 = new Biquad('lp', 7800, 0.7, sr), ck = new Biquad('bp', 3700, 0.45, sr), ck2 = new Biquad('lp', 7800, 0.7, sr);
    const tc = 0.08, tauC = decayTau(0.019);
    for (let i = 0; i < n; i++) {
      const t = i / sr;
      // swell: −30 dB → −8 dB over 80 ms
      const swell = t < tc ? Math.pow(10, (-30 + 22 * smoothstep(t / tc)) / 20) : 0;
      let v = sw2.process(sw.process(white(rng))) * swell * 1.4;
      // click at tc, 19 ms to −20 dB
      if (t >= tc) v += ck2.process(ck.process(white(rng))) * Math.exp(-(t - tc) / tauC) * 1.0;
      else ck2.process(ck.process(white(rng)));
      out[i] = v;
    }
    return finish(out, sr, 0.01);
  },
};

/** pogoLaunch3 analogue: a slow sub-bass swell and decay, a short downward whistle, a rumbling tail. Plus a 54/81 Hz body so it is audible on phone speakers (DESIGN). */
const launchBoom: Recipe = {
  id: 'launch_boom', analog: 'pogoLaunch3', renderRate: 16000,
  targets: { durationSec: 1.9, centroidHz: 250, decay20Ms: 487, attackMs: 222 },
  render(sr, rng) {
    const n = Math.round(1.9 * sr), out = new Float32Array(n);
    const sub = new SweepOsc(sr), body1 = new SweepOsc(sr), body2 = new SweepOsc(sr), wh = new SweepOsc(sr);
    const rum = new Biquad('lp', 380, 0.8, sr), rumHi = new Biquad('hp', 60, 0.7, sr), whoosh = new Biquad('bp', 1500, 0.6, sr);
    const tau = decayTau(0.43);
    for (let i = 0; i < n; i++) {
      const t = i / sr;
      const swell = smoothstep(t / 0.24) * (t < 0.24 ? 1 : Math.exp(-(t - 0.24) / tau));
      const f0 = 30 - 6 * Math.min(1, t / 0.8);                       // 30 → 24 Hz
      let v = sub.next(f0) * 1.0 * swell;
      v += (body1.next(f0 * 2.1) * 0.5 + body2.next(f0 * 3.2) * 0.3) * swell;
      const wf = 2500 * Math.pow(700 / 2500, Math.min(1, t / 0.3));   // whistle 2.5 kHz → 700 Hz
      v += wh.next(wf) * 0.45 * Math.exp(-t / 0.16) * smoothstep(t / 0.03);
      v += whoosh.process(white(rng)) * 0.6 * smoothstep(t / 0.1) * Math.exp(-t / 0.35);
      v += rumHi.process(rum.process(white(rng))) * 0.3 * smoothstep(t / 0.12) * Math.exp(-t / 0.85);
      out[i] = v;
    }
    softClip(out, 1.2);
    return finish(out, sr, 0.15);
  },
};

// ── collision ("boing": a ≈ 118 Hz comb under a gliding ≈ 1 kHz resonance) ──────────────────────────────────────
interface Boing {
  durationSec: number;
  /** pulse (comb) rate, Hz */
  f0: number;
  /** resonance centre as a function of time */
  centre: (t: number) => number;
  /** resonance width (Gaussian σ, Hz) */
  sigma: number;
  /** attack time constant and decay time constant, seconds */
  tauA: number; tauD: number;
  /** slow amplitude wobble (Hz, depth 0..1) */
  wobble?: [number, number];
}

function renderBoing(b: Boing, sr: number, rng: Rng): Float32Array {
  const n = Math.round(b.durationSec * sr), out = new Float32Array(n);
  const phase0 = rng.next() * 0.3;                                    // tiny per-render phase variation, still deterministic by seed
  let kLo = 1, kHi = 1, lastBand = -1;
  for (let i = 0; i < n; i++) {
    const t = i / sr;
    const fc = b.centre(t);
    const band = (i >> 5);                                            // recompute the active harmonic window every 32 samples
    if (band !== lastBand) { lastBand = band; kLo = Math.max(1, Math.floor((fc - 3.3 * b.sigma) / b.f0)); kHi = Math.ceil((fc + 3.3 * b.sigma) / b.f0); }
    let v = 0;
    for (let k = kLo; k <= kHi; k++) {
      const df = (k * b.f0 - fc) / b.sigma;
      v += Math.exp(-0.5 * df * df) * Math.sin(TAU * k * b.f0 * t + phase0);
    }
    const env = (1 - Math.exp(-t / b.tauA)) * Math.exp(-t / b.tauD) * (b.wobble ? 1 + b.wobble[1] * Math.sin(TAU * b.wobble[0] * t) : 1);
    out[i] = v * env;
  }
  return finish(out, sr, 0.04);
}

const glide = (f1: number, f2: number, t0: number, span: number) => (t: number): number => f1 + (f2 - f1) * smoothstep((t - t0) / span);

const boing = (id: string, analog: string, targets: RecipeTargets, b: Boing): Recipe => ({ id, analog, targets, renderRate: 11025, render: (sr, rng) => renderBoing(b, sr, rng) });

const collision1 = boing('collision_1', 'bounce1', { durationSec: 1.6, centroidHz: 1150, decay20Ms: 1100, attackMs: 57 }, {
  durationSec: 1.6, f0: 117.8, sigma: 125, tauA: 0.0095, tauD: decayTau(1.2),
  centre: t => glide(830, 1120, 0, 0.14)(t) + 55 * Math.sin(TAU * 0.63 * t), wobble: [0.63, 0.25],
});
const collision2 = boing('collision_2', 'bounce2', { durationSec: 0.665, centroidHz: 1200, decay20Ms: 460, attackMs: 23 }, {
  durationSec: 0.665, f0: 117.8, sigma: 140, tauA: 0.009, tauD: decayTau(0.46),
  centre: glide(715, 1430, 0.05, 0.27),
});
const collision3 = boing('collision_3', 'bounce3', { durationSec: 0.669, centroidHz: 1290, decay20Ms: 467, attackMs: 15 }, {
  durationSec: 0.669, f0: 117.8, sigma: 150, tauA: 0.005, tauD: decayTau(0.467),
  centre: glide(830, 1650, 0.09, 0.3),
});
const collision4 = boing('collision_4', 'bounce4', { durationSec: 0.92, centroidHz: 1130, decay20Ms: 594, attackMs: 92 }, {
  durationSec: 0.92, f0: 117.8, sigma: 125, tauA: 0.04, tauD: decayTau(0.62),
  centre: t => 855 * Math.pow(2, 1.15 * Math.pow(0.5 + 0.5 * Math.sin(TAU * 5.2 * t - Math.PI / 2), 2)),
});

// ── breaking ─────────────────────────────────────────────────────────────────────────────────────────────────────
/** break1 analogue: a crack burst on a ≈ 64 Hz thump, 0.52 s, −20 dB after ≈ 190 ms. */
const break1: Recipe = {
  id: 'break_1', analog: 'break1', renderRate: 22050,
  targets: { durationSec: 0.52, centroidHz: 765, decay20Ms: 186 },
  render(sr, rng) {
    const n = Math.round(0.52 * sr), out = new Float32Array(n);
    const th = new SweepOsc(sr), rat = new Biquad('lp', 600, 0.8, sr), rat2 = new Biquad('bp', 3000, 0.5, sr), top = new Biquad('lp', 5000, 0.7, sr);
    const tauT = decayTau(0.26);
    for (let i = 0; i < n; i++) {
      const t = i / sr;
      out[i] = th.next(64 - 14 * Math.min(1, t / 0.2)) * 0.36 * Math.exp(-t / tauT) * Math.min(1, t / 0.004)
        + rat.process(white(rng)) * 0.8 * Math.exp(-t / 0.11) * Math.min(1, t / 0.002)
        + rat2.process(white(rng)) * 0.12 * Math.exp(-t / 0.09) * Math.min(1, t / 0.002);
    }
    // six cracks over the first 0.25 s with the same *profile* as the original (the third, ≈ 70 ms in, is the loudest); times and
    // levels jitter per seed so no two renders are identical
    const at = [0.0, 0.03, 0.073, 0.122, 0.167, 0.224], lv = [-3, -3.4, 0, -9, -11.5, -17], fc = [900, 1700, 1100, 2400, 1400, 800];
    for (let k = 0; k < 6; k++) {
      crack(out, sr, rng, Math.max(0, at[k] + (rng.next() * 2 - 1) * 0.004), Math.pow(10, (lv[k] + (rng.next() * 2 - 1) * 1.0) / 20) * 3.6, fc[k] * (0.9 + rng.next() * 0.2), 0.008 + rng.next() * 0.008);
    }
    for (let i = 0; i < n; i++) out[i] = top.process(out[i]);
    return finish(out, sr, 0.05);
  },
};

/** break2 analogue: a heavy 30–140 Hz rumble that holds for about a second, with ≈ 14 cracks on top, 1.86 s. */
const break2: Recipe = {
  id: 'break_2', analog: 'break2', renderRate: 16000,
  targets: { durationSec: 1.86, centroidHz: 290, decay20Ms: 1300 },
  render(sr, rng) {
    const n = Math.round(1.86 * sr), out = new Float32Array(n);
    const lp = new Biquad('lp', 240, 0.7, sr), mid = new Biquad('bp', 130, 0.9, sr), s1 = new SweepOsc(sr), s2 = new SweepOsc(sr), deb = new Biquad('bp', 1500, 0.7, sr);
    const tauR = 0.17;
    for (let i = 0; i < n; i++) {
      const t = i / sr;
      const env = Math.min(1, t / 0.01) * (t < 1.0 ? 0.88 + 0.12 * Math.sin(TAU * 3.1 * t) : 0.88 * Math.exp(-(t - 1.0) / tauR));
      out[i] = (lp.process(white(rng)) * 2.6 + mid.process(white(rng)) * 2.2 + s1.next(43) * 0.07 + s2.next(55) * 0.05) * env
        + deb.process(white(rng)) * 0.1 * Math.min(1, t / 0.05) * Math.exp(-Math.max(0, t - 0.3) / 0.6);
    }
    crack(out, sr, rng, 0.012, 4.2, 800, 0.03, 0.7);                       // the opening impact: the loudest moment is at the very start
    const times: number[] = [];
    for (let k = 0; k < 13; k++) times.push(0.06 + rng.next() * 1.15);
    times.sort((a, b) => a - b);
    times.forEach((t0, k) => crack(out, sr, rng, t0, Math.pow(10, (-5 - k * 0.7 + (rng.next() * 2 - 1) * 2) / 20) * 1.9, 400 + rng.next() * 5000, 0.01 + rng.next() * 0.02, 0.8));
    return finish(out, sr, 0.15);
  },
};

// ── ice slide (seamless loop) ──────────────────────────────────────────────────────────────────────────────────
/** iceSlide analogue: stationary broadband noise, pink up to ≈ 6 kHz then flatter (≈ 41 % of the energy above 8 kHz), flat envelope, seamless loop. */
const iceSlideLoop: Recipe = {
  id: 'ice_slide_loop', analog: 'iceSlide',
  targets: { durationSec: 1.4, centroidHz: 6100, loop: true },
  render(sr, rng) {
    const loopLen = Math.round(1.4 * sr), fade = Math.round(0.09 * sr), n = loopLen + fade;
    const src = new Float32Array(n), pink = new PinkNoise();
    const hp = new Biquad('hp', 260, 0.7, sr), hp2 = new Biquad('hp', 260, 0.7, sr), top = new Biquad('hp', 7500, 0.7, sr), roll = new Biquad('lp', 17500, 0.6, sr);
    for (let i = 0; i < n; i++) {
      const w = white(rng), t = i / sr;
      const base = hp2.process(hp.process(pink.next(w)));
      const air = top.process(white(rng)) * 0.95;
      src[i] = roll.process(base * 1.75 + air) * (1 + 0.05 * Math.sin(TAU * 0.9 * t + 1.3) + 0.04 * Math.sin(TAU * 7.9 * t));
    }
    const out = makeLoop(src, loopLen, fade);
    removeDc(out, sr);
    normalize(out, RECIPE_PEAK_DB);
    return out;
  },
};

// ── time effect ──────────────────────────────────────────────────────────────────────────────────────────────────
/** pogoTime analogue: a wavering 1.3 s opening, then a stack of partials that rises ≈ 0.13 → 4 kHz over several seconds, 6.4 s. */
const timeRiser: Recipe = {
  id: 'time_riser', analog: 'pogoTime', renderRate: 22050,
  targets: { durationSec: 6.4, centroidHz: 1210, decay20Ms: 4900 },
  render(sr, rng) {
    const n = Math.round(6.4 * sr), out = new Float32Array(n);
    const partials = Array.from({ length: 7 }, () => new SweepOsc(sr, rng.next() * TAU));
    const weights = partials.map((_, k) => 0.4 / Math.pow(k + 1, 0.85));
    const low = new SweepOsc(sr), g1 = new Biquad('bp', 500, 4, sr), g2 = new Biquad('bp', 1300, 4, sr), bed = new Biquad('bp', 900, 0.7, sr);
    const tauAll = decayTau(4.9);
    for (let i = 0; i < n; i++) {
      const t = i / sr;
      const env = Math.min(1, t / 0.056) * (t < 0.9 ? 1 : Math.exp(-(t - 0.9) / tauAll));
      // opening gesture 0–1.3 s: two sweeping noise formants
      const open = t < 1.4 ? Math.sin(Math.PI * Math.min(1, t / 1.4)) : 0;
      g1.set(300 + 600 * smoothstep(t / 1.3)); g2.set(900 + 1400 * smoothstep(t / 1.3));
      let v = (g1.process(white(rng)) * 0.9 + g2.process(white(rng)) * 0.5) * open * 2.4 + bed.process(white(rng)) * 5.5 * Math.exp(-t / 0.07) * Math.min(1, t / 0.004);
      // rising partial stack from 0.8 s
      const rise = smoothstep((t - 0.6) / 0.8);
      const f0 = 330 * Math.pow(2, 0.42 * Math.max(0, t - 0.6));              // ≈ +0.42 octave/s
      const tailEnv = t > 4.4 ? Math.exp(-(t - 4.4) / 2.2) : 1;
      for (let k = 0; k < partials.length; k++) {
        const fk = f0 * (k + 1) * (1 + 0.0015 * k);
        if (fk < sr * 0.45) v += partials[k].next(fk) * rise * weights[k] * tailEnv;       // no partial above 0.45·sr (no aliasing)
      }
      v += low.next(44) * 0.3 * Math.exp(-t / 1.9) * Math.min(1, t / 0.05);
      v += bed.process(white(rng)) * 0.05 * rise;
      out[i] = v * env;
    }
    softClip(out, 1.1);
    return finish(out, sr, 0.4);
  },
};

export const RECIPES: Readonly<Record<string, Recipe>> = Object.fromEntries(
  [chargeTick, launchPop, launchBoom, collision1, collision2, collision3, collision4, break1, break2, iceSlideLoop, timeRiser].map(r => [r.id, r]),
);

/** Warm-up order: what the first seconds of play need first; the 6.4 s sting last. */
export const WARM_ORDER: readonly string[] = ['charge_tick', 'launch_pop', 'collision_2', 'collision_3', 'collision_1', 'collision_4', 'ice_slide_loop', 'break_1', 'launch_boom', 'break_2', 'time_riser'];
