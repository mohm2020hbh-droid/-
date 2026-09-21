/**
 * WORLD 5 harness for FLIP ERROR.
 *
 * The machine's claim was that it repeats. The forest's claim is the opposite
 * one: that a thing which is ALIVE still has to warn you. Every plant in world
 * 5 moves on its own, and the only reason that is a timing problem rather than
 * a coin flip is the moment of swelling before it strikes. So the check this
 * harness exists for is the telegraph: nothing in the forest goes lethal
 * without having visibly warmed up first.
 *
 * Then: is it somewhere else, is its whole vocabulary on the field, is there
 * anything of the machine left in it, does the same line flown twice end the
 * same way, does every level clear on the line the verifier proved, does the
 * last world stay on top of the player through the overgrowth AND then actually
 * go quiet for the Heart, is there still no ambience, and does it hold 60fps
 * with more moving parts than any world before it.
 *
 *   node tools/foresttest.mjs [--headed] [--shots DIR]
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
  : path.join(root, 'build/foresttest');
fs.mkdirSync(shotDir, { recursive: true });

const IDS = [25, 26, 27, 28, 29, 30];
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

// 1 — the forest is somewhere else ---------------------------------------------
await open(24);
const machine = await page.evaluate(() => ({ world: FLIP.world(), scene: FLIP.scene() }));
await open(25);
const forest = await page.evaluate(() => ({
  world: FLIP.world(), scene: FLIP.scene(), looks: FLIP.looks(), bpm: FLIP.bpm(),
}));
check('level 25 is a fifth world', forest.world === 5 && machine.world === 4,
  `world ${machine.world} then ${forest.world}`);
check('and it is drawn as a forest, not as machinery',
  machine.scene === 'CLOCKWORK' && forest.scene === 'FOREST', `${machine.scene} -> ${forest.scene}`);
check('the forest runs at least as fast as the machine did', forest.bpm >= 204, `${forest.bpm} BPM`);
await page.screenshot({ path: path.join(shotDir, '01-level25.png') });

// 2 — the whole vocabulary is on the field --------------------------------------
const seen = new Set();
const surfaces = new Set();
// Counted the way the ladder test counts it: a part is MOVING if it travels or
// if it switches on and off. Both totals are measured here rather than compared
// against a number written into the test, because a hardcoded threshold is a
// claim about world 4 that stops being true the moment world 4 changes.
const partsIn = async ids => {
  let n = 0;
  for (const id of ids) {
    await open(id);
    n += await page.evaluate(() => FLIP.movers() + FLIP.pulsing());
  }
  return n;
};
for (const id of IDS) {
  await open(id);
  const lv = await page.evaluate(() => ({ looks: FLIP.looks(), surfaces: FLIP.surfaces() }));
  lv.looks.split(',').forEach(k => seen.add(k));
  lv.surfaces.split(',').forEach(k => surfaces.add(k));
}
const wanted = ['FLOWER', 'VINE', 'ROOT', 'THORN', 'SPORE', 'SEED', 'BLOOM', 'PULSE'];
check('every living part reaches a level',
  wanted.every(k => seen.has(k)), [...seen].sort().join(','));
check('and there is not a spike in the whole world', !seen.has('SPIKE'), [...seen].sort().join(','));
check('the floor is moss', surfaces.has('MOSS'), [...surfaces].sort().join(','));
const older = ['SAND_WAVE', 'RUIN', 'RELIC', 'GEYSER', 'BOULDER', 'LASER', 'BUBBLE', 'ORB',
               'TENTACLE', 'CRYSTAL', 'JELLY', 'RING', 'WAVE', 'SHARD',
               'GEAR', 'PISTON', 'SHUTTER', 'CHAIN', 'STEAM', 'CYLINDER', 'CRUSHER', 'RAIL', 'BOLT'];
check('and nothing from the first four worlds came with it',
  older.every(k => !seen.has(k)), [...seen].sort().join(','));
const forestParts = await partsIn(IDS);
const machineParts = await partsIn([19, 20, 21, 22, 23, 24]);
check('the forest has more moving parts than the machine did',
  forestParts > machineParts, `${forestParts} against the machine's ${machineParts}`);

// 3 — NOTHING GOES LETHAL WITHOUT WARNING --------------------------------------
// The one claim world 5 lives on. Every plant that switches on and off is
// sampled across a whole run of LEVEL 29: each frame, how many are live and how
// many are visibly swelling. If the live count ever rises without anything
// having warmed in the frames just before, the forest bit someone who had no
// way of reading it.
await open(29);
const telegraph = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[29].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  let prevLive = FLIP.pulsingLive(), sinceWarm = 99, rises = 0, unwarned = 0, warmFrames = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    const warm = FLIP.pulsingWarm(), live = FLIP.pulsingLive();
    if (warm > 0) { warmFrames++; sinceWarm = 0; } else sinceWarm++;
    if (live > prevLive) { rises++; if (sinceWarm > 12) unwarned++; }
    prevLive = live;
    if (FLIP.state() !== 'RUNNING' || ++f > 4000)
      return res({ state: FLIP.state(), rises, unwarned, warmFrames, frames: f });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('something in the forest is always visibly swelling',
  telegraph.warmFrames > 100, `${telegraph.warmFrames} of ${telegraph.frames} frames`);
check('and nothing ever turns lethal unannounced',
  telegraph.rises > 10 && telegraph.unwarned === 0,
  `${telegraph.rises} strikes, ${telegraph.unwarned} with no warning`);

// 4 — the same line gives the same run ------------------------------------------
// Proved properly in core, where the fixed timestep can be stepped directly.
// What a browser can prove is the half the player sees: fly the same plan twice
// and get the same ending.
const outcome = () => page.evaluate(() => new Promise(res => {
  const plan = window.__plans[30].jumps;
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
await open(30);
const runA = await outcome();
await open(30);
const runB = await outcome();
check('the same line flown twice ends the same way',
  runA === runB && runA.startsWith('COMPLETE'), `${runA} then ${runB}`);

// 5 — every level clears on its verified line ------------------------------------
for (const id of IDS) {
  await open(id);
  const out = await fly(id);
  check(`level ${id} clears on its verified line`,
    out.state === 'COMPLETE', `${out.pct}%${out.state === 'DEAD' ? ' — ' + out.cause : ''}`);
  if (id === 30) await page.screenshot({ path: path.join(shotDir, '03-level30.png') });
}

// 6 — the overgrowth closes in, and then the Heart lets go ------------------------
// Two halves of the same check, because either one alone is a different game.
// From 78% to 94% LEVEL 30 is supposed to be continuous - no empty deck - and
// from 97% it is supposed to be completely still, which is the only rest the
// last level of the game ever gives.
await open(30);
const ending = await page.evaluate(() => new Promise(res => {
  const plan = window.__plans[30].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  let dense = 0, empty = 0, calm = 0, noisy = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    const p = FLIP.progress();
    if (p > 0.78 && p < 0.94) { dense++; if (FLIP.crowdAhead(14) === 0) empty++; }
    if (p > 0.97) { calm++; if (FLIP.crowdAhead(10) > 0) noisy++; }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000)
      return res({ state: FLIP.state(), dense, empty, calm, noisy });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('the overgrowth never gives the player empty floor',
  ending.dense > 50 && ending.empty === 0,
  `${ending.dense} frames from 78% to 94%, ${ending.empty} with nothing in the next 14u`);
check('and then the Heart is genuinely still',
  ending.calm > 10 && ending.noisy === 0,
  `${ending.calm} frames past 97%, ${ending.noisy} with anything left in front`);

// 7 — still no ambience, in this world either -------------------------------------
await open(30);
await frames(30);
const quiet = await page.evaluate(() =>
  [...document.querySelectorAll('audio')].filter(a => a.src || !a.paused).length);
check('the forest has no ambience bed of any kind', quiet === 0, `${quiet} active media elements`);
const probes = ['20_w1_future_ambience_L1_60s', '25_w2_desert_ambience_L1_60s',
                '30_tension_riser_1', '45_unease_low_1', '02_menu_idle_hum_loop'];
const loaded = await page.evaluate(names => names.filter(n => FLIP.playCue(n)), probes);
check('and no environmental recording is even in memory',
  loaded.length === 0, `${probes.length} probed, ${loaded.length} loaded`);

// 8 — and it still runs -------------------------------------------------------------
await open(29);
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
check('60fps holds with the whole forest moving',
  median < 20 && p99 < 34, `median ${median.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${pace.length} frames`);

check('no javascript errors across world 5', errors.length === 0, errors.slice(0, 2).join(' | '));

await browser.close();
server.close();
const passed = results.filter(r => r.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(passed === results.length ? 0 : 1);
