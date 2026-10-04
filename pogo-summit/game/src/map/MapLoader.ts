/**
 * MapLoader — parse · migrate · normalise · structurally check · serialise (SPEC §5, §19.3).
 *
 * Loader = "can this JSON be represented as a MapDocument" (types, ranges, known enums, finite numbers).
 * The validator (MapValidator) answers the different question "is this a good, playable map".
 */
import {
  BEHAVIOR_TYPES, DOC_KEYS, ENTITY_TYPES, MAP_FORMAT, MAP_FORMAT_VERSION, PHYSICS_CONTRACT, REGION_TYPES,
  type ChunkingDef, type Json, type LightingDef, type MapDocument, type MapEntity, type MapManifest,
} from './schema';
import { type MapIssue, MapLoadError, mkIssue } from './MapIssue';

// ── defaults ────────────────────────────────────────────────────────────────────────────────────────────────────
export const defaultChunking = (): ChunkingDef => ({
  mode: 'auto-grid', cell: { w: 32, h: 32 }, defs: [], activateRadius: 40, loadRadius: 64, unloadRadius: 96, lodDistances: [24, 48, 96], maxActive: 9,
});
export const defaultLighting = (): LightingDef => ({
  sun: { dir: [-0.55, 0.62, 0.55], intensity: 1.5, color: '#ffe3b8' },
  ambient: { color: '#d6e4ff', intensity: 0.9 },
  shadows: { enabled: true, mode: 'blob' },
  volumes: [],
});
export const defaultManifest = (id = 'untitled'): MapManifest => ({
  id, name: id, author: '', description: '', version: '1.0.0', difficulty: 1, estimatedTimeSec: 60, theme: 'autumn_hills',
  mapSize: { width: 0, height: 0 }, checkpointCount: 0, tags: [],
  requirements: { minFormatVersion: MAP_FORMAT_VERSION, capabilities: [], physics: PHYSICS_CONTRACT },
});

/** A complete, valid-structure, empty map. */
export function newMapDocument(id = 'untitled', patch: Partial<MapDocument> = {}): MapDocument {
  return normalizeMap({ format: MAP_FORMAT, formatVersion: MAP_FORMAT_VERSION, manifest: defaultManifest(id), ...patch });
}

type Obj = Record<string, unknown>;
const isObj = (v: unknown): v is Obj => typeof v === 'object' && v !== null && !Array.isArray(v);
const clone = <T>(v: T): T => (v === undefined ? v : JSON.parse(JSON.stringify(v)));
export const cloneMap = (d: MapDocument): MapDocument => clone(d);

/** Fill every optional section with its default (does not invent spawn/finish content). */
export function normalizeMap(raw: Partial<MapDocument> & Obj): MapDocument {
  const m = (raw.manifest ?? {}) as Partial<MapManifest>;
  const manifest: MapManifest = { ...defaultManifest(m.id ?? 'untitled'), ...m, requirements: { ...defaultManifest().requirements, ...(m.requirements ?? {}) } };
  const doc: MapDocument = {
    format: MAP_FORMAT,
    formatVersion: typeof raw.formatVersion === 'number' ? raw.formatVersion : MAP_FORMAT_VERSION,
    manifest,
    world: { bounds: { minX: -10, maxX: 10, minY: -10, maxY: 10 }, killY: -20, modes: { doubleJump: false, puzzle: false, grapple: false }, ...(raw.world as object) } as MapDocument['world'],
    theme: (raw.theme as MapDocument['theme']) ?? { ref: manifest.theme },
    materials: (raw.materials as MapDocument['materials']) ?? {},
    assets: (raw.assets as MapDocument['assets']) ?? [],
    prefabs: (raw.prefabs as MapDocument['prefabs']) ?? {},
    spawn: (raw.spawn as MapDocument['spawn']) ?? null,
    finish: (raw.finish as MapDocument['finish']) ?? { zones: [] },
    checkpoints: (raw.checkpoints as MapDocument['checkpoints']) ?? [],
    progress: (raw.progress as MapDocument['progress']) ?? { routes: [] },
    splits: (raw.splits as MapDocument['splits']) ?? { splits: [], targets: { gold: 0, silver: 0, bronze: 0 } },
    paths: (raw.paths as MapDocument['paths']) ?? [],
    entities: (raw.entities as MapDocument['entities']) ?? [],
    regions: (raw.regions as MapDocument['regions']) ?? [],
    background: (raw.background as MapDocument['background']) ?? { layers: [] },
    lighting: { ...defaultLighting(), ...(raw.lighting as object) } as LightingDef,
    camera: { zoom: 1, ...(raw.camera as object) } as MapDocument['camera'],
    vfx: { maxParticles: 600, ...(raw.vfx as object) },
    audio: { maxVoices: 16, ...(raw.audio as object) },
    chunks: { ...defaultChunking(), ...(raw.chunks as object) } as ChunkingDef,
    metadata: (raw.metadata as MapDocument['metadata']) ?? {},
  };
  if (raw.extensions) doc.extensions = raw.extensions as Record<string, Json>;
  return doc;
}

