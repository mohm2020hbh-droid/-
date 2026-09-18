FLIP ERROR — ALL SOUNDS v2

DESIGN RULE:
NO BACKGROUND MUSIC.
No songs, no melodies, no DnB/Synthwave tracks.

Instead the game uses long, reactive ENVIRONMENTAL SOUNDSCAPES.

WORLD 1:
Future / neon city atmosphere.
Use one of:
20_w1_future_ambience_L1_60s.wav (low tension)
20_w1_future_ambience_L2_60s.wav
20_w1_future_ambience_L3_60s.wav
20_w1_future_ambience_L4_60s.wav
20_w1_future_ambience_L5_60s.wav (highest tension)

WORLD 2:
Neon desert atmosphere.
Use one of:
25_w2_desert_ambience_L1_60s.wav (low tension)
25_w2_desert_ambience_L2_60s.wav
25_w2_desert_ambience_L3_60s.wav
25_w2_desert_ambience_L4_60s.wav
25_w2_desert_ambience_L5_60s.wav (highest tension)

Recommended implementation:
- Start each level with L1/L2 ambience.
- Crossfade or layer upward as progress increases: 25% -> L2, 50% -> L3, 70% -> L4, 90% -> L5.
- Use 30_tension_riser_1..4 sparingly at dangerous transitions.
- Never replace gameplay SFX with the ambience.

GLOBAL:
01 game entry hum
02 menu idle ambience
03 level start
04 button click
05 confirm
06 cancel
07 complete
08 strong loss
09 perfect finish
10 world transition

PLAYER:
11 jump
12 double jump
13 land
14 collect star
15 near miss
16 hazard hit
17 trail spark
18 speed whoosh
19 secret unlock

WORLD 1 ONE-SHOTS:
35 neon electric arc
36 distant machine hit

WORLD 2 ONE-SHOTS:
37 sand wave
38 sand geyser
39 falling ruin
40 laser charge
41 laser blast
42 wind blast
43 collapsing bridge

UNEASE:
45_unease_low_1..3

IMPORTANT:
The "sound per world" is a long environment system, not a short 10-second tune.
The player should hear a world that feels alive, tense, adventurous and sometimes unsettling.
As progress rises, tension should increase through layers/crossfades, not through a song.
