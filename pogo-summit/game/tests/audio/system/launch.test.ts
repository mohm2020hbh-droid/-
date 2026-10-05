import { describe, expect, it } from 'vitest';
import { SFX } from '../../../src/audio/AudioEvents';
import { launchScale } from '../../../src/audio/system/AudioEvent';
import { AUDIO_CONFIG } from '../../../src/audio/system/audioConfig';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import { mulberry32 } from '../../../src/audio/system/rng';
import { makeDirector, makeRig, simEv } from './helpers';

describe('POGO_LAUNCH — the standard layer always, the power layer when the sim says boost', () => {
  it('an ordinary launch plays only the pop, on the PLAYER bus', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('launch', { intensity: 1, tick: 5 })]);
    const pop = rig.host.started.filter(v => v.buffer.duration < 0.5);
    expect(rig.host.started.filter(v => v.params.bus === 'PLAYER')).toHaveLength(rig.host.started.length);
    expect(pop).toHaveLength(1);
    expect(rig.host.started.every(v => v.buffer.duration < 0.5)).toBe(true);        // no boom
  });
  it('a launch with a boost event on the same tick adds the 1.9 s boom layer', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('launch', { intensity: 1, tick: 5 }), simEv('boost', { tick: 5 })]);
    const durations = rig.host.started.map(v => v.buffer.duration).sort((a, b) => a - b);
    expect(durations).toHaveLength(2);
    expect(durations[0]).toBeLessThan(0.3);
    expect(durations[1]).toBeGreaterThan(1.5);
  });
  it('a boost event on another tick does not turn that launch into a power launch', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('launch', { intensity: 1, tick: 5 }), simEv('boost', { tick: 6 })]);
    expect(rig.host.started).toHaveLength(1);
  });
  it('the boost event itself makes no separate sound', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('boost', { tick: 5 })]);
    expect(rig.host.started).toHaveLength(0);
  });
  it('volumes follow the original: pop 50 (scaled by charge power), boom 90…100', () => {
    const L = AUDIO_CONFIG.launch;
    for (let seed = 1; seed <= 30; seed++) {
      const rig = makeRig(seed), dir = makeDirector(rig);
      dir.handle([simEv('launch', { intensity: 1, tick: 1 }), simEv('boost', { tick: 1 })]);
      const [pop, boom] = rig.host.started.slice().sort((a, b) => a.buffer.duration - b.buffer.duration);
      expect(pop.params.gain).toBeCloseTo(0.5, 9);
      expect(boom.params.gain).toBeGreaterThanOrEqual(0.9 - 1e-9);
      expect(boom.params.gain).toBeLessThanOrEqual(1.0 + 1e-9);
      expect(pop.params.pitch).toBeGreaterThanOrEqual(L.pitchMin); expect(pop.params.pitch).toBeLessThanOrEqual(L.pitchMax);
      expect(boom.params.pitch).toBeGreaterThanOrEqual(L.powerPitchMin); expect(boom.params.pitch).toBeLessThanOrEqual(L.powerPitchMax);
    }
  });
  it('a weaker launch (less charge) is quieter, monotonically', () => {
    expect(launchScale(0)).toBeCloseTo(AUDIO_CONFIG.launch.minScale, 9);
    expect(launchScale(1)).toBe(1);
    let prev = 0;
    for (let i = 0; i <= 20; i++) { const s = launchScale(i / 20); expect(s).toBeGreaterThanOrEqual(prev); prev = s; }
    const weak = makeRig(), strong = makeRig();
    makeDirector(weak).handle([simEv('launch', { intensity: 0.1, tick: 1 })]);
    makeDirector(strong).handle([simEv('launch', { intensity: 1, tick: 1 })]);
    expect(weak.host.started[0].params.gain).toBeLessThan(strong.host.started[0].params.gain);
  });
  it('the character says "hup" on some launches through the same pipeline', () => {
    let hup = 0;
    for (let seed = 1; seed <= 200; seed++) {
      const rig = makeRig(seed), dir = makeDirector(rig, { rng: mulberry32(seed * 7919) });
      dir.handle([simEv('launch', { intensity: 0.5, tick: 1 })]);
      hup += rig.legacy.filter(l => l.id === SFX.voiceHup).length;
    }
    expect(hup).toBeGreaterThan(80);                   // ≈ 60 %
    expect(hup).toBeLessThan(160);
  });
  it('the old boost whoosh is gone: boost never reaches the legacy player', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('launch', { tick: 1 }), simEv('boost', { tick: 1 })]);
    expect(rig.legacy.some(l => (l.id as string) === 'boost')).toBe(false);
  });
  it('launch layers are two voices of one event (cap 3), both counted by the pool', () => {
    const rig = makeRig();
    rig.audio.emit(AUDIO_EVENT.POGO_LAUNCH, { intensity: 1, power: true });
    expect(rig.audio.pool.countFor(AUDIO_EVENT.POGO_LAUNCH)).toBe(2);
  });
});
