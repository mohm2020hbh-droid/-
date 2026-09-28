# Shape Jump — Architecture

> مرتبط بـ [GDD.md](GDD.md). هذا الملف يشرح **كيف** نبني ما يصفه الـGDD، ولماذا اخترنا كل قرار.

---

## 1. القرارات التقنية الأساسية

| القرار | الاختيار | السبب المختصر |
|---|---|---|
| المحرك | **Godot 4.7.2 (stable)** | محرك 2D حقيقي: Physics، Camera، Particles، Audio Buses، محرر مرئي، وتصدير Android مباشر. |
| اللغة | **GDScript مع Static Typing** | اللغة الأصلية للمحرك، والـTyping يمنع فئة كاملة من الأخطاء. |
| Renderer | **Compatibility (OpenGL ES 3)** | أوسع دعم لأجهزة Android. التوهج مرسوم يدويًا فلا نحتاج Bloom. |
| فيزياء اللاعب | **CharacterBody2D + move_and_slide** | Ground detection وSnap ومنصات متحركة مدعومة في المحرك. |
| المنصات المتحركة | **AnimatableBody2D** | تحمل الـCharacterBody بشكل صحيح. |
| الأخطار / الجمع / المحفزات | **Area2D** | كشف تداخل بلا استجابة فيزيائية. |
| معدل الفيزياء | 60 Hz ثابت + **Physics Interpolation** | منطق حتمي ورسم ناعم على 90/120Hz. كل ما يحرك Transforms يفعل ذلك داخل الـPhysics Tick. |
| تأليف المستويات | **مولّد Python** (`tools/levelgen`) يكتب مشاهد `.tscn` عادية | المستويات الصعبة تحتاج ضبطًا رقميًا لنوافذ التوقيت؛ المولّد يحاكي حركة اللاعب بدقة Tick، والمشاهد الناتجة تبقى قابلة للفتح والتعديل في المحرر. |
| الحفظ | **ConfigFile** في `user://` | بسيط ومقروء ويكفي بيانات التقدم. |
| الاختبارات | **Test Runner صغير داخل المشروع** | بلا Addons، يعمل Headless بأمر واحد. |

---

## 2. هيكل المجلدات

