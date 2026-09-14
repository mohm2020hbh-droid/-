class_name PuzzleBoard
extends Control

## Turns a [PuzzleDefinition]'s element list into live controls and feeds taps
## into a [PuzzleSession]. It knows nothing about any individual puzzle: every
## trick in the game is expressed as data that this renderer happens to honour.

signal target_tapped(target: String)
signal press_started(target: String)
signal press_ended(target: String)

const SHAPE_BASE := 118.0
const MIN_TOUCH := 88.0        ## No interactive element is ever smaller than this.
const LABEL_PADDING := 18.0
const MIN_BOARD_WIDTH := 240.0
const SIDE_PADDING := 26.0     ## Matches ScreenBase.EDGE_PADDING.
const ROW_SEPARATION := 18
const COLUMN_SEPARATION := 18

var puzzle: PuzzleDefinition
var session: PuzzleSession

var _root: Control
var _widgets: Dictionary = {}      ## StringName -> Control
var _strips: Dictionary = {}       ## StringName -> CharStrip
var _usable_width := MIN_BOARD_WIDTH

func build(p: PuzzleDefinition, s: PuzzleSession) -> void:
	puzzle = p
	session = s
	_usable_width = _measure_usable_width()
	_clear()
	_root = _make_layout_root()
	add_child(_root)
	_populate()
	_connect_session()

## The width a row may occupy. Prefers the board's own measured width, falls back
## to the live viewport, and finally to the project's design width. Without the
## last two fallbacks a board built before it enters the tree would silently
## collapse to the minimum and cramp every puzzle.
func _measure_usable_width() -> float:
	if size.x > MIN_BOARD_WIDTH:
		return size.x
	if is_inside_tree():
		var viewport_width := get_viewport_rect().size.x
		if viewport_width > 0.0:
			return maxf(MIN_BOARD_WIDTH, viewport_width - SIDE_PADDING * 2.0)
	var design_width := float(ProjectSettings.get_setting(
			"display/window/size/viewport_width", 720))
	return maxf(MIN_BOARD_WIDTH, design_width - SIDE_PADDING * 2.0)

func usable_width() -> float:
	return _usable_width

func _clear() -> void:
	_widgets.clear()
	_strips.clear()
	for child in get_children():
		child.queue_free()

func _make_layout_root() -> Control:
	var dir := Loc.layout_direction()
	match puzzle.layout:
		PuzzleDefinition.Layout.FREE:
			var free := Control.new()
			free.set_anchors_preset(Control.PRESET_FULL_RECT)
			free.mouse_filter = Control.MOUSE_FILTER_IGNORE
			# FREE positions are physical, not reading-order: a memory puzzle about
			# "where the red one was" must put it in the same place in both
			# languages, or its own explanation stops being true. Anchors are
			# mirrored under an RTL layout direction, so this root opts out.
			free.layout_direction = Control.LAYOUT_DIRECTION_LTR
			return free
		PuzzleDefinition.Layout.GRID2:
			var grid := GridContainer.new()
			grid.columns = 2
			grid.layout_direction = dir
			grid.add_theme_constant_override("h_separation", COLUMN_SEPARATION)
			grid.add_theme_constant_override("v_separation", ROW_SEPARATION)
			grid.set_anchors_preset(Control.PRESET_FULL_RECT)
			# A GridContainer has no alignment of its own, so stretching it would
			# pack the cells against the starting edge. It is centred instead.
			return _centered(grid, false)
		_:
			var column := VBoxContainer.new()
			column.layout_direction = dir
			column.alignment = BoxContainer.ALIGNMENT_CENTER
			column.add_theme_constant_override("separation", ROW_SEPARATION)
			column.set_anchors_preset(Control.PRESET_FULL_RECT)
			return _centered(column)

## Centres the board vertically while letting it use the full width available.
## A CenterContainer would shrink children to their minimum size, which is what
## forces a fixed board width and breaks on narrow screens.
func _centered(inner: Control, stretch: bool = true) -> Control:
	var column := VBoxContainer.new()
	column.set_anchors_preset(Control.PRESET_FULL_RECT)
	column.alignment = BoxContainer.ALIGNMENT_CENTER
	column.mouse_filter = Control.MOUSE_FILTER_IGNORE
	inner.size_flags_horizontal = Control.SIZE_EXPAND_FILL if stretch \
			else Control.SIZE_SHRINK_CENTER
	column.add_child(inner)
	return column

