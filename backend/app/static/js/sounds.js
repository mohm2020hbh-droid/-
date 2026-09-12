/**
 * The target sound bank.
 *
 * Every target is *synthesised from a recipe* rather than shipped as an audio
 * file: the bank costs nothing to download, works with no network at all, and
 * renders bit-identical samples every time, which is what makes scoring
 * repeatable. Pure arithmetic over Float32Array — no Web Audio — so the same
 * renderer runs in the browser and under Node in the tests.
 */

export const SOUND_RATE = 22050;

/* ------------------------------------------------------------------ *
 * Primitives
 * ------------------------------------------------------------------ */

/** Seeded PRNG, so "noise" is the same noise on every render. */
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function waveform(type, phase, random) {
  switch (type) {
    case "sine": return Math.sin(2 * Math.PI * phase);
    case "triangle": return 4 * Math.abs(phase - Math.floor(phase + 0.5)) - 1;
    case "square": return phase - Math.floor(phase) < 0.5 ? 1 : -1;
    case "pulse": return phase - Math.floor(phase) < 0.12 ? 1 : -1;
    case "saw": return 2 * (phase - Math.floor(phase)) - 1;
    case "noise": return random() * 2 - 1;
    default: return Math.sin(2 * Math.PI * phase);
  }
}

/** Frequency at time `t` from a piecewise-linear breakpoint list. */
function frequencyAt(points, t, duration) {
  if (points.length === 1) return points[0][1];
  const position = t / duration;
  for (let i = 0; i < points.length - 1; i++) {
    const [t0, f0] = points[i];
    const [t1, f1] = points[i + 1];
    if (position >= t0 && position <= t1) {
      const span = t1 - t0 || 1;
      return f0 + ((f1 - f0) * (position - t0)) / span;
    }
  }
  return points[points.length - 1][1];
}

/** RBJ biquad — one filter shape is enough to separate hiss from rumble. */
function biquad(samples, rate, { type = "lp", freq = 2000, q = 0.8 }) {
  const w0 = (2 * Math.PI * freq) / rate;
  const cos = Math.cos(w0), sin = Math.sin(w0);
  const alpha = sin / (2 * q);
  let b0, b1, b2;
  const a0 = 1 + alpha, a1 = -2 * cos, a2 = 1 - alpha;

  if (type === "hp") { b0 = (1 + cos) / 2; b1 = -(1 + cos); b2 = (1 + cos) / 2; }
  else if (type === "bp") { b0 = alpha; b1 = 0; b2 = -alpha; }
  else { b0 = (1 - cos) / 2; b1 = 1 - cos; b2 = (1 - cos) / 2; }

  const out = new Float32Array(samples.length);
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < samples.length; i++) {
    const x0 = samples[i];
    const y0 = (b0 / a0) * x0 + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2;
    x2 = x1; x1 = x0; y2 = y1; y1 = y0;
    out[i] = y0;
  }
  return out;
}

/** Render one continuous "bite" of sound from a spec. */
function renderBite(spec, seed) {
  const rate = SOUND_RATE;
  const duration = spec.duration;
  const length = Math.max(1, Math.round(duration * rate));
  const out = new Float32Array(length);
  const random = mulberry32(seed);

  const points = spec.freq || [[0, 440]];
  const harmonics = spec.harmonics || 1;
  const attack = spec.attack ?? 0.02;
  const release = spec.release ?? 0.15;
  const noiseMix = spec.noiseMix ?? 0;

  let phase = 0;
  for (let i = 0; i < length; i++) {
    const t = i / rate;
    let frequency = frequencyAt(points, t, duration);

    if (spec.vibrato) {
      frequency *= 1 + spec.vibrato.depth * Math.sin(2 * Math.PI * spec.vibrato.rate * t);
    }
    phase += frequency / rate;

    let value = 0;
    if (spec.wave === "noise") {
      value = waveform("noise", phase, random);
    } else {
      for (let h = 1; h <= harmonics; h++) {
        value += waveform(spec.wave || "sine", phase * h, random) / h;
      }
      if (noiseMix > 0) value = value * (1 - noiseMix) + (random() * 2 - 1) * noiseMix;
    }

    // Amplitude envelope: linear attack, optional tremolo, exponential release.
    let amplitude = 1;
    if (t < attack) amplitude = t / attack;
    const releaseStart = duration - release;
    if (t > releaseStart && release > 0) {
      amplitude *= Math.max(0, 1 - (t - releaseStart) / release) ** 1.5;
    }
    if (spec.tremolo) {
      amplitude *= 1 - spec.tremolo.depth * (0.5 + 0.5 * Math.sin(2 * Math.PI * spec.tremolo.rate * t));
    }

    out[i] = value * amplitude;
  }

  return spec.filter ? biquad(out, rate, spec.filter) : out;
}

