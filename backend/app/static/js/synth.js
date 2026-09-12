/**
 * The synthesis engine behind the target sounds.
 *
 * The first version of this file drove every target from a single raw
 * oscillator (saw/square/sine/noise) through one filter. That is why the
 * animals came out sounding like a buzzer: what makes a voice read as a
 * *cat* rather than a tone is not its waveform, it is
 *
 *   - a glottal pulse source rather than a mathematical saw,
 *   - two or three formant resonances that glide over the sound,
 *   - and small period-to-period irregularity (jitter/shimmer), because
 *     perfect periodicity is the single strongest "electronic" cue.
 *
 * So this engine offers four voices, and each target picks the one that
 * matches how the real sound is actually produced:
 *
 *   voice    - glottal source + moving formants (animals, monsters)
 *   partials - independent inharmonic partials with their own decays
 *              (bells, chimes, plucked strings, drums — a real bell is
 *              inharmonic, which a harmonic series can never imitate)
 *   noise    - filtered noise, optionally in grains (rain, wind, fire)
 *   tone     - oscillators and FM (sirens, horns, phones, robots — these
 *              really are electronic, so here it is the correct answer)
 *
 * Pure arithmetic over Float32Array: no Web Audio, so the same renderer
 * runs in the browser and under Node in the tests.
 */

export const SOUND_RATE = 22050;

/* ------------------------------------------------------------------ *
 * Determinism
 * ------------------------------------------------------------------ */

/** Seeded PRNG, so every render of a target is bit-identical. */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* ------------------------------------------------------------------ *
 * Breakpoint curves
 * ------------------------------------------------------------------ */

/** Value at normalised position 0..1 from a [[pos, value], ...] list. */
function curveAt(points, position) {
  if (!points || !points.length) return 0;
  if (points.length === 1) return points[0][1];
  if (position <= points[0][0]) return points[0][1];
  for (let i = 0; i < points.length - 1; i++) {
    const [p0, v0] = points[i];
    const [p1, v1] = points[i + 1];
    if (position >= p0 && position <= p1) {
      const span = p1 - p0 || 1;
      return v0 + ((v1 - v0) * (position - p0)) / span;
    }
  }
  return points[points.length - 1][1];
}

/**
 * Amplitude envelope. `attack`/`release` are seconds; `shape` optionally
 * replaces the plain sustain with a breakpoint curve so a bray or a bark
 * can have its own internal swell.
 */
function envelopeAt(spec, t, duration) {
  const attack = spec.attack ?? 0.02;
  const release = spec.release ?? 0.15;
  let amplitude = 1;

  if (spec.shape) amplitude *= Math.max(0, curveAt(spec.shape, t / duration));
  if (t < attack) amplitude *= attack > 0 ? t / attack : 1;

  const releaseStart = duration - release;
  if (release > 0 && t > releaseStart) {
    const k = Math.max(0, 1 - (t - releaseStart) / release);
    amplitude *= k ** (spec.releaseCurve ?? 1.6);
  }
  return amplitude;
}

/* ------------------------------------------------------------------ *
 * Filters
 * ------------------------------------------------------------------ */

/**
 * Topology-preserving state-variable filter.
 *
 * Unlike a fixed biquad this stays stable while its cutoff moves every
 * single sample, which is what lets a formant *glide* — the difference
 * between a cat's "me-ow" and a flat buzz.
 */
class SvFilter {
  constructor(rate) {
    this.rate = rate;
    this.ic1 = 0;
    this.ic2 = 0;
  }

  process(input, freq, q, mode) {
    const clamped = Math.max(20, Math.min(this.rate * 0.45, freq));
    const g = Math.tan((Math.PI * clamped) / this.rate);
    const k = 1 / Math.max(0.05, q);
    const a1 = 1 / (1 + g * (g + k));
    const a2 = g * a1;
    const a3 = g * a2;

    const v3 = input - this.ic2;
    const v1 = a1 * this.ic1 + a2 * v3;
    const v2 = this.ic2 + a2 * this.ic1 + a3 * v3;
    this.ic1 = 2 * v1 - this.ic1;
    this.ic2 = 2 * v2 - this.ic2;

    if (mode === "lp") return v2;
    if (mode === "hp") return input - k * v1 - v2;
    return v1; // bandpass
  }
}

/** One-pole radiation/lip characteristic: tilts the source spectrum up. */
function differentiate(samples, coefficient = 0.97) {
  const out = new Float32Array(samples.length);
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    out[i] = samples[i] - coefficient * previous;
    previous = samples[i];
  }
  return out;
}

/* ------------------------------------------------------------------ *
 * 1. voice — glottal source through moving formants
 * ------------------------------------------------------------------ */

