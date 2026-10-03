import type { InputSource } from './InputSource';
import type { PogoInput } from '../sim/PogoState';
import type { ControlSettings } from '../progression/settings';
import { DT } from '../sim/math';

/**
 * TouchControls — Phase 3. Touch ONLY (keyboard exists solely behind ?debug=1 for automated tests).
 *
 *   DRAG scheme (default, as specified):  Touch Down → start charge · Touch Drag → aim (tilt) + optional pull-down power ·
 *                                          Touch Release → launch. Anywhere on the canvas.
 *   PAD scheme (matches the reference image): floating stick (tilt) + Jump/Charge button + Boost button.
 *
 * Robustness: pointer-id tracking (multi-touch safe), pointercancel / lostpointercapture / blur / visibility change ⇒
 * cancel (abort charge, never launch), short taps are latched for ≥ 3 sim ticks so they are never missed.
 */
export interface PadElements {
  stickZone: HTMLElement;
  stickKnob: HTMLElement;
  jumpBtn: HTMLElement;
}

export class TouchControls implements InputSource {
  private settings: ControlSettings;
  private canvas: HTMLElement;
  private boostBtn: HTMLElement | null = null;
  private pad: PadElements | null = null;

  // DRAG state
  private dragId = -1;
  private ax = 0; private ay = 0;
  private dx = 0; private dy = 0;
  private holdTicks = 0;
  private held = false;
  private releasing = false;
  private pullNow = 0;
  private pullLast = 0;
  // PAD state
  private stickId = -1; private sx = 0; private sy = 0; private sdx = 0;
  private jumpId = -1;
  // outputs
  private tiltRaw = 0;
  private tiltSmooth = 0;
  private boostPulse = false;
  private cancelPulse = false;
  private disposers: (() => void)[] = [];
  /** Debug keyboard (only when ?debug=1). */
  private keys = new Set<string>();
  keyboard = false;
  onTouchStart?: () => void;

  constructor(canvas: HTMLElement, settings: ControlSettings) {
    this.canvas = canvas;
    this.settings = settings;
    this.bindCanvas();
    const cancelAll = () => this.cancel();
    this.on(window, 'blur', cancelAll);
    this.on(document, 'visibilitychange', () => { if (document.hidden) cancelAll(); });
  }

  setSettings(s: ControlSettings): void { this.settings = s; }

  private on<K extends keyof HTMLElementEventMap>(el: EventTarget, type: string, fn: (e: never) => void): void {
    el.addEventListener(type, fn as EventListener, { passive: false });
    this.disposers.push(() => el.removeEventListener(type, fn as EventListener));
  }

  attachBoost(el: HTMLElement): void {
    this.boostBtn = el;
    this.on(el, 'pointerdown', (e: PointerEvent) => { e.preventDefault(); e.stopPropagation(); this.boostPulse = true; this.onTouchStart?.(); });
  }

  attachPad(p: PadElements): void {
    this.pad = p;
    this.on(p.stickZone, 'pointerdown', (e: PointerEvent) => {
      e.preventDefault(); e.stopPropagation();
      if (this.stickId !== -1) return;
      this.stickId = e.pointerId; p.stickZone.setPointerCapture(e.pointerId);
      const r = p.stickZone.getBoundingClientRect();
      this.sx = r.left + r.width / 2; this.sy = r.top + r.height / 2; this.sdx = e.clientX - this.sx;
      this.updateKnob(); this.onTouchStart?.();
    });
    this.on(p.stickZone, 'pointermove', (e: PointerEvent) => {
      if (e.pointerId !== this.stickId) return;
      e.preventDefault();
      this.sdx = e.clientX - this.sx; this.updateKnob();
    });
    const upStick = (e: PointerEvent) => {
      if (e.pointerId !== this.stickId) return;
      this.stickId = -1; this.sdx = 0; this.updateKnob();
    };
    this.on(p.stickZone, 'pointerup', upStick);
    this.on(p.stickZone, 'pointercancel', (e: PointerEvent) => { upStick(e); this.cancelPulse = true; });
    this.on(p.jumpBtn, 'pointerdown', (e: PointerEvent) => {
      e.preventDefault(); e.stopPropagation();
      if (this.jumpId !== -1) return;
      this.jumpId = e.pointerId; p.jumpBtn.setPointerCapture(e.pointerId);
      this.pressJump(); p.jumpBtn.classList.add('down'); this.onTouchStart?.();
    });
    const upJump = (cancel: boolean) => (e: PointerEvent) => {
      if (e.pointerId !== this.jumpId) return;
      this.jumpId = -1; p.jumpBtn.classList.remove('down');
      if (cancel) { this.cancel(); } else this.releaseJump();
    };
    this.on(p.jumpBtn, 'pointerup', upJump(false));
    this.on(p.jumpBtn, 'pointercancel', upJump(true));
    this.on(p.jumpBtn, 'lostpointercapture', (e: PointerEvent) => { if (e.pointerId === this.jumpId) upJump(true)(e); });
  }

  private updateKnob(): void {
    if (!this.pad) return;
    const r = this.pad.stickZone.getBoundingClientRect();
    const R = r.width * 0.32;
    const x = Math.max(-R, Math.min(R, this.sdx));
    this.pad.stickKnob.style.transform = `translate(${x}px,0)`;
  }