// ── migration ───────────────────────────────────────────────────────────────────────────────────────────────────
type Migration = (raw: Obj) => Obj;
/**
 * `formatVersion` n → n+1. Version 1 was the pre-release draft that kept a flat `platforms[]` list; it is upgraded to the
 * entity list with the built-in `stone_platform` / `moving_platform` prefabs (position = centre of the top surface).
 */
const MIGRATIONS: Record<number, Migration> = {
  1: raw => {
    const out: Obj = { ...raw, formatVersion: 2 };
    const plats = (Array.isArray(raw.platforms) ? raw.platforms : []) as Obj[];
    delete out.platforms;
    const entities = (Array.isArray(raw.entities) ? raw.entities : []) as Obj[];
    for (const p of plats) {
      const move = p.move as Obj | undefined;
      const e: Obj = {
        id: String(p.id), type: move ? 'moving' : 'platform', prefab: move ? 'moving_platform' : p.surface === 'slippery' ? 'ice_platform' : 'stone_platform',
        position: { x: Number(p.x), y: Number(p.y) },
        properties: { width: Number(p.w), thickness: Number(p.h) },
      };
      if (move) e.behavior = { type: 'move', mode: 'sine', x: { amplitude: Number(move.dx ?? 0), period: Number(move.period ?? 6), phase: Number(move.phase ?? 0) }, y: { amplitude: Number(move.dy ?? 0), period: Number(move.period ?? 6), phase: Number(move.phase ?? 0) } };
      entities.push(e);
    }
    out.entities = entities;
    return out;
  },
};

export function migrateMap(raw: Obj): Obj {
  let cur = raw;
  let v = typeof cur.formatVersion === 'number' ? cur.formatVersion : 1;
  while (v < MAP_FORMAT_VERSION) {
    const step = MIGRATIONS[v];
    if (!step) break;
    cur = step(cur);
    v = (cur.formatVersion as number) ?? v + 1;
  }
  return cur;
}

// ── structural check ────────────────────────────────────────────────────────────────────────────────────────────
const ID_RE = /^[A-Za-z0-9_.\-]{1,64}$/;

