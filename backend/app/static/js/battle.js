/**
 * Voice Battle — the second online mode.
 *
 * Two players share a room exactly like the original Duel game, but instead
 * of one performing while the other rates by ear, *both* imitate the same
 * target at once and each device scores its own recording with the same
 * acoustic engine single player uses (`dsp.js`), against the identical
 * deterministic target both devices render locally from the `sound_id` the
 * server names. The server only compares the two numbers and picks a winner
 * — there is no server-side audio pipeline, and no human judge.
 *
 * That "each client scores itself" design mirrors the trust model the
 * original Duel mode already has (a human-submitted `rating_submitted` score
 * is likewise taken at the client's word) rather than adding a new one for
 * this feature.
 */

import { compareAudio } from "./dsp.js";
import { SOUND_RATE, targetSamples } from "./sounds.js";
import { microphoneSupported, playSamples, recordClip } from "./audio.js";
import { serverUrl } from "./protocol.js";

const $ = (id) => document.getElementById(id);
const RESULT_DWELL_MS = 2600;

const state = {
  socket: null, playerId: null, opponentId: null, roomCode: null,
  round: null, totalWins: {}, pendingRound: null, pendingOver: null,
  dwellTimer: null, submitting: false, show: null,
};
const recordControl = {};

function notice(text) {
  const active = document.querySelector(".screen.active");
  const box = active?.querySelector(".notice");
  if (box) { box.textContent = text; box.classList.add("show"); }
}

function clearNotices() {
  document.querySelectorAll(".notice").forEach((n) => n.classList.remove("show"));
}

function setConnection(text, cls) {
  const el = $("battle-conn-status");
  if (el) { el.textContent = text; el.className = "status" + (cls ? " " + cls : ""); }
}

/* ---------------------------- socket ---------------------------- */

function connect() {
  return new Promise((resolve, reject) => {
    if (state.socket && state.socket.readyState === WebSocket.OPEN) { resolve(); return; }
    setConnection("جارٍ الاتصال…");
    const socket = new WebSocket(serverUrl());
    state.socket = socket;

    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.type === "connected") {
        state.playerId = message.payload.player_id;
        setConnection("متصل", "ok");
        resolve();
      }
      onEvent(message.type, message.payload);
    };
    socket.onerror = () => { setConnection("تعذّر الاتصال", "bad"); reject(new Error("socket")); };
    socket.onclose = () => {
      setConnection("انقطع الاتصال", "bad");
      state.socket = null;
      if (document.querySelector(".screen.active")?.id?.startsWith("screen-battle")) {
        leave("انقطع الاتصال بالخادم");
      }
    };
  });
}

function send(type, payload) {
  if (!state.socket || state.socket.readyState !== WebSocket.OPEN) {
    notice("لا يوجد اتصال بالخادم");
    return false;
  }
  state.socket.send(JSON.stringify({ type, payload: payload || {} }));
  return true;
}

/* ---------------------------- events ---------------------------- */

function onEvent(type, payload) {
  switch (type) {
    case "room_created":
      state.roomCode = payload.code;
      $("battle-room-code").textContent = payload.code;
      clearNotices();
      state.show("screen-battle-waiting");
      break;

    case "players_ready":
      state.opponentId = [payload.player_a_id, payload.player_b_id]
        .find((id) => id !== state.playerId) || null;
      state.totalWins = { [payload.player_a_id]: 0, [payload.player_b_id]: 0 };
      break;

    case "battle_round_start":
      state.pendingRound = payload;
      if (document.querySelector(".screen.active")?.id !== "screen-battle-result") applyPending();
      break;

    case "battle_round_result":
      onRoundResult(payload);
      break;

    case "battle_over":
      state.pendingOver = payload;
      if (document.querySelector(".screen.active")?.id !== "screen-battle-result") applyPending();
      break;

    case "opponent_disconnected":
      leave("انسحب الخصم من المعركة");
      break;

    case "error":
      onServerError(payload.reason);
      break;

    default:
      break;
  }
}

function onServerError(reason) {
  const messages = {
    invalid_code: "الكود غير صحيح، تأكد منه وحاول مجددًا",
    room_full: "الغرفة ممتلئة",
    invalid_score: "قيمة النتيجة غير صالحة",
    stale_round: "انتهت هذه الجولة بالفعل",
    wrong_phase: "انتهت هذه الجولة بالفعل",
  };
  state.submitting = false;
  notice(messages[reason] || "تعذّر تنفيذ الطلب");
}

function applyPending() {
  clearTimeout(state.dwellTimer);

  if (state.pendingOver) {
    const over = state.pendingOver;
    state.pendingOver = null;
    state.pendingRound = null;
    state.totalWins = over.total_wins;
    const draw = over.winner_id === null;
    const won = over.winner_id === state.playerId;
    $("battle-over-emoji").textContent = draw ? "🤝" : won ? "🏆" : "😮‍💨";
    $("battle-over-title").textContent = draw ? "تعادل في المعركة!" : won ? "فزت بالمعركة!" : "فاز خصمك بالمعركة";
    $("battle-over-mine").textContent = state.totalWins[state.playerId] || 0;
    $("battle-over-theirs").textContent = state.totalWins[state.opponentId] || 0;
    state.show("screen-battle-over");
    return;
  }

  if (!state.pendingRound) return;
  const round = state.pendingRound;
  state.pendingRound = null;
  state.round = round;
  state.submitting = false;
  clearNotices();

  $("battle-round-label").textContent = `الجولة ${round.round_number} من ${round.total_rounds}`;
  $("battle-target-emoji").textContent = round.sound_emoji;
  $("battle-target-name").textContent = round.sound_name;
  $("battle-listen-status").textContent = "";
  $("battle-phase-ready").style.display = "block";
  $("battle-phase-countdown").style.display = "none";
  $("battle-phase-recording").style.display = "none";
  $("battle-phase-waiting").style.display = "none";
  $("btn-battle-start").disabled = false;

  state.show("screen-battle-round");
  playTarget();
}

