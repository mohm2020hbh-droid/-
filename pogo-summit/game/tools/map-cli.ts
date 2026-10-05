// Usage: npm run map -- <command> …   (Map System V2 command line, SPEC §15)
//   validate <map.json|pkg.pogomap> [--deep] [--budget android-low]
//   build <map.json> -o out.pogomap [--assets <dir>]
//   inspect <map.json|pkg.pogomap>
//   chunks <map.json>
//   migrate <levelId> [-o map.json]            LevelData → map.json (built-in levels: level_01, physics_test)
//   import-legacy <file.$$M> [-o map.json] [--splits splitSetup.txt]    developer tool, geometry/behaviour data only
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { LEVEL_01 } from '../src/data/levels/level01';
import { PHYSICS_TEST } from '../src/data/levels/physicsTest';
import { parseMap, serializeMap } from '../src/map/MapLoader';
import { validateMap } from '../src/map/MapValidator';
import { PackageReader, buildPackage } from '../src/map/MapPackage';
import { levelDataToMap } from '../src/map/MapCompile';
import { importLegacyWmp } from '../src/map/legacyWmp';
import { documentChunkSource } from '../src/map/MapChunk';
import { PrefabRegistry, resolveEntity } from '../src/map/MapPrefab';
import { buildInstance } from '../src/map/MapEntity';
import { buildCurve } from '../src/map/MapBehavior';
import type { MapDocument } from '../src/map/schema';

const [cmd, ...rest] = process.argv.slice(2);
const flag = (n: string): string | undefined => { const i = rest.indexOf(n); return i >= 0 ? rest[i + 1] : undefined; };
const has = (n: string): boolean => rest.includes(n);
const file = rest.find(a => !a.startsWith('-') && rest[rest.indexOf(a) - 1] !== '-o' && rest[rest.indexOf(a) - 1] !== '--assets' && rest[rest.indexOf(a) - 1] !== '--budget' && rest[rest.indexOf(a) - 1] !== '--splits');

function load(path: string): MapDocument {
  const bytes = readFileSync(path);
  if (path.endsWith('.pogomap')) return PackageReader.open(new Uint8Array(bytes)).fullDocument();
  return parseMap(bytes.toString('utf8'), { lenient: true }).doc;
}
function print(r: ReturnType<typeof validateMap>): void {
  for (const i of r.issues) console.log(`${i.severity.padEnd(7)} ${i.code.padEnd(24)} ${i.path.padEnd(28)} ${i.message}`);
  console.log(`\n${r.ok ? 'OK' : 'FAILED'} — ${r.counts.error} error(s), ${r.counts.warning} warning(s), ${r.counts.info} info`);
}

switch (cmd) {
  case 'validate': {
    const r = validateMap(load(file!), { deep: has('--deep'), budget: flag('--budget') });
    print(r); process.exit(r.ok ? 0 : 1); break;
  }
  case 'build': {
    const doc = load(file!);
    const assets: Record<string, Uint8Array> = {};
    const dir = flag('--assets');
    for (const a of doc.assets) if (dir && existsSync(join(dir, a.path))) assets[a.path] = new Uint8Array(readFileSync(join(dir, a.path)));
    const out = flag('-o') ?? 'out.pogomap';
    const res = buildPackage(doc, { assets });
    writeFileSync(out, res.bytes);
    console.log(`${out}: ${res.bytes.length} bytes, ${res.files.length} files, ${res.manifest.package.chunks} chunks, capabilities: ${res.manifest.manifest.requirements.capabilities.join(', ')}`);
    break;
  }
  case 'inspect': {
    const path = file!;
    if (path.endsWith('.pogomap')) {
      const rd = PackageReader.open(new Uint8Array(readFileSync(path))); rd.verifyAll();
      console.log(JSON.stringify(rd.packageManifest(), null, 2));
      for (const e of rd.entries()) console.log(`${String(e.length).padStart(9)}  crc ${e.crc32.toString(16).padStart(8, '0')}  ${e.path}`);
    } else {
      const d = load(path);
      console.log(`${d.manifest.id} v${d.manifest.version} — ${d.entities.length} entities, ${d.regions.length} regions, ${d.checkpoints.length} checkpoints, theme ${d.manifest.theme}`);
    }
    break;
  }
  case 'chunks': {
    const d = load(file!);
    const reg = PrefabRegistry.forDoc(d);
    const paths = new Map(d.paths.map(p => [p.id, buildCurve(p)]));
    const src = documentChunkSource(d, e => buildInstance(resolveEntity(e, reg).entity, paths).extent);
    for (const c of src.listChunks()) console.log(`${c.id.padEnd(14)} ${String(c.entityCount).padStart(6)} entities  x ${c.extent.minX.toFixed(1)}…${c.extent.maxX.toFixed(1)}  y ${c.extent.minY.toFixed(1)}…${c.extent.maxY.toFixed(1)}`);
    break;
  }
  case 'migrate': {
    const level = file === 'physics_test' ? PHYSICS_TEST : LEVEL_01;
    const doc = levelDataToMap(level);
    const out = flag('-o');
    if (out) { writeFileSync(out, serializeMap(doc)); console.log(`wrote ${out}`); } else process.stdout.write(serializeMap(doc));
    break;
  }
  case 'import-legacy': {
    const text = readFileSync(file!, 'latin1');
    const splitFile = flag('--splits');
    const splitNames = splitFile && existsSync(splitFile) ? readFileSync(splitFile, 'utf8').split(/\r?\n/).filter(Boolean) : undefined;
    const { doc, report } = importLegacyWmp(text, { splitNames, id: file!.split(/[\\/]/).pop()!.replace(/\W+/g, '_') });
    for (const i of report.issues) console.log(`${i.severity.padEnd(7)} ${i.code.padEnd(18)} ${i.message}`);
    console.log(JSON.stringify(report.stats));
    const out = flag('-o'); if (out) { writeFileSync(out, serializeMap(doc)); console.log(`wrote ${out}`); }
    break;
  }
  default:
    console.log('commands: validate | build | inspect | chunks | migrate | import-legacy (see the header of tools/map-cli.ts)');
    process.exit(cmd ? 2 : 0);
}
