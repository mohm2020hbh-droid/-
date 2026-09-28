# Level Generator — World 01 · World 02 · World 03 · World 04

أداة تطوير (Python 3، بلا مكتبات خارجية) تكتب مستويات العوالم الأربعة وتتحقق منها. المجلد `tools/` يحتوي `.gdignore`، فلا يستورده Godot ولا يدخل في التصدير.

| الملف | الدور |
|---|---|
| `levelgen.py` | الـAPI: الكتل، العوائق (نسخ مطابقة لـHitboxes الـGDScript)، محاكاة حركة اللاعب Tick بـTick، الضبط، التقارير، كتابة المشاهد |
| `world_01.py` | مستويات World 01 الخمسة كمقاطع (Groups) مقروءة، وكتابة `world_01.tres` والمسارات |
| `world_02.py` | مستويات World 02 الخمسة (The Monochrome Void)، و`world_02.tres` (ثيم `mono`، خلفية `background_mono.tscn`، يُفتح بإنهاء World 01) و`world_02_routes.gd` |
| `world_03.py` | مستويات World 03 الخمسة (The Horrifying Galaxy)، مبنية من "Beats" بقلب الجاذبية، و`world_03.tres` (ثيم `galaxy`، خلفية `background_galaxy.tscn`، يُفتح بإنهاء World 02) و`world_03_routes.gd`. `--load` يطبع حمل التوقيت (bit/s) |
| `world_04.py` | مستويات World 04 الخمسة (The Inverted Garden)، مبنية من Beats بالـSurface Attach (TAP + TAP، بلا قفز) وخطة سطوح (`Plan`)، و`world_04.tres` (`surface_latch = true`، ثيم `garden`، خلفية `background_garden.tscn`، يُفتح بإنهاء World 03، العالم الأخير) و`world_04_routes.gd`. يطبع حمل التوقيت ونوع كل إيماءة (A عبور، f فشل؛ J/D لا يجب أن تظهر) |
| `noisy_player.py` | لاعب بخطأ توقيت بشري على المحاكاة: `python3 noisy_player.py world_04 3 --trials=300 --sigma=30` → نسبة إنهاء المستوى في محاولة واحدة ونسبة كل مقطع بين Checkpointين |

## التشغيل

```bash
cd shape-jump/tools/levelgen
python3 world_01.py              # يبني المستويات الخمسة (≈ 3 دقائق)
python3 world_01.py 3            # يبني Level 03 فقط، ويُبقي مسارات الباقي كما هي
python3 world_01.py 3 --windows  # + نافذة التوقيت لكل لمسة ونتيجة ضبط كل عائق
python3 world_01.py 3 --taps=5,6 # نوافذ لمسات محددة فقط (أسرع)
python3 world_02.py              # World 02 كاملًا (≈ 7 دقائق)
python3 world_02.py 2 4          # مستويات محددة في عملية واحدة
python3 world_03.py              # World 03 كاملًا (≈ 25 دقيقة، الضبط أثقل)
python3 world_03.py 5 --load     # Level 05 مع النوافذ وحمل التوقيت
python3 world_04.py 5 --windows  # World 04: كل مستوى ≈ 2–6 دقائق؛ مستويات مختلفة يمكن بناؤها بالتوازي (قفل على ملف المسارات)
```

> لا تشغّل عدة عمليات للعالم نفسه بالتوازي: كل عملية تقرأ ملف المسارات ثم تعيد كتابته.

المخرجات:
- `levels/world_01/level_0N.tscn` و`.tres` (المشهد والـ`LevelData`)، و`levels/world_01/world_01.tres`.
- `levels/world_01/world_01_routes.gd`: المسار المقصود لكل مستوى (مواضع اللمس بالـtiles)، يستخدمه اللاعب الآلي في الاختبارات وأداة التدقيق.

البناء حتمي: نفس الكود = نفس الملفات بايتًا بايتًا.

## كتابة مقطع

```python
lv.group("PulseOverGap")                 # اسم المقطع في شجرة المشهد
lv.tap(208.4)                            # لمسة أرضية مقصودة عند x (مركز اللاعب، tiles)
lv.block(213.5, 219, 0)                  # أرض من x=213.5 إلى 219 بارتفاع 0
g = lv.pulse_gate(212.6, 1.4)            # بوابة فتحتها الأولى على قوس المسار الفعلي
lv.tune(g, 208.4, 130)                   # طورها يجعل نافذة تلك اللمسة ≈ 130ms
lv.dj(211.0)                             # لمسة يجب أن تكون Double Jump
lv.shards_along(205, 220, 3.0)           # Shards على المسار النهائي
```

