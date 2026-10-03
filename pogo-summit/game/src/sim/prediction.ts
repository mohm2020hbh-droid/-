import type { PhysicsConfig } from './PhysicsConfig';
import type { PhysicsWorld } from './PhysicsWorld';
import { type PogoInput, type PogoState, NEUTRAL_INPUT, modeOf, tipCenterLen } from './PogoState';
import { type SimContext, stepPogo } from './PogoPhysicsController';
import type { SimEvent } from './events';

export interface Prediction {
  /** x,y pairs of the stick's lowest point every `stride` ticks. */
  points: number[];
  ticks: number;
  /** Ended planted on a surface. */
  landed: boolean;
  hazard: boolean;
  fell: boolean;
  finished: boolean;
  /** Final state (when landed). */
  end: PogoState;
  groundId: number;
  /** World position of the landing tip centre. */
  landX: number;
  landY: number;
  events: SimEvent[];
}

const ev: SimEvent[] = [];

/** Charge ticks that give exactly `power01` through the charge curve. */
export function chargeTicksForPower(power01: number, cfg: PhysicsConfig): number {
  return Math.round(Math.pow(Math.min(1, Math.max(0, power01)), 1 / cfg.chargeCurve) * cfg.chargeTicksMax);
}

/**
 * Simulate a full launch with the REAL physics from a planted state:
 * set the stick angle, hold the charge to `power01`, release, then fly with `airInput(tick)` (default neutral).
 * Used by the trajectory guide (Phase 3 assist), the Physics Lab and the level-solvability bot.
 */
export function simulateJump(
  ctx: SimContext, from: PogoState, angleRad: number, power01: number,
  opts: { maxTicks?: number; stride?: number; airInput?: (t: number, s: PogoState) => PogoInput } = {},
): Prediction {
  const { maxTicks = 360, stride = 6, airInput } = opts;
  const s: PogoState = { ...from };
  s.angle = angleRad; s.omega = 0;
  s.mode = 'CHARGING'; s.prevHeld = true; s.charge = chargeTicksForPower(power01, ctx.cfg);
  const lc = tipCenterLen(ctx.cfg);
  // keep the tip centre where it was when the angle changes
  const tcx = from.x - Math.sin(from.angle) * lc, tcy = from.y - Math.cos(from.angle) * lc;
  s.x = tcx + Math.sin(angleRad) * lc; s.y = tcy + Math.cos(angleRad) * lc;

  const points: number[] = [];
  const events: SimEvent[] = [];
  const hc = ctx.cfg.comHeight;
  let hazard = false, fell = false, airborne = false, t = 0;
  stepPogo(ctx, s, { ...NEUTRAL_INPUT, jumpHeld: false }, ev); // release ⇒ launch
  for (const e of ev) events.push(e);
  ev.length = 0;
  airborne = modeOf(s) !== 'CHARGING' && modeOf(s) !== 'GROUNDED';
  for (t = 1; t < maxTicks; t++) {
    const inp = airInput ? airInput(t, s) : NEUTRAL_INPUT;
    stepPogo(ctx, s, inp, ev);
    for (const e of ev) {
      events.push(e);
      if (e.type === 'hazard') hazard = true;
      if (e.type === 'fall') fell = true;
    }
    ev.length = 0;
    if (t % stride === 0) points.push(s.x - Math.sin(s.angle) * hc, s.y - Math.cos(s.angle) * hc);
    if (hazard || fell || modeOf(s) === 'FINISHED') break;
    if (airborne && (modeOf(s) === 'GROUNDED' || modeOf(s) === 'CHARGING')) break;
    if (modeOf(s) === 'AIR' || modeOf(s) === 'SLIDING') airborne = true;
  }
  const landed = !hazard && !fell && (modeOf(s) === 'GROUNDED' || modeOf(s) === 'CHARGING');
  const lc2 = tipCenterLen(ctx.cfg);
  return {
    points, ticks: t, landed, hazard, fell, finished: modeOf(s) === 'FINISHED', end: s, groundId: landed ? s.groundId : -1,
    landX: s.x - Math.sin(s.angle) * lc2, landY: s.y - Math.cos(s.angle) * lc2, events,
  };
}

export type { PhysicsWorld };
