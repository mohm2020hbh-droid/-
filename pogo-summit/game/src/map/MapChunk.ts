/**
 * MapChunk — chunk layout, streaming policy, visibility culling, LOD and instancing (SPEC §10).
 *
 *   ChunkSource   where chunk contents come from (a MapDocument in memory, or a MapPackage read lazily)
 *   ChunkManager  distance-based load / activate / deactivate / unload with hysteresis, pinning, stable ordering
 *   helpers       visibleChunks · lodLevel · buildBatches · estimateLoad
 */
import type { AssetRef, ChunkDef, ChunkingDef, MapDocument, MapEntity, Rect } from './schema';
import type { Aabb } from './MapCollision';
import type { EntityInstance } from './MapEntity';

export interface ChunkInfo { id: string; extent: Rect; bounds: Rect; pinned: boolean; entityCount: number; tags: string[] }
export interface ChunkData { id: string; entities: MapEntity[] }
export interface ChunkSource {
  listChunks(): ChunkInfo[];
  readChunk(id: string): ChunkData;
}

const rectOf = (a: Aabb): Rect => ({ minX: a.minX, maxX: a.maxX, minY: a.minY, maxY: a.maxY });
export const rectDistance = (r: Rect, x: number, y: number): number => {
  const dx = x < r.minX ? r.minX - x : x > r.maxX ? x - r.maxX : 0;
  const dy = y < r.minY ? r.minY - y : y > r.maxY ? y - r.maxY : 0;
  return Math.hypot(dx, dy);
};
export const rectsOverlap = (a: Rect, b: Rect): boolean => a.minX <= b.maxX && a.maxX >= b.minX && a.minY <= b.maxY && a.maxY >= b.minY;
export const expandRect = (r: Rect, m: number): Rect => ({ minX: r.minX - m, maxX: r.maxX + m, minY: r.minY - m, maxY: r.maxY + m });

/** Chunk id for an extent centre under the document's chunking (explicit defs take precedence; auto-grid otherwise). */
export function chunkIdFor(chunking: ChunkingDef, cx: number, cy: number, originX = 0, originY = 0): string {
  if (chunking.mode === 'explicit' && chunking.defs.length) {
    let best: ChunkDef | null = null, bd = Infinity;
    for (const d of chunking.defs) {
      const dist = rectDistance(d.bounds, cx, cy);
      if (dist < bd) { bd = dist; best = d; if (dist === 0) break; }
    }
    return best!.id;
  }
  const ix = Math.floor((cx - originX) / chunking.cell.w), iy = Math.floor((cy - originY) / chunking.cell.h);
  return `c_${ix}_${iy}`;
}

/**
 * Build a ChunkSource from an in-memory document. `extentOf(entity)` supplies each entity's swept extent (the caller owns
 * prefab resolution, so this module stays free of it). Entities with an explicit `chunk` go there.
 */
export function documentChunkSource(doc: MapDocument, extentOf: (e: MapEntity) => Aabb): ChunkSource & { assignment: Map<string, string> } {
  const members = new Map<string, MapEntity[]>();
  const extents = new Map<string, Aabb>();
  const assignment = new Map<string, string>();
  for (const e of doc.entities) {
    const ex = extentOf(e);
    const id = e.chunk ?? chunkIdFor(doc.chunks, (ex.minX + ex.maxX) / 2, (ex.minY + ex.maxY) / 2);
    assignment.set(e.id, id);
    let list = members.get(id);
    if (!list) { list = []; members.set(id, list); }
    list.push(e);
    const cur = extents.get(id);
    extents.set(id, cur ? { minX: Math.min(cur.minX, ex.minX), maxX: Math.max(cur.maxX, ex.maxX), minY: Math.min(cur.minY, ex.minY), maxY: Math.max(cur.maxY, ex.maxY) } : { ...ex });
  }
  const defs = new Map(doc.chunks.defs.map(d => [d.id, d]));
  // explicit chunks may be empty (still listed so tools see them)
  for (const d of doc.chunks.defs) if (!members.has(d.id)) { members.set(d.id, []); extents.set(d.id, { minX: d.bounds.minX, maxX: d.bounds.maxX, minY: d.bounds.minY, maxY: d.bounds.maxY }); }
  const infos: ChunkInfo[] = [...members.keys()].sort().map(id => {
    const ex = extents.get(id)!;
    const d = defs.get(id);
    return { id, extent: rectOf(ex), bounds: d ? d.bounds : rectOf(ex), pinned: !!d?.pinned, entityCount: members.get(id)!.length, tags: d?.tags ?? [] };
  });
  return {
    assignment,
    listChunks: () => infos,
    readChunk: id => ({ id, entities: members.get(id) ?? [] }),
  };
}

