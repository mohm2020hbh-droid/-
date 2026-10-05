import type { AudioManager } from './AudioManager';
import { SFX, type SfxId } from './AudioEvents';
import type { SFXManager } from './SFXManager';
import type { Emitter } from '../render/map/scene';
import type { Json, MapEvent, RegionDef } from '../map/schema';

/**
 * MapAudio — Map System V2 events → sound (Visual V2 · phase 9).
 *
 *   one-shots     checkpoint · break · restore · water splash · teleport · boost zone · `audio` effects of regions —
 *                 positional when the event has a position (distance attenuation + stereo pan), with per-sound COOLDOWNS
 *                 and a cap of simultaneous one-shots per frame
 *   zones         regions with `audio` effects switch ambience layers (wind, chimes, cave, water, lava) on enter / off on exit,
 *                 cross-faded
 *   emitters      `audio` entities (and waterfalls / water / lava, which bring their own) are positional loops: gain falls
 *                 off with distance, they pan by their side of the screen, and at most `maxVoices` of them sound at once —
 *                 the nearest win; a voice slot is reused (pooled) when a farther emitter takes over
 *
 * `MapAudioCore` is pure logic over an `AudioBackend`, so it is fully unit-tested; `WebAudioMapBackend` is the procedural
 * WebAudio implementation (no external sound files).
 */
export interface AudioBackend {
  readonly ready: boolean;
  oneShot(sound: SfxId, o: { gain: number; pan: number; pitch?: number }): void;
  loopStart(sound: string, key: string): void;
  loopSet(key: string, gain: number, pan: number): void;
  loopStop(key: string): void;
}

export const LOOP_SOUNDS = ['wind', 'water', 'waterfall', 'lava', 'chimes', 'cave'] as const;
const ONE_SHOT: Record<string, SfxId> = {
  splash: SFX.splash, checkpoint: SFX.checkpoint, break: SFX.breakPlatform, teleport: SFX.teleport, chime: SFX.chime, boostzone: SFX.boostZone, lavapop: SFX.lavaPop,
};
const COOLDOWN: Record<string, number> = { splash: 0.15, checkpoint: 0.35, break: 0.12, teleport: 0.3, chime: 0.1, boostzone: 0.3, lavapop: 0.08 };

export interface Listener { x: number; y: number }
export interface MapAudioOptions { maxVoices: number; maxOneShotsPerFrame?: number }
export interface MapAudioStats { activeLoops: number; ambientLayers: string[]; oneShots: number; suppressedByCooldown: number; suppressedByBudget: number; voiceSwaps: number }

/** Gain from distance: 1 at the source → 0 at `radius` (quadratic roll-off). */
export const attenuation = (dist: number, radius: number): number => (dist >= radius || radius <= 0 ? 0 : (1 - dist / radius) ** 2);
export const panOf = (dx: number, radius: number): number => Math.max(-0.85, Math.min(0.85, dx / Math.max(1, radius * 0.6)));

const num = (v: unknown, d: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : d);

export class MapAudioCore {
  private readonly regions = new Map<string, RegionDef>();
  private readonly cooldown = new Map<string, number>();
  private readonly layers = new Map<string, { gain: number; target: number }>();
  private readonly zoneLayers = new Map<string, string[]>();
  private readonly voices = new Map<string, { sound: string; gain: number }>();
  private clock = 0;
  private frameShots = 0;
  readonly stats: MapAudioStats = { activeLoops: 0, ambientLayers: [], oneShots: 0, suppressedByCooldown: 0, suppressedByBudget: 0, voiceSwaps: 0 };
  /** Where an entity is (for positional break / hit sounds). */
  positionOf: (id: string) => { x: number; y: number } | null = () => null;
  private listener: Listener = { x: 0, y: 0 };

  constructor(private readonly backend: AudioBackend, regions: readonly RegionDef[], private readonly o: MapAudioOptions) {
    for (const r of regions) this.regions.set(r.id, r);
  }

  /** One-shot with cooldown, frame budget and optional position. */
  play(sound: string, o: { x?: number; y?: number; gain?: number; radius?: number; key?: string } = {}): boolean {
    const id = ONE_SHOT[sound.replace(/^builtin:/, '')];
    if (!id || !this.backend.ready) return false;
    const key = o.key ?? sound;
    const cd = COOLDOWN[sound.replace(/^builtin:/, '')] ?? 0.1;
    if (this.clock - (this.cooldown.get(key) ?? -1e9) < cd) { this.stats.suppressedByCooldown++; return false; }
    if (this.frameShots >= (this.o.maxOneShotsPerFrame ?? 4)) { this.stats.suppressedByBudget++; return false; }
    let gain = o.gain ?? 1, pan = 0;
    if (o.x !== undefined && o.y !== undefined) {
      const radius = o.radius ?? 40, d = Math.hypot(o.x - this.listener.x, o.y - this.listener.y);
      gain *= attenuation(d, radius); pan = panOf(o.x - this.listener.x, radius);
      if (gain < 0.01) return false;
    }
    this.cooldown.set(key, this.clock); this.frameShots++; this.stats.oneShots++;
    this.backend.oneShot(id, { gain, pan });
    return true;
  }

