import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { BUILTIN_MAPS } from '../../src/map/builtinMaps';
import { PrefabRegistry } from '../../src/map/MapPrefab';
import { BUILTIN_THEMES, themeMaterials } from '../../src/map/MapTheme';
import { collectMaterialUse, themeMaterialUse, WARM_SCAN_LIMIT } from '../../src/map/MapWarm';
import { MapRuntime } from '../../src/map/MapRuntime';
import { MapScene } from '../../src/render/map/scene';
import { baseDoc, stone } from './fixtures';

const THEME = BUILTIN_THEMES.world_meadow;

describe('MapWarm — which material × geometry kinds a map draws (pre-compiled at load)', () => {
  it('lists plain meshes for merged/single entities and instanced kinds for scatter, deduplicated', () => {
    const doc = BUILTIN_MAPS.showcase_v2;
    const uses = collectMaterialUse(doc, PrefabRegistry.forDoc(doc), THEME, new Map())!;
    expect(uses.length).toBeGreaterThan(10);
    expect(new Set(uses.map(u => `${u.material}|${u.kind}|${u.foreground}|${u.ghost}`)).size).toBe(uses.length);
    const kinds = new Set(uses.map(u => u.kind));
    expect(kinds.has('mesh')).toBe(true); expect(kinds.has('instanced-tinted')).toBe(true);
    expect(uses.some(u => u.foreground)).toBe(true);                         // foreground grass dithers: its own program
    expect(uses.some(u => u.ghost)).toBe(true);                              // timed / breakable ledges ghost
  });
  it('a tiny map without scatter or gates needs only plain meshes', () => {
    const doc = baseDoc('warm_small', d => { d.entities.push(stone('s1', 20, 2)); });
    const uses = collectMaterialUse(doc, PrefabRegistry.forDoc(doc), THEME, new Map())!;
    expect(uses.every(u => u.kind === 'mesh' && !u.ghost)).toBe(true);
  });
  it('documents without an entity list (packages) or beyond the scan limit fall back to the theme', () => {
    const empty = baseDoc('e', d => { d.entities = []; });
    expect(collectMaterialUse(empty, PrefabRegistry.forDoc(empty), THEME, new Map())).toBeNull();
    expect(WARM_SCAN_LIMIT).toBeGreaterThan(1000);
    const fb = themeMaterialUse(themeMaterials(THEME));
    expect(fb.length).toBe(Object.keys(themeMaterials(THEME)).length * 3);
  });
  it('the scene turns every use into a hidden mesh sharing one geometry, and cleans up', () => {
    const doc = BUILTIN_MAPS.showcase_v2;
    const rt = new MapRuntime(doc); rt.prepareSpawn();
    const scene = new MapScene({ runtime: rt, theme: THEME });
    const g = scene.warmGroup({ x: 0, y: 0 });
    expect(g.children.length).toBe(scene.stats.warmPrograms);
    expect(g.children.length).toBeGreaterThan(10);
    const geos = new Set(g.children.map(c => (c as THREE.Mesh).geometry));
    expect(geos.size).toBe(1);
    expect(g.children.some(c => (c as THREE.InstancedMesh).isInstancedMesh)).toBe(true);
    expect(g.children.every(c => c.scale.x < 0.01)).toBe(true);
    scene.disposeWarm(g);
    expect(g.children.length).toBe(0);
    scene.dispose();
  });
});