- الإحداثيات: x بالـtiles، والارتفاع بالـtiles فوق خط الأرض (للأعلى موجب).
- `lv.land_x(x, rise, air)` يحسب أين يهبط قفز من x إلى ارتفاع نسبي — لبناء المنصات حيث يصل المسار فعلًا.
- `tune(element, tap, target_ms, osc=False, forced=True)`: يجرب 120 طورًا ويختار الأقرب للهدف والأكثر تمركزًا حول اللمسة. مع `forced` يُرفض أي طور يسمح بالنجاة دون تلك اللمسة. يفشل البناء برسالة تشرح السبب إن لم يوجد طور صالح.
- `fit_phase(element, lazy=[...])`: بديل أبسط (Level 01): أبعد طور عن المسار مع قتل المسارات الكسولة.
- الضبط والوضع يحدثان في `done()` **بترتيب الكتابة**، والعوائق التي لم يأتِ دورها لا تدخل المحاكاة. بعدها `recenter_route()` يمركز كل لمسة في نافذتها ويتحقق من المسار كاملًا بعد كل نقل.

## عناصر World 02 في الـAPI

| الدالة | العنصر |
|---|---|
| `shadow(x0, x1, top)` | **SHADOW GAP**: أرض مرسومة بلا تصادم (حافة متقطعة). البناء يفشل إن وُضعت فوق أرض حقيقية |
| `mirror_wall(...)` / `mirror_wall_fit(x, margin)` | **MIRROR WALL**: لوحان يفتحان ممرًا ويغلقانه؛ `_fit` يضع الممر على قوس المسار |
| `orbit(...)` / `orbit_fit(x, radius, spin)` | **ORBIT RING**: حلقة تدور بفتحات؛ `_fit` يضع مركزها حيث يعبر المسار جانبيها (3 فتحات × 60° افتراضيًا) |
| `column(x0, w, bottom, h, travel)` / `column_under(x, w, margin)` | **BLACK COLUMN** متحرك عموديًا، أو ثابت تحت القوس (يعاقب القفزة المتأخرة) |
| `chaser(x_from, x_to, lag, period, reach, tongue)` | **SHADOW CHASER**: ظل خلف اللاعب يمد لسانًا على الأرض بإيقاع ثابت |
| `maze(x, h, length, hold, turn, direction)` | **ROTATING MAZE**: لوح يدور ربع دورة كل مرة |
| `binary(x, white, black, period)` | **BINARY GATE**: حاجز بحالتين (أبيض/أسود) بمجالين مختلفين |
| `whiteout(x0, x1, ...)` (و`world_02.whiteout(lv, …, flash_x)`) | **WHITEOUT**: وميض أبيض بصري فقط يبدأ عند موضع محدد |
| `floating(...)` / `split_floor(x0, n, w, top, travel, period)` + `slab_group(slabs)` | **FLOATING PANELS** و**SPLIT FLOOR** (طور واحد قابل للضبط لكل الألواح) |

### مطابقة فيزياء المحرك (مهم للمنصات المتحركة)

- المنصة المتحركة (`AnimatableBody2D` مع `sync_to_physics`) تصل لمحرك الفيزياء **بعد Tick واحد** من حركة عقدتها: المحاكاة تصطدم بـ`Surface.solid(t)` = موضعها عند `t − 1 Tick`.
- الوقوف: الـFloor Snap يختار أعلى سطح تحت الجسم ضمن 12px، لكن السطح المجاور لا "يلتقط" اللاعب إلا إن لم يكن فوق قدميه قبل Tick (من هناك يرفعه صاعدًا).
- الجدران: تُفحص بالمسح (أين القدمان لحظة لمس الواجهة)، والـLedge Assist يقيس الدرجة من القدمين القديمتين وقت الوقوف (≤ 10px).
- **Crush**: لوح يرفع اللاعب إلى أسفل لوح آخر (أو ينزل على رأسه) = موت، كما في المحرك.
- الـMaze يعيد نمطه كل 4 دورات: الطور يُكتب كسرًا والدورات الكاملة تُضاف إلى `start_quarter`.

## World 03: الجاذبية في المحاكاة