```
shape-jump/
├── project.godot / export_presets.cfg / default_bus_layout.tres
├── docs/                         GDD + Architecture
├── src/
│   ├── autoload/                 events.gd (Signal Bus) · save_system.gd
│   ├── audio/                    audio_manager.gd (Autoload) · sound_library.gd
│   ├── core/                     game_const.gd · palette.gd (ثيمات العوالم: red / mono / galaxy / garden) · neon.gd · soft_light.tres
│   ├── gravity/gravity_state.gd  World 03: مصدر الحقيقة الوحيد لحالة الجاذبية (DOWN / UP) ومرحلتها (NORMAL / FLIPPING)
│   ├── surface/surface_run.gd    World 04: SurfaceRun — حالة الجري على السطحين (GROUND_RUN / AIRBORNE / LATCHING / CEILING_RUN …)
│   ├── player/
│   │   ├── movement_config.gd    Resource: أرقام الحركة (ومنها Double Jump، ومدى وسرعة الـLatch)
│   │   ├── player_motor.gd       قواعد القفز النقية (بلا Nodes): Jump/Double Jump أو Surface Latch، Coyote، Buffer، طابور اللمسات
│   │   ├── surface_latch.gd      World 04: SurfaceLatch.probe — هل السطح المقابل في المدى وصالح للإمساك هذا الـTick
│   │   ├── player.gd             CharacterBody2D: يطبق الـMotor، التصادم، الموت، التعويض الأفقي فوق المنصات
│   │   ├── player_visual.gd      الرسم والـAnimations (قلبة الـDJ، النواة المجوفة)؛ `void_style` = كيان World 02؛ `garden_style` = بذرة World 04
│   │   ├── player_fx.gd          Particles، الذيل، حلقة الـDJ، التحطم
│   │   └── player.tscn · default_movement.tres
│   ├── level/
│   │   ├── level.gd              جذر المستوى: ساعة المستوى، التسجيل، الـRewind، إشارات الإنذار
│   │   ├── level_data.gd         Resource: id، الاسم، الوصف، المشهد، معامل السرعة
│   │   ├── world_data.gd         Resource: رقم العالم، اسمه، قائمة LevelData، العالم التالي، `requires`، `theme`، `background`
│   │   └── elements/
│   │       ├── hazard.gd                 قاعدة كل خطر (طبقة، Hitbox مُصغَّر، رسم خلف الكتل، cue)
│   │       ├── gate.gd                   Pulse / Sequential / Timed Opening
│   │       ├── rotating_arm.gd · crush_block.gd · prism_beam.gd · energy_field.gd
│   │       ├── rotor.gd · wall_panel.gd · spikes.gd
│   │       ├── collapsing_path.gd        ممر ينهار (StaticBody2D، يمشى عليه)
│   │       ├── block.gd · phase_block.gd · oscillator.gd (SINE / LINEAR / STEPS)
│   │       ├── hazard_art.gd · slab_art.gd · hazard_pulse.gd   رسم الأخطار
│   │       ├── organic_art.gd   الأشكال العضوية لـWorlds 01-03 (CRYSTAL / INK / ROCK): حافة قريبة من الـHitbox، تُبنى مرة من Seed
│   │       ├── World 02: shadow_block · mirror_wall · orbit_ring · black_column · shadow_chaser
│   │       │            maze_panel · binary_gate · whiteout_zone · timeline (خطوات مشتركة)
│   │       ├── galaxy/ (World 03): gravity_gate · flip_field · gravity_mine · falling_asteroid · ceiling_trap
│   │       │            orbital_hazard · dual_hazard · gravity_echo · gravity_lens · galaxy_block · galaxy_art
│   │       ├── garden/ (World 04): rising_roots · sweeping_branch · closing_flower · water_wave · waterfall
│   │       │            wind_burst · falling_rock (+ ice) · hanging_vines · leaf_glider · ink_flow · canvas_curtain
│   │       │            hanging_boulder · bird_flock · garden_weather (leaves / snow) · garden_block · garden_hazard · garden_art
│   │       └── shard · checkpoint · finish_gate
│   ├── camera/game_camera.gd     كاميرا واحدة لكل العوالم: Zoom 1، Follow + Look-ahead (28%) + Shake + get_view_rect + تأطير شريط اللعب (`frame_band`) + `anchor_to` + `framing_report`؛ لا تدور أبدًا
│   ├── background/               Parallax إجرائي: background.tscn (أحمر) · background_mono.tscn (أبيض/أسود)
│   │                             background_galaxy.tscn + galaxy_backdrop.gd (مجرة مقطّعة Chunks + متحكم ألوان الجاذبية)
│   │                             background_garden.tscn + garden_backdrop.gd (حديقة مرسومة تنقلب حول أفقها) + garden_look.gd (أدوار الألوان)
│   ├── game/
│   │   ├── game_session.gd       State Machine + الوسيط الوحيد في مشهد اللعب
│   │   ├── progression.gd        قواعد الفتح (دوال static فوق SaveSystem)
│   │   ├── progress_tracker.gd   تقدّم المستوى % بالمسافة + عقوبة الموت 25 نقطة
│   │   ├── autoplay.gd           مفتاح QA (`-- --autoplay`): اللعبة تلعب نفسها بمسار كل مستوى، في العوالم الأربعة
│   │   ├── whiteout_veil.gd      طبقة الـWHITEOUT: أبيض فوق العالم + كل حافة آمنة وكل Hitbox بالأسود
│   │   ├── tap_input.gd          الإدخال → "tapped" / "pause_requested"
│   │   ├── score_tracker.gd      منطق النقاط النقي
│   │   └── game.tscn             المشهد الرئيسي (worlds = [world_01.tres … world_04.tres])
│   └── ui/                       hud · start_overlay (تبويبات العوالم) + level_card · death_banner · level_complete_panel · pause_menu · screen_fade
│                                 ui_look.gd + theme_mono.tres / theme_galaxy.tres / theme_garden.tres: لبس الواجهة بثيم العالم
├── levels/world_01/              level_01…05.tscn/.tres + world_01.tres + world_01_routes.gd (مُولَّدة)
├── levels/world_02/              نفس البنية لـWorld 02 (مُولَّدة من world_02.py)
├── levels/world_03/              نفس البنية لـWorld 03 (مُولَّدة من world_03.py)
├── levels/world_04/              نفس البنية لـWorld 04 (مُولَّدة من world_04.py)
├── assets/audio/                 sfx/*.wav · ambient/void_drone.ogg · sound_library.tres
├── tools/                        (.gdignore — لا يستورده Godot)
│   ├── levelgen/                 levelgen.py · world_01.py … world_04.py · noisy_player.py · README.md
│   ├── audio/gen_sfx.py          توليد الأصوات (gen_sfx_galaxy.py: صوت القلب وإنذاره؛ gen_sfx_garden.py: صوتا الـLatch والـMiss)
│   └── web/                      build_playtest.py + playtest_page.html (نسخة الويب للتجربة)
└── tests/
    ├── test_runner.* · test_case.gd
    ├── support/                  GameHarness · PhysicsArena · SaveSandbox
    ├── unit/ · integration/
    └── tools/level_audit.tscn    تدقيق المستويات على الفيزياء الفعلية (نوافذ، استراتيجيات كسولة)
```

