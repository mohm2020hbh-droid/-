extends Node

## Proves, through the real navigation mechanism (Game.goto ->
## change_scene_to_file), that pressing the Next Stage button on the result
## screen actually takes the player to the next puzzle — not just that the
## right signal fires, but that a real Button node's "pressed" signal drives
## the whole chain through to a new scene being current.
##
## change_scene_to_file() replaces the tree's current_scene. Because this
## .tscn is the one passed on the command line, Godot's own startup promotes
## this script's root node TO current_scene before _ready() ever runs — so
## the very first real navigation in this test (into stage 1) would free
## `self` mid-coroutine, before any of the rest of the test could run.
## The fix: hand the current_scene slot to a disposable placeholder node
## first, so every later change_scene_to_file() only ever touches the
## placeholder's replacement, never this node. `self` then survives the
## entire test as an ordinary, undisturbed child of the tree root.
var _failures := 0

func _ready() -> void:
	var dir := DirAccess.open("user://")
	dir.remove("profile.json")
	dir.remove("profile.backup.json")
	SaveManager.data = SaveManager.default_data()
	SaveManager.data["language"] = "en"
	SaveManager.data["seen_intro"] = true
	Loc.set_locale("en")

	var tree := get_tree()
	# The engine's own startup is still mid-way through adding this node's
	# tree when _ready() runs, so any add_child()/current_scene reassignment
	# attempted synchronously here races it ("Parent node is busy setting up
	# children"). Waiting a frame lets that settle first.
	await tree.process_frame
	var placeholder := Node.new()
	tree.root.add_child(placeholder)
	tree.current_scene = placeholder

	Game.pending_daily = false
	Game.pending_stage = 1
	# Deferred, same as Game.goto(): calling this synchronously from _ready()
	# races the SceneTree's own node-adding pass and triggers "Parent node is
	# busy adding/removing children".
	tree.change_scene_to_file.call_deferred(Game.SCENES["puzzle"])
	await tree.process_frame
	await tree.process_frame
	await tree.process_frame

	var screen := tree.current_scene
	_expect(screen != null and screen.get("session") != null,
			"stage 1's real scene is current and its session started")
	if screen == null:
		_finish(tree)
		return

	var puzzle: PuzzleDefinition = screen.puzzle
	screen.session.tap(puzzle.solution.target)
	_expect(screen.session.finished, "stage 1 solves through the real scene")

	# Wait out the same reveal delay the real screen uses before it shows
	# the result overlay.
	await tree.create_timer(1.0).timeout
	await tree.process_frame

	var next_button: Node = _find_by_name(tree.current_scene, "NextStageButton")
	_expect(next_button != null, "the Next Stage button exists on the result overlay")
	if next_button == null:
		_finish(tree)
		return

	_expect(next_button is Button and next_button.custom_minimum_size.y >= 100.0,
			"the Next Stage button is the large primary size (>=100px tall)")

	# The one check that actually catches a real bug found this way: a
	# button can be correctly wired (pressed -> next_requested -> Game.
	# start_stage) and still be completely untappable on a real screen if
	# its parent overlay rendered at size (0, 0) — which happened here once,
	# silently, with every signal-level test still green, because emitting
	# a Button's "pressed" signal directly bypasses hit-testing entirely and
	# proves nothing about whether a finger could ever have reached it.
	var button_rect: Rect2 = next_button.get_global_rect()
	_expect(button_rect.size.x > 100.0 and button_rect.size.y >= 100.0,
			"the Next Stage button has a real, non-zero on-screen hit area (%s)"
					% button_rect)
	_expect(button_rect.position.x >= 0.0 and button_rect.end.x <= 720.0
			and button_rect.position.y >= 0.0 and button_rect.end.y <= 1280.0,
			"the Next Stage button's hit area sits inside the visible screen (%s)"
					% button_rect)

	# From here on, nothing may await on `self`: the button press below is
	# the real path to Game.start_stage() -> Game.goto() -> a deferred
	# change_scene_to_file that will free this node once it runs.
	_check_after_navigation(tree, next_button)

## Presses the real button, then hands off to a Timer parented under the
## tree root (never freed by a scene change) to verify the navigation
## actually completed a few frames later.
##
## The callback below must not touch `self` in any way — not a member
## variable, not a helper method — because by the time it fires, `self` is
## very likely already freed (that is the whole premise being tested). Every
## value it needs is captured into a plain local first, and it calls only
## the global print() and the passed-in `tree`, neither of which depend on
## this node still being alive.
func _check_after_navigation(tree: SceneTree, next_button: Node) -> void:
	var earlier_failures := _failures
	next_button.emit_signal("pressed")

	var checker := Timer.new()
	checker.one_shot = true
	checker.wait_time = 0.4
	tree.root.add_child(checker)
	checker.timeout.connect(func() -> void:
		var pending_ok: bool = Game.pending_stage == 2
		print(("   PASS  " if pending_ok else "   FAIL  ") +
				"pressing Next Stage set the game to navigate to stage 2 (pending_stage=%d)"
						% Game.pending_stage)

		var new_screen: Node = tree.current_scene
		var navigated_ok: bool = new_screen != null
		print(("   PASS  " if navigated_ok else "   FAIL  ") +
				"the scene tree actually navigated to a new screen")

		var is_stage_two := false
		if navigated_ok:
			var new_puzzle = new_screen.get("puzzle")
			is_stage_two = new_puzzle != null and new_puzzle.index == 2
		print(("   PASS  " if is_stage_two else "   FAIL  ") +
				"the new screen is genuinely stage 2, not stage 1 again or the menu")

		var late_failures := 0
		for ok in [pending_ok, navigated_ok, is_stage_two]:
			if not ok:
				late_failures += 1
		var total := earlier_failures + late_failures
		print("\n%s  next-stage navigation test (%d failure(s))"
				% ["PASS" if total == 0 else "FAIL", total])
		tree.quit(1 if total > 0 else 0))
	checker.start()

func _find_by_name(root: Node, target_name: String) -> Node:
	if root == null:
		return null
	if root.name == target_name:
		return root
	for child in root.get_children():
		var found := _find_by_name(child, target_name)
		if found != null:
			return found
	return null

func _expect(ok: bool, what: String) -> void:
	_print(ok, what)
	if not ok:
		_failures += 1

func _print(ok: bool, what: String) -> void:
	print(("   PASS  " if ok else "   FAIL  ") + what)

func _finish(tree: SceneTree) -> void:
	print("\n%s  next-stage navigation test (%d failure(s))"
			% ["PASS" if _failures == 0 else "FAIL", _failures])
	tree.quit(1 if _failures > 0 else 0)
