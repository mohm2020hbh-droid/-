/**
 * MapRuntime — runs a map next to the physics core (SPEC §4).
 *
 *   constructor      core data (spawn, finish, regions, checkpoints, progress, paths, theme) + a ChunkSource
 *   beforeStep(s)    stream chunks for the player position · evaluate behaviours for tick s.tick+1 · update collider
 *                    activity / poses (MapWorld) — pure functions of (tick, counters, flags)
 *   afterStep(s, ev) breakable arming · zones & regions · checkpoints · progress · run statistics · finish
 *
 * The physics core is never modified. The runtime's only writes into PogoState are (a) the respawn anchor
 * (`safeGround/safeLx/safeLy/safeNx/safeNy`, DESIGN-class fields) and (b) player moves done through the core's own
 * `respawn()` (kill / teleport effects).
 */
import type { LevelData } from '../data/LevelData';
import type { PhysicsConfig } from '../sim/PhysicsConfig';
import { createPhysicsConfig } from '../sim/PhysicsConfig';
import { WORLD_Q_PER_M } from '../sim/PhysicsWorld';
import { type PogoState, syncPresentation, tipCenterQ } from '../sim/PogoState';
import { respawn } from '../sim/core/step';
import type { SimEvent } from '../sim/events';
import { TICK_RATE } from '../sim/math';
import type { Effect, Json, MapDocument, MapEntity, MapEvent, MapEventType, RegionDef, Vec2 } from './schema';
import { MapWorld } from './MapWorld';
import { PrefabRegistry, resolveEntity } from './MapPrefab';
import { type EntityInstance, buildInstance } from './MapEntity';
import { type Curve, type BehaviorView, buildCurve, evalGate } from './MapBehavior';
import { placePolys, polysAabb } from './MapCollision';
import { type ChunkData, type ChunkInfo, type ChunkSource, ChunkManager, type InstanceBatch, buildBatches, documentChunkSource, policyOf } from './MapChunk';
import { ProgressTracker, buildProgress } from './MapProgress';
import { CheckpointTracker, type CheckpointHit, type CheckpointSkip, type RunStore, RunRecorder, type RunSummary } from './MapCheckpoint';
import { regionAabb, regionContains } from './MapRegion';
import { resolveTheme } from './MapTheme';
import type { MapIssue } from './MapIssue';

export interface MapHost {
  /** Called for effects the runtime cannot perform itself (camera, vfx, audio, lighting, fog, hint, emit, reveal…). */
  onEffect?(type: MapEventType, data: Json | undefined, ev: MapEvent): void;
}

export interface MapRuntimeOptions {
  cfg?: PhysicsConfig;
  registry?: PrefabRegistry;
  store?: RunStore;
  host?: MapHost;
  /** false ⇒ every chunk is loaded and active from the start (reference mode for determinism tests). Default true. */
  streaming?: boolean;
  source?: ChunkSource;
}

interface ActiveInst {
  inst: EntityInstance;
  chunk: string;
  /** Collider slot per piece (same order as inst.pieces). */
  slots: number[];
  gate: boolean;
  broken: boolean;
  brokenAt: number;
  armedAt: number;
  oneWayOn: boolean[];
}

interface LoadedChunk { id: string; info: ChunkInfo; items: ActiveInst[]; instances: EntityInstance[]; batches: InstanceBatch[]; active: boolean }

export interface EntityVisualState { visible: 'visible' | 'ghost' | 'hidden'; blink: boolean; broken: boolean; angle: number }

const GRID = 32;

export class MapRuntime {
  readonly doc: MapDocument;
  readonly cfg: PhysicsConfig;
  readonly registry: PrefabRegistry;
  readonly paths = new Map<string, Curve>();
  readonly world: MapWorld;
  readonly chunks: ChunkManager;
  readonly progress: ProgressTracker;
  readonly checkpoints: CheckpointTracker;
  readonly recorder: RunRecorder;
  readonly flags = new Map<string, number | boolean>();
  readonly events: MapEvent[] = [];
  readonly loadIssues: MapIssue[] = [];
  /** Visual state of gated / rotating entities for the renderer (entity id → state). */
  readonly visualState = new Map<string, EntityVisualState>();
  summary: RunSummary | null = null;
  kills = 0;
  finished = false;

