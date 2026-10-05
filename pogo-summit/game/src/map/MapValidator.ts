/**
 * MapValidator — everything that can be checked before a map is played (SPEC §14).
 * Result severities: ERROR (blocks loading/building) · WARNING · INFO.
 */
import { PACKAGE_MAX_FILES, type MapDocument, type Rect } from './schema';
import { type MapIssue, type ValidationReport, makeReport, mkIssue } from './MapIssue';
import { structuralCheck } from './MapLoader';
import { PrefabRegistry, resolveEntity } from './MapPrefab';
import { type EntityInstance, buildInstance } from './MapEntity';
import { behaviorProblems, buildCurve } from './MapBehavior';
import { type Aabb, placePolys, polysAabb } from './MapCollision';
import { buildProgress } from './MapProgress';
import { SAFE_ACTIVATE_RADIUS, chunkIdFor, estimateLoad, emptyEstimate, type LoadEstimate, rectDistance, rectsOverlap } from './MapChunk';
import { regionAabb } from './MapRegion';
import { availableMaterials, backdropOf, resolveTheme, slotId, themeMaterials } from './MapTheme';
import { bridgeMismatch, materialProblems, normalizeMaterial, texturesOfMaterial } from './MapMaterial';
import { BUILTIN_MESH_IDS, PROCEDURAL_TEXTURES, isBuiltinMesh, meshParamProblems } from './MapAssets';
import { type ResolvedVisual, resolveVisual, visualProblems } from './MapVisual';
import { estimateBackdrop, estimateVisuals } from './MapRenderPlan';
import { scatterProblems } from './MapScatter';
import { cameraProfileProblems } from './MapCamera';
import { RENDER_LAYERS, SURFACE_TYPES, type MaterialDef } from './schema';
import { closestOnPoly, makePoly, polysOverlapDepth } from '../sim/geometry';
import { analyzeLevel, type AnalysisOptions } from '../sim/analysis';
import { createPhysicsConfig } from '../sim/PhysicsConfig';
import { compileRenderLevel } from './MapCompile';

export interface BudgetPreset { name: string; drawCalls: number; triangles: number; textureBytes: number; activeChunks: number; colliders: number; entities: number; vfx: number; audio: number }
export const BUDGETS: Record<string, BudgetPreset> = {
  'android-mid': { name: 'android-mid', drawCalls: 150, triangles: 250_000, textureBytes: 96 * 1024 * 1024, activeChunks: 9, colliders: 600, entities: 2500, vfx: 600, audio: 16 },
  'android-low': { name: 'android-low', drawCalls: 90, triangles: 120_000, textureBytes: 48 * 1024 * 1024, activeChunks: 6, colliders: 300, entities: 1200, vfx: 300, audio: 8 },
};

/** Triangle estimates for procedural styles (the renderer builds them; used only for budgeting). */
export const PROCEDURAL_TRIS: Record<string, number> = {
  rock: 600, wood: 300, ice: 500, bounce: 700, special: 500, ruin: 800, lava: 500, goal: 700, crystals: 400, spikes: 300, thorns: 300, cliff: 800,
  tree_cluster: 3000, rock_cluster: 1500, landmark: 6000, mountain: 1500, flowers: 200, toggle: 120, timed: 120, blade: 200, boost: 300, checkpoint: 300, start_line: 100, finish_line: 100,
};
export const proceduralTris = (style: string | undefined): number => (style ? PROCEDURAL_TRIS[style] ?? 400 : 400);

export interface ValidateOptions {
  budget?: string | BudgetPreset;
  /** Run the brute-force reachability analysis with the real physics (slow: seconds to a minute). */
  deep?: boolean;
  analysis?: Partial<AnalysisOptions>;
  registry?: PrefabRegistry;
}

const GAMEPLAY = new Set(['platform', 'wall', 'slope', 'ceiling', 'hazard', 'interactive', 'moving', 'trigger']);
const hit = (a: Rect, b: Aabb): boolean => a.minX <= b.maxX && a.maxX >= b.minX && a.minY <= b.maxY && a.maxY >= b.minY;
const aabbOverlap = (a: Aabb, b: Aabb, m = 0): boolean => a.minX - m <= b.maxX && a.maxX + m >= b.minX && a.minY - m <= b.maxY && a.maxY + m >= b.minY;

interface PieceGeo { id: string; entityId: string; kind: 'solid' | 'hazard'; poly: ReturnType<typeof makePoly>; box: Aabb; moving: boolean }

