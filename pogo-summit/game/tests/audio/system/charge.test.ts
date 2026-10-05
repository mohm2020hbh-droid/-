import { describe, expect, it } from 'vitest';
import { AUDIO_CONFIG } from '../../../src/audio/system/audioConfig';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import { makeDirector, makeRig, simEv } from './helpers';

const CH = AUDIO_EVENT.POGO_CHARGE;

describe('POGO_CHARGE — one click on the start of the charge, never a continuous sound', () => {
  it('charge_start plays exactly one short click on the PLAYER bus', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('charge_start', { intensity: 0 })]);
    expect(rig.host.started).toHaveLength(1);
    const v = rig.host.started[0];
    expect(v.params.bus).toBe('PLAYER');
    expect(v.params.loop).toBe(false);
    expect(v.buffer.duration).toBeCloseTo(0.105, 2);
    expect(v.params.gain).toBeCloseTo(0.5, 9);                          // ORIGINAL volume 50
    expect(v.params.pitch).toBeGreaterThanOrEqual(0.9);
    expect(v.params.pitch).toBeLessThanOrEqual(1.0);
  });
  it('single instance (handle gating): a second charge_start while it still sounds is ignored', () => {
    const rig = makeRig();
    expect(rig.audio.emit(CH).played).toBe(true);
    rig.host.advance(0.03);
    const r = rig.audio.emit(CH);
    expect(r.played).toBe(false); expect(r.reason).toBe('voice-limit');
    expect(rig.host.started).toHaveLength(1);
  });
  it('after the click has ended, the next charge plays again', () => {
    const rig = makeRig();
    rig.audio.emit(CH);
    rig.host.advance(0.2);
    expect(rig.audio.pool.countFor(CH)).toBe(0);
    expect(rig.audio.emit(CH).played).toBe(true);
    expect(rig.host.started).toHaveLength(2);
  });
  it('the launch cuts a charge click that is still sounding (fade, then the handle is free)', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('charge_start', { intensity: 0 })]);
    rig.host.advance(0.02);
    dir.handle([simEv('launch', { intensity: 0.5, tick: 9 })]);
    const click = rig.host.started[0];
    expect(click.stopped).toBe(true);
    expect(click.stopFade).toBeCloseTo(AUDIO_CONFIG.charge.cutFadeSec, 9);
    expect(rig.audio.pool.countFor(CH)).toBe(0);
  });
  it('input and motion alone make no sound: only events do', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    // 600 frames of a pogo that is held, aimed, tilted and moving — no sim events, not sliding
    for (let i = 0; i < 600; i++) {
      dir.update({ slideMode: false, grounded: i % 2 === 0, sx: 0, sy: 0, groundId: 0, x: i * 0.05, y: Math.sin(i / 10) }, 10);
      rig.host.advance(1 / 60);
    }
    dir.handle([]);
    expect(rig.host.started).toHaveLength(0);
    expect(rig.legacy).toHaveLength(0);
  });
  it('a slide *entry* event by itself starts nothing (the loop follows the state, not the event)', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('slide', { intensity: 0.3, surface: 'slippery', material: 'ice' })]);
    expect(rig.host.started).toHaveLength(0);
    expect(rig.legacy).toHaveLength(0);
  });
  it('does nothing before the audio is unlocked, and does not play late once it is', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    rig.host.ready = false;
    dir.handle([simEv('charge_start', { intensity: 0 })]);
    expect(rig.host.started).toHaveLength(0);
    rig.host.ready = true;
    rig.host.advance(1);
    expect(rig.host.started).toHaveLength(0);          // events are not queued
    expect(rig.audio.stats.skipped['not-ready']).toBe(1);
  });
});
