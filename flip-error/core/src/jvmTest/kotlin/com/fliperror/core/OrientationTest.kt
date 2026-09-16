package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A phone held upright: the only screen that should ever be asked to rotate. */
private fun phonePortrait(w: Int = 390, h: Int = 844) =
    ViewportProbe(w, h, DeviceOrientation.PORTRAIT, handheldPointer = true)

/** The same phone, turned. */
private fun phoneLandscape(w: Int = 844, h: Int = 390) =
    ViewportProbe(w, h, DeviceOrientation.LANDSCAPE, handheldPointer = true)

class OrientationTest {

    @Test fun `a phone held upright is the one case that asks for a rotation`() {
        assertEquals(GateReason.HANDHELD_PORTRAIT, decideGate(phonePortrait()))
        assertTrue(decideGate(phonePortrait()).blocksPlay)
    }

    @Test fun `turning the phone lifts the gate`() {
        val reason = decideGate(phoneLandscape())
        assertEquals(GateReason.VIEWPORT_LANDSCAPE, reason)
        assertFalse(reason.blocksPlay)
    }

    // The bug this gate was rewritten for: the player rotates, the host panel
    // stays a tall sliver, and a viewport-only test traps them forever.
    @Test fun `a rotated phone in a tall embed panel plays instead of nagging`() {
        val panel = ViewportProbe(360, 700, DeviceOrientation.LANDSCAPE, handheldPointer = true)
        assertEquals(GateReason.DEVICE_LANDSCAPE, decideGate(panel))
        assertFalse(decideGate(panel).blocksPlay)
    }

    @Test fun `a narrow desktop window is the users own choice, not a mistake`() {
        val window = ViewportProbe(520, 900, DeviceOrientation.LANDSCAPE, handheldPointer = false)
        assertEquals(GateReason.POINTER_NOT_HANDHELD, decideGate(window))
        // Even a desktop whose screen somehow reads portrait keeps playing.
        val pivoted = ViewportProbe(520, 900, DeviceOrientation.PORTRAIT, handheldPointer = false)
        assertFalse(decideGate(pivoted).blocksPlay)
    }

    @Test fun `a device that will not report its orientation never blocks`() {
        val unknown = ViewportProbe(390, 844, DeviceOrientation.UNKNOWN, handheldPointer = true)
        assertEquals(GateReason.DEVICE_UNKNOWN, decideGate(unknown))
        assertFalse(decideGate(unknown).blocksPlay)
    }

    @Test fun `a thumbnail sized frame shows the game, not the rotate screen`() {
        val thumb = ViewportProbe(240, 180, DeviceOrientation.PORTRAIT, handheldPointer = true)
        assertEquals(GateReason.VIEWPORT_PREVIEW, decideGate(thumb))
        assertFalse(decideGate(thumb).blocksPlay)
    }

    @Test fun `a frame measured before layout never blocks`() {
        for (p in listOf(
            ViewportProbe(0, 0, DeviceOrientation.PORTRAIT, handheldPointer = true),
            ViewportProbe(0, 844, DeviceOrientation.PORTRAIT, handheldPointer = true),
            ViewportProbe(390, 0, DeviceOrientation.PORTRAIT, handheldPointer = true),
            ViewportProbe(-1, -1, DeviceOrientation.PORTRAIT, handheldPointer = true),
        )) {
            assertEquals(GateReason.VIEWPORT_UNMEASURED, decideGate(p), "probe $p")
            assertFalse(decideGate(p).blocksPlay)
        }
    }

    @Test fun `the override wins over every other signal`() {
        val worst = phonePortrait().copy(override = true)
        assertEquals(GateReason.OVERRIDE, decideGate(worst))
        assertFalse(decideGate(worst).blocksPlay)
    }

    // Requirement: the rotate screen must disappear on its own the moment the
    // frame becomes wider than it is tall, with no further input.
    @Test fun `the gate clears as soon as width passes height`() {
        val blocked = phonePortrait(500, 520)
        assertTrue(decideGate(blocked).blocksPlay)
        assertFalse(decideGate(blocked.copy(width = 521)).blocksPlay)
        // A square frame is still not landscape, so it keeps the gate.
        assertTrue(decideGate(blocked.copy(width = 520)).blocksPlay)
    }

    @Test fun `no landscape viewport can ever be blocked`() {
        for (w in listOf(300, 480, 844, 1280, 2400))
            for (h in listOf(200, 299, 390, 600))
                for (device in DeviceOrientation.entries)
                    for (touch in listOf(true, false)) {
                        if (w <= h) continue
                        val p = ViewportProbe(w, h, device, touch)
                        assertFalse(decideGate(p).blocksPlay, "probe $p")
                    }
    }

    @Test fun `exactly one reason is allowed to hide the game`() {
        assertEquals(listOf(GateReason.HANDHELD_PORTRAIT), GateReason.entries.filter { it.blocksPlay })
    }

    @Test fun `the preview cutoff is exclusive on both axes`() {
        val atCutoff = ViewportProbe(PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT + 200,
            DeviceOrientation.PORTRAIT, handheldPointer = true)
        assertEquals(GateReason.HANDHELD_PORTRAIT, decideGate(atCutoff))
        assertEquals(GateReason.VIEWPORT_PREVIEW, decideGate(atCutoff.copy(width = PREVIEW_MAX_WIDTH - 1)))
    }
}
