#!/usr/bin/env python3
"""World 04 — THE INVERTED GARDEN: five levels built on the SURFACE LATCH.

    python3 tools/levelgen/world_04.py              # build, check, write all levels
    python3 tools/levelgen/world_04.py 3 --windows  # one level, with tap windows

Writes levels/world_04/level_0N.tscn + .tres, levels/world_04/world_04.tres
and levels/world_04/world_04_routes.gd.

The corridor: floating islands of garden, the ground's top at height 0 and a
ceiling whose underside is C tiles up. The run is always left to right. A tap
jumps away from the surface you run on; a tap in the air (the second tap)
LATCHES to the other surface if it is within reach (160 px of the body's far
side: from a flat jump, a ceiling up to about 5.6 tiles), and is spent if it
is not. Gravity belongs to the player: it points at whichever surface it last
latched to. Heights below are world heights (tiles above the ground line);
taps are x positions of the player's centre, as in the other worlds.
"""
import math
import os
import sys

from levelgen import (Level, T, DT, HALF, G_UP, G_DOWN, MAX_FALL, V_JUMP, LATCH_REACH, latch_ticks,
                      GROUND, CEILING, FLOOR, SKY)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..") + "/"
WORLD = "world_04"
# The standard corridor: the ceiling's underside C tiles above the ground.
C = 5.0
# Thickness of the floating islands (the sky shows past them).
SLAB = 1.5


# ------------------------------------------------------------------ physics --

def jump_height_at(tau):
    """Feet height (px) tau s after a flat jump's take-off."""
    if tau <= 0.36:
        return V_JUMP * tau - 0.5 * G_UP * tau * tau
    return 150.0 - 0.5 * G_DOWN * (tau - 0.36) ** 2


def latch_window(corridor=C):
    """(earliest, latest) s after a flat take-off at which a latch reaches a
    ceiling `corridor` tiles up (None if it never does): the head must be
    within LATCH_REACH of it."""
    need = corridor * T - 2 * HALF - LATCH_REACH
    ts = [i * DT for i in range(1, 60) if jump_height_at(i * DT) >= need]
    return (ts[0], ts[-1]) if ts else None


# ------------------------------------------------------------------ terrain --

class Band:
    """One side of the corridor along the level: a face height per stretch
    (steps), holes (pits), slick stretches; emitted as garden islands."""

    def __init__(self, x0, x1, height, ceiling):
        self.x0, self.x1, self.height, self.ceiling = x0, x1, height, ceiling
        self.over = []    # (x0, x1, height)
        self.holes = []   # (x0, x1)
        self.slick = []   # (x0, x1)

    def set(self, x0, x1, height):
        self.over.append((x0, x1, height))

    def cut(self, x0, x1):
        self.holes.append((x0, x1))

    def slicken(self, x0, x1):
        self.slick.append((x0, x1))

    def _at(self, x):
        h = self.height
        for a, b, v in self.over:
            if a <= x < b:
                h = v
        slick = any(a <= x < b for a, b in self.slick)
        hole = any(a <= x < b for a, b in self.holes)
        return None if hole else (h, slick)

    def emit(self, lv):
        cuts = {self.x0, self.x1}
        for a, b, _ in self.over:
            cuts |= {a, b}
        for a, b in self.holes + self.slick:
            cuts |= {a, b}
        xs = sorted(x for x in cuts if self.x0 <= x <= self.x1)
        runs = []
        for a, b in zip(xs, xs[1:]):
            if b - a < 1e-6:
                continue
            state = self._at((a + b) * 0.5)
            if runs and runs[-1][2] == state and abs(runs[-1][1] - a) < 1e-6:
                runs[-1] = (runs[-1][0], b, state)
            else:
                runs.append((a, b, state))
        for a, b, state in runs:
            if state is None:
                continue
            h, slick = state
            if self.ceiling:
                lv.garden_block(a, b, h + SLAB, h, top_edge=False, bottom_edge=True, latchable=not slick)
            else:
                lv.garden_block(a, b, h, h - SLAB, latchable=not slick)


def new_level(key, number, name, tagline, speed, corridor=C):
    """A World 04 level whose corridor is `corridor` tiles tall (the taller,
    the nearer the top of the jump a latch has to come)."""
    lv = Level(key, f"w04_l{number:02d}", name, tagline, speed)
    lv.latch_mode = True
    lv.corridor = (0.0, corridor)
    lv.kill_y = 7.0 * T
    lv.kill_top = -(corridor + 7.0) * T
    # Centre the corridor on screen whichever surface is the floor.
    lv.camera_offset = -(corridor * T * 0.5 - HALF)
    lv.tune_any_kind = True
    return lv


def start_level(lv):
    for x in (7, 9, 11):
        lv.shard(x, 0.5)
    return Band(-12.0, 0.0, 0.0, False), Band(-12.0, 0.0, lv.corridor[1], True)


def finish_level(lv, floor, top, end, up=False):
    lv._dbg_floor, lv._dbg_top = floor, top
    floor.x1 = top.x1 = end + 16.0
    floor.emit(lv)
    top.emit(lv)
    lv.finish(end, lv.corridor[1] if up else 0.0, up=up)
    lv.done()
    lv.progress_checkpoints()
    return lv


# ------------------------------------------------------------------- beats --
# Each beat is one tap (or a jump and its latch) at x, the player's centre,
# on the current floor (`up`: the ceiling). Hazards that move are tuned
# against their tap to a target window (ms).

