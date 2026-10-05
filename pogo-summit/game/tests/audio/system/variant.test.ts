import { describe, expect, it } from 'vitest';
import { VariantSelector, type AudioVariantDef } from '../../../src/audio/system/AudioVariant';
import { mulberry32 } from '../../../src/audio/system/rng';

const v = (id: string, extra: Partial<AudioVariantDef> = {}): AudioVariantDef => ({ id, source: { kind: 'synth', recipe: id }, ...extra });
const four = [v('a'), v('b'), v('c'), v('d')];
const random = (key = 'e', variantCooldownMs?: number) => ({ mode: 'random' as const, key, variantCooldownMs });

describe('VariantSelector — random with the original anti-repeat rule', () => {
  it('never picks the same variant twice in a row (100 000 draws, N = 2…6)', () => {
    for (const n of [2, 3, 4, 5, 6]) {
      const vs = Array.from({ length: n }, (_, i) => v(`v${i}`));
      const sel = new VariantSelector(mulberry32(n));
      let last = '';
      for (let i = 0; i < 100000 / n; i++) {
        const [p] = sel.pick(vs, random(), {}, i * 0.001);
        expect(p.id).not.toBe(last);
        last = p.id;
      }
    }
  });
  it('reaches every variant, roughly uniformly', () => {
    const sel = new VariantSelector(mulberry32(1));
    const count: Record<string, number> = { a: 0, b: 0, c: 0, d: 0 };
    const N = 40000;
    for (let i = 0; i < N; i++) count[sel.pick(four, random(), {}, i * 0.001)[0].id]++;
    for (const k of Object.keys(count)) { expect(count[k]).toBeGreaterThan(N * 0.2); expect(count[k]).toBeLessThan(N * 0.3); }
  });
  it('a single variant may repeat (nothing else to pick)', () => {
    const sel = new VariantSelector(mulberry32(1));
    expect(sel.pick([v('only')], random(), {}, 0)[0].id).toBe('only');
    expect(sel.pick([v('only')], random(), {}, 0.01)[0].id).toBe('only');
  });
  it('the repeat rule is "next index": a draw equal to the last pick moves on by one (modulo N)', () => {
    // a stub rng that always draws index 1: the first pick is b, the second would be b again → c, then b is not the last → b
    const rng = { next: () => 0.3 };            // floor(0.3 · 4) = 1
    const sel = new VariantSelector(rng);
    expect(sel.pick(four, random(), {}, 0)[0].id).toBe('b');
    expect(sel.pick(four, random(), {}, 1)[0].id).toBe('c');
    expect(sel.pick(four, random(), {}, 2)[0].id).toBe('b');
  });
  it('is reproducible with the same seed', () => {
    const run = (seed: number) => { const s = new VariantSelector(mulberry32(seed)); return Array.from({ length: 50 }, (_, i) => s.pick(four, random(), {}, i)[0].id).join(''); };
    expect(run(3)).toBe(run(3));
    expect(run(3)).not.toBe(run(4));
  });
  it('keeps one history per key', () => {
    const rng = { next: () => 0.3 };
    const sel = new VariantSelector(rng);
    expect(sel.pick(four, random('x'), {}, 0)[0].id).toBe('b');
    expect(sel.pick(four, random('y'), {}, 0)[0].id).toBe('b');       // another event: its own history
  });
  it('reset forgets the history', () => {
    const rng = { next: () => 0.3 };
    const sel = new VariantSelector(rng);
    sel.pick(four, random(), {}, 0);
    sel.reset();
    expect(sel.pick(four, random(), {}, 1)[0].id).toBe('b');
  });
});

describe('VariantSelector — per-variant cooldown', () => {
  it('skips a variant that is still inside its cooldown', () => {
    const sel = new VariantSelector(mulberry32(5));
    const seen = new Set<string>();
    // all four within 0.25 s: with cooldown 250 ms each id can sound at most once
    for (let i = 0; i < 4; i++) seen.add(sel.pick(four, random('e', 250), {}, i * 0.01)[0].id);
    expect(seen.size).toBe(4);
  });
  it('never silences by cooldown alone: when every variant is cooling down, one still plays', () => {
    const sel = new VariantSelector(mulberry32(5));
    for (let i = 0; i < 4; i++) sel.pick(four, random('e', 250), {}, 0);
    expect(sel.pick(four, random('e', 250), {}, 0.01)).toHaveLength(1);
  });
  it('a cooled-down variant becomes eligible again', () => {
    const sel = new VariantSelector(mulberry32(5));
    for (let i = 0; i < 4; i++) sel.pick(four, random('e', 250), {}, 0);
    const later = new Set<string>();
    for (let i = 0; i < 40; i++) later.add(sel.pick(four, random('e', 250), {}, 10 + i)[0].id);
    expect(later.size).toBe(4);
  });
});

describe('VariantSelector — weights, eligibility, layers, forced', () => {
  it('weights bias the draw (the no-repeat rule still caps any variant at every other pick)', () => {
    const sel = new VariantSelector(mulberry32(2));
    const vs = [v('heavy', { weight: 9 }), v('light', { weight: 1 }), v('other', { weight: 1 })];
    const c: Record<string, number> = { heavy: 0, light: 0, other: 0 };
    let last = '';
    for (let i = 0; i < 20000; i++) { const id = sel.pick(vs, random(), {}, i)[0].id; c[id]++; expect(id).not.toBe(last); last = id; }
    expect(c.heavy).toBeGreaterThanOrEqual(Math.max(c.light, c.other));
    expect(c.heavy).toBeLessThanOrEqual(10000);              // never two in a row ⇒ at most half of the draws
  });
  it('`when` filters the candidates', () => {
    const sel = new VariantSelector(mulberry32(2));
    const vs = [v('a', { when: c => c.material === 'ice' }), v('b', { when: c => c.material !== 'ice' })];
    for (let i = 0; i < 20; i++) expect(sel.pick(vs, random(), { material: 'ice' }, i)[0].id).toBe('a');
    expect(sel.pick(vs, random(), { material: 'grass' }, 99)[0].id).toBe('b');
  });
  it('returns nothing when no variant is eligible', () => {
    const sel = new VariantSelector(mulberry32(2));
    expect(sel.pick([v('a', { when: () => false })], random(), {}, 0)).toEqual([]);
  });
  it('layers: every eligible variant', () => {
    const sel = new VariantSelector(mulberry32(2));
    const vs = [v('pop'), v('boom', { when: c => !!c.power })];
    expect(sel.pick(vs, { mode: 'layers', key: 'l' }, {}, 0).map(x => x.id)).toEqual(['pop']);
    expect(sel.pick(vs, { mode: 'layers', key: 'l' }, { power: true }, 1).map(x => x.id)).toEqual(['pop', 'boom']);
  });
  it('context: the first eligible; a forced variant wins in any mode', () => {
    const sel = new VariantSelector(mulberry32(2));
    expect(sel.pick(four, { mode: 'context', key: 'c' }, {}, 0)[0].id).toBe('a');
    expect(sel.pick(four, random(), { variant: 'c' }, 0)[0].id).toBe('c');
    expect(sel.pick(four, random(), { variant: 'nope' }, 0)).toHaveLength(1);       // unknown forced id: falls back to normal selection
  });
});
