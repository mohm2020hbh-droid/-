import type { PogoState } from '../PogoState';
import { cosD, sinD } from '../math';
import type { Frame, SimContext } from './frame';

/**
 * Air physics — E1 gravity and drag, E2 speed cap.
 *   ê = R_γ·x̂,   a = R_γ·( −0.05·(v·ê)·Δt , −8.5·Δt ),   v ← v + a
 * On the main map γ = 0: v_x ← v_x − 0.05·v_x·Δt;  v_z ← v_z − 8.5·Δt  (no vertical drag).
 */
export function airStep(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  const c = cosD(s.gamma), sn = sinD(s.gamma);
  const vLat = s.qvx * c + s.qvy * sn;                 // v·ê
  const ax = -cfg.airDrag * vLat * f.dt;
  const ay = -cfg.gravity * f.dt;
  s.qvx += ax * c - ay * sn;
  s.qvy += ax * sn + ay * c;
}

/** E2: if |v| > 300 then v ← 300·v/|v|. */
export function capSpeed(ctx: SimContext, s: PogoState): void {
  const sp = Math.hypot(s.qvx, s.qvy);
  if (sp > ctx.cfg.maxSpeed) { const k = ctx.cfg.maxSpeed / sp; s.qvx *= k; s.qvy *= k; }
}
