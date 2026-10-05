import * as THREE from 'three';
import { PROCEDURAL_TEXTURES, textureBytes } from '../../map/MapAssets';
import type { AssetRef } from '../../map/schema';

/**
 * TextureLibrary — procedural tileable textures (no binary assets) and package textures, with reuse and memory accounting.
 * Albedo maps are neutral-grey multipliers (≈ 0.55–1.0) so a material's `baseColor` stays the dominant colour; normal maps
 * are derived from tileable height fields. All procedural textures are 128² (64² for foliage/cloud) with mip chains
 * (≈ 87 KB / 22 KB each) and are generated lazily — only what the loaded map's materials reference.
 */

// ── tileable noise ──────────────────────────────────────────────────────────────────────────────────────────────
function hash(x: number, y: number, s: number): number {
  let h = (Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(s | 0, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}
function pnoise(u: number, v: number, period: number, seed: number): number {
  const x = u * period, y = v * period, xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const sx = xf * xf * (3 - 2 * xf), sy = yf * yf * (3 - 2 * yf);
  const w = (i: number) => ((i % period) + period) % period;
  const a = hash(w(xi), w(yi), seed), b = hash(w(xi + 1), w(yi), seed), c = hash(w(xi), w(yi + 1), seed), d = hash(w(xi + 1), w(yi + 1), seed);
  return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
}
function pfbm(u: number, v: number, period: number, seed: number, oct: number): number {
  let s = 0, amp = 0.5, tot = 0, p = period;
  for (let i = 0; i < oct; i++) { s += pnoise(u, v, p, seed + i * 17) * amp; tot += amp; amp *= 0.5; p *= 2; }
  return s / tot;
}
/** Periodic Voronoi: [f1, f2, cellId]. */
function pvoronoi(u: number, v: number, cells: number, seed: number): [number, number, number] {
  const x = u * cells, y = v * cells, xi = Math.floor(x), yi = Math.floor(y);
  let f1 = 9, f2 = 9, id = 0;
  for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) {
    const cx = xi + i, cy = yi + j, wx = ((cx % cells) + cells) % cells, wy = ((cy % cells) + cells) % cells;
    const px = cx + hash(wx, wy, seed), py = cy + hash(wx, wy, seed + 7);
    const d = Math.hypot(px - x, py - y);
    if (d < f1) { f2 = f1; f1 = d; id = hash(wx, wy, seed + 13); } else if (d < f2) f2 = d;
  }
  return [f1, f2, id];
}
const sstep = (e0: number, e1: number, x: number): number => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };

type Field = (u: number, v: number) => number;
/** Height fields (0..1). */
const HEIGHT: Record<string, Field> = {
  rock: (u, v) => {
    const f = pfbm(u, v, 4, 11, 5), [a, b] = pvoronoi(u, v, 6, 3), crack = sstep(0, 0.12, b - a), strata = 0.5 + 0.5 * Math.sin((v + pfbm(u, v, 4, 31, 2) * 0.12) * Math.PI * 2 * 7);
    return 0.35 + f * 0.4 + crack * 0.2 + strata * 0.05;
  },
  grass: (u, v) => 0.3 + pfbm(u, v, 8, 51, 3) * 0.45 + (pnoise(u, v, 64, 61) * 0.5 + pnoise(u, v, 128, 67) * 0.5 - 0.5) * 0.4,
  snow: (u, v) => 0.7 + pfbm(u, v, 6, 71, 3) * 0.25 + (pnoise(u, v, 96, 77) - 0.5) * 0.06,
  ash: (u, v) => 0.4 + pfbm(u, v, 10, 81, 4) * 0.45 + (pnoise(u, v, 64, 83) - 0.5) * 0.18,
  moss: (u, v) => 0.4 + pfbm(u, v, 6, 91, 4) * 0.5,
  wood: (u, v) => { const grain = pnoise(u * 0.5, v, 32, 101) * 0.6 + pfbm(u, v, 12, 103, 3) * 0.4; const ring = 0.5 + 0.5 * Math.sin((v * 18 + pfbm(u, v, 3, 105, 2) * 3) * Math.PI); return 0.35 + grain * 0.4 + ring * 0.2; },
  brick: (u, v) => {
    const rows = 8, cols = 4, ry = v * rows, row = Math.floor(ry), off = row % 2 ? 0.5 : 0, cx = u * cols + off;
    const fx = cx - Math.floor(cx), fy = ry - row;
    const mortar = Math.min(sstep(0, 0.07, fx) * sstep(0, 0.07, 1 - fx), sstep(0, 0.12, fy) * sstep(0, 0.12, 1 - fy));
    return mortar * (0.6 + 0.3 * pfbm(u, v, 12, 111, 3) + 0.1 * hash(Math.floor(cx), row, 5)) + 0.05;
  },
  lava: (u, v) => { const [a, b] = pvoronoi(u, v, 5, 121); const crust = sstep(0.0, 0.16, b - a); return 0.15 + crust * 0.6 + pfbm(u, v, 8, 123, 3) * 0.25; },
  foliage: (u, v) => 0.3 + pfbm(u, v, 5, 131, 3) * 0.5 + pnoise(u, v, 24, 133) * 0.2,
  cloud: (u, v) => 0.6 + pfbm(u, v, 3, 141, 3) * 0.4,
};

