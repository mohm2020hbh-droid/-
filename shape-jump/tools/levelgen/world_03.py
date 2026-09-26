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
import math
import os
import sys

from levelgen import Level, T, DT, HALF, G_UP, G_DOWN, MAX_FALL, V_JUMP, V_DOUBLE

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


def dj_range(lv, tau, corridor=C, rise=0.0):
    """Tiles from a jump's take-off to where it comes down through `rise`
    tiles above its floor, with the double jump `tau` s after take-off, under
    a ceiling `corridor` tiles up (the head stops against it)."""
    room = corridor * T - 2 * HALF
    y, vy, t, doubled = 0.0, -V_JUMP, 0.0, False
    while True:
        if not doubled and t >= tau:
            vy, doubled = -V_DOUBLE, True
        vy = min(vy + (G_UP if vy < 0 else G_DOWN) * DT * 0.5, MAX_FALL)
        y += vy * DT
        if -y > room:
            y, vy = -room, 0.0
        vy = min(vy + (G_UP if vy < 0 else G_DOWN) * DT * 0.5, MAX_FALL)
        t += DT
        if vy > 0.0 and y >= -rise * T and t > 0.05:
            return t * lv.speed / T


def dj_gap(lv, spans, x, tau, up, lead=0.8, rise=1.6, ledge=3.0, base=0.0):
    """A gap in `spans` (the current floor) that a jump at x only clears with
    its double jump no earlier than `tau` s after take-off (a late double
    jump carries further), onto a ledge `rise` tiles above the take-off (so
    a double jump from deep in the gap cannot reach it). `base`: the
    take-off's height above the floor (from a ledge). Returns where the
    landing ledge ends (where the landing floor goes on, if flush)."""
    lv.tap(x)
    lv.dj(x + tau * lv.tiles_per_second())
    land = x + dj_range(lv, tau, corridor=C - base, rise=rise)
    spans.cut(x + lead, land - 0.1)
    height = base + rise
    if height > 0.0:
        end = land - 0.1 + ledge
        if up:
            lv.gblock(land - 0.1, end, C + SLAB, C - height, top_edge=False, bottom_edge=True)
        else:
            lv.gblock(land - 0.1, end, height, -SLAB)
        spans.cut(land - 0.1, end)
        return end
    return land


def flip_land_x(lv, x_gate, corridor=C):
    """Where a player running on one surface lands on the other after a gate."""
    return airborne_x(lv, x_gate, corridor - 2 * HALF / T)


def ground(lv, x0, x1, top=0.0):
    lv.gblock(x0, x1, top, top - SLAB)


def roof(lv, x0, x1, underside=C):
    lv.roof(x0, x1, underside, SLAB)


def corridor(lv, x0, x1):
    """Ground and ceiling from x0 to x1."""
    ground(lv, x0, x1)
    roof(lv, x0, x1)


def off(up, h):
    """World height of a point `h` tiles away from the current floor."""
    return C - h if up else h


def floor_trap(lv, x0, width, up, **kw):
    """A CEILING TRAP set in the surface you run on (bites toward the other)."""
    return lv.trap(x0, width, C if up else 0.0, 1 if up else -1, **kw)


def far_trap(lv, x0, width, up, **kw):
    """A trap in the surface over your head (cuts the height of a jump)."""
    return lv.trap(x0, width, 0.0 if up else C, -1 if up else 1, **kw)


def wall(lv, x0, x1, h, hanging):
    """An INVERTED WALL: a block h tiles tall standing on the ground, or
    hanging from the ceiling. Its end face lets you by on the other surface
    only; its face toward the corridor is a floor, lit."""
    if hanging:
        lv.gblock(x0, x1, C + SLAB, C - h, top_edge=False, bottom_edge=True)
    else:
        lv.gblock(x0, x1, h, -SLAB)


def new_level(key, number, name, tagline, speed):
    lv = Level(key, f"w03_l{number:02d}", name, tagline, speed)
    lv.kill_y = 7.0 * T
    lv.kill_top = -(C + 7.0) * T
    # Centre the corridor on screen (below the HUD) whichever surface is the floor.
    lv.camera_offset = -140.0
    lv.tune_any_kind = True
    return lv


