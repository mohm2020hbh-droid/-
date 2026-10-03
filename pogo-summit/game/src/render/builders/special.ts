import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import type { HazardDef, PlatformDef } from '../../data/LevelData';
import type { StyleMaterials } from '../materials';
import { mulberry32, noise3, pick, range } from '../noise';
import { blobGeometry, col, lerpColor, merge, mesh, paint, solid, xf } from '../geom';
import { type Decor, buildRockPlatform, flower } from './rock';

/** Wooden plank platform (static or hanging). Top at y = 0. */
export function buildWoodPlatform(p: PlatformDef, theme: WorldTheme): THREE.BufferGeometry {
  const rng = mulberry32((p.seed ?? 1) * 31 + 7);
  const depth = p.depth ?? 2.8;
  const { w, h } = p;
  const wood = col(theme.terrain.wood), dark = col(theme.terrain.woodDark);
  const parts: THREE.BufferGeometry[] = [];
  const nPl = Math.max(4, Math.round(w / 0.55));
  const plankW = w / nPl;
  for (let i = 0; i < nPl; i++) {
    const g = new THREE.BoxGeometry(plankW * 0.94, h * 0.55, depth, 1, 1, 1);
    xf(g, -w / 2 + plankW * (i + 0.5), -h * 0.275, 0);
    const c = lerpColor(wood, dark, range(rng, 0.0, 0.35));
    paint(g, (_x, _y, _z, _nx, ny) => (ny > 0.5 ? c.clone().multiplyScalar(1.1) : c.clone().multiplyScalar(0.92)));
    out(parts, g);
  }
  // under-beams
  for (const x of [-w / 2 + 0.5, w / 2 - 0.5]) {
    const g = new THREE.BoxGeometry(0.34, h * 0.55, depth * 1.06);
    xf(g, x, -h * 0.78, 0); solid(g, dark); parts.push(g);
  }
  const rail = new THREE.BoxGeometry(w * 0.94, h * 0.18, 0.28); xf(rail, 0, -h * 0.78, depth / 2 + 0.04); solid(rail, dark); parts.push(rail);
  // iron corner rings
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    const r = new THREE.TorusGeometry(0.13, 0.04, 5, 8); xf(r, sx * (w / 2 - 0.35), 0.06, sz * (depth / 2 - 0.3), Math.PI / 2, 0, 0); solid(r, '#4a4a55'); parts.push(r);
  }
  return merge(parts);
}
const out = (arr: THREE.BufferGeometry[], g: THREE.BufferGeometry): void => { arr.push(g); };

/** Ice slab with crystals. */
export function buildIcePlatform(p: PlatformDef, theme: WorldTheme): THREE.BufferGeometry {
  const t = theme.terrain;
  const iceTheme: WorldTheme = {
    ...theme, capStyle: 'snow',
    terrain: { ...t, rockLight: '#eaf8ff', rockMid: '#a6dcf5', rockDark: '#4f8fd0', capA: '#f7fcff', capB: '#d8f0fc', capDark: '#9fd2f0', soil: '#7fb9e2' },
  };
  const rng = mulberry32((p.seed ?? 3) * 17 + 5);
  const base = buildRockPlatform({ w: p.w, h: p.h, depth: p.depth ?? 4.2, taper: p.taper ?? 0.8, seed: p.seed ?? 3, theme: iceTheme, decor: 'none' });
  const parts: THREE.BufferGeometry[] = [base];
  const n = Math.round(p.w / 1.6);
  for (let i = 0; i < n; i++) {
    const hh = range(rng, 0.5, 1.5), rr = range(rng, 0.14, 0.3);
    const g = new THREE.ConeGeometry(rr, hh, 5, 1);
    xf(g, range(rng, -p.w / 2 + 0.6, p.w / 2 - 0.6), hh / 2, range(rng, -1.4, -0.4), range(rng, -0.2, 0.2), rng() * 6, range(rng, -0.2, 0.2));
    paint(g, (_x, y) => lerpColor(col('#8fd2f6'), col('#f4fcff'), Math.min(1, y / hh)));
    parts.push(g);
  }
  return merge(parts);
}

export interface BouncePadView {
  group: THREE.Group;
  /** 0 = rest, 1 = fully compressed. */
  setCompression(c: number): void;
}

