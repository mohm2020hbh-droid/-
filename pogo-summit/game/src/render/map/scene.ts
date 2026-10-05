import * as THREE from 'three';
import type { EntityInstance } from '../../map/MapEntity';
import type { MapRuntime } from '../../map/MapRuntime';
import { BUILTIN_MESHES, type MeshParams, isBuiltinMesh } from '../../map/MapAssets';
import { lodLevel } from '../../map/MapChunk';
import { type ResolvedVisual, resolveVisual } from '../../map/MapVisual';
import { expandScatter, hashString } from '../../map/MapScatter';
import { backdropOf, lightingOf, resolveTheme, themeMaterials } from '../../map/MapTheme';
import { slotId } from '../../map/MapTheme';
import type { MaterialDef, RenderLayer, ThemeDef } from '../../map/schema';
import { Backdrop } from './backdrop';
import { type MeshPart, compact, merge, triCount } from './geo';
import { MaterialLibrary } from './materials';
import { type Lod, generateMesh } from './meshes';
import { type AssetBytes, TextureLibrary } from './textures';

/**
 * MapScene — the Visual-V2 renderer of a MapRuntime (phases 1, 2, 6). It owns everything drawn for a map except the
 * character, particles and lights:
 *
 *   chunk rendering   a visual chunk is built when the runtime loads a chunk and disposed when it unloads
 *   merged            static parametric geometry (platforms, cliffs, slabs): one merged mesh per (chunk, material, LOD)
 *   instanced         repeated decoration: one InstancedMesh per (mesh variant, material, LOD) SHARED by all chunks,
 *                     with per-instance frustum / distance culling and per-instance LOD
 *   single            entities with behaviours (moving, rotating, gated, breakable): own meshes, transformed each frame
 *   backdrop          layered far field with parallax (see backdrop.ts)
 *
 * Visual geometry is generated (`meshes.ts`), never taken from the collision shapes.
 */
export interface MapSceneOptions {
  runtime: MapRuntime;
  theme: ThemeDef;
  /** 0..1 decoration density (QUALITY.decor). */
  density?: number;
  assets?: AssetBytes | null;
  /** chunks built per frame after the first update (default 2). */
  maxBuildPerFrame?: number;
  /** reflection map for glossy materials (optional). */
  environment?: THREE.Texture | null;
  /** scales the LOD thresholds (quality: < 1 switches to cheaper levels earlier). */
  lodBias?: number;
  normalMaps?: boolean;
}

export interface SceneStats {
  chunksBuilt: number; chunksVisible: number; chunksLoaded: number;
  instancesTotal: number; instancesVisible: number; culledFrustum: number; culledDistance: number;
  lod: [number, number, number];
  pools: number; mergedMeshes: number; singles: number;
  /** triangles of everything submitted by the CPU model (before the GPU's own culling) */
  trianglesSubmitted: number; drawCallsEstimated: number;
  buildMsTotal: number; buildMsLast: number; buildMsMax: number;
  textureBytes: number; materials: number; textures: number;
  fallbackMeshes: number;
}

export interface Emitter { id: string; kind: 'vfx' | 'audio'; x: number; y: number; z: number; props: Record<string, unknown> }

interface Group {
  geoKey: string; mesh: string; lodMesh: [string | undefined, string | undefined]; params: MeshParams; variant: number;
  parts: { role: string; material: string }[];
  cast: boolean; recv: boolean; layer: RenderLayer; foreground: boolean;
  lodDepth: [number, number]; cullDepth: number; radius: number;
  n: number; matrices: number[]; centers: number[]; radii: number[]; tint: number[];
  m: Float32Array; c: Float32Array; r: Float32Array; lodState: Uint8Array; tinted: boolean;
  pools: (Pool | null)[][];
}
interface Pool { mesh: THREE.InstancedMesh; capacity: number; count: number; tinted: boolean; triPerInstance: number }
interface MergedItem { rv: ResolvedVisual; matrix: THREE.Matrix4 }
interface Batch {
  key: string; items: MergedItem[]; material: string; role: string; layer: RenderLayer; cast: boolean; recv: boolean; lodDepth: [number, number];
  meshes: (THREE.Mesh | null)[]; lod: number;
}
interface Single {
  id: string; rv: ResolvedVisual; inst: EntityInstance; root: THREE.Group; lodMeshes: (THREE.Mesh[] | null)[]; lod: number;
  base: THREE.Matrix4; pivot: THREE.Vector3 | null; ghost: boolean; state: string;
}
interface ChunkView { id: string; root: THREE.Group; box: THREE.Box3; groups: Group[]; batches: Batch[]; singles: Single[]; visible: boolean; emitters: Emitter[]; entityPos: Map<string, THREE.Vector3>; minDepth: number }

const RENDER_ORDER: Record<RenderLayer, number> = { background: 0, midground: 1, gameplay: 2, foreground: 3 };
const stableKey = (p: MeshParams): string => JSON.stringify(Object.keys(p).sort().map(k => [k, p[k]]));
const _m = new THREE.Matrix4(), _q = new THREE.Quaternion(), _e = new THREE.Euler(), _p = new THREE.Vector3(), _s = new THREE.Vector3(), _o = new THREE.Matrix4(), _m2 = new THREE.Matrix4();

