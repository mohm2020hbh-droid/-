#!/usr/bin/env python3
"""World 03 — THE HORRIFYING GALAXY: five levels built on GRAVITY FLIP.

    python3 tools/levelgen/world_03.py            # build, check, write all levels
    python3 tools/levelgen/world_03.py 3 --windows # one level, with tap windows

Writes levels/world_03/level_0N.tscn + .tres, levels/world_03/world_03.tres
and levels/world_03/world_03_routes.gd.

The corridor: the ground's top at height 0 and a ceiling whose underside is
at height C. A GRAVITY GATE at x turns gravity when the player's centre gets
there; with gravity up the player falls to the ceiling and runs on it, and
every jump pushes back toward the ground. Heights below are always world
heights (tiles above the ground line); taps are x positions of the player's
centre, as in the other worlds.
"""
import os
import sys

from levelgen import Level, T, DT, HALF, G_UP, G_DOWN, MAX_FALL

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..") + "/"
WORLD = "world_03"
# The ceiling's underside in the standard corridor (tiles above the ground).
C = 5.5
# Thickness of the floating ground and ceiling slabs (the galaxy shows past them).
SLAB = 1.5


def airborne_x(lv, x_start, drop, vy0=0.0):
    """Where (x, tiles) the feet have moved `drop` tiles toward the floor,
    starting at x_start with speed vy0 (px/s, + toward the floor): the
    motor's integration, for placing ledges where a flip or a fall lands."""
    y, vy, x = 0.0, vy0, x_start * T
    for _ in range(600):
        if y >= drop * T:
            break
        vy = min(vy + (G_UP if vy < 0 else G_DOWN) * DT * 0.5, MAX_FALL)
        y += vy * DT
        x += lv.speed * DT
        vy = min(vy + (G_UP if vy < 0 else G_DOWN) * DT * 0.5, MAX_FALL)
    return x / T


def flip_land_x(lv, x_gate, corridor=C):
    """Where a player running on one surface lands on the other after a gate."""
    return airborne_x(lv, x_gate, corridor - 2 * HALF / T)


def ground(lv, x0, x1, top=0.0):
    lv.gblock(x0, x1, top, top - SLAB)


def roof(lv, x0, x1, underside=C):
    lv.roof(x0, x1, underside, SLAB)


def new_level(key, number, name, tagline, speed):
    lv = Level(key, f"w03_l{number:02d}", name, tagline, speed)
    lv.kill_y = 7.0 * T
    lv.kill_top = -(C + 7.0) * T
    # Centre the corridor on screen (below the HUD) whichever surface is the floor.
    lv.camera_offset = -140.0
    return lv


def level_01():
    """FIRST FLIP — hard: the ground, the ceiling and back. A gate and a pit
    to learn the flip, a short run on the ceiling (a trap, a gap), the way
    back down, a dual hazard, a controlled double jump, and a harder flip
    taken in the air over gravity mines."""
    lv = new_level("level_01", 1, "First Flip", "Hard", 1.14)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    ground(lv, -12, 36)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # ------------------------------------------------ a mine to jump ---
    lv.group("FirstMine")
    a = 19.0
    lv.tap(a)
    lv.mine(a + 3.3, C, radius=22.0)
    lv.shard(a + 3.3, 2.3)

    # -------------------------------------------------- the first flip ---
    lv.group("FirstFlip")
    gate = 30.0
    lv.lens(gate - 4.0, C * 0.5, 70.0)
    lv.gravity_gate(gate, True, C)
    land = flip_land_x(lv, gate)
    lv.echo(land - 0.5, land + 5.0, C - 0.8, C, True)
    # The ground ends past the gate: the ceiling is the only way on.
    lv.shards_along(gate + 1.0, land + 4.0, 1.6)

    # ------------------------------------------- running on the ceiling ---
    lv.group("Ceiling")
    b = land + 4.5
    lv.tap(b)
    lv.tune(lv.trap(b + 2.4, 2.0, C, 1, reach=1.1, period=1.5), b, 170)
    c = b + 7.2
    lv.tap(c)
    roof(lv, -12, c + 0.9)
    gap_end = c + 5.0
    roof(lv, gap_end, gap_end + 9.0)
    lv.shards_along(c + 1.0, gap_end, 1.5)

    # --------------------------------------------------- back down ---
    lv.group("BackDown")
    down = gap_end + 4.5
    lv.gravity_gate(down, False, C)
    back = flip_land_x(lv, down)
    ground(lv, back - 3.0, back + 16)
    lv.echo(back - 1.0, back + 4.0, 0.0, 0.8, False)

    # ------------------------------------------------ the dual hazard ---
    lv.group("Dual")
    d = back + 6.0
    lv.tap(d)
    lv.tune(lv.dual(d + 3.2, C, low=0.3, high=2.0, period=1.5), d, 170)
    roof(lv, d - 4.0, d + 8.0)

    # ---------------------------------------- a controlled double jump ---
    lv.group("DoubleJump")
    e = back + 14.8
    lv.tap(e)
    lv.dj(e + 3.6)
    far = lv.land_x(e, 0.0, air=3.6 / tps)
    ground(lv, far - 0.8, far + 20)
    lv.shards_along(e + 1.0, far, 1.8)

    # ------------------------------------ the harder flip, in the air ---
    lv.group("AirFlip")
    f = far + 8.0
    lv.tap(f)
    gate2 = f + 2.4
    lv.gravity_gate(gate2, True, C)
    roof(lv, f - 6.0, f + 30.0)
    lv.mine(f + 3.4, C, radius=20.0)
    # Resting on the ground until the flip, then on the ceiling ahead of you.
    m2 = f + 12.0
    lv.mine(m2, C, radius=22.0, phase=0.3)
    g = m2 - 3.3
    lv.tap(g)
    lv.echo(m2 - 1.0, m2 + 1.0, C - 1.0, C, True, danger=True)
    h = m2 + 6.0
    lv.tap(h)
    lv.tune(lv.trap(h + 2.4, 2.0, C, 1, reach=1.1, period=1.3, phase=0.2), h, 150)

    # ---------------------------------------------------- home ---
    lv.group("Home")
    home = h + 9.0
    lv.gravity_gate(home, False, C)
    last = flip_land_x(lv, home)
    ground(lv, far + 20, far + 21)  # (the ground under the air flip)
    ground(lv, last - 3.0, last + 30)
    lv.shards_along(f, h + 4, 3.0)

    lv.finish(last + 16)
    lv.done()
    lv.progress_checkpoints()
    return lv


