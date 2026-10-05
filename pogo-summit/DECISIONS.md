# DECISIONS.md — سجل القرارات

> كل قرار هنا له: السياق، المصدر (XLSX/PDF/الصورة + درجة الثقة)، القرار، البدائل المرفوضة.
> درجات الثقة: **A** مؤكد · **B** استنتاج قوي · **C** تقدير/قياس من Gameplay · **D** مجهول · **TUNE_ME** = لا رقم موثّق، قيمة بداية عملية مؤقتة من عندنا.

---

## قرارات المشروع والبنية

### DEC-001 — المشروع في مجلد مستقل `pogo-summit/`
- **السياق:** المستودع يحتوي مشروعًا سابقًا مختلفًا تمامًا (ClipFlow: مدير حافظة، Kotlin/Compose). الطلب: «مشروع جديد من الصفر، ليس تعديلًا للسابق».
- **القرار:** مجلد جديد `pogo-summit/` بإعدادات Gradle وnpm خاصة به. لم يُعدَّل أي ملف من المشروع السابق ولا يعتمد عليه.
- **مرفوض:** حذف المشروع السابق (إتلاف لعمل المستخدم دون طلب صريح).

### DEC-002 — التقنية: نواة TypeScript + Three.js داخل غلاف Android أصلي (WebView)
- **السياق (قيود بيئة فعلية، تم فحصها):**
  - لا Unity ولا Godot ولا Android SDK في البيئة. `github.com` (إصدارات Godot) → 403. `dl.google.com` (Google Maven + مستودع SDK) → **403 بسياسة المنظمة**، أي لا يمكن تشغيل Android Gradle Plugin هنا.
  - ما هو متاح: Java 21, Gradle 8.14, Node 22, Maven Central, npm, apt (Ubuntu)، وChromium (Playwright) يدعم WebGL2 عبر SwiftShader.
  - الطلب نفسه يشترط: «لا تقل تم بدون Build وتشغيل فعلي» و«Visual QA بمقارنة لقطات مع الصورة المرجعية».
- **القرار:** نواة اللعبة بـ TypeScript، العرض بـ Three.js (WebGL2)، والغلاف Android أصلي بـ Java (Activity + WebView + جسر JS للاهتزاز والحفظ). هذا هو المسار الوحيد الذي يسمح بـ **تشغيل اللعبة فعليًا وأخذ لقطات وقياسها** في هذه البيئة.
- **تخفيف المخاطر:**
  - مجلد `game/src/sim` **نقي**: بلا DOM ولا Three.js ولا زمن حقيقي. قابل للنقل إلى Godot/Unity/Kotlin بإعادة كتابة آلية، ومغطى باختبارات.
  - كل الأصول **إجرائية** (هندسة، خامات، صوت) — لا ملفات خارجية، فلا مشكلة حقوق ولا حجم.
- **ثمن القرار (بشفافية):** أداء WebView أقل من محرك أصلي على الأجهزة الضعيفة جدًا؛ لم يُقَس على جهاز حقيقي (انظر DEC-015).
- **مرفوض:** (أ) Kotlin + OpenGL ES خام: لا يمكن اختباره بصريًا هنا إطلاقًا. (ب) Godot/Unity: غير قابلين للتثبيت هنا.

### DEC-003 — سلسلة بناء APK مزدوجة
- **مسار 1 (يعمل هنا، تم التحقق منه):** `tools/build-apk.sh` يستخدم أدوات Ubuntu (`aapt`, `dalvik-exchange`, `apksigner`, `zipalign`, `android-23.jar`) لإنتاج **APK debug موقّع (v2/v3)**. minSdk 24، targetSdk 34 في الـ manifest.
- **مسار 2 (للإطلاق، غير قابل للتشغيل هنا):** مشروع Gradle قياسي `android/` (AGP + compileSdk 35) لإنتاج **AAB** وAPK release. يحتاج `dl.google.com` أو جهاز مطوّر عليه Android Studio. **لم يُبنَ هنا → غير مُتحقَّق منه.**
- الكود الأصلي مكتوب ليُترجم على المسارين (Java 8، reflection لواجهات API ≥ 26).

