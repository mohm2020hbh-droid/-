import { describe, expect, it } from 'vitest';
import { parseMap } from '../../src/map/MapLoader';
import { validateMap, BUDGETS } from '../../src/map/MapValidator';
import type { MapDocument } from '../../src/map/schema';
import { baseDoc, smallMap, stone } from './fixtures';

const codes = (d: MapDocument, opts?: Parameters<typeof validateMap>[1]) => validateMap(d, opts).issues.map(i => `${i.severity}:${i.code}`);
const has = (d: MapDocument, c: string, sev = 'ERROR', opts?: Parameters<typeof validateMap>[1]) => expect(codes(d, opts), c).toContain(`${sev}:${c}`);
const clone = (d: MapDocument): MapDocument => JSON.parse(JSON.stringify(d));

describe('MapValidator — baseline', () => {
  it('the small hand-authored map has no errors, only INFO/WARNING, and a STATS line', () => {
    const doc = parseMap(smallMap()).doc;
    const r = validateMap(doc);
    expect(r.issues.filter(i => i.severity === 'ERROR')).toEqual([]);
    expect(r.ok).toBe(true);
    expect(r.issues.some(i => i.code === 'STATS')).toBe(true);
    expect(r.counts.error).toBe(0);
  });
  it('the minimal base doc is valid', () => { expect(validateMap(baseDoc()).ok).toBe(true); });
});

describe('MapValidator — spawn / finish', () => {
  it('missing spawn', () => { const d = baseDoc(); d.spawn = null; has(d, 'SPAWN_MISSING'); });
  it('spawn that is not on a surface', () => { const d = baseDoc(); d.spawn = { id: 's', position: { x: 0, y: 8 } }; has(d, 'SPAWN_NOT_ON_GROUND'); });
  it('spawn on a moving platform does not count as ground (it moves away)', () => {
    const d = baseDoc(); d.entities = [{ id: 'm', type: 'moving', prefab: 'moving_platform', position: { x: 0, y: 0 } }]; has(d, 'SPAWN_NOT_ON_GROUND');
  });
  it('missing finish', () => { const d = baseDoc(); d.finish = { zones: [] }; has(d, 'FINISH_MISSING'); });
  it('finish outside the world', () => { const d = baseDoc(); d.finish.zones[0].position = { x: 5000, y: 5000 }; has(d, 'OBJECT_OUT_OF_BOUNDS'); });
  it('unreachable finish (deep analysis with the real physics): a 60 m gap cannot be crossed', () => {
    const d = baseDoc('gap'); d.entities = [stone('a', 0, 0, 10, 5), stone('b', 60, 0, 10, 5)]; d.finish = { zones: [{ id: 'f', position: { x: 60, y: 2.5 }, shape: { kind: 'box', w: 3, h: 5 } }] };
    d.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 60, y: 0 }] }] };
    const fast = { analysis: { angleStepDeg: 15, loadStep: 25, samplesX: [0.5], phases: [0] } };
    has(d, 'FINISH_UNREACHABLE', 'ERROR', { deep: true, ...fast });
    const ok = baseDoc('near'); ok.entities = [stone('a', 0, 0, 10, 5), stone('b', 11, 0, 10, 5)]; ok.finish = { zones: [{ id: 'f', position: { x: 11, y: 2.5 }, shape: { kind: 'box', w: 3, h: 5 } }] };
    ok.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 11, y: 0 }] }] };
    expect(codes(ok, { deep: true, ...fast })).not.toContain('ERROR:FINISH_UNREACHABLE');
  }, 120_000);
});