## 3. فصل المسؤوليات

| النظام | المسؤول | ما لا يفعله |
|---|---|---|
| **Movement** | `player_motor.gd` + `movement_config.gd` | لا يلمس Nodes. يقرر كل Tick: لا قفزة / Jump / Double Jump، ويرجع السرعة العمودية. |
| **Player** | `player.gd` | لا يعرف Score أو UI أو Audio. يطلق `jumped` / `double_jumped` / `landed` / `died`. |
| **Visual / FX** | `player_visual.gd`, `player_fx.gd` | لا يؤثر على اللعب. |
| **Level** | `level.gd` | لا يعرف اللاعب. يحرك العناصر بالساعة، ويطلق: Shard، Checkpoint، النهاية، `obstacle_cued`. |
| **Obstacles** | `hazard.gd` وأبناؤه | بيانات + شكل + Hitbox + `apply_time(t)`. لا منطق موت داخلها (الـHurtbox هو من يرى الطبقة). |
| **Progression** | `progression.gd` + `WorldData` | لا حالة خاصة: يقرأ `SaveSystem` فقط. |
| **Camera** | `game_camera.gd` | يتبع هدفًا ويهتز عند الطلب. |
| **UI** | `src/ui/*` | لا منطق لعب: يعرض قيمًا ويطلق Signals (level_chosen، next، retry، resume، restart). |
| **Audio** | `audio_manager.gd` | الوحيد الذي يعرف أي صوت لأي حدث. |
| **Game State** | `game_session.gd` | الوسيط الوحيد: يستقبل Signals ويستدعي الأنظمة. |
| **Save** | `save_system.gd` | API صغيرة: `record_result` / `get_record`، كتابة ذرية عبر ملف مؤقت. |

### العوالم والثيمات
- `GameSession.worlds` قائمة `WorldData`. `_use_world(i)` يبدّل كل شيء مرة واحدة قبل بناء المستوى: `Palette.use(world.theme)` (كل الألوان static)، الخلفية (`world.background`)، `player.visual.void_style`، ألوان الـParticles والذيل (`PlayerFx.refresh_colors`)، الجمر، والواجهة (`UiLook.apply` + `theme_mono.tres`).
- الفتح: العالم مفتوح إن كان `requires == null` أو اكتمل العالم المطلوب. بعد آخر مستوى، **NEXT** يفتح العالم التالي (`_play_next`).
- نقطة الاستئناف في الويب (`--resume`) صارت 7 حقول: العالم، المستوى، الـCheckpoint، النسبة، المحاولات، الـTiles، الـShards.

### قاعدة التواصل
- **Signals للأعلى، استدعاءات للأسفل.**
- **Events Bus** يطلقه `GameSession` حصرًا للأنظمة العامة (Audio). إنذارات العوائق تمر: العنصر ← `Level.report_cue` ← `obstacle_cued` ← `GameSession` (يتحقق أن المصدر داخل الكاميرا) ← `Events.obstacle_warning / obstacle_slam`.
- **كل الإدخال** يمر عبر `GameSession.press_jump()`، واللاعب الآلي في الاختبارات يستدعي نفس الدالة.

---

## 4. تدفق البيانات في مشهد اللعب

```
                 ┌──────────────────────── GameSession (State Machine) ────────────────────────┐
  Touch/Click →  │ TapInput.tapped ─► press_jump ─► READY: start_run()   PLAYING: request_jump  │
  Level card  →  │ StartOverlay.level_chosen ─► play_level(i)  (إن كان مفتوحًا)                │
                 │                                                                              │
                 │  Player.jumped/double_jumped/landed/died ─┐   Level.shard_collected ───┐      │
                 │                                           ▼                             ▼      │
                 │                   Events.emit(...) → AudioManager       ScoreTracker → HUD     │
                 │  died → DeathBanner + shake → fade → level.rewind_to(t) → player.respawn_at(p) │
                 │  finish → SaveSystem.record_result → LevelCompletePanel (Next / Retry)         │
                 └──────────────────────────────────────────────────────────────────────────────┘
```

### Game State Machine
```
READY (اختيار المستوى) ──tap──► PLAYING ──died──► DYING ──(تحطم + fade)──► PLAYING
                                   │  ▲
                            pause  │  │ resume
                                   ▼  │
                                  PAUSED

PLAYING ──finish──► COMPLETE ──NEXT LEVEL──► READY (المستوى التالي)
                             └──RETRY──────► READY (نفس المستوى)
```
الـHUD مخفي في READY. `play_level(i)` يرفض أي مستوى مغلق.

