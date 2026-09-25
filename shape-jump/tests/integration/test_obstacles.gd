extends TestCase
## The World 01 obstacle language: timelines, deadliness on the real physics
## server, telegraphs, cues and fair hitboxes.

const T := GameConst.TILE

var arena: PhysicsArena
var player: Player


func before_each() -> void:
	arena = PhysicsArena.new()
	add_child(arena)


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


## A long floor and a running player; returns the recorded death causes.
func _run_into(hazard: Node2D, ticks := 90) -> Array[StringName]:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	arena.add_child(hazard)
	player = arena.spawn_player(Vector2(0, 0))
	var causes: Array[StringName] = []
	player.died.connect(func(cause: StringName) -> void: causes.append(cause))
	await arena.ticks(ticks)
	return causes


func _reset_arena() -> void:
	arena.queue_free()
	await get_tree().physics_frame
	arena = PhysicsArena.new()
	add_child(arena)


## Everything a timed element changes, to compare states at equal times.
func _state_of(node: Node) -> Array:
	var out := [node.get(&"position"), node.get(&"rotation"), node.get(&"self_modulate")]
	for child in node.get_children(true):
		if child is CollisionShape2D:
			out.append([child.position, child.rotation, child.disabled])
		elif child is Node2D:
			out.append([child.position, child.rotation, child.self_modulate])
	if node is Oscillator:
		out.append([node.get_parent().position, node.get_parent().self_modulate])
	return out


# --- Gate ------------------------------------------------------------------

func _pulse_gate() -> Gate:
	var gate := Gate.new()
	gate.stops = PackedVector2Array([Vector2(-80, 160), Vector2(-260, 140)])
	gate.hold_time = 0.8
	gate.move_time = 0.2
	gate.warning_time = 0.3
	return gate


func test_gate_opening_steps_through_its_stops() -> void:
	var gate := _pulse_gate()
	arena.add_child(gate)
	assert_eq(gate.opening_at(0.4), Vector2(-80, 160), "holding at the first stop")
	assert_eq(gate.opening_at(1.4), Vector2(-260, 140), "holding at the second stop")
	var mid := gate.opening_at(0.9)
	assert_true(mid.x < -80.0 and mid.x > -260.0, "sliding between them")
	assert_eq(gate.opening_at(2.4), gate.opening_at(0.4), "the cycle repeats")
	assert_eq(gate.upcoming_stop(0.3), -1, "no telegraph early in a hold")
	assert_eq(gate.upcoming_stop(0.6), 1, "ghost of the next opening before the move")
	gate.apply_time(0.4)
	assert_eq(gate.get_opening_span(), Vector2(-160, 0), "opening from 160 px up to the floor")


func test_gate_lets_the_player_through_its_opening_only() -> void:
	var open := _pulse_gate()
	open.position = Vector2(T * 5, 0)
	var causes := await _run_into(open)
	assert_eq(causes, [] as Array[StringName], "ran through a floor-level opening")
	await _reset_arena()
	var high := _pulse_gate()
	high.position = Vector2(T * 5, 0)
	high.phase = 0.5  # Opening held high: the lower slab blocks the floor.
	causes = await _run_into(high)
	assert_eq(causes, [&"hazard"] as Array[StringName], "the lower slab kills")


# --- Rotating arm ----------------------------------------------------------

func test_rotating_arm_angle_is_a_pure_function_of_time() -> void:
	var arm := RotatingArm.new()
	arm.speed = 2.0
	arm.phase = 0.25
	arena.add_child(arm)
	arm.apply_time(1.5)
	assert_near(arm.rotation, fposmod(0.25 * TAU + 3.0, TAU), 0.0001)
	var before := _state_of(arm)
	arm.apply_time(7.0)
	arm.apply_time(1.5)
	assert_eq(_state_of(arm), before)


func test_rotating_arm_kills_in_its_path_and_its_gap_lets_through() -> void:
	# Hub 3 tiles up; a single arm reaching the floor when pointing down.
	for pointing_down in [true, false]:
		await _reset_arena()
		var arm := RotatingArm.new()
		arm.position = Vector2(T * 5, -T * 3)
		arm.arms = 1
		arm.length = T * 3 + 24.0
		arm.speed = 0.0
		arm.phase = 0.25 if pointing_down else 0.75
		var causes := await _run_into(arm)
		arm.apply_time(0.0)
		if pointing_down:
			assert_eq(causes, [&"hazard"] as Array[StringName], "the arm sweeps the floor")
		else:
			assert_eq(causes, [] as Array[StringName], "arm up: the way is clear")


# --- Crush block -----------------------------------------------------------

func _crusher() -> CrushBlock:
	var crusher := CrushBlock.new()
	crusher.size = Vector2(T * 2, T * 3)
	crusher.travel = Vector2(0, T * 3)
	crusher.period = 2.0
	crusher.warning_time = 0.4
	crusher.slam_time = 0.1
	crusher.closed_time = 0.3
	crusher.return_time = 0.5
	return crusher


