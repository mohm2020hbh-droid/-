/**
 * MapAssets — catalogue of the assets Map System V2 ships *as code* (Visual V2 · phases 1, 3, 5): procedural textures and
 * builtin mesh generators. Pure data: the Three.js implementations live in `src/render/map/` and are checked against this
 * catalogue by tests (triangle counts), so the validator's budgets describe what the renderer really builds.
 *
 *   builtin:<name>   a procedural mesh generator (parameters in `visual.meshParams`)
 *   proc:<name>      a procedural texture (usable as baseColorMap / normalMap / emissiveMap of any material)
 */
import type { Json, RenderLayer } from './schema';

// ── procedural textures ─────────────────────────────────────────────────────────────────────────────────────────
export interface ProceduralTextureInfo { size: number; kind: 'albedo' | 'normal' | 'mask' }
/** All square RGBA8 with a full mip chain (×4/3), generated once at start-up (~ms) — no binary asset. */
export const PROCEDURAL_TEXTURES: Record<string, ProceduralTextureInfo> = {
  rock: { size: 128, kind: 'albedo' }, rock_n: { size: 128, kind: 'normal' },
  grass: { size: 128, kind: 'albedo' }, grass_n: { size: 128, kind: 'normal' },
  snow: { size: 128, kind: 'albedo' }, snow_n: { size: 128, kind: 'normal' },
  ash: { size: 128, kind: 'albedo' }, ash_n: { size: 128, kind: 'normal' },
  moss: { size: 128, kind: 'albedo' },
  wood: { size: 128, kind: 'albedo' }, wood_n: { size: 128, kind: 'normal' },
  brick: { size: 128, kind: 'albedo' }, brick_n: { size: 128, kind: 'normal' },
  ice_n: { size: 128, kind: 'normal' },
  water_n: { size: 128, kind: 'normal' },
  lava: { size: 128, kind: 'mask' }, lava_n: { size: 128, kind: 'normal' },
  crystal_n: { size: 128, kind: 'normal' },
  foliage: { size: 64, kind: 'albedo' }, foliage_n: { size: 64, kind: 'normal' },
  cloud: { size: 64, kind: 'albedo' },
};
export const isKnownProceduralTexture = (ref: string): boolean => ref.startsWith('proc:') && ref.slice(5) in PROCEDURAL_TEXTURES;
export const textureBytes = (size: number): number => Math.round(size * size * 4 * (4 / 3));

// ── roles / slots ───────────────────────────────────────────────────────────────────────────────────────────────
/**
 * A mesh is made of *parts*; each part has a role and every role maps to a theme material slot ("@rock" …).
 * `visual.materials[role]` overrides one role, `visual.material` overrides the mesh's first role.
 */
export const ROLE_SLOT: Record<string, string> = {
  body: 'rock', cap: 'ground', wood: 'secondary', ice: 'ice', stone: 'stone', foliage: 'foliage', trunk: 'trunk',
  water: 'water', lava: 'lava', crystal: 'crystal', cloud: 'cloud', snow: 'cloud', glow: 'glow', bounce: 'bounce',
};

// ── builtin meshes ──────────────────────────────────────────────────────────────────────────────────────────────
export type MeshParams = Record<string, Json>;
export interface BuiltinMeshInfo {
  id: string;
  label: string;
  /** Parameter defaults (also the list of accepted parameter names). */
  defaults: MeshParams;
  /** Roles (parts) the mesh produces for the given parameters; the first is the primary role. */
  roles(p: MeshParams): string[];
  /** Triangles of ONE instance at LOD 0/1/2. */
  tris(p: MeshParams, lod: 0 | 1 | 2): number;
  /** Depth layer when the author does not choose one. */
  layer: RenderLayer;
  /** True for meshes meant to be repeated (scatter / instancing) — they have `variants` of different silhouettes. */
  repeat: boolean;
}

const n = (p: MeshParams, k: string, d: number): number => { const v = p[k]; return typeof v === 'number' && Number.isFinite(v) ? v : d; };
const s = (p: MeshParams, k: string, d: string): string => { const v = p[k]; return typeof v === 'string' ? v : d; };
const lodPick = (lod: 0 | 1 | 2, a: number, b: number, c: number): number => (lod === 0 ? a : lod === 1 ? b : c);
/** Triangles of a RoundedBoxGeometry with `seg` rounding segments: 6 faces × (2·seg+1)² quads × 2. */
export const roundedBoxTris = (seg: number): number => 12 * (2 * seg + 1) ** 2;
/** Icosphere faces for a subdivision detail: 20·(detail+1)². */
export const icoTris = (detail: number): number => 20 * (detail + 1) ** 2;

