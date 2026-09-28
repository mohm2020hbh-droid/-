#!/usr/bin/env python3
"""World 04 — THE INVERTED GARDEN: five levels built on the SURFACE ATTACH.

    python3 tools/levelgen/world_04.py              # build, check, write all levels
    python3 tools/levelgen/world_04.py 3 --windows  # one level, with its windows

Writes levels/world_04/level_0N.tscn + .tres, levels/world_04/world_04.tres
and levels/world_04/world_04_routes.gd.

The corridor: floating islands of garden, the ground's top at height 0 and a
ceiling whose underside is C tiles up. The run is always left to right, and
there is NO JUMP in World 04: TAP TAP (two taps, one gesture) attaches the
player to the surface across, a fast crossing (about 0.2 s, ~2 tiles on);
gravity turns when it touches. A gesture that finds no surface it can hold
(open sky, slick stone, something solid in the way, out of reach) does
nothing at all.

The player's x is a straight line in time, so every obstacle meets it at a
fixed moment: the whole game is WHEN to attach. Each beat below is one
attach (x: its second tap) between an obstacle that says "not yet" (on the
surface across, at the landing, or crossing the corridor) and one that says
"no later" (on the surface you run on, or the surface itself ending). Heights
are world heights (tiles above the ground line).
"""
import math
import os
import sys

from levelgen import (Level, T, DT, HALF, BASE_SPEED, ATTACH_REACH, GESTURE_GAP, attach_ticks, hop_rise,
                      hop_ticks, GROUND, CEILING, FLOOR, SKY)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..") + "/"
WORLD = "world_04"
# The standard corridor: the ceiling's underside C tiles above the ground.
C = 5.0
# Thickness of the floating islands (the sky shows past them).
SLAB = 1.5


# ------------------------------------------------------------------ physics --

def tune(lv, element, tap, ms, **options):
    """lv.tune with the level's severity applied to the target window."""
    return lv.tune(element, tap, round(ms * lv.ms_scale), **options)


def cross_dx(lv, corridor=None, gap=GESTURE_GAP):
    """How far (tiles) the run carries the player from a gesture's second tap
    to the touch across a corridor `corridor` tiles tall (default: the
    level's): the crossing starts from the hop the first tap began `gap`
    ticks earlier."""
    corridor = corridor or lv.corridor[1]
    return attach_ticks(corridor * T - 2 * HALF - hop_rise(gap)) * lv.tiles_per_tick()


def hop_dx(lv):
    """How far (tiles) a hop carries the player over flat ground."""
    return hop_ticks() * lv.tiles_per_tick()


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


def new_level(key, number, name, tagline, speed, corridor=C, ms_scale=1.0):
    """A World 04 level whose corridor is `corridor` tiles tall; every timing
    target of its beats is scaled by `ms_scale` (the level's severity)."""
    lv = Level(key, f"w04_l{number:02d}", name, tagline, speed)
    lv.ms_scale = ms_scale
    lv.latch_mode = True
    lv.corridor = (0.0, corridor)
    lv.kill_y = 7.0 * T
    lv.kill_top = -(corridor + 7.0) * T
    # The camera centres the corridor whichever surface is the floor.
    lv.corridor_height = corridor
    # A window counts only the shifts that still make the intended action
    # (a hop that stays a hop, an attach that attaches).
    lv.tune_any_kind = False
    return lv


def start_level(lv):
    for x in (7, 9, 11):
        lv.shard(x, 0.5)
    return Band(-12.0, 0.0, 0.0, False), Band(-12.0, 0.0, lv.corridor[1], True)


def finish_level(lv, floor, top, end, plan):
    lv._dbg_floor, lv._dbg_top = floor, top
    # The surface left behind at the last crossing is gone to the end too.
    plan.close(end + 16.0)
    floor.x1 = top.x1 = end + 16.0
    floor.emit(lv)
    top.emit(lv)
    lv.finish(end, lv.corridor[1] if plan.up else 0.0, up=plan.up)
    lv.done()
    lv.progress_checkpoints()
    return lv


