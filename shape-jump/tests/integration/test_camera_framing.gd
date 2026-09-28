extends TestCase
## One camera, one framing (docs/GDD.md §10): measured on the running game
## in World 01, 02, 03, World 04 level 1 and level 5. The scale (zoom), the
## player's size and place on screen, the look-ahead and the floor line are
## the same everywhere; the view never turns; on the ceiling (Worlds 03 and
## 04) the band is framed the same way, mirrored top to bottom.

## (world index, level index) of every case.
const CASES: Array[Vector2i] = [Vector2i(0, 0), Vector2i(1, 0), Vector2i(2, 0), Vector2i(3, 0), Vector2i(3, 4)]

var _save := SaveSandbox.new()
var _harnesses: Array[GameHarness] = []


func before_each() -> void:
	_save.enter()


func after_each() -> void:
	for h in _harnesses:
		h.free_game()
	_harnesses.clear()
	await get_tree().physics_frame
	_save.leave()


## Plays [param case] with its route until the player has run on the wanted
## surface for half a second (hops included: the frame keeps the floor it left
## from); returns the camera's report then, measured while standing.
func _measure(case: Vector2i, want_up: bool) -> Dictionary:
	var h := GameHarness.new(self, Autoplay.route_for(case.x, case.y))
	_harnesses.append(h)
	await h.start(case.y, case.x)
	h.game.press_jump()
	var standing := 0
	for i in 60 * 90:
		await h.tick()
		if h.game.state != GameSession.State.PLAYING:
			break
		var p := h.game.player
		var on_it := h.game.gravity.up == want_up and not p.is_attaching() and (p.is_on_floor() or p.is_hopping())
		standing = standing + 1 if on_it else 0
		if standing >= 30 and p.is_on_floor():
			var report := h.game.camera.framing_report()
			print("  framing W%d L%d on the %s: %s" % [case.x + 1, case.y + 1, "ceiling" if want_up else "ground", report])
			h.free_game()
			return report
	h.free_game()
	return {}


func test_every_world_is_framed_the_same_on_the_ground() -> void:
	var reports: Array[Dictionary] = []
	for case in CASES:
		var r := await _measure(case, false)
		assert_false(r.is_empty(), "W%d L%d: ran on the ground" % [case.x + 1, case.y + 1])
		if r.is_empty():
			return
		reports.append(r)
	var first := reports[0]
	for i in reports.size():
		var r := reports[i]
		var name := "W%d L%d" % [CASES[i].x + 1, CASES[i].y + 1]
		assert_eq(r.zoom, Vector2.ONE, "%s: zoom 1" % name)
		assert_eq(r.rotation, 0.0, "%s: the view is upright" % name)
		assert_eq(r.view, first.view, "%s: the same view size" % name)
		assert_eq(r.player_px, first.player_px, "%s: the player is the same size on screen" % name)
		assert_near(r.anchor_x, 0.28, 0.003, "%s: the player at 28%% from the left" % name)
		assert_near(r.look_ahead_px, first.look_ahead_px, 3.0, "%s: the same look-ahead" % name)
		assert_true(r.floor_frac > 0.72 and r.floor_frac < 0.76,
			"%s: the floor line at %.1f%% of the height (72-76%%)" % [name, r.floor_frac * 100.0])
		assert_near(r.floor_y, first.floor_y, 12.0, "%s: floor line within 12 px of World 01's" % name)


func test_the_ceiling_is_framed_as_the_ground_mirrored_and_never_turned() -> void:
	for case in [Vector2i(2, 0), Vector2i(3, 0), Vector2i(3, 4)]:
		var name := "W%d L%d" % [case.x + 1, case.y + 1]
		var ground := await _measure(case, false)
		var ceiling := await _measure(case, true)
		assert_false(ceiling.is_empty(), "%s: ran on the ceiling" % name)
		if ceiling.is_empty() or ground.is_empty():
			continue
		assert_eq(ceiling.rotation, 0.0, "%s: the view never turns" % name)
		assert_near(ceiling.anchor_x, 0.28, 0.003, "%s: still at 28%% from the left (the way ahead on the right)" % name)
		assert_near(ceiling.floor_y, ceiling.view.y - ground.floor_y, 16.0,
			"%s: the ceiling's floor line mirrors the ground's (%.0f vs %.0f)" % [name, ceiling.floor_y, ground.floor_y])
