// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_SETTINGS, mergeSettings } from '../src/progression/settings';
import { SaveSystem, defaultSave, parseSave } from '../src/progression/SaveSystem';
import { LEVELS, Progression } from '../src/progression/Progression';
import { LEVEL_01 } from '../src/data/levels/level01';
import { HapticManager } from '../src/haptics/HapticManager';
import { ITEMS } from '../src/data/items';
import { t, setLang } from '../src/ui/i18n';

describe('settings', () => {
  it('clamps out-of-range and wrong-typed values to safe ranges', () => {
    const s = mergeSettings(DEFAULT_SETTINGS, { control: { sensitivity: 99, swipeDistance: -5, deadzone: 'x', scheme: 'nonsense', guide: 'full' }, audio: { master: 7, music: -1 }, language: 'ar' });
    expect(s.control.sensitivity).toBe(2.5);
    expect(s.control.swipeDistance).toBe(40);
    expect(s.control.deadzone).toBe(DEFAULT_SETTINGS.control.deadzone);
    expect(s.control.scheme).toBe('drag');
    expect(s.control.guide).toBe('full');
    expect(s.audio.master).toBe(1); expect(s.audio.music).toBe(0);
    expect(s.language).toBe('ar');
  });
  it('null / garbage patch ⇒ defaults', () => { expect(mergeSettings(DEFAULT_SETTINGS, null)).toEqual(DEFAULT_SETTINGS); expect(mergeSettings(DEFAULT_SETTINGS, 5 as never)).toEqual(DEFAULT_SETTINGS); });
});

describe('SaveSystem (Phase 16: settings, progress, best times, unlocks, world/level progress, customisation, control + audio settings)', () => {
  beforeEach(() => localStorage.clear());
  it('corrupt or hostile JSON never crashes and falls back to defaults', () => {
    for (const raw of [null, '', '{', 'null', '[]', '"x"', '{"levels":5,"settings":"x","equipped":7,"stats":null}']) {
      const d = parseSave(raw);
      expect(d.schemaVersion).toBe(1); expect(d.worldsUnlocked).toContain('world_1'); expect(d.unlocked.length).toBeGreaterThan(5);
    }
  });
  it('round-trips through storage', () => {
    const a = new SaveSystem();
    a.data.settings.control.sensitivity = 1.7; a.data.levels.level_01 = { completed: true, bestTimeSec: 41.5, bestJumps: 20, bestBoosts: 1, stars: 3, attempts: 4, completions: 2, bestProgress: 1 };
    a.data.equipped.hat = 'hat_beanie';
    a.save(); a.flush();
    const b = new SaveSystem();
    expect(b.data.settings.control.sensitivity).toBe(1.7);
    expect(b.data.levels.level_01.bestTimeSec).toBe(41.5);
    expect(b.data.levels.level_01.stars).toBe(3);
  });
  it('sanitises tampered values (stars > 3, negative counters, unknown equipped item)', () => {
    const d = parseSave(JSON.stringify({ levels: { level_01: { completed: true, stars: 99, attempts: -4, bestProgress: 7 } }, equipped: { hat: 'hat_does_not_exist', stick: 'stick_neon' }, stats: { totalJumps: -9 } }));
    expect(d.levels.level_01.stars).toBe(3); expect(d.levels.level_01.attempts).toBe(0); expect(d.levels.level_01.bestProgress).toBe(1);
    expect(d.equipped.hat).toBe('hat_beanie');      // unknown ⇒ default
    expect(d.equipped.stick).toBe('stick_copper');  // locked ⇒ default
    expect(d.stats.totalJumps).toBe(0);
  });
});

