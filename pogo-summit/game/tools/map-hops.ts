/** Dev tool: per-hop solvability of a map's `metadata.route` (and extra pairs) on the real physics. Usage: tsx tools/map-hops.ts <map.json> [from:to …] */
import { readFileSync } from 'node:fs';
import { parseMap } from '../src/map/MapLoader';
import { compileRenderLevel } from '../src/map/MapCompile';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { hopPlans } from '../src/sim/routeBot';

const file = process.argv[2];
const doc = parseMap(readFileSync(file, 'utf8'), { lenient: true }).doc;
const level = compileRenderLevel(doc);
const cfg = createPhysicsConfig();
const route = level.route ?? [];
const pairs: [string, string][] = process.argv.length > 3 ? process.argv.slice(3).map(a => a.split(':') as [string, string]) : route.slice(0, -1).map((id, i) => [id, route[i + 1]] as [string, string]);
for (const [a, b] of pairs) {
  const t0 = performance.now();
  // the pogo stands near the edge that faces the target (a player repositions with small hops first)
  const ca = level.platforms.find(p => p.id === a), cb = level.platforms.find(p => p.id === b);
  const right = ((cb?.x ?? 0) - (ca?.x ?? 0)) >= 0;
  const rows = (right ? [0.8, 0.92] : [0.2, 0.08]).map(t => hopPlans(level, cfg, a, b, { t, tick: 0, idleMax: Number(process.env.IDLE ?? 4) }));
  console.log(`${a.padEnd(8)}→ ${b.padEnd(8)} plans ${rows.map(r => String(r.plans).padStart(4)).join(' / ')}  best margin ${rows.map(r => r.best.toFixed(2)).join(' / ')} m  ${Math.round(performance.now() - t0)} ms`);
}
