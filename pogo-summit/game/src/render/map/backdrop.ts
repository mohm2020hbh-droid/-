import * as THREE from 'three';
import { BUILTIN_MESHES, type MeshParams, isBuiltinMesh } from '../../map/MapAssets';
import { lerpColor } from '../../map/MapColor';
import { hashString, rng32 } from '../../map/MapScatter';
import type { BackdropLayer, Rect } from '../../map/schema';
import { mulberry32 } from '../noise';
import { generateMesh } from './meshes';
import { triCount, type MeshPart } from './geo';

/**
 * Backdrop — the far field of a map (Visual V2 · phase 6): layered mountain ranges, cloud banks, distant silhouettes and
 * landmarks, haze bands, a sea, glows. Every layer sits at a depth behind the gameplay plane and follows the camera by its
 * own factor (parallax), so the world reads as deep and extended instead of a flat sky. Layers are cheap: unlit baked-colour
 * meshes (the scene fog supplies aerial perspective) and instanced clouds.
 */
export interface BackdropOptions {
  bounds: Rect;
  sunDir: THREE.Vector3;
  fogColor: string;
  /** Standard material for the cloud slot (tinted per layer). */
  cloudMaterial: (tint: string) => THREE.Material;
  /** 0..1 quality density of instanced decoration. */
  density: number;
}

interface LayerView { id: string; group: THREE.Group; follow: number; baseY: number; cx: number; glued: boolean; tick?: (dt: number, t: number) => void }