def land_after_latch(lv, x_jump, x_latch, corridor=None):
    """Where (x) a jump at x_jump latched at x_latch touches the other surface
    of a corridor `corridor` tiles tall (default: the level's)."""
    corridor = corridor or lv.corridor[1]
    t = (x_latch - x_jump) / lv.tiles_per_second()
    d = max(corridor * T - 2 * HALF - jump_height_at(t), 0.0)
    return x_latch + (latch_ticks(d) + 1) * lv.tiles_per_tick()


def cross(lv, x, dt=0.33, corridor=None):
    """Jump at x and latch `dt` s after: over to the other surface. Returns
    (latch x, landing x)."""
    lv.tap(x)
    xl = round(x + dt * lv.tiles_per_second(), 3)
    lv.latch(xl)
    return xl, land_after_latch(lv, x, xl, corridor)


class Plan:
    """Which surface exists where. After every crossing the surface you left
    is cut away until just before the next crossing back needs it, so every
    latch is necessary, a missed one is a fall, and every stretch has a
    single floor (a checkpoint can stand anywhere). Choice regions (a low
    tunnel, a slick patch, a gust, a moving ceiling) keep both surfaces:
    close() before them, open() after."""

    def __init__(self, lv, floor, top):
        self.lv, self.floor, self.top = lv, floor, top
        self.up = False
        self.cut_from = None

    def _other(self):
        return self.floor if self.up else self.top

    def close(self, x):
        """The surface across from you is there again from x on."""
        if self.cut_from is not None and x > self.cut_from + 0.5:
            self._other().cut(self.cut_from, x)
        self.cut_from = None

    def open(self, x):
        """The surface across from you is gone from x on."""
        self.close(x)
        self.cut_from = x

    def cross(self, xj, dt=0.33, corridor=None, keep=False):
        """Jump at xj, latch dt s later; the target surface is there from
        just before the latch, the one left behind gone after the landing
        (unless `keep`). Returns (latch x, landing x)."""
        self.close(xj + dt * self.lv.tiles_per_second() - 1.2)
        xl, land = cross(self.lv, xj, dt, corridor)
        self.up = not self.up
        if not keep:
            self.open(land + 1.0)
        return xl, land


def glade(lv, floor, top, x, up, length=8.0):
    """A calm stretch where only the surface you run on exists (the other
    side opens onto the sky): no obstacle, no tap, a place for a checkpoint
    to stand (it can only be reached, and respawned on, on that surface).
    Returns where it ends."""
    (floor if up else top).cut(x, x + length)
    return x + length


def after(lv, x, extra=0.0):
    """The earliest x for the next jump after a flat hop at x: the hop's
    airtime (0.676 s) and four ticks of margin, plus `extra` tiles."""
    return x + (0.676 + 4 * DT) * lv.tiles_per_second() + extra


def hop(lv, x, hazard, ms):
    """A plain jump at x over `hazard`, tuned to a `ms` window."""
    lv.tap(x)
    lv.tune(hazard, x, ms)
    return x


# ------------------------------------------------------------------ levels --

def rock_hop(lv, x, ms, period=1.4, at=2.6, anchor=SKY, ice=False, corridor=None):
    """A jump at x past a column where stones fall across the corridor. (A
    stone picks its side as it breaks loose and takes about 0.9 s to cross
    the corridor, so a column that can reach you stands at least 9 tiles
    past the landing of a latch.)"""
    lv.tap(x)
    lv.tune(lv.rock(x + at, anchor=anchor, period=period, ice=ice, c=corridor), x, ms)
    return x


def torrent_hop(lv, x, ms, period=1.8, hold_ratio=0.45, phase=0.0, length=2.6, corridor=None):
    """Roots on your floor under a waterfall pouring from the sky side,
    `length` tiles down: the jump over the roots rises into the water, so it
    has to pass while the waterfall is a thread."""
    lv.tap(x)
    lv.tune(lv.roots(x + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.3, c=corridor), x, ms + 60)
    lv.tune(lv.waterfall(x + 2.9, width=76.0, length=length * T, deadly=True, period=period, hold_ratio=hold_ratio,
                         phase=phase, c=corridor), x, ms)
    return x


def branch_hop(lv, x, ms, up, period=1.8, amplitude=0.9, length=2.6, corridor=None):
    """Roots on your floor and a branch swinging from the far side into the
    arc of the jump: jump while it is swung aside (up: you run on the
    ceiling, so the branch rises from the ground)."""
    lv.tap(x)
    lv.tune(lv.roots(x + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.3, c=corridor), x, ms + 60)
    lv.tune(lv.branch(x + 3.0, length, anchor=GROUND if up else CEILING, amplitude=amplitude, period=period,
                      c=corridor), x, ms)
    return x


def quick_cross(lv, x, corridor, dt=0.12):
    """In a low tunnel the other surface is in reach almost at once: jump and
    latch in quick succession (a double tap, `dt` s apart)."""
    return cross(lv, x, dt, corridor)


def curtain_hop(lv, x, ms, period=1.6, corridor=None):
    """Roots on your floor under a painted curtain hanging from the side
    across: it rolls down into the arc of the jump, so the jump goes while
    it is rolled up."""
    lv.tap(x)
    lv.tune(lv.roots(x + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.3, c=corridor), x, ms + 60)
    lv.tune(lv.curtain(x + 2.2, 1.9, 0.6, 2.6, anchor=SKY, period=period, hold_ratio=0.4, c=corridor), x, ms)
    return x


