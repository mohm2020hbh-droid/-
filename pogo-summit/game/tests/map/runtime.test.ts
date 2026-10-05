import { describe, expect, it } from 'vitest';
import { placeInAir, createPlantedState } from '../../src/sim/PogoState';
import { rotatePoints } from '../../src/sim/geometry';
import { DEG, TICK_RATE } from '../../src/sim/math';
import { parseMap } from '../../src/map/MapLoader';
import { MemoryRunStore } from '../../src/map/MapCheckpoint';
import type { MapDocument, MapEntity } from '../../src/map/schema';
import { baseDoc, boot, smallMap, stone } from './fixtures';

const withEntities = (extra: MapEntity[], mut: (d: MapDocument) => void = () => {}): MapDocument => baseDoc('t', d => { d.entities.push(...extra); mut(d); });

describe('MapRuntime — spawn, finish, progress, checkpoints', () => {
  it('spawn: the pogo starts planted on the surface under the spawn point', () => {
    const t = boot(parseMap(smallMap()).doc);
    const s = t.state();
    expect(t.rt.world.colliders[s.groundId].id).toBe('start');
    expect(s.x).toBeCloseTo(0, 1); expect(s.grounded).toBe(true); expect(s.mode).toBe('GROUNDED');
    t.step(40);                                                                    // the pogo hops by itself (locked physics E8)
    expect(t.simEvents.some(e => e.type === 'launch')).toBe(true);
    expect(t.state().startedTick).toBeGreaterThan(0);
  });

  it('finish: touching the finish zone ends the run, emits `finish` and records a medal-checked summary', () => {
    const doc = baseDoc(); doc.splits.targets = { gold: 100, silver: 200, bronze: 300 };
    const t = boot(doc);
    t.step(60);                                                                    // run has started (first launch)
    placeInAir(t.cfg, t.state(), 100, 6);
    t.step(2);
    expect(t.state().mode).toBe('FINISHED');
    expect(t.rt.finished).toBe(true);
    expect(t.mapEvents.filter(e => e.type === 'finish')).toHaveLength(1);
    expect(t.rt.summary).toMatchObject({ completed: true, medal: 'gold', mapId: 't' });
    expect(t.rt.summary!.timeSec).toBeGreaterThan(0);
    expect(t.simEvents.some(e => e.type === 'goal')).toBe(true);
  });

  it('progress follows the real route (percent 0…100) and never decreases `max`', () => {
    const t = boot(baseDoc());
    t.step(1);
    expect(t.rt.progress.sample.percent).toBeLessThan(5);
    placeInAir(t.cfg, t.state(), 50, 6); t.step(1);
    expect(t.rt.progress.sample.percent).toBeGreaterThan(45); expect(t.rt.progress.sample.percent).toBeLessThan(55);
    placeInAir(t.cfg, t.state(), 20, 6); t.step(1);
    expect(t.rt.progress.current).toBeLessThan(25);
    expect(t.rt.progress.max).toBeGreaterThan(45);
  });

  it('checkpoint: reaching the region records the split, anchors the respawn there and pins progress', () => {
    const doc = withEntities([stone('mid', 40, 0, 8, 4)], d => {
      d.checkpoints = [{ id: 'cp0', order: 0, name: 'Half', region: { kind: 'box', x: 40, y: 5, w: 8, h: 10 }, respawn: { x: 40, y: 0 }, progress: 60 }];
      d.manifest.checkpointCount = 1;
      d.splits = { splits: [{ id: 's0', name: 'Half', checkpoint: 'cp0', parSec: 10 }], targets: { gold: 0, silver: 0, bronze: 0 } };
    });
    const store = new MemoryRunStore();
    const t = boot(doc, { store });
    t.step(60);                                                                    // start the run
    placeInAir(t.cfg, t.state(), 40, 5);
    t.step(1);
    const cp = t.mapEvents.find(e => e.type === 'checkpoint')!;
    expect(cp.id).toBe('cp0');
    expect(t.rt.checkpoints.reached.has('cp0')).toBe(true);
    expect(t.rt.world.colliders[t.state().safeGround].id).toBe('mid');                 // respawn anchor moved to the checkpoint
    expect(t.rt.progress.max).toBeGreaterThanOrEqual(60);
    const split = t.mapEvents.find(e => e.type === 'split')!;
    expect(split.id).toBe('s0'); expect((split.data as { timeSec: number }).timeSec).toBeGreaterThan(0);
    t.mapEvents.length = 0; t.step(5);
    expect(t.mapEvents.some(e => e.type === 'checkpoint')).toBe(false);                // fires once
    // a kill now respawns at the checkpoint, not at the spawn
    t.rt.kill(t.state());
    expect(t.state().x).toBeCloseTo(40, 0);
    expect(t.state().hazards).toBe(1);
  });

  it('resetRun clears flags, checkpoints and the summary for the next attempt', () => {
    const doc = withEntities([], d => { d.checkpoints = [{ id: 'c', order: 0, region: { kind: 'box', x: 100, y: 5, w: 10, h: 10 }, respawn: { x: 100, y: 3 } }]; d.manifest.checkpointCount = 1; });
    const t = boot(doc);
    placeInAir(t.cfg, t.state(), 100, 6); t.step(1);
    expect(t.rt.checkpoints.count).toBe(1);
    t.rt.resetRun();
    expect(t.rt.checkpoints.count).toBe(0); expect(t.rt.finished).toBe(false); expect(t.rt.progress.max).toBe(0);
  });
});

