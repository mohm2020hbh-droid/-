import * as THREE from 'three';
import type { MapRuntime } from '../../map/MapRuntime';
import { CameraProfileBlender, baseProfileOf } from '../../map/MapCamera';
import { sampleTheme } from '../../map/MapTheme';
import type { CameraProfile, Json, MapEvent, RegionDef, ThemeDef } from '../../map/schema';
import type { GameRenderer, RenderFrame } from '../GameRenderer';
import type { SkyView } from '../atmosphere';
import type { FxKind } from '../Vfx';
import type { LightRig } from './lights';

/**
 * MapPresentation — the render-side consumer of MapRuntime events (Visual V2 · phases 8, 10):
 *
 *   camera   CameraProfile + camera zones (regions of type "camera") blended every frame
 *   lights   lighting / fog zones push LightRig overrides; day/night samples retarget the base
 *   vfx      checkpoint / finish / break / water splash / teleport / boost-zone effects, theme ambient particles,
 *            waterfall and lava emitters — everything goes through the pooled `Vfx` (one InstancedMesh, fixed capacity)
 *
 * Audio is NOT done here (render layer); `MapAudio` consumes the same events from the app layer.
 */
const KNOWN_FX = new Set<string>(['dust', 'jumpdust', 'ring', 'groundring', 'impact', 'sparkle', 'ice', 'goo', 'boost', 'confetti', 'goal', 'hazard', 'speed', 'debris', 'charge', 'splash', 'lava', 'checkpoint', 'break']);

export class MapPresentation {
  readonly camera: CameraProfileBlender;
  private readonly regions = new Map<string, RegionDef>();
  private emitAcc = new Map<string, number>();
  private slideT = 0;
  private dnT = 0;

  constructor(private readonly gr: GameRenderer, private readonly rt: MapRuntime, private readonly theme: ThemeDef) {
    this.camera = new CameraProfileBlender(baseProfileOf(rt.doc.camera));
    this.camera.snap();
    for (const r of rt.doc.regions) this.regions.set(r.id, r);
  }

  /** Blended camera profile for this frame. */
  cameraProfile(dt: number): Readonly<CameraProfile> { return this.camera.update(dt); }

  // ── runtime events ──────────────────────────────────────────────────────────────────────────────────────────
  /** Called by the game for every map-event batch (once per physics tick that produced events). */
  handle(events: readonly MapEvent[]): void {
    const vfx = this.gr.vfx, scene = this.gr.mapScene, rig = this.gr.lightRig;
    for (const ev of events) {
      const x = ev.x ?? 0, y = ev.y ?? 0;
      switch (ev.type) {
        case 'zone_enter': case 'zone_exit': {
          const region = ev.id ? this.regions.get(ev.id) : undefined;
          const enter = ev.type === 'zone_enter';
          this.camera.onZone(enter ? 'enter' : 'exit', region);
          if (region && !enter && rig && (region.enter ?? []).some(f => f.op === 'lighting' || f.op === 'fog')) rig.popZone(region.id);
          if (region?.type === 'water') { vfx.burst('splash', x, y, 0, 1, enter ? 1 : 0.55, '#bfeaff', '#ffffff'); }
          break;
        }
        case 'camera': if (ev.id) this.camera.onEffect(ev.id, ev.data); break;
        case 'lighting': if (rig && ev.id) { const d = (ev.data ?? {}) as { ambient?: number; sun?: number; color?: string }; rig.pushZone(ev.id, { ambient: d.ambient, sun: d.sun, color: d.color }); } break;
        case 'fog': if (rig && ev.id) { const d = (ev.data ?? {}) as { density?: number; color?: string }; rig.pushZone(ev.id, { fogDensity: d.density, fogColor: d.color }); } break;
        case 'checkpoint': vfx.burst('checkpoint', x, y, 0, 1, 1, this.theme.palette.accent, '#ffffff'); if (ev.id) scene?.setCheckpointReached(ev.id); break;
        case 'finish': vfx.burst('confetti', x, y, 0, 1, 1); vfx.burst('goal', x, y, 0, 1, 1); break;
        case 'break': { const p = ev.id ? scene?.positionOf(ev.id) : null; if (p) { vfx.burst('break', p.x, p.y, 0, 1, 1, '#c9a070', '#8a6a48'); vfx.burst('dust', p.x, p.y, 0, 1, 0.8, '#cdbca8'); } break; }
        case 'restore': { const p = ev.id ? scene?.positionOf(ev.id) : null; if (p) vfx.burst('sparkle', p.x, p.y + 0.4, 0, 1, 0.5, '#ffffff', '#cfe9ff'); break; }
        case 'teleport': { const d = (ev.data ?? {}) as { x?: number; y?: number }; vfx.burst('ring', x, y, 0, 1, 0.8, '#b66cff', '#ffffff'); if (d.x !== undefined) vfx.burst('sparkle', d.x, d.y ?? y, 0, 1, 0.8, '#b66cff', '#ffffff'); break; }
        case 'boost_zone': vfx.burst('boost', x, y, 0, 1, 1, '#ffb347', '#ffffff'); break;
        case 'kill': vfx.burst('hazard', x, y, 0, 1, 1); break;
        case 'vfx': {
          const d = (ev.data ?? {}) as { id?: string; burst?: number };
          const kind = d.id && KNOWN_FX.has(d.id) ? (d.id as FxKind) : 'sparkle';
          vfx.burst(kind, x || this.gr.rig.focus.x, y || this.gr.rig.focus.y, 0, 1, Math.min(1, (d.burst ?? 1)));
          break;
        }
        default: break;
      }
    }
  }

