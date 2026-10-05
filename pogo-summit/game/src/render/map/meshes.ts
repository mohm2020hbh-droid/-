import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';
import { fbm3, mulberry32, noise3 } from '../noise';
import { type MeshParams, meshParams } from '../../map/MapAssets';
import { type MeshPart, compact, finalize, lumpy, merge, place, skyAo, split, triCount, weld } from './geo';

/**
 * Builtin mesh generators of Visual V2 (originals — no legacy models). Every generator returns *parts* (role → geometry);
 * the renderer gives each role its theme material. Geometry is rounded/smooth-shaded low-poly with baked ambient
 * occlusion in the vertex colours, three levels of detail and `variant`s (different silhouettes of the same family).
 * Triangle counts are mirrored by `BUILTIN_MESHES[id].tris` in src/map/MapAssets.ts (tests keep them in sync).
 */
export type Lod = 0 | 1 | 2;
type Gen = (p: MeshParams, lod: Lod, variant: number) => MeshPart[];

const num = (p: MeshParams, k: string, d: number): number => { const v = p[k]; return typeof v === 'number' && Number.isFinite(v) ? v : d; };
const str = (p: MeshParams, k: string, d: string): string => { const v = p[k]; return typeof v === 'string' ? v : d; };
const clamp = (v: number, a: number, b: number): number => Math.max(a, Math.min(b, v));
const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;
const smooth = (e0: number, e1: number, x: number): number => THREE.MathUtils.smoothstep(x, e0, e1);
const seedOf = (p: MeshParams, variant: number): number => ((Math.floor(num(p, 'seed', 1)) * 2654435761) ^ (variant * 40503)) >>> 0;
const pick3 = <T>(lod: Lod, a: T, b: T, c: T): T => (lod === 0 ? a : lod === 1 ? b : c);

/** Rounded box, top-centred? no: centred at the origin. Welded + smooth. */
function rbox(w: number, h: number, d: number, seg: number, r: number): THREE.BufferGeometry {
  // three's RoundedBoxGeometry returns a UNIT cube for 0 segments, so the cheapest level is a plain box
  if (seg <= 0) return weld(new THREE.BoxGeometry(w, h, d));
  const g = new RoundedBoxGeometry(w, h, d, seg, Math.max(0.001, Math.min(r, w / 2 - 1e-3, h / 2 - 1e-3, d / 2 - 1e-3)));
  return weld(g);
}

/** Displace every vertex along its normal by a noise field (amplitude in metres). */
function roughen(g: THREE.BufferGeometry, amp: (x: number, y: number, z: number, ny: number) => number, seed: number, freq = 0.45): void {
  const pos = g.getAttribute('position') as THREE.BufferAttribute, nor = g.getAttribute('normal') as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
    const k = (fbm3(x * freq + seed * 0.013, y * freq, z * freq, seed, 3) - 0.5) * 2 * amp(x, y, z, nor.getY(i));
    pos.setXYZ(i, x + nor.getX(i) * k, y + nor.getY(i) * k, z + nor.getZ(i) * k);
  }
}

/** Top cap (grass / snow) = upward-facing faces plus a ragged lip a little way down the sides. */
const capPicker = (capT: number, seed: number) => (cx: number, cy: number, cz: number, ny: number): boolean =>
  ny > 0.38 || cy > -capT * (0.55 + 0.9 * noise3(cx * 0.8, 0, cz * 0.8, seed));

// ── platform ────────────────────────────────────────────────────────────────────────────────────────────────────
const platform: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 6), h = num(p, 'h', 3), d = num(p, 'depth', 5), taper = clamp(num(p, 'taper', 0.72), 0.2, 1);
  const style = str(p, 'style', 'rock'), seed = seedOf(p, variant);
  const seg = pick3(lod, 3, 2, 1);
  const r = Math.max(0.05, num(p, 'radius', 0) > 0 ? num(p, 'radius', 0) : Math.min(0.55, h * 0.38, w * 0.2, d * 0.2));
  const g = rbox(w, h, d, seg, r);
  const pos = g.getAttribute('position') as THREE.BufferAttribute;
  // shape: top surface at y = 0, bottom at −h, front face a little in front of the gameplay plane
  const rough = style === 'rock' || style === 'crystal' ? 1 : style === 'ruin' ? 0.45 : 0.12;
  for (let i = 0; i < pos.count; i++) {
    const t = (pos.getY(i) + h / 2) / h;                       // 0 bottom … 1 top
    pos.setXYZ(i, pos.getX(i) * lerp(taper, 1, t), pos.getY(i) - h / 2, pos.getZ(i) - d * 0.2);
  }
  g.computeVertexNormals();
  if (rough > 0 && lod < 2) roughen(g, (_x, y, _z, ny) => rough * Math.min(0.55, 0.12 * Math.min(w, h * 2)) * (1 - 0.88 * smooth(0.6, 0.95, ny)) * (ny < -0.3 ? 1.5 : 1) * (y > -0.2 ? 0.5 : 1), seed);
  g.computeVertexNormals();
  finalize(g, skyAo(-h, h, 1));
  if (style === 'wood') return [{ role: 'wood', geometry: g }];
  if (style === 'ice') return [{ role: 'ice', geometry: g }];
  if (style === 'ruin') return [{ role: 'stone', geometry: g }];
  if (style === 'crystal') return [{ role: 'crystal', geometry: g }];
  if (style === 'bounce') { const [pad, base] = split(g, (_cx, cy) => cy > -h * 0.42); return [{ role: 'bounce', geometry: compact(pad) }, { role: 'stone', geometry: compact(base) }]; }
  const [cap, body] = split(g, capPicker(Math.min(0.5, h * 0.25), seed));
  return [{ role: 'body', geometry: compact(body) }, { role: 'cap', geometry: compact(cap) }];
};

