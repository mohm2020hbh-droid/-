package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionTest {

    @Test fun `a new profile owns one free item per category and nothing else`() {
        val p = Progress()
        assertEquals(0, p.coins)
        Category.entries.forEach { c ->
            val id = p.equipped(c)
            assertTrue(p.owns(id), "$c is wearing $id without owning it")
            assertEquals(0, Shop.byId.getValue(id).price, "$c starts in a paid item")
        }
        assertEquals(Category.entries.size, Shop.items.count { it.free },
            "there must be exactly one free item per category")
    }

    @Test fun `level 1 is open and level 2 waits for it`() {
        val p = Progress()
        assertTrue(p.unlocked(1))
        assertFalse(p.unlocked(2))
        p.finish(1, setOf(0), attempts = 4)
        assertTrue(p.unlocked(2))
        assertFalse(p.unlocked(3))
    }

    @Test fun `a first clear pays, a replay barely does`() {
        val p = Progress()
        val first = p.finish(1, emptySet(), attempts = 9)
        assertEquals(Payout.FIRST_CLEAR, first.coins)
        assertTrue(first.firstClear)
        val again = p.finish(1, emptySet(), attempts = 9)
        assertEquals(Payout.REPLAY, again.coins)
        assertFalse(again.firstClear)
        // The property that matters is not that grinding is impossible, but that
        // it is the worst way to earn: finding a coin you missed beats replaying,
        // and five replays are still worth less than clearing something new.
        assertTrue(Payout.STAR > Payout.REPLAY * 1.5,
            "a star coin (${Payout.STAR}) barely beats a replay (${Payout.REPLAY})")
        assertTrue(Payout.REPLAY * 5 <= Payout.FIRST_CLEAR,
            "five replays (${Payout.REPLAY * 5}) should not match a first clear (${Payout.FIRST_CLEAR})")
    }

    @Test fun `a star coin pays once, however many times it is collected`() {
        val p = Progress()
        val a = p.finish(1, setOf(0, 2), attempts = 5)
        assertEquals(2, a.newStars)
        assertEquals(Payout.FIRST_CLEAR + 2 * Payout.STAR, a.coins)
        val b = p.finish(1, setOf(0, 2), attempts = 5)
        assertEquals(0, b.newStars)
        assertEquals(Payout.REPLAY, b.coins)
        val c = p.finish(1, setOf(0, 1, 2), attempts = 5)
        assertEquals(1, c.newStars, "only the newly found coin pays")
        assertEquals(setOf(0, 1, 2), p.starsIn(1))
    }

    @Test fun `a perfect first run pays a bonus, a perfect replay does not`() {
        val a = Progress()
        assertEquals(Payout.FIRST_CLEAR + Payout.PERFECT, a.finish(1, emptySet(), attempts = 1).coins)
        val b = Progress()
        b.finish(1, emptySet(), attempts = 12)
        assertEquals(Payout.REPLAY, b.finish(1, emptySet(), attempts = 1).coins,
            "a perfect replay of a cleared level must not reopen the bonus")
    }

    @Test fun `buying spends, and only once`() {
        val p = Progress()
        val item = Shop.of(Category.COLOR).first { !it.free }
        assertFalse(p.buy(item.id), "bought with an empty purse")
        p.coins = item.price
        assertTrue(p.buy(item.id))
        assertEquals(0, p.coins)
        assertTrue(p.owns(item.id))
        assertFalse(p.buy(item.id), "bought the same item twice")
    }

    @Test fun `you cannot wear what you do not own`() {
        val p = Progress()
        val item = Shop.of(Category.SHAPE).first { !it.free }
        assertFalse(p.equip(item.id))
        assertEquals(Shop.defaults.getValue(Category.SHAPE), p.equipped(Category.SHAPE))
        p.coins = item.price
        p.buy(item.id)
        assertTrue(p.equip(item.id))
        assertEquals(item.id, p.equipped(Category.SHAPE))
    }

    @Test fun `nothing in the shop sells an advantage`() {
        // The catalogue is the guard rail: if a future item is not cosmetic, its
        // category has to be invented here first, and this test is where that
        // conversation happens.
        assertEquals(setOf(Category.SHAPE, Category.COLOR, Category.TRAIL, Category.FACE),
            Shop.items.map { it.category }.toSet())
        val suspicious = listOf("jump", "speed", "shield", "revive", "slow", "life", "boost", "skip")
        Shop.items.forEach { item ->
            suspicious.forEach { word ->
                assertFalse(item.id.contains(word, true) || item.name.contains(word, true),
                    "'${item.name}' sounds like power, not paint")
            }
        }
    }

    @Test fun `a save survives a round trip`() {
        val p = Progress()
        p.finish(1, setOf(0, 2), attempts = 3)
        p.finish(2, setOf(1), attempts = 7)
        p.coins = 500
        val item = Shop.of(Category.TRAIL).first { !it.free }
        p.buy(item.id); p.equip(item.id)

        val back = Progress.parse(p.serialize())
        assertEquals(p.coins, back.coins)
        assertEquals(setOf(0, 2), back.starsIn(1))
        assertEquals(setOf(1), back.starsIn(2))
        assertTrue(back.unlocked(2))
        assertTrue(back.owns(item.id))
        assertEquals(item.id, back.equipped(Category.TRAIL))
        assertEquals(3, back.totalStars)
        assertEquals(p.serialize(), back.serialize())
    }

    @Test fun `a broken save becomes a fresh profile instead of a crash`() {
        for (junk in listOf(null, "", "   ", "v2|nonsense", "v1|", "v1|x|y|z|w",
                            "v1|12|1:1:zzz:2|not.an.item|SHAPE=nope", "|||||")) {
            val p = Progress.parse(junk)
            assertTrue(p.coins >= 0, "junk save '$junk' produced ${p.coins} coins")
            Category.entries.forEach { assertTrue(p.owns(p.equipped(it))) }
        }
    }

    @Test fun `a save cannot dress the player in something unpaid`() {
        val forged = "v1|0||color.yellow|COLOR=color.orange"
        val p = Progress.parse(forged)
        assertEquals("color.yellow", p.equipped(Category.COLOR),
            "a hand-edited save equipped an item it does not own")
    }

    @Test fun `clearing both levels does not buy out the shop`() {
        val p = Progress()
        p.finish(1, setOf(0, 1, 2), attempts = 1)
        p.finish(2, setOf(0, 1, 2), attempts = 1)
        val everything = Shop.items.filter { !it.free }.sumOf { it.price }
        assertTrue(p.coins < everything / 4,
            "a single perfect pass through the game earns ${p.coins} of $everything")
        assertTrue(p.coins >= Shop.items.filter { !it.free }.minOf { it.price },
            "a perfect pass through both levels should buy at least one thing")
    }
}

