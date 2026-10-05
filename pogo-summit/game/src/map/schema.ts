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

/**
 * LightingProfile (Visual V2 · phase 7): everything the renderer needs to light a world with ONE shadow-casting sun and a
 * hemisphere light (no per-object realtime lights). Missing in a theme ⇒ derived from `sky`/`lighting`/`fog`.
 */
export type ShadowQuality = 'off' | 'blob' | 'low' | 'medium' | 'high';
export interface LightingProfile {
  sunDirection: [number, number, number];
  sunIntensity: number;
  sunColor?: string;
  ambientIntensity: number;
  ambientSky?: string;
  ambientGround?: string;
  fogDensity: number;
  fogColor?: string;
  shadowQuality: ShadowQuality;
  exposure?: number;
}

export type BackdropKind = 'mountains' | 'clouds' | 'fog' | 'silhouettes' | 'landmarks' | 'sea' | 'glow';
/** One far-field layer of a theme's backdrop (mountains, cloud banks, haze bands, distant structures…). */
export interface BackdropLayer {
  id: string;
  kind: BackdropKind;
  /** Depth behind the gameplay plane (metres, positive = farther). */
  z: number;
  /** 0 = fixed in the world, 1 = glued to the camera (parallax follow factor). */
  follow: number;
  /** Base height of the layer's lowest point and its vertical extent (metres). */
  y: number; height: number; width: number;
  color: string; color2?: string;
  /** Instances for clouds / silhouettes / landmarks. */
  count?: number; seed?: number;
  /** Builtin mesh id for silhouettes / landmarks (e.g. "builtin:ancient_structure"). */
  mesh?: string; meshParams?: Record<string, Json>;
  snow?: boolean; opacity?: number;
  /** 0..1 mix toward the fog colour (aerial perspective). */
  haze?: number;
}

export type ParticleKind = 'leaf' | 'snow' | 'ember' | 'ash' | 'mote' | 'petal' | 'spark' | 'mist';
export interface AmbientParticle { kind: ParticleKind; rate: number; colors: string[]; size?: number; speed?: number }

export interface ThemeDef {
  id: string;
  name: string;
  /** Existing renderer theme this one extends (deep-merged with `overrides`). */
  base?: 'autumn_hills' | 'snow_peaks' | 'ancient_ruins' | 'volcanic_depths';
  palette: { primary: string; secondary: string; accent: string; background: string };
  sky: ThemeColors & { sunDir: [number, number, number]; sunIntensity: number };
  fog: { color: string; density: number };
  lighting: { hemiSky: string; hemiGround: string; hemiIntensity: number; exposure: number };
  /**
   * Material slots ("@ground" …). ground = grass/snow/ash cap, rock = body of natural geometry, secondary = wood/props,
   * the others are optional (the renderer derives them from the palette when absent).
   */
  slots: { ground: string; secondary: string; water: string; lava?: string; rock?: string; foliage?: string; trunk?: string; crystal?: string; stone?: string; ice?: string; cloud?: string; glow?: string; bounce?: string };
  /** Map-wide material definitions shipped with the theme (referenced by the slots). */
  materials?: Record<string, MaterialDef>;
  vegetation: { density: number; kinds: string[] };
  vfx: { ambient: string[]; intensity: number };
  audio: { ambient: string; wind: number; birds: number; water: number };
  backgroundProps: string[];
  /** Visual V2: lighting profile, far-field backdrop, ambient particles and ambience bed. */
  lightingProfile?: LightingProfile;
  backdrop?: BackdropLayer[];
  particles?: AmbientParticle[];
  ambientAudio?: { bed: 'meadow' | 'snow' | 'ruins' | 'lava' | 'mystic'; wind: number; birds: number; water: number; chimes?: number; drone?: number };
  weather?: { type: 'none' | 'rain' | 'snow' | 'ash' | 'leaves'; intensity: number; windX?: number };
  dayNight?: {
    cycleSec: number;
    keyframes: { t: number; sky?: Partial<ThemeColors>; fog?: Partial<{ color: string; density: number }>; sunIntensity?: number; hemiIntensity?: number }[];
  };
  overrides?: Record<string, Json>;
}
export type ThemeRef = { ref: string; theme?: undefined } | { theme: ThemeDef; ref?: undefined };

export type ShaderKind = 'stylized-lit' | 'unlit' | 'palette' | 'emissive' | 'water' | 'ice' | 'foliage';
/**
 * Physical surface type of a material (Visual V2 · phase 3). It is a *bridge* to the existing physics surface kinds
 * (`MapMaterial.SURFACE_BRIDGE`): it invents no physics constant. Only SLIPPERY/ICE change the simulation (E15).
 */
