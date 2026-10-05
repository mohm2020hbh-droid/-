import { describe, expect, it } from 'vitest';
import { baseDoc, stone } from './fixtures';
import { validateMap } from '../../src/map/MapValidator';
import { PrefabRegistry, resolveEntity } from '../../src/map/MapPrefab';
import { buildInstance } from '../../src/map/MapEntity';
import { BUILTIN_MESHES, BUILTIN_MESH_IDS, PROCEDURAL_TEXTURES, STYLE_TO_MESH, isBuiltinMesh } from '../../src/map/MapAssets';
import { SURFACE_BRIDGE, bridgeDefaults, bridgeMismatch, materialProblems, normalizeMaterial, surfaceTypeOf, texturesOfMaterial } from '../../src/map/MapMaterial';
import { BUILTIN_THEMES, V2_THEME_IDS, lightingOf, resolveMaterialRef, themeIdentity, themeMaterials } from '../../src/map/MapTheme';
import { SLOT_NAMES } from '../../src/map/themeKit';
import { expandScatter, hashString, rng32 } from '../../src/map/MapScatter';
import { DEFAULT_LOD_DEPTH, layerOf, resolveVisual } from '../../src/map/MapVisual';
import { estimateBackdrop, estimateVisuals } from '../../src/map/MapRenderPlan';
import { CameraProfileBlender, DEFAULT_CAMERA_PROFILE, baseProfileOf, cameraProfileProblems, mergeProfile } from '../../src/map/MapCamera';
import { SURFACE_TYPES, type MapDocument, type MapEntity, type RegionDef } from '../../src/map/schema';

const inst = (doc: MapDocument, e: MapEntity) => {
  const reg = PrefabRegistry.forDoc(doc);
  const r = resolveEntity(e, reg).entity;
  return buildInstance(r, new Map());
};
const codes = (d: MapDocument) => validateMap(d).issues.map(i => `${i.severity}:${i.code}`);