describe('MapValidator — collision', () => {
  it('invalid collision: zero-area box and non-convex polygon', () => {
    const d = baseDoc(); d.entities.push({ id: 'z', type: 'platform', position: { x: 10, y: 0 }, collision: { shape: { kind: 'box', w: 0, h: 3 } } });
    has(d, 'COLLISION_INVALID');
    const e = baseDoc(); e.entities.push({ id: 'n', type: 'platform', position: { x: 10, y: 0 }, collision: { shape: { kind: 'convex', points: [{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 2, y: 1 }, { x: 4, y: 4 }, { x: 0, y: 4 }] } } });
    has(e, 'COLLISION_INVALID');
  });
  it('a solid-type entity with no collision is flagged', () => { const d = baseDoc(); d.entities.push({ id: 'ghost', type: 'platform', position: { x: 10, y: 0 }, visual: { kind: 'procedural' } }); has(d, 'COLLISION_INVALID', 'WARNING'); });
  it('overlapping static solids', () => { const d = baseDoc(); d.entities.push(stone('over', 3, 1, 8, 4)); has(d, 'COLLISION_OVERLAP', 'WARNING'); });
  it('touching solids are fine; a 5 mm seam is a hole warning', () => {
    const touch = baseDoc(); touch.entities = [{ id: 'a', type: 'wall', prefab: 'wall', position: { x: 0, y: 0 }, properties: { width: 4, height: 4 } }, { id: 'b', type: 'wall', prefab: 'wall', position: { x: 4, y: 0 }, properties: { width: 4, height: 4 } }];
    expect(codes(touch)).not.toContain('WARNING:COLLISION_OVERLAP'); expect(codes(touch)).not.toContain('WARNING:COLLISION_HOLE');
    const seam = baseDoc(); seam.entities = [{ id: 'a', type: 'wall', prefab: 'wall', position: { x: 0, y: 0 }, properties: { width: 4, height: 4 } }, { id: 'b', type: 'wall', prefab: 'wall', position: { x: 4.005, y: 0 }, properties: { width: 4, height: 4 } }];
    seam.spawn = { id: 's', position: { x: 0, y: 2 } };
    has(seam, 'COLLISION_HOLE', 'WARNING');
  });
  it('a moving platform overlapping a wall at the end of its range', () => {
    const d = baseDoc(); d.entities.push({ id: 'm', type: 'moving', prefab: 'moving_platform', position: { x: 30, y: 3 }, properties: { ampX: 8 } }, { id: 'w', type: 'wall', prefab: 'wall', position: { x: 36, y: 3 }, properties: { width: 2, height: 8 } });
    has(d, 'COLLISION_OVERLAP', 'WARNING');
  });
  it('objects outside the bounds: gameplay = ERROR, scenery = INFO', () => {
    const d = baseDoc(); d.entities.push(stone('far', 900, 0)); has(d, 'OBJECT_OUT_OF_BOUNDS');
    const e = baseDoc(); e.entities.push({ id: 'tree', type: 'decor', prefab: 'tree_cluster', position: { x: 900, y: 0 } }); has(e, 'OBJECT_OUT_OF_BOUNDS', 'INFO');
  });
});

describe('MapValidator — references', () => {
  it('missing prefab', () => { const d = baseDoc(); d.entities.push({ id: 'x', type: 'platform', prefab: 'no_such_prefab', position: { x: 10, y: 0 } }); has(d, 'PREFAB_MISSING'); });
  it('missing material (named and theme slot) / theme', () => {
    const d = baseDoc(); d.entities.push({ id: 'x', type: 'decor', position: { x: 10, y: 0 }, visual: { kind: 'procedural', material: 'does_not_exist' } }); has(d, 'MATERIAL_MISSING');
    const e = baseDoc(); e.entities.push({ id: 'x', type: 'decor', position: { x: 10, y: 0 }, visual: { kind: 'procedural', material: '@lava' } }); has(e, 'MATERIAL_MISSING');            // autumn has no lava slot
    const t = baseDoc(); t.theme = { ref: 'nope' }; has(t, 'THEME_MISSING');
  });
  it('missing texture / model / audio', () => {
    const d = baseDoc(); d.materials = { m: { id: 'm', shader: 'palette', color: '#fff', palette: 'tex_missing' } }; has(d, 'TEXTURE_MISSING');
    const e = baseDoc(); e.entities.push({ id: 'x', type: 'decor', position: { x: 10, y: 0 }, visual: { kind: 'mesh', mesh: 'rock_a' } }); has(e, 'MODEL_MISSING');
    const f = baseDoc(); f.regions = [{ id: 'r', type: 'audio', shape: { kind: 'box', x: 0, y: 0, w: 4, h: 4 }, enter: [{ op: 'audio', id: 'wind_loop' }] }]; has(f, 'AUDIO_MISSING');
    // declared → resolved
    const g = baseDoc(); g.assets = [{ id: 'rock_a', kind: 'mesh', path: 'models/rock_a.glb', tris: 800 }]; g.entities.push({ id: 'x', type: 'decor', position: { x: 10, y: 0 }, visual: { kind: 'mesh', mesh: 'rock_a' } });
    expect(codes(g)).not.toContain('ERROR:MODEL_MISSING');
    const h = baseDoc(); h.assets = [{ id: 'unused', kind: 'texture', path: 'textures/u.png' }]; has(h, 'ASSET_UNUSED', 'INFO');
  });
  it('asset path traversal is rejected', () => { const d = baseDoc(); d.assets = [{ id: 'x', kind: 'texture', path: '../secret.png' }]; has(d, 'STRUCT_STRING'); });
});