def rest(lv, x, length=8.0):
    """A calm stretch: nothing to jump, a place for a checkpoint to stand."""
    return x + length


def level_01():
    """BLUE BLOOM — hard. Teaches GROUND -> jump -> second tap -> CEILING ->
    back to the GROUND: a low ceiling for the first latch, roots and flowers
    that grow where you run, the ceiling ending under you, a wave, a gust
    that carries the latch to a ceiling too high for a jump (and back down:
    wind acts from your floor); then a last third that keeps crossing, with
    less time for every move."""
    lv = new_level("level_01", 1, "Blue Bloom", "Hard", 1.16)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top)
    plan.open(13.0)

    lv.group("Garden")
    x = hop(lv, 16.0, lv.roots(18.1, 1.5, 1.6, period=1.7), 240)
    x = hop(lv, after(lv, x, 1.0), lv.flower(after(lv, x, 4.1), period=1.9), 220)
    x = hop(lv, after(lv, x, 0.5), lv.roots(after(lv, x, 2.7), 1.5, 1.6, period=1.6), 220)
    lv.shards_along(14.0, x + 5.0, 2.5)

    lv.group("FirstLatch")
    # The first ceiling is low (easy to reach) and the ground ends: go up.
    xj = after(lv, x, 2.0)
    top.set(xj - 4.0, xj + 40.0, 4.5)
    xl, land = plan.cross(xj, 0.30, 4.5)
    lv.waterfall(land + 9.5, width=46.0, length=180.0, anchor=CEILING, c=4.5)
    x = hop(lv, land + 3.2, lv.roots(land + 5.5, 1.5, 1.4, anchor=FLOOR, period=1.6, c=4.5), 230)
    lv.shards_along(xl, x + 8.0, 2.0)

    lv.group("BackDown")
    # The ceiling ends: come back down before it does.
    xl, land = plan.cross(after(lv, x, 1.5), 0.33, 4.5)
    x = hop(lv, land + 4.0, lv.flower(land + 7.2, period=1.8), 210)
    lv.shards_along(xl, x + 4.0, 2.2)

    lv.group("Rest")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("Wave")
    x = hop(lv, x, lv.wave(x + 5.0, 6.0, period=2.6), 200)
    x = hop(lv, after(lv, x), lv.roots(after(lv, x, 2.2), 1.6, 1.6, period=1.5), 200)
    lv.shards_along(x - 9.0, x + 6.0, 2.0)

    lv.group("Gust")
    # A ceiling too high for any jump (6 tiles): only the updraft's lift
    # carries the latch up to it.
    xj = after(lv, x, 1.0)
    top.set(xj - 4.0, xj + 34.0, 6.0)
    wind = lv.wind(xj - 2.2, 10.0, -1100.0, period=1.8, hold_ratio=0.35, c=6.0)
    xl, land = plan.cross(xj, 0.36, 6.0)
    lv.tune(wind, [xj, xl], 220)
    x = hop(lv, land + 3.5, lv.flower(land + 6.7, anchor=FLOOR, period=1.8, c=6.0), 200)
    lv.shards_along(xj, x + 5.0, 2.0)

    lv.group("Down")
    # Still six tiles tall: the gust that lifted you up now lifts you away
    # from the ceiling, down to the ground (wind acts from your floor).
    xj = after(lv, x, 0.5)
    back = lv.wind(xj - 2.0, 10.0, -1100.0, period=1.8, hold_ratio=0.35, c=6.0)
    xl, land = plan.cross(xj, 0.36, 6.0)
    lv.tune(back, [xj, xl], 220)
    x = hop(lv, land + 3.5, lv.roots(land + 5.6, 1.8, 1.6, period=1.4), 180)

    lv.group("Rest2")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("Pressure")
    # The last third: cross, hop, cross, with less time for each.
    xl, land = plan.cross(x, 0.33)
    x = hop(lv, land + 2.6, lv.flower(land + 5.7, anchor=FLOOR, period=1.3, hold_ratio=0.5), 170)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.4, lv.wave(land + 6.8, 4.5, period=2.0, speed=380.0), 160)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.6, lv.roots(land + 4.8, 1.6, 1.6, anchor=FLOOR, period=1.2), 160)
    x = hop(lv, after(lv, x), lv.flower(after(lv, x, 3.1), anchor=FLOOR, period=1.3, hold_ratio=0.5), 150)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.4, lv.roots(land + 4.6, 1.6, 1.6, period=1.2), 150)
    x = hop(lv, after(lv, x), lv.flower(after(lv, x, 3.1), period=1.2, hold_ratio=0.5), 150)
    lv.shards_along(land - 30.0, x + 6.0, 2.4)
    return finish_level(lv, floor, top, x + 12.0)


