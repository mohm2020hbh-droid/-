import type { PogoState } from '../PogoState';
import { makeEvent } from '../events';
import { asinD, clamp, sign, wrap180 } from '../math';
import type { Frame, SimContext } from './frame';

/**
 * Wall bounce — E14. Runs when the collision move touched something, the player is not grounded and N = 0.
 *
 *  1. side effects:  N ← 2, p ← 0, L ← 0, θ ← wrap180(θ), θ_j ← θ
 *  2. direction:     r̂ = reflection of the movement off the surface [A·B: the engine's `bounce` vector].
 *                    Fallback (A): r = v − 2(v·n)n.     d = 0.9·r̂ + n
 *  3. speed:         S = max(28, 0.4·|v|)·min(1 + n_z, 1);   b = S·d/|d|;   if n_z > 0 then b_z = n_z·S
 *  4. velocity:      v ← (0.875·b_x + s_x, b_z + s_z)                                    (assignment)
 *  5. spin:          ω ← 0.5·wrap180(γ − θ) − 0.2·1.5·sgn(σ)·|σ|^0.75,  σ = asin(n_x) [deg]   (assignment)
 * There is no restitution coefficient: the rebound speed is S, not e·|v|.
 */
export function wallBounce(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  const nx = f.touchNx, ny = f.touchNy;
  const speedIn = Math.hypot(s.qvx, s.qvy);

  s.noGround = cfg.noGroundTime;
  s.boost = 0;
  s.load = 0;
  s.theta = wrap180(s.theta);
  s.thetaJump = s.theta;

  // r̂: reflection of the movement vector (engine `bounce`); fallback to the velocity (A) when there was no movement
  let mx = f.dispX, my = f.dispY;
  if (Math.hypot(mx, my) < 1e-9) { mx = s.qvx; my = s.qvy; }
  const dot = mx * nx + my * ny;
  let rx = mx + cfg.bounceReflect * dot * nx, ry = my + cfg.bounceReflect * dot * ny;
  let rl = Math.hypot(rx, ry);
  if (rl < 1e-12) { rx = nx; ry = ny; rl = 1; }
  rx /= rl; ry /= rl;

  let dx = cfg.bounceDirWeight * rx + nx, dy = cfg.bounceDirWeight * ry + ny;
  const dl = Math.hypot(dx, dy) || 1;
  dx /= dl; dy /= dl;

  const S = Math.max(cfg.bounceMinSpeed, cfg.bounceSpeedFactor * speedIn) * Math.min(1 + ny, cfg.bounceSlopeCap);
  const bx = S * dx;
  let by = S * dy;
  if (ny > 0) by = ny * S;

  s.qvx = cfg.bounceXScale * bx + s.sx;
  s.qvy = by + s.sy;

  const sigma = asinD(nx);
  s.omega = cfg.bounceSpin * wrap180(s.gamma - s.theta)
    - cfg.slopeSpinBounceFactor * cfg.slopeSpinGain * sign(sigma) * Math.pow(Math.abs(sigma), cfg.slopeSpinExponent);

  const k = cfg.qPerMetre;
  f.ev.push(makeEvent('wall_hit', s.tick, f.touchX / k, f.touchY / k, clamp(speedIn / (cfg.maxSpeed / 2), 0, 1), nx, ny));
}
