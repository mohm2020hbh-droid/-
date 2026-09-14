class_name PuzzleDefinition
extends RefCounted

## A single puzzle, fully resolved for one locale.
##
## A puzzle is authored once but carries an independent spec per locale, because
## in this game the language is frequently part of the trick: the same idea can
## have a different board, a different instruction and a *different answer* in
## Arabic and in English. Nothing here assumes the two locales agree.

enum Layout { FLOW, ROW, COLUMN, GRID2, FREE }

const LAYOUT_NAMES := {
	"flow": Layout.FLOW, "row": Layout.ROW, "column": Layout.COLUMN,
	"grid2": Layout.GRID2, "free": Layout.FREE,
}

var id: StringName
var index: int = 0             ## 1-based stage number within the whole game.
var chapter: int = 1
var type: StringName = &"visual"
var locale: String = "en"

var instruction: String = ""
var sub_instruction: String = ""
var layout: Layout = Layout.FLOW
var elements: Array[PuzzleElement] = []
var solution: SolutionRule
var hints: PackedStringArray = PackedStringArray()
var explanation: String = ""
var wrong_notes: Dictionary = {}   ## target string -> nudge shown after tapping it.
## Chrome that this puzzle turns into a tap target ("ui:hint", "ui:stage", ...).
## Armed chrome loses its normal function for the duration of the puzzle, so the
## player is never silently punished for using a button that still looked live.
var ui_targets: PackedStringArray = PackedStringArray()

var time_limit: float = 0.0        ## 0 = untimed. A visible countdown that can fail you.
var par_time: float = 20.0         ## Solve under this (clean) for the third star.
var max_attempts: int = 0          ## 0 = unlimited.
var reward: int = 10               ## Base points.
var show_timer: bool = false
var show_attempts: bool = false
var allow_hints: bool = true
var allow_skip: bool = true
var tags: PackedStringArray = PackedStringArray()

static func from_dict(raw: Dictionary, loc: String, stage_index: int) -> PuzzleDefinition:
	var locales: Dictionary = raw.get("locales", {})
	if not locales.has(loc):
		return null
	var spec: Dictionary = locales[loc]

	var p := PuzzleDefinition.new()
	p.id = StringName(raw.get("id", ""))
	p.index = stage_index
	p.chapter = int(raw.get("chapter", 1))
	p.type = StringName(raw.get("type", "visual"))
	p.locale = loc

	p.time_limit = float(raw.get("time_limit", 0.0))
	p.par_time = float(raw.get("par_time", 20.0))
	p.max_attempts = int(raw.get("max_attempts", 0))
	p.reward = int(raw.get("reward", 10))
	p.show_timer = bool(raw.get("show_timer", p.time_limit > 0.0))
	p.show_attempts = bool(raw.get("show_attempts", p.max_attempts > 0))
	p.allow_hints = bool(raw.get("allow_hints", true))
	p.allow_skip = bool(raw.get("allow_skip", true))
	for t in raw.get("tags", []):
		p.tags.append(str(t))

	p.instruction = spec.get("instruction", "")
	p.sub_instruction = spec.get("sub_instruction", "")
	p.layout = LAYOUT_NAMES.get(spec.get("layout", "flow"), Layout.FLOW)
	for ed in spec.get("elements", []):
		p.elements.append(PuzzleElement.from_dict(ed))
	p.solution = SolutionRule.from_dict(spec.get("solution", {}))
	for h in spec.get("hints", []):
		p.hints.append(str(h))
	p.explanation = spec.get("explanation", "")
	p.wrong_notes = spec.get("wrong", {})
	for u in spec.get("ui_targets", []):
		p.ui_targets.append(str(u))
	return p

func element_by_id(eid: StringName) -> PuzzleElement:
	for e in elements:
		if e.id == eid:
			return e
	return null

func tappable_element_ids() -> Array[StringName]:
	var out: Array[StringName] = []
	for e in elements:
		if e.is_tappable():
			out.append(e.id)
	return out