func test_crush_block_timeline() -> void:
	var crusher := _crusher()
	arena.add_child(crusher)
	assert_near(crusher.get_open_time(), 0.7, 0.0001)
	var phases := [crusher.phase_at(0.3), crusher.phase_at(0.9), crusher.phase_at(1.15),
		crusher.phase_at(1.3), crusher.phase_at(1.8)]
	assert_eq(phases, [CrushBlock.Phase.OPEN, CrushBlock.Phase.ARMING, CrushBlock.Phase.SLAM,
		CrushBlock.Phase.CLOSED, CrushBlock.Phase.RETURN])
	assert_eq(crusher.closure_at(0.3), 0.0, "open")
	assert_eq(crusher.closure_at(1.3), 1.0, "shut")
	assert_true(crusher.closure_at(1.12) < 0.5, "the slam accelerates")


func test_crush_block_kills_when_shut_and_lets_through_when_open() -> void:
	for shut in [true, false]:
		await _reset_arena()
		var crusher := _crusher()
		crusher.position = Vector2(T * 5, -T * 6)  # Open: its bottom 3 tiles above the floor.
		var causes := await _run_into(crusher, 1)
		crusher.apply_time(1.3 if shut else 0.2)
		await arena.ticks(90)
		if shut:
			assert_eq(causes, [&"hazard"] as Array[StringName], "slammed onto the floor")
		else:
			assert_eq(causes, [] as Array[StringName], "passed under it while open")


# --- Energy field ----------------------------------------------------------

func test_energy_field_is_deadly_only_while_on() -> void:
	var states := {}
	for when in [0.2, 1.3, 1.8]:  # on, off, warning (period 2, on 0.5, warning 0.35)
		await _reset_arena()
		var field := EnergyField.new()
		field.position = Vector2(T * 5, -T * 3)
		field.size = Vector2(T, T * 3)
		field.period = 2.0
		field.on_ratio = 0.5
		field.warning_time = 0.35
		var causes := await _run_into(field, 1)
		field.apply_time(when)
		states[when] = field.state_at(when)
		await arena.ticks(60)
		assert_eq(causes.size(), 1 if when == 0.2 else 0, "deadly only when on (t=%.1f)" % when)
	assert_eq(states, {0.2: EnergyField.State.ON, 1.3: EnergyField.State.OFF, 1.8: EnergyField.State.WARNING})


# --- Collapsing path -------------------------------------------------------

func test_collapsing_path_drops_tile_by_tile_and_rewinds() -> void:
	var path := CollapsingPath.new()
	path.tiles = 4
	path.collapse_time = 1.0
	path.interval = 0.2
	arena.add_child(path)
	path.apply_time(1.25)
	assert_eq([path.is_tile_solid(0), path.is_tile_solid(1), path.is_tile_solid(2)], [false, false, true])
	path.apply_time(0.5)
	assert_true(path.is_tile_solid(0) and path.is_tile_solid(3), "rewound: all back")
	path.interval = -0.2
	path.apply_time(1.1)
	assert_eq([path.is_tile_solid(0), path.is_tile_solid(3)], [true, false], "negative interval: far end first")


func test_player_falls_when_the_tile_under_it_drops() -> void:
	var path := CollapsingPath.new()
	path.position = Vector2(-T * 2, 0)
	path.tiles = 4
	path.collapse_time = 1.0
	path.interval = 0.0
	arena.add_child(path)
	path.apply_time(0.0)
	player = arena.spawn_player(Vector2(0, 0), false)
	await arena.ticks(5)
	assert_true(player.is_on_floor(), "standing on the path")
	path.apply_time(1.1)
	await arena.ticks(15)
	assert_false(player.is_on_floor(), "the path fell away")
	assert_true(player.get_feet_position().y > 20.0)


# --- Rotor, beam, panel, oscillator ----------------------------------------

func test_rotor_hitbox_turns_with_the_drawing() -> void:
	var rotor := Rotor.new()
	rotor.points = 3
	rotor.spin = 1.0
	arena.add_child(rotor)
	rotor.apply_time(0.5)
	assert_near(rotor.rotation, 0.5, 0.0001, "angle = spin * t")
	var causes := await _run_into(_placed(Rotor.new(), Vector2(T * 5, -30)))
	assert_eq(causes, [&"hazard"] as Array[StringName], "a rotor on the path kills")


func test_prism_beam_kills_on_contact() -> void:
	var beam := PrismBeam.new()
	beam.span = T * 3
	var causes := await _run_into(_placed(beam, Vector2(T * 6, -24)))
	assert_eq(causes, [&"hazard"] as Array[StringName])


