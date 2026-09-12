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
import { SOUND_RATE, soundName } from "./sounds.js";
import { loadTarget, targetFor } from "./targets.js";
import { microphoneSupported, playSamples, recordClip } from "./audio.js";
import { serverUrl } from "./protocol.js";
import { getLanguage, t } from "./i18n.js";

const $ = (id) => document.getElementById(id);
const RESULT_DWELL_MS = 2600;

const state = {
  socket: null, playerId: null, opponentId: null, roomCode: null,
  round: null, totalWins: {}, pendingRound: null, pendingOver: null,
  lastResult: null, lastOver: null,
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
    setConnection(t("conn.connecting"));
    const socket = new WebSocket(serverUrl());
    state.socket = socket;

    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.type === "connected") {
        state.playerId = message.payload.player_id;
        setConnection(t("conn.connected"), "ok");
        resolve();
      }
      onEvent(message.type, message.payload);
    };
    socket.onerror = () => { setConnection(t("conn.failed"), "bad"); reject(new Error("socket")); };
    socket.onclose = () => {
      setConnection(t("conn.lost"), "bad");
      state.socket = null;
      if (document.querySelector(".screen.active")?.id?.startsWith("screen-battle")) {
        leave(t("conn.lost.server"));
      }
    };
  });
}

function send(type, payload) {
  if (!state.socket || state.socket.readyState !== WebSocket.OPEN) {
    notice(t("conn.none"));
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
      leave(t("error.opponent.left.battle"));
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
    invalid_code: t("error.invalid_code"),
    room_full: t("error.room_full"),
    invalid_score: t("error.invalid_score.battle"),
    stale_round: t("error.stale_round"),
    wrong_phase: t("error.stale_round"),
  };
  state.submitting = false;
  notice(messages[reason] || t("error.generic"));
}

function applyPending() {
  clearTimeout(state.dwellTimer);

  if (state.pendingOver) {
    const over = state.pendingOver;
    state.lastOver = over;
    state.pendingOver = null;
    state.pendingRound = null;
    state.totalWins = over.total_wins;
    const draw = over.winner_id === null;
    const won = over.winner_id === state.playerId;
    $("battle-over-emoji").textContent = draw ? "🤝" : won ? "🏆" : "😮‍💨";
    $("battle-over-title").textContent = draw ? t("battle.over.draw") : won ? t("battle.over.won") : t("battle.over.lost");
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

  $("battle-target-emoji").textContent = round.sound_emoji;
  renderRoundText();
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
  state.lastResult = payload;

  const myScore = payload.scores[state.playerId] ?? 0;
  const theirScore = payload.scores[state.opponentId] ?? 0;
  const won = payload.round_winner_id === state.playerId;
  const draw = payload.round_winner_id === null;

  $("battle-result-round").textContent = t("result.round", { number: payload.round_number });
  for (const [id, value] of [["battle-result-mine", myScore], ["battle-result-theirs", theirScore]]) {
    const el = $(id);
    el.textContent = value;
    el.classList.remove("pop");
    void el.offsetWidth;
    el.classList.add("pop");
  }
  $("battle-result-banner").textContent = draw ? t("battle.round.draw") : won ? t("battle.round.won") : t("battle.round.lost");
  $("battle-result-banner").className = draw ? "" : won ? "pass" : "fail";
  $("battle-result-wins-mine").textContent = state.totalWins[state.playerId] || 0;
  $("battle-result-wins-theirs").textContent = state.totalWins[state.opponentId] || 0;

  state.show("screen-battle-result");
  state.dwellTimer = setTimeout(applyPending, RESULT_DWELL_MS);
}

/**
 * The round's own text: the round counter and the target's name in the active
 * language, resolved from the stable `sound_id` so both players are looking at
 * the same sound whichever language each of them has chosen.
 */
function renderRoundText() {
  const round = state.round;
  if (!round) return;
  const language = getLanguage();
  const fromServer = language === "en"
    ? (round.sound_name_en ?? round.sound_name)
    : round.sound_name;
  $("battle-round-label").textContent =
    t("battle.round", { number: round.round_number, total: round.total_rounds });
  $("battle-target-name").textContent = soundName(round.sound_id, language, fromServer);
}

/** Re-paint the live battle text after a language change. State is untouched. */
export function redrawBattle() {
  renderRoundText();
  if (state.lastResult) {
    const payload = state.lastResult;
    const won = payload.round_winner_id === state.playerId;
    const draw = payload.round_winner_id === null;
    $("battle-result-round").textContent = t("result.round", { number: payload.round_number });
    $("battle-result-banner").textContent =
      draw ? t("battle.round.draw") : won ? t("battle.round.won") : t("battle.round.lost");
  }
  if (state.lastOver) {
    const draw = state.lastOver.winner_id === null;
    const won = state.lastOver.winner_id === state.playerId;
    $("battle-over-title").textContent =
      draw ? t("battle.over.draw") : won ? t("battle.over.won") : t("battle.over.lost");
  }
  if (!state.socket) setConnection(t("conn.offline"));
}

/* ---------------------------- the round itself ---------------------------- */

function playTarget() {
  const round = state.round;
  if (!round) return;
  $("battle-listen-status").textContent = t("rate.playing");
  // Both players must hear the same audio the score is computed from, so the
  // target is resolved (dropped-in recording, else synthesis) before playing.
  loadTarget(round.sound_id)
    .then((samples) => playSamples(samples, SOUND_RATE))
    .then(() => { $("battle-listen-status").textContent = ""; });
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
    notice(t("error.record.failed", { detail: error.name || error.message }));
    return;
  }

  $("battle-phase-recording").style.display = "none";
  $("battle-phase-waiting").style.display = "block";

  if (!clip.samples) {
    submitScore(round.round_number, 0);
    return;
  }

  const comparison = compareAudio(clip.samples, clip.sampleRate, targetFor(round.sound_id), SOUND_RATE);
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
    catch (e) { notice(t("conn.unreachable")); }
  };

  $("btn-battle-join").onclick = async () => {
    clearNotices();
    const code = $("input-battle-code").value.trim();
    if (code.length !== 4) { notice(t("error.code.length")); return; }
    try { await connect(); send("join_room", { code }); }
    catch (e) { notice(t("conn.unreachable")); }
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
      t("error.mic.address");
  }
}

/** Used by the main menu when backing out of Voice Battle. */
export function leaveBattle() {
  if (state.roomCode) send("leave_room");
  leave(null);
}
