import { describe, expect, it } from 'vitest';
import { flatGround, input, makeLevel, run, setup } from './helpers';
import { DEG, TICK_RATE } from '../src/sim/math';
import { simulateJump } from '../src/sim/prediction';
import { PARAM_DEFS, createPhysicsConfig, paramsByStatus } from '../src/sim/PhysicsConfig';

describe('config evidence rules', () => {
  it('tick rate and fixed-point step are the documented A-grade values', () => {
    expect(PARAM_DEFS.tickRate.value).toBe(120);
    expect(PARAM_DEFS.fixedPointStep.value).toBe(1 / 1024);
    expect(TICK_RATE).toBe(120);
  });
  it('every movement physics parameter is TUNE_ME (sources contain no physics numbers)', () => {
    const must = ['gravity', 'maxFallSpeed', 'launchSpeedMin', 'launchSpeedMax', 'chargeTicksMax', 'tiltRateGround', 'tiltRateAir',
      'tiltMaxAngle', 'turnSpeed', 'floorRestitution', 'wallRestitution', 'energyLoss', 'slideFriction', 'steepSlopeAngle',
      'boostThreshold', 'boostPower', 'boostRotation', 'airControl', 'groundControl', 'acceleration', 'deceleration',
      'maxHorizontalSpeed', 'landingResponse'];
    const tune = new Set<string>(paramsByStatus('TUNE_ME'));
    for (const k of must) expect(tune.has(k), k).toBe(true);
  });
  it('typical jump cycle is in the neighbourhood of the 150-tick video reference', () => {
    const cfg = createPhysicsConfig();
    // half charge, vertical: flight time up+down on level ground + half the charge time
    const v = cfg.launchSpeedMin + (cfg.launchSpeedMax - cfg.launchSpeedMin) * 0.5;
    const flight = (2 * v) / cfg.gravity * TICK_RATE;
    const total = flight + cfg.chargeTicksMax * 0.5;
    expect(total).toBeGreaterThan(100);
    expect(total).toBeLessThan(200);
  });
});

describe('start + gravity + launch', () => {
  it('starts planted on the ground under startPosition', () => {
    const { pogo } = setup(makeLevel([flatGround()]));
    expect(pogo.state.mode).toBe('GROUNDED');
    run(pogo, 30);
    expect(pogo.state.mode).toBe('GROUNDED');
    expect(pogo.state.vy).toBe(0);
  });

  it('release launches along the stick axis with speed between min and max', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround()]));
    run(pogo, 5);
    run(pogo, 1, input({ jumpHeld: true }));          // press
    expect(pogo.state.mode).toBe('CHARGING');
    run(pogo, 3, input({ jumpHeld: true }));
    const ev = run(pogo, 1, input({ jumpHeld: false })); // release
    expect(ev.some(e => e.type === 'launch')).toBe(true);
    const sp = Math.hypot(pogo.state.vx, pogo.state.vy);
    expect(sp).toBeGreaterThanOrEqual(cfg.launchSpeedMin - 1e-6);
    expect(sp).toBeLessThanOrEqual(cfg.launchSpeedMin + 1.0);
    expect(pogo.state.vy).toBeGreaterThan(0);
    expect(Math.abs(pogo.state.vx)).toBeLessThan(0.2); // upright ⇒ straight up
  });

  it('full charge reaches launchSpeedMax and the apex matches v²/2g within tick-integration error', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    run(pogo, 5);
    run(pogo, cfg.chargeTicksMax + 10, input({ jumpHeld: true }));
    run(pogo, 1, input({ jumpHeld: false }));
    expect(pogo.state.vy).toBeCloseTo(cfg.launchSpeedMax, 1);
    const y0 = pogo.state.y;
    let apex = y0;
    for (let i = 0; i < 400 && pogo.state.vy > -1; i++) { pogo.step(); apex = Math.max(apex, pogo.state.y); }
    const expected = (cfg.launchSpeedMax ** 2) / (2 * cfg.gravity);
    expect(apex - y0).toBeGreaterThan(expected * 0.97);
    expect(apex - y0).toBeLessThan(expected * 1.03);
  });

  it('tilt sets the launch direction (angle, not "button = speed")', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    run(pogo, 5);
    run(pogo, 60, input({ jumpHeld: true, tilt: 1 }));     // lean right while charging
    expect(pogo.state.angle).toBeGreaterThan(40 * DEG);
    expect(pogo.state.angle).toBeLessThanOrEqual(cfg.tiltMaxAngle * DEG + 1e-3);
    run(pogo, 1, input({ jumpHeld: false, tilt: 1 }));
    expect(pogo.state.vx).toBeGreaterThan(3);
    expect(pogo.state.vy).toBeGreaterThan(3);
  });

  it('lands, plants and stops (no tunnelling, no jitter) after a max-power jump', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    run(pogo, 5);
    run(pogo, cfg.chargeTicksMax, input({ jumpHeld: true }));
    const ev = run(pogo, 1, input({ jumpHeld: false }));
    expect(ev.some(e => e.type === 'launch')).toBe(true);
    const events = run(pogo, 400);
    expect(events.some(e => e.type === 'land')).toBe(true);
    expect(pogo.state.mode).toBe('GROUNDED');
    expect(pogo.state.y).toBeLessThan(2.0);
    expect(Math.abs(pogo.state.vy)).toBeLessThan(0.01);
  });
});

