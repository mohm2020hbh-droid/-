/**
 * PhysicsConfig — the single, central place for every physics value.
 *
 * EVIDENCE RULES (see PHYSICS_MASTER.md / DECISIONS.md DEC-010..014):
 *  - The source XLSX/PDF state that NO numeric movement physics exists in the game files
 *    (it lives in an encrypted overlay). So every movement parameter is `TUNE_ME`.
 *  - `TUNE_ME` means: "no documented number; `value` is OUR OWN working starting point so the
 *    game is playable". It is never the original Pogostuck value and must not be presented as one.
 *  - `SOURCE_A` is used only for values actually documented at grade A (tick rate, fixed-point step).
 *  - `DESIGN` is for assist features that do not exist in the original (coyote time, press buffer…).
 *  - When a value is measured (see PHYSICS_MASTER.md §5) change its status to `MEASURED_C` and cite it.
 */

export type ParamStatus = 'TUNE_ME' | 'SOURCE_A' | 'DESIGN' | 'MEASURED_C';
export type ParamGroup = 'time' | 'launch' | 'rotation' | 'collision' | 'boost' | 'geometry' | 'assist';

export interface ParamDef {
  value: number;
  unit: string;
  min: number;
  max: number;
  step: number;
  status: ParamStatus;
  /** Confidence grade of the *original* value: A/B/C documented, D unknown. */
  grade: 'A' | 'B' | 'C' | 'D';
  /** Reference into the XLSX (U-xx unknown row, V-xxx video row, F-xxx file row). */
  ref: string;
  group: ParamGroup;
  label: string;
  note: string;
}

const tune = (
  value: number, unit: string, min: number, max: number, step: number,
  ref: string, group: ParamGroup, label: string, note: string,
): ParamDef => ({ value, unit, min, max, step, status: 'TUNE_ME', grade: 'D', ref, group, label, note });

const source = (
  value: number, unit: string, ref: string, group: ParamGroup, label: string, note: string,
): ParamDef => ({ value, unit, min: value, max: value, step: 0, status: 'SOURCE_A', grade: 'A', ref, group, label, note });

const design = (
  value: number, unit: string, min: number, max: number, step: number,
  group: ParamGroup, label: string, note: string,
): ParamDef => ({ value, unit, min, max, step, status: 'DESIGN', grade: 'D', ref: '—', group, label, note });

