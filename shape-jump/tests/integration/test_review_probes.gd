extends TestCase
## Probes written during the full code review (scenarios that normal play
## does not reach). Kept as regression tests.

var h: GameHarness
var _save := SaveSandbox.new()


func before_each() -> void:
	_save.enter()
	h = GameHarness.new(self, Level01Route.TAPS)
	await h.start()


func after_each() -> void:
	get_tree().paused = false
	if h:
		h.free_game()
	await get_tree().physics_frame
	_save.leave()


func _moving_saw(at: Vector2) -> Saw:
	var saw := Saw.new()
	saw.position = at
	var osc := Oscillator.new()
	osc.travel = Vector2(0, -100)
	osc.period = 1.0
	saw.add_child(osc)
	return saw


func test_obstacles_spawned_and_freed_at_runtime() -> void:
	h.game.press_jump()
	await h.run_ticks(5)
	var level := h.game.level
	for i in 30:
		var saw := _moving_saw(Vector2(9000 + i * 10, -300))
		level.add_child(saw)
		var start_y := saw.position.y
		await h.run_ticks(8)
		assert_true(absf(saw.position.y - start_y) > 1.0, "a runtime-spawned oscillator moves (round %d)" % i)
		saw.queue_free()
		await h.run_ticks(2)
	assert_false(h.game.player.is_dead())


func test_freeing_a_level_obstacle_mid_run() -> void:
	h.game.press_jump()
	await h.run_ticks(5)
	var level := h.game.level
	for node in level.get_timed_elements().duplicate():
		var element: Node = node.get_parent() if node is Oscillator else node
		element.queue_free()
		break
	for shard in level.find_children("*", "Area2D"):
		if shard is Shard:
			shard.queue_free()
			break
	await h.run_ticks(30)
	h.game.player.die(&"test")  # Forces a rewind over the freed elements.
	await h.run_until(h.is_state(GameSession.State.PLAYING))
	assert_false(h.game.player.is_dead())


## Random but reproducible sequences of every state-changing call, checking
## the invariants that tie the state machine to the tree after each step.
func test_rapid_state_transitions_keep_invariants() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 20260925
	var game := h.game
	for step in 400:
		match rng.randi_range(0, 7):
			0, 1: game.press_jump()
			2: game.pause()
			3: game.resume()
			4: game.restart_level()
			5: game.player.die(&"fuzz")
			6: game.get_viewport().push_input(_tap_event(rng.randi_range(0, 2)))
			7: pass
		await h.run_ticks(rng.randi_range(0, 12))
		var s := game.state
		var ctx := "step %d state %s" % [step, GameSession.State.keys()[s]]
		assert_eq(get_tree().paused, s == GameSession.State.PAUSED, "tree paused iff PAUSED (%s)" % ctx)
		assert_eq(game.pause_menu.visible, s == GameSession.State.PAUSED, "pause menu iff PAUSED (%s)" % ctx)
		if s == GameSession.State.PLAYING or s == GameSession.State.READY:
			assert_false(game.player.is_dead(), "alive while %s" % ctx)
		if s == GameSession.State.DYING:
			assert_true(game.player.is_dead(), "dead while %s" % ctx)
		assert_eq(game.level.running, s in [GameSession.State.PLAYING, GameSession.State.DYING, GameSession.State.COMPLETE], "clock runs iff playing (%s)" % ctx)
		if not failures.is_empty():
			return


func _tap_event(index: int) -> InputEvent:
	var touch := InputEventScreenTouch.new()
	touch.index = index
	touch.pressed = true
	touch.position = Vector2(640, 400)
	return touch


## Pausing and resuming must be invisible to the simulation.
func test_pause_resume_is_transparent_to_the_simulation() -> void:
	h.free_game()
	var reference := await _finish_run([])
	var paused := await _finish_run([90, 400, 800, 1300, 1700])
	assert_eq(paused, reference, "same finish with pauses in the middle")


func _finish_run(pause_at_ticks: Array) -> Dictionary:
	var run := GameHarness.new(self, Level01Route.TAPS)
	await run.start()
	run.game.press_jump()
	var playing_ticks := 0
	while run.game.state != GameSession.State.COMPLETE and run.ticks < 60 * 90:
		if run.game.state == GameSession.State.PLAYING:
			playing_ticks += 1
			if playing_ticks in pause_at_ticks:
				run.game.pause()
				await run.run_ticks(37)
				run.game.resume()
		await run.tick()
	var result := {"clock": snappedf(run.game.level.clock, 0.0001), "score": run.game.score.get_score(),
		"deaths": run.deaths.size(), "x": snappedf(run.game.player.global_position.x, 0.01)}
	run.free_game()
	await get_tree().physics_frame
	return result


func test_restarting_many_times_does_not_leak() -> void:
	h.game.press_jump()
	await h.run_ticks(60)
	h.game.restart_level()
	await h.run_ticks(5)
	var nodes := Performance.get_monitor(Performance.OBJECT_NODE_COUNT)
	var objects := Performance.get_monitor(Performance.OBJECT_COUNT)
	var orphans := Performance.get_monitor(Performance.OBJECT_ORPHAN_NODE_COUNT)
	for i in 40:
		h.game.press_jump()
		await h.run_ticks(20)
		h.game.restart_level()
		await h.run_ticks(5)
	assert_eq(Performance.get_monitor(Performance.OBJECT_NODE_COUNT), nodes, "node count stable")
	assert_eq(Performance.get_monitor(Performance.OBJECT_ORPHAN_NODE_COUNT), orphans, "no orphan nodes")
	assert_true(Performance.get_monitor(Performance.OBJECT_COUNT) - objects < 50, "object count stable (%d -> %d)" % [objects, Performance.get_monitor(Performance.OBJECT_COUNT)])