- **الجاذبية من جدول ثابت:** `_gravity_list()` يجمع أحداث `GravityGate` و`FlipField` (x، الاتجاه)، و`gravity_up_at(t)` / `last_gravity_change(t)` تطابق `Level` في GDScript (الحدث عند وصول **مركز** اللاعب إلى x).
- **إطار معكوس:** ما دامت الجاذبية للأعلى تجري المحاكاة في إطار y → −y: الأسطح تُلف بـ`Mirrored` (يصبح أسفل الكتلة سطحها)، ومضلعات الأخطار تُعكس، فتعمل قواعد الـMotor كما هي. عند القلب: نفس السرعة في العالم (`vy = −vy` في الإطار الجديد)، بلا أرض ولا Coyote، والـBuffer يسقط، والطابور يبقى — مثل `PlayerMotor.flip()`.
- كل Tick يسجل `up` و`feet_h` (الوجه الملامس للأرضية)، و`y`/`h` = أسفل الصندوق في العالم. الـCheckpoints تُوضع على السقف مقلوبة (`rotation = PI`) حين تكون الجاذبية للأعلى.
- `tune_any_kind = True` (World 03 فقط): الضبط يقيس النافذة كما يقيسها التدقيق (أي قفزة تنجو تُحسب)، فيختار أطوارًا تقتل البدائل الكسولة (لمسة مبكرة تتحول Double Jump). World 01/02 على الافتراضي، ومخرجاتهما لم تتغير بايتًا.

| الدالة | العنصر |
|---|---|
| `gblock` / `roof` / `slab` / `floater` | كتل المجرة: أرض، سقف (وجهه السفلي مضاء)، جدار أفقي مضاء من الوجهين (INVERTED WALL / DUAL ROUTE)، منصة عائمة |
| `gravity_gate(x, up, ceiling)` | **GRAVITY GATE** بعرض الممر |
| `flip_field(x0, x1, inside_up, ceiling, pit=False)` | **FLIP FIELD** (و**GRAVITY PIT** مع `pit=True`) |
| `mine(x, ceiling)` | **GRAVITY MINE**: يستقر على الأرضية الحالية ويسقط عند القلب بتسارع ثابت |
| `asteroid(x, ceiling, period)` | **FALLING ASTEROID** نحو الأرضية الحالية (اتجاهه يُقرأ عند بداية كل دورة) |
| `trap(x0, w, surface, facing, reach)` | **CEILING TRAP** في أي سطح |
| `orbital(x, h, radius, bodies, spin)` | **ORBITAL HAZARD** |
| `dual(x, ceiling, low, high, alternate)` | **DUAL HAZARD**: شوكتان متقابلتان (معًا = شقّ، أو بالتناوب) |
| `echo(...)` / `lens(...)` | **GRAVITY ECHO** و**GRAVITY LENS** (بصرية، بلا تصادم) |

وفي `world_03.py` "Beats" جاهزة: `mine_pair`، `slot`، `trap`، `rock`، `orbit_hop`، `orbit_dj`، `rock_dj`، `air_gate` (قلب في قمة قفزة فوق خطر)، `ground_gate`، `low_hops` (تحت جدار)، `dj_gap` (Double Jump متأخر إلى حافة مرتفعة). `orbit_dj` يأخذ اتجاه الدوران من منظور السطح الحالي (الجاذبية للأعلى تعكسه في العالم).

## World 04: TAP + TAP = Surface Attach في المحاكاة

