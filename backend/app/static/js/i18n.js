/**
 * Localization.
 *
 * Every piece of text the player can see comes from here, addressed by a
 * stable key rather than by its wording. That keeps content separate from
 * presentation: adding a language means adding one dictionary below and
 * nothing else, and no screen, handler, or sound definition has to change.
 *
 * Conventions
 *   - Keys are dotted and namespaced by screen: `sp.hint.timed`,
 *     `online.error.room_full`.
 *   - Placeholders are named: `t("sp.best", { score: 82 })` fills `{score}`.
 *   - Sound names are NOT here. They live with the sound in sounds.js, keyed
 *     by the same stable `sound_id` the server and the scoring engine use, so
 *     `donkey` is "حمار" or "Donkey" while staying one sound with one target
 *     audio and one score.
 *
 * The chosen language is remembered under its own storage key, separate from
 * progress, so switching language never disturbs unlocked stages or scores.
 */

export const LANGUAGES = [
  { code: "ar", label: "العربية", dir: "rtl" },
  { code: "en", label: "English", dir: "ltr" },
];

const DEFAULT_LANGUAGE = "ar";   // Arabic is the game's default.
const STORAGE_KEY = "voiceduel.language.v1";

/* ==================================================================== *
 * Arabic — the reference dictionary. Every key exists here.
 * ==================================================================== */
