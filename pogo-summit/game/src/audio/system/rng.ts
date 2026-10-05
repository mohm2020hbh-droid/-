import type { Rng } from './types';

/** mulberry32 — a tiny seeded PRNG: every random choice of the audio system is reproducible in tests and in recipe rendering. */
export function mulberry32(seed: number): Rng {
  let a = seed >>> 0;
  return {
    next(): number {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    },
  };
}

/** Default runtime source (presentation code only — `sim/` never uses randomness). */
export const mathRng: Rng = { next: () => Math.random() };

export const rangeOf = (r: Rng, lo: number, hi: number): number => lo + (hi - lo) * r.next();
export const intOf = (r: Rng, n: number): number => Math.min(n - 1, Math.floor(r.next() * n));

/** Seed of one recipe's noise stream: the base seed mixed with a hash of the recipe id (so every recipe is deterministic and distinct). */
export function seedFor(base: number, id: string): number {
  let h = 2166136261;
  for (let i = 0; i < id.length; i++) { h ^= id.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (base ^ (h >>> 0)) >>> 0;
}
