import type { PogoState } from '../PogoState';
import { makeEvent } from '../events';
import { clamp, cosD, sinD } from '../math';
import { isSlippery } from '../SurfacePhysics';
import type { Frame, SimContext } from './frame';

/**
 * Slide / ice — E15.
 *   In slide mode (m = 1), per axis:   t = 48·normalize(n + R_γ·(0,−1));   s_i ← s_i + clamp(0.25·(t_i − s_i), ±1.35)·Δt
 *   Out of slide mode (m = 0):         |s| < 0.25 ∨ ¬g ⇒ s ← 0, else s ← s·(1 − 0.5·Δt)
 * Entry (landing on a slippery surface): m ← 1, s_x ← v_x, s_z ← 0 (main map). Landing elsewhere: m ← 0.
 */
export function updateSlide(ctx: SimContext, s: PogoState, f: Frame): void {
  const { cfg } = ctx;
  if (s.slideMode) {
    // g = R_γ·(0,−1);  t = 48·normalize(n + g)
    const gx = sinD(s.gamma), gy = -cosD(s.gamma);
    let tx = s.nx + gx, ty = s.ny + gy;
    const len = Math.hypot(tx, ty);
    if (len > 1e-12) { const k = cfg.slideTargetSpeed / len; tx *= k; ty *= k; } else { tx = 0; ty = 0; }
    s.sx += clamp((tx - s.sx) * cfg.slideResponse, -cfg.slideAccelClamp, cfg.slideAccelClamp) * f.dt;
    s.sy += clamp((ty - s.sy) * cfg.slideResponse, -cfg.slideAccelClamp, cfg.slideAccelClamp) * f.dt;
  } else if (Math.hypot(s.sx, s.sy) < cfg.slideStop || !s.grounded) {
    s.sx = 0; s.sy = 0;
  } else {
    const k = 1 - cfg.slideDecay * f.dt;
    s.sx *= k; s.sy *= k;
  }
}

/** Slide entry on a landing (called from the landing stage). */
export function enterSlide(ctx: SimContext, s: PogoState, f: Frame, collider: number): void {
  const c = ctx.world.colliders[collider];
  if (c && isSlippery(c.surface)) {
    s.slideMode = true;
    s.sx = s.qvx;
    s.sy = 0;
    const k = ctx.cfg.qPerMetre;
    f.ev.push(makeEvent('slide', s.tick, s.qx / k, s.qy / k, 0.3, s.nx, s.ny, c.surface, c.material));
  } else s.slideMode = false;
}
