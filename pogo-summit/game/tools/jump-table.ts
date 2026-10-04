import { makeLevel, flatGround, setup } from '../tests/helpers';
import { simulateLaunch } from '../src/sim/prediction';

// Range / apex / flight time on flat ground for the locked physics (spec §10 style), by spring load and stick angle.
const { world, cfg, pogo } = setup(makeLevel([flatGround({ w: 800, x: 300 })], { startPosition: { x: 0, y: 0 } }));
for (let i = 0; i < 3; i++) pogo.step();
const x0 = pogo.state.x;
console.log('range (m) / apex (m above launch) / flight (real s), flat ground');
for (const load of [40, 95, 120, 200, 300]) {
  const row: string[] = [];
  for (const deg of [0, 15, 30, 45, 60]) {
    const p = simulateLaunch({ world, cfg }, pogo.state, { theta: -deg, load, maxTicks: 900, stride: 2 });
    let apex = 0; for (let i = 1; i < p.points.length; i += 2) apex = Math.max(apex, p.points[i] - p.points[1]);
    row.push(`${deg}°: ${(p.landX - x0).toFixed(1)}m/${apex.toFixed(1)}m/${(p.ticks / cfg.tickRate).toFixed(2)}s`);
  }
  console.log(`L ${String(load).padStart(3)}  ` + row.join('  |  '));
}