  private readonly host: MapHost;
  private readonly loaded = new Map<string, LoadedChunk>();
  private readonly slotTable = new Map<string, number[]>();
  private readonly slotOwner = new Map<number, string>();
  private activeList: ActiveInst[] = [];
  private activeDirty = true;
  private readonly reached = new Set<string>();
  private readonly regions: RegionDef[];
  private readonly regionGrid = new Map<string, number[]>();
  private readonly regionInside = new Set<number>();
  private readonly regionFired = new Set<number>();
  private readonly zoneInside = new Set<string>();
  private pinnedChunk: string | null = null;
  private readonly view: BehaviorView;
  private readonly tip = { x: 0, y: 0 };
  private readonly tmpEv: SimEvent[] = [];
  private tick = 0;

  constructor(core: MapDocument, opts: MapRuntimeOptions = {}) {
    this.doc = core;
    this.cfg = opts.cfg ?? createPhysicsConfig();
    this.registry = opts.registry ?? new PrefabRegistry(core.prefabs);
    this.host = opts.host ?? {};
    for (const p of core.paths) this.paths.set(p.id, buildCurve(p));
    this.regions = core.regions;
    this.regions.forEach((r, i) => {
      const b = regionAabb(r.shape);
      for (let gx = Math.floor(b.minX / GRID); gx <= Math.floor(b.maxX / GRID); gx++) for (let gy = Math.floor(b.minY / GRID); gy <= Math.floor(b.maxY / GRID); gy++) {
        const k = `${gx},${gy}`; const l = this.regionGrid.get(k); if (l) l.push(i); else this.regionGrid.set(k, [i]);
      }
    });
    this.progress = new ProgressTracker(buildProgress(core.progress));
    this.checkpoints = new CheckpointTracker(core.checkpoints);
    this.recorder = new RunRecorder(core.manifest.id, core.manifest.version, core.splits, opts.store);
    this.view = { tick: 0, jumps: 0, boosts: 0, deaths: 0, checkpoints: 0, flags: this.flags, reached: this.reached };

    const spawn = core.spawn;
    const g = core.finish.zones[0];
    const shell: LevelData = {
      levelId: core.manifest.id, worldId: 'map', name: core.manifest.name, theme: resolveTheme(core.theme, core.manifest.theme).theme.id,
      startPosition: spawn ? { ...spawn.position } : { x: 0, y: 0 },
      goalPosition: g ? { ...g.position } : { x: 0, y: 0 },
      goal: g && g.shape.kind === 'box' ? { x: g.position.x, y: g.position.y - g.shape.h / 2, w: g.shape.w, h: g.shape.h } : { x: 0, y: 0, w: 1, h: 1 },
      platforms: [], obstacles: [], hazards: [], movingObjects: [], specialSurfaces: [],
      difficulty: core.manifest.difficulty,
      progress: { path: (core.progress.routes.find(r => r.kind === 'main')?.points ?? []).map(p => ({ x: p.x, y: p.y })) },
      bounds: { ...core.world.bounds }, killY: core.world.killY, parTimeSec: core.splits.targets.gold, landmarks: [], hints: [],
    };
    this.world = new MapWorld(shell, opts.cfg?.qPerMetre ?? WORLD_Q_PER_M);

    // resident colliders: finish zones (goal triggers)
    core.finish.zones.forEach(z => {
      const local = z.shape.kind === 'box'
        ? [{ x: -z.shape.w / 2, y: -z.shape.h / 2 }, { x: z.shape.w / 2, y: -z.shape.h / 2 }, { x: z.shape.w / 2, y: z.shape.h / 2 }, { x: -z.shape.w / 2, y: z.shape.h / 2 }]
        : z.shape.points;
      const pts = local.map(p => ({ x: p.x + z.position.x, y: p.y + z.position.y }));
      const [slot] = this.world.reserve(1);
      this.world.install(slot, { id: z.id, kind: 'goal', surface: 'goal', material: 'goal', safe: false, pts }, true);
    });

    const source = opts.source ?? documentChunkSource(core, e => this.extentOf(e));
    const streaming = opts.streaming !== false;
    const policy = policyOf(core.chunks);
    this.chunks = new ChunkManager(source, streaming ? policy : { ...policy, activateRadius: Infinity, loadRadius: Infinity, unloadRadius: Infinity }, {
      load: (info, data) => this.onLoad(info, data),
      activate: id => this.onActivate(id),
      deactivate: id => this.onDeactivate(id),
      unload: id => this.onUnload(id),
    });
    // spawn chunk(s) are pinned and everything near the spawn is ready before the first step
    const sp = spawn?.position ?? { x: 0, y: 0 };
    for (const id of this.chunks.chunksAt(sp.x, sp.y)) this.chunks.pin(id);
    this.chunks.update(sp.x, sp.y);
    this.applyBehaviors(1, null);
    this.world.flush();
  }

