import { describe, expect, it } from 'vitest';
import { flatGround, input, makeLevel, run, setup } from './helpers';
import { PARAM_DEFS, createPhysicsConfig, paramsByStatus } from '../src/sim/PhysicsConfig';
import { PhysicsWorld } from '../src/sim/PhysicsWorld';
import { type PogoState, createPogoState, placeInAir, rotateAboutTip } from '../src/sim/PogoState';
import { type Frame, type SimContext, newFrame } from '../src/sim/core/frame';
import { airStep, capSpeed } from '../src/sim/core/air';
import { applyRotation } from '../src/sim/core/rotation';
import { groundStep } from '../src/sim/core/groundPressure';
import { chargeStep, impactOf, springWindow } from '../src/sim/core/charge';
import { launch } from '../src/sim/core/launch';
import { wallBounce } from '../src/sim/core/wallBounce';
import { updateSlide } from '../src/sim/core/slide';
import { boostCheck } from '../src/sim/core/boost';
import { groundContact } from '../src/sim/core/groundContact';
import { integratePosition, platformRelease, updateHull } from '../src/sim/core/integration';
import { probeGround } from '../src/sim/core/collision';
import { dtTicks } from '../src/sim/units';
import { asinD, sinD, wrap180 } from '../src/sim/math';
import { simulateLaunch } from '../src/sim/prediction';

/**
 * LOCKED SPEC regression suite — one block per equation of Pogostuck_Physics_LOCKED_SPEC.md (E1…E18), the constants
 * table (§5) and the validation targets (§10). Expected values are written out from the spec, NOT read from the
 * implementation. Nothing here may be "fixed" by changing a constant: a failing test means the implementation is wrong.
 */

// ── §5 locked constants, transcribed from the spec (stored doubles where the spec lists them) ───────────────────
const SPEC_5: Record<string, number> = {
  gravity: 8.5, airDrag: 0.05000000074505806, maxSpeed: 300, slideZGain: 4, groundPressure: 24, groundTurnDivisor: 2,
  turnTarget: 32, turnResponse: 0.5249999761581421, rotationHullLift: 16, tipPivotLimit: 256, probeExtension: 6, probeHalfSize: 4,
  impactExponent: 0.925000011920929, impactGain: 1.649999976158142, impactBoostBonus: 20, loadMaxFloor: 95, loadMaxFloorBoostBonus: 25,
  loadMaxCap: 300, loadMinExponent: 0.8999999761581421, loadMinFloor: 40, chargeRate: 16, minLaunchLoad: 2,
  launchSpeedPerLoad: 0.7423499822616577, normalAngleOffset: 90, normalBlendClamp: 45, normalBlendFactor: 0.1875,
  launchSlideCarryX: 0.25, launchSlideCarryZ: 0, launchSpinPerTilt: 0.12449999898672104, launchSpinTiltClamp: 45,
  slopeSpinExponent: 0.75, slopeSpinGain: 1.5, slopeSpinLaunchFactor: 0.25, noGroundTime: 2, jumpTimerGain: 0.44999998807907104,
  boostRotation: 285, bounceReflect: -2, bounceDirWeight: 0.8999999761581421, bounceSpeedFactor: 0.4000000059604645, bounceMinSpeed: 28,
  bounceSlopeCap: 1, bounceXScale: 0.875, bounceSpin: 0.5, slopeSpinBounceFactor: 0.20000000298023224,
  slideTargetSpeed: 48, slideResponse: 0.25, slideAccelClamp: 1.350000023841858, slideDecay: 0.5, slideStop: 0.25,
  platformDownReduction: 0.75, ledgeExitPop: 5, hullHalfX: 12.5, hullMaxZ: 30, hullMinZBase: -55, hullExtLimit: -12.25,
  springRelaxRate: 120, springExtAmp: 18, timeFactor: 973 / 1024, qPerMetre: 52, tickRate: 120,
};

const DT = dtTicks(createPhysicsConfig());

/** A world with a very low floor so free-flight unit tests never touch anything. */
function emptyCtx(): { ctx: SimContext; s: PogoState; f: Frame } {
  const cfg = createPhysicsConfig();
  const world = new PhysicsWorld(makeLevel([flatGround({ y: -500, w: 20 })], { startPosition: { x: 0, y: -500 }, killY: -2000, bounds: { minX: -50, maxX: 150, minY: -600, maxY: 150 } }), cfg.qPerMetre);
  const s = createPogoState(world, cfg);
  placeInAir(cfg, s, 0, 0);
  const f = newFrame();
  f.dt = DT; f.ev = [];
  return { ctx: { world, cfg }, s, f };
}

describe('§5 constants: every locked value is present, exact and labelled', () => {
  it('has exactly the spec values (stored doubles included)', () => {
    for (const [k, v] of Object.entries(SPEC_5)) expect((PARAM_DEFS as Record<string, { value: number }>)[k]?.value, k).toBe(v);
  });
  it('has no TUNE_ME / estimated parameter: every status is LOCKED_A, LOCKED_AB, SUPPLIED or DESIGN', () => {
    const known = new Set([...paramsByStatus('LOCKED_A'), ...paramsByStatus('LOCKED_AB'), ...paramsByStatus('SUPPLIED'), ...paramsByStatus('DESIGN')]);
    expect(known.size).toBe(Object.keys(PARAM_DEFS).length);
  });
  it('every locked parameter cites its spec row; supplied/design ones do not pretend to be original', () => {
    for (const [k, d] of Object.entries(PARAM_DEFS)) {
      if (d.status.startsWith('LOCKED')) expect(d.ref, k).toMatch(/^(P|T|U)\d|^—$/);
      else expect(d.grade, k).toBe('D');
    }
  });
  it('Δt = 16·973/1024/120 T per tick (spec §2): 0.126693 T at 120 Hz', () => {
    expect(DT).toBeCloseTo((16 * (973 / 1024)) / 120, 12);
    expect(DT).toBeCloseTo(0.126693, 6);
  });
});

