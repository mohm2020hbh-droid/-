/**
 * PhysicsConfig — the single, central place for every physics value.
 *
 * SOURCE OF TRUTH: `Pogostuck_Physics_LOCKED_SPEC.md` (extracted from the original binaries; evidence in
 * `Pogostuck_Physics_Final_Review.md` and `Pogostuck_Physics_Constants.csv`). Every locked value is stored
 * here exactly as the compiled game stores it (including the float-rounded doubles listed in the spec).
 *
 * UNITS (spec §1): Q = quant (length), T = tick (time unit of `time_step`), L = spring-load counter, deg = degrees.
 *
 * STATUS
 *  - `LOCKED_A`   value, operation and meaning proven by compiled game code.
 *  - `LOCKED_AB`  value and operation proven; the meaning relies on one named engine routine (see `note`).
 *  - `SUPPLIED`   the spec explicitly leaves it to the Android project (collision geometry, numerics). NOT an original value.
 *  - `DESIGN`     presentation/gameplay glue that is not physics (feedback thresholds, respawn rule).
 *  There is no `TUNE_ME` any more: nothing is estimated from gameplay (class C is empty).
 */

export type ParamStatus = 'LOCKED_A' | 'LOCKED_AB' | 'SUPPLIED' | 'DESIGN';
export type ParamGroup =
  | 'time' | 'air' | 'charge' | 'launch' | 'rotation' | 'ground' | 'bounce' | 'slide' | 'boost' | 'hull' | 'supplied' | 'presentation';

export interface ParamDef {
  value: number;
  unit: string;
  status: ParamStatus;
  /** Confidence grade of the value's *meaning*: A confirmed, B inferred (named engine semantics), D = supplied by us. */
  grade: 'A' | 'B' | 'D';
  /** Locked-spec / CSV row id (P01…), or `—`. */
  ref: string;
  group: ParamGroup;
  label: string;
  note: string;
}

const A = (ref: string, value: number, unit: string, group: ParamGroup, label: string, note = ''): ParamDef =>
  ({ value, unit, status: 'LOCKED_A', grade: 'A', ref, group, label, note });
const AB = (ref: string, value: number, unit: string, group: ParamGroup, label: string, note: string): ParamDef =>
  ({ value, unit, status: 'LOCKED_AB', grade: 'B', ref, group, label, note });
const supplied = (value: number, unit: string, label: string, note: string): ParamDef =>
  ({ value, unit, status: 'SUPPLIED', grade: 'D', ref: '—', group: 'supplied', label, note });
const design = (value: number, unit: string, label: string, note: string): ParamDef =>
  ({ value, unit, status: 'DESIGN', grade: 'D', ref: '—', group: 'presentation', label, note });