### Player States
`IDLE → RUN → JUMP → FALL → RUN ... → DEAD`. الـDouble Jump **حدث** (`double_jumped`) وليس حالة: الجسم يبقى JUMP/FALL، والرسم يقرأ `has_double_jump()`.

### قرار القفز داخل الـMotor (كل Physics Tick)
1. على الأرض: تُستعاد القفزة الهوائية ويُعاد ضبط الـCoyote.
2. إن كانت هناك لمسة في الطابور: أرضية إن كان على الأرض أو داخل الـCoyote، وإلا هوائية إن بقيت واحدة، وإلا تذهب إلى الـBuffer.
3. إن لم تكن: الـBuffer يُنفَّذ قفزةً أرضية عند الهبوط.
4. قفزة واحدة على الأكثر لكل Tick؛ اللمسات الزائدة عن القفزات المتاحة لا تدخل الطابور بل تُدمج في خانة الـBuffer الواحدة.

---

## 5. ساعة المستوى (Level Clock) — لماذا هي قلب الحتمية

- `Level.clock` يتقدم بـ`delta` الفيزياء فقط أثناء اللعب، وكل عنصر زمني يطبق `apply_time(t)` كدالة نقية.
- العناصر **تسجّل نفسها** (`Level.join` / `Level.leave`)، فالعوائق التي تُنشأ أو تُحذف أثناء اللعب تعمل كالموضوعة في المحرر. `Level.of(element)` يجد مستوى العنصر.
- `rewind_to(t)`: كل العناصر تعود لنفس الطور، الـShards المجمعة بعد `t` تعود، وتُعاد ضبط الـInterpolation للعناصر المتحركة حتى لا "تنزلق" بصريًا.
- **Checkpoint على شبكة الـTicks:** `GameSession.time_at(x)` يقرّب زمن الـCheckpoint إلى أقرب Tick، و`respawn_feet_at` يضع اللاعب في الموضع المقابل لذلك الـTick بالضبط. بدون ذلك يعود اللاعب بفارق جزء من Tick، فتتغير توقيتات كل ما بعده (اكتُشف كحلقة موت في Level 02).

ترتيب التنفيذ في كل Tick: `Level` (−10) ← `Player` (0) ← `GameCamera` (+10) عبر `process_physics_priority`.

### الجاذبية (World 03)
- **مصدر حقيقة واحد:** `GravityState` (RefCounted) يملكه `GameSession`، ويقرؤه `Player` و`GameCamera` و`Level` وعناصر المجرة والخلفية. `up` (DOWN / UP)، `phase` (NORMAL / FLIPPING، 0.4 ث)، `down_sign()`، وإشارة `flipped(up, instant)`. `set_up` بلا أثر إن لم تتغير القيمة: لا قلب مكرر.
- **الجاذبية دالة نقية للساعة:** مركز اللاعب `x = origin + speed·t` (سرعة الجري ثابتة)، فالبوابة عند x تقلب في زمن ثابت. `Level.gravity_up_at(t)` / `last_gravity_change(t)` / `next_gravity_change(t)` من أحداث البوابات والحقول (`gravity_events()`)، و`GameSession` يضبط خط الجري (`set_run_line`). `rewind_to` يعيد الجاذبية الصحيحة فورًا، والـRespawn يأخذها من الجدول (لا من الحالة الحية: الاستئناف بعد فقد سياق WebGL يصل للـCheckpoint من بداية المستوى).
- **اللاعب:** الـMotor يعمل في إطار محلي (+y نحو السطح الحالي)؛ `Player` يحوّل بـ`g = down_sign()` ويضبط `up_direction`. عند القلب: نفس السرعة في العالم (`motor.flip()` يعكسها محليًا)، لا أرض ولا Coyote، الـBuffer يسقط، اللمسات في الطابور تبقى، والـDouble Jump يبقى. الرسم ينقلب عموديًا (`PlayerVisual`/`PlayerFx`: `scale.y = cos(π·k)`، ليس دورانًا: لا Mirror أفقي أبدًا)؛ جسم التصادم لا يدور أبدًا.
- **الكاميرا:** لا تدور (كل العوالم): اللعب من اليسار لليمين دائمًا. الإزاحة العمودية وحدود السقوط وخطا الموت (`kill_y` / `kill_top`) كلها بـ`down_sign()`؛ عند القلب (`flipped` غير فوري) تنتقل المرساة إلى الجهة الأخرى من الشريط (`band_height − 2·half`) فيبقى مركز الإطار مكانه. الـHUD في CanvasLayer لا يدور.
- **الشكل:** `galaxy_backdrop.gd` يمزج حالتي الألوان (GROUND أزرق/بنفسجي، CEILING برتقالي/كهرماني) على زمن الانتقال: السماء، الطبقات، `level.modulate`، الجمر؛ مع حلقة ضوء عند اللاعب ونبضة شاشة خفيفة. الأخطار ماجنتا ثابتة (`GalaxyArt.DANGER`) مقروءة في الحالتين.

