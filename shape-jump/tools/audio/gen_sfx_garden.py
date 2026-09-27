#!/usr/bin/env python3
"""World 04's sounds (The Inverted Garden), on the helpers of gen_sfx.py.

    python3 tools/audio/gen_sfx_garden.py

Writes only assets/audio/sfx/latch.wav and latch_miss.wav, so the older
sounds are never touched. Deterministic.
"""
import math
import random

import gen_sfx as g


def main():
    random.seed(44)
    # Surface latch, the world's signature: a quick airy rise (the seed
    # turning over), then a soft wooden pluck and a bloom of two bell notes
    # as it takes hold of the other surface.
    rise = g.tone(lambda t: 330 + 520 * min(t / 0.12, 1.0), 0.16, attack=0.01, decay=0.06,
                  harmonics=((1, 1.0), (2, 0.25)))
    air = g.gain(g.lowpass(g.noise(0.14, 0.05, attack=0.03), 0.3), 0.5)
    pluck = g.delay(g.tone(lambda t: 196, 0.12, attack=0.001, decay=0.03, harmonics=((1, 1.0), (2.7, 0.4))), 0.1)
    bell = lambda f, at: g.delay(g.tone(lambda t: f, 0.34, attack=0.003, decay=0.11,
                                        harmonics=((1, 1.0), (2, 0.2), (3.01, 0.08))), at)
    g.sfx("latch", g.mix(g.gain(rise, 0.5), air, pluck, g.gain(bell(1046.5, 0.11), 0.4),
                         g.gain(bell(1568.0, 0.16), 0.3)), peak=0.45)

    # Latch missed (nothing in reach): a short hollow puff falling away,
    # quiet so it informs without scolding.
    puff = g.gain(g.lowpass(g.noise(0.12, 0.04, attack=0.005), 0.12), 0.8)
    sag = g.tone(lambda t: 260 - 140 * min(t / 0.14, 1.0), 0.16, attack=0.005, decay=0.05,
                 harmonics=((1, 1.0), (2, 0.15)))
    g.sfx("latch_miss", g.mix(puff, g.gain(sag, 0.5)), peak=0.25)
    print("ok")


if __name__ == "__main__":
    main()
