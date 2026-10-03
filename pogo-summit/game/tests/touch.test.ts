// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest';
import { TouchControls } from '../src/input/TouchControls';
import { DEFAULT_SETTINGS, type ControlSettings } from '../src/progression/settings';

/** Dispatch a pointer-like event with the fields TouchControls reads. */
function ptr(el: HTMLElement, type: string, id: number, x = 0, y = 0): void {
  const e = new Event(type, { bubbles: true, cancelable: true });
  Object.assign(e, { pointerId: id, clientX: x, clientY: y });
  el.dispatchEvent(e);
}
const settings = (o: Partial<ControlSettings> = {}): ControlSettings => ({ ...DEFAULT_SETTINGS.control, smoothing: 0, ...o });
function sampleN(c: TouchControls, n: number) { const out = []; for (let i = 0; i < n; i++) out.push(c.sample()); return out; }

describe('TouchControls — DRAG scheme (Touch Down / Drag / Release / Cancel)', () => {
  let canvas: HTMLElement, c: TouchControls;
  beforeEach(() => {
    document.body.innerHTML = '';
    canvas = document.createElement('div'); canvas.setPointerCapture = () => undefined; document.body.append(canvas);
    c = new TouchControls(canvas, settings());
  });

  it('Touch Down starts the charge; Release ends it (falling edge reported exactly once)', () => {
    ptr(canvas, 'pointerdown', 1, 100, 100);
    const held = sampleN(c, 6);
    expect(held.every(s => s.jumpHeld)).toBe(true);
    ptr(canvas, 'pointerup', 1, 100, 100);
    const after = sampleN(c, 3);
    expect(after[0].jumpHeld).toBe(false);
    expect(after.slice(1).every(s => !s.jumpHeld)).toBe(true);
  });

  it('a very short tap (down+up before any sample) is latched ≥ 3 ticks so the simulation always sees it', () => {
    ptr(canvas, 'pointerdown', 1, 50, 50);
    ptr(canvas, 'pointerup', 1, 50, 50);
    const s = sampleN(c, 6).map(x => x.jumpHeld);
    expect(s.slice(0, 3)).toEqual([true, true, true]);
    expect(s.slice(3).every(v => v === false)).toBe(true);
  });

  it('Touch Drag sets the lean: right = +, left = −, proportional to swipeDistance, saturating at ±1', () => {
    ptr(canvas, 'pointerdown', 1, 200, 100);
    ptr(canvas, 'pointermove', 1, 200 + 45, 100);       // half of 90 px
    const half = c.sample().tilt;
    expect(half).toBeGreaterThan(0.25); expect(half).toBeLessThan(0.75);
    ptr(canvas, 'pointermove', 1, 200 + 400, 100);
    expect(c.sample().tilt).toBeCloseTo(1, 2);
    ptr(canvas, 'pointermove', 1, 200 - 400, 100);
    expect(c.sample().tilt).toBeCloseTo(-1, 2);
  });

  it('deadzone ignores tiny finger jitter', () => {
    ptr(canvas, 'pointerdown', 1, 200, 100);
    ptr(canvas, 'pointermove', 1, 203, 100);
    expect(c.sample().tilt).toBe(0);
  });

  it('pull-down gesture raises the charge floor (swipe strength), never below 0 or above 1', () => {
    ptr(canvas, 'pointerdown', 1, 200, 100);
    expect(c.sample().pull).toBe(0);
    ptr(canvas, 'pointermove', 1, 200, 100 + 95);
    expect(c.sample().pull).toBeGreaterThan(0.6);
    ptr(canvas, 'pointermove', 1, 200, 100 + 900);
    expect(c.sample().pull).toBeLessThanOrEqual(1);
    c.setSettings(settings({ swipeStrength: 0 }));
    expect(c.sample().pull).toBe(0);
  });

  it('release reports the last pull value on the release tick (so the launch uses it)', () => {
    ptr(canvas, 'pointerdown', 1, 200, 100);
    sampleN(c, 4);
    ptr(canvas, 'pointermove', 1, 200, 195);
    sampleN(c, 2);
    ptr(canvas, 'pointerup', 1, 200, 195);
    const rel = sampleN(c, 3).find(s => !s.jumpHeld)!;
    expect(rel.pull).toBeGreaterThan(0.8);
  });

  it('Cancellation: pointercancel aborts (cancel pulse, no held) — never a launch', () => {
    ptr(canvas, 'pointerdown', 1, 100, 100);
    sampleN(c, 5);
    ptr(canvas, 'pointercancel', 1);
    const s = sampleN(c, 3);
    expect(s[0].cancel).toBe(true);
    expect(s.every(x => !x.jumpHeld)).toBe(true);
    expect(s[1].cancel).toBe(false);
  });

  it('window blur / page hidden cancels an active touch', () => {
    ptr(canvas, 'pointerdown', 1, 100, 100);
    sampleN(c, 3);
    window.dispatchEvent(new Event('blur'));
    const s = c.sample();
    expect(s.cancel).toBe(true);
    expect(s.jumpHeld).toBe(false);
  });

  it('Multi-touch safety: a second finger neither steals control nor releases the first', () => {
    ptr(canvas, 'pointerdown', 1, 100, 100);
    ptr(canvas, 'pointerdown', 2, 500, 100);
    ptr(canvas, 'pointermove', 2, 900, 100);
    expect(c.sample().tilt).toBe(0);
    ptr(canvas, 'pointerup', 2, 900, 100);
    expect(sampleN(c, 5).every(s => s.jumpHeld)).toBe(true);
    ptr(canvas, 'pointerup', 1, 100, 100);
    expect(sampleN(c, 4).some(s => !s.jumpHeld)).toBe(true);
  });

  it('input smoothing eases the tilt instead of jumping (and 0 = instant)', () => {
    c.setSettings(settings({ smoothing: 0.8 }));
    ptr(canvas, 'pointerdown', 1, 200, 100);
    ptr(canvas, 'pointermove', 1, 400, 100);
    const first = c.sample().tilt;
    expect(first).toBeGreaterThan(0); expect(first).toBeLessThan(0.3);
    sampleN(c, 200);
    expect(c.sample().tilt).toBeGreaterThan(0.95);
  });

  it('floating anchor: dragging far drags the anchor so full tilt is always one swipe-distance away from the thumb', () => {
    ptr(canvas, 'pointerdown', 1, 100, 100);
    ptr(canvas, 'pointermove', 1, 100 + 600, 100);
    c.sample();
    ptr(canvas, 'pointermove', 1, 100 + 600 - 60, 100); // thumb returns 60 px
    expect(c.sample().tilt).toBeLessThan(1);
  });
});