export function buildBouncePad(p: PlatformDef, theme: WorldTheme, mats: StyleMaterials): BouncePadView {
  const g = new THREE.Group();
  const T = theme.terrain;
  const top = new THREE.Group();
  const r = p.w / 2;
  const topDisc = xf(new THREE.CylinderGeometry(r, r * 0.96, 0.28, 14), 0, -0.14, 0);
  paint(topDisc, (_x, _y, _z, _nx, ny) => (ny > 0.5 ? col(T.bounceTop).multiplyScalar(1.05) : col(T.bounceTop).multiplyScalar(0.62)));
  const ring = xf(new THREE.TorusGeometry(r * 0.82, 0.045, 5, 18), 0, 0.01, 0, Math.PI / 2, 0, 0); solid(ring, '#ffe08a');
  const dots = [0, 1, 2, 3, 4, 5].map(i => solid(xf(new THREE.IcosahedronGeometry(0.07, 0), Math.cos(i * 1.047) * r * 0.55, 0.02, Math.sin(i * 1.047) * r * 0.55), '#fff2c0'));
  top.add(mesh(merge([topDisc, ring, ...dots]), mats.flat, true));
  g.add(top);
  const baseH = 0.32;
  const base = xf(new THREE.CylinderGeometry(r * 0.96, r * 1.04, baseH, 14), 0, -p.h + baseH / 2, 0);
  paint(base, () => col(T.bounceTop).multiplyScalar(0.5));
  g.add(mesh(merge([base]), mats.flat, true));
  const coilGeo = merge([solid(new THREE.TorusGeometry(0.46, 0.07, 5, 12), T.bounceCoil)]);
  const rings: THREE.Mesh[] = [];
  const coilTop = -0.28, coilBot = -p.h + baseH;
  const N = 6;
  for (const sx of [-0.46, 0.46]) {
    for (let i = 0; i < N; i++) {
      const m = mesh(coilGeo, mats.flat, true, true);
      m.rotation.x = Math.PI / 2; m.scale.setScalar(p.w / 5.2 * 0.95);
      m.position.x = sx * p.w / 5.2 * 1.9;
      g.add(m); rings.push(m);
    }
  }
  const setCompression = (c: number): void => {
    const span = coilTop - coilBot;
    const k = 1 - c * 0.62;
    top.position.y = -(1 - k) * span;
    let idx = 0;
    for (const _sx of [0, 1]) for (let i = 0; i < N; i++) { rings[idx++].position.y = coilBot + (span * k) * (i + 0.5) / N; }
  };
  setCompression(0);
  return { group: g, setCompression };
}

/** Red crystal cluster on pink rock — the "Hazard" surface from the reference. */
export function buildHazard(h: HazardDef, theme: WorldTheme): THREE.BufferGeometry {
  const rng = mulberry32(h.id.length * 977 + Math.round(h.x * 13));
  const parts: THREE.BufferGeometry[] = [];
  const T = theme.terrain;
  const n = Math.max(5, Math.round(h.w * 2.6));
  for (let i = 0; i < n; i++) {
    const f = i / (n - 1) - 0.5;
    const hh = h.h * range(rng, 0.55, 1.05) * (1 - Math.abs(f) * 0.5);
    const rr = range(rng, 0.13, 0.24);
    const g = new THREE.ConeGeometry(rr, hh, 5, 1);
    xf(g, f * h.w * 0.9, hh / 2, range(rng, -0.7, 0.7), range(rng, -0.15, 0.15), rng() * 6, range(rng, -0.2, 0.2));
    const c = col(T.hazardCrystal), tip = col('#ff9a8a');
    paint(g, (_x, y) => lerpColor(c.clone().multiplyScalar(0.7), tip, Math.min(1, Math.max(0, y / h.h)) * 0.7 + 0.1));
    parts.push(g);
  }
  // pink rock base lumps
  for (let i = 0; i < 4; i++) {
    const b = blobGeometry(range(rng, 0.3, 0.55), 0, 0.3, (a, bb, c) => noise3(a + i, bb, c, 3));
    xf(b, range(rng, -h.w / 2, h.w / 2), 0.1, range(rng, -0.8, 0.8), 0, rng() * 6, 0, 1, 0.55, 1);
    paint(b, () => col(T.hazardRock).multiplyScalar(range(rng, 0.8, 1.05)), true);
    parts.push(b);
  }
  return merge(parts);
}

export interface GoalView { group: THREE.Group; update(t: number): void }

