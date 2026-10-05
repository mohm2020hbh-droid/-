import { describe, expect, it } from 'vitest';
import { SFX } from '../../../src/audio/AudioEvents';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import type { SimEventType } from '../../../src/sim/events';
import { makeDirector, makeRig, simEv } from './helpers';

const ALL: SimEventType[] = ['charge_start', 'launch', 'land', 'hard_impact', 'bounce', 'wall_hit', 'slide', 'boost_armed', 'boost', 'boost_pad', 'hazard', 'fall', 'respawn', 'goal'];

describe('PogoAudioDirector — every simulation event has a defined sound (or none, on purpose)', () => {
  it('maps each SimEventType', () => {
    const expected: Record<SimEventType, { events: string[] }> = {
      charge_start: { events: ['POGO_CHARGE'] },
      launch: { events: ['POGO_LAUNCH'] },
      land: { events: ['SURFACE_LAND'] },
      hard_impact: { events: ['HARD_IMPACT'] },
      bounce: { events: ['POGO_COLLISION'] },
      wall_hit: { events: ['POGO_COLLISION'] },
      slide: { events: [] },                       // the loop follows the slide state, not the entry event
      boost_armed: { events: ['BOOST_ARMED'] },
      boost: { events: [] },                       // part of the launch layers
      boost_pad: { events: ['BOOST_PAD'] },
      hazard: { events: ['HAZARD', 'VOICE_OUCH'] },
      fall: { events: ['FALL'] },
      respawn: { events: ['RESPAWN'] },
      goal: { events: ['FINISH', 'VOICE_YAY'] },
    };
    for (const type of ALL) {
      const rig = makeRig(3), dir = makeDirector(rig);
      dir.handle([simEv(type, { intensity: 0.9, material: 'stone', surface: 'normal', tick: 4 })]);
      const got = Object.entries(rig.audio.stats.perEvent).filter(([, v]) => v!.played > 0).map(([k]) => k).filter(k => k !== 'VOICE_HUP').sort();
      expect(got, type).toEqual(expected[type].events.slice().sort());
    }
  });
  it('an empty batch does nothing', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([]);
    expect(rig.audio.stats.emitted).toBe(0);
  });
  it('landings: the flavour follows the material (through the legacy adapter), the level follows the impact', () => {
    const cases: [string, string][] = [['grass', SFX.landSoft], ['wood', SFX.landWood], ['ice', SFX.landIce], ['goo', SFX.landGoo], ['metal', SFX.landWood], ['water', SFX.splash], ['lava', SFX.hazard], ['unknown_material', SFX.landSoft]];
    for (const [material, sfx] of cases) {
      const rig = makeRig(), dir = makeDirector(rig);
      dir.handle([simEv('land', { intensity: 0.5, material, surface: 'normal' })]);
      expect(rig.legacy, material).toHaveLength(1);
      expect(rig.legacy[0].id, material).toBe(sfx);
      expect(rig.legacy[0].o!.intensity).toBeCloseTo(0.25 + 0.5 * 0.75, 9);
    }
  });
  it('a landing on a bounce surface is a collision boing, with the landing impact as its impact speed', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('land', { intensity: 0.8, surface: 'bounce', material: 'rubber' })]);
    expect(rig.legacy).toHaveLength(0);
    expect(rig.host.started).toHaveLength(1);
    expect(rig.host.started[0].params.bus).toBe('PLAYER');
    expect(rig.audio.stats.perEvent.POGO_COLLISION?.played).toBe(1);
    // and Map V2's uppercase vocabulary works too
    const rig2 = makeRig(), dir2 = makeDirector(rig2);
    dir2.handle([simEv('land', { intensity: 0.8, surface: 'BOUNCE' })]);
    expect(rig2.audio.stats.perEvent.POGO_COLLISION?.played).toBe(1);
  });
  it('wall_hit: impact speed = the event intensity, positional at the contact point', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    rig.audio.setListener(0, 0, 10);
    dir.handle([simEv('wall_hit', { intensity: 0.9, x: 6, y: 1 })]);
    expect(rig.host.started).toHaveLength(1);
    expect(rig.host.started[0].params.pan).toBeGreaterThan(0);
    const weak = makeRig(), d2 = makeDirector(weak);
    d2.handle([simEv('wall_hit', { intensity: 0.01 })]);
    expect(weak.host.started).toHaveLength(0);                                // a brush against a wall is silent
  });
  it('a respawn cuts a sliding loop', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.update({ slideMode: true, grounded: true, sx: 30, sy: 0, groundId: 0, x: 0, y: 0 }, 10);
    expect(rig.host.live()).toHaveLength(1);
    dir.handle([simEv('respawn')]);
    expect(rig.host.live().filter(v => v.params.loop)).toHaveLength(0);
  });
  it('update() keeps the listener on the player', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.update({ slideMode: false, grounded: true, sx: 0, sy: 0, groundId: 0, x: 12.5, y: -3 }, 7.5);
    expect(rig.audio.listener).toEqual({ x: 12.5, y: -3, halfWidth: 7.5 });
    dir.update({ slideMode: false, grounded: true, sx: 0, sy: 0, groundId: 0, x: 13, y: -3 });
    expect(rig.audio.listener.halfWidth).toBe(7.5);                            // unchanged when none is given
  });
  it('is read-only with respect to the simulation: it never mutates the events or the state it is given', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    const ev = simEv('launch', { intensity: 0.7 }), frozen = Object.freeze({ ...ev });
    const st = Object.freeze({ slideMode: true, grounded: true, sx: 20, sy: 0, groundId: 0, x: 0, y: 0 });
    expect(() => { dir.handle([frozen]); dir.update(st, 10); }).not.toThrow();
  });
  it('UI sounds go through the same pipeline (UI bus events via the legacy adapter)', () => {
    const rig = makeRig();
    rig.audio.emit(AUDIO_EVENT.UI_CLICK);
    rig.audio.emit(AUDIO_EVENT.UI_STAR, { pitch: 1.24 });
    expect(rig.legacy.map(l => l.id)).toEqual([SFX.uiClick, SFX.uiStar]);
    expect(rig.legacy[1].o!.pitch).toBeCloseTo(1.24, 9);
  });
});
