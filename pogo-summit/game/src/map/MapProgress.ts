/**
 * MapProgress — progress along the *actual route* (SPEC §11).
 *
 * Percent 0…100 comes from anchors on a main route (first point 0, last 100, explicit `percent` on any point; the rest
 * is interpolated by path length). Branch / optional / secret routes attach to the main route and map onto the percent
 * interval between their `from` and `to` points. Projection is **windowed**: only segments whose percent interval is
 * close to the current progress are candidates, so a route that doubles back (or branches) never snaps to a far segment.
 */
import type { ProgressDef, RouteDef, Vec2 } from './schema';

export interface Seg { route: string; ax: number; ay: number; bx: number; by: number; len: number; p0: number; p1: number; d0: number }
export interface ProgressSample { percent: number; max: number; route: string; distanceAlong: number; lateral: number }

export const DEFAULT_WINDOW = { backPercent: 15, forwardPercent: 30, relocateDistance: 12 };

/** Percent for every point of a route given the anchors (explicit `percent` values and the two end values). */
export function routePercents(points: RouteDef['points'], startPercent: number, endPercent: number): number[] {
  const n = points.length;
  const cum = [0];
  for (let i = 1; i < n; i++) cum.push(cum[i - 1] + Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y));
  const pct: (number | undefined)[] = points.map(p => p.percent);
  if (pct[0] === undefined) pct[0] = startPercent;
  if (pct[n - 1] === undefined) pct[n - 1] = endPercent;
  const out: number[] = new Array(n).fill(0);
  let i = 0;
  while (i < n) {
    if (pct[i] === undefined) { i++; continue; }
    let j = i + 1;
    while (j < n && pct[j] === undefined) j++;
    if (j >= n) { out[i] = pct[i]!; break; }
    const span = cum[j] - cum[i];
    for (let k = i; k <= j; k++) out[k] = pct[i]! + (pct[j]! - pct[i]!) * (span > 0 ? (cum[k] - cum[i]) / span : 0);
    i = j;
  }
  return out;
}

export interface ProgressModel {
  segs: Seg[];
  /** Length (m) of the main route. */
  length: number;
  window: { backPercent: number; forwardPercent: number; relocateDistance: number };
  /** Position on the main route at a percent (for editors / ghosts). */
  pointAt(percent: number, out?: Vec2): Vec2;
  problems: string[];
}

export function buildProgress(def: ProgressDef): ProgressModel {
  const problems: string[] = [];
  const segs: Seg[] = [];
  const percents = new Map<string, number[]>();
  const routes = new Map(def.routes.map(r => [r.id, r]));
  const mains = def.routes.filter(r => r.kind === 'main');
  if (mains.length !== 1) problems.push(mains.length === 0 ? 'no main route' : 'more than one main route');
  const order: RouteDef[] = [...mains, ...def.routes.filter(r => r.kind !== 'main')];
  let length = 0;
  for (const r of order) {
    if (r.points.length < 2) { problems.push(`route "${r.id}" needs at least 2 points`); continue; }
    let from = 0, to = 100;
    if (r.kind !== 'main') {
      const fr = r.from ? percents.get(r.from.route)?.[r.from.point] : undefined;
      const tr = r.to ? percents.get(r.to.route)?.[r.to.point] : undefined;
      if (fr === undefined || tr === undefined) { problems.push(`route "${r.id}" must attach with valid from/to points of an earlier route`); continue; }
      if (tr <= fr) { problems.push(`route "${r.id}" must end at a higher percent than it starts (${fr} → ${tr})`); continue; }
      from = fr; to = tr;
    }
    const pcts = routePercents(r.points, from, to);
    for (let i = 1; i < pcts.length; i++) if (pcts[i] < pcts[i - 1] - 1e-9) { problems.push(`route "${r.id}" has non-monotonic percent anchors at point ${i}`); break; }
    percents.set(r.id, pcts);
    let d = 0;
    for (let i = 0; i + 1 < r.points.length; i++) {
      const a = r.points[i], b = r.points[i + 1];
      const len = Math.hypot(b.x - a.x, b.y - a.y);
      if (len > 0) segs.push({ route: r.id, ax: a.x, ay: a.y, bx: b.x, by: b.y, len, p0: pcts[i], p1: pcts[i + 1], d0: d });
      d += len;
    }
    if (r.kind === 'main') length = d;
  }
  void routes;
  if (mains.length === 1 && length <= 0) problems.push('main route has zero length');
  const main = segs.filter(s => s.route === mains[0]?.id);
  return {
    segs, length, problems,
    window: { ...DEFAULT_WINDOW, ...(def.window ?? {}) },
    pointAt(percent, out = { x: 0, y: 0 }) {
      for (const s of main) {
        if (percent >= Math.min(s.p0, s.p1) - 1e-9 && percent <= Math.max(s.p0, s.p1) + 1e-9) {
          const t = s.p1 === s.p0 ? 0 : (percent - s.p0) / (s.p1 - s.p0);
          out.x = s.ax + (s.bx - s.ax) * t; out.y = s.ay + (s.by - s.ay) * t; return out;
        }
      }
      const last = main[main.length - 1] ?? { bx: 0, by: 0 };
      out.x = last.bx; out.y = last.by; return out;
    },
  };
}

