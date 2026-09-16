/**
 * Game-feel harness for FLIP ERROR.
 *
 * The playtest proves the level can be played; this proves it can be felt. It
 * drives the real build in Chromium and checks the three things a player judges
 * a runner on: that the second jump is a real, limited tool, that the trail is
 * a living spread of copies rather than a sprite stuck to the runner, and that
 * none of it costs the frame budget.
 *
 *   node tools/feeltest.mjs [--headed] [--shots DIR]
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dist = path.join(root, 'web/build/dist/js/productionExecutable');
const plan = JSON.parse(fs.readFileSync(path.join(root, 'core/build/level1-plan.json'), 'utf8'));
const shotDir = process.argv.includes('--shots')
  ? process.argv[process.argv.indexOf('--shots') + 1]
  : path.join(root, 'build/feeltest');
fs.mkdirSync(shotDir, { recursive: true });

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json' };
const server = http.createServer((req, res) => {
  const asked = req.url.split('?')[0];
  const f = path.join(dist, asked === '/' ? 'index.html' : asked);
  if (asked === '/favicon.ico') { res.writeHead(204); return res.end(); }
  if (!f.startsWith(dist) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': MIME[path.extname(f)] ?? 'application/octet-stream' });
  res.end(fs.readFileSync(f));
});
await new Promise(r => server.listen(0, r));
const url = `http://127.0.0.1:${server.address().port}/`;

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

const preinstalled = '/opt/pw-browsers/chromium';
const browser = await chromium.launch({
  headless: !process.argv.includes('--headed'),
  ...(fs.existsSync(preinstalled) ? { executablePath: preinstalled } : {}),
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding',
         '--disable-backgrounding-occluded-windows'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 600 }, deviceScaleFactor: 1 });

const errors = [];
const external = t => /ERR_CERT|fonts\.googleapis|fonts\.gstatic|Failed to load resource/.test(t);
page.on('console', m => { if (m.type() === 'error' && !external(m.text())) errors.push(m.text()); });
page.on('pageerror', e => errors.push(String(e)));

await page.addInitScript(p => { window.__plan = p; }, plan.jumps);
await page.goto(url, { waitUntil: 'load' });
await page.waitForFunction(() => window.FLIP && typeof window.FLIP.doubleJumps === 'function', { timeout: 10000 });
// The game opens on the level select now, so every harness starts a level.
await page.evaluate(() => { FLIP.wipe(); FLIP.play(1); });
await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });

const frames = n => page.evaluate(k => new Promise(res => {
  let i = 0; const go = () => (++i >= k ? res() : requestAnimationFrame(go)); requestAnimationFrame(go);
}), n);
const restart = async () => { await page.evaluate(() => FLIP.restart()); await frames(3); };

/** Fly one jump, optionally tapping again the moment [when] is true. */
const fly = (when) => page.evaluate(w => new Promise(res => {
  FLIP.restart();
  requestAnimationFrame(() => {
    FLIP.tap();
    let peak = FLIP.y(), started = false, tapped = false, f = 0;
    const go = () => {
      f++;
      if (!started && !FLIP.grounded()) started = true;
      if (started && !tapped) {
        const doTap = w === 'apex' ? FLIP.vy() <= 0
                    : w === 'mash' ? true
                    : w === 'late' ? FLIP.vy() < -14
                    : false;
        if (doTap) { FLIP.tap(); tapped = true; }
      }
      peak = Math.max(peak, FLIP.y());
      if (started && FLIP.grounded()) return res({ peak, doubles: FLIP.doubleJumps(), frames: f });
      if (f > 400) return res({ peak, doubles: FLIP.doubleJumps(), frames: f });
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  });
}), when);

// 1 — the second jump is a real lift, and the player can see it is one.
{
  const single = await fly('none');
  const dbl = await fly('apex');
  check('a second tap lifts the runner clearly higher',
        dbl.doubles === 1 && dbl.peak > single.peak + 1.5,
        `single peak ${single.peak.toFixed(2)}u -> double peak ${dbl.peak.toFixed(2)}u`);
  check('the boost stays inside one rhythmic beat', dbl.frames < single.frames * 1.9,
        `${single.frames} -> ${dbl.frames} frames airborne`);
}

// 2 — and it is limited: mashing buys nothing, and there is no third jump.
{
  const mashed = await fly('mash');
  check('mashing inside the lockout buys no boost', mashed.doubles === 0,
        `${mashed.doubles} boost(s), peak ${mashed.peak.toFixed(2)}u`);

  // Hammered inside ONE flight and stopped well before it can end. Sampling a
  // longer window is useless at 60Hz: the buffered tap re-launches the runner
  // the instant it lands, inside a single browser frame, so a chain of flights
  // looks like one. Core tests hold the no-third-jump rule across every timing;
  // this holds it in the real shell for a flight we know we are still inside.
  const hammered = await page.evaluate(() => new Promise(res => {
    FLIP.restart();
    requestAnimationFrame(() => {
      FLIP.tap();
      let f = 0;
      const go = () => {
        f++;
        FLIP.tap();
        // one boosted flight is ~49 frames; stop at 26 and we are mid-air
        if (f >= 26 || FLIP.state() !== 'RUNNING') {
          return res({ boosts: FLIP.doubleJumps(), grounded: FLIP.grounded() });
        }
        requestAnimationFrame(go);
      };
      requestAnimationFrame(go);
    });
  }));
  check('hammering one flight still buys exactly one boost',
        hammered.boosts === 1 && !hammered.grounded,
        `${hammered.boosts} boost(s), still airborne ${!hammered.grounded}`);
}

// 3 — the late window is shut, which is what keeps the landing buffer alive.
{
  const late = await fly('late');
  check('a tap during the drop is not a boost', late.doubles === 0);
}

// 4 — the boost has a purpose: the level's one star sits above a single jump.
{
  await restart();
  // The star sits in the level's one long rest, with no planned jump between
  // x=155 and x=180, so the run to it is the verified line plus one extra jump
  // that only pays off if it is boosted.
  const star = await page.evaluate(() => new Promise(res => {
    let i = 0, jumped = false, boosted = false, f = 0;
    const go = () => {
      f++;
      const x = FLIP.x();
      if (i < window.__plan.length && x >= window.__plan[i].x) { FLIP.tap(); i++; }
      else if (!jumped && x >= 166.3) { FLIP.tap(); jumped = true; }
      else if (jumped && !boosted && !FLIP.grounded() && FLIP.vy() <= 0) { FLIP.tap(); boosted = true; }
      if (x > 176 || f > 4000 || FLIP.state() !== 'RUNNING') {
        return res({ stars: FLIP.stars(), x, boosted, state: FLIP.state(), peak: FLIP.y() });
      }
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  check('the boost is what reaches the level star', star.stars === 1,
        `${star.stars} star(s), boosted=${star.boosted}, reached x=${star.x.toFixed(0)} (${star.state})`);
  await page.screenshot({ path: path.join(shotDir, '02-star-boost.png') });
}

// 5 — the trail is a living spread of copies, not a sprite pinned to the runner.
{
  await restart();
  await frames(40);
  const a = await page.evaluate(() => ({
    n: FLIP.effects(), head: FLIP.trailHeadX(), tail: FLIP.trailTailX(), x: FLIP.x(),
  }));
  await frames(12);
  const b = await page.evaluate(() => ({
    n: FLIP.effects(), head: FLIP.trailHeadX(), tail: FLIP.trailTailX(), x: FLIP.x(),
  }));
  check('the trail is a spread of copies behind the runner',
        a.head - a.tail > 0.8 && a.head <= a.x + 0.01,
        `${(a.head - a.tail).toFixed(2)}u of ghosts, newest ${(a.x - a.head).toFixed(2)}u behind`);
  check('the trail moves with the runner rather than sitting still',
        b.head > a.head + 0.5 && b.tail > a.tail + 0.5,
        `head ${a.head.toFixed(1)} -> ${b.head.toFixed(1)}, tail ${a.tail.toFixed(1)} -> ${b.tail.toFixed(1)}`);
  check('effects are live, not a static layer', a.n > 8 && b.n > 8, `${a.n} then ${b.n} live pieces`);
  await page.screenshot({ path: path.join(shotDir, '01-run-trail.png') });
}

// 6 — a boost visibly spends itself: more effects at the moment, then decay.
{
  await restart();
  const spike = await page.evaluate(() => new Promise(res => {
    let base = 0, jumped = false, boosted = false, peakFx = 0, f = 0;
    const go = () => {
      f++;
      if (!jumped) { base = FLIP.effects(); FLIP.tap(); jumped = true; }
      else if (!boosted && !FLIP.grounded() && FLIP.vy() <= 0) { FLIP.tap(); boosted = true; }
      else if (boosted) peakFx = Math.max(peakFx, FLIP.effects());
      if (f > 80) return res({ base, peakFx });
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  check('a boost throws more than a plain jump does', spike.peakFx > spike.base + 12,
        `${spike.base} -> ${spike.peakFx} live pieces`);
  await page.screenshot({ path: path.join(shotDir, '03-boost-burst.png') });
}

// 7 — the decoration clears itself; nothing accumulates across a run.
{
  await restart();
  await frames(120);
  const during = await page.evaluate(() => FLIP.effects());
  await page.evaluate(() => FLIP.restart());
  await frames(2);
  const afterReset = await page.evaluate(() => FLIP.effects());
  check('a retry wipes the old run off the screen', afterReset < during / 2,
        `${during} live pieces -> ${afterReset} after retry`);
}

// 8 — landings and close calls are being detected, so their cues can fire.
{
  await restart();
  const run = await page.evaluate(() => new Promise(res => {
    let i = 0, f = 0;
    const go = () => {
      f++;
      if (i < window.__plan.length && FLIP.x() >= window.__plan[i].x) { FLIP.tap(); i++; }
      if (FLIP.state() !== 'RUNNING' || f > 4000) {
        return res({ near: FLIP.nearMisses(), state: FLIP.state(), pct: FLIP.progress() });
      }
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  check('close calls are detected during a real run', run.near > 0,
        `${run.near} near misses over ${(run.pct * 100).toFixed(0)}% of the level`);
  check('the verified line still clears the level with all of this on',
        run.state === 'COMPLETE', run.state);
  await page.screenshot({ path: path.join(shotDir, '04-complete.png') });
}

// 9 — none of it costs the frame budget.
{
  await restart();
  const ft = await page.evaluate(() => new Promise(res => {
    const times = []; let last = performance.now(); let i = 0, f = 0;
    const go = () => {
      const now = performance.now(); times.push(now - last); last = now;
      f++;
      if (i < window.__plan.length && FLIP.x() >= window.__plan[i].x) { FLIP.tap(); i++; }
      // boost whenever it is legal, to keep the effect load at its worst
      if (FLIP.canDouble()) FLIP.tap();
      if (FLIP.state() !== 'RUNNING') { FLIP.restart(); }
      if (f > 900) return res(times.slice(60));
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  const sorted = [...ft].sort((a, b) => a - b);
  const p50 = sorted[Math.floor(sorted.length * 0.5)];
  const p99 = sorted[Math.floor(sorted.length * 0.99)];
  check('60fps holds while boosting nonstop', p50 < 20 && p99 < 40,
        `median ${p50.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${ft.length} frames`);
}

check('no javascript errors during the feel pass', errors.length === 0, errors.slice(0, 3).join(' | '));

await browser.close();
server.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(failed.length ? 1 : 0);
