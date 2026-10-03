import type { PhysicsConfig } from './PhysicsConfig';
import type { PogoState } from './PogoState';
import type { SimEvent } from './events';
import { makeEvent } from './events';
import { DEG, DT, sign } from './math';

/**
 * BoostSystem — rotation detection → arm → release.
 *
 * Source: the original has "Double jump" and "720° boost" modes (XLSX F-016, grade A *existence only*) and boost
 * items (TYPE_BOOSTJUICE, A). The mechanism is unknown (XLSX U-25, grade C-after-measurement). This implementation is
 * therefore OUR design (DECISIONS DEC-014), all constants TUNE_ME:
 *
 *   Rotation Detection : in the air, angular speed |ω| ≥ boostThreshold counts as "spinning"
 *   Rotation Amount    : spinning accumulates signed rotation; reversing direction restarts the run
 *   Boost Threshold    : boostRotation (deg) accumulated ⇒ boost becomes READY
 *   Boost Power        : boostPower (m/s) added along the stick axis
 *   Jump Hold          : if pressed while grounded the boost is QUEUED and joins the next launch
 *   Boost Release      : consumed at launch (grounded) or immediately (airborne)
 */
export function updateSpin(s: PogoState, cfg: PhysicsConfig, ev: SimEvent[]): void {
  const thr = cfg.boostThreshold * DEG;
  if (Math.abs(s.omega) >= thr) {
    if (sign(s.omega) !== sign(s.spin) && s.spin !== 0) s.spin = 0; // direction reversed ⇒ new run
    s.spin += s.omega * DT;
    s.spinIdle = 0;
    if (!s.boostReady && Math.abs(s.spin) >= cfg.boostRotation * DEG) {
      s.boostReady = true;
      ev.push(makeEvent('boost_armed', s.tick, s.x, s.y, 1, 0, 1));
    }
  } else if (++s.spinIdle > 30) {
    s.spin = 0;
  }
}

export function resetSpin(s: PogoState): void {
  s.spin = 0;
  s.spinIdle = 0;
}

/** Called when the Boost control is pressed. Returns true if it did something. */
export function pressBoost(s: PogoState, cfg: PhysicsConfig, ev: SimEvent[]): boolean {
  if (!s.boostReady) return false;
  if (s.mode === 'AIR' || s.mode === 'SLIDING') {
    const dx = Math.sin(s.angle), dy = Math.cos(s.angle);
    s.vx += dx * cfg.boostPower;
    s.vy += dy * cfg.boostPower;
    s.boostReady = false;
    s.boostQueued = false;
    resetSpin(s); // a consumed boost must not re-arm from the same spin run
    s.boosts++;
    ev.push(makeEvent('boost', s.tick, s.x, s.y, 1, dx, dy));
    return true;
  }
  if (s.mode === 'GROUNDED' || s.mode === 'CHARGING') {
    s.boostQueued = true;
    return true;
  }
  return false;
}
