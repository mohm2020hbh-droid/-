import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { MapPresentation } from '../../src/render/map/presentation';
import { LightRig } from '../../src/render/map/lights';
import { MapRuntime } from '../../src/map/MapRuntime';
import { BUILTIN_THEMES } from '../../src/map/MapTheme';
import { baseProfileOf } from '../../src/map/MapCamera';
import { baseDoc } from '../map/fixtures';
import type { GameRenderer, RenderFrame } from '../../src/render/GameRenderer';
import type { CameraProfile, MapEvent, RegionDef } from '../../src/map/schema';

interface Burst { kind: string; x: number; y: number; power: number; c1?: string }
function fake(doc = baseDoc('pres'), themeId = 'world_meadow') {
  const bursts: Burst[] = [], ambient: { kind: string; rate: number }[] = [], reached: string[] = [];
  const hemi = new THREE.HemisphereLight('#ffffff', '#444444', 0.5), sun = new THREE.DirectionalLight('#ffffff', 1.0), fog = new THREE.FogExp2('#aabbcc', 0.01);
  const rig = new LightRig(sun, hemi, fog);
  const emitters: { id: string; kind: 'vfx' | 'audio'; x: number; y: number; z: number; props: Record<string, unknown> }[] = [];
  const gr = {
    vfx: { burst: (kind: string, x: number, y: number, _z: number, _s: number, power: number, c1?: string) => bursts.push({ kind, x, y, power, c1 }), ambient: (_x: number, _y: number, _w: number, _h: number, kind: string, _c: string[], rate: number) => ambient.push({ kind, rate }) },
    mapScene: { positionOf: (id: string) => (id === 'crate' ? { x: 7, y: 3 } : null), setCheckpointReached: (id: string) => reached.push(id), emitters: () => emitters },
    lightRig: rig, rig: { focus: { x: 0, y: 0 }, halfW: 12, halfH: 7 },
  } as unknown as GameRenderer;
  const rt = new MapRuntime(doc);
  const theme = BUILTIN_THEMES[themeId];
  const pres = new MapPresentation(gr, rt, theme);
  return { pres, bursts, ambient, reached, rig, emitters, rt, doc, theme };
}
const ev = (type: string, id?: string, data?: unknown, x = 1, y = 2): MapEvent => ({ type, id, data, x, y, t: 0 } as unknown as MapEvent);
const frameOf = (mode = 'AIR'): RenderFrame => ({ state: { mode }, pose: { footX: 3, footY: 1 } } as unknown as RenderFrame);

describe('MapPresentation — camera zones', () => {
  it('blends the camera profile in on zone_enter and back out on zone_exit (event-driven, no polling)', () => {
    const doc = baseDoc('cam');
    doc.regions.push({ id: 'vista', type: 'camera', shape: { kind: 'box', x: 10, y: 5, w: 20, h: 20 }, params: { profile: { followDistance: 32, fov: 42 } as CameraProfile, blend: 0.6 } } as unknown as RegionDef);
    const { pres } = fake(doc);
    const base = baseProfileOf(doc.camera).followDistance;
    expect(pres.cameraProfile(1 / 60).followDistance).toBeCloseTo(base, 3);
    pres.handle([ev('zone_enter', 'vista')]);
    expect(pres.camera.activeZones()).toEqual(['vista']);
    let p = pres.cameraProfile(1 / 60);
    for (let i = 0; i < 120; i++) p = pres.cameraProfile(1 / 60);
    expect(p.followDistance).toBeCloseTo(32, 1); expect(p.fov).toBeCloseTo(42, 1);
    pres.handle([ev('zone_exit', 'vista')]);
    for (let i = 0; i < 240; i++) p = pres.cameraProfile(1 / 60);
    expect(p.followDistance).toBeCloseTo(base, 1);
    expect(pres.camera.activeZones()).toEqual([]);
  });

  it('camera effects (op: camera) push a modifier', () => {
    const { pres } = fake();
    pres.handle([ev('camera', 'shake_zoom', { profile: { zoom: 1.35 } })]);
    let p = pres.cameraProfile(1 / 60); for (let i = 0; i < 200; i++) p = pres.cameraProfile(1 / 60);
    expect(p.zoom).toBeCloseTo(1.35, 2);
  });
});

