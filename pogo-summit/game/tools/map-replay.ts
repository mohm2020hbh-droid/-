/**
 * Dev tool: replay a route-bot plan on the REAL map runtime (streaming chunks, timed / breakable / hidden ledges, checkpoints).
 * Usage: tsx tools/map-replay.ts <map.json> <bot-output.txt>      (the output of tools/map-bot.ts: "a → b  idle I tiltG G tt T hd H tiltA A (N ticks)")
 * The bot plans on the static collision of the compiled level (gated pieces treated as solid); this checks what a player would really get.
 */
import { readFileSync } from 'node:fs';
import { parseMap } from '../src/map/MapLoader';
import { compileRenderLevel } from '../src/map/MapCompile';
import { MapRuntime } from '../src/map/MapRuntime';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { PhysicsWorld } from '../src/sim/PhysicsWorld';
import { NEUTRAL_INPUT, createPogoState } from '../src/sim/PogoState';
import { PogoPhysicsController, stepPogo } from '../src/sim/PogoPhysicsController';
import { runHop, type Hop } from '../src/sim/routeBot';
import type { SimEvent } from '../src/sim/events';

const [file, planFile] = process.argv.slice(2);
const doc = parseMap(readFileSync(file, 'utf8'), { lenient: true }).doc;
const cfg = createPhysicsConfig();
const hops: Hop[] = readFileSync(planFile, 'utf8').split('\n').flatMap(l => {
  const m = /^(\S+) → (\S+)\s+idle (\d+) tiltG (\S+) tt (\d+) hd (\d+) tiltA (\S+) \((\d+) ticks\)/.exec(l);
  return m ? [{ from: m[1], to: m[2], idle: +m[3], tiltG: +m[4], tt: +m[5], hd: +m[6], tiltA: +m[7], ticks: +m[8] }] : [];
});
if (!hops.length) { console.error('no hops parsed'); process.exit(2); }

// 1. rebuild the per-tick script on the static compiled level (what the bot planned on)
const level = compileRenderLevel(doc);
const world = new PhysicsWorld(level, cfg.qPerMetre);
let st = createPogoState(world, cfg);
const script: ReturnType<typeof runHop>['script'] = [];
for (const h of hops) { const r = runHop(world, cfg, st, h); script.push(...r.script); st = r.s; }
console.log(`plan: ${hops.length} hops, ${script.length} ticks (${(script.length / cfg.tickRate).toFixed(1)} s)`);

// 2. play it on the real runtime
const rt = new MapRuntime(doc, { cfg });
rt.prepareSpawn();
const pogo = new PogoPhysicsController(rt.world, cfg);
const events: string[] = [];
let finishedAt = -1, kills = 0;
for (let i = 0; i < script.length; i++) {
  rt.beforeStep(pogo.state);
  const ev: SimEvent[] = pogo.step({ ...NEUTRAL_INPUT, ...script[i] });
  for (const m of rt.afterStep(pogo.state, ev)) {
    if (m.type === 'checkpoint' || m.type === 'finish' || m.type === 'kill' || m.type === 'break' || m.type === 'restore') events.push(`${i}:${m.type}${m.id ? ':' + m.id : ''}`);
    if (m.type === 'finish' && finishedAt < 0) finishedAt = i;
    if (m.type === 'kill') kills++;
  }
  if (pogo.state.mode === 'FINISHED' && finishedAt < 0) finishedAt = i;
  if (rt.finished && finishedAt < 0) finishedAt = i;
}
const s = pogo.state;
console.log(`runtime: finished=${finishedAt >= 0}${finishedAt >= 0 ? ` at tick ${finishedAt} (${(finishedAt / cfg.tickRate).toFixed(1)} s)` : ''} kills=${kills} end=(${s.x.toFixed(1)}, ${s.y.toFixed(1)}) mode=${s.mode} progress max=${rt.progress.max.toFixed(1)}%`);
console.log(events.join('  '));
void stepPogo;
process.exit(finishedAt >= 0 ? 0 : 1);