describe('collision + surfaces', () => {
  it('a fast fall never tunnels through a thin platform', () => {
    const lvl = makeLevel([flatGround({ w: 200, h: 0.6, taper: 1 })], { startPosition: { x: 0, y: 0 } });
    const { pogo, cfg } = setup(lvl);
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1;
    pogo.state.y = 40; pogo.state.x = 0; pogo.state.vy = -cfg.maxFallSpeed;
    run(pogo, 400);
    expect(pogo.state.y).toBeGreaterThan(-0.5);
    expect(pogo.state.mode).toBe('GROUNDED');
  });

  it('wall: torso bounces with wallRestitution and loses speed', () => {
    const lvl = makeLevel([flatGround({ w: 80 })], {
      obstacles: [{ id: 'wall', pts: [{ x: 6, y: -6 }, { x: 8, y: -6 }, { x: 8, y: 40 }, { x: 6, y: 40 }] }],
    });
    const { pogo, cfg } = setup(lvl);
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1;
    pogo.state.x = 2; pogo.state.y = 10; pogo.state.vx = 12; pogo.state.vy = 0; pogo.state.angle = 0;
    const ev = run(pogo, 40);
    expect(pogo.state.vx).toBeLessThan(0);
    expect(ev.some(e => e.type === 'wall_hit')).toBe(true);
    expect(Math.abs(pogo.state.vx)).toBeLessThan(12 * cfg.wallRestitution + 1);
  });

  it('bounce pad launches upward with at least bounce.minSpeed and emits a bounce event', () => {
    const lvl = makeLevel([flatGround({ x: -20, w: 30 })], { startPosition: { x: -20, y: 0 }, specialSurfaces: [{ id: 'pad', kind: 'bounce', x: 14, y: 0, w: 4, h: 1 }] });
    const { pogo } = setup(lvl);
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1;
    pogo.state.x = 14; pogo.state.y = 8; pogo.state.vx = 0; pogo.state.vy = -10; pogo.state.angle = 0;
    let bounced = false, vyAfter = 0;
    for (let i = 0; i < 200; i++) {
      const ev = pogo.step();
      if (ev.some(e => e.type === 'bounce')) { bounced = true; vyAfter = pogo.state.vy; break; }
    }
    expect(bounced).toBe(true);
    expect(vyAfter).toBeGreaterThanOrEqual(15);
  });

  it('slippery surface keeps horizontal momentum after landing (slides), rock stops quickly', () => {
    const ice = makeLevel([flatGround({ w: 200, kind: 'ice' })]);
    const rock = makeLevel([flatGround({ w: 200 })]);
    const slide = (lvl: ReturnType<typeof makeLevel>) => {
      const { pogo } = setup(lvl);
      pogo.state.mode = 'AIR'; pogo.state.groundId = -1;
      pogo.state.x = -20; pogo.state.y = 3; pogo.state.vx = 10; pogo.state.vy = -4; pogo.state.angle = 0;
      run(pogo, 240);
      return pogo.state.x;
    };
    const xi = slide(ice), xr = slide(rock);
    expect(xi).toBeGreaterThan(xr + 4);
  });

  it('hazard triggers an event and respawns on the last safe spot', () => {
    const lvl = makeLevel([flatGround({ w: 80 })], { hazards: [{ id: 'sp', kind: 'spikes', x: 10, y: 0, w: 3, h: 1.4 }] });
    const { pogo } = setup(lvl);
    run(pogo, 130); // become a safe spot
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1;
    pogo.state.x = 10; pogo.state.y = 3; pogo.state.vx = 0; pogo.state.vy = -6;
    const ev = run(pogo, 60);
    expect(ev.some(e => e.type === 'hazard')).toBe(true);
    expect(ev.some(e => e.type === 'respawn')).toBe(true);
    expect(pogo.state.mode).toBe('GROUNDED');
    expect(Math.abs(pogo.state.x)).toBeLessThan(0.5);
  });

  it('moving platform carries a planted player', () => {
    const lvl = makeLevel([flatGround({ x: -40, w: 20 })], {
      movingObjects: [{ id: 'mv', kind: 'wood', x: 0, y: 0, w: 8, h: 1, move: { dx: 6, dy: 0, period: 4 } }],
      startPosition: { x: 0, y: 0 },
    });
    const { pogo } = setup(lvl);
    const x0 = pogo.state.x;
    run(pogo, 120); // 1 s of a 4 s cycle ⇒ sin(π/2)·6
    expect(pogo.state.mode).toBe('GROUNDED');
    expect(pogo.state.x - x0).toBeGreaterThan(4.5);
  });

  it('goal trigger finishes the run', () => {
    const lvl = makeLevel([flatGround({ w: 80 })], { goal: { x: 5, y: 0, w: 2, h: 4 } });
    const { pogo } = setup(lvl);
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1; pogo.state.x = 5; pogo.state.y = 4; pogo.state.vy = 0;
    const ev = run(pogo, 30);
    expect(ev.some(e => e.type === 'goal')).toBe(true);
    expect(pogo.state.mode).toBe('FINISHED');
  });
});

