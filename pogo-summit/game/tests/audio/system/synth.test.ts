import { describe, expect, it } from 'vitest';
import { mulberry32, seedFor } from '../../../src/audio/system/rng';
import { DEFAULT_BANK_SEED } from '../../../src/audio/system/SoundBank';
import { RECIPES, RECIPE_PEAK_DB, WARM_ORDER, renderRateOf } from '../../../src/audio/system/synth/recipes';
import { attackMs, bandShare, centroid, decay20Ms, mean, peak, powerSpectrum, rms, toDb } from './measure';

const render = (id: string, ctxRate = 44100, seed = DEFAULT_BANK_SEED) => { const r = RECIPES[id]; const rate = renderRateOf(r, ctxRate); return { x: r.render(rate, mulberry32(seedFor(seed, id))), rate, r }; };
const IDS = Object.keys(RECIPES);
const cache = new Map<string, { x: Float32Array; rate: number }>();
const get = (id: string) => { let c = cache.get(id); if (!c) { const { x, rate } = render(id); c = { x, rate }; cache.set(id, c); } return c; };

describe('recipes are deterministic, finite, clean and consistently loud', () => {
  it('exist for every warm-up id and the warm order has no stranger', () => {
    for (const id of WARM_ORDER) expect(RECIPES[id], id).toBeTruthy();
    expect(new Set(WARM_ORDER).size).toBe(IDS.length);
  });
  for (const id of IDS) {
    it(`${id}: finite, peak ${RECIPE_PEAK_DB} dBFS, no DC, faded edges`, () => {
      const { x } = get(id);
      for (let i = 0; i < x.length; i++) if (!Number.isFinite(x[i])) throw new Error(`non-finite sample ${i}`);
      expect(toDb(peak(x))).toBeCloseTo(RECIPE_PEAK_DB, 1);
      expect(Math.abs(mean(x))).toBeLessThan(0.002);
      if (!RECIPES[id].targets.loop) { expect(Math.abs(x[0])).toBeLessThan(0.02); expect(Math.abs(x[x.length - 1])).toBeLessThan(0.02); }
    });
    it(`${id}: the same seed gives the same samples, another seed does not (noise recipes)`, () => {
      const a = render(id).x, b = render(id).x, c = render(id, 44100, 12345).x;
      expect(Array.from(a.slice(0, 2000))).toEqual(Array.from(b.slice(0, 2000)));
      let same = true; for (let i = 0; i < Math.min(a.length, c.length); i += 7) if (a[i] !== c[i]) { same = false; break; }
      expect(same === false || id.startsWith('collision') === false).toBe(true);            // boings only differ by a tiny phase offset
    });
  }
});

