class_name ProgressTracker
extends RefCounted
## Level progress in percent, by distance: 0 at the spawn, 100 at the finish
## gate, from the player's x (never from time or obstacle counts). It only
## rises while running. A death takes [constant DEATH_PENALTY] points off
## (never below 0); after the respawn it rises again once the player is
## further along than the lowered value. Pure logic, so it is unit-tested.

signal changed(percent: float)

const DEATH_PENALTY := 25.0

var percent := 0.0

var _start_x := 0.0
var _length := 1.0


func reset(start_x: float, finish_x: float) -> void:
	_start_x = start_x
	_length = maxf(finish_x - start_x, 1.0)
	percent = 0.0
	changed.emit(percent)


## Where [param x] lies along the level, 0..100.
func percent_at(x: float) -> float:
	return clampf(100.0 * (x - _start_x) / _length, 0.0, 100.0)


func update(x: float) -> void:
	var at := percent_at(x)
	if at > percent:
		percent = at
		changed.emit(percent)


## Applies the death penalty; returns the points actually taken.
func penalize() -> float:
	var before := percent
	percent = maxf(percent - DEATH_PENALTY, 0.0)
	changed.emit(percent)
	return before - percent


func complete() -> void:
	percent = 100.0
	changed.emit(percent)
