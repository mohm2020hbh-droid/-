import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { LEVEL_01 } from '../../src/data/levels/level01';
import { PHYSICS_TEST } from '../../src/data/levels/physicsTest';
import type { LevelData } from '../../src/data/LevelData';
import { PhysicsWorld } from '../../src/sim/PhysicsWorld';
import { PogoPhysicsController } from '../../src/sim/PogoPhysicsController';
import { createPhysicsConfig } from '../../src/sim/PhysicsConfig';
import { NEUTRAL_INPUT, type PogoInput } from '../../src/sim/PogoState';
import { MapRuntime } from '../../src/map/MapRuntime';
import { compileRenderLevel, levelDataToMap } from '../../src/map/MapCompile';
import { parseMap, serializeMap } from '../../src/map/MapLoader';
import { validateMap } from '../../src/map/MapValidator';

const cfg = createPhysicsConfig();
const fx = JSON.parse(readFileSync(join(process.cwd(), 'tests/fixtures/level01.route.json'), 'utf8')) as { script: [number, number][] };
const frames: PogoInput[] = fx.script.map(([tilt, held]) => ({ ...NEUTRAL_INPUT, tilt, jumpHeld: !!held }));

function colliderSummary(w: PhysicsWorld) {
  return Object.fromEntries(w.colliders.map(c => [c.id, { kind: c.kind, surface: c.surface, material: c.material, safe: c.safe, pts: c.poly.pts, move: !!c.move }]));
}

describe('migration LevelData → MapDocument (SPEC §19.1)', () => {
  for (const level of [LEVEL_01, PHYSICS_TEST] as LevelData[]) {
    describe(level.levelId, () => {
      const doc = levelDataToMap(level);

      it('the migrated document survives save → load unchanged and has no validation errors', () => {
        const text = serializeMap(doc);
        expect(parseMap(text).doc).toEqual(doc);
        const rep = validateMap(doc);
        expect(rep.issues.filter(i => i.severity === 'ERROR')).toEqual([]);
      });

      it('builds the same collider set as PhysicsWorld(level): ids, geometry, kinds, surfaces, safety, iteration order', () => {
        const base = new PhysicsWorld(level);
        const rt = new MapRuntime(doc, { cfg, streaming: false });
        expect(colliderSummary(rt.world)).toEqual(colliderSummary(base));
        expect(rt.world.solids.map(c => c.id)).toEqual(base.solids.map(c => c.id));
        expect(rt.world.triggers.map(c => c.id)).toEqual(base.triggers.map(c => c.id));
      });

      it('the compiled render level keeps the platform / obstacle / hazard lists', () => {
        const back = compileRenderLevel(doc);
        expect(back.platforms.map(p => p.id)).toEqual(level.platforms.map(p => p.id));
        expect(back.movingObjects.map(p => p.id)).toEqual(level.movingObjects.map(p => p.id));
        expect(back.specialSurfaces.map(p => p.id)).toEqual(level.specialSurfaces.map(p => p.id));
        expect(back.obstacles.map(o => o.id)).toEqual(level.obstacles.map(o => o.id));
        expect(back.hazards.map(h => h.id)).toEqual(level.hazards.map(h => h.id));
        expect(back.goal).toEqual(level.goal);
        expect(back.killY).toBe(level.killY);
        expect(back.startPosition).toEqual(level.startPosition);
        for (const p of level.platforms) { const q = back.platforms.find(x => x.id === p.id)!; expect([q.x, q.y, q.w, q.h, q.kind, q.seed, q.decor]).toEqual([p.x, p.y, p.w, p.h, p.kind, p.seed, p.decor]); }
        expect(back.movingObjects.map(p => p.move)).toEqual(level.movingObjects.map(p => ({ dx: p.move!.dx, dy: p.move!.dy, period: p.move!.period, phase: p.move!.phase ?? 0 })));
        expect(back.hints.map(h => h.id)).toEqual(level.hints.map(h => h.id));
        expect(back.landmarks.map(l => l.id)).toEqual(level.landmarks.map(l => l.id));
        if (level.route) expect(back.route).toEqual(level.route);
      });
    });
  }

  it('REPLAY: the recorded LEVEL_01 route produces a bit-identical state trace on the migrated map (and finishes)', () => {
    const base = new PogoPhysicsController(new PhysicsWorld(LEVEL_01), cfg);
    const rt = new MapRuntime(levelDataToMap(LEVEL_01), { cfg, streaming: false });
    const mapPogo = new PogoPhysicsController(rt.world, cfg);
    let finishedAt = -1;
    for (let i = 0; i < frames.length; i++) {
      base.step(frames[i]);
      rt.beforeStep(mapPogo.state);
      const ev = mapPogo.step(frames[i]);
      rt.afterStep(mapPogo.state, ev);
      const a = base.state, b = mapPogo.state;
      if (a.qx !== b.qx || a.qy !== b.qy || a.qvx !== b.qvx || a.qvy !== b.qvy || a.theta !== b.theta || a.omega !== b.omega || a.tick !== b.tick || a.mode !== b.mode || a.jumps !== b.jumps || a.load !== b.load) {
        throw new Error(`state diverged at tick ${i}: base (${a.qx}, ${a.qy}) vs map (${b.qx}, ${b.qy})`);
      }
      if (a.mode === 'FINISHED' && finishedAt < 0) finishedAt = i;
    }
    expect(finishedAt).toBeGreaterThan(0);
    expect(mapPogo.state.mode).toBe('FINISHED');
    expect(rt.finished).toBe(true);
    expect(rt.summary!.completed).toBe(true);
    expect(rt.events.some(e => e.type === 'finish') || rt.summary !== null).toBe(true);
  });

  it('REPLAY with real streaming (default chunking, small cells): the run still reaches the goal with an identical trace', () => {
    const doc = levelDataToMap(LEVEL_01);
    doc.chunks = { ...doc.chunks, mode: 'auto-grid', cell: { w: 12, h: 12 }, defs: [], activateRadius: 40, loadRadius: 56, unloadRadius: 72 };
    const base = new PogoPhysicsController(new PhysicsWorld(LEVEL_01), cfg);
    const rt = new MapRuntime(doc, { cfg });
    const mapPogo = new PogoPhysicsController(rt.world, cfg);
    for (let i = 0; i < frames.length; i++) {
      base.step(frames[i]);
      rt.beforeStep(mapPogo.state);
      rt.afterStep(mapPogo.state, mapPogo.step(frames[i]));
      const a = base.state, b = mapPogo.state;
      if (a.qx !== b.qx || a.qy !== b.qy || a.qvx !== b.qvx || a.qvy !== b.qvy || a.tick !== b.tick) throw new Error(`streamed run diverged at tick ${i}`);
    }
    expect(mapPogo.state.mode).toBe('FINISHED');
    expect(rt.chunks.stats.loads).toBeGreaterThan(rt.chunks.allInfos().length - 1);       // chunks really were streamed
    expect(rt.chunks.stats.unloads).toBeGreaterThan(0);
  });
});