export type SurfaceType = 'NORMAL' | 'ICE' | 'SLIPPERY' | 'BOUNCE' | 'HAZARD' | 'WATER' | 'LAVA' | 'GOAL';
export const SURFACE_TYPES: readonly SurfaceType[] = ['NORMAL', 'ICE', 'SLIPPERY', 'BOUNCE', 'HAZARD', 'WATER', 'LAVA', 'GOAL'];

/**
 * MapMaterial — a PBR-like stylised material. `color`/`albedo`/`normal`/`emissive:number` are the pre-V2 spellings and are
 * still accepted (see `normalizeMaterial`). Texture references are asset ids or `proc:<name>` procedural textures.
 */
export interface MaterialDef {
  id: string;
  shader?: ShaderKind;
  /** pre-V2 alias of `baseColor`. */
  color?: string;
  baseColor?: string;
  baseColorMap?: string;
  /** pre-V2 alias of `baseColorMap`. */
  albedo?: string;
  normalMap?: string;
  /** pre-V2 alias of `normalMap`. */
  normal?: string;
  normalScale?: number;
  palette?: string;
  roughness?: number;
  metalness?: number;
  /** number = pre-V2 intensity (0..1) in the base colour; string = emissive colour (use with `emissiveIntensity`). */
  emissive?: number | string;
  emissiveIntensity?: number;
  emissiveMap?: string;
  opacity?: number;
  /** Texture repeat multiplier (per axis or both). */
  tiling?: number | { x: number; y: number };
  /** Texture repeats per metre in `world` UV mode (default 0.25 = one tile per 4 m). */
  uvScale?: number;
  uvMode?: 'world' | 'object';
  surfaceType?: SurfaceType;
  /** 0..1 strength of the baked vertex ambient occlusion (default 1). */
  aoStrength?: number;
  /** UV scroll speed in tiles/second (water, lava, waterfalls). */
  flow?: { x: number; y: number };
  doubleSided?: boolean;
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
/** The four depth layers of a map (Visual V2 · phase 6). */
export type RenderLayer = 'foreground' | 'gameplay' | 'midground' | 'background';
export const RENDER_LAYERS: readonly RenderLayer[] = ['foreground', 'gameplay', 'midground', 'background'];
/** Procedural placement of many instances of one mesh inside a rectangle around the entity (render-time, deterministic). */
export interface ScatterDef {
  count: number;
  /** Rectangle around the entity position (metres). */
  width: number; height?: number;
  seed?: number;
  scale?: [number, number];
  /** Random yaw/roll jitter in degrees (±). */
  rotation?: number;
  /** Minimum spacing between instances (metres, 0 = none). */
  spacing?: number;
  /** Vertical jitter (±metres). */
  yJitter?: number;
  /** Depth jitter (±metres around the entity z). */
  zJitter?: number;
  /** Number of mesh variants cycled through (geometry variety), default 4. */
  variants?: number;
  /** Per-instance brightness variation 0..1. */
  tintVariance?: number;
}
export interface VisualDef {
  kind: 'procedural' | 'mesh' | 'sprite' | 'none';
  style?: string;
  /** Builtin generator id ("builtin:rock") or the id of a declared mesh asset (glTF). Never the collision shape. */
  mesh?: string;
  /** Parameters of a builtin generator (seed, size, kind …). */
  meshParams?: Record<string, Json>;
  material?: string;
  /** Per-role material overrides (cap, body, foliage, trunk, water, crystal …). `material` is the "body" role. */
  materials?: Record<string, string>;
  /** Depth layer (default derived from `layer` / entity type). */
  renderLayer?: RenderLayer;
  scatter?: ScatterDef;
  /** Beyond this camera distance (metres) the visual is not drawn. */
  cullDistance?: number;
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
  | { op: 'camera'; zoom?: number; offsetX?: number; offsetY?: number; lockY?: number | null; duration?: number; profile?: Partial<CameraProfile>; reset?: boolean }
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
/**
 * CameraProfile (Visual V2 · phase 10). Defaults equal the shipped camera (`DEFAULT_CAMERA_PROFILE`), so a map that
 * does not set anything frames the world exactly like before.
 */
export interface CameraProfile {
  /** Distance of the camera from the gameplay plane (metres). */
  followDistance: number;
  /** Camera height above the look target (metres). */
  height: number;
  /** Maximum horizontal look-ahead (metres) and its gain on the horizontal speed. */
  lookAhead: number;
  lookAheadGain: number;
  /** Follow smoothing times in seconds (horizontal, vertical). */
  smoothing: number;
  smoothingY: number;
  /** Look target offset above the player while grounded (metres). */
  verticalBias: number;
  /** Vertical field of view (degrees). */
  fov: number;
  /** Multiplier of the follow distance (1 = none). */
  zoom: number;
}
export interface CameraDef { zoom: number; minZoom?: number; maxZoom?: number; lookAhead?: number; profile?: Partial<CameraProfile> }
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
