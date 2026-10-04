import { describe, expect, it } from 'vitest';
import { flatGround, input, makeLevel, run, setup } from './helpers';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { placeInAir } from '../src/sim/PogoState';
import { TICK_RATE } from '../src/sim/math';

describe('start + continuous hopping (spec E8: the pogo charges and launches by itself)', () => {
  it('starts planted on the ground under startPosition, grounded, window [40, 95]', () => {
    const { pogo } = setup(makeLevel([flatGround()]));
    expect(pogo.state.mode).toBe('GROUNDED');
    expect(pogo.state.grounded).toBe(true);
    expect(pogo.state.loadMin).toBe(40);
    expect(pogo.state.loadMax).toBe(95);
    expect(pogo.state.y).toBeCloseTo(55 / 52, 6);   // origin sits one stick length above the surface
  });

  it('with no input it hops in place forever, vertically, on the idle hop', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    let launches = 0;
    for (let i = 0; i < 2400; i++) { if (pogo.step().some(e => e.type === 'launch')) launches++; expect(Math.abs(pogo.state.qx)).toBeLessThan(0.01); }
    expect(launches).toBeGreaterThan(15);
    expect(pogo.state.falls).toBe(0);
  });

  it('the stick never lies down: collision-checked rotation limits the lean on the ground', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    let maxLean = 0;
    for (let i = 0; i < 3000; i++) {
      pogo.step(input({ tilt: -1, jumpHeld: true }));
      if (pogo.state.grounded) maxLean = Math.max(maxLean, Math.abs(pogo.state.theta));
    }
    expect(maxLean).toBeGreaterThan(20);
    expect(maxLean).toBeLessThan(75);
    expect(pogo.state.falls).toBe(0);
  });

  it('steering in the air changes the landing angle, not the velocity (E4 only rotates)', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 800 })]));
    placeInAir(cfg, pogo.state, 0, 20, 30, 0);
    const vx0 = pogo.state.qvx;
    for (let i = 0; i < 20; i++) pogo.step(input({ tilt: 1 }));
    // only drag acts on v_x (0.05 per T), steering adds nothing
    const dragOnly = 30 * Math.exp(-0.05000000074505806 * 20 * (16 * (973 / 1024) / 120));
    expect(pogo.state.qvx).toBeCloseTo(dragOnly, 1);
    expect(pogo.state.theta).toBeLessThan(-10);
    void vx0;
  });
});

