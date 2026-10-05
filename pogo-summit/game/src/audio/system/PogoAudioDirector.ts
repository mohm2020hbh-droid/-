import type { SimEvent } from '../../sim/events';
import type { AudioLoop } from '../AudioManager';
import { AUDIO_CONFIG } from './audioConfig';
import { IceSlideController, type IceSlideInput } from './IceSlide';
import { isSlideSurface, surfaceClass } from './MapAudioBridge';
import { mathRng } from './rng';
import { AUDIO_EVENT, type AudioEventContext, type AudioEventId, type EmitResult, type Rng } from './types';

/** What the director needs from the manager. */
export interface DirectorAudio {
  emit(id: AudioEventId, c?: AudioEventContext): EmitResult;
  startLoop(id: AudioEventId, c?: AudioEventContext): AudioLoop | null;
  silence(fadeSec?: number): void;
  setListener(x: number, y: number, halfWidth?: number): void;
  readonly now: number;
}

/** The slice of `PogoState` the director reads — never written. */
export interface DirectorState extends IceSlideInput {
  /** collider index the tip stands on (valid while grounded) */
  groundId: number;
  /** position, metres */
  x: number; y: number;
}

export interface SurfaceInfo { surface?: string; material?: string }

export interface DirectorOptions {
  /** collider index → its surface kind and material (from the physics world, read-only) */
  surfaceOf?: (groundId: number) => SurfaceInfo | null | undefined;
  rng?: Rng;
}

/**
 * PogoAudioDirector — simulation events → audio events.
 *
 *   charge_start                 → POGO_CHARGE            one click; one instance; cut at launch
 *   launch                       → POGO_LAUNCH            standard layer, + power layer when the same tick has `boost`
 *   wall_hit · land on `bounce`  → POGO_COLLISION         impact speed = the event's own normalised intensity
 *   land · hard_impact           → SURFACE_LAND · HARD_IMPACT
 *   (slide state, per frame)     → ICE_SLIDE loop         START / LOOP-MODULATE / STOP (`IceSlideController`)
 *   hazard · fall · goal · …     → the matching cue (+ character voices)
 *
 * Nothing is produced by holding, aiming, tilting or moving: only by the events above and the slide state. The director
 * reads `SimEvent`s and a read-only state slice, so the Physics Core is untouched (`tests/architecture.test.ts`).
 */
export class PogoAudioDirector {
  readonly ice: IceSlideController;
  private readonly rng: Rng;
  private readonly surfaceOf?: (groundId: number) => SurfaceInfo | null | undefined;

  constructor(private readonly audio: DirectorAudio, o: DirectorOptions = {}) {
    this.rng = o.rng ?? mathRng;
    this.surfaceOf = o.surfaceOf;
    this.ice = new IceSlideController({ startLoop: (id, c) => audio.startLoop(id, c), get now() { return audio.now; } });
  }

  /** One display frame's batch of simulation events. */
  handle(events: readonly SimEvent[]): void {
    if (events.length === 0) return;
    const boostTicks = new Set<number>();
    for (const e of events) if (e.type === 'boost') boostTicks.add(e.tick);
    for (const e of events) {
      switch (e.type) {
        case 'charge_start': this.audio.emit(AUDIO_EVENT.POGO_CHARGE); break;
        case 'launch':
          this.audio.emit(AUDIO_EVENT.POGO_LAUNCH, { intensity: e.intensity, power: boostTicks.has(e.tick), x: e.x, y: e.y });
          if (this.rng.next() < AUDIO_CONFIG.launch.voiceHupChance) this.audio.emit(AUDIO_EVENT.VOICE_HUP, { intensity: e.intensity });
          break;
        case 'wall_hit':
        case 'bounce':
          this.audio.emit(AUDIO_EVENT.POGO_COLLISION, { intensity: e.intensity, x: e.x, y: e.y, surface: e.surface, material: e.material });
          break;
        case 'land':
          if (surfaceClass(e.surface, e.material) === 'bounce') this.audio.emit(AUDIO_EVENT.POGO_COLLISION, { intensity: e.intensity, x: e.x, y: e.y, surface: e.surface, material: e.material });
          else this.audio.emit(AUDIO_EVENT.SURFACE_LAND, { intensity: e.intensity, surface: e.surface, material: e.material });
          break;
        case 'hard_impact': this.audio.emit(AUDIO_EVENT.HARD_IMPACT, { intensity: e.intensity }); break;
        case 'boost_armed': this.audio.emit(AUDIO_EVENT.BOOST_ARMED); break;
        case 'boost_pad': this.audio.emit(AUDIO_EVENT.BOOST_PAD); break;
        case 'hazard': this.audio.emit(AUDIO_EVENT.HAZARD); this.audio.emit(AUDIO_EVENT.VOICE_OUCH); break;
        case 'fall': this.audio.emit(AUDIO_EVENT.FALL); break;
        case 'respawn': this.ice.stop(); this.audio.emit(AUDIO_EVENT.RESPAWN); break;
        case 'goal': this.audio.emit(AUDIO_EVENT.FINISH); this.audio.emit(AUDIO_EVENT.VOICE_YAY); break;
        // `boost` is part of the launch layers; `slide` (entry) starts nothing by itself — the loop follows the state
        default: break;
      }
    }
  }

  /** Once per display frame while playing: the listener and the ice-slide loop. */
  update(s: DirectorState, halfWidth?: number): void {
    this.audio.setListener(s.x, s.y, halfWidth);
    let ok = true;
    if (this.surfaceOf) {
      const info = s.grounded ? this.surfaceOf(s.groundId) : null;
      ok = !!info && isSlideSurface(surfaceClass(info.surface, info.material));
    }
    this.ice.update(s, ok);
  }

  /** Pause, results, level change: fade out whatever loops. */
  silence(): void { this.ice.stop(); this.audio.silence(0.1); }
}
