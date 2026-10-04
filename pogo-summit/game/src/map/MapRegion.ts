/** Region shapes: containment and extents (SPEC §8.8). */
import type { RegionShape, Vec2 } from './schema';
import type { Aabb } from './MapCollision';

export function regionContains(s: RegionShape, x: number, y: number): boolean {
  switch (s.kind) {
    case 'box': return x >= s.x - s.w / 2 && x <= s.x + s.w / 2 && y >= s.y - s.h / 2 && y <= s.y + s.h / 2;
    case 'circle': return (x - s.x) * (x - s.x) + (y - s.y) * (y - s.y) <= s.r * s.r;
    case 'polygon': return pointInPolygon(s.points, x, y);
  }
}

/** Even-odd rule (works for concave polygons, either winding). */
export function pointInPolygon(pts: Vec2[], x: number, y: number): boolean {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const a = pts[i], b = pts[j];
    if ((a.y > y) !== (b.y > y) && x < ((b.x - a.x) * (y - a.y)) / (b.y - a.y) + a.x) inside = !inside;
  }
  return inside;
}

export function regionAabb(s: RegionShape): Aabb {
  switch (s.kind) {
    case 'box': return { minX: s.x - s.w / 2, maxX: s.x + s.w / 2, minY: s.y - s.h / 2, maxY: s.y + s.h / 2 };
    case 'circle': return { minX: s.x - s.r, maxX: s.x + s.r, minY: s.y - s.r, maxY: s.y + s.r };
    case 'polygon': {
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      for (const p of s.points) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
      return { minX, maxX, minY, maxY };
    }
  }
}

export const regionCentre = (s: RegionShape): Vec2 => { const b = regionAabb(s); return { x: (b.minX + b.maxX) / 2, y: (b.minY + b.maxY) / 2 }; };
