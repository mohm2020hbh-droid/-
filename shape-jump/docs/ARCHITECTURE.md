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
├── project.godot               إعدادات المشروع، Input Map، أسماء طبقات الفيزياء، Autoloads
├── default_bus_layout.tres     Master / SFX / Ambient
├── docs/                       GDD + Architecture
├── src/
│   ├── autoload/               خدمات عامة فقط (3 ملفات)
│   │   ├── events.gd           Signal Bus للأحداث العابرة للأنظمة
│   │   ├── audio_manager.gd    يربط الأحداث بالأصوات
│   │   └── save_system.gd      الحفظ والتحميل
│   ├── core/                   ثوابت ومساعدات بلا حالة
│   │   ├── palette.gd          الألوان (مصدر واحد للهوية البصرية)
│   │   ├── layers.gd           أرقام طبقات الفيزياء + حجم الـTile
│   │   └── neon.gd             دوال رسم الحواف المضيئة
│   ├── player/
│   │   ├── movement_config.gd  Resource: كل أرقام الحركة القابلة للضبط
│   │   ├── player_motor.gd     منطق الحركة النقي (بلا Nodes) ← قابل للاختبار
│   │   ├── player.gd           CharacterBody2D: يطبق الـMotor، التصادم، الحالات
│   │   ├── player_visual.gd    الرسم والـAnimations فقط (الدوران هنا فقط)
│   │   ├── player_fx.gd        Particles والذيل الضوئي
│   │   └── player.tscn
│   ├── level/
│   │   ├── level.gd            جذر أي مستوى: ساعة المستوى، الـSpawn، الـCheckpoints
│   │   ├── level_data.gd       Resource: اسم/معرف/معامل سرعة المستوى
│   │   └── elements/           عناصر جاهزة (@tool تظهر في المحرر مباشرة)
│   │       ├── block.gd              منصة صلبة (ثابتة أو متحركة)
│   │       ├── phase_block.gd        منصة تختفي بدورة زمنية
│   │       ├── oscillator.gd         مكوّن حركة يُضاف لأي عنصر
│   │       ├── spikes.gd · saw.gd    أخطار
│   │       ├── shard.gd              Collectible
│   │       └── checkpoint.gd · finish_gate.gd
│   ├── camera/game_camera.gd   Follow + Look-ahead + Shake
│   ├── background/             طبقات Parallax إجرائية
│   ├── game/
│   │   ├── game_session.gd     Game State Machine + ربط الأنظمة
│   │   ├── score_tracker.gd    منطق النقاط النقي ← قابل للاختبار
│   │   └── game.tscn           المشهد الرئيسي
│   └── ui/                     HUD، Start، Pause، Complete، Fade، Theme
├── levels/
│   └── level_01.tscn (+ .tres)
├── assets/audio/               ملفات الصوت + sound_library.tres
└── tests/
    ├── test_runner.tscn / .gd  مشغّل الاختبارات
    ├── test_case.gd            دوال assert
    ├── unit/                   منطق نقي
    └── integration/            تشغيل المستوى فعليًا بلاعب آلي
```

---

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

---

## 4. تدفق البيانات في مشهد اللعب

```
                 ┌────────────────────── GameSession (State Machine) ─────────────────────┐
  Touch/Click →  │ _unhandled_input ─► READY: start()   PLAYING: player.request_jump()   │
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
| Unit: Level integrity | وجود Spawn وFinish وCheckpoints، تسجيل العناصر الزمنية، الحتمية `f(t)` |
| Integration: Playthrough | لاعب آلي بجدول قفزات ثابت **ينهي Level 01 بدون موت**، ومرتين بنفس النتيجة بالضبط (Determinism) |
| Integration: Death/Respawn | الموت يعيد للـCheckpoint مع استرجاع الـScore والـShards |

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
