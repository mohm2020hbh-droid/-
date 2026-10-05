/**
 * MapRenderPlan — cost model of what the Visual-V2 renderer builds (phase 2). Pure, shared by the validator
 * (budgets → WARNINGs), the stats tool and the tests that compare it with the real renderer.
 *
 * The renderer batches per chunk as follows (see src/render/map/MapScene.ts):
 *   merged    static parametric geometry (platforms, walls, slopes, markers): ONE merged mesh per (chunk, material)
 *   instanced repeated decoration (scatter / `instancing`): one InstancedMesh per (mesh variant, material, LOD), SHARED by
 *             all loaded chunks (instances are added/removed as chunks stream)
 *   single    entities with behaviours (moving, rotating, gated, breakable): one mesh per part
 * so draw calls scale with the number of distinct *materials and variants*, not with the number of objects.
 */
import { BUILTIN_MESHES, PROCEDURAL_TEXTURES, textureBytes } from './MapAssets';
import { texturesOfMaterial } from './MapMaterial';
import { type ResolvedVisual, instanceCountOf } from './MapVisual';
import type { AssetRef, BackdropLayer, MaterialDef, RenderLayer } from './schema';

export interface VisualEstimate {
  /** Main-pass draw calls (worst case: every instance group visible). */
  drawCalls: number;
  /** Estimated extra draw calls of the shadow pass (casters near the sun target). */
  shadowCalls: number;
  /** Triangles with every instance at LOD0. */
  triangles: number;
  /** Extra triangles of the shadow pass (shadow-casting visuals near the sun target, at their expected LOD). */
  shadowTriangles: number;
  /** Triangles with LOD chosen by layer depth (what the camera really sees). */
  trianglesExpected: number;
  textureBytes: number;
  materials: number;
  items: number;
  instances: number;
  singles: number;
  mergedCalls: number;
  instancedCalls: number;
  byLayer: Record<RenderLayer, { items: number; triangles: number }>;
}

export const emptyVisualEstimate = (): VisualEstimate => ({
  drawCalls: 0, shadowCalls: 0, triangles: 0, shadowTriangles: 0, trianglesExpected: 0, textureBytes: 0, materials: 0, items: 0, instances: 0, singles: 0, mergedCalls: 0, instancedCalls: 0,
  byLayer: { foreground: { items: 0, triangles: 0 }, gameplay: { items: 0, triangles: 0 }, midground: { items: 0, triangles: 0 }, background: { items: 0, triangles: 0 } },
});

/** LOD level the default thresholds give a layer at its typical depth (used for the "expected" triangle count). */
const EXPECTED_LOD: Record<RenderLayer, 0 | 1 | 2> = { foreground: 0, gameplay: 0, midground: 1, background: 2 };

export type MaterialLookup = (id: string) => MaterialDef | undefined;

/** Estimate the visuals of a set of chunks (each chunk = one list). Chunks are batched independently. */
export function estimateVisuals(chunks: readonly (readonly ResolvedVisual[])[], mat: MaterialLookup, assets: ReadonlyMap<string, AssetRef>): VisualEstimate {
  const est = emptyVisualEstimate();
  const materials = new Set<string>();
  const textures = new Set<string>();
  const useMaterial = (id: string): void => {
    if (materials.has(id)) return;
    materials.add(id);
    const def = mat(id);
    if (def) for (const t of texturesOfMaterial(def)) textures.add(t);
  };
  let castGroups = 0, castTris = 0;
  // instanced pools are shared by every loaded chunk (one InstancedMesh per mesh variant × material × LOD), so they are
  // counted once for the whole window; merged static geometry is batched per chunk.
  const instanced = new Map<string, { variants: number; count: number; factor: number; cast: boolean }>();
  for (const list of chunks) {
    const merged = new Set<string>();
    for (const rv of list) {
      const n = instanceCountOf(rv);
      est.items++; est.instances += n;
      const t0 = rv.tris[0] * n, tx = rv.tris[EXPECTED_LOD[rv.layer]] * n;
      est.triangles += t0; est.trianglesExpected += tx;
      if (rv.cast) castTris += tx;
      est.byLayer[rv.layer].items++; est.byLayer[rv.layer].triangles += tx;
      for (const p of rv.parts) useMaterial(p.material);
      if (rv.mode === 'single') { est.singles++; est.drawCalls += rv.parts.length; if (rv.cast) castGroups += rv.parts.length; continue; }
      if (rv.mode === 'merged') { for (const p of rv.parts) merged.add(p.material); if (rv.cast) castGroups++; continue; }
      const info = BUILTIN_MESHES[rv.mesh];
      const variants = Math.max(1, Math.min(n, Math.round(rv.scatter?.variants ?? (typeof rv.params.variants === 'number' ? rv.params.variants : info?.repeat ? 4 : 1))));
      for (const p of rv.parts) {
        const key = `${rv.mesh}|${p.material}`;
        const g = instanced.get(key);
        if (g) { g.count += n; g.variants = Math.max(g.variants, variants); }
        else instanced.set(key, { variants, count: n, factor: rv.layer === 'midground' || rv.layer === 'background' ? 2 : 1, cast: rv.cast });
      }
    }
    est.mergedCalls += merged.size;
  }
  for (const g of instanced.values()) { const calls = Math.min(g.count, g.variants) * g.factor; est.instancedCalls += calls; if (g.cast) castGroups += Math.min(g.count, g.variants); }
  est.drawCalls += est.mergedCalls + est.instancedCalls;
  est.shadowCalls = Math.ceil(castGroups * 0.5);
  est.shadowTriangles = Math.round(castTris * 0.5);
  est.materials = materials.size;
  for (const t of textures) {
    if (t.startsWith('proc:')) { const p = PROCEDURAL_TEXTURES[t.slice(5)]; if (p) est.textureBytes += textureBytes(p.size); }
    else { const a = assets.get(t); if (a) est.textureBytes += textureBytes(Math.max(a.width ?? 0, a.height ?? 0)) * (Math.min(a.width ?? 0, a.height ?? 0) / Math.max(1, Math.max(a.width ?? 0, a.height ?? 0))); }
  }
  return est;
}

/** Cost of a theme's far-field backdrop (fixed per map, independent of chunking). */
export function estimateBackdrop(layers: readonly BackdropLayer[] | undefined): { drawCalls: number; triangles: number } {
  let drawCalls = 0, triangles = 0;
  for (const l of layers ?? []) {
    switch (l.kind) {
      case 'mountains': drawCalls += l.snow ? 2 : 1; triangles += BUILTIN_MESHES['builtin:mountain'].tris({ w: l.width * 3.6 }, 2); break;
      case 'clouds': { const c = l.count ?? 10; drawCalls += 2; triangles += c * BUILTIN_MESHES['builtin:cloud'].tris({}, 1); break; }
      case 'silhouettes': { const c = l.count ?? 10; const m = BUILTIN_MESHES[l.mesh ?? 'builtin:tree']; drawCalls += 2; triangles += c * (m ? m.tris({ ...(m.defaults), ...(l.meshParams ?? {}) }, 2) : 40); break; }
      case 'landmarks': { const c = l.count ?? 1; const m = BUILTIN_MESHES[l.mesh ?? 'builtin:island']; drawCalls += 2; triangles += c * (m ? m.tris({ ...(m.defaults), ...(l.meshParams ?? {}) }, 1) : 200); break; }
      case 'sea': drawCalls += 1; triangles += 400; break;
      case 'fog': case 'glow': drawCalls += 1; triangles += 2; break;
    }
  }
  return { drawCalls, triangles };
}
