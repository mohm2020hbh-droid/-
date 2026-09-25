"""Level building blocks for Shape Jump (dev tool; see tools/levelgen/README.md).

A level is written as Python: blocks, obstacles and the intended route (the
x positions where the player taps). The player's x(t) is linear and every
timed element is a pure function of level time, so this module can replay
the route tick by tick with the motor's exact rules (Sim), knows every
hazard's hitbox at any time (mirrors of the GDScript classes), and fits each
obstacle's phase so the intended path runs through the middle of its gap.
The Godot audit (tests/tools/level_audit.tscn) then checks the real thing.

Coordinates: tiles (64 px) for x; heights in tiles above the ground line
(up is positive). Everything is converted to Godot pixels (y down).
"""
import math

T = 64.0
DT = 1.0 / 60.0

# Mirrors src/player/movement_config.gd defaults.
BASE_SPEED = 520.0
JUMP_H = 150.0
APEX_T = 0.36
FALL_MULT = 1.3
MAX_FALL = 1400.0
G_UP = 2.0 * JUMP_H / APEX_T ** 2
G_DOWN = G_UP * FALL_MULT
V_JUMP = 2.0 * JUMP_H / APEX_T
V_DOUBLE = math.sqrt(2.0 * G_UP * JUMP_H)
COYOTE = 0.08
BUFFER = 0.12
HALF = 24.0          # Half size of the player's body.
HURT_HALF = 18.0     # Half size of the hurtbox.
LEDGE_ASSIST = 10.0
INSET = 4.0          # Hazard.HITBOX_INSET

GAME_SRC = "res://src/level/elements/"


def f(x):
    return f"{x:.4f}".rstrip("0").rstrip(".") if isinstance(x, float) else str(x)


def v2(x, y):
    return f"Vector2({f(float(x))}, {f(float(y))})"


def packed_v2(points):
    return "PackedVector2Array(" + ", ".join(f"{f(float(a))}, {f(float(b))}" for a, b in points) + ")"


def smoothstep(k):
    k = min(max(k, 0.0), 1.0)
    return k * k * (3.0 - 2.0 * k)


# ------------------------------------------------------------------ geometry --

def rect_poly(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def rotated_rect(cx, cy, w, h, angle):
    c, s = math.cos(angle), math.sin(angle)
    pts = []
    for dx, dy in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)):
        pts.append((cx + dx * c - dy * s, cy + dx * s + dy * c))
    return pts


def circle_poly(cx, cy, r, n=12):
    return [(cx + r * math.cos(math.tau * i / n), cy + r * math.sin(math.tau * i / n)) for i in range(n)]


def separation(poly, box):
    """SAT separation (px) between a convex polygon and an AABB (x0, y0, x1, y1):
    positive = apart by at least that much, negative = overlapping."""
    bx0, by0, bx1, by1 = box
    box_pts = [(bx0, by0), (bx1, by0), (bx1, by1), (bx0, by1)]
    axes = [(1.0, 0.0), (0.0, 1.0)]
    n = len(poly)
    for i in range(n):
        ax, ay = poly[i]
        bx, by = poly[(i + 1) % n]
        ex, ey = bx - ax, by - ay
        length = math.hypot(ex, ey)
        if length > 1e-9:
            axes.append((-ey / length, ex / length))
    best = -1e9
    for nx, ny in axes:
        p = [px * nx + py * ny for px, py in poly]
        b = [px * nx + py * ny for px, py in box_pts]
        best = max(best, min(b) - max(p), min(p) - max(b))
    return best


# ---------------------------------------------------------------- components --

class Osc:
    """Mirror of oscillator.gd: moves its parent along `travel` (px, y down)."""

    def __init__(self, travel, period, phase=0.0, wave="sine", hold_ratio=0.6, warning=0.3, show_track=True):
        self.travel, self.period, self.phase = travel, period, phase
        self.wave, self.hold_ratio, self.warning, self.show_track = wave, hold_ratio, warning, show_track

    def progress(self, t):
        cycle = (t / self.period + self.phase) % 1.0
        if self.wave == "sine":
            return 0.5 - 0.5 * math.cos(cycle * math.tau)
        if self.wave == "linear":
            return 1.0 - abs(cycle * 2.0 - 1.0)
        hold, move = self.hold_ratio * 0.5, (1.0 - self.hold_ratio) * 0.5
        if cycle < hold:
            return 0.0
        if cycle < hold + move:
            return smoothstep((cycle - hold) / move)
        if cycle < hold * 2 + move:
            return 1.0
        return 1.0 - smoothstep((cycle - hold * 2 - move) / move)

    def offset(self, t):
        k = self.progress(t)
        return self.travel[0] * k, self.travel[1] * k

    def reach(self):
        return min(0.0, self.travel[0]), max(0.0, self.travel[0])

    def props(self):
        p = {"travel": v2(*self.travel), "period": f(float(self.period)), "phase": f(float(self.phase % 1.0))}
        if self.wave != "sine":
            p["wave"] = {"linear": 1, "steps": 2}[self.wave]
        if self.wave == "steps":
            p["hold_ratio"] = f(float(self.hold_ratio))
            if self.warning != 0.3:
                p["warning_time"] = f(float(self.warning))
        if not self.show_track:
            p["show_track"] = "false"
        return p


