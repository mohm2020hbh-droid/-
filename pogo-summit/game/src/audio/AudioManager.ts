import type { AudioSettings } from '../progression/settings';
import { AUDIO_CONFIG } from './system/audioConfig';
import { AUDIO_EVENTS, type AudioEventDef } from './system/AudioEvent';
import { AudioPool, type PoolVoice } from './system/AudioPool';
import { AudioZoneSet } from './system/AudioZone';
import { BusGraph } from './system/AudioBus';
import { originalSpatial, radiusSpatial, type Listener } from './system/AudioEmitter';
import { SoundBank, type SampleLoader, type SampleManifest } from './system/SoundBank';
import { VariantSelector, type AudioVariantDef } from './system/AudioVariant';
import { WebAudioHost } from './system/WebAudioHost';
import { mathRng } from './system/rng';
import { WARM_ORDER } from './system/synth/recipes';
import type { AudioEventContext, AudioEventId, AudioHost, EmitResult, HostVoice, LegacySfxPlayer, Rng, SkipReason } from './system/types';

/** A looping voice owned by a caller (the ice slide). `set` ramps gain / pitch / pan; `stop` fades out and frees the voice. */
export interface AudioLoop {
  readonly event: AudioEventId;
  readonly active: boolean;
  set(p: { gain?: number; pitch?: number; pan?: number }, rampSec?: number): void;
  stop(fadeSec?: number): void;
}

export interface AudioManagerStats {
  emitted: number; played: number;
  skipped: Record<SkipReason, number>;
  perEvent: Partial<Record<AudioEventId, { played: number; skipped: number }>>;
}

interface EventState { lastPlay: number; recent: number[]; lastGain: number }

const emptyResult = (reason: SkipReason): EmitResult => ({ played: false, reason, variants: [], gain: 0, pitch: 1 });
const MIN_AUDIBLE = 0.003;

/** Drop non-finite numbers from a caller's context (NaN must never reach a gain), clamp gains to ≥ 0. */
function cleanContext(c: AudioEventContext): AudioEventContext {
  const fin = (v: number | undefined): number | undefined => (v !== undefined && Number.isFinite(v) ? v : undefined);
  return { ...c, intensity: fin(c.intensity), x: fin(c.x), y: fin(c.y), pan: fin(c.pan), radius: fin(c.radius), offset01: fin(c.offset01),
    gain: fin(c.gain) === undefined ? undefined : Math.max(0, c.gain!), pitch: fin(c.pitch) === undefined ? undefined : Math.max(0.1, c.pitch!) };
}

/**
 * AudioManager — the orchestrator of the audio system (POGOSTUCK_AUDIO_SYSTEM_SPEC.md).
 *
 *   emit(event, context) → catalogue entry → gates (cooldown, burst, intensity) → variant selection → gain / pitch / pan
 *                        → voice pool (priority, caps, stealing) → host voice on the event's bus
 *
 * It also keeps the surface the older modules rely on (`ctx`, `buses.sfx/music/ambient`, `unlock`, `apply`, `noiseSource`…), so
 * `SFXManager`, `MusicManager`, `AmbientManager` and `MapAudio` are unchanged. All logic is pure over an `AudioHost`; the real
 * host is `WebAudioHost`, tests use a fake. Missing audio never throws: every failure is an `EmitResult` with a reason.
 */
export class AudioManager {
  readonly host: AudioHost;
  readonly mixer = new BusGraph();
  readonly pool: AudioPool;
  readonly bank: SoundBank;
  readonly zones = new AudioZoneSet();
  readonly selector: VariantSelector;
  readonly stats: AudioManagerStats = { emitted: 0, played: 0, skipped: { 'unknown-event': 0, 'not-ready': 0, cooldown: 0, burst: 0, inaudible: 0, missing: 0, 'voice-limit': 0, disabled: 0 }, perEvent: {} };
  settings: AudioSettings = { master: 0.8, music: 0.55, sfx: 0.9, ambient: 0.6 };
  listener: Listener = { x: 0, y: 0, halfWidth: AUDIO_CONFIG.spatial.defaultHalfWidth };
  /** The old procedural player; variants of kind `legacy` go through it. */
  legacy: LegacySfxPlayer | null = null;
  /** Set false to silence every event (tests, a future "mute" option). */
  enabled = true;
  onUnlock?: () => void;
  private readonly rng: Rng;
  private readonly state = new Map<AudioEventId, EventState>();
  private readonly loops = new Map<AudioEventId, AudioLoopImpl>();
  private warmTimer = 0;

