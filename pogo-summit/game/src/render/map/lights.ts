import * as THREE from 'three';
import type { LightingProfile, ShadowQuality } from '../../map/schema';

/**
 * LightRig — the whole lighting of a map: ONE shadow-casting sun + ONE hemisphere light (+ fog). No per-object realtime
 * lights, which keeps fragment cost flat on a phone. Zones (`lighting` / `fog` effects of regions) push temporary overrides
 * that are blended in over ~0.5 s; day/night samples retarget the base.
 */
export const SHADOW_MAP: Record<ShadowQuality, number> = { off: 0, blob: 0, low: 512, medium: 1024, high: 2048 };

export interface LightOverride { sun?: number; ambient?: number; color?: string; fogDensity?: number; fogColor?: string }
export interface LightState { sun: number; ambient: number; sunColor: THREE.Color; fogDensity: number; fogColor: THREE.Color }

export interface DeviceShadow { shadows: boolean; shadowMap: number }

/** Effective shadow-map size: the profile's request, capped by the device quality (0 = no shadow map; the blob stays). */
export function effectiveShadowMap(q: ShadowQuality, device: DeviceShadow): number {
  if (!device.shadows) return 0;
  return Math.min(SHADOW_MAP[q], device.shadowMap);
}

export class LightRig {
  private base: LightState;
  private cur: LightState;
  private readonly zones: { id: string; ov: LightOverride }[] = [];
  readonly sunDir = new THREE.Vector3(-0.5, 0.6, 0.6).normalize();
  shadowMap = 0;

  constructor(private readonly sun: THREE.DirectionalLight, private readonly hemi: THREE.HemisphereLight, private readonly fog: THREE.FogExp2 | null, private readonly onFogColor: ((c: THREE.Color) => void) | null = null) {
    const st = (): LightState => ({ sun: sun.intensity, ambient: hemi.intensity, sunColor: sun.color.clone(), fogDensity: fog?.density ?? 0.004, fogColor: fog?.color.clone() ?? new THREE.Color('#b9bde2') });
    this.base = st(); this.cur = st();
  }

  get state(): Readonly<LightState> { return this.cur; }
  get baseState(): Readonly<LightState> { return this.base; }

  /** Set the base look from a profile (instantly: this is level load / theme sample). */
  apply(p: LightingProfile, device: DeviceShadow, snap = true): void {
    this.sunDir.set(...p.sunDirection).normalize();
    this.base = { sun: p.sunIntensity, ambient: p.ambientIntensity, sunColor: new THREE.Color(p.sunColor ?? '#ffffff'), fogDensity: p.fogDensity, fogColor: new THREE.Color(p.fogColor ?? '#b9bde2') };
    if (p.ambientSky) this.hemi.color.set(p.ambientSky);
    if (p.ambientGround) this.hemi.groundColor.set(p.ambientGround);
    this.shadowMap = effectiveShadowMap(p.shadowQuality, device);
    this.sun.castShadow = this.shadowMap > 0;
    if (this.shadowMap > 0 && this.sun.shadow.mapSize.x !== this.shadowMap) { this.sun.shadow.mapSize.set(this.shadowMap, this.shadowMap); this.sun.shadow.map?.dispose(); (this.sun.shadow as { map: unknown }).map = null; }
    if (snap) { this.cur = { sun: this.base.sun, ambient: this.base.ambient, sunColor: this.base.sunColor.clone(), fogDensity: this.base.fogDensity, fogColor: this.base.fogColor.clone() }; this.write(); }
  }

  /** Day/night: retarget base colours/intensities without touching the shadow map or direction. */
  retarget(sunIntensity: number, ambient: number, fogDensity: number, fogColor: string, sunColor?: string): void {
    this.base.sun = sunIntensity; this.base.ambient = ambient; this.base.fogDensity = fogDensity; this.base.fogColor.set(fogColor);
    if (sunColor) this.base.sunColor.set(sunColor);
  }

  pushZone(id: string, ov: LightOverride): void { this.popZone(id); this.zones.push({ id, ov }); }
  popZone(id: string): void { const i = this.zones.findIndex(z => z.id === id); if (i >= 0) this.zones.splice(i, 1); }
  clearZones(): void { this.zones.length = 0; }
  activeZones(): string[] { return this.zones.map(z => z.id); }

  /** Target = base overridden by zones in push order. */
  target(): LightState {
    const t: LightState = { sun: this.base.sun, ambient: this.base.ambient, sunColor: this.base.sunColor.clone(), fogDensity: this.base.fogDensity, fogColor: this.base.fogColor.clone() };
    for (const { ov } of this.zones) {
      if (ov.sun !== undefined) t.sun = ov.sun;
      if (ov.ambient !== undefined) t.ambient = ov.ambient;
      if (ov.color) t.sunColor.set(ov.color);
      if (ov.fogDensity !== undefined) t.fogDensity = ov.fogDensity;
      if (ov.fogColor) t.fogColor.set(ov.fogColor);
    }
    return t;
  }

  update(dt: number): void {
    const t = this.target(), k = 1 - Math.exp(-dt / 0.22);
    this.cur.sun += (t.sun - this.cur.sun) * k; this.cur.ambient += (t.ambient - this.cur.ambient) * k;
    this.cur.fogDensity += (t.fogDensity - this.cur.fogDensity) * k;
    this.cur.sunColor.lerp(t.sunColor, k); this.cur.fogColor.lerp(t.fogColor, k);
    this.write();
  }

  private write(): void {
    this.sun.intensity = this.cur.sun; this.sun.color.copy(this.cur.sunColor); this.hemi.intensity = this.cur.ambient;
    if (this.fog) { this.fog.density = this.cur.fogDensity; this.fog.color.copy(this.cur.fogColor); this.onFogColor?.(this.cur.fogColor); }
  }
}
