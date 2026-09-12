"""End-to-end browser checks against a real running server.

These drive a real Chromium against a real uvicorn process — not a mocked DOM
— because the failures this is meant to catch are exactly the ones unit tests
cannot see: a module that throws on load, a cached page, a stage list that
never renders, a screen that still shows Arabic in English mode.

Run with:   .venv/bin/python tests/e2e/test_play_browser.py
Requires:   pip install playwright   (the browser is already on the image)
"""

from __future__ import annotations

import os
import socket
import subprocess
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

BACKEND = Path(__file__).resolve().parents[2]
VIEWPORTS = [360, 390, 412]

passed = 0
failed = 0


def check(label: str, condition: bool, detail: str = "") -> bool:
    global passed, failed
    if condition:
        passed += 1
        print(f"  ok   {label}" + (f"  {detail}" if detail else ""))
    else:
        failed += 1
        print(f"  FAIL {label}" + (f"  {detail}" if detail else ""))
    return bool(condition)


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_for(port: int, timeout: float = 25.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return True
        except OSError:
            time.sleep(0.2)
    return False


def text(page, selector: str) -> str:
    return (page.text_content(selector) or "").strip()


ARABIC = set(range(0x0600, 0x0700)) | set(range(0x0750, 0x0780))


def has_arabic(value: str) -> bool:
    return any(ord(ch) in ARABIC for ch in value)


def record_console(errors: list[str], message) -> None:
    """Collect real console errors, ignoring the ones that are by design.

    Probing for a dropped-in recording at /static/audio/<id>.mp3 is *meant* to
    404 when no file has been dropped in, and the browser logs every 404 to the
    console. Those are expected; anything else is a defect.
    """
    if message.type != "error":
        return
    url = (message.location or {}).get("url", "") or ""
    if "/static/audio/" in url or "favicon" in url:
        return
    errors.append(f"{message.text} @ {url}")


def visible_text(page) -> str:
    """Every piece of text a player can actually see right now."""
    return page.evaluate(
        """() => {
        const out = [];
        const walk = (node) => {
          for (const child of node.children) {
            const style = getComputedStyle(child);
            if (style.display === 'none' || style.visibility === 'hidden') continue;
            // The language switch names each language in its own script on
            // purpose, so it is not part of a "is this translated" scan.
            if (child.closest('.lang-switch')) continue;
            if (child.children.length === 0) {
              const t = (child.textContent || '').trim();
              if (t) out.push(t);
            } else {
              walk(child);
            }
          }
        };
        const active = document.querySelector('.screen.active');
        if (active) walk(active);
        const bar = document.getElementById('topbar');
        if (bar && !bar.hidden) walk(bar);
        return out.join(' | ');
      }"""
    )


def main() -> int:
    port = free_port()
    server = subprocess.Popen(
        [
            str(BACKEND / ".venv" / "bin" / "python"),
            "-m", "uvicorn", "app.main:app",
            "--host", "127.0.0.1", "--port", str(port), "--log-level", "warning",
        ],
        cwd=BACKEND,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    if not wait_for(port):
        print("server did not start")
        print((server.stderr.read() or b"").decode()[-2000:])
        server.kill()
        return 1

    base = f"http://127.0.0.1:{port}"
    try:
        with sync_playwright() as pw:
            # Use the browser already on the image when there is one, rather
            # than downloading a matching build.
            preinstalled = Path("/opt/pw-browsers/chromium")
            launch_args = {"executable_path": str(preinstalled)} if preinstalled.exists() else {}
            browser = pw.chromium.launch(
                **launch_args,
                args=[
                    "--use-fake-ui-for-media-stream",
                    "--use-fake-device-for-media-stream",
                    "--autoplay-policy=no-user-gesture-required",
                ]
            )
            run_checks(browser, base)
            browser.close()
    finally:
        server.terminate()
        try:
            server.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server.kill()

    print(f"\n{passed} passed, {failed} failed")
    return 1 if failed else 0


def run_checks(browser, base: str) -> None:
    context = browser.new_context(
        viewport={"width": 390, "height": 844},
        permissions=["microphone"],
    )
    page = context.new_page()
    errors: list[str] = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.on("console", lambda m: record_console(errors, m))

    # ---------------------------------------------------------------- #
    # 1. The page loads, and it loads the CURRENT build
    # ---------------------------------------------------------------- #
    response = page.goto(f"{base}/play", wait_until="networkidle")
    check("/play responds 200", response.status == 200, f"status {response.status}")
    check("/play is served uncached",
          "no-store" in (response.header_value("cache-control") or ""),
          response.header_value("cache-control") or "(no header)")

    check("no JavaScript error on load", not errors, "; ".join(errors[:3]))

    # ---------------------------------------------------------------- #
    # 2. The first screen is the mode chooser, not a room form
    # ---------------------------------------------------------------- #
    check("the first screen is the main menu",
          page.evaluate("document.querySelector('.screen.active').id") == "screen-menu")
    check("both modes are offered on it",
          page.is_visible("#btn-mode-single") and page.is_visible("#btn-mode-online"))
    check("the language switch is on it",
          page.is_visible('.lang[data-lang="ar"]') and page.is_visible('.lang[data-lang="en"]'))

    # ---------------------------------------------------------------- #
    # 3. Arabic is the default, and it is right-to-left
    # ---------------------------------------------------------------- #
    check("Arabic is the default language",
          page.evaluate("document.documentElement.lang") == "ar")
    check("Arabic renders right-to-left",
          page.evaluate("document.documentElement.dir") == "rtl")
    check("the Arabic menu is in Arabic", has_arabic(text(page, ".mode-title")),
          text(page, "#btn-mode-single .mode-title"))

    # ---------------------------------------------------------------- #
    # 4. Single player, in Arabic
    # ---------------------------------------------------------------- #
    page.click("#btn-mode-single")
    page.wait_for_selector("#screen-sp-stages.active")
    stages = page.locator("#stage-list .stage")
    check("the stage list renders all 20 stages", stages.count() == 20, f"{stages.count()} rows")
    check("stage 1 is unlocked and the rest are locked",
          not stages.nth(0).is_disabled() and stages.nth(1).is_disabled())
    check("the back button appears once inside a mode", page.is_visible("#btn-back"))

    stages.nth(0).click()
    page.wait_for_selector("#screen-sp-stage.active")
    check("a stage screen shows its target and hint",
          bool(text(page, "#sp-title")) and text(page, "#sp-title") != "—",
          text(page, "#sp-title"))
    check("the stage hint is filled in",
          bool(text(page, "#sp-hint")) and text(page, "#sp-hint") != "—")

    # The target must be real, playable audio rendered on the device.
    target = page.evaluate(
        """async () => {
        const { stageTarget, STAGES } = await import('/static/js/stages.js');
        const samples = stageTarget(STAGES[0]);
        let peak = 0, sum = 0;
        for (const v of samples) { peak = Math.max(peak, Math.abs(v)); sum += v * v; }
        return { length: samples.length, peak, rms: Math.sqrt(sum / samples.length) };
      }"""
    )
    check("the stage's target audio is audible",
          target["length"] > 5000 and 0.05 < target["rms"] < 0.5 and target["peak"] <= 1.0,
          f"len={target['length']} rms={target['rms']:.3f} peak={target['peak']:.2f}")

    # Microphone + scoring, end to end through the real UI.
    page.click("#btn-attempt")
    page.wait_for_selector("#sp-recording", state="visible", timeout=5000)
    check("pressing record enters the recording state", True)
    # Let a real amount of audio in before stopping: the fake microphone does
    # produce a tone, but a clip cut off at zero seconds has nothing to score,
    # which is a legitimate refusal rather than a bug.
    page.wait_for_timeout(2500)
    page.click("#btn-stop-early")
    page.wait_for_selector("#screen-sp-result.active", timeout=25000)
    score = text(page, "#sp-result-score")
    check("a recording produces a numeric score", score.isdigit() and 0 <= int(score) <= 100, score)
    rows = page.locator("#sp-breakdown .bar-row")
    check("the acoustic breakdown is shown", rows.count() >= 4, f"{rows.count()} measures")

    saved = page.evaluate("localStorage.getItem('voiceduel.progress.v1')")
    check("the attempt is saved to local progress", saved is not None and "stage-1" in saved)

    # ---------------------------------------------------------------- #
    # 5. Switching to English
    # ---------------------------------------------------------------- #
    page.click(".btn-menu") if page.is_visible(".btn-menu") else page.click("#btn-sp-list")
    page.click("#btn-back") if page.is_visible("#btn-back") else None
    page.wait_for_selector("#screen-menu.active", timeout=5000)

    page.click('.lang[data-lang="en"]')
    check("English sets the document language",
          page.evaluate("document.documentElement.lang") == "en")
    check("English renders left-to-right",
          page.evaluate("document.documentElement.dir") == "ltr")
    check("the page title is translated",
          not has_arabic(page.title()), page.title())

    menu_text = visible_text(page)
    check("the English main menu has no Arabic left on it",
          not has_arabic(menu_text), menu_text[:120])

    progress_after = page.evaluate("localStorage.getItem('voiceduel.progress.v1')")
    check("switching language did not touch saved progress", progress_after == saved)

    # ---------------------------------------------------------------- #
    # 6. Every English screen, checked for leftover Arabic
    # ---------------------------------------------------------------- #
    page.click("#btn-mode-single")
    page.wait_for_selector("#screen-sp-stages.active")
    stages_text = visible_text(page)
    check("the English stage list has no Arabic left on it",
          not has_arabic(stages_text), stages_text[:160])
    check("stage titles are in English",
          "Ambulance" in stages_text or "Siren" in stages_text, stages_text[:80])
    check("stage 1 stays unlocked after the language switch",
          not page.locator("#stage-list .stage").nth(0).is_disabled())

    page.locator("#stage-list .stage").nth(0).click()
    page.wait_for_selector("#screen-sp-stage.active")
    stage_text = visible_text(page)
    check("the English stage screen has no Arabic left on it",
          not has_arabic(stage_text), stage_text[:160])

    page.click("#btn-back")
    page.wait_for_selector("#screen-sp-stages.active")
    page.click("#btn-back")
    page.wait_for_selector("#screen-menu.active")

    # Online: mode select, normal room, battle room.
    page.click("#btn-mode-online")
    page.wait_for_selector("#screen-online-select.active")
    select_text = visible_text(page)
    check("the English online chooser has no Arabic left on it",
          not has_arabic(select_text), select_text[:160])
    check("both online modes are offered",
          page.is_visible("#btn-select-normal") and page.is_visible("#btn-select-battle"))

    page.click("#btn-select-normal")
    page.wait_for_selector("#screen-online-home.active")
    room_text = visible_text(page)
    placeholder = page.get_attribute("#input-code", "placeholder")
    check("the English normal room has no Arabic left on it",
          not has_arabic(room_text), room_text[:160])
    check("the room-code placeholder is translated",
          not has_arabic(placeholder or ""), placeholder)

    page.click("#btn-back")
    page.wait_for_selector("#screen-online-select.active")
    page.click("#btn-select-battle")
    page.wait_for_selector("#screen-battle-home.active")
    battle_text = visible_text(page)
    check("the English Voice Battle room has no Arabic left on it",
          not has_arabic(battle_text), battle_text[:160])

    # ---------------------------------------------------------------- #
    # 7. A real online round, in English, between two tabs
    # ---------------------------------------------------------------- #
    page.click("#btn-back")
    page.wait_for_selector("#screen-online-select.active")
    page.click("#btn-select-normal")
    page.wait_for_selector("#screen-online-home.active")
    page.click("#btn-create")
    page.wait_for_selector("#screen-online-waiting.active", timeout=10000)
    code = text(page, "#room-code")
    check("creating a room yields a 4-digit code", code.isdigit() and len(code) == 4, code)

    guest = context.new_page()
    guest.goto(f"{base}/play", wait_until="networkidle")
    guest.evaluate("localStorage.setItem('voiceduel.language.v1', 'en')")
    guest.reload(wait_until="networkidle")
    check("the chosen language survives a reload",
          guest.evaluate("document.documentElement.lang") == "en")

    guest.click("#btn-mode-online")
    guest.click("#btn-select-normal")
    guest.wait_for_selector("#screen-online-home.active")
    guest.fill("#input-code", code)
    guest.click("#btn-join")

    page.wait_for_selector("#screen-online-play.active", timeout=15000)
    guest.wait_for_selector("#screen-online-play.active", timeout=15000)
    check("both players reach the round screen", True)
    round_label = text(page, "#play-round")
    check("the round counter is in English", not has_arabic(round_label), round_label)
    sound_name = text(page, "#play-sound")
    check("the target sound is named in English",
          bool(sound_name) and not has_arabic(sound_name), sound_name)

    # Whoever is performing records; the other rates.
    performer = page if page.is_visible("#play-performer") else guest
    rater = guest if performer is page else page
    rater.wait_for_selector("#screen-online-rating.active", timeout=25000)
    rate_text = visible_text(rater)
    check("the English rating screen has no Arabic left on it",
          not has_arabic(rate_text), rate_text[:160])
    rater.click("#btn-submit")

    performer.wait_for_selector("#screen-online-result.active", timeout=15000)
    result_text = visible_text(performer)
    check("the English round result has no Arabic left on it",
          not has_arabic(result_text), result_text[:160])

    guest.close()

    # ---------------------------------------------------------------- #
    # 8. A Voice Battle round, in Arabic, to prove both modes survive
    # ---------------------------------------------------------------- #
    host = context.new_page()
    opponent = context.new_page()
    for tab in (host, opponent):
        tab.goto(f"{base}/play", wait_until="networkidle")
        tab.click("#btn-mode-online")
        tab.click("#btn-select-battle")
        tab.wait_for_selector("#screen-battle-home.active")

    host.click("#btn-battle-create")
    host.wait_for_selector("#screen-battle-waiting.active", timeout=10000)
    battle_code = text(host, "#battle-room-code")
    opponent.fill("#input-battle-code", battle_code)
    opponent.click("#btn-battle-join")

    host.wait_for_selector("#screen-battle-round.active", timeout=15000)
    opponent.wait_for_selector("#screen-battle-round.active", timeout=15000)
    check("a Voice Battle round starts for both players", True, f"code {battle_code}")
    check("the battle names its target",
          bool(text(host, "#battle-target-name")) and text(host, "#battle-target-name") != "—",
          text(host, "#battle-target-name"))

    for tab in (host, opponent):
        tab.click("#btn-battle-start")
    host.wait_for_selector("#screen-battle-result.active", timeout=40000)
    mine = text(host, "#battle-result-mine")
    check("the battle round is scored acoustically on each device",
          mine.isdigit() and 0 <= int(mine) <= 100, f"my score {mine}")
    host.close()
    opponent.close()

    # ---------------------------------------------------------------- #
    # 9. Phone widths, with the design untouched
    # ---------------------------------------------------------------- #
    for width in VIEWPORTS:
        page.set_viewport_size({"width": width, "height": 780})
        page.goto(f"{base}/play", wait_until="networkidle")
        overflow = page.evaluate(
            "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"
        )
        check(f"the menu fits a {width}px screen with no sideways scroll",
              overflow <= 1, f"overflow {overflow}px")
        page.click("#btn-mode-single")
        page.wait_for_selector("#screen-sp-stages.active")
        overflow = page.evaluate(
            "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"
        )
        check(f"the stage list fits a {width}px screen", overflow <= 1, f"overflow {overflow}px")

    check("no JavaScript error during the whole run", not errors, "; ".join(errors[:3]))
    context.close()


if __name__ == "__main__":
    sys.exit(main())
