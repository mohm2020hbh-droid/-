import { describe, expect, it } from 'vitest';
import { MapAudioCore, attenuation, panOf, type AudioBackend } from '../../src/audio/MapAudio';
import { SFX, type SfxId } from '../../src/audio/AudioEvents';
import type { Emitter } from '../../src/render/map/scene';
import type { MapEvent, RegionDef } from '../../src/map/schema';

class FakeBackend implements AudioBackend {
  ready = true;
  shots: { sound: SfxId; gain: number; pan: number }[] = [];
  loops = new Map<string, { sound: string; gain: number; pan: number }>();
  started: string[] = []; stopped: string[] = [];
  oneShot(sound: SfxId, o: { gain: number; pan: number }): void { this.shots.push({ sound, gain: o.gain, pan: o.pan }); }
  loopStart(sound: string, key: string): void { this.loops.set(key, { sound, gain: 0, pan: 0 }); this.started.push(key); }
  loopSet(key: string, gain: number, pan: number): void { const l = this.loops.get(key); if (l) { l.gain = gain; l.pan = pan; } }
  loopStop(key: string): void { this.loops.delete(key); this.stopped.push(key); }
}

const region = (id: string, type: RegionDef['type'] = 'audio'): RegionDef => ({ id, type, shape: { kind: 'rect', x: 0, y: 0, w: 10, h: 10 } } as unknown as RegionDef);
const ev = (type: MapEvent['type'], id?: string, data?: unknown, x?: number, y?: number): MapEvent => ({ type, id, data, x, y, t: 0 } as unknown as MapEvent);
const emitter = (id: string, x: number, y: number, props: Record<string, unknown> = {}): Emitter => ({ id, kind: 'audio', x, y, z: 0, props: { sound: 'wind', radius: 20, volume: 1, ...props } });
const mk = (maxVoices = 4, maxOneShotsPerFrame = 4) => { const b = new FakeBackend(); const c = new MapAudioCore(b, [region('cave'), region('lake', 'water')], { maxVoices, maxOneShotsPerFrame }); return { b, c }; };

describe('distance attenuation & pan', () => {
  it('is 1 at the source, 0 at the radius, monotonic in between', () => {
    expect(attenuation(0, 20)).toBe(1);
    expect(attenuation(20, 20)).toBe(0);
    expect(attenuation(30, 20)).toBe(0);
    let prev = 1;
    for (let d = 1; d < 20; d++) { const a = attenuation(d, 20); expect(a).toBeLessThan(prev); prev = a; }
    expect(attenuation(5, 0)).toBe(0);
  });
  it('pans toward the side of the source and is clamped', () => {
    expect(panOf(-10, 20)).toBeLessThan(0);
    expect(panOf(10, 20)).toBeGreaterThan(0);
    expect(panOf(1e6, 20)).toBeLessThanOrEqual(0.85);
    expect(panOf(0, 20)).toBe(0);
  });
});

describe('one-shots', () => {
  it('maps map events to sounds', () => {
    const { b, c } = mk();
    c.update(0.016, { x: 0, y: 0 }, []);
    c.handle([ev('checkpoint', 'cp1'), ev('teleport', 'tp'), ev('boost_zone', 'bz')]);
    expect(b.shots.map(s => s.sound)).toEqual([SFX.checkpoint, SFX.teleport, SFX.boostZone]);
  });
  it('plays a positional break sound attenuated by distance, and drops it when out of range', () => {
    const { b, c } = mk();
    c.positionOf = id => (id === 'near' ? { x: 5, y: 0 } : id === 'far' ? { x: 500, y: 0 } : null);
    c.update(0.016, { x: 0, y: 0 }, []);
    c.handle([ev('break', 'near'), ev('break', 'far')]);
    expect(b.shots).toHaveLength(1);
    expect(b.shots[0].sound).toBe(SFX.breakPlatform);
    expect(b.shots[0].gain).toBeLessThan(1);
    expect(b.shots[0].gain).toBeGreaterThan(0);
    expect(b.shots[0].pan).toBeGreaterThan(0);
  });
  it('enforces per-sound cooldowns (and they expire)', () => {
    const { b, c } = mk();
    c.update(0.016, { x: 0, y: 0 }, []);
    c.handle([ev('checkpoint', 'a')]); c.handle([ev('checkpoint', 'a')]);
    expect(b.shots).toHaveLength(1);
    expect(c.stats.suppressedByCooldown).toBe(1);
    c.update(0.5, { x: 0, y: 0 }, []);
    c.handle([ev('checkpoint', 'a')]);
    expect(b.shots).toHaveLength(2);
  });
  it('caps simultaneous one-shots per frame', () => {
    const { b, c } = mk(4, 2);
    c.update(0.016, { x: 0, y: 0 }, []);
    c.handle([ev('checkpoint', 'a'), ev('teleport', 'b'), ev('boost_zone', 'c')]);
    expect(b.shots).toHaveLength(2);
    expect(c.stats.suppressedByBudget).toBe(1);
    c.update(0.016, { x: 0, y: 0 }, []);                      // the next frame the budget is back
    c.handle([ev('boost_zone', 'c')]);
    expect(b.shots).toHaveLength(3);
  });
  it('water zones splash on enter (louder) and exit', () => {
    const { b, c } = mk();
    c.update(0.016, { x: 0, y: 0 }, []);
    c.handle([ev('zone_enter', 'lake', undefined, 1, 1)]);
    c.update(1, { x: 0, y: 0 }, []);
    c.handle([ev('zone_exit', 'lake', undefined, 1, 1)]);
    expect(b.shots.map(s => s.sound)).toEqual([SFX.splash, SFX.splash]);
    expect(b.shots[0].gain).toBeGreaterThan(b.shots[1].gain);
  });
  it('ignores unknown sound names', () => { const { b, c } = mk(); expect(c.play('nope')).toBe(false); expect(b.shots).toHaveLength(0); });
});