### DEC-004 — جذر الواجهة: HTML/CSS فوق Canvas
- HUD والقوائم عناصر DOM (حادّة، RTL، safe-area عبر `env()`)، والمشهد ثلاثي الأبعاد في Canvas واحد. لا خطوط/صور خارجية.

---

## قرارات الفيزياء (تفاصيل في PHYSICS_MASTER.md)

### DEC-010 — خطوة محاكاة ثابتة 120 Hz
- **المصدر:** XLSX V-001/V-002/V-003 (**A** — من لوحة تصحيح الفيديو).
- **القرار:** `TICK_RATE = 120`. العرض بالاستيفاء بين حالتين، فيعمل على 60/90/120 Hz دون تغيير السلوك.
- **ملاحظة:** الفيديو يثبت معدل **اللعبة الأصلية**؛ تبنّيه قرار تصميم عندنا لا ادعاء أنه «قيمة الفيزياء الأصلية».

### DEC-011 — تكميم الحالة بخطوة 1/1024
- **المصدر:** XLSX F-003/F-004/F-005 (**A**): المحرك الأصلي يستخدم ثابتة الفاصلة 22.10.
- **القرار:** خيار `fixedPointStep = 1/1024` يُطبَّق على الموضع والسرعة في نهاية كل tick لضمان **حتمية** إعادة التشغيل (replay) واختبار «نفس الإدخال → نفس الحالة». لا يُدّعى أنه يطابق معادلات اللعبة الأصلية.

### DEC-012 — كل معاملات الحركة = `TUNE_ME`
- **المصدر:** XLSX «اقرأني» + «مجهول» U-01…U-26؛ PDF ص9/ص26: لا رقم فيزيائي في الملفات (overlay مشفّر، entropy 7.82).
- **القرار:** كل معامل في `PhysicsConfig` يحمل `status: 'TUNE_ME'` ويُعرض كذلك في Physics Lab. القيمة العددية الحالية **قيمة بداية عملية من عندنا** لجعل اللعبة قابلة للعب، **وليست** قيمة Pogostuck الأصلية (التي لا يعرفها أحد من المصادر).
- **هدف ضبط موثّق (A مشتق من الفيديو):** متوسط الفاصل بين قفزتين = **1.249 ث ≈ 150 tick** (V-025/V-026، 116 قفزة في 144.9 ث). نستخدمه مؤشر إحساس: دورة القفز النموذجية (شحن + طيران + هبوط) عندنا تُضبط حول هذا الرقم، ويظهر في Lab.
- **الأولوية 1 في «مجهول» (قياس مدد الخانة 5 لزمن الشحن):** غير منفَّذة بعد → `chargeTicksMax` يبقى TUNE_ME.

### DEC-013 — نموذج الحركة: «زاوية + توقيت» لا «سرعة مباشرة»
- **المصدر:** F-011 (**B**)، PDF ص10: اللاعب لا يتحكم بالسرعة بل بزاوية الميل وتوقيت القفز.
- **القرار:** المُدخل يضبط **زاوية العصا** (أرضًا: زاوية مستهدفة تتحرك نحوها بمعدل محدود؛ جوًّا: معدل دوران)، **الضغط المستمر يشحن**، **ترك اللمس يطلق** القفزة (V-019، **B**: الإطلاق عند تحرير الزر). لا شيء من نوع «يمين = سرعة يمين».
- **تفاصيل التنفيذ (تصميم خاص بنا):** جسيم عند مركز الكتلة + دوران يتحكم به المدخل + ثلاث دوائر تصادم (طرف العصا، الجذع، الرأس). السبب: متوقَّع الاستقرار وقابل للاختبار، ولا يعلق اللاعب أبدًا (يمكنه دائمًا تدوير العصا).