// ── slab / poly slab ────────────────────────────────────────────────────────────────────────────────────────────
const slab: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 2), h = num(p, 'h', 2), d = num(p, 'd', 2), r = num(p, 'radius', 0.25);
  const g = rbox(w, h, d, pick3(lod, 2, 1, 0), r);
  roughen(g, () => 0.02, seedOf(p, variant), 0.9);
  g.computeVertexNormals();
  return [{ role: 'body', geometry: finalize(g, skyAo(-h / 2, h, 0.8)) }];
};

const polySlab: Gen = (p, lod, variant) => {
  const raw = Array.isArray(p.points) ? (p.points as unknown as { x: number; y: number }[]) : [];
  const pts = raw.length >= 3 ? raw : [{ x: -2, y: -1 }, { x: 2, y: -1 }, { x: 2, y: 1 }, { x: -2, y: 1 }];
  const d = num(p, 'depth', 4), b = Math.min(num(p, 'bevel', 0.25), d * 0.3);
  const k = pts.length;
  let cx = 0, cy = 0; for (const q of pts) { cx += q.x; cy += q.y; } cx /= k; cy /= k;
  const inset = (q: { x: number; y: number }, amt: number) => { const dx = q.x - cx, dy = q.y - cy, l = Math.hypot(dx, dy) || 1; const s = Math.max(0, 1 - amt / l); return { x: cx + dx * s, y: cy + dy * s }; };
  const zf = d * 0.3, zb = -d * 0.7;
  const rings: { x: number; y: number; z: number }[][] = [
    pts.map(q => ({ ...inset(q, b), z: zf })), pts.map(q => ({ x: q.x, y: q.y, z: zf - b })),
    pts.map(q => ({ x: q.x, y: q.y, z: zb + b })), pts.map(q => ({ ...inset(q, b), z: zb })),
  ];
  const verts: number[] = []; const idx: number[] = [];
  rings.forEach(r => r.forEach(v => verts.push(v.x, v.y, v.z)));
  const fc = verts.length / 3; verts.push(cx, cy, zf);
  const bc = fc + 1; verts.push(cx, cy, zb);
  for (let i = 0; i < k; i++) { const j = (i + 1) % k; idx.push(fc, i, j); }                              // front fan (CCW seen from +z)
  for (let r = 0; r < 3; r++) for (let i = 0; i < k; i++) { const j = (i + 1) % k; const a = r * k + i, bb = r * k + j, c = (r + 1) * k + i, e = (r + 1) * k + j; idx.push(a, c, bb, bb, c, e); }
  for (let i = 0; i < k; i++) { const j = (i + 1) % k; idx.push(bc, 3 * k + j, 3 * k + i); }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
  g.setIndex(idx);
  const w = weld(g);
  void lod; void variant;
  const minY = Math.min(...pts.map(q => q.y)), maxY = Math.max(...pts.map(q => q.y));
  finalize(w, skyAo(minY, maxY - minY, 0.9));
  const [cap, body] = split(w, (_cx, _cy, _cz, ny) => ny > 0.45);
  return [{ role: 'body', geometry: compact(body) }, { role: 'cap', geometry: compact(cap) }];
};

// ── rock / cliff ────────────────────────────────────────────────────────────────────────────────────────────────
const rock: Gen = (p, lod, variant) => {
  const size = num(p, 'size', 1.5), flat = clamp(num(p, 'flat', 0.7), 0.3, 1.2), seed = seedOf(p, variant);
  const g = lumpy(pick3<number>(lod, 2, 1, 0), 0.34, (x, y, z) => fbm3(x + seed * 0.01, y, z, seed, 2), 1, flat, 0.9 + (seed % 7) * 0.03);
  const pos = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) pos.setY(i, Math.max(pos.getY(i), -0.55 * flat));
  place(g, 0, 0.55 * flat, 0, 0, (seed % 360) * 0.0174, 0, size, size, size);
  g.computeVertexNormals();
  return [{ role: 'body', geometry: finalize(g, skyAo(0, size * flat, 1)) }];
};