class Spans:
    """A surface along the level with holes cut in it; emitted as blocks."""

    def __init__(self, x0, x1):
        self.x0, self.x1, self.holes = x0, x1, []

    def cut(self, x0, x1):
        self.holes.append((x0, x1))

    def emit(self, lv, make):
        x = self.x0
        for h0, h1 in sorted(self.holes):
            if h0 > x:
                make(lv, x, h0)
            x = max(x, h1)
        if self.x1 > x:
            make(lv, x, self.x1)


# ------------------------------------------------------------------ beats --
# Each beat is one tap (or a jump and its double jump) at x, the player's
# centre, on the current floor (`up`: gravity pulls to the ceiling). Hazards
# that move are tuned against their tap to a target window (ms).

def mine_pair(lv, x, spread=2.6, phase=0.0):
    """Two gravity mines one jump from x must clear (on whichever floor)."""
    lv.tap(x)
    lv.mine(x + 2.2, C, phase=phase)
    lv.mine(x + 2.2 + spread, C, phase=phase + 0.5)
    return x


def slot(lv, x, ms, period=1.2, phase=0.0, high=2.2):
    """A DUAL HAZARD whose spires rise together: the jump has to pass between
    the ground spire's tip and the ceiling spire's."""
    lv.tap(x)
    lv.tune(lv.dual(x + 3.2, C, low=0.3, high=high, period=period, phase=phase), x, ms)
    return x


def trap(lv, x, up, ms, period=1.2, phase=0.0, width=3.0, reach=1.3):
    """A CEILING TRAP in the floor you run on, timed against the jump."""
    lv.tap(x)
    lv.tune(floor_trap(lv, x + 2.2, width, up, reach=reach, period=period, phase=phase), x, ms)
    return x


def rock(lv, x, ms, period=1.2, phase=0.0, at=3.3):
    """A mine to jump, and a FALLING ASTEROID through the same place."""
    lv.tap(x)
    lv.mine(x + at, C, phase=phase)
    lv.tune(lv.asteroid(x + at, C, period=period), x, ms)
    return x


def orbit_hop(lv, x, up, ms, radius=1.5, spin=2.2, bodies=2, mine=True, lift=2.6):
    """A jump at x through an ORBITAL HAZARD whose ring crosses the arc of the
    jump (over a mine that asks for the jump): the jump goes as the bodies
    swing clear."""
    lv.tap(x)
    if mine:
        lv.mine(x + 3.3, C)
    lv.tune(lv.orbital(x + 3.3, off(up, lift), radius, bodies=bodies, spin=spin), x, ms)
    return x


def orbit_dj(lv, x, up, ms, spin=2.4, radius=1.3, dj=3.2):
    """Three mines too long for one jump: jump, and double jump through an
    ORBITAL HAZARD turning around the top of the second rise. `spin` is
    seen from the floor you run on (gravity up mirrors it in the world)."""
    lv.tap(x)
    lv.dj(x + dj)
    lv.mine(x + 2.2, C)
    lv.mine(x + 5.2, C, phase=0.5)
    lv.mine(x + 8.2, C)
    world_spin = -spin if up else spin
    lv.tune(lv.orbital(x + 5.2, off(up, 3.6), radius, bodies=2, spin=world_spin), [x, x + dj], ms)
    return x


def rock_dj(lv, x, ms, period=1.1, dj=3.2):
    """Three mines too long for one jump: jump, and double jump past a
    FALLING ASTEROID crossing the top of the second rise."""
    lv.tap(x)
    lv.dj(x + dj)
    lv.mine(x + 2.2, C)
    lv.mine(x + 5.2, C, phase=0.5)
    lv.mine(x + 8.2, C)
    lv.tune(lv.asteroid(x + 5.2, C, period=period), [x, x + dj], ms)
    return x


