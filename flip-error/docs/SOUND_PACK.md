# FLIP ERROR sound pack — master manifest

Every recording in the pack, which arrived as five ZIPs that are one library.
The 42 files below are unique: nothing was duplicated between the parts and
nothing was dropped. What ships is the encoded library in
`web/src/jsMain/resources/audio/` — each master as both Opus/WebM and MP3, so a
browser plays whichever it can. The masters themselves are 112MB of 44.1kHz
stereo WAV and are deliberately NOT in git: a binary that size lives in the
history forever and is paid for on every clone. This file is the record of them
— name, size, duration, format and checksum — so a master can always be matched
back to what shipped.

Three files this pack's own README lists are absent from it: `04_button_click`,
`06_ui_cancel` and `17_trail_spark`. See AudioMap for what covers those jobs.

| master | duration | WAV | Opus | MP3 | md5 |
|---|---:|---:|---:|---:|---|
| `01_game_enter_hum.wav` | 2.20s | 379 KB | 12 KB | 18 KB | `c6e54af71aba` |
| `02_menu_idle_hum_loop.wav` | 12.00s | 2067 KB | 70 KB | 118 KB | `483415c92bc4` |
| `03_level_start_riser.wav` | 0.85s | 146 KB | 5 KB | 7 KB | `77a00ac6874e` |
| `05_ui_confirm.wav` | 0.11s | 20 KB | 1 KB | 1 KB | `da53ecae5b2c` |
| `07_level_complete.wav` | 0.53s | 91 KB | 5 KB | 5 KB | `4689ac4408fb` |
| `08_strong_loss.wav` | 0.48s | 83 KB | 3 KB | 4 KB | `aaa540f8ccd5` |
| `09_perfect_finish.wav` | 0.65s | 112 KB | 6 KB | 6 KB | `001e8e5e8425` |
| `10_world_transition.wav` | 1.00s | 172 KB | 7 KB | 8 KB | `9f2566786c9c` |
| `11_jump.wav` | 0.14s | 24 KB | 2 KB | 2 KB | `576c698057da` |
| `12_double_jump.wav` | 0.29s | 50 KB | 2 KB | 3 KB | `220bab442b9b` |
| `13_land.wav` | 0.12s | 21 KB | 1 KB | 1 KB | `1fb4206604ce` |
| `14_collect_star.wav` | 0.24s | 41 KB | 2 KB | 2 KB | `6a3b8387aa00` |
| `15_near_miss.wav` | 0.17s | 29 KB | 2 KB | 2 KB | `8736d80125e2` |
| `16_hazard_hit.wav` | 0.18s | 31 KB | 2 KB | 2 KB | `1dd22dec5e0c` |
| `18_speed_whoosh.wav` | 0.32s | 55 KB | 3 KB | 3 KB | `46aa74218dfe` |
| `19_secret_unlock.wav` | 0.33s | 57 KB | 3 KB | 3 KB | `36be32a1f979` |
| `20_w1_future_ambience_L1_60s.wav` | 60.00s | 10336 KB | 410 KB | 587 KB | `39ac8d46959a` |
| `20_w1_future_ambience_L2_60s.wav` | 60.00s | 10336 KB | 409 KB | 587 KB | `7ce67588635b` |
| `20_w1_future_ambience_L3_60s.wav` | 60.00s | 10336 KB | 409 KB | 587 KB | `0220b5bf095b` |
| `20_w1_future_ambience_L4_60s.wav` | 60.00s | 10336 KB | 409 KB | 587 KB | `89cf66027fa1` |
| `20_w1_future_ambience_L5_60s.wav` | 60.00s | 10336 KB | 409 KB | 587 KB | `9b035b307b5e` |
| `25_w2_desert_ambience_L1_60s.wav` | 60.00s | 10336 KB | 394 KB | 587 KB | `7ba1ec50398f` |
| `25_w2_desert_ambience_L2_60s.wav` | 60.00s | 10336 KB | 392 KB | 587 KB | `0768baa77b3d` |
| `25_w2_desert_ambience_L3_60s.wav` | 60.00s | 10336 KB | 393 KB | 587 KB | `a14bca43602f` |
| `25_w2_desert_ambience_L4_60s.wav` | 60.00s | 10336 KB | 395 KB | 587 KB | `76b84cbc1091` |
| `25_w2_desert_ambience_L5_60s.wav` | 60.00s | 10336 KB | 395 KB | 587 KB | `8651be921a4f` |
| `30_tension_riser_1.wav` | 6.00s | 1034 KB | 37 KB | 47 KB | `09edcef9225d` |
| `30_tension_riser_2.wav` | 8.00s | 1378 KB | 54 KB | 63 KB | `ca94d741132b` |
| `30_tension_riser_3.wav` | 10.00s | 1723 KB | 70 KB | 79 KB | `55e439c17ed7` |
| `30_tension_riser_4.wav` | 12.00s | 2067 KB | 89 KB | 94 KB | `dea42180da77` |
| `35_w1_neon_electric_arc.wav` | 0.90s | 155 KB | 6 KB | 8 KB | `5fc4f8419c3b` |
| `36_w1_distant_machine_hit.wav` | 0.90s | 155 KB | 5 KB | 8 KB | `f113609e8536` |
| `37_w2_sand_wave.wav` | 0.80s | 138 KB | 5 KB | 7 KB | `2a384b285f7e` |
| `38_w2_sand_geyser.wav` | 0.60s | 103 KB | 4 KB | 5 KB | `ef81cb3703b1` |
| `39_w2_falling_ruin.wav` | 0.90s | 155 KB | 6 KB | 8 KB | `c2c89a04ce81` |
| `40_w2_laser_charge.wav` | 0.65s | 112 KB | 5 KB | 6 KB | `6436aeec6359` |
| `41_w2_laser_blast.wav` | 0.28s | 48 KB | 2 KB | 3 KB | `4f13f3b86795` |
| `42_w2_wind_blast.wav` | 0.70s | 121 KB | 5 KB | 6 KB | `38a7d9e6f67c` |
| `43_w2_collapse_bridge.wav` | 0.90s | 155 KB | 6 KB | 8 KB | `1b2699f57c05` |
| `45_unease_low_1.wav` | 0.90s | 155 KB | 5 KB | 8 KB | `324cba11bd66` |
| `45_unease_low_2.wav` | 0.90s | 155 KB | 5 KB | 8 KB | `cc05ec6a2356` |
| `45_unease_low_3.wav` | 0.90s | 155 KB | 5 KB | 8 KB | `335f17c8b9cb` |

**42 recordings.** Masters 111.9 MB · Opus 4.3 MB · MP3 6.3 MB · shipped 10.6 MB.