// ── manager ─────────────────────────────────────────────────────────────────────────────────────────────────────
export type ChunkState = 'unloaded' | 'loaded' | 'active';
export interface ChunkPolicy { activateRadius: number; loadRadius: number; unloadRadius: number; maxActive: number }
export const policyOf = (c: ChunkingDef): ChunkPolicy => ({ activateRadius: c.activateRadius, loadRadius: c.loadRadius, unloadRadius: c.unloadRadius, maxActive: c.maxActive });

/** Physics-safety lower bound for the activation radius (SPEC §10.2): vMax · lookahead + margin. */
export const SAFE_ACTIVATE_RADIUS = 32;

export interface ChunkHooks {
  load(info: ChunkInfo, data: ChunkData): void;
  activate(id: string): void;
  deactivate(id: string): void;
  unload(id: string): void;
}

export interface ChunkStats { loaded: number; active: number; loads: number; unloads: number; activations: number; deactivations: number; peakLoaded: number; peakActive: number; overBudget: boolean }

export class ChunkManager {
  private readonly infos: ChunkInfo[];
  private readonly state = new Map<string, ChunkState>();
  private readonly pinned = new Set<string>();
  private readonly grid = new Map<string, number[]>();
  private readonly gridCell: number;
  private loadedOrder: string[] = [];
  readonly stats: ChunkStats = { loaded: 0, active: 0, loads: 0, unloads: 0, activations: 0, deactivations: 0, peakLoaded: 0, peakActive: 0, overBudget: false };

  constructor(private readonly source: ChunkSource, private readonly policy: ChunkPolicy, private readonly hooks: ChunkHooks) {
    this.infos = [...source.listChunks()].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
    this.gridCell = Number.isFinite(policy.loadRadius) ? Math.max(16, policy.loadRadius / 2) : 64;
    this.infos.forEach((info, i) => {
      this.state.set(info.id, 'unloaded');
      if (info.pinned) this.pinned.add(info.id);
      const x0 = Math.floor(info.extent.minX / this.gridCell), x1 = Math.floor(info.extent.maxX / this.gridCell);
      const y0 = Math.floor(info.extent.minY / this.gridCell), y1 = Math.floor(info.extent.maxY / this.gridCell);
      for (let gx = x0; gx <= x1; gx++) for (let gy = y0; gy <= y1; gy++) {
        const k = `${gx},${gy}`;
        const l = this.grid.get(k);
        if (l) l.push(i); else this.grid.set(k, [i]);
      }
    });
  }

  stateOf(id: string): ChunkState { return this.state.get(id) ?? 'unloaded'; }
  isActive(id: string): boolean { return this.state.get(id) === 'active'; }
  activeIds(): string[] { return this.loadedOrder.filter(id => this.state.get(id) === 'active'); }
  loadedIds(): string[] { return [...this.loadedOrder]; }
  infoOf(id: string): ChunkInfo | undefined { return this.infos.find(i => i.id === id); }
  allInfos(): readonly ChunkInfo[] { return this.infos; }
  pin(id: string): void { this.pinned.add(id); }
  unpin(id: string): void { if (!this.infoOf(id)?.pinned) this.pinned.delete(id); }
  isPinned(id: string): boolean { return this.pinned.has(id); }

  /** Chunk ids whose extent contains (x, y) (any state). */
  chunksAt(x: number, y: number): string[] {
    const out: string[] = [];
    const list = this.grid.get(`${Math.floor(x / this.gridCell)},${Math.floor(y / this.gridCell)}`) ?? [];
    for (const i of list) { const e = this.infos[i].extent; if (x >= e.minX && x <= e.maxX && y >= e.minY && y <= e.maxY) out.push(this.infos[i].id); }
    return out;
  }

