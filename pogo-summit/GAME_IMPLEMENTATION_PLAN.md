# GAME_IMPLEMENTATION_PLAN.md

> الوثيقة الحيّة: الخطة + **حالة التحقق الفعلية** (القسم الأخير). لا يُكتب «تم» هنا إلا لما بُني وشُغِّل واختُبر.
> الرموز: ✔ بُني وشُغِّل واختُبر هنا ولا نقص معروف · ◐ يعمل هنا لكن له نقص موثَّق أو يحتاج جهازًا حقيقيًا للتحقق · ○ مخطَّط · ✘ محجوب

## الترتيب المتّبع (حسب START NOW في الطلب)
1. قراءة XLSX (7 أوراق) ✔ 2. قراءة PDF (27 ص) ✔ 3. فحص الصورة ✔ 4. الوثائق ✔ 5. مشروع Android جديد ✔ (APK يُبنى ويُوقَّع؛ Gradle محجوب) 6. Player + Physics + Touch + Collision + Camera ✔ 7. LEVEL_01 ✔ 8. Build + تشغيل ◐ (تشغيل في Chromium بدون واجهة؛ لا جهاز Android)

## الخريطة: المراحل ↔ الوحدات
| المرحلة | الوحدة / الملف | الحالة |
|---|---|---|
| 0 Init + الوثائق الست | `*.md` | ✔ |
| 1 Core (Player/Pogo/Ground/Platforms/Gravity/Jump/Landing/Collision/Fall/Reset/Camera/Goal) | `sim/*`, `render/CameraRig` | ✔ |
| 2 Physics (PogoPhysicsController, PhysicsConfig, SurfacePhysics, CollisionResponse, JumpSystem, BoostSystem) | `sim/*` | ✔ |
| 3 Mobile Pogo Control | `input/*` | ◐ |
| 4 Movement (زخم، فصل Ground/Air) | `sim/PogoPhysicsController` | ✔ |
| 5 Boost | `sim/BoostSystem` | ◐ |
| 6 Surfaces | `sim/SurfacePhysics` | ◐ |
| 7 Level System | `data/LevelData`, `levels/level01` | ✔ |
| 8 Worlds | `data/worlds` (4 ثيمات؛ العالم 1 كامل) | ◐ |
| 9 Visual / Art Direction | `render/*` | ◐ |
| 10 UI/UX | `ui/*` | ◐ |
| 11 Audio | `audio/*` | ◐ |
| 12 Haptics | `haptics/*` | ◐ |
| 13 VFX | `render/Vfx` | ✔ |
| 14 Progression | `progression/*` | ✔ |
| 15 Customization | `data/items`, `ui/screens/Wardrobe` | ✔ |
| 16 Save | `progression/SaveSystem` | ✔ |
| 17 Android Optimization | pooling, LOD, quality tiers | ◐ |
| 18 Physics Debug Lab | `lab/PhysicsLab` | ✔ |
| 19 First Playable (LEVEL_01) | `data/levels/level01` | ✔ |
| 20 Visual QA | `qa/*` | ◐ |
| 21 Testing | `tests/*` + سيناريوهات Playwright | ✔ |
| 22 Final Android Build | `android/`, `tools/build-apk.sh` | ◐ |

## نطاق الإصدار 0.1 (قرار صريح)
الطلب يقول: «لا تبنِ اللعبة كلها دفعة واحدة… ثم اختبرها». لذلك:
- **مكتمل الجودة:** العالم 1 (Green Hills) + LEVEL_01 + كل الأنظمة التحتية.
- **جاهز بيانيًا فقط:** العوالم 2–4 (ثيمات/لوحات/إعدادات صعوبة) — قابلة للمعاينة، لكن مراحلها الفعلية **لم تُصمَّم** بعد.
- **خارج النطاق الحالي (مكتوب هنا كي لا يُدَّعى):** متصدرون عالميون، تعاوني/Rubber band، وضع Skate/Loot، إعلانات/مشتريات، Emote داخل اللعب.

## حالة التحقق (آخر تحديث: نهاية الجلسة — كل سطر هنا نُفّذ فعلًا)

