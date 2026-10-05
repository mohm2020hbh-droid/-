import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { PROCEDURAL_TEXTURES, textureBytes } from '../../src/map/MapAssets';
import { BUILTIN_THEMES, backdropOf, lightingOf, themeMaterials } from '../../src/map/MapTheme';
import { Backdrop } from '../../src/render/map/backdrop';
import { MaterialLibrary } from '../../src/render/map/materials';
import { LightRig, SHADOW_MAP, effectiveShadowMap } from '../../src/render/map/lights';
import { TextureLibrary, proceduralPixels } from '../../src/render/map/textures';
import type { LightingProfile } from '../../src/map/schema';

const THEMES = ['world_meadow', 'world_ice', 'world_volcanic', 'world_mystic'];

describe('procedural textures', () => {
  it('are deterministic, correctly sized and opaque-where-expected', () => {
    for (const name of Object.keys(PROCEDURAL_TEXTURES)) {
      const info = PROCEDURAL_TEXTURES[name], a = proceduralPixels(name), b = proceduralPixels(name);
      expect(a.length, name).toBe(info.size * info.size * 4);
      expect(Buffer.from(a).equals(Buffer.from(b)), `${name} deterministic`).toBe(true);
    }
  });
  it('normal maps point mostly "up" (z-dominant, tangent-space)', () => {
    const px = proceduralPixels('rock_n');
    let zSum = 0; const n = px.length / 4;
    for (let i = 0; i < n; i++) zSum += px[i * 4 + 2] / 255;
    expect(zSum / n).toBeGreaterThan(0.75);
  });
  it('TextureLibrary shares one texture per reference + colour space and accounts GPU bytes', () => {
    const lib = new TextureLibrary();
    const a = lib.get('proc:rock', true), b = lib.get('proc:rock', true), c = lib.get('proc:rock', false);
    expect(a).toBe(b); expect(c).not.toBe(a);
    expect(lib.count).toBe(2);
    expect(lib.bytes).toBe(textureBytes(PROCEDURAL_TEXTURES.rock.size) * 2);
    lib.dispose();
    expect(lib.count).toBe(0); expect(lib.bytes).toBe(0);
  });
});

describe('MaterialLibrary', () => {
  const mk = (id = 'world_meadow') => {
    const defs = themeMaterials(BUILTIN_THEMES[id]);
    const tex = new TextureLibrary();
    return { lib: new MaterialLibrary(tex, k => defs[k]), tex, defs };
  };
  it('caches per material × variant (shared instances), variants differ', () => {
    const { lib, defs } = mk();
    const id = Object.keys(defs)[0];
    const a = lib.get(id), b = lib.get(id), ghost = lib.get(id, { ghost: true }), tinted = lib.get(id, { tint: '#ff0000' });
    expect(a).toBe(b); expect(ghost).not.toBe(a); expect(tinted).not.toBe(a);
    expect(ghost.transparent).toBe(true); expect(ghost.opacity).toBeLessThanOrEqual(0.28);
    expect(tinted.color.getHex()).toBe(0xff0000);
  });
  it('unknown ids fall back to one shared neutral material instead of throwing', () => {
    const { lib } = mk();
    expect(lib.get('nope')).toBe(lib.get('another-nope'));
  });
  it('every theme slot builds; textured slots get maps (and normal maps can be disabled)', () => {
    for (const th of THEMES) {
      const { lib, defs } = mk(th);
      for (const id of Object.keys(defs)) { const m = lib.get(id); expect(m, `${th}/${id}`).toBeInstanceOf(THREE.MeshStandardMaterial); expect(m.roughness).toBeGreaterThanOrEqual(0); expect(m.roughness).toBeLessThanOrEqual(1); }
      const withMaps = Object.keys(defs).filter(id => lib.get(id).map);
      expect(withMaps.length, th).toBeGreaterThan(0);
      const lo = mk(th); lo.lib.normalMaps = false;
      for (const id of Object.keys(lo.defs)) expect(lo.lib.get(id).normalMap, `${th}/${id}`).toBeFalsy();
    }
  });
  it('surface types come through describe() (physics bridge reads them, the renderer never decides physics)', () => {
    const { lib, defs } = mk('world_ice');
    const types = new Set(Object.keys(defs).map(id => lib.describe(id)?.surfaceType));
    expect(types.has('ICE')).toBe(true);
  });
});