class Plan:
    """Which surface exists where. The surface across from you exists only
    around the attaches that need it; after every attach the surface you
    left is cut away (from `late` tiles on) until the next attach back needs
    it, so every attach is necessary (skipping one runs off an edge), and
    every stretch between them has a single floor (a checkpoint can stand
    anywhere)."""

    def __init__(self, lv, floor, top, early=1.8, late=1.6):
        self.lv, self.floor, self.top = lv, floor, top
        self.up = False
        self.cut_from = None
        # How much surface a beat leaves around its attach (tiles): the
        # level's slack, before any obstacle narrows it.
        self.early, self.late = early, late

    def _other(self):
        return self.floor if self.up else self.top

    def here(self):
        """The anchor of the surface you run on."""
        return CEILING if self.up else GROUND

    def across(self):
        """The anchor of the surface across the corridor."""
        return GROUND if self.up else CEILING

    def close(self, x):
        """The surface across from you is there again from x on."""
        if self.cut_from is not None and x > self.cut_from + 0.5:
            self._other().cut(self.cut_from, x)
        self.cut_from = None

    def open(self, x):
        """The surface across from you is gone from x on."""
        self.close(x)
        self.cut_from = x

    def cross(self, x, early=None, late=None):
        """TAP TAP with its second tap at x. The surface across is there from
        `early` tiles before the crossing reaches it; the surface left behind
        ends `late` tiles after x. Returns the x where the crossing lands."""
        early = self.early if early is None else early
        late = self.late if late is None else late
        dx = cross_dx(self.lv)
        self.close(x + dx - early)
        self.lv.attach(x)
        self.up = not self.up
        self.open(x + late)
        return x + dx


def rest(x, length=8.0):
    """A calm stretch on one surface: a place for a checkpoint to stand."""
    return x + length


# ------------------------------------------------------------------- beats --
# One attach each, x = its second tap. Each returns the landing x. Obstacles
# that move are tuned against the attach to a target window (ms).

def roots_here(lv, plan, x, ms, period=1.6, gap=1.0, width=1.4, early=None):
    """ROOTS rise on the surface you run on, just past x: attach before they
    reach you (the surface goes on past them; a late attach meets them)."""
    r = lv.roots(x + gap, width, 2.1, anchor=plan.here(), period=period, hold=0.5)
    land = plan.cross(x, early=early, late=gap + width + 1.5)
    tune(lv, r, x, ms)
    return land


def flower_across(lv, plan, x, ms, period=1.6, ahead=0.4, early=3.2, late=None):
    """A FLOWER on the surface across closes over the landing: attach after
    it has passed you, while it is closed (it opens again behind you)."""
    fl = lv.flower(x + cross_dx(lv) - ahead, anchor=plan.across(), period=period, hold_ratio=0.55)
    land = plan.cross(x, early=early, late=late)
    tune(lv, fl, x, ms)
    return land


def branch_through(lv, plan, x, ms, period=1.8, amplitude=0.85, length=2.6):
    """A BRANCH swings from the surface across into the middle of the
    corridor where the crossing goes: attach while it is swung aside."""
    b = lv.branch(x + cross_dx(lv) * 0.55, length, anchor=plan.across(), amplitude=amplitude, period=period)
    land = plan.cross(x)
    tune(lv, b, x, ms)
    return land


def flock_through(lv, plan, x, ms, period=2.0, sweep=1.4):
    """A FLOCK sweeps up and down the middle of the corridor where the
    crossing goes: attach through the gap it leaves."""
    fk = lv.flock(x + cross_dx(lv) * 0.5, lv.corridor[1] * 0.5, sweep, period=period)
    land = plan.cross(x)
    tune(lv, fk, x, ms)
    return land


def rock_through(lv, plan, x, ms, period=1.5, ice=False):
    """Stones break loose from the sky side and fall across the corridor
    where the crossing goes: attach between them."""
    # The side it breaks from is fixed (the surface across at this attach),
    # not read from the run's surface log: the attach's timing then never
    # changes which way a later stone falls.
    rk = lv.rock(x + cross_dx(lv) * 0.6, anchor=plan.across(), period=period, ice=ice)
    land = plan.cross(x)
    tune(lv, rk, x, ms)
    return land


def waterfall_through(lv, plan, x, ms, period=1.6, hold_ratio=0.4, share=0.62):
    """A WATERFALL pours from the surface across, `share` of the way down:
    running under it is safe, crossing through it is not unless it is a
    thread."""
    wf = lv.waterfall(x + cross_dx(lv) * 0.5, width=70.0, length=lv.corridor[1] * T * share, deadly=True,
                      anchor=plan.across(), period=period, hold_ratio=hold_ratio)
    land = plan.cross(x)
    tune(lv, wf, x, ms)
    return land


def wave_here(lv, plan, x, ms, period=2.4, speed=380.0, run=5.0):
    """A tall WAVE forms on the surface you run on and rolls toward you (too
    tall to hop): attach before it arrives."""
    wv = lv.wave(x + 2.2 + run, run, height=100.0, anchor=plan.here(), period=period, speed=speed)
    land = plan.cross(x, late=run + 3.0)
    tune(lv, wv, x, ms)
    return land


def ink_across(lv, plan, x, ms, period=2.0, reach=3.0):
    """INK floods along the surface across, from just past the landing:
    attach after the flood has drawn back."""
    ik = lv.ink(x + cross_dx(lv) - 0.3, reach, anchor=plan.across(), period=period, hold_ratio=0.45)
    land = plan.cross(x, early=2.6)
    tune(lv, ik, x, ms)
    return land