/** M = T(position) · Rz(rotation) · S(scale) · T(offset) — the same order as the collision placement. */
function entityMatrix(rv: ResolvedVisual, inst: EntityInstance, out: THREE.Matrix4): THREE.Matrix4 {
  const r = inst.r;
  _q.setFromEuler(_e.set(0, 0, (r.rotation * Math.PI) / 180));
  out.compose(_p.set(r.x, r.y, rv.z), _q, _s.set(r.sx, r.sy, (r.sx + r.sy) / 2));
  if (rv.offset.x !== 0 || rv.offset.y !== 0) out.multiply(_o.makeTranslation(rv.offset.x, rv.offset.y, 0));
  return out;
}

const FALLBACK_MESH = 'builtin:rock';

export class MapScene {
  readonly root = new THREE.Group();
  readonly materials: MaterialLibrary;
  readonly textures: TextureLibrary;
  readonly backdrop: Backdrop;
  readonly stats: SceneStats = {
    chunksBuilt: 0, chunksVisible: 0, chunksLoaded: 0, instancesTotal: 0, instancesVisible: 0, culledFrustum: 0, culledDistance: 0, lod: [0, 0, 0], pools: 0, mergedMeshes: 0, singles: 0,
    trianglesSubmitted: 0, drawCallsEstimated: 0, buildMsTotal: 0, buildMsLast: 0, buildMsMax: 0, textureBytes: 0, materials: 0, textures: 0, fallbackMeshes: 0,
  };
  /** Events for tests / HUD: chunk ids as they are built and disposed. */
  readonly log: { built: string[]; disposed: string[] } = { built: [], disposed: [] };

  private readonly rt: MapRuntime;
  private readonly theme: ThemeDef;
  private readonly density: number;
  private readonly lodBias: number;
  private readonly maxBuild: number;
  private readonly views = new Map<string, ChunkView>();
  private readonly pools = new Map<string, Pool>();
  private readonly instRoot = new THREE.Group();
  private readonly frustum = new THREE.Frustum();
  private readonly pv = new THREE.Matrix4();
  private readonly sphere = new THREE.Sphere();
  private readonly geoStore = new Map<string, { parts: MeshPart[]; tris: number }>();
  private firstUpdate = true;
  private time = 0;
  private emitterCache: Emitter[] | null = null;
  private readonly markers = new Map<string, { glow: THREE.MeshStandardMaterial; kind: 'start' | 'checkpoint' | 'finish'; reached: boolean; pulse?: THREE.Sprite }>();
  private readonly markerRoot = new THREE.Group();
  private readonly tmpOff = { x: 0, y: 0, z: 0, vx: 0, vy: 0 } as unknown as { x: number; y: number; z: number };

  constructor(o: MapSceneOptions) {
    this.rt = o.runtime; this.theme = o.theme;
    this.density = o.density ?? 1; this.lodBias = o.lodBias ?? 1; this.maxBuild = o.maxBuildPerFrame ?? 2;
    this.root.name = 'map-scene'; this.instRoot.name = 'instances';
    this.textures = new TextureLibrary(o.runtime.doc.assets, o.assets ?? null);
    const defs: Record<string, MaterialDef> = { ...themeMaterials(o.theme), ...o.runtime.doc.materials };
    this.materials = new MaterialLibrary(this.textures, id => defs[id]);
    this.materials.normalMaps = o.normalMaps ?? true;
    if (o.environment) this.materials.setEnvironment(o.environment);
    const lp = lightingOf(o.theme);
    const cloudSlot = slotId(o.theme, 'cloud') ?? 'cloud';
    this.backdrop = new Backdrop(backdropOf(o.theme), {
      bounds: o.runtime.doc.world.bounds, sunDir: new THREE.Vector3(...lp.sunDirection).normalize(), fogColor: lp.fogColor ?? o.theme.fog.color,
      cloudMaterial: tint => this.materials.get(cloudSlot, { tint }), density: this.density,
    });
    this.root.add(this.backdrop.root, this.instRoot, this.markerRoot);
    this.buildMarkers();
  }

  /** Chunk ids with built visuals. */
  builtChunks(): string[] { return [...this.views.keys()].sort(); }
  /** Emitter entities (type vfx / audio) of the loaded chunks. */
  emitters(): Emitter[] { return (this.emitterCache ??= [...this.views.values()].flatMap(v => v.emitters)); }
  /** World position of a loaded entity's visual (for break / hit effects), or null. */
  positionOf(id: string): { x: number; y: number } | null {
    for (const v of this.views.values()) { const p = v.entityPos.get(id); if (p) return { x: p.x, y: p.y }; }
    return null;
  }
  poolCount(): number { return this.pools.size; }
  chunkView(id: string): { visible: boolean; merged: number; singles: number; groups: number; box: THREE.Box3 } | undefined {
    const v = this.views.get(id);
    return v ? { visible: v.visible, merged: v.batches.length, singles: v.singles.length, groups: v.groups.length, box: v.box } : undefined;
  }

