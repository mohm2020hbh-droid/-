/**
 * MapPrefab — prefab registry and entity resolution (SPEC §6.3, §7).
 *
 * resolve(entity) = merge( prefab chain template ← entity fields ), with the template expanded over the parameters
 * first (defaults ← entity.properties). Entity fields always win over the prefab.
 */
import type { BehaviorDef, CollisionDef, Json, MapEntity, ParamDef, PrefabDef, VisualDef } from './schema';
import { BUILTIN_PREFABS } from './builtinPrefabs';
import { type ExprScope, MapExprError, expandTemplate } from './MapExpr';
import { type MapIssue, mkIssue } from './MapIssue';

export class PrefabRegistry {
  private readonly map = new Map<string, PrefabDef>();
  constructor(extra: Record<string, PrefabDef> = {}, includeBuiltin = true) {
    if (includeBuiltin) for (const p of BUILTIN_PREFABS) this.map.set(p.id, p);
    for (const [id, p] of Object.entries(extra)) this.map.set(id, { ...p, id });
  }
  has(id: string): boolean { return this.map.has(id); }
  get(id: string): PrefabDef | undefined { return this.map.get(id); }
  ids(): string[] { return [...this.map.keys()]; }
  /** Prefab chain root → leaf; reports cycles / missing parents through `issues`. */
  chain(id: string, issues?: MapIssue[], path = ''): PrefabDef[] {
    const out: PrefabDef[] = [];
    const seen = new Set<string>();
    let cur: string | undefined = id;
    while (cur) {
      if (seen.has(cur)) { issues?.push(mkIssue('ERROR', 'PREFAB_CYCLE', path, `prefab "${id}" has an extends cycle at "${cur}"`)); break; }
      seen.add(cur);
      const p = this.map.get(cur);
      if (!p) { issues?.push(mkIssue('ERROR', 'PREFAB_MISSING', path, `prefab "${cur}" is not defined`)); break; }
      out.unshift(p);
      cur = p.extends;
    }
    return out;
  }
}

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

/** Deep merge: objects merge recursively, arrays and scalars are replaced; `undefined` in `over` keeps the base. */
export function deepMerge<T>(base: T, over: unknown): T {
  if (over === undefined) return base;
  if (isObj(base) && isObj(over)) {
    const out: Record<string, unknown> = { ...base };
    for (const [k, v] of Object.entries(over)) out[k] = k in out ? deepMerge(out[k], v) : v;
    return out as T;
  }
  return over as T;
}

export interface ResolvedEntity {
  id: string;
  type: MapEntity['type'];
  prefab?: string;
  x: number; y: number; z: number;
  rotation: number;
  sx: number; sy: number;
  tags: string[];
  /** Effective parameters: prefab defaults ← entity.properties (+ unknown properties passed through). */
  props: Record<string, Json>;
  visual?: VisualDef;
  collisions: CollisionDef[];
  behaviors: BehaviorDef[];
  chunk?: string;
  enabled: boolean;
  requires: string[];
}

function paramScope(chain: PrefabDef[], props: Record<string, Json> | undefined, issues: MapIssue[], path: string): { scope: ExprScope; defs: Record<string, ParamDef> } {
  const defs: Record<string, ParamDef> = {};
  for (const p of chain) Object.assign(defs, p.params ?? {});
  const scope: ExprScope = {};
  for (const [k, d] of Object.entries(defs)) {
    let v: Json = d.default;
    const given = props?.[k];
    if (given !== undefined) {
      if (typeof given !== typeof d.default) { issues.push(mkIssue('ERROR', 'PARAM_TYPE', `${path}/properties/${k}`, `parameter "${k}" expects ${typeof d.default}`)); }
      else v = given;
    }
    if (d.type === 'number' && typeof v === 'number') {
      if (d.min !== undefined && v < d.min) issues.push(mkIssue('ERROR', 'PARAM_RANGE', `${path}/properties/${k}`, `parameter "${k}" = ${v} is below min ${d.min}`));
      if (d.max !== undefined && v > d.max) issues.push(mkIssue('ERROR', 'PARAM_RANGE', `${path}/properties/${k}`, `parameter "${k}" = ${v} is above max ${d.max}`));
    }
    if (d.type === 'enum' && typeof v === 'string' && !d.values.includes(v)) issues.push(mkIssue('ERROR', 'PARAM_RANGE', `${path}/properties/${k}`, `parameter "${k}" must be one of ${d.values.join(', ')}`));
    scope[k] = v as never;
  }
  return { scope, defs };
}

const toArray = <T>(v: T | T[] | null | undefined): T[] => (v == null ? [] : Array.isArray(v) ? v : [v]);

/** Resolve one entity against the registry. Never throws: problems are returned as issues and the best-effort result. */
export function resolveEntity(e: MapEntity, reg: PrefabRegistry, path = ''): { entity: ResolvedEntity; issues: MapIssue[] } {
  const issues: MapIssue[] = [];
  let template: Partial<MapEntity> = {};
  let requires: string[] = [];
  const chainTags: string[] = [];
  let scope: ExprScope = {};
  if (e.prefab) {
    const chain = reg.chain(e.prefab, issues, `${path}/prefab`);
    const ps = paramScope(chain, e.properties, issues, path);
    scope = ps.scope;
    for (const p of chain) {
      try { const exp = expandTemplate(p.entity, scope); chainTags.push(...(exp.tags ?? [])); template = deepMerge(template, exp); }
      catch (err) { issues.push(mkIssue('ERROR', 'PREFAB_EXPR', `${path}/prefab`, `prefab "${p.id}": ${(err as MapExprError).message}`)); }
      requires = requires.concat(p.requires ?? []);
    }
  }
  const merged = deepMerge(template, { ...e, position: undefined, properties: undefined, prefab: undefined, id: undefined } as Partial<MapEntity>);
  const props: Record<string, Json> = { ...(scope as Record<string, Json>), ...(e.properties ?? {}) };
  const entity: ResolvedEntity = {
    id: e.id,
    type: (e.type ?? template.type ?? 'decor') as MapEntity['type'],
    prefab: e.prefab,
    x: e.position?.x ?? 0, y: e.position?.y ?? 0, z: e.position?.z ?? 0,
    rotation: e.rotation ?? 0,
    sx: e.scale?.x ?? 1, sy: e.scale?.y ?? 1,
    tags: [...new Set([...chainTags, ...(e.tags ?? [])])],
    props,
    visual: merged.visual,
    collisions: toArray(merged.collision as CollisionDef | CollisionDef[] | null | undefined),
    behaviors: toArray(merged.behavior as BehaviorDef | BehaviorDef[] | undefined),
    chunk: e.chunk,
    enabled: e.enabled ?? true,
    requires,
  };
  return { entity, issues };
}
