import { NEUTRAL_INPUT, type PogoInput } from '../sim/PogoState';

/** Anything that can produce one PogoInput per simulation tick (touch schemes, scripted QA input, debug keyboard). */
export interface InputSource {
  /** Called once per sim tick (120 Hz). Must return a fresh-or-reused PogoInput; edge pulses are consumed here. */
  sample(): PogoInput;
  /** Smoothed tilt currently shown in UI/animation (−1..1). */
  readonly tilt: number;
  dispose(): void;
}

/** Programmatic input used by QA automation (window.__pogo) and tests. */
export class ScriptedInput implements InputSource {
  private cur: PogoInput = { ...NEUTRAL_INPUT };
  private pulseBoost = false;
  private pulseCancel = false;
  set(p: Partial<PogoInput>): void {
    if (p.boostPressed) this.pulseBoost = true;
    if (p.cancel) this.pulseCancel = true;
    this.cur = { ...this.cur, ...p, boostPressed: false, cancel: false };
  }
  sample(): PogoInput {
    const out = { ...this.cur, boostPressed: this.pulseBoost, cancel: this.pulseCancel };
    this.pulseBoost = false; this.pulseCancel = false;
    return out;
  }
  get tilt(): number { return this.cur.tilt; }
  dispose(): void { /* nothing */ }
}

/** Plays back a recorded per-tick input script (QA / E2E replays). */
export class ScriptInput implements InputSource {
  private i = 0;
  tilt = 0;
  constructor(private readonly frames: readonly PogoInput[]) {}
  get done(): boolean { return this.i >= this.frames.length; }
  sample(): PogoInput {
    const f = this.frames[Math.min(this.i, this.frames.length - 1)];
    if (this.i < this.frames.length) this.i++;
    this.tilt = f.tilt;
    return this.i >= this.frames.length ? { ...NEUTRAL_INPUT } : f;
  }
  dispose(): void { /* nothing */ }
}