  /** Map events of a tick (zones, checkpoints, breaks, effects). */
  handle(events: readonly MapEvent[]): void {
    for (const ev of events) {
      const x = ev.x, y = ev.y;
      switch (ev.type) {
        case 'checkpoint': this.play('checkpoint'); break;
        case 'break': { const p = ev.id ? this.positionOf(ev.id) : null; this.play('break', { ...(p ?? {}), key: `break:${ev.id}`, radius: 45 }); break; }
        case 'restore': { const p = ev.id ? this.positionOf(ev.id) : null; this.play('chime', { ...(p ?? {}), gain: 0.6, key: `restore:${ev.id}`, radius: 30 }); break; }
        case 'teleport': this.play('teleport'); break;
        case 'boost_zone': this.play('boostzone'); break;
        case 'zone_enter': case 'zone_exit': {
          const r = ev.id ? this.regions.get(ev.id) : undefined;
          if (!r) break;
          if (r.type === 'water') this.play('splash', { x, y, gain: ev.type === 'zone_enter' ? 1 : 0.55, radius: 60, key: 'water-zone' });
          if (ev.type === 'zone_exit') this.zoneExit(r);
          break;
        }
        case 'audio': {
          const d = (ev.data ?? {}) as { id?: string; volume?: number };
          const name = (d.id ?? '').replace(/^builtin:/, '');
          if ((LOOP_SOUNDS as readonly string[]).includes(name)) {
            this.setLayer(name, num(d.volume, 0.8));
            if (ev.id) { const l = this.zoneLayers.get(ev.id) ?? []; if (!l.includes(name)) l.push(name); this.zoneLayers.set(ev.id, l); }
          } else this.play(name, { gain: num(d.volume, 1), x, y, radius: 60, key: `fx:${ev.id}:${name}` });
          break;
        }
        default: break;
      }
    }
  }

  private zoneExit(r: RegionDef): void {
    const l = this.zoneLayers.get(r.id);
    if (!l) return;
    for (const name of l) this.setLayer(name, 0);
    this.zoneLayers.delete(r.id);
  }

  /** Ambience layer (cross-faded by `update`). */
  setLayer(name: string, volume: number): void { const l = this.layers.get(name); if (l) l.target = volume; else this.layers.set(name, { gain: 0, target: volume }); }

  /** Theme bed: the base ambience layers a theme asks for (chime pads for the mystic bed, a drone, lava rumble). */
  setBed(a?: { bed?: string; chimes?: number; drone?: number }): void {
    if (!a) return;
    if (a.chimes) this.setLayer('chimes', Math.min(1, a.chimes) * 0.6);
    if (a.drone) this.setLayer('cave', Math.min(1, a.drone) * 0.5);
    if (a.bed === 'lava') this.setLayer('lava', 0.35);
  }

  /** Per frame: fade layers, pick the nearest `maxVoices` emitters, drive their gains. */
  update(dt: number, listener: Listener, emitters: readonly Emitter[]): void {
    this.clock += dt; this.frameShots = 0; this.listener = listener;
    if (!this.backend.ready) return;                         // audio not unlocked yet (no user gesture): start loops once it is, never mark them as started
    // zone ambience layers
    const k = 1 - Math.exp(-dt / 0.6);
    for (const [name, l] of [...this.layers]) {
      l.gain += (l.target - l.gain) * k;
      const key = `layer:${name}`;
      if (l.gain < 0.005 && l.target === 0) { if (this.voices.has(key)) { this.backend.loopStop(key); this.voices.delete(key); } this.layers.delete(name); continue; }
      if (!this.voices.has(key)) { this.backend.loopStart(name, key); this.voices.set(key, { sound: name, gain: 0 }); }
      this.backend.loopSet(key, l.gain, 0);
    }
    // positional emitters: nearest first, limited voices
    const cand: { e: Emitter; gain: number; pan: number }[] = [];
    for (const e of emitters) {
      if (e.kind !== 'audio') continue;
      const radius = num(e.props.radius, 24), d = Math.hypot(e.x - listener.x, e.y - listener.y);
      const g = attenuation(d, radius) * num(e.props.volume, 0.6);
      if (g > 0.005) cand.push({ e, gain: g, pan: panOf(e.x - listener.x, radius) });
    }
    cand.sort((a, b) => b.gain - a.gain);
    const budget = Math.max(0, this.o.maxVoices - [...this.voices.keys()].filter(k2 => k2.startsWith('layer:')).length);
    const keep = new Set<string>();
    for (const c of cand.slice(0, budget)) {
      const key = `emitter:${c.e.id}`;
      keep.add(key);
      if (!this.voices.has(key)) { this.backend.loopStart(String(c.e.props.sound ?? 'wind'), key); this.voices.set(key, { sound: String(c.e.props.sound ?? 'wind'), gain: 0 }); }
      this.backend.loopSet(key, c.gain, c.pan);
    }
    for (const key of [...this.voices.keys()]) {
      if (!key.startsWith('emitter:') || keep.has(key)) continue;
      this.backend.loopStop(key); this.voices.delete(key); this.stats.voiceSwaps++;     // slot returns to the pool
    }
    this.stats.activeLoops = this.voices.size; this.stats.ambientLayers = [...this.layers.keys()].sort();
  }

