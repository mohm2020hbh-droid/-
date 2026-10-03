import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import { type Rng, fbm3, mulberry32, noise3, pick, range } from '../noise';
import { SURF, blobGeometry, col, lerpColor, merge, paint, solid, withSurface, xf } from '../geom';
import { deform, roundedBlock, smoothBlob, sstep } from '../shapes';
import { autumnTree, cypressTree, deadTree, pineTree } from './trees';

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

/**
 * Rock body — rounded, bevelled, tapered block with smooth normals (render/shapes.ts). Gameplay-facing x sides only ever
 * bulge INWARD so the visual never pokes outside the collision trapezoid; camera-facing z faces and the underside are free.
 */
export function rockBody(o: SlabOpts): THREE.BufferGeometry {
  const { w, h, depth, taper, seed, theme } = o;
  const H = h + 0.05, top = -0.22;                       // hidden just under the cap: never coplanar (z-fighting)
  const blk = roundedBlock(w, H, depth * 0.9, Math.min(0.6, h * 0.3, w * 0.22), 0.62, 2);
  deform(blk, (x, y, z, dx, dy, dz, out) => {
    const t = (y + H / 2) / H;                            // 0 bottom … 1 top
    const sc = taper + (1 - taper) * t;
    let X = x * sc, Y = top - H / 2 + y, Z = z * (0.82 + 0.18 * t);
    const n1 = fbm3(X * 0.42, Y * 0.42, Z * 0.42, seed, 3), n2 = noise3(X * 0.9 + 9, Y * 0.9, Z * 0.9, seed + 3);
    // big soft bulges + TERRACED chisel (stepped plateaus ⇒ crisp horizontal ledges that catch the light)
    const terr = Math.round((n1 - 0.5) * 5) / 5;
    const chunk = (n1 - 0.5) * 0.35 + terr * 0.45 + (n2 - 0.5) * 0.16;
    if (Math.abs(dx) > 0.35 && dy > -0.6) X -= Math.sign(dx) * Math.abs(chunk) * 0.55;          // inward only
    else X += dx * chunk * 0.6;
    Z += dz * chunk * 1.1;
    if (dy < -0.4) Y -= (0.35 + n1 * 0.9) * Math.min(1.2, h / 3) * (-dy);                        // ragged underside
    else Y += dy * chunk * 0.3;
    out.set(X, Y, Z);
  });
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark);
  const tmp = new THREE.Color();
  return paint(blk.geometry, (x, y, z, nx, ny, nz) => {
    const t = Math.max(0, Math.min(1, (y + h) / h)); // 0 bottom → 1 top
    const band = Math.sin(y * 2.1 + fbm3(x * 0.3, y * 0.4, z * 0.3, seed + 5) * 5);
    lerpColor(rd, rm, Math.pow(t, 0.7), tmp);
    if (band > 0.45) tmp.lerp(rl, 0.32 * t + 0.1);
    if (nz > 0.5 && t > 0.35) tmp.lerp(rl, 0.18);                       // sun-facing front
    if (ny < -0.3) tmp.lerp(rd, 0.55);                                   // underside
    if (y > -0.75) tmp.multiplyScalar(0.72 + 0.28 * sstep(-0.2, -0.75, y)); // contact shadow under the grass lip
    tmp.multiplyScalar(0.9 + 0.2 * noise3(x * 1.1, y * 1.1, z * 1.1, seed + 11));
    return tmp.clone();
  }, true);
}