def vines_across(lv, plan, x, ms, period=1.8):
    """VINES let themselves down from the surface across, over the first half
    of the crossing: attach while they are drawn up (short, they hang clear
    of the path and of the landing)."""
    vn = lv.vines(x + 0.25, 1.0, 0.6, 2.6, anchor=plan.across(), period=period)
    land = plan.cross(x)
    tune(lv, vn, x, ms)
    return land


def slick_wait(lv, plan, x, ms, slick=6.0, period=1.5):
    """The surface across is SLICK STONE (it cannot be held) until just
    before the landing, and roots rise on yours right after: a blind TAP TAP
    under the slick stone fails; the attach has to wait for the living
    garden, and go before the roots."""
    dx = cross_dx(lv)
    (plan.floor if plan.up else plan.top).slicken(x + dx - slick, x + dx - 0.9)
    r = lv.roots(x + 1.0, 1.4, 2.1, anchor=plan.here(), period=period, hold=0.5)
    land = plan.cross(x, early=slick, late=4.0)
    tune(lv, r, x, ms)
    return land


def boulder_through(lv, plan, x, ms, period=2.0):
    """A BOULDER swings on its rope from the ceiling across the crossing."""
    bd = lv.boulder(x + cross_dx(lv) * 0.5, 2.4, radius=36.0, amplitude=0.8, period=period)
    land = plan.cross(x)
    tune(lv, bd, x, ms)
    return land


def curtain_through(lv, plan, x, ms, period=1.8):
    """A painted CURTAIN rolls down from the surface across over the first
    half of the crossing: attach while it is rolled up (then it hangs clear
    of the path and of the landing)."""
    ct = lv.curtain(x + 0.1, 1.0, 0.5, 2.6, anchor=plan.across(), period=period, hold_ratio=0.45)
    land = plan.cross(x)
    tune(lv, ct, x, ms)
    return land


def glider_through(lv, plan, x, ms, period=2.2):
    """A LEAF rides the wind in loops through the middle of the corridor."""
    gl = lv.glider(x + cross_dx(lv) * 0.5, lv.corridor[1] * 0.5, loop=(110.0, 70.0), period=period)
    land = plan.cross(x)
    tune(lv, gl, x, ms)
    return land


def moving_ceiling(lv, plan, x, ms, period=2.2, ride=7.0):
    """(From the ground.) The ceiling ahead is a PANEL that rises out of reach
    and comes back down: a blind TAP TAP while it is up fails. Attach while
    it is down, ride it (it carries you), and TAP TAP back down to the ground
    before it ends, again while it is low enough. Returns the landing x of
    the way back down."""
    assert not plan.up, "the moving ceiling is reached from the ground"
    dx = cross_dx(lv)
    c = lv.corridor[1]
    x0 = x + dx - 2.5
    width = 2.5 + ride + 2.0
    plan.top.cut(x - 6.0, x0 + width + 4.0)   # No fixed ceiling over the ride.
    # Its underside moves between the corridor's ceiling (in reach) and 1.6
    # tiles higher (beyond the reach of an attach).
    mv = lv.garden_mover(x0, width, c + 0.5, (0.0, 1.6), period, thickness=0.5)
    plan.close(x - 6.0)
    lv.attach(x)
    plan.up = True
    plan.open(x + 1.6)                         # The ground ends: ride the panel.
    x2 = x + dx + ride
    plan.close(x2 + dx - 1.8)                  # The ground is back for the way down.
    lv.attach(x2)
    plan.up = False
    plan.open(x2 + 1.6)
    tune(lv, mv, [x, x2], ms, osc=True)
    return x2 + dx


# --------------------------------------------------------------- hop beats --
# One HOP each (x: its tap), over something small on the surface you run on.
# Between attaches the surface across is gone, so a TAP TAP there only hops
# (its attach finds nothing); hop_pinch guards it so that an attach dies.

HOP_MID = 12.5   # Ticks from a hop's tap to the middle of its flight.
COYOTE_TICKS = 4.8   # MovementConfig.coyote_time in ticks.


def _here(plan):
    """The band of the surface you run on."""
    return plan.top if plan.up else plan.floor


def hop_mid(lv, x):
    """Where (tiles) the hop tapped at x is highest."""
    return x + HOP_MID * lv.tiles_per_tick()


def hop_roots(lv, plan, x, ms, period=1.5, width=0.8, reach=1.0, hold=0.45):
    """Small ROOTS rise on the surface you run on: hop over them while they
    stand (a hop clears 1 tile; the attach roots are twice that)."""
    r = lv.roots(hop_mid(lv, x) - width * 0.5, width, reach, anchor=plan.here(), period=period, hold=hold)
    lv.hop(x)
    tune(lv, r, x, ms)
    return x + hop_dx(lv)


