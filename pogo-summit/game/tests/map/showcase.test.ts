// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { BUILTIN_MAPS } from '../../src/map/builtinMaps';
import { parseMap, serializeMap } from '../../src/map/MapLoader';
import { validateMap } from '../../src/map/MapValidator';
import { PackageReader, buildPackage } from '../../src/map/MapPackage';
import { MapRuntime } from '../../src/map/MapRuntime';
import { BUILTIN_THEMES } from '../../src/map/MapTheme';
import { usesVisualV2 } from '../../src/map/MapCompile';
import { PrefabRegistry } from '../../src/map/MapPrefab';
import { MapScene } from '../../src/render/map/scene';
import { setLang, t } from '../../src/ui/i18n';
import { boot } from './fixtures';
import type { MapDocument } from '../../src/map/schema';

const clone = (d: MapDocument): MapDocument => JSON.parse(JSON.stringify(d));
const showcase = (): MapDocument => clone(BUILTIN_MAPS.showcase_v2);
const THEMES = ['world_meadow', 'world_ice', 'world_volcanic', 'world_mystic'];

describe('showcase_v2 — the Visual-V2 showcase map (pure JSON)', () => {
  it('is registered, loads through the normal parser and round-trips', () => {
    expect(BUILTIN_MAPS.showcase_v2).toBeTruthy();
    const doc = parseMap(serializeMap(BUILTIN_MAPS.showcase_v2)).doc;
    expect(doc.manifest.id).toBe('showcase_v2');
    expect(doc.entities.length).toBeGreaterThan(80);
  });

  it('validates with no ERROR and no WARNING under both Android budgets', () => {
    for (const budget of ['android-mid', 'android-low']) {
      const r = validateMap(showcase(), { budget });
      expect(r.issues.filter(i => i.severity !== 'INFO').map(i => `${i.severity} ${i.code} ${i.message}`), budget).toEqual([]);
      expect(r.ok).toBe(true);
      expect(r.stats!.drawCalls + r.stats!.shadowCalls + r.stats!.backdropCalls).toBeLessThanOrEqual(r.stats ? (budget === 'android-low' ? 90 : 150) : 0);
      expect(r.stats!.triangles).toBeLessThan(budget === 'android-low' ? 120_000 : 250_000);
    }
  });

  it('uses the Visual-V2 renderer and shows every capability the brief asks for', () => {
    const doc = showcase();
    expect(usesVisualV2(doc, PrefabRegistry.forDoc(doc))).toBe(true);
    const prefabs = new Set(doc.entities.map(e => e.prefab));
    for (const p of ['moving_ledge', 'slope', 'ice_ledge', 'timed_ledge', 'breakable_ledge', 'crystal_spikes', 'rotating_blade', 'tree_cluster', 'rock_cluster', 'grass_patch', 'bush_cluster', 'cliff', 'waterfall', 'water', 'background_mountain', 'cloud', 'ancient_structure', 'crystal_cluster', 'vfx_emitter', 'audio_emitter'])
      expect(prefabs.has(p), `prefab ${p}`).toBe(true);
    expect(doc.checkpoints.length).toBeGreaterThanOrEqual(3);
    expect(doc.progress.routes.some(r => r.kind === 'secret')).toBe(true);
    const zones = (type: string) => doc.regions.filter(r => r.type === type).length;
    expect(zones('camera')).toBeGreaterThan(0); expect(zones('lighting') + zones('fog')).toBeGreaterThan(0); expect(zones('audio')).toBeGreaterThan(0); expect(zones('water')).toBeGreaterThan(0);
    const layers = new Set(doc.entities.map(e => e.visual?.renderLayer ?? e.type));
    expect(doc.entities.some(e => (e.position.z ?? 0) > 1)).toBe(true);        // foreground
    expect(doc.entities.some(e => (e.position.z ?? 0) < -100)).toBe(true);     // background
    void layers;
  });

  it('keeps collision separate from visuals: most entities are scenery without colliders', () => {
    const t0 = boot(showcase());
    const m = t0.rt.metrics();
    const doc = t0.rt.doc;
    const solidish = doc.entities.filter(e => ['platform', 'moving', 'slope', 'interactive', 'hazard'].includes(e.type)).length;
    expect(doc.entities.length - solidish).toBeGreaterThan(solidish);            // decor + background + emitters outnumber gameplay pieces
    expect(m.activeColliders).toBeLessThan(80);
  });

  it('every hint text exists in English and Arabic', () => {
    const keys = new Set<string>();
    const walk = (o: unknown): void => { if (Array.isArray(o)) o.forEach(walk); else if (o && typeof o === 'object') for (const [k, v] of Object.entries(o)) { if ((k === 'text' || k === 'textKey') && typeof v === 'string') keys.add(v); walk(v); } };
    walk(BUILTIN_MAPS.showcase_v2);
    expect(keys.size).toBeGreaterThan(0);
    for (const lang of ['en', 'ar'] as const) { setLang(lang); for (const k of keys) expect(t(k), `${lang}:${k}`).not.toBe(k); }
    setLang('en');
  });

  it('loads, validates and builds a scene under all four themes (the theme is the only thing that changes)', () => {
    for (const id of THEMES) {
      const doc = showcase(); doc.theme = { ref: id };
      expect(validateMap(doc).ok, id).toBe(true);
      const rt = new MapRuntime(doc); rt.prepareSpawn();
      const scene = new MapScene({ runtime: rt, theme: BUILTIN_THEMES[id] });
      const cam = new THREE.PerspectiveCamera(30, 16 / 9, 0.5, 900); cam.position.set(0, 5, 19); cam.lookAt(0, 3, 0);
      scene.beginFrame(); scene.update({ dt: 1 / 60, time: 1, camera: cam, focus: { x: 0, y: 3 }, tick: 1 });
      expect(scene.stats.chunksBuilt, id).toBeGreaterThan(0);
      expect(scene.stats.fallbackMeshes, id).toBe(0);
      scene.dispose();
    }
  });

  it('streams across its whole length: chunks build and dispose, residency stays small', () => {
    const doc = showcase();
    const rt = new MapRuntime(doc); rt.prepareSpawn();
    const scene = new MapScene({ runtime: rt, theme: BUILTIN_THEMES.world_meadow });
    const cam = new THREE.PerspectiveCamera(30, 16 / 9, 0.5, 900);
    let maxBuilt = 0, built = 0;
    for (let x = -4; x <= 270; x += 6) {
      const y = 3 + ((x + 4) / 274) * 68;
      rt.chunks.update(x, y);
      cam.position.set(x, y + 2, 19); cam.lookAt(x, y, 0);
      for (let k = 0; k < 6; k++) { scene.beginFrame(); scene.update({ dt: 1 / 60, time: 1, camera: cam, focus: { x, y }, tick: k }); }
      maxBuilt = Math.max(maxBuilt, scene.builtChunks().length);
    }
    built = scene.stats.chunksBuilt;
    expect(built).toBeGreaterThan(maxBuilt);
    expect(scene.log.disposed.length).toBeGreaterThan(0);
    expect(maxBuilt).toBeLessThanOrEqual(16);
    scene.dispose();
  });

  it('packs into a .pogomap and runs from the package', () => {
    const doc = showcase();
    const rd = PackageReader.open(buildPackage(doc).bytes);
    const rt = new MapRuntime(rd.core(), { source: rd });
    expect(rt.doc.manifest.id).toBe('showcase_v2');
    rt.prepareSpawn();
    expect(rt.metrics().loadedChunks).toBeGreaterThan(0);
    expect(rt.metrics().loadedChunks).toBeLessThan(10);                          // streaming from the package parses only the chunks near the spawn
  });
});
