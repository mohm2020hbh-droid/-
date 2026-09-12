/**
 * Single player: hear a target, imitate it, get an acoustic similarity score.
 *
 * Everything here runs on the device — the target is synthesised locally, the
 * recording never leaves the browser, and the score comes from dsp.js. No
 * server, no network, no transcription.
 */

import { compareAudio } from "./dsp.js";
import { SOUND_RATE } from "./sounds.js";
import { playSamples, recordClip, microphoneSupported } from "./audio.js";
import { loadTargets } from "./targets.js";
import { t } from "./i18n.js";
import {
  CHALLENGE, STAGES, STAGES_BY_ID, challengeLabel,
  isUnlocked, stageTarget, stageTitle, starsFor, unlockedCount,
} from "./stages.js";
import { bestScore, loadProgress, recordScore } from "./storage.js";

const $ = (id) => document.getElementById(id);
const stars = (count) => "⭐".repeat(count) + "☆".repeat(3 - count);

let currentStage = null;
let lastResult = null;
const recordControl = {};

/* ------------------------------------------------------------------ *
 * Stage list
 * ------------------------------------------------------------------ */

export function renderStageList(show) {
  const { best } = loadProgress();
  const list = $("stage-list");
  list.innerHTML = "";

  for (const stage of STAGES) {
    const unlocked = isUnlocked(stage, best);
    const score = best[stage.id] ?? null;

    const row = document.createElement("button");
    row.className = "stage" + (unlocked ? "" : " locked");
    row.disabled = !unlocked;
    row.innerHTML = `
      <span class="stage-number">${stage.number}</span>
      <span class="stage-main">
        <span class="stage-title">${unlocked ? stage.emoji + " " + stageTitle(stage) : t("stages.locked")}</span>
        <span class="stage-meta">${t("stages.meta", { challenge: challengeLabel(stage.type), pass: stage.passMark })}</span>
      </span>
      <span class="stage-score">${score === null ? "—" : score + "<br><small>" + stars(starsFor(stage, score)) + "</small>"}</span>
    `;
    row.onclick = () => openStage(stage.id, show);
    list.appendChild(row);
  }

  const done = STAGES.filter((s) => (best[s.id] ?? -1) >= s.passMark).length;
  $("stage-progress").textContent =
    t("stages.summary", { done, total: STAGES.length, unlocked: unlockedCount(best) });
}

/* ------------------------------------------------------------------ *
 * Playing one stage
 * ------------------------------------------------------------------ */

/** Paint the stage screen's text. Split out so a language switch can redraw it. */
function renderStageHeader() {
  const stage = currentStage;
  if (!stage) return;

  $("sp-stage-number").textContent = t("sp.stage.number", { number: stage.number });
  $("sp-challenge").textContent = challengeLabel(stage.type);
  $("sp-emoji").textContent = stage.emoji;
  $("sp-title").textContent = stageTitle(stage);
  $("sp-passmark").textContent = t("sp.passmark", { pass: stage.passMark });

  const best = bestScore(stage.id);
  $("sp-best").textContent = best === null
    ? t("sp.best.none")
    : t("sp.best.some", { score: best });

  $("sp-hint").textContent = {
    [CHALLENGE.SEQUENCE]: t("sp.hint.sequence"),
    [CHALLENGE.TIMED]: t("sp.hint.timed", { seconds: stage.recordSeconds }),
    [CHALLENGE.DISTORTED]: t("sp.hint.distorted"),
  }[stage.type] ?? t("sp.hint.single");
}

function openStage(stageId, show) {
  currentStage = STAGES_BY_ID[stageId];
  lastResult = null;

  renderStageHeader();

  setPhase("ready");
  show("screen-sp-stage");
  // Resolve this stage's audio before the first playback, so that if a real
  // recording has been dropped in for one of these sounds, the player hears
  // it and is scored against it rather than against the synthesised version.
  loadTargets(currentStage.soundIds).then(() => {
    if (currentStage && currentStage.id === stageId) playTarget();
  });
}

function setPhase(phase) {
  for (const id of ["sp-ready", "sp-recording", "sp-analysing"]) {
    $(id).style.display = "none";
  }
  $({ ready: "sp-ready", recording: "sp-recording", analysing: "sp-analysing" }[phase]).style.display = "block";
}

async function playTarget() {
  if (!currentStage) return;
  const button = $("btn-hear");
  button.disabled = true;
  button.textContent = t("sp.hear.playing");
  try {
    await playSamples(stageTarget(currentStage), SOUND_RATE);
  } finally {
    button.disabled = false;
    button.textContent = t("sp.hear.again");
  }
}

