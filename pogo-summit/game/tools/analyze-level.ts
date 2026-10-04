import { LEVEL_01 } from '../src/data/levels/level01';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { analyzeLevel } from '../src/sim/analysis';

const t0 = Date.now();
const a = analyzeLevel(LEVEL_01, createPhysicsConfig());
console.log(`analysis in ${((Date.now() - t0) / 1000).toFixed(1)}s — goal reachable: ${a.goalReached}`);
console.log('unreachable:', a.unreachable.join(', ') || '(none)');
const route = LEVEL_01.route ?? [];
for (let i = 0; i < route.length - 1; i++) {
  const e = a.edges.filter(x => x.from === route[i] && x.to === route[i + 1]).sort((p, q) => q.cells - p.cells)[0];
  console.log(`${route[i].padEnd(6)} → ${route[i + 1].padEnd(6)} ${e ? `cells ${String(e.cells).padStart(3)}/${e.total} best ${e.bestAngleDeg}° L=${e.bestLoad}` : 'NO DIRECT EDGE'}`);
}
console.log('hazard share', Object.fromEntries(Object.entries(a.hazardShare).map(([k, v]) => [k, +v.toFixed(2)])));