def _offset(osc, t):
    return osc.offset(t) if osc else (0.0, 0.0)


class Element:
    """A scene node; subclasses that are hazards implement polys(t) and x_range()."""
    base = "Node"
    type_ = "Node2D"
    script = None
    instance = None
    osc = None

    def props(self):
        return {}

    def polys(self, t):
        return []

    def x_range(self):
        return (0.0, 0.0)


class Gate(Element):
    base, type_, script = "Gate", "Area2D", "gate"

    def __init__(self, x, stops, hold, move, phase, warning, width, reach):
        self.x, self.stops, self.hold, self.move = x, stops, hold, move
        self.phase, self.warning, self.width, self.reach = phase, warning, width, reach

    def opening(self, t):
        n = len(self.stops)
        if n == 1:
            return self.stops[0]
        step = self.hold + self.move
        u = (t + self.phase * step * n) % (step * n)
        i = min(int(u / step), n - 1)
        local = u - i * step
        if local < self.hold:
            return self.stops[i]
        k = smoothstep((local - self.hold) / self.move)
        a, b = self.stops[i], self.stops[(i + 1) % n]
        return (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)

    def polys(self, t):
        cy, h = self.opening(t)
        top, bottom = cy - h / 2, cy + h / 2
        x0, x1 = self.x - self.width / 2 + INSET, self.x + self.width / 2 - INSET
        return [rect_poly(x0, top - self.reach + INSET, x1, top - INSET),
                rect_poly(x0, bottom + INSET, x1, bottom + self.reach - INSET)]

    def x_range(self):
        return (self.x - self.width / 2, self.x + self.width / 2)

    def props(self):
        p = {"position": v2(self.x, 0), "stops": packed_v2(self.stops), "hold_time": f(float(self.hold)),
             "move_time": f(float(self.move)), "phase": f(float(self.phase % 1.0)),
             "warning_time": f(float(self.warning))}
        if self.width != 40.0:
            p["width"] = f(float(self.width))
        if self.reach != 1200.0:
            p["reach"] = f(float(self.reach))
        return p


class Arm(Element):
    base, type_, script = "RotatingArm", "Area2D", "rotating_arm"

    def __init__(self, x, y, arms, length, thickness, speed, phase, hub):
        self.x, self.y, self.arms, self.length, self.thickness = x, y, arms, length, thickness
        self.speed, self.phase, self.hub = speed, phase, hub

    def angle(self, t):
        return self.phase * math.tau + self.speed * t

    def polys(self, t):
        out = []
        arm_length = self.length - INSET
        for i in range(self.arms):
            a = self.angle(t) + math.tau * i / self.arms
            cx = self.x + math.cos(a) * arm_length / 2
            cy = self.y + math.sin(a) * arm_length / 2
            out.append(rotated_rect(cx, cy, arm_length, max(self.thickness - 2 * INSET, 4.0), a))
        out.append(circle_poly(self.x, self.y, max(self.hub - INSET, 4.0)))
        return out

    def x_range(self):
        return (self.x - self.length, self.x + self.length)

    def props(self):
        p = {"position": v2(self.x, self.y), "arms": self.arms, "length": f(float(self.length)),
             "speed": f(float(self.speed)), "phase": f(float(self.phase % 1.0))}
        if self.thickness != 22.0:
            p["thickness"] = f(float(self.thickness))
        if self.hub != 24.0:
            p["hub_radius"] = f(float(self.hub))
        return p


