import type { Vec2 } from './math';

/** Result of a circle-vs-convex-polygon test. `nx,ny` points from the polygon toward the circle centre. */
export interface Contact {
  depth: number;
  nx: number;
  ny: number;
  /** Closest point on the polygon boundary. */
  px: number;
  py: number;
}

export const newContact = (): Contact => ({ depth: 0, nx: 0, ny: 1, px: 0, py: 0 });

/** Convex polygon with cached edge normals (outward, CCW winding). */
export interface Poly {
  pts: Vec2[];
  enx: number[];
  eny: number[];
}

export function makePoly(pts: Vec2[]): Poly {
  const n = pts.length;
  const enx: number[] = [];
  const eny: number[] = [];
  for (let i = 0; i < n; i++) {
    const a = pts[i], b = pts[(i + 1) % n];
    const ex = b.x - a.x, ey = b.y - a.y;
    const len = Math.hypot(ex, ey) || 1;
    enx.push(ey / len);
    eny.push(-ex / len);
  }
  return { pts, enx, eny };
}

/** Signed area > 0 for CCW. */
export function signedArea(pts: Vec2[]): number {
  let a = 0;
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i], q = pts[(i + 1) % pts.length];
    a += p.x * q.y - q.x * p.y;
  }
  return a / 2;
}

export function isConvexCCW(pts: Vec2[]): boolean {
  const n = pts.length;
  if (n < 3 || signedArea(pts) <= 0) return false;
  for (let i = 0; i < n; i++) {
    const a = pts[i], b = pts[(i + 1) % n], c = pts[(i + 2) % n];
    const cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
    if (cross < -1e-9) return false;
  }
  return true;
}

export function rotatePoints(pts: Vec2[], angleRad: number, ox = 0, oy = 0): Vec2[] {
  const c = Math.cos(angleRad), s = Math.sin(angleRad);
  return pts.map(p => ({ x: ox + (p.x - ox) * c - (p.y - oy) * s, y: oy + (p.x - ox) * s + (p.y - oy) * c }));
}

export function translatePoints(pts: Vec2[], dx: number, dy: number): Vec2[] {
  return pts.map(p => ({ x: p.x + dx, y: p.y + dy }));
}

/**
 * Circle (cx,cy,r) vs convex polygon. Returns true and fills `out` when overlapping.
 * Handles the centre being inside the polygon (uses the least-penetrating face).
 */
export function circleVsConvex(poly: Poly, cx: number, cy: number, r: number, out: Contact): boolean {
  const { pts, enx, eny } = poly;
  const n = pts.length;
  let maxSep = -Infinity;
  let best = 0;
  for (let i = 0; i < n; i++) {
    const a = pts[i];
    const sep = (cx - a.x) * enx[i] + (cy - a.y) * eny[i];
    if (sep > maxSep) { maxSep = sep; best = i; }
  }
  if (maxSep > r) return false;
  if (maxSep <= 0) {
    out.nx = enx[best]; out.ny = eny[best];
    out.depth = r - maxSep;
    out.px = cx - out.nx * maxSep; out.py = cy - out.ny * maxSep;
    return true;
  }
  // Outside the polygon but within r: nearest boundary feature (edge interior or vertex).
  let bd2 = Infinity, bx = 0, by = 0;
  for (let i = 0; i < n; i++) {
    const a = pts[i], b = pts[(i + 1) % n];
    const ex = b.x - a.x, ey = b.y - a.y;
    const l2 = ex * ex + ey * ey;
    let t = l2 > 0 ? ((cx - a.x) * ex + (cy - a.y) * ey) / l2 : 0;
    t = t < 0 ? 0 : t > 1 ? 1 : t;
    const qx = a.x + ex * t, qy = a.y + ey * t;
    const d2 = (cx - qx) ** 2 + (cy - qy) ** 2;
    if (d2 < bd2) { bd2 = d2; bx = qx; by = qy; }
  }
  const dist = Math.sqrt(bd2);
  if (dist >= r) return false;
  if (dist < 1e-9) { out.nx = enx[best]; out.ny = eny[best]; }
  else { out.nx = (cx - bx) / dist; out.ny = (cy - by) / dist; }
  out.depth = r - dist;
  out.px = bx; out.py = by;
  return true;
}

