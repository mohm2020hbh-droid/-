/**
 * Orientation-gate harness for the FLIP ERROR web build.
 *
 * The gate's whole job is to tell a portrait *phone* apart from a portrait
 * *frame*, so every case here is a real Chromium context shaped like one of the
 * screens that used to get it wrong: a phone upright, the same phone turned, a
 * rotated phone inside a tall embed panel, a narrow desktop window, a thumbnail.
 * Each one checks the decision, and — where the gate is supposed to lift —
 * that Level 1 is genuinely running behind it.
 *
 *   node tools/orientation.mjs [--headed] [--shots DIR]
 */
import { chromium, devices } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dist = path.join(root, 'web/build/dist/js/productionExecutable');
const shotDir = process.argv.includes('--shots')
  ? process.argv[process.argv.indexOf('--shots') + 1]
  : path.join(root, 'build/orientation');
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
const base = `http://127.0.0.1:${server.address().port}/`;

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

const external = t => /ERR_CERT|fonts\.googleapis|fonts\.gstatic|Failed to load resource/.test(t);
const jsErrors = [];

/** Open the game in a context shaped like one real screen. */
async function open(opts, { query = '' } = {}) {
  const ctx = await browser.newContext(opts);
  const page = await ctx.newPage();
  page.on('console', m => { if (m.type() === 'error' && !external(m.text())) jsErrors.push(m.text()); });
  page.on('pageerror', e => jsErrors.push(String(e)));
  await page.goto(base + query, { waitUntil: 'load' });
  await page.waitForFunction(() => window.FLIP && typeof window.FLIP.gated === 'function', { timeout: 10000 });
  return { ctx, page };
}

const gate = page => page.evaluate(() => ({
  gated: FLIP.gated(), reason: FLIP.gateReason(), probe: FLIP.probe(),
  state: FLIP.state(), viewUnits: FLIP.viewUnits(), uiHeight: FLIP.uiHeight(),
  overlay: !document.getElementById('rotate').hidden,
}));

/** Does the level actually run here — moving, drawing, and taking a jump? */
async function playsLevel1(page) {
  const x0 = await page.evaluate(() => FLIP.x());
  await page.evaluate(() => new Promise(res => {
    let i = 0; const go = () => (++i >= 30 ? res() : requestAnimationFrame(go)); requestAnimationFrame(go);
  }));
  const moved = (await page.evaluate(() => FLIP.x())) - x0;
  const jumped = await page.evaluate(() => new Promise(res => {
    FLIP.tap();
    requestAnimationFrame(() => res(!FLIP.grounded() && FLIP.vy() > 0));
  }));
  // A canvas that is one flat colour is a black screen, not a level.
  const painted = await page.evaluate(() => {
    const c = document.getElementById('c');
    const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
    const seen = new Set();
    for (let y = 0; y < c.height; y += 8)
      for (let x = 0; x < c.width; x += 8) {
        const i = (y * c.width + x) * 4;
        seen.add(`${d[i]},${d[i + 1]},${d[i + 2]}`);
      }
    return seen.size;
  });
  return { moved, jumped, painted };
}

const PIXEL_PORTRAIT = devices['Pixel 7'];
const PIXEL_LANDSCAPE = devices['Pixel 7 landscape'];