function renderVoice(spec, rate, random) {
  const duration = spec.duration;
  const length = Math.max(1, Math.round(duration * rate));
  const source = new Float32Array(length);

  const jitter = spec.jitter ?? 0.02;       // period-length irregularity
  const shimmer = spec.shimmer ?? 0.06;     // period-amplitude irregularity
  const subharmonic = spec.subharmonic ?? 0; // every other period ducked
  const openQuotient = spec.openQuotient ?? 0.55;

  // --- build the glottal pulse train, period by period ---
  let i = 0;
  let periodIndex = 0;
  while (i < length) {
    const position = i / length;
    let f0 = Math.max(20, curveAt(spec.f0, position));
    if (spec.vibrato) {
      // A sheep's bleat and a horse's neigh are both *defined* by a fast
      // frequency wobble; without it they are just held vowels.
      f0 *= 1 + spec.vibrato.depth * Math.sin((2 * Math.PI * spec.vibrato.rate * i) / rate);
    }
    const periodLength = Math.max(
      4,
      Math.round((rate / f0) * (1 + jitter * (random() * 2 - 1))),
    );

    let gain = 1 + shimmer * (random() * 2 - 1);
    // Ducking alternate periods puts real energy at f0/2 — the
    // diplophonic rasp that makes a roar sound like an animal and not
    // like a low sine.
    if (subharmonic > 0 && periodIndex % 2 === 1) gain *= 1 - subharmonic;

    const open = Math.max(2, Math.round(periodLength * openQuotient));
    for (let n = 0; n < periodLength && i + n < length; n++) {
      if (n < open) {
        const p = n / open;
        source[i + n] = Math.sin(Math.PI * p) ** 2 * gain;
      }
    }
    i += periodLength;
    periodIndex++;
  }

  let excitation = differentiate(source, spec.tilt ?? 0.97);

  // --- breath noise, mixed into the same excitation ---
  const breath = spec.breath ?? 0;
  if (breath > 0) {
    for (let n = 0; n < length; n++) {
      excitation[n] = excitation[n] * (1 - breath * 0.5) + (random() * 2 - 1) * breath;
    }
  }

  // --- parallel formant resonators ---
  const formants = (spec.formants ?? []).map((f) => ({
    curve: Array.isArray(f.f) ? f.f : [[0, f.f]],
    q: f.q ?? 8,
    gain: f.gain ?? 1,
    filter: new SvFilter(rate),
  }));

  const out = new Float32Array(length);
  const roughness = spec.roughness;

  for (let n = 0; n < length; n++) {
    const t = n / rate;
    const position = n / length;
    const input = excitation[n];

    let value = 0;
    if (formants.length) {
      for (const formant of formants) {
        value += formant.filter.process(input, curveAt(formant.curve, position), formant.q, "bp") * formant.gain;
      }
    } else {
      value = input;
    }

    if (roughness) {
      // Slow amplitude modulation: the growl on top of the voice.
      value *= 1 - roughness.depth * 0.5 * (1 + Math.sin(2 * Math.PI * roughness.rate * t));
    }

    out[n] = value * envelopeAt(spec, t, duration);
  }
  return out;
}

/* ------------------------------------------------------------------ *
 * 2. partials — independent inharmonic components
 * ------------------------------------------------------------------ */

function renderPartials(spec, rate, random) {
  const duration = spec.duration;
  const length = Math.max(1, Math.round(duration * rate));
  const out = new Float32Array(length);
  const base = spec.base;

  for (const partial of spec.partials) {
    const freq = base * partial.ratio;
    if (freq >= rate * 0.45) continue;
    const gain = partial.gain ?? 1;
    // Each partial rings for its own length — high partials dying first
    // is what a struck metal object actually does.
    const decay = partial.decay ?? duration;
    const phase = random() * Math.PI * 2;
    const omega = (2 * Math.PI * freq) / rate;
    // A touch of detune per partial stops it sounding like an additive
    // organ and more like a physical object.
    const detune = 1 + (partial.detune ?? 0.0006) * (random() * 2 - 1);

    for (let n = 0; n < length; n++) {
      const t = n / rate;
      out[n] += Math.sin(omega * detune * n + phase) * gain * Math.exp(-t / decay);
    }
  }

  const strike = spec.strike ?? 0;
  if (strike > 0) {
    // The initial noisy "clack" of hammer on metal / pick on string.
    const strikeLength = Math.round(rate * 0.012);
    const filter = new SvFilter(rate);
    for (let n = 0; n < strikeLength && n < length; n++) {
      const noise = filter.process(random() * 2 - 1, spec.strikeFreq ?? 3000, 1.0, "bp");
      out[n] += noise * strike * (1 - n / strikeLength);
    }
  }

  for (let n = 0; n < length; n++) {
    out[n] *= envelopeAt(spec, n / rate, duration);
  }
  return out;
}

