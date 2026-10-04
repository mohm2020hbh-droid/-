import type { LevelData } from '../data/LevelData';
import type { PhysicsConfig } from './PhysicsConfig';
import { PhysicsWorld } from './PhysicsWorld';
import { type PogoState, createPlantedState, createPogoState } from './PogoState';
import { simulateLaunch } from './prediction';

/**
 * Level analysis "bot" — proves a level is solvable and measures how forgiving each hop is, using the REAL physics
 * (no separate approximation). It brute-forces a grid of (stick angle × spring load) launches from sampled standing
 * points and records where each ends up.
 *
 * Model of the player's freedom (LOCKED_SPEC §6): the angle is free within ±maxTiltDeg of the surface normal (the
 * stick can be rotated in the air and on the ground before the pogo launches) and the load can be anything between the
 * landing window's minimum and maximum (hold the charge for more). The window assumed for a standing node is the one
 * of a landing with v = 0 (L ∈ [40, 95]); falling faster only widens it, so the result is a conservative reachability test.
 */
export interface AnalysisOptions {
  angleStepDeg: number;
  /** Maximum |angle| from the surface normal (deg). */
  maxTiltDeg: number;
  /** Load grid step (L). */
  loadStep: number;
  /** Fractions along the top edge to start from. */
  samplesX: number[];
  /** Start ticks (moving-platform phases) to try. */
  phases: number[];
}

export const DEFAULT_ANALYSIS: AnalysisOptions = {
  angleStepDeg: 5,
  maxTiltDeg: 70,
  loadStep: 5,
  samplesX: [0.02, 0.15, 0.5, 0.85, 0.98],
  phases: [0, 180, 360, 540],
};

export interface Edge {
  from: string;
  to: string;
  /** Best number of (angle, load) cells landing on `to` from a single start node (target size). */
  cells: number;
  total: number;
  bestAngleDeg: number;
  bestLoad: number;
}

export interface Analysis {
  reached: string[];
  edges: Edge[];
  goalReached: boolean;
  unreachable: string[];
  hazardShare: Record<string, number>;
}

function gridAngles(maxDeg: number, stepDeg: number): number[] {
  const out: number[] = [];
  const m = Math.floor(maxDeg / stepDeg);
  for (let i = -m; i <= m; i++) out.push(i * stepDeg);
  return out;
}

function gridLoads(cfg: PhysicsConfig, step: number, lo: number, hi: number): number[] {
  const out: number[] = [];
  for (let l = lo; l <= hi + 1e-9; l += step) out.push(+l.toFixed(3));
  if (out[out.length - 1] < hi) out.push(hi);
  void cfg;
  return out;
}

/** All outcomes of launching from one planted node. */
export function launchOutcomes(world: PhysicsWorld, cfg: PhysicsConfig, start: PogoState, opts: AnalysisOptions) {
  const results: { angle: number; load: number; to?: string; goal: boolean; hazard: boolean }[] = [];
  const base = start.thetaN - cfg.normalAngleOffset;
  for (const a of gridAngles(opts.maxTiltDeg, opts.angleStepDeg)) {
    for (const load of gridLoads(cfg, opts.loadStep, start.loadMin, start.loadMax)) {
      const pr = simulateLaunch({ world, cfg }, start, { theta: base + a, load, maxTicks: 700, stride: 100 });
      results.push({
        angle: a, load,
        to: pr.landed ? world.colliders[pr.groundId].id : undefined,
        goal: pr.finished, hazard: pr.hazard || pr.fell,
      });
    }
  }
  return results;
}

export function analyzeLevel(level: LevelData, cfg: PhysicsConfig, partial: Partial<AnalysisOptions> = {}): Analysis {
  const opts = { ...DEFAULT_ANALYSIS, ...partial };
  const world = new PhysicsWorld(level, cfg.qPerMetre);
  const startState = createPogoState(world, cfg);
  const startId = world.colliders[startState.groundId].id;
  const idToIdx = new Map(world.colliders.map(c => [c.id, c.index]));
  const reached = new Set<string>([startId]);
  const queue = [startId];
  const edges: Edge[] = [];
  const hazardShare: Record<string, number> = {};
  let goalReached = false;
  const total = gridAngles(opts.maxTiltDeg, opts.angleStepDeg).length * gridLoads(cfg, opts.loadStep, startState.loadMin, startState.loadMax).length;

  while (queue.length) {
    const id = queue.shift()!;
    const idx = idToIdx.get(id)!;
    const c = world.colliders[idx];
    const best = new Map<string, { n: number; a: number; l: number }>();
    let hz = 0, tot = 0;
    for (const f of opts.samplesX) {
      for (const ph of (c.move ? opts.phases : [opts.phases[0]])) {
        const start = createPlantedState(world, cfg, idx, f, ph);
        const res = launchOutcomes(world, cfg, start, opts);
        const counts = new Map<string, { n: number; a: number; l: number }>();
        for (const r of res) {
          tot++; if (r.hazard) hz++;
          if (r.goal) goalReached = true;
          if (r.to) { const e = counts.get(r.to) ?? { n: 0, a: r.angle, l: r.load }; e.n++; counts.set(r.to, e); }
        }
        for (const [to, v] of counts) { const b = best.get(to); if (!b || v.n > b.n) best.set(to, v); }
      }
    }
    hazardShare[id] = tot ? hz / tot : 0;
    for (const [to, v] of best) {
      if (to === id) continue;
      edges.push({ from: id, to, cells: v.n, total, bestAngleDeg: v.a, bestLoad: v.l });
      if (!reached.has(to)) { reached.add(to); queue.push(to); }
    }
  }
  const all = world.solids.map(c => c.id).filter(i => !i.startsWith('wall') && !i.startsWith('ceil'));
  return { reached: [...reached], edges, goalReached, unreachable: all.filter(i => !reached.has(i)), hazardShare };
}
