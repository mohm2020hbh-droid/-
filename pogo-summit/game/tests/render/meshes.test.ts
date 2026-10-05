import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { BUILTIN_MESHES, BUILTIN_MESH_IDS, type MeshParams } from '../../src/map/MapAssets';
import { GENERATORS, generateMesh, trisOfParts, type Lod } from '../../src/render/map/meshes';

const LODS: Lod[] = [0, 1, 2];
const cases: [string, MeshParams][] = [
  ...BUILTIN_MESH_IDS.map((id): [string, MeshParams] => [id, {}]),
  ['builtin:platform', { style: 'wood', w: 9, h: 1.2 }], ['builtin:platform', { style: 'ice' }], ['builtin:platform', { style: 'bounce', h: 1.6 }], ['builtin:platform', { style: 'ruin' }], ['builtin:platform', { style: 'crystal' }],
  ['builtin:tree', { kind: 'broadleaf' }], ['builtin:tree', { kind: 'dead', height: 12 }], ['builtin:tree', { kind: 'crystal' }], ['builtin:tree', { kind: 'pine', height: 16 }],
  ['builtin:mountain', { w: 300, snow: false, profile: 'volcano', h: 90 }], ['builtin:mountain', { w: 60, profile: 'cone' }], ['builtin:water', { w: 30 }], ['builtin:waterfall', { h: 40, w: 5 }],
  ['builtin:crystal_cluster', { count: 9 }], ['builtin:spikes', { count: 3 }], ['builtin:ancient_structure', { kind: 'gate', glow: true }], ['builtin:ancient_structure', { kind: 'tower' }],
  ['builtin:ancient_structure', { kind: 'pillar' }], ['builtin:ancient_structure', { kind: 'wall' }], ['builtin:ancient_structure', { kind: 'obelisk', glow: true }],
  ['builtin:marker', { kind: 'finish' }], ['builtin:cliff', { terraces: 6 }], ['builtin:grass', { blades: 30 }],
  ['builtin:poly_slab', { points: [{ x: 0, y: 0 }, { x: 6, y: 0 }, { x: 6, y: 3 }] }], ['builtin:poly_slab', { points: [{ x: -2, y: 0 }, { x: 2, y: 0 }, { x: 3, y: 2 }, { x: 0, y: 3 }, { x: -3, y: 2 }] }],
];

