/**
 * MapEditor — headless authoring API (SPEC §15). Every mutation is a command with an inverse (undo/redo); every property
 * is data, so no code is edited to build a moving platform. A visual editor is a client of this class.
 */
import type {
  BehaviorDef, CheckpointDef, CollisionDef, FinishZone, MapDocument, MapEntity, RegionDef, RouteDef, ThemeRef, Vec2, Json, ChunkingDef,
} from './schema';
import { cloneMap, newMapDocument, parseMap, serializeMap } from './MapLoader';
import { PrefabRegistry, resolveEntity } from './MapPrefab';
import { type BuildOptions, type BuildResult, buildPackage } from './MapPackage';
import { type ValidateOptions, validateMap } from './MapValidator';
import { type ValidationReport } from './MapIssue';
import { MapRuntime } from './MapRuntime';
import { PogoPhysicsController } from '../sim/PogoPhysicsController';
import { NEUTRAL_INPUT, type PogoInput } from '../sim/PogoState';
import { createPhysicsConfig } from '../sim/PhysicsConfig';
import { instancesOf, compileRenderLevel } from './MapCompile';
import { playRoute, type RoutePlan } from '../sim/routeBot';
import { rotatePoints } from '../sim/geometry';
import { DEG } from '../sim/math';

interface Command { label: string; redo(): void; undo(): void }

export interface PreviewOptions {
  ticks: number;
  /** Input per tick (default: no input — the pogo hops by itself). */
  input?: (tick: number) => PogoInput;
  /** false = stream chunks like the game does (default), true = load everything. */
  fullLoad?: boolean;
}
export interface PreviewResult {
  ticks: number; finished: boolean; finishedTick: number; deaths: number; jumps: number; progressMax: number; x: number; y: number;
  checkpoints: string[]; eventCounts: Record<string, number>;
}

export interface CreateOptions { id: string; name?: string; author?: string; theme?: string; bounds?: { minX: number; maxX: number; minY: number; maxY: number } }

export class MapEditor {
  doc: MapDocument;
  private registry: PrefabRegistry;
  private undoStack: Command[] = [];
  private redoStack: Command[] = [];
  private readonly MAX = 500;
  private index = new Map<string, MapEntity>();
  private counter = 0;

  constructor(doc: MapDocument) {
    this.doc = doc;
    this.registry = new PrefabRegistry(doc.prefabs);
    this.reindex();
  }

  static create(o: CreateOptions): MapEditor {
    const doc = newMapDocument(o.id);
    doc.manifest.name = o.name ?? o.id; doc.manifest.author = o.author ?? ''; doc.manifest.theme = o.theme ?? 'autumn_hills';
    doc.theme = { ref: doc.manifest.theme };
    if (o.bounds) { doc.world.bounds = { ...o.bounds }; doc.world.killY = o.bounds.minY - 10; }
    return new MapEditor(doc);
  }
  static open(input: string | unknown): MapEditor { return new MapEditor(parseMap(input, { lenient: true }).doc); }

  // ── plumbing ─────────────────────────────────────────────────────────────────────────────────────────────────
  private reindex(): void { this.index = new Map(this.doc.entities.map(e => [e.id, e])); }
  private run(c: Command): void {
    c.redo();
    this.undoStack.push(c); if (this.undoStack.length > this.MAX) this.undoStack.shift();
    this.redoStack.length = 0;
  }
  get history(): string[] { return this.undoStack.map(c => c.label); }
  get canUndo(): boolean { return this.undoStack.length > 0; }
  get canRedo(): boolean { return this.redoStack.length > 0; }
  undo(): boolean { const c = this.undoStack.pop(); if (!c) return false; c.undo(); this.redoStack.push(c); return true; }
  redo(): boolean { const c = this.redoStack.pop(); if (!c) return false; c.redo(); this.undoStack.push(c); return true; }
  entity(id: string): MapEntity | undefined { return this.index.get(id); }
  private need(id: string): MapEntity { const e = this.index.get(id); if (!e) throw new Error(`no entity "${id}"`); return e; }
  private freshId(base: string): string {
    for (;;) { const id = `${base}_${String(++this.counter).padStart(2, '0')}`; if (!this.index.has(id)) return id; }
  }
  /** Replace a property of an object with undo (captures the previous value; `undefined` deletes). */
  private setField<T extends object, K extends keyof T>(label: string, obj: T, key: K, value: T[K] | undefined, after?: () => void): void {
    const had = Object.prototype.hasOwnProperty.call(obj, key); const prev = obj[key];
    const set = (v: T[K] | undefined, present: boolean) => { if (present && v !== undefined) obj[key] = v as T[K]; else delete obj[key]; after?.(); };
    this.run({ label, redo: () => set(value, value !== undefined), undo: () => set(prev, had) });
  }

