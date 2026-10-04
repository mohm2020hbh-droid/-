/**
 * MapWorld — the physics world of a map (SPEC §0.2, §10.2).
 *
 * `MapWorld extends PhysicsWorld` and is the *only* bridge between the map layer and the physics core:
 *   - it owns every collider (built from convex polygons produced by MapCollision), in a **stable slot table**;
 *   - it can activate/deactivate a collider (membership of `solids` / `triggers`) without changing its index;
 *   - it evaluates animated colliders analytically (`offsetAt` override; the core derives platform carry from it);
 *   - it can re-pose a rotating collider (replace its polygon) before a tick.
 * The core reads `solids`, `triggers`, `colliders[i]`, `offsetAtQ` and `level` exactly as it does for a plain PhysicsWorld.
 */
import type { LevelData } from '../data/LevelData';
import { type Collider, type ColliderKind, type Offset, PhysicsWorld, WORLD_Q_PER_M } from '../sim/PhysicsWorld';
import type { SurfaceId } from '../sim/SurfacePhysics';
import { makePoly } from '../sim/geometry';
import { DEG } from '../sim/math';
import { rotatePoints } from '../sim/geometry';
import type { Vec2 } from './schema';
import type { Motion, Offset3 } from './MapBehavior';
import type { Aabb } from './MapCollision';

export interface ColliderSpec {
  id: string;
  kind: ColliderKind;
  surface: SurfaceId;
  material: string;
  safe: boolean;
  /** Convex CCW polygon, world metres, at tick 0 and rotation angle 0 of the behaviour. */
  pts: Vec2[];
  motion?: Motion | null;
  /** Rotating collider: the polygon is rotated about `pivot` (world metres) by an angle set through `setAngle`. */
  rot?: { pivot: Vec2 } | null;
  /** Swept extent (metres) used for the broad phase; defaults to the polygon extent expanded by the motion range. */
  sweep?: Aabb;
}

const TOMB_ID = '__unloaded__';

export class MapWorld extends PhysicsWorld {
  private readonly motionOf: (Motion | null)[] = [];
  private readonly basePts: (Vec2[] | null)[] = [];
  private readonly pivotOf: (Vec2 | null)[] = [];
  private readonly angleOf: number[] = [];
  private readonly activeMask: number[] = [];
  private readonly tomb: Collider;
  private readonly idIndex = new Map<string, number>();
  private dirty = false;
  private readonly tmpOff: Offset3 = { x: 0, y: 0, z: 0, vx: 0, vy: 0 };
  /** Number of `flush()` calls that actually rebuilt the active lists (observability for tests). */
  rebuilds = 0;

  /** `shell` supplies startPosition / goal / killY / bounds (geometry lists are ignored — they stay empty). */
  constructor(shell: LevelData, qPerMetre = WORLD_Q_PER_M) {
    super({ ...shell, platforms: [], obstacles: [], hazards: [], movingObjects: [], specialSurfaces: [] }, qPerMetre);
    // the base constructor added the shell goal; the map layer owns the whole collider table
    this.colliders.length = 0; this.solids.length = 0; this.triggers.length = 0;
    this.tomb = this.makeCollider(-1, { id: TOMB_ID, kind: 'solid', surface: 'normal', material: 'stone', safe: false, pts: [{ x: 0, y: 0 }, { x: 1e-3, y: 0 }, { x: 0, y: 1e-3 }] });
  }

  get slotCount(): number { return this.colliders.length; }
  indexOfId(id: string): number { return this.idIndex.get(id) ?? -1; }
  isTomb(c: Collider | undefined): boolean { return c === this.tomb; }
  isActive(index: number): boolean { return this.activeMask[index] === 1; }
  activeCount(): number { let n = 0; for (const c of this.colliders) if (c !== this.tomb && this.activeMask[c.index] === 1) n++; return n; }

  /** Append `n` empty slots (tombstones) and return their indices. */
  reserve(n: number): number[] {
    const out: number[] = [];
    for (let i = 0; i < n; i++) {
      const idx = this.colliders.length;
      this.colliders.push(this.tomb);
      this.motionOf[idx] = null; this.basePts[idx] = null; this.pivotOf[idx] = null; this.angleOf[idx] = 0; this.activeMask[idx] = 0;
      out.push(idx);
    }
    return out;
  }

