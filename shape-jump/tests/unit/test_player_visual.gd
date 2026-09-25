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
