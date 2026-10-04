# PHYSICS_MASTER.md — الفيزياء: ما هو معروف، وما هو TUNE_ME

> المصدر الأساسي: `Pogostuck_Physics_Master.xlsx` (7 أوراق) + PDF «تحليل لعبة Pogostuck» (27 ص).
> **الخلاصة الصريحة للمصدرين:** *لا يوجد أي رقم فيزيائي لحركة اللاعب في الملفات* (الجاذبية، القفز، الاحتكاك، الارتداد كلها في overlay مشفّر 4MB، entropy 7.82). لذلك:
> 1. لا نخترع قيمًا ونقول إنها الأصلية.
> 2. كل معامل حركة = `TUNE_ME` (قيمة بداية عملية من عندنا، قابلة للتعديل الحي في Physics Lab).
> 3. ما نأخذه من المصادر: **البنية والتوقيت والنِّسَب** فقط.

---

## 1) ما استُخرج من XLSX (مجموع الأوراق السبع)

| الورقة | المحتوى | ما استخدمناه فعليًا | الدرجة |
|---|---|---|---|
| اقرأني | القواعد ومفتاح الدرجات | قاعدة «لا نخلط الملفات بالفيديو»، و«لعبتنا لا تحتاج أرقام Pogostuck: كل حقل TUNE-ME» | — |
| ملفات_قيم (F-001…F-060) | محرك، تحكم، سلوكات، أزمنة صوت، شبكة تقدم | F-003/004 ثابتة الفاصلة 1/1024 · F-008 اصطدام مبسّط منفصل عن العرض (`_COL_`) · F-011 نموذج «زاوية+توقيت» · F-016 وجود Double jump/720° boost · F-018..021 ارتداد/انزلاق من الأسماء · F-025..035 معاملات سلوك العناصر (مرجع حركات المنصات) · F-036/037 توقيت صوت الشحن/الإطلاق · F-047..060 فكرة شبكة التقدم | A/B |
| ملفات_أبعاد (D-001…D-208) | أبعاد المجسمات | **نِسَب فقط**: كتلة أساسية 128؛ منصة طويلة 502×59 (≈ 3.9 كتلة)؛ شوكة ≈ 0.47 كتلة؛ عجلة ≈ 2.3 كتلة؛ نصف قطر التوهج ≈ 3.7 كتلة | A (القياس) |
| فيديو_HUD (V-001…V-029) | محاكاة وإدخال وقفزات | **120 tick/ث** · 5 خانات إدخال (3,4 = يسار/يمين B؛ 5 = قفز/شحن B) · **الإطلاق عند تحرير الزر (B)** · 116 قفزة في 144.9 ث · متوسط **1.249 ث ≈ 150 tick** بين قفزتين · 48 قفزة/دقيقة | A (قراءة) / B (معنى) |
| فيديو_أزمنة | نقاط عبور وتقدم | إيقاع المرحلة: 8 مقاطع من 16.4 إلى 25.0 ث؛ معدل تقدم 0.51 إلى 1.17 %/ث (تصميم زمن LEVEL_01) | A |
| مجهول (U-01…U-26) | ما لا رقم له + طريقة قياسه | **قائمة TUNE_ME** أدناه | — |
| تقاطع_وتعارض (X-01…X-14) | تعارضات | انظر DECISIONS.md → DEC-022 | — |

## 2) ما استُخرج من PDF (مرتبطًا بالفيزياء والحركة)

| الموضوع | الحقيقة | الدرجة |
|---|---|---|
| نموذج التحكم | لا تحكم مباشر بالسرعة بل **زاوية الميل + توقيت القفز** (ص10، ص19) | B |
| دورة اللعب | توجيه العصا → قفز → طيران/ارتداد → هبوط → (نجاح: ارتفاع جديد / خطأ: سقوط) → توجيه (ص21) | B |
| الشحن | `pogoLoad2` (0.105 ث) ثم `pogoLaunch2` (0.18 ث) يرجّحان **شحنًا ثم إطلاقًا** (ص3) | B |
| أنواع الحركة | قفز، توجيه، قفز مزدوج، ارتداد (فطر/مطاط)، انزلاق (جليد/وحل)، مناطق جاذبية، كتل تظهر/تختفي، عجلات دوارة، منصات متحركة جيبيًا (ص20) | A/B |
| سلوكات المنصات | `moveSine_act` (مسافة+سرعة+إزاحة بالمحاور)، `map3Wheel_act` (دوران 1.5 + تذبذب ±45°)، `toggleBlock_act` (صلب/شبح بالتعزيز)، `mushroom_act` (bounce_light) (ص6) | A |
| العقوبة | لا شاشة خسارة: **السقوط = فقدان الارتفاع**؛ عقوبات زمنية في أوضاع معينة (ص3، ص13) | A/B |
| لا ريسبون/نقاط حفظ | اللعبة مشهورة بغيابها (ص3 — C) | C |