  // ── objects ──────────────────────────────────────────────────────────────────────────────────────────────────
  /** Place a prefab instance; returns the new entity id. */
  place(prefabId: string, position: { x: number; y: number; z?: number }, overrides: Partial<MapEntity> = {}): string {
    const chain = this.registry.chain(prefabId);
    if (!chain.length || chain[0].id === undefined || !this.registry.has(prefabId)) throw new Error(`unknown prefab "${prefabId}"`);
    const type = (overrides.type ?? chain.map(p => p.entity.type).filter(Boolean).pop() ?? 'decor') as MapEntity['type'];
    const id = overrides.id ?? this.freshId(prefabId);
    if (this.index.has(id)) throw new Error(`entity id "${id}" already exists`);
    const e: MapEntity = { ...overrides, id, type, prefab: prefabId, position: { ...position } };
    this.run({
      label: `place ${prefabId}`,
      redo: () => { this.doc.entities.push(e); this.index.set(id, e); },
      undo: () => { const i = this.doc.entities.lastIndexOf(e); if (i >= 0) this.doc.entities.splice(i, 1); this.index.delete(id); },
    });
    return id;
  }

  private forEntities(ids: string[], label: string, fn: (e: MapEntity) => (() => void)): void {
    const es = ids.map(id => this.need(id));
    let undos: (() => void)[] = [];
    this.run({
      label,
      redo: () => { undos = es.map(fn); },
      undo: () => { for (const u of undos.reverse()) u(); undos = []; },
    });
  }

  move(ids: string[], dx: number, dy: number): void {
    this.forEntities(ids, 'move', e => { const p = { ...e.position }; e.position = { ...e.position, x: e.position.x + dx, y: e.position.y + dy }; return () => { e.position = p; }; });
  }
  /** Rotate about `pivot` (default: each entity's own origin). */
  rotate(ids: string[], deg: number, pivot?: Vec2): void {
    this.forEntities(ids, 'rotate', e => {
      const p = { ...e.position }; const r = e.rotation;
      if (pivot) { const q = rotatePoints([{ x: e.position.x, y: e.position.y }], deg * DEG, pivot.x, pivot.y)[0]; e.position = { ...e.position, x: q.x, y: q.y }; }
      e.rotation = (e.rotation ?? 0) + deg;
      return () => { e.position = p; if (r === undefined) delete e.rotation; else e.rotation = r; };
    });
  }
  scale(ids: string[], sx: number, sy = sx): void {
    this.forEntities(ids, 'scale', e => { const s = e.scale; e.scale = { x: (e.scale?.x ?? 1) * sx, y: (e.scale?.y ?? 1) * sy }; return () => { if (s === undefined) delete e.scale; else e.scale = s; }; });
  }
  duplicate(ids: string[], dx = 1, dy = 0): string[] {
    const copies = ids.map(id => { const src = this.need(id); const c = JSON.parse(JSON.stringify(src)) as MapEntity; c.id = this.freshId(src.prefab ?? src.id.replace(/_\d+$/, '')); c.position = { ...c.position, x: c.position.x + dx, y: c.position.y + dy }; delete c.chunk; return c; });
    this.run({
      label: 'duplicate',
      redo: () => { for (const c of copies) { this.doc.entities.push(c); this.index.set(c.id, c); } },
      undo: () => { for (const c of copies) { const i = this.doc.entities.lastIndexOf(c); if (i >= 0) this.doc.entities.splice(i, 1); this.index.delete(c.id); } },
    });
    return copies.map(c => c.id);
  }
  remove(ids: string[]): void {
    const removed = ids.map(id => ({ e: this.need(id), at: this.doc.entities.indexOf(this.need(id)) })).sort((a, b) => a.at - b.at);
    this.run({
      label: 'delete',
      redo: () => { for (const { e } of [...removed].reverse()) { const i = this.doc.entities.indexOf(e); if (i >= 0) this.doc.entities.splice(i, 1); this.index.delete(e.id); } },
      undo: () => { for (const { e, at } of removed) { this.doc.entities.splice(Math.min(at, this.doc.entities.length), 0, e); this.index.set(e.id, e); } },
    });
  }

