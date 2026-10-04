import type { PhysicsConfig } from './PhysicsConfig';
import type { PhysicsWorld } from './PhysicsWorld';
import { type PogoInput, type PogoState, type Pose, NEUTRAL_INPUT, computePose, createPogoState, newPose } from './PogoState';
import { type SimContext } from './core/frame';
import { respawn, stepPogo } from './core/step';
import type { SimEvent } from './events';
import { clamp } from './math';

/**
 * PogoPhysicsController — owns one PogoState and steps the locked-spec physics at a fixed 120 Hz.
 * The physics itself lives in `core/` (see `core/step.ts` for the update order). This class adds only what presentation
 * needs: the previous pose for render interpolation.
 */
export type { SimContext };
export { stepPogo, respawn };

export class PogoPhysicsController {
  readonly ctx: SimContext;
  state: PogoState;
  readonly events: SimEvent[] = [];
  /** Pose before the last step (for interpolation): metres / render radians. */
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
