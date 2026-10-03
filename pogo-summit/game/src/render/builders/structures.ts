import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import type { StyleMaterials } from '../materials';
import { type Rng, fbm3, mulberry32, noise3, pick, range } from '../noise';
import { SURF, blobGeometry, col, lerpColor, merge, mesh, paint, solid, withSurface, xf } from '../geom';
import { autumnTree, cypressTree, deadTree, pineTree } from './trees';
import { mergeVertices } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import { roundedBlock, deform, smoothBlob, sstep } from '../shapes';
import { bush, flower, mushroom } from './rock';

const C = (h: string): THREE.Color => col(h);

/** Welded, displaceable cylinder (open or capped) — base primitive for islands, pillars, spires. */
export function weldedCylinder(rTop: number, rBot: number, h: number, radial: number, hSeg: number, open = true): THREE.BufferGeometry {
  let g: THREE.BufferGeometry = new THREE.CylinderGeometry(rTop, rBot, h, radial, hSeg, open);
  g.deleteAttribute('normal'); g.deleteAttribute('uv');
  g = mergeVertices(g, 1e-4);
  return g;
}

/** Rock column/underside: radial bulges + stepped strata + noisy tip; returns painted smooth geometry. */
export function rockColumn(theme: WorldTheme, rTop: number, rBot: number, h: number, seed: number, o: { radial?: number; strata?: number; tip?: number; moss?: number } = {}): THREE.BufferGeometry {
  const radial = o.radial ?? 12, strata = o.strata ?? 2.6;
  const g = weldedCylinder(rTop, rBot, h, radial, Math.max(3, Math.round(h / 1.4)), false);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i) - h / 2, z = p.getZ(i);
    const r = Math.hypot(x, z);
    if (r < 1e-4) { p.setXYZ(i, 0, y - (y < -h + 0.01 ? (o.tip ?? 0) : 0), 0); continue; }
    const a = Math.atan2(z, x);
    const layer = Math.floor((-y) / strata), within = ((-y) / strata) % 1;
    const step = (noise3(layer * 1.7, a * 0.7, 0.3, seed) - 0.5) * 0.22 + (within < 0.18 ? 0.06 : 0);   // eroded ledges
    const bulge = (fbm3(Math.cos(a) * 1.3, y * 0.18, Math.sin(a) * 1.3, seed + 3, 3) - 0.5) * 0.45;
    const k = 1 + bulge + step;
    p.setXYZ(i, x * k, y - (y < -h + 0.01 ? (o.tip ?? 0) * (0.6 + noise3(a, 0, 0, seed) * 0.8) : 0), z * k);
  }
  g.computeVertexNormals();
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark), moss = C(theme.terrain.capB);
  const tmp = new THREE.Color();
  paint(g, (x, y, z, nx, ny, nz) => {
    const t = Math.max(0, Math.min(1, (y + h) / h));
    const band = Math.sin(y * (Math.PI * 2 / strata) + fbm3(x * 0.2, y * 0.2, z * 0.2, seed) * 3);
    lerpColor(rd, rm, 0.25 + 0.65 * Math.pow(t, 0.6), tmp);
    if (band > 0.4) tmp.lerp(rl, 0.3 * t + 0.08);
    if (ny > 0.45) tmp.lerp(moss, 0.65 * (o.moss ?? 1));                                   // moss on ledge tops
    else if (noise3(x * 0.5, y * 0.35, z * 0.5, seed + 6) > 0.74 && t > 0.4) tmp.lerp(moss, 0.45 * (o.moss ?? 1));
    if (nz > 0.5) tmp.lerp(rl, 0.12);
    return tmp.clone().multiplyScalar(0.9 + 0.2 * noise3(x * 1.2, y * 1.2, z * 1.2, seed + 7));
  }, true);
  return g;
}

