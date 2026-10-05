import type { AudioEventId } from './types';

/** One live voice as the pool sees it. */
export interface PoolVoice {
  readonly id: number;
  readonly event: AudioEventId;
  readonly priority: number;
  readonly startedAt: number;
  gain: number;
  /** Stops the platform voice (set by the manager once the host has started it). */
  stop: (fadeSec: number) => void;
  /** Voices without an end callback (legacy adapter) are freed when the clock passes this time. */
  endsAt?: number;
}

export interface AcquireRequest {
  event: AudioEventId;
  priority: number;
  gain: number;
  /** simultaneous voices of this event, and what to do at the cap */
  maxPerEvent: number;
  atCap: 'ignore' | 'steal-oldest';
  endsAt?: number;
}

export type AcquireResult =
  | { ok: true; voice: PoolVoice; stolen: PoolVoice[] }
  | { ok: false; reason: 'voice-limit' };

export interface PoolStats { acquired: number; stolen: number; rejected: number; released: number; peak: number }

/**
 * AudioPool — voice bookkeeping, independent of the platform.
 *
 *  1. An event at its own cap either ignores the request (`ignore`: charge click, ice slide — the original's *handle gating*)
 *     or steals its own oldest voice (`steal-oldest`: collisions).
 *  2. A full pool steals the voice with the lowest (priority, gain) among those whose priority is ≤ the request's; if there is
 *     none the request is rejected. Ties go to the oldest.
 *  3. A stolen voice is stopped with a short fade.
 * Voices are freed when the platform reports they ended (`release`) or, for voices without a callback, when `expire` passes their end.
 */
export class AudioPool {
  private live: PoolVoice[] = [];
  private nextId = 1;
  readonly stats: PoolStats = { acquired: 0, stolen: 0, rejected: 0, released: 0, peak: 0 };

  constructor(public capacity: number, private readonly stealFadeSec = 0.03) {}

  get count(): number { return this.live.length; }
  countFor(event: AudioEventId): number { let n = 0; for (const v of this.live) if (v.event === event) n++; return n; }
  voices(): readonly PoolVoice[] { return this.live; }

  acquire(req: AcquireRequest, now: number): AcquireResult {
    this.expire(now);
    const stolen: PoolVoice[] = [];
    if (this.countFor(req.event) >= req.maxPerEvent) {
      if (req.atCap === 'ignore') { this.stats.rejected++; return { ok: false, reason: 'voice-limit' }; }
      const own = this.live.filter(v => v.event === req.event).sort((a, b) => a.startedAt - b.startedAt || a.id - b.id)[0];
      this.steal(own, stolen);
    }
    if (this.live.length >= this.capacity) {
      const victims = this.live.filter(v => v.priority <= req.priority).sort((a, b) => a.priority - b.priority || a.gain - b.gain || a.startedAt - b.startedAt);
      if (victims.length === 0) { this.stats.rejected++; return { ok: false, reason: 'voice-limit' }; }
      this.steal(victims[0], stolen);
    }
    const voice: PoolVoice = { id: this.nextId++, event: req.event, priority: req.priority, startedAt: now, gain: req.gain, stop: () => {}, endsAt: req.endsAt };
    this.live.push(voice);
    this.stats.acquired++;
    if (this.live.length > this.stats.peak) this.stats.peak = this.live.length;
    return { ok: true, voice, stolen };
  }

  private steal(v: PoolVoice, out: PoolVoice[]): void {
    this.live = this.live.filter(x => x !== v);
    this.stats.stolen++;
    out.push(v);
    v.stop(this.stealFadeSec);
  }

  /** The platform reports the voice ended (or the manager gave up on it). Safe to call twice. */
  release(id: number): void {
    const n = this.live.length;
    this.live = this.live.filter(v => v.id !== id);
    if (this.live.length < n) this.stats.released++;
  }

  /** Free voices that have no end callback and whose time is up. */
  expire(now: number): void {
    if (!this.live.some(v => v.endsAt !== undefined && v.endsAt <= now)) return;
    const gone = this.live.filter(v => v.endsAt !== undefined && v.endsAt <= now);
    this.live = this.live.filter(v => !(v.endsAt !== undefined && v.endsAt <= now));
    this.stats.released += gone.length;
  }

  /** Stop every voice (optionally only one event's). */
  stopAll(event?: AudioEventId, fadeSec = 0.05): number {
    const hit = this.live.filter(v => !event || v.event === event);
    for (const v of hit) v.stop(fadeSec);
    this.live = this.live.filter(v => !hit.includes(v));
    this.stats.released += hit.length;
    return hit.length;
  }

  clear(): void { this.live = []; }
}
