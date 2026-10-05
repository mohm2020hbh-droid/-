/**
 * MapCamera — CameraProfile defaults and zone blending (Visual V2 · phase 10). Pure maths: the renderer's CameraRig reads
 * the blended profile every frame.
 *
 *   camera zones are regions of `type: "camera"` with `params.profile` (a partial CameraProfile) and `params.blend`
 *   (seconds); entering pushes the modifier, leaving pops it. The `camera` effect (`op: "camera"`) can push/reset too.
 */
import type { CameraDef, CameraProfile, Json, MapDocument, RegionDef } from './schema';

/** Equals the constants of the shipped CameraRig, so an untouched map frames exactly as before. */
export const DEFAULT_CAMERA_PROFILE: CameraProfile = {
  followDistance: 19, height: 2.3, lookAhead: 5.5, lookAheadGain: 0.3, smoothing: 0.26, smoothingY: 0.3, verticalBias: 1.55, fov: 30, zoom: 1,
};

export const CAMERA_PROFILE_KEYS = Object.keys(DEFAULT_CAMERA_PROFILE) as (keyof CameraProfile)[];

/** Allowed range per field (validator + clamp). Wide enough for creative framing, narrow enough to stay playable. */
export const CAMERA_PROFILE_LIMITS: Record<keyof CameraProfile, [number, number]> = {
  followDistance: [8, 60], height: [-4, 12], lookAhead: [0, 14], lookAheadGain: [0, 1], smoothing: [0.05, 2], smoothingY: [0.05, 2], verticalBias: [-3, 6], fov: [15, 60], zoom: [0.5, 2.5],
};

export function cameraProfileProblems(p: Partial<CameraProfile> | undefined): string[] {
  const out: string[] = [];
  for (const [k, v] of Object.entries(p ?? {})) {
    const lim = CAMERA_PROFILE_LIMITS[k as keyof CameraProfile];
    if (!lim) { out.push(`"${k}" is not a camera profile field (${CAMERA_PROFILE_KEYS.join(', ')})`); continue; }
    if (typeof v !== 'number' || !Number.isFinite(v) || v < lim[0] || v > lim[1]) out.push(`${k} must be a number in ${lim[0]}…${lim[1]}`);
  }
  return out;
}

export function mergeProfile(base: CameraProfile, over: Partial<CameraProfile> | undefined): CameraProfile {
  const out = { ...base };
  if (over) for (const k of CAMERA_PROFILE_KEYS) { const v = over[k]; if (typeof v === 'number' && Number.isFinite(v)) out[k] = Math.max(CAMERA_PROFILE_LIMITS[k][0], Math.min(CAMERA_PROFILE_LIMITS[k][1], v)); }
  return out;
}

/** The map's base profile: defaults ← `camera.zoom` (legacy) ← `camera.profile`. */
export function baseProfileOf(cam: CameraDef | undefined): CameraProfile {
  const legacy: Partial<CameraProfile> = {};
  if (cam && typeof cam.zoom === 'number' && cam.zoom !== 1) legacy.zoom = cam.zoom;
  if (cam && typeof cam.lookAhead === 'number') legacy.lookAhead = cam.lookAhead;
  return mergeProfile(mergeProfile(DEFAULT_CAMERA_PROFILE, legacy), cam?.profile);
}

/** The camera zones of a map (regions of type "camera" that carry a profile). */
export function cameraZonesOf(doc: Pick<MapDocument, 'regions'>): RegionDef[] {
  return doc.regions.filter(r => r.type === 'camera' && r.params && typeof r.params.profile === 'object' && r.params.profile !== null);
}

interface Modifier { id: string; profile: Partial<CameraProfile>; blend: number; priority: number; order: number }

/**
 * Blends the base profile with the active zone modifiers (higher priority wins per field, later entry wins ties) and moves
 * the current profile toward the target with an exponential approach whose time constant is the zone's `blend` seconds.
 */
export class CameraProfileBlender {
  private readonly mods: Modifier[] = [];
  private order = 0;
  private cur: CameraProfile;
  private target: CameraProfile;
  private blend = 0.8;

  constructor(private base: CameraProfile = DEFAULT_CAMERA_PROFILE) { this.cur = { ...base }; this.target = { ...base }; }

  setBase(base: CameraProfile): void { this.base = base; this.retarget(); }
  get current(): Readonly<CameraProfile> { return this.cur; }
  get targetProfile(): Readonly<CameraProfile> { return this.target; }
  activeZones(): string[] { return this.mods.map(m => m.id); }

  push(id: string, profile: Partial<CameraProfile>, blendSec = 0.8, priority = 0): void {
    this.pop(id);
    this.mods.push({ id, profile, blend: Math.max(0, blendSec), priority, order: this.order++ });
    this.retarget(blendSec);
  }
  pop(id: string): void {
    const i = this.mods.findIndex(m => m.id === id);
    if (i < 0) return;
    const blend = this.mods[i].blend;
    this.mods.splice(i, 1);
    this.retarget(blend);
  }
  clear(): void { this.mods.length = 0; this.retarget(0.5); }

  private retarget(blend = this.blend): void {
    this.blend = blend;
    const ordered = [...this.mods].sort((a, b) => a.priority - b.priority || a.order - b.order);
    let t = { ...this.base };
    for (const m of ordered) t = mergeProfile(t, m.profile);
    this.target = t;
  }

  /** Jump straight to the target (level load / respawn). */
  snap(): void { this.cur = { ...this.target }; }

  update(dt: number): Readonly<CameraProfile> {
    const k = this.blend <= 1e-4 ? 1 : 1 - Math.exp(-dt / Math.max(1e-4, this.blend * 0.45));
    for (const key of CAMERA_PROFILE_KEYS) this.cur[key] += (this.target[key] - this.cur[key]) * k;
    return this.cur;
  }

  /** Feed a `zone_enter` / `zone_exit` map event (region id + type in `data`). Returns true if the event concerned a camera zone. */
  onZone(kind: 'enter' | 'exit', region: RegionDef | undefined): boolean {
    if (!region || region.type !== 'camera') return false;
    const prof = region.params?.profile as Partial<CameraProfile> | undefined;
    if (!prof) return false;
    if (kind === 'enter') this.push(region.id, prof, Number(region.params?.blend ?? 0.8), region.priority ?? 0);
    else this.pop(region.id);
    return true;
  }

  /** Feed a `camera` effect event (op: "camera"). */
  onEffect(id: string, data: Json | undefined): void {
    const fx = (data ?? {}) as { profile?: Partial<CameraProfile>; reset?: boolean; zoom?: number; duration?: number };
    if (fx.reset) { this.pop(id); return; }
    const prof: Partial<CameraProfile> = { ...(fx.profile ?? {}) };
    if (typeof fx.zoom === 'number') prof.zoom = fx.zoom;
    if (Object.keys(prof).length) this.push(id, prof, fx.duration ?? 0.8);
  }
}
