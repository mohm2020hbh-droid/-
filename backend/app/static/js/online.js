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

const $ = (id) => document.getElementById(id);
const RESULT_DWELL_MS = 3000;

const state = {
  socket: null, playerId: null, opponentId: null, roomCode: null,
  round: null, scores: {}, incoming: null, incomingMime: "audio/webm",
  pendingRound: null, pendingGameOver: null,
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
    setConnection("جارٍ الاتصال…");
    const socket = new WebSocket(serverUrl());
    socket.binaryType = "arraybuffer";
    state.socket = socket;

    socket.onmessage = (event) => {
      if (typeof event.data === "string") {
        const message = JSON.parse(event.data);
        if (message.type === "connected") {
          state.playerId = message.payload.player_id;
          setConnection("متصل", "ok");
          resolve();
        }
        onEvent(message.type, message.payload);
      } else {
        const frame = decodeAudioFrame(event.data);
        if (frame) onAudio(frame);
      }
    };
    socket.onerror = () => { setConnection("تعذّر الاتصال", "bad"); reject(new Error("socket")); };
    socket.onclose = () => {
      setConnection("انقطع الاتصال", "bad");
      state.socket = null;
      if (document.querySelector(".screen.active")?.id?.startsWith("screen-online")) {
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
      leave("انسحب الخصم من المباراة");
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
    audio_too_large: "التسجيل طويل جدًا",
    not_your_turn: "ليس دورك في هذه الجولة",
    stale_round: "انتهت هذه الجولة بالفعل",
    wrong_phase: "انتهت هذه الجولة بالفعل",
    invalid_score: "قيمة التقييم غير صالحة",
  };
  state.submitting = false;
  $("btn-submit").disabled = false;
  notice(messages[reason] || "تعذّر تنفيذ الطلب");
}

function applyPending() {
  clearTimeout(state.dwellTimer);

  if (state.pendingGameOver) {
    const over = state.pendingGameOver;
    state.pendingGameOver = null;
    state.pendingRound = null;
    state.scores = over.final_scores;
    const draw = over.winner_id === null;
    const won = over.winner_id === state.playerId;
    $("over-emoji").textContent = draw ? "🤝" : won ? "🏆" : "😮‍💨";
    $("over-title").textContent = draw ? "تعادل!" : won ? "فزت بالمبارزة!" : "فاز خصمك هذه المرة";
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

  $("play-round").textContent = `الجولة ${round.round_number} من ${round.total_rounds}`;
  $("play-emoji").textContent = round.sound_emoji;
  $("play-sound").textContent = round.sound_name;
  $("rate-emoji").textContent = round.sound_emoji;
  $("rate-sound").textContent = round.sound_name;

  const performing = round.performer_id === state.playerId;
  $("play-performer").style.display = performing ? "block" : "none";
  $("play-rater").style.display = performing ? "none" : "block";
  state.show("screen-online-play");

  if (performing) perform(round);
}

function onRoundResult(payload) {
  clearTimeout(state.dwellTimer);
  state.scores = payload.total_scores;
  const mine = payload.performer_id === state.playerId;
  $("result-round").textContent = `نتيجة الجولة ${payload.round_number}`;
  $("result-score").textContent = payload.score;
  $("result-score").classList.remove("pop");
  void $("result-score").offsetWidth;
  $("result-score").classList.add("pop");
  $("result-caption").textContent = payload.timed_out
    ? (mine ? "انتهى الوقت قبل إرسال تسجيلك" : "انتهى وقت الجولة")
    : (mine ? "هذه نقاطك عن أدائك" : "هذه النقاط التي منحتَها لخصمك");
  $("result-mine").textContent = state.scores[state.playerId] || 0;
  $("result-theirs").textContent = state.scores[state.opponentId] || 0;
  state.show("screen-online-result");
  state.dwellTimer = setTimeout(applyPending, RESULT_DWELL_MS);
}

/* ---------------------------- performing ---------------------------- */

async function perform(round) {
  $("play-rec").textContent = "جارٍ التسجيل";

  if (!microphoneSupported()) {
    notice("المتصفح لا يتيح الميكروفون هنا (يتطلب https أو 127.0.0.1). ستُرسل نغمة بديلة.");
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
    $("play-rec").textContent = "جارٍ إرسال التسجيل…";
    state.socket?.send(encodeAudioFrame(round.round_number, clip.bytes, clip.mime));
  } catch (error) {
    notice("تعذّر التسجيل: " + (error.name || error.message) + ". ستُرسل نغمة بديلة.");
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
  $("rate-playing").textContent = "🔊 جارٍ التشغيل…";
  await playEncoded(state.incoming, state.incomingMime);
  $("rate-playing").textContent = "انتهى التشغيل";
}

function updateScoreLabel(score) {
  $("rate-score").textContent = score;
  const labels = [[90, "مطابق تمامًا! 🤯"], [70, "تقليد ممتاز 👏"], [50, "قريب من الصوت 🙂"],
                  [30, "محاولة متواضعة 😅"], [0, "بعيد عن المطلوب 😂"]];
  $("rate-label").textContent = (labels.find(([floor]) => score >= floor) || labels[4])[1];
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
    catch (e) { notice("تعذّر الاتصال بالخادم"); }
  };

  $("btn-join").onclick = async () => {
    clearNotices();
    const code = $("input-code").value.trim();
    if (code.length !== 4) { notice("أدخل كودًا مكوّنًا من ٤ أرقام"); return; }
    try { await connect(); send("join_room", { code }); }
    catch (e) { notice("تعذّر الاتصال بالخادم"); }
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
      "الميكروفون غير متاح على هذا العنوان — افتح الصفحة عبر http://127.0.0.1:8000/play أو https.";
  }
}

/** Used by the main menu when backing out of online mode. */
export function leaveOnline() {
  if (state.roomCode) send("leave_room");
  leave(null);
}
