import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { MapRuntime } from '../../src/map/MapRuntime';
import { BUILTIN_THEMES, slotId } from '../../src/map/MapTheme';
import { MapScene } from '../../src/render/map/scene';
import { corridorMap, rng } from '../map/fixtures';
import type { MapDocument, ThemeDef } from '../../src/map/schema';

function camera(x: number, y: number, dist = 19): THREE.PerspectiveCamera {
  const c = new THREE.PerspectiveCamera(30, 16 / 9, 0.5, 900);
  c.position.set(x, y + 2.3, dist); c.lookAt(x, y, 0); c.updateMatrixWorld(); c.updateProjectionMatrix();
  return c;
}

/** 400 m corridor with trees / rocks / grass as decoration, one mountain, a collision-less cliff and two emitters. */
function decorMap(theme = 'world_meadow'): MapDocument {
  const d = corridorMap({ tiles: 20, tileW: 20 });
  d.theme = { ref: theme };
  const r = rng(7);
  for (let i = 0; i < 20; i++) {
    d.entities.push({ id: `forest${i}`, type: 'decor', prefab: 'tree_cluster', position: { x: i * 20 + 10, y: 0, z: -6 - r() * 6 }, properties: { count: 8, spread: 14, seed: i + 1 } });
    d.entities.push({ id: `rocks${i}`, type: 'decor', prefab: 'rock_cluster', position: { x: i * 20 + 4, y: 0, z: -2 - r() * 2 }, properties: { count: 5, spread: 8, seed: i + 50 } });
  }
  d.entities.push({ id: 'cliff', type: 'decor', prefab: 'cliff', position: { x: 30, y: -2, z: -12 } });
  d.entities.push({ id: 'mtn', type: 'background', prefab: 'mountain', position: { x: 100, y: -10, z: -90 } });
  d.entities.push({ id: 'amb', type: 'audio', prefab: 'audio_emitter', position: { x: 12, y: 4 }, properties: { sound: 'wind', radius: 20, volume: 0.5 } });
  d.entities.push({ id: 'fx', type: 'vfx', prefab: 'vfx_emitter', position: { x: 14, y: 3 }, properties: { kind: 'sparkle', rate: 4 } });
  return d;
}

const frame = (scene: MapScene, cam: THREE.PerspectiveCamera, x: number, y: number, tick = 1, time = 1): void => {
  scene.beginFrame();
  scene.update({ dt: 1 / 60, time, camera: cam, focus: { x, y }, tick });
};
const themeOf = (id: string): ThemeDef => BUILTIN_THEMES[id];
const open = (doc = decorMap(), opts: Partial<ConstructorParameters<typeof MapScene>[0]> = {}) => {
  const rt = new MapRuntime(doc);
  rt.prepareSpawn();
  const scene = new MapScene({ runtime: rt, theme: themeOf(doc.theme.ref as string), ...opts });
  return { rt, scene };
};

describe('MapScene — chunk rendering & streaming', () => {
  it('builds exactly the chunks the runtime has loaded, nearest first, and none for far parts of the map', () => {
    const { rt, scene } = open();
    frame(scene, camera(0, 3), 0, 3);
    const loaded = rt.chunks.loadedIds();
    expect(loaded.length).toBeGreaterThan(0);
    expect(scene.builtChunks()).toEqual([...loaded].sort());
    expect(loaded.length).toBeLessThan(rt.chunks.infoOf(loaded[0]) ? 20 : 0);            // not the whole 400 m
    expect(scene.stats.chunksBuilt).toBe(loaded.length);
    scene.dispose();
  });

  it('streams: chunks behind the player are disposed, chunks ahead are built, resident count stays bounded', () => {
    const { rt, scene } = open();
    let maxBuilt = 0;
    frame(scene, camera(0, 3), 0, 3);
    const firstSet = scene.builtChunks();
    for (let x = 0; x <= 380; x += 10) {
      rt.chunks.update(x, 3);
      for (let k = 0; k < 4; k++) frame(scene, camera(x, 3), x, 3, x + k);               // a few frames: builds are rate-limited
      maxBuilt = Math.max(maxBuilt, scene.builtChunks().length);
    }
    expect(scene.log.disposed.length).toBeGreaterThan(0);
    expect(scene.log.built.length).toBeGreaterThan(firstSet.length);
    expect(scene.builtChunks()).toEqual([...rt.chunks.loadedIds()].sort());              // converged with the runtime
    expect(scene.builtChunks().filter(id => !rt.chunks.isPinned(id)).some(id => firstSet.includes(id))).toBe(false);   // the start chunks are gone (the pinned spawn chunk stays)
    expect(maxBuilt).toBeLessThan(scene.stats.chunksBuilt);                              // far fewer resident than ever built
    // GPU resources of disposed chunks are released: instances of unloaded chunks no longer count
    expect(scene.stats.instancesTotal).toBeLessThan(2500);
    scene.dispose();
  });

  it('limits builds per frame after the first update (no hitches) and reports build timing', () => {
    const { rt, scene } = open(decorMap(), { maxBuildPerFrame: 1 });
    frame(scene, camera(0, 3), 0, 3);
    const b0 = scene.stats.chunksBuilt;
    rt.chunks.update(200, 3);
    frame(scene, camera(200, 3), 200, 3);
    expect(scene.stats.chunksBuilt - b0).toBeLessThanOrEqual(1);
    expect(scene.stats.buildMsLast).toBeGreaterThanOrEqual(0);
    expect(scene.stats.buildMsMax).toBeGreaterThanOrEqual(scene.stats.buildMsLast);
    scene.dispose();
  });
});

