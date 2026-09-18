package com.fliperror.web

/**
 * THE AUDIO MAP: every file in the FLIP ERROR sound pack, and the job it does.
 *
 * The pack arrived as five ZIPs that are one library - 42 recordings, no
 * duplicates between the parts. This file is the single place that says what
 * each one is FOR, so the answer to "where is 38_w2_sand_geyser used" is one
 * grep and not a hunt through an engine.
 *
 * Three of the names its own README lists are not in the pack: 04_button_click,
 * 06_ui_cancel and 17_trail_spark. Where they would have gone is marked below,
 * and the synthesised voice covers those three jobs instead - see Audio.
 *
 * NOTHING HERE IS MUSIC. The five ambience beds per world are sixty-second
 * environments meant to be layered and crossfaded, which is what the tension
 * system does with them. They are never sequenced, never put on a grid, and
 * never treated as a track.
 */
object AudioMap {

    /** Where the encoded library lives, relative to the page. */
    const val DIR = "audio/"

    // --- the room: five layers per world, crossfaded by progress -------------
    //
    // Each is a full sixty seconds. They are versions of the same place at
    // different pressures, so they layer rather than replace - which is why the
    // tension system can sit BETWEEN two of them instead of switching.

    val world1Beds = listOf(
        "20_w1_future_ambience_L1_60s",
        "20_w1_future_ambience_L2_60s",
        "20_w1_future_ambience_L3_60s",
        "20_w1_future_ambience_L4_60s",
        "20_w1_future_ambience_L5_60s",
    )
    val world2Beds = listOf(
        "25_w2_desert_ambience_L1_60s",
        "25_w2_desert_ambience_L2_60s",
        "25_w2_desert_ambience_L3_60s",
        "25_w2_desert_ambience_L4_60s",
        "25_w2_desert_ambience_L5_60s",
    )
    fun bedsFor(world: Int) = if (world >= 2) world2Beds else world1Beds

    /** The menus have their own room, quieter and going nowhere. */
    const val MENU_BED = "02_menu_idle_hum_loop"

    // --- moments -------------------------------------------------------------

    const val GAME_ENTER = "01_game_enter_hum"       // the first second of the game
    const val LEVEL_START = "03_level_start_riser"
    const val UI_CONFIRM = "05_ui_confirm"           // 04_button_click / 06_ui_cancel are absent
    const val LEVEL_COMPLETE = "07_level_complete"
    const val STRONG_LOSS = "08_strong_loss"
    const val PERFECT_FINISH = "09_perfect_finish"   // a clear with every coin
    const val WORLD_TRANSITION = "10_world_transition"
    const val SECRET_UNLOCK = "19_secret_unlock"     // a cosmetic bought

    // --- the player ----------------------------------------------------------

    const val JUMP = "11_jump"
    const val DOUBLE_JUMP = "12_double_jump"
    const val LAND = "13_land"
    const val COLLECT = "14_collect_star"
    const val NEAR_MISS = "15_near_miss"
    const val HAZARD_HIT = "16_hazard_hit"           // what killed you, under the loss
    const val SPEED_WHOOSH = "18_speed_whoosh"       // 17_trail_spark is absent; this stands in,
                                                     // sparsely, so the trail has a voice at all

    // --- the risers ----------------------------------------------------------
    //
    // One per step up in tension, used at the transition and nowhere else. The
    // README asks for them sparingly and it is right: a riser on every bar would
    // be a rhythm, and a rhythm is the thing this game does not have.

    val risers = listOf("30_tension_riser_1", "30_tension_riser_2",
                        "30_tension_riser_3", "30_tension_riser_4")

    // --- things happening out of sight ---------------------------------------

    val world1Events = listOf("35_w1_neon_electric_arc", "36_w1_distant_machine_hit")
    val unease = listOf("45_unease_low_1", "45_unease_low_2", "45_unease_low_3")

    // --- world 2's obstacles, each with its own voice -------------------------

    const val SAND_WAVE = "37_w2_sand_wave"
    const val SAND_GEYSER = "38_w2_sand_geyser"
    const val FALLING_RUIN = "39_w2_falling_ruin"
    const val LASER_CHARGE = "40_w2_laser_charge"    // while it warms, where it can be seen
    const val LASER_BLAST = "41_w2_laser_blast"      // the instant it becomes lethal
    const val WIND_BLAST = "42_w2_wind_blast"
    const val COLLAPSE_BRIDGE = "43_w2_collapse_bridge"

    /** Everything that is loaded as a one-shot sample, in one list. */
    val oneShots: List<String> = listOf(
        GAME_ENTER, LEVEL_START, UI_CONFIRM, LEVEL_COMPLETE, STRONG_LOSS,
        PERFECT_FINISH, WORLD_TRANSITION, SECRET_UNLOCK,
        JUMP, DOUBLE_JUMP, LAND, COLLECT, NEAR_MISS, HAZARD_HIT, SPEED_WHOOSH,
        SAND_WAVE, SAND_GEYSER, FALLING_RUIN, LASER_CHARGE, LASER_BLAST,
        WIND_BLAST, COLLAPSE_BRIDGE,
    ) + risers + world1Events + unease

    /** Everything streamed rather than decoded: the long beds. */
    val streamed: List<String> = world1Beds + world2Beds + listOf(MENU_BED)

    /** Every recording the pack shipped that this build actually plays. */
    val all: List<String> = oneShots + streamed
}
