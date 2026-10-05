# Generates src/map/maps/showcase_v2.json. Usage: python3 tools/gen-showcase-v2.py src/map/maps/showcase_v2.json
# The map itself is plain JSON (no TypeScript); this script only computes positions from edge-to-edge gaps and anchors every
# decoration, zone, camera and checkpoint to the platforms, so re-spacing the route moves everything with it.
import json, math, sys
E = []
P = {}   # id -> (x, y, width)  (platform centre, top surface y, top width)
def ent(id, type, prefab=None, x=0, y=0, z=None, props=None, **kw):
    e = {"id": id, "type": type}
    if prefab: e["prefab"] = prefab
    pos = {"x": round(x, 2), "y": round(y, 2)}
    if z is not None: pos["z"] = z
    e["position"] = pos
    if props: e["properties"] = props
    e.update(kw)
    E.append(e); return e
KIND = {"rock_ledge":"platform","wood_ledge":"platform","ice_ledge":"platform","ruin_ledge":"platform","crystal_ledge":"platform",
        "moving_ledge":"moving","timed_ledge":"interactive","breakable_ledge":"interactive","hidden_ledge":"interactive"}
def plat(id, prefab, x, y, w, **props):
    p = {"width": w}; p.update(props)
    P[id] = (x, y, w)
    return ent(id, KIND[prefab], prefab, x, y, props=p)
def right_of(prev, gap, w, dy):
    """x centre of a platform of width w whose left edge is `gap` metres right of prev's right edge."""
    px, py, pw = P[prev]
    return px + pw / 2 + gap + w / 2, py + dy

# ───────────── gameplay (edge gaps ≤ 4.5 m, rises ≤ 4 m, widths ≥ 6: every hop has a comfortable landing window) ─────────────
# A · start + tutorial
plat("start", "rock_ledge", 0, 0, 18, thickness=9, depth=12, seed=11)
x, y = right_of("start", 3, 9, 1.5);  plat("a1", "rock_ledge", x, y, 9, thickness=4, seed=12)
x, y = right_of("a1", 3.5, 9, 2);     plat("a2", "wood_ledge", x, y, 9, seed=13)
# B · moving platform section
x, y = right_of("a2", 3.5, 9, 2);     plat("b1", "rock_ledge", x, y, 9, thickness=4, seed=14)
x, y = right_of("b1", 4, 9, 2);       plat("b_mv", "moving_ledge", x, y, 9, ampX=2, period=10, seed=15)
x, y = right_of("b_mv", 4.5, 9, 2);   plat("b2", "rock_ledge", x, y, 9, thickness=4, seed=16)
# C · slope (gentle: 1.6 m over 10 m)
sx = P["b2"][0] + 4.5 + 5; sy = P["b2"][1] + 0.8          # the low end meets the top of b2 (the wedge's origin is its centre)
ent("c_slope", "slope", "slope", sx, sy, props={"width": 10, "height": 1.6})
P["c_slope"] = (sx, P["b2"][1] + 1.6, 10)
cx, cy = sx + 5 + 3.5 + 4, P["b2"][1] + 1.6 + 1.4
plat("c1", "rock_ledge", cx, cy, 8, thickness=4, seed=17)
# D · ice
x, y = right_of("c1", 4, 9, 1.5);     plat("d1", "ice_ledge", x, y, 9, seed=18)
x, y = right_of("d1", 4.5, 9, 2);     plat("d2", "ice_ledge", x, y, 9, seed=19)
x, y = right_of("d2", 4, 9, 2);       plat("d3", "rock_ledge", x, y, 9, thickness=4, seed=20)
# E · vertical climb (zig-zag around the tower axis `tx`, 1.5 m of horizontal gap, 4 m rise per step)
x, y = right_of("d3", 3, 6.5, 3.5);   plat("e1", "rock_ledge", x, y, 6.5, thickness=3.5, seed=21)
tx = x - 4
plat("e2", "rock_ledge", tx - 4, y + 4, 6.5, thickness=3.5, seed=22)
plat("e3", "rock_ledge", tx + 4, y + 8, 6.5, thickness=3.5, seed=23)
plat("e4", "rock_ledge", tx - 4, y + 12, 6.5, thickness=3.5, seed=24)
plat("e5", "rock_ledge", tx + 4, y + 16, 6.5, thickness=3.5, seed=25)
# F · hazards
x, y = right_of("e5", 3.5, 8, 3.5);   plat("f1", "rock_ledge", x, y, 8, thickness=4, seed=26)
ent("spikes1", "hazard", "crystal_spikes", (P["e5"][0] + P["e5"][2] / 2 + P["f1"][0] - P["f1"][2] / 2) / 2, P["e5"][1] - 3.2, props={"width": 4, "height": 1.8, "count": 5, "seed": 3})
x, y = right_of("f1", 4, 8, 2.5);     plat("f2", "rock_ledge", x, y, 8, thickness=4, seed=27)
bx = (P["f1"][0] + P["f2"][0]) / 2
ent("blade1", "hazard", "rotating_blade", bx, P["f2"][1] + 9, props={"length": 7, "thickness": 0.9, "speed": 70},
    visual={"kind": "mesh", "mesh": "builtin:blade", "renderLayer": "gameplay", "castShadow": True, "meshParams": {"length": 7, "thickness": 0.9}})