def level_02():
    """FALLING GARDEN — very hard. Everything falls: stones from the sky side
    onto the floor you run on, waterfalls swelling into torrents, vines
    letting themselves down, branches swinging across. More crossings, and
    crossings through what is falling, not only to get somewhere."""
    lv = new_level("level_02", 2, "Falling Garden", "Very hard", 1.19, corridor=5.25)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top)
    plan.open(13.0)

    lv.group("Stones")
    x = rock_hop(lv, 16.0, 170, period=1.3)
    x = hop(lv, after(lv, x), lv.roots(after(lv, x, 2.2), 1.6, 1.6, period=1.3), 160)
    x = rock_hop(lv, after(lv, x), 160, period=1.2)
    lv.shards_along(14.0, x + 5.0, 2.4)

    lv.group("Torrent")
    # A waterfall pours from the sky side into the arc of the jump: jump
    # while it is a thread.
    x = torrent_hop(lv, after(lv, x), 150, period=1.5, hold_ratio=0.35)

    lv.group("UpAndAway")
    # Vines let down from the ceiling right where you latch: go up between
    # their drops.
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.vines(xl - 1.2, 1.4, 0.9, 2.6, period=1.4), [xj, xl], 150, forced=False)
    x = hop(lv, land + 2.8, lv.roots(land + 5.0, 1.5, 1.5, anchor=FLOOR, period=1.25), 150)
    lv.shards_along(x - 8.0, x + 6.0, 2.0)

    lv.group("Branch")
    # A branch swings up from the ground, across the arc of the ceiling
    # runner's jump: jump while it is swung aside.
    x = branch_hop(lv, after(lv, x), 140, True, period=1.5)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = rock_hop(lv, land + 9.0, 140, period=1.15)

    lv.group("Rest")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("Falls")
    x = hop(lv, x, lv.flower(x + 3.1, period=1.2, hold_ratio=0.5), 140)
    x = torrent_hop(lv, after(lv, x), 130, period=1.4, hold_ratio=0.35)
    x = torrent_hop(lv, after(lv, x), 130, period=1.35, hold_ratio=0.35, phase=0.3)
    # Up through a waterfall pouring from the ceiling side, and back down
    # through one pouring from the ground side (it always pours from the sky).
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.waterfall(xl - 0.2, width=70.0, length=140.0, deadly=True, period=1.4, hold_ratio=0.35), [xj, xl], 130,
            forced=False)
    x = hop(lv, land + 2.8, lv.roots(land + 5.0, 1.5, 1.5, anchor=FLOOR, period=1.2), 135)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.waterfall(xl - 0.2, width=70.0, length=140.0, deadly=True, period=1.35, hold_ratio=0.35), [xj, xl], 130,
            forced=False)
    x = hop(lv, land + 2.8, lv.flower(land + 5.9, period=1.2, hold_ratio=0.5), 135)
    lv.shards_along(x - 16.0, x + 5.0, 2.2)

    lv.group("Avalanche")
    # Stones fall through the way up: latch between them.
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    for i in range(2):
        lv.tune(lv.rock(xl - 0.4 + 1.9 * i, anchor=SKY, period=1.3, warning=0.5), [xj, xl], 140, forced=False)
    x = hop(lv, land + 11.5, lv.flower(land + 14.6, anchor=FLOOR, period=1.2, hold_ratio=0.5), 135)
    lv.shards_along(xl, x + 4.0, 2.0)

    lv.group("Swing")
    # Down again through a branch swinging up from the ground.
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.branch(xl + 0.4, 3.0, anchor=GROUND, amplitude=0.85, period=1.6), [xj, xl], 130, forced=False)
    x = rock_hop(lv, land + 9.0, 130, period=1.1)

    lv.group("Rest2")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("Downpour")
    x = hop(lv, x, lv.roots(x + 2.2, 1.6, 1.6, period=1.1), 125)
    x = torrent_hop(lv, after(lv, x), 120, period=1.3, hold_ratio=0.35)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.vines(xl - 1.2, 1.4, 0.9, 2.6, period=1.25), [xj, xl], 120, forced=False)
    x = hop(lv, land + 2.8, lv.roots(land + 5.0, 1.5, 1.5, anchor=FLOOR, period=1.1), 120)
    x = branch_hop(lv, after(lv, x), 115, True, period=1.3)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = rock_hop(lv, land + 9.0, 115, period=1.05)
    x = hop(lv, after(lv, x), lv.flower(after(lv, x, 3.1), period=1.1, hold_ratio=0.5), 115)
    lv.shards_along(x - 30.0, x + 6.0, 2.4)
    return finish_level(lv, floor, top, x + 12.0)


