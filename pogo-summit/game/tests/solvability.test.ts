import { describe, expect, it } from 'vitest';
import { LEVEL_01 } from '../src/data/levels/level01';
import { validateLevel } from '../src/data/LevelData';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { PhysicsWorld } from '../src/sim/PhysicsWorld';
import { analyzeLevel } from '../src/sim/analysis';
import { polysOverlapDepth } from '../src/sim/geometry';
import { createPogoState } from '../src/sim/PogoState';

describe('LEVEL_01 data', () => {
  it('passes structural validation', () => expect(validateLevel(LEVEL_01)).toEqual([]));

  it('starts planted on solid ground', () => {
    const w = new PhysicsWorld(LEVEL_01);
    const s = createPogoState(w, createPhysicsConfig());
    expect(w.colliders[s.groundId].id).toBe('start');
  });

  it('no two solid colliders overlap (touching allowed) — prevents hidden walls inside the route', () => {
    const w = new PhysicsWorld(LEVEL_01);
    const bad: string[] = [];
    const sol = w.solids;
    for (let i = 0; i < sol.length; i++) for (let j = i + 1; j < sol.length; j++) {
      const a = sol[i], b = sol[j];
      if (a.move || b.move) {
        // moving platforms: test the extremes of their range
        const cases = [-1, 0, 1];
        let worst = 0;
        for (const k of cases) {
          const shift = (c: typeof a, f: number) => ({ ...c.poly, pts: c.poly.pts.map(p => ({ x: p.x + (c.move ? c.move.dx * f : 0), y: p.y + (c.move ? c.move.dy * f : 0) })) });
          worst = Math.max(worst, polysOverlapDepth(shift(a, k), shift(b, k)));
        }
        if (worst > 0.05) bad.push(`${a.id}×${b.id} ${worst.toFixed(2)}`);
        continue;
      }
      const d = polysOverlapDepth(a.poly, b.poly);
      if (d > 0.05) bad.push(`${a.id}×${b.id} ${d.toFixed(2)}`);
    }
    expect(bad).toEqual([]);
  });
});

describe('LEVEL_01 solvability (real physics, brute-force angle × power grid)', () => {
  const cfg = createPhysicsConfig();
  const a = analyzeLevel(LEVEL_01, cfg);

  it('the goal is reachable from the start', () => expect(a.goalReached).toBe(true));
  it('every platform is reachable', () => expect(a.unreachable).toEqual([]));

  it('every hop of the designed route has a forgiving target (≥ 10 of 297 grid cells, i.e. a window ≥ ~15° × 0.35 power)', () => {
    const route = LEVEL_01.route!;
    const weak: string[] = [];
    for (let i = 0; i < route.length - 1; i++) {
      const best = Math.max(0, ...a.edges.filter(e => e.from === route[i] && e.to === route[i + 1]).map(e => e.cells));
      // pad rebound grid is 81 cells; normal hops 297
      const min = route[i].startsWith('pad') ? 10 : 10;
      if (best < min) weak.push(`${route[i]}→${route[i + 1]}:${best}`);
    }
    expect(weak).toEqual([]);
  });
});