  private makeCollider(index: number, s: ColliderSpec): Collider {
    const k = this.qPerMetre;
    const poly = makePoly(s.pts);
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const p of s.pts) { minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); }
    let sweep: Aabb = { minX, maxX, minY, maxY };
    if (s.motion) sweep = { minX: minX + s.motion.range.minX, maxX: maxX + s.motion.range.maxX, minY: minY + s.motion.range.minY, maxY: maxY + s.motion.range.maxY };
    if (s.rot) {
      let rad = 0;
      for (const p of s.pts) rad = Math.max(rad, Math.hypot(p.x - s.rot.pivot.x, p.y - s.rot.pivot.y));
      sweep = { minX: Math.min(sweep.minX, s.rot.pivot.x - rad), maxX: Math.max(sweep.maxX, s.rot.pivot.x + rad), minY: Math.min(sweep.minY, s.rot.pivot.y - rad), maxY: Math.max(sweep.maxY, s.rot.pivot.y + rad) };
      if (s.motion) sweep = { minX: sweep.minX + s.motion.range.minX, maxX: sweep.maxX + s.motion.range.maxX, minY: sweep.minY + s.motion.range.minY, maxY: sweep.maxY + s.motion.range.maxY };
    }
    if (s.sweep) sweep = s.sweep;
    const c: Collider = {
      index, id: s.id, kind: s.kind, surface: s.surface, material: s.material, poly, minX: sweep.minX, minY: sweep.minY, maxX: sweep.maxX, maxY: sweep.maxY,
      safe: s.safe, dir: 1,
      qpoly: makePoly(s.pts.map(p => ({ x: p.x * k, y: p.y * k }))),
      qMinX: sweep.minX * k, qMinY: sweep.minY * k, qMaxX: sweep.maxX * k, qMaxY: sweep.maxY * k,
    };
    if (s.motion) {
      // marker for the core's platform-carry test; the real motion is `motionOf` (see offsetAt)
      c.move = { dx: Math.max(Math.abs(s.motion.range.minX), Math.abs(s.motion.range.maxX)), dy: Math.max(Math.abs(s.motion.range.minY), Math.abs(s.motion.range.maxY)), periodTicks: 1, phase: 0 };
    }
    return c;
  }

  /** Put a collider into a reserved slot (inactive until `setActive(index, true)`). */
  install(index: number, s: ColliderSpec, active = true): Collider {
    const c = this.makeCollider(index, s);
    this.colliders[index] = c;
    this.motionOf[index] = s.motion ?? null;
    this.basePts[index] = s.rot ? s.pts : null;
    this.pivotOf[index] = s.rot ? s.rot.pivot : null;
    this.angleOf[index] = 0;
    this.activeMask[index] = active ? 1 : 0;
    this.idIndex.set(s.id, index);
    this.dirty = true;
    return c;
  }

  /** Free a slot (the collider object is dropped; the slot index stays valid and points at the tombstone). */
  uninstall(index: number): void {
    const c = this.colliders[index];
    if (c && c !== this.tomb) this.idIndex.delete(c.id);
    this.colliders[index] = this.tomb;
    this.motionOf[index] = null; this.basePts[index] = null; this.pivotOf[index] = null; this.activeMask[index] = 0;
    this.dirty = true;
  }

  setActive(index: number, active: boolean): void {
    const v = active ? 1 : 0;
    if (this.activeMask[index] !== v) { this.activeMask[index] = v; this.dirty = true; }
  }

  /** Re-pose a rotating collider (degrees about its pivot). No-op if the angle did not change. */
  setAngle(index: number, angleDeg: number): void {
    const base = this.basePts[index], pv = this.pivotOf[index];
    if (!base || !pv || this.angleOf[index] === angleDeg) return;
    this.angleOf[index] = angleDeg;
    const c = this.colliders[index];
    const pts = angleDeg === 0 ? base : rotatePoints(base, angleDeg * DEG, pv.x, pv.y);
    const k = this.qPerMetre;
    c.poly = makePoly(pts);
    c.qpoly = makePoly(pts.map(p => ({ x: p.x * k, y: p.y * k })));
  }
  angle(index: number): number { return this.angleOf[index] ?? 0; }

  /** Rebuild `solids` / `triggers` from the activity mask (index order ⇒ deterministic iteration). Only when something changed. */
  flush(): boolean {
    if (!this.dirty) return false;
    this.dirty = false; this.rebuilds++;
    this.solids.length = 0; this.triggers.length = 0;
    for (let i = 0; i < this.colliders.length; i++) {
      const c = this.colliders[i];
      if (c === this.tomb || this.activeMask[i] !== 1) continue;
      (c.kind === 'solid' ? this.solids : this.triggers).push(c);
    }
    // same trigger order as PhysicsWorld: hazards first, goals last
    this.triggers.sort((a, b) => (a.kind === 'goal' ? 1 : 0) - (b.kind === 'goal' ? 1 : 0) || a.index - b.index);
    return true;
  }

  override offsetAt(c: Collider, tick: number, out: Offset): Offset {
    const m = this.motionOf[c.index];
    if (!m || this.colliders[c.index] !== c) return super.offsetAt(c, tick, out);
    const t = this.tmpOff;
    m.at(tick, t);
    out.x = t.x; out.y = t.y; out.vx = t.vx; out.vy = t.vy;
    return out;
  }
}
