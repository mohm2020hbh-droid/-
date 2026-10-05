import { describe, expect, it } from 'vitest';
import { SFX } from '../../../src/audio/AudioEvents';
import { MapAudioCore, WebAudioMapBackend } from '../../../src/audio/MapAudio';
import type { SFXManager } from '../../../src/audio/SFXManager';
import { MapAudioBridge, SFX_TO_EVENT } from '../../../src/audio/system/MapAudioBridge';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import type { MapEvent, RegionDef } from '../../../src/map/schema';
import { makeDirector, makeRig, simEv } from './helpers';

const ev = (type: MapEvent['type'], id?: string, data?: unknown): MapEvent => ({ type, id, data, tick: 0 } as unknown as MapEvent);
const stubSfx = { play: () => { throw new Error('the old player must not be used for events that have an audio event'); } } as unknown as SFXManager;

describe('Map V2 → audio events', () => {
  it('every one-shot of MapAudioCore reaches the audio system through an event', () => {
    const rig = makeRig(), core = new MapAudioCore(new WebAudioMapBackend(rig.audio, stubSfx), [] as RegionDef[], { maxVoices: 4, maxOneShotsPerFrame: 20 });
    core.update(0.016, { x: 0, y: 0 }, []);
    const names = ['splash', 'checkpoint', 'break', 'teleport', 'chime', 'boostzone', 'lavapop'];
    for (const n of names) expect(core.play(n), n).toBe(true);
    const played = Object.keys(rig.audio.stats.perEvent).sort();
    expect(played).toEqual(['BOOST_ZONE', 'CHECKPOINT', 'CHIME', 'LAVA_POP', 'POGO_BREAK', 'SPLASH', 'TELEPORT'].sort());
    expect(Object.values(SFX_TO_EVENT).sort()).toEqual(played);                    // the table lists exactly these
  });
  it('split → TIME_EFFECT (UI bus, gameplay version: truncated, at the split gain)', () => {
    const rig = makeRig(), bridge = new MapAudioBridge(rig.audio);
    bridge.handle([ev('split', 's1')]);
    expect(rig.host.started).toHaveLength(1);
    const v = rig.host.started[0];
    expect(v.params.bus).toBe('UI');
    expect(v.params.maxDuration).toBeCloseTo(3, 9);
    expect(v.params.gain).toBeCloseTo(0.6, 9);
    expect(v.buffer.duration).toBeGreaterThan(6);                                  // the full 6.4 s recipe, cut at 3 s by the host
  });
  it('a burst of splits plays one sting (cooldown / single voice)', () => {
    const rig = makeRig(), bridge = new MapAudioBridge(rig.audio);
    bridge.handle([ev('split', 'a'), ev('split', 'b'), ev('split', 'c')]);
    expect(rig.host.started).toHaveLength(1);
  });
  it('map `finish` is not mapped (the sim `goal` already plays the finish sound — never twice)', () => {
    const rig = makeRig(), bridge = new MapAudioBridge(rig.audio), dir = makeDirector(rig);
    bridge.handle([ev('finish', 'f')]);
    expect(rig.audio.stats.emitted).toBe(0);
    dir.handle([simEv('goal')]);
    expect(rig.audio.stats.perEvent.FINISH?.played).toBe(1);
    expect(rig.audio.stats.perEvent.VOICE_YAY?.played).toBe(1);
  });
  it('hazard and finish map onto the same cues as before (legacy sounds, new buses)', () => {
    const rig = makeRig(), dir = makeDirector(rig);
    dir.handle([simEv('hazard'), simEv('goal')]);
    expect(rig.legacy.map(l => l.id).sort()).toEqual([SFX.goal, SFX.hazard, SFX.voiceOuch, SFX.voiceYay].sort());
  });
  it('events that need no map position still work without one', () => {
    const rig = makeRig(), bridge = new MapAudioBridge(rig.audio);
    expect(() => bridge.handle([ev('zone_enter', 'z'), ev('checkpoint'), ev('poi')])).not.toThrow();
    expect(rig.audio.stats.emitted).toBe(0);
  });
});

describe('the environment bed and zones feed the AMBIENT path, not the one-shot pool', () => {
  it('ambient layers are loops owned by MapAudioCore; they never occupy pool voices', () => {
    const rig = makeRig();
    const core = new MapAudioCore(new WebAudioMapBackend(rig.audio, stubSfx), [] as RegionDef[], { maxVoices: 4 });
    core.setBed({ bed: 'mystic', chimes: 0.7, drone: 0.5 });
    core.update(1, { x: 0, y: 0 }, []);
    expect(core.stats.ambientLayers).toEqual(['cave', 'chimes']);
    expect(rig.audio.pool.count).toBe(0);
  });
});

describe('Physics Core boundary: the audio system only reads', () => {
  it('uses no member of the physics core at runtime (only types)', async () => {
    const { readFileSync, readdirSync } = await import('node:fs');
    const { join } = await import('node:path');
    const dir = join(process.cwd(), 'src', 'audio', 'system');
    const files = readdirSync(dir, { recursive: true, withFileTypes: false }) as string[];
    for (const f of files.filter(x => x.endsWith('.ts'))) {
      const src = readFileSync(join(dir, f), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
      expect(src, f).not.toMatch(/from\s+['"][./]*\/sim\/core/);
      expect(src, f).not.toMatch(/from\s+['"][./]*\/sim\/(PogoPhysicsController|PhysicsWorld|PhysicsConfig)/);
      // only type imports from sim/ and map/
      for (const m of src.matchAll(/^import\s+(?!type)[^;]*from\s+['"]([^'"]+)['"]/gm)) expect(m[1], `${f}: ${m[0]}`).not.toMatch(/\/(sim|map)\//);
    }
  });
});