class Check {
  readonly issues: MapIssue[] = [];
  err(code: string, path: string, msg: string, extra: Partial<MapIssue> = {}): void { this.issues.push(mkIssue('ERROR', code, path, msg, extra)); }
  warn(code: string, path: string, msg: string, extra: Partial<MapIssue> = {}): void { this.issues.push(mkIssue('WARNING', code, path, msg, extra)); }
  num(v: unknown, path: string, o: { min?: number; max?: number; int?: boolean; opt?: boolean } = {}): v is number {
    if (v === undefined && o.opt) return false;
    if (typeof v !== 'number' || !Number.isFinite(v)) { this.err('STRUCT_NUMBER', path, 'expected a finite number'); return false; }
    if (o.int && !Number.isInteger(v)) { this.err('STRUCT_NUMBER', path, 'expected an integer'); return false; }
    if (o.min !== undefined && v < o.min) { this.err('STRUCT_RANGE', path, `must be ≥ ${o.min}`); return false; }
    if (o.max !== undefined && v > o.max) { this.err('STRUCT_RANGE', path, `must be ≤ ${o.max}`); return false; }
    return true;
  }
  str(v: unknown, path: string, o: { opt?: boolean; re?: RegExp; nonEmpty?: boolean } = {}): v is string {
    if (v === undefined && o.opt) return false;
    if (typeof v !== 'string') { this.err('STRUCT_STRING', path, 'expected a string'); return false; }
    if (o.nonEmpty && v.length === 0) { this.err('STRUCT_STRING', path, 'must not be empty'); return false; }
    if (o.re && !o.re.test(v)) { this.err('STRUCT_STRING', path, `does not match ${o.re}`); return false; }
    return true;
  }
  obj(v: unknown, path: string, opt = false): v is Obj {
    if (v === undefined && opt) return false;
    if (!isObj(v)) { this.err('STRUCT_OBJECT', path, 'expected an object'); return false; }
    return true;
  }
  arr(v: unknown, path: string, opt = false): v is unknown[] {
    if (v === undefined && opt) return false;
    if (!Array.isArray(v)) { this.err('STRUCT_ARRAY', path, 'expected an array'); return false; }
    return true;
  }
  vec2(v: unknown, path: string): boolean {
    if (!this.obj(v, path)) return false;
    const a = this.num(v.x, `${path}/x`), b = this.num(v.y, `${path}/y`);
    return a && b;
  }
  bool(v: unknown, path: string, opt = false): boolean {
    if (v === undefined && opt) return false;
    if (typeof v !== 'boolean') { this.err('STRUCT_BOOLEAN', path, 'expected a boolean'); return false; }
    return true;
  }
  oneOf(v: unknown, path: string, allowed: readonly string[], code = 'STRUCT_ENUM'): boolean {
    if (typeof v !== 'string' || !allowed.includes(v)) { this.err(code, path, `must be one of ${allowed.join(' | ')}`); return false; }
    return true;
  }
}

function checkShape(c: Check, s: unknown, path: string): void {
  if (!c.obj(s, path)) return;
  const kind = s.kind;
  if (!c.oneOf(kind, `${path}/kind`, ['box', 'sphere', 'capsule', 'convex', 'mesh', 'slope'])) return;
  switch (kind) {
    case 'box': c.num(s.w, `${path}/w`, { min: 0 }); c.num(s.h, `${path}/h`, { min: 0 }); c.num(s.taper, `${path}/taper`, { opt: true, min: 0, max: 1 }); break;
    case 'sphere': c.num(s.r, `${path}/r`, { min: 0 }); break;
    case 'capsule': c.num(s.r, `${path}/r`, { min: 0 }); c.num(s.length, `${path}/length`, { min: 0 }); break;
    case 'slope': c.num(s.w, `${path}/w`, { min: 0 }); c.num(s.h, `${path}/h`, { min: 0 }); break;
    case 'convex': if (c.arr(s.points, `${path}/points`)) (s.points as unknown[]).forEach((p, i) => c.vec2(p, `${path}/points/${i}`)); break;
    case 'mesh':
      if (s.outline !== undefined && c.arr(s.outline, `${path}/outline`)) (s.outline as unknown[]).forEach((p, i) => c.vec2(p, `${path}/outline/${i}`));
      if (s.polygons !== undefined && c.arr(s.polygons, `${path}/polygons`)) (s.polygons as unknown[]).forEach((poly, i) => { if (c.arr(poly, `${path}/polygons/${i}`)) (poly as unknown[]).forEach((p, j) => c.vec2(p, `${path}/polygons/${i}/${j}`)); });
      if (s.outline === undefined && s.polygons === undefined) c.err('STRUCT_SHAPE', path, 'mesh needs outline or polygons');
      break;
  }
}

function checkRegionShape(c: Check, s: unknown, path: string): void {
  if (!c.obj(s, path)) return;
  if (!c.oneOf(s.kind, `${path}/kind`, ['box', 'circle', 'polygon'])) return;
  if (s.kind === 'box') { c.num(s.x, `${path}/x`); c.num(s.y, `${path}/y`); c.num(s.w, `${path}/w`, { min: 0 }); c.num(s.h, `${path}/h`, { min: 0 }); }
  else if (s.kind === 'circle') { c.num(s.x, `${path}/x`); c.num(s.y, `${path}/y`); c.num(s.r, `${path}/r`, { min: 0 }); }
  else if (c.arr(s.points, `${path}/points`)) (s.points as unknown[]).forEach((p, i) => c.vec2(p, `${path}/points/${i}`));
}

