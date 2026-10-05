/**
 * MapMaterial — the material model of Map System V2 (Visual V2 · phase 3). Pure data + validation, no rendering.
 *
 *   normalizeMaterial(def)     any (pre-V2 or V2) MaterialDef → a fully-defaulted MapMaterial
 *   SURFACE_BRIDGE             SurfaceType → the existing physics surface kind (the "Physics Bridge")
 *   materialProblems(def)      range / reference problems (validator)
 *
 * `surfaceType` invents no physics: the only surface rule of the locked physics is slippery (E15). Everything else is a
 * declared kind that behaves as a normal solid (see SurfacePhysics) and is reported as a capability gap by the validator.
 */
import type { SurfaceId } from '../sim/SurfacePhysics';
import { isHexColor } from './MapColor';
import type { MaterialDef, ShaderKind, SurfaceType } from './schema';
import { SURFACE_TYPES } from './schema';

export interface MapMaterial {
  id: string;
  shader: ShaderKind;
  baseColor: string;
  baseColorMap?: string;
  normalMap?: string;
  normalScale: number;
  roughness: number;
  metalness: number;
  emissive: string;
  emissiveIntensity: number;
  emissiveMap?: string;
  opacity: number;
  tiling: { x: number; y: number };
  uvScale: number;
  uvMode: 'world' | 'object';
  surfaceType: SurfaceType;
  aoStrength: number;
  flow: { x: number; y: number };
  doubleSided: boolean;
  wobble?: { amplitude: number; speed: number };
  fallback?: string;
}

/** What a surface type means for the physics layer. `solid:false` ⇒ the surface carries no collision of its own. */
export interface SurfaceBridge {
  surface: SurfaceId;
  hazard: boolean;
  solid: boolean;
  /** Declared-but-not-applied capability (validator WARNING when a solid uses it). */
  capability?: 'bouncePush';
  /** One-line explanation (editor / validator hints). */
  note: string;
}

export const SURFACE_BRIDGE: Record<SurfaceType, SurfaceBridge> = {
  NORMAL: { surface: 'normal', hazard: false, solid: true, note: 'ordinary solid' },
  ICE: { surface: 'slippery', hazard: false, solid: true, note: 'slippery (E15 slide entry) with an icy look' },
  SLIPPERY: { surface: 'slippery', hazard: false, solid: true, note: 'slippery (E15 slide entry)' },
  BOUNCE: { surface: 'bounce', hazard: false, solid: true, capability: 'bouncePush', note: 'declared bounce surface — behaves as normal under the locked physics' },
  HAZARD: { surface: 'hazard', hazard: true, solid: true, note: 'respawn on touch (core hazard)' },
  WATER: { surface: 'normal', hazard: false, solid: false, note: 'visual/audio only — no collision (use a water region for splash effects)' },
  LAVA: { surface: 'hazard', hazard: true, solid: true, note: 'respawn on touch (core hazard) with a molten look' },
  GOAL: { surface: 'goal', hazard: false, solid: false, note: 'visual only — the finish zone is the goal trigger' },
};

/** Default surface type of a physics surface kind (used when a material does not declare one). */
export function surfaceTypeOf(surface: SurfaceId | undefined, hazard: boolean | undefined): SurfaceType {
  if (hazard || surface === 'hazard') return 'HAZARD';
  switch (surface) {
    case 'slippery': return 'SLIPPERY';
    case 'bounce': return 'BOUNCE';
    case 'goal': return 'GOAL';
    default: return 'NORMAL';
  }
}

const clamp01 = (v: number): number => Math.max(0, Math.min(1, v));
const num = (v: unknown, d: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : d);

/** Shader-level defaults: what a material looks like when the author only gives a colour. */
const SHADER_DEFAULTS: Record<ShaderKind, Partial<MapMaterial>> = {
  'stylized-lit': { roughness: 0.86, metalness: 0 },
  unlit: { roughness: 1, metalness: 0 },
  palette: { roughness: 0.9, metalness: 0 },
  emissive: { roughness: 0.5, metalness: 0, emissiveIntensity: 0.8 },
  water: { roughness: 0.15, metalness: 0, opacity: 0.74, flow: { x: 0.035, y: 0.06 }, doubleSided: true },
  ice: { roughness: 0.14, metalness: 0.04 },
  foliage: { roughness: 0.78, metalness: 0, doubleSided: true },
};

const SURFACE_DEFAULTS: Partial<Record<SurfaceType, Partial<MapMaterial>>> = {
  ICE: { roughness: 0.14, metalness: 0.04 },
  WATER: { roughness: 0.15, opacity: 0.74, flow: { x: 0.035, y: 0.06 }, doubleSided: true },
  LAVA: { roughness: 0.7, emissiveIntensity: 0.9, flow: { x: 0.02, y: 0.035 } },
};

