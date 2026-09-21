/**
 * Every level, flown at 60fps on the line the solver proved, in a real browser.
 *
 * The verifier runs at 240Hz. A player runs at 60. This is the harness that
 * asks whether those two agree - and it is the one that decides whether a
 * reported "impossible bit at 30%" is a level bug, an engine bug, or a
 * frame-rate bug, because it reports the x and the cause of every death.
 *
 *   node tools/runall.mjs [--headed] [--levels 4,5]
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dist = path.join(root, 'web/build/dist/js/productionExecutable');
const only = process.argv.includes('--levels')
  ? process.argv[process.argv.indexOf('--levels') + 1].split(',').map(Number)
  // Every level there is. Spelled out rather than generated, so adding a
  // world means touching this line - a harness that quietly tests eighteen
  // of thirty levels and reports 18/18 is worse than no harness.
  : [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30];
const shotDir = path.join(root, 'build/runall');
fs.mkdirSync(shotDir, { recursive: true });

const plans = {};
for (const id of only) {
  const f = path.join(root, `core/build/level${id}-plan.json`);
  if (!fs.existsSync(f)) { console.error(`missing ${f}`); process.exit(1); }
  plans[id] = JSON.parse(fs.readFileSync(f, 'utf8'));
}

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json' };
const server = http.createServer((req, res) => {
  const a = req.url.split('?')[0];
  const f = path.join(dist, a === '/' ? 'index.html' : a);
  if (a === '/favicon.ico') { res.writeHead(204); return res.end(); }
  if (!f.startsWith(dist) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': MIME[path.extname(f)] ?? 'application/octet-stream' });
  res.end(fs.readFileSync(f));
});
await new Promise(r => server.listen(0, r));
const url = `http://127.0.0.1:${server.address().port}/`;

const browser = await chromium.launch({
  headless: !process.argv.includes('--headed'),
  executablePath: fs.existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined,
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
await page.waitForFunction(() => window.FLIP && typeof window.FLIP.play === 'function', { timeout: 10000 });
await page.evaluate(() => { FLIP.wipe(); FLIP.setting('unlockAll', true); });

let bad = 0;
for (const id of only) {
  await page.evaluate(i => FLIP.play(i), id);
  await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
  const out = await page.evaluate(lv => new Promise(res => {
    const plan = window.__plans[lv].jumps;
    let i = 0, owed = false, bx = 0, f = 0;
    const step = () => {
      const x = FLIP.x();
      // A planned take-off is taken FROM THE GROUND; a planned boost in the air.
      if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
        owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
      } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
      if (FLIP.state() !== 'RUNNING' || ++f > 4000) {
        return res({ state: FLIP.state(), pct: FLIP.progress() * 100, x: FLIP.x(),
                     cause: FLIP.cause(), used: i, of: plan.length, stars: FLIP.stars() });
      }
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }), id);
  const ok = out.state === 'COMPLETE';
  if (!ok) { bad++; await page.screenshot({ path: path.join(shotDir, `L${id}-died.png`) }); }
  console.log(`${ok ? 'PASS' : 'FAIL'}  LEVEL ${String(id).padStart(2)}  ` +
    `${out.pct.toFixed(1).padStart(5)}%  ${out.state}` +
    (ok ? '' : `  ${out.cause} at x=${out.x.toFixed(1)}  (jump ${out.used}/${out.of})`));
}
console.log(errors.length ? `\nJS errors: ${errors.slice(0, 3).join(' | ')}` : '\nno javascript errors');
await browser.close(); server.close();
console.log(`${only.length - bad}/${only.length} levels cleared at 60fps`);
process.exit(bad === 0 ? 0 : 1);