## 3) نموذج الفيزياء المنفَّذ (كود في `game/src/sim/`)

```
Input {tilt∈[-1,1], jumpHeld, boostPressed}
   │
   ├─ GROUNDED/CHARGING: الزاوية المستهدفة = زاوية السطح + tilt·tiltMaxAngle،
   │       تتحرك نحوها بمعدل tiltRateGround·groundControl (تسارع turnSpeed)
   │       الضغط يزيد charge حتى chargeTicksMax، التحرير ⇒ launch
   ├─ AIR: tilt ⇒ معدل دوران tiltRateAir·airControl، يُحفظ الزخم، الجاذبية، maxFallSpeed
   └─ SLIDING: سطح زلق/منحدر شديد (steepSlopeAngle) ⇒ انزلاق باحتكاك السطح
Collision: 3 دوائر (طرف العصا r=0.14، جذع r=0.5، رأس r=0.38) × مضلعات محدبة
   tip: مسطّح ⇒ plant/bounce ، زاوية سيئة ⇒ deflect ، حائط/سقف ⇒ wallRestitution
   body: ارتطام ⇒ wallRestitution + energyLoss + ركلة زاوية
Launch: v = d·speed·surface.velocityMultiplier + vplatform + vslide·launchMomentumRetain
```

- **القياس:** متر، ثانية، زاوية بالراديان داخليًا (الدرجات في الإعدادات). `y` للأعلى.
- **الزمن:** `TICK_RATE = 120` (A من الفيديو)؛ كل ما هو «ticks» معرَّف بهذه الوحدة (V-011: التيك 8.33ms).
- **الحتمية:** كل tick يُكمَّم موضعه وسرعته بـ 1/1024 (A: 22.10). اختبار: تشغيل نفس سلسلة الإدخال مرتين → نفس الحالة بت-ببت.
- **منع النفق (tunneling):** تقسيم الخطوة إلى 1..4 خطوات فرعية بحيث لا تتجاوز الحركة 0.12 م.

### الأسطح (Phase 6)
| السطح | friction | restitution | velocityMultiplier | الملاحظة |
|---|---|---|---|---|
| normal | 1.0 | = floorRestitution | 1.0 | العشب/الصخر |
| slope | (حسب الزاوية) | — | — | **مُشتق من الهندسة**: إن كان الميل ≤ steepSlopeAngle قابل للوقوف |
| wall / ceiling | 0.6 | = wallRestitution | — | مُشتق من اتجاه العمود |
| moving | — | — | — | الجسم يتحرك جيبيًا؛ اللاعب يرث سرعته |
| bounce | 0.8 | 1.05 (+ سرعة دنيا) | — | ارتداد تلقائي يمكن توجيهه بالزاوية (≤ bounceAimMax) |
| slippery (جليد) | 0.04 | = floorRestitution | 1.0 | يحفظ الزخم؛ القفز منه ممكن |
| sticky (وحل/نسغ) | 2.5 | 0 | 0.82 | يمتص الزخم ويُضعف القفزة |
| hazard | — | — | — | لمسة ⇒ حدث + إعادة للمنصة الآمنة الأخيرة |
| boost | 1.0 | — | — | دفعة على اتجاه السطح (`TYPE_BOOSTJUICE`: A وجود) |
| goal | — | — | — | تجاوز خط النهاية (`startFinish`: A/B) |

## 4) المعاملات (TUNE_ME) — **مولَّد آليًا من `PhysicsConfig.ts`**

<!-- PARAMS:BEGIN -->
> Generated (72 parameters): LOCKED_A=48 · LOCKED_AB=18 · SUPPLIED=4 · DESIGN=2. There is no TUNE_ME and no estimated (class C) value.
> `LOCKED_*` values come from `Pogostuck_Physics_LOCKED_SPEC.md` exactly as the compiled game stores them. Grade = confidence of the *meaning* (A confirmed, B inferred with named engine semantics, D = supplied by this project, not an original value).