class Crusher(Element):
    base, type_, script = "CrushBlock", "Area2D", "crush_block"

    def __init__(self, x0, y0, size, travel, period, phase, warning, slam, closed, ret):
        self.x0, self.y0, self.size, self.travel = x0, y0, size, travel
        self.period, self.phase, self.warning = period, phase, warning
        self.slam, self.closed, self.ret = slam, closed, ret

    def open_time(self):
        return self.period - self.warning - self.slam - self.closed - self.ret

    def closure(self, t):
        u = (t + self.phase * self.period) % self.period
        a = self.open_time() + self.warning
        if u < a:
            return 0.0
        if u < a + self.slam:
            k = (u - a) / self.slam
            return k * k
        if u < a + self.slam + self.closed:
            return 1.0
        return 1.0 - smoothstep((u - a - self.slam - self.closed) / self.ret)

    def polys(self, t):
        k = self.closure(t)
        ox, oy = self.travel[0] * k, self.travel[1] * k
        return [rect_poly(self.x0 + ox + INSET, self.y0 + oy + INSET,
                          self.x0 + ox + self.size[0] - INSET, self.y0 + oy + self.size[1] - INSET)]

    def x_range(self):
        return (self.x0 + min(0.0, self.travel[0]), self.x0 + self.size[0] + max(0.0, self.travel[0]))

    def props(self):
        return {"position": v2(self.x0, self.y0), "size": v2(*self.size), "travel": v2(*self.travel),
                "period": f(float(self.period)), "phase": f(float(self.phase % 1.0)),
                "warning_time": f(float(self.warning)), "slam_time": f(float(self.slam)),
                "closed_time": f(float(self.closed)), "return_time": f(float(self.ret))}


class Field(Element):
    base, type_, script = "EnergyField", "Area2D", "energy_field"

    def __init__(self, x0, y0, size, period, on_ratio, phase, warning, osc):
        self.x0, self.y0, self.size, self.period = x0, y0, size, period
        self.on_ratio, self.phase, self.warning, self.osc = on_ratio, phase, warning, osc

    def is_on(self, t):
        u = ((t / self.period + self.phase) % 1.0) * self.period
        return u < self.on_ratio * self.period

    def polys(self, t):
        if not self.is_on(t):
            return []
        ox, oy = _offset(self.osc, t)
        return [rect_poly(self.x0 + ox + INSET, self.y0 + oy + INSET,
                          self.x0 + ox + self.size[0] - INSET, self.y0 + oy + self.size[1] - INSET)]

    def x_range(self):
        lo, hi = self.osc.reach() if self.osc else (0.0, 0.0)
        return (self.x0 + lo, self.x0 + self.size[0] + hi)

    def props(self):
        return {"position": v2(self.x0, self.y0), "size": v2(*self.size), "period": f(float(self.period)),
                "on_ratio": f(float(self.on_ratio)), "phase": f(float(self.phase % 1.0)),
                "warning_time": f(float(self.warning))}


class Prism(Element):
    base, type_, script = "PrismBeam", "Area2D", "prism_beam"

    def __init__(self, x, y, span, osc, thickness=14.0):
        self.x, self.y, self.span, self.osc, self.thickness = x, y, span, osc, thickness

    def polys(self, t):
        ox, oy = _offset(self.osc, t)
        h = max(self.thickness - INSET, 6.0) / 2
        return [rect_poly(self.x + ox - self.span / 2, self.y + oy - h, self.x + ox + self.span / 2, self.y + oy + h)]

    def x_range(self):
        lo, hi = self.osc.reach() if self.osc else (0.0, 0.0)
        return (self.x - self.span / 2 - 12 + lo, self.x + self.span / 2 + 12 + hi)

    def props(self):
        p = {"position": v2(self.x, self.y), "span": f(float(self.span))}
        if self.thickness != 14.0:
            p["thickness"] = f(float(self.thickness))
        return p


class Rotor(Element):
    base, type_, script = "Rotor", "Area2D", "rotor"

    def __init__(self, x, y, radius, points, spin, phase, osc, hitbox_scale=0.8):
        self.x, self.y, self.radius, self.points = x, y, radius, points
        self.spin, self.phase, self.osc, self.hitbox_scale = spin, phase, osc, hitbox_scale

    def polys(self, t):
        ox, oy = _offset(self.osc, t)
        rot = self.phase * math.tau + self.spin * t
        r = self.radius * self.hitbox_scale
        pts = []
        for i in range(self.points):
            a = math.tau * i / self.points - math.pi / 2 + rot
            pts.append((self.x + ox + r * math.cos(a), self.y + oy + r * math.sin(a)))
        return [pts]

    def x_range(self):
        lo, hi = self.osc.reach() if self.osc else (0.0, 0.0)
        return (self.x - self.radius + lo, self.x + self.radius + hi)

    def props(self):
        return {"position": v2(self.x, self.y), "radius": f(float(self.radius)), "points": self.points,
                "spin": f(float(self.spin)), "phase": f(float(self.phase % 1.0))}


