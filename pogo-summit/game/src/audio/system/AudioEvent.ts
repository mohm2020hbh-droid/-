import { LANDING_BY_MATERIAL, SFX, type SfxId } from '../AudioEvents';
import { AUDIO_CONFIG, volumeToGain } from './audioConfig';
import type { AudioVariantDef, SelectMode } from './AudioVariant';
import { AUDIO_EVENT, type AudioEventContext, type AudioEventId, type BusId, type Rng } from './types';

/**
 * One kind of occurrence the game can make sound for. The catalogue below (`AUDIO_EVENTS`) is the only place where an event's
 * bus, variants, selection, limits and level mapping are decided; the sim-side and map-side directors only say *what happened*.
 */
export interface AudioEventDef {
  id: AudioEventId;
  bus: BusId;
  kind: 'oneshot' | 'loop';
  variants: readonly AudioVariantDef[];
  select: SelectMode;
  /** per-variant cooldown inside `random` selection, ms */
  variantCooldownMs?: number;
  /** minimum interval between two plays of this event, ms */
  cooldownMs: number;
  /** at most `max` plays per `windowMs` (the "no machine gun" cap) */
  burst?: { max: number; windowMs: number };
  /** simultaneous voices of this event, and what to do at the cap */
  maxVoices: number;
  atCap: 'ignore' | 'steal-oldest';
  /** pool priority (higher survives) */
  priority: number;
  /** below this `intensity` the event is silent */
  minIntensity?: number;
  /** `listener`: heard at the listener (the pogo's own sounds); `positional`: attenuated / panned by distance */
  spatial: 'listener' | 'positional';
  /** original range class (positional events: 1, 1.5, 2, 3 × the listener half-width) */
  range?: number;
  /** roll-off radius in metres (Map V2 `radius` model); overrides `range` */
  radius?: number;
  /** default linear gain and pitch of the event (variants may override) */
  gain(ctx: AudioEventContext, rng: Rng): number;
  pitch(ctx: AudioEventContext, rng: Rng): number;
  /** events cut when this one starts (the charge click is cut at launch) */
  stops?: readonly { event: AudioEventId; fadeSec: number }[];
  /** a hit within `windowMs` of the previous play is attenuated */
  retriggerDuck?: { windowMs: number; gain: number };
  /** truncate long stingers in gameplay */
  truncate?: { maxSec: number; fadeSec: number };
}

const clamp01 = (x: number): number => (x < 0 ? 0 : x > 1 ? 1 : x);
const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;
const uniform = (r: Rng, lo: number, hi: number): number => lo + (hi - lo) * r.next();

// ── level mappings (pure, exported for tests) ─────────────────────────────────────────────────────────────────────

/** Impact (0..1) → gain curve of a collision: 0 below `minImpact`, then `lerp(curveLo, 1, x^curveExp)`. DESIGN. */
export function impactCurve(impact01: number): number {
  const c = AUDIO_CONFIG.collision;
  if (!(impact01 >= c.minImpact)) return 0;
  const x = Math.min(1, (impact01 - c.minImpact) / (1 - c.minImpact));
  return lerp(c.curveLo, 1, Math.pow(x, c.curveExp));
}

/** Collision gain: original volume 40 + random(10) (AU-13) × the impact curve. */
export function collisionGain(impact01: number, rng: Rng): number {
  const c = AUDIO_CONFIG.collision;
  return volumeToGain(uniform(rng, c.volumeMin, c.volumeMax)) * impactCurve(impact01);
}

/** Collision pitch: ±jitter and a small speed shift, clamped to [0.93, 1.07]. DESIGN (the original's 0.9–1.1 is wider). */
export function collisionPitch(impact01: number, rng: Rng): number {
  const c = AUDIO_CONFIG.collision;
  const p = (1 + (rng.next() * 2 - 1) * c.pitchJitter) * (1 + (clamp01(impact01) - 0.5) * c.pitchSpeedShift);
  return Math.max(0.93, Math.min(1.07, p));
}