export const BUILTIN_MESHES: Record<string, BuiltinMeshInfo> = {};
const def = (info: BuiltinMeshInfo): void => { BUILTIN_MESHES[info.id] = info; };

def({
  id: 'builtin:platform', label: 'Rounded platform slab', layer: 'gameplay', repeat: false,
  defaults: { w: 6, h: 3, taper: 0.72, depth: 5, seed: 1, style: 'rock', radius: 0 },
  roles: p => { const st = s(p, 'style', 'rock'); return st === 'wood' ? ['wood'] : st === 'ice' ? ['ice'] : st === 'bounce' ? ['bounce', 'stone'] : st === 'ruin' ? ['stone'] : st === 'crystal' ? ['crystal'] : ['body', 'cap']; },
  tris: (_p, lod) => roundedBoxTris(lodPick(lod, 3, 2, 1)),
});
def({
  id: 'builtin:slab', label: 'Rounded box', layer: 'gameplay', repeat: true,
  defaults: { w: 2, h: 2, d: 2, radius: 0.25, seed: 1 },
  roles: () => ['body'],
  tris: (_p, lod) => roundedBoxTris(lodPick(lod, 2, 1, 0)),
});
def({
  id: 'builtin:poly_slab', label: 'Extruded convex polygon (slopes, wedges)', layer: 'gameplay', repeat: false,
  defaults: { points: [], depth: 4, seed: 1, bevel: 0.25 },
  roles: () => ['body', 'cap'],
  tris: (p, _lod) => 8 * (Array.isArray(p.points) && p.points.length >= 3 ? p.points.length : 4),
});
def({
  id: 'builtin:rock', label: 'Boulder', layer: 'midground', repeat: true,
  defaults: { size: 1.5, seed: 1, flat: 0.7, variants: 4 },
  roles: () => ['body'],
  tris: (_p, lod) => icoTris(lodPick(lod, 2, 1, 0)),
});
def({
  id: 'builtin:cliff', label: 'Terraced cliff face', layer: 'gameplay', repeat: false,
  defaults: { w: 6, h: 20, depth: 6, seed: 1, terraces: 4 },
  roles: () => ['body', 'cap'],
  tris: (p, lod) => Math.max(1, Math.round(n(p, 'terraces', 4))) * roundedBoxTris(lodPick(lod, 2, 1, 0)),
});
def({
  id: 'builtin:tree', label: 'Tree', layer: 'midground', repeat: true,
  defaults: { kind: 'pine', height: 7, seed: 1, variants: 4 },
  roles: p => { const k = s(p, 'kind', 'pine'); return k === 'dead' ? ['trunk'] : k === 'crystal' ? ['trunk', 'crystal'] : ['trunk', 'foliage']; },
  tris: (p, lod) => {
    const k = s(p, 'kind', 'pine'), trunk = 2 * lodPick(lod, 6, 5, 4);
    if (k === 'pine') return trunk + lodPick(lod, 4, 3, 2) * 2 * lodPick(lod, 8, 6, 5);
    if (k === 'broadleaf') return trunk + lodPick(lod, 4, 4, 2) * icoTris(lodPick(lod, 1, 0, 0));
    if (k === 'dead') return trunk + lodPick(lod, 3, 2, 1) * 10;
    return trunk + lodPick(lod, 3, 2, 1) * 3 * lodPick(lod, 6, 4, 4);
  },
});
def({
  id: 'builtin:bush', label: 'Bush', layer: 'gameplay', repeat: true,
  defaults: { size: 1.2, seed: 1, variants: 4 },
  roles: () => ['foliage'],
  tris: (_p, lod) => lodPick(lod, 3, 3, 1) * icoTris(lodPick(lod, 1, 0, 0)),
});
def({
  id: 'builtin:grass', label: 'Grass tuft', layer: 'foreground', repeat: true,
  defaults: { blades: 12, height: 0.9, seed: 1, variants: 4 },
  roles: () => ['foliage'],
  tris: (p, lod) => Math.max(2, Math.round(n(p, 'blades', 12) * lodPick(lod, 1, 0.6, 0.3))) * 3,
});
def({
  id: 'builtin:mountain', label: 'Mountain / ridge', layer: 'background', repeat: false,
  defaults: { w: 120, h: 50, depth: 40, seed: 1, profile: 'ridge', snow: true },
  roles: p => (p.snow === false ? ['body'] : ['body', 'snow']),
  tris: (p, lod) => { const cols = Math.max(8, Math.round(n(p, 'w', 120) / 3.4)); return lodPick(lod, 2 * cols * 8, 2 * Math.max(2, cols >> 1) * 4, 2 * Math.max(2, cols >> 2) * 2) + 12; },
});
def({
  id: 'builtin:cloud', label: 'Stylised cloud', layer: 'background', repeat: true,
  defaults: { w: 14, h: 4, seed: 1, variants: 4 },
  roles: () => ['cloud'],
  tris: (_p, lod) => lodPick(lod, 6 * icoTris(1), 6 * icoTris(0), 3 * icoTris(0)),
});
def({
  id: 'builtin:water', label: 'Water body', layer: 'gameplay', repeat: false,
  defaults: { w: 12, h: 0.4, depth: 4, seed: 1 },
  roles: () => ['water'],
  tris: (p, lod) => { const cols = Math.max(2, Math.round(n(p, 'w', 12) / 2)); return lodPick(lod, 2 * cols * 3 + 4, 2 * Math.max(1, cols >> 1) * 2 + 2, 4); },
});
def({
  id: 'builtin:waterfall', label: 'Waterfall', layer: 'midground', repeat: false,
  defaults: { w: 3, h: 16, seed: 1 },
  roles: () => ['water', 'stone'],
  tris: (p, lod) => { const rows = Math.max(4, Math.round(n(p, 'h', 16) / 1.5)); return lodPick(lod, 2 * 3 * rows + 2 * roundedBoxTris(1), 2 * 2 * (rows >> 1) + 2 * roundedBoxTris(0), 2 * 1 * 2 + 2 * roundedBoxTris(0)); },
});
def({
  id: 'builtin:crystal_cluster', label: 'Crystal cluster', layer: 'gameplay', repeat: true,
  defaults: { count: 6, height: 3, spread: 1.4, seed: 1, variants: 4 },
  roles: () => ['crystal'],
  tris: (p, lod) => Math.max(1, Math.round(n(p, 'count', 6))) * lodPick(lod, 18, 12, 4),
});
def({
  id: 'builtin:ancient_structure', label: 'Ancient structure (arch, gate, pillar, wall, obelisk, tower)', layer: 'midground', repeat: false,
  defaults: { kind: 'arch', w: 8, h: 10, seed: 1, glow: false },
  roles: p => (p.glow === true ? ['stone', 'glow'] : ['stone']),
  tris: (p, lod) => {
    const k = s(p, 'kind', 'arch');
    const parts = k === 'arch' ? 5 : k === 'gate' ? 6 : k === 'pillar' ? 3 : k === 'wall' ? 6 : k === 'obelisk' ? 2 : 7;
    return lodPick(lod, parts, parts, Math.ceil(parts / 2)) * roundedBoxTris(lodPick(lod, 1, 0, 0)) + (p.glow === true ? 6 : 0);
  },
});
def({
  id: 'builtin:spikes', label: 'Spike / crystal row (hazard look)', layer: 'gameplay', repeat: false,
  defaults: { width: 3.2, height: 1.5, count: 5, seed: 1, style: 'crystals' },
  roles: () => ['crystal'],
  tris: (p, lod) => Math.max(1, Math.round(n(p, 'count', 5))) * lodPick(lod, 18, 12, 4),
});
def({
  id: 'builtin:blade', label: 'Rotating blade', layer: 'gameplay', repeat: false,
  defaults: { length: 8, thickness: 0.8, depth: 1.2, seed: 1 },
  roles: () => ['stone', 'glow'],
  tris: (_p, lod) => roundedBoxTris(lodPick(lod, 2, 1, 0)) + 4 * lodPick(lod, 10, 6, 4),
});
def({
  id: 'builtin:island', label: 'Floating island', layer: 'background', repeat: true,
  defaults: { radius: 6, seed: 1, variants: 4 },
  roles: () => ['body', 'cap'],
  tris: (_p, lod) => icoTris(lodPick(lod, 2, 1, 0)),
});
def({
  id: 'builtin:marker', label: 'Checkpoint / start / finish marker', layer: 'gameplay', repeat: false,
  defaults: { kind: 'checkpoint', height: 4, w: 3 },
  roles: () => ['stone', 'glow'],
  tris: (p, lod) => (s(p, 'kind', 'checkpoint') === 'finish' ? 3 : 2) * roundedBoxTris(lodPick(lod, 1, 0, 0)) + 2,
});