// A — the ordinary desktop/landscape case must be untouched by the rewrite.
{
  const { ctx, page } = await open({ viewport: { width: 1280, height: 600 } });
  const g = await gate(page);
  check('landscape window plays, and says why', !g.gated && g.reason === 'VIEWPORT_LANDSCAPE',
        `${g.reason} at ${g.probe.width}x${g.probe.height}`);
  check('landscape shows no rotate overlay', !g.overlay);
  const p = await playsLevel1(page);
  check('landscape enters Level 1 and runs', p.moved > 2 && p.jumped && p.painted > 20,
        `x +${p.moved.toFixed(1)}, jump ${p.jumped}, ${p.painted} colours drawn`);
  // 600/13 = 46.2 px per unit, 1280/46.2 = 27.7 units. The width floor must not bind.
  check('landscape framing is unchanged by the width floor',
        Math.abs(g.viewUnits - 1280 / (600 / 13)) < 0.01, `${g.viewUnits.toFixed(2)} units across`);
  check('landscape HUD is unchanged by the width floor',
        Math.abs(g.uiHeight - 600) < 0.01, `HUD sized against ${g.uiHeight.toFixed(1)}px`);
  await page.screenshot({ path: path.join(shotDir, 'a-landscape.png') });
  await ctx.close();
}

// B — a phone actually held upright: the one screen that earns the prompt.
{
  const { ctx, page } = await open(PIXEL_PORTRAIT);
  const g = await gate(page);
  check('upright phone is asked to rotate', g.gated && g.reason === 'HANDHELD_PORTRAIT',
        `${g.reason} at ${g.probe.width}x${g.probe.height}, device ${g.probe.device}`);
  check('upright phone sees the rotate overlay', g.overlay);
  await page.screenshot({ path: path.join(shotDir, 'b-portrait-gated.png') });
  await ctx.close();
}

// C — the same phone, turned.
{
  const { ctx, page } = await open(PIXEL_LANDSCAPE);
  const g = await gate(page);
  check('turned phone plays', !g.gated && g.reason === 'VIEWPORT_LANDSCAPE',
        `${g.reason} at ${g.probe.width}x${g.probe.height}`);
  const p = await playsLevel1(page);
  check('turned phone enters Level 1 and runs', p.moved > 2 && p.jumped && p.painted > 20,
        `x +${p.moved.toFixed(1)}, ${p.painted} colours drawn`);
  await page.screenshot({ path: path.join(shotDir, 'c-phone-landscape.png') });
  await ctx.close();
}

// D — THE REPORTED BUG. Phone rotated, host panel still a tall sliver. The old
//     viewport-only test trapped this screen behind the prompt forever.
{
  const { ctx, page } = await open({
    ...PIXEL_PORTRAIT, viewport: { width: 360, height: 700 },
    screen: { width: 915, height: 412 },
  });
  const g = await gate(page);
  check('rotated phone in a tall panel is never gated', !g.gated && g.reason === 'DEVICE_LANDSCAPE',
        `${g.reason}, frame ${g.probe.width}x${g.probe.height}, device ${g.probe.device}`);
  const p = await playsLevel1(page);
  check('tall panel still enters Level 1 and runs', p.moved > 2 && p.jumped && p.painted > 20,
        `x +${p.moved.toFixed(1)}, ${p.painted} colours drawn`);
  check('tall panel keeps the track readable', g.viewUnits >= 17.99,
        `${g.viewUnits.toFixed(1)} units across (floor 18)`);
  // A HUD sized off a tall frame's height overruns its own labels.
  check('tall panel shrinks the HUD instead of overrunning it', g.uiHeight < 700 * 0.75,
        `HUD sized against ${g.uiHeight.toFixed(0)}px in a 700px frame`);
  await page.screenshot({ path: path.join(shotDir, 'd-tall-panel.png') });
  await ctx.close();
}

// E — a narrow desktop window is the user's own choice.
{
  const { ctx, page } = await open({ viewport: { width: 520, height: 900 } });
  const g = await gate(page);
  check('narrow desktop window plays', !g.gated && g.reason === 'POINTER_NOT_HANDHELD',
        `${g.reason} at ${g.probe.width}x${g.probe.height}`);
  const p = await playsLevel1(page);
  check('narrow desktop window enters Level 1', p.moved > 2 && p.painted > 20,
        `x +${p.moved.toFixed(1)}, ${p.painted} colours drawn`);
  await ctx.close();
}

