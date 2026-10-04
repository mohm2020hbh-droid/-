/** Hand-authored fixtures for the map tests (own layouts; nothing from the legacy kit). */
import type { MapDocument } from '../../src/map/schema';

/** A small but complete map as JSON text: start → stepping stone → mover → ice → goal island. */
export function smallMap(): Record<string, unknown> {
  return {
    format: 'pogo-summit.map', formatVersion: 2,
    manifest: {
      id: 'demo_small', name: 'Demo Small', author: 'tests', description: 'tiny test map', version: '1.0.0', difficulty: 2, estimatedTimeSec: 40,
      theme: 'autumn_hills', mapSize: { width: 56, height: 42 }, checkpointCount: 1, tags: ['demo'],
      requirements: { minFormatVersion: 2, capabilities: ['move'], physics: 'locked-spec-1' },
    },
    world: { bounds: { minX: -12, maxX: 60, minY: -14, maxY: 30 }, killY: -22 },
    theme: { ref: 'autumn_hills' },
    spawn: { id: 'spawn', position: { x: 0, y: 0 } },
    finish: { zones: [{ id: 'finish', position: { x: 50, y: 8 }, shape: { kind: 'box', w: 2.2, h: 4 } }] },
    checkpoints: [{ id: 'cp0', order: 0, name: 'Half way', region: { kind: 'box', x: 25, y: 5, w: 8, h: 10 }, respawn: { x: 24, y: 3.5 }, progress: 50 }],
    progress: { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 12, y: 2 }, { x: 24, y: 4 }, { x: 36, y: 6 }, { x: 50, y: 8 }] }] },
    splits: { splits: [{ id: 's0', name: 'Half way', checkpoint: 'cp0', parSec: 18 }], targets: { gold: 30, silver: 40, bronze: 55 } },
    entities: [
      { id: 'start', type: 'platform', prefab: 'stone_platform', position: { x: 0, y: 0 }, properties: { width: 10, thickness: 6 } },
      { id: 'step1', type: 'platform', prefab: 'stone_platform', position: { x: 12, y: 2 }, properties: { width: 6, thickness: 4 } },
      { id: 'mover', type: 'moving', prefab: 'moving_platform', position: { x: 24, y: 4 }, properties: { width: 6, ampX: 3, period: 6 } },
      { id: 'ice1', type: 'platform', prefab: 'ice_platform', position: { x: 36, y: 6 }, properties: { width: 9 } },
      { id: 'goalrock', type: 'platform', prefab: 'stone_platform', position: { x: 50, y: 7 }, properties: { width: 9, thickness: 4 } },
      { id: 'spikes', type: 'hazard', prefab: 'spike', position: { x: 18, y: -6 } },
      { id: 'tree1', type: 'decor', prefab: 'tree_cluster', position: { x: -6, y: 0, z: -4 } },
      { id: 'ridge', type: 'background', prefab: 'background_object', position: { x: 20, y: 10, z: -120 } },
    ],
    regions: [{ id: 'killfloor', type: 'kill', shape: { kind: 'box', x: 24, y: -18, w: 90, h: 6 }, enter: [{ op: 'kill' }] }],
  };
}
export const smallMapJson = (): string => JSON.stringify(smallMap());
export type SmallMap = ReturnType<typeof smallMap> & Partial<MapDocument>;

import { newMapDocument } from '../../src/map/MapLoader';
import type { MapEntity } from '../../src/map/schema';

/** Empty, valid-structure doc with a spawn on a start platform and a finish zone; callers add what they test. */
export function baseDoc(id = 't', extra: (d: MapDocument) => void = () => {}): MapDocument {
  const d = newMapDocument(id);
  d.manifest.name = id;
  d.world.bounds = { minX: -20, maxX: 120, minY: -20, maxY: 60 };
  d.world.killY = -40;
  d.spawn = { id: 'spawn', position: { x: 0, y: 0 } };
  d.finish = { zones: [{ id: 'finish', position: { x: 100, y: 5 }, shape: { kind: 'box', w: 3, h: 5 } }] };
  d.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 100, y: 5 }] }] };
  d.entities = [
    { id: 'start', type: 'platform', prefab: 'stone_platform', position: { x: 0, y: 0 }, properties: { width: 12, thickness: 6 } },
    { id: 'end', type: 'platform', prefab: 'stone_platform', position: { x: 100, y: 3 }, properties: { width: 12, thickness: 6 } },
  ];
  extra(d);
  return d;
}

export const stone = (id: string, x: number, y: number, width = 6, thickness = 3): MapEntity =>
  ({ id, type: 'platform', prefab: 'stone_platform', position: { x, y }, properties: { width, thickness } });