function toNormal(h: Float32Array, n: number, strength: number): Uint8Array {
  const out = new Uint8Array(n * n * 4);
  const at = (x: number, y: number) => h[((y + n) % n) * n + ((x + n) % n)];
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const dx = (at(x + 1, y) - at(x - 1, y)) * strength, dy = (at(x, y + 1) - at(x, y - 1)) * strength;
    const l = Math.hypot(dx, dy, 1), i = (y * n + x) * 4;
    out[i] = Math.round((-dx / l * 0.5 + 0.5) * 255); out[i + 1] = Math.round((dy / l * 0.5 + 0.5) * 255); out[i + 2] = Math.round((1 / l * 0.5 + 0.5) * 255); out[i + 3] = 255;
  }
  return out;
}

function heightField(name: string, n: number): Float32Array {
  const f = HEIGHT[name], h = new Float32Array(n * n);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) h[y * n + x] = f(x / n, y / n);
  return h;
}

/** Faceted normals: each Voronoi cell is a flat plane tilted at random (ice, crystals). */
function facetNormals(n: number, cells: number, seed: number, tilt: number): Uint8Array {
  const out = new Uint8Array(n * n * 4);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const [a, b, id] = pvoronoi(x / n, y / n, cells, seed);
    const ang = id * 6.2832, t = tilt * (0.4 + 0.6 * hash(Math.floor(id * 1000), 1, seed)), edge = sstep(0, 0.05, b - a);
    let nx = Math.cos(ang) * t * edge, ny = Math.sin(ang) * t * edge;
    nx += (pnoise(x / n, y / n, 24, seed + 1) - 0.5) * 0.08; ny += (pnoise(x / n, y / n, 24, seed + 2) - 0.5) * 0.08;
    const l = Math.hypot(nx, ny, 1), i = (y * n + x) * 4;
    out[i] = Math.round((-nx / l * 0.5 + 0.5) * 255); out[i + 1] = Math.round((ny / l * 0.5 + 0.5) * 255); out[i + 2] = Math.round((1 / l * 0.5 + 0.5) * 255); out[i + 3] = 255;
  }
  return out;
}

function waterNormals(n: number): Uint8Array {
  const h = new Float32Array(n * n);
  const waves = [[3, 1, 0.5], [2, 5, 0.3], [6, 2, 0.2], [1, 7, 0.15]];
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const u = x / n, v = y / n;
    let s = 0;
    for (const [kx, ky, a] of waves) s += Math.sin((u * kx + v * ky + pfbm(u, v, 4, 151, 2) * 0.6) * Math.PI * 2) * a;
    h[y * n + x] = 0.5 + s * 0.3 + (pfbm(u, v, 8, 153, 3) - 0.5) * 0.3;
  }
  return toNormal(h, n, 3.2);
}

function albedoFrom(name: string, n: number): Uint8Array {
  const h = heightField(name, n), out = new Uint8Array(n * n * 4);
  let lo = Infinity, hi = -Infinity;
  for (const v of h) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
  // neutral multiplier ≈ 0.55…1.0 (lava: dark crust … bright cracks used as emissive map too)
  const dark = name === 'lava' ? 0.08 : name === 'snow' || name === 'cloud' ? 0.82 : 0.55;
  for (let i = 0; i < h.length; i++) {
    let t = (h[i] - lo) / Math.max(1e-6, hi - lo);
    if (name === 'lava') t = 1 - t;            // cracks (low height) glow
    const v = Math.round((dark + (1 - dark) * t) * 255);
    out[i * 4] = v; out[i * 4 + 1] = v; out[i * 4 + 2] = v; out[i * 4 + 3] = 255;
  }
  return out;
}