const ar = {
  "app.title": "مبارزة الأصوات",
  "app.tagline": "مبارزة الأصوات",

  /* ---- chrome ---- */
  "nav.back": "رجوع",
  "nav.language": "اللغة",

  /* ---- main menu ---- */
  "menu.single.title": "اللعب الفردي",
  "menu.single.sub": "المراحل والتحديات",
  "menu.online.title": "اللعب أونلاين",
  "menu.online.sub": "تحدَّ لاعبًا آخر",

  /* ---- screen titles ---- */
  "title.stages": "المراحل",
  "title.stage": "تحدي التقليد",
  "title.result": "النتيجة",
  "title.online": "اللعب أونلاين",
  "title.room.normal": "غرفة عادية",
  "title.room.new": "غرفة جديدة",
  "title.round": "الجولة",
  "title.rating": "التقييم",
  "title.round.result": "نتيجة الجولة",
  "title.match.over": "انتهت المباراة",
  "title.battle": "معركة صوتية",
  "title.battle.arena": "المعركة",
  "title.battle.over": "انتهت المعركة",

  /* ---- single player: stage list ---- */
  "stages.progress.none": "ابدأ من المرحلة الأولى",
  "stages.progress.some": "تقدمك: {done} من {total} مرحلة",
  "stages.summary": "{done} من {total} مرحلة مكتملة · {unlocked} مفتوحة",
  "stages.locked": "🔒 مقفل",
  "stages.meta": "{challenge} · النجاح من {pass}",

  /* ---- challenge types ---- */
  "challenge.single": "تقليد مباشر",
  "challenge.timed": "تحدي الوقت",
  "challenge.sequence": "تسلسل أصوات",
  "challenge.distorted": "صوت مشوّش",
  "challenge.sequence.join": " ثم ",

  /* ---- single player: a stage ---- */
  "sp.stage.number": "المرحلة {number}",
  "sp.passmark": "النجاح من {pass} فأعلى",
  "sp.best.none": "لم تحاول هذه المرحلة بعد",
  "sp.best.some": "أفضل نتيجة لك: {score}",
  "sp.hint.single": "استمع للصوت المطلوب ثم قلّده بصوتك.",
  "sp.hint.timed": "لديك {seconds} ثوانٍ فقط. استعد قبل الضغط.",
  "sp.hint.sequence": "استمع للتسلسل كاملًا، ثم قلّد الأصوات بالترتيب نفسه.",
  "sp.hint.distorted": "الصوت مشوّش عمدًا. ركّز على الشكل العام وليس التفاصيل.",
  "sp.hear.again": "🔊 استمع مرة أخرى",
  "sp.hear.playing": "🔊 يُشغَّل…",
  "sp.record.start": "🎤 ابدأ التسجيل",
  "sp.recording": "جارٍ التسجيل — قلّد الآن",
  "sp.record.stop": "أنهيت، حلّل الآن",
  "sp.analysing": "جارٍ تحليل الصوت ومقارنته بالهدف",
  "sp.back": "رجوع للمراحل",

  /* ---- single player: result ---- */
  "sp.result.passed": "نجحت في المرحلة!",
  "sp.result.failed": "لم تبلغ حد النجاح",
  "sp.result.improved": "أفضل نتيجة جديدة! 🎉",
  "sp.result.passmark": "النجاح من {pass}",
  "sp.result.breakdown": "تفصيل المقارنة الصوتية",
  "sp.next": "المرحلة التالية",
  "sp.retry": "إعادة المحاولة",
  "sp.hear.target": "🔊 استمع للهدف",
  "sp.all.stages": "كل المراحل",

  /* ---- scoring breakdown ---- */
  "score.timbre": "نبرة الصوت (الطيف)",
  "score.pitch": "حدة الطبقة",
  "score.dynamics": "تموّج الصوت",
  "score.voicing": "نقاء/خشونة",
  "score.brightness": "سطوع الصوت",

  /* ---- single player: errors ---- */
  "error.mic.unsupported": "المتصفح لا يتيح الميكروفون هنا. افتح الصفحة عبر 127.0.0.1 أو https.",
  "error.record.empty": "لم يُلتقط أي صوت. حاول مرة أخرى.",
  "error.record.failed": "تعذّر فتح الميكروفون: {detail}",
  "error.analyse.unsupported": "تعذّر تحليل التسجيل في هذا المتصفح.",
  "error.analyse.silent": "التسجيل صامت أو قصير جدًا. اقترب من الميكروفون وحاول مجددًا.",
  "error.analyse.failed": "تعذّر تحليل الصوت.",

  /* ---- online: mode select ---- */
  "online.heading": "أونلاين",
  "online.tagline": "اختر طريقة اللعب مع خصمك",
  "online.normal.title": "غرفة عادية",
  "online.normal.sub": "أداء بالتناوب وتقييم يدوي",
  "online.battle.title": "معركة صوتية",
  "online.battle.sub": "كلاكما يقلّد معًا، والأدق يفوز",

  /* ---- online: normal room ---- */
  "room.normal.heading": "غرفة عادية",
  "room.normal.blurb": "قلّد الصوت المطلوب، ودع خصمك يحكم عليك",
  "room.create": "إنشاء غرفة",
  "room.code.placeholder": "كود الغرفة (٤ أرقام)",
  "room.join": "الانضمام لغرفة",
  "room.share": "شارك هذا الكود مع خصمك",
  "room.waiting": "بانتظار انضمام اللاعب الثاني…",
  "room.cancel": "إلغاء والعودة",

  /* ---- connection ---- */
  "conn.offline": "غير متصل",
  "conn.connecting": "جارٍ الاتصال…",
  "conn.connected": "متصل",
  "conn.failed": "تعذّر الاتصال",
  "conn.lost": "انقطع الاتصال",
  "conn.lost.server": "انقطع الاتصال بالخادم",
  "conn.none": "لا يوجد اتصال بالخادم",
  "conn.unreachable": "تعذّر الاتصال بالخادم",

  /* ---- online: errors ---- */
  "error.invalid_code": "الكود غير صحيح، تأكد منه وحاول مجددًا",
  "error.room_full": "الغرفة ممتلئة",
  "error.audio_too_large": "التسجيل طويل جدًا",
  "error.not_your_turn": "ليس دورك في هذه الجولة",
  "error.stale_round": "انتهت هذه الجولة بالفعل",
  "error.invalid_score": "قيمة التقييم غير صالحة",
  "error.invalid_score.battle": "قيمة النتيجة غير صالحة",
  "error.generic": "تعذّر تنفيذ الطلب",
  "error.code.length": "أدخل كودًا مكوّنًا من ٤ أرقام",
  "error.opponent.left": "انسحب الخصم من المباراة",
  "error.opponent.left.battle": "انسحب الخصم من المعركة",
  "error.mic.address": "الميكروفون غير متاح على هذا العنوان — افتح الصفحة عبر http://127.0.0.1:8000/play أو https.",

  /* ---- online: play ---- */
  "play.round": "الجولة {number} من {total}",
  "play.your.turn": "دورك! قلّد الصوت الآن",
  "play.recording": "جارٍ التسجيل",
  "play.sending": "جارٍ إرسال التسجيل…",
  "play.send.now": "أرسل الآن",
  "play.opponent.recording": "خصمك يسجّل الآن…",
  "play.get.ready": "استعد للاستماع والتقييم",
  "play.leave": "الخروج من المباراة",
  "play.mic.fallback": "المتصفح لا يتيح الميكروفون هنا (يتطلب https أو 127.0.0.1). ستُرسل نغمة بديلة.",
  "play.record.fallback": "تعذّر التسجيل: {detail}. ستُرسل نغمة بديلة.",

  /* ---- online: rating ---- */
  "rate.heading": "قيّم تقليد خصمك",
  "rate.playing": "🔊 جارٍ التشغيل…",
  "rate.played": "انتهى التشغيل",
  "rate.replay": "إعادة الاستماع",
  "rate.outof": "من 100",
  "rate.submit": "إرسال التقييم",
  "rate.label.90": "مطابق تمامًا! 🤯",
  "rate.label.70": "تقليد ممتاز 👏",
  "rate.label.50": "قريب من الصوت 🙂",
  "rate.label.30": "محاولة متواضعة 😅",
  "rate.label.0": "بعيد عن المطلوب 😂",

  /* ---- online: round result ---- */
  "result.round": "نتيجة الجولة {number}",
  "result.round.plain": "نتيجة الجولة",
  "result.you": "أنت",
  "result.opponent": "الخصم",
  "result.next.soon": "الجولة التالية بعد لحظات…",
  "result.timeout.mine": "انتهى الوقت قبل إرسال تسجيلك",
  "result.timeout.theirs": "انتهى وقت الجولة",
  "result.yours": "هذه نقاطك عن أدائك",
  "result.given": "هذه النقاط التي منحتَها لخصمك",

  /* ---- online: match over ---- */
  "over.draw": "تعادل!",
  "over.won": "فزت بالمبارزة!",
  "over.lost": "فاز خصمك هذه المرة",
  "over.new.match": "مباراة جديدة",
  "over.menu": "القائمة الرئيسية",

  /* ---- voice battle ---- */
  "battle.heading": "معركة صوتية",
  "battle.blurb": "كلاكما يقلّد الصوت نفسه في آن واحد، ويفوز الأدق",
  "battle.create": "إنشاء معركة",
  "battle.code.placeholder": "كود المعركة (٤ أرقام)",
  "battle.join": "الانضمام لمعركة",
  "battle.round": "الجولة {number} من {total}",
  "battle.you": "أنت",
  "battle.opponent": "الخصم",
  "battle.listen": "🔊 استمع للهدف",
  "battle.start": "ابدأ ⚔️",
  "battle.imitate.now": "قلّد الصوت الآن",
  "battle.waiting.opponent": "⏳ بانتظار الخصم…",
  "battle.leave": "الخروج من المعركة",
  "battle.wins.mine": "انتصاراتك",
  "battle.wins.theirs": "انتصارات الخصم",
  "battle.round.won": "فزت بالجولة! 🎉",
  "battle.round.lost": "خسرت الجولة",
  "battle.round.draw": "تعادل 🤝",
  "battle.over.draw": "تعادل في المعركة!",
  "battle.over.won": "فزت بالمعركة!",
  "battle.over.lost": "فاز خصمك بالمعركة",
  "battle.new": "معركة جديدة",
  "battle.cancel": "إلغاء والعودة",
};