def air_gate(lv, x, up_after, ms, kind="trap", period=1.0, phase=0.0):
    """A FLIP IN THE AIR: jump a hazard at x; the gate stands at the top of
    the jump, so gravity turns mid-flight and carries you on to the other
    surface (about 5.6 tiles on). Returns the gate's x."""
    was_up = not up_after
    lv.tap(x)
    if kind == "trap":
        el = floor_trap(lv, x + 2.0, 2.6, was_up, reach=1.3, period=period, phase=phase)
    else:
        el = lv.dual(x + 3.2, C, low=0.3, high=2.2, period=period, alternate=True, phase=phase)
    lv.gravity_gate(x + 3.3, up_after, C)
    lv.tune(el, x, ms)
    return x + 3.3


def lane_slab(lv, x0, x1, low, high):
    """An INVERTED WALL across the corridor from height low to high: a floor
    on both faces, splitting the corridor into two lanes."""
    lv.slab(x0, x1, high, low)


def low_hops(lv, x, up, count, ms, spacing=4.8, period=0.9):
    """Under a lane wall the jump is cut short: quick low hops over mines,
    every other one a trap timed against it."""
    for i in range(count):
        lv.tap(x)
        if i % 2 == 0:
            lv.mine(x + 2.0, C, phase=0.3 * i)
        else:
            lv.tune(floor_trap(lv, x + 1.6, 2.0, up, reach=1.2, period=period, phase=0.2 * i), x, ms)
        x += spacing
    return x - spacing


def ground_gate(lv, x, up, lens=False):
    """A GRAVITY GATE met running: gravity turns, you fall to the other
    surface (an echo marks where). Returns where you land."""
    if lens:
        lv.lens(x - 4.0, C * 0.5, 70.0)
    lv.gravity_gate(x, up, C)
    land = flip_land_x(lv, x)
    lv.echo(land - 0.5, land + 3.0, C - 0.8 if up else 0.0, C if up else 0.8, up)
    return land


def finish_level(lv, floor, top, end, up=False):
    floor.x1 = top.x1 = end + 16.0
    floor.emit(lv, ground)
    top.emit(lv, roof)
    lv.finish(end, C if up else 0.0, up=up)
    lv.done()
    lv.progress_checkpoints()
    return lv


def start_level(lv):
    for x in (7, 9, 11):
        lv.shard(x, 0.5)
    return Spans(-12.0, 0.0), Spans(-12.0, 0.0)


def level_01():
    """FIRST FLIP — hard: mines on the ground, the first gate, a run on the
    ceiling (mines, a trap, a gap, the first dual hazard), back down for a
    controlled double jump, then flips taken in the air."""
    lv = new_level("level_01", 1, "First Flip", "Hard", 1.15)
    floor, top = start_level(lv)

    lv.group("Mines")
    x = 18.0
    lv.tap(x)
    lv.mine(x + 3.3, C)
    x = mine_pair(lv, x + 8.0, 3.05, 0.25)
    lv.shards_along(15.0, x + 6.0, 3.0)

    lv.group("FirstFlip")
    land = ground_gate(lv, x + 9.0, True, lens=True)

    lv.group("Ceiling")
    x = mine_pair(lv, land + 3.2, 3.15, 0.1)
    x = trap(lv, x + 7.6, True, 165, period=1.4)
    x += 7.6
    lv.tap(x)
    top.cut(x + 1.0, x + 6.6)
    x = mine_pair(lv, x + 9.4, 3.2, 0.6)
    x = slot(lv, x + 7.6, 165, period=1.4)
    x = trap(lv, x + 7.6, True, 155, period=1.3, phase=0.4)
    lv.shards_along(land + 2.0, x + 6.0, 3.0)

    lv.group("BackDown")
    land = ground_gate(lv, x + 8.0, False)
    x = slot(lv, land + 3.2, 155, period=1.3)
    x = trap(lv, x + 7.6, False, 155, period=1.3)

    lv.group("DoubleJump")
    x += 7.6
    end = dj_gap(lv, floor, x, 0.44, False)
    lv.shards_along(x + 1.0, end, 3.0)
    x = mine_pair(lv, end + 3.0, 3.2, 0.8)

    lv.group("AirFlip")
    g = air_gate(lv, x + 7.6, True, 155, period=1.3)
    lv.echo(g + 1.5, g + 5.0, C - 0.8, C, True)
    x = mine_pair(lv, g + 5.3, 3.2, 0.4)
    x = slot(lv, x + 7.6, 145, period=1.2)
    x = trap(lv, x + 7.6, True, 145, period=1.2)
    x += 7.6
    end = dj_gap(lv, top, x, 0.46, True)
    lv.shards_along(g + 2.0, end, 3.0)

    lv.group("Home")
    g = air_gate(lv, end + 3.8, False, 145, kind="dual", period=1.2)
    x = mine_pair(lv, g + 5.3, 3.2, 0.2)
    x = slot(lv, x + 7.6, 140, period=1.2)
    x = trap(lv, x + 7.6, False, 140, period=1.1)
    x = mine_pair(lv, x + 7.6, 3.2, 0.9)
    x = slot(lv, x + 7.6, 140, period=1.1, phase=0.6)
    lv.shards_along(g + 2.0, x + 6.0, 3.0)
    return finish_level(lv, floor, top, x + 11.0)


