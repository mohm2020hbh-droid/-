/** Dev tool: play a map's `metadata.route` with the route bot on the real physics. Usage: tsx tools/map-bot.ts <map.json> [maxExpansions] */
import { readFileSync } from 'node:fs';
import { parseMap } from '../src/map/MapLoader';
import { compileRenderLevel } from '../src/map/MapCompile';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { playRoute } from '../src/sim/routeBot';

const file = process.argv[2];
const doc = parseMap(readFileSync(file, 'utf8'), { lenient: true }).doc;
const level = compileRenderLevel(doc);
const t0 = performance.now();
const plan = playRoute(level, createPhysicsConfig(), { maxExpansions: Number(process.argv[3] ?? 600), log: m => console.error(m) });
console.log(JSON.stringify({ success: plan.success, hops: plan.hops.length, jumps: plan.jumps, ticks: plan.ticks, failedAt: plan.failedAt, expansions: plan.expansions, ms: Math.round(performance.now() - t0) }));
for (const h of plan.hops) console.log(`${h.from} → ${h.to}  idle ${h.idle} tiltG ${h.tiltG} tt ${h.tt} hd ${h.hd} tiltA ${h.tiltA} (${h.ticks} ticks)`);
