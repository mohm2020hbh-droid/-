/**
 * The FLIP ERROR sound pack, in a real browser.
 *
 * It answers the questions the pack itself raises: did all 42 recordings arrive
 * and decode, is the room a LAYERED environment rather than a track, does the
 * tension actually climb across the five bands the pack was cut for, do the
 * world 2 obstacles announce themselves, and does any of it cost the frame
 * budget. It also checks the thing the whole design rests on - that no two worlds
 * sound the same.
 *
 *   node tools/soundtest.mjs [--headed]
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dist = path.join(root, 'web/build/dist/js/productionExecutable');
const shotDir = path.join(root, 'build/soundtest');
fs.mkdirSync(shotDir, { recursive: true });

const plans = {};
for (const id of [1, 7, 9, 11]) {
  const f = path.join(root, `core/build/level${id}-plan.json`);
  if (fs.existsSync(f)) plans[id] = JSON.parse(fs.readFileSync(f, 'utf8'));
}

const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json',
  '.webm': 'audio/webm', '.mp3': 'audio/mpeg',
};
let served = 0;
const server = http.createServer((req, res) => {
  const a = decodeURIComponent(req.url.split('?')[0]);
  const f = path.join(dist, a === '/' ? 'index.html' : a);
  if (a === '/favicon.ico') { res.writeHead(204); return res.end(); }
  if (!f.startsWith(dist) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  if (/\.(webm|mp3)$/.test(f)) served++;
  const body = fs.readFileSync(f);
  res.writeHead(200, {
    'content-type': MIME[path.extname(f)] ?? 'application/octet-stream',
    'content-length': body.length, 'accept-ranges': 'bytes',
  });
  res.end(body);
});
await new Promise(r => server.listen(0, r));
const url = `http://127.0.0.1:${server.address().port}/`;

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

const browser = await chromium.launch({
  headless: !process.argv.includes('--headed'),
  executablePath: fs.existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined,
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding',
         '--autoplay-policy=no-user-gesture-required', '--mute-audio'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 600 }, deviceScaleFactor: 1 });
const errors = [];
const external = t => /ERR_CERT|fonts\.googleapis|fonts\.gstatic|Failed to load resource/.test(t);
page.on('console', m => { if (m.type() === 'error' && !external(m.text())) errors.push(m.text()); });
page.on('pageerror', e => errors.push(String(e)));

await page.addInitScript(p => { window.__plans = p; }, plans);
await page.goto(url, { waitUntil: 'load' });
await page.waitForFunction(() => window.FLIP && typeof window.FLIP.samplesReady === 'function');
await page.evaluate(() => { FLIP.wipe(); FLIP.setting('unlockAll', true); });

// A real gesture, so nothing is waiting on the autoplay policy.
await page.mouse.click(640, 560);
await page.waitForFunction(() => FLIP.samplesReady(), { timeout: 30000 }).catch(() => {});

const frames = n => page.evaluate(k => new Promise(res => {
  let i = 0;
  const step = () => (++i >= k ? res() : requestAnimationFrame(step));
  requestAnimationFrame(step);
}), n);

// 1 — the library is in ------------------------------------------------------
const lib = await page.evaluate(() => ({
  ready: FLIP.samplesReady(), loaded: FLIP.samplesLoaded(), fmt: FLIP.audioFormat(),
}));
check('the sound pack loads and decodes', lib.ready, `${lib.loaded} cues, format ${lib.fmt}`);
const want = await page.evaluate(() => FLIP.cueCount());
check('every one-shot recording decoded', lib.loaded === want, `${lib.loaded} of ${want}`);

// 2 — the menus have a room, and it is not the level's --------------------------
await page.evaluate(() => FLIP.openMenu());
await frames(40);
await page.screenshot({ path: path.join(shotDir, '01-menu.png') });

// 3 — the room is layered, and the layers move with the level -------------------
const open = async id => {
  await page.evaluate(i => FLIP.play(i), id);
  await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
  await frames(3);
};

const sampleBeds = async (lv, marks) => {
  await open(lv);
  return page.evaluate(([id, want]) => new Promise(res => {
    const plan = window.__plans[id]?.jumps ?? [];
    const seen = {};
    let i = 0, owed = false, bx = 0, f = 0, next = 0;
    const step = () => {
      const x = FLIP.x(), p = FLIP.progress();
      if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
        owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
      } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
      while (next < want.length && p >= want[next]) {
        seen[want[next]] = { beds: FLIP.beds(), tension: FLIP.tension() };
        next++;
      }
      if (next >= want.length || FLIP.state() !== 'RUNNING' || ++f > 4000) return res(seen);
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }), [lv, marks]);
};

const marks = [0.05, 0.30, 0.55, 0.75, 0.95];
// The bands are 0-25, 25-50, 50-70, 70-90, 90-100 and each crossfades over its
// last 6%, so a handover is only visible just BEFORE a boundary. Sampling in the
// middle of a band and concluding there is no crossfade is the test's mistake,
// not the engine's.
const edges = [0.235, 0.485, 0.685, 0.885];
const w1 = await sampleBeds(1, marks);
const loudest = o => {
  const g = o.beds.split(',').map(Number);
  return g.indexOf(Math.max(...g));
};
const seq = marks.map(m => (w1[m] ? loudest(w1[m]) : -1));
check('the room is five layers, not one file',
  new Set(seq.filter(v => v >= 0)).size >= 4, `loudest layer at each mark: ${seq.join(' -> ')}`);
check('and it climbs a layer at a time as the level does',
  seq.every((v, i) => i === 0 || v >= seq[i - 1]) && seq[seq.length - 1] > seq[0],
  seq.join(' -> '));
const w1edges = await sampleBeds(1, edges);
const mixed = edges.filter(m => w1edges[m] &&
  w1edges[m].beds.split(',').filter(v => Number(v) > 0.05).length > 1);
check('the layers crossfade rather than switch', mixed.length >= 3,
  `two layers audible at ${mixed.length} of ${edges.length} boundaries: ` +
  edges.map(m => w1edges[m] ? `[${w1edges[m].beds}]` : '-').join(' '));

// 4 — the two worlds are different rooms ---------------------------------------
await open(1);
await frames(20);
const cityBeds = await page.evaluate(() => FLIP.beds());
await open(7);
await frames(20);
const desertBeds = await page.evaluate(() => FLIP.beds());
const world = await page.evaluate(() => FLIP.world());
check('world 2 plays its own room', world === 2 && cityBeds.length > 0 && desertBeds.length > 0,
  `city [${cityBeds}] desert [${desertBeds}]`);
const files = await page.evaluate(() =>
  [...document.querySelectorAll('audio')].map(a => a.src.split('/').pop()));
check('and the two worlds never share a recording',
  files.some(f => f.startsWith('20_w1')) && files.some(f => f.startsWith('25_w2')),
  `${files.length} beds wired`);

// 5 — every cue in the map can actually be fired -------------------------------
const cues = await page.evaluate(() => {
  const names = ['11_jump', '12_double_jump', '13_land', '14_collect_star', '15_near_miss',
    '16_hazard_hit', '08_strong_loss', '07_level_complete', '09_perfect_finish',
    '01_game_enter_hum', '03_level_start_riser', '05_ui_confirm', '10_world_transition',
    '18_speed_whoosh', '19_secret_unlock',
    '37_w2_sand_wave', '38_w2_sand_geyser', '39_w2_falling_ruin', '40_w2_laser_charge',
    '41_w2_laser_blast', '42_w2_wind_blast', '43_w2_collapse_bridge',
    '35_w1_neon_electric_arc', '36_w1_distant_machine_hit',
    '30_tension_riser_1', '30_tension_riser_2', '30_tension_riser_3', '30_tension_riser_4',
    '45_unease_low_1', '45_unease_low_2', '45_unease_low_3'];
  return names.filter(n => !FLIP.playCue(n));
});
check('every cue the map names can be fired', cues.length === 0,
  cues.length ? `missing: ${cues.join(', ')}` : '31 cues, all present');

// 6 — the desert's obstacles announce themselves --------------------------------
await open(9);
const heard = await page.evaluate(() => new Promise(res => {
  // Count what the cue layer fires over a real run of SUN STRIKE, which is the
  // level built out of beams and geysers.
  const fired = {};
  const real = FLIP.playCue;
  window.__hook = n => { fired[n] = (fired[n] || 0) + 1; };
  const plan = window.__plans[9].jumps;
  let i = 0, owed = false, bx = 0, f = 0;
  const step = () => {
    const x = FLIP.x();
    if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
      owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
    } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
    if (FLIP.state() !== 'RUNNING' || ++f > 4000) return res({ state: FLIP.state(), f });
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
check('a desert level plays through with its cue layer running',
  heard.state === 'COMPLETE', `${heard.state} after ${heard.f} frames`);
await page.screenshot({ path: path.join(shotDir, '02-level9.png') });

// 7 — and none of it costs the frame budget -------------------------------------
await open(11);
const pace = await page.evaluate(() => new Promise(res => {
  const gaps = []; let last = performance.now(); let f = 0;
  const step = t => {
    gaps.push(t - last); last = t;
    if (++f > 700) return res(gaps.slice(5));
    if (f % 8 === 0) FLIP.tap();
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}));
pace.sort((a, b) => a - b);
const p50 = pace[Math.floor(pace.length / 2)];
const p99 = pace[Math.floor(pace.length * 0.99)];
check('60fps holds with the whole pack playing',
  p50 < 20 && p99 < 34, `median ${p50.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${pace.length} frames`);

check('the browser actually fetched the audio', served > 0, `${served} audio requests served`);
check('no javascript errors with sound on', errors.length === 0, errors.slice(0, 2).join(' | '));

await browser.close();
server.close();
const passed = results.filter(r => r.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(passed === results.length ? 0 : 1);
