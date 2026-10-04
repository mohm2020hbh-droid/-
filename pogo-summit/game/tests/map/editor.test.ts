import { describe, expect, it } from 'vitest';
import { MapEditor } from '../../src/map/MapEditor';
import { parseMap, serializeMap } from '../../src/map/MapLoader';
import { PackageReader } from '../../src/map/MapPackage';
import { smallMap } from './fixtures';

const snap = (e: MapEditor): string => JSON.stringify(e.doc);

function fresh(): MapEditor {
  const ed = MapEditor.create({ id: 'authoring', name: 'Authoring test', author: 'tests', bounds: { minX: -20, maxX: 120, minY: -20, maxY: 60 } });
  ed.place('stone_platform', { x: 0, y: 0 }, { id: 'start', properties: { width: 12, thickness: 6 } });
  ed.setSpawn({ x: 0, y: 0 });
  ed.setFinish([{ id: 'finish', position: { x: 40, y: 5 }, shape: { kind: 'box', w: 3, h: 5 } }]);
  ed.setProgressRoute({ id: 'main', kind: 'main', points: [{ x: 0, y: 0 }, { x: 40, y: 5 }] });
  ed.place('stone_platform', { x: 40, y: 3 }, { id: 'end', properties: { width: 10, thickness: 5 } });
  return ed;
}

describe('MapEditor — create / place / transform / delete with undo & redo', () => {
  it('a map is authored entirely through data operations and validates', () => {
    const ed = fresh();
    expect(ed.validate().ok).toBe(true);
    expect(ed.doc.entities.map(e => e.id)).toEqual(['start', 'end']);
    expect(ed.resolved('start').collisions[0].shape).toMatchObject({ kind: 'box', w: 12, h: 6 });
  });

  it('place → move → rotate → scale → duplicate → delete, each undone and redone exactly', () => {
    const ed = fresh();
    const s0 = snap(ed);
    const id = ed.place('moving_platform', { x: 20, y: 4 });
    expect(id).toBe('moving_platform_01'); expect(ed.entity(id)!.type).toBe('moving');
    const s1 = snap(ed);
    ed.move([id], 2, 1); expect(ed.entity(id)!.position).toMatchObject({ x: 22, y: 5 });
    ed.rotate([id], 90, { x: 20, y: 4 });                                           // about a pivot: position orbits
    expect(ed.entity(id)!.rotation).toBe(90); expect(ed.entity(id)!.position.x).toBeCloseTo(20 - 1, 9); expect(ed.entity(id)!.position.y).toBeCloseTo(4 + 2, 9);
    ed.scale([id], 2, 0.5); expect(ed.entity(id)!.scale).toEqual({ x: 2, y: 0.5 });
    const dup = ed.duplicate([id], 5, 0);
    expect(dup).toHaveLength(1); expect(ed.entity(dup[0])!.position.x).toBeCloseTo(ed.entity(id)!.position.x + 5, 9);
    ed.remove([id, dup[0]]); expect(ed.entity(id)).toBeUndefined();
    const s2 = snap(ed);
    expect(ed.history.slice(-6)).toEqual(['place moving_platform', 'move', 'rotate', 'scale', 'duplicate', 'delete']);
    // undo everything
    for (let i = 0; i < 5; i++) expect(ed.undo()).toBe(true);
    expect(snap(ed)).toBe(s1);
    expect(ed.undo()).toBe(true); expect(snap(ed)).toBe(s0);
    // redo everything
    for (let i = 0; i < 6; i++) expect(ed.redo()).toBe(true);
    expect(snap(ed)).toBe(s2);
    expect(ed.redo()).toBe(false);
    // a new edit clears the redo stack
    ed.undo(); ed.move(['start'], 1, 0); expect(ed.canRedo).toBe(false);
  });

  it('delete keeps the document order on undo (entity order = physics order)', () => {
    const ed = fresh();
    ed.place('stone_platform', { x: 10, y: 0 }, { id: 'mid' });
    const order = ed.doc.entities.map(e => e.id);
    ed.remove(['start', 'mid']);
    expect(ed.doc.entities.map(e => e.id)).toEqual(['end']);
    ed.undo(); expect(ed.doc.entities.map(e => e.id)).toEqual(order);
  });

  it('data-driven properties: collision, material, behaviour, property, tags — no code edit to make a moving platform', () => {
    const ed = fresh();
    const id = ed.place('stone_platform', { x: 20, y: 4 }, { id: 'p' });
    ed.setBehavior(id, { type: 'move', mode: 'sine', x: { amplitude: 3, period: 5 } });
    expect(ed.instances().find(i => i.r.id === 'p')!.motion).not.toBeNull();
    ed.setCollision(id, { shape: { kind: 'slope', w: 6, h: 3 }, surface: 'slippery' });
    expect(ed.resolved(id).collisions[0]).toMatchObject({ surface: 'slippery', shape: { kind: 'slope' } });
    ed.setMaterial(id, '@secondary'); expect(ed.entity(id)!.visual!.material).toBe('@secondary');
    ed.setProperty(id, 'width', 9); expect(ed.entity(id)!.properties).toMatchObject({ width: 9 });
    ed.setTags(id, ['a', 'b']); expect(ed.entity(id)!.tags).toEqual(['a', 'b']);
    ed.setBehavior(id, null); expect(ed.entity(id)!.behavior).toBeUndefined();
    ed.setCollision(id, null); expect(ed.resolved(id).collisions).toEqual([]);
    for (let i = 0; i < 7; i++) ed.undo();
    expect(ed.entity(id)!.behavior).toBeUndefined(); expect(ed.entity(id)!.collision).toBeUndefined(); expect(ed.entity(id)!.properties).toBeUndefined();
  });

  it('regions, triggers, checkpoints (manifest count follows), spawn, finish, theme, chunking — all undoable', () => {
    const ed = fresh();
    const before = snap(ed);
    ed.addRegion({ id: 'pit', type: 'kill', shape: { kind: 'box', x: 20, y: -10, w: 60, h: 4 }, enter: [{ op: 'kill' }] });
    ed.setTrigger('lever', { kind: 'circle', x: 10, y: 4, r: 3 }, [{ op: 'setFlag', name: 'f', value: true }]);
    ed.setCheckpoint({ id: 'cp0', order: 0, region: { kind: 'box', x: 20, y: 5, w: 4, h: 8 }, respawn: { x: 20, y: 3 } });
    expect(ed.doc.manifest.checkpointCount).toBe(1);
    ed.setTheme({ ref: 'snow_peaks' }); expect(ed.doc.manifest.theme).toBe('snow_peaks');
    ed.setChunking({ activateRadius: 50, loadRadius: 70, unloadRadius: 100 }); expect(ed.doc.chunks.activateRadius).toBe(50);
    ed.removeCheckpoint('cp0'); expect(ed.doc.manifest.checkpointCount).toBe(0);
    ed.removeRegion('pit'); expect(ed.doc.regions.map(r => r.id)).toEqual(['lever']);
    expect(() => ed.addRegion({ id: 'lever', type: 'trigger', shape: { kind: 'box', x: 0, y: 0, w: 1, h: 1 } })).toThrow(/already exists/);
    while (ed.canUndo && ed.history.length > 5) ed.undo();
    while (ed.canUndo && snap(ed) !== before) { if (!ed.undo()) break; }
    expect(snap(ed)).toBe(before);
  });

  it('errors are explicit: unknown prefab, unknown entity, duplicate id', () => {
    const ed = fresh();
    expect(() => ed.place('no_such_prefab', { x: 0, y: 0 })).toThrow(/unknown prefab/);
    expect(() => ed.move(['ghost'], 1, 1)).toThrow(/no entity/);
    expect(() => ed.place('stone_platform', { x: 0, y: 0 }, { id: 'start' })).toThrow(/already exists/);
    expect(() => ed.removeCheckpoint('x')).toThrow(/no checkpoint/);
  });

  it('history is bounded', () => {
    const ed = fresh();
    for (let i = 0; i < 520; i++) ed.move(['start'], 0.01, 0);
    expect(ed.history.length).toBe(500);
  });
});