### DEC-014 — نظام Boost (تصميم خاص — الآلية الأصلية D)
- **المصدر:** F-016 (**A** وجود فقط: وضعا Double jump و720° boost)، U-25 (**C**): الآلية غير معروفة.
- **القرار:** تدوير العصا جوًّا بسرعة زاوية ≥ `boostThreshold` يُراكم زاوية؛ عند بلوغ `boostRotation` يصبح Boost «جاهزًا» (زر Boost يتوهج كما في الصورة المرجعية)، والضغط عليه يعطي دفعة `boostPower` على محور العصا (جوًّا فورًا، أو أرضًا عند الإطلاق التالي). كل الأرقام TUNE_ME.

### DEC-015 — نمط التحكم: DRAG (افتراضي) + PAD (مطابق للصورة المرجعية)
- **التعارض:** نص Phase 3 يصف لمسة واحدة (Down=شحن، Drag=اتجاه/قوة، Release=إطلاق)، بينما الصورة المرجعية تعرض عصا افتراضية + زر Jump/Charge + زر Boost.
- **القرار:** الاثنان مدعومان فوق نفس مُدخلات مجردة `{tilt, jumpHeld, boostPressed}`. **الافتراضي = DRAG** (نص الطلب الصريح)، و**PAD** خيار في Options (مطابق للصورة). لا يتغير شيء في الفيزياء بين النمطين.
- لوحة المفاتيح موجودة فقط في وضع `?debug=1` للاختبار الآلي، وليست جزءًا من التحكم النهائي.

---

## قرارات المراحل والبصريات

### DEC-020 — LEVEL_01 مصممة بقياسات إيقاع الفيديو + مختبرة آليًا
- **المصدر:** XLSX فيديو_أزمنة (A): الخريطة 3 = 146.075 ث و116 قفزة؛ PDF: تصاعد صعوبة (تعليم → عناصر جديدة).
- **القرار:** LEVEL_01 تُصمَّم للتعليم لا للنسخ: لا أي نسخ لخرائط Pogostuck. قابليتها للحل تُثبت **آليًا** بروبوت يجرّب شبكة (زاوية × شحن) على الفيزياء الفعلية (انظر tests/solvability).

### DEC-021 — لا إسقاط لمواضع/أبعاد Pogostuck الأصلية
- **المصدر:** X-05, X-12, X-14 في «تقاطع_وتعارض».
- **القرار:** الأبعاد في ملفات_أبعاد (مثل `woodenBlock1 = 128`) مرجع **نسب** فقط. وحدتنا هي المتر مع محور Y للأعلى بالتعريف (لا نفترض شيئًا عن Z الأصلية — X-14).

### DEC-022 — تعارضات PDF الداخلية وكيف حُسمت
| المعرّف | التعارض | ما اعتُمد | الأثر علينا |
|---|---|---|---|
| X-10 | مدة iceSlide: 1.40 ث (ص8) مقابل «لم تُقس» (ص15) | 1.40 ث كمدة ملف صوت فقط | لا أثر فيزيائي؛ صوت الانزلاق عندنا إجرائي |
| X-11 | pogoLoad/Launch مقيسة (ص3) أم لا (ص15) | النسختان 2 فقط (0.105/0.18 ث) | مرجع توقيت صوت الشحن/الإطلاق (يُنسخ **التوقيت** لا الملف) |
| X-12 | نطاق −13145…16768 للمرحلة 11 أم main | غير محسوم | لا يُستخدم |
| X-13 | شبكة 161×256 والعمود 244 | 256 عرضًا × 161 ارتفاعًا (B) | شبكة التقدم عندنا مسار خطي أبسط |
| X-14 | أي محور رأسي | غير مؤكد (C) | تعريفنا: Y لأعلى |

### DEC-023 — شخصية أصلية
- الصورة المرجعية تعرض طاهيًا. التعليمات: «لا تنسخ شخصياتها». شخصيتنا أصلية: متسلق صغير برأس كبير (Chibi)، قبعة صوفية صفراء بكرة حمراء، وشاح، معطف تركوازي (يتباين مع بيئة الخريف الحمراء/الخضراء). عصا البوغو برتقالية/نحاسية.

