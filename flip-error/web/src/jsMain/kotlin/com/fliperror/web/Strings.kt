package com.fliperror.web

import com.fliperror.core.Category
import com.fliperror.core.Lang

/**
 * Every word the interface says, in both languages it says them in.
 *
 * English is the default. Arabic is not a decoration here - the page flips to
 * RTL with it, and the uppercase letter-spacing the neon look leans on is
 * dropped, because Arabic letters join and spacing them apart breaks the words.
 */
object Strings {
    var lang: Lang = Lang.EN

    private val en = mapOf(
        "play" to "PLAY", "levels" to "LEVELS", "shop" to "SHOP", "settings" to "SETTINGS",
        "level" to "LEVEL", "complete" to "COMPLETE", "unlocked" to "UNLOCKED",
        "world" to "WORLD", "world1" to "NEON CITY", "world2" to "NEON DESERT",
        "world3" to "THE ABYSS", "world4" to "CLOCKWORK",
        "newWorld" to "NEW WORLD",
        "locked" to "LOCKED", "soon" to "SOON", "back" to "BACK",
        "hint" to "TAP ANYWHERE TO JUMP · TAP AGAIN IN THE AIR TO BOOST",
        "buy" to "BUY", "equip" to "EQUIP", "equipped" to "EQUIPPED",
        "purchase" to "PURCHASE?", "item" to "ITEM", "price" to "PRICE",
        "cancel" to "CANCEL", "purchased" to "PURCHASED",
        "levelComplete" to "LEVEL COMPLETE", "starCoins" to "STAR COINS",
        "new" to "NEW", "perfect" to "PERFECT RUN", "earned" to "EARNED",
        "nextLevel" to "NEXT LEVEL", "replay" to "REPLAY",
        // No MUSIC entry. There is no music - see Audio - and a label for a
        // thing that does not exist tells the player they failed to hear it.
        "master" to "MASTER VOLUME", "sfx" to "SFX", "ambience" to "AMBIENCE",
        "vibration" to "VIBRATION",
        "reduceEffects" to "REDUCE EFFECTS", "colorblind" to "COLORBLIND MODE",
        "language" to "LANGUAGE", "reset" to "RESET PROGRESS", "resetGo" to "RESET",
        "unlockAll" to "UNLOCK ALL LEVELS", "tryAll" to "TRY EVERY COSMETIC", "tryOn" to "TRY ON",
        "resetAsk" to "ERASE ALL PROGRESS?", "notBuilt" to "NOT BUILT YET",
        "shapes" to "SHAPES", "colors" to "COLORS", "trails" to "TRAILS", "faces" to "FACES",
    )

    private val ar = mapOf(
        "play" to "العب", "levels" to "المراحل", "shop" to "المتجر", "settings" to "الإعدادات",
        "level" to "مرحلة", "complete" to "مكتملة", "unlocked" to "مفتوحة",
        "world" to "عالم", "world1" to "مدينة النيون", "world2" to "صحراء النيون",
        "world3" to "الهاوية", "world4" to "الآلة",
        "newWorld" to "عالم جديد",
        "locked" to "مقفلة", "soon" to "قريبًا", "back" to "رجوع",
        "hint" to "المس أي مكان للقفز · المس مرة أخرى في الهواء للاندفاع",
        "buy" to "شراء", "equip" to "تجهيز", "equipped" to "مُجهَّز",
        "purchase" to "تأكيد الشراء؟", "item" to "العنصر", "price" to "السعر",
        "cancel" to "إلغاء", "purchased" to "تم الشراء",
        "levelComplete" to "اكتملت المرحلة", "starCoins" to "نجوم",
        "new" to "جديد", "perfect" to "جولة مثالية", "earned" to "المكتسب",
        "nextLevel" to "المرحلة التالية", "replay" to "إعادة",
        "master" to "الصوت العام", "sfx" to "المؤثرات", "ambience" to "أصوات البيئة",
        "vibration" to "الاهتزاز",
        "reduceEffects" to "تقليل المؤثرات", "colorblind" to "وضع عمى الألوان",
        "language" to "اللغة", "reset" to "مسح التقدم", "resetGo" to "مسح",
        "unlockAll" to "فتح كل المراحل", "tryAll" to "تجربة كل المظاهر", "tryOn" to "جرّب",
        "resetAsk" to "مسح كل التقدم؟", "notBuilt" to "لم تُبنَ بعد",
        "shapes" to "أشكال", "colors" to "ألوان", "trails" to "آثار", "faces" to "وجوه",
    )

    operator fun get(key: String): String =
        (if (lang == Lang.AR) ar[key] else en[key]) ?: en[key] ?: key

    fun category(c: Category) = when (c) {
        Category.SHAPE -> get("shapes")
        Category.COLOR -> get("colors")
        Category.TRAIL -> get("trails")
        Category.FACE -> get("faces")
    }

    /** Level names stay in English: they are the level's identity, not UI chrome. */
    val rtl get() = lang == Lang.AR
}
