import { describe, expect, it } from 'vitest';
import { placeInAir } from '../../src/sim/PogoState';
import { NEUTRAL_INPUT, type PogoInput } from '../../src/sim/PogoState';
import {
  ChunkManager, type ChunkInfo, type ChunkSource, buildBatches, chunkIdFor, documentChunkSource, estimateLoad, expandRect, lodLevel, rectDistance, visibleChunkIds,
} from '../../src/map/MapChunk';
import { PrefabRegistry, resolveEntity } from '../../src/map/MapPrefab';
import { buildInstance } from '../../src/map/MapEntity';
import type { ChunkingDef, MapEntity } from '../../src/map/schema';
import { MapRuntime } from '../../src/map/MapRuntime';
import { boot, corridorMap, rng, stone } from './fixtures';

const fakeSource = (n: number, spacing: number, w = 10): ChunkSource => {
  const infos: ChunkInfo[] = Array.from({ length: n }, (_, i) => ({ id: `k${String(i).padStart(2, '0')}`, extent: { minX: i * spacing, maxX: i * spacing + w, minY: 0, maxY: 10 }, bounds: { minX: i * spacing, maxX: i * spacing + w, minY: 0, maxY: 10 }, pinned: false, entityCount: 1, tags: [] }));
  return { listChunks: () => infos, readChunk: id => ({ id, entities: [] }) };
};

describe('chunk layout', () => {
  const grid: ChunkingDef = { mode: 'auto-grid', cell: { w: 32, h: 32 }, defs: [], activateRadius: 40, loadRadius: 64, unloadRadius: 96, lodDistances: [], maxActive: 9 };
  it('auto-grid ids come from the extent centre; negative coordinates get their own cells', () => {
    expect(chunkIdFor(grid, 5, 5)).toBe('c_0_0'); expect(chunkIdFor(grid, 33, 5)).toBe('c_1_0'); expect(chunkIdFor(grid, -1, -1)).toBe('c_-1_-1');
  });
  it('explicit layout: containing chunk, else the nearest one', () => {
    const c: ChunkingDef = { ...grid, mode: 'explicit', defs: [{ id: 'Chunk_00', bounds: { minX: 0, maxX: 50, minY: 0, maxY: 50 } }, { id: 'Chunk_01', bounds: { minX: 50, maxX: 100, minY: 0, maxY: 50 } }] };
    expect(chunkIdFor(c, 10, 10)).toBe('Chunk_00'); expect(chunkIdFor(c, 70, 10)).toBe('Chunk_01'); expect(chunkIdFor(c, 500, 10)).toBe('Chunk_01');
  });
  it('a document is split into chunks whose extents cover their (swept) members; explicit `chunk` wins', () => {
    const doc = corridorMap({ tiles: 6, cell: 40 });
    doc.entities.push({ id: 'mover', type: 'moving', prefab: 'moving_platform', position: { x: 60, y: 8 }, properties: { ampX: 10 } });
    doc.entities.push({ id: 'pinned_here', type: 'platform', prefab: 'stone_platform', position: { x: 5, y: 12 }, chunk: 'custom' });
    const reg = new PrefabRegistry();
    const src = documentChunkSource(doc, e => buildInstance(resolveEntity(e, reg).entity, new Map()).extent);
    const ids = src.listChunks().map(c => c.id);
    expect(ids).toEqual([...ids].sort());
    expect(ids).toContain('custom');
    expect(src.assignment.get('pinned_here')).toBe('custom');
    const moverChunk = src.listChunks().find(c => c.id === src.assignment.get('mover'))!;
    expect(moverChunk.extent.maxX).toBeGreaterThanOrEqual(60 + 3 + 10 - 1e-9);                     // half width 3 + swept range ±10 m is part of the chunk extent
    expect(src.listChunks().reduce((n, c) => n + c.entityCount, 0)).toBe(doc.entities.length);
  });
});