class Panel(Element):
    base, type_, script = "WallPanel", "Area2D", "wall_panel"

    def __init__(self, x0, y0, size, osc):
        self.x0, self.y0, self.size, self.osc = x0, y0, size, osc

    def polys(self, t):
        ox, oy = _offset(self.osc, t)
        return [rect_poly(self.x0 + ox + INSET, self.y0 + oy + INSET,
                          self.x0 + ox + self.size[0] - INSET, self.y0 + oy + self.size[1] - INSET)]

    def x_range(self):
        lo, hi = self.osc.reach() if self.osc else (0.0, 0.0)
        return (self.x0 + lo, self.x0 + self.size[0] + hi)

    def props(self):
        return {"position": v2(self.x0, self.y0), "size": v2(*self.size)}


class Spikes(Element):
    base, type_, script = "Spikes", "Area2D", "spikes"

    def __init__(self, x0, y0, count, facing_down):
        self.x0, self.y0, self.count, self.down = x0, y0, count, facing_down
        d = 1.0 if facing_down else -1.0
        self._polys = []
        for i in range(count):
            tri = [(x0 + i * 64, y0), (x0 + i * 64 + 32, y0 + d * 52), (x0 + (i + 1) * 64, y0)]
            cx = sum(p[0] for p in tri) / 3
            cy = sum(p[1] for p in tri) / 3
            self._polys.append([(cx + (px - cx) * 0.7, cy + (py - cy) * 0.7) for px, py in tri])

    def polys(self, t):
        return self._polys

    def x_range(self):
        return (self.x0, self.x0 + self.count * 64)

    def props(self):
        p = {"position": v2(self.x0, self.y0), "count": self.count}
        if self.down:
            p["facing"] = 1
        return p


class Plain(Element):
    """Any other node: fixed props, no hitbox."""

    def __init__(self, base, type_, script, props, instance=None):
        self.base, self.type_, self.script, self._props, self.instance = base, type_, script, props, instance

    def props(self):
        return self._props


class Surface:
    """A solid top the simulated player can land on: an AABB (px, y down),
    optionally moved by an Osc or only present before `drop` (level time)."""

    def __init__(self, x0, x1, top, bottom, osc=None, drop=None):
        self.x0, self.x1, self.top, self.bottom, self.osc, self.drop = x0, x1, top, bottom, osc, drop

    def alive(self, t):
        return self.drop is None or t < self.drop

    def rect(self, t):
        dx, dy = _offset(self.osc, t)
        return self.x0 + dx, self.x1 + dx, self.top + dy, self.bottom + dy


# --------------------------------------------------------------------- level --

