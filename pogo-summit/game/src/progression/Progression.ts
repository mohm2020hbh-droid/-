import type { SaveSystem, LevelRecord, RunRecord } from './SaveSystem';
import { newLevelRecord } from './SaveSystem';
import { ITEMS, type ItemDef, type Unlock } from '../data/items';
import type { LevelData } from '../data/LevelData';
import { LEVEL_01 } from '../data/levels/level01';

/** World/Level registry + completion, stars, unlocks and the local leaderboard (Phase 14). */
export interface LevelEntry { id: string; worldId: string; data: LevelData | null; name: string }

/** Only LEVEL_01 is authored in this release; later levels are declared (so the UI/progression are real) but locked "coming soon". */
export const LEVELS: LevelEntry[] = [
  { id: 'level_01', worldId: 'world_1', data: LEVEL_01, name: 'First Steps' },
  { id: 'level_02', worldId: 'world_1', data: null, name: 'Mossy Ledges' },
  { id: 'level_03', worldId: 'world_2', data: null, name: 'Frozen Pines' },
  { id: 'level_04', worldId: 'world_3', data: null, name: 'Sunset Arches' },
  { id: 'level_05', worldId: 'world_4', data: null, name: 'Ember Stairs' },
];

export interface RunResult { levelId: string; timeSec: number; jumps: number; boosts: number; falls: number; completed: boolean; progress: number }
export interface RunOutcome { newBest: boolean; stars: number; newItems: ItemDef[]; rank: number }

export class Progression {
  constructor(private readonly save: SaveSystem) {}

  record(levelId: string): LevelRecord { return this.save.data.levels[levelId] ?? newLevelRecord(); }

  stars(level: LevelData, timeSec: number): number {
    if (timeSec <= level.parTimeSec) return 3;
    if (timeSec <= level.parTimeSec * 1.7) return 2;
    return 1;
  }

  isLevelPlayable(id: string): boolean {
    const e = LEVELS.find(l => l.id === id);
    if (!e || !e.data) return false;
    const idx = LEVELS.indexOf(e);
    return idx === 0 || this.record(LEVELS[idx - 1].id).completed || !LEVELS[idx - 1].data;
  }

  isWorldUnlocked(worldId: string): boolean { return this.save.data.worldsUnlocked.includes(worldId); }

  attempt(levelId: string): void {
    const d = this.save.data;
    const r = (d.levels[levelId] ??= newLevelRecord());
    r.attempts++;
    this.save.save();
  }

  /** Apply a finished (or abandoned) run to the save, return what changed. */
  finish(res: RunResult, level: LevelData): RunOutcome {
    const d = this.save.data;
    const r = (d.levels[res.levelId] ??= newLevelRecord());
    d.stats.totalJumps += res.jumps; d.stats.totalBoosts += res.boosts; d.stats.totalFalls += res.falls; d.stats.runs++;
    r.bestProgress = Math.max(r.bestProgress, res.progress);
    let newBest = false, stars = 0, rank = 0;
    if (res.completed) {
      r.completed = true; r.completions++;
      stars = this.stars(level, res.timeSec);
      if (r.bestTimeSec === null || res.timeSec < r.bestTimeSec) { r.bestTimeSec = res.timeSec; newBest = true; }
      r.bestJumps = r.bestJumps === null ? res.jumps : Math.min(r.bestJumps, res.jumps);
      r.bestBoosts = Math.max(r.bestBoosts ?? 0, res.boosts);
      r.stars = Math.max(r.stars, stars);
      const lb = (d.leaderboard[res.levelId] ??= []);
      const run: RunRecord = { timeSec: res.timeSec, jumps: res.jumps, boosts: res.boosts, falls: res.falls, date: Date.now() };
      lb.push(run); lb.sort((a, b) => a.timeSec - b.timeSec); if (lb.length > 10) lb.length = 10;
      rank = lb.indexOf(run) + 1;
      // unlock the next world once every level of the current one is done
      const idx = LEVELS.findIndex(l => l.id === res.levelId);
      const next = LEVELS[idx + 1];
      if (next && !d.worldsUnlocked.includes(next.worldId) && LEVELS.filter(l => l.worldId === LEVELS[idx].worldId && l.data).every(l => this.record(l.id).completed)) d.worldsUnlocked.push(next.worldId);
    }
    const newItems = this.checkUnlocks();
    this.save.save();
    return { newBest, stars, newItems, rank };
  }

  totalStars(): number { return Object.values(this.save.data.levels).reduce((a, l) => a + l.stars, 0); }

  meets(u: Unlock): boolean {
    const d = this.save.data;
    switch (u.type) {
      case 'default': return true;
      case 'levelComplete': return !!d.levels[u.level]?.completed;
      case 'stars': return this.totalStars() >= u.n;
      case 'jumps': return d.stats.totalJumps >= u.n;
      case 'boosts': return d.stats.totalBoosts >= u.n;
      case 'runs': return d.stats.runs >= u.n;
    }
  }

  isUnlocked(item: ItemDef): boolean { return this.save.data.unlocked.includes(item.itemId); }

  /** Re-evaluate every unlock condition; returns the items that just became available. */
  checkUnlocks(): ItemDef[] {
    const d = this.save.data, fresh: ItemDef[] = [];
    for (const it of ITEMS) if (!d.unlocked.includes(it.itemId) && this.meets(it.unlockCondition)) { d.unlocked.push(it.itemId); fresh.push(it); }
    if (fresh.length) this.save.save();
    return fresh;
  }

  describe(u: Unlock): string {
    switch (u.type) {
      case 'default': return '';
      case 'levelComplete': return `Complete ${LEVELS.find(l => l.id === u.level)?.name ?? u.level}`;
      case 'stars': return `Collect ${u.n} ★`;
      case 'jumps': return `${u.n} jumps in total`;
      case 'boosts': return `${u.n} boosts in total`;
      case 'runs': return `Finish ${u.n} runs`;
    }
  }

  equip(item: ItemDef): boolean {
    if (!this.isUnlocked(item)) return false;
    this.save.data.equipped[item.category] = item.itemId;
    this.save.save();
    return true;
  }
}
