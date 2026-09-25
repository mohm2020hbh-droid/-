# Shape Jump — Architecture

> مرتبط بـ [GDD.md](GDD.md). هذا الملف يشرح **كيف** نبني ما يصفه الـGDD، ولماذا اخترنا كل قرار.

---

## 1. القرارات التقنية الأساسية

| القرار | الاختيار | السبب المختصر |
|---|---|---|
| المحرك | **Godot 4.7.2 (stable)** | محرك 2D حقيقي مجاني: Physics، Camera، Particles، Audio Buses، محرر مرئي للمستويات، وتصدير Android مباشر. بناء محرك خاص بـKotlin كان سيعني كتابة وصيانة كل هذا يدويًا. |
| اللغة | **GDScript مع Static Typing** | اللغة الأصلية للمحرك، أسرع دورة تطوير، والـTyping يمنع فئة كاملة من الأخطاء. |
| Renderer | **Compatibility (OpenGL ES 3)** | أوسع دعم لأجهزة Android المتوسطة والضعيفة. التوهج يُرسم يدويًا فلا نحتاج Bloom مكلفًا. |
| فيزياء اللاعب | **CharacterBody2D + move_and_slide** | Ground detection وSnap ومنصات متحركة مدعومة ومختبرة في المحرك. لا حاجة لـRigidBody (غير قابل للتحكم الدقيق). |
| المنصات المتحركة | **AnimatableBody2D** | صُممت لتحريك أجسام صلبة تحمل الـCharacterBody بشكل صحيح. |
| الأخطار / الجمع / المحفزات | **Area2D** | كشف تداخل بدون استجابة فيزيائية. |
| معدل الفيزياء | 60 Hz ثابت + **Physics Interpolation** | منطق حتمي، ورسم ناعم على شاشات 90/120Hz. (تم التحقق تجريبيًا أن الكاميرا تُستوفى بشكل صحيح.) |
| الشاشة | 1280×720 أساس، `canvas_items` + `expand`، Landscape | منطقة التصميم تظهر دائمًا كاملة؛ الشاشات الأعرض ترى أكثر أمامها. |
| الحفظ | **ConfigFile** في `user://` | بسيط، مقروء، مدمج في المحرك، يكفي لبيانات تقدم صغيرة. |
| الاختبارات | **Test Runner صغير داخل المشروع** | بدون Addons خارجية، يعمل Headless في أي CI بأمر واحد. الانتقال لـGUT لاحقًا سهل لأن الاختبارات دوال `test_*` عادية. |

---

## 2. هيكل المجلدات

