import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import { type Rng, fbm3, mulberry32, noise3, pick, range } from '../noise';
import { blobGeometry, col, lerpColor, merge, paint, solid, xf } from '../geom';

/**
 * Rock / platform builders.  Local frame: origin = centre of the TOP surface (the collision top), +x right, −y down,
 * z toward the camera. The top surface of the grass cap is EXACTLY y = 0 so the planted tip never floats or sinks.
 */
export interface SlabOpts {
  w: number; h: number; depth: number; taper: number; seed: number;
  theme: WorldTheme;
  /** 'rock' = grass-capped cliff chunk; 'ice'/'wood'/... handled by dedicated builders. */
  capThickness?: number;
}

const C = (hex: string): THREE.Color => col(hex);

const isTopV = (v: number): boolean => v > 0.499;

/** Rock body — noisy trapezoidal prism, banded strata, darker toward the bottom. */
export function rockBody(o: SlabOpts): THREE.BufferGeometry {
  const { w, h, depth, taper, seed, theme } = o;
  const sx = Math.max(2, Math.round(w / 1.3)), sy = Math.max(2, Math.round(h / 0.95)), sz = Math.max(2, Math.round(depth / 1.5));
  const g = new THREE.BoxGeometry(1, 1, 1, sx, sy, sz);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const u = p.getX(i), v = p.getY(i), s = p.getZ(i);
    const t = v + 0.5; // 0 bottom … 1 top
    let x = u * w * (taper + (1 - taper) * t);
    let y = (v - 0.5) * h;
    if (isTopV(v)) y = -0.32; // hidden just under the cap: never coplanar with the cap top (z-fighting)
    let z = s * depth * (0.8 + 0.2 * t);
    const n1 = noise3(x * 0.5, y * 0.5, z * 0.5, seed), n2 = noise3(x * 0.5 + 9, y * 0.5, z * 0.5, seed + 3), n3 = noise3(x * 0.5, y * 0.5 + 9, z * 0.5, seed + 7);
    const isTop = v > 0.499, isSideX = Math.abs(u) > 0.499, isFaceZ = Math.abs(s) > 0.499;
    if (!isTop) y += (n2 - 0.5) * 0.55;
    if (isSideX) x -= Math.sign(u) * n1 * 0.32; // only ever INWARD: visuals stay inside the collision silhouette
    if (isFaceZ) z += Math.sign(s) * (n3 - 0.5) * 0.7;
    if (!isTop && !isSideX && !isFaceZ) { x += (n1 - 0.5) * 0.3; z += (n3 - 0.5) * 0.3; }
    if (v < -0.499) y -= n1 * 0.9; // ragged underside
    p.setXYZ(i, x, y, z);
  }
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark);
  const tmp = new THREE.Color();
  return paint(g, (x, y, z, nx, ny, nz) => {
    const t = Math.max(0, Math.min(1, (y + h) / h)); // 0 bottom → 1 top
    const band = Math.sin(y * 2.4 + fbm3(x * 0.3, y * 0.4, z * 0.3, seed + 5) * 5);
    lerpColor(rd, rm, Math.pow(t, 0.75), tmp);
    if (band > 0.55) tmp.lerp(rl, 0.35);
    if (ny < -0.5) tmp.lerp(rd, 0.7);
    else if (nz > 0.6) tmp.lerp(rl, 0.12 + 0.2 * t);
    // sun-baked top edge vs shaded interior
    tmp.multiplyScalar(0.92 + 0.16 * noise3(x * 1.1, y * 1.1, z * 1.1, seed + 11));
    return tmp.clone();
  });
}