x, y = right_of("f2", 4, 6, 2.5);     plat("f3", "timed_ledge", x, y, 6, thickness=2.2, period=5, duty=0.8, phase=0.0, seed=28)
x, y = right_of("f3", 4.5, 9, 2.5);   plat("g1", "rock_ledge", x, y, 9, thickness=4, seed=29)
gx, gy = P["g1"][0], P["g1"][1]
# G · secret (seal ledge + hidden bridge to the alcove): up and to the left of g1, clear of the main route
plat("s_seal", "crystal_ledge", gx + 3, gy + 6, 6, seed=30)
plat("s1", "hidden_ledge", gx + 11, gy + 10.5, 6, seed=31)
plat("s2", "hidden_ledge", gx + 19, gy + 15, 6, seed=32)
plat("s3", "hidden_ledge", gx + 11, gy + 19.5, 6, seed=33)
plat("s_alcove", "ruin_ledge", gx + 1, gy + 24, 10, thickness=3, depth=7, seed=34)
# H · final challenge (narrower, timed / moving / breakable, but gaps stay ≤ 5 m)
x, y = right_of("g1", 4.5, 7, 2.5);   plat("h1", "rock_ledge", x, y, 7, thickness=3.5, seed=35)
x, y = right_of("h1", 4.5, 6, 2.5);   plat("h2", "moving_ledge", x, y, 8, ampX=2.5, ampY=0, period=9, seed=36)
x, y = right_of("h2", 4.5, 6, 2.5);   plat("h3", "timed_ledge", x, y, 6, thickness=2.2, period=5, duty=0.8, phase=0.0, seed=37)
x, y = right_of("h3", 4.5, 6, 2.5);   plat("h4", "rock_ledge", x, y, 6, thickness=3.5, seed=38)
x, y = right_of("h4", 4.5, 6, 2.5);   plat("h5", "breakable_ledge", x, y, 6, delay=2.0, respawn=3, seed=39)
x, y = right_of("h5", 4.5, 14, 3.5);  plat("fin", "rock_ledge", x, y, 14, thickness=6, depth=12, seed=40)
MAIN = ["start","a1","a2","b1","b_mv","b2","c_slope","c1","d1","d2","d3","e1","e2","e3","e4","e5","f1","f2","f3","g1","h1","h2","h3","h4","h5","fin"]
route = [m for m in MAIN if m != "fin"]
def X(i): return P[i][0]
def Y(i): return P[i][1]

# ───────────── environment (everything is anchored to the platforms above) ─────────────
_rp = [(P[i][0], P[i][1]) for i in MAIN]
def route_y(x):
    for (x0,y0),(x1,y1) in zip(_rp, _rp[1:]):
        if x0 <= x <= x1: return y0 + (y1-y0)*(x-x0)/max(1e-6, x1-x0)
    return _rp[0][1] if x < _rp[0][0] else _rp[-1][1]
