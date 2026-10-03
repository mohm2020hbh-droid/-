import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import type { HazardDef, PlatformDef } from '../../data/LevelData';
import type { StyleMaterials } from '../materials';
import { mulberry32, noise3, pick, range } from '../noise';
import { SURF, col, lerpColor, merge, mesh, paint, solid, withSurface, xf } from '../geom';
import { deform, roundedBlock, smoothBlob, sstep } from '../shapes';
import { type Decor, buildRockPlatform, flower } from './rock';

/**
 * Platform types with a unique, readable identity each (visual upgrade):
 *   wood    bevelled planks, nails, cross beams, rope trim (+ posts when static; chain hooks when hanging)
 *   ice     faceted glossy crystal slab with frosted top (own glossy material, flat shading)
 *   bounce  cushion with polka dots + rim, twin helix springs on a stone/wood pedestal
 *   hazard  faceted glowing crystal shards on a rock base
 *   goal    banner pole on a plinth with a light pillar
 * Local frame: origin = top-centre of the collider; the walkable top is EXACTLY y = 0.
 */

/** Bevelled plank deck (static or hanging). Top at y = 0. */
export function buildWoodPlatform(p: PlatformDef & { depth: number }, theme: WorldTheme, hanging = false): THREE.BufferGeometry {
  const rng = mulberry32((p.seed ?? 1) * 31 + 7);
  const { w, h, depth } = p;
  const wood = col(theme.terrain.wood), dark = col(theme.terrain.woodDark);
  const parts: THREE.BufferGeometry[] = [];
  const plankT = Math.min(0.3, h * 0.35);
  withSurface(SURF.WOOD, () => {
    const nPl = Math.max(4, Math.round(w / 0.5));
    const plankW = w / nPl;
    for (let i = 0; i < nPl; i++) {
      const b = roundedBlock(plankW * 0.93, plankT, depth * range(rng, 0.96, 1.02), 0.05, 0.6, 1);
      const g = b.geometry;
      xf(g, -w / 2 + plankW * (i + 0.5), -plankT / 2, range(rng, -0.05, 0.05), 0, range(rng, -0.012, 0.012), 0);
      const c = lerpColor(wood, dark, range(rng, 0.0, 0.3)).offsetHSL(range(rng, -0.01, 0.01), 0, range(rng, -0.03, 0.03));
      paint(g, (_x, y, _z, _nx, ny) => (ny > 0.6 ? c.clone().multiplyScalar(1.08) : c.clone().multiplyScalar(0.8 + 0.2 * sstep(-plankT, 0, y))), true);
      parts.push(g);
    }
    // cross beams (front + back) and two stringers underneath
    for (const z of [depth / 2 - 0.25, -depth / 2 + 0.25]) {
      const b = roundedBlock(w * 0.98, 0.22, 0.24, 0.06, 0.8, 1).geometry;
      xf(b, 0, -plankT - 0.11, z); paint(b, () => dark.clone().multiplyScalar(0.95), true); parts.push(b);
    }
    for (const x of [-w / 2 + 0.55, w / 2 - 0.55]) {
      const b = roundedBlock(0.26, 0.3, depth * 1.04, 0.06, 0.8, 1).geometry;
      xf(b, x, -plankT - 0.3, 0); paint(b, () => dark.clone().multiplyScalar(0.8), true); parts.push(b);
    }
    // static decks stand on two log posts that go down behind the play plane
    if (!hanging) {
      for (const x of [-w / 2 + 0.6, w / 2 - 0.6]) {
        const post = new THREE.CylinderGeometry(0.17, 0.22, 9, 7, 1);
        xf(post, x, -plankT - 4.6, -depth / 2 + 0.25, range(rng, -0.04, 0.04), 0, range(rng, -0.05, 0.05));
        paint(post, (_x, y) => lerpColor(dark.clone().multiplyScalar(0.5), dark, sstep(-9, -0.5, y)), true);
        parts.push(post);
      }
      const brace = new THREE.CylinderGeometry(0.09, 0.09, Math.hypot(w - 1.2, 3), 5);
      xf(brace, 0, -plankT - 2.2, -depth / 2 + 0.2, 0, 0, Math.atan2(w - 1.2, 3) * (rng() < 0.5 ? 1 : -1));
      paint(brace, () => dark.clone().multiplyScalar(0.75), true); parts.push(brace);
    }
  });
  withSurface(SURF.PLAIN, () => {
    // rope trim along the front edge + nails
    const rope = new THREE.CylinderGeometry(0.045, 0.045, w * 0.98, 6); xf(rope, 0, -plankT * 0.55, depth / 2 + 0.04, 0, 0, Math.PI / 2);
    solid(rope, '#cdb58a', true); parts.push(rope);
    const nPl = Math.max(4, Math.round(w / 0.5));
    for (let i = 0; i < nPl; i++) for (const z of [depth / 2 - 0.25, -depth / 2 + 0.25]) {
      const nail = new THREE.CylinderGeometry(0.035, 0.035, 0.02, 5); xf(nail, -w / 2 + (w / nPl) * (i + 0.5), 0.005, z);
      solid(nail, '#4a4650', true); parts.push(nail);
    }
    if (hanging) for (const sx of [-1, 1]) {   // iron hooks where the chains attach
      const hook = new THREE.TorusGeometry(0.16, 0.05, 6, 10); xf(hook, sx * (w / 2 - 0.35), 0.12, 0, 0, 0, 0);
      solid(hook, '#6c6a78', true); parts.push(hook);
      const plate = roundedBlock(0.5, 0.06, 0.5, 0.02, 0.5, 1).geometry; xf(plate, sx * (w / 2 - 0.35), 0.01, 0);
      solid(plate, '#55535f', true); parts.push(plate);
    }
  });
  return merge(parts);
}

