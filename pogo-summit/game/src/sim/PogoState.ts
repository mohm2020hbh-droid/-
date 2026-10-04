import type { PhysicsConfig } from './PhysicsConfig';
import type { Collider, PhysicsWorld } from './PhysicsWorld';
import { DEG, atan2D, cosD, sinD, wrap180 } from './math';
import { geometryM, speedQtToMs, ticksPerRealSecond } from './units';
import { springWindow } from './core/charge';

export type PogoMode = 'AIR' | 'GROUNDED' | 'CHARGING' | 'SLIDING' | 'FINISHED';

/**
 * Abstract input produced by a ControlScheme (touch). The simulation never sees raw pointers or keys.
 * Locked physics reads ONLY `tilt` and `jumpHeld` (E4 turn input, E8 hold). `boostPressed` and `pull` are inert: the
 * boost is the automatic power jump of E13 and the original has no pull-power gesture.
 */
export interface PogoInput {
  /** −1 (lean left) … +1 (lean right). Analog turn input: ω_t = 32·(u_left − u_right) with u_left = max(0,−tilt). */
  tilt: number;
  /** True while the charge control is held (the original "hold" of E8, default Space). */
  jumpHeld: boolean;
  /** Inert (the locked spec has no boost button). */
  boostPressed: boolean;
  /** Inert (the locked spec has no pull-power gesture). */
  pull: number;
  /** Edge pulse: touch cancelled ⇒ treated as releasing the hold. */
  cancel: boolean;
}

/** Re-reads the mode (TypeScript cannot see mutations made through helper calls). */
export const modeOf = (s: { mode: PogoMode }): PogoMode => s.mode;

export const NEUTRAL_INPUT: Readonly<PogoInput> = Object.freeze({ tilt: 0, jumpHeld: false, boostPressed: false, pull: 0, cancel: false });

/**
 * PogoState — "Physics State" of LOCKED_SPEC §1.
 *
 * The first block is the physics truth, in the spec's own units (Q, T, degrees, L). The second block is derived
 * presentation data (metres, real seconds, radians) that is rewritten at the end of every tick; renderers, HUD and
 * audio read ONLY that block and never write to either.
 */
export interface PogoState {
  tick: number;

  // ── physics state (Q / T / deg / L) ────────────────────────────────────────────────────────────────────────
  /** Origin position (Q). +y is up. */
  qx: number; qy: number;
  /** v — velocity (Q/T). */
  qvx: number; qvy: number;
  /** s — slide velocity (Q/T). */
  sx: number; sy: number;
  /** θ — body angle (deg), 0 = upright, + = leaning toward −x; accumulates without wrap in the air. */
  theta: number;
  /** ω — angular velocity (deg/T). */
  omega: number;
  /** γ — gravity angle (deg). 0 on the main map; kept as a state variable because E1/E10/E14/E15 are written with it. */
  gamma: number;
  /** L — spring load, L_min / L_max — spring window, L_last — load of the last launch. */
  load: number; loadMin: number; loadMax: number; loadLast: number;
  /** g — grounded flag. */
  grounded: boolean;
  /** N — no-ground timer (T), J — jump timer (T). */
  noGround: number; jumpTimer: number;
  /** p — boost flag (power jump armed for the next landing). */
  boost: number;
  /** θ_j — angle at the last jump. */
  thetaJump: number;
  /** m — slide mode. */
  slideMode: boolean;
  /** n — surface normal (unit) and θ_n = atan2(n_z, n_x). */
  nx: number; ny: number; thetaN: number;
  /** tip position of the previous grounded frame (pivot memory, E5). */
  tipPrevX: number; tipPrevY: number;
  /** Spring bone state (E18): P_b, P_max, extension X, hull bottom z_min (relative to the origin). */
  springBone: number; springBoneMax: number; springExt: number; hullMinZ: number;
  /** Platform carry velocity c (Q/T) of the supporting moving platform (E17). */
  carryX: number; carryY: number;
  /** The hold input of the current tick (kept for the presentation layer). */
  held: boolean;
  /** Collider index the tip is standing on (valid while grounded). */
  groundId: number;

  // ── gameplay stats / respawn (not physics) ─────────────────────────────────────────────────────────────────
  jumps: number;
  boosts: number;
  falls: number;
  hazards: number;
  startedTick: number;
  finishedTick: number;
  lastImpact: number;
  safeGround: number;
  /** Safe tip point in the collider's BASE coordinates (Q) and the surface normal there. */
  safeLx: number; safeLy: number; safeNx: number; safeNy: number;
  /** Set to the tick of the last teleport (respawn) so renderers can skip interpolation. */
  teleportTick: number;

