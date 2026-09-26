class_name Palette
## Single source of truth for the game's colors (see docs/GDD.md §13).
## Each world has its own look ([member WorldData.theme]): [method use]
## switches every color at once, before a level is built, so elements that
## draw once pick up the world they belong to.
##   &"red"  – World 01, The Red Void: black and neon red.
##   &"mono" – World 02, The Monochrome Void: black and white only.
##   &"galaxy" – World 03, The Horrifying Galaxy: a cold base; the two
##               gravity colours (blue/violet, orange/amber) are layered on
##               top by GalaxyLook.

const THEMES := {
	&"red": {
		"SKY_TOP": Color("07040a"), "SKY_HORIZON": Color("3a0d18"), "FOG": Color("5a1222"),
		"SUN": Color("ffb3ae"), "SUN_GLOW": Color("ff3b4a"),
		"SILHOUETTE_FAR": Color("1e0a12"), "SILHOUETTE_MID": Color("12060b"),
		"BLOCK_BODY": Color("0c090d"), "BLOCK_FACE": Color("171016"),
		"NEON": Color("ff2340"), "NEON_DIM": Color("8a1224"),
		"HAZARD": Color("ff3048"), "HAZARD_CORE": Color("ffb0b8"),
		"HAZARD_BODY": Color("2a070c"), "HAZARD_STRIPE": Color(0.55, 0.08, 0.13, 0.5),
		"PLAYER_BODY": Color("09060a"), "PLAYER_EDGE": Color("ff3d55"), "PLAYER_CORE": Color("fff0f2"),
		"SHARD_EDGE": Color("ff4d6a"), "SHARD_CORE": Color("ffe3ea"),
		"UI_TEXT": Color("f4e9ec"), "UI_MUTED": Color("b89aa2"), "UI_PANEL": Color(0.043, 0.027, 0.039, 0.86),
		"UI_ACCENT": Color("ff2340"), "UI_CARD": Color(0.05, 0.03, 0.045),
	},
	&"galaxy": {
		"SKY_TOP": Color("04030c"), "SKY_HORIZON": Color("191135"), "FOG": Color("3b2c70"),
		"SUN": Color("d6ccff"), "SUN_GLOW": Color("9b7bff"),
		"SILHOUETTE_FAR": Color("141129"), "SILHOUETTE_MID": Color("0a0918"),
		"BLOCK_BODY": Color("0b0a17"), "BLOCK_FACE": Color("1c1a33"),
		"NEON": Color("ebe7ff"), "NEON_DIM": Color("6c6694"),
		"HAZARD": Color("ff3d8b"), "HAZARD_CORE": Color("ffd6ec"),
		"HAZARD_BODY": Color("1c0718"), "HAZARD_STRIPE": Color(1.0, 0.24, 0.55, 0.35),
		"PLAYER_BODY": Color("000000"), "PLAYER_EDGE": Color("ffffff"), "PLAYER_CORE": Color("ffffff"),
		"SHARD_EDGE": Color("8ff0ff"), "SHARD_CORE": Color("f2feff"),
		"UI_TEXT": Color("f1edff"), "UI_MUTED": Color("a49ec8"), "UI_PANEL": Color(0.03, 0.025, 0.07, 0.88),
		"UI_ACCENT": Color("b48cff"), "UI_CARD": Color(0.045, 0.04, 0.085),
	},
	&"mono": {
		"SKY_TOP": Color("000000"), "SKY_HORIZON": Color("262626"), "FOG": Color("d8d8d8"),
		"SUN": Color("c8c8c8"), "SUN_GLOW": Color("ffffff"),
		"SILHOUETTE_FAR": Color("2b2b2b"), "SILHOUETTE_MID": Color("080808"),
		"BLOCK_BODY": Color("0a0a0a"), "BLOCK_FACE": Color("1a1a1a"),
		"NEON": Color("f2f2f2"), "NEON_DIM": Color("6e6e6e"),
		"HAZARD": Color("ffffff"), "HAZARD_CORE": Color("ffffff"),
		"HAZARD_BODY": Color("000000"), "HAZARD_STRIPE": Color(1.0, 1.0, 1.0, 0.32),
		"PLAYER_BODY": Color("000000"), "PLAYER_EDGE": Color("ffffff"), "PLAYER_CORE": Color("ffffff"),
		"SHARD_EDGE": Color("ffffff"), "SHARD_CORE": Color("e8e8e8"),
		"UI_TEXT": Color("f2f2f2"), "UI_MUTED": Color("9c9c9c"), "UI_PANEL": Color(0.02, 0.02, 0.02, 0.88),
		"UI_ACCENT": Color("ffffff"), "UI_CARD": Color(0.035, 0.035, 0.035),
	},
}

static var theme: StringName = &"red"

static var SKY_TOP: Color
static var SKY_HORIZON: Color
static var FOG: Color
static var SUN: Color
static var SUN_GLOW: Color
static var SILHOUETTE_FAR: Color
static var SILHOUETTE_MID: Color

static var BLOCK_BODY: Color
static var BLOCK_FACE: Color
static var NEON: Color
static var NEON_DIM: Color

static var HAZARD: Color
static var HAZARD_CORE: Color
static var HAZARD_BODY: Color
static var HAZARD_STRIPE: Color

static var PLAYER_BODY: Color
static var PLAYER_EDGE: Color
static var PLAYER_CORE: Color

static var SHARD_EDGE: Color
static var SHARD_CORE: Color

static var UI_TEXT: Color
static var UI_MUTED: Color
static var UI_PANEL: Color
static var UI_ACCENT: Color
static var UI_CARD: Color


static func _static_init() -> void:
	use(&"red")


## Switches every color to [param name]'s theme; returns true when it changed.
static func use(name: StringName) -> bool:
	var table: Dictionary = THEMES.get(name, THEMES[&"red"])
	var changed := theme != name
	theme = name if THEMES.has(name) else &"red"
	SKY_TOP = table.SKY_TOP
	SKY_HORIZON = table.SKY_HORIZON
	FOG = table.FOG
	SUN = table.SUN
	SUN_GLOW = table.SUN_GLOW
	SILHOUETTE_FAR = table.SILHOUETTE_FAR
	SILHOUETTE_MID = table.SILHOUETTE_MID
	BLOCK_BODY = table.BLOCK_BODY
	BLOCK_FACE = table.BLOCK_FACE
	NEON = table.NEON
	NEON_DIM = table.NEON_DIM
	HAZARD = table.HAZARD
	HAZARD_CORE = table.HAZARD_CORE
	HAZARD_BODY = table.HAZARD_BODY
	HAZARD_STRIPE = table.HAZARD_STRIPE
	PLAYER_BODY = table.PLAYER_BODY
	PLAYER_EDGE = table.PLAYER_EDGE
	PLAYER_CORE = table.PLAYER_CORE
	SHARD_EDGE = table.SHARD_EDGE
	SHARD_CORE = table.SHARD_CORE
	UI_TEXT = table.UI_TEXT
	UI_MUTED = table.UI_MUTED
	UI_PANEL = table.UI_PANEL
	UI_ACCENT = table.UI_ACCENT
	UI_CARD = table.UI_CARD
	return changed


static func is_mono() -> bool:
	return theme == &"mono"


static func is_galaxy() -> bool:
	return theme == &"galaxy"