  // ── per frame ───────────────────────────────────────────────────────────────────────────────────────────────
  update(dt: number, time: number, f: RenderFrame): void {
    const vfx = this.gr.vfx, rig = this.gr.rig, scene = this.gr.mapScene;
    // theme ambient particles around the camera (rate = per-frame probability scale)
    for (const p of this.theme.particles ?? []) vfx.ambient(rig.focus.x, rig.focus.y, rig.halfW * 1.1, rig.halfH, p.kind, p.colors, p.rate, p.size);
    // emitters of loaded chunks (lava vents, waterfalls, sparks …): only those near the camera
    if (scene) {
      for (const e of scene.emitters()) {
        if (e.kind !== 'vfx') continue;
        if (Math.abs(e.x - rig.focus.x) > rig.halfW * 1.6 + 8 || Math.abs(e.y - rig.focus.y) > rig.halfH * 1.8 + 8) continue;
        const rate = Number(e.props.rate ?? 3), kind = String(e.props.kind ?? 'sparkle');
        let acc = (this.emitAcc.get(e.id) ?? 0) + dt * rate;
        const radius = Number(e.props.radius ?? 1.5);
        while (acc >= 1) {
          acc -= 1;
          const fk = KNOWN_FX.has(kind) ? (kind as FxKind) : 'sparkle';
          vfx.burst(fk, e.x + (Math.random() - 0.5) * radius, e.y, 0, 1, Number(e.props.power ?? 0.35), typeof e.props.color === 'string' ? e.props.color : '#ffffff', '#ffffff');
        }
        this.emitAcc.set(e.id, acc);
      }
    }
    // ice spray while sliding
    this.slideT -= dt;
    if (f.state.mode !== 'AIR' && this.slideActive) { if (this.slideT <= 0) { this.slideT = 0.07; vfx.burst('ice', f.pose.footX, f.pose.footY, 0, 1, 0.5, '#cfeaff', '#ffffff'); } this.slideActive = false; }
    void time;
  }
  /** Set by the renderer when a `slide` sim event arrives this frame. */
  slideActive = false;

  /** Day/night cycle: sample the theme and retarget lights, fog and the sky dome. */
  applyDayNight(time: number, rig: LightRig, sky: SkyView): void {
    if (time - this.dnT < 0.25) return;
    this.dnT = time;
    const t = sampleTheme(this.theme, time);
    rig.retarget(t.sky.sunIntensity, t.lighting.hemiIntensity, t.fog.density, t.fog.color, t.sky.sun);
    const u = (sky.mesh.material as THREE.ShaderMaterial).uniforms;
    u.uTop.value.set(t.sky.top); u.uMid.value.set(t.sky.mid); u.uHor.value.set(t.sky.horizon); u.uSunCol.value.set(t.sky.sun);
  }

  dispose(): void { this.emitAcc.clear(); this.regions.clear(); }
}

export type { Json };
