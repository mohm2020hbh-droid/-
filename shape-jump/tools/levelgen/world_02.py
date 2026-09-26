#!/usr/bin/env python3
"""World 02 — THE MONOCHROME VOID: five levels, generated.

    python3 tools/levelgen/world_02.py            # build, check, write all levels
    python3 tools/levelgen/world_02.py 3 --windows # one level, with tap windows

Writes levels/world_02/level_0N.tscn + .tres, levels/world_02/world_02.tres
and levels/world_02/world_02_routes.gd. Same movement as World 01 (tap, one
double jump); the difficulty comes from new rules: ground that is not there
(SHADOW GAPS), walls that open and close (MIRROR WALLS), rotating rings
(ORBIT RINGS), blinding flashes (WHITEOUT), moving monoliths (BLACK COLUMNS),
rising and sinking slabs (SPLIT FLOOR), platforms over nothing (FLOATING
PANELS), a shadow that hunts from behind (SHADOW CHASER), turning walls
(ROTATING MAZE) and two-state barriers (BINARY GATES).
"""
import os
import sys

from levelgen import Level, T

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..") + "/"
WORLD = "world_02"


def level_01():
    """INVERSION — hard: SHADOW GAPS, MIRROR WALLS and BLACK COLUMNS, first
    alone, then two at a time; the last section is built on the lesson that
    what looks solid is not, and what looks closed is only a reflection."""
    lv = Level("level_01", "w02_l01", "Inversion", "Hard", 1.10)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    lv.block(-12, 22, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # ------------------------------------------------------ shadow gaps ---
    lv.group("FirstShadow")
    # Dashed lip, no collision: the floor ahead is a shadow.
    lv.shadow(22, 26.5, 0)
    lv.tap(20.6)
    lv.block(26.5, 36, 0)
    lv.shards_along(21.6, 25.6, 1.5)

    lv.group("ShadowStair")
    # A real step up, then shadow on both sides of it.
    lv.shadow(36, 40, 0)
    lv.tap(34.3)
    lv.block(40, 42, 1.0)
    lv.shadow(42, 47, 1.0)
    lv.tap(41.3)
    land = lv.land_x(41.3, -1.0)
    lv.block(47, 59.5, 0)
    lv.shards_along(35.3, land - 0.5, 1.8)

    # ------------------------------------------------------ mirror walls ---
    lv.group("FirstMirror")
    m1 = 51.0
    lv.tap(m1)
    lv.tune(lv.mirror_wall_fit(m1 + 3.0, 0.45, period=1.6), m1, 170)
    lv.shard(m1 + 3.0, 2.0)

    lv.group("MirrorPair")
    m2 = 58.0
    lv.tap(m2)
    lv.tune(lv.mirror_wall_fit(m2 + 2.2, 0.4, period=1.4), m2, 150)
    lv.shadow(59.5, 63.5, 0)
    lv.block(63.5, 77.5, 0)
    lv.tune(lv.mirror_wall_fit(m2 + 4.2, 0.4, period=1.4), m2, 150)

    # ----------------------------------------------------- black columns ---
    lv.group("FirstColumn")
    c1 = 69.0
    lv.tap(c1)
    # A monolith rising out of the floor: over it while it stands.
    lv.tune(lv.column(c1 + 2.4, 1.0, -2.2, 3.2, travel=2.2, period=1.5), c1, 170, osc=True)

    lv.group("ColumnDoor")
    c2 = 76.0
    lv.tap(c2)
    lv.shadow(77.5, 81.6, 0)
    # Hanging monolith over a shadow: jump the shadow while it is up.
    lv.tune(lv.column(c2 + 2.6, 1.2, 0.2, 5.0, travel=3.4, period=1.6), c2, 150, osc=True)
    lv.shards_along(c2 + 1, c2 + 5, 2.0)

    # --------------------------------------------------------- pairs ---
    lv.group("Pairs_ShadowMirror")
    p1 = 87.0
    lv.block(81.6, 88.5, 0)
    lv.shadow(88.5, 93, 0)
    lv.tap(p1)
    lv.tune(lv.mirror_wall_fit(p1 + 3.2, 0.35, period=1.3), p1, 140)
    lv.block(93, 97, 0)
    p2 = 95.4
    lv.tap(p2)
    step = lv.land_x(p2, 1.2) - 0.9
    lv.block(step, step + 1.6, 1.2)
    lv.tune(lv.column(p2 + 2.0, 1.0, -2.4, 3.0, travel=2.4, period=1.3), p2, 140, osc=True)
    p3 = step + 1.2
    lv.tap(p3)
    lv.dj(p3 + 3.6)
    far = lv.land_x(p3, -1.2, air=3.6 / tps)
    lv.shadow(step + 1.6, far - 0.8, 1.2)
    lv.tune(lv.mirror_wall_fit(p3 + 5.4, 0.35, period=1.2), p3 + 3.6, 140)
    lv.block(far - 1.0, far + 14, 0)
    lv.shards_along(p1, far, 3.0)

    lv.group("Pairs_ColumnMirror")
    q1 = far + 6.0
    lv.tap(q1)
    lv.tune(lv.column(q1 + 2.2, 1.0, 0.3, 5.0, travel=3.4, period=1.2), q1, 140, osc=True, forced=False)
    lv.tune(lv.mirror_wall_fit(q1 + 4.6, 0.35, period=1.2), q1, 140)
    q2 = q1 + 7.6
    lv.tap(q2)
    lv.shadow(q1 + 8.5, q1 + 13.0, 0)
    lv.tune(lv.column(q2 + 3.0, 1.0, -2.6, 3.4, travel=2.8, period=1.2), q2, 130, osc=True)
    end = lv.land_x(q2, 0)
    t1 = end + 8.0
    lv.block(q1 + 13.0, t1 + 1.0, 0)
    lv.shards_along(q1, end, 3.0)

    lv.group("Triples")
    # All three at once: a wall on the way up, a slot between two
    # monoliths for the double jump, and a floor that is not there.
    lv.tap(t1)
    lv.tune(lv.mirror_wall_fit(t1 + 2.4, 0.35, period=1.2), t1, 130)
    lv.dj(t1 + 3.6)
    far = lv.land_x(t1, 0.0, air=3.6 / tps)
    lv.shadow(t1 + 1.0, far - 0.8, 0)
    lv.tune(lv.column(t1 + 6.2, 1.2, 5.8, 5.0, travel=-2.8, period=1.2), t1 + 3.6, 130, osc=True)
    lv.column_under(t1 + 6.2, 1.2, 0.5)
    t2 = far + 3.5
    lv.tap(t2)
    lv.tune(lv.column(t2 + 2.4, 1.0, -2.2, 3.4, travel=2.4, period=1.1), t2, 130, osc=True)
    lv.tune(lv.mirror_wall_fit(t2 + 4.6, 0.3, period=1.1), t2, 130)
    end = lv.land_x(t2, 0)
    lv.block(far - 0.8, end + 8.9, 0)
    lv.shards_along(t1, end, 3.0)

    # ------------------------------------------ appearances mislead ---
    lv.group("Inversion")
    # The flat run ahead is a shadow; the only real ground is the black
    # step above it, reached with a double jump.
    r1 = end + 8.0
    lv.shadow(r1 + 1.0, r1 + 16.0, 0)
    lv.tap(r1)
    lv.dj(r1 + 3.2)
    s1 = lv.land_x(r1, 2.0, air=3.2 / tps) - 0.9
    lv.block(s1, s1 + 1.4, 2.0)
    lv.tune(lv.column(r1 + 2.0, 1.0, -2.8, 3.8, travel=3.0, period=1.2), r1, 130, osc=True)
    r2 = s1 + 1.1
    lv.tap(r2)
    s2 = lv.land_x(r2, 0.0) - 0.9
    lv.block(s2, s2 + 1.4, 2.0)
    lv.tune(lv.mirror_wall_fit(r2 + 3.0, 0.3, period=1.1), r2, 120)
    r3 = s2 + 1.1
    lv.tap(r3)
    lv.dj(r3 + 3.4)
    fin = lv.land_x(r3, -1.0, air=3.4 / tps)
    lv.shadow(s2 + 1.4, fin - 0.6, 1.0)
    lv.tune(lv.column(r3 + 5.0, 1.0, 3.6, 5.0, travel=-2.6, period=1.1), r3 + 3.4, 120, osc=True)
    lv.block(fin - 0.6, fin + 20, 1.0)
    lv.shards_along(r1, fin, 3.0)

    lv.finish(fin + 13, 1.0)
    lv.done()
    lv.progress_checkpoints()
    return lv


def level_02():
    """ORBIT — very hard: ORBIT RINGS, FLOATING PANELS and SPLIT FLOOR. Long
    aerial chains (jump, through a ring, double jump, onto a moving panel,
    a narrow landing) where the double jump is a real decision: spend it
    early for the ring, or keep it for the panel."""
    lv = Level("level_02", "w02_l02", "Orbit", "Very hard", 1.13)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    lv.block(-12, 24, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # ------------------------------------------------------ first ring ---
    lv.group("FirstRing")
    a = 20.0
    lv.tap(a)
    lv.tune(lv.orbit_fit(a + 3.3, 2.0, spin=2.2), a, 170)
    lv.block(24, 32.5, 0)
    lv.shard(a + 3.3, 2.2)

    lv.group("RingOverShadow")
    b = 30.0
    lv.tap(b)
    lv.shadow(32.5, 35.5, 0)
    lv.tune(lv.orbit_fit(b + 3.3, 2.0, spin=2.8), b, 150)
    c = 44.0
    lv.block(35.5, c + 0.8, 0)

    # ------------------------------------------------- floating panels ---
    lv.group("FirstPanel")
    lv.tap(c)
    pan = lv.floating(lv.land_x(c, 0.5) - 1.6, 2.4, 0.5, (2.4, 0.0), 1.6)
    c2 = lv.land_x(c, 0.5) + 0.6
    lv.tap(c2)
    lv.tune(pan, [c, c2], 170, osc=True)
    land = lv.land_x(c2, 0.0)
    d0 = land + 8.0
    lv.block(land - 1.0, d0, 0)
    lv.shards_along(c, land, 2.0)

    # ------------------------------------------------------ split floor ---
    lv.group("SplitFloor")
    slabs = lv.split_floor(d0, 4, 2.2, 0.0, 1.2, 1.4)
    d = d0 - 1.2
    lv.tap(d)
    d2 = lv.land_x(d, 1.2) + 0.4
    lv.tap(d2)
    lv.tune(lv.slab_group(slabs), [d, d2], 150)
    e0 = d0 + 4 * 2.2
    lv.shards_along(d, e0, 2.0)

    # ------------------------------------- ring on the double jump arc ---
    lv.group("RingHigh")
    e = e0 + 5.0
    lv.tap(e)
    lv.dj(e + 3.0)
    far = lv.land_x(e, 0.0, air=3.0 / tps)
    lv.block(e0, e + 0.9, 0)
    lv.tune(lv.orbit_fit(e + 6.4, 2.0, spin=2.4), e + 3.0, 150)
    lv.block(far - 1.0, far + 9.9, 0)
    lv.shards_along(e, far, 2.5)

    # ------------- the chain: jump, ring, double jump, panel, landing ---
    lv.group("Chain")
    g = far + 9.0
    lv.tap(g)
    lv.tune(lv.orbit_fit(g + 3.3, 2.0, spin=2.6), g, 140)
    lv.dj(g + 6.0)
    px = lv.land_x(g, 1.0, air=6.0 / tps) - 1.4
    pan = lv.floating(px, 2.0, 1.0, (2.2, 0.0), 1.5)
    g2 = px + 2.4
    lv.tap(g2)
    lv.tune(pan, [g + 6.0, g2], 140, osc=True)
    n1 = lv.land_x(g2, 0.5) - 0.8
    lv.block(n1, n1 + 1.2, 1.5)
    lv.tune(lv.orbit_fit(g2 + 3.3, 1.9, spin=2.2), g2, 140)
    g3 = n1 + 1.0
    lv.tap(g3)
    far = lv.land_x(g3, -1.5)
    s0 = far + 9.0
    lv.block(far - 1.0, s0 + 0.9, 0)
    lv.shards_along(g, far, 3.0)

    # ------------------------------------------ stairs that breathe ---
    lv.group("Stairs")
    lv.tap(s0)
    stair = lv.split_floor(s0 + 4.6, 2, 2.0, 0.6, 1.2, 1.2)
    s1 = lv.land_x(s0, 0.6) + 0.4
    lv.tap(s1)
    lv.tune(lv.slab_group(stair), [s0, s1], 140)
    lv.tune(lv.orbit_fit(s0 + 3.3, 2.0, spin=2.6), s0, 140)
    top = lv.land_x(s1, 1.2) - 1.0
    lv.block(top, top + 2.0, 2.2)
    s2 = top + 1.4
    lv.tap(s2)
    lv.dj(s2 + 4.0)
    far = lv.land_x(s2, -2.2, air=4.0 / tps)
    lv.tune(lv.orbit_fit(s2 + 6.2, 2.0, spin=2.4), s2 + 4.0, 130)
    px = far - 1.4
    drop = lv.floating(px - 1.0, 2.0, 0.0, (2.2, 0.0), 1.2)
    s3 = far + 1.4
    lv.tap(s3)
    lv.tune(drop, [s2 + 4.0, s3], 130, osc=True)
    far = lv.land_x(s3, 0.0)
    lv.block(far - 1.0, far + 8.9, 0)
    lv.shards_along(s0, far, 3.0)

    # ------------------------------------------------ vertical climb ---
    lv.group("Climb")
    h = far + 8.0
    lv.tap(h)
    lift_x = lv.land_x(h, 0.0) - 1.3
    lift = lv.floating(lift_x, 2.2, 0.0, (0.0, 2.6), 1.8, wave="steps", hold_ratio=0.5)
    h2 = lift_x + 1.8
    lv.tap(h2)
    lv.tune(lift, [h, h2], 140, osc=True)
    top = lv.land_x(h2, 2.2) - 0.8
    lv.block(top, top + 1.4, 4.6)
    h3 = top + 1.1
    lv.tap(h3)
    lv.dj(h3 + 4.6)
    far = lv.land_x(h3, -4.6, air=4.6 / tps)
    lv.tune(lv.orbit_fit(h3 + 7.0, 2.0, spin=2.4), h3 + 4.6, 130)
    lv.block(far - 1.0, far + 9.9, 0)
    lv.shards_along(h, far, 3.0)

    # ------------------------------------------------- final chain ---
    lv.group("FinalChain")
    k = far + 9.0
    lv.tap(k)
    slabs = lv.split_floor(k + 4.6, 3, 2.2, 0.8, 1.0, 1.3)
    k2 = lv.land_x(k, 0.8) + 0.2
    lv.tap(k2)
    n0 = lv.land_x(k2, 1.2) - 0.8
    lv.block(n0, n0 + 1.3, 2.0)
    lv.tune(lv.slab_group(slabs), [k, k2], 150)
    lv.tune(lv.orbit_fit(k + 3.3, 2.0, spin=2.6), k, 130)
    # From the ledge: jump, ring, double jump, panel, narrow landing.
    k3 = n0 + 1.1
    lv.tap(k3)
    lv.tune(lv.orbit_fit(k3 + 3.3, 2.0, spin=2.4), k3, 130)
    lv.dj(k3 + 5.8)
    px = lv.land_x(k3, -1.0, air=5.8 / tps) - 1.3
    pan = lv.floating(px, 1.8, 1.0, (2.4, 0.0), 1.3)
    k4 = px + 2.4
    lv.tap(k4)
    lv.tune(pan, [k3 + 5.8, k4], 130, osc=True)
    n2 = lv.land_x(k4, 1.0) - 0.7
    lv.block(n2, n2 + 1.1, 2.0)
    k5 = n2 + 0.9
    lv.tap(k5)
    lv.dj(k5 + 4.0)
    fin = lv.land_x(k5, -2.0, air=4.0 / tps)
    lv.tune(lv.orbit_fit(k5 + 6.4, 2.0, spin=2.8), k5 + 4.0, 120)
    lv.block(fin - 1.0, fin + 22, 0)
    lv.shards_along(k, fin, 3.0)

    land = fin + 4.0
    lv.finish(land + 10)
    lv.done()
    lv.progress_checkpoints()
    return lv


def whiteout(lv, x0, x1, flash_x, period=2.4, flash=0.9, warning=0.45):
    """A WHITEOUT zone over x0..x1 whose flash begins as the player's centre
    reaches flash_x: the glare warns `warning` s before, while everything
    is still visible, and repeats every `period` s after."""
    phase = (warning / period - lv.clock(flash_x) / period) % 1.0
    lv.whiteout(x0, x1, period, flash, warning, phase)


def level_03():
    """WHITEOUT — extremely hard: the screen floods white on a rhythm (every
    real ledge and hazard stays drawn in black, the shadows vanish), a
    shadow hunts from behind and lunges along the floor, black columns and
    shadow gaps keep the feet busy. The route is always shown before the
    flash; the lunges come on a fixed beat."""
    lv = Level("level_03", "w02_l03", "Whiteout", "Extremely hard", 1.16)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    lv.block(-12, 22, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # ------------------------------------------------- first whiteout ---
    lv.group("FirstFlash")
    a = 20.0
    lv.tap(a)
    lv.shadow(22, 26.4, 0)
    lv.block(26.4, 38, 0)
    # Seen first, then the white: the flash comes mid-air.
    whiteout(lv, 14, 40, a + 1.0)
    b = 31.0
    lv.tap(b)
    lv.tune(lv.column(b + 2.4, 1.0, -2.2, 3.2, travel=2.2, period=1.4), b, 170, osc=True)
    lv.shards_along(a, b + 5, 2.0)

    # ------------------------------------------------------ the chase ---
    lv.group("Chase")
    beat = 1.7 * tps       # A lunge every 1.7 s: one jump on each.
    c0 = 44.0
    lunges = [c0 + i * beat for i in range(4)]
    lv.block(38, lunges[0] + 1.0, 0)
    for i, x in enumerate(lunges):
        lv.tap(x)
        gap_end = x + 5.2
        lv.shadow(x + 1.6, gap_end, 0)
        nxt = lunges[i + 1] + 1.0 if i + 1 < len(lunges) else x + 23.8
        lv.block(gap_end, nxt, 0)
    chaser = lv.chaser(c0 - 10.0, lunges[-1] + 6.0, lag=3.0, period=1.7, reach=5.0, tongue=0.9)
    lv.tune(chaser, lunges, 150)
    # Between the lunges: columns on their own beat.
    for i, x in enumerate(lunges[:-1]):
        m = x + 8.0
        lv.tap(m)
        lv.tune(lv.column(m + 2.3, 1.0, -2.2, 3.1, travel=2.2, period=1.7), m, 150, osc=True)
    whiteout(lv, lunges[1] - 4.0, lunges[2] + 6.0, lunges[2] - 0.6, period=1.7, flash=0.8, warning=0.5)
    lv.shards_along(c0, lunges[-1] + 5, 3.0)

    # ------------------------------------- columns inside the white ---
    lv.group("WhiteColumns")
    w0 = lunges[-1] + 20.0
    w1 = w0 + 2.0
    lv.tap(w1)
    lv.shadow(w1 + 1.8, w1 + 5.4, 0)
    # A hanging monolith over the shadow; the flash lands mid-jump.
    lv.tune(lv.column(w1 + 2.8, 1.2, 0.2, 5.0, travel=3.4, period=1.4), w1, 140, osc=True)
    w2 = w1 + 7.6
    lv.block(w1 + 5.4, w2 + 0.9, 0)
    lv.tap(w2)
    lv.dj(w2 + 3.8)
    far = lv.land_x(w2, 0.0, air=3.8 / tps)
    lv.shadow(w2 + 0.9, far - 0.8, 0)
    lv.tune(lv.column(w2 + 2.6, 1.0, -2.2, 3.4, travel=2.4, period=1.3), w2, 140, osc=True)
    lv.tune(lv.column(w2 + 6.4, 1.2, 5.8, 5.0, travel=-2.8, period=1.3), w2 + 3.8, 130, osc=True)
    lv.column_under(w2 + 6.4, 1.2, 0.4)
    whiteout(lv, w0 - 6.0, far + 2.0, w2 + 0.5, period=1.6, flash=0.8, warning=0.5)
    w3 = far + 3.5
    lv.tap(w3)
    step = lv.land_x(w3, 1.0) - 0.9
    lv.block(far - 0.8, w3 + 0.9, 0)
    lv.block(step, step + 1.3, 1.0)
    lv.tune(lv.column(w3 + 2.4, 1.0, -2.2, 3.6, travel=2.6, period=1.2), w3, 130, osc=True)
    w4 = step + 1.1
    lv.tap(w4)
    far = lv.land_x(w4, -1.0)
    lv.shadow(step + 1.3, far - 0.7, 1.0)
    lv.tune(lv.column(w4 + 3.0, 1.2, 4.6, 5.0, travel=-2.4, period=1.2), w4, 130, osc=True)
    whiteout(lv, far - 12.0, far + 4.0, w4 - 0.4, period=1.6, flash=0.8, warning=0.5)
    lv.block(far - 0.7, far + 16.5, 0)
    lv.shards_along(w1, far, 3.0)

    # ------------------------------------------- the chase, faster ---
    lv.group("Chase2")
    beat = 1.5 * tps
    d0 = far + 16.0
    lunges = [d0 + i * beat for i in range(3)]
    lv.block(far + 16.5, lunges[0] + 1.0, 0)
    for i, x in enumerate(lunges):
        lv.tap(x)
        if i % 2 == 0:
            # Double jump over a long shadow while the tongue is out.
            lv.dj(x + 3.6)
            land = lv.land_x(x, 0.0, air=3.6 / tps)
            lv.shadow(x + 1.4, land - 0.7, 0)
            lv.tune(lv.column(x + 6.2, 1.2, 5.8, 5.0, travel=-2.8, period=1.5), x + 3.6, 130, osc=True)
            lv.column_under(x + 6.2, 1.2, 0.4)
        else:
            land = x + 6.3
            lv.shadow(x + 1.6, land - 1.0, 0)
            lv.tune(lv.column(x + 3.0, 1.2, 0.2, 5.0, travel=3.4, period=1.5), x, 130, osc=True, forced=False)
        nxt = lunges[i + 1] + 1.0 if i + 1 < len(lunges) else x + 30.0
        lv.block(land - 0.7 if i % 2 == 0 else land - 1.0, nxt, 0)
    chaser = lv.chaser(d0 - 5.0, lunges[-1] + 6.0, lag=3.0, period=1.5, reach=5.0, tongue=0.9)
    lv.tune(chaser, lunges, 130)
    whiteout(lv, lunges[0] + 4.0, lunges[-1] + 6.0, lunges[1] - 0.5, period=3.0, flash=0.8, warning=0.5)
    lv.shards_along(d0, lunges[-1] + 5, 3.0)

    # --------------------------------------------- the last stretch ---
    lv.group("Finale")
    f0 = lunges[-1] + 30.0
    beat = 1.3 * tps
    lunges = [f0 + i * beat for i in range(5)]
    lv.block(lunges[0] - 8.0, lunges[0] + 1.0, 0)
    for i, x in enumerate(lunges):
        lv.tap(x)
        lv.dj(x + 3.4)
        land = lv.land_x(x, 0.0, air=3.4 / tps)
        lv.shadow(x + 1.2, land - 0.6, 0)
        nxt = lunges[i + 1] + 1.0 if i + 1 < len(lunges) else x + 34.0
        lv.block(land - 0.6, nxt, 0)
        lv.tune(lv.column(x + 2.4, 1.0, -2.2, 3.2, travel=2.4, period=1.3), x, 120, osc=True)
        lv.tune(lv.column(x + 6.0, 1.2, 5.8, 5.0, travel=-2.8, period=1.3), x + 3.4, 120, osc=True)
        lv.column_under(x + 6.0, 1.2, 0.4)
    chaser = lv.chaser(f0 - 5.0, lunges[-1] + 6.0, floor=0.0, lag=3.0, period=1.3, reach=5.0, tongue=0.9)
    lv.tune(chaser, lunges[::2], 120, forced=False)
    whiteout(lv, f0 - 6.0, lunges[-1] + 8.0, lunges[0] + 1.0, period=1.3, flash=0.7, warning=0.45)
    end = lunges[-1] + 14.0
    lv.shards_along(f0, lunges[-1] + 6, 3.0)

    lv.finish(end + 6)
    lv.done()
    lv.progress_checkpoints()
    return lv


def level_04():
    """FRACTAL — extremely hard, a mastery test: ROTATING MAZE panels that
    turn a quarter on a beat, BINARY GATES that swap their solid half,
    orbit rings, split floor and floating panels, chained: jump, through a
    turning opening, double jump, onto a moving panel, a gate flips, land
    at once. Every change is on a fixed clock and flickers before it
    happens."""
    lv = Level("level_04", "w02_l04", "Fractal", "Extremely hard", 1.20)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    lv.block(-12, 24, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # ----------------------------------------------------- the maze ---
    lv.group("FirstMaze")
    a = 20.0
    lv.tap(a)
    # A bar at knee height: over it while it lies flat, never while it stands.
    lv.tune(lv.maze(a + 3.3, 0.75, 3.6, hold=0.8, turn=0.25), a, 170)
    b = a + 8.0
    lv.tap(b)
    lv.tune(lv.maze(b + 3.0, 0.75, 3.6, hold=0.6, turn=0.2, direction=-1), b, 150)
    lv.block(24, 51.9, 0)
    lv.shards_along(a, b + 5, 2.0)

    # ---------------------------------------------------- binary gates ---
    lv.group("FirstBinary")
    c = 38.0
    lv.tap(c)
    # WHITE: a wall up to 1.8 (go over); BLACK: a ceiling from 2.4 (stay low).
    lv.tune(lv.binary(c + 3.0, white=(-1, 1.8), black=(2.2, 14), period=1.3), c, 150)
    c2 = 51.0
    lv.tap(c2)
    lv.dj(c2 + 3.4)
    lv.tune(lv.maze(c2 + 2.6, 0.75, 3.0, hold=0.7, turn=0.22), c2, 150)
    # WHITE: up to 3.2 (only a double jump clears it); BLACK: from 3.8 down.
    lv.tune(lv.binary(c2 + 5.8, white=(-1, 3.2), black=(3.8, 14), period=1.1), c2 + 3.4, 140)
    land = lv.land_x(c2, 0.0, air=3.4 / tps)
    lv.shadow(c2 + 0.9, land - 0.8, 0)
    d = land + 5.0
    lv.block(land - 0.8, d + 0.9, 0)
    lv.shards_along(c, land, 2.5)

    # ------------------------------------------- ring over split floor ---
    lv.group("RingSlabs")
    lv.tap(d)
    # Wide slabs: land early on the first, leave it before its seam.
    slabs = lv.split_floor(d + 4.2, 2, 3.4, 0.8, 1.2, 1.3)
    d2 = lv.land_x(d, 0.8) + 0.2
    lv.tap(d2)
    lv.tune(lv.slab_group(slabs), [d, d2], 150)
    lv.tune(lv.orbit_fit(d + 3.3, 2.0, spin=2.4), d, 140)
    n = lv.land_x(d2, 1.2) - 0.8
    lv.block(n, n + 1.3, 2.0)
    lv.tune(lv.maze(d2 + 3.0, 4.6, 2.6, hold=0.6, turn=0.2), d2, 140, forced=False)
    d3 = n + 1.1
    lv.tap(d3)
    far = lv.land_x(d3, -2.0)
    lv.tune(lv.orbit_fit(d3 + 3.4, 2.0, spin=2.6), d3, 130)
    lv.block(far - 1.0, far + 9.9, 0)
    lv.shards_along(d, far, 3.0)

    # ------------------ the chain: jump, maze, DJ, panel, gate, land ---
    lv.group("Chain")
    g = far + 9.0
    lv.tap(g)
    lv.tune(lv.maze(g + 3.3, 0.75, 3.6, hold=0.55, turn=0.2), g, 130)
    lv.dj(g + 5.6)
    px = lv.land_x(g, 1.0, air=5.6 / tps) - 1.3
    pan = lv.floating(px, 1.2, 1.0, (2.4, 0.0), 1.2)
    g2 = px + 2.1
    lv.tap(g2)
    lv.tune(pan, [g + 5.6, g2], 130, osc=True)
    lv.tune(lv.orbit_fit(g + 7.8, 1.9, spin=2.4), g + 5.6, 140)
    n2 = lv.land_x(g2, 0.8) - 0.7
    lv.block(n2, n2 + 1.2, 1.8)
    lv.tune(lv.binary(g2 + 3.0, white=(-1, 2.6), black=(3.3, 14), period=0.8), g2, 110, forced=False)
    g3 = n2 + 1.0
    lv.tap(g3)
    lv.tune(lv.orbit_fit(g3 + 3.3, 2.0, spin=2.6), g3, 130)
    far = lv.land_x(g3, -1.8)
    lv.shadow(n2 + 1.2, far - 0.7, 0)
    lv.block(far - 0.7, far + 16, 0)
    lv.shards_along(g, far, 3.0)

    # ------------------------------------------ turning corridor ---
    lv.group("Corridor")
    h = far + 8.0
    lv.tap(h)
    lv.block(far + 15, h + 30, 0)
    # Two bars turning in opposite senses, then a gate: a rhythm to learn.
    lv.tune(lv.maze(h + 3.0, 0.75, 3.6, hold=0.5, turn=0.2), h, 130)
    h2 = h + 7.2
    lv.tap(h2)
    lv.tune(lv.maze(h2 + 3.0, 0.75, 3.6, hold=0.5, turn=0.2, direction=-1), h2, 130)
    h3 = h2 + 7.2
    lv.tap(h3)
    lv.tune(lv.binary(h3 + 3.0, white=(-1, 1.8), black=(2.3, 14), period=0.8), h3, 110)
    h4 = h3 + 7.0
    lv.tap(h4)
    lv.tune(lv.maze(h4 + 2.6, 0.75, 3.0, hold=0.5, turn=0.2), h4, 130)
    lv.dj(h4 + 3.4)
    lv.tune(lv.orbit_fit(h4 + 5.8, 2.0, spin=2.6), h4 + 3.4, 120)
    far = lv.land_x(h4, 0.0, air=3.4 / tps)
    lv.shadow(h + 30, far - 0.7, 0)
    lv.block(far - 0.7, far + 9.9, 0)
    lv.shards_along(h, far, 3.0)

    # ---------------------------------------- gates between rings ---
    lv.group("GateRings")
    j = far + 9.0
    lv.tap(j)
    lv.tune(lv.orbit_fit(j + 3.3, 2.0, spin=2.6), j, 130)
    lv.dj(j + 5.6)
    slabs = lv.split_floor(lv.land_x(j, 0.6, air=5.6 / tps) - 1.0, 2, 3.2, 0.6, 1.2, 1.0)
    j2 = lv.land_x(j, 0.6, air=5.6 / tps) + 0.5
    lv.tap(j2)
    lv.tune(lv.slab_group(slabs), [j + 5.6, j2], 150)
    lv.tune(lv.maze(j2 + 3.0, 0.75 + 0.6, 3.6, hold=0.5, turn=0.18), j2, 130)
    far = lv.land_x(j2, -0.6)
    lv.block(far - 1.0, far + 9.9, 0)
    lv.shards_along(j, far, 3.0)

    # -------------------------------------------------- the fractal ---
    lv.group("Fractal")
    k = far + 9.0
    lv.tap(k)
    lv.tune(lv.orbit_fit(k + 3.3, 2.0, spin=2.6), k, 130)
    lv.dj(k + 5.8)
    px = lv.land_x(k, 1.0, air=5.8 / tps) - 1.2
    pan = lv.floating(px, 1.8, 1.0, (0.0, 1.4), 1.2, wave="steps", hold_ratio=0.5)
    k2 = px + 1.5
    lv.tap(k2)
    lv.tune(pan, [k + 5.8, k2], 130, osc=True)
    lv.tune(lv.orbit_fit(k + 8.0, 2.0, spin=2.4), k + 5.8, 140)
    lv.tune(lv.maze(k2 + 3.0, 5.6, 2.6, hold=0.5, turn=0.2), k2, 120, forced=False)
    slabs = lv.split_floor(lv.land_x(k2, 0.2) - 1.0, 2, 3.2, 1.2, 1.2, 1.2)
    k3 = lv.land_x(k2, 0.2) + 0.5
    lv.tap(k3)
    lv.tune(lv.slab_group(slabs), [k2, k3], 120)
    lv.dj(k3 + 3.6)
    fin = lv.land_x(k3, -1.2, air=3.6 / tps)
    lv.tune(lv.orbit_fit(k3 + 5.9, 2.0, spin=2.8), k3 + 3.6, 120)
    lv.tune(lv.binary(k3 + 8.4, white=(-1, 3.4), black=(4.2, 14), period=0.8), k3 + 3.6, 110, forced=False)
    lv.shadow(k + 1.0, fin - 0.8, 0)
    lv.shards_along(k, fin, 3.0)

    # ------------------------------------------------------- echo ---
    lv.group("Echo")
    # The corridor again, faster, straight after the fractal.
    e1 = fin + 4.0
    lv.tap(e1)
    lv.tune(lv.maze(e1 + 3.0, 0.75, 3.6, hold=0.45, turn=0.18), e1, 120)
    e2 = e1 + 7.2
    lv.tap(e2)
    lv.tune(lv.binary(e2 + 3.0, white=(-1, 1.8), black=(2.3, 14), period=0.8), e2, 110)
    e3 = e2 + 7.0
    lv.tap(e3)
    lv.block(fin - 0.8, e3 + 0.9, 0)
    lv.dj(e3 + 3.4)
    end = lv.land_x(e3, 0.0, air=3.4 / tps)
    lv.shadow(e3 + 0.9, end - 0.7, 0)
    lv.tune(lv.orbit_fit(e3 + 5.8, 2.0, spin=2.6), e3 + 3.4, 120)
    lv.block(end - 0.7, end + 22, 0)
    lv.shards_along(e1, end, 3.0)

    lv.finish(end + 14)
    lv.done()
    lv.progress_checkpoints()
    return lv


def level_05():
    """ABSOLUTE ZERO — brutal but fair: all ten systems, in five sections:
    shadow gaps and black columns; orbit rings and floating panels; the
    whiteout and the chaser; the maze and binary gates; and a final
    gauntlet that uses everything at once and is the hardest stretch of
    both worlds. Every window stays at least four ticks wide."""
    lv = Level("level_05", "w02_l05", "Absolute Zero", "Brutal but fair", 1.24)
    tps = lv.tiles_per_second()

    lv.group("Runway")
    lv.block(-12, 21.6, 0)
    for x in (9, 11, 13):
        lv.shard(x, 0.5)

    # --------------------------------- 1. shadow gaps, black columns ---
    lv.group("S1_Shadows")
    a = 20.0
    lv.tap(a)
    lv.shadow(21.6, 25.8, 0)
    lv.tune(lv.column(a + 2.8, 1.2, 0.2, 5.0, travel=3.4, period=1.2), a, 140, osc=True)
    a2 = 28.6
    lv.block(25.8, a2 + 0.9, 0)
    lv.tap(a2)
    step = lv.land_x(a2, 1.2) - 0.9
    lv.shadow(a2 + 0.9, step, 0)
    lv.block(step, step + 1.3, 1.2)
    lv.tune(lv.column(a2 + 2.4, 1.0, -2.2, 3.4, travel=2.6, period=1.1), a2, 130, osc=True)
    a3 = step + 1.1
    lv.tap(a3)
    lv.dj(a3 + 3.6)
    far = lv.land_x(a3, -1.2, air=3.6 / tps)
    lv.shadow(step + 1.3, far - 0.7, 1.2)
    lv.tune(lv.column(a3 + 6.4, 1.2, 7.0, 5.0, travel=-3.0, period=1.1), a3 + 3.6, 120, osc=True)
    lv.column_under(a3 + 6.4, 1.2, 0.4)
    a4 = far + 4.0
    lv.block(far - 0.7, a4 + 0.9, 0)
    lv.tap(a4)
    land = lv.land_x(a4, 0.0)
    lv.shadow(a4 + 0.9, land - 0.8, 0)
    lv.tune(lv.column(a4 + 3.0, 1.2, 0.2, 5.0, travel=3.4, period=1.0), a4, 120, osc=True, forced=False)
    lv.column_under(a4 + 3.0, 1.2, 0.5)
    lv.block(land - 0.8, land + 8.9, 0)
    lv.shards_along(a, land, 3.0)

    # ------------------------------------ 2. orbit rings, floating ---
    lv.group("S2_Orbit")
    b = land + 8.0
    lv.tap(b)
    lv.tune(lv.orbit_fit(b + 3.4, 2.0, spin=2.6), b, 130)
    lv.dj(b + 6.0)
    px = lv.land_x(b, 1.0, air=6.0 / tps) - 1.3
    pan = lv.floating(px, 1.8, 1.0, (2.4, 0.0), 1.2)
    b2 = px + 2.4
    lv.tap(b2)
    lv.tune(pan, [b + 6.0, b2], 130, osc=True)
    lv.tune(lv.orbit_fit(b + 8.2, 2.0, spin=2.4), b + 6.0, 150)
    lv.tune(lv.orbit_fit(b2 + 3.4, 2.0, spin=2.8), b2, 120)
    px2 = lv.land_x(b2, 0.6) - 1.2
    pan2 = lv.floating(px2, 1.8, 1.6, (0.0, 1.4), 1.2, wave="steps", hold_ratio=0.5)
    b3 = px2 + 1.5
    lv.tap(b3)
    lv.tune(pan2, [b2, b3], 120, osc=True)
    lv.dj(b3 + 3.8)
    far = lv.land_x(b3, -1.6, air=3.8 / tps)
    lv.tune(lv.orbit_fit(b3 + 6.2, 2.0, spin=2.4), b3 + 3.8, 120)
    lv.block(far - 1.0, far + 13.0, 0)
    lv.shards_along(b, far, 3.0)

    # --------------------------------- 3. the whiteout and the chaser ---
    lv.group("S3_Chase")
    beat = 1.3 * tps
    c0 = far + 12.0
    lunges = [c0 + i * beat for i in range(4)]
    for i, x in enumerate(lunges):
        lv.tap(x)
        if i % 2:
            lv.dj(x + 3.4)
            land = lv.land_x(x, 0.0, air=3.4 / tps)
            lv.tune(lv.column(x + 6.0, 1.2, 7.0, 5.0, travel=-3.0, period=1.3), x + 3.4, 130, osc=True)
            lv.column_under(x + 6.0, 1.2, 0.6)
        else:
            land = lv.land_x(x, 0.0)
            lv.tune(lv.column(x + 3.0, 1.2, 0.2, 5.0, travel=3.4, period=1.3), x, 120, osc=True, forced=False)
        lv.shadow(x + 1.2, land - 0.7, 0)
        nxt = lunges[i + 1] + 1.0 if i + 1 < len(lunges) else x + 30.0
        lv.block(land - 0.7, nxt, 0)
    chaser = lv.chaser(c0 - 5.0, lunges[-1] + 6.0, lag=3.0, period=1.3, reach=5.0, tongue=0.9)
    lv.tune(chaser, lunges, 120)
    whiteout(lv, c0 - 4.0, lunges[-1] + 8.0, lunges[1] + 0.8, period=2.6, flash=0.8, warning=0.5)
    far = lunges[-1] + 16.0
    lv.shards_along(c0, lunges[-1] + 6, 3.0)

    # --------------------------------------- 4. the maze, binary gates ---
    lv.group("S4_Maze")
    m = far + 6.0
    lv.tap(m)
    lv.tune(lv.maze(m + 3.3, 0.75, 3.6, hold=0.45, turn=0.18), m, 120)
    m2 = m + 8.0
    lv.tap(m2)
    lv.tune(lv.binary(m2 + 2.2, white=(-1, 1.5), black=(2.6, 14), period=0.9), m2, 120)
    lv.tune(lv.binary(m2 + 4.4, white=(-1, 1.5), black=(2.6, 14), period=0.9), m2, 110)
    m3 = m2 + 8.0
    lv.tap(m3)
    lv.dj(m3 + 3.4)
    lv.tune(lv.maze(m3 + 3.3, 0.75, 3.6, hold=0.45, turn=0.18, direction=-1), m3, 120)
    lv.tune(lv.binary(m3 + 5.8, white=(-1, 3.2), black=(3.8, 14), period=0.9), m3 + 3.4, 110)
    far = lv.land_x(m3, 0.0, air=3.4 / tps)
    lv.shadow(m3 + 1.0, far - 0.7, 0)
    lv.block(lunges[-1] + 30.0, m3 + 1.0, 0)
    lv.block(far - 0.7, far + 9.9, 0)
    lv.shards_along(m, far, 3.0)

    # -------------------------------------------- 5. final gauntlet ---
    lv.group("S5_Gauntlet")
    g = far + 9.0
    lv.tap(g)
    lv.tune(lv.mirror_wall_fit(g + 1.6, 0.3, period=1.0), g, 110)
    slabs = lv.split_floor(g + 4.4, 2, 3.4, 0.8, 1.2, 1.1)
    g2 = lv.land_x(g, 0.8) + 0.2
    lv.tap(g2)
    lv.tune(lv.slab_group(slabs), [g, g2], 150)
    n = lv.land_x(g2, 1.2) - 0.9
    lv.block(n, n + 1.5, 2.0)
    lv.tune(lv.orbit_fit(g2 + 3.3, 2.0, spin=2.6), g2, 150)
    g3 = n + 1.0
    lv.tap(g3)
    lv.tune(lv.mirror_wall_fit(g3 + 1.5, 0.3, period=0.9), g3, 110)
    lv.dj(g3 + 5.2)
    px = lv.land_x(g3, -1.0, air=5.2 / tps) - 1.2
    pan = lv.floating(px, 1.4, 1.0, (2.2, 0.0), 1.1)
    lv.tune(lv.maze(g3 + 3.0, 6.0, 2.6, hold=0.5, turn=0.18), g3, 130, forced=False)
    g4 = px + 2.2
    lv.tap(g4)
    lv.tune(pan, [g3 + 5.2, g4], 110, osc=True)
    lv.tune(lv.binary(g4 + 1.6, white=(-1, 1.9), black=(2.9, 14), period=0.9), g4, 130, forced=False)
    n2 = lv.land_x(g4, 0.2) - 0.7
    lv.block(n2, n2 + 1.1, 1.2)
    g5 = n2 + 0.9
    lv.tap(g5)
    lv.tune(lv.mirror_wall_fit(g5 + 1.5, 0.3, period=0.9), g5, 100)
    lv.dj(g5 + 3.6)
    fin = lv.land_x(g5, -1.2, air=3.6 / tps)
    lv.tune(lv.column(g5 + 6.2, 1.2, 7.0, 5.0, travel=-3.0, period=1.0), g5 + 3.6, 110, osc=True)
    lv.column_under(g5 + 6.2, 1.2, 0.55)
    lv.shadow(n2 + 1.1, fin - 0.6, 1.2)
    lv.shadow(g3 + 1.0, n2, 0.0)
    chaser = lv.chaser(g - 4.0, fin + 4.0, lag=3.2, period=2.2, reach=5.2, tongue=0.9)
    lv.tune(chaser, [g], 110, forced=False)
    whiteout(lv, g4 - 6.0, fin + 6.0, g4 - 0.3, period=2.2, flash=0.8, warning=0.5)
    # The last beat: through a ring, a double jump over the last shadow,
    # out through a closing mirror wall.
    g6 = fin + 3.2
    lv.block(fin - 0.6, g6 + 0.9, 0)
    lv.tap(g6)
    lv.tune(lv.orbit_fit(g6 + 3.3, 2.0, spin=2.8), g6, 120)
    lv.dj(g6 + 5.4)
    end = lv.land_x(g6, 0.0, air=5.4 / tps)
    lv.shadow(g6 + 0.9, end - 0.7, 0)
    lv.tune(lv.mirror_wall_fit(g6 + 7.8, 0.35, period=1.0), g6 + 5.4, 130)
    lv.block(end - 0.7, end + 24, 0)
    lv.shards_along(g, end, 3.0)

    lv.finish(end + 14)
    lv.done()
    lv.progress_checkpoints()
    return lv


LEVELS = [level_01, level_02, level_03, level_04, level_05]


def read_routes():
    """Routes already in levels/world_02/world_02_routes.gd: {level number: (name, taps)}."""
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
    lines = ["class_name World02Routes",
             "## Generated by tools/levelgen/world_02.py: the intended solution of each",
             "## World 02 level, as the player-centre x (tiles) of every tap.", ""]
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
             '[ext_resource type="PackedScene" path="res://src/background/background_mono.tscn" id="3_background"]',
             '[ext_resource type="Resource" path="res://levels/world_01/world_01.tres" id="4_requires"]']
    for i in range(1, count + 1):
        lines.append(f'[ext_resource type="Resource" path="res://levels/{WORLD}/level_{i:02d}.tres" id="level_{i:02d}"]')
    refs = ", ".join(f'ExtResource("level_{i:02d}")' for i in range(1, count + 1))
    lines += ["", "[resource]", 'script = ExtResource("1_world")', f'id = &"{WORLD}"', "number = 2",
              'display_name = "The Monochrome Void"', f'levels = Array[ExtResource("2_level")]([{refs}])',
              'next_world_name = ""', 'requires = ExtResource("4_requires")', 'theme = &"mono"',
              'background = ExtResource("3_background")']
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
        lv.write(ROOT, f"levels/{WORLD}/level_{i:02d}.tscn", f"levels/{WORLD}/level_{i:02d}.tres")
        routes[i] = (lv.name, sorted(lv.route))
    write_routes(routes)
    write_world(len(LEVELS))


if __name__ == "__main__":
    main()
