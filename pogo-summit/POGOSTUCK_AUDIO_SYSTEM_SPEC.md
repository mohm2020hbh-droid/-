# POGOSTUCK_AUDIO_SYSTEM_SPEC.md

Specification of the audio system of the new Pogo Summit game — the SPEC step of *ANALYZE → SPEC → IMPLEMENT → TEST*. It is **inspired by the organisation and behaviour of the original game's sound layer**, as measured and extracted in `POGOSTUCK_AUDIO_ANALYSIS.md`, and designed for an Android / WebView / Three.js game that must stay extensible.

| Document | Role |
|---|---|
| `POGOSTUCK_AUDIO_ANALYSIS.md` | the 11 original files measured; the original's call sites, rules and **UNKNOWN** list (evidence ids `AU-xx`) |
| this file | what the new system is: events, buses, variants, rules, Map V2 binding, Android constraints, tests |
| `game/src/audio/system/audioConfig.ts` | every tuning number, each tagged by origin (§1) — the single place to change a value |
| `DECISIONS.md` DEC-063 … | decisions taken |

## 0. Scope and hard constraints

* **Not changed:** Physics Core (`game/src/sim/**`), `Pogostuck_Physics_LOCKED_SPEC.md`, Map System V2 (`game/src/map/**`). The audio system *reads* simulation events and `PogoState` (read-only) and Map V2 events; it never writes either. `tests/architecture.test.ts` keeps proving that `sim/` imports nothing from presentation. **No physics constant is added**: every number in `audioConfig.ts` is an *audio* mapping or threshold.
* **Nothing is guessed** about the original: anything not in evidence is **UNKNOWN** (analysis §5) and the system does not depend on it.
* **Originality:** the original WAVs are not shipped. All sound is **procedural** (rendered once to cached buffers from code written from scratch, shaped by *measured descriptors* only: duration, band balance, envelope shape — "timing copied, not the file", DEC-030 / X-10 / X-11). An **optional** sample-bank manifest lets a developer who owns licensed files substitute them locally; it is off by default and never fetched unless a flag asks for it.
* **Only real events make sound.** There is no per-frame, per-pixel or per-drag sound. Charging, aiming and moving the pogo produce nothing by themselves (§8).

## 1. Where each rule comes from

Every rule in this document and every tunable in `audioConfig.ts` carries one of three tags:

| Tag | Meaning |
|---|---|
| **ORIGINAL** | a value or structure extracted from the original game (analysis id `AU-xx`) and reproduced as it is |
| **ORIGINAL-INSPIRED** | a pattern the original uses somewhere (e.g. anti-repeat for slime sounds) applied to a case where the original does not use it |
| **DESIGN** | new, audio-only, chosen for the new game (mobile voice limits, minimum impact, burst caps…). Never presented as a fact about the original |

## 2. Architecture

```
                     sim/ (Physics Core, untouched)               map/ (Map V2, untouched)
                       │ SimEvent[] + PogoState (read-only)         │ MapEvent[] (zone_enter, break, checkpoint, split…)
                       ▼                                            ▼
  ┌───────────────────────────────┐                  ┌──────────────────────────────┐
  │ PogoAudioDirector             │                  │ MapAudioCore (existing, tested)│
  │  SimEvent → AudioEvent        │                  │  zones · emitters · one-shots │
  │  IceSlideController (loop)    │                  │  + AudioZoneSet, surface bridge│
  └──────────────┬────────────────┘                  └───────────────┬──────────────┘
                 │  audio.emit(id, ctx) / audio.startLoop(id, ctx)    │
                 ▼                                                    ▼
  ┌──────────────────────────────────────────────────────────────────────────────────┐
  │ AudioManager (orchestrator; keeps the old host surface: ctx, buses, unlock, apply)│
  │   AudioEvent catalogue ─► VariantSelector ─► gain/pitch/pan ─► AudioPool ─► host  │
  │   SoundBank (buffer cache · lazy render · warm-up · optional samples)             │
  │   BusGraph (MASTER · SFX · PLAYER · SURFACE · AMBIENT · UI · MUSIC)               │
  │   AudioEmitter (spatial) · AudioZoneSet (ambient zones)                           │
  └───────────────────────────────┬──────────────────────────────────────────────────┘
                                  ▼
            AudioHost  ── WebAudioHost (AudioContext singleton, pooled voice channels)
                       └─ FakeHost (tests)
```

