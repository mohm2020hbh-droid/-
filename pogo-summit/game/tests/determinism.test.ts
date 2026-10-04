import { describe, expect, it } from 'vitest';
import { flatGround, input, makeLevel, setup } from './helpers';
import type { PogoInput } from '../src/sim/PogoState';

/** A scripted "player": holds, leans, spins — enough to exercise every code path (the only inputs the locked physics reads). */
function script(t: number): PogoInput {
  const phase = t % 300;
  return input({
    jumpHeld: phase > 20 && phase < 20 + 30 + ((t / 300) | 0) * 7,
    tilt: Math.sin(t / 37) * (phase > 90 ? 1 : 0.4),
    cancel: t % 977 === 0,
  });
}

function hash(vals: number[]): string {
  let h = 2166136261;
  for (const v of vals) {
    const b = new DataView(new ArrayBuffer(8)); b.setFloat64(0, v);
    for (let i = 0; i < 8; i++) { h ^= b.getUint8(i); h = Math.imul(h, 16777619); }
  }
  return (h >>> 0).toString(16);
}

describe('determinism (replay): fixed 120 Hz step, no clocks, no randomness', () => {
  const lvl = () => makeLevel([flatGround({ w: 120 }), { id: 'p2', kind: 'wood', x: 30, y: 6, w: 8, h: 1 }, { id: 'p3', kind: 'ice', x: 44, y: 10, w: 10, h: 2 }],
    { movingObjects: [{ id: 'mv', kind: 'wood', x: -20, y: 3, w: 6, h: 1, move: { dx: 5, dy: 1, period: 5 } }] });

  function runScript(n: number) {
    const { pogo } = setup(lvl());
    const trace: number[] = [];
    for (let t = 0; t < n; t++) {
      pogo.step(script(t));
      const s = pogo.state;
      if (t % 10 === 0) trace.push(s.qx, s.qy, s.qvx, s.qvy, s.theta, s.omega, s.load, s.loadMin, s.loadMax, s.sx, s.sy);
    }
    return { hash: hash(trace), state: { ...pogo.state } };
  }

  it('same input sequence ⇒ bit-identical state (3 independent runs, 60 000 ticks)', () => {
    const a = runScript(60000), b = runScript(60000), c = runScript(60000);
    expect(a.hash).toBe(b.hash);
    expect(b.hash).toBe(c.hash);
    expect(a.state).toEqual(b.state);
  });

  it('never produces NaN/Infinity and never escapes the world under random abuse', () => {
    const { pogo } = setup(lvl());
    let seed = 12345;
    const rnd = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
    for (let t = 0; t < 120000; t++) {
      pogo.step(input({ jumpHeld: rnd() < 0.5, tilt: rnd() * 2 - 1, cancel: rnd() < 0.005 }));
      const s = pogo.state;
      if (!Number.isFinite(s.qx + s.qy + s.qvx + s.qvy + s.theta + s.omega + s.load + s.sx + s.sy)) throw new Error('non-finite at ' + t);
      if (s.y < -80) throw new Error('escaped below the world at ' + t);
      if (Math.hypot(s.qvx, s.qvy) > 300 + 1e-6 && !s.grounded) throw new Error('speed cap broken at ' + t);
      if (s.load > s.loadMax + 1e-9) throw new Error('load above window at ' + t);
    }
    expect(pogo.state.falls).toBeGreaterThanOrEqual(0);
  });
});

describe('input robustness', () => {
  it('a cancelled touch counts as releasing the hold: the pogo launches at the first L ≥ L_min instead of charging to L_max', () => {
    const held = setup(makeLevel([flatGround({ w: 200 })])).pogo;
    const cancelled = setup(makeLevel([flatGround({ w: 200 })])).pogo;
    let a = 0, b = 0;
    for (let i = 0; i < 100 && !a; i++) if (held.step(input({ jumpHeld: true })).some(e => e.type === 'launch')) a = held.state.loadLast;
    for (let i = 0; i < 100 && !b; i++) if (cancelled.step(input({ jumpHeld: true, cancel: i > 5 })).some(e => e.type === 'launch')) b = cancelled.state.loadLast;
    expect(a).toBe(95);
    expect(b).toBeLessThan(45);
  });

  it('a hold pressed in the air carries into the landing: the pogo charges to L_max without a fresh press', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 800 })]));
    let launches = 0;
    let lastLoad = 0;
    for (let i = 0; i < 2000 && launches < 3; i++) if (pogo.step(input({ jumpHeld: true })).some(e => e.type === 'launch')) { launches++; lastLoad = pogo.state.loadLast; }
    expect(launches).toBe(3);
    expect(lastLoad).toBeGreaterThan(94.99);
  });
});