const cliff: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 6), h = num(p, 'h', 20), d = num(p, 'depth', 6), n = Math.max(1, Math.round(num(p, 'terraces', 4))), seed = seedOf(p, variant);
  const rnd = mulberry32(seed);
  const caps: THREE.BufferGeometry[] = [], bodies: THREE.BufferGeometry[] = [];
  const bh = h / n;
  for (let i = 0; i < n; i++) {
    const wi = w * (0.8 + 0.2 * (i / Math.max(1, n - 1))) * (0.92 + rnd() * 0.16), di = d * (0.78 + 0.22 * (i / Math.max(1, n - 1)));
    const g = rbox(wi, bh * 1.04, di, pick3(lod, 2, 1, 0), Math.min(0.5, bh * 0.3, wi * 0.15));
    const pos = g.getAttribute('position') as THREE.BufferAttribute;
    const ox = (rnd() - 0.5) * w * 0.12;
    for (let k = 0; k < pos.count; k++) { const t = (pos.getY(k) + bh * 0.52) / (bh * 1.04); pos.setXYZ(k, pos.getX(k) * lerp(0.9, 1, t) + ox, pos.getY(k) - i * bh - bh * 0.52, pos.getZ(k) - di * 0.2); }
    g.computeVertexNormals();
    if (lod < 2) roughen(g, (_x, _y, _z, ny) => 0.3 * (1 - 0.85 * smooth(0.6, 0.95, ny)), seed + i * 31, 0.35);
    g.computeVertexNormals();
    finalize(g, skyAo(-h, h * 0.5, 1));
    const [cap, body] = split(g, capPicker(0.35, seed + i));
    caps.push(compact(cap)); bodies.push(compact(body));
  }
  return [{ role: 'body', geometry: merge(bodies) }, { role: 'cap', geometry: merge(caps) }];
};

// ── trees / bush / grass ────────────────────────────────────────────────────────────────────────────────────────
function tube(rTop: number, rBot: number, h: number, radial: number, y: number, lean = 0): THREE.BufferGeometry {
  const g = new THREE.CylinderGeometry(rTop, rBot, h, radial, 1, true);
  place(g, lean * 0.5, y + h / 2, 0);
  const pos = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) pos.setX(i, pos.getX(i) + lean * 0.5 * ((pos.getY(i) - y) / h));
  return weld(g);
}

const tree: Gen = (p, lod, variant) => {
  const kind = str(p, 'kind', 'pine'), H = num(p, 'height', 7), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const trunkG: THREE.BufferGeometry[] = [], leafG: THREE.BufferGeometry[] = [];
  const rad = pick3(lod, 6, 5, 4);
  const lean = (rnd() - 0.5) * H * 0.08;
  const trunkH = H * (kind === 'pine' ? 0.28 : kind === 'dead' ? 0.95 : 0.5);
  trunkG.push(tube(H * 0.022, H * 0.04, trunkH, rad, 0, lean));
  let role2 = 'foliage';
  if (kind === 'pine') {
    const nC = pick3(lod, 4, 3, 2), R = H * 0.2, rr = pick3(lod, 8, 6, 5);
    for (let i = 0; i < nC; i++) {
      const rad_i = R * (1.05 - i * 0.2) * (0.92 + rnd() * 0.16), hc = H * 0.4 * (nC === 2 ? 1.2 : 1);
      const c = new THREE.ConeGeometry(rad_i, hc, rr, 1, false);
      place(c, lean * (0.3 + i * 0.15), H * 0.2 + i * H * 0.17 * (nC === 2 ? 1.5 : 1) + hc / 2, 0, 0, rnd() * 3, 0);
      const cp = c.getAttribute('position') as THREE.BufferAttribute;
      for (let v = 0; v < cp.count; v++) { const yy = cp.getY(v); if (yy < H * 0.2 + i * H * 0.17 + hc * 0.2) { const k = 1 + (noise3(cp.getX(v) * 3, i, cp.getZ(v) * 3, seed) - 0.5) * 0.35; cp.setX(v, cp.getX(v) * k); cp.setZ(v, cp.getZ(v) * k); } }
      leafG.push(weld(c));
    }
  } else if (kind === 'broadleaf') {
    const nB = pick3(lod, 4, 4, 2), det = pick3(lod, 1, 0, 0);
    for (let i = 0; i < nB; i++) {
      const rr = H * (0.19 + rnd() * 0.07), a = (i / nB) * 6.283 + rnd();
      const b = lumpy(det, 0.28, (x, y, z) => noise3(x + i, y, z, seed), 1, 0.85, 1);
      place(b, lean + Math.cos(a) * H * 0.13 * (i ? 1 : 0), trunkH + H * 0.12 + (i ? rnd() * H * 0.14 : H * 0.12), Math.sin(a) * H * 0.1 * (i ? 1 : 0), 0, 0, 0, rr, rr, rr);
      leafG.push(b);
    }
  } else if (kind === 'dead') {
    const nB = pick3(lod, 3, 2, 1);
    for (let i = 0; i < nB; i++) { const by = trunkH * (0.45 + i * 0.2), len = H * (0.3 - i * 0.05), a = (i % 2 ? 1 : -1) * (0.6 + rnd() * 0.5); const br = tube(H * 0.006, H * 0.018, len, 5, 0); place(br, lean * 0.6, by, 0, 0, 0, -a); trunkG.push(br); }
  } else { // crystal tree
    role2 = 'crystal';
    const nC = pick3(lod, 3, 2, 1);
    for (let i = 0; i < nC; i++) { const c = crystal(H * (0.28 - i * 0.04), H * 0.05, pick3(lod, 6, 4, 4)); place(c, lean + (i - 1) * H * 0.08, trunkH * 0.9 + i * H * 0.08, 0, 0, rnd() * 3, (i - 1) * 0.35); leafG.push(c); }
  }
  const trunk = trunkG.map(g => finalize(g, skyAo(0, H, 1)));
  const leaf = leafG.map(g => finalize(g, (x, y, z, nx, ny) => (kind === 'pine' ? skyAo(H * 0.2, H * 0.8, 0.9)(x, y, z, nx, ny, 0) : 0.55 + 0.45 * smooth(trunkH * 0.6, H, y)) * (0.82 + 0.18 * noise3(x * 2, y * 2, z * 2, seed))));
  const parts: MeshPart[] = [{ role: 'trunk', geometry: merge(trunk) }];
  if (leaf.length) parts.push({ role: role2, geometry: merge(leaf) });
  return parts;
};