/* ==================================================================== *
 * English
 * ==================================================================== */
const en = {
  "app.title": "Voice Duel",
  "app.tagline": "Voice Duel",

  "nav.back": "Back",
  "nav.language": "Language",

  "menu.single.title": "Single Player",
  "menu.single.sub": "Stages and challenges",
  "menu.online.title": "Play Online",
  "menu.online.sub": "Challenge another player",

  "title.stages": "Stages",
  "title.stage": "Imitation Challenge",
  "title.result": "Result",
  "title.online": "Play Online",
  "title.room.normal": "Normal Room",
  "title.room.new": "New Room",
  "title.round": "Round",
  "title.rating": "Rating",
  "title.round.result": "Round Result",
  "title.match.over": "Match Over",
  "title.battle": "Voice Battle",
  "title.battle.arena": "Battle",
  "title.battle.over": "Battle Over",

  "stages.progress.none": "Start with stage one",
  "stages.progress.some": "Progress: {done} of {total} stages",
  "stages.summary": "{done} of {total} stages cleared · {unlocked} unlocked",
  "stages.locked": "🔒 Locked",
  "stages.meta": "{challenge} · pass mark {pass}",

  "challenge.single": "Straight imitation",
  "challenge.timed": "Time attack",
  "challenge.sequence": "Sound sequence",
  "challenge.distorted": "Distorted sound",
  "challenge.sequence.join": " then ",

  "sp.stage.number": "Stage {number}",
  "sp.passmark": "Pass mark: {pass} or above",
  "sp.best.none": "You have not tried this stage yet",
  "sp.best.some": "Your best: {score}",
  "sp.hint.single": "Listen to the target sound, then imitate it with your voice.",
  "sp.hint.timed": "You only get {seconds} seconds. Get ready before you press.",
  "sp.hint.sequence": "Listen to the whole sequence, then imitate the sounds in the same order.",
  "sp.hint.distorted": "The sound is distorted on purpose. Go for the overall shape, not the detail.",
  "sp.hear.again": "🔊 Listen again",
  "sp.hear.playing": "🔊 Playing…",
  "sp.record.start": "🎤 Start recording",
  "sp.recording": "Recording — imitate now",
  "sp.record.stop": "Done, analyse it",
  "sp.analysing": "Analysing your voice and comparing it with the target",
  "sp.back": "Back to stages",

  "sp.result.passed": "Stage cleared!",
  "sp.result.failed": "Below the pass mark",
  "sp.result.improved": "New personal best! 🎉",
  "sp.result.passmark": "Pass mark: {pass}",
  "sp.result.breakdown": "Acoustic comparison breakdown",
  "sp.next": "Next stage",
  "sp.retry": "Try again",
  "sp.hear.target": "🔊 Hear the target",
  "sp.all.stages": "All stages",

  "score.timbre": "Tone colour (spectrum)",
  "score.pitch": "Pitch",
  "score.dynamics": "Loudness shape",
  "score.voicing": "Clarity / roughness",
  "score.brightness": "Brightness",

  "error.mic.unsupported": "This browser will not allow the microphone here. Open the page over 127.0.0.1 or https.",
  "error.record.empty": "No sound was picked up. Try again.",
  "error.record.failed": "Could not open the microphone: {detail}",
  "error.analyse.unsupported": "This browser cannot analyse the recording.",
  "error.analyse.silent": "The recording is silent or too short. Move closer to the microphone and try again.",
  "error.analyse.failed": "Could not analyse the audio.",

  "online.heading": "Online",
  "online.tagline": "Choose how you want to play your opponent",
  "online.normal.title": "Normal Room",
  "online.normal.sub": "Take turns, rate each other by hand",
  "online.battle.title": "Voice Battle",
  "online.battle.sub": "You both imitate at once — closest wins",

  "room.normal.heading": "Normal Room",
  "room.normal.blurb": "Imitate the target sound and let your opponent judge you",
  "room.create": "Create a room",
  "room.code.placeholder": "Room code (4 digits)",
  "room.join": "Join a room",
  "room.share": "Share this code with your opponent",
  "room.waiting": "Waiting for the second player…",
  "room.cancel": "Cancel and go back",

  "conn.offline": "Not connected",
  "conn.connecting": "Connecting…",
  "conn.connected": "Connected",
  "conn.failed": "Could not connect",
  "conn.lost": "Connection lost",
  "conn.lost.server": "Lost the connection to the server",
  "conn.none": "No connection to the server",
  "conn.unreachable": "Could not reach the server",

  "error.invalid_code": "That code is not right — check it and try again",
  "error.room_full": "That room is full",
  "error.audio_too_large": "That recording is too long",
  "error.not_your_turn": "It is not your turn this round",
  "error.stale_round": "This round has already finished",
  "error.invalid_score": "That rating is not a valid value",
  "error.invalid_score.battle": "That score is not a valid value",
  "error.generic": "Could not complete the request",
  "error.code.length": "Enter a 4-digit code",
  "error.opponent.left": "Your opponent left the match",
  "error.opponent.left.battle": "Your opponent left the battle",
  "error.mic.address": "The microphone is not available on this address — open the page over http://127.0.0.1:8000/play or https.",

  "play.round": "Round {number} of {total}",
  "play.your.turn": "Your turn! Imitate the sound now",
  "play.recording": "Recording",
  "play.sending": "Sending your recording…",
  "play.send.now": "Send now",
  "play.opponent.recording": "Your opponent is recording…",
  "play.get.ready": "Get ready to listen and rate",
  "play.leave": "Leave the match",
  "play.mic.fallback": "This browser will not allow the microphone here (https or 127.0.0.1 is required). A placeholder tone will be sent.",
  "play.record.fallback": "Recording failed: {detail}. A placeholder tone will be sent.",

  "rate.heading": "Rate your opponent's imitation",
  "rate.playing": "🔊 Playing…",
  "rate.played": "Playback finished",
  "rate.replay": "Listen again",
  "rate.outof": "out of 100",
  "rate.submit": "Submit rating",
  "rate.label.90": "Spot on! 🤯",
  "rate.label.70": "Excellent imitation 👏",
  "rate.label.50": "Close to the sound 🙂",
  "rate.label.30": "A modest attempt 😅",
  "rate.label.0": "Nowhere near 😂",

  "result.round": "Round {number} result",
  "result.round.plain": "Round result",
  "result.you": "You",
  "result.opponent": "Opponent",
  "result.next.soon": "Next round in a moment…",
  "result.timeout.mine": "Time ran out before your recording was sent",
  "result.timeout.theirs": "The round timed out",
  "result.yours": "These are your points for your performance",
  "result.given": "These are the points you gave your opponent",

  "over.draw": "A draw!",
  "over.won": "You won the duel!",
  "over.lost": "Your opponent won this time",
  "over.new.match": "New match",
  "over.menu": "Main menu",

  "battle.heading": "Voice Battle",
  "battle.blurb": "You both imitate the same sound at the same time — the closest wins",
  "battle.create": "Create a battle",
  "battle.code.placeholder": "Battle code (4 digits)",
  "battle.join": "Join a battle",
  "battle.round": "Round {number} of {total}",
  "battle.you": "You",
  "battle.opponent": "Opponent",
  "battle.listen": "🔊 Hear the target",
  "battle.start": "Start ⚔️",
  "battle.imitate.now": "Imitate the sound now",
  "battle.waiting.opponent": "⏳ Waiting for your opponent…",
  "battle.leave": "Leave the battle",
  "battle.wins.mine": "Your wins",
  "battle.wins.theirs": "Opponent wins",
  "battle.round.won": "You won the round! 🎉",
  "battle.round.lost": "You lost the round",
  "battle.round.draw": "A draw 🤝",
  "battle.over.draw": "The battle is a draw!",
  "battle.over.won": "You won the battle!",
  "battle.over.lost": "Your opponent won the battle",
  "battle.new": "New battle",
  "battle.cancel": "Cancel and go back",
};

