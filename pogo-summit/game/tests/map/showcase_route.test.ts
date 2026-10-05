import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { BUILTIN_MAPS } from '../../src/map/builtinMaps';
import { compileRenderLevel } from '../../src/map/MapCompile';
import { MapRuntime } from '../../src/map/MapRuntime';
import { createPhysicsConfig } from '../../src/sim/PhysicsConfig';
import { PhysicsWorld } from '../../src/sim/PhysicsWorld';
import { NEUTRAL_INPUT, createPogoState } from '../../src/sim/PogoState';
import { PogoPhysicsController } from '../../src/sim/PogoPhysicsController';
import { parseHopLines, runHop } from '../../src/sim/routeBot';

/**
 * showcase_v2 is completable. `tests/fixtures/showcase_v2.route.txt` is the output of the route bot (`npm run map -- bot`),
 * which plans on the static collision of the compiled level. The test rebuilds its per-tick input script and plays it on the
 * REAL map runtime (streaming chunks, moving platform, timed and breakable ledges, checkpoints): it must follow the static
 * plan hop by hop and reach the finish without a single death. If the layout or the physics change on purpose, regenerate the
 * fixture with `npx tsx tools/map-bot.ts src/map/maps/showcase_v2.json 600 > tests/fixtures/showcase_v2.route.txt`.
 */
describe('showcase_v2 — machine-verified route', () => {
  const hops = parseHopLines(readFileSync(join(process.cwd(), 'tests', 'fixtures', 'showcase_v2.route.txt'), 'utf8'));
  const doc = BUILTIN_MAPS.showcase_v2;
  const cfg = createPhysicsConfig();

  it('the bot plan covers every route platform in order', () => {
    expect(hops.length).toBeGreaterThan(40);
    const order = (doc.metadata?.route as string[]) ?? [];
    const visited = hops.map(h => h.to).filter((id, i, a) => i === 0 || id !== a[i - 1]);
    expect(visited.filter(id => order.includes(id) || id === 'goal')).toEqual([...order, 'goal']);
  });

  it('replays on the real runtime: finishes without dying, passing every checkpoint', () => {
    const level = compileRenderLevel(doc);
    const world = new PhysicsWorld(level, cfg.qPerMetre);
    let st = createPogoState(world, cfg);
    const script: ReturnType<typeof runHop>['script'] = [];
    const ends: { tick: number; x: number; y: number }[] = [];
    for (const h of hops) { const r = runHop(world, cfg, st, h); script.push(...r.script); st = r.s; ends.push({ tick: script.length, x: r.s.x, y: r.s.y }); }

    const rt = new MapRuntime(doc, { cfg });
    rt.prepareSpawn();
    const pogo = new PogoPhysicsController(rt.world, cfg);
    const reached: string[] = [];
    let finishedAt = -1, kills = 0, bi = 0, worst = 0;
    for (let i = 0; i < script.length; i++) {
      rt.beforeStep(pogo.state);
      if (bi < ends.length && i === ends[bi].tick) { worst = Math.max(worst, Math.hypot(pogo.state.x - ends[bi].x, pogo.state.y - ends[bi].y)); bi++; }
      const ev = pogo.step({ ...NEUTRAL_INPUT, ...script[i] });
      for (const m of rt.afterStep(pogo.state, ev)) {
        if (m.type === 'checkpoint' && m.id) reached.push(m.id);
        if (m.type === 'kill') kills++;
        if (m.type === 'finish' && finishedAt < 0) finishedAt = i;
      }
      if (rt.finished && finishedAt < 0) finishedAt = i;
    }
    expect(finishedAt).toBeGreaterThan(0);
    expect(kills).toBe(0);
    expect(worst).toBeLessThan(0.05);                                           // the runtime follows the static plan hop by hop
    for (const cp of ['cp0', 'cp1', 'cp2']) expect(reached, cp).toContain(cp);
    expect(finishedAt / cfg.tickRate).toBeLessThan(95);
  });
});
