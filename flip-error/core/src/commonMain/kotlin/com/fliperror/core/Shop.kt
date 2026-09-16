package com.fliperror.core

/**
 * Everything the STAR COINS buy. All of it is appearance: no item here touches
 * the jump arc, the run speed, the hitboxes or the number of lives, because the
 * only thing that gets a player past the finish spike is having learned it.
 */
enum class Category { SHAPE, COLOR, TRAIL, FACE }

data class Cosmetic(
    val id: String,
    val name: String,
    val category: Category,
    val price: Int,
) {
    val free get() = price == 0
}

object Shop {

    val items: List<Cosmetic> = listOf(
        Cosmetic("shape.square", "SQUARE", Category.SHAPE, 0),
        Cosmetic("shape.triangle", "TRIANGLE", Category.SHAPE, 120),
        Cosmetic("shape.circle", "CIRCLE", Category.SHAPE, 120),
        Cosmetic("shape.diamond", "DIAMOND", Category.SHAPE, 150),
        Cosmetic("shape.star", "STAR", Category.SHAPE, 200),
        Cosmetic("shape.cat", "CAT", Category.SHAPE, 260),
        Cosmetic("shape.hexagon", "HEXAGON", Category.SHAPE, 140),
        Cosmetic("shape.octagon", "OCTAGON", Category.SHAPE, 160),
        Cosmetic("shape.crystal", "CRYSTAL", Category.SHAPE, 220),
        Cosmetic("shape.bolt", "BOLT", Category.SHAPE, 240),
        Cosmetic("shape.arrow", "ARROW", Category.SHAPE, 180),
        Cosmetic("shape.ring", "RING", Category.SHAPE, 200),
        Cosmetic("shape.cross", "CROSS", Category.SHAPE, 180),

        Cosmetic("color.yellow", "YELLOW", Category.COLOR, 0),
        Cosmetic("color.cyan", "CYAN", Category.COLOR, 80),
        Cosmetic("color.pink", "PINK", Category.COLOR, 80),
        Cosmetic("color.green", "GREEN", Category.COLOR, 100),
        Cosmetic("color.violet", "VIOLET", Category.COLOR, 100),
        Cosmetic("color.orange", "ORANGE", Category.COLOR, 120),

        Cosmetic("trail.basic", "BASIC", Category.TRAIL, 0),
        Cosmetic("trail.neon", "NEON", Category.TRAIL, 140),
        Cosmetic("trail.spark", "SPARK", Category.TRAIL, 180),
        Cosmetic("trail.pulse", "PULSE", Category.TRAIL, 220),
        Cosmetic("trail.rainbow", "RAINBOW", Category.TRAIL, 300),

        Cosmetic("face.classic", "CLASSIC", Category.FACE, 0),
        Cosmetic("face.cool", "COOL", Category.FACE, 90),
        Cosmetic("face.sleepy", "SLEEPY", Category.FACE, 90),
        Cosmetic("face.grin", "GRIN", Category.FACE, 130),
    )

    val byId: Map<String, Cosmetic> = items.associateBy { it.id }

    fun of(category: Category) = items.filter { it.category == category }

    /** What a new player starts with: exactly one free item per category. */
    val defaults: Map<Category, String> =
        Category.entries.associateWith { c -> of(c).first { it.free }.id }
}