class StarPersistenceTest {

    @Test fun `a coin picked up is kept even when the run is lost`() {
        val p = Progress()
        assertEquals(Payout.STAR, p.collectStar(1, 0))
        assertEquals(Payout.STAR, p.collectStar(1, 2))
        // ...and then the player dies at 85%. No finish() is ever called.
        assertEquals(setOf(0, 2), p.starsIn(1))
        assertEquals(2 * Payout.STAR, p.coins)
        assertFalse(p.unlocked(2), "dying must still not unlock the next level")
    }

    @Test fun `picking the same coin up again pays nothing`() {
        val p = Progress()
        p.collectStar(1, 0)
        assertEquals(0, p.collectStar(1, 0))
        assertEquals(Payout.STAR, p.coins)
    }

    @Test fun `finishing does not pay twice for coins already banked`() {
        val p = Progress()
        p.collectStar(1, 0)
        p.collectStar(1, 1)
        val award = p.finish(1, setOf(0, 1, 2), attempts = 6)
        assertEquals(1, award.newStars, "only the coin found on the winning run is new")
        assertEquals(Payout.FIRST_CLEAR + Payout.STAR, award.coins)
        assertEquals(setOf(0, 1, 2), p.starsIn(1))
    }

    @Test fun `banked coins survive the save`() {
        val p = Progress()
        p.collectStar(2, 1)
        val back = Progress.parse(p.serialize())
        assertEquals(setOf(1), back.starsIn(2))
        assertEquals(Payout.STAR, back.coins)
    }
}

class SettingsTest {

    @Test fun `a new profile plays with sound on and in english`() {
        val s = Settings()
        assertTrue(s.music && s.sfx && s.vibration)
        assertFalse(s.reduceEffects || s.colorblind)
        assertEquals(Lang.EN, s.lang)
    }

    @Test fun `settings survive a round trip`() {
        val s = Settings()
        s.music = false; s.vibration = false; s.colorblind = true; s.lang = Lang.AR
        val back = Settings.parse(s.serialize())
        assertFalse(back.music); assertTrue(back.sfx); assertFalse(back.vibration)
        assertFalse(back.reduceEffects); assertTrue(back.colorblind)
        assertEquals(Lang.AR, back.lang)
        assertEquals(s.serialize(), back.serialize())
    }

    @Test fun `a broken settings blob becomes the defaults`() {
        for (junk in listOf(null, "", "s9|11111|EN", "s1|", "s1|xx|ZZ", "||")) {
            val s = Settings.parse(junk)
            assertTrue(s.music && s.sfx, "junk '$junk' turned the sound off")
            assertEquals(Lang.EN, s.lang)
        }
    }
}

class UnlockAllTest {

    @Test fun `the testing switch opens every door and nothing else`() {
        val p = Progress()
        assertFalse(p.unlocked(5))
        p.unlockAllForTesting = true
        (1..6).forEach { assertTrue(p.unlocked(it), "level $it stayed shut") }
        // it is a door key, not a cheat: nothing is completed, nothing is owned,
        // and the purse has not moved.
        assertEquals(0, p.coins)
        assertEquals(0, p.totalStars)
        assertFalse(p.record(1).completed)
    }

    @Test fun `turning it back off restores the real progression`() {
        val p = Progress()
        p.unlockAllForTesting = true
        assertTrue(p.unlocked(4))
        p.unlockAllForTesting = false
        assertFalse(p.unlocked(2), "the door stayed open after the key was taken away")
        p.finish(1, emptySet(), attempts = 3)
        assertTrue(p.unlocked(2))
    }

    @Test fun `the switch survives a save, and an old save defaults it off`() {
        val s = Settings()
        s.unlockAll = true
        assertTrue(Settings.parse(s.serialize()).unlockAll)
        // a settings blob written before the switch existed
        assertFalse(Settings.parse("s1|11100|EN").unlockAll)
    }
}