### time

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `tickRate` | 120 | tick/s | LOCKED_AB | B | T02 | Fixed 120 Hz loop = the original default frame cap (fps_max 120, A). One physics update per tick; fixed-step loop is our implementation choice. |
| `ticksPerSecond` | 16 | T/s | LOCKED_AB | B | — | Spec §2: Δt = 16 · Δt_real · timeFactor (B: engine time unit). |
| `timeFactor` | 0.9501953125 | × | LOCKED_AB | B | T01 | Written every play frame as var 973/1024 = 0.9501953125 (source literal 0.95). That it scales time_step is engine semantics (B). |
| `qPerMetre` | 52 | Q/m | LOCKED_A | A | U01 | HUD display unit (52 Q = 1 m). Metres-per-quant is a free presentation choice (spec §7); we adopt the original display unit. |

### air

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `gravity` | 8.5 | Q/T² | LOCKED_A | A | P01 | E1: v_z ← v_z − 8.5·Δt |
| `airDrag` | 0.05000000074505806 | 1/T | LOCKED_A | A | P02 | E1: v_x ← v_x − 0.05·v_x·Δt (stored double of the literal 0.05); no vertical drag. |
| `maxSpeed` | 300 | Q/T | LOCKED_A | A | P09 | E2: if /v/ > 300 then v ← 300·v//v/ |
| `slideZGain` | 4 | × | LOCKED_A | A | P11 | E3: d_z = (v_z + 4·s_z)·Δt |

### rotation

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `turnTarget` | 32 | deg/T | LOCKED_A | A | P16 | E4: ω_t = 32·(u_left − u_right) |
| `turnResponse` | 0.5249999761581421 | 1/T | LOCKED_A | A | P17 | E4: ω += (ω_t − ω)·0.525·Δt/(1+√J) |
| `groundTurnDivisor` | 2 | × | LOCKED_A | A | P15 | E4: Lb = 1/(1 + 2g) → 1 in the air, 1/3 on the ground |
| `rotationHullLift` | 16 | Q | LOCKED_A | A | P18 | E18: hull bottom is raised 16 Q while the rotation is collision-checked. |

### ground

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `groundPressure` | 24 | Q/T | LOCKED_AB | B | P14 | E5: v ← −24·n (magnitude A; direction "into the surface" needs the tilt convention, B). |
| `tipPivotLimit` | 256 | Q | LOCKED_A | A | P19 | E5: pivot correction applied if /Δtip_x/ < 256 and /Δtip_z/ < 256. |
| `probeExtension` | 6 | Q | LOCKED_A | A | P20 | E6: end = tip + (6 + /s_z/)·û_stick |
| `probeHalfSize` | 4 | Q | LOCKED_AB | B | P22 | E6: ±4 Q box trace (box semantics of c_trace: B). |

### charge

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `impactExponent` | 0.925000011920929 | — | LOCKED_A | A | P23 | E7: I = 1.65·/v/^0.925 |
| `impactGain` | 1.649999976158142 | L/(Q/T)^0.925 | LOCKED_A | A | P24 | E7 |
| `impactBoostBonus` | 20 | L | LOCKED_A | A | P25a | E7: + 20·p inside the L_max clamp |
| `loadMaxFloor` | 95 | L | LOCKED_A | A | P25 | E7: lower clamp bound 95 + 25·p |
| `loadMaxFloorBoostBonus` | 25 | L | LOCKED_A | A | P26 | E7 |
| `loadMaxCap` | 300 | L | LOCKED_A | A | P27 | E7: upper clamp bound |
| `loadMinExponent` | 0.8999999761581421 | — | LOCKED_A | A | P28 | E7: L_min = max(40, I^0.9) |
| `loadMinFloor` | 40 | L | LOCKED_A | A | P29 | E7 |
| `chargeRate` | 16 | L/T | LOCKED_A | A | P32 | E8: L ← min(L + 16·Δt, L_max) |
| `minLaunchLoad` | 2 | L | LOCKED_A | A | P33 | E8: launch only if L > 2 |

