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
import { parseHopLines, runHop, type Hop } from '../src/sim/routeBot';
import type { SimEvent } from '../src/sim/events';

const [file, planFile] = process.argv.slice(2);
const doc = parseMap(readFileSync(file, 'utf8'), { lenient: true }).doc;
const cfg = createPhysicsConfig();
const hops: Hop[] = parseHopLines(readFileSync(planFile, 'utf8'));
if (!hops.length) { console.error('no hops parsed'); process.exit(2); }

// 1. rebuild the per-tick script on the static compiled level (what the bot planned on)
const level = compileRenderLevel(doc);
const world = new PhysicsWorld(level, cfg.qPerMetre);
let st = createPogoState(world, cfg);
const script: ReturnType<typeof runHop>['script'] = [];
const bounds: { tick: number; hop: string; x: number; y: number }[] = [];     // where the static plan ends every hop
for (const h of hops) { const r = runHop(world, cfg, st, h); script.push(...r.script); st = r.s; bounds.push({ tick: script.length, hop: `${h.from} → ${h.to}`, x: r.s.x, y: r.s.y }); }
console.log(`plan: ${hops.length} hops, ${script.length} ticks (${(script.length / cfg.tickRate).toFixed(1)} s)`);

// 2. play it on the real runtime
const rt = new MapRuntime(doc, { cfg });
rt.prepareSpawn();
const pogo = new PogoPhysicsController(rt.world, cfg);
const events: string[] = [];
let finishedAt = -1, kills = 0, bi = 0, diverged = '';
for (let i = 0; i < script.length; i++) {
  rt.beforeStep(pogo.state);
  if (bi < bounds.length && i === bounds[bi].tick) { const b = bounds[bi++]; if (!diverged && Math.hypot(pogo.state.x - b.x, pogo.state.y - b.y) > 0.05) diverged = `first divergence at the end of hop "${b.hop}" (tick ${b.tick}): runtime (${pogo.state.x.toFixed(2)}, ${pogo.state.y.toFixed(2)}) vs static plan (${b.x.toFixed(2)}, ${b.y.toFixed(2)})`; }
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
console.log(diverged || 'runtime followed the static plan hop by hop');
console.log(events.join('  '));
void stepPogo;
process.exit(finishedAt >= 0 ? 0 : 1);
