import type { LevelData } from '../data/LevelData';
import type { PhysicsConfig } from './PhysicsConfig';
import { PhysicsWorld } from './PhysicsWorld';
import { NEUTRAL_INPUT, type PogoInput, type PogoState, createPlantedState, createPogoState, tipCenterQ } from './PogoState';
import { stepPogo } from './PogoPhysicsController';
import type { SimEvent } from './events';

/**
 * routeBot — plays a designed route END TO END on the real simulation, through the same input path a player uses
 * (a turn input and the charge hold, nothing else), so the result is a replayable per-tick input script.
 *
 * The pogo hops by itself (LOCKED_SPEC E8), so a "hop" here is a plan the bot applies from a landing:
 *   idle      number of extra idle hops first (waits for a moving platform)
 *   tiltG     turn input during the first `tt` ticks of the plan (rotates the stick on the ground)
 *   hold      charge held for the first `hd` ticks (longer hold = more load, up to L_max)
 *   tiltA     turn input while airborne (steers the landing angle, which the next hop starts from)
 * A depth-first search with a small branching factor picks, for every target platform, the plans that land on it with
 * the largest margin from the edges, backtracking when a later hop becomes impossible.
 */
export interface Hop { from: string; to: string; idle: number; tiltG: number; tt: number; hd: number; tiltA: number; ticks: number }
export interface RoutePlan {
  success: boolean;
  hops: Hop[];
  /** Per-tick input script for the whole run. */
  script: PogoInput[];
  ticks: number;
  jumps: number;
  failedAt?: string;
  expansions: number;
}

interface Candidate { hop: Hop; end: PogoState; script: PogoInput[]; score: number; finished: boolean; landedId: string }

const TILTS_G = [-1, -0.5, 0.5, 1];
const TT = [15, 30, 45];
const HD = [0, 20, 28, 36, 44, 52];
const TILTS_A = [-1, -0.5, 0, 0.5, 1];

export function runHop(world: PhysicsWorld, cfg: PhysicsConfig, from: PogoState, p: Omit<Hop, 'from' | 'to' | 'ticks'>, maxTicks = 900) {
  const s: PogoState = { ...from };
  const ctx = { world, cfg };
  const ev: SimEvent[] = [];
  const script: PogoInput[] = [];
  let hazard = false, finished = false;
  const push = (i: PogoInput): boolean => {
    script.push(i); ev.length = 0; stepPogo(ctx, s, i, ev);
    for (const e of ev) { if (e.type === 'hazard' || e.type === 'fall') hazard = true; }
    if (s.mode === 'FINISHED') finished = true;
    return ev.some(e => e.type === 'land');
  };
  // idle hops: neutral input until `idle` landings happened
  let landings = 0, guard = 0;
  while (landings < p.idle && guard++ < 3000 && !hazard && !finished) if (push({ ...NEUTRAL_INPUT })) landings++;
  if (hazard) return { s, script, hazard, finished, landed: false };
  let airborne = false, landed = false;
  for (let t = 0; t < maxTicks && !hazard && !finished; t++) {
    const inp: PogoInput = { ...NEUTRAL_INPUT };
    if (s.grounded && !airborne) { inp.tilt = t < p.tt ? p.tiltG : 0; inp.jumpHeld = t < p.hd; }
    else { airborne = true; inp.tilt = p.tiltA; }
    const land = push(inp);
    if (!s.grounded) airborne = true;
    if (airborne && land && s.grounded) { landed = true; break; }
  }
  return { s, script, hazard, finished, landed };
}

/** Distance (m) of the landing tip from the nearest end of the collider's top edge (bigger = safer landing). */
function margin(world: PhysicsWorld, cfg: PhysicsConfig, s: PogoState): number {
  const c = world.colliders[s.groundId];
  if (!c) return -1;
  const { pts, eny } = c.qpoly;
  let best = 0;
  for (let i = 1; i < pts.length; i++) if (eny[i] > eny[best]) best = i;
  const a = pts[best], b = pts[(best + 1) % pts.length];
  const lo = Math.min(a.x, b.x), hi = Math.max(a.x, b.x);
  const tip = { x: 0, y: 0 };
  tipCenterQ(cfg, s, tip);
  const off = { x: 0, y: 0, vx: 0, vy: 0 };
  world.offsetAtQ(c, s.tick, off);
  const x = tip.x - off.x;
  return Math.min(x - lo, hi - x) / cfg.qPerMetre;
}

