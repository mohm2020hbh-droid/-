import { LEVEL_01 } from '../src/data/levels/level01';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { playRoute } from '../src/sim/routeBot';
import { writeFileSync } from 'node:fs';
const t0 = Date.now();
const plan = playRoute(LEVEL_01, createPhysicsConfig());
console.log(`success=${plan.success} hops=${plan.hops.length} jumps=${plan.jumps} ticks=${plan.ticks} (${(plan.ticks / 120).toFixed(1)}s sim) failedAt=${plan.failedAt ?? '-'}  [${((Date.now() - t0) / 1000).toFixed(1)}s]`);
for (const h of plan.hops) console.log(`${h.from.padEnd(6)}→ ${h.to.padEnd(6)} wait ${String(h.wait).padStart(3)} tilt ${h.tilt.toFixed(1).padStart(5)} power ${h.power.toFixed(2)}`);
writeFileSync(process.argv[2] ?? '/tmp/route-plan.json', JSON.stringify({ hops: plan.hops, script: plan.script.map(i => [+i.tilt.toFixed(3), i.jumpHeld ? 1 : 0, +i.pull.toFixed(3)]) }));
