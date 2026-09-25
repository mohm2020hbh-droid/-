#!/usr/bin/env python3
"""World 01 — THE RED VOID: five levels, generated.

    python3 tools/levelgen/world_01.py            # build, check, write all levels
    python3 tools/levelgen/world_01.py 3 --windows # one level, with tap windows

Writes levels/world_01/level_0N.tscn + .tres and tests/support/world_01_routes.gd.
Every obstacle's phase is fitted against the intended route (the player's
taps), with the lazy alternative (skipping the tap) required to die.
"""
import os
import sys

from levelgen import Level, T

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..") + "/"


def level_01():
    """AWAKENING — hard tutorial: gates, a slow windmill, gaps, a moving
    platform, the first double jump. Single ideas first, pairs in the
    middle, and a last fifth that asks for all of it at once."""
    lv = Level("level_01", "w01_l01", "Awakening", "Hard tutorial", 1.0)

    lv.group("Runway")
    lv.block(-12, 26, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)
    # A barrier with its window at the floor: the opening is the way through.
    lv.gate(19, [(1.4, 2.8)])
    lv.shard(19, 0.5)

    lv.group("FirstGap")
    lv.tap(24.4)
    lv.block(29.5, 51, 0)
    lv.shards_along(25.4, 28.4, 1.5)

    lv.group("RaisedWindow")
    lv.tap(33.2)
    lv.gate(36.2, [(2.45, 2.3)])
    lv.shard(36.2, 2.2)

    lv.group("PulseGate")
    lv.tap(40.9)
    g = lv.gate(43.8, [(1.35, 2.7), (2.7, 2.4)], hold=0.8, move=0.3)
    lv.fit_phase(g, lazy=[lv.without(40.9)])
    lv.shard(43.8, 2.2)

    lv.group("SecondGap")
    lv.tap(49.6)
    lv.block(55, 80, 0)
    lv.shards_along(50.6, 53.6, 1.5)

    lv.group("Windmill")
    lv.tap(60.2)
    a = lv.arm(63.2, 0.5, 1.6, speed=1.5, arms=2)
    lv.fit_phase(a, lazy=[lv.without(60.2)])
    lv.shard(63.2, 3.0)

    lv.checkpoint(68)

    # ------------------------------------------------ pairs: 68 .. 128 ---
    lv.group("Elevator")
    lv.block(80, 84, 0)
    lv.tap(82.4)
    period = 2.4
    lv.mover(87.5, 5, -0.5, (0, 2.25), period, lv.osc_phase(88.2, period, 0.02))
    lv.tap(91.6)
    lv.block(96.5, 112, 1.0)
    lv.shards_along(83.5, 93.5, 2.0)

    lv.group("GateAfterLanding")
    lv.tap(98.6)
    g = lv.gate(101.5, [(2.35, 2.7), (3.7, 2.4)], hold=0.7, move=0.25)
    lv.fit_phase(g, lazy=[lv.without(98.6)])

    lv.group("SecondWindmill")
    lv.tap(104.8)
    a = lv.arm(107.8, 1.5, 1.6, speed=-2.0, arms=2)
    lv.fit_phase(a, lazy=[lv.without(104.8)])

    lv.group("WindowOverGap")
    lv.block(112, 118, 0)
    lv.tap(116.6)
    lv.block(122, 145, 0)
    lv.gate(120.4, [(2.65, 2.3)])
    lv.shards_along(117.5, 121.5, 1.0)

    lv.checkpoint(128)

    # ------------------------------------------ double jump: 128 .. 182 ---
    lv.group("DoubleJumpWall")
    lv.tap(142.4)
    lv.dj(144.5)
    lv.block(147.5, 160, 3.5)
    lv.shards_along(143, 147, 1.0)

    lv.group("DoubleJumpGap")
    lv.tap(158.6)
    lv.dj(162.9)
    lv.block(168.5, 174, 3.5)
    lv.block(174, 209.5, 0)
    lv.shards_along(160, 167, 1.4)

    lv.checkpoint(182)

    # ----------------------------------------- the last fifth: 182 .. 232 ---
    lv.group("HighWindow")
    lv.tap(193.4)
    lv.dj(195.7)
    lv.gate(198.5, [(4.2, 2.4)])
    lv.shards_along(198.5, 198.5)  # Through the middle of the gate, on the final path.

    lv.group("ThirdWindmill")
    lv.tap(202.9)
    a = lv.arm(205.9, 0.5, 1.7, speed=1.9, arms=2)
    lv.fit_phase(a, lazy=[lv.without(202.9)])

    lv.group("PulseOverGap")
    lv.tap(208.4)
    lv.block(213.5, 219, 0)
    g = lv.gate(212.6, [(2.6, 2.2), (1.2, 2.4)], hold=0.6, move=0.25)
    lv.fit_phase(g, lazy=[lv.without(208.4)])

    lv.group("FinalDoubleGap")
    lv.tap(217.6)
    lv.dj(221.4)
    g = lv.gate(223.2, [(3.9, 2.6), (2.2, 2.6)], hold=0.6, move=0.25)
    lv.fit_phase(g, lazy=[lv.without(221.4)])
    lv.block(226.5, 250, 0)

    lv.finish(238)
    lv.done()
    return lv


