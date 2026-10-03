// Usage: node tools/qa-shot.mjs <out.png> [W H] [x y] [extra-query]
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const [out, W = '1280', H = '576', X = '0', Y = '2', extra = ''] = process.argv.slice(2);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png' };
const srv = createServer((req, res) => {
  const p = join(root, req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0]);
  if (!existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] ?? 'application/octet-stream' }); res.end(readFileSync(p));
}).listen(0);
const port = srv.address().port;
const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox'] });
const p = await b.newPage({ viewport: { width: +W, height: +H }, deviceScaleFactor: 1 });
p.on('console', m => { const t = m.text(); if (!t.startsWith('[vite]')) console.log('console:', t); });
p.on('pageerror', e => console.log('pageerror:', e.message));
await p.goto(`http://localhost:${port}/?debug=1&quality=default${extra ? '&' + extra : ''}`);
await p.waitForFunction('window.__ready', null, { timeout: 30000 });
const js = process.env.QA_JS || '';
await p.evaluate(([x, y, js]) => { const a = window.__pogo; if (js) (0, eval)(js); a.cam(x, y); a.stepFrames(30); }, [+X, +Y, js]);
await p.screenshot({ path: out });
const info = await p.evaluate(() => { const r = window.__pogo.game.renderer; return JSON.stringify(r.stats); });
console.log('render stats', info);
await b.close(); srv.close();