func test_wall_panel_is_a_deadly_wall() -> void:
	var panel := WallPanel.new()
	panel.size = Vector2(48, T * 3)
	var causes := await _run_into(_placed(panel, Vector2(T * 5, -T * 3)))
	assert_eq(causes, [&"hazard"] as Array[StringName])


func test_oscillator_steps_hold_move_and_flash_before_moving() -> void:
	var holder := Node2D.new()
	arena.add_child(holder)
	var osc := Oscillator.new()
	osc.wave = Oscillator.Wave.STEPS
	osc.travel = Vector2(0, -128)
	osc.period = 4.0
	osc.hold_ratio = 0.5  # Hold 1 s at each end, move 1 s each way.
	osc.warning_time = 0.3
	holder.add_child(osc)
	assert_eq(osc.progress_at(0.5), 0.0, "holding at the start")
	assert_near(osc.progress_at(1.5), 0.5, 0.001, "half way out")
	assert_eq(osc.progress_at(2.5), 1.0, "holding at the far end")
	assert_true(osc.is_about_to_move(0.8), "warning before the move out")
	assert_true(osc.is_about_to_move(2.8), "warning before the move back")
	assert_false(osc.is_about_to_move(0.3) or osc.is_about_to_move(1.5), "quiet otherwise")


func _placed(node: Node2D, at: Vector2) -> Node2D:
	node.position = at
	return node


# --- Rules for every obstacle ----------------------------------------------

func test_every_new_element_is_a_pure_function_of_time() -> void:
	var elements: Array[Node] = [_pulse_gate(), RotatingArm.new(), _crusher(), EnergyField.new(),
		CollapsingPath.new(), Rotor.new()]
	var carrier := PrismBeam.new()
	var osc := Oscillator.new()
	osc.wave = Oscillator.Wave.STEPS
	carrier.add_child(osc)
	for element in elements:
		arena.add_child(element)
	arena.add_child(carrier)
	elements.append(osc)
	for element in elements:
		element.apply_time(3.7)
		var first := _state_of(element)
		element.apply_time(11.2)
		element.apply_time(3.7)
		assert_eq(_state_of(element), first, "%s: same time, same state" % element.get_script().get_global_name())


func test_hitboxes_are_never_larger_than_the_drawings() -> void:
	var gate := _pulse_gate()
	var crusher := _crusher()
	var field := EnergyField.new()
	var panel := WallPanel.new()
	var arm := RotatingArm.new()
	var beam := PrismBeam.new()
	for node in [gate, crusher, field, panel, arm, beam]:
		arena.add_child(node)
	var slab: Vector2 = _shape_sizes(gate)[0]
	assert_true(slab.x < gate.width and slab.y < gate.reach, "gate slabs")
	assert_true(_shape_sizes(crusher)[0] < crusher.size, "crush block")
	assert_true(_shape_sizes(field)[0] < field.size, "energy field")
	assert_true(_shape_sizes(panel)[0] < panel.size, "wall panel")
	var bar: Vector2 = _shape_sizes(arm)[0]
	assert_true(bar.x < arm.length and bar.y < arm.thickness, "arm")
	var ray: Vector2 = _shape_sizes(beam)[0]
	assert_true(ray.x <= beam.span and ray.y < beam.thickness, "beam")
	var rotor := Rotor.new()
	arena.add_child(rotor)
	for p in rotor.get_polygon(rotor.hitbox_scale):
		assert_true(p.length() < rotor.radius, "rotor hitbox inside the blade")


## Sizes of the rectangle hitboxes a hazard generated, in creation order.
func _shape_sizes(hazard: Node) -> Array[Vector2]:
	var sizes: Array[Vector2] = []
	for child in hazard.get_children(true):
		if child is CollisionShape2D and child.shape is RectangleShape2D:
			sizes.append((child.shape as RectangleShape2D).size)
	return sizes


# --- Cues --------------------------------------------------------------------

func test_crush_block_cues_warning_and_slam_in_play_but_not_on_rewind() -> void:
	var level := Level.new()
	var spawn := Marker2D.new()
	spawn.name = "SpawnPoint"
	level.add_child(spawn)
	level.add_child(FinishGate.new())
	var crusher := _crusher()
	level.add_child(crusher)
	arena.add_child(level)
	var cues: Array[StringName] = []
	level.obstacle_cued.connect(func(kind: StringName, _at: Vector2) -> void: cues.append(kind))
	level.running = true
	await arena.ticks(125)  # Just over one 2 s cycle.
	assert_eq(cues, [&"warning", &"slam"] as Array[StringName], "one warning, one slam per cycle")
	level.running = false
	cues.clear()
	level.rewind_to(1.3)  # Straight into the shut phase: a jump in time, not play.
	level.rewind_to(0.9)
	assert_eq(cues, [] as Array[StringName], "rewinds are silent")