function gradientTexture(w: number, h: number, fn: (u: number, v: number) => number): THREE.DataTexture {
  const d = new Uint8Array(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const a = Math.round(255 * Math.max(0, Math.min(1, fn(x / (w - 1), y / (h - 1))))); const i = (y * w + x) * 4; d[i] = d[i + 1] = d[i + 2] = 255; d[i + 3] = a; }
  const t = new THREE.DataTexture(d, w, h, THREE.RGBAFormat);
  t.magFilter = t.minFilter = THREE.LinearFilter; t.needsUpdate = true; t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

/** Bake directional shading + a base colour into the vertex colours of an unlit mesh part. */
function bake(part: MeshPart, color: string, sun: THREE.Vector3, haze: string, hazeAmt: number): THREE.BufferGeometry {
  const g = part.geometry.clone();
  const base = new THREE.Color(lerpColor(color, haze, hazeAmt));
  const nor = g.getAttribute('normal') as THREE.BufferAttribute, col = g.getAttribute('color') as THREE.BufferAttribute;
  const c = new THREE.Color();
  for (let i = 0; i < col.count; i++) {
    const lit = Math.max(0, nor.getX(i) * sun.x + nor.getY(i) * sun.y + nor.getZ(i) * sun.z);
    const k = (0.6 + 0.4 * Math.min(1, lit * 1.25)) * col.getX(i);
    c.copy(base).multiplyScalar(k);
    col.setXYZ(i, c.r, c.g, c.b);
  }
  col.needsUpdate = true;
  return g;
}

export class Backdrop {
  readonly root = new THREE.Group();
  private readonly layers: LayerView[] = [];
  private readonly disposables: { dispose(): void }[] = [];
  drawCalls = 0;
  triangles = 0;

  constructor(layers: readonly BackdropLayer[], private readonly o: BackdropOptions) {
    this.root.name = 'backdrop';
    for (const l of layers) this.addLayer(l);
  }

  private track<T extends { dispose(): void }>(x: T): T { this.disposables.push(x); return x; }

  private addLayer(l: BackdropLayer): void {
    const group = new THREE.Group();
    group.name = `backdrop:${l.id}`;
    const { bounds } = this.o;
    const cx = (bounds.minX + bounds.maxX) / 2;
    const view: LayerView = { id: l.id, group, follow: l.follow, baseY: l.y, cx, glued: l.kind === 'glow' };
    const seed = (l.seed ?? hashString(l.id)) >>> 0;
    const haze = l.haze ?? 0.3;
    switch (l.kind) {
      case 'mountains': {
        const span = bounds.maxX - bounds.minX + l.width * 3.6;
        const parts = generateMesh('builtin:mountain', { w: span, h: l.height, depth: l.width * 0.9, seed, profile: 'ridge', snow: l.snow !== false }, 0, 0);
        for (const p of parts) {
          const col = p.role === 'snow' ? '#f3f7ff' : l.color;
          const geo = this.track(bake(p, col, this.o.sunDir, this.o.fogColor, p.role === 'snow' ? haze * 0.8 : haze));
          const m = new THREE.Mesh(geo, this.track(new THREE.MeshBasicMaterial({ vertexColors: true })));
          m.position.set(cx, l.y, -l.z); m.frustumCulled = false; group.add(m); this.drawCalls++; this.triangles += triCount(geo);
        }
        break;
      }
      case 'clouds': {
        const rnd = mulberry32(seed), variants = 3;
        const per = Math.max(1, Math.round((l.count ?? 10) * this.o.density));
        const mat = this.o.cloudMaterial(l.color);
        const sets: { v: number; xs: number[]; ys: number[]; zs: number[]; sc: number[]; sp: number[] }[] = Array.from({ length: variants }, (_, v) => ({ v, xs: [], ys: [], zs: [], sc: [], sp: [] }));
        for (let i = 0; i < per; i++) {
          const s = sets[i % variants];
          s.xs.push(bounds.minX - l.width * 0.3 + rnd() * (bounds.maxX - bounds.minX + l.width * 0.8)); s.ys.push(l.y + (rnd() - 0.5) * l.height); s.zs.push(-l.z + (rnd() - 0.5) * 30);
          s.sc.push(1.4 + rnd() * 1.6); s.sp.push(0.15 + rnd() * 0.4);
        }
        const meshes: { im: THREE.InstancedMesh; s: (typeof sets)[number] }[] = [];
        for (const s of sets) {
          if (!s.xs.length) continue;
          const part = generateMesh('builtin:cloud', { w: 16, h: 4.2, seed }, 1, s.v)[0];
          const geo = this.track(part.geometry);
          const im = new THREE.InstancedMesh(geo, mat, s.xs.length);
          im.instanceMatrix.setUsage(THREE.DynamicDrawUsage); im.frustumCulled = false;
          group.add(im); meshes.push({ im, s }); this.drawCalls++; this.triangles += triCount(geo) * s.xs.length;
        }
        const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), pos = new THREE.Vector3(), sc = new THREE.Vector3();
        const span = bounds.maxX - bounds.minX + l.width * 0.8;
        const write = (): void => {
          for (const { im, s } of meshes) { for (let i = 0; i < s.xs.length; i++) { pos.set(s.xs[i], s.ys[i], s.zs[i]); sc.set(s.sc[i], s.sc[i] * 0.9, s.sc[i]); im.setMatrixAt(i, m4.compose(pos, q, sc)); } im.instanceMatrix.needsUpdate = true; }
        };
        write();
        view.tick = dt => {
          for (const { s } of meshes) for (let i = 0; i < s.xs.length; i++) { s.xs[i] += s.sp[i] * dt; if (s.xs[i] > bounds.minX - l.width * 0.3 + span) s.xs[i] -= span; }
          write();
        };
        break;
      }
      case 'silhouettes':
      case 'landmarks': {
        const meshId = l.mesh && isBuiltinMesh(l.mesh) ? l.mesh : l.kind === 'silhouettes' ? 'builtin:tree' : 'builtin:island';
        const rnd = rng32(seed), variants = Math.min(3, BUILTIN_MESHES[meshId].repeat ? 3 : 1);
        const count = Math.max(1, Math.round((l.count ?? 8) * (l.kind === 'silhouettes' ? this.o.density : 1)));
        const params: MeshParams = { ...(l.meshParams ?? {}), seed };
        const span = bounds.maxX - bounds.minX + l.width;
        const mat = this.track(new THREE.MeshBasicMaterial({ vertexColors: true }));
        const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), pos = new THREE.Vector3(), sc = new THREE.Vector3(), e = new THREE.Euler();
        for (let v = 0; v < variants; v++) {
          const n = Math.floor(count / variants) + (v < count % variants ? 1 : 0);
          if (!n) continue;
          for (const part of generateMesh(meshId, params, l.kind === 'silhouettes' ? 2 : 1, v)) {
            const role = part.role;
            const col = role === 'glow' ? (l.color2 ?? '#ffffff') : role === 'snow' || role === 'cap' ? lerpColor(l.color, '#ffffff', 0.35) : l.color;
            const geo = this.track(bake(part, col, this.o.sunDir, this.o.fogColor, haze));
            const im = new THREE.InstancedMesh(geo, mat, n);
            for (let i = 0; i < n; i++) {
              const s = (l.kind === 'silhouettes' ? 0.8 + rnd() * 0.6 : 0.85 + rnd() * 0.4);
              pos.set(this.o.bounds.minX - l.width * 0.5 + (i + rnd() * 0.8 + v * 0.3) / n * span, l.y + (l.kind === 'landmarks' ? rnd() * l.height : 0), -l.z + (rnd() - 0.5) * 16);
              q.setFromEuler(e.set(0, (rnd() - 0.5) * 0.6, 0)); sc.set(s, s, s);
              im.setMatrixAt(i, m4.compose(pos, q, sc));
            }
            im.instanceMatrix.needsUpdate = true; im.frustumCulled = false;
            group.add(im); this.drawCalls++; this.triangles += triCount(geo) * n;
          }
        }
        break;
      }
      case 'sea': {
        const tex = this.track(gradientTexture(4, 32, (_u, v) => 1 - v * v));
        const geo = this.track(new THREE.PlaneGeometry(l.width * 2.4, l.height * 2.2));
        const mat = this.track(new THREE.MeshBasicMaterial({ color: l.color, map: tex, transparent: true, opacity: l.opacity ?? 0.95, depthWrite: false }));
        const m = new THREE.Mesh(geo, mat); m.position.set(cx, l.y - l.height * 0.3, -l.z); m.frustumCulled = false; m.renderOrder = -20;
        group.add(m); this.drawCalls++; this.triangles += 2;
        view.tick = (_dt, t) => { mat.opacity = (l.opacity ?? 0.95) * (0.9 + 0.1 * Math.sin(t * 0.9)); };
        break;
      }
      case 'fog': {
        const tex = this.track(gradientTexture(4, 32, (_u, v) => Math.sin(Math.PI * v) ** 1.4));
        const geo = this.track(new THREE.PlaneGeometry(l.width, l.height));
        const mat = this.track(new THREE.MeshBasicMaterial({ color: l.color, map: tex, transparent: true, opacity: l.opacity ?? 0.4, depthWrite: false, fog: false }));
        const m = new THREE.Mesh(geo, mat); m.position.set(cx, l.y + l.height / 2, -l.z); m.frustumCulled = false; m.renderOrder = -15;
        group.add(m); this.drawCalls++; this.triangles += 2;
        break;
      }
      case 'glow': {
        const tex = this.track(gradientTexture(64, 64, (u, v) => Math.max(0, 1 - Math.hypot(u - 0.5, v - 0.5) * 2) ** 2));
        const mat = this.track(new THREE.SpriteMaterial({ color: l.color, map: tex, transparent: true, opacity: l.opacity ?? 0.5, depthWrite: false, blending: THREE.AdditiveBlending, fog: false }));
        const sp = new THREE.Sprite(mat); sp.scale.set(l.width, l.height, 1); sp.position.set(0, l.y, -l.z); sp.frustumCulled = false; sp.renderOrder = -10;
        group.add(sp); this.drawCalls++; this.triangles += 2;
        break;
      }
    }
    this.layers.push(view);
    this.root.add(group);
  }

  /** Parallax: layers follow the camera focus by their factor; glued layers (glows) keep their screen position. */
  update(dt: number, time: number, focus: { x: number; y: number }): void {
    for (const l of this.layers) {
      if (l.glued) l.group.position.set(focus.x, focus.y * l.follow, 0);
      else l.group.position.set((focus.x - l.cx) * l.follow, focus.y * l.follow, 0);
      l.tick?.(dt, time);
    }
  }

  layerIds(): string[] { return this.layers.map(l => l.id); }
  layerGroup(id: string): THREE.Group | undefined { return this.layers.find(l => l.id === id)?.group; }

  dispose(): void { for (const d of this.disposables) d.dispose(); this.disposables.length = 0; this.layers.length = 0; this.root.clear(); }
}