### launch

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `launchSpeedPerLoad` | 0.7423499822616577 | (Q/T)/L | LOCKED_A | A | P34 | E9: /V/ = 0.74235·L |
| `normalAngleOffset` | 90 | deg | LOCKED_AB | B | P35 | E9: δ = clamp(wrap180(θ_n − 90 − θ), ±45) (ang() wrap: B) |
| `normalBlendClamp` | 45 | deg | LOCKED_A | A | P36 | E9 |
| `normalBlendFactor` | 0.1875 | — | LOCKED_A | A | P37 | E9: a = θ + 0.1875·δ |
| `launchSlideCarryX` | 0.25 | — | LOCKED_A | A | P38 | E9: v_x = V_x + 0.25·s_x |
| `launchSlideCarryZ` | 0 | — | LOCKED_A | A | P39 | E9: v_z = V_z + 0·s_z |
| `launchSpinPerTilt` | 0.12449999898672104 | (deg/T)/deg | LOCKED_A | A | P40 | E10 |
| `launchSpinTiltClamp` | 45 | deg | LOCKED_A | A | P40b | E10 |
| `slopeSpinExponent` | 0.75 | — | LOCKED_A | A | P41 | E10, E14: /σ/^0.75 |
| `slopeSpinGain` | 1.5 | — | LOCKED_A | A | P42 | E10, E14 |
| `slopeSpinLaunchFactor` | 0.25 | — | LOCKED_A | A | P43 | E10 |
| `noGroundTime` | 2 | T | LOCKED_A | A | P44 | E11, E14: N ← 2 T after a launch or wall bounce |
| `jumpTimerGain` | 0.44999998807907104 | T/√L | LOCKED_A | A | P45 | E11: J ← 0.45·√L_max |

### boost

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `boostRotation` | 285 | deg | LOCKED_A | A | P46 | E13: airborne int(/θ − θ_j/) > 285 ⇒ p ← 1 |

### bounce

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `bounceReflect` | -2 | — | LOCKED_A | A | P47 | E14 fallback: r = v − 2(v·n)n |
| `bounceDirWeight` | 0.8999999761581421 | — | LOCKED_AB | B | P48 | E14: d = 0.9·r̂ + n (r̂ = engine `bounce` vector in the original: B) |
| `bounceSpeedFactor` | 0.4000000059604645 | — | LOCKED_A | A | P49 | E14: S = max(28, 0.4·/v/)·min(1+n_z, 1) |
| `bounceMinSpeed` | 28 | Q/T | LOCKED_A | A | P50 | E14 |
| `bounceSlopeCap` | 1 | — | LOCKED_A | A | P73 | E14: min(1 + n_z, 1) |
| `bounceXScale` | 0.875 | — | LOCKED_A | A | P51 | E14: v_x = 0.875·b_x + s_x |
| `bounceSpin` | 0.5 | (deg/T)/deg | LOCKED_A | A | P52 | E14: ω = 0.5·wrap180(γ − θ) − … |
| `slopeSpinBounceFactor` | 0.20000000298023224 | — | LOCKED_A | A | P53 | E14: … − 0.2·1.5·sgn(σ)/σ/^0.75 |

### slide

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `slideTargetSpeed` | 48 | Q/T | LOCKED_AB | B | P55 | E15: t = 48·normalize(n + R_γ(0,−1)) (direction needs normalize/rotate semantics: B) |
| `slideResponse` | 0.25 | 1/T | LOCKED_A | A | P57 | E15: s += clamp(0.25·(t − s), ±1.35)·Δt |
| `slideAccelClamp` | 1.350000023841858 | Q/T² | LOCKED_A | A | P58 | E15 |
| `slideDecay` | 0.5 | 1/T | LOCKED_AB | B | P59 | E15: s ← s·(1 − 0.5·Δt) out of slide mode (vec_lerp semantics: B) |
| `slideStop` | 0.25 | Q/T | LOCKED_A | A | P72 | E15: /s/ < 0.25 ∨ airborne ⇒ s ← 0 |
| `platformDownReduction` | 0.75 | — | LOCKED_A | A | P61 | E17: v_z += c_z·(1 − 0.75·[c_z < 0]) |
| `ledgeExitPop` | 5 | Q/T | LOCKED_A | A | P71 | E16: v ← (s_x, 5) |

