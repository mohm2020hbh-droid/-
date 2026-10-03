import type { PhysicsConfig } from './PhysicsConfig';
import type { Collider, Offset, PhysicsWorld } from './PhysicsWorld';
import { type PogoInput, type PogoState, type Pose, NEUTRAL_INPUT, computePose, createPogoState, modeOf, newPose, tipCenterLen } from './PogoState';
import { SURFACES, type SurfaceDef, classifyNormal, isStable, surfaceRestitution } from './SurfacePhysics';
import { type CircleKind, aimedNormal, frictionStep, impactKick, keptTangential, reboundSpeed } from './CollisionResponse';
import { launchPower, performLaunch } from './JumpSystem';
import { pressBoost, resetSpin, updateSpin } from './BoostSystem';
import { type SimEvent, makeEvent } from './events';
import { circleVsConvex, closestOnPoly, newContact, pointInPoly } from './geometry';
import { DEG, DT, approach, clamp, q } from './math';

/**
 * PogoPhysicsController — the deterministic 120 Hz pogo simulation.
 *
 * Model (DECISIONS DEC-013): the player CONTROLS the stick ANGLE and the jump TIMING, never the velocity
 * (XLSX F-011 grade B / PDF p.10, p.19). Internally: a point mass at the centre of mass + a prescribed rotation +
 * three collision circles (tip / torso / head). Everything numeric comes from PhysicsConfig (TUNE_ME).
 *
 *   GROUNDED  tip planted; stick angle eases toward  surfaceNormal + tilt·tiltMaxAngle  (ground control)
 *   CHARGING  planted + jump held; release ⇒ launch along the stick axis (+ platform velocity + retained slide)
 *   AIR       gravity, angular velocity from tilt (air control), momentum preserved, collisions
 *   SLIDING   contact on a surface too steep/slippery to plant on
 *   FINISHED  goal reached
 */
export interface SimContext { world: PhysicsWorld; cfg: PhysicsConfig }

const OFF: Offset = { x: 0, y: 0, vx: 0, vy: 0 };
const CT = newContact();
const CP = { x: 0, y: 0, nx: 0, ny: 1, dist: 0 };
const AIM = { x: 0, y: 0 };
const SUPPORT_TOL = 0.05;
const MAX_STEP = 0.12; // metres per sub-step (tunnelling guard)
let slidingFlag = false;

type ContactResult = 'none' | 'planted' | 'hit';

// ─────────────────────────────────────────────────────────────────────────────
// public stepping API
// ─────────────────────────────────────────────────────────────────────────────
export function stepPogo(ctx: SimContext, s: PogoState, inp: PogoInput, ev: SimEvent[]): void {
  const { cfg } = ctx;
  if (s.mode === 'FINISHED') { s.tick++; return; }
  s.tick++;

  const released = !inp.jumpHeld && s.prevHeld;
  const rising = inp.jumpHeld && !s.prevHeld;
  s.prevHeld = inp.jumpHeld;
  if (rising) s.pressBuffer = cfg.pressBufferTicks;
  else if (!inp.jumpHeld) s.pressBuffer = 0;
  else if (s.pressBuffer > 0) s.pressBuffer--;

  if (inp.boostPressed) pressBoost(s, cfg, ev);

  const tilt = clamp(inp.tilt, -1, 1);
  if (s.mode === 'GROUNDED' || s.mode === 'CHARGING') stepPlanted(ctx, s, inp, tilt, rising, ev);
  else stepFree(ctx, s, inp, tilt, released, ev);

  if (modeOf(s) !== 'FINISHED') checkTriggers(ctx, s, ev);
  if (modeOf(s) !== 'FINISHED' && s.y < ctx.world.level.killY) {
    s.falls++;
    ev.push(makeEvent('fall', s.tick, s.x, s.y, 1));
    respawn(ctx, s, ev);
  }
  if (s.y > s.maxY) s.maxY = s.y;
  quantize(s);
}

function quantize(s: PogoState): void {
  s.x = q(s.x); s.y = q(s.y); s.vx = q(s.vx); s.vy = q(s.vy);
  s.angle = q(s.angle); s.omega = q(s.omega); s.lx = q(s.lx); s.ly = q(s.ly); s.slideV = q(s.slideV);
}