  // ── helpers ──────────────────────────────────────────────────────────────────────────────────────────────────
  private extentOf(e: MapEntity) {
    const { entity } = resolveEntity(e, this.registry);
    return buildInstance(entity, this.paths).extent;
  }

  /** Resolve + compile one entity (never throws; problems go to `loadIssues`). */
  private makeInstance(e: MapEntity, path: string): EntityInstance {
    const { entity, issues } = resolveEntity(e, this.registry, path);
    const inst = buildInstance(entity, this.paths, path);
    for (const i of issues) this.loadIssues.push(i);
    for (const i of inst.problems) this.loadIssues.push(i);
    return inst;
  }

  private emit(type: MapEventType, id?: string, data?: Json, x?: number, y?: number): MapEvent {
    const ev: MapEvent = { type, tick: this.tick };
    if (id !== undefined) ev.id = id;
    if (data !== undefined) ev.data = data;
    if (x !== undefined) { ev.x = x; ev.y = y; }
    this.events.push(ev);
    return ev;
  }

  // ── chunk hooks ──────────────────────────────────────────────────────────────────────────────────────────────
  private onLoad(info: ChunkInfo, data: ChunkData): void {
    const instances = data.entities.map((e, i) => this.makeInstance(e, `/chunks/${info.id}/entities/${i}`));
    const total = instances.reduce((n, i) => n + i.pieces.length, 0);
    let slots = this.slotTable.get(info.id);
    if (!slots || slots.length !== total) { slots = this.world.reserve(total); this.slotTable.set(info.id, slots); for (const s of slots) this.slotOwner.set(s, info.id); }
    const items: ActiveInst[] = [];
    let cursor = 0;
    for (const inst of instances) {
      const my: number[] = [];
      inst.pieces.forEach(p => {
        const slot = slots![cursor++];
        const r = inst.r;
        const pts = placePolys([p.local], r.x, r.y, r.rotation, r.sx, r.sy)[0];
        let rot: { pivot: Vec2 } | null = null;
        if (inst.rotation && inst.rotation.axis === 'z') rot = { pivot: placePolys([[inst.rotation.pivot]], r.x, r.y, r.rotation, r.sx, r.sy)[0][0] };
        this.world.install(slot, { id: p.id, kind: p.kind, surface: p.surface, material: p.material, safe: p.safe, pts, motion: inst.motion, rot }, false);
        my.push(slot);
      });
      items.push({ inst, chunk: info.id, slots: my, gate: true, broken: false, brokenAt: -1, armedAt: -1, oneWayOn: inst.pieces.map(() => true) });
    }
    this.loaded.set(info.id, { id: info.id, info, items, instances, batches: buildBatches(instances).batches, active: false });
    this.emit('chunk_load', info.id);
  }

  private onActivate(id: string): void {
    const c = this.loaded.get(id);
    if (!c) return;
    c.active = true; this.activeDirty = true;
    this.emit('chunk_activate', id);
  }

  private onDeactivate(id: string): void {
    const c = this.loaded.get(id);
    if (!c) return;
    c.active = false; this.activeDirty = true;
    for (const a of c.items) for (const s of a.slots) this.world.setActive(s, false);
    this.emit('chunk_deactivate', id);
  }

