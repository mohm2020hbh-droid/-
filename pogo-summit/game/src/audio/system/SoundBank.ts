import { AUDIO_CONFIG } from './audioConfig';
import { mulberry32, seedFor } from './rng';
import { RECIPES, WARM_ORDER, renderRateOf, type Recipe } from './synth/recipes';
import type { AudioHost } from './types';

/** Base seed of every recipe stream (`tools/audio-render.ts` uses the same, so what is measured is what plays). */
export const DEFAULT_BANK_SEED = 0x5eed;

export interface BankStats {
  hits: number; misses: number; evictions: number;
  /** bytes of PCM currently cached (float32) */
  bytes: number;
  /** total ms spent rendering recipes */
  renderMs: number;
  rendered: number;
  /** variants that could not be produced (no recipe, failed sample) — counted once each */
  missing: number;
  samplesLoaded: number;
}

/** Optional licensed-file substitution (`?audioPack=<url>`): variant id → file. Off unless a manifest is supplied. */
export interface SampleManifest { variants: Record<string, { url: string; gain?: number }> }

/** Injected so the bank is testable and never touches `fetch` itself. */
export interface SampleLoader { fetchBytes(url: string): Promise<ArrayBuffer> }

interface Entry { buffer: unknown; bytes: number; lastUsed: number; pinned: boolean; fromSample: boolean }

/**
 * SoundBank — the buffer cache.
 *
 *  • `get(id)` returns the cached buffer; on a miss it renders the recipe synchronously (lazy) and caches it. AudioBuffers are
 *    immutable, so every voice shares one.
 *  • `warm(ids)` renders ahead (called in idle slices after the first user gesture).
 *  • An optional manifest maps variant ids to licensed files: the recipe plays *immediately* while the file loads, then the file.
 *    A failed load marks the variant missing once and the recipe keeps playing.
 *  • A memory budget with LRU eviction (pinned entries — the ones warm-up rendered — go last).
 *  • Missing audio never throws: `get` returns null and `stats.missing` counts it once per id (the original silently skips a
 *    source that is not loaded: analysis AU-05).
 */
export class SoundBank {
  private readonly cache = new Map<string, Entry>();
  private readonly missingIds = new Set<string>();
  private readonly loading = new Set<string>();
  private clock = 0;
  readonly stats: BankStats = { hits: 0, misses: 0, evictions: 0, bytes: 0, renderMs: 0, rendered: 0, missing: 0, samplesLoaded: 0 };
  manifest: SampleManifest | null = null;
  loader: SampleLoader | null = null;
  /** Called once per variant that could not be produced. */
  onMissing: (id: string, reason: string) => void = () => {};
  /** Clock for render timing (injectable). */
  nowMs: () => number = () => (typeof performance !== 'undefined' ? performance.now() : Date.now());

  constructor(private readonly host: AudioHost, private readonly recipes: Readonly<Record<string, Recipe>> = RECIPES, private readonly seed = DEFAULT_BANK_SEED, private readonly maxBytes = AUDIO_CONFIG.bank.maxBytes) {}

  has(id: string): boolean { return this.cache.has(id); }
  isMissing(id: string): boolean { return this.missingIds.has(id); }
  get size(): number { return this.cache.size; }

  /** The buffer for a variant (recipe id or variant id), or null when none can be produced. */
  get(id: string, variantId = id): unknown | null {
    const hit = this.cache.get(variantId);
    if (hit) { hit.lastUsed = ++this.clock; this.stats.hits++; this.maybeLoadSample(variantId); return hit.buffer; }
    this.stats.misses++;
    const b = this.render(id, variantId, false);
    this.maybeLoadSample(variantId);
    return b;
  }

  /** Render a recipe into the cache (warm-up). Returns false when it does not exist. */
  warm(id: string): boolean {
    if (this.cache.has(id)) return true;
    return this.render(id, id, true) !== null;
  }

  /** Warm the recipes in `WARM_ORDER` that are not cached yet; stops after `budgetMs`. Returns the number still to do. */
  warmSome(budgetMs: number, order: readonly string[] = WARM_ORDER): number {
    const t0 = this.nowMs();
    let left = 0;
    for (const id of order) {
      if (this.cache.has(id) || this.missingIds.has(id)) continue;
      if (this.nowMs() - t0 >= budgetMs) { left++; continue; }
      this.warm(id);
    }
    return left;
  }

  private render(recipeId: string, key: string, pin: boolean): unknown | null {
    const r = this.recipes[recipeId];
    if (!r) { this.markMissing(key, 'no recipe'); return null; }
    try {
      const rate = renderRateOf(r, this.host.sampleRate);
      const t0 = this.nowMs();
      const pcm = r.render(rate, mulberry32(seedFor(this.seed, r.id)));
      this.stats.renderMs += this.nowMs() - t0;
      this.stats.rendered++;
      const buffer = this.host.makeBuffer(pcm, rate);
      this.put(key, { buffer, bytes: pcm.length * 4, lastUsed: ++this.clock, pinned: pin, fromSample: false });
      return buffer;
    } catch (e) {
      this.markMissing(key, `render failed: ${(e as Error).message}`);
      return null;
    }
  }

  private put(key: string, e: Entry): void {
    const old = this.cache.get(key);
    if (old) this.stats.bytes -= old.bytes;
    this.cache.set(key, e);
    this.stats.bytes += e.bytes;
    this.evict();
  }

  /** LRU eviction beyond the budget; the entry just inserted is never evicted, pinned entries only when nothing else is left. */
  private evict(): void {
    while (this.stats.bytes > this.maxBytes && this.cache.size > 1) {
      let victim: [string, Entry] | null = null;
      for (const kv of this.cache) {
        if (kv[1].lastUsed === this.clock) continue;
        if (!victim || (kv[1].pinned ? 1 : 0) < (victim[1].pinned ? 1 : 0) || ((kv[1].pinned ? 1 : 0) === (victim[1].pinned ? 1 : 0) && kv[1].lastUsed < victim[1].lastUsed)) victim = kv;
      }
      if (!victim) return;
      this.cache.delete(victim[0]);
      this.stats.bytes -= victim[1].bytes;
      this.stats.evictions++;
    }
  }

  private markMissing(id: string, reason: string): void {
    if (this.missingIds.has(id)) return;
    this.missingIds.add(id);
    this.stats.missing++;
    this.onMissing(id, reason);
  }

  /** Start loading the licensed file of a variant (once); the recipe keeps playing until it is ready. */
  private maybeLoadSample(variantId: string): void {
    const m = this.manifest?.variants[variantId];
    if (!m || !this.loader || !this.host.decode || this.loading.has(variantId)) return;
    const cached = this.cache.get(variantId);
    if (cached?.fromSample) return;
    this.loading.add(variantId);
    this.loader.fetchBytes(m.url)
      .then(bytes => this.host.decode!(bytes))
      .then(buffer => {
        const bytes = Math.round(this.host.durationOf(buffer) * this.host.sampleRate * 4);
        this.put(variantId, { buffer, bytes, lastUsed: ++this.clock, pinned: true, fromSample: true });
        this.stats.samplesLoaded++;
      })
      .catch((e: unknown) => { this.markMissing(`sample:${variantId}`, `sample failed: ${(e as Error)?.message ?? e}`); })
      .finally(() => { this.loading.delete(variantId); });
  }

  /** Per-variant gain from the manifest (1 when none). */
  sampleGain(variantId: string): number { return this.cache.get(variantId)?.fromSample ? this.manifest?.variants[variantId]?.gain ?? 1 : 1; }

  clear(): void { this.cache.clear(); this.stats.bytes = 0; }
}
