import type { LevelData, PlatformDef } from '../data/LevelData';
import { defaultMaterial, defaultSurface, platformPoints } from '../data/LevelData';
import type { SurfaceId } from './SurfacePhysics';
import { TAU, TICK_RATE, type Vec2 } from './math';
import { type Poly, closestOnPoly, makePoly } from './geometry';

export type ColliderKind = 'solid' | 'hazard' | 'goal';

export interface Collider {
  index: number;
  id: string;
  kind: ColliderKind;
  surface: SurfaceId;
  material: string;
  poly: Poly;
  /** Broad-phase AABB, expanded by the motion range. */
  minX: number; minY: number; maxX: number; maxY: number;
  move?: { dx: number; dy: number; periodTicks: number; phase: number };
  safe: boolean;
  /** Push direction for boost pads. */
  dir: number;
}

export interface Offset { x: number; y: number; vx: number; vy: number }

export class PhysicsWorld {
  readonly colliders: Collider[] = [];
  readonly solids: Collider[] = [];
  readonly triggers: Collider[] = [];
  readonly level: LevelData;

  constructor(level: LevelData) {
    this.level = level;
    const add = (id: string, pts: Vec2[], kind: ColliderKind, surface: SurfaceId, material: string, move: PlatformDef['move'], safe: boolean, dir: number) => {
      const poly = makePoly(pts);
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      for (const p of pts) { minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); }
      const c: Collider = {
        index: this.colliders.length, id, kind, surface, material, poly, minX, minY, maxX, maxY, safe, dir,
        move: move ? { dx: move.dx, dy: move.dy, periodTicks: move.period * TICK_RATE, phase: move.phase ?? 0 } : undefined,
      };
      if (move) { c.minX -= Math.abs(move.dx); c.maxX += Math.abs(move.dx); c.minY -= Math.abs(move.dy); c.maxY += Math.abs(move.dy); }
      this.colliders.push(c);
      (kind === 'solid' ? this.solids : this.triggers).push(c);
    };
    const addPlatform = (p: PlatformDef) => {
      add(p.id, platformPoints(p), 'solid', p.surface ?? defaultSurface(p.kind), p.material ?? defaultMaterial(p.kind), p.move, p.safe ?? !p.move, p.dir ?? 1);
    };
    level.platforms.forEach(addPlatform);
    level.movingObjects.forEach(addPlatform);
    level.specialSurfaces.forEach(addPlatform);
    for (const o of level.obstacles) add(o.id, o.pts, 'solid', 'normal', o.material ?? 'stone', undefined, false, 1);
    for (const h of level.hazards) {
      const hw = h.w / 2;
      add(h.id, [{ x: h.x - hw, y: h.y }, { x: h.x + hw, y: h.y }, { x: h.x + hw * 0.8, y: h.y + h.h * 0.85 }, { x: h.x - hw * 0.8, y: h.y + h.h * 0.85 }], 'hazard', 'hazard', 'crystal', undefined, false, 1);
    }
    const g = level.goal;
    add('goal', [{ x: g.x - g.w / 2, y: g.y }, { x: g.x + g.w / 2, y: g.y }, { x: g.x + g.w / 2, y: g.y + g.h }, { x: g.x - g.w / 2, y: g.y + g.h }], 'goal', 'goal', 'goal', undefined, false, 1);
  }

  /** Analytic platform offset + velocity at a tick (pure — safe for prediction). */
  offsetAt(c: Collider, tick: number, out: Offset): Offset {
    const m = c.move;
    if (!m) { out.x = 0; out.y = 0; out.vx = 0; out.vy = 0; return out; }
    const th = TAU * (tick / m.periodTicks + m.phase);
    const s = Math.sin(th), co = Math.cos(th);
    const w = (TAU * TICK_RATE) / m.periodTicks; // rad/s
    out.x = m.dx * s; out.y = m.dy * s; out.vx = m.dx * co * w; out.vy = m.dy * co * w;
    return out;
  }

  /** Solid collider nearest to (x,y) at tick 0 within `maxDist`, for placing the start position. */
  findGround(x: number, y: number, maxDist = 0.3): { collider: Collider; x: number; y: number; nx: number; ny: number } | null {
    const o = { x: 0, y: 0, nx: 0, ny: 1, dist: 0 };
    let best: { collider: Collider; x: number; y: number; nx: number; ny: number } | null = null;
    let bd = maxDist;
    for (const c of this.solids) {
      closestOnPoly(c.poly, x, y, o);
      if (o.dist < bd) { bd = o.dist; best = { collider: c, x: o.x, y: o.y, nx: o.nx, ny: o.ny }; }
    }
    return best;
  }
}