def level_03():
    """THE FLOODED SKY — extremely hard. Water and ink: waves that form on the
    surface you run on, ink rivers too long to jump that belong to one
    surface, a low tunnel where the latch is a quick double tap, flocks
    sweeping across the way you cross, a waterfall pouring from the sky
    side. GROUND -> CEILING -> water -> GROUND -> double action -> CEILING:
    the second tap has to be planned."""
    lv = new_level("level_03", 3, "The Flooded Sky", "Extremely hard", 1.22, corridor=5.3)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top)
    plan.open(13.0)

    lv.group("Tide")
    x = hop(lv, 16.0, lv.roots(18.2, 1.6, 1.6, period=1.4), 180)
    xt = after(lv, x)
    x = hop(lv, xt, lv.wave(xt + 5.0, 5.0, anchor=FLOOR, period=2.3, speed=360.0), 160)
    lv.shards_along(14.0, x + 5.0, 2.4)

    lv.group("Up")
    xl, land = plan.cross(after(lv, x), 0.33)
    xt = land + 2.6
    x = hop(lv, xt, lv.wave(xt + 5.0, 4.5, anchor=FLOOR, period=2.1, speed=380.0), 150)

    lv.group("InkRiver")
    # Ink floods the ceiling ahead, too far to jump: back down (and it would
    # be down there too, had you stayed on the ground).
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.8, lv.flower(land + 5.9, period=1.4, hold_ratio=0.5), 150)
    lv.shards_along(xl - 6.0, x + 5.0, 2.2)

    lv.group("Flock")
    xt = after(lv, x)
    lv.tap(xt)
    lv.tune(lv.roots(xt + 2.3, 1.4, 1.5, period=1.3), xt, 210)
    lv.tune(lv.flock(xt + 3.4, 2.3, 1.4, period=1.8), xt, 150)
    x = xt

    lv.group("Rest")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("DoubleAction")
    # A low tunnel (4 tiles, both surfaces): ink takes the ground, then the
    # ceiling, then the ground again. Jump and latch at once, three times:
    # up, down, up.
    t0 = x
    plan.close(t0 - 1.0)
    xl, land = quick_cross(lv, t0 + 1.0, 4.0)
    lv.ink(land + 9.0, 7.5, anchor=GROUND, period=2.4, hold_ratio=0.1, c=4.0)
    xl, land = quick_cross(lv, land + 8.4, 4.0)
    lv.ink(land + 9.0, 7.5, anchor=CEILING, period=2.4, hold_ratio=0.1, c=4.0)
    xl, land = quick_cross(lv, land + 8.4, 4.0)
    lv.ink(land + 11.0, 6.0, anchor=GROUND, period=2.4, hold_ratio=0.1, c=4.0)
    plan.up = True
    lv.shards_along(t0, land + 3.0, 1.6)
    xt = land + 3.6
    x = hop(lv, xt, lv.wave(xt + 5.0, 4.5, anchor=FLOOR, period=2.0, speed=380.0, c=4.0), 140)
    x = hop(lv, after(lv, x), lv.roots(after(lv, x, 2.2), 1.6, 1.6, anchor=FLOOR, period=1.2, c=4.0), 140)
    lv.shards_along(x - 12.0, x + 4.0, 2.2)
    # The tunnel ends: the ceiling steps back up to five tiles, and the
    # ground falls away.
    tunnel_end = after(lv, x, -1.0)
    top.set(t0 - 1.0, tunnel_end, 4.0)
    plan.open(tunnel_end)

    lv.group("Pour")
    # A waterfall pours from the ground side (the sky, seen from up here)
    # while you cross down through it: go when it is a thread.
    xj = tunnel_end + 3.0
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.waterfall(xl + 0.6, width=70.0, length=150.0, deadly=True, period=1.6, hold_ratio=0.4), [xj, xl], 140,
            forced=False)
    x = hop(lv, land + 3.0, lv.wave(land + 7.5, 5.0, anchor=FLOOR, period=2.0, speed=380.0), 130)

    lv.group("Rest2")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("Flood")
    # The last third: flip, flip, flip, each one planned around the water.
    xl, land = plan.cross(x, 0.33)
    xt = land + 2.6
    lv.tap(xt)
    lv.tune(lv.roots(xt + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.2), xt, 190)
    lv.tune(lv.flock(xt + 3.4, 2.7, 1.3, period=1.7), xt, 130)
    x = hop(lv, after(lv, xt), lv.roots(after(lv, xt, 2.2), 1.6, 1.6, anchor=FLOOR, period=1.2), 130)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.flock(xl + 0.8, 2.5, 1.5, period=1.8), [xj, xl], 120, forced=False)
    x = hop(lv, land + 3.0, lv.wave(land + 7.5, 5.0, anchor=FLOOR, period=1.9, speed=400.0), 120)
    x = hop(lv, after(lv, x), lv.flower(after(lv, x, 3.1), period=1.2, hold_ratio=0.5), 120)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.8, lv.ink(land + 7.0, 3.4, anchor=FLOOR, period=1.4, hold_ratio=0.3), 120)
    x = hop(lv, after(lv, x), lv.roots(after(lv, x, 2.2), 1.6, 1.6, anchor=FLOOR, period=1.2), 120)
    lv.shards_along(x - 34.0, x + 6.0, 2.4)
    return finish_level(lv, floor, top, x + 12.0, up=True)


