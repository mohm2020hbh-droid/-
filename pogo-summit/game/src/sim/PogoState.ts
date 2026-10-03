import type { PhysicsConfig } from './PhysicsConfig';
import type { PhysicsWorld } from './PhysicsWorld';
import { DEG } from './math';

export type PogoMode = 'AIR' | 'GROUNDED' | 'CHARGING' | 'SLIDING' | 'FINISHED';

/** Abstract input produced by a ControlScheme (touch). The simulation never sees raw pointers or keys. */
export interface PogoInput {
  /** −1 (lean left) … +1 (lean right). */
  tilt: number;
  /** True while the jump/charge control is held. Release (true→false) launches. */
  jumpHeld: boolean;
  /** Edge pulse: the Boost button was pressed this tick. */
  boostPressed: boolean;
  /** 0..1 extra charge from a pull-down gesture (DRAG scheme) — acts as a lower bound on power. */
  pull: number;
  /** Edge pulse: touch cancelled (abort charge, do not launch). */
  cancel: boolean;
}

/** Re-reads the mode (TypeScript cannot see mutations made through helper calls). */
export const modeOf = (s: { mode: PogoMode }): PogoMode => s.mode;

export const NEUTRAL_INPUT: Readonly<PogoInput> = Object.freeze({ tilt: 0, jumpHeld: false, boostPressed: false, pull: 0, cancel: false });

export interface PogoState {
  tick: number;
  /** Centre of mass (torso circle centre), metres. */
  x: number;
  y: number;
  vx: number;
  vy: number;
  /** Stick angle from vertical, radians, + = leaning toward +x. Unbounded (counts full rotations). */
  angle: number;
  omega: number;
  mode: PogoMode;

  // ground contact (valid when GROUNDED / CHARGING)
  groundId: number;
  /** Tip-circle centre in the ground collider's BASE coordinates. */
  lx: number;
  ly: number;
  gnx: number;
  gny: number;
  slideV: number;
  plantedTicks: number;
  airTicks: number;
  coyote: number;

  // jump
  charge: number;
  prevHeld: boolean;
  pressBuffer: number;

  // boost
  spin: number;
  spinIdle: number;
  boostReady: boolean;
  boostQueued: boolean;

  // stats
  jumps: number;
  boosts: number;
  falls: number;
  hazards: number;
  startedTick: number;
  finishedTick: number;
  maxY: number;
  lastImpact: number;

  // respawn point
  safeGround: number;
  safeLx: number;
  safeLy: number;
  safeNx: number;
  safeNy: number;
  /** Set to the tick of the last teleport (respawn) so renderers can skip interpolation. */
  teleportTick: number;
}

export const tipCenterLen = (cfg: PhysicsConfig): number => cfg.comHeight - cfg.tipRadius;

export interface Pose {
  /** Torso / COM */
  x: number; y: number;
  angle: number;
  /** Tip-circle centre */
  tipX: number; tipY: number;
  /** Lowest end of the stick (visual foot). */
  footX: number; footY: number;
  headX: number; headY: number;
}

export function computePose(s: Pick<PogoState, 'x' | 'y' | 'angle'>, cfg: PhysicsConfig, out: Pose): Pose {
  const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
  out.x = s.x; out.y = s.y; out.angle = s.angle;
  const lc = tipCenterLen(cfg);
  out.tipX = s.x - dx * lc; out.tipY = s.y - dy * lc;
  out.footX = s.x - dx * cfg.comHeight; out.footY = s.y - dy * cfg.comHeight;
  const hd = cfg.headHeight - cfg.comHeight;
  out.headX = s.x + dx * hd; out.headY = s.y + dy * hd;
  return out;
}

export const newPose = (): Pose => ({ x: 0, y: 0, angle: 0, tipX: 0, tipY: 0, footX: 0, footY: 0, headX: 0, headY: 0 });

/** Initial state: planted on the surface under level.startPosition. */
export function createPogoState(world: PhysicsWorld, cfg: PhysicsConfig): PogoState {
  const start = world.level.startPosition;
  const g = world.findGround(start.x, start.y, 0.6);
  if (!g) throw new Error(`Level ${world.level.levelId}: startPosition is not on any solid surface`);
  const nx = g.nx, ny = g.ny;
  const angle = Math.atan2(nx, ny);
  const tcx = g.x + nx * cfg.tipRadius, tcy = g.y + ny * cfg.tipRadius;
  const lc = tipCenterLen(cfg);
  return {
    tick: 0,
    x: tcx + Math.sin(angle) * lc, y: tcy + Math.cos(angle) * lc,
    vx: 0, vy: 0, angle, omega: 0, mode: 'GROUNDED',
    groundId: g.collider.index, lx: tcx, ly: tcy, gnx: nx, gny: ny, slideV: 0, plantedTicks: 0, airTicks: 0, coyote: 0,
    charge: 0, prevHeld: false, pressBuffer: 0,
    spin: 0, spinIdle: 0, boostReady: false, boostQueued: false,
    jumps: 0, boosts: 0, falls: 0, hazards: 0, startedTick: -1, finishedTick: -1, maxY: tcy, lastImpact: 0,
    safeGround: g.collider.index, safeLx: tcx, safeLy: tcy, safeNx: nx, safeNy: ny, teleportTick: -1,
  };
}

export const degToRad = (d: number): number => d * DEG;

/**
 * Planted state at fraction `t` (0..1, left→right) along the most upward-facing edge of a collider.
 * Used by the Physics Lab ("Test Jump" on any platform) and by the solvability bot.
 */
export function createPlantedState(world: PhysicsWorld, cfg: PhysicsConfig, colliderIndex: number, t: number, tick = 0): PogoState {
  const c = world.colliders[colliderIndex];
  const { pts, eny } = c.poly;
  let best = 0;
  for (let i = 1; i < pts.length; i++) if (eny[i] > eny[best]) best = i;
  const a = pts[best], b = pts[(best + 1) % pts.length];
  // edge goes right→left for a CCW top face, so interpolate from b to a to get left→right
  const px = b.x + (a.x - b.x) * t, py = b.y + (a.y - b.y) * t;
  const nx = c.poly.enx[best], ny = eny[best];
  const angle = Math.atan2(nx, ny);
  const lc = tipCenterLen(cfg);
  const lx = px + nx * cfg.tipRadius, ly = py + ny * cfg.tipRadius;
  const off = { x: 0, y: 0, vx: 0, vy: 0 };
  world.offsetAt(c, tick, off);
  const s = createPogoState(world, cfg);
  Object.assign(s, {
    tick, mode: 'GROUNDED' as PogoMode, groundId: c.index, lx, ly, gnx: nx, gny: ny, angle, omega: 0, slideV: 0,
    x: off.x + lx + Math.sin(angle) * lc, y: off.y + ly + Math.cos(angle) * lc, vx: off.vx, vy: off.vy,
    safeGround: c.index, safeLx: lx, safeLy: ly, safeNx: nx, safeNy: ny,
  });
  return s;
}