FIN_X = X("fin")
tx_axis = tx
# distant terrain islands (cliff origin = centre): tops sit 4 m below the route so sky and mountains stay visible above
isl = [-6, 27, 60, 94, 178, 208, 240, FIN_X + 14]
for i, x in enumerate(isl):
    w, h = 36 if i % 2 == 0 else 32, 15
    ent(f"terrain{i}", "decor", "cliff", x, route_y(x) - 4 - h / 2, -14.5 - (i % 3) * 1.0, props={"width": w, "height": h, "depth": 9, "terraces": 3, "seed": 100 + i})
# hero mountains + cloud banks
for i,(x,h,pr) in enumerate([(30,80,'ridge'),(X("d2"),90,'cone'),(X("g1"),90,'ridge'),(FIN_X + 12,90,'ridge')]):
    ent(f"peak{i}", "background", "background_mountain", x, route_y(x) + 22 - h, -135 - i*8, props={"width": 200 + (i % 2) * 20, "height": h, "profile": pr, "snow": True, "seed": 200+i})
for i,(x,dy,z) in enumerate([(30,22,-42),(110,22,-48),(190,25,-52),(250,-20,-56),(70,0,-60)]):
    ent(f"clouds{i}", "background", "cloud", x, route_y(x) + dy, z, props={"count": 4, "spread": 50, "seed": 300+i, "width": 18})
# trees / bushes / grass / rocks dressing the start and the early sections
ent("trees_start", "decor", "tree_cluster", 0, 0, -3.5, props={"count": 6, "spread": 17, "seed": 4, "kind": "broadleaf", "height": 4.6})
ent("pines_start", "decor", "tree_cluster", -4, 0, -6.5, props={"count": 5, "spread": 14, "seed": 5, "kind": "pine", "height": 6.5})
ent("bushes_start", "decor", "bush_cluster", 1, 0, -1, props={"count": 8, "spread": 16, "seed": 6, "size": 1.0})
ent("grass_start", "decor", "grass_patch", 1, 0, 0, props={"count": 70, "spread": 16, "seed": 7})
for k, pid in enumerate(["a1","a2","b1","b2","c1","d3","f1","g1","h1","h4"]):
    x, y, w = P[pid]
    ent(f"grass_{pid}", "decor", "grass_patch", x, y, 0, props={"count": int(w*3.2), "spread": w*0.8, "seed": 8+k})
    if pid in ("a1","b1","b2","c1","g1"):
        ent(f"bush_{pid}", "decor", "bush_cluster", x+w*0.28, y, -1, props={"count": 2, "spread": w*0.4, "seed": 28+k, "size": 0.7})
ent("rocks_a", "decor", "rock_cluster", 8, 0, -1.2, props={"count": 5, "spread": 7, "seed": 41})
ent("rocks_b", "decor", "rock_cluster", X("b2") - 3.5, Y("b2"), -1.5, props={"count": 4, "spread": 6, "seed": 42})
ent("trees_fin", "decor", "tree_cluster", FIN_X + 2, Y("fin"), -4.5, props={"count": 6, "spread": 14, "seed": 43, "kind": "broadleaf", "height": 5})
ent("grass_fin", "decor", "grass_patch", FIN_X, Y("fin"), 0, props={"count": 60, "spread": 12, "seed": 44})
ent("bush_fin", "decor", "bush_cluster", FIN_X - 6, Y("fin"), -1.5, props={"count": 5, "spread": 8, "seed": 45, "size": 1.1})
# foreground foliage at the lower screen edge (dithers away near the player)
for i, x in enumerate([4, 34, 70, 100, 124, 150, 175, 214, FIN_X - 18]):
    ent(f"fg{i}", "decor", "grass_patch", x, route_y(x) - 5, 4.5, props={"count": 14, "spread": 5, "seed": 500+i, "layer": "foreground", "height": 2.2})