/** Grass / snow / sand / ash top disc with a domed, flat-ish top and a draped rim. Top at y ≈ 0. */
export function capDisc(theme: WorldTheme, radius: number, seed: number, rx = 1, rz = 1): THREE.BufferGeometry {
  const capT = 0.6;
  const g = weldedCylinder(radius * 1.04, radius, capT, 18, 2, false);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const r = Math.hypot(x, z) / radius;
    const n = fbm3(x * 0.3, 0, z * 0.3, seed + 9, 2);
    const yy = y > capT / 2 - 1e-3 ? 0.12 * (1 - r * r) + (n - 0.5) * 0.12 : y < -capT / 2 + 1e-3 ? -capT - n * 0.55 : -capT * 0.45 + (n - 0.5) * 0.1;
    p.setXYZ(i, x * rx * (1 + (n - 0.5) * 0.1), yy, z * rz * (1 + (n - 0.5) * 0.1));
  }
  g.computeVertexNormals();
  const A = C(theme.terrain.capA), B = C(theme.terrain.capB), D = C(theme.terrain.capDark), S = C(theme.terrain.soil);
  paint(g, (x, y, z, _nx, ny) => (ny > 0.5 ? lerpColor(B, A, fbm3(x * 0.25, 0, z * 0.25, seed + 3, 2) * 1.35 - 0.1) : lerpColor(S, D, 0.4 + 0.6 * sstep(-capT - 0.5, 0, y))), true);
  return g;
}

/** Floating island: domed cap with trees/props over a tapering, stepped rock underside. */
export function buildIsland(rng: Rng, theme: WorldTheme, radius: number, thick: number, seed: number, opts: { trees?: number; detail?: number; props?: boolean } = {}): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  const detail = opts.detail ?? 0;
  const body = rockColumn(theme, radius, radius * 0.18, thick * 1.25, seed, { radial: detail > 0 ? 14 : 10, strata: Math.max(1.2, thick / 3), tip: thick * 0.45, moss: 0.8 });
  xf(body, 0, -0.35, 0);
  parts.push(body, capDisc(theme, radius, seed));
  // moss drips around the rim
  const dA = C(theme.terrain.capB), dD = C(theme.terrain.capDark);
  const nd = detail > 0 ? Math.round(radius * 1.6) : 0;
  for (let i = 0; i < nd; i++) {
    const a = rng() * 6.283, len = range(rng, 0.4, 1.3), r = range(rng, 0.12, 0.3);
    const g = new THREE.ConeGeometry(r, len, 5, 1); g.rotateX(Math.PI);
    xf(g, Math.cos(a) * radius * 1.01, -0.5 - len / 2, Math.sin(a) * radius * 1.01, 0, -a, 0, 1, 1, 0.6);
    paint(g, (_x, y) => lerpColor(dD, dA, sstep(-0.5 - len, -0.4, y)), true, SURF.FOLIAGE);
    parts.push(g);
  }
  const n = opts.trees ?? Math.round(radius * 0.9);
  const autumn = theme.scatter.autumn, pines = theme.scatter.pines;
  for (let i = 0; i < n; i++) {
    const a = rng() * 6.283, r = Math.sqrt(rng()) * radius * 0.78;
    const h = range(rng, 3.4, 6.8) * Math.min(1.6, 0.6 + radius / 10);
    const t = vegetation(rng, theme, h, detail, autumn, pines);
    const tm = new THREE.Matrix4().makeTranslation(Math.cos(a) * r, 0.05, Math.sin(a) * r * 0.8);
    t.forEach(g => { g.applyMatrix4(tm); parts.push(g); });
  }
  if (opts.props !== false) {
    const nb = Math.round(radius * 0.6);
    for (let i = 0; i < nb; i++) {
      const a = rng() * 6.283, r = Math.sqrt(rng()) * radius * 0.85;
      const px = Math.cos(a) * r, pz = Math.sin(a) * r * 0.8;
      const pickk = rng();
      const geoms = pickk < 0.5 ? bush(rng, theme, px, pz, range(rng, 0.6, 1.1), seed + i) : pickk < 0.8 || theme.capStyle !== 'grass' ? propRock(rng, theme, px, pz, range(rng, 0.4, 0.9), seed + i) : flower(rng, px, pz, ['#ffffff', '#ffd1e0', '#fff0a0'], 1.4);
      geoms.forEach(g => { xf(g, 0, 0.05, 0); parts.push(g); });
    }
  }
  return merge(parts);
}

/** World-appropriate tree pick. */
export function vegetation(rng: Rng, theme: WorldTheme, h: number, detail: number, autumn: number, pines: number): THREE.BufferGeometry[] {
  switch (theme.worldId) {
    case 'world_3': return rng() < 0.6 ? cypressTree(rng, theme, h * 1.1) : autumnTree(rng, theme, h * 0.8, detail);
    case 'world_4': return deadTree(rng, theme, h * 0.9);
    default: return rng() * (autumn + pines + 0.0001) < autumn ? autumnTree(rng, theme, h, detail) : pineTree(rng, theme, h, theme.capStyle === 'snow', detail);
  }
}

