import { describe, expect, it } from 'vitest';
import { SFX } from '../../../src/audio/AudioEvents';
import { MapAudioCore, WebAudioMapBackend } from '../../../src/audio/MapAudio';
import type { SFXManager } from '../../../src/audio/SFXManager';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import type { MapEvent, RegionDef } from '../../../src/map/schema';
import { makeRig } from './helpers';

const BREAKS = ['break_1', 'break_2'];
const ev = (type: MapEvent['type'], id?: string, data?: unknown, x?: number, y?: number): MapEvent => ({ type, id, data, x, y, tick: 0 } as unknown as MapEvent);
const stubSfx = { play: () => {} } as unknown as SFXManager;

function setup(seed = 3) {
  const rig = makeRig(seed);
  const backend = new WebAudioMapBackend(rig.audio, stubSfx);
  const core = new MapAudioCore(backend, [] as RegionDef[], { maxVoices: 4, maxOneShotsPerFrame: 8 });
  const pos: Record<string, { x: number; y: number }> = {};
  core.positionOf = id => pos[id] ?? null;
  return { rig, core, pos };
}

describe('POGO_BREAK — Map V2 break events through the audio system', () => {
  it('a break event plays one of the two break variants on the SURFACE bus', () => {
    const { rig, core, pos } = setup();
    pos.p1 = { x: 3, y: 0 };
    core.update(0.016, { x: 0, y: 0 }, []);
    core.handle([ev('break', 'p1')]);
    expect(rig.host.started).toHaveLength(1);
    expect(rig.host.started[0].params.bus).toBe('SURFACE');
    expect(['break_1', 'break_2'].some(id => rig.audio.bank.has(id))).toBe(true);
    const d = rig.host.started[0].buffer.duration;
    expect(Math.abs(d - 0.52) < 0.05 || Math.abs(d - 1.86) < 0.05).toBe(true);
  });
  it('is positional: attenuated by distance, panned to the side of the platform, silent beyond the radius', () => {
    const { rig, core, pos } = setup();
    pos.near = { x: 8, y: 0 }; pos.left = { x: -8, y: 0 }; pos.far = { x: 500, y: 0 };
    core.update(0.016, { x: 0, y: 0 }, []);
    core.handle([ev('break', 'near')]);
    core.update(0.2, { x: 0, y: 0 }, []); rig.host.advance(0.2);
    core.handle([ev('break', 'left')]);
    core.update(0.2, { x: 0, y: 0 }, []); rig.host.advance(0.2);
    core.handle([ev('break', 'far')]);
    expect(rig.host.started).toHaveLength(2);            // far: silent
    const [near, left] = rig.host.started;
    expect(near.params.gain).toBeLessThan(1);
    expect(near.params.pan).toBeGreaterThan(0);
    expect(left.params.pan).toBeLessThan(0);
  });
  it('alternates between the two variants without an immediate repeat', () => {
    const { rig, core, pos } = setup(9);
    const seen: number[] = [];
    for (let i = 0; i < 12; i++) {
      pos[`p${i}`] = { x: 2, y: 0 };
      core.update(0.5, { x: 0, y: 0 }, []); rig.host.advance(0.5);
      core.handle([ev('break', `p${i}`)]);
      seen.push(rig.host.started[rig.host.started.length - 1].buffer.duration > 1 ? 2 : 1);
    }
    expect(rig.host.started).toHaveLength(12);
    for (let i = 1; i < seen.length; i++) expect(seen[i]).not.toBe(seen[i - 1]);
    expect(new Set(seen)).toEqual(new Set([1, 2]));
  });
  it('the manager adds its own 120 ms cooldown, so a flood of breaks in one frame cannot stack', () => {
    const { rig, core, pos } = setup();
    for (let i = 0; i < 5; i++) pos[`q${i}`] = { x: 1, y: 0 };
    core.update(0.016, { x: 0, y: 0 }, []);
    core.handle([0, 1, 2, 3, 4].map(i => ev('break', `q${i}`)));
    expect(rig.host.started).toHaveLength(1);
    expect(rig.audio.stats.skipped.cooldown).toBe(4);
  });
  it('break_2 is the heavier collapse (original volume 100 vs 90)', () => {
    const rig = makeRig(1);
    const g = new Map<string, number>();
    for (let i = 0; i < 40 && g.size < 2; i++) {
      rig.host.advance(1);
      const r = rig.audio.emit(AUDIO_EVENT.POGO_BREAK);
      g.set(r.variants[0], r.gain);
    }
    expect(g.get('break_1')!).toBeLessThanOrEqual(0.9 * 1.05 + 1e-9);
    expect(g.get('break_2')!).toBeLessThanOrEqual(1.05 + 1e-9);
    expect(g.get('break_1')!).toBeGreaterThan(0.8);
    expect(g.get('break_2')!).toBeGreaterThan(0.9);
  });
  it('without a position the break is heard at full level (no spatial gain applied twice)', () => {
    const { rig, core } = setup();
    core.update(0.016, { x: 0, y: 0 }, []);
    core.handle([ev('break', 'unknown')]);
    expect(rig.host.started[0].params.gain).toBeGreaterThan(0.8);
    expect(rig.host.started[0].params.pan).toBe(0);
  });
  it('restore → chime and checkpoint → checkpoint cue go through the same pipeline (legacy variants, UI/SFX buses)', () => {
    const { rig, core } = setup();
    core.update(0.016, { x: 0, y: 0 }, []);
    core.handle([ev('checkpoint', 'cp'), ev('restore', 'r1'), ev('teleport', 't')]);
    expect(rig.legacy.map(l => l.id).sort()).toEqual([SFX.checkpoint, SFX.chime, SFX.teleport].sort());
    expect(rig.audio.stats.perEvent.CHECKPOINT?.played).toBe(1);
    expect(rig.audio.stats.perEvent.CHIME?.played).toBe(1);
    expect(rig.audio.stats.perEvent.TELEPORT?.played).toBe(1);
  });
});
