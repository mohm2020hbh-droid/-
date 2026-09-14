class_name FeedbackFx
extends RefCounted

## Short, cheap motion used to make every tap feel answered. All of it respects
## the "reduce motion" accessibility setting.

static func motion_enabled() -> bool:
	return not bool(SaveManager.get_setting("reduce_motion", false))

static func shake(node: Control, strength: float = 14.0) -> void:
	if not motion_enabled() or not is_instance_valid(node):
		return
	var origin := node.position
	var tween := node.create_tween()
	for offset in [strength, -strength * 0.7, strength * 0.4, 0.0]:
		tween.tween_property(node, "position", origin + Vector2(offset, 0.0), 0.05)\
				.set_trans(Tween.TRANS_SINE)
	tween.tween_callback(func() -> void:
		if is_instance_valid(node):
			node.position = origin)

static func pop(node: Control, amount: float = 1.16) -> void:
	if not is_instance_valid(node):
		return
	node.pivot_offset = node.size * 0.5
	if not motion_enabled():
		return
	var tween := node.create_tween()
	tween.tween_property(node, "scale", Vector2.ONE * amount, 0.08)\
			.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(node, "scale", Vector2.ONE, 0.14)\
			.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_IN_OUT)

static func flash(node: CanvasItem, color: Color, duration: float = 0.22) -> void:
	if not is_instance_valid(node):
		return
	var original: Color = node.modulate
	var tween := node.create_tween()
	tween.tween_property(node, "modulate", color, duration * 0.35)
	tween.tween_property(node, "modulate", original, duration * 0.65)

static func fade_in(node: CanvasItem, duration: float = 0.25) -> void:
	if not is_instance_valid(node):
		return
	node.modulate.a = 0.0
	node.create_tween().tween_property(node, "modulate:a", 1.0, duration)

static func fade_out(node: CanvasItem, duration: float = 0.2) -> void:
	if not is_instance_valid(node):
		return
	node.create_tween().tween_property(node, "modulate:a", 0.0, duration)

## The slow breathing used to make a decoy look inviting.
static func start_pulse(node: Control) -> void:
	if not motion_enabled() or not is_instance_valid(node):
		return
	node.pivot_offset = node.size * 0.5
	var tween := node.create_tween().set_loops()
	tween.tween_property(node, "scale", Vector2.ONE * 1.05, 0.85)\
			.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tween.tween_property(node, "scale", Vector2.ONE, 0.85)\
			.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
