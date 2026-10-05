import { AUDIO_CONFIG } from './audioConfig';

/** Where the player hears from: the camera target, with the camera's half-width (the unit of the original's range classes). */
export interface Listener { x: number; y: number; halfWidth: number }

export interface Spatial { gain: number; pan: number }

const clamp = (v: number, a: number, b: number): number => (v < a ? a : v > b ? b : v);

/** Gain from distance for the Map V2 roll-off: 1 at the source → 0 at `radius` (quadratic). */
export const attenuation = (dist: number, radius: number): number => (dist >= radius || radius <= 0 ? 0 : (1 - dist / radius) ** 2);
export const panOf = (dx: number, radius: number): number => Math.max(-0.85, Math.min(0.85, dx / Math.max(1, radius * 0.6)));

/**
 * The original's positional model (analysis AU-07, `kuSoundUpdateFrame`):
 *   gain = clamp(range · 1.25 − dist / W, 0, 1)         pan = clamp(Δx / W · 0.2, −1, 1)
 * `W` is the listener's half-width, `range` one of the original's classes (1, 1.5, 2, 3).
 */
export function originalSpatial(dx: number, dy: number, halfWidth: number, range: number): Spatial {
  const s = AUDIO_CONFIG.spatial;
  const W = Math.max(1e-6, halfWidth);
  return { gain: clamp(range * s.rangeScale - Math.hypot(dx, dy) / W, 0, 1), pan: clamp((dx / W) * s.panScale, -1, 1) };
}

/** Map V2 radius model. */
export function radiusSpatial(dx: number, dy: number, radius: number): Spatial {
  return { gain: attenuation(Math.hypot(dx, dy), radius), pan: panOf(dx, radius) };
}

export type SpatialModel = 'original' | 'radius';

export interface EmitterOptions {
  model: SpatialModel;
  /** original range class (model `original`) */
  range?: number;
  /** roll-off radius, metres (model `radius`) */
  radius?: number;
  /** position source; when it returns a point the emitter follows it */
  follow?: () => { x: number; y: number } | null;
}

/**
 * AudioEmitter — a positional sound source. A one-shot at a position, a loop that follows a moving thing and an ambient entity
 * are all emitters; `spatial(listener)` is the only thing they answer.
 */
export class AudioEmitter {
  constructor(readonly id: string, public x: number, public y: number, readonly opts: EmitterOptions) {}

  moveTo(x: number, y: number): void { this.x = x; this.y = y; }

  spatial(l: Listener): Spatial {
    const p = this.opts.follow?.();
    if (p) { this.x = p.x; this.y = p.y; }
    const dx = this.x - l.x, dy = this.y - l.y;
    return this.opts.model === 'original'
      ? originalSpatial(dx, dy, l.halfWidth, this.opts.range ?? 1)
      : radiusSpatial(dx, dy, this.opts.radius ?? 20);
  }
}