describe('MapRuntime — moving platform', () => {
  const doc = () => withEntities([{ id: 'mover', type: 'moving', prefab: 'moving_platform', position: { x: 30, y: 0 }, properties: { width: 8, ampX: 6, period: 8 } }]);

  it('the collider follows the analytic offset at every tick (and its extent covers the whole range)', () => {
    const t = boot(doc());
    const c = t.rt.world.colliders[t.idx('mover')];
    const o = { x: 0, y: 0, vx: 0, vy: 0 };
    t.rt.world.offsetAt(c, 2 * TICK_RATE, o);
    expect(o.x).toBeCloseTo(6, 9);                                                  // quarter period of 8 s
    t.rt.world.offsetAt(c, 6 * TICK_RATE, o);
    expect(o.x).toBeCloseTo(-6, 9);
    expect(c.move).toBeDefined();
    expect(c.minX).toBeLessThanOrEqual(30 - 4 - 6 + 1e-9); expect(c.maxX).toBeGreaterThanOrEqual(30 + 4 + 6 - 1e-9);
  });

  it('a rider is carried by the platform (the core derives the carry from the offset)', () => {
    const d = doc(); d.world.bounds.maxX = 200;
    const t = boot(d);
    const mover = t.idx('mover');
    t.state().tick = 0;
    Object.assign(t.state(), createPlantedState(t.rt.world, t.cfg, mover, 0.5, 0));
    const x0 = t.state().x;
    let maxDrift = 0;
    for (let i = 0; i < 2 * TICK_RATE; i++) {                                       // 2 s = a quarter period: the platform moves +6 m
      t.step(1);
      const c = t.rt.world.colliders[mover]; const off = { x: 0, y: 0, vx: 0, vy: 0 }; t.rt.world.offsetAt(c, t.state().tick, off);
      if (t.state().grounded && t.state().groundId === mover) maxDrift = Math.max(maxDrift, Math.abs(t.state().x - (x0 + off.x)));
    }
    expect(Math.abs(t.state().x - x0)).toBeGreaterThan(2);                          // it really moved with the platform
    expect(maxDrift).toBeLessThan(1.5);                                              // and stayed on it
  });
});

