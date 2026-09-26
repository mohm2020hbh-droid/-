class_name Timeline
## Shared timing curves for World 02 machines (mirrored exactly in
## tools/levelgen/levelgen.py). All take a cycle position 0..1.


## Hold at 0, move to 1, hold at 1, move back (the Oscillator STEPS wave).
static func steps(cycle: float, hold_ratio: float) -> float:
	var hold := hold_ratio * 0.5
	var move := (1.0 - hold_ratio) * 0.5
	if cycle < hold:
		return 0.0
	if cycle < hold + move:
		return smoothstep(0.0, 1.0, (cycle - hold) / move)
	if cycle < hold * 2.0 + move:
		return 1.0
	return 1.0 - smoothstep(0.0, 1.0, (cycle - hold * 2.0 - move) / move)


## True during the [param warning] seconds before either move of [method steps].
static func steps_warning(cycle: float, hold_ratio: float, period: float, warning: float) -> bool:
	var at := cycle * period
	var hold := hold_ratio * 0.5 * period
	var move := (1.0 - hold_ratio) * 0.5 * period
	return (at >= hold - warning and at < hold) or (at >= hold * 2.0 + move - warning and at < hold * 2.0 + move)
