/**
 * MapCompile — bridges between the map format and the rest of the game (SPEC §19.1, I7).
 *
 *   compileRenderLevel(doc)   best-effort `LevelData` for the EXISTING renderer, analysis tools and the route bot
 *   levelDataToMap(level)     migration: LevelData → MapDocument (order and ids preserved)
 *   createMapRuntime(doc)     MapRuntime for a document
 *   capabilitiesOf(doc)       capability names the map needs (manifest.requirements.capabilities)
 */
import type { HazardDef, LevelData, ObstacleDef, PlatformDef, PlatformKind } from '../data/LevelData';
import { defaultMaterial, defaultSurface } from '../data/LevelData';
import type { WorldTheme } from '../data/worlds';
import { PrefabRegistry, resolveEntity } from './MapPrefab';
import { buildInstance, type EntityInstance } from './MapEntity';
import { buildCurve } from './MapBehavior';
import { placePolys, polysAabb } from './MapCollision';
import { MapRuntime, type MapRuntimeOptions } from './MapRuntime';
import { resolveTheme, toWorldTheme } from './MapTheme';
import { newMapDocument, defaultManifest } from './MapLoader';
import type { Effect, MapDocument, MapEntity, MoveBehavior, RegionDef } from './schema';

const PLATFORM_KINDS: PlatformKind[] = ['rock', 'wood', 'ice', 'bounce', 'special', 'ruin', 'lava', 'goal'];

export function instancesOf(doc: MapDocument, registry = new PrefabRegistry(doc.prefabs)): EntityInstance[] {
  const paths = new Map(doc.paths.map(p => [p.id, buildCurve(p)]));
  return doc.entities.map((e, i) => buildInstance(resolveEntity(e, registry, `/entities/${i}`).entity, paths, `/entities/${i}`));
}

export function createMapRuntime(doc: MapDocument, opts: MapRuntimeOptions = {}): MapRuntime { return new MapRuntime(doc, opts); }

export function worldThemeOf(doc: MapDocument): WorldTheme { return toWorldTheme(resolveTheme(doc.theme, doc.manifest.theme).theme); }

/** Entities that the existing renderer/analysis can express as LevelData lists, in document order. */
export function compileRenderLevel(doc: MapDocument, registry = new PrefabRegistry(doc.prefabs)): LevelData {
  const level: LevelData = {
    levelId: doc.manifest.id, worldId: 'map', name: doc.manifest.name, theme: resolveTheme(doc.theme, doc.manifest.theme).theme.id,
    startPosition: doc.spawn ? { ...doc.spawn.position } : { x: 0, y: 0 },
    goalPosition: { x: 0, y: 0 }, goal: { x: 0, y: 0, w: 1, h: 1 },
    platforms: [], obstacles: [], hazards: [], movingObjects: [], specialSurfaces: [],
    difficulty: doc.manifest.difficulty,
    progress: { path: (doc.progress.routes.find(r => r.kind === 'main')?.points ?? []).map(p => ({ x: p.x, y: p.y })) },
    bounds: { ...doc.world.bounds }, killY: doc.world.killY, parTimeSec: doc.splits.targets.gold,
    landmarks: [], hints: [],
  };
  const z = doc.finish.zones[0];
  if (z && z.shape.kind === 'box') { level.goal = { x: z.position.x, y: z.position.y - z.shape.h / 2, w: z.shape.w, h: z.shape.h }; level.goalPosition = { x: z.position.x, y: z.position.y }; }
  const route = doc.metadata.route;
  if (Array.isArray(route)) level.route = route.map(String);
  const paths = new Map(doc.paths.map(p => [p.id, buildCurve(p)]));
  const platformIds = new Set<string>();
  doc.entities.forEach((e, i) => {
    const { entity: r } = resolveEntity(e, registry, `/entities/${i}`);
    const inst = buildInstance(r, paths);
    const v = r.visual;
    if (r.type === 'decor' && v?.style === 'landmark') {
      level.landmarks.push({ id: r.id, type: String(r.props.landmark ?? 'castle') as never, x: r.x, y: r.y, z: r.z, scale: Number(r.props.scale ?? 1), flip: !!r.props.flip, seed: v.seed });
      return;
    }
    if (!inst.pieces.length) return;
    const p0 = inst.pieces[0];
    const col = r.collisions[0];
    if (p0.kind === 'hazard') {
      const polys = inst.pieces.map(p => placePolys([p.local], r.x, r.y, r.rotation, r.sx, r.sy)[0]);
      const bb = polysAabb(polys);
      const style = v?.style === 'spikes' || v?.style === 'thorns' ? v.style : 'crystals';
      const h: HazardDef = { id: r.id, kind: style, x: (bb.minX + bb.maxX) / 2, y: bb.minY, w: bb.maxX - bb.minX, h: (bb.maxY - bb.minY) / 0.85 };
      if (r.prefab === 'spike') { h.x = r.x; h.y = r.y; h.w = Number(r.props.width ?? h.w); h.h = Number(r.props.height ?? h.h); }
      level.hazards.push(h);
      return;
    }
    const isBoxPlatform = inst.pieces.length === 1 && col?.shape.kind === 'box' && col.shape.anchor === 'topCenter' && r.type !== 'wall' && r.type !== 'ceiling';
    if (isBoxPlatform && col) {
      const sh = col.shape as { w: number; h: number; taper?: number };
      const style = v?.style as PlatformKind | undefined;
      const kind: PlatformKind = style && PLATFORM_KINDS.includes(style) ? style : p0.surface === 'slippery' ? 'ice' : p0.surface === 'bounce' ? 'bounce' : 'rock';
      const pd: PlatformDef = {
        id: r.id, kind, x: r.x, y: r.y, w: sh.w * r.sx, h: sh.h * r.sy, taper: sh.taper ?? 1,
        surface: p0.surface, material: p0.material as never, safe: p0.safe, seed: v?.seed, depth: v?.depth, decor: v?.decor as never,
      };
      if (r.rotation) pd.angleDeg = r.rotation;
      const mv = r.behaviors.find((b): b is MoveBehavior => b.type === 'move');
      if (mv && mv.mode === 'sine') {
        const { x, y } = mv;
        const ref = x ?? y;
        // LevelData's MoveDef has one period/phase for both axes; other motions stay analytic in MapWorld only
        if (ref && (!x || !y || (x.period === y.period && (x.phase ?? 0) === (y.phase ?? 0)))) pd.move = { dx: x?.amplitude ?? 0, dy: y?.amplitude ?? 0, period: ref.period, phase: ref.phase ?? 0 };
      }
      if (inst.motion || (r.behaviors.some(b => b.type === 'move'))) { level.movingObjects.push(pd); platformIds.add(pd.id); }
      else if (p0.surface === 'bounce' || kind === 'special') level.specialSurfaces.push(pd);
      else level.platforms.push(pd);
      return;
    }
    // everything else: convex pieces as obstacles
    inst.pieces.forEach(p => {
      const pts = placePolys([p.local], r.x, r.y, r.rotation, r.sx, r.sy)[0];
      const kind: ObstacleDef['kind'] = r.type === 'ceiling' ? 'ceiling' : r.type === 'wall' ? (v?.style === 'wall' ? 'wall' : 'cliff') : 'cliff';
      level.obstacles.push({ id: p.id, pts, kind, material: p.material as never, seed: v?.seed, depth: v?.depth });
    });
  });
  for (const rg of doc.regions) {
    if (rg.type !== 'hint') continue;
    const fx = (rg.enter ?? []).find((f): f is Extract<Effect, { op: 'hint' }> => f.op === 'hint');
    if (!fx || rg.shape.kind !== 'circle') continue;
    level.hints.push({ id: rg.id, x: rg.shape.x, y: rg.shape.y, textKey: fx.textKey, radius: rg.shape.r });
  }
  return level;
}