/** Small boulder prop. */
export function propRock(rng: Rng, theme: WorldTheme, x: number, z: number, r: number, seed: number): THREE.BufferGeometry[] {
  const g = smoothBlob(r, 1, 0.3, (a, b, c) => noise3(a, b, c, seed));
  xf(g, x, r * 0.35, z, 0, rng() * 6, 0, 1, 0.7, 1);
  const rm = C(theme.terrain.rockMid), rl = C(theme.terrain.rockLight), rd = C(theme.terrain.rockDark);
  paint(g, (_x, y, _z, _nx, ny) => lerpColor(rd, ny > 0.3 ? rl : rm, sstep(-r * 0.2, r * 0.6, y)), true);
  return [g];
}

/**
 * Support under a platform so it never reads as a block hanging in the void: a stepped rock column BEHIND the play plane
 * (its front stays at z ≤ −0.6, so the player can never visually pass through it). `reach` ≥ 20 m ⇒ a pillar widening
 * down into the cloud sea; shorter ⇒ a tapered root that ends well above the platform below.
 */
export function buildSupport(rng: Rng, theme: WorldTheme, w: number, depth: number, h: number, reach: number, seed: number): { geometry: THREE.BufferGeometry; z: number } | null {
  // only platforms with NOTHING below get a full pillar; others keep their short roots (a long root would hang into the
  // sky band of the lower tier's camera — the sky corridor of DEC-034 wins)
  const toSea = reach > 90;
  if (!toSea) return null;
  const len = 48;
  const rTop = Math.min(w * 0.3, depth * 0.42, 3.2);
  const rBot = rTop * 1.45;
  const g = rockColumn(theme, rTop, rBot, len, seed, { radial: 12, strata: 2.8, tip: 0, moss: 0.9 });
  const parts: THREE.BufferGeometry[] = [g];
  const nv = Math.round(len / 6);
  for (let i = 0; i < nv; i++) {
    const a = range(rng, -0.6, 0.6), yy = -range(rng, 1, Math.min(len - 1, 14));
    vinesAt(rng, theme, Math.sin(a) * rTop, yy, Math.cos(a) * rTop * 1.02).forEach(v => parts.push(v));
  }
  const geo = merge(parts);
  xf(geo, 0, -h * 0.6, 0);
  return { geometry: geo, z: -0.6 - rTop * 1.05 };
}

function vinesAt(rng: Rng, theme: WorldTheme, x: number, y: number, z: number): THREE.BufferGeometry[] {
  return withSurface(SURF.FOLIAGE, () => {
    const out: THREE.BufferGeometry[] = [];
    const stemC = col(theme.terrain.capDark), leafC = col(theme.terrain.capB);
    const segs = 3 + Math.floor(rng() * 4);
    for (let k = 0; k < segs; k++) {
      const yy = y - k * 0.4, sway = Math.sin(k * 0.9) * 0.08;
      out.push(solid(xf(new THREE.CylinderGeometry(0.02, 0.022, 0.42, 3), x + sway, yy, z), stemC));
      if (k % 2 === 0) out.push(solid(xf(new THREE.IcosahedronGeometry(0.12, 0), x + sway + 0.1, yy, z, 0, 0, 0, 1, 0.5, 0.45), leafC));
    }
    return out;
  });
}

/** Tall rock pillar rising from the cloud sea, grass top with trees. */
export function buildPillar(rng: Rng, theme: WorldTheme, radius: number, height: number, seed: number): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  parts.push(rockColumn(theme, radius * 0.95, radius * 1.35, height, seed, { radial: 12, strata: 3.2 }));
  parts.push(capDisc(theme, radius * 0.97, seed));
  const n = Math.round(radius * 0.8);
  for (let i = 0; i < n; i++) {
    const a = rng() * 6.283, r = Math.sqrt(rng()) * radius * 0.72, h = range(rng, 3.5, 7);
    const t = vegetation(rng, theme, h, 0, theme.scatter.autumn, theme.scatter.pines);
    const tm = new THREE.Matrix4().makeTranslation(Math.cos(a) * r, 0.05, Math.sin(a) * r);
    t.forEach(gg => { gg.applyMatrix4(tm); parts.push(gg); });
  }
  return merge(parts);
}