/**
 * Render a full target.
 *
 * Most sounds are one bite repeated (a dog's two barks, a bird's chirps).
 * Some calls have genuinely different phrases in sequence — a donkey's rising
 * bray then its lower haw, a monkey's "ooh ooh ah ah" — and for those the
 * sound declares `parts`: a list of distinct specs concatenated with gaps,
 * each rendered as its own bite.
 */
export function renderSound(sound) {
  const combined = sound.parts ? renderParts(sound) : renderRepeated(sound);

  let peak = 0;
  for (let i = 0; i < combined.length; i++) peak = Math.max(peak, Math.abs(combined[i]));
  if (peak > 0) for (let i = 0; i < combined.length; i++) combined[i] = (combined[i] / peak) * 0.85;
  return combined;
}

function renderRepeated(sound) {
  const spec = sound.spec;
  const repeats = spec.repeat ?? 1;
  const gap = spec.gap ?? 0;
  const bite = renderBite(spec, sound.seed ?? 1);
  const gapSamples = Math.round(gap * SOUND_RATE);
  const total = bite.length * repeats + gapSamples * (repeats - 1);
  const out = new Float32Array(total);

  for (let r = 0; r < repeats; r++) {
    const offset = r * (bite.length + gapSamples);
    // A touch of level variation keeps repeats from sounding mechanical.
    const gain = 1 - 0.12 * r;
    for (let i = 0; i < bite.length; i++) out[offset + i] = bite[i] * gain;
  }
  return out;
}

function renderParts(sound) {
  const bites = sound.parts.map((part, index) => renderBite(part, (sound.seed ?? 1) + index * 101));
  const gapSamples = Math.round((sound.partGap ?? 0.08) * SOUND_RATE);
  const total = bites.reduce((sum, bite) => sum + bite.length, 0) + gapSamples * (bites.length - 1);
  const out = new Float32Array(total);

  let offset = 0;
  for (const bite of bites) {
    out.set(bite, offset);
    offset += bite.length + gapSamples;
  }
  return out;
}

/** Apply the "distorted" challenge treatment: harsher, and harder to parse. */
export function distort(samples, amount = 0.7) {
  const drive = 1 + amount * 12;
  const out = new Float32Array(samples.length);
  const random = mulberry32(7);
  for (let i = 0; i < samples.length; i++) {
    const wet = Math.tanh(samples[i] * drive) * 0.8 + (random() * 2 - 1) * 0.06 * amount;
    out[i] = wet;
  }
  return biquad(out, SOUND_RATE, { type: "bp", freq: 1200, q: 0.7 });
}

/* ------------------------------------------------------------------ *
 * The bank
 * ------------------------------------------------------------------ */