### DEC-024 — «Look & Feel» من الصورة (قياس موضوعي)
- لوحة الألوان والتركيب والأنواع الستة للمنصات والعوالم الأربعة والـHUD مأخوذة من الصورة (VISUAL_DIRECTION.md). كل ما فيها «لغة بصرية» وليس نسخًا: لا أصول ولا شخصيات من الصورة.

---

## قرارات النظام

### DEC-030 — الصوت إجرائي بالكامل (WebAudio)
- الطلب: لا صوت Pogostuck الأصلي. نولّد SFX وموسيقى وأجواء برمجيًا (مفاتيح خماسية، ضوضاء مرشّحة). مرجع **التوقيت** فقط من PDF ص15 (مثل: boostBanana 0.62 ث، monoBulletTime 4.26 ث؛ مدد ملفات لا مدد أحداث فيزيائية).

### DEC-031 — الاهتزاز عبر جسر أصلي
- `HapticManager`: أحداث + Intensity/Duration/Cooldown، لا اهتزاز كل إطار. على Android عبر `Vibrator`/`VibrationEffect` (reflection)، وعلى المتصفح `navigator.vibrate`. PDF ص16: «متى يحدث الاهتزاز في الأصل: مجهول» → نصمم التوقيت بأنفسنا.

### DEC-032 — بلا إعلانات ولا مشتريات في هذا الإصدار
- PDF ص3/ص18: لا SDK إعلاني ولا عملة مدفوعة في الأصل (**A/B**). عندنا: لا إنترنت أصلًا (لا صلاحية INTERNET) → نموذج «Data safety: لا جمع بيانات».
- **لوحة المتصدرين محلية فقط** (أفضل الأزمنة على الجهاز). متصدّرون عالميون = خارج النطاق الحالي (يحتاج خادمًا).

### DEC-033 — الحفظ
- مخطط JSON بنسخة (`schemaVersion`) مع ترحيل. التخزين الأساسي SharedPreferences عبر الجسر، الاحتياطي `localStorage`. كل قراءة/كتابة محاطة بـ try/catch.

### DEC-034 — «ممر السماء» وحجم زاوي أقصى للخلفيات
- **المشكلة (قياس):** سماء مرئية 7–14% من النصف العلوي مقابل 33% في المرجع؛ الجزر والمعالم القريبة تغطي الشاشة (FOV الكاميرا 30° فقط).
- **القرار:** مولّد العالم العائم يرفض أي جزيرة/عمود يقطع النطاق [camY+0.2H, camY+1.0H] عند أي منصة (H = نصف ارتفاع الإطار عند عمق الجسم)، ويحدّ حجمها الزاوي (≤ ~45% من ارتفاع الإطار). المعالم تُوضع يدويًا بنفس القاعدة.
- **النتيجة:** سماء 14–50% وسحاب 10–21% (المرجع 33%/12%).

### DEC-035 — تدرّج السماء يُمدَّد لـFOV الضيق + منظور جوي مخبوز للجبال
- زاوية الارتفاع المرئية ±15° فقط ⇒ كان اللون العلوي لا يظهر أبدًا. نضرب الارتفاع ×3.4 (+ ازدياد خفيف مع الارتفاع) في الـshader.
- الجبال: `MeshBasicMaterial` بلا ضباب المشهد؛ التضبيب يُخبز في ألوان الرؤوس لكل سلسلة (0.1/0.3/0.5) فتبقى زرقاء مشبعة بدل أن تبيضّ.

### DEC-036 — قياس بصري موضوعي بدل الانطباع
- `tools/qa-compare.py` يحسب السطوع/التشبّع/نسبة السماء/السحاب/الدافئ/الأخضر/كثافة الحواف + k-means للون من المرجع واللقطات. هو حارس للاتجاه الفني لا «درجة تشابه». الفجوة المتبقية الموثقة: كثافة التفاصيل (0.04 مقابل 0.095).

