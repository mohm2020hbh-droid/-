// Usage: npm run map -- <command> …   (Map System V2 command line, SPEC §15)
//   validate <map.json|pkg.pogomap> [--deep] [--budget android-low]
//   build <map.json> -o out.pogomap [--assets <dir>]
//   inspect <map.json|pkg.pogomap>
//   stats <map.json|pkg.pogomap>            entity/layer/collision breakdown, materials, worst-window cost vs. the Android budgets
//   preview <map.json> [--out dir] [--theme id] [--at x,y;x,y] [--size WxH] [--perf]
//                                           renders the map in the real game (headless Chromium) → PNG screenshots (+ renderer counters)
//   bot <map.json> [maxExpansions]          plays metadata.route with the route bot on the real physics (slow on big maps)
//   chunks <map.json>
//   migrate <levelId> [-o map.json]            LevelData → map.json (built-in levels: level_01, physics_test)
//   import-legacy <file.$$M> [-o map.json] [--splits splitSetup.txt]    developer tool, geometry/behaviour data only
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { LEVEL_01 } from '../src/data/levels/level01';
import { PHYSICS_TEST } from '../src/data/levels/physicsTest';
import { parseMap, serializeMap } from '../src/map/MapLoader';
import { BUDGETS, validateMap } from '../src/map/MapValidator';
import { resolveVisual } from '../src/map/MapVisual';
import { resolveTheme } from '../src/map/MapTheme';
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
  case 'stats': {
    const d = load(file!);
    const reg = PrefabRegistry.forDoc(d), paths = new Map(d.paths.map(p => [p.id, buildCurve(p)]));
    const theme = resolveTheme(d.theme, d.manifest.theme).theme;
    const byType = new Map<string, number>(), byLayer = new Map<string, number>(), byMesh = new Map<string, number>(), byMat = new Map<string, number>();
    let solid = 0, visualOnly = 0, derived = 0, instances = 0, triLod0 = 0, scattered = 0;
    const bump = (m: Map<string, number>, k: string, n = 1) => m.set(k, (m.get(k) ?? 0) + n);
    for (const e of d.entities) {
      const { entity: r } = resolveEntity(e, reg);
      const inst = buildInstance(r, paths);
      bump(byType, e.type);
      if (inst.pieces.length) solid++; else visualOnly++;
      const rv = resolveVisual(inst, { theme, assets: new Map(d.assets.map(a => [a.id, a])) });
      if (!rv) continue;
      bump(byLayer, rv.layer); bump(byMesh, rv.mesh);
      for (const part of rv.parts) bump(byMat, part.material);
      if (rv.derived) derived++;
      const n = rv.scatter ? Math.max(1, Math.round(rv.scatter.count)) : 1;
      if (rv.scatter) scattered++;
      instances += n; triLod0 += n * rv.tris[0];
    }
    const fmt = (m: Map<string, number>): string => [...m.entries()].sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k} ${v}`).join(' · ');
    const b = d.world.bounds;
    console.log(`${d.manifest.id} v${d.manifest.version} — theme ${theme.id} — ${(b.maxX - b.minX).toFixed(0)} × ${(b.maxY - b.minY).toFixed(0)} m — ${d.checkpoints.length} checkpoints — ${d.regions.length} regions`);
    console.log(`entities ${d.entities.length}: ${fmt(byType)}`);
    console.log(`render layers: ${fmt(byLayer)}`);
    console.log(`collision: ${solid} entities with collision, ${visualOnly} visual-only · ${derived} visuals derived from collision (no visual declared)`);
    console.log(`visual instances ${instances} (${scattered} scatter entities), ${triLod0.toLocaleString('en')} triangles if everything were drawn at LOD0`);
    console.log(`meshes: ${fmt(byMesh)}`);
    console.log(`materials: ${fmt(byMat)}`);
    console.log(`camera: base profile ${JSON.stringify(d.camera.profile ?? {})} · camera zones ${d.regions.filter(r => r.type === 'camera').length} · lighting zones ${d.regions.filter(r => r.type === 'lighting' || r.type === 'fog').length} · audio zones ${d.regions.filter(r => r.type === 'audio').length}`);
    for (const name of Object.keys(BUDGETS)) {
      const rep = validateMap(d, { budget: name }), st = rep.stats, bu = BUDGETS[name];
      if (!st) { console.log(`\nbudget ${name}: not available (structural errors)`); continue; }
      const row = (label: string, v: number, cap: number, unit = ''): string => `  ${label.padEnd(16)} ${String(Math.round(v)).padStart(9)}${unit} / ${cap}${unit}  ${v > cap ? 'OVER' : v > cap * 0.8 ? 'close' : 'ok'}`;
      console.log(`\nworst window (around chunk ${st.worstWindowAt}) vs budget ${name}:`);
      console.log(row('draw calls', st.drawCalls + st.shadowCalls + st.backdropCalls, bu.drawCalls) + `   (${st.drawCalls} + ${st.shadowCalls} shadow + ${st.backdropCalls} backdrop)`);
      console.log(row('triangles', st.triangles, bu.triangles) + `   (LOD0 worst case ${st.trianglesLod0})`);
      console.log(row('texture memory', st.textureBytes / 1048576, bu.textureBytes / 1048576, ' MB'));
      console.log(row('colliders', st.colliders, bu.colliders));
      console.log(row('VFX particles', Math.max(st.vfx, d.vfx.maxParticles), bu.vfx));
      console.log(row('audio voices', Math.max(st.audio, d.audio.maxVoices), bu.audio));
      console.log(`  issues: ${rep.counts.error} error(s), ${rep.counts.warning} warning(s)`);
    }
    break;
  }
  case 'preview': {
    const r = spawnSync('node', [fileURLToPath(new URL('./map-preview.mjs', import.meta.url)), ...rest], { stdio: 'inherit' });
    process.exit(r.status ?? 1);
    break;
  }
  case 'bot': {
    const { playRoute } = await import('../src/sim/routeBot');
    const { compileRenderLevel } = await import('../src/map/MapCompile');
    const { createPhysicsConfig } = await import('../src/sim/PhysicsConfig');
    const doc = load(file!);
    const t0 = performance.now();
    const plan = playRoute(compileRenderLevel(doc), createPhysicsConfig(), { maxExpansions: Number(rest.find(a => /^\d+$/.test(a)) ?? 400) });
    console.log(JSON.stringify({ success: plan.success, hops: plan.hops.length, jumps: plan.jumps, ticks: plan.ticks, failedAt: plan.failedAt, expansions: plan.expansions, seconds: Math.round((performance.now() - t0) / 1000) }));
    for (const h of plan.hops) console.log(`${h.from} → ${h.to}  idle ${h.idle} tiltG ${h.tiltG} tt ${h.tt} hd ${h.hd} tiltA ${h.tiltA} (${h.ticks} ticks)`);
    process.exit(plan.success ? 0 : 1);
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
    console.log('commands: validate | build | inspect | stats | preview | bot | chunks | migrate | import-legacy (see the header of tools/map-cli.ts)');
    process.exit(cmd ? 2 : 0);
}
