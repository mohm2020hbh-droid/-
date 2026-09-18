/**
 * WORLD 3 harness for FLIP ERROR.
 *
 * The deserttest proves world 2 is a different PLACE. This one has a harder job,
 * because world 3's claim is not that it looks different - it is that it asks a
 * different QUESTION. Worlds 1 and 2 answer every obstacle by being in the air
 * or not being in the air; the bubble wall is answered by being at a PARTICULAR
 * HEIGHT, and if that is not true in the running game then world 3 really is
 * world 2 in blue.
 *
 * So this harness asks, in order: is it somewhere else, is there a single spike
 * left in it, is its whole vocabulary actually on the field, does the bubble
 * wall genuinely spare the jump and genuinely punish the second tap, does every
 * level clear on the line the verifier proved, is there any quiet floor in the
 * finale, is there still no ambience anywhere in it, and does it hold 60fps.
 *
 *   node tools/abysstest.mjs [--headed] [--shots DIR]
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
  : path.join(root, 'build/abysstest');
fs.mkdirSync(shotDir, { recursive: true });

const IDS = [13, 14, 15, 16, 17, 18];
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

/** Fly a level's verified line and report how it ended. */
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

// 1 — the abyss is somewhere else ---------------------------------------------
await open(12);
const desert = await page.evaluate(() => ({ world: FLIP.world(), scene: FLIP.scene() }));
await open(13);
const abyss = await page.evaluate(() => ({
  world: FLIP.world(), scene: FLIP.scene(), looks: FLIP.looks(), bpm: FLIP.bpm(),
}));
check('level 13 is a third world', abyss.world === 3 && desert.world === 2,
  `world ${desert.world} then ${abyss.world}`);
check('and it is drawn as deep water, not as a desert',
  desert.scene === 'DESERT' && abyss.scene === 'ABYSS', `${desert.scene} -> ${abyss.scene}`);
check('the abyss runs faster than the desert did', abyss.bpm >= 190, `${abyss.bpm} BPM`);
await page.screenshot({ path: path.join(shotDir, '01-level13-abyss.png') });

// 2 — the whole vocabulary is actually on the field ---------------------------
const seen = new Set();
const surfaces = new Set();
let pulsingTotal = 0;
for (const id of IDS) {
  await open(id);
  const lv = await page.evaluate(() => ({
    looks: FLIP.looks(), surfaces: FLIP.surfaces(),
    pulsing: FLIP.pulsing(),
  }));
  lv.looks.split(',').forEach(k => seen.add(k));
  lv.surfaces.split(',').forEach(k => surfaces.add(k));
  pulsingTotal += lv.pulsing;
}
// Bubbles, orbs, arms, crystals, jellies, rings and swells. Every one of these
// is a shape worlds 1 and 2 do not contain.
const wanted = ['BUBBLE', 'ORB', 'TENTACLE', 'CRYSTAL', 'JELLY', 'RING', 'WAVE'];
check('every abyss obstacle type reaches a level',
  wanted.every(k => seen.has(k)), [...seen].sort().join(','));
// The world's flattest claim, and the easiest one to break by accident: there is
// not one spike in it. A spike in the abyss is world 2 with a filter on.
check('and there is not a single spike in the whole world',
  !seen.has('SPIKE'), [...seen].sort().join(','));
check('the floor itself is made of bubbles somewhere',
  surfaces.has('BUBBLE'), [...surfaces].sort().join(','));
// Arms, jellies and both halves of every split: the things that are only
// dangerous for part of their cycle, and therefore the things that have to
// telegraph. LEVEL 13 has none by design - it teaches three shapes that are
// always there - so this counts the world, not each level.
check('the abyss is full of things that switch on and off',
  pulsingTotal >= 30, `${pulsingTotal} of them`);
// Nothing from world 2 leaked in either.
const desertOnly = ['SAND_WAVE', 'RUIN', 'RELIC', 'GEYSER', 'BOULDER', 'LASER'];
check('and nothing from the desert came with it',
  desertOnly.every(k => !seen.has(k)), [...seen].sort().join(','));

