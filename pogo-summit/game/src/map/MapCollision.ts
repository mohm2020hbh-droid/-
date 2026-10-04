/**
 * MapCollision — collision shapes → convex polygons (SPEC §9).
 *
 * The physics core collides convex polygons, so every shape of the map format is reduced to one or more convex,
 * counter-clockwise polygons in the entity's local frame; `placePolys` then applies scale · rotation · position.
 * Visual meshes never come through here (Visual mesh ≠ collision shape).
 */
import type { ShapeDef, Vec2 } from './schema';
import { DEG } from '../sim/math';
import { isConvexCCW, rotatePoints, signedArea } from '../sim/geometry';

export const MAX_CONVEX_VERTS = 16;
export const MIN_AREA = 1e-4;

export interface ShapeProblem { code: 'COLLISION_INVALID'; message: string }
export interface ShapePolys { polys: Vec2[][]; problems: ShapeProblem[] }

const bad = (message: string): ShapeProblem => ({ code: 'COLLISION_INVALID', message });
const finite = (pts: Vec2[]): boolean => pts.every(p => Number.isFinite(p.x) && Number.isFinite(p.y));
const shift = (pts: Vec2[], o?: Vec2): Vec2[] => (o && (o.x || o.y) ? pts.map(p => ({ x: p.x + o.x, y: p.y + o.y })) : pts);

/** Andrew monotone-chain convex hull, CCW, collinear points dropped. */
export function convexHull(points: Vec2[]): Vec2[] {
  const pts = [...points].sort((a, b) => a.x - b.x || a.y - b.y);
  if (pts.length < 3) return pts;
  const cross = (o: Vec2, a: Vec2, b: Vec2) => (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
  const lower: Vec2[] = [];
  for (const p of pts) { while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) lower.pop(); lower.push(p); }
  const upper: Vec2[] = [];
  for (let i = pts.length - 1; i >= 0; i--) { const p = pts[i]; while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) upper.pop(); upper.push(p); }
  lower.pop(); upper.pop();
  return lower.concat(upper);
}

