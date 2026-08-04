package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

object L10n {
    private val arMap = mapOf(
        "appName" to "COPY",
        "premium" to "بريميوم",
        "navHistory" to "السجل",
        "navSnippets" to "المقتطفات",
        "navSync" to "المزامنة",
        "navStats" to "الإحصائيات",
        "navSettings" to "الإعدادات",
        "navPremium" to "الاشتراك",
        "searchPlaceholder" to "ابحث عن العناصر المنسوخة...",
        "statsSavedItems" to "العناصر",
        "statsSnippetsCount" to "المقتطفات",
        "statsLastSync" to "آخر مزامنة",
        "filterAll" to "الكل",
        "filterText" to "نصوص",
        "filterLink" to "روابط",
        "filterImage" to "صور",
        "filterCode" to "كود",
        "emptyTitle" to "الحافظة فارغة تمامًا",
        "emptyDesc" to "انسخ أي نص أو رابط من أي تطبيق وسيظهر هنا تلقائيًا وبسرعة فائقة مع الحفاظ على خصوصيتك.",
        "learnMore" to "معرفة المزيد",
        "toastCopied" to "تم النسخ إلى الحافظة",
        "sourceApp" to "التطبيق: ",
        "settingsTitle" to "الإعدادات العامة",
        "settingsAppearance" to "مظهر التطبيق",
        "settingsLanguage" to "لغة التطبيق",
        "settingsService" to "خدمة مراقبة الحافظة",
        "settingsBoot" to "التشغيل بعد إعادة تشغيل الجهاز",
        "settingsRetention" to "مدة الاحتفاظ بالعناصر",
        "settingsDatabase" to "إدارة قاعدة البيانات",
        "settingsBackup" to "النسخ الاحتياطي والمزامنة",
        "settingsPrivacy" to "الخصوصية والأمان",
        "settingsAbout" to "حول التطبيق",
        "settingsLanguageAr" to "العربية",
        "settingsLanguageEn" to "English",
        "settingsLanguageSystem" to "استخدام لغة الجهاز",
        "settingsClearHistory" to "مسح السجل بالكامل",
        "settingsCleanUnpinned" to "تنظيف غير المثبت",
        "confirmClearTitle" to "هل أنت متأكد؟",
        "confirmClearDesc" to "سيتم مسح جميع البيانات من قاعدة البيانات نهائيًا ولا يمكن التراجع عن هذا الإجراء.",
        "cancel" to "إلغاء",
        "confirm" to "تأكيد ومسح",
        "premiumUpgrade" to "الترقية للميزات الاحترافية",
        "premiumActive" to "الحساب الاحترافي مفعل بنجاح 🎉",
        "syncStatus" to "حالة الاتصال",
        "syncDevices" to "الأجهزة المرتبطة",
        "syncNow" to "مزامنة سريعة الآن",
        "syncQR" to "ربط جهاز جديد عبر رمز QR",
        "retentionOption1" to "يوم واحد",
        "retentionOption7" to "7 أيام",
        "retentionOption30" to "30 يومًا",
        "retentionOption90" to "90 يومًا",
        "retentionForever" to "للأبد",
        "serviceRunning" to "الخدمة تعمل في الخلفية",
        "serviceStopped" to "الخدمة متوقفة حاليًا",
        "serviceEnable" to "تفعيل الخدمة",
        "serviceDisable" to "إيقاف الخدمة",
        "dbStats" to "حجم الحافظة الحالية: %d عنصر",
        "lastSyncTime" to "قبل دقيقتين",
        "copiedSuccessfully" to "تم النسخ بنجاح!"
    )

    private val enMap = mapOf(
        "appName" to "COPY",
        "premium" to "Premium",
        "navHistory" to "History",
        "navSnippets" to "Snippets",
        "navSync" to "Sync",
        "navStats" to "Stats",
        "navSettings" to "Settings",
        "navPremium" to "Subscription",
        "searchPlaceholder" to "Search copied items...",
        "statsSavedItems" to "Saved Items",
        "statsSnippetsCount" to "Snippets",
        "statsLastSync" to "Last Sync",
        "filterAll" to "All",
        "filterText" to "Text",
        "filterLink" to "Links",
        "filterImage" to "Images",
        "filterCode" to "Code",
        "emptyTitle" to "Clipboard is Empty",
        "emptyDesc" to "Copy any text or link from any application and it will show up here automatically, keeping your privacy secure.",
        "learnMore" to "Learn More",
        "toastCopied" to "Copied to clipboard",
        "sourceApp" to "App: ",
        "settingsTitle" to "General Settings",
        "settingsAppearance" to "App Appearance",
        "settingsLanguage" to "App Language",
        "settingsService" to "Clipboard Monitoring Service",
        "settingsBoot" to "Start Automatically on Boot",
        "settingsRetention" to "Retention Duration",
        "settingsDatabase" to "Database Management",
        "settingsBackup" to "Backup & Sync",
        "settingsPrivacy" to "Privacy & Security",
        "settingsAbout" to "About App",
        "settingsLanguageAr" to "العربية",
        "settingsLanguageEn" to "English",
        "settingsLanguageSystem" to "Use System Language",
        "settingsClearHistory" to "Clear All History",
        "settingsCleanUnpinned" to "Clean Unpinned Elements",
        "confirmClearTitle" to "Are you sure?",
        "confirmClearDesc" to "All data will be permanently wiped from the local database. This action is irreversible.",
        "cancel" to "Cancel",
        "confirm" to "Confirm & Wipe",
        "premiumUpgrade" to "Upgrade to Premium Features",
        "premiumActive" to "Premium Account Active 🎉",
        "syncStatus" to "Connection Status",
        "syncDevices" to "Linked Devices",
        "syncNow" to "Quick Sync Now",
        "syncQR" to "Link New Device via QR Code",
        "retentionOption1" to "1 Day",
        "retentionOption7" to "7 Days",
        "retentionOption30" to "30 Days",
        "retentionOption90" to "90 Days",
        "retentionForever" to "Forever",
        "serviceRunning" to "Service running in background",
        "serviceStopped" to "Service is currently stopped",
        "serviceEnable" to "Enable Service",
        "serviceDisable" to "Disable Service",
        "dbStats" to "Current Clipboard Size: %d elements",
        "lastSyncTime" to "2 mins ago",
        "copiedSuccessfully" to "Copied successfully!"
    )

    fun get(key: String, lang: String): String {
        val activeLang = if (lang == "system") {
            if (java.util.Locale.getDefault().language.startsWith("ar")) "ar" else "en"
        } else {
            lang
        }
        return if (activeLang == "ar") {
            arMap[key] ?: enMap[key] ?: key
        } else {
            enMap[key] ?: arMap[key] ?: key
        }
    }
}