  // ── markers (spawn, checkpoints, finish) ────────────────────────────────────────────────────────────────────
  private buildMarkers(): void {
    const doc = this.rt.doc, theme = this.theme;
    const stoneId = slotId(theme, 'stone') ?? 'stone', glowId = slotId(theme, 'glow') ?? 'glow';
    const add = (id: string, kind: 'start' | 'checkpoint' | 'finish', x: number, y: number, w: number, h: number): void => {
      const parts = generateMesh('builtin:marker', { kind, height: h, w }, 0, 0);
      const group = new THREE.Group(); group.position.set(x, y, kind === 'finish' ? 0 : -0.4); group.name = `marker:${id}`;
      const glow = this.materials.get(glowId).clone();
      glow.emissiveIntensity = kind === 'finish' ? 1.2 : 0.25; glow.side = THREE.DoubleSide;
      for (const p of parts) { const m = new THREE.Mesh(p.geometry, p.role === 'glow' ? glow : this.materials.get(stoneId)); m.castShadow = p.role !== 'glow'; m.receiveShadow = true; group.add(m); }
      let pulse: THREE.Sprite | undefined;
      if (kind !== 'start') {
        const tex = new THREE.DataTexture(new Uint8Array(64 * 64 * 4).map((_, i) => (i % 4 === 3 ? Math.round(255 * Math.max(0, 1 - Math.hypot(((i >> 2) % 64) / 63 - 0.5, (((i >> 2) / 64) | 0) / 63 - 0.5) * 2) ** 2) : 255)), 64, 64, THREE.RGBAFormat);
        tex.magFilter = tex.minFilter = THREE.LinearFilter; tex.needsUpdate = true;
        pulse = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, color: glow.emissive, transparent: true, opacity: 0.35, depthWrite: false, blending: THREE.AdditiveBlending, fog: false }));
        pulse.scale.set(w * 2.2, h * 1.5, 1); pulse.position.set(0, h * 0.5, 0.2); group.add(pulse);
      }
      this.markerRoot.add(group);
      this.markers.set(id, { glow, kind, reached: false, pulse });
    };
    if (doc.spawn) add('spawn', 'start', doc.spawn.position.x, doc.spawn.position.y, 3.2, 3.2);
    for (const c of doc.checkpoints) if (!c.optional) add(c.id, 'checkpoint', c.respawn.x, c.respawn.y - 0.05, 3, 3.6);
    for (const z of doc.finish.zones) { const w = z.shape.kind === 'box' ? z.shape.w : 3, h = z.shape.kind === 'box' ? z.shape.h : 4; add(z.id, 'finish', z.position.x, z.position.y - h / 2, Math.max(3, w + 1), Math.max(3.5, h)); }
  }

  /** A checkpoint was reached: its banner lights up. */
  setCheckpointReached(id: string): void { const m = this.markers.get(id); if (m) m.reached = true; }
  resetMarkers(): void { for (const m of this.markers.values()) m.reached = false; }
  markerState(id: string): { reached: boolean; intensity: number } | null { const m = this.markers.get(id); return m ? { reached: m.reached, intensity: m.glow.emissiveIntensity } : null; }

  private updateMarkers(time: number): void {
    for (const m of this.markers.values()) {
      const base = m.kind === 'finish' ? 1.1 + 0.35 * Math.sin(time * 2.2) : m.reached ? 1.5 + 0.2 * Math.sin(time * 3) : m.kind === 'checkpoint' ? 0.22 : 0.4;
      m.glow.emissiveIntensity += (base - m.glow.emissiveIntensity) * 0.2;
      if (m.pulse) (m.pulse.material as THREE.SpriteMaterial).opacity = m.kind === 'finish' ? 0.35 + 0.15 * Math.sin(time * 2.2) : m.reached ? 0.4 : 0.08;
    }
  }

  /** Waterfalls, lava and water bodies bring their own particle / audio emitters. */
  private autoEmitters(rv: ResolvedVisual, view: ChunkView): void {
    const r = this.rt.doc, id = rv.entityId, inst = this.rt.loadedChunk(view.id)?.instances.find(i => i.r.id === id);
    if (!inst) return;
    const x = inst.r.x, y = inst.r.y, p = rv.params;
    const n = (k: string, d: number): number => (typeof p[k] === 'number' ? (p[k] as number) : d);
    void r;
    if (rv.mesh === 'builtin:waterfall') {
      view.emitters.push({ id: `${id}:splash`, kind: 'vfx', x, y: y - n('h', 16) + 0.6, z: rv.z, props: { kind: 'splash', rate: 3.5, radius: n('w', 3), power: 0.5, color: '#ffffff' } });
      view.emitters.push({ id: `${id}:snd`, kind: 'audio', x, y: y - n('h', 16) / 2, z: rv.z, props: { sound: 'waterfall', radius: 34, volume: 0.8 } });
    } else if (rv.mesh === 'builtin:water') {
      view.emitters.push({ id: `${id}:snd`, kind: 'audio', x, y, z: rv.z, props: { sound: 'water', radius: 22, volume: 0.45 } });
    }
    if (rv.parts.some(pt => this.materials.describe(pt.material)?.surfaceType === 'LAVA')) {
      view.emitters.push({ id: `${id}:lava`, kind: 'vfx', x, y, z: rv.z, props: { kind: 'lava', rate: 2, radius: Math.max(2, n('w', 4)), power: 0.4, color: '#ff9a3a' } });
      view.emitters.push({ id: `${id}:lavasnd`, kind: 'audio', x, y, z: rv.z, props: { sound: 'lava', radius: 24, volume: 0.6 } });
    }
  }

  // ── per-frame update ────────────────────────────────────────────────────────────────────────────────────────
  update(a: { dt: number; time: number; camera: THREE.PerspectiveCamera; focus: { x: number; y: number }; tick: number; playerPx?: { x: number; y: number } | null; fadeRadiusPx?: number }): void {
    this.time = a.time;
    const loaded = this.rt.chunks.loadedIds();
    // dispose what the runtime unloaded
    for (const id of [...this.views.keys()]) if (!loaded.includes(id)) this.disposeChunk(id);
    // build what it loaded (nearest first, a few per frame to avoid hitches)
    const missing = loaded.filter(id => !this.views.has(id));
    if (missing.length) {
      missing.sort((x, y) => this.chunkDist(x, a.focus) - this.chunkDist(y, a.focus));
      const n = this.firstUpdate ? missing.length : this.maxBuild;
      for (const id of missing.slice(0, n)) this.buildChunk(id);
    }
    this.firstUpdate = false;
    this.stats.chunksLoaded = loaded.length;

    a.camera.updateMatrixWorld();
    this.pv.multiplyMatrices(a.camera.projectionMatrix, a.camera.matrixWorldInverse);
    this.frustum.setFromProjectionMatrix(this.pv);
    if (a.playerPx) { this.materials.fade.uPlayerPx.value.set(a.playerPx.x, a.playerPx.y); this.materials.fade.uFadeR.value = a.fadeRadiusPx ?? 0; }
    this.materials.rim.value.set(this.theme.sky.sun).multiplyScalar(0.5);
    this.materials.update(a.time);

    this.stats.chunksVisible = 0; this.stats.mergedMeshes = 0;
    const vz = a.camera.matrixWorldInverse.elements;
    for (const v of this.views.values()) {
      v.visible = this.frustum.intersectsBox(v.box);
      v.root.visible = v.visible;
      if (!v.visible) continue;
      this.stats.chunksVisible++;
      v.minDepth = this.boxDepth(v.box, vz);
      for (const b of v.batches) this.updateBatchLod(v, b);
      this.updateSingles(v, a.tick, vz);
    }
    this.updateInstances(vz);
    this.backdrop.update(a.dt, a.time, a.focus);
    this.updateMarkers(a.time);
    this.finishStats();
  }

  private chunkDist(id: string, p: { x: number; y: number }): number {
    const e = this.rt.chunks.infoOf(id)?.extent;
    if (!e) return 0;
    const dx = Math.max(e.minX - p.x, 0, p.x - e.maxX), dy = Math.max(e.minY - p.y, 0, p.y - e.maxY);
    return Math.hypot(dx, dy);
  }

  private boxDepth(box: THREE.Box3, m: ArrayLike<number>): number {
    let best = Infinity;
    for (let i = 0; i < 8; i++) {
      const x = i & 1 ? box.max.x : box.min.x, y = i & 2 ? box.max.y : box.min.y, z = i & 4 ? box.max.z : box.min.z;
      best = Math.min(best, -(m[2] * x + m[6] * y + m[10] * z + m[14]));
    }
    return best;
  }

  // ── chunk build / dispose ───────────────────────────────────────────────────────────────────────────────────
  private buildChunk(id: string): void {
    const t0 = performance.now();
    const lc = this.rt.loadedChunk(id);
    const root = new THREE.Group(); root.name = `chunk:${id}`;
    const view: ChunkView = { id, root, box: new THREE.Box3(), groups: [], batches: [], singles: [], visible: false, emitters: [], entityPos: new Map(), minDepth: 0 };
    this.views.set(id, view); this.emitterCache = null;
    if (!lc) { this.root.add(root); return; }
    const ctx = { theme: this.theme, assets: new Map(this.rt.doc.assets.map(a => [a.id, a])) };
    const groups = new Map<string, Group>();
    const batches = new Map<string, Batch>();
    const box = view.box;
    for (const inst of lc.instances) {
      const r = inst.r;
      view.entityPos.set(r.id, new THREE.Vector3(r.x, r.y, r.z));
      if (r.type === 'vfx' || r.type === 'audio') view.emitters.push({ id: r.id, kind: r.type, x: r.x, y: r.y, z: r.z, props: r.props });
      let rv = resolveVisual(inst, ctx);
      if (!rv) { box.expandByPoint(_p.set(r.x, r.y, r.z)); continue; }
      this.autoEmitters(rv, view);
      if (!isBuiltinMesh(rv.mesh)) {                                    // package mesh assets need a glTF provider; draw a stand-in rock
        this.stats.fallbackMeshes++;
        rv = { ...rv, mesh: FALLBACK_MESH, builtin: true, params: { size: 1.5 }, parts: [{ role: 'body', material: rv.parts[0]?.material ?? '' }], tris: [180, 80, 20] };
      }
      if (rv.mode === 'single') view.singles.push(this.makeSingle(inst, rv, view));
      else if (rv.mode === 'instanced') this.addInstanced(inst, rv, groups, box);
      else this.addMerged(inst, rv, batches, box);
    }
    // merged: build the LOD0 meshes of the whole chunk in one pass (each entity generates its parts once)
    view.batches = [...batches.values()];
    this.buildMergedLod(view, 0);
    for (const g of groups.values()) {
      g.m = new Float32Array(g.matrices); g.c = new Float32Array(g.centers); g.r = new Float32Array(g.radii); g.lodState = new Uint8Array(g.n);
      g.matrices = []; g.centers = []; g.radii = []; g.pools = [[], [], []]; g.tinted = g.tint.some(t => Math.abs(t - 1) > 1e-3);
      this.stats.instancesTotal += g.n;
    }
    view.groups = [...groups.values()];
    if (box.isEmpty()) box.set(new THREE.Vector3(-1, -1, -1), new THREE.Vector3(1, 1, 1));
    box.expandByScalar(1);
    this.root.add(root);
    this.stats.chunksBuilt++;
    const ms = performance.now() - t0;
    this.stats.buildMsLast = ms; this.stats.buildMsTotal += ms; this.stats.buildMsMax = Math.max(this.stats.buildMsMax, ms);
    this.log.built.push(id);
  }

  private disposeChunk(id: string): void {
    const v = this.views.get(id);
    if (!v) return;
    v.root.traverse(o => { const m = o as THREE.Mesh; if (m.geometry) m.geometry.dispose(); });
    this.root.remove(v.root);
    for (const g of v.groups) this.stats.instancesTotal -= g.n;
    this.views.delete(id); this.emitterCache = null;
    this.log.disposed.push(id);
  }

  private addMerged(inst: EntityInstance, rv: ResolvedVisual, batches: Map<string, Batch>, box: THREE.Box3): void {
    const matrix = entityMatrix(rv, inst, new THREE.Matrix4());
    const radius = this.estimateRadius(rv);
    box.expandByPoint(_p.set(matrix.elements[12], matrix.elements[13], matrix.elements[14])); box.expandByScalar(0);
    box.union(new THREE.Box3().setFromCenterAndSize(_p.set(matrix.elements[12], matrix.elements[13], matrix.elements[14]), _s.set(radius * 2, radius * 2, radius * 2)));
    rv.parts.forEach(part => {
      const key = `${part.material}|${rv.layer}|${rv.lodDepth.join(',')}|${rv.cast ? 1 : 0}${rv.receive ? 1 : 0}|${part.role}`;
      let b = batches.get(key);
      if (!b) { b = { key, items: [], material: part.material, role: part.role, layer: rv.layer, cast: rv.cast, recv: rv.receive, lodDepth: rv.lodDepth, meshes: [null, null, null], lod: 0 }; batches.set(key, b); }
      b.items.push({ rv, matrix });
    });
  }

  /** Rough bounding radius of one instance of a resolved visual (metres), from its parameters. */
  private estimateRadius(rv: ResolvedVisual): number {
    const p = rv.params, n = (k: string, d: number): number => (typeof p[k] === 'number' ? (p[k] as number) : d);
    switch (rv.mesh) {
      case 'builtin:platform': case 'builtin:cliff': return Math.hypot(n('w', 6), n('h', 3), n('depth', 5)) / 2 + 1;
      case 'builtin:mountain': return Math.hypot(n('w', 120), n('h', 50), n('depth', 40)) / 2;
      case 'builtin:waterfall': return Math.hypot(n('w', 3), n('h', 16)) / 2 + 2;
      case 'builtin:water': return Math.hypot(n('w', 12), n('depth', 4)) / 2 + 1;
      case 'builtin:ancient_structure': return Math.hypot(n('w', 8), n('h', 10)) / 2 + 2;
      case 'builtin:tree': return n('height', 7);
      case 'builtin:spikes': return Math.hypot(n('width', 3.2), n('height', 1.5)) / 2 + 1;
      case 'builtin:blade': return n('length', 8) / 2 + 1;
      case 'builtin:poly_slab': return 8;
      default: return 6;
    }
  }

  private addInstanced(inst: EntityInstance, rv: ResolvedVisual, groups: Map<string, Group>, box: THREE.Box3): void {
    const r = inst.r;
    const variants = Math.max(1, Math.round(rv.scatter?.variants ?? (typeof rv.params.variants === 'number' ? rv.params.variants : BUILTIN_MESHES[rv.mesh]?.repeat ? 4 : 1)));
    const seed = (typeof rv.params.seed === 'number' ? rv.params.seed : hashString(r.id)) >>> 0;
    const list = rv.scatter
      ? expandScatter(rv.scatter, { x: r.x, y: r.y, z: rv.z, rotation: r.rotation, sx: r.sx, sy: r.sy }, seed, this.density)
      : [{ x: r.x, y: r.y, z: rv.z, yaw: 0, roll: r.rotation, scale: 1, variant: hashString(r.id) % variants, tint: 1 }];
    const baseR = this.estimateRadius(rv);
    for (const it of list) {
      const key = `${rv.mesh}|${stableKey(rv.params)}|${it.variant}|${rv.parts.map(p => p.material).join('+')}|${rv.cast ? 1 : 0}${rv.receive ? 1 : 0}|${rv.layer}|${rv.lodDepth.join(',')}|${rv.lodMesh.join(',')}`;
      let g = groups.get(key);
      if (!g) {
        g = {
          geoKey: key, mesh: rv.mesh, lodMesh: rv.lodMesh, params: rv.params, variant: it.variant, parts: rv.parts, cast: rv.cast, recv: rv.receive, layer: rv.layer, foreground: rv.layer === 'foreground',
          lodDepth: [rv.lodDepth[0] * this.lodBias, rv.lodDepth[1] * this.lodBias], cullDepth: rv.cullDepth, radius: baseR, n: 0, matrices: [], centers: [], radii: [], tint: [],
          m: new Float32Array(0), c: new Float32Array(0), r: new Float32Array(0), lodState: new Uint8Array(0), tinted: false, pools: [],
        };
        groups.set(key, g);
      }
      _q.setFromEuler(_e.set(0, (it.yaw * Math.PI) / 180, (it.roll * Math.PI) / 180));
      const sc = it.scale * (rv.scatter ? 1 : 1);
      _m.compose(_p.set(it.x, it.y, it.z), _q, rv.scatter ? _s.set(sc, sc, sc) : _s.set(r.sx, r.sy, (r.sx + r.sy) / 2));
      if (!rv.scatter && (rv.offset.x !== 0 || rv.offset.y !== 0)) _m.multiply(_o.makeTranslation(rv.offset.x, rv.offset.y, 0));
      g.matrices.push(..._m.elements);
      g.centers.push(it.x, it.y + baseR * 0.4 * it.scale, it.z); g.radii.push(baseR * (rv.scatter ? it.scale : Math.max(r.sx, r.sy))); g.tint.push(it.tint); g.n++;
      box.union(new THREE.Box3().setFromCenterAndSize(_p.set(it.x, it.y + baseR * 0.4, it.z), _s.set(baseR * 2 * it.scale, baseR * 2 * it.scale, baseR * 2 * it.scale)));
    }
  }

  // ── merged batches ──────────────────────────────────────────────────────────────────────────────────────────
  private buildMergedLod(view: ChunkView, lod: Lod, only?: Batch): void {
    const per = new Map<Batch, THREE.BufferGeometry[]>();
    const todo = only ? [only] : view.batches;
    // one generation per entity (its parts are shared by the batches of its roles)
    const byEntity = new Map<string, { item: MergedItem; batches: Batch[] }>();
    for (const b of todo) for (const it of b.items) { const e = byEntity.get(it.rv.entityId); if (e) e.batches.push(b); else byEntity.set(it.rv.entityId, { item: it, batches: [b] }); }
    for (const { item: it, batches: bs } of byEntity.values()) {
      const meshForLod = lod > 0 && it.rv.lodMesh[lod - 1] && isBuiltinMesh(it.rv.lodMesh[lod - 1]!) ? it.rv.lodMesh[lod - 1]! : it.rv.mesh;
      const parts = generateMesh(meshForLod, meshForLod === it.rv.mesh ? it.rv.params : {}, lod, 0);
      for (const b of bs) {
        const part = parts.find(p => p.role === b.role) ?? (b.role === 'cap' || b.role === 'snow' ? undefined : parts[0]);
        if (!part || !part.geometry.getIndex() || part.geometry.getIndex()!.count === 0) continue;
        const g = part.geometry.clone().applyMatrix4(it.matrix);
        const l = per.get(b); if (l) l.push(g); else per.set(b, [g]);
      }
      for (const p of parts) p.geometry.dispose();
    }
    for (const b of todo) {
      const gs = per.get(b);
      if (!gs?.length) { b.meshes[lod] = null; continue; }
      const geo = merge(gs);
      for (const g of gs) if (g !== geo) g.dispose();
      geo.computeBoundingSphere(); geo.computeBoundingBox();
      const mesh = new THREE.Mesh(geo, this.materials.get(b.material, { foreground: b.layer === 'foreground' }));
      mesh.castShadow = b.cast; mesh.receiveShadow = b.recv; mesh.renderOrder = RENDER_ORDER[b.layer]; mesh.name = `merged:${b.key}:LOD${lod}`;
      mesh.visible = false;
      b.meshes[lod] = mesh;
      view.root.add(mesh);
    }
  }

  private updateBatchLod(view: ChunkView, b: Batch): void {
    const d = view.minDepth, th = [b.lodDepth[0] * this.lodBias, b.lodDepth[1] * this.lodBias];
    const lod = lodLevel(d, th, b.lod) as Lod;
    if (!b.meshes[lod] && b.meshes[lod] !== null && lod > 0) this.buildMergedLod(view, lod, b);
    if (b.meshes[lod] === undefined) this.buildMergedLod(view, lod, b);
    b.lod = lod;
    let shown: THREE.Mesh | null = null;
    for (let l = 0; l < 3; l++) { const m = b.meshes[l]; if (m) { m.visible = l === lod; if (l === lod) shown = m; } }
    if (!shown) for (let l = lod; l >= 0 && !shown; l--) { const m = b.meshes[l]; if (m) { m.visible = true; shown = m; } }   // a missing level falls back to the nearest built one
    if (shown) { this.stats.mergedMeshes++; this.stats.trianglesSubmitted += triCount(shown.geometry); }
  }

  // ── singles (behaviours) ────────────────────────────────────────────────────────────────────────────────────
  private makeSingle(inst: EntityInstance, rv: ResolvedVisual, view: ChunkView): Single {
    const root = new THREE.Group(); root.name = `single:${rv.entityId}`;
    root.matrixAutoUpdate = false;
    const s: Single = { id: rv.entityId, rv, inst, root, lodMeshes: [null, null, null], lod: 0, base: entityMatrix(rv, inst, new THREE.Matrix4()), pivot: null, ghost: false, state: '' };
    if (inst.rotation?.axis === 'z') {
      const p = inst.rotation.pivot, r = inst.r, c = Math.cos((r.rotation * Math.PI) / 180), si = Math.sin((r.rotation * Math.PI) / 180);
      s.pivot = new THREE.Vector3(r.x + (p.x * r.sx) * c - (p.y * r.sy) * si, r.y + (p.x * r.sx) * si + (p.y * r.sy) * c, rv.z);
    }
    this.buildSingleLod(s, 0);
    view.root.add(root);
    const e = this.estimateRadius(rv);
    view.box.union(new THREE.Box3().setFromCenterAndSize(_p.set(inst.r.x, inst.r.y, rv.z), _s.set(e * 2 + 8, e * 2 + 8, e * 2)));      // moving objects: the swept range too
    if (inst.motion) { const rg = inst.motion.range; view.box.expandByPoint(_p.set(inst.r.x + rg.minX - e, inst.r.y + rg.minY - e, rv.z)); view.box.expandByPoint(_p.set(inst.r.x + rg.maxX + e, inst.r.y + rg.maxY + e, rv.z)); }
    return s;
  }

  private buildSingleLod(s: Single, lod: Lod): void {
    if (s.lodMeshes[lod]) return;
    const rv = s.rv;
    const meshForLod = lod > 0 && rv.lodMesh[lod - 1] && isBuiltinMesh(rv.lodMesh[lod - 1]!) ? rv.lodMesh[lod - 1]! : rv.mesh;
    const parts = generateMesh(meshForLod, meshForLod === rv.mesh ? rv.params : {}, lod, 0);
    const meshes: THREE.Mesh[] = [];
    parts.forEach((p, i) => {
      if (!p.geometry.getIndex() || p.geometry.getIndex()!.count === 0) return;
      const mat = rv.parts[i] ?? rv.parts[0];
      const m = new THREE.Mesh(p.geometry, this.materials.get(mat.material, { foreground: rv.layer === 'foreground' }));
      m.castShadow = rv.cast; m.receiveShadow = rv.receive; m.renderOrder = RENDER_ORDER[rv.layer]; m.userData.part = i; m.visible = lod === s.lod;
      s.root.add(m); meshes.push(m);
    });
    s.lodMeshes[lod] = meshes;
  }

  private updateSingles(view: ChunkView, tick: number, vz: ArrayLike<number>): void {
    const vs = this.rt.visualState;
    for (const s of view.singles) {
      const inst = s.inst, rv = s.rv;
      // transform: T(offset) · (pivot rotation) · base
      _m.copy(s.base);
      if (inst.rotation) {
        const ang = (inst.rotation.angle(tick) * Math.PI) / 180;
        if (s.pivot) { _m2.makeTranslation(s.pivot.x, s.pivot.y, 0).multiply(_o.makeRotationZ(ang)).multiply(new THREE.Matrix4().makeTranslation(-s.pivot.x, -s.pivot.y, 0)); _m.premultiply(_m2); }
        else { const ax = inst.rotation.axis; const rot = ax === 'x' ? _o.makeRotationX(ang) : _o.makeRotationY(ang); _m2.makeTranslation(inst.r.x, inst.r.y, rv.z).multiply(rot).multiply(new THREE.Matrix4().makeTranslation(-inst.r.x, -inst.r.y, -rv.z)); _m.premultiply(_m2); }
      }
      if (inst.motion) { inst.motion.at(tick, this.tmpOff as never); _m.premultiply(_o.makeTranslation(this.tmpOff.x, this.tmpOff.y, ((this.tmpOff as unknown as { z?: number }).z) ?? 0)); }
      s.root.matrix.copy(_m); s.root.matrixWorldNeedsUpdate = true;
      // behaviour state: hidden / ghost / blink / tint
      const st = vs.get(s.id);
      const hidden = st?.visible === 'hidden';
      const ghost = st?.visible === 'ghost' || (!!st?.blink && Math.floor(this.time * 7) % 2 === 0);
      let tint: string | undefined;
      const tg = inst.gates[0];
      if (tg?.type === 'toggle' && tg.tint) tint = (st?.visible ?? 'visible') === 'visible' ? tg.tint.active : tg.tint.inactive;
      const key = `${hidden ? 'h' : ghost ? 'g' : 'v'}${tint ?? ''}`;
      s.root.visible = !hidden;
      if (key !== s.state) { s.state = key; this.restyleSingle(s, ghost, tint); }
      // LOD from the view depth of the object's centre
      const depth = -(vz[2] * _m.elements[12] + vz[6] * _m.elements[13] + vz[10] * _m.elements[14] + vz[14]);
      const lod = lodLevel(depth, [rv.lodDepth[0] * this.lodBias, rv.lodDepth[1] * this.lodBias], s.lod) as Lod;
      if (lod !== s.lod || !s.lodMeshes[lod]) { this.buildSingleLod(s, lod); s.lod = lod; if (s.state) this.restyleSingle(s, ghost, tint); }
      for (let l = 0; l < 3; l++) for (const m of s.lodMeshes[l] ?? []) m.visible = l === s.lod;
      this.stats.singles++;
      for (const m of s.lodMeshes[s.lod] ?? []) this.stats.trianglesSubmitted += triCount(m.geometry);
    }
  }

  private restyleSingle(s: Single, ghost: boolean, tint?: string): void {
    for (let l = 0; l < 3; l++) for (const m of s.lodMeshes[l] ?? []) {
      const part = s.rv.parts[(m.userData.part as number) ?? 0] ?? s.rv.parts[0];
      m.material = this.materials.get(part.material, { ghost, tint, foreground: s.rv.layer === 'foreground' });
      m.castShadow = s.rv.cast && !ghost;
    }
  }

  // ── instanced pools ─────────────────────────────────────────────────────────────────────────────────────────
  private poolFor(g: Group, partIdx: number, lod: Lod): Pool {
    let p = g.pools[lod][partIdx];
    if (p) return p;
    const part = g.parts[partIdx];
    const meshForLod = lod > 0 && g.lodMesh[lod - 1] && isBuiltinMesh(g.lodMesh[lod - 1]!) ? g.lodMesh[lod - 1]! : g.mesh;
    const gk = `${meshForLod}|${meshForLod === g.mesh ? stableKey(g.params) : ''}|${g.variant}|${lod}`;
    let entry = this.geoStore.get(gk);
    if (!entry) {
      const parts = generateMesh(meshForLod, meshForLod === g.mesh ? g.params : {}, lod, g.variant);
      entry = { parts, tris: parts.reduce((n, q) => n + triCount(q.geometry), 0) };
      this.geoStore.set(gk, entry);
    }
    const role = BUILTIN_MESHES[meshForLod]?.roles(meshForLod === g.mesh ? g.params : {})[partIdx] ?? part.role;
    const geoPart = entry.parts.find(q => q.role === role) ?? entry.parts[Math.min(partIdx, entry.parts.length - 1)];
    const poolKey = `${gk}|${role}|${part.material}|${g.foreground}|${g.cast}|${g.recv}|${g.tinted}`;
    let pool = this.pools.get(poolKey);
    if (!pool) {
      const cap = 64;
      const mesh = new THREE.InstancedMesh(geoPart.geometry, this.materials.get(part.material, { foreground: g.foreground }), cap);
      mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
      if (g.tinted) { mesh.instanceColor = new THREE.InstancedBufferAttribute(new Float32Array(cap * 3), 3); mesh.instanceColor.setUsage(THREE.DynamicDrawUsage); }
      mesh.frustumCulled = false; mesh.count = 0; mesh.castShadow = g.cast; mesh.receiveShadow = g.recv; mesh.renderOrder = RENDER_ORDER[g.layer]; mesh.name = `pool:${poolKey.slice(0, 60)}`;
      this.instRoot.add(mesh);
      pool = { mesh, capacity: cap, count: 0, tinted: g.tinted, triPerInstance: triCount(geoPart.geometry) };
      this.pools.set(poolKey, pool);
    }
    g.pools[lod][partIdx] = pool;
    return pool;
  }

  private growPool(p: Pool): void {
    const cap = p.capacity * 2;
    const old = p.mesh;
    const mesh = new THREE.InstancedMesh(old.geometry, old.material, cap);
    mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    (mesh.instanceMatrix.array as Float32Array).set(old.instanceMatrix.array as Float32Array);
    if (p.tinted) { mesh.instanceColor = new THREE.InstancedBufferAttribute(new Float32Array(cap * 3), 3); mesh.instanceColor.setUsage(THREE.DynamicDrawUsage); (mesh.instanceColor.array as Float32Array).set(old.instanceColor!.array as Float32Array); }
    mesh.frustumCulled = false; mesh.count = old.count; mesh.castShadow = old.castShadow; mesh.receiveShadow = old.receiveShadow; mesh.renderOrder = old.renderOrder; mesh.name = old.name;
    this.instRoot.remove(old); old.dispose();
    this.instRoot.add(mesh);
    p.mesh = mesh; p.capacity = cap;
  }

  private updateInstances(vz: ArrayLike<number>): void {
    for (const p of this.pools.values()) p.count = 0;
    const planes = this.frustum.planes;
    const st = this.stats;
    st.instancesVisible = 0; st.culledFrustum = 0; st.culledDistance = 0; st.lod[0] = st.lod[1] = st.lod[2] = 0;
    for (const v of this.views.values()) {
      if (!v.visible) continue;
      for (const g of v.groups) {
        const n = g.n, c = g.c, rr = g.r, m = g.m, ls = g.lodState, cull = g.cullDepth, th = g.lodDepth;
        for (let i = 0; i < n; i++) {
          const x = c[i * 3], y = c[i * 3 + 1], z = c[i * 3 + 2], r = rr[i];
          const depth = -(vz[2] * x + vz[6] * y + vz[10] * z + vz[14]);
          if (depth - r > cull) { st.culledDistance++; continue; }
          let out = false;
          for (let k = 0; k < 6; k++) { const pl = planes[k]; if (pl.normal.x * x + pl.normal.y * y + pl.normal.z * z + pl.constant < -r) { out = true; break; } }
          if (out) { st.culledFrustum++; continue; }
          const lod = lodLevel(depth, th, ls[i]) as Lod;
          ls[i] = lod; st.lod[lod]++; st.instancesVisible++;
          for (let pi = 0; pi < g.parts.length; pi++) {
            const pool = this.poolFor(g, pi, lod);
            if (pool.count >= pool.capacity) this.growPool(pool);
            (pool.mesh.instanceMatrix.array as Float32Array).set(m.subarray(i * 16, i * 16 + 16), pool.count * 16);
            if (pool.tinted && pool.mesh.instanceColor) { const t = g.tint[i] ?? 1, o = pool.count * 3, a = pool.mesh.instanceColor.array as Float32Array; a[o] = a[o + 1] = a[o + 2] = t; }
            pool.count++;
          }
        }
      }
    }
    for (const p of this.pools.values()) {
      p.mesh.count = p.count; p.mesh.visible = p.count > 0;
      if (p.count > 0) { p.mesh.instanceMatrix.needsUpdate = true; if (p.mesh.instanceColor) p.mesh.instanceColor.needsUpdate = true; }
    }
  }

  private finishStats(): void {
    const s = this.stats;
    let pools = 0, tris = 0;
    for (const p of this.pools.values()) if (p.count > 0) { pools++; tris += p.count * p.triPerInstance; }
    s.pools = pools;
    s.trianglesSubmitted += tris + this.backdrop.triangles;
    s.drawCallsEstimated = pools + s.mergedMeshes + s.singles + this.backdrop.drawCalls;
    s.textureBytes = this.textures.bytes; s.materials = this.materials.stats().materials; s.textures = this.textures.count;
    // per-frame counters are recomputed from scratch on the next update
    this.stats.singles = s.singles;
  }

  /** Reset per-frame accumulators (called by the owner right before `update`). */
  beginFrame(): void { this.stats.trianglesSubmitted = 0; this.stats.singles = 0; }

  dispose(): void {
    for (const id of [...this.views.keys()]) this.disposeChunk(id);
    for (const p of this.pools.values()) p.mesh.dispose();
    this.pools.clear();
    for (const e of this.geoStore.values()) for (const p of e.parts) p.geometry.dispose();
    this.geoStore.clear();
    for (const m of this.markers.values()) { m.glow.dispose(); (m.pulse?.material as THREE.SpriteMaterial | undefined)?.dispose(); }
    this.markerRoot.traverse(o => { const m = o as THREE.Mesh; if (m.geometry) m.geometry.dispose(); });
    this.markers.clear();
    this.backdrop.dispose(); this.materials.dispose(); this.textures.dispose();
    this.root.clear();
  }
}

export { compact };
