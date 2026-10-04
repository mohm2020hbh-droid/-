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

describe('LEVEL_01 solvability (locked-spec physics, brute-force angle × load grid)', () => {
  const cfg = createPhysicsConfig();
  // Conservative: every standing node is assumed to have the v = 0 landing window (L ∈ [40, 95]); the standing points
  // include both platform ends (the tight hops are launched from an edge).
  const a = analyzeLevel(LEVEL_01, cfg, { samplesX: [0.02, 0.5, 0.98], phases: [0, 360] });

  it('the goal is reachable from the start', () => expect(a.goalReached).toBe(true));
  it('every platform is reachable', () => expect(a.unreachable).toEqual([]));

  it('every hop of the designed route has at least one (angle, load) cell that lands on its target', () => {
    const route = LEVEL_01.route!;
    const missing: string[] = [];
    for (let i = 0; i < route.length - 1; i++) {
      const best = Math.max(0, ...a.edges.filter(e => e.from === route[i] && e.to === route[i + 1]).map(e => e.cells));
      if (best < 1) missing.push(`${route[i]}→${route[i + 1]}`);
    }
    expect(missing).toEqual([]);
  });
}, 120_000);
