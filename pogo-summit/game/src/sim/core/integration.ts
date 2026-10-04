import type { PogoState } from '../PogoState';
import { tipCenterQ } from '../PogoState';
import type { Offset } from '../PhysicsWorld';
import { moveWithCollision } from './collision';
import type { Frame, SimContext } from './frame';
import { capSpeed } from './air';
import { sinD } from '../math';

const OFF0: Offset = { x: 0, y: 0, vx: 0, vy: 0 };
const OFF1: Offset = { x: 0, y: 0, vx: 0, vy: 0 };
const TIP = { x: 0, y: 0 };

/**
 * Position integration — E3: d = ((v_x + s_x)·Δt, (v_z + 4·s_z)·Δt), applied with collision (semi-implicit Euler:
 * the velocity was already updated this tick by E1/E5). The movement vector is kept for the wall-bounce reflection.
 */
export function integratePosition(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  const dx = (s.qvx + s.sx) * f.dt;
  const dy = (s.qvy + cfg.slideZGain * s.sy) * f.dt;
  f.dispX = dx; f.dispY = dy;
  moveWithCollision(ctx, s, f, dx, dy);
}

/**
 * Moving-platform carry (our supplied mechanic — the original relies on the engine's entity push).
 * While grounded on a moving collider the body is moved by the platform's displacement this tick and the pivot memory
 * is re-anchored; the platform velocity c = Δp/Δt is kept for the release of E17.
 */
export function platformCarry(ctx: SimContext, s: PogoState, f: Frame): void {
  s.carryX = 0; s.carryY = 0;
  if (!s.grounded || s.groundId < 0) return;
  const c = ctx.world.colliders[s.groundId];
  if (!c || !c.move) return;
  ctx.world.offsetAtQ(c, s.tick, OFF1);
  ctx.world.offsetAtQ(c, s.tick - 1, OFF0);
  const dx = OFF1.x - OFF0.x, dy = OFF1.y - OFF0.y;
  if (dx === 0 && dy === 0) return;
  moveWithCollision(ctx, s, f, dx, dy);
  tipCenterQ(ctx.cfg, s, TIP);
  s.tipPrevX = TIP.x; s.tipPrevY = TIP.y;
  s.carryX = dx / f.dt; s.carryY = dy / f.dt;
}

/**
 * E17 — release from a moving platform: airborne with carry c ≠ 0:  v_x += c_x;  v_z += c_z·(1 − 0.75·[c_z < 0]); then E2.
 */
export function platformRelease(ctx: SimContext, s: PogoState): void {
  if (!s.grounded && (s.carryX !== 0 || s.carryY !== 0)) {
    s.qvx += s.carryX;
    s.qvy += s.carryY * (1 - ctx.cfg.platformDownReduction * (s.carryY < 0 ? 1 : 0));
  }
  s.carryX = 0; s.carryY = 0;
  capSpeed(ctx, s);
}

/**
 * E18 — spring extension and collision-hull bottom.
 *   airborne:  P_b ← max(P_b − 120·Δt, −200)
 *   X = 18·sin(0.9·P_b);   if X < 0: X ← X·(0.01·P_max)·(200 + X)·0.0025
 *   while N = 0:  z_min = −55 + max(X, −12.25)
 */
export function updateHull(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  if (!s.grounded) s.springBone = Math.max(s.springBone - cfg.springRelaxRate * f.dt, cfg.springRelaxFloor);
  let X = cfg.springExtAmp * sinD(cfg.springExtSineGain * s.springBone);
  if (X < 0) X = X * (((s.springBoneMax * cfg.springExtNegScale) * (cfg.springExtNegOffset + X)) * cfg.springExtNegGain);
  s.springExt = X;
  if (s.noGround <= 0) s.hullMinZ = cfg.hullMinZBase + Math.max(X, cfg.hullExtLimit);
}