def level_04():
    """PAINTED STORM — brutal. The garden turns into a storm of paint: gusts
    that press you down (no latch while they blow) or lift you (a latch
    reaches a ceiling no jump can), curtains unrolling from the side across
    from you, razor leaves gliding through the corridor, a slick ceiling with
    one place to hold, a ceiling that rises and sinks, boulders. The colour
    of the world is part of the puzzle: flowers bloom on the surface you run
    on and curtains hang from the other, so every flip rearranges them."""
    lv = new_level("level_04", 4, "Painted Storm", "Brutal", 1.25, corridor=5.4)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top)
    plan.open(13.0)

    lv.group("Gale")
    x = hop(lv, 16.0, lv.flower(19.1, anchor=FLOOR, period=1.2, hold_ratio=0.5), 130)
    x = curtain_hop(lv, after(lv, x), 120, period=1.4)
    lv.shards_along(14.0, x + 5.0, 2.4)

    lv.group("Downdraft")
    # Gusts press you onto the ground: a latch only reaches between them.
    xj = after(lv, x)
    plan.close(xj - 6.0)
    gust = lv.wind(xj - 3.0, 9.0, 950.0, period=1.3, hold_ratio=0.45)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(gust, [xj, xl], 120, forced=False)
    xt = land + 2.8
    lv.tap(xt)
    lv.tune(lv.roots(xt + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.3), xt, 190)
    lv.tune(lv.glider(xt + 3.2, 3.6, loop=(110.0, 50.0), period=2.0), xt, 115)
    x = xt

    lv.group("Slick")
    # Down to the ground, then up again under a slick ceiling that holds in
    # one place only: the latch has to land there.
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.8, lv.flower(land + 5.9, anchor=FLOOR, period=1.2, hold_ratio=0.5), 115)
    xj = after(lv, x)
    plan.close(xj - 7.0)
    xl, land = plan.cross(xj, 0.33)
    top.slicken(xj - 7.0, xl - 0.9)
    top.slicken(land + 2.6, land + 9.0)
    x = hop(lv, land + 2.6, lv.roots(land + 4.8, 1.5, 1.5, anchor=FLOOR, period=1.1), 115)
    lv.shards_along(xj - 8.0, x + 5.0, 2.0)

    lv.group("Rest")
    xl, land = plan.cross(after(lv, x), 0.33)
    x = rest(lv, land + 2.0, 7.0)

    lv.group("PaintedSwitch")
    # Flowers bloom where you run, curtains hang from across: flip, and the
    # garden rearranges itself around you.
    x = hop(lv, x, lv.flower(x + 3.1, anchor=FLOOR, period=1.15, hold_ratio=0.5), 115)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.6, lv.flower(land + 5.7, anchor=FLOOR, period=1.1, hold_ratio=0.5), 110)
    x = curtain_hop(lv, after(lv, x), 105, period=1.3)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.curtain(xl - 0.6, 1.6, 0.5, 2.4, anchor=SKY, period=1.25, hold_ratio=0.4), [xj, xl], 105, forced=False)
    x = hop(lv, land + 2.8, lv.ink(land + 7.0, 3.4, anchor=FLOOR, period=1.3, hold_ratio=0.3), 105)
    lv.shards_along(xj - 30.0, x + 5.0, 2.2)

    lv.group("Rising")
    # A ceiling that rises out of reach and sinks back: latch as it comes down.
    m0 = after(lv, x) + 1.0
    plan.close(m0 - 1.0)
    top.cut(m0 - 1.0, m0 + 8.0)
    lift = lv.garden_mover(m0 - 1.0, 9.0, lv.corridor[1] + 0.5, (0.0, 1.3), 1.5, thickness=0.5)
    xl, land = plan.cross(m0 + 0.4, 0.34)
    lv.tune(lift, [m0 + 0.4, xl], 110, osc=True, forced=False)
    x = land

    lv.group("Boulders")
    # Down from the rising ceiling before it ends (nothing after it up there),
    # past a swinging boulder.
    top.cut(m0 + 8.0, m0 + 20.0)
    xj = max(land + 1.5, m0 + 3.6)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.boulder(xl + 1.0, 2.6, radius=38.0, amplitude=0.8, period=1.7), [xj, xl], 105, forced=False)
    x = hop(lv, land + 3.0, lv.roots(land + 5.2, 1.6, 1.6, period=1.05), 105)

    lv.group("Rest2")
    x = rest(lv, after(lv, x), 8.0)

    lv.group("Storm")
    xj = x
    top.set(xj - 4.0, xj + 20.0, 6.0)
    lift2 = lv.wind(xj - 3.0, 10.0, -1100.0, period=1.4, hold_ratio=0.35, c=6.0)
    xl, land = plan.cross(xj, 0.36, 6.0)
    lv.tune(lift2, [xj, xl], 105, forced=False)
    xt = land + 2.8
    lv.tap(xt)
    lv.tune(lv.roots(xt + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.2, c=6.0), xt, 170)
    lv.tune(lv.glider(xt + 3.2, 4.2, loop=(110.0, 50.0), period=1.9), xt, 100)
    x = xt
    xj = after(lv, x)
    down = lv.wind(xj - 3.0, 9.0, -1100.0, period=1.3, hold_ratio=0.35, c=6.0)
    xl, land = plan.cross(xj, 0.36, 6.0)
    lv.tune(down, [xj, xl], 100, forced=False)
    x = curtain_hop(lv, land + 3.0, 95, period=1.25)
    x = hop(lv, after(lv, x), lv.flower(after(lv, x, 3.1), anchor=FLOOR, period=1.1, hold_ratio=0.5), 95)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.branch(xl + 0.4, 2.8, anchor=GROUND, amplitude=0.85, period=1.5), [xj, xl], 95, forced=False)
    x = hop(lv, land + 2.8, lv.ink(land + 7.0, 3.4, anchor=FLOOR, period=1.2, hold_ratio=0.3), 95)
    lv.weather(xj - 40.0, x + 12.0, "leaves", density=0.7)
    lv.shards_along(xj - 30.0, x + 6.0, 2.4)
    return finish_level(lv, floor, top, x + 12.0, up=True)