def hop_gap(lv, plan, x, ms):
    """A GAP in the surface you run on: hop across. Nothing moves: the gap's
    width is the timing, sized so the take-off window is about `ms` (the
    wider, the less time: the hop must leave late and land just past it)."""
    tpt = lv.tiles_per_tick()
    coyote = COYOTE_TICKS * tpt
    ms = round(ms * lv.ms_scale)
    width = hop_dx(lv) + 2 * HALF / T + coyote - (ms * 60.0 / 1000.0 - 1.0) * tpt
    mid = hop_mid(lv, x)
    _here(plan).cut(mid - width * 0.5, mid + width * 0.5)
    lv.hop(x)
    lv.notes.append(f"Gap x={mid - width * 0.5:.1f}: {width:.2f} tiles for {ms} ms")
    return x + hop_dx(lv)


def hop_ink(lv, plan, x, ms, reach=2.2, period=1.8, hold_ratio=0.5):
    """INK spills along the surface you run on, toward you: hop over the
    flow (thin: a hop clears it; spread wide: the hop must be on time)."""
    ik = lv.ink(hop_mid(lv, x) + reach * 0.5, reach, anchor=plan.here(), period=period, hold_ratio=hold_ratio)
    lv.hop(x)
    tune(lv, ik, x, ms)
    return x + hop_dx(lv)


def hop_wave(lv, plan, x, ms, height=44.0, period=2.0, speed=360.0, run=4.0):
    """A small WAVE rolls at you along the surface you run on: hop over its
    crest (it closes in fast: a short window)."""
    wv = lv.wave(hop_mid(lv, x) + run * 0.5, run, height=height, anchor=plan.here(), period=period, speed=speed)
    lv.hop(x)
    tune(lv, wv, x, ms)
    return x + hop_dx(lv)


def hop_pinch(lv, plan, x, ms, period=1.5, flower_period=1.3):
    """Both surfaces closed at once: small roots on yours, and a short
    stretch of surface across with a FLOWER closing over where an attach
    from this hop would land (later, the stretch has ended: an attach finds
    nothing). Only a hop gets through: a TAP TAP lands in the flower."""
    near = 3 * lv.tiles_per_tick() + cross_dx(lv, gap=3)   # the earliest landing of a TAP TAP
    plan.close(x - 1.5)
    plan.open(x + near + 0.9)
    fl = lv.flower(x + near + 0.35, anchor=plan.across(), period=flower_period, hold_ratio=0.85)
    r = lv.roots(hop_mid(lv, x) - 0.4, 0.8, 1.0, anchor=plan.here(), period=period, hold=0.45)
    lv.hop(x)
    tune(lv, r, x, ms)
    lv.guard_attach(fl, x, near + 1.5)
    return x + hop_dx(lv)


def hop_attach(lv, plan, x, ms, late_gap=12, period=1.5):
    """HOP, then ATTACH from the hop: small roots on your surface, and your
    surface ends right after them. The first tap hops the roots; the second,
    late in the hop (`late_gap` ticks on), carries it across. Returns the
    landing x."""
    hx = x - late_gap * lv.tiles_per_tick()
    r = lv.roots(hop_mid(lv, hx) - 0.4, 0.8, 1.0, anchor=plan.here(), period=period, hold=0.45)
    dx = cross_dx(lv, gap=late_gap)
    plan.close(x + dx - plan.early)
    lv.attach(x, gap=late_gap)
    plan.up = not plan.up
    plan.open(x + plan.late)
    tune(lv, r, x, ms)
    return x + dx


# ------------------------------------------------------------------ levels --

def on_ground(plan):
    assert not plan.up, "this beat starts from the ground"


