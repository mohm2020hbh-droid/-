import { describe, expect, it } from 'vitest';
import { PhysicsWorld } from '../../src/sim/PhysicsWorld';
import { TICK_RATE } from '../../src/sim/math';
import type { ToggleBehavior } from '../../src/map/schema';
import { LEVEL_01 } from '../../src/data/levels/level01';
import {
  type BehaviorView, buildCurve, compileMove, compileRotate, evalCondition, evalGate, evalTimed, evalToggle, behaviorProblems,
} from '../../src/map/MapBehavior';

const view = (o: Partial<BehaviorView> = {}): BehaviorView => ({ tick: 0, jumps: 0, boosts: 0, deaths: 0, checkpoints: 0, flags: new Map(), reached: new Set(), ...o });
const out = () => ({ x: 0, y: 0, z: 0, vx: 0, vy: 0 });
const NONE = new Map();

describe('MapBehavior — moving platform (move)', () => {
  it('sine: offset = amplitude · sin(2π(t/period + phase)) per axis (Lissajous when the periods differ)', () => {
    const m = compileMove({ type: 'move', mode: 'sine', x: { amplitude: 4, period: 6 }, y: { amplitude: 2, period: 3, phase: 0.25 } }, NONE)!;
    const o = out();
    m.at(0, o); expect(o.x).toBe(0); expect(o.y).toBeCloseTo(2, 12);                      // sin(2π·0.25) = 1
    m.at(Math.round(1.5 * TICK_RATE), o); expect(o.x).toBeCloseTo(4, 9);                  // quarter period of x
    expect(m.range).toEqual({ minX: -4, maxX: 4, minY: -2, maxY: 2 });
  });

  it('sine is bit-identical to PhysicsWorld.offsetAt for the same period/phase (migration contract)', () => {
    const w = new PhysicsWorld(LEVEL_01);
    const q4 = w.colliders.find(c => c.id === 'q4')!;
    const mv = LEVEL_01.movingObjects[0].move!;
    const m = compileMove({ type: 'move', mode: 'sine', x: { amplitude: mv.dx, period: mv.period, phase: mv.phase }, y: { amplitude: mv.dy, period: mv.period, phase: mv.phase } }, NONE)!;
    const a = { x: 0, y: 0, vx: 0, vy: 0 }, b = out();
    for (const t of [0, 1, 17, 360, 1234, 99999]) { w.offsetAt(q4, t, a); m.at(t, b); expect(b.x).toBe(a.x); expect(b.y).toBe(a.y); expect(b.vx).toBe(a.vx); expect(b.vy).toBe(a.vy); }
  });

  it('is a pure function of the tick (replay-safe): the same tick always gives the same offset', () => {
    const m = compileMove({ type: 'move', mode: 'sine', x: { amplitude: 3, period: 5, phase: 0.1 } }, NONE)!;
    const a = out(), b = out();
    m.at(777, a); m.at(5, b); m.at(777, b);
    expect(b.x).toBe(a.x);
  });

  it('linear: ping-pong between waypoints at constant speed with pauses at the ends', () => {
    const m = compileMove({ type: 'move', mode: 'linear', points: [{ x: 10, y: 0 }], speed: 5, pingPong: true, pause: 1 }, NONE)!;
    const o = out();
    m.at(0, o); expect(o.x).toBe(0);
    m.at(0.5 * TICK_RATE, o); expect(o.x).toBe(0);                                         // initial pause
    m.at(2 * TICK_RATE, o); expect(o.x).toBeCloseTo(5, 9);                                  // 1 s pause + 1 s at 5 m/s
    m.at(3 * TICK_RATE, o); expect(o.x).toBeCloseTo(10, 9);                                 // arrives
    m.at(3.5 * TICK_RATE, o); expect(o.x).toBeCloseTo(10, 9);                               // pause at the far end
    m.at(5 * TICK_RATE, o); expect(o.x).toBeCloseTo(5, 9);                                  // coming back
    m.at(6 * TICK_RATE, o); expect(o.x).toBeCloseTo(0, 9);
    expect(m.range.maxX).toBe(10);
  });

  it('linear loop returns to the origin; smooth easing is symmetric', () => {
    const loop = compileMove({ type: 'move', mode: 'linear', points: [{ x: 4, y: 0 }, { x: 4, y: 4 }], speed: 4 }, NONE)!;
    const o = out();
    const total = (4 + 4 + Math.hypot(4, 4)) / 4;                                           // closed triangle
    loop.at(Math.round(total * TICK_RATE), o); expect(Math.hypot(o.x, o.y)).toBeLessThan(0.02);
    const sm = compileMove({ type: 'move', mode: 'linear', points: [{ x: 8, y: 0 }], speed: 4, pingPong: true, ease: 'smooth' }, NONE)!;
    sm.at(1 * TICK_RATE, o); expect(o.x).toBeCloseTo(4, 6);                                  // half way at half time
    sm.at(0.5 * TICK_RATE, o); expect(o.x).toBeLessThan(2);                                  // slower at the start (ease-in)
  });

  it('path: follows a bezier curve by arc length; loop / pingpong / once end behaviour', () => {
    const bez = buildCurve({ kind: 'bezier', points: [{ x: 0, y: 0 }, { x: 0, y: 4 }, { x: 8, y: 4 }, { x: 8, y: 0 }] });
    expect(bez.length).toBeGreaterThan(8); expect(bez.length).toBeLessThan(14);
    const paths = new Map([['arc', bez]]);
    const o = out();
    const once = compileMove({ type: 'move', mode: 'path', path: 'arc', duration: 4, loop: 'once' }, paths)!;
    once.at(0, o); expect(o).toMatchObject({ x: 0, y: 0 });
    once.at(2 * TICK_RATE, o); expect(o.x).toBeCloseTo(4, 1); expect(o.y).toBeGreaterThan(2.5);   // apex region
    once.at(100 * TICK_RATE, o); expect(o.x).toBeCloseTo(8, 9); expect(o.y).toBeCloseTo(0, 9);     // stays at the end
    const loop = compileMove({ type: 'move', mode: 'path', path: 'arc', duration: 4, loop: 'loop' }, paths)!;
    loop.at(4 * TICK_RATE + 1, o); expect(o.x).toBeLessThan(0.1);                                  // wrapped
    const pp = compileMove({ type: 'move', mode: 'path', path: 'arc', speed: 2, loop: 'pingpong' }, paths)!;
    pp.at(Math.round((bez.length / 2) * 2 * TICK_RATE) + 0, o);                                    // back at the start after there-and-back
    expect(Math.hypot(o.x, o.y)).toBeLessThan(0.05);
  });

  it('spline passes through its control points; closed polyline wraps', () => {
    const sp = buildCurve({ kind: 'spline', points: [{ x: 0, y: 0 }, { x: 5, y: 3 }, { x: 10, y: 0 }] });
    const p = { x: 0, y: 0 };
    sp.at(0, p); expect(p).toEqual({ x: 0, y: 0 });
    sp.at(sp.length, p); expect(p.x).toBeCloseTo(10, 9);
    const closed = buildCurve({ kind: 'polyline', closed: true, points: [{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 3 }] });
    expect(closed.length).toBeCloseTo(4 + 3 + 5, 9);
  });

  it('invalid motions are rejected (null) rather than producing NaN', () => {
    expect(compileMove({ type: 'move', mode: 'path', path: 'missing', speed: 1 }, NONE)).toBeNull();
    expect(compileMove({ type: 'move', mode: 'linear', points: [], speed: 1 }, NONE)).toBeNull();
    expect(compileMove({ type: 'move', mode: 'linear', points: [{ x: 1, y: 0 }], speed: 0 }, NONE)).toBeNull();
  });
});