export function buildGoal(x: number, y: number, theme: WorldTheme, mats: StyleMaterials): GoalView {
  const g = new THREE.Group();
  g.position.set(x, y, 0);
  const pole = solid(xf(new THREE.CylinderGeometry(0.07, 0.09, 4.4, 6), 0, 2.2, 0), '#e8e0d0');
  const knob = solid(xf(new THREE.IcosahedronGeometry(0.16, 1), 0, 4.5, 0), '#f6c43a', true);
  g.add(mesh(merge([pole, knob]), mats.flat, true));
  // cloth with per-frame wave
  const cloth = new THREE.PlaneGeometry(1.9, 1.15, 10, 4);
  const cp = cloth.getAttribute('position') as THREE.BufferAttribute;
  const base = cp.array.slice() as Float32Array;
  const colors = new Float32Array(cp.count * 3);
  const a = col('#ff8a2c'), b = col('#ffd14a');
  for (let i = 0; i < cp.count; i++) { const c = lerpColor(a, b, (base[i * 3] + 0.95) / 1.9 * 0.6); colors[i * 3] = c.r; colors[i * 3 + 1] = c.g; colors[i * 3 + 2] = c.b; }
  cloth.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  const clothMesh = new THREE.Mesh(cloth, new THREE.MeshStandardMaterial({ vertexColors: true, side: THREE.DoubleSide, roughness: 0.8 }));
  clothMesh.position.set(1.0, 3.75, 0);
  clothMesh.castShadow = true;
  g.add(clothMesh);
  // star emblem
  const starShape = new THREE.Shape();
  for (let i = 0; i < 10; i++) { const r = i % 2 === 0 ? 0.36 : 0.15, an = Math.PI / 2 + (i * Math.PI) / 5; (i === 0 ? starShape.moveTo : starShape.lineTo).call(starShape, Math.cos(an) * r, Math.sin(an) * r); }
  starShape.closePath();
  const starGeo = new THREE.ExtrudeGeometry(starShape, { depth: 0.12, bevelEnabled: true, bevelSize: 0.03, bevelThickness: 0.03, bevelSegments: 1 });
  starGeo.translate(0, 0, -0.06);
  const star = new THREE.Mesh(starGeo, new THREE.MeshStandardMaterial({ color: '#ffd24a', emissive: '#ff9a1a', emissiveIntensity: 0.6, roughness: 0.4, metalness: 0.2 }));
  star.position.set(1.0, 3.75, 0.08);
  star.scale.setScalar(1.0);
  g.add(star);
  const floating = new THREE.Mesh(starGeo, star.material);
  floating.position.set(-1.8, 2.3, 0.4);
  floating.scale.setScalar(1.3);
  g.add(floating);
  const halo = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTexture('#ffd36a'), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, opacity: 0.8 }));
  halo.scale.set(3.2, 3.2, 1); halo.position.copy(floating.position);
  g.add(halo);
  const update = (t: number): void => {
    for (let i = 0; i < cp.count; i++) {
      const bx = base[i * 3], by = base[i * 3 + 1];
      const k = (bx + 0.95) / 1.9;
      cp.setZ(i, Math.sin(t * 4.0 - bx * 3.2) * 0.14 * k + Math.sin(t * 2.3 + by * 2) * 0.03 * k);
      cp.setY(i, by + Math.sin(t * 3.3 - bx * 2.4) * 0.03 * k);
    }
    cp.needsUpdate = true;
    floating.rotation.y = t * 1.6; star.rotation.y = Math.sin(t * 1.3) * 0.3;
    floating.position.y = 2.3 + Math.sin(t * 2.1) * 0.15; halo.position.y = floating.position.y;
    halo.material.opacity = 0.65 + Math.sin(t * 3) * 0.15;
  };
  return { group: g, update };
}

const texCache = new Map<string, THREE.Texture>();
export function glowTexture(color: string): THREE.Texture {
  const hit = texCache.get(color);
  if (hit) return hit;
  const c = document.createElement('canvas'); c.width = c.height = 128;
  const x = c.getContext('2d')!;
  const gr = x.createRadialGradient(64, 64, 0, 64, 64, 64);
  gr.addColorStop(0, color); gr.addColorStop(0.3, color + 'aa'); gr.addColorStop(1, color + '00');
  x.fillStyle = gr; x.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  texCache.set(color, t);
  return t;
}

export { pick, flower };
export type { Decor };
