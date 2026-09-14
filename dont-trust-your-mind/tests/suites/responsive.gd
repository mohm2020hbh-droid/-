extends TestSuite

## Lays every screen out at the aspect ratios Android actually ships and checks
## that nothing overflows horizontally.
##
## Vertical overflow is allowed where a screen scrolls; horizontal overflow is
## always a layout bug on a phone, and on an RTL layout it is the failure mode
## that hides content off the left edge where nobody scrolls to find it.

const SIZES := [
	{"name": "16:9 phone", "size": Vector2i(720, 1280)},
	{"name": "20:9 tall phone", "size": Vector2i(720, 1600)},
	{"name": "small phone", "size": Vector2i(600, 1024)},
	{"name": "tablet 16:10", "size": Vector2i(1200, 1920)},
]
const SCREENS := ["language", "intro", "menu", "stages", "settings", "daily", "achievements"]
const SAMPLE_STAGES := [1, 4, 8, 20, 21, 27, 31, 42, 47, 50]
const TOLERANCE := 1.0

var _host: SubViewport

func suite_name() -> String:
	return "responsive layout"

func run() -> void:
	var original := Loc.locale
	_host = SubViewport.new()
	_host.disable_3d = true
	add_child(_host)

	for loc in Loc.SUPPORTED:
		Loc.set_locale(loc)
		for spec in SIZES:
			_host.size = spec["size"]
			await _check_all(loc, spec["name"], Vector2(spec["size"]))

	Loc.set_locale(original)
	_host.queue_free()

func _check_all(loc: String, size_name: String, viewport: Vector2) -> void:
	for screen in SCREENS:
		await _check_scene(load(Game.SCENES[screen]), viewport,
				"%s [%s, %s]" % [screen, loc, size_name])
	for stage in SAMPLE_STAGES:
		Game.pending_daily = false
		Game.pending_stage = stage
		await _check_scene(load(Game.SCENES["puzzle"]), viewport,
				"stage %d [%s, %s]" % [stage, loc, size_name])

func _check_scene(packed: PackedScene, viewport: Vector2, label: String) -> void:
	if packed == null:
		return
	var instance := packed.instantiate()
	_host.add_child(instance)
	await get_tree().process_frame
	await get_tree().process_frame

	var overflow := _worst_overflow(instance, viewport)
	check(overflow <= TOLERANCE,
			"%s: content stays inside the screen width (overflow %.1fpx)"
					% [label, overflow])

	instance.queue_free()
	await get_tree().process_frame

## Walks visible controls and returns the largest horizontal overshoot.
func _worst_overflow(root: Node, viewport: Vector2) -> float:
	var worst := 0.0
	var stack: Array[Node] = [root]
	while not stack.is_empty():
		var node: Node = stack.pop_back()
		for child in node.get_children():
			stack.append(child)
		var control := node as Control
		if control == null or not control.is_visible_in_tree():
			continue
		# Scroll containers legitimately hold content wider than themselves.
		if _inside_scroller(control, root):
			continue
		var rect := control.get_global_rect()
		worst = maxf(worst, maxf(-rect.position.x, rect.end.x - viewport.x))
	return worst

func _inside_scroller(control: Control, root: Node) -> bool:
	var node: Node = control.get_parent()
	while node != null and node != root:
		if node is ScrollContainer:
			return true
		node = node.get_parent()
	return false