/** True if no two non-adjacent edges of the closed polygon cross. */
export function isSimplePolygon(pts: Vec2[]): boolean {
  const n = pts.length;
  const ccw = (a: Vec2, b: Vec2, c: Vec2) => (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
  for (let i = 0; i < n; i++) {
    const a = pts[i], b = pts[(i + 1) % n];
    for (let j = i + 1; j < n; j++) {
      if (j === i || (j + 1) % n === i || (i + 1) % n === j) continue;
      const c = pts[j], d = pts[(j + 1) % n];
      const d1 = ccw(a, b, c), d2 = ccw(a, b, d), d3 = ccw(c, d, a), d4 = ccw(c, d, b);
      if (((d1 > 1e-12 && d2 < -1e-12) || (d1 < -1e-12 && d2 > 1e-12)) && ((d3 > 1e-12 && d4 < -1e-12) || (d3 < -1e-12 && d4 > 1e-12))) return false;
    }
  }
  return true;
}

/** Ear-clipping triangulation of a simple polygon (any winding). Returns CCW triangles or null if it is not simple. */
export function triangulate(outline: Vec2[]): Vec2[][] | null {
  const pts = signedArea(outline) < 0 ? [...outline].reverse() : [...outline];
  const idx = pts.map((_, i) => i);
  const tris: Vec2[][] = [];
  const cross = (a: Vec2, b: Vec2, c: Vec2) => (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
  const inTri = (p: Vec2, a: Vec2, b: Vec2, c: Vec2) => cross(a, b, p) > 1e-12 && cross(b, c, p) > 1e-12 && cross(c, a, p) > 1e-12;
  let guard = idx.length * idx.length + 10;
  while (idx.length > 3 && guard-- > 0) {
    let clipped = false;
    for (let k = 0; k < idx.length; k++) {
      const ia = idx[(k + idx.length - 1) % idx.length], ib = idx[k], ic = idx[(k + 1) % idx.length];
      const a = pts[ia], b = pts[ib], c = pts[ic];
      const cr = cross(a, b, c);
      if (Math.abs(cr) <= 1e-12) { idx.splice(k, 1); clipped = true; break; }           // collinear vertex: drop
      if (cr < 0) continue;                                                              // reflex
      let ok = true;
      for (const j of idx) { if (j === ia || j === ib || j === ic) continue; if (inTri(pts[j], a, b, c)) { ok = false; break; } }
      if (!ok) continue;
      tris.push([a, b, c]);
      idx.splice(k, 1); clipped = true; break;
    }
    if (!clipped) return null;
  }
  if (idx.length === 3) { const [a, b, c] = idx.map(i => pts[i]); if (Math.abs(cross(a, b, c)) > 1e-12) tris.push([a, b, c]); }
  return tris;
}

const same = (a: Vec2, b: Vec2): boolean => Math.abs(a.x - b.x) < 1e-9 && Math.abs(a.y - b.y) < 1e-9;

/** Hertel–Mehlhorn: merge triangles across shared edges while the union stays convex (≤ MAX_CONVEX_VERTS). */
export function mergeConvex(tris: Vec2[][]): Vec2[][] {
  let polys = tris.map(t => [...t]);
  let merged = true;
  while (merged) {
    merged = false;
    outer: for (let i = 0; i < polys.length; i++) {
      for (let j = i + 1; j < polys.length; j++) {
        const A = polys[i], B = polys[j];
        for (let ai = 0; ai < A.length; ai++) {
          const u = A[ai], v = A[(ai + 1) % A.length];
          const bi = B.findIndex((p, k) => same(p, v) && same(B[(k + 1) % B.length], u));
          if (bi < 0) continue;
          // A ring from v … u, then B's vertices strictly between u and v (B walks u → … → v)
          const ring: Vec2[] = [];
          for (let k = 0; k < A.length; k++) ring.push(A[(ai + 1 + k) % A.length]);                  // v … u
          const bu = (bi + 1) % B.length;                                                              // index of u in B
          for (let k = 1; k < B.length - 1; k++) ring.push(B[(bu + k) % B.length]);
          if (ring.length > MAX_CONVEX_VERTS || !isConvexCCW(ring)) continue;
          polys[i] = ring; polys.splice(j, 1); merged = true; break outer;
        }
      }
    }
  }
  polys = polys.map(p => p);
  return polys;
}

/** Concave simple outline → convex pieces. Returns null if the outline is not a simple polygon. */
export function decomposeConcave(outline: Vec2[]): Vec2[][] | null {
  if (outline.length < 3 || !isSimplePolygon(outline)) return null;
  const ccw = signedArea(outline) < 0 ? [...outline].reverse() : outline;
  if (isConvexCCW(ccw) && ccw.length <= MAX_CONVEX_VERTS) return [ccw];
  const tris = triangulate(ccw);
  return tris ? mergeConvex(tris) : null;
}

/** Problems of one convex polygon (empty ⇒ fine). */
export function convexProblems(pts: Vec2[]): string[] {
  const out: string[] = [];
  if (pts.length < 3) return ['fewer than 3 vertices'];
  if (!finite(pts)) return ['non-finite coordinate'];
  if (pts.length > MAX_CONVEX_VERTS) out.push(`more than ${MAX_CONVEX_VERTS} vertices`);
  const a = signedArea(pts);
  if (Math.abs(a) < MIN_AREA) out.push('area below 1e-4 m²');
  else if (!isConvexCCW(a < 0 ? [...pts].reverse() : pts)) out.push('polygon is not convex');
  return out;
}

/** Shape → convex CCW polygons in the entity's local frame. */
export function shapeToPolys(shape: ShapeDef): ShapePolys {
  const problems: ShapeProblem[] = [];
  let polys: Vec2[][] = [];
  switch (shape.kind) {
    case 'box': {
      const w = shape.w, h = shape.h, taper = shape.taper ?? 1;
      const hw = w / 2, bw = (w * taper) / 2;
      polys = [shape.anchor === 'topCenter'
        ? [{ x: -hw, y: 0 }, { x: -bw, y: -h }, { x: bw, y: -h }, { x: hw, y: 0 }]
        : [{ x: -bw, y: -h / 2 }, { x: bw, y: -h / 2 }, { x: hw, y: h / 2 }, { x: -hw, y: h / 2 }]];
      polys = polys.map(p => shift(p, shape.offset));
      break;
    }
    case 'sphere': {
      const n = Math.max(6, Math.min(MAX_CONVEX_VERTS, Math.floor(shape.segments ?? 16)));
      const pts: Vec2[] = [];
      for (let i = 0; i < n; i++) { const a = (2 * Math.PI * i) / n; pts.push({ x: shape.r * Math.cos(a), y: shape.r * Math.sin(a) }); }
      polys = [shift(pts, shape.offset)];
      break;
    }
    case 'capsule': {
      const seg = Math.max(2, Math.min(7, Math.floor(shape.segments ?? 6)));
      const half = shape.length / 2, r = shape.r;
      const pts: Vec2[] = [];
      for (let i = 0; i <= seg; i++) { const a = -Math.PI / 2 + (Math.PI * i) / seg; pts.push({ x: half + r * Math.cos(a), y: r * Math.sin(a) }); }
      for (let i = 0; i <= seg; i++) { const a = Math.PI / 2 + (Math.PI * i) / seg; pts.push({ x: -half + r * Math.cos(a), y: r * Math.sin(a) }); }
      const rotated = shape.axis === 'y' ? rotatePoints(pts, Math.PI / 2) : pts;
      polys = [shift(rotated, shape.offset)];
      break;
    }
    case 'slope': {
      const hw = shape.w / 2, hh = shape.h / 2;
      polys = [shape.mirror ? [{ x: -hw, y: -hh }, { x: hw, y: -hh }, { x: -hw, y: hh }] : [{ x: -hw, y: -hh }, { x: hw, y: -hh }, { x: hw, y: hh }]];
      break;
    }
    case 'convex': {
      const pts = shape.points;
      polys = [signedArea(pts) < 0 ? [...pts].reverse() : pts];
      break;
    }
    case 'mesh': {
      if (shape.polygons) polys = shape.polygons.map(p => (signedArea(p) < 0 ? [...p].reverse() : p));
      if (shape.outline) {
        const d = decomposeConcave(shape.outline);
        if (!d) problems.push(bad('mesh outline is not a simple polygon (self-intersecting or degenerate)'));
        else polys = polys.concat(d);
      }
      // pre-decomposed pieces must already be convex; concave ones are decomposed instead of rejected
      const fixed: Vec2[][] = [];
      for (const p of polys) {
        if (p.length >= 3 && finite(p) && !isConvexCCW(p) && Math.abs(signedArea(p)) >= MIN_AREA) { const d = decomposeConcave(p); if (d) { fixed.push(...d); continue; } }
        fixed.push(p);
      }
      polys = fixed;
      break;
    }
  }
  for (const p of polys) for (const m of convexProblems(p)) problems.push(bad(m));
  return { polys, problems };
}

/** Scale · rotate (degrees CCW) · translate. A mirrored scale keeps the polygons counter-clockwise. */
export function placePolys(polys: Vec2[][], x: number, y: number, rotationDeg: number, sx = 1, sy = 1): Vec2[][] {
  const flip = sx * sy < 0;
  return polys.map(p => {
    let pts = sx === 1 && sy === 1 ? p : p.map(v => ({ x: v.x * sx, y: v.y * sy }));
    if (flip) pts = [...pts].reverse();
    if (rotationDeg) pts = rotatePoints(pts, rotationDeg * DEG, 0, 0);
    return pts.map(v => ({ x: v.x + x, y: v.y + y }));
  });
}

export interface Aabb { minX: number; minY: number; maxX: number; maxY: number }
export function polysAabb(polys: Vec2[][]): Aabb {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const p of polys) for (const v of p) { if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x; if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y; }
  return { minX, minY, maxX, maxY };
}
export const unionAabb = (a: Aabb, b: Aabb): Aabb => ({ minX: Math.min(a.minX, b.minX), minY: Math.min(a.minY, b.minY), maxX: Math.max(a.maxX, b.maxX), maxY: Math.max(a.maxY, b.maxY) });
export const emptyAabb = (): Aabb => ({ minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity });
