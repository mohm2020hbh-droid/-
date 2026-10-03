import type { LevelData } from '../data/LevelData';
import type { PhysicsConfig } from './PhysicsConfig';
import { PhysicsWorld } from './PhysicsWorld';
import { type PogoState, createPlantedState, createPogoState } from './PogoState';
import { simulateJump } from './prediction';
import { DEG } from './math';
import { stepPogo } from './PogoPhysicsController';
import type { SimEvent } from './events';

/**
 * Level analysis "bot" — proves a level is solvable and measures how forgiving each hop is, using the REAL physics
 * (no separate approximation). It brute-forces a grid of (stick angle × charge power) launches from sampled standing
 * points and records where each ends up. This is the level-design counterpart of XLSX "fidelity" rules: numbers come
 * from the simulation, never from assumptions.
 */
export interface AnalysisOptions {
  angleStepDeg: number;
  powerStep: number;
  /** Fractions along the top edge to start from. */
  samplesX: number[];
  /** Start ticks (moving-platform phases) to try. */
  phases: number[];
}

export const DEFAULT_ANALYSIS: AnalysisOptions = {
  angleStepDeg: 5,
  powerStep: 0.1,
  samplesX: [0.15, 0.5, 0.85],
  phases: [0, 180, 360, 540],
};

export interface Edge {
  from: string;
  to: string;
  /** Best number of (angle,power) cells landing on `to` from a single start node (target size). */
  cells: number;
  total: number;
  bestAngleDeg: number;
  bestPower: number;
}

export interface Analysis {
  reached: string[];
  edges: Edge[];
  goalReached: boolean;
  unreachable: string[];
  hazardShare: Record<string, number>;
}

function gridAngles(cfg: PhysicsConfig, stepDeg: number): number[] {
  const out: number[] = [];
  const m = Math.floor(cfg.tiltMaxAngle / stepDeg);
  for (let i = -m; i <= m; i++) out.push(i * stepDeg);
  return out;
}

function gridPowers(step: number): number[] {
  const out: number[] = [];
  for (let p = 0; p <= 1 + 1e-9; p += step) out.push(Math.min(1, +p.toFixed(4)));
  return out;
}

/** All outcomes of launching from one planted node. */
function launchOutcomes(world: PhysicsWorld, cfg: PhysicsConfig, start: PogoState, opts: AnalysisOptions) {
  const results: { angle: number; power: number; to?: string; goal: boolean; hazard: boolean; viaBounce?: string }[] = [];
  const nAngle = Math.atan2(start.gnx, start.gny);
  for (const a of gridAngles(cfg, opts.angleStepDeg)) {
    for (const p of gridPowers(opts.powerStep)) {
      const pr = simulateJump({ world, cfg }, start, nAngle + a * DEG, p, { maxTicks: 700, stride: 100 });
      const bounce = pr.events.find((e: SimEvent) => e.type === 'bounce');
      results.push({
        angle: a, power: p,
        to: pr.landed ? world.colliders[pr.groundId].id : undefined,
        goal: pr.finished, hazard: pr.hazard || pr.fell,
        viaBounce: bounce && bounce.collider !== undefined ? world.colliders[bounce.collider].id : undefined,
      });
    }
  }
  return results;
}

