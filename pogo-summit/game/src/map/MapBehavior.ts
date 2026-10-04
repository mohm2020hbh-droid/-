/**
 * MapBehavior — data-driven behaviours as pure functions of time and counters (SPEC §8).
 *
 *   move      sine (per-axis Lissajous) · linear (waypoints) · path (polyline / bezier / spline), loop / pingpong / once
 *   rotate    continuous · sine · free (closed-form damped flywheel)
 *   toggle    counter parity (jumps / boosts / checkpoints / flag) → active set
 *   timed     clock duty cycle
 *   conditional  boolean condition over flags and counters
 *
 * Nothing here has state: `f(tick, view)`. The sine mode uses exactly the arithmetic of `PhysicsWorld.offsetAt`, so a
 * migrated level moves bit-identically to the original data.
 */
import type {
  BehaviorDef, Condition, ConditionalBehavior, InactiveState, MoveBehavior, PathDef, RotateBehavior, TimedBehavior, ToggleBehavior, Vec2,
} from './schema';
import { TAU, TICK_RATE } from '../sim/math';

// ── view of the world a behaviour may read ──────────────────────────────────────────────────────────────────────
export interface BehaviorView {
  /** The physics tick about to be simulated. */
  tick: number;
  jumps: number;
  boosts: number;
  deaths: number;
  /** Number of checkpoints reached so far this run. */
  checkpoints: number;
  flags: ReadonlyMap<string, number | boolean>;
  reached: ReadonlySet<string>;
}

export function evalCondition(c: Condition, v: BehaviorView): boolean {
  if ('flag' in c) { const f = v.flags.get(c.flag); return f === true || (typeof f === 'number' && f !== 0); }
  if ('counter' in c) {
    const n = c.counter === 'jumps' ? v.jumps : c.counter === 'boosts' ? v.boosts : c.counter === 'deaths' ? v.deaths : v.checkpoints;
    const m = c.mod && c.mod > 0 ? c.mod : 0;
    const k = m ? ((n % m) + m) % m : n;
    return c.in.includes(k);
  }
  if ('checkpoint' in c) return v.reached.has(c.checkpoint) === c.reached;
  if ('all' in c) return c.all.every(x => evalCondition(x, v));
  if ('any' in c) return c.any.some(x => evalCondition(x, v));
  return !evalCondition(c.not, v);
}

// ── curves (polyline / bezier / spline) with an arc-length table ────────────────────────────────────────────────
export interface Curve { length: number; at(s: number, out: Vec2): Vec2; bounds(): { minX: number; maxX: number; minY: number; maxY: number } }
const SAMPLES_PER_SEGMENT = 32;

function tableCurve(samples: Vec2[]): Curve {
  const cum = [0];
  for (let i = 1; i < samples.length; i++) cum.push(cum[i - 1] + Math.hypot(samples[i].x - samples[i - 1].x, samples[i].y - samples[i - 1].y));
  const length = cum[cum.length - 1];
  return {
    length,
    at(s, out) {
      if (samples.length === 0) { out.x = 0; out.y = 0; return out; }
      if (length <= 0 || s <= 0) { out.x = samples[0].x; out.y = samples[0].y; return out; }
      if (s >= length) { const l = samples[samples.length - 1]; out.x = l.x; out.y = l.y; return out; }
      let lo = 0, hi = cum.length - 1;
      while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (cum[mid] <= s) lo = mid; else hi = mid; }
      const t = (s - cum[lo]) / (cum[hi] - cum[lo] || 1);
      out.x = samples[lo].x + (samples[hi].x - samples[lo].x) * t;
      out.y = samples[lo].y + (samples[hi].y - samples[lo].y) * t;
      return out;
    },
    bounds() {
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      for (const p of samples) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
      return { minX, maxX, minY, maxY };
    },
  };
}

