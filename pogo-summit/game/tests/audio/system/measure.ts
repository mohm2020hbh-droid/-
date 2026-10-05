/** A small measurement kit for rendered recipes (mirrors the definitions of tools/audio-analyze.py closely enough for tolerance checks). */

/** In-place radix-2 FFT. */
export function fft(re: Float64Array, im: Float64Array): void {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) { [re[i], re[j]] = [re[j], re[i]]; [im[i], im[j]] = [im[j], im[i]]; }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (-2 * Math.PI) / len, wr = Math.cos(ang), wi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let cr = 1, ci = 0;
      for (let k = 0; k < len / 2; k++) {
        const a = i + k, b = i + k + len / 2;
        const tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
        re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti;
        const ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr;
      }
    }
  }
}

export interface Spec { f: Float64Array; p: Float64Array; total: number }

/** Hann-windowed power spectrum of the whole signal. */
export function powerSpectrum(x: Float32Array, sr: number): Spec {
  let n = 1; while (n < x.length) n <<= 1;
  const re = new Float64Array(n), im = new Float64Array(n);
  for (let i = 0; i < x.length; i++) re[i] = x[i] * (0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (x.length - 1)));
  fft(re, im);
  const half = n / 2, f = new Float64Array(half), p = new Float64Array(half);
  let total = 0;
  for (let k = 0; k < half; k++) { f[k] = (k * sr) / n; p[k] = re[k] * re[k] + im[k] * im[k]; total += p[k]; }
  return { f, p, total };
}

export const centroid = (s: Spec): number => { let a = 0; for (let k = 0; k < s.f.length; k++) a += s.f[k] * s.p[k]; return a / s.total; };
export const bandShare = (s: Spec, lo: number, hi: number): number => { let a = 0; for (let k = 0; k < s.f.length; k++) if (s.f[k] >= lo && s.f[k] < hi) a += s.p[k]; return a / s.total; };

/** RMS envelope in dB, 5 ms window, 1 ms hop. */
export function envelopeDb(x: Float32Array, sr: number): { t: Float64Array; db: Float64Array } {
  const win = Math.round(0.005 * sr), hop = Math.round(0.001 * sr);
  const n = Math.max(1, Math.floor((x.length - win) / hop));
  const t = new Float64Array(n), db = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    let s = 0; for (let k = 0; k < win; k++) { const v = x[i * hop + k]; s += v * v; }
    t[i] = (i * hop + win / 2) / sr; db[i] = 10 * Math.log10(s / win + 1e-20);
  }
  return { t, db };
}

/** ms from the first time the envelope exceeds −60 dBFS to the envelope maximum. */
export function attackMs(x: Float32Array, sr: number): number {
  const { t, db } = envelopeDb(x, sr);
  let iMax = 0; for (let i = 1; i < db.length; i++) if (db[i] > db[iMax]) iMax = i;
  let i0 = 0; while (i0 < db.length && db[i0] < -60) i0++;
  return (t[iMax] - t[Math.min(i0, iMax)]) * 1000;
}

/** ms from the envelope maximum until it has fallen 20 dB and stays below. */
export function decay20Ms(x: Float32Array, sr: number): number {
  const { t, db } = envelopeDb(x, sr);
  let iMax = 0; for (let i = 1; i < db.length; i++) if (db[i] > db[iMax]) iMax = i;
  let last = iMax;
  for (let i = iMax; i < db.length; i++) if (db[i] > db[iMax] - 20) last = i;
  return (t[Math.min(last + 1, t.length - 1)] - t[iMax]) * 1000;
}

export const rms = (x: Float32Array): number => { let s = 0; for (let i = 0; i < x.length; i++) s += x[i] * x[i]; return Math.sqrt(s / x.length); };
export const peak = (x: Float32Array): number => { let m = 0; for (let i = 0; i < x.length; i++) m = Math.max(m, Math.abs(x[i])); return m; };
export const mean = (x: Float32Array): number => { let s = 0; for (let i = 0; i < x.length; i++) s += x[i]; return s / x.length; };
export const toDb = (v: number): number => 20 * Math.log10(v);
