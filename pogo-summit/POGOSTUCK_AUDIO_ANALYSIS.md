# POGOSTUCK_AUDIO_ANALYSIS.md

Analysis of the **11 original sound files** the user supplied (`Pogostuck_Original_Audio.zip`) and of **what the original game does with them**. This is the ANALYZE step of *ANALYZE → SPEC → IMPLEMENT → TEST*; the design that follows from it is `POGOSTUCK_AUDIO_SYSTEM_SPEC.md`.

| Document | Role |
|---|---|
| this file | what the files are (measured), what the original game does with them (extracted), what is **UNKNOWN** |
| `POGOSTUCK_AUDIO_SYSTEM_SPEC.md` | the new audio system: events, buses, variants, rules, Map V2 binding, Android constraints, tests |
| `qa/audio/original_analysis.json` | the raw measurements behind every number below (numbers only — no audio) |
| `game/tools/audio-analyze.py` | the analyser that produced them (numpy only) |
| `Pogostuck_Original_Physics_Extraction.md` | the earlier extraction of the same program; this file reuses its address convention (`IMG:0x…` = byte offset in the decoded image) |

## 0. Scope, rules, evidence tags

* **Not touched by this task:** Physics Core (`game/src/sim/**`), `Pogostuck_Physics_LOCKED_SPEC.md`, Map System V2 (`game/src/map/**`). No physics constant is added anywhere. Audio tuning numbers live in `game/src/audio/system/audioConfig.ts` and are tagged by origin (§1 of the spec).
* **Nothing is guessed.** A claim without evidence is written **UNKNOWN** and listed in §5 with the cheapest test that would resolve it.
* **Originality.** The 11 WAVs are *analysis inputs only*. They are not copied into the repository, the bundle or the APK (repository rule in `Pogostuck_Original_Physics_Extraction.md`: the new game must not copy original code text, levels, models, textures, **audio** or branding; `DECISIONS.md` DEC-030 and X-10/X-11: audio is procedural, only *timings* are referenced). Only measured *numbers* are kept.
* **Evidence tags** (same discipline as the physics extraction):

| Tag | Meaning |
|---|---|
| **EXTRACTED** | read literally from a file: a RIFF header field, a decoded script line (with its address), a table entry |
| **DERIVED** | computed from the samples by a stated formula (peak, RMS, loudness, spectrum, envelope…) |
| **INFERRED** | a conclusion drawn from ≥ 2 pieces of evidence; the evidence is named |
| **ESTIMATED** | a heuristic reading (e.g. a description of how a sound "feels"); never used as a rule |
| **UNKNOWN** | no evidence available |

Confidence words: **HIGH** = at least two independent EXTRACTED/DERIVED evidences agree · **MEDIUM** = one EXTRACTED evidence plus an INFERRED step · **LOW** = INFERRED only, or contradicted in part.

## 1. Inputs and method

### 1.1 What was and was not available

| Item | Status |
|---|---|
| `Pogostuck_Original_Audio.zip` — 11 WAV files | **supplied** (hashes in §1.3) |
| `Pogostuck.exe` — the game script (decoded overlay), which contains the whole `kuSound*` sound layer and every call site | **supplied** (earlier task); analysed here for audio |
| `pogoMain.dll` | **supplied**; imports only `KERNEL32`, `steam_api`, `IMM32`, `ADVAPI32`; 238 exports, none audio-related (`ackSuper*` = Steam/IO/LZSS) → **it plays no sound** (EXTRACTED: PE import/export tables) |
| `pogoSound.dll`, `kuSound.dll` | **NOT supplied.** The script calls `kuSoundDSB8*` (Play, Stop, SetVolume, SetSpeed, SetPan, Get/SetCurrentPosition, GetStatus, Release) and `kupack*` (`kupackRegistryLoad`, `kupackFindFile`, `kupackLoadBuffer`, `kupackLoadBuffer_namebased`; the archive name `1.kupack` is a string in the script) — none of these has a body in the script (EXTRACTED: no function header in the decoded listing), so they live in a DLL. Everything that would depend on them is **UNKNOWN** (§5) |
| `kupack_audio_index.csv` | **NOT supplied.** The file→ID packing, per-file loop points and any per-file gain are therefore **UNKNOWN**; the file *names* per ID come from the script's own source table (§3.1) |
| `CustomMaps.zip` | supplied earlier; contains **no audio** and no per-map audio data ("ambient audio is chosen by the game, not the map", `MAP_SYSTEM_V2_ANALYSIS.md`) — Map V2 audio zones are therefore a feature of the new game, not of the original map format |

### 1.2 Method (DERIVED metrics)

All numbers come from `game/tools/audio-analyze.py` (Python 3 + numpy, no other dependency). Definitions:

* **Format** — RIFF `fmt ` and `data` chunks parsed byte-wise; frames = data bytes / (channels × bytes per sample).
* **Peak / true peak** — max |sample| in dBFS; true peak = max after 4× FFT oversampling, in dBTP (a BS.1770-style estimate).
* **RMS** — over the whole file, both channels, no gating, dBFS. **Crest** = peak − RMS.
* **Loudness** — ITU-R BS.1770 K-weighting (pre-filter + RLB high-pass, 48 kHz-referenced coefficients re-derived for 44.1 kHz), 400 ms blocks with 75 % overlap, absolute gate −70 LUFS and relative gate −10 LU → **LUFS-I**. Files shorter than 400 ms (`pogoLoad2`, `pogoLaunch2`) have no complete block: only the **ungated K-weighted mean** is meaningful (printed as "LUFS ungated"; LUFS-I shows —). Cross-check against ffmpeg's `ebur128` on five files (`bounce1` −15.3 vs −15.5, `break2` −12.1 vs −11.8, `iceSlide` −17.7 vs −17.7, `pogoLaunch3` −17.9 vs −18.0, `pogoTime` −12.0 vs −12.1): ≤ 0.3 LU.
* **Envelope** — rectified, 5 ms RMS windows with 1 ms hop (dB). **Onset** = first time the envelope exceeds −60 dBFS. **Attack** = time from onset to the *envelope maximum* (a measure of "how long until the loudest moment", **not** a transient rise time; use *Rise 10→90 %* for that). **Decay −20 / −40 dB** = time from the envelope maximum until it has fallen 20 / 40 dB. **Effective length** = onset → last time within −60 dB. **RT60** (Schroeder backward integration, fit −5…−25 dB) is stored in the JSON but is **not** used here: for modulated or stationary sounds it is not a reverberation time.
* **Spectrum** — Hann-windowed magnitude spectrum of the whole file (power-weighted): centroid, roll-off at 85 / 95 % cumulative energy, standard deviation about the centroid ("bandwidth"), flatness (geometric/arithmetic mean; ≈ 1 noise, ≪ 1 tonal), eight band-energy shares, strongest spectral lines (local maxima ≥ 12 dB above the local median, ≥ 40 Hz apart, ≥ 20 Hz, dB relative to the strongest bin at ≥ 20 Hz; broad humps and noise produce no line). **Sweep** = dominant-line and centroid trajectory from a 4096-point STFT, used only where ≥ 5 frames are valid; frames whose refined peak falls outside 30 Hz…Nyquist are dropped (a bug that produced NaN slopes for the three sub-bass files was found and fixed in this task).
* **Stereo** — L/R correlation and side/mid energy ratio.