/** Chain of interlocking links spanning y ∈ [−0.5, 0.5] at rest length `len` (scaled along y by the length at runtime). */
export function chainGeometry(len: number): THREE.BufferGeometry {
  const linkL = 0.34, n = Math.max(4, Math.round(len / linkL));
  const parts: THREE.BufferGeometry[] = [];
  withSurface(SURF.PLAIN, () => {
    for (let i = 0; i < n; i++) {
      const g = new THREE.TorusGeometry(0.11, 0.035, 5, 8);
      xf(g, 0, 0, 0, 0, i % 2 ? Math.PI / 2 : 0, 0, 1, 1.7, 1);
      xf(g, 0, (i + 0.5) / n - 0.5, 0, 0, 0, 0, 1, 1 / len, 1);
      solid(g, i % 2 ? '#7b7a88' : '#8c8b99', true);
      parts.push(g);
    }
  });
  return merge(parts);
}

/** Faceted glossy ice slab with a frosted top and a few crystal shards. Uses the flat-shaded glossy ice material. */
export function buildIcePlatform(p: PlatformDef & { depth: number }, theme: WorldTheme): THREE.BufferGeometry {
  const rng = mulberry32((p.seed ?? 3) * 17 + 5);
  const { w, h, depth } = p;
  const taper = p.taper ?? 0.8;
  const t = theme.terrain;
  const style = theme.worldId === 'world_3' ? 'tile' : theme.worldId === 'world_4' ? 'obsidian' : 'ice';
  const deep = col(style === 'obsidian' ? '#1d1426' : style === 'tile' ? '#1f6f78' : '#245aa8');
  const mid = col(style === 'obsidian' ? '#3a2a4a' : style === 'tile' ? '#3fb6b0' : '#56b2ea');
  void t;
  const frost = col(style === 'obsidian' ? '#6a4a7a' : style === 'tile' ? '#e8f4ee' : '#d6f0ff');
  const parts: THREE.BufferGeometry[] = [];
  withSurface(SURF.PLAIN, () => {
    const blk = roundedBlock(w, h + 0.1, depth, 0.25, 0.9, 1);
    deform(blk, (x, y, z, dx, dy, dz, out) => {
      const tt = (y + (h + 0.1) / 2) / (h + 0.1);
      let X = x * (taper + (1 - taper) * tt), Y = y - (h + 0.1) / 2 + 0.0, Z = z * (0.85 + 0.15 * tt);
      const n = noise3(x * 0.8, y * 0.8, z * 0.8, p.seed ?? 3) - 0.5;
      if (dy > 0.9) Y = 0;                                                      // walkable top exactly flat
      else if (Math.abs(dx) > 0.4) X -= Math.sign(dx) * Math.abs(n) * 0.35;    // inward only
      else { Z += dz * n * 0.6; if (dy < -0.4) Y -= (0.3 + (n + 0.5) * 1.2) * Math.min(1, h / 2.5); }
      out.set(X, Y, Z);
    });
    const g = blk.geometry.toNonIndexed(); g.computeVertexNormals();          // facets: crystal look
    paint(g, (x, y, z, nx, ny) => {
      if (ny > 0.85) return lerpColor(mid, frost, 0.35 + 0.35 * noise3(x * 1.6, 0, z * 1.6, 9));
      const k = sstep(-h - 0.8, 0, y);
      const c = lerpColor(deep, mid, k);
      if (noise3(x * 1.4, y * 1.4, z * 1.4, 21) > 0.68) c.lerp(frost, 0.45);   // internal veins
      if (y > -0.25) c.lerp(frost, 0.35);
      return c;
    });
    parts.push(g);
    const n = Math.round(w / 1.6);
    for (let i = 0; i < n; i++) {
      const hh = range(rng, 0.5, 1.4), rr = range(rng, 0.12, 0.26);
      const s = new THREE.CylinderGeometry(0, rr, hh, 6, 1);
      xf(s, range(rng, -w / 2 + 0.6, w / 2 - 0.6), hh / 2 - 0.05, range(rng, -depth / 2 + 0.3, -depth / 2 + 1.0), range(rng, -0.25, 0.25), rng() * 6, range(rng, -0.25, 0.25));
      paint(s, (_x, y) => lerpColor(mid, frost, sstep(0, hh, y)));
      parts.push(s);
    }
  });
  return merge(parts);
}