### السطح (World 04: Surface Latch)
- **مفتاح واحد:** `WorldData.surface_latch` → `Level.surface_latch` و`Player.set_surface_latch` (يضبط `motor.air_action = SURFACE_LATCH`). في العوالم الأخرى المفتاح مطفأ والسلوك كما كان حرفيًا (Double Jump، والجاذبية من البوابات).
- **القرار داخل الـMotor:** اللمسة الهوائية في وضع الـLatch تستهلك `air_jumps_left` مثل الـDJ، لكن نتيجتها `Jump.LATCH` (قلب السرعة محليًا ثم `latch_speed` نحو السطح الجديد، `latching = true`، مسح طابور اللمسات) أو `Jump.LATCH_MISS` (لا شيء يتحرك ولا Teleport؛ اللمسة تنتظر في الـBuffer، فالـMiss قبيل الهبوط يصير قفزة عند الهبوط كأي لمسة مبكرة). `latching` ينتهي بالهبوط؛ أثناءه لا لمسات ولا رياح (`external_accel`).
- **الفحص:** `SurfaceLatch.probe(can_latch)` كل Tick في `Player._physics_process` قبل `motor.begin_tick(on_floor, delta, in_reach)`: `test_move` نحو `up_direction` بطول `latch_reach`، الوجه يواجه اللاعب (`normal·up < −0.7`)، `latchable != false`، ونقطة الوصول بعد `latch_ticks(distance)` على نفس السطح (±2 px).
- **الجاذبية يقودها اللاعب:** عند `Jump.LATCH` يقلب `Player` الـ`GravityState` بنفسه (`_latch_turning` يمنع قلب السرعة مرتين)، و`GameSession._on_player_latched` يسجل السطح في سجل المحاولة (`Level.record_surface(up)` بزمن الساعة) ويثبت الكاميرا (`camera.anchor_to(landing.y)`). `Level.surface_up_at(t)` يعطي أرضية أي لحظة من السجل (حدث عند t يسري بعد t)، فعناصر "أرضية اللحظة" (`GardenHazard.Anchor.FLOOR / SKY`) تبقى دوال نقية للساعة. `Level._apply_time` لا يكتب الجاذبية في World 04.
- **Respawn:** `level.reset_surface(respawn.gravity_up)` قبل `rewind_to` (سجل جديد من سطح الـCheckpoint)، ثم `gravity.set_up(..., true)`، فالعالم والألوان واللاعب والكاميرا تعود كلها لحالة ذلك السطح. `GameSession.checkpoint_hangs(marker)` يقرر سطح الـCheckpoint من دورانه.
- **الكاميرا:** نفس الكاميرا المشتركة (لا دوران ولا Mirror)؛ الإزاحة العمودية بـ`down_sign()` فالممر يبقى في منتصف الشاشة؛ `anchor_to(landing.y)` بعد الـLatch؛ اللعب دائمًا من اليسار لليمين.
- **الألوان:** `GardenLook` يربط أدوار العقد (`garden_ink`، `garden_soil`، `garden_bloom`، `garden_water`، `garden_leaf`، `garden_mark`، `garden_shard`) بلوني الحالتين ويمزجها بـ`self_modulate` (والـShards بـ`modulate`)؛ `garden_backdrop.gd` يقود المزج على زمن القلب ويقلب المنظر عموديًا (`pivot.scale.y = cos(π·blend)`) مع حلقة وتموّج بلا وميض.
- **الرياح:** `Level.wind_at(x)` يجمع `wind(x, t, up)` من عناصر `WindBurst`، و`Player.wind_source` يمررها للـMotor كتسارع عمودي بالنسبة لأرضية اللاعب (x(t) خطي دائمًا: لا دفع أفقي).


**سرعة جري ثابتة:** `Player` يطرح سرعة الأرضية الأفقية من حركته. السرعة تُقرأ من `PhysicsServer2D.body_get_direct_state` للجسم الذي يقف عليه (السرعة **الحالية** في نقطة التلامس)، لا من `get_platform_velocity()` التي تتأخر Tick على المنصات المتسارعة. بذلك يبقى `x(t)` خطيًا تمامًا، والمسار (قائمة مواضع اللمس) يصف حلًا كاملًا للمستوى.