| Module (`game/src/audio/system/`) | Responsibility | Pure (testable without WebAudio)? |
|---|---|---|
| `types.ts` | `BusId`, `AudioEventId`, `AudioEventContext`, `EmitResult`, `AudioHost`, `HostVoice` | yes |
| `rng.ts` | seeded RNG (`mulberry32`), so every random choice is reproducible in tests | yes |
| `audioConfig.ts` | all tunables, tagged ORIGINAL / ORIGINAL-INSPIRED / DESIGN | yes |
| `AudioBus.ts` | `AudioBus`, `BusGraph`: parent chain, volume, mute, effective gain, settings mapping | yes |
| `AudioVariant.ts` | `AudioVariantDef`, `VariantSelector` (random, weights, context, layers, no-immediate-repeat, per-variant cooldown) | yes |
| `AudioEvent.ts` | event ids, `AudioEventDef`, the catalogue `AUDIO_EVENTS`, gain/pitch mappers | yes |
| `AudioPool.ts` | voice bookkeeping: capacity, per-event cap, priority + age stealing, stats | yes |
| `AudioEmitter.ts` | spatial models (original range classes; radius roll-off), `Listener`, `AudioEmitter` | yes |
| `AudioZone.ts` | `AudioZone`, `AudioZoneSet` (overlapping ambient zones, priorities, beds) | yes |
| `SoundBank.ts` | cached buffers, lazy procedural rendering, warm-up queue, LRU budget, optional samples | yes (injected host/loader) |
| `synth/dsp.ts`, `synth/recipes.ts` | pure-JS DSP kit and the recipes (one per variant) | yes |
| `IceSlide.ts` | START / LOOP-MODULATE / STOP state machine | yes |
| `PogoAudioDirector.ts` | `SimEvent` → `AudioEvent`, charge cut, power layer, voice-hup chance | yes |
| `MapAudioBridge.ts` | `surfaceClass` (`SurfaceType` / sim `surface` / `material`), the old-sound → event table (`SFX_TO_EVENT`, used by `WebAudioMapBackend` so `MapAudioCore` one-shots go through events), `split` → `TIME_EFFECT` | yes |
| `WebAudioHost.ts` | the only file that touches `AudioContext` | no (tested with a fake context) |
| `../AudioManager.ts` | orchestrator; backwards-compatible surface for `SFXManager`, `MusicManager`, `AmbientManager`, `MapAudio` | via `FakeHost` |

Dependency rules: `system/*` imports nothing from `render/`, `ui/`, `game/`, `sim/core`, `map/` (only *types* from `sim/events` and `map/schema`); `sim/` and `map/` import nothing from `audio/`.

## 3. Buses

```
MASTER ─┬─ SFX ─┬─ PLAYER     charge, launch, collisions (the pogo's own sounds)
        │       └─ SURFACE    ice slide, landings per material, breaking platforms
        ├─ AMBIENT            zone layers, emitters, theme bed
        ├─ UI                 interface sounds, checkpoint / finish / time stingers
        └─ MUSIC              (MusicManager, unchanged)
```

* Effective gain of a bus = product of its own volume and every ancestor's (`BusGraph.effective(id)`); `muted` forces 0.
* **Settings mapping (no save-format change):** `settings.master → MASTER`, `settings.sfx → SFX` and `UI`, `settings.ambient → AMBIENT`, `settings.music → MUSIC`; `PLAYER` and `SURFACE` default to 1.0 and exist for mixing and for future options.
* Output chain unchanged: `MASTER → DynamicsCompressor (−14 dB, 4:1) → destination`.
* The legacy nodes `audio.buses.sfx / music / ambient` are the `SFX`, `MUSIC`, `AMBIENT` bus nodes, so old modules keep working.

## 4. Event catalogue

`AudioEventId`: the six events requested plus the events that carry the remaining game sounds through the same pipeline (so cooldowns, voice limits and buses apply to everything).