describe('E1 air physics: gravity and drag', () => {
  it('v_x ← v_x − 0.05·v_x·Δt and v_z ← v_z − 8.5·Δt (γ = 0)', () => {
    const { ctx, s, f } = emptyCtx();
    s.qvx = 100; s.qvy = 50;
    airStep(ctx, s, f);
    expect(s.qvx).toBeCloseTo(100 - 0.05000000074505806 * 100 * DT, 12);
    expect(s.qvy).toBeCloseTo(50 - 8.5 * DT, 12);
  });
  it('there is no vertical drag', () => {
    const { ctx, s, f } = emptyCtx();
    s.qvx = 0; s.qvy = 200;
    airStep(ctx, s, f);
    expect(s.qvy).toBeCloseTo(200 - 8.5 * DT, 12);
  });
  it('general form: with γ = 90° gravity acts along −x (rotated frame)', () => {
    const { ctx, s, f } = emptyCtx();
    s.gamma = 90; s.qvx = 0; s.qvy = 0;
    airStep(ctx, s, f);
    expect(s.qvx).toBeCloseTo(8.5 * DT, 9);   // R_90·(0, −8.5Δt) = (+8.5Δt, 0)
    expect(Math.abs(s.qvy)).toBeLessThan(1e-9);
  });
  it('full tick: velocity updates first, then the position moves by the NEW velocity (semi-implicit Euler)', () => {
    const { ctx, s } = emptyCtx();
    const ev: never[] = [];
    void ev;
    const x0 = s.qx, y0 = s.qy;
    s.qvx = 20; s.qvy = 10;
    const { pogo } = setup(makeLevel([flatGround({ y: -500, w: 20 })], { startPosition: { x: 0, y: -500 }, killY: -2000, bounds: { minX: -50, maxX: 150, minY: -600, maxY: 150 } }));
    placeInAir(pogo.cfg, pogo.state, x0 / 52, y0 / 52, 20, 10);
    pogo.step();
    const vy = 10 - 8.5 * DT, vx = 20 - 0.05000000074505806 * 20 * DT;
    expect(pogo.state.qvy).toBeCloseTo(vy, 10);
    expect(pogo.state.qy - y0).toBeCloseTo(vy * DT, 10);
    expect(pogo.state.qx - x0).toBeCloseTo(vx * DT, 10);
    void ctx;
  });
});

describe('E2 speed cap', () => {
  it('|v| > 300 ⇒ v ← 300·v/|v| (direction preserved)', () => {
    const { ctx, s } = emptyCtx();
    s.qvx = 400; s.qvy = 300;
    capSpeed(ctx, s);
    expect(Math.hypot(s.qvx, s.qvy)).toBeCloseTo(300, 9);
    expect(s.qvx / s.qvy).toBeCloseTo(4 / 3, 9);
  });
  it('|v| ≤ 300 is untouched', () => {
    const { ctx, s } = emptyCtx();
    s.qvx = 120; s.qvy = -200;
    capSpeed(ctx, s);
    expect(s.qvx).toBe(120); expect(s.qvy).toBe(-200);
  });
  it('a full free-fall never exceeds 300 Q/T', () => {
    const { pogo } = setup(makeLevel([flatGround({ y: -500, w: 20 })], { startPosition: { x: 0, y: -500 }, killY: -2000, bounds: { minX: -50, maxX: 150, minY: -600, maxY: 150 } }));
    placeInAir(pogo.cfg, pogo.state, 0, 0, 0, -100);
    let max = 0;
    for (let i = 0; i < 1500; i++) { pogo.step(); max = Math.max(max, Math.hypot(pogo.state.qvx, pogo.state.qvy)); }
    expect(max).toBeLessThanOrEqual(300 + 1e-9);
    expect(max).toBeGreaterThan(299.9);
  });
});

describe('E3 position integration', () => {
  it('d = ((v_x + s_x)·Δt, (v_z + 4·s_z)·Δt)', () => {
    const { ctx, s, f } = emptyCtx();
    s.qvx = 10; s.qvy = 20; s.sx = 6; s.sy = 3;
    const x0 = s.qx, y0 = s.qy;
    integratePosition(ctx, s, f);
    expect(s.qx - x0).toBeCloseTo((10 + 6) * DT, 12);
    expect(s.qy - y0).toBeCloseTo((20 + 4 * 3) * DT, 12);
    expect(f.dispX).toBeCloseTo((10 + 6) * DT, 12);
  });
});

describe('E4 turn', () => {
  it('ω ← ω + (ω_t − ω)·0.525·Δt/(1+√J); θ ← θ + Lb·ω·Δt (air, Lb = 1)', () => {
    const { ctx, s, f } = emptyCtx();
    f.uLeft = 1; f.uRight = 0; s.omega = 0; s.jumpTimer = 0; s.theta = 0;
    applyRotation(ctx, s, f);
    const w = (32 - 0) * 0.5249999761581421 * DT / (1 + 0);
    expect(s.omega).toBeCloseTo(w, 12);
    expect(s.theta).toBeCloseTo(1 * w * DT, 12);
  });
  it('left input drives θ positive (leaning toward −x), right input negative', () => {
    const a = emptyCtx(); a.f.uLeft = 1; applyRotation(a.ctx, a.s, a.f); expect(a.s.omega).toBeGreaterThan(0);
    const b = emptyCtx(); b.f.uRight = 1; applyRotation(b.ctx, b.s, b.f); expect(b.s.omega).toBeLessThan(0);
  });
  it('the jump timer slows the response by 1/(1+√J)', () => {
    const { ctx, s, f } = emptyCtx();
    f.uLeft = 1; s.jumpTimer = 4;
    applyRotation(ctx, s, f);
    expect(s.omega).toBeCloseTo(32 * 0.5249999761581421 * DT / 3, 12);
  });
  it('on the ground the same ω rotates the stick 1/3 as fast (Lb = 1/(1+2g))', () => {
    const air = emptyCtx(); air.s.omega = 20; air.f.uLeft = 1; air.s.grounded = false;
    const gnd = emptyCtx(); gnd.s.omega = 20; gnd.f.uLeft = 1; gnd.s.grounded = true;
    gnd.s.tipPrevX = 1e9; // pivot memory irrelevant here
    const t0 = air.s.theta;
    applyRotation(air.ctx, air.s, air.f);
    applyRotation(gnd.ctx, gnd.s, gnd.f);
    expect((gnd.s.theta - t0) * 3).toBeCloseTo(air.s.theta - t0, 9);
  });
  it('the target rate is 32°/T: ω converges to 32 in the air', () => {
    const { ctx, s, f } = emptyCtx();
    f.uLeft = 1;
    for (let i = 0; i < 400; i++) applyRotation(ctx, s, f);
    expect(s.omega).toBeGreaterThan(31.99);
    expect(s.omega).toBeLessThanOrEqual(32);
  });
  it('analog input scales the target: half tilt ⇒ ω → 16', () => {
    const { ctx, s, f } = emptyCtx();
    f.uLeft = 0.5;
    for (let i = 0; i < 400; i++) applyRotation(ctx, s, f);
    expect(s.omega).toBeCloseTo(16, 2);
  });
});

