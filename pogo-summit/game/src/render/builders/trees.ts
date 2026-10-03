import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import { type Rng, noise3, pick, range } from '../noise';
import { SURF, col, lerpColor, paint, solid, withSurface, xf } from '../geom';
import { smoothBlob, sstep } from '../shapes';

/**
 * Tree builders — origin at the base of the trunk, +y up. Smooth welded canopies (no faceting), baked ambient occlusion
 * (dark core / underside, sun-lit crown), tapered bent trunks with root flare. `detail` 0 = far (cheap), 1 = mid, 2 = near.
 */
function tint(base: string, rng: Rng, amount = 0.04): THREE.Color {
  const c = col(base);
  const hsl = { h: 0, s: 0, l: 0 };
  c.getHSL(hsl);
  return c.setHSL(hsl.h + range(rng, -amount, amount) * 0.5, Math.min(1, hsl.s * range(rng, 0.92, 1.08)), hsl.l * range(rng, 0.9, 1.1));
}

/** Tapered, slightly bent trunk with a root flare; returns the geometry and the top point. */
export function trunkGeometry(rng: Rng, theme: WorldTheme, h: number, r: number, radial = 7): THREE.BufferGeometry[] {
  return withSurface(SURF.WOOD, () => {
    const c = col(theme.trunk);
    const g = new THREE.CylinderGeometry(r * 0.55, r, h, radial, 4);
    const p = g.getAttribute('position') as THREE.BufferAttribute;
    const bend = range(rng, -0.12, 0.12) * h, ph = rng() * 6;
    for (let i = 0; i < p.count; i++) {
      const y = p.getY(i) + h / 2, t = y / h;
      const flare = 1 + Math.pow(1 - t, 4) * 0.7;                                   // root flare
      const n = noise3(p.getX(i) * 3, y * 1.5, p.getZ(i) * 3, 3) - 0.5;
      p.setXYZ(i, p.getX(i) * flare * (1 + n * 0.25) + Math.sin(t * 2.2 + ph) * bend * t, y, p.getZ(i) * flare * (1 + n * 0.25));
    }
    g.computeVertexNormals();
    paint(g, (_x, y) => lerpColor(c.clone().multiplyScalar(0.55), c, sstep(0, h, y) * 0.7 + 0.3), true);
    return [g];
  });
}

/** One canopy clump: welded blob, AO from the canopy centre (dark core) + sun-facing crown highlight. */
function clump(rng: Rng, rad: number, detail: number, k: number, cx: number, cy: number, cz: number, color: THREE.Color, coreY: number, coreR: number): THREE.BufferGeometry {
  const g = smoothBlob(rad, detail, 0.24, (a, b, c) => noise3(a + k * 3.1, b, c, 5));
  xf(g, cx, cy, cz, 0, rng() * 6, 0, 1, range(rng, 0.8, 0.95), 1);
  const dark = color.clone().multiplyScalar(0.42), lit = color.clone().lerp(new THREE.Color('#fff3c0'), 0.12);
  paint(g, (x, y, z, nx, ny) => {
    const dCore = Math.hypot(x - cx * 0.3, (y - coreY) * 1.2, z - cz * 0.3) / coreR;   // inside the crown = occluded
    const ao = sstep(0.35, 1.05, dCore);
    const c = lerpColor(dark, color, ao * 0.8 + Math.max(0, ny) * 0.3);
    if (ny > 0.55) c.lerp(lit, (ny - 0.55) * 0.9);
    return c;
  }, true, SURF.FOLIAGE);
  return g;
}

export function autumnTree(rng: Rng, theme: WorldTheme, h: number, detail = 1): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const trunkH = h * 0.5;
  out.push(...trunkGeometry(rng, theme, trunkH + h * 0.12, h * 0.065, detail > 0 ? 7 : 5));
  const n = detail > 0 ? 7 : 4;
  const palette = theme.foliage;
  const base = pick(rng, palette);
  const coreY = trunkH + h * 0.22, coreR = h * 0.42;
  for (let i = 0; i < n; i++) {
    const rad = h * range(rng, 0.2, 0.32) * (i === 0 ? 1.15 : 1);
    const ang = (i / n) * 6.283 + rng(), rr = i === 0 ? 0 : h * range(rng, 0.14, 0.28);
    const c = tint(rng() < 0.7 ? base : pick(rng, palette), rng, 0.05);
    out.push(clump(rng, rad, Math.min(detail, 1) + (detail > 1 ? 1 : 0), i, Math.cos(ang) * rr, trunkH + h * range(rng, 0.02, 0.34) + (i === 0 ? h * 0.18 : 0), Math.sin(ang) * rr * 0.75, c, coreY, coreR));
  }
  return out;
}

