package com.fliperror.core

/**
 * A landscape-only game still has to answer one question honestly: is this
 * screen really portrait, or does the game merely sit in a frame that happens
 * to be taller than it is wide? An embedded panel, a preview thumbnail and a
 * narrow desktop window all report a portrait *viewport* on a device that is
 * not portrait at all — blocking those is how a player ends up staring at
 * "rotate your device" on a screen that is already rotated.
 *
 * So the gate blocks on evidence, never on the absence of it: it asks for a
 * rotation only when the viewport is portrait AND the device itself says it is
 * portrait AND the pointer is a handheld one. Every other case plays.
 */
enum class DeviceOrientation { PORTRAIT, LANDSCAPE, UNKNOWN }

/** Why the gate decided what it decided. Surfaced for tests and diagnostics. */
enum class GateReason {
    /** Explicitly bypassed: dev flag, or the player took the escape hatch. */
    OVERRIDE,

    /** The frame has no measurable size yet, so nothing can be concluded. */
    VIEWPORT_UNMEASURED,

    /** Small enough to be a thumbnail or card preview, not a play session. */
    VIEWPORT_PREVIEW,

    /** Already wide enough to play. The common case, and the one that lifts the gate. */
    VIEWPORT_LANDSCAPE,

    /** A mouse-driven screen: a narrow window is the user's own choice, not a mistake. */
    POINTER_NOT_HANDHELD,

    /** The device is rotated; only the frame around the game is narrow. */
    DEVICE_LANDSCAPE,

    /** The device will not say which way it is facing, so it does not get a vote. */
    DEVICE_UNKNOWN,

    /** The one case that earns the rotate screen. */
    HANDHELD_PORTRAIT,
}

/** True only for the single reason that justifies hiding the game. */
val GateReason.blocksPlay: Boolean get() = this == GateReason.HANDHELD_PORTRAIT

/** Everything the shell manages to learn about the screen, in CSS pixels. */
data class ViewportProbe(
    val width: Int,
    val height: Int,
    val device: DeviceOrientation,
    val handheldPointer: Boolean,
    val override: Boolean = false,
)

/** Below this the frame is a preview surface, not somewhere anyone is playing. */
const val PREVIEW_MAX_WIDTH = 280
const val PREVIEW_MAX_HEIGHT = 200

fun decideGate(p: ViewportProbe): GateReason = when {
    p.override -> GateReason.OVERRIDE
    p.width <= 0 || p.height <= 0 -> GateReason.VIEWPORT_UNMEASURED
    p.width < PREVIEW_MAX_WIDTH || p.height < PREVIEW_MAX_HEIGHT -> GateReason.VIEWPORT_PREVIEW
    p.width > p.height -> GateReason.VIEWPORT_LANDSCAPE
    !p.handheldPointer -> GateReason.POINTER_NOT_HANDHELD
    p.device == DeviceOrientation.LANDSCAPE -> GateReason.DEVICE_LANDSCAPE
    p.device == DeviceOrientation.UNKNOWN -> GateReason.DEVICE_UNKNOWN
    else -> GateReason.HANDHELD_PORTRAIT
}
