/**
 * Map System V2 — data model (MAP_SYSTEM_V2_SPEC.md §5–§13).
 *
 * Pure data: everything here is JSON-serialisable. Units: metres, +x right, +y up, `z` = visual depth layer,
 * rotation in degrees CCW, time in seconds (the runtime converts to physics ticks).
 * Nothing in this module (or in `src/map/` as a whole) touches the Physics Core.
 */
import type { Vec2 } from '../sim/math';
import type { SurfaceId } from '../sim/SurfacePhysics';

export type { Vec2 };

export const MAP_FORMAT = 'pogo-summit.map';
export const MAP_FORMAT_VERSION = 2;
/** Physics contract the map was authored against (the locked spec revision this project implements). */
export const PHYSICS_CONTRACT = 'locked-spec-1';
/** Hard cap of files in one map package (every chunk is one file) — authors enlarge the chunk cell for very large maps. */
export const PACKAGE_MAX_FILES = 4096;

// ── shared small types ──────────────────────────────────────────────────────────────────────────────────────────
export interface Vec3 { x: number; y: number; z?: number }
export interface Rect { minX: number; maxX: number; minY: number; maxY: number }
export type Json = null | boolean | number | string | Json[] | { [k: string]: Json };

// ── manifest ────────────────────────────────────────────────────────────────────────────────────────────────────
export interface MapRequirements {
  minFormatVersion: number;
  /** Behaviour/feature names the map uses (computed by tools; hosts may refuse unknown ones). */
  capabilities: string[];
  physics: string;
}

export interface MapManifest {
  id: string;
  name: string;
  author: string;
  description: string;
  version: string;
  thumbnail?: string;
  /** 1 (easy) … 10 (extreme). */
  difficulty: number;
  estimatedTimeSec: number;
  theme: string;
  mapSize: { width: number; height: number };
  checkpointCount: number;
  tags: string[];
  requirements: MapRequirements;
  createdAt?: string;
  updatedAt?: string;
}

// ── world / theme / materials / assets ──────────────────────────────────────────────────────────────────────────
export interface WorldSettings {
  bounds: Rect;
  killY: number;
  /** Player-rule switches of the legacy kit. Reserved: they change player physics, so they must stay false. */
  modes: { doubleJump: boolean; puzzle: boolean; grapple: boolean };
}

export interface ThemeColors { top: string; mid: string; horizon: string; sun: string }
export interface ThemeDef {
  id: string;
  name: string;
  /** Existing renderer theme this one extends (deep-merged with `overrides`). */
  base?: 'autumn_hills' | 'snow_peaks' | 'ancient_ruins' | 'volcanic_depths';
  palette: { primary: string; secondary: string; accent: string; background: string };
  sky: ThemeColors & { sunDir: [number, number, number]; sunIntensity: number };
  fog: { color: string; density: number };
  lighting: { hemiSky: string; hemiGround: string; hemiIntensity: number; exposure: number };
  slots: { ground: string; secondary: string; water: string; lava?: string };
  vegetation: { density: number; kinds: string[] };
  vfx: { ambient: string[]; intensity: number };
  audio: { ambient: string; wind: number; birds: number; water: number };
  backgroundProps: string[];
  weather?: { type: 'none' | 'rain' | 'snow' | 'ash' | 'leaves'; intensity: number; windX?: number };
  dayNight?: {
    cycleSec: number;
    keyframes: { t: number; sky?: Partial<ThemeColors>; fog?: Partial<{ color: string; density: number }>; sunIntensity?: number; hemiIntensity?: number }[];
  };
  overrides?: Record<string, Json>;
}
export type ThemeRef = { ref: string; theme?: undefined } | { theme: ThemeDef; ref?: undefined };

export type ShaderKind = 'stylized-lit' | 'unlit' | 'palette' | 'emissive' | 'water' | 'ice' | 'foliage';
export interface MaterialDef {
  id: string;
  shader: ShaderKind;
  color: string;
  palette?: string;
  albedo?: string;
  normal?: string;
  roughness?: number;
  emissive?: number;
  uvScale?: number;
  wobble?: { amplitude: number; speed: number };
  fallback?: string;
}

export type AssetKind = 'mesh' | 'texture' | 'audio' | 'material' | 'collision';
export interface AssetRef {
  id: string;
  kind: AssetKind;
  path: string;
  bytes?: number;
  /** meshes */
  tris?: number;
  /** textures */
  width?: number; height?: number; format?: string;
}