def level_05():
    """THE INVERTED GARDEN — brutal but fair. No new ability: the jump, the
    second tap, timing, reading, and knowing which surface to be on. Every
    obstacle of the garden, a snowfall where ice falls among the flakes, and
    the FINAL GAUNTLET (the last stretch, the hardest of the world): up past
    a swinging branch, down through a flock, a wave, up against a gust, a
    flower, down again, ink, and one last latch onto the ceiling right
    before the finish."""
    lv = new_level("level_05", 5, "The Inverted Garden", "Brutal but fair", 1.28, corridor=5.45)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top)
    plan.open(13.0)

    lv.group("Mastery")
    x = hop(lv, 16.0, lv.roots(18.2, 1.6, 1.6, period=1.3), 140)
    x = hop(lv, after(lv, x), lv.flower(after(lv, x, 3.1), period=1.3, hold_ratio=0.5), 130)
    x = rock_hop(lv, after(lv, x), 130, period=1.3)
    lv.shards_along(14.0, x + 5.0, 2.4)

    lv.group("Snowfall")
    s0 = after(lv, x) - 3.0
    x = rock_hop(lv, after(lv, x), 125, period=1.2, ice=True)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.rock(xl + 0.6, anchor=SKY, period=1.3, ice=True), [xj, xl], 120, forced=False)
    # (Far enough on for a shard to break loose from the ground after the
    # latch and reach the ceiling: about 0.9 s.)
    x = rock_hop(lv, land + 9.0, 120, period=1.2, ice=True)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = rock_hop(lv, land + 9.0, 115, period=1.15, ice=True)
    lv.weather(s0, x + 8.0, "snow", density=0.8)
    lv.shards_along(s0, x + 5.0, 2.2)

    lv.group("Rest")
    x = rest(lv, after(lv, x), 7.0)

    lv.group("Flight")
    xt = x
    lv.tap(xt)
    lv.tune(lv.roots(xt + 2.3, 1.4, 1.5, period=1.3), xt, 175)
    lv.tune(lv.flock(xt + 3.4, 2.4, 1.4, period=1.6), xt, 115)
    xj = after(lv, xt)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.flock(xl + 0.8, 2.5, 1.5, period=1.7), [xj, xl], 110, forced=False)
    xt = land + 2.8
    lv.tap(xt)
    lv.tune(lv.roots(xt + 2.3, 1.4, 1.5, anchor=FLOOR, period=1.2), xt, 170)
    lv.tune(lv.glider(xt + 3.2, 3.6, loop=(110.0, 50.0), period=2.0), xt, 110)
    xj = after(lv, xt)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.boulder(xl + 1.0, 2.6, radius=38.0, amplitude=0.8, period=1.8), [xj, xl], 110, forced=False)
    x = hop(lv, land + 3.0, lv.wave(land + 7.5, 5.0, anchor=FLOOR, period=1.8, speed=420.0), 110)
    lv.shards_along(xt - 8.0, x + 5.0, 2.2)

    lv.group("Rapids")
    t0 = after(lv, x) - 1.0
    plan.close(t0 - 1.0)
    xl, land = quick_cross(lv, t0 + 1.0, 4.0)
    lv.ink(land + 9.0, 7.5, anchor=GROUND, period=2.2, hold_ratio=0.1, c=4.0)
    xl, land = quick_cross(lv, land + 8.4, 4.0)
    lv.ink(land + 9.0, 7.5, anchor=CEILING, period=2.2, hold_ratio=0.1, c=4.0)
    xl, land = quick_cross(lv, land + 8.4, 4.0)
    lv.ink(land + 11.0, 6.0, anchor=GROUND, period=2.2, hold_ratio=0.1, c=4.0)
    plan.up = True
    xt = land + 3.6
    x = hop(lv, xt, lv.roots(xt + 2.2, 1.6, 1.6, anchor=FLOOR, period=1.1, c=4.0), 105)
    lv.shards_along(t0, x + 4.0, 1.8)
    tunnel_end = after(lv, x, -1.0)
    top.set(t0 - 1.0, tunnel_end, 4.0)
    plan.open(tunnel_end)

    lv.group("Tempest")
    xj = tunnel_end + 3.0
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.curtain(xl - 0.6, 1.6, 0.5, 2.4, anchor=SKY, period=1.3, hold_ratio=0.4), [xj, xl], 105, forced=False)
    x = curtain_hop(lv, land + 3.0, 105, period=1.3)

    lv.group("Rest2")
    x = rest(lv, after(lv, x), 7.0)

    lv.group("Gale")
    xj = x
    plan.close(xj - 6.0)
    gust = lv.wind(xj - 3.0, 9.0, 950.0, period=1.4, hold_ratio=0.45)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(gust, [xj, xl], 105, forced=False)
    x = hop(lv, land + 2.6, lv.flower(land + 5.7, anchor=FLOOR, period=1.15, hold_ratio=0.5), 100)
    xj = after(lv, x)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.branch(xl + 0.4, 2.8, anchor=GROUND, amplitude=0.85, period=1.6), [xj, xl], 100, forced=False)
    x = hop(lv, land + 2.8, lv.ink(land + 7.0, 3.4, anchor=FLOOR, period=1.3, hold_ratio=0.3), 100)
    lv.shards_along(xj - 16.0, x + 5.0, 2.2)

    lv.group("FinalGauntlet")
    # GROUND -> jump -> latch -> CEILING -> swinging branch -> release ->
    # latch -> GROUND -> wave -> jump -> latch -> CEILING (against a gust) ->
    # flower -> flip -> ink -> the last latch, and the finish.
    xl, land = plan.cross(after(lv, x), 0.33)
    xt = branch_hop(lv, land + 2.7, 95, True, period=1.4, length=2.4)
    xj = after(lv, xt)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(lv.flock(xl + 0.8, 2.5, 1.5, period=1.5), [xj, xl], 95, forced=False)
    x = hop(lv, land + 3.0, lv.wave(land + 7.5, 5.0, anchor=FLOOR, period=1.7, speed=440.0), 95)
    xj = after(lv, x)
    plan.close(xj - 6.0)
    gust = lv.wind(xj - 3.0, 9.0, 950.0, period=1.3, hold_ratio=0.45)
    xl, land = plan.cross(xj, 0.33)
    lv.tune(gust, [xj, xl], 95, forced=False)
    x = hop(lv, land + 2.6, lv.flower(land + 5.7, anchor=FLOOR, period=1.1, hold_ratio=0.5), 90)
    xl, land = plan.cross(after(lv, x), 0.33)
    x = hop(lv, land + 2.8, lv.ink(land + 7.0, 3.4, anchor=FLOOR, period=1.2, hold_ratio=0.3), 90)
    xl, land = plan.cross(after(lv, x), 0.33)
    lv.shards_along(xj - 60.0, land + 1.0, 2.2)
    return finish_level(lv, floor, top, land + 2.5, up=True)


