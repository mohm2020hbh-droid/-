import type { PogoState } from '../PogoState';
import { makeEvent } from '../events';
import type { Frame, SimContext } from './frame';

/**
 * Boost (power jump) — E13. In the air: if int(|θ − θ_j|) > 285 ∧ p < 1 then p ← 1.
 * Effect at the next landing (E7): L_max = clamp(I + 20, 120, 300). p is cleared by the next launch (E11) or a wall bounce (E14).
 * There is no boost button: the original's boost is this automatic power jump.
 */
export function boostCheck(ctx: SimContext, s: PogoState, f: Frame): void {
  if (s.grounded) return;
  if (Math.trunc(Math.abs(s.theta - s.thetaJump)) > ctx.cfg.boostRotation && s.boost < 1) {
    s.boost = 1;
    const k = ctx.cfg.qPerMetre;
    f.ev.push(makeEvent('boost_armed', s.tick, s.qx / k, s.qy / k, 1, 0, 1));
  }
}