### DEC-037 — معاينة العوالم بدل ادّعاء مراحل لها
- العوالم 2–4 تُعاين بـ`?debug=1&autostart=1&world=world_2…` على LEVEL_01 بثيمها. هذا يتحقق من الثيم فقط؛ **لا توجد مراحل للعوالم 2–4 بعد** (مذكور في خطة التنفيذ).

### DEC-038 — Visual upgrade: surface detail without texture assets
- **Problem (user QA screenshots):** flat vertex colour over large faces made platforms and cliffs read as "cubes with a material".
- **Decision:** one procedural detail texture, 256² RGBA (rock cracks / grass / grain / cavity), generated at start-up and sampled **triplanar in world space** by the terrain material (`render/surface.ts`). Mipmaps are on and detail fades with distance. The surface type (natural / foliage / wood / plain) lives in **vertex-colour alpha**, so a merged mesh carries several looks in one draw call. The player keeps a clean material with its own rim light (`mats.char`), so it separates from the background.
- **Cost:** 1 extra texture of about 340 KB; no extra draw calls.

### DEC-039 — Organic shape kit instead of BoxGeometry
- `render/shapes.ts`: `roundedBlock` (rounded edges + welded vertices + smooth normals + free deformation), `smoothBlob`, `rockColumn`. Every platform, rock, cliff, island, support and castle spire is built from it.
- **Collision rule unchanged:** gameplay-facing faces only ever move **inward** and the walkable top stays exactly at y = 0, so the visuals never leave the collider silhouette. Physics files untouched (verified: route/determinism tests unchanged).

### DEC-040 — A distinct identity per platform type
- Rock = grass cap with a draped edge, moss drips, terraced body · wood = bevelled planks, nails, rope trim, posts (static) / iron hooks + link chains (moving) · ice = faceted glossy crystal with its own material (`mats.ice`) · bounce = cushion with a rim and dots on twin helix springs and a pedestal · hazard = per world (red crystals / blue ice spikes / bronze spears / glowing lava shards) · goal = plinth + flag + light pillar.

### DEC-041 — Real variety between worlds (not just recolours)
- Hero landmark: storybook castle (W1), blue-roofed frost castle (W2), ruined temple on a sandstone spire (W3), volcano with lava rivers and smoke (W4).
- Bridge: stone / snow-capped with icicles / collapsed ruin / basalt.
- Vegetation: autumn + pine (W1), snowy pine (W2), cypress (W3), bare dead trees (W4).
- Platform decor: flowers / snow lumps + ice crystals / dry grass + broken column stumps / obsidian shards.
- Waterfall flow: nearly frozen in W2, slow lava in W4.
- W4 was too dark; it was brightened (sun 2.1, hemi 1.3, exposure 1.12) while keeping its dark-red identity.

### DEC-042 — Mobile budget after the upgrade
- Merging the character (≈ 20 draw calls instead of ≈ 60) and reducing tessellation (rounded steps 0.6–1.35 m; one-piece flowers; foliage detail 1) brought it to **≈ 118 draw calls and ≈ 115k rendered triangles** (including the shadow pass), within the < 250 / < 150k budget (measured with `renderer.info` in headless Chromium).

### DEC-043 — Bundled HUD font
- Baloo 2 (Latin) + Baloo Bhaijaan 2 (Arabic), **SIL OFL 1.1** (license copied with the files into `fonts/`); ≈ 80 KB woff2 for 4 files. No network at runtime (the app has no INTERNET permission). Added `font/woff2` to the shell's `ShellLogic.mime` + a JUnit test.

### DEC-044 — Locked-spec physics replaces the earlier hand-tuned model
- **السياق:** طلب IMPLEMENT: تطبيق `Pogostuck_Physics_LOCKED_SPEC.md` وحده.
- **القرار:** حُذفت `JumpSystem/BoostSystem/CollisionResponse` وكل قيم TUNE_ME/C؛ الفيزياء الآن في `src/sim/core/*` (E1–E18)، والثوابت في `PhysicsConfig.ts` بقيمها المخزّنة (مثل 0.7423499822616577). لا يوجد زر boost ولا إيماءة سحب-للقوة ولا coyote/press-buffer ولا restitution، لأن المواصفة لا تحتوي أيًّا منها.
- **الأثر:** الإعدادات `swipeStrength` و`chargeTime` بلا تأثير (تبقى فقط لتوافق الحفظ القديم).

