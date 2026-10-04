import type { PogoState } from '../PogoState';
import type { PhysicsConfig } from '../PhysicsConfig';
import { tipCenterQ } from '../PogoState';
import type { Offset } from '../PhysicsWorld';
import { circleVsConvex, newContact, obbVsConvex } from '../geometry';
import { cosD, sinD } from '../math';
import type { Frame, ProbeResult, SimContext } from './frame';

/**
 * Collision resolution — the part the LOCKED SPEC does NOT lock (§7): the original engine's `c_move`/`c_trace` are not
 * available, so this module is OUR implementation of the two services the locked equations need:
 *
 *   moveWithCollision(d)   move the body by d, sliding along contacts, never tunnelling, and report "touched" + normal
 *                          (the engine's c_move + hit/normal)                                       [E3, E5, E17]
 *   probeGround()          sweep a ±4 Q circle from the origin to tip + (6 + |s_z|) along the stick  [E6]
 *   rotationAllowed()      collision-checked rotation (the engine's c_rotate)                         [E4]
 *
 * Collision shapes (all geometry values are the spec's hull numbers; the shape itself is our choice):
 *   - hull: box across ±12.5 Q and along the stick from z_min to +30 Q (E18), ORIENTED with the stick so it follows the
 *     body when the pogo leans. Floor-like contacts of the hull's LOWER end are ignored in the position resolution — the
 *     tip supports the body (E5/E6) — but every hull contact blocks a rotation, with the hull bottom lifted by 16 Q (P18).
 *   - tip : circle of radius 4 Q (the probe half-size) at the lower end of the stick.
 * Nothing here changes a locked equation; it only decides WHERE the body is and WHETHER it touched something.
 */
const CT = newContact();
const OFF: Offset = { x: 0, y: 0, vx: 0, vy: 0 };
const TIP = { x: 0, y: 0 };
const EPS = 1e-9;

/** The hull as an oriented box: centre, unit axis along the stick (tip → head), half-extents, and its world AABB. */
interface HullBox { cx: number; cy: number; ax: number; ay: number; hz: number; hx: number; minX: number; maxX: number; minY: number; maxY: number }
const HB: HullBox = { cx: 0, cy: 0, ax: 0, ay: 1, hz: 0, hx: 0, minX: 0, maxX: 0, minY: 0, maxY: 0 };

function hullBox(cfg: PhysicsConfig, s: Pick<PogoState, 'theta' | 'hullMinZ'>, lift: number, ox: number, oy: number, out: HullBox): HullBox {
  const ax = -sinD(s.theta), ay = cosD(s.theta);
  const z0 = s.hullMinZ + lift, z1 = cfg.hullMaxZ;
  const mid = (z0 + z1) / 2;
  out.hz = (z1 - z0) / 2; out.hx = cfg.hullHalfX;
  out.ax = ax; out.ay = ay;
  out.cx = ox + ax * mid; out.cy = oy + ay * mid;
  const ex = out.hz * Math.abs(ax) + out.hx * Math.abs(ay), ey = out.hz * Math.abs(ay) + out.hx * Math.abs(ax);
  out.minX = out.cx - ex; out.maxX = out.cx + ex; out.minY = out.cy - ey; out.maxY = out.cy + ey;
  return out;
}

function record(f: Frame, nx: number, ny: number, px: number, py: number, collider: number): void {
  f.touched = true; f.touchNx = nx; f.touchNy = ny; f.touchX = px; f.touchY = py; f.touchCollider = collider;
}

/** Push the body out of every solid it overlaps (tip circle + hull). Up to 4 relaxation passes. */
function resolveContacts(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg, world } = ctx;
  const r = cfg.tipRadius;
  for (let pass = 0; pass < 4; pass++) {
    let any = false;
    for (const c of world.solids) {
      // tip circle
      tipCenterQ(cfg, s, TIP);
      if (!(TIP.x + r < c.qMinX || TIP.x - r > c.qMaxX || TIP.y + r < c.qMinY || TIP.y - r > c.qMaxY)) {
        world.offsetAtQ(c, s.tick, OFF);
        if (circleVsConvex(c.qpoly, TIP.x - OFF.x, TIP.y - OFF.y, r, CT) && CT.depth > EPS) {
          s.qx += CT.nx * CT.depth; s.qy += CT.ny * CT.depth;
          record(f, CT.nx, CT.ny, CT.px + OFF.x, CT.py + OFF.y, c.index);
          any = true;
        }
      }
      // hull (oriented with the stick)
      hullBox(cfg, s, 0, s.qx, s.qy, HB);
      if (HB.maxX < c.qMinX || HB.minX > c.qMaxX || HB.maxY < c.qMinY || HB.minY > c.qMaxY) continue;
      world.offsetAtQ(c, s.tick, OFF);
      if (obbVsConvex(c.qpoly, HB.cx - OFF.x, HB.cy - OFF.y, HB.ax, HB.ay, HB.hz, HB.hx, CT) && CT.depth > EPS) {
        // the tip supports the body: ignore a floor-like contact of the hull's LOWER end (not its side, not its head end)
        if (CT.ny > 0.5 && CT.nx * HB.ax + CT.ny * HB.ay > 0.5 && !cfg.hullSupportsFloor) continue;
        s.qx += CT.nx * CT.depth; s.qy += CT.ny * CT.depth;
        record(f, CT.nx, CT.ny, s.qx, s.qy, c.index);
        any = true;
      }
    }
    if (!any) break;
  }
}