export function playRoute(level: LevelData, cfg: PhysicsConfig, opts: { maxExpansions?: number; idleMax?: number; log?: (msg: string) => void } = {}): RoutePlan {
  const { maxExpansions = 400, idleMax = 8, log } = opts;
  const route = level.route ?? [];
  const world = new PhysicsWorld(level, cfg.qPerMetre);
  const byId = new Map(world.colliders.map(c => [c.id, c.index]));
  const targets = route.slice(1).filter(id => byId.has(id));
  targets.push('goal');
  const plan: RoutePlan = { success: false, hops: [], script: [], ticks: 0, jumps: 0, expansions: 0 };
  const stack: { hop: Hop; script: PogoInput[] }[] = [];

  const candidates = (state: PogoState, target: string, isGoal: boolean) => {
    const here = world.colliders[state.groundId];
    const tc = world.colliders[byId.get(target)!];
    const movingHere = !!here?.move || !!tc?.move;
    const idles = movingHere ? Array.from({ length: idleMax + 1 }, (_, i) => i) : [0];
    const direct: Candidate[] = [];
    const repo: Candidate[] = [];
    const dir = Math.sign((tc.minX + tc.maxX) / 2 - (here.minX + here.maxX) / 2) || 1;
    const combos: [number, number][] = [[0, 0]];
    for (const g of TILTS_G) for (const tt of TT) combos.push([g, tt]);
    for (const idle of idles) for (const [tiltG, tt] of combos) for (const hd of HD) for (const tiltA of TILTS_A) {
      const r = runHop(world, cfg, state, { idle, tiltG, tt, hd, tiltA });
      if (r.hazard) continue;
      const hop: Hop = { from: here.id, to: target, idle, tiltG, tt, hd, tiltA, ticks: r.script.length };
      if (isGoal && r.finished) { direct.push({ hop, end: r.s, script: r.script, score: 1000 - r.script.length * 0.001, finished: true, landedId: 'goal' }); continue; }
      if (!r.landed) continue;
      const on = world.colliders[r.s.groundId];
      const m = margin(world, cfg, r.s);
      if (!isGoal && on.id === target) direct.push({ hop, end: r.s, script: r.script, score: m * 10 - idle * 0.5 - r.script.length * 0.001, finished: false, landedId: on.id });
      else if (on.id === here.id && !on.move && m > 0.7) {
        const lat = (tipX(r.s) - tipX(state)) * dir;
        if (lat > 0) repo.push({ hop: { ...hop, to: here.id }, end: r.s, script: r.script, score: lat - r.script.length * 0.0005, finished: false, landedId: on.id });
      }
    }
    direct.sort((a, b) => b.score - a.score);
    repo.sort((a, b) => b.score - a.score);
    return { direct, repo };
  };
  const tipX = (s: PogoState): number => { const t = { x: 0, y: 0 }; tipCenterQ(cfg, s, t); return t.x / cfg.qPerMetre; };

  const dfs = (state: PogoState, k: number, repoLeft: number): boolean => {
    if (plan.expansions++ >= maxExpansions) return false;
    const target = targets[k];
    const isGoal = target === 'goal';
    const { direct, repo } = candidates(state, target, isGoal);
    log?.(`k=${k} ${target} direct=${direct.length} repo=${repo.length} expansions=${plan.expansions}`);
    for (const c of direct.slice(0, 3)) {
      if (!isGoal && c.score < 0) continue;
      stack.push({ hop: c.hop, script: c.script });
      if (isGoal) { plan.ticks = c.end.tick; plan.jumps = c.end.jumps; return true; }
      if (dfs(c.end, k + 1, 3)) return true;
      stack.pop();
    }
    if (repoLeft > 0) for (const c of repo.slice(0, 2)) {
      stack.push({ hop: c.hop, script: c.script });
      if (dfs(c.end, k, repoLeft - 1)) return true;
      stack.pop();
    }
    if (!plan.failedAt || k > targets.indexOf(plan.failedAt)) plan.failedAt = target;
    return false;
  };

  const start = createPogoState(world, cfg);
  plan.success = dfs(start, 0, 3);
  plan.hops = stack.map(x => x.hop);
  plan.script = stack.flatMap(x => x.script);
  if (!plan.success) plan.ticks = plan.script.length;
  return plan;
}

/**
 * How many (idle, tilt, hold, air-tilt) plans take the pogo from standing on `fromId` (at fraction `t` along its top,
 * at tick `tick`) onto `toId` — a per-hop solvability probe that needs no search over the rest of the route.
 * `best` is the largest landing margin (m) from the target's edges; 0 plans means the hop was not found by the grid.
 */
export function hopPlans(level: LevelData, cfg: PhysicsConfig, fromId: string, toId: string, opts: { t?: number; tick?: number; idleMax?: number } = {}): { plans: number; best: number; hop?: Hop } {
  const { t = 0.5, tick = 0, idleMax = 8 } = opts;
  const world = new PhysicsWorld(level, cfg.qPerMetre);
  const byId = new Map(world.colliders.map(c => [c.id, c.index]));
  const fi = byId.get(fromId), ti = byId.get(toId);
  if (fi === undefined || ti === undefined) return { plans: 0, best: 0 };
  const here = world.colliders[fi], tc = world.colliders[ti];
  const start = createPlantedState(world, cfg, fi, t, tick);
  const idles = here.move || tc.move ? Array.from({ length: idleMax + 1 }, (_, i) => i) : [0];
  const combos: [number, number][] = [[0, 0]];
  for (const g of TILTS_G) for (const tt of TT) combos.push([g, tt]);
  let plans = 0, best = 0, bestHop: Hop | undefined;
  for (const idle of idles) for (const [tiltG, tt] of combos) for (const hd of HD) for (const tiltA of TILTS_A) {
    const r = runHop(world, cfg, start, { idle, tiltG, tt, hd, tiltA });
    if (r.hazard || !r.landed) continue;
    if (world.colliders[r.s.groundId]?.id !== toId) continue;
    plans++;
    const m = margin(world, cfg, r.s);
    if (m > best) { best = m; bestHop = { from: fromId, to: toId, idle, tiltG, tt, hd, tiltA, ticks: r.script.length }; }
  }
  return { plans, best, hop: bestHop };
}

export { tipCenterQ };