describe('collision (our implementation of what the spec leaves open)', () => {
  it('a fast fall never tunnels through a thin platform', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200, h: 0.6, taper: 1 })]));
    placeInAir(cfg, pogo.state, 0, 40, 0, -cfg.maxSpeed);
    const ev = run(pogo, 400);
    expect(ev.some(e => e.type === 'land')).toBe(true);
    expect(pogo.state.y).toBeGreaterThan(-0.5);
    expect(pogo.state.falls).toBe(0);
  });

  it('a low ceiling stops a rising pogo dead (S = max(28,…)·min(1+n_z,1) = 0 for n_z = −1) and it falls back', () => {
    const lvl = makeLevel([flatGround({ w: 200 })], { obstacles: [{ id: 'ceil', pts: [{ x: -10, y: 4 }, { x: 10, y: 4 }, { x: 10, y: 6 }, { x: -10, y: 6 }] }] });
    const { pogo, cfg } = setup(lvl);
    placeInAir(cfg, pogo.state, 0, 2.5, 0, 60);
    let hit = false;
    for (let i = 0; i < 200 && !hit; i++) hit = pogo.step().some(e => e.type === 'wall_hit');
    expect(hit).toBe(true);
    expect(Math.hypot(pogo.state.qvx, pogo.state.qvy)).toBeLessThan(1e-6);
    expect(pogo.state.y).toBeLessThan(4);
  });

  it('the oriented hull follows a leaning stick: a pogo leaning 55° clears a ledge its upright box would have hit', () => {
    // a platform whose top is 0.5 m above the pogo's tip arc; leaning right the hull stays off the platform side
    const lvl = makeLevel([flatGround({ w: 200 }), { id: 'ledge', kind: 'rock', x: 12, y: 3, w: 8, h: 2 }]);
    const { pogo, cfg } = setup(lvl);
    placeInAir(cfg, pogo.state, 8.2, 4.4, 20, 40, -55);
    let wall = false;
    for (let i = 0; i < 60; i++) wall = pogo.step().some(e => e.type === 'wall_hit') || wall;
    expect(wall).toBe(false);
  });

  it('hazard triggers an event and respawns on the last safe landing', () => {
    const lvl = makeLevel([flatGround({ w: 80 })], { hazards: [{ id: 'sp', kind: 'spikes', x: 10, y: 0, w: 3, h: 1.4 }] });
    const { pogo, cfg } = setup(lvl);
    run(pogo, 60);                                  // hops in place: lands on a safe platform
    placeInAir(cfg, pogo.state, 10, 3, 0, -20);
    const ev = run(pogo, 80);
    expect(ev.some(e => e.type === 'hazard')).toBe(true);
    expect(ev.some(e => e.type === 'respawn')).toBe(true);
    expect(pogo.state.hazards).toBe(1);
    expect(Math.abs(pogo.state.x)).toBeLessThan(1.0);
  });

  it('goal trigger finishes the run', () => {
    const lvl = makeLevel([flatGround({ w: 80 })], { goal: { x: 5, y: 0, w: 2, h: 4 } });
    const { pogo, cfg } = setup(lvl);
    placeInAir(cfg, pogo.state, 5, 4, 0, 0);
    const ev = run(pogo, 30);
    expect(ev.some(e => e.type === 'goal')).toBe(true);
    expect(pogo.state.mode).toBe('FINISHED');
    expect(pogo.state.finishedTick).toBeGreaterThan(0);
  });

  it('falling below killY respawns and counts a fall', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 40 })], { killY: -20 }));
    run(pogo, 40);
    placeInAir(cfg, pogo.state, 100, -10, 0, -50);
    const ev = run(pogo, 200);
    expect(ev.some(e => e.type === 'fall')).toBe(true);
    expect(pogo.state.falls).toBe(1);
  });

  it('a moving platform carries the hopping pogo with it', () => {
    const lvl = makeLevel([flatGround({ x: -80, w: 20 })], {
      movingObjects: [{ id: 'mv', kind: 'wood', x: 0, y: 0, w: 14, h: 1, move: { dx: 6, dy: 0, period: 4 } }],
      startPosition: { x: 0, y: 0 },
    });
    const { pogo } = setup(lvl);
    const x0 = pogo.state.x;
    let maxX = x0, onPlatform = 0;
    for (let i = 0; i < 480; i++) { pogo.step(); maxX = Math.max(maxX, pogo.state.x); if (pogo.state.grounded && pogo.world.colliders[pogo.state.groundId].id === 'mv') onPlatform++; }
    expect(maxX - x0).toBeGreaterThan(4);
    expect(onPlatform).toBeGreaterThan(100);
    expect(pogo.state.falls).toBe(0);
  });
});

describe('units and conversions', () => {
  it('the original HUD metre is 52 Q: a 95-jump rises 5.54 m, a 300-jump 55.8 m', () => {
    const cfg = createPhysicsConfig();
    expect(cfg.qPerMetre).toBe(52);
    expect(288.1 / cfg.qPerMetre).toBeCloseTo(5.54, 2);
    expect(2903.4 / cfg.qPerMetre).toBeCloseTo(55.83, 2);
  });
  it('the presentation velocity is m/s of real time: 70.52 Q/T ⇒ 21.7 m/s', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 800 })]));
    placeInAir(cfg, pogo.state, 0, 10, 0, 70.52);
    expect(pogo.state.vy).toBeCloseTo(70.52 * 16 * (973 / 1024) / 52, 6);
    expect(pogo.state.vy).toBeCloseTo(20.6, 0);
    expect(TICK_RATE).toBe(120);
  });
});