/** Moss / snow / sand cap with a hanging lip. Top face is flat at y=0. */
export function capGeometry(o: SlabOpts): THREE.BufferGeometry {
  const { w, depth, seed, theme } = o;
  const capT = o.capThickness ?? 0.55;
  const ov = theme.capStyle === 'grass' ? 0.12 : 0.08;
  const sx = Math.max(3, Math.round(w / 0.8)), sz = Math.max(2, Math.round(depth / 1.1));
  const g = new THREE.BoxGeometry(1, 1, 1, sx, 2, sz);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  const W = w + ov * 2, D = depth + 0.35;
  for (let i = 0; i < p.count; i++) {
    const u = p.getX(i), v = p.getY(i), s = p.getZ(i);
    let x = u * W, z = s * D;
    let y = v > 0.001 ? 0 : v < -0.001 ? -capT : -capT * 0.5;
    const n = noise3(x * 0.8, 0, z * 0.8, seed + 21);
    const corner = Math.abs(u) > 0.499 && Math.abs(s) > 0.499;
    if (corner) { x -= Math.sign(u) * 0.28; z -= Math.sign(s) * 0.28; }
    if (v < -0.001) { y = -capT - n * 0.6 - (Math.abs(u) > 0.499 || Math.abs(s) > 0.499 ? 0.25 : 0); x *= 0.985; }
    else if (v > -0.001 && v < 0.001) { x += Math.sign(u) * 0.07 * n; z += Math.sign(s) * 0.07 * n; } // lip bulge
    p.setXYZ(i, x, y, z);
  }
  const A = C(theme.terrain.capA), B = C(theme.terrain.capB), D2 = C(theme.terrain.capDark), S = C(theme.terrain.soil);
  const tmp = new THREE.Color();
  return paint(g, (x, y, z, nx, ny) => {
    if (ny > 0.5) {
      const n = fbm3(x * 0.55, 0, z * 0.55, seed + 31, 2);
      lerpColor(B, A, n * 1.3 - 0.1, tmp);
      if (noise3(x * 1.7, 3, z * 1.7, seed + 41) > 0.76) tmp.lerp(D2, 0.35); // darker moss clumps
      return tmp.clone();
    }
    const t = Math.max(0, Math.min(1, (y + capT + 0.6) / (capT + 0.6)));
    lerpColor(S, D2, 0.4 + 0.6 * t, tmp);
    if (y > -capT * 0.45) tmp.lerp(B, 0.5);
    return tmp.clone();
  });
}

/** Stalactite-like rock spikes under the platform (gives chunky floating-island silhouettes). */
export function underSpikes(o: SlabOpts, rng: Rng): THREE.BufferGeometry[] {
  const { w, h, depth, taper, theme } = o;
  const n = Math.max(2, Math.round(w / 2.3));
  const out: THREE.BufferGeometry[] = [];
  const rd = C(theme.terrain.rockDark), rm = C(theme.terrain.rockMid);
  for (let i = 0; i < n; i++) {
    const r = range(rng, 0.35, 0.8) * Math.min(1.2, 0.6 + h / 5);
    const len = range(rng, 0.9, 2.3) * Math.min(1.5, 0.7 + h / 4);
    const g = new THREE.ConeGeometry(r, len, 5, 1);
    xf(g, 0, 0, 0, Math.PI, rng() * 6.28, 0);
    const x = (i / (n - 1 || 1) - 0.5) * w * taper * 0.8 + range(rng, -0.3, 0.3);
    const z = range(rng, -depth * 0.28, depth * 0.28);
    xf(g, x, -h - len * 0.42, z);
    paint(g, (_x, y, _z, ny) => lerpColor(rd, rm, Math.max(0, Math.min(1, (y + h + len) / len)) * 0.6 + (ny > 0 ? 0.1 : 0)));
    out.push(g);
  }
  return out;
}

/** Short grass tufts / snow lumps along the top surface. */
export function topTufts(o: SlabOpts, rng: Rng): THREE.BufferGeometry[] {
  const { w, depth, theme } = o;
  const out: THREE.BufferGeometry[] = [];
  const n = Math.round(w * 1.6);
  const a = C(theme.terrain.capA), b = C(theme.terrain.capB), d = C(theme.terrain.capDark);
  for (let i = 0; i < n; i++) {
    const g = new THREE.ConeGeometry(range(rng, 0.05, 0.1), range(rng, 0.14, 0.3), 3, 1);
    xf(g, 0, 0.1, 0, range(rng, -0.25, 0.25), rng() * 6.28, range(rng, -0.25, 0.25));
    xf(g, range(rng, -w / 2 + 0.3, w / 2 - 0.3), 0, range(rng, 0.3, depth / 2 - 0.2));
    const c = lerpColor(d, rng() < 0.5 ? a : b, rng());
    solid(g, c);
    out.push(g);
  }
  return out;
}

export function flower(rng: Rng, x: number, z: number, colors: string[], scale = 1): THREE.BufferGeometry[] {
  const c = col(pick(rng, colors));
  const stemH = range(rng, 0.22, 0.4) * scale;
  const stem = xf(new THREE.CylinderGeometry(0.018, 0.022, stemH, 4), x, stemH / 2, z);
  solid(stem, '#4d7a33');
  const head = xf(new THREE.IcosahedronGeometry(0.09 * scale, 0), x, stemH + 0.03, z);
  solid(head, c);
  const center = xf(new THREE.IcosahedronGeometry(0.04 * scale, 0), x, stemH + 0.1 * scale, z);
  solid(center, '#f6c43a');
  return [stem, head, center];
}