  // ── derived presentation data (metres / real seconds / radians) ────────────────────────────────────────────
  mode: PogoMode;
  /** Origin position (m). */
  x: number; y: number;
  /** Velocity (m/s, real time). */
  vx: number; vy: number;
  /** Stick angle (rad), + = leaning toward +x (render convention; = −θ). */
  angle: number;
  /** Angular velocity (rad/s, real time, render convention). */
  omegaRad: number;
  /** Highest origin height reached (m). */
  maxY: number;
  /** Charge fraction 0..1 = L / L_max (HUD ring, character crouch). */
  charge01: number;
  /** Boost armed (p ≥ 1) — HUD indicator. */
  boostReady: boolean;
  /** Surface normal in presentation terms (same as nx,ny). */
  gnx: number; gny: number;
}

/** Lowest point of the stick (Q), as a function of the physics state. */
export function tipCenterQ(cfg: PhysicsConfig, s: Pick<PogoState, 'qx' | 'qy' | 'theta'>, out: { x: number; y: number }): void {
  // axis from tip to origin = (−sin θ, cos θ); the tip circle centre lies tipLength − tipRadius below the origin
  const d = cfg.tipLength - cfg.tipRadius;
  out.x = s.qx + sinD(s.theta) * d;
  out.y = s.qy - cosD(s.theta) * d;
}

export interface Pose {
  /** Origin (m) */
  x: number; y: number;
  /** Render angle (rad), + = toward +x. */
  angle: number;
  /** Tip-circle centre */
  tipX: number; tipY: number;
  /** Lowest end of the stick (visual foot). */
  footX: number; footY: number;
  headX: number; headY: number;
}

export function computePose(s: Pick<PogoState, 'x' | 'y' | 'angle'>, cfg: PhysicsConfig, out: Pose): Pose {
  const g = geometryM(cfg);
  const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
  out.x = s.x; out.y = s.y; out.angle = s.angle;
  const lc = g.comHeight - g.tipRadius;
  out.tipX = s.x - dx * lc; out.tipY = s.y - dy * lc;
  out.footX = s.x - dx * g.comHeight; out.footY = s.y - dy * g.comHeight;
  const hd = g.headHeight - g.comHeight;
  out.headX = s.x + dx * hd; out.headY = s.y + dy * hd;
  return out;
}

export const newPose = (): Pose => ({ x: 0, y: 0, angle: 0, tipX: 0, tipY: 0, footX: 0, footY: 0, headX: 0, headY: 0 });

/** Rewrite the derived presentation block from the physics state. Called at the end of every tick and after any teleport. */
export function syncPresentation(s: PogoState, cfg: PhysicsConfig, finished = s.mode === 'FINISHED'): void {
  s.x = s.qx / cfg.qPerMetre; s.y = s.qy / cfg.qPerMetre;
  s.vx = speedQtToMs(cfg, s.qvx); s.vy = speedQtToMs(cfg, s.qvy);
  s.angle = -s.theta * DEG;
  s.omegaRad = -s.omega * DEG * ticksPerRealSecond(cfg);
  if (s.y > s.maxY) s.maxY = s.y;
  s.charge01 = s.loadMax > 0 ? Math.min(1, s.load / s.loadMax) : 0;
  s.boostReady = s.boost >= 1;
  s.gnx = s.nx; s.gny = s.ny;
  s.mode = finished ? 'FINISHED' : !s.grounded ? 'AIR' : s.load > 0 ? 'CHARGING' : 'GROUNDED';
}

/**
 * Place the player planted on `c` at the BASE-coordinate (Q) contact point (px,py) with surface normal (nx,ny):
 * the stick is aligned with the normal (θ = θ_n − 90), the tip circle rests on the surface, the spring window is the
 * one E7 gives for a landing with v = 0 (so no value is invented), L = 0.
 */
export function plantAt(s: PogoState, world: PhysicsWorld, cfg: PhysicsConfig, c: Collider, px: number, py: number, nx: number, ny: number, tick: number): void {
  const off = { x: 0, y: 0, vx: 0, vy: 0 };
  world.offsetAtQ(c, tick, off);
  const thetaN = atan2D(ny, nx);
  const theta = wrap180(thetaN - cfg.normalAngleOffset);
  const r = cfg.tipRadius;
  const tcx = off.x + px + nx * r, tcy = off.y + py + ny * r;
  const d = cfg.tipLength - r;
  const win = springWindow(cfg, 0, 0);
  s.tick = tick;
  s.qx = tcx + (-sinD(theta)) * d; s.qy = tcy + cosD(theta) * d;
  s.qvx = off.vx; s.qvy = off.vy; s.sx = 0; s.sy = 0;
  s.theta = theta; s.thetaJump = theta; s.omega = 0;
  s.load = 0; s.loadMin = win.min; s.loadMax = win.max; s.loadLast = 0;
  s.grounded = true; s.noGround = 0; s.jumpTimer = 0; s.boost = 0; s.slideMode = false;
  s.nx = nx; s.ny = ny; s.thetaN = thetaN;
  s.tipPrevX = tcx; s.tipPrevY = tcy;
  s.springBone = cfg.springRelaxFloor; s.springBoneMax = 0; s.springExt = 0; s.hullMinZ = cfg.hullMinZBase;
  s.carryX = 0; s.carryY = 0; s.held = false;
  s.groundId = c.index;
}

