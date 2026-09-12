/**
 * Online multiplayer — unchanged in behaviour from the original client, just
 * lifted out of the page into a module.
 *
 * Two players share a 4-digit room code, take turns performing, and the *other
 * player* scores the imitation by hand with a slider. That human judgement is
 * deliberate: online mode is a party game between two people, while single
 * player is the one scored acoustically by the engine in dsp.js.
 */

import { decodeAudioFrame, encodeAudioFrame, serverUrl } from "./protocol.js";
import { encodeWav, microphoneSupported, playEncoded, recordClip } from "./audio.js";
import { getLanguage, t } from "./i18n.js";
import { soundName } from "./sounds.js";

const $ = (id) => document.getElementById(id);
const RESULT_DWELL_MS = 3000;

const state = {
  socket: null, playerId: null, opponentId: null, roomCode: null,
  round: null, scores: {}, incoming: null, incomingMime: "audio/webm",
  pendingRound: null, pendingGameOver: null,
  lastResult: null, lastGameOver: null,
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
  const el = $("conn-status");
  if (el) { el.textContent = text; el.className = "status" + (cls ? " " + cls : ""); }
}

/* ---------------------------- socket ---------------------------- */

function connect() {
  return new Promise((resolve, reject) => {
    if (state.socket && state.socket.readyState === WebSocket.OPEN) { resolve(); return; }
    setConnection(t("conn.connecting"));
    const socket = new WebSocket(serverUrl());
    socket.binaryType = "arraybuffer";
    state.socket = socket;

    socket.onmessage = (event) => {
      if (typeof event.data === "string") {
        const message = JSON.parse(event.data);
        if (message.type === "connected") {
          state.playerId = message.payload.player_id;
          setConnection(t("conn.connected"), "ok");
          resolve();
        }
        onEvent(message.type, message.payload);
      } else {
        const frame = decodeAudioFrame(event.data);
        if (frame) onAudio(frame);
      }
    };
    socket.onerror = () => { setConnection(t("conn.failed"), "bad"); reject(new Error("socket")); };
    socket.onclose = () => {
      setConnection(t("conn.lost"), "bad");
      state.socket = null;
      if (document.querySelector(".screen.active")?.id?.startsWith("screen-online")) {
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
      $("room-code").textContent = payload.code;
      clearNotices();
      state.show("screen-online-waiting");
      break;

    case "players_ready":
      state.opponentId = [payload.player_a_id, payload.player_b_id]
        .find((id) => id !== state.playerId) || null;
      state.scores = { [payload.player_a_id]: 0, [payload.player_b_id]: 0 };
      break;

    case "round_start":
      state.pendingRound = payload;
      if (document.querySelector(".screen.active")?.id !== "screen-online-result") applyPending();
      break;

    case "audio_ready":
      state.show("screen-online-rating");
      break;

    case "round_result":
      onRoundResult(payload);
      break;

    case "game_over":
      state.pendingGameOver = payload;
      if (document.querySelector(".screen.active")?.id !== "screen-online-result") applyPending();
      break;

    case "opponent_disconnected":
      leave(t("error.opponent.left"));
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
    audio_too_large: t("error.audio_too_large"),
    not_your_turn: t("error.not_your_turn"),
    stale_round: t("error.stale_round"),
    wrong_phase: t("error.stale_round"),
    invalid_score: t("error.invalid_score"),
  };
  state.submitting = false;
  $("btn-submit").disabled = false;
  notice(messages[reason] || t("error.generic"));
}

function applyPending() {
  clearTimeout(state.dwellTimer);

  if (state.pendingGameOver) {
    const over = state.pendingGameOver;
    state.lastGameOver = over;
    state.pendingGameOver = null;
    state.pendingRound = null;
    state.scores = over.final_scores;
    const draw = over.winner_id === null;
    const won = over.winner_id === state.playerId;
    $("over-emoji").textContent = draw ? "🤝" : won ? "🏆" : "😮‍💨";
    $("over-title").textContent = draw ? t("over.draw") : won ? t("over.won") : t("over.lost");
    $("over-mine").textContent = state.scores[state.playerId] || 0;
    $("over-theirs").textContent = state.scores[state.opponentId] || 0;
    state.show("screen-online-over");
    return;
  }

  if (!state.pendingRound) return;

  const round = state.pendingRound;
  state.pendingRound = null;
  state.round = round;
  clearNotices();

  $("play-round").textContent = t("play.round", { number: round.round_number, total: round.total_rounds });
  $("play-emoji").textContent = round.sound_emoji;
  $("rate-emoji").textContent = round.sound_emoji;
  renderRoundSoundName();

  const performing = round.performer_id === state.playerId;
  $("play-performer").style.display = performing ? "block" : "none";
  $("play-rater").style.display = performing ? "none" : "block";
  state.show("screen-online-play");

  if (performing) perform(round);
}

function onRoundResult(payload) {
  clearTimeout(state.dwellTimer);
  state.scores = payload.total_scores;
  state.lastResult = payload;
  const mine = payload.performer_id === state.playerId;
  $("result-round").textContent = t("result.round", { number: payload.round_number });
  $("result-score").textContent = payload.score;
  $("result-score").classList.remove("pop");
  void $("result-score").offsetWidth;
  $("result-score").classList.add("pop");
  $("result-caption").textContent = payload.timed_out
    ? (mine ? t("result.timeout.mine") : t("result.timeout.theirs"))
    : (mine ? t("result.yours") : t("result.given"));
  $("result-mine").textContent = state.scores[state.playerId] || 0;
  $("result-theirs").textContent = state.scores[state.opponentId] || 0;
  state.show("screen-online-result");
  state.dwellTimer = setTimeout(applyPending, RESULT_DWELL_MS);
}

/* ---------------------------- performing ---------------------------- */

async function perform(round) {
  $("play-rec").textContent = t("play.recording");

  if (!microphoneSupported()) {
    notice(t("play.mic.fallback"));
    setTimeout(() => sendFallbackTone(round), round.countdown_seconds * 1000);
    return;
  }

  try {
    const clip = await recordClip(
      round.countdown_seconds,
      (remaining) => {
        $("play-countdown").textContent = remaining;
        $("play-bar").style.width =
          `${Math.max(0, (remaining / round.countdown_seconds) * 100)}%`;
      },
      recordControl,
    );
    $("play-rec").textContent = t("play.sending");
    state.socket?.send(encodeAudioFrame(round.round_number, clip.bytes, clip.mime));
  } catch (error) {
    notice(t("play.record.fallback", { detail: error.name || error.message }));
    sendFallbackTone(round);
  }
}

/** Used only when the browser refuses the microphone, so a match still plays. */
function sendFallbackTone(round) {
  const rate = 22050, length = Math.round(rate * 1.5);
  const samples = new Float32Array(length);
  const frequency = 220 + Math.random() * 440;
  for (let i = 0; i < length; i++) {
    samples[i] = Math.sin((2 * Math.PI * frequency * i) / rate) * (1 - i / length);
  }
  state.socket?.send(
    encodeAudioFrame(round.round_number, encodeWav(samples, rate), "audio/wav"),
  );
}

/* ---------------------------- rating ---------------------------- */

function onAudio(frame) {
  if (state.round && frame.header.round_number !== state.round.round_number) return;
  state.incoming = frame.audio.slice();
  state.incomingMime = frame.header.mime || "audio/webm";
  state.submitting = false;
  $("btn-submit").disabled = false;
  $("input-score").value = 50;
  updateScoreLabel(50);
  state.show("screen-online-rating");
  playIncoming();
}

async function playIncoming() {
  if (!state.incoming) return;
  $("rate-playing").textContent = t("rate.playing");
  await playEncoded(state.incoming, state.incomingMime);
  $("rate-playing").textContent = t("rate.played");
}

/**
 * The target's name in the active language.
 *
 * Resolved from the stable `sound_id` against the local library first; the
 * server's own names are the fallback, because its duel prompt list has a few
 * entries this client does not synthesise.
 */
function renderRoundSoundName() {
  const round = state.round;
  if (!round) return;
  const language = getLanguage();
  const fromServer = language === "en"
    ? (round.sound_name_en ?? round.sound_name)
    : round.sound_name;
  const name = soundName(round.sound_id, language, fromServer);
  $("play-sound").textContent = name;
  $("rate-sound").textContent = name;
}

function updateScoreLabel(score) {
  $("rate-score").textContent = score;
  const labels = [[90, "rate.label.90"], [70, "rate.label.70"], [50, "rate.label.50"],
                  [30, "rate.label.30"], [0, "rate.label.0"]];
  $("rate-label").textContent = t((labels.find(([floor]) => score >= floor) || labels[4])[1]);
}

/**
 * Re-paint the live online text after a language change.
 *
 * Only wording is rebuilt — the room, the round, the scores and the socket are
 * all untouched, so switching language mid-match costs nothing.
 */
export function redrawOnline() {
  const round = state.round;
  if (round) {
    $("play-round").textContent =
      t("play.round", { number: round.round_number, total: round.total_rounds });
    renderRoundSoundName();
  }
  if (state.lastResult) {
    const { round_number: number, performer_id: performer, timed_out: timedOut } = state.lastResult;
    const mine = performer === state.playerId;
    $("result-round").textContent = t("result.round", { number });
    $("result-caption").textContent = timedOut
      ? (mine ? t("result.timeout.mine") : t("result.timeout.theirs"))
      : (mine ? t("result.yours") : t("result.given"));
  }
  if (state.lastGameOver) {
    const draw = state.lastGameOver.winner_id === null;
    const won = state.lastGameOver.winner_id === state.playerId;
    $("over-title").textContent = draw ? t("over.draw") : won ? t("over.won") : t("over.lost");
  }
  updateScoreLabel(Number($("input-score").value));
  if (!state.socket) setConnection(t("conn.offline"));
}

/* ---------------------------- lifecycle ---------------------------- */

function leave(message) {
  clearTimeout(state.dwellTimer);
  recordControl.stopEarly?.();
  state.roomCode = null; state.round = null; state.incoming = null;
  state.pendingRound = null; state.pendingGameOver = null;
  clearNotices();
  state.show("screen-online-home");
  if (message) notice(message);
}

export function initOnline(show) {
  state.show = show;

  $("btn-create").onclick = async () => {
    clearNotices();
    try { await connect(); send("create_room"); }
    catch (e) { notice(t("conn.unreachable")); }
  };

  $("btn-join").onclick = async () => {
    clearNotices();
    const code = $("input-code").value.trim();
    if (code.length !== 4) { notice(t("error.code.length")); return; }
    try { await connect(); send("join_room", { code }); }
    catch (e) { notice(t("conn.unreachable")); }
  };

  $("input-code").oninput = (event) => {
    event.target.value = event.target.value.replace(/\D/g, "").slice(0, 4);
    $("btn-join").disabled = event.target.value.length !== 4;
  };

  $("btn-send-now").onclick = () => recordControl.stopEarly?.();
  $("input-score").oninput = (e) => updateScoreLabel(parseInt(e.target.value, 10));
  $("btn-replay").onclick = playIncoming;

  $("btn-submit").onclick = () => {
    if (state.submitting || !state.round) return;
    state.submitting = true;
    $("btn-submit").disabled = true;
    send("rating_submitted", {
      round_number: state.round.round_number,
      score: parseInt($("input-score").value, 10),
    });
  };

  document.querySelectorAll(".btn-leave").forEach((button) => {
    button.onclick = () => { if (state.roomCode) send("leave_room"); leave(null); };
  });

  if (!microphoneSupported()) {
    $("mic-hint").textContent =
      t("error.mic.address");
  }
}

/** Used by the main menu when backing out of online mode. */
export function leaveOnline() {
  if (state.roomCode) send("leave_room");
  leave(null);
}