LEVELS = [level_01, level_02, level_03, level_04, level_05]


# ------------------------------------------------------------------ output --

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
    lines = ["class_name World04Routes",
             "## Generated by tools/levelgen/world_04.py: the intended solution of each",
             "## World 04 level, as the player-centre x (tiles) of every tap.", ""]
    for number in sorted(routes):
        name, taps = routes[number]
        lines.append(f"## {name}")
        lines.append(f"const LEVEL_{number:02d}: PackedFloat32Array = [{', '.join(f'{x:g}' for x in taps)}]")
    names = ", ".join(f"LEVEL_{n:02d}" for n in sorted(routes))
    lines += ["", "", "## The route of level [param index] (0-based).",
              "static func get_route(index: int) -> PackedFloat32Array:",
              f"\tvar all: Array[PackedFloat32Array] = [{names}]",
              "\treturn all[index] if index >= 0 and index < all.size() else PackedFloat32Array()"]
    with open(ROOT + f"levels/{WORLD}/{WORLD}_routes.gd", "w") as fh:
        fh.write("\n".join(lines) + "\n")


def write_world(count):
    lines = ['[gd_resource type="Resource" script_class="WorldData" format=3]', "",
             '[ext_resource type="Script" path="res://src/level/world_data.gd" id="1_world"]',
             '[ext_resource type="Script" path="res://src/level/level_data.gd" id="2_level"]',
             '[ext_resource type="PackedScene" path="res://src/background/background_garden.tscn" id="3_background"]',
             '[ext_resource type="Resource" path="res://levels/world_03/world_03.tres" id="4_requires"]']
    for i in range(1, count + 1):
        lines.append(f'[ext_resource type="Resource" path="res://levels/{WORLD}/level_{i:02d}.tres" id="level_{i:02d}"]')
    refs = ", ".join(f'ExtResource("level_{i:02d}")' for i in range(1, count + 1))
    lines += ["", "[resource]", 'script = ExtResource("1_world")', f'id = &"{WORLD}"', "number = 4",
              'display_name = "The Inverted Garden"', f'levels = Array[ExtResource("2_level")]([{refs}])',
              'next_world_name = ""', 'requires = ExtResource("4_requires")', 'theme = &"garden"',
              'background = ExtResource("3_background")', 'progress_milestones = Array[float]([50.0, 75.0])',
              'surface_latch = true']
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


def _locked(fn):
    """Runs fn() holding the routes lock: levels can be built in parallel
    processes (each rewrites the shared routes file)."""
    import fcntl
    os.makedirs(ROOT + f"levels/{WORLD}", exist_ok=True)
    with open(ROOT + f"levels/{WORLD}/.routes.lock", "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        try:
            return fn()
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def main():
    args = sys.argv[1:]
    only = [int(a) for a in args if a.isdigit()] or list(range(1, len(LEVELS) + 1))
    taps_arg = [int(t) for a in args if a.startswith("--taps=") for t in a[7:].split(",")]
    os.makedirs(ROOT + f"levels/{WORLD}", exist_ok=True)
    for i in only:
        lv = LEVELS[i - 1]()
        taps = lv.report(windows="--windows" in args or bool(taps_arg) or "--load" in args, only=taps_arg or None)
        if taps and not taps_arg:
            print("  " + timing_load(lv, taps))
        for note in lv.notes:
            print("  " + note)
        ticks, _ = lv.run()
        kinds = "".join({"ground": "J", "latch": "L", "miss": "m"}.get(s["jump"], "") for s in ticks if s["jump"])
        print(f"  taps: {kinds}  latches: {kinds.count('L')}")
        lv.write(ROOT, f"levels/{WORLD}/level_{i:02d}.tscn", f"levels/{WORLD}/level_{i:02d}.tres")

        def save(lv=lv, i=i):
            routes = read_routes()
            routes[i] = (lv.name, sorted(lv.route))
            write_routes(routes)
            write_world(len(LEVELS))
        _locked(save)


if __name__ == "__main__":
    main()
