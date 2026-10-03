import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import { type Rng, noise3, pick, range } from '../noise';
import { blobGeometry, col, lerpColor, paint, solid, xf } from '../geom';

/** Tree builders — origin at the base of the trunk, +y up. All smooth-normal blobs + faceted cones (low-poly look). */

function tint(base: string, rng: Rng, amount = 0.04): THREE.Color {
  const c = col(base);
  const hsl = { h: 0, s: 0, l: 0 };
  c.getHSL(hsl);
  return c.setHSL(hsl.h + range(rng, -amount, amount) * 0.5, Math.min(1, hsl.s * range(rng, 0.92, 1.08)), hsl.l * range(rng, 0.9, 1.1));
}

export function trunkGeometry(rng: Rng, theme: WorldTheme, h: number, r: number): THREE.BufferGeometry[] {
  const segs = 3;
  const out: THREE.BufferGeometry[] = [];
  const c = col(theme.trunk);
  let x = 0;
  for (let i = 0; i < segs; i++) {
    const y0 = (h * i) / segs;
    const rr0 = r * (1 - i * 0.2), rr1 = r * (1 - (i + 1) * 0.2);
    const bend = range(rng, -0.12, 0.12) * h * 0.15;
    const g = new THREE.CylinderGeometry(rr1, rr0, h / segs + 0.05, 6, 1);
    xf(g, x + bend / 2, y0 + h / segs / 2, 0, 0, 0, bend * -0.08);
    paint(g, (_x, y) => lerpColor(c.clone().multiplyScalar(0.7), c, Math.min(1, y / h) * 0.6 + 0.4), true);
    out.push(g);
    x += bend;
  }
  return out;
}

export function autumnTree(rng: Rng, theme: WorldTheme, h: number, detail = 1): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const trunkH = h * 0.5;
  out.push(...trunkGeometry(rng, theme, trunkH, h * 0.07));
  const n = detail > 0 ? 7 : 5;
  const palette = theme.foliage;
  const base = pick(rng, palette);
  for (let i = 0; i < n; i++) {
    const rad = h * range(rng, 0.22, 0.34);
    const g = blobGeometry(rad, detail, 0.26, (a, b, c) => noise3(a + i * 3.1, b, c, 5));
    const ang = (i / n) * 6.283 + rng(), rr = i === 0 ? 0 : h * range(rng, 0.14, 0.3);
    xf(g, Math.cos(ang) * rr, trunkH + h * range(rng, 0.0, 0.34) + (i === 0 ? h * 0.2 : 0), Math.sin(ang) * rr * 0.7, 0, rng() * 6, 0, 1, range(rng, 0.78, 0.95), 1);
    const c = tint(rng() < 0.7 ? base : pick(rng, palette), rng, 0.05);
    const shade = c.clone().multiplyScalar(0.55);
    paint(g, (_x, y) => lerpColor(shade, c, Math.min(1, Math.max(0, (y - (trunkH - rad)) / (rad * 2.4))), new THREE.Color()), true);
    out.push(g);
  }
  return out;
}

export function pineTree(rng: Rng, theme: WorldTheme, h: number, snowy = false): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const trunkH = h * 0.18;
  out.push(solid(xf(new THREE.CylinderGeometry(h * 0.03, h * 0.045, trunkH, 5), 0, trunkH / 2, 0), theme.trunk));
  const tiers = 4;
  const greens = theme.pine;
  for (let i = 0; i < tiers; i++) {
    const t = i / tiers;
    const r = h * (0.3 - t * 0.2), ch = h * 0.34;
    const g = new THREE.ConeGeometry(r, ch, 7, 1);
    xf(g, range(rng, -0.04, 0.04), trunkH * 0.7 + h * (0.17 + t * 0.62 - 0.17) + ch / 2 - 0.0, range(rng, -0.04, 0.04), 0, rng() * 6, 0);
    const c = tint(pick(rng, greens), rng, 0.03);
    paint(g, (_x, y, _z, _nx, ny) => {
      const k = Math.min(1, Math.max(0, (y - trunkH * 0.7) / h));
      const tc = lerpColor(c.clone().multiplyScalar(0.62), c, 0.35 + 0.65 * k);
      if (snowy && ny > 0.1) tc.lerp(col('#f4f8ff'), 0.7);
      return tc;
    });
    out.push(g);
  }
  return out;
}

/** Large foreground tree: gnarled trunk + huge canopy clusters (used at z > 0 to frame the screen). */
export function bigTree(rng: Rng, theme: WorldTheme, scale: number): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = [];
  const h = 16 * scale;
  out.push(...trunkGeometry(rng, theme, h * 0.62, h * 0.07));
  const palette = theme.foliage;
  for (let i = 0; i < 14; i++) {
    const rad = h * range(rng, 0.13, 0.22);
    const g = blobGeometry(rad, 1, 0.28, (a, b, c) => noise3(a + i * 2.3, b, c, 9));
    const ang = (i / 14) * 6.283 + rng() * 0.6, rr = h * range(rng, 0.1, 0.42);
    xf(g, Math.cos(ang) * rr, h * range(rng, 0.52, 0.86), Math.sin(ang) * rr * 0.6, 0, rng() * 6, 0, 1, 0.8, 1);
    const c = tint(pick(rng, palette), rng, 0.05);
    const shade = c.clone().multiplyScalar(0.42);
    paint(g, (_x, y) => lerpColor(shade, c, Math.min(1, Math.max(0, (y - h * 0.4) / (h * 0.5)))), true);
    out.push(g);
  }
  return out;
}