export function buildCurve(def: Pick<PathDef, 'kind' | 'points' | 'closed'>): Curve {
  const pts = def.points;
  if (pts.length === 0) return tableCurve([]);
  if (def.kind === 'polyline' || pts.length < 3 && def.kind === 'spline') return tableCurve(def.closed ? [...pts, pts[0]] : [...pts]);
  const out: Vec2[] = [];
  if (def.kind === 'bezier') {
    for (let i = 0; i + 3 < pts.length; i += 3) {
      const [p0, p1, p2, p3] = [pts[i], pts[i + 1], pts[i + 2], pts[i + 3]];
      for (let k = i === 0 ? 0 : 1; k <= SAMPLES_PER_SEGMENT; k++) {
        const t = k / SAMPLES_PER_SEGMENT, u = 1 - t;
        out.push({ x: u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x, y: u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y });
      }
    }
    if (out.length === 0) return tableCurve([...pts]);
    return tableCurve(def.closed ? [...out, out[0]] : out);
  }
  // uniform Catmull-Rom through the control points
  const n = pts.length;
  const get = (i: number): Vec2 => (def.closed ? pts[((i % n) + n) % n] : pts[Math.max(0, Math.min(n - 1, i))]);
  const segs = def.closed ? n : n - 1;
  for (let i = 0; i < segs; i++) {
    const p0 = get(i - 1), p1 = get(i), p2 = get(i + 1), p3 = get(i + 2);
    for (let k = i === 0 ? 0 : 1; k <= SAMPLES_PER_SEGMENT; k++) {
      const t = k / SAMPLES_PER_SEGMENT, t2 = t * t, t3 = t2 * t;
      out.push({
        x: 0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
        y: 0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
      });
    }
  }
  return tableCurve(out);
}

// ── motion ──────────────────────────────────────────────────────────────────────────────────────────────────────
export interface Offset3 { x: number; y: number; z: number; vx: number; vy: number }
export interface Motion {
  /** Offset (metres) from the entity origin at a physics tick, with the analytic/finite-difference velocity (m/s). */
  at(tick: number, out: Offset3): Offset3;
  /** Bounding box of every offset the motion can reach. */
  range: { minX: number; maxX: number; minY: number; maxY: number };
}

const smooth = (t: number): number => t * t * (3 - 2 * t);

/**
 * Position along a curve for a cycle clock: `loop` wraps, `pingpong` goes there and back, `once` stops at the end.
 * `pause` seconds are held at each end. Returns the arc-length position s ∈ [0, L].
 */
function curveParam(L: number, durationS: number, mode: 'loop' | 'pingpong' | 'once', pause: number, ease: 'linear' | 'smooth', tS: number): number {
  if (L <= 0 || durationS <= 0) return 0;
  const p = Math.max(0, pause);
  let u: number;
  if (mode === 'once') {
    if (tS <= 0) u = 0;
    else if (tS < p) u = 0;
    else u = Math.min(1, (tS - p) / durationS);
  } else if (mode === 'loop') {
    const cyc = durationS + p;
    const c = ((tS % cyc) + cyc) % cyc;
    u = c < p ? 0 : (c - p) / durationS;
  } else {
    const leg = durationS + p;
    const cyc = 2 * leg;
    const c = ((tS % cyc) + cyc) % cyc;
    if (c < leg) u = c < p ? 0 : (c - p) / durationS;
    else { const d = c - leg; u = 1 - (d < p ? 0 : (d - p) / durationS); }
  }
  u = Math.max(0, Math.min(1, u));
  return L * (ease === 'smooth' ? smooth(u) : u);
}

const rangeOf = (c: Curve, rel: Vec2) => { const b = c.bounds(); return { minX: b.minX - rel.x, maxX: b.maxX - rel.x, minY: b.minY - rel.y, maxY: b.maxY - rel.y }; };