def level_02():
    """UPSIDE DOWN — very hard: most of it on the ceiling. Asteroids that
    fall toward whichever floor you run on, traps, dual spires, double
    jumps chained from ledge to ledge, gates in quick succession, a drop into
    the void that a gate catches, and a finish hanging from the ceiling."""
    lv = new_level("level_02", 2, "Upside Down", "Very hard", 1.18)
    floor, top = start_level(lv)

    lv.group("Start")
    x = mine_pair(lv, 15.0, 2.9, 0.1)
    lv.lens(x + 4.0, C * 0.5, 70.0)
    g = air_gate(lv, x + 7.6, True, 150, period=1.3)
    lv.echo(g + 1.5, g + 5.0, C - 0.8, C, True)
    lv.shards_along(15.0, g, 2.0)

    lv.group("Asteroids")
    x = rock(lv, g + 5.3, 150, period=1.4)
    x = trap(lv, x + 7.4, True, 140, period=1.2)
    x += 7.4
    lv.tap(x)
    lv.mine(x + 2.4, C, phase=0.6)
    lv.mine(x + 5.3, C, phase=0.1)
    lv.tune(lv.asteroid(x + 3.85, C, period=1.3), x, 140)
    x = slot(lv, x + 7.4, 140, period=1.2)
    lv.shards_along(g + 5.0, x + 6.0, 2.2)

    lv.group("DoubleJumps")
    x = rock_dj(lv, x + 7.4, 130, period=1.2)
    x += 11.5
    end = dj_gap(lv, top, x, 0.46, True, rise=1.2, ledge=4.2)
    x2 = end - 1.4
    end = dj_gap(lv, top, x2, 0.46, True, rise=0.5, ledge=3.0, base=1.2)
    lv.shards_along(x + 1.0, end, 1.6)

    lv.group("QuickGates")
    g = air_gate(lv, end + 3.8, False, 140, kind="dual", period=1.2)
    x = mine_pair(lv, g + 5.3, 3.0, 0.4)
    g = air_gate(lv, x + 7.4, True, 140, period=1.1)
    lv.echo(g + 1.5, g + 5.0, C - 0.8, C, True, danger=True)
    x = trap(lv, g + 5.3, True, 130, period=1.1)

    lv.group("Rain")
    x = rock(lv, x + 7.4, 130, period=1.2, phase=0.2)
    x = rock(lv, x + 7.4, 130, period=1.2, phase=0.7)
    x = slot(lv, x + 7.4, 130, period=1.1)
    x = mine_pair(lv, x + 7.4, 3.1, 0.8)
    x = rock_dj(lv, x + 7.4, 120, period=1.1)
    x = rock(lv, x + 11.5, 130, period=1.1)
    lv.shards_along(g + 5.0, x + 6.0, 2.4)

    lv.group("Turn")
    g = air_gate(lv, x + 7.4, False, 130, kind="dual", period=1.1)
    x = slot(lv, g + 5.3, 130, period=1.1, phase=0.3)
    x += 7.4
    end = dj_gap(lv, floor, x, 0.5, False)
    lv.shards_along(g + 2.0, end, 2.0)

    # Into the void: the ground ends and a gate catches you in the air.
    lv.group("IntoTheVoid")
    x = end + 3.0
    lv.tap(x)
    g = x + 5.0
    floor.cut(x + 0.9, g + 2.5)
    lv.gravity_gate(g, True, C)
    lv.echo(g + 1.5, g + 5.0, C - 0.8, C, True)

    lv.group("Final")
    x = trap(lv, g + 6.5, True, 120, period=1.1)
    x += 7.4
    lv.tap(x)
    lv.mine(x + 2.4, C, phase=0.1)
    lv.mine(x + 5.3, C, phase=0.6)
    lv.tune(lv.asteroid(x + 3.85, C, period=1.1), x, 120)
    x = slot(lv, x + 7.4, 120, period=1.0)
    x += 7.4
    end = dj_gap(lv, top, x, 0.5, True)
    lv.shards_along(g + 5.0, end, 2.2)
    return finish_level(lv, floor, top, end + 12.0, up=True)


