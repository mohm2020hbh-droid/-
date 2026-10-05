import { describe, expect, it } from 'vitest';
import { Vfx, type FxKind } from '../../src/render/Vfx';

const NEW_KINDS: FxKind[] = ['splash', 'lava', 'checkpoint', 'break'];
const OLD_KINDS: FxKind[] = ['dust', 'jumpdust', 'ring', 'groundring', 'impact', 'sparkle', 'ice', 'goo', 'boost', 'confetti', 'goal', 'hazard', 'speed', 'debris', 'charge'];

describe('Vfx pool (map events → particles)', () => {
  it('every effect kind spawns particles, including the map-V2 ones (splash, lava, checkpoint, break)', () => {
    for (const kind of [...OLD_KINDS, ...NEW_KINDS]) {
      const v = new Vfx(320);
      v.burst(kind, 0, 0, 0, 1, 1);
      expect(v.activeCount(), kind).toBeGreaterThan(0);
    }
  });

  it('is a single fixed-size instanced mesh: bursts never allocate, the buffers keep their size', () => {
    const v = new Vfx(320);
    const sizes = () => [v.mesh.count, v.mesh.geometry.getAttribute('iPos').count, v.mesh.geometry.getAttribute('iCol').count];
    const before = sizes();
    for (let i = 0; i < 200; i++) v.burst(NEW_KINDS[i % 4], i, 0, 0, 1, 1);
    expect(sizes()).toEqual(before);
    expect(v.activeCount()).toBeLessThanOrEqual(320);
  });

  it('particles expire and their slots are reused (pooling)', () => {
    const v = new Vfx(320);
    v.burst('confetti', 0, 0, 0, 1, 1);
    const n = v.activeCount();
    expect(n).toBeGreaterThan(5);
    for (let i = 0; i < 400; i++) v.update(1 / 30);                                          // ≫ any particle lifetime
    expect(v.activeCount()).toBe(0);
    v.burst('confetti', 0, 0, 0, 1, 1);
    expect(v.activeCount()).toBe(n);                                                           // same deterministic count: the pool starts over cleanly
  });

  it('the particle budget caps live particles; the oldest are recycled instead of growing', () => {
    const v = new Vfx(320);
    v.setBudget(40);
    expect(v.particleBudget).toBe(40);
    for (let i = 0; i < 30; i++) { v.burst('confetti', 0, 0, 0, 1, 1); v.update(1 / 60); }
    expect(v.activeCount()).toBeLessThanOrEqual(40);
    v.setBudget(Infinity);
    expect(v.particleBudget).toBe(320);                                                        // clamps to the pool capacity
    v.setBudget(1);
    expect(v.particleBudget).toBe(16);                                                         // …and has a floor
  });

  it('density scales the amount (quality tiers)', () => {
    const hi = new Vfx(320), lo = new Vfx(320);
    lo.setDensity(0.3);
    hi.burst('confetti', 0, 0, 0, 1, 1); lo.burst('confetti', 0, 0, 0, 1, 1);
    expect(lo.activeCount()).toBeLessThan(hi.activeCount());
  });

  it('ambient (theme) particles fill the view slowly and never exceed the budget', () => {
    const v = new Vfx(320);
    v.setBudget(100);
    for (let i = 0; i < 600; i++) { v.ambient(0, 0, 14, 8, 'snow', ['#ffffff'], 1); v.ambient(0, 0, 14, 8, 'mote', ['#ffd6a0'], 1); v.update(1 / 60); }
    expect(v.activeCount()).toBeGreaterThan(5);
    expect(v.activeCount()).toBeLessThanOrEqual(100);
  });
});