export interface BouncePadView {
  group: THREE.Group;
  /** 0 = rest, 1 = fully compressed. */
  setCompression(c: number): void;
}

/** Helix spring (TubeGeometry along a helix), height 1 at rest, scaled on y for compression. */
function springGeometry(r: number, turns: number, wire: number, color: string): THREE.BufferGeometry {
  const pts: THREE.Vector3[] = [];
  const N = turns * 16;
  for (let i = 0; i <= N; i++) { const t = i / N, a = t * turns * Math.PI * 2; pts.push(new THREE.Vector3(Math.cos(a) * r, t, Math.sin(a) * r)); }
  const g = new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), N, wire, 5, false);
  return withSurface(SURF.PLAIN, () => paint(g, (_x, y) => lerpColor(col(color).multiplyScalar(0.7), col(color), 0.5 + 0.5 * Math.sin(y * 30)), true));
}

export function buildBouncePad(p: PlatformDef, theme: WorldTheme, mats: StyleMaterials): BouncePadView {
  const g = new THREE.Group();
  const T = theme.terrain;
  const top = new THREE.Group();
  const r = p.w / 2;
  const topC = col(T.bounceTop);
  const pad = withSurface(SURF.PLAIN, () => {
    // cushion: flat walkable top, puffy rounded rim, polka dots
    const cushion = new THREE.CylinderGeometry(r, r * 0.94, 0.5, 28, 2);
    const cp = cushion.getAttribute('position') as THREE.BufferAttribute;
    for (let i = 0; i < cp.count; i++) {
      const x = cp.getX(i), y = cp.getY(i), z = cp.getZ(i), rr = Math.hypot(x, z) / r;
      const bulge = y > -0.01 && y < 0.01 ? 1.04 : 1;
      cp.setXYZ(i, x * bulge, y - 0.25 - (y > 0.24 ? 0 : 0) - (rr > 0.97 && y > 0.24 ? 0.04 : 0), z * bulge);
    }
    cushion.computeVertexNormals();
    paint(cushion, (_x, y, _z, _nx, ny) => (ny > 0.6 ? topC.clone().multiplyScalar(1.05) : topC.clone().multiplyScalar(0.62 + 0.3 * sstep(-0.5, 0, y))), true);
    const rim = xf(new THREE.TorusGeometry(r * 0.97, 0.11, 8, 32), 0, -0.08, 0, Math.PI / 2, 0, 0);
    solid(rim, '#ffd84a', true);
    const ring = xf(new THREE.TorusGeometry(r * 0.55, 0.05, 5, 24), 0, 0.005, 0, Math.PI / 2, 0, 0, 1, 1, 0.3);
    solid(ring, '#fff2c0', true);
    const dots: THREE.BufferGeometry[] = [];
    for (let i = 0; i < 8; i++) {
      const a = i * (Math.PI / 4) + 0.3, rr = r * (i % 2 ? 0.75 : 0.3);
      dots.push(solid(xf(new THREE.CylinderGeometry(0.16, 0.16, 0.02, 10), Math.cos(a) * rr, 0.004, Math.sin(a) * rr), '#ffe9a8', true));
    }
    return merge([cushion, rim, ring, ...dots]);
  });
  const pm = mesh(pad, mats.smooth, true, true);
  top.add(pm);
  g.add(top);
  // pedestal: stone ring + wooden board under the springs
  const baseH = 0.4;
  const base = withSurface(SURF.NATURAL, () => {
    const b = roundedBlock(p.w * 0.9, baseH, p.w * 0.55, 0.12, 0.6, 2).geometry;
    xf(b, 0, -p.h + baseH / 2, 0);
    paint(b, (_x, y) => lerpColor(col(T.rockDark), col(T.rockMid), sstep(-p.h, -p.h + baseH, y)), true);
    const stump = new THREE.CylinderGeometry(p.w * 0.36, p.w * 0.18, 3.2, 10, 3);
    xf(stump, 0, -p.h - 1.6, -0.4);
    paint(stump, (_x, y) => lerpColor(col(T.rockDark).multiplyScalar(0.7), col(T.rockMid), sstep(-p.h - 3.2, -p.h, y) * 0.8), true);
    return merge([b, stump]);
  });
  g.add(mesh(base, mats.smooth, true, true));
  const coilTop = -0.42, coilBot = -p.h + baseH;
  const springs: THREE.Mesh[] = [];
  const sg = springGeometry(0.42, 6, 0.055, T.bounceCoil);
  for (const sx of [-1, 1]) {
    const m = mesh(sg, mats.smooth, true, false);
    m.position.set(sx * r * 0.45, coilBot, 0);
    g.add(m); springs.push(m);
  }
  const setCompression = (c: number): void => {
    const span = coilTop - coilBot;
    const k = 1 - c * 0.6;
    top.position.y = -(1 - k) * span;
    for (const s of springs) s.scale.y = span * k;
  };
  setCompression(0);
  return { group: g, setCompression };
}

