import { describe, expect, it } from 'vitest';
import { parseMap } from '../../src/map/MapLoader';
import { LIMITS, PackageError, PackageReader, buildPackage, checkPath, crc32 } from '../../src/map/MapPackage';
import { MapRuntime } from '../../src/map/MapRuntime';
import { PogoPhysicsController } from '../../src/sim/PogoPhysicsController';
import { createPhysicsConfig } from '../../src/sim/PhysicsConfig';
import type { MapDocument } from '../../src/map/schema';
import { baseDoc, corridorMap, smallMap } from './fixtures';

const small = (): MapDocument => parseMap(smallMap()).doc;
const bytes = (n: number, v = 7): Uint8Array => new Uint8Array(n).fill(v);

describe('MapPackage — build, read, verify', () => {
  it('builds a package with manifest, core, chunk index and chunk files; the manifest carries computed fields', () => {
    const doc = small(); doc.manifest.checkpointCount = 99; doc.manifest.requirements.capabilities = [];
    const r = buildPackage(doc);
    const rd = PackageReader.open(r.bytes);
    const paths = rd.entries().map(e => e.path);
    expect(paths).toEqual([...paths].sort());                                       // deterministic order
    expect(paths).toEqual(expect.arrayContaining(['map_manifest.json', 'core.json', 'chunks/index.json']));
    expect(paths.some(p => p.startsWith('chunks/chunk_'))).toBe(true);
    const m = rd.packageManifest();
    expect(m.manifest.id).toBe('demo_small');
    expect(m.manifest.checkpointCount).toBe(1);                                      // recomputed
    expect(m.manifest.requirements.capabilities).toEqual(expect.arrayContaining(['move', 'checkpoints', 'chunks', 'slippery', 'hazard']));
    expect(m.package).toMatchObject({ containerVersion: 1, chunks: rd.listChunks().length });
    expect(r.manifest).toEqual(m);
    expect(r.report.ok).toBe(true);
  });

  it('the build is deterministic: same document ⇒ identical bytes', () => {
    const a = buildPackage(small()).bytes, b = buildPackage(small()).bytes;
    expect(Buffer.from(a).equals(Buffer.from(b))).toBe(true);
    const d = small(); d.manifest.name = 'Other';
    expect(Buffer.from(buildPackage(d).bytes).equals(Buffer.from(a))).toBe(false);
  });

  it('core + chunks reassemble to the original document (entities and everything else)', () => {
    const doc = small();
    const rd = PackageReader.open(buildPackage(doc).bytes);
    expect(rd.core().entities).toEqual([]);
    const full = rd.fullDocument();
    expect(full.entities.map(e => e.id).sort()).toEqual(doc.entities.map(e => e.id).sort());
    expect(full.regions).toEqual(doc.regions); expect(full.checkpoints).toEqual(doc.checkpoints); expect(full.progress).toEqual(doc.progress);
    expect(full.spawn).toEqual(doc.spawn);
  });

  it('chunks are parsed lazily: a runtime that stays near the start parses only the chunks it loads', () => {
    const doc = corridorMap({ tiles: 30, cell: 24 });
    const rd = PackageReader.open(buildPackage(doc).bytes);
    const cfg = createPhysicsConfig();
    const rt = new MapRuntime(rd.core(), { cfg, source: rd });
    const pogo = new PogoPhysicsController(rt.world, cfg);
    for (let i = 0; i < 300; i++) { rt.beforeStep(pogo.state); rt.afterStep(pogo.state, pogo.step()); }
    expect(rd.stats.chunksParsed).toBe(rt.chunks.stats.loads);
    expect(rd.stats.chunksParsed).toBeLessThan(rd.listChunks().length / 2);          // far chunks never parsed
    expect(pogo.state.falls).toBe(0);
  });

  it('assets are packaged, CRC-checked and must exist for every declared asset', () => {
    const doc = small();
    doc.assets = [{ id: 'rock', kind: 'mesh', path: 'models/rock.glb', tris: 500 }, { id: 'pal', kind: 'texture', path: 'textures/pal.png', width: 32, height: 32 }, { id: 'wind', kind: 'audio', path: 'audio/wind.ogg' }];
    doc.materials = { m: { id: 'm', shader: 'palette', color: '#fff', palette: 'pal' } };
    doc.entities.push({ id: 'rk', type: 'decor', position: { x: 5, y: 3 }, visual: { kind: 'mesh', mesh: 'rock', material: 'm' } });
    doc.regions.push({ id: 'amb', type: 'audio', shape: { kind: 'box', x: 10, y: 5, w: 5, h: 5 }, enter: [{ op: 'audio', id: 'wind' }] });
    doc.metadata.localized = { en: { name: 'Demo', description: 'A small map.' }, ar: { description: 'خريطة صغيرة' } };
    const files = { 'models/rock.glb': bytes(100, 1), 'textures/pal.png': bytes(50, 2), 'audio/wind.ogg': bytes(80, 3) };
    const r = buildPackage(doc, { assets: files });
    const rd = PackageReader.open(r.bytes);
    expect(Array.from(rd.file('models/rock.glb'))).toEqual(Array.from(files['models/rock.glb']));
    expect(rd.text('metadata/description.ar.txt')).toBe('خريطة صغيرة');                 // UTF-8, localised
    expect(() => buildPackage(doc, { assets: { 'models/rock.glb': bytes(1) } })).toThrow(/no content supplied/);
    rd.verifyAll();
  });

  it('corruption is detected: bad magic, index CRC, file CRC, truncation', () => {
    const good = buildPackage(small()).bytes;
    expect(() => PackageReader.open(good.subarray(0, 10))).toThrow(PackageError);
    const magic = good.slice(); magic[0] = 0x41; expect(() => PackageReader.open(magic)).toThrow(/bad magic/);
    const idx = good.slice(); idx[20] ^= 0xff; expect(() => PackageReader.open(idx)).toThrow(/index is corrupt/);
    const rd0 = PackageReader.open(good);
    const e = rd0.entries().find(x => x.path === 'core.json')!;
    const dv = new DataView(good.buffer, good.byteOffset);
    const start = 16 + dv.getUint32(8, true);
    const flip = good.slice(); flip[start + e.offset + 5] ^= 0x55;
    const rd = PackageReader.open(flip);
    expect(() => rd.core()).toThrow(/corrupt/);
    expect(() => PackageReader.open(good.subarray(0, good.length - 10))).toThrow(/outside the package|truncated/);
    expect(() => PackageReader.open(new Uint8Array(0))).toThrow(PackageError);
  });

  it('data-only: scripts, shaders and executables are refused when building and when reading', () => {
    for (const bad of ['scripts/evil.js', 'fx/water.fx', 'bin/tool.exe', 'lib/x.dll', 'a/b.c', 'shaders/x.glsl', 'run.sh', 'page.html', 'map.wmb']) expect(checkPath(bad), bad).toMatch(/not allowed/);
    expect(checkPath('../x.png')).toMatch(/relative/); expect(checkPath('/abs/x.png')).toMatch(/relative/); expect(checkPath('a//b.png')).toMatch(/relative/); expect(checkPath('a\\b.png')).toMatch(/relative/);
    expect(checkPath('textures/ok.png')).toBeNull(); expect(checkPath('x'.repeat(200) + '.png')).toMatch(/length/);
    const doc = small(); doc.assets = [{ id: 'x', kind: 'texture', path: 'textures/evil.js' }];
    expect(() => buildPackage(doc, { assets: { 'textures/evil.js': bytes(3) } })).toThrow(/not allowed|not valid/);
    const d2 = small(); d2.assets = [{ id: 'x', kind: 'mesh', path: 'models/x.png' }];
    expect(() => buildPackage(d2, { assets: { 'models/x.png': bytes(3) } })).toThrow(/not valid for this kind/);
    // a hand-crafted container whose index names a script is rejected by the reader
    const payload = bytes(4);
    const index = new TextEncoder().encode(JSON.stringify({ files: [{ path: 'evil.js', offset: 0, length: 4, crc32: crc32(payload), mime: 'text/javascript' }] }));
    const out = new Uint8Array(16 + index.length + 4);
    out.set([0x50, 0x47, 0x4d, 0x50], 0); new DataView(out.buffer).setUint16(4, 1, true); new DataView(out.buffer).setUint32(8, index.length, true); new DataView(out.buffer).setUint32(12, crc32(index), true);
    out.set(index, 16); out.set(payload, 16 + index.length);
    expect(() => PackageReader.open(out)).toThrow(/not allowed/);
  });

  it('validation errors block the build (with issues attached); force overrides for tooling', () => {
    const doc = small(); doc.spawn = null;
    try { buildPackage(doc); expect.unreachable(); } catch (e) { expect(e).toBeInstanceOf(PackageError); expect((e as PackageError).issues.some(i => i.code === 'SPAWN_MISSING')).toBe(true); }
    expect(() => buildPackage(doc, { force: true })).not.toThrow();
  });

  it('limits: file count', () => {
    const old = LIMITS.maxFiles; LIMITS.maxFiles = 3;
    try { expect(() => buildPackage(small())).toThrow(/too many files/); } finally { LIMITS.maxFiles = old; }
  });

  it('a package built from a doc loads and plays (spawn → hop) through the reader as ChunkSource', () => {
    const rd = PackageReader.open(buildPackage(baseDoc()).bytes);
    const cfg = createPhysicsConfig();
    const rt = new MapRuntime(rd.core(), { cfg, source: rd });
    const pogo = new PogoPhysicsController(rt.world, cfg);
    for (let i = 0; i < 100; i++) { rt.beforeStep(pogo.state); rt.afterStep(pogo.state, pogo.step()); }
    expect(rt.world.colliders[pogo.state.groundId]?.id ?? pogo.state.mode).toBeTruthy();
    expect(pogo.state.jumps).toBeGreaterThan(0);
  });
});