export function bush(rng: Rng, theme: WorldTheme, x: number, z: number, r: number, seed: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const base = pick(rng, theme.foliage.length > 1 ? theme.foliage : ['#6c9a3a', '#4f7f34']);
  const n = 3;
  for (let i = 0; i < n; i++) {
    const g = blobGeometry(r * range(rng, 0.55, 0.85), 1, 0.22, (a, b, c) => noise3(a + i * 5, b, c, seed));
    xf(g, x + range(rng, -r * 0.6, r * 0.6), r * 0.45, z + range(rng, -r * 0.4, r * 0.4), 0, rng() * 6, 0, 1, 0.8, 1);
    const cc = col(base);
    const hsl = { h: 0, s: 0, l: 0 }; cc.getHSL(hsl); cc.setHSL(hsl.h + range(rng, -0.015, 0.015), hsl.s, hsl.l * range(rng, 0.88, 1.12));
    paint(g, (_x, y) => cc.clone().multiplyScalar(0.8 + Math.min(0.35, Math.max(0, y) / r * 0.3)), true);
    out.push(g);
  }
  return out;
}

export function mushroom(rng: Rng, x: number, z: number, s = 1): THREE.BufferGeometry[] {
  const stemH = 0.35 * s;
  const stem = xf(new THREE.CylinderGeometry(0.07 * s, 0.1 * s, stemH, 6), x, stemH / 2, z); solid(stem, '#f1e2c4');
  const cap = xf(new THREE.SphereGeometry(0.28 * s, 8, 5, 0, Math.PI * 2, 0, Math.PI / 2), x, stemH, z, 0, 0, 0, 1, 0.7, 1);
  solid(cap, rng() < 0.5 ? '#d9402a' : '#f08a2c', true);
  const dots: THREE.BufferGeometry[] = [];
  for (let i = 0; i < 3; i++) {
    const a = rng() * 6.28, r = 0.14 * s;
    dots.push(solid(xf(new THREE.IcosahedronGeometry(0.04 * s, 0), x + Math.cos(a) * r, stemH + 0.12 * s, z + Math.sin(a) * r), '#fff3e0'));
  }
  return [stem, cap, ...dots];
}

export function fence(theme: WorldTheme, x0: number, x1: number, z: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const wood = col(theme.terrain.wood), dark = col(theme.terrain.woodDark);
  const n = Math.max(2, Math.round((x1 - x0) / 1.4));
  for (let i = 0; i <= n; i++) {
    const x = x0 + ((x1 - x0) * i) / n;
    out.push(solid(xf(new THREE.BoxGeometry(0.16, 1.0, 0.16), x, 0.5, z), dark));
  }
  for (const y of [0.35, 0.72]) out.push(solid(xf(new THREE.BoxGeometry(x1 - x0, 0.12, 0.1), (x0 + x1) / 2, y, z + 0.02), wood));
  return out;
}

export function signPost(theme: WorldTheme, x: number, z: number): THREE.BufferGeometry[] {
  const wood = col(theme.terrain.wood), dark = col(theme.terrain.woodDark);
  return [
    solid(xf(new THREE.BoxGeometry(0.14, 1.3, 0.14), x, 0.65, z), dark),
    solid(xf(new THREE.BoxGeometry(1.0, 0.55, 0.1), x, 1.15, z + 0.08, 0, 0, 0.06), wood),
    solid(xf(new THREE.BoxGeometry(0.7, 0.07, 0.05), x, 1.2, z + 0.14, 0, 0, 0.06), dark),
    solid(xf(new THREE.BoxGeometry(0.5, 0.07, 0.05), x - 0.05, 1.05, z + 0.14, 0, 0, 0.06), dark),
  ];
}

/** Hanging vines with leaves from the front lip. */
export function vines(rng: Rng, theme: WorldTheme, w: number, depth: number, n: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const stemC = col(theme.terrain.capDark), leafC = [col(theme.terrain.capB), col(theme.terrain.capA)];
  for (let i = 0; i < n; i++) {
    const x = range(rng, -w / 2 + 0.4, w / 2 - 0.4), z = depth / 2 + 0.12, len = range(rng, 1.0, 2.8);
    const segs = Math.round(len / 0.38);
    for (let k = 0; k < segs; k++) {
      const y = -0.5 - k * 0.38, sway = Math.sin(k * 0.8 + i) * 0.08;
      out.push(solid(xf(new THREE.CylinderGeometry(0.018, 0.02, 0.4, 3), x + sway, y, z), stemC));
      if (k % 2 === 0) out.push(solid(xf(new THREE.IcosahedronGeometry(0.1, 0), x + sway + (k % 4 ? 0.1 : -0.1), y, z, 0, 0, 0, 1, 0.5, 0.4), leafC[(k + i) % 2]));
    }
  }
  return out;
}

export type Decor = 'none' | 'flowers' | 'bushes' | 'vines' | 'mushrooms' | 'fence' | 'sign';

