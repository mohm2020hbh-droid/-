import { describe, expect, it } from 'vitest';
import { BUILTIN_PREFABS } from '../../src/map/builtinPrefabs';
import { PrefabRegistry, deepMerge, resolveEntity } from '../../src/map/MapPrefab';
import { buildInstance } from '../../src/map/MapEntity';
import { isConvexCCW } from '../../src/sim/geometry';
import { placePolys } from '../../src/map/MapCollision';
import type { MapEntity, PrefabDef } from '../../src/map/schema';

const reg = new PrefabRegistry();
const ent = (o: Partial<MapEntity>): MapEntity => ({ id: 'e', type: 'platform', position: { x: 0, y: 0 }, ...o });

describe('MapPrefab — data-driven templates', () => {
  it('the required prefab set exists (no code needed to author a moving platform)', () => {
    for (const id of ['stone_platform', 'ice_platform', 'moving_platform', 'bounce_platform', 'spike', 'wall', 'slope', 'tree_cluster', 'rock_cluster', 'boost', 'checkpoint', 'start', 'finish', 'decoration', 'background_object', 'hazard', 'trigger']) {
      expect(reg.has(id), id).toBe(true);
    }
  });

  it('every built-in prefab resolves with its default parameters into valid, convex, CCW collision', () => {
    for (const p of BUILTIN_PREFABS) {
      const { entity, issues } = resolveEntity(ent({ id: p.id, prefab: p.id, type: (p.entity.type ?? 'decor') as never }), reg);
      expect(issues, p.id).toEqual([]);
      const inst = buildInstance(entity, new Map());
      expect(inst.problems, p.id).toEqual([]);
      for (const piece of inst.pieces) expect(isConvexCCW(placePolys([piece.local], 0, 0, 0)[0]), `${p.id} piece`).toBe(true);
    }
  });

  it('parameters feed the expressions; entity fields override the prefab; properties are validated against min/max', () => {
    const r = resolveEntity(ent({ prefab: 'stone_platform', properties: { width: 10, thickness: 4 } }), reg).entity;
    expect(r.collisions[0].shape).toMatchObject({ kind: 'box', w: 10, h: 4, taper: 0.72, anchor: 'topCenter' });
    const over = resolveEntity(ent({ prefab: 'stone_platform', collision: { shape: { kind: 'box', w: 99, h: 1 }, surface: 'slippery' } }), reg).entity;
    expect(over.collisions[0]).toMatchObject({ surface: 'slippery', material: 'grass' });                 // merged: surface overridden, material kept
    expect((over.collisions[0].shape as { w: number }).w).toBe(99);
    const bad = resolveEntity(ent({ prefab: 'stone_platform', properties: { width: 9999 } }), reg);
    expect(bad.issues.map(i => i.code)).toContain('PARAM_RANGE');
    const wrongType = resolveEntity(ent({ prefab: 'stone_platform', properties: { width: 'wide' } }), reg);
    expect(wrongType.issues.map(i => i.code)).toContain('PARAM_TYPE');
    expect(resolveEntity(ent({ prefab: 'stone_platform', collision: null }), reg).entity.collisions).toEqual([]);    // null removes collision
  });

  it('moving_platform: a motion behaviour comes out of parameters alone', () => {
    const r = resolveEntity(ent({ type: 'moving', prefab: 'moving_platform', properties: { ampX: 5, ampY: 2, period: 8, phase: 0.5 } }), reg).entity;
    expect(r.behaviors[0]).toEqual({ type: 'move', mode: 'sine', x: { amplitude: 5, period: 8, phase: 0.5 }, y: { amplitude: 2, period: 8, phase: 0.5 } });
    const inst = buildInstance(r, new Map());
    expect(inst.motion).not.toBeNull();
    expect(inst.pieces[0].safe).toBe(false);                                                               // riders must not be saved as respawn anchors
  });

  it('map-local prefabs: extends, override a built-in, cycles and missing parents are reported', () => {
    const mine: Record<string, PrefabDef> = {
      mossy: { id: 'mossy', category: 'platform', extends: 'stone_platform', params: { width: { type: 'number', default: 8 } }, entity: { visual: { kind: 'procedural', style: 'rock', material: '@secondary' }, tags: ['mossy'] } },
      a: { id: 'a', category: 'x', extends: 'b', entity: {} }, b: { id: 'b', category: 'x', extends: 'a', entity: {} },
      orphan: { id: 'orphan', category: 'x', extends: 'nope', entity: {} },
    };
    const r2 = new PrefabRegistry(mine);
    const m = resolveEntity(ent({ prefab: 'mossy' }), r2).entity;
    expect(m.collisions[0].shape).toMatchObject({ w: 8 });                                                  // child default replaces the parent's
    expect(m.visual!.material).toBe('@secondary'); expect(m.tags).toEqual(expect.arrayContaining(['stone_platform', 'mossy']));
    expect(resolveEntity(ent({ prefab: 'a' }), r2).issues.map(i => i.code)).toContain('PREFAB_CYCLE');
    expect(resolveEntity(ent({ prefab: 'orphan' }), r2).issues.map(i => i.code)).toContain('PREFAB_MISSING');
    expect(resolveEntity(ent({ prefab: 'ghost' }), r2).issues.map(i => i.code)).toContain('PREFAB_MISSING');
    const override = new PrefabRegistry({ stone_platform: { id: 'stone_platform', category: 'platform', entity: { type: 'platform', tags: ['custom'] } } });
    expect(resolveEntity(ent({ prefab: 'stone_platform' }), override).entity.collisions).toEqual([]);      // the map's definition replaced the built-in
  });

  it('a broken expression in a prefab is an issue, not an exception', () => {
    const r2 = new PrefabRegistry({ boom: { id: 'boom', category: 'x', params: { a: { type: 'number', default: 1 } }, entity: { collision: { shape: { kind: 'box', w: '=a / 0' as never, h: 1 } } } } });
    const out = resolveEntity(ent({ prefab: 'boom' }), r2);
    expect(out.issues.map(i => i.code)).toContain('PREFAB_EXPR');
  });

  it('deepMerge: objects merge, arrays/scalars replace, undefined keeps the base', () => {
    expect(deepMerge({ a: { b: 1, c: 2 }, d: [1, 2] }, { a: { b: 9 }, d: [3], e: undefined })).toEqual({ a: { b: 9, c: 2 }, d: [3], e: undefined });
  });
});