describe('MapMaterial (Visual V2 · phase 3)', () => {
  it('normalises pre-V2 and V2 spellings into one fully-defaulted material', () => {
    const legacy = normalizeMaterial({ id: 'a', shader: 'stylized-lit', color: '#112233', albedo: 'tex', normal: 'tex_n', emissive: 0.4 });
    expect(legacy.baseColor).toBe('#112233');
    expect(legacy.baseColorMap).toBe('tex'); expect(legacy.normalMap).toBe('tex_n');
    expect(legacy.emissiveIntensity).toBe(0.4);
    const v2 = normalizeMaterial({ id: 'b', baseColor: '#ffffff', roughness: 0.3, metalness: 0.2, opacity: 0.5, tiling: 3, uvScale: 0.5, surfaceType: 'ICE', emissive: '#00ff00', emissiveIntensity: 2 });
    expect(v2).toMatchObject({ roughness: 0.3, metalness: 0.2, opacity: 0.5, tiling: { x: 3, y: 3 }, uvScale: 0.5, surfaceType: 'ICE', emissive: '#00ff00', emissiveIntensity: 2 });
    const bare = normalizeMaterial({ id: 'c' });
    expect(bare.surfaceType).toBe('NORMAL'); expect(bare.opacity).toBe(1); expect(bare.roughness).toBeGreaterThan(0.5);
  });
  it('shader / surface type give sensible defaults (water is translucent and flows, ice is glossy, lava glows)', () => {
    expect(normalizeMaterial({ id: 'w', surfaceType: 'WATER' })).toMatchObject({ opacity: 0.74, doubleSided: true });
    expect(normalizeMaterial({ id: 'w', surfaceType: 'WATER' }).flow.y).toBeGreaterThan(0);
    expect(normalizeMaterial({ id: 'i', surfaceType: 'ICE' }).roughness).toBeLessThan(0.2);
    expect(normalizeMaterial({ id: 'l', surfaceType: 'LAVA' }).emissiveIntensity).toBeGreaterThan(0.5);
  });
  it('lists the textures a material samples', () => {
    expect(texturesOfMaterial({ id: 'm', baseColorMap: 'proc:rock', normalMap: 'proc:rock_n', emissiveMap: 'e' })).toEqual(['proc:rock', 'proc:rock_n', 'e']);
  });
  it('reports out-of-range / malformed values', () => {
    const p = materialProblems({ id: 'm', roughness: 2, metalness: -1, opacity: 5, baseColor: 'red', tiling: 0, uvScale: 0, surfaceType: 'LIQUID' as never });
    expect(p.length).toBeGreaterThanOrEqual(6);
    expect(materialProblems({ id: 'ok', baseColor: '#abc', roughness: 0.5, tiling: { x: 2, y: 2 } })).toEqual([]);
  });
  it('Physics Bridge: every surface type maps onto an EXISTING physics surface — no new physics is invented', () => {
    expect(Object.keys(SURFACE_BRIDGE).sort()).toEqual([...SURFACE_TYPES].sort());
    const allowed = new Set(['normal', 'slippery', 'bounce', 'hazard', 'goal', 'sticky', 'boost']);
    for (const b of Object.values(SURFACE_BRIDGE)) expect(allowed.has(b.surface)).toBe(true);
    expect(SURFACE_BRIDGE.ICE.surface).toBe('slippery'); expect(SURFACE_BRIDGE.SLIPPERY.surface).toBe('slippery');
    expect(SURFACE_BRIDGE.HAZARD.hazard).toBe(true); expect(SURFACE_BRIDGE.LAVA.hazard).toBe(true);
    expect(SURFACE_BRIDGE.WATER.solid).toBe(false); expect(SURFACE_BRIDGE.GOAL.solid).toBe(false);
    expect(SURFACE_BRIDGE.BOUNCE.capability).toBe('bouncePush');            // declared, not applied (locked spec)
    expect(surfaceTypeOf('slippery', false)).toBe('SLIPPERY'); expect(surfaceTypeOf('normal', true)).toBe('HAZARD'); expect(surfaceTypeOf(undefined, undefined)).toBe('NORMAL');
  });
  it('bridge defaults fill ONLY what a collision did not state; explicit values always win', () => {
    const doc = baseDoc();
    doc.materials = { ice_m: { id: 'ice_m', surfaceType: 'ICE' }, lava_m: { id: 'lava_m', surfaceType: 'LAVA' }, water_m: { id: 'water_m', surfaceType: 'WATER' } };
    const mk = (material: string, collision: NonNullable<MapEntity['collision']>): MapEntity => ({ id: 'e', type: 'platform', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:slab', material }, collision });
    const shape = { kind: 'box', w: 4, h: 1 } as const;
    expect(inst(doc, mk('ice_m', { shape })).pieces[0].surface).toBe('slippery');
    expect(inst(doc, mk('ice_m', { shape, surface: 'normal' })).pieces[0].surface).toBe('normal');         // explicit wins
    expect(inst(doc, mk('lava_m', { shape })).pieces[0].kind).toBe('hazard');
    expect(inst(doc, mk('lava_m', { shape, hazard: false })).pieces[0].kind).toBe('solid');                 // explicit wins
    expect(inst(doc, mk('water_m', { shape })).pieces[0].surface).toBe('normal');                           // water adds no physics
    expect(bridgeDefaults(undefined)).toBeNull(); expect(bridgeDefaults({ id: 'n', surfaceType: 'NORMAL' })).toBeNull();
    expect(bridgeMismatch('ICE', 'normal', false)).toBe(true); expect(bridgeMismatch('ICE', 'slippery', false)).toBe(false); expect(bridgeMismatch('WATER', 'normal', false)).toBe(false);
  });
});

describe('themes (Visual V2 · phase 4, 7)', () => {
  it('there are four distinct Visual-V2 worlds with a complete identity', () => {
    expect(V2_THEME_IDS).toEqual(['world_meadow', 'world_ice', 'world_volcanic', 'world_mystic']);
    const skies = new Set<string>(), grounds = new Set<string>();
    for (const id of V2_THEME_IDS) {
      const t = BUILTIN_THEMES[id], v = themeIdentity(t);
      expect(v.sky.top).toMatch(/^#/); expect(v.fog.density).toBeGreaterThan(0);
      expect(v.lighting.shadowQuality).toBeTruthy(); expect(v.lighting.sunDirection).toHaveLength(3);
      expect(v.primaryColor && v.secondaryColor && v.accentColor).toBeTruthy();
      expect(v.groundMaterial).toBeTruthy(); expect(v.rockMaterial).toBeTruthy();
      expect(v.vegetation.kinds.length).toBeGreaterThan(0);
      expect(v.background.length).toBeGreaterThanOrEqual(5);
      expect(v.particles.length).toBeGreaterThanOrEqual(2);
      expect(v.ambientAudio.bed).toBeTruthy();
      skies.add(t.sky.top + t.sky.horizon); grounds.add(themeMaterials(t)[v.groundMaterial!].baseColor!);
      // all 13 slots (lava only when declared) resolve to a material with a texture or emissive look
      const mats = themeMaterials(t);
      for (const slot of SLOT_NAMES) { const ref = resolveMaterialRef(`@${slot}`, t); expect(ref, slot).toBeTruthy(); expect(mats[ref!], `${id}.${slot}`).toBeTruthy(); }
    }
    expect(skies.size).toBe(4); expect(grounds.size).toBe(4);                      // four different looks
  });
  it('every procedural texture a theme references exists in the catalogue', () => {
    for (const t of Object.values(BUILTIN_THEMES)) for (const m of Object.values(themeMaterials(t))) for (const tex of texturesOfMaterial(m)) expect(tex.startsWith('proc:') && tex.slice(5) in PROCEDURAL_TEXTURES, `${t.id}: ${tex}`).toBe(true);
  });
  it('pre-V2 themes still resolve every slot (kit derived from their palette) and get a derived lighting profile', () => {
    const t = BUILTIN_THEMES.snow_peaks;
    expect(themeMaterials(t)[resolveMaterialRef('@rock', t)!]).toBeTruthy();
    const lp = lightingOf(t);
    expect(lp.sunIntensity).toBe(t.sky.sunIntensity); expect(lp.fogDensity).toBe(t.fog.density); expect(lp.shadowQuality).toBe('medium');
    expect(resolveMaterialRef('@lava', BUILTIN_THEMES.autumn_hills)).toBeUndefined();   // lava stays an opt-in slot
  });
  it('a map can swap its whole look by changing only the theme reference', () => {
    const a = baseDoc(); a.theme = { ref: 'world_meadow' };
    const b = JSON.parse(JSON.stringify(a)) as MapDocument; b.theme = { ref: 'world_volcanic' };
    expect(JSON.stringify(a.entities)).toBe(JSON.stringify(b.entities));
    expect(validateMap(a).ok && validateMap(b).ok).toBe(true);
  });
});

describe('builtin mesh catalogue', () => {
  it('has every environment generator the brief names, each with roles and LOD triangle counts that decrease', () => {
    for (const id of ['platform', 'rock', 'cliff', 'tree', 'bush', 'grass', 'mountain', 'cloud', 'water', 'waterfall', 'crystal_cluster', 'ancient_structure']) expect(isBuiltinMesh(`builtin:${id}`), id).toBe(true);
    for (const id of BUILTIN_MESH_IDS) {
      const m = BUILTIN_MESHES[id], p = { ...m.defaults };
      expect(m.roles(p).length).toBeGreaterThan(0);
      const [t0, t1, t2] = [m.tris(p, 0), m.tris(p, 1), m.tris(p, 2)];
      expect(t0, id).toBeGreaterThan(0); expect(t1, id).toBeLessThanOrEqual(t0); expect(t2, id).toBeLessThanOrEqual(t1);
    }
  });
  it('legacy style names map onto catalogue meshes', () => { for (const m of Object.values(STYLE_TO_MESH)) expect(isBuiltinMesh(m.mesh)).toBe(true); });
});

describe('MapScatter — deterministic procedural placement', () => {
  const base = { x: 10, y: 5, z: -3, rotation: 0, sx: 1, sy: 1 };
  const sc = { count: 40, width: 20, height: 4, seed: 7, scale: [0.6, 1.4] as [number, number], spacing: 0.5, variants: 5 };
  it('is a pure function of (scatter, base, seed): identical on every run, different for another seed', () => {
    const a = expandScatter(sc, base, 7), b = expandScatter(sc, base, 7), c = expandScatter(sc, base, 8);
    expect(a).toEqual(b); expect(a).not.toEqual(c);
  });
  it('stays inside the rectangle, varies scale / yaw / variant / tint (no identical repeats)', () => {
    const a = expandScatter(sc, base, 7);
    expect(a.length).toBeGreaterThan(20);
    for (const i of a) { expect(Math.abs(i.x - 10)).toBeLessThanOrEqual(10.001); expect(Math.abs(i.y - 5)).toBeLessThanOrEqual(2.001); expect(i.scale).toBeGreaterThanOrEqual(0.6); expect(i.scale).toBeLessThanOrEqual(1.4); expect(i.variant).toBeLessThan(5); }
    expect(new Set(a.map(i => i.scale.toFixed(3))).size).toBeGreaterThan(a.length * 0.8);
    expect(new Set(a.map(i => i.variant)).size).toBeGreaterThanOrEqual(4);
    expect(new Set(a.map(i => i.yaw.toFixed(1))).size).toBeGreaterThan(a.length * 0.8);
  });
  it('respects spacing', () => {
    const a = expandScatter({ ...sc, count: 30, width: 30, height: 0, spacing: 1.5 }, base, 3);
    for (let i = 0; i < a.length; i++) for (let j = i + 1; j < a.length; j++) expect(Math.hypot(a[i].x - a[j].x, a[i].y - a[j].y)).toBeGreaterThan(0.7);
  });
  it('lower quality density keeps a strict prefix of the instances (no popping when quality changes)', () => {
    const full = expandScatter({ ...sc, spacing: 0 }, base, 11, 1), half = expandScatter({ ...sc, spacing: 0 }, base, 11, 0.5);
    expect(half.length).toBe(Math.ceil(full.length * 0.5));
    expect(half).toEqual(full.slice(0, half.length));
  });
  it('follows the entity rotation and scale; rng32/hashString are stable', () => {
    const rot = expandScatter({ count: 1, width: 10, height: 0, seed: 1 }, { ...base, rotation: 90 }, 1)[0];
    const flat = expandScatter({ count: 1, width: 10, height: 0, seed: 1 }, base, 1)[0];
    expect(rot.y - 5).toBeCloseTo(flat.x - 10, 5);
    expect(rng32(5)()).toBe(rng32(5)()); expect(hashString('abc')).toBe(hashString('abc')); expect(hashString('abc')).not.toBe(hashString('abd'));
  });
});

describe('MapVisual — visual ≠ collision (phase 1)', () => {
  const theme = BUILTIN_THEMES.world_meadow;
  const ctx = { theme, assets: new Map() };
  it('a platform with an explicit visual mesh is drawn from that mesh; its collision stays a plain box', () => {
    const d = baseDoc();
    const e: MapEntity = { id: 'p', type: 'platform', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:platform', meshParams: { style: 'wood' }, castShadow: true, receiveShadow: true, renderLayer: 'gameplay' }, collision: { shape: { kind: 'box', w: 6, h: 2, taper: 0.9, anchor: 'topCenter' } } };
    const i = inst(d, e), rv = resolveVisual(i, ctx)!;
    expect(rv.mesh).toBe('builtin:platform'); expect(rv.derived).toBe(false);
    expect(rv.params).toMatchObject({ w: 6, h: 2, taper: 0.9, style: 'wood' });          // size derived from the collision, look from the visual
    expect(rv.parts.map(p => p.role)).toEqual(['wood']); expect(rv.cast).toBe(true);
    expect(i.pieces[0].local.length).toBe(4);                                              // collision is the 4-vertex trapezoid, not the visual
  });
  it('a high-detail visual can sit on a trivial collider (and the reverse): they share only the transform', () => {
    const d = baseDoc();
    const e: MapEntity = { id: 'r', type: 'decor', position: { x: 4, y: 1 }, visual: { kind: 'mesh', mesh: 'builtin:rock', meshParams: { size: 3 } } };
    const rv = resolveVisual(inst(d, e), ctx)!;
    expect(rv.tris[0]).toBeGreaterThan(150); expect(inst(d, e).pieces).toHaveLength(0);
    const sph: MapEntity = { id: 's', type: 'platform', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:rock' }, collision: { shape: { kind: 'sphere', r: 1 } } };
    const si = inst(d, sph);
    expect(si.pieces[0].local.length).toBe(16); expect(resolveVisual(si, ctx)!.tris[0]).toBe(BUILTIN_MESHES['builtin:rock'].tris({}, 0));
  });
  it('an entity with collision but NO visual gets a derived stand-in (flagged), never the raw collision polygon', () => {
    const d = baseDoc();
    const e: MapEntity = { id: 'w', type: 'wall', position: { x: 0, y: 0 }, collision: { shape: { kind: 'box', w: 2, h: 12 } } };
    const rv = resolveVisual(inst(d, e), ctx)!;
    expect(rv.derived).toBe(true); expect(rv.mesh).toBe('builtin:cliff'); expect(rv.params).toMatchObject({ w: 2, h: 12 });
    const slope: MapEntity = { id: 'sl', type: 'slope', position: { x: 0, y: 0 }, collision: { shape: { kind: 'slope', w: 6, h: 3 } } };
    expect(resolveVisual(inst(d, slope), ctx)!.mesh).toBe('builtin:poly_slab');
    expect(codes(Object.assign(baseDoc(), { entities: [...baseDoc().entities, e] }))).toContain('INFO:VISUAL_DERIVED');
  });
  it('kind none draws nothing; disabled entities draw nothing; a visual-only decor has no pieces', () => {
    const d = baseDoc();
    expect(resolveVisual(inst(d, { id: 'n', type: 'platform', position: { x: 0, y: 0 }, visual: { kind: 'none' }, collision: { shape: { kind: 'box', w: 2, h: 1 } } }), ctx)).toBeNull();
    expect(resolveVisual(inst(d, { id: 'x', type: 'decor', position: { x: 0, y: 0 }, enabled: false, visual: { kind: 'mesh', mesh: 'builtin:rock' } }), ctx)).toBeNull();
  });
  it('legacy procedural styles are dressed by the matching generator; legacy "@ground" keeps the default dressing', () => {
    const d = baseDoc();
    const rv = resolveVisual(inst(d, stone('s', 0, 0)), ctx)!;
    expect(rv.mesh).toBe('builtin:platform'); expect(rv.parts).toEqual([{ role: 'body', material: 'mat.world_meadow.rock' }, { role: 'cap', material: 'mat.world_meadow.ground' }]);
    const named = resolveVisual(inst(d, { id: 'n', type: 'decor', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:rock', material: '@stone' } }), ctx)!;
    expect(named.parts[0].material).toBe('mat.world_meadow.stone');                          // V2 meshes honour slot overrides
    const roles = resolveVisual(inst(d, { id: 'r', type: 'platform', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:platform', materials: { cap: '@ice' } }, collision: { shape: { kind: 'box', w: 4, h: 2 } } }), ctx)!;
    expect(roles.parts.find(p => p.role === 'cap')!.material).toBe('mat.world_meadow.ice');
  });
  it('layers: explicit renderLayer ← legacy layer ← type ← z; non-gameplay layers get a default depth', () => {
    expect(layerOf({ kind: 'mesh', renderLayer: 'foreground' }, 'decor', 0)).toBe('foreground');
    expect(layerOf({ kind: 'procedural', layer: 'far' }, 'decor', 0)).toBe('background');
    expect(layerOf(undefined, 'platform', -30)).toBe('gameplay'); expect(layerOf(undefined, 'decor', -60)).toBe('background'); expect(layerOf(undefined, 'decor', -10)).toBe('midground'); expect(layerOf(undefined, 'decor', 3)).toBe('foreground');
    const d = baseDoc();
    expect(resolveVisual(inst(d, { id: 'f', type: 'decor', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:grass', renderLayer: 'foreground' } }), ctx)!.z).toBe(4);
    expect(resolveVisual(inst(d, { id: 'b', type: 'decor', position: { x: 0, y: 0, z: -90 }, visual: { kind: 'mesh', mesh: 'builtin:mountain', renderLayer: 'background' } }), ctx)!.z).toBe(-90);
  });
  it('render mode: dynamic → single; scatter/instancing → instanced; otherwise merged', () => {
    const d = baseDoc();
    const box = { kind: 'box', w: 4, h: 1, anchor: 'topCenter' } as const;
    const mode = (e: MapEntity) => resolveVisual(inst(d, e), ctx)!.mode;
    expect(mode({ id: 'm', type: 'moving', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:platform' }, collision: { shape: box }, behavior: { type: 'move', mode: 'sine', x: { amplitude: 2, period: 4 } } })).toBe('single');
    expect(mode({ id: 'g', type: 'interactive', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:slab' }, collision: { shape: box }, behavior: { type: 'timed', period: 4 } })).toBe('single');
    expect(mode({ id: 's', type: 'decor', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:rock', scatter: { count: 5, width: 4 } } })).toBe('instanced');
    expect(mode({ id: 'p', type: 'platform', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:platform' }, collision: { shape: box } })).toBe('merged');
  });
  it('LOD: layer defaults ascend; explicit visual.lod overrides the thresholds and the LOD mesh', () => {
    for (const l of Object.values(DEFAULT_LOD_DEPTH)) expect(l[0]).toBeLessThan(l[1]);
    const d = baseDoc();
    const rv = resolveVisual(inst(d, { id: 'l', type: 'decor', position: { x: 0, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:tree', lod: [{ distance: 20, tris: 90 }, { distance: 50, tris: 20, mesh: 'builtin:bush' }] } }), ctx)!;
    expect(rv.lodDepth).toEqual([20, 50]); expect(rv.tris[1]).toBe(90); expect(rv.tris[2]).toBe(20); expect(rv.lodMesh[1]).toBe('builtin:bush');
  });
});

describe('MapRenderPlan — draw-call model', () => {
  const theme = BUILTIN_THEMES.world_meadow;
  const matDef = (id: string) => themeMaterials(theme)[id];
  const plan = (d: MapDocument, ...ents: MapEntity[]) => estimateVisuals([ents.map(e => resolveVisual(inst(d, e), { theme, assets: new Map() })!)], matDef, new Map());
  it('merged static platforms cost one call per material, not per object', () => {
    const d = baseDoc();
    const a = plan(d, ...Array.from({ length: 5 }, (_, i) => stone(`s${i}`, i * 9, 0)));
    const b = plan(d, ...Array.from({ length: 60 }, (_, i) => stone(`s${i}`, i * 9, 0)));
    expect(b.drawCalls).toBe(a.drawCalls); expect(b.mergedCalls).toBe(2);                    // rock body + ground cap
    expect(b.triangles).toBeGreaterThan(a.triangles * 10);
  });
  it('instanced scatter: 1 000 instances cost no more draw calls than 10 (variants × materials × LOD factor)', () => {
    const d = baseDoc();
    const mk = (n: number): MapEntity => ({ id: `t${n}`, type: 'decor', position: { x: 0, y: 0, z: -12 }, visual: { kind: 'mesh', mesh: 'builtin:tree', renderLayer: 'midground', scatter: { count: n, width: 80, variants: 4 } } });
    const small = plan(d, mk(10)), big = plan(d, mk(1000));
    expect(big.instancedCalls).toBe(small.instancedCalls); expect(big.instances).toBe(1000); expect(big.triangles).toBeGreaterThan(small.triangles * 50);
    expect(big.trianglesExpected).toBeLessThan(big.triangles);                               // midground trees are LOD1 at their depth
  });
  it('instanced pools are shared across chunks; merged geometry is batched per chunk', () => {
    const d = baseDoc();
    const tree = (id: string, x: number): MapEntity => ({ id, type: 'decor', position: { x, y: 0, z: -12 }, visual: { kind: 'mesh', mesh: 'builtin:tree', renderLayer: 'midground', scatter: { count: 20, width: 20, variants: 3 } } });
    const rvs = (e: MapEntity) => resolveVisual(inst(d, e), { theme, assets: new Map() })!;
    const one = estimateVisuals([[rvs(tree('a', 0))]], matDef, new Map()), four = estimateVisuals([0, 1, 2, 3].map(i => [rvs(tree(`t${i}`, i * 32))]), matDef, new Map());
    expect(four.instancedCalls).toBe(one.instancedCalls);
    const slab = (id: string, x: number) => resolveVisual(inst(d, stone(id, x, 0)), { theme, assets: new Map() })!;
    expect(estimateVisuals([[slab('a', 0)], [slab('b', 40)]], matDef, new Map()).mergedCalls).toBe(4);
  });
  it('dynamic entities are single meshes (one call per part) and counted as such', () => {
    const d = baseDoc();
    const e = plan(d, { id: 'm', type: 'moving', prefab: 'moving_platform', position: { x: 0, y: 0 } });
    expect(e.singles).toBe(1); expect(e.drawCalls).toBe(1);
  });
  it('textures: each distinct texture counts once, procedural sizes come from the catalogue', () => {
    const d = baseDoc();
    const a = plan(d, stone('a', 0, 0)), b = plan(d, stone('a', 0, 0), stone('b', 9, 0), stone('c', 18, 0));
    expect(b.textureBytes).toBe(a.textureBytes); expect(a.textureBytes).toBeGreaterThan(100_000);
  });
  it('backdrop cost is derived from the theme layers', () => {
    for (const id of V2_THEME_IDS) { const b = estimateBackdrop(BUILTIN_THEMES[id].backdrop); expect(b.drawCalls).toBeGreaterThan(8); expect(b.drawCalls).toBeLessThan(40); expect(b.triangles).toBeLessThan(30_000); }
    expect(estimateBackdrop(undefined)).toEqual({ drawCalls: 0, triangles: 0 });
  });
});

describe('MapCamera — CameraProfile and zones (phase 10)', () => {
  it('defaults equal the shipped camera; legacy zoom/lookAhead and profile layer on top', () => {
    expect(baseProfileOf(undefined)).toEqual(DEFAULT_CAMERA_PROFILE);
    const p = baseProfileOf({ zoom: 1.2, lookAhead: 3, profile: { fov: 40 } });
    expect(p.zoom).toBe(1.2); expect(p.lookAhead).toBe(3); expect(p.fov).toBe(40); expect(p.followDistance).toBe(19);
  });
  it('clamps to limits and reports invalid fields', () => {
    expect(mergeProfile(DEFAULT_CAMERA_PROFILE, { fov: 500, zoom: -3 })).toMatchObject({ fov: 60, zoom: 0.5 });
    expect(cameraProfileProblems({ fov: 500, nope: 1 } as never).length).toBe(2);
    expect(cameraProfileProblems({ fov: 35, zoom: 1.5 })).toEqual([]);
  });
  it('zones blend smoothly toward the zone profile on enter and back on exit; priority wins', () => {
    const b = new CameraProfileBlender();
    const zone = (id: string, profile: object, priority = 0): RegionDef => ({ id, type: 'camera', shape: { kind: 'box', x: 0, y: 0, w: 1, h: 1 }, params: { profile: profile as never, blend: 1 }, priority });
    expect(b.onZone('enter', zone('z1', { followDistance: 30, fov: 36 }))).toBe(true);
    expect(b.current.followDistance).toBe(19);
    for (let i = 0; i < 30; i++) b.update(1 / 30);
    expect(b.current.followDistance).toBeGreaterThan(25); expect(b.current.followDistance).toBeLessThan(30.001);
    for (let i = 0; i < 300; i++) b.update(1 / 30);
    expect(b.current.followDistance).toBeCloseTo(30, 2);
    b.onZone('enter', zone('z2', { followDistance: 14 }, 5));                              // higher priority
    expect(b.targetProfile.followDistance).toBe(14); expect(b.targetProfile.fov).toBe(36);     // fov still from z1
    b.onZone('exit', zone('z2', {})); expect(b.targetProfile.followDistance).toBe(30);
    b.onZone('exit', zone('z1', {}));
    for (let i = 0; i < 600; i++) b.update(1 / 30);
    expect(b.current.followDistance).toBeCloseTo(19, 2); expect(b.activeZones()).toEqual([]);
    expect(b.onZone('enter', { id: 'k', type: 'kill', shape: { kind: 'box', x: 0, y: 0, w: 1, h: 1 } })).toBe(false);
  });
  it('camera effects push / reset a modifier; snap jumps to the target', () => {
    const b = new CameraProfileBlender();
    b.onEffect('fx', { profile: { zoom: 1.4 } }); b.snap(); expect(b.current.zoom).toBe(1.4);
    b.onEffect('fx', { reset: true }); b.snap(); expect(b.current.zoom).toBe(1);
  });
});

describe('MapValidator — Visual V2 rules', () => {
  const withEntity = (e: MapEntity, patch: (d: MapDocument) => void = () => {}) => { const d = baseDoc(); d.entities.push(e); patch(d); return d; };
  const decor = (visual: MapEntity['visual'], z = 0): MapEntity => ({ id: 'x', type: 'decor', position: { x: 10, y: 4, z }, visual });
  it('unknown builtin mesh, bad meshParams, bad LOD, bad scatter are reported', () => {
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:nope' })))).toContain('ERROR:MODEL_MISSING');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock', meshParams: { colour: 3 } })))).toContain('WARNING:MESH_PARAM');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock', lod: [{ distance: 50, tris: 1 }, { distance: 20, tris: 1 }] })))).toContain('ERROR:LOD_INVALID');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock', lod: [{ distance: 5, tris: 1 }, { distance: 9, tris: 1 }, { distance: 20, tris: 1 }] })))).toContain('ERROR:LOD_INVALID');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock', scatter: { count: 0, width: 3 } })))).toContain('ERROR:SCATTER_INVALID');
  });
  it('layer vs z mismatches warn (a foreground object behind the gameplay plane, a background object at z = 0)', () => {
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:grass', renderLayer: 'foreground' }, -5)))).toContain('WARNING:LAYER_Z_MISMATCH');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:mountain', renderLayer: 'background' }, -5)))).toContain('WARNING:LAYER_Z_MISMATCH');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:mountain', renderLayer: 'background' }, -90)))).not.toContain('WARNING:LAYER_Z_MISMATCH');
  });
  it('material problems: invalid values (ERROR), missing role material (ERROR), unknown procedural texture (ERROR)', () => {
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock' }), d => { d.materials = { m: { id: 'm', roughness: 7 } }; }))).toContain('ERROR:MATERIAL_INVALID');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock', materials: { body: 'ghost' } })))).toContain('ERROR:MATERIAL_MISSING');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock' }), d => { d.materials = { m: { id: 'm', baseColorMap: 'proc:nothing' } }; }))).toContain('ERROR:TEXTURE_MISSING');
    expect(codes(withEntity(decor({ kind: 'mesh', mesh: 'builtin:rock' }), d => { d.materials = { m: { id: 'm', baseColorMap: 'proc:rock', normalMap: 'proc:rock_n' } }; }))).not.toContain('ERROR:TEXTURE_MISSING');
  });
  it('Physics-Bridge guard: an ice-looking material on a normal collision (and lava on a solid) is a WARNING', () => {
    const e = (material: string, collision: NonNullable<MapEntity['collision']>): MapEntity => ({ id: 'x', type: 'platform', position: { x: 10, y: 0 }, visual: { kind: 'mesh', mesh: 'builtin:platform', material }, collision });
    const shape = { kind: 'box', w: 6, h: 2, anchor: 'topCenter' } as const;
    const mats = (d: MapDocument) => { d.materials = { ice_m: { id: 'ice_m', surfaceType: 'ICE' }, lava_m: { id: 'lava_m', surfaceType: 'LAVA' }, water_m: { id: 'water_m', surfaceType: 'WATER' } }; };
    expect(codes(withEntity(e('ice_m', { shape, surface: 'normal' }), mats))).toContain('WARNING:MATERIAL_SURFACE_MISMATCH');
    expect(codes(withEntity(e('ice_m', { shape }), mats))).not.toContain('WARNING:MATERIAL_SURFACE_MISMATCH');                // bridged by default
    expect(codes(withEntity(e('lava_m', { shape, hazard: false }), mats))).toContain('WARNING:MATERIAL_SURFACE_MISMATCH');
    expect(codes(withEntity(e('water_m', { shape }), mats))).toContain('WARNING:MATERIAL_SURFACE_MISMATCH');                   // water has no collision of its own
  });
  it('camera profile problems in the map or in a camera zone are errors', () => {
    const d = baseDoc(); d.camera.profile = { fov: 500 };
    expect(codes(d)).toContain('ERROR:CAMERA_PROFILE_INVALID');
    const z = baseDoc(); z.regions.push({ id: 'cz', type: 'camera', shape: { kind: 'box', x: 5, y: 5, w: 4, h: 4 }, params: { profile: { zoom: 9 } } });
    expect(codes(z)).toContain('ERROR:CAMERA_PROFILE_INVALID');
    const ok = baseDoc(); ok.regions.push({ id: 'cz', type: 'camera', shape: { kind: 'box', x: 5, y: 5, w: 4, h: 4 }, params: { profile: { zoom: 1.4 } } });
    expect(codes(ok)).not.toContain('ERROR:CAMERA_PROFILE_INVALID');
  });
  it('an inline theme with a broken backdrop layer is an error; an incomplete theme is INFO only', () => {
    const t = { ...BUILTIN_THEMES.autumn_hills, id: 'inline', backdrop: [{ id: 'b', kind: 'mountains' as const, z: -5, follow: 3, y: 0, height: 10, width: 10, color: '#fff' }] };
    const d = baseDoc(); d.theme = { theme: t };
    expect(codes(d)).toContain('ERROR:THEME_INVALID');
    expect(codes(baseDoc())).toContain('INFO:THEME_INCOMPLETE');
  });
  it('the environment prefabs resolve, carry no collision, and validate clean', () => {
    const reg = PrefabRegistry.forDoc(baseDoc());
    for (const id of ['rock_cluster', 'cliff', 'tree_cluster', 'bush_cluster', 'grass_patch', 'mountain', 'cloud', 'water', 'waterfall', 'crystal_cluster', 'ancient_structure', 'background_mountain']) {
      expect(reg.has(id), id).toBe(true);
      const e: MapEntity = { id: 'e', type: 'decor', prefab: id, position: { x: 10, y: 4, z: id.includes('mountain') ? -90 : id === 'cloud' ? -80 : 0 } };
      const r = resolveEntity(e, reg);
      expect(r.issues, id).toEqual([]);
      expect(r.entity.collisions, `${id} must not collide by default`).toEqual([]);
      expect(r.entity.visual?.kind === 'mesh' || r.entity.visual?.kind === 'procedural').toBe(true);
      const d = baseDoc(); d.entities.push({ ...e, type: id.includes('mountain') || id === 'cloud' ? 'background' : 'decor' });
      const rep = validateMap(d);
      expect(rep.issues.filter(i => i.severity === 'ERROR'), id).toEqual([]);
    }
  });
});