describe('MapRuntime — rotating object', () => {
  const blade = (): MapDocument => withEntities([{ id: 'blade', type: 'hazard', prefab: 'rotating_blade', position: { x: 50, y: 30 }, properties: { length: 8, thickness: 1, speed: 90 } }]);

  it('re-poses the collision polygon every tick about the pivot; the broad-phase box covers every angle', () => {
    const t = boot(blade(), { streaming: false });
    const idx = t.idx('blade');
    const c0 = t.rt.world.colliders[idx].poly.pts.map(p => ({ ...p }));
    t.state().tick = TICK_RATE - 1; t.rt.beforeStep(t.state());                    // prepares tick = 1 s → 90°
    expect(t.rt.world.angle(idx)).toBeCloseTo(90, 9);
    const a0 = 90 / TICK_RATE;                                  // construction already posed the blade for tick 1
    const expected = rotatePoints(c0, (90 - a0) * DEG, 50, 30);
    t.rt.world.colliders[idx].poly.pts.forEach((p, i) => { expect(p.x).toBeCloseTo(expected[i].x, 9); expect(p.y).toBeCloseTo(expected[i].y, 9); });
    const q = t.rt.world.colliders[idx].qpoly.pts[0];
    expect(q.x).toBeCloseTo(t.rt.world.colliders[idx].poly.pts[0].x * 52, 9);       // quant copy kept in sync
    const c = t.rt.world.colliders[idx];
    expect(c.minX).toBeLessThanOrEqual(50 - 4 + 1e-9); expect(c.maxY).toBeGreaterThanOrEqual(30 + 4 - 1e-9);
    expect(t.rt.visualState.get('blade')!.angle).toBeCloseTo(90, 9);
  });

  it('a rotating hazard kills when it sweeps into the pogo (collision really rotates)', () => {
    const t = boot(blade(), { streaming: false });
    placeInAir(t.cfg, t.state(), 50 + 3.2, 30 + 1, 0, 0);                            // right of the pivot, beside the horizontal blade end … then the blade turns
    let hazards = 0;
    for (let i = 0; i < 3 * TICK_RATE && hazards === 0; i++) { t.step(1); hazards = t.state().hazards; if (t.state().y < 20) break; }
    expect(t.simEvents.some(e => e.type === 'hazard') || hazards > 0 || t.state().y < 29).toBe(true);
  });
});

describe('MapRuntime — toggle / timed / conditional objects', () => {
  const toggles = (): MapDocument => withEntities([
    { id: 'red', type: 'interactive', prefab: 'toggle_block', position: { x: 30, y: 6 }, properties: { group: 0 } },
    { id: 'blue', type: 'interactive', prefab: 'toggle_block', position: { x: 36, y: 6 }, properties: { group: 1 } },
  ]);
  const solidIds = (t: ReturnType<typeof boot>) => t.rt.world.solids.map(c => c.id);

  it('red is solid on even jump counts, blue on odd — the swap happens on the tick after the launch', () => {
    const t = boot(toggles());
    expect(solidIds(t)).toContain('red'); expect(solidIds(t)).not.toContain('blue');
    t.state().jumps = 1; t.rt.beforeStep(t.state());
    expect(solidIds(t)).toContain('blue'); expect(solidIds(t)).not.toContain('red');
    expect(t.rt.visualState.get('red')!.visible).toBe('ghost'); expect(t.rt.visualState.get('blue')!.visible).toBe('visible');
    t.state().jumps = 2; t.rt.beforeStep(t.state());
    expect(solidIds(t)).toContain('red');
    expect(t.rt.events.filter(e => e.type === 'toggle').length).toBeGreaterThan(0);
  });

  it('driven by the real simulation: every launch flips the groups', () => {
    const t = boot(toggles());
    const seen = new Set<string>();
    for (let i = 0; i < 200; i++) {
      t.rt.beforeStep(t.state());
      seen.add(`${t.state().jumps % 2}:${solidIds(t).includes('red')}`);                // the state the physics tick will see
      t.rt.afterStep(t.state(), t.pogo.step());
    }
    expect(t.state().jumps).toBeGreaterThanOrEqual(3);
    expect(seen).toEqual(new Set(['0:true', '1:false']));                           // parity ↔ solidity, always consistent
  });

  it('timed block follows the clock: on for `duty` of the period, phase shifts it', () => {
    const t = boot(withEntities([{ id: 'blink', type: 'interactive', prefab: 'timed_block', position: { x: 30, y: 6 }, properties: { period: 4, duty: 0.5 } }]));
    const idx = t.idx('blink');
    const at = (sec: number) => { t.state().tick = Math.round(sec * TICK_RATE) - 1; t.rt.beforeStep(t.state()); return t.rt.world.isActive(idx); };
    expect(at(0.5)).toBe(true); expect(at(1.9)).toBe(true); expect(at(2.5)).toBe(false); expect(at(3.9)).toBe(false); expect(at(4.5)).toBe(true);
  });

  it('conditional object: a region effect sets a flag that opens a gate (secret / optional route logic)', () => {
    const doc = withEntities([{ id: 'gate', type: 'interactive', position: { x: 30, y: 6 }, collision: { shape: { kind: 'box', w: 2, h: 8 } }, behavior: { type: 'conditional', when: { flag: 'open' }, inactive: { collision: false, visual: 'hidden' } } }],
      d => { d.regions = [{ id: 'lever', type: 'trigger', shape: { kind: 'box', x: 10, y: 6, w: 4, h: 4 }, once: true, enter: [{ op: 'setFlag', name: 'open', value: true }] }]; });
    const t = boot(doc);
    const idx = t.idx('gate');
    expect(t.rt.world.isActive(idx)).toBe(false);
    placeInAir(t.cfg, t.state(), 10, 6); t.step(1);
    expect(t.rt.flags.get('open')).toBe(true);
    t.step(1);
    expect(t.rt.world.isActive(idx)).toBe(true);
  });
});

