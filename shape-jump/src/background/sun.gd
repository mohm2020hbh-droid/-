@tool
extends Node2D
## Pale, oversized sun disk low in the haze (docs/GDD.md §13). Drawn once.
## Kept muted so the playfield neon and the player always read on top of it.

@export var radius := 175.0


func _draw() -> void:
	Neon.soft_light(self, Vector2.ZERO, radius * 3.2, Color(Palette.SUN_GLOW, 0.3))
	draw_circle(Vector2.ZERO, radius, Palette.SUN.darkened(0.38))
	draw_circle(Vector2(-radius * 0.1, -radius * 0.08), radius * 0.88, Color(Palette.SUN.darkened(0.22), 0.6))
	# Haze bands across the disk, like the reference's layered dusk.
	for i in 4:
		var y := radius * (0.15 + 0.2 * i)
		var half := sqrt(maxf(radius * radius - y * y, 0.0))
		draw_line(Vector2(-half, y), Vector2(half, y), Color(Palette.SKY_HORIZON, 0.35 + 0.1 * i), 3.0 + 2.0 * i)
