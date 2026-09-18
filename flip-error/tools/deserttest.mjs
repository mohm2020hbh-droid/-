/**
 * WORLD 2 harness for FLIP ERROR.
 *
 * The playtest proves LEVEL 1 can be played and the feeltest proves it can be
 * felt. This one proves the desert is a different PLACE: that it is built out of
 * obstacles world 1 does not have, that its timed hazards warn before they kill,
 * that no ambience bed survived the audio decision - and, the part that matters
 * most, that every one of its six levels can actually be cleared by a human-rate
 * player flying the line the verifier proved.
 *
 *   node tools/deserttest.mjs [--headed] [--shots DIR]
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dist = path.join(root, 'web/build/dist/js/productionExecutable');
const shotDir = process.argv.includes('--shots')
  ? process.argv[process.argv.indexOf('--shots') + 1]
  : path.join(root, 'build/deserttest');
fs.mkdirSync(shotDir, { recursive: true });

const plans = {};
for (const id of [7, 8, 9, 10, 11, 12]) {
  const f = path.join(root, `core/build/level${id}-plan.json`);
  if (!fs.existsSync(f)) { console.error(`missing ${f} — run :core:jvmTest first`); process.exit(1); }
  plans[id] = JSON.parse(fs.readFileSync(f, 'utf8'));
}

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

await page.addInitScript(p => { window.__plans = p; }, plans);
await page.goto(url, { waitUntil: 'load' });
await page.waitForFunction(() => window.FLIP && typeof window.FLIP.scene === 'function', { timeout: 10000 });
// Every desert level is behind world 1, so the harness opens the lot.
await page.evaluate(() => { FLIP.wipe(); FLIP.setting('unlockAll', true); });

const frames = n => page.evaluate(k => new Promise(res => {
  let i = 0;
  const step = () => (++i >= k ? res() : requestAnimationFrame(step));
  requestAnimationFrame(step);
}), n);

const open = async id => {
  await page.evaluate(i => FLIP.play(i), id);
  await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
  await frames(2);
};

// 1 — the desert is somewhere else -------------------------------------------
await open(1);
const city = await page.evaluate(() => ({ world: FLIP.world(), scene: FLIP.scene(), looks: FLIP.looks() }));
await open(7);
const desert = await page.evaluate(() => ({
  world: FLIP.world(), scene: FLIP.scene(), looks: FLIP.looks(),
  surfaces: FLIP.surfaces(), bpm: FLIP.bpm(),
}));
check('level 7 is a new world', desert.world === 2 && city.world === 1,
  `world ${city.world} then ${desert.world}`);
check('and it is drawn as a desert, not a city',
  city.scene === 'CITY' && desert.scene === 'DESERT', `${city.scene} -> ${desert.scene}`);
check('the city is built out of spikes alone', city.looks === 'SPIKE', city.looks);
check('the desert is built out of things the city does not have',
  desert.looks.includes('SAND_WAVE') && desert.looks !== 'SPIKE', desert.looks);
check('and it runs on sand rather than stone',
  desert.surfaces.includes('SAND'), desert.surfaces);
check('the desert runs hot', desert.bpm >= 165 && desert.bpm <= 190, `${desert.bpm} BPM`);
await page.screenshot({ path: path.join(shotDir, '01-level7-sand.png') });

// 2 — the whole vocabulary is actually on the field ---------------------------
const seen = new Set();
const surfaces = new Set();
let pulsingTotal = 0, windsTotal = 0, stormsTotal = 0;
for (const id of [7, 8, 9, 10, 11, 12]) {
  await open(id);
  const lv = await page.evaluate(() => ({
    looks: FLIP.looks(), surfaces: FLIP.surfaces(), pulsing: FLIP.pulsing(),
    winds: FLIP.winds(), storms: FLIP.storms(),
  }));
  lv.looks.split(',').forEach(k => seen.add(k));
  lv.surfaces.split(',').forEach(k => surfaces.add(k));
  pulsingTotal += lv.pulsing;
  windsTotal += lv.winds;
  stormsTotal += lv.storms;
}
const wanted = ['SAND_WAVE', 'RUIN', 'RELIC', 'GEYSER', 'LASER', 'BOULDER'];
check('every new obstacle type reaches a level',
  wanted.every(k => seen.has(k)), [...seen].sort().join(','));
const wantedSurfaces = ['SAND', 'TEMPLE', 'BRIDGE', 'MIRAGE'];
check('and every new kind of ground does too',
  wantedSurfaces.every(k => surfaces.has(k)), [...surfaces].sort().join(','));
check('the desert has hazards that switch on and off', pulsingTotal >= 20, `${pulsingTotal} of them`);
check('and columns of moving air', windsTotal >= 3, `${windsTotal} of them`);
check('and weather you have to run through', stormsTotal >= 2, `${stormsTotal} storms`);

// 2b — the storm is weather, never a hazard ----------------------------------
await open(10);
const storm = await page.evaluate(() => new Promise(res => {
  // Stand still inside the sandstorm and see what it does. The answer has to be
  // "nothing": a storm that can kill is a hazard you cannot see, which is the
  // random death this game does not ship.
  const plan = window.__plans[10].jumps;
  let i = 0, owed = false, bx = 0, f = 0, sawStorm = 0, maxHaze = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    const haze = FLIP.storminess();
    if (haze > 0) sawStorm++;
    maxHaze = Math.max(maxHaze, haze);
    if (!window.__shot && FLIP.progress() > 0.45 && haze > 0.4) {
      window.__shot = true;              // hold a frame deep inside the weather
      return res({ state: FLIP.state(), sawStorm, maxHaze, cause: FLIP.cause(), held: true });
    }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000)
      return res({ state: FLIP.state(), sawStorm, maxHaze, cause: FLIP.cause() });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('the level is actually run through a storm',
  storm.sawStorm > 120 && storm.maxHaze > 0.4,
  `${storm.sawStorm} frames in it, thickest ${storm.maxHaze.toFixed(2)}`);
await page.screenshot({ path: path.join(shotDir, '04-level10-storm.png') });
// and now let the rest of it play out, to prove the weather never kills
const stormEnd = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[10].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  while (i < plan.length && FLIP.x() >= plan[i].x) i++;   // catch up to where we paused
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000)
      return res({ state: FLIP.state(), cause: FLIP.cause(), pct: FLIP.progress() * 100 });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('and the storm never kills anyone', stormEnd.state === 'COMPLETE',
  `${stormEnd.pct.toFixed(0)}% ${stormEnd.state}${stormEnd.state === 'DEAD' ? ' — ' + stormEnd.cause : ''}`);

// 3 — nothing switches on without warning ------------------------------------
// Sampled while the line is actually being flown, because "does it warn" is a
// question about the moments the player is really in, not about an idle runner
// dying on the first spike.
await open(9);
await page.evaluate(() => {
  const plan = window.__plans[9].jumps;
  window.__w = { warm: 0, live: 0, least: 99, frames: 0, shot: false };
  let i = 0, owed = false, bx = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (FLIP.pulsingWarm() > 0) window.__w.warm++;
    const live = FLIP.pulsingLive();
    window.__w.live = Math.max(window.__w.live, live);
    window.__w.least = Math.min(window.__w.least, live);
    window.__w.frames++;
    if (FLIP.progress() > 0.55) { window.__w.shot = true; return; }   // hold here for the camera
    if (FLIP.state() === 'RUNNING') requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
});
await page.waitForFunction(() => window.__w.shot || window.__w.frames > 3000, { timeout: 20000 });
const warn = await page.evaluate(() => window.__w);
check('timed hazards spend real time charging where the player can see them',
  warn.warm > 0 && warn.live > 0,
  `${warn.warm}/${warn.frames} frames charging, ${warn.live} lethal`);
// The level always has SOMETHING pulsing somewhere, so the meaningful question
// is whether they really cycle: at some point in the run, fewer of them are
// lethal than at another. A hazard that is on every frame is a wall.
check('and they really do switch off again',
  warn.least < warn.live, `between ${warn.least} and ${warn.live} lethal at once`);
await page.screenshot({ path: path.join(shotDir, '02-level9-beams.png') });

// 4 — the room is gone, and that is the point --------------------------------
// World 2 used to crossfade five sixty-second beds across five tension bands.
// That whole system was removed by decision: the game is gameplay SFX only now.
// What is checked here is the absence - no bed, no loop, no stream - because a
// removal nobody tests is a removal that comes back.
await open(12);
await frames(30);
const quiet = await page.evaluate(() =>
  [...document.querySelectorAll('audio')].filter(a => a.src || !a.paused).length);
check('the desert has no ambience bed of any kind', quiet === 0, `${quiet} active media elements`);

// 5 — every desert level can be cleared on its verified line -------------------
const clears = [];
for (const id of [7, 8, 9, 10, 11, 12]) {
  await open(id);
  const out = await page.evaluate(lv => new Promise(res => {
    const plan = window.__plans[lv].jumps;
    let i = 0, owed = false, bx = 0, f = 0;
    const step = () => {
      const x = FLIP.x();
      // A planned jump is taken from the ground; a planned boost is taken in the
      // air. Tapping a ground jump early, while still flying from the last one,
      // buys a double jump nobody asked for - which is exactly how LEVEL 7 died
      // at 97% before this harness learned the difference.
      if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
        owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
      } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
      if (FLIP.state() !== 'RUNNING' || ++f > 4000) {
        return res({ state: FLIP.state(), pct: Math.round(FLIP.progress() * 100),
                     cause: FLIP.cause(), taps: FLIP.taps() });
      }
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }), id);
  clears.push({ id, ...out });
  check(`level ${id} clears on its verified line`,
    out.state === 'COMPLETE', `${out.pct}%${out.state === 'DEAD' ? ' — ' + out.cause : ''}`);
  if (id === 12) await page.screenshot({ path: path.join(shotDir, '03-level12-finish.png') });
}

// 6 — and it still runs --------------------------------------------------------
await open(10);
const pace = await page.evaluate(() => new Promise(res => {
  const gaps = []; let last = performance.now(); let f = 0;
  const step = t => {
    gaps.push(t - last); last = t;
    if (++f > 800) return res(gaps.slice(5));
    if (f % 7 === 0) FLIP.tap();
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
pace.sort((a, b) => a - b);
const median = pace[Math.floor(pace.length / 2)];
const p99 = pace[Math.floor(pace.length * 0.99)];
check('60fps holds with the whole desert moving',
  median < 20 && p99 < 34, `median ${median.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${pace.length} frames`);

check('no javascript errors across world 2', errors.length === 0, errors.slice(0, 2).join(' | '));

await browser.close();
server.close();
const passed = results.filter(r => r.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(passed === results.length ? 0 : 1);