/** A storybook castle: keep, towers with red cone roofs, curtain walls, lit windows. */
export function buildCastle(rng: Rng, theme: WorldTheme): { body: THREE.BufferGeometry; glow: THREE.BufferGeometry } {
  const parts: THREE.BufferGeometry[] = [];
  const wins: THREE.BufferGeometry[] = [];
  const stone = C('#d9ccb8'), stoneD = C('#a99a8c'), roof = C('#b9432f'), roofD = C('#7c2d28');
  // rock spire the castle stands on (instead of a green blob): stepped rock column + grass cap
  const spire = rockColumn(theme, 15, 5, 46, 4, { radial: 14, strata: 4.5, tip: 6 });
  xf(spire, 0, -0.6, 0, 0, 0, 0, 1.35, 1, 0.9);
  parts.push(spire, (() => { const c = capDisc(theme, 15, 4, 1.35, 0.9); return c; })());
  const keep = new THREE.BoxGeometry(7, 12, 6); xf(keep, 0, 6, 0);
  paint(keep, (_x, y, _z, nx, ny, nz) => lerpColor(stoneD, stone, 0.5 + 0.5 * Math.abs(nz)));
  parts.push(keep);
  const keepRoof = new THREE.ConeGeometry(5.6, 6, 4, 1); xf(keepRoof, 0, 15, 0, 0, Math.PI / 4, 0);
  paint(keepRoof, (_x, y) => lerpColor(roofD, roof, Math.min(1, (y - 12) / 6)));
  parts.push(keepRoof);
  const towers: [number, number, number, number][] = [[-9, 0, 2.1, 14], [9, 0, 2.1, 14], [-5, 5, 1.7, 10], [5, 5, 1.7, 10], [0, -6, 1.9, 12]];
  for (const [x, z, r, h] of towers) {
    const t = new THREE.CylinderGeometry(r, r * 1.08, h, 8, 1); xf(t, x, h / 2, z);
    paint(t, (_x, _y, _z, nx) => lerpColor(stoneD, stone, 0.5 + 0.5 * nx));
    parts.push(t);
    const rf = new THREE.ConeGeometry(r * 1.45, h * 0.5, 8, 1); xf(rf, x, h + h * 0.25, z);
    paint(rf, (_x, y) => lerpColor(roofD, roof, Math.min(1, (y - h) / (h * 0.5))));
    parts.push(rf);
    const flag = new THREE.BoxGeometry(1.6, 0.8, 0.1); xf(flag, x + 0.9, h + h * 0.5 + 0.8, z); solid(flag, '#f4b63a'); parts.push(flag);
    const pole = new THREE.CylinderGeometry(0.06, 0.06, 2, 4); xf(pole, x, h + h * 0.5 + 0.6, z); solid(pole, '#4a3a30'); parts.push(pole);
    for (let k = 0; k < 2; k++) { const w = new THREE.BoxGeometry(0.5, 0.9, 0.2); xf(w, x + (k ? 0.5 : -0.5) * 0, h * (0.4 + k * 0.25), z + r + 0.02); solid(w, '#ffd37a'); wins.push(w); }
  }
  for (const [x1, x2, z] of [[-9, 9, 0], [-5, 5, 5]] as [number, number, number][]) {
    const wall = new THREE.BoxGeometry(x2 - x1, 5, 1.2); xf(wall, (x1 + x2) / 2, 2.5, z);
    paint(wall, (_x, _y, _z, nx, ny, nz) => lerpColor(stoneD, stone, 0.4 + 0.5 * Math.abs(nz)));
    parts.push(wall);
    for (let i = 0; i <= (x2 - x1) / 1.4; i++) { const m = new THREE.BoxGeometry(0.7, 0.8, 1.3); xf(m, x1 + i * 1.4, 5.4, z); solid(m, stone); parts.push(m); }
  }
  for (let k = 0; k < 6; k++) { const w = new THREE.BoxGeometry(0.7, 1.2, 0.2); xf(w, -2.4 + (k % 3) * 2.4, 5 + Math.floor(k / 3) * 4, 3.02); solid(w, '#ffd37a'); wins.push(w); }
  return { body: merge(parts), glow: merge(wins) };
}