/* ------------------------------------------------------------------ *
 * 3. noise — filtered, optionally granular
 * ------------------------------------------------------------------ */

function renderNoise(spec, rate, random) {
  const duration = spec.duration;
  const length = Math.max(1, Math.round(duration * rate));
  const out = new Float32Array(length);

  const bands = spec.bands ?? [{ f: spec.freq ?? 1500, q: spec.q ?? 0.7, gain: 1 }];
  const filters = bands.map(() => new SvFilter(rate));
  const mode = spec.mode ?? "bp";

  // Grains: short random bursts rather than a steady hiss. This is the
  // difference between "rain" and "white noise", and the whole character
  // of a crackling fire.
  const grains = spec.grains;
  let grainGate = new Float32Array(length).fill(1);
  if (grains) {
    grainGate = new Float32Array(length);
    const spacing = rate / grains.rate;
    let at = 0;
    while (at < length) {
      const decay = Math.max(2, Math.round(rate * (grains.decay ?? 0.02)));
      const amplitude = 0.4 + random() * 0.6;
      for (let n = 0; n < decay && at + n < length; n++) {
        grainGate[at + n] += amplitude * (1 - n / decay);
      }
      at += Math.max(4, Math.round(spacing * (1 + (grains.jitter ?? 0.6) * (random() * 2 - 1))));
    }
  }

  const tremolo = spec.tremolo;
  for (let n = 0; n < length; n++) {
    const t = n / rate;
    const position = n / length;
    const white = random() * 2 - 1;

    let value = 0;
    for (let b = 0; b < bands.length; b++) {
      const band = bands[b];
      const freq = curveAt(Array.isArray(band.f) ? band.f : [[0, band.f]], position);
      value += filters[b].process(white, freq, band.q ?? 0.7, mode) * (band.gain ?? 1);
    }

    if (tremolo) {
      value *= 1 - tremolo.depth * 0.5 * (1 + Math.sin(2 * Math.PI * tremolo.rate * t));
    }

    out[n] = value * grainGate[n] * envelopeAt(spec, t, duration);
  }
  return out;
}

/* ------------------------------------------------------------------ *
 * 4. tone — oscillators and FM, for things that really are electronic
 * ------------------------------------------------------------------ */

function waveform(type, phase, random) {
  switch (type) {
    case "sine": return Math.sin(2 * Math.PI * phase);
    case "triangle": return 4 * Math.abs(phase - Math.floor(phase + 0.5)) - 1;
    case "square": return phase - Math.floor(phase) < 0.5 ? 1 : -1;
    case "pulse": return phase - Math.floor(phase) < 0.15 ? 1 : -1;
    case "saw": return 2 * (phase - Math.floor(phase)) - 1;
    case "noise": return random() * 2 - 1;
    default: return Math.sin(2 * Math.PI * phase);
  }
}

function renderTone(spec, rate, random) {
  const duration = spec.duration;
  const length = Math.max(1, Math.round(duration * rate));
  const out = new Float32Array(length);
  const harmonics = spec.harmonics ?? 1;
  const noiseMix = spec.noiseMix ?? 0;
  const filter = spec.filter ? new SvFilter(rate) : null;

  let phase = 0;
  let modPhase = 0;
  for (let n = 0; n < length; n++) {
    const t = n / rate;
    const position = n / length;
    let freq = curveAt(spec.freq, position);

    if (spec.vibrato) {
      freq *= 1 + spec.vibrato.depth * Math.sin(2 * Math.PI * spec.vibrato.rate * t);
    }
    if (spec.fm) {
      // Ring/FM character: the classic "robot" and "alarm" texture.
      modPhase += (freq * spec.fm.ratio) / rate;
      freq *= 1 + spec.fm.depth * Math.sin(2 * Math.PI * modPhase);
    }
    phase += freq / rate;

    let value = 0;
    for (let h = 1; h <= harmonics; h++) {
      value += waveform(spec.wave ?? "sine", phase * h, random) / h;
    }
    if (noiseMix > 0) value = value * (1 - noiseMix) + (random() * 2 - 1) * noiseMix;

    if (spec.tremolo) {
      value *= 1 - spec.tremolo.depth * 0.5 * (1 + Math.sin(2 * Math.PI * spec.tremolo.rate * t));
    }
    if (filter) {
      const f = curveAt(
        Array.isArray(spec.filter.freq) ? spec.filter.freq : [[0, spec.filter.freq]],
        position,
      );
      value = filter.process(value, f, spec.filter.q ?? 0.8, spec.filter.type ?? "lp");
    }

    out[n] = value * envelopeAt(spec, t, duration);
  }
  return out;
}

