# GAME_IMPLEMENTATION_PLAN.md

> الوثيقة الحيّة: الخطة + **حالة التحقق الفعلية** (القسم الأخير). لا يُكتب «تم» هنا إلا لما بُني وشُغِّل واختُبر.
> الرموز: ✔ مُنجز ومُتحقَّق · ◐ مُنجز جزئيًا/بدون تحقق على جهاز · ○ مخطَّط · ✘ محجوب

## الترتيب المتّبع (حسب START NOW في الطلب)
1. قراءة XLSX (7 أوراق) ✔ 2. قراءة PDF (27 ص) ✔ 3. فحص الصورة ✔ 4. الوثائق ✔ 5. مشروع Android جديد ◐ 6. Player + Physics + Touch + Collision + Camera ○ 7. LEVEL_01 ○ 8. Build + تشغيل ○

## الخريطة: المراحل ↔ الوحدات
| المرحلة | الوحدة / الملف | الحالة |
|---|---|---|
| 0 Init + الوثائق الست | `*.md` | ✔ |
| 1 Core (Player/Pogo/Ground/Platforms/Gravity/Jump/Landing/Collision/Fall/Reset/Camera/Goal) | `sim/*`, `render/CameraRig` | ○ |
| 2 Physics (PogoPhysicsController, PhysicsConfig, SurfacePhysics, CollisionResponse, JumpSystem, BoostSystem) | `sim/*` | ○ |
| 3 Mobile Pogo Control | `input/*` | ○ |
| 4 Movement (زخم، فصل Ground/Air) | `sim/PogoPhysicsController` | ○ |
| 5 Boost | `sim/BoostSystem` | ○ |
| 6 Surfaces | `sim/SurfacePhysics` | ○ |
| 7 Level System | `data/LevelData`, `levels/level01` | ○ |
| 8 Worlds | `data/worlds` (4 ثيمات؛ العالم 1 كامل) | ○ |
| 9 Visual / Art Direction | `render/*` | ○ |
| 10 UI/UX | `ui/*` | ○ |
| 11 Audio | `audio/*` | ○ |
| 12 Haptics | `haptics/*` | ○ |
| 13 VFX | `render/Vfx` | ○ |
| 14 Progression | `progression/*` | ○ |
| 15 Customization | `data/items`, `ui/screens/Wardrobe` | ○ |
| 16 Save | `progression/SaveSystem` | ○ |
| 17 Android Optimization | pooling, LOD, quality tiers | ○ |
| 18 Physics Debug Lab | `lab/PhysicsLab` | ○ |
| 19 First Playable (LEVEL_01) | `data/levels/level01` | ○ |
| 20 Visual QA | `qa/*` | ○ |
| 21 Testing | `tests/*` + سيناريوهات Playwright | ○ |
| 22 Final Android Build | `android/`, `tools/build-apk.sh` | ○ |

## نطاق الإصدار 0.1 (قرار صريح)
الطلب يقول: «لا تبنِ اللعبة كلها دفعة واحدة… ثم اختبرها». لذلك:
- **مكتمل الجودة:** العالم 1 (Green Hills) + LEVEL_01 + كل الأنظمة التحتية.
- **جاهز بيانيًا فقط:** العوالم 2–4 (ثيمات/لوحات/إعدادات صعوبة) — قابلة للمعاينة، لكن مراحلها الفعلية **لم تُصمَّم** بعد.
- **خارج النطاق الحالي (مكتوب هنا كي لا يُدَّعى):** متصدرون عالميون، تعاوني/Rubber band، وضع Skate/Loot، إعلانات/مشتريات، Emote داخل اللعب.

## حالة التحقق (تُحدَّث في النهاية)
_(تُملأ بعد الاختبارات الفعلية)_
