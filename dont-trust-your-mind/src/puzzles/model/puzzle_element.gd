class_name PuzzleElement
extends RefCounted

## One visual/interactive atom of a puzzle board.
## Elements are pure data: the board decides how to render each [member kind].

enum Kind {
	LABEL,   ## A run of text, tappable as a whole.
	CHARS,   ## Text whose individual characters are tappable (BiDi-aware).
	SHAPE,   ## A geometric primitive.
	BUTTON,  ## A framed, button-looking control.
	ZONE,    ## An invisible but layout-occupying tap region.
	SPACER,  ## Invisible, never tappable. Used to shape a layout.
}

const KIND_NAMES := {
	"label": Kind.LABEL, "chars": Kind.CHARS, "shape": Kind.SHAPE,
	"button": Kind.BUTTON, "zone": Kind.ZONE, "spacer": Kind.SPACER,
}

enum ShapeForm { CIRCLE, SQUARE, TRIANGLE, DIAMOND }

const SHAPE_NAMES := {
	"circle": ShapeForm.CIRCLE, "square": ShapeForm.SQUARE,
	"triangle": ShapeForm.TRIANGLE, "diamond": ShapeForm.DIAMOND,
}

var id: StringName
var kind: Kind = Kind.LABEL
var text: String = ""
var shape: ShapeForm = ShapeForm.CIRCLE
var color: String = "text"          ## Palette key or #rrggbb.
var text_color: String = ""         ## Empty means "derive from color/palette".
var font_size: int = 0              ## 0 means "use the board default for this kind".
var scale: float = 1.0
var rotation_deg: float = 0.0
var row: int = 0                    ## Elements sharing a row are laid out together.
var pos: Vector2 = Vector2(-1, -1)  ## FREE layout only: normalised 0..1 position.
var interactive: bool = true
var opacity: float = 1.0
var pulse: bool = false             ## Slow attention-grabbing pulse (a lure, usually).
var reveal_after: float = -1.0      ## Seconds before the element appears; <0 = immediate.
var hide_after: float = -1.0        ## Seconds before the element disappears; <0 = never.
var starts_hidden: bool = false
var on_tap: Dictionary = {}         ## Side effects: reveal/hide/set_text/set_color.
var note: String = ""               ## Author note; never shown to the player.

static func from_dict(d: Dictionary) -> PuzzleElement:
	var e := PuzzleElement.new()
	e.id = StringName(d.get("id", ""))
	e.kind = KIND_NAMES.get(d.get("kind", "label"), Kind.LABEL)
	e.text = d.get("text", "")
	e.shape = SHAPE_NAMES.get(d.get("shape", "circle"), ShapeForm.CIRCLE)
	e.color = d.get("color", "text")
	e.text_color = d.get("text_color", "")
	e.font_size = int(d.get("font_size", 0))
	e.scale = float(d.get("scale", 1.0))
	e.rotation_deg = float(d.get("rotation_deg", 0.0))
	e.row = int(d.get("row", 0))
	var raw_pos = d.get("pos", null)
	if raw_pos is Array and raw_pos.size() == 2:
		e.pos = Vector2(float(raw_pos[0]), float(raw_pos[1]))
	e.interactive = bool(d.get("interactive", true))
	e.opacity = float(d.get("opacity", 1.0))
	e.pulse = bool(d.get("pulse", false))
	e.reveal_after = float(d.get("reveal_after", -1.0))
	e.hide_after = float(d.get("hide_after", -1.0))
	e.starts_hidden = bool(d.get("starts_hidden", false)) or e.reveal_after >= 0.0
	e.on_tap = d.get("on_tap", {})
	e.note = d.get("note", "")
	return e

func is_tappable() -> bool:
	return interactive and kind != Kind.SPACER