/* ------------------------------------------------------------------ *
 * Assembly
 * ------------------------------------------------------------------ */

/**
 * 5. mix — several layers summed.
 *
 * Real complex sources are not one generator: a train whistle is a chord
 * *plus* escaping steam, thunder is a low rumble *plus* a sharp crack, a
 * helicopter is blade thump *plus* turbine whine. Each layer carries its
 * own kind and gain.
 */
function renderMix(spec, rate, random) {
  const length = Math.max(1, Math.round(spec.duration * rate));
  const out = new Float32Array(length);

  spec.layers.forEach((layer, index) => {
    const renderer = RENDERERS[layer.kind];
    if (!renderer) throw new Error(`unknown layer kind: ${layer.kind}`);
    // Each layer gets its own deterministic stream so layers never
    // correlate into a single buzzy source.
    const rendered = renderer(
      { ...layer, duration: layer.duration ?? spec.duration },
      rate,
      mulberry32(Math.floor(random() * 1e9) + index * 7919),
    );
    const gain = layer.gain ?? 1;
    const offset = Math.round((layer.at ?? 0) * rate);
    for (let n = 0; n < rendered.length && offset + n < length; n++) {
      out[offset + n] += rendered[n] * gain;
    }
  });

  for (let n = 0; n < length; n++) out[n] *= envelopeAt(spec, n / rate, spec.duration);
  return out;
}

const RENDERERS = {
  voice: renderVoice,
  partials: renderPartials,
  noise: renderNoise,
  tone: renderTone,
  mix: renderMix,
};

/** Render one continuous segment from a spec. */
export function renderSegment(spec, rate, seed) {
  const renderer = RENDERERS[spec.kind];
  if (!renderer) throw new Error(`unknown sound kind: ${spec.kind}`);
  return renderer(spec, rate, mulberry32(seed));
}

/**
 * Normalise for consistent perceived loudness across the whole library.
 *
 * Peak normalisation alone makes percussive targets (a bark, a drum) feel
 * far quieter than sustained ones (a siren) even at the same peak, so the
 * level is set by RMS and only then limited by peak.
 */
function normalise(samples, targetRms = 0.16, peakCeiling = 0.95) {
  let sum = 0;
  let peak = 0;
  for (let i = 0; i < samples.length; i++) {
    sum += samples[i] * samples[i];
    peak = Math.max(peak, Math.abs(samples[i]));
  }
  if (peak <= 0) return samples;

  const rms = Math.sqrt(sum / samples.length);
  let gain = rms > 1e-6 ? targetRms / rms : peakCeiling / peak;
  if (peak * gain > peakCeiling) gain = peakCeiling / peak;

  for (let i = 0; i < samples.length; i++) samples[i] *= gain;
  return samples;
}

/**
 * Render a full target: a single segment, a segment repeated (two barks),
 * or several genuinely different segments in sequence (a donkey's rising
 * bray then its falling haw).
 */
export function renderSound(sound, rate = SOUND_RATE) {
  const seed = sound.seed ?? 1;
  let combined;

  if (sound.parts) {
    const segments = sound.parts.map((part, index) => renderSegment(part, rate, seed + index * 101));
    const gap = Math.round((sound.partGap ?? 0.06) * rate);
    const total = segments.reduce((sum, s) => sum + s.length, 0) + gap * (segments.length - 1);
    combined = new Float32Array(total);
    let offset = 0;
    for (const segment of segments) {
      combined.set(segment, offset);
      offset += segment.length + gap;
    }
  } else {
    const spec = sound.spec;
    const repeats = spec.repeat ?? 1;
    const gap = Math.round((spec.gap ?? 0) * rate);
    const segment = renderSegment(spec, rate, seed);
    const total = segment.length * repeats + gap * (repeats - 1);
    combined = new Float32Array(total);
    for (let r = 0; r < repeats; r++) {
      const offset = r * (segment.length + gap);
      const gain = 1 - 0.1 * r; // repeats taper, so they are not mechanical
      for (let n = 0; n < segment.length; n++) combined[offset + n] = segment[n] * gain;
    }
  }

  return normalise(combined);
}

/** The "distorted" challenge treatment: harsher and harder to parse. */
export function distort(samples, amount = 0.7) {
  const drive = 1 + amount * 12;
  const random = mulberry32(7);
  const filter = new SvFilter(SOUND_RATE);
  const out = new Float32Array(samples.length);
  for (let i = 0; i < samples.length; i++) {
    const wet = Math.tanh(samples[i] * drive) * 0.8 + (random() * 2 - 1) * 0.06 * amount;
    out[i] = filter.process(wet, 1200, 0.7, "bp");
  }
  return normalise(out);
}
