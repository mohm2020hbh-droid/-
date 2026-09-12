/**
 * Acoustic similarity engine.
 *
 * Compares two recordings by what they *sound* like — spectral shape over
 * time, loudness contour, pitch contour and brightness. There is no speech
 * recognition anywhere in here: nothing is transcribed, and no word or
 * phoneme model exists. The input is raw PCM and the output is a number.
 *
 * Pure functions over Float32Array, so the same code runs in the browser and
 * under Node for the tests.
 */

export const ANALYSIS_RATE = 16000;   // everything is compared at this rate
const FRAME_SIZE = 512;               // 32 ms window
const HOP_SIZE = 160;                 // 10 ms hop
const MEL_BANDS = 24;
const MEL_MIN_HZ = 80;
const MEL_MAX_HZ = 7000;
const MAX_FRAMES = 500;               // bounds the DTW cost (~5 s of audio)
const SILENCE_FLOOR = 0.02;           // relative to peak, for trimming
const MIN_VOICED_PAIRS = 8;
const ACTIVITY_RANGE = 4.0;   // nats below the loudest frame that still counts
const ACTIVITY_KNEE = 2.6;
const ACTIVE_FRAME = 0.35;    // an aligned pair below this is treated as a gap
const EPS = 1e-10;

/* ------------------------------------------------------------------ *
 * FFT — iterative in-place radix-2
 * ------------------------------------------------------------------ */

function fftInPlace(re, im) {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      let t = re[i]; re[i] = re[j]; re[j] = t;
      t = im[i]; im[i] = im[j]; im[j] = t;
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (-2 * Math.PI) / len;
    const wRe = Math.cos(ang), wIm = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let curRe = 1, curIm = 0;
      for (let k = 0; k < len / 2; k++) {
        const aRe = re[i + k], aIm = im[i + k];
        const bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm;
        const bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe;
        re[i + k] = aRe + bRe; im[i + k] = aIm + bIm;
        re[i + k + len / 2] = aRe - bRe; im[i + k + len / 2] = aIm - bIm;
        const nextRe = curRe * wRe - curIm * wIm;
        curIm = curRe * wIm + curIm * wRe;
        curRe = nextRe;
      }
    }
  }
}

/** Magnitude spectrum of one real frame (length must be a power of two). */
function magnitudeSpectrum(frame) {
  const n = frame.length;
  const re = new Float64Array(n);
  const im = new Float64Array(n);
  re.set(frame);
  fftInPlace(re, im);
  const bins = n / 2 + 1;
  const mag = new Float64Array(bins);
  for (let i = 0; i < bins; i++) mag[i] = Math.hypot(re[i], im[i]);
  return mag;
}

/* ------------------------------------------------------------------ *
 * Preparation: resample, trim, normalise
 * ------------------------------------------------------------------ */

/** Linear resampling. Good enough: the features are 24 coarse mel bands. */
export function resample(input, fromRate, toRate) {
  if (fromRate === toRate) return Float32Array.from(input);
  const ratio = fromRate / toRate;
  const outLength = Math.floor(input.length / ratio);
  const out = new Float32Array(outLength);
  for (let i = 0; i < outLength; i++) {
    const pos = i * ratio;
    const i0 = Math.floor(pos);
    const i1 = Math.min(i0 + 1, input.length - 1);
    const frac = pos - i0;
    out[i] = input[i0] * (1 - frac) + input[i1] * frac;
  }
  return out;
}

/** Drop leading and trailing near-silence so timing starts at the sound. */
export function trimSilence(samples) {
  let peak = 0;
  for (let i = 0; i < samples.length; i++) peak = Math.max(peak, Math.abs(samples[i]));
  if (peak < EPS) return new Float32Array(0);

  const threshold = peak * SILENCE_FLOOR;
  let start = 0;
  while (start < samples.length && Math.abs(samples[start]) < threshold) start++;
  let end = samples.length - 1;
  while (end > start && Math.abs(samples[end]) < threshold) end--;
  return samples.slice(start, end + 1);
}

/** Scale to a fixed RMS so loudness never drives the score. */
function normaliseRms(samples) {
  let sum = 0;
  for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
  const rms = Math.sqrt(sum / Math.max(1, samples.length));
  if (rms < EPS) return samples;
  const gain = 0.1 / rms;
  const out = new Float32Array(samples.length);
  for (let i = 0; i < samples.length; i++) out[i] = samples[i] * gain;
  return out;
}

/* ------------------------------------------------------------------ *
 * Mel filterbank
 * ------------------------------------------------------------------ */

const hzToMel = (hz) => 2595 * Math.log10(1 + hz / 700);
const melToHz = (mel) => 700 * (10 ** (mel / 2595) - 1);