describe('E5 ground pressure and tip pivot', () => {
  it('v ← −24·n (n = (0.6, 0.8))', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.nx = 0.6; s.ny = 0.8; s.thetaN = Math.atan2(0.8, 0.6) / (Math.PI / 180);
    const tip = { x: s.qx + sinD(s.theta) * 51, y: s.qy - Math.cos(0) * 51 };
    s.tipPrevX = tip.x; s.tipPrevY = tip.y;
    groundStep(ctx, s, f);
    expect(s.qvx).toBeCloseTo(-24 * 0.6, 9);
    expect(s.qvy).toBeCloseTo(-24 * 0.8, 9);
  });
  it('pivot: the body is moved by tip_prev − tip when both components are < 256 Q', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.nx = 0; s.ny = 1; s.thetaN = 90;
    const x0 = s.qx, y0 = s.qy;
    s.tipPrevX = s.qx + 10; s.tipPrevY = s.qy - 51 + 5;   // tip is currently at (qx, qy − 51)
    groundStep(ctx, s, f);
    expect(s.qx - x0).toBeCloseTo(10, 9);
    expect(s.qy - y0).toBeCloseTo(5, 9);
  });
  it('pivot is NOT applied when a component is ≥ 256 Q', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.nx = 0; s.ny = 1; s.thetaN = 90;
    const x0 = s.qx;
    s.tipPrevX = s.qx + 300; s.tipPrevY = s.qy - 51;
    groundStep(ctx, s, f);
    expect(s.qx).toBe(x0);
  });
  it('turning on the ground pivots about the tip: the tip does not move while the stick rotates', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    const s = pogo.state;
    const tipX = () => s.qx + sinD(s.theta) * 51;
    const x0 = tipX();
    for (let i = 0; i < 15; i++) pogo.step(input({ tilt: -1 }));   // lean left, before the first launch
    expect(s.theta).toBeGreaterThan(2);
    expect(Math.abs(tipX() - x0)).toBeLessThan(0.05);
  });
});

describe('E6 ground probe', () => {
  it('reaches tip + 6 Q beyond the tip end, with a ±4 Q probe: hit below 65 Q, none above', () => {
    const cfg = createPhysicsConfig();
    const world = new PhysicsWorld(makeLevel([flatGround({ w: 200 })]), cfg.qPerMetre);
    const s = createPogoState(world, cfg);
    const probe = { hit: false, nx: 0, ny: 0, collider: -1 };
    s.theta = 0;
    s.qx = 0; s.qy = 64.9; probeGround({ world, cfg }, s, probe);
    expect(probe.hit).toBe(true); expect(probe.ny).toBeCloseTo(1, 6);
    s.qy = 65.2; probeGround({ world, cfg }, s, probe);
    expect(probe.hit).toBe(false);
  });
  it('the probe lengthens by |s_z|', () => {
    const cfg = createPhysicsConfig();
    const world = new PhysicsWorld(makeLevel([flatGround({ w: 200 })]), cfg.qPerMetre);
    const s = createPogoState(world, cfg);
    const probe = { hit: false, nx: 0, ny: 0, collider: -1 };
    s.theta = 0; s.qx = 0; s.qy = 70; s.sy = -10;
    probeGround({ world, cfg }, s, probe);
    expect(probe.hit).toBe(true);
    s.sy = 0; probeGround({ world, cfg }, s, probe);
    expect(probe.hit).toBe(false);
  });
});

describe('E7 landing force and spring window', () => {
  const I = (v: number) => 1.649999976158142 * Math.pow(v, 0.925000011920929);
  it('I = 1.65·|v|^0.925', () => {
    const cfg = createPhysicsConfig();
    expect(impactOf(cfg, 70.5)).toBeCloseTo(I(70.5), 9);
    expect(impactOf(cfg, 0)).toBe(0);
  });
  it('L_min = max(40, I^0.9), L_max = clamp(I + 20p, 95 + 25p, 300)', () => {
    const cfg = createPhysicsConfig();
    for (const v of [0, 20, 50, 70.5, 100, 200, 276.8, 300, 1000]) {
      const w0 = springWindow(cfg, v, 0), w1 = springWindow(cfg, v, 1);
      expect(w0.min).toBeCloseTo(Math.max(40, Math.pow(I(v), 0.8999999761581421)), 9);
      expect(w0.max).toBeCloseTo(Math.min(300, Math.max(95, I(v))), 9);
      expect(w1.max).toBeCloseTo(Math.min(300, Math.max(120, I(v) + 20)), 9);
      expect(w1.min).toBeCloseTo(w0.min, 9);
    }
  });
  it('a landing with v = 0 gives the window [40, 95] (the spawn window)', () => {
    const w = springWindow(createPhysicsConfig(), 0, 0);
    expect(w.min).toBe(40); expect(w.max).toBe(95);
  });
  it('real landing: the window of the landing tick is E7 of the speed at that tick', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    placeInAir(cfg, pogo.state, 0, 8, 0, -120);
    let speed = 0, landed = false;
    for (let i = 0; i < 400 && !landed; i++) {
      const ev = pogo.step();
      if (ev.some(e => e.type === 'land')) { landed = true; speed = Math.hypot(pogo.state.qvx, pogo.state.qvy); }
    }
    expect(landed).toBe(true);
    const w = springWindow(cfg, speed, 0);
    expect(pogo.state.loadMin).toBeCloseTo(w.min, 9);
    expect(pogo.state.loadMax).toBeCloseTo(w.max, 9);
    expect(pogo.state.lastImpact).toBeCloseTo(I(speed), 9);
    expect(pogo.state.loadMax).toBeGreaterThan(95); // a fast fall opens the window
  });
});