  // ── DRAG scheme on the canvas ────────────────────────────────────────────
  private bindCanvas(): void {
    const c = this.canvas;
    this.on(c, 'pointerdown', (e: PointerEvent) => {
      if (this.settings.scheme !== 'drag') return;
      e.preventDefault();
      if (this.dragId !== -1) return; // multi-touch safety: only the first finger controls
      this.dragId = e.pointerId;
      c.setPointerCapture?.(e.pointerId);
      this.ax = e.clientX; this.ay = e.clientY; this.dx = 0; this.dy = 0;
      this.pressJump();
      this.onTouchStart?.();
    });
    this.on(c, 'pointermove', (e: PointerEvent) => {
      if (e.pointerId !== this.dragId) return;
      e.preventDefault();
      this.dx = e.clientX - this.ax; this.dy = e.clientY - this.ay;
      // floating anchor: dragging far drags the anchor along so a short thumb travel always reaches full tilt
      const lim = this.settings.swipeDistance * 1.35;
      if (Math.abs(this.dx) > lim) { this.ax += this.dx - Math.sign(this.dx) * lim; this.dx = Math.sign(this.dx) * lim; }
    });
    const up = (cancel: boolean) => (e: PointerEvent) => {
      if (e.pointerId !== this.dragId) return;
      this.dragId = -1;
      if (cancel) this.cancel(); else this.releaseJump();
    };
    this.on(c, 'pointerup', up(false));
    this.on(c, 'pointercancel', up(true));
    this.on(c, 'lostpointercapture', (e: PointerEvent) => { if (e.pointerId === this.dragId) up(true)(e); });
    this.on(c, 'contextmenu', (e: Event) => e.preventDefault());
  }

  private pressJump(): void { this.held = true; this.releasing = false; this.holdTicks = 3; }
  private releaseJump(): void { this.releasing = true; }

  /** Abort: clear everything and make the simulation abandon the charge without launching. */
  cancel(): void {
    this.dragId = -1; this.stickId = -1; this.jumpId = -1; this.sdx = 0; this.dx = 0; this.dy = 0;
    this.held = false; this.releasing = false; this.cancelPulse = true; this.pullNow = 0;
    this.pad?.jumpBtn.classList.remove('down'); this.updateKnob();
  }

  // ── debug keyboard (QA only) ────────────────────────────────────────────
  enableDebugKeyboard(): void {
    this.keyboard = true;
    this.on(window, 'keydown', (e: KeyboardEvent) => { if (e.repeat) return; this.keys.add(e.code); if (e.code === 'Space') this.pressJump(); if (e.code === 'KeyB') this.boostPulse = true; });
    this.on(window, 'keyup', (e: KeyboardEvent) => { this.keys.delete(e.code); if (e.code === 'Space') this.releaseJump(); });
  }

  // ── sampling (called once per sim tick) ─────────────────────────────────
  get tilt(): number { return this.tiltSmooth; }

  private shape(v: number): number {
    const dz = this.settings.deadzone;
    const a = Math.abs(v);
    if (a <= dz) return 0;
    const n = (a - dz) / (1 - dz);
    return Math.sign(v) * Math.pow(n, 1.25);
  }

  sample(): PogoInput {
    const s = this.settings;
    // raw tilt from whichever scheme is active
    let raw = 0;
    if (this.keyboard && (this.keys.has('ArrowLeft') || this.keys.has('ArrowRight'))) raw = (this.keys.has('ArrowRight') ? 1 : 0) - (this.keys.has('ArrowLeft') ? 1 : 0);
    else if (s.scheme === 'drag' && this.dragId !== -1) raw = this.shape((this.dx / s.swipeDistance)) * s.sensitivity;
    else if (s.scheme === 'pad' && this.stickId !== -1 && this.pad) raw = this.shape(this.sdx / (this.pad.stickZone.getBoundingClientRect().width * 0.32)) * s.sensitivity;
    raw = Math.max(-1, Math.min(1, raw));
    // time-constant smoothing at the sim rate (0 = instant)
    const tau = s.smoothing * 0.12;
    this.tiltSmooth = tau <= 0 ? raw : this.tiltSmooth + (raw - this.tiltSmooth) * (1 - Math.exp(-DT / tau));
    if (Math.abs(this.tiltSmooth) < 1e-4) this.tiltSmooth = 0;

    // pull-down gesture → explicit charge floor (DRAG only)
    if (s.scheme === 'drag' && this.dragId !== -1) {
      const pd = (this.dy / s.swipeDistance - 0.35) / 0.65;
      this.pullNow = Math.max(0, Math.min(1, pd)) * s.swipeStrength;
      this.pullLast = this.pullNow;
    } else this.pullNow = 0;

    // jump/charge line: a tap is latched for ≥ 3 ticks; the release is reported on the sample where it takes effect
    let report = this.held;
    let justReleased = false;
    if (this.held && this.releasing && this.holdTicks <= 0) { report = false; justReleased = true; this.held = false; this.releasing = false; }
    else if (this.holdTicks > 0) this.holdTicks--;

    const out: PogoInput = {
      tilt: this.tiltSmooth,
      jumpHeld: report,
      boostPressed: this.boostPulse,
      pull: report ? this.pullNow : justReleased ? this.pullLast : 0,
      cancel: this.cancelPulse,
    };
    if (!report) this.pullLast = 0;
    this.boostPulse = false; this.cancelPulse = false;
    return out;
  }

  /** Current pull gesture for HUD feedback. */
  get pull(): number { return this.pullNow; }
  get isDown(): boolean { return this.held; }

  dispose(): void { this.disposers.forEach(d => d()); this.disposers = []; }
}
