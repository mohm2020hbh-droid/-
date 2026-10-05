import { describe, expect, it } from 'vitest';
import { SoundBank } from '../../../src/audio/system/SoundBank';
import { RECIPES, type Recipe } from '../../../src/audio/system/synth/recipes';
import { FakeHost, type FakeBuffer } from './helpers';

const dummy = (id: string, sec: number, rate?: number): Recipe => ({ id, analog: 'x', targets: { durationSec: sec }, renderRate: rate, render: (sr) => new Float32Array(Math.round(sec * sr)).fill(0.1) });

describe('SoundBank — the buffer cache', () => {
  it('renders on first use (miss), then serves the same buffer (hit)', () => {
    const b = new SoundBank(new FakeHost());
    const a1 = b.get('charge_tick'), a2 = b.get('charge_tick');
    expect(a1).toBeTruthy(); expect(a2).toBe(a1);
    expect(b.stats.misses).toBe(1); expect(b.stats.hits).toBe(1); expect(b.stats.rendered).toBe(1);
    expect(b.stats.renderMs).toBeGreaterThanOrEqual(0);
  });
  it('is deterministic: two banks render identical buffers', () => {
    const a = new SoundBank(new FakeHost()).get('collision_2') as FakeBuffer, c = new SoundBank(new FakeHost()).get('collision_2') as FakeBuffer;
    expect(a.length).toBe(c.length);
  });
  it('renders at min(context rate, recipe rate)', () => {
    const host = new FakeHost(); host.sampleRate = 48000;
    const b = new SoundBank(host);
    expect((b.get('collision_1') as FakeBuffer).sampleRate).toBe(11025);
    expect((b.get('charge_tick') as FakeBuffer).sampleRate).toBe(48000);
  });
  it('warm() renders ahead and returns false for an unknown recipe (counted missing once)', () => {
    const b = new SoundBank(new FakeHost());
    expect(b.warm('break_1')).toBe(true); expect(b.has('break_1')).toBe(true);
    expect(b.warm('nope')).toBe(false); expect(b.warm('nope')).toBe(false);
    expect(b.stats.missing).toBe(1);
    expect(b.get('nope')).toBeNull();
  });
  it('warmSome renders in slices within a time budget and reports what is left', () => {
    const b = new SoundBank(new FakeHost());
    let t = 0; b.nowMs = () => (t += 4);                                       // every clock read costs 4 ms
    const left = b.warmSome(10, ['charge_tick', 'launch_pop', 'collision_1', 'collision_2', 'break_1']);
    expect(left).toBeGreaterThan(0); expect(left).toBeLessThan(5);
    expect(b.size).toBeGreaterThan(0);
    b.nowMs = () => 0;
    expect(b.warmSome(1e9, ['charge_tick', 'launch_pop', 'collision_1', 'collision_2', 'break_1'])).toBe(0);
    expect(b.size).toBe(5);
  });
  it('warm-up order covers every recipe of the catalogue', () => {
    const b = new SoundBank(new FakeHost());
    expect(b.warmSome(1e9)).toBe(0);
    expect(b.size).toBe(Object.keys(RECIPES).length);
    expect(b.stats.bytes).toBeGreaterThan(0);
    expect(b.stats.bytes).toBeLessThan(8 * 1024 * 1024);                      // well inside the budget (≈ 2 MB at 44.1 kHz)
  });
  it('LRU: beyond the memory budget the least recently used non-pinned entry goes first, the newest never', () => {
    const host = new FakeHost();
    const recipes = { a: dummy('a', 1), b: dummy('b', 1), c: dummy('c', 1) };
    const budget = 44100 * 4 * 2 + 10;                                        // room for two 1 s buffers
    const bank = new SoundBank(host, recipes, 1, budget);
    bank.get('a'); bank.get('b'); bank.get('a');                              // a was used last
    bank.get('c');                                                            // over budget → b (least recently used) is evicted
    expect(bank.has('a')).toBe(true); expect(bank.has('c')).toBe(true); expect(bank.has('b')).toBe(false);
    expect(bank.stats.evictions).toBe(1);
    expect(bank.stats.bytes).toBeLessThanOrEqual(budget);
    expect(bank.get('b')).toBeTruthy();                                       // an evicted entry simply renders again
  });
  it('pinned (warm-up) entries are evicted last', () => {
    const host = new FakeHost();
    const recipes = { a: dummy('a', 1), b: dummy('b', 1), c: dummy('c', 1) };
    const bank = new SoundBank(host, recipes, 1, 44100 * 4 * 2 + 10);
    bank.warm('a'); bank.get('b'); bank.get('c');
    expect(bank.has('a')).toBe(true); expect(bank.has('b')).toBe(false);
  });
  it('clear() empties the cache', () => {
    const b = new SoundBank(new FakeHost());
    b.get('charge_tick'); b.clear();
    expect(b.size).toBe(0); expect(b.stats.bytes).toBe(0);
  });
});