describe('E8 charge', () => {
  it('while L < L_min the pogo charges by itself at 16·Δt per tick; no hold needed', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 0; s.loadMin = 40; s.loadMax = 95; f.hold = false;
    expect(chargeStep(ctx, s, f)).toBe('charging');
    expect(s.load).toBeCloseTo(16 * DT, 12);
  });
  it('with the hold it keeps charging past L_min up to L_max, and L is clamped to L_max', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 94; s.loadMin = 40; s.loadMax = 95; f.hold = true;
    expect(chargeStep(ctx, s, f)).toBe('charging');
    expect(s.load).toBe(95);
    expect(chargeStep(ctx, s, f)).toBe('launch');
  });
  it('without the hold and L ≥ L_min it launches; the launch needs L > 2', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.loadMin = 0; s.loadMax = 95; f.hold = false;
    s.load = 2; expect(chargeStep(ctx, s, f)).toBe('idle');
    s.load = 2.0001; expect(chargeStep(ctx, s, f)).toBe('launch');
  });
  it('the spring bone follows the load while charging (E18: P_b = P_max = L)', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 10; s.loadMin = 40; s.loadMax = 95;
    chargeStep(ctx, s, f);
    expect(s.springBone).toBe(s.load); expect(s.springBoneMax).toBe(s.load);
  });
  it('from the spawn: no hold ⇒ launches at the first L ≥ 40 (ticks = 20 + 1); hold ⇒ launches at exactly L = 95 (47 + 1)', () => {
    const idle = setup(makeLevel([flatGround({ w: 200 })])).pogo;
    let n = 0; while (!run(idle, 1).some(e => e.type === 'launch') && n++ < 100);
    expect(n).toBe(Math.ceil(40 / (16 * DT)));            // 20 charging ticks, the launch is the next tick (index 20)
    expect(idle.state.loadLast).toBeGreaterThanOrEqual(40);
    expect(idle.state.loadLast).toBeLessThan(40 + 16 * DT + 1e-9);
    const held = setup(makeLevel([flatGround({ w: 200 })])).pogo;
    n = 0; while (!run(held, 1, input({ jumpHeld: true })).some(e => e.type === 'launch') && n++ < 200);
    expect(n).toBe(Math.ceil(95 / (16 * DT)));            // 47 charging ticks
    expect(held.state.loadLast).toBe(95);
  });
});

describe('E9 launch velocity and angle', () => {
  const launchFrom = (theta: number, nx: number, ny: number, load = 95, sx = 0, sy = 0) => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = load; s.loadMax = 95; s.theta = theta; s.sx = sx; s.sy = sy;
    s.nx = nx; s.ny = ny; s.thetaN = Math.atan2(ny, nx) / (Math.PI / 180);
    launch(ctx, s, f);
    return s;
  };
  it('upright on flat ground: V = (0, 0.74235·L)', () => {
    const s = launchFrom(0, 0, 1);
    expect(s.qvx).toBeCloseTo(0, 9);
    expect(s.qvy).toBeCloseTo(0.7423499822616577 * 95, 9);
  });
  it('a = θ + 0.1875·clamp(wrap180(θ_n − 90 − θ), ±45); V = 0.74235·L·(−sin a, cos a)', () => {
    const s = launchFrom(30, 0, 1);
    const a = 30 + 0.1875 * (-30);
    const sp = 0.7423499822616577 * 95;
    expect(s.qvx).toBeCloseTo(-sinD(a) * sp, 9);
    expect(s.qvy).toBeCloseTo(Math.cos(a * Math.PI / 180) * sp, 9);
    expect(s.qvx).toBeLessThan(0); // θ > 0 leans toward −x and so does the launch
  });
  it('the normal blend is clamped to ±45°', () => {
    const s = launchFrom(-80, 0, 1);
    const a = -80 + 0.1875 * 45;
    expect(s.qvx).toBeCloseTo(-sinD(a) * 0.7423499822616577 * 95, 9);
  });
  it('on a slope the launch is pulled toward the surface normal', () => {
    const nx = -0.5, ny = Math.sqrt(3) / 2;               // normal leaning left (surface rising to the right), θ_n = 120°
    const s = launchFrom(0, nx, ny);
    const a = 0 + 0.1875 * 30;
    expect(s.qvx).toBeCloseTo(-sinD(a) * 0.7423499822616577 * 95, 8);
    expect(s.qvx).toBeLessThan(0);
  });
  it('v ← (V_x + 0.25·s_x, V_z + 0·s_z): the incoming slide is carried 25 % horizontally, 0 % vertically', () => {
    const s = launchFrom(0, 0, 1, 95, 8, 5);
    expect(s.qvx).toBeCloseTo(0.25 * 8, 9);
    expect(s.qvy).toBeCloseTo(0.7423499822616577 * 95, 9);
  });
  it('the velocity is REPLACED (the pre-launch velocity is not kept)', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 50; s.loadMax = 95; s.nx = 0; s.ny = 1; s.thetaN = 90;
    s.qvx = 200; s.qvy = -250;
    launch(ctx, s, f);
    expect(s.qvx).toBeCloseTo(0, 9);
    expect(s.qvy).toBeCloseTo(0.7423499822616577 * 50, 9);
  });
});

describe('E10 launch spin', () => {
  it('ω += 0.1245·clamp(wrap180(θ − γ), ±45) − 0.25·1.5·sgn(σ)·|σ|^0.75, σ = asin(n_x)°', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 95; s.loadMax = 95; s.theta = 30; s.nx = 0; s.ny = 1; s.thetaN = 90; s.omega = 0;
    launch(ctx, s, f);
    expect(s.omega).toBeCloseTo(0.12449999898672104 * 30, 9);
  });
  it('tilt term clamps at ±45°', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 95; s.loadMax = 95; s.theta = 100; s.nx = 0; s.ny = 1; s.thetaN = 90;
    launch(ctx, s, f);
    expect(s.omega).toBeCloseTo(0.12449999898672104 * 45, 9);
  });
  it('the slope term subtracts 0.375·sgn(σ)|σ|^0.75', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 95; s.loadMax = 95; s.theta = 0; s.omega = 1;
    s.nx = -0.5; s.ny = Math.sqrt(3) / 2; s.thetaN = 120;
    launch(ctx, s, f);
    const sigma = asinD(-0.5);
    expect(sigma).toBeCloseTo(-30, 9);
    expect(s.omega).toBeCloseTo(1 + 0 - 0.25 * 1.5 * -1 * Math.pow(30, 0.75), 9);
  });
});

describe('E11 after launch', () => {
  it('L ← 0, g ← 0, N ← 2 T, J ← 0.45·√L_max, p ← 0, θ_j ← wrap180(θ), L_last ← L; jumps++', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.load = 80; s.loadMax = 100; s.theta = 380; s.boost = 1; s.nx = 0; s.ny = 1; s.thetaN = 90;
    const jumps = s.jumps;
    launch(ctx, s, f);
    expect(s.load).toBe(0); expect(s.grounded).toBe(false); expect(s.noGround).toBe(2);
    expect(s.jumpTimer).toBeCloseTo(0.44999998807907104 * Math.sqrt(100), 12);
    expect(s.boost).toBe(0); expect(s.loadLast).toBe(80);
    expect(s.thetaJump).toBeCloseTo(wrap180(380), 12);
    expect(s.theta).toBeCloseTo(20, 12);
    expect(s.jumps).toBe(jumps + 1);
    expect(f.ev.some(e => e.type === 'launch')).toBe(true);
  });
  it('after a launch the pogo cannot re-ground for N = 2 T (≈ 16 ticks)', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    let t = 0; while (!run(pogo, 1).some(e => e.type === 'launch') && t++ < 100);
    let n = 0;
    while (pogo.state.noGround > 0 && n < 40) { run(pogo, 1); expect(pogo.state.grounded).toBe(false); n++; }
    expect(n).toBe(Math.ceil(2 / DT));
  });
});