describe('MapScene — batching, instancing & draw calls', () => {
  it('merges static geometry per chunk×material and instances decoration: draw calls ≪ entity count', () => {
    const { scene } = open();
    frame(scene, camera(0, 3), 0, 3);
    const s = scene.stats;
    expect(s.instancesTotal).toBeGreaterThan(30);                                          // trees/rocks/grass really instanced
    expect(s.pools).toBeGreaterThan(0);
    expect(s.pools).toBeLessThan(40);                                                      // shared pools (mesh variant × material × LOD)
    expect(s.drawCallsEstimated).toBeLessThan(150);
    expect(s.trianglesSubmitted).toBeGreaterThan(1000);
    scene.dispose();
  });

  it('a dense forest costs a handful of draw calls, not one per tree', () => {
    const d = corridorMap({ tiles: 4, tileW: 20 });
    d.theme = { ref: 'world_meadow' };
    for (let i = 0; i < 14; i++) d.entities.push({ id: `dense${i}`, type: 'decor', prefab: 'tree_cluster', position: { x: 2 + i * 4, y: 0, z: -8 }, properties: { count: 40, spread: 40, seed: i + 1 } });
    const { scene } = open(d);
    frame(scene, camera(20, 3), 20, 3);
    expect(scene.stats.instancesTotal).toBeGreaterThan(250);
    expect(scene.stats.drawCallsEstimated).toBeLessThan(scene.stats.instancesTotal / 2);   // a tree is ≥ 2 parts: without instancing this would be ≥ 2 draws each
    scene.dispose();
  });

  it('instanced pools are shared across chunks (pool count does not grow with the number of loaded chunks)', () => {
    const { rt, scene } = open();
    frame(scene, camera(0, 3), 0, 3);
    const p0 = scene.stats.pools;
    for (let x = 20; x <= 200; x += 20) { rt.chunks.update(x, 3); for (let k = 0; k < 4; k++) frame(scene, camera(x, 3), x, 3); }
    expect(scene.stats.pools).toBeLessThanOrEqual(p0 + 12);                                 // only new LOD variants add pools
    expect(scene.stats.pools).toBeLessThan(60);
    scene.dispose();
  });

  it('decoration needs no collision: decor / background entities add no colliders, the collider set is the same without them', () => {
    const bare = decorMap(); bare.entities = bare.entities.filter(e => e.type !== 'decor' && e.type !== 'background');
    const a = new MapRuntime(decorMap()), b = new MapRuntime(bare);
    a.prepareSpawn(); b.prepareSpawn();
    expect(a.metrics().activeColliders).toBe(b.metrics().activeColliders);
    expect(a.metrics().activeColliders).toBeGreaterThan(0);
    // …yet the scene draws the decor: more triangles than the bare map
    const sa = new MapScene({ runtime: a, theme: themeOf('world_meadow') }), sb = new MapScene({ runtime: b, theme: themeOf('world_meadow') });
    frame(sa, camera(0, 3), 0, 3); frame(sb, camera(0, 3), 0, 3);
    expect(sa.stats.trianglesSubmitted).toBeGreaterThan(sb.stats.trianglesSubmitted);
    sa.dispose(); sb.dispose();
  });
});

