import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { LEVEL_01 } from '../src/data/levels/level01';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { PhysicsWorld } from '../src/sim/PhysicsWorld';
import { PogoPhysicsController } from '../src/sim/PogoPhysicsController';
import { NEUTRAL_INPUT, type PogoInput } from '../src/sim/PogoState';
import { progressFraction } from '../src/data/LevelData';

/**
 * End-to-end completability: a recorded input script (produced by tools/route-plan.ts with the route bot on the real
 * physics) must take LEVEL_01 from the start to the goal in ONE continuous run. If a physics/level change breaks it, this test
 * fails — regenerate with `npx tsx tools/route-plan.ts tests/fixtures/level01.route.json` ONLY after confirming the change is intended.
 */
describe('LEVEL_01 end-to-end replay', () => {
  const fx = JSON.parse(readFileSync(join(process.cwd(), 'tests/fixtures/level01.route.json'), 'utf8')) as { hops: { from: string; to: string }[]; script: [number, number][] };
  const frames: PogoInput[] = fx.script.map(([tilt, held]) => ({ ...NEUTRAL_INPUT, tilt, jumpHeld: !!held }));

  it('reaches the goal, never touching a hazard, with a plausible number of jumps', () => {
    const cfg = createPhysicsConfig();
    const pogo = new PogoPhysicsController(new PhysicsWorld(LEVEL_01, cfg.qPerMetre), cfg);
    let hazards = 0, goal = false, maxProgress = 0;
    for (const f of frames) {
      for (const e of pogo.step(f)) { if (e.type === 'hazard') hazards++; if (e.type === 'goal') goal = true; }
      maxProgress = Math.max(maxProgress, progressFraction(LEVEL_01.progress.path, pogo.state.x, pogo.state.y));
      if (goal) break;
    }
    expect(goal).toBe(true);
    expect(hazards).toBe(0);
    expect(pogo.state.falls).toBe(0);
    expect(pogo.state.mode).toBe('FINISHED');
    // the pogo hops by itself (LOCKED_SPEC E8), so the counter includes the idle hops used for repositioning
    expect(pogo.state.jumps).toBeGreaterThanOrEqual(17);
    expect(pogo.state.jumps).toBeLessThanOrEqual(60);
    expect(maxProgress).toBeGreaterThan(0.95);
    const seconds = (pogo.state.finishedTick - pogo.state.startedTick) / 120;
    expect(seconds).toBeLessThan(LEVEL_01.parTimeSec); // a perfect run beats the 3-star par
  });

  it('uses every mechanic the level teaches that the locked spec defines: a moving-platform wait, ice and the low ceiling', () => {
    const hops = fx.hops.map(h => `${h.from}>${h.to}`);
    expect(hops.some(h => h.startsWith('p5>pad1'))).toBe(true); // onto the (spec-less) bounce-pad surface
    expect(hops.some(h => h.startsWith('q3>q4'))).toBe(true);   // onto the moving platform
    expect(hops.some(h => h.startsWith('r1>r2'))).toBe(true);   // ice
    expect(hops.some(h => h.startsWith('r2>r3'))).toBe(true);   // under the ceiling
  });
});