const bush: Gen = (p, lod, variant) => {
  const size = num(p, 'size', 1.2), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const det = pick3(lod, 1, 0, 0), n = pick3(lod, 3, 3, 1);
  const gs: THREE.BufferGeometry[] = [];
  for (let i = 0; i < n; i++) {
    const r = size * (0.5 + rnd() * 0.25) * (n === 1 ? 1.7 : 1);
    const b = lumpy(det, 0.3, (x, y, z) => noise3(x + i * 3, y, z, seed), 1, 0.78, 1);
    place(b, (i - (n - 1) / 2) * size * 0.55, r * 0.55, (rnd() - 0.5) * size * 0.3, 0, rnd() * 3, 0, r, r, r);
    gs.push(finalize(b, (_x, y, _z, _nx, ny) => 0.5 + 0.5 * smooth(0, size * 0.9, y) * (0.7 + 0.3 * ny)));
  }
  return [{ role: 'foliage', geometry: merge(gs) }];
};

const grass: Gen = (p, lod, variant) => {
  const blades = Math.max(2, Math.round(num(p, 'blades', 12) * pick3(lod, 1, 0.6, 0.3))), H = num(p, 'height', 0.9), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const verts: number[] = [], cols: number[] = [], idx: number[] = [];
  for (let i = 0; i < blades; i++) {
    const a = rnd() * 6.283, ox = Math.cos(a) * rnd() * H * 0.45, oz = Math.sin(a) * rnd() * H * 0.3;
    const hh = H * (0.6 + rnd() * 0.6), lean = (rnd() - 0.5) * hh * 0.7, bw = H * 0.07, yaw = rnd() * 3.1416;
    const cx = Math.cos(yaw), sx = Math.sin(yaw);
    const v = (x: number, y: number, c: number) => { verts.push(ox + x * cx, y, oz + x * sx); cols.push(c, c, c); };
    const b = verts.length / 3;
    v(-bw, 0, 0.42); v(bw, 0, 0.42); v(-bw * 0.7 + lean * 0.35, hh * 0.5, 0.75); v(bw * 0.7 + lean * 0.35, hh * 0.5, 0.75); v(lean, hh, 1);
    idx.push(b, b + 1, b + 3, b, b + 3, b + 2, b + 2, b + 3, b + 4);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3));
  g.setIndex(idx);
  g.computeVertexNormals();
  const pos = g.getAttribute('position') as THREE.BufferAttribute;
  const uv = new Float32Array(pos.count * 2);
  for (let i = 0; i < pos.count; i++) { uv[i * 2] = pos.getX(i) + pos.getZ(i); uv[i * 2 + 1] = pos.getY(i); }
  g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  g.computeBoundingSphere(); g.computeBoundingBox();
  return [{ role: 'foliage', geometry: g }];
};