// ── capabilities ────────────────────────────────────────────────────────────────────────────────────────────────
export function capabilitiesOf(doc: MapDocument, registry = new PrefabRegistry(doc.prefabs)): string[] {
  const caps = new Set<string>();
  for (const e of doc.entities) {
    const { entity: r } = resolveEntity(e, registry);
    for (const b of r.behaviors) {
      if (b.type === 'boostZone') caps.add('boostSurface');
      else if (b.type === 'squash' || b.type === 'poi') caps.add('presentation');
      else caps.add(b.type);
      if (b.type === 'move' && b.mode !== 'sine') caps.add(`move.${b.mode}`);
    }
    for (const c of r.collisions) {
      if (c.surface === 'slippery') caps.add('slippery');
      if (c.surface === 'bounce') caps.add('bouncePush');
      if (c.surface === 'boost') caps.add('boostSurface');
      if (c.oneWay) caps.add('oneWay');
      if (c.shape.kind === 'mesh') caps.add('meshCollision');
      if (c.hazard) caps.add('hazard');
    }
    for (const q of r.requires) caps.add(q);
    if (r.visual?.kind === 'mesh') caps.add('meshVisual');
  }
  for (const rg of doc.regions) caps.add(`zone.${rg.type}`);
  if (doc.progress.routes.some(r => r.kind !== 'main')) caps.add('branches');
  if (doc.checkpoints.length) caps.add('checkpoints');
  if (doc.paths.length) caps.add('paths');
  if (doc.chunks.defs.length > 1 || doc.chunks.mode === 'auto-grid') caps.add('chunks');
  if (doc.theme && 'theme' in doc.theme && doc.theme.theme?.dayNight) caps.add('dayNight');
  return [...caps].sort();
}

// ── migration LevelData → MapDocument ───────────────────────────────────────────────────────────────────────────
/**
 * Convert a LevelData into a MapDocument. Order and ids are preserved (platforms, moving, special, obstacles, hazards,
 * landmarks), so the resulting world has the same colliders in the same order as `new PhysicsWorld(level)`.
 * The whole level lives in one explicit chunk (small level).
 */
