import { build, context } from 'esbuild';
import { cpSync, mkdirSync, rmSync, existsSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(root, 'dist');
const watch = process.argv.includes('--watch');
const prod = !process.argv.includes('--dev');
const toAndroid = process.argv.includes('--android');

rmSync(dist, { recursive: true, force: true });
mkdirSync(dist, { recursive: true });
cpSync(join(root, 'src', 'index.html'), join(dist, 'index.html'));
// bundled UI fonts (SIL OFL 1.1): Baloo 2 (Latin) + Baloo Bhaijaan 2 (Arabic) — offline, no network at runtime
mkdirSync(join(dist, 'fonts'), { recursive: true });
for (const [pkg, file] of [['baloo-2', 'baloo-2-latin'], ['baloo-bhaijaan-2', 'baloo-bhaijaan-2-arabic']]) {
  for (const wgt of [600, 800]) cpSync(join(root, 'node_modules', '@fontsource', pkg, 'files', `${file}-${wgt}-normal.woff2`), join(dist, 'fonts', `${file}-${wgt}.woff2`));
  cpSync(join(root, 'node_modules', '@fontsource', pkg, 'LICENSE'), join(dist, 'fonts', `${pkg}-OFL.txt`));
}

const opts = {
  entryPoints: [join(root, 'src', 'main.ts')],
  bundle: true, format: 'iife', target: ['es2020', 'chrome90'],
  outfile: join(dist, 'game.js'),
  minify: prod, sourcemap: prod ? false : 'inline', legalComments: 'none', logLevel: 'info',
  define: { 'process.env.NODE_ENV': '"production"' },
};
if (watch) { const c = await context(opts); await c.watch(); console.log('watching…'); }
else {
  await build(opts);
  const kb = (statSync(join(dist, 'game.js')).size / 1024).toFixed(0);
  console.log(`built dist/game.js (${kb} KB)`);
  if (toAndroid) {
    const dest = join(root, '..', 'android', 'app', 'src', 'main', 'assets', 'www');
    rmSync(dest, { recursive: true, force: true }); mkdirSync(dest, { recursive: true });
    cpSync(dist, dest, { recursive: true });
    console.log('copied to', dest);
  }
}