// F — a card-sized preview shows the game, not a prompt nobody can act on.
{
  const { ctx, page } = await open({ ...PIXEL_PORTRAIT, viewport: { width: 240, height: 180 } });
  const g = await gate(page);
  check('thumbnail frame shows the game', !g.gated && g.reason === 'VIEWPORT_PREVIEW',
        `${g.reason} at ${g.probe.width}x${g.probe.height}`);
  await ctx.close();
}

// G — rotating clears the prompt with no input at all.
{
  const { ctx, page } = await open({
    ...PIXEL_PORTRAIT, viewport: { width: 390, height: 844 }, screen: { width: 390, height: 844 },
  });
  check('starts gated before the rotation', (await gate(page)).gated);
  await page.setViewportSize({ width: 844, height: 390 });
  await page.waitForFunction(() => !FLIP.gated(), { timeout: 4000 }).catch(() => {});
  const g = await gate(page);
  check('rotating lifts the prompt with no input', !g.gated && !g.overlay, g.reason);
  const p = await playsLevel1(page);
  check('the level starts fresh after rotating', p.moved > 2 && p.jumped,
        `x +${p.moved.toFixed(1)}`);
  check('the paused run did not burn attempts behind the prompt',
        (await page.evaluate(() => FLIP.attempts())) <= 2,
        `attempt ${await page.evaluate(() => FLIP.attempts())}`);
  await ctx.close();
}

// H — the gate re-checks itself even when the host fires no event we can hear.
{
  const { ctx, page } = await open({
    ...PIXEL_PORTRAIT, viewport: { width: 390, height: 844 }, screen: { width: 390, height: 844 },
  });
  check('starts gated before the silent change', (await gate(page)).gated);
  const t0 = Date.now();
  await page.evaluate(() => { window.FLIP_FORCE_PLAY = true; });   // no resize, no event
  const cleared = await page.waitForFunction(() => !FLIP.gated(), { timeout: 4000 })
    .then(() => true).catch(() => false);
  check('the gate polls itself free without any event', cleared,
        cleared ? `cleared in ${Date.now() - t0}ms` : 'still gated after 4s');
  await ctx.close();
}

// I — nobody is ever trapped: the prompt hands out its own way past itself.
{
  const { ctx, page } = await open(PIXEL_PORTRAIT);
  const shown = await page.waitForFunction(() => FLIP.escapeVisible(), { timeout: 8000 })
    .then(() => true).catch(() => false);
  check('a stuck rotate prompt offers a way past itself', shown);
  await page.screenshot({ path: path.join(shotDir, 'i-escape-offered.png') });
  await page.click('#escape');
  await page.waitForFunction(() => !FLIP.gated(), { timeout: 3000 }).catch(() => {});
  const g = await gate(page);
  check('taking the escape hatch starts the level', !g.gated && g.reason === 'OVERRIDE', g.reason);
  const p = await playsLevel1(page);
  // Read the framing only once frames have actually been drawn — a paused
  // renderer still reports the scale it last used.
  const drawn = await gate(page);
  check('portrait play is fair, not a keyhole', drawn.viewUnits >= 17.99 && p.moved > 2 && p.painted > 20,
        `${drawn.viewUnits.toFixed(1)} units across, x +${p.moved.toFixed(1)}`);
  await page.screenshot({ path: path.join(shotDir, 'j-portrait-playing.png') });
  await ctx.close();
}

// J — the development bypass, which the player is never shown.
{
  const { ctx, page } = await open(PIXEL_PORTRAIT, { query: '?play=1' });
  const g = await gate(page);
  check('the dev bypass skips the gate', !g.gated && g.reason === 'OVERRIDE', g.reason);
  check('the dev bypass is not a control the player can see',
        await page.evaluate(() => document.getElementById('rotate').hidden));
  await ctx.close();
}

check('no javascript errors in any orientation', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));

await browser.close();
server.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(failed.length ? 1 : 0);
