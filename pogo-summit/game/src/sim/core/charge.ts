import type { PhysicsConfig } from '../PhysicsConfig';
import type { PogoState } from '../PogoState';
import { makeEvent } from '../events';
import { clamp } from '../math';
import type { Frame, SimContext } from './frame';

/** E7: landing impulse I = 1.65·|v|^0.925 (|v| in Q/T). */
export function impactOf(cfg: PhysicsConfig, speed: number): number {
  return cfg.impactGain * Math.pow(speed, cfg.impactExponent);
}

/**
 * E7: the spring window of a landing with speed |v| and boost flag p:
 *   L_min = max(40, I^0.9)        L_max = clamp(I + 20·p, 95 + 25·p, 300)
 * (the original adds an extra term e to both clamp arguments for special objects; e = 0 in normal play.)
 */
export function springWindow(cfg: PhysicsConfig, speed: number, boost: number): { min: number; max: number } {
  const I = impactOf(cfg, speed);
  const p = boost >= 1 ? 1 : 0;
  const max = clamp(I + cfg.impactBoostBonus * p, cfg.loadMaxFloor + cfg.loadMaxFloorBoostBonus * p, cfg.loadMaxCap);
  const min = Math.max(cfg.loadMinFloor, Math.pow(I, cfg.loadMinExponent));
  return { min, max };
}

export type ChargeResult = 'charging' | 'launch' | 'idle';

/**
 * E8 — Pogo charge, evaluated on a grounded tick that touched the surface:
 *   if (L < L_min ∨ hold) ∧ L < L_max :  L ← min(L + 16·Δt, L_max)
 *   else if L > 2                     :  launch
 * The spring bone follows the load while charging (E18: P_b = P_max = L).
 * There is no "release to launch": the pogo launches by itself as soon as L ≥ L_min without hold, or L = L_max.
 */
export function chargeStep(ctx: SimContext, s: PogoState, f: Frame): ChargeResult {
  const { cfg } = ctx;
  if ((s.load < s.loadMin || f.hold) && s.load < s.loadMax) {
    const before = s.load;
    s.load = Math.min(s.load + cfg.chargeRate * f.dt, s.loadMax);
    s.springBone = s.load; s.springBoneMax = s.load;
    if (before === 0 && f.hold) f.ev.push(makeEvent('charge_start', s.tick, s.qx / cfg.qPerMetre, s.qy / cfg.qPerMetre, 0, s.nx, s.ny));
    return 'charging';
  }
  if (s.load > cfg.minLaunchLoad) return 'launch';
  return 'idle';
}