function onRoundResult(payload) {
  clearTimeout(state.dwellTimer);
  state.totalWins = payload.total_wins;

  const myScore = payload.scores[state.playerId] ?? 0;
  const theirScore = payload.scores[state.opponentId] ?? 0;
  const won = payload.round_winner_id === state.playerId;
  const draw = payload.round_winner_id === null;

  $("battle-result-round").textContent = `نتيجة الجولة ${payload.round_number}`;
  for (const [id, value] of [["battle-result-mine", myScore], ["battle-result-theirs", theirScore]]) {
    const el = $(id);
    el.textContent = value;
    el.classList.remove("pop");
    void el.offsetWidth;
    el.classList.add("pop");
  }
  $("battle-result-banner").textContent = draw ? "تعادل 🤝" : won ? "فزت بالجولة! 🎉" : "خسرت الجولة";
  $("battle-result-banner").className = draw ? "" : won ? "pass" : "fail";
  $("battle-result-wins-mine").textContent = state.totalWins[state.playerId] || 0;
  $("battle-result-wins-theirs").textContent = state.totalWins[state.opponentId] || 0;

  state.show("screen-battle-result");
  state.dwellTimer = setTimeout(applyPending, RESULT_DWELL_MS);
}

/* ---------------------------- the round itself ---------------------------- */

function playTarget() {
  const round = state.round;
  if (!round) return;
  $("battle-listen-status").textContent = "🔊 جارٍ التشغيل…";
  playSamples(targetSamples(round.sound_id), SOUND_RATE).then(() => {
    $("battle-listen-status").textContent = "";
  });
}

async function startAttempt() {
  const round = state.round;
  if (!round || state.submitting) return;

  $("btn-battle-start").disabled = true;
  $("battle-phase-ready").style.display = "none";
  $("battle-phase-countdown").style.display = "block";

  for (const label of ["3", "2", "1"]) {
    $("battle-countdown-number").textContent = label;
    await delay(700);
  }

  $("battle-phase-countdown").style.display = "none";
  $("battle-phase-recording").style.display = "block";
  $("battle-attempt-countdown").textContent = round.attempt_seconds;

  let clip;
  try {
    clip = await recordClip(
      round.attempt_seconds,
      (remaining) => { $("battle-attempt-countdown").textContent = Math.max(0, remaining); },
      recordControl,
    );
  } catch (error) {
    // No usable recording (denied mic, hardware failure): report 0 rather
    // than leaving the opponent to wait out the server's timeout.
    submitScore(round.round_number, 0);
    notice("تعذّر التسجيل: " + (error.name || error.message));
    return;
  }

  $("battle-phase-recording").style.display = "none";
  $("battle-phase-waiting").style.display = "block";

  if (!clip.samples) {
    submitScore(round.round_number, 0);
    return;
  }

  const comparison = compareAudio(clip.samples, clip.sampleRate, targetSamples(round.sound_id), SOUND_RATE);
  submitScore(round.round_number, comparison.ok ? comparison.score : 0);
}

function submitScore(roundNumber, score) {
  state.submitting = true;
  send("battle_attempt_submitted", { round_number: roundNumber, score });
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/* ---------------------------- lifecycle ---------------------------- */

function leave(message) {
  clearTimeout(state.dwellTimer);
  recordControl.stopEarly?.();
  state.roomCode = null; state.round = null;
  state.pendingRound = null; state.pendingOver = null;
  clearNotices();
  state.show("screen-battle-home");
  if (message) notice(message);
}

export function initBattle(show) {
  state.show = show;

  $("btn-battle-create").onclick = async () => {
    clearNotices();
    try { await connect(); send("create_room", { mode: "battle" }); }
    catch (e) { notice("تعذّر الاتصال بالخادم"); }
  };

  $("btn-battle-join").onclick = async () => {
    clearNotices();
    const code = $("input-battle-code").value.trim();
    if (code.length !== 4) { notice("أدخل كودًا مكوّنًا من ٤ أرقام"); return; }
    try { await connect(); send("join_room", { code }); }
    catch (e) { notice("تعذّر الاتصال بالخادم"); }
  };

  $("input-battle-code").oninput = (event) => {
    event.target.value = event.target.value.replace(/\D/g, "").slice(0, 4);
    $("btn-battle-join").disabled = event.target.value.length !== 4;
  };

  $("btn-battle-listen").onclick = playTarget;
  $("btn-battle-start").onclick = startAttempt;

  document.querySelectorAll(".btn-leave-battle").forEach((button) => {
    button.onclick = () => { if (state.roomCode) send("leave_room"); leave(null); };
  });

  if (!microphoneSupported()) {
    $("battle-mic-hint").textContent =
      "الميكروفون غير متاح على هذا العنوان — افتح الصفحة عبر 127.0.0.1 أو https.";
  }
}

/** Used by the main menu when backing out of Voice Battle. */
export function leaveBattle() {
  if (state.roomCode) send("leave_room");
  leave(null);
}