export const PARAM_DEFS = {
  // ── time (spec §2) ──────────────────────────────────────────────────────────────────────────────────────────
  tickRate: AB('T02', 120, 'tick/s', 'time', 'Tick rate', 'Fixed 120 Hz loop = the original default frame cap (fps_max 120, A). One physics update per tick; fixed-step loop is our implementation choice.'),
  ticksPerSecond: AB('—', 16, 'T/s', 'time', 'Game ticks per second', 'Spec §2: Δt = 16 · Δt_real · timeFactor (B: engine time unit).'),
  timeFactor: AB('T01', 973 / 1024, '×', 'time', 'time_factor', 'Written every play frame as var 973/1024 = 0.9501953125 (source literal 0.95). That it scales time_step is engine semantics (B).'),
  qPerMetre: A('U01', 52, 'Q/m', 'time', 'Quants per metre', 'HUD display unit (52 Q = 1 m). Metres-per-quant is a free presentation choice (spec §7); we adopt the original display unit.'),

  // ── air (E1, E2) ────────────────────────────────────────────────────────────────────────────────────────────
  gravity: A('P01', 8.5, 'Q/T²', 'air', 'Gravity', 'E1: v_z ← v_z − 8.5·Δt'),
  airDrag: A('P02', 0.05000000074505806, '1/T', 'air', 'Air drag', 'E1: v_x ← v_x − 0.05·v_x·Δt (stored double of the literal 0.05); no vertical drag.'),
  maxSpeed: A('P09', 300, 'Q/T', 'air', 'Max speed', 'E2: if |v| > 300 then v ← 300·v/|v|'),
  slideZGain: A('P11', 4, '×', 'air', 'Slide z displacement gain', 'E3: d_z = (v_z + 4·s_z)·Δt'),

  // ── rotation (E4) ───────────────────────────────────────────────────────────────────────────────────────────
  turnTarget: A('P16', 32, 'deg/T', 'rotation', 'Turn target rate', 'E4: ω_t = 32·(u_left − u_right)'),
  turnResponse: A('P17', 0.5249999761581421, '1/T', 'rotation', 'Turn response', 'E4: ω += (ω_t − ω)·0.525·Δt/(1+√J)'),
  groundTurnDivisor: A('P15', 2, '×', 'rotation', 'Ground turn divisor', 'E4: Lb = 1/(1 + 2g) → 1 in the air, 1/3 on the ground'),
  rotationHullLift: A('P18', 16, 'Q', 'rotation', 'Hull lift during rotation', 'E18: hull bottom is raised 16 Q while the rotation is collision-checked.'),

  // ── ground (E5, E6) ─────────────────────────────────────────────────────────────────────────────────────────
  groundPressure: AB('P14', 24, 'Q/T', 'ground', 'Ground pressure', 'E5: v ← −24·n (magnitude A; direction "into the surface" needs the tilt convention, B).'),
  tipPivotLimit: A('P19', 256, 'Q', 'ground', 'Tip pivot limit', 'E5: pivot correction applied if |Δtip_x| < 256 and |Δtip_z| < 256.'),
  probeExtension: A('P20', 6, 'Q', 'ground', 'Probe extension', 'E6: end = tip + (6 + |s_z|)·û_stick'),
  probeHalfSize: AB('P22', 4, 'Q', 'ground', 'Probe box half-size', 'E6: ±4 Q box trace (box semantics of c_trace: B).'),

  // ── charge: landing force + spring window + charge (E7, E8) ────────────────────────────────────────────────
  impactExponent: A('P23', 0.925000011920929, '—', 'charge', 'Impact exponent', 'E7: I = 1.65·|v|^0.925'),
  impactGain: A('P24', 1.649999976158142, 'L/(Q/T)^0.925', 'charge', 'Impact gain', 'E7'),
  impactBoostBonus: A('P25a', 20, 'L', 'charge', 'Boost bonus on impact', 'E7: + 20·p inside the L_max clamp'),
  loadMaxFloor: A('P25', 95, 'L', 'charge', 'L_max floor', 'E7: lower clamp bound 95 + 25·p'),
  loadMaxFloorBoostBonus: A('P26', 25, 'L', 'charge', 'Boost bonus on L_max floor', 'E7'),
  loadMaxCap: A('P27', 300, 'L', 'charge', 'L_max cap', 'E7: upper clamp bound'),
  loadMinExponent: A('P28', 0.8999999761581421, '—', 'charge', 'L_min exponent', 'E7: L_min = max(40, I^0.9)'),
  loadMinFloor: A('P29', 40, 'L', 'charge', 'L_min floor', 'E7'),
  chargeRate: A('P32', 16, 'L/T', 'charge', 'Charge rate', 'E8: L ← min(L + 16·Δt, L_max)'),
  minLaunchLoad: A('P33', 2, 'L', 'charge', 'Minimum launch load', 'E8: launch only if L > 2'),

  // ── launch (E9–E11) ─────────────────────────────────────────────────────────────────────────────────────────
  launchSpeedPerLoad: A('P34', 0.7423499822616577, '(Q/T)/L', 'launch', 'Launch speed per load', 'E9: |V| = 0.74235·L'),
  normalAngleOffset: AB('P35', 90, 'deg', 'launch', 'Normal-angle offset', 'E9: δ = clamp(wrap180(θ_n − 90 − θ), ±45) (ang() wrap: B)'),
  normalBlendClamp: A('P36', 45, 'deg', 'launch', 'Normal-blend clamp', 'E9'),
  normalBlendFactor: A('P37', 0.1875, '—', 'launch', 'Normal-blend factor', 'E9: a = θ + 0.1875·δ'),
  launchSlideCarryX: A('P38', 0.25, '—', 'launch', 'Launch slide carry x', 'E9: v_x = V_x + 0.25·s_x'),
  launchSlideCarryZ: A('P39', 0, '—', 'launch', 'Launch slide carry z', 'E9: v_z = V_z + 0·s_z'),
  launchSpinPerTilt: A('P40', 0.12449999898672104, '(deg/T)/deg', 'launch', 'Launch spin per tilt', 'E10'),
  launchSpinTiltClamp: A('P40b', 45, 'deg', 'launch', 'Launch spin tilt clamp', 'E10'),
  slopeSpinExponent: A('P41', 0.75, '—', 'launch', 'Slope-spin exponent', 'E10, E14: |σ|^0.75'),
  slopeSpinGain: A('P42', 1.5, '—', 'launch', 'Slope-spin gain', 'E10, E14'),
  slopeSpinLaunchFactor: A('P43', 0.25, '—', 'launch', 'Slope-spin factor at launch', 'E10'),
  noGroundTime: A('P44', 2, 'T', 'launch', 'No-ground time', 'E11, E14: N ← 2 T after a launch or wall bounce'),
  jumpTimerGain: A('P45', 0.44999998807907104, 'T/√L', 'launch', 'Jump-timer gain', 'E11: J ← 0.45·√L_max'),

  // ── boost (E13) ─────────────────────────────────────────────────────────────────────────────────────────────
  boostRotation: A('P46', 285, 'deg', 'boost', 'Boost rotation threshold', 'E13: airborne int(|θ − θ_j|) > 285 ⇒ p ← 1'),

  // ── wall bounce (E14) ───────────────────────────────────────────────────────────────────────────────────────
  bounceReflect: A('P47', -2, '—', 'bounce', 'Reflection factor', 'E14 fallback: r = v − 2(v·n)n'),
  bounceDirWeight: AB('P48', 0.8999999761581421, '—', 'bounce', 'Bounce direction weight', 'E14: d = 0.9·r̂ + n (r̂ = engine `bounce` vector in the original: B)'),
  bounceSpeedFactor: A('P49', 0.4000000059604645, '—', 'bounce', 'Bounce speed factor', 'E14: S = max(28, 0.4·|v|)·min(1+n_z, 1)'),
  bounceMinSpeed: A('P50', 28, 'Q/T', 'bounce', 'Bounce minimum speed', 'E14'),
  bounceSlopeCap: A('P73', 1, '—', 'bounce', 'Bounce slope attenuation cap', 'E14: min(1 + n_z, 1)'),
  bounceXScale: A('P51', 0.875, '—', 'bounce', 'Bounce x scale', 'E14: v_x = 0.875·b_x + s_x'),
  bounceSpin: A('P52', 0.5, '(deg/T)/deg', 'bounce', 'Bounce righting spin', 'E14: ω = 0.5·wrap180(γ − θ) − …'),
  slopeSpinBounceFactor: A('P53', 0.20000000298023224, '—', 'bounce', 'Slope-spin factor at bounce', 'E14: … − 0.2·1.5·sgn(σ)|σ|^0.75'),

  // ── slide / ice (E15) ───────────────────────────────────────────────────────────────────────────────────────
  slideTargetSpeed: AB('P55', 48, 'Q/T', 'slide', 'Slide target speed', 'E15: t = 48·normalize(n + R_γ(0,−1)) (direction needs normalize/rotate semantics: B)'),
  slideResponse: A('P57', 0.25, '1/T', 'slide', 'Slide response', 'E15: s += clamp(0.25·(t − s), ±1.35)·Δt'),
  slideAccelClamp: A('P58', 1.350000023841858, 'Q/T²', 'slide', 'Slide acceleration limit', 'E15'),
  slideDecay: AB('P59', 0.5, '1/T', 'slide', 'Slide decay', 'E15: s ← s·(1 − 0.5·Δt) out of slide mode (vec_lerp semantics: B)'),
  slideStop: A('P72', 0.25, 'Q/T', 'slide', 'Slide stop threshold', 'E15: |s| < 0.25 ∨ airborne ⇒ s ← 0'),
  platformDownReduction: A('P61', 0.75, '—', 'slide', 'Platform downward carry reduction', 'E17: v_z += c_z·(1 − 0.75·[c_z < 0])'),
  ledgeExitPop: A('P71', 5, 'Q/T', 'slide', 'Ledge exit pop', 'E16: v ← (s_x, 5)'),

  // ── hull (E18) ──────────────────────────────────────────────────────────────────────────────────────────────
  hullHalfX: AB('P62', 12.5, 'Q', 'hull', 'Hull half-width', 'E18: x ∈ [−12.5, 12.5] (use as collision box: B)'),
  hullMaxZ: AB('P64', 30, 'Q', 'hull', 'Hull top', 'E18: z_max = 30'),
  hullMinZBase: AB('P65', -55, 'Q', 'hull', 'Hull bottom (base)', 'E18: z_min = −55 + max(X, −12.25)'),
  hullExtLimit: AB('P66', -12.25, 'Q', 'hull', 'Spring-extension limit', 'E18'),
  springRelaxRate: A('P67', 120, 'L/T', 'hull', 'Spring relax rate', 'E18: airborne P_b ← max(P_b − 120·Δt, −200)'),
  springRelaxFloor: A('P67b', -200, 'L', 'hull', 'Spring relax floor', 'E18'),
  springExtAmp: AB('P68', 18, 'Q', 'hull', 'Spring-extension amplitude', 'E18: X = 18·sin(0.9·P_b) (sinv in degrees: B)'),
  springExtSineGain: AB('P68a', 0.8999999761581421, '—', 'hull', 'Spring-extension sine gain', 'E18: sin(0.9·P_b); literal verified at IMG 0x3eadb6'),
  springExtNegScale: AB('P68b', 0.009999999776482582, '—', 'hull', 'Spring-extension rescale 0.01', 'E18: X<0 ⇒ X·(0.01·P_max)·(200+X)·0.0025; literal verified at IMG 0x3eaf21'),
  springExtNegOffset: AB('P68c', 200, '—', 'hull', 'Spring-extension rescale 200', 'E18; literal verified at IMG 0x3eafc3'),
  springExtNegGain: AB('P68d', 0.0024999999441206455, '—', 'hull', 'Spring-extension rescale 0.0025', 'E18; literal verified at IMG 0x3eaf60'),

  // ── supplied by the Android project (spec §7: not original values) ─────────────────────────────────────────
  tipLength: supplied(55, 'Q', 'Stick length (origin → tip end)', 'Our own pogo geometry: equals the hull base so the tip end meets the hull bottom when upright. Original geometry lives in model files (D).'),
  tipRadius: supplied(4, 'Q', 'Tip contact radius', 'Our collision shape for the tip; reuses the probe half-size (P22).'),
  collisionStep: supplied(2, 'Q', 'Collision sub-step', 'Numerical: maximum travel per collision sub-step (anti-tunnelling).'),
  hullSupportsFloor: supplied(0, 'bool', 'Hull bottom supports the body', '0 = floor-like hull contacts (n_z > 0.5) are ignored; the tip supports the body (E5/E6).'),

  // ── presentation (not physics) ─────────────────────────────────────────────────────────────────────────────
  hardImpactLoad: design(150, 'L', 'Hard-impact impulse', 'Feedback only: landing impulse I at which audio/haptics/camera treat a landing as hard.'),
  safeLandingRecord: design(1, 'bool', 'Landing records a respawn point', 'The pogo hops continuously, so the safe respawn spot is stored at each landing on a safe platform (instead of after standing still).'),
} satisfies Record<string, ParamDef>;

export type ParamKey = keyof typeof PARAM_DEFS;
export type PhysicsConfig = { -readonly [K in ParamKey]: number };

export function createPhysicsConfig(overrides: Partial<PhysicsConfig> = {}): PhysicsConfig {
  const cfg = {} as PhysicsConfig;
  for (const k of Object.keys(PARAM_DEFS) as ParamKey[]) cfg[k] = PARAM_DEFS[k].value;
  return Object.assign(cfg, overrides);
}

export function paramsByStatus(status: ParamStatus): ParamKey[] {
  return (Object.keys(PARAM_DEFS) as ParamKey[]).filter(k => PARAM_DEFS[k].status === status);
}
