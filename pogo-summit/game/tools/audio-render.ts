/**
 * audio-render — render every procedural recipe to a 16-bit mono WAV so it can be measured with the same analyser as the
 * originals (`tools/audio-analyze.py`) and listened to on a desktop.
 *
 *   npx tsx tools/audio-render.ts <out-dir> [sampleRate=44100] [seed=0x5eed]
 *   python3 tools/audio-analyze.py <out-dir> <out.json>
 *
 * The output is *this project's* synthesised sound; nothing here reads or writes the original recordings.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { RECIPES, renderRateOf } from '../src/audio/system/synth/recipes';
import { mulberry32, seedFor } from '../src/audio/system/rng';
import { DEFAULT_BANK_SEED } from '../src/audio/system/SoundBank';

const out = process.argv[2];
if (!out) { console.error('usage: tsx tools/audio-render.ts <out-dir> [sampleRate] [seed]'); process.exit(1); }
const sr = Number(process.argv[3] ?? 44100), seed = Number(process.argv[4] ?? DEFAULT_BANK_SEED);
mkdirSync(out, { recursive: true });

function wav16(x: Float32Array, rate: number): Buffer {
  const data = Buffer.alloc(x.length * 2);
  for (let i = 0; i < x.length; i++) data.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(x[i] * 32767))), i * 2);
  const h = Buffer.alloc(44);
  h.write('RIFF', 0); h.writeUInt32LE(36 + data.length, 4); h.write('WAVE', 8); h.write('fmt ', 12); h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22); h.writeUInt32LE(rate, 24); h.writeUInt32LE(rate * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
  h.write('data', 36); h.writeUInt32LE(data.length, 40);
  return Buffer.concat([h, data]);
}

for (const r of Object.values(RECIPES)) {
  const rate = renderRateOf(r, sr);
  const t0 = performance.now();
  const x = r.render(rate, mulberry32(seedFor(seed, r.id)));
  const ms = performance.now() - t0;
  writeFileSync(join(out, `${r.id}.wav`), wav16(x, rate));
  console.log(`${r.id.padEnd(16)} ${(x.length / rate).toFixed(3)} s  @${rate} Hz  ${ms.toFixed(1)} ms render  (analog ${r.analog})`);
}
