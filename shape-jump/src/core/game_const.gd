class_name GameConst
## Shared constants: level grid size and physics layer bits.
## Layer names mirror [layer_names] in project.godot.

const TILE := 64.0

const LAYER_WORLD := 1 << 0
const LAYER_PLAYER := 1 << 1
const LAYER_HAZARD := 1 << 2
const LAYER_PICKUP := 1 << 3
const LAYER_TRIGGER := 1 << 4