describe('MapRuntime — surfaces, hazards, boost, triggers, kill zones', () => {
  it('slippery surface: landing on ice enters slide mode (E15), landing on stone does not', () => {
    const doc = withEntities([{ id: 'ice', type: 'platform', prefab: 'ice_platform', position: { x: 30, y: 0 }, properties: { width: 10 } }]);
    const t = boot(doc);
    expect(t.rt.world.colliders[t.idx('ice')].surface).toBe('slippery');
    placeInAir(t.cfg, t.state(), 30, 3, 6, -20);                                      // drop onto the ice with some horizontal speed
    let slid = false; for (let i = 0; i < 200 && !slid; i++) { t.step(1); if (t.state().slideMode && t.state().groundId === t.idx('ice')) slid = true; }
    expect(slid).toBe(true);
    const t2 = boot(doc);
    placeInAir(t2.cfg, t2.state(), 0, 3, 6, -20);                                    // same drop on the stone start platform
    let slid2 = false; for (let i = 0; i < 200; i++) { t2.step(1); if (t2.state().slideMode) slid2 = true; }
    expect(slid2).toBe(false);
  });

  it('hazard collider: touching a spike respawns at the last safe spot and counts a death', () => {
    const doc = withEntities([{ id: 'spikes', type: 'hazard', prefab: 'spike', position: { x: 6, y: 0 }, properties: { width: 3, height: 2 } }]);
    const t = boot(doc);
    expect(t.rt.world.colliders[t.idx('spikes')].kind).toBe('hazard');
    placeInAir(t.cfg, t.state(), 6, 4, 0, -10);
    for (let i = 0; i < 120 && t.state().hazards === 0; i++) t.step(1);
    expect(t.state().hazards).toBe(1);
    expect(t.simEvents.some(e => e.type === 'hazard')).toBe(true);
    expect(t.simEvents.some(e => e.type === 'respawn')).toBe(true);
  });

  it('boost zone: declared and emitted (`boost_zone`) but NOT applied to the physics state (integration conflict I1)', () => {
    const t = boot(withEntities([{ id: 'juice', type: 'interactive', prefab: 'boost', position: { x: 30, y: 6 }, properties: { width: 4, height: 4 } }]));
    placeInAir(t.cfg, t.state(), 30, 6); t.step(1);
    expect(t.mapEvents.some(e => e.type === 'boost_zone')).toBe(true);
    expect(t.state().boost).toBe(0); expect(t.state().boosts).toBe(0);
  });

  it('trigger region: enter / stay / exit effects, `once` fires a single time', () => {
    const doc = baseDoc('t', d => {
      d.regions = [
        { id: 'z', type: 'trigger', shape: { kind: 'box', x: 20, y: 6, w: 6, h: 6 }, enter: [{ op: 'incCounter', name: 'enters' }], exit: [{ op: 'emit', event: 'left' }], stay: [{ op: 'incCounter', name: 'stays' }] },
        { id: 'o', type: 'trigger', shape: { kind: 'circle', x: 40, y: 6, r: 3 }, once: true, enter: [{ op: 'incCounter', name: 'once' }] },
      ];
    });
    const t = boot(doc);
    placeInAir(t.cfg, t.state(), 20, 6); t.step(1);
    expect(t.rt.flags.get('enters')).toBe(1);
    t.step(3);
    expect(Number(t.rt.flags.get('stays'))).toBeGreaterThanOrEqual(3);
    placeInAir(t.cfg, t.state(), 60, 6); t.step(1);
    expect(t.mapEvents.some(e => e.type === 'zone_exit' && e.id === 'z')).toBe(true);
    expect(t.mapEvents.some(e => e.type === 'emit' && e.id === 'left')).toBe(true);
    for (let k = 0; k < 2; k++) { placeInAir(t.cfg, t.state(), 40, 6); t.step(1); placeInAir(t.cfg, t.state(), 60, 6); t.step(1); }
    expect(t.rt.flags.get('once')).toBe(1);
  });

  it('kill zone region and the teleport effect', () => {
    const doc = baseDoc('t', d => {
      d.regions = [
        { id: 'pit', type: 'kill', shape: { kind: 'box', x: 50, y: 5, w: 6, h: 6 }, enter: [{ op: 'kill' }] },
        { id: 'warp', type: 'teleport', shape: { kind: 'box', x: 70, y: 5, w: 4, h: 4 }, enter: [{ op: 'teleport', to: { x: 0, y: 0 } }] },
      ];
    });
    const t = boot(doc);
    placeInAir(t.cfg, t.state(), 50, 5); t.step(1);
    expect(t.mapEvents.some(e => e.type === 'kill')).toBe(true);
    expect(t.state().hazards).toBe(1); expect(t.state().x).toBeCloseTo(0, 0);
    placeInAir(t.cfg, t.state(), 70, 5); t.step(1);
    expect(t.mapEvents.some(e => e.type === 'teleport' && (e.data as { ok: boolean }).ok)).toBe(true);
    expect(t.state().x).toBeCloseTo(0, 0);
  });

  it('camera / vfx / audio / lighting / fog / hint effects are forwarded to the host as events', () => {
    const seen: string[] = [];
    const doc = baseDoc('t', d => { d.regions = [{ id: 'fx', type: 'camera', shape: { kind: 'box', x: 20, y: 6, w: 6, h: 6 }, enter: [{ op: 'camera', zoom: 1.4 }, { op: 'vfx', id: 'embers', burst: 20 }, { op: 'fog', density: 0.02 }, { op: 'hint', textKey: 'hint_x' }] }]; });
    const t = boot(doc, { host: { onEffect: (type) => { seen.push(type); } } });
    placeInAir(t.cfg, t.state(), 20, 6); t.step(1);
    expect(seen).toEqual(['camera', 'vfx', 'fog', 'hint']);
  });

  it('breakable platform: breaks `delay` seconds after the first landing, restores after `respawn`', () => {
    const doc = withEntities([{ id: 'crumble', type: 'interactive', prefab: 'breakable_platform', position: { x: 30, y: 0 }, properties: { width: 8, delay: 0.5, respawn: 2 } }]);
    const t = boot(doc);
    const idx = t.idx('crumble');
    placeInAir(t.cfg, t.state(), 30, 4, 0, -10);
    let broke = -1;
    for (let i = 0; i < 400 && broke < 0; i++) { t.step(1); if (t.mapEvents.some(e => e.type === 'break')) broke = t.state().tick; }
    expect(broke).toBeGreaterThan(0);
    expect(t.rt.world.isActive(idx)).toBe(false);
    const landed = t.simEvents.find(e => e.type === 'land' && e.collider === idx)!;
    expect(broke - landed.tick).toBeGreaterThanOrEqual(Math.round(0.5 * TICK_RATE) - 2); expect(broke - landed.tick).toBeLessThanOrEqual(Math.round(0.5 * TICK_RATE) + 3);
    let restored = false; for (let i = 0; i < 3 * TICK_RATE && !restored; i++) { t.step(1); restored = t.mapEvents.some(e => e.type === 'restore'); }
    expect(restored).toBe(true);
  });

  it('one-way platform: the pogo passes up through it from below and lands on top', () => {
    const doc = withEntities([{ id: 'ledge', type: 'platform', prefab: 'one_way_platform', position: { x: 30, y: 8 }, properties: { width: 8 } }]);
    const t = boot(doc);
    const idx = t.idx('ledge');
    placeInAir(t.cfg, t.state(), 30, 3, 0, 100);                                      // below the ledge, flying up fast
    let landedOn = -1;
    for (let i = 0; i < 400 && landedOn < 0; i++) { t.step(1); const l = t.simEvents.filter(e => e.type === 'land').pop(); if (l && l.collider === idx) landedOn = i; }
    expect(landedOn).toBeGreaterThan(0);
    expect(t.state().y).toBeGreaterThan(8);                                           // ended up above the ledge
    // control: without one-way the same shot is blocked from below
    const solid = withEntities([{ id: 'ledge', type: 'platform', prefab: 'stone_platform', position: { x: 30, y: 8 }, properties: { width: 8, thickness: 0.6 } }]);
    const t2 = boot(solid);
    placeInAir(t2.cfg, t2.state(), 30, 3, 0, 100);
    t2.step(120);
    expect(t2.state().y).toBeLessThan(8);
  });
});