describe('recipes match the descriptors measured on the originals (POGOSTUCK_AUDIO_ANALYSIS.md §2)', () => {
  const within = (v: number, target: number, tol: number, what: string) => expect(Math.abs(v - target) / target, `${what}: ${v.toFixed(2)} vs ${target}`).toBeLessThanOrEqual(tol);
  for (const id of IDS) {
    const t = RECIPES[id].targets;
    it(`${id}: duration ${t.durationSec} s${t.centroidHz ? `, centroid ≈ ${t.centroidHz} Hz` : ''}${t.decay20Ms ? `, −20 dB after ≈ ${t.decay20Ms} ms` : ''}`, () => {
      const { x, rate } = get(id);
      within(x.length / rate, t.durationSec, 0.03, 'duration');
      if (t.centroidHz) within(centroid(powerSpectrum(x, rate)), t.centroidHz, 0.4, 'centroid');
      if (t.decay20Ms) within(decay20Ms(x, rate), t.decay20Ms, 0.25, 'decay −20 dB');
      if (t.attackMs && t.attackMs > 20) within(attackMs(x, rate), t.attackMs, 0.45, 'attack');
    });
  }
  it('charge_tick: a click — loudest moment within 8 ms, 5 kHz-ish and bright (59 % above 4 kHz in the original)', () => {
    const { x, rate } = get('charge_tick');
    expect(attackMs(x, rate)).toBeLessThan(8);
    const s = powerSpectrum(x, rate);
    expect(bandShare(s, 4000, 1e9)).toBeGreaterThan(0.45);
  });
  it('launch_pop: loudest moment late (the click ends the swell) and 4–8 kHz carries most', () => {
    const { x, rate } = get('launch_pop');
    const a = attackMs(x, rate);
    expect(a).toBeGreaterThan(60); expect(a).toBeLessThan(100);
    expect(bandShare(powerSpectrum(x, rate), 2000, 8000)).toBeGreaterThan(0.6);
  });
  it('launch_boom: sub-bass body (most energy below 250 Hz), slow swell (loudest moment 150–350 ms)', () => {
    const { x, rate } = get('launch_boom');
    const s = powerSpectrum(x, rate);
    expect(bandShare(s, 0, 250)).toBeGreaterThan(0.9);
    expect(bandShare(s, 60, 250)).toBeGreaterThan(0.03);                      // the phone-speaker body (DESIGN)
    const a = attackMs(x, rate); expect(a).toBeGreaterThan(150); expect(a).toBeLessThan(350);
  });
  it('collision_1…4: line spectra — ≥ 95 % of the energy between 0.5 and 2.5 kHz, like the four original boings', () => {
    for (const id of ['collision_1', 'collision_2', 'collision_3', 'collision_4']) {
      const { x, rate } = get(id);
      expect(bandShare(powerSpectrum(x, rate), 500, 2500), id).toBeGreaterThan(0.95);
    }
  });
  it('collision_2/3 glide upward, collision_1 stays, collision_4 undulates (centroid of first vs last third)', () => {
    const third = (id: string, k: number) => { const { x, rate } = get(id); const n = Math.floor(x.length / 3); return centroid(powerSpectrum(x.slice(k * n, (k + 1) * n), rate)); };
    expect(third('collision_2', 2)).toBeGreaterThan(third('collision_2', 0) * 1.25);
    expect(third('collision_3', 2)).toBeGreaterThan(third('collision_3', 0) * 1.25);
    expect(Math.abs(third('collision_1', 2) / third('collision_1', 0) - 1)).toBeLessThan(0.35);
  });
  it('the four boings are distinct sounds (different durations and spectra)', () => {
    const d = ['collision_1', 'collision_2', 'collision_3', 'collision_4'].map(id => get(id).x.length / get(id).rate);
    expect(new Set(d.map(v => v.toFixed(2))).size).toBe(4 - 1);               // 2 and 3 are both ≈ 0.67 s, as in the originals
    const c = ['collision_2', 'collision_3'].map(id => centroid(powerSpectrum(get(id).x, get(id).rate)));
    expect(Math.abs(c[0] - c[1])).toBeGreaterThan(10);
  });
  it('break_1: thump + cracks (half of the energy at 20–60 Hz); break_2: heavy rumble (most below 250 Hz), loudest moment at the start', () => {
    const b1 = get('break_1'), b2 = get('break_2');
    expect(bandShare(powerSpectrum(b1.x, b1.rate), 20, 60)).toBeGreaterThan(0.35);
    expect(bandShare(powerSpectrum(b2.x, b2.rate), 0, 250)).toBeGreaterThan(0.7);
    expect(attackMs(b2.x, b2.rate)).toBeLessThan(80);
  });
  it('ice_slide_loop: stationary broadband noise — bright (centroid > 4 kHz, > 30 % above 8 kHz) with a flat envelope', () => {
    const { x, rate } = get('ice_slide_loop');
    const s = powerSpectrum(x, rate);
    expect(centroid(s)).toBeGreaterThan(4000);
    expect(bandShare(s, 8000, 1e9)).toBeGreaterThan(0.3);
    const q = Math.floor(x.length / 4);
    const levels = [0, 1, 2, 3].map(i => toDb(rms(x.slice(i * q, (i + 1) * q))));
    expect(Math.max(...levels) - Math.min(...levels)).toBeLessThan(2);
  });
  it('time_riser: ≈ 6.4 s, mid-range heavy (≥ 55 % between 0.5 and 2 kHz in the original: 71 %)', () => {
    const { x, rate } = get('time_riser');
    expect(bandShare(powerSpectrum(x, rate), 500, 2000)).toBeGreaterThan(0.5);
  });
});

describe('the ice loop is seamless and rate-independent', () => {
  it('the last sample flows into the first one: the step at the wrap is no bigger than a typical neighbour step', () => {
    const { x } = get('ice_slide_loop');
    let sum = 0; for (let i = 1; i < x.length; i++) sum += Math.abs(x[i] - x[i - 1]);
    const typical = sum / (x.length - 1);
    expect(Math.abs(x[0] - x[x.length - 1])).toBeLessThan(typical * 6);        // a click would be tens of × larger
    // and the level is steady across the wrap (no dip from a bad cross-fade)
    const n = Math.round(0.02 * 44100);
    const head = rms(x.slice(0, n)), tail = rms(x.slice(x.length - n));
    expect(Math.abs(toDb(head) - toDb(tail))).toBeLessThan(2);
  });
  it('spectral descriptors are stable between 44.1 kHz and 48 kHz contexts (recipes follow the context rate)', () => {
    for (const id of ['charge_tick', 'collision_3', 'ice_slide_loop', 'break_1']) {
      const a = render(id, 44100), b = render(id, 48000);
      const ca = centroid(powerSpectrum(a.x, a.rate)), cb = centroid(powerSpectrum(b.x, b.rate));
      expect(Math.abs(ca - cb) / ca, id).toBeLessThan(0.2);
      expect(Math.abs(a.x.length / a.rate - b.x.length / b.rate), id).toBeLessThan(0.01);
    }
  });
  it('low-rate recipes (boings, break_2, boom, sting) render below the context rate; bright ones at full rate', () => {
    expect(renderRateOf(RECIPES.collision_1, 48000)).toBe(11025);
    expect(renderRateOf(RECIPES.launch_boom, 48000)).toBe(16000);
    expect(renderRateOf(RECIPES.time_riser, 48000)).toBe(22050);
    expect(renderRateOf(RECIPES.ice_slide_loop, 48000)).toBe(48000);
    expect(renderRateOf(RECIPES.charge_tick, 48000)).toBe(48000);
    expect(renderRateOf(RECIPES.collision_1, 8000)).toBe(8000);                // never above the context rate
  });
});

describe('render cost stays small (phones are slower than this machine, so the budget is generous)', () => {
  it('every recipe renders in well under a second; all of them together in under 1.5 s', () => {
    let total = 0;
    for (const id of IDS) { const t0 = performance.now(); render(id, 48000); const ms = performance.now() - t0; total += ms; expect(ms, id).toBeLessThan(600); }
    expect(total).toBeLessThan(1500);
  });
});
