/** One ambience layer a zone asks for (a loop name from `LOOP_SOUNDS` and a 0..1 volume). */
export interface AudioZoneLayer { name: string; volume: number }

export interface AudioZoneDef {
  id: string;
  layers: AudioZoneLayer[];
  /** higher wins when a single value is needed (reverb tag, dominant zone) */
  priority?: number;
  reverb?: string;
}

/**
 * AudioZoneSet — the runtime view of Map V2 audio zones.
 *
 * A zone is *active* between its `enter` and `exit`. The target volume of a layer is the MAXIMUM among the active zones that ask
 * for it, so leaving one zone cannot silence a layer that another, overlapping zone still wants (the earlier bookkeeping wrote and
 * cleared layers per zone, last writer wins). The theme bed is a permanent zone.
 */
export class AudioZoneSet {
  private readonly defs = new Map<string, AudioZoneDef>();
  private readonly active: string[] = [];
  static readonly BED = '__bed';

  /** Register or extend a zone's layers (a Map V2 region's `audio` effects arrive one at a time). */
  define(id: string, layers: readonly AudioZoneLayer[] = [], o: { priority?: number; reverb?: string } = {}): AudioZoneDef {
    let d = this.defs.get(id);
    if (!d) { d = { id, layers: [], ...o }; this.defs.set(id, d); }
    for (const l of layers) {
      const ex = d.layers.find(x => x.name === l.name);
      if (ex) ex.volume = l.volume; else d.layers.push({ ...l });
    }
    if (o.priority !== undefined) d.priority = o.priority;
    if (o.reverb !== undefined) d.reverb = o.reverb;
    return d;
  }

  enter(id: string, layers: readonly AudioZoneLayer[] = [], o: { priority?: number; reverb?: string } = {}): void {
    this.define(id, layers, o);
    if (!this.active.includes(id)) this.active.push(id);
  }

  exit(id: string): void {
    const i = this.active.indexOf(id);
    if (i >= 0) this.active.splice(i, 1);
  }

  /** Theme bed: base layers that never leave. */
  setBed(layers: readonly AudioZoneLayer[]): void {
    const d = this.defs.get(AudioZoneSet.BED);
    if (d) d.layers = [];
    this.enter(AudioZoneSet.BED, layers, { priority: -1 });
  }

  isActive(id: string): boolean { return this.active.includes(id); }
  activeIds(): string[] { return [...this.active]; }

  /** Layer name → target volume (max over the active zones). */
  targets(): Map<string, number> {
    const out = new Map<string, number>();
    for (const id of this.active) for (const l of this.defs.get(id)?.layers ?? []) out.set(l.name, Math.max(out.get(l.name) ?? 0, l.volume));
    return out;
  }

  /** The active zone with the highest priority (ties: the most recently entered). */
  dominant(): AudioZoneDef | undefined {
    let best: AudioZoneDef | undefined;
    for (const id of this.active) { const d = this.defs.get(id)!; if (!best || (d.priority ?? 0) >= (best.priority ?? 0)) best = d; }
    return best;
  }

  /** Forget everything (level end). */
  clear(): void { this.defs.clear(); this.active.length = 0; }
}
