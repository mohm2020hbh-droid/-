# PROJECT_ARCHITECTURE.md

## 1) الفكرة المعمارية
**فصل صارم بين المحاكاة والعرض.** المحاكاة (`sim/`) دوال وحالات نقية بلا DOM ولا Three.js ولا ساعة حائط؛ تُدار بـ tick ثابت 120Hz. كل ما عداها (عرض، صوت، اهتزاز، واجهة) **مستهلك للأحداث والحالة** فقط.

```
                 ┌────────────────────────── game/src ───────────────────────────┐
 Touch/Pointer → │ input/TouchInput ─▶ ControlScheme (Drag | Pad) ─▶ Input{tilt,jumpHeld,boost}
                 │                                                      │
                 │   data/LevelData ─▶ sim/PhysicsWorld ◀── PhysicsConfig (TUNE_ME)
                 │                         │
                 │            sim/PogoPhysicsController.step(input)  @120Hz
                 │       (JumpSystem · BoostSystem · SurfacePhysics · CollisionResponse)
                 │                         │  state + SimEvents[]
                 │      ┌──────────────────┼──────────────────────┬───────────────┐
                 │   render/*          audio/*               haptics/*       progression/*
                 │ (Three.js scene)  (Audio events)       (Haptic events)  (Save, unlocks)
                 │      │ camera rig        │                                   │
                 │      └────────── ui/* (HUD, menus, lab) ◀────────────────────┘
                 └────────────────────────────────────────────────────────────────┘
                        │ window.PogoNative (JS bridge)
                 android/ (Activity + WebView + Vibrator + SharedPreferences)
```

## 2) هيكل المجلدات
```
pogo-summit/
├─ *.md                      الوثائق الست
├─ reference/                الصورة المرجعية
├─ game/
│  ├─ src/
│  │  ├─ sim/                PhysicsConfig, SurfacePhysics, CollisionResponse, JumpSystem,
│  │  │                      BoostSystem, PogoPhysicsController, PhysicsWorld, geometry, prediction
│  │  ├─ data/               LevelData (types+validate), levels/level01, worlds, items (customization)
│  │  ├─ input/              TouchInput, ControlSchemes
│  │  ├─ render/             Renderer, materials, builders (rock/tree/props/structures), Character,
│  │  │                      EnvironmentBuilder, CameraRig, Vfx
│  │  ├─ audio/              AudioManager (منسّق + سطح قديم), MusicManager, SFXManager (محوّل legacy), AmbientManager, MapAudio, AudioEvents
│  │  │  └─ system/          types, audioConfig, AudioBus, AudioVariant, AudioEvent, AudioPool, AudioEmitter, AudioZone, SoundBank,
│  │  │                      synth/{dsp,recipes}, IceSlide, PogoAudioDirector, MapAudioBridge, WebAudioHost
│  │  ├─ haptics/            HapticManager
│  │  ├─ progression/        SaveSystem, Progression
│  │  ├─ ui/                 dom helpers, Hud, screens/*, i18n
│  │  ├─ lab/                PhysicsLab
│  │  ├─ game/               Game (حلقة اللعب + ربط الأنظمة)
│  │  └─ main.ts
│  ├─ tests/                 vitest (فيزياء، حتمية، قابلية الحل، حفظ)
│  └─ tools/                 build.mjs, qa-screenshots.mjs, gen-physics-doc.mjs
├─ android/                  مشروع Gradle (AAB) + src مشترك مع build-apk.sh
├─ tools/build-apk.sh        مسار apt لإنتاج APK debug
└─ qa/                       لقطات وتقارير
```

## 3) حلقة التشغيل
1. `requestAnimationFrame(t)`: `dt` حقيقي مقيّد (≤ 100ms).
2. مُجمّع زمن: `while (acc ≥ 1/120) { input = scheme.sample(); sim.step(input); events → bus; acc -= 1/120 }`.
3. عرض: `alpha = acc/(1/120)` ⇒ استيفاء موضع/زاوية اللاعب بين آخر حالتين.
4. كاميرا + نظم المرئيات + HUD + صوت/اهتزاز من الأحداث.
5. `visibilitychange`/`pause` ⇒ إيقاف الحلقة والصوت.

## 4) حدود الوحدات (قواعد صارمة)
- `sim/*` لا يستورد من `render|ui|audio|haptics|input`. (يُفحص آليًا في `tests/architecture.test.ts`.)
- `data/*` بيانات خالصة قابلة للتسلسل (JSON-like) + تحقق `validateLevel`.
- **الصوت** (`POGOSTUCK_AUDIO_SYSTEM_SPEC.md`): `SimEvent` ⇒ `PogoAudioDirector` ⇒ حدث صوتي ⇒ `AudioManager.emit/startLoop` ⇒ كتالوج + اختيار variant + pool ⇒ `AudioHost`. المنطق نقي (يُختبر بمضيف وهمي)، و`WebAudioHost` وحده يلمس Web Audio. النظام يقرأ أحداث المحاكاة و`PogoState` وأحداث `MapRuntime` فقط (types فقط من `sim/` و`map/`، يفرضه `tests/audio/system/mapBridge.test.ts`) ولا يكتب فيها شيئًا.
- أحداث الفيزياء `SimEvent` هي العقد الوحيد مع الصوت/الاهتزاز/الجسيمات: `charge_start, launch, land, hard_impact, bounce, wall_hit, boost_armed, boost, hazard, fall, respawn, goal`.
- لا `new` في حلقة اللعب: الجسيمات والمتجهات المؤقتة مجمّعة.

## 5) البيانات (Data-driven)
- **LevelData**: `levelId, worldId, theme, startPosition, goalPosition, platforms[], obstacles[], hazards[], movingObjects[], specialSurfaces[], difficulty, progress` (+ `bounds`, `killY`, `landmarks`).
- **WorldTheme**: لوحة (Primary/Secondary/Accent/Background)، سماء، ضباب، شمس، مواد، أنواع الدعائم، صوت المحيط، ملف الصعوبة.
- **SurfaceDef**: `friction, restitution, velocityMultiplier, soundEvent, particleEvent, hapticEvent`.
- **ItemDef**: `itemId, category, icon, prefab, unlockCondition, rarity`.

## 6) الحفظ
`SaveSystem`: `{schemaVersion, settings{audio,controls,graphics,language}, progress{worlds,levels{best time,jumps,boosts,stars}}, unlocked[], equipped{}, stats}`. كتابة مؤجلة (debounce) + عند `pause`. أولوية للجسر الأصلي (SharedPreferences) ثم localStorage.

## 7) خط الإنتاج
`npm run build` (esbuild IIFE + minify) → `index.html` + `game.js` ⇒ `android/app/src/main/assets/www/` ⇒ Activity تخدمها من نطاق افتراضي HTTPS (`shouldInterceptRequest`) فتعمل ES features/WebAudio/fetch بلا قيود `file://`.
