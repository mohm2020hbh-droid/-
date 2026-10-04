import type { PogoState } from '../PogoState';
import { makeEvent } from '../events';
import { asinD, clamp, cosD, sign, sinD, wrap180 } from '../math';
import type { Frame, SimContext } from './frame';

/**
 * Launch — E9 (velocity and angle), E10 (spin), E11 (after launch).
 *
 *   θ ← wrap180(θ); θ_j ← θ
 *   δ = clamp(wrap180(θ_n − 90 − θ), ±45);   a = θ + 0.1875·δ
 *   V = 0.74235·L·(−sin a, cos a);           v ← (V_x + 0.25·s_x, V_z + 0·s_z)      (assignment)
 *   ω ← ω + 0.1245·clamp(wrap180(θ − γ), ±45) − 0.25·1.5·sgn(σ)·|σ|^0.75,  σ = asin(n_x) [deg]
 *   L_last ← L; L ← 0; g ← 0; N ← 2; J ← 0.45·√L_max; p ← 0
 */
export function launch(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  // E11 (done first in the original): wrap the angle and remember it for the boost check
  s.theta = wrap180(s.theta);
  s.thetaJump = s.theta;

  // E9
  const delta = clamp(wrap180(s.thetaN - cfg.normalAngleOffset - s.theta), -cfg.normalBlendClamp, cfg.normalBlendClamp);
  const a = s.theta + cfg.normalBlendFactor * delta;
  const speed = cfg.launchSpeedPerLoad * s.load;
  s.qvx = -sinD(a) * speed + cfg.launchSlideCarryX * s.sx;
  s.qvy = cosD(a) * speed + cfg.launchSlideCarryZ * s.sy;

  // E10
  const sigma = asinD(s.nx);
  const tilt = clamp(wrap180(s.theta - s.gamma), -cfg.launchSpinTiltClamp, cfg.launchSpinTiltClamp);
  s.omega += cfg.launchSpinPerTilt * tilt
    - cfg.slopeSpinLaunchFactor * cfg.slopeSpinGain * sign(sigma) * Math.pow(Math.abs(sigma), cfg.slopeSpinExponent);

  // E11
  s.loadLast = s.load;
  s.load = 0;
  s.grounded = false;
  s.noGround = cfg.noGroundTime;
  s.jumpTimer = cfg.jumpTimerGain * Math.sqrt(s.loadMax);
  const boosted = s.boost >= 1 && s.loadLast > cfg.loadMaxFloor;
  s.boost = 0;
  s.jumps++;
  if (boosted) s.boosts++;
  if (s.startedTick < 0) s.startedTick = s.tick;
  const k = cfg.qPerMetre;
  f.ev.push(makeEvent('launch', s.tick, s.qx / k, s.qy / k, clamp(s.loadLast / cfg.hardImpactLoad, 0, 1), -sinD(a), cosD(a)));
  if (boosted) f.ev.push(makeEvent('boost', s.tick, s.qx / k, s.qy / k, 1, -sinD(a), cosD(a)));
}