/** Stone viaduct with N arches (extruded shape with arch holes). */
export function buildArchBridge(theme: WorldTheme, arches = 5): THREE.BufferGeometry {
  const L = arches * 10, H = 12, deck = 3;
  const s = new THREE.Shape();
  s.moveTo(0, 0); s.lineTo(0, H); s.lineTo(L, H); s.lineTo(L, 0);
  for (let i = arches - 1; i >= 0; i--) {
    const x0 = i * 10 + 1.2, x1 = (i + 1) * 10 - 1.2;
    s.lineTo(x1, 0);
    const r = (x1 - x0) / 2;
    s.absarc((x0 + x1) / 2, 0, r, 0, Math.PI, false);
    s.lineTo(x0, 0);
  }
  s.closePath();
  const g = new THREE.ExtrudeGeometry(s, { depth: 3, bevelEnabled: false, curveSegments: 8 });
  xf(g, -L / 2, 0, -1.5);
  const stone = C(theme.terrain.stone), dark = C(theme.terrain.rockMid), moss = C(theme.terrain.capB);
  paint(g, (x, y, z, nx, ny, nz) => {
    const c = lerpColor(dark, stone, 0.45 + 0.5 * Math.abs(nz) + (noise3(x * 0.8, y * 0.8, z, 2) - 0.5) * 0.4);
    if (ny > 0.5) c.lerp(moss, 0.55);
    return c;
  });
  const parts: THREE.BufferGeometry[] = [g];
  for (let i = 0; i <= arches; i++) { const p = new THREE.BoxGeometry(1.6, H - deck + 1, 3.4); xf(p, -L / 2 + i * 10, (H - deck) / 2 + 0.5, 0); paint(p, () => dark.clone().lerp(stone, 0.4)); parts.push(p); }
  for (let i = 0; i < arches * 5; i++) { const b = new THREE.BoxGeometry(1.4, 0.8, 0.5); xf(b, -L / 2 + 1 + i * 2, H + 0.4, 1.5); solid(b, stone); parts.push(b); }
  return merge(parts);
}

/** Rope-and-plank bridge between two points (visible in the mid-ground, like the reference). */
export function buildWoodBridge(theme: WorldTheme, length = 14, sag = 0.9, depth = 2.4): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  const wood = C(theme.terrain.wood), dark = C(theme.terrain.woodDark);
  const n = Math.round(length / 0.7);
  const path = (t: number): [number, number] => [(t - 0.5) * length, -Math.sin(t * Math.PI) * sag];
  for (let i = 0; i <= n; i++) {
    const [x, y] = path(i / n), [x2, y2] = path(Math.min(1, (i + 0.5) / n));
    const g = new THREE.BoxGeometry(0.6, 0.12, depth); xf(g, x, y, 0, 0, 0, Math.atan2(y2 - y, x2 - x)); solid(g, lerpColor(wood, dark, ((i * 37) % 10) / 24)); parts.push(g);
  }
  for (const sz of [-1, 1]) {
    for (let i = 0; i < n; i++) {
      const [x, y] = path(i / n), [x2, y2] = path((i + 1) / n);
      const len = Math.hypot(x2 - x, y2 - y);
      const r = new THREE.CylinderGeometry(0.035, 0.035, len + 0.05, 4); xf(r, (x + x2) / 2, (y + y2) / 2 + 0.95, sz * depth / 2, 0, 0, Math.PI / 2 + Math.atan2(y2 - y, x2 - x)); solid(r, '#cdb58a'); parts.push(r);
      if (i % 3 === 0) { const p = new THREE.CylinderGeometry(0.06, 0.07, 1.0, 5); xf(p, x, y + 0.48, sz * depth / 2); solid(p, dark); parts.push(p); }
    }
  }
  for (const sx of [-1, 1]) { const p = new THREE.BoxGeometry(0.4, 2.2, 0.4); xf(p, sx * length / 2, 0.5, 0); solid(p, dark); parts.push(p); }
  return merge(parts);
}

export interface WaterfallView { group: THREE.Group; update(t: number): void }