  private onUnload(id: string): void {
    const c = this.loaded.get(id);
    if (!c) return;
    for (const a of c.items) for (const s of a.slots) this.world.uninstall(s);
    for (const a of c.items) this.visualState.delete(a.inst.r.id);
    this.loaded.delete(id); this.activeDirty = true;
    this.emit('chunk_unload', id);
  }

  private rebuildActive(): void {
    this.activeList = [];
    for (const id of [...this.loaded.keys()].sort()) { const c = this.loaded.get(id)!; if (c.active) for (const a of c.items) this.activeList.push(a); }
    this.activeDirty = false;
  }

  // ── per-tick API ────────────────────────────────────────────────────────────────────────────────────────────
  /** Prepare the world for the physics tick that is about to run (`s.tick + 1`). */
  beforeStep(s: PogoState): void {
    this.events.length = 0;
    this.tick = s.tick;
    this.chunks.update(s.x, s.y);
    this.applyBehaviors(s.tick + 1, s);
    this.world.flush();
  }

  private deaths(s: PogoState | null): number { return s ? s.hazards + s.falls : 0; }

  private applyBehaviors(tick: number, s: PogoState | null): void {
    if (this.activeDirty) this.rebuildActive();
    const v = this.view;
    v.tick = tick;
    v.jumps = s?.jumps ?? 0; v.boosts = s?.boosts ?? 0; v.deaths = this.deaths(s); v.checkpoints = this.checkpoints.count;
    if (s) tipCenterQ(this.cfg, s, this.tip);
    const tipX = this.tip.x / this.cfg.qPerMetre, tipY = (this.tip.y - this.cfg.tipRadius) / this.cfg.qPerMetre;
    const off = { x: 0, y: 0, vx: 0, vy: 0 };
    for (const a of this.activeList) {
      const inst = a.inst;
      // breakable timers
      const bk = inst.breakable;
      if (bk) {
        if (!a.broken && a.armedAt >= 0 && tick >= a.armedAt + Math.round((bk.delay ?? 0.4) * TICK_RATE)) { a.broken = true; a.brokenAt = tick; this.emit('break', inst.r.id); }
        else if (a.broken && (bk.respawn ?? 0) > 0 && tick >= a.brokenAt + Math.round((bk.respawn ?? 0) * TICK_RATE)) { a.broken = false; a.armedAt = -1; this.emit('restore', inst.r.id); }
      }
      // gate
      let on = !a.broken;
      let blink = false;
      let visible: EntityVisualState['visible'] = 'visible';
      if (inst.gates.length) {
        const g = evalGate(inst.gates[0], v);
        if (g.active !== a.gate) { a.gate = g.active; this.emit('toggle', inst.r.id, g.active); }
        on = on && g.active; blink = g.blink;
        visible = g.active ? 'visible' : g.inactive.visual;
      } else if (a.broken) visible = 'hidden';
      // rotation (collision rotates only about the depth axis)
      let angle = 0;
      if (inst.rotation) {
        angle = inst.rotation.angle(tick);
        if (inst.rotation.axis === 'z') for (const sl of a.slots) this.world.setAngle(sl, angle);
      }
      // pieces: one-way gating then activity
      for (let i = 0; i < a.slots.length; i++) {
        let pieceOn = on;
        const ow = inst.pieces[i].oneWay;
        if (ow && pieceOn && s) pieceOn = this.oneWayAllows(a, i, ow, tipX, tipY, tick, off);
        this.world.setActive(a.slots[i], pieceOn);
      }
      if (inst.gates.length || inst.breakable || inst.rotation) this.visualState.set(inst.r.id, { visible, blink, broken: a.broken, angle });
    }
  }