/** Initial state: planted on the surface under level.startPosition. */
export function createPogoState(world: PhysicsWorld, cfg: PhysicsConfig): PogoState {
  if (world.qPerMetre !== cfg.qPerMetre) throw new Error('PhysicsWorld.qPerMetre must equal cfg.qPerMetre');
  const start = world.level.startPosition;
  const g = world.findGround(start.x, start.y, 0.6);
  if (!g) throw new Error(`Level ${world.level.levelId}: startPosition is not on any solid surface`);
  const win = springWindow(cfg, 0, 0);
  const s: PogoState = {
    tick: 0,
    qx: 0, qy: 0, qvx: 0, qvy: 0, sx: 0, sy: 0, theta: 0, omega: 0, gamma: 0,
    load: 0, loadMin: win.min, loadMax: win.max, loadLast: 0,
    grounded: true, noGround: 0, jumpTimer: 0, boost: 0, thetaJump: 0, slideMode: false,
    nx: 0, ny: 1, thetaN: 90, tipPrevX: 0, tipPrevY: 0,
    springBone: cfg.springRelaxFloor, springBoneMax: 0, springExt: 0, hullMinZ: cfg.hullMinZBase,
    carryX: 0, carryY: 0, held: false, groundId: g.collider.index,
    jumps: 0, boosts: 0, falls: 0, hazards: 0, startedTick: -1, finishedTick: -1, lastImpact: 0,
    safeGround: g.collider.index, safeLx: 0, safeLy: 0, safeNx: g.nx, safeNy: g.ny, teleportTick: -1,
    mode: 'GROUNDED', x: 0, y: 0, vx: 0, vy: 0, angle: 0, omegaRad: 0, maxY: -Infinity, charge01: 0, boostReady: false, gnx: g.nx, gny: g.ny,
  };
  const k = cfg.qPerMetre;
  plantAt(s, world, cfg, g.collider, g.x * k, g.y * k, g.nx, g.ny, 0);
  s.safeLx = g.x * k; s.safeLy = g.y * k;
  syncPresentation(s, cfg);
  return s;
}

/**
 * Planted state at fraction `t` (0..1, left→right) along the most upward-facing edge of a collider.
 * Used by the Physics Lab ("Test Jump" on any platform), the solvability analysis and the route bot.
 */
export function createPlantedState(world: PhysicsWorld, cfg: PhysicsConfig, colliderIndex: number, t: number, tick = 0): PogoState {
  const c = world.colliders[colliderIndex];
  const { pts, eny } = c.qpoly;
  let best = 0;
  for (let i = 1; i < pts.length; i++) if (eny[i] > eny[best]) best = i;
  const a = pts[best], b = pts[(best + 1) % pts.length];
  // edge goes right→left for a CCW top face, so interpolate from b to a to get left→right
  const px = b.x + (a.x - b.x) * t, py = b.y + (a.y - b.y) * t;
  const nx = c.qpoly.enx[best], ny = eny[best];
  const s = createPogoState(world, cfg);
  plantAt(s, world, cfg, c, px, py, nx, ny, tick);
  s.safeGround = c.index; s.safeLx = px; s.safeLy = py; s.safeNx = nx; s.safeNy = ny;
  syncPresentation(s, cfg);
  return s;
}

export const degToRad = (d: number): number => d * DEG;

/** Rotate the stick to `theta` (deg) about the planted tip: the tip stays where it is (Lab / analysis helper). */
export function rotateAboutTip(cfg: PhysicsConfig, s: PogoState, theta: number): void {
  const tip = { x: 0, y: 0 };
  tipCenterQ(cfg, s, tip);
  const d = cfg.tipLength - cfg.tipRadius;
  s.theta = theta;
  s.qx = tip.x - sinD(theta) * d;
  s.qy = tip.y + cosD(theta) * d;
  s.tipPrevX = tip.x; s.tipPrevY = tip.y;
  syncPresentation(s, cfg);
}

/**
 * Teleport the player into free flight at (xM, yM) metres with velocity (vxQ, vyQ) in Q/T. Resets the spring and the
 * timers but keeps the stats. Used by the Lab and by tests; marks the teleport so renderers skip interpolation.
 */
export function placeInAir(cfg: PhysicsConfig, s: PogoState, xM: number, yM: number, vxQ = 0, vyQ = 0, theta = 0): void {
  s.qx = xM * cfg.qPerMetre; s.qy = yM * cfg.qPerMetre;
  s.qvx = vxQ; s.qvy = vyQ; s.sx = 0; s.sy = 0;
  s.theta = theta; s.thetaJump = theta; s.omega = 0;
  s.grounded = false; s.load = 0; s.noGround = 0; s.jumpTimer = 0; s.boost = 0; s.slideMode = false;
  s.springBone = cfg.springRelaxFloor; s.springBoneMax = 0; s.springExt = 0; s.hullMinZ = cfg.hullMinZBase;
  s.carryX = 0; s.carryY = 0; s.groundId = -1;
  s.teleportTick = s.tick;
  syncPresentation(s, cfg);
}
