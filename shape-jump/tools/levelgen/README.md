# Level Generator — World 01 · World 02 · World 03 · World 04

أداة تطوير (Python 3، بلا مكتبات خارجية) تكتب مستويات العوالم الأربعة وتتحقق منها. المجلد `tools/` يحتوي `.gdignore`، فلا يستورده Godot ولا يدخل في التصدير.

| الملف | الدور |
|---|---|
| `levelgen.py` | الـAPI: الكتل، العوائق (نسخ مطابقة لـHitboxes الـGDScript)، محاكاة حركة اللاعب Tick بـTick، الضبط، التقارير، كتابة المشاهد |
| `world_01.py` | مستويات World 01 الخمسة كمقاطع (Groups) مقروءة، وكتابة `world_01.tres` والمسارات |
| `world_02.py` | مستويات World 02 الخمسة (The Monochrome Void)، و`world_02.tres` (ثيم `mono`، خلفية `background_mono.tscn`، يُفتح بإنهاء World 01) و`world_02_routes.gd` |
| `world_03.py` | مستويات World 03 الخمسة (The Horrifying Galaxy)، مبنية من "Beats" بقلب الجاذبية، و`world_03.tres` (ثيم `galaxy`، خلفية `background_galaxy.tscn`، يُفتح بإنهاء World 02) و`world_03_routes.gd`. `--load` يطبع حمل التوقيت (bit/s) |
| `world_04.py` | مستويات World 04 الخمسة (The Inverted Garden)، مبنية من Beats بالـHop (لمسة) والـSurface Attach (TAP + TAP) وخطة سطوح (`Plan`)، و`world_04.tres` (`surface_latch = true`، ثيم `garden`، خلفية `background_garden.tscn`، يُفتح بإنهاء World 03، العالم الأخير) و`world_04_routes.gd`. يطبع حمل التوقيت والأفعال (H قفزة قصيرة، A عبور) |
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

## World 04: TAP = Hop · TAP + TAP = Surface Attach في المحاكاة

- **`latch_mode`:** المسار قائمة **كل** اللمسات: الـHop لمسة واحدة (`lv.hop(x)`)، والعبور لمستان (`lv.attach(x, gap=6)`: الأولى قبل الثانية بـ`gap` Tick). المحاكاة تتبع قواعد `PlayerMotor` حرفيًا: لمسة على سطح → Hop في نفس الـTick (`V_HOP`، وجاذبية World 04 هي جاذبية الـHop `G_HOP_UP` / `G_HOP_DOWN`) وتفتح نافذة `ATTACH_WINDOW` = 0.3 ث؛ لمسة ثانية فيها → طلب عبور يُفحص بـ`attach_probe` (مثل `SurfaceAttach.probe`: يمشي مسار العبور الحقيقي من موضع الـHop حتى `ATTACH_REACH` = 360 px، وأول ما يلمسه يجب أن يكون وجه سطح قابل للإمساك)؛ نجاح = عبور بلا جاذبية يتسارع من 1000 إلى 1800 px/s ويقلب الإطار عند اللمس (`"arrive"`)، فشل = الـHop مستمر؛ لمسة في الهواء بلا إيماءة مفتوحة → Buffer (حتى لمستين) يُلعب عند الهبوط. العوائق المثبتة على `FLOOR` / `SKY` تقرأ أرضية اللحظة من سجل السطوح كما في `Level.surface_up_at`.
- **العبور حركة واحدة:** النوافذ (`_survival_window`، `windows`)، والتوسيط (`recenter_route`، ينقل اللمسة الأمامية أولًا)، والضرورة (`necessity`: العبور يُحذف كاملًا، ويُقصّ إلى الـHop وحده — كلاهما يجب أن يموت)، واللاعب البشري (`noisy_player.py`) تحرك لمستي العبور معًا (`_partner` / `_shift`). `kinds`: اللمسة الأولى "hop" والثانية "attach"؛ `actions()` يعطي المسار كأفعال.
- **بداية من Checkpoint = Respawn:** (x، الارتفاع، السطح) وسجل جديد، والزمن مقرّب لشبكة الـTicks؛ نقاط البداية وقطع الـCheckpoints لا تقع قرب أي لمسة (`gesture_lead()` = نافذة الإيماءة كاملة).
- **الأفق:** في World 04 لا يقل عن 6 خانات بعد اللمسة، و`_lives` يعدّ السقوط بعيدًا عن السقف موتًا (حافة السقف يُرى السقوط منها متأخرًا).
- **خطة السطوح (`Plan`):** السطح المقابل موجود فقط حول العبورات التي تحتاجه (`early` قبل الوصول)، والسطح المتروك يُقطع بعد `late` من اللمسة الثانية، فكل عبور ضروري وبين العبورات لا سطح مقابل (TAP TAP هناك = Hop فقط). `early` / `late` صغيران في المستويات الصعبة: الـHop يمنح اللمسة الثانية وقتًا إضافيًا بعد الحافة، فالهامش الهندسي هو ما يضيّق النافذة.
- **مفاتيح الصعوبة:** `ms_scale` لكل مستوى (يضرب كل أهداف النوافذ)، أهداف `tune` لكل Beat، عرض الفجوات (`hop_gap` يحسبه من النافذة المطلوبة: كلما اتسعت الفجوة ضاقت لحظة الإقلاع)، الهامش، والمسافات بين الـBeats.

| الدالة | العنصر |
|---|---|
| `garden_block` / `garden_mover` | كتل الحديقة (أرض/سقف، `latchable=False` = حجر أملس؛ في `world_04.py`: `Band.slicken(x0, x1)` و`Band.cut`) وسقف متحرك |
| `roots` · `branch` · `flower` · `wave` · `waterfall` · `wind` · `rock` (`ice=True`) · `vines` · `glider` · `ink` · `curtain` · `boulder` · `flock` | العوائق، بمرساة `GROUND` / `CEILING` / `FLOOR` (أرضية اللاعب عند بداية الدورة) / `SKY` (المقابل لها) |
| `weather(x0, x1, "leaves" / "snow")` | طقس زينة بلا تصادم |
| `hop(x)` · `attach(x, gap=6)` | Hop يجب أن يبقى Hop · إيماءة يجب أن تنتهي بعبور |
| `guard_attach(element, hop_x, reach)` | يختار طور عنصر يحرس السطح المقابل فوق Hop: أي TAP TAP مكانه (اللمسة الثانية بعد 3..17 Tick) إما يموت أو لا يجد سطحًا |

وفي `world_04.py` Beats جاهزة، كل واحد فعل واحد:
- **Hop** (على سطحك): `hop_roots` (جذور صغيرة، خانة واحدة)، `hop_gap` (فجوة ثابتة: عرضها هو التوقيت)، `hop_ink` (حبر رقيق يزحف نحوك)، `hop_wave` (موجة منخفضة تتدحرج نحوك)، `hop_pinch` (جذور صغيرة وزهرة تنغلق على السطح المقابل: الـHop وحده ينجو).
- **Attach:** `roots_here` و`wave_here` (جذور 2.1 خانة وموجة 100 px على سطحك: لا يقفزها Hop)، `flower_across` و`ink_across` (خطر على المقابل: اعبر بعده)، `vines_across` و`curtain_through`، `branch_through` و`boulder_through` و`flock_through` و`glider_through` و`rock_through` و`waterfall_through`، `slick_wait`، `moving_ceiling` (عبوران).
- **مركّب:** `hop_attach` (Hop فوق جذور صغيرة ثم عبور من الـHop نفسه بلمسة ثانية متأخرة، 12 Tick).

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