function buildMelFilters(bins, sampleRate) {
  const lowMel = hzToMel(MEL_MIN_HZ);
  const highMel = hzToMel(MEL_MAX_HZ);
  const points = new Float64Array(MEL_BANDS + 2);
  for (let i = 0; i < points.length; i++) {
    const mel = lowMel + ((highMel - lowMel) * i) / (MEL_BANDS + 1);
    points[i] = Math.floor(((bins - 1) * 2 * melToHz(mel)) / sampleRate);
  }

  const filters = [];
  for (let b = 0; b < MEL_BANDS; b++) {
    const left = points[b], centre = points[b + 1], right = points[b + 2];
    const weights = new Float64Array(bins);
    for (let k = left; k < centre; k++) {
      if (centre > left) weights[k] = (k - left) / (centre - left);
    }
    for (let k = centre; k < right; k++) {
      if (right > centre) weights[k] = (right - k) / (right - centre);
    }
    if (right === left) weights[Math.min(centre, bins - 1)] = 1;
    filters.push(weights);
  }
  return filters;
}

const hannWindow = (size) => {
  const w = new Float64Array(size);
  for (let i = 0; i < size; i++) w[i] = 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (size - 1));
  return w;
};

const WINDOW = hannWindow(FRAME_SIZE);
const MEL_FILTERS = buildMelFilters(FRAME_SIZE / 2 + 1, ANALYSIS_RATE);

/* ------------------------------------------------------------------ *
 * Pitch — normalised autocorrelation, no transcription involved
 * ------------------------------------------------------------------ */

const MIN_LAG = Math.floor(ANALYSIS_RATE / 1000); // 1000 Hz
const MAX_LAG = Math.floor(ANALYSIS_RATE / 70);   // 70 Hz
const VOICING_THRESHOLD = 0.32;

function estimatePitch(frame) {
  let energy = 0;
  for (let i = 0; i < frame.length; i++) energy += frame[i] * frame[i];
  if (energy < EPS) return 0;

  let bestLag = 0, bestScore = 0;
  for (let lag = MIN_LAG; lag <= Math.min(MAX_LAG, frame.length - 1); lag++) {
    let corr = 0, norm = 0;
    for (let i = 0; i + lag < frame.length; i++) {
      corr += frame[i] * frame[i + lag];
      norm += frame[i + lag] * frame[i + lag];
    }
    const score = corr / (Math.sqrt(energy * norm) + EPS);
    if (score > bestScore) { bestScore = score; bestLag = lag; }
  }
  if (bestScore < VOICING_THRESHOLD || bestLag === 0) return 0; // unvoiced
  return ANALYSIS_RATE / bestLag;
}

/* ------------------------------------------------------------------ *
 * Feature extraction
 * ------------------------------------------------------------------ */

/**
 * Turn samples into the per-frame acoustic description used for comparison.
 * Returns null when there is not enough sound to analyse.
 */
export function extractFeatures(samples, sampleRate = ANALYSIS_RATE) {
  let audio = resample(samples, sampleRate, ANALYSIS_RATE);
  audio = trimSilence(audio);
  if (audio.length < FRAME_SIZE * 2) return null;
  audio = normaliseRms(audio);

  const frameCount = Math.min(
    MAX_FRAMES,
    1 + Math.floor((audio.length - FRAME_SIZE) / HOP_SIZE),
  );
  if (frameCount < 4) return null;

  const mel = [];
  const energy = new Float64Array(frameCount);
  const centroid = new Float64Array(frameCount);
  const pitch = new Float64Array(frameCount);
  const frame = new Float64Array(FRAME_SIZE);

  for (let f = 0; f < frameCount; f++) {
    const offset = f * HOP_SIZE;
    let rms = 0;
    for (let i = 0; i < FRAME_SIZE; i++) {
      const sample = audio[offset + i];
      rms += sample * sample;
      frame[i] = sample * WINDOW[i];
    }
    energy[f] = Math.log(Math.sqrt(rms / FRAME_SIZE) + EPS);

    const raw = audio.subarray(offset, offset + FRAME_SIZE);
    pitch[f] = estimatePitch(raw);

    const mag = magnitudeSpectrum(frame);

    let weighted = 0, total = 0;
    for (let k = 0; k < mag.length; k++) {
      const hz = (k * ANALYSIS_RATE) / FRAME_SIZE;
      weighted += hz * mag[k];
      total += mag[k];
    }
    centroid[f] = total > EPS ? weighted / total : 0;

    // Log mel energies, then mean-removed and L2-normalised: what remains is
    // the *shape* of the spectrum, independent of level and channel gain.
    const band = new Float64Array(MEL_BANDS + 1);
    for (let b = 0; b < MEL_BANDS; b++) {
      const weights = MEL_FILTERS[b];
      let sum = 0;
      for (let k = 0; k < mag.length; k++) sum += weights[k] * mag[k];
      band[b] = Math.log(sum + EPS);
    }
    let mean = 0;
    for (let b = 0; b < MEL_BANDS; b++) mean += band[b];
    mean /= MEL_BANDS;
    let norm = 0;
    for (let b = 0; b < MEL_BANDS; b++) { band[b] -= mean; norm += band[b] * band[b]; }
    norm = Math.sqrt(norm) + EPS;
    for (let b = 0; b < MEL_BANDS; b++) band[b] /= norm;
    mel.push(band);
  }

  // The last dimension carries how *active* the frame is, so that a gap
  // matches a gap and a gap never matches a burst. Without it, silent frames
  // normalise into arbitrary directions and swamp everything else — which is
  // exactly what rhythmic targets like chirps and doorbells are made of.
  let peakDb = -Infinity;
  for (let f = 0; f < frameCount; f++) peakDb = Math.max(peakDb, energy[f]);
  const activity = new Float64Array(frameCount);
  for (let f = 0; f < frameCount; f++) {
    const a = Math.max(0, Math.min(1, (energy[f] - (peakDb - ACTIVITY_RANGE)) / ACTIVITY_KNEE));
    activity[f] = a;
    const vector = mel[f];
    for (let b = 0; b < MEL_BANDS; b++) vector[b] *= a;
    vector[MEL_BANDS] = Math.sqrt(Math.max(0, 1 - a * a));
  }

  return {
    mel, energy, centroid, pitch, activity, frameCount,
    durationSeconds: audio.length / ANALYSIS_RATE,
  };
}

