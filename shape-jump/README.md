# Shape Jump — World 01 · World 02 · World 03 · World 04

لعبة Runner ثنائية الأبعاد بلمسة واحدة: نواة هندسية مضيئة تجري وحدها، وأنت تقرر متى تقفز — ومتى تقفز مرة ثانية في الهواء.
World 01 "The Red Void": خمسة مستويات صعبة جدًا وعادلة، من AWAKENING إلى RED VOID.
World 02 "The Monochrome Void": أبيض وأسود فقط، عشرة أنظمة جديدة (ظلال بلا أرض، جدران مرايا، حلقات دوّارة، Whiteout، أعمدة، أرضية منقسمة، منصات عائمة، ظل مطارد، متاهة دوّارة، بوابات ثنائية) وخمسة مستويات أصعب، من INVERSION إلى ABSOLUTE ZERO. يُفتح بإنهاء World 01.
World 03 "The Horrifying Galaxy" (المجرة المرعبة): اللاعب مربع أبيض واضح في رعب كوني بين أرض وسقف عائمين، وميكانيكية واحدة جديدة: **قلب الجاذبية**. بوابات وحقول تقلب الجاذبية فتجري على السقف (والشاشة لا تدور: اللعب من اليسار لليمين دائمًا) والعالم يتحول من الأزرق البنفسجي إلى البرتقالي الكهرماني؛ ألغام تسقط مع الجاذبية، صخور، شفرات، شوكتان متقابلتان، أجسام مدارية، ممرات مقسومة وجدران مقلوبة. خمسة مستويات من FIRST FLIP إلى THE HORRIFYING GALAXY، ينتهي آخرها بـFINAL GRAVITY GAUNTLET. يُفتح بإنهاء World 02.
World 04 "The Inverted Garden" (الحديقة المقلوبة): حديقة فنية حيّة بلا مكعبات ولا مسامير، وحركتان بلا Double Jump: **TAP = Hop** (قفزة قصيرة فوق العوائق الصغيرة: جذور صغيرة، فجوات، حبر رقيق، موجة منخفضة) و**TAP + TAP = Surface Attach** (الـHop يتحول إلى عبور مباشر للسطح المقابل، أرض ↔ سقف، في ≈ 0.2 ث). كل مستوى يخلط الثلاثة: حواجز لا يتجاوزها إلا الـHop (وبعضها يقتل من يضغط مرتين)، وحواجز لا يتجاوزها إلا الصعود للسقف أو العودة للأرض. الجري دائمًا من اليسار إلى اليمين؛ على الأرض العالم أزرق وأبيض واللاعب أصفر، وعلى السقف العالم أصفر وأسود واللاعب أزرق، والمنظر كله ينقلب حول أفقه. خمسة مستويات صعبة جدًا تتصاعد بالتوقيت وقلة المساحة، من BLUE BLOOM إلى THE INVERTED GARDEN (41 فعلًا، ينتهي بـFINAL GAUNTLET من 16 فعلًا). يُفتح بإنهاء World 03، وهو العالم الأخير.

- التصميم: [docs/GDD.md](docs/GDD.md)
- البنية التقنية: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- مولّد المستويات: [tools/levelgen/README.md](tools/levelgen/README.md)

---

## التشغيل

1. ثبّت **Godot 4.7.2** (النسخة القياسية، ليست .NET): https://godotengine.org/download
2. افتح Godot ← **Import** ← اختر الملف `shape-jump/project.godot`.
3. اضغط **F5** (Run Project). تظهر شاشة اختيار المستوى: المستوى الأول مفتوح، وكل مستوى يُفتح بإنهاء الذي قبله، وكل عالم يُفتح بإنهاء الذي قبله (تبويبات العوالم أعلى الشاشة).

| الإدخال | الفعل |
|---|---|
| لمس الشاشة / زر الماوس الأيسر / Space / ↑ / W | World 01–03: على الأرض قفزة · في الهواء **Double Jump** (مرة واحدة) · World 04: لمسة = **Hop**، لمستان = **Surface Attach** · في شاشة البداية: ابدأ |
| لمس بطاقة مستوى في شاشة البداية | اختيار المستوى (إن كان مفتوحًا) |
| زر ❚❚ أعلى اليمين / Esc / P | إيقاف مؤقت |

