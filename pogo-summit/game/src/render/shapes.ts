import * as THREE from 'three';
import { mergeVertices } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

/**
 * Organic shape kit (visual upgrade): rounded, welded, densely-enough tessellated blocks that can be displaced along their
 * normals and shaded SMOOTH — replaces raw BoxGeometry so platforms and rocks read as sculpted forms with bevelled edges.
 */

/** Coordinates along one axis: `cornerSeg` samples inside each rounded band, interior spaced ≈ `step`. */
function axisSamples(half: number, r: number, step: number, cornerSeg: number): number[] {
  const out: number[] = [];
  const inner = Math.max(0, half - r);
  for (let i = 0; i < cornerSeg; i++) out.push(-half + (r * i) / cornerSeg);
  const n = Math.max(1, Math.round((2 * inner) / step));
  for (let i = 0; i <= n; i++) out.push(-inner + (2 * inner * i) / n);
  for (let i = cornerSeg - 1; i >= 0; i--) out.push(half - (r * i) / cornerSeg);
  return out;
}

export interface RoundedBlock {
  geometry: THREE.BufferGeometry;
  /** per-vertex outward "box normal" (direction of the rounding), useful for displacement. */
  dir: Float32Array;
}

/**
 * Rounded box (w × h × d, edge radius r) centred at the origin. Welded (indexed) so smooth normals have no seams.
 * `step` = interior tessellation (m), `cornerSeg` = segments across each bevel.
 */
export function roundedBlock(w: number, h: number, d: number, r: number, step = 0.6, cornerSeg = 3): RoundedBlock {
  const hx = w / 2, hy = h / 2, hz = d / 2;
  r = Math.min(r, hx * 0.98, hy * 0.98, hz * 0.98);
  const xs = axisSamples(hx, r, step, cornerSeg), ys = axisSamples(hy, r, step, cornerSeg), zs = axisSamples(hz, r, step, cornerSeg);
  const pos: number[] = [];
  const idx: number[] = [];
  // 6 faces: (axis u, axis v, fixed axis, sign)
  const face = (us: number[], vs: number[], make: (u: number, v: number) => [number, number, number], flip: boolean) => {
    const base = pos.length / 3;
    for (let j = 0; j < vs.length; j++) for (let i = 0; i < us.length; i++) pos.push(...make(us[i], vs[j]));
    const W = us.length;
    for (let j = 0; j < vs.length - 1; j++) for (let i = 0; i < W - 1; i++) {
      const a = base + j * W + i, b = a + 1, c = a + W, e = c + 1;
      if (flip) idx.push(a, c, b, b, c, e); else idx.push(a, b, c, b, e, c);
    }
  };
  face(xs, ys, (u, v) => [u, v, hz], false);   // +z
  face(xs, ys, (u, v) => [u, v, -hz], true);   // −z
  face(zs, ys, (u, v) => [hx, v, u], true);    // +x
  face(zs, ys, (u, v) => [-hx, v, u], false);  // −x
  face(xs, zs, (u, v) => [u, hy, v], true);    // +y
  face(xs, zs, (u, v) => [u, -hy, v], false);  // −y
  let g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setIndex(idx);
  g = mergeVertices(g, 1e-4);
  // round: project every vertex onto the rounded box
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  const dir = new Float32Array(p.count * 3);
  const ix = hx - r, iy = hy - r, iz = hz - r;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const cx = Math.max(-ix, Math.min(ix, x)), cy = Math.max(-iy, Math.min(iy, y)), cz = Math.max(-iz, Math.min(iz, z));
    let dx = x - cx, dy = y - cy, dz = z - cz;
    const l = Math.hypot(dx, dy, dz) || 1;
    dx /= l; dy /= l; dz /= l;
    p.setXYZ(i, cx + dx * r, cy + dy * r, cz + dz * r);
    dir[i * 3] = dx; dir[i * 3 + 1] = dy; dir[i * 3 + 2] = dz;
  }
  g.computeVertexNormals();
  return { geometry: g, dir };
}

/** Move every vertex by `fn(x,y,z,dx,dy,dz)` (returns the new position into `out`), then recompute smooth normals. */
export function deform(b: RoundedBlock, fn: (x: number, y: number, z: number, dx: number, dy: number, dz: number, out: THREE.Vector3) => void): THREE.BufferGeometry {
  const p = b.geometry.getAttribute('position') as THREE.BufferAttribute;
  const v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) {
    v.set(p.getX(i), p.getY(i), p.getZ(i));
    fn(v.x, v.y, v.z, b.dir[i * 3], b.dir[i * 3 + 1], b.dir[i * 3 + 2], v);
    p.setXYZ(i, v.x, v.y, v.z);
  }
  p.needsUpdate = true;
  b.geometry.computeVertexNormals();
  return b.geometry;
}

/** Smooth lumpy blob (welded icosphere) — boulders, bushes, canopy clumps. */
export function smoothBlob(radius: number, detail: number, amp: number, noise: (x: number, y: number, z: number) => number): THREE.BufferGeometry {
  let g: THREE.BufferGeometry = new THREE.IcosahedronGeometry(radius, detail);
  g.deleteAttribute('normal'); g.deleteAttribute('uv');
  g = mergeVertices(g, 1e-4);
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const l = Math.hypot(x, y, z) || 1;
    const k = 1 + (noise(x / radius * 1.6, y / radius * 1.6, z / radius * 1.6) - 0.5) * 2 * amp;
    p.setXYZ(i, (x / l) * radius * k, (y / l) * radius * k, (z / l) * radius * k);
  }
  g.computeVertexNormals();
  return g;
}

/** Smoothstep helper. */
export const sstep = (a: number, b: number, x: number): number => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
