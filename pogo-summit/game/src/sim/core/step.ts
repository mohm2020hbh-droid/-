import { type PogoInput, type PogoState, plantAt, syncPresentation } from '../PogoState';
import { type SimEvent, makeEvent } from '../events';
import { capSpeed, airStep } from './air';
import { boostCheck } from './boost';
import { overlapsTrigger } from './collision';
import { type Frame, type SimContext, newFrame } from './frame';
import { groundContact, onLanding } from './groundContact';
import { groundStep } from './groundPressure';
import { readInput } from './input';
import { integratePosition, platformCarry, platformRelease, updateHull } from './integration';
import { launch } from './launch';
import { applyRotation } from './rotation';
import { updateSlide } from './slide';
import { wallBounce } from './wallBounce';
import { chargeStep } from './charge';
import { dtTicks } from '../units';
import { tipCenterQ } from '../PogoState';

const FRAME: Frame = newFrame();
const TIP = { x: 0, y: 0 };

/**
 * stepPogo — one deterministic physics update (LOCKED_SPEC §3). The stages follow the spec's update order exactly:
 *
 *   1  timers (E12)                          8  landing: slide entry + spring window (E15, E7)
 *   2  slide (E15)                           9  grounded ∧ touched ∧ N = 0 → charge or launch (E8–E11)
 *   3  platform carry                           else touched ∧ N = 0 → wall bounce (E14)
 *   4  turn (E4)                            10  platform release (E17) + speed cap (E2)
 *   5  grounded → tip pivot + pressure (E5)  11  boost (E13)
 *      airborne → gravity + drag (E1)       12  spring extension → hull (E18), pivot memory
 *   6  speed cap (E2)                       13  triggers, respawn, stats
 *   7  position integration (E3) with collision, then ground probe (E6, E16)
 *
 * Module map (implementation order requested for this task): input → charge → launch → air → groundContact →
 * groundPressure → rotation → wallBounce → slide → collision → boost → integration.
 */
export function stepPogo(ctx: SimContext, s: PogoState, inp: PogoInput, ev: SimEvent[]): void {
  const { cfg } = ctx;
  if (s.mode === 'FINISHED') { s.tick++; return; }
  s.tick++;
  const f = FRAME;
  f.dt = dtTicks(cfg);
  f.ev = ev;
  f.touched = false; f.landed = false; f.touchCollider = -1; f.dispX = 0; f.dispY = 0;
  f.probe.hit = false;

  readInput(inp, s, f);

  // 1 — timers (E12)
  if (s.noGround > 0) s.grounded = false;
  s.noGround = Math.max(s.noGround - f.dt, 0);
  if (s.grounded) s.jumpTimer = 0;
  else { s.jumpTimer = Math.max(s.jumpTimer - f.dt, 0); s.slideMode = false; }

  // 2 — slide (E15), 3 — platform carry
  updateSlide(ctx, s, f);
  platformCarry(ctx, s, f);

  // 4 — turn (E4)
  applyRotation(ctx, s, f);

  // 5 — grounded: pivot + pressure (E5); airborne: gravity + drag (E1)
  if (s.grounded) groundStep(ctx, s, f); else airStep(ctx, s, f);

  // 6 — speed cap (E2)
  capSpeed(ctx, s);

  // 7 — position integration (E3) with collision, ground probe (E6, E16)
  f.gBefore = s.grounded;
  integratePosition(ctx, s, f);
  groundContact(ctx, s, f);

  // 8 — landing
  if (f.landed) onLanding(ctx, s, f);

  // 9 — charge / launch, or wall bounce
  if (f.touched && s.noGround <= 0) {
    if (s.grounded) {
      if (chargeStep(ctx, s, f) === 'launch') launch(ctx, s, f);
    } else wallBounce(ctx, s, f);
  }

  // 10 — platform release (E17) + cap (E2)
  platformRelease(ctx, s);

  // 11 — boost (E13), 12 — hull (E18) and pivot memory
  boostCheck(ctx, s, f);
  updateHull(ctx, s, f);
  if (s.grounded) { tipCenterQ(cfg, s, TIP); s.tipPrevX = TIP.x; s.tipPrevY = TIP.y; }

  // 13 — triggers, respawn
  let finished = false;
  for (const c of ctx.world.triggers) {
    if (!overlapsTrigger(ctx, s, c.index)) continue;
    const k = cfg.qPerMetre;
    if (c.kind === 'hazard') {
      s.hazards++;
      ev.push(makeEvent('hazard', s.tick, s.qx / k, s.qy / k, 1, 0, 1, c.surface, c.material));
      respawn(ctx, s, ev);
      break;
    }
    if (c.kind === 'goal') {
      finished = true;
      s.finishedTick = s.tick;
      s.qvx = 0; s.qvy = 0; s.omega = 0;
      ev.push(makeEvent('goal', s.tick, s.qx / k, s.qy / k, 1));
      break;
    }
  }
  if (!finished && s.qy / cfg.qPerMetre < ctx.world.level.killY) {
    s.falls++;
    ev.push(makeEvent('fall', s.tick, s.qx / cfg.qPerMetre, s.qy / cfg.qPerMetre, 1));
    respawn(ctx, s, ev);
  }
  syncPresentation(s, cfg, finished);
}

/** Put the player back on the last safe spot (plain reset of dynamic state, stats preserved). */
export function respawn(ctx: SimContext, s: PogoState, ev: SimEvent[]): void {
  const { cfg, world } = ctx;
  const g = world.colliders[s.safeGround];
  plantAt(s, world, cfg, g, s.safeLx, s.safeLy, s.safeNx, s.safeNy, s.tick);
  s.teleportTick = s.tick;
  const k = cfg.qPerMetre;
  ev.push(makeEvent('respawn', s.tick, s.qx / k, s.qy / k, 0));
}
