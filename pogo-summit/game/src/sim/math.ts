/** Shared numeric helpers for the deterministic simulation. No DOM / Three.js here. */

/** Simulation rate. SOURCE: XLSX V-001/V-002/V-003 (grade A, read from the replay debug panel). */
export const TICK_RATE = 120;
export const DT = 1 / TICK_RATE;
export const DEG = Math.PI / 180;
export const TAU = Math.PI * 2;
/** 1.0 == 1024 internal units in the original engine (XLSX F-003/F-004, grade A). */
export const FIXED_STEP = 1 / 1024;

export interface Vec2 { x: number; y: number }

export const clamp = (v: number, a: number, b: number): number => (v < a ? a : v > b ? b : v);
export const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;
/** Quantise to the 22.10 fixed-point grid (exactly representable in binary floating point). */
export const q = (v: number): number => Math.round(v * 1024) / 1024;
export const approach = (v: number, target: number, maxDelta: number): number =>
  v < target ? Math.min(v + maxDelta, target) : Math.max(v - maxDelta, target);
export const wrapPi = (a: number): number => {
  let r = (a + Math.PI) % TAU;
  if (r < 0) r += TAU;
  return r - Math.PI;
};
export const sign = (v: number): number => (v > 0 ? 1 : v < 0 ? -1 : 0);
export const ticksToSeconds = (t: number): number => t / TICK_RATE;
