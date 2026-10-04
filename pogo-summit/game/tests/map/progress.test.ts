import { describe, expect, it } from 'vitest';
import { progressFraction } from '../../src/data/LevelData';
import { TICK_RATE } from '../../src/sim/math';
import { ProgressTracker, buildProgress, routePercents } from '../../src/map/MapProgress';
import { CheckpointTracker, MemoryRunStore, RunRecorder, medalFor, type CheckpointHit, type CheckpointSkip } from '../../src/map/MapCheckpoint';
import { regionContains, pointInPolygon, regionAabb } from '../../src/map/MapRegion';
import type { CheckpointDef, ProgressDef, SplitsDef } from '../../src/map/schema';

const line = (...pts: [number, number][]): ProgressDef => ({ routes: [{ id: 'main', kind: 'main', points: pts.map(([x, y]) => ({ x, y })) }] });

describe('MapProgress — percent anchors', () => {
  it('first point = 0, last = 100, the rest interpolated by length; explicit anchors pin a point', () => {
    const pts = [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 20, y: 0 }];
    expect(routePercents(pts, 0, 100)).toEqual([0, 50, 100]);
    expect(routePercents([pts[0], { ...pts[1], percent: 30 }, pts[2]], 0, 100)).toEqual([0, 30, 100]);
    expect(routePercents([{ x: 0, y: 0 }, { x: 3, y: 0 }, { x: 4, y: 0 }, { x: 8, y: 0 }], 0, 100).map(v => +v.toFixed(6))).toEqual([0, 37.5, 50, 100]);
  });

  it('projection is exact on straight routes and clamps outside the ends', () => {
    const m = buildProgress(line([0, 0], [10, 0], [20, 0]));
    expect(m.length).toBe(20);
    const t = new ProgressTracker(m);
    expect(t.update(5, 1).percent).toBeCloseTo(25, 9);
    expect(t.update(15, -3).percent).toBeCloseTo(75, 9);
    expect(t.update(40, 0).percent).toBe(100);
    t.reset(); expect(t.update(-5, 0).percent).toBe(0);
    expect(t.sample.lateral).toBe(5);
    expect(m.pointAt(75)).toEqual({ x: 15, y: 0 });
  });

  it('a route that doubles back does not snap to the far leg (the legacy nearest-point failure)', () => {
    const pts = [{ x: 0, y: 0 }, { x: 20, y: 0 }, { x: 20, y: 4 }, { x: 0, y: 4 }];
    const model = buildProgress({ routes: [{ id: 'main', kind: 'main', points: pts }] });
    const t = new ProgressTracker(model);
    t.update(0, 0);
    let last = 0;
    for (let x = 1; x <= 10; x++) { last = t.update(x, 2.6).percent; }          // walking the first leg, 1.4 m from the return leg
    expect(last).toBeCloseTo((10 / 44) * 100, 6);
    expect(progressFraction(pts, 10, 2.6) * 100).toBeGreaterThan(60);           // nearest-point (old behaviour) jumps to the return leg
    // follow the route all the way: monotone
    let prev = last;
    for (const [x, y] of [[20, 1], [20, 3], [15, 3.9], [5, 4]] as [number, number][]) { const p = t.update(x, y).percent; expect(p).toBeGreaterThanOrEqual(prev - 1e-9); prev = p; }
    expect(prev).toBeCloseTo(((20 + 4 + 15) / 44) * 100, 6);
  });

  it('branches map onto the percent interval of the main route they attach to', () => {
    const def: ProgressDef = { routes: [
      { id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 20, y: 0 }, { x: 40, y: 0 }] },
      { id: 'high', kind: 'optional', from: { route: 'main', point: 1 }, to: { route: 'main', point: 2 }, points: [{ x: 20, y: 0 }, { x: 30, y: 8 }, { x: 40, y: 0 }] },
    ] };
    const t = new ProgressTracker(buildProgress(def));
    t.update(0, 0);
    t.update(18, 0);
    const mid = t.update(30, 8);
    expect(mid.route).toBe('high'); expect(mid.percent).toBeCloseTo(75, 6);
    expect(t.update(40, 0).percent).toBeCloseTo(100, 9);
  });

  it('checkpoint pins raise `max` but not `current`; relocation after a teleport searches globally', () => {
    const t = new ProgressTracker(buildProgress(line([0, 0], [100, 0])));
    t.update(10, 0);
    t.pin(60);
    expect(t.max).toBe(60); expect(t.current).toBeCloseTo(10, 9);
    const far = t.update(95, 1);                                              // outside the window, > relocate distance from it → global search
    expect(far.percent).toBeCloseTo(95, 9); expect(far.max).toBeCloseTo(95, 9);
    t.update(5, 0); expect(t.max).toBeCloseTo(95, 9);                          // max never decreases
  });

  it('model problems: no main route, bad attachment, non-monotonic anchors, zero length', () => {
    expect(buildProgress({ routes: [] }).problems).toContain('no main route');
    expect(buildProgress(line([0, 0], [0, 0])).problems.some(p => p.includes('zero length'))).toBe(true);
    const bad = buildProgress({ routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 5, y: 0, percent: 80 }, { x: 10, y: 0, percent: 20 }] }] });
    expect(bad.problems.some(p => p.includes('non-monotonic'))).toBe(true);
    const att = buildProgress({ routes: [{ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 10, y: 0 }] }, { id: 'b', kind: 'branch', points: [{ x: 0, y: 0 }, { x: 1, y: 1 }] }] });
    expect(att.problems.some(p => p.includes('attach'))).toBe(true);
  });
});