  /** One-way (map layer, SPEC §9.4): active while the player's tip is on the allowed side; 0.25 m hysteresis keeps it solid under a rider. */
  private oneWayAllows(a: ActiveInst, i: number, dir: string, tipX: number, tipBottomY: number, tick: number, off: { x: number; y: number; vx: number; vy: number }): boolean {
    const c = this.world.colliders[a.slots[i]];
    this.world.offsetAt(c, tick, off);
    if (dir !== 'up') return true;                       // other directions are accepted by the schema but not gated yet (validator INFO)
    const px = tipX - off.x;
    const { pts, eny } = c.poly;
    let top = -Infinity;
    for (let k = 0; k < pts.length; k++) {
      if (eny[k] < 0.2) continue;
      const p = pts[k], q = pts[(k + 1) % pts.length];
      const lo = Math.min(p.x, q.x), hi = Math.max(p.x, q.x);
      if (px < lo - 0.3 || px > hi + 0.3 || hi - lo < 1e-9) continue;
      const t = Math.max(0, Math.min(1, (px - p.x) / (q.x - p.x)));
      top = Math.max(top, p.y + (q.y - p.y) * t + off.y);
    }
    if (top === -Infinity) return a.oneWayOn[i];
    const was = a.oneWayOn[i];
    const now = was ? tipBottomY >= top - 0.25 : tipBottomY >= top;
    a.oneWayOn[i] = now;
    return now;
  }

  /** Process the result of the physics tick that just ran. Returns the map events of this tick (reused array). */
  afterStep(s: PogoState, simEvents: readonly SimEvent[]): readonly MapEvent[] {
    this.tick = s.tick;
    const x = s.x, y = s.y;
    // breakables: arm on contact events
    for (const e of simEvents) {
      if ((e.type === 'land' || e.type === 'wall_hit' || e.type === 'bounce') && e.collider !== undefined) {
        const owner = this.slotOwner.get(e.collider);
        const c = owner ? this.loaded.get(owner) : undefined;
        if (!c) continue;
        for (const a of c.items) {
          const bk = a.inst.breakable;
          if (!bk || a.armedAt >= 0 || !a.slots.includes(e.collider)) continue;
          if (bk.trigger === 'land' && e.type !== 'land') continue;
          a.armedAt = s.tick;
        }
      }
    }
    // progress & checkpoints
    this.progress.update(x, y);
    const hits: CheckpointHit[] = [], skips: CheckpointSkip[] = [];
    this.checkpoints.update(x, y, s.tick, hits, skips);
    for (const h of hits) this.onCheckpoint(s, h.def.id);
    for (const k of skips) this.emit('checkpoint_skipped', k.def.id, { expected: k.expected });
    // regions + entity zones
    this.testRegions(s, x, y);
    this.testZones(s, x, y);
    // keep the respawn anchor's chunk resident
    this.pinSafeChunk(s);
    // finish
    if (!this.finished && s.finishedTick >= 0) {
      this.finished = true;
      this.summary = this.recorder.summarize({ startedTick: s.startedTick, finishedTick: s.finishedTick, jumps: s.jumps, deaths: this.deaths(s), progressMax: this.progress.max });
      this.emit('finish', undefined, { timeSec: this.summary.timeSec, medal: this.summary.medal }, x, y);
    }
    return this.events;
  }

  private onCheckpoint(s: PogoState, id: string): void {
    const def = this.doc.checkpoints.find(c => c.id === id)!;
    this.reached.add(id);
    const split = this.recorder.onCheckpoint(id, s.tick, s.startedTick);
    if (def.progress !== undefined) this.progress.pin(def.progress);
    const anchored = this.setAnchor(s, def.respawn.x, def.respawn.y);
    const pct = this.progress.max;
    this.emit('checkpoint', id, { order: def.order, anchored, percent: pct, deltaToPB: split?.deltaToPB ?? null, timeSec: split?.timeSec ?? null }, s.x, s.y);
    if (split) this.emit('split', split.id, { timeSec: split.timeSec, segmentSec: split.segmentSec, deltaToPB: split.deltaToPB, deltaToPar: split.deltaToPar });
  }

  /** Load + activate the chunks around a point regardless of distance (teleport targets) and refresh collider activity. */
  private ensureActiveAround(x: number, y: number, s: PogoState): void {
    for (const id of this.chunks.chunksAt(x, y)) this.chunks.ensureLoaded(id, true);
    this.applyBehaviors(s.tick + 1, s);
    this.world.flush();
  }

