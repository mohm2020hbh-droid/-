import { BUS_IDS, type AudioHost, type BusId, type HostVoice, type VoiceParams } from './types';

export interface WebAudioHostOptions {
  /** Context factory (tests supply a fake). Default: `new AudioContext({ latencyHint: 'interactive' })`. */
  createContext?: () => AudioContext;
  /** Maximum pooled voice channels. */
  maxChannels?: number;
}

/** A pooled voice channel: `gain → pan → bus`. Only the cheap `AudioBufferSourceNode` is created per play. */
interface Channel { gain: GainNode; pan: StereoPannerNode | null; out: AudioNode; bus: BusId | null; busy: boolean }

let sharedContext: AudioContext | null = null;

/**
 * WebAudioHost — the only file that touches the Web Audio API.
 *
 *  • One `AudioContext` per page (module-level singleton); created on the first user gesture (`unlock`), resumed on every later
 *    one, suspended when the app is hidden, and re-resumed after an interruption (`statechange`).
 *  • Graph:  buses (GainNodes mirroring `BusGraph`) → MASTER → DynamicsCompressor → destination.
 *  • Voice channels are pooled and reused, so playing a sound allocates one source node and nothing else.
 *  • `start` returns null when the context is not running — the caller never queues sound for later.
 */
export class WebAudioHost implements AudioHost {
  ctx: AudioContext | null = null;
  master!: GainNode;
  /** One GainNode per bus (MASTER included). */
  private readonly busNodes = new Map<BusId, GainNode>();
  /** Legacy named buses used by `SFXManager`, `MusicManager`, `AmbientManager`, `MapAudio`. */
  readonly buses = {} as { sfx: GainNode; music: GainNode; ambient: GainNode };
  unlocked = false;
  /** Fired once when the context first reaches `running`. */
  onUnlock?: () => void;
  /** Fired when the context stops running by itself (phone call, tab switch…) so the app can resume on the next gesture. */
  needsResume = false;
  private noise: AudioBuffer | null = null;
  private readonly channels: Channel[] = [];
  private readonly pending = new Map<BusId, number>();
  /** Counters for tests and diagnostics. */
  readonly stats = { channelsCreated: 0, channelReuses: 0, voicesStarted: 0, contextsCreated: 0 };

  constructor(private readonly opts: WebAudioHostOptions = {}) {
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', () => { if (document.hidden) this.suspend(); else this.resume(); });
  }

  /** Test helper: forget the shared context. */
  static resetShared(): void { sharedContext = null; }

  get ready(): boolean { return !!this.ctx && this.ctx.state === 'running'; }
  get now(): number { return this.ctx ? this.ctx.currentTime : 0; }
  get sampleRate(): number { return this.ctx ? this.ctx.sampleRate : 44100; }

  /** Call from a user gesture. Safe to call repeatedly: the context is created once and merely resumed afterwards. */
  unlock(): void {
    if (this.ctx) { this.resume(); return; }
    try {
      const ctx = this.opts.createContext ? this.opts.createContext() : this.sharedOrNew();
      this.ctx = ctx;
      this.buildGraph(ctx);
      ctx.onstatechange = () => {
        if (ctx.state === 'running') { this.needsResume = false; if (!this.unlocked) { this.unlocked = true; this.onUnlock?.(); } }
        else if (this.unlocked) this.needsResume = true;                 // interrupted / suspended by the system: resume on the next gesture
      };
      void ctx.resume().then(() => {
        if (ctx.state === 'running' && !this.unlocked) { this.unlocked = true; this.onUnlock?.(); }
      }).catch(() => { /* still locked: the next gesture retries */ });
    } catch { /* audio is optional */ }
  }