def level_03():
    """ORBIT — extremely hard: orbital hazards across the arc of every jump
    and double jump, flip fields that turn gravity for a stretch, the
    corridor split in two lanes, platforms floating over the gaps of the
    ceiling."""
    lv = new_level("level_03", 3, "Orbit", "Extremely hard", 1.21)
    floor, top = start_level(lv)

    lv.group("Orbits")
    x = mine_pair(lv, 15.0, 3.0, 0.2)
    # The first orbit turns slowly: learn it before it tightens.
    x = orbit_hop(lv, x + 7.4, False, 190, spin=1.7)
    x = orbit_hop(lv, x + 8.6, False, 150, bodies=3, spin=-1.9)
    lv.shards_along(15.0, x + 6.0, 2.0)

    lv.group("Field")
    f0 = x + 8.0
    f1 = f0 + 32.0
    lv.flip_field(f0, f1, True, C)
    lv.lens(f0 - 4.0, C * 0.5, 70.0)
    land = flip_land_x(lv, f0)
    lv.echo(land - 0.5, land + 3.0, C - 0.8, C, True)
    x = mine_pair(lv, land + 2.8, 3.0, 0.4)
    x = orbit_hop(lv, x + 7.4, True, 125, spin=2.5)
    x = trap(lv, x + 7.4, True, 125, period=1.1)
    back = flip_land_x(lv, f1)
    lv.echo(back - 0.5, back + 3.0, 0.0, 0.8, False)
    lv.shards_along(land + 2.0, f1, 2.2)

    lv.group("OrbitJump")
    x = orbit_dj(lv, back + 2.8, False, 125)

    lv.group("Lanes")
    s0 = x + 11.0
    lane_slab(lv, s0, s0 + 16.0, 2.35, 3.0)
    x = low_hops(lv, s0 + 1.5, False, 3, 125, spacing=5.0, period=1.0)
    lv.shards_along(s0 + 1.0, s0 + 15.0, 1.6, lift=0.2)

    lv.group("Up")
    g = air_gate(lv, s0 + 18.0, True, 125, period=1.1)
    lv.echo(g + 1.5, g + 5.0, C - 0.8, C, True)
    x = orbit_hop(lv, g + 5.3, True, 125, spin=-2.3)

    lv.group("Floaters")
    x += 7.6
    lv.tap(x)
    top.cut(x + 0.8, x + 11.0)
    fl = lv.floater(x + 4.0, 3.0, C + 0.5, (0.0, -1.4), 1.6)
    lv.tune(fl, [x], 150, osc=True)
    lv.tap(x + 6.2)
    lv.shards_along(x + 1.0, x + 11.0, 1.5)
    x = orbit_dj(lv, x + 15.0, True, 115, spin=2.5)
    x = trap(lv, x + 11.0, True, 115, period=1.0)

    lv.group("DownField")
    p0 = x + 8.0
    p1 = p0 + 21.0
    lv.flip_field(p0, p1, False, C)
    down = flip_land_x(lv, p0)
    lv.echo(down - 0.5, down + 3.0, 0.0, 0.8, False)
    x = orbit_hop(lv, down + 2.8, False, 115, spin=2.6)
    x = slot(lv, x + 7.4, 115, period=1.0)
    up_land = flip_land_x(lv, p1)
    lv.echo(up_land - 0.5, up_land + 3.0, C - 0.8, C, True)
    lv.shards_along(down + 2.0, p1, 2.2)

    lv.group("LanesUp")
    s1 = up_land + 2.0
    lane_slab(lv, s1, s1 + 16.0, 2.5, 3.15)
    x = low_hops(lv, s1 + 1.5, True, 3, 115, spacing=5.0, period=0.95)

    lv.group("Home")
    g = air_gate(lv, s1 + 18.0, False, 115, kind="dual", period=1.0)
    x = orbit_hop(lv, g + 5.3, False, 115, bodies=3, spin=2.5)
    x = orbit_dj(lv, x + 7.4, False, 105, spin=2.6)
    x += 11.0
    end = dj_gap(lv, floor, x, 0.5, False)
    lv.shards_along(g + 2.0, end, 2.0)
    return finish_level(lv, floor, top, end + 12.0)