// ── mountain / cloud / island ───────────────────────────────────────────────────────────────────────────────────
const mountain: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 120), H = num(p, 'h', 50), D = num(p, 'depth', 40), profile = str(p, 'profile', 'ridge'), snow = p.snow !== false, seed = seedOf(p, variant);
  const cols0 = Math.max(8, Math.round(w / 3.4)), rows0 = 8;
  const cols = pick3(lod, cols0, cols0 >> 1, cols0 >> 2), rows = pick3(lod, rows0, rows0 >> 1, 2);
  const nx = Math.max(2, cols), nz = Math.max(1, rows);
  const g = new THREE.PlaneGeometry(w, D, nx, nz);
  g.rotateX(-Math.PI / 2);
  const pos = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), z = pos.getZ(i);
    const u = x / (w / 2), v = z / (D / 2);
    let hgt: number;
    if (profile === 'cone' || profile === 'volcano') {
      const r = Math.hypot(u, v * 0.6);
      let k = Math.max(0, 1 - r);
      k = Math.pow(k, 1.25) + (fbm3(x * 0.05, 0, z * 0.07, seed, 3) - 0.5) * 0.22 * k;
      if (profile === 'volcano' && r < 0.16) k = 0.84 + (r / 0.16) ** 2 * 0.08 - 0.12 * (1 - r / 0.16);
      hgt = k * H;
    } else {
      const ridge = 1 - Math.abs(fbm3(x * 0.012, 0, z * 0.02, seed, 4) * 2 - 1);
      const peak = Math.pow(ridge, 1.9) * 0.9 + fbm3(x * 0.05, 0, z * 0.05, seed + 71, 3) * 0.22 + fbm3(x * 0.16, 0, z * 0.16, seed + 91, 2) * 0.08;
      hgt = peak * H * Math.max(0.12, 1 - v * v);
    }
    pos.setY(i, hgt * Math.max(0.0, 1 - Math.pow(Math.abs(u), 6)));
  }
  const skirt = new THREE.BoxGeometry(w, 60, D);
  skirt.translate(0, -30 + 0.5, 0);
  const merged = weld(merge([weldPlain(g), weldPlain(skirt)]));
  finalize(merged, (_x, y, _z, _nx, ny) => 0.7 + 0.3 * smooth(0, H * 0.6, y) * (0.5 + 0.5 * ny));
  if (!snow) return [{ role: 'body', geometry: merged }];
  const [sn, body] = split(merged, (_cx, cy, _cz, ny) => cy > H * 0.68 && ny > 0.3);
  return [{ role: 'body', geometry: compact(body) }, { role: 'snow', geometry: compact(sn) }];
};
/** Strip to positions + index only (so geometries of different origin can be merged and welded together). */
function weldPlain(g: THREE.BufferGeometry): THREE.BufferGeometry {
  const o = new THREE.BufferGeometry();
  o.setAttribute('position', g.getAttribute('position').clone());
  if (g.getIndex()) o.setIndex(g.getIndex()!.clone());
  return o;
}

const cloud: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 14), h = num(p, 'h', 4), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const n = pick3<number>(lod, 6, 6, 3), det = pick3<number>(lod, 1, 0, 0);
  const gs: THREE.BufferGeometry[] = [];
  for (let i = 0; i < n; i++) {
    const u = n === 1 ? 0.5 : i / (n - 1);
    const r = h * (0.55 + 0.45 * Math.sin(u * Math.PI)) * (0.8 + rnd() * 0.4);
    const b = lumpy(det, 0.22, (x, y, z) => noise3(x + i * 5, y, z, seed), 1.25, 0.8, 1);
    const pos = b.getAttribute('position') as THREE.BufferAttribute;
    for (let k = 0; k < pos.count; k++) pos.setY(k, Math.max(pos.getY(k), -0.35));
    place(b, (u - 0.5) * w, (rnd() - 0.3) * h * 0.4, (rnd() - 0.5) * h * 0.6, 0, 0, 0, r, r, r);
    b.computeVertexNormals();
    gs.push(finalize(b, (_x, y, _z, _nx, ny) => 0.72 + 0.28 * smooth(-0.8, 0.6, ny) * (0.8 + 0.2 * smooth(-h, h, y))));
  }
  return [{ role: 'cloud', geometry: merge(gs) }];
};

const island: Gen = (p, lod, variant) => {
  const R = num(p, 'radius', 6), seed = seedOf(p, variant);
  const g = lumpy(pick3<number>(lod, 2, 1, 0), 0.22, (x, y, z) => fbm3(x + seed * 0.01, y, z, seed, 2), 1, 1, 1);
  const pos = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
    pos.setXYZ(i, x * R, y >= 0 ? y * R * 0.18 : y * R * 0.95 * (1 - 0.25 * Math.hypot(x, z)), z * R * 0.8);
  }
  g.computeVertexNormals();
  finalize(g, skyAo(-R, R, 0.9));
  const [cap, body] = split(g, (_cx, cy, _cz, ny) => ny > 0.4 && cy > -R * 0.15);
  return [{ role: 'body', geometry: compact(body) }, { role: 'cap', geometry: compact(cap) }];
};

