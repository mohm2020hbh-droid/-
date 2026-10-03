import type { AudioManager } from './AudioManager';
import type { WorldTheme } from '../data/worlds';

/**
 * MusicManager — original generative music (pad + arpeggio + bass + light percussion) in the world's scale.
 * Gameplay uses OCCASIONAL phrases with silences between them: the source analysis notes that the original's music "does not
 * play as a continuous track but appears at intervals" (PDF p.15, grade B), leaving room for ambience.
 */
const SCALES: Record<WorldTheme['musicScale'], number[]> = {
  pentatonicMajor: [0, 2, 4, 7, 9],
  dorian: [0, 2, 3, 5, 7, 9, 10],
  phrygian: [0, 1, 3, 5, 7, 8, 10],
  lydian: [0, 2, 4, 6, 7, 9, 11],
};
const ROOTS: Record<string, number> = { pentatonicMajor: 261.63, dorian: 293.66, phrygian: 329.63, lydian: 369.99 };
const PROG = [0, 5, 3, 4]; // scale degrees (indices) per bar

export type MusicMode = 'menu' | 'game';

export class MusicManager {
  private timer = 0;
  private scale: number[] = SCALES.pentatonicMajor;
  private root = 261.63;
  private tempo = 92;
  private mode: MusicMode = 'menu';
  private step = 0;
  private nextT = 0;
  private playing = false;
  private out: GainNode | null = null;
  private intensity = 0.3;
  private phraseBars = 0;
  private restUntil = 0;

  constructor(private readonly A: AudioManager) {}

  private freq(deg: number, oct = 0): number {
    const n = this.scale.length;
    const idx = ((deg % n) + n) % n, o = Math.floor(deg / n) + oct;
    return this.root * Math.pow(2, (this.scale[idx] + 12 * o) / 12);
  }

  start(theme: WorldTheme, mode: MusicMode): void {
    this.stop(0.4);
    if (!this.A.ctx) return;
    this.scale = SCALES[theme.musicScale]; this.root = ROOTS[theme.musicScale]; this.mode = mode;
    this.tempo = mode === 'menu' ? 84 : 96;
    const c = this.A.ctx;
    this.out = c.createGain(); this.out.gain.value = 0.0001; this.out.connect(this.A.buses.music);
    this.out.gain.setTargetAtTime(1, c.currentTime, 0.8);
    this.step = 0; this.nextT = c.currentTime + 0.15; this.playing = true; this.phraseBars = 0; this.restUntil = 0;
    this.timer = window.setInterval(() => this.schedule(), 100);
  }

  stop(fade = 1.2): void {
    window.clearInterval(this.timer); this.timer = 0;
    this.playing = false;
    const c = this.A.ctx;
    if (this.out && c) { const o = this.out; o.gain.setTargetAtTime(0.0001, c.currentTime, fade / 3); window.setTimeout(() => { try { o.disconnect(); } catch { /* ignore */ } }, fade * 1000 + 200); }
    this.out = null;
  }

  setIntensity(v: number): void { this.intensity = Math.max(0, Math.min(1, v)); }

  private schedule(): void {
    const c = this.A.ctx;
    if (!c || !this.playing || !this.out || c.state !== 'running') { if (c) this.nextT = Math.max(this.nextT, c.currentTime + 0.05); return; }
    const sixteenth = 60 / this.tempo / 4;
    while (this.nextT < c.currentTime + 0.4) {
      if (this.mode === 'game' && this.restUntil > this.nextT) { this.nextT += sixteenth * 16; this.step = 0; continue; }
      this.playStep(this.step, this.nextT, sixteenth);
      this.step++;
      if (this.step % 16 === 0) {
        this.phraseBars++;
        if (this.mode === 'game' && this.phraseBars >= 24) { this.phraseBars = 0; this.restUntil = this.nextT + sixteenth * 16 * (3 + Math.floor(Math.random() * 6)); }
      }
      this.nextT += sixteenth;
    }
  }

  private tone(type: OscillatorType, f: number, t: number, dur: number, gain: number, lp = 3000, attack = 0.01): void {
    const c = this.A.ctx!, o = c.createOscillator(), g = c.createGain(), fl = c.createBiquadFilter();
    o.type = type; o.frequency.value = f; fl.type = 'lowpass'; fl.frequency.value = lp;
    g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(gain, t + attack); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(fl); fl.connect(g); g.connect(this.out!); o.start(t); o.stop(t + dur + 0.05);
  }

  private playStep(step: number, t: number, s16: number): void {
    const bar = Math.floor(step / 16) % PROG.length, pos = step % 16;
    const root = PROG[bar];
    const chord = [root, root + 2, root + 4];
    if (pos === 0) {
      // pad: two detuned triangles per chord tone, long attack
      for (const d of chord) { const f = this.freq(d, -1); this.tone('triangle', f, t, s16 * 16.5, 0.05, 1100, 0.5); this.tone('triangle', f * 1.004, t, s16 * 16.5, 0.04, 1000, 0.6); }
      this.tone('sine', this.freq(root, -2), t, s16 * 8, 0.16, 400, 0.02); // bass
    }
    if (pos === 8) this.tone('sine', this.freq(root, -2), t, s16 * 7, 0.12, 400, 0.02);
    // arpeggio (eighths), gentle accents
    if (pos % 2 === 0) {
      const seq = [0, 1, 2, 1, 2, 3, 2, 1];
      const idx = seq[(pos / 2) % 8];
      const deg = chord[idx % 3] + (idx >= 3 ? 5 : 0);
      const vel = (pos % 4 === 0 ? 0.075 : 0.05) * (0.5 + this.intensity);
      this.tone('triangle', this.freq(deg, 0), t, s16 * 3.5, vel, 2600, 0.004);
      if (this.mode === 'menu' && pos === 6) this.tone('sine', this.freq(chord[2], 1), t, s16 * 6, 0.04, 3500, 0.01);
    }
    // soft percussion layer (more in gameplay when the player moves fast)
    if (this.intensity > 0.25 || this.mode === 'game') {
      if (pos === 0 || pos === 8) this.kick(t, 0.2 * this.intensity + 0.06);
      if (pos % 4 === 2) this.hat(t, 0.03 + 0.03 * this.intensity);
    }
  }

  private kick(t: number, g: number): void {
    const c = this.A.ctx!, o = c.createOscillator(), gg = c.createGain();
    o.frequency.setValueAtTime(130, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.12);
    gg.gain.setValueAtTime(g, t); gg.gain.exponentialRampToValueAtTime(0.0001, t + 0.16);
    o.connect(gg); gg.connect(this.out!); o.start(t); o.stop(t + 0.2);
  }
  private hat(t: number, g: number): void {
    const c = this.A.ctx!, s = this.A.noiseSource(), f = c.createBiquadFilter(), gg = c.createGain();
    f.type = 'highpass'; f.frequency.value = 7500;
    gg.gain.setValueAtTime(g, t); gg.gain.exponentialRampToValueAtTime(0.0001, t + 0.05);
    s.connect(f); f.connect(gg); gg.connect(this.out!); s.start(t, Math.random(), 0.06);
  }
}