/** Launch level scale from the charge power (DESIGN): lerp(minScale, 1, intensity). */
export const launchScale = (intensity: number): number => lerp(AUDIO_CONFIG.launch.minScale, 1, clamp01(intensity));

/** Ice slide modulation by slide speed |s| in Q/T — the ORIGINAL formulas (AU-16). */
export function iceModulation(slideSpeed: number): { gain: number; pitch: number } {
  const i = AUDIO_CONFIG.ice;
  const s = Math.max(0, slideSpeed);
  return {
    pitch: Math.min(i.speedBase + i.speedPerSlide * s, i.speedMax),
    gain: volumeToGain(Math.min(i.volBase + i.volPerSlide * s, i.volMax)),
  };
}

// ── catalogue ────────────────────────────────────────────────────────────────────────────────────────────────────

const synth = (id: string, recipe: string, extra: Partial<AudioVariantDef> = {}): AudioVariantDef => ({ id, source: { kind: 'synth', recipe }, ...extra });
const legacy = (id: string, sfx: SfxId | ((c: AudioEventContext) => SfxId), extra: Partial<AudioVariantDef> & { intensity?: (c: AudioEventContext) => number } = {}): AudioVariantDef => {
  const { intensity, ...rest } = extra;
  return { id, source: { kind: 'legacy', sfx, intensity }, durationHint: 0.5, ...rest };
};

const L = AUDIO_CONFIG;
const one = (): number => 1;

/** Defaults of a legacy one-shot (cooldowns are the legacy player's own gaps). */
const simple = (id: AudioEventId, bus: BusId, variants: readonly AudioVariantDef[], o: Partial<AudioEventDef> = {}): AudioEventDef => ({
  id, bus, kind: 'oneshot', variants, select: 'random', cooldownMs: 0, maxVoices: 4, atCap: 'steal-oldest', priority: 4, spatial: 'listener', gain: one, pitch: one, ...o,
});