describe('MapRegion — containment', () => {
  it('box / circle / polygon (concave, either winding)', () => {
    expect(regionContains({ kind: 'box', x: 0, y: 0, w: 4, h: 2 }, 2, 1)).toBe(true);
    expect(regionContains({ kind: 'box', x: 0, y: 0, w: 4, h: 2 }, 2.01, 0)).toBe(false);
    expect(regionContains({ kind: 'circle', x: 1, y: 1, r: 2 }, 2.4, 2.4)).toBe(true);
    expect(regionContains({ kind: 'circle', x: 1, y: 1, r: 2 }, 3, 3)).toBe(false);
    const L = [{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 1 }, { x: 1, y: 1 }, { x: 1, y: 4 }, { x: 0, y: 4 }];
    expect(pointInPolygon(L, 0.5, 3)).toBe(true); expect(pointInPolygon(L, 3, 3)).toBe(false); expect(pointInPolygon([...L].reverse(), 0.5, 3)).toBe(true);
    expect(regionAabb({ kind: 'polygon', points: L })).toEqual({ minX: 0, maxX: 4, minY: 0, maxY: 4 });
  });
});

const cp = (id: string, order: number, x: number, extra: Partial<CheckpointDef> = {}): CheckpointDef =>
  ({ id, order, region: { kind: 'box', x, y: 0, w: 2, h: 2 }, respawn: { x, y: 0 }, ...extra });
const step = (t: CheckpointTracker, x: number, tick = 0) => { const hits: CheckpointHit[] = [], skips: CheckpointSkip[] = []; t.update(x, 0, tick, hits, skips); return { hits: hits.map(h => h.def.id), skips: skips.map(s => `${s.def.id}>${s.expected}`) }; };

describe('MapCheckpoint — order rules', () => {
  it('monotonic (default): forward-only, a lower checkpoint reached later is ignored, ids are not limited to one digit', () => {
    const t = new CheckpointTracker([cp('a', 0, 10), cp('b', 1, 20), cp('c', 2, 30), ...Array.from({ length: 12 }, (_, i) => cp(`z${i}`, 10 + i, 100 + 10 * i))]);
    expect(step(t, 10).hits).toEqual(['a']);
    expect(step(t, 30).hits).toEqual(['c']);               // skipping b is allowed in monotonic mode …
    expect(step(t, 20).hits).toEqual([]);                  // … but b can no longer be taken
    expect(t.lastOrder).toBe(2); expect(t.count).toBe(2);
    expect(step(t, 100 + 10 * 11).hits).toEqual(['z11']);  // 14 checkpoints in total
  });
  it('strict: must be taken in sequence; entering the wrong one reports a skip once', () => {
    const t = new CheckpointTracker([cp('a', 0, 10, { orderMode: 'strict' }), cp('b', 1, 20, { orderMode: 'strict' })]);
    expect(step(t, 20)).toEqual({ hits: [], skips: ['b>a'] });
    expect(step(t, 20).skips).toEqual([]);                  // reported only once
    expect(step(t, 10).hits).toEqual(['a']);
    expect(step(t, 20).hits).toEqual(['b']);                // now allowed even though still "inside" earlier
  });
  it('any / optional checkpoints are independent; `requires` gates activation', () => {
    const t = new CheckpointTracker([cp('a', 0, 10), cp('side', 5, 50, { optional: true }), cp('gated', 1, 30, { requires: ['side'] })]);
    expect(step(t, 30).hits).toEqual([]);
    expect(step(t, 50).hits).toEqual(['side']);
    expect(t.lastOrder).toBe(-1);                           // optional does not advance the order
    expect(step(t, 30).hits).toEqual(['gated']);
  });
  it('reset clears everything', () => {
    const t = new CheckpointTracker([cp('a', 0, 10)]);
    step(t, 10); t.reset(); expect(t.count).toBe(0); expect(step(t, 10).hits).toEqual(['a']);
  });
});