/** Moss / snow / sand cap: rounded slab, top EXACTLY flat at y = 0 over the collider, irregular draped lower edge. */
export function capGeometry(o: SlabOpts): THREE.BufferGeometry {
  const { w, depth, seed, theme } = o;
  const capT = o.capThickness ?? 0.5;
  const ov = 0.16;
  const W = w + ov * 2, D = depth + 0.3;
  const blk = roundedBlock(W, capT, D, Math.min(0.2, capT * 0.42), 0.6, 2);
  deform(blk, (x, y, z, dx, dy, dz, out) => {
    let Y = y - capT / 2, X = x, Z = z;
    const n = fbm3(x * 0.7, 0, z * 0.7, seed + 21, 2);
    if (dy < -0.2) Y -= (n * 0.55 + 0.05) * (0.6 + 0.4 * Math.max(Math.abs(dx), Math.abs(dz)));     // draped edge
    else if (dy < 0.6) { X += dx * (n - 0.4) * 0.12; Z += dz * (n - 0.4) * 0.12; }                 // soft lip bulge
    out.set(X, Y, Z);
  });
  const A = C(theme.terrain.capA), B = C(theme.terrain.capB), D2 = C(theme.terrain.capDark), S = C(theme.terrain.soil);
  const tmp = new THREE.Color();
  return paint(blk.geometry, (x, y, z, nx, ny, nz) => {
    if (ny > 0.55) {
      const n = fbm3(x * 0.5, 0, z * 0.5, seed + 31, 2);
      lerpColor(B, A, n * 1.35 - 0.12, tmp);
      if (noise3(x * 1.5, 3, z * 1.5, seed + 41) > 0.72) tmp.lerp(D2, 0.3);       // darker clumps
      const edge = Math.max(Math.abs(x) / (W / 2), Math.abs(z) / (D / 2));
      tmp.lerp(A, sstep(0.82, 1, edge) * 0.35);                                     // sun-kissed rim (readable edge)
      return tmp.clone();
    }
    const t = Math.max(0, Math.min(1, (y + capT + 0.6) / (capT + 0.6)));
    lerpColor(S, D2, 0.35 + 0.65 * t, tmp);
    if (y > -capT * 0.5) tmp.lerp(B, 0.55);
    if (nz > 0.4 && y > -capT * 0.6) tmp.lerp(A, 0.15);
    return tmp.clone();
  }, true);
}

/** Moss / icicle drips hanging from the cap lip (front and sides) — breaks the straight edge like the reference. */
export function capDrips(o: SlabOpts, rng: Rng): THREE.BufferGeometry[] {
  const { w, depth, theme } = o;
  const out: THREE.BufferGeometry[] = [];
  const capT = o.capThickness ?? 0.5;
  const icy = theme.capStyle === 'snow', sandy = theme.capStyle === 'sand';
  const a = C(theme.terrain.capB), d = C(theme.terrain.capDark);
  const n = Math.round(w * (sandy ? 0.35 : 0.8));
  for (let i = 0; i < n; i++) {
    const front = rng() < 0.8;
    const len = range(rng, 0.35, 1.1) * (icy ? 1.2 : 1);
    const r = range(rng, 0.1, 0.24);
    // tapered tongue: wide where it hangs off the lip, thin at the tip (cone + noise), flush with the cap edge
    const g = new THREE.ConeGeometry(r, len, 6, 3);
    g.rotateX(Math.PI);
    const pp = g.getAttribute('position') as THREE.BufferAttribute;
    for (let k = 0; k < pp.count; k++) {
      const yy = pp.getY(k), nn = noise3(pp.getX(k) * 4 + i, yy * 3, pp.getZ(k) * 4, 5);
      pp.setXYZ(k, pp.getX(k) * (0.8 + nn * 0.5) + Math.sin(yy * 3 + i) * 0.04, yy, pp.getZ(k) * 0.55);
    }
    const x = front ? range(rng, -w / 2, w / 2) : (rng() < 0.5 ? -1 : 1) * (w / 2 + 0.12);
    const z = front ? depth / 2 + 0.1 : range(rng, -depth / 2 + 0.5, depth / 2 - 0.2);
    if (!front) g.rotateY(Math.PI / 2);
    xf(g, x, -capT * 0.75 - len / 2, z);
    paint(g, (_x, y) => lerpColor(d, a, sstep(-capT - len, -capT * 0.4, y) * 0.85), true, icy ? SURF.PLAIN : SURF.FOLIAGE);
    out.push(g);
  }
  return out;
}