def level_01():
    """BLUE BLOOM — hard. The two moves of the garden, one at a time and then
    mixed: a TAP hops small things on the surface you run on (gaps, small
    roots, thin ink, low waves), a TAP TAP takes you across when the way on
    is closed (tall roots, a tall wave, a flower closing, a branch, stones).
    Then both, back to back, with less room between them."""
    lv = new_level("level_01", 1, "Blue Bloom", "Hard", 1.18, corridor=5.2, ms_scale=0.58)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top, early=1.4, late=0.5)
    plan.open(13.0)

    lv.group("FirstHops")
    x = 16.0
    land = hop_gap(lv, plan, x, 300)
    x = land + 3.0
    land = hop_roots(lv, plan, x, 260, period=1.8)
    x = land + 3.0
    land = hop_gap(lv, plan, x, 270)
    lv.shards_along(14.0, land, 2.0)

    lv.group("FirstAttach")
    x = land + 4.0
    land = roots_here(lv, plan, x, 260, period=1.8, early=5.0)
    x = land + 3.5
    land = hop_roots(lv, plan, x, 250, period=1.7)
    x = land + 3.5
    land = flower_across(lv, plan, x, 250, period=1.8)
    x = land + 3.0
    land = hop_ink(lv, plan, x, 230, reach=2.0)
    x = land + 3.0
    land = wave_here(lv, plan, x, 230, period=2.4)
    lv.shards_along(x - 20.0, land + 3.0, 2.0)

    lv.group("Rest")
    x = rest(land, 13.0)   # (Long: the wave behind reaches into it.)

    lv.group("Garden")
    land = hop_gap(lv, plan, x, 250)
    x = land + 3.5
    land = ink_across(lv, plan, x, 220, period=2.0)
    x = land + 2.5
    land = hop_roots(lv, plan, x, 220, period=1.6)
    x = land + 3.0
    land = branch_through(lv, plan, x, 220, period=1.9)
    x = land + 3.0
    land = hop_wave(lv, plan, x, 220)
    x = land + 3.0
    land = rock_through(lv, plan, x, 210, period=1.7)
    x = land + 2.5
    land = hop_roots(lv, plan, x, 200, period=1.5)
    lv.shards_along(x - 30.0, land + 3.0, 2.0)

    lv.group("Rest2")
    x = rest(land, 9.0)

    lv.group("Pressure")
    land = roots_here(lv, plan, x, 200, period=1.6)
    x = land + 2.5
    land = hop_gap(lv, plan, x, 230)
    x = land + 2.5
    land = vines_across(lv, plan, x, 200, period=1.8)
    x = land + 2.5
    land = hop_ink(lv, plan, x, 190, reach=2.2)
    x = land + 2.5
    land = hop_pinch(lv, plan, x, 190)
    x = land + 2.5
    land = waterfall_through(lv, plan, x, 190, period=1.7)
    x = land + 2.5
    land = hop_roots(lv, plan, x, 180, period=1.5)
    x = land + 2.5
    land = flower_across(lv, plan, x, 180, period=1.7)
    lv.shards_along(x - 30.0, land + 3.0, 2.2)
    return finish_level(lv, floor, top, land + 9.0, plan)


def level_02():
    """FALLING GARDEN — very hard. Things fall across the way (stones,
    waterfalls, a swinging boulder, vines and curtains) while the surface
    you run on keeps breaking into gaps: hop the gaps, attach between the
    falls; the first hop-then-attach from the hop itself."""
    lv = new_level("level_02", 2, "Falling Garden", "Very hard", 1.24, corridor=5.3, ms_scale=0.78)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top, early=1.2, late=0.3)
    plan.open(13.0)

    lv.group("Stones")
    x = 17.0
    land = hop_gap(lv, plan, x, 220)
    x = land + 2.5
    land = rock_through(lv, plan, x, 200, period=1.5)
    x = land + 2.5
    land = hop_roots(lv, plan, x, 200, period=1.5)
    x = land + 2.5
    land = vines_across(lv, plan, x, 190, period=1.7)
    x = land + 2.5
    land = hop_ink(lv, plan, x, 190, reach=2.2)
    x = land + 2.5
    land = boulder_through(lv, plan, x, 190, period=1.9)
    x = land + 2.5
    land = hop_gap(lv, plan, x, 200)
    x = land + 2.5
    land = flower_across(lv, plan, x, 180, period=1.6)
    lv.shards_along(14.0, land + 3.0, 2.2)

    lv.group("Rest")
    x = rest(land, 9.0)

    lv.group("Falls")
    land = hop_wave(lv, plan, x, 180)
    x = land + 2.5
    land = waterfall_through(lv, plan, x, 180, period=1.6)
    x = land + 2.5
    land = hop_roots(lv, plan, x, 175, period=1.5)
    x = land + 2.5
    land = rock_through(lv, plan, x, 175, period=1.4)
    x = land + 2.5
    land = hop_gap(lv, plan, x, 190)
    x = land + 2.0
    land = roots_here(lv, plan, x, 170, period=1.5)
    x = land + 2.5
    land = hop_roots(lv, plan, x, 170, period=1.4)
    x = land + 2.5
    land = curtain_through(lv, plan, x, 170, period=1.7)
    x = land + 2.5
    land = hop_attach(lv, plan, x, 165)
    lv.shards_along(x - 38.0, land + 3.0, 2.2)

    lv.group("Rest2")
    x = rest(land, 9.0)

    lv.group("Downpour")
    land = hop_ink(lv, plan, x, 160, reach=2.3)
    x = land + 2.0
    land = waterfall_through(lv, plan, x, 160, period=1.5)
    x = land + 2.0
    land = hop_roots(lv, plan, x, 160, period=1.4)
    x = land + 2.0
    land = hop_gap(lv, plan, x, 180)
    x = land + 2.0
    land = rock_through(lv, plan, x, 155, period=1.3)
    x = land + 2.0
    land = hop_wave(lv, plan, x, 155)
    x = land + 2.0
    land = boulder_through(lv, plan, x, 150, period=1.8)
    x = land + 2.0
    land = hop_roots(lv, plan, x, 150, period=1.35)
    x = land + 2.0
    land = vines_across(lv, plan, x, 150, period=1.6)
    lv.shards_along(x - 36.0, land + 3.0, 2.2)
    return finish_level(lv, floor, top, land + 9.0, plan)


