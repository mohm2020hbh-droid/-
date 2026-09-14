class_name Palette
extends RefCounted

## The whole game's colour vocabulary. Puzzle JSON refers to these by name, so a
## puzzle can say "red" and mean exactly the same red everywhere, which matters
## when a puzzle's answer depends on two things being the same colour.

const BG := Color("#0B0D10")
const SURFACE := Color("#14181D")
const SURFACE_HI := Color("#1D232B")
const LINE := Color("#2A323C")
const TEXT := Color("#ECEFF3")
const TEXT_DIM := Color("#7B838F")
const TEXT_FAINT := Color("#4A525E")

const RED := Color("#E5484D")
const BLUE := Color("#3E8FE0")
const GREEN := Color("#3FB950")
const YELLOW := Color("#E5C04B")
const PURPLE := Color("#A371F7")
const ORANGE := Color("#E08C3E")
const PINK := Color("#E06C9F")
const CYAN := Color("#42C9C2")
const WHITE := Color("#FFFFFF")
const BLACK := Color("#000000")
const GREY := Color("#6B7380")
const GOLD := Color("#E8C547")

const NAMED := {
	"bg": BG, "surface": SURFACE, "surface_hi": SURFACE_HI, "line": LINE,
	"text": TEXT, "dim": TEXT_DIM, "faint": TEXT_FAINT,
	"red": RED, "blue": BLUE, "green": GREEN, "yellow": YELLOW,
	"purple": PURPLE, "orange": ORANGE, "pink": PINK, "cyan": CYAN,
	"white": WHITE, "black": BLACK, "grey": GREY, "gold": GOLD,
}

## Accepts a palette name or a literal "#rrggbb". Unknown names fall back to
## TEXT rather than to an invisible colour, so a content typo is obvious on screen.
static func resolve(key: String, fallback: Color = TEXT) -> Color:
	if key.is_empty():
		return fallback
	if key.begins_with("#"):
		return Color.from_string(key, fallback)
	return NAMED.get(key, fallback)

static func is_named(key: String) -> bool:
	return NAMED.has(key)

## Readable foreground for text drawn on top of [param background].
static func on_color(background: Color) -> Color:
	return BLACK if background.get_luminance() > 0.55 else WHITE