export const AUDIO_EVENTS: Readonly<Record<AudioEventId, AudioEventDef>> = {
  // ── the six events of the audio brief ─────────────────────────────────────────────────────────────────────────
  POGO_CHARGE: {
    id: AUDIO_EVENT.POGO_CHARGE, bus: 'PLAYER', kind: 'oneshot', select: 'random',
    variants: [synth('charge_tick', 'charge_tick', { analog: 'pogoLoad2' })],
    cooldownMs: 0, maxVoices: 1, atCap: 'ignore', priority: 7, spatial: 'listener',
    gain: () => volumeToGain(L.charge.volume),
    pitch: (_c, r) => uniform(r, L.charge.pitchMin, L.charge.pitchMax),
  },
  POGO_LAUNCH: {
    id: AUDIO_EVENT.POGO_LAUNCH, bus: 'PLAYER', kind: 'oneshot', select: 'layers',
    variants: [
      synth('launch_pop', 'launch_pop', {
        analog: 'pogoLaunch2',
        gain: c => volumeToGain(L.launch.volume) * launchScale(c.intensity ?? 1),
        pitch: (_c, r) => uniform(r, L.launch.pitchMin, L.launch.pitchMax),
      }),
      synth('launch_boom', 'launch_boom', {
        analog: 'pogoLaunch3', when: c => !!c.power,
        gain: (c, r) => volumeToGain(uniform(r, L.launch.powerVolumeMin, L.launch.powerVolumeMax)) * launchScale(c.intensity ?? 1),
        pitch: (_c, r) => uniform(r, L.launch.powerPitchMin, L.launch.powerPitchMax),
      }),
    ],
    cooldownMs: 0, maxVoices: 3, atCap: 'steal-oldest', priority: 8, spatial: 'listener',
    gain: one, pitch: one,
    stops: [{ event: AUDIO_EVENT.POGO_CHARGE, fadeSec: L.charge.cutFadeSec }],
  },
  POGO_COLLISION: {
    id: AUDIO_EVENT.POGO_COLLISION, bus: 'PLAYER', kind: 'oneshot', select: 'random',
    variants: [
      synth('collision_1', 'collision_1', { analog: 'bounce1' }), synth('collision_2', 'collision_2', { analog: 'bounce2' }),
      synth('collision_3', 'collision_3', { analog: 'bounce3' }), synth('collision_4', 'collision_4', { analog: 'bounce4' }),
    ],
    variantCooldownMs: L.collision.variantCooldownMs,
    cooldownMs: L.collision.minIntervalMs, burst: { max: L.collision.burstMax, windowMs: L.collision.burstWindowMs },
    maxVoices: L.collision.maxVoices, atCap: 'steal-oldest', priority: 6, minIntensity: L.collision.minImpact,
    spatial: 'positional', range: L.collision.range,
    gain: (c, r) => collisionGain(c.intensity ?? 0, r),
    pitch: (c, r) => collisionPitch(c.intensity ?? 0, r),
    retriggerDuck: { windowMs: L.collision.duckWindowMs, gain: L.collision.duckGain },
  },
  POGO_BREAK: {
    id: AUDIO_EVENT.POGO_BREAK, bus: 'SURFACE', kind: 'oneshot', select: 'random',
    variants: [
      synth('break_1', 'break_1', { analog: 'break1', gain: () => volumeToGain(L.breakage.volume1) }),
      synth('break_2', 'break_2', { analog: 'break2', gain: () => volumeToGain(L.breakage.volume2) }),
    ],
    cooldownMs: L.breakage.minIntervalMs, maxVoices: L.breakage.maxVoices, atCap: 'steal-oldest', priority: 5,
    spatial: 'positional', radius: L.breakage.radius,
    gain: one, pitch: (_c, r) => 1 + (r.next() * 2 - 1) * L.breakage.pitchJitter,
  },
  ICE_SLIDE: {
    id: AUDIO_EVENT.ICE_SLIDE, bus: 'SURFACE', kind: 'loop', select: 'random',
    variants: [synth('ice_slide_loop', 'ice_slide_loop', { analog: 'iceSlide' })],
    cooldownMs: 0, maxVoices: 1, atCap: 'ignore', priority: 7, spatial: 'listener', range: L.ice.range,
    gain: one, pitch: one,
  },
  TIME_EFFECT: {
    id: AUDIO_EVENT.TIME_EFFECT, bus: 'UI', kind: 'oneshot', select: 'random',
    variants: [synth('time_riser', 'time_riser', { analog: 'pogoTime' })],
    cooldownMs: 500, maxVoices: 1, atCap: 'steal-oldest', priority: 9, spatial: 'listener',
    gain: () => volumeToGain(L.time.volume), pitch: one,
    truncate: { maxSec: L.time.gameplayMaxSec, fadeSec: L.time.gameplayFadeSec },
  },

  // ── surface contact (existing procedural landings, through the same pipeline) ────────────────────────────────
  SURFACE_LAND: simple(AUDIO_EVENT.SURFACE_LAND, 'SURFACE', [
    legacy('surface_land', c => LANDING_BY_MATERIAL[c.material ?? 'grass'] ?? SFX.landSoft, { intensity: c => 0.25 + (c.intensity ?? 0) * 0.75, durationHint: 0.3 }),
  ], { select: 'context', priority: 5 }),
  HARD_IMPACT: simple(AUDIO_EVENT.HARD_IMPACT, 'SURFACE', [legacy('hard_impact', SFX.hardImpact, { durationHint: 0.5 })], { priority: 6 }),

  // ── gameplay cues ────────────────────────────────────────────────────────────────────────────────────────────
  BOOST_ARMED: simple(AUDIO_EVENT.BOOST_ARMED, 'SFX', [legacy('boost_armed', SFX.boostArmed, { durationHint: 0.4 })]),
  BOOST_PAD: simple(AUDIO_EVENT.BOOST_PAD, 'SFX', [legacy('boost_pad', SFX.boostPad, { durationHint: 0.3 })]),
  FALL: simple(AUDIO_EVENT.FALL, 'SFX', [legacy('fall', SFX.fall, { durationHint: 0.9 })]),
  RESPAWN: simple(AUDIO_EVENT.RESPAWN, 'SFX', [legacy('respawn', SFX.respawn, { durationHint: 0.6 })]),
  HAZARD: simple(AUDIO_EVENT.HAZARD, 'SFX', [legacy('hazard', SFX.hazard, { durationHint: 0.4 })], { priority: 8 }),
  FINISH: simple(AUDIO_EVENT.FINISH, 'UI', [legacy('finish', SFX.goal, { durationHint: 1.6 })], { priority: 9 }),
  CHECKPOINT: simple(AUDIO_EVENT.CHECKPOINT, 'UI', [legacy('checkpoint', SFX.checkpoint, { durationHint: 0.6 })], { priority: 9 }),
  SPLASH: simple(AUDIO_EVENT.SPLASH, 'SURFACE', [legacy('splash', SFX.splash, { durationHint: 0.5, intensity: c => c.gain ?? 0.6 })]),
  TELEPORT: simple(AUDIO_EVENT.TELEPORT, 'SFX', [legacy('teleport', SFX.teleport, { durationHint: 0.5 })]),
  CHIME: simple(AUDIO_EVENT.CHIME, 'SFX', [legacy('chime', SFX.chime, { durationHint: 0.7, intensity: c => c.gain ?? 0.5 })]),
  LAVA_POP: simple(AUDIO_EVENT.LAVA_POP, 'AMBIENT', [legacy('lava_pop', SFX.lavaPop, { durationHint: 0.2 })], { priority: 2 }),
  BOOST_ZONE: simple(AUDIO_EVENT.BOOST_ZONE, 'SFX', [legacy('boost_zone', SFX.boostZone, { durationHint: 0.4 })]),

  // ── character voices ─────────────────────────────────────────────────────────────────────────────────────────
  VOICE_HUP: simple(AUDIO_EVENT.VOICE_HUP, 'PLAYER', [legacy('voice_hup', SFX.voiceHup, { durationHint: 0.2, intensity: c => c.intensity ?? 0.6 })], { maxVoices: 1, atCap: 'ignore' }),
  VOICE_OUCH: simple(AUDIO_EVENT.VOICE_OUCH, 'PLAYER', [legacy('voice_ouch', SFX.voiceOuch, { durationHint: 0.4 })], { maxVoices: 1, atCap: 'steal-oldest', priority: 7 }),
  VOICE_YAY: simple(AUDIO_EVENT.VOICE_YAY, 'PLAYER', [legacy('voice_yay', SFX.voiceYay, { durationHint: 0.5 })], { maxVoices: 1, atCap: 'steal-oldest', priority: 7 }),

  // ── interface ────────────────────────────────────────────────────────────────────────────────────────────────
  UI_CLICK: simple(AUDIO_EVENT.UI_CLICK, 'UI', [legacy('ui_click', SFX.uiClick, { durationHint: 0.1 })], { priority: 9 }),
  UI_BACK: simple(AUDIO_EVENT.UI_BACK, 'UI', [legacy('ui_back', SFX.uiBack, { durationHint: 0.15 })], { priority: 9 }),
  UI_CONFIRM: simple(AUDIO_EVENT.UI_CONFIRM, 'UI', [legacy('ui_confirm', SFX.uiConfirm, { durationHint: 0.3 })], { priority: 9 }),
  UI_UNLOCK: simple(AUDIO_EVENT.UI_UNLOCK, 'UI', [legacy('ui_unlock', SFX.uiUnlock, { durationHint: 0.5 })], { priority: 9 }),
  UI_STAR: simple(AUDIO_EVENT.UI_STAR, 'UI', [legacy('ui_star', SFX.uiStar, { durationHint: 0.4 })], { priority: 9 }),
};

/** Every procedural recipe id the catalogue needs (warm-up order = catalogue order). */
export function recipeIds(): string[] {
  const out: string[] = [];
  for (const def of Object.values(AUDIO_EVENTS)) {
    for (const v of def.variants) {
      const r = v.source.kind === 'synth' ? v.source.recipe : v.source.kind === 'sample' ? v.source.recipe : undefined;
      if (r && !out.includes(r)) out.push(r);
    }
  }
  return out;
}