  constructor(host?: AudioHost, o: { rng?: Rng; lowQuality?: boolean; events?: Readonly<Record<AudioEventId, AudioEventDef>>; bank?: SoundBank } = {}) {
    this.host = host ?? new WebAudioHost();
    this.rng = o.rng ?? mathRng;
    this.events = o.events ?? AUDIO_EVENTS;
    this.pool = new AudioPool(o.lowQuality ? AUDIO_CONFIG.pool.capacityLow : AUDIO_CONFIG.pool.capacity, AUDIO_CONFIG.pool.stealFadeSec);
    this.bank = o.bank ?? new SoundBank(this.host);
    this.selector = new VariantSelector(this.rng);
    this.mixer.onChange = (bus, g) => this.host.setBusGain(bus, g);
    this.mixer.applySettings(this.settings);
    if (this.host instanceof WebAudioHost) this.host.onUnlock = () => { this.mixer.flush(); this.onUnlock?.(); this.scheduleWarmUp(); };
  }

  readonly events: Readonly<Record<AudioEventId, AudioEventDef>>;

  // ── the surface of the earlier AudioManager (kept so SFXManager / MusicManager / AmbientManager / MapAudio need no change) ──
  get ctx(): AudioContext | null { return this.host instanceof WebAudioHost ? this.host.ctx : null; }
  get master(): GainNode { return (this.host as WebAudioHost).master; }
  get buses(): { sfx: GainNode; music: GainNode; ambient: GainNode } { return (this.host as WebAudioHost).buses; }
  get unlocked(): boolean { return this.host instanceof WebAudioHost ? this.host.unlocked : this.host.ready; }
  get now(): number { return this.host.now; }
  get ready(): boolean { return this.host.ready; }
  /** Call from a user gesture. Safe to call repeatedly. */
  unlock(): void { this.host.unlock(); }
  suspend(): void { this.host.suspend(); }
  resume(): void { this.host.resume(); }
  noiseSource(loop = false): AudioBufferSourceNode { return (this.host as WebAudioHost).noiseSource(loop); }

  /** Push the user's sliders into the bus graph. */
  apply(s: AudioSettings = this.settings): void {
    this.settings = s;
    this.mixer.applySettings(s);
  }

  // ── events ────────────────────────────────────────────────────────────────────────────────────────────────────
  private st(id: AudioEventId): EventState {
    let s = this.state.get(id);
    if (!s) { s = { lastPlay: -1e9, recent: [], lastGain: 1 }; this.state.set(id, s); }
    return s;
  }

  private note(id: AudioEventId, played: boolean, reason?: SkipReason): void {
    const e = (this.stats.perEvent[id] ??= { played: 0, skipped: 0 });
    if (played) { e.played++; this.stats.played++; } else { e.skipped++; if (reason) this.stats.skipped[reason]++; }
  }

  private fail(id: AudioEventId, reason: SkipReason): EmitResult { this.note(id, false, reason); return emptyResult(reason); }

  /** Where an event's spatial gain / pan come from, if it has a position. */
  private spatialOf(def: AudioEventDef, c: AudioEventContext): { gain: number; pan: number } {
    if (def.spatial !== 'positional' || c.x === undefined || c.y === undefined) return { gain: 1, pan: c.pan ?? 0 };
    const dx = c.x - this.listener.x, dy = c.y - this.listener.y;
    const s = c.radius !== undefined || def.radius !== undefined ? radiusSpatial(dx, dy, c.radius ?? def.radius!) : originalSpatial(dx, dy, this.listener.halfWidth, def.range ?? 1);
    return { gain: s.gain, pan: c.pan ?? s.pan };
  }

