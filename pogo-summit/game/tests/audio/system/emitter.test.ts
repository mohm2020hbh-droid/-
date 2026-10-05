import { describe, expect, it } from 'vitest';
import { AudioEmitter, attenuation, originalSpatial, panOf, radiusSpatial } from '../../../src/audio/system/AudioEmitter';
import { attenuation as mapAttenuation, panOf as mapPanOf } from '../../../src/audio/MapAudio';

describe('original positional model (analysis AU-07): gain = clamp(range·1.25 − dist/W, 0, 1), pan = clamp(Δx/W · 0.2, −1, 1)', () => {
  it('range 1: full gain up to 0.25 W, silent from 1.25 W', () => {
    expect(originalSpatial(0, 0, 10, 1).gain).toBe(1);
    expect(originalSpatial(2.5, 0, 10, 1).gain).toBeCloseTo(1, 12);
    expect(originalSpatial(7.5, 0, 10, 1).gain).toBeCloseTo(0.5, 12);
    expect(originalSpatial(12.5, 0, 10, 1).gain).toBe(0);
    expect(originalSpatial(40, 0, 10, 1).gain).toBe(0);
  });
  it('the range classes 1, 1.5, 2, 3 carry further: silent from 1.25·range·W', () => {
    for (const r of [1, 1.5, 2, 3]) {
      expect(originalSpatial(1.25 * r * 10 - 0.001, 0, 10, r).gain).toBeGreaterThan(0);
      expect(originalSpatial(1.25 * r * 10, 0, 10, r).gain).toBe(0);
    }
    expect(originalSpatial(0, 15, 10, 2).gain).toBe(1);                       // distance is Euclidean (y counts)
  });
  it('pan: ±0.2 per half-width, clamped to ±1', () => {
    expect(originalSpatial(10, 0, 10, 3).pan).toBeCloseTo(0.2, 12);
    expect(originalSpatial(-10, 0, 10, 3).pan).toBeCloseTo(-0.2, 12);
    expect(originalSpatial(100, 0, 10, 3).pan).toBe(1);
    expect(originalSpatial(-100, 0, 10, 3).pan).toBe(-1);
    expect(originalSpatial(0, 5, 10, 3).pan).toBe(0);
  });
  it('is monotonic non-increasing with distance and never negative', () => {
    let prev = 1;
    for (let d = 0; d < 60; d += 0.5) { const g = originalSpatial(d, 0, 10, 2).gain; expect(g).toBeLessThanOrEqual(prev + 1e-12); expect(g).toBeGreaterThanOrEqual(0); prev = g; }
  });
  it('a degenerate listener width does not divide by zero', () => {
    const s = originalSpatial(5, 0, 0, 2);
    expect(Number.isFinite(s.gain)).toBe(true); expect(Number.isFinite(s.pan)).toBe(true);
  });
});

describe('radius model (Map V2) is the existing one, shared with MapAudio', () => {
  it('is re-exported unchanged', () => { expect(mapAttenuation).toBe(attenuation); expect(mapPanOf).toBe(panOf); });
  it('1 at the source, 0 at the radius, quadratic in between', () => {
    expect(radiusSpatial(0, 0, 20).gain).toBe(1);
    expect(radiusSpatial(10, 0, 20).gain).toBeCloseTo(0.25, 12);
    expect(radiusSpatial(20, 0, 20).gain).toBe(0);
    expect(radiusSpatial(-8, 0, 20).pan).toBeLessThan(0);
  });
});

describe('AudioEmitter', () => {
  const L = { x: 0, y: 0, halfWidth: 10 };
  it('answers gain and pan for its model', () => {
    const e = new AudioEmitter('e', 5, 0, { model: 'original', range: 2 });
    expect(e.spatial(L)).toEqual(originalSpatial(5, 0, 10, 2));
    const r = new AudioEmitter('r', 5, 0, { model: 'radius', radius: 20 });
    expect(r.spatial(L)).toEqual(radiusSpatial(5, 0, 20));
  });
  it('moves and follows a moving thing', () => {
    let p = { x: 30, y: 0 };
    const e = new AudioEmitter('f', 0, 0, { model: 'original', range: 1, follow: () => p });
    expect(e.spatial(L).gain).toBe(0);
    p = { x: 1, y: 0 };
    expect(e.spatial(L).gain).toBe(1);
    expect(e.x).toBe(1);
    const m = new AudioEmitter('m', 100, 0, { model: 'original', range: 1 });
    m.moveTo(0, 0);
    expect(m.spatial(L).gain).toBe(1);
  });
  it('a follow source that returns null keeps the last position', () => {
    const e = new AudioEmitter('n', 4, 0, { model: 'original', range: 3, follow: () => null });
    expect(e.spatial(L)).toEqual(originalSpatial(4, 0, 10, 3));
  });
});