def level_04():
    """DISTORTION — brutal: gates chained so close the world keeps turning,
    walls that close one surface and leave the other, a gravity pit that
    carries you over the void, lanes on the ceiling, double jumps chained
    from ledge to ledge, traps and spires on both surfaces."""
    lv = new_level("level_04", 4, "Distortion", "Brutal", 1.24)
    floor, top = start_level(lv)

    lv.group("Start")
    x = mine_pair(lv, 15.0, 3.1, 0.2)
    x = orbit_dj(lv, x + 7.7, False, 115, spin=2.5)
    lv.shards_along(15.0, x + 10.0, 2.0)

    lv.group("Chain")
    lv.lens(x + 8.0, C * 0.5, 70.0)
    g = air_gate(lv, x + 11.0, True, 115, period=1.0)
    x = trap(lv, g + 5.5, True, 105, period=1.0)
    g = air_gate(lv, x + 7.7, False, 105, kind="dual", period=1.0)
    x = mine_pair(lv, g + 5.5, 3.1, 0.6)
    g = air_gate(lv, x + 7.7, True, 105, period=0.95)
    lv.shards_along(g - 20.0, g + 3.0, 2.4)

    lv.group("Walls")
    w0 = g + 3.5
    wall(lv, w0, w0 + 12.0, 3.4, False)
    x = w0 + 2.0
    lv.tap(x)
    lv.tune(floor_trap(lv, x + 1.8, 2.2, True, reach=1.1, period=0.95), x, 95)
    x += 5.0
    lv.tap(x)
    lv.tune(floor_trap(lv, x + 1.8, 2.2, True, reach=1.1, period=0.9, phase=0.5), x, 95)
    down = ground_gate(lv, w0 + 13.0, False)
    wall(lv, down + 1.5, down + 13.5, 3.4, True)
    x = down + 3.0
    lv.tap(x)
    lv.tune(floor_trap(lv, x + 1.8, 2.2, False, reach=1.1, period=0.9, phase=0.2), x, 95)
    x += 5.0
    lv.tap(x)
    lv.tune(floor_trap(lv, x + 1.8, 2.2, False, reach=1.1, period=0.95), x, 95)
    lv.shards_along(w0 + 1.0, down + 13.0, 2.2)

    lv.group("Pit")
    p0 = down + 17.0
    p1 = p0 + 10.0
    lv.flip_field(p0, p1, True, C, pit=True)
    floor.cut(p0 + 0.3, p1 + 3.5)
    x = p0 + 5.4
    lv.tap(x)
    lv.mine(x + 2.4, C, phase=0.3)
    after = flip_land_x(lv, p1)
    x = slot(lv, after + 2.6, 105, period=0.95)

    lv.group("Lanes")
    g = air_gate(lv, x + 7.7, True, 105, period=0.95)
    s0 = g + 3.3
    lane_slab(lv, s0, s0 + 21.0, 2.5, 3.15)
    x = low_hops(lv, s0 + 1.5, True, 4, 100, spacing=4.8, period=0.9)
    lv.shards_along(s0 + 1.0, s0 + 20.0, 1.6, lift=0.2)

    lv.group("DoubleChain")
    x = orbit_dj(lv, s0 + 24.0, True, 95, spin=2.6)
    x = rock_dj(lv, x + 11.5, 95, period=0.95)
    end = x + 11.5
    lv.shards_along(s0 + 23.0, end - 3.0, 1.8)

    lv.group("Turning")
    g = air_gate(lv, end, False, 100, kind="dual", period=0.95)
    x = rock(lv, g + 5.5, 100, period=1.0)
    g = air_gate(lv, x + 7.7, True, 100, period=0.9)
    x = orbit_hop(lv, g + 5.5, True, 100, bodies=3, spin=-2.6)
    g = air_gate(lv, x + 7.7, False, 95, kind="dual", period=0.9)
    x = slot(lv, g + 5.5, 95, period=0.9)
    x = orbit_dj(lv, x + 7.7, False, 95, spin=2.7)
    x = rock_dj(lv, x + 11.5, 95, period=0.95)
    lv.shards_along(g - 12.0, x + 9.0, 2.2)
    return finish_level(lv, floor, top, x + 16.0)