export const PARAM_DEFS = {
  // ── time ────────────────────────────────────────────────────────────────
  tickRate: source(120, 'tick/s', 'V-001', 'time', 'Tick rate', 'معدل المحاكاة في الأصل (لوحة تصحيح الفيديو)؛ تبنّيناه قرارًا.'),
  fixedPointStep: source(1 / 1024, 'unit', 'F-003/F-005', 'time', 'Fixed-point step', 'دقة 22.10 في المحرك الأصلي؛ نستخدمها للحتمية فقط.'),

  // ── launch / gravity ────────────────────────────────────────────────────
  gravity: tune(26, 'm/s²', 8, 60, 0.5, 'U-01', 'launch', 'Gravity', 'الجاذبية (مخزنة في overlay مشفّر).'),
  maxFallSpeed: tune(32, 'm/s', 10, 80, 1, 'U-02', 'launch', 'Max fall speed', 'السرعة النهائية للسقوط.'),
  maxHorizontalSpeed: tune(22, 'm/s', 5, 60, 1, 'U-15', 'launch', 'Max horizontal speed', 'سقف السرعة الأفقية.'),
  launchSpeedMin: tune(8.5, 'm/s', 3, 20, 0.1, 'U-03', 'launch', 'Launch speed (min)', 'سرعة الإطلاق عند أقصر ضغطة.'),
  launchSpeedMax: tune(19, 'm/s', 8, 40, 0.1, 'U-04', 'launch', 'Launch speed (max)', 'سرعة الإطلاق بعد شحن كامل.'),
  chargeTicksMax: tune(84, 'tick', 20, 240, 1, 'U-05', 'launch', 'Charge ticks (max)', 'مدة الشحن الكامل. الأولوية 1 للقياس من مدد الخانة 5 في الفيديو.'),
  chargeCurve: tune(1, 'exp', 0.5, 2, 0.05, 'U-15', 'launch', 'Charge curve', 'أس منحنى الشحن (1 = خطي).'),

  // ── rotation / control ──────────────────────────────────────────────────
  tiltRateGround: tune(150, '°/s', 30, 400, 5, 'U-06', 'rotation', 'Tilt rate (ground)', 'أقصى معدل ميل على الأرض.'),
  tiltRateAir: tune(380, '°/s', 60, 900, 10, 'U-07', 'rotation', 'Tilt rate (air)', 'أقصى معدل دوران جوًّا.'),
  tiltMaxAngle: tune(65, '°', 20, 85, 1, 'U-08', 'rotation', 'Tilt max angle', 'أقصى ميل أرضي عن عمود السطح.'),
  turnSpeed: tune(1600, '°/s²', 200, 6000, 50, 'U-09', 'rotation', 'Turn speed', 'تسارع تغيّر معدل الدوران.'),
  angularDamping: tune(1.6, '1/s', 0, 8, 0.1, 'U-09', 'rotation', 'Angular damping', 'تخميد الدوران جوًّا عند غياب الإدخال.'),
  groundControl: tune(1, 'x', 0.2, 2, 0.05, 'U-15', 'rotation', 'Ground control', 'سلطة التحكم أرضًا.'),
  airControl: tune(1, 'x', 0.2, 2, 0.05, 'U-15', 'rotation', 'Air control', 'سلطة التحكم جوًّا.'),

  // ── collision / surfaces ────────────────────────────────────────────────
  floorRestitution: tune(0.18, 'x', 0, 1, 0.01, 'U-10', 'collision', 'Floor restitution', 'ارتداد الأرض.'),
  wallRestitution: tune(0.45, 'x', 0, 1, 0.01, 'U-11', 'collision', 'Wall restitution', 'ارتداد الجدار والسقف.'),
  energyLoss: tune(0.15, 'x', 0, 0.9, 0.01, 'U-12', 'collision', 'Energy loss', 'الفقد الإضافي للزخم المماسي عند كل اصطدام.'),
  slideFriction: tune(0.3, 'μ', 0, 2, 0.01, 'U-13', 'collision', 'Slide friction', 'معامل الاحتكاك الحركي أثناء الانزلاق.'),
  steepSlopeAngle: tune(52, '°', 15, 80, 1, 'U-14', 'collision', 'Steep slope angle', 'أكبر ميل يثبت عليه الطرف على سطح عادي.'),
  stableLandingAngle: tune(72, '°', 30, 89, 1, 'U-15', 'collision', 'Stable landing angle', 'أقصى زاوية بين العصا وعمود السطح لهبوط ناجح؛ أكبر منها = انحراف.'),
  plantSpeed: tune(3.5, 'm/s', 0.5, 10, 0.1, 'U-10', 'collision', 'Plant speed', 'سرعة عمودية بعد الارتداد دونها يثبت الطرف.'),
  deceleration: tune(16, 'm/s²', 0, 60, 0.5, 'U-13', 'collision', 'Deceleration', 'فرملة الانزلاق المتبقي بعد الهبوط (× احتكاك السطح).'),
  acceleration: tune(22, 'm/s²', 0, 80, 1, 'U-15', 'collision', 'Acceleration', 'تسارع أسطح الدفع (Boost pad) على الطرف المزروع.'),
  landingResponse: tune(0.55, 'x', 0, 1, 0.01, 'U-15', 'collision', 'Landing response', 'نسبة الزخم المماسي المحوّلة إلى تأرجح العصا عند الهبوط.'),
  launchMomentumRetain: tune(0.75, 'x', 0, 1, 0.01, 'U-15', 'collision', 'Launch momentum retain', 'نسبة سرعة الانزلاق التي تُحفظ عند الإطلاق.'),
  hardImpactSpeed: tune(17, 'm/s', 5, 40, 0.5, 'U-12', 'collision', 'Hard impact speed', 'عتبة حدث الاصطدام الشديد (صوت/اهتزاز/اهتزاز كاميرا).'),
  restSpeed: tune(1.0, 'm/s', 0.1, 4, 0.1, 'U-10', 'collision', 'Rest speed', 'دون هذه السرعة العمودية يُعدّ التماس سكونًا بلا ارتداد.'),

  // ── boost ───────────────────────────────────────────────────────────────
  boostThreshold: tune(140, '°/s', 30, 600, 5, 'U-25', 'boost', 'Boost threshold', 'أدنى سرعة زاوية تُحتسب كدوران.'),
  boostRotation: tune(330, '°', 90, 1080, 10, 'U-25', 'boost', 'Boost rotation', 'الدوران المتراكم المطلوب لتجهيز Boost (الأصل: وضع 720° — الآلية D).'),
  boostPower: tune(9, 'm/s', 2, 25, 0.5, 'U-25', 'boost', 'Boost power', 'السرعة المضافة على محور العصا.'),

  // ── geometry (player + stick), U-16 ─────────────────────────────────────
  tipRadius: tune(0.14, 'm', 0.05, 0.4, 0.01, 'U-16', 'geometry', 'Tip radius', 'نصف قطر دائرة طرف العصا.'),
  bodyRadius: tune(0.5, 'm', 0.2, 0.9, 0.01, 'U-16', 'geometry', 'Torso radius', 'نصف قطر دائرة الجذع.'),
  headRadius: tune(0.38, 'm', 0.15, 0.7, 0.01, 'U-16', 'geometry', 'Head radius', 'نصف قطر دائرة الرأس.'),
  comHeight: tune(1.2, 'm', 0.6, 2, 0.01, 'U-16', 'geometry', 'COM height', 'بُعد مركز الكتلة (الجذع) عن نهاية العصا.'),
  headHeight: tune(1.95, 'm', 1, 3, 0.01, 'U-16', 'geometry', 'Head height', 'بُعد مركز الرأس عن نهاية العصا.'),

  impactSpin: tune(0.18, 'rad/s per m/s', 0, 0.6, 0.01, 'U-09', 'collision', 'Impact spin', 'ركلة دوران العصا عند ارتطام الجسم (تظهر كتخبّط).'),

  // ── assists (not in the original — our own mobile-friendliness features) ─
  coyoteTicks: design(6, 'tick', 0, 20, 1, 'assist', 'Coyote ticks', 'مهلة إطلاق بعد فقدان الدعم (50ms).'),
  pressBufferTicks: design(8, 'tick', 0, 24, 1, 'assist', 'Press buffer', 'لمسة قبل الهبوط بلحظة تُحتسب بداية شحن عند الهبوط.'),
  safeTicks: design(60, 'tick', 10, 240, 5, 'assist', 'Safe spot ticks', 'مدة الثبات على منصة لتصبح نقطة إعادة آمنة.'),
  bounceAimMax: design(35, '°', 0, 70, 1, 'assist', 'Bounce aim max', 'أقصى انحراف لتوجيه ارتداد النابض بميل العصا.'),
} satisfies Record<string, ParamDef>;

export type ParamKey = keyof typeof PARAM_DEFS;
export type PhysicsConfig = { -readonly [K in ParamKey]: number };

export function createPhysicsConfig(overrides: Partial<PhysicsConfig> = {}): PhysicsConfig {
  const cfg = {} as PhysicsConfig;
  for (const k of Object.keys(PARAM_DEFS) as ParamKey[]) cfg[k] = PARAM_DEFS[k].value;
  return Object.assign(cfg, overrides);
}

/** Reference value from the video analysis: avg ticks between two jumps (XLSX V-026, grade A derived). */
export const REFERENCE_JUMP_INTERVAL_TICKS = 150;
export const REFERENCE_JUMP_INTERVAL_SECONDS = 144.9 / 116; // V-025 = 1.249 s

export function paramsByStatus(status: ParamStatus): ParamKey[] {
  return (Object.keys(PARAM_DEFS) as ParamKey[]).filter(k => PARAM_DEFS[k].status === status);
}