// ── water / waterfall ───────────────────────────────────────────────────────────────────────────────────────────
const water: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 12), h = num(p, 'h', 0.4), d = num(p, 'depth', 4), seed = seedOf(p, variant);
  const cols0 = Math.max(2, Math.round(w / 2));
  const cols = pick3(lod, cols0, Math.max(1, cols0 >> 1), 1), rows = pick3(lod, 3, 2, 1);
  const top = new THREE.PlaneGeometry(w, d, cols, rows);
  top.rotateX(-Math.PI / 2);
  place(top, 0, 0, -d * 0.2);
  const tp = top.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < tp.count; i++) tp.setY(i, (noise3(tp.getX(i) * 0.5, 0, tp.getZ(i) * 0.5, seed) - 0.5) * 0.12 * (lod < 2 ? 1 : 0));
  const front = new THREE.PlaneGeometry(w, Math.max(h, 0.2), 1, pick3(lod, 2, 1, 1));
  place(front, 0, -Math.max(h, 0.2) / 2, d * 0.3);
  const parts = [top, front].map(g => { const o = weldPlain(g); o.setAttribute('uv', g.getAttribute('uv').clone()); return o; });
  const merged = merge(parts.map(g => { const n2 = g.clone(); n2.computeVertexNormals(); return n2; }));
  merged.computeVertexNormals();
  finalize(merged, (_x, y) => 0.6 + 0.4 * smooth(-Math.max(h, 0.2), 0, y));
  return [{ role: 'water', geometry: merged }];
};

const waterfall: Gen = (p, lod, variant) => {
  const w = num(p, 'w', 3), H = num(p, 'h', 16), seed = seedOf(p, variant);
  const rows = Math.max(4, Math.round(H / 1.5));
  const cols = pick3(lod, 3, 2, 1), rr = pick3(lod, rows, rows >> 1, 2);
  const sheet = new THREE.PlaneGeometry(w, H, cols, Math.max(1, rr));
  place(sheet, 0, -H / 2, 0.55);
  const sp = sheet.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < sp.count; i++) { const t = (sp.getY(i) + H) / H; sp.setZ(i, sp.getZ(i) + Math.sin(t * 3.14) * 0.25 + (noise3(sp.getX(i), sp.getY(i) * 0.3, 0, seed) - 0.5) * 0.15); }
  sheet.computeVertexNormals();
  const sheetG = finalize(weldPlainNormals(sheet), (_x, y) => 0.75 + 0.25 * smooth(-H, 0, y));
  const lip = rbox(w * 1.5, 1.2, 2.2, pick3(lod, 1, 0, 0), 0.3);
  place(lip, 0, 0.1, 0.1);
  const bed = rbox(w * 1.8, 1.6, 2.6, pick3(lod, 1, 0, 0), 0.4);
  place(bed, 0, -H - 0.2, 0.0);
  const stone = merge([lip, bed].map(g => finalize(g, skyAo(-H, H, 0.9))));
  return [{ role: 'water', geometry: sheetG }, { role: 'stone', geometry: stone }];
};
function weldPlainNormals(g: THREE.BufferGeometry): THREE.BufferGeometry {
  const o = new THREE.BufferGeometry();
  o.setAttribute('position', g.getAttribute('position').clone());
  o.setAttribute('normal', g.getAttribute('normal').clone());
  o.setAttribute('uv', g.getAttribute('uv').clone());
  o.setIndex(g.getIndex()!.clone());
  return o;
}