/** A long flat corridor of touching ground tiles with walls at both ends: the pogo can hop along it for a long way. */
export function corridorMap(opts: { tiles: number; tileW?: number; cell?: number; streaming?: Partial<MapDocument['chunks']> } = { tiles: 10 }): MapDocument {
  const tileW = opts.tileW ?? 20;
  const d = baseDoc('corridor');
  const total = opts.tiles * tileW;
  d.world.bounds = { minX: -30, maxX: total + 30, minY: -20, maxY: 60 };
  d.finish = { zones: [{ id: 'finish', position: { x: total - 5, y: 3 }, shape: { kind: 'box', w: 3, h: 5 } }] };
  d.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: total - 5, y: 0 }] }] };
  d.entities = [];
  for (let i = 0; i < opts.tiles; i++) d.entities.push({ id: `t${i}`, type: 'platform', prefab: 'stone_platform', position: { x: i * tileW + tileW / 2 - tileW / 2 + 0, y: 0 }, properties: { width: tileW - 0.0001, thickness: 6, taper: 1 } });
  // hold the left end
  d.entities.push({ id: 'wallL', type: 'wall', prefab: 'wall', position: { x: -4, y: 10 }, properties: { width: 4, height: 40 } });
  d.entities.push({ id: 'wallR', type: 'wall', prefab: 'wall', position: { x: total + 4, y: 10 }, properties: { width: 4, height: 40 } });
  d.chunks = { ...d.chunks, mode: 'auto-grid', cell: { w: opts.cell ?? 24, h: 24 }, activateRadius: 40, loadRadius: 56, unloadRadius: 80, ...(opts.streaming ?? {}) };
  return d;
}

/** Deterministic pseudo-random generator (mulberry32). */
export function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => { a = (a + 0x6d2b79f5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

/** Large procedural map: `rows` strips of platforms and decor along `length` metres (own layout, not a copy of anything). */
export function largeMap(o: { length: number; platformsPer100m?: number; decorPer100m?: number; seed?: number } = { length: 4000 }): MapDocument {
  const rand = rng(o.seed ?? 7);
  const d = baseDoc('large');
  const L = o.length;
  d.world.bounds = { minX: -30, maxX: L + 30, minY: -30, maxY: 120 };
  d.world.killY = -60;
  d.finish = { zones: [{ id: 'finish', position: { x: L, y: 5 }, shape: { kind: 'box', w: 3, h: 5 } }] };
  d.progress = { routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: L, y: 5 }] }] };
  d.entities = [{ id: 'start', type: 'platform', prefab: 'stone_platform', position: { x: 0, y: 0 }, properties: { width: 12, thickness: 6 } }];
  const nPlat = Math.round((L / 100) * (o.platformsPer100m ?? 30));
  const nDecor = Math.round((L / 100) * (o.decorPer100m ?? 120));
  for (let i = 0; i < nPlat; i++) {
    const x = rand() * L, y = 1 + rand() * 80;
    d.entities.push({ id: `p${i}`, type: 'platform', prefab: i % 5 === 0 ? 'ice_platform' : 'stone_platform', position: { x, y }, properties: { width: 3 + rand() * 6, thickness: 1.5 + rand() * 2 } });
  }
  for (let i = 0; i < nDecor; i++) d.entities.push({ id: `d${i}`, type: 'decor', prefab: i % 2 ? 'tree_cluster' : 'rock_cluster', position: { x: rand() * L, y: rand() * 90, z: -2 - rand() * 20 } });
  d.entities.push({ id: 'ground', type: 'platform', prefab: 'stone_platform', position: { x: L, y: 3 }, properties: { width: 12, thickness: 6 } });
  return d;
}

import { PogoPhysicsController } from '../../src/sim/PogoPhysicsController';
import { createPhysicsConfig } from '../../src/sim/PhysicsConfig';
import { NEUTRAL_INPUT, type PogoInput } from '../../src/sim/PogoState';
import type { SimEvent } from '../../src/sim/events';
import type { MapEvent } from '../../src/map/schema';
import { MapRuntime, type MapRuntimeOptions } from '../../src/map/MapRuntime';

/** Runtime + pogo + a `step(n, input)` helper that runs the hooks exactly like Game.tick does. */
export function boot(doc: MapDocument, opts: MapRuntimeOptions = {}) {
  const cfg = createPhysicsConfig();
  const rt = new MapRuntime(doc, { cfg, ...opts });
  const pogo = new PogoPhysicsController(rt.world, cfg);
  const mapEvents: MapEvent[] = [];
  const simEvents: SimEvent[] = [];
  const step = (n = 1, inp: PogoInput | ((i: number) => PogoInput) = NEUTRAL_INPUT) => {
    for (let i = 0; i < n; i++) {
      rt.beforeStep(pogo.state);
      const ev = pogo.step(typeof inp === 'function' ? inp(i) : inp);
      for (const e of ev) simEvents.push({ ...e });
      for (const m of rt.afterStep(pogo.state, ev)) mapEvents.push({ ...m });
    }
  };
  return { cfg, rt, pogo, state: () => pogo.state, step, mapEvents, simEvents, idx: (id: string) => rt.world.indexOfId(id) };
}