describe('MapPresentation — lighting zones', () => {
  it('lighting / fog events push rig overrides and leaving the zone pops them', () => {
    const doc = baseDoc('light');
    doc.regions.push({ id: 'cave', type: 'lighting', shape: { kind: 'box', x: 10, y: 5, w: 20, h: 20 }, enter: [{ op: 'lighting', ambient: 0.1, sun: 0.2 }] } as unknown as RegionDef);
    const { pres, rig } = fake(doc);
    const baseAmb = rig.baseState.ambient;
    pres.handle([ev('zone_enter', 'cave'), ev('lighting', 'cave', { ambient: 0.1, sun: 0.2 })]);
    expect(rig.activeZones()).toEqual(['cave']);
    for (let i = 0; i < 200; i++) rig.update(1 / 60);
    expect(rig.state.ambient).toBeLessThan(baseAmb * 0.5);
    pres.handle([ev('zone_exit', 'cave')]);
    expect(rig.activeZones()).toEqual([]);
    for (let i = 0; i < 400; i++) rig.update(1 / 60);
    expect(rig.state.ambient).toBeCloseTo(baseAmb, 2);
  });
  it('day / night retargets the base lights (throttled) without touching zone overrides', () => {
    const { pres, rig, theme } = fake(baseDoc('dn'), 'world_mystic');
    expect(theme.dayNight).toBeTruthy();
    const sky = { mesh: new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.ShaderMaterial({ uniforms: { uTop: { value: new THREE.Color() }, uMid: { value: new THREE.Color() }, uHor: { value: new THREE.Color() }, uSunCol: { value: new THREE.Color() } } })) };
    pres.applyDayNight(0, rig, sky as never);
    const a = rig.baseState.fogColor.getHex(), top = (sky.mesh.material as THREE.ShaderMaterial).uniforms.uTop.value.getHex();
    pres.applyDayNight(0.1, rig, sky as never);                                           // < 0.25 s: ignored
    expect(rig.baseState.fogColor.getHex()).toBe(a);
    pres.applyDayNight(theme.dayNight!.cycleSec * 0.5, rig, sky as never);
    expect(rig.baseState.fogColor.getHex()).not.toBe(a);
    expect((sky.mesh.material as THREE.ShaderMaterial).uniforms.uTop.value.getHex()).not.toBe(top);
  });
});

describe('MapPresentation — VFX hooks', () => {
  it('maps runtime events to bursts: checkpoint / finish / break / restore / teleport / boost / kill / water', () => {
    const doc = baseDoc('fx');
    doc.regions.push({ id: 'lake', type: 'water', shape: { kind: 'box', x: 10, y: -2, w: 10, h: 4 } } as unknown as RegionDef);
    const { pres, bursts, reached } = fake(doc);
    pres.handle([
      ev('checkpoint', 'cp0', undefined, 4, 5), ev('finish', 'finish', undefined, 50, 8), ev('break', 'crate'), ev('restore', 'crate'),
      ev('teleport', 'tp', { x: 30, y: 9 }, 5, 5), ev('boost_zone', 'bz'), ev('kill', 'spikes'), ev('zone_enter', 'lake', undefined, 12, -1),
    ]);
    const kinds = bursts.map(b => b.kind);
    for (const k of ['checkpoint', 'confetti', 'goal', 'break', 'dust', 'sparkle', 'ring', 'boost', 'hazard', 'splash']) expect(kinds, k).toContain(k);
    expect(reached).toEqual(['cp0']);
    const brk = bursts.find(b => b.kind === 'break')!;
    expect([brk.x, brk.y]).toEqual([7, 3]);                                                // at the entity's visual, not at the player
  });
  it('vfx effects: known ids burst as named, unknown ids fall back to sparkle; power is capped at 1', () => {
    const { pres, bursts } = fake();
    pres.handle([ev('vfx', 'a', { id: 'ice', burst: 5 }, 2, 2), ev('vfx', 'b', { id: 'definitely-not-a-fx' }, 3, 3)]);
    expect(bursts.map(b => b.kind)).toEqual(['ice', 'sparkle']);
    expect(bursts[0].power).toBeLessThanOrEqual(1);
  });
  it('theme ambient particles are emitted every update', () => {
    const { pres, ambient, theme } = fake();
    pres.update(1 / 60, 1, frameOf());
    expect(ambient.length).toBe((theme.particles ?? []).length);
    expect(ambient.length).toBeGreaterThan(0);
  });
  it('vfx emitters near the camera fire at their rate; far ones are silent', () => {
    const { pres, bursts, emitters } = fake();
    emitters.push({ id: 'near', kind: 'vfx', x: 2, y: 1, z: 0, props: { kind: 'lava', rate: 10, radius: 2 } });
    emitters.push({ id: 'far', kind: 'vfx', x: 500, y: 1, z: 0, props: { kind: 'lava', rate: 10, radius: 2 } });
    emitters.push({ id: 'snd', kind: 'audio', x: 1, y: 1, z: 0, props: { sound: 'wind' } });
    for (let i = 0; i < 60; i++) pres.update(1 / 60, i / 60, frameOf());               // one second
    const n = bursts.filter(b => b.kind === 'lava').length;
    expect(n).toBeGreaterThanOrEqual(9); expect(n).toBeLessThanOrEqual(11);
    expect(bursts.every(b => Math.abs(b.x) < 10)).toBe(true);
  });
  it('slide spray only while the sim reports sliding, rate-limited', () => {
    const { pres, bursts } = fake();
    pres.slideActive = true; pres.update(1 / 60, 0, frameOf('GROUND'));
    expect(bursts.filter(b => b.kind === 'ice')).toHaveLength(1);
    for (let i = 0; i < 3; i++) { pres.slideActive = true; pres.update(1 / 60, 0, frameOf('GROUND')); }
    expect(bursts.filter(b => b.kind === 'ice')).toHaveLength(1);                         // 0.07 s cooldown
    pres.update(1 / 60, 0, frameOf('GROUND'));                                           // not sliding any more
    expect(bursts.filter(b => b.kind === 'ice')).toHaveLength(1);
  });
});
