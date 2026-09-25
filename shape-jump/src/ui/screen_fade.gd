class_name ScreenFade
extends ColorRect
## Full-screen fade used to hide camera cuts (respawn, restart).


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	color = Color(Palette.SKY_TOP, 0.0)


## Tween step helper: adds "fade to [param alpha] over [param duration]".
func tween_to(tween: Tween, alpha: float, duration: float) -> void:
	tween.tween_property(self, ^"color:a", alpha, duration)