```
shape-jump/
├── project.godot               إعدادات المشروع، Input Map، أسماء طبقات الفيزياء، Autoloads، Theme
├── export_presets.cfg          تصدير Android جاهز (يستثني tests/ و docs/)
├── default_bus_layout.tres     Master / SFX / Ambient
├── docs/                       GDD + Architecture
├── src/
│   ├── autoload/               خدمات عامة (Autoload) بلا منطق لعب
│   │   ├── events.gd           Signal Bus للأحداث العابرة للأنظمة
│   │   └── save_system.gd      الحفظ والتحميل
│   ├── audio/
│   │   ├── audio_manager.gd    (Autoload) يربط الأحداث بالأصوات، Pool من 8 مشغلات
│   │   └── sound_library.gd    Resource: معرّف → ملف صوت
│   ├── core/                   ثوابت ومساعدات بلا حالة
│   │   ├── game_const.gd       حجم الـTile + أرقام طبقات الفيزياء
│   │   ├── palette.gd          الألوان (مصدر واحد للهوية البصرية)
│   │   ├── neon.gd             دوال رسم الحواف المضيئة والتوهج
│   │   └── soft_light.tres     تدرج دائري للتوهج
│   ├── player/
│   │   ├── movement_config.gd  Resource: كل أرقام الحركة القابلة للضبط
│   │   ├── default_movement.tres
│   │   ├── player_motor.gd     منطق الحركة النقي (بلا Nodes) ← قابل للاختبار
│   │   ├── player.gd           CharacterBody2D: يطبق الـMotor، التصادم، الحالات
│   │   ├── player_visual.gd    الرسم والـAnimations فقط (الدوران هنا فقط)
│   │   ├── player_fx.gd        Particles والذيل الضوئي وحلقة الموت
│   │   └── player.tscn
│   ├── level/
│   │   ├── level.gd            جذر أي مستوى: ساعة المستوى، الـSpawn، الـCheckpoints
│   │   ├── level_data.gd       Resource: معرف/اسم/معامل سرعة المستوى
│   │   └── elements/           عناصر جاهزة (@tool تظهر في المحرر مباشرة)
│   │       ├── block.gd              منصة صلبة (ثابتة، أو متحركة على AnimatableBody2D)
│   │       ├── phase_block.gd        منصة تختفي بدورة زمنية
│   │       ├── oscillator.gd         مكوّن حركة يُضاف لأي عنصر
│   │       ├── spikes.gd · saw.gd · hazard_pulse.gd   أخطار
│   │       ├── shard.gd / .tscn      Collectible
│   │       └── checkpoint.gd · finish_gate.gd
│   ├── camera/game_camera.gd   Follow + Look-ahead + Shake
│   ├── background/             طبقات Parallax إجرائية (سماء، شمس، أبراج، ضباب)
│   ├── game/
│   │   ├── game_session.gd     Game State Machine + الوسيط الوحيد في مشهد اللعب
│   │   ├── tap_input.gd        الإدخال → "tapped" / "pause_requested" (تعدد الأصابع، منع التكرار)
│   │   ├── score_tracker.gd    منطق النقاط النقي ← قابل للاختبار
│   │   └── game.tscn           المشهد الرئيسي
│   └── ui/                     HUD، Start، Pause، Complete، Fade، Theme
├── levels/
│   └── level_01.tscn + level_01.tres
├── assets/audio/               ملفات الصوت + sound_library.tres
└── tests/
    ├── test_runner.tscn / .gd  مشغّل الاختبارات (يُفشل الاختبار عند أي خطأ من المحرك)
    ├── test_case.gd            دوال assert
    ├── support/                GameHarness (يشغّل المشهد الحقيقي Tick بـTick بإصبع آلي)،
    │                           Level01Route (حل المستوى)، SaveSandbox (حفظ مؤقت للاختبار)
    ├── unit/                   منطق نقي + سلامة المستويات
    └── integration/            فيزياء فعلية + إنهاء Level 01
```

## 3. فصل المسؤوليات

| النظام | المسؤول | ما لا يفعله |
|---|---|---|
| **Player** | `player.gd` | لا يعرف شيئًا عن Score أو UI أو Audio. يطلق Signals محلية فقط. |
| **Movement** | `player_motor.gd` + `movement_config.gd` | لا يلمس Nodes أو Physics Server. يأخذ حالة ويرجع سرعة. |
| **Physics** | `player.gd` (move_and_slide، Ledge Assist، قواعد الموت بالجدار) | لا يدوّر صندوق التصادم أبدًا. |
| **Visual** | `player_visual.gd`, `player_fx.gd` | لا يؤثر على اللعب. يمكن حذفه واللعبة تبقى تعمل (كما في الاختبارات). |
| **Level** | `level.gd` + العناصر | لا يعرف اللاعب. يطلق Signals: Shard جُمع، Checkpoint، نهاية. |
| **Obstacles** | `spikes.gd`, `saw.gd` | بيانات + شكل + Hitbox فقط. لا منطق موت داخلها. |
| **Collectibles** | `shard.gd` | يعرف فقط أنه جُمع ومتى (لإعادته عند الرجوع للـCheckpoint). |
| **Camera** | `game_camera.gd` | لا يقرأ الإدخال ولا الحالة؛ يتبع هدفًا ويهتز عند الطلب. |
| **UI** | `src/ui/*` | لا منطق لعب. يعرض قيمًا ويطلق Signals (resume, restart). |
| **Audio** | `audio_manager.gd` | الوحيد الذي يعرف أي صوت لأي حدث. |
| **Game State** | `game_session.gd` | الوسيط الوحيد في مشهد اللعب: يستقبل Signals من الأسفل ويستدعي الأنظمة للأسفل. |
| **Save** | `save_system.gd` | لا يعرف شيئًا عن اللعب؛ API صغيرة: `record_result` / `get_record`. |

### قاعدة التواصل
- **Signals للأعلى، استدعاءات للأسفل.** العنصر لا يستدعي أبًا أو أخًا.
- **Events Bus** فقط للأحداث التي تهم أنظمة عامة (Audio الآن؛ Haptics/Analytics لاحقًا). يطلقها `GameSession` حصرًا → مصدر واحد يسهل تتبعه.
- **كل الإدخال** يمر عبر `GameSession.press_jump()`: `TapInput` يحوّل اللمس/الماوس/لوحة المفاتيح إلى إشارة `tapped` واحدة لكل لمسة، واللاعب الآلي في الاختبارات يستدعي نفس الدالة.