async function attempt(show) {
  if (!currentStage) return;

  if (!microphoneSupported()) {
    showStageNotice(t("error.mic.unsupported"));
    return;
  }

  setPhase("recording");
  $("sp-countdown").textContent = currentStage.recordSeconds;

  let clip;
  try {
    clip = await recordClip(
      currentStage.recordSeconds,
      (remaining) => { $("sp-countdown").textContent = remaining; },
      recordControl,
    );
  } catch (error) {
    setPhase("ready");
    showStageNotice(
      error.message === "empty"
        ? t("error.record.empty")
        : t("error.record.failed", { detail: error.name || error.message }),
    );
    return;
  }

  setPhase("analysing");

  if (!clip.samples) {
    setPhase("ready");
    showStageNotice(t("error.analyse.unsupported"));
    return;
  }

  // Yield once so the "analysing" state actually paints before the DSP runs.
  await new Promise((resolve) => setTimeout(resolve, 30));

  const comparison = compareAudio(
    clip.samples, clip.sampleRate, stageTarget(currentStage), SOUND_RATE,
  );

  if (!comparison.ok) {
    setPhase("ready");
    showStageNotice(
      comparison.reason === "no_audio"
        ? t("error.analyse.silent")
        : t("error.analyse.failed"),
    );
    return;
  }

  const improved = recordScore(currentStage.id, comparison.score);
  showResult(comparison, improved, show);
}

function showStageNotice(message) {
  const box = $("sp-notice");
  box.textContent = message;
  box.classList.add("show");
}

/* ------------------------------------------------------------------ *
 * Result
 * ------------------------------------------------------------------ */

/**
 * The comparison breakdown, by translation key rather than by wording, so the
 * same five acoustic measures are named in whichever language is active.
 */
const BREAKDOWN_ROWS = [
  ["score.timbre", "timbre"],
  ["score.pitch", "pitch"],
  ["score.dynamics", "dynamics"],
  ["score.voicing", "voicing"],
  ["score.brightness", "brightness"],
];

/** Paint the result screen from the last comparison. Redrawable. */
function renderResult() {
  const stage = currentStage;
  const result = lastResult;
  if (!stage || !result) return;

  const passed = result.score >= stage.passMark;

  $("sp-result-score").textContent = result.score;
  $("sp-result-stars").textContent = stars(starsFor(stage, result.score));
  $("sp-result-title").textContent = t(passed ? "sp.result.passed" : "sp.result.failed");
  $("sp-result-title").className = passed ? "pass" : "fail";
  $("sp-result-note").textContent = result.improved
    ? t("sp.result.improved")
    : t("sp.result.passmark", { pass: stage.passMark });

  $("sp-breakdown").innerHTML = BREAKDOWN_ROWS
    .map(([key, part]) => [t(key), result.parts[part]])
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([label, value]) => `
    <div class="bar-row">
      <span class="bar-label">${label}</span>
      <span class="bar-track"><i style="width:${value}%"></i></span>
      <span class="bar-value">${value}</span>
    </div>`).join("");

  const next = STAGES[stage.number]; // stage.number is 1-based
  $("btn-sp-next").style.display = passed && next ? "block" : "none";
}

function showResult(comparison, improved, show) {
  lastResult = { ...comparison, improved };
  renderResult();

  $("sp-result-score").classList.remove("pop");
  void $("sp-result-score").offsetWidth;
  $("sp-result-score").classList.add("pop");

  show("screen-sp-result");
}

/** Re-paint whatever single-player text is on screen after a language change. */
export function redrawSinglePlayer() {
  renderStageHeader();
  renderResult();
}

/* ------------------------------------------------------------------ *
 * Wiring
 * ------------------------------------------------------------------ */

export function initSinglePlayer(show) {
  $("btn-hear").onclick = playTarget;
  $("btn-attempt").onclick = () => attempt(show);
  $("btn-stop-early").onclick = () => recordControl.stopEarly?.();

  $("btn-sp-retry").onclick = () => openStage(currentStage.id, show);
  $("btn-sp-next").onclick = () => {
    const next = STAGES[currentStage.number];
    if (next) openStage(next.id, show);
  };
  $("btn-sp-list").onclick = () => { renderStageList(show); show("screen-sp-stages"); };
  $("btn-sp-back").onclick = () => { renderStageList(show); show("screen-sp-stages"); };

  $("btn-sp-hear-again").onclick = playTarget;
}
