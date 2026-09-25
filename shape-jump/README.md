# Shape Jump — World 01

لعبة Runner ثنائية الأبعاد بلمسة واحدة: نواة هندسية مضيئة تجري وحدها، وأنت تقرر متى تقفز — ومتى تقفز مرة ثانية في الهواء.
World 01 "The Red Void": خمسة مستويات صعبة جدًا وعادلة، من AWAKENING إلى RED VOID.

- التصميم: [docs/GDD.md](docs/GDD.md)
- البنية التقنية: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- مولّد المستويات: [tools/levelgen/README.md](tools/levelgen/README.md)

---

## التشغيل

1. ثبّت **Godot 4.7.2** (النسخة القياسية، ليست .NET): https://godotengine.org/download
2. افتح Godot ← **Import** ← اختر الملف `shape-jump/project.godot`.
3. اضغط **F5** (Run Project). تظهر شاشة اختيار المستوى: المستوى الأول مفتوح، وكل مستوى يُفتح بإنهاء الذي قبله.

| الإدخال | الفعل |
|---|---|
| لمس الشاشة / زر الماوس الأيسر / Space / ↑ / W | على الأرض: قفزة · في الهواء: **Double Jump** (مرة واحدة) · في شاشة البداية: ابدأ |
| لمس بطاقة مستوى في شاشة البداية | اختيار المستوى (إن كان مفتوحًا) |
| زر ❚❚ أعلى اليمين / Esc / P | إيقاف مؤقت |

**لتجربة مستوى مغلق أثناء التطوير:** في `src/game/game.tscn` اضبط `start_level` على رقم المستوى (0–4)، أو امسح الحفظ من `user://save.cfg`.

## التصدير إلى Android

الإعداد جاهز في `export_presets.cfg` (Landscape، Immersive، arm64 + armv7، يستثني `tests/` و`docs/`؛ و`tools/` لا يستورده Godot أصلًا).

1. **Editor ← Manage Export Templates ← Download and Install** (مرة واحدة).
2. **Editor ← Editor Settings ← Export ← Android**: مسار Android SDK وJava SDK (JDK 17).
3. **Project ← Export ← Android ← Export Project** → `export/shape-jump.apk`، أو **Remote Debug** لهاتف متصل بـUSB.

> الـRenderer هو **Compatibility (OpenGL ES 3)** لأوسع دعم للأجهزة المتوسطة والضعيفة.

## الاختبارات والتدقيق

```bash
godot --headless --path shape-jump --import          # مرة أولى أو بعد إضافة class_name جديد
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn -- --filter=playthrough
godot --headless --path shape-jump --fixed-fps 60 res://tests/tools/level_audit.tscn -- --level=5 --windows --exploits
```

129 اختبارًا في ~19 ثانية (بنفس النتائج على 20 و30 و60 و144 FPS)، منها:
- قواعد الـDouble Jump: الارتفاع المزدوج، لا قفزة ثالثة أبدًا، لمستان في إطار واحد، الـCoyote يُبقي الـDJ، الاستعادة عند الهبوط.
- كل نوع من العوائق الـ12 على الفيزياء الفعلية: متى يقتل، متى يسمح بالمرور، الإنذار، والـHitbox لا يتجاوز الرسم.
- **لاعب آلي ينهي كل مستوى من الخمسة بدون موت**، والموت عند كل Checkpoint ثم الإنهاء بنفس التوقيت، والحتمية.
- الفتح والتقدم، لوحة الموت، WORLD 01 COMPLETE.

أداة `level_audit` تقيس لكل لمسة نافذة التوقيت على المحرك الفعلي (أدنى نافذة في العالم: 66ms = 4 Ticks)، وتتأكد أن الاستراتيجيات الكسولة تموت مبكرًا.

## ضبط الإحساس (Game Feel)

كل أرقام الحركة في `src/player/movement_config.gd` (وتُعدَّل من `src/player/default_movement.tres` في الـInspector):

| القيمة | الافتراضي | التأثير |
|---|---|---|
| Run Speed | 520 px/s | السرعة المرجعية (كل مستوى يضربها بـ`speed_scale`: 1.0 → 1.23) |
| Jump Height / Time To Apex | 150 px / 0.36 s | القفزة؛ الجاذبية تُشتق تلقائيًا |
| Double Jump Height | 150 px | ارتفاع القفزة الثانية من نقطة إطلاقها |
| Air Jumps | 1 | عدد القفزات الهوائية (الحد الأقصى 1 = قفزتان إجمالًا) |
| Fall Gravity Multiplier | 1.3 | ثقل الهبوط |
| Coyote / Jump Buffer | 0.08 / 0.12 s | التسامح في التوقيت |
| Ledge Assist | 10 px | التسامح مع حواف المنصات |

⚠️ تغيير أي رقم حركة يغيّر مسارات المستويات الخمسة: عدّل الثوابت المطابقة في `tools/levelgen/levelgen.py`، وأعد بناء المستويات، ثم شغّل التدقيق والاختبارات.

## تعديل المستويات

المستويات مكتوبة في `tools/levelgen/world_01.py` ومولّدة إلى `levels/world_01/`:

```bash
cd shape-jump/tools/levelgen && python3 world_01.py 3 --windows
```

المشاهد الناتجة عادية تمامًا ويمكن فتحها في المحرر (كل العناصر `@tool` وتعرض مساراتها)، لكن أي تعديل يدوي يُستبدل عند البناء التالي؛ التفاصيل في [tools/levelgen/README.md](tools/levelgen/README.md).

---

> المستودع يحتوي أيضًا تطبيق ClipFlow (Android/Kotlin) في جذره؛ اللعبة مستقلة تمامًا داخل `shape-jump/`.