describe('MapEditor — preview, validate, build, export', () => {
  it('preview runs the real physics: no input ⇒ the pogo hops in place (0 deaths); a teleport-free run reports counters', () => {
    const ed = fresh();
    const r = ed.preview({ ticks: 600 });
    expect(r.finished).toBe(false); expect(r.deaths).toBe(0); expect(r.jumps).toBeGreaterThan(5);
    expect(Math.abs(r.x)).toBeLessThan(2);
    const streamed = ed.preview({ ticks: 600, fullLoad: false }), full = ed.preview({ ticks: 600, fullLoad: true });
    expect(streamed).toEqual(full);
  });

  it('validate → build → export: the JSON re-opens identically and the package opens with the same manifest', () => {
    const ed = fresh();
    expect(ed.validate().ok).toBe(true);
    const json = ed.exportJson();
    const re = MapEditor.open(json);
    expect(serializeMap(re.doc)).toBe(json);
    const built = ed.build();
    expect(PackageReader.open(built.bytes).manifest().id).toBe('authoring');
    expect(built.manifest.manifest.requirements.capabilities).toContain('chunks');
  });

  it('opening an invalid document is allowed (lenient) so it can be fixed in the editor', () => {
    const bad = smallMap(); (bad.entities as { type: string }[])[0].type = 'banana';
    const ed = MapEditor.open(bad);
    expect(ed.doc.entities.length).toBeGreaterThan(0);
    expect(parseMap(JSON.stringify(bad), { lenient: true }).issues.length).toBeGreaterThan(0);
  });

  it('a build error in the doc is surfaced (validation blocks the package)', () => {
    const ed = fresh(); ed.setSpawn({ x: 0, y: 30 });
    expect(() => ed.build()).toThrow(/validation error/);
  });
});