/** Hazard: faceted glowing shards on a rock base (shape varies per world: crystals, ice spikes, bronze spears, obsidian). */
export function buildHazard(h: HazardDef, theme: WorldTheme): THREE.BufferGeometry {
  const rng = mulberry32(h.id.length * 977 + Math.round(h.x * 13));
  const parts: THREE.BufferGeometry[] = [];
  const T = theme.terrain;
  const metal = theme.worldId === 'world_3';
  withSurface(SURF.PLAIN, () => {
    const n = Math.max(5, Math.round(h.w * 2.6));
    for (let i = 0; i < n; i++) {
      const f = i / (n - 1) - 0.5;
      const hh = h.h * range(rng, 0.6, 1.1) * (1 - Math.abs(f) * 0.45);
      const rr = metal ? range(rng, 0.05, 0.08) : range(rng, 0.13, 0.26);
      const g = new THREE.CylinderGeometry(0, rr, hh, metal ? 4 : 6, 1);
      xf(g, f * h.w * 0.9, hh / 2, range(rng, -0.7, 0.7), range(rng, -0.18, 0.18), rng() * 6, range(rng, -0.25, 0.25));
      const c = col(T.hazardCrystal), tip = c.clone().lerp(new THREE.Color('#ffffff'), 0.55);
      const gg = g.toNonIndexed(); gg.computeVertexNormals();
      paint(gg, (_x, y) => lerpColor(c.clone().multiplyScalar(0.55), tip, sstep(0, h.h, y) * 0.9));
      parts.push(gg);
    }
  });
  withSurface(SURF.NATURAL, () => {
    for (let i = 0; i < 5; i++) {
      const b = smoothBlob(range(rng, 0.3, 0.55), 1, 0.3, (a, bb, c) => noise3(a + i, bb, c, 3));
      xf(b, range(rng, -h.w / 2, h.w / 2), 0.08, range(rng, -0.8, 0.8), 0, rng() * 6, 0, 1, 0.5, 1);
      paint(b, (_x, y) => col(T.hazardRock).multiplyScalar(0.7 + 0.35 * sstep(-0.1, 0.25, y)), true);
      parts.push(b);
    }
  });
  return merge(parts);
}

export interface GoalView { group: THREE.Group; update(t: number): void }