/* ------------------------------------------------------------------ *
 * DTW over the mel sequences
 * ------------------------------------------------------------------ */

const cosineDistance = (a, b) => {
  let dot = 0;
  for (let i = 0; i < a.length; i++) dot += a[i] * b[i];
  return 1 - Math.max(-1, Math.min(1, dot)); // vectors are unit length
};

/**
 * Dynamic time warping: lets the player's imitation be slower or faster than
 * the target without being punished for it.
 */
function dtw(a, b) {
  const n = a.length, m = b.length;
  const cost = new Float64Array((n + 1) * (m + 1)).fill(Infinity);
  const at = (i, j) => i * (m + 1) + j;
  cost[at(0, 0)] = 0;

  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      const d = cosineDistance(a[i - 1], b[j - 1]);
      cost[at(i, j)] = d + Math.min(
        cost[at(i - 1, j)],
        cost[at(i, j - 1)],
        cost[at(i - 1, j - 1)],
      );
    }
  }

  // Walk the optimal path back so the other features can be compared aligned.
  const path = [];
  let i = n, j = m;
  while (i > 0 && j > 0) {
    path.push([i - 1, j - 1]);
    const diag = cost[at(i - 1, j - 1)];
    const up = cost[at(i - 1, j)];
    const left = cost[at(i, j - 1)];
    if (diag <= up && diag <= left) { i--; j--; }
    else if (up <= left) { i--; }
    else { j--; }
  }
  path.reverse();

  let totalDistance = 0;
  for (const [x, y] of path) totalDistance += cosineDistance(a[x], b[y]);
  return { meanDistance: totalDistance / Math.max(1, path.length), path };
};

/* ------------------------------------------------------------------ *
 * Comparison helpers
 * ------------------------------------------------------------------ */

function correlation(xs, ys) {
  const n = xs.length;
  if (n < 3) return null;
  let mx = 0, my = 0;
  for (let i = 0; i < n; i++) { mx += xs[i]; my += ys[i]; }
  mx /= n; my /= n;
  let num = 0, dx = 0, dy = 0;
  for (let i = 0; i < n; i++) {
    const a = xs[i] - mx, b = ys[i] - my;
    num += a * b; dx += a * a; dy += b * b;
  }
  if (dx < EPS || dy < EPS) return null;
  return num / Math.sqrt(dx * dy);
}

const median = (values) => {
  if (!values.length) return 0;
  const sorted = Float64Array.from(values).sort();
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
};

const mean = (values) => {
  if (!values.length) return 0;
  let sum = 0;
  for (const v of values) sum += v;
  return sum / values.length;
};

/* ------------------------------------------------------------------ *
 * The score
 * ------------------------------------------------------------------ */

const clamp01 = (x) => Math.max(0, Math.min(1, x));

/**
 * Spread a raw similarity across the 0..1 range.
 *
 * Two different real sounds rarely score below ~0.3 raw similarity, and a
 * human voice imitating a synthesised sound rarely rises above ~0.85, so the
 * useful band is stretched to fill the scale the player sees.
 */