---

## 4. تدفق البيانات في مشهد اللعب

```
                 ┌────────────────────── GameSession (State Machine) ─────────────────────┐
  Touch/Click →  │ TapInput.tapped ─► press_jump ─► READY: start()   PLAYING: request_jump │
                 │                                                                         │
                 │  Player.jumped/landed/died ─────┐        Level.shard_collected ───┐     │
                 │                                 ▼                                 ▼     │
                 │            Events.emit(...) → AudioManager       ScoreTracker → HUD      │
                 │            player.died → camera.shake → fade → level.rewind_to(t)        │
                 │                                             → player.respawn_at(p)       │
                 └─────────────────────────────────────────────────────────────────────────┘
```

### Game State Machine
```
READY ──tap──► PLAYING ──died──► DYING ──(0.45s + fade)──► PLAYING
                 │  ▲
          pause  │  │ resume
                 ▼  │
                PAUSED                PLAYING ──finish──► COMPLETE ──play again──► READY
```
State Machine بـ `enum` ودالة `_enter_state()` — كافية لخمس حالات. نمط State-per-Node سيكون تعقيدًا بلا فائدة هنا.

### Player States
`IDLE → RUN → JUMP → FALL → RUN ... → DEAD`. تُشتق من الفيزياء (on_floor، اتجاه السرعة) في نهاية كل Physics Tick، وتُطلق `state_changed` للـVisual.

---

## 5. ساعة المستوى (Level Clock) — لماذا هي قلب الحتمية

- `Level` يملك `clock: float` يتقدم بـ`delta` الفيزياء فقط أثناء اللعب.
- كل عنصر متحرك في Group `"timed"` ويطبق `apply_time(t)`: موقعه/حالته **دالة نقية** في `t`.
- `Checkpoint` يسجل قيمة الساعة لحظة المرور. عند الموت: `level.rewind_to(t)` → كل العناصر تعود لنفس الطور، والـShards المجمعة بعد `t` تعود.
- النتيجة: نفس المحاولة = نفس التوقيتات = مستوى قابل للتعلم، واختبار آلي يمكنه إثبات أن المستوى قابل للإنهاء.

ترتيب التنفيذ في كل Tick: `Level` (يحرك العناصر) ← ثم `Player` (يتحرك بالنسبة لها)، مضمون بترتيب الشجرة.

**سرعة جري ثابتة:** `Player` يطرح سرعة المنصة الأفقية من حركته، فتحمله المنصات عموديًا فقط. بذلك يبقى `x(t)` خطيًا، وكل موضع في المستوى يقابل لحظة ثابتة من ساعة المستوى (المستوى "مقطوعة موسيقية"). هذا ما يسمح بـ:
- حساب أطوار العناصر المتحركة من موقعها عند التصميم.
- وصف حل كامل للمستوى كقائمة مواضع قفز (Route) يعيد تشغيلها اللاعب الآلي في الاختبارات.

---

## 6. طبقات الفيزياء

| # | الاسم | من عليها | من يراقبها |
|---|---|---|---|
| 1 | `world` | Blocks، منصات متحركة ومختفية | جسم اللاعب |
| 2 | `player` | جسم اللاعب | Shards، Checkpoints، Finish |
| 3 | `hazard` | Spikes، Saws | Hurtbox اللاعب |
| 4 | `pickup` | Shards | — |
| 5 | `trigger` | Checkpoints، Finish | — |

---

## 7. نظام الصوت

```
GameSession ──► Events.player_jumped ──► AudioManager ──► SoundLibrary["jump"] ──► Pool(8) على Bus "SFX"
```
- `SoundLibrary` (Resource) = قاموس `id → AudioStream`. إضافة صوت = ملف + سطر في المكتبة.
- Pool من `AudioStreamPlayer` يسمح بتداخل الأصوات دون إنشاء Nodes أثناء اللعب.
- Bus منفصل `Ambient` جاهز لصوت خلفية (`AudioManager.play_ambient(stream)`).

---

## 8. نظام الحفظ

`user://save.cfg` (ConfigFile):
```ini
[meta]
version=1
[level_01]
best_score=5230
best_shards=18
completed=true
```
`version` موجود لتمكين Migration لاحقًا دون كسر حفظ اللاعبين.