- **`latch_mode`:** لا قفز. كل مدخل في المسار هو **اللمسة الثانية** للإيماءة، والمحاكاة تضع اللمسة الأولى قبلها بـ`GESTURE_GAP` (5 Ticks)؛ الإيماءة `idle → pending → request` كما في `PlayerMotor` (نافذة `ATTACH_WINDOW` = 0.3 ث). الطلب يُفحص بـ`attach_probe` كما يفحصه `SurfaceAttach.probe`: يمشي مسار العبور الحقيقي (الجري + `attach_speed_at(k)` نحو السطح المقابل) حتى `ATTACH_REACH` = 360 px، وأول ما يلمسه يجب أن يكون وجه سطح قابل للإمساك. إن صح: عبور بلا جاذبية يتسارع من 1000 إلى 1800 px/s، والوصول (لمس السطح) يقلب الإطار ويُسجَّل في سجل سطوح المحاولة (`"arrive"`)؛ وإلا فشل (`"fail"`) بلا حركة. العوائق المثبتة على `FLOOR` / `SKY` تقرأ أرضية اللحظة من السجل كما في `Level.surface_up_at`.
- **بداية من Checkpoint = Respawn:** (x، الارتفاع، السطح) وسجل جديد، والزمن مقرّب لشبكة الـTicks كما يفعل `GameSession.time_at`؛ ونقطة إعادة المحاولة (`_ground_before`، `_cut_points`) لا تقع أبدًا بين لمستي إيماءة (`Level.gesture_lead()`).
- **`check()`** يرفض مسارًا يعبر إلى حافة سطح أملس (حالة ملتبسة في المحرك).
- **خطة السطوح (`Plan` في `world_04.py`):** السطح المقابل موجود فقط حول العبورات التي تحتاجه (`early` قبل الوصول)، وبعد كل عبور يُقطع السطح المتروك (`late` بعد اللمسة)، فكل عبور ضروري (تخطيه = سقوط) ولكل مقطع أرضية واحدة يقف عليها الـCheckpoint. `early` / `late` لكل مستوى هما "هامشه" قبل أن تضيّقه العوائق.
- **مفاتيح الصعوبة:** أهداف `tune` لنافذة كل Beat (ms)، الهامش (`Plan(early, late)`)، والمسافة بين الـBeats (فواصل غير متساوية تكسر الإيقاع الثابت).

| الدالة | العنصر |
|---|---|
| `garden_block` / `garden_mover` | كتل الحديقة (أرض/سقف، `latchable=False` = حجر أملس؛ في `world_04.py`: `Band.slicken(x0, x1)` و`Band.cut`) وسقف متحرك |
| `roots` · `branch` · `flower` · `wave` · `waterfall` · `wind` · `rock` (`ice=True`) · `vines` · `glider` · `ink` · `curtain` · `boulder` · `flock` | العوائق، بمرساة `GROUND` / `CEILING` / `FLOOR` (أرضية اللاعب عند بداية الدورة) / `SKY` (المقابل لها) |
| `weather(x0, x1, "leaves" / "snow")` | طقس زينة بلا تصادم |
| `attach(x)` | إيماءة يجب أن تنتهي بعبور (والتقرير يفشل إن فشلت أو صارت شيئًا آخر) |

وفي `world_04.py` Beats جاهزة، كل واحد عبور واحد وسؤال "متى؟": `roots_here` و`wave_here` (خطر على سطحك: اعبر قبله)، `flower_across` و`ink_across` (خطر على المقابل: اعبر بعده)، `vines_across` و`curtain_through` (ستارة فوق نصف المسار الأول)، `branch_through` و`boulder_through` و`flock_through` و`glider_through` و`rock_through` و`waterfall_through` (شيء يعبر طريق العبور)، `slick_wait` (سقف أملس: انتظر)، `moving_ceiling` (سقف يعلو خارج المدى وينزل: عبوران)، و`rest`.

## التحقق على المحرك

المحاكاة محافظة (هامش أمان 2px حول كل Hitbox)، لكن المرجع النهائي هو Godot:

```bash
godot --headless --path shape-jump --fixed-fps 60 res://tests/tools/level_audit.tscn -- --world=4 --level=5 --windows --exploits
godot ... level_audit.tscn -- --world=2 --level=4 --probe=72,75 --probe-contacts --trace-jumps   # تشخيص Tick بـTick
```

- `ROUTE`: المسار كاملًا بلا موت (وعند الموت: العائق القاتل وزاويته وآخر مواضع اللاعب)، عدد القفزات والـDouble Jumps، والـShards المجمعة (وأي Shard فائت).
- `--windows`: لكل لمسة، كم Tick يمكن تقديمها أو تأخيرها وحدها مع النجاة.
- `--exploits`: استراتيجيات كسولة (بلا لمس، لمس بإيقاع ثابت، Jump+DJ متكرر) يجب أن تموت مبكرًا.

ثم الاختبارات (`tests/test_runner.tscn`) تُنهي كل مستوى بالمسار ومن كل Checkpoint.

## ملاحظات قياس

- نافذة لمسة تأتي مباشرة بعد هبوط تشمل الـBuffer (0.12s): أي لمسة قبل الهبوط بـ≤ 7 Ticks تُنفَّذ لحظة الهبوط، فلا يستطيع أي عائق تضييق هذا الجزء.
- إذا أعطى الضبط نافذة أوسع من الهدف في التقرير، فغالبًا هناك مسار بديل (مثل Double Jump مبكر بدل قفزة أرضية): أغلقه بعائق يغطي ذلك الارتفاع.
