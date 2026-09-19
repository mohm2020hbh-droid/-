/**
 * Is any jump in this game BLIND?
 *
 * The solver proves a level is possible; it says nothing about whether the
 * player can see what they are being asked to do. A take-off whose landing sits
 * past the right edge of the screen is a jump into the dark - the window can be
 * a third of a second wide and it will still read as an impossible wall,
 * because the only way to find it is to die there first and memorise it.
 *
 *   node tools/blindtest.mjs [--levels 4,5]
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
  // of twenty-four levels and reports 18/18 is worse than no harness.
  : [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24];
const plans = {};
for (const id of only)
  plans[id] = JSON.parse(fs.readFileSync(path.join(root, `core/build/level${id}-plan.json`), 'utf8'));

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
  headless: true,
  executablePath: fs.existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined,
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding'],
});
// The narrowest shape a phone in landscape actually is: least room ahead.
const page = await browser.newPage({ viewport: { width: 740, height: 360 }, deviceScaleFactor: 1 });
await page.addInitScript(p => { window.__plans = p; }, plans);
await page.goto(url, { waitUntil: 'load' });
await page.waitForFunction(() => window.FLIP && typeof window.FLIP.gapAheadX === 'function');
await page.evaluate(() => { FLIP.wipe(); FLIP.setting('unlockAll', true); });

let blind = 0, total = 0;
for (const id of only) {
  await page.evaluate(i => FLIP.play(i), id);
  await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
  const out = await page.evaluate(lv => new Promise(res => {
    const plan = window.__plans[lv].jumps;
    const seen = [];
    let i = 0, owed = false, bx = 0, f = 0;
    const step = () => {
      const x = FLIP.x();
      if (i < plan.length && x >= plan[i].x && FLIP.grounded()) {
        // At the instant of commitment: where is the next ground, and can it be seen?
        seen.push({ x, ahead: FLIP.aheadUnits(), land: FLIP.gapAheadX(),
                    boosted: plan[i].boosted, pct: FLIP.progress() * 100 });
        owed = plan[i].boosted; bx = plan[i].boostX; i++; FLIP.tap();
      } else if (owed && FLIP.canDouble() && x >= bx) { FLIP.tap(); owed = false; }
      if (FLIP.state() !== 'RUNNING' || ++f > 4000) return res({ seen, state: FLIP.state() });
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }), id);

  for (const s of out.seen) {
    if (s.land < 0) continue;
    total++;
    const margin = (s.x + s.ahead) - s.land;      // how far past the screen edge the landing sits
    if (margin < 0) {
      blind++;
      console.log(`BLIND  LEVEL ${String(id).padStart(2)} @ ${s.pct.toFixed(1)}%  ` +
        `take-off x=${s.x.toFixed(1)}  next ground x=${s.land.toFixed(1)}  ` +
        `screen edge x=${(s.x + s.ahead).toFixed(1)}  short by ${(-margin).toFixed(2)}u` +
        (s.boosted ? '  [needs a boost]' : ''));
    }
  }
}
console.log(`\n${total - blind}/${total} committed jumps had their landing on screen`);
await browser.close(); server.close();
process.exit(blind === 0 ? 0 : 1);
