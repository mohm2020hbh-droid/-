package com.carom.core.level

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ElementsParserTest {

    private fun parse(elements: String, extra: String = "") = LevelParser.parse(
        "e",
        """{"bounces": 2, "ball": [450, 1600], "goal": [450, 400] $extra, "elements": [$elements]}""",
    )

    @Test
    fun levelsWithoutElementsKeepTheirOldMeaning() {
        val l = LevelParser.parse("plain", """{"bounces": 2, "ball": [450, 1600], "goal": [450, 400]}""")
        assertTrue(l.elements.isEmpty())
        assertEquals(1, l.exitRequired)
        assertEquals(LevelDefaults.DRAG, l.drag, 0.0)
        assertEquals(0.0, l.friction, 0.0)
    }

    @Test
    fun aBoosterReadsItsPositionRotationScaleAndForce() {
        val e = parse("""{"kind": "booster", "pos": [450, 900], "scale": [300, 120], "rotation": -90, "force": 6.3}""").elements.single()
        assertEquals(ElementKind.BOOSTER, e.kind)
        assertEquals(Shape.RECT, e.shape)
        assertEquals(450.0, e.x, 0.0)
        assertEquals(-90.0, e.rotation, 0.0)
        assertEquals(300.0, e.scaleX, 0.0)
        assertEquals(6.3, e.force, 0.0)
        assertFalse(e.physical)
    }

    @Test
    fun theReferencesOwnNamesWork() {
        val e = parse(
            """{"collisionType": "DeathZone", "position": [100, 200], "size": [50, 60], "genericValue": 3, "genericVector": [0, 1],
                "isDeathTrigger": true, "isPhysical": false, "isSwitched": true}""",
        ).elements.single()
        assertEquals(ElementKind.DEATH, e.kind)
        assertEquals(100.0, e.x, 0.0)
        assertEquals(3.0, e.value, 0.0)
        assertEquals(1.0, e.vector.y, 0.0)
        assertTrue(e.deathTrigger)
        assertTrue(e.switched)
        assertEquals(ElementKind.BALL_CONTAINER, ElementKind.parse("BallContainer"))
        assertEquals(ElementKind.SLOWMO_ZONE, ElementKind.parse("SlowMo TouchZone"))
        assertEquals(ElementKind.TOUCH_ZONE, ElementKind.parse("touch_zone"))
    }

    @Test
    fun aPortalIsACircleWithItsLinkBoostAndDecay() {
        val e = parse("""{"kind": "portal", "id": "a", "link": "b", "pos": [1, 2], "radius": 70, "boost": 1.2, "decay": 0.1, "angularSpeed": 90}""").elements.single()
        assertEquals(Shape.CIRCLE, e.shape)
        assertEquals(140.0, e.scaleX, 0.0)
        assertEquals("b", e.link)
        assertEquals(1.2, e.boost, 0.0)
        assertEquals(0.1, e.decay, 0.0)
        assertEquals(90.0, e.angularSpeed, 0.0)
    }

    @Test
    fun movingScalingAndRotatingAreReadAsBlocks() {
        val e = parse(
            """{"kind": "solid", "pos": [500, 500], "scale": [100, 100], "snap": [10, 10],
                "moving": {"to": [700, 500], "period": 3, "wave": "linear", "phase": 0.25},
                "scaling": {"to": [200, 100], "period": 5},
                "rotating": {"speed": 30}}""",
        ).elements.single()
        assertEquals(700.0, e.moving!!.toX, 0.0)
        assertEquals(Wave.LINEAR, e.moving!!.wave)
        assertEquals(0.25, e.moving!!.phase, 0.0)
        assertEquals(200.0, e.scaling!!.toX, 0.0)
        assertEquals(30.0, e.rotating!!.speed, 0.0)
        assertEquals(10.0, e.snap!!.x, 0.0)
        assertTrue(e.animated)
        assertNull(parse("""{"kind": "solid", "pos": [5, 5], "scale": [9, 9]}""").elements.single().moving)
    }

    @Test
    fun badElementsAreRefusedWithAMessage() {
        for (bad in listOf(
            """{"pos": [1, 1]}""",
            """{"kind": "jelly", "pos": [1, 1]}""",
            """{"kind": "booster"}""",
            """{"kind": "solid", "pos": [1, 1], "moving": {"period": 2}}""",
            """{"kind": "solid", "pos": [1, 1], "shape": "star"}""",
        )) {
            try {
                parse(bad)
                fail("accepted: $bad")
            } catch (e: LevelFormatException) {
                assertTrue(e.message!!.contains("element #0"))
            }
        }
    }

    @Test
    fun theValidatorChecksLinksIdsAndTheExit() {
        fun problems(elements: String, extra: String = "") = LevelValidator.problems(parse(elements, extra))
        assertTrue(problems("""{"kind": "portal", "id": "a", "pos": [100, 100], "scale": [100, 100]}""").any { "no 'link'" in it })
        assertTrue(problems("""{"kind": "portal", "id": "a", "link": "zzz", "pos": [100, 100], "scale": [100, 100]}""").any { "not a portal" in it })
        val pair = """{"kind": "portal", "id": "a", "link": "b", "pos": [100, 100], "scale": [100, 100]},
                      {"kind": "portal", "id": "b", "link": "a", "pos": [800, 100], "scale": [100, 100]}"""
        assertTrue(problems(pair).isEmpty())
        assertTrue(problems("""{"kind": "solid", "pos": [99999, 5], "scale": [9, 9]}""").any { "outside" in it })
        assertTrue(problems("", extra = """, "exitRequired": 2""").any { "exit needs 2" in it })
        assertTrue(problems("""{"kind": "ballContainer", "pos": [100, 1000], "scale": [80, 80], "value": 1}""", extra = """, "exitRequired": 2""").isEmpty())
        assertTrue(problems("""{"kind": "death", "pos": [450, 1600], "scale": [300, 300]}""").any { "ball starts inside" in it })
    }
}