describe('E12 timers', () => {
  it('N and J decrease by Δt per tick (J only while airborne)', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ y: -500, w: 20 })], { startPosition: { x: 0, y: -500 }, killY: -2000, bounds: { minX: -50, maxX: 150, minY: -600, maxY: 150 } }));
    placeInAir(cfg, pogo.state, 0, 0);
    pogo.state.noGround = 1; pogo.state.jumpTimer = 2;
    pogo.step();
    expect(pogo.state.noGround).toBeCloseTo(1 - DT, 12);
    expect(pogo.state.jumpTimer).toBeCloseTo(2 - DT, 12);
  });
  it('J is held at 0 while grounded', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 200 })]));
    pogo.state.jumpTimer = 3;
    pogo.step();
    expect(pogo.state.jumpTimer).toBe(0);
  });
});

describe('E13 boost (power jump)', () => {
  it('p ← 1 when int(|θ − θ_j|) > 285 in the air, not at exactly 285.99', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = false; s.thetaJump = 10;
    s.theta = 10 + 285.99; boostCheck(ctx, s, f); expect(s.boost).toBe(0);
    s.theta = 10 + 286; boostCheck(ctx, s, f); expect(s.boost).toBe(1);
    expect(f.ev.some(e => e.type === 'boost_armed')).toBe(true);
  });
  it('works for either rotation direction', () => {
    const { ctx, s, f } = emptyCtx();
    s.thetaJump = 0; s.theta = -300; boostCheck(ctx, s, f);
    expect(s.boost).toBe(1);
  });
  it('is not evaluated on the ground', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.thetaJump = 0; s.theta = 500; boostCheck(ctx, s, f);
    expect(s.boost).toBe(0);
  });
  it('spinning in the air with the turn input arms it, and the next landing opens a boosted window [≥ 120]', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 200 })]));
    placeInAir(cfg, pogo.state, 0, 30, 0, 0);
    let armed = false, landed = false;
    for (let i = 0; i < 2000 && !landed; i++) {
      const ev = pogo.step(input({ tilt: -1 }));
      if (ev.some(e => e.type === 'boost_armed')) armed = true;
      if (ev.some(e => e.type === 'land')) landed = true;
    }
    expect(armed).toBe(true);
    expect(pogo.state.loadMax).toBeGreaterThanOrEqual(120);
  });
  it('a boosted launch (L_last > 95) counts as a boost and clears p', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.boost = 1; s.load = 120; s.loadMax = 150; s.nx = 0; s.ny = 1; s.thetaN = 90;
    const b = s.boosts;
    launch(ctx, s, f);
    expect(s.boosts).toBe(b + 1); expect(s.boost).toBe(0);
    expect(f.ev.some(e => e.type === 'boost')).toBe(true);
  });
});

describe('E14 wall bounce', () => {
  const bounce = (vx: number, vy: number, nx: number, ny: number, sx = 0, sy = 0, theta = 0, omega = 0) => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = false; s.noGround = 0; s.qvx = vx; s.qvy = vy; s.sx = sx; s.sy = sy; s.theta = theta; s.omega = omega; s.load = 5; s.boost = 1;
    f.touched = true; f.touchNx = nx; f.touchNy = ny; f.dispX = vx * DT; f.dispY = vy * DT;
    wallBounce(ctx, s, f);
    return s;
  };
  it('hitting a vertical wall head-on: S = max(28, 0.4|v|), d = −x̂, v_x = 0.875·(−S)', () => {
    const s = bounce(100, 0, -1, 0);
    expect(s.qvx).toBeCloseTo(0.875 * -(0.4000000059604645 * 100), 9);
    expect(s.qvy).toBeCloseTo(0, 9);
  });
  it('the minimum rebound speed is 28 Q/T', () => {
    const s = bounce(10, 0, -1, 0);
    expect(s.qvx).toBeCloseTo(0.875 * -28, 9);
  });
  it('there is no restitution: the rebound speed does not scale like e·|v| (doubling |v| less than doubles it near the floor)', () => {
    const a = Math.abs(bounce(40, 0, -1, 0).qvx), b = Math.abs(bounce(20, 0, -1, 0).qvx);
    expect(a / b).toBeLessThan(2);         // 0.875·40·... vs 0.875·28
  });
  it('a ceiling (n_z = −1) gives S = 0: the pogo stops dead', () => {
    const s = bounce(0, 100, 0, -1);
    expect(Math.hypot(s.qvx, s.qvy)).toBeCloseTo(0, 9);
  });
  it('a floor-like contact: b_z = n_z·S; the slide is added afterwards', () => {
    const s = bounce(0, -100, 0, 1, 3, 2);
    expect(s.qvy).toBeCloseTo(1 * (0.4000000059604645 * 100) + 2, 9);
    expect(s.qvx).toBeCloseTo(0.875 * 0 + 3, 9);
  });
  it('direction d = 0.9·r̂ + n, normalised: an oblique hit keeps its tangential direction', () => {
    const s = bounce(80, 60, -1, 0);          // moving up-right into a wall: reflected up-left
    expect(s.qvx).toBeLessThan(0);
    expect(s.qvy).toBeGreaterThan(0);
    const S = Math.max(28, 0.4000000059604645 * 100) * 1;
    // r̂ = (−0.8, 0.6);  d = 0.9·r̂ + n with n = (−1, 0)
    const w = 0.8999999761581421;
    const dx = w * -0.8 + -1, dy = w * 0.6;
    const L = Math.hypot(dx, dy);
    expect(s.qvx).toBeCloseTo(0.875 * S * dx / L, 9);
    expect(s.qvy).toBeCloseTo(S * dy / L, 9);
  });
  it('spin: ω ← 0.5·wrap180(γ − θ) − 0.2·1.5·sgn(σ)|σ|^0.75 (assignment)', () => {
    const s = bounce(100, 0, -1, 0, 0, 0, 40, 77);
    const sigma = -90;
    expect(s.omega).toBeCloseTo(0.5 * wrap180(0 - 40) - 0.20000000298023224 * 1.5 * Math.sign(sigma) * Math.pow(90, 0.75), 9);
  });
  it('side effects: N ← 2, p ← 0, L ← 0, θ wrapped, θ_j ← θ', () => {
    const s = bounce(100, 0, -1, 0, 0, 0, 400);
    expect(s.noGround).toBe(2); expect(s.boost).toBe(0); expect(s.load).toBe(0);
    expect(s.theta).toBeCloseTo(40, 12); expect(s.thetaJump).toBeCloseTo(40, 12);
  });
  it('in the full simulation a flying pogo bounces off a wall with no gain of speed and gets N = 2', () => {
    const lvl = makeLevel([flatGround({ w: 200 })], { obstacles: [{ id: 'wall', pts: [{ x: 6, y: -6 }, { x: 8, y: -6 }, { x: 8, y: 40 }, { x: 6, y: 40 }] }] });
    const { pogo, cfg } = setup(lvl);
    placeInAir(cfg, pogo.state, 2, 10, 120, 0);
    let hit = false, vxAfter = 0;
    for (let i = 0; i < 100 && !hit; i++) { const ev = pogo.step(); if (ev.some(e => e.type === 'wall_hit')) { hit = true; vxAfter = pogo.state.qvx; } }
    expect(hit).toBe(true);
    expect(vxAfter).toBeLessThan(0);
    expect(Math.abs(vxAfter)).toBeLessThanOrEqual(0.875 * Math.max(28, 0.4 * 125) + 1);
    expect(pogo.state.noGround).toBeGreaterThan(1.5);
  });
});