class Level:
    def __init__(self, key, level_id, name, tagline, speed_scale, spawn_x=4.0, kill_depth=7.5):
        self.key, self.id, self.name, self.tagline = key, level_id, name, tagline
        self.speed = BASE_SPEED * speed_scale
        self.speed_scale = speed_scale
        self.spawn_px = spawn_x * T
        self.kill_y = kill_depth * T
        self.entries = []        # (group, name, element)
        self.hazards = []
        self.surfaces = []
        self.route = []
        self.checkpoints = []    # (x tiles, height tiles)
        self.finish_x = None
        self.shards = 0
        self.group_name = "."
        self.groups = []
        self.notes = []
        self._names = {}
        self._sim = None
        self._path = None

    # ---------------------------------------------------------------- time --
    def clock(self, x_tiles):
        """Level time at which the player's centre is at x (tiles)."""
        return (x_tiles * T - self.spawn_px) / self.speed

    def tiles_per_tick(self):
        return self.speed * DT / T

    # --------------------------------------------------------------- nodes --
    def group(self, name):
        self.groups.append(name)
        self.group_name = name

    def add(self, element):
        key = (self.group_name, element.base)
        n = self._names.get(key, 0) + 1
        self._names[key] = n
        self.entries.append((self.group_name, element.base if n == 1 else f"{element.base}{n}", element))
        if not isinstance(element, Plain):
            self.hazards.append(element)
        self._sim = None
        self._path = None
        return element

    # ------------------------------------------------------------ geometry --
    def block(self, x0, x1, top, bottom=-10.0, top_edge=True):
        """Solid block from x0 to x1 (tiles) whose top is `top` tiles above the ground line."""
        props = {"position": v2(x0 * T, -top * T), "size": v2((x1 - x0) * T, (top - bottom) * T)}
        if not top_edge:
            props["top_edge"] = "false"
        self.add(Plain("Block", "StaticBody2D", "block", props))
        self.surfaces.append(Surface(x0 * T, x1 * T, -top * T, -bottom * T))
        self._path = None

    def ceiling(self, x0, x1, bottom, top=14.0):
        self.block(x0, x1, top, bottom, top_edge=False)

    def mover(self, x0, width, top, travel, period, phase=0.0, wave="sine", hold_ratio=0.6, thickness=0.5):
        """Moving platform (AnimatableBody2D + Oscillator); travel in tiles (dx, up)."""
        osc = Osc((travel[0] * T, -travel[1] * T), period, phase, wave, hold_ratio)
        el = Plain("MovingPlatform", "AnimatableBody2D", "block",
                   {"position": v2(x0 * T, -top * T), "size": v2(width * T, thickness * T)})
        el.osc = osc
        self.add(el)
        surface = Surface(x0 * T, (x0 + width) * T, -top * T, -(top - thickness) * T, osc=osc)
        self.surfaces.append(surface)
        self._path = None
        return osc

    def collapsing(self, x0, tiles, top, collapse_time, interval, warning=0.35, tile_w=1.0, thickness=0.5):
        self.add(Plain("CollapsingPath", "StaticBody2D", "collapsing_path",
                       {"position": v2(x0 * T, -top * T), "tiles": tiles, "tile_size": v2(tile_w * T, thickness * T),
                        "collapse_time": f(float(collapse_time)), "interval": f(float(interval)),
                        "warning_time": f(float(warning))}))
        for i in range(tiles):
            order = i if interval >= 0 else tiles - 1 - i
            self.surfaces.append(Surface((x0 + i * tile_w) * T, (x0 + (i + 1) * tile_w) * T, -top * T,
                                         -(top - thickness) * T, drop=collapse_time + order * abs(interval)))
        self._path = None

    # ------------------------------------------------------------ hazards --
    def gate(self, x, stops, hold=0.9, move=0.25, phase=0.0, warning=0.35, width=40.0, reach=1200.0):
        """stops: [(centre height, opening height)] in tiles above the ground line."""
        return self.add(Gate(x * T, [(-c * T, h * T) for c, h in stops], hold, move, phase, warning, width, reach))

    def arm(self, x, height, length, speed, phase=0.0, arms=2, thickness=22.0, hub=24.0):
        return self.add(Arm(x * T, -height * T, arms, length * T, thickness, speed, phase, hub))

    def crusher(self, x0, width, open_bottom, travel, period, phase=0.0, warning=0.45, slam=0.1, closed=0.35,
                ret=0.55, height=None):
        """Block `width` tiles wide whose bottom sits `open_bottom` tiles up when open; it moves by
        `travel` tiles (negative = down) to close."""
        height = height if height is not None else abs(travel)
        top = open_bottom + height
        return self.add(Crusher(x0 * T, -top * T, (width * T, height * T), (0.0, -travel * T), period, phase,
                                warning, slam, closed, ret))

    def field(self, x0, width, bottom, height, period, on_ratio, phase=0.0, warning=0.35, osc=None):
        el = Field(x0 * T, -(bottom + height) * T, (width * T, height * T), period, on_ratio, phase, warning, osc)
        el.osc = osc
        return self.add(el)

    def prism(self, x, height, span, travel, period, phase=0.0, wave="sine", hold_ratio=0.6):
        osc = Osc((0.0, -travel * T), period, phase, wave, hold_ratio)
        el = Prism(x * T, -height * T, span * T, osc)
        el.osc = osc
        return self.add(el)

    def rotor(self, x, height, radius=40.0, points=3, spin=3.0, phase=0.0, osc=None):
        el = Rotor(x * T, -height * T, radius, points, spin, phase, osc)
        el.osc = osc
        return self.add(el)

    def panel(self, x0, width, bottom, height, osc=None):
        el = Panel(x0 * T, -(bottom + height) * T, (width * T, height * T), osc)
        el.osc = osc
        return self.add(el)

    def spikes(self, x0, count, height=0.0, facing_down=False):
        return self.add(Spikes(x0 * T, -height * T, count, facing_down))

    # ------------------------------------------------------------- pickups --
    def shard(self, x, height):
        self.shards += 1
        self.add(Plain("Shard", None, None, {"position": v2(round(x * T), round(-height * T))}, instance="shard"))

    def shards_along(self, x_from, x_to, step=1.5, lift=0.3):
        """Shards on the simulated path of the route, `lift` tiles above the body."""
        x = x_from
        while x <= x_to + 1e-6:
            self.shard(x, self.at(x)["h"] / T + 0.375 + lift)
            x += step

    def checkpoint(self, x, height=0.0):
        self.checkpoints.append((x, height))
        self.add(Plain("Checkpoint", "Area2D", "checkpoint", {"position": v2(x * T, -height * T)}))

    def finish(self, x, height=0.0):
        self.finish_x = x
        self.add(Plain("FinishGate", "Area2D", "finish_gate", {"position": v2(x * T, -height * T)}))

    # --------------------------------------------------------------- route --
    def tap(self, *xs):
        for x in xs:
            self.route.append(round(x, 3))
        self._sim = None
        self._path = None

    # ---------------------------------------------------------- simulation --
    def simulate(self, route=None, start=None, stop_x=None, hazards=True):
        """Replays `route` (tap x list, tiles) tick by tick with the motor's rules.
        start: None (spawn) or (x tiles, height tiles) of a checkpoint to start from.
        Returns (ticks, death) where each tick is a dict and death is None or (cause, x)."""
        route = sorted(self.route if route is None else route)
        if start is None:
            x, y = self.spawn_px, 0.0
        else:
            x, y = start[0] * T, -start[1] * T
        t0 = (x - self.spawn_px) / self.speed
        vy, grounded, coyote, air_jumps, queued, buffer = 0.0, True, 0.0, 1, 0, 0.0
        support = None
        next_tap = 0
        while next_tap < len(route) and route[next_tap] < x / T:
            next_tap += 1
        end_x = ((self.finish_x if self.finish_x is not None else 1e9) + 1.0) * T
        if stop_x is not None:
            end_x = min(end_x, stop_x * T)
        hz = sorted(self.hazards, key=lambda h: h.x_range()[0]) if hazards else []
        out = []
        tick = 0
        while x < end_x and tick < 60 * 300:
            if next_tap < len(route) and x / T >= route[next_tap]:
                available = 2 if grounded else ((1 if coyote > 0 else 0) + air_jumps)
                if queued < available:
                    queued += 1
                else:
                    buffer = BUFFER
                next_tap += 1
            t = t0 + (tick + 1) * DT
            # begin_tick
            on_floor = grounded
            if on_floor:
                coyote, air_jumps = COYOTE, 1
            can_ground = on_floor or coyote > 0
            jump = None
            if queued > 0:
                queued -= 1
                if can_ground:
                    jump = "ground"
                elif air_jumps > 0:
                    jump = "air"
                else:
                    buffer = BUFFER
            elif buffer > 0 and can_ground:
                jump, buffer = "ground", 0.0
            if jump == "ground":
                vy, coyote = -V_JUMP, 0.0
            elif jump == "air":
                vy, air_jumps = -V_DOUBLE, air_jumps - 1
            if not on_floor:
                coyote = max(coyote - DT, 0.0)
            if buffer > 0:
                buffer = max(buffer - DT, 0.0)
            vy = min(vy + (G_UP if vy < 0 else G_DOWN) * DT * 0.5, MAX_FALL)
            # move and collide
            nx, ny = x + self.speed * DT, y + vy * DT
            landed, new_support, death = False, None, None
            if grounded and jump is None and support is not None and support.alive(t):
                sx0, sx1, stop, _ = support.rect(t)
                if nx + HALF > sx0 and nx - HALF < sx1 and abs(ny - stop) <= 12.0:
                    ny, vy, landed, new_support = stop, 0.0, True, support
            for s in self.surfaces:
                if landed or not s.alive(t):
                    continue
                sx0, sx1, stop, sbottom = s.rect(t)
                if nx + HALF <= sx0 or nx - HALF >= sx1:
                    continue
                if vy >= 0 and y <= stop + 1.0 and ny >= stop:
                    ny, landed, new_support = stop, True, s
                elif x + HALF <= sx0 + 0.5 and ny > stop and ny - 2 * HALF < sbottom:
                    if ny - stop <= LEDGE_ASSIST:
                        ny, landed, new_support = stop, True, s
                    else:
                        death = "wall"
                elif vy < 0 and y - 2 * HALF >= sbottom - 0.5 and ny - 2 * HALF < sbottom:
                    ny, vy = sbottom + 2 * HALF, 0.0
            grounded, support = landed, new_support
            if landed:
                vy = 0.0
            vy = min(vy + (G_UP if vy < 0 else G_DOWN) * DT * 0.5, MAX_FALL)
            if grounded:
                air_jumps = 1
            x, y = nx, ny
            tick += 1
            # hazards (area overlap of the hurtbox)
            clearance, hit = 1e9, None
            box = (x - HURT_HALF, y - HALF - HURT_HALF, x + HURT_HALF, y - HALF + HURT_HALF)
            for h in hz:
                lo, hi = h.x_range()
                if lo > x + 200:
                    break
                if hi < x - 200:
                    continue
                for poly in h.polys(t):
                    sep = separation(poly, box)
                    if sep < clearance:
                        clearance, hit = sep, h
            if death is None and clearance < 0.0:
                death = "hazard"
            if death is None and y > self.kill_y:
                death = "fall"
            out.append({"tick": tick, "t": t, "x": x / T, "y": y, "h": -y, "vy": vy, "grounded": grounded,
                        "jump": jump, "air_jumps": air_jumps, "clearance": clearance, "near": hit})
            if death:
                return out, (death, x / T, hit)
        return out, None

    def run(self):
        if self._sim is None:
            self._sim = self.simulate()
        return self._sim

    def path(self):
        """The route's trajectory ignoring hazards (cached)."""
        if self._path is None:
            self._path, _ = self.simulate(hazards=False)
        return self._path

    def at(self, x_tiles):
        """Simulated state when the player's centre first reaches x (hazards ignored)."""
        for s in self.path():
            if s["x"] >= x_tiles:
                return s
        return self.path()[-1]

    # --------------------------------------------------------------- fitting --
    def _clearance(self, element, ticks):
        lo, hi = element.x_range()
        worst = 1e9
        for s in ticks:
            if lo - 60 <= s["x"] * T <= hi + 60:
                box = (s["x"] * T - HURT_HALF, s["y"] - HALF - HURT_HALF,
                       s["x"] * T + HURT_HALF, s["y"] - HALF + HURT_HALF)
                for poly in element.polys(s["t"]):
                    worst = min(worst, separation(poly, box))
        return worst

    def fit_phase(self, element, osc=False, lazy=(), steps=720, report=True):
        """Sets the phase of `element` (or of its oscillator) to the value that keeps
        the intended route furthest from its hitbox, among the phases where every
        route in `lazy` (e.g. the route without the tap this obstacle asks for)
        runs into it. Returns the clearance of the intended route (px)."""
        target = element.osc if osc else element
        ticks, _ = self.simulate(hazards=False)
        others = [self.simulate(route=r, hazards=False)[0] for r in lazy]
        best, best_value = -1e9, None
        for i in range(steps):
            target.phase = i / steps
            if any(self._clearance(element, o) >= 0.0 for o in others):
                continue
            value = self._clearance(element, ticks)
            if value > best:
                best, best_value = value, i / steps
        if best_value is None:
            raise SystemExit(f"{self.key}: no phase of {element.base} at x={element.x_range()[0] / T:.1f} "
                             "blocks the lazy routes")
        target.phase = best_value
        self._sim = None
        if report:
            self.notes.append(f"{element.base} x={element.x_range()[0] / T:.1f}: clearance {best:.1f} px")
        return best

    def osc_phase(self, x_tiles, period, progress, rising=True, lead=0.0):
        """Phase of a SINE oscillator that is at `progress` (0..1 of its travel), rising
        or falling, when the player's centre is at x (optionally `lead` s earlier)."""
        c = math.acos(1.0 - 2.0 * progress) / math.tau
        if not rising:
            c = 1.0 - c
        return (c - (self.clock(x_tiles) - lead) / period) % 1.0

    def without(self, *taps):
        """The route minus the given taps (for fit_phase's lazy routes)."""
        r = list(self.route)
        for x in taps:
            r.remove(round(x, 3))
        return r

    # ------------------------------------------------------------- analysis --
    def check(self):
        ticks, death = self.run()
        if death:
            cause, x, hit = death
            raise SystemExit(f"{self.key}: simulated death '{cause}' at x={x:.2f} ({type(hit).__name__ if hit else ''})")
        return ticks

    def _start_before(self, x):
        best = None
        for cx, ch in self.checkpoints:
            if cx < x - 1.0:
                best = (cx, ch)
        return best

    def _horizon(self, j, route):
        x = route[min(j + 3, len(route) - 1)] + 1.0 if j + 3 < len(route) else (self.finish_x or 1e9)
        for cx, _ in self.checkpoints:
            if cx > route[j]:
                return min(x, cx - 0.5)
        return x

    def survives(self, route, j):
        start = self._start_before(route[j])
        _, death = self.simulate(route=route, start=start, stop_x=self._horizon(j, route))
        return death is None

    def windows(self, max_shift=24, only=None):
        """Per tap: (lo, hi) tick shifts that still survive to the horizon."""
        out = []
        step = self.tiles_per_tick()
        base = sorted(self.route)
        for j in range(len(base)):
            if only and j + 1 not in only:
                out.append(None)
                continue
            if not self.survives(base, j):
                out.append(None)
                continue
            lo = hi = 0
            for k in range(1, max_shift + 1):
                r = list(base)
                r[j] -= k * step
                if not self.survives(sorted(r), j):
                    break
                lo = -k
            for k in range(1, max_shift + 1):
                r = list(base)
                r[j] += k * step
                if not self.survives(sorted(r), j):
                    break
                hi = k
            out.append((lo, hi))
        return out

    def necessity(self):
        """Taps that can be dropped without dying before the horizon (should be none)."""
        base = sorted(self.route)
        spare = []
        for j in range(len(base)):
            r = base[:j] + base[j + 1:]
            start = self._start_before(base[j])
            _, death = self.simulate(route=r, start=start, stop_x=self._horizon(j, base))
            if death is None:
                spare.append((j + 1, base[j]))
        return spare

    def report(self, windows=True, only=None):
        ticks = self.check()
        base = sorted(self.route)
        jumps = sum(1 for s in ticks if s["jump"] == "ground")
        doubles = sum(1 for s in ticks if s["jump"] == "air")
        print(f"{self.key}: {len(ticks) / 60:.1f} s, {len(base)} taps ({jumps} jumps, {doubles} double), "
              f"{self.shards} shards, finish x={self.finish_x}")
        spare = [] if only else self.necessity()
        if spare:
            print("  spare taps (level survives without them):", spare)
        if windows:
            sizes = []
            for j, w in enumerate(self.windows(only=only)):
                if only and j + 1 not in only:
                    continue
                if w is None:
                    print(f"  T{j + 1:02d} x={base[j]:7.2f}  FAILS")
                    continue
                ms = (w[1] - w[0] + 1) * 1000 / 60
                sizes.append(ms)
                print(f"  T{j + 1:02d} x={base[j]:7.2f}  [{w[0]:+3d},{w[1]:+3d}] {ms:4.0f} ms")
            if sizes:
                srt = sorted(sizes)
                print(f"  windows: min {srt[0]:.0f} ms, median {srt[len(srt) // 2]:.0f} ms")

    # --------------------------------------------------------------- write --
    def write(self, root, scene_path, data_path):
        scripts = sorted({e.script for _, _, e in self.entries if e.script} |
                         {"oscillator" for _, _, e in self.entries if e.osc})
        ext = [("Script", "res://src/level/level.gd", "level"), ("Resource", "res://" + data_path, "data")]
        ext += [("Script", GAME_SRC + s + ".gd", s) for s in scripts]
        ext.append(("PackedScene", GAME_SRC + "shard.tscn", "shard"))
        out = ["[gd_scene format=3]\n"]
        out += [f'[ext_resource type="{a}" path="{b}" id="{c}"]' for a, b, c in ext]
        out.append("")
        node_name = "".join(p.capitalize() for p in self.key.split("_"))
        out.append(f'[node name="{node_name}" type="Node2D"]\nscript = ExtResource("level")\n'
                   f'data = ExtResource("data")\nkill_y = {f(self.kill_y)}\n')
        out.append(f'[node name="SpawnPoint" type="Marker2D" parent="."]\nposition = {v2(self.spawn_px, 0)}\n')
        written_groups = set()
        for group, name, el in self.entries:
            if group != "." and group not in written_groups:
                written_groups.add(group)
                out.append(f'[node name="{group}" type="Node2D" parent="."]\n')
            props = el.props()
            if el.instance:
                lines = [f'[node name="{name}" parent="{group}" instance=ExtResource("{el.instance}")]']
            else:
                lines = [f'[node name="{name}" type="{el.type_}" parent="{group}"]']
            if "position" in props:
                lines.append(f"position = {props['position']}")
            if el.script:
                lines.append(f'script = ExtResource("{el.script}")')
            lines += [f"{k} = {v}" for k, v in props.items() if k != "position"]
            out.append("\n".join(lines) + "\n")
            if el.osc:
                lines = [f'[node name="Oscillator" type="Node2D" parent="{group}/{name}"]',
                         'script = ExtResource("oscillator")']
                lines += [f"{k} = {v}" for k, v in el.osc.props().items()]
                out.append("\n".join(lines) + "\n")
        with open(root + scene_path, "w") as fh:
            fh.write("\n".join(out))
        with open(root + data_path, "w") as fh:
            fh.write('[gd_resource type="Resource" script_class="LevelData" format=3]\n\n'
                     '[ext_resource type="Script" path="res://src/level/level_data.gd" id="1_data"]\n\n'
                     '[resource]\nscript = ExtResource("1_data")\n'
                     f'id = &"{self.id}"\ndisplay_name = "{self.name}"\ntagline = "{self.tagline}"\n'
                     f'scene_path = "res://{scene_path}"\nspeed_scale = {f(float(self.speed_scale))}\n')