export function compileMove(b: MoveBehavior, paths: ReadonlyMap<string, Curve>): Motion | null {
  if (b.mode === 'sine') {
    const ax = b.x, ay = b.y, az = b.z;
    const ok = (o: typeof ax): o is NonNullable<typeof ax> => !!o && Number.isFinite(o.period) && o.period > 0 && Number.isFinite(o.amplitude);
    const eval1 = (o: NonNullable<typeof ax>, tick: number): { v: number; d: number } => {
      const periodTicks = o.period * TICK_RATE;
      const th = TAU * (tick / periodTicks + (o.phase ?? 0));
      const w = (TAU * TICK_RATE) / periodTicks;
      return { v: o.amplitude * Math.sin(th), d: o.amplitude * Math.cos(th) * w };
    };
    return {
      at(tick, out) {
        out.x = out.y = out.z = out.vx = out.vy = 0;
        if (ok(ax)) { const r = eval1(ax, tick); out.x = r.v; out.vx = r.d; }
        if (ok(ay)) { const r = eval1(ay, tick); out.y = r.v; out.vy = r.d; }
        if (ok(az)) out.z = eval1(az, tick).v;
        return out;
      },
      range: { minX: -(ok(ax) ? Math.abs(ax.amplitude) : 0), maxX: ok(ax) ? Math.abs(ax.amplitude) : 0, minY: -(ok(ay) ? Math.abs(ay.amplitude) : 0), maxY: ok(ay) ? Math.abs(ay.amplitude) : 0 },
    };
  }
  let curve: Curve;
  let origin: Vec2 = { x: 0, y: 0 };
  let speed: number | undefined; let duration: number | undefined;
  let mode: 'loop' | 'pingpong' | 'once' = 'loop'; let pause = 0; let ease: 'linear' | 'smooth' = 'linear'; let phase = 0;
  if (b.mode === 'linear') {
    if (!b.points || b.points.length === 0 || !(b.speed > 0)) return null;
    const pts: Vec2[] = [{ x: 0, y: 0 }, ...b.points];
    curve = buildCurve({ kind: 'polyline', points: pts, closed: !b.pingPong });
    speed = b.speed; mode = b.pingPong ? 'pingpong' : 'loop'; pause = b.pause ?? 0; ease = b.ease ?? 'linear'; phase = b.phase ?? 0;
  } else {
    const c = paths.get(b.path);
    if (!c || c.length <= 0) return null;
    curve = c; speed = b.speed; duration = b.duration; mode = b.loop ?? 'loop'; pause = b.pause ?? 0; phase = b.phase ?? 0;
    const o = { x: 0, y: 0 }; c.at(0, o); origin = o;
  }
  const dur = duration && duration > 0 ? duration : speed && speed > 0 ? curve.length / speed : 0;
  if (!(dur > 0)) return null;
  const cycle = mode === 'pingpong' ? 2 * (dur + pause) : dur + pause;
  const tmp: Vec2 = { x: 0, y: 0 }, tmp2: Vec2 = { x: 0, y: 0 };
  const posAt = (tS: number, out: Vec2): Vec2 => { curve.at(curveParam(curve.length, dur, mode, pause, ease, tS + phase * cycle), out); out.x -= origin.x; out.y -= origin.y; return out; };
  return {
    at(tick, out) {
      const tS = tick / TICK_RATE;
      posAt(tS, tmp);
      out.x = tmp.x; out.y = tmp.y; out.z = 0;
      const h = 0.5 / TICK_RATE;
      posAt(tS + h, tmp2); const ax2 = tmp2.x, ay2 = tmp2.y;
      posAt(tS - h, tmp2);
      out.vx = (ax2 - tmp2.x) * TICK_RATE; out.vy = (ay2 - tmp2.y) * TICK_RATE;
      return out;
    },
    range: rangeOf(curve, origin),
  };
}

// ── rotation ────────────────────────────────────────────────────────────────────────────────────────────────────
export interface Rotation { axis: 'x' | 'y' | 'z'; pivot: Vec2; angle(tick: number): number }
export function compileRotate(b: RotateBehavior): Rotation {
  const base = b.base ?? 0;
  const pivot = b.pivot ?? { x: 0, y: 0 };
  const axis = b.axis ?? 'z';
  let angle: (tick: number) => number;
  if (b.mode === 'sine') {
    const period = b.period && b.period > 0 ? b.period : 4, amp = b.amplitude ?? 45, ph = b.phase ?? 0;
    angle = tick => base + amp * Math.sin(TAU * (tick / (period * TICK_RATE) + ph));
  } else if (b.mode === 'free') {
    const w0 = b.initialSpeed ?? b.speed ?? 0, k = b.damping ?? 0.3;
    angle = tick => { const t = tick / TICK_RATE; return k > 0 ? base + (w0 / k) * (1 - Math.exp(-k * t)) : base + w0 * t; };
  } else {
    const sp = b.speed ?? 0;
    angle = tick => base + sp * (tick / TICK_RATE);
  }
  return { axis, pivot, angle };
}

// ── gating (toggle / timed / conditional) ───────────────────────────────────────────────────────────────────────
export interface GateState { active: boolean; blink: boolean; inactive: Required<Pick<InactiveState, 'visual'>> }
const DEFAULT_INACTIVE: InactiveState = { collision: false, visual: 'ghost' };

