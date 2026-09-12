/**
 * Where a target sound comes from.
 *
 * Every target is synthesised locally by synth.js, so the game is fully
 * playable offline with no downloads and no server. This module adds one
 * thing on top: a real recording, if one has been dropped in, wins over the
 * synthesised version.
 *
 * To use a real recording for a sound, drop a file into
 *
 *     backend/app/static/audio/<sound_id>.mp3        (or .ogg / .wav)
 *
 * using the sound's id exactly as it appears in sounds.js — `donkey.mp3`,
 * `lion.wav`, `train.ogg`. Nothing else needs to change: the server lists
 * what is there at `/api/target-audio` and the file is picked up on the next
 * page load.
 *
 * The same buffer is used for playback AND for scoring, so the two can never
 * drift apart: a player is always scored against exactly what they heard.
 */

import { SOUND_RATE, targetSamples } from "./sounds.js";

/** soundId -> Float32Array. Holds whichever source won. */
const resolved = new Map();
/** soundId -> in-flight promise, so a sound is never fetched twice at once. */
const pending = new Map();

/**
 * soundId -> filename, for the recordings that are actually present.
 *
 * The server lists them in one request rather than the client guessing at
 * three extensions per sound: 44 sounds would otherwise mean a burst of
 * speculative 404s in the console on every session.
 */
let catalogue = null;
let cataloguePromise = null;

async function loadCatalogue() {
  if (catalogue) return catalogue;
  if (cataloguePromise) return cataloguePromise;

  cataloguePromise = (async () => {
    try {
      const response = await fetch("/api/target-audio", { cache: "no-store" });
      catalogue = response.ok ? (await response.json()).available ?? {} : {};
    } catch {
      // Offline, or an older server without the endpoint: synthesise
      // everything, which is the normal case anyway.
      catalogue = {};
    }
    cataloguePromise = null;
    return catalogue;
  })();

  return cataloguePromise;
}

/**
 * Decode an audio file to mono Float32 at SOUND_RATE.
 *
 * OfflineAudioContext does the resampling, which keeps a 44.1 kHz download
 * comparable with a 22.05 kHz synthesised target.
 */
async function decodeToTargetRate(bytes) {
  const Ctx = window.OfflineAudioContext || window.webkitOfflineAudioContext;
  const Decode = window.AudioContext || window.webkitAudioContext;
  if (!Ctx || !Decode) return null;

  // Decode at the file's own rate first; a context created just to decode is
  // closed straight away so it does not hold an audio device open.
  const decoder = new Decode();
  let decoded;
  try {
    decoded = await decoder.decodeAudioData(bytes);
  } finally {
    if (decoder.state !== "closed") decoder.close();
  }

  const frames = Math.max(1, Math.round((decoded.duration * SOUND_RATE)));
  const offline = new Ctx(1, frames, SOUND_RATE);
  const source = offline.createBufferSource();
  source.buffer = decoded;
  source.connect(offline.destination);
  source.start();
  const rendered = await offline.startRendering();
  return rendered.getChannelData(0).slice();
}

/** Fetch a dropped-in recording. Returns null when there isn't one. */
async function fetchOverride(soundId) {
  const available = await loadCatalogue();
  const filename = available[soundId];
  if (!filename) return null;

  try {
    const response = await fetch(`/static/audio/${filename}`, { cache: "force-cache" });
    if (!response.ok) return null;
    const samples = await decodeToTargetRate(await response.arrayBuffer());
    if (samples && samples.length > 0) return samples;
  } catch {
    // A corrupt, unsupported, or unreachable file must never break the game;
    // fall through to the synthesised target.
  }
  return null;
}

/**
 * Make sure a sound's target is ready, preferring a real recording.
 *
 * Await this before playing or scoring. It is safe to call repeatedly: the
 * result is cached for the rest of the session.
 */
export async function loadTarget(soundId) {
  if (resolved.has(soundId)) return resolved.get(soundId);
  if (pending.has(soundId)) return pending.get(soundId);

  const task = (async () => {
    const override = await fetchOverride(soundId);
    const samples = override ?? targetSamples(soundId);
    resolved.set(soundId, samples);
    pending.delete(soundId);
    return samples;
  })();

  pending.set(soundId, task);
  return task;
}

/** Which sounds have a dropped-in recording, for diagnostics and tests. */
export async function overriddenIds() {
  return Object.keys(await loadCatalogue());
}

/** Prime several sounds at once — a sequence stage, or a battle round. */
export async function loadTargets(soundIds) {
  return Promise.all(soundIds.map((id) => loadTarget(id)));
}

/**
 * The target for a sound, synchronously.
 *
 * Returns the dropped-in recording when loadTarget() has already resolved it,
 * and otherwise the synthesised target. Scoring paths call this, which is why
 * playback paths await loadTarget() first: once primed, the score is computed
 * against the very audio the player heard.
 */
export function targetFor(soundId) {
  if (resolved.has(soundId)) return resolved.get(soundId);
  const synthesised = targetSamples(soundId);
  resolved.set(soundId, synthesised);
  return synthesised;
}

/** True when this sound is playing from a dropped-in file rather than synthesis. */
export function isOverridden(soundId) {
  return resolved.has(soundId) && resolved.get(soundId) !== targetSamples(soundId);
}