func _content_container() -> Control:
	if puzzle.layout == PuzzleDefinition.Layout.FREE:
		return _root
	return _root.get_child(0)

func _populate() -> void:
	var host := _content_container()
	if puzzle.layout == PuzzleDefinition.Layout.FREE:
		for element in puzzle.elements:
			_place_free(host, element)
		return
	if puzzle.layout == PuzzleDefinition.Layout.GRID2:
		for element in puzzle.elements:
			host.add_child(_make_widget(element))
		return

	for row_index in _row_indices():
		var row := _make_row()
		host.add_child(row)
		for element in puzzle.elements:
			if element.row == row_index:
				row.add_child(_make_widget(element))

func _row_indices() -> Array[int]:
	var rows: Array[int] = []
	for element in puzzle.elements:
		var r := element.row
		if puzzle.layout == PuzzleDefinition.Layout.COLUMN:
			r = puzzle.elements.find(element)
			element.row = r
		if not rows.has(element.row):
			rows.append(element.row)
	rows.sort()
	return rows

func _make_row() -> Control:
	if puzzle.layout == PuzzleDefinition.Layout.FLOW:
		var flow := HFlowContainer.new()
		flow.layout_direction = Loc.layout_direction()
		flow.alignment = FlowContainer.ALIGNMENT_CENTER
		flow.add_theme_constant_override("h_separation", COLUMN_SEPARATION)
		flow.add_theme_constant_override("v_separation", ROW_SEPARATION)
		flow.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		return flow
	var box := HBoxContainer.new()
	box.layout_direction = Loc.layout_direction()
	box.alignment = BoxContainer.ALIGNMENT_CENTER
	box.add_theme_constant_override("separation", COLUMN_SEPARATION)
	box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return box

func _place_free(host: Control, element: PuzzleElement) -> void:
	var widget := _make_widget(element)
	host.add_child(widget)
	var anchor := element.pos
	if anchor.x < 0.0:
		anchor = Vector2(0.5, 0.5)
	widget.set_anchors_preset(Control.PRESET_TOP_LEFT)
	widget.anchor_left = anchor.x
	widget.anchor_top = anchor.y
	widget.anchor_right = anchor.x
	widget.anchor_bottom = anchor.y
	var min_size := widget.get_combined_minimum_size()
	widget.offset_left = -min_size.x * 0.5
	widget.offset_top = -min_size.y * 0.5
	widget.offset_right = min_size.x * 0.5
	widget.offset_bottom = min_size.y * 0.5

# --- Widget construction ------------------------------------------------------

func _make_widget(element: PuzzleElement) -> Control:
	var widget: Control
	match element.kind:
		PuzzleElement.Kind.SHAPE:
			widget = _make_shape(element)
		PuzzleElement.Kind.CHARS:
			widget = _make_chars(element)
		PuzzleElement.Kind.BUTTON:
			widget = _make_button(element)
		PuzzleElement.Kind.ZONE:
			widget = _make_zone(element)
		PuzzleElement.Kind.SPACER:
			widget = _make_spacer(element)
		_:
			widget = _make_label(element)

	widget.name = str(element.id) if element.id != &"" else widget.name
	widget.modulate.a = element.opacity
	widget.rotation_degrees = element.rotation_deg
	if element.rotation_deg != 0.0:
		widget.pivot_offset = widget.get_combined_minimum_size() * 0.5
	if element.starts_hidden:
		widget.visible = false
	_widgets[element.id] = widget

	if element.pulse and element.is_tappable():
		widget.ready.connect(func() -> void: FeedbackFx.start_pulse(widget), CONNECT_ONE_SHOT)
	return widget

func _make_shape(element: PuzzleElement) -> Control:
	var shape := ShapeWidget.new()
	shape.form = element.shape
	shape.fill = Palette.resolve(element.color, Palette.BLUE)
	shape.label_text = element.text
	shape.label_color = Palette.resolve(element.text_color, Palette.on_color(shape.fill))
	shape.label_font_size = element.font_size if element.font_size > 0 else 30
	var side := maxf(SHAPE_BASE * element.scale,
			MIN_TOUCH if element.is_tappable() else 24.0)
	side = minf(side, _usable_width)
	shape.custom_minimum_size = Vector2(side, side)
	if element.is_tappable():
		shape.tapped.connect(_on_tap.bind(str(element.id), shape))
		shape.press_started.connect(func() -> void: press_started.emit(str(element.id)))
		shape.press_ended.connect(func() -> void: press_ended.emit(str(element.id)))
	else:
		shape.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return shape