export function levelDataToMap(level: LevelData): MapDocument {
  const doc = newMapDocument(level.levelId);
  const w = level.bounds.maxX - level.bounds.minX, h = level.bounds.maxY - level.bounds.minY;
  doc.manifest = {
    ...defaultManifest(level.levelId), name: level.name, author: 'Pogo Summit', description: `${level.name} (migrated from LevelData)`,
    difficulty: Math.max(1, Math.min(10, Math.round(level.difficulty))), estimatedTimeSec: level.parTimeSec, theme: level.theme,
    mapSize: { width: w, height: h }, tags: [level.worldId, 'migrated'],
  };
  doc.world = { bounds: { ...level.bounds }, killY: level.killY, modes: { doubleJump: false, puzzle: false, grapple: false } };
  doc.theme = { ref: level.theme };
  doc.spawn = { id: 'spawn', position: { ...level.startPosition } };
  doc.finish = { zones: [{ id: 'goal', position: { x: level.goal.x, y: level.goal.y + level.goal.h / 2 }, shape: { kind: 'box', w: level.goal.w, h: level.goal.h } }] };
  doc.progress = { routes: [{ id: 'main', kind: 'main', points: level.progress.path.map(p => ({ x: p.x, y: p.y })) }] };
  doc.splits = { splits: [], targets: { gold: level.parTimeSec, silver: Math.round(level.parTimeSec * 1.4), bronze: Math.round(level.parTimeSec * 2) } };
  doc.chunks = { mode: 'explicit', cell: { w: 32, h: 32 }, defs: [{ id: 'c_all', bounds: { minX: level.bounds.minX - 10, maxX: level.bounds.maxX + 10, minY: level.bounds.minY - 20, maxY: level.bounds.maxY + 10 } }], activateRadius: 40, loadRadius: 64, unloadRadius: 96, lodDistances: [24, 48, 96], maxActive: 9 };
  if (level.route) doc.metadata.route = [...level.route];
  const ents: MapEntity[] = [];
  const addPlatform = (p: PlatformDef): void => {
    const e: MapEntity = {
      id: p.id, type: p.move ? 'moving' : 'platform', position: { x: p.x, y: p.y },
      visual: { kind: 'procedural', style: p.kind, material: '@ground', ...(p.seed !== undefined ? { seed: p.seed } : {}), ...(p.decor ? { decor: p.decor } : {}), ...(p.depth !== undefined ? { depth: p.depth } : {}) },
      collision: { shape: { kind: 'box', w: p.w, h: p.h, taper: p.taper ?? 0.72, anchor: 'topCenter' }, surface: p.surface ?? defaultSurface(p.kind), material: p.material ?? defaultMaterial(p.kind), safe: p.safe ?? !p.move },
    };
    if (p.angleDeg) e.rotation = p.angleDeg;
    if (p.move) e.behavior = { type: 'move', mode: 'sine', x: { amplitude: p.move.dx, period: p.move.period, phase: p.move.phase ?? 0 }, y: { amplitude: p.move.dy, period: p.move.period, phase: p.move.phase ?? 0 } };
    ents.push(e);
  };
  level.platforms.forEach(addPlatform);
  level.movingObjects.forEach(addPlatform);
  level.specialSurfaces.forEach(addPlatform);
  for (const o of level.obstacles) {
    ents.push({
      id: o.id, type: o.kind === 'ceiling' ? 'ceiling' : 'wall', position: { x: 0, y: 0 },
      visual: { kind: 'procedural', style: o.kind ?? 'cliff', material: '@ground', ...(o.seed !== undefined ? { seed: o.seed } : {}), ...(o.depth !== undefined ? { depth: o.depth } : {}) },
      collision: { shape: { kind: 'convex', points: o.pts.map(p => ({ x: p.x, y: p.y })) }, surface: 'normal', material: o.material ?? 'stone', safe: false },
    });
  }
  for (const hz of level.hazards) {
    const hw = hz.w / 2;
    ents.push({
      id: hz.id, type: 'hazard', position: { x: hz.x, y: hz.y },
      visual: { kind: 'procedural', style: hz.kind, material: '@secondary' },
      collision: { shape: { kind: 'convex', points: [{ x: -hw, y: 0 }, { x: hw, y: 0 }, { x: hw * 0.8, y: hz.h * 0.85 }, { x: -hw * 0.8, y: hz.h * 0.85 }] }, hazard: true, material: 'crystal' },
    });
  }
  for (const lm of level.landmarks) {
    ents.push({ id: lm.id, type: 'decor', position: { x: lm.x, y: lm.y, z: lm.z }, visual: { kind: 'procedural', style: 'landmark', ...(lm.seed !== undefined ? { seed: lm.seed } : {}), layer: 'far' }, properties: { landmark: lm.type, scale: lm.scale ?? 1, flip: !!lm.flip } });
  }
  doc.entities = ents;
  const regions: RegionDef[] = level.hints.map(hn => ({ id: hn.id, type: 'hint', shape: { kind: 'circle', x: hn.x, y: hn.y, r: hn.radius }, enter: [{ op: 'hint', textKey: hn.textKey }] }));
  doc.regions = regions;
  doc.manifest.requirements.capabilities = capabilitiesOf(doc);
  return doc;
}
