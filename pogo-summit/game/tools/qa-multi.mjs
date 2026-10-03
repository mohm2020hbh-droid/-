// Usage: node tools/qa-multi.mjs '<json views>' [W H] [outPrefix]
// views: [{"n":"start","x":-3,"y":2,"zoom":19,"js":"..."}]
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const views = JSON.parse(process.argv[2]);
const [W = '1280', H = '576', prefix = '../qa/m_'] = process.argv.slice(3);
const types = { '.html': 'text/html', '.js': 'text/javascript' };
const srv = createServer((req, res) => {
  const p = join(root, req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0]);
  if (!existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] ?? 'application/octet-stream' }); res.end(readFileSync(p));
}).listen(0);
const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox'] });
const p = await b.newPage({ viewport: { width: +W, height: +H }, deviceScaleFactor: 1 });
p.on('pageerror', e => console.log('pageerror:', e.message));
p.on('console', m => { const t = m.text(); if (/error|warn/i.test(t) && !/404/.test(t)) console.log('console:', t); });
await p.goto(`http://localhost:${srv.address().port}/?debug=1&quality=default`);
await p.waitForFunction('window.__ready', null, { timeout: 30000 });
for (const v of views) {
  await p.evaluate(([v]) => { const a = window.__pogo; a.zoom(v.zoom ?? 19); if (v.js) (0, eval)(v.js); a.cam(v.x, v.y); a.stepFrames(v.frames ?? 30); }, [v]);
  await p.screenshot({ path: `${prefix}${v.n}.png` });
  console.log('shot', v.n);
}
await b.close(); srv.close();