const calibrate = (raw, floor, ceiling) =>
  clamp01((raw - floor) / (ceiling - floor));

/**
 * Compare a player's recording against a target sound.
 *
 * @returns {{score:number, parts:object, ok:boolean, reason?:string}}
 *   `score` is 0..100. `ok` is false when there was nothing usable to compare,
 *   in which case `reason` says why and `score` is 0.
 */
export function compareAudio(playerSamples, playerRate, targetSamples, targetRate) {
  const player = extractFeatures(playerSamples, playerRate);
  if (!player) return { score: 0, ok: false, reason: "no_audio", parts: {} };

  const target = extractFeatures(targetSamples, targetRate);
  if (!target) return { score: 0, ok: false, reason: "no_target", parts: {} };

  const { meanDistance, path } = dtw(player.mel, target.mel);

  // 1. Timbre — the dominant term: does it have the same spectral shape?
  const timbre = calibrate(1 - meanDistance / 2, 0.55, 0.97);

  // 2. Dynamics — does the loudness rise and fall the same way?
  const playerEnergy = [], targetEnergy = [];
  for (const [i, j] of path) {
    playerEnergy.push(player.energy[i]);
    targetEnergy.push(target.energy[j]);
  }
  const energyCorrelation = correlation(playerEnergy, targetEnergy);
  const dynamics = energyCorrelation === null ? null : calibrate(energyCorrelation, -0.2, 0.9);

  // 3. Pitch — compare the *shape* of the contour, not the absolute octave,
  //    so imitating a siren an octave low is not punished.
  const playerPitch = [], targetPitch = [];
  for (const [i, j] of path) {
    if (player.pitch[i] > 0 && target.pitch[j] > 0) {
      playerPitch.push(Math.log(player.pitch[i]));
      targetPitch.push(Math.log(target.pitch[j]));
    }
  }
  let pitch = null;
  if (playerPitch.length >= MIN_VOICED_PAIRS) {
    const pm = median(playerPitch), tm = median(targetPitch);
    const shape = correlation(
      playerPitch.map((v) => v - pm),
      targetPitch.map((v) => v - tm),
    );
    const octaves = Math.abs(pm - tm) / Math.LN2;
    const register = clamp01(1 - octaves / 2.5);
    const contour = shape === null ? 0.5 : calibrate(shape, -0.3, 0.85);
    pitch = 0.65 * contour + 0.35 * register;
  }

  // 4. Voicing — a periodic tone against a noisy hiss is a real mismatch,
  //    and this catches it even when the coarse spectral shape is close.
  let voicingPairs = 0, voicingAgreement = 0;
  for (const [i, j] of path) {
    if (player.activity[i] < ACTIVE_FRAME && target.activity[j] < ACTIVE_FRAME) continue;
    voicingPairs++;
    if ((player.pitch[i] > 0) === (target.pitch[j] > 0)) voicingAgreement++;
  }
  const voicing = voicingPairs >= 4 ? calibrate(voicingAgreement / voicingPairs, 0.2, 0.95) : null;

  // 5. Brightness — bright hiss against dark rumble should not score well.
  //    Measured over the frames that actually carry sound.
  const activeCentroid = (features) => {
    const values = [];
    for (let f = 0; f < features.frameCount; f++) {
      if (features.activity[f] >= ACTIVE_FRAME) values.push(features.centroid[f]);
    }
    return values.length ? mean(values) : mean(Array.from(features.centroid));
  };
  const brightnessRatio = Math.abs(
    Math.log((activeCentroid(player) + 1) / (activeCentroid(target) + 1)),
  );
  const brightness = clamp01(1 - brightnessRatio / 1.2);

  // Weighted blend, renormalised over whatever could actually be measured
  // (an unvoiced hiss has no pitch contour to compare).
  const terms = [
    [timbre, 0.40],
    [dynamics, 0.14],
    [pitch, 0.17],
    [voicing, 0.13],
    [brightness, 0.16],
  ].filter(([value]) => value !== null);

  let weighted = 0, totalWeight = 0;
  for (const [value, weight] of terms) { weighted += value * weight; totalWeight += weight; }
  const combined = weighted / totalWeight;

  return {
    score: Math.round(clamp01(combined) * 100),
    ok: true,
    parts: {
      timbre: Math.round(timbre * 100),
      dynamics: dynamics === null ? null : Math.round(dynamics * 100),
      pitch: pitch === null ? null : Math.round(pitch * 100),
      voicing: voicing === null ? null : Math.round(voicing * 100),
      brightness: Math.round(brightness * 100),
    },
  };
}