### DEC-045 — Our own collision (spec §7 class D), kept separate from the locked equations
- الحركة مع الاصطدام، فحص الدوران، والمسبار هي تنفيذنا (`core/collision.ts`): دائرة طرف r=4 Q + صندوق هيكل موجّه (±12.5 عرضًا، z_min…+30 طولًا) يتبع اتجاه العصا؛ تُتجاهل التلامسات الأرضية للطرف السفلي من الهيكل لأن الطرف يحمل الجسم (E5/E6).
- اتجاه الارتداد r̂ يُحسب من الإزاحة المنعكسة عن السطح (بديل لـ `bounce` الخاص بالمحرك الأصلي)، وحمل المنصات المتحركة (c) من فرق موضع المنصة.
- لم تُنسخ أي شيفرة أصلية. القيم المقفلة لم تُغيَّر لتناسب هذا التنفيذ.

### DEC-046 — Unit presentation: 52 Q = 1 m, Δt = 0.126693 T at a fixed 120 Hz
- من §2 و§7 للمواصفة؛ الحالة تُخزَّن بـ Q/T/درجات (double) ويُشتق منها المتر وم/ث والراديان لكل tick لأجل العرض فقط.

### DEC-047 — LEVEL_01 geometry unchanged; two tight hops documented
- المنصات لم تُعدَّل. التحليل (شبكة زاوية×حِمل، نافذة محافظة L∈[40,95]) والروبوت الكامل (`routeBot`) يثبتان أن الهدف قابل للوصول، لكن q1→q2 (2 خلايا من 348) وq2→q3 (4) ضيقتان. القرار: الإبلاغ عنهما بدل تغيير الفيزياء أو المستوى في هذه المهمة.
- `pad1` (منصة الارتداد) صارت سطحًا عاديًا لأن المواصفة تستبعد ارتدادات الكيانات (E01–E02، §8).

### DEC-048 — Safe respawn recorded at landing
- `safeLandingRecord` (DESIGN): تسجيل نقطة إعادة الظهور عند الهبوط على سطح آمن — ليس جزءًا من المواصفة وليس قيمة أصلية.

### DEC-049 — Map System V2 يعيش خارج نواة الفيزياء
- **السياق:** طلب بناء نظام خرائط جديد مستوحى من دراسة `CustomMaps.zip` دون نسخ شيفرة أو خرائط أو أصول Pogostuck.
- **القرار:** كل الشيفرة في `game/src/map/*`؛ `MapWorld extends PhysicsWorld` ولا يعدّل `src/sim/core/*` ولا `PhysicsConfig` ولا `Pogostuck_Physics_LOCKED_SPEC.md`. الاتصال بالفيزياء عبر أربعة مقابس فقط: بناء المصادمات، تفعيل/تعطيل المصادمات وإزاحتها كدوال نقية من `(tick, counters, flags)`، قراءة `PogoState` بعد كل خطوة، وكتابة وحيدة هي مرساة إعادة الظهور (فئة DESIGN) + `respawn()` الخاص بالنواة. يُفرض ذلك باختبار (`tests/map/boundary.test.ts`).

### DEC-050 — صيغة `map.json` (formatVersion 2) مصدر الحقيقة؛ الباقي مشتق
- أمتار، +x يمين، +y أعلى، ترتيب مفاتيح ثابت، تعابير `=expr` بمحلّل يدوي (لا `eval`)، prefabs كبيانات، سلوكيات كبيانات مكتوبة (move/rotate/toggle/timed/breakable/conditional…)، المرئي منفصل عن التصادم داخل الكيان نفسه. الحزمة `.pogomap` (PGMP) بيانات فقط مع CRC32 وحدود 4096 ملف / 128 MiB.

