@tool
class_name SlabArt
extends Node2D
## The picture of a deadly slab, drawn once. Its owner (a gate or crush
## block) moves the node on physics ticks; physics interpolation keeps that
## motion smooth at any frame rate without redrawing. Drawn in the world's
## own organic language ([OrganicArt]): the striking face is its thorns.

var rect := Rect2(0, 0, 64, 64):
	set(value):
		rect = value
		_changed()
var hot_face: HazardArt.Face = HazardArt.Face.NONE:
	set(value):
		hot_face = value
		_changed()
## A crusher: its striking face is set with deeper thorns.
var with_teeth := false:
	set(value):
		with_teeth = value
		_changed()
## Picks this slab's shape (the owner's place in the level): stable across
## attempts, different from its neighbours.
var art_seed := 0:
	set(value):
		art_seed = value
		_changed()

var _shape: OrganicArt.Shape


func _changed() -> void:
	_shape = null
	queue_redraw()


func _draw() -> void:
	if _shape == null:
		_shape = OrganicArt.build(rect, art_seed, OrganicArt.theme_style(), hot_face)
	HazardArt.organic_slab(self, _shape, with_teeth)
