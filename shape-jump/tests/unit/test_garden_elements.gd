extends TestCase
## World 04's obstacle kit on its own (docs/GDD.md §9D): every element builds,
## runs through its cycles on both surfaces, is a pure function of the level
## clock and the surface log (a rewind replays it exactly), keeps its side
## for a whole cycle whatever the player does mid-cycle, and warns before it
## strikes.

const T := GameConst.TILE
const CEILING := -5.0 * T

var level: Level


func before_each() -> void:
	level = Level.new()
	level.surface_latch = true
	level.gravity = GravityState.new()
	var spawn := Marker2D.new()
	spawn.name = "SpawnPoint"
	level.add_child(spawn)
	var finish := FinishGate.new()
	finish.position = Vector2(4000, 0)
	level.add_child(finish)


func after_each() -> void:
	if level:
		level.queue_free()
		level = null
	await get_tree().process_frame


func _all() -> Array[Node2D]:
	var out: Array[Node2D] = []
	var roots := RisingRoots.new()
	roots.anchor = GardenHazard.Anchor.FLOOR
	out.append(roots)
	var branch := SweepingBranch.new()
	branch.anchor = GardenHazard.Anchor.CEILING
	out.append(branch)
	var flower := ClosingFlower.new()
	flower.anchor = GardenHazard.Anchor.FLOOR
	out.append(flower)
	var wave := WaterWave.new()
	wave.anchor = GardenHazard.Anchor.FLOOR
	out.append(wave)
	var fall := Waterfall.new()
	fall.deadly = true
	fall.anchor = GardenHazard.Anchor.SKY
	out.append(fall)
	var rock := FallingRock.new()
	rock.anchor = GardenHazard.Anchor.SKY
	out.append(rock)
	var ice := FallingRock.new()
	ice.ice = true
	ice.anchor = GardenHazard.Anchor.SKY
	out.append(ice)
	var vines := HangingVines.new()
	vines.anchor = GardenHazard.Anchor.CEILING
	out.append(vines)
	out.append(LeafGlider.new())
	var ink := InkFlow.new()
	ink.anchor = GardenHazard.Anchor.FLOOR
	out.append(ink)
	var curtain := CanvasCurtain.new()
	curtain.anchor = GardenHazard.Anchor.SKY
	out.append(curtain)
	var boulder := HangingBoulder.new()
	boulder.anchor = GardenHazard.Anchor.CEILING
	out.append(boulder)
	out.append(BirdFlock.new())
	out.append(WindBurst.new())
	var weather := GardenWeather.new()
	weather.kind = GardenWeather.Kind.SNOW
	out.append(weather)
	for i in out.size():
		out[i].position = Vector2(400.0 + i * 300.0, 0.0)
		if &"ceiling" in out[i]:
			out[i].set(&"ceiling", CEILING)
	return out


func _snapshot(node: Node) -> Array:
	var out := []
	for child in node.get_children(true):
		if child is Node2D:
			out.append([child.position, child.rotation, child.scale, (child as CanvasItem).visible])
		if child is CollisionShape2D:
			out.append(child.position)
	return out


func test_every_element_builds_and_runs_on_both_surfaces() -> void:
	var elements := _all()
	for e in elements:
		level.add_child(e)
	add_child(level)
	await get_tree().process_frame
	for surface_up in [false, true]:
		level.reset_surface(surface_up)
		var t := 0.0
		while t < 8.0:
			level.rewind_to(t)
			t += 1.0 / 60.0
	for e in elements:
		assert_true(e.is_inside_tree(), "%s in the level" % e.get_class())


func test_elements_are_pure_functions_of_time_and_the_surface_log() -> void:
	for e in _all():
		level.add_child(e)
	add_child(level)
	await get_tree().process_frame
	level.reset_surface(false)
	level.rewind_to(1.0)
	level.clock = 2.3
	level.record_surface(true)
	level.rewind_to(3.7)
	var first := []
	for e in level.get_timed_elements():
		first.append(_snapshot(e))
	level.rewind_to(9.1)
	level.rewind_to(3.7)
	var second := []
	for e in level.get_timed_elements():
		second.append(_snapshot(e))
	assert_eq(second, first, "same time and log -> same positions, shapes and states")


## A floor-anchored element keeps the side it chose at the start of its
## cycle: a latch in the middle of a cycle never moves it across.
func test_a_latch_mid_cycle_never_moves_an_element_across_the_corridor() -> void:
	var flower := ClosingFlower.new()
	flower.anchor = GardenHazard.Anchor.FLOOR
	flower.ceiling = CEILING
	flower.period = 2.0
	level.add_child(flower)
	add_child(level)
	await get_tree().process_frame
	level.reset_surface(false)
	level.rewind_to(0.3)
	var heart := flower.get(&"_heart") as Node2D
	var before := heart.position
	level.clock = 0.5
	level.record_surface(true)  # The player latches to the ceiling mid-cycle.
	level.rewind_to(1.2)
	assert_eq(heart.position, before, "still on the ground for the rest of this cycle")
	level.rewind_to(2.3)  # Next cycle: it opens where the player is now.
	assert_near(heart.position.y, CEILING, 0.01, "the next cycle grows on the ceiling")


func test_surface_log_reads_like_the_level_generator() -> void:
	add_child(level)
	level.reset_surface(false)
	level.clock = 1.0
	level.record_surface(true)
	level.clock = 2.5
	level.record_surface(false)
	assert_false(level.surface_up_at(0.5))
	assert_false(level.surface_up_at(1.0), "a latch logged at t changes the floor only after t")
	assert_true(level.surface_up_at(1.0 + 1.0 / 60.0))
	assert_true(level.surface_up_at(2.5))
	assert_false(level.surface_up_at(3.0))
	level.reset_surface(true)
	assert_true(level.surface_up_at(0.0), "a respawn starts a fresh log from its surface")
	assert_true(level.get_surface_log().is_empty())


func test_wind_lifts_only_inside_its_zone_and_only_while_it_blows() -> void:
	var wind := WindBurst.new()
	wind.position = Vector2(1000.0, 0.0)
	wind.width = 400.0
	wind.lift = -800.0
	wind.period = 2.0
	wind.hold_ratio = 0.4
	level.add_child(wind)
	add_child(level)
	await get_tree().process_frame
	assert_eq(wind.wind(900.0, 1.0, false), 0.0, "before the zone")
	assert_eq(wind.wind(1500.0, 1.0, false), 0.0, "after the zone")
	var strongest := 0.0
	var calmest := -1e9
	for i in 120:
		var push := wind.wind(1200.0, i / 60.0, false)
		strongest = minf(strongest, push)
		calmest = maxf(calmest, push)
	assert_near(strongest, -800.0, 1.0, "full gust inside")
	assert_near(calmest, 0.0, 0.001, "calm between gusts")
	level.clock = 1.0
	assert_eq(level.wind_at(1200.0), wind.wind(1200.0, 1.0, false), "the level sums its zones")
