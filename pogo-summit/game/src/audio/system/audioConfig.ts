/**
 * audioConfig — EVERY tuning number of the audio system, in one place.
 *
 * None of these is a physics constant: they are audio mapping coefficients, thresholds and limits. Each leaf has an origin tag in
 * `CONFIG_TAGS` (checked by `tests/audio/system/config.test.ts`: no number without a tag, no tag without a number):
 *
 *   ORIGINAL           extracted from the original game (POGOSTUCK_AUDIO_ANALYSIS.md, evidence id in the comment) and reproduced as is
 *   ORIGINAL-INSPIRED  a pattern the original uses elsewhere, applied to a case where it does not
 *   DESIGN             new, audio-only, chosen for the new game
 *
 * "Original volume units" are the 0–100 numbers of the original call sites (charge 50, bounce 40–50…). The original's DirectSound
 * volume scale is UNKNOWN (analysis U1); the new system reads them linearly as `volume / 100`.
 */
export const AUDIO_CONFIG = {
  /** original volume unit → linear gain divisor */
  volumeUnit: 100,

  pool: {
    /** global voice capacity (low-quality devices use `capacityLow`) */
    capacity: 16,
    capacityLow: 10,
    /** fade used when a voice is stolen, seconds */
    stealFadeSec: 0.03,
  },

  bank: {
    /** buffer memory budget, bytes (LRU eviction beyond it) */
    maxBytes: 8 * 1024 * 1024,
  },

  charge: {
    /** pogoLoad2 call: volume 50, pitch 0.9 + random(0.1), one instance (AU-09) */
    volume: 50, pitchMin: 0.9, pitchMax: 1.0,
    /** fade used to cut the click when the launch fires (original: kuSoundStop(h, −50)) */
    cutFadeSec: 0.06,
  },

  launch: {
    /** pogoLaunch2: volume 50, pitch 0.9 + random(0.2) (AU-10) */
    volume: 50, pitchMin: 0.9, pitchMax: 1.1,
    /** launch intensity → gain scale: lerp(minScale, 1, intensity) */
    minScale: 0.7,
    /** pogoLaunch3 on power jumps: volume 90 + random(10), pitch 0.9 + random(0.2) (AU-12) */
    powerVolumeMin: 90, powerVolumeMax: 100, powerPitchMin: 0.9, powerPitchMax: 1.1,
    /** chance that the character says "hup" on a launch */
    voiceHupChance: 0.6,
  },

  collision: {
    /** bounce1–4: volume 40 + random(10) (AU-13) */
    volumeMin: 40, volumeMax: 50,
    /** below this impact (0..1) nothing is heard */
    minImpact: 0.06,
    /** gain curve: lerp(curveLo, 1, ((impact − minImpact)/(1 − minImpact))^curveExp) */
    curveLo: 0.35, curveExp: 0.8,
    /** pitch: (1 ± jitter) · (1 + (impact − 0.5) · speedShift) — always within [0.93, 1.07] */
    pitchJitter: 0.04, pitchSpeedShift: 0.06,
    /** a variant is not eligible again before this many ms (unless nothing else is) */
    variantCooldownMs: 250,
    /** event cooldown, burst cap, voice cap */
    minIntervalMs: 90, burstMax: 4, burstWindowMs: 600, maxVoices: 3,
    /** a hit within `duckWindowMs` of the previous one is attenuated */
    duckWindowMs: 200, duckGain: 0.63,
    /** positional range class of the bounce call sites (AU-13) */
    range: 2,
  },

  breakage: {
    /** break1 volume 90, break2 volume 100 (AU-15) */
    volume1: 90, volume2: 100,
    pitchJitter: 0.05,
    minIntervalMs: 120, maxVoices: 2,
    /** positional roll-off radius, metres (same as the existing MapAudio break sound) */
    radius: 45,
  },

  ice: {
    /** modulation formulas of the original (AU-16): pitch = min(speedBase + speedPerSlide·|s|, speedMax); volume = min(volBase + volPerSlide·|s|, volMax) */
    speedBase: 0.675, speedPerSlide: 0.01, speedMax: 1,
    volBase: 20, volPerSlide: 1.5, volMax: 70,
    /** original kuSoundStop(h, −50): fade-out ≈ 0.25 s (time unit INFERRED, analysis §3.1) */
    stopFadeSec: 0.25,
    /** slide speed (Q/T, the sim's own unit) to start / to stop (hysteresis) */
    startSpeed: 3, stopSpeed: 1,
    /** after a stop the loop may not restart for this long */
    restartGuardSec: 0.12,
    /** positional range class (AU-16) */
    range: 1.5,
  },

  time: {
    /** pogoTime: volume 100 (AU-17) */
    volume: 100,
    /** gameplay triggers play only the first part of the 6.4 s sting */
    gameplayMaxSec: 3.0, gameplayFadeSec: 0.6,
    /** gain of the sting when triggered by a Map V2 split */
    splitGain: 0.6,
  },

  spatial: {
    /** original positional gain: clamp(range·rangeScale − dist/W, 0, 1) (AU-07) */
    rangeScale: 1.25,
    /** original pan: clamp(Δx/W · panScale, −1, 1) (AU-07) */
    panScale: 0.2,
    /** listener half-width when the camera does not provide one, metres */
    defaultHalfWidth: 10,
  },

  /** legacy one-shots carried through the same pipeline */
  legacy: {
    volume: 100,
  },

} as const;

