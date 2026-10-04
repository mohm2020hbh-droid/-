import type { PhysicsConfig } from '../PhysicsConfig';
import type { PhysicsWorld } from '../PhysicsWorld';
import type { SimEvent } from '../events';

/** Everything the physics core needs besides the state. Pure data: no DOM, no clocks. */
export interface SimContext { world: PhysicsWorld; cfg: PhysicsConfig }

/** Result of the ground probe (E6). */
export interface ProbeResult {
  hit: boolean;
  /** Unit normal of the surface at the hit (pointing toward the body). */
  nx: number; ny: number;
  /** Collider index. */
  collider: number;
}

/** Per-tick temporaries shared by the core stages. Reused between ticks (no allocation in the hot path). */
export interface Frame {
  /** Δt in ticks (T). */
  dt: number;
  ev: SimEvent[];
  /** Turn input: u_left, u_right ∈ [0,1]; hold. */
  uLeft: number; uRight: number; hold: boolean;
  /** g at the start of the ground-contact stage (L2b4 in the original). */
  gBefore: boolean;
  /** The collision move touched something this tick (L94) and the last contact. */
  touched: boolean;
  touchNx: number; touchNy: number; touchX: number; touchY: number; touchCollider: number;
  /** Displacement (Q) applied by the integration step — the movement vector the engine would reflect. */
  dispX: number; dispY: number;
  probe: ProbeResult;
  /** The state was landed on / launched this tick (for events and stats). */
  landed: boolean;
}

export function newFrame(): Frame {
  return {
    dt: 0, ev: [], uLeft: 0, uRight: 0, hold: false, gBefore: false,
    touched: false, touchNx: 0, touchNy: 1, touchX: 0, touchY: 0, touchCollider: -1, dispX: 0, dispY: 0,
    probe: { hit: false, nx: 0, ny: 1, collider: -1 }, landed: false,
  };
}
