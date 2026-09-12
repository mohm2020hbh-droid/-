/**
 * Single-player progression.
 *
 * Twenty stages that ramp in difficulty and cycle through the four challenge
 * types: a plain imitation, a timed one, a sequence of sounds, and a distorted
 * target that is harder to pick apart.
 */

import { SOUNDS_BY_ID, SOUND_RATE, distort, soundName } from "./sounds.js";
import { targetFor } from "./targets.js";
import { getLanguage, t } from "./i18n.js";

export const CHALLENGE = {
  SINGLE: "single",
  TIMED: "timed",
  SEQUENCE: "sequence",
  DISTORTED: "distorted",
};

/**
 * The name of a challenge type in the active language.
 *
 * Resolved on every call rather than frozen into a table at load time, so a
 * language switch mid-game relabels the stages immediately.
 */
export function challengeLabel(type) {
  return t(`challenge.${type}`);
}

const SEQUENCE_GAP_SECONDS = 0.35;

/** [type, sound ids, pass mark, seconds to record] */
const PLAN = [
  [CHALLENGE.SINGLE, ["ambulance"], 35, 6],
  [CHALLENGE.SINGLE, ["bee"], 38, 6],
  [CHALLENGE.SINGLE, ["cat"], 40, 6],
  [CHALLENGE.SINGLE, ["snake"], 40, 6],
  [CHALLENGE.SINGLE, ["cow"], 42, 6],
  [CHALLENGE.TIMED, ["dog"], 42, 3],
  [CHALLENGE.SINGLE, ["rooster"], 44, 6],
  [CHALLENGE.TIMED, ["bird"], 44, 3],
  [CHALLENGE.SINGLE, ["train"], 45, 6],
  [CHALLENGE.SEQUENCE, ["cat", "dog"], 45, 7],
  [CHALLENGE.SINGLE, ["lion"], 46, 6],
  [CHALLENGE.TIMED, ["alarm"], 46, 3],
  [CHALLENGE.SEQUENCE, ["bee", "snake"], 47, 7],
  [CHALLENGE.DISTORTED, ["police"], 48, 6],
  [CHALLENGE.SINGLE, ["elephant"], 48, 6],
  [CHALLENGE.SEQUENCE, ["frog", "owl"], 50, 7],
  [CHALLENGE.DISTORTED, ["motorcycle"], 50, 6],
  [CHALLENGE.TIMED, ["cricket"], 52, 3],
  [CHALLENGE.SEQUENCE, ["rooster", "cow", "sheep"], 54, 9],
  [CHALLENGE.DISTORTED, ["helicopter"], 55, 6],
];

export const STAGES = PLAN.map(([type, soundIds, passMark, recordSeconds], index) => ({
  id: `stage-${index + 1}`,
  number: index + 1,
  type,
  soundIds,
  passMark,
  recordSeconds,
  sounds: soundIds.map((id) => SOUNDS_BY_ID[id]),
  emoji: soundIds.map((id) => SOUNDS_BY_ID[id].emoji).join(" "),
}));

export const STAGES_BY_ID = Object.fromEntries(STAGES.map((s) => [s.id, s]));

/**
 * A stage's display title: its sounds' names, in the active language.
 *
 * The stage is identified by its sound ids, never by their names, so the same
 * stage keeps the same identity, target audio and saved score whichever
 * language it is shown in.
 */
export function stageTitle(stage) {
  const language = getLanguage();
  return stage.soundIds
    .map((id) => soundName(id, language))
    .join(t("challenge.sequence.join"));
}

/** Concatenate the stage's sounds, applying the distorted treatment if needed. */
export function stageTarget(stage) {
  const parts = stage.soundIds.map((id) => targetFor(id));

  let combined;
  if (parts.length === 1) {
    combined = parts[0];
  } else {
    const gap = Math.round(SEQUENCE_GAP_SECONDS * SOUND_RATE);
    const total = parts.reduce((sum, p) => sum + p.length, 0) + gap * (parts.length - 1);
    combined = new Float32Array(total);
    let offset = 0;
    for (const part of parts) {
      combined.set(part, offset);
      offset += part.length + gap;
    }
  }

  return stage.type === CHALLENGE.DISTORTED ? distort(combined) : combined;
}

/** Stars are the quick read on a run; the pass mark is the gate. */
export function starsFor(stage, score) {
  if (score >= 85) return 3;
  if (score >= 70) return 2;
  if (score >= stage.passMark) return 1;
  return 0;
}

/** A stage opens once the one before it has been passed. */
export function isUnlocked(stage, best) {
  if (stage.number === 1) return true;
  const previous = STAGES[stage.number - 2];
  return (best[previous.id] ?? -1) >= previous.passMark;
}

export function unlockedCount(best) {
  return STAGES.filter((stage) => isUnlocked(stage, best)).length;
}
