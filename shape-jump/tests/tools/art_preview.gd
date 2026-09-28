extends Node2D
## QA tool: every obstacle and ledge of one world side by side, rendered and
## saved as a picture (the organic art review, docs/GDD.md §13B).
##   godot --path . --rendering-driver opengl3 res://tests/tools/art_preview.tscn -- --theme=red --out=/tmp/red.png
## Themes: red (World 01), mono (World 02), galaxy (World 03), garden (World 04).

const E := "res://src/level/elements/"


func _ready() -> void:
	var theme := StringName(Autoplay.option("--theme")) if Autoplay.option("--theme") != "" else &"red"
	var out := Autoplay.option("--out")
	Palette.use(theme)
	RenderingServer.set_default_clear_color(Palette.SKY_TOP.lerp(Palette.SKY_HORIZON, 0.35))
	var camera := Camera2D.new()
	camera.position = Vector2(640, 360)
	add_child(camera)
	match theme:
		&"mono":
			_world_02()
		&"galaxy":
			_world_03()
		&"garden":
			_world_04()
		_:
			_world_01()
	for i in 12:
		await get_tree().process_frame
	if out != "":
		get_viewport().get_texture().get_image().save_png(out)
	get_tree().quit()


func _add(script: String, pos: Vector2, props := {}, body := "") -> Node2D:
	var node: Node2D
	match body:
		"static":
			node = StaticBody2D.new()
		"area":
			node = Area2D.new()
		_:
			node = Node2D.new()
	node.set_script(load(E + script + ".gd"))
	node.position = pos
	for key in props:
		node.set(key, props[key])
	add_child(node)
	return node


func _time(t: float) -> void:
	for node in get_children():
		if node.has_method(&"apply_time"):
			node.apply_time(t)


func _world_01() -> void:
	_add("block", Vector2(-20, 560), {"size": Vector2(560, 300)}, "static")
	_add("block", Vector2(700, 560), {"size": Vector2(620, 300)}, "static")
	_add("block", Vector2(420, 420), {"size": Vector2(192, 32)}, "static")
	_add("block", Vector2(1150, 368), {"size": Vector2(64, 192)}, "static")
	_add("gate", Vector2(120, 560), {"width": 40.0, "reach": 600.0, "stops": PackedVector2Array([Vector2(-150, 150)])}, "area")
	_add("crush_block", Vector2(230, 150), {"size": Vector2(128, 160), "travel": Vector2(0, 160)}, "area")
	_add("energy_field", Vector2(420, 250), {"size": Vector2(64, 160)}, "area")
	_add("prism_beam", Vector2(620, 300), {"span": 220.0}, "area")
	_add("rotating_arm", Vector2(880, 360), {"length": 130.0}, "area")
	_add("rotor", Vector2(1060, 200), {"radius": 44.0}, "area")
	_add("wall_panel", Vector2(1240, 200), {"size": Vector2(40, 160)}, "area")
	_add("collapsing_path", Vector2(620, 480), {"tiles": 4}, "static")
	_add("checkpoint", Vector2(760, 560), {}, "area")
	_time(0.9)


func _world_02() -> void:
	_add("block", Vector2(-20, 560), {"size": Vector2(420, 300)}, "static")
	_add("shadow_block", Vector2(400, 560), {"size": Vector2(256, 300)})
	_add("block", Vector2(656, 560), {"size": Vector2(700, 300)}, "static")
	_add("block", Vector2(470, 400), {"size": Vector2(160, 32)}, "static")
	_add("black_column", Vector2(60, 300), {"size": Vector2(64, 260)}, "area")
	_add("black_column", Vector2(60, -100), {"size": Vector2(64, 280)}, "area")
	_add("orbit_ring", Vector2(300, 300), {"radius": 110.0}, "area")
	_add("maze_panel", Vector2(560, 220), {"length": 200.0}, "area")
	_add("mirror_wall", Vector2(760, 560), {"center_y": -200.0, "max_gap": 150.0}, "area")
	_add("binary_gate", Vector2(930, 560), {}, "area")
	_add("shadow_chaser", Vector2(0, 560), {"spawn_x": -420.0, "lag": 200.0, "t_end": 100.0}, "area")
	_add("block", Vector2(1090, 368), {"size": Vector2(64, 192)}, "static")
	_add("checkpoint", Vector2(1220, 560), {}, "area")
	_time(1.1)


