class_name StartOverlay
extends Control
## The world screen before a run: title, the world's five levels (locked
## ones cannot be picked), the chosen level's name and best score, and a
## pulsing "TAP TO START". Picking a card emits [signal level_chosen]; any
## tap outside the cards is handled by GameSession and starts the run.

signal level_chosen(index: int)
signal world_chosen(index: int)

var _time := 0.0
var _cards: Array[LevelCard] = []
var _tabs: Array[Button] = []

@onready var _world_tabs: HBoxContainer = %WorldTabs
@onready var _world_label: Label = %WorldLabel
@onready var _cards_row: HBoxContainer = %Cards
@onready var _level_label: Label = %LevelLabel
@onready var _best_label: Label = %BestLabel
@onready var _tap_label: Label = %TapLabel
@onready var _unlock_label: Label = %UnlockLabel


func _process(delta: float) -> void:
	_time += delta
	_tap_label.modulate.a = 0.55 + 0.45 * (0.5 + 0.5 * sin(_time * 3.5))


## Shows world [param world_index] of [param worlds] with level [param index]
## selected; a tab per world switches worlds (locked ones say so).
func open(worlds: Array[WorldData], world_index: int, index: int) -> void:
	var world := worlds[world_index]
	_build_tabs(worlds, world_index)
	_world_label.text = "WORLD %02d  ·  %s" % [world.number, world.display_name.to_upper()]
	_build_cards(world)
	for i in _cards.size():
		var card := _cards[i]
		card.locked = not Progression.is_unlocked(world, i)
		card.completed = Progression.is_completed(world.levels[i])
		card.selected = i == index
	var data := world.levels[index]
	var record := SaveSystem.get_record(data.id)
	_level_label.text = "%s  —  %s" % [data.display_name.to_upper(), data.tagline.to_upper()]
	_best_label.text = "BEST  %s" % UiFormat.thousands(record.best_score) if record.completed else ""
	var done := Progression.is_world_completed(world)
	_unlock_label.text = ""
	if done:
		_unlock_label.text = "WORLD %02d COMPLETE" % world.number
		if world.next_world_name != "":
			_unlock_label.text += "  ·  %s UNLOCKED" % world.next_world_name.to_upper()
	_time = 0.0
	show()


func close() -> void:
	hide()


func _build_tabs(worlds: Array[WorldData], current: int) -> void:
	_world_tabs.visible = worlds.size() > 1
	if _tabs.size() != worlds.size():
		for tab in _tabs:
			tab.queue_free()
		_tabs.clear()
		for i in worlds.size():
			var tab := Button.new()
			tab.focus_mode = Control.FOCUS_NONE
			tab.custom_minimum_size = Vector2(170, 44)
			tab.pressed.connect(_on_tab_pressed.bind(i))
			_world_tabs.add_child(tab)
			_tabs.append(tab)
	for i in worlds.size():
		var open := Progression.is_world_unlocked(worlds[i])
		_tabs[i].text = "WORLD %02d" % worlds[i].number if open else "WORLD %02d  —  LOCKED" % worlds[i].number
		_tabs[i].disabled = not open
		_tabs[i].button_pressed = i == current
		_tabs[i].toggle_mode = true
		_tabs[i].modulate = Color.WHITE if i == current else Color(1, 1, 1, 0.6)


func _on_tab_pressed(index: int) -> void:
	world_chosen.emit(index)


func _build_cards(world: WorldData) -> void:
	if _cards.size() == world.levels.size() and _cards[0].title == world.levels[0].display_name:
		for card in _cards:
			card.queue_redraw()
		return
	for card in _cards:
		card.queue_free()
	_cards.clear()
	for i in world.levels.size():
		var card := LevelCard.new()
		card.number = i + 1
		card.title = world.levels[i].display_name
		card.pressed.connect(_on_card_pressed.bind(i))
		_cards_row.add_child(card)
		_cards.append(card)


func _on_card_pressed(index: int) -> void:
	if not _cards[index].locked:
		level_chosen.emit(index)
