import { LEVEL_01 } from '../src/data/levels/level01';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { playRoute } from '../src/sim/routeBot';
import { writeFileSync } from 'node:fs';
const t0 = Date.now();
const plan = playRoute(LEVEL_01, createPhysicsConfig());
console.log(`success=${plan.success} hops=${plan.hops.length} jumps=${plan.jumps} ticks=${plan.ticks} (${(plan.ticks / 120).toFixed(1)}s sim) failedAt=${plan.failedAt ?? '-'} expansions=${plan.expansions}  [${((Date.now() - t0) / 1000).toFixed(1)}s]`);
for (const h of plan.hops) console.log(`${h.from.padEnd(6)}→ ${h.to.padEnd(6)} idle ${h.idle} tiltG ${h.tiltG.toFixed(1).padStart(5)} tt ${String(h.tt).padStart(2)} hold ${String(h.hd).padStart(2)} tiltA ${h.tiltA.toFixed(1).padStart(5)} (${h.ticks} ticks)`);
writeFileSync(process.argv[2] ?? '/tmp/route-plan.json', JSON.stringify({ hops: plan.hops, script: plan.script.map(i => [+i.tilt.toFixed(3), i.jumpHeld ? 1 : 0]) }));
