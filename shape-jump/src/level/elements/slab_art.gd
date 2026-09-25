@tool
class_name SlabArt
extends Node2D
## The picture of a deadly slab, drawn once. Its owner (a gate or crush
## block) moves the node on physics ticks; physics interpolation keeps that
## motion smooth at any frame rate without redrawing.

var rect := Rect2(0, 0, 64, 64):
	set(value):
		rect = value
		queue_redraw()
var hot_face: HazardArt.Face = HazardArt.Face.NONE:
	set(value):
		hot_face = value
		queue_redraw()
var with_teeth := false:
	set(value):
		with_teeth = value
		queue_redraw()


func _draw() -> void:
	HazardArt.slab(self, rect, hot_face)
	if with_teeth:
		HazardArt.teeth(self, rect, hot_face)
