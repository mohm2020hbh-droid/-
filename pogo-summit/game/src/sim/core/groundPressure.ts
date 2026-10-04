import type { PogoState } from '../PogoState';
import { cosD, sinD } from '../math';
import { tipCenterQ } from '../PogoState';
import { moveWithCollision } from './collision';
import type { Frame, SimContext } from './frame';

const TIP = { x: 0, y: 0 };

/**
 * Ground pressure — E5, evaluated on a grounded tick after the turn:
 *   tip pivot (A):  Δtip = tip_prev − tip(θ);  if |Δtip_x| < 256 ∧ |Δtip_z| < 256 then move the body by Δtip
 *   pressure:       v ← R_{θ_n}·(−24, 0) = −24·n          (magnitude A; direction "into the surface" A·B)
 */
export function groundStep(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  tipCenterQ(cfg, s, TIP);
  const dx = s.tipPrevX - TIP.x, dy = s.tipPrevY - TIP.y;
  if (Math.abs(dx) < cfg.tipPivotLimit && Math.abs(dy) < cfg.tipPivotLimit && (dx !== 0 || dy !== 0)) moveWithCollision(ctx, s, f, dx, dy);
  s.qvx = -cfg.groundPressure * cosD(s.thetaN);
  s.qvy = -cfg.groundPressure * sinD(s.thetaN);
}