/** Closest boundary point of a convex polygon to (cx,cy); normal points toward the query point. */
export function closestOnPoly(poly: Poly, cx: number, cy: number, out: { x: number; y: number; nx: number; ny: number; dist: number }): void {
  const { pts, enx, eny } = poly;
  const n = pts.length;
  let bd2 = Infinity, bx = 0, by = 0, bi = 0;
  for (let i = 0; i < n; i++) {
    const a = pts[i], b = pts[(i + 1) % n];
    const ex = b.x - a.x, ey = b.y - a.y;
    const l2 = ex * ex + ey * ey;
    let t = l2 > 0 ? ((cx - a.x) * ex + (cy - a.y) * ey) / l2 : 0;
    t = t < 0 ? 0 : t > 1 ? 1 : t;
    const qx = a.x + ex * t, qy = a.y + ey * t;
    const d2 = (cx - qx) ** 2 + (cy - qy) ** 2;
    if (d2 < bd2) { bd2 = d2; bx = qx; by = qy; bi = i; }
  }
  const dist = Math.sqrt(bd2);
  out.x = bx; out.y = by; out.dist = dist;
  if (dist < 1e-9) { out.nx = enx[bi]; out.ny = eny[bi]; }
  else { out.nx = (cx - bx) / dist; out.ny = (cy - by) / dist; }
}

export function pointInPoly(poly: Poly, x: number, y: number): boolean {
  const { pts, enx, eny } = poly;
  for (let i = 0; i < pts.length; i++) {
    if ((x - pts[i].x) * enx[i] + (y - pts[i].y) * eny[i] > 0) return false;
  }
  return true;
}

/** SAT penetration depth between two convex polygons (> 0 ⇒ overlapping, 0 ⇒ separated or touching). */
export function polysOverlapDepth(a: Poly, b: Poly): number {
  let minDepth = Infinity;
  for (const [p, o] of [[a, b], [b, a]] as const) {
    for (let i = 0; i < p.pts.length; i++) {
      const nx = p.enx[i], ny = p.eny[i];
      let minA = Infinity, maxA = -Infinity, minB = Infinity, maxB = -Infinity;
      for (const v of p.pts) { const d = v.x * nx + v.y * ny; minA = Math.min(minA, d); maxA = Math.max(maxA, d); }
      for (const v of o.pts) { const d = v.x * nx + v.y * ny; minB = Math.min(minB, d); maxB = Math.max(maxB, d); }
      const depth = Math.min(maxA, maxB) - Math.max(minA, minB);
      if (depth <= 0) return 0;
      minDepth = Math.min(minDepth, depth);
    }
  }
  return minDepth;
}

/**
 * Oriented box vs convex polygon (SAT). The box has centre (cx,cy), unit axis `a = (ax, ay)` with half-extent `hz`
 * along it and the perpendicular axis `b = (ay, −ax)` with half-extent `hx`.
 * On overlap fills `out` with the minimum-translation axis: `nx,ny` points from the polygon toward the box and
 * `depth` is the overlap along it.
 */
export function obbVsConvex(poly: Poly, cx: number, cy: number, ax: number, ay: number, hz: number, hx: number, out: Contact): boolean {
  const { pts, enx, eny } = poly;
  const n = pts.length;
  const bx = ay, by = -ax;
  let best = Infinity, bnx = 0, bny = 1;
  // box axes
  for (let k = 0; k < 2; k++) {
    const ux = k === 0 ? ax : bx, uy = k === 0 ? ay : by;
    let minP = Infinity, maxP = -Infinity;
    for (let i = 0; i < n; i++) { const d = pts[i].x * ux + pts[i].y * uy; if (d < minP) minP = d; if (d > maxP) maxP = d; }
    const c = cx * ux + cy * uy, r = k === 0 ? hz : hx;
    const ov = Math.min(c + r, maxP) - Math.max(c - r, minP);
    if (ov <= 0) return false;
    if (ov < best) { best = ov; const sg = c < (minP + maxP) / 2 ? -1 : 1; bnx = ux * sg; bny = uy * sg; }
  }
  // polygon edge normals
  for (let i = 0; i < n; i++) {
    const ux = enx[i], uy = eny[i];
    const pd = pts[i].x * ux + pts[i].y * uy; // every polygon point projects to ≤ pd (outward normal)
    let minP = pd;
    for (let k = 0; k < n; k++) { const d = pts[k].x * ux + pts[k].y * uy; if (d < minP) minP = d; }
    const c = cx * ux + cy * uy, r = hz * Math.abs(ax * ux + ay * uy) + hx * Math.abs(bx * ux + by * uy);
    const ov = Math.min(c + r, pd) - Math.max(c - r, minP);
    if (ov <= 0) return false;
    if (ov < best) { best = ov; const sg = c < (minP + pd) / 2 ? -1 : 1; bnx = ux * sg; bny = uy * sg; }
  }
  out.depth = best; out.nx = bnx; out.ny = bny; out.px = cx; out.py = cy;
  return true;
}