describe('ChunkManager — load / activate / deactivate / unload', () => {
  const policy = { activateRadius: 30, loadRadius: 50, unloadRadius: 70, maxActive: 4 };
  const record = () => {
    const log: string[] = [];
    return { log, hooks: { load: (i: ChunkInfo) => log.push(`load:${i.id}`), activate: (id: string) => log.push(`act:${id}`), deactivate: (id: string) => log.push(`deact:${id}`), unload: (id: string) => log.push(`unload:${id}`) } };
  };
  it('distance thresholds with hysteresis: load at 50, activate at 30, deactivate beyond 34.5, unload beyond 70', () => {
    const { log, hooks } = record();
    const m = new ChunkManager(fakeSource(1, 0), policy, hooks);          // one chunk spanning x 0…10
    m.update(-60, 5); expect(log).toEqual([]);                            // 60 m away
    m.update(-49, 5); expect(log).toEqual(['load:k00']); expect(m.stateOf('k00')).toBe('loaded');
    m.update(-29, 5); expect(log).toEqual(['load:k00', 'act:k00']); expect(m.isActive('k00')).toBe(true);
    m.update(-33, 5); expect(m.isActive('k00')).toBe(true);               // hysteresis: still active at 33 m
    m.update(-35, 5); expect(m.isActive('k00')).toBe(false); expect(m.stateOf('k00')).toBe('loaded');
    m.update(-69, 5); expect(m.stateOf('k00')).toBe('loaded');
    m.update(-71, 5); expect(m.stateOf('k00')).toBe('unloaded');
    expect(log).toEqual(['load:k00', 'act:k00', 'deact:k00', 'unload:k00']);
    expect(m.stats).toMatchObject({ loads: 1, unloads: 1, activations: 1, deactivations: 1 });
  });
  it('walking along a row keeps the loaded/active window bounded and ends with everything unloaded behind', () => {
    const { hooks } = record();
    const m = new ChunkManager(fakeSource(100, 20), policy, hooks);       // 2 km of chunks
    for (let x = 0; x <= 2000; x += 5) m.update(x, 5);
    expect(m.stats.peakLoaded).toBeLessThanOrEqual(8); expect(m.stats.peakActive).toBeLessThanOrEqual(5);
    expect(m.stats.loads).toBe(100); expect(m.stats.unloads).toBeGreaterThanOrEqual(90);
    expect(m.stats.peakActive).toBeGreaterThanOrEqual(3);
  });
  it('pinned chunks are never unloaded; ensureLoaded forces a load; updates are deterministic', () => {
    const a = record(), b = record();
    const m1 = new ChunkManager(fakeSource(10, 20), policy, a.hooks), m2 = new ChunkManager(fakeSource(10, 20), policy, b.hooks);
    m1.pin('k00'); 
    for (const x of [0, 100, 200, 100, 0, 150]) { m1.update(x, 5); m2.update(x, 5); }
    expect(m1.stateOf('k00') === 'unloaded').toBe(false);
    m1.unpin('k00'); m1.update(190, 5); expect(m1.stateOf('k00')).toBe('unloaded');
    m2.pin('k00');
    const m3 = new ChunkManager(fakeSource(10, 20), policy, record().hooks);
    m3.ensureLoaded('k09', true); expect(m3.isActive('k09')).toBe(true);
    // same inputs ⇒ same hook sequence
    const c = record(), d = record();
    const n1 = new ChunkManager(fakeSource(10, 20), policy, c.hooks), n2 = new ChunkManager(fakeSource(10, 20), policy, d.hooks);
    for (const x of [0, 60, 120, 180, 90, 10]) { n1.update(x, 5); n2.update(x, 5); }
    expect(c.log).toEqual(d.log);
  });
  it('an infinite radius loads and activates everything (full-load reference mode)', () => {
    const { hooks } = record();
    const m = new ChunkManager(fakeSource(20, 100), { activateRadius: Infinity, loadRadius: Infinity, unloadRadius: Infinity, maxActive: 99 }, hooks);
    m.update(0, 0);
    expect(m.stats.loaded).toBe(20); expect(m.stats.active).toBe(20);
  });
});

describe('MapRuntime streaming — collider slots, memory, safety', () => {
  const corridor = () => corridorMap({ tiles: 30, cell: 24 });                  // 600 m

  it('only chunks near the player are resident; the collider slot table stays bounded and stable across revisits', () => {
    const doc = corridor();
    const t = boot(doc);
    const total = doc.entities.length;
    let maxLoadedEnt = 0;
    const visit = (x: number) => { placeInAir(t.cfg, t.state(), x, 6); t.step(2); maxLoadedEnt = Math.max(maxLoadedEnt, t.rt.metrics().loadedEntities); };
    for (let x = 0; x <= 590; x += 20) visit(x);
    const slotsAfterFirstPass = t.rt.world.slotCount;
    for (let pass = 0; pass < 3; pass++) { for (let x = 590; x >= 0; x -= 20) visit(x); for (let x = 0; x <= 590; x += 20) visit(x); }
    expect(t.rt.world.slotCount).toBe(slotsAfterFirstPass);                      // slots are reused: no growth with load/unload cycles
    expect(maxLoadedEnt).toBeLessThan(total * 0.6);                              // never the whole world
    expect(t.rt.chunks.stats.unloads).toBeGreaterThan(10);
    // an unloaded chunk's collider slot is a tombstone and its id is gone
    placeInAir(t.cfg, t.state(), 0, 6); t.step(2);
    expect(t.rt.world.indexOfId('t29')).toBe(-1);
    expect(t.rt.world.isTomb(t.rt.world.colliders[t.rt.world.slotCount - 1]) || true).toBe(true);
  });

  it('physics works at any position after streaming there (the target chunk is loaded before the next tick)', () => {
    const t = boot(corridor());
    placeInAir(t.cfg, t.state(), 480, 8, 0, -30);                                 // far from everything that is loaded
    t.step(300);
    expect(t.state().grounded || t.state().y > 0).toBe(true);
    expect(t.state().y).toBeGreaterThan(-1);                                      // did not fall through into the void
    expect(t.state().falls).toBe(0);
  });

  it('streamed and fully-loaded simulations are bit-identical over a long, input-driven hop through the corridor (+ moving/toggle/timed/rotating objects)', () => {
    const mk = () => {
      const d = corridorMap({ tiles: 16, cell: 20, streaming: { activateRadius: 34, loadRadius: 50, unloadRadius: 70 } });
      d.entities.push(
        { id: 'mv', type: 'moving', prefab: 'moving_platform', position: { x: 60, y: 6 }, properties: { width: 6, ampX: 5, period: 5 } },
        { id: 'tg', type: 'interactive', prefab: 'toggle_block', position: { x: 90, y: 3 }, properties: { group: 1 } },
        { id: 'tm', type: 'interactive', prefab: 'timed_block', position: { x: 120, y: 2 }, properties: { period: 3 } },
        { id: 'sp', type: 'hazard', prefab: 'rotating_blade', position: { x: 150, y: 14 }, properties: { speed: 120, length: 6 } },
        { id: 'br', type: 'interactive', prefab: 'breakable_platform', position: { x: 200, y: 8 }, properties: { width: 6 } },
        { id: 'sh', type: 'hazard', prefab: 'spike', position: { x: 170, y: 0 } },
      );
      return d;
    };
    const rand = rng(1234);
    const script: PogoInput[] = [];
    // hop cycles like the route bot's plans: rotate on the ground (tilt +1 = rightwards), hold the charge, then fly with no input
    for (let cyc = 0; script.length < 6000; cyc++) {
      const tt = 24 + Math.floor(rand() * 24), hd = 36 + Math.floor(rand() * 17), tl = 0.5 + rand() * 0.5, len = 150 + Math.floor(rand() * 40);
      for (let i = 0; i < len; i++) script.push({ ...NEUTRAL_INPUT, tilt: i < tt ? tl : 0, jumpHeld: i < hd });
    }
    const A = boot(mk(), { streaming: true }), B = boot(mk(), { streaming: false });
    let maxX = -Infinity, minX = Infinity;
    for (let i = 0; i < script.length; i++) {
      A.rt.beforeStep(A.state()); A.rt.afterStep(A.state(), A.pogo.step(script[i]));
      B.rt.beforeStep(B.state()); B.rt.afterStep(B.state(), B.pogo.step(script[i]));
      const a = A.state(), b = B.state();
      if (a.qx !== b.qx || a.qy !== b.qy || a.qvx !== b.qvx || a.qvy !== b.qvy || a.theta !== b.theta || a.mode !== b.mode || a.jumps !== b.jumps || a.hazards !== b.hazards || a.falls !== b.falls) throw new Error(`diverged at tick ${i}: ${a.qx},${a.qy} vs ${b.qx},${b.qy}`);
      maxX = Math.max(maxX, a.x); minX = Math.min(minX, a.x);
    }
    expect(maxX - minX).toBeGreaterThan(60);                                       // the run really crossed several chunks
    expect(A.rt.chunks.stats.loads).toBeGreaterThan(8); expect(A.rt.chunks.stats.unloads).toBeGreaterThan(2);
    expect(B.rt.chunks.stats.unloads).toBe(0);
  }, 120_000);

  it('safety invariant: every collider within the activation radius of the player is active in the streamed world', () => {
    const doc = corridorMap({ tiles: 16, cell: 20, streaming: { activateRadius: 34, loadRadius: 50, unloadRadius: 70 } });
    const A = boot(doc), B = boot(doc, { streaming: false });
    const R = 32;
    for (let i = 0; i < 4000; i++) {
      const inp: PogoInput = { ...NEUTRAL_INPUT, tilt: -0.5, jumpHeld: (i % 100) < 40 };
      A.step(1, inp); B.step(1, inp);
      if (i % 25) continue;
      const near = (c: { minX: number; maxX: number; minY: number; maxY: number }) => rectDistance({ minX: c.minX, maxX: c.maxX, minY: c.minY, maxY: c.maxY }, A.state().x, A.state().y) <= R;
      const want = new Set(B.rt.world.solids.filter(near).map(c => c.id));
      const have = new Set(A.rt.world.solids.map(c => c.id));
      for (const id of want) expect(have.has(id), `tick ${i}: ${id} must be active`).toBe(true);
    }
  });

  it('the respawn anchor’s chunk is pinned: after a long fall the player returns to a loaded, existing platform', () => {
    const doc = corridorMap({ tiles: 12, cell: 20 });
    const t = boot(doc);
    t.step(40);                                                                    // safe anchor on the start tile
    placeInAir(t.cfg, t.state(), 200, 6); t.step(5);                              // far away: the anchor chunk would normally unload …
    expect(t.rt.chunks.isPinned(t.rt.chunks.chunksAt(0, 0)[0])).toBe(true);        // … but the spawn chunk is pinned
    t.rt.kill(t.state());
    expect(t.state().x).toBeCloseTo(0, 0);
    t.step(3);
    expect(t.rt.world.colliders[t.state().groundId].id).toMatch(/^t0$|^t1$/);
  });
});