def level_03():
    """THE FLOODED SKY — extremely hard. Water and ink on both surfaces:
    waves to hop and waves to flee, ink to hop and ink to wait out, slick
    stone that cannot be held, a ceiling that rises out of reach; the
    question is always which move, and exactly when."""
    lv = new_level("level_03", 3, "The Flooded Sky", "Extremely hard", 1.30, corridor=5.4, ms_scale=0.70)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top, early=1.0, late=0.2)
    plan.open(13.0)

    lv.group("Tide")
    x = 17.0
    land = hop_wave(lv, plan, x, 170)
    x = land + 2.0
    land = wave_here(lv, plan, x, 165, period=2.2, speed=420.0)
    x = land + 2.0
    land = hop_ink(lv, plan, x, 165, reach=2.3)
    x = land + 2.0
    land = ink_across(lv, plan, x, 160, period=1.9)
    x = land + 2.0
    land = hop_gap(lv, plan, x, 170)
    x = land + 2.0
    land = waterfall_through(lv, plan, x, 160, period=1.5)
    x = land + 2.0
    land = hop_wave(lv, plan, x, 155)
    x = land + 2.0
    land = roots_here(lv, plan, x, 155, period=1.5)
    lv.shards_along(14.0, land + 3.0, 2.2)

    lv.group("SlickStone")
    x = land + 2.5
    land = hop_ink(lv, plan, x, 150, reach=2.4)
    x = land + 2.0
    land = slick_wait(lv, plan, x, 150, period=1.5)
    x = land + 2.0
    land = hop_roots(lv, plan, x, 150, period=1.4)
    x = land + 2.0
    land = vines_across(lv, plan, x, 150, period=1.6)

    lv.group("Rest")
    x = rest(land, 9.0)

    lv.group("RisingCeiling")
    on_ground(plan)
    land = moving_ceiling(lv, plan, x, 150, period=2.2)

    lv.group("Flood")
    x = land + 4.0
    land = hop_attach(lv, plan, x, 145)
    x = land + 2.0
    land = hop_wave(lv, plan, x, 145)
    x = land + 2.0
    land = ink_across(lv, plan, x, 140, period=1.8)
    x = land + 2.0
    land = hop_gap(lv, plan, x, 150)
    x = land + 2.0
    land = wave_here(lv, plan, x, 140, period=2.0, speed=440.0)
    x = land + 2.0
    land = hop_ink(lv, plan, x, 140, reach=2.4)
    x = land + 2.0
    land = waterfall_through(lv, plan, x, 135, period=1.4)
    lv.shards_along(x - 34.0, land + 3.0, 2.2)

    lv.group("Rest2")
    x = rest(land, 9.0)

    lv.group("Deluge")
    w0 = x - 2.0
    land = hop_pinch(lv, plan, x, 135)
    x = land + 2.0
    land = glider_through(lv, plan, x, 135, period=1.9)
    x = land + 2.0
    land = hop_gap(lv, plan, x, 140)
    x = land + 2.0
    land = flock_through(lv, plan, x, 130, period=1.9)
    x = land + 2.0
    land = hop_wave(lv, plan, x, 130)
    x = land + 2.0
    land = slick_wait(lv, plan, x, 130, period=1.4)
    x = land + 2.0
    land = hop_ink(lv, plan, x, 125, reach=2.5)
    x = land + 2.0
    land = roots_here(lv, plan, x, 125, period=1.4)
    x = land + 2.0
    land = hop_roots(lv, plan, x, 125, period=1.35)
    lv.weather(w0, land + 4.0, "leaves", density=0.7)
    lv.shards_along(x - 40.0, land + 3.0, 2.2)
    return finish_level(lv, floor, top, land + 9.0, plan)