describe('TouchControls — PAD scheme + Boost button', () => {
  it('boost button emits one pulse per press; joystick leans; jump button holds and releases', () => {
    document.body.innerHTML = '';
    const canvas = document.createElement('div'); document.body.append(canvas);
    const mk = () => { const e = document.createElement('div'); e.setPointerCapture = () => undefined; Object.defineProperty(e, 'getBoundingClientRect', { value: () => ({ left: 0, top: 0, width: 200, height: 200, right: 200, bottom: 200, x: 0, y: 0, toJSON: () => ({}) }) }); document.body.append(e); return e; };
    const stickZone = mk(), knob = document.createElement('div'), jumpBtn = mk(), boostBtn = mk();
    const c = new TouchControls(canvas, settings({ scheme: 'pad' }));
    c.attachPad({ stickZone, stickKnob: knob, jumpBtn }); c.attachBoost(boostBtn);
    ptr(boostBtn, 'pointerdown', 7);
    expect(c.sample().boostPressed).toBe(true);
    expect(c.sample().boostPressed).toBe(false);
    ptr(stickZone, 'pointerdown', 3, 100, 100);
    ptr(stickZone, 'pointermove', 3, 100 + 64, 100); // R = 0.32*200 = 64
    expect(c.sample().tilt).toBeCloseTo(1, 1);
    ptr(stickZone, 'pointerup', 3, 164, 100);
    expect(c.sample().tilt).toBe(0);
    ptr(jumpBtn, 'pointerdown', 4);
    expect(sampleN(c, 5).every(s => s.jumpHeld)).toBe(true);
    ptr(jumpBtn, 'pointerup', 4);
    expect(sampleN(c, 3).some(s => !s.jumpHeld)).toBe(true);
    // in PAD mode a canvas touch must NOT charge (the buttons do)
    ptr(canvas, 'pointerdown', 9, 10, 10);
    expect(sampleN(c, 4).every(s => !s.jumpHeld)).toBe(true);
  });
});