/** Move the origin by (dx, dy) Q with collision (sub-stepped). Sets `f.touched` and the last contact. */
export function moveWithCollision(ctx: SimContext, s: PogoState, f: Frame, dx: number, dy: number): void {
  const len = Math.hypot(dx, dy);
  const n = Math.max(1, Math.ceil(len / ctx.cfg.collisionStep));
  const sx = dx / n, sy = dy / n;
  for (let i = 0; i < n; i++) {
    s.qx += sx; s.qy += sy;
    resolveContacts(ctx, s, f);
  }
}

// ── ground probe (E6) ──────────────────────────────────────────────────────────────────────────────────────────
const PC = { nx: 0, ny: 1, collider: -1 };

function probeOverlap(ctx: SimContext, tick: number, x: number, y: number, r: number): boolean {
  const { world } = ctx;
  for (const c of world.solids) {
    if (x + r < c.qMinX || x - r > c.qMaxX || y + r < c.qMinY || y - r > c.qMaxY) continue;
    world.offsetAtQ(c, tick, OFF);
    if (circleVsConvex(c.qpoly, x - OFF.x, y - OFF.y, r, CT)) { PC.nx = CT.nx; PC.ny = CT.ny; PC.collider = c.index; return true; }
  }
  return false;
}

/**
 * E6: sweep the probe circle (radius = probeHalfSize) from the origin to `tip + (6 + |s_z|)·û_stick` where û_stick
 * is the unit vector from the origin toward the tip. Returns the first hit with the surface normal there.
 */
export function probeGround(ctx: SimContext, s: PogoState, out: ProbeResult): ProbeResult {
  const { cfg } = ctx;
  const ux = sinD(s.theta), uy = -cosD(s.theta);
  const len = cfg.tipLength + cfg.probeExtension + Math.abs(s.sy);
  const r = cfg.probeHalfSize;
  const step = cfg.collisionStep;
  out.hit = false;
  let prev = 0;
  for (let t = 0; ; t += step) {
    if (t > len) t = len;
    if (probeOverlap(ctx, s.tick, s.qx + ux * t, s.qy + uy * t, r)) {
      // refine: smallest t in (prev, t] that overlaps
      let lo = prev, hi = t;
      if (hi > lo) for (let i = 0; i < 8; i++) {
        const mid = (lo + hi) / 2;
        if (probeOverlap(ctx, s.tick, s.qx + ux * mid, s.qy + uy * mid, r)) hi = mid; else lo = mid;
      }
      probeOverlap(ctx, s.tick, s.qx + ux * hi, s.qy + uy * hi, r);
      out.hit = true; out.nx = PC.nx; out.ny = PC.ny; out.collider = PC.collider;
      return out;
    }
    if (t >= len) break;
    prev = t;
  }
  return out;
}

// ── collision-checked rotation (the engine's c_rotate) ─────────────────────────────────────────────────────────
/** Deepest overlap of the hull (bottom lifted by `lift`) with any solid, for a body whose origin is (ox, oy) and angle `theta`. */
function bodyOverlap(ctx: SimContext, s: PogoState, ox: number, oy: number, theta: number, lift: number): number {
  const { cfg, world } = ctx;
  hullBox(cfg, { theta, hullMinZ: s.hullMinZ }, lift, ox, oy, HB);
  let depth = 0;
  for (const c of world.solids) {
    if (HB.maxX < c.qMinX || HB.minX > c.qMaxX || HB.maxY < c.qMinY || HB.minY > c.qMaxY) continue;
    world.offsetAtQ(c, s.tick, OFF);
    if (obbVsConvex(c.qpoly, HB.cx - OFF.x, HB.cy - OFF.y, HB.ax, HB.ay, HB.hz, HB.hx, CT) && CT.depth > depth) depth = CT.depth;
  }
  return depth;
}

/**
 * Collision check for a rotation to `newTheta`: the body (after the tip pivot when grounded) must not newly overlap a
 * solid. Rotation that does not worsen an existing overlap is always allowed, so the player can never get stuck.
 */
export function rotationAllowed(ctx: SimContext, s: PogoState, newTheta: number): boolean {
  const { cfg } = ctx;
  let ox = s.qx, oy = s.qy;
  if (s.grounded) {
    const d = cfg.tipLength - cfg.tipRadius;
    const tx = s.qx + sinD(newTheta) * d, ty = s.qy - cosD(newTheta) * d;
    const dx = s.tipPrevX - tx, dy = s.tipPrevY - ty;
    if (Math.abs(dx) < cfg.tipPivotLimit && Math.abs(dy) < cfg.tipPivotLimit) { ox += dx; oy += dy; }
  }
  const lift = cfg.rotationHullLift;
  const next = bodyOverlap(ctx, s, ox, oy, newTheta, lift);
  if (next <= 0.01) return true;
  return next <= bodyOverlap(ctx, s, s.qx, s.qy, s.theta, lift);
}

// ── triggers (hazard / goal) ───────────────────────────────────────────────────────────────────────────────────
/** True if the body (hull box or tip circle) overlaps the trigger collider. */
export function overlapsTrigger(ctx: SimContext, s: PogoState, colliderIndex: number): boolean {
  const { cfg, world } = ctx;
  const c = world.colliders[colliderIndex];
  world.offsetAtQ(c, s.tick, OFF);
  tipCenterQ(cfg, s, TIP);
  if (circleVsConvex(c.qpoly, TIP.x - OFF.x, TIP.y - OFF.y, cfg.tipRadius, CT)) return true;
  hullBox(cfg, s, 0, s.qx, s.qy, HB);
  return obbVsConvex(c.qpoly, HB.cx - OFF.x, HB.cy - OFF.y, HB.ax, HB.ay, HB.hz, HB.hx, CT);
}
