import { describe, expect, it } from 'vitest';
import { isConvexCCW, signedArea } from '../../src/sim/geometry';
import { platformPoints } from '../../src/data/LevelData';
import { decomposeConcave, placePolys, polysAabb, shapeToPolys, isSimplePolygon, convexProblems } from '../../src/map/MapCollision';

const area = (ps: { x: number; y: number }[][]) => ps.reduce((a, p) => a + Math.abs(signedArea(p)), 0);

describe('MapCollision — shapes → convex polygons', () => {
  it('box with topCenter anchor + taper reproduces the existing platform silhouette bit for bit', () => {
    const p = { id: 'a', kind: 'rock' as const, x: 12.5, y: 3.25, w: 6.5, h: 4, taper: 0.72 };
    const mine = placePolys(shapeToPolys({ kind: 'box', w: 6.5, h: 4, taper: 0.72, anchor: 'topCenter' }).polys, 12.5, 3.25, 0)[0];
    expect(mine).toEqual(platformPoints(p));
    const rot = placePolys(shapeToPolys({ kind: 'box', w: 6.5, h: 4, taper: 0.72, anchor: 'topCenter' }).polys, 12.5, 3.25, 15)[0];
    expect(rot).toEqual(platformPoints({ ...p, angleDeg: 15 }));
  });

  it('every primitive yields convex CCW polygons with the right area', () => {
    const box = shapeToPolys({ kind: 'box', w: 4, h: 2 });
    expect(box.problems).toEqual([]); expect(area(box.polys)).toBeCloseTo(8, 9); expect(isConvexCCW(box.polys[0])).toBe(true);
    const sph = shapeToPolys({ kind: 'sphere', r: 1, segments: 16 });
    expect(sph.polys[0].length).toBe(16); expect(area(sph.polys)).toBeCloseTo(0.5 * 16 * Math.sin((2 * Math.PI) / 16), 9);
    const cap = shapeToPolys({ kind: 'capsule', r: 0.5, length: 3 });
    expect(cap.problems).toEqual([]); expect(cap.polys[0].length).toBeLessThanOrEqual(16);
    const bb = polysAabb(cap.polys); expect(bb.maxX - bb.minX).toBeCloseTo(4, 9); expect(bb.maxY - bb.minY).toBeCloseTo(1, 9);
    const capY = shapeToPolys({ kind: 'capsule', r: 0.5, length: 3, axis: 'y' });
    const bbY = polysAabb(capY.polys); expect(bbY.maxY - bbY.minY).toBeCloseTo(4, 9);
    const slope = shapeToPolys({ kind: 'slope', w: 6, h: 3 });
    expect(area(slope.polys)).toBeCloseTo(9, 9); expect(isConvexCCW(slope.polys[0])).toBe(true);
    const mir = shapeToPolys({ kind: 'slope', w: 6, h: 3, mirror: true });
    expect(isConvexCCW(mir.polys[0])).toBe(true); expect(mir.polys[0][2]).toEqual({ x: -3, y: 1.5 });
  });

  it('convex: clockwise input is fixed, a non-convex polygon is reported', () => {
    const cw = shapeToPolys({ kind: 'convex', points: [{ x: 0, y: 0 }, { x: 0, y: 2 }, { x: 2, y: 2 }, { x: 2, y: 0 }] });
    expect(cw.problems).toEqual([]); expect(signedArea(cw.polys[0])).toBeGreaterThan(0);
    const dent = shapeToPolys({ kind: 'convex', points: [{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 2, y: 1 }, { x: 4, y: 4 }, { x: 0, y: 4 }] });
    expect(dent.problems.length).toBeGreaterThan(0);
  });

  it('mesh: a concave L outline is decomposed into convex pieces covering exactly the same area', () => {
    const L = [{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 1 }, { x: 1, y: 1 }, { x: 1, y: 4 }, { x: 0, y: 4 }];
    const r = shapeToPolys({ kind: 'mesh', outline: L });
    expect(r.problems).toEqual([]);
    expect(r.polys.length).toBeGreaterThanOrEqual(2);
    expect(r.polys.every(p => isConvexCCW(p) && p.length <= 16)).toBe(true);
    expect(area(r.polys)).toBeCloseTo(7, 9);                     // 4·1 + 1·3
  });

  it('mesh: a U shape and a many-vertex "bean" decompose with conserved area; a self-crossing outline is rejected', () => {
    const U = [{ x: 0, y: 0 }, { x: 6, y: 0 }, { x: 6, y: 5 }, { x: 4, y: 5 }, { x: 4, y: 2 }, { x: 2, y: 2 }, { x: 2, y: 5 }, { x: 0, y: 5 }];
    const u = decomposeConcave(U)!;
    expect(u).not.toBeNull(); expect(area(u)).toBeCloseTo(6 * 5 - 2 * 3, 9);
    const bean = Array.from({ length: 40 }, (_, i) => { const a = (2 * Math.PI * i) / 40; const r = 3 + Math.sin(3 * a); return { x: r * Math.cos(a), y: r * Math.sin(a) }; });
    const b = decomposeConcave(bean)!;
    expect(b).not.toBeNull(); expect(b.every(p => isConvexCCW(p) && p.length <= 16)).toBe(true);
    expect(area(b)).toBeCloseTo(Math.abs(signedArea(bean)), 6);
    const bow = [{ x: 0, y: 0 }, { x: 2, y: 2 }, { x: 2, y: 0 }, { x: 0, y: 2 }];
    expect(isSimplePolygon(bow)).toBe(false);
    expect(shapeToPolys({ kind: 'mesh', outline: bow }).problems.length).toBeGreaterThan(0);
  });

  it('degenerate shapes are flagged (zero area, NaN, too many vertices)', () => {
    expect(shapeToPolys({ kind: 'box', w: 0, h: 2 }).problems.length).toBeGreaterThan(0);
    expect(convexProblems([{ x: 0, y: 0 }, { x: NaN, y: 0 }, { x: 1, y: 1 }])).toContain('non-finite coordinate');
    const many = Array.from({ length: 20 }, (_, i) => ({ x: Math.cos((2 * Math.PI * i) / 20), y: Math.sin((2 * Math.PI * i) / 20) }));
    expect(convexProblems(many).some(m => m.includes('vertices'))).toBe(true);
  });

  it('placePolys: negative scale mirrors and stays CCW; rotation + translation applied', () => {
    const base = shapeToPolys({ kind: 'slope', w: 2, h: 2 }).polys;
    const m = placePolys(base, 0, 0, 0, -1, 1);
    expect(signedArea(m[0])).toBeGreaterThan(0);
    const r = placePolys(shapeToPolys({ kind: 'box', w: 2, h: 2 }).polys, 5, 5, 90);
    const bb = polysAabb(r); expect(bb.minX).toBeCloseTo(4, 9); expect(bb.maxX).toBeCloseTo(6, 9);
  });
});
