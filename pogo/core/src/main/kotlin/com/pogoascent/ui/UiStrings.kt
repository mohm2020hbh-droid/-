package com.pogoascent.ui

/** All user-visible text, English and Arabic. Keys are used by the Android views; missing Arabic falls back to English. */
object UiStrings {
  private val en = mapOf(
    "app_name" to "Pogo Ascent", "play" to "Play", "continue" to "Continue", "worlds" to "Worlds", "levels" to "Levels",
    "wardrobe" to "Wardrobe", "leaderboard" to "Leaderboard", "settings" to "Settings", "how_to_play" to "How To Play",
    "credits" to "Credits", "quit" to "Quit", "back" to "Back", "resume" to "Resume", "restart" to "Restart",
    "main_menu" to "Main Menu", "paused" to "Paused", "level_complete" to "Level Complete!", "next_level" to "Next Level",
    "time" to "Time", "jumps" to "Jumps", "boosts" to "Boosts", "best" to "Best", "new_best" to "New best!",
    "coins" to "Coins", "locked" to "Locked", "video" to "Graphics", "audio" to "Audio", "controls" to "Controls", "gameplay" to "Gameplay",
    "quality" to "Quality", "resolution" to "Resolution scale", "fps_cap" to "FPS cap", "effects" to "Effects", "particles" to "Particles", "shadows" to "Shadows",
    "master" to "Master", "music" to "Music", "sfx" to "Sound effects", "ambient" to "Ambient",
    "sensitivity" to "Sensitivity", "deadzone" to "Deadzone", "layout" to "Layout", "left_handed" to "Left-handed", "haptics" to "Vibration",
    "button_size" to "Jump button size", "show_hud" to "Show HUD", "show_timer" to "Show timer", "show_fps" to "Show FPS",
    "camera_shake" to "Camera shake", "camera_zoom" to "Camera zoom", "tutorial" to "Tutorial hints", "reset_progress" to "Reset progress",
    "equip" to "Equip", "equipped" to "Equipped", "buy" to "Buy", "no_scores" to "No times yet — finish a level!",
    "hint_lean" to "Drag left/right to lean the stick", "hint_charge" to "Hold JUMP to charge, release to launch",
    "hint_spin" to "Spin in the air, then land holding JUMP for a Boost", "physics_test" to "Physics Test",
    "htp_1" to "Lean: drag your left thumb sideways to tilt the pogo stick. Where you lean is where you will jump.",
    "htp_2" to "Charge: hold JUMP — the spring compresses. Longer hold = higher, longer jump. Release to launch.",
    "htp_3" to "Air control: while flying, drag to spin the stick. Land with the stick roughly upright or the tip slips.",
    "htp_4" to "Bounce chain: keep JUMP held when you touch down and the landing speed is stored in the spring.",
    "htp_5" to "Boost: spin more than a full flip in one jump, land while holding JUMP, then release — you get a bigger launch.",
    "htp_6" to "Reach the golden flag. Falling is never fatal — you just lose height. Spikes and the abyss send you back to the last checkpoint.",
    "credits_text" to "Pogo Ascent — an original game inspired by the pogo-stick climbing genre.\nAll code, levels, sounds and models are original and generated at runtime.\nNo assets from other games are used.",
  )
  private val ar = mapOf(
    "app_name" to "صعود البوجو", "play" to "العب", "continue" to "متابعة", "worlds" to "العوالم", "levels" to "المراحل",
    "wardrobe" to "الخزانة", "leaderboard" to "المتصدرون", "settings" to "الإعدادات", "how_to_play" to "طريقة اللعب",
    "credits" to "الشكر", "quit" to "خروج", "back" to "رجوع", "resume" to "متابعة", "restart" to "إعادة",
    "main_menu" to "القائمة الرئيسية", "paused" to "إيقاف مؤقت", "level_complete" to "اكتملت المرحلة!", "next_level" to "المرحلة التالية",
    "time" to "الوقت", "jumps" to "القفزات", "boosts" to "التعزيزات", "best" to "الأفضل", "new_best" to "رقم قياسي جديد!",
    "coins" to "عملات", "locked" to "مقفل", "video" to "الرسوميات", "audio" to "الصوت", "controls" to "التحكم", "gameplay" to "اللعب",
    "quality" to "الجودة", "resolution" to "دقة العرض", "fps_cap" to "حد الإطارات", "effects" to "المؤثرات", "particles" to "الجسيمات", "shadows" to "الظلال",
    "master" to "الرئيسي", "music" to "الموسيقى", "sfx" to "المؤثرات الصوتية", "ambient" to "الأجواء",
    "sensitivity" to "الحساسية", "deadzone" to "المنطقة الميتة", "layout" to "التخطيط", "left_handed" to "للأيسر", "haptics" to "الاهتزاز",
    "button_size" to "حجم زر القفز", "show_hud" to "إظهار الواجهة", "show_timer" to "إظهار المؤقت", "show_fps" to "إظهار الإطارات",
    "camera_shake" to "اهتزاز الكاميرا", "camera_zoom" to "تقريب الكاميرا", "tutorial" to "تلميحات التعليم", "reset_progress" to "مسح التقدم",
    "equip" to "ارتداء", "equipped" to "مرتدى", "buy" to "شراء", "no_scores" to "لا توجد أوقات بعد — أنهِ مرحلة!",
    "hint_lean" to "اسحب يمينًا أو يسارًا لإمالة العصا", "hint_charge" to "اضغط مطولًا على القفز للشحن ثم ارفع إصبعك للانطلاق",
    "hint_spin" to "دُر في الهواء ثم اهبط والقفز مضغوط للحصول على تعزيز", "physics_test" to "اختبار الفيزياء",
    "htp_1" to "الإمالة: اسحب بإبهامك الأيسر جانبيًا لإمالة عصا البوجو. تقفز في الاتجاه الذي تميل إليه.",
    "htp_2" to "الشحن: اضغط مطولًا على القفز فينضغط النابض. كلما طال الضغط ارتفعت القفزة وطالت. ارفع إصبعك للانطلاق.",
    "htp_3" to "التحكم في الهواء: اسحب لتدوير العصا أثناء الطيران. اهبط والعصا شبه عمودية وإلا انزلقت.",
    "htp_4" to "سلسلة الارتداد: أبقِ القفز مضغوطًا عند اللمس لتُخزَّن سرعة الهبوط في النابض.",
    "htp_5" to "التعزيز: دُر أكثر من لفة كاملة في قفزة واحدة، اهبط والقفز مضغوط ثم اتركه لتحصل على انطلاقة أقوى.",
    "htp_6" to "ابلغ العلم الذهبي. السقوط ليس قاتلًا، تخسر الارتفاع فقط. الأشواك والهاوية تعيدانك لآخر نقطة حفظ.",
    "credits_text" to "صعود البوجو — لعبة أصلية مستوحاة من نوع التسلق بعصا البوجو.\nكل الشيفرة والمراحل والأصوات والنماذج أصلية وتُولَّد أثناء التشغيل.\nلا تُستخدم أي أصول من ألعاب أخرى.",
  )

  fun get(key: String, lang: String): String = (if (lang == "ar") ar[key] else null) ?: en[key] ?: key
  fun keys(): Set<String> = en.keys
  fun missingArabic(): Set<String> = en.keys - ar.keys
  fun unknownArabic(): Set<String> = ar.keys - en.keys
}
