/** Dev tool: how comfortable is one hop? Shifts the target platform by a range of dx (m) and counts landing plans. Usage: tsx tools/map-tune.ts <map.json> <from> <to> [dxFrom dxTo step] [dy] */
import { readFileSync } from 'node:fs';
import { parseMap } from '../src/map/MapLoader';
import { compileRenderLevel } from '../src/map/MapCompile';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { hopPlans } from '../src/sim/routeBot';

const [file, from, to, a = '-4', b = '1', st = '1', dyArg = '0'] = process.argv.slice(2);
const cfg = createPhysicsConfig();
for (let dx = Number(a); dx <= Number(b) + 1e-9; dx += Number(st)) {
  const doc = parseMap(readFileSync(file, 'utf8'), { lenient: true }).doc;
  const e = doc.entities.find(x => x.id === to)!;
  e.position = { ...e.position, x: e.position.x + dx, y: e.position.y + Number(dyArg) };
  const level = compileRenderLevel(doc);
  const rows = [0.2, 0.5, 0.8].map(t => hopPlans(level, cfg, from, to, { t, tick: 0, idleMax: 3 }));
  console.log(`dx ${String(dx).padStart(5)} dy ${dyArg}  plans ${rows.map(r => String(r.plans).padStart(4)).join(' / ')}  margin ${rows.map(r => r.best.toFixed(2)).join(' / ')}`);
}
