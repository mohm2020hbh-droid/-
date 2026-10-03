import type { SurfaceId } from '../sim/SurfacePhysics';
import type { Vec2 } from '../sim/math';
import { DEG } from '../sim/math';
import { isConvexCCW, rotatePoints, translatePoints } from '../sim/geometry';

/**
 * LevelData — every level is pure data (Phase 7). Coordinates: metres, +x right, +y up.
 * Field names follow the prompt: levelId, worldId, theme, startPosition, goalPosition, platforms, obstacles,
 * hazards, movingObjects, specialSurfaces, difficulty, progress.
 */
export type PlatformKind = 'rock' | 'wood' | 'ice' | 'bounce' | 'special' | 'ruin' | 'lava' | 'goal';
export type MaterialId = 'grass' | 'wood' | 'stone' | 'ice' | 'goo' | 'metal' | 'sand' | 'lava';

export interface MoveDef {
  /** Half-range vector in metres (offset = (dx,dy)·sin θ). */
  dx: number;
  dy: number;
  /** Seconds per full cycle. */
  period: number;
  phase?: number;
}

export interface PlatformDef {
  id: string;
  kind: PlatformKind;
  /** Centre of the TOP surface. */
  x: number;
  y: number;
  /** Top width / thickness. */
  w: number;
  h: number;
  /** Counter-clockwise rotation about (x,y); positive = rising to the right. */
  angleDeg?: number;
  /** Bottom width as a fraction of top width (collision silhouette is a trapezoid). */
  taper?: number;
  surface?: SurfaceId;
  material?: MaterialId;
  move?: MoveDef;
  /** Can be used as a respawn point (default: true unless it moves). */
  safe?: boolean;
  /** Direction for 'boost' pads (+1 right, −1 left). */
  dir?: 1 | -1;
  /** Visual seed + hints (do not affect physics). */
  seed?: number;
  depth?: number;
  decor?: 'none' | 'flowers' | 'bushes' | 'vines' | 'mushrooms' | 'fence' | 'sign';
}

export interface ObstacleDef {
  id: string;
  /** Convex polygon, CCW. */
  pts: Vec2[];
  kind?: 'cliff' | 'wall' | 'ceiling';
  material?: MaterialId;
  seed?: number;
  depth?: number;
}

export interface HazardDef {
  id: string;
  kind: 'spikes' | 'crystals' | 'thorns';
  /** Base-centre of the hazard. */
  x: number;
  y: number;
  w: number;
  h: number;
  angleDeg?: number;
}

export interface GoalDef { x: number; y: number; w: number; h: number }

export interface LandmarkDef {
  id: string;
  type: 'castle' | 'arch_bridge' | 'waterfall' | 'tower' | 'big_tree' | 'windmill' | 'wood_bridge' | 'floating_island';
  x: number;
  y: number;
  z: number;
  scale?: number;
  flip?: boolean;
  seed?: number;
}

export interface HintDef { id: string; x: number; y: number; textKey: string; radius: number }

export interface LevelData {
  levelId: string;
  worldId: string;
  name: string;
  theme: string;
  startPosition: Vec2;
  goalPosition: Vec2;
  goal: GoalDef;
  platforms: PlatformDef[];
  obstacles: ObstacleDef[];
  hazards: HazardDef[];
  movingObjects: PlatformDef[];
  specialSurfaces: PlatformDef[];
  difficulty: number;
  /** Progress track: polyline from start to goal; progress % = nearest-point fraction (inspired by map3Progress.pak). */
  progress: { path: Vec2[] };
  bounds: { minX: number; maxX: number; minY: number; maxY: number };
  killY: number;
  /** Target time for ★★★ (seconds) — tuned with the replay rhythm (XLSX V-025). */
  parTimeSec: number;
  landmarks: LandmarkDef[];
  hints: HintDef[];
  /** Designer's intended route (platform ids). Used by tests/solvability and by the Lab. */
  route?: string[];
}