| Event | Bus | Kind | Variants (procedural recipes) | Selection | Trigger in the new game | Original basis |
|---|---|---|---|---|---|---|
| `POGO_CHARGE` | PLAYER | one-shot, **1 voice** | `charge_tick` | single | sim `charge_start` | ORIGINAL: `pogoLoad2`, handle-gated, 50, pitch 0.9–1.0, cut at launch (AU-09). **DESIGN trigger** (hold start, not landing) |
| `POGO_LAUNCH` | PLAYER | one-shot, **layers** | `launch_pop` (always) · `launch_boom` (power jump) | layers | sim `launch`; power layer when the same tick has sim `boost` | ORIGINAL structure: `pogoLaunch2` every launch (50, 0.9–1.1), `pogoLaunch3` added on power jumps (90–100) (AU-10…12) |
| `POGO_COLLISION` | PLAYER | one-shot | `collision_1`, `collision_2`, `collision_3`, `collision_4` | **random, no immediate repeat** | sim `wall_hit`; sim `land` on a `bounce` surface | ORIGINAL-INSPIRED: the four `bounce` boings, uniform random (AU-13); P1 anti-repeat from slime/thorn families (AU-18). Impact-speed scaling = **DESIGN** (§6) |
| `POGO_BREAK` | SURFACE | one-shot | `break_1`, `break_2` | random, no immediate repeat | Map V2 `break` | volumes 90 / 100 ORIGINAL (AU-15); generic use = **DESIGN** |
| `ICE_SLIDE` | SURFACE | **loop**, 1 voice | `ice_slide_loop` | single | `IceSlideController` (§7) | ORIGINAL: loop, handle-gated, modulation formulas, fade-out stop (AU-16) |
| `TIME_EFFECT` | UI | one-shot | `time_riser` | single | Map V2 `split`; explicit API | ORIGINAL: `pogoTime` is a 2D menu sting (AU-17); gameplay trigger = **DESIGN** |
| `SURFACE_LAND` | SURFACE | one-shot | legacy `land_soft / land_wood / land_ice / land_goo` by material | context | sim `land` (non-bounce) | existing procedural landings (original: `thud1–3`, not supplied) |
| `HARD_IMPACT` | SURFACE | one-shot | legacy `hard_impact` | single | sim `hard_impact` | existing |
| `BOOST_ARMED`, `BOOST_PAD`, `FALL`, `RESPAWN`, `HAZARD`, `FINISH`, `CHECKPOINT`, `SPLASH`, `TELEPORT`, `CHIME`, `LAVA_POP`, `BOOST_ZONE` | SFX / UI | one-shot | legacy procedural sounds | single | sim / Map V2 events | existing (adapter variants, §11) |
| `VOICE_HUP`, `VOICE_OUCH`, `VOICE_YAY` | PLAYER | one-shot | legacy formant voices | single | launch (60 % chance), hazard, goal | existing |
| `UI_*` | UI | one-shot | legacy | single | `AudioManager.emit('UI_CLICK')` | existing |

The sim's `boost` event no longer plays the old whoosh: the boosted launch is the power layer of `POGO_LAUNCH`, as in the original (§8).

## 5. Variants and selection

```ts
interface AudioVariantDef {
  id: string;                         // bank key: 'collision_3'
  source: { kind: 'synth'; recipe: string }
        | { kind: 'sample'; url: string; recipe?: string }     // optional licensed file, falls back to the recipe
        | { kind: 'legacy'; sfx: SfxId };                       // adapter to the existing procedural SFXManager
  weight?: number;                    // default 1
  trimDb?: number;                    // level trim of this variant
  when?: (ctx: AudioEventContext) => boolean;   // eligibility (layers / context selection)
  durationHint?: number;              // seconds (legacy voices have no end callback)
  analog?: string;                    // provenance note only ('bounce2')
}
```

`VariantSelector.pick(def, ctx, now)` — modes:

* **`random`** — candidates = variants whose `when` passes and that are not inside their own `variantCooldown`; if that empties the set the cooldown is ignored (never silence by cooldown alone). Index `i = ⌊rng·N⌋`; **P1 rule (ORIGINAL-INSPIRED, AU-18): `if (i == last && N > 1) i = (i + 1) mod N`**; `last = i`. With weights, the draw is weighted first and the same rule resolves a repeat.
* **`layers`** — every variant whose `when` passes (the launch pop always, the boom on `ctx.power`).
* **`context`** — `ctx.variant` forces one; otherwise the first eligible by `when` (used by `SURFACE_LAND`).

## 6. Collision system (`POGO_COLLISION`)