export function validateMap(doc: MapDocument, opts: ValidateOptions = {}): ValidationReport {
  const issues: MapIssue[] = [];
  const push = (i: MapIssue) => issues.push(i);
  const err = (code: string, path: string, msg: string, extra: Partial<MapIssue> = {}) => push(mkIssue('ERROR', code, path, msg, extra));
  const warn = (code: string, path: string, msg: string, extra: Partial<MapIssue> = {}) => push(mkIssue('WARNING', code, path, msg, extra));
  const info = (code: string, path: string, msg: string, extra: Partial<MapIssue> = {}) => push(mkIssue('INFO', code, path, msg, extra));
  const registry = opts.registry ?? PrefabRegistry.forDoc(doc);

  // ── structure ────────────────────────────────────────────────────────────────────────────────────────────────
  const structural = structuralCheck(JSON.parse(JSON.stringify(doc)));
  for (const s of structural) push(s);
  if (structural.length) return makeReport(issues);        // the semantic checks below assume a well-formed document

  // ids
  const dup = (kind: string, ids: string[], base: string) => {
    const seen = new Set<string>();
    ids.forEach((id, i) => { if (seen.has(id)) err('STRUCT_DUPLICATE_ID', `${base}/${i}`, `duplicate ${kind} id "${id}"`); seen.add(id); });
  };
  dup('entity', doc.entities.map(e => e.id), '/entities');
  dup('region', doc.regions.map(r => r.id), '/regions');
  dup('checkpoint', doc.checkpoints.map(c => c.id), '/checkpoints');
  dup('path', doc.paths.map(p => p.id), '/paths');
  dup('finish zone', doc.finish.zones.map(z => z.id), '/finish/zones');
  dup('chunk', doc.chunks.defs.map(d => d.id), '/chunks/defs');
  dup('route', doc.progress.routes.map(r => r.id), '/progress/routes');
  dup('split', doc.splits.splits.map(s => s.id), '/splits/splits');

  const { theme, found: themeFound } = resolveTheme(doc.theme, doc.manifest.theme);
  if (!themeFound) err('THEME_MISSING', '/theme', `theme "${'ref' in doc.theme ? doc.theme.ref : doc.manifest.theme}" is not defined`);
  const mats = availableMaterials(doc, theme);
  const assets = new Map(doc.assets.map(a => [a.id, a]));
  const pathIds = new Set(doc.paths.map(p => p.id));
  const paths = new Map(doc.paths.map(p => [p.id, buildCurve(p)]));
  const bounds = doc.world.bounds;
  const boundsBig: Rect = { minX: bounds.minX - 10, maxX: bounds.maxX + 10, minY: bounds.minY - 10, maxY: bounds.maxY + 10 };

  // world switches that would change player rules
  for (const k of ['doubleJump', 'puzzle', 'grapple'] as const) if (doc.world.modes[k]) err('BEHAVIOR_UNSUPPORTED', `/world/modes/${k}`, `mode "${k}" changes player physics and is not supported (the locked physics is not extendable by maps)`);
  if (bounds.maxX <= bounds.minX || bounds.maxY <= bounds.minY) err('STRUCT_RANGE', '/world/bounds', 'bounds must have positive size');
  if (doc.world.killY >= bounds.minY) err('STRUCT_RANGE', '/world/killY', 'killY must be below bounds.minY');

  // ── assets / materials ───────────────────────────────────────────────────────────────────────────────────────
  const usedAssets = new Set<string>();
  const themeMats = themeMaterials(theme);
  const matDef = (id: string): MaterialDef | undefined => doc.materials[id] ?? themeMats[id];
  const textureOf = (id: string): string[] => { const m = matDef(id); return m ? texturesOfMaterial(m) : []; };
  const checkMaterialDef = (id: string, m: MaterialDef, base: string): void => {
    for (const msg of materialProblems(m)) err('MATERIAL_INVALID', `${base}/${id}`, `material "${id}": ${msg}`);
    const refs: [string, string | undefined][] = [['palette', m.palette], ['baseColorMap', m.baseColorMap], ['albedo', m.albedo], ['normalMap', m.normalMap], ['normal', m.normal], ['emissiveMap', m.emissiveMap]];
    for (const [k, t] of refs) {
      if (!t) continue;
      if (t.startsWith('proc:')) { if (!(t.slice(5) in PROCEDURAL_TEXTURES)) err('TEXTURE_MISSING', `${base}/${id}/${k}`, `procedural texture "${t}" does not exist (known: ${Object.keys(PROCEDURAL_TEXTURES).map(x => 'proc:' + x).join(', ')})`); continue; }
      usedAssets.add(t);
      const a = assets.get(t);
      if (!a || a.kind !== 'texture') err('TEXTURE_MISSING', `${base}/${id}/${k}`, `texture asset "${t}" is not declared`);
    }
    if (m.fallback && !mats.has(m.fallback)) err('MATERIAL_MISSING', `${base}/${id}/fallback`, `fallback material "${m.fallback}" is not defined`);
  };
  for (const [id, m] of Object.entries(doc.materials)) checkMaterialDef(id, m, '/materials');
  if ('theme' in doc.theme && doc.theme.theme) {
    for (const [id, m] of Object.entries(doc.theme.theme.materials ?? {})) checkMaterialDef(id, m, '/theme/theme/materials');
    for (const [k, l] of (doc.theme.theme.backdrop ?? []).entries()) {
      const bp = `/theme/theme/backdrop/${k}`;
      if (!(l.z > 0)) err('THEME_INVALID', `${bp}/z`, `backdrop layer "${l.id}": z (depth behind the gameplay plane) must be > 0`);
      if (!(l.follow >= 0 && l.follow <= 1)) err('THEME_INVALID', `${bp}/follow`, `backdrop layer "${l.id}": follow must be 0…1`);
      if (l.mesh && !isBuiltinMesh(l.mesh) && assets.get(l.mesh)?.kind !== 'mesh') err('MODEL_MISSING', `${bp}/mesh`, `backdrop layer "${l.id}": mesh "${l.mesh}" does not exist`);
    }
  }
  // Visual-V2 identity of the theme (info only: missing parts are derived by the renderer)
  const missingIdentity = [!theme.lightingProfile && 'lightingProfile', !theme.backdrop?.length && 'backdrop', !theme.particles?.length && 'particles', !theme.ambientAudio && 'ambientAudio'].filter((x): x is string => !!x);
  if (missingIdentity.length) info('THEME_INCOMPLETE', '/theme', `theme "${theme.id}" has no ${missingIdentity.join(', ')} (derived from sky/fog/lighting by the renderer)`);
  if (doc.camera.profile) for (const msg of cameraProfileProblems(doc.camera.profile)) err('CAMERA_PROFILE_INVALID', '/camera/profile', msg);
  for (const a of doc.assets) if (a.path.includes('..') || a.path.startsWith('/')) err('STRUCT_STRING', `/assets/${a.id}/path`, `asset path "${a.path}" must be relative without ".."`);

  // ── entities ─────────────────────────────────────────────────────────────────────────────────────────────────
  const instances: EntityInstance[] = [];
  const rvs: (ResolvedVisual | null)[] = [];
  const geos: PieceGeo[] = [];
  doc.entities.forEach((e, i) => {
    const path = `/entities/${i}`;
    const { entity: r, issues: ri } = resolveEntity(e, registry, path);
    for (const x of ri) push({ ...x, entityId: e.id });
    const inst = buildInstance(r, paths, path);
    for (const x of inst.problems) push(x);
    instances.push(inst);
    const rv = resolveVisual(inst, { theme, assets });
    rvs.push(rv);
    if (rv) {
      // depth layer vs z
      const lz: Record<string, [number, number]> = { foreground: [0.5, Infinity], gameplay: [-10, 2.5], midground: [-80, -0.5], background: [-Infinity, -20] };
      const [zlo, zhi] = lz[rv.layer];
      if (rv.z < zlo || rv.z > zhi) warn('LAYER_Z_MISMATCH', `${path}/position/z`, `entity "${e.id}": layer "${rv.layer}" expects z in ${zlo === -Infinity ? '(−∞' : '[' + zlo}…${zhi === Infinity ? '∞)' : zhi + ']'} but z = ${rv.z}`, { entityId: e.id });
      if (rv.derived && GAMEPLAY.has(r.type)) info('VISUAL_DERIVED', `${path}/visual`, `entity "${e.id}" declares no visual: the renderer draws a stylised stand-in derived from its collision`, { entityId: e.id });
      if (rv.scatter && rv.mode === 'single') warn('BEHAVIOR_CONFLICT', `${path}/visual/scatter`, `entity "${e.id}": a scattered visual cannot have behaviours (it is a group of static instances)`, { entityId: e.id });
      // physics bridge: the surface type of a material vs what the collision does
      if (GAMEPLAY.has(r.type) && inst.pieces.length) {
        for (const part of rv.parts) {
          const m = matDef(part.material);
          if (!m) continue;
          const nm = normalizeMaterial(m);
          if (nm.surfaceType === 'NORMAL') continue;
          const p0 = inst.pieces[0];
          if (nm.surfaceType === 'WATER' || nm.surfaceType === 'GOAL') { warn('MATERIAL_SURFACE_MISMATCH', `${path}/visual/material`, `entity "${e.id}": material "${part.material}" is ${nm.surfaceType} (no collision of its own) but the entity is a solid`, { entityId: e.id }); continue; }
          if (bridgeMismatch(nm.surfaceType, p0.surface, p0.kind === 'hazard')) warn('MATERIAL_SURFACE_MISMATCH', `${path}/visual/material`, `entity "${e.id}": material "${part.material}" looks ${nm.surfaceType} but the collision is ${p0.kind === 'hazard' ? 'hazard' : p0.surface} — players will not get the physics they see`, { entityId: e.id, hint: 'set collision.surface / hazard to match, or use a NORMAL material' });
        }
      }
    }
    // visual references
    const v = r.visual;
    if (v) {
      if (v.material) {
        if (v.material.startsWith('@')) { if (!resolveSlot(v.material, theme)) err('MATERIAL_MISSING', `${path}/visual/material`, `theme slot "${v.material}" is not defined by theme "${theme.id}"`, { entityId: e.id }); }
        else if (!mats.has(v.material)) err('MATERIAL_MISSING', `${path}/visual/material`, `material "${v.material}" is not defined`, { entityId: e.id });
      }
      if (v.mesh) {
        usedAssets.add(v.mesh);
        if (v.mesh.startsWith('builtin:')) {
          if (!isBuiltinMesh(v.mesh)) err('MODEL_MISSING', `${path}/visual/mesh`, `builtin mesh "${v.mesh}" does not exist (known: ${BUILTIN_MESH_IDS.join(', ')})`, { entityId: e.id });
          else for (const msg of meshParamProblems(v.mesh, v.meshParams)) warn('MESH_PARAM', `${path}/visual/meshParams`, `entity "${e.id}": ${msg}`, { entityId: e.id });
        } else if (assets.get(v.mesh)?.kind !== 'mesh') err('MODEL_MISSING', `${path}/visual/mesh`, `mesh asset "${v.mesh}" is not declared`, { entityId: e.id });
      }
      for (const [role, ref] of Object.entries(v.materials ?? {})) {
        if (ref.startsWith('@')) { if (!resolveSlot(ref, theme)) err('MATERIAL_MISSING', `${path}/visual/materials/${role}`, `theme slot "${ref}" is not defined by theme "${theme.id}"`, { entityId: e.id }); }
        else if (!mats.has(ref)) err('MATERIAL_MISSING', `${path}/visual/materials/${role}`, `material "${ref}" is not defined`, { entityId: e.id });
      }
      if (v.renderLayer !== undefined && !RENDER_LAYERS.includes(v.renderLayer)) err('STRUCT_ENUM', `${path}/visual/renderLayer`, `renderLayer must be one of ${RENDER_LAYERS.join('/')}`, { entityId: e.id });
      for (const pr of visualProblems(v)) err(pr.code, `${path}/visual`, `entity "${e.id}": ${pr.message}`, { entityId: e.id });
      if (v.scatter) for (const msg of scatterProblems(v.scatter)) err('SCATTER_INVALID', `${path}/visual/scatter`, `entity "${e.id}": ${msg}`, { entityId: e.id });
      for (const lod of v.lod ?? []) if (lod.mesh) { usedAssets.add(lod.mesh); if (!lod.mesh.startsWith('builtin:') && assets.get(lod.mesh)?.kind !== 'mesh') err('MODEL_MISSING', `${path}/visual/lod`, `LOD mesh "${lod.mesh}" is not declared`, { entityId: e.id }); }
      if (v.kind === 'mesh' && !v.mesh) err('MODEL_MISSING', `${path}/visual`, 'visual kind "mesh" needs a mesh', { entityId: e.id });
      if (v.layer && doc.background.layers.length && !doc.background.layers.some(l => l.id === v.layer) && !['mid', 'far', 'near'].includes(v.layer)) warn('STRUCT_ENUM', `${path}/visual/layer`, `layer "${v.layer}" is not defined in background.layers`, { entityId: e.id });
    }
    // behaviours
    r.behaviors.forEach((b, k) => {
      for (const m of behaviorProblems(b, pathIds)) err(b.type === 'move' && m.includes('is not defined') ? 'PATH_MISSING' : 'BEHAVIOR_INVALID', `${path}/behavior${r.behaviors.length > 1 ? `/${k}` : ''}`, `entity "${e.id}": ${m}`, { entityId: e.id });
      if (b.type === 'boostZone') warn('CAPABILITY_UNAVAILABLE', `${path}/behavior`, `entity "${e.id}": boostZone is declared but not applied (the locked spec sets the boost flag only through E13)`, { entityId: e.id, hint: 'capability boostSurface' });
      if (b.type === 'rotate' && b.axis && b.axis !== 'z' && r.collisions.length) info('BEHAVIOR_UNSUPPORTED', `${path}/behavior`, `entity "${e.id}": rotation about ${b.axis} is visual-only; collision stays planar`, { entityId: e.id });
    });
    for (const [ci, c] of r.collisions.entries()) {
      const cp = `${path}/collision${r.collisions.length > 1 ? `/${ci}` : ''}`;
      if (c.surface === 'bounce' || c.surface === 'sticky' || c.surface === 'boost') warn('CAPABILITY_UNAVAILABLE', cp, `entity "${e.id}": surface "${c.surface}" behaves as "normal" under the locked physics`, { entityId: e.id, hint: c.surface === 'bounce' ? 'capability bouncePush' : 'capability boostSurface' });
      if (c.oneWay && c.oneWay !== 'up') warn('BEHAVIOR_UNSUPPORTED', cp, `entity "${e.id}": one-way "${c.oneWay}" is accepted but only "up" is gated by the map layer`, { entityId: e.id });
      if (c.safe && inst.rotation && inst.rotation.axis === 'z') warn('CAPABILITY_UNAVAILABLE', cp, `entity "${e.id}": a rotating collider is marked safe, but riders are not carried by rotation`, { entityId: e.id });
      if (c.trigger && (c.hazard || c.oneWay)) warn('BEHAVIOR_INVALID', cp, `entity "${e.id}": a trigger cannot also be a hazard/one-way`, { entityId: e.id });
    }
    if (r.type === 'background' && r.collisions.length) warn('BEHAVIOR_INVALID', `${path}/collision`, `background entity "${e.id}" has collision`, { entityId: e.id });
    if (r.type === 'decor' && r.collisions.some(c => !c.trigger)) info('BEHAVIOR_INVALID', `${path}/collision`, `decor entity "${e.id}" has collision (use a platform/wall type)`, { entityId: e.id });
    if (GAMEPLAY.has(r.type) && r.type !== 'trigger' && r.collisions.length === 0 && r.enabled) warn('COLLISION_INVALID', path, `${r.type} entity "${e.id}" has no collision`, { entityId: e.id });
    // bounds
    if (inst.extent.minX <= inst.extent.maxX) {
      if (!hit(boundsBig, inst.extent)) {
        (GAMEPLAY.has(r.type) ? err : info)('OBJECT_OUT_OF_BOUNDS', path, `entity "${e.id}" lies entirely outside the world bounds${GAMEPLAY.has(r.type) ? '' : ' (scenery — fine if intended)'}`, { entityId: e.id });
      } else if (GAMEPLAY.has(r.type) && (inst.extent.minX < boundsBig.minX || inst.extent.maxX > boundsBig.maxX || inst.extent.minY < boundsBig.minY || inst.extent.maxY > boundsBig.maxY)) {
        warn('OBJECT_OUT_OF_BOUNDS', path, `entity "${e.id}" extends more than 10 m outside the world bounds`, { entityId: e.id });
      }
    }
    // geometry for overlap / seam / spawn checks
    for (const p of inst.pieces) {
      if (p.kind !== 'solid' && p.kind !== 'hazard') continue;
      const pts = placePolys([p.local], r.x, r.y, r.rotation, r.sx, r.sy)[0];
      geos.push({ id: p.id, entityId: r.id, kind: p.kind, poly: makePoly(pts), box: polysAabb([pts]), moving: !!inst.motion || !!inst.rotation || inst.gates.length > 0 || !!inst.breakable || !!p.oneWay });
      if (inst.motion || inst.rotation) { const g = geos[geos.length - 1]; g.box = inst.extent; }
      if (!hit(boundsBig, geos[geos.length - 1].box)) err('COLLISION_OUT_OF_BOUNDS', `${path}/collision`, `collision of "${e.id}" lies outside the world bounds`, { entityId: e.id });
    }
  });

  // effects referencing assets / flags
  const checkEffects = (list: import('./schema').Effect[] | undefined, path: string) => {
    list?.forEach((fx, i) => {
      if (fx.op === 'audio') { usedAssets.add(fx.id); if (!fx.id.startsWith('builtin:') && assets.get(fx.id)?.kind !== 'audio') err('AUDIO_MISSING', `${path}/${i}`, `audio "${fx.id}" is not declared`); }
      if (fx.op === 'setCheckpoint' && !doc.checkpoints.some(c => c.id === fx.id)) err('CHECKPOINT_ORDER', `${path}/${i}`, `setCheckpoint refers to unknown checkpoint "${fx.id}"`);
    });
  };
  doc.regions.forEach((r, i) => {
    if (r.type === 'camera') {
      const prof = r.params?.profile as Partial<import('./schema').CameraProfile> | undefined;
      if (!prof) warn('CAMERA_PROFILE_INVALID', `/regions/${i}`, `camera zone "${r.id}" has no params.profile (it changes nothing)`);
      else for (const msg of cameraProfileProblems(prof)) err('CAMERA_PROFILE_INVALID', `/regions/${i}/params/profile`, `camera zone "${r.id}": ${msg}`);
    }
    for (const fx of [...(r.enter ?? []), ...(r.exit ?? []), ...(r.stay ?? [])]) if (fx.op === 'camera' && fx.profile) for (const msg of cameraProfileProblems(fx.profile)) err('CAMERA_PROFILE_INVALID', `/regions/${i}`, `region "${r.id}": ${msg}`);
    checkEffects(r.enter, `/regions/${i}/enter`); checkEffects(r.exit, `/regions/${i}/exit`); checkEffects(r.stay, `/regions/${i}/stay`);
    if (r.type === 'checkpoint' || r.type === 'finish') err('STRUCT_ENUM', `/regions/${i}/type`, `region type "${r.type}" is generated from checkpoints[]/finish — do not author it by hand`);
    const b = regionAabb(r.shape);
    if (!(b.maxX > b.minX && b.maxY > b.minY)) err('STRUCT_RANGE', `/regions/${i}/shape`, `region "${r.id}" has zero size`);
    if (!hit(boundsBig, b)) warn('OBJECT_OUT_OF_BOUNDS', `/regions/${i}`, `region "${r.id}" lies outside the world bounds`);
    if (r.type === 'kill' && !(r.enter ?? []).some(f => f.op === 'kill')) warn('BEHAVIOR_INVALID', `/regions/${i}`, `kill region "${r.id}" has no kill effect`);
  });
  for (const a of doc.assets) if (!usedAssets.has(a.id)) info('ASSET_UNUSED', `/assets/${a.id}`, `asset "${a.id}" is not referenced`);

  // ── spawn / finish ───────────────────────────────────────────────────────────────────────────────────────────
  if (!doc.spawn) err('SPAWN_MISSING', '/spawn', 'the map has no spawn point');
  else {
    const sp = doc.spawn.position;
    if (!hit(bounds, { minX: sp.x, maxX: sp.x, minY: sp.y, maxY: sp.y })) err('OBJECT_OUT_OF_BOUNDS', '/spawn', 'spawn is outside the world bounds');
    let best = Infinity;
    const o = { x: 0, y: 0, nx: 0, ny: 1, dist: 0 };
    for (const g of geos) { if (g.moving || g.kind !== 'solid') continue; closestOnPoly(g.poly, sp.x, sp.y, o); best = Math.min(best, o.dist); }
    if (best > 0.3) err('SPAWN_NOT_ON_GROUND', '/spawn/position', `spawn is ${best === Infinity ? 'far from any solid' : best.toFixed(2) + ' m from the nearest solid'} (needs ≤ 0.3 m)`);
  }
  if (doc.finish.zones.length === 0) err('FINISH_MISSING', '/finish', 'the map has no finish zone');
  doc.finish.zones.forEach((z, i) => {
    if (z.shape.kind === 'box' && !(z.shape.w > 0 && z.shape.h > 0)) err('STRUCT_RANGE', `/finish/zones/${i}/shape`, 'finish zone has zero size');
    if (!hit(boundsBig, { minX: z.position.x, maxX: z.position.x, minY: z.position.y, maxY: z.position.y })) err('OBJECT_OUT_OF_BOUNDS', `/finish/zones/${i}`, 'finish zone is outside the world bounds');
  });

  // ── collision quality ────────────────────────────────────────────────────────────────────────────────────────
  const statics = geos.filter(g => !g.moving).sort((a, b) => a.box.minX - b.box.minX);
  for (let i = 0; i < statics.length; i++) {
    for (let j = i + 1; j < statics.length; j++) {
      const a = statics[i], b = statics[j];
      if (b.box.minX > a.box.maxX + 0.05) break;
      if (a.entityId === b.entityId || !aabbOverlap(a.box, b.box, 0.05)) continue;
      if (a.kind !== 'solid' || b.kind !== 'solid') continue;
      const d = polysOverlapDepth(a.poly, b.poly);
      if (d > 0.05) warn('COLLISION_OVERLAP', `/entities`, `solids "${a.id}" and "${b.id}" overlap by ${d.toFixed(2)} m`, { entityId: a.entityId, hint: b.entityId });
      else if (d === 0) {
        const gap = polyGap(a.poly, b.poly);
        if (gap > 1e-9 && gap < 0.03) warn('COLLISION_HOLE', `/entities`, `solids "${a.id}" and "${b.id}" leave a ${(gap * 1000).toFixed(1)} mm seam`, { entityId: a.entityId, hint: b.entityId });
      }
    }
  }
  // moving vs static: corners of the motion range
  for (const inst1 of instances) {
    if (!inst1.motion) continue;
    const m = inst1.motion.range;
    for (const piece of inst1.pieces) {
      if (piece.kind !== 'solid') continue;
      const base = placePolys([piece.local], inst1.r.x, inst1.r.y, inst1.r.rotation, inst1.r.sx, inst1.r.sy)[0];
      for (const [dx, dy] of [[m.minX, m.minY], [m.maxX, m.maxY], [m.minX, m.maxY], [m.maxX, m.minY]]) {
        const poly = makePoly(base.map(v => ({ x: v.x + dx, y: v.y + dy })));
        const bx = polysAabb([poly.pts]);
        for (const s of statics) {
          if (s.entityId === inst1.r.id || !aabbOverlap(bx, s.box) || s.kind !== 'solid') continue;
          const d = polysOverlapDepth(poly, s.poly);
          if (d > 0.05) { warn('COLLISION_OVERLAP', `/entities`, `moving "${piece.id}" overlaps "${s.id}" by ${d.toFixed(2)} m at the end of its range`, { entityId: inst1.r.id, hint: s.entityId }); break; }
        }
      }
    }
  }

  // ── progress / checkpoints / splits ──────────────────────────────────────────────────────────────────────────
  const model = buildProgress(doc.progress);
  for (const p of model.problems) err(p.includes('non-monotonic') ? 'PROGRESS_ANCHORS' : 'PROGRESS_INVALID', '/progress', p);
  const main = doc.progress.routes.find(r => r.kind === 'main');
  if (main && main.points.length >= 2 && doc.spawn) {
    const a = main.points[0], b = main.points[main.points.length - 1];
    if (Math.hypot(a.x - doc.spawn.position.x, a.y - doc.spawn.position.y) > 8) warn('PROGRESS_ENDPOINTS', '/progress/routes', 'the main route does not start near the spawn (> 8 m)');
    const z = doc.finish.zones[0];
    if (z && Math.hypot(b.x - z.position.x, b.y - z.position.y) > 8) warn('PROGRESS_ENDPOINTS', '/progress/routes', 'the main route does not end near the finish (> 8 m)');
  }
  const orders = doc.checkpoints.map(c => c.order);
  new Set(orders.filter((o, i) => orders.indexOf(o) !== i)).forEach(o => err('CHECKPOINT_ORDER', '/checkpoints', `checkpoint order ${o} is used more than once`));
  const sorted = [...doc.checkpoints].sort((a, b) => a.order - b.order);
  sorted.forEach((c, i) => {
    if (c.order !== i && !c.optional) warn('CHECKPOINT_ORDER', `/checkpoints`, `checkpoint orders are not contiguous from 0 (found ${c.order} at position ${i})`);
    const reg = regionAabb(c.region);
    if (!(reg.maxX > reg.minX && reg.maxY > reg.minY)) err('CHECKPOINT_REGION', `/checkpoints`, `checkpoint "${c.id}" has a zero-size region`);
    for (const r of c.requires ?? []) if (!doc.checkpoints.some(x => x.id === r)) err('CHECKPOINT_ORDER', '/checkpoints', `checkpoint "${c.id}" requires unknown checkpoint "${r}"`);
    const prev = sorted[i - 1];
    if (prev && c.progress !== undefined && prev.progress !== undefined && c.progress <= prev.progress && !c.optional && !prev.optional) err('CHECKPOINT_ORDER', '/checkpoints', `checkpoint "${c.id}" has progress ${c.progress} ≤ the previous checkpoint's ${prev.progress}`);
    let ground = Infinity; const o = { x: 0, y: 0, nx: 0, ny: 1, dist: 0 };
    for (const g of statics) { if (g.kind !== 'solid') continue; closestOnPoly(g.poly, c.respawn.x, c.respawn.y, o); ground = Math.min(ground, o.dist); }
    for (const g of geos) { if (!g.moving || g.kind !== 'solid') continue; closestOnPoly(g.poly, c.respawn.x, c.respawn.y, o); ground = Math.min(ground, o.dist); }
    if (ground > 3) warn('CHECKPOINT_RESPAWN', '/checkpoints', `checkpoint "${c.id}" respawn point is ${ground === Infinity ? 'far from' : ground.toFixed(1) + ' m from'} any solid (needs ≤ 3 m to anchor)`);
  });
  if (doc.manifest.checkpointCount !== doc.checkpoints.length) warn('CHECKPOINT_COUNT', '/manifest/checkpointCount', `manifest says ${doc.manifest.checkpointCount} checkpoints, the map has ${doc.checkpoints.length}`);
  doc.splits.splits.forEach((s, i) => { if (!doc.checkpoints.some(c => c.id === s.checkpoint)) err('CHECKPOINT_ORDER', `/splits/splits/${i}`, `split "${s.id}" refers to unknown checkpoint "${s.checkpoint}"`); });
  const t = doc.splits.targets;
  if (t.gold > 0 && !(t.gold < t.silver && t.silver < t.bronze)) warn('STRUCT_RANGE', '/splits/targets', 'medal targets should satisfy gold < silver < bronze');

  // ── chunks ───────────────────────────────────────────────────────────────────────────────────────────────────
  const ck = doc.chunks;
  if (ck.activateRadius < SAFE_ACTIVATE_RADIUS) err('CHUNK_RADIUS', '/chunks/activateRadius', `activateRadius ${ck.activateRadius} m is below the physics-safe minimum ${SAFE_ACTIVATE_RADIUS} m (vMax 0.73 m/tick × 30 ticks + margin)`);
  if (ck.loadRadius < ck.activateRadius) err('CHUNK_RADIUS', '/chunks/loadRadius', 'loadRadius must be ≥ activateRadius');
  if (ck.unloadRadius <= ck.loadRadius) err('CHUNK_RADIUS', '/chunks/unloadRadius', 'unloadRadius must be > loadRadius (hysteresis)');
  const defIds = new Set(ck.defs.map(d => d.id));
  for (let i = 0; i < ck.defs.length; i++) for (let j = i + 1; j < ck.defs.length; j++) {
    const a = ck.defs[i].bounds, b = ck.defs[j].bounds;
    const ox = Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX), oy = Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY);
    if (ox > 0.5 && oy > 0.5) warn('CHUNK_OVERLAP', '/chunks/defs', `chunks "${ck.defs[i].id}" and "${ck.defs[j].id}" overlap (${ox.toFixed(1)} × ${oy.toFixed(1)} m)`);
  }
  const assignment = new Map<string, string>();
  doc.entities.forEach((e, i) => {
    const ex = instances[i].extent;
    const cx = (ex.minX + ex.maxX) / 2, cy = (ex.minY + ex.maxY) / 2;
    if (e.chunk) {
      if (ck.mode === 'explicit' && !defIds.has(e.chunk)) err('CHUNK_UNASSIGNED', `/entities/${i}/chunk`, `entity "${e.id}" refers to unknown chunk "${e.chunk}"`);
      assignment.set(e.id, e.chunk);
    } else {
      assignment.set(e.id, chunkIdFor(ck, cx, cy));
      if (GAMEPLAY.has(instances[i].r.type) && ck.mode === 'explicit' && ck.defs.length && !ck.defs.some(d => cx >= d.bounds.minX && cx <= d.bounds.maxX && cy >= d.bounds.minY && cy <= d.bounds.maxY)) warn('CHUNK_UNASSIGNED', `/entities/${i}`, `entity "${e.id}" lies outside every explicit chunk (assigned to the nearest)`);
    }
  });

  const chunkCount = new Set(assignment.values()).size;
  if (chunkCount > PACKAGE_MAX_FILES - 100) err('CHUNK_TOO_MANY', '/chunks', `${chunkCount} chunks cannot be packaged (limit ${PACKAGE_MAX_FILES} files): increase chunks.cell`);
  else if (chunkCount > Math.floor(PACKAGE_MAX_FILES * 0.85)) warn('CHUNK_TOO_MANY', '/chunks', `${chunkCount} chunks is close to the package limit of ${PACKAGE_MAX_FILES} files: consider a larger chunks.cell`);

  // ── budgets ──────────────────────────────────────────────────────────────────────────────────────────────────
  const budget = typeof opts.budget === 'object' ? opts.budget : BUDGETS[opts.budget ?? 'android-mid'] ?? BUDGETS['android-mid'];
  const byChunk = new Map<string, EntityInstance[]>();
  const visByChunk = new Map<string, ResolvedVisual[]>();
  doc.entities.forEach((e, i) => {
    const id = assignment.get(e.id)!;
    (byChunk.get(id) ?? byChunk.set(id, []).get(id)!).push(instances[i]);
    const rv = rvs[i];
    if (rv) (visByChunk.get(id) ?? visByChunk.set(id, []).get(id)!).push(rv);
  });
  const chunkInfo: { id: string; extent: Rect; list: EntityInstance[]; vis: ResolvedVisual[] }[] = [...byChunk.entries()].sort(([a], [b]) => (a < b ? -1 : 1)).map(([id, list]) => {
    let ex: Aabb = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity };
    for (const it of list) ex = { minX: Math.min(ex.minX, it.extent.minX), maxX: Math.max(ex.maxX, it.extent.maxX), minY: Math.min(ex.minY, it.extent.minY), maxY: Math.max(ex.maxY, it.extent.maxY) };
    return { id, extent: ex, list, vis: visByChunk.get(id) ?? [] };
  });
  let worst: { est: LoadEstimate; at: string; chunks: number } = { est: emptyEstimate(), at: '', chunks: 0 };
  const key = (e: LoadEstimate) => e.colliders / budget.colliders + e.entities / budget.entities;
  const cdist = (o: { extent: Rect }, c: { extent: Rect }) => rectDistance(o.extent, (c.extent.minX + c.extent.maxX) / 2, (c.extent.minY + c.extent.maxY) / 2);
  // what the camera can see around a chunk centre (gameplay layer), used for draw calls / triangles
  const VIS_W = 70, VIS_H = 44;
  const backdrop = estimateBackdrop(backdropOf(theme));
  let vis = { drawCalls: 0, shadowCalls: 0, triangles: 0, trianglesExpected: 0, textureBytes: 0, at: '' };
  let texWorst = 0;
  for (const c of chunkInfo) {
    const near = chunkInfo.filter(o => cdist(o, c) <= ck.activateRadius).sort((a, b) => cdist(a, c) - cdist(b, c)).slice(0, ck.maxActive);
    const est = estimateLoad(near.flatMap(w => w.list), assets, textureOf, proceduralTris);
    if (key(est) > key(worst.est)) worst = { est, at: c.id, chunks: near.length };
    const cx = (c.extent.minX + c.extent.maxX) / 2, cy = (c.extent.minY + c.extent.maxY) / 2;
    const seen = chunkInfo.filter(o => o.extent.minX <= cx + VIS_W / 2 && o.extent.maxX >= cx - VIS_W / 2 && o.extent.minY <= cy + VIS_H / 2 && o.extent.maxY >= cy - VIS_H / 2);
    const ve = estimateVisuals(seen.map(w => w.vis), matDef, assets);
    if (ve.drawCalls + ve.shadowCalls > vis.drawCalls + vis.shadowCalls) vis = { ...vis, drawCalls: ve.drawCalls, shadowCalls: ve.shadowCalls, at: c.id };
    if (ve.trianglesExpected > vis.trianglesExpected) vis = { ...vis, triangles: ve.triangles, trianglesExpected: ve.trianglesExpected };
    texWorst = Math.max(texWorst, estimateVisuals(near.map(w => w.vis), matDef, assets).textureBytes);
  }
  const e = { ...worst.est, drawCalls: vis.drawCalls + vis.shadowCalls + backdrop.drawCalls, triangles: vis.trianglesExpected + backdrop.triangles, textureBytes: texWorst };
  const budgetCheck = (code: string, label: string, v: number, cap: number) => {
    if (v > cap * 2) err(code, '/chunks', `${label} ${Math.round(v)} in the worst window (around chunk "${worst.at}") is more than 2× the ${budget.name} budget ${cap}`);
    else if (v > cap) warn(code, '/chunks', `${label} ${Math.round(v)} in the worst window (around chunk "${worst.at}") exceeds the ${budget.name} budget ${cap}`);
  };
  budgetCheck('BUDGET_DRAWCALLS', 'draw calls (main + shadow pass + backdrop)', e.drawCalls, budget.drawCalls);
  budgetCheck('BUDGET_TRIANGLES', 'triangles', e.triangles, budget.triangles);
  budgetCheck('BUDGET_TEXTURE_MEMORY', 'texture bytes', e.textureBytes, budget.textureBytes);
  budgetCheck('BUDGET_COLLIDERS', 'colliders', e.colliders, budget.colliders);
  budgetCheck('BUDGET_ENTITIES', 'entities', e.entities, budget.entities);
  budgetCheck('BUDGET_VFX', 'VFX particles', Math.max(e.vfx, 0), budget.vfx);
  budgetCheck('BUDGET_AUDIO', 'audio sources', e.audio, budget.audio);
  if (doc.vfx.maxParticles > budget.vfx) warn('BUDGET_VFX', '/vfx/maxParticles', `declared particle cap ${doc.vfx.maxParticles} exceeds the ${budget.name} budget ${budget.vfx}`);
  if (doc.audio.maxVoices > budget.audio) warn('BUDGET_AUDIO', '/audio/maxVoices', `declared voice cap ${doc.audio.maxVoices} exceeds the ${budget.name} budget ${budget.audio}`);
  if (ck.maxActive > budget.activeChunks) warn('BUDGET_ENTITIES', '/chunks/maxActive', `maxActive ${ck.maxActive} exceeds the ${budget.name} budget ${budget.activeChunks}`);

  // ── reachability (opt-in) ────────────────────────────────────────────────────────────────────────────────────
  if (opts.deep && !issues.some(i => i.severity === 'ERROR')) {
    const level = compileRenderLevel(doc, registry);
    try {
      const a = analyzeLevel(level, createPhysicsConfig(), opts.analysis ?? {});
      if (!a.goalReached) err('FINISH_UNREACHABLE', '/finish', 'the brute-force physics analysis found no way from the spawn to the finish (static collision, conservative landing window)');
      for (const u of a.unreachable) info('PLATFORM_UNREACHABLE', '/entities', `platform "${u}" is not reachable in the analysis`, { entityId: u });
      const route = level.route ?? [];
      for (let i = 0; i + 1 < route.length; i++) {
        const best = Math.max(0, ...a.edges.filter(x => x.from === route[i] && x.to === route[i + 1]).map(x => x.cells));
        if (best === 0) warn('HOP_UNREACHABLE', '/metadata/route', `no (angle, load) cell lands ${route[i]} → ${route[i + 1]}`);
        else if (best < 3) info('HOP_TIGHT', '/metadata/route', `hop ${route[i]} → ${route[i + 1]} is tight (${best} landing cells)`);
      }
    } catch (ex) { warn('FINISH_UNREACHABLE', '/finish', `reachability analysis could not run: ${(ex as Error).message}`); }
  }

  info('STATS', '', `${doc.entities.length} entities · ${byChunk.size} chunks · worst window ≈ ${e.drawCalls} draw calls (${vis.drawCalls} + ${vis.shadowCalls} shadow + ${backdrop.drawCalls} backdrop), ${Math.round(e.triangles)} triangles (all-LOD0 worst case ${Math.round(vis.triangles + backdrop.triangles)}), ${(e.textureBytes / 1048576).toFixed(1)} MB textures, ${e.colliders} colliders (${budget.name})`);
  return makeReport(issues);
}

function resolveSlot(ref: string, theme: ReturnType<typeof resolveTheme>['theme']): string | undefined {
  return slotId(theme, ref.slice(1));
}

/** Minimum distance between two non-overlapping convex polygons. */
function polyGap(a: ReturnType<typeof makePoly>, b: ReturnType<typeof makePoly>): number {
  let best = Infinity;
  const o = { x: 0, y: 0, nx: 0, ny: 1, dist: 0 };
  for (const v of a.pts) { closestOnPoly(b, v.x, v.y, o); best = Math.min(best, o.dist); }
  for (const v of b.pts) { closestOnPoly(a, v.x, v.y, o); best = Math.min(best, o.dist); }
  return best;
}

