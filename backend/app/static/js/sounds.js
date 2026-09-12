/**
 * The target sound library.
 *
 * Each entry describes *how the real sound is produced*, and hands that to
 * the matching generator in `synth.js`: animals are a glottal source
 * through gliding formants, struck metal is inharmonic partials with
 * independent decays, weather is granular noise, and the genuinely
 * electronic sounds (siren, phone, robot) are oscillators — because for
 * those, electronic is correct.
 *
 * Names are per-language; the `id` is stable and is what the scoring
 * engine, the stage list and the online protocol all key on, so switching
 * language never changes which audio is played.
 *
 * A recorded file at `/static/audio/<id>.(mp3|ogg|wav)` overrides the
 * synthesised version for both playback and scoring — see `targets.js`.
 */

import { SOUND_RATE, distort, renderSound } from "./synth.js";

export { SOUND_RATE, distort, renderSound };

/* Formant values below are in the range real measurements put them:
 * F1 tracks how open the mouth is, F2 how far forward the tongue is. That
 * is why a cat's "me-ow" is written as F1 rising while F2 falls — it is
 * literally a vowel glide from [i] to [a] to [u]. */

export const SOUNDS = [
  /* ---------------------------------------------------------------- *
   * Animals — glottal source + moving formants
   * ---------------------------------------------------------------- */

  { id: "lion", name: "زئير أسد", nameEn: "Lion", emoji: "🦁", seed: 101,
    spec: { kind: "voice", duration: 2.0, attack: 0.12, release: 0.65, releaseCurve: 1.3,
            f0: [[0, 85], [0.35, 112], [0.75, 95], [1, 68]],
            jitter: 0.035, shimmer: 0.12, subharmonic: 0.55, breath: 0.18,
            roughness: { rate: 38, depth: 0.5 },
            shape: [[0, 0.5], [0.3, 1], [0.8, 0.9], [1, 0.6]],
            formants: [{ f: 350, q: 4.5 }, { f: 780, q: 6, gain: 0.8 }, { f: 1700, q: 8, gain: 0.35 }] } },

  { id: "cat", name: "مواء قطة", nameEn: "Cat", emoji: "🐱", seed: 102,
    spec: { kind: "voice", duration: 0.85, attack: 0.05, release: 0.3,
            f0: [[0, 480], [0.25, 700], [0.6, 640], [1, 430]],
            jitter: 0.022, shimmer: 0.09, breath: 0.06,
            formants: [
              { f: [[0, 420], [0.35, 880], [1, 430]], q: 6 },
              { f: [[0, 2100], [0.35, 1350], [1, 950]], q: 7, gain: 0.7 },
              { f: 2950, q: 9, gain: 0.25 },
            ] } },

  { id: "dog", name: "نباح كلب", nameEn: "Dog", emoji: "🐶", seed: 103,
    spec: { kind: "voice", duration: 0.2, attack: 0.004, release: 0.13, releaseCurve: 2.2,
            repeat: 2, gap: 0.22,
            f0: [[0, 430], [0.2, 330], [1, 215]],
            jitter: 0.03, shimmer: 0.1, subharmonic: 0.28, breath: 0.26,
            formants: [{ f: 520, q: 5.5 }, { f: 1500, q: 7, gain: 0.85 }, { f: 2650, q: 9, gain: 0.4 }] } },

  { id: "cow", name: "خوار بقرة", nameEn: "Cow", emoji: "🐄", seed: 104,
    spec: { kind: "voice", duration: 1.6, attack: 0.16, release: 0.5,
            f0: [[0, 150], [0.3, 178], [0.7, 165], [1, 118]],
            jitter: 0.02, shimmer: 0.08, subharmonic: 0.16, breath: 0.13,
            shape: [[0, 0.6], [0.35, 1], [1, 0.7]],
            formants: [{ f: 400, q: 5 }, { f: 940, q: 6, gain: 0.7 }, { f: 2050, q: 9, gain: 0.3 }] } },

  { id: "sheep", name: "ثغاء خروف", nameEn: "Sheep", emoji: "🐑", seed: 105,
    spec: { kind: "voice", duration: 1.1, attack: 0.05, release: 0.3,
            f0: [[0, 360], [0.4, 340], [1, 300]],
            vibrato: { rate: 15, depth: 0.075 },
            jitter: 0.03, shimmer: 0.12, breath: 0.16, subharmonic: 0.12,
            formants: [{ f: 820, q: 6 }, { f: 1950, q: 7, gain: 0.7 }, { f: 2900, q: 9, gain: 0.3 }] } },

  { id: "horse", name: "صهيل حصان", nameEn: "Horse", emoji: "🐴", seed: 106,
    spec: { kind: "voice", duration: 1.25, attack: 0.03, release: 0.35,
            f0: [[0, 520], [0.15, 780], [0.45, 700], [1, 290]],
            vibrato: { rate: 21, depth: 0.06 },
            jitter: 0.04, shimmer: 0.14, subharmonic: 0.3, breath: 0.3,
            shape: [[0, 0.8], [0.2, 1], [1, 0.55]],
            formants: [{ f: 700, q: 5 }, { f: 1900, q: 7, gain: 0.8 }, { f: 3000, q: 9, gain: 0.35 }] } },

  { id: "donkey", name: "نهيق حمار", nameEn: "Donkey", emoji: "🫏", seed: 107, partGap: 0.04,
    parts: [
      // "hee" — rising, bright, strained
      { kind: "voice", duration: 0.55, attack: 0.03, release: 0.1,
        f0: [[0, 300], [0.45, 630], [1, 590]],
        jitter: 0.045, shimmer: 0.14, subharmonic: 0.35, breath: 0.26,
        formants: [{ f: 700, q: 5 }, { f: 2000, q: 7, gain: 0.85 }, { f: 3100, q: 9, gain: 0.3 }] },
      // "haw" — collapsing, dark, rasping
      { kind: "voice", duration: 0.62, attack: 0.02, release: 0.3, releaseCurve: 1.3,
        f0: [[0, 285], [0.4, 200], [1, 105]],
        jitter: 0.05, shimmer: 0.16, subharmonic: 0.5, breath: 0.3,
        roughness: { rate: 30, depth: 0.4 },
        formants: [{ f: 400, q: 4.5 }, { f: 900, q: 6, gain: 0.7 }, { f: 1800, q: 9, gain: 0.25 }] },
    ] },

  { id: "rooster", name: "صياح ديك", nameEn: "Rooster", emoji: "🐓", seed: 108, partGap: 0.05,
    parts: [
      { kind: "voice", duration: 0.17, attack: 0.01, release: 0.06,
        f0: [[0, 700], [1, 790]], jitter: 0.03, breath: 0.22,
        formants: [{ f: 900, q: 6 }, { f: 2250, q: 8, gain: 0.7 }] },
      { kind: "voice", duration: 0.15, attack: 0.01, release: 0.06,
        f0: [[0, 780], [1, 700]], jitter: 0.03, breath: 0.22,
        formants: [{ f: 900, q: 6 }, { f: 2250, q: 8, gain: 0.7 }] },
      // the long drawn-out third syllable
      { kind: "voice", duration: 0.46, attack: 0.02, release: 0.14,
        f0: [[0, 900], [0.25, 1010], [0.7, 960], [1, 840]],
        jitter: 0.035, shimmer: 0.12, breath: 0.26, subharmonic: 0.15,
        formants: [{ f: 1000, q: 6 }, { f: 2400, q: 8, gain: 0.75 }, { f: 3300, q: 10, gain: 0.3 }] },
      { kind: "voice", duration: 0.26, attack: 0.02, release: 0.16,
        f0: [[0, 700], [1, 500]], jitter: 0.04, breath: 0.28, subharmonic: 0.2,
        formants: [{ f: 800, q: 5.5 }, { f: 2000, q: 8, gain: 0.6 }] },
    ] },

  { id: "chicken", name: "قَقَقَة دجاجة", nameEn: "Chicken", emoji: "🐔", seed: 109,
    spec: { kind: "voice", duration: 0.11, attack: 0.004, release: 0.06, releaseCurve: 2,
            repeat: 3, gap: 0.15,
            f0: [[0, 430], [1, 300]],
            jitter: 0.035, shimmer: 0.12, breath: 0.2,
            formants: [{ f: 920, q: 7 }, { f: 1950, q: 8, gain: 0.7 }, { f: 3000, q: 10, gain: 0.3 }] } },

  { id: "duck", name: "بطة", nameEn: "Duck", emoji: "🦆", seed: 110,
    spec: { kind: "voice", duration: 0.16, attack: 0.005, release: 0.08, releaseCurve: 1.8,
            repeat: 2, gap: 0.18,
            f0: [[0, 330], [1, 205]],
            jitter: 0.04, shimmer: 0.13, subharmonic: 0.32, breath: 0.3,
            // The nasal, high-Q resonance is the whole "quack".
            formants: [{ f: 920, q: 10 }, { f: 2050, q: 7, gain: 0.6 }, { f: 3000, q: 9, gain: 0.25 }] } },

  { id: "monkey", name: "قرد", nameEn: "Monkey", emoji: "🐒", seed: 111, partGap: 0.08,
    parts: [
      // "oo oo" — closed vowel, low F1 and F2
      { kind: "voice", duration: 0.13, attack: 0.01, release: 0.06,
        f0: [[0, 620], [1, 660]], jitter: 0.03, breath: 0.12,
        formants: [{ f: 350, q: 7 }, { f: 820, q: 8, gain: 0.8 }] },
      { kind: "voice", duration: 0.13, attack: 0.01, release: 0.06,
        f0: [[0, 640], [1, 600]], jitter: 0.03, breath: 0.12,
        formants: [{ f: 350, q: 7 }, { f: 820, q: 8, gain: 0.8 }] },
      // "ah ah" — open vowel, F1 jumps up
      { kind: "voice", duration: 0.16, attack: 0.01, release: 0.09,
        f0: [[0, 420], [1, 370]], jitter: 0.035, shimmer: 0.1, breath: 0.2,
        formants: [{ f: 760, q: 6 }, { f: 1250, q: 7, gain: 0.8 }, { f: 2500, q: 9, gain: 0.3 }] },
      { kind: "voice", duration: 0.16, attack: 0.01, release: 0.09,
        f0: [[0, 400], [1, 340]], jitter: 0.035, shimmer: 0.1, breath: 0.2,
        formants: [{ f: 760, q: 6 }, { f: 1250, q: 7, gain: 0.8 }, { f: 2500, q: 9, gain: 0.3 }] },
    ] },

  { id: "elephant", name: "بوق فيل", nameEn: "Elephant", emoji: "🐘", seed: 112,
    spec: { kind: "voice", duration: 1.5, attack: 0.05, release: 0.3,
            f0: [[0, 240], [0.25, 520], [0.75, 560], [1, 300]],
            jitter: 0.025, shimmer: 0.09, subharmonic: 0.12, breath: 0.15,
            shape: [[0, 0.5], [0.25, 1], [0.8, 0.95], [1, 0.6]],
            // Brassy: strong upper resonances, unlike the cow's dark ones.
            formants: [{ f: 1000, q: 4 }, { f: 2050, q: 6, gain: 0.85 }, { f: 3100, q: 8, gain: 0.45 }] } },

  { id: "frog", name: "نقيق ضفدع", nameEn: "Frog", emoji: "🐸", seed: 113,
    spec: { kind: "voice", duration: 0.34, attack: 0.02, release: 0.1,
            repeat: 3, gap: 0.2,
            f0: [[0, 185], [1, 165]],
            jitter: 0.04, shimmer: 0.15, subharmonic: 0.3, breath: 0.2,
            // The croak *is* the pulsing — a frog's call is amplitude
            // modulated at a few tens of hertz.
            roughness: { rate: 33, depth: 0.95 },
            formants: [{ f: 520, q: 8 }, { f: 1250, q: 9, gain: 0.7 }] } },

  { id: "owl", name: "نعيق بومة", nameEn: "Owl", emoji: "🦉", seed: 114,
    spec: { kind: "voice", duration: 0.5, attack: 0.08, release: 0.25,
            repeat: 2, gap: 0.3,
            f0: [[0, 345], [0.3, 320], [1, 295]],
            jitter: 0.012, shimmer: 0.05, breath: 0.34,
            // Hollow: one strong narrow resonance and very little else.
            formants: [{ f: 330, q: 12 }, { f: 820, q: 6, gain: 0.25 }] } },

  { id: "monster", name: "زمجرة وحش", nameEn: "Monster", emoji: "👹", seed: 115,
    spec: { kind: "voice", duration: 2.0, attack: 0.15, release: 0.6, releaseCurve: 1.2,
            f0: [[0, 62], [0.4, 86], [0.75, 70], [1, 48]],
            jitter: 0.06, shimmer: 0.2, subharmonic: 0.7, breath: 0.3,
            roughness: { rate: 22, depth: 0.65 },
            shape: [[0, 0.45], [0.35, 1], [1, 0.65]],
            formants: [{ f: 250, q: 4 }, { f: 620, q: 5, gain: 0.75 }, { f: 1250, q: 8, gain: 0.35 }] } },

  { id: "snore", name: "شخير نائم", nameEn: "Snoring", emoji: "😴", seed: 116,
    spec: { kind: "voice", duration: 1.4, attack: 0.3, release: 0.45,
            f0: [[0, 78], [0.6, 96], [1, 74]],
            jitter: 0.06, shimmer: 0.2, subharmonic: 0.45, breath: 0.6,
            roughness: { rate: 16, depth: 0.8 },
            formants: [{ f: 300, q: 4 }, { f: 700, q: 5, gain: 0.6 }] } },

  { id: "laugh", name: "ضحكة عالية", nameEn: "Laughter", emoji: "😂", seed: 117, partGap: 0.06,
    parts: [0, 1, 2, 3].map((i) => ({
      kind: "voice", duration: 0.14, attack: 0.012, release: 0.08, releaseCurve: 2,
      f0: [[0, 400 - i * 22], [1, 330 - i * 22]],
      jitter: 0.035, shimmer: 0.12, breath: 0.22,
      formants: [{ f: 760, q: 6 }, { f: 1220, q: 7, gain: 0.8 }, { f: 2500, q: 9, gain: 0.3 }],
    })) },

  /* ---------------------------------------------------------------- *
   * Insects and small creatures
   * ---------------------------------------------------------------- */

  { id: "bee", name: "طنين نحلة", nameEn: "Bee", emoji: "🐝", seed: 118,
    // A bee is thin and nasal, not heavy: the bandpass throws away the low
    // end an engine keeps, so the two never read as the same buzz.
    spec: { kind: "tone", duration: 1.5, attack: 0.12, release: 0.3,
            wave: "saw", harmonics: 10, noiseMix: 0.08,
            freq: [[0, 235], [0.4, 280], [1, 245]],
            vibrato: { rate: 11, depth: 0.05 },
            // Wingbeat amplitude flutter: what separates a bee from a hum.
            tremolo: { rate: 46, depth: 0.32 },
            filter: { type: "bp", freq: 1500, q: 0.75 } } },

  { id: "mosquito", name: "طنين بعوضة", nameEn: "Mosquito", emoji: "🦟", seed: 119,
    spec: { kind: "tone", duration: 1.3, attack: 0.1, release: 0.25,
            wave: "saw", harmonics: 4, noiseMix: 0.06,
            freq: [[0, 610], [0.5, 700], [1, 630]],
            vibrato: { rate: 15, depth: 0.045 },
            filter: { type: "hp", freq: 480, q: 0.8 } } },

  { id: "cricket", name: "صرصور الليل", nameEn: "Cricket", emoji: "🦗", seed: 120,
    spec: { kind: "tone", duration: 0.11, attack: 0.005, release: 0.04,
            repeat: 5, gap: 0.1, wave: "square", freq: [[0, 4300]],
            tremolo: { rate: 58, depth: 0.9 },
            filter: { type: "hp", freq: 3000, q: 0.8 } } },

  { id: "bird", name: "زقزقة عصفور", nameEn: "Bird", emoji: "🐦", seed: 121,
    spec: { kind: "tone", duration: 0.13, attack: 0.006, release: 0.05,
            repeat: 4, gap: 0.11, wave: "sine", harmonics: 2,
            // Birdsong is a fast, near-pure frequency sweep.
            freq: [[0, 2600], [0.35, 4300], [1, 3000]] } },

  { id: "snake", name: "فحيح أفعى", nameEn: "Snake", emoji: "🐍", seed: 122,
    // A hiss is two things rain is not: narrowly band-limited very high up,
    // and delivered in breaths rather than as a continuous patter.
    spec: { kind: "noise", duration: 0.7, attack: 0.15, release: 0.25,
            repeat: 2, gap: 0.14,
            bands: [{ f: 6300, q: 2.2 }, { f: 8600, q: 1.6, gain: 0.35 }],
            mode: "hp" } },

  /* ---------------------------------------------------------------- *
   * Struck and resonant objects — inharmonic partials
   * ---------------------------------------------------------------- */

  { id: "bell", name: "رنين جرس", nameEn: "Bell", emoji: "🔔", seed: 123,
    spec: { kind: "partials", duration: 2.2, attack: 0.002, release: 0.5, base: 700,
            strike: 0.6, strikeFreq: 4200,
            // A real bell is inharmonic: hum, prime, tierce, quint, nominal.
            partials: [
              { ratio: 0.5, gain: 0.5, decay: 2.0 },
              { ratio: 1.0, gain: 1.0, decay: 1.6 },
              { ratio: 1.19, gain: 0.6, decay: 1.2 },
              { ratio: 1.5, gain: 0.5, decay: 0.9 },
              { ratio: 2.0, gain: 0.7, decay: 0.7 },
              { ratio: 2.6, gain: 0.35, decay: 0.45 },
              { ratio: 3.4, gain: 0.25, decay: 0.3 },
            ] } },

  { id: "doorbell", name: "جرس باب", nameEn: "Doorbell", emoji: "🚪", seed: 124, partGap: 0.02,
    parts: [660, 520].map((base) => ({
      kind: "partials", duration: 0.85, attack: 0.002, release: 0.35, base,
      strike: 0.35, strikeFreq: 3500,
      partials: [
        { ratio: 1.0, gain: 1.0, decay: 0.8 },
        { ratio: 2.76, gain: 0.4, decay: 0.4 },
        { ratio: 5.4, gain: 0.15, decay: 0.2 },
      ],
    })) },

  { id: "guitar", name: "عزف جيتار", nameEn: "Guitar", emoji: "🎸", seed: 125,
    // A guitar is heard as a phrase, not a note: three plucked strings
    // rising. The rising pitch and the pluck-decay shape together are what
    // no sustained horn or struck bell can be mistaken for.
    partGap: 0.01,
    parts: [220, 293, 392].map((base, index) => ({
      kind: "partials", duration: index === 2 ? 1.1 : 0.6,
      attack: 0.002, release: index === 2 ? 0.5 : 0.3, base,
      strike: 0.28, strikeFreq: base * 9,
      // Plucked string: harmonic, with the upper partials dying almost at
      // once, which keeps it clear of a bell's long bright shimmer.
      partials: Array.from({ length: 8 }, (_, i) => ({
        ratio: i + 1, gain: 1 / (i + 1) ** 1.25, decay: 1.4 / (i * 1.2 + 1),
      })),
    })) },

  { id: "drum", name: "دقات طبل", nameEn: "Drum", emoji: "🥁", seed: 126,
    spec: { kind: "mix", duration: 0.3, attack: 0.001, release: 0.2, releaseCurve: 2.5,
            repeat: 3, gap: 0.22,
            layers: [
              // Membrane modes: a drum is inharmonic and dies fast.
              { kind: "partials", base: 78, strike: 0.25, strikeFreq: 400, attack: 0.001, release: 0.22,
                partials: [
                  { ratio: 1.0, gain: 1.0, decay: 0.26 },
                  { ratio: 1.59, gain: 0.4, decay: 0.13 },
                  { ratio: 2.14, gain: 0.18, decay: 0.07 },
                ] },
              // A deep body thump: the drum has to stay unambiguously low.
              { kind: "noise", gain: 0.5, attack: 0.001, release: 0.09, releaseCurve: 3,
                bands: [{ f: 130, q: 1.0 }], mode: "lp" },
            ] } },

  { id: "car_horn", name: "منبه سيارة", nameEn: "Car Horn", emoji: "📢", seed: 127,
    spec: { kind: "partials", duration: 0.45, attack: 0.012, release: 0.06, base: 440,
            repeat: 2, gap: 0.2,
            // Car horns are a harmonic-rich dyad; the beating between the
            // two pitches is the recognisable part.
            partials: [
              { ratio: 1.0, gain: 1.0, decay: 3 },
              { ratio: 1.19, gain: 0.9, decay: 3 },
              { ratio: 2.0, gain: 0.55, decay: 3 },
              { ratio: 2.38, gain: 0.5, decay: 3 },
              { ratio: 3.0, gain: 0.3, decay: 3 },
              { ratio: 3.57, gain: 0.25, decay: 3 },
              { ratio: 4.0, gain: 0.15, decay: 3 },
            ] } },

  { id: "foghorn", name: "بوق ضباب", nameEn: "Foghorn", emoji: "📯", seed: 128,
    // A horn is a stopped pipe: odd harmonics only, which is the hollow,
    // mournful colour a plucked string does not have.
    spec: { kind: "partials", duration: 1.7, attack: 0.18, release: 0.4, base: 95,
            partials: [
              { ratio: 1.0, gain: 1.0, decay: 6 },
              { ratio: 3.0, gain: 0.55, decay: 6 },
              { ratio: 5.0, gain: 0.3, decay: 6 },
              { ratio: 7.0, gain: 0.14, decay: 6 },
              { ratio: 9.0, gain: 0.07, decay: 6 },
            ] } },

  /* ---------------------------------------------------------------- *
   * Vehicles and machines
   * ---------------------------------------------------------------- */

  { id: "ambulance", name: "صفارة إسعاف", nameEn: "Ambulance Siren", emoji: "🚑", seed: 129,
    spec: { kind: "tone", duration: 2.0, attack: 0.05, release: 0.2,
            wave: "sine", harmonics: 4,
            freq: [[0, 650], [0.25, 975], [0.5, 650], [0.75, 975], [1, 650]],
            filter: { type: "lp", freq: 3000, q: 0.7 } } },

  { id: "police", name: "صفارة شرطة", nameEn: "Police Siren", emoji: "🚓", seed: 130,
    spec: { kind: "tone", duration: 1.6, attack: 0.02, release: 0.1,
            wave: "square", harmonics: 2,
            // A yelp: much faster alternation than the ambulance wail.
            freq: [[0, 780], [0.17, 1180], [0.34, 780], [0.5, 1180], [0.67, 780], [0.84, 1180], [1, 800]],
            filter: { type: "lp", freq: 2800, q: 0.7 } } },

  { id: "train", name: "صفير قطار", nameEn: "Train Whistle", emoji: "🚆", seed: 131,
    spec: { kind: "mix", duration: 1.9, attack: 0.14, release: 0.45,
            layers: [
              // A steam whistle is a chord, not one pitch. Its intervals are
              // kept clear of the bell's so the two stay distinguishable.
              { kind: "partials", base: 400, gain: 0.8, attack: 0.14, release: 0.45,
                partials: [
                  { ratio: 1.0, gain: 1.0, decay: 8 },
                  { ratio: 1.335, gain: 0.85, decay: 8 },
                  { ratio: 2.0, gain: 0.45, decay: 8 },
                  { ratio: 2.67, gain: 0.25, decay: 8 },
                ] },
              // Escaping steam — on a real whistle this is most of the sound,
              // and it is what separates it from a struck bell.
              { kind: "noise", gain: 0.75, attack: 0.14, release: 0.45,
                bands: [{ f: 2400, q: 0.45 }, { f: 5000, q: 0.35, gain: 0.6 }] },
            ] } },

  { id: "airplane", name: "طائرة", nameEn: "Airplane", emoji: "✈️", seed: 132,
    spec: { kind: "mix", duration: 2.2, attack: 0.4, release: 0.5,
            layers: [
              // Turbine whine climbing, kept high and thin.
              { kind: "tone", gain: 0.35, wave: "saw", harmonics: 5, attack: 0.4, release: 0.5,
                freq: [[0, 210], [0.6, 330], [1, 300]],
                filter: { type: "bp", freq: [[0, 1900], [1, 2700]], q: 1.3 } },
              // Jet roar: a wide low rumble, not a mid-band buzz.
              { kind: "noise", gain: 1.0, attack: 0.4, release: 0.5,
                bands: [{ f: [[0, 380], [1, 720]], q: 0.45 }, { f: 2200, q: 0.4, gain: 0.45 }] },
            ] } },

  { id: "helicopter", name: "مروحة هليكوبتر", nameEn: "Helicopter", emoji: "🚁", seed: 133,
    spec: { kind: "mix", duration: 2.2, attack: 0.25, release: 0.4,
            layers: [
              // Blade slap: discrete thumps, not a tremolo'd tone. Short
              // grains keep them separate, which is the whole character.
              { kind: "noise", gain: 1.0, attack: 0.25, release: 0.4,
                bands: [{ f: 300, q: 1.2 }], mode: "lp",
                grains: { rate: 10, jitter: 0.08, decay: 0.028 } },
              // Turbine whine, kept low enough not to mask the rhythm.
              { kind: "tone", gain: 0.2, wave: "saw", harmonics: 4, attack: 0.25, release: 0.4,
                freq: [[0, 470]], filter: { type: "bp", freq: 1400, q: 1.4 } },
            ] } },

  { id: "motorcycle", name: "دراجة نارية", nameEn: "Motorcycle", emoji: "🏍️", seed: 134,
    spec: { kind: "mix", duration: 2.0, attack: 0.1, release: 0.3,
            layers: [
              // Low and heavy, but the identity is the exhaust pulse: a deep
              // engine that does not throb is a foghorn, and a buzz without
              // the low end is a bee.
              { kind: "tone", gain: 1.0, wave: "saw", harmonics: 7, noiseMix: 0.22,
                attack: 0.1, release: 0.3,
                freq: [[0, 78], [0.45, 165], [0.8, 140], [1, 104]],
                tremolo: { rate: 22, depth: 0.62 },
                filter: { type: "lp", freq: 1100, q: 0.9 } },
              // Each exhaust stroke gets its own grain, so the throb survives
              // even when a player's imitation smooths the timbre away.
              { kind: "noise", gain: 0.45, attack: 0.1, release: 0.3,
                bands: [{ f: 620, q: 0.7 }], mode: "lp",
                grains: { rate: 22, jitter: 0.12, decay: 0.03 } },
            ] } },

  { id: "door", name: "صرير باب", nameEn: "Creaking Door", emoji: "🚪", seed: 135,
    spec: { kind: "mix", duration: 1.4, attack: 0.1, release: 0.3,
            layers: [
              // Stick-slip friction: a rising, grainy squeal.
              { kind: "noise", gain: 1.0, attack: 0.1, release: 0.3,
                bands: [{ f: [[0, 500], [0.6, 1500], [1, 1100]], q: 5 }],
                grains: { rate: 55, jitter: 0.7, decay: 0.02 } },
              { kind: "tone", gain: 0.45, wave: "saw", harmonics: 3, noiseMix: 0.3,
                attack: 0.1, release: 0.3,
                freq: [[0, 320], [0.6, 900], [1, 700]],
                filter: { type: "bp", freq: [[0, 700], [1, 1400]], q: 3 } },
            ] } },

  /* ---------------------------------------------------------------- *
   * Weather and environment — granular noise
   * ---------------------------------------------------------------- */

  { id: "rain", name: "تساقط مطر", nameEn: "Rain", emoji: "🌧️", seed: 136,
    // Rain sits in the middle: bright droplet ticks over the dull body of
    // water hitting a surface. That low band is what stops it sounding like
    // a hiss, and the grains are what stop it sounding like a wash.
    spec: { kind: "noise", duration: 2.2, attack: 0.25, release: 0.4,
            bands: [{ f: 1900, q: 0.55 }, { f: 4200, q: 0.5, gain: 0.45 },
                    { f: 620, q: 0.9, gain: 0.4 }],
            // Individual droplets rather than a flat hiss.
            grains: { rate: 300, jitter: 0.9, decay: 0.006 } } },

  { id: "thunder", name: "دوي رعد", nameEn: "Thunder", emoji: "⛈️", seed: 137,
    spec: { kind: "mix", duration: 2.4, attack: 0.005, release: 1.0,
            layers: [
              // The crack.
              { kind: "noise", gain: 0.9, attack: 0.002, release: 0.25, releaseCurve: 2.5,
                duration: 0.35, bands: [{ f: 1800, q: 0.4 }] },
              // The long rumble that follows it.
              { kind: "noise", gain: 1.0, at: 0.05, attack: 0.06, release: 1.2,
                duration: 2.3, bands: [{ f: 110, q: 0.9 }], mode: "lp",
                tremolo: { rate: 3.5, depth: 0.4 } },
            ] } },

  { id: "wind", name: "عصف رياح", nameEn: "Wind", emoji: "🌬️", seed: 138,
    // Wind's signature is a resonant whistle that moves. The narrow band
    // sweeping through the mids is what a listener hears as gusting, and it
    // keeps wind clear of the ocean's flat low wash.
    spec: { kind: "noise", duration: 2.4, attack: 0.5, release: 0.7,
            bands: [{ f: [[0, 700], [0.4, 1550], [0.7, 1150], [1, 780]], q: 2.8 },
                    { f: 2700, q: 0.55, gain: 0.28 }],
            tremolo: { rate: 0.7, depth: 0.55 } } },

  { id: "ocean", name: "موج بحر", nameEn: "Ocean Waves", emoji: "🌊", seed: 139,
    // A wave is low, wide and slow: almost all the energy under 1 kHz,
    // shaped as one long swell that breaks and drains away.
    spec: { kind: "noise", duration: 2.6, attack: 0.7, release: 0.9,
            shape: [[0, 0.2], [0.45, 1], [0.72, 0.45], [1, 0.15]],
            bands: [{ f: 340, q: 0.65 }, { f: 900, q: 0.5, gain: 0.45 }],
            mode: "lp" } },

  { id: "fire", name: "طقطقة نار", nameEn: "Crackling Fire", emoji: "🔥", seed: 140,
    spec: { kind: "mix", duration: 2.0, attack: 0.15, release: 0.4,
            layers: [
              // Sharp crackles and pops.
              { kind: "noise", gain: 1.0, attack: 0.05, release: 0.3,
                bands: [{ f: 2400, q: 1.2 }],
                grains: { rate: 42, jitter: 0.95, decay: 0.018 } },
              // The steady roar underneath.
              { kind: "noise", gain: 0.4, attack: 0.2, release: 0.4,
                bands: [{ f: 450, q: 0.6 }], mode: "lp" },
            ] } },

  /* ---------------------------------------------------------------- *
   * Signals — genuinely electronic, so oscillators are correct
   * ---------------------------------------------------------------- */

  { id: "phone", name: "رنين هاتف", nameEn: "Phone Ring", emoji: "📞", seed: 141,
    spec: { kind: "tone", duration: 0.6, attack: 0.008, release: 0.06,
            repeat: 2, gap: 0.25, wave: "sine", harmonics: 3,
            freq: [[0, 900]], tremolo: { rate: 20, depth: 0.8 },
            filter: { type: "lp", freq: 3000, q: 0.7 } } },

  { id: "alarm", name: "منبه ساعة", nameEn: "Alarm Clock", emoji: "⏰", seed: 142,
    spec: { kind: "tone", duration: 0.17, attack: 0.004, release: 0.04,
            repeat: 4, gap: 0.11, wave: "square", freq: [[0, 1250]],
            filter: { type: "lp", freq: 3400, q: 0.8 } } },

  { id: "whistle", name: "صافرة", nameEn: "Whistle", emoji: "📣", seed: 143,
    spec: { kind: "mix", duration: 0.9, attack: 0.01, release: 0.08,
            layers: [
              { kind: "tone", gain: 1.0, wave: "sine", harmonics: 2, attack: 0.01, release: 0.08,
                freq: [[0, 2700], [0.5, 2850], [1, 2750]],
                vibrato: { rate: 24, depth: 0.02 } },
              // Air noise: a referee whistle is never a pure tone.
              { kind: "noise", gain: 0.28, attack: 0.01, release: 0.08,
                bands: [{ f: 2800, q: 2.5 }] },
            ] } },

  { id: "robot", name: "روبوت", nameEn: "Robot", emoji: "🤖", seed: 144,
    spec: { kind: "tone", duration: 0.16, attack: 0.003, release: 0.05,
            repeat: 3, gap: 0.08, wave: "square",
            freq: [[0, 520], [0.5, 780], [1, 620]],
            fm: { ratio: 3, depth: 0.35 },
            filter: { type: "lp", freq: 3200, q: 0.9 } } },
];

export const SOUNDS_BY_ID = Object.fromEntries(SOUNDS.map((s) => [s.id, s]));

/** Cached renders — a given target is always the same samples. */
const cache = new Map();

export function targetSamples(soundId) {
  if (!cache.has(soundId)) {
    const sound = SOUNDS_BY_ID[soundId];
    if (!sound) throw new Error(`unknown sound: ${soundId}`);
    cache.set(soundId, renderSound(sound));
  }
  return cache.get(soundId);
}

/**
 * The display name for a sound in the given language.
 *
 * Keyed on the stable `sound_id` — the same id the server broadcasts and the
 * scoring engine uses — so `donkey` is "حمار" or "Donkey" while remaining one
 * sound with one target audio and one score.
 *
 * `fallback` covers ids that exist only in the server's duel prompt list
 * (`app/models/sounds.py`), which has a few entries this synthesis library
 * does not: the server sends both names, so the caller passes the right one.
 */
export function soundName(soundId, language = "ar", fallback = null) {
  const sound = SOUNDS_BY_ID[soundId];
  if (!sound) return fallback ?? soundId;
  return language === "en" ? sound.nameEn : sound.name;
}
