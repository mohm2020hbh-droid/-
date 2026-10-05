import { describe, expect, it } from 'vitest';
import { collisionGain, collisionPitch, impactCurve } from '../../../src/audio/system/AudioEvent';
import { AUDIO_CONFIG } from '../../../src/audio/system/audioConfig';
import { mulberry32 } from '../../../src/audio/system/rng';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import { makeRig } from './helpers';

const COLL = AUDIO_EVENT.POGO_COLLISION;
const VARIANTS = ['collision_1', 'collision_2', 'collision_3', 'collision_4'];
const C = AUDIO_CONFIG.collision;

describe('impact speed → volume (DESIGN curve over the ORIGINAL volume 40…50)', () => {
  it('is silent below the minimum impact and audible from it', () => {
    expect(impactCurve(0)).toBe(0);
    expect(impactCurve(C.minImpact - 1e-6)).toBe(0);
    expect(impactCurve(C.minImpact)).toBeCloseTo(C.curveLo, 9);
    expect(impactCurve(NaN)).toBe(0);
  });
  it('is monotonic non-decreasing, bounded in [curveLo, 1] once audible, and 1 at full impact', () => {
    let prev = 0;
    for (let i = 0; i <= 1000; i++) {
      const v = impactCurve(i / 1000);
      expect(v).toBeGreaterThanOrEqual(prev - 1e-12);
      if (i / 1000 >= C.minImpact) { expect(v).toBeGreaterThanOrEqual(C.curveLo - 1e-12); expect(v).toBeLessThanOrEqual(1); }
      prev = v;
    }
    expect(impactCurve(1)).toBeCloseTo(1, 9);
    expect(impactCurve(5)).toBeCloseTo(1, 9);            // clamped
  });
  it('the final gain stays within volume 40…50 scaled by the curve', () => {
    const rng = mulberry32(3);
    for (let i = 0; i < 5000; i++) {
      const imp = rng.next();
      const g = collisionGain(imp, rng), k = impactCurve(imp);
      expect(g).toBeGreaterThanOrEqual(0.4 * k - 1e-12);
      expect(g).toBeLessThanOrEqual(0.5 * k + 1e-12);
    }
  });
  it('harder hits are louder (mean over the random volume)', () => {
    const rng = mulberry32(4);
    const mean = (imp: number) => { let s = 0; for (let i = 0; i < 2000; i++) s += collisionGain(imp, rng); return s / 2000; };
    const m = [0.1, 0.3, 0.5, 0.7, 0.9, 1].map(mean);
    for (let i = 1; i < m.length; i++) expect(m[i]).toBeGreaterThan(m[i - 1]);
  });
});

describe('pitch variation is limited', () => {
  it('stays within [0.93, 1.07] for every impact (10 000 draws)', () => {
    const rng = mulberry32(5);
    let lo = 9, hi = 0;
    for (let i = 0; i < 10000; i++) { const p = collisionPitch(rng.next(), rng); lo = Math.min(lo, p); hi = Math.max(hi, p); }
    expect(lo).toBeGreaterThanOrEqual(0.93);
    expect(hi).toBeLessThanOrEqual(1.07);
    expect(hi - lo).toBeGreaterThan(0.05);          // but it does vary
  });
  it('is far narrower than the original 0.9…1.1 spread would allow on the speed axis alone', () => {
    const rng = { next: () => 0.5 };                // no jitter
    expect(collisionPitch(0, rng)).toBeCloseTo(1 - 0.03, 9);
    expect(collisionPitch(1, rng)).toBeCloseTo(1 + 0.03, 9);
  });
});

