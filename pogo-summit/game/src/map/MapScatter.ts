/**
 * MapScatter — deterministic procedural placement (Visual V2 · phase 5).
 *
 * `expandScatter` turns one `visual.scatter` block into instance transforms. It is a pure function of
 * (scatter, entity transform, seed): the same map always dresses the same way, on every device, with no `Math.random`.
 */
import type { ScatterDef } from './schema';

export interface ScatterInstance {
  x: number; y: number; z: number;
  /** yaw about the vertical axis (degrees) and roll about the depth axis (degrees). */
  yaw: number; roll: number;
  scale: number;
  /** mesh variant index 0…variants−1 */
  variant: number;
  /** brightness multiplier ≈ 1 */
  tint: number;
}

/** mulberry32 — small, fast, well distributed; identical to the generator used by the legacy builders. */
export function rng32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Stable 32-bit hash of a string (entity ids → default seeds). */
export function hashString(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}

export const MAX_SCATTER = 4000;

export function scatterProblems(s: ScatterDef): string[] {
  const out: string[] = [];
  if (!Number.isFinite(s.count) || s.count < 1 || s.count > MAX_SCATTER) out.push(`scatter.count must be 1…${MAX_SCATTER}`);
  if (!Number.isFinite(s.width) || s.width <= 0) out.push('scatter.width must be > 0');
  if (s.height !== undefined && (!Number.isFinite(s.height) || s.height < 0)) out.push('scatter.height must be ≥ 0');
  if (s.scale && (s.scale.length !== 2 || !(s.scale[0] > 0) || s.scale[1] < s.scale[0])) out.push('scatter.scale must be [min, max] with 0 < min ≤ max');
  if (s.variants !== undefined && (!Number.isInteger(s.variants) || s.variants < 1 || s.variants > 16)) out.push('scatter.variants must be an integer 1…16');
  return out;
}

export interface ScatterBase { x: number; y: number; z: number; rotation: number; sx: number; sy: number }

/**
 * Instances inside the rectangle `width × height` centred on the base position (rotated with the entity). `count` is
 * multiplied by `density` (quality scaling, 0…1) — the first `⌈count·density⌉` instances are kept, so lower densities are
 * strict subsets of higher ones (no popping when quality changes).
 */
export function expandScatter(s: ScatterDef, base: ScatterBase, seed: number, density = 1): ScatterInstance[] {
  const rnd = rng32(seed ^ 0x9e3779b9);
  const total = Math.max(1, Math.min(MAX_SCATTER, Math.round(s.count)));
  const keep = Math.max(1, Math.ceil(total * Math.max(0, Math.min(1, density))));
  const w = s.width * base.sx, h = (s.height ?? 0) * base.sy;
  const [smin, smax] = s.scale ?? [0.8, 1.25];
  const variants = Math.max(1, Math.round(s.variants ?? 4));
  const spacing = s.spacing ?? 0;
  const rot = ((base.rotation ?? 0) * Math.PI) / 180, cr = Math.cos(rot), sr = Math.sin(rot);
  const placed: { x: number; y: number }[] = [];
  const out: ScatterInstance[] = [];
  for (let i = 0; i < total && out.length < keep; i++) {
    // draw every random number for every index (even when rejected) so subsets stay stable
    let lx = (rnd() - 0.5) * w, ly = (rnd() - 0.5) * h;
    const sc = smin + rnd() * (smax - smin);
    const yaw = (rnd() * 2 - 1) * (s.rotation ?? 180), roll = (rnd() * 2 - 1) * (s.rotation !== undefined ? Math.min(12, s.rotation) : 8);
    const jy = (rnd() * 2 - 1) * (s.yJitter ?? 0), jz = (rnd() * 2 - 1) * (s.zJitter ?? 0);
    const variant = Math.floor(rnd() * variants) % variants, tintR = rnd();
    if (spacing > 0) {
      let tries = 0;
      while (tries++ < 6 && placed.some(p => (p.x - lx) ** 2 + (p.y - ly) ** 2 < spacing * spacing)) { lx = (rnd() - 0.5) * w; ly = (rnd() - 0.5) * h; }
      if (placed.some(p => (p.x - lx) ** 2 + (p.y - ly) ** 2 < spacing * spacing * 0.5)) continue;
      placed.push({ x: lx, y: ly });
    }
    const tv = s.tintVariance ?? 0.12;
    out.push({
      x: base.x + lx * cr - ly * sr, y: base.y + lx * sr + ly * cr + jy, z: base.z + jz,
      yaw, roll, scale: sc, variant, tint: 1 + (tintR * 2 - 1) * tv,
    });
  }
  return out;
}
