import type { AudioSettings } from '../../progression/settings';
import type { BusId } from './types';

/** One mixing bus: a volume, an optional mute, a parent. The platform node (a `GainNode`) is driven from `effective()`. */
export class AudioBus {
  volume = 1;
  muted = false;
  constructor(readonly id: BusId, readonly parent: AudioBus | null) {}

  /** Product of this bus's volume and every ancestor's; 0 when this bus or any ancestor is muted. */
  effective(): number {
    let g = 1;
    for (let b: AudioBus | null = this; b; b = b.parent) {
      if (b.muted) return 0;
      g *= b.volume;
    }
    return g;
  }

  /** Chain of ids from this bus up to MASTER (for diagnostics and tests). */
  path(): BusId[] {
    const out: BusId[] = [];
    for (let b: AudioBus | null = this; b; b = b.parent) out.push(b.id);
    return out;
  }
}

/**
 *   MASTER ─┬─ SFX ─┬─ PLAYER
 *           │       └─ SURFACE
 *           ├─ AMBIENT
 *           ├─ UI
 *           └─ MUSIC
 */
export class BusGraph {
  private readonly buses = new Map<BusId, AudioBus>();
  /** Called whenever the effective gain of a bus may have changed. */
  onChange: (bus: BusId, effective: number) => void = () => {};

  constructor() {
    const master = this.add('MASTER', null);
    const sfx = this.add('SFX', master);
    this.add('PLAYER', sfx);
    this.add('SURFACE', sfx);
    this.add('AMBIENT', master);
    this.add('UI', master);
    this.add('MUSIC', master);
  }

  private add(id: BusId, parent: AudioBus | null): AudioBus {
    const b = new AudioBus(id, parent);
    this.buses.set(id, b);
    return b;
  }

  get(id: BusId): AudioBus { return this.buses.get(id)!; }
  effective(id: BusId): number { return this.get(id).effective(); }

  /** Children of a bus (they are affected when it changes). */
  childrenOf(id: BusId): BusId[] { return [...this.buses.values()].filter(b => b.parent?.id === id).map(b => b.id); }

  set(id: BusId, volume: number, muted?: boolean): void {
    const b = this.get(id);
    b.volume = Math.max(0, Math.min(1, volume));
    if (muted !== undefined) b.muted = muted;
    this.notify(id);
  }

  mute(id: BusId, muted: boolean): void { this.get(id).muted = muted; this.notify(id); }

  private notify(id: BusId): void {
    this.onChange(id, this.effective(id));
    for (const c of this.childrenOf(id)) this.notify(c);
  }

  /** The user's four sliders map onto the buses: master → MASTER, sfx → SFX and UI, ambient → AMBIENT, music → MUSIC. */
  applySettings(s: AudioSettings): void {
    this.set('MASTER', s.master);
    this.set('SFX', s.sfx);
    this.set('UI', s.sfx);
    this.set('AMBIENT', s.ambient);
    this.set('MUSIC', s.music);
  }

  /** Push every bus's effective gain (after the platform context was created). */
  flush(): void { for (const id of this.buses.keys()) this.onChange(id, this.effective(id)); }
}
