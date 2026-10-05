/**
 * MapWarm — which (material × geometry kind) combinations a map will draw (Visual V2 · performance).
 *
 * A WebGL program is compiled the first time a material is drawn with a given feature set (map, normal map, instancing,
 * per-instance colour, depth dither, transparency). On a phone each compile is a visible hitch when it happens in the
 * middle of a jump, so the renderer compiles every combination a map can produce while the level loads. This module is the
 * pure list of those combinations; the renderer turns each into a tiny hidden mesh and calls `renderer.compile`.
 */
import type { MapDocument, MaterialDef, ThemeDef } from './schema';
import type { PrefabRegistry } from './MapPrefab';
import { resolveEntity } from './MapPrefab';
import { type Curve, buildCurve } from './MapBehavior';
import { buildInstance } from './MapEntity';
import { resolveVisual } from './MapVisual';

export type WarmKind = 'mesh' | 'instanced' | 'instanced-tinted';
export interface MaterialUse { material: string; kind: WarmKind; foreground: boolean; ghost: boolean }

const keyOf = (u: MaterialUse): string => `${u.material}|${u.kind}|${u.foreground ? 'f' : ''}${u.ghost ? 'g' : ''}`;

/** Entities scanned at most; bigger maps use the theme-level fallback (their chunks are not all in memory anyway). */
export const WARM_SCAN_LIMIT = 6000;

/** Combinations used by the document's entities (exact), or null when the document has no entity list to scan. */
export function collectMaterialUse(doc: MapDocument, registry: PrefabRegistry, theme: ThemeDef, assets: ReadonlyMap<string, { tris?: number }>, paths?: ReadonlyMap<string, Curve>): MaterialUse[] | null {
  if (!doc.entities.length || doc.entities.length > WARM_SCAN_LIMIT) return null;
  const pm = paths ?? new Map(doc.paths.map(p => [p.id, buildCurve(p)]));
  const out = new Map<string, MaterialUse>();
  const add = (u: MaterialUse): void => { out.set(keyOf(u), u); };
  for (const e of doc.entities) {
    const inst = buildInstance(resolveEntity(e, registry).entity, pm);
    const rv = resolveVisual(inst, { theme, assets });
    if (!rv) continue;
    const foreground = rv.layer === 'foreground';
    const tinted = !!rv.scatter && (rv.scatter.tintVariance ?? 0.12) > 1e-3;
    for (const p of rv.parts) {
      if (rv.mode === 'instanced') add({ material: p.material, kind: tinted ? 'instanced-tinted' : 'instanced', foreground, ghost: false });
      else {
        add({ material: p.material, kind: 'mesh', foreground, ghost: false });
        if (rv.mode === 'single' && (inst.gates.length > 0 || !!inst.breakable || !!rv.visibleWhen)) add({ material: p.material, kind: 'mesh', foreground, ghost: true });
      }
    }
  }
  return [...out.values()];
}

/** Fallback: every material of the theme / document in the plain and instanced forms. */
export function themeMaterialUse(defs: Readonly<Record<string, MaterialDef>>): MaterialUse[] {
  const out: MaterialUse[] = [];
  for (const material of Object.keys(defs)) {
    out.push({ material, kind: 'mesh', foreground: false, ghost: false }, { material, kind: 'instanced', foreground: false, ghost: false }, { material, kind: 'instanced-tinted', foreground: false, ghost: false });
  }
  return out;
}
