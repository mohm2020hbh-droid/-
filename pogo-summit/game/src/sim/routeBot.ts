import type { LevelData } from '../data/LevelData';
import type { PhysicsConfig } from './PhysicsConfig';
import { PhysicsWorld } from './PhysicsWorld';
import { NEUTRAL_INPUT, type PogoInput, type PogoState, createPogoState, tipCenterLen } from './PogoState';
import { stepPogo } from './PogoPhysicsController';
import type { SimEvent } from './events';

/**
 * routeBot — plays a designed route END TO END on the real simulation, through the same input path a player uses
 * (lean while standing, short press with a pull-power, release). Proves the level is completable in ONE continuous run,
 * including waiting for moving platforms, and yields a replayable input script (used by the browser E2E test).
 */
export interface Hop { from: string; to: string; wait: number; tilt: number; power: number; ticks: number }
export interface RoutePlan {
  success: boolean;
  hops: Hop[];
  /** Per-tick input script for the whole run. */
  script: PogoInput[];
  ticks: number;
  jumps: number;
  failedAt?: string;
}

const ROTATE_TICKS = 70, PRESS_TICKS = 3;

function runHop(world: PhysicsWorld, cfg: PhysicsConfig, from: PogoState, wait: number, tilt: number, power: number, maxFlight = 700) {
  const s: PogoState = { ...from };
  const ctx = { world, cfg };
  const ev: SimEvent[] = [];
  const script: PogoInput[] = [];
  const push = (i: PogoInput) => { script.push(i); stepPogo(ctx, s, i, ev); };
  for (let i = 0; i < wait; i++) push({ ...NEUTRAL_INPUT });
  for (let i = 0; i < ROTATE_TICKS; i++) push({ ...NEUTRAL_INPUT, tilt });
  for (let i = 0; i < PRESS_TICKS; i++) push({ ...NEUTRAL_INPUT, tilt, jumpHeld: true, pull: power });
  push({ ...NEUTRAL_INPUT, tilt, jumpHeld: false, pull: power });
  let hazard = false, finished = false, airborne = false;
  for (let t = 0; t < maxFlight; t++) {
    ev.length = 0;
    push({ ...NEUTRAL_INPUT });
    if (ev.some(e => e.type === 'hazard' || e.type === 'fall')) hazard = true;
    if (s.mode === 'FINISHED') { finished = true; break; }
    if (s.mode === 'AIR' || s.mode === 'SLIDING') airborne = true;
    if (hazard) break;
    if (airborne && (s.mode === 'GROUNDED' || s.mode === 'CHARGING')) {
      // settle a few ticks so the plant is stable
      for (let k = 0; k < 30; k++) push({ ...NEUTRAL_INPUT });
      break;
    }
  }
  return { s, script, hazard, finished, landed: s.mode === 'GROUNDED' };
}

/** Distance of the landing tip from the nearest end of the collider's top edge (bigger = safer landing). */
function margin(world: PhysicsWorld, s: PogoState): number {
  const c = world.colliders[s.groundId];
  if (!c) return -1;
  const { pts, eny } = c.poly;
  let best = 0;
  for (let i = 1; i < pts.length; i++) if (eny[i] > eny[best]) best = i;
  const a = pts[best], b = pts[(best + 1) % pts.length];
  const lo = Math.min(a.x, b.x), hi = Math.max(a.x, b.x);
  const x = s.lx;
  return Math.min(x - lo, hi - x);
}

export function playRoute(level: LevelData, cfg: PhysicsConfig, opts: { tiltStep?: number; powerStep?: number; waits?: number[] } = {}): RoutePlan {
  const { tiltStep = 0.1, powerStep = 0.05 } = opts;
  const route = level.route ?? [];
  const world = new PhysicsWorld(level);
  let state = createPogoState(world, cfg);
  const plan: RoutePlan = { success: false, hops: [], script: [], ticks: 0, jumps: 0 };
  const byId = new Map(world.colliders.map(c => [c.id, c.index]));
  // route nodes that are bounce pads are traversed *through* (the bot targets the platform after the pad)
  const targets = route.slice(1).filter(id => world.colliders[byId.get(id)!].surface !== 'bounce');
  const goalId = 'goal';
  targets.push(goalId);
  for (let k = 0; k < targets.length; k++) {
    const target = targets[k];
    const isGoal = target === goalId;
    const tIdx = byId.get(target)!;
    const movingHere = !!world.colliders[state.groundId]?.move || !!world.colliders[tIdx]?.move || targets.slice(k, k + 2).some(id => world.colliders[byId.get(id)!].move);
    const waits = opts.waits ?? (movingHere ? Array.from({ length: 25 }, (_, i) => i * 30) : [0]);
    // up to 5 attempts: a direct hop to the target, else a REPOSITION hop along the current platform toward it
    let reached = false;
    for (let attempt = 0; attempt < 6 && !reached; attempt++) {
      let best: { hop: Hop; r: ReturnType<typeof runHop>; score: number } | null = null;
      let repo: { hop: Hop; r: ReturnType<typeof runHop>; score: number } | null = null;
      const here = world.colliders[state.groundId];
      const tc = world.colliders[tIdx];
      const dir = Math.sign((tc.minX + tc.maxX) / 2 - (here.minX + here.maxX) / 2) || 1;
      for (const wait of waits) {
        for (let ti = -Math.round(1 / tiltStep); ti <= Math.round(1 / tiltStep); ti++) {
          const tilt = +(ti * tiltStep).toFixed(3);
          for (let pw = 0; pw <= 1.0001; pw += powerStep) {
            const power = +Math.min(1, pw).toFixed(3);
            const r = runHop(world, cfg, state, wait, tilt, power);
            if (r.hazard) continue;
            const hop: Hop = { from: here.id, to: target, wait, tilt, power, ticks: r.script.length };
            if (isGoal && r.finished) { if (1000 > (best?.score ?? -Infinity)) best = { hop, r, score: 1000 }; continue; }
            if (!r.landed) continue;
            const landedOn = world.colliders[r.s.groundId];
            if (!isGoal && landedOn.id === target) {
              const sc = margin(world, r.s) * 10 - wait * 0.02 - r.script.length * 0.001;
              if (sc > (best?.score ?? -Infinity)) best = { hop, r, score: sc };
            } else if (landedOn.id === here.id && margin(world, r.s) > 0.7 && !landedOn.move) {
              // reposition: progress toward the target side of THIS platform
              const sc = (r.s.lx - state.lx) * dir - r.script.length * 0.0005;
              if (sc > (repo?.score ?? 0.5)) repo = { hop: { ...hop, to: here.id }, r, score: sc };
            }
          }
        }
      }
      const pick = best && best.score >= 0 ? best : repo;
      if (!pick) { plan.failedAt = target; return plan; }
      plan.hops.push(pick.hop);
      plan.script.push(...pick.r.script);
      state = pick.r.s;
      plan.jumps = state.jumps;
      if (pick === best) reached = true;
      if (isGoal && state.mode === 'FINISHED') { plan.success = true; plan.ticks = state.tick; return plan; }
    }
    if (!reached) { plan.failedAt = target; return plan; }
    if (isGoal) { plan.success = state.mode === 'FINISHED'; plan.ticks = state.tick; return plan; }
  }
  plan.ticks = state.tick;
  return plan;
}

export { tipCenterLen };
