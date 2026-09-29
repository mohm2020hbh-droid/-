package com.carom.game.ui

import java.util.Locale

/** All on-screen words. Arabic is used when the device language is Arabic, English otherwise. */
class UiText private constructor(
    val language: String,
    val tagline: String,
    val play: String,
    val levels: String,
    val level: String,
    val levelComplete: String,
    val levelFailed: String,
    val allComplete: String,
    val next: String,
    val retry: String,
    val restart: String,
    val outOfBounces: String,
    val ballStopped: String,
    val vibrationOn: String,
    val vibrationOff: String,
) {
    /** Letter spacing looks right in Latin capitals but breaks Arabic joining, so it is per language. */
    val letterSpacing: Float get() = if (language == "ar") 0f else 1f

    companion object {
        private val ENGLISH = UiText(
            language = "en",
            tagline = "AIM  ·  BOUNCE  ·  SOLVE",
            play = "PLAY",
            levels = "LEVELS",
            level = "LEVEL",
            levelComplete = "LEVEL COMPLETE",
            levelFailed = "LEVEL FAILED",
            allComplete = "ALL LEVELS COMPLETE",
            next = "NEXT",
            retry = "RETRY",
            restart = "RESTART",
            outOfBounces = "OUT OF BOUNCES",
            ballStopped = "THE BALL STOPPED",
            vibrationOn = "VIBRATION ON",
            vibrationOff = "VIBRATION OFF",
        )

        private val ARABIC = UiText(
            language = "ar",
            tagline = "صوّب  ·  ارتدّ  ·  احلُل",
            play = "العب",
            levels = "المراحل",
            level = "المرحلة",
            levelComplete = "اكتملت المرحلة",
            levelFailed = "فشلت المحاولة",
            allComplete = "أنهيت جميع المراحل",
            next = "التالية",
            retry = "أعد المحاولة",
            restart = "إعادة",
            outOfBounces = "نفدت الارتدادات",
            ballStopped = "توقفت الكرة",
            vibrationOn = "الاهتزاز: يعمل",
            vibrationOff = "الاهتزاز: متوقف",
        )

        fun forLocale(locale: Locale = Locale.getDefault()): UiText =
            if (locale.language == "ar") ARABIC else ENGLISH
    }
}