const waterVert = `varying vec2 vUv; varying float vFogDepth;
void main(){ vUv = uv; vec4 mv = modelViewMatrix * vec4(position,1.0); vFogDepth = -mv.z; gl_Position = projectionMatrix * mv; }`;
const waterFrag = `uniform float uTime; uniform vec3 uColor; uniform vec3 uFoam; uniform vec3 uFogColor; uniform float uFogDensity;
varying vec2 vUv; varying float vFogDepth;
float h(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void main(){
  float x = vUv.x; float y = vUv.y;
  float streak = 0.0;
  for(int i=0;i<4;i++){ float fi=float(i); float cx = x*(6.0+fi*5.0)+fi*3.1; float id=floor(cx); float f=fract(cx);
    float sp = 0.9+h(vec2(id,fi))*1.3; float v = fract(y*(1.5+fi*0.7) + uTime*sp + h(vec2(id,fi+9.0)));
    streak += smoothstep(0.55,0.9,v) * smoothstep(0.0,0.35,f) * smoothstep(1.0,0.65,f) * (0.35 - fi*0.05); }
  float edge = smoothstep(0.0,0.14,x)*smoothstep(1.0,0.86,x);
  float top = smoothstep(1.0,0.9,y); float foam = smoothstep(0.12,0.0,y) + smoothstep(0.9,1.0,y)*0.6;
  vec3 c = mix(uColor, uFoam, clamp(streak + foam*0.9, 0.0, 1.0));
  float a = (0.78 + streak*0.2) * edge * (0.35 + 0.65*top);
  float fogF = 1.0 - exp(-pow(uFogDensity*vFogDepth, 2.0));
  gl_FragColor = vec4(mix(c, uFogColor, fogF), a);
}`;

/**
 * Cliff with a waterfall. `y` of the placed group = TOP of the cliff; it extends 70 m downward into the cloud sea
 * (so it always stands on something), the water ribbon runs down its front face and ends in mist.
 */
export function buildWaterfall(rng: Rng, theme: WorldTheme, mats: StyleMaterials, scale: number, seed: number): WaterfallView {
  const g = new THREE.Group();
  const W = 14 * scale, H = 70, D = 7 * scale;
  const blk = roundedBlock(W, H, D, Math.min(1.6, W * 0.12), 1.4, 2);
  deform(blk, (x, y, z, dx, dy, dz, out) => {
    const Y = y - H / 2;
    const layer = Math.floor(-Y / 3.2), within = (-Y / 3.2) % 1;
    const ledge = (noise3(layer * 1.3, x * 0.08, 0.5, seed) - 0.5) * 1.6 + (within < 0.15 ? 0.5 : 0);
    const n = fbm3(x * 0.15, Y * 0.12, z * 0.15, seed, 3) - 0.5;
    out.set(x + dx * (n * 2.2 + ledge * 0.6), Y + (dy > 0.5 ? n * 0.6 : 0), z + dz * (n * 1.8 + ledge));
  });
  const cliff = blk.geometry;
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark), moss = C(theme.terrain.capB);
  paint(cliff, (x, y, z, nx, ny, nz) => {
    const band = Math.sin(y * 1.9 + fbm3(x * 0.2, y * 0.2, z * 0.2, seed) * 3);
    const c = lerpColor(rd, rm, 0.45 + 0.25 * band);
    if (band > 0.4) c.lerp(rl, 0.4);
    if (nz > 0.5) c.lerp(rl, 0.15);
    if (ny > 0.5) return lerpColor(C(theme.terrain.capDark), moss, fbm3(x * 0.4, 0, z * 0.4, seed));
    if (ny > 0.25) c.lerp(moss, 0.6);                                       // mossy ledges
    if (noise3(x * 0.5, y * 0.3, z * 0.5, seed + 4) > 0.68) c.lerp(moss, 0.55); // moss streaks
    return c.multiplyScalar(0.85 + 0.3 * noise3(x * 1.3, y * 1.3, z * 1.3, seed + 7));
  }, true);
  const parts: THREE.BufferGeometry[] = [cliff];
  for (let i = 0; i < 6; i++) {
    const t = vegetation(rng, theme, range(rng, 4, 8) * scale, 0, theme.scatter.autumn + 0.3, theme.scatter.pines);
    const tm = new THREE.Matrix4().makeTranslation(range(rng, -W * 0.4, W * 0.4), 0.05, range(rng, -D * 0.3, D * 0.3));
    t.forEach(gg => { gg.applyMatrix4(tm); parts.push(gg); });
  }
  const body = mesh(merge(parts), mats.smooth, false, false);
  body.position.y = 0;
  g.add(body);
  const uniforms = {
    uTime: { value: 0 }, uColor: { value: C(theme.water) }, uFoam: { value: C(theme.foam) },
    uFogColor: { value: C(theme.fog.color) }, uFogDensity: { value: theme.fog.density },
  };
  const wh = 46;
  const water = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.22, wh, 1, 1), new THREE.ShaderMaterial({ uniforms, vertexShader: waterVert, fragmentShader: waterFrag, transparent: true, depthWrite: false }));
  water.position.set(W * (rng() - 0.5) * 0.3, -wh / 2 + 0.3, D / 2 + 0.35);
  g.add(water);
  const mist = new THREE.Sprite(new THREE.SpriteMaterial({ map: softPuffTexture(), color: '#ffffff', transparent: true, opacity: 0.55, depthWrite: false }));
  mist.scale.set(W * 0.9, W * 0.5, 1); mist.position.set(water.position.x, -wh + 2, D / 2 + 1);
  g.add(mist);
  return { group: g, update(t) { uniforms.uTime.value = t; mist.material.opacity = 0.5 + Math.sin(t * 1.3) * 0.06; } };
}

