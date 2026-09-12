/**
 * Screen routing, the start screen, and the back button.
 *
 * `/play` always opens on the start screen: the two modes hang off it, and
 * neither is entered until the player picks one. Single player is the offline
 * stage progression scored by the acoustic engine; online is the original
 * room-code game, reached only through this menu.
 */

import { initBattle, leaveBattle } from "./battle.js";
import { initOnline, leaveOnline } from "./online.js";
import { initSinglePlayer, renderStageList } from "./single.js";
import { loadProgress } from "./storage.js";
import { STAGES } from "./stages.js";

const topbar = document.getElementById("topbar");
const topbarTitle = document.getElementById("topbar-title");

/**
 * Where the back button goes from each screen, and what the bar says.
 *
 * `leaveRoom` marks the screens where backing out means abandoning a match:
 * the server has to be told, or the opponent is left staring at a dead room.
 */
const SCREENS = {
  "screen-menu": { title: null, back: null },

  "screen-sp-stages": { title: "المراحل", back: "screen-menu" },
  "screen-sp-stage": { title: "تحدي التقليد", back: "screen-sp-stages" },
  "screen-sp-result": { title: "النتيجة", back: "screen-sp-stages" },

  "screen-online-select": { title: "اللعب أونلاين", back: "screen-menu" },

  "screen-online-home": { title: "غرفة عادية", back: "screen-online-select" },
  "screen-online-waiting": { title: "غرفة جديدة", back: "screen-online-home", leave: "online" },
  "screen-online-play": { title: "الجولة", back: "screen-online-home", leave: "online" },
  "screen-online-rating": { title: "التقييم", back: "screen-online-home", leave: "online" },
  "screen-online-result": { title: "نتيجة الجولة", back: "screen-online-home", leave: "online" },
  "screen-online-over": { title: "انتهت المباراة", back: "screen-online-home", leave: "online" },

  "screen-battle-home": { title: "معركة صوتية", back: "screen-online-select" },
  "screen-battle-waiting": { title: "غرفة جديدة", back: "screen-battle-home", leave: "battle" },
  "screen-battle-round": { title: "المعركة", back: "screen-battle-home", leave: "battle" },
  "screen-battle-result": { title: "نتيجة الجولة", back: "screen-battle-home", leave: "battle" },
  "screen-battle-over": { title: "انتهت المعركة", back: "screen-battle-home", leave: "battle" },
};

let currentScreen = "screen-menu";

export function show(screenId) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.remove("active"));
  document.getElementById(screenId).classList.add("active");
  currentScreen = screenId;

  const screen = SCREENS[screenId] || {};
  topbar.hidden = !screen.back;
  topbarTitle.textContent = screen.title || "";
  window.scrollTo(0, 0);
}

function goBack() {
  const screen = SCREENS[currentScreen];
  if (!screen || !screen.back) return;

  if (screen.leave === "online") { leaveOnline(); return; }
  if (screen.leave === "battle") { leaveBattle(); return; }
  if (screen.back === "screen-sp-stages") renderStageList(show);
  show(screen.back);
}

document.getElementById("btn-back").onclick = goBack;

/** The Android/browser back gesture should feel like the on-screen button. */
window.addEventListener("popstate", () => {
  if (currentScreen !== "screen-menu") {
    goBack();
    history.pushState(null, "");
  }
});
history.pushState(null, "");

/* ---------------------------- the two modes ---------------------------- */

document.getElementById("btn-mode-single").onclick = () => {
  renderStageList(show);
  show("screen-sp-stages");
};

document.getElementById("btn-mode-online").onclick = () => show("screen-online-select");
document.getElementById("btn-select-normal").onclick = () => show("screen-online-home");
document.getElementById("btn-select-battle").onclick = () => show("screen-battle-home");

document.querySelectorAll(".btn-menu").forEach((button) => {
  button.onclick = () => {
    if (currentScreen.startsWith("screen-online-") && currentScreen !== "screen-online-select") {
      leaveOnline();
    } else if (currentScreen.startsWith("screen-battle-")) {
      leaveBattle();
    }
    show("screen-menu");
    refreshMenuFooter();
  };
});

/* ---------------------------- start screen ---------------------------- */

function refreshMenuFooter() {
  const { best } = loadProgress();
  const done = STAGES.filter((stage) => (best[stage.id] ?? -1) >= stage.passMark).length;
  document.getElementById("menu-foot").textContent =
    done > 0 ? `تقدمك: ${done} من ${STAGES.length} مرحلة` : "ابدأ من المرحلة الأولى";
}

initSinglePlayer(show);
initOnline(show);
initBattle(show);
refreshMenuFooter();
show("screen-menu");
