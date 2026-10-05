// Usage: node tools/qa-audio-ingame.mjs [map] [theme]
// Runs the production bundle in headless Chromium and drives the real game loop: unlock audio, hold/release the charge (Space), put the
// pogo on a slippery platform in slide state, and report the audio system's own counters. Fails on any page error.
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const [map = 'showcase_v2', theme = 'world_ice'] = process.argv.slice(2);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.woff2': 'font/woff2' };
const srv = createServer((req, res) => {
  const p = join(root, req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0]);
  if (!existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] ?? 'application/octet-stream' }); res.end(readFileSync(p));
}).listen(0);
const port = srv.address().port;
const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--autoplay-policy=no-user-gesture-required'] });
const p = await b.newPage({ viewport: { width: 1000, height: 480 }, deviceScaleFactor: 1 });
const errors = [];
p.on('console', m => { const t = m.text(); if (m.type() === 'error' && !t.startsWith('Failed to load resource')) errors.push(t); });   // a missing favicon is not an error of the game
p.on('pageerror', e => errors.push('pageerror: ' + e.message));
await p.goto(`http://localhost:${port}/?debug=1&map=${map}&theme=${theme}`);
await p.waitForFunction('window.__ready', null, { timeout: 40000 });
await p.evaluate(() => { window.__pogo.app.audio.unlock(); });
await p.waitForFunction('window.__pogo.app.audio.ready', null, { timeout: 5000 });
const snap = () => p.evaluate(() => { const a = window.__pogo.app; return { ready: a.audio.ready, state: a.snapshot().state, events: a.audio.stats.perEvent, skipped: a.audio.stats.skipped, pool: a.audio.pool.count, bank: { n: a.audio.bank.size, bytes: a.audio.bank.stats.bytes, missing: a.audio.bank.stats.missing } }; });
await p.waitForTimeout(1500);                                   // let the bank warm up
console.log('after unlock', JSON.stringify(await snap()));
// hold the charge for 0.6 s, release: charge_start → (auto launch) → launch
await p.keyboard.down('Space'); await p.waitForTimeout(600); await p.keyboard.up('Space');
await p.waitForTimeout(1500);
const afterJump = await snap();
console.log('after a jump', JSON.stringify(afterJump.events), 'pool', afterJump.pool, 'bank', JSON.stringify(afterJump.bank));
// slide: drop the pogo onto an ice ledge with some horizontal speed; the REAL physics enters slide mode on landing (E15)
const dropped = await p.evaluate(() => {
  const a = window.__pogo, g = a.game;
  if (!a.warp('d1', 0, 2.5)) return false;                      // showcase_v2 ice ledge
  g.pogo.state.qvx = 36;                                          // Q/T: a running landing (QA only — the physics does the rest)
  return true;
});
console.log('dropped on an ice ledge:', dropped);
let sawSlide = false, loopStarted = false;
for (let i = 0; i < 40 && dropped; i++) {
  await p.waitForTimeout(100);
  const st = await p.evaluate(() => { const a = window.__pogo.app, s = a.game.pogo.state; return { slide: s.slideMode, grounded: s.grounded, sx: s.sx, ice: a.audio.stats.perEvent.ICE_SLIDE?.played ?? 0, loops: a.audio.pool.countFor('ICE_SLIDE') }; });
  if (st.slide) sawSlide = true;
  if (st.loops > 0) loopStarted = true;
  if (i % 8 === 0) console.log('  t+' + (i * 100) + 'ms', JSON.stringify(st));
  if (loopStarted && !st.slide) break;
}
console.log('physics entered slide mode:', sawSlide, '| ice loop was started by the audio system:', loopStarted);
await p.waitForTimeout(1500);
console.log('ice loop voices after the slide ended:', await p.evaluate(() => window.__pogo.app.audio.pool.countFor('ICE_SLIDE')));
const final = await snap();
console.log('FINAL', JSON.stringify(final));
await b.close(); srv.close();
if (errors.length) { console.log('ERRORS:', errors.slice(0, 8)); process.exit(1); }
console.log('no page errors');