### ما نُفِّذ وتحقَّقنا منه فعلًا
| الفحص | الأداة | النتيجة |
|---|---|---|
| الفيزياء: ثوابت A، إطلاق/جاذبية، اصطدام بلا اختراق، جدار، نطاط، جليد، خطر، منصة متحركة، هدف، Boost، تطابق مسار الدليل مع المحاكاة | vitest (`tests/physics.test.ts`) | ✔ |
| الحتمية: نفس المدخلات ⇒ حالة متطابقة بتًّا (3 تشغيلات × 60 000 tick) | vitest (`determinism.test.ts`) | ✔ |
| نقاء `src/sim` (لا DOM/Three/Math.random/Date.now) | vitest (`architecture.test.ts`) | ✔ |
| قابلية إكمال LEVEL_01: تحليل شبكة (زاوية×قوة) لكل حافة + بوت مسار كامل + إعادة تشغيل مسجَّلة | vitest (`solvability`, `route`) + `tools/analyze-level.ts` | ✔ كل الحواف ≥ 10 خلايا ناجحة من 297 |
| إكمال LEVEL_01 داخل التطبيق الحقيقي (حلقة اللعبة + نفس السكربت) بعد آخر تغيير بصري | Playwright/Chromium، `playScript` | ✔ `FINISHED`, 18 قفزة، 0 سقوط، تقدّم 100% |
| التحكم باللمس: Down/Drag/Release/Cancel، multi-touch، deadzone، smoothing، pull، PAD | vitest + jsdom (12 اختبارًا) **وأحداث لمس حقيقية عبر CDP** | ✔ |
| الحفظ/التقدّم/النجوم/المتصدرون المحلي/الفتح/الإعدادات/الاهتزاز (cooldown)/i18n RTL | vitest (`systems.test.ts`, 16) | ✔ |
| مزامنة PHYSICS_MASTER.md مع `PhysicsConfig` (42 معاملًا، لا قيمة مجهولة بدرجة غير D) | vitest (`docs.test.ts`) | ✔ |
| غلاف Android: منطق المضيف (أصل HTTPS الافتراضي، حجب المضيفات، الإدراجات) | JUnit عبر Gradle محلي (`android/shell-tests`) | ✔ 10/10 |
| بناء APK وتوقيعه والتحقق | `tools/build-apk.sh` (aapt/dx/zipalign/apksigner) | ✔ 366 KB؛ v2+v3 verifies؛ package `app.pogosummit.game` · versionName 0.1.0 · minSdk 24 · targetSdk 34 · الصلاحية الوحيدة `VIBRATE` · landscape · أيقونات mdpi→xxxhdpi |
| `tsc --noEmit` | TypeScript 5.7 | ✔ نظيف |
| مجموع vitest | 8 ملفات | ✔ **78/78** |
| عرض WebGL2 الفعلي + لقطات (قائمة، اختيار، HUD، Pause، Options، Wardrobe، Lab، النتائج، 4 عوالم) | Chromium + SwiftShader، `qa/*.png` | ✔ |
| عدّادات الرسم | `renderer.info` | draw calls ≈ 125–160 (< 250) · مثلثات ≈ 31–67k (< 150k) · 3 خامات |

### Visual QA مقابل الصورة المرجعية (Phase 20) — قياس فعلي (`tools/qa-compare.py` ⇒ `qa/qa-compare-report.txt`)
| المقياس | المرجع (منطقة اللعب) | لقطاتنا (5 مواضع) | الحكم |
|---|---|---|---|
| متوسط السطوع | 0.485 | 0.41 – 0.52 | ✔ ضمن المدى |
| تشبّع | 0.470 | 0.39 – 0.53 | ✔ |
| سماء زرقاء في النصف العلوي | 0.33 | 0.14 – 0.50 | ✔ (البداية أدنى لأن جدار الجرف يغطي اليسار) |
| سحاب في النصف العلوي | 0.12 | 0.10 – 0.21 | ✔ |
| دافئ (برتقالي/أحمر الخريف) | 0.24 | 0.08 – 0.28 | ✔ ما عدا لقطة الجليد (متعمَّد) |
| كثافة الحواف/التفاصيل (Sobel) | 0.095 | 0.040 – 0.045 | ✘ **فجوة معروفة**: ≈ نصف تفاصيل الصورة |
| اللاعب ≥ 20% من ارتفاع الشاشة | — | ≈ 26% | ✔ |
| ≥ 5 طبقات عمق | — | سماء، 3 سلاسل جبال، سحاب، جزر/أعمدة، معالم (قلعة/جسر/شلال)، مستوى اللعب، إطار نباتي أمامي | ✔ |
| 4 عوالم متمايزة بصريًا | — | Green Hills · Snow Peaks · Ancient Ruins · Volcanic Depths (معاينة LEVEL_01 بثيم كل عالم) | ✔ معاينة فقط |

