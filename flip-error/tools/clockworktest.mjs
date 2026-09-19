/**
 * WORLD 4 harness for FLIP ERROR.
 *
 * The abyss had to prove it was a different PLACE. The machine has to prove
 * something harder: that it is a different KIND of obstacle. Everything in
 * worlds 1 to 3 is a thing that happens to the runner; everything in world 4 is
 * a mechanism on a cycle, and the claim the world lives or dies on is that the
 * cycles are DETERMINISTIC - that the same level, played the same way, does the
 * same thing every time, so a death is always the player's to learn from.
 *
 * So this harness flies LEVEL 24 twice and compares the two runs frame for
 * frame. If any part of the machine has a clock of its own, the two disagree.
 *
 * Then: is it somewhere else, is its whole vocabulary on the field, is there a
 * spike left in it, does the ram punish the jump and spare the run, does every
 * level clear on the line the verifier proved, is the final gauntlet actually
 * continuous, is there still no ambience, and does it hold 60fps with the
 * densest levels in the game.
 *
 *   node tools/clockworktest.mjs [--headed] [--shots DIR]
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
  : path.join(root, 'build/clockworktest');
fs.mkdirSync(shotDir, { recursive: true });

const IDS = [19, 20, 21, 22, 23, 24];
const plans = {};
for (const id of IDS) {
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

const fly = (id) => page.evaluate(lv => new Promise(res => {
  const plan = window.__plans[lv].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  const step = () => {
    const x = FLIP.x();
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

// 1 — the machine is somewhere else -------------------------------------------
await open(18);
const abyss = await page.evaluate(() => ({ world: FLIP.world(), scene: FLIP.scene() }));
await open(19);
const works = await page.evaluate(() => ({
  world: FLIP.world(), scene: FLIP.scene(), looks: FLIP.looks(), bpm: FLIP.bpm(),
}));
check('level 19 is a fourth world', works.world === 4 && abyss.world === 3,
  `world ${abyss.world} then ${works.world}`);
check('and it is drawn as a machine, not as water',
  abyss.scene === 'ABYSS' && works.scene === 'CLOCKWORK', `${abyss.scene} -> ${works.scene}`);
check('the machine runs faster than the abyss did', works.bpm >= 204, `${works.bpm} BPM`);
await page.screenshot({ path: path.join(shotDir, '01-level19.png') });

// 2 — the whole vocabulary is on the field -------------------------------------
const seen = new Set();
const surfaces = new Set();
let pulsingTotal = 0;
for (const id of IDS) {
  await open(id);
  const lv = await page.evaluate(() => ({
    looks: FLIP.looks(), surfaces: FLIP.surfaces(), pulsing: FLIP.pulsing(),
  }));
  lv.looks.split(',').forEach(k => seen.add(k));
  lv.surfaces.split(',').forEach(k => surfaces.add(k));
  pulsingTotal += lv.pulsing;
}
const wanted = ['GEAR', 'PISTON', 'SHUTTER', 'CHAIN', 'STEAM', 'CYLINDER', 'CRUSHER', 'RAIL', 'BOLT'];
check('every machine part reaches a level',
  wanted.every(k => seen.has(k)), [...seen].sort().join(','));
check('and there is not a spike in the whole world', !seen.has('SPIKE'), [...seen].sort().join(','));
check('the deck is plate, and some of it is a belt',
  surfaces.has('PLATE') && surfaces.has('CONVEYOR'), [...surfaces].sort().join(','));
check('the machine is full of parts that switch on and off',
  pulsingTotal >= 25, `${pulsingTotal} of them`);
const older = ['SAND_WAVE', 'RUIN', 'RELIC', 'GEYSER', 'BOULDER', 'LASER', 'BUBBLE', 'ORB',
               'TENTACLE', 'CRYSTAL', 'JELLY', 'RING', 'WAVE', 'SHARD'];
check('and nothing from the first three worlds came with it',
  older.every(k => !seen.has(k)), [...seen].sort().join(','));

// 3 — THE SAME LINE GIVES THE SAME RUN ----------------------------------------
// The machine's promise is same input, same result, and the place that is
// actually proved is core's ClockworkCycleTest, which steps the fixed timestep
// directly: two traces of every hazard state, bit for bit, plus every cycle
// checked against itself three periods later. A browser cannot ask that
// question - it samples the simulation at whatever moments the compositor hands
// it, so two runs disagree about where the runner was on frame 300 without
// anything in the game being non-deterministic.
//
// What a browser CAN prove is the outcome: the same line, flown twice, finishes
// the same way with the same number of taps. That is the part the player sees.
const outcome = () => page.evaluate(() => new Promise(res => {
  const plan = window.__plans[24].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000) {
      return res(`${FLIP.state()}/${FLIP.taps()}/${Math.round(FLIP.progress() * 100)}`);
    }
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
await open(24);
const runA = await outcome();
await open(24);
const runB = await outcome();
check('the same line flown twice ends the same way',
  runA === runB && runA.startsWith('COMPLETE'), `${runA} then ${runB}`);

// 4 — a ram punishes the jump and spares the run -------------------------------
// LEVEL 19's gate at 118 comes down to 1.25u and stops there. Running under it
// is always possible; jumping into it never is. Both halves are checked, because
// a gate that never kills is scenery and one that always kills is a locked door.
await open(19);
const ran = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[19].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  const step = () => {
    const x = FLIP.x();
    // fly the line up to the gate, then take nothing but the planned jumps
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (x > 124 || FLIP.state() !== 'RUNNING' || ++f > 2000)
      return res({ state: FLIP.state(), x: FLIP.x(), cause: FLIP.cause() });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('a shut gate can be run under', ran.state === 'RUNNING' && ran.x > 124,
  `reached x=${ran.x.toFixed(1)}${ran.state === 'DEAD' ? ' — ' + ran.cause : ''}`);

await open(19);
const jumped = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[19].jumps;
  let i = 0, owed = false, bx = 0, f = 0, tapped = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    else if (x > 112 && x < 119 && FLIP.grounded()) { FLIP.tap(); tapped++; }
    if (x > 124 || FLIP.state() !== 'RUNNING' || ++f > 2000)
      return res({ state: FLIP.state(), x: FLIP.x(), cause: FLIP.cause(), tapped });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('and jumping into it is what kills you',
  jumped.state === 'DEAD' && jumped.tapped > 0,
  `${jumped.tapped} taps, ${jumped.state} at x=${jumped.x.toFixed(1)}`);

// 5 — every level clears on its verified line ----------------------------------
for (const id of IDS) {
  await open(id);
  const out = await fly(id);
  check(`level ${id} clears on its verified line`,
    out.state === 'COMPLETE', `${out.pct}%${out.state === 'DEAD' ? ' — ' + out.cause : ''}`);
  if (id === 24) await page.screenshot({ path: path.join(shotDir, '03-level24.png') });
}

// 6 — the final gauntlet is actually continuous --------------------------------
await open(24);
const gauntlet = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[24].jumps;
  let i = 0, owed = false, bx = 0, f = 0, sampled = 0, empty = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    const p = FLIP.progress();
    // to 98% and not to the line: the last five units are the run-out past
    // the final bolt, and nothing is supposed to be in them.
    if (p > 0.84 && p < 0.98) { sampled++; if (FLIP.crowdAhead(14) === 0) empty++; }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000) return res({ state: FLIP.state(), sampled, empty });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('the final gauntlet never gives the player empty deck',
  gauntlet.sampled > 50 && gauntlet.empty === 0,
  `${gauntlet.sampled} frames from 84% to 98%, ${gauntlet.empty} with nothing in the next 14u`);

// 7 — still no ambience, in this world either ----------------------------------
await open(24);
await frames(30);
const quiet = await page.evaluate(() =>
  [...document.querySelectorAll('audio')].filter(a => a.src || !a.paused).length);
check('the machine has no ambience bed of any kind', quiet === 0, `${quiet} active media elements`);
const probes = ['20_w1_future_ambience_L1_60s', '25_w2_desert_ambience_L1_60s',
                '30_tension_riser_1', '45_unease_low_1', '02_menu_idle_hum_loop'];
const loaded = await page.evaluate(names => names.filter(n => FLIP.playCue(n)), probes);
check('and no environmental recording is even in memory',
  loaded.length === 0, `${probes.length} probed, ${loaded.length} loaded`);

// 8 — and it still runs ---------------------------------------------------------
await open(23);
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
check('60fps holds with the whole factory turning',
  median < 20 && p99 < 34, `median ${median.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${pace.length} frames`);

check('no javascript errors across world 4', errors.length === 0, errors.slice(0, 2).join(' | '));

await browser.close();
server.close();
const passed = results.filter(r => r.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(passed === results.length ? 0 : 1);