func _world_03() -> void:
	var g := "galaxy/"
	_add(g + "galaxy_block", Vector2(-20, 560), {"size": Vector2(600, 96)}, "static")
	_add(g + "galaxy_block", Vector2(700, 560), {"size": Vector2(620, 96)}, "static")
	_add(g + "galaxy_block", Vector2(-20, 80), {"size": Vector2(1320, 96), "top_edge": false, "bottom_edge": true}, "static")
	_add(g + "galaxy_block", Vector2(430, 330), {"size": Vector2(192, 40), "bottom_edge": true}, "static")
	_add(g + "gravity_mine", Vector2(120, 560), {"ceiling": -384.0}, "area")
	_add(g + "ceiling_trap", Vector2(220, 560), {"width": 128.0}, "area")
	_add(g + "dual_hazard", Vector2(420, 560), {"ceiling": -384.0}, "area")
	_add(g + "orbital_hazard", Vector2(760, 380), {"radius": 80.0}, "area")
	_add(g + "falling_asteroid", Vector2(980, 560), {"ceiling": -384.0}, "area")
	_add(g + "gravity_echo", Vector2(1060, 496), {"size": Vector2(128, 64)})
	_add(g + "gravity_gate", Vector2(1220, 560), {}, "area")
	_add(g + "gravity_lens", Vector2(640, 250), {})
	_add("checkpoint", Vector2(600, 560), {}, "area")
	_time(0.7)


func _world_04() -> void:
	RenderingServer.set_default_clear_color(Color("9cc8f5"))
	var g := "garden/"
	var c := -333.0  # A 5.2-tile corridor: the ceiling's underside.
	_add(g + "garden_block", Vector2(-20, 560), {"size": Vector2(560, 96)}, "static")
	_add(g + "garden_block", Vector2(700, 560), {"size": Vector2(620, 96)}, "static")
	_add(g + "garden_block", Vector2(-20, 560 + c - 96), {"size": Vector2(700, 96), "top_edge": false, "bottom_edge": true}, "static")
	_add(g + "garden_block", Vector2(760, 560 + c - 96), {"size": Vector2(560, 96), "top_edge": false, "bottom_edge": true,
		"latchable": false}, "static")
	var h := {"ceiling": c}
	_add(g + "rising_roots", Vector2(60, 560), h.merged({"width": 96.0, "reach": 110.0}), "area")
	_add(g + "closing_flower", Vector2(220, 560), h.merged({"hold_ratio": 0.2}), "area")
	_add(g + "water_wave", Vector2(430, 560), h.merged({"run": 60.0}), "area")
	_add(g + "waterfall", Vector2(620, 560), h.merged({"width": 70.0, "length": 200.0, "deadly": true, "anchor": 1}), "area")
	_add(g + "sweeping_branch", Vector2(760, 560), h.merged({"length": 160.0, "anchor": 1}), "area")
	_add(g + "falling_rock", Vector2(880, 560), h.merged({}), "area")
	_add(g + "hanging_vines", Vector2(960, 560), h.merged({"width": 64.0, "long": 150.0, "anchor": 1}), "area")
	_add(g + "leaf_glider", Vector2(1080, 420), {}, "area")
	_add(g + "ink_flow", Vector2(1260, 560), h.merged({"reach": 150.0}), "area")
	_add(g + "canvas_curtain", Vector2(300, 560), h.merged({"width": 70.0, "long": 150.0, "anchor": 1}), "area")
	_add(g + "hanging_boulder", Vector2(520, 560), h.merged({"rope": 130.0, "anchor": 1}), "area")
	_add(g + "bird_flock", Vector2(1160, 560), {"middle": -170.0, "sweep": 40.0}, "area")
	_add("checkpoint", Vector2(20, 560), {}, "area")
	var t := Autoplay.option("--time")
	_time(float(t) if t != "" else 0.55)