/** Outcomes when rebounding off a bounce pad with every stick angle (pad aim), starting from a typical drop. */
function bounceOutcomes(world: PhysicsWorld, cfg: PhysicsConfig, padIndex: number, opts: AnalysisOptions) {
  const results: { angle: number; power: number; to?: string; goal: boolean; hazard: boolean }[] = [];
  const pad = world.colliders[padIndex];
  const cx = (pad.minX + pad.maxX) / 2, top = pad.maxY;
  for (const f of opts.samplesX) {
    for (const a of gridAngles(cfg, opts.angleStepDeg)) {
      const s = createPogoState(world, cfg);
      Object.assign(s, { mode: 'AIR', groundId: -1, x: pad.minX + (pad.maxX - pad.minX) * f, y: top + 2.2, vx: 0, vy: -9, angle: a * DEG, omega: 0, tick: 0 });
      const ev: SimEvent[] = [];
      let landed: string | undefined, goal = false, hazard = false;
      for (let t = 0; t < 700; t++) {
        stepPogo({ world, cfg }, s, { tilt: 0, jumpHeld: false, boostPressed: false, pull: 0, cancel: false }, ev);
        if (ev.some(e => e.type === 'hazard' || e.type === 'fall')) { hazard = true; break; }
        if (s.mode === 'FINISHED') { goal = true; break; }
        if (s.mode === 'GROUNDED' && t > 5 && world.colliders[s.groundId] !== pad) { landed = world.colliders[s.groundId].id; break; }
        ev.length = 0;
      }
      results.push({ angle: a, power: 0, to: landed, goal, hazard });
    }
  }
  return results;
}

export function analyzeLevel(level: LevelData, cfg: PhysicsConfig, partial: Partial<AnalysisOptions> = {}): Analysis {
  const opts = { ...DEFAULT_ANALYSIS, ...partial };
  const world = new PhysicsWorld(level);
  const startState = createPogoState(world, cfg);
  const startId = world.colliders[startState.groundId].id;
  const idToIdx = new Map(world.colliders.map(c => [c.id, c.index]));
  const reached = new Set<string>([startId]);
  const queue = [startId];
  const edges: Edge[] = [];
  const hazardShare: Record<string, number> = {};
  let goalReached = false;

  const record = (from: string, counts: Map<string, { n: number; a: number; p: number }>, total: number) => {
    for (const [to, v] of counts) {
      if (to === from) continue;
      edges.push({ from, to, cells: v.n, total, bestAngleDeg: v.a, bestPower: v.p });
      if (!reached.has(to)) { reached.add(to); queue.push(to); }
    }
  };

  while (queue.length) {
    const id = queue.shift()!;
    const idx = idToIdx.get(id)!;
    const c = world.colliders[idx];
    if (c.surface === 'bounce') {
      const res = bounceOutcomes(world, cfg, idx, opts);
      const perTarget = new Map<string, { n: number; a: number; p: number }>();
      for (const r of res) if (r.to) { const e = perTarget.get(r.to) ?? { n: 0, a: r.angle, p: 0 }; e.n++; perTarget.set(r.to, e); }
      if (res.some(r => r.goal)) goalReached = true;
      record(id, perTarget, res.length);
      continue;
    }
    // best single start node per target
    const best = new Map<string, { n: number; a: number; p: number }>();
    let hz = 0, tot = 0;
    for (const f of opts.samplesX) {
      for (const ph of (c.move ? opts.phases : [opts.phases[0]])) {
        const start = createPlantedState(world, cfg, idx, f, ph);
        const res = launchOutcomes(world, cfg, start, opts);
        const counts = new Map<string, { n: number; a: number; p: number }>();
        for (const r of res) {
          tot++; if (r.hazard) hz++;
          if (r.goal) goalReached = true;
          if (r.to) { const e = counts.get(r.to) ?? { n: 0, a: r.angle, p: r.power }; e.n++; counts.set(r.to, e); }
          if (r.viaBounce) { const e = counts.get(r.viaBounce) ?? { n: 0, a: r.angle, p: r.power }; e.n++; counts.set(r.viaBounce, e); }
        }
        for (const [to, v] of counts) { const b = best.get(to); if (!b || v.n > b.n) best.set(to, v); }
      }
    }
    hazardShare[id] = tot ? hz / tot : 0;
    const total = gridAngles(cfg, opts.angleStepDeg).length * gridPowers(opts.powerStep).length;
    record(id, best, total);
  }
  const all = world.solids.map(c => c.id).filter(i => !i.startsWith('wall') && !i.startsWith('ceil'));
  return { reached: [...reached], edges, goalReached, unreachable: all.filter(i => !reached.has(i)), hazardShare };
}
