import { AUDIO_CONFIG } from './audioConfig';
import type { EmitResult } from './types';
import { AUDIO_EVENT, type AudioEventContext, type AudioEventId } from './types';
import { SFX, type SfxId } from '../AudioEvents';
import type { MapEvent } from '../../map/schema';

/**
 * Map System V2 ⇄ audio.
 *
 * `surfaceClass` is the one place that reads the simulation's `surface` (`SurfaceId`: normal, bounce, slippery, sticky, hazard, boost,
 * goal) and the Map V2 `SurfaceType` vocabulary (NORMAL, ICE, SLIPPERY, BOUNCE, HAZARD, WATER, LAVA, GOAL) together with a collider's
 * `material` id, and decides what the audio treats the surface as. Only `ice` / `slippery` allow the sliding loop; `bounce` turns a
 * landing into a collision "boing"; the rest choose the landing flavour.
 */
export type SurfaceClass = 'normal' | 'ice' | 'slippery' | 'bounce' | 'hazard' | 'lava' | 'water' | 'goal';

export function surfaceClass(surface?: string, material?: string): SurfaceClass {
  const s = (surface ?? '').toLowerCase(), m = (material ?? '').toLowerCase();
  if (s === 'ice') return 'ice';
  if (s === 'slippery') return /ice|frost|frozen|snow|glacier/.test(m) ? 'ice' : 'slippery';
  if (s === 'bounce') return 'bounce';
  if (s === 'lava' || (s === 'hazard' && /lava|magma|molten/.test(m))) return 'lava';
  if (s === 'hazard') return 'hazard';
  if (s === 'water' || (s === '' || s === 'normal') && /water/.test(m)) return 'water';
  if (s === 'goal') return 'goal';
  return 'normal';
}

/** True for the surfaces under which the sliding loop may sound. */
export const isSlideSurface = (c: SurfaceClass): boolean => c === 'ice' || c === 'slippery';

/** Old procedural sound → the event that carries it now (so Map V2 one-shots go through the same pipeline). */
export const SFX_TO_EVENT: Partial<Record<SfxId, AudioEventId>> = {
  [SFX.splash]: AUDIO_EVENT.SPLASH,
  [SFX.checkpoint]: AUDIO_EVENT.CHECKPOINT,
  [SFX.breakPlatform]: AUDIO_EVENT.POGO_BREAK,
  [SFX.teleport]: AUDIO_EVENT.TELEPORT,
  [SFX.chime]: AUDIO_EVENT.CHIME,
  [SFX.boostZone]: AUDIO_EVENT.BOOST_ZONE,
  [SFX.lavaPop]: AUDIO_EVENT.LAVA_POP,
};

export interface MapAudioSink { emit(id: AudioEventId, c?: AudioEventContext): EmitResult }

/**
 * MapAudioBridge — Map V2 events that `MapAudioCore` does not handle itself.
 *
 *   split   → TIME_EFFECT   (a timed split was recorded; gameplay version of the sting)
 *
 * Checkpoint, break, restore, teleport, boost zone, water zones and `audio` effects stay in `MapAudioCore` (positional lookups and
 * the zone layers live there). `finish` is deliberately NOT mapped: the simulation's `goal` event already plays the finish sound,
 * and both fire when a map is completed.
 */
export class MapAudioBridge {
  constructor(private readonly audio: MapAudioSink) {}

  handle(events: readonly MapEvent[]): void {
    for (const ev of events) {
      if (ev.type === 'split') this.audio.emit(AUDIO_EVENT.TIME_EFFECT, { gain: AUDIO_CONFIG.time.splitGain });
    }
  }
}
