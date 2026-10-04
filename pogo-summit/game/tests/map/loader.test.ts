import { describe, expect, it } from 'vitest';
import { MAP_FORMAT, MAP_FORMAT_VERSION } from '../../src/map/schema';
import { MapLoadError } from '../../src/map/MapIssue';
import { newMapDocument, parseMap, serializeMap, structuralCheck, migrateMap } from '../../src/map/MapLoader';
import { smallMapJson } from './fixtures';

describe('MapLoader — load · deserialize · save', () => {
  it('parses a hand-written small map from JSON text and normalises every optional section', () => {
    const { doc, issues } = parseMap(smallMapJson());
    expect(issues).toEqual([]);
    expect(doc.format).toBe(MAP_FORMAT);
    expect(doc.formatVersion).toBe(MAP_FORMAT_VERSION);
    expect(doc.entities.length).toBeGreaterThan(3);
    expect(doc.chunks.mode).toBe('auto-grid');             // defaulted
    expect(doc.world.modes).toEqual({ doubleJump: false, puzzle: false, grapple: false });
    expect(doc.regions).toBeDefined();
  });

  it('save → load round-trip is lossless and the canonical text is a fixed point', () => {
    const a = parseMap(smallMapJson()).doc;
    const t1 = serializeMap(a);
    const b = parseMap(t1).doc;
    expect(b).toEqual(a);
    expect(serializeMap(b)).toBe(t1);
    expect(t1.endsWith('\n')).toBe(true);
    expect(t1.indexOf('"format"')).toBeLessThan(t1.indexOf('"manifest"'));        // schema key order
    expect(t1.indexOf('"manifest"')).toBeLessThan(t1.indexOf('"entities"'));
  });

  it('accepts an object as well as text, and does not mutate its input', () => {
    const raw = JSON.parse(smallMapJson());
    const before = JSON.stringify(raw);
    parseMap(raw);
    expect(JSON.stringify(raw)).toBe(before);
  });

  it('newMapDocument() is a structurally valid empty map (spawn is null until authored)', () => {
    const d = newMapDocument('x');
    expect(structuralCheck(JSON.parse(serializeMap(d)))).toEqual([]);
    expect(d.spawn).toBeNull();
  });

  it('invalid JSON and wrong format are rejected with a MapLoadError listing the issue', () => {
    expect(() => parseMap('{ nope')).toThrow(MapLoadError);
    expect(() => parseMap({ format: 'other', formatVersion: 2 })).toThrow(MapLoadError);
    expect(() => parseMap([])).toThrow(MapLoadError);
  });

  it('a newer format version is refused; an older draft (v1) is migrated', () => {
    const d = JSON.parse(smallMapJson());
    expect(() => parseMap({ ...d, formatVersion: 99 })).toThrow(/newer than supported/);
    const v1 = { format: MAP_FORMAT, formatVersion: 1, manifest: d.manifest, platforms: [
      { id: 'a', x: 0, y: 0, w: 6, h: 3 },
      { id: 'm', x: 10, y: 2, w: 4, h: 1, move: { dx: 3, dy: 0, period: 5, phase: 0.25 } },
    ] };
    const up = migrateMap(v1 as never) as { formatVersion: number; entities: { id: string; type: string; prefab: string; behavior?: { x: { amplitude: number; phase: number } } }[] };
    expect(up.formatVersion).toBe(2);
    expect(up.entities.map(e => [e.id, e.type, e.prefab])).toEqual([['a', 'platform', 'stone_platform'], ['m', 'moving', 'moving_platform']]);
    expect(up.entities[1].behavior!.x).toMatchObject({ amplitude: 3, phase: 0.25 });
    expect(parseMap(v1).doc.formatVersion).toBe(2);
  });

  it('reports every structural problem with a path (invalid map)', () => {
    const d = JSON.parse(smallMapJson());
    d.entities[0].position.x = 'left';
    d.entities[1].type = 'banana';
    d.entities[2].collision = { shape: { kind: 'hexagon' } };
    d.checkpoints = [{ id: 'cp', order: -1 }];
    const issues = structuralCheck(d);
    const paths = issues.map(i => i.path);
    expect(paths).toContain('/entities/0/position/x');
    expect(paths).toContain('/entities/1/type');
    expect(paths).toContain('/entities/2/collision/shape/kind');
    expect(paths.some(p => p.startsWith('/checkpoints/0'))).toBe(true);
    expect(issues.every(i => i.severity === 'ERROR')).toBe(true);
    expect(() => parseMap(d)).toThrow(MapLoadError);
    const lenient = parseMap(d, { lenient: true });                 // the editor can still open it
    expect(lenient.issues.length).toBe(issues.length);
  });

  it('rejects NaN / Infinity coordinates', () => {
    const d = JSON.parse(smallMapJson());
    d.entities[0].position.y = Infinity;
    expect(structuralCheck(d).some(i => i.path === '/entities/0/position/y')).toBe(true);
  });

  it('rejects ids with illegal characters', () => {
    const d = JSON.parse(smallMapJson());
    d.entities[0].id = 'bad id!';
    expect(structuralCheck(d).some(i => i.path === '/entities/0/id')).toBe(true);
  });
});
