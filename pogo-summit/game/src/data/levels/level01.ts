import type { LevelData } from '../LevelData';
import { rectPoints } from '../LevelData';

/**
 * LEVEL_01 — "First Steps" (World 1 · Green Hills).
 *
 * A guided three-tier climb: Tier 1 (rightwards) Starting Area → Platforms → precision hops → Bounce pad;
 * Tier 2 (leftwards) Hazard → Moving platform; Tier 3 (rightwards) Slippery ice → Low ceiling (air-control lesson) → Goal.
 *
 * Original layout (NOT a copy of any Pogostuck map). Pacing target: ≈ 1.25 s per hop (XLSX V-025, grade A derived).
 * Every route edge is verified by the physics bot in tests/solvability.test.ts using the real simulation.
 */
export const LEVEL_01: LevelData = {
  levelId: 'level_01',
  worldId: 'world_1',
  name: 'First Steps',
  theme: 'autumn_hills',
  startPosition: { x: -3, y: 0 },
  goalPosition: { x: 52, y: 41 },
  goal: { x: 52, y: 41, w: 2.2, h: 5 },
  difficulty: 2,
  parTimeSec: 110,
  killY: -26,
  bounds: { minX: -9, maxX: 80, minY: -12, maxY: 50 },
  progress: {
    path: [
      { x: -3, y: 0 }, { x: 16.5, y: 1.5 }, { x: 28.5, y: 3.5 }, { x: 40, y: 6 }, { x: 50.5, y: 8.5 }, { x: 58.5, y: 11.5 },
      { x: 66.5, y: 11.5 }, { x: 73.5, y: 15.2 }, { x: 61.5, y: 18 }, { x: 47, y: 21 }, { x: 31, y: 23 }, { x: 17, y: 25 },
      { x: 8, y: 28.5 }, { x: 19, y: 32 }, { x: 31, y: 34.7 }, { x: 41, y: 37.4 }, { x: 51, y: 41 },
    ],
  },
  platforms: [
    // ── Tier 1 · starting area + steps (rightwards) ───────────────────────────
    { id: 'start', kind: 'rock', x: 1, y: 0, w: 20, h: 10, decor: 'flowers', seed: 11, depth: 7 },
    { id: 'p1', kind: 'rock', x: 16.5, y: 1.5, w: 7, h: 4, decor: 'bushes', seed: 12 },
    { id: 'p2', kind: 'rock', x: 28.5, y: 3.5, w: 6.5, h: 4, decor: 'mushrooms', seed: 13 },
    { id: 'p3', kind: 'wood', x: 40, y: 6, w: 6, h: 1.2, taper: 0.9, seed: 14 },
    { id: 'p4', kind: 'rock', x: 50.5, y: 8.5, w: 5, h: 4, decor: 'flowers', seed: 15 },
    { id: 'p5', kind: 'rock', x: 58.5, y: 11.5, w: 4.5, h: 3.5, decor: 'sign', seed: 16 },
    // ── Tier 2 · bounce landing, hazard, moving platform (leftwards) ──────────
    { id: 'q1', kind: 'rock', x: 73.5, y: 15.2, w: 6, h: 2.2, decor: 'vines', seed: 21 },
    { id: 'q2', kind: 'rock', x: 61.5, y: 18, w: 5, h: 3.5, decor: 'bushes', seed: 22 },
    { id: 'q3', kind: 'rock', x: 47, y: 21, w: 13, h: 3.5, decor: 'fence', seed: 23 },
    { id: 'q5', kind: 'rock', x: 17, y: 25, w: 8, h: 4, decor: 'flowers', seed: 25 },
    // ── Tier 3 · ice, low ceiling, goal island (rightwards) ───────────────────
    { id: 'r1', kind: 'rock', x: 8, y: 28.5, w: 5.5, h: 3.5, decor: 'mushrooms', seed: 31 },
    { id: 'r2', kind: 'ice', x: 19, y: 32, w: 9, h: 2.5, taper: 0.8, seed: 32 },
    { id: 'r3', kind: 'rock', x: 31, y: 34.7, w: 5.2, h: 3, decor: 'bushes', seed: 33 },
    { id: 'r4', kind: 'rock', x: 41, y: 37.4, w: 4.8, h: 3.5, seed: 34 },
    { id: 'r5', kind: 'goal', x: 51, y: 41, w: 9, h: 5, decor: 'flowers', seed: 35 },
  ],
  movingObjects: [
    { id: 'q4', kind: 'wood', x: 31, y: 23, w: 6, h: 1.2, taper: 0.9, move: { dx: 4, dy: 0, period: 6, phase: 0 }, seed: 24 },
  ],
  specialSurfaces: [
    { id: 'pad1', kind: 'bounce', x: 66.5, y: 11.5, w: 5, h: 1.6, taper: 0.8, seed: 41 },
  ],
  obstacles: [
    { id: 'wallL', kind: 'cliff', pts: rectPoints(-11.5, 15, 5, 90), seed: 1, depth: 12 },
    { id: 'wallR', kind: 'cliff', pts: rectPoints(82.5, 15, 5, 90), seed: 2, depth: 12 },
    // low overhang above r3: forces a flatter, lower arc (air-control / angle lesson)
    { id: 'ceil1', kind: 'ceiling', pts: rectPoints(30.5, 39.3, 6.5, 2.2), seed: 3, depth: 6 },
  ],
  hazards: [
    { id: 'spikes1', kind: 'crystals', x: 47, y: 21, w: 3.2, h: 1.5 },
  ],
  landmarks: [
    { id: 'castle', type: 'castle', x: 34, y: 30, z: -95, scale: 1.4, seed: 5 },
    { id: 'arch', type: 'arch_bridge', x: 6, y: 26, z: -60, scale: 1.3, seed: 6 },
    { id: 'wf1', type: 'waterfall', x: -7.5, y: 18, z: -3, scale: 1.2, seed: 7 },
    { id: 'wf2', type: 'waterfall', x: 78, y: 30, z: -4, scale: 1, seed: 8 },
    { id: 'bridge', type: 'wood_bridge', x: 12, y: 8, z: -9, scale: 1, seed: 9 },
    { id: 'island1', type: 'floating_island', x: 20, y: 14, z: -14, scale: 0.9, seed: 10 },
    { id: 'tree1', type: 'big_tree', x: -6, y: 4, z: 6, scale: 1.6, seed: 11 },
  ],
  hints: [
    { id: 'h_charge', x: -3, y: 2, textKey: 'hint_charge', radius: 6 },
    { id: 'h_aim', x: 16.5, y: 3, textKey: 'hint_aim', radius: 5 },
    { id: 'h_bounce', x: 58.5, y: 13.5, textKey: 'hint_bounce', radius: 5 },
    { id: 'h_hazard', x: 56, y: 22, textKey: 'hint_hazard', radius: 6 },
    { id: 'h_ice', x: 19, y: 34, textKey: 'hint_ice', radius: 6 },
    { id: 'h_ceiling', x: 24, y: 36.5, textKey: 'hint_ceiling', radius: 5 },
  ],
  route: ['start', 'p1', 'p2', 'p3', 'p4', 'p5', 'pad1', 'q1', 'q2', 'q3', 'q4', 'q5', 'r1', 'r2', 'r3', 'r4', 'r5'],
};
