class_name SolutionRule
extends RefCounted

## Declarative description of what counts as solving a puzzle.
##
## Targets are addressed by string:
##   "some_id"              -> an element on the board
##   "char:some_id:3"       -> the 4th logical character of a CHARS element
##   "ui:hint" / "ui:stage" / "ui:skip" / "ui:back" / "ui:timer" / "ui:attempts"
##                          -> chrome outside the board, so the UI itself can be the answer
##   "board"                -> the empty space of the board behind every element

enum Kind {
	TAP,       ## One target.
	SEQUENCE,  ## Several targets, in order.
	NO_TAP,    ## Touch nothing until [member duration] elapses.
	HOLD,      ## Press and keep holding one target for [member duration].
}

const KIND_NAMES := {
	"tap": Kind.TAP, "sequence": Kind.SEQUENCE, "no_tap": Kind.NO_TAP, "hold": Kind.HOLD,
}

var kind: Kind = Kind.TAP
var target: String = ""
var targets: PackedStringArray = PackedStringArray()
## TAP only: other targets that are equally correct. Used when a puzzle honestly
## has more than one right answer, so the player is never punished for picking
## the one the author happened not to write down first.
var alternatives: PackedStringArray = PackedStringArray()
var duration: float = 0.0
var not_before: float = 0.0   ## A correct target tapped earlier than this is wrong.
var not_after: float = 0.0    ## 0 = no upper bound.
var reset_on_wrong: bool = true  ## SEQUENCE only: a wrong tap restarts the chain.
## Authored expectation, parallel to [method all_targets]: the character each
## "char:" target is supposed to land on. The content test shapes the real text
## and compares, so an off-by-one index in a puzzle fails the build instead of
## quietly asking the player for the wrong letter.
var expect: PackedStringArray = PackedStringArray()

static func from_dict(d: Dictionary) -> SolutionRule:
	var r := SolutionRule.new()
	r.kind = KIND_NAMES.get(d.get("kind", "tap"), Kind.TAP)
	r.target = d.get("target", "")
	for t in d.get("targets", []):
		r.targets.append(str(t))
	for a in d.get("alternatives", []):
		r.alternatives.append(str(a))
	r.duration = float(d.get("duration", 0.0))
	r.not_before = float(d.get("not_before", 0.0))
	r.not_after = float(d.get("not_after", 0.0))
	r.reset_on_wrong = bool(d.get("reset_on_wrong", true))
	for e in d.get("expect", []):
		r.expect.append(str(e))
	return r

## Every target this rule can accept, for validation and for the solve-reveal.
func all_targets() -> PackedStringArray:
	if kind == Kind.SEQUENCE:
		return targets
	if kind == Kind.NO_TAP:
		return PackedStringArray()
	var out := PackedStringArray([target])
	out.append_array(alternatives)
	return out

## Whether this rule is satisfied purely by waiting.
func is_passive() -> bool:
	return kind == Kind.NO_TAP

## True when [param candidate] is an accepted answer for a TAP rule.
func accepts(candidate: String) -> bool:
	return candidate == target or alternatives.has(candidate)
