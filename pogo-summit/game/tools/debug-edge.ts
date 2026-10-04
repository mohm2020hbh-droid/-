import { LEVEL_01 } from '../src/data/levels/level01';
import { createPhysicsConfig } from '../src/sim/PhysicsConfig';
import { PhysicsWorld } from '../src/sim/PhysicsWorld';
import { createPlantedState } from '../src/sim/PogoState';
import { simulateLaunch } from '../src/sim/prediction';

const from = process.argv[2] ?? 'p4';
const cfg = createPhysicsConfig(); const world = new PhysicsWorld(LEVEL_01);
const fi = world.colliders.findIndex(c => c.id === from);
for (const f of [0.15, 0.5, 0.85]) {
  const st = createPlantedState(world, cfg, fi, f, 0);
  console.log(`--- from ${from} @${f}: origin(${st.x.toFixed(2)},${st.y.toFixed(2)})`);
  const lines: string[] = [];
  for (const a of [-50, -40, -30, -20, 20, 30, 40, 50]) {
    const row: string[] = [];
    for (const load of [55, 70, 85, 95]) {
      const r = simulateLaunch({ world, cfg }, st, { theta: a, load, maxTicks: 700, stride: 200 });
      row.push(`${a}°/L${load}:${r.landed ? world.colliders[r.groundId].id : r.hazard ? 'HAZ' : r.fell ? 'FALL' : '??'}@${r.landX.toFixed(1)},${r.landY.toFixed(1)}`);
    }
    lines.push(row.join('  '));
  }
  console.log(lines.join('\n'));
}