describe('MapScene — LOD & culling', () => {
  it('uses LOD0 for near decoration and cheaper levels when the camera is far away', () => {
    const { scene } = open();
    frame(scene, camera(0, 3, 19), 0, 3);
    const near = [...scene.stats.lod];
    expect(near[0]).toBeGreaterThan(0);
    const { scene: sFar } = open();
    frame(sFar, camera(0, 3, 150), 0, 3);                                                  // pulled far back: everything is deep
    const far = [...sFar.stats.lod];
    expect(far[1] + far[2]).toBeGreaterThan(near[1] + near[2]);
    expect(far[0]).toBeLessThan(near[0]);
    scene.dispose(); sFar.dispose();
  });

  it('a smaller lodBias (low quality) switches to cheaper levels earlier', () => {
    const hi = open(decorMap(), { lodBias: 1 }), lo = open(decorMap(), { lodBias: 0.4 });
    for (const s of [hi.scene, lo.scene]) frame(s, camera(0, 3, 40), 0, 3);
    expect(lo.scene.stats.lod[1] + lo.scene.stats.lod[2]).toBeGreaterThanOrEqual(hi.scene.stats.lod[1] + hi.scene.stats.lod[2]);
    expect(lo.scene.stats.trianglesSubmitted).toBeLessThanOrEqual(hi.scene.stats.trianglesSubmitted);
    hi.scene.dispose(); lo.scene.dispose();
  });

  it('frustum culls instances outside the view and chunks are hidden when off screen', () => {
    const { rt, scene } = open();
    for (let x = 0; x <= 120; x += 20) rt.chunks.update(x, 3);
    for (let k = 0; k < 8; k++) frame(scene, camera(10, 3), 10, 3, k);
    expect(scene.stats.culledFrustum).toBeGreaterThan(0);
    expect(scene.stats.instancesVisible).toBeLessThan(scene.stats.instancesTotal);
    expect(scene.stats.chunksVisible).toBeLessThan(scene.stats.chunksLoaded);
    scene.dispose();
  });

  it('density < 1 removes decoration instances (quality scaling) and never changes gameplay geometry', () => {
    const full = open(decorMap(), { density: 1 }), thin = open(decorMap(), { density: 0.4 });
    frame(full.scene, camera(0, 3), 0, 3); frame(thin.scene, camera(0, 3), 0, 3);
    expect(thin.scene.stats.instancesTotal).toBeLessThan(full.scene.stats.instancesTotal);
    expect(thin.scene.stats.mergedMeshes).toBe(full.scene.stats.mergedMeshes);
    full.scene.dispose(); thin.scene.dispose();
  });
});

describe('MapScene — materials, textures & themes', () => {
  it('shares materials and textures: counts stay small and texture memory is bounded', () => {
    const { rt, scene } = open();
    for (let x = 0; x <= 380; x += 20) { rt.chunks.update(x, 3); for (let k = 0; k < 3; k++) frame(scene, camera(x, 3), x, 3); }
    expect(scene.stats.materials).toBeLessThan(40);
    expect(scene.stats.textures).toBeLessThan(40);
    expect(scene.stats.textureBytes).toBeLessThan(8 * 1024 * 1024);
    scene.dispose();
  });

  it('different themes produce visibly different rock / ground materials', () => {
    const colours = ['world_meadow', 'world_ice', 'world_volcanic', 'world_mystic'].map(id => {
      const { scene } = open(decorMap(id));
      const m = scene.materials.get(slotId(themeOf(id), 'rock') ?? 'rock') as THREE.MeshStandardMaterial;
      const c = m.color.getHex();
      scene.dispose();
      return c;
    });
    expect(new Set(colours).size).toBe(4);
  });

  it('normal maps can be switched off (low quality) without breaking the scene', () => {
    const { scene } = open(decorMap(), { normalMaps: false });
    frame(scene, camera(0, 3), 0, 3);
    const m = scene.materials.get(slotId(themeOf('world_meadow'), 'rock') ?? 'rock') as THREE.MeshStandardMaterial;
    expect(m.normalMap).toBeFalsy();
    expect(scene.stats.chunksBuilt).toBeGreaterThan(0);
    scene.dispose();
  });
});

describe('MapScene — emitters, markers, positions', () => {
  it('exposes audio / vfx emitter entities of loaded chunks and entity positions', () => {
    const { scene } = open();
    frame(scene, camera(10, 3), 10, 3);
    const em = scene.emitters();
    expect(em.find(e => e.id === 'amb')?.kind).toBe('audio');
    expect(em.find(e => e.id === 'fx')?.kind).toBe('vfx');
    const p = scene.positionOf('amb');
    expect(p).not.toBeNull();
    expect(Math.abs(p!.x - 12)).toBeLessThan(0.01);
    expect(scene.positionOf('does-not-exist')).toBeNull();
    scene.dispose();
  });

  it('checkpoint banners light up when reached and reset on restart', () => {
    const { scene } = open(decorMap());
    // the corridor fixture has no checkpoints: spawn + finish markers exist
    expect(scene.markerState('spawn')).not.toBeNull();
    expect(scene.markerState('finish')).not.toBeNull();
    scene.setCheckpointReached('finish');
    scene.resetMarkers();
    expect(scene.markerState('finish')?.reached).toBe(false);
    scene.dispose();
  });
});

describe('MapScene — dispose', () => {
  it('releases everything and leaves an empty root', () => {
    const { rt, scene } = open();
    for (let x = 0; x <= 100; x += 20) { rt.chunks.update(x, 3); frame(scene, camera(x, 3), x, 3); }
    scene.dispose();
    expect(scene.root.children.length).toBe(0);
    expect(scene.poolCount()).toBe(0);
    expect(scene.builtChunks()).toEqual([]);
  });
});