  setCollision(id: string, def: CollisionDef | CollisionDef[] | null): void { const e = this.need(id); this.setField('set collision', e, 'collision', def === null ? null : def); }
  setMaterial(id: string, material: string): void {
    const e = this.need(id);
    const prev = e.visual ? JSON.parse(JSON.stringify(e.visual)) : undefined;
    this.run({
      label: 'set material',
      redo: () => { e.visual = { ...(e.visual ?? { kind: 'procedural' }), material }; },
      undo: () => { if (prev === undefined) delete e.visual; else e.visual = prev; },
    });
  }
  setBehavior(id: string, def: BehaviorDef | BehaviorDef[] | null): void { const e = this.need(id); this.setField('set behavior', e, 'behavior', def === null ? undefined : def); }
  setProperty(id: string, key: string, value: Json): void {
    const e = this.need(id);
    const had = e.properties && key in e.properties; const prev = e.properties?.[key]; const hadProps = !!e.properties;
    this.run({
      label: `set ${key}`,
      redo: () => { e.properties = { ...(e.properties ?? {}), [key]: value }; },
      undo: () => { if (!hadProps) delete e.properties; else if (had) e.properties = { ...e.properties!, [key]: prev as Json }; else { const p = { ...e.properties! }; delete p[key]; e.properties = p; } },
    });
  }
  setTags(id: string, tags: string[]): void { this.setField('set tags', this.need(id), 'tags', [...tags]); }
  setVisual(id: string, visual: MapEntity['visual']): void { this.setField('set visual', this.need(id), 'visual', visual); }

  // ── regions / triggers ───────────────────────────────────────────────────────────────────────────────────────
  addRegion(def: RegionDef): void {
    if (this.doc.regions.some(r => r.id === def.id)) throw new Error(`region "${def.id}" already exists`);
    this.run({ label: `add region ${def.type}`, redo: () => { this.doc.regions.push(def); }, undo: () => { const i = this.doc.regions.indexOf(def); if (i >= 0) this.doc.regions.splice(i, 1); } });
  }
  removeRegion(id: string): void {
    const r = this.doc.regions.find(x => x.id === id); if (!r) throw new Error(`no region "${id}"`);
    const at = this.doc.regions.indexOf(r);
    this.run({ label: 'remove region', redo: () => { this.doc.regions.splice(this.doc.regions.indexOf(r), 1); }, undo: () => { this.doc.regions.splice(at, 0, r); } });
  }
  /** Trigger zone = a region of type "trigger" (or any type) with enter effects. */
  setTrigger(id: string, shape: RegionDef['shape'], enter: RegionDef['enter'], type: RegionDef['type'] = 'trigger'): void {
    this.addRegion({ id, type, shape, enter });
  }