/** Stalactite-like roots under the platform (chunky floating-rock silhouette). */
export function underSpikes(o: SlabOpts, rng: Rng): THREE.BufferGeometry[] {
  const { w, h, depth, taper, theme } = o;
  const n = Math.max(1, Math.round(w / 3));
  const out: THREE.BufferGeometry[] = [];
  const rd = C(theme.terrain.rockDark), rm = C(theme.terrain.rockMid);
  for (let i = 0; i < n; i++) {
    const r = range(rng, 0.4, 0.85) * Math.min(1.2, 0.6 + h / 5);
    const len = range(rng, 1.0, 2.4) * Math.min(1.5, 0.7 + h / 4);
    const g = smoothBlob(r, 1, 0.22, (x, y, z) => noise3(x + i * 3, y, z, 13));
    xf(g, 0, 0, 0, 0, rng() * 6.28, 0, 1, len / r * 0.55, 1);
    const x = (n === 1 ? 0 : (i / (n - 1) - 0.5)) * w * taper * 0.7 + range(rng, -0.3, 0.3);
    const z = range(rng, -depth * 0.2, depth * 0.15);
    xf(g, x, -h - len * 0.25, z);
    paint(g, (_x, y) => lerpColor(rd, rm, Math.max(0, Math.min(1, (y + h + len) / (len * 1.2))) * 0.6), true);
    out.push(g);
  }
  return out;
}

/** Grass blade clusters (fans of thin blades, dark roots → bright tips), denser along the front lip. */
export function topTufts(o: SlabOpts, rng: Rng): THREE.BufferGeometry[] {
  const { w, depth, theme } = o;
  const out: THREE.BufferGeometry[] = [];
  if (theme.capStyle !== 'grass') return out;
  const n = Math.round(w * 2.4);
  const a = C(theme.terrain.capA), b = C(theme.terrain.capB), d = C(theme.terrain.capDark);
  for (let i = 0; i < n; i++) {
    const cx = range(rng, -w / 2 + 0.2, w / 2 - 0.2);
    const cz = rng() < 0.55 ? range(rng, depth / 2 - 0.5, depth / 2 + 0.05) : range(rng, -depth / 2 + 0.3, depth / 2 - 0.3);
    const blades = 4 + Math.floor(rng() * 3);
    const tipC = lerpColor(b, a, rng() * 0.8 + 0.2);
    for (let k = 0; k < blades; k++) {
      const hgt = range(rng, 0.16, 0.36);
      const g = new THREE.ConeGeometry(range(rng, 0.025, 0.04), hgt, 3, 1);
      xf(g, 0, hgt / 2, 0, range(rng, -0.45, 0.45), rng() * 6.28, range(rng, -0.45, 0.45), 1, 1, 0.4);
      xf(g, cx + range(rng, -0.06, 0.06), 0, cz + range(rng, -0.06, 0.06));
      paint(g, (_x, y) => lerpColor(d, tipC, sstep(0, hgt, y)), false, SURF.PLAIN);
      out.push(g);
    }
  }
  return out;
}

export function flower(rng: Rng, x: number, z: number, colors: string[], scale = 1): THREE.BufferGeometry[] {
  return withSurface(SURF.PLAIN, () => {
    const c = col(pick(rng, colors));
    const stemH = range(rng, 0.22, 0.4) * scale;
    const stem = xf(new THREE.CylinderGeometry(0.018, 0.022, stemH, 4), x, stemH / 2, z);
    solid(stem, '#4d7a33');
    const out = [stem];
    // flat 5-petal disc (10 triangles): reads as a flower from the gameplay camera at a fraction of the cost
    const head = new THREE.CircleGeometry(0.1 * scale, 10);
    const hp = head.getAttribute('position') as THREE.BufferAttribute;
    for (let i = 1; i < hp.count; i++) { const k = (i - 1) % 2 ? 0.45 : 1; hp.setXY(i, hp.getX(i) * k, hp.getY(i) * k); }
    xf(head, x, stemH + 0.03, z, -Math.PI / 2 + 0.5, rng() * 6, 0);
    out.push(solid(head, c, true));
    out.push(solid(xf(new THREE.IcosahedronGeometry(0.035 * scale, 0), x, stemH + 0.05, z), '#f6c43a'));
    return out;
  });
}