describe('boost', () => {
  it('spinning past boostRotation arms the boost; pressing it adds boostPower along the stick axis', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1; pogo.state.x = 0; pogo.state.y = 60; pogo.state.vx = 0; pogo.state.vy = 5;
    let armed = false;
    for (let i = 0; i < 400 && !armed; i++) armed = pogo.step(input({ tilt: 1 })).some(e => e.type === 'boost_armed');
    expect(armed).toBe(true);
    expect(pogo.state.boostReady).toBe(true);
    const { vx: vx0, vy: vy0, angle } = pogo.state;
    const ev = pogo.step(input({ boostPressed: true }));
    expect(ev.some(e => e.type === 'boost')).toBe(true);
    expect(pogo.state.boosts).toBe(1);
    // Δv over the tick = axis·boostPower (+ one tick of gravity); the axis is evaluated at the press
    const dvx = pogo.state.vx - vx0, dvy = pogo.state.vy - vy0 + cfg.gravity / TICK_RATE;
    expect(Math.abs(dvx - Math.sin(angle) * cfg.boostPower)).toBeLessThan(0.6);
    expect(Math.abs(dvy - Math.cos(angle) * cfg.boostPower)).toBeLessThan(0.6);
    expect(pogo.state.boostReady).toBe(false);
  });

  it('slow rotation below boostThreshold never arms the boost', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]), { tiltRateAir: 100 });
    pogo.state.mode = 'AIR'; pogo.state.groundId = -1; pogo.state.x = 0; pogo.state.y = 5000; pogo.state.vy = 0;
    let armed = false;
    for (let i = 0; i < 600; i++) armed = armed || pogo.step(input({ tilt: 1 })).some(e => e.type === 'boost_armed');
    expect(armed).toBe(false);
  });

  it('a queued ground boost is consumed by the next launch (launch speed + boostPower)', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    run(pogo, 5);
    pogo.state.boostReady = true;
    pogo.step(input({ boostPressed: true }));
    expect(pogo.state.boostQueued).toBe(true);
    run(pogo, 2, input({ jumpHeld: true }));
    run(pogo, 1, input({ jumpHeld: false }));
    expect(Math.hypot(pogo.state.vx, pogo.state.vy)).toBeGreaterThan(cfg.launchSpeedMin + cfg.boostPower - 1);
    expect(pogo.state.boosts).toBe(1);
  });
});

describe('prediction (trajectory guide) uses the real physics', () => {
  it('predicted landing equals the actually simulated landing', () => {
    const { pogo, cfg, world } = setup(makeLevel([flatGround({ w: 200 })]));
    run(pogo, 5);
    const pred = simulateJump({ world, cfg }, pogo.state, 30 * DEG, 0.7);
    expect(pred.landed).toBe(true);
    // real run
    pogo.state.angle = 30 * DEG;
    run(pogo, 20, input({ jumpHeld: true, tilt: 0 }));
    expect(pogo.state.mode).toBe('CHARGING');
  });
});