### hull

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `hullHalfX` | 12.5 | Q | LOCKED_AB | B | P62 | E18: x ∈ [−12.5, 12.5] (use as collision box: B) |
| `hullMaxZ` | 30 | Q | LOCKED_AB | B | P64 | E18: z_max = 30 |
| `hullMinZBase` | -55 | Q | LOCKED_AB | B | P65 | E18: z_min = −55 + max(X, −12.25) |
| `hullExtLimit` | -12.25 | Q | LOCKED_AB | B | P66 | E18 |
| `springRelaxRate` | 120 | L/T | LOCKED_A | A | P67 | E18: airborne P_b ← max(P_b − 120·Δt, −200) |
| `springRelaxFloor` | -200 | L | LOCKED_A | A | P67b | E18 |
| `springExtAmp` | 18 | Q | LOCKED_AB | B | P68 | E18: X = 18·sin(0.9·P_b) (sinv in degrees: B) |
| `springExtSineGain` | 0.8999999761581421 | — | LOCKED_AB | B | P68a | E18: sin(0.9·P_b); literal verified at IMG 0x3eadb6 |
| `springExtNegScale` | 0.009999999776482582 | — | LOCKED_AB | B | P68b | E18: X<0 ⇒ X·(0.01·P_max)·(200+X)·0.0025; literal verified at IMG 0x3eaf21 |
| `springExtNegOffset` | 200 | — | LOCKED_AB | B | P68c | E18; literal verified at IMG 0x3eafc3 |
| `springExtNegGain` | 0.0024999999441206455 | — | LOCKED_AB | B | P68d | E18; literal verified at IMG 0x3eaf60 |

### supplied

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `tipLength` | 55 | Q | SUPPLIED | D | — | Our own pogo geometry: equals the hull base so the tip end meets the hull bottom when upright. Original geometry lives in model files (D). |
| `tipRadius` | 4 | Q | SUPPLIED | D | — | Our collision shape for the tip; reuses the probe half-size (P22). |
| `collisionStep` | 2 | Q | SUPPLIED | D | — | Numerical: maximum travel per collision sub-step (anti-tunnelling). |
| `hullSupportsFloor` | 0 | bool | SUPPLIED | D | — | 0 = floor-like hull contacts (n_z > 0.5) are ignored; the tip supports the body (E5/E6). |

### presentation

| Parameter | Value | Unit | Status | Grade | Spec row | Note |
|---|---|---|---|---|---|---|
| `hardImpactLoad` | 150 | L | DESIGN | D | — | Feedback only: landing impulse I at which audio/haptics/camera treat a landing as hard. |
| `safeLandingRecord` | 1 | bool | DESIGN | D | — | The pogo hops continuously, so the safe respawn spot is stored at each landing on a safe platform (instead of after standing still). |
<!-- PARAMS:END -->

## 5) خطة القياس لتحويل TUNE_ME → C/A (من ورقة «مجهول»)
1. **الأولوية 1 (لا تحتاج تسجيلًا جديدًا):** قراءة مدد بقاء الخانة 5 = 1 في الفيديو → `chargeTicksMax` (U-05) بدقة ±4 tick.
2. **الأولوية 1 (تحتاج مرجع مسافة):** تسجيل 60fps مقابل جسم معروف (كتلة 128): `gravity` (U-01)، `launchSpeedMin/Max` (U-03/04).
3. **الأولوية 2:** معدلات الميل `tiltRate*` و`tiltMaxAngle` (U-06..09)، وحجم اللاعب (U-16).
4. **الأولوية 3:** الارتداد والاحتكاك والمنحدرات (U-10..14).
5. بعد كل قياس: عدّل القيمة في `PhysicsConfig.ts`، غيّر `status` من `TUNE_ME` إلى `MEASURED_C` واذكر المصدر، وأعد تشغيل Lab وسيناريوهات الحل (tests/solvability).

## 6) أدوات المقارنة داخل اللعبة
- **Physics Lab** (`?lab=1` أو من القائمة): عرض الجاذبية، السرعات، الزاوية، قوة القفز، نقطة التماس والعمود، حالة Boost، الارتفاع... + أزرار Reset / Test Jump / Test Bounce / Test Boost / Test Fall + منزلقات حيّة لكل TUNE_ME.
- **مؤشر الإيقاع:** يقيس متوسط ticks بين قفزتين ويقارنه بـ 150 tick (A مشتق من V-026).