// ── crystals / spikes ───────────────────────────────────────────────────────────────────────────────────────────
/** Hexagonal-prism crystal with a pyramid tip (sides 6 → 18 tris, 4 → 12, simple pyramid → 4). Base at y = 0. */
function crystal(h: number, r: number, sides: number, pyramid = false): THREE.BufferGeometry {
  const verts: number[] = [], idx: number[] = [];
  const ring = (y: number, rad: number, off = 0) => { const b = verts.length / 3; for (let i = 0; i < sides; i++) { const a = (i / sides) * 6.2832 + off; verts.push(Math.cos(a) * rad, y, Math.sin(a) * rad); } return b; };
  if (pyramid) {
    const a = ring(0, r), tip = verts.length / 3; verts.push(0, h, 0);
    for (let i = 0; i < sides; i++) idx.push(a + i, tip, a + ((i + 1) % sides));
  } else {
    const a = ring(0, r), b = ring(h * 0.62, r * 0.92, 0.1), tip = verts.length / 3; verts.push(0, h, 0);
    for (let i = 0; i < sides; i++) { const j = (i + 1) % sides; idx.push(a + i, b + i, a + j, a + j, b + i, b + j); idx.push(b + i, tip, b + j); }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}

const crystalCluster: Gen = (p, lod, variant) => {
  const n = Math.max(1, Math.round(num(p, 'count', 6))), H = num(p, 'height', 3), spread = num(p, 'spread', 1.4), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const gs: THREE.BufferGeometry[] = [];
  for (let i = 0; i < n; i++) {
    const hh = H * (i === 0 ? 1 : 0.35 + rnd() * 0.55), r = H * (0.06 + rnd() * 0.05) * (i === 0 ? 1.4 : 1);
    const c = crystal(hh, r, pick3(lod, 6, 4, 4), lod === 2);
    const a = rnd() * 6.283, off = i === 0 ? 0 : spread * (0.3 + rnd() * 0.7);
    place(c, Math.cos(a) * off, 0, Math.sin(a) * off * 0.6, (rnd() - 0.5) * 0.5, rnd() * 3, (rnd() - 0.5) * 0.7 + (i === 0 ? 0 : Math.cos(a) * 0.25));
    gs.push(finalize(c, (_x, y) => 0.45 + 0.55 * smooth(0, H, y)));
  }
  return [{ role: 'crystal', geometry: merge(gs) }];
};

const spikes: Gen = (p, lod, variant) => {
  const W = num(p, 'width', 3.2), H = num(p, 'height', 1.5), n = Math.max(1, Math.round(num(p, 'count', 5))), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const gs: THREE.BufferGeometry[] = [];
  for (let i = 0; i < n; i++) {
    const u = n === 1 ? 0.5 : i / (n - 1);
    const hh = H * (0.65 + rnd() * 0.35) * (1 - 0.18 * Math.abs(u - 0.5) * 2), r = Math.min(W / n * 0.62, H * 0.3);
    const c = crystal(hh, r, pick3(lod, 6, 4, 4), lod === 2);
    place(c, (u - 0.5) * (W - r * 1.4), 0, (rnd() - 0.5) * 0.5, (rnd() - 0.5) * 0.3, rnd() * 3, (rnd() - 0.5) * 0.35);
    gs.push(finalize(c, (_x, y) => 0.5 + 0.5 * smooth(0, H, y)));
  }
  return [{ role: 'crystal', geometry: merge(gs) }];
};

// ── blade / marker / ancient structures ─────────────────────────────────────────────────────────────────────────
const blade: Gen = (p, lod, variant) => {
  const L = num(p, 'length', 8), T = num(p, 'thickness', 0.8), D = num(p, 'depth', 1.2);
  const b = rbox(L, T, D, pick3(lod, 2, 1, 0), Math.min(T * 0.4, 0.3));
  roughen(b, () => 0.015, seedOf(p, variant), 1);
  b.computeVertexNormals();
  const parts: MeshPart[] = [{ role: 'stone', geometry: finalize(b, skyAo(-T, T, 0.7)) }];
  const hub = new THREE.CylinderGeometry(T * 0.9, T * 0.9, D * 1.1, pick3(lod, 10, 6, 4), 1);
  hub.rotateX(Math.PI / 2);
  parts.push({ role: 'glow', geometry: finalize(weld(hub)) });
  return parts;
};

const marker: Gen = (p, lod) => {
  const kind = str(p, 'kind', 'checkpoint'), H = num(p, 'height', 4), W = num(p, 'w', 3);
  const seg = pick3(lod, 1, 0, 0);
  const stone: THREE.BufferGeometry[] = [], glow: THREE.BufferGeometry[] = [];
  const post = (x: number) => { const g = rbox(0.35, H, 0.35, seg, 0.1); place(g, x, H / 2, 0); stone.push(finalize(g, skyAo(0, H, 0.8))); };
  post(-W / 2); post(W / 2);
  if (kind === 'finish') { const beam = rbox(W + 0.6, 0.4, 0.4, seg, 0.1); place(beam, 0, H, 0); stone.push(finalize(beam, skyAo(0, H, 0.5))); }
  const banner = new THREE.PlaneGeometry(W - 0.5, kind === 'finish' ? H * 0.32 : H * 0.28, 1, 1);
  place(banner, 0, H * (kind === 'finish' ? 0.78 : 0.8), 0.02);
  const bg = weldPlainNormals(banner.clone().rotateX(0)); bg.computeVertexNormals();
  glow.push(finalize(bg, () => 1));
  return [{ role: 'stone', geometry: merge(stone) }, { role: 'glow', geometry: merge(glow) }];
};

const ancient: Gen = (p, lod, variant) => {
  const kind = str(p, 'kind', 'arch'), W = num(p, 'w', 8), H = num(p, 'h', 10), seed = seedOf(p, variant), rnd = mulberry32(seed);
  const seg = pick3(lod, 1, 0, 0);
  const blocks: { w: number; h: number; d: number; x: number; y: number; rz?: number; ry?: number }[] = [];
  const th = Math.max(0.6, W * 0.16), dp = Math.max(0.8, W * 0.2);
  switch (kind) {
    case 'gate':
      blocks.push({ w: th, h: H, d: dp, x: -W / 2, y: H / 2 }, { w: th, h: H, d: dp, x: W / 2, y: H / 2 }, { w: W + th, h: th * 0.9, d: dp * 1.05, x: 0, y: H + th * 0.45 },
        { w: th * 1.3, h: th * 0.5, d: dp * 1.3, x: -W / 2, y: th * 0.25 }, { w: th * 1.3, h: th * 0.5, d: dp * 1.3, x: W / 2, y: th * 0.25 }, { w: W * 0.5, h: th * 0.5, d: dp * 1.1, x: 0, y: H + th * 1.1 });
      break;
    case 'pillar':
      blocks.push({ w: th * 1.6, h: th * 0.6, d: th * 1.6, x: 0, y: th * 0.3 }, { w: th, h: H, d: th, x: 0, y: H / 2 + th * 0.3 }, { w: th * 1.5, h: th * 0.55, d: th * 1.5, x: 0, y: H + th * 0.6 });
      break;
    case 'wall':
      for (let i = 0; i < 6; i++) { const bw = W / 6; blocks.push({ w: bw * 0.98, h: H * (0.45 + rnd() * 0.55), d: dp, x: (i - 2.5) * bw, y: 0 }); blocks[i].y = blocks[i].h / 2; }
      break;
    case 'obelisk':
      blocks.push({ w: th * 1.8, h: th * 0.8, d: th * 1.8, x: 0, y: th * 0.4 }, { w: th, h: H, d: th, x: 0, y: H / 2 + th * 0.8 });
      break;
    case 'tower':
      for (let i = 0; i < 7; i++) { const k = 1 - i * 0.09, bh = H / 7; blocks.push({ w: W * 0.5 * k, h: bh * 1.02, d: W * 0.5 * k, x: 0, y: bh * (i + 0.5) }); }
      break;
    default: // arch
      blocks.push({ w: th, h: H * 0.78, d: dp, x: -W / 2, y: H * 0.39 }, { w: th, h: H * 0.78, d: dp, x: W / 2, y: H * 0.39 },
        { w: W * 0.34, h: th, d: dp, x: -W * 0.3, y: H * 0.8 }, { w: W * 0.34, h: th, d: dp, x: W * 0.3, y: H * 0.8 }, { w: W * 0.42, h: th * 1.1, d: dp, x: 0, y: H * 0.92 });
  }
  // the far level keeps ceil(n/2) blocks that preserve the silhouette (both pillars + the top of an arch, …)
  const keep: Record<string, number[]> = { arch: [0, 1, 4], gate: [0, 1, 2], pillar: [0, 1], wall: [0, 2, 5], obelisk: [1], tower: [0, 2, 4, 6] };
  const use = lod === 2 ? (keep[kind] ?? keep.arch).map(i => blocks[i]) : blocks;
  const gs = use.map(b => {
    const g = rbox(b.w, b.h, b.d, seg, Math.min(0.18, b.w * 0.2, b.h * 0.2));
    if (kind === 'obelisk' || kind === 'tower') { const pos = g.getAttribute('position') as THREE.BufferAttribute; for (let i = 0; i < pos.count; i++) { const t = (pos.getY(i) + b.h / 2) / b.h; const k = kind === 'obelisk' ? lerp(1, 0.55, t) : 1; pos.setX(i, pos.getX(i) * k); pos.setZ(i, pos.getZ(i) * k); } }
    place(g, b.x, b.y, -b.d * 0.1, 0, (rnd() - 0.5) * 0.06, (rnd() - 0.5) * 0.04);
    g.computeVertexNormals();
    if (lod < 2) roughen(g, () => 0.05, seed, 0.9);
    g.computeVertexNormals();
    return finalize(g, skyAo(0, H, 0.9));
  });
  const parts: MeshPart[] = [{ role: 'stone', geometry: merge(gs) }];
  if (p.glow === true) {
    const gl: THREE.BufferGeometry[] = [];
    for (let i = 0; i < 3; i++) { const q = new THREE.PlaneGeometry(th * 0.3, th * 0.8, 1, 1); place(q, (i - 1) * th * 0.35, H * (0.3 + i * 0.2), dp * 0.5 + 0.02); gl.push(finalize(weldPlainNormals(q.clone()), () => 1)); }
    parts.push({ role: 'glow', geometry: merge(gl) });
  }
  return parts;
};

export const GENERATORS: Record<string, Gen> = {
  'builtin:platform': platform, 'builtin:slab': slab, 'builtin:poly_slab': polySlab, 'builtin:rock': rock, 'builtin:cliff': cliff,
  'builtin:tree': tree, 'builtin:bush': bush, 'builtin:grass': grass, 'builtin:mountain': mountain, 'builtin:cloud': cloud, 'builtin:island': island,
  'builtin:water': water, 'builtin:waterfall': waterfall, 'builtin:crystal_cluster': crystalCluster, 'builtin:spikes': spikes, 'builtin:blade': blade,
  'builtin:marker': marker, 'builtin:ancient_structure': ancient,
};

/** Parts of a builtin mesh (catalogue defaults applied). Geometry is freshly allocated: the caller owns / caches it. */
export function generateMesh(meshId: string, params: MeshParams, lod: Lod, variant = 0): MeshPart[] {
  const gen = GENERATORS[meshId];
  if (!gen) throw new Error(`unknown builtin mesh "${meshId}"`);
  return gen(meshParams(meshId, params), lod, variant);
}

export const trisOfParts = (parts: MeshPart[]): number => parts.reduce((n, p) => n + triCount(p.geometry), 0);