describe('E15 slide / ice', () => {
  it('slide mode: s_i ← s_i + clamp(0.25·(t_i − s_i), ±1.35)·Δt with t = 48·normalize(n + (0,−1))', () => {
    const { ctx, s, f } = emptyCtx();
    s.slideMode = true; s.grounded = true; s.nx = -0.5; s.ny = Math.sqrt(3) / 2; s.sx = 0; s.sy = 0;
    updateSlide(ctx, s, f);
    expect(s.sx).toBeCloseTo(-1.350000023841858 * DT, 12);   // 0.25·t_x clamps to −1.35
    expect(s.sy).toBeCloseTo(-1.350000023841858 * DT, 12);
  });
  it('near the target the unclamped branch applies: 0.25·(t − s)', () => {
    const { ctx, s, f } = emptyCtx();
    s.slideMode = true; s.grounded = true; s.nx = -0.5; s.ny = Math.sqrt(3) / 2;
    const len = Math.hypot(-0.5, Math.sqrt(3) / 2 - 1);
    const tx = 48 * -0.5 / len, ty = 48 * (Math.sqrt(3) / 2 - 1) / len;
    s.sx = tx + 2; s.sy = ty + 2;
    updateSlide(ctx, s, f);
    expect(s.sx).toBeCloseTo(tx + 2 + (0.25 * -2) * DT, 9);
    expect(s.sy).toBeCloseTo(ty + 2 + (0.25 * -2) * DT, 9);
  });
  it('on a flat floor n + g = 0 ⇒ t = 0 and the slide decays toward 0 at ≤ 1.35·Δt per tick', () => {
    const { ctx, s, f } = emptyCtx();
    s.slideMode = true; s.grounded = true; s.nx = 0; s.ny = 1; s.sx = 40; s.sy = 0;
    updateSlide(ctx, s, f);
    expect(s.sx).toBeCloseTo(40 - 1.350000023841858 * DT, 9);
  });
  it('out of slide mode: |s| < 0.25 or airborne ⇒ 0; otherwise s·(1 − 0.5·Δt)', () => {
    const a = emptyCtx(); a.s.slideMode = false; a.s.grounded = true; a.s.sx = 0.2; a.s.sy = 0; updateSlide(a.ctx, a.s, a.f); expect(a.s.sx).toBe(0);
    const b = emptyCtx(); b.s.slideMode = false; b.s.grounded = false; b.s.sx = 30; updateSlide(b.ctx, b.s, b.f); expect(b.s.sx).toBe(0);
    const c = emptyCtx(); c.s.slideMode = false; c.s.grounded = true; c.s.sx = 30; c.s.sy = 4; updateSlide(c.ctx, c.s, c.f);
    expect(c.s.sx).toBeCloseTo(30 * (1 - 0.5 * DT), 12); expect(c.s.sy).toBeCloseTo(4 * (1 - 0.5 * DT), 12);
  });
  it('landing on ice enters slide mode with s_x = v_x, s_z = 0; landing on rock does not', () => {
    const land = (kind: 'ice' | 'rock') => {
      const { pogo, cfg } = setup(makeLevel([flatGround({ w: 400, kind })]));
      placeInAir(cfg, pogo.state, 0, 6, 40, -60);
      let vx = 0;
      for (let i = 0; i < 300; i++) { const ev = pogo.step(); if (ev.some(e => e.type === 'land')) { vx = pogo.state.qvx; break; } }
      return { s: pogo.state, vx };
    };
    const ice = land('ice'), rock = land('rock');
    expect(ice.s.slideMode).toBe(true);
    expect(ice.s.sx).toBeCloseTo(ice.vx, 9);
    expect(ice.s.sy).toBe(0);
    expect(rock.s.slideMode).toBe(false);
    expect(rock.s.sx).toBe(0);
  });
  it('a slide carries the pogo along the ice (rock does not)', () => {
    const slideDist = (kind: 'ice' | 'rock') => {
      const { pogo, cfg } = setup(makeLevel([flatGround({ w: 800, kind })]));
      placeInAir(cfg, pogo.state, -20, 6, 40, -60);
      let landedX = 0;
      for (let i = 0; i < 300; i++) { const ev = pogo.step(); if (ev.some(e => e.type === 'land')) { landedX = pogo.state.qx; break; } }
      for (let i = 0; i < 15; i++) pogo.step();
      return pogo.state.qx - landedX;
    };
    expect(slideDist('ice')).toBeGreaterThan(slideDist('rock') + 20);
  });
});