export function buildGoal(x: number, y: number, theme: WorldTheme, mats: StyleMaterials): GoalView {
  const g = new THREE.Group();
  g.position.set(x, y, 0);
  const body = withSurface(SURF.PLAIN, () => {
    const pole = xf(new THREE.CylinderGeometry(0.075, 0.095, 4.4, 10), 0, 2.2, 0);
    paint(pole, (_x, yy) => lerpColor(col('#b9b0a0'), col('#f2ece0'), sstep(0, 4.4, yy)), true);
    const knob = xf(new THREE.SphereGeometry(0.17, 12, 8), 0, 4.52, 0); solid(knob, '#f6c43a', true);
    const collar = xf(new THREE.CylinderGeometry(0.13, 0.13, 0.12, 10), 0, 0.5, 0); solid(collar, '#f6c43a', true);
    return merge([pole, knob, collar]);
  });
  const plinth = withSurface(SURF.NATURAL, () => {
    const b = roundedBlock(1.1, 0.45, 1.1, 0.12, 0.5, 2).geometry; xf(b, 0, 0.2, 0);
    paint(b, (_x, yy, _z, _nx, ny) => col(theme.terrain.stone).multiplyScalar(ny > 0.5 ? 1.05 : 0.8 + 0.2 * sstep(0, 0.45, yy)), true);
    return b;
  });
  g.add(mesh(merge([body, plinth]), mats.smooth, true));
  // cloth with per-frame wave
  const cloth = new THREE.PlaneGeometry(1.9, 1.15, 12, 5);
  const cp = cloth.getAttribute('position') as THREE.BufferAttribute;
  const base = cp.array.slice() as Float32Array;
  const colors = new Float32Array(cp.count * 3);
  const a = col('#ff7a1c'), b = col('#ffd14a');
  for (let i = 0; i < cp.count; i++) { const c = lerpColor(a, b, (base[i * 3] + 0.95) / 1.9 * 0.6); if (Math.abs(base[i * 3 + 1]) > 0.48) c.multiplyScalar(0.8); colors[i * 3] = c.r; colors[i * 3 + 1] = c.g; colors[i * 3 + 2] = c.b; }
  cloth.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  const clothMesh = new THREE.Mesh(cloth, new THREE.MeshStandardMaterial({ vertexColors: true, side: THREE.DoubleSide, roughness: 0.75 }));
  clothMesh.position.set(1.0, 3.75, 0);
  clothMesh.castShadow = true;
  g.add(clothMesh);
  // star emblem
  const starShape = new THREE.Shape();
  for (let i = 0; i < 10; i++) { const r = i % 2 === 0 ? 0.36 : 0.15, an = Math.PI / 2 + (i * Math.PI) / 5; (i === 0 ? starShape.moveTo : starShape.lineTo).call(starShape, Math.cos(an) * r, Math.sin(an) * r); }
  starShape.closePath();
  const starGeo = new THREE.ExtrudeGeometry(starShape, { depth: 0.12, bevelEnabled: true, bevelSize: 0.03, bevelThickness: 0.03, bevelSegments: 2 });
  starGeo.translate(0, 0, -0.06);
  const starMat = new THREE.MeshStandardMaterial({ color: '#ffd24a', emissive: '#ff9a1a', emissiveIntensity: 0.65, roughness: 0.35, metalness: 0.25 });
  const star = new THREE.Mesh(starGeo, starMat);
  star.position.set(1.0, 3.75, 0.08);
  g.add(star);
  const floating = new THREE.Mesh(starGeo, starMat);
  floating.position.set(-1.8, 2.3, 0.4);
  floating.scale.setScalar(1.3);
  g.add(floating);
  const halo = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTexture('#ffd36a'), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, opacity: 0.8 }));
  halo.scale.set(3.2, 3.2, 1); halo.position.copy(floating.position);
  g.add(halo);
  // soft light pillar marking the finish (additive, camera-facing quad)
  const beam = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 9), new THREE.MeshBasicMaterial({ map: beamTexture(), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, opacity: 0.45, color: '#ffe7a0' }));
  beam.position.set(0, 4.5, -0.6);
  g.add(beam);
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
    (beam.material as THREE.MeshBasicMaterial).opacity = 0.35 + Math.sin(t * 1.7) * 0.08;
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

let _beam: THREE.Texture | null = null;
function beamTexture(): THREE.Texture {
  if (_beam) return _beam;
  const c = document.createElement('canvas'); c.width = 64; c.height = 128;
  const x = c.getContext('2d')!;
  const gh = x.createLinearGradient(0, 0, 64, 0);
  gh.addColorStop(0, 'rgba(255,255,255,0)'); gh.addColorStop(0.5, 'rgba(255,255,255,1)'); gh.addColorStop(1, 'rgba(255,255,255,0)');
  x.fillStyle = gh; x.fillRect(0, 0, 64, 128);
  x.globalCompositeOperation = 'destination-in';
  const gv = x.createLinearGradient(0, 0, 0, 128);
  gv.addColorStop(0, 'rgba(0,0,0,0)'); gv.addColorStop(0.7, 'rgba(0,0,0,0.8)'); gv.addColorStop(1, 'rgba(0,0,0,1)');
  x.fillStyle = gv; x.fillRect(0, 0, 64, 128);
  _beam = new THREE.CanvasTexture(c); _beam.colorSpace = THREE.SRGBColorSpace;
  return _beam;
}

export { pick, flower, buildRockPlatform };
export type { Decor };