### 1.3 Files

| File | SHA-256 (first 16 hex) | Bytes | RIFF chunks |
|---|---|---|---|
| `pogoLoad2.wav` | `e47fda83477077fd` | 18604 | `fmt ` 16 B · `data` 18560 B |
| `pogoLaunch2.wav` | `81e244af2fcadd8d` | 31892 | `fmt ` 16 B · `data` 31848 B |
| `pogoLaunch3.wav` | `31e127f9fec6add6` | 336512 | `fmt ` 16 B · `data` 336468 B |
| `bounce1.wav` | `c72d9d84d6c06147` | 281796 | `fmt ` 16 B · `data` 281752 B |
| `bounce2.wav` | `54ac95b134c414a6` | 117396 | `fmt ` 16 B · `data` 117352 B |
| `bounce3.wav` | `d200a42ac0e84fbe` | 118016 | `fmt ` 16 B · `data` 117972 B |
| `bounce4.wav` | `fd9926644e8cf754` | 162296 | `fmt ` 16 B · `data` 162252 B |
| `break1.wav` | `bee662c1ab3153c4` | 91672 | `fmt ` 16 B · `data` 91628 B |
| `break2.wav` | `5327b958c99c853f` | 328412 | `fmt ` 16 B · `data` 328368 B |
| `iceSlide.wav` | `99d7f0f74cd5ba47` | 246760 | `fmt ` 16 B · `data` 246716 B |
| `pogoTime.wav` | `e8ceb99d1cfbbb1d` | 1131504 | `fmt ` 16 B · `data` 1131460 B |

All 11 are uncompressed PCM (`fmt ` tag 1, 16 bytes), 44 100 Hz, 16-bit, 2 channels, with only the `fmt ` and `data` chunks — **EXTRACTED**.

## 2. The 11 files, measured

### 2.1 Format and level (DERIVED)

| File | ID | Duration s | Frames | Rate Hz | Ch | Bits | Peak dBFS | True peak dBTP | RMS dBFS | Crest dB | LUFS-I | LUFS ungated | LUFS mom. max | Clipped samples |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `pogoLoad2.wav` | 8 | 0.105 | 4640 | 44100 | 2 | 16 | -4.57 | -4.52 | -21.80 | 17.2 | — | -16.99 | — | 0 |
| `pogoLaunch2.wav` | 7 | 0.181 | 7962 | 44100 | 2 | 16 | -0.03 | 0.36 | -28.61 | 28.6 | — | -22.59 | — | 0 |
| `pogoLaunch3.wav` | 287 | 1.907 | 84117 | 44100 | 2 | 16 | -1.00 | -0.98 | -15.41 | 14.4 | -17.97 | -19.36 | -13.88 | 0 |
| `bounce1.wav` | 50 | 1.597 | 70438 | 44100 | 2 | 16 | -0.71 | -0.70 | -18.24 | 17.5 | -15.55 | -15.24 | -10.13 | 0 |
| `bounce2.wav` | 51 | 0.665 | 29338 | 44100 | 2 | 16 | -2.43 | -2.42 | -16.15 | 13.7 | -13.44 | -13.34 | -11.23 | 0 |
| `bounce3.wav` | 52 | 0.669 | 29493 | 44100 | 2 | 16 | -2.06 | -2.06 | -15.41 | 13.3 | -12.86 | -12.54 | -10.40 | 0 |
| `bounce4.wav` | 53 | 0.920 | 40563 | 44100 | 2 | 16 | -0.10 | -0.07 | -14.35 | 14.3 | -10.69 | -11.21 | -8.16 | 0 |
| `break1.wav` | 156 | 0.519 | 22907 | 44100 | 2 | 16 | -0.26 | -0.23 | -16.95 | 16.7 | -16.69 | -15.33 | -14.20 | 0 |
| `break2.wav` | 177 | 1.861 | 82092 | 44100 | 2 | 16 | -1.94 | -1.78 | -12.19 | 10.3 | -11.76 | -12.51 | -7.82 | 0 |
| `iceSlide.wav` | 272 | 1.399 | 61679 | 44100 | 2 | 16 | -8.13 | -8.08 | -23.55 | 15.4 | -17.74 | -18.07 | -17.14 | 0 |
| `pogoTime.wav` | 145 | 6.414 | 282865 | 44100 | 2 | 16 | 0.00 | 0.57 | -14.26 | 14.3 | -12.06 | -11.98 | -5.11 | 1799 |

Reading: the mastering is "hot": nine of the eleven peak above −2.5 dBFS and two (`pogoLaunch2`, `pogoTime`) exceed 0 dBTP between samples; `pogoTime` has 1 799 samples at full scale (clipped at source). DC offsets are negligible except `break2` (L −0.021 / R +0.019 of full scale, opposite signs: slow baseline wander) and `pogoLaunch3` (+0.006); the new sounds are rendered without DC. `iceSlide` is the quiet one (peak −8.1 dBFS, −17.7 LUFS). **Loudness differences between files are inherent in the assets**; in the original they are further shaped by the *call volume* (§3.2): charge and standard launch are played at 50, bounces at 40–50, power launch at 90–100, ice slide at 20–70 (modulated), time sting at 100.

### 2.2 Time behaviour (DERIVED)

| File | Onset ms | Attack (onset → envelope max) ms | Rise 10→90 % ms | Decay −20 dB ms | Decay −40 dB ms | Effective length ms | Trailing silence ms | First 20 ms dB re peak | Last 50 ms dB re peak | Start/end step (FS) |
|---|---|---|---|---|---|---|---|---|---|---|
| `pogoLoad2.wav` | 0.00 | 2.5 | 0.0 | 66 | 96 | 98 | 1 | -3.6 | -22.2 | 0.0058 |
| `pogoLaunch2.wav` | 0.39 | 85.9 | 24.9 | 19 | 36 | 122 | 55 | -28.3 | -73.0 | 0.0000 |
| `pogoLaunch3.wav` | 0.45 | 221.5 | 191.6 | 487 | 1423 | 1645 | 0 | -35.1 | -50.7 | 0.0024 |
| `bounce1.wav` | 0.07 | 57.3 | 21.0 | 1096 | 1437 | 1494 | 59 | -4.4 | -70.6 | 0.0000 |
| `bounce2.wav` | 0.09 | 23.4 | 20.0 | 459 | 552 | 575 | 61 | -4.6 | -79.1 | 0.0000 |
| `bounce3.wav` | 0.11 | 15.4 | 11.0 | 467 | 628 | 643 | 18 | -3.4 | -41.7 | 0.0001 |
| `bounce4.wav` | 0.05 | 92.2 | 69.8 | 594 | 790 | 882 | 17 | -13.8 | -47.3 | 0.0001 |
| `break1.wav` | 0.00 | 73.3 | 69.8 | 186 | 377 | 450 | 18 | -4.2 | -51.9 | 0.0044 |
| `break2.wav` | 0.70 | 16.8 | 2.0 | 1315 | 1735 | 1753 | 17 | -1.1 | -58.4 | 0.0004 |
| `iceSlide.wav` | 0.02 | 595.1 | 585.7 | 800 | 800 | 1395 | 0 | -6.6 | -7.6 | 0.0083 |
| `pogoTime.wav` | 0.00 | 56.4 | 52.9 | 4944 | 6240 | 6296 | 52 | -6.7 | -78.1 | 0.0012 |