def level_05():
    """THE HORRIFYING GALAXY — brutal but fair, in five parts: mastery of the
    ground; gravity turning fast; spires and asteroids on both surfaces;
    long chained sequences; and the FINAL GRAVITY GAUNTLET, the last 15%,
    the hardest stretch of the world."""
    lv = new_level("level_05", 5, "The Horrifying Galaxy", "Brutal but fair", 1.28)
    floor, top = start_level(lv)

    lv.group("GroundMastery")
    x = mine_pair(lv, 15.0, 3.1, 0.1)
    x = orbit_hop(lv, x + 7.9, False, 110, spin=2.6)
    x = slot(lv, x + 7.9, 100, period=0.95)
    x = orbit_dj(lv, x + 7.9, False, 100, spin=2.6)
    x = trap(lv, x + 11.8, False, 100, period=0.95)
    x = orbit_hop(lv, x + 7.9, False, 100, bodies=3, spin=-2.7)
    lv.shards_along(15.0, x + 6.0, 3.2)

    lv.group("FastFlips")
    lv.lens(x + 5.0, C * 0.5, 70.0)
    g = air_gate(lv, x + 7.9, True, 100, period=0.95)
    x = trap(lv, g + 5.7, True, 100, period=0.95)
    g = air_gate(lv, x + 7.9, False, 100, kind="dual", period=0.9)
    x = rock(lv, g + 5.7, 100, period=1.0)
    g = air_gate(lv, x + 7.9, True, 100, period=0.9)
    x = slot(lv, g + 5.7, 100, period=0.9)
    g = air_gate(lv, x + 7.9, False, 100, kind="dual", period=0.9)
    x = mine_pair(lv, g + 5.7, 3.2, 0.7)
    lv.shards_along(g - 30.0, x + 6.0, 3.2)

    lv.group("BothSurfaces")
    x = slot(lv, x + 7.9, 90, period=0.9, phase=0.25)
    f0 = x + 8.0
    f1 = f0 + 32.0
    lv.flip_field(f0, f1, True, C)
    fl = flip_land_x(lv, f0)
    lv.echo(fl - 0.5, fl + 3.0, C - 0.8, C, True)
    x = rock(lv, fl + 2.6, 90, period=0.95)
    x = orbit_dj(lv, x + 7.9, True, 90, spin=2.7)
    ob = flip_land_x(lv, f1)
    lv.echo(ob - 0.5, ob + 3.0, 0.0, 0.8, False, danger=True)
    x = slot(lv, ob + 2.6, 90, period=0.85, phase=0.5)
    x += 7.9
    lv.tap(x)
    lv.mine(x + 2.4, C, phase=0.3)
    lv.mine(x + 5.4, C, phase=0.8)
    lv.tune(lv.asteroid(x + 3.9, C, period=0.9), x, 90)
    lv.shards_along(fl, x + 6.0, 3.2)

    lv.group("Chains")
    x = orbit_dj(lv, x + 8.2, False, 90, spin=2.7)
    x = rock_dj(lv, x + 11.8, 90, period=0.95)
    g = air_gate(lv, x + 11.8, True, 90, period=0.9)
    x = rock_dj(lv, g + 5.7, 90, period=1.0)
    g = air_gate(lv, x + 11.8, False, 90, kind="dual", period=0.85)
    x = orbit_hop(lv, g + 5.7, False, 90, bodies=3, spin=2.8)
    lv.shards_along(g - 40.0, x + 6.0, 3.0)

    lv.group("FinalGauntlet")
    x = orbit_dj(lv, x + 7.9, False, 85, spin=2.8)
    g = air_gate(lv, x + 11.8, True, 85, period=0.85)
    lv.echo(g + 1.5, g + 5.0, C - 0.8, C, True, danger=True)
    s1 = g + 3.3
    lane_slab(lv, s1, s1 + 29.0, 2.5, 3.15)
    x = low_hops(lv, s1 + 1.5, True, 6, 85, spacing=4.5, period=0.8)
    g = air_gate(lv, s1 + 31.0, False, 85, kind="dual", period=0.8)
    x = orbit_dj(lv, g + 5.7, False, 85, spin=2.9)
    lv.shards_along(g - 38.0, x + 9.0, 2.6)
    return finish_level(lv, floor, top, x + 12.5)