export function normalizeMaterial(def: MaterialDef): MapMaterial {
  const shader: ShaderKind = def.shader ?? (def.surfaceType === 'WATER' ? 'water' : def.surfaceType === 'ICE' ? 'ice' : def.surfaceType === 'LAVA' ? 'emissive' : 'stylized-lit');
  const surfaceType: SurfaceType = def.surfaceType ?? (shader === 'ice' ? 'ICE' : shader === 'water' ? 'WATER' : 'NORMAL');
  const base: Partial<MapMaterial> = { ...SHADER_DEFAULTS[shader], ...(def.surfaceType ? SURFACE_DEFAULTS[def.surfaceType] : {}) };
  const baseColor = def.baseColor ?? def.color ?? '#b9aaa3';
  // pre-V2: `emissive: number` = intensity in the base colour
  const emissiveIsNumber = typeof def.emissive === 'number';
  const tiling = typeof def.tiling === 'number' ? { x: def.tiling, y: def.tiling } : def.tiling ? { x: def.tiling.x, y: def.tiling.y } : { x: 1, y: 1 };
  return {
    id: def.id,
    shader,
    baseColor,
    baseColorMap: def.baseColorMap ?? def.albedo ?? def.palette,
    normalMap: def.normalMap ?? def.normal,
    normalScale: num(def.normalScale, 0.8),
    roughness: clamp01(num(def.roughness, base.roughness ?? 0.86)),
    metalness: clamp01(num(def.metalness, base.metalness ?? 0)),
    emissive: typeof def.emissive === 'string' ? def.emissive : baseColor,
    emissiveIntensity: emissiveIsNumber ? clamp01(def.emissive as number) : Math.max(0, num(def.emissiveIntensity, typeof def.emissive === 'string' ? 1 : base.emissiveIntensity ?? 0)),
    emissiveMap: def.emissiveMap,
    opacity: clamp01(num(def.opacity, base.opacity ?? 1)),
    tiling,
    uvScale: Math.max(0.001, num(def.uvScale, 0.25)),
    uvMode: def.uvMode ?? 'world',
    surfaceType,
    aoStrength: clamp01(num(def.aoStrength, 1)),
    flow: def.flow ?? base.flow ?? { x: 0, y: 0 },
    doubleSided: def.doubleSided ?? base.doubleSided ?? false,
    wobble: def.wobble,
    fallback: def.fallback,
  };
}

/** Texture references (asset ids or `proc:*`) a material samples. */
export function texturesOfMaterial(def: MaterialDef): string[] {
  const m = normalizeMaterial(def);
  return [m.baseColorMap, m.normalMap, m.emissiveMap].filter((x): x is string => !!x);
}

const PROC_PREFIX = 'proc:';
export const isProceduralTexture = (ref: string): boolean => ref.startsWith(PROC_PREFIX);

/** Problems of a material definition (all reported by the validator as MATERIAL_INVALID). */
export function materialProblems(def: MaterialDef): string[] {
  const out: string[] = [];
  const range = (v: unknown, name: string, lo: number, hi: number): void => {
    if (v === undefined) return;
    if (typeof v !== 'number' || !Number.isFinite(v) || v < lo || v > hi) out.push(`${name} must be a number in ${lo}…${hi}`);
  };
  const color = (v: unknown, name: string): void => { if (v !== undefined && (typeof v !== 'string' || !isHexColor(v))) out.push(`${name} must be a #rgb / #rrggbb colour`); };
  color(def.baseColor, 'baseColor'); color(def.color, 'color');
  if (typeof def.emissive === 'string') color(def.emissive, 'emissive'); else range(def.emissive, 'emissive', 0, 1);
  range(def.roughness, 'roughness', 0, 1);
  range(def.metalness, 'metalness', 0, 1);
  range(def.opacity, 'opacity', 0, 1);
  range(def.normalScale, 'normalScale', 0, 4);
  range(def.emissiveIntensity, 'emissiveIntensity', 0, 8);
  range(def.aoStrength, 'aoStrength', 0, 1);
  range(def.uvScale, 'uvScale', 0.001, 64);
  if (typeof def.tiling === 'number') range(def.tiling, 'tiling', 0.01, 256);
  else if (def.tiling) { range(def.tiling.x, 'tiling.x', 0.01, 256); range(def.tiling.y, 'tiling.y', 0.01, 256); }
  if (def.surfaceType !== undefined && !SURFACE_TYPES.includes(def.surfaceType)) out.push(`surfaceType "${String(def.surfaceType)}" is not one of ${SURFACE_TYPES.join('/')}`);
  if (def.uvMode !== undefined && def.uvMode !== 'world' && def.uvMode !== 'object') out.push('uvMode must be "world" or "object"');
  return out;
}

/**
 * Physics a material implies for a collision that did not state it. Explicit `collision.surface`/`hazard` always win;
 * only unset fields are filled. Returns null when the material says nothing (NORMAL/undefined) or carries no solid.
 */
export function bridgeDefaults(def: MaterialDef | undefined): { surface?: SurfaceId; hazard?: boolean } | null {
  if (!def?.surfaceType) return null;
  const b = SURFACE_BRIDGE[def.surfaceType];
  if (!b.solid) return null;
  if (def.surfaceType === 'NORMAL') return null;
  return b.hazard ? { hazard: true } : { surface: b.surface };
}

/** True when the physics implied by `type` differs from what the collision actually declares. */
export function bridgeMismatch(type: SurfaceType, surface: SurfaceId | undefined, hazard: boolean | undefined): boolean {
  const b = SURFACE_BRIDGE[type];
  if (!b.solid) return false;
  if (b.hazard) return !hazard;
  return !!hazard || (surface ?? 'normal') !== b.surface;
}
