// Usage: node tools/audio-browser-check.mjs <out-dir>
// Runs the audio system against the REAL Web Audio API in headless Chromium (live context + an offline render of a scripted session).
// Writes <out-dir>/session.wav (stereo, 16-bit, the mix after the bus graph and compressor) and <out-dir>/browser-check.json.
// Analyse the WAV with `python3 tools/audio-analyze.py <out-dir>`.
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { build } from 'esbuild';
import { createServer } from 'node:http';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = process.argv[2];
if (!out) { console.error('usage: node tools/audio-browser-check.mjs <out-dir>'); process.exit(1); }
mkdirSync(out, { recursive: true });

const bundle = await build({
  entryPoints: [join(root, 'tools', 'audio-check-entry.ts')], bundle: true, format: 'iife', target: ['chrome90'], write: false, logLevel: 'warning',
  define: { 'process.env.NODE_ENV': '"production"' },
});
const js = bundle.outputFiles[0].text;
const srv = createServer((req, res) => {
  if (req.url === '/bundle.js') { res.writeHead(200, { 'content-type': 'text/javascript' }); res.end(js); return; }
  res.writeHead(200, { 'content-type': 'text/html' }); res.end('<!doctype html><title>audio check</title><script src="/bundle.js"></script>');
}).listen(0);
const port = srv.address().port;

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM ?? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
  args: ['--no-sandbox', '--autoplay-policy=no-user-gesture-required', '--disable-features=AudioServiceOutOfProcess'],
});
const page = await browser.newPage();
page.on('console', m => console.log('console:', m.text()));
page.on('pageerror', e => console.log('pageerror:', e.message));
await page.goto(`http://localhost:${port}/`);
const result = await page.evaluate(() => window.runAudioCheck());
await browser.close(); srv.close();

const off = result.offline;
if (off && off.left && off.left.length) {
  const n = off.left.length, data = Buffer.alloc(n * 4);
  for (let i = 0; i < n; i++) {
    data.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(off.left[i] * 32767))), i * 4);
    data.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(off.right[i] * 32767))), i * 4 + 2);
  }
  const h = Buffer.alloc(44), sr = off.sampleRate;
  h.write('RIFF', 0); h.writeUInt32LE(36 + data.length, 4); h.write('WAVE', 8); h.write('fmt ', 12); h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20); h.writeUInt16LE(2, 22); h.writeUInt32LE(sr, 24); h.writeUInt32LE(sr * 4, 28); h.writeUInt16LE(4, 32); h.writeUInt16LE(16, 34);
  h.write('data', 36); h.writeUInt32LE(data.length, 40);
  writeFileSync(join(out, 'session.wav'), Buffer.concat([h, data]));
  delete off.left; delete off.right;
}
writeFileSync(join(out, 'browser-check.json'), JSON.stringify(result, null, 2));

// verdict: the run must show the real engine working, not merely not crashing
const L = result.live ?? {}, O = result.offline ?? {};
const checks = {
  'live context unlocked and running': L.ready === true && L.state === 'running',
  'every recipe rendered (11), none missing': L.recipes === 11 && L.bankStats?.missing === 0,
  'all six brief events played in both rounds': ['POGO_CHARGE', 'POGO_LAUNCH', 'POGO_COLLISION', 'POGO_BREAK', 'ICE_SLIDE', 'TIME_EFFECT'].every(e => (L.managerStats?.perEvent?.[e]?.played ?? 0) >= 2),
  'voice channels are reused, not created per sound': (L.hostStats?.channelReuses ?? 0) > 0 && (L.hostStats?.channelsCreated ?? 99) < (L.hostStats?.voicesStarted ?? 0),
  'one AudioContext for the page': L.hostStats?.contextsCreated === 1,
  'the pool drains to zero once everything has ended': L.poolCountDrained === 0,
  'offline session rendered with the scripted timeline': Array.isArray(O.log) && O.log.length > 30 && !!O.stats,
  'no voice-limit rejection in the session': (O.stats?.skipped?.['voice-limit'] ?? 1) === 0,
};
console.log(JSON.stringify(result, null, 2).slice(0, 1800), '\n…');
let bad = 0;
for (const [name, ok] of Object.entries(checks)) { console.log(ok ? 'PASS' : 'FAIL', name); if (!ok) bad++; }
if (bad) process.exit(1);