---

## 9. الأداء (ميزانية الهاتف المتوسط)

- رسم العناصر الثابتة يتم **مرة واحدة** (`_draw` مخزّن) وليس كل Frame.
- لا `instantiate()` أثناء اللعب: كل الـParticles موجودة مسبقًا وتُعاد (`restart()`).
- لا Post-Processing. التوهج طبقات خطوط شفافة.
- الخلفية أشكال مرسومة إجرائيًا (بدون Textures كبيرة) → حجم APK صغير.

---

## 10. الاختبار

| النوع | ماذا يثبت |
|---|---|
| Unit: MovementConfig | الجاذبية والسرعة المشتقة تعطي ارتفاع القفزة المطلوب فعليًا بمحاكاة 60Hz |
| Unit: PlayerMotor | Coyote، Buffer، لا Double Jump، حد سرعة السقوط |
| Unit: ScoreTracker | حساب النقاط، Snapshot/Restore عند الـCheckpoint |
| Unit: SaveSystem | الحفظ والقراءة، أفضل نتيجة لا تنقص |
| Unit: Audio / Project setup | كل أصوات اللعب موجودة في المكتبة، الـInput Map والـAudio Buses ومعدل الفيزياء |
| Unit: Level integrity | وجود Spawn وFinish، ترتيب الـCheckpoints، ≥ 1.5 ثانية أرض آمنة بعد كل Checkpoint، الحتمية `f(t)` للعناصر الزمنية |
| Unit: PlayerVisual | الـSquash لا ينفجر مع توقف إطار طويل، ويستقر على 30/60/144 FPS |
| Integration: Player physics | على الفيزياء الفعلية: زمن الاستجابة (القفز في أول Physics Tick بعد اللمس)، ارتفاع القفزة، الهبوط بلا اهتزاز، الهبوط على 3px من الحافة، فجوات ≤ 1 tile تُعبر بلا قفز، الاصطدام بالسقف، Coyote وBuffer فعليًا، Ledge Assist أثناء الجري والسقوط، السحق، عدم الاختراق بأقصى سرعة سقوط، المصاعد صعودًا ونزولًا، المنصة المختفية تحت اللاعب، الموت والعودة في نفس الإطار |
| Integration: Camera | ثبات موضع اللاعب على الشاشة، لا تمايل مع القفز، صعود ناعم مع الدرج، موت السقوط داخل الشاشة، انتقال فوري بعد العودة |
| Integration: Game flow | أول لمسة تبدأ ولا تقفز، لمسة مكررة في نفس الإطار تُحسب مرة، إصبع ثانٍ يقفز، الموت قبل/بعد Checkpoint، Restart من الإيقاف وأثناء الموت، الإيقاف أثناء الموت ثم الاستئناف |
| Integration: Playthrough | لاعب آلي بجدول قفزات ثابت **ينهي Level 01 بدون موت**، ومرتين بنفس النتيجة بالضبط (Determinism) |
| Integration: Death/Respawn | موت متعمد ← عودة للـCheckpoint ← إنهاء المستوى بنفس المسار (يثبت تطابق التوقيت بعد العودة) |

أي خطأ يسجله المحرك أثناء اختبار (Script error، استدعاء غير صالح...) يُفشل ذلك الاختبار عبر `Logger` مخصص.

التشغيل:
```bash
godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn
```
`--fixed-fps` يجعل المحاكاة تعمل أسرع من الزمن الحقيقي بنفس النتائج (الفيزياء بخطوة ثابتة).

ما **لا** تثبته الاختبارات: الإحساس (Game Feel) — يحتاج Playtesting بشري على هاتف (قائمة §16 في الـGDD).

---

## 11. نقاط التوسع المستقبلية (بدون تعديل البنية)

| الإضافة | أين |
|---|---|
| مستوى جديد | `levels/level_XX.tscn` + `LevelData` |
| عنصر جديد | مشهد في `src/level/elements/` يطبق `apply_time(t)` إن كان زمنيًا |
| صوت جديد | ملف + سطر في `sound_library.tres` |
| قائمة رئيسية / اختيار مستوى | مشهد جديد يستدعي `game.tscn` مع `level_scene` مختلف |
| Haptics / Analytics | Listener جديد على `Events` |
| Localization | ملفات ترجمة + خط عربي في الـTheme |