func _make_chars(element: PuzzleElement) -> Control:
	var strip := CharStrip.new()
	strip.text = element.text
	strip.set_font(GameTheme.bold())
	strip.font_color = Palette.resolve(element.color, Palette.TEXT)
	strip.max_font_size = element.font_size if element.font_size > 0 else 92
	strip.show_slots = true
	strip.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	if element.is_tappable():
		strip.character_tapped.connect(func(index: int) -> void:
			_on_tap("char:%s:%d" % [element.id, index], strip))
		strip.strip_tapped.connect(_on_tap.bind(str(element.id), strip))
	else:
		strip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_strips[element.id] = strip
	return strip

func _make_label(element: PuzzleElement) -> Control:
	var font := GameTheme.semibold()
	var font_size: int = element.font_size if element.font_size > 0 else 40

	var label := Label.new()
	label.text = element.text
	label.add_theme_font_override("font", font)
	label.add_theme_font_size_override("font_size", font_size)
	label.add_theme_color_override("font_color", Palette.resolve(element.color, Palette.TEXT))
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	label.text_direction = Control.TEXT_DIRECTION_AUTO
	label.layout_direction = Loc.layout_direction()

	# A label inside a flow row has no natural width to defend itself with, so it
	# must claim the width its text actually needs. Without this the container
	# squeezes it to nothing and autowrap breaks the text one character per line,
	# which in Arabic also severs the cursive joins and renders the word unreadable.
	var measured := font.get_string_size(element.text, HORIZONTAL_ALIGNMENT_LEFT,
			-1.0, font_size)
	var wraps := measured.x > _usable_width
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART if wraps \
			else TextServer.AUTOWRAP_OFF
	label.custom_minimum_size = Vector2(
			minf(measured.x + LABEL_PADDING, _usable_width),
			MIN_TOUCH if element.is_tappable() else 0.0)

	if element.is_tappable():
		label.mouse_filter = Control.MOUSE_FILTER_STOP
		label.gui_input.connect(_on_control_input.bind(str(element.id), label))
	else:
		label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return label

func _make_button(element: PuzzleElement) -> Control:
	var button := Button.new()
	button.text = element.text
	button.custom_minimum_size = Vector2(
			minf(maxf(180.0 * element.scale, MIN_TOUCH), _usable_width),
			maxf(88.0 * element.scale, MIN_TOUCH))
	button.layout_direction = Loc.layout_direction()
	button.add_theme_font_size_override("font_size",
			element.font_size if element.font_size > 0 else GameTheme.SIZE_BUTTON)
	var tint := Palette.resolve(element.color, Palette.SURFACE)
	if element.color != "text" and element.color != "surface":
		button.add_theme_stylebox_override("normal", GameTheme.flat_box(tint, 16, 2, tint))
		button.add_theme_stylebox_override("hover", GameTheme.flat_box(tint.lightened(0.08), 16, 2, tint))
		button.add_theme_stylebox_override("pressed", GameTheme.flat_box(tint.darkened(0.15), 16, 2, tint))
		button.add_theme_color_override("font_color",
				Palette.resolve(element.text_color, Palette.on_color(tint)))
	elif not element.text_color.is_empty():
		button.add_theme_color_override("font_color", Palette.resolve(element.text_color))
	if element.is_tappable():
		button.pressed.connect(_on_tap.bind(str(element.id), button))
		button.button_down.connect(func() -> void: press_started.emit(str(element.id)))
		button.button_up.connect(func() -> void: press_ended.emit(str(element.id)))
	else:
		button.disabled = true
	return button