describe('E16 ledge exit, E17 platform release', () => {
  it('E16: grounded last tick, probe misses, N = 0 ⇒ v ← (s_x, 5)', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; f.gBefore = true; s.noGround = 0; s.sx = 7; s.qvx = 50; s.qvy = -24;
    groundContact(ctx, s, f);
    expect(s.qvx).toBe(7); expect(s.qvy).toBe(5);
    expect(s.grounded).toBe(false);
  });
  it('E17: v_x += c_x; v_z += c_z·(1 − 0.75·[c_z < 0]) when airborne with a carry', () => {
    const a = emptyCtx(); a.s.grounded = false; a.s.carryX = 10; a.s.carryY = -20; a.s.qvx = 0; a.s.qvy = 0;
    platformRelease(a.ctx, a.s);
    expect(a.s.qvx).toBe(10); expect(a.s.qvy).toBeCloseTo(-20 * 0.25, 12);
    const b = emptyCtx(); b.s.grounded = false; b.s.carryX = 0; b.s.carryY = 8; platformRelease(b.ctx, b.s);
    expect(b.s.qvy).toBe(8);
  });
  it('E17: no release while grounded; carry is cleared', () => {
    const { ctx, s } = emptyCtx();
    s.grounded = true; s.carryX = 10; s.qvx = 1;
    platformRelease(ctx, s);
    expect(s.qvx).toBe(1); expect(s.carryX).toBe(0);
  });
  it('a pogo launched from a moving platform inherits its velocity (E17 in the full sim)', () => {
    const lvl = makeLevel([flatGround({ x: -80, w: 20 })], {
      movingObjects: [{ id: 'mv', kind: 'wood', x: 0, y: 0, w: 12, h: 1, move: { dx: 8, dy: 0, period: 6 } }],
      startPosition: { x: 0, y: 0 },
    });
    const { pogo } = setup(lvl);
    let vxAtLaunch = 0;
    for (let i = 0; i < 400; i++) { const ev = pogo.step(); if (ev.some(e => e.type === 'launch')) { vxAtLaunch = pogo.state.qvx; break; } }
    expect(Math.abs(vxAtLaunch)).toBeGreaterThan(1);       // not zero: the platform's velocity was added
  });
});

describe('E18 collision hull and spring extension', () => {
  it('X = 18·sin(0.9·P_b); z_min = −55 + max(X, −12.25) while N = 0 (grounded, P_b = P_max = 95)', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.noGround = 0; s.springBone = 95; s.springBoneMax = 95;
    updateHull(ctx, s, f);
    const X = 18 * Math.sin(0.8999999761581421 * 95 * Math.PI / 180);
    expect(s.springExt).toBeCloseTo(X, 9);
    expect(s.hullMinZ).toBeCloseTo(-55 + X, 9);
    expect(X).toBeCloseTo(17.94, 1);
  });
  it('z_min is not updated while N > 0', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.noGround = 1; s.springBone = 95; s.hullMinZ = -55;
    updateHull(ctx, s, f);
    expect(s.hullMinZ).toBe(-55);
  });
  it('airborne: P_b ← max(P_b − 120·Δt, −200)', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = false; s.springBone = 95;
    updateHull(ctx, s, f);
    expect(s.springBone).toBeCloseTo(95 - 120 * DT, 9);
    s.springBone = -199; updateHull(ctx, s, f);
    expect(s.springBone).toBe(-200);
  });
  it('negative extension is rescaled by (0.01·P_max)·(200 + X)·0.0025 and limited to −12.25', () => {
    const { ctx, s, f } = emptyCtx();
    s.grounded = true; s.noGround = 0; s.springBone = -50; s.springBoneMax = 95;
    updateHull(ctx, s, f);
    const X0 = 18 * Math.sin(0.8999999761581421 * -50 * Math.PI / 180);
    const X = X0 * (((95 * 0.009999999776482582) * (200 + X0)) * 0.0024999999441206455);
    expect(s.springExt).toBeCloseTo(X, 9);
    expect(s.hullMinZ).toBeCloseTo(-55 + Math.max(X, -12.25), 9);
  });
});

describe('§10 validation targets (spec) — the implementation must reproduce them', () => {
  /** [L, launch speed Q/T, apex Q, flight T] from LOCKED_SPEC §10 */
  const TARGETS: [number, number, number, number][] = [
    [40, 29.69, 50.0, 6.97], [95, 70.52, 288.1, 16.47], [120, 89.08, 461.2, 20.90], [200, 148.47, 1287.3, 34.84], [300, 222.70, 2903.4, 52.32],
  ];
  for (const [L, v0, apex, flight] of TARGETS) {
    it(`L = ${L}: launch ${v0} Q/T, apex ${apex} Q, flight ${flight} T (flat ground, upright)`, () => {
      const { pogo } = setup(makeLevel([flatGround({ w: 800 })]));
      const s = pogo.state;
      s.loadMin = L; s.loadMax = L;                               // a window that pins the launch load to exactly L
      let launchTick = -1, landTick = -1, y0 = 0, apexQ = 0;
      for (let i = 0; i < 4000; i++) {
        const ev = pogo.step();
        if (launchTick < 0 && ev.some(e => e.type === 'launch')) {
          launchTick = i; y0 = 55; expect(s.loadLast).toBeCloseTo(L, 9);
          expect(Math.hypot(s.qvx, s.qvy)).toBeCloseTo(v0, 2);
        } else if (launchTick >= 0) {
          apexQ = Math.max(apexQ, s.qy - y0);
          if (ev.some(e => e.type === 'land')) { landTick = i; break; }
        }
      }
      expect(landTick).toBeGreaterThan(launchTick);
      expect(apexQ).toBeGreaterThan(apex * 0.995);
      expect(apexQ).toBeLessThan(apex * 1.005);
      expect((landTick - launchTick) * DT).toBeGreaterThan(flight - 0.25);
      expect((landTick - launchTick) * DT).toBeLessThan(flight + 0.25);
      // real-time seconds of the flight (spec table: 0.458, 1.083, 1.375, 2.292, 3.442)
      expect((landTick - launchTick) / 120).toBeGreaterThan((flight / (16 * (973 / 1024))) - 0.02);
    });
  }
});

describe('charge 95 — full integration from the spawn', () => {
  it('holding the charge from the spawn launches at exactly L = 95 after 47 charging ticks, v₀ = 70.52 Q/T straight up', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 800 })]));
    let ticks = 0, launched = false;
    for (let i = 0; i < 100 && !launched; i++) { launched = pogo.step(input({ jumpHeld: true })).some(e => e.type === 'launch'); ticks++; }
    expect(launched).toBe(true);
    expect(ticks).toBe(48);                                   // 47 charging ticks + the launch tick
    expect(pogo.state.loadLast).toBe(95);
    expect(pogo.state.qvx).toBeCloseTo(0, 9);
    expect(pogo.state.qvy).toBeCloseTo(0.7423499822616577 * 95, 9);
    expect(pogo.state.qvy).toBeCloseTo(70.52, 2);
  });
  it('the apex of that jump is 288.1 Q (5.54 HUD metres) and the flight lasts 16.5 T', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 800 })]));
    run(pogo, 47, input({ jumpHeld: true }));
    pogo.step(input({ jumpHeld: true }));
    let apex = 0, t = 0, landed = false;
    for (; t < 400 && !landed; t++) { const ev = pogo.step(); apex = Math.max(apex, pogo.state.qy - 55); landed = ev.some(e => e.type === 'land'); }
    expect(apex).toBeCloseTo(288.1, 0);
    expect(apex / cfg.qPerMetre).toBeCloseTo(5.54, 1);
    expect((t + 1) * DT).toBeCloseTo(16.47, 0);
  });
});

