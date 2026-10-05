import type { AudioLoop } from '../AudioManager';
import { AUDIO_CONFIG } from './audioConfig';
import { iceModulation } from './AudioEvent';
import { AUDIO_EVENT, type AudioEventContext } from './types';

/** The part of the simulation state the slide loop reads (a subset of `PogoState`, read-only). */
export interface IceSlideInput {
  /** m — the locked physics' slide-mode flag (E15) */
  slideMode: boolean;
  /** g — grounded */
  grounded: boolean;
  /** s — slide velocity (Q/T, the sim's own unit) */
  sx: number; sy: number;
}

/** What the controller needs from the manager. */
export interface LoopHost {
  startLoop(id: typeof AUDIO_EVENT.ICE_SLIDE, c?: AudioEventContext): AudioLoop | null;
  readonly now: number;
}

export type IceSlideState = 'IDLE' | 'LOOPING';

/**
 * IceSlideController — START / LOOP-MODULATE / STOP of the sliding sound.
 *
 *   IDLE ──(sliding ∧ speed ≥ startSpeed ∧ restart guard over)──► LOOPING ──(¬sliding ∨ speed < stopSpeed)──► IDLE
 *
 * `sliding` = slide mode ∧ grounded ∧ the ground is a slippery/ice surface. While LOOPING the pitch and gain follow the slide
 * speed with the ORIGINAL formulas (`iceModulation`, analysis AU-16). The controller only *reads* the state; it never changes it.
 * Driven once per display frame; no sound is ever produced by input or by position alone.
 */
export class IceSlideController {
  state: IceSlideState = 'IDLE';
  readonly counters = { starts: 0, stops: 0, modulations: 0 };
  private loop: AudioLoop | null = null;
  private stoppedAt = -1e9;

  constructor(private readonly audio: LoopHost, private readonly cfg: { startSpeed: number; stopSpeed: number; stopFadeSec: number; restartGuardSec: number } = AUDIO_CONFIG.ice) {}

  /** `surfaceOk`: the ground under the pogo is an ice / slippery surface. */
  update(s: IceSlideInput, surfaceOk: boolean): IceSlideState {
    const speed = Math.hypot(s.sx, s.sy);
    const sliding = s.slideMode && s.grounded && surfaceOk;
    if (this.state === 'IDLE') {
      if (sliding && speed >= this.cfg.startSpeed && this.audio.now - this.stoppedAt >= this.cfg.restartGuardSec) {
        const m = iceModulation(speed);
        const loop = this.audio.startLoop(AUDIO_EVENT.ICE_SLIDE, { gain: m.gain, pitch: m.pitch });
        if (loop) { this.loop = loop; this.state = 'LOOPING'; this.counters.starts++; }
      }
      return this.state;
    }
    // LOOPING
    if (!this.loop || !this.loop.active) { this.loop = null; this.state = 'IDLE'; return this.state; }      // the voice was stolen or ended elsewhere
    if (!sliding || speed < this.cfg.stopSpeed) { this.stop(); return this.state; }
    const m = iceModulation(speed);
    this.loop.set({ gain: m.gain, pitch: m.pitch });
    this.counters.modulations++;
    return this.state;
  }

  /** Fade the loop out now (pause, results, level change). */
  stop(fadeSec = this.cfg.stopFadeSec): void {
    if (this.loop) { this.loop.stop(fadeSec); this.loop = null; this.counters.stops++; this.stoppedAt = this.audio.now; }
    this.state = 'IDLE';
  }
}
