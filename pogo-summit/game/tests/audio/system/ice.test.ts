import { describe, expect, it } from 'vitest';
import { iceModulation } from '../../../src/audio/system/AudioEvent';
import { AUDIO_CONFIG } from '../../../src/audio/system/audioConfig';
import { IceSlideController, type LoopHost } from '../../../src/audio/system/IceSlide';
import { isSlideSurface, surfaceClass } from '../../../src/audio/system/MapAudioBridge';
import type { AudioLoop } from '../../../src/audio/AudioManager';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import { makeDirector, makeRig, simEv } from './helpers';

const I = AUDIO_CONFIG.ice;

/** A loop host that records what the controller asks for. */
class FakeLoops implements LoopHost {
  now = 0;
  started: { gain?: number; pitch?: number; loop: FakeLoop }[] = [];
  deny = false;
  startLoop(_id: typeof AUDIO_EVENT.ICE_SLIDE, c: { gain?: number; pitch?: number } = {}): AudioLoop | null {
    if (this.deny) return null;
    const loop = new FakeLoop(); this.started.push({ gain: c.gain, pitch: c.pitch, loop }); return loop;
  }
}
class FakeLoop implements AudioLoop {
  readonly event = AUDIO_EVENT.ICE_SLIDE;
  alive = true; sets: { gain?: number; pitch?: number }[] = []; stopFade: number | null = null;
  get active(): boolean { return this.alive; }
  set(p: { gain?: number; pitch?: number }): void { this.sets.push(p); }
  stop(fade = 0): void { this.alive = false; this.stopFade = fade; }
}

const slide = (o: Partial<{ slideMode: boolean; grounded: boolean; sx: number; sy: number }> = {}) => ({ slideMode: true, grounded: true, sx: 20, sy: 0, ...o });

describe('ice-slide modulation = the ORIGINAL formulas (analysis AU-16)', () => {
  it('pitch = min(0.675 + 0.01·|s|, 1), volume = min(20 + 1.5·|s|, 70)/100', () => {
    for (const s of [0, 1, 5, 10, 20, 32, 40, 100]) {
      const m = iceModulation(s);
      expect(m.pitch).toBeCloseTo(Math.min(0.675 + 0.01 * s, 1), 12);
      expect(m.gain).toBeCloseTo(Math.min(20 + 1.5 * s, 70) / 100, 12);
    }
  });
  it('saturates: full pitch from |s| = 32.5, full volume (0.7) from |s| = 33.33', () => {
    expect(iceModulation(32.5).pitch).toBeCloseTo(1, 12);
    expect(iceModulation(32).pitch).toBeLessThan(1);
    expect(iceModulation(100).pitch).toBe(1);
    expect(iceModulation(33.34).gain).toBeCloseTo(0.7, 12);
    expect(iceModulation(33).gain).toBeLessThan(0.7);
  });
  it('is monotonic non-decreasing in speed and ignores negative input', () => {
    let pg = 0, pp = 0;
    for (let s = 0; s <= 60; s += 0.5) { const m = iceModulation(s); expect(m.gain).toBeGreaterThanOrEqual(pg); expect(m.pitch).toBeGreaterThanOrEqual(pp); pg = m.gain; pp = m.pitch; }
    expect(iceModulation(-5)).toEqual(iceModulation(0));
  });
});