const NORMAL_SOURCE: Record<string, { field: string; strength: number }> = {
  rock_n: { field: 'rock', strength: 3.2 }, grass_n: { field: 'grass', strength: 1.6 }, snow_n: { field: 'snow', strength: 1.4 }, ash_n: { field: 'ash', strength: 2 },
  wood_n: { field: 'wood', strength: 2.2 }, brick_n: { field: 'brick', strength: 4 }, lava_n: { field: 'lava', strength: 2.6 }, foliage_n: { field: 'foliage', strength: 2 },
};

/** RGBA8 pixels of a procedural texture. */
export function proceduralPixels(name: string): Uint8Array {
  const info = PROCEDURAL_TEXTURES[name];
  if (!info) throw new Error(`unknown procedural texture "${name}"`);
  const n = info.size;
  if (name === 'ice_n') return facetNormals(n, 7, 161, 0.55);
  if (name === 'crystal_n') return facetNormals(n, 5, 171, 0.8);
  if (name === 'water_n') return waterNormals(n);
  const src = NORMAL_SOURCE[name];
  if (src) return toNormal(heightField(src.field, n), n, src.strength);
  return albedoFrom(name, n);
}

export interface AssetBytes { read(path: string): Uint8Array | null }

export class TextureLibrary {
  private readonly cache = new Map<string, THREE.Texture>();
  private readonly users = new Map<string, number>();
  private readonly assets = new Map<string, AssetRef>();
  private bytesTotal = 0;
  generated = 0;
  constructor(assets: readonly AssetRef[] = [], private readonly source: AssetBytes | null = null) { for (const a of assets) this.assets.set(a.id, a); }

  /** GPU memory of every texture held (RGBA8 + mip chain). */
  get bytes(): number { return this.bytesTotal; }
  get count(): number { return this.cache.size; }
  keys(): string[] { return [...this.cache.keys()]; }

  /** Shared base texture for a reference (`proc:*` or an asset id). `srgb` selects the colour space (albedo/emissive). */
  get(ref: string, srgb: boolean): THREE.Texture {
    const key = `${ref}|${srgb ? 's' : 'l'}`;
    let t = this.cache.get(key);
    if (t) { this.users.set(key, (this.users.get(key) ?? 0) + 1); return t; }
    if (ref.startsWith('proc:')) {
      const name = ref.slice(5), info = PROCEDURAL_TEXTURES[name];
      const px = proceduralPixels(name);
      t = new THREE.DataTexture(px, info.size, info.size, THREE.RGBAFormat);
      this.bytesTotal += textureBytes(info.size);
      this.generated++;
    } else {
      t = this.placeholder();
      const a = this.assets.get(ref);
      const data = a && this.source ? this.source.read(a.path) : null;
      if (a && data) void this.decode(t, data, a);
      this.bytesTotal += textureBytes(Math.max(a?.width ?? 4, a?.height ?? 4));
    }
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.magFilter = THREE.LinearFilter; t.minFilter = THREE.LinearMipmapLinearFilter; t.generateMipmaps = true; t.anisotropy = 2;
    t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
    t.needsUpdate = true;
    this.cache.set(key, t); this.users.set(key, 1);
    return t;
  }

  private placeholder(): THREE.DataTexture { return new THREE.DataTexture(new Uint8Array([200, 200, 200, 255]), 1, 1, THREE.RGBAFormat); }

  /** Decode a PNG/JPEG/WebP package texture without blocking; the placeholder is replaced when ready. */
  private async decode(target: THREE.Texture, bytes: Uint8Array, a: AssetRef): Promise<void> {
    try {
      const mime = /\.jpe?g$/i.test(a.path) ? 'image/jpeg' : /\.webp$/i.test(a.path) ? 'image/webp' : 'image/png';
      const bmp = await createImageBitmap(new Blob([bytes as BlobPart], { type: mime }), { imageOrientation: 'flipY' });
      target.image = bmp as unknown as HTMLImageElement;
      target.needsUpdate = true;
    } catch { /* keep the placeholder: a broken texture must never break the map */ }
  }

  dispose(): void { for (const t of this.cache.values()) t.dispose(); this.cache.clear(); this.users.clear(); this.bytesTotal = 0; }
}
