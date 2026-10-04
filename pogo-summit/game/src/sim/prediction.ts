import type { PhysicsWorld } from './PhysicsWorld';
import { type PogoState, NEUTRAL_INPUT, type PogoInput, modeOf } from './PogoState';
import { type SimContext } from './core/frame';
import { stepPogo } from './core/step';
import { launch } from './core/launch';
import { newFrame } from './core/frame';
import type { SimEvent } from './events';
import { ticksPerRealSecond } from './units';
import { tipCenterQ } from './PogoState';
import { cosD, sinD } from './math';

export interface Prediction {
  /** x,y pairs (metres) of the stick's lowest point every `stride` ticks. */
  points: number[];
  ticks: number;
  /** Ended planted on a surface. */
  landed: boolean;
  hazard: boolean;
  fell: boolean;
  finished: boolean;
  /** Final state. */
  end: PogoState;
  groundId: number;
  /** World position (m) of the landing tip. */
  landX: number;
  landY: number;
  events: SimEvent[];
}

const ev: SimEvent[] = [];

/**
 * Simulate "launch now" with the REAL physics from a grounded state: apply the launch (E9–E11) with the current spring
 * load, then fly with `airInput(tick)` (default neutral) until the pogo lands, hits a hazard, falls or finishes.
 * Used by the trajectory guide, the Physics Lab and the level analysis.
 *   opts.theta  override the stick angle (deg) before the launch
 *   opts.load   override the spring load L before the launch
 */
export function simulateLaunch(
  ctx: SimContext, from: PogoState,
  opts: { theta?: number; load?: number; maxTicks?: number; stride?: number; airInput?: (t: number, s: PogoState) => PogoInput } = {},
): Prediction {
  const { maxTicks = 360, stride = 6, airInput } = opts;
  const s: PogoState = { ...from };
  if (opts.theta !== undefined) {
    // rotate the stick about the planted tip (the tip stays where it is)
    const tip = { x: 0, y: 0 };
    tipCenterQ(ctx.cfg, from, tip);
    const d = ctx.cfg.tipLength - ctx.cfg.tipRadius;
    s.theta = opts.theta;
    s.qx = tip.x - sinD(opts.theta) * d;
    s.qy = tip.y + cosD(opts.theta) * d;
  }
  if (opts.load !== undefined) s.load = opts.load;
  const f = newFrame();
  const events: SimEvent[] = [];
  f.ev = events; f.dt = 0;
  launch(ctx, s, f);
  const points: number[] = [];
  const k = ctx.cfg.qPerMetre;
  let hazard = false, fell = false, t = 0;
  for (t = 1; t < maxTicks; t++) {
    const inp = airInput ? airInput(t, s) : NEUTRAL_INPUT;
    stepPogo(ctx, s, inp, ev);
    for (const e of ev) {
      events.push(e);
      if (e.type === 'hazard') hazard = true;
      if (e.type === 'fall') fell = true;
    }
    ev.length = 0;
    if (t % stride === 0) points.push(s.x - Math.sin(s.angle) * ctx.cfg.tipLength / k, s.y - Math.cos(s.angle) * ctx.cfg.tipLength / k);
    if (hazard || fell || modeOf(s) === 'FINISHED') break;
    if (s.grounded && s.noGround <= 0) break;
  }
  const landed = !hazard && !fell && s.grounded && modeOf(s) !== 'FINISHED';
  return {
    points, ticks: t, landed, hazard, fell, finished: modeOf(s) === 'FINISHED', end: s, groundId: landed ? s.groundId : -1,
    landX: s.x - Math.sin(s.angle) * ctx.cfg.tipLength / k, landY: s.y - Math.cos(s.angle) * ctx.cfg.tipLength / k, events,
  };
}

/** Real seconds that `ticks` simulation ticks last (120 Hz fixed loop). */
export const ticksToRealSeconds = (ctx: SimContext, ticks: number): number => ticks / ctx.cfg.tickRate;
export { ticksPerRealSecond };
export type { PhysicsWorld };