export function bush(rng: Rng, theme: WorldTheme, x: number, z: number, r: number, seed: number, snowy = false): THREE.BufferGeometry[] {
  return withSurface(SURF.FOLIAGE, () => {
    const out: THREE.BufferGeometry[] = [];
    const base = pick(rng, theme.foliage.length > 1 ? theme.foliage : ['#6c9a3a', '#4f7f34']);
    const n = 3 + Math.floor(rng() * 2);
    for (let i = 0; i < n; i++) {
      const rr = r * range(rng, 0.5, 0.85);
      const g = smoothBlob(rr, 1, 0.2, (a, b, c) => noise3(a + i * 5, b, c, seed));
      const px = x + range(rng, -r * 0.6, r * 0.6), py = rr * 0.7, pz = z + range(rng, -r * 0.4, r * 0.4);
      xf(g, px, py, pz, 0, rng() * 6, 0, 1, 0.82, 1);
      const cc = col(base);
      const hsl = { h: 0, s: 0, l: 0 }; cc.getHSL(hsl); cc.setHSL(hsl.h + range(rng, -0.015, 0.015), hsl.s, hsl.l * range(rng, 0.88, 1.12));
      const dark = cc.clone().multiplyScalar(0.45);
      // ambient occlusion: dark at the base / inside, sun-lit crown
      const snow = new THREE.Color('#f4f8ff');
      paint(g, (_x, y, _z, _nx, ny) => { const c = lerpColor(dark, cc, sstep(0, rr * 1.4, y) * 0.75 + Math.max(0, ny) * 0.25); if (snowy && ny > 0.35) c.lerp(snow, Math.min(1, (ny - 0.35) * 2.5)); return c; }, true);
      out.push(g);
    }
    return out;
  });
}

export function mushroom(rng: Rng, x: number, z: number, s = 1): THREE.BufferGeometry[] {
  return withSurface(SURF.PLAIN, () => {
    const stemH = 0.35 * s;
    const stem = xf(new THREE.CylinderGeometry(0.07 * s, 0.1 * s, stemH, 8), x, stemH / 2, z);
    paint(stem, (_x, y) => lerpColor(col('#b9a07a'), col('#f4e6c8'), sstep(0, stemH, y)), true);
    const capC = col(rng() < 0.5 ? '#d9402a' : '#f08a2c');
    const cap = xf(new THREE.SphereGeometry(0.28 * s, 12, 6, 0, Math.PI * 2, 0, Math.PI / 2), x, stemH, z, 0, 0, 0, 1, 0.7, 1);
    paint(cap, (_x, y) => lerpColor(capC.clone().multiplyScalar(0.7), capC, sstep(stemH, stemH + 0.18 * s, y)), true);
    const gill = xf(new THREE.CylinderGeometry(0.27 * s, 0.1 * s, 0.03 * s, 12), x, stemH - 0.01, z); solid(gill, '#e9d3b0', true);
    const dots: THREE.BufferGeometry[] = [];
    for (let i = 0; i < 4; i++) {
      const a = rng() * 6.28, rr = 0.15 * s;
      dots.push(solid(xf(new THREE.SphereGeometry(0.035 * s, 5, 4), x + Math.cos(a) * rr, stemH + 0.13 * s, z + Math.sin(a) * rr, 0, 0, 0, 1, 0.5, 1), '#fff3e0', true));
    }
    return [stem, cap, gill, ...dots];
  });
}

