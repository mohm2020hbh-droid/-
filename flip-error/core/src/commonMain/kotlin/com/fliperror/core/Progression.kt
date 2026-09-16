package com.fliperror.core

/**
 * What the player keeps between runs: coins, which STAR COINS they have picked
 * up in each level, which levels are open, and what they are wearing.
 *
 * Pure data with its own text format, so the shell only has to hand it a string
 * from storage and put one back. The payouts live here too, where they can be
 * argued with in a test rather than discovered in play.
 */
object Payout {
    /** First time a level is cleared. */
    const val FIRST_CLEAR = 25
    /** Every clear after that. Low on purpose: replaying is for the coins you missed. */
    const val REPLAY = 5
    /** Each STAR COIN, the first time it is collected in that level. */
    const val STAR = 10
    /** Cleared on the first attempt, no deaths. */
    const val PERFECT = 15
}

data class LevelRecord(
    val completed: Boolean = false,
    /** Indices of the star coins collected in this level, ever. */
    val stars: Set<Int> = emptySet(),
    val bestAttempts: Int = 0,
)

data class Award(val coins: Int, val newStars: Int, val firstClear: Boolean, val perfect: Boolean)

class Progress private constructor(
    var coins: Int,
    private val levels: MutableMap<Int, LevelRecord>,
    private val owned: MutableSet<String>,
    private val equipped: MutableMap<Category, String>,
) {

    constructor() : this(0, HashMap(), HashSet(), HashMap())

    init {
        Shop.items.filter { it.free }.forEach { owned += it.id }
        Shop.defaults.forEach { (c, id) -> equipped.getOrPut(c) { id } }
    }

    fun record(levelId: Int): LevelRecord = levels[levelId] ?: LevelRecord()

    /** Level 1 is always open; each later level opens when the one before is cleared. */
    fun unlocked(levelId: Int): Boolean = levelId <= 1 || record(levelId - 1).completed

    fun starsIn(levelId: Int): Set<Int> = record(levelId).stars

    val totalStars: Int get() = levels.values.sumOf { it.stars.size }

    /**
     * Bank a finished run. Coins are only paid for things that had not been done
     * before, so a cleared level cannot be farmed by replaying it.
     */
    fun finish(levelId: Int, starsCollected: Set<Int>, attempts: Int): Award {
        val before = record(levelId)
        val fresh = starsCollected - before.stars
        val firstClear = !before.completed
        val perfect = attempts <= 1
        var paid = if (firstClear) Payout.FIRST_CLEAR else Payout.REPLAY
        paid += fresh.size * Payout.STAR
        if (perfect && firstClear) paid += Payout.PERFECT
        coins += paid
        levels[levelId] = LevelRecord(
            completed = true,
            stars = before.stars + starsCollected,
            bestAttempts = if (before.bestAttempts == 0) attempts else minOf(before.bestAttempts, attempts),
        )
        return Award(paid, fresh.size, firstClear, perfect)
    }

    /**
     * Bank a coin the moment it is touched, not when the level ends.
     *
     * There are no checkpoints, so a player who reaches 85% with three coins and
     * dies would otherwise lose all three for the crime of being nearly good
     * enough. What they picked up is theirs; the run still has to be finished for
     * the level itself to count. Returns the coins paid, 0 if it was already had.
     */
    fun collectStar(levelId: Int, index: Int): Int {
        val before = record(levelId)
        if (index in before.stars) return 0
        levels[levelId] = before.copy(stars = before.stars + index)
        coins += Payout.STAR
        return Payout.STAR
    }

    fun owns(id: String) = id in owned

    fun buy(id: String): Boolean {
        val item = Shop.byId[id] ?: return false
        if (owns(id)) return false
        if (coins < item.price) return false
        coins -= item.price
        owned += id
        return true
    }

    fun equip(id: String): Boolean {
        val item = Shop.byId[id] ?: return false
        if (!owns(id)) return false
        equipped[item.category] = id
        return true
    }

    fun equipped(category: Category): String = equipped[category] ?: Shop.defaults.getValue(category)

    // --- storage ----------------------------------------------------------
    //
    // One line, four fields. Hand-rolled because a save file is not worth a
    // dependency, and because anything unreadable must degrade to a fresh
    // profile rather than take the game down with it.

    fun serialize(): String = buildString {
        append("v1|").append(coins).append('|')
        append(levels.entries.joinToString(",") { (id, r) ->
            "$id:${if (r.completed) 1 else 0}:${r.stars.sorted().joinToString(".")}:${r.bestAttempts}"
        })
        append('|').append(owned.sorted().joinToString(","))
        append('|').append(equipped.entries.joinToString(",") { (c, id) -> "${c.name}=$id" })
    }

    companion object {
        fun parse(raw: String?): Progress {
            val fresh = Progress()
            if (raw.isNullOrBlank()) return fresh
            val parts = raw.split('|')
            if (parts.size < 5 || parts[0] != "v1") return fresh
            fresh.coins = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
            parts[2].split(',').filter { it.isNotBlank() }.forEach { entry ->
                val f = entry.split(':')
                val id = f.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val stars = f.getOrNull(2).orEmpty().split('.')
                    .mapNotNull { it.toIntOrNull() }.toSet()
                fresh.levels[id] = LevelRecord(
                    completed = f.getOrNull(1) == "1",
                    stars = stars,
                    bestAttempts = f.getOrNull(3)?.toIntOrNull() ?: 0,
                )
            }
            parts[3].split(',').filter { it.isNotBlank() }
                .filter { it in Shop.byId }.forEach { fresh.owned += it }
            parts[4].split(',').filter { it.isNotBlank() }.forEach { pair ->
                val (c, id) = pair.split('=').let { it.getOrNull(0) to it.getOrNull(1) }
                val cat = Category.entries.firstOrNull { it.name == c } ?: return@forEach
                if (id != null && id in Shop.byId && id in fresh.owned) fresh.equipped[cat] = id
            }
            return fresh
        }
    }
}