# water: pond under the b1 gap, waterfall + pool in the midground
PX, PY = X("b1"), Y("b1") - 11
ent("pond", "decor", "water", PX, PY, -1.0, props={"width": 24, "depth": 5, "seed": 50},
    visual={"kind": "mesh", "mesh": "builtin:water", "renderLayer": "gameplay", "meshParams": {"w": 24, "h": 5, "depth": 5, "seed": 50}})
FX = X("b_mv") + 6
ent("fall1", "decor", "waterfall", FX, Y("b_mv") + 18, -15, props={"width": 4, "height": 26, "seed": 51})
ent("fall_pool", "decor", "water", FX, Y("b_mv") - 7, -12, props={"width": 18, "depth": 6, "seed": 52}, visual={"kind": "mesh", "mesh": "builtin:water", "renderLayer": "midground", "meshParams": {"w": 18, "depth": 6, "seed": 52}})
# ice section
for i, pid in enumerate(["d1", "d2", "d3"]):
    ent(f"ice_cry{i}", "decor", "crystal_cluster", X(pid) + 1, Y(pid), -2.5, props={"count": 6, "height": 3.2, "spread": 2.2, "seed": 60+i})
# climbing tower: cliff walls (centre-origin) flank the zig-zag; a glowing ruin stands behind it
tw_h = (Y("e5") - Y("e1")) + 28
tw_y = (Y("e1") - 8 + Y("e5") + 20) / 2
ent("tower_wall_l", "decor", "cliff", tx_axis - 11.5, tw_y, -3.2, props={"width": 6, "height": tw_h, "depth": 4, "terraces": 6, "seed": 70})
ent("tower_wall_r", "decor", "cliff", tx_axis + 11.5, tw_y + 4, -3.2, props={"width": 6, "height": tw_h, "depth": 4, "terraces": 6, "seed": 71})
ent("tower_ruin", "decor", "ancient_structure", tx_axis, Y("e5") + 2, -9, props={"kind": "tower", "width": 14, "height": 26, "seed": 72, "glow": True})
# hazard section
ent("haz_cry", "decor", "crystal_cluster", X("f2") + 2, Y("f2"), -2.5, props={"count": 7, "height": 3.5, "spread": 2.4, "seed": 80})
ent("gate_f", "decor", "ancient_structure", X("f3") + 5, Y("f3"), -7, props={"kind": "gate", "width": 11, "height": 11, "seed": 81})
# secret alcove
AX, AY = X("s_alcove"), Y("s_alcove")
ent("alc_obelisk", "decor", "ancient_structure", AX, AY, -4, props={"kind": "obelisk", "width": 4, "height": 11, "glow": True, "seed": 90})
ent("alc_pillars", "decor", "ancient_structure", AX + 4.5, AY, -3, props={"kind": "pillar", "width": 3, "height": 6, "seed": 91})
ent("alc_cry", "decor", "crystal_cluster", AX - 4, AY, -1.5, props={"count": 8, "height": 3.8, "spread": 2.6, "seed": 92})
ent("seal_cry", "decor", "crystal_cluster", X("s_seal"), Y("s_seal"), -1, props={"count": 5, "height": 2.4, "spread": 1.4, "seed": 93})
# finish
ent("fin_gate", "decor", "ancient_structure", FIN_X, Y("fin"), -4, props={"kind": "gate", "width": 12, "height": 12, "seed": 95, "glow": True})
ent("fin_arch", "decor", "ancient_structure", X("h4"), Y("h4") - 21, -22, props={"kind": "arch", "width": 18, "height": 16, "seed": 96})
# emitters (particles + positional audio)
ent("em_secret", "vfx", "vfx_emitter", X("s_seal"), Y("s_seal") + 2, 0, props={"kind": "sparkle", "rate": 2.5, "radius": 2, "power": 0.35, "color": "#7be8ff"})
ent("em_alcove", "vfx", "vfx_emitter", AX, AY + 2, 0, props={"kind": "sparkle", "rate": 3, "radius": 5, "power": 0.4, "color": "#d6a8ff"})
ent("em_ice", "vfx", "vfx_emitter", X("d1") + 7, Y("d1") + 2, 0, props={"kind": "ice", "rate": 1.4, "radius": 14, "power": 0.3, "color": "#cfeaff"})
ent("snd_wind", "audio", "audio_emitter", tx_axis, Y("e3"), 0, props={"sound": "wind", "radius": 40, "volume": 0.7})
ent("snd_chimes", "audio", "audio_emitter", AX, AY + 1, 0, props={"sound": "chimes", "radius": 28, "volume": 0.6})
ent("snd_pond", "audio", "audio_emitter", PX, PY + 2, 0, props={"sound": "water", "radius": 24, "volume": 0.4})