function checkEntity(c: Check, e: unknown, path: string): void {
  if (!c.obj(e, path)) return;
  c.str(e.id, `${path}/id`, { re: ID_RE });
  c.oneOf(e.type, `${path}/type`, ENTITY_TYPES);
  if (c.obj(e.position, `${path}/position`)) { c.num(e.position.x, `${path}/position/x`); c.num(e.position.y, `${path}/position/y`); c.num(e.position.z, `${path}/position/z`, { opt: true }); }
  c.num(e.rotation, `${path}/rotation`, { opt: true });
  if (e.scale !== undefined && c.obj(e.scale, `${path}/scale`)) { c.num(e.scale.x, `${path}/scale/x`); c.num(e.scale.y, `${path}/scale/y`); }
  if (e.tags !== undefined && c.arr(e.tags, `${path}/tags`)) (e.tags as unknown[]).forEach((t, i) => c.str(t, `${path}/tags/${i}`));
  if (e.prefab !== undefined) c.str(e.prefab, `${path}/prefab`, { re: ID_RE });
  if (e.properties !== undefined) c.obj(e.properties, `${path}/properties`);
  if (e.visual !== undefined && c.obj(e.visual, `${path}/visual`)) c.oneOf(e.visual.kind, `${path}/visual/kind`, ['procedural', 'mesh', 'sprite', 'none']);
  const cols = e.collision === undefined || e.collision === null ? [] : Array.isArray(e.collision) ? e.collision : [e.collision];
  cols.forEach((col, i) => {
    const cp = Array.isArray(e.collision) ? `${path}/collision/${i}` : `${path}/collision`;
    if (c.obj(col, cp)) { checkShape(c, col.shape, `${cp}/shape`); if (col.oneWay !== undefined) c.oneOf(col.oneWay, `${cp}/oneWay`, ['up', 'down', 'left', 'right']); }
  });
  if (e.behavior !== undefined) {
    const bs = Array.isArray(e.behavior) ? e.behavior : [e.behavior];
    bs.forEach((b, i) => { const bp = Array.isArray(e.behavior) ? `${path}/behavior/${i}` : `${path}/behavior`; if (c.obj(b, bp)) c.oneOf(b.type, `${bp}/type`, BEHAVIOR_TYPES, 'STRUCT_BEHAVIOR'); });
  }
}

