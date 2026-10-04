import type { PogoInput, PogoState } from '../PogoState';
import { clamp } from '../math';
import type { Frame } from './frame';

/**
 * Input stage. The locked physics reads exactly two things (LOCKED_SPEC §6):
 *   turn   u_left, u_right ∈ [0,1]   → E4  ω_t = 32·(u_left − u_right)
 *   hold   charge control            → E8  (L < L_min ∨ hold) ∧ L < L_max
 * Tilt convention: −1 = lean left (u_left = 1), +1 = lean right. A cancelled touch counts as releasing the hold.
 */
export function readInput(inp: PogoInput, s: PogoState, f: Frame): void {
  const tilt = clamp(inp.tilt, -1, 1);
  f.uLeft = tilt < 0 ? -tilt : 0;
  f.uRight = tilt > 0 ? tilt : 0;
  f.hold = inp.jumpHeld && !inp.cancel;
  s.held = f.hold;
}
