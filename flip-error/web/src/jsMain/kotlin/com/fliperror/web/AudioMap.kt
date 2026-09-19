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
 * 06_ui_cancel and 17_trail_spark. Where they would have gone is marked below.
 * Nothing is invented to replace them: 05_ui_confirm covers confirmation, a
 * refused purchase makes no sound at all, and the trail borrows 18_speed_whoosh.
 *
 * THERE IS NO MUSIC AND NO AMBIENCE. The pack's eleven sixty-second environments
 * and everything cut to sit under them are listed below as what this game does
 * NOT load. Every name in [oneShots] is a cue with a cause behind it.
 */
object AudioMap {

    /** Where the encoded library lives, relative to the page. */
    const val DIR = "audio/"

    // --- WHAT IS DELIBERATELY NOT HERE ---------------------------------------
    //
    // The pack's environmental half is gone from the game by decision, not by
    // accident. Twenty recordings are still on disk and nothing references them:
    //
    //   20_w1_future_ambience_L1..L5   the city's five-layer room
    //   25_w2_desert_ambience_L1..L5   the desert's five-layer room
    //   02_menu_idle_hum_loop          the menu bed
    //   30_tension_riser_1..4          the tension transitions
    //   35_w1_neon_electric_arc        \
    //   36_w1_distant_machine_hit       > things happening out of sight
    //   45_unease_low_1..3             /
    //
    // The whole layering and crossfade system that played them was deleted with
    // them - the beds, the streamed <audio> elements, the ambience bus, the
    // tension bands and the scheduler that fired events on its own clock. Left
    // switched off but wired up, that machinery would still have been fetching,
    // decoding and looping audio nobody asked for; the point of the decision is
    // that none of it runs, so none of it exists in the active game.
    //
    // Every sound below has a CAUSE: the player did something, the UI did
    // something, or an obstacle did something. Nothing plays because time passed.

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

    // --- world 2's obstacles, each with its own voice -------------------------

    const val SAND_WAVE = "37_w2_sand_wave"
    const val SAND_GEYSER = "38_w2_sand_geyser"
    const val FALLING_RUIN = "39_w2_falling_ruin"
    const val LASER_CHARGE = "40_w2_laser_charge"    // while it warms, where it can be seen
    const val LASER_BLAST = "41_w2_laser_blast"      // the instant it becomes lethal
    const val WIND_BLAST = "42_w2_wind_blast"
    const val COLLAPSE_BRIDGE = "43_w2_collapse_bridge"

    // --- world 3's obstacles, on the same seven recordings ---------------------
    //
    // The pack predates the abyss and contains no recording made for it. The rule
    // is that the pack is the only source, so world 3 gets no sound of its own
    // invention - and it does not have to: seven of these files are short,
    // abstract hazard SFX whose names describe where they were first USED, not
    // what they sound like. A swell is a swell; a burst from below is a burst
    // from below; something coming apart is something coming apart.
    //
    // So this is a deliberate reuse and it is written down here rather than left
    // for someone to discover:
    //
    //   37_w2_sand_wave        -> the swell rolling down the lane
    //   38_w2_sand_geyser      -> an arm coming up out of the floor
    //   39_w2_falling_ruin     -> a bubble coming apart
    //   40_w2_laser_charge     -> a jelly swelling, while it is still safe
    //   41_w2_laser_blast      -> the instant that jelly is lethal
    //   42_w2_wind_blast       -> a ring of pressure passing
    //   43_w2_collapse_bridge  -> a crystal swinging down into the lane
    //
    // Nothing is renamed on disk and nothing is re-encoded. These are aliases.

    const val ABYSS_SWELL = SAND_WAVE
    const val ABYSS_ARM = SAND_GEYSER
    const val ABYSS_SPLIT = FALLING_RUIN
    const val ABYSS_JELLY_WARN = LASER_CHARGE
    const val ABYSS_JELLY_OPEN = LASER_BLAST
    const val ABYSS_RING = WIND_BLAST
    const val ABYSS_CRYSTAL = COLLAPSE_BRIDGE

    // --- world 4's machine, on the same seven recordings -----------------------
    //
    // Same reasoning, and it fits this world better than either of the others:
    // these files are short mechanical noises to begin with. A vent firing is a
    // burst from below, a ram landing is an impact, a rail charging is a charge.
    // The one that needed thought is the gear, and 43 - which is a heavy thing
    // cracking - is the closest the pack comes to a tooth coming round.
    //
    //   37_w2_sand_wave        -> a chain sweeping past
    //   38_w2_sand_geyser      -> a steam vent firing
    //   39_w2_falling_ruin     -> a ram landing, a press closing, a bolt dropping
    //   40_w2_laser_charge     -> anything winding up: ram, rail, vent
    //   41_w2_laser_blast      -> a rail going live
    //   42_w2_wind_blast       -> a gate panel moving
    //   43_w2_collapse_bridge  -> a gear tooth coming round

    const val MACHINE_CHAIN = SAND_WAVE
    const val MACHINE_STEAM = SAND_GEYSER
    const val MACHINE_IMPACT = FALLING_RUIN
    const val MACHINE_WARN = LASER_CHARGE
    const val MACHINE_LIVE = LASER_BLAST
    const val MACHINE_GATE = WIND_BLAST
    const val MACHINE_GEAR = COLLAPSE_BRIDGE

    /**
     * Every recording the game loads, and the whole of it. There is no second
     * list: if a sound is not here it is not fetched, not decoded, not held in
     * memory and not playable.
     */
    val oneShots: List<String> = listOf(
        GAME_ENTER, LEVEL_START, UI_CONFIRM, LEVEL_COMPLETE, STRONG_LOSS,
        PERFECT_FINISH, WORLD_TRANSITION, SECRET_UNLOCK,
        JUMP, DOUBLE_JUMP, LAND, COLLECT, NEAR_MISS, HAZARD_HIT, SPEED_WHOOSH,
        SAND_WAVE, SAND_GEYSER, FALLING_RUIN, LASER_CHARGE, LASER_BLAST,
        WIND_BLAST, COLLAPSE_BRIDGE,
    )
}