/** Structural issues of an arbitrary parsed value (all ERROR severity). Empty ⇒ it can be normalised to a MapDocument. */
export function structuralCheck(raw: unknown): MapIssue[] {
  const c = new Check();
  if (!c.obj(raw, '')) return c.issues;
  if (raw.format !== MAP_FORMAT) c.err('STRUCT_FORMAT', '/format', `format must be "${MAP_FORMAT}"`);
  if (c.num(raw.formatVersion, '/formatVersion', { int: true, min: 1 }) && raw.formatVersion > MAP_FORMAT_VERSION) {
    c.err('STRUCT_VERSION', '/formatVersion', `format version ${raw.formatVersion} is newer than supported (${MAP_FORMAT_VERSION})`);
  }
  if (c.obj(raw.manifest, '/manifest')) {
    const m = raw.manifest;
    c.str(m.id, '/manifest/id', { re: ID_RE }); c.str(m.name, '/manifest/name', { nonEmpty: true });
    c.str(m.version, '/manifest/version', { re: /^\d+\.\d+\.\d+([-+][A-Za-z0-9.\-]+)?$/ });
    c.num(m.difficulty, '/manifest/difficulty', { min: 1, max: 10 });
    c.num(m.estimatedTimeSec, '/manifest/estimatedTimeSec', { min: 0 });
    c.num(m.checkpointCount, '/manifest/checkpointCount', { min: 0, int: true });
    if (c.arr(m.tags, '/manifest/tags')) (m.tags as unknown[]).forEach((t, i) => c.str(t, `/manifest/tags/${i}`));
    if (c.obj(m.mapSize, '/manifest/mapSize')) { c.num(m.mapSize.width, '/manifest/mapSize/width', { min: 0 }); c.num(m.mapSize.height, '/manifest/mapSize/height', { min: 0 }); }
    c.str(m.theme, '/manifest/theme', { nonEmpty: true });
    if (c.obj(m.requirements, '/manifest/requirements')) {
      c.num(m.requirements.minFormatVersion, '/manifest/requirements/minFormatVersion', { int: true, min: 1 });
      c.str(m.requirements.physics, '/manifest/requirements/physics');
      c.arr(m.requirements.capabilities, '/manifest/requirements/capabilities');
    }
  }
  if (raw.world !== undefined && c.obj(raw.world, '/world')) {
    const w = raw.world;
    if (c.obj(w.bounds, '/world/bounds', true)) for (const k of ['minX', 'maxX', 'minY', 'maxY']) c.num(w.bounds[k], `/world/bounds/${k}`);
    c.num(w.killY, '/world/killY', { opt: true });
  }
  if (raw.spawn !== undefined && raw.spawn !== null && c.obj(raw.spawn, '/spawn')) { c.str(raw.spawn.id, '/spawn/id', { re: ID_RE }); c.vec2(raw.spawn.position, '/spawn/position'); }
  if (raw.finish !== undefined && c.obj(raw.finish, '/finish') && c.arr(raw.finish.zones, '/finish/zones')) {
    (raw.finish.zones as unknown[]).forEach((z, i) => { if (c.obj(z, `/finish/zones/${i}`)) { c.str(z.id, `/finish/zones/${i}/id`, { re: ID_RE }); c.vec2(z.position, `/finish/zones/${i}/position`); if (c.obj(z.shape, `/finish/zones/${i}/shape`)) c.oneOf(z.shape.kind, `/finish/zones/${i}/shape/kind`, ['box', 'polygon']); } });
  }
  if (raw.checkpoints !== undefined && c.arr(raw.checkpoints, '/checkpoints')) {
    (raw.checkpoints as unknown[]).forEach((k, i) => {
      const p = `/checkpoints/${i}`;
      if (!c.obj(k, p)) return;
      c.str(k.id, `${p}/id`, { re: ID_RE }); c.num(k.order, `${p}/order`, { int: true, min: 0 }); checkRegionShape(c, k.region, `${p}/region`); c.vec2(k.respawn, `${p}/respawn`);
      c.num(k.progress, `${p}/progress`, { opt: true, min: 0, max: 100 });
    });
  }
  if (raw.progress !== undefined && c.obj(raw.progress, '/progress') && c.arr(raw.progress.routes, '/progress/routes')) {
    (raw.progress.routes as unknown[]).forEach((r, i) => {
      const p = `/progress/routes/${i}`;
      if (!c.obj(r, p)) return;
      c.str(r.id, `${p}/id`, { re: ID_RE }); c.oneOf(r.kind, `${p}/kind`, ['main', 'optional', 'branch', 'secret']);
      if (c.arr(r.points, `${p}/points`)) (r.points as unknown[]).forEach((pt, j) => { if (c.vec2(pt, `${p}/points/${j}`)) c.num((pt as Obj).percent, `${p}/points/${j}/percent`, { opt: true, min: 0, max: 100 }); });
    });
  }
  if (raw.splits !== undefined && c.obj(raw.splits, '/splits')) {
    if (c.arr(raw.splits.splits, '/splits/splits')) (raw.splits.splits as unknown[]).forEach((s, i) => { if (c.obj(s, `/splits/splits/${i}`)) { c.str(s.id, `/splits/splits/${i}/id`, { re: ID_RE }); c.str(s.checkpoint, `/splits/splits/${i}/checkpoint`); } });
    if (c.obj(raw.splits.targets, '/splits/targets')) for (const k of ['gold', 'silver', 'bronze']) c.num(raw.splits.targets[k], `/splits/targets/${k}`, { min: 0 });
  }
  if (raw.paths !== undefined && c.arr(raw.paths, '/paths')) {
    (raw.paths as unknown[]).forEach((pa, i) => { if (c.obj(pa, `/paths/${i}`)) { c.str(pa.id, `/paths/${i}/id`, { re: ID_RE }); c.oneOf(pa.kind, `/paths/${i}/kind`, ['polyline', 'bezier', 'spline']); if (c.arr(pa.points, `/paths/${i}/points`)) (pa.points as unknown[]).forEach((pt, j) => c.vec2(pt, `/paths/${i}/points/${j}`)); } });
  }
  if (raw.entities !== undefined && c.arr(raw.entities, '/entities')) (raw.entities as unknown[]).forEach((e, i) => checkEntity(c, e, `/entities/${i}`));
  if (raw.regions !== undefined && c.arr(raw.regions, '/regions')) {
    (raw.regions as unknown[]).forEach((r, i) => { const p = `/regions/${i}`; if (!c.obj(r, p)) return; c.str(r.id, `${p}/id`, { re: ID_RE }); c.oneOf(r.type, `${p}/type`, REGION_TYPES); checkRegionShape(c, r.shape, `${p}/shape`); });
  }
  if (raw.chunks !== undefined && c.obj(raw.chunks, '/chunks')) {
    const k = raw.chunks;
    c.oneOf(k.mode, '/chunks/mode', ['auto-grid', 'explicit']);
    if (c.obj(k.cell, '/chunks/cell')) { c.num(k.cell.w, '/chunks/cell/w', { min: 1 }); c.num(k.cell.h, '/chunks/cell/h', { min: 1 }); }
    for (const f of ['activateRadius', 'loadRadius', 'unloadRadius']) c.num(k[f], `/chunks/${f}`, { min: 0 });
    c.num(k.maxActive, '/chunks/maxActive', { min: 1, int: true });
    if (c.arr(k.defs, '/chunks/defs')) (k.defs as unknown[]).forEach((d, i) => { if (c.obj(d, `/chunks/defs/${i}`)) { c.str(d.id, `/chunks/defs/${i}/id`, { re: ID_RE }); if (c.obj(d.bounds, `/chunks/defs/${i}/bounds`)) for (const f of ['minX', 'maxX', 'minY', 'maxY']) c.num(d.bounds[f], `/chunks/defs/${i}/bounds/${f}`); } });
  }
  return c.issues;
}

