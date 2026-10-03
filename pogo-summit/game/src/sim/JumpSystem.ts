import type { PhysicsConfig } from './PhysicsConfig';
import type { PogoState } from './PogoState';
import type { SurfaceDef } from './SurfacePhysics';
import type { SimEvent } from './events';
import { makeEvent } from './events';
import { clamp, lerp } from './math';
import { resetSpin } from './BoostSystem';

/**
 * JumpSystem — charge → launch.
 * Source behaviour: input 5 (jump/charge) is held, and the jump is launched on RELEASE (XLSX V-017..V-019, grade B);
 * pogoLoad2 → pogoLaunch2 audio order implies "charge then launch" (F-037, grade B).
 * All magnitudes are TUNE_ME.
 */
export function chargeFraction(s: Pick<PogoState, 'charge'>, cfg: PhysicsConfig): number {
  return clamp(s.charge / cfg.chargeTicksMax, 0, 1);
}

/** Final power 0..1: time-based charge shaped by chargeCurve, lower-bounded by an explicit pull gesture. */
export function launchPower(s: Pick<PogoState, 'charge'>, cfg: PhysicsConfig, pull: number): number {
  const timed = Math.pow(chargeFraction(s, cfg), cfg.chargeCurve);
  return clamp(Math.max(timed, pull), 0, 1);
}

export function launchSpeedFor(power01: number, cfg: PhysicsConfig, surf: SurfaceDef): number {
  return lerp(cfg.launchSpeedMin, cfg.launchSpeedMax, power01) * surf.velocityMultiplier;
}

/**
 * Apply the launch to the state. `vp` = platform velocity, `(tx,ty)` = surface tangent.
 * Momentum rule: v = platform + slide·retain + stickAxis·speed (never "button = speed").
 */
export function performLaunch(
  s: PogoState, cfg: PhysicsConfig, surf: SurfaceDef, power01: number,
  vpx: number, vpy: number, tx: number, ty: number, material: string, ev: SimEvent[],
): void {
  let speed = launchSpeedFor(power01, cfg, surf);
  if (s.boostQueued && s.boostReady) {
    speed += cfg.boostPower;
    s.boostReady = false;
    s.boostQueued = false;
    resetSpin(s);
    s.boosts++;
    ev.push(makeEvent('boost', s.tick, s.x, s.y, 1, 0, 1));
  }
  const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
  const slide = s.slideV * cfg.launchMomentumRetain;
  s.vx = vpx + tx * slide + dx * speed;
  s.vy = vpy + ty * slide + dy * speed;
  s.vx = clamp(s.vx, -cfg.maxHorizontalSpeed, cfg.maxHorizontalSpeed);
  s.mode = 'AIR';
  s.groundId = -1;
  s.slideV = 0;
  s.airTicks = 0;
  s.coyote = 0;
  s.charge = 0;
  s.jumps++;
  if (s.startedTick < 0) s.startedTick = s.tick;
  const normalized = (speed - cfg.launchSpeedMin) / Math.max(1e-6, cfg.launchSpeedMax - cfg.launchSpeedMin);
  ev.push(makeEvent('launch', s.tick, s.x - dx * (cfg.comHeight), s.y - dy * cfg.comHeight, clamp(normalized, 0, 1), -dx, -dy, surf.id, material));
}
