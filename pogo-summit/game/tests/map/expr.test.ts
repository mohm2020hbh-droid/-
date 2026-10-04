import { describe, expect, it } from 'vitest';
import { MapExprError, evalExpr, expandTemplate } from '../../src/map/MapExpr';

describe('MapExpr — safe prefab expressions', () => {
  const s = { width: 6, thickness: 3, taper: 0.72, flip: true, label: 'x' };
  it('evaluates arithmetic with precedence, unary minus and parentheses', () => {
    expect(evalExpr('width * 0.5 + 1', s)).toBe(4);
    expect(evalExpr('-(width - thickness) * 2', s)).toBe(-6);
    expect(evalExpr('2 + 3 * 4', s)).toBe(14);
    expect(evalExpr('(2 + 3) * 4', s)).toBe(20);
    expect(evalExpr('1e2 + 1.5', s)).toBe(101.5);
  });
  it('supports the whitelisted functions and the pi constant', () => {
    expect(evalExpr('min(width, thickness)', s)).toBe(3);
    expect(evalExpr('max(1, abs(-4))', s)).toBe(4);
    expect(evalExpr('clamp(width * 3, 0, 10)', s)).toBe(10);
    expect(evalExpr('round(pi * 100) / 100', s)).toBe(3.14);
  });
  it('a lone identifier returns the raw parameter (string / boolean allowed)', () => {
    expect(evalExpr('label', s)).toBe('x');
    expect(evalExpr('flip', s)).toBe(true);
    expect(evalExpr('width', s)).toBe(6);
  });
  it('rejects anything outside the language (no eval, no property access, no unknown names)', () => {
    for (const bad of ['process.exit()', 'width.toString', 'foo', 'width +', '(width', '1 / 0', 'alert(1)', 'a;b', 'label + 1']) {
      expect(() => evalExpr(bad, s as never), bad).toThrow(MapExprError);
    }
  });
  it('expandTemplate walks objects/arrays and only expands "=…" strings', () => {
    const out = expandTemplate({ shape: { kind: 'box', w: '=width', h: '=thickness * 2', pts: ['=width', 7, 'plain'] }, n: 3 }, s);
    expect(out).toEqual({ shape: { kind: 'box', w: 6, h: 6, pts: [6, 7, 'plain'] }, n: 3 });
  });
});