/** Complete rock platform with optional decoration, merged into ONE geometry. */
export function buildRockPlatform(o: SlabOpts & { decor?: Decor }): THREE.BufferGeometry {
  const rng = mulberry32(o.seed * 7919 + 13);
  const parts: THREE.BufferGeometry[] = [rockBody(o), capGeometry(o), ...underSpikes(o, rng), ...topTufts(o, rng)];
  const back = -o.depth / 2 + 0.55;
  // a few flowers on every grassy platform
  if (o.theme.capStyle === 'grass') {
    const nf = Math.round(o.w * 0.5);
    for (let i = 0; i < nf; i++) parts.push(...flower(rng, range(rng, -o.w / 2 + 0.4, o.w / 2 - 0.4), range(rng, 0.5, o.depth / 2 - 0.3), ['#ffffff', '#fff3d8', '#ffd1e0', '#fff0a0'], 0.9));
  }
  switch (o.decor) {
    case 'flowers':
      for (let i = 0; i < o.w * 1.3; i++) parts.push(...flower(rng, range(rng, -o.w / 2 + 0.4, o.w / 2 - 0.4), range(rng, -o.depth / 2 + 0.6, o.depth / 2 - 0.3), ['#ffffff', '#ffd1e0', '#fff0a0', '#c9a6ff'], 1));
      break;
    case 'bushes': {
      const nb = Math.max(1, Math.round(o.w / 3));
      for (let i = 0; i < nb; i++) parts.push(...bush(rng, o.theme, range(rng, -o.w / 2 + 0.8, o.w / 2 - 0.8), back + range(rng, 0, 0.4), range(rng, 0.55, 0.9), o.seed + i));
      break;
    }
    case 'vines': parts.push(...vines(rng, o.theme, o.w, o.depth, Math.round(o.w * 0.9))); break;
    case 'mushrooms':
      for (let i = 0; i < 3 + Math.round(o.w / 3); i++) parts.push(...mushroom(rng, range(rng, -o.w / 2 + 0.6, o.w / 2 - 0.6), back + range(rng, 0, 0.8), range(rng, 0.8, 1.7)));
      break;
    case 'fence': parts.push(...fence(o.theme, -o.w / 2 + 0.8, o.w / 2 - 0.8, back)); break;
    case 'sign': parts.push(...signPost(o.theme, -o.w / 2 + 0.7, back + 0.4)); parts.push(...bush(rng, o.theme, o.w / 2 - 0.9, back + 0.2, 0.7, o.seed)); break;
    default: break;
  }
  if (o.decor !== 'vines' && o.w > 4) parts.push(...vines(rng, o.theme, o.w, o.depth, Math.round(o.w * 0.35)));
  return merge(parts);
}

/** Big cliff mass used for walls / ceilings. Face toward gameplay stays inside the collision silhouette. */
export function buildCliff(cx: number, cy: number, w: number, h: number, depth: number, seed: number, theme: WorldTheme, mossTop: boolean): THREE.BufferGeometry {
  const rng = mulberry32(seed * 104729 + 5);
  const sx = Math.max(2, Math.round(w / 1.6)), sy = Math.max(3, Math.round(h / 2.2)), sz = Math.max(2, Math.round(depth / 2));
  const g = new THREE.BoxGeometry(w, h, depth, sx, sy, sz);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const n1 = noise3(x * 0.35, y * 0.25, z * 0.35, seed), n2 = noise3(x * 0.35 + 5, y * 0.25, z * 0.35, seed + 1);
    const ex = Math.abs(x) > w / 2 - 0.01, ez = Math.abs(z) > depth / 2 - 0.01;
    // keep the playfield-facing x faces flat-ish; push the rest around freely
    p.setXYZ(i, x + (ex ? 0 : (n1 - 0.5) * 0.9) , y + (n2 - 0.5) * 0.6, z + (ez ? (n1 - 0.5) * 1.6 : (n2 - 0.5) * 0.8));
  }
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark), moss = C(theme.terrain.capB), mossD = C(theme.terrain.capDark);
  const tmp = new THREE.Color();
  paint(g, (x, y, z, nx, ny, nz) => {
    const band = Math.sin(y * 0.9 + fbm3(x * 0.2, y * 0.25, z * 0.2, seed + 9) * 6);
    lerpColor(rd, rm, 0.35 + 0.35 * (0.5 + 0.5 * band), tmp);
    if (nz > 0.5 || Math.abs(nx) > 0.5) tmp.lerp(rl, 0.18);
    if (ny > 0.5 && mossTop) tmp.copy(lerpColor(mossD, moss, fbm3(x * 0.4, 0, z * 0.4, seed + 3)));
    else if (noise3(x * 0.7, y * 0.7, z * 0.7, seed + 12) > 0.8) tmp.lerp(moss, 0.55); // moss stains
    tmp.multiplyScalar(0.8 + 0.4 * noise3(x * 1.3, y * 1.3, z * 1.3, seed + 13));
    return tmp.clone();
  });
  xf(g, cx, cy, 0);
  return g;
}
