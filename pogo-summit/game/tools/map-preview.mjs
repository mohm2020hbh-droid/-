// Usage: node tools/map-preview.mjs <map.json> [--out dir] [--theme id] [--at x,y;x,y;…] [--size WxH] [--frames n] [--perf]
//   Loads the map straight from JSON in the real game (headless Chromium, software GL) and writes screenshots.
//   Default shots: the spawn plus four evenly spaced points along metadata.route / the checkpoints.
//   --perf prints the renderer counters (draw calls, triangles, textures, scene stats) for every shot.
//   (software GL: FPS is NOT representative of a phone; draw calls / triangles / memory are exact)
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { createServer } from 'node:http';
import { readFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname, extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const args = process.argv.slice(2);
const flag = (n, d) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : d; };
const file = args.find((a, i) => !a.startsWith('-') && (i === 0 || !args[i - 1].startsWith('--') || args[i - 1] === '--perf'));
if (!file) { console.error('usage: preview <map.json> [--out dir] [--theme id] [--at x,y;x,y] [--size WxH] [--frames n] [--perf]'); process.exit(2); }
const game = join(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(game, 'dist');
if (!existsSync(join(dist, 'game.js')) || args.includes('--rebuild')) { console.log('building the game bundle …'); execFileSync('node', [join(game, 'tools', 'build.mjs')], { cwd: game, stdio: 'inherit' }); }
const mapText = readFileSync(resolve(file), 'utf8');
const doc = JSON.parse(mapText);
const outDir = resolve(flag('--out', 'map-preview')); mkdirSync(outDir, { recursive: true });
const [W, H] = flag('--size', '1280x600').split('x').map(Number);
const frames = Number(flag('--frames', '90'));
const theme = flag('--theme');

// shots: explicit --at, otherwise spawn + points along the route / checkpoints
let points = [];
if (flag('--at')) points = flag('--at').split(';').map(s => s.split(',').map(Number));
else {
  const byId = new Map((doc.entities ?? []).map(e => [e.id, e]));
  const route = (doc.metadata?.route ?? []).map(id => byId.get(id)).filter(Boolean).map(e => [e.position.x, e.position.y + 2.5]);
  const cps = (doc.checkpoints ?? []).map(c => [c.respawn.x, c.respawn.y + 1]);
  const pool = route.length >= 5 ? route : cps;
  points = [[doc.spawn.position.x, doc.spawn.position.y + 2.5]];
  if (pool.length) for (let i = 1; i <= 4; i++) points.push(pool[Math.min(pool.length - 1, Math.round((i * (pool.length - 1)) / 4))]);
}

const types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json' };
const srv = createServer((req, res) => {
  const u = req.url.split('?')[0];
  if (u === '/__map.json') { res.writeHead(200, { 'content-type': 'application/json' }); res.end(mapText); return; }
  const p = join(dist, u === '/' ? 'index.html' : u);
  if (!existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] ?? 'application/octet-stream' }); res.end(readFileSync(p));
}).listen(0);
const base = `http://localhost:${srv.address().port}/`;
const executablePath = process.env.CHROMIUM_PATH ?? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const b = await chromium.launch({ executablePath, args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--autoplay-policy=no-user-gesture-required'] });
const ctx = await b.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
const p = await ctx.newPage();
let errors = 0;
p.on('pageerror', e => { errors++; console.log('PAGEERROR:', e.message); });
p.on('console', m => { const t = m.text(); if (/error|fail/i.test(t) && !/404|GPU stall/.test(t)) { errors++; console.log('console:', t); } });
await p.goto(`${base}?mapUrl=/__map.json&debug=1${theme ? `&theme=${theme}` : ''}`);
await p.waitForFunction('window.__ready && window.__pogo && window.__pogo.game.mapRuntime', null, { timeout: 60000 });
const name = doc.manifest?.id ?? 'map';
let n = 0;
for (const [x, y] of points) {
  await p.evaluate(([px, py]) => window.__pogo.warpAt(px, py), [x, y]);
  await p.evaluate(f => window.__pogo.stepFrames(f), frames);
  const out = join(outDir, `${name}_${String(n++).padStart(2, '0')}.png`);
  await p.screenshot({ path: out });
  console.log(`shot ${out}  @ (${x.toFixed(1)}, ${y.toFixed(1)})`);
  if (args.includes('--perf')) {
    await p.waitForTimeout(150);
    const perf = await p.evaluate('window.__pogo.perf()');
    console.log('  perf', JSON.stringify({ frameCalls: perf.wholeFrame.calls, frameTris: perf.wholeFrame.triangles, mainCalls: perf.mainPass.calls, mainTris: perf.mainPass.triangles, drawCalls: perf.drawCalls, triangles: perf.triangles, textures: perf.textures, geometries: perf.geometries, heapMB: perf.heapMB, scene: perf.scene && { chunksLoaded: perf.scene.chunksLoaded, chunksVisible: perf.scene.chunksVisible, pools: perf.scene.pools, instancesVisible: perf.scene.instancesVisible, lod: perf.scene.lod, textureMB: +(perf.scene.textureBytes / 1048576).toFixed(2), buildMsMax: +perf.scene.buildMsMax.toFixed(1) } }));
  }
}
await b.close(); srv.close();
console.log(errors ? `done with ${errors} browser error(s)` : 'done — no browser errors');
process.exit(errors ? 1 : 0);
