# Shape Jump — Prototype v0.1

لعبة Runner ثنائية الأبعاد بلمسة واحدة: نواة هندسية مضيئة تجري وحدها، وأنت تقرر متى تقفز فقط.

- التصميم: [docs/GDD.md](docs/GDD.md)
- البنية التقنية: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## التشغيل

1. ثبّت **Godot 4.7.2** (النسخة القياسية، ليست .NET): https://godotengine.org/download
2. افتح Godot ← **Import** ← اختر الملف `shape-jump/project.godot`.
3. اضغط **F5** (Run Project).

| الإدخال | الفعل |
|---|---|
| لمس الشاشة / زر الماوس الأيسر / Space / ↑ / W | قفز (وبدء الجري من شاشة البداية) |
| زر ❚❚ أعلى اليمين / Esc / P | إيقاف مؤقت |

## التصدير إلى Android

الإعداد جاهز في `export_presets.cfg` (Landscape، Immersive، arm64 + armv7، يستثني `tests/` و`docs/` من الـAPK).

1. **Editor ← Manage Export Templates ← Download and Install** (مرة واحدة).
2. **Editor ← Editor Settings ← Export ← Android**: حدد مسار Android SDK ومسار Java SDK (JDK 17). يولّد Godot مفتاح Debug تلقائيًا؛ وإن طلب Keystore راجع صفحة *Exporting for Android* في وثائق Godot.
3. **Project ← Export ← Android ← Export Project** → `export/shape-jump.apk`، أو زر **Remote Debug** للتشغيل مباشرة على هاتف متصل بـUSB.

> ملاحظة: الـRenderer هو **Compatibility (OpenGL ES 3)** لأوسع دعم للأجهزة المتوسطة والضعيفة.

## الاختبارات

```bash
godot --headless --path shape-jump --import          # مرة أولى أو بعد إضافة class_name جديد
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn -- --filter=playthrough
```

84 اختبارًا تعمل في ~8 ثوانٍ (وتنجح بنفس النتائج على 20 و30 و60 و144 FPS)، وتشمل:
- رياضيات القفزة (الارتفاع الفعلي = 150px المضبوطة)، Coyote، Buffer، لا Double Jump.
- فيزياء حقيقية: زمن الاستجابة، الهبوط والحواف، الفجوات، السقف، السحق، المصاعد، Ledge Assist، عدم الاختراق.
- الكاميرا (موضع اللاعب، لا تمايل، موت السقوط داخل الشاشة) وتدفق اللعب (بدء، تعدد الأصابع، موت، Restart، إيقاف).
- **لاعب آلي ينهي Level 01 بدون موت**، ونتيجتان متطابقتان تمامًا لتشغيلين (Determinism)، وموت متعمد ← عودة للـCheckpoint ← إنهاء المستوى بنفس المسار.
- قواعد سلامة المستوى (Spawn، Finish، ترتيب الـCheckpoints، ≥ 1.5 ثانية أرض آمنة بعد كل Checkpoint).
- أي خطأ من المحرك أو السكربت أثناء اختبار يُفشل ذلك الاختبار.

## ضبط الإحساس (Game Feel)

كل أرقام الحركة في ملف واحد: `src/player/default_movement.tres` (افتحه في الـInspector):

| القيمة | الافتراضي | التأثير |
|---|---|---|
| Run Speed | 520 px/s | سرعة الجري |
| Jump Height | 150 px | ارتفاع القفزة (دقيق فعليًا) |
| Time To Apex | 0.36 s | سرعة الصعود؛ الجاذبية تُشتق تلقائيًا |
| Fall Gravity Multiplier | 1.3 | ثقل الهبوط |
| Coyote / Jump Buffer | 0.08 / 0.12 s | التسامح في التوقيت |
| Ledge Assist | 10 px | التسامح مع حواف المنصات |

باقي قيم الإحساس قابلة للضبط من الـInspector:
- **Game** (GameSession) في `src/game/game.tscn`: مدة التحطم قبل الـFade، أزمنة الـFade، قوة اهتزاز الموت، فرملة النهاية.
- **GameCamera** في نفس المشهد: موضع اللاعب الأفقي، إزاحة الأفق، سرعات المتابعة، المنطقة الميتة للسقوط.
- **Visual / Fx** في `src/player/player.tscn`: الـSquash & Stretch، الدوران، طول الذيل الضوئي.

⚠️ تغيير سرعة الجري أو القفزة يغيّر مسار Level 01؛ شغّل الاختبارات بعدها — اختبار الـplaythrough سيخبرك أين يموت اللاعب الآلي.

## إضافة مستوى جديد

1. أنشئ مشهدًا جذره `Node2D` بسكربت `src/level/level.gd`، وأضف `Marker2D` باسم `SpawnPoint`.
2. أنشئ `LevelData` (`.tres`) بمعرّف واسم ومعامل سرعة، واربطه بخاصية `data`.
3. أضف العناصر من `src/level/elements/` — كلها `@tool`: غيّر الحجم/العدد من الـInspector والشكل يتحدث فورًا. فعّل Grid Snap بقيمة 64.
4. لأي عنصر متحرك: أضف له ابنًا بسكربت `oscillator.gd` (المسار يظهر في المحرر).
5. أضف المستوى إلى `LEVELS` في `tests/unit/test_level_integrity.gd`، واكتب له مسارًا في اختبار playthrough.

---

> المستودع يحتوي أيضًا تطبيق ClipFlow (Android/Kotlin) في جذره؛ اللعبة مستقلة تمامًا داخل `shape-jump/`.
