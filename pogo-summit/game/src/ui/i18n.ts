/** Minimal EN/AR localisation. Arabic switches the whole UI to RTL. */
export type Lang = 'en' | 'ar';
const EN = {
  play: 'Play', options: 'Options', wardrobe: 'Wardrobe', leaderboard: 'Leaderboard', howto: 'How to Play', quit: 'Quit',
  gameMode: 'Game Mode', adventure: 'Adventure', adventureSub: 'Climb world by world', physicsLab: 'Physics Lab', physicsLabSub: 'Tune & test the movement', locked: 'Locked',
  world: 'World', level: 'Level', start: 'Start', back: 'Back', resume: 'Resume', restart: 'Restart', menu: 'Main menu', next: 'Next', retry: 'Retry',
  paused: 'Paused', complete: 'Level Complete!', newBest: 'New best!', time: 'Time', jumps: 'Jumps', boosts: 'Boosts', best: 'Best', stars: 'Stars', falls: 'Falls',
  progress: 'Progress', height: 'Height', jumpCharge: 'Jump / Charge', boost: 'Boost',
  audio: 'Audio', controls: 'Controls', graphics: 'Graphics', language: 'Language',
  master: 'Master', music: 'Music', sfx: 'Effects', ambient: 'Ambience', haptics: 'Haptics', hapticStrength: 'Vibration strength',
  scheme: 'Control style', schemeDrag: 'Drag (one finger)', schemePad: 'Pad (stick + buttons)', swipeDistance: 'Swipe distance', swipeStrength: 'Swipe strength',
  sensitivity: 'Touch sensitivity', deadzone: 'Deadzone', smoothing: 'Input smoothing', chargeTime: 'Charge time', leftHanded: 'Left-handed layout',
  guide: 'Trajectory guide', guideOff: 'Off', guideShort: 'Short', guideFull: 'Full',
  quality: 'Quality', qHigh: 'High', qDefault: 'Default', qSimplified: 'Simplified', shake: 'Screen shake', on: 'On', off: 'Off',
  hat: 'Hat', stick: 'Stick', outfit: 'Outfit', skin: 'Skin', boostFx: 'Boost effect', emote: 'Emote', equip: 'Equip', equipped: 'Equipped', unlock: 'Unlock',
  noRuns: 'No runs yet — go climb!', localOnly: 'Personal bests on this device', rank: 'Rank',
  hint_charge: 'Hold to charge · drag sideways to aim · release to jump',
  hint_aim: 'Tilt toward the next ledge. Longer hold = higher jump.',
  hint_bounce: 'Land on the spring pad — tilt to aim the rebound.',
  hint_hazard: 'Red crystals reset you to your last safe spot. Jump over them!',
  hint_ice: 'Ice keeps your momentum. Slide, then jump.',
  hint_ceiling: 'Low ceiling! Use a flatter jump.',
  hint_boost: 'Spin in the air to charge Boost, then tap it.',
  how1: 'Hold anywhere to charge your pogo.', how2: 'Drag left or right to lean the stick — that sets your direction.', how3: 'Release to launch. The longer you charge, the higher you go.',
  how4: 'In the air, drag to spin. Spin fast and far to power up Boost.', how5: 'Land on the tip, angled well. Bad angles bounce you off.', how6: 'Reach the flag at the top!',
  fell: 'Back to your last safe spot', hazardHit: 'Ouch!', boostReady: 'Boost ready!',
  tapToStart: 'Tap to start', loading: 'Loading…', version: 'Version',
} as const;
type Key = keyof typeof EN;
const AR: Record<Key, string> = {
  play: 'العب', options: 'الإعدادات', wardrobe: 'الخزانة', leaderboard: 'المتصدرون', howto: 'كيف تلعب', quit: 'خروج',
  gameMode: 'وضع اللعب', adventure: 'المغامرة', adventureSub: 'تسلّق عالمًا بعد عالم', physicsLab: 'مختبر الفيزياء', physicsLabSub: 'اضبط الحركة واختبرها', locked: 'مقفل',
  world: 'العالم', level: 'المرحلة', start: 'ابدأ', back: 'رجوع', resume: 'متابعة', restart: 'إعادة', menu: 'القائمة الرئيسية', next: 'التالي', retry: 'أعد المحاولة',
  paused: 'متوقف مؤقتًا', complete: 'اكتملت المرحلة!', newBest: 'رقم قياسي جديد!', time: 'الزمن', jumps: 'القفزات', boosts: 'التعزيزات', best: 'الأفضل', stars: 'النجوم', falls: 'السقطات',
  progress: 'التقدم', height: 'الارتفاع', jumpCharge: 'قفز / شحن', boost: 'تعزيز',
  audio: 'الصوت', controls: 'التحكم', graphics: 'الرسوم', language: 'اللغة',
  master: 'العام', music: 'الموسيقى', sfx: 'المؤثرات', ambient: 'الأجواء', haptics: 'الاهتزاز', hapticStrength: 'شدة الاهتزاز',
  scheme: 'نمط التحكم', schemeDrag: 'سحب (إصبع واحد)', schemePad: 'عصا وأزرار', swipeDistance: 'مسافة السحب', swipeStrength: 'قوة السحب',
  sensitivity: 'حساسية اللمس', deadzone: 'المنطقة الميتة', smoothing: 'تنعيم الإدخال', chargeTime: 'زمن الشحن', leftHanded: 'تخطيط لليد اليسرى',
  guide: 'دليل المسار', guideOff: 'إيقاف', guideShort: 'قصير', guideFull: 'كامل',
  quality: 'الجودة', qHigh: 'عالية', qDefault: 'افتراضية', qSimplified: 'مبسّطة', shake: 'اهتزاز الشاشة', on: 'تشغيل', off: 'إيقاف',
  hat: 'القبعة', stick: 'العصا', outfit: 'الملابس', skin: 'لون البشرة', boostFx: 'تأثير التعزيز', emote: 'إيماءة', equip: 'ارتداء', equipped: 'مُرتدى', unlock: 'افتح',
  noRuns: 'لا توجد محاولات بعد — ابدأ التسلق!', localOnly: 'أفضل أرقامك على هذا الجهاز', rank: 'الترتيب',
  hint_charge: 'اضغط للشحن · اسحب جانبًا للتوجيه · ارفع إصبعك للقفز',
  hint_aim: 'مِل نحو الحافة التالية. الضغط الأطول = قفزة أعلى.',
  hint_bounce: 'اهبط على النابض — مِل لتوجيه الارتداد.',
  hint_hazard: 'البلورات الحمراء تعيدك لآخر نقطة آمنة. اقفز فوقها!',
  hint_ice: 'الجليد يحفظ زخمك. انزلق ثم اقفز.',
  hint_ceiling: 'سقف منخفض! استخدم قفزة أكثر انبساطًا.',
  hint_boost: 'دُر في الهواء لشحن التعزيز ثم اضغطه.',
  how1: 'اضغط في أي مكان لشحن عصا البوغو.', how2: 'اسحب يمينًا أو يسارًا لإمالة العصا — هذا يحدد الاتجاه.', how3: 'ارفع إصبعك للإطلاق. كلما شحنت أكثر ارتفعت أكثر.',
  how4: 'في الهواء اسحب لتدور. الدوران السريع الطويل يشحن التعزيز.', how5: 'اهبط على الطرف بزاوية جيدة. الزوايا السيئة ترتدّ بك.', how6: 'اصل إلى العلم في القمة!',
  fell: 'عدت لآخر نقطة آمنة', hazardHit: 'آخ!', boostReady: 'التعزيز جاهز!',
  tapToStart: 'المس للبدء', loading: 'جارٍ التحميل…', version: 'الإصدار',
};
let lang: Lang = 'en';
export const setLang = (l: Lang): void => { lang = l; document.documentElement.lang = l; document.documentElement.dir = l === 'ar' ? 'rtl' : 'ltr'; };
export const getLang = (): Lang => lang;
export const t = (k: Key | string): string => (lang === 'ar' ? (AR as Record<string, string>)[k] : (EN as Record<string, string>)[k]) ?? (EN as Record<string, string>)[k] ?? k;
export type { Key };
