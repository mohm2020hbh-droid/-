import type { LevelData, PlatformDef } from '../src/data/LevelData';
import { PhysicsWorld } from '../src/sim/PhysicsWorld';
import { PogoPhysicsController } from '../src/sim/PogoPhysicsController';
import { type PhysicsConfig, createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { NEUTRAL_INPUT, type PogoInput } from '../src/sim/PogoState';

export function makeLevel(platforms: PlatformDef[], extra: Partial<LevelData> = {}): LevelData {
  return {
    levelId: 'T', worldId: 'test', name: 'test', theme: 'test',
    startPosition: { x: 0, y: 0 }, goalPosition: { x: 100, y: 100 },
    goal: { x: 100, y: 100, w: 2, h: 2 },
    platforms, obstacles: [], hazards: [], movingObjects: [], specialSurfaces: [],
    difficulty: 1, progress: { path: [{ x: 0, y: 0 }, { x: 100, y: 100 }] },
    bounds: { minX: -50, maxX: 150, minY: -40, maxY: 150 }, killY: -60, parTimeSec: 60, landmarks: [], hints: [],
    ...extra,
  };
}

export const flatGround = (over: Partial<PlatformDef> = {}): PlatformDef =>
  ({ id: 'ground', kind: 'rock', x: 0, y: 0, w: 60, h: 6, ...over });

export function setup(level: LevelData, cfgOver: Partial<PhysicsConfig> = {}) {
  const cfg = createPhysicsConfig(cfgOver);
  const world = new PhysicsWorld(level, cfg.qPerMetre);
  const pogo = new PogoPhysicsController(world, cfg);
  return { cfg, world, pogo };
}

export const input = (o: Partial<PogoInput> = {}): PogoInput => ({ ...NEUTRAL_INPUT, ...o });

/** Run N ticks with the same input; returns all events. */
export function run(pogo: PogoPhysicsController, n: number, inp: PogoInput = NEUTRAL_INPUT) {
  const all: ReturnType<PogoPhysicsController['step']> = [];
  for (let i = 0; i < n; i++) all.push(...pogo.step(inp));
  return all;
}