LEVELS = [level_01]


def read_routes():
    out = {}
    try:
        with open(ROOT + f"levels/{WORLD}/{WORLD}_routes.gd") as fh:
            lines = fh.read().splitlines()
    except FileNotFoundError:
        return out
    for i, line in enumerate(lines):
        if line.startswith("const LEVEL_"):
            number = int(line[len("const LEVEL_"):].split(":")[0])
            taps = [float(x) for x in line.split("= [")[1].rstrip("]").split(",")]
            out[number] = (lines[i - 1].lstrip("# ").strip(), taps)
    return out


def write_routes(routes):
    lines = ["class_name World03Routes",
             "## Generated by tools/levelgen/world_03.py: the intended solution of each",
             "## World 03 level, as the player-centre x (tiles) of every tap.", ""]
    for number in sorted(routes):
        name, taps = routes[number]
        lines.append(f"## {name}")
        lines.append(f"const LEVEL_{number:02d}: PackedFloat32Array = [{', '.join(f'{x:g}' for x in taps)}]")
    names = ", ".join(f"LEVEL_{n:02d}" for n in sorted(routes))
    lines += ["", "", "## The route of level [param index] (0-based).",
              "static func get_route(index: int) -> PackedFloat32Array:",
              f"\tvar all: Array[PackedFloat32Array] = [{names}]",
              "\treturn all[index]"]
    with open(ROOT + f"levels/{WORLD}/{WORLD}_routes.gd", "w") as fh:
        fh.write("\n".join(lines) + "\n")


def write_world(count):
    lines = ['[gd_resource type="Resource" script_class="WorldData" format=3]', "",
             '[ext_resource type="Script" path="res://src/level/world_data.gd" id="1_world"]',
             '[ext_resource type="Script" path="res://src/level/level_data.gd" id="2_level"]',
             '[ext_resource type="PackedScene" path="res://src/background/background_galaxy.tscn" id="3_background"]',
             '[ext_resource type="Resource" path="res://levels/world_02/world_02.tres" id="4_requires"]']
    for i in range(1, count + 1):
        lines.append(f'[ext_resource type="Resource" path="res://levels/{WORLD}/level_{i:02d}.tres" id="level_{i:02d}"]')
    refs = ", ".join(f'ExtResource("level_{i:02d}")' for i in range(1, count + 1))
    lines += ["", "[resource]", 'script = ExtResource("1_world")', f'id = &"{WORLD}"', "number = 3",
              'display_name = "The Horrifying Galaxy"', f'levels = Array[ExtResource("2_level")]([{refs}])',
              'next_world_name = ""', 'requires = ExtResource("4_requires")', 'theme = &"galaxy"',
              'background = ExtResource("3_background")', 'progress_milestones = Array[float]([50.0, 75.0])']
    with open(ROOT + f"levels/{WORLD}/{WORLD}.tres", "w") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    args = sys.argv[1:]
    only = [int(a) for a in args if a.isdigit()] or list(range(1, len(LEVELS) + 1))
    taps_arg = [int(t) for a in args if a.startswith("--taps=") for t in a[7:].split(",")]
    routes = read_routes()
    os.makedirs(ROOT + f"levels/{WORLD}", exist_ok=True)
    for i in only:
        lv = LEVELS[i - 1]()
        lv.report(windows="--windows" in args or bool(taps_arg), only=taps_arg or None)
        for note in lv.notes:
            print("  " + note)
        print("  gravity:", ", ".join(f"{x:.1f}{'↑' if up else '↓'}" for x, up in lv.gravity_changes()))
        lv.write(ROOT, f"levels/{WORLD}/level_{i:02d}.tscn", f"levels/{WORLD}/level_{i:02d}.tres")
        routes[i] = (lv.name, sorted(lv.route))
    write_routes(routes)
    write_world(len(LEVELS))


if __name__ == "__main__":
    main()