export class ProgressTracker {
  current = 0;
  max = 0;
  route = '';
  distanceAlong = 0;
  lateral = 0;
  private started = false;
  readonly sample: ProgressSample = { percent: 0, max: 0, route: '', distanceAlong: 0, lateral: 0 };

  constructor(readonly model: ProgressModel) {}

  reset(): void { this.current = 0; this.max = 0; this.started = false; this.route = ''; this.distanceAlong = 0; this.lateral = 0; }

  /** Raise the best progress to at least `percent` (checkpoint pins). */
  pin(percent: number): void { if (percent > this.max) this.max = percent; }

  private scan(x: number, y: number, lo: number, hi: number, useWindow: boolean): { seg: Seg; t: number; dist: number; pct: number } | null {
    let best: { seg: Seg; t: number; dist: number; pct: number } | null = null;
    for (const s of this.model.segs) {
      if (useWindow && Math.max(s.p0, s.p1) < lo - 1e-9) continue;
      if (useWindow && Math.min(s.p0, s.p1) > hi + 1e-9) continue;
      const ex = s.bx - s.ax, ey = s.by - s.ay;
      let t = ((x - s.ax) * ex + (y - s.ay) * ey) / (s.len * s.len);
      t = t < 0 ? 0 : t > 1 ? 1 : t;
      const dist = Math.hypot(x - (s.ax + ex * t), y - (s.ay + ey * t));
      const pct = s.p0 + (s.p1 - s.p0) * t;
      // smaller distance wins; on (near) ties prefer the larger percent (forward)
      if (!best || dist < best.dist - 1e-9 || (Math.abs(dist - best.dist) <= 1e-9 && pct > best.pct)) best = { seg: s, t, dist, pct };
    }
    return best;
  }

  /** Update from a player position. Returns the reused sample object. */
  update(x: number, y: number): ProgressSample {
    const w = this.model.window;
    let hit = this.started ? this.scan(x, y, this.current - w.backPercent, this.current + w.forwardPercent, true) : null;
    if (!hit || hit.dist > w.relocateDistance) {
      const global = this.scan(x, y, 0, 100, false);
      if (global && (!hit || global.dist < hit.dist)) hit = global;
    }
    if (hit) {
      this.started = true;
      this.current = hit.pct; this.route = hit.seg.route; this.distanceAlong = hit.seg.d0 + hit.seg.len * hit.t; this.lateral = hit.dist;
      if (hit.pct > this.max) this.max = hit.pct;
    }
    const s = this.sample;
    s.percent = this.current; s.max = this.max; s.route = this.route; s.distanceAlong = this.distanceAlong; s.lateral = this.lateral;
    return s;
  }
}