describe('MapValidator — behaviours', () => {
  it('broken behaviour parameters', () => {
    const d = baseDoc(); d.entities.push({ id: 'm', type: 'moving', prefab: 'moving_platform', position: { x: 30, y: 3 }, behavior: { type: 'move', mode: 'sine', x: { amplitude: 3, period: 0 } } }); has(d, 'BEHAVIOR_INVALID');
    const e = baseDoc(); e.entities.push({ id: 'm', type: 'moving', prefab: 'moving_platform', position: { x: 30, y: 3 }, behavior: { type: 'move', mode: 'path', path: 'gone', speed: 1 } }); has(e, 'PATH_MISSING');
  });
  it('conflicting behaviours on one entity', () => {
    const d = baseDoc(); d.entities.push({ id: 'm', type: 'interactive', prefab: 'toggle_block', position: { x: 30, y: 3 }, behavior: [{ type: 'toggle', channel: 'jumps', active: [0] }, { type: 'timed', period: 2 }] }); has(d, 'BEHAVIOR_CONFLICT');
  });
  it('map-wide player-rule modes are refused', () => { const d = baseDoc(); d.world.modes.doubleJump = true; has(d, 'BEHAVIOR_UNSUPPORTED'); });
  it('capabilities the locked physics does not provide are WARNINGs (bounce, boost, rotating rider)', () => {
    const d = baseDoc(); d.entities.push({ id: 'b', type: 'platform', prefab: 'bounce_platform', position: { x: 30, y: 0 } }, { id: 'z', type: 'interactive', prefab: 'boost', position: { x: 40, y: 3 } });
    has(d, 'CAPABILITY_UNAVAILABLE', 'WARNING');
    const e = baseDoc(); e.entities.push({ id: 'r', type: 'interactive', position: { x: 30, y: 8 }, collision: { shape: { kind: 'box', w: 4, h: 1 }, safe: true }, behavior: { type: 'rotate', mode: 'continuous', speed: 30 } });
    has(e, 'CAPABILITY_UNAVAILABLE', 'WARNING');
  });
});

describe('MapValidator — progress, checkpoints, chunks', () => {
  it('invalid progress path', () => {
    const d = baseDoc(); d.progress = { routes: [] }; has(d, 'PROGRESS_INVALID');
    const e = baseDoc(); e.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 0, y: 0 }] }] }; has(e, 'PROGRESS_INVALID');
    const f = baseDoc(); f.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 5, y: 0, percent: 90 }, { x: 10, y: 0, percent: 10 }] }] }; has(f, 'PROGRESS_ANCHORS');
    const g = baseDoc(); g.progress.routes[0].points = [{ x: 60, y: 0 }, { x: 70, y: 0 }]; has(g, 'PROGRESS_ENDPOINTS', 'WARNING');
  });
  const cp = (id: string, order: number, x: number) => ({ id, order, region: { kind: 'box' as const, x, y: 5, w: 4, h: 10 }, respawn: { x, y: 3 } });
  it('broken checkpoint order: duplicates, gaps, decreasing progress, unknown references, count mismatch', () => {
    const d = baseDoc(); d.checkpoints = [cp('a', 0, 20), cp('b', 0, 40)]; has(d, 'CHECKPOINT_ORDER');
    const e = baseDoc(); e.checkpoints = [cp('a', 0, 20), cp('b', 2, 40)]; has(e, 'CHECKPOINT_ORDER', 'WARNING');
    const f = baseDoc(); f.checkpoints = [{ ...cp('a', 0, 20), progress: 60 }, { ...cp('b', 1, 40), progress: 30 }]; has(f, 'CHECKPOINT_ORDER');
    const g = baseDoc(); g.checkpoints = [{ ...cp('a', 0, 20), requires: ['ghost'] }]; has(g, 'CHECKPOINT_ORDER');
    const h = baseDoc(); h.splits = { splits: [{ id: 's', name: 'S', checkpoint: 'ghost' }], targets: { gold: 0, silver: 0, bronze: 0 } }; has(h, 'CHECKPOINT_ORDER');
    const i = baseDoc(); i.checkpoints = [cp('a', 0, 20)]; has(i, 'CHECKPOINT_COUNT', 'WARNING');
    const j = baseDoc(); j.checkpoints = [{ ...cp('a', 0, 20), region: { kind: 'box', x: 20, y: 5, w: 0, h: 0 } }]; has(j, 'CHECKPOINT_REGION');
    const k = baseDoc(); k.checkpoints = [{ ...cp('a', 0, 20), respawn: { x: 20, y: 40 } }]; has(k, 'CHECKPOINT_RESPAWN', 'WARNING');
  });
  it('hand-authored checkpoint / finish regions are refused (typed lists are authoritative)', () => { const d = baseDoc(); d.regions = [{ id: 'cp', type: 'checkpoint', shape: { kind: 'box', x: 0, y: 0, w: 2, h: 2 } }]; has(d, 'STRUCT_ENUM'); });
  it('chunk radii: the activation radius must keep the physics safe', () => {
    const d = baseDoc(); d.chunks.activateRadius = 10; has(d, 'CHUNK_RADIUS');
    const e = baseDoc(); e.chunks.unloadRadius = e.chunks.loadRadius; has(e, 'CHUNK_RADIUS');
  });
  it('chunk overlap and unassigned entities (explicit layout)', () => {
    const d = baseDoc(); d.chunks.mode = 'explicit'; d.chunks.defs = [{ id: 'a', bounds: { minX: -20, maxX: 50, minY: -20, maxY: 60 } }, { id: 'b', bounds: { minX: 40, maxX: 120, minY: -20, maxY: 60 } }];
    has(d, 'CHUNK_OVERLAP', 'WARNING');
    const e = baseDoc(); e.chunks.mode = 'explicit'; e.chunks.defs = [{ id: 'a', bounds: { minX: -20, maxX: 50, minY: -20, maxY: 60 } }]; has(e, 'CHUNK_UNASSIGNED', 'WARNING');
    const f = baseDoc(); f.chunks.mode = 'explicit'; f.chunks.defs = [{ id: 'a', bounds: { minX: -20, maxX: 120, minY: -20, maxY: 60 } }]; f.entities[0].chunk = 'zzz'; has(f, 'CHUNK_UNASSIGNED');
  });
  it('duplicate ids', () => { const d = baseDoc(); d.entities.push(stone('start', 30, 0)); has(d, 'STRUCT_DUPLICATE_ID'); });
});