describe('zone ambience layers', () => {
  it('fades a layer in on zone audio effects and out + stops it after the zone is left', () => {
    const { b, c } = mk();
    c.update(0.016, { x: 0, y: 0 }, []);
    c.handle([ev('audio', 'cave', { op: 'audio', id: 'cave', volume: 0.8 })]);
    for (let i = 0; i < 120; i++) c.update(1 / 60, { x: 0, y: 0 }, []);
    const key = 'layer:cave';
    expect(b.loops.get(key)?.gain ?? 0).toBeGreaterThan(0.6);
    expect(c.stats.ambientLayers).toEqual(['cave']);
    c.handle([ev('zone_exit', 'cave', undefined, 0, 0)]);
    for (let i = 0; i < 600; i++) c.update(1 / 60, { x: 0, y: 0 }, []);
    expect(b.loops.has(key)).toBe(false);
    expect(b.stopped).toContain(key);
    expect(c.stats.ambientLayers).toEqual([]);
  });
  it('theme beds start base layers', () => {
    const { c } = mk();
    c.setBed({ bed: 'mystic', chimes: 0.7, drone: 0.5 });
    c.update(1, { x: 0, y: 0 }, []);
    expect(c.stats.ambientLayers).toEqual(['cave', 'chimes']);
  });
});

describe('positional emitter voices', () => {
  it('only the nearest `maxVoices` emitters sound, quieter with distance, and slots are reused', () => {
    const { b, c } = mk(2);
    const es = [emitter('a', 3, 0), emitter('b', 9, 0), emitter('c', 15, 0), emitter('far', 400, 0)];
    c.update(0.016, { x: 0, y: 0 }, es);
    expect([...b.loops.keys()].sort()).toEqual(['emitter:a', 'emitter:b']);
    expect(b.loops.get('emitter:a')!.gain).toBeGreaterThan(b.loops.get('emitter:b')!.gain);
    // walk toward c: it takes over the farthest voice
    c.update(0.016, { x: 14, y: 0 }, es);
    expect([...b.loops.keys()].sort()).toEqual(['emitter:b', 'emitter:c']);
    expect(c.stats.voiceSwaps).toBeGreaterThan(0);
    expect(c.stats.activeLoops).toBe(2);
  });
  it('pans by the side of the screen and silences emitters beyond their radius', () => {
    const { b, c } = mk(4);
    c.update(0.016, { x: 0, y: 0 }, [emitter('l', -8, 0), emitter('r', 8, 0), emitter('x', 25, 0)]);
    expect(b.loops.get('emitter:l')!.pan).toBeLessThan(0);
    expect(b.loops.get('emitter:r')!.pan).toBeGreaterThan(0);
    expect(b.loops.has('emitter:x')).toBe(false);
  });
  it('layers use up voice slots first, and dispose stops everything', () => {
    const { b, c } = mk(2);
    c.setLayer('wind', 1); c.setLayer('water', 1);
    c.update(0.5, { x: 0, y: 0 }, [emitter('a', 1, 0)]);
    expect([...b.loops.keys()].filter(k => k.startsWith('emitter:'))).toHaveLength(0);
    c.dispose();
    expect(b.loops.size).toBe(0);
  });
});
