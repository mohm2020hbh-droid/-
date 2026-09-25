#!/usr/bin/env python3
"""Synthesises the game's placeholder sounds (pure Python, no dependencies).

    python3 tools/audio/gen_sfx.py

Writes assets/audio/sfx/*.wav and assets/audio/ambient/void_drone.ogg (the
ambient loop is encoded with ffmpeg when it is on PATH or IMAGEIO_FFMPEG is
set; otherwise a .wav is written next to it). Deterministic: same files every
run. Replace any file with a real sound under the same name at any time.
"""
import math
import os
import random
import shutil
import struct
import subprocess
import wave

SR = 22050
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SFX = os.path.join(ROOT, "assets", "audio", "sfx")
AMBIENT = os.path.join(ROOT, "assets", "audio", "ambient")
random.seed(7)


def write(path, samples, peak=0.5):
    m = max(1e-9, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / m * peak)) * 32767)) for s in samples)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


def sfx(name, samples, peak=0.5):
    write(os.path.join(SFX, name + ".wav"), samples, peak)


def env(t, attack, decay):
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * math.exp(-t / decay)


def tone(freq_fn, dur, attack=0.003, decay=0.05, harmonics=((1, 1.0),)):
    out, phase = [], 0.0
    for i in range(int(dur * SR)):
        t = i / SR
        phase += 2 * math.pi * freq_fn(t) / SR
        s = sum(a * math.sin(phase * h) for h, a in harmonics)
        out.append(s * env(t, attack, decay))
    return out


def noise(dur, decay, attack=0.0005):
    return [random.uniform(-1, 1) * env(i / SR, attack, decay) for i in range(int(dur * SR))]


def lowpass(track, amount):
    out, y = [], 0.0
    for s in track:
        y += (s - y) * amount
        out.append(y)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def gain(track, g):
    return [s * g for s in track]


def delay(track, seconds):
    return [0.0] * int(seconds * SR) + track


def drone(seconds):
    """A seamless loop: every partial completes whole cycles in [seconds]."""
    n = int(seconds * SR)
    rng = random.Random(11)
    # Low body: A1, E2, A2 with slow swells, each an integer number of cycles.
    partials = [(55.0, 1.0, 1), (82.5, 0.55, 2), (110.0, 0.35, 3), (165.0, 0.12, 1)]
    # Distant "wind": a band of quiet partials with random phases.
    wind = [(round(rng.uniform(260, 900) * seconds) / seconds, rng.uniform(0.004, 0.012), rng.uniform(0, math.tau))
            for _ in range(48)]
    out = []
    for i in range(n):
        t = i / SR
        s = 0.0
        for freq, amp, lfo in partials:
            swell = 0.75 + 0.25 * math.sin(math.tau * lfo * t / seconds)
            s += amp * swell * math.sin(math.tau * freq * t)
        gust = 0.6 + 0.4 * math.sin(math.tau * 2 * t / seconds + 1.3)
        for freq, amp, ph in wind:
            s += amp * gust * math.sin(math.tau * freq * t + ph)
        out.append(s)
    return out


def encode_loop(samples, name):
    os.makedirs(AMBIENT, exist_ok=True)
    wav = os.path.join(AMBIENT, name + ".wav")
    write(wav, samples, peak=0.6)
    ffmpeg = os.environ.get("IMAGEIO_FFMPEG") or shutil.which("ffmpeg")
    if not ffmpeg:
        print("ffmpeg not found: kept", wav)
        return
    ogg = os.path.join(AMBIENT, name + ".ogg")
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", ogg], check=True)
    os.remove(wav)


def main():
    os.makedirs(SFX, exist_ok=True)
    # Jump: short upward chirp with a soft square edge.
    sfx("jump", tone(lambda t: 300 + 2600 * t, 0.11, decay=0.045,
                     harmonics=((1, 1.0), (3, 0.25), (5, 0.08))), peak=0.42)

    # Land: low thump plus a tiny noise click.
    thump = tone(lambda t: 140 - 500 * t, 0.09, attack=0.001, decay=0.03)
    click = [random.uniform(-1, 1) * math.exp(-i / (SR * 0.006)) * 0.5 for i in range(int(0.02 * SR))]
    sfx("land", mix(thump, click), peak=0.5)

    # Collect: bright bell fifth.
    bell = mix(tone(lambda t: 1318.5, 0.3, attack=0.002, decay=0.09),
               delay(tone(lambda t: 1975.5, 0.26, attack=0.002, decay=0.08), 0.045))
    sfx("collect", bell, peak=0.38)

    # Death: falling tone through a noise burst, crushed.
    fall = tone(lambda t: 520 * math.exp(-6 * t) + 50, 0.45, attack=0.001, decay=0.14,
                harmonics=((1, 1.0), (2, 0.5), (3, 0.3)))
    burst = [random.uniform(-1, 1) * math.exp(-i / (SR * 0.08)) for i in range(int(0.3 * SR))]
    crushed = [round(s * 6) / 6 for s in mix(fall, burst)]
    sfx("death", crushed, peak=0.55)

    # Checkpoint: rising three-note arpeggio.
    notes = [659.3, 830.6, 987.8]
    sfx("checkpoint", mix(*[delay(tone(lambda t, f=f: f, 0.3, decay=0.1, harmonics=((1, 1.0), (2, 0.2))), i * 0.07)
                            for i, f in enumerate(notes)]), peak=0.4)

    # Complete: four-note climb that rings out.
    notes = [523.3, 659.3, 784.0, 1046.5]
    sfx("complete", mix(*[delay(tone(lambda t, f=f: f, 0.9, decay=0.3, harmonics=((1, 1.0), (2, 0.25), (3, 0.1))), i * 0.11)
                          for i, f in enumerate(notes)]), peak=0.45)

    # UI click: a crisp tick.
    sfx("ui_click", tone(lambda t: 1800, 0.04, attack=0.0005, decay=0.008), peak=0.35)

    # Added for World 01 (kept after the originals so those stay byte-identical).
    # Double jump: a higher, brighter chirp over an airy whoosh, clearly not the jump.
    chirp = tone(lambda t: 620 * math.exp(9.0 * t), 0.15, decay=0.06, harmonics=((1, 1.0), (2, 0.35), (4, 0.12)))
    whoosh = gain(lowpass(noise(0.16, 0.05, attack=0.02), 0.25), 0.6)
    sparkle = delay(tone(lambda t: 2637.0, 0.12, decay=0.035), 0.05)
    sfx("double_jump", mix(chirp, whoosh, gain(sparkle, 0.35)), peak=0.42)

    # Obstacle warning: two low, dry pulses (a machine arming itself).
    pulse = tone(lambda t: 196 - 60 * t, 0.07, attack=0.002, decay=0.022, harmonics=((1, 1.0), (2, 0.6), (3, 0.35)))
    sfx("warning", mix(pulse, delay(pulse, 0.09)), peak=0.36)

    # Slam: a heavy block hitting home.
    boom = tone(lambda t: 90 * math.exp(-5 * t) + 35, 0.3, attack=0.001, decay=0.08, harmonics=((1, 1.0), (2, 0.4)))
    grit = gain(lowpass(noise(0.12, 0.03), 0.35), 0.8)
    sfx("slam", mix(boom, grit), peak=0.5)

    # Ambient: the low drone of the Red Void, 8 s seamless loop.
    encode_loop(drone(8.0), "void_drone")
    print("ok")


if __name__ == "__main__":
    main()