/** Stylised fir: drooping jagged skirts (smooth), snow caps on snowy worlds. */
export function pineTree(rng: Rng, theme: WorldTheme, h: number, snowy = false, detail = 1): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const trunkH = h * 0.2;
  out.push(...trunkGeometry(rng, theme, trunkH + h * 0.2, h * 0.04, 5));
  const tiers = detail > 0 ? 4 : 3;
  const greens = theme.pine;
  const radial = detail > 0 ? 9 : 6;
  for (let i = 0; i < tiers; i++) {
    const t = i / tiers;
    const r = h * (0.3 - t * 0.19), ch = h * (0.36 - t * 0.04);
    const g = new THREE.ConeGeometry(r, ch, radial, 2);
    const p = g.getAttribute('position') as THREE.BufferAttribute;
    for (let k = 0; k < p.count; k++) {
      const y = p.getY(k), x = p.getX(k), z = p.getZ(k);
      if (y < -ch / 2 + 0.001) {                                   // jagged, drooping skirt
        const a = Math.atan2(z, x);
        const jag = (Math.round(((a + Math.PI) / (2 * Math.PI)) * radial * 2) % 2) ? 0.82 : 1.06;
        p.setXYZ(k, x * jag, y - (jag > 1 ? ch * 0.1 : 0), z * jag);
      }
    }
    g.computeVertexNormals();
    xf(g, range(rng, -0.03, 0.03) * h, trunkH * 0.8 + h * (t * 0.58) + ch / 2, range(rng, -0.03, 0.03) * h, 0, rng() * 6, 0);
    const c = tint(pick(rng, greens), rng, 0.03);
    const tierBase = trunkH * 0.8 + h * t * 0.58;
    paint(g, (_x, y, _z, _nx, ny) => {
      const k = sstep(tierBase, tierBase + ch, y);
      const tc = lerpColor(c.clone().multiplyScalar(0.5), c, 0.3 + 0.7 * k);
      if (snowy && ny > 0.25) tc.lerp(col('#f4f8ff'), Math.min(1, (ny - 0.25) * 2.2));
      return tc;
    }, true, SURF.FOLIAGE);
    out.push(g);
  }
  return out;
}

/** Large foreground tree: gnarled trunk + huge canopy clusters (used at z ≥ 0 to frame the screen). */
export function bigTree(rng: Rng, theme: WorldTheme, scale: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const h = 16 * scale;
  out.push(...trunkGeometry(rng, theme, h * 0.7, h * 0.075, 9));
  // two branches
  for (const s of [-1, 1]) {
    const b = withSurface(SURF.WOOD, () => solid(xf(new THREE.CylinderGeometry(h * 0.02, h * 0.035, h * 0.3, 6), s * h * 0.09, h * 0.55, 0, 0, 0, -s * 0.8), col(theme.trunk).multiplyScalar(0.85), true));
    out.push(b);
  }
  const palette = theme.foliage;
  const coreY = h * 0.7, coreR = h * 0.45;
  for (let i = 0; i < 14; i++) {
    const rad = h * range(rng, 0.13, 0.22);
    const ang = (i / 14) * 6.283 + rng() * 0.6, rr = h * range(rng, 0.1, 0.42);
    out.push(clump(rng, rad, 2, i, Math.cos(ang) * rr, h * range(rng, 0.52, 0.88), Math.sin(ang) * rr * 0.6, tint(pick(rng, palette), rng, 0.05), coreY, coreR));
  }
  return out;
}

/** Bare, twisted dead tree (volcanic / ruins worlds). */
export function deadTree(rng: Rng, theme: WorldTheme, h: number): THREE.BufferGeometry[] {
  return withSurface(SURF.WOOD, () => {
    const out: THREE.BufferGeometry[] = [];
    const c = col(theme.trunk);
    const seg = (x0: number, y0: number, len: number, ang: number, r: number, depth: number): void => {
      const g = new THREE.CylinderGeometry(r * 0.6, r, len, 5, 1);
      const x1 = x0 + Math.sin(ang) * len, y1 = y0 + Math.cos(ang) * len;
      xf(g, (x0 + x1) / 2, (y0 + y1) / 2, range(rng, -0.1, 0.1), 0, 0, -ang);
      paint(g, (_x, y) => lerpColor(c.clone().multiplyScalar(0.6), c, sstep(0, h, y)), true);
      out.push(g);
      if (depth > 0) {
        seg(x1, y1, len * range(rng, 0.55, 0.75), ang + range(rng, 0.35, 0.8), r * 0.6, depth - 1);
        if (rng() < 0.8) seg(x1, y1, len * range(rng, 0.5, 0.7), ang - range(rng, 0.35, 0.8), r * 0.6, depth - 1);
      }
    };
    seg(0, 0, h * 0.45, range(rng, -0.15, 0.15), h * 0.06, 3);
    return out;
  });
}

/** Columnar cypress (ruins world). */
export function cypressTree(rng: Rng, theme: WorldTheme, h: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [...trunkGeometry(rng, theme, h * 0.2, h * 0.04, 5)];
  const c = tint(pick(rng, theme.pine), rng, 0.03);
  const g = smoothBlob(h * 0.2, 1, 0.18, (a, b, cc) => noise3(a, b * 2, cc, 9));
  xf(g, 0, h * 0.55, 0, 0, 0, 0, 1, 2.3, 1);
  paint(g, (_x, y, _z, _nx, ny) => lerpColor(c.clone().multiplyScalar(0.45), c, sstep(h * 0.15, h, y) * 0.8 + Math.max(0, ny) * 0.2), true, SURF.FOLIAGE);
  out.push(g);
  return out;
}
