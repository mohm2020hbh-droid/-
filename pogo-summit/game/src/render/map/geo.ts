import * as THREE from 'three';
import { mergeGeometries, mergeVertices } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

/**
 * Geometry toolbox of the Visual-V2 mesh library. Every geometry leaves `finalize()` as an INDEXED, welded mesh with exactly
 * four attributes — position, normal, uv (box-projected, in metres) and color (RGB ambient-occlusion multiplier) — so any
 * parts can be merged or instanced together and any MapMaterial (normal maps included) can be applied.
 */
export interface MeshPart { role: string; geometry: THREE.BufferGeometry }

/** Remove everything but positions, weld coincident vertices and recompute smooth normals. */
export function weld(g: THREE.BufferGeometry, tol = 1e-4): THREE.BufferGeometry {
  for (const name of Object.keys(g.attributes)) if (name !== 'position') g.deleteAttribute(name);
  const out = mergeVertices(g, tol);
  out.computeVertexNormals();
  return out;
}

export type AoFn = (x: number, y: number, z: number, nx: number, ny: number, nz: number) => number;

/** Baked sky occlusion: down-facing and ground-contact vertices are darker, tops a touch brighter. */
export const skyAo = (minY: number, height: number, strength = 1): AoFn => (_x, y, _z, _nx, ny) => {
  const sky = 0.62 + 0.38 * THREE.MathUtils.smoothstep(ny, -0.9, 0.55);
  const contact = 0.7 + 0.3 * THREE.MathUtils.smoothstep(y - minY, 0, Math.max(0.3, height * 0.18));
  const v = sky * contact;
  return 1 - (1 - v) * strength;
};

/** Set position-derived uv (box projection in metres: top/bottom → xz, sides → zy or xy) and the AO colour attribute. */
export function finalize(g: THREE.BufferGeometry, ao: AoFn | null = null): THREE.BufferGeometry {
  if (!g.getAttribute('normal')) g.computeVertexNormals();
  const pos = g.getAttribute('position') as THREE.BufferAttribute, nor = g.getAttribute('normal') as THREE.BufferAttribute;
  const n = pos.count;
  const uv = new Float32Array(n * 2), col = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) {
    const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
    const nx = nor.getX(i), ny = nor.getY(i), nz = nor.getZ(i);
    const ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
    if (ay >= ax && ay >= az) { uv[i * 2] = x; uv[i * 2 + 1] = z; }
    else if (ax >= az) { uv[i * 2] = z; uv[i * 2 + 1] = y; }
    else { uv[i * 2] = x; uv[i * 2 + 1] = y; }
    const a = ao ? ao(x, y, z, nx, ny, nz) : 1;
    col[i * 3] = a; col[i * 3 + 1] = a; col[i * 3 + 2] = a;
  }
  g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  g.setAttribute('color', new THREE.BufferAttribute(col, 3));
  g.computeBoundingSphere(); g.computeBoundingBox();
  return g;
}

/** Split an indexed geometry into two parts by a per-triangle predicate (shares the vertex buffers). */
export function split(g: THREE.BufferGeometry, pick: (cx: number, cy: number, cz: number, ny: number) => boolean): [THREE.BufferGeometry, THREE.BufferGeometry] {
  const idx = g.getIndex()!;
  const pos = g.getAttribute('position') as THREE.BufferAttribute, nor = g.getAttribute('normal') as THREE.BufferAttribute;
  const a: number[] = [], b: number[] = [];
  for (let i = 0; i < idx.count; i += 3) {
    const i0 = idx.getX(i), i1 = idx.getX(i + 1), i2 = idx.getX(i + 2);
    const cx = (pos.getX(i0) + pos.getX(i1) + pos.getX(i2)) / 3, cy = (pos.getY(i0) + pos.getY(i1) + pos.getY(i2)) / 3, cz = (pos.getZ(i0) + pos.getZ(i1) + pos.getZ(i2)) / 3;
    const ny = (nor.getY(i0) + nor.getY(i1) + nor.getY(i2)) / 3;
    (pick(cx, cy, cz, ny) ? a : b).push(i0, i1, i2);
  }
  const mk = (list: number[]): THREE.BufferGeometry => {
    const o = new THREE.BufferGeometry();
    for (const [k, v] of Object.entries(g.attributes)) o.setAttribute(k, v);
    o.setIndex(list);
    o.computeBoundingSphere(); o.computeBoundingBox();
    return o;
  };
  return [mk(a), mk(b)];
}

/** Drop vertices not referenced by the index (after `split`, so parts own compact buffers). */
export function compact(g: THREE.BufferGeometry): THREE.BufferGeometry {
  const idx = g.getIndex();
  if (!idx || idx.count === 0) return g;
  const remap = new Map<number, number>();
  const order: number[] = [];
  const out: number[] = [];
  for (let i = 0; i < idx.count; i++) {
    const v = idx.getX(i);
    let m = remap.get(v);
    if (m === undefined) { m = order.length; remap.set(v, m); order.push(v); }
    out.push(m);
  }
  const o = new THREE.BufferGeometry();
  for (const [k, attr] of Object.entries(g.attributes)) {
    const a = attr as THREE.BufferAttribute;
    const arr = new Float32Array(order.length * a.itemSize);
    order.forEach((src, dst) => { for (let c = 0; c < a.itemSize; c++) arr[dst * a.itemSize + c] = a.array[src * a.itemSize + c]; });
    o.setAttribute(k, new THREE.BufferAttribute(arr, a.itemSize));
  }
  o.setIndex(out);
  o.computeBoundingSphere(); o.computeBoundingBox();
  return o;
}

export function merge(list: THREE.BufferGeometry[]): THREE.BufferGeometry {
  if (list.length === 1) return list[0];
  const out = mergeGeometries(list, false);
  if (!out) throw new Error('mergeGeometries failed (attribute mismatch)');
  out.computeBoundingSphere(); out.computeBoundingBox();
  return out;
}

const _m = new THREE.Matrix4(), _q = new THREE.Quaternion(), _e = new THREE.Euler(), _p = new THREE.Vector3(), _s = new THREE.Vector3();
/** In-place translate / rotate (radians, XYZ) / scale. */
export function place(g: THREE.BufferGeometry, px = 0, py = 0, pz = 0, rx = 0, ry = 0, rz = 0, sx = 1, sy = sx, sz = sx): THREE.BufferGeometry {
  _e.set(rx, ry, rz); _q.setFromEuler(_e); _p.set(px, py, pz); _s.set(sx, sy, sz);
  g.applyMatrix4(_m.compose(_p, _q, _s));
  return g;
}

export const triCount = (g: THREE.BufferGeometry): number => (g.getIndex() ? g.getIndex()!.count : (g.getAttribute('position') as THREE.BufferAttribute).count) / 3;

/** Lumpy sphere: icosahedron with radial noise (rocks, bushes, cloud puffs). Unit radius, welded, smooth normals. */
export function lumpy(detail: number, amp: number, noise: (x: number, y: number, z: number) => number, sx = 1, sy = 1, sz = 1): THREE.BufferGeometry {
  const g = new THREE.IcosahedronGeometry(1, detail);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const k = 1 + (noise(x * 1.7, y * 1.7, z * 1.7) - 0.5) * 2 * amp;
    p.setXYZ(i, x * k * sx, y * k * sy, z * k * sz);
  }
  return weld(g);
}
