import type { PogoState } from '../PogoState';
import { rotationAllowed } from './collision';
import type { Frame, SimContext } from './frame';

/**
 * Rotation — E4.
 *   Lb  = 1/(1 + 2g)                   (1 in the air, 1/3 on the ground)
 *   ω_t = 32·(u_left − u_right)
 *   ω  ← ω + (ω_t − ω)·0.525·Δt / (1 + √J)
 *   θ  ← θ + Lb·ω·Δt                  (collision-checked, like the engine's c_rotate)
 * Positive ω increases θ; θ > 0 leans toward −x (the original's key A = turn left).
 * When the check refuses a step, θ stays and ω is NOT altered (the original reads θ back from the entity).
 */
export function applyRotation(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  const lb = 1 / (1 + cfg.groundTurnDivisor * (s.grounded ? 1 : 0));
  const omegaT = cfg.turnTarget * (f.uLeft - f.uRight);
  s.omega += ((omegaT - s.omega) * cfg.turnResponse * f.dt) / (1 + Math.sqrt(s.jumpTimer));
  const next = s.theta + lb * s.omega * f.dt;
  if (next !== s.theta && rotationAllowed(ctx, s, next)) s.theta = next;
}