export function defaultSurface(kind: PlatformKind): SurfaceId {
  switch (kind) {
    case 'bounce': return 'bounce';
    case 'ice': return 'slippery';
    default: return 'normal';
  }
}

export function defaultMaterial(kind: PlatformKind): MaterialId {
  switch (kind) {
    case 'wood': return 'wood';
    case 'ice': return 'ice';
    case 'bounce': return 'metal';
    case 'ruin': return 'sand';
    case 'lava': return 'stone';
    case 'special': return 'goo';
    default: return 'grass';
  }
}

/** Trapezoidal collision silhouette for a platform (CCW). */
export function platformPoints(p: PlatformDef): Vec2[] {
  const taper = p.taper ?? 0.72;
  const hw = p.w / 2, bw = (p.w * taper) / 2;
  let pts: Vec2[] = [
    { x: -hw, y: 0 }, { x: -bw, y: -p.h }, { x: bw, y: -p.h }, { x: hw, y: 0 },
  ];
  if (p.angleDeg) pts = rotatePoints(pts, p.angleDeg * DEG, 0, 0);
  return translatePoints(pts, p.x, p.y);
}

export function rectPoints(cx: number, cy: number, w: number, h: number, angleDeg = 0): Vec2[] {
  let pts: Vec2[] = [
    { x: -w / 2, y: -h / 2 }, { x: w / 2, y: -h / 2 }, { x: w / 2, y: h / 2 }, { x: -w / 2, y: h / 2 },
  ];
  if (angleDeg) pts = rotatePoints(pts, angleDeg * DEG);
  return translatePoints(pts, cx, cy);
}

export function allPlatforms(l: LevelData): PlatformDef[] {
  return [...l.platforms, ...l.movingObjects, ...l.specialSurfaces];
}

/** Static validation (structure only; reachability is proven by tests/solvability). */
export function validateLevel(l: LevelData): string[] {
  const errs: string[] = [];
  const ids = new Set<string>();
  const dup = (id: string) => { if (ids.has(id)) errs.push(`duplicate id ${id}`); ids.add(id); };
  for (const p of allPlatforms(l)) {
    dup(p.id);
    if (p.w <= 0 || p.h <= 0) errs.push(`${p.id}: non-positive size`);
    if (!isConvexCCW(platformPoints(p))) errs.push(`${p.id}: polygon not convex CCW`);
  }
  for (const o of l.obstacles) { dup(o.id); if (!isConvexCCW(o.pts)) errs.push(`${o.id}: polygon not convex CCW`); }
  for (const h of l.hazards) dup(h.id);
  if (l.progress.path.length < 2) errs.push('progress path needs ≥ 2 points');
  if (l.killY >= l.bounds.minY) errs.push('killY must be below bounds.minY');
  return errs;
}

/** Progress fraction 0..1 of the nearest point on the track polyline. */
export function progressFraction(path: Vec2[], x: number, y: number): number {
  let total = 0;
  const seg: number[] = [];
  for (let i = 0; i < path.length - 1; i++) { const l = Math.hypot(path[i + 1].x - path[i].x, path[i + 1].y - path[i].y); seg.push(l); total += l; }
  if (total <= 0) return 0;
  let bestD = Infinity, bestAt = 0, acc = 0;
  for (let i = 0; i < path.length - 1; i++) {
    const a = path[i], b = path[i + 1];
    const ex = b.x - a.x, ey = b.y - a.y;
    const l2 = ex * ex + ey * ey;
    let t = l2 > 0 ? ((x - a.x) * ex + (y - a.y) * ey) / l2 : 0;
    t = Math.max(0, Math.min(1, t));
    const d = Math.hypot(x - (a.x + ex * t), y - (a.y + ey * t));
    if (d < bestD) { bestD = d; bestAt = acc + seg[i] * t; }
    acc += seg[i];
  }
  return bestAt / total;
}