let _puff: THREE.Texture | null = null;
export function softPuffTexture(): THREE.Texture {
  if (_puff) return _puff;
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const x = c.getContext('2d')!;
  const rng = mulberry32(99);
  const blobs = 9;
  for (let i = 0; i < blobs; i++) {
    const a = (i / blobs) * 6.283, d = i === 0 ? 0 : 55 + rng() * 25;
    const cx = 128 + Math.cos(a) * d * 0.95, cy = 140 + Math.sin(a) * d * 0.5 - (i === 0 ? 10 : 0), r = i === 0 ? 78 : 42 + rng() * 22;
    const gr = x.createRadialGradient(cx, cy, 0, cx, cy, r);
    gr.addColorStop(0, 'rgba(255,255,255,0.95)'); gr.addColorStop(0.55, 'rgba(255,255,255,0.55)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = gr; x.beginPath(); x.arc(cx, cy, r, 0, 6.283); x.fill();
  }
  // soft shaded belly
  const bg = x.createLinearGradient(0, 100, 0, 220);
  bg.addColorStop(0, 'rgba(120,100,160,0)'); bg.addColorStop(1, 'rgba(120,100,160,0.28)');
  x.globalCompositeOperation = 'source-atop'; x.fillStyle = bg; x.fillRect(0, 0, 256, 256);
  _puff = new THREE.CanvasTexture(c);
  _puff.colorSpace = THREE.SRGBColorSpace;
  return _puff;
}

let _cumulus: THREE.Texture | null = null;
/** Crisp storybook cumulus (defined silhouette, flat belly, lilac underside) — the reference's clouds have outlines, not veils. */
export function cumulusTexture(): THREE.Texture {
  if (_cumulus) return _cumulus;
  const c = document.createElement('canvas'); c.width = 256; c.height = 128;
  const x = c.getContext('2d')!;
  const rng = mulberry32(2024);
  const base = 104;
  const puffs: [number, number, number][] = [];
  for (let i = 0; i < 11; i++) {
    const t = i / 10, cx = 28 + t * 200, mound = Math.sin(t * Math.PI);
    const r = 20 + mound * 30 * (0.7 + rng() * 0.5);
    puffs.push([cx, base - r * 0.62 - mound * 10 * rng(), r]);
  }
  puffs.push([128, base - 52, 44]);
  x.fillStyle = '#ffffff';
  for (const [cx, cy, r] of puffs) { x.beginPath(); x.arc(cx, cy, r, 0, 6.283); x.fill(); }
  // belly shade + sun-lit top, clipped to the silhouette
  x.globalCompositeOperation = 'source-atop';
  const g = x.createLinearGradient(0, 20, 0, base + 6);
  g.addColorStop(0, 'rgba(255,248,240,0)'); g.addColorStop(0.55, 'rgba(200,196,230,0.35)'); g.addColorStop(1, 'rgba(140,132,196,0.75)');
  x.fillStyle = g; x.fillRect(0, 0, 256, 128);
  // flat bottom: cut everything below the base line with a short fade
  x.globalCompositeOperation = 'destination-out';
  const cut = x.createLinearGradient(0, base - 4, 0, base + 10);
  cut.addColorStop(0, 'rgba(0,0,0,0)'); cut.addColorStop(1, 'rgba(0,0,0,1)');
  x.fillStyle = cut; x.fillRect(0, base - 4, 256, 128);
  _cumulus = new THREE.CanvasTexture(c);
  _cumulus.colorSpace = THREE.SRGBColorSpace;
  return _cumulus;
}

export { pick };
