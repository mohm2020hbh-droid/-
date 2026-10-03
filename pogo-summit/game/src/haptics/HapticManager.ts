import { Native } from '../platform/Native';

/**
 * HapticManager (Phase 12). Events: Jump · Landing · Hard Impact · Boost · Goal · UI (+ Bounce, Hazard).
 * Parameters per event: Intensity · Duration · Cooldown. Never fires per frame; every event is rate-limited.
 * The original game's haptic timing is unknown (PDF p.16: "when the vibration happens: unknown") so all values are ours.
 */
export type HapticEvent = 'jump' | 'landing' | 'hardImpact' | 'bounce' | 'boost' | 'goal' | 'hazard' | 'ui';

interface Pattern { duration: number; intensity: number; cooldown: number; pattern?: number[] }

export const HAPTIC_PATTERNS: Record<HapticEvent, Pattern> = {
  jump: { duration: 14, intensity: 0.35, cooldown: 70 },
  landing: { duration: 18, intensity: 0.55, cooldown: 70 },
  hardImpact: { duration: 55, intensity: 1.0, cooldown: 160 },
  bounce: { duration: 30, intensity: 0.7, cooldown: 90 },
  boost: { duration: 40, intensity: 0.8, cooldown: 120 },
  goal: { duration: 0, intensity: 0.9, cooldown: 600, pattern: [40, 60, 40, 60, 120] },
  hazard: { duration: 70, intensity: 1.0, cooldown: 220 },
  ui: { duration: 8, intensity: 0.25, cooldown: 45 },
};

export class HapticManager {
  enabled = true;
  strength = 1;
  private lastAt = new Map<HapticEvent, number>();
  /** Test hook: records what would have been sent. */
  log: { event: HapticEvent; ms: number; amp: number; at: number }[] = [];
  logging = false;

  constructor(private readonly now: () => number = () => performance.now()) {}

  trigger(event: HapticEvent, scale = 1): boolean {
    if (!this.enabled || this.strength <= 0) return false;
    const p = HAPTIC_PATTERNS[event];
    const t = this.now();
    const last = this.lastAt.get(event) ?? -1e9;
    if (t - last < p.cooldown) return false;
    this.lastAt.set(event, t);
    const amp = Math.max(0.05, Math.min(1, p.intensity * (0.4 + 0.6 * Math.max(0, Math.min(1, scale))) * this.strength));
    if (this.logging) this.log.push({ event, ms: p.duration, amp, at: t });
    if (p.pattern) Native.vibratePattern(p.pattern, amp);
    else Native.vibrate(p.duration * (0.7 + 0.5 * Math.max(0, Math.min(1, scale))), amp);
    return true;
  }
}