export const isBuiltinMesh = (id: string): boolean => id in BUILTIN_MESHES;
export const BUILTIN_MESH_IDS: string[] = Object.keys(BUILTIN_MESHES);

/** Legacy `visual.style` names (migrated levels, pre-V2 prefabs) → the builtin generator that dresses them. */
export const STYLE_TO_MESH: Record<string, { mesh: string; params?: MeshParams }> = {
  rock: { mesh: 'builtin:platform', params: { style: 'rock' } },
  goal: { mesh: 'builtin:platform', params: { style: 'rock' } },
  special: { mesh: 'builtin:platform', params: { style: 'crystal' } },
  wood: { mesh: 'builtin:platform', params: { style: 'wood', taper: 0.9 } },
  ice: { mesh: 'builtin:platform', params: { style: 'ice' } },
  bounce: { mesh: 'builtin:platform', params: { style: 'bounce' } },
  ruin: { mesh: 'builtin:platform', params: { style: 'ruin' } },
  lava: { mesh: 'builtin:platform', params: { style: 'crystal' } },
  cliff: { mesh: 'builtin:cliff' },
  wall: { mesh: 'builtin:cliff', params: { terraces: 2 } },
  ceiling: { mesh: 'builtin:cliff', params: { terraces: 2 } },
  crystals: { mesh: 'builtin:spikes', params: { style: 'crystals' } },
  spikes: { mesh: 'builtin:spikes', params: { style: 'spikes' } },
  thorns: { mesh: 'builtin:spikes', params: { style: 'thorns' } },
  blade: { mesh: 'builtin:blade' },
  toggle: { mesh: 'builtin:slab' },
  timed: { mesh: 'builtin:slab' },
  boost: { mesh: 'builtin:slab', params: { radius: 0.4 } },
  tree_cluster: { mesh: 'builtin:tree' },
  rock_cluster: { mesh: 'builtin:rock' },
  mountain: { mesh: 'builtin:mountain' },
  flowers: { mesh: 'builtin:grass' },
  checkpoint: { mesh: 'builtin:marker', params: { kind: 'checkpoint' } },
  start_line: { mesh: 'builtin:marker', params: { kind: 'start' } },
  finish_line: { mesh: 'builtin:marker', params: { kind: 'finish' } },
};

/** Parameters with catalogue defaults applied (unknown parameter names are kept: they may belong to a LOD mesh). */
export function meshParams(meshId: string, given: MeshParams | undefined): MeshParams {
  const info = BUILTIN_MESHES[meshId];
  return { ...(info?.defaults ?? {}), ...(given ?? {}) };
}

/** Problems of a parameter set (validator MESH_PARAM). */
export function meshParamProblems(meshId: string, given: MeshParams | undefined): string[] {
  const info = BUILTIN_MESHES[meshId];
  if (!info) return [`unknown builtin mesh "${meshId}"`];
  const out: string[] = [];
  for (const [k, v] of Object.entries(given ?? {})) {
    if (!(k in info.defaults)) { out.push(`"${k}" is not a parameter of ${meshId} (known: ${Object.keys(info.defaults).join(', ')})`); continue; }
    const d = info.defaults[k];
    if (typeof d === 'number' && (typeof v !== 'number' || !Number.isFinite(v))) out.push(`${k} must be a finite number`);
    else if (typeof d === 'string' && typeof v !== 'string') out.push(`${k} must be a string`);
    else if (typeof d === 'boolean' && typeof v !== 'boolean') out.push(`${k} must be a boolean`);
  }
  return out;
}