# ───────────── document ─────────────
def region(id, type, x, y, w, h, **kw):
    r = {"id": id, "type": type, "shape": {"kind": "box", "x": round(x,2), "y": round(y,2), "w": w, "h": h}}; r.update(kw); return r
def circ(id, x, y, r, key):
    return {"id": id, "type": "hint", "shape": {"kind": "circle", "x": round(x,2), "y": round(y,2), "r": r}, "enter": [{"op": "hint", "textKey": key}]}
cps = [("cp0","Moving planks","b2",0, 22),("cp1","Ice crossing","d3",1, 45),("cp2","Crystal gate","g1",2, 72)]
mid = lambda a, b: (X(a) + X(b)) / 2
regions = [
  circ("h_charge", -3, 2, 6, "hint_charge"), circ("h_aim", X("a1"), Y("a1") + 1.5, 5, "hint_aim"),
  circ("h_ice", X("d1"), Y("d1") + 2, 7, "hint_ice"), circ("h_hazard", X("f1") - 5, Y("f1"), 6, "hint_hazard"),
  region("pond_water", "water", PX, PY + 0.5, 24, 3.2, enter=[{"op": "audio", "id": "builtin:splash", "volume": 0.9}]),
  region("zone_ice", "fog", X("d2"), Y("d2") + 1, 44, 14, enter=[{"op": "fog", "density": 0.0055, "color": "#cfe3f5"}, {"op": "lighting", "ambient": 1.18, "color": "#eaf5ff"}]),
  region("zone_climb_cam", "camera", tx_axis, Y("e3"), 22, 30, params={"profile": {"followDistance": 24, "verticalBias": 3.0, "lookAhead": 3.0, "fov": 31}, "blend": 1.2}, priority=1),
  region("zone_climb_audio", "audio", tx_axis, Y("e3"), 22, 30, enter=[{"op": "audio", "id": "builtin:wind", "volume": 0.9}]),
  region("zone_haz_cam", "camera", mid("f1", "f3"), Y("f2") + 1, 40, 14, params={"profile": {"followDistance": 17.5, "smoothing": 0.2}, "blend": 0.9}, priority=1),
  region("zone_secret_audio", "audio", X("g1") + 10, Y("g1") + 18, 34, 24, enter=[{"op": "audio", "id": "builtin:chimes", "volume": 0.8}]),
  region("zone_secret_light", "lighting", X("g1") + 10, Y("g1") + 18, 34, 24, enter=[{"op": "lighting", "ambient": 1.25, "color": "#d6dcff"}, {"op": "fog", "density": 0.0052, "color": "#6a58b8"}]),
  region("zone_final_cam", "camera", mid("h2", "h5"), Y("h3") + 1, 44, 22, params={"profile": {"followDistance": 21, "zoom": 1.0, "lookAhead": 6.5}, "blend": 1.4}, priority=1),
  region("zone_final_light", "lighting", mid("h2", "h5"), Y("h3") + 1, 44, 22, enter=[{"op": "lighting", "sun": 2.3, "ambient": 0.95, "color": "#ffc58a"}, {"op": "fog", "density": 0.0034, "color": "#e7c9c0"}]),
  region("seal", "secret", X("s_seal"), Y("s_seal") + 2, 3.2, 2.6, once=True, enter=[
      {"op": "setFlag", "name": "secret_open", "value": True}, {"op": "reveal", "tag": "secret"},
      {"op": "vfx", "id": "checkpoint", "burst": 1}, {"op": "audio", "id": "builtin:checkpoint", "volume": 1}]),
]
checkpoints = [
  {"id": c[0], "order": c[3], "name": c[1], "region": {"kind": "box", "x": round(X(c[2]),2), "y": round(Y(c[2])+3.5,2), "w": 7, "h": 8}, "respawn": {"x": round(X(c[2])-1.2,2), "y": round(Y(c[2]),2)}, "progress": c[4]} for c in cps
] + [{"id": "cp_secret", "order": 3, "name": "Crystal alcove", "optional": True, "orderMode": "any", "region": {"kind": "box", "x": round(AX,2), "y": round(AY+3,2), "w": 9, "h": 7}, "respawn": {"x": round(AX-1.5,2), "y": round(AY,2)}}]
main = [(i, X(i), Y(i)) for i in MAIN]
sec = ["g1", "s_seal", "s1", "s2", "s3", "s_alcove"]
W_MAX = round(FIN_X + 22); H_MAX = round(Y("s_alcove") + 26)
doc = {
 "format": "pogo-summit.map", "formatVersion": 2,
 "manifest": {"id": "showcase_v2", "name": "Showcase V2", "author": "Pogo Summit", "version": "1.0.0",
   "description": "A guided tour of Map System V2: moving planks, a ramp, ice, a vertical climb, hazards, a secret route and a final challenge.",
   "difficulty": 3, "estimatedTimeSec": 120, "theme": "world_meadow", "mapSize": {"width": W_MAX + 25, "height": H_MAX + 32}, "checkpointCount": 4,
   "tags": ["showcase", "v2", "tutorial"], "requirements": {"minFormatVersion": 2, "capabilities": [], "physics": "locked-spec-1"}},
 "world": {"bounds": {"minX": -25, "maxX": W_MAX, "minY": -32, "maxY": H_MAX}, "killY": -40, "modes": {"doubleJump": False, "puzzle": False, "grapple": False}},
 "theme": {"ref": "world_meadow"},
 "spawn": {"id": "spawn", "position": {"x": -4, "y": 0}, "facing": 1},
 "finish": {"zones": [{"id": "finish", "position": {"x": round(FIN_X,2), "y": round(Y("fin") + 2.5,2)}, "shape": {"kind": "box", "w": 2.6, "h": 5}}]},
 "checkpoints": checkpoints,
 "progress": {"routes": [
    {"id": "main", "kind": "main", "points": [{"x": round(x,2), "y": round(y,2)} for _, x, y in main]},
    {"id": "secret", "kind": "secret", "points": [{"x": round(X(i),2), "y": round(Y(i),2)} for i in sec], "from": {"route": "main", "point": MAIN.index("g1")}, "to": {"route": "main", "point": MAIN.index("h1")}}]},
 "splits": {"splits": [{"id": "s0", "name": "Moving planks", "checkpoint": "cp0", "parSec": 24}, {"id": "s1", "name": "Ice crossing", "checkpoint": "cp1", "parSec": 52}, {"id": "s2", "name": "Crystal gate", "checkpoint": "cp2", "parSec": 84}], "targets": {"gold": 95, "silver": 125, "bronze": 180}},
 "entities": E,
 "regions": regions,
 "camera": {"zoom": 1, "profile": {"followDistance": 19, "fov": 30}},
 "vfx": {"maxParticles": 280},
 "audio": {"maxVoices": 8},
 "chunks": {"mode": "auto-grid", "cell": {"w": 32, "h": 32}, "defs": [], "activateRadius": 40, "loadRadius": 64, "unloadRadius": 96, "lodDistances": [24, 48, 96], "maxActive": 6},
 "metadata": {"license": "original", "credits": ["Pogo Summit"], "route": route},
}
json.dump(doc, open(sys.argv[1], "w"), indent=1)
print(len(E), "entities;", "; ".join(f"{i}=({P[i][0]:.1f},{P[i][1]:.1f},w{P[i][2]})" for i in MAIN))