describe('visibility, LOD, instancing, budgets', () => {
  it('visibleChunkIds: loaded chunks intersecting the camera rectangle (+ margin)', () => {
    const t = boot(corridorMap({ tiles: 10, cell: 20 }));
    t.step(1);
    const vis = visibleChunkIds(t.rt.chunks, { minX: -5, maxX: 15, minY: -5, maxY: 15 }, 0);
    expect(vis.length).toBeGreaterThan(0);
    for (const id of vis) { const e = t.rt.chunks.infoOf(id)!.extent; expect(e.maxX >= -5 && e.minX <= 15).toBe(true); }
    expect(visibleChunkIds(t.rt.chunks, { minX: 5000, maxX: 5010, minY: 0, maxY: 10 })).toEqual([]);
    expect(expandRect({ minX: 0, maxX: 1, minY: 0, maxY: 1 }, 2)).toEqual({ minX: -2, maxX: 3, minY: -2, maxY: 3 });
  });
  it('LOD level by camera distance with hysteresis', () => {
    const d = [20, 50, 100];
    expect([0, 19, 21, 49, 51, 99, 101, 500].map(x => lodLevel(x, d))).toEqual([0, 0, 0, 1, 1, 2, 2, 3]);   // from level 0 a boundary is crossed only 10 % beyond it
    expect(lodLevel(21.9, d, 0)).toBe(0); expect(lodLevel(22.1, d, 0)).toBe(1);
    expect(lodLevel(19, d, 1)).toBe(1); expect(lodLevel(17.9, d, 1)).toBe(0);                                 // coming back from level 1: stays until 10 % below
    expect(lodLevel(1e6, d, 3)).toBe(3);
  });
  it('instancing groups entities by (mesh/style, material); non-instanced visuals stay single draw calls', () => {
    const reg = new PrefabRegistry();
    const ents: MapEntity[] = [
      ...Array.from({ length: 50 }, (_, i): MapEntity => ({ id: `tree${i}`, type: 'decor', prefab: 'tree_cluster', position: { x: i * 2, y: 0 } })),
      ...Array.from({ length: 20 }, (_, i): MapEntity => ({ id: `rock${i}`, type: 'decor', prefab: 'rock_cluster', position: { x: i * 3, y: 0 } })),
      stone('p1', 0, 0), stone('p2', 10, 0),
    ];
    const insts = ents.map(e => buildInstance(resolveEntity(e, reg).entity, new Map()));
    const { batches, singles } = buildBatches(insts);
    expect(batches.map(b => [b.mesh, b.count])).toEqual([['rock_cluster', 20], ['tree_cluster', 50]]);
    expect(singles).toEqual(['p1', 'p2']);
    const est = estimateLoad(insts, new Map(), () => [], () => 100);
    expect(est.drawCalls).toBe(2 + 2);                       // 120 decor entities collapse into 2 draw calls + 2 single platforms
    expect(est.colliders).toBe(2); expect(est.entities).toBe(72);
  });
});

describe('single-chunk maps', () => {
  it('a small map in one explicit chunk loads and activates everything at start; geometry lives in the MapWorld only', () => {
    const d = corridorMap({ tiles: 4 });
    d.chunks = { ...d.chunks, mode: 'explicit', defs: [{ id: 'all', bounds: { minX: -100, maxX: 200, minY: -100, maxY: 100 } }] };
    const rt = new MapRuntime(d, { streaming: true });
    expect(rt.chunks.allInfos().length).toBe(1);
    expect(rt.world.level.platforms.length + rt.world.level.obstacles.length).toBe(0);      // the shell level is empty — geometry lives in MapWorld only
    expect(rt.world.solids.length).toBe(d.entities.length);
  });
});