describe('MapCheckpoint — splits, personal best, medals', () => {
  const splits: SplitsDef = { splits: [{ id: 's0', name: 'One', checkpoint: 'a', parSec: 10 }, { id: 's1', name: 'Two', checkpoint: 'b', parSec: 20 }], targets: { gold: 25, silver: 35, bronze: 50 } };
  it('records split and segment times against the run start, with deltas to par and to the personal best', () => {
    const store = new MemoryRunStore();
    const r1 = new RunRecorder('m', '1.0.0', splits, store);
    const start = 100;
    expect(r1.onCheckpoint('a', start + 12 * TICK_RATE, start)!.deltaToPB).toBeNull();
    const s1 = r1.onCheckpoint('b', start + 30 * TICK_RATE, start)!;
    expect(s1.timeSec).toBeCloseTo(30, 9); expect(s1.segmentSec).toBeCloseTo(18, 9); expect(s1.deltaToPar).toBeCloseTo(10, 9);
    expect(r1.onCheckpoint('b', start + 31 * TICK_RATE, start)).toBeNull();            // a split is recorded once
    expect(r1.onCheckpoint('zzz', 5, start)).toBeNull();                                // not a split checkpoint
    const sum1 = r1.summarize({ startedTick: start, finishedTick: start + 33 * TICK_RATE, jumps: 40, deaths: 2, progressMax: 100 });
    expect(sum1).toMatchObject({ completed: true, timeSec: 33, jumps: 40, deaths: 2, medal: 'silver' });

    const r2 = new RunRecorder('m', '1.0.0', splits, store);                            // second run compares with the stored best
    const a = r2.onCheckpoint('a', start + 10 * TICK_RATE, start)!;
    expect(a.deltaToPB).toBeCloseTo(-2, 9);                                             // 10 s vs 12 s
    r2.onCheckpoint('b', start + 26 * TICK_RATE, start);
    const sum2 = r2.summarize({ startedTick: start, finishedTick: start + 28 * TICK_RATE, jumps: 30, deaths: 0, progressMax: 100 });
    expect(sum2.medal).toBe('silver');
    const rec = store.get('m@1.0.0')!;
    expect(rec.best!.timeSec).toBe(28); expect(rec.runs).toBe(2); expect(rec.completions).toBe(2);
    expect(rec.bestSegments.s0).toBeCloseTo(10, 9); expect(rec.bestSegments.s1).toBeCloseTo(16, 9);

    const r3 = new RunRecorder('m', '1.0.0', splits, store);                            // a worse completion does not replace the best
    r3.summarize({ startedTick: 0, finishedTick: 90 * TICK_RATE, jumps: 1, deaths: 9, progressMax: 100 });
    expect(store.get('m@1.0.0')!.best!.timeSec).toBe(28);
    expect(MemoryRunStore.deserialize(store.serialize()).get('m@1.0.0')).toEqual(store.get('m@1.0.0'));
  });
  it('an abandoned run has no medal and does not touch the best', () => {
    const store = new MemoryRunStore();
    const r = new RunRecorder('m', '1', splits, store);
    expect(r.summarize({ startedTick: 5, finishedTick: -1, jumps: 3, deaths: 1, progressMax: 40 })).toMatchObject({ completed: false, medal: 'none', timeSec: 0 });
    expect(store.get('m@1')!.best).toBeUndefined();
  });
  it('medal thresholds', () => {
    expect([20, 25, 25.1, 35, 50, 51].map(t => medalFor(t, splits.targets))).toEqual(['gold', 'gold', 'silver', 'silver', 'bronze', 'none']);
    expect(medalFor(10, { gold: 0, silver: 0, bronze: 0 })).toBe('none');
  });
});
