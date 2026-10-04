/**
 * MapCheckpoint — ordered checkpoints with split times, personal-best deltas and run statistics (SPEC §11.1, §12).
 */
import type { CheckpointDef, SplitsDef } from './schema';
import { regionContains } from './MapRegion';
import { TICK_RATE } from '../sim/math';

export interface CheckpointHit { def: CheckpointDef; tick: number }
export interface CheckpointSkip { def: CheckpointDef; tick: number; expected: string | null }

export class CheckpointTracker {
  readonly defs: CheckpointDef[];
  readonly reached = new Set<string>();
  /** Highest `order` reached among ordered (non-optional) checkpoints; -1 = none. */
  lastOrder = -1;
  private readonly inside = new Set<string>();
  private readonly skippedReported = new Set<string>();

  constructor(defs: CheckpointDef[]) { this.defs = [...defs].sort((a, b) => a.order - b.order || a.id.localeCompare(b.id)); }

  reset(): void { this.reached.clear(); this.lastOrder = -1; this.inside.clear(); this.skippedReported.clear(); }
  get count(): number { return this.reached.size; }

  /** The ordered (non-optional) checkpoint the player has to reach next. */
  expected(): CheckpointDef | null {
    for (const d of this.defs) if (!d.optional && !this.reached.has(d.id)) return d;
    return null;
  }

  private allowed(d: CheckpointDef): boolean {
    if (d.requires && !d.requires.every(r => this.reached.has(r))) return false;
    const mode = d.orderMode ?? 'monotonic';
    if (d.optional || mode === 'any') return true;
    if (mode === 'monotonic') return d.order > this.lastOrder;
    const exp = this.expected();
    return exp?.id === d.id;
  }

  /** Mark a checkpoint reached without a position test (effect `setCheckpoint`). Returns false if unknown or already reached. */
  force(id: string): CheckpointDef | null {
    const d = this.defs.find(x => x.id === id);
    if (!d || this.reached.has(id)) return null;
    this.reached.add(id);
    if (!d.optional) this.lastOrder = Math.max(this.lastOrder, d.order);
    return d;
  }

  /** Check the player position; returns checkpoints newly reached and (once each) skipped ones. */
  update(x: number, y: number, tick: number, hits: CheckpointHit[], skips: CheckpointSkip[]): void {
    for (const d of this.defs) {
      if (this.reached.has(d.id)) continue;
      const inside = regionContains(d.region, x, y);
      const entered = inside && !this.inside.has(d.id);
      if (inside) this.inside.add(d.id); else this.inside.delete(d.id);
      if (!inside) continue;
      if (this.allowed(d)) {
        this.reached.add(d.id);
        if (!d.optional) this.lastOrder = Math.max(this.lastOrder, d.order);
        hits.push({ def: d, tick });
      } else if (entered && !this.skippedReported.has(d.id)) {
        this.skippedReported.add(d.id);
        skips.push({ def: d, tick, expected: this.expected()?.id ?? null });
      }
    }
  }
}

// ── run statistics ──────────────────────────────────────────────────────────────────────────────────────────────
export type Medal = 'gold' | 'silver' | 'bronze' | 'none';

export interface SplitTime { id: string; name: string; tick: number; timeSec: number; segmentSec: number; deltaToPB: number | null; deltaToPar: number | null }
export interface RunSummary {
  mapId: string; version: string; completed: boolean; timeSec: number; jumps: number; deaths: number; progressMax: number;
  splits: SplitTime[]; medal: Medal;
}

export interface BestRecord { timeSec: number; splits: Record<string, number>; jumps: number; deaths: number }
export interface StoredRecords { version: 1; best?: BestRecord; bestSegments: Record<string, number>; runs: number; completions: number }

/** Persistence seam (the app stores JSON; tests use memory). Keys are `mapId@version`. */
export interface RunStore { get(key: string): StoredRecords | undefined; set(key: string, rec: StoredRecords): void }
export class MemoryRunStore implements RunStore {
  private readonly m = new Map<string, StoredRecords>();
  get(key: string): StoredRecords | undefined { return this.m.get(key); }
  set(key: string, rec: StoredRecords): void { this.m.set(key, JSON.parse(JSON.stringify(rec))); }
  serialize(): string { return JSON.stringify([...this.m.entries()]); }
  static deserialize(text: string): MemoryRunStore { const s = new MemoryRunStore(); for (const [k, v] of JSON.parse(text) as [string, StoredRecords][]) s.set(k, v); return s; }
}

export function medalFor(timeSec: number, t: SplitsDef['targets']): Medal {
  if (!(timeSec > 0)) return 'none';
  if (t.gold > 0 && timeSec <= t.gold) return 'gold';
  if (t.silver > 0 && timeSec <= t.silver) return 'silver';
  if (t.bronze > 0 && timeSec <= t.bronze) return 'bronze';
  return 'none';
}

export class RunRecorder {
  readonly splits: SplitTime[] = [];
  private lastSplitSec = 0;
  readonly key: string;

  constructor(readonly mapId: string, readonly version: string, private readonly cfg: SplitsDef, private readonly store?: RunStore) { this.key = `${mapId}@${version}`; }

  reset(): void { this.splits.length = 0; this.lastSplitSec = 0; }
  private rec(): StoredRecords | undefined { return this.store?.get(this.key); }

  /** Record the split of checkpoint `checkpointId` reached at `tick` (run start = `startedTick`). Returns the split or null if it is not a split. */
  onCheckpoint(checkpointId: string, tick: number, startedTick: number): SplitTime | null {
    const def = this.cfg.splits.find(s => s.checkpoint === checkpointId);
    if (!def || startedTick < 0) return null;
    if (this.splits.some(s => s.id === def.id)) return null;
    const timeSec = (tick - startedTick) / TICK_RATE;
    const pb = this.rec()?.best?.splits[def.id];
    const sp: SplitTime = {
      id: def.id, name: def.name, tick, timeSec, segmentSec: timeSec - this.lastSplitSec,
      deltaToPB: pb === undefined ? null : timeSec - pb, deltaToPar: def.parSec === undefined ? null : timeSec - def.parSec,
    };
    this.lastSplitSec = timeSec;
    this.splits.push(sp);
    return sp;
  }

  /** Finish (or abandon) a run and update personal bests. */
  summarize(p: { startedTick: number; finishedTick: number; jumps: number; deaths: number; progressMax: number }): RunSummary {
    const completed = p.finishedTick >= 0 && p.startedTick >= 0;
    const timeSec = completed ? (p.finishedTick - p.startedTick) / TICK_RATE : 0;
    const summary: RunSummary = {
      mapId: this.mapId, version: this.version, completed, timeSec, jumps: p.jumps, deaths: p.deaths, progressMax: p.progressMax,
      splits: [...this.splits], medal: completed ? medalFor(timeSec, this.cfg.targets) : 'none',
    };
    if (this.store) {
      const rec: StoredRecords = this.rec() ?? { version: 1, bestSegments: {}, runs: 0, completions: 0 };
      rec.runs++;
      for (const s of this.splits) if (rec.bestSegments[s.id] === undefined || s.segmentSec < rec.bestSegments[s.id]) rec.bestSegments[s.id] = s.segmentSec;
      if (completed) {
        rec.completions++;
        if (!rec.best || timeSec < rec.best.timeSec) rec.best = { timeSec, splits: Object.fromEntries(this.splits.map(s => [s.id, s.timeSec])), jumps: p.jumps, deaths: p.deaths };
      }
      this.store.set(this.key, rec);
    }
    return summary;
  }
}
