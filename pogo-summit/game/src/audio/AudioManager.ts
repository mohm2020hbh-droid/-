import type { AudioSettings } from '../progression/settings';

/**
 * AudioManager — owns the WebAudio graph:   sfx ─┐
 *                                            music ─┼─► master ─► compressor ─► destination
 *                                          ambient ─┘
 * All sound is generated procedurally (no Pogostuck audio, no external files). The context is created lazily on the first
 * user gesture (browser/WebView autoplay policy) and suspended when the app is hidden.
 */
export class AudioManager {
  ctx: AudioContext | null = null;
  master!: GainNode;
  readonly buses = {} as { sfx: GainNode; music: GainNode; ambient: GainNode };
  private noise: AudioBuffer | null = null;
  settings: AudioSettings = { master: 0.8, music: 0.55, sfx: 0.9, ambient: 0.6 };
  unlocked = false;
  onUnlock?: () => void;

  constructor() {
    document.addEventListener('visibilitychange', () => { if (document.hidden) this.suspend(); else this.resume(); });
  }

  /** Call from a user gesture. Safe to call repeatedly. */
  unlock(): void {
    if (this.ctx) { void this.resume(); return; }
    try {
      const AC = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AC) return;
      const ctx = new AC({ latencyHint: 'interactive' });
      this.ctx = ctx;
      this.master = ctx.createGain();
      const comp = ctx.createDynamicsCompressor();
      comp.threshold.value = -14; comp.knee.value = 18; comp.ratio.value = 4; comp.attack.value = 0.004; comp.release.value = 0.2;
      this.master.connect(comp); comp.connect(ctx.destination);
      for (const k of ['sfx', 'music', 'ambient'] as const) { const g = ctx.createGain(); g.connect(this.master); this.buses[k] = g; }
      this.apply();
      // shared white-noise buffer (2 s)
      const nb = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
      const d = nb.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
      this.noise = nb;
      void ctx.resume().then(() => { this.unlocked = ctx.state === 'running'; if (this.unlocked) this.onUnlock?.(); });
    } catch { /* audio is optional */ }
  }

  apply(s: AudioSettings = this.settings): void {
    this.settings = s;
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    this.master.gain.setTargetAtTime(s.master, t, 0.03);
    this.buses.sfx.gain.setTargetAtTime(s.sfx, t, 0.03);
    this.buses.music.gain.setTargetAtTime(s.music, t, 0.05);
    this.buses.ambient.gain.setTargetAtTime(s.ambient, t, 0.05);
  }

  get now(): number { return this.ctx ? this.ctx.currentTime : 0; }
  get ready(): boolean { return !!this.ctx && this.ctx.state === 'running'; }
  suspend(): void { void this.ctx?.suspend(); }
  resume(): void { if (this.ctx && this.ctx.state !== 'running') void this.ctx.resume(); }

  noiseSource(loop = false): AudioBufferSourceNode {
    const s = this.ctx!.createBufferSource();
    s.buffer = this.noise;
    s.loop = loop;
    if (loop) s.loopStart = Math.random();
    return s;
  }
}
