import type { PogoState } from '../PogoState';
import { makeEvent } from '../events';
import { atan2D, clamp } from '../math';
import { tipCenterQ } from '../PogoState';
import { probeGround } from './collision';
import { impactOf, springWindow } from './charge';
import { enterSlide } from './slide';
import type { Frame, SimContext } from './frame';

const TIPC = { x: 0, y: 0 };

/**
 * Ground contact — E6 (probe + grounded flag), E16 (ledge exit), E7 (landing).
 *
 *   probe skipped while N > 0 (treated as a miss)
 *   g_before ∧ ¬hit ∧ N = 0  ⇒ v ← (s_x, 5)                       E16
 *   touched ∧ hit            ⇒ g ← 1
 *   ¬hit                     ⇒ g ← 0
 *   ¬g                       ⇒ L ← 0
 *   ¬g_before ∧ g            ⇒ landing: slide entry (E15), I = 1.65|v|^0.925, L_min, L_max (E7)
 */
export function groundContact(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  const probe = f.probe;
  if (s.noGround > 0) { probe.hit = false; }
  else {
    probeGround(ctx, s, probe);
    if (f.gBefore && !probe.hit) { s.qvx = s.sx; s.qvy = cfg.ledgeExitPop; }   // E16
  }
  if (f.touched && probe.hit) s.grounded = true;
  if (!probe.hit) s.grounded = false;
  if (probe.hit && s.grounded) {
    s.nx = probe.nx; s.ny = probe.ny; s.thetaN = atan2D(probe.ny, probe.nx); s.groundId = probe.collider;
  }
  if (!s.grounded) s.load = 0;
  f.landed = !f.gBefore && s.grounded;
}

/** Landing frame (E7 + slide entry). */
export function onLanding(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  enterSlide(ctx, s, f, s.groundId);
  const speed = Math.hypot(s.qvx, s.qvy);
  const I = impactOf(cfg, speed);
  const win = springWindow(cfg, speed, s.boost);
  s.loadMin = win.min; s.loadMax = win.max;
  s.lastImpact = I;
  const k = cfg.qPerMetre;
  const c = ctx.world.colliders[s.groundId];
  f.ev.push(makeEvent('land', s.tick, s.qx / k, s.qy / k, clamp(I / cfg.hardImpactLoad, 0, 1), s.nx, s.ny, c?.surface, c?.material, s.groundId));
  if (I >= cfg.hardImpactLoad) f.ev.push(makeEvent('hard_impact', s.tick, s.qx / k, s.qy / k, 1, s.nx, s.ny, c?.surface, c?.material));
  if (cfg.safeLandingRecord && c && c.safe && !s.slideMode) {
    // record the tip contact point in the collider's base coordinates as the respawn point
    const off = { x: 0, y: 0, vx: 0, vy: 0 };
    ctx.world.offsetAtQ(c, s.tick, off);
    const r = cfg.tipRadius;
    tipCenterQ(cfg, s, TIPC);
    const tcx = TIPC.x, tcy = TIPC.y;
    s.safeGround = c.index; s.safeLx = tcx - off.x - s.nx * r; s.safeLy = tcy - off.y - s.ny * r; s.safeNx = s.nx; s.safeNy = s.ny;
  }
}