describe('charge 300 — full integration through a real fall', () => {
  it('a fall of ≥ 277 Q/T opens the window to 300; holding charges 0 → 300 in ≈ 18.75 T and launches at 222.70 Q/T, apex ≈ 2903 Q (55.8 m)', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 800 })], { bounds: { minX: -50, maxX: 150, minY: -40, maxY: 400 }, killY: -100 }));
    placeInAir(cfg, pogo.state, 0, 100, 0, 0);               // 100 m ⇒ v ≈ √(2·8.5·5200) ≈ 297 Q/T at the ground (capped at 300)
    let landed = false;
    for (let i = 0; i < 2000 && !landed; i++) landed = pogo.step(input({ jumpHeld: true })).some(e => e.type === 'land');
    expect(landed).toBe(true);
    expect(pogo.state.loadMax).toBe(300);
    expect(pogo.state.loadMin).toBeGreaterThan(40);
    // keep holding: charges to 300 and launches by itself
    let n = 0, launched = false, y0 = 0;
    for (; n < 400 && !launched; n++) { launched = pogo.step(input({ jumpHeld: true })).some(e => e.type === 'launch'); }
    expect(launched).toBe(true);
    expect(pogo.state.loadLast).toBe(300);
    expect(n * DT).toBeGreaterThan(18.75 - 2 * DT - 0.2);
    expect(n * DT).toBeLessThan(18.75 + 3 * DT + 0.2);
    expect(Math.hypot(pogo.state.qvx, pogo.state.qvy)).toBeGreaterThan(222.6);
    expect(Math.hypot(pogo.state.qvx, pogo.state.qvy)).toBeLessThan(222.8);
    y0 = pogo.state.qy;
    let apex = 0;
    for (let i = 0; i < 1500; i++) { pogo.step(); apex = Math.max(apex, pogo.state.qy - y0); if (pogo.state.grounded && i > 20) break; }
    expect(apex).toBeGreaterThan(2903.4 * 0.995);
    expect(apex).toBeLessThan(2903.4 * 1.005);
    expect(apex / 52).toBeCloseTo(55.83, 0);
  });
});

describe('passive bounce (no charge input) converges to the idle hop', () => {
  it('95-jump → L_min ≈ 54 → launch ≈ 40 → L_min = 40 → stable idle hop (spec §10: 70.5 → 40.3 → 29.7)', () => {
    const { pogo } = setup(makeLevel([flatGround({ w: 800 })]));
    run(pogo, 47, input({ jumpHeld: true }));
    const speeds: number[] = [];
    const mins: number[] = [];
    for (let i = 0; i < 3000 && speeds.length < 5; i++) {
      const ev = pogo.step();
      if (ev.some(e => e.type === 'launch')) speeds.push(Math.hypot(pogo.state.qvx, pogo.state.qvy));
      if (ev.some(e => e.type === 'land')) mins.push(pogo.state.loadMin);
    }
    expect(speeds[0]).toBeCloseTo(70.52, 1);
    expect(mins[0]).toBeGreaterThan(53); expect(mins[0]).toBeLessThan(54.6);
    expect(speeds[1]).toBeGreaterThan(39.5); expect(speeds[1]).toBeLessThan(41.6);
    expect(mins[1]).toBe(40);
    expect(speeds[2]).toBeGreaterThan(29.6); expect(speeds[2]).toBeLessThan(30.6);
    expect(Math.abs(speeds[4] - speeds[3])).toBeLessThan(1e-6);   // stable
  });
});

describe('controls ↔ physics conventions', () => {
  it('turning left leans the stick toward −x and the launch goes left; turning right goes right', () => {
    const left = setup(makeLevel([flatGround({ w: 800 })])).pogo, right = setup(makeLevel([flatGround({ w: 800 })])).pogo;
    for (let i = 0; i < 18; i++) { left.step(input({ tilt: -1 })); right.step(input({ tilt: 1 })); }
    expect(left.state.theta).toBeGreaterThan(0); expect(right.state.theta).toBeLessThan(0);
    expect(left.state.angle).toBeLessThan(0);   // render angle: + = toward +x
    let l = false, r = false;
    for (let i = 0; i < 10; i++) { l = left.step(input({ tilt: -1 })).some(e => e.type === 'launch') || l; r = right.step(input({ tilt: 1 })).some(e => e.type === 'launch') || r; if (l && r) break; }
    expect(left.state.qvx).toBeLessThan(0); expect(right.state.qvx).toBeGreaterThan(0);
  });
  it('rotateAboutTip keeps the tip fixed', () => {
    const { pogo, cfg } = setup(makeLevel([flatGround({ w: 800 })]));
    const s = pogo.state;
    const tipX = (): number => s.qx + sinD(s.theta) * (cfg.tipLength - cfg.tipRadius);
    const x0 = tipX();
    rotateAboutTip(cfg, s, 40);
    expect(tipX()).toBeCloseTo(x0, 9);
  });
  it('simulateLaunch (trajectory guide) reproduces the real launch + flight exactly', () => {
    const { pogo, cfg, world } = setup(makeLevel([flatGround({ w: 800 })]));
    run(pogo, 5);
    rotateAboutTip(cfg, pogo.state, 25);
    pogo.state.load = 80;
    const pred = simulateLaunch({ world, cfg }, pogo.state, { maxTicks: 800 });
    pogo.state.loadMin = 0;                                     // launch as soon as the next tick evaluates it
    let launched = false, landed = false, land = { x: 0, y: 0 };
    for (let i = 0; i < 800 && !landed; i++) {
      const ev = pogo.step();
      if (ev.some(e => e.type === 'launch')) launched = true;
      if (launched && ev.some(e => e.type === 'land')) { landed = true; land = { x: pogo.state.x, y: pogo.state.y }; }
    }
    expect(pred.landed).toBe(true);
    expect(landed).toBe(true);
    expect(pred.end.x).toBeCloseTo(land.x, 1);
    expect(pred.end.y).toBeCloseTo(land.y, 1);
  });
});
