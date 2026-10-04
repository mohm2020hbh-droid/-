/**
 * MapExpr — the tiny, safe expression language of prefab templates (SPEC §7).
 *
 * `"=width * 0.5 + max(a, 1)"` is evaluated over the prefab parameters. Hand-written recursive-descent parser: numbers,
 * parameter names, `+ - * /`, unary minus, parentheses, `min max abs clamp sqrt floor ceil round`, constant `pi`.
 * No property access, no calls to anything else, never `eval`.
 */
export class MapExprError extends Error {}

export type ExprValue = number | boolean | string;
export type ExprScope = Record<string, ExprValue>;

const FUNCS: Record<string, (...a: number[]) => number> = {
  min: Math.min, max: Math.max, abs: Math.abs, sqrt: Math.sqrt, floor: Math.floor, ceil: Math.ceil, round: Math.round,
  clamp: (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v),
};
const CONSTS: Record<string, number> = { pi: Math.PI };

interface Tok { t: 'num' | 'id' | 'op' | 'end'; v: string }

function lex(src: string): Tok[] {
  const out: Tok[] = [];
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === ' ' || c === '\t') { i++; continue; }
    if (/[0-9.]/.test(c)) {
      let j = i; while (j < src.length && /[0-9.eE]/.test(src[j]) && !(src[j] === 'e' && !/[0-9+-]/.test(src[j + 1] ?? ''))) j++;
      // allow exponent sign
      if ((src[j - 1] === 'e' || src[j - 1] === 'E') && (src[j] === '+' || src[j] === '-')) { j++; while (j < src.length && /[0-9]/.test(src[j])) j++; }
      out.push({ t: 'num', v: src.slice(i, j) }); i = j; continue;
    }
    if (/[A-Za-z_]/.test(c)) { let j = i; while (j < src.length && /[A-Za-z0-9_]/.test(src[j])) j++; out.push({ t: 'id', v: src.slice(i, j) }); i = j; continue; }
    if ('+-*/(),'.includes(c)) { out.push({ t: 'op', v: c }); i++; continue; }
    throw new MapExprError(`unexpected character "${c}" in expression "${src}"`);
  }
  out.push({ t: 'end', v: '' });
  return out;
}

class Parser {
  private i = 0;
  constructor(private readonly toks: Tok[], private readonly scope: ExprScope, private readonly src: string) {}
  parse(): number {
    const v = this.expr();
    if (this.toks[this.i].t !== 'end') throw new MapExprError(`unexpected "${this.toks[this.i].v}" in "${this.src}"`);
    return v;
  }
  private peek(): Tok { return this.toks[this.i]; }
  private eat(v: string): boolean { const t = this.peek(); if (t.t === 'op' && t.v === v) { this.i++; return true; } return false; }
  private expr(): number {
    let v = this.term();
    for (;;) { if (this.eat('+')) v += this.term(); else if (this.eat('-')) v -= this.term(); else return v; }
  }
  private term(): number {
    let v = this.unary();
    for (;;) {
      if (this.eat('*')) v *= this.unary();
      else if (this.eat('/')) { const d = this.unary(); if (d === 0) throw new MapExprError(`division by zero in "${this.src}"`); v /= d; }
      else return v;
    }
  }
  private unary(): number { if (this.eat('-')) return -this.unary(); if (this.eat('+')) return this.unary(); return this.primary(); }
  private primary(): number {
    const t = this.peek();
    if (t.t === 'num') { this.i++; const n = Number(t.v); if (!Number.isFinite(n)) throw new MapExprError(`bad number "${t.v}"`); return n; }
    if (t.t === 'id') {
      this.i++;
      if (this.eat('(')) {
        const f = FUNCS[t.v];
        if (!f) throw new MapExprError(`unknown function "${t.v}"`);
        const args: number[] = [];
        if (!this.eat(')')) { do args.push(this.expr()); while (this.eat(',')); if (!this.eat(')')) throw new MapExprError(`missing ")" in "${this.src}"`); }
        return f(...args);
      }
      if (t.v in CONSTS) return CONSTS[t.v];
      if (!(t.v in this.scope)) throw new MapExprError(`unknown parameter "${t.v}" in "${this.src}"`);
      const v = this.scope[t.v];
      if (typeof v === 'boolean') return v ? 1 : 0;
      if (typeof v !== 'number') throw new MapExprError(`parameter "${t.v}" is not a number in "${this.src}"`);
      return v;
    }
    if (this.eat('(')) { const v = this.expr(); if (!this.eat(')')) throw new MapExprError(`missing ")" in "${this.src}"`); return v; }
    throw new MapExprError(`unexpected "${t.v || 'end'}" in "${this.src}"`);
  }
}

/** Evaluate `src` (without the leading "="). A lone parameter name returns the raw value (string/boolean allowed). */
export function evalExpr(src: string, scope: ExprScope): ExprValue {
  const s = src.trim();
  if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(s) && s in scope) return scope[s];
  return new Parser(lex(s), scope, s).parse();
}

/** Recursively expand every string that starts with "=" inside a JSON-like value. */
export function expandTemplate<T>(value: T, scope: ExprScope): T {
  if (typeof value === 'string') return (value.startsWith('=') ? evalExpr(value.slice(1), scope) : value) as unknown as T;
  if (Array.isArray(value)) return value.map(v => expandTemplate(v, scope)) as unknown as T;
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) out[k] = expandTemplate(v, scope);
    return out as T;
  }
  return value;
}
