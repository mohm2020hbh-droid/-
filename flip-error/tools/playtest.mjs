/**
 * Automated playtest for the FLIP ERROR web build.
 *
 * Drives the real game in a real browser: taps the screen, checks the runner
 * reacts on the next frame, forces each kind of death, retries, and plays the
 * verifier's perfect-run plan end to end. Screenshots are written so the
 * gameplay layer can be eyeballed for readability.
 *
 *   node tools/playtest.mjs [--headed] [--shots DIR]
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
  : path.join(root, 'build/playtest');
fs.mkdirSync(shotDir, { recursive: true });

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json' };
const server = http.createServer((req, res) => {
  const f = path.join(dist, req.url === '/' ? 'index.html' : req.url.split('?')[0]);
  if (req.url === '/favicon.ico') { res.writeHead(204); return res.end(); }
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

// The container ships one Chromium build; point at it rather than downloading.
const preinstalled = '/opt/pw-browsers/chromium';
const browser = await chromium.launch({
  headless: !process.argv.includes('--headed'),
  ...(fs.existsSync(preinstalled) ? { executablePath: preinstalled } : {}),
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding',
         '--disable-backgrounding-occluded-windows'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 600 }, deviceScaleFactor: 1 });

const errors = [];
// The webfont is a progressive enhancement and this container's TLS proxy
// blocks it; a failed external fetch is not a game error.
const external = t => /ERR_CERT|fonts\.googleapis|fonts\.gstatic|Failed to load resource/.test(t);
page.on('console', m => { if (m.type() === 'error' && !external(m.text())) errors.push(m.text()); });
page.on('pageerror', e => errors.push(String(e)));

await page.addInitScript(p => { window.__plan = p; }, plan.jumps);
await page.goto(url, { waitUntil: 'load' });
await page.waitForFunction(() => window.FLIP && typeof window.FLIP.x === 'function', { timeout: 10000 });
// The game opens on the level select now, so every harness starts a level.
await page.evaluate(() => { FLIP.wipe(); FLIP.play(1); });
await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });

const S = () => page.evaluate(() => ({
  state: FLIP.state(), x: FLIP.x(), y: FLIP.y(), vy: FLIP.vy(),
  grounded: FLIP.grounded(), face: FLIP.face(), cause: FLIP.cause(),
  progress: FLIP.progress(), attempts: FLIP.attempts(), taps: FLIP.taps(),
  stateTime: FLIP.stateTime(),
}));
const frames = n => page.evaluate(k => new Promise(res => {
  let i = 0; const go = () => (++i >= k ? res() : requestAnimationFrame(go)); requestAnimationFrame(go);
}), n);
const restart = async () => { await page.evaluate(() => FLIP.restart()); await frames(2); };

// 1 — the page comes up clean and the runner is already moving.
{
  const a = await S(); await frames(30); const b = await S();
  check('auto-run starts immediately', b.x > a.x + 2, `x ${a.x.toFixed(1)} -> ${b.x.toFixed(1)}`);
  check('runner starts on the ground', a.grounded && a.face === 'RUN');
}

// 2 — a tap is a jump, on the very next frame. Measured inside the page so
//     the devtools round trip cannot inflate the number.
{
  await restart();
  const latency = await page.evaluate(() => new Promise(res => {
    let n = 0, tapAt = -1;
    const go = () => {
      n++;
      if (n === 5) { FLIP.tap(); tapAt = n; }
      if (tapAt > 0 && !FLIP.grounded()) return res(n - tapAt);
      if (n > 90) return res(-1);
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  check('tap jumps on the next frame', latency >= 0 && latency <= 1, `${latency} frame(s)`);
  const after = await S();
  check('face switches to JUMP', after.face === 'JUMP');
  await page.screenshot({ path: path.join(shotDir, '01-jump.png') });
}

// 3 — touch input works, not only mouse.
{
  await restart(); await frames(10);
  await page.touchscreen.tap(400, 300).catch(() => page.mouse.click(400, 300));
  await frames(1);
  const s = await S();
  check('touch tap also jumps', !s.grounded && s.vy > 0, `vy ${s.vy.toFixed(1)}`);
}

// 4 — doing nothing kills you on the first spike, and the game says why.
{
  await restart();
  await page.waitForFunction(() => FLIP.state() !== 'RUNNING', { timeout: 15000 });
  const s = await S();
  check('idling dies on the first spike', s.state === 'DEAD' && s.cause === 'SPIKE',
        `cause ${s.cause} at ${(s.progress * 100).toFixed(0)}%`);
  check('death face is shown', s.face === 'DEAD');
}

// 5 — the retry gate: nothing during the 0.25s effect, any tap after it.
//     Both halves are driven inside the page so the timing is not smeared.
{
  const gated = await page.evaluate(() => new Promise(res => {
    const before = FLIP.attempts();
    FLIP.tap();                                   // still inside the death effect
    const during = FLIP.attempts();
    const wait = () => {
      if (FLIP.stateTime() < 0.30) return requestAnimationFrame(wait);
      FLIP.tap();
      requestAnimationFrame(() => res({ before, during, after: FLIP.attempts(),
                                        state: FLIP.state(), x: FLIP.x() }));
    };
    requestAnimationFrame(wait);
  }));
  check('tap during the death effect does not retry', gated.during === gated.before,
        `attempts ${gated.before} -> ${gated.during}`);
  check('tap after the effect retries instantly',
        gated.state === 'RUNNING' && gated.after === gated.before + 1 && gated.x < 1.0,
        `attempt ${gated.after}, restarted at x ${gated.x.toFixed(2)}`);
  await page.screenshot({ path: path.join(shotDir, '02-death.png') });
}

// 5b — jumping inside the ceiling corridor is its own death.
{
  await restart();
  await page.evaluate(() => new Promise(res => {
    let i = 0;
    const go = () => {
      if (i < window.__plan.length && FLIP.x() >= window.__plan[i].x) { FLIP.tap(); i++; }
      if (FLIP.x() >= 115) { FLIP.tap(); return res(); }       // tap under the ceiling
      if (FLIP.state() !== 'RUNNING') return res();
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  await page.waitForFunction(() => FLIP.state() !== 'RUNNING', { timeout: 8000 }).catch(() => {});
  const s = await S();
  check('jumping in the ceiling corridor kills with its own cause',
        s.cause === 'CEILING_SPIKE', `cause ${s.cause} at ${(s.progress * 100).toFixed(0)}%`);
}

// 6 — missing a gap is a pit death, not a silent fall.
{
  await restart();
  const gapJump = plan.jumps[6].x;           // the jump over the first 3.85u gap
  await page.evaluate(stopAt => new Promise(res => {
    let i = 0;
    const jumps = window.__plan.filter(j => j.x < stopAt);
    const go = () => {
      if (i < jumps.length && FLIP.x() >= jumps[i].x) { FLIP.tap(); i++; }
      if (FLIP.state() !== 'RUNNING' || FLIP.x() > stopAt + 12) return res();
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }), gapJump).catch(() => {});
  await page.waitForFunction(() => FLIP.state() !== 'RUNNING', { timeout: 20000 }).catch(() => {});
  const s = await S();
  check('skipping the gap jump reads as a missed jump', s.state === 'DEAD' && s.cause === 'PIT',
        `cause ${s.cause} at ${(s.progress * 100).toFixed(0)}%`);
}

// 7 — the perfect run clears the level.
{
  await restart();
  const t0 = Date.now();
  await page.evaluate(() => new Promise(res => {
    let i = 0;
    const jumps = window.__plan;
    window.__frameTimes = [];
    let last = performance.now();
    const go = () => {
      const now = performance.now();
      window.__frameTimes.push(now - last); last = now;
      if (i < jumps.length && FLIP.x() >= jumps[i].x) { FLIP.tap(); i++; }
      if (FLIP.state() !== 'RUNNING') return res();
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  const s = await S();
  check('the perfect run reaches the finish gate', s.state === 'COMPLETE',
        s.state === 'COMPLETE' ? `${((Date.now() - t0) / 1000).toFixed(1)}s wall clock, ${s.taps} taps`
                               : `died at ${(s.progress * 100).toFixed(0)}% (${s.cause})`);
  await frames(10);
  await page.screenshot({ path: path.join(shotDir, '05-complete.png') });

  const ft = await page.evaluate(() => window.__frameTimes.slice(30));
  const sorted = [...ft].sort((a, b) => a - b);
  const p50 = sorted[Math.floor(sorted.length * 0.5)];
  const p99 = sorted[Math.floor(sorted.length * 0.99)];
  check('frame pacing stays at 60fps', p50 < 20 && p99 < 40,
        `median ${p50.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${ft.length} frames`);
}

// 8 — readability shots along the run.
{
  await restart();
  for (const [name, upto] of [['03-ceiling', 112], ['04-gauntlet', 228], ['06-finish', 284]]) {
    await page.evaluate(stop => new Promise(res => {
      let i = window.__plan.findIndex(j => j.x >= FLIP.x());
      const go = () => {
        if (i >= 0 && i < window.__plan.length && FLIP.x() >= window.__plan[i].x) { FLIP.tap(); i++; }
        if (FLIP.x() >= stop || FLIP.state() !== 'RUNNING') return res();
        requestAnimationFrame(go);
      };
      requestAnimationFrame(go);
    }), upto);
    await page.screenshot({ path: path.join(shotDir, name + '.png') });
  }
}

check('no javascript errors during play', errors.length === 0, errors.slice(0, 3).join(' | '));

await browser.close();
server.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(failed.length ? 1 : 0);
