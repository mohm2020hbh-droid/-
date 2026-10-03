import type { LevelData } from '../LevelData';
import { rectPoints } from '../LevelData';

/**
 * PHYSICS_TEST — the "PhysicsTest scene" of Phase 18: a clean proving ground for every surface and interaction:
 * flat ground · bounce pad · slippery ice · 25° slope · wall · free-fall space. Used by the Physics Lab (tools to compare the
 * implementation with Pogostuck_Physics_Master.xlsx: measured jump cadence vs. the 150-tick video reference, etc.).
 */
export const PHYSICS_TEST: LevelData = {
  levelId: 'physics_test', worldId: 'world_1', name: 'Physics Test', theme: 'autumn_hills',
  startPosition: { x: 0, y: 0 }, goalPosition: { x: 86, y: 4.2 }, goal: { x: 86, y: 4.2, w: 2, h: 4 },
  difficulty: 1, parTimeSec: 60, killY: -30,
  bounds: { minX: -14, maxX: 92, minY: -12, maxY: 44 },
  progress: { path: [{ x: 0, y: 0 }, { x: 86, y: 4.2 }] },
  platforms: [
    { id: 'gA', kind: 'rock', x: 2, y: 0, w: 28, h: 8, seed: 3, depth: 6 },
    { id: 'gB', kind: 'rock', x: 31, y: 0, w: 6, h: 8, seed: 4, depth: 6 },
    { id: 'ice', kind: 'ice', x: 42, y: 0, w: 16, h: 3, taper: 0.9, seed: 5 },
    { id: 'gC', kind: 'rock', x: 53, y: 0, w: 6, h: 8, seed: 6, depth: 6 },
    { id: 'slope', kind: 'rock', x: 60.53, y: 2.1, w: 10, h: 3, angleDeg: 25, taper: 0.9, seed: 7 },
    { id: 'gD', kind: 'rock', x: 77, y: 4.2, w: 24, h: 8, seed: 8, depth: 6 },
  ],
  movingObjects: [],
  specialSurfaces: [{ id: 'pad', kind: 'bounce', x: 22, y: 0, w: 5, h: 1.6, taper: 0.8, seed: 9 }],
  obstacles: [
    { id: 'wallL', kind: 'cliff', pts: rectPoints(-16.5, 15, 5, 70), seed: 1, depth: 10 },
    { id: 'wallR', kind: 'cliff', pts: rectPoints(92.5, 15, 5, 70), seed: 2, depth: 10 },
  ],
  hazards: [],
  landmarks: [{ id: 'castle', type: 'castle', x: 40, y: 20, z: -95, scale: 1.4, seed: 5 }, { id: 'arch', type: 'arch_bridge', x: 10, y: 18, z: -60, scale: 1.3, seed: 6 }],
  hints: [],
  route: ['gA', 'gB', 'ice', 'gC', 'slope', 'gD'],
};
