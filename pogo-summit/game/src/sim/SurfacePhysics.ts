import type { PhysicsConfig } from './PhysicsConfig';
import { DEG } from './math';

/**
 * SurfacePhysics — material surfaces + geometry classification.
 *
 * The prompt's list (Normal, Slope, Wall, Moving Platform, Bounce, Slippery, Sticky, Hazard, Boost, Goal)
 * is realised as:
 *   MATERIAL surfaces (this table): normal · bounce · slippery · sticky · hazard · boost · goal
 *   GEOMETRY classes (derived from the contact normal): floor · slope · steep · wall · ceiling
 *   MOTION (derived from the collider): moving platform
 *
 * Original-game evidence: slime/pinkSap/coconutSlippery (A existence, B meaning), mushroom bounce_light (A/B),
 * boostjuice TYPE_BOOSTJUICE (A existence), startFinish (A/B). All numeric values below are OUR starting points
 * (TUNE_ME) — the sources contain no friction/restitution numbers.
 */
export type SurfaceId = 'normal' | 'bounce' | 'slippery' | 'sticky' | 'hazard' | 'boost' | 'goal';

export interface SurfaceDef {
  id: SurfaceId;
  /** Multiplier on friction-related terms (1 = normal rock/grass). */
  friction: number;
  /** Multiplier of cfg.floorRestitution, or an absolute value if restitutionAbsolute. */
  restitution: number;
  restitutionAbsolute?: boolean;
  /** Multiplies launch speed when jumping FROM this surface. */
  velocityMultiplier: number;
  /** Can the stick tip be planted on it. */
  plantable: boolean;
  bounce?: { minSpeed: number; aimMaxDeg: number };
  /** Pushes a planted tip along the surface direction with `push * cfg.acceleration`. */
  push?: number;
  hazard?: boolean;
  goal?: boolean;
  soundEvent: string;
  particleEvent: string;
  hapticEvent: string;
}

export const SURFACES: Record<SurfaceId, SurfaceDef> = {
  normal:   { id: 'normal',   friction: 1.0,  restitution: 1.0, velocityMultiplier: 1.0,  plantable: true,  soundEvent: 'land_soft',  particleEvent: 'dust',        hapticEvent: 'landing' },
  bounce:   { id: 'bounce',   friction: 0.8,  restitution: 1.05, restitutionAbsolute: true, velocityMultiplier: 1.0, plantable: false,
              bounce: { minSpeed: 16, aimMaxDeg: 35 },                                                                      soundEvent: 'bounce',     particleEvent: 'bounce_ring', hapticEvent: 'bounce' },
  slippery: { id: 'slippery', friction: 0.04, restitution: 1.0, velocityMultiplier: 1.0,  plantable: true,  soundEvent: 'land_ice',   particleEvent: 'ice_chips',   hapticEvent: 'landing' },
  sticky:   { id: 'sticky',   friction: 2.5,  restitution: 0.0, velocityMultiplier: 0.82, plantable: true,  soundEvent: 'land_goo',   particleEvent: 'goo',         hapticEvent: 'landing' },
  hazard:   { id: 'hazard',   friction: 1.0,  restitution: 1.0, velocityMultiplier: 1.0,  plantable: false, hazard: true,           soundEvent: 'hazard',     particleEvent: 'hazard_burst', hapticEvent: 'hazard' },
  boost:    { id: 'boost',    friction: 1.0,  restitution: 1.0, velocityMultiplier: 1.25, plantable: true,  push: 1,               soundEvent: 'boost_pad',  particleEvent: 'boost_burst', hapticEvent: 'boost' },
  goal:     { id: 'goal',     friction: 1.0,  restitution: 1.0, velocityMultiplier: 1.0,  plantable: false, goal: true,             soundEvent: 'goal',       particleEvent: 'goal_burst',  hapticEvent: 'goal' },
};

export type GeometryClass = 'floor' | 'slope' | 'steep' | 'wall' | 'ceiling';

/** Max slope (from horizontal) at which a tip can still be planted at all (very sticky surfaces only). */
export const MAX_PLANT_ANGLE = 75 * DEG;

export function classifyNormal(ny: number, cfg: PhysicsConfig): GeometryClass {
  const a = Math.acos(Math.min(1, Math.max(-1, ny))); // angle between normal and world-up
  if (a <= 6 * DEG) return 'floor';
  if (a <= cfg.steepSlopeAngle * DEG) return 'slope';
  if (a <= MAX_PLANT_ANGLE) return 'steep';
  return ny > -0.5 ? 'wall' : 'ceiling';
}

/**
 * Static-friction rule: the tip holds on a slope of angle α when tan α ≤ tan(steep) · surface.friction.
 * Flat ground is always stable (tan 0 = 0), so slippery flat ice holds but keeps its slide velocity.
 */
export function isStable(ny: number, surf: SurfaceDef, cfg: PhysicsConfig): boolean {
  if (!surf.plantable) return false;
  const a = Math.acos(Math.min(1, Math.max(-1, ny)));
  if (a > MAX_PLANT_ANGLE) return false;
  return Math.tan(a) <= Math.tan(cfg.steepSlopeAngle * DEG) * surf.friction + 1e-9;
}

export function surfaceRestitution(surf: SurfaceDef, cfg: PhysicsConfig): number {
  return surf.restitutionAbsolute ? surf.restitution : cfg.floorRestitution * surf.restitution;
}
