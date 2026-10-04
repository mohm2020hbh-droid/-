import type { PhysicsConfig } from './PhysicsConfig';

/**
 * Unit conversions between the locked physics units (Q = quant, T = tick, spec §1) and the presentation units
 * (metres, real seconds). The physics core works in Q/T only; everything that leaves it is converted here.
 *
 *   Δt (T per simulation tick)   = ticksPerSecond · timeFactor / tickRate           [LOCKED_SPEC §2]
 *   T per real second            = ticksPerSecond · timeFactor
 *   metres = Q / qPerMetre       (free presentation choice; the original HUD uses 52 Q = 1 m)
 */
export const dtTicks = (cfg: Pick<PhysicsConfig, 'ticksPerSecond' | 'timeFactor' | 'tickRate'>): number =>
  (cfg.ticksPerSecond * cfg.timeFactor) / cfg.tickRate;

export const ticksPerRealSecond = (cfg: Pick<PhysicsConfig, 'ticksPerSecond' | 'timeFactor'>): number => cfg.ticksPerSecond * cfg.timeFactor;

export const qToM = (cfg: Pick<PhysicsConfig, 'qPerMetre'>, q: number): number => q / cfg.qPerMetre;
export const mToQ = (cfg: Pick<PhysicsConfig, 'qPerMetre'>, m: number): number => m * cfg.qPerMetre;

/** Q/T → m/s (real time). */
export const speedQtToMs = (cfg: PhysicsConfig, v: number): number => (v * ticksPerRealSecond(cfg)) / cfg.qPerMetre;

/** Presentation geometry in metres, derived from the physics geometry (Q). */
export interface GeometryM {
  /** origin → tip end */
  comHeight: number;
  /** origin → top of the hull */
  headHeight: number;
  tipRadius: number;
}

export function geometryM(cfg: PhysicsConfig): GeometryM {
  return {
    comHeight: cfg.tipLength / cfg.qPerMetre,
    headHeight: (cfg.tipLength + cfg.hullMaxZ) / cfg.qPerMetre,
    tipRadius: cfg.tipRadius / cfg.qPerMetre,
  };
}