// 3 — the new question: an obstacle answered by a PARTICULAR HEIGHT ----------
// LEVEL 16's first bubble wall stands at x=64 with an opening from 1.7 to 4.2,
// which is head height for a single jump and under the floor of a double one.
// Both halves are checked here, because only the pair proves the mechanic: a
// wall the verified line sails through is not an obstacle, and a wall a second
// tap also sails through is not the obstacle this world was built around.
const WALL_X = 64;
await open(16);
const through = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[16].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (x > 72 || FLIP.state() !== 'RUNNING' || ++f > 2000)
      return res({ state: FLIP.state(), x: FLIP.x(), cause: FLIP.cause() });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('the opening in a bubble wall can be flown through',
  through.state === 'RUNNING' && through.x > 72,
  `reached x=${through.x.toFixed(1)}${through.state === 'DEAD' ? ' — ' + through.cause : ''}`);
await page.screenshot({ path: path.join(shotDir, '02-level16-wall.png') });

await open(16);
const doubled = await page.evaluate(wallX => new Promise(res => {
  const plan = window.__plans[16].jumps;
  let i = 0, owed = false, bx = 0, f = 0, extra = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    // The one difference: a second tap on the way INTO the wall. This is the
    // player who has learned that when in doubt you go higher.
    else if (!extra && x > wallX - 4 && x < wallX && !FLIP.grounded() && FLIP.canDouble()) {
      FLIP.tap(); extra++;
    }
    if (x > 72 || FLIP.state() !== 'RUNNING' || ++f > 2000)
      return res({ state: FLIP.state(), x: FLIP.x(), cause: FLIP.cause(), extra });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}), WALL_X);
check('and going higher through the same opening is what kills you',
  doubled.state === 'DEAD' && doubled.extra > 0,
  `${doubled.extra} extra tap(s), ${doubled.state} at x=${doubled.x.toFixed(1)}`);

// 4 — every abyss level clears on its verified line ---------------------------
for (const id of IDS) {
  await open(id);
  const out = await fly(id);
  check(`level ${id} clears on its verified line`,
    out.state === 'COMPLETE', `${out.pct}%${out.state === 'DEAD' ? ' — ' + out.cause : ''}`);
  if (id === 18) await page.screenshot({ path: path.join(shotDir, '03-level18-gauntlet.png') });
}

// 5 — and the finale really is a gauntlet -------------------------------------
// LEVEL 18's last twelfth is meant to have no quiet floor in it. That is a
// measurable claim - something is inside the next twelve units the whole way to
// the line - so it is measured here rather than asserted in a comment.
await open(18);
const gauntlet = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[18].jumps;
  let i = 0, owed = false, bx = 0, f = 0, sampled = 0, empty = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (FLIP.progress() > 0.88) { sampled++; if (FLIP.crowdAhead(12) === 0) empty++; }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000)
      return res({ state: FLIP.state(), sampled, empty });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('the final gauntlet never gives the player empty floor',
  gauntlet.sampled > 60 && gauntlet.empty === 0,
  `${gauntlet.sampled} frames past 88%, ${gauntlet.empty} with nothing in the next 12u`);

// 6 — still no ambience, in this world either ---------------------------------
await open(18);
await frames(30);
const quiet = await page.evaluate(() =>
  [...document.querySelectorAll('audio')].filter(a => a.src || !a.paused).length);
check('the abyss has no ambience bed of any kind', quiet === 0, `${quiet} active media elements`);
// And the environmental half of the pack is not merely silent - it is not there.
const probes = ['20_w1_future_ambience_L1_60s', '25_w2_desert_ambience_L1_60s',
                '30_tension_riser_1', '45_unease_low_1', '02_menu_idle_hum_loop'];
const loaded = await page.evaluate(names => names.filter(n => FLIP.playCue(n)), probes);
check('and no environmental recording is even in memory',
  loaded.length === 0, `${probes.length} probed, ${loaded.length} loaded`);

// 7 — and it still runs --------------------------------------------------------
await open(18);
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
check('60fps holds with the whole abyss moving',
  median < 20 && p99 < 34, `median ${median.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${pace.length} frames`);

check('no javascript errors across world 3', errors.length === 0, errors.slice(0, 2).join(' | '));

await browser.close();
server.close();
const passed = results.filter(r => r.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(passed === results.length ? 0 : 1);
