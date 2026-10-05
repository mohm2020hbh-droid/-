import type { SfxId } from '../AudioEvents';
import type { AudioEventContext, Rng } from './types';

/** Where a variant's sound comes from. */
export type VariantSource =
  /** a procedural recipe rendered once to a cached buffer */
  | { kind: 'synth'; recipe: string }
  /** an optional licensed file (sample-bank manifest); the recipe plays until it has loaded, and if it never does */
  | { kind: 'sample'; url: string; recipe?: string }
  /** adapter to the existing per-call procedural player (`SFXManager`), so old sounds go through the same events */
  | { kind: 'legacy'; sfx: SfxId | ((ctx: AudioEventContext) => SfxId); intensity?: (ctx: AudioEventContext) => number };

export interface AudioVariantDef {
  /** bank key, unique across the catalogue ('collision_3') */
  id: string;
  source: VariantSource;
  /** relative draw weight in `random` selection (default 1) */
  weight?: number;
  /** level trim of this variant, dB */
  trimDb?: number;
  /** eligibility: layers play every eligible variant, `context` the first eligible, `random` draws among the eligible */
  when?: (ctx: AudioEventContext) => boolean;
  /** per-variant linear gain / pitch (override the event's defaults — a layered launch has a quiet pop and a loud boom) */
  gain?: (ctx: AudioEventContext, rng: Rng) => number;
  pitch?: (ctx: AudioEventContext, rng: Rng) => number;
  /** seconds: legacy voices have no end callback, so the pool frees them after this */
  durationHint?: number;
  /** provenance note only (which original file this variant is the analogue of) */
  analog?: string;
}

export type SelectMode = 'random' | 'layers' | 'context';

export interface SelectorPolicy {
  mode: SelectMode;
  /** a variant is not eligible again before this many ms (ignored when nothing else is eligible) */
  variantCooldownMs?: number;
  /** key under which "the previous pick" is remembered (the event id) */
  key: string;
}

/**
 * VariantSelector — which variant(s) play.
 *
 * `random` implements the anti-repeat rule of the original (found for the slime-slip and thorn-horror sounds, analysis P1):
 *   i = int(random(N));  if (i == last) i = (i + 1) % N;  last = i
 * plus an optional per-variant cooldown so a long variant is not drawn again while its tail is still ringing.
 */
export class VariantSelector {
  private readonly last = new Map<string, string>();
  private readonly playedAt = new Map<string, number>();
  constructor(private readonly rng: Rng) {}

  pick(variants: readonly AudioVariantDef[], policy: SelectorPolicy, ctx: AudioEventContext, nowSec: number): AudioVariantDef[] {
    const eligible = variants.filter(v => !v.when || v.when(ctx));
    if (eligible.length === 0) return [];
    if (policy.mode === 'layers') { for (const v of eligible) this.playedAt.set(v.id, nowSec); return eligible; }
    if (ctx.variant) {
      const forced = variants.find(v => v.id === ctx.variant);
      if (forced) { this.playedAt.set(forced.id, nowSec); return [forced]; }
    }
    if (policy.mode === 'context') { const v = eligible[0]; this.playedAt.set(v.id, nowSec); return [v]; }

    // random
    let pool = eligible;
    const cd = (policy.variantCooldownMs ?? 0) / 1000;
    if (cd > 0) {
      const fresh = eligible.filter(v => nowSec - (this.playedAt.get(v.id) ?? -1e9) >= cd);
      if (fresh.length > 0) pool = fresh;                         // never silence by cooldown alone
    }
    const n = pool.length;
    let i: number;
    const totalW = pool.reduce((s, v) => s + (v.weight ?? 1), 0);
    if (pool.every(v => (v.weight ?? 1) === (pool[0].weight ?? 1))) i = Math.min(n - 1, Math.floor(this.rng.next() * n));
    else { let r = this.rng.next() * totalW; i = 0; for (; i < n - 1; i++) { r -= pool[i].weight ?? 1; if (r < 0) break; } }
    const lastId = this.last.get(policy.key);
    if (n > 1 && lastId !== undefined && pool[i].id === lastId) i = (i + 1) % n;     // P1: reroll to the next index
    const v = pool[i];
    this.last.set(policy.key, v.id);
    this.playedAt.set(v.id, nowSec);
    return [v];
  }

  /** Forget history (level change). */
  reset(): void { this.last.clear(); this.playedAt.clear(); }
}