// ─────────────────────────────────────────────────────────────────────────────
// planted (GROUNDED / CHARGING)
// ─────────────────────────────────────────────────────────────────────────────
function stepPlanted(ctx: SimContext, s: PogoState, inp: PogoInput, tilt: number, rising: boolean, ev: SimEvent[]): void {
  const { cfg, world } = ctx;
  const g = world.colliders[s.groundId];
  const surf = SURFACES[g.surface];
  world.offsetAt(g, s.tick, OFF);
  const ox = OFF.x, oy = OFF.y, ovx = OFF.vx, ovy = OFF.vy; // bodyPenetration() reuses OFF, so copy
  const r = cfg.tipRadius;
  const lc = tipCenterLen(cfg);

  // 1) residual slide along the surface tangent (ice keeps it, rock brakes it quickly)
  let tx = s.gny, ty = -s.gnx;
  if (surf.push) s.slideV = clamp(s.slideV + g.dir * surf.push * cfg.acceleration * DT, -cfg.maxHorizontalSpeed, cfg.maxHorizontalSpeed);
  if (s.slideV !== 0) {
    s.lx += tx * s.slideV * DT;
    s.ly += ty * s.slideV * DT;
    if (!surf.push) s.slideV = approach(s.slideV, 0, cfg.deceleration * surf.friction * DT);
    if (Math.abs(s.slideV) < 0.01) s.slideV = 0;
  }

  // 2) re-snap the tip to the surface; lose support past the edge
  closestOnPoly(g.poly, s.lx, s.ly, CP);
  if (pointInPoly(g.poly, s.lx, s.ly)) { CP.nx = -CP.nx; CP.ny = -CP.ny; }
  let tcx = ox + s.lx, tcy = oy + s.ly;
  const lost = CP.dist > r + SUPPORT_TOL;
  if (!lost) {
    s.lx = CP.x + CP.nx * r; s.ly = CP.y + CP.ny * r;
    s.gnx = CP.nx; s.gny = CP.ny;
    tx = s.gny; ty = -s.gnx;
    tcx = ox + s.lx; tcy = oy + s.ly;
  }

  if (lost || !isStable(s.gny, surf, cfg)) {
    // leave the ground: keep platform velocity + slide as free velocity
    const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
    s.x = tcx + dx * lc; s.y = tcy + dy * lc;
    s.vx = ovx + tx * s.slideV;
    s.vy = ovy + ty * s.slideV;
    s.coyote = s.mode === 'CHARGING' && lost ? cfg.coyoteTicks : 0;
    if (!s.coyote) s.charge = 0;
    s.mode = lost ? 'AIR' : 'SLIDING';
    s.slideV = 0;
    s.airTicks = 0;
    ev.push(makeEvent('slide', s.tick, tcx, tcy, 0.3, s.gnx, s.gny, g.surface, g.material));
    return;
  }

  // 3) stick rotation about the planted tip (ground control): angle eases toward a target set by tilt
  const nAng = Math.atan2(s.gnx, s.gny);
  const maxTilt = cfg.tiltMaxAngle * DEG;
  const ctl = clamp(tilt * cfg.groundControl, -1, 1);
  const target = nAng + ctl * maxTilt;
  const rateMax = cfg.tiltRateGround * DEG * cfg.groundControl;
  const desired = clamp((target - s.angle) * 12, -rateMax, rateMax);
  s.omega = approach(s.omega, desired, cfg.turnSpeed * DEG * DT);
  let na = s.angle + s.omega * DT;
  if ((s.angle - target) * (na - target) < 0) { na = target; s.omega = 0; }
  if (na !== s.angle) {
    const before = bodyPenetration(ctx, tcx, tcy, s.angle, s.tick);
    const after = bodyPenetration(ctx, tcx, tcy, na, s.tick);
    if (after <= Math.max(before, 0.004)) s.angle = na; else s.omega = 0;
  }
  s.x = tcx + Math.sin(s.angle) * lc;
  s.y = tcy + Math.cos(s.angle) * lc;
  s.vx = ovx + tx * s.slideV;
  s.vy = ovy + ty * s.slideV;
  s.plantedTicks++;
  s.airTicks = 0;

  // 4) charge → launch
  if (s.mode === 'GROUNDED' && inp.jumpHeld && (rising || s.pressBuffer > 0)) {
    s.mode = 'CHARGING';
    s.charge = 0;
    s.pressBuffer = 0;
    ev.push(makeEvent('charge_start', s.tick, tcx, tcy, 0, s.gnx, s.gny, g.surface, g.material));
  }
  if (s.mode === 'CHARGING') {
    if (inp.cancel) { s.mode = 'GROUNDED'; s.charge = 0; }
    else if (inp.jumpHeld) s.charge = Math.min(s.charge + 1, cfg.chargeTicksMax);
    else {
      const power = launchPower(s, cfg, inp.pull);
      performLaunch(s, cfg, surf, power, ovx, ovy, tx, ty, g.material, ev);
      return;
    }
  }

  // 5) remember a safe respawn spot once we have stood still long enough
  if (s.plantedTicks === cfg.safeTicks && g.safe && Math.abs(s.slideV) < 0.1) {
    s.safeGround = g.index; s.safeLx = s.lx; s.safeLy = s.ly; s.safeNx = s.gnx; s.safeNy = s.gny;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// free motion (AIR / SLIDING)
// ─────────────────────────────────────────────────────────────────────────────
function stepFree(ctx: SimContext, s: PogoState, inp: PogoInput, tilt: number, released: boolean, ev: SimEvent[]): void {
  const { cfg, world } = ctx;

  if (s.coyote > 0) {
    s.coyote--;
    if (released && s.charge > 0 && s.groundId >= 0) {
      const g = world.colliders[s.groundId];
      performLaunch(s, cfg, SURFACES[g.surface], launchPower(s, cfg, inp.pull), s.vx, s.vy, s.gny, -s.gnx, g.material, ev);
      return;
    }
    if (inp.cancel) s.charge = 0;
  } else if (s.charge > 0) s.charge = 0;

  // air control: tilt drives angular velocity (rotation), not linear velocity
  const rateAir = cfg.tiltRateAir * DEG * cfg.airControl;
  if (tilt !== 0) s.omega = approach(s.omega, tilt * rateAir, cfg.turnSpeed * DEG * cfg.airControl * DT);
  else s.omega *= Math.max(0, 1 - cfg.angularDamping * DT);
  s.angle += s.omega * DT;
  updateSpin(s, cfg, ev);

  s.vy -= cfg.gravity * DT;
  if (s.vy < -cfg.maxFallSpeed) s.vy = -cfg.maxFallSpeed;
  s.vx = clamp(s.vx, -cfg.maxHorizontalSpeed, cfg.maxHorizontalSpeed);

  slidingFlag = false;
  const speed = Math.hypot(s.vx, s.vy);
  const n = clamp(Math.ceil((speed * DT) / MAX_STEP), 1, 4);
  const h = DT / n;
  for (let k = 0; k < n; k++) {
    s.x += s.vx * h;
    s.y += s.vy * h;
    if (resolveContacts(ctx, s, ev, h)) return; // planted
  }
  s.mode = slidingFlag ? 'SLIDING' : 'AIR';
  s.airTicks++;
}

function circleOf(ctx: SimContext, s: PogoState, ci: number, out: { x: number; y: number; r: number }): void {
  const { cfg } = ctx;
  const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
  if (ci === 0) { const lc = tipCenterLen(cfg); out.x = s.x - dx * lc; out.y = s.y - dy * lc; out.r = cfg.tipRadius; }
  else if (ci === 1) { out.x = s.x; out.y = s.y; out.r = cfg.bodyRadius; }
  else { const hh = cfg.headHeight - cfg.comHeight; out.x = s.x + dx * hh; out.y = s.y + dy * hh; out.r = cfg.headRadius; }
}
const CIRC = { x: 0, y: 0, r: 0 };
const KINDS: CircleKind[] = ['tip', 'torso', 'head'];

/** Returns true if the tip got planted (state is then GROUNDED). */
function resolveContacts(ctx: SimContext, s: PogoState, ev: SimEvent[], h: number): boolean {
  const { world } = ctx;
  for (let pass = 0; pass < 3; pass++) {
    let any = false;
    for (let ci = 0; ci < 3; ci++) {
      for (const c of world.solids) {
        circleOf(ctx, s, ci, CIRC);
        if (CIRC.x + CIRC.r < c.minX || CIRC.x - CIRC.r > c.maxX || CIRC.y + CIRC.r < c.minY || CIRC.y - CIRC.r > c.maxY) continue;
        world.offsetAt(c, s.tick, OFF);
        if (!circleVsConvex(c.poly, CIRC.x - OFF.x, CIRC.y - OFF.y, CIRC.r, CT)) continue;
        any = true;
        const res = ci === 0 ? handleTip(ctx, s, c, h, ev) : handleBody(ctx, s, KINDS[ci], c, ev);
        if (res === 'planted') return true;
      }
    }
    if (!any) break;
  }
  return false;
}

function handleBody(ctx: SimContext, s: PogoState, kind: CircleKind, c: Collider, ev: SimEvent[]): ContactResult {
  const { cfg } = ctx;
  const nx = CT.nx, ny = CT.ny;
  s.x += nx * CT.depth;
  s.y += ny * CT.depth;
  const rvx = s.vx - OFF.vx, rvy = s.vy - OFF.vy;
  const vn = rvx * nx + rvy * ny;
  if (vn >= 0) return 'none';
  const impact = -vn;
  const tx = ny, ty = -nx;
  const vt = rvx * tx + rvy * ty;
  const e = ny > 0.7 ? cfg.floorRestitution : cfg.wallRestitution;
  const vnOut = reboundSpeed(impact, e, cfg.restSpeed);
  const vtOut = impact < cfg.restSpeed ? vt : keptTangential(vt, cfg);
  s.vx = OFF.vx + nx * vnOut + tx * vtOut;
  s.vy = OFF.vy + ny * vnOut + ty * vtOut;
  if (impact >= cfg.restSpeed) s.omega += impactKick(kind, nx, impact, cfg);
  if (impact > 3) {
    ev.push(makeEvent('wall_hit', s.tick, CT.px + OFF.x, CT.py + OFF.y, clamp(impact / cfg.hardImpactSpeed, 0, 1), nx, ny, c.surface, c.material));
    if (impact >= cfg.hardImpactSpeed) ev.push(makeEvent('hard_impact', s.tick, CT.px + OFF.x, CT.py + OFF.y, 1, nx, ny, c.surface, c.material));
  }
  return 'hit';
}

function handleTip(ctx: SimContext, s: PogoState, c: Collider, h: number, ev: SimEvent[]): ContactResult {
  const { cfg } = ctx;
  const surf = SURFACES[c.surface];
  const nx = CT.nx, ny = CT.ny;
  const px = CT.px + OFF.x, py = CT.py + OFF.y;
  s.x += nx * CT.depth;
  s.y += ny * CT.depth;
  const rvx = s.vx - OFF.vx, rvy = s.vy - OFF.vy;
  const vn = rvx * nx + rvy * ny;
  if (vn >= 0) return 'none';
  const impact = -vn;
  const tx = ny, ty = -nx;
  const vt = rvx * tx + rvy * ty;
  const geo = classifyNormal(ny, cfg);
  const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
  const inc = Math.acos(clamp(dx * nx + dy * ny, -1, 1));

  const deflect = (restitution: number): ContactResult => {
    const vnOut = reboundSpeed(impact, restitution, cfg.restSpeed);
    const vtOut = impact < cfg.restSpeed ? vt : keptTangential(vt, cfg);
    s.vx = OFF.vx + nx * vnOut + tx * vtOut;
    s.vy = OFF.vy + ny * vnOut + ty * vtOut;
    if (impact >= cfg.restSpeed) s.omega += impactKick('tip', nx, impact, cfg);
    if (impact > 3) ev.push(makeEvent('wall_hit', s.tick, px, py, clamp(impact / cfg.hardImpactSpeed, 0, 1), nx, ny, c.surface, c.material));
    return 'hit';
  };

  if (geo === 'wall' || geo === 'ceiling') return deflect(cfg.wallRestitution);

  if (surf.bounce) {
    // Spring pad: automatic rebound along the normal, aimable by the stick tilt within bounceAimMax.
    aimedNormal(nx, ny, dx, dy, cfg.bounceAimMax * DEG, AIM);
    const speed = Math.max(surf.bounce.minSpeed, impact * surf.restitution);
    s.vx = OFF.vx + AIM.x * speed + tx * vt * 0.5;
    s.vy = OFF.vy + AIM.y * speed + ty * vt * 0.5;
    s.vx = clamp(s.vx, -cfg.maxHorizontalSpeed, cfg.maxHorizontalSpeed);
    resetSpin(s);
    ev.push(makeEvent('bounce', s.tick, px, py, clamp(speed / cfg.launchSpeedMax, 0, 1), nx, ny, c.surface, c.material, c.index));
    return 'hit';
  }

  if (inc > cfg.stableLandingAngle * DEG) return deflect(Math.max(cfg.wallRestitution, cfg.floorRestitution)); // bad angle ⇒ glance off

  const e = surfaceRestitution(surf, cfg);
  const vnOut = reboundSpeed(impact, e, cfg.restSpeed);
  const stable = isStable(ny, surf, cfg);

  if (stable) {
    if (vnOut < cfg.plantSpeed) { plant(ctx, s, c, surf, vt, impact, ev); return 'planted'; }
    // soft hop (restitution high enough to leave the surface)
    s.vx = OFF.vx + nx * vnOut + tx * keptTangential(vt, cfg);
    s.vy = OFF.vy + ny * vnOut + ty * keptTangential(vt, cfg);
    ev.push(makeEvent('land', s.tick, px, py, clamp(impact / (cfg.hardImpactSpeed * 1.2), 0, 1), nx, ny, c.surface, c.material));
    return 'hit';
  }

  // unstable (steep / non-plantable): contact sliding with kinetic friction
  const vtOut = frictionStep(vt, cfg.slideFriction * surf.friction, cfg.gravity, ny, h);
  s.vx = OFF.vx + nx * vnOut + tx * vtOut;
  s.vy = OFF.vy + ny * vnOut + ty * vtOut;
  slidingFlag = true;
  if (impact > cfg.restSpeed * 2) ev.push(makeEvent('land', s.tick, px, py, clamp(impact / (cfg.hardImpactSpeed * 1.2), 0, 1), nx, ny, c.surface, c.material));
  return 'hit';
}

function plant(ctx: SimContext, s: PogoState, c: Collider, surf: SurfaceDef, vt: number, impact: number, ev: SimEvent[]): void {
  const { cfg } = ctx;
  const lc = tipCenterLen(cfg);
  const nx = CT.nx, ny = CT.ny;
  const tcx = s.x - Math.sin(s.angle) * lc, tcy = s.y - Math.cos(s.angle) * lc;
  s.mode = 'GROUNDED';
  s.groundId = c.index;
  s.lx = tcx - OFF.x; s.ly = tcy - OFF.y;
  s.gnx = nx; s.gny = ny;
  s.slideV = vt * (1 - cfg.landingResponse) * (1 - cfg.energyLoss);
  s.omega += (cfg.landingResponse * vt) / lc; // momentum → stick swing toward the direction of travel
  s.vx = OFF.vx + ny * s.slideV;
  s.vy = OFF.vy - nx * s.slideV;
  s.charge = 0; s.coyote = 0; s.plantedTicks = 0; s.airTicks = 0;
  s.lastImpact = impact;
  resetSpin(s);
  const k = clamp(impact / (cfg.hardImpactSpeed * 1.2), 0, 1);
  ev.push(makeEvent('land', s.tick, CT.px + OFF.x, CT.py + OFF.y, k, nx, ny, c.surface, c.material));
  if (impact >= cfg.hardImpactSpeed) ev.push(makeEvent('hard_impact', s.tick, CT.px + OFF.x, CT.py + OFF.y, 1, nx, ny, c.surface, c.material));
  if (surf.push) ev.push(makeEvent('boost_pad', s.tick, CT.px + OFF.x, CT.py + OFF.y, 1, nx, ny, c.surface, c.material));
}

/** Max penetration depth of the torso + head circles at a hypothetical stick angle (tip centre fixed). */
function bodyPenetration(ctx: SimContext, tcx: number, tcy: number, ang: number, tick: number): number {
  const { cfg, world } = ctx;
  const lc = tipCenterLen(cfg);
  const dx = Math.sin(ang), dy = Math.cos(ang);
  const hh = cfg.headHeight - cfg.comHeight;
  const bx = tcx + dx * lc, by = tcy + dy * lc;
  const hx = bx + dx * hh, hy = by + dy * hh;
  let depth = 0;
  for (const c of world.solids) {
    world.offsetAt(c, tick, OFF);
    if (!(bx + cfg.bodyRadius < c.minX || bx - cfg.bodyRadius > c.maxX || by + cfg.bodyRadius < c.minY || by - cfg.bodyRadius > c.maxY)
      && circleVsConvex(c.poly, bx - OFF.x, by - OFF.y, cfg.bodyRadius, CT)) depth = Math.max(depth, CT.depth);
    if (!(hx + cfg.headRadius < c.minX || hx - cfg.headRadius > c.maxX || hy + cfg.headRadius < c.minY || hy - cfg.headRadius > c.maxY)
      && circleVsConvex(c.poly, hx - OFF.x, hy - OFF.y, cfg.headRadius, CT)) depth = Math.max(depth, CT.depth);
  }
  return depth;
}

function checkTriggers(ctx: SimContext, s: PogoState, ev: SimEvent[]): void {
  const { world } = ctx;
  for (const c of world.triggers) {
    for (let ci = 0; ci < 3; ci++) {
      circleOf(ctx, s, ci, CIRC);
      if (CIRC.x + CIRC.r < c.minX || CIRC.x - CIRC.r > c.maxX || CIRC.y + CIRC.r < c.minY || CIRC.y - CIRC.r > c.maxY) continue;
      if (!circleVsConvex(c.poly, CIRC.x, CIRC.y, CIRC.r, CT)) continue;
      if (c.kind === 'hazard') {
        s.hazards++;
        ev.push(makeEvent('hazard', s.tick, CT.px, CT.py, 1, CT.nx, CT.ny, c.surface, c.material));
        respawn(ctx, s, ev);
        return;
      }
      if (c.kind === 'goal') {
        s.mode = 'FINISHED';
        s.finishedTick = s.tick;
        s.vx = 0; s.vy = 0; s.omega = 0;
        ev.push(makeEvent('goal', s.tick, s.x, s.y, 1));
        return;
      }
    }
  }
}

/** Put the player back on the last safe spot (plain reset of dynamic state, stats preserved). */
export function respawn(ctx: SimContext, s: PogoState, ev: SimEvent[]): void {
  const { cfg, world } = ctx;
  const g = world.colliders[s.safeGround];
  world.offsetAt(g, s.tick, OFF);
  s.mode = 'GROUNDED';
  s.groundId = g.index;
  s.lx = s.safeLx; s.ly = s.safeLy; s.gnx = s.safeNx; s.gny = s.safeNy;
  s.angle = Math.atan2(s.gnx, s.gny);
  s.omega = 0; s.slideV = 0; s.charge = 0; s.pressBuffer = 0; s.coyote = 0; s.plantedTicks = 0;
  s.boostQueued = false;
  resetSpin(s);
  const lc = tipCenterLen(cfg);
  s.x = OFF.x + s.lx + Math.sin(s.angle) * lc;
  s.y = OFF.y + s.ly + Math.cos(s.angle) * lc;
  s.vx = OFF.vx; s.vy = OFF.vy;
  s.teleportTick = s.tick;
  ev.push(makeEvent('respawn', s.tick, s.x, s.y, 0));
}

// ─────────────────────────────────────────────────────────────────────────────
// class wrapper used by the game (keeps previous pose for render interpolation)
// ─────────────────────────────────────────────────────────────────────────────
export class PogoPhysicsController {
  readonly ctx: SimContext;
  state: PogoState;
  readonly events: SimEvent[] = [];
  /** Pose before the last step (for interpolation). */
  prev = { x: 0, y: 0, angle: 0 };
  private poseTmp: Pose = newPose();

  constructor(world: PhysicsWorld, cfg: PhysicsConfig) {
    this.ctx = { world, cfg };
    this.state = createPogoState(world, cfg);
    this.syncPrev();
  }

  get cfg(): PhysicsConfig { return this.ctx.cfg; }
  get world(): PhysicsWorld { return this.ctx.world; }

  reset(): void {
    this.state = createPogoState(this.ctx.world, this.ctx.cfg);
    this.events.length = 0;
    this.syncPrev();
  }

  /** Advance one tick. Returns the (reused) event array for this tick. */
  step(input: PogoInput = NEUTRAL_INPUT): SimEvent[] {
    this.syncPrev();
    this.events.length = 0;
    stepPogo(this.ctx, this.state, input, this.events);
    if (this.state.teleportTick === this.state.tick) this.syncPrev();
    return this.events;
  }

  private syncPrev(): void { this.prev.x = this.state.x; this.prev.y = this.state.y; this.prev.angle = this.state.angle; }

  /** Interpolated pose (alpha 0..1 between prev and current). */
  pose(alpha: number): Pose {
    const s = this.state;
    const a = clamp(alpha, 0, 1);
    return computePose({ x: this.prev.x + (s.x - this.prev.x) * a, y: this.prev.y + (s.y - this.prev.y) * a, angle: this.prev.angle + (s.angle - this.prev.angle) * a }, this.ctx.cfg, this.poseTmp);
  }
}