describe('POGO_COLLISION through the manager', () => {
  it('gates very weak impacts', () => {
    const { audio, host } = makeRig();
    const r = audio.emit(COLL, { intensity: 0.02 });
    expect(r.played).toBe(false); expect(r.reason).toBe('inaudible');
    expect(host.started).toHaveLength(0);
  });
  it('plays one of the four boings on the PLAYER bus, with a gain from the curve', () => {
    const { audio, host } = makeRig();
    const r = audio.emit(COLL, { intensity: 0.8 });
    expect(r.played).toBe(true);
    expect(VARIANTS).toContain(r.variants[0]);
    expect(host.started).toHaveLength(1);
    expect(host.started[0].params.bus).toBe('PLAYER');
    expect(host.started[0].params.gain).toBeGreaterThan(0.25);
    expect(host.started[0].params.gain).toBeLessThanOrEqual(0.5);
    expect(host.started[0].params.loop).toBe(false);
  });
  it('reaches all four variants and never repeats one immediately (events 1 s apart)', () => {
    const { audio, host } = makeRig(11);
    const seen: string[] = [];
    for (let i = 0; i < 400; i++) { host.advance(1); const r = audio.emit(COLL, { intensity: 0.6 }); expect(r.played).toBe(true); seen.push(r.variants[0]); }
    expect(new Set(seen)).toEqual(new Set(VARIANTS));
    for (let i = 1; i < seen.length; i++) expect(seen[i]).not.toBe(seen[i - 1]);
  });
  it('event cooldown: a second hit 50 ms later is suppressed, one 100 ms later plays', () => {
    const { audio, host } = makeRig();
    expect(audio.emit(COLL, { intensity: 0.6 }).played).toBe(true);
    host.advance(0.05);
    const r = audio.emit(COLL, { intensity: 0.6 });
    expect(r.played).toBe(false); expect(r.reason).toBe('cooldown');
    host.advance(0.05);
    expect(audio.emit(COLL, { intensity: 0.6 }).played).toBe(true);
    expect(audio.stats.skipped.cooldown).toBe(1);
  });
  it('burst cap: at most 4 per 600 ms even when every hit clears the cooldown', () => {
    const { audio, host } = makeRig();
    const res: boolean[] = [];
    for (let i = 0; i < 6; i++) { res.push(audio.emit(COLL, { intensity: 0.6 }).played); host.advance(0.1); }       // 6 hits in 0.6 s
    expect(res.filter(Boolean)).toHaveLength(4);
    expect(audio.stats.skipped.burst).toBe(2);
    host.advance(0.7);                                  // the window slides
    expect(audio.emit(COLL, { intensity: 0.6 }).played).toBe(true);
  });
  it('no machine gun: 100 hits in one second make a handful of sounds, never more than 4 in any 600 ms', () => {
    const { audio, host } = makeRig();
    let played = 0;
    for (let i = 0; i < 100; i++) { if (audio.emit(COLL, { intensity: 0.7 }).played) played++; host.advance(0.01); }
    expect(played).toBeGreaterThan(0);
    expect(played).toBeLessThanOrEqual(8);
    expect(host.started.length).toBe(played);
    const t = host.started.map(v => v.startedAt);
    for (const t0 of t) expect(t.filter(x => x >= t0 && x < t0 + 0.6).length).toBeLessThanOrEqual(4);
    for (let i = 1; i < t.length; i++) expect(t[i] - t[i - 1]).toBeGreaterThanOrEqual(0.09 - 1e-9);
  });
  it('voice cap 3: the fourth long collision steals the oldest voice', () => {
    const { audio, host } = makeRig();
    for (let i = 0; i < 4; i++) { audio.emit(COLL, { intensity: 0.6 }); host.advance(0.1); }
    expect(host.started).toHaveLength(4);
    const live = host.live();
    expect(live.length).toBeLessThanOrEqual(3);
    expect(host.started[0].stopped).toBe(true);        // the oldest was cut
    expect(host.started[0].stopFade).toBeCloseTo(AUDIO_CONFIG.pool.stealFadeSec, 9);
    expect(audio.pool.countFor(COLL)).toBeLessThanOrEqual(3);
  });
  it('a hit within 200 ms of the previous one is attenuated (−4 dB)', () => {
    const mean = (gap: number) => {
      let s = 0, n = 0;
      for (let seed = 1; seed <= 200; seed++) {
        const { audio, host } = makeRig(seed);
        audio.emit(COLL, { intensity: 0.8 }); host.advance(gap);
        const r = audio.emit(COLL, { intensity: 0.8 });
        if (r.played) { s += r.gain; n++; }
      }
      return s / n;
    };
    const close = mean(0.12), far = mean(1.0);
    expect(close / far).toBeGreaterThan(0.55);
    expect(close / far).toBeLessThan(0.72);              // ≈ 0.63
  });
  it('is positional: attenuated and panned by the contact point, silent beyond the range', () => {
    const { audio } = makeRig();
    audio.setListener(0, 0, 10);
    const near = audio.emit(COLL, { intensity: 1, x: 0, y: 0 });
    expect(near.played).toBe(true);
    const mid = makeRig(7); mid.audio.setListener(0, 0, 10);
    const m = mid.audio.emit(COLL, { intensity: 1, x: 22, y: 0 });        // 2·1.25 − 22/10 = 0.3
    expect(m.played).toBe(true);
    expect(m.gain).toBeLessThan(near.gain * 0.7);
    expect(mid.host.started[0].params.pan).toBeGreaterThan(0);
    const far = makeRig(7); far.audio.setListener(0, 0, 10);
    const f = far.audio.emit(COLL, { intensity: 1, x: 40, y: 0 });
    expect(f.played).toBe(false); expect(f.reason).toBe('inaudible');
  });
});