export function evalToggle(b: ToggleBehavior, v: BehaviorView): GateState {
  const m = b.modulus && b.modulus >= 1 ? Math.floor(b.modulus) : 2;
  const n = b.channel === 'jumps' ? v.jumps : b.channel === 'boosts' ? v.boosts : b.channel === 'checkpoints' ? v.checkpoints : Number(v.flags.get(b.flag ?? '') ?? 0) || 0;
  const k = ((Math.floor(n) % m) + m) % m;
  return { active: b.active.includes(k), blink: false, inactive: { visual: (b.inactive ?? DEFAULT_INACTIVE).visual ?? 'ghost' } };
}

export function evalTimed(b: TimedBehavior, v: BehaviorView): GateState {
  const period = b.period > 0 ? b.period : 1;
  const duty = b.duty ?? 0.5;
  const cycles = v.tick / TICK_RATE / period + (b.phase ?? 0);
  const u = cycles - Math.floor(cycles);
  const active = u < duty;
  const blink = !!b.warn && b.warn > 0 && active && (duty - u) * period < b.warn;
  return { active, blink, inactive: { visual: (b.inactive ?? DEFAULT_INACTIVE).visual ?? 'ghost' } };
}

export function evalConditional(b: ConditionalBehavior, v: BehaviorView): GateState {
  return { active: evalCondition(b.when, v), blink: false, inactive: { visual: (b.inactive ?? DEFAULT_INACTIVE).visual ?? 'ghost' } };
}

export type GateBehavior = ToggleBehavior | TimedBehavior | ConditionalBehavior;
export const isGate = (b: BehaviorDef): b is GateBehavior => b.type === 'toggle' || b.type === 'timed' || b.type === 'conditional';
export function evalGate(b: GateBehavior, v: BehaviorView): GateState {
  return b.type === 'toggle' ? evalToggle(b, v) : b.type === 'timed' ? evalTimed(b, v) : evalConditional(b, v);
}

/** Problems of a behaviour block (used by the validator). */
export function behaviorProblems(b: BehaviorDef, pathIds: ReadonlySet<string>): string[] {
  const out: string[] = [];
  const pos = (v: unknown, n: string) => { if (typeof v !== 'number' || !Number.isFinite(v) || v <= 0) out.push(`${n} must be a positive number`); };
  switch (b.type) {
    case 'move':
      if (b.mode === 'sine') {
        for (const ax of ['x', 'y', 'z'] as const) { const o = b[ax]; if (o) { pos(o.period, `move.${ax}.period`); if (!Number.isFinite(o.amplitude)) out.push(`move.${ax}.amplitude must be finite`); } }
        if (!b.x && !b.y && !b.z) out.push('sine move needs at least one axis');
      } else if (b.mode === 'linear') { if (!b.points?.length) out.push('linear move needs points'); pos(b.speed, 'move.speed'); }
      else if (b.mode === 'path') { if (!pathIds.has(b.path)) out.push(`path "${b.path}" is not defined`); if (!(b.speed && b.speed > 0) && !(b.duration && b.duration > 0)) out.push('path move needs speed or duration'); }
      else out.push(`unknown move mode`);
      break;
    case 'rotate':
      if (!['continuous', 'sine', 'free'].includes(b.mode)) out.push('unknown rotate mode');
      if (b.mode === 'sine') pos(b.period ?? 4, 'rotate.period');
      if (b.mode === 'free' && b.damping !== undefined && b.damping < 0) out.push('rotate.damping must be ≥ 0');
      break;
    case 'toggle':
      if (!Array.isArray(b.active) || b.active.length === 0) out.push('toggle.active must list at least one value');
      if (b.modulus !== undefined && (!Number.isInteger(b.modulus) || b.modulus < 1)) out.push('toggle.modulus must be a positive integer');
      if (b.channel === 'flag' && !b.flag) out.push('toggle on channel "flag" needs a flag name');
      break;
    case 'timed': pos(b.period, 'timed.period'); if (b.duty !== undefined && (b.duty <= 0 || b.duty >= 1)) out.push('timed.duty must be in (0, 1)'); break;
    case 'breakable': if (b.delay !== undefined && b.delay < 0) out.push('breakable.delay must be ≥ 0'); if (b.respawn !== undefined && b.respawn < 0) out.push('breakable.respawn must be ≥ 0'); break;
    case 'conditional': if (!b.when) out.push('conditional needs `when`'); break;
    default: break;
  }
  return out;
}
