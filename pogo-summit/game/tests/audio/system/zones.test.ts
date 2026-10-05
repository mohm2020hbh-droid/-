import { describe, expect, it } from 'vitest';
import { MapAudioCore, type AudioBackend } from '../../../src/audio/MapAudio';
import { AudioZoneSet } from '../../../src/audio/system/AudioZone';
import type { MapEvent, RegionDef } from '../../../src/map/schema';

class Backend implements AudioBackend {
  ready = true; loops = new Map<string, { sound: string; gain: number }>(); stopped: string[] = [];
  oneShot(): void {}
  loopStart(sound: string, key: string): void { this.loops.set(key, { sound, gain: 0 }); }
  loopSet(key: string, gain: number): void { const l = this.loops.get(key); if (l) l.gain = gain; }
  loopStop(key: string): void { this.loops.delete(key); this.stopped.push(key); }
}
const region = (id: string): RegionDef => ({ id, type: 'audio', shape: { kind: 'rect', x: 0, y: 0, w: 10, h: 10 } } as unknown as RegionDef);
const ev = (type: MapEvent['type'], id?: string, data?: unknown): MapEvent => ({ type, id, data, tick: 0 } as unknown as MapEvent);
const run = (c: MapAudioCore, sec: number) => { for (let i = 0; i < Math.round(sec * 60); i++) c.update(1 / 60, { x: 0, y: 0 }, []); };

describe('AudioZoneSet', () => {
  it('a layer\'s target is the maximum over the active zones', () => {
    const z = new AudioZoneSet();
    z.enter('a', [{ name: 'wind', volume: 0.4 }]);
    z.enter('b', [{ name: 'wind', volume: 0.9 }, { name: 'cave', volume: 0.3 }]);
    expect(z.targets().get('wind')).toBe(0.9);
    expect(z.targets().get('cave')).toBe(0.3);
    z.exit('b');
    expect(z.targets().get('wind')).toBe(0.4);
    expect(z.targets().has('cave')).toBe(false);
    z.exit('a');
    expect(z.targets().size).toBe(0);
  });
  it('enter / exit are idempotent; define() extends a zone\'s layers; a layer update replaces its volume', () => {
    const z = new AudioZoneSet();
    z.enter('a', [{ name: 'wind', volume: 0.4 }]); z.enter('a', [{ name: 'wind', volume: 0.6 }, { name: 'water', volume: 0.2 }]);
    expect(z.activeIds()).toEqual(['a']);
    expect(z.targets().get('wind')).toBe(0.6); expect(z.targets().get('water')).toBe(0.2);
    z.exit('a'); z.exit('a'); z.exit('never');
    expect(z.activeIds()).toEqual([]);
  });
  it('the theme bed is a permanent zone that survives exits and combines by max', () => {
    const z = new AudioZoneSet();
    z.setBed([{ name: 'chimes', volume: 0.42 }]);
    z.enter('cave', [{ name: 'chimes', volume: 0.1 }, { name: 'cave', volume: 0.8 }]);
    expect(z.targets().get('chimes')).toBe(0.42);
    z.exit('cave');
    expect(z.targets().get('chimes')).toBe(0.42);
    z.setBed([{ name: 'lava', volume: 0.35 }]);               // a new bed replaces the old one
    expect(z.targets().has('chimes')).toBe(false);
    expect(z.targets().get('lava')).toBe(0.35);
  });
  it('dominant() is the highest priority (ties: the latest) and clear() forgets everything', () => {
    const z = new AudioZoneSet();
    z.enter('low', [], { priority: 1 }); z.enter('high', [], { priority: 5, reverb: 'cave' }); z.enter('mid', [], { priority: 3 });
    expect(z.dominant()?.id).toBe('high'); expect(z.dominant()?.reverb).toBe('cave');
    z.clear();
    expect(z.dominant()).toBeUndefined(); expect(z.activeIds()).toEqual([]);
  });
});

describe('MapAudioCore uses zones: overlapping zones cannot silence each other', () => {
  it('leaving one of two zones that want the same layer keeps it; leaving both fades it out and stops it', () => {
    const b = new Backend(), c = new MapAudioCore(b, [region('z1'), region('z2')], { maxVoices: 4 });
    c.handle([ev('audio', 'z1', { op: 'audio', id: 'wind', volume: 0.5 }), ev('audio', 'z2', { op: 'audio', id: 'wind', volume: 0.8 })]);
    run(c, 3);
    expect(b.loops.get('layer:wind')!.gain).toBeGreaterThan(0.7);                         // max(0.5, 0.8)
    c.handle([ev('zone_exit', 'z2')]);
    run(c, 4);
    expect(b.loops.has('layer:wind')).toBe(true);                                         // z1 still wants it
    expect(b.loops.get('layer:wind')!.gain).toBeCloseTo(0.5, 1);
    c.handle([ev('zone_exit', 'z1')]);
    run(c, 12);
    expect(b.loops.has('layer:wind')).toBe(false);
    expect(b.stopped).toContain('layer:wind');
  });
  it('the theme bed stays through zone changes', () => {
    const b = new Backend(), c = new MapAudioCore(b, [region('z1')], { maxVoices: 4 });
    c.setBed({ bed: 'mystic', chimes: 1, drone: 0 });
    c.handle([ev('audio', 'z1', { op: 'audio', id: 'chimes', volume: 0.2 })]);
    c.handle([ev('zone_exit', 'z1')]);
    run(c, 4);
    expect(b.loops.get('layer:chimes')!.gain).toBeCloseTo(0.6, 1);
  });
  it('dispose clears the zones', () => {
    const b = new Backend(), c = new MapAudioCore(b, [region('z1')], { maxVoices: 4 });
    c.handle([ev('audio', 'z1', { op: 'audio', id: 'wind', volume: 0.5 })]);
    run(c, 1);
    c.dispose();
    expect(b.loops.size).toBe(0);
    expect(c.zones.activeIds()).toEqual([]);
  });
});
