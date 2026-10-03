import type { PhysicsConfig } from './PhysicsConfig';
import { clamp } from './math';

/**
 * CollisionResponse — stateless helpers for contact velocity math.
 * All coefficients come from PhysicsConfig (TUNE_ME): the sources contain no restitution/friction numbers
 * (XLSX U-10/U-11/U-12/U-13, grade C-after-measurement).
 */

export type CircleKind = 'tip' | 'torso' | 'head';

/** Lever sign for the spin kick: a hit at the tip tumbles the top the other way vs. a hit at the head. */
export const LEVER: Record<CircleKind, number> = { tip: -1, torso: 0.25, head: 1 };

/** Rotate unit vector (nx,ny) toward (dx,dy) by at most `maxRad`. Returns the result in `out`. */
export function aimedNormal(nx: number, ny: number, dx: number, dy: number, maxRad: number, out: { x: number; y: number }): void {
  const cross = nx * dy - ny * dx;
  const dot = nx * dx + ny * dy;
  const ang = clamp(Math.atan2(cross, dot), -maxRad, maxRad);
  const c = Math.cos(ang), s = Math.sin(ang);
  out.x = nx * c - ny * s;
  out.y = nx * s + ny * c;
}

/** Normal-bounce speed for a regular hit (impact = approach speed, ≥ 0). */
export function reboundSpeed(impact: number, restitution: number, restSpeed: number): number {
  return impact < restSpeed ? 0 : impact * restitution;
}

/** Tangential speed kept after an impact. */
export function keptTangential(vt: number, cfg: PhysicsConfig): number {
  return vt * (1 - cfg.energyLoss);
}

/** Spin kick (rad/s) from an impact on a body circle. */
export function impactKick(kind: CircleKind, nx: number, impact: number, cfg: PhysicsConfig): number {
  return nx * impact * cfg.impactSpin * LEVER[kind];
}

/** Kinetic friction decrement of a tangential speed over `h` seconds on a sliding contact. */
export function frictionStep(vt: number, mu: number, g: number, ny: number, h: number): number {
  const dv = mu * g * Math.max(ny, 0.2) * h;
  return Math.abs(vt) <= dv ? 0 : vt - Math.sign(vt) * dv;
}