describe('IceSlideController — START / LOOP-MODULATE / STOP', () => {
  it('starts only when really sliding: slide mode ∧ grounded ∧ slippery surface ∧ speed ≥ start', () => {
    const cases: [string, ReturnType<typeof slide>, boolean, boolean][] = [
      ['sliding', slide(), true, true],
      ['not in slide mode', slide({ slideMode: false }), true, false],
      ['airborne', slide({ grounded: false }), true, false],
      ['not an ice / slippery surface', slide(), false, false],
      ['too slow', slide({ sx: I.startSpeed - 0.1 }), true, false],
      ['exactly the start speed', slide({ sx: I.startSpeed }), true, true],
      ['diagonal slide speed counts', slide({ sx: 2.2, sy: 2.2 }), true, true],
    ];
    for (const [name, s, ok, expectStart] of cases) {
      const host = new FakeLoops(), c = new IceSlideController(host);
      c.update(s, ok);
      expect(host.started.length, name).toBe(expectStart ? 1 : 0);
      expect(c.state, name).toBe(expectStart ? 'LOOPING' : 'IDLE');
    }
  });
  it('the loop starts at the original volume / pitch for the current slide speed', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    c.update(slide({ sx: 20 }), true);
    expect(host.started[0].gain).toBeCloseTo(0.5, 9);                       // min(20 + 30, 70)/100
    expect(host.started[0].pitch).toBeCloseTo(0.875, 9);                    // 0.675 + 0.2
  });
  it('modulates every frame while looping', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    c.update(slide({ sx: 10 }), true);
    for (const sp of [12, 18, 25, 40]) c.update(slide({ sx: sp }), true);
    const sets = host.started[0].loop.sets;
    expect(sets).toHaveLength(4);
    expect(sets[3].gain).toBeCloseTo(0.7, 9); expect(sets[3].pitch).toBe(1);
    expect(sets[0].gain).toBeCloseTo((20 + 1.5 * 12) / 100, 9);
    expect(c.counters).toEqual({ starts: 1, stops: 0, modulations: 4 });
  });
  it('stops with the original fade (≈ 0.25 s) when any condition is lost', () => {
    const lost: [string, Partial<{ slideMode: boolean; grounded: boolean; sx: number }>, boolean][] = [
      ['slide mode ends', { slideMode: false }, true],
      ['leaves the ground', { grounded: false }, true],
      ['ground is not slippery any more', {}, false],
      ['speed falls below the stop speed', { sx: I.stopSpeed - 0.1 }, true],
    ];
    for (const [name, change, ok] of lost) {
      const host = new FakeLoops(), c = new IceSlideController(host);
      c.update(slide(), true);
      c.update(slide(change), ok);
      expect(host.started[0].loop.alive, name).toBe(false);
      expect(host.started[0].loop.stopFade, name).toBeCloseTo(I.stopFadeSec, 9);
      expect(c.state, name).toBe('IDLE');
      expect(c.counters.stops, name).toBe(1);
    }
  });
  it('hysteresis: between the stop and start speeds a running loop keeps going and an idle one stays idle', () => {
    const mid = (I.startSpeed + I.stopSpeed) / 2;
    const host = new FakeLoops(), c = new IceSlideController(host);
    c.update(slide({ sx: mid }), true);
    expect(host.started).toHaveLength(0);
    c.update(slide({ sx: 10 }), true);
    c.update(slide({ sx: mid }), true);
    expect(c.state).toBe('LOOPING');
  });
  it('restart guard: no new loop within 0.12 s of a stop, then it may restart', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    c.update(slide(), true);
    c.update(slide({ slideMode: false }), true);                           // stop at t = 0
    host.now = 0.05;
    c.update(slide(), true);
    expect(host.started).toHaveLength(1);
    host.now = I.restartGuardSec + 0.001;
    c.update(slide(), true);
    expect(host.started).toHaveLength(2);
    expect(c.state).toBe('LOOPING');
  });
  it('handle gating: while looping, no second loop is ever requested', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    for (let i = 0; i < 100; i++) c.update(slide({ sx: 10 + (i % 7) }), true);
    expect(host.started).toHaveLength(1);
  });
  it('a loop that was stolen or ended elsewhere returns the controller to IDLE', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    c.update(slide(), true);
    host.started[0].loop.alive = false;
    c.update(slide(), true);
    expect(c.state).toBe('IDLE');
  });
  it('if the manager cannot start the loop (denied), the controller stays idle and retries next frame', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    host.deny = true;
    c.update(slide(), true); expect(c.state).toBe('IDLE');
    host.deny = false;
    c.update(slide(), true); expect(c.state).toBe('LOOPING');
  });
  it('stop() is idempotent and counts once', () => {
    const host = new FakeLoops(), c = new IceSlideController(host);
    c.update(slide(), true);
    c.stop(); c.stop();
    expect(c.counters.stops).toBe(1);
  });
});

describe('surface classes (Map V2 SurfaceType ∪ sim surface ∪ material)', () => {
  it('ICE and SLIPPERY (either vocabulary) allow the slide loop; nothing else does', () => {
    const slideOk: [string | undefined, string | undefined][] = [['slippery', undefined], ['SLIPPERY', 'stone'], ['slippery', 'ice'], ['ICE', undefined], ['ice', 'ice_rock'], ['slippery', 'frozen_slab']];
    for (const [s, m] of slideOk) expect(isSlideSurface(surfaceClass(s, m)), `${s}/${m}`).toBe(true);
    const slideNo: [string | undefined, string | undefined][] = [['normal', 'ice'], ['NORMAL', undefined], ['bounce', undefined], ['hazard', 'lava'], ['sticky', undefined], ['goal', undefined], [undefined, undefined], ['boost', undefined]];
    for (const [s, m] of slideNo) expect(isSlideSurface(surfaceClass(s, m)), `${s}/${m}`).toBe(false);
  });
  it('classifies ice vs slippery, bounce, hazard, lava, water, goal', () => {
    expect(surfaceClass('slippery', 'ice')).toBe('ice');
    expect(surfaceClass('slippery', 'stone')).toBe('slippery');
    expect(surfaceClass('BOUNCE')).toBe('bounce');
    expect(surfaceClass('hazard', 'rock')).toBe('hazard');
    expect(surfaceClass('hazard', 'lava')).toBe('lava');
    expect(surfaceClass('LAVA')).toBe('lava');
    expect(surfaceClass('normal', 'water')).toBe('water');
    expect(surfaceClass('GOAL')).toBe('goal');
    expect(surfaceClass('normal', 'grass')).toBe('normal');
  });
});