describe('MapBehavior — rotating object (rotate)', () => {
  it('continuous: angle = base + speed·t', () => {
    const r = compileRotate({ type: 'rotate', mode: 'continuous', speed: 90, base: 10 });
    expect(r.angle(0)).toBe(10); expect(r.angle(TICK_RATE)).toBeCloseTo(100, 9); expect(r.angle(4 * TICK_RATE)).toBeCloseTo(370, 9);
    expect(r.axis).toBe('z');
  });
  it('sine: oscillates around base with the given amplitude and period', () => {
    const r = compileRotate({ type: 'rotate', mode: 'sine', base: 0, amplitude: 45, period: 4 });
    expect(r.angle(0)).toBeCloseTo(0, 9); expect(r.angle(TICK_RATE)).toBeCloseTo(45, 9); expect(r.angle(3 * TICK_RATE)).toBeCloseTo(-45, 9);
    expect(r.angle(4 * TICK_RATE)).toBeCloseTo(0, 9);
  });
  it('free: damped flywheel approaches base + ω0/k (closed form of the exponential decay)', () => {
    const r = compileRotate({ type: 'rotate', mode: 'free', initialSpeed: 120, damping: 0.5 });
    expect(r.angle(0)).toBe(0);
    expect(r.angle(1000 * TICK_RATE)).toBeCloseTo(240, 6);
    expect(r.angle(TICK_RATE)).toBeCloseTo(240 * (1 - Math.exp(-0.5)), 9);
    const undamped = compileRotate({ type: 'rotate', mode: 'free', initialSpeed: 30, damping: 0 });
    expect(undamped.angle(2 * TICK_RATE)).toBeCloseTo(60, 9);
  });
});