Reading: `pogoLoad2` is a 105 ms click that is cut abruptly (last 50 ms still −22 dB re peak, 1 ms of trailing silence). `pogoLaunch2` is **not** a decaying click: its envelope *rises* for ≈ 80 ms (first 20 ms are −28 dB re peak) and peaks at ≈ 86 ms, then dies in ≈ 40 ms. `iceSlide` has a flat envelope (first 20 ms −6.6 dB, last 50 ms −7.6 dB re peak, no silence, start/end step 0.008 FS) — built to loop. The four `bounce` files have long, slow decays (−20 dB after 0.46–1.1 s) and 15–92 ms to their loudest moment.

### 2.3 Spectrum (DERIVED)

| File | Centroid Hz | Roll-off 85 % Hz | Roll-off 95 % Hz | Bandwidth σ Hz | Flatness | Strongest spectral lines Hz (dB re strongest line ≥ 20 Hz) | L/R correlation | Side/Mid dB |
|---|---|---|---|---|---|---|---|---|
| `pogoLoad2.wav` | 4888 | 8296 | 10788 | 3334 | 3.3e-02 | 452 (0) | 1.0000 | -57.2 |
| `pogoLaunch2.wav` | 4786 | 6234 | 10713 | 2584 | 2.6e-02 | — | 1.0000 | -57.5 |
| `pogoLaunch3.wav` | 161 | 29 | 308 | 778 | 2.8e-03 | 25 (0), 1454 (-32), 4307 (-40), 6429 (-43), 9604 (-50) | 0.9707 | -18.3 |
| `bounce1.wav` | 1152 | 1294 | 1361 | 156 | 3.9e-05 | 1060 (0), 1178 (-0), 1296 (-4), 942 (-8), 1119 (-9) | 1.0000 | -47.4 |
| `bounce2.wav` | 1208 | 1530 | 1649 | 288 | 3.4e-05 | 1532 (0), 1414 (-1), 1297 (-2), 1179 (-3), 1649 (-4) | 1.0000 | -46.9 |
| `bounce3.wav` | 1290 | 1766 | 1884 | 372 | 4.3e-05 | 1767 (0), 1650 (-1), 1532 (-3), 1884 (-3), 767 (-5) | 1.0000 | -46.5 |
| `bounce4.wav` | 1128 | 1313 | 1659 | 258 | 1.3e-05 | 855 (-7), 723 (-13), 1908 (-16), 592 (-20), 783 (-21) | 1.0000 | -46.5 |
| `break1.wav` | 765 | 1707 | 4420 | 1606 | 6.8e-03 | 262 (-10) | 0.9968 | -27.9 |
| `break2.wav` | 290 | 239 | 1376 | 915 | 2.7e-03 | 7373 (-35) | 0.7848 | -9.2 |
| `iceSlide.wav` | 6120 | 10942 | 14306 | 4691 | 2.1e-01 | 252 (-4), 16850 (-13) | 0.7872 | -9.2 |
| `pogoTime.wav` | 1210 | 1789 | 3307 | 1200 | 5.8e-03 | 44 (0), 455 (-10), 844 (-12), 366 (-14), 2204 (-18) | 0.8765 | -11.8 |

Band energy shares:

| File | infra <20 Hz | sub 20–60 | bass 60–250 | low-mid 250–500 | mid 0.5–2 k | high-mid 2–4 k | presence 4–8 k | air >8 k |
|---|---|---|---|---|---|---|---|---|
| `pogoLoad2.wav` | 0.0 % | 0.0 % | 1.2 % | 9.0 % | 15.2 % | 15.6 % | 42.4 % | 16.5 % |
| `pogoLaunch2.wav` | 0.0 % | 0.0 % | 0.6 % | 1.2 % | 5.7 % | 27.2 % | 54.7 % | 10.6 % |
| `pogoLaunch3.wav` | 1.2 % | 88.6 % | 4.6 % | 1.2 % | 2.0 % | 1.5 % | 0.7 % | 0.2 % |
| `bounce1.wav` | 0.0 % | 0.0 % | 0.0 % | 0.1 % | 99.7 % | 0.2 % | 0.0 % | 0.0 % |
| `bounce2.wav` | 0.0 % | 0.0 % | 0.0 % | 0.1 % | 99.2 % | 0.7 % | 0.0 % | 0.0 % |
| `bounce3.wav` | 0.0 % | 0.0 % | 0.0 % | 0.0 % | 97.9 % | 2.0 % | 0.0 % | 0.0 % |
| `bounce4.wav` | 0.0 % | 0.0 % | 0.0 % | 0.0 % | 99.0 % | 0.9 % | 0.0 % | 0.0 % |
| `break1.wav` | 4.0 % | 51.7 % | 12.4 % | 7.6 % | 10.7 % | 5.9 % | 7.1 % | 0.5 % |
| `break2.wav` | 8.8 % | 25.2 % | 51.5 % | 6.0 % | 5.2 % | 1.8 % | 1.1 % | 0.4 % |
| `iceSlide.wav` | 0.2 % | 0.3 % | 0.6 % | 6.4 % | 21.8 % | 15.0 % | 14.3 % | 41.4 % |
| `pogoTime.wav` | 0.5 % | 7.7 % | 3.3 % | 4.7 % | 70.8 % | 9.7 % | 2.9 % | 0.5 % |

Spectral trajectory (STFT):