**ما أصلحه المرور البصري:** غطّت الجزر العائمة والمعالم القريبة السماء بالكامل (سماء 7–14% فقط) ⇒ قاعدة «ممر السماء» (لا جزيرة تدخل نطاق [camY+0.2H, camY+1.0H] عند أي موضع كاميرا) + سقف للحجم الزاوي؛ تدرّج السماء كان لا يصل لونه العلوي لأن FOV ضيق (±15°) ⇒ تمديد ×3.4؛ الجبال أبهتها الضباب ⇒ منظور جوي مخبوز؛ إضافة غيوم cumulus واضحة الحدود؛ جروف بطبقات؛ سلاسل المنصات المتحركة كانت سوداء (metalness بلا خريطة بيئة).

**الفجوة المتبقية بصراحة:** الصورة المرجعية مرسومة/مُصيَّرة بتفاصيل دقيقة (خامات، كروم، نباتات صغيرة). لوحتنا إجرائية low-poly بألوان رؤوس (لا خامات) فتبدو أنظف وأقل تفصيلًا. مقياس الحواف ≈ 0.04 مقابل 0.095. هذا الفارق **لم يُغلق**؛ الإغلاق يتطلب أصولًا مرسومة/خامات (نقاط عمل للإصدار التالي).

### Visual upgrade pass (2nd user request: "VISUAL QUALITY UPGRADE — DO NOT REBUILD GAMEPLAY") — results
**Gameplay unchanged:** no changes under `src/sim`, `src/input`, `src/data/levels`, `src/progression`. Proof: the 78 vitest tests pass unchanged, and the recorded route replay in the real app gives the same result before and after (`FINISHED`, 18 jumps, 0 falls); the replay was also run **with full rendering every frame** through the whole level up to the results screen (`qa/live_gameplay_run.jpg`).

| Area | What was done (see DEC-038…043) | Visual evidence |
|---|---|---|
| Surfaces / lighting | Triplanar procedural detail + sky occlusion + separate rim light for the player; W1 and W4 brightened | `qa/before_after_world1.jpg` |
| Assets | `shapes.ts` kit (rounded edges, smooth shading); rocks/cliffs with strata, ledges, fluting, moss, shelves with trees; smooth trees with baked AO; flowers/bushes/mushrooms redone | same |
| Platforms | A distinct identity per type (rock / wood with posts or chains / glossy ice / bounce pad with helix springs / hazard per world / goal with a light pillar) + rock supports under platforms with nothing below | `qa/after_*.png` |
| World variety | Landmark, bridge, vegetation, decor, hazard, waterfall flow and clouds all differ per world | `qa/before_after_worlds234.jpg` |
| Player | Every part redrawn: real eyes, helix pogo spring, tubes, foot pegs, red scarf, beanie badge; ≈ 20 draw calls instead of ≈ 60 | `qa/after_*.png` |
| HUD | Bundled offline font, stat card with the timer as the hero number, progress bar with a flag, light hint card, Boost readiness ring | all screenshots |
| VFX | Jump dust + ground ring, landing dust + debris, hard impact (flash + big ring), bounce, oriented boost trail, goal effect | `qa/live_gameplay_run.jpg` |
| Performance | **101–124 draw calls · 103k–142k triangles** (including the shadow pass) across 4 worlds and 7 views; APK grew from 366 KB to 454 KB | `renderer.info` (headless Chromium) |
| Production build | The minified bundle that ships in the APK renders correctly (screenshot taken from it) | `qa/after_prodbuild.png` |

**Numeric metrics (`qa/qa-compare-after.txt`) — reported honestly:** brightness 0.45–0.52 (reference 0.485) ✔, saturation 0.41–0.51 (0.47) ✔, open sky 0.25–0.49 (0.33) ✔. **Edge density did not improve numerically (≈ 0.034–0.045 vs 0.095 in the reference):** the improvement came from forms, smooth shading and soft surface detail, which the Sobel filter at 360 px barely registers. The reference is a painted image with very fine detail, and **that gap is still open** (it needs hand-painted textures/assets).

**Still unverified:** a real device (FPS, thermals, haptics), AAB/Play — unchanged from the section above.