describe('ice slide through the director and the manager', () => {
  const surfaces: Record<number, { surface: string; material: string }> = { 0: { surface: 'normal', material: 'grass' }, 1: { surface: 'slippery', material: 'ice' } };
  const state = (o: Partial<{ slideMode: boolean; grounded: boolean; sx: number; sy: number; groundId: number }> = {}) => ({ slideMode: true, grounded: true, sx: 24, sy: 0, groundId: 1, x: 0, y: 0, ...o });

  it('plays a SURFACE-bus loop with a random start offset, follows the speed, fades out at the end', () => {
    const rig = makeRig(), dir = makeDirector(rig, { surfaceOf: id => surfaces[id] });
    dir.update(state(), 10);
    expect(rig.host.started).toHaveLength(1);
    const v = rig.host.started[0];
    expect(v.params.loop).toBe(true);
    expect(v.params.bus).toBe('SURFACE');
    expect(v.params.offset!).toBeGreaterThanOrEqual(0);
    expect(v.params.offset!).toBeLessThan(v.buffer.duration);
    expect(v.params.gain).toBeCloseTo(0.56, 9);                           // (20 + 1.5·24)/100
    dir.update(state({ sx: 35 }), 10);
    expect(v.gain).toBeCloseTo(0.7, 9); expect(v.pitch).toBeCloseTo(1, 9);
    expect(rig.audio.pool.countFor(AUDIO_EVENT.ICE_SLIDE)).toBe(1);
    dir.update(state({ slideMode: false }), 10);
    expect(v.stopped).toBe(true);
    expect(v.stopFade).toBeCloseTo(0.25, 9);
    expect(rig.audio.pool.countFor(AUDIO_EVENT.ICE_SLIDE)).toBe(0);
  });
  it('does not start on a normal surface even if the slide flag is set, nor in mid-air', () => {
    const rig = makeRig(), dir = makeDirector(rig, { surfaceOf: id => surfaces[id] });
    dir.update(state({ groundId: 0 }), 10);
    dir.update(state({ grounded: false }), 10);
    expect(rig.host.started).toHaveLength(0);
  });
  it('the same loop handle is returned while it plays; after a stop a new voice starts', () => {
    const rig = makeRig();
    const a = rig.audio.startLoop(AUDIO_EVENT.ICE_SLIDE, { gain: 0.5 })!;
    expect(a.active).toBe(true);
    expect(rig.audio.startLoop(AUDIO_EVENT.ICE_SLIDE, { gain: 0.5 })).toBe(a);
    expect(rig.host.started).toHaveLength(1);
    a.stop(0.25);
    expect(a.active).toBe(false);
    const b = rig.audio.startLoop(AUDIO_EVENT.ICE_SLIDE, { gain: 0.5 })!;
    expect(b).not.toBe(a);
    expect(rig.host.started).toHaveLength(2);
  });
  it('a respawn silences the loop; a slide *event* does not start one', () => {
    const rig = makeRig(), dir = makeDirector(rig, { surfaceOf: id => surfaces[id] });
    dir.update(state(), 10);
    dir.handle([simEv('slide'), simEv('respawn')]);
    expect(rig.host.started[0].stopped).toBe(true);
  });
  it('silence() (pause / results / level change) fades every loop out', () => {
    const rig = makeRig(), dir = makeDirector(rig, { surfaceOf: id => surfaces[id] });
    dir.update(state(), 10);
    dir.silence();
    expect(rig.host.live()).toHaveLength(0);
  });
  it('without a surface lookup the slide flag alone is trusted (the physics only sets it on a slippery landing)', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.update(state(), 10);
    expect(rig.host.started).toHaveLength(1);
  });
  it('the loop cannot start before the audio is unlocked', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    rig.host.ready = false;
    dir.update(state(), 10);
    expect(rig.host.started).toHaveLength(0);
    expect(dir.ice.state).toBe('IDLE');
    rig.host.ready = true;
    dir.update(state(), 10);
    expect(rig.host.started).toHaveLength(1);
  });
});
