/**
 * MapEntity — a resolved entity turned into runtime form: collider pieces, motion, rotation, gates, swept extent (SPEC §6).
 */
import type { BehaviorDef, BreakableBehavior, CollisionDef, Effect, OneWayDir, Vec2 } from './schema';
import type { SurfaceId } from '../sim/SurfacePhysics';
import { type Aabb, emptyAabb, placePolys, polysAabb, shapeToPolys, unionAabb } from './MapCollision';
import { type Curve, type GateBehavior, type Motion, type Rotation, compileMove, compileRotate, isGate } from './MapBehavior';
import type { ResolvedEntity } from './MapPrefab';
import { type MapIssue, mkIssue } from './MapIssue';

export type PieceKind = 'solid' | 'hazard';
export interface PieceSpec {
  /** Collider id: the entity id when the entity has exactly one piece, else `entityId#n`. */
  id: string;
  entityId: string;
  kind: PieceKind;
  surface: SurfaceId;
  material: string;
  safe: boolean;
  oneWay?: OneWayDir;
  /** Convex CCW polygon in the entity-local frame. */
  local: Vec2[];
}

export interface ZoneSpec { id: string; entityId: string; poly: Vec2[]; enter: Effect[] }

export interface EntityInstance {
  r: ResolvedEntity;
  pieces: PieceSpec[];
  zones: ZoneSpec[];
  motion: Motion | null;
  rotation: Rotation | null;
  gates: GateBehavior[];
  breakable: BreakableBehavior | null;
  /** Swept axis-aligned extent in world metres (collision + motion; visual-only entities use their position). */
  extent: Aabb;
  problems: MapIssue[];
}

export function buildInstance(r: ResolvedEntity, paths: ReadonlyMap<string, Curve>, path = ''): EntityInstance {
  const problems: MapIssue[] = [];
  let motion: Motion | null = null;
  let rotation: Rotation | null = null;
  const gates: GateBehavior[] = [];
  let breakable: BreakableBehavior | null = null;
  const extras: BehaviorDef[] = [];
  for (const b of r.behaviors) {
    if (b.type === 'move') {
      const m = compileMove(b, paths);
      if (!m) problems.push(mkIssue('ERROR', 'BEHAVIOR_INVALID', `${path}/behavior`, `entity "${r.id}": move behaviour cannot be compiled`, { entityId: r.id }));
      else if (motion) problems.push(mkIssue('ERROR', 'BEHAVIOR_CONFLICT', `${path}/behavior`, `entity "${r.id}": more than one motion behaviour`, { entityId: r.id }));
      else motion = m;
    } else if (b.type === 'rotate') {
      if (rotation) problems.push(mkIssue('ERROR', 'BEHAVIOR_CONFLICT', `${path}/behavior`, `entity "${r.id}": more than one rotation behaviour`, { entityId: r.id }));
      else rotation = compileRotate(b);
    } else if (isGate(b)) gates.push(b);
    else if (b.type === 'breakable') breakable = b;
    else extras.push(b);
  }
  if (gates.length + (breakable ? 1 : 0) > 1) problems.push(mkIssue('ERROR', 'BEHAVIOR_CONFLICT', `${path}/behavior`, `entity "${r.id}": more than one gating behaviour (toggle/timed/conditional/breakable)`, { entityId: r.id }));

  const rawPieces: { col: CollisionDef; local: Vec2[] }[] = [];
  const zones: ZoneSpec[] = [];
  const cols = r.enabled ? r.collisions : [];
  const triggerEffects: Effect[] = [];
  for (const b of extras) if (b.type === 'boostZone') triggerEffects.push({ op: 'emit', event: 'boost_zone', data: { kind: b.kind, once: !!b.once } });
  cols.forEach((col, ci) => {
    if (col.enabled === false) return;
    const sp = shapeToPolys(col.shape);
    for (const pr of sp.problems) problems.push(mkIssue('ERROR', pr.code, `${path}/collision${cols.length > 1 ? `/${ci}` : ''}/shape`, `entity "${r.id}": ${pr.message}`, { entityId: r.id }));
    if (sp.problems.length) return;
    sp.polys.forEach(local => {
      if (col.trigger) {
        const poly = placePolys([local], r.x, r.y, r.rotation, r.sx, r.sy)[0];
        zones.push({ id: `${r.id}`, entityId: r.id, poly, enter: triggerEffects.length ? triggerEffects : [{ op: 'emit', event: `trigger:${r.id}` }] });
      } else rawPieces.push({ col, local });
    });
  });
  const movingish = !!motion || !!rotation || gates.length > 0 || !!breakable;
  const pieces: PieceSpec[] = rawPieces.map((p, i) => ({
    id: rawPieces.length === 1 ? r.id : `${r.id}#${i}`,
    entityId: r.id,
    kind: p.col.hazard ? 'hazard' : 'solid',
    surface: p.col.hazard ? 'hazard' : (p.col.surface ?? 'normal'),
    material: p.col.material ?? (p.col.hazard ? 'crystal' : 'stone'),
    safe: p.col.safe ?? (!p.col.hazard && !movingish && r.type !== 'wall' && r.type !== 'ceiling'),
    oneWay: p.col.oneWay,
    local: p.local,
  }));

  // swept extent (world metres)
  let extent = emptyAabb();
  const staticPolys = pieces.map(p => placePolys([p.local], r.x, r.y, r.rotation, r.sx, r.sy)[0]).concat(zones.map(z => z.poly));
  if (staticPolys.length) extent = polysAabb(staticPolys);
  else extent = { minX: r.x, maxX: r.x, minY: r.y, maxY: r.y };
  if (rotation && rotation.axis === 'z' && staticPolys.length) {
    const c = placePolys([[rotation.pivot]], r.x, r.y, r.rotation, r.sx, r.sy)[0][0];
    let rad = 0;
    for (const poly of staticPolys) for (const v of poly) rad = Math.max(rad, Math.hypot(v.x - c.x, v.y - c.y));
    extent = unionAabb(extent, { minX: c.x - rad, maxX: c.x + rad, minY: c.y - rad, maxY: c.y + rad });
  }
  if (motion) extent = { minX: extent.minX + motion.range.minX, maxX: extent.maxX + motion.range.maxX, minY: extent.minY + motion.range.minY, maxY: extent.maxY + motion.range.maxY };
  // if the entity moves, the rotated extent has to move too (conservative union)
  return { r, pieces, zones, motion, rotation, gates, breakable, extent, problems };
}