def level_04():
    """PAINTED STORM — brutal. Every attach turns the painting over, and
    every stretch mixes the two moves with less room between them: hop,
    attach, hop on the ceiling, attach back, a hop that turns into an attach,
    both surfaces closed at once (only a hop gets through)."""
    lv = new_level("level_04", 4, "Painted Storm", "Brutal", 1.35, corridor=5.4, ms_scale=0.73)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top, early=0.9, late=0.1)
    plan.open(13.0)

    lv.group("Canvas")
    w0 = 14.0
    x = 17.0
    land = hop_roots(lv, plan, x, 150, period=1.4)
    x = land + 2.0
    land = curtain_through(lv, plan, x, 150, period=1.7)
    x = land + 2.0
    land = hop_gap(lv, plan, x, 130)
    x = land + 2.0
    land = branch_through(lv, plan, x, 145, period=1.7)
    x = land + 2.0
    land = hop_ink(lv, plan, x, 145, reach=2.4)
    x = land + 2.0
    land = glider_through(lv, plan, x, 145, period=2.0)
    x = land + 2.0
    land = hop_roots(lv, plan, x, 140, period=1.35)
    x = land + 2.0
    land = flower_across(lv, plan, x, 140, period=1.5)
    x = land + 2.0
    land = hop_wave(lv, plan, x, 140)
    x = land + 2.0
    on_ground(plan)
    land = moving_ceiling(lv, plan, x, 135, period=2.0)
    lv.weather(w0, land + 3.0, "leaves", density=0.6)
    lv.shards_along(14.0, land + 3.0, 2.2)

    lv.group("Rest")
    x = rest(land, 9.0)

    lv.group("Storm")
    land = hop_roots(lv, plan, x, 135, period=1.35)
    x = land + 2.0
    land = hop_attach(lv, plan, x, 135)
    x = land + 2.0
    land = hop_ink(lv, plan, x, 130, reach=2.5)
    x = land + 2.0
    land = boulder_through(lv, plan, x, 130, period=1.8)
    x = land + 2.0
    land = hop_pinch(lv, plan, x, 130)
    x = land + 2.0
    land = curtain_through(lv, plan, x, 130, period=1.6)
    x = land + 2.0
    land = hop_gap(lv, plan, x, 130)
    x = land + 2.0
    land = ink_across(lv, plan, x, 125, period=1.6)
    x = land + 2.0
    land = hop_wave(lv, plan, x, 120)
    lv.shards_along(x - 40.0, land + 3.0, 2.2)

    lv.group("Rest2")
    x = rest(land, 9.0)

    lv.group("Tempest")
    w0 = x - 2.0
    land = slick_wait(lv, plan, x, 120, period=1.3)
    x = land + 1.8
    land = hop_gap(lv, plan, x, 125)
    x = land + 1.8
    land = glider_through(lv, plan, x, 120, period=1.8)
    x = land + 1.8
    land = hop_roots(lv, plan, x, 115, period=1.3)
    x = land + 1.8
    land = wave_here(lv, plan, x, 115, period=1.8, speed=460.0)
    x = land + 1.8
    land = hop_ink(lv, plan, x, 115, reach=2.5)
    x = land + 1.8
    land = curtain_through(lv, plan, x, 115, period=1.5)
    x = land + 1.8
    land = hop_pinch(lv, plan, x, 110)
    x = land + 1.8
    land = flock_through(lv, plan, x, 110, period=1.7)
    x = land + 1.8
    land = hop_roots(lv, plan, x, 110, period=1.3)
    x = land + 1.8
    land = branch_through(lv, plan, x, 110, period=1.5)
    lv.weather(w0, land + 3.0, "leaves", density=0.8)
    lv.shards_along(x - 46.0, land + 3.0, 2.2)
    return finish_level(lv, floor, top, land + 9.0, plan)