const DICTIONARIES = { ar, en };

/* ==================================================================== *
 * Runtime
 * ==================================================================== */

let current = DEFAULT_LANGUAGE;
const listeners = new Set();

function readStored() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && DICTIONARIES[stored]) return stored;
  } catch {
    // Private mode or blocked storage: fall back to the default language
    // rather than failing to start.
  }
  return DEFAULT_LANGUAGE;
}

/** The active language code. */
export function getLanguage() {
  return current;
}

export function languageDir(code = current) {
  return LANGUAGES.find((l) => l.code === code)?.dir ?? "rtl";
}

/**
 * Translate a key.
 *
 * An untranslated key falls back to Arabic and then to the key itself, so a
 * gap in a dictionary shows up as visible text rather than as a blank screen.
 */
export function t(key, params) {
  const dictionary = DICTIONARIES[current] ?? ar;
  let text = dictionary[key];
  if (text === undefined) text = ar[key];
  if (text === undefined) return key;
  if (!params) return text;
  return text.replace(/\{(\w+)\}/g, (whole, name) =>
    (params[name] !== undefined ? String(params[name]) : whole));
}

/**
 * Re-render the static markup.
 *
 * Elements opt in with an attribute, so markup stays declarative:
 *   data-i18n             → textContent (the <title> element included, which
 *                           is how the browser tab is translated)
 *   data-i18n-placeholder → placeholder
 *   data-i18n-aria-label  → aria-label
 *   data-i18n-title       → title
 */