---

## 6. طبقات الفيزياء

| # | الاسم | من عليها | من يراقبها |
|---|---|---|---|
| 1 | `world` | Blocks، منصات متحركة، بلاطات الانهيار | جسم اللاعب |
| 2 | `player` | جسم اللاعب | Shards، Checkpoints، Finish |
| 3 | `hazard` | كل أبناء `Hazard` | Hurtbox اللاعب فقط |
| 4 | `pickup` | Shards | — |
| 5 | `trigger` | Checkpoints، Finish | — |

---

## 7. نظام الصوت

```
GameSession ──► Events.player_double_jumped ──► AudioManager ──► SoundLibrary["double_jump"] ──► Pool(8) على "SFX"
```
- المعرّفات: `jump`, `double_jump`, `land`, `collect`, `checkpoint`, `warning`, `slam`, `death`, `complete`, `ui_click`, `gravity_flip`, `gravity_warning` (World 03)، `latch`, `latch_miss` (World 04)، و`ambient` (يبدأ مع أول مستوى على Bus "Ambient" ويتكرر).
- إضافة صوت = ملف + سطر في `sound_library.tres` + ربط حدث في `AudioManager`.

---

## 8. نظام الحفظ

`user://save.cfg` (ConfigFile)، قسم لكل معرّف مستوى:
```ini
[meta]
version=1
[w01_l01]
best_score=5830
best_shards=36
completed=true
```
الفتح مشتق من `completed` (`Progression.is_unlocked`)، و"World 02 مفتوح" = اكتمال كل مستويات World 01. الكتابة إلى ملف مؤقت ثم استبدال، مع استرداد إن انقطعت الكتابة.

---

## 9. الأداء (ميزانية الهاتف المتوسط)

- رسم العناصر يتم **مرة واحدة**؛ الحركة تغيّر Transforms فقط (ألواح البوابات وكتل السحق `SlabArt`، والـShards `ShardGem`).
- لا `instantiate()` أثناء اللعب: كل الـParticles موجودة مسبقًا وتُعاد.
- لا Post-Processing؛ الخلفية إجرائية بلا Textures كبيرة.
- الأخطار لا تراقب شيئًا (`monitoring = false`): الـHurtbox وحده يفحص طبقتها.
- خلفية World 03 طبقات ملفوفة مقطّعة إلى Chunks بعرض 1024 px، كل Chunk عنصر رسم مستقل يُرسم مرة واحدة، فيستبعد الـRenderer ما خارج الشاشة (من 952 Draw Call إلى ≈ 340، ومن 20,880 Primitive إلى ≈ 4,000). كل تغيير الجاذبية يغيّر `modulate` وTransforms فقط؛ السماء وحدها تُعاد رسمًا أثناء الانتقال. لا Render Targets ولا Shaders ولا Textures جديدة.
- World 04: الحديقة تُرسم مرة واحدة بألوان محايدة وتُلوَّن بـ`modulate`؛ الأزهار والأشجار والأعشاب مجمّعة في دفعات (`GardenArt.Batch`) فانخفض أول نموذج من 753 Draw Call و59,000 Primitive إلى 100–280 Draw Call و6,000–20,000 Primitive أثناء اللعب (1280×720)، والذاكرة ≈ 27 MB VRAM ثابتة عبر المستويات الخمسة والـRestarts. الطقس (أوراق/ثلج) ≤ 40 شكلًا ولا يُعاد رسمه خارج الشاشة. لا Shaders ولا Render Targets ولا Textures.

---

## 10. الاختبار

253 اختبارًا (≈ 135 ثانية)، تنجح بنفس النتائج على 20 و60 FPS. اختبارات المستويات تعمل على العوالم الأربعة: كل ملف لـWorld 01 له ابن `…_w02` / `…_w03` / `…_w04` / `test_world_0N_playthrough` يغيّر `world_index()` فقط (وفي World 04 يُفرض الموت بـ`GameHarness.kill_at` حيث كانت اللمسة، لأن لمسة لاحقة قد تنوب عن لمسة متروكة).

