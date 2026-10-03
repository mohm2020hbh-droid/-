// Usage: node tools/qa-scenario.mjs <scenario.json|inline json> [W H] [outPrefix]
// steps: {goto:"?flags"} {click:"text"} {tap:[x,y]} {wait:ms} {frames:n} {shot:"name"} {eval:"js"} {log:"js expr"}
//        {touch:{down:[x,y]}} {touch:{move:[x,y]}} {touch:{up:true}}  (real CDP touch events)
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const arg = process.argv[2];
const steps = JSON.parse(existsSync(arg) ? readFileSync(arg, 'utf8') : arg);
const [W = '844', H = '390', prefix = '../qa/s_'] = process.argv.slice(3);
const types = { '.html': 'text/html', '.js': 'text/javascript' };
const srv = createServer((req, res) => {
  const p = join(root, req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0]);
  if (!existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] ?? 'application/octet-stream' }); res.end(readFileSync(p));
}).listen(0);
const base = `http://localhost:${srv.address().port}/`;
const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--autoplay-policy=no-user-gesture-required'] });
const ctx = await b.newContext({ viewport: { width: +W, height: +H }, deviceScaleFactor: 1, hasTouch: true, isMobile: true });
const p = await ctx.newPage();
const cdp = await ctx.newCDPSession(p);
p.on('pageerror', e => console.log('PAGEERROR:', e.message));
p.on('console', m => { const t = m.text(); if (/error|warn|fail/i.test(t) && !/404|GPU stall/.test(t)) console.log('console:', t); });
const touch = async (type, x, y, id = 1) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' || type === 'touchCancel' ? [] : [{ x, y, id }] });
for (const s of steps) {
  if (s.goto !== undefined) { await p.goto(base + s.goto); await p.waitForFunction('window.__ready', null, { timeout: 40000 }); }
  else if (s.click) { await p.getByText(s.click, { exact: false }).first().click({ timeout: 8000 }); }
  else if (s.tap) { await p.touchscreen.tap(s.tap[0], s.tap[1]); }
  else if (s.wait) await p.waitForTimeout(s.wait);
  else if (s.frames) await p.evaluate(n => window.__pogo.stepFrames(n), s.frames);
  else if (s.shot) { await p.screenshot({ path: `${prefix}${s.shot}.png` }); console.log('shot', s.shot); }
  else if (s.eval) await p.evaluate(s.eval);
  else if (s.log) console.log(s.log, '=>', JSON.stringify(await p.evaluate(s.log)));
  else if (s.touch) {
    const t = s.touch, id = t.id ?? 1;
    if (t.down) await touch('touchStart', t.down[0], t.down[1], id);
    if (t.move) await touch('touchMove', t.move[0], t.move[1], id);
    if (t.up) await touch('touchEnd', 0, 0, id);
    if (t.cancel) await touch('touchCancel', 0, 0, id);
  }
}
await b.close(); srv.close();