  private candidates(px: number, py: number, radius: number): Set<number> {
    const out = new Set<number>();
    if (!Number.isFinite(radius)) { for (let i = 0; i < this.infos.length; i++) out.add(i); return out; }
    const gx0 = Math.floor((px - radius) / this.gridCell), gx1 = Math.floor((px + radius) / this.gridCell);
    const gy0 = Math.floor((py - radius) / this.gridCell), gy1 = Math.floor((py + radius) / this.gridCell);
    for (let gx = gx0; gx <= gx1; gx++) for (let gy = gy0; gy <= gy1; gy++) { const l = this.grid.get(`${gx},${gy}`); if (l) for (const i of l) out.add(i); }
    return out;
  }

  /**
   * Stream for a player position. Deterministic (stable id order). Hysteresis: a chunk deactivates at 1.15× its activation
   * radius and unloads beyond `unloadRadius`; pinned chunks are never unloaded (they may be deactivated).
   */
  update(px: number, py: number): void {
    const { activateRadius: ar, loadRadius: lr, unloadRadius: ur } = this.policy;
    const hyst = Math.max(4, ar * 0.15);
    const cand = this.candidates(px, py, Math.max(lr, ur) + 1);
    const idx = [...cand].sort((a, b) => a - b);
    // unload / deactivate what is loaded (including chunks outside the candidate window)
    for (const id of [...this.loadedOrder]) {
      const info = this.infoOf(id)!;
      const d = rectDistance(info.extent, px, py);
      const st = this.state.get(id);
      if (st === 'active' && d > ar + hyst) { this.state.set(id, 'loaded'); this.stats.deactivations++; this.hooks.deactivate(id); }
      if (this.state.get(id) === 'loaded' && d > ur && !this.pinned.has(id)) {
        this.state.set(id, 'unloaded'); this.stats.unloads++; this.hooks.unload(id);
        this.loadedOrder = this.loadedOrder.filter(x => x !== id);
      }
    }
    for (const i of idx) {
      const info = this.infos[i];
      const d = rectDistance(info.extent, px, py);
      let st = this.state.get(info.id)!;
      if (st === 'unloaded' && d <= lr) {
        this.hooks.load(info, this.source.readChunk(info.id));
        this.state.set(info.id, 'loaded'); st = 'loaded'; this.stats.loads++;
        this.loadedOrder.push(info.id); this.loadedOrder.sort();
      }
      if (st === 'loaded' && d <= ar) { this.state.set(info.id, 'active'); this.stats.activations++; this.hooks.activate(info.id); }
    }
    // pinned chunks stay loaded but may be inactive; make sure pinned-and-loaded chunks are at least `loaded`
    let active = 0;
    for (const id of this.loadedOrder) if (this.state.get(id) === 'active') active++;
    this.stats.loaded = this.loadedOrder.length; this.stats.active = active;
    this.stats.peakLoaded = Math.max(this.stats.peakLoaded, this.stats.loaded);
    this.stats.peakActive = Math.max(this.stats.peakActive, active);
    this.stats.overBudget = active > this.policy.maxActive;
  }

  /** Force a chunk loaded (and optionally active) regardless of distance. */
  ensureLoaded(id: string, activate = false): void {
    const info = this.infoOf(id);
    if (!info) return;
    if (this.state.get(id) === 'unloaded') {
      this.hooks.load(info, this.source.readChunk(id));
      this.state.set(id, 'loaded'); this.stats.loads++; this.loadedOrder.push(id); this.loadedOrder.sort();
    }
    if (activate && this.state.get(id) === 'loaded') { this.state.set(id, 'active'); this.stats.activations++; this.hooks.activate(id); }
  }
}