  /** Move the respawn anchor to the nearest ground around (x, y). Returns false if there is none (anchor unchanged). */
  setAnchor(s: PogoState, x: number, y: number): boolean {
    const g = this.world.findGround(x, y, 3);
    if (!g) return false;
    const k = this.cfg.qPerMetre;
    s.safeGround = g.collider.index; s.safeLx = g.x * k; s.safeLy = g.y * k; s.safeNx = g.nx; s.safeNy = g.ny;
    return true;
  }

  private pinSafeChunk(s: PogoState): void {
    const owner = this.slotOwner.get(s.safeGround) ?? null;
    if (owner !== this.pinnedChunk) {
      if (this.pinnedChunk) this.chunks.unpin(this.pinnedChunk);
      if (owner) this.chunks.pin(owner);
      this.pinnedChunk = owner;
    }
  }

  // ── regions / zones / effects ────────────────────────────────────────────────────────────────────────────────
  private testRegions(s: PogoState, x: number, y: number): void {
    const cand = this.regionGrid.get(`${Math.floor(x / GRID)},${Math.floor(y / GRID)}`) ?? [];
    const now = new Set<number>();
    for (const i of cand) {
      const r = this.regions[i];
      if (r.enabled === false || (r.once && this.regionFired.has(i))) continue;
      if (regionContains(r.shape, x, y)) now.add(i);
    }
    for (const i of now) {
      const r = this.regions[i];
      if (!this.regionInside.has(i)) { this.emit('zone_enter', r.id, { type: r.type }, x, y); if (r.once) this.regionFired.add(i); this.runEffects(r.enter, s, r.id); }
      else this.runEffects(r.stay, s, r.id);
    }
    for (const i of [...this.regionInside]) {
      if (now.has(i)) continue;
      const r = this.regions[i];
      this.emit('zone_exit', r.id, { type: r.type }, x, y);
      this.runEffects(r.exit, s, r.id);
    }
    this.regionInside.clear();
    for (const i of now) this.regionInside.add(i);
  }

  private testZones(s: PogoState, x: number, y: number): void {
    if (this.activeDirty) this.rebuildActive();
    for (const a of this.activeList) {
      for (const z of a.inst.zones) {
        const inside = pointInPoly(z.poly, x, y);
        const key = `${a.chunk}/${z.id}`;
        if (inside && !this.zoneInside.has(key)) { this.zoneInside.add(key); this.emit('zone_enter', z.id, { type: 'trigger' }, x, y); this.runEffects(z.enter, s, z.id); }
        else if (!inside && this.zoneInside.has(key)) { this.zoneInside.delete(key); this.emit('zone_exit', z.id, { type: 'trigger' }, x, y); }
      }
    }
  }

  private runEffects(list: Effect[] | undefined, s: PogoState, source: string): void {
    if (!list) return;
    for (const fx of list) this.applyEffect(fx, s, source);
  }

  applyEffect(fx: Effect, s: PogoState, source: string): void {
    switch (fx.op) {
      case 'kill': this.kill(s); this.emit('kill', source, null, s.x, s.y); break;
      case 'teleport': {
        const to = 'spawn' in fx.to ? (this.doc.spawn?.position ?? { x: 0, y: 0 }) : fx.to;
        this.ensureActiveAround(to.x, to.y, s);                                  // the target area must exist before we look for ground there
        const ok = this.setAnchor(s, to.x, to.y);
        if (ok) { this.tmpEv.length = 0; respawn({ world: this.world, cfg: this.cfg }, s, this.tmpEv); syncPresentation(s, this.cfg); }
        this.emit('teleport', source, { x: to.x, y: to.y, ok }, to.x, to.y);
        break;
      }
      case 'setCheckpoint': {
        const def = this.checkpoints.force(fx.id);
        if (def) this.onCheckpoint(s, def.id);
        break;
      }
      case 'setFlag': this.flags.set(fx.name, fx.value); this.emit('flag', fx.name, fx.value); break;
      case 'incCounter': { const cur = Number(this.flags.get(fx.name) ?? 0) || 0; const v = cur + (fx.by ?? 1); this.flags.set(fx.name, v); this.emit('flag', fx.name, v); break; }
      case 'reveal': this.flags.set(`reveal:${fx.tag}`, true); this.emit('reveal', fx.tag); break;
      case 'camera': this.hostEffect('camera', source, fx as unknown as Json); break;
      case 'vfx': this.hostEffect('vfx', source, fx as unknown as Json); break;
      case 'audio': this.hostEffect('audio', source, fx as unknown as Json); break;
      case 'lighting': this.hostEffect('lighting', source, fx as unknown as Json); break;
      case 'fog': this.hostEffect('fog', source, fx as unknown as Json); break;
      case 'hint': this.hostEffect('hint', source, fx as unknown as Json); break;
      case 'emit': {
        const type: MapEventType = fx.event === 'boost_zone' ? 'boost_zone' : 'emit';
        this.hostEffect(type, fx.event, (fx.data ?? null) as Json);
        break;
      }
    }
  }

