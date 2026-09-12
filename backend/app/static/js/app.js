/**
 * Screen routing and the main menu.
 *
 * Two modes hang off the menu: single player, which is entirely offline and
 * scored by the acoustic engine, and online multiplayer, which is the original
 * room-code game where the other player scores by hand.
 */

import { initOnline, leaveOnline } from "./online.js";
import { initSinglePlayer, renderStageList } from "./single.js";

function show(screenId) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.remove("active"));
  document.getElementById(screenId).classList.add("active");
  window.scrollTo(0, 0);
}

document.getElementById("btn-mode-single").onclick = () => {
  renderStageList(show);
  show("screen-sp-stages");
};

document.getElementById("btn-mode-online").onclick = () => show("screen-online-home");

document.querySelectorAll(".btn-menu").forEach((button) => {
  button.onclick = () => {
    // Backing out of a room must tell the server, or the opponent is stranded.
    if (document.querySelector(".screen.active")?.id?.startsWith("screen-online")) {
      leaveOnline();
    }
    show("screen-menu");
  };
});

initSinglePlayer(show);
initOnline(show);
show("screen-menu");