describe('MapBehavior — toggle / timed / conditional gates', () => {
  it('toggle: red/blue blocks swap with every jump (modulus 2), the group number selects the phase', () => {
    const red: ToggleBehavior = { type: 'toggle', channel: 'jumps', active: [0] };
    const blue: ToggleBehavior = { type: 'toggle', channel: 'jumps', active: [1] };
    for (let jumps = 0; jumps < 6; jumps++) {
      expect(evalToggle(red, view({ jumps })).active).toBe(jumps % 2 === 0);
      expect(evalToggle(blue, view({ jumps })).active).toBe(jumps % 2 === 1);
    }
  });
  it('toggle: modulus 3 gives three alternating groups; boosts / checkpoints / flag channels', () => {
    const g = (k: number): ToggleBehavior => ({ type: 'toggle', channel: 'jumps', modulus: 3, active: [k] });
    expect([0, 1, 2].map(k => evalToggle(g(k), view({ jumps: 4 })).active)).toEqual([false, true, false]);
    expect(evalToggle({ type: 'toggle', channel: 'boosts', active: [1] }, view({ boosts: 3 })).active).toBe(true);
    expect(evalToggle({ type: 'toggle', channel: 'checkpoints', modulus: 4, active: [2] }, view({ checkpoints: 2 })).active).toBe(true);
    expect(evalToggle({ type: 'toggle', channel: 'flag', flag: 'door', active: [1] }, view({ flags: new Map([['door', 1]]) })).active).toBe(true);
    expect(evalToggle({ type: 'toggle', channel: 'jumps', active: [0], inactive: { collision: false, visual: 'hidden' } }, view({ jumps: 1 })).inactive.visual).toBe('hidden');
  });
  it('timed: on for `duty` of every period, phase shifts the window, blinks during the warning time', () => {
    const b = { type: 'timed', period: 4, duty: 0.5, phase: 0, warn: 0.5 } as const;
    expect(evalTimed(b, view({ tick: 0 })).active).toBe(true);
    expect(evalTimed(b, view({ tick: 1.9 * TICK_RATE })).blink).toBe(true);                // last 0.5 s of the on-window
    expect(evalTimed(b, view({ tick: 1.0 * TICK_RATE })).blink).toBe(false);
    expect(evalTimed(b, view({ tick: 2.5 * TICK_RATE })).active).toBe(false);
    expect(evalTimed({ ...b, phase: 0.5 }, view({ tick: 0 })).active).toBe(false);
    expect(evalTimed(b, view({ tick: 4 * TICK_RATE })).active).toBe(true);                 // next period
  });
  it('conditions: flags, counters with modulus, checkpoints, all / any / not', () => {
    const v = view({ flags: new Map<string, number | boolean>([['a', true], ['n', 0]]), jumps: 5, reached: new Set(['cp0']) });
    expect(evalCondition({ flag: 'a' }, v)).toBe(true);
    expect(evalCondition({ flag: 'n' }, v)).toBe(false);
    expect(evalCondition({ flag: 'zzz' }, v)).toBe(false);
    expect(evalCondition({ counter: 'jumps', mod: 2, in: [1] }, v)).toBe(true);
    expect(evalCondition({ checkpoint: 'cp0', reached: true }, v)).toBe(true);
    expect(evalCondition({ all: [{ flag: 'a' }, { not: { flag: 'n' } }] }, v)).toBe(true);
    expect(evalCondition({ any: [{ flag: 'n' }, { counter: 'jumps', in: [5] }] }, v)).toBe(true);
    expect(evalGate({ type: 'conditional', when: { flag: 'a' } }, v).active).toBe(true);
  });
});

describe('MapBehavior — validation of behaviour blocks', () => {
  const paths = new Set(['p1']);
  it('reports broken parameters instead of accepting them', () => {
    expect(behaviorProblems({ type: 'move', mode: 'sine', x: { amplitude: 1, period: 0 } }, paths).length).toBe(1);
    expect(behaviorProblems({ type: 'move', mode: 'sine' }, paths).length).toBe(1);
    expect(behaviorProblems({ type: 'move', mode: 'path', path: 'nope', speed: 1 }, paths)[0]).toContain('nope');
    expect(behaviorProblems({ type: 'move', mode: 'path', path: 'p1' }, paths)[0]).toContain('speed or duration');
    expect(behaviorProblems({ type: 'toggle', channel: 'jumps', active: [] }, paths).length).toBe(1);
    expect(behaviorProblems({ type: 'toggle', channel: 'flag', active: [1] }, paths)[0]).toContain('flag name');
    expect(behaviorProblems({ type: 'timed', period: -1 }, paths).length).toBe(1);
    expect(behaviorProblems({ type: 'timed', period: 2, duty: 1 }, paths).length).toBe(1);
    expect(behaviorProblems({ type: 'move', mode: 'sine', x: { amplitude: 1, period: 2 } }, paths)).toEqual([]);
    expect(behaviorProblems({ type: 'rotate', mode: 'sine', amplitude: 10, period: 3 }, paths)).toEqual([]);
  });
});