export type ConfigTag = 'ORIGINAL' | 'ORIGINAL-INSPIRED' | 'DESIGN';

/** Origin of every numeric leaf of `AUDIO_CONFIG` (dotted path). */
export const CONFIG_TAGS: Record<string, ConfigTag> = {
  'volumeUnit': 'DESIGN',
  'pool.capacity': 'DESIGN', 'pool.capacityLow': 'DESIGN', 'pool.stealFadeSec': 'DESIGN',
  'bank.maxBytes': 'DESIGN',
  'charge.volume': 'ORIGINAL', 'charge.pitchMin': 'ORIGINAL', 'charge.pitchMax': 'ORIGINAL', 'charge.cutFadeSec': 'DESIGN',
  'launch.volume': 'ORIGINAL', 'launch.pitchMin': 'ORIGINAL', 'launch.pitchMax': 'ORIGINAL', 'launch.minScale': 'DESIGN',
  'launch.powerVolumeMin': 'ORIGINAL', 'launch.powerVolumeMax': 'ORIGINAL', 'launch.powerPitchMin': 'ORIGINAL', 'launch.powerPitchMax': 'ORIGINAL',
  'launch.voiceHupChance': 'DESIGN',
  'collision.volumeMin': 'ORIGINAL', 'collision.volumeMax': 'ORIGINAL', 'collision.minImpact': 'DESIGN', 'collision.curveLo': 'DESIGN', 'collision.curveExp': 'DESIGN',
  'collision.pitchJitter': 'DESIGN', 'collision.pitchSpeedShift': 'DESIGN', 'collision.variantCooldownMs': 'DESIGN', 'collision.minIntervalMs': 'DESIGN',
  'collision.burstMax': 'DESIGN', 'collision.burstWindowMs': 'DESIGN', 'collision.maxVoices': 'DESIGN', 'collision.duckWindowMs': 'DESIGN', 'collision.duckGain': 'DESIGN',
  'collision.range': 'ORIGINAL',
  'breakage.volume1': 'ORIGINAL', 'breakage.volume2': 'ORIGINAL', 'breakage.pitchJitter': 'DESIGN', 'breakage.minIntervalMs': 'DESIGN', 'breakage.maxVoices': 'DESIGN', 'breakage.radius': 'DESIGN',
  'ice.speedBase': 'ORIGINAL', 'ice.speedPerSlide': 'ORIGINAL', 'ice.speedMax': 'ORIGINAL', 'ice.volBase': 'ORIGINAL', 'ice.volPerSlide': 'ORIGINAL', 'ice.volMax': 'ORIGINAL',
  'ice.stopFadeSec': 'ORIGINAL', 'ice.startSpeed': 'DESIGN', 'ice.stopSpeed': 'DESIGN', 'ice.restartGuardSec': 'DESIGN', 'ice.range': 'ORIGINAL',
  'time.volume': 'ORIGINAL', 'time.gameplayMaxSec': 'DESIGN', 'time.gameplayFadeSec': 'DESIGN', 'time.splitGain': 'DESIGN',
  'spatial.rangeScale': 'ORIGINAL', 'spatial.panScale': 'ORIGINAL', 'spatial.defaultHalfWidth': 'DESIGN',
  'legacy.volume': 'DESIGN',
};

/** Dotted paths of every numeric leaf (for the sync test). */
export function configLeaves(obj: unknown = AUDIO_CONFIG, prefix = ''): string[] {
  if (typeof obj === 'number') return [prefix];
  if (obj && typeof obj === 'object') return Object.entries(obj).flatMap(([k, v]) => configLeaves(v, prefix ? `${prefix}.${k}` : k));
  return [];
}

/** Original volume units (0–100) → linear gain. */
export const volumeToGain = (v: number): number => Math.max(0, v) / AUDIO_CONFIG.volumeUnit;