describe('MapValidator — budgets (Android)', () => {
  it('too many colliders / entities / draw calls in one window raise budget warnings, 2× raises errors', () => {
    const d = baseDoc('crowd');
    for (let i = 0; i < 700; i++) d.entities.push({ id: `p${i}`, type: 'platform', prefab: 'stone_platform', position: { x: 20 + (i % 35) * 2.5, y: 8 + Math.floor(i / 35) * 2.2 }, properties: { width: 1, thickness: 0.4 } });
    d.world.bounds = { minX: -20, maxX: 120, minY: -20, maxY: 90 };
    d.chunks.cell = { w: 200, h: 200 };
    const r = validateMap(d, { budget: 'android-mid' });
    expect(r.issues.some(i => i.code === 'BUDGET_COLLIDERS')).toBe(true);
    expect(r.issues.some(i => i.code === 'BUDGET_DRAWCALLS')).toBe(true);
    const low = validateMap(d, { budget: 'android-low' });
    expect(low.issues.filter(i => i.code === 'BUDGET_COLLIDERS')[0].severity).toBe('ERROR');          // 700 > 2 × 300
    expect(BUDGETS['android-low'].colliders).toBeLessThan(BUDGETS['android-mid'].colliders);
  });
  it('triangle and texture-memory budgets use declared asset data', () => {
    const d = baseDoc('heavy');
    d.assets = [{ id: 'rock', kind: 'mesh', path: 'models/r.glb', tris: 120_000 }, { id: 'tex', kind: 'texture', path: 'textures/big.png', width: 4096, height: 4096 }, { id: 'tex_n', kind: 'texture', path: 'textures/big_n.png', width: 4096, height: 4096 }];
    d.materials = { mat: { id: 'mat', shader: 'stylized-lit', color: '#888', albedo: 'tex', normal: 'tex_n' } };
    for (let i = 0; i < 4; i++) d.entities.push({ id: `r${i}`, type: 'decor', position: { x: 10 + i, y: 4 }, visual: { kind: 'mesh', mesh: 'rock', material: 'mat' } });
    const r = validateMap(d, { budget: 'android-mid' });
    expect(r.issues.some(i => i.code === 'BUDGET_TRIANGLES' && i.severity === 'WARNING')).toBe(true);        // 480 k > 250 k
    expect(r.issues.some(i => i.code === 'BUDGET_TEXTURE_MEMORY')).toBe(true);                                // two 4096² RGBA textures + mips ≈ 179 MB > 96 MB, each counted once
  });
  it('declared VFX / audio caps above the preset are flagged', () => { const d = baseDoc(); d.vfx.maxParticles = 5000; d.audio.maxVoices = 64; const c = codes(d); expect(c).toContain('WARNING:BUDGET_VFX'); expect(c).toContain('WARNING:BUDGET_AUDIO'); });
});