| File | Frames used | Dominant line start → end Hz | Dominant slope oct/s | Centroid start → end Hz | Reading |
|---|---|---|---|---|---|
| `pogoLoad2.wav` | 6 | 443 → 444 | -26.44 | 4854 → 2554 | only 6 usable frames (0.105 s): trajectory not meaningful; fixed ring near 440 Hz |
| `pogoLaunch2.wav` | 9 | 436 → 2251 | +10.90 | 2372 → 3829 | only 9 usable frames (0.18 s): trajectory not meaningful |
| `pogoLaunch3.wav` | 55 | 212 → 57 | -0.73 | 1037 → 189 | sub-bass (below the STFT's usable range) + a downward whistle in the first 0.3 s |
| `bounce1.wav` | 122 | 829 → 997 | -0.06 | 870 → 976 | flat tone around 0.8–1.4 kHz |
| `bounce2.wav` | 45 | 715 → 1416 | +2.36 | 773 → 1415 | rising glide 0.7 → 1.4 kHz (≈ +1 octave), then steady |
| `bounce3.wav` | 48 | 826 → 1648 | +2.62 | 863 → 1607 | rising glide 0.8 → 1.65 kHz (≈ +1 octave), then steady |
| `bounce4.wav` | 64 | 859 → 997 | -0.15 | 860 → 943 | undulating 0.86–1.9 kHz (≈ 4–5 cycles), net flat |
| `break1.wav` | 26 | 64 → 44 | -1.28 | 1129 → 202 | energy collapses from broadband (1.1 kHz centroid) to < 250 Hz |
| `break2.wav` | 110 | 48 → 55 | +0.20 | 89 → 516 | sustained 30–140 Hz rumble; centroid drifts up as the rumble decays |
| `iceSlide.wav` | 116 | 515 → 283 | -0.87 | 6291 → 5150 | no sweep: stationary noise (dominant line wanders at random) |
| `pogoTime.wav` | 506 | 127 → 3919 | +0.70 | 620 → 2242 | long rise 0.13 → 3.9 kHz (≈ +0.7 oct/s) over several parallel partials |

Reading:

* **`bounce1–4`** are *line spectra*: flatness ≈ 10⁻⁵, 98–99.7 % of the energy between 0.5 and 2 kHz, and the strongest lines sit **≈ 118 Hz apart** in all four (1060 / 1178 / 1296 Hz in `bounce1`; 1179 / 1297 / 1414 / 1532 Hz in `bounce2`; 1532 / 1650 / 1767 / 1884 Hz in `bounce3`). A ≈ 118 Hz comb with a resonance-like centre that is steady (`bounce1`), glides up ≈ one octave (`bounce2`, `bounce3`) or undulates ≈ 5 times (`bounce4`) is the signature of a *buzzing, rubbery "boing"* — a pulse train of ≈ 118 Hz passing a resonance. L/R correlation 1.0000 and side/mid ≈ −47 dB: effectively mono.
* **`pogoLoad2`** and **`pogoLaunch2`** are bright and short: centroids 4.9 and 4.8 kHz, 59 % and 65 % of the energy above 4 kHz, near-mono (side/mid −57 dB). `pogoLoad2` has a ≈ 440 Hz ring (strongest line) decaying with the click.
* **`pogoLaunch3`** is a sub-bass event: 89 % of the energy at 20–60 Hz (strongest line 24.9 Hz, only 1.2 % below 20 Hz), centroid 161 Hz, with a faint downward whistle (≈ 2.5 kHz → 0.7 kHz) in the first 0.3 s. Almost all of it is below what a phone speaker reproduces.
* **`break1`** / **`break2`** are broadband crunches over a sub-bass body (`break1`: 52 % at 20–60 Hz, a 64 Hz line in its first 50 ms; `break2`: 25 % at 20–60 Hz, 52 % at 60–250 Hz, 9 % below 20 Hz), with many individual crack onsets (`break1`: 30, 73, 122, 167, 224 ms; `break2`: ≈ 14 within the first 1.15 s). `break2` is wide (L/R correlation 0.78).
* **`iceSlide`** is stationary broadband noise: flatness 0.21 (the highest of the set), centroid 6.1 kHz, 41 % of the energy above 8 kHz, only 1.1 % below 250 Hz; wide stereo (correlation 0.79).
* **`pogoTime`** is a 6.4 s *rising-partials* event: parallel harmonic lines rising from ≈ 0.5 kHz toward 4 kHz (visible in the spectrogram), with a wavering, noisy first 1.3 s and a 44 Hz body; 71 % of its energy in 0.5–2 kHz.

### 2.4 Function of each file — classification

"Measured character" is DERIVED; "function" combines it with the call-site evidence of §3. Confidence is stated separately for **what the sound is** and for **the label the user gave it**.

| File (ID) | Measured character | Function in the original game | Confidence — original function | User's label | Confidence — label |
|---|---|---|---|---|---|
| `pogoLoad2` (8) | 105 ms bright tick with a 440 Hz ring, 2.5 ms to its loudest moment | **Spring-load click**: played once when ground contact begins (§3.6), single instance, cut when the launch fires | **HIGH** (name + acoustics + 4 call sites + stop-at-launch) · *trigger meaning "landing"*: MEDIUM (EXTRACTED structure, INFERRED meaning) | Charge | **MEDIUM** — it is the "load" sound; the new game binds it to the start of the hold (design, spec §4) |
| `pogoLaunch2` (7) | 181 ms swell into a click, 4–8 kHz | **Standard launch**: plays on **every** launch, both ordinary and power jumps; 2D | **HIGH** (call at the single launch block; remote copy; intro) | Launch variant | **HIGH** as the standard launch |
| `pogoLaunch3` (287) | 1.9 s sub-bass boom with downward whistle | **Power-jump layer**: played by `effectPlayerPowerJumpInit` (default jump-effect types 0/1) when the launch is a power jump (spring was above 95); also double jump, rings, cut-scenes. Layered **on top of** `pogoLaunch2` | **HIGH** | Launch variant | **MEDIUM** — it is a *layer on boosted launches*, not an alternative picked at random (§4) |
| `bounce1–4` (50–53) | 0.67–1.6 s buzzing, rubbery "boing", ≈ 1 kHz, 118 Hz comb | **Boing of bouncy entities**: caterpillar bones, entities flagged bouncy, eggs; chosen **uniformly at random** among the four; **no impact-speed dependence found** | **HIGH** (6 call sites, identical pattern) | Collision variants | **MEDIUM** — fits acoustically; "scaled by impact speed" is a design choice (§4) |
| `break1` (156) | 0.52 s crack burst + 64 Hz thump | Map-3 specific: shortcut-block key logic | **MEDIUM** — one call site; acoustically a break | Break variant | **MEDIUM** — generic use is design |
| `break2` (177) | 1.86 s heavy collapse, 30–140 Hz rumble + cracks | Map-3 specific: player-intro cut-scene | **MEDIUM** — one call site; acoustically a heavy break | Break variant | **MEDIUM** — generic use is design |
| `iceSlide` (272) | 1.4 s stationary broadband noise, flat envelope, loop-friendly | **Slide loop**: starts when slide mode is active, loops, volume and pitch follow slide speed, stops with a fast fade when slide mode ends | **HIGH** (loop flag, handle gating, modulation, stop) | Sliding loop | **HIGH** |
| `pogoTime` (145) | 6.4 s rising-partials sting, clipped at source | **Main-menu sting**: 2D one-shot at a one-time title-menu event (stage 6, §3.3); suppressible by the option `menuOptionDisableMoreSounds` | **HIGH** for its original use | Time effect | **LOW** — no gameplay "time effect" call site exists; the original's in-game time jingle is `parTime.wav` (ID 99, **not supplied**) |

## 3. What the original game does with them

### 3.1 The sound layer (EXTRACTED from the decoded script)

The script contains its own sound library, `kuSound*` (39 functions, `IMG:0x57d76f … 0x594f63`), written on top of DirectSound-style buffers (`kuSoundDSB8*`, DLL-side).

* **Source table** `kuSoundSources`: 369 slots × 136 bytes (`kuSoundPlayAtPos` bounds-checks `arg0 >= 369` and prints `invalid sourceID`). It is filled by `kuSoundSourcesInit_filesetup` @`0x58d5e2` through `kuSoundSourceInit_IDTypeNamePreload(id, type, file, preload)` @`0x57f1f9`: **357** initialised entries — **52 music** (type 1), **261 SFX** (type 2), **44 ambient** (type 3). Preload flag: SFX 253 × 1, 4 × 0, 4 × −1; music 50 × 0 (+2 other) — i.e. **short effects are preloaded, long music streams on demand**. All 11 analysed files are type 2, preload 1: IDs 7 `pogoLaunch2`, 8 `pogoLoad2`, 50–53 `bounce1–4`, 145 `pogoTime`, 156 `break1`, 177 `break2`, 272 `iceSlide`, 287 `pogoLaunch3`.
* **Playing.** `kuSoundPlayAtPos(id, &pos, loop, volume, speed, range)` @`0x58238f` → `kuSoundDuplicateDSB8(source.dsb8)` (every play is a *duplicate of the shared source buffer* = the original's "buffer cache + per-play voice") → `kuSoundCreate(dsb, id, loop·4 | source.flags, pos, volume, speed, 0, range)` @`0x581be9`. `kuSoundPlay2D(id, loop, volume, speed, pan)` @`0x5837e1` is `kuSoundPlayAtPos(id, 0, loop, volume, speed, 1)` followed by `instance.pan = pan`. Both return an instance handle, or **0 when the sound could not be played** (`kuSoundInitialized` false, `id ≥ 369`, `!source.isInitialized`, `source.isLoaded < 2`) — **a missing or not-yet-loaded sound is silently skipped, never an error**.
* **Instance** (88 bytes): `volume`, `speed` (pitch multiplier: `kuSoundDSB8SetSpeed`; `speed = 1` = normal — INFERRED from the 0.9…1.1 call values and the `0.1 + 0.9·fade/100` slowdown), `pan`, `range`, `fadePerc` / `fadeSpeed`, `flags` (1 positional · 4 loop · 128 music-type · 256 ambient · 1024 finished · 4096 timeout · 8192 paused · 32768 pause-on-menu · 131072 alternate fall-off · 262144 on-demand preload), `pointer` (an address the library zeroes when the instance is destroyed — the basis of *handle gating*, §3.4).
* **Mixer per frame** (`kuSoundUpdateFrame` @`0x589d41`): `dsbVolume = kuSoundMasterVolume × spatialGain × categoryVolume × (fadePerc / 100) × instance.volume`, `DSB8SetVolume`, `DSB8SetPan(instance.pan)`. Spatial gain (positional instances): `clamp(range·1.25 − dist / min(camera.right, 1500), 0, 1)`; alternate fall-off (flag 131072): `clamp(1.25 − dist / (W·range), 0, 1)` then `g ← g²·0.5 + 0.5·g`; pan `clamp(cos(camera.roll) · Δx / W · 0.2, −1, 1)`. Categories: music / SFX / ambient volumes.
* **Fades.** `kuSoundStop(handle, fadeSpeed)` @`0x58410b` only sets `instance.fadeSpeed`; each frame `fadePerc ← max(fadePerc + fadeSpeed·L, 0)` with `L = time_step·0.5` (`0x589ea0`). `fadeSpeed = −50` ⇒ −25 per time-step unit ⇒ silent in **≈ 0.25 s** *if* the engine's `time_step` unit is 1/16 s (**INFERRED**: the locked physics spec uses the same time unit, T = 1/16 s, `PhysicsConfig.ticksPerSecond = 16`, grade B — it is not read from the engine itself).
* **Instance utilities.** `kuSoundGetNumInstances(id)` and `kuSoundStopInstances(id, keep, fadeSpeed)` @`0x5948ad` (fades the oldest live instances until at most `keep` remain).
* **Voice limit / priority / channel system:** **none at script level** (no global counter, no priority field, no stealing). Anything of that kind inside the DLLs is **UNKNOWN**.

### 3.2 Call-site ledger (EXTRACTED)

`vol`/`pitch`/`range` are the literal arguments. "2D" = `kuSoundPlay2D` (no position); "pos" = `kuSoundPlayAtPos`.

| Sound | Caller @ address | Kind | loop | vol | pitch (`speed`) | range |
|---|---|---|---|---|---|---|
| `pogoLoad2` (8) | `playerMove` @`3da0ba` (local) | pos | 0 | 50 | 0.9 + random(0.1) | 1.0 |
| | `networkKuInterpolate` @`49900a` (remote player) | pos | 0 | 50 × `player_sfx_vol_fac` | 0.9 + random | 1 |
| | `playerOutroDo` @`25906f`, `playerMonoOutroDo` @`2bb5f3` (cut-scenes) | pos | 0 | 50 | 0.9 + random | 1.0 |
| `pogoLaunch2` (7) | `playerMove` @`3e1a7e` (local, every launch) | **2D** | 0 | 50 × `soundPrevVersionCompatibilityFac` | 0.9 + random(0.2) | 1 |
| | `networkKuInterpolate` @`4992d3` (remote) | pos | 0 | 50 × `player_sfx_vol_fac` | 0.9 + random | 1 |
| | `playerIntroDo` @`2559f1` | pos | 0 | 50 | 0.85 | 1.0 |
| `pogoLaunch3` (287) | `effectPlayerPowerJumpInit` @`20411d`, `2042c1` (jump-effect types 0 and 1) | pos | 0 | (90 + random(10)) × `La54` | 0.9 + random(0.2) | `L34` = 2 local / 1.5 remote |
| | `doubleJumpEffectCreate` @`221026` / `2212ef` | pos | 0 | (80 + r) / (90 + r) × factor | 1.15 + r / 0.9 + r | factor |
| | `monolithPlayerIntroFnc` @`2b4b22` · `map3PlayerIntroFnc` @`367cec`, `36ffab` | pos | 0 | 90 · 90 · 60 | 1.0 · 1.0 · 0.85 | 2 |
| | `map3RingsOFUpdate` @`2dc1d0` | pos | 0 | 80 + random | 1.15 + random | 2 |
| `bounce1–4` (50–53) | `playerMove` @`3de957` (caterpillar bone hit), `3dfccb` (bouncy-entity contact), `3e6a37`, `3e70a3` (second contact path) | pos | 0 | 40 + random(10) | 0.9 + random(0.2) (`3dfccb`, `3e70a3`: −0.3 more for entity type 34) | 2 |
| | `egg_act` @`3fc4ff`, `dungeonEgg_act` @`6419a2` | pos | 0 | 40 + random(10) | 0.9 + random(0.2) | 2 |
| `break1` (156) | `map3ShortcutsUpdate_keyLogic` @`3269f1` | pos | 0 | 90 | 0.75 | 1 |
| `break2` (177) | `map3PlayerIntroFnc` @`367c60` | pos | 0 | 100 | 1.0 | 3 |
| `iceSlide` (272) | `playerMove` @`3cc2e0` (level 9: `map3WaterSlide` ID 316 @`3cc17c`) | pos | **1** | 1, then `SetVolume` each frame | 1, then `SetSpeed` each frame | 1.5 |
| `pogoTime` (145) | `mainFrameMenu` @`577146` | **2D** | 0 | 100 × `kuSound2DSoundVolumeF` | 1 | – |

`kuSound2DSoundVolumeF = kuSound2DSoundVolume × 0.01` (@`58a443`); the numeric values of `soundPrevVersionCompatibilityFac` and `player_sfx_vol_fac` were not tracked (**UNKNOWN**; they scale volumes only).

### 3.3 The research questions, answered per sound

| Sound | Who calls it | In which event | Random? | Cooldown? | Volume modifier? | Pitch modifier? | 2D or 3D | Priority / channel |
|---|---|---|---|---|---|---|---|---|
| `pogoLoad2` | `playerMove` (local), `networkKuInterpolate` (remote), two outro functions | the frame in which ground contact **begins** (`!L2b4 && heroGroundContact` @`3d9525`, `L2b4` saved at `3d2c99`); remote: observed state change | pitch only | **no timer**; a *handle gate* (`if !KUSOUND_SFX_POGOLOAD` @`3d9fcd`) lets only one instance exist; the handle is zeroed when the instance dies | fixed 50 (× `player_sfx_vol_fac` for remote) | 0.9–1.0 | positional, range 1 (local player: centred by the camera) | none |
| `pogoLaunch2` | `playerMove`, `networkKuInterpolate`, `playerIntroDo` | every launch (single launch block, both the power and the ordinary branch; `arg0.powerJumpNext = 0` @`3e195f`, sound @`3e1a7e`) | pitch only | none (launch rate is bounded by physics) | 50 × compat. factor | 0.9–1.1 | **2D** locally, positional for remote | none; it also **stops the charge sound** (`kuSoundStop(POGOLOAD, −50)` @`3e1aa7`, handle cleared @`3e1ac4`) |
| `pogoLaunch3` | `effectPlayerPowerJumpInit`, called by `playerMove` @`3e11af` on a power jump: `(arg0.powerJumpNext …) && arg0.heroSpringLoadedLast > 95` @`3e110c`; also `doubleJumpEffectCreate`, rings, cut-scenes | power jump (boosted launch) | volume +random(10), pitch +random(0.2) | none; the effect re-init fades a previous instance (`kuSoundStop(h, −50)` @`203cbd`) | (90…100) × `La54` (1 local, `player_sfx_vol_fac` remote) | 0.9–1.1 | positional, range 2 (local) / 1.5 (remote) | none; remote effect sounds only if `kuSoundPlayerSounds` is on |
| `bounce1–4` | `playerMove` (4 sites), `egg_act`, `dungeonEgg_act` | contact with an entity flagged bouncy (`skill[98] & 144`) or a caterpillar bone; the same block adds a fixed push to the player's speed — **independent of impact speed** | **yes: `50 + int(random(4))` — uniform, no anti-repeat** | none found at the call sites | 40 + random(10) | 0.9 + random(0.2); −0.3 for entity type 34 | positional, range 2 | none |
| `break1` | `map3ShortcutsUpdate_keyLogic` | breaking a map-3 shortcut block | no | none | 90 | 0.75 | positional, range 1 | none |
| `break2` | `map3PlayerIntroFnc` | map-3 intro cut-scene | no | none | 100 | 1.0 | positional, range 3 | none |
| `iceSlide` | `playerMove` | slide mode (`arg0.flags & 1` @`3cb980`) is active and no handle exists | start offset random (`DSB8SetCurrentPosition(random(length))` @`3cc3e3`) | handle gate (one instance) | `min(20 + 1.5·\|slide\|, 70)`; level 9: `min(25 + 1.5·\|slide\|, 75)` | `min(0.675 + 0.01·\|slide\|, 1)`; level 9: `min(0.85 + 0.015·\|slide\|, 1.25)` | positional, range 1.5, follows the player (`kuSoundUpdatePos` each frame) | none |
| `pogoTime` | `mainFrameMenu` | once, in title-menu stage 6 (`L40 = menuTitleStage == 6` @`575321`) when the menu character's one-time flag `skill[23]` is still 0 (@`577046`–`5770c8`), unless `menuOptionDisableMoreSounds` (@`5770e6`) | no | one-time flag | 100 × option factor | 1 | **2D** | none |

### 3.4 Patterns the original uses (EXTRACTED) — the basis of the new system's rules

| # | Pattern | Where | Used for |
|---|---|---|---|
| P1 | **No immediate repeat, "reroll to the next index"**: `i = int(random(N)); if (i == last) i = (i+1) % N; last = i` | `slipSoundLast` (static) @`3e3623`–`3e3663` (slime slip, N = 3); `monoThornLayerHorrorSndID` @`3e8e89`–`3e8ec9` (thorn horror, N = 4) | variant families that fire repeatedly |
| P2 | **Concurrency cap with a random margin**: `if (kuSoundGetNumInstances(45) + random(2) < 5) play(45, vol 40+random(15), pitch 0.9+random(0.2), range 1)` | `p_boostEffect_shotgunPellet_fade` @`17cd6b`/`17cdfa` (ricochet, many hits per frame) | the original's "no machine gun" guard |
| P3 | **Keep-N-and-fade** `kuSoundStopInstances(id, keep, −50)` | `playerEmoteDo` @`24cf28`, `24d169`, `24d1a4` | cut the previous voice line before the next |
| P4 | **Handle gating**: store the handle (`kuSoundSetPointer(h, &HANDLE)`), play only if the handle is 0, clear it on stop | `KUSOUND_SFX_POGOLOAD` @`3d9fcd`/`3da10e`, `KUSOUND_ICE_SLIDE` @`3cc0bc` | one instance of the charge click and of the slide loop |
| P5 | **Option gates**: `kuSoundPlayerSounds` (other players' effect sounds), `kuSoundCharacterVoices` (voice vs thud), `menuOptionDisableMoreSounds` (menu sting), `kuSound2DSoundVolumeF` | various | user options |
| P6 | **Random jitter on every one-shot**: pitch `0.9 + random(0.2)` (or `0.1`, `0.15`), volume `+ random(10…15)` | all call sites in §3.2 | "never identical twice" |
| P7 | **Range classes**: 1.0, 1.5, 2, 3 (multiples of the camera half-width) | §3.2 | how far a sound carries |
| P8 | **Lazy loading by flag**: `preLoadWanted`, `kuSoundPreloadFiles` (on-demand preload for type 1 and flag 262144) | `kuSoundPlayAtPos` @`582608`–`5827d6` | music streams; SFX preloaded |
| P9 | **Silent failure**: play returns 0 for an invalid / not-loaded source | `kuSoundPlayAtPos` @`58241e`–`582849` | missing audio never throws |

### 3.5 Ice slide in detail (EXTRACTED, `playerMove` @`3cc0bc`–`3ccaed`)

* **Active when** slide mode `arg0.flags & 1` is set (@`3cb980`). In that branch `slideSpeed` is driven toward the slide target (`slideSpeed += clamp((target − slideSpeed)·0.25, ±1.35)·time_step`, already in `Pogostuck_Physics_LOCKED_SPEC.md` E15) — **the audio reads that state, it does not own it**.
* **START** when the handle is 0: `kuSoundPlayAtPos(272, &pos, loop = 1, vol = 1, speed = 1, range = 1.5)` and keep the handle; then jump to a random position inside the buffer (`DSB8GetCurrentPosition`, `random`, `DSB8SetCurrentPosition`). On level 9 the source is `map3WaterSlide` (ID 316) plus a one-off splash (ID 275).
* **MODULATE** every frame while the handle exists: `kuSoundPause(h, 0)` (un-pause), `kuSoundUpdatePos(h, &pos)`, `m = |slideSpeed|`, `SetSpeed(h, min(0.675 + 0.01·m, 1))`, `SetVolume(h, min(20 + 1.5·m, 70))`. Both curves saturate at `m ≈ 32.5` (speed) and `m ≈ 33.3` (volume), below the slide target speed of 48 Q/T, so in steady sliding the loop sits at full pitch and volume; it is quieter and lower only while the slide is still accelerating or has decayed.
* **STOP** in the "not in slide mode" branch (@`3cca9c`–`3ccae7`): `kuSoundPause(h, 0); kuSoundStop(h, −50); h = 0` — the loop fades out in ≈ 0.25 s (unit INFERRED, §3.1). In that branch `slideSpeed` is zeroed if `|slideSpeed| < 0.25` or the player is airborne, else it decays (`0.5·time_step`).
* Whether the slide state ends in flight: slide mode is only set or cleared at a landing, so after launching from ice the flag can stay set until the next landing; the original's loop therefore keeps following `|slideSpeed|` until the branch is left. **The new system adds the extra condition "grounded"** so that a sound is never heard in mid-air (design, spec §7).

### 3.6 Charge, launch and power jump in detail (EXTRACTED)

* **Charge click (`pogoLoad2`).** The block at @`3d9525` runs in the frame when ground contact starts. After the level-specific bookkeeping in that block, `if !KUSOUND_SFX_POGOLOAD` → `kuSoundPlayAtPos(8, …, 50, 0.9 + random(0.1), 1.0)`, then `kuSoundSetPointer`. **Meaning (INFERRED, MEDIUM):** the spring starts loading on landing, so the "load" click marks that moment. No loop, no pitch-with-charge: there is **no continuous charge sound in the original**.
* **Launch (`pogoLaunch2`).** In the launch block: `playerLocalJustJumped = 1` @`3e0eba`; rumble strength from the spring load `min(heroSpringLoaded·0.5, 100)·0.75` @`3e0f60`; **power-jump branch** if `(powerJumpNext …) && heroSpringLoadedLast > 95` @`3e110c` (`playerLocalJustJumped = 2`, `effectPlayerPowerJumpInit(…, int(sk_jumpEffect_active), 5.5)` @`3e11af`, `pTrailDuration = 5.5`) else the ordinary branch (rumble × 0.5); then for **both** `kuSoundPlay2D(7, 0, 50·fac, 0.9 + random(0.2), 0)` @`3e1a7e` and the charge click is cut (`kuSoundStop(POGOLOAD, −50)`).
* **Power jump (`pogoLaunch3`).** `effectPlayerPowerJumpInit` selects by the equipped cosmetic `sk_jumpEffect_active` (0…12): types 0 and 1 (the default family) spawn flash particles (`p_explo_flash1`, `p_explo_new1`, `p_explo_new2`, `p_boostEffect_shotgunFlash`) and play ID 287; the other types play other sources (IDs 10, 46, 60, 61 + n, 64, 216, 254, 273, 310, 337 + n). The new game has the default only.
* **In the new game's physics** the same two facts exist as sim events: `launch` (always) and `boost` (emitted on the same tick when the launch is a power jump: `s.boost ≥ 1 ∧ L_last > loadMaxFloor`, `game/src/sim/core/launch.ts`) — the audio can follow the original's structure exactly without touching physics.

### 3.7 Other original sounds that are relevant (not supplied)

* **Ground impact** is **not** a bounce sound: the contact code plays `thud1–3` (IDs 151–153) at fixed volume (`(95 + random(5))·fac` 2D, pitch 0.95 + random(0.15), @`3e50de`; positional `40 + random(10)` @`3e9169`) **or** a character voice `dudeOof1–5` (IDs 76–80) when `kuSoundCharacterVoices` is on (@`3e4eed`). **No speed-dependent volume exists at these sites.** `thud*` and `dudeOof*` were not supplied.
* **`parTime.wav` (ID 99)** is the original's time jingle: credits, dungeon intro/score, and the player-death handler (`playerMove` @`3b7152`, positional, vol 100, range 3).
* **`slimeSlip1–3` (89–91)** use the P1 anti-repeat; **`thornHorror1–4` (255–258)** the same.

## 4. The requested mapping versus the evidence

The user's mapping is a **design target** for the new game ("inspired by the organisation and behaviour of the original, designed for the new Android game"). Where the original differs, this is stated so the design is a decision, not a claim about the original.

| Requested | What the original does (evidence) | Consequence for the new system |
|---|---|---|
| `bounce1–4` = collision variants, chosen by **impact speed**, volume scaled by speed | boing of bouncy **entities**; uniform random of 4; volume `40 + random(10)`; **no speed dependence found** (§3.3); ordinary ground impacts use `thud*`/voices | variant choice stays uniform-random with the original's own anti-repeat (P1) — an **ORIGINAL-INSPIRED** rule; scaling by impact speed, a minimum-impact threshold, a cooldown and a burst cap are **DESIGN** (audio only) and labelled as such |
| `break1–2` = break variants | each used at one map-3 site only; no generic break call site found | random no-repeat between the two for Map V2 `break` events — **DESIGN** |
| `pogoLaunch2/3` = launch variants | not alternatives: **2 always, 3 added on power jumps** | modelled as a *layered* event (standard always; power layer when the sim emits `boost`) — **ORIGINAL** structure |
| `pogoLoad2` = charge | click at **start of ground contact**, single instance, cut at launch; **no continuous sound** | one click on the sim's `charge_start`, single instance, cut at launch, **no per-frame sound** — **ORIGINAL** shape, **DESIGN** trigger (hold start instead of landing) |
| `iceSlide` = sliding loop, START / LOOP-MODULATE / STOP, only when really sliding | exactly that (§3.5), formulas included | the original's modulation formulas are reused verbatim (they are audio mapping coefficients extracted from the audio code, not physics constants); the extra "grounded + slippery surface" condition is **DESIGN** |
| `pogoTime` = time effect | menu sting at a menu event; the time jingle is a different, unsupplied file | `TIME_EFFECT` exists as an event with this character; its gameplay trigger (Map V2 `split`) is **DESIGN** |
| "priority / channel system" | none at script level; DLL-side unknown | a voice pool with priorities is **DESIGN** (needed for mobile voice limits) |

## 5. UNKNOWN — and the cheapest test that would resolve it

| # | UNKNOWN | Why | Cheapest resolving test |
|---|---|---|---|
| U1 | DSB-level volume scale (linear amplitude vs dB), `SetSpeed` → playback-rate mapping, pan law, hardware voice limit, clipping behaviour | `kuSoundDSB8*` live in `pogoSound.dll` / `kuSound.dll`, not supplied | supply the DLLs; read the exported `kuSoundDSB8SetVolume/SetSpeed/SetPan` |
| U2 | file ↔ ID packing in `1.kupack`, per-file gain, loop points | `kupack_audio_index.csv` and the DLL not supplied | supply the CSV; compare file sizes with §1.3 |
| U3 | numeric values of `soundPrevVersionCompatibilityFac`, `player_sfx_vol_fac`, `kuSound2DSoundVolume` defaults | assigned outside the call sites; not traced | grep their assignments in the decoded script (cheap) |
| U4 | unit of the fade rate (`time_step` = 1/16 s?) | supported by the physics spec's time unit (T = 1/16 s, grade B) but not read from the engine | decode `*time_step` initialisation in `acknex.dll` / observe a recorded fade |
| U5 | any priority / voice stealing inside the DLLs | DLLs not supplied | supply the DLLs |
| U6 | exact semantics of the original "trigger on ground-contact start" for `pogoLoad2` (landing vs first touch after a bounce) | structure extracted, meaning inferred | run the original with an audio log, or trace `heroGroundContact` writes |
| U7 | loudness balance the original *actually* produced (call volume × DSB scale × source level) | needs U1 | needs U1 |
| U8 | how `iceSlide.wav` was meant to loop (loop points, whether the DLL cross-fades) | needs U2 | needs U2 |
| U9 | what the player hears for impacts in the original on non-bouncy surfaces beyond `thud*`/voices (not supplied) | files absent | supply `thud1–3`, `dudeOof1–5` |

## 6. Evidence ledger

| ID | Tag | Claim | Evidence |
|---|---|---|---|
| AU-01 | EXTRACTED | 11 files: PCM, 44.1 kHz, 16-bit, stereo, only `fmt `+`data` | RIFF headers (§1.3) |
| AU-02 | EXTRACTED | `kuSoundSources` has 369 slots × 136 B; 357 entries (52 music / 261 SFX / 44 ambient) | `kuSoundPlayAtPos` @`58245e`, `582535`; `kuSoundSourcesInit_filesetup` @`58d5e2` |
| AU-03 | EXTRACTED | IDs 7, 8, 50–53, 145, 156, 177, 272, 287 = the 11 files; all type 2, preload 1 | `kuSoundSourceInit_IDTypeNamePreload` table |
| AU-04 | EXTRACTED | Play = duplicate of a shared source buffer | `kuSoundDuplicateDSB8` @`582948`, `582ce9` |
| AU-05 | EXTRACTED | Play returns 0 for invalid / uninitialised / not-loaded source (no error) | `kuSoundPlayAtPos` @`58241e`–`582849` |
| AU-06 | EXTRACTED | `kuSoundPlayAtPos(id,pos,loop,vol,speed,range)`; `Play2D(id,loop,vol,speed,pan)` = PlayAtPos with no position | @`583412`, @`583885`, @`583902` |
| AU-07 | EXTRACTED | mixer formula and spatial gain/pan | `kuSoundUpdateFrame` @`58a50d`–`58c0d0` |
| AU-08 | EXTRACTED | `kuSoundStop` sets `fadeSpeed`; fade update uses `time_step·0.5` | @`584284`, @`589ea0`, @`58aa6c`, @`58ac38` |
| AU-09 | EXTRACTED | `pogoLoad2`: block entered on new ground contact, handle-gated, stored, cut at launch | @`3d2c99`, `3d9525`, `3d9fcd`, `3da0ba`, `3da10e`, `3e1aa7`, `3e1ac4` |
| AU-10 | EXTRACTED | `pogoLaunch2` on every launch, 2D, 50, pitch 0.9 + random(0.2) | @`3e1a07`, `3e1a7e` |
| AU-11 | EXTRACTED | power-jump branch condition and `effectPlayerPowerJumpInit` call | @`3e110c`, `3e11af` |
| AU-12 | EXTRACTED | `pogoLaunch3` in the default effect types, vol `(90+random(10))·La54`, range 2 / 1.5 | `effectPlayerPowerJumpInit` @`203d6f`–`20411d`, `204171`–`2042c1` |
| AU-13 | EXTRACTED | bounce selection `50 + int(random(4))`, vol `40 + random(10)`, pitch `0.9 + random(0.2)`, range 2; six call sites | @`3de957`, `3dfccb`, `3e6a37`, `3e70a3`, `3fc4ff`, `6419a2` |
| AU-14 | EXTRACTED | bounce sites sit in entity-contact code (`skill[98] & 144`, caterpillar bone) and add a fixed push; no speed-dependent volume | @`3de26a`–`3de957`, `3debe5`–`3dfccb` |
| AU-15 | EXTRACTED | `break1` at map-3 shortcut key logic (90, 0.75, 1); `break2` at map-3 intro (100, 1.0, 3) | @`3269f1`, `367c60` |
| AU-16 | EXTRACTED | ice slide start / modulate / stop with the formulas of §3.5 | @`3cc0bc`–`3ccaed` |
| AU-17 | EXTRACTED | `pogoTime` at the main-menu stage-6 one-time event, gated by `menuOptionDisableMoreSounds` | @`575321`, `577046`–`577146` |
| AU-18 | EXTRACTED | anti-repeat P1 (`slipSoundLast`, thorn horror) | @`3e3623`–`3e3663`, `3e8e89`–`3e8ec9` |
| AU-19 | EXTRACTED | concurrency cap P2 (`kuSoundGetNumInstances(45) + random(2) < 5`) | @`17cd6b`, `17cdfa` |
| AU-20 | EXTRACTED | keep-N-and-fade P3 | @`24cf28`, `24d169`, `24d1a4`, `5948ad` |
| AU-21 | EXTRACTED | thud / oof at ground contact have fixed volume | @`3e4cc4`–`3e50de`, `3e9169` |
| AU-22 | EXTRACTED | `pogoMain.dll` has no audio imports or exports | PE tables |
| AU-23 | EXTRACTED | `kuSoundDSB8*` and `kupack*` have no body in the script (DLL-side) | decoded listing |
| AU-24 | DERIVED | all numbers in §2 | `qa/audio/original_analysis.json`; loudness cross-checked with ffmpeg `ebur128` |
| AU-25 | INFERRED | `pogoLoad2` marks the start of spring loading (landing) | AU-09 + the name + the cut at launch |
| AU-26 | INFERRED | `SetSpeed` is a playback-rate multiplier | call values 0.9…1.1; `speed = 0.1 + 0.9·fade/100` @`58c288` |
| AU-27 | INFERRED | fade-out of `−50` ≈ 0.25 s | AU-08 + the assumption `time_step` = 1/16 s |
| AU-28 | ESTIMATED | the "boing / buzz / sub-bass boom / sting" descriptions of §2.3 | spectra of §2.3 |

## 7. Reproduction

```
python3 game/tools/audio-analyze.py <folder with the WAVs> qa/audio/original_analysis.json
```

The call-site ledger is reproducible from the decoded program of `Pogostuck.exe` using the method of `Pogostuck_Original_Physics_Extraction.md` §2 (decoded image **not committed**: it is the original program). The audio files themselves are not in the repository.