// ── parse / serialise ───────────────────────────────────────────────────────────────────────────────────────────
export interface ParseOptions {
  /** Return the document together with the structural issues instead of throwing (editor use). */
  lenient?: boolean;
}

/** Parse JSON text (or an already parsed object), migrate, check structure, normalise. Throws {@link MapLoadError}. */
export function parseMap(input: string | unknown, opts: ParseOptions = {}): { doc: MapDocument; issues: MapIssue[] } {
  let raw: unknown = input;
  if (typeof input === 'string') {
    try { raw = JSON.parse(input); } catch (e) { throw new MapLoadError([mkIssue('ERROR', 'STRUCT_JSON', '', `invalid JSON: ${(e as Error).message}`)]); }
  }
  if (!isObj(raw)) throw new MapLoadError([mkIssue('ERROR', 'STRUCT_OBJECT', '', 'map root must be an object')]);
  const migrated = migrateMap(clone(raw as Obj));
  const issues = structuralCheck(migrated);
  if (issues.length && !opts.lenient) throw new MapLoadError(issues);
  const doc = normalizeMap(migrated as Partial<MapDocument> & Obj);
  return { doc, issues };
}

const ENTITY_KEYS: (keyof MapEntity)[] = ['id', 'type', 'name', 'prefab', 'position', 'rotation', 'scale', 'tags', 'properties', 'visual', 'collision', 'behavior', 'chunk', 'enabled'];

function stripUndefined(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(stripUndefined);
  if (isObj(v)) {
    const o: Obj = {};
    for (const [k, x] of Object.entries(v)) { if (x === undefined) continue; o[k] = stripUndefined(x); }
    return o;
  }
  return v;
}

/** Canonical JSON: schema key order at top level and on entities, no undefined, 2-space indent, trailing newline. */
export function serializeMap(doc: MapDocument): string {
  const top: Obj = {};
  for (const k of DOC_KEYS) {
    const v = (doc as unknown as Obj)[k];
    if (v === undefined) continue;
    if (k === 'entities') top[k] = doc.entities.map(e => { const o: Obj = {}; for (const kk of ENTITY_KEYS) { const x = (e as unknown as Obj)[kk]; if (x !== undefined) o[kk] = x; } for (const kk of Object.keys(e)) if (!(kk in o) && (e as unknown as Obj)[kk] !== undefined) o[kk] = (e as unknown as Obj)[kk]; return o; });
    else top[k] = v;
  }
  return JSON.stringify(stripUndefined(top), null, 2) + '\n';
}
