/**
 * MapVisual — resolves what an entity LOOKS like (Visual V2 · phases 1, 2, 5, 6). Pure and shared by the renderer, the
 * validator and the stats tool, so budgets always describe what the renderer will build.
 *
 *   gameplay geometry ≠ collision geometry ≠ visual geometry ≠ decoration ≠ background
 *
 * The collision shape never becomes the final look. An entity with a `visual.mesh` is drawn from that mesh; an entity
 * with legacy `visual.style` is dressed by the matching builtin generator; an entity with collision but NO visual gets a
 * *derived* stylised slab sized from its collision (flagged `derived`, validator INFO) — never the raw collision polygon.
 */
import type { Json, MapEntity, RenderLayer, ScatterDef, ThemeDef, VisualDef } from './schema';
import type { Vec2 } from './schema';
import type { EntityInstance } from './MapEntity';
import { BUILTIN_MESHES, ROLE_SLOT, STYLE_TO_MESH, type MeshParams, isBuiltinMesh, meshParams } from './MapAssets';
import { hashString } from './MapScatter';
import { resolveMaterialRef, slotId } from './MapTheme';

export type RenderMode = 'instanced' | 'merged' | 'single';

export interface ResolvedPart { role: string; material: string }
export interface ResolvedVisual {
  entityId: string;
  /** `builtin:*` generator or a mesh asset id. */
  mesh: string;
  builtin: boolean;
  params: MeshParams;
  parts: ResolvedPart[];
  layer: RenderLayer;
  /** World z of the visual (metres; negative = farther from the camera). */
  z: number;
  /** Visual origin offset relative to the entity position (e.g. centre-anchored collision boxes). */
  offset: Vec2;
  mode: RenderMode;
  /** View-depth (metres) at which LOD1 / LOD2 start. */
  lodDepth: [number, number];
  /** Explicit alternative meshes for LOD1 / LOD2 (builtin ids or asset ids). */
  lodMesh: [string | undefined, string | undefined];
  cast: boolean;
  receive: boolean;
  cullDepth: number;
  scatter?: ScatterDef;
  tint?: string;
  emissive: number;
  /** The visual was derived from the collision because the entity declares none. */
  derived: boolean;
  /** Triangles of one instance at LOD0/1/2 (asset meshes: declared tris or the visual's `tris`). */
  tris: [number, number, number];
  visibleWhen?: VisualDef['visibleWhen'];
}

export interface VisualContext {
  theme: ThemeDef;
  assets: ReadonlyMap<string, { tris?: number }>;
  /** Quality scatter density (0…1), default 1. */
  density?: number;
}

/** Default view-depth thresholds for LOD1/LOD2 per layer. */
export const DEFAULT_LOD_DEPTH: Record<RenderLayer, [number, number]> = {
  gameplay: [60, 120], foreground: [30, 60], midground: [34, 70], background: [80, 160],
};
export const DEFAULT_CULL_DEPTH: Record<RenderLayer, number> = { gameplay: 170, foreground: 70, midground: 230, background: 520 };
/** Where a visual sits in depth when the author names a layer but gives no z. */
export const DEFAULT_LAYER_Z: Record<RenderLayer, number> = { foreground: 4, gameplay: 0, midground: -14, background: -70 };

const GAMEPLAY_TYPES = new Set<MapEntity['type']>(['platform', 'wall', 'slope', 'ceiling', 'hazard', 'interactive', 'moving', 'trigger', 'marker']);

/** Depth layer of an entity: explicit `renderLayer` ← legacy `layer` name ← entity type ← z. */
export function layerOf(v: VisualDef | undefined, type: MapEntity['type'], z: number): RenderLayer {
  if (v?.renderLayer) return v.renderLayer;
  if (v?.layer === 'far') return 'background';
  if (v?.layer === 'mid') return 'midground';
  if (v?.layer === 'near') return 'foreground';
  if (type === 'background') return 'background';
  if (GAMEPLAY_TYPES.has(type)) return 'gameplay';
  if (z > 1.5) return 'foreground';
  if (z < -40) return 'background';
  if (z < -2) return 'midground';
  return 'gameplay';
}

const num = (v: Json | undefined, d: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : d);

function bboxOf(polys: Vec2[][]): { minX: number; maxX: number; minY: number; maxY: number } {
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const poly of polys) for (const p of poly) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
  return { minX, maxX, minY, maxY };
}

/** Default slab depth (metres, z extent) per look. */
const depthOfStyle = (style: string): number => (style === 'wood' ? 2.8 : style === 'ice' ? 4.2 : style === 'bounce' ? 3 : 5);

