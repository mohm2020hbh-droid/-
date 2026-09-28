class_name GardenLook
## THE INVERTED GARDEN's two colour states (docs/GDD.md §9D) and the blend
## between them: the single source of World 04's colours.
##
##   GROUND  (the ground is the floor):  a BLUE + WHITE world, a YELLOW player
##   CEILING (the ceiling is the floor): a YELLOW + BLACK world, a BLUE player
##
## [member blend] runs 0 (ground) -> 1 (ceiling) over each attach's transition
## (the backdrop drives it). Everything in a level is drawn once, in neutral
## base colours chosen per role, and tinted by one modulate per role, so a
## flip recolours the whole world by changing a few hundred modulates and
## redrawing nothing. The roles are node groups:
##   garden_ink    hazards: dark ink bodies with white rims -> black ink, gold rims
##   garden_soil   terrain bodies (drawn white/grey)       -> cobalt / dark umber
##   garden_bloom  grass, moss, blossoms on the faces you run on -> white / gold
##   garden_water  water in the level (drawn white/grey)    -> sky blue / gold
##   garden_leaf   leaves and foliage in the level          -> pale blue / black
##   garden_mark   checkpoints, the finish                  -> deep blue / black
##   garden_shard  shards                                   -> navy / azure
## Hazards keep the darkest value and a bright rim in both states, terrain a
## middle value, the backdrop the lightest; the player is the only warm (or
## the only cool) colour on screen: it always reads first.

const ROLES := {
	&"garden_ink": [Color(1.0, 1.0, 1.0), Color(1.0, 0.84, 0.24)],
	&"garden_soil": [Color(0.30, 0.52, 0.94), Color(0.13, 0.10, 0.04)],
	&"garden_bloom": [Color(0.97, 0.99, 1.0), Color(1.0, 0.84, 0.22)],
	&"garden_water": [Color(0.62, 0.84, 1.0), Color(1.0, 0.80, 0.16)],
	&"garden_leaf": [Color(0.58, 0.78, 1.0), Color(0.16, 0.12, 0.05)],
	&"garden_mark": [Color(0.10, 0.27, 0.86), Color(0.07, 0.05, 0.02)],
	&"garden_shard": [Color(0.30, 0.34, 0.60), Color(1.0, 1.0, 1.0)],
}

## The ink hazards are drawn with (then tinted per state).
const INK_BODY := Color(0.035, 0.055, 0.19)
const INK_DEEP := Color(0.015, 0.025, 0.10)
const INK_RIM := Color(1.0, 1.0, 1.0)

## The player: yellow on the ground, blue on the ceiling, each with an edge
## that stands off both worlds, and the other state's colour inside it.
const PLAYER_GROUND := {"body": Color("ffd21f"), "edge": Color("0b1a4a"), "seed": Color("1e6bff"),
	"glow": Color("fff1a8")}
const PLAYER_CEILING := {"body": Color("1e6bff"), "edge": Color("ffffff"), "seed": Color("ffd21f"),
	"glow": Color("9cc3ff")}

## How far below the view's centre the landscape's horizon sits (px, in
## the ground state; the backdrop turns it over in the ceiling state).
const HORIZON_Y := 150.0

## 0 = the ground state, 1 = the ceiling state.
static var blend := 0.0


static func role_color(role: StringName, b: float = blend) -> Color:
	var pair: Array = ROLES[role]
	return (pair[0] as Color).lerp(pair[1], b)


static func player_color(key: String, b: float = blend) -> Color:
	return (PLAYER_GROUND[key] as Color).lerp(PLAYER_CEILING[key], b)


## Roles tinted through modulate (their drawing lives in children that
## animate their own self_modulate, e.g. a shard's shimmer).
const WHOLE_ROLES: Array[StringName] = [&"garden_shard"]


## Tints every node of every role in [param tree] for blend [param b].
static func apply(tree: SceneTree, b: float) -> void:
	blend = b
	for role: StringName in ROLES:
		var tint := role_color(role, b)
		var whole := role in WHOLE_ROLES
		for node in tree.get_nodes_in_group(role):
			if whole:
				(node as CanvasItem).modulate = tint
			else:
				(node as CanvasItem).self_modulate = tint


## Gives the shared level pieces (shards, checkpoints, the finish) their
## World 04 roles.
static func dress_level(level: Node) -> void:
	for node in level.find_children("*", "Area2D", true, false):
		if node is Shard:
			node.add_to_group(&"garden_shard")
		elif node is Checkpoint or node is FinishGate:
			node.add_to_group(&"garden_mark")


## Tints one node for the current blend (a node that joins mid-level).
static func tint(item: CanvasItem, role: StringName) -> void:
	if not item.is_in_group(role):
		item.add_to_group(role)
	item.self_modulate = role_color(role)