describe('LightRig', () => {
  const profile = (over: Partial<LightingProfile> = {}): LightingProfile => ({ ...lightingOf(BUILTIN_THEMES.world_meadow), ...over });
  const mk = () => {
    const hemi = new THREE.HemisphereLight('#fff', '#444', 0.4), sun = new THREE.DirectionalLight('#fff', 1), fog = new THREE.FogExp2('#aabbcc', 0.01);
    return { rig: new LightRig(sun, hemi, fog), sun, hemi, fog };
  };
  it('applies the profile at once and keeps the light count tiny (1 sun + 1 hemisphere)', () => {
    const { rig, sun, hemi, fog } = mk();
    rig.apply(profile({ sunIntensity: 2.2, ambientIntensity: 0.9, fogDensity: 0.02 }), { shadows: true, shadowMap: 1024 });
    expect(sun.intensity).toBeCloseTo(2.2, 5); expect(hemi.intensity).toBeCloseTo(0.9, 5); expect(fog.density).toBeCloseTo(0.02, 5);
    const scene = new THREE.Scene(); scene.add(sun, hemi);
    let lights = 0; scene.traverse(o => { if ((o as THREE.Light).isLight) lights++; });
    expect(lights).toBe(2);
  });
  it('shadow quality is capped by the device, never raised', () => {
    expect(effectiveShadowMap('high', { shadows: true, shadowMap: 1024 })).toBe(1024);
    expect(effectiveShadowMap('low', { shadows: true, shadowMap: 2048 })).toBe(SHADOW_MAP.low);
    expect(effectiveShadowMap('high', { shadows: false, shadowMap: 2048 })).toBe(0);
    expect(effectiveShadowMap('off', { shadows: true, shadowMap: 2048 })).toBe(0);
    expect(effectiveShadowMap('blob', { shadows: true, shadowMap: 2048 })).toBe(0);
  });
  it('zones override in push order, blend smoothly and pop back to the base', () => {
    const { rig, sun } = mk();
    rig.apply(profile({ sunIntensity: 1.5 }), { shadows: true, shadowMap: 1024 });
    rig.pushZone('dark', { sun: 0.2 });
    expect(rig.target().sun).toBe(0.2);
    rig.update(1 / 60);
    expect(sun.intensity).toBeGreaterThan(0.2); expect(sun.intensity).toBeLessThan(1.5);          // blending, not snapping
    for (let i = 0; i < 300; i++) rig.update(1 / 60);
    expect(sun.intensity).toBeCloseTo(0.2, 2);
    rig.pushZone('dark', { sun: 0.4 });                                                          // same id replaces
    expect(rig.activeZones()).toEqual(['dark']);
    rig.popZone('dark');
    for (let i = 0; i < 300; i++) rig.update(1 / 60);
    expect(sun.intensity).toBeCloseTo(1.5, 2);
  });
});

describe('Backdrop (distant layers)', () => {
  const mk = (themeId: string, density = 1) => {
    const th = BUILTIN_THEMES[themeId], lp = lightingOf(th);
    const tex = new TextureLibrary(), defs = themeMaterials(th), lib = new MaterialLibrary(tex, k => defs[k]);
    return new Backdrop(backdropOf(th), { bounds: { minX: 0, maxX: 300, minY: -30, maxY: 100 }, sunDir: new THREE.Vector3(...lp.sunDirection).normalize(), fogColor: th.fog.color, cloudMaterial: () => lib.get('cloud'), density });
  };
  it('every V2 theme has a rich backdrop at a small, bounded cost', () => {
    for (const th of THEMES) {
      const b = mk(th);
      expect(b.root.children.length, th).toBeGreaterThanOrEqual(3);          // several depth layers
      expect(b.drawCalls, th).toBeLessThan(40);
      expect(b.triangles, th).toBeLessThan(25000);
      b.dispose();
    }
  });
  it('follows the focus with parallax (far layers move less than near ones) and ticks without error', () => {
    const b = mk('world_meadow');
    b.update(1 / 60, 0, { x: 0, y: 10 });
    const pos = () => b.root.children.map(c => c.position.x);
    const before = pos();
    b.update(1 / 60, 1, { x: 100, y: 10 });
    const after = pos();
    expect(after.some((x, i) => Math.abs(x - before[i]) > 0.01)).toBe(true);
    b.dispose();
  });
  it('density thins decoration but keeps the layers', () => {
    const hi = mk('world_meadow', 1), lo = mk('world_meadow', 0.3);
    expect(lo.root.children.length).toBe(hi.root.children.length);
    expect(lo.triangles).toBeLessThanOrEqual(hi.triangles);
    hi.dispose(); lo.dispose();
  });
});