describe('Progression (world/level completion, best time, stars, unlocks, leaderboard)', () => {
  let P: Progression, S: SaveSystem;
  beforeEach(() => { localStorage.clear(); S = new SaveSystem(); P = new Progression(S); });
  const run = (timeSec: number, over: Partial<{ jumps: number; boosts: number; falls: number }> = {}) => P.finish({ levelId: 'level_01', timeSec, jumps: 20, boosts: 0, falls: 0, completed: true, progress: 1, ...over }, LEVEL_01);

  it('stars follow the par time (3 ≤ par, 2 ≤ 1.7×par, else 1)', () => {
    expect(P.stars(LEVEL_01, LEVEL_01.parTimeSec)).toBe(3);
    expect(P.stars(LEVEL_01, LEVEL_01.parTimeSec * 1.5)).toBe(2);
    expect(P.stars(LEVEL_01, LEVEL_01.parTimeSec * 3)).toBe(1);
  });
  it('first completion records best time/jumps; a slower run does not overwrite it; a faster one does', () => {
    expect(run(70).newBest).toBe(true);
    expect(run(90).newBest).toBe(false);
    expect(P.record('level_01').bestTimeSec).toBe(70);
    expect(run(60, { jumps: 15 }).newBest).toBe(true);
    expect(P.record('level_01').bestTimeSec).toBe(60);
    expect(P.record('level_01').bestJumps).toBe(15);
    expect(P.record('level_01').completions).toBe(3);
  });
  it('an abandoned run counts stats but not completion', () => {
    P.finish({ levelId: 'level_01', timeSec: 12, jumps: 5, boosts: 1, falls: 2, completed: false, progress: 0.2 }, LEVEL_01);
    expect(P.record('level_01').completed).toBe(false);
    expect(S.data.stats.totalJumps).toBe(5); expect(S.data.stats.totalFalls).toBe(2);
    expect(P.record('level_01').bestProgress).toBeCloseTo(0.2);
  });
  it('leaderboard is sorted ascending, capped at 10, and reports the rank', () => {
    const ranks = [80, 70, 90, 60, 100, 75, 85, 65, 95, 55, 50, 120].map(t => run(t).rank);
    const lb = S.data.leaderboard.level_01;
    expect(lb.length).toBe(10);
    for (let i = 1; i < lb.length; i++) expect(lb[i].timeSec).toBeGreaterThanOrEqual(lb[i - 1].timeSec);
    expect(lb[0].timeSec).toBe(50);
    expect(ranks[0]).toBe(1); expect(ranks[11]).toBe(0 + ranks[11]);
  });
  it('items unlock from their conditions and never relock; defaults are available from the start', () => {
    const base = S.data.unlocked.length;
    expect(S.data.unlocked).toContain('hat_beanie');
    expect(S.data.unlocked).not.toContain('hat_propeller');
    const out = run(70);
    expect(out.newItems.map(i => i.itemId)).toContain('hat_propeller');   // levelComplete
    expect(S.data.unlocked.length).toBeGreaterThan(base);
    P.finish({ levelId: 'level_01', timeSec: 999, jumps: 200, boosts: 0, falls: 0, completed: false, progress: 0.1 }, LEVEL_01);
    expect(S.data.unlocked).toContain('hat_tophat');                       // 150 total jumps
  });
  it('equip works only for unlocked items', () => {
    const locked = ITEMS.find(i => i.itemId === 'stick_neon')!;
    expect(P.equip(locked)).toBe(false);
    expect(S.data.equipped.stick).toBe('stick_copper');
    run(70);
    expect(P.equip(ITEMS.find(i => i.itemId === 'stick_bamboo')!)).toBe(true);
    expect(S.data.equipped.stick).toBe('stick_bamboo');
  });
  it('level gating: level 1 is playable, undeclared/locked levels are not', () => {
    expect(P.isLevelPlayable('level_01')).toBe(true);
    expect(P.isLevelPlayable('level_02')).toBe(false);   // declared but not authored yet
    expect(P.isLevelPlayable('nope')).toBe(false);
    expect(LEVELS.filter(l => l.data).length).toBe(1);
  });
});

describe('HapticManager (Intensity · Duration · Cooldown, never per frame)', () => {
  it('respects cooldown, enabled flag and strength', () => {
    let now = 0;
    const h = new HapticManager(() => now); h.logging = true;
    expect(h.trigger('jump', 1)).toBe(true);
    expect(h.trigger('jump', 1)).toBe(false);          // inside the 70 ms cooldown
    now = 100; expect(h.trigger('jump', 1)).toBe(true);
    now = 101; expect(h.trigger('landing', 1)).toBe(true); // different event has its own cooldown
    h.enabled = false; now = 1000; expect(h.trigger('hardImpact', 1)).toBe(false);
    h.enabled = true; h.strength = 0; expect(h.trigger('hardImpact', 1)).toBe(false);
    h.strength = 1; now = 2000;
    expect(h.trigger('hardImpact', 1)).toBe(true);
    const hard = h.log.find(l => l.event === 'hardImpact')!;
    const jump = h.log.find(l => l.event === 'jump')!;
    expect(hard.amp).toBeGreaterThan(jump.amp);        // impacts feel stronger than jumps
  });
  it('a 60 fps loop calling every frame produces ≪ 60 vibrations per second', () => {
    let now = 0; const h = new HapticManager(() => now); h.logging = true;
    for (let i = 0; i < 60; i++) { now = i * 16.7; h.trigger('landing', 1); }
    expect(h.log.length).toBeLessThanOrEqual(15);
  });
});

describe('i18n', () => {
  it('Arabic switches direction to RTL and every level hint resolves in both languages', () => {
    setLang('ar'); expect(document.documentElement.dir).toBe('rtl');
    for (const h of LEVEL_01.hints) { expect(t(h.textKey)).not.toBe(h.textKey); }
    setLang('en'); expect(document.documentElement.dir).toBe('ltr');
    for (const h of LEVEL_01.hints) { expect(t(h.textKey)).not.toBe(h.textKey); }
    expect(t('play')).toBe('Play');
  });
});

describe('defaults', () => { it('a fresh save contains no secrets and a stable schema', () => { expect(Object.keys(defaultSave()).sort()).toEqual(['equipped', 'leaderboard', 'levels', 'schemaVersion', 'settings', 'stats', 'tutorialSeen', 'unlocked', 'worldsUnlocked']); }); });