### DEC-051 — القدرات المُعلنة وغير المطبّقة (تعارضات تكامل مسجّلة، لا تغيير في الفيزياء)
- `boostZone` وسطح `boost`/`bounce`/`sticky` تُعلَن في البيانات وتصدر كأوامر لكنها **لا تُطبَّق** (المواصفة المقفلة لا تحتوي دفعات الكيانات ولا كتابة `boost` خارج E13)؛ المدقّق يُصدر `CAPABILITY_UNAVAILABLE`. المصادمات الدوّارة لا تحمل الراكب (النواة تحمل الإزاحة فقط). أوضاع اللاعب (`doubleJump`/`puzzle`/`grapple`) مرفوضة. السطح أحادي الاتجاه يُنفَّذ في طبقة الخريطة (نشاط المصادم) وللاتجاه `up` فقط.

### DEC-052 — البث بالقطع (chunks) بمرساة آمنة وخانات مصادمات ثابتة
- المناطق/نقاط التفتيش/مناطق النهاية/مسار التقدّم بيانات مقيمة (صغيرة)، والكيانات فقط تُقطَّع. خانات المصادمات ثابتة مع «قبر» بدل الحذف كي لا تتدلّى `groundId/safeGround`. `activateRadius ≥ 32 m` (سرعة قصوى 0.7335 m/tick × 30 tick + هامش). الإثبات: محاكاة متدفقة مقابل محمَّلة بالكامل متطابقة بت-ببت على 6000 tick.

### DEC-053 — الترحيل من `LevelData` بدقة بت-ببت
- `levelDataToMap(LEVEL_01)` ينتج المجموعة نفسها من المصادمات (المعرّفات، الهندسة، الأنواع، الأسطح، الترتيب) ويعيد مسار الروبوت المسجَّل بحالة مطابقة بت-ببت (تحميل كامل ومع بث حقيقي). خريطة المطوّر `first_steps_v2` (`?map=first_steps_v2`) هي هذا الترحيل.

### DEC-054 — المرئي ≠ التصادم ≠ الزينة ≠ الخلفية (Visual V2)
- **السياق:** المطلوب تحويل Map System V2 من نظام بيانات إلى نظام عرض فعلي داخل اللعبة دون المساس بالفيزياء.
- **القرار:** مجسّم الشكل النهائي لا يُشتق أبدًا من مجسّم التصادم. الكيان بلا `visual` يحصل على بديل مُشتق مُنمَّق (`VISUAL_DERIVED` كمعلومة في المدقّق). أربع طبقات عرض (`foreground / gameplay / midground / background`) لكل منها عتبات LOD وإخفاء بعيد وضباب جوي (`LAYER_HAZE`). الزينة والخلفية بلا مصادمات افتراضيًا (مثبت باختبار: عدد المصادمات النشطة لا يتغيّر بإضافة الزينة).

### DEC-055 — وحدات التخطيط نقية في `src/map`، وThree.js في `src/render/map`، والصوت في `src/audio`
- `MapMaterial / MapAssets / MapScatter / MapVisual / MapRenderPlan / MapCamera / MapWarm / themeKit / themesV2` لا تستورد DOM ولا Three.js ولا ساعات ولا `Math.random` (يفرضه `tests/map/boundary.test.ts`)، فيشاركها المدقّق والمصيّر وأداة `stats` ونفس الأرقام تظهر في الثلاثة. التوزيع العشوائي للزينة دالة نقية من (seed, entity) فتتطابق الخريطة على كل جهاز.

### DEC-056 — نموذج التجميع: merged / instanced / single وميزانيات صريحة
- هندسة ثابتة ⇒ شبكة مدموجة واحدة لكل (chunk × مادة × LOD)؛ الزينة المتكررة ⇒ `InstancedMesh` واحد لكل (شبكة × مادة × LOD) مشترك بين كل القطع المحمّلة مع قصّ مخروط الرؤية والمسافة وLOD لكل نسخة؛ الكيانات المتحركة/المشروطة/القابلة للكسر ⇒ شبكات مستقلة. المدقّق يعدّ draw calls (رئيسي + ظلال + خلفية) والمثلثات (مع تمرير الظلال) وذاكرة القوام والمصادمات والجسيمات والأصوات ويصدر WARNING عند تجاوز الميزانية (`android-mid`/`android-low`) و ERROR عند ضعفها.

