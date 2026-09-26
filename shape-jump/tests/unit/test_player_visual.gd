extends TestCase
## The procedural animation must stay sane at any frame rate.

const PLAYER_SCENE := preload("res://src/player/player.tscn")

var player: Player
var visual: PlayerVisual


func before_each() -> void:
	player = PLAYER_SCENE.instantiate()
	add_child(player)
	visual = player.get_node("Visual")


func after_each() -> void:
	player.queue_free()
	await get_tree().process_frame


func test_squash_survives_frame_hitches() -> void:
	player.landed.emit(1400.0)  # Hardest landing squash.
	for delta in [0.25, 0.5, 1.0, 0.3, 0.02]:  # App resume, GC stalls...
		visual._process(delta)
		var squash := visual.get_squash()
		assert_true(squash.x > 0.3 and squash.x < 2.0 and squash.y > 0.3 and squash.y < 2.0,
			"squash stays bounded after a %.2f s frame (got %s)" % [delta, squash])


func test_squash_settles_back_to_rest_at_low_and_high_frame_rates() -> void:
	for fps in [30.0, 60.0, 144.0]:
		player.landed.emit(1100.0)
		for i in int(fps * 1.5):
			visual._process(1.0 / fps)
		assert_true(visual.get_squash().distance_to(Vector2.ONE) < 0.01, "settled at %d fps" % fps)


func test_world_02_entity_collapses_on_death_and_returns_on_respawn() -> void:
	visual.void_style = true
	player.die(&"test")
	assert_true(visual.visible, "the shell stays on screen while the void collapses")
	for i in 10:
		visual._process(visual.void_death_time / 10.0 + 0.001)
	assert_false(visual.visible, "gone once the collapse is over")
	player.respawn_at(Vector2(0, 0), false)
	assert_true(visual.visible, "back at the respawn")


func test_world_01_core_hides_at_once_on_death() -> void:
	visual.void_style = false
	player.die(&"test")
	assert_false(visual.visible, "the core shatters (PlayerFx) and is hidden at once")


func test_world_02_entity_draws_at_any_frame_rate() -> void:
	visual.void_style = true
	for delta in [1.0 / 144.0, 1.0 / 60.0, 1.0 / 20.0, 0.5]:
		player.jumped.emit()
		player.double_jumped.emit()
		visual._process(delta)
		visual.queue_redraw()
		await get_tree().process_frame
	assert_true(visual._danger >= 0.0 and visual._danger <= 1.0, "danger stays within 0..1")