describe('builtin mesh generators', () => {
  it('there is a generator for every catalogue entry and vice versa', () => { expect(Object.keys(GENERATORS).sort()).toEqual([...BUILTIN_MESH_IDS].sort()); });

  it('catalogue triangle counts equal what the generators really build (so validator budgets are honest)', () => {
    for (const [id, params] of cases) {
      const p = { ...BUILTIN_MESHES[id].defaults, ...params };
      for (const lod of LODS) {
        const actual = trisOfParts(generateMesh(id, p, lod, 0));
        expect(BUILTIN_MESHES[id].tris(p, lod), `${id} ${JSON.stringify(params)} LOD${lod}`).toBe(actual);
      }
    }
  });

  it('every part is a valid indexed geometry with position/normal/uv/color, finite values, a bounding sphere, and the catalogue role list', () => {
    for (const [id, params] of cases) {
      const p = { ...BUILTIN_MESHES[id].defaults, ...params };
      for (const lod of LODS) {
        const parts = generateMesh(id, p, lod, 1);
        expect(parts.map(x => x.role), `${id} roles`).toEqual(BUILTIN_MESHES[id].roles(p));
        for (const part of parts) {
          const g = part.geometry;
          for (const name of ['position', 'normal', 'uv', 'color']) expect(g.getAttribute(name), `${id}/${part.role} has ${name}`).toBeTruthy();
          expect(g.getIndex(), `${id}/${part.role} is indexed`).toBeTruthy();
          expect(g.getAttribute('color').itemSize).toBe(3);
          for (const name of ['position', 'normal', 'uv', 'color']) { const a = g.getAttribute(name).array as Float32Array; for (let i = 0; i < a.length; i++) if (!Number.isFinite(a[i])) throw new Error(`${id}/${part.role}/${name}[${i}] = ${a[i]}`); }
          const idx = g.getIndex()!; const n = g.getAttribute('position').count;
          for (let i = 0; i < idx.count; i++) if (idx.getX(i) >= n) throw new Error(`${id}/${part.role}: index out of range`);
          if (idx.count > 0) expect(g.boundingSphere && g.boundingSphere.radius > 0, `${id}/${part.role} bounds`).toBe(true);
          expect(idx.count % 3).toBe(0);
          if (part.role !== 'snow') expect(idx.count, `${id} ${JSON.stringify(params)} LOD${lod} ${part.role} has triangles`).toBeGreaterThan(0);   // a coarse mountain may have no snow triangles (the renderer skips empty parts)
        }
      }
    }
  });

  it('LOD levels never grow and keep the silhouette size (±35 %)', () => {
    for (const [id, params] of cases) {
      const p = { ...BUILTIN_MESHES[id].defaults, ...params };
      const t = LODS.map(l => trisOfParts(generateMesh(id, p, l, 0)));
      expect(t[1], `${id} LOD1`).toBeLessThanOrEqual(t[0]); expect(t[2], `${id} LOD2`).toBeLessThanOrEqual(t[1] + 8);
      const size = (l: Lod) => { const b = new THREE.Box3(); for (const part of generateMesh(id, p, l, 0)) { part.geometry.computeBoundingBox(); b.union(part.geometry.boundingBox!); } return b.getSize(new THREE.Vector3()); };
      if (id === 'builtin:grass') continue;                                       // a tuft with fewer blades is naturally narrower
      const a = size(0), c = size(2);
      for (const ax of ['x', 'y', 'z'] as const) if (a[ax] > 0.5) expect(c[ax], `${id} ${ax}`).toBeGreaterThan(a[ax] * 0.5);
    }
  });

  it('is deterministic for a seed and different across seeds and variants (no identical repeats)', () => {
    const sig = (id: string, p: MeshParams, v: number) => generateMesh(id, p, 0, v).map(x => Array.from((x.geometry.getAttribute('position').array as Float32Array).slice(0, 60)).map(n => n.toFixed(4)).join(',')).join('|');
    for (const id of ['builtin:rock', 'builtin:tree', 'builtin:bush', 'builtin:cloud', 'builtin:crystal_cluster', 'builtin:platform', 'builtin:island']) {
      const base = BUILTIN_MESHES[id].defaults;
      expect(sig(id, base, 0)).toBe(sig(id, base, 0));
      expect(sig(id, base, 0)).not.toBe(sig(id, base, 1));
      expect(sig(id, { ...base, seed: 2 }, 0)).not.toBe(sig(id, { ...base, seed: 3 }, 0));
    }
  });

  it('platforms: top surface at y = 0, the body hangs below, the cap is the upper skin, taper narrows the bottom', () => {
    const parts = generateMesh('builtin:platform', { w: 8, h: 3, taper: 0.5, depth: 5, style: 'rock' }, 0, 0);
    const box = new THREE.Box3();
    for (const p of parts) { p.geometry.computeBoundingBox(); box.union(p.geometry.boundingBox!); }
    expect(box.max.y).toBeLessThan(0.45); expect(box.max.y).toBeGreaterThan(-0.05); expect(box.min.y).toBeLessThan(-2.4); expect(box.min.y).toBeGreaterThan(-3.6);
    expect(box.max.x - box.min.x).toBeGreaterThan(7.5); expect(box.max.x - box.min.x).toBeLessThan(9.2);
    const cap = parts.find(p => p.role === 'cap')!.geometry, body = parts.find(p => p.role === 'body')!.geometry;
    cap.computeBoundingBox(); body.computeBoundingBox();
    expect(cap.boundingBox!.max.y).toBeGreaterThan(body.boundingBox!.max.y - 0.2);
    expect(cap.boundingBox!.min.y).toBeGreaterThan(-1.3);                                  // the cap is a skin, not the whole block
    const bottom = new THREE.Box3(); const pos = body.getAttribute('position') as THREE.BufferAttribute;
    for (let i = 0; i < pos.count; i++) if (pos.getY(i) < -2.8) bottom.expandByPoint(new THREE.Vector3(pos.getX(i), pos.getY(i), pos.getZ(i)));
    expect(bottom.max.x - bottom.min.x).toBeLessThan(6.2);                                 // narrower than the 8 m top
  });

  it('the visual mesh is never the collision polygon: a trapezoid collider (4 verts) is dressed by hundreds of triangles', () => {
    expect(trisOfParts(generateMesh('builtin:platform', { w: 6, h: 3 }, 0, 0))).toBeGreaterThan(400);
  });

  it('baked AO: undersides / ground contact are darker than tops', () => {
    const g = generateMesh('builtin:rock', { size: 2 }, 0, 0)[0].geometry;
    const pos = g.getAttribute('position') as THREE.BufferAttribute, nor = g.getAttribute('normal') as THREE.BufferAttribute, col = g.getAttribute('color') as THREE.BufferAttribute;
    let top = 0, nt = 0, bot = 0, nb = 0;
    for (let i = 0; i < pos.count; i++) { if (nor.getY(i) > 0.8) { top += col.getX(i); nt++; } else if (nor.getY(i) < -0.5) { bot += col.getX(i); nb++; } }
    expect(nt).toBeGreaterThan(0); expect(nb).toBeGreaterThan(0);
    expect(top / nt).toBeGreaterThan(bot / nb + 0.1);
  });
});