### DEC-057 — جسر الفيزياء للمواد: `surfaceType` يعطي افتراضات فقط
- `ICE/SLIPPERY→slippery` و`HAZARD/LAVA→hazard` و`BOUNCE` مُعلن غير مطبّق (DEC-051) و`WATER/GOAL` غير صلبين؛ القيمة تُستعمل فقط حين لا يصرّح `collision.surface/hazard`، والمدقّق يحذّر من التعارض. لا ثوابت فيزياء جديدة ولا تعديل لـ`src/sim/core`.

### DEC-058 — تجميع برامج الـshader مسبقًا عند تحميل الخريطة
- القياس: في أول مرور على الخريطة ظهرت 5 «تجمّدات» لإطار واحد (1.5–2.8 ث على SwiftShader) كل واحدة ببرنامج shader جديد. الآن `MapWarm` يسرد (مادة × نوع هندسة) التي تستعملها الخريطة فعلًا و`GameRenderer.loadMapScene` يرسمها مرة واحدة بمقياس 0.002 قبل بدء اللعب؛ بعد ذلك لا يظهر أي برنامج جديد على امتداد الخريطة (32 برنامجًا ثابتة، إطارات 2–23 ms).

### DEC-059 — `CameraProfile` ومناطق الكاميرا
- القيم الافتراضية للملف = ثوابت الكاميرا القديمة حرفيًا (مسافة 19، FOV 30…) فلا يتغيّر أي مستوى قائم؛ مناطق `camera` تدفع/تسحب تعديلات بأولوية ومدّة مزج، والتحويل بين الحالات أُسّي. الكاميرا تحتفظ بإطارها الآمن للاعب مهما كان الملف.

### DEC-060 — لا اختبار على جهاز حقيقي: `DEVICE_TEST = NOT_AVAILABLE`
- بُني APK debug (538 KB، مُوقَّع v2/v3) لكن البيئة بلا `adb` ولا جهاز ولا محاكي. أرقام FPS من SwiftShader ليست ممثّلة لهاتف؛ أرقام draw calls والمثلثات والذاكرة دقيقة (عدّادات `renderer.info`). لا ادّعاء بنجاح اختبار جهاز في أي وثيقة.

### DEC-061 — خريطة `showcase_v2`: مسافات مُعايَرة بالفيزياء لا بالتقدير
- المسافات الأولى (فجوات 6–8 م) أعطت 0–4 خطط هبوط لكل قفزة في بحث الشبكة؛ أُعيد التخطيط بحيث تُحسب مواضع المنصات من فجوات حافة-إلى-حافة ≤ 4.5 م وارتفاعات ≤ 4 م وعروض ≥ 6 م (9–17 خطة وهامش 2–3.7 م لمعظم القفزات). الزينة والمناطق والكاميرات مرتبطة بالمنصات برمجيًا فتتحرك معها.

### DEC-062 — إصلاح: ميزانية الجسيمات كانت لا تُطبَّق
- `Vfx.spawn()` كانت تعيد تدوير فتحة المؤشر أيًا كانت حالتها فيتجاوز العدد الحي الميزانية حتى سعة المجمّع (اكتُشف باختبار). الآن عند بلوغ الميزانية يُعاد تدوير أقدم جسيم حيّ فعلًا. كذلك كانت أنواع `boost` و`speed` لا تُنتج جسيمات في `burst()` رغم أن العرض يطلبها؛ أُضيفت.

---

## ما لم يتحقق منه بعد (يُحدَّث في آخر الجلسة)
انظر قسم «حالة التحقق» في GAME_IMPLEMENTATION_PLAN.md — هو المرجع الوحيد لما اختُبر فعلًا وما لم يُختبر.