def piston(lv, x, width=1.0, height=1.6, period=1.5, closed=0.35):
    """A block that rams up out of the floor and sinks back (a crush block from below)."""
    return lv.crusher(x, width, -height - 0.05, height, period, height=height + 0.05, warning=0.3,
                      slam=0.08, closed=closed, ret=0.35)


def level_02():
    """PRESSURE — very hard: crush blocks, faster arms, narrow platforms and
    short chained gaps. Obstacles come back to back: the level never waits."""
    lv = Level("level_02", "w01_l02", "Pressure", "Very hard", 1.08)

    # ------------------------------------------------------ crush: 0 .. 95 ---
    lv.group("Runway")
    lv.block(-12, 21, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    lv.group("GapThenPiston")
    lv.block(25, 38, 0)
    lv.tap(19.6, 27.6)
    lv.tune(piston(lv, 31.0, height=2.0), 27.6, 180)
    lv.shards_along(21, 24, 1.5)

    lv.group("CrusherOverGap")
    lv.block(42, 60, 0)
    lv.tap(36.5)
    lv.tune(lv.crusher(38.8, 2.4, 4.4, -2.8, 1.8, height=3.0), 36.5, 180)

    lv.group("TwinPistons")
    lv.tap(44.6, 51.0)
    lv.tune(piston(lv, 47.8, height=1.9), 44.6, 200)
    lv.tune(piston(lv, 54.2, width=1.5, height=2.1), 51.0, 170)

    lv.group("NarrowSteps")
    lv.block(65.1, 66.3, 0.5)
    lv.block(71.7, 72.9, 1.0)
    lv.tap(59.1, 65.9, 72.5)
    lv.shards_along(60, 77, 2.2)

    lv.group("SlamOnLanding")
    lv.block(78, 82.5, 0)
    lv.block(87.2, 88.8, 0.5)
    lv.block(94.5, 124, 0)
    lv.tap(81.2, 88.4)
    slam = lv.crusher(86.9, 2.2, 4.4, -3.9, 1.6, height=3.0, closed=0.25, ret=0.45)
    lv.tune(slam, 88.4, 170)
    lv.shards_along(82, 93, 2.0)

    lv.checkpoint(100)

    # ----------------------------------------------------- arms: 100 .. 166 ---
    lv.group("FastWindmill")
    lv.tap(113.4)
    lv.tune(lv.arm(116.6, 0.5, 1.8, speed=2.6), 113.4, 200)

    lv.group("ArmOverGap")
    lv.block(128, 146.6, 0)
    lv.tap(122.3)
    lv.tune(lv.arm(126.0, 4.3, 2.6, speed=-1.8), 122.3, 180)

    lv.group("CounterWindmills")
    lv.tap(130.8, 137.6)
    lv.tune(lv.arm(134.0, 0.5, 1.8, speed=2.4), 130.8, 200)
    lv.tune(lv.arm(140.8, 0.5, 1.8, speed=-2.4), 137.6, 180)

    lv.group("WindmillBetweenLedges")
    lv.block(151.9, 153.5, 0.5)
    lv.block(159.2, 184.2, 0)
    lv.tap(146.3, 153.1)
    lv.tune(lv.arm(156.6, -0.6, 2.4, speed=2.2), 153.1, 170)
    lv.shards_along(146, 158, 2.2)

    lv.checkpoint(166)

    # ------------------------------------------------ chains: 166 .. 240 ---
    lv.group("PistonThenDoubleJump")
    lv.tap(179.4)
    lv.dj(185.3)
    lv.tune(piston(lv, 182.0, height=1.8), 179.4, 200)
    ledge = lv.land_x(179.4, 2.0, air=(185.3 - 179.4) / lv.tiles_per_second()) - 0.9
    lv.block(ledge, ledge + 1.4, 2.0)
    lv.tune(lv.crusher(186.4, 2.2, 5.0, -2.1, 1.6, height=3.0), 185.3, 160)
    lv.tap(ledge + 1.0)
    down = lv.land_x(ledge + 1.0, -2.0)
    lv.tap(down + 0.6)
    x0 = down + 7.6
    lv.block(down - 0.8, x0 + 1.0, 0)
    lv.tune(piston(lv, down + 3.8, height=2.0, period=1.3, closed=0.3), down + 0.6, 170)
    lv.shards_along(180, ledge + 1.0, 1.8)

    lv.group("CrusherTunnel")
    lv.tap(x0)
    plats = []
    for i in range(3):
        px = lv.land_x(x0, 0) - 0.4 if i == 0 else lv.land_x(plats[-1][1], 0) - 0.4
        plats.append((px, px + 1.1))
        lv.block(px, px + 1.4, 0)
        lv.tap(px + 1.1)
    last = lv.land_x(plats[-1][1], 0)
    for px, tap in plats:
        lv.tune(lv.crusher(px - 0.4, 2.2, 4.2, -4.2, 1.5, height=3.0), tap, 170)
    cp = last + 5.0
    lv.block(last - 1.0, cp + 14.0, 0)
    lv.checkpoint(cp)

    # --------------------------------------- the last fifth: pressure ---
    lv.group("PistonRush")
    a = cp + 14.5
    lv.tap(a - 3.2, a + 3.3)
    lv.tune(piston(lv, a, height=2.1, period=1.2, closed=0.25), a - 3.2, 160)
    lv.tune(piston(lv, a + 6.5, height=2.1, period=1.2, closed=0.25), a + 3.3, 150)
    edge = a + 12.0
    lv.block(cp + 14.0, edge, 0)

    lv.group("GapCrusherDoubleJump")
    t1 = edge - 1.2
    t2 = t1 + 5.4
    lv.tap(t1)
    lv.dj(t2)
    high = lv.land_x(t1, 2.5, air=(t2 - t1) / lv.tiles_per_second()) - 0.8
    lv.block(high, high + 1.5, 2.5)
    lv.tune(lv.crusher(t1 + 4.2, 2.4, 5.2, -2.4, 1.5, height=3.0), t2, 150)
    lv.shards_along(t1 + 1, high + 1, 1.6)

    lv.group("DropIntoWindmill")
    t3 = high + 1.1
    lv.tap(t3)
    land = lv.land_x(t3, -2.5)
    lv.block(land - 1.0, land + 25, 0)
    t4 = land + 0.8
    lv.tap(t4)
    lv.tune(lv.arm(t4 + 3.3, 0.5, 1.8, speed=2.3), t4, 160)

    lv.finish(land + 16)
    lv.done()
    return lv


def level_03():
    """FRACTURE — extremely hard: collapsing paths, prism sweeps, moving and
    shifting floors, climbs and drops. Everything that moves shows its track;
    everything that falls cracks first."""
    lv = Level("level_03", "w01_l03", "Fracture", "Extremely hard", 1.13)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    lv.block(-12, 22, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    lv.group("CrumblingBridge")
    # Falls away right behind the player, and the field and the beam on it
    # still have to be read: there is no stopping to think.
    lv.collapsing(22, 18, 0.0, lv.clock(22.4) + 0.25, 1.0 / tps, tile_w=1.0, thickness=0.5)
    lv.block(40, 44, 0)
    lv.tap(26.5)
    lv.tune(lv.field(29.0, 1.0, 0.0, 1.8, 1.1, 0.6), 26.5, 170)
    lv.tap(34.0)
    lv.tune(lv.prism(37.0, 0.3, 2.0, 2.4, 1.5), 34.0, 170, osc=True)
    lv.shards_along(24, 38, 3.5)

    lv.group("CollapseTowardYou")
    # A bridge crumbling from the far end: jump before the edge reaches you,
    # far enough to clear what has already fallen.
    lv.collapsing(44, 14, 0.0, lv.clock(51.0), -0.055, tile_w=1.0, thickness=0.5)
    lv.block(58, 64.5, 0)
    lv.tap(52.2)

    lv.group("PrismOverGap")
    lv.block(69.5, 92, 0)
    lv.tap(63.4)
    lv.tune(lv.prism(67.0, 0.4, 4.0, 3.0, 1.8), 63.4, 170, osc=True)
    lv.shards_along(64, 69, 1.5)
    lv.tap(71.0)
    lv.tune(lv.field(73.2, 1.0, 0.0, 1.8, 1.0, 0.6), 71.0, 160)

    lv.checkpoint(78)

    # ------------------------------------------ moving floors: 78 .. 172 ---
    lv.group("Shuttle")
    lv.block(105.5, 112, 0)
    lv.tap(90.9)
    shuttle = lv.mover(95.5, 1.6, 0.0, (4.5, 0.0), 1.7)
    lv.tap(99.4)
    lv.tune(shuttle, [90.9, 99.4], 170, osc=True)
    # A low beam across the far end of the shuttle's run.
    lv.tune(lv.prism(102.2, 0.6, 1.6, 2.2, 1.6), 99.4, 170, osc=True)
    lv.shards_along(92, 104, 3.0)

    lv.group("ElevatorUp")
    lv.block(122.5, 127.5, 3.5)
    lv.tap(110.6)
    lift = lv.mover(116.3, 1.8, -1.0, (0.0, 3.5), 1.9)
    lv.tap(118.4)
    lv.tune(lift, [110.6, 118.4], 170, osc=True)
    # An energy field between the lift and the high tier: leave on its beat.
    lv.tune(lv.field(119.6, 1.0, 1.2, 4.2, 1.3, 0.76), 118.4, 160)
    lv.shards_along(111, 122, 3.0)

    lv.group("ShiftingHeights")
    # Three platforms that hold high or low and snap between (flashing first).
    taps = [126.3]
    xs = []
    for i in range(3):
        px = lv.land_x(taps[-1], 0.0) - 1.2
        xs.append(px)
        taps.append(px + 1.5)
    lv.block(lv.land_x(taps[-1], 0.0) - 1.0, 160, 3.5)
    lv.tap(*taps)
    lv.window_gate(taps[-1] + 3.0, 0.4)
    for i, px in enumerate(xs):
        plat = lv.mover(px + 0.3, 1.6, 2.8, (0.0, 2.0), 1.3, wave="steps", hold_ratio=0.5)
        lv.tune(plat, [taps[i], taps[i + 1]], 160, osc=True)
    for i in range(2):
        # Energy between the shifting platforms: hop through on its beat.
        gap = (xs[i] + 1.9 + xs[i + 1] + 0.3) / 2.0
        lv.tune(lv.field(gap - 0.5, 1.0, 3.2, 3.5, 1.2, 0.7), taps[i + 1], 170)
    lv.shards_along(127, 158, 3.5)

    lv.group("Drop")
    lv.tap(154.5)
    lv.tune(lv.field(157.0, 1.0, 3.5, 1.8, 1.0, 0.6), 154.5, 160)
    lv.block(161.5, 186, 0)
    lv.checkpoint(172)

    # ------------- moving floor > jump > prism > double jump > narrow > collapse ---
    lv.group("ShuttlePrismDoubleJump")
    a = 185.0
    lv.tap(a)
    shuttle = lv.mover(189.6, 2.4, 0.0, (3.0, 0.0), 1.8)
    b = 192.4
    lv.tap(b)
    lv.tune(shuttle, [a, b], 170, osc=True)
    b = sorted(lv.route)[-1]
    c = b + 4.3
    lv.dj(c)
    ledge = lv.land_x(b, 3.0, air=(c - b) / tps) - 0.7
    lv.block(ledge, ledge + 1.4, 3.0)
    lv.tune(lv.prism(b + 2.6, 1.2, 2.6, 2.6, 1.6), c, 150, osc=True)
    lv.shards_along(a + 1, ledge + 1, 3.0)

    lv.group("ChasingCollapse")
    path_x = ledge + 1.4
    d = path_x + 4.0
    length = 10
    lv.collapsing(path_x, length, 3.0, lv.clock(path_x) + 0.35, 1.0 / (1.25 * tps))
    lv.tap(d)
    lv.tune(lv.prism(d + 3.2, 3.4, 2.2, 1.8, 1.4), d, 170, osc=True)
    e = d + 4.9
    lv.dj(e)  # Over the prism and still airborne: the double jump carries you across.
    land = lv.land_x(d, -2.0, air=(e - d) / tps)
    lv.window_gate(e + 3.6, 0.35)
    cp = land + 5.0
    lv.block(land - 1.5, cp + 16.0, 1.0)
    lv.shards_along(path_x + 1, land, 3.0)
    lv.checkpoint(cp, 1.0)

    # ------------------------------------------- the last fifth: fracture ---
    lv.group("FinalCollapseTowardYou")
    b0 = cp + 16.0
    lv.collapsing(b0, 12, 1.0, lv.clock(b0 + 6.0), -0.05)
    f1 = b0 + 7.4
    lv.tap(f1)

    lv.group("FinalShiftingStep")
    px = lv.land_x(f1, 0.0) - 1.1
    step = lv.mover(px + 0.3, 1.4, 0.6, (0.0, 1.6), 1.2, wave="steps", hold_ratio=0.5)
    f2 = px + 1.5
    lv.tap(f2)
    lv.tune(step, [f1, f2], 150, osc=True)

    lv.group("FinalPrismDoubleJump")
    f3 = f2 + 4.6
    lv.dj(f3)
    ledge = lv.land_x(f2, 2.5, air=(f3 - f2) / tps) - 0.7
    lv.block(ledge, ledge + 1.3, 3.5)
    lv.tune(lv.prism(f2 + 3.4, 0.3, 2.4, 2.8, 1.3), f3, 140, osc=True)
    lv.window_gate(f3 + 2.0, 0.3)  # On the rising arc: it times the double jump itself.
    lv.shards_along(f1, ledge + 1, 3.0)

    lv.group("FinalChase")
    f4 = ledge + 1.0
    lv.tap(f4)
    lv.tune(lv.timed_gate(f4 + 3.6, 0.35, hold=0.35, move=0.14), f4, 120)
    chase = lv.land_x(f4, -2.0) - 0.8
    lv.collapsing(chase, 9, 1.5, lv.clock(chase) + 0.25, 1.0 / (1.3 * tps))
    f5 = chase + 3.2
    lv.tap(f5)
    lv.tune(lv.prism(f5 + 3.3, 1.9, 2.0, 1.8, 1.2), f5, 140, osc=True)
    f6 = chase + 8.3
    lv.dj(f6)
    end = lv.land_x(f5, -1.5, air=(f6 - f5) / tps)
    lv.window_gate(f6 + 2.4, 0.35)
    lv.block(end - 1.5, end + 20, 0)
    lv.shards_along(f4, end, 3.0)

    lv.finish(end + 12)
    lv.done()
    return lv


def level_04():
    """SEQUENCE — extremely hard: runs of gates at different heights that only
    one arc threads, rotating systems in pairs, pulse gates with moving
    openings and long aerial chains. Short windows, no visual noise."""
    lv = Level("level_04", "w01_l04", "Sequence", "Extremely hard", 1.17)
    tps = lv.tiles_per_second()
    from levelgen import Osc

    lv.group("Runway")
    lv.block(-12, 50, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    lv.group("ThreadTheArc")
    # Three windows on one jump: rising, apex, falling.
    lv.tap(21.0)
    for x in (22.4, 24.4, 26.6):
        lv.window_gate(x, 0.6)
    lv.shards_along(22, 27, 1.1)

    lv.group("LowHighLow")
    # Under a low window, then jump + double jump through a high one, then down
    # through a low one again.
    lv.window_gate(33.0, 0.5)
    lv.tap(34.2)
    lv.dj(36.6)
    lv.window_gate(40.0, 0.55)
    lv.window_gate(43.4, 0.5)
    lv.shards_along(35, 44, 1.5)

    lv.group("GateDoubleGateLanding")
    # Jump > window > double jump > window > land at once on a ledge.
    lv.tap(48.8)
    lv.window_gate(50.6, 0.35)
    lv.dj(51.9)
    ledge = lv.land_x(48.8, 2.0, air=(51.9 - 48.8) / tps) - 0.6
    lv.block(ledge, ledge + 1.4, 2.0)
    lv.window_gate(ledge - 1.6, 0.3)
    lv.tap(ledge + 1.1)
    floor = lv.land_x(ledge + 1.1, -2.0)
    lv.block(floor - 1.0, floor + 24, 0)
    lv.shards_along(49, ledge + 1, 1.5)
    cp = floor + 6.0
    lv.checkpoint(cp)

    # ----------------------------------------------------- rotating systems ---
    lv.group("CounterWindmills")
    r1 = cp + 16.0
    lv.tap(r1, r1 + 6.9)
    lv.tune(lv.arm(r1 + 3.2, 0.5, 1.6, speed=2.8), r1, 170)
    lv.tune(lv.arm(r1 + 10.1, 0.5, 1.6, speed=-2.8), r1 + 6.9, 160)

    lv.group("SlidingPanel")
    # A wall panel that rises to let you run under it, then drops to the
    # floor where only a jump at the right moment clears it.
    wp = r1 + 14.4
    lv.tap(wp)
    lv.tune(lv.panel(wp + 3.0, 0.75, 0.0, 1.7, osc=Osc((0.0, -2.9 * 64), 1.4, wave="steps", hold_ratio=0.5)),
            wp, 160, osc=True)

    lv.group("RotorOverGap")
    g0 = r1 + 23.6
    lv.block(floor + 24, g0, 0) if g0 > floor + 24 else None
    g1 = g0 + 4.6
    lv.block(g1, g1 + 7.0, 0)
    r2 = g0 - 1.3
    lv.tap(r2)
    rotor = lv.rotor(g0 + 2.3, 0.4, radius=40.0, points=3, spin=4.0,
                     osc=Osc((0.0, -2.8 * 64), 1.5))
    lv.tune(rotor, r2, 160, osc=True)

    lv.group("ArmOverLedge")
    r3 = g1 + 5.6
    lv.tap(r3)
    lx = lv.land_x(r3, 1.0) - 1.0
    lv.block(lx, lx + 1.5, 1.0)
    r4 = lx + 1.2
    lv.tap(r4)
    lv.tune(lv.arm(lx + 0.75, 4.6, 2.9, speed=2.1), [r3, r4], 160)
    after = lv.land_x(r4, -1.0)
    r5 = after + 2.0
    lv.block(after - 1.0, r5 + 1.2, 0)

    lv.group("RotorsAroundADoubleJump")
    lv.tap(r5)
    lv.dj(r5 + 3.9)
    rot1 = lv.rotor(r5 + 2.4, 2.9, radius=36.0, points=4, spin=-3.0,
                    osc=Osc((0.0, -1.6 * 64), 1.2))
    rot2 = lv.rotor(r5 + 7.0, 1.4, radius=36.0, points=3, spin=3.5,
                    osc=Osc((0.0, -2.2 * 64), 1.4))
    lv.tune(rot1, r5, 160, osc=True)
    lv.tune(rot2, r5 + 3.9, 150, osc=True)
    end2 = lv.land_x(r5, 0.0, air=3.9 / tps)
    cp2 = end2 + 5.0
    lv.checkpoint(cp2)
    lv.block(end2 - 1.2, cp2 + 16, 0)

    # ------------------------------------------ pulse gates, moving floors ---
    lv.group("ThreeStopGate")
    p1 = cp2 + 15.0
    lv.tap(p1)
    g = lv.gate(p1 + 3.4, [(1.25, 2.5), (2.75, 2.2), (4.2, 2.2)], hold=0.45, move=0.2)
    lv.tune(g, p1, 150)

    lv.group("ShuttleThroughPulse")
    e1 = p1 + 8.0
    lv.block(cp2 + 16, e1, 0) if e1 > cp2 + 16 else None
    s1 = e1 - 1.2
    lv.tap(s1)
    shuttle = lv.mover(lv.land_x(s1, 0.5) - 1.6, 2.4, 0.5, (2.6, 0.0), 1.6)
    s2 = lv.land_x(s1, 0.5) + 0.6
    lv.tap(s2)
    lv.tune(shuttle, [s1, s2], 160, osc=True)
    pg = lv.gate(s2 + 3.3, [(1.9, 2.2), (3.1, 2.2)], hold=0.5, move=0.2)
    lv.tune(pg, s2, 150)
    back = lv.land_x(s2, -0.5)
    lv.block(back - 1.0, back + 8, 0)

    lv.group("LateDoubleJumpToLedge")
    w1 = back + 5.5
    lv.tap(w1)
    lv.dj(w1 + 6.0)
    far = lv.land_x(w1, 1.0, air=6.0 / tps) - 0.5
    lv.block(far, far + 1.4, 1.0)
    # A timed opening: the window shuts on a beat; the late double jump
    # has to meet it open.
    lv.tune(lv.timed_gate(far - 1.5, 0.35, hold=0.45, move=0.15), w1 + 6.0, 150)
    w2 = far + 1.1
    lv.tap(w2)
    down = lv.land_x(w2, -1.0)
    cp3 = down + 5.0
    lv.block(down - 1.0, cp3 + 16, 0)
    lv.checkpoint(cp3)

    # ------------------------------------------------- the last fifth: chains ---
    lv.group("LandJumpDoubleJump")
    # Jump > land > jump at once > double jump, twice, gates on every arc.
    c1 = cp3 + 15.5
    lv.tap(c1)
    n1 = lv.land_x(c1, 0.5) - 0.9
    lv.block(cp3 + 16, c1 + 1.2, 0)
    lv.block(n1, n1 + 1.3, 0.5)
    lv.window_gate(c1 + 3.4, 0.35)
    c2 = n1 + 1.0
    lv.tap(c2)
    lv.dj(c2 + 3.3)
    n2 = lv.land_x(c2, 1.5, air=3.3 / tps) - 0.7
    lv.block(n2, n2 + 1.3, 2.0)
    lv.window_gate(c2 + 4.9, 0.3)
    c3 = n2 + 1.0
    lv.tap(c3)
    lv.dj(c3 + 3.6)
    n3 = lv.land_x(c3, -1.0, air=3.6 / tps) - 0.8
    lv.block(n3, n3 + 1.5, 1.0)
    lv.tune(lv.arm(c3 + 5.4, 4.8, 2.3, speed=2.4), c3 + 3.6, 140)

    lv.group("FinalThread")
    c4 = n3 + 1.2
    lv.tap(c4)
    lv.dj(c4 + 4.4)
    fin = lv.land_x(c4, -1.0, air=4.4 / tps)
    for dx in (1.6, 3.8):
        lv.window_gate(c4 + dx, 0.3)
    lv.tune(lv.pulse_gate(c4 + 6.2, -1.6, hold=0.45, move=0.2), c4 + 4.4, 140)
    lv.block(fin - 1.2, fin + 20, 0)
    lv.shards_along(c1, fin, 2.0)

    lv.finish(fin + 12)
    lv.done()
    return lv


def level_05():
    """RED VOID — brutal but fair: no new tricks, everything at once and
    faster. Five sections; the last 15% is the hardest stretch of the world.
    Every window is at least four ticks wide on the intended route."""
    lv = Level("level_05", "w01_l05", "Red Void", "Brutal but fair", 1.23)
    tps = lv.tiles_per_second()
    from levelgen import Osc

    lv.group("Runway")
    lv.block(-12, 24, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # ------------------------------------------ 1. speed and timing ---
    lv.group("S1_Rhythm")
    a = 20.0
    lv.tap(a)
    lv.tune(lv.arm(a + 3.6, 0.5, 1.8, speed=3.0), a, 150)
    b = a + 7.3
    lv.tap(b)
    lv.tune(piston(lv, b + 3.3, height=2.1, period=1.1, closed=0.25), b, 140)
    c = b + 7.2
    lv.block(24, c + 1.2, 0)
    lv.tap(c)
    n1 = lv.land_x(c, 0.5) - 0.9
    lv.block(n1, n1 + 1.2, 0.5)
    d = n1 + 1.0
    lv.tap(d)
    n2 = lv.land_x(d, 0.5) - 0.9
    lv.block(n2, n2 + 1.2, 1.0)
    lv.tune(lv.arm(d + 3.2, 4.6, 2.6, speed=-2.4), d, 140)
    e = n2 + 1.0
    lv.tap(e)
    lv.window_gate(e + 3.4, 0.35)
    f1 = lv.land_x(e, -1.0)
    cp1 = f1 + 5.0
    lv.block(f1 - 1.0, cp1 + 17, 0)
    lv.shards_along(a, f1, 3.5)
    lv.checkpoint(cp1)

    # ------------------------------- 2. moving platforms and pulse gates ---
    lv.group("S2_Shuttle")
    s1 = cp1 + 16.2
    lv.tap(s1)
    shuttle = lv.mover(lv.land_x(s1, 0.5) - 1.5, 2.2, 0.5, (2.8, 0.0), 1.5)
    s2 = lv.land_x(s1, 0.5) + 0.5
    lv.tap(s2)
    lv.tune(shuttle, [s1, s2], 150, osc=True)
    lv.tune(lv.pulse_gate(s2 + 3.4, 1.5, hold=0.4, move=0.18), s2, 140)

    lv.group("S2_Elevator")
    lift_x = lv.land_x(s2, 0.0) - 1.2
    lift = lv.mover(lift_x, 2.4, -0.5, (0.0, 3.0), 1.8)
    s3 = lift_x + 2.0
    lv.tap(s3)
    lv.tune(lift, [s2, s3], 150, osc=True)
    top = lv.land_x(s3, 1.5) - 0.9
    lv.block(top, top + 6.0, 3.0)
    lv.tune(lv.pulse_gate(s3 + 3.0, 1.3, hold=0.45, move=0.18), s3, 140)
    s4 = top + 5.0
    lv.tap(s4)
    lv.tune(lv.field(s4 + 2.4, 1.0, 3.6, 3.2, 1.1, 0.62), s4, 140)
    lv.dj(s4 + 4.2)
    f2 = lv.land_x(s4, -3.0, air=4.2 / tps)
    cp2 = f2 + 5.0
    lv.block(f2 - 1.2, cp2 + 17, 0)
    lv.tune(lv.pulse_gate(s4 + 6.0, -1.4, hold=0.4, move=0.18), s4 + 4.2, 130)
    lv.shards_along(s1, f2, 3.5)
    lv.checkpoint(cp2)

    # ------------------------------------ 3. crush blocks, forced double jump ---
    lv.group("S3_CrusherDoubleJump")
    k1 = cp2 + 16.0
    lv.tap(k1)
    lv.dj(k1 + 4.0)
    l1 = lv.land_x(k1, 1.5, air=4.0 / tps) - 0.8
    lv.block(l1, l1 + 1.3, 1.5)
    lv.tune(lv.crusher(k1 + 2.6, 2.2, 4.6, -2.4, 1.5, height=3.0), k1, 140)
    lv.tune(lv.crusher(k1 + 5.6, 2.2, 5.4, -2.2, 1.5, height=3.0), k1 + 4.0, 130)

    lv.group("S3_SlamLedges")
    k2 = l1 + 1.0
    lv.tap(k2)
    l2 = lv.land_x(k2, -0.5) - 0.8
    lv.block(l2, l2 + 1.3, 1.0)
    lv.tune(lv.crusher(l2 - 0.4, 2.1, 4.6, -3.6, 1.4, height=3.0, closed=0.25, ret=0.45), k2, 140)
    k3 = l2 + 1.0
    lv.tap(k3)
    lv.dj(k3 + 4.4)
    f3 = lv.land_x(k3, -1.0, air=4.4 / tps)
    lv.window_gate(k3 + 6.6, 0.35)
    lv.tune(piston(lv, f3 + 0.8, width=1.4, height=2.0, period=1.2, closed=0.25), k3 + 4.4, 130)
    cp3 = f3 + 6.0
    lv.block(f3 - 1.2, cp3 + 17, 0)
    lv.shards_along(k1, f3, 3.5)
    lv.checkpoint(cp3)

    # ------------------------- 4. sequential gates, arms, collapsing path ---
    lv.group("S4_GatesOnTheCollapse")
    q0 = cp3 + 17
    lv.collapsing(q0, 18, 0.0, lv.clock(q0) + 0.3, 1.0 / (1.12 * tps))
    q1 = q0 + 2.0
    lv.tap(q1)
    for dx in (1.4, 3.6, 5.8):
        lv.window_gate(q1 + dx, 0.4)
    q2 = q1 + 7.4
    lv.tap(q2)
    lv.tune(lv.arm(q2 + 3.6, 0.5, 1.8, speed=3.0), q2, 130)
    q3 = q0 + 16.8
    lv.tap(q3)
    lv.dj(q3 + 4.6)
    f4 = lv.land_x(q3, 0.0, air=4.6 / tps)
    for dx in (2.0, 6.4):
        lv.window_gate(q3 + dx, 0.35)
    cp4 = f4 + 5.0
    lv.block(f4 - 1.2, cp4 + 16, 0)
    lv.shards_along(q1, f4, 3.5)
    lv.checkpoint(cp4)

    # ------------------------------------------------ 5. final gauntlet ---
    lv.group("S5_Gauntlet")
    g1 = cp4 + 15.5
    lv.tap(g1)
    lv.tune(lv.prism(g1 + 3.4, 0.4, 2.2, 2.4, 1.2), g1, 120, osc=True)
    g2 = g1 + 7.0
    lv.block(cp4 + 16, g2 + 1.1, 0)
    lv.tap(g2)
    h1 = lv.land_x(g2, 1.0) - 0.9
    lv.block(h1, h1 + 1.2, 1.0)
    # The panel times the jump; the arm sweeping above it shuts the high line
    # (an early double jump over everything).
    lv.tune(lv.arm(g2 + 3.4, 5.0, 2.6, speed=-2.8), g2, 150)
    lv.tune(lv.panel(g2 + 3.2, 0.75, 0.0, 1.7, osc=Osc((0.0, -2.9 * 64), 1.2, wave="steps", hold_ratio=0.5)),
            g2, 120, osc=True)
    g3 = h1 + 1.0
    lv.tap(g3)
    lv.dj(g3 + 3.8)
    h2 = lv.land_x(g3, 2.0, air=3.8 / tps) - 0.8
    lv.block(h2, h2 + 1.2, 3.0)
    lv.tune(lv.pulse_gate(g3 + 5.2, 1.2, hold=0.35, move=0.16), g3 + 3.8, 110)
    g4 = h2 + 1.0
    lv.tap(g4)
    lv.tune(lv.timed_gate(g4 + 3.2, 0.3, hold=0.35, move=0.14), g4, 100)
    h3x = lv.land_x(g4, -1.5) - 1.0
    lv.collapsing(h3x, 7, 1.5, lv.clock(h3x) + 0.2, 1.0 / (1.35 * tps))
    g5 = h3x + 2.6
    lv.tap(g5)
    lv.dj(g5 + 4.8)
    fin = lv.land_x(g5, -1.5, air=4.8 / tps)
    lv.tune(lv.rotor(g5 + 3.2, 2.1, radius=40.0, points=3, spin=4.5,
                     osc=Osc((0.0, -1.8 * 64), 1.1)), g5, 100, osc=True)
    lv.tune(lv.pulse_gate(g5 + 7.2, -1.4, hold=0.35, move=0.16), g5 + 4.8, 100)
    lv.block(fin - 1.2, fin + 22, 0)
    lv.shards_along(g1, fin, 3.0)

    lv.finish(fin + 14)
    lv.done()
    return lv


LEVELS = [level_01, level_02, level_03, level_04, level_05]


def read_routes():
    """Routes already in tests/support/world_01_routes.gd: {level number: (name, taps)}."""
    out = {}
    try:
        with open(ROOT + "tests/support/world_01_routes.gd") as fh:
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
    lines = ["class_name World01Routes",
             "## Generated by tools/levelgen/world_01.py: the intended solution of each",
             "## World 01 level, as the player-centre x (tiles) of every tap.", ""]
    for number in sorted(routes):
        name, taps = routes[number]
        lines.append(f"## {name}")
        lines.append(f"const LEVEL_{number:02d}: PackedFloat32Array = [{', '.join(f'{x:g}' for x in taps)}]")
    names = ", ".join(f"LEVEL_{n:02d}" for n in sorted(routes))
    lines += ["", "", "## The route of level [param index] (0-based).",
              "static func get_route(index: int) -> PackedFloat32Array:",
              f"\tvar all: Array[PackedFloat32Array] = [{names}]",
              "\treturn all[index]"]
    with open(ROOT + "tests/support/world_01_routes.gd", "w") as fh:
        fh.write("\n".join(lines) + "\n")


def write_world(count):
    lines = ['[gd_resource type="Resource" script_class="WorldData" format=3]', "",
             '[ext_resource type="Script" path="res://src/level/world_data.gd" id="1_world"]',
             '[ext_resource type="Script" path="res://src/level/level_data.gd" id="2_level"]']
    for i in range(1, count + 1):
        lines.append(f'[ext_resource type="Resource" path="res://levels/world_01/level_{i:02d}.tres" id="level_{i:02d}"]')
    refs = ", ".join(f'ExtResource("level_{i:02d}")' for i in range(1, count + 1))
    lines += ["", "[resource]", 'script = ExtResource("1_world")', 'id = &"world_01"', "number = 1",
              'display_name = "The Red Void"', f'levels = Array[ExtResource("2_level")]([{refs}])']
    lines.append('next_world_name = "World 02"')
    with open(ROOT + "levels/world_01/world_01.tres", "w") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    args = sys.argv[1:]
    only = [int(a) for a in args if a.isdigit()] or list(range(1, len(LEVELS) + 1))
    taps_arg = [int(t) for a in args if a.startswith("--taps=") for t in a[7:].split(",")]
    routes = read_routes()
    os.makedirs(ROOT + "levels/world_01", exist_ok=True)
    for i in only:
        lv = LEVELS[i - 1]()
        lv.report(windows="--windows" in args or bool(taps_arg), only=taps_arg or None)
        for note in lv.notes:
            print("  " + note)
        lv.write(ROOT, f"levels/world_01/level_{i:02d}.tscn", f"levels/world_01/level_{i:02d}.tres")
        routes[i] = (lv.name, sorted(lv.route))
    write_routes(routes)
    write_world(len(LEVELS))


if __name__ == "__main__":
    main()