// ── prefabs ─────────────────────────────────────────────────────────────────────────────────────────────────────
export type ParamDef =
  | { type: 'number'; default: number; min?: number; max?: number; unit?: string; label?: string }
  | { type: 'boolean'; default: boolean; label?: string }
  | { type: 'string'; default: string; label?: string }
  | { type: 'color'; default: string; label?: string }
  | { type: 'enum'; default: string; values: string[]; label?: string };

export interface PrefabDef {
  id: string;
  label?: string;
  category: string;
  extends?: string;
  params?: Record<string, ParamDef>;
  /** Entity template; strings beginning with "=" are expressions over the prefab params. */
  entity: Partial<Omit<MapEntity, 'id'>>;
  /** Capability names needed for the prefab to behave as intended (declared, may be unavailable). */
  requires?: string[];
}

// ── spawn / finish / checkpoints / progress / splits ───────────────────────────────────────────────────────────
export interface SpawnDef { id: string; position: Vec2; facing?: 1 | -1 }

export type RegionShape =
  | { kind: 'box'; x: number; y: number; w: number; h: number }
  | { kind: 'circle'; x: number; y: number; r: number }
  | { kind: 'polygon'; points: Vec2[] };

/** Box zones are centred on `position`. */
export interface FinishZone { id: string; position: Vec2; shape: { kind: 'box'; w: number; h: number } | { kind: 'polygon'; points: Vec2[] } }
export interface FinishDef { zones: FinishZone[] }

export type OrderMode = 'monotonic' | 'strict' | 'any';
export interface CheckpointDef {
  id: string;
  order: number;
  name?: string;
  region: RegionShape;
  respawn: Vec2;
  progress?: number;
  optional?: boolean;
  requires?: string[];
  orderMode?: OrderMode;
}

export interface RoutePoint { x: number; y: number; percent?: number; name?: string }
export interface RouteDef {
  id: string;
  kind: 'main' | 'optional' | 'branch' | 'secret';
  points: RoutePoint[];
  from?: { route: string; point: number };
  to?: { route: string; point: number };
}
export interface ProgressDef {
  routes: RouteDef[];
  window?: { backPercent: number; forwardPercent: number; relocateDistance: number };
}

export interface SplitDef { id: string; name: string; checkpoint: string; parSec?: number }
export interface SplitsDef {
  splits: SplitDef[];
  targets: { gold: number; silver: number; bronze: number; par?: number };
}

// ── paths ───────────────────────────────────────────────────────────────────────────────────────────────────────
export interface PathDef {
  id: string;
  kind: 'polyline' | 'bezier' | 'spline';
  /** polyline/spline: control points; bezier: 3n+1 points (p0,c1,c2,p1,c3,c4,p2,…). */
  points: Vec2[];
  closed?: boolean;
}

// ── conditions ──────────────────────────────────────────────────────────────────────────────────────────────────
export type Condition =
  | { flag: string }
  | { counter: 'jumps' | 'boosts' | 'deaths' | 'checkpoints'; mod?: number; in: number[] }
  | { checkpoint: string; reached: boolean }
  | { all: Condition[] }
  | { any: Condition[] }
  | { not: Condition };

// ── shapes / collision ──────────────────────────────────────────────────────────────────────────────────────────
export type ShapeDef =
  | { kind: 'box'; w: number; h: number; taper?: number; anchor?: 'center' | 'topCenter'; offset?: Vec2 }
  | { kind: 'sphere'; r: number; segments?: number; offset?: Vec2 }
  | { kind: 'capsule'; r: number; length: number; axis?: 'x' | 'y'; segments?: number; offset?: Vec2 }
  | { kind: 'convex'; points: Vec2[] }
  | { kind: 'mesh'; outline?: Vec2[]; polygons?: Vec2[][] }
  | { kind: 'slope'; w: number; h: number; mirror?: boolean };

export type OneWayDir = 'up' | 'down' | 'left' | 'right';
export interface CollisionDef {
  shape: ShapeDef;
  surface?: SurfaceId;
  material?: string;
  hazard?: boolean;
  trigger?: boolean;
  oneWay?: OneWayDir;
  safe?: boolean;
  enabled?: boolean;
}

