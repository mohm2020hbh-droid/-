import { describe, expect, it } from 'vitest';
import { BusGraph } from '../../../src/audio/system/AudioBus';
import { AUDIO_EVENTS } from '../../../src/audio/system/AudioEvent';
import { AUDIO_EVENT, BUS_IDS, type BusId } from '../../../src/audio/system/types';
import { makeRig } from './helpers';

describe('BusGraph — effective gain is the product along the parent chain', () => {
  it('has the seven buses wired MASTER ← SFX ← (PLAYER, SURFACE), MASTER ← AMBIENT / UI / MUSIC', () => {
    const g = new BusGraph();
    expect(g.get('PLAYER').path()).toEqual(['PLAYER', 'SFX', 'MASTER']);
    expect(g.get('SURFACE').path()).toEqual(['SURFACE', 'SFX', 'MASTER']);
    for (const b of ['AMBIENT', 'UI', 'MUSIC', 'SFX'] as BusId[]) expect(g.get(b).path()).toEqual([b, 'MASTER']);
    expect(g.get('MASTER').path()).toEqual(['MASTER']);
    for (const id of BUS_IDS) expect(g.get(id)).toBeTruthy();
    expect(g.childrenOf('SFX').sort()).toEqual(['PLAYER', 'SURFACE']);
  });
  it('multiplies volumes down the chain', () => {
    const g = new BusGraph();
    g.set('MASTER', 0.5); g.set('SFX', 0.8); g.set('PLAYER', 0.25);
    expect(g.effective('PLAYER')).toBeCloseTo(0.5 * 0.8 * 0.25, 12);
    expect(g.effective('SURFACE')).toBeCloseTo(0.5 * 0.8, 12);
    expect(g.effective('AMBIENT')).toBeCloseTo(0.5, 12);
  });
  it('mute silences the bus and everything below it', () => {
    const g = new BusGraph();
    g.mute('SFX', true);
    expect(g.effective('PLAYER')).toBe(0); expect(g.effective('SURFACE')).toBe(0); expect(g.effective('SFX')).toBe(0);
    expect(g.effective('AMBIENT')).toBe(1);
    g.mute('SFX', false);
    expect(g.effective('PLAYER')).toBe(1);
  });
  it('clamps volumes to 0…1', () => {
    const g = new BusGraph();
    g.set('SFX', 5); expect(g.get('SFX').volume).toBe(1);
    g.set('SFX', -1); expect(g.get('SFX').volume).toBe(0);
  });
  it('notifies the platform for the bus and every descendant when a parent changes', () => {
    const g = new BusGraph(); const seen = new Map<BusId, number>();
    g.onChange = (b, e) => seen.set(b, e);
    g.set('SFX', 0.5);
    expect(seen.get('SFX')).toBeCloseTo(0.5, 12);
    expect(seen.get('PLAYER')).toBeCloseTo(0.5, 12); expect(seen.get('SURFACE')).toBeCloseTo(0.5, 12);
    expect(seen.has('AMBIENT')).toBe(false);
    g.set('MASTER', 0.5);
    expect(seen.get('PLAYER')).toBeCloseTo(0.25, 12); expect(seen.get('MUSIC')).toBeCloseTo(0.5, 12);
  });
  it('maps the four user sliders: master → MASTER, sfx → SFX and UI, ambient → AMBIENT, music → MUSIC', () => {
    const g = new BusGraph();
    g.applySettings({ master: 0.8, music: 0.55, sfx: 0.9, ambient: 0.6 });
    expect(g.get('MASTER').volume).toBe(0.8); expect(g.get('SFX').volume).toBe(0.9); expect(g.get('UI').volume).toBe(0.9);
    expect(g.get('AMBIENT').volume).toBe(0.6); expect(g.get('MUSIC').volume).toBe(0.55);
    expect(g.effective('PLAYER')).toBeCloseTo(0.8 * 0.9, 12);
    expect(g.effective('UI')).toBeCloseTo(0.8 * 0.9, 12);
  });
});

describe('the manager pushes bus gains to the platform', () => {
  it('apply(settings) reaches the host for every bus', () => {
    const { audio, host } = makeRig();
    audio.apply({ master: 0.5, music: 0.4, sfx: 0.6, ambient: 0.2 });
    expect(host.busGains.get('MASTER')).toBeCloseTo(0.5, 12);
    expect(host.busGains.get('SFX')).toBeCloseTo(0.5 * 0.6, 12);
    expect(host.busGains.get('PLAYER')).toBeCloseTo(0.5 * 0.6, 12);
    expect(host.busGains.get('AMBIENT')).toBeCloseTo(0.5 * 0.2, 12);
    expect(host.busGains.get('MUSIC')).toBeCloseTo(0.5 * 0.4, 12);
    expect(audio.settings.sfx).toBe(0.6);
  });
});

describe('event levels', () => {
  it('every event plays on the bus the catalogue gives it', () => {
    for (const id of [AUDIO_EVENT.POGO_CHARGE, AUDIO_EVENT.POGO_LAUNCH, AUDIO_EVENT.POGO_COLLISION, AUDIO_EVENT.POGO_BREAK, AUDIO_EVENT.TIME_EFFECT]) {
      const rig = makeRig();
      const r = rig.audio.emit(id, { intensity: 1, power: true });
      expect(r.played, id).toBe(true);
      for (const v of rig.host.started) expect(v.params.bus, id).toBe(AUDIO_EVENTS[id].bus);
    }
  });
  it('the caller\'s gain multiplies the event gain; spatial gain multiplies again; gains are linear', () => {
    const rig = makeRig(5);
    const base = rig.audio.emit(AUDIO_EVENT.POGO_CHARGE).gain;
    rig.host.advance(1);
    const half = rig.audio.emit(AUDIO_EVENT.POGO_CHARGE, { gain: 0.5 }).gain;
    expect(half).toBeCloseTo(base * 0.5, 9);
  });
  it('original call volumes keep their relation: charge = launch pop = 0.5, boom ≥ 0.9, time sting = 1, break_2 = 1', () => {
    const rig = makeRig(5);
    expect(rig.audio.emit(AUDIO_EVENT.POGO_CHARGE).gain).toBeCloseTo(0.5, 9);
    rig.host.advance(1);
    expect(rig.audio.emit(AUDIO_EVENT.TIME_EFFECT).gain).toBeCloseTo(1, 9);
  });
  it('gains below the audible floor are dropped, not played', () => {
    const rig = makeRig();
    const r = rig.audio.emit(AUDIO_EVENT.POGO_CHARGE, { gain: 0.001 });
    expect(r.played).toBe(false); expect(r.reason).toBe('inaudible');
    expect(rig.host.started).toHaveLength(0);
  });
  it('the time sting is truncated in gameplay and plays in full on request', () => {
    const a = makeRig();
    a.audio.emit(AUDIO_EVENT.TIME_EFFECT);
    expect(a.host.started[0].params.maxDuration).toBeCloseTo(3.0, 9);
    expect(a.host.started[0].params.fadeOut).toBeCloseTo(0.6, 9);
    const b = makeRig();
    b.audio.emit(AUDIO_EVENT.TIME_EFFECT, { full: true });
    expect(b.host.started[0].params.maxDuration).toBeUndefined();
    a.host.advance(3.1);
    expect(a.audio.pool.count).toBe(0);                    // truncated voice ended at 3 s
  });
});
