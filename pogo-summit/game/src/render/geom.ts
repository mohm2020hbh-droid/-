import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

/** Small procedural-geometry toolbox. Everything is non-indexed + vertex-coloured so a whole prop merges to one draw call. */
export const col = (hex: string | number): THREE.Color => new THREE.Color(hex);

const _m = new THREE.Matrix4();
const _q = new THREE.Quaternion();
const _e = new THREE.Euler();
const _p = new THREE.Vector3();
const _s = new THREE.Vector3();

/** Apply translate/rotate/scale to a geometry (in place) and return it. */
export function xf(g: THREE.BufferGeometry, px = 0, py = 0, pz = 0, rx = 0, ry = 0, rz = 0, sx = 1, sy = sx, sz = sx): THREE.BufferGeometry {
  _e.set(rx, ry, rz);
  _q.setFromEuler(_e);
  _p.set(px, py, pz);
  _s.set(sx, sy, sz);
  _m.compose(_p, _q, _s);
  g.applyMatrix4(_m);
  return g;
}

export type ColorFn = (x: number, y: number, z: number, nx: number, ny: number, nz: number, face: number) => THREE.Color;

/** Convert to non-indexed, recompute (flat) normals and paint a colour per face. */
export function paint(g: THREE.BufferGeometry, fn: ColorFn, smoothNormals = false): THREE.BufferGeometry {
  const geo = g;
  if (!smoothNormals) {
    if (g.index) { // flat shading needs unshared vertices — convert IN PLACE so callers that ignore the return value still work
      const ni = g.toNonIndexed();
      g.setIndex(null);
      g.setAttribute('position', ni.getAttribute('position'));
      g.setAttribute('normal', ni.getAttribute('normal'));
      g.deleteAttribute('uv');
      g.clearGroups();
      ni.dispose();
    }
    geo.computeVertexNormals();
  }
  const pos = geo.getAttribute('position') as THREE.BufferAttribute;
  const nor = geo.getAttribute('normal') as THREE.BufferAttribute;
  const colors = new Float32Array(pos.count * 3);
  if (smoothNormals) {
    for (let i = 0; i < pos.count; i++) {
      const c = fn(pos.getX(i), pos.getY(i), pos.getZ(i), nor.getX(i), nor.getY(i), nor.getZ(i), i);
      colors[i * 3] = c.r; colors[i * 3 + 1] = c.g; colors[i * 3 + 2] = c.b;
    }
  } else {
    for (let i = 0; i < pos.count; i += 3) {
      const cx = (pos.getX(i) + pos.getX(i + 1) + pos.getX(i + 2)) / 3;
      const cy = (pos.getY(i) + pos.getY(i + 1) + pos.getY(i + 2)) / 3;
      const cz = (pos.getZ(i) + pos.getZ(i + 1) + pos.getZ(i + 2)) / 3;
      const c = fn(cx, cy, cz, nor.getX(i), nor.getY(i), nor.getZ(i), i / 3);
      for (let k = 0; k < 3; k++) { colors[(i + k) * 3] = c.r; colors[(i + k) * 3 + 1] = c.g; colors[(i + k) * 3 + 2] = c.b; }
    }
  }
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  return geo;
}

export function solid(g: THREE.BufferGeometry, color: THREE.Color | string | number, smoothNormals = false): THREE.BufferGeometry {
  const c = color instanceof THREE.Color ? color : new THREE.Color(color);
  return paint(g, () => c, smoothNormals);
}

export function merge(list: THREE.BufferGeometry[]): THREE.BufferGeometry {
  const clean = list.map(g => {
    const n = g.index ? g.toNonIndexed() : g;
    for (const name of Object.keys(n.attributes)) if (!['position', 'normal', 'color'].includes(name)) n.deleteAttribute(name);
    if (!n.getAttribute('color')) solid(n, '#ffffff');
    return n;
  });
  const out = mergeGeometries(clean, false);
  if (!out) throw new Error('mergeGeometries failed');
  return out;
}

/** Lumpy sphere: icosahedron with per-vertex radial noise (foliage, bushes, clouds, boulders). */
export function blobGeometry(radius: number, detail: number, amp: number, noise: (x: number, y: number, z: number) => number): THREE.BufferGeometry {
  const g = new THREE.IcosahedronGeometry(radius, detail);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const l = Math.hypot(x, y, z) || 1;
    const k = 1 + (noise(x / radius * 1.7, y / radius * 1.7, z / radius * 1.7) - 0.5) * 2 * amp;
    p.setXYZ(i, (x / l) * radius * k, (y / l) * radius * k, (z / l) * radius * k);
  }
  g.computeVertexNormals();
  return g;
}

export const lerpColor = (a: THREE.Color, b: THREE.Color, t: number, out = new THREE.Color()): THREE.Color => out.copy(a).lerp(b, Math.max(0, Math.min(1, t)));

export function mesh(g: THREE.BufferGeometry, m: THREE.Material, castShadow = false, receiveShadow = true): THREE.Mesh {
  const o = new THREE.Mesh(g, m);
  o.castShadow = castShadow;
  o.receiveShadow = receiveShadow;
  return o;
}

export function dispose(obj: THREE.Object3D): void {
  obj.traverse(o => {
    const m = o as THREE.Mesh;
    if (m.geometry) m.geometry.dispose();
  });
}
