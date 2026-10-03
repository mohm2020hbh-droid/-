package com.carom.core.audio

/**
 * The groups of sound the player controls. Master sits over all of them: Music is the background track, Sfx is every sound the game
 * itself makes (gameplay, special elements, feedback) and Ui is the buttons and menus.
 */
enum class AudioBus { MUSIC, SFX, UI }

/** What a sound is for. Gameplay, power-ups and feedback share the Sfx bus; the category is how they are told apart and balanced. */
enum class AudioCategory { GAMEPLAY, POWER_UP, FEEDBACK, UI, MUSIC }

/**
 * Every sound the game plays, in one place: which bus it belongs to (so which volume and switch control it), how important it is when
 * many sounds compete, how loud it is next to the other sounds of its bus ([level]), and how often it may repeat ([burst] plays within
 * [window] seconds, no more, so no sound can be stacked into a buzz while real, quick, consecutive events still all sound).
 *
 * Nothing plays a sound by any other route: screens ask for a cue, the audio manager works out its gain from the settings and the gate.
 */
enum class AudioCue(
    val bus: AudioBus,
    val category: AudioCategory,
    val priority: Int,
    val level: Double,
    val burst: Int,
    val window: Double,
) {
    // ---- gameplay
    /** The ball hits something: the recorded bounce, louder for a harder hit. Together with the shake and the vibration, on the same frame. */
    BOUNCE(AudioBus.SFX, AudioCategory.GAMEPLAY, VoicePool.Priority.BOUNCE, 1.0, 4, 0.05),

    /** The level is won: the heartbeat that closes the fan's spin. */
    SUCCESS(AudioBus.SFX, AudioCategory.GAMEPLAY, VoicePool.Priority.EXIT_COMPLETE, 0.9, 1, 0.5),

    /** The fan at the goal speeding up (part of the win, stopped if the level is left). */
    SUCCESS_SPIN(AudioBus.SFX, AudioCategory.GAMEPLAY, VoicePool.Priority.EXIT_COMPLETE, 0.8, 1, 0.3),

    /** The fan bursting (part of the win). */
    SUCCESS_BURST(AudioBus.SFX, AudioCategory.GAMEPLAY, VoicePool.Priority.EXIT_COMPLETE, 0.95, 1, 0.3),

    /** A ball breaks (no bounces left, or a deadly zone): the loss. Never the clear pulse. */
    FAIL_BREAK(AudioBus.SFX, AudioCategory.GAMEPLAY, VoicePool.Priority.EXPLOSION, 0.85, 2, 0.1),

    /** A ball ran out of speed and fades: the other way to lose. */
    FAIL_STOP(AudioBus.SFX, AudioCategory.GAMEPLAY, VoicePool.Priority.BOUNCE, 0.6, 1, 0.2),

    // ---- special elements (power-ups)
    /** A ball loses half its speed in a clock. Its own sound, unlike the bounce, the pulse, the buttons and the music. */
    CLOCK(AudioBus.SFX, AudioCategory.POWER_UP, VoicePool.Priority.CLOCK, 0.5, 2, 0.1),

    /** A ball touches a switch and something opens or closes. */
    SWITCH(AudioBus.SFX, AudioCategory.POWER_UP, VoicePool.Priority.PORTAL, 0.5, 1, 0.1),

    /** A ball goes through a portal. */
    PORTAL(AudioBus.SFX, AudioCategory.POWER_UP, VoicePool.Priority.PORTAL, 0.7, 2, 0.1),

    // ---- feedback
    /** A ball reached an exit that still needs more. */
    EXIT_PARTIAL(AudioBus.SFX, AudioCategory.FEEDBACK, VoicePool.Priority.EXIT_PARTIAL, 0.6, 1, 0.15),

    /**
     * The restart button's first press while the ball is very slow: a very light, rounded tick. The three presses 1 -> 2 -> 3 are one gesture, and these
     * three sounds say how far it has got. Nothing else plays them.
     */
    RESTART_TAP_1(AudioBus.SFX, AudioCategory.FEEDBACK, VoicePool.Priority.EXIT_PARTIAL, 0.28, 1, 0.12),

    /** The second press: the same tick a step higher, with the first one's echo behind it: it is going somewhere. */
    RESTART_TAP_2(AudioBus.SFX, AudioCategory.FEEDBACK, VoicePool.Priority.EXIT_PARTIAL, 0.4, 1, 0.12),

    /** The third press: three notes climbing and a soft low settle under them, "done": the ball is back at its start. */
    RESTART_TAP_3(AudioBus.SFX, AudioCategory.FEEDBACK, VoicePool.Priority.EXIT_PARTIAL, 0.52, 1, 0.12),

    // ---- interface (all light, none ever louder than a collision)
    /** A button goes down. */
    UI_PRESS(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.32, 1, 0.08),

    /** A level is chosen in the list. */
    UI_LEVEL_SELECT(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.34, 1, 0.08),

    /** The list moves to another world. */
    UI_WORLD_SELECT(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.34, 1, 0.12),

    /** A new world opens. */
    UI_UNLOCK(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.4, 1, 0.5),

    /** Back, one screen up. */
    UI_BACK(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.32, 1, 0.08),

    /** The one action the screen is for (play, next). */
    UI_CONFIRM(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.36, 1, 0.08),

    /** The soft swell between a finished level and the next one. */
    LEVEL_TRANSITION(AudioBus.UI, AudioCategory.UI, VoicePool.Priority.UI, 0.3, 1, 0.5),
}