function hullPoints(polys: Vec2[][]): Vec2[] {
  const pts = polys.flat().map(p => ({ x: p.x, y: p.y })).sort((a, b) => a.x - b.x || a.y - b.y);
  const cross = (o: Vec2, a: Vec2, b: Vec2): number => (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
  const lo: Vec2[] = [], up: Vec2[] = [];
  for (const p of pts) { while (lo.length >= 2 && cross(lo[lo.length - 2], lo[lo.length - 1], p) <= 0) lo.pop(); lo.push(p); }
  for (let i = pts.length - 1; i >= 0; i--) { const p = pts[i]; while (up.length >= 2 && cross(up[up.length - 2], up[up.length - 1], p) <= 0) up.pop(); up.push(p); }
  up.pop(); lo.pop();
  return lo.concat(up);
}

/** Mesh parameters implied by the entity's collision (size only — never the polygon itself, except for slopes/wedges). */
function derivedParams(meshId: string, inst: EntityInstance, style: string): MeshParams {
  const r = inst.r;
  const col = r.collisions.find(c => !c.trigger);
  const polys = inst.pieces.map(p => p.local);
  const out: MeshParams = {};
  if (!col || polys.length === 0) return out;
  const bb = bboxOf(polys);
  const w = bb.maxX - bb.minX, h = bb.maxY - bb.minY;      // entity-local (unscaled): the render transform applies the entity scale
  switch (meshId) {
    case 'builtin:platform': {
      out.w = +w.toFixed(3); out.h = +h.toFixed(3);
      if (col.shape.kind === 'box') out.taper = col.shape.taper ?? 1;
      out.depth = depthOfStyle(style);
      break;
    }
    case 'builtin:cliff': out.w = +w.toFixed(3); out.h = +h.toFixed(3); out.depth = 7; break;
    case 'builtin:slab': out.w = +w.toFixed(3); out.h = +h.toFixed(3); out.d = +Math.min(w, h, 4).toFixed(3); break;
    case 'builtin:spikes': out.width = +w.toFixed(3); out.height = +(h / 0.85).toFixed(3); out.count = Math.max(1, Math.round(w / 0.7)); break;
    case 'builtin:blade': out.length = +w.toFixed(3); out.thickness = +h.toFixed(3); break;
    case 'builtin:poly_slab': out.points = hullPoints(polys).map(p => ({ x: +p.x.toFixed(3), y: +p.y.toFixed(3) })) as unknown as Json; out.depth = 4; break;
    default: break;
  }
  return out;
}

/** Offset of the visual origin in entity-local space (unscaled): centre-anchored boxes are generated with their top at y = 0. The renderer composes T(pos)·R(rotation)·S(scale)·T(offset). */
function visualOffset(inst: EntityInstance, meshId: string): Vec2 {
  const col = inst.r.collisions.find(c => !c.trigger);
  if (!col || meshId !== 'builtin:platform' && meshId !== 'builtin:cliff' && meshId !== 'builtin:slab') return { x: 0, y: 0 };
  const bb = bboxOf(inst.pieces.map(p => p.local));
  if (meshId === 'builtin:slab') return { x: (bb.minX + bb.maxX) / 2, y: (bb.minY + bb.maxY) / 2 };
  // platform / cliff meshes are built with the top surface at y = 0 and centred in x
  return { x: (bb.minX + bb.maxX) / 2, y: bb.maxY };
}

export function resolveVisual(inst: EntityInstance, ctx: VisualContext): ResolvedVisual | null {
  const r = inst.r;
  if (!r.enabled) return null;
  const v = r.visual;
  const hasCollision = inst.pieces.length > 0;
  if (v?.kind === 'none') return null;
  if (!v && !hasCollision) return null;

  let derived = false;
  let mesh: string;
  let extra: MeshParams = {};
  const style = v?.style ?? '';
  if (v?.kind === 'mesh' && v.mesh) mesh = v.mesh;
  else if (v?.kind === 'procedural' || v?.kind === 'sprite') {
    const m = STYLE_TO_MESH[style];
    if (m) { mesh = m.mesh; extra = m.params ?? {}; }
    else mesh = r.type === 'background' ? 'builtin:mountain' : GAMEPLAY_TYPES.has(r.type) && hasCollision ? 'builtin:platform' : 'builtin:rock';
  } else {
    // no visual at all: stylised stand-in derived from the collision (never the raw collision mesh)
    derived = true;
    const shape = r.collisions.find(c => !c.trigger)?.shape.kind;
    mesh = r.type === 'hazard' ? 'builtin:spikes' : shape === 'slope' || shape === 'convex' || shape === 'mesh' || shape === 'sphere' || shape === 'capsule' ? 'builtin:poly_slab' : r.type === 'wall' || r.type === 'ceiling' ? 'builtin:cliff' : 'builtin:platform';
  }
  // a slab mesh can only dress a box-shaped collider; slopes, wedges and other convex shapes get the extruded-polygon mesh
  if (mesh === 'builtin:platform' && hasCollision) {
    const shape = r.collisions.find(c => !c.trigger)?.shape.kind;
    if (shape && shape !== 'box') mesh = 'builtin:poly_slab';
  }
  const builtin = isBuiltinMesh(mesh);
  const platformStyle = typeof extra.style === 'string' ? extra.style : style;
  const params: MeshParams = builtin
    ? { ...meshParams(mesh, undefined), ...derivedParams(mesh, inst, platformStyle), ...extra, ...(v?.meshParams ?? {}) }
    : { ...(v?.meshParams ?? {}) };
  if (builtin && 'seed' in BUILTIN_MESHES[mesh].defaults && typeof (v?.meshParams ?? {}).seed !== 'number') params.seed = v?.seed ?? (hashString(r.id) % 9973) + 1;
  if (builtin && mesh === 'builtin:platform' && v?.decor) params.decor = v.decor;

  // layer / depth
  const layer = layerOf(v, r.type, r.z);
  const z = r.z !== 0 || layer === 'gameplay' ? r.z : DEFAULT_LAYER_Z[layer];

  // parts → materials. V2 meshes: `materials[role]` overrides a role, `material` the primary role. Legacy procedural visuals
  // ("@ground", "@secondary" meant "the theme's terrain class") keep the mesh's default dressing; only a named material overrides.
  const roles = builtin ? BUILTIN_MESHES[mesh].roles(params) : ['body'];
  const legacy = v?.kind === 'procedural' || v?.kind === 'sprite';
  const parts: ResolvedPart[] = roles.map((role, i) => {
    let ref: string | undefined = v?.materials?.[role];
    if (!ref && i === 0 && v?.material && !(legacy && v.material.startsWith('@'))) ref = v.material;
    if (!ref) ref = `@${ROLE_SLOT[role] ?? 'rock'}`;
    return { role, material: resolveMaterialRef(ref, ctx.theme) ?? slotId(ctx.theme, 'glow') ?? ref };   // optional slot missing (e.g. lava) → glow
  });

  // mode
  const dynamic = !!inst.motion || !!inst.rotation || inst.gates.length > 0 || !!inst.breakable || !!v?.visibleWhen
    || r.behaviors.some(b => b.type === 'squash' || b.type === 'poi');
  const mode: RenderMode = dynamic ? 'single' : v?.scatter || v?.instancing ? 'instanced' : 'merged';

  // LOD thresholds
  const d = DEFAULT_LOD_DEPTH[layer];
  const lodArr = v?.lod ?? [];
  const lodDepth: [number, number] = [lodArr[0]?.distance ?? d[0], lodArr[1]?.distance ?? Math.max(lodArr[0]?.distance ?? d[0], d[1])];
  const lodMesh: [string | undefined, string | undefined] = [lodArr[0]?.mesh, lodArr[1]?.mesh];

  // triangles
  const tris: [number, number, number] = [0, 0, 0];
  for (const lod of [0, 1, 2] as const) {
    const meshForLod = lod === 0 ? mesh : lodMesh[lod - 1] ?? mesh;
    const explicit = lod > 0 ? lodArr[lod - 1]?.tris : undefined;
    const isB = isBuiltinMesh(meshForLod);
    tris[lod] = explicit ?? (isB ? BUILTIN_MESHES[meshForLod].tris(isB && meshForLod === mesh ? params : meshParams(meshForLod, undefined), lod) : ctx.assets.get(meshForLod)?.tris ?? v?.tris ?? 400);
  }

  const castDefault = layer === 'gameplay' && (r.type === 'platform' || r.type === 'wall' || r.type === 'moving' || r.type === 'interactive' || r.type === 'slope' || r.type === 'ceiling');
  return {
    entityId: r.id, mesh, builtin, params, parts, layer, z, offset: visualOffset(inst, mesh), mode, lodDepth, lodMesh,
    cast: v?.castShadow ?? castDefault, receive: v?.receiveShadow ?? layer !== 'background',
    cullDepth: v?.cullDistance ?? DEFAULT_CULL_DEPTH[layer],
    scatter: v?.scatter, tint: v?.tint, emissive: v?.emissive ?? 0, derived, tris, visibleWhen: v?.visibleWhen,
  };
}

/** Number of instances a resolved visual contributes (scatter count, else 1). */
export const instanceCountOf = (rv: ResolvedVisual): number => (rv.scatter ? Math.max(1, Math.min(4000, Math.round(rv.scatter.count))) : 1);

/** Problems of a visual definition (validator). Empty ⇒ fine. */
export function visualProblems(v: VisualDef): { code: string; message: string }[] {
  const out: { code: string; message: string }[] = [];
  if (v.lod) {
    if (v.lod.length > 2) out.push({ code: 'LOD_INVALID', message: 'at most two LOD entries (LOD1, LOD2) are supported; LOD0 is the base visual' });
    if (v.lod.some((l, k) => !(l.distance > 0) || (k > 0 && l.distance <= v.lod![k - 1].distance))) out.push({ code: 'LOD_INVALID', message: 'LOD distances must be positive and ascend' });
  }
  if (v.cullDistance !== undefined && !(v.cullDistance > 0)) out.push({ code: 'LOD_INVALID', message: 'cullDistance must be > 0' });
  return out;
}