  private sharedOrNew(): AudioContext {
    if (sharedContext && sharedContext.state !== 'closed') return sharedContext;
    const AC = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AC) throw new Error('no Web Audio');
    sharedContext = new AC({ latencyHint: 'interactive' });
    this.stats.contextsCreated++;
    return sharedContext;
  }

  private buildGraph(ctx: AudioContext): void {
    this.master = ctx.createGain();
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -14; comp.knee.value = 18; comp.ratio.value = 4; comp.attack.value = 0.004; comp.release.value = 0.2;
    this.master.connect(comp); comp.connect(ctx.destination);
    this.busNodes.set('MASTER', this.master);
    const parentOf: Record<Exclude<BusId, 'MASTER'>, BusId> = { SFX: 'MASTER', PLAYER: 'SFX', SURFACE: 'SFX', AMBIENT: 'MASTER', UI: 'MASTER', MUSIC: 'MASTER' };
    for (const id of BUS_IDS) {
      if (id === 'MASTER') continue;
      const g = ctx.createGain();
      g.connect(this.busNodes.get(parentOf[id])!);
      this.busNodes.set(id, g);
    }
    this.buses.sfx = this.busNodes.get('SFX')!; this.buses.music = this.busNodes.get('MUSIC')!; this.buses.ambient = this.busNodes.get('AMBIENT')!;
    // gains requested before the context existed
    for (const [bus, g] of this.pending) this.setBusGain(bus, g);
    this.pending.clear();
    // shared white-noise buffer (2 s) for the procedural legacy sounds
    const nb = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
    const d = nb.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    this.noise = nb;
  }

  suspend(): void { void this.ctx?.suspend().catch(() => {}); }
  resume(): void { if (this.ctx && this.ctx.state !== 'running' && this.ctx.state !== 'closed') void this.ctx.resume().catch(() => {}); }

  busNode(id: BusId): GainNode | undefined { return this.busNodes.get(id); }

  setBusGain(bus: BusId, gain: number): void {
    const ctx = this.ctx, n = this.busNodes.get(bus);
    if (!ctx || !n) { this.pending.set(bus, gain); return; }
    n.gain.setTargetAtTime(gain, ctx.currentTime, bus === 'MUSIC' || bus === 'AMBIENT' ? 0.05 : 0.03);
  }

  makeBuffer(data: Float32Array, sampleRate: number): unknown {
    const b = this.ctx!.createBuffer(1, data.length, sampleRate);
    b.copyToChannel(data, 0);
    return b;
  }

  decode(bytes: ArrayBuffer): Promise<unknown> { return this.ctx!.decodeAudioData(bytes); }
  durationOf(buffer: unknown): number { return (buffer as AudioBuffer).duration; }

  noiseSource(loop = false): AudioBufferSourceNode {
    const s = this.ctx!.createBufferSource();
    s.buffer = this.noise;
    s.loop = loop;
    if (loop) s.loopStart = Math.random();
    return s;
  }

  // ── voices ───────────────────────────────────────────────────────────────────────────────────────────────────
  private acquireChannel(bus: BusId): Channel | null {
    const ctx = this.ctx!;
    let ch = this.channels.find(c => !c.busy);
    if (ch) this.stats.channelReuses++;
    else {
      if (this.channels.length >= (this.opts.maxChannels ?? 32)) return null;
      const gain = ctx.createGain();
      const pan = ctx.createStereoPanner ? ctx.createStereoPanner() : null;
      if (pan) gain.connect(pan);
      ch = { gain, pan, out: pan ?? gain, bus: null, busy: false };
      this.channels.push(ch);
      this.stats.channelsCreated++;
    }
    if (ch.bus !== bus) {
      try { ch.out.disconnect(); } catch { /* not connected yet */ }
      ch.out.connect(this.busNodes.get(bus)!);
      ch.bus = bus;
    }
    ch.busy = true;
    return ch;
  }

  start(buffer: unknown, p: VoiceParams, onEnded: () => void): HostVoice | null {
    const ctx = this.ctx;
    if (!ctx || ctx.state !== 'running') return null;
    const ch = this.acquireChannel(p.bus);
    if (!ch) return null;
    const t = ctx.currentTime;
    const src = ctx.createBufferSource();
    src.buffer = buffer as AudioBuffer;
    src.loop = p.loop;
    src.playbackRate.value = p.pitch;
    ch.gain.gain.cancelScheduledValues(t);
    ch.gain.gain.setValueAtTime(0, t);
    ch.gain.gain.linearRampToValueAtTime(Math.max(0, p.gain), t + 0.002);          // 2 ms attack ramp: no click even for a loud start
    ch.pan?.pan.cancelScheduledValues(t);
    ch.pan?.pan.setValueAtTime(p.pan, t);
    src.connect(ch.gain);
    let done = false;
    const end = (): void => {
      if (done) return;
      done = true;
      try { src.disconnect(); } catch { /* already */ }
      ch.busy = false;
      onEnded();
    };
    src.onended = end;
    src.start(t, p.offset ?? 0);
    if (p.maxDuration !== undefined && !p.loop) {
      const fade = Math.min(p.fadeOut ?? 0.1, p.maxDuration), stopAt = t + p.maxDuration;
      ch.gain.gain.setValueAtTime(Math.max(0, p.gain), Math.max(t + 0.003, stopAt - fade));
      ch.gain.gain.linearRampToValueAtTime(0, stopAt);
      src.stop(stopAt + 0.01);
    }
    this.stats.voicesStarted++;
    return {
      set: (q, ramp = 0.03) => {
        const n = ctx.currentTime, tc = Math.max(0.001, ramp / 3);
        if (q.gain !== undefined) ch.gain.gain.setTargetAtTime(Math.max(0, q.gain), n, tc);
        if (q.pitch !== undefined) src.playbackRate.setTargetAtTime(q.pitch, n, tc);
        if (q.pan !== undefined) ch.pan?.pan.setTargetAtTime(q.pan, n, tc);
      },
      stop: (fade = 0) => {
        if (done) return;
        const n = ctx.currentTime;
        try {
          if (fade <= 0.002) { src.stop(); return; }
          ch.gain.gain.cancelScheduledValues(n);
          ch.gain.gain.setTargetAtTime(0, n, fade / 4);
          src.stop(n + fade + 0.02);
        } catch { end(); }
      },
    };
  }
}
