/**
 * Local progress. Single player is fully offline, so this is the only place
 * progress lives — there is no account and nothing is sent anywhere.
 */

const KEY = "voiceduel.progress.v1";

const emptyProgress = () => ({ best: {}, lastStage: null });

function read() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return emptyProgress();
    const parsed = JSON.parse(raw);
    return {
      best: parsed && typeof parsed.best === "object" && parsed.best ? parsed.best : {},
      lastStage: parsed ? parsed.lastStage ?? null : null,
    };
  } catch (e) {
    // Private mode, cleared storage, or a corrupted value: start fresh rather
    // than breaking the game.
    return emptyProgress();
  }
}

function write(progress) {
  try {
    localStorage.setItem(KEY, JSON.stringify(progress));
    return true;
  } catch (e) {
    return false;
  }
}

export const loadProgress = read;

/** Record a run. Keeps the best score only, and returns whether it improved. */
export function recordScore(stageId, score) {
  const progress = read();
  const previous = progress.best[stageId] ?? -1;
  const improved = score > previous;
  if (improved) progress.best[stageId] = score;
  progress.lastStage = stageId;
  write(progress);
  return improved;
}

export function bestScore(stageId) {
  return read().best[stageId] ?? null;
}

export function resetProgress() {
  write(emptyProgress());
}
