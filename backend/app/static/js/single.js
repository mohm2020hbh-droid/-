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
import {
  CHALLENGE, CHALLENGE_LABEL, STAGES, STAGES_BY_ID,
  isUnlocked, stageTarget, starsFor, unlockedCount,
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
        <span class="stage-title">${unlocked ? stage.emoji + " " + stage.title : "🔒 مقفل"}</span>
        <span class="stage-meta">${CHALLENGE_LABEL[stage.type]} · النجاح من ${stage.passMark}</span>
      </span>
      <span class="stage-score">${score === null ? "—" : score + "<br><small>" + stars(starsFor(stage, score)) + "</small>"}</span>
    `;
    row.onclick = () => openStage(stage.id, show);
    list.appendChild(row);
  }

  const done = STAGES.filter((s) => (best[s.id] ?? -1) >= s.passMark).length;
  $("stage-progress").textContent =
    `${done} من ${STAGES.length} مرحلة مكتملة · ${unlockedCount(best)} مفتوحة`;
}

/* ------------------------------------------------------------------ *
 * Playing one stage
 * ------------------------------------------------------------------ */

function openStage(stageId, show) {
  currentStage = STAGES_BY_ID[stageId];
  lastResult = null;

  $("sp-stage-number").textContent = `المرحلة ${currentStage.number}`;
  $("sp-challenge").textContent = CHALLENGE_LABEL[currentStage.type];
  $("sp-emoji").textContent = currentStage.emoji;
  $("sp-title").textContent = currentStage.title;
  $("sp-passmark").textContent = `النجاح من ${currentStage.passMark} فأعلى`;
  $("sp-best").textContent = bestScore(stageId) === null
    ? "لم تحاول هذه المرحلة بعد"
    : `أفضل نتيجة لك: ${bestScore(stageId)}`;

  if (currentStage.type === CHALLENGE.SEQUENCE) {
    $("sp-hint").textContent = "استمع للتسلسل كاملًا، ثم قلّد الأصوات بالترتيب نفسه.";
  } else if (currentStage.type === CHALLENGE.TIMED) {
    $("sp-hint").textContent = `لديك ${currentStage.recordSeconds} ثوانٍ فقط. استعد قبل الضغط.`;
  } else if (currentStage.type === CHALLENGE.DISTORTED) {
    $("sp-hint").textContent = "الصوت مشوّش عمدًا. ركّز على الشكل العام وليس التفاصيل.";
  } else {
    $("sp-hint").textContent = "استمع للصوت المطلوب ثم قلّده بصوتك.";
  }

  setPhase("ready");
  show("screen-sp-stage");
  playTarget();
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
  button.textContent = "🔊 يُشغَّل…";
  try {
    await playSamples(stageTarget(currentStage), SOUND_RATE);
  } finally {
    button.disabled = false;
    button.textContent = "🔊 استمع مرة أخرى";
  }
}

async function attempt(show) {
  if (!currentStage) return;

  if (!microphoneSupported()) {
    showStageNotice("المتصفح لا يتيح الميكروفون هنا. افتح الصفحة عبر 127.0.0.1 أو https.");
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
        ? "لم يُلتقط أي صوت. حاول مرة أخرى."
        : "تعذّر فتح الميكروفون: " + (error.name || error.message),
    );
    return;
  }

  setPhase("analysing");

  if (!clip.samples) {
    setPhase("ready");
    showStageNotice("تعذّر تحليل التسجيل في هذا المتصفح.");
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
        ? "التسجيل صامت أو قصير جدًا. اقترب من الميكروفون وحاول مجددًا."
        : "تعذّر تحليل الصوت.",
    );
    return;
  }

  lastResult = comparison;
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

function showResult(comparison, improved, show) {
  const stage = currentStage;
  const passed = comparison.score >= stage.passMark;
  const earned = starsFor(stage, comparison.score);

  $("sp-result-score").textContent = comparison.score;
  $("sp-result-score").classList.remove("pop");
  void $("sp-result-score").offsetWidth;
  $("sp-result-score").classList.add("pop");
  $("sp-result-stars").textContent = stars(earned);
  $("sp-result-title").textContent = passed ? "نجحت في المرحلة!" : "لم تبلغ حد النجاح";
  $("sp-result-title").className = passed ? "pass" : "fail";
  $("sp-result-note").textContent = improved ? "أفضل نتيجة جديدة! 🎉" : `النجاح من ${stage.passMark}`;

  const parts = comparison.parts;
  const rows = [
    ["نبرة الصوت (الطيف)", parts.timbre],
    ["حدة الطبقة", parts.pitch],
    ["تموّج الصوت", parts.dynamics],
    ["نقاء/خشونة", parts.voicing],
    ["سطوع الصوت", parts.brightness],
  ].filter(([, value]) => value !== null && value !== undefined);

  $("sp-breakdown").innerHTML = rows.map(([label, value]) => `
    <div class="bar-row">
      <span class="bar-label">${label}</span>
      <span class="bar-track"><i style="width:${value}%"></i></span>
      <span class="bar-value">${value}</span>
    </div>`).join("");

  const next = STAGES[stage.number]; // stage.number is 1-based
  $("btn-sp-next").style.display = passed && next ? "block" : "none";
  show("screen-sp-result");
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