### Audio system (task "ANALYZE → SPEC → IMPLEMENT → TEST", DEC-063…072) — what was actually run
**Not touched:** `src/sim`, `Pogostuck_Physics_LOCKED_SPEC.md`, `src/map` (diff-checked; `architecture`, `spec`, `physics`, `determinism`, `map/boundary` pass unchanged).

| Check | Tool | Result |
|---|---|---|
| 11 original WAVs measured (format, peak, RMS, LUFS, spectrum, envelope) | `tools/audio-analyze.py` (LUFS cross-checked with ffmpeg `ebur128`, ≤ 0.3 LU) | `POGOSTUCK_AUDIO_ANALYSIS.md`, `qa/audio/original_analysis.json` |
| Original sound layer and 20 call sites read from the decoded script | RE listing (addresses in the analysis) | ✔ ; `pogoSound.dll`, `kuSound.dll`, `kupack_audio_index.csv` **not supplied ⇒ UNKNOWN** |
| Audio system logic: charge, launch, collision, variants, no immediate repeat, cooldown/burst/voice cap, ice START/MODULATE/STOP, break, pooling, volume mapping, missing audio, zones, Map V2 bridge, recipes vs measured descriptors | vitest, 17 new files / 253 tests | ✔ |
| Whole suite | vitest | ✔ **53 files / 735 tests** (was 36 / 482) · `tsc --noEmit` clean · production bundle 1008 KB |
| Real Web Audio API (live context, channel pooling, every event, drain to idle) | `tools/audio-browser-check.mjs`, headless Chromium | ✔ unlock 35 ms, 14 voices on 11 pooled channels (3 reused), pool peak 9, drains to 0 |
| Scripted session mixed through the real bus graph + compressor, exported and analysed | same (OfflineAudioContext) → `tools/audio-analyze.py` | ✔ timeline as scripted (charge click, power launch, boings, ice loop modulated and faded, breaks, truncated sting; the 4th wall hit suppressed by the cooldown); peak −10.8 dBFS, no clipping |
| Production bundle driving the real game loop: charge, auto-launch, landings, wall hit, real ice landing ⇒ slide loop started and later freed | `tools/qa-audio-ingame.mjs` | ✔ 0 page errors, 0 skipped/missing events, 11 recipes warm (1.3 MB) |

**Still unverified:** the sound has **not been heard by a person** (no audio device here; mix levels follow the original's call-volume ratios, not an ear); no Android device (`DEVICE_TEST = NOT_AVAILABLE`); the licensed-sample bank path is tested with fakes only.

### ما لم يُتحقَّق منه (لا يُدَّعى)
- ✘ **تشغيل على جهاز/محاكي Android**: لا KVM ولا جهاز هنا. لم يُجرَّب: Immersive، Cutout/Safe-area الفعلي، الاهتزاز الفعلي، لمس بإصبع حقيقي، الأداء الحقيقي (كل `fps` المقاسة هنا من SwiftShader/CPU ولا تمثل هاتفًا).
- ✘ **Gradle/AGP وإنتاج AAB وتوقيع الإطلاق**: `dl.google.com` محجوب في هذه البيئة (403). مشروع Gradle مكتوب (compileSdk/targetSdk 35) لكنه غير مبني. الـAPK الحالي debug موقّع بمفتاح debug ويُبنى بسلسلة apt القديمة (android-23.jar، targetSdk 34 في الـmanifest).
- ◐ الصوت: يُبنى ويُشغَّل بلا أخطاء على Web Audio حقيقي في Chromium ويُحلَّل رقميًا (انظر قسم Audio system أعلاه) لكن **لم يستمع إليه إنسان** (لا جهاز صوت)؛ ضبط المزج يحتاج أذنًا وجهازًا حقيقيًا.
- ◐ **Boost** عبر الواجهة الحقيقية من البداية للنهاية لم يُختبر آليًا (المنطق مغطّى بوحدات + نبضة زر PAD في jsdom)؛ LEVEL_01 لا يتطلبه.
- ◐ أسطح `Sticky` و`Boost` و`Slope` موجودة في `SurfacePhysics` لكن لا يستخدمها LEVEL_01 ولا تغطيها اختبارات مخصّصة.
- ◐ العوالم 2–4: ثيمات/لوحات/إضاءة/موسيقى/صعوبة فقط؛ **لا مراحل فعلية** لها.
- ◐ كل معاملات الحركة `TUNE_ME`: قيمنا الابتدائية للعب وليست قيم Pogostuck؛ تُضبط بالقياس من الفيديو (خطة القياس في PHYSICS_MASTER.md).
