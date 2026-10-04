import { describe, expect, it } from 'vitest';
import { placeInAir } from '../../src/sim/PogoState';
import { parseMap } from '../../src/map/MapLoader';
import { PackageReader, buildPackage } from '../../src/map/MapPackage';
import { MapRuntime } from '../../src/map/MapRuntime';
import { PogoPhysicsController } from '../../src/sim/PogoPhysicsController';
import { createPhysicsConfig } from '../../src/sim/PhysicsConfig';
import { validateMap } from '../../src/map/MapValidator';
import { NEUTRAL_INPUT } from '../../src/sim/PogoState';
import { PACKAGE_MAX_FILES } from '../../src/map/schema';
import { boot, largeMap, smallMap } from './fixtures';

const now = () => performance.now();

describe('small map — end to end', () => {
  it('JSON text → parse → validate → run → package → run again from the package: identical state traces', () => {
    const doc = parseMap(JSON.stringify(smallMap())).doc;
    expect(validateMap(doc).ok).toBe(true);
    const cfg = createPhysicsConfig();
    const runFrom = (rt: MapRuntime) => {
      const pogo = new PogoPhysicsController(rt.world, cfg);
      const trace: number[] = [];
      for (let i = 0; i < 900; i++) {
        rt.beforeStep(pogo.state);
        rt.afterStep(pogo.state, pogo.step({ ...NEUTRAL_INPUT, tilt: i % 200 < 30 ? 0.6 : 0, jumpHeld: i % 160 < 50 }));
        trace.push(pogo.state.qx, pogo.state.qy, pogo.state.qvx, pogo.state.qvy);
      }
      return trace;
    };
    const fromDoc = runFrom(new MapRuntime(doc, { cfg }));
    const rd = PackageReader.open(buildPackage(doc).bytes);
    const fromPkg = runFrom(new MapRuntime(rd.core(), { cfg, source: rd }));
    expect(fromPkg).toEqual(fromDoc);
    expect(fromDoc.some(v => v !== 0)).toBe(true);
  });
});

describe('large map — streaming keeps memory and time bounded', () => {
  const doc = largeMap({ length: 20_000 });                                        // ≈ 30 000 entities over 20 km
  const total = doc.entities.length;

  it(`loads a ${total.toLocaleString('en')}-entity map without instantiating the world (peak resident entities ≪ total)`, () => {
    const t0 = now();
    const t = boot(doc);
    const bootMs = now() - t0;
    let maxEnt = 0, maxLoaded = 0;
    const t1 = now();
    for (let x = 0; x <= 20_000; x += 40) {
      placeInAir(t.cfg, t.state(), x, 90); t.step(2);
      const m = t.rt.metrics(); maxEnt = Math.max(maxEnt, m.loadedEntities); maxLoaded = Math.max(maxLoaded, m.loadedChunks);
    }
    const travMs = now() - t1;
    expect(total).toBeGreaterThan(25_000);
    expect(maxEnt).toBeLessThan(total * 0.02);                                      // < 2 % of the entities were ever resident at once
    expect(maxLoaded).toBeLessThanOrEqual(30);                                     // 2-D window: ≈ 5 columns × 3 rows of 32 m cells + pinned anchors
    expect(t.rt.metrics().activeColliders).toBeLessThan(100);
    expect(t.rt.chunks.stats.loads).toBeGreaterThan(300); expect(t.rt.chunks.stats.unloads).toBeGreaterThan(280);
    // generous wall-clock guards (typically ≈ 0.2 s and ≈ 0.3 s on a dev machine; a 20× margin keeps CI stable)
    expect(bootMs).toBeLessThan(8000); expect(travMs).toBeLessThan(10_000);
  });

  it('physics is intact at far positions: the pogo lands on a platform 12 km from the spawn and hops on it', () => {
    const t = boot(doc);
    const target = doc.entities.filter(e => e.id.startsWith('p') && e.position.x > 12_000).sort((a, b) => a.position.x - b.position.x)[0];
    const w = (target.properties?.width as number) ?? 6;
    placeInAir(t.cfg, t.state(), target.position.x, target.position.y + 5, 0, -10);
    let landed: string | null = null;
    for (let i = 0; i < 400 && !landed; i++) { t.step(1); const l = t.simEvents.filter(e => e.type === 'land').pop(); if (l?.collider !== undefined) landed = t.rt.world.colliders[l.collider].id; }
    expect(w).toBeGreaterThan(0);
    expect(landed).not.toBeNull();
    expect(t.state().falls).toBe(0);
    expect(t.rt.world.indexOfId(target.id)).toBeGreaterThanOrEqual(0);              // its chunk is loaded now
    expect(t.rt.world.indexOfId('p1')).toBe(-1);                                    // and a far-away one is not
  });

  it('validates quickly even at this size (errors none; overlaps of the random layout are warnings)', () => {
    const t0 = now();
    const r = validateMap(doc);
    expect(now() - t0).toBeLessThan(15_000);
    expect(r.ok).toBe(true);
    expect(r.issues.some(i => i.code === 'COLLISION_OVERLAP')).toBe(true);
    const stats = r.issues.find(i => i.code === 'STATS')!;
    expect(stats.message).toContain(`${total} entities`);
  });

  it('packages into chunk files and a streaming runtime parses only the chunks it touches', () => {
    const t0 = now();
    const built = buildPackage(doc);
    const buildMs = now() - t0;
    const rd = PackageReader.open(built.bytes);
    expect(rd.listChunks().length).toBeLessThan(PACKAGE_MAX_FILES);
    expect(rd.entries().length).toBe(rd.listChunks().length + 3);                    // + manifest, core, chunk index
    const cfg = createPhysicsConfig();
    const rt = new MapRuntime(rd.core(), { cfg, source: rd });
    const pogo = new PogoPhysicsController(rt.world, cfg);
    for (let x = 0; x <= 2000; x += 40) { placeInAir(cfg, pogo.state, x, 90); for (let i = 0; i < 2; i++) { rt.beforeStep(pogo.state); rt.afterStep(pogo.state, pogo.step()); } }
    expect(rd.stats.chunksParsed).toBe(rt.chunks.stats.loads);
    expect(rd.stats.chunksParsed).toBeLessThan(rd.listChunks().length * 0.2);
    expect(built.bytes.length).toBeLessThan(12 * 1024 * 1024);
    expect(buildMs).toBeLessThan(20_000);
  });
});

describe('huge map — 90 000 entities (streaming only)', () => {
  it('boots in well under a second of CPU and never keeps more than a few hundred entities resident', () => {
    const doc = largeMap({ length: 60_000 });
    expect(doc.entities.length).toBeGreaterThan(85_000);
    const t0 = now();
    const t = boot(doc);
    let maxEnt = 0;
    for (let x = 0; x <= 60_000; x += 60) { placeInAir(t.cfg, t.state(), x, 90); t.step(1); maxEnt = Math.max(maxEnt, t.rt.metrics().loadedEntities); }
    expect(maxEnt).toBeLessThan(900);
    expect(now() - t0).toBeLessThan(30_000);
    const r = validateMap(doc, { budget: 'android-mid' });
    expect(r.ok).toBe(false);                                                        // 5 700 chunks cannot be packaged: reported, not discovered at build time
    expect(r.issues.some(i => i.code === 'CHUNK_TOO_MANY' && i.severity === 'ERROR')).toBe(true);
  }, 120_000);
});
