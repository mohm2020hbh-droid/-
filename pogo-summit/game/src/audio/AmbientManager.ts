import type { AudioManager } from './AudioManager';
import type { WorldTheme } from '../data/worlds';

/** AmbientManager — wind (louder with height), birds, water near waterfalls, lava crackle, snow whistle. All procedural. */
export class AmbientManager {
  private nodes: AudioNode[] = [];
  private wind: { g: GainNode; f: BiquadFilterNode } | null = null;
  private water: GainNode | null = null;
  private timer = 0;
  private theme: WorldTheme | null = null;
  private height01 = 0;
  private active = false;

  constructor(private readonly A: AudioManager) {}

  start(theme: WorldTheme): void {
    this.stop();
    const c = this.A.ctx;
    if (!c) return;
    this.theme = theme; this.active = true;
    const out = this.A.buses.ambient;
    // wind: looping noise through a slowly modulated band-pass
    const src = this.A.noiseSource(true), f = c.createBiquadFilter(), g = c.createGain();
    f.type = 'bandpass'; f.frequency.value = theme.ambience.id === 'snow' ? 900 : 500; f.Q.value = 0.7; g.gain.value = 0.0001;
    src.connect(f); f.connect(g); g.connect(out); src.start();
    const lfo = c.createOscillator(), lg = c.createGain(); lfo.frequency.value = 0.11; lg.gain.value = 260; lfo.connect(lg); lg.connect(f.frequency); lfo.start();
    this.wind = { g, f }; this.nodes.push(src, f, g, lfo, lg);
    // water (only for themes with water)
    if (theme.ambience.water > 0) {
      const ws = this.A.noiseSource(true), wf = c.createBiquadFilter(), wg = c.createGain();
      wf.type = 'lowpass'; wf.frequency.value = 1800; wg.gain.value = 0.0001;
      ws.connect(wf); wf.connect(wg); wg.connect(out); ws.start();
      this.water = wg; this.nodes.push(ws, wf, wg);
    }
    this.timer = window.setInterval(() => this.tick(), 1800);
  }

  stop(): void {
    window.clearInterval(this.timer); this.timer = 0; this.active = false;
    const c = this.A.ctx;
    for (const n of this.nodes) { try { (n as OscillatorNode).stop?.(); } catch { /* ignore */ } try { n.disconnect(); } catch { /* ignore */ } }
    this.nodes = []; this.wind = null; this.water = null;
    void c;
  }

  /** height01: 0 at the start … 1 at the top; waterNear: 0..1. Cheap, call ~10×/s. */
  update(height01: number, waterNear: number): void {
    const c = this.A.ctx;
    if (!c || !this.theme) return;
    this.height01 = height01;
    const t = c.currentTime, a = this.theme.ambience;
    this.wind?.g.gain.setTargetAtTime((0.04 + 0.14 * a.wind) * (0.5 + height01), t, 0.5);
    this.water?.gain.setTargetAtTime(0.0001 + 0.12 * a.water * waterNear, t, 0.4);
  }

  private tick(): void {
    const c = this.A.ctx, th = this.theme;
    if (!c || !th || !this.active || c.state !== 'running') return;
    const a = th.ambience;
    if (a.birds > 0 && Math.random() < 0.45 * a.birds * (1 - this.height01 * 0.7)) this.chirp(c);
    if (a.id === 'lava' && Math.random() < 0.7) this.crackle(c);
  }

  private chirp(c: AudioContext): void {
    const t = c.currentTime + Math.random() * 0.6, n = 2 + Math.floor(Math.random() * 3), base = 2200 + Math.random() * 1800;
    const pan = c.createStereoPanner ? c.createStereoPanner() : null;
    if (pan) { pan.pan.value = Math.random() * 1.6 - 0.8; pan.connect(this.A.buses.ambient); }
    for (let i = 0; i < n; i++) {
      const o = c.createOscillator(), g = c.createGain(), s = t + i * 0.11;
      o.frequency.setValueAtTime(base, s); o.frequency.exponentialRampToValueAtTime(base * (1.25 + Math.random() * 0.4), s + 0.07);
      g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(0.025, s + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, s + 0.09);
      o.connect(g); g.connect(pan ?? this.A.buses.ambient); o.start(s); o.stop(s + 0.12);
    }
  }

  private crackle(c: AudioContext): void {
    const s = this.A.noiseSource(), f = c.createBiquadFilter(), g = c.createGain(), t = c.currentTime + Math.random() * 1.2;
    f.type = 'bandpass'; f.frequency.value = 1500 + Math.random() * 2500; f.Q.value = 3;
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.05, t + 0.004); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.07);
    s.connect(f); f.connect(g); g.connect(this.A.buses.ambient); s.start(t, Math.random(), 0.1);
  }
}
