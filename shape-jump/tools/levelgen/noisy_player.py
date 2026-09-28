#!/usr/bin/env python3
"""A player with human timing error, on the tick-exact simulator.

    python3 noisy_player.py world_04 3 [--trials=300] [--sigma=30] [--gap-sigma=25] [--seed=1]

Rebuilds level 3 of the world (the same level the generator writes), then
plays its intended route many times with every tap moved by a normal error
(sigma ms, rounded to physics ticks, as a real tap lands on a tick). A
World 04 TAP TAP moves as one, its two taps a varying time apart
(gap-sigma ms). Prints
the share of runs that finish the level in one attempt, and the share that
get through each stretch between checkpoints (the respawn puts the player
on the checkpoint, so each stretch is a separate try). The player knows the
route: this measures execution, not reading.
"""
import importlib
import random
import sys

from levelgen import ATTACH_WINDOW, DT, T


def jittered(lv, route, rng, sigma_ms, tile_per_tick, lo, hi, gap_sigma_ms):
    """The route with every move in [lo, hi) moved by a random whole number
    of ticks (normal, sigma ms). World 04: a TAP TAP moves as one, and the
    time between its two taps varies too (normal, gap_sigma_ms), never under
    one tick nor past the attach window."""
    out = list(route)
    for j, x in enumerate(route):
        if not (lo <= x < hi) or lv._leads(route, j):
            continue
        shift = round(rng.gauss(0.0, sigma_ms / 1000.0) / DT) * tile_per_tick
        out[j] = x + shift
        p = lv._partner(route, j)
        if p is not None:
            gap = round((route[j] - route[p]) / tile_per_tick + rng.gauss(0.0, gap_sigma_ms / 1000.0) / DT)
            gap = min(max(gap, 1), round(ATTACH_WINDOW / DT) - 1)
            out[p] = out[j] - gap * tile_per_tick
    return sorted(out)


def main():
    args = sys.argv[1:]
    module = importlib.import_module(args[0])
    index = int(args[1])
    opt = {a.split("=")[0][2:]: a.split("=")[1] for a in args[2:] if a.startswith("--")}
    trials = int(opt.get("trials", 300))
    sigma = float(opt.get("sigma", 30))
    gap_sigma = float(opt.get("gap-sigma", 25))
    rng = random.Random(int(opt.get("seed", 1)))

    lv = module.LEVELS[index - 1]()
    route = sorted(lv.route)
    tpt = lv.tiles_per_tick()
    starts = [None] + list(lv.checkpoints)
    ends = [c[0] for c in lv.checkpoints] + [lv.finish_x]
    spawn = lv.spawn_px / T

    def run(start, stop, lo, hi):
        r = jittered(lv, route, rng, sigma, tpt, lo, hi, gap_sigma)
        return lv._lives(lv.simulate(route=r, start=start, stop_x=stop))

    whole = sum(run(None, lv.finish_x + 0.5, 0.0, 1e9) for _ in range(trials))
    parts = []
    for start, end in zip(starts, ends):
        lo = spawn if start is None else start[0]
        ok = sum(run(start, end + 0.5, lo, end + 0.5) for _ in range(trials))
        parts.append(ok / trials)
    names = ["start -> checkpoint 1", "checkpoint 1 -> checkpoint 2", "checkpoint 2 -> finish"]
    print(f"{lv.key} ({lv.name}), sigma {sigma:.0f} ms, {trials} runs each")
    print(f"  one attempt, start to finish: {100.0 * whole / trials:.0f}%")
    for name, share in zip(names, parts):
        tries = f"~{1.0 / share:.1f} tries" if share > 0 else "never"
        print(f"  {name}: {100.0 * share:.0f}% ({tries})")


if __name__ == "__main__":
    main()