def level_05():
    """THE INVERTED GARDEN — brutal but fair. No new move: the hop and the
    attach, faster, closer together, with the least room of the world; a
    snowfall where ice falls among the flakes; the FINAL GAUNTLET, sixteen
    moves in a row, the tightest windows of the garden."""
    lv = new_level("level_05", 5, "The Inverted Garden", "Brutal but fair", 1.40, corridor=5.45, ms_scale=0.86)
    floor, top = start_level(lv)
    plan = Plan(lv, floor, top, early=0.8, late=0.0)
    plan.open(13.0)

    lv.group("Mastery")
    x = 17.0
    land = hop_roots(lv, plan, x, 130, period=1.35)
    x = land + 1.8
    land = roots_here(lv, plan, x, 130, period=1.4)
    x = land + 1.8
    land = hop_ink(lv, plan, x, 125, reach=2.5)
    x = land + 1.8
    land = branch_through(lv, plan, x, 125, period=1.6)
    x = land + 1.8
    land = hop_gap(lv, plan, x, 115)
    x = land + 1.8
    land = ink_across(lv, plan, x, 125, period=1.6)
    x = land + 1.8
    land = hop_wave(lv, plan, x, 120)
    x = land + 1.8
    land = flock_through(lv, plan, x, 120, period=1.7)
    x = land + 1.8
    land = hop_pinch(lv, plan, x, 120)
    x = land + 1.8
    land = vines_across(lv, plan, x, 120, period=1.5)
    lv.shards_along(14.0, land + 3.0, 2.2)

    lv.group("Snowfall")
    x = land + 1.8
    s0 = x - 3.0
    land = hop_roots(lv, plan, x, 115, period=1.3)
    x = land + 1.8
    land = rock_through(lv, plan, x, 115, period=1.3, ice=True)
    x = land + 1.8
    land = hop_gap(lv, plan, x, 110)
    lv.weather(s0, land + 4.0, "snow", density=0.8)
    lv.shards_along(s0, land + 3.0, 2.2)

    lv.group("Rest")
    x = rest(land, 9.0)

    lv.group("Blizzard")
    s0 = x - 2.0
    land = flower_across(lv, plan, x, 115, period=1.4)
    x = land + 1.8
    land = hop_ink(lv, plan, x, 110, reach=2.6)
    x = land + 1.8
    land = rock_through(lv, plan, x, 110, period=1.25, ice=True)
    x = land + 1.8
    land = hop_roots(lv, plan, x, 110, period=1.3)
    x = land + 1.8
    land = boulder_through(lv, plan, x, 110, period=1.7)
    lv.weather(s0, land + 4.0, "snow", density=0.8)
    lv.shards_along(s0, land + 3.0, 2.2)

    lv.group("Rapids")
    x = land + 1.8
    land = hop_wave(lv, plan, x, 105)
    x = land + 1.8
    land = wave_here(lv, plan, x, 105, period=1.8, speed=480.0)
    x = land + 1.8
    land = hop_gap(lv, plan, x, 100)
    x = land + 1.8
    land = waterfall_through(lv, plan, x, 105, period=1.3)
    x = land + 1.8
    land = hop_ink(lv, plan, x, 100, reach=2.6)
    x = land + 1.8
    land = slick_wait(lv, plan, x, 100, period=1.3)
    x = land + 1.8
    land = hop_roots(lv, plan, x, 100, period=1.25)
    lv.shards_along(x - 30.0, land + 3.0, 2.2)

    lv.group("Rest2")
    x = rest(land, 9.0)

    lv.group("FinalGauntlet")
    land = hop_attach(lv, plan, x, 95)
    x = land + 1.6
    land = hop_roots(lv, plan, x, 95, period=1.25)
    x = land + 1.6
    land = curtain_through(lv, plan, x, 90, period=1.5)
    x = land + 1.6
    land = hop_gap(lv, plan, x, 85)
    x = land + 1.6
    land = flock_through(lv, plan, x, 90, period=1.6)
    x = land + 1.6
    land = hop_wave(lv, plan, x, 90)
    x = land + 1.6
    land = roots_here(lv, plan, x, 88, period=1.25)
    x = land + 1.6
    land = hop_pinch(lv, plan, x, 88)
    x = land + 1.6
    land = branch_through(lv, plan, x, 85, period=1.5)
    x = land + 1.6
    land = hop_ink(lv, plan, x, 85, reach=2.6)
    x = land + 1.6
    land = ink_across(lv, plan, x, 83, period=1.5)
    x = land + 1.6
    land = hop_roots(lv, plan, x, 83, period=1.25)
    x = land + 1.6
    land = wave_here(lv, plan, x, 83, period=1.7, speed=500.0)
    x = land + 1.6
    land = hop_gap(lv, plan, x, 90)
    x = land + 1.6
    land = glider_through(lv, plan, x, 83, period=1.6)
    x = land + 1.6
    land = hop_wave(lv, plan, x, 83)
    lv.shards_along(x - 60.0, land + 3.0, 2.2)
    return finish_level(lv, floor, top, land + 8.0, plan)


LEVELS = [level_01, level_02, level_03, level_04, level_05]


def read_routes():
    """{number: (name, taps)}: every tap of each level's intended solution."""
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
    """The routes as the engine's finger plays them: every tap (a hop is one,
    a TAP TAP surface attach two)."""
    lines = ["class_name World04Routes",
             "## Generated by tools/levelgen/world_04.py: the intended solution of each",
             "## World 04 level, as the player-centre x (tiles) of every tap. A lone tap",
             "## is a hop; two taps close together are one TAP TAP surface attach.", ""]
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
        acts = lv.actions()
        line = "".join("A" if k == "attach" else "H" for k, _ in acts)
        hops = line.count("H")
        print(f"  actions: {line}  hops: {hops}, attaches: {len(acts) - hops}")
        lv.write(ROOT, f"levels/{WORLD}/level_{i:02d}.tscn", f"levels/{WORLD}/level_{i:02d}.tres")

        def save(lv=lv, i=i):
            routes = read_routes()
            routes[i] = (lv.name, sorted(lv.route))
            write_routes(routes)
            write_world(len(LEVELS))
        _locked(save)


if __name__ == "__main__":
    main()