export function fence(theme: WorldTheme, x0: number, x1: number, z: number): THREE.BufferGeometry[] {
  return withSurface(SURF.WOOD, () => {
    const out: THREE.BufferGeometry[] = [];
    const wood = col(theme.terrain.wood), dark = col(theme.terrain.woodDark);
    const n = Math.max(2, Math.round((x1 - x0) / 1.4));
    for (let i = 0; i <= n; i++) {
      const x = x0 + ((x1 - x0) * i) / n;
      const b = roundedBlock(0.16, 1.0, 0.16, 0.04, 0.5, 1);
      out.push(solid(xf(b.geometry, x, 0.5, z, 0, 0, (i % 2 ? 1 : -1) * 0.03), dark, true));
      out.push(solid(xf(new THREE.ConeGeometry(0.11, 0.14, 4), x, 1.06, z, 0, Math.PI / 4, 0), dark));
    }
    for (const y of [0.35, 0.72]) { const b = roundedBlock(x1 - x0, 0.12, 0.1, 0.03, 0.8, 1); out.push(solid(xf(b.geometry, (x0 + x1) / 2, y, z + 0.06, 0, 0, (y - 0.5) * 0.02), wood, true)); }
    return out;
  });
}

export function signPost(theme: WorldTheme, x: number, z: number): THREE.BufferGeometry[] {
  return withSurface(SURF.WOOD, () => {
    const wood = col(theme.terrain.wood), dark = col(theme.terrain.woodDark);
    return [
      solid(xf(roundedBlock(0.14, 1.3, 0.14, 0.03, 0.5, 1).geometry, x, 0.65, z), dark, true),
      solid(xf(roundedBlock(1.0, 0.55, 0.1, 0.04, 0.5, 1).geometry, x, 1.15, z + 0.08, 0, 0, 0.06), wood, true),
      solid(xf(new THREE.BoxGeometry(0.7, 0.07, 0.05), x, 1.2, z + 0.14, 0, 0, 0.06), dark),
      solid(xf(new THREE.BoxGeometry(0.5, 0.07, 0.05), x - 0.05, 1.05, z + 0.14, 0, 0, 0.06), dark),
    ];
  });
}