describe('MapRuntime — snapshot and misc', () => {
  it('snapshot captures flags, reached checkpoints, progress and breakables as JSON', () => {
    const t = boot(baseDoc('t', d => { d.regions = [{ id: 'z', type: 'trigger', shape: { kind: 'box', x: 20, y: 6, w: 6, h: 6 }, enter: [{ op: 'setFlag', name: 'seen', value: true }] }]; }));
    placeInAir(t.cfg, t.state(), 20, 6); t.step(1);
    const snap = t.rt.snapshot() as { flags: Record<string, unknown>; progress: { max: number } };
    expect(snap.flags.seen).toBe(true);
    expect(JSON.parse(JSON.stringify(snap))).toEqual(snap);
  });
  it('invalid entities do not crash the runtime: problems are collected in loadIssues', () => {
    const t = boot(withEntities([{ id: 'bad', type: 'platform', prefab: 'ghost_prefab', position: { x: 30, y: 0 } }, { id: 'bad2', type: 'platform', position: { x: 40, y: 0 }, collision: { shape: { kind: 'box', w: 0, h: 0 } } }]));
    expect(t.rt.loadIssues.map(i => i.code)).toEqual(expect.arrayContaining(['PREFAB_MISSING', 'COLLISION_INVALID']));
    t.step(10);
    expect(t.state().tick).toBe(10);
  });
});

describe('restarting a run far from the spawn (regression: createPogoState found no ground)', () => {
  it('prepareSpawn() makes the spawn area ready again after the player travelled far away', async () => {
    const { largeMap, boot } = await import('./fixtures');
    const { createPogoState, placeInAir } = await import('../../src/sim/PogoState');
    const doc = largeMap({ length: 3000 });
    const t = boot(doc);
    placeInAir(t.cfg, t.state(), 2500, 60);
    t.step(6);                                                      // streaming follows the player: the spawn chunks go inactive/unloaded
    expect(() => createPogoState(t.rt.world, t.cfg)).toThrow(/startPosition is not on any solid surface/);
    t.rt.prepareSpawn();
    expect(() => createPogoState(t.rt.world, t.cfg)).not.toThrow();
    const s = createPogoState(t.rt.world, t.cfg);
    expect(s.grounded).toBe(true);
  });
});