**لتجربة مستوى مغلق أثناء التطوير:** شغّل بـ`-- --unlock-all`، أو في `src/game/game.tscn` اضبط `start_world` (0–3) و`start_level` (0–4)، أو امسح الحفظ من `user://save.cfg`.

## نسخة الويب للتجربة (Playtest)

```bash
python3 tools/web/build_playtest.py --godot=/path/to/godot   # يحتاج قوالب Web لـGodot 4.7.2
cd export/playtest && python3 -m http.server 8000           # ثم افتح http://127.0.0.1:8000/play.html
```

- يصدّر إعداد **Web** (بدون Threads، فلا يحتاج أي Headers خاصة من السيرفر) ويجمع صفحة واحدة في `export/playtest/`: المحرك مضغوطًا (≈ 10MB)، وبيانات اللعبة مضمّنة في الصفحة.
- الصفحة تعرض زرّين: **PLAY** (التقدّم العادي) و**TEST MODE** (كل مراحل العوالم الأربعة مفتوحة). وضع الاختبار يمرّر `-- --unlock-all` للعبة؛ نفس الخيار يعمل على الكمبيوتر: `godot --path shape-jump -- --unlock-all`.

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
godot --headless --path shape-jump --fixed-fps 60 res://tests/tools/level_audit.tscn -- --world=4 --level=5 --windows --exploits
```

262 اختبارًا في ~200 ثانية (بنفس النتائج على 20 و60 FPS)، منها:
- قواعد الـDouble Jump: الارتفاع المزدوج، لا قفزة ثالثة أبدًا، لمستان في إطار واحد، الـCoyote يُبقي الـDJ، الاستعادة عند الهبوط.
- كل نوع من العوائق الـ12 على الفيزياء الفعلية: متى يقتل، متى يسمح بالمرور، الإنذار، والـHitbox لا يتجاوز الرسم.
- **لاعب آلي ينهي كل مستوى من العشرين بدون موت**، والموت عند كل Checkpoint ثم الإنهاء بنفس التوقيت، والحتمية.
- الفتح والتقدم، لوحة الموت، WORLD 01 COMPLETE والانتقال إلى World 02 بثيمه.
- تقدّم المستوى % وعقوبة الموت (25 نقطة) والعودة عند آخر Checkpoint (≈ 33% و≈ 66%) في كل مستوى من العوالم الأربعة.
- World 02: ثيمه وخلفيته وقفله، ولا عائق من World 01 فيه، وكل أنظمته العشرة مستخدمة.
- World 03: قلب الجاذبية على الفيزياء الفعلية (الحالات A–D، لا Coyote ولا Buffer عبر القلب، 24 قلبًا متتاليًا، حافة منصة، منصة متحركة، زاوية سقف)، والكاميرا التي لا تدور ولا تتحرك عند القلب ولا Mirror للاعب والـHUD الثابت، والموت والـRestart والـPause أثناء القلب، والعودة على السقف بالجاذبية والكاميرا والألوان الصحيحة، والاستئناف بعد فقد WebGL، وWORLD 03 COMPLETE.
- World 03: اللاعب مربع أبيض (الرسم فقط؛ الحركة كما هي).
- World 04: الـHop والعبور على الفيزياء الفعلية بمصفوفة الإدخال كاملة (لمسة = Hop بارتفاعه وزمنه، TAP TAP على الأرض وعلى السقف، TAP TAP سريع جدًا، لمسة وTAP TAP على السقف، لمس أثناء العبور وعند الوصول وأثناء الموت والـRespawn، عبورات متتالية، الضغط المتواصل لا يتجاوز ارتفاع Hop واحد ولا يسلسل العبورات، لمسات في الهواء تنتظر الهبوط، لا سطح مقابل = الـHop وحده، جدار في الطريق، لوح بين السطحين، حجر أملس، سطح ينتهي قبل الوصول، المدى، نافذة الإيماءة، سقف متحرك، حافة السطح)، وفي كل Tick: x يزيد و`velocity.x > 0` ولا Mirror؛ الكاميرا لا تتبع قوس العبور، تبدّل ألوان العالم واللاعب عند الوصول، مراحل `SurfaceRun` ومنها `HOPPING`، الموت والـRestart والـPause أثناء العبور، الـRespawn على Checkpoint سقف وأرض، الاستئناف بعد فقد WebGL، 10 Restarts بلا تسرب، وWORLD 04 COMPLETE بلا World 05.
- الكاميرا مقاسة أثناء اللعب في W01 وW02 وW03 وW04 L1 وL5: Zoom 1، نفس حجم اللاعب ونفس الـLook-ahead، اللاعب عند 28% من اليسار، وخط الأرضية عند 73-74% من الارتفاع في كل العوالم (ومعكوسًا على السقف).
- العوائق العضوية لـWorlds 01-03 (كريستال / حبر / صخر فضائي): `tests/tools/art_preview.tscn` يرسم كل عوائق عالم جنبًا إلى جنب.

تشغيل العوالم كاملة آليًا (بموتين مقصودين في كل مستوى) على اللعبة نفسها: `godot --headless --path shape-jump --fixed-fps 60 -- --autoplay --autoplay-die=50,80 --autoplay-quit` (وعالم واحد: `--autoplay-world=4 --autoplay-one-world`)، أو في صفحة الويب بإضافة `#autoplay-deaths` (أو `#autoplay-deaths-w4`) إلى الرابط.