export const SOUNDS = [
  { id: "ambulance", name: "صفارة إسعاف", emoji: "🚑", seed: 11,
    spec: { wave: "sine", duration: 1.6, harmonics: 3, attack: 0.05, release: 0.2,
            freq: [[0, 650], [0.25, 980], [0.5, 650], [0.75, 980], [1, 650]] } },

  { id: "police", name: "صفارة شرطة", emoji: "🚓", seed: 12,
    spec: { wave: "square", duration: 1.4, attack: 0.01, release: 0.1, filter: { type: "lp", freq: 2600 },
            freq: [[0, 780], [0.5, 1150], [1, 780]] } },

  { id: "car_horn", name: "منبه سيارة", emoji: "📢", seed: 13,
    spec: { wave: "saw", duration: 0.5, harmonics: 5, attack: 0.01, release: 0.06,
            repeat: 2, gap: 0.22, filter: { type: "bp", freq: 1900, q: 1.2 }, freq: [[0, 620]] } },

  { id: "bee", name: "طنين نحلة", emoji: "🐝", seed: 14,
    spec: { wave: "saw", duration: 1.5, harmonics: 5, attack: 0.1, release: 0.25,
            vibrato: { rate: 9, depth: 0.05 }, filter: { type: "lp", freq: 1400 },
            freq: [[0, 190], [0.5, 235], [1, 195]] } },

  { id: "mosquito", name: "طنين بعوضة", emoji: "🦟", seed: 15,
    spec: { wave: "saw", duration: 1.3, harmonics: 3, attack: 0.08, release: 0.2,
            vibrato: { rate: 13, depth: 0.04 }, filter: { type: "hp", freq: 500 },
            freq: [[0, 620], [0.5, 700], [1, 640]] } },

  { id: "snake", name: "فحيح أفعى", emoji: "🐍", seed: 16,
    spec: { wave: "noise", duration: 1.5, attack: 0.15, release: 0.4,
            filter: { type: "hp", freq: 3200, q: 0.7 } } },

  { id: "wind", name: "عصف رياح", emoji: "🌬️", seed: 17,
    spec: { wave: "noise", duration: 2.2, attack: 0.5, release: 0.7,
            tremolo: { rate: 0.7, depth: 0.5 }, filter: { type: "lp", freq: 900, q: 0.6 } } },

  { id: "ocean", name: "موج بحر", emoji: "🌊", seed: 18,
    spec: { wave: "noise", duration: 2.4, attack: 0.7, release: 0.9,
            filter: { type: "lp", freq: 1500, q: 0.5 } } },

  { id: "rain", name: "تساقط مطر", emoji: "🌧️", seed: 19,
    spec: { wave: "noise", duration: 2.0, attack: 0.2, release: 0.4,
            filter: { type: "hp", freq: 2200, q: 0.6 } } },

  { id: "thunder", name: "دوي رعد", emoji: "⛈️", seed: 20,
    spec: { wave: "noise", duration: 1.8, attack: 0.02, release: 1.1,
            filter: { type: "lp", freq: 260, q: 0.9 } } },

  { id: "frog", name: "نقيق ضفدع", emoji: "🐸", seed: 21,
    spec: { wave: "pulse", duration: 0.26, attack: 0.01, release: 0.06, harmonics: 2,
            repeat: 3, gap: 0.18, filter: { type: "lp", freq: 1100 },
            freq: [[0, 165], [1, 140]] } },

  { id: "cat", name: "مواء قطة", emoji: "🐱", seed: 22,
    spec: { wave: "saw", duration: 0.9, harmonics: 6, attack: 0.08, release: 0.3,
            filter: { type: "bp", freq: 900, q: 1.1 },
            freq: [[0, 420], [0.35, 640], [1, 380]] } },

  { id: "dog", name: "نباح كلب", emoji: "🐶", seed: 23,
    spec: { wave: "saw", duration: 0.22, harmonics: 5, attack: 0.005, release: 0.12,
            noiseMix: 0.25, repeat: 2, gap: 0.2, filter: { type: "lp", freq: 1800 },
            freq: [[0, 320], [0.3, 240], [1, 180]] } },

  { id: "cow", name: "خوار بقرة", emoji: "🐄", seed: 24,
    spec: { wave: "saw", duration: 1.5, harmonics: 7, attack: 0.15, release: 0.45,
            filter: { type: "lp", freq: 1100 },
            freq: [[0, 175], [0.4, 210], [1, 140]] } },

  { id: "sheep", name: "ثغاء خروف", emoji: "🐑", seed: 25,
    spec: { wave: "saw", duration: 1.1, harmonics: 6, attack: 0.06, release: 0.3,
            vibrato: { rate: 14, depth: 0.06 }, filter: { type: "bp", freq: 1000, q: 0.9 },
            freq: [[0, 340], [1, 300]] } },

  { id: "rooster", name: "صياح ديك", emoji: "🐓", seed: 26,
    spec: { wave: "saw", duration: 1.2, harmonics: 5, attack: 0.03, release: 0.25,
            filter: { type: "bp", freq: 1300, q: 0.8 },
            freq: [[0, 520], [0.2, 760], [0.45, 700], [0.7, 840], [1, 480]] } },

  { id: "bird", name: "زقزقة عصفور", emoji: "🐦", seed: 27,
    spec: { wave: "sine", duration: 0.16, attack: 0.01, release: 0.06, harmonics: 2,
            repeat: 4, gap: 0.1, freq: [[0, 2400], [0.5, 3400], [1, 2600]] } },

  { id: "owl", name: "نعيق بومة", emoji: "🦉", seed: 28,
    spec: { wave: "sine", duration: 0.5, attack: 0.09, release: 0.25, harmonics: 2,
            repeat: 2, gap: 0.25, freq: [[0, 330], [0.4, 300], [1, 280]] } },

  { id: "train", name: "صفير قطار", emoji: "🚆", seed: 29,
    spec: { wave: "sine", duration: 1.7, harmonics: 4, attack: 0.15, release: 0.5,
            noiseMix: 0.12, filter: { type: "lp", freq: 2400 },
            freq: [[0, 440], [0.15, 520], [0.85, 510], [1, 420]] } },

  { id: "phone", name: "رنين هاتف", emoji: "📞", seed: 30,
    spec: { wave: "sine", duration: 0.5, harmonics: 2, attack: 0.01, release: 0.05,
            tremolo: { rate: 20, depth: 0.7 }, repeat: 2, gap: 0.22, freq: [[0, 900]] } },

  { id: "alarm", name: "منبه ساعة", emoji: "⏰", seed: 31,
    spec: { wave: "square", duration: 0.18, attack: 0.005, release: 0.04,
            repeat: 4, gap: 0.11, filter: { type: "lp", freq: 3200 }, freq: [[0, 1250]] } },

  { id: "doorbell", name: "جرس باب", emoji: "🔔", seed: 32,
    spec: { wave: "sine", duration: 0.75, harmonics: 3, attack: 0.005, release: 0.6,
            repeat: 2, gap: 0.05, freq: [[0, 660], [1, 655]] } },

  { id: "helicopter", name: "مروحة هليكوبتر", emoji: "🚁", seed: 33,
    spec: { wave: "pulse", duration: 2.0, attack: 0.2, release: 0.4, noiseMix: 0.4,
            tremolo: { rate: 11, depth: 0.8 }, filter: { type: "lp", freq: 700 },
            freq: [[0, 70]] } },

  { id: "motorcycle", name: "محرك دراجة نارية", emoji: "🏍️", seed: 34,
    spec: { wave: "saw", duration: 1.8, harmonics: 6, attack: 0.1, release: 0.3, noiseMix: 0.3,
            tremolo: { rate: 22, depth: 0.35 }, filter: { type: "lp", freq: 1300 },
            freq: [[0, 95], [0.5, 190], [1, 130]] } },

  { id: "lion", name: "زئير أسد", emoji: "🦁", seed: 35,
    spec: { wave: "saw", duration: 1.7, harmonics: 8, attack: 0.12, release: 0.5, noiseMix: 0.3,
            filter: { type: "lp", freq: 800 },
            freq: [[0, 110], [0.4, 145], [1, 95]] } },

  { id: "elephant", name: "بوق فيل", emoji: "🐘", seed: 36,
    spec: { wave: "saw", duration: 1.4, harmonics: 7, attack: 0.06, release: 0.3,
            filter: { type: "bp", freq: 800, q: 0.9 },
            freq: [[0, 300], [0.3, 560], [0.8, 600], [1, 420]] } },

  { id: "cricket", name: "صرصور الليل", emoji: "🦗", seed: 37,
    spec: { wave: "square", duration: 0.09, attack: 0.005, release: 0.03,
            repeat: 5, gap: 0.07, filter: { type: "hp", freq: 3000 }, freq: [[0, 4200]] } },

  { id: "snore", name: "شخير نائم", emoji: "😴", seed: 38,
    spec: { wave: "saw", duration: 1.2, harmonics: 5, attack: 0.3, release: 0.5, noiseMix: 0.5,
            tremolo: { rate: 18, depth: 0.5 }, filter: { type: "lp", freq: 600 },
            freq: [[0, 85], [0.6, 120], [1, 80]] } },

  { id: "laugh", name: "ضحكة عالية", emoji: "😂", seed: 39,
    spec: { wave: "saw", duration: 0.19, harmonics: 5, attack: 0.02, release: 0.09,
            repeat: 4, gap: 0.07, filter: { type: "bp", freq: 1100, q: 0.9 },
            freq: [[0, 400], [1, 330]] } },

  { id: "guitar", name: "عزف جيتار", emoji: "🎸", seed: 40,
    spec: { wave: "triangle", duration: 1.1, harmonics: 6, attack: 0.005, release: 0.9,
            filter: { type: "lp", freq: 2600 }, freq: [[0, 246]] } },

  /* -------- expansion pack: clearer, more varied animals and objects -------- */

  { id: "donkey", name: "نهيق حمار", emoji: "🫏", seed: 41, partGap: 0.05,
    parts: [
      { wave: "saw", duration: 0.5, harmonics: 6, attack: 0.02, release: 0.1, noiseMix: 0.35,
        filter: { type: "bp", freq: 900, q: 0.8 }, freq: [[0, 200], [0.6, 560], [1, 480]] },
      { wave: "saw", duration: 0.45, harmonics: 6, attack: 0.05, release: 0.25, noiseMix: 0.3,
        filter: { type: "lp", freq: 700 }, freq: [[0, 260], [1, 140]] },
    ] },

  { id: "horse", name: "صهيل حصان", emoji: "🐴", seed: 42,
    spec: { wave: "saw", duration: 1.2, harmonics: 6, attack: 0.02, release: 0.3, noiseMix: 0.3,
            vibrato: { rate: 22, depth: 0.16 }, filter: { type: "bp", freq: 650, q: 0.7 },
            freq: [[0, 200], [0.15, 380], [0.5, 420], [1, 220]] } },

  { id: "chicken", name: "قَقَقَة دجاجة", emoji: "🐔", seed: 43,
    spec: { wave: "saw", duration: 0.12, harmonics: 3, attack: 0.005, release: 0.05, noiseMix: 0.15,
            repeat: 3, gap: 0.13, filter: { type: "bp", freq: 900, q: 1.0 },
            freq: [[0, 480], [1, 300]] } },

  { id: "duck", name: "بطة تصدر صوتًا", emoji: "🦆", seed: 44,
    spec: { wave: "square", duration: 0.16, attack: 0.005, release: 0.05, noiseMix: 0.2,
            repeat: 2, gap: 0.18, filter: { type: "bp", freq: 750, q: 1.0 },
            freq: [[0, 260], [1, 180]] } },

  { id: "monkey", name: "صوت قرد", emoji: "🐒", seed: 45, partGap: 0.09,
    parts: [
      { wave: "saw", duration: 0.12, harmonics: 4, attack: 0.005, release: 0.06, freq: [[0, 700]] },
      { wave: "saw", duration: 0.12, harmonics: 4, attack: 0.005, release: 0.06, freq: [[0, 700]] },
      { wave: "saw", duration: 0.14, harmonics: 4, attack: 0.01, release: 0.08, freq: [[0, 350]] },
      { wave: "saw", duration: 0.14, harmonics: 4, attack: 0.01, release: 0.08, freq: [[0, 350]] },
    ] },

  { id: "airplane", name: "طائرة", emoji: "✈️", seed: 46,
    spec: { wave: "saw", duration: 2.0, harmonics: 6, attack: 0.3, release: 0.5, noiseMix: 0.45,
            tremolo: { rate: 30, depth: 0.15 }, filter: { type: "lp", freq: 1400 },
            freq: [[0, 90], [0.6, 160], [1, 140]] } },

  { id: "door", name: "صرير باب", emoji: "🚪", seed: 47,
    spec: { wave: "saw", duration: 1.3, attack: 0.15, release: 0.3, noiseMix: 0.25,
            filter: { type: "bp", freq: 1100, q: 1.3 },
            freq: [[0, 300], [0.4, 900], [0.7, 850], [1, 400]] } },

  { id: "bell", name: "رنين جرس", emoji: "🔔", seed: 48,
    spec: { wave: "sine", duration: 1.6, harmonics: 5, attack: 0.005, release: 1.4,
            filter: { type: "bp", freq: 740, q: 2.6 }, freq: [[0, 740]] } },

  { id: "foghorn", name: "بوق ضباب", emoji: "📯", seed: 49,
    spec: { wave: "saw", duration: 1.4, harmonics: 4, attack: 0.1, release: 0.4,
            filter: { type: "lp", freq: 500 }, freq: [[0, 110]] } },

  { id: "fire", name: "طقطقة نار", emoji: "🔥", seed: 50,
    spec: { wave: "noise", duration: 1.7, attack: 0.15, release: 0.4,
            tremolo: { rate: 14, depth: 0.55 }, filter: { type: "bp", freq: 2200, q: 0.5 } } },

  { id: "monster", name: "زمجرة وحش", emoji: "👹", seed: 51,
    spec: { wave: "saw", duration: 1.9, harmonics: 9, attack: 0.15, release: 0.6, noiseMix: 0.55,
            tremolo: { rate: 7, depth: 0.45 }, filter: { type: "lp", freq: 450 },
            freq: [[0, 70], [0.35, 100], [0.7, 60], [1, 50]] } },

  { id: "robot", name: "صفير روبوت", emoji: "🤖", seed: 52,
    spec: { wave: "square", duration: 0.14, attack: 0.003, release: 0.04,
            vibrato: { rate: 9, depth: 0.35 }, repeat: 3, gap: 0.07,
            filter: { type: "lp", freq: 3200 }, freq: [[0, 650]] } },

  { id: "whistle", name: "صافرة حادة", emoji: "📣", seed: 53,
    spec: { wave: "sine", duration: 0.9, harmonics: 2, attack: 0.005, release: 0.05,
            filter: { type: "bp", freq: 2800, q: 2.0 }, freq: [[0, 2800]] } },

  { id: "drum", name: "دقات طبل", emoji: "🥁", seed: 54,
    spec: { wave: "noise", duration: 0.09, attack: 0.002, release: 0.07,
            repeat: 3, gap: 0.22, filter: { type: "lp", freq: 160, q: 1.1 } } },
];

export const SOUNDS_BY_ID = Object.fromEntries(SOUNDS.map((s) => [s.id, s]));

/** Cached renders — a target is always the same samples. */
const cache = new Map();

export function targetSamples(soundId) {
  if (!cache.has(soundId)) {
    const sound = SOUNDS_BY_ID[soundId];
    if (!sound) throw new Error(`unknown sound: ${soundId}`);
    cache.set(soundId, renderSound(sound));
  }
  return cache.get(soundId);
}