  private hostEffect(type: MapEventType, id: string, data: Json | undefined): void {
    const ev = this.emit(type, id, data);
    this.host.onEffect?.(type, data, ev);
  }

  /** Kill the player through the core's respawn (counts as a death like a hazard touch). */
  kill(s: PogoState): void {
    this.kills++;
    s.hazards++;
    this.tmpEv.length = 0;
    respawn({ world: this.world, cfg: this.cfg }, s, this.tmpEv);
    syncPresentation(s, this.cfg);
  }

  // ── run control / introspection ──────────────────────────────────────────────────────────────────────────────
  /** Start a new run: counters, flags, progress, checkpoints and breakables reset (the caller resets the pogo). */
  resetRun(): void {
    this.flags.clear(); this.reached.clear(); this.progress.reset(); this.checkpoints.reset(); this.recorder.reset();
    this.summary = null; this.finished = false; this.kills = 0; this.regionInside.clear(); this.regionFired.clear(); this.zoneInside.clear();
    for (const c of this.loaded.values()) for (const a of c.items) { a.broken = false; a.armedAt = -1; a.brokenAt = -1; a.gate = true; a.oneWayOn.fill(true); }
  }

  loadedChunk(id: string): LoadedChunk | undefined { return this.loaded.get(id); }
  /** Instance batches of every loaded chunk (renderer input). */
  allBatches(): InstanceBatch[] { const out: InstanceBatch[] = []; for (const id of [...this.loaded.keys()].sort()) out.push(...this.loaded.get(id)!.batches); return out; }
  loadedInstances(): EntityInstance[] { const out: EntityInstance[] = []; for (const id of [...this.loaded.keys()].sort()) out.push(...this.loaded.get(id)!.instances); return out; }
  /** Collider index of an entity's first piece (−1 if its chunk is not loaded). */
  colliderOf(entityId: string): number { for (const c of this.loaded.values()) for (const a of c.items) if (a.inst.r.id === entityId && a.slots.length) return a.slots[0]; return -1; }
  /** Metrics for dashboards/tests. */
  metrics(): { loadedChunks: number; activeChunks: number; loadedEntities: number; slots: number; activeColliders: number; rebuilds: number } {
    let ents = 0; for (const c of this.loaded.values()) ents += c.items.length;
    return { loadedChunks: this.loaded.size, activeChunks: this.chunks.stats.active, loadedEntities: ents, slots: this.world.slotCount, activeColliders: this.world.solids.length + this.world.triggers.length, rebuilds: this.world.rebuilds };
  }

  snapshot(): Json {
    const breakables: Json = {};
    for (const c of this.loaded.values()) for (const a of c.items) if (a.inst.breakable) (breakables as Record<string, Json>)[a.inst.r.id] = { broken: a.broken, brokenAt: a.brokenAt, armedAt: a.armedAt };
    return {
      flags: Object.fromEntries(this.flags) as Json, reached: [...this.reached], lastOrder: this.checkpoints.lastOrder, finished: this.finished, kills: this.kills,
      progress: { current: this.progress.current, max: this.progress.max }, breakables,
    };
  }
}

function pointInPoly(pts: Vec2[], x: number, y: number): boolean {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const a = pts[i], b = pts[j];
    if ((a.y > y) !== (b.y > y) && x < ((b.x - a.x) * (y - a.y)) / (b.y - a.y) + a.x) inside = !inside;
  }
  return inside;
}

export { polysAabb };
