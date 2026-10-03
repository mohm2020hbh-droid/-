/** Persistent user settings (Phase 10/16): audio, controls, graphics, language. All values have safe defaults. */
export type ControlScheme = 'drag' | 'pad';
export type GuideMode = 'off' | 'short' | 'full';
export type QualityId = 'high' | 'default' | 'simplified';

export interface ControlSettings {
  scheme: ControlScheme;
  /** Pixels of finger travel for full tilt (DRAG) — "Swipe Distance". */
  swipeDistance: number;
  /** Gain of the pull-down gesture on charge power (0 disables) — "Swipe Strength". */
  swipeStrength: number;
  /** Tilt multiplier — "Touch Sensitivity". */
  sensitivity: number;
  /** Fraction of the travel ignored around the anchor — "Deadzone". */
  deadzone: number;
  /** 0 = raw, 1 = very smooth — "Input Smoothing". */
  smoothing: number;
  /** Charge time multiplier (1 = PhysicsConfig.chargeTicksMax) — "Charge Time". */
  chargeTime: number;
  leftHanded: boolean;
  guide: GuideMode;
}

export interface AudioSettings { master: number; music: number; sfx: number; ambient: number }
export interface GraphicsSettings { quality: QualityId; fps60: boolean; screenShake: boolean }
export interface Settings {
  control: ControlSettings;
  audio: AudioSettings;
  graphics: GraphicsSettings;
  haptics: { enabled: boolean; strength: number };
  language: 'en' | 'ar';
}

export const DEFAULT_SETTINGS: Settings = {
  control: { scheme: 'drag', swipeDistance: 90, swipeStrength: 1, sensitivity: 1, deadzone: 0.08, smoothing: 0.35, chargeTime: 1, leftHanded: false, guide: 'short' },
  audio: { master: 0.8, music: 0.55, sfx: 0.9, ambient: 0.6 },
  graphics: { quality: 'default', fps60: true, screenShake: true },
  haptics: { enabled: true, strength: 1 },
  language: 'en',
};

export function mergeSettings(base: Settings, patch: unknown): Settings {
  const out: Settings = JSON.parse(JSON.stringify(base));
  const p = patch as Partial<Settings> | null;
  if (!p || typeof p !== 'object') return out;
  const num = (v: unknown, lo: number, hi: number, d: number): number => (typeof v === 'number' && Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : d);
  if (p.control) {
    const c = p.control;
    out.control.scheme = c.scheme === 'pad' ? 'pad' : 'drag';
    out.control.swipeDistance = num(c.swipeDistance, 40, 220, base.control.swipeDistance);
    out.control.swipeStrength = num(c.swipeStrength, 0, 2, base.control.swipeStrength);
    out.control.sensitivity = num(c.sensitivity, 0.4, 2.5, base.control.sensitivity);
    out.control.deadzone = num(c.deadzone, 0, 0.4, base.control.deadzone);
    out.control.smoothing = num(c.smoothing, 0, 0.9, base.control.smoothing);
    out.control.chargeTime = num(c.chargeTime, 0.6, 1.6, base.control.chargeTime);
    out.control.leftHanded = !!c.leftHanded;
    out.control.guide = c.guide === 'off' || c.guide === 'full' ? c.guide : 'short';
  }
  if (p.audio) for (const k of ['master', 'music', 'sfx', 'ambient'] as const) out.audio[k] = num(p.audio[k], 0, 1, base.audio[k]);
  if (p.graphics) {
    out.graphics.quality = p.graphics.quality === 'high' || p.graphics.quality === 'simplified' ? p.graphics.quality : 'default';
    out.graphics.fps60 = p.graphics.fps60 !== false;
    out.graphics.screenShake = p.graphics.screenShake !== false;
  }
  if (p.haptics) { out.haptics.enabled = p.haptics.enabled !== false; out.haptics.strength = num(p.haptics.strength, 0, 1.5, 1); }
  if (p.language === 'ar') out.language = 'ar';
  return out;
}