func _make_zone(element: PuzzleElement) -> Control:
	var zone := Control.new()
	var side := maxf(SHAPE_BASE * element.scale, MIN_TOUCH)
	zone.custom_minimum_size = Vector2(side, side)
	zone.mouse_filter = Control.MOUSE_FILTER_STOP if element.is_tappable() \
			else Control.MOUSE_FILTER_IGNORE
	# A faint dashed outline keeps an "empty" target honest: the player can see
	# that something is there to aim at, which is the difference between a
	# lateral-thinking puzzle and a pixel hunt.
	var frame := ColorRect.new()
	frame.color = Palette.resolve(element.color, Color(1, 1, 1, 0.035))
	frame.set_anchors_preset(Control.PRESET_FULL_RECT)
	frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	zone.add_child(frame)
	if not element.text.is_empty():
		var hint_label := Label.new()
		hint_label.text = element.text
		hint_label.add_theme_color_override("font_color", Palette.TEXT_FAINT)
		hint_label.add_theme_font_size_override("font_size", 22)
		hint_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		hint_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		hint_label.set_anchors_preset(Control.PRESET_FULL_RECT)
		hint_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
		zone.add_child(hint_label)
	if element.is_tappable():
		zone.gui_input.connect(_on_control_input.bind(str(element.id), zone))
	return zone

func _make_spacer(element: PuzzleElement) -> Control:
	var spacer := Control.new()
	spacer.custom_minimum_size = Vector2(SHAPE_BASE * element.scale, 24.0 * element.scale)
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return spacer

# --- Interaction --------------------------------------------------------------

func _on_control_input(event: InputEvent, target: String, widget: Control) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		widget.accept_event()
		if event.pressed:
			press_started.emit(target)
			_on_tap(target, widget)
		else:
			press_ended.emit(target)

func _on_tap(target: String, widget: Control) -> void:
	if session == null or session.finished:
		return
	FeedbackFx.pop(widget, 1.1)
	target_tapped.emit(target)
	session.tap(target)

## The board's own empty space is a legitimate answer for some puzzles.
func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed \
			and event.button_index == MOUSE_BUTTON_LEFT:
		if session != null and not session.finished:
			target_tapped.emit("board")
			session.tap("board")

# --- Session reactions --------------------------------------------------------

func _connect_session() -> void:
	session.element_revealed.connect(_reveal)
	session.element_hidden.connect(_hide)
	session.element_changed.connect(_change)
	session.wrong_tap.connect(_on_wrong)

func _reveal(id: StringName) -> void:
	var widget: Control = _widgets.get(id)
	if widget != null:
		widget.visible = true
		FeedbackFx.fade_in(widget)
		Audio.play(Audio.Sfx.REVEAL)

func _hide(id: StringName) -> void:
	var widget: Control = _widgets.get(id)
	if widget != null:
		widget.visible = false

func _change(id: StringName, changes: Dictionary) -> void:
	var widget: Control = _widgets.get(id)
	if widget == null:
		return
	if changes.has("text"):
		if widget is CharStrip:
			widget.text = changes["text"]
		elif widget is ShapeWidget:
			widget.label_text = changes["text"]
		elif widget.has_method("set_text"):
			widget.set_text(str(changes["text"]))
	if changes.has("color"):
		var color := Palette.resolve(str(changes["color"]))
		if widget is ShapeWidget:
			widget.fill = color
		elif widget is CharStrip:
			widget.font_color = color
			widget.queue_redraw()
		elif widget is Label:
			widget.add_theme_color_override("font_color", color)
	FeedbackFx.flash(widget, Palette.WHITE, 0.2)

func _on_wrong(target: String, _note: String) -> void:
	var widget := widget_for(target)
	if widget != null:
		FeedbackFx.shake(widget)
		FeedbackFx.flash(widget, Palette.RED)

## Resolves a solution target string back to the control that represents it.
func widget_for(target: String) -> Control:
	if target.begins_with("char:"):
		var parts := target.split(":")
		if parts.size() > 1:
			return _strips.get(StringName(parts[1]))
		return null
	return _widgets.get(StringName(target))

## Highlights the answer after a solve or a give-up, so the trick is always shown.
func reveal_solution() -> void:
	for target in puzzle.solution.all_targets():
		if target.begins_with("char:"):
			var parts := target.split(":")
			var strip: CharStrip = _strips.get(StringName(parts[1]))
			if strip != null and parts.size() > 2:
				strip.highlight(int(parts[2]), Color(Palette.GREEN, 0.35))
			continue
		var widget: Control = _widgets.get(StringName(target))
		if widget != null:
			widget.visible = true
			FeedbackFx.flash(widget, Palette.GREEN, 0.5)
			FeedbackFx.pop(widget, 1.2)