أداة `level_audit` (`--world=1|2|3|4 --level=N`) تقيس لكل لمسة نافذة التوقيت على المحرك الفعلي (أدنى نافذة: 66ms = 4 Ticks في World 02، و83ms في World 03 وWorld 04)، وتتأكد أن الاستراتيجيات الكسولة (بلا لمس، لمس بإيقاع ثابت، وفي World 04: TAP TAP بإيقاع ثابت) تموت كلها ولا تنهي أي مستوى، وفي World 04 تعدّ عبوراتها. و`tools/levelgen/noisy_player.py` يقيس نسبة الإنهاء للاعب بخطأ توقيت بشري (σ = 30ms).

## ضبط الإحساس (Game Feel)

كل أرقام الحركة في `src/player/movement_config.gd` (وتُعدَّل من `src/player/default_movement.tres` في الـInspector):

| القيمة | الافتراضي | التأثير |
|---|---|---|
| Run Speed | 520 px/s | السرعة المرجعية (كل مستوى يضربها بـ`speed_scale`: World 01: 1.0 → 1.23، World 02: 1.10 → 1.24، World 03: 1.15 → 1.28، World 04: 1.18 → 1.40) |
| Jump Height / Time To Apex | 150 px / 0.36 s | القفزة؛ الجاذبية تُشتق تلقائيًا |
| Double Jump Height | 150 px | ارتفاع القفزة الثانية من نقطة إطلاقها |
| Air Jumps | 1 | عدد القفزات الهوائية (الحد الأقصى 1 = قفزتان إجمالًا)؛ لا يُستخدم في World 04 (لا قفزة ثانية) |
| Hop Height / Time To Apex | 72 px / 0.22 s | World 04: القفزة القصيرة (لمسة واحدة)؛ جاذبية World 04 تُشتق منها |
| Attach Window | 0.3 s | World 04: أقصى زمن بين لمستي الإيماءة |
| Attach Reach / Speed / Accel / Max Speed | 360 px / 1000 px/s / 6000 px/s² / 1800 px/s | World 04: أبعد سطح مقابل يُعبر إليه، وسرعة العبور وتسارعها وحدها (عبور الممر ≈ 12 Tick) |
| Fall Gravity Multiplier | 1.3 | ثقل الهبوط |
| Coyote / Jump Buffer | 0.08 / 0.12 s | التسامح في التوقيت |
| Ledge Assist | 10 px | التسامح مع حواف المنصات |

⚠️ تغيير أي رقم حركة يغيّر مسارات المستويات العشرين: عدّل الثوابت المطابقة في `tools/levelgen/levelgen.py`، وأعد بناء المستويات، ثم شغّل التدقيق والاختبارات.

## تعديل المستويات

المستويات مكتوبة في `tools/levelgen/world_01.py` … `world_04.py` ومولّدة إلى `levels/world_0N/`:

```bash
cd shape-jump/tools/levelgen && python3 world_01.py 3 --windows
```

المشاهد الناتجة عادية تمامًا ويمكن فتحها في المحرر (كل العناصر `@tool` وتعرض مساراتها)، لكن أي تعديل يدوي يُستبدل عند البناء التالي؛ التفاصيل في [tools/levelgen/README.md](tools/levelgen/README.md).

---

> المستودع يحتوي أيضًا تطبيق ClipFlow (Android/Kotlin) في جذره؛ اللعبة مستقلة تمامًا داخل `shape-jump/`.