| الملف | ماذا يثبت |
|---|---|
| unit/test_player_motor (20) | Jump، Double Jump، حد القفزتين، Coyote، Buffer، طابور اللمسات، أقصى سرعة سقوط |
| unit/test_level_integrity | خمسة مستويات بسرعة متصاعدة، Spawn/Finish/Shards، ترتيب الـCheckpoints و≥ 1.5s أرض آمنة بعدها، حتمية `f(t)` |
| unit: audio، save، score، visual، project | كل المعرّفات موجودة، الـAmbient يتكرر، الحفظ الذري، النقاط، الـSquash، إعدادات المشروع |
| integration/test_double_jump (11) | القمة المزدوجة، جدار لا تعبره قفزة واحدة، لا قفزة ثالثة، لمستان في إطار واحد، الاستعادة بعد الهبوط، الحافة والـCoyote، سقف منخفض، Respawn وسط DJ، الهبوط على منصة متحركة |
| integration/test_obstacles (16) | خط زمن كل نوع، القتل والمرور على الفيزياء الفعلية، الإنذارات، الأصوات داخل اللعب فقط، Hitbox ≤ الرسم دائمًا |
| integration/test_player_physics (29) | الاستجابة، الارتفاع، الحواف، الفجوات، السقف، Ledge Assist، السحق، المصاعد، منصة متسارعة لا تزيح اللاعب أفقيًا |
| integration/test_world_01_playthrough · _02 · _03 · _04 | **كل مستوى في العوالم الأربعة يُنهى بمساره بلا موت**، الحتمية، والموت عند كل Checkpoint ثم الإنهاء بنفس التوقيت (وبجاذبية ذلك الـCheckpoint) |
| unit/test_level_integrity_w02 | ثيم `mono` وخلفيته، يُفتح بـWorld 01، لا عائق من World 01، وكل أنظمة World 02 مستخدمة |
| unit/test_level_integrity_w03 (9) | ثيم `galaxy` وخلفيته، يُفتح بـWorld 02 ويفتح World 04، لا عائق من العالمين السابقين وكل عناصر المجرة مستخدمة، جدول الجاذبية يتبدل فعلًا ويزداد، كل Checkpoint والنهاية على أرضية لحظتها (مقلوبة على السقف)، خطا الموت وتأطير الممر |
| integration/test_gravity_flip (17) | القلب على الفيزياء الفعلية: الهبوط على السقف والعودة، القفز وارتفاعه مقلوبًا، الحالات A–D، لا Coyote ولا Buffer عبر القلب، لمستان في Tick، 24 قلبًا متتاليًا، خط الموت العلوي، Respawn على السقف، الرسم ينقلب عموديًا بلا Mirror والجسم لا يدور، القلب عند حافة منصة وفوق منصة متحركة، إدخال سريع جدًا، زاوية سقف (Ledge Assist مقلوب) |
| integration/test_world_03_gravity (11) | على اللعبة الكاملة: الكاميرا لا تدور ولا تتحرك عند القلب (< 8 px)، اللاعب لا يُعكس أفقيًا ويتقدم لليمين، والـHUD ثابت، البوابة تقلب عند x نفسه قفزت أم لا، الموت أثناء القلب، الموت بعد Checkpoint على السقف وعلى الأرض (الجاذبية والكاميرا والألوان)، Restart أثناء القلب، 10 Restarts متتالية بلا تسرب، Pause أثناء القلب، 100% على السقف والتقدم لا يتراجع، الاستئناف بعد فقد WebGL على السقف، World 02 → World 03، وWORLD 03 COMPLETE يفتح World 04 |
| unit/test_level_integrity_w04 (9) | ثيم `garden` وخلفيته، يُفتح بـWorld 03 وهو الأخير، كل مستوى `surface_latch`، لا عائق من العوالم السابقة وكل عناصر الحديقة مستخدمة، كل Checkpoint والنهاية حيث لا يوجد إلا سطحهما، العبورات تزداد، ترتيب الـCheckpoints ومدرجها الآمن، الحتمية |
| unit/test_garden_elements (5) | كل عنصر يُبنى ويعمل على السطحين، دوال نقية للزمن وسجل السطوح، Latch في منتصف الدورة لا ينقل عنصرًا عبر الممر، قراءة السجل مطابقة للمولّد، الرياح داخل منطقتها وأثناء هبوبها فقط |
| integration/test_surface_latch (16) | الـLatch على الفيزياء الفعلية: قرب القمة إلى السقف ومن السقف إلى الأرض، العبور سريع ويحفظ سرعة الجري، الـMiss المبكر مستهلك، لمستان في إطار واحد، 12 عبورًا متتاليًا، سقف أعلى من المدى، اللمسات أثناء العبور تُبتلع، سطح ينتهي قبل الوصول، كتلة في الطريق، سطح أملس = Miss، Miss قبيل الهبوط يقفز عند الهبوط، سقف متحرك، الرياح ترفع القفزة لا الـLatch، الموت أثناء العبور |
| integration/test_surface_attach_ceiling (10) | من السقف على الفيزياء الفعلية: CEILING_RUN ← AIRBORNE ← LATCHING ← GROUND_RUN بلا Double Jump ولا Teleport، اللمسة الثانية بعد 1..43 Tick تلتصق فقط إن كانت الأرض في المدى، الضغط المتواصل (لا قفزة ثالثة ولا Latch ثانٍ)، مباشرة بعد الوصول للسقف، لمسة في Tick الهبوط، اللمسات أثناء العبور، أرض تنتهي قبل الوصول، لا أرض (حفرة)، كتلة في الطريق، الموت والـRespawn على السقف؛ وفي كل Tick: x يزيد، `velocity.x > 0`، لا Mirror ولا دوران |
| integration/test_camera_framing (2) | الكاميرا مقاسة أثناء اللعب في W01 وW02 وW03 وW04 L1 وW04 L5: Zoom 1، لا دوران، نفس حجم اللاعب ونفس الـLook-ahead، اللاعب عند 28%، خط الأرضية عند 72-76% من الارتفاع؛ وعلى السقف (W03 وW04) الإطار نفسه معكوسًا عموديًا |
| unit/test_tap_input (3) | لمسة واحدة لكل ضغطة: الإفلات لا يُحسب، والضغط المطوّل (تكرار المفتاح) لا يُحسب، والنقرة المحاكاة من اللمس لا تُحسب |
| integration/test_world_04_surface (16) | على اللعبة الكاملة: لا دوران ولا Mirror واللعب من اليسار لليمين في كل Tick، الإطار لا يتبع قوس العبور، تبدّل ألوان العالم واللاعب، أسماء حالات `SurfaceRun`، الـMiss بلا حركة، الضغط المتواصل، الموت والـRestart والـPause أثناء الـLatch، Respawn على Checkpoint سقف وأرض، الاستئناف بعد فقد WebGL على السقف، آخر Latch ثم 100%، World 03 → World 04 وWORLD 04 COMPLETE، 10 Restarts بلا تسرب، اللمس أثناء الموت |
| unit/test_progress_tracker (5) · integration/test_level_progress (2) | التقدّم بالمسافة 0..100، العقوبة 25 نقطة وحدّ الصفر، Checkpointان قرب 33% و66% في كل مستوى، وفي كل مستوى: موتان (≈50% و≈80%) → عقوبة → عودة عند آخر Checkpoint بحالة نظيفة → إنهاء بـ100% |
| integration/test_progression (7) | قواعد الفتح، وضع الاختبار `--unlock-all`، رفض المستوى المغلق، NEXT LEVEL، WORLD 01 COMPLETE، لوحة الموت وعدّاد المحاولات |
| integration: camera، game flow، review probes | الكاميرا، تدفق اللعب واللمس، عوائق تُنشأ/تُحذف أثناء اللعب، تسلسل حالات سريع |