/** Hanging vines with leaves from the front lip. */
export function vines(rng: Rng, theme: WorldTheme, w: number, depth: number, n: number): THREE.BufferGeometry[] {
  return withSurface(SURF.FOLIAGE, () => vinesImpl(rng, theme, w, depth, n));
}
function vinesImpl(rng: Rng, theme: WorldTheme, w: number, depth: number, n: number): THREE.BufferGeometry[] {
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
  const parts: THREE.BufferGeometry[] = [rockBody(o), capGeometry(o), ...capDrips(o, rng), ...underSpikes(o, rng), ...topTufts(o, rng)];
  const back = -o.depth / 2 + 0.55;
  // a few flowers on every grassy platform
  if (o.theme.capStyle === 'grass') {
    const nf = Math.round(o.w * 0.5);
    for (let i = 0; i < nf; i++) parts.push(...flower(rng, range(rng, -o.w / 2 + 0.4, o.w / 2 - 0.4), range(rng, 0.5, o.depth / 2 - 0.3), ['#ffffff', '#fff3d8', '#ffd1e0', '#fff0a0'], 0.9));
  }
  const w = o.theme.worldId;
  const X = () => range(rng, -o.w / 2 + 0.6, o.w / 2 - 0.6);
  switch (o.decor) {
    case 'flowers':
      if (w === 'world_2') for (let i = 0; i < o.w * 0.5; i++) parts.push(...snowLump(rng, X(), range(rng, -o.depth / 2 + 0.6, o.depth / 2 - 0.4)));
      else if (w === 'world_3') for (let i = 0; i < o.w * 0.9; i++) parts.push(...dryGrass(rng, X(), range(rng, -o.depth / 2 + 0.6, o.depth / 2 - 0.3)));
      else if (w === 'world_4') for (let i = 0; i < o.w * 0.4; i++) parts.push(...shard(rng, X(), back + range(rng, 0, 1), range(rng, 0.25, 0.5), '#ff7a2a', '#ffd070'));
      else for (let i = 0; i < o.w * 1.3; i++) parts.push(...flower(rng, X(), range(rng, -o.depth / 2 + 0.6, o.depth / 2 - 0.3), ['#ffffff', '#ffd1e0', '#fff0a0', '#c9a6ff'], 1));
      break;
    case 'bushes': {
      const nb = Math.max(1, Math.round(o.w / 3));
      for (let i = 0; i < nb; i++) {
        const x = range(rng, -o.w / 2 + 0.8, o.w / 2 - 0.8), z = back + range(rng, 0, 0.4);
        if (w === 'world_4') deadTree(rng, o.theme, range(rng, 1.2, 2)).forEach(g => { xf(g, x, 0, z); parts.push(g); });
        else parts.push(...bush(rng, o.theme, x, z, range(rng, 0.55, 0.9), o.seed + i, w === 'world_2'));
      }
      break;
    }
    case 'vines': if (w !== 'world_4') parts.push(...vines(rng, o.theme, o.w, o.depth, Math.round(o.w * 0.9))); break;
    case 'mushrooms':
      for (let i = 0; i < 3 + Math.round(o.w / 3); i++) {
        const x = X(), z = back + range(rng, 0, 0.8), sc = range(rng, 0.8, 1.7);
        if (w === 'world_2') parts.push(...shard(rng, x, z, sc * 0.45, '#7fd0ff', '#f0fbff'));
        else if (w === 'world_3') parts.push(...columnStump(rng, o.theme, x, z, sc));
        else if (w === 'world_4') parts.push(...shard(rng, x, z, sc * 0.5, '#3a2440', '#ff8a3a'));
        else parts.push(...mushroom(rng, x, z, sc));
      }
      break;
    case 'fence': parts.push(...fence(o.theme, -o.w / 2 + 0.8, o.w / 2 - 0.8, back)); break;
    case 'sign': parts.push(...signPost(o.theme, -o.w / 2 + 0.7, back + 0.4)); if (w !== 'world_4') parts.push(...bush(rng, o.theme, o.w / 2 - 0.9, back + 0.2, 0.7, o.seed, w === 'world_2')); break;
    default: break;
  }
  if (o.decor !== 'vines' && o.w > 4 && w !== 'world_4' && w !== 'world_3') parts.push(...vines(rng, o.theme, o.w, o.depth, Math.round(o.w * 0.35)));
  return merge(parts);
}

/**
 * Big cliff mass used for walls / ceilings: rounded block with eroded strata ledges on the camera-facing face, moss on
 * ledge tops, hanging vines and the odd tree/bush clinging to the face. The gameplay-facing x faces stay FLAT (collision).
 */
