/**
 * MapPackage — the distributable map container (SPEC §16).
 *
 *   "PGMP" · u16 containerVersion · u16 flags · u32 indexLength · u32 indexCrc32 · index JSON · payload
 *
 * Data only (no scripts, no shader source), CRC32 per file, lazy per-chunk parsing, deterministic build.
 */
import { MAP_FORMAT_VERSION, PACKAGE_MAX_FILES, type AssetRef, type MapDocument, type MapManifest } from './schema';
import { MapLoadError, type MapIssue, type ValidationReport, mkIssue } from './MapIssue';
import { normalizeMap, parseMap, serializeMap, structuralCheck } from './MapLoader';
import { type ChunkData, type ChunkInfo, type ChunkSource, chunkIdFor, documentChunkSource } from './MapChunk';
import { PrefabRegistry, resolveEntity } from './MapPrefab';
import { buildInstance } from './MapEntity';
import { buildCurve } from './MapBehavior';
import { capabilitiesOf } from './MapCompile';
import { type ValidateOptions, validateMap } from './MapValidator';

export const CONTAINER_VERSION = 1;
const MAGIC = [0x50, 0x47, 0x4d, 0x50]; // "PGMP"
export const LIMITS = { maxFiles: PACKAGE_MAX_FILES, maxBytes: 128 * 1024 * 1024, maxPath: 128 };
export const ALLOWED_EXT = new Set(['json', 'png', 'jpg', 'jpeg', 'webp', 'glb', 'gltf', 'ogg', 'mp3', 'wav', 'txt', 'bin']);
export const FORBIDDEN_EXT = new Set(['js', 'mjs', 'cjs', 'html', 'htm', 'exe', 'dll', 'so', 'dylib', 'fx', 'glsl', 'hlsl', 'frag', 'vert', 'c', 'cpp', 'h', 'py', 'sh', 'bat', 'cmd', 'ps1', 'jar', 'apk', 'wdl', 'wmb', 'wmp']);
const ASSET_EXT: Record<AssetRef['kind'], string[]> = { mesh: ['glb', 'gltf', 'bin'], texture: ['png', 'jpg', 'jpeg', 'webp'], audio: ['ogg', 'mp3', 'wav'], material: ['json'], collision: ['json'] };

export class PackageError extends Error { constructor(msg: string, readonly issues: MapIssue[] = []) { super(msg); } }

