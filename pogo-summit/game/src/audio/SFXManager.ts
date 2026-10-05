import type { AudioManager } from './AudioManager';
import { SFX, type SfxId } from './AudioEvents';

/**
 * SFXManager — procedural effects. Timing references from the sources (PDF p.3/p.15, XLSX F-036..F-042) are used only as
 * *durations of design intent* (pogoLoad 0.105 s, pogoLaunch 0.18 s, boost 0.62 s …) — the sounds themselves are original.
 */
export class SFXManager {
  private last = new Map<string, number>();
  private chargeNodes: { o: OscillatorNode; f: BiquadFilterNode; g: GainNode } | null = null;
  constructor(private readonly A: AudioManager) {}

  private osc(type: OscillatorType, f0: number, f1: number, t0: number, dur: number, gain: number, out: AudioNode, attack = 0.004, curve: 'exp' | 'lin' = 'exp'): OscillatorNode {
    const c = this.A.ctx!;
    const o = c.createOscillator(), g = c.createGain();
    o.type = type;
    o.frequency.setValueAtTime(Math.max(20, f0), t0);
    if (f1 !== f0) (curve === 'exp' ? o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t0 + dur) : o.frequency.linearRampToValueAtTime(f1, t0 + dur));
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(Math.max(0.0002, gain), t0 + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g); g.connect(out);
    o.start(t0); o.stop(t0 + dur + 0.05);
    return o;
  }

  private noiseBurst(t0: number, dur: number, gain: number, type: BiquadFilterType, f0: number, f1: number, q: number, out: AudioNode, attack = 0.003): void {
    const c = this.A.ctx!;
    const s = this.A.noiseSource();
    const f = c.createBiquadFilter(), g = c.createGain();
    f.type = type; f.Q.value = q;
    f.frequency.setValueAtTime(f0, t0);
    if (f1 !== f0) f.frequency.exponentialRampToValueAtTime(Math.max(30, f1), t0 + dur);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(Math.max(0.0002, gain), t0 + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    s.connect(f); f.connect(g); g.connect(out);
    s.start(t0, Math.random() * 1.5, dur + 0.05);
  }

  private formant(f0: number, f1: number, center: number, t0: number, dur: number, gain: number, out: AudioNode, vib = 0): void {
    const c = this.A.ctx!;
    const o = c.createOscillator(), bp = c.createBiquadFilter(), g = c.createGain();
    o.type = 'sawtooth';
    o.frequency.setValueAtTime(f0, t0); o.frequency.linearRampToValueAtTime(f1, t0 + dur);
    if (vib > 0) { const l = c.createOscillator(), lg = c.createGain(); l.frequency.value = 9; lg.gain.value = vib; l.connect(lg); lg.connect(o.frequency); l.start(t0); l.stop(t0 + dur + 0.05); }
    bp.type = 'bandpass'; bp.Q.value = 3.2; bp.frequency.setValueAtTime(center * 0.8, t0); bp.frequency.linearRampToValueAtTime(center * 1.25, t0 + dur);
    g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(gain, t0 + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(bp); bp.connect(g); g.connect(out);
    o.start(t0); o.stop(t0 + dur + 0.05);
  }

  play(id: SfxId, o: { intensity?: number; pitch?: number } = {}): void {
    const A = this.A;
    if (!A.ready) return;
    const now = A.now;
    const gap = id === SFX.uiClick ? 0.03 : id === SFX.wallHit ? 0.09 : id === SFX.landSoft || id === SFX.landWood ? 0.05 : id === SFX.splash ? 0.12 : id === SFX.lavaPop || id === SFX.chime ? 0.08 : 0.02;
    if (now - (this.last.get(id) ?? -1) < gap) return;
    this.last.set(id, now);
    const out = A.buses.sfx;
    const k = Math.max(0.05, Math.min(1, o.intensity ?? 0.6));
    const p = (o.pitch ?? 1) * (0.94 + Math.random() * 0.12); // variation: repeated events never sound identical (PDF p.16, grade B)
    const t = now + 0.001;
    switch (id) {
      case SFX.uiClick: this.osc('sine', 880 * p, 1180 * p, t, 0.07, 0.22, out); break;
      case SFX.uiBack: this.osc('sine', 700 * p, 470 * p, t, 0.1, 0.22, out); break;
      case SFX.uiConfirm: this.osc('triangle', 660 * p, 660 * p, t, 0.09, 0.22, out); this.osc('triangle', 990 * p, 990 * p, t + 0.08, 0.16, 0.22, out); break;
      case SFX.uiUnlock: [784, 988, 1319].forEach((f, i) => this.osc('triangle', f, f, t + i * 0.09, 0.3, 0.2, out)); break;
      case SFX.uiStar: this.osc('sine', 800 * p, 1500 * p, t, 0.14, 0.25, out); this.osc('sine', 1600 * p, 1600 * p, t + 0.05, 0.2, 0.1, out); break;
      case SFX.chargeStart: this.noiseBurst(t, 0.105, 0.14, 'highpass', 2500, 4200, 0.8, out); this.osc('square', 140 * p, 200 * p, t, 0.105, 0.05, out); break;
      case SFX.launch: {
        this.osc('triangle', 170 * p, (360 + 360 * k) * p, t, 0.13, 0.32, out);
        this.osc('triangle', (360 + 360 * k) * p, 230 * p, t + 0.12, 0.2, 0.2, out);
        this.noiseBurst(t, 0.18, 0.09 + 0.12 * k, 'bandpass', 700, 2600, 1.2, out);
        break;
      }
      case SFX.landSoft: this.osc('sine', 150 * p, 52, t, 0.15, 0.55 * k, out); this.noiseBurst(t, 0.07, 0.22 * k, 'lowpass', 1200, 400, 0.7, out); break;
      case SFX.landWood: this.osc('triangle', 310 * p, 170 * p, t, 0.09, 0.35 * k, out); this.noiseBurst(t, 0.05, 0.3 * k, 'bandpass', 1200, 900, 1.5, out); this.osc('sine', 120, 55, t, 0.12, 0.3 * k, out); break;
      case SFX.landIce: this.osc('sine', 1900 * p, 1850 * p, t, 0.22, 0.14 * k + 0.05, out); this.osc('sine', 2540 * p, 2500 * p, t, 0.16, 0.1 * k + 0.04, out); this.noiseBurst(t, 0.09, 0.18 * k, 'highpass', 5000, 7000, 0.8, out); this.osc('sine', 160, 70, t, 0.1, 0.25 * k, out); break;
      case SFX.landGoo: {
        const o2 = this.osc('sine', 240 * p, 90, t, 0.22, 0.4 * k, out);
        const c = A.ctx!; const l = c.createOscillator(), lg = c.createGain(); l.frequency.value = 22; lg.gain.value = 40; l.connect(lg); lg.connect(o2.frequency); l.start(t); l.stop(t + 0.25);
        this.noiseBurst(t, 0.12, 0.12 * k, 'bandpass', 500, 300, 2, out);
        break;
      }
      case SFX.hardImpact: this.osc('sine', 95, 32, t, 0.38, 0.85, out); this.noiseBurst(t, 0.3, 0.4, 'lowpass', 900, 200, 0.7, out); this.noiseBurst(t, 0.06, 0.4, 'bandpass', 2600, 1800, 1.4, out); break;
      case SFX.bounce: {
        const o1 = this.osc('sine', 210 * p, 640 * p, t, 0.1, 0.45, out);
        this.osc('sine', 640 * p, 300 * p, t + 0.09, 0.32, 0.38, out);
        const c = A.ctx!; const l = c.createOscillator(), lg = c.createGain(); l.frequency.value = 15; lg.gain.value = 35; l.connect(lg); lg.connect(o1.frequency); l.start(t); l.stop(t + 0.15);
        this.osc('sine', 1280 * p, 1260 * p, t, 0.3, 0.07, out);
        break;
      }
      case SFX.boost: {
        this.noiseBurst(t, 0.55, 0.3, 'bandpass', 380, 3200, 1.1, out, 0.04);
        this.osc('sawtooth', 220 * p, 880 * p, t, 0.55, 0.1, out, 0.06);
        this.osc('triangle', 1320 * p, 1320 * p, t + 0.08, 0.3, 0.16, out); this.osc('triangle', 1760 * p, 1760 * p, t + 0.18, 0.35, 0.14, out);
        break;
      }
      case SFX.boostArmed: [880, 1175, 1568].forEach((f, i) => this.osc('sine', f, f, t + i * 0.06, 0.2, 0.14, out)); break;
      case SFX.boostPad: this.osc('sawtooth', 300 * p, 1200 * p, t, 0.22, 0.12, out); this.noiseBurst(t, 0.2, 0.12, 'highpass', 1500, 5000, 0.8, out); break;
      case SFX.fall: this.osc('sine', 1100, 240, t, 0.85, 0.09, out, 0.05); this.noiseBurst(t, 0.8, 0.07, 'bandpass', 900, 400, 1, out, 0.1); break;
      case SFX.respawn: this.osc('sine', 300, 900, t, 0.4, 0.16, out, 0.05); [1200, 1600].forEach((f, i) => this.osc('sine', f, f, t + 0.15 + i * 0.07, 0.22, 0.08, out)); break;
      case SFX.hazard: this.osc('sawtooth', 150, 90, t, 0.32, 0.28, out); this.noiseBurst(t, 0.28, 0.3, 'bandpass', 1800, 500, 1.2, out); this.osc('sine', 80, 40, t, 0.3, 0.5, out); break;
      case SFX.wallHit: this.osc('sine', 190 * p, 80, t, 0.11, 0.4 * k, out); this.noiseBurst(t, 0.08, 0.2 * k, 'bandpass', 750, 500, 1.2, out); break;
      case SFX.slide: this.noiseBurst(t, 0.2, 0.08, 'bandpass', 1500, 900, 2, out, 0.05); break;
      case SFX.goal: {
        [523, 659, 784, 1046, 1318, 1568].forEach((f, i) => { this.osc('triangle', f, f, t + i * 0.1, 0.5, 0.22, out); this.osc('sine', f * 2, f * 2, t + i * 0.1, 0.35, 0.06, out); });
        this.noiseBurst(t + 0.5, 0.9, 0.08, 'highpass', 5000, 8000, 0.8, out, 0.1);
        break;
      }
      case SFX.splash: this.noiseBurst(t, 0.32, 0.3 * k, 'bandpass', 1400, 500, 0.9, out, 0.01); this.noiseBurst(t + 0.02, 0.18, 0.22 * k, 'highpass', 3000, 6500, 0.8, out); this.osc('sine', 260 * p, 110, t, 0.2, 0.22 * k, out); break;
      case SFX.checkpoint: [659, 880, 1175].forEach((f, i) => { this.osc('triangle', f * p, f * p, t + i * 0.07, 0.34, 0.2, out); this.osc('sine', f * 2, f * 2, t + i * 0.07, 0.22, 0.05, out); }); break;
      case SFX.breakPlatform: this.noiseBurst(t, 0.28, 0.36 * k, 'bandpass', 900, 300, 1.1, out, 0.002); this.osc('square', 180 * p, 70, t, 0.14, 0.12 * k, out); this.osc('sine', 90, 44, t, 0.22, 0.3 * k, out); break;
      case SFX.teleport: this.osc('sine', 300 * p, 1500 * p, t, 0.26, 0.16, out, 0.02); this.osc('triangle', 1500 * p, 380, t + 0.18, 0.3, 0.14, out); this.noiseBurst(t, 0.3, 0.07, 'highpass', 3000, 7000, 0.8, out, 0.05); break;
      case SFX.chime: this.osc('sine', 1318 * p, 1318 * p, t, 0.6, 0.12 * k + 0.04, out, 0.01); this.osc('sine', 1976 * p, 1976 * p, t + 0.03, 0.5, 0.06, out, 0.01); break;
      case SFX.lavaPop: this.osc('sine', 130 * p, 55, t, 0.16, 0.3 * k, out); this.noiseBurst(t, 0.1, 0.18 * k, 'bandpass', 2200, 900, 1.4, out, 0.002); break;
      case SFX.boostZone: this.osc('sawtooth', 240 * p, 1000 * p, t, 0.3, 0.1, out, 0.04); this.noiseBurst(t, 0.3, 0.14, 'bandpass', 600, 3600, 1.1, out, 0.04); break;
      case SFX.voiceHup: this.formant(230 * p, 320 * p, 900, t, 0.13, 0.2 + 0.1 * k, out); break;
      case SFX.voiceOuch: this.formant(340, 170, 750, t, 0.32, 0.26, out, 6); break;
      case SFX.voiceYay: this.formant(300, 460, 1100, t, 0.4, 0.22, out, 14); break;
      default: break;
    }
  }

  /** Looping charge whine whose pitch follows the charge fraction (pogoLoad → pogoLaunch intent, grade B). */
  chargeUpdate(frac: number, active: boolean): void {
    const A = this.A;
    if (!A.ready) return;
    const c = A.ctx!, t = c.currentTime;
    if (!active) { if (this.chargeNodes) { const n = this.chargeNodes; n.g.gain.setTargetAtTime(0.0001, t, 0.03); n.o.stop(t + 0.15); this.chargeNodes = null; } return; }
    if (!this.chargeNodes) {
      const o = c.createOscillator(), f = c.createBiquadFilter(), g = c.createGain();
      o.type = 'sawtooth'; f.type = 'lowpass'; f.Q.value = 4; f.frequency.value = 600; g.gain.value = 0.0001;
      o.connect(f); f.connect(g); g.connect(A.buses.sfx); o.start();
      this.chargeNodes = { o, f, g };
    }
    const n = this.chargeNodes;
    n.o.frequency.setTargetAtTime(150 + 420 * frac, t, 0.03);
    n.f.frequency.setTargetAtTime(500 + 2400 * frac, t, 0.05);
    n.g.gain.setTargetAtTime(0.03 + 0.06 * frac, t, 0.03);
  }
}