LEVELS = [level_01, level_02, level_03, level_04, level_05]


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


def timing_load(lv, taps, span=4.5):
    """Precision per second: log2(1000 ms / window) summed over the taps, per
    second of run; the whole level, its last 15% and the hardest `span` s."""
    speed = lv.tiles_per_second()
    start = lv.spawn_px / T
    fin = lv.finish_x
    bits = [(x, math.log2(1000.0 / ms)) for x, ms in taps]
    whole = sum(b for _, b in bits) / ((fin - start) / speed)
    cut = start + 0.85 * (fin - start)
    last = sum(b for x, b in bits if x >= cut) / ((fin - cut) / speed)
    width = span * speed
    peak, at = 0.0, 0.0
    x = start
    while x + width <= fin:
        b = sum(v for tx, v in bits if x <= tx < x + width) / span
        if b > peak:
            peak, at = b, x
        x += 0.5
    return (f"load {whole:.2f} bit/s | last 15% {last:.2f} | peak {peak:.2f} at x={at:.0f}..{at + width:.0f} | "
            f"min {min(ms for _, ms in taps):.0f} ms, median {sorted(ms for _, ms in taps)[len(taps) // 2]:.0f} ms")


def main():
    args = sys.argv[1:]
    only = [int(a) for a in args if a.isdigit()] or list(range(1, len(LEVELS) + 1))
    taps_arg = [int(t) for a in args if a.startswith("--taps=") for t in a[7:].split(",")]
    routes = read_routes()
    os.makedirs(ROOT + f"levels/{WORLD}", exist_ok=True)
    for i in only:
        lv = LEVELS[i - 1]()
        taps = lv.report(windows="--windows" in args or bool(taps_arg) or "--load" in args, only=taps_arg or None)
        if taps and not taps_arg:
            print("  " + timing_load(lv, taps))
        for note in lv.notes:
            print("  " + note)
        print("  gravity:", ", ".join(f"{x:.1f}{'↑' if up else '↓'}" for x, up in lv.gravity_changes()))
        lv.write(ROOT, f"levels/{WORLD}/level_{i:02d}.tscn", f"levels/{WORLD}/level_{i:02d}.tres")
        routes[i] = (lv.name, sorted(lv.route))
    write_routes(routes)
    write_world(len(LEVELS))


if __name__ == "__main__":
    main()
