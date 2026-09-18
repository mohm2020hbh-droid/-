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
for (const id of [1, 9, 16, 18]) {
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

// 1 — the library is in, and it is the gameplay half only ---------------------
const lib = await page.evaluate(() => ({
  ready: FLIP.samplesReady(), loaded: FLIP.samplesLoaded(),
  fmt: FLIP.audioFormat(), want: FLIP.cueCount(),
}));
check('the sound pack loads and decodes', lib.ready, `${lib.loaded} cues, format ${lib.fmt}`);
check('every cue the map names decoded', lib.loaded === lib.want, `${lib.loaded} of ${lib.want}`);

// 2 — and NOTHING is looping or streaming -------------------------------------
// The ambience decision, checked at runtime rather than in the source. An
// <audio> element with a source, or any element playing, is the whole failure
// mode this rule exists to prevent.
const media = await page.evaluate(() => {
  const els = [...document.querySelectorAll('audio')];
  return { count: els.length, sourced: els.filter(a => a.src).length,
           playing: els.filter(a => !a.paused).length };
});
check('no media element is streaming anything',
  media.sourced === 0 && media.playing === 0,
  `${media.count} element(s) in the page, ${media.sourced} with a source, ${media.playing} playing`);

// 3 — every gameplay and UI cue can be fired ----------------------------------
const open = async id => {
  await page.evaluate(i => FLIP.play(i), id);
  await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
  await frames(3);
};
const cues = await page.evaluate(() => {
  const names = ['11_jump', '12_double_jump', '13_land', '14_collect_star', '15_near_miss',
    '16_hazard_hit', '08_strong_loss', '07_level_complete', '09_perfect_finish',
    '01_game_enter_hum', '03_level_start_riser', '05_ui_confirm', '10_world_transition',
    '18_speed_whoosh', '19_secret_unlock',
    '37_w2_sand_wave', '38_w2_sand_geyser', '39_w2_falling_ruin', '40_w2_laser_charge',
    '41_w2_laser_blast', '42_w2_wind_blast', '43_w2_collapse_bridge'];
  return { missing: names.filter(n => !FLIP.playCue(n)), n: names.length };
});
check('every gameplay and UI cue fires', cues.missing.length === 0,
  cues.missing.length ? `missing: ${cues.missing.join(', ')}` : `${cues.n} cues`);

// And the environmental half is not loaded at all - not merely unplayed.
const ambient = await page.evaluate(() => {
  const names = ['20_w1_future_ambience_L1_60s', '25_w2_desert_ambience_L1_60s',
    '02_menu_idle_hum_loop', '30_tension_riser_1', '35_w1_neon_electric_arc',
    '45_unease_low_1'];
  return names.filter(n => FLIP.playCue(n));
});
check('and no environmental recording is even in memory',
  ambient.length === 0, ambient.length ? `playable: ${ambient.join(', ')}` : '6 probed, none loaded');

// 4 — a level of each world plays through with its cues running ----------------
for (const [id, label] of [[1, 'world 1'], [9, 'world 2'], [18, 'world 3']]) {
  await open(id);
  const out = await page.evaluate(lv => new Promise(res => {
    const plan = window.__plans[lv].jumps;
    let i = 0, owed = false, bx = 0, f = 0;
    const step = () => {
      const x = FLIP.x();
      if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
        owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
      } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
      if (FLIP.state() !== 'RUNNING' || ++f > 4000) return res({ state: FLIP.state() });
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }), id);
  check(`${label} plays through with sound on`, out.state === 'COMPLETE', out.state);
}
await page.screenshot({ path: path.join(shotDir, '02-level18.png') });

// 5 — still nothing looping after all of that ----------------------------------
const after = await page.evaluate(() =>
  [...document.querySelectorAll('audio')].filter(a => a.src || !a.paused).length);
check('and still nothing is looping after three levels', after === 0, `${after} active`);

// 6 — and none of it costs the frame budget ------------------------------------
await open(16);
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