  /** Make a one-shot (or a layered group) for `id`. Never throws; returns what happened. */
  emit(id: AudioEventId, ctx: AudioEventContext = {}): EmitResult {
    this.stats.emitted++;
    const c = cleanContext(ctx);
    const def = this.events[id];
    if (!def || def.kind === 'loop') return this.fail(id, 'unknown-event');          // loop events are started with `startLoop`
    if (!this.enabled) return this.fail(id, 'disabled');
    if (!this.host.ready) return this.fail(id, 'not-ready');
    const now = this.host.now, st = this.st(id);
    this.pool.expire(now);
    if (def.cooldownMs > 0 && (now - st.lastPlay) * 1000 < def.cooldownMs) return this.fail(id, 'cooldown');
    if (def.burst) {
      const horizon = now - def.burst.windowMs / 1000;
      st.recent = st.recent.filter(t => t > horizon);
      if (st.recent.length >= def.burst.max) return this.fail(id, 'burst');
    }
    if (def.minIntensity !== undefined && (c.intensity ?? 0) < def.minIntensity) return this.fail(id, 'inaudible');
    const sp = this.spatialOf(def, c);
    if (!(sp.gain >= MIN_AUDIBLE)) return this.fail(id, 'inaudible');

    const picks = this.selector.pick(def.variants, { mode: def.select, variantCooldownMs: def.variantCooldownMs, key: id }, c, now);
    if (picks.length === 0) return this.fail(id, 'missing');

    const duck = def.retriggerDuck && (now - st.lastPlay) * 1000 < def.retriggerDuck.windowMs ? def.retriggerDuck.gain : 1;
    // cut the events this one replaces (the charge click at launch)
    for (const s of def.stops ?? []) this.cut(s.event, s.fadeSec);

    const started: string[] = [];
    let firstGain = 0, firstPitch = 1, voiceLimited = false, missing = false;
    const tryVariant = (v: AudioVariantDef): void => {
      const gain = (v.gain ? v.gain(c, this.rng) : def.gain(c, this.rng)) * dbToGain(v.trimDb ?? 0) * (c.gain ?? 1) * duck * sp.gain;
      const pitch = (v.pitch ? v.pitch(c, this.rng) : def.pitch(c, this.rng)) * (c.pitch ?? 1);
      if (!(gain >= MIN_AUDIBLE) || !Number.isFinite(pitch)) return;
      const r = this.startVoice(def, v, c, gain, pitch, sp.pan, false);
      if (r === 'ok') { started.push(v.id); if (started.length === 1) { firstGain = gain; firstPitch = pitch; } }
      else if (r === 'voice-limit') voiceLimited = true;
      else if (r === 'missing') missing = true;
    };
    for (const v of picks) tryVariant(v);
    // a variant that cannot be produced (no recipe, failed sample, host refused) falls back to another eligible one
    if (started.length === 0 && missing && def.select !== 'layers') {
      const tried = new Set(picks.map(p => p.id));
      for (const v of def.variants) {
        if (tried.has(v.id) || (v.when && !v.when(c)) || this.bank.isMissing(v.id)) continue;
        tryVariant(v);
        if (started.length > 0) break;
      }
    }
    if (started.length === 0) return this.fail(id, voiceLimited ? 'voice-limit' : missing ? 'missing' : 'inaudible');
    st.lastPlay = now; st.recent.push(now); st.lastGain = firstGain;
    this.note(id, true);
    return { played: true, variants: started, gain: firstGain, pitch: firstPitch };
  }

  /** Start one voice. */
  private startVoice(def: AudioEventDef, v: AudioVariantDef, c: AudioEventContext, gain: number, pitch: number, pan: number, loop: boolean, offset?: number): 'ok' | 'voice-limit' | 'missing' {
    const now = this.host.now;
    if (v.source.kind === 'legacy') {
      if (!this.legacy) return 'missing';
      // the old procedural player has no handle: the pool voice expires after the variant's duration hint
      const acq = this.pool.acquire({ event: def.id, priority: def.priority, gain, maxPerEvent: def.maxVoices, atCap: def.atCap, endsAt: now + (v.durationHint ?? 0.5) }, now);
      if (!acq.ok) return 'voice-limit';
      const sfx = typeof v.source.sfx === 'function' ? v.source.sfx(c) : v.source.sfx;
      const intensity = v.source.intensity ? v.source.intensity(c) : c.intensity ?? Math.min(1, gain);
      this.legacy.play(sfx, { intensity, pitch });
      return 'ok';
    }
    const recipe = v.source.kind === 'synth' ? v.source.recipe : v.source.recipe ?? v.id;
    const buffer = this.bank.get(recipe, v.id);
    if (!buffer) return 'missing';
    const acq = this.pool.acquire({ event: def.id, priority: def.priority, gain, maxPerEvent: def.maxVoices, atCap: def.atCap }, now);
    if (!acq.ok) return 'voice-limit';
    const voice = acq.voice;
    const trunc = def.truncate && !c.full && !loop ? def.truncate : null;
    const hv = this.host.start(buffer, {
      bus: def.bus, gain: gain * this.bank.sampleGain(v.id), pitch, pan, loop, offset,
      maxDuration: trunc ? trunc.maxSec : undefined, fadeOut: trunc ? trunc.fadeSec : undefined,
    }, () => this.pool.release(voice.id));
    if (!hv) { this.pool.release(voice.id); return 'missing'; }
    voice.stop = (fade: number) => hv.stop(fade);
    return 'ok';
  }

  /** Stop every voice of an event (with a fade). Returns how many. */
  cut(event: AudioEventId, fadeSec = 0.05): number {
    const loop = this.loops.get(event);
    if (loop?.active) loop.stop(fadeSec);
    return this.pool.stopAll(event, fadeSec);
  }

  /** Stop everything (level end, pause). */
  silence(fadeSec = 0.05): void {
    for (const l of [...this.loops.values()]) if (l.active) l.stop(fadeSec);
    this.pool.stopAll(undefined, fadeSec);
  }

