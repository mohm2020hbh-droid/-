import { DEFAULT_SETTINGS, type Settings, mergeSettings } from './settings';
import { DEFAULT_EQUIPPED, ITEMS, type Category } from '../data/items';
import { Native } from '../platform/Native';

/** Phase 14/16: everything persisted — settings, progress, best times, unlocks, customisation, control + audio settings. */
export interface LevelRecord {
  completed: boolean;
  bestTimeSec: number | null;
  bestJumps: number | null;
  bestBoosts: number | null;
  stars: number;
  attempts: number;
  completions: number;
  bestProgress: number;
}
export interface RunRecord { timeSec: number; jumps: number; boosts: number; falls: number; date: number }
export interface Stats { totalJumps: number; totalBoosts: number; totalFalls: number; totalPlaySec: number; runs: number }

export interface SaveData {
  schemaVersion: 1;
  settings: Settings;
  levels: Record<string, LevelRecord>;
  worldsUnlocked: string[];
  unlocked: string[];
  equipped: Record<Category, string>;
  stats: Stats;
  leaderboard: Record<string, RunRecord[]>;
  tutorialSeen: boolean;
}

const KEY = 'pogo.save.v1';

export const newLevelRecord = (): LevelRecord => ({ completed: false, bestTimeSec: null, bestJumps: null, bestBoosts: null, stars: 0, attempts: 0, completions: 0, bestProgress: 0 });

export function defaultSave(): SaveData {
  return {
    schemaVersion: 1,
    settings: JSON.parse(JSON.stringify(DEFAULT_SETTINGS)),
    levels: {}, worldsUnlocked: ['world_1'],
    unlocked: ITEMS.filter(i => i.unlockCondition.type === 'default').map(i => i.itemId),
    equipped: { ...DEFAULT_EQUIPPED },
    stats: { totalJumps: 0, totalBoosts: 0, totalFalls: 0, totalPlaySec: 0, runs: 0 },
    leaderboard: {}, tutorialSeen: false,
  };
}

/** Defensive parse: unknown/corrupt data never crashes the game; missing fields get defaults. */
export function parseSave(raw: string | null): SaveData {
  const base = defaultSave();
  if (!raw) return base;
  try {
    const o = JSON.parse(raw) as Partial<SaveData>;
    if (!o || typeof o !== 'object') return base;
    base.settings = mergeSettings(base.settings, o.settings);
    if (o.levels && typeof o.levels === 'object') {
      for (const [id, r] of Object.entries(o.levels)) {
        const l = newLevelRecord();
        const rr = r as Partial<LevelRecord>;
        l.completed = !!rr.completed;
        l.bestTimeSec = typeof rr.bestTimeSec === 'number' ? rr.bestTimeSec : null;
        l.bestJumps = typeof rr.bestJumps === 'number' ? rr.bestJumps : null;
        l.bestBoosts = typeof rr.bestBoosts === 'number' ? rr.bestBoosts : null;
        l.stars = Math.max(0, Math.min(3, Math.floor(+(rr.stars ?? 0)) || 0));
        l.attempts = Math.max(0, Math.floor(+(rr.attempts ?? 0)) || 0);
        l.completions = Math.max(0, Math.floor(+(rr.completions ?? 0)) || 0);
        l.bestProgress = Math.max(0, Math.min(1, +(rr.bestProgress ?? 0) || 0));
        base.levels[id] = l;
      }
    }
    if (Array.isArray(o.worldsUnlocked)) base.worldsUnlocked = [...new Set(['world_1', ...o.worldsUnlocked.filter(x => typeof x === 'string')])];
    if (Array.isArray(o.unlocked)) base.unlocked = [...new Set([...base.unlocked, ...o.unlocked.filter(x => typeof x === 'string')])];
    if (o.equipped) for (const k of Object.keys(base.equipped) as Category[]) { const v = (o.equipped as Record<string, string>)[k]; if (typeof v === 'string' && base.unlocked.includes(v)) base.equipped[k] = v; }
    if (o.stats) for (const k of Object.keys(base.stats) as (keyof Stats)[]) base.stats[k] = Math.max(0, +(o.stats[k] ?? 0) || 0);
    if (o.leaderboard && typeof o.leaderboard === 'object') {
      for (const [id, runs] of Object.entries(o.leaderboard)) if (Array.isArray(runs)) base.leaderboard[id] = runs.filter(r => r && typeof r.timeSec === 'number').slice(0, 20).map(r => ({ timeSec: r.timeSec, jumps: r.jumps | 0, boosts: r.boosts | 0, falls: r.falls | 0, date: +r.date || 0 }));
    }
    base.tutorialSeen = !!o.tutorialSeen;
  } catch { /* corrupt save ⇒ defaults */ }
  return base;
}

export class SaveSystem {
  data: SaveData;
  private timer = 0;
  private dirty = false;

  constructor() {
    this.data = parseSave(Native.load(KEY));
    window.addEventListener('pagehide', () => this.flush());
    document.addEventListener('visibilitychange', () => { if (document.hidden) this.flush(); });
  }

  /** Debounced write (never on a per-frame path). */
  save(): void {
    this.dirty = true;
    if (this.timer) return;
    this.timer = window.setTimeout(() => { this.timer = 0; this.flush(); }, 600);
  }

  flush(): void {
    if (!this.dirty) return;
    this.dirty = false;
    try { Native.save(KEY, JSON.stringify(this.data)); } catch { /* storage full / blocked */ }
  }

  reset(): void { this.data = defaultSave(); this.dirty = true; this.flush(); }
}