  // ── progress / checkpoints / spawn / finish ──────────────────────────────────────────────────────────────────
  setProgressRoute(route: RouteDef): void {
    const routes = this.doc.progress.routes; const prev = routes.find(r => r.id === route.id); const at = prev ? routes.indexOf(prev) : -1;
    this.run({
      label: 'set route',
      redo: () => { if (at >= 0) routes[at] = route; else routes.push(route); },
      undo: () => { if (at >= 0) routes[at] = prev!; else routes.splice(routes.indexOf(route), 1); },
    });
  }
  setCheckpoint(def: CheckpointDef): void {
    const list = this.doc.checkpoints; const prev = list.find(c => c.id === def.id); const at = prev ? list.indexOf(prev) : -1;
    const count = this.doc.manifest.checkpointCount;
    this.run({
      label: 'set checkpoint',
      redo: () => { if (at >= 0) list[at] = def; else list.push(def); this.doc.manifest.checkpointCount = list.length; },
      undo: () => { if (at >= 0) list[at] = prev!; else list.splice(list.indexOf(def), 1); this.doc.manifest.checkpointCount = count; },
    });
  }
  removeCheckpoint(id: string): void {
    const list = this.doc.checkpoints; const c = list.find(x => x.id === id); if (!c) throw new Error(`no checkpoint "${id}"`);
    const at = list.indexOf(c); const count = this.doc.manifest.checkpointCount;
    this.run({ label: 'remove checkpoint', redo: () => { list.splice(list.indexOf(c), 1); this.doc.manifest.checkpointCount = list.length; }, undo: () => { list.splice(at, 0, c); this.doc.manifest.checkpointCount = count; } });
  }
  setSpawn(position: Vec2, facing?: 1 | -1): void { this.setField('set spawn', this.doc, 'spawn', { id: this.doc.spawn?.id ?? 'spawn', position: { ...position }, ...(facing ? { facing } : {}) }); }
  setFinish(zones: FinishZone[]): void { this.setField('set finish', this.doc, 'finish', { zones: zones.map(z => ({ ...z })) }); }
  setTheme(ref: ThemeRef): void {
    const prevTheme = this.doc.theme, prevManifest = this.doc.manifest.theme;
    this.run({
      label: 'set theme',
      redo: () => { this.doc.theme = ref; if ('ref' in ref && ref.ref) this.doc.manifest.theme = ref.ref; },
      undo: () => { this.doc.theme = prevTheme; this.doc.manifest.theme = prevManifest; },
    });
  }
  setChunking(c: Partial<ChunkingDef>): void { const prev = this.doc.chunks; this.run({ label: 'set chunking', redo: () => { this.doc.chunks = { ...prev, ...c }; }, undo: () => { this.doc.chunks = prev; } }); }

  // ── preview / validate / build / export ──────────────────────────────────────────────────────────────────────
  validate(opts?: ValidateOptions): ValidationReport { return validateMap(this.doc, opts); }

  /** Compile and run the real physics for `ticks` (no input ⇒ the pogo hops in place). */
  preview(o: PreviewOptions): PreviewResult {
    const cfg = createPhysicsConfig();
    const rt = new MapRuntime(cloneMap(this.doc), { cfg, streaming: !o.fullLoad });
    const pogo = new PogoPhysicsController(rt.world, cfg);
    const counts: Record<string, number> = {};
    let finishedTick = -1, i = 0;
    for (; i < o.ticks; i++) {
      rt.beforeStep(pogo.state);
      const ev = pogo.step(o.input ? o.input(i) : NEUTRAL_INPUT);
      for (const m of rt.afterStep(pogo.state, ev)) counts[m.type] = (counts[m.type] ?? 0) + 1;
      if (pogo.state.mode === 'FINISHED') { finishedTick = i; break; }
    }
    const s = pogo.state;
    return { ticks: i, finished: s.mode === 'FINISHED', finishedTick, deaths: s.hazards + s.falls, jumps: s.jumps, progressMax: rt.progress.max, x: s.x, y: s.y, checkpoints: [...rt.checkpoints.reached], eventCounts: counts };
  }

  /** Let the route bot play the declared route (slow on big maps — use on small ones). */
  previewBot(): RoutePlan { return playRoute(compileRenderLevel(this.doc, this.registry), createPhysicsConfig(), { maxExpansions: 150 }); }

  build(opts?: BuildOptions): BuildResult { return buildPackage(this.doc, opts); }
  exportJson(): string { return serializeMap(this.doc); }
  /** Resolved view of an entity (what the runtime will see). */
  resolved(id: string) { return resolveEntity(this.need(id), this.registry).entity; }
  instances() { return instancesOf(this.doc, this.registry); }
}
