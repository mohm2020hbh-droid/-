import { AudioManager } from '../../../src/audio/AudioManager';
import { mulberry32 } from '../../../src/audio/system/rng';
import { SoundBank } from '../../../src/audio/system/SoundBank';
import { PogoAudioDirector, type DirectorOptions } from '../../../src/audio/system/PogoAudioDirector';
import type { AudioHost, BusId, HostVoice, VoiceParams } from '../../../src/audio/system/types';
import { makeEvent, type SimEvent, type SimEventType } from '../../../src/sim/events';
import type { SfxId } from '../../../src/audio/AudioEvents';

export interface FakeBuffer { length: number; sampleRate: number; duration: number }

export class FakeVoice implements HostVoice {
  stopped = false; stopFade: number | null = null; stoppedAt = -1;
  sets: { gain?: number; pitch?: number; pan?: number; ramp?: number }[] = [];
  ended = false;
  constructor(readonly host: FakeHost, readonly id: number, readonly buffer: FakeBuffer, readonly params: VoiceParams, readonly startedAt: number, private readonly onEnded: () => void) {}
  get endsAt(): number {
    const d = this.buffer.duration / Math.max(0.01, this.params.pitch);
    return this.params.loop ? Infinity : this.startedAt + Math.min(d, this.params.maxDuration ?? Infinity);
  }
  set(p: { gain?: number; pitch?: number; pan?: number }, ramp?: number): void { this.sets.push({ ...p, ramp }); }
  stop(fade = 0): void { if (this.stopped) return; this.stopped = true; this.stopFade = fade; this.stoppedAt = this.host.now; this.finish(); }
  finish(): void { if (this.ended) return; this.ended = true; this.onEnded(); }
  /** the last gain the voice was set to (or started with) */
  get gain(): number { for (let i = this.sets.length - 1; i >= 0; i--) if (this.sets[i].gain !== undefined) return this.sets[i].gain!; return this.params.gain; }
  get pitch(): number { for (let i = this.sets.length - 1; i >= 0; i--) if (this.sets[i].pitch !== undefined) return this.sets[i].pitch!; return this.params.pitch; }
}

/** A platform for the audio system with a controllable clock: records every voice, ends them when time passes. */
export class FakeHost implements AudioHost {
  ready = true;
  now = 0;
  sampleRate = 44100;
  started: FakeVoice[] = [];
  busGains = new Map<BusId, number>();
  unlocks = 0; suspends = 0; resumes = 0;
  canStart = true;
  /** bytes → buffer for sample-bank tests; reject to simulate a bad file */
  decodeImpl: (bytes: ArrayBuffer) => Promise<unknown> = async b => ({ length: b.byteLength, sampleRate: 44100, duration: b.byteLength / 44100 });
  private nextId = 1;
  decode(bytes: ArrayBuffer): Promise<unknown> { return this.decodeImpl(bytes); }
  unlock(): void { this.unlocks++; }
  suspend(): void { this.suspends++; }
  resume(): void { this.resumes++; }
  makeBuffer(data: Float32Array, sampleRate: number): FakeBuffer { return { length: data.length, sampleRate, duration: data.length / sampleRate }; }
  durationOf(b: unknown): number { return (b as FakeBuffer).duration; }
  setBusGain(bus: BusId, g: number): void { this.busGains.set(bus, g); }
  start(buffer: unknown, p: VoiceParams, onEnded: () => void): HostVoice | null {
    if (!this.canStart) return null;
    const v = new FakeVoice(this, this.nextId++, buffer as FakeBuffer, { ...p }, this.now, onEnded);
    this.started.push(v);
    return v;
  }
  /** Move the clock; voices whose natural end has passed end (and free their pool slot). */
  advance(sec: number): void {
    this.now += sec;
    for (const v of this.started) if (!v.ended && !v.stopped && v.endsAt <= this.now) v.finish();
  }
  /** voices started so far for a bus */
  on(bus: BusId): FakeVoice[] { return this.started.filter(v => v.params.bus === bus); }
  live(): FakeVoice[] { return this.started.filter(v => !v.ended); }
}

export interface Rig { host: FakeHost; audio: AudioManager; legacy: { id: SfxId; o?: { intensity?: number; pitch?: number } }[] }

let shared: SoundBank | null = null;
/** One bank per test file: recipes render once (FakeHost buffers are plain objects, so any host can use them). */
export const sharedBank = (): SoundBank => (shared ??= new SoundBank(new FakeHost()));

/** Manager over a fake host, seeded RNG, legacy player recorded. */
export function makeRig(seed = 7, o: { lowQuality?: boolean; ownBank?: boolean } = {}): Rig {
  const host = new FakeHost();
  const audio = new AudioManager(host, { rng: mulberry32(seed), lowQuality: o.lowQuality, bank: o.ownBank ? undefined : sharedBank() });
  const legacy: Rig['legacy'] = [];
  audio.legacy = { play: (id, opts) => { legacy.push({ id, o: opts }); } };
  return { host, audio, legacy };
}

export function makeDirector(rig: Rig, o: DirectorOptions = {}): PogoAudioDirector {
  return new PogoAudioDirector(rig.audio, { rng: mulberry32(99), ...o });
}

/** A simulation event with sensible defaults. */
export const simEv = (type: SimEventType, o: Partial<SimEvent> = {}): SimEvent =>
  ({ ...makeEvent(type, o.tick ?? 1, o.x ?? 0, o.y ?? 0, o.intensity ?? 0.5, o.nx ?? 0, o.ny ?? 1, o.surface, o.material, o.collider), ...o });