// ── behaviours ──────────────────────────────────────────────────────────────────────────────────────────────────
export interface Osc { amplitude: number; period: number; phase?: number }
export type MoveBehavior = { type: 'move' } & (
  | { mode: 'sine'; x?: Osc; y?: Osc; z?: Osc }
  | { mode: 'linear'; points: Vec2[]; speed: number; pingPong?: boolean; pause?: number; ease?: 'linear' | 'smooth'; phase?: number }
  | { mode: 'path'; path: string; speed?: number; duration?: number; loop?: 'loop' | 'pingpong' | 'once'; phase?: number; pause?: number }
);
export interface RotateBehavior {
  type: 'rotate';
  mode: 'continuous' | 'sine' | 'free';
  speed?: number; base?: number; amplitude?: number; period?: number; phase?: number;
  damping?: number; initialSpeed?: number; pivot?: Vec2; axis?: 'x' | 'y' | 'z';
}
export interface InactiveState { collision?: false; visual?: 'ghost' | 'hidden' | 'visible' }
export interface ToggleBehavior {
  type: 'toggle';
  channel: 'jumps' | 'boosts' | 'checkpoints' | 'flag';
  flag?: string;
  modulus?: number;
  active: number[];
  inactive?: InactiveState;
  tint?: { active?: string; inactive?: string };
}
export interface TimedBehavior { type: 'timed'; period: number; phase?: number; duty?: number; inactive?: InactiveState; warn?: number }
export interface BreakableBehavior { type: 'breakable'; trigger: 'land' | 'touch'; delay?: number; respawn?: number }
export interface ConditionalBehavior { type: 'conditional'; when: Condition; inactive?: InactiveState }
export interface BoostZoneBehavior { type: 'boostZone'; kind: 'powerJump'; once?: boolean }
export interface SquashBehavior { type: 'squash'; amount?: number }
export interface PoiBehavior { type: 'poi'; style?: string }
export type BehaviorDef =
  | MoveBehavior | RotateBehavior | ToggleBehavior | TimedBehavior | BreakableBehavior
  | ConditionalBehavior | BoostZoneBehavior | SquashBehavior | PoiBehavior;
export const BEHAVIOR_TYPES = ['move', 'rotate', 'toggle', 'timed', 'breakable', 'conditional', 'boostZone', 'squash', 'poi'] as const;

// ── visual ──────────────────────────────────────────────────────────────────────────────────────────────────────
export interface LodLevel { distance: number; mesh?: string; tris: number }
export interface VisualDef {
  kind: 'procedural' | 'mesh' | 'sprite' | 'none';
  style?: string;
  mesh?: string;
  material?: string;
  tint?: string;
  lod?: LodLevel[];
  instancing?: boolean | string;
  tris?: number;
  layer?: string;
  parallax?: { x: number; y: number };
  castShadow?: boolean;
  receiveShadow?: boolean;
  emissive?: number;
  visibleWhen?: Condition;
  seed?: number;
  decor?: string;
  /** Depth of the slab behind the gameplay plane (renderer hint, metres). */
  depth?: number;
}

// ── entities ────────────────────────────────────────────────────────────────────────────────────────────────────
export const ENTITY_TYPES = [
  'platform', 'wall', 'slope', 'ceiling', 'hazard', 'interactive', 'moving',
  'decor', 'background', 'light', 'vfx', 'audio', 'marker', 'trigger',
] as const;
export type EntityType = (typeof ENTITY_TYPES)[number];

export interface MapEntity {
  id: string;
  type: EntityType;
  name?: string;
  prefab?: string;
  position: Vec3;
  rotation?: number;
  scale?: { x: number; y: number };
  tags?: string[];
  properties?: Record<string, Json>;
  visual?: VisualDef;
  collision?: CollisionDef | CollisionDef[] | null;
  behavior?: BehaviorDef | BehaviorDef[];
  chunk?: string;
  enabled?: boolean;
}

// ── regions / effects ───────────────────────────────────────────────────────────────────────────────────────────
export const REGION_TYPES = [
  'kill', 'checkpoint', 'finish', 'teleport', 'camera', 'vfx', 'audio', 'lighting', 'fog', 'ambient', 'hint', 'water', 'secret', 'trigger', 'hazard',
] as const;
export type RegionType = (typeof REGION_TYPES)[number];

export type Effect =
  | { op: 'kill' }
  | { op: 'teleport'; to: Vec2 | { spawn: true } }
  | { op: 'setCheckpoint'; id: string }
  | { op: 'setFlag'; name: string; value: boolean | number }
  | { op: 'incCounter'; name: string; by?: number }
  | { op: 'camera'; zoom?: number; offsetX?: number; offsetY?: number; lockY?: number | null; duration?: number }
  | { op: 'vfx'; id: string; burst?: number }
  | { op: 'audio'; id: string; volume?: number }
  | { op: 'lighting'; ambient?: number; sun?: number; color?: string }
  | { op: 'fog'; density?: number; color?: string }
  | { op: 'hint'; textKey: string }
  | { op: 'reveal'; tag: string }
  | { op: 'emit'; event: string; data?: Json };