// ── CRC32 ───────────────────────────────────────────────────────────────────────────────────────────────────────
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
export function crc32(data: Uint8Array): number { let c = 0xffffffff; for (let i = 0; i < data.length; i++) c = CRC_TABLE[(c ^ data[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }

const enc = new TextEncoder(), dec = new TextDecoder('utf-8', { fatal: true });
const utf8 = (s: string): Uint8Array => enc.encode(s);
const extOf = (p: string): string => { const i = p.lastIndexOf('.'); return i < 0 ? '' : p.slice(i + 1).toLowerCase(); };
const mimeOf = (ext: string): string => ({ json: 'application/json', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', glb: 'model/gltf-binary', gltf: 'model/gltf+json', ogg: 'audio/ogg', mp3: 'audio/mpeg', wav: 'audio/wav', txt: 'text/plain', bin: 'application/octet-stream' } as Record<string, string>)[ext] ?? 'application/octet-stream';

export function checkPath(path: string): string | null {
  if (!path || path.length > LIMITS.maxPath) return `path length must be 1…${LIMITS.maxPath}`;
  if (path.startsWith('/') || path.includes('\\') || path.split('/').some(s => s === '..' || s === '.' || s === '')) return 'path must be relative, normalised, without ".." or empty segments';
  if (/[^\x21-\x7e]/.test(path)) return 'path must be printable ASCII without spaces';
  const e = extOf(path);
  if (FORBIDDEN_EXT.has(e)) return `file type ".${e}" is not allowed in a data-only package`;
  if (!ALLOWED_EXT.has(e)) return `file type ".${e}" is not allowed (allowed: ${[...ALLOWED_EXT].join(' ')})`;
  return null;
}

export interface IndexEntry { path: string; offset: number; length: number; crc32: number; mime: string }
export interface PackageInfo { containerVersion: number; files: number; bytes: number; chunks: number; createdAt: string; contentCrc32: number }
export interface PackageManifest { manifest: MapManifest; package: PackageInfo }

export interface BuildOptions {
  /** Binary content of declared assets, keyed by `asset.path`. */
  assets?: Record<string, Uint8Array>;
  validate?: ValidateOptions;
  /** ISO timestamp stored in the manifest (default: the manifest's updatedAt/createdAt or a fixed epoch ⇒ deterministic). */
  createdAt?: string;
  /** Build even if validation reports ERRORs (never used by the editor). */
  force?: boolean;
}
export interface BuildResult { bytes: Uint8Array; manifest: PackageManifest; report: ValidationReport; files: IndexEntry[] }

// ── build ───────────────────────────────────────────────────────────────────────────────────────────────────────
export function buildPackage(docIn: MapDocument, opts: BuildOptions = {}): BuildResult {
  const registry = new PrefabRegistry(docIn.prefabs);
  const doc = JSON.parse(JSON.stringify(docIn)) as MapDocument;
  // derived manifest fields
  doc.manifest.checkpointCount = doc.checkpoints.length;
  doc.manifest.requirements = { ...doc.manifest.requirements, minFormatVersion: MAP_FORMAT_VERSION, capabilities: capabilitiesOf(doc, registry) };
  if (!(doc.manifest.mapSize.width > 0)) doc.manifest.mapSize = { width: doc.world.bounds.maxX - doc.world.bounds.minX, height: doc.world.bounds.maxY - doc.world.bounds.minY };
  const report = validateMap(doc, opts.validate);
  if (!report.ok && !opts.force) throw new PackageError(`map has ${report.counts.error} validation error(s): ${report.issues.filter(i => i.severity === 'ERROR').slice(0, 3).map(i => `${i.code} ${i.message}`).join('; ')}`, report.issues);

  const files = new Map<string, Uint8Array>();
  const add = (path: string, data: Uint8Array | string): void => {
    const why = checkPath(path);
    if (why) throw new PackageError(`file "${path}": ${why}`);
    if (files.has(path)) throw new PackageError(`duplicate file "${path}"`);
    files.set(path, typeof data === 'string' ? utf8(data) : data);
  };

  // chunks
  const paths = new Map(doc.paths.map(p => [p.id, buildCurve(p)]));
  const src = documentChunkSource(doc, e => buildInstance(resolveEntity(e, registry).entity, paths).extent);
  const infos = src.listChunks();
  for (const info of infos) add(`chunks/chunk_${info.id}.json`, JSON.stringify({ id: info.id, entities: src.readChunk(info.id).entities }));
  add('chunks/index.json', JSON.stringify({ chunks: infos }));
  // core
  const core = { ...doc, entities: [] as MapDocument['entities'] };
  add('core.json', serializeMap(normalizeMap(core as never)));
  // assets
  for (const a of doc.assets) {
    const why = checkPath(a.path);
    if (why) throw new PackageError(`asset "${a.id}": ${why}`);
    if (!ASSET_EXT[a.kind].includes(extOf(a.path))) throw new PackageError(`asset "${a.id}" (${a.kind}): extension ".${extOf(a.path)}" is not valid for this kind`);
    const data = opts.assets?.[a.path];
    if (!data) throw new PackageError(`asset "${a.id}": no content supplied for "${a.path}"`);
    add(a.path, data);
  }
  // metadata
  for (const [lang, loc] of Object.entries(doc.metadata.localized ?? {})) if (loc.description) add(`metadata/description.${lang}.txt`, loc.description);

  const bytes = assemble(files, doc, opts, infos.length);
  const reader = PackageReader.open(bytes);
  return { bytes, manifest: reader.packageManifest(), report, files: reader.entries() };
}

function assemble(files: Map<string, Uint8Array>, doc: MapDocument, opts: BuildOptions, chunkCount: number): Uint8Array {
  const sortedPaths = [...files.keys()].sort();
  const createdAt = opts.createdAt ?? doc.manifest.updatedAt ?? doc.manifest.createdAt ?? '1970-01-01T00:00:00.000Z';
  // manifest file needs the index CRC of everything else ⇒ two-pass: content CRC over the sorted (path, crc) list
  const entries: IndexEntry[] = [];
  let offset = 0;
  const crcs = sortedPaths.map(p => crc32(files.get(p)!));
  const contentCrc = crc32(utf8(sortedPaths.map((p, i) => `${p}:${crcs[i]}`).join('\n')));
  const total = [...files.values()].reduce((n, f) => n + f.length, 0);
  const pm: PackageManifest = { manifest: doc.manifest, package: { containerVersion: CONTAINER_VERSION, files: sortedPaths.length + 1, bytes: total, chunks: chunkCount, createdAt, contentCrc32: contentCrc } };
  const mfData = utf8(JSON.stringify(pm, null, 2) + '\n');
  const all = new Map(files); all.set('map_manifest.json', mfData);
  if (all.size > LIMITS.maxFiles) throw new PackageError(`too many files (${all.size} > ${LIMITS.maxFiles})`);
  const finalPaths = [...all.keys()].sort();
  for (const p of finalPaths) { const d = all.get(p)!; entries.push({ path: p, offset, length: d.length, crc32: crc32(d), mime: mimeOf(extOf(p)) }); offset += d.length; }
  if (offset > LIMITS.maxBytes) throw new PackageError(`package is larger than ${LIMITS.maxBytes} bytes`);
  const indexBytes = utf8(JSON.stringify({ files: entries }));
  const out = new Uint8Array(16 + indexBytes.length + offset);
  const dv = new DataView(out.buffer);
  out.set(MAGIC, 0); dv.setUint16(4, CONTAINER_VERSION, true); dv.setUint16(6, 0, true); dv.setUint32(8, indexBytes.length, true); dv.setUint32(12, crc32(indexBytes), true);
  out.set(indexBytes, 16);
  let p = 16 + indexBytes.length;
  for (const path of finalPaths) { const d = all.get(path)!; out.set(d, p); p += d.length; }
  return out;
}

// ── read ────────────────────────────────────────────────────────────────────────────────────────────────────────
export class PackageReader implements ChunkSource {
  private readonly byPath = new Map<string, IndexEntry>();
  private chunkInfos: ChunkInfo[] | null = null;
  private coreDoc: MapDocument | null = null;
  readonly stats = { chunksParsed: 0, filesVerified: 0 };

  private constructor(private readonly bytes: Uint8Array, private readonly payloadStart: number, entries: IndexEntry[]) {
    for (const e of entries) this.byPath.set(e.path, e);
  }

  /** Parse and verify the container header and index (file contents are verified lazily, or via `verifyAll`). */
  static open(bytes: Uint8Array): PackageReader {
    if (bytes.length < 16 || MAGIC.some((b, i) => bytes[i] !== b)) throw new PackageError('not a map package (bad magic)');
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const ver = dv.getUint16(4, true);
    if (ver > CONTAINER_VERSION) throw new PackageError(`container version ${ver} is newer than supported (${CONTAINER_VERSION})`);
    const len = dv.getUint32(8, true), crc = dv.getUint32(12, true);
    if (16 + len > bytes.length) throw new PackageError('truncated package (index)');
    const indexBytes = bytes.subarray(16, 16 + len);
    if (crc32(indexBytes) !== crc) throw new PackageError('package index is corrupt (CRC mismatch)');
    let index: { files: IndexEntry[] };
    try { index = JSON.parse(dec.decode(indexBytes)); } catch { throw new PackageError('package index is not valid JSON'); }
    if (!Array.isArray(index.files)) throw new PackageError('package index has no file list');
    if (index.files.length > LIMITS.maxFiles) throw new PackageError('too many files');
    const start = 16 + len;
    let total = 0;
    const seen = new Set<string>();
    for (const e of index.files) {
      const why = checkPath(e.path);
      if (why) throw new PackageError(`file "${e.path}": ${why}`);
      if (seen.has(e.path)) throw new PackageError(`duplicate file "${e.path}"`);
      seen.add(e.path);
      if (!Number.isInteger(e.offset) || !Number.isInteger(e.length) || e.offset < 0 || e.length < 0 || start + e.offset + e.length > bytes.length) throw new PackageError(`file "${e.path}" lies outside the package (truncated?)`);
      total += e.length;
    }
    if (total > LIMITS.maxBytes) throw new PackageError('package is too large');
    return new PackageReader(bytes, start, index.files);
  }

  entries(): IndexEntry[] { return [...this.byPath.values()]; }
  has(path: string): boolean { return this.byPath.has(path); }

  /** File bytes (CRC-verified on every read). */
  file(path: string): Uint8Array {
    const e = this.byPath.get(path);
    if (!e) throw new PackageError(`file "${path}" is not in the package`);
    const d = this.bytes.subarray(this.payloadStart + e.offset, this.payloadStart + e.offset + e.length);
    if (crc32(d) !== e.crc32) throw new PackageError(`file "${path}" is corrupt (CRC mismatch)`);
    this.stats.filesVerified++;
    return d;
  }
  text(path: string): string { return dec.decode(this.file(path)); }
  verifyAll(): void { for (const e of this.byPath.values()) this.file(e.path); }

  packageManifest(): PackageManifest { return JSON.parse(this.text('map_manifest.json')) as PackageManifest; }
  manifest(): MapManifest { return this.packageManifest().manifest; }

  /** Core document (everything except entities). Parsed once. Structural problems throw a MapLoadError. */
  core(): MapDocument {
    if (!this.coreDoc) {
      const raw = JSON.parse(this.text('core.json'));
      const issues = structuralCheck(raw);
      if (issues.length) throw new MapLoadError(issues);
      this.coreDoc = parseMap(raw).doc;
    }
    return this.coreDoc;
  }

  listChunks(): ChunkInfo[] {
    if (!this.chunkInfos) this.chunkInfos = (JSON.parse(this.text('chunks/index.json')) as { chunks: ChunkInfo[] }).chunks;
    return this.chunkInfos;
  }

  /** Parse one chunk file on demand (never cached here — the ChunkManager owns loaded chunks). */
  readChunk(id: string): ChunkData {
    const raw = JSON.parse(this.text(`chunks/chunk_${id}.json`)) as ChunkData;
    this.stats.chunksParsed++;
    return raw;
  }

  /** Reassemble the full document (authoring / tools; defeats streaming on purpose). */
  fullDocument(): MapDocument {
    const doc = JSON.parse(JSON.stringify(this.core())) as MapDocument;
    doc.entities = this.listChunks().flatMap(c => this.readChunk(c.id).entities);
    return doc;
  }
}

export { chunkIdFor, mkIssue };
