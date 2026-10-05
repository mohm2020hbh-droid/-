import type { Rng } from '../types';

/**
 * A small pure-JS DSP kit: enough to render the game's sounds offline into cached buffers. Everything is deterministic (seeded
 * RNG), allocation-light and free of WebAudio, so recipes run (and are measured) under Node as well as in the WebView.
 */

export const TAU = Math.PI * 2;
export const dbToGain = (db: number): number => Math.pow(10, db / 20);
export const smoothstep = (x: number): number => { const t = x < 0 ? 0 : x > 1 ? 1 : x; return t * t * (3 - 2 * t); };
export const clamp = (v: number, a: number, b: number): number => (v < a ? a : v > b ? b : v);

/** Uniform white noise in [−1, 1). */
export const white = (r: Rng): number => r.next() * 2 - 1;

export type BiquadType = 'lp' | 'hp' | 'bp';

/** RBJ-cookbook biquad (direct form I). `set` retunes without clearing the state, so frequency sweeps are click-free. */
export class Biquad {
  private b0 = 0; private b1 = 0; private b2 = 0; private a1 = 0; private a2 = 0;
  private x1 = 0; private x2 = 0; private y1 = 0; private y2 = 0;
  private q: number;
  constructor(readonly type: BiquadType, f: number, q: number, readonly sr: number) { this.q = q; this.set(f, q); }

  set(f: number, q = this.q): void {
    this.q = q;
    const fc = clamp(f, 10, this.sr * 0.49);
    const w0 = (TAU * fc) / this.sr, cw = Math.cos(w0), alpha = Math.sin(w0) / (2 * q);
    let b0: number, b1: number, b2: number;
    if (this.type === 'lp') { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = b0; }
    else if (this.type === 'hp') { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = b0; }
    else { b0 = alpha; b1 = 0; b2 = -alpha; }
    const a0 = 1 + alpha;
    this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0;
    this.a1 = (-2 * cw) / a0; this.a2 = (1 - alpha) / a0;
  }

  process(x: number): number {
    const y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
    this.x2 = this.x1; this.x1 = x; this.y2 = this.y1; this.y1 = y;
    return y;
  }
}

/** Paul Kellet's economy pink-noise filter (≈ −3 dB/octave). */
export class PinkNoise {
  private b0 = 0; private b1 = 0; private b2 = 0;
  next(w: number): number {
    this.b0 = 0.99765 * this.b0 + w * 0.099046;
    this.b1 = 0.963 * this.b1 + w * 0.2965164;
    this.b2 = 0.57 * this.b2 + w * 1.0526913;
    return (this.b0 + this.b1 + this.b2 + w * 0.1848) * 0.25;
  }
}

/** One-pole DC blocker (≈ 12 Hz corner) applied in place. */
export function removeDc(x: Float32Array, sr: number): void {
  const R = 1 - (TAU * 12) / sr;
  let px = 0, py = 0;
  for (let i = 0; i < x.length; i++) { const v = x[i]; const y = v - px + R * py; px = v; py = y; x[i] = y; }
}

/** Linear fade-in / fade-out (seconds) in place — guarantees no click at either end. */
export function fadeEdges(x: Float32Array, sr: number, inSec: number, outSec: number): void {
  const a = Math.min(x.length, Math.round(inSec * sr)), b = Math.min(x.length, Math.round(outSec * sr));
  for (let i = 0; i < a; i++) x[i] *= i / a;
  for (let i = 0; i < b; i++) x[x.length - 1 - i] *= i / b;
}

export function peakOf(x: Float32Array): number { let m = 0; for (let i = 0; i < x.length; i++) { const a = Math.abs(x[i]); if (a > m) m = a; } return m; }

/** Scale so that the sample peak equals `peakDb` dBFS. */
export function normalize(x: Float32Array, peakDb: number): void {
  const p = peakOf(x);
  if (p < 1e-9) return;
  const k = dbToGain(peakDb) / p;
  for (let i = 0; i < x.length; i++) x[i] *= k;
}

/** Soft limiter (tanh) — keeps sums of layers from exceeding full scale without hard clipping. */
export function softClip(x: Float32Array, drive = 1): void {
  for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * drive) / Math.tanh(drive);
}

/**
 * Make a seamless loop of `loopLen` samples from a source that is at least `loopLen + fade` long. The first `fade` samples are
 * an equal-power cross-fade from the source's *overshoot* (the samples after `loopLen`, which is what naturally follows the last
 * sample) into the source's own start — so the last sample flows into the first one without a step.
 */
export function makeLoop(x: Float32Array, loopLen: number, fade: number): Float32Array {
  const out = x.slice(0, loopLen);
  for (let i = 0; i < fade; i++) {
    const t = (i / fade) * Math.PI / 2;
    out[i] = x[i] * Math.sin(t) + x[loopLen + i] * Math.cos(t);
  }
  return out;
}

/** Sine with a frequency that is a function of time (phase accumulated, so glides are click-free). */
export class SweepOsc {
  private ph = 0;
  constructor(private readonly sr: number, ph0 = 0) { this.ph = ph0; }
  next(freq: number): number { this.ph += (TAU * freq) / this.sr; if (this.ph > TAU) this.ph -= TAU; return Math.sin(this.ph); }
}

/** Exponential-decay envelope helper: amplitude at `t` for a decay that reaches −`db` dB after `sec` seconds. */
export const decayTau = (sec: number, db = 20): number => sec / (db / 8.685889638);
