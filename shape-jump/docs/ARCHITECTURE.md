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
│   ├── core/                     game_const.gd · palette.gd · neon.gd · soft_light.tres
│   ├── player/
│   │   ├── movement_config.gd    Resource: أرقام الحركة (ومنها Double Jump)
│   │   ├── player_motor.gd       قواعد القفز النقية (بلا Nodes): Jump/Double Jump، Coyote، Buffer، طابور اللمسات
│   │   ├── player.gd             CharacterBody2D: يطبق الـMotor، التصادم، الموت، التعويض الأفقي فوق المنصات
│   │   ├── player_visual.gd      الرسم والـAnimations (قلبة الـDJ، النواة المجوفة)
│   │   ├── player_fx.gd          Particles، الذيل، حلقة الـDJ، التحطم
│   │   └── player.tscn · default_movement.tres
│   ├── level/
│   │   ├── level.gd              جذر المستوى: ساعة المستوى، التسجيل، الـRewind، إشارات الإنذار
│   │   ├── level_data.gd         Resource: id، الاسم، الوصف، المشهد، معامل السرعة
│   │   ├── world_data.gd         Resource: رقم العالم، اسمه، قائمة LevelData، اسم العالم التالي
│   │   └── elements/
│   │       ├── hazard.gd                 قاعدة كل خطر (طبقة، Hitbox مُصغَّر، رسم خلف الكتل، cue)
│   │       ├── gate.gd                   Pulse / Sequential / Timed Opening
│   │       ├── rotating_arm.gd · crush_block.gd · prism_beam.gd · energy_field.gd
│   │       ├── rotor.gd · wall_panel.gd · spikes.gd
│   │       ├── collapsing_path.gd        ممر ينهار (StaticBody2D، يمشى عليه)
│   │       ├── block.gd · phase_block.gd · oscillator.gd (SINE / LINEAR / STEPS)
│   │       ├── hazard_art.gd · slab_art.gd · hazard_pulse.gd   رسم الأخطار
│   │       └── shard · checkpoint · finish_gate
│   ├── camera/game_camera.gd     Follow + Look-ahead + Shake + get_view_rect
│   ├── background/               Parallax إجرائي
│   ├── game/
│   │   ├── game_session.gd       State Machine + الوسيط الوحيد في مشهد اللعب
│   │   ├── progression.gd        قواعد الفتح (دوال static فوق SaveSystem)
│   │   ├── tap_input.gd          الإدخال → "tapped" / "pause_requested"
│   │   ├── score_tracker.gd      منطق النقاط النقي
│   │   └── game.tscn             المشهد الرئيسي (world = world_01.tres)
│   └── ui/                       hud · start_overlay + level_card · death_banner · level_complete_panel · pause_menu · screen_fade
├── levels/world_01/              level_01…05.tscn/.tres + world_01.tres  (مُولَّدة)
├── assets/audio/                 sfx/*.wav · ambient/void_drone.ogg · sound_library.tres
├── tools/                        (.gdignore — لا يستورده Godot)
│   ├── levelgen/                 levelgen.py · world_01.py · README.md
│   ├── audio/gen_sfx.py          توليد الأصوات
│   └── web/                      build_playtest.py + playtest_page.html (نسخة الويب للتجربة)
└── tests/
    ├── test_runner.* · test_case.gd
    ├── support/                  GameHarness · PhysicsArena · SaveSandbox · World01Routes (مُولَّد)
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
- المعرّفات: `jump`, `double_jump`, `land`, `collect`, `checkpoint`, `warning`, `slam`, `death`, `complete`, `ui_click`, و`ambient` (يبدأ مع أول مستوى على Bus "Ambient" ويتكرر).
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

---

## 10. الاختبار

130 اختبارًا (≈ 20 ثانية)، تنجح بنفس النتائج على 20 و30 و60 و144 FPS.

| الملف | ماذا يثبت |
|---|---|
| unit/test_player_motor (20) | Jump، Double Jump، حد القفزتين، Coyote، Buffer، طابور اللمسات، أقصى سرعة سقوط |
| unit/test_level_integrity | خمسة مستويات بسرعة متصاعدة، Spawn/Finish/Shards، ترتيب الـCheckpoints و≥ 1.5s أرض آمنة بعدها، حتمية `f(t)` |
| unit: audio، save، score، visual، project | كل المعرّفات موجودة، الـAmbient يتكرر، الحفظ الذري، النقاط، الـSquash، إعدادات المشروع |
| integration/test_double_jump (11) | القمة المزدوجة، جدار لا تعبره قفزة واحدة، لا قفزة ثالثة، لمستان في إطار واحد، الاستعادة بعد الهبوط، الحافة والـCoyote، سقف منخفض، Respawn وسط DJ، الهبوط على منصة متحركة |
| integration/test_obstacles (16) | خط زمن كل نوع، القتل والمرور على الفيزياء الفعلية، الإنذارات، الأصوات داخل اللعب فقط، Hitbox ≤ الرسم دائمًا |
| integration/test_player_physics (29) | الاستجابة، الارتفاع، الحواف، الفجوات، السقف، Ledge Assist، السحق، المصاعد، منصة متسارعة لا تزيح اللاعب أفقيًا |
| integration/test_world_01_playthrough | **كل مستوى يُنهى بمساره بلا موت**، الحتمية، والموت عند كل Checkpoint ثم الإنهاء بنفس التوقيت |
| integration/test_progression (6) | قواعد الفتح، وضع الاختبار `--unlock-all`، رفض المستوى المغلق، NEXT LEVEL، WORLD 01 COMPLETE، لوحة الموت وعدّاد المحاولات |
| integration: camera، game flow، review probes | الكاميرا، تدفق اللعب واللمس، عوائق تُنشأ/تُحذف أثناء اللعب، تسلسل حالات سريع |

أي خطأ يسجله المحرك أثناء اختبار يُفشله.

```bash
godot --headless --path shape-jump --import
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn [-- --filter=obstacles]
godot --headless --path shape-jump --fixed-fps 60 res://tests/tools/level_audit.tscn -- --level=5 --windows --exploits
```

ما **لا** تثبته الاختبارات: الإحساس — يحتاج Playtesting على هاتف (§16 في الـGDD).

---

## 11. نقاط التوسع (بدون تعديل البنية)

| الإضافة | أين |
|---|---|
| مستوى في World 01 | دالة جديدة في `tools/levelgen/world_01.py` + إضافتها إلى `LEVELS` |
| World 02 | ملف `world_02.py` بنفس الـAPI، و`WorldData` جديد، و`game.tscn` يأخذ `world` مختلفًا |
| نوع عائق جديد | سكربت يرث `Hazard` ويطبق `apply_time(t)`، ونسخة Hitbox مطابقة في `levelgen.py` |
| صوت جديد | ملف + سطر في `sound_library.tres` |
| Haptics / Analytics | Listener جديد على `Events` |
| Localization | ملفات ترجمة + خط عربي في الـTheme |