1. **Impact speed.** `impact01 = ctx.intensity` — the simulation's own normalisation: for `wall_hit` it is `|v_in| / (maxSpeed/2)` clamped to 0…1; for `land` it is `I / hardImpactLoad` (`game/src/sim/events.ts`: "Normalised 0..1 strength (impact speed, charge power…)"). The audio never recomputes physics.
2. **Gate.** `impact01 < minImpact` (0.06, DESIGN) → no sound (`reason: 'inaudible'`).
3. **Variant.** random with the P1 anti-repeat rule + per-variant cooldown 250 ms (DESIGN).
4. **Volume.** `volume = U(40, 50)` (ORIGINAL), `gain = volume/100 × curve(impact01)`, `curve(x) = lerp(0.35, 1, ((x − minImpact)/(1 − minImpact))^0.8)` (DESIGN). Monotonic, bounded in `[0.35, 1]·volume/100`.
5. **Pitch.** `pitch = (1 + U(−0.04, 0.04)) · (1 + (impact01 − 0.5)·0.06)` ⇒ always within `[0.93, 1.07]` (DESIGN; the original's 0.9–1.1 is wider than the requested "limited pitch variation").
6. **No machine gun** (all DESIGN, tested): event cooldown 90 ms · burst cap 4 per 600 ms · voice cap 3 with steal-oldest · retrigger duck −4 dB when the previous collision sounded < 200 ms ago. (The original's own guard for rapid-fire hits is a concurrency cap with random margin, `numInstances + random(2) < 5` — AU-19; the voice cap above is its deterministic form.)
7. **Spatial.** positional at the contact point, `range` 2 (ORIGINAL class of the bounce sites), relative to the listener.

## 7. Ice slide (`ICE_SLIDE`) — START / LOOP-MODULATE / STOP

State machine (`IceSlideController`, pure; driven by `PogoAudioDirector.update` once per display frame from the read-only `PogoState`):

```
            sliding && speed ≥ START          ┌───────────────┐
   ┌──────┐ ───────────────────────────────► │   LOOPING     │ ── each frame: MODULATE(speed)
   │ IDLE │                                   └──────┬────────┘
   └──────┘ ◄───────────────────────────────────────┘
            !sliding  ∨  speed < STOP   → stop with fade 0.25 s, then restart guard 0.12 s
```

* **`sliding`** = `state.slideMode ∧ state.grounded ∧ surface(groundId) ∈ {ICE, SLIPPERY}`. `slideMode` is the physics' own E15 flag; `grounded` is the **DESIGN** addition (no sound in mid-air); the surface class comes from the bridge (§10) and covers both vocabularies (`SurfaceType.ICE/SLIPPERY`, sim `surface: 'slippery'`, `material: 'ice'`).
* **`speed`** = `hypot(state.sx, state.sy)` in the sim's own Q/T (the slide velocity of the locked spec — read, never changed).
* **START** when `speed ≥ 3` (DESIGN, ≈ 0.9 m/s). The loop starts at a **random offset** inside the buffer (ORIGINAL, AU-16: `DSB8SetCurrentPosition(random)`).
* **MODULATE** each frame (ORIGINAL formulas, AU-16; the original's volume unit 0–100 is read as `/100`):
  `pitch = min(0.675 + 0.01·speed, 1)` · `volume = min(20 + 1.5·speed, 70)` ⇒ `gain = volume/100`. Both saturate near `speed ≈ 33` (below the slide target of 48 Q/T): steady sliding sits at full pitch and volume; accelerating or decaying slides are quieter and lower.
* **STOP** when not sliding or `speed < 1` (DESIGN hysteresis): fade-out **0.25 s** (ORIGINAL: `kuSoundStop(h, −50)`; unit INFERRED, analysis §3.1 / U4), then a 0.12 s restart guard (DESIGN) so landing-launching on ice does not stutter the loop.
* **Voice:** `maxVoices 1`, handle-gated like the original (`startLoop` while active returns the same loop).
* Pause / results / level change → `director.silence()` fades the loop out.

## 8. Charge, launch and movement are separate

| Sim event | Audio | Rule |
|---|---|---|
| `charge_start` | `POGO_CHARGE` once | one voice (a second request while the tick is sounding is ignored — handle gating, AU-09); pitch 0.9–1.0 |
| *(holding, aiming, dragging, tilting, moving, rotating)* | **nothing** | there is no per-frame audio on input or motion; the old looping "charge whine" (`SFXManager.chargeUpdate`) is **removed** |
| `launch` | `POGO_LAUNCH`: `launch_pop`; **plus `launch_boom` if a `boost` event has the same tick** | `POGO_CHARGE` is cut with a 60 ms fade (ORIGINAL cut-at-launch, AU-09; fade length DESIGN); gain `volume/100 · lerp(0.7, 1, intensity)` (DESIGN scaling) |
| `boost` | *(part of the launch layers)* | no separate sound |
| `slide` | *(none)* | the loop is driven by state (§7), not by the entry event |

A test feeds the director only input/motion state and asserts that the host was never asked to play.

## 9. Voice pool, emitters, zones

* **`AudioPool`** — global capacity 16 (10 on `quality: simplified`), DESIGN. `acquire({event, priority, gain})`:
  1. if the event is at its own cap → `ignore` (charge, ice) or steal the oldest voice of that event (collision);
  2. else if the pool is full → steal the voice with the lowest `(priority, gain)` among those `≤` the request's priority (oldest first); if none, reject (`voice-limit`);
  3. every stolen voice is stopped with a 30 ms fade.
  Priorities (DESIGN): `POGO_LAUNCH` 8 · `POGO_CHARGE` 7 · `ICE_SLIDE` 7 · `POGO_COLLISION` 6 · `FINISH`/`CHECKPOINT` 9 · `POGO_BREAK` 5 · `SURFACE_LAND` 5 · ambience 2.
  The **WebAudio host** pools the *channels* (`GainNode` + `StereoPannerNode`) so no node churn happens per sound; only the cheap `AudioBufferSourceNode` is created per play (it cannot be reused).
* **`AudioEmitter` / spatial** — two models, both pure:
  * `original` (ORIGINAL, AU-07): `gain = clamp(range·1.25 − dist/W, 0, 1)`, `pan = clamp(Δx/W · 0.2, −1, 1)`, `W` = listener half-width (m), `range` ∈ {1, 1.5, 2, 3};
  * `radius`: the existing Map V2 roll-off `(1 − d/r)²` and `panOf` (kept so `MapAudioCore` behaves exactly as before).
  The player's own sounds are at the listener (gain 1, pan 0); positional events (`break`, collisions) use the original model; Map `audio` entities keep `radius`.
* **`AudioZoneSet`** — Map V2 `audio`/`water` regions become `AudioZone { id, layers: {name, volume}[], priority }`. Entering adds the zone, leaving removes it; the ambient target of a layer is the **maximum** among active zones (the old bookkeeping let one zone's exit silence a layer another overlapping zone still wanted). The theme bed (`ThemeDef.ambientAudio`) is a permanent zone.

## 10. Map System V2 binding

| Map V2 concept | Audio behaviour |
|---|---|
| **Surface Type** (`SurfaceType`: NORMAL, ICE, SLIPPERY, BOUNCE, HAZARD, WATER, LAVA, GOAL; sim `surface`; collider `material`) | `surfaceClass(surface, material)` → `normal \| ice \| slippery \| bounce \| hazard \| water \| lava \| goal`. `ice`/`slippery` allow `ICE_SLIDE`; `bounce` routes a landing to `POGO_COLLISION`; others pick the landing flavour (`SURFACE_LAND`) |
| **Audio Zone** (region type `audio`) | `AudioZoneSet` layers fade in/out (0.6 s cross-fade, unchanged) |
| **Ambient Zone** (`audio` effects, emitters, waterfalls) | `MapAudioCore` loops/emitters unchanged (positional `radius` model, nearest-`maxVoices`) |
| **Checkpoint** (`checkpoint`) | `CHECKPOINT` (UI bus), 0.35 s cooldown (existing) |
| **Hazard** (sim `hazard`) | `HAZARD` + `VOICE_OUCH` |
| **Finish** (sim `goal`, Map `finish`) | `FINISH` + `VOICE_YAY` |
| **Environment** (`theme.ambientAudio`: bed, wind, birds, water, chimes, drone) | `AudioZoneSet` bed + `AmbientManager` (unchanged) |
| **Break** (`break`, `restore`) | `POGO_BREAK` (positional, radius 45 m as before) / `CHIME` |
| **Split** (`split`) | `TIME_EFFECT` (gameplay trigger, DESIGN; truncated to 3 s + 0.6 s fade) |
| Map `audio.maxVoices` | caps the ambient emitter voices (existing) |

`src/map/**` is not modified: the bridge only consumes `MapEvent`s through the existing `game.onMapEvents`.

## 11. Procedural bank, caching, loading

* **Recipes** (`synth/recipes.ts`) render **mono Float32 buffers once** at the context's sample rate with a seeded RNG, then `host.makeBuffer`. Targets are *measured descriptors* of the originals (analysis §2), never samples:

| Variant | Analog | Target descriptors (from the analysis) |
|---|---|---|
| `charge_tick` | `pogoLoad2` | 105 ms · centroid ≈ 4.9 kHz · ring ≈ 440 Hz · 2.5 ms to peak · −20 dB at ≈ 66 ms |
| `launch_pop` | `pogoLaunch2` | ≈ 0.18 s · swelling noise to a click at ≈ 86 ms · centroid ≈ 4.8 kHz · 55 % of energy 4–8 kHz |
| `launch_boom` | `pogoLaunch3` | ≈ 1.9 s · slow swell (peak ≈ 220 ms) · sub-bass body · downward whistle · −20 dB at ≈ 0.49 s · **plus a 90–250 Hz body so it is audible on phone speakers (DESIGN)** |
| `collision_1`, `collision_2`, `collision_3`, `collision_4` | `bounce1`…`bounce4` | ≈ 118 Hz comb under a ≈ 1 kHz resonance; 1.6 / 0.67 / 0.67 / 0.92 s; steady / glide up an octave ×2 / undulating; −20 dB at 1.1 / 0.46 / 0.47 / 0.59 s; centroid ≈ 1.1–1.3 kHz |
| `break_1`, `break_2` | `break1`, `break2` | 0.52 s crack burst on a ≈ 64 Hz thump / 1.86 s heavy rumble 30–140 Hz with cracks; −20 dB at 0.19 / 1.3 s |
| `ice_slide_loop` | `iceSlide` | 1.4 s stationary noise, flat envelope, centroid ≈ 6 kHz, ≈ 41 % above 8 kHz, **seamless loop** |
| `time_riser` | `pogoTime` | ≈ 6.4 s · rising partials ≈ 0.13 → 3.9 kHz · −20 dB at ≈ 4.9 s |

  Loudness: recipes are normalised to a common peak (−3 dBFS); relative level between events comes from the original *call volumes* (analysis §3.2: 50 / 50 / 40–50 / 90–100 / 20–70 / 100), read linearly as `volume/100` (**DESIGN**, because the original's DSB volume scale is **UNKNOWN**, U1).
* **`SoundBank`** — `get(id)` returns the cached buffer; a miss renders the recipe synchronously (lazy) and caches; `warm(ids)` renders in idle slices after the first unlock (menus give seconds of idle time). Memory budget 8 MB with LRU eviction (**1.31 MB measured** for all 11 recipes: the low-bandwidth ones render at 11–22 kHz). Hits / misses / bytes / render ms are in `stats`.
* **Optional samples** — `manifest.json` (`{ variants: { collision_1: { url, gain } } }`), enabled only by `?audioPack=<url>`. A sample-backed variant plays its **recipe immediately** while the file loads, then the file. A failed fetch/decode marks the variant `missing` once and the recipe keeps playing.
* **Legacy adapter** — a variant `{kind:'legacy', sfx}` calls the existing `SFXManager.play`; its pool voice expires after `durationHint`. This is how every old sound goes through the same events, buses, cooldowns and voice limits while its synthesis is migrated recipe by recipe later.
* **Missing audio never throws** (ORIGINAL behaviour, AU-05): an unknown event, a variant without recipe/sample, a failed load or a not-ready host returns `EmitResult { played: false, reason }`, counts in `stats`, and the selector falls back to another variant when one exists.

## 12. Android / WebView / Three.js

| Requirement | Design |
|---|---|
| `AudioContext` singleton | one context per page (`WebAudioHost.shared`); a second host reuses it |
| Mobile-safe initialisation | context created **only on the first user gesture** (`TouchControls.onTouchStart`, UI clicks); `resume()` on every later gesture; `statechange` handler marks `needsResume` after an interruption; `visibilitychange` / `pogo-pause` suspend, `focus` resumes |
| No sound before unlock | `emit` returns `not-ready`; events are **not queued** (sound only on real events, never late) |
| Buffer caching | `SoundBank` renders once; AudioBuffers are immutable and shared by all voices |
| Audio pooling | channel pool (gain + panner) + `AudioPool` bookkeeping |
| Lazy loading | recipes render on first use or in warm-up; samples fetched on demand; music is untouched |
| Voice limits | global 16 (10 on low quality), per-event caps (§4), burst caps |
| Latency | `latencyHint: 'interactive'`; no per-event node graph construction beyond a source node |
| Memory | 1.3 MB of buffers measured, 8 MB hard budget |
| Battery | no timers at idle; ambient loops only inside zones; context suspended when hidden |
| Sample rate | rendered at the context's rate (no resampling at play time) |

## 13. Test plan → requirement

| Requirement | Test file |
|---|---|
| charge (one click, single instance, no sound without an event, cut on launch) | `tests/audio/system/charge.test.ts` |
| launch (standard always, power layer on `boost`, gain by intensity, stops charge) | `launch.test.ts` |
| collision (impact → volume, threshold, pitch bounds, 4 variants reachable) | `collision.test.ts` |
| variant selection (weights, `when`, forced, cooldown fallback) | `variant.test.ts` |
| **no immediate repeat** (100 000 draws, every N) | `variant.test.ts`, `collision.test.ts` |
| **cooldown** / burst / voice cap / duck ("no machine gun") | `collision.test.ts` |
| **ice slide** (START only when really sliding, modulate = original formulas, STOP, hysteresis, surface class) | `ice.test.ts` |
| **break** (Map V2 event, variants alternate, positional attenuation) | `break.test.ts` |
| **audio pooling** (capacity, per-event cap, priority stealing, release, node-channel reuse, context singleton) | `pool.test.ts`, `webaudio.test.ts` |
| **volume mapping** (bus product, settings, curves monotone/bounded) | `volume.test.ts` |
| **missing audio** (unknown event, missing recipe, failed sample, not ready) | `missing.test.ts` |
| zones / bridge / surface classes / Map V2 events | `zones.test.ts`, `mapBridge.test.ts` |
| recipes (determinism, durations, loop seam, no DC, descriptors vs targets) | `synth.test.ts` |
| movement produces no audio | `director.test.ts` |
| physics untouched | existing `architecture`, `spec`, `physics`, `determinism`, `map/boundary` tests |

## 14. Where the design departs from the original (and why it is acceptable)

See `POGOSTUCK_AUDIO_ANALYSIS.md` §4. In short: bounce files are entity boings chosen without speed dependence (the new impact-speed scaling, threshold and caps are DESIGN); `pogoLaunch3` is a layer, not a random alternative (modelled as a layer); `pogoLoad2` fires on ground contact in the original and on charge start here; `pogoTime` is a menu sting (the new `TIME_EFFECT` trigger is DESIGN); the original has no priority system (the pool is DESIGN, needed on mobile).

## 15. What this system does not do (limits, stated up front)

* **Nobody has heard it.** There is no audio device here and no Android device (`DEVICE_TEST = NOT_AVAILABLE`). Everything below is *measured*, not listened to. Overall level and balance follow the original's call-volume ratios (analysis §3.2), read linearly (**DESIGN**, since the original's DirectSound volume scale is **UNKNOWN**, analysis U1); expect to tune levels by ear on a phone — `audioConfig.ts` is the only place to do it.
* The recipes are *approximations of descriptors*, not reproductions: centroids are within ≈ ±17 %, and a few envelope numbers differ (§16.2). They are written from scratch.
* Landings, cues, UI and character voices still use the earlier per-call procedural synthesis (through the legacy adapter, so they obey events, buses, cooldowns and voice limits). Migrating them to cached recipes is future work.
* The licensed-sample path (`?audioPack=`) is tested with fakes only; no real file was ever loaded.
* Ambient loops (zone layers, emitters) are still noise graphs owned by `MapAudioCore`/`WebAudioMapBackend` — long-lived and few, not per-event.

## 16. Results

### 16.1 Tests

| Scope | Result |
|---|---|
| New audio-system tests | **17 files, 253 tests** (`tests/audio/system/*.test.ts`) — all pass |
| Audio tests in total (with the 14 existing `MapAudioCore` tests, which pass unchanged) | 18 files, 267 tests |
| Whole suite | **53 files, 735 tests** pass (was 36 files / 482) · `tsc --noEmit` clean · production bundle builds (1008 KB) |
| Physics / map untouched | `git diff` shows nothing under `game/src/sim`, `game/src/map`, `Pogostuck_Physics_LOCKED_SPEC.md`; `architecture`, `spec`, `physics`, `determinism`, `map/boundary` pass unchanged |

### 16.2 Recipes against the measured originals (same analyser, `tools/audio-analyze.py`; each cell is *new / original*)

| Recipe | Analog | Duration s (new / original) | Centroid Hz | Loudest moment ms | −20 dB after ms | Dominant band (share) | Rate |
|---|---|---|---|---|---|---|---|
| `charge_tick` | `pogoLoad2` | 0.105 / 0.105 | 5520 / 4888 | 3 / 2 | 68 / 66 | presence 42 % / presence 42 % | 44100 |
| `launch_pop` | `pogoLaunch2` | 0.150 / 0.181 | 4646 / 4786 | 80 / 86 | 21 / 19 | presence 46 % / presence 55 % | 44100 |
| `launch_boom` | `pogoLaunch3` | 1.900 / 1.907 | 189 / 161 | 281 / 222 | 459 / 487 | sub 84 % / sub 89 % | 16000 |
| `collision_1` | `bounce1` | 1.600 / 1.597 | 1153 / 1152 | 42 / 57 | 1046 / 1096 | mid 100 % / mid 100 % | 11025 |
| `collision_2` | `bounce2` | 0.665 / 0.665 | 1225 / 1208 | 25 / 23 | 469 / 459 | mid 100 % / mid 99 % | 11025 |
| `collision_3` | `bounce3` | 0.669 / 0.669 | 1262 / 1290 | 17 / 15 | 468 / 467 | mid 100 % / mid 98 % | 11025 |
| `collision_4` | `bounce4` | 0.920 / 0.920 | 1201 / 1128 | 76 / 92 | 663 / 594 | mid 99 % / mid 99 % | 11025 |
| `break_1` | `break1` | 0.520 / 0.519 | 733 / 765 | 33 / 73 | 225 / 186 | sub 57 % / sub 52 % | 22050 |
| `break_2` | `break2` | 1.860 / 1.861 | 283 / 290 | 19 / 17 | 1251 / 1315 | bass 57 % / bass 52 % | 16000 |
| `ice_slide_loop` | `iceSlide` | 1.400 / 1.399 | 6525 / 6120 | 1171 / 595 | 226 / 800 | air 38 % / air 41 % | 44100 |
| `time_riser` | `pogoTime` | 6.400 / 6.414 | 1141 / 1210 | 47 / 56 | 4813 / 4944 | mid 66 % / mid 71 % | 22050 |

Reading: durations match to the millisecond; centroids are within ≈ ±17 % (`launch_boom` is deliberately brighter: it carries a 90–250 Hz body so it is audible on phone speakers — **DESIGN**); the −20 dB times of the boings, `break_2`, `launch_boom` and `time_riser` are within ≈ ±15 %. Known differences: `break_1` is seed-dependent (its decay measures 114–209 ms over eight seeds, target 186; the default seed gives 225), and `ice_slide_loop` is a stationary loop, so "loudest moment" and "−20 dB" have no meaning for it (its band shares are within ±4 points: low-mid 7.4/6.4, mid 23/21.8, presence 17/14.3, air 37.9/41.4 %). Raw numbers: `qa/audio/recipes_analysis.json` (new) and `qa/audio/original_analysis.json` (originals).

### 16.3 The real Web Audio API (`node tools/audio-browser-check.mjs <dir>`, headless Chromium)

| Check | Result |
|---|---|
| Live `AudioContext` | unlocked in 35 ms, `running`, 44.1 kHz, base latency 10 ms, **one** context for the page |
| Recipe render cost on this engine | 190 ms for all 11 (largest: `time_riser` 88 ms, `launch_boom` 43 ms; the rest ≤ 17 ms); 1.31 MB of buffers; 0 missing |
| All six events, two rounds | 12/12 played, 0 skipped |
| Pooling | 14 voices on **11** channels (3 reused); pool peak 9; the pool **drains to 0** once everything ended |
| Offline session (48 kHz; the scripted timeline: charge click at 0.2 s → power launch at 0.45 s → 4 wall hits at 0.9 / 1.1 / 1.25 / 1.3 s → ice slide from 1.8 s with the slide speed ramping 8 → 45 → 3 Q/T in 31 steps, ended at 3.35 s → 2 breaks → time sting at 4.7 s), mixed through the real buses and compressor | 10 voices on **4** channels (6 reuses); the 4th wall hit, 50 ms after the 3rd, was suppressed by the cooldown, as scripted; **peak −10.8 dBFS (no clipping)**, −23.4 LUFS-I; the spectrogram shows each event at its scripted time (boom and whistle under the pop, boing glides, the broadband ice loop fading out after 3.35 s, silence, the break cracks, the rising partials of the sting truncated at ≈ 7.7 s + fade) |

### 16.4 The built game (`node tools/qa-audio-ingame.mjs showcase_v2 world_ice`, production bundle, headless Chromium)

Unlock → 11 recipes warm in the background (1.3 MB, none missing) → hold and release the charge → `POGO_CHARGE` 1, `POGO_LAUNCH` 3, `SURFACE_LAND` 3, `POGO_COLLISION` 1, `FALL` 1 → the pogo is dropped on an ice ledge with horizontal speed: **the real physics enters slide mode on landing, the audio system starts the `ICE_SLIDE` loop, and after the slide the loop's voice is freed** (pool count 0). 0 page errors; 0 skipped events.

### 16.5 Reproduce

```
cd game
npm test                                           # everything, incl. tests/audio/system
npx tsx tools/audio-render.ts <dir>                # render the recipes to WAV
python3 tools/audio-analyze.py <dir> <out.json>    # measure them (same tool as for the originals)
node tools/audio-browser-check.mjs <dir>           # real Web Audio: live + offline session → session.wav
npm run build && node tools/qa-audio-ingame.mjs    # the built game, real physics, audio counters
```
