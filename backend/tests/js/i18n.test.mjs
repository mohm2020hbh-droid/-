/**
 * Localization coverage.
 *
 * The thing that actually goes wrong with a second language is not a bad
 * translation — it is a *missing* one, which shows up to the player as Arabic
 * text on an English screen. These tests catch that mechanically:
 *
 *   1. Both dictionaries cover exactly the same keys.
 *   2. No English string still contains Arabic letters.
 *   3. Every key referenced by the markup exists.
 *   4. Every key referenced from JavaScript exists.
 *   5. Every `{placeholder}` is present in both languages.
 *   6. Every sound has a name in both languages, keyed by its stable id.
 *
 * Run with: node tests/js/i18n.test.mjs
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import { dictionaries, LANGUAGES } from "../../app/static/js/i18n.js";
import { SOUNDS, soundName } from "../../app/static/js/sounds.js";

const here = dirname(fileURLToPath(import.meta.url));
const staticDir = join(here, "..", "..", "app", "static");
const read = (...parts) => readFileSync(join(staticDir, ...parts), "utf8");

let passed = 0;
let failed = 0;

function check(label, condition, detail = "") {
  if (condition) {
    passed++;
    console.log(`  ok   ${label}${detail ? "  " + detail : ""}`);
  } else {
    failed++;
    console.log(`  FAIL ${label}${detail ? "  " + detail : ""}`);
  }
}

const ARABIC = /[؀-ۿ]/;
const { ar, en } = dictionaries();

/* ------------------------------------------------------------------ *
 * 1. Same keys on both sides
 * ------------------------------------------------------------------ */
const arKeys = Object.keys(ar).sort();
const enKeys = Object.keys(en).sort();
const missingInEn = arKeys.filter((k) => !(k in en));
const extraInEn = enKeys.filter((k) => !(k in ar));

check("the English dictionary covers every Arabic key",
  missingInEn.length === 0,
  missingInEn.length ? `missing: ${missingInEn.join(", ")}` : `${arKeys.length} keys`);

check("the English dictionary has no keys Arabic lacks",
  extraInEn.length === 0,
  extraInEn.length ? `orphaned: ${extraInEn.join(", ")}` : "");

/* ------------------------------------------------------------------ *
 * 2. No Arabic left in the English dictionary
 * ------------------------------------------------------------------ */
const arabicInEn = enKeys.filter((k) => ARABIC.test(en[k]));
check("no English string still contains Arabic text",
  arabicInEn.length === 0,
  arabicInEn.length ? `${arabicInEn.join(", ")}` : "");

const emptyStrings = arKeys.filter((k) => !ar[k]?.trim() || !en[k]?.trim());
check("no translation is blank",
  emptyStrings.length === 0,
  emptyStrings.length ? `${emptyStrings.join(", ")}` : "");

/* ------------------------------------------------------------------ *
 * 3. Keys the markup asks for
 * ------------------------------------------------------------------ */
const html = read("index.html");
const markupKeys = new Set();
for (const match of html.matchAll(/data-i18n(?:-placeholder|-aria-label|-title)?="([^"]+)"/g)) {
  markupKeys.add(match[1]);
}
const unknownMarkupKeys = [...markupKeys].filter((k) => !(k in ar));

check(`every key used in index.html exists (${markupKeys.size} used)`,
  unknownMarkupKeys.length === 0,
  unknownMarkupKeys.length ? `unknown: ${unknownMarkupKeys.join(", ")}` : "");

/* ------------------------------------------------------------------ *
 * 4. Keys the code asks for
 *
 * Only literal `t("...")` calls can be checked statically, which is exactly
 * why the few computed keys are built from a fixed prefix (`challenge.` plus
 * a challenge type) — those are covered separately below.
 * ------------------------------------------------------------------ */