export function buildCliff(cx: number, cy: number, w: number, h: number, depth: number, seed: number, theme: WorldTheme, mossTop: boolean): THREE.BufferGeometry {
  const blk = roundedBlock(w, h, depth, Math.min(0.9, w * 0.18, h * 0.3), 1.35, 2);
  deform(blk, (x, y, z, dx, dy, dz, out) => {
    const layer = Math.floor((y + 500) / 3.1), within = ((y + 500) / 3.1) - layer;
    const ledge = (noise3(layer * 0.9, 0.5, 0.5, seed + 21) - 0.5) * 1.4 + (within > 0.82 ? 0.5 : 0) * (noise3(x * 0.5, layer, z * 0.5, seed + 22) + 0.3);
    const n = fbm3(x * 0.3, y * 0.22, z * 0.3, seed, 3) - 0.5;
    const sideX = Math.abs(dx) > 0.5;
    const flute = -0.35 * Math.pow(Math.abs(Math.sin(x * 1.9 + n * 4 + seed)), 6);        // vertical grooves
    out.set(x + (sideX ? -Math.sign(dx) * Math.abs(n) * 0.25 : dx * n * 0.8), y + dy * n * 0.5, z + dz * (n * 1.5 + ledge + flute));
  });
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark), moss = C(theme.terrain.capB), mossD = C(theme.terrain.capDark);
  const tmp = new THREE.Color();
  paint(blk.geometry, (x, y, z, nx, ny, nz) => {
    const band = Math.sin(y * 2.0 + fbm3(x * 0.2, y * 0.25, z * 0.2, seed + 9) * 4);
    lerpColor(rd, rm, 0.45 + 0.3 * (0.5 + 0.5 * band), tmp);
    if (nz > 0.5 || Math.abs(nx) > 0.5) tmp.lerp(rl, 0.25 + 0.2 * noise3(x * 0.6, y * 0.6, z * 0.6, seed + 15));
    if (ny > 0.3) tmp.lerp(mossTop ? moss : rl, 0.55);                                    // ledge tops catch light / moss
    if (ny > 0.6 && mossTop) tmp.copy(lerpColor(mossD, moss, fbm3(x * 0.4, 0, z * 0.4, seed + 3)));
    else if (noise3(x * 0.7, y * 0.5, z * 0.7, seed + 12) > 0.72) tmp.lerp(moss, 0.5);      // moss / vine stains
    if (ny < -0.4) tmp.multiplyScalar(0.7);                                                   // overhang undersides
    if (fbm3(x * 0.25, y * 0.12, z * 0.25, seed + 17, 2) > 0.58) tmp.lerp(moss, 0.55);         // big moss patches
    tmp.multiplyScalar(0.8 + 0.32 * noise3(x * 1.4, y * 0.12, z * 1.4, seed + 19));           // vertical water stains
    tmp.multiplyScalar(0.92 + 0.2 * noise3(x * 1.6, y * 1.6, z * 1.6, seed + 13));
    return tmp.clone();
  }, true);
  const parts: THREE.BufferGeometry[] = [blk.geometry];
  // clinging vegetation on the camera face: grassy rock shelves with bushes / small trees, hanging vines
  const rng = mulberry32(seed * 977 + 3);
  const nV = Math.round(h / 7);
  for (let i = 0; i < nV; i++) {
    const y = -h / 2 + range(rng, 1, h - 1), x = range(rng, -w / 2 + 0.4, w / 2 - 0.4);
    const v = vines(rng, theme, 0.8, 0, 2);
    v.forEach(g => { xf(g, x, y, depth / 2 + 0.1); parts.push(g); });
  }
  if (mossTop || theme.capStyle !== 'ash') {
    const nS = Math.max(1, Math.round(h / 9));
    for (let i = 0; i < nS; i++) {
      const y = -h / 2 + (i + 0.5) * (h / nS) + range(rng, -1.2, 1.2);
      const sw = Math.min(w * 0.9, range(rng, 2.4, 4.2)), sx = range(rng, -w / 2 + sw / 2, w / 2 - sw / 2);
      const so: SlabOpts = { w: sw, h: range(rng, 0.8, 1.4), depth: range(rng, 1.4, 2.2), taper: 0.6, seed: seed * 31 + i, theme };
      const shelf = [rockBody(so), capGeometry(so), ...capDrips(so, rng)];
      const r = rng();
      if (r < 0.45) shelf.push(...bush(rng, theme, range(rng, -sw / 3, sw / 3), 0, range(rng, 0.5, 0.85), seed + i));
      else if (r < 0.8) vegetation4Cliff(rng, theme, range(rng, 2.2, 3.8)).forEach(g => { xf(g, range(rng, -sw / 4, sw / 4), 0, range(rng, -0.2, 0.2)); shelf.push(g); });
      if (theme.capStyle === 'grass') for (let k = 0; k < 3; k++) shelf.push(...flower(rng, range(rng, -sw / 2 + 0.3, sw / 2 - 0.3), range(rng, 0, so.depth / 2 - 0.2), ['#ffffff', '#ffd1e0', '#fff0a0'], 1));
      shelf.forEach(g => { xf(g, sx, y, depth / 2 + so.depth / 2 - 0.3); parts.push(g); });
    }
  }
  const out = merge(parts);
  xf(out, cx, cy, 0);
  return out;
}