أي خطأ يسجله المحرك أثناء اختبار يُفشله.

```bash
godot --headless --path shape-jump --import
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn [-- --filter=obstacles]
godot --headless --path shape-jump --fixed-fps 60 res://tests/tools/level_audit.tscn -- --world=2 --level=5 --windows --exploits
godot --headless --path shape-jump --fixed-fps 60 -- --autoplay --autoplay-die=50,80 --autoplay-quit   # العالمان كاملين بموتين في كل مستوى
godot ... -- --autoplay --autoplay-world=4 --autoplay-one-world --autoplay-quit                          # World 04 وحده
```
صفحة الويب تشغّل نفس الـAutoplay بإضافة `#autoplay` أو `#autoplay-deaths` (أو `-w2` / `-w3` / `-w4` لعالم واحد، و`#stress-w4` لأربع دورات) إلى رابطها، وتطبع الأحداث في Console المتصفح.

ما **لا** تثبته الاختبارات: الإحساس — يحتاج Playtesting على هاتف (§16 في الـGDD).

---

## 11. نقاط التوسع (بدون تعديل البنية)

| الإضافة | أين |
|---|---|
| مستوى في World 01 | دالة جديدة في `tools/levelgen/world_01.py` + إضافتها إلى `LEVELS` |
| World 05 | ملف `world_05.py` بنفس الـAPI، و`WorldData` (`requires = world_04`، `theme`، `background`)، وإضافته إلى `worlds` في `game.tscn` و`Autoplay.route_for` (وتعبئة `next_world_name` في World 04) |
| نوع عائق جديد | سكربت يرث `Hazard` ويطبق `apply_time(t)`، ونسخة Hitbox مطابقة في `levelgen.py` |
| صوت جديد | ملف + سطر في `sound_library.tres` |
| Haptics / Analytics | Listener جديد على `Events` |
| Localization | ملفات ترجمة + خط عربي في الـTheme |
