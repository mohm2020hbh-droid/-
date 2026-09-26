#!/usr/bin/env python3
"""World 03's sounds (The Horrifying Galaxy), on the helpers of gen_sfx.py.

    python3 tools/audio/gen_sfx_galaxy.py

Writes only assets/audio/sfx/gravity_flip.wav and gravity_warning.wav, so the
older sounds are never touched. Deterministic.
"""
import math
import random

import gen_sfx as g


def main():
    random.seed(33)
    # Gravity flip, the world's signature: a deep swell that turns over
    # (the pitch climbs, then falls away), a rushing whoosh through the
    # middle, and a bright ring as the new floor takes hold.
    swell = g.tone(lambda t: 55 + 190 * math.sin(math.pi * min(t / 0.5, 1.0)), 0.62, attack=0.04, decay=0.28,
                   harmonics=((1, 1.0), (2, 0.45), (3, 0.2)))
    rush = g.gain(g.lowpass(g.noise(0.5, 0.2, attack=0.12), 0.18), 0.9)
    ring = g.delay(g.tone(lambda t: 880 + 40 * math.sin(40 * t), 0.4, attack=0.002, decay=0.12,
                          harmonics=((1, 1.0), (1.5, 0.35), (3, 0.12))), 0.24)
    g.sfx("gravity_flip", g.mix(swell, rush, g.gain(ring, 0.45)), peak=0.5)

    # Gravity warning: a gate waking up ahead, two soft rising glass notes.
    note = lambda f: g.tone(lambda t: f * (1 + 0.04 * t), 0.22, attack=0.01, decay=0.07,
                            harmonics=((1, 1.0), (2, 0.3), (3, 0.1)))
    g.sfx("gravity_warning", g.mix(note(523.3), g.delay(note(784.0), 0.1)), peak=0.3)
    print("ok")


if __name__ == "__main__":
    main()