/** Small tree for cliff shelves (kept here to avoid an import cycle with structures.ts). */
function vegetation4Cliff(rng: Rng, theme: WorldTheme, h: number): THREE.BufferGeometry[] {
  if (theme.worldId === 'world_4') return deadTree(rng, theme, h);
  if (theme.worldId === 'world_3') return cypressTree(rng, theme, h);
  return rng() < theme.scatter.autumn / (theme.scatter.autumn + theme.scatter.pines + 1e-4) ? autumnTree(rng, theme, h, 0) : pineTree(rng, theme, h, theme.capStyle === 'snow', 0);
}

/** Snow lump (Snow Peaks decor). */
function snowLump(rng: Rng, x: number, z: number): THREE.BufferGeometry[] {
  const r = range(rng, 0.25, 0.5);
  const g = smoothBlob(r, 1, 0.2, (a, b, c) => noise3(a, b, c, 4)); xf(g, x, r * 0.25, z, 0, rng() * 6, 0, 1, 0.55, 1);
  return [paint(g, (_x, y) => lerpColor(new THREE.Color('#b9cce6'), new THREE.Color('#f8fbff'), sstep(-0.1, r * 0.5, y)), true, SURF.PLAIN)];
}

/** Dry grass clump (Ancient Ruins decor). */
function dryGrass(rng: Rng, x: number, z: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  for (let k = 0; k < 5; k++) {
    const hgt = range(rng, 0.25, 0.5), g = new THREE.ConeGeometry(0.03, hgt, 3, 1);
    xf(g, x + range(rng, -0.08, 0.08), hgt / 2, z + range(rng, -0.08, 0.08), range(rng, -0.4, 0.4), rng() * 6, range(rng, -0.4, 0.4), 1, 1, 0.4);
    out.push(paint(g, (_x, y) => lerpColor(new THREE.Color('#8a6a3a'), new THREE.Color('#f0d48a'), sstep(0, hgt, y)), false, SURF.PLAIN));
  }
  return out;
}

/** Crystal / obsidian shard cluster (Snow: ice crystals · Volcanic: glowing obsidian). */
function shard(rng: Rng, x: number, z: number, s: number, base: string, tip: string): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const b = new THREE.Color(base), t = new THREE.Color(tip);
  for (let k = 0; k < 3; k++) {
    const hh = s * range(rng, 0.8, 1.6), g = new THREE.CylinderGeometry(0, s * 0.3, hh, 5, 1);
    xf(g, x + range(rng, -0.2, 0.2) * s, hh / 2 - 0.05, z + range(rng, -0.2, 0.2) * s, range(rng, -0.35, 0.35), rng() * 6, range(rng, -0.35, 0.35));
    const gg = g.toNonIndexed(); gg.computeVertexNormals();
    out.push(paint(gg, (_x, y) => lerpColor(b, t, sstep(0, hh, y)), false, SURF.PLAIN));
  }
  return out;
}

/** Broken fluted column stump (Ancient Ruins decor). */
function columnStump(rng: Rng, theme: WorldTheme, x: number, z: number, s: number): THREE.BufferGeometry[] {
  const hh = range(rng, 0.4, 1.1) * s, g = new THREE.CylinderGeometry(0.28 * s, 0.32 * s, hh, 10, 1);
  xf(g, x, hh / 2, z, range(rng, -0.08, 0.08), rng() * 3, range(rng, -0.08, 0.08));
  const c = new THREE.Color(theme.terrain.stone);
  return [paint(g, (_x, y) => c.clone().multiplyScalar(0.75 + 0.3 * sstep(0, hh, y)), true)];
}
