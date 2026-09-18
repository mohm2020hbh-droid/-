/**
 * THE PACK IS THE ONLY SOURCE. This proves it, mechanically.
 *
 * Four questions, each answered from the files on disk rather than from anyone's
 * memory of what was changed:
 *
 *   1. Does any web source still GENERATE sound? An oscillator, a noise buffer,
 *      a filter sweep - anything that makes a waveform instead of playing one.
 *   2. Does every sound the code names exist in the shipped library?
 *   3. Does every file in the shipped library come from the pack?
 *   4. Did every recording in the pack survive encoding, in both containers?
 *
 *   node tools/audioaudit.mjs [--masters DIR]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const src = path.join(root, 'web/src/jsMain/kotlin/com/fliperror/web');
const lib = path.join(root, 'web/src/jsMain/resources/audio');
const masters = process.argv.includes('--masters')
  ? process.argv[process.argv.indexOf('--masters') + 1] : null;

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

// 1 — nothing in the game may generate a sound ---------------------------------
// The list is the WebAudio surface that produces audio from nothing. Gain,
// compressor and the media/buffer sources are absent on purpose: they route and
// play what the pack supplies, they do not invent anything.
const generators = [
  'createOscillator', 'createBiquadFilter', 'createWaveShaper', 'createConvolver',
  'createPeriodicWave', 'createDelay', 'createPanner', 'OscillatorNode',
  'createScriptProcessor', 'AudioWorklet', 'createChannelMerger',
];
const offenders = [];
for (const f of fs.readdirSync(src).filter(n => n.endsWith('.kt'))) {
  const text = fs.readFileSync(path.join(src, f), 'utf8');
  text.split('\n').forEach((line, i) => {
    if (line.trim().startsWith('//') || line.trim().startsWith('*')) return;
    for (const g of generators) if (line.includes(g)) offenders.push(`${f}:${i + 1} ${g}`);
  });
}
check('no source file generates a sound', offenders.length === 0,
  offenders.length ? offenders.slice(0, 5).join(' | ') : `${generators.length} generator APIs, none used`);

// Also: nothing may create a noise buffer by hand.
const handRolled = [];
for (const f of fs.readdirSync(src).filter(n => n.endsWith('.kt'))) {
  const text = fs.readFileSync(path.join(src, f), 'utf8');
  if (/createBuffer\s*\(/.test(text) || /getChannelData/.test(text)) handRolled.push(f);
}
check('and none writes raw samples of its own', handRolled.length === 0, handRolled.join(', '));

// 2 — every sound the code names is in the library ------------------------------
const mapText = fs.readFileSync(path.join(src, 'AudioMap.kt'), 'utf8');
const named = [...new Set(
  // Case-insensitive on purpose: the bed names carry an uppercase layer number
  // (20_w1_future_ambience_L1_60s), and a lowercase-only pattern silently missed
  // all ten of them - which had this audit reporting the game's own room as a
  // stray asset.
  [...mapText.matchAll(/"([0-9]{2}_[A-Za-z0-9_]+)"/g)].map(m => m[1])
)].sort();
const shipped = new Set(fs.readdirSync(lib).map(f => f.replace(/\.(webm|mp3)$/, '')));
const missing = named.filter(n => !shipped.has(n));
check('every sound the code names exists in the library',
  missing.length === 0, missing.length ? `missing: ${missing.join(', ')}` : `${named.length} names`);

// Every one of them in BOTH containers, or a browser somewhere goes quiet.
const halfEncoded = named.filter(n =>
  !fs.existsSync(path.join(lib, n + '.webm')) || !fs.existsSync(path.join(lib, n + '.mp3')));
check('and each one in both containers', halfEncoded.length === 0, halfEncoded.join(', '));

// 3 — nothing in the library is from anywhere else ------------------------------
// The pack's own naming is NN_name; anything that does not match, or that the map
// does not name, would be a stray asset and the whole rule's failure mode.
const strays = [...shipped].filter(n => !/^[0-9]{2}_/.test(n) || !named.includes(n));
check('nothing in the library is from outside the pack',
  strays.length === 0, strays.length ? strays.join(', ') : `${shipped.size} recordings, all mapped`);

// 4 — and the pack itself came through encoding whole ----------------------------
if (masters && fs.existsSync(masters)) {
  const wavs = fs.readdirSync(masters).filter(f => f.endsWith('.wav')).map(f => f.slice(0, -4));
  const lost = wavs.filter(n => !shipped.has(n));
  check('every recording in the pack survived encoding',
    lost.length === 0, lost.length ? `lost: ${lost.join(', ')}` : `${wavs.length} masters`);
  const extra = [...shipped].filter(n => !wavs.includes(n));
  check('and the library holds nothing the pack did not', extra.length === 0, extra.join(', '));
  const unused = wavs.filter(n => !named.includes(n));
  console.log(unused.length
    ? `\nNOTE: ${unused.length} recording(s) present but not mapped: ${unused.join(', ')}`
    : '\nEvery recording in the pack is mapped to a job.');
} else {
  console.log('\n(masters not supplied; skipped the pack-completeness checks)');
}

const passed = results.filter(r => r.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
process.exit(passed === results.length ? 0 : 1);