export interface RegionDef {
  id: string;
  type: RegionType;
  shape: RegionShape;
  params?: Record<string, Json>;
  enter?: Effect[];
  exit?: Effect[];
  stay?: Effect[];
  once?: boolean;
  enabled?: boolean;
  tags?: string[];
  priority?: number;
}

// ── background / lighting / camera / audio / vfx ───────────────────────────────────────────────────────────────
export interface BackgroundLayer { id: string; z: number; parallax: { x: number; y: number }; fog?: number; anchor?: 'center' | 'bottom' }
export interface BackgroundDef { sky?: string; layers: BackgroundLayer[] }
export interface LightVolume { id: string; shape: RegionShape; ambient?: number; sun?: number; color?: string; priority?: number }
export interface LightingDef {
  sun: { dir: [number, number, number]; intensity: number; color: string };
  ambient: { color: string; intensity: number };
  shadows: { enabled: boolean; mode: 'blob' | 'map' };
  volumes: LightVolume[];
}
export interface CameraDef { zoom: number; minZoom?: number; maxZoom?: number; lookAhead?: number }
export interface AudioDef { maxVoices: number; reverb?: string }
export interface VfxDef { maxParticles: number }

// ── chunks ──────────────────────────────────────────────────────────────────────────────────────────────────────
export interface ChunkDef { id: string; bounds: Rect; tags?: string[]; pinned?: boolean }
export interface ChunkingDef {
  mode: 'auto-grid' | 'explicit';
  cell: { w: number; h: number };
  defs: ChunkDef[];
  activateRadius: number;
  loadRadius: number;
  unloadRadius: number;
  lodDistances: number[];
  maxActive: number;
}

// ── document ────────────────────────────────────────────────────────────────────────────────────────────────────
export interface MapMetadata {
  license?: string;
  credits?: string[];
  changelog?: string[];
  localized?: Record<string, { name?: string; description?: string }>;
  [k: string]: Json | undefined;
}

export interface MapDocument {
  format: typeof MAP_FORMAT;
  formatVersion: number;
  manifest: MapManifest;
  world: WorldSettings;
  theme: ThemeRef;
  materials: Record<string, MaterialDef>;
  assets: AssetRef[];
  prefabs: Record<string, PrefabDef>;
  /** `null` while the map is still being authored (the validator reports SPAWN_MISSING). */
  spawn: SpawnDef | null;
  finish: FinishDef;
  checkpoints: CheckpointDef[];
  progress: ProgressDef;
  splits: SplitsDef;
  paths: PathDef[];
  entities: MapEntity[];
  regions: RegionDef[];
  background: BackgroundDef;
  lighting: LightingDef;
  camera: CameraDef;
  vfx: VfxDef;
  audio: AudioDef;
  chunks: ChunkingDef;
  metadata: MapMetadata;
  extensions?: Record<string, Json>;
}

/** Top-level keys in canonical order (serialisation order). */
export const DOC_KEYS: (keyof MapDocument)[] = [
  'format', 'formatVersion', 'manifest', 'world', 'theme', 'materials', 'assets', 'prefabs', 'spawn', 'finish', 'checkpoints',
  'progress', 'splits', 'paths', 'entities', 'regions', 'background', 'lighting', 'camera', 'vfx', 'audio', 'chunks', 'metadata', 'extensions',
];

// ── events produced by the runtime ──────────────────────────────────────────────────────────────────────────────
export type MapEventType =
  | 'chunk_load' | 'chunk_unload' | 'chunk_activate' | 'chunk_deactivate'
  | 'zone_enter' | 'zone_exit' | 'zone_stay'
  | 'checkpoint' | 'checkpoint_skipped' | 'split' | 'finish'
  | 'toggle' | 'break' | 'restore'
  | 'teleport' | 'kill' | 'camera' | 'vfx' | 'audio' | 'lighting' | 'fog' | 'hint' | 'reveal' | 'flag' | 'emit'
  | 'boost_zone' | 'squash' | 'poi';
export interface MapEvent { type: MapEventType; tick: number; id?: string; x?: number; y?: number; data?: Json }
