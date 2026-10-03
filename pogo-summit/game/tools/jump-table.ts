import { makeLevel, flatGround, setup } from '../tests/helpers';
import { simulateJump } from '../src/sim/prediction';
import { DEG, TICK_RATE } from '../src/sim/math';

const { world, cfg, pogo } = setup(makeLevel([flatGround({ w: 400, x: 150 })], { startPosition: { x: 0, y: 0 } }));
for (let i = 0; i < 10; i++) pogo.step();
console.log('rest COM height', pogo.state.y.toFixed(3));
console.log('range (m) / apex (m above start) / flight (s) on flat ground');
for (const power of [0, 0.25, 0.5, 0.75, 1]) {
  const row: string[] = [];
  for (const deg of [0, 15, 30, 45, 60]) {
    const p = simulateJump({ world, cfg }, pogo.state, deg * DEG, power, { maxTicks: 600, stride: 2 });
    let apex = 0; for (let i = 1; i < p.points.length; i += 2) apex = Math.max(apex, p.points[i]);
    row.push(`${deg}°: ${(p.landX - 0).toFixed(1)}m/${apex.toFixed(1)}m/${(p.ticks / TICK_RATE).toFixed(2)}s`);
  }
  console.log(`power ${power.toFixed(2)}  ` + row.join('  |  '));
}