// ── visibility / LOD / instancing ───────────────────────────────────────────────────────────────────────────────
/** Loaded chunks intersecting the camera rectangle (+ margin). */
export function visibleChunkIds(mgr: ChunkManager, cam: Rect, margin = 4): string[] {
  const r = expandRect(cam, margin);
  return mgr.loadedIds().filter(id => rectsOverlap(mgr.infoOf(id)!.extent, r));
}

/** LOD level for a camera distance with hysteresis (10 %); `distances` ascending, level 0 = most detailed. */
export function lodLevel(distance: number, distances: readonly number[], prev = 0, hysteresis = 0.1): number {
  let lvl = 0;
  for (let i = 0; i < distances.length; i++) {
    const t = distances[i];
    const edge = prev > i ? t * (1 - hysteresis) : t * (1 + hysteresis);
    if (distance > edge) lvl = i + 1;
  }
  return lvl;
}

export interface InstanceTransform { id: string; x: number; y: number; z: number; rotation: number; sx: number; sy: number }
export interface InstanceBatch { key: string; mesh: string; material: string; count: number; tris: number; transforms: InstanceTransform[] }

/** Group instancing-flagged visuals by (mesh/style, material); everything else stays an individual draw call. */
export function buildBatches(instances: readonly EntityInstance[]): { batches: InstanceBatch[]; singles: string[] } {
  const batches = new Map<string, InstanceBatch>();
  const singles: string[] = [];
  for (const inst of instances) {
    const v = inst.r.visual;
    if (!v || v.kind === 'none' || !inst.r.enabled) continue;
    const mesh = v.mesh ?? v.style ?? 'procedural';
    const material = v.material ?? '';
    if (!v.instancing) { singles.push(inst.r.id); continue; }
    const group = typeof v.instancing === 'string' ? v.instancing : '';
    const key = `${group}|${mesh}|${material}`;
    let b = batches.get(key);
    if (!b) { b = { key, mesh, material, count: 0, tris: 0, transforms: [] }; batches.set(key, b); }
    b.count++; b.tris += v.tris ?? 0;
    b.transforms.push({ id: inst.r.id, x: inst.r.x, y: inst.r.y, z: inst.r.z, rotation: inst.r.rotation, sx: inst.r.sx, sy: inst.r.sy });
  }
  return { batches: [...batches.values()].sort((a, b) => (a.key < b.key ? -1 : 1)), singles };
}

export interface LoadEstimate { drawCalls: number; triangles: number; textureBytes: number; colliders: number; entities: number; vfx: number; audio: number }
export const emptyEstimate = (): LoadEstimate => ({ drawCalls: 0, triangles: 0, textureBytes: 0, colliders: 0, entities: 0, vfx: 0, audio: 0 });

/**
 * Cost estimate of a set of instances (what the budgets of SPEC §18 are checked against).
 * `texturesOf(materialId)` lists the texture asset ids a material samples; each distinct texture counts once
 * (RGBA8 with a full mip chain ≈ ×4/3).
 */
export function estimateLoad(instances: readonly EntityInstance[], assets: ReadonlyMap<string, AssetRef>, texturesOf: (materialId: string) => string[] = () => [], proceduralTris: (style: string | undefined) => number = () => 0): LoadEstimate {
  const est = emptyEstimate();
  const { batches, singles } = buildBatches(instances);
  est.drawCalls = batches.length + singles.length;
  const seenTex = new Set<string>();
  for (const inst of instances) {
    est.entities++;
    est.colliders += inst.pieces.length;
    if (inst.r.type === 'vfx') est.vfx += Number(inst.r.props.particles ?? 40);
    if (inst.r.type === 'audio') est.audio += 1;
    const v = inst.r.visual;
    if (!v || v.kind === 'none') continue;
    const mesh = v.mesh ? assets.get(v.mesh) : undefined;
    est.triangles += mesh?.tris ?? v.tris ?? (v.kind === 'procedural' ? proceduralTris(v.style) : 0);
    if (v.material) {
      for (const tid of texturesOf(v.material)) {
        const t = assets.get(tid);
        if (t && !seenTex.has(t.id)) { seenTex.add(t.id); est.textureBytes += Math.round((t.width ?? 0) * (t.height ?? 0) * 4 * (4 / 3)); }
      }
    }
  }
  return est;
}
