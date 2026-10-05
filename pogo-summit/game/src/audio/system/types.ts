import type { SfxId } from '../AudioEvents';

/**
 * Shared types of the audio system (POGOSTUCK_AUDIO_SYSTEM_SPEC.md). Nothing in this file touches WebAudio, the DOM or the
 * simulation: the system is pure logic over an `AudioHost`, so it is fully unit-tested with a fake host.
 */

/** Mixing buses. MASTER ← SFX ← (PLAYER, SURFACE) · MASTER ← AMBIENT · MASTER ← UI · MASTER ← MUSIC. */
export type BusId = 'MASTER' | 'SFX' | 'PLAYER' | 'SURFACE' | 'AMBIENT' | 'UI' | 'MUSIC';
export const BUS_IDS: readonly BusId[] = ['MASTER', 'SFX', 'PLAYER', 'SURFACE', 'AMBIENT', 'UI', 'MUSIC'];

/** The only place where audio event ids are named (the sound-side counterpart of `SimEventType`). */
export const AUDIO_EVENT = {
  // the six events of the audio brief
  POGO_CHARGE: 'POGO_CHARGE', POGO_LAUNCH: 'POGO_LAUNCH', POGO_COLLISION: 'POGO_COLLISION', POGO_BREAK: 'POGO_BREAK', ICE_SLIDE: 'ICE_SLIDE', TIME_EFFECT: 'TIME_EFFECT',
  // surface contact
  SURFACE_LAND: 'SURFACE_LAND', HARD_IMPACT: 'HARD_IMPACT',
  // gameplay cues carried through the same pipeline
  BOOST_ARMED: 'BOOST_ARMED', BOOST_PAD: 'BOOST_PAD', FALL: 'FALL', RESPAWN: 'RESPAWN', HAZARD: 'HAZARD', FINISH: 'FINISH', CHECKPOINT: 'CHECKPOINT',
  SPLASH: 'SPLASH', TELEPORT: 'TELEPORT', CHIME: 'CHIME', LAVA_POP: 'LAVA_POP', BOOST_ZONE: 'BOOST_ZONE',
  // character voices
  VOICE_HUP: 'VOICE_HUP', VOICE_OUCH: 'VOICE_OUCH', VOICE_YAY: 'VOICE_YAY',
  // interface
  UI_CLICK: 'UI_CLICK', UI_BACK: 'UI_BACK', UI_CONFIRM: 'UI_CONFIRM', UI_UNLOCK: 'UI_UNLOCK', UI_STAR: 'UI_STAR',
} as const;
export type AudioEventId = (typeof AUDIO_EVENT)[keyof typeof AUDIO_EVENT];

/** What the caller knows about the occasion. Everything is optional; unknown fields mean "no information". */
export interface AudioEventContext {
  /** 0..1 strength: impact speed (collisions, landings) or charge power (launch) — the simulation's own normalisation. */
  intensity?: number;
  /** Power-jump layer requested (the sim emitted `boost` on the launch tick). */
  power?: boolean;
  /** Surface kind (sim `SurfaceId` or Map V2 `SurfaceType`) and material id at the event. */
  surface?: string;
  material?: string;
  /** Event position in metres (positional events). */
  x?: number;
  y?: number;
  /** Force one variant (by id). */
  variant?: string;
  /** Extra linear gain multiplier from the caller (e.g. a Map effect's volume). */
  gain?: number;
  /** Extra pitch multiplier from the caller. */
  pitch?: number;
  /** Override of the listener-relative pan (−1..1) when the caller computed it already. */
  pan?: number;
  /** Override of the positional roll-off radius in metres (Map V2 effects carry their own). */
  radius?: number;
  /** Play a long stinger in full (menus) instead of the truncated gameplay version. */
  full?: boolean;
  /** Loops: start position inside the buffer, 0..1 (random when absent). */
  offset01?: number;
}

export type SkipReason = 'unknown-event' | 'not-ready' | 'cooldown' | 'burst' | 'inaudible' | 'missing' | 'voice-limit' | 'disabled';

export interface EmitResult {
  played: boolean;
  reason?: SkipReason;
  /** ids of the variants that started (layers can start several). */
  variants: string[];
  /** linear gain / pitch of the first started voice (0 / 1 when nothing played). */
  gain: number;
  pitch: number;
}

/** Parameters of one voice at start and when it is updated. */
export interface VoiceParams {
  bus: BusId;
  gain: number;
  pitch: number;
  pan: number;
  loop: boolean;
  /** Start offset into the buffer, seconds (random loop start). */
  offset?: number;
  /** Stop after this many seconds (with `fadeOut`) — used to truncate long stingers in gameplay. */
  maxDuration?: number;
  fadeOut?: number;
}

/** A voice the host is playing. */
export interface HostVoice {
  /** Ramp the parameters (seconds). */
  set(p: Partial<Pick<VoiceParams, 'gain' | 'pitch' | 'pan'>>, rampSec?: number): void;
  /** Fade out and stop (0 = immediately). */
  stop(fadeSec?: number): void;
}

/**
 * The platform under the audio system. `WebAudioHost` is the real one; tests use a fake. An *opaque* buffer handle keeps the
 * system free of `AudioBuffer`.
 */
export interface AudioHost {
  /** True once the platform may make sound (context running after a user gesture). */
  readonly ready: boolean;
  /** Monotonic seconds (the audio clock). */
  readonly now: number;
  readonly sampleRate: number;
  /** Request the context (call from a user gesture). */
  unlock(): void;
  suspend(): void;
  resume(): void;
  /** Wrap mono PCM into a platform buffer. */
  makeBuffer(data: Float32Array, sampleRate: number): unknown;
  /** Decode an encoded file (optional sample banks). */
  decode?(bytes: ArrayBuffer): Promise<unknown>;
  /** Start a voice. `onEnded` fires when it finishes by itself or is stopped. Returns null when it cannot start. */
  start(buffer: unknown, p: VoiceParams, onEnded: () => void): HostVoice | null;
  /** Push the effective gain of a bus. */
  setBusGain(bus: BusId, gain: number): void;
  /** Duration of a platform buffer, seconds. */
  durationOf(buffer: unknown): number;
}

/** What the manager needs from the old procedural player (adapter variants). */
export interface LegacySfxPlayer {
  play(id: SfxId, o?: { intensity?: number; pitch?: number }): void;
}

export interface Rng {
  /** [0, 1) */
  next(): number;
}
