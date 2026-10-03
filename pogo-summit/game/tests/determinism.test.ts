import { describe, expect, it } from 'vitest';
import { flatGround, input, makeLevel, setup } from './helpers';
import type { PogoInput } from '../src/sim/PogoState';

/** A scripted "player": taps, leans, spins, boosts — enough to exercise every code path. */
function script(t: number): PogoInput {
  const phase = t % 300;
  return input({
    jumpHeld: phase > 20 && phase < 20 + 30 + ((t / 300) | 0) * 7,
    tilt: Math.sin(t / 37) * (phase > 90 ? 1 : 0.4),
    boostPressed: t % 411 === 0,
    pull: 0,
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

describe('determinism (replay) — 22.10 fixed-point quantisation, XLSX F-003', () => {
  const lvl = () => makeLevel([flatGround({ w: 120 }), { id: 'p2', kind: 'wood', x: 30, y: 6, w: 8, h: 1 }, { id: 'p3', kind: 'ice', x: 44, y: 10, w: 10, h: 2 }],
    { specialSurfaces: [{ id: 'b', kind: 'bounce', x: -20, y: 0, w: 4, h: 1 }] });

  function runScript(n: number) {
    const { pogo } = setup(lvl());
    const trace: number[] = [];
    for (let t = 0; t < n; t++) {
      pogo.step(script(t));
      const s = pogo.state;
      if (t % 10 === 0) trace.push(s.x, s.y, s.vx, s.vy, s.angle, s.omega);
    }
    return { hash: hash(trace), state: { ...pogo.state } };
  }

  it('same input sequence ⇒ bit-identical state (3 independent runs, 60 000 ticks)', () => {
    const a = runScript(60000), b = runScript(60000), c = runScript(60000);
    expect(a.hash).toBe(b.hash);
    expect(b.hash).toBe(c.hash);
    expect(a.state).toEqual(b.state);
  });

  it('positions and velocities lie on the 1/1024 grid', () => {
    const { pogo } = setup(lvl());
    for (let t = 0; t < 3000; t++) {
      pogo.step(script(t));
      const s = pogo.state;
      for (const v of [s.x, s.y, s.vx, s.vy]) expect(Math.abs(v * 1024 - Math.round(v * 1024))).toBeLessThan(1e-9);
    }
  });

  it('never produces NaN/Infinity and never escapes the world under random abuse', () => {
    const { pogo } = setup(lvl());
    let seed = 12345;
    const rnd = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
    for (let t = 0; t < 120000; t++) {
      pogo.step(input({ jumpHeld: rnd() < 0.5, tilt: rnd() * 2 - 1, boostPressed: rnd() < 0.01, pull: rnd() < 0.05 ? rnd() : 0, cancel: rnd() < 0.005 }));
      const s = pogo.state;
      if (!Number.isFinite(s.x + s.y + s.vx + s.vy + s.angle + s.omega)) throw new Error('non-finite at ' + t);
      if (s.y < -80) throw new Error('escaped below the world at ' + t);
    }
    expect(pogo.state.falls).toBeGreaterThanOrEqual(0);
  });
});

describe('input robustness', () => {
  it('cancel aborts a charge without launching', () => {
    const { pogo } = setup(makeLevel([flatGround()]));
    pogo.step(input({ jumpHeld: true }));
    for (let i = 0; i < 20; i++) pogo.step(input({ jumpHeld: true }));
    expect(pogo.state.mode).toBe('CHARGING');
    pogo.step(input({ jumpHeld: false, cancel: true }));
    expect(pogo.state.mode).toBe('GROUNDED');
    expect(pogo.state.jumps).toBe(0);
  });

  it('a touch held from the air does NOT start charging on landing unless pressed within the press-buffer window', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1; pogo.state.x = 0; pogo.state.y = 12; pogo.state.vy = 0;
    // press 40 ticks before landing (way outside the 8-tick buffer) and keep holding
    for (let i = 0; i < 200 && pogo.state.mode === 'AIR'; i++) pogo.step(input({ jumpHeld: true }));
    for (let i = 0; i < 10; i++) pogo.step(input({ jumpHeld: true }));
    expect(['GROUNDED']).toContain(pogo.state.mode);
    expect(pogo.state.jumps).toBe(0);
  });

  it('a press just before landing is buffered and starts the charge at touchdown', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1; pogo.state.x = 0; pogo.state.y = 4; pogo.state.vy = -6;
    let pressed = false, charging = false;
    for (let i = 0; i < 300; i++) {
      // COM rests 1.2 m above the surface; press ~0.5 m before touchdown (≈4 ticks at this speed, inside the 8-tick buffer)
      const nearGround = pogo.state.y < 1.2 + 0.5;
      if (!pressed && nearGround) pressed = true;
      pogo.step(input({ jumpHeld: pressed }));
      if ((pogo.state.mode as string) === 'CHARGING') { charging = true; break; }
    }
    expect(charging).toBe(true);
  });
});