const sources = ["app.js", "single.js", "online.js", "battle.js", "stages.js"];
const codeKeys = new Set();
for (const file of sources) {
  const source = read("js", file);
  for (const match of source.matchAll(/\bt\("([a-z0-9.]+)"/gi)) codeKeys.add(match[1]);
}
const unknownCodeKeys = [...codeKeys].filter((k) => !(k in ar));

check(`every key used in JavaScript exists (${codeKeys.size} used)`,
  unknownCodeKeys.length === 0,
  unknownCodeKeys.length ? `unknown: ${unknownCodeKeys.join(", ")}` : "");

// The computed ones: `challenge.${type}` in stages.js.
const challengeTypes = ["single", "timed", "sequence", "distorted"];
const missingChallenge = challengeTypes.filter((type) => !(`challenge.${type}` in ar));
check("every challenge type has a localized label",
  missingChallenge.length === 0,
  missingChallenge.length ? `missing: ${missingChallenge.join(", ")}` : "");

/* ------------------------------------------------------------------ *
 * 5. Placeholders match across languages
 * ------------------------------------------------------------------ */
const placeholders = (text) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(",");
const mismatched = arKeys.filter((k) => placeholders(ar[k]) !== placeholders(en[k] ?? ""));

check("every placeholder appears in both languages",
  mismatched.length === 0,
  mismatched.length
    ? mismatched.map((k) => `${k} (ar: ${placeholders(ar[k])} / en: ${placeholders(en[k])})`).join("; ")
    : "");

/* ------------------------------------------------------------------ *
 * 6. Sound names, keyed by the stable id
 * ------------------------------------------------------------------ */
const namelessSounds = SOUNDS.filter((s) => !s.name?.trim() || !s.nameEn?.trim());
check(`every one of the ${SOUNDS.length} sounds has an Arabic and an English name`,
  namelessSounds.length === 0,
  namelessSounds.length ? namelessSounds.map((s) => s.id).join(", ") : "");

const arabicInSoundEn = SOUNDS.filter((s) => ARABIC.test(s.nameEn));
check("no English sound name contains Arabic text",
  arabicInSoundEn.length === 0,
  arabicInSoundEn.length ? arabicInSoundEn.map((s) => s.id).join(", ") : "");

// The same id must resolve to a name in either language, and to different
// names — otherwise one of the two is really just a copy of the other.
const notTranslated = SOUNDS.filter((s) => soundName(s.id, "ar") === soundName(s.id, "en"));
check("the same sound id gives a different name in each language",
  notTranslated.length === 0,
  notTranslated.length ? notTranslated.map((s) => s.id).join(", ") : "");

check("an unknown sound id falls back to the server's name rather than breaking",
  soundName("not_a_sound", "en", "Server Name") === "Server Name"
  && soundName("not_a_sound", "en") === "not_a_sound");

/* ------------------------------------------------------------------ *
 * 7. Nothing visible is left hard-coded in the markup
 *
 * A string in index.html that is not behind a data-i18n attribute cannot be
 * translated, so the markup is scanned for stray Arabic.
 * ------------------------------------------------------------------ */
const untranslatedMarkup = [];
for (const match of html.matchAll(/>([^<>]*[؀-ۿ][^<>]*)</g)) {
  const text = match[1].trim();
  // The language switch names each language in its own script on purpose.
  if (text === "العربية") continue;
  const before = html.slice(Math.max(0, match.index - 400), match.index);
  const tag = before.lastIndexOf("<");
  if (tag >= 0 && /data-i18n/.test(before.slice(tag))) continue;
  untranslatedMarkup.push(text.slice(0, 40));
}
check("no Arabic text in index.html is outside a translated element",
  untranslatedMarkup.length === 0,
  untranslatedMarkup.length ? untranslatedMarkup.join(" | ") : "");

// Same scan over the screen code: a literal Arabic string in a handler can
// never be translated.
const hardCoded = [];
for (const file of sources) {
  const source = read("js", file);
  for (const line of source.split("\n")) {
    if (!ARABIC.test(line)) continue;
    hardCoded.push(`${file}: ${line.trim().slice(0, 50)}`);
  }
}
check("no screen module has a hard-coded Arabic string",
  hardCoded.length === 0,
  hardCoded.length ? hardCoded.join(" | ") : "");

/* ------------------------------------------------------------------ *
 * 8. Direction
 * ------------------------------------------------------------------ */
check("Arabic is right-to-left and English is left-to-right",
  LANGUAGES.find((l) => l.code === "ar").dir === "rtl"
  && LANGUAGES.find((l) => l.code === "en").dir === "ltr");

console.log(`\n  ${arKeys.length} keys x ${LANGUAGES.length} languages, ${SOUNDS.length} sound names`);
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