  /** Stop everything (level end). */
  dispose(): void { for (const key of this.voices.keys()) this.backend.loopStop(key); this.voices.clear(); this.layers.clear(); this.zoneLayers.clear(); }
}

// ── WebAudio backend ────────────────────────────────────────────────────────────────────────────────────────────
interface Loop { nodes: AudioNode[]; gain: GainNode; pan: StereoPannerNode | null; timer: number; sources: AudioScheduledSourceNode[] }

export class WebAudioMapBackend implements AudioBackend {
  private readonly loops = new Map<string, Loop>();
  constructor(private readonly A: AudioManager, private readonly sfx: SFXManager) {}
  get ready(): boolean { return this.A.ready; }

  oneShot(sound: SfxId, o: { gain: number; pan: number; pitch?: number }): void { this.sfx.play(sound, { intensity: Math.max(0.1, Math.min(1, o.gain)), pitch: o.pitch }); }

  loopStart(sound: string, key: string): void {
    const c = this.A.ctx;
    if (!c || this.loops.has(key)) return;
    const gain = c.createGain(); gain.gain.value = 0.0001;
    const pan = c.createStereoPanner ? c.createStereoPanner() : null;
    gain.connect(pan ?? this.A.buses.ambient); if (pan) pan.connect(this.A.buses.ambient);
    const loop: Loop = { nodes: [gain], gain, pan, timer: 0, sources: [] };
    const noise = (type: BiquadFilterType, f: number, q = 0.7, f2?: BiquadFilterType, g2?: number): void => {
      const src = this.A.noiseSource(true), flt = c.createBiquadFilter();
      flt.type = type; flt.frequency.value = f; flt.Q.value = q;
      src.connect(flt);
      if (f2) { const f3 = c.createBiquadFilter(); f3.type = f2; f3.frequency.value = g2 ?? 4000; flt.connect(f3); f3.connect(gain); loop.nodes.push(f3); } else flt.connect(gain);
      src.start(); loop.sources.push(src); loop.nodes.push(src, flt);
    };
    switch (sound) {
      case 'wind': { noise('bandpass', 600, 0.7); const lfo = c.createOscillator(), lg = c.createGain(); lfo.frequency.value = 0.13; lg.gain.value = 240; lfo.connect(lg); lg.connect((loop.nodes[2] as BiquadFilterNode).frequency); lfo.start(); loop.sources.push(lfo); loop.nodes.push(lfo, lg); break; }
      case 'water': noise('lowpass', 1500, 0.7); break;
      case 'waterfall': noise('highpass', 500, 0.7, 'lowpass', 6500); break;
      case 'cave': noise('lowpass', 140, 0.9); break;
      case 'lava': noise('lowpass', 260, 0.9); loop.timer = window.setInterval(() => { if (this.A.ready && Math.random() < 0.7) this.sfx.play(SFX.lavaPop, { intensity: 0.15 + Math.random() * 0.2, pitch: 0.8 + Math.random() * 0.5 }); }, 520); break;
      case 'chimes': loop.timer = window.setInterval(() => { if (this.A.ready && Math.random() < 0.65) this.sfx.play(SFX.chime, { intensity: 0.2 + Math.random() * 0.3, pitch: [0.5, 0.667, 0.75, 1, 1.125][Math.floor(Math.random() * 5)] }); }, 900); break;
      default: noise('bandpass', 500, 0.7);
    }
    this.loops.set(key, loop);
  }

  loopSet(key: string, gain: number, pan: number): void {
    const l = this.loops.get(key), c = this.A.ctx;
    if (!l || !c) return;
    const t = c.currentTime;
    l.gain.gain.setTargetAtTime(Math.max(0.0001, gain * 0.35), t, 0.12);
    l.pan?.pan.setTargetAtTime(pan, t, 0.1);
  }

  loopStop(key: string): void {
    const l = this.loops.get(key), c = this.A.ctx;
    if (!l) return;
    window.clearInterval(l.timer);
    if (c) l.gain.gain.setTargetAtTime(0.0001, c.currentTime, 0.1);
    window.setTimeout(() => { for (const s of l.sources) { try { s.stop(); } catch { /* already stopped */ } } for (const n of l.nodes) { try { n.disconnect(); } catch { /* ignore */ } } }, 400);
    this.loops.delete(key);
  }
}

export type { Json };