  // ── loops ─────────────────────────────────────────────────────────────────────────────────────────────────────
  /**
   * Start a loop (the ice slide). Handle-gated like the original: while a loop of this event is active, the same handle is
   * returned. `c.gain` / `c.pitch` are the initial values; `c.offset01` (0..1) is the start position inside the buffer (random if absent).
   */
  startLoop(id: AudioEventId, ctx: AudioEventContext = {}): AudioLoop | null {
    this.stats.emitted++;
    const c = cleanContext(ctx);
    const def = this.events[id];
    if (!def || def.kind !== 'loop') { this.fail(id, 'unknown-event'); return null; }
    const existing = this.loops.get(id);
    if (existing?.active) return existing;
    if (!this.enabled) { this.fail(id, 'disabled'); return null; }
    if (!this.host.ready) { this.fail(id, 'not-ready'); return null; }
    const picks = this.selector.pick(def.variants, { mode: 'random', key: id }, c, this.host.now);
    const v = picks[0];
    if (!v) { this.fail(id, 'missing'); return null; }
    const buffer = this.bank.get(v.source.kind === 'synth' ? v.source.recipe : v.source.kind === 'sample' ? v.source.recipe ?? v.id : v.id, v.id);
    if (!buffer) { this.fail(id, 'missing'); return null; }
    const gain = c.gain ?? 1, pitch = c.pitch ?? 1;
    const now = this.host.now;
    const acq = this.pool.acquire({ event: id, priority: def.priority, gain, maxPerEvent: def.maxVoices, atCap: def.atCap }, now);
    if (!acq.ok) { this.fail(id, 'voice-limit'); return null; }
    const dur = this.host.durationOf(buffer);
    const offset = (c.offset01 ?? this.rng.next()) * dur * 0.999;          // random start inside the buffer (original: DSB8SetCurrentPosition(random))
    const loop = new AudioLoopImpl(id, this, acq.voice);
    const hv = this.host.start(buffer, { bus: def.bus, gain, pitch, pan: c.pan ?? 0, loop: true, offset }, () => { this.pool.release(acq.voice.id); loop.markEnded(); });
    if (!hv) { this.pool.release(acq.voice.id); this.fail(id, 'missing'); return null; }
    loop.attach(hv);
    acq.voice.stop = (fade: number) => loop.stop(fade);
    this.loops.set(id, loop);
    this.note(id, true);
    return loop;
  }

  /** @internal */
  _loopEnded(id: AudioEventId, l: AudioLoopImpl): void { if (this.loops.get(id) === l) this.loops.delete(id); }

  // ── listener / lifecycle ──────────────────────────────────────────────────────────────────────────────────────
  setListener(x: number, y: number, halfWidth?: number): void {
    this.listener.x = x; this.listener.y = y;
    if (halfWidth !== undefined && halfWidth > 0) this.listener.halfWidth = halfWidth;
  }

  // ── optional sample bank ──────────────────────────────────────────────────────────────────────────────────────
  useSamples(manifest: SampleManifest, loader: SampleLoader): void { this.bank.manifest = manifest; this.bank.loader = loader; }

  // ── warm-up ───────────────────────────────────────────────────────────────────────────────────────────────────
  /** Render the recipes the first seconds of play need, in idle slices (one recipe per slice). */
  scheduleWarmUp(order: readonly string[] = WARM_ORDER): void {
    if (this.warmTimer) return;
    const step = (): void => {
      this.warmTimer = 0;
      const left = this.bank.warmSome(6, order);        // ≈ one cheap recipe per slice
      if (left > 0) this.warmTimer = setTimeout(step, 30) as unknown as number;
    };
    this.warmTimer = setTimeout(step, 50) as unknown as number;
  }

  dispose(): void { if (this.warmTimer) clearTimeout(this.warmTimer); this.warmTimer = 0; this.silence(0); this.bank.clear(); }
}

const dbToGain = (db: number): number => (db === 0 ? 1 : Math.pow(10, db / 20));

class AudioLoopImpl implements AudioLoop {
  private hv: HostVoice | null = null;
  private ended = false;
  constructor(readonly event: AudioEventId, private readonly owner: AudioManager, private readonly voice: PoolVoice) {}
  get active(): boolean { return !this.ended && this.hv !== null; }
  attach(hv: HostVoice): void { this.hv = hv; }
  markEnded(): void { this.ended = true; this.owner._loopEnded(this.event, this); }
  set(p: { gain?: number; pitch?: number; pan?: number }, rampSec = 0.05): void { if (this.active) { this.hv!.set(p, rampSec); if (p.gain !== undefined) this.voice.gain = p.gain; } }
  stop(fadeSec: number = AUDIO_CONFIG.ice.stopFadeSec): void {
    if (!this.active) return;
    this.hv!.stop(fadeSec);
    this.markEnded();                                  // handle is free immediately: a restart gets a new voice while the old one fades
    this.owner.pool.release(this.voice.id);
  }
}