export function applyTranslations(root = document) {
  for (const node of root.querySelectorAll("[data-i18n]")) {
    node.textContent = t(node.dataset.i18n);
  }
  for (const node of root.querySelectorAll("[data-i18n-placeholder]")) {
    node.placeholder = t(node.dataset.i18nPlaceholder);
  }
  for (const node of root.querySelectorAll("[data-i18n-aria-label]")) {
    node.setAttribute("aria-label", t(node.dataset.i18nAriaLabel));
  }
  for (const node of root.querySelectorAll("[data-i18n-title]")) {
    node.title = t(node.dataset.i18nTitle);
  }
}

/** Called after every language change, so live screens can redraw. */
export function onLanguageChange(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Switch language.
 *
 * Writes only its own storage key, so progress, unlocked stages, best scores
 * and stars are untouched. Also flips the document's direction, which is what
 * makes every logical CSS property in app.css mirror correctly.
 */
export function setLanguage(code, { persist = true } = {}) {
  if (!DICTIONARIES[code]) return;
  current = code;
  document.documentElement.lang = code;
  document.documentElement.dir = languageDir(code);
  if (persist) {
    try {
      localStorage.setItem(STORAGE_KEY, code);
    } catch {
      // Language simply will not be remembered; the game still works.
    }
  }
  applyTranslations();
  for (const listener of listeners) listener(code);
}

/** Restore the remembered language. Call once, before the first render. */
export function initLanguage() {
  setLanguage(readStored(), { persist: false });
  return current;
}

/** Every key that exists, for the localization test to check coverage. */
export function dictionaries() {
  return DICTIONARIES;
}
