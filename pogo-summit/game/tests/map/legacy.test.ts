import { describe, expect, it } from 'vitest';
import { importLegacyWmp } from '../../src/map/legacyWmp';
import { parseMap, serializeMap } from '../../src/map/MapLoader';
import { validateMap } from '../../src/map/MapValidator';
import type { BehaviorDef } from '../../src/map/schema';

/**
 * Synthetic sample in the structure of the legacy text export (own numbers, own names — NOT a legacy map).
 * 52 units = 1 m; legacy axes: x, y = depth, z = up.
 */
const bits = (...flags: number[]): string => { let v = 0; for (const f of flags) v |= 1 << f; return v.toString(2).padStart(32, '0'); };
const model = (o: { file: string; name: string; action: string; pos: [number, number, number]; skills?: number[]; flagBits?: string; scale?: number }) => `{ //model
	7
	${o.pos.join(' ')}
	0 0 0
	${o.scale ?? 1} ${o.scale ?? 1} ${o.scale ?? 1}
	${o.file}
	${o.name}
	${o.action}
	${Array.from({ length: 20 }, (_, i) => o.skills?.[i] ?? 0).join(' ')}
	${o.flagBits ?? bits(17, 26)}
	0
	50
	0
	0
	someMaterial
}
`;
const SAMPLE = `// wmpio (1.30), 01.01.2024

version 711


{ //level
	0
	map.$$w
	palette.pcx
	{   // 0
		vertices 8
		faces 6
		0 -100 0   0 100 0   520 -100 0   520 100 0   0 -100 -104   0 100 -104   520 -100 -104   520 100 -104
		4  1 0 2 3 : #default ( 0 1 0 0 ) ( 0 0 -1 0 ) : 0011 0 50 ndef
		{
			0000
			ndef
			0
		}
	}
}
{ //sun
	5
	0
	60
}
{ //start
	1
	260 0 52
	90 0 0
	pos_000
}
${model({ file: 'block.mdl', name: 'mover', action: 'moveSine_act', pos: [1040, 0, 52], skills: [104, 52, 5, 5, 0, 90] })}
${model({ file: 'wheel.mdl', name: 'spin', action: 'map3Wheel_act', pos: [1560, 0, 260], skills: [2], flagBits: bits(2, 17, 26) })}
${model({ file: 'wheel.mdl', name: 'swing', action: 'map3Wheel_act', pos: [1664, 0, 260], skills: [1, 10, 30], flagBits: bits(3, 17, 26) })}
${model({ file: 'block.mdl', name: 'toggleBlock_blue', action: 'toggleBlock_act', pos: [2080, 0, 52], flagBits: bits(3, 17, 26) })}
${model({ file: 'block.mdl', name: 'toggleBlock_red', action: 'toggleBlock_act', pos: [2184, 0, 52], flagBits: bits(17, 26) })}
${model({ file: 'block.mdl', name: 'blink', action: 'toggleSine_act', pos: [2288, 0, 52], skills: [5, 180], flagBits: bits(4, 17, 26) })}
${model({ file: 'ice.mdl', name: 'ice_COL', action: 'ndef', pos: [2600, 0, 0], flagBits: bits(6, 8, 17, 26) })}
${model({ file: 'ice.mdl', name: 'iceVisual', action: 'ndef', pos: [2600, 0, 0], flagBits: bits(9, 17, 26) })}
${model({ file: 'thorn.mdl', name: 'thorn', action: 'monolithThorn_act', pos: [2900, 0, 0] })}
${model({ file: 'juice.mdl', name: 'juice', action: 'boostjuice_act', pos: [3000, 0, 52] })}
${model({ file: 'cloud.mdl', name: 'cloud', action: 'bgObject_act', pos: [1000, 2000, 1500], skills: [0.5, 0.25], flagBits: bits(9) })}
${model({ file: 'mush.mdl', name: 'mushroom', action: 'mushroom_act', pos: [500, 0, 0], flagBits: bits(3, 17, 26) })}
{ //path
	6
	path_progress
	3
	28
	260 0 52
	1560 0 260
	3120 0 52
	0 0 0 0 0 0
	0 0 0 0 0 0
	0 0 0 0 0 0
	1 2 1000 0 0 0
	2 3 1000 0 0 0
}
{ //path
	6
	thornsTargetPath
	2
	8
	2900 0 100
	2900 0 300
	0 0 0 0 0 0
	0 0 0 0 0 0
	1 2 200 0 0 0
}
{ //region
	8
	-5200 -64 -2600
	5200 64 -2400
	kill
}
{ //region
	8
	1400 256 0
	1800 384 520
	CP_0
}
{ //region
	8
	2900 256 0
	3300 384 520
	CP_1
}
{ //region
	8
	3100 -64 0
	3300 64 520
	reg_finish
}
{ //region
	8
	0 0 0
	10 10 10
	mystery_zone
}
`;

describe('legacyWmp importer (developer tool, synthetic sample)', () => {
  const { doc, report } = importLegacyWmp(SAMPLE, { id: 'imported_sample', name: 'Imported', splitNames: ['First', 'Second'] });
  const ent = (id: string) => doc.entities.find(e => e.id.startsWith(id))!;
  const beh = (id: string): BehaviorDef => { const b = ent(id).behavior; return (Array.isArray(b) ? b[0] : b)!; };

  it('converts units (52 per metre) and axes (legacy z → y); spawn, finish, checkpoints, kill zone, route', () => {
    expect(doc.spawn!.position).toEqual({ x: 5, y: 1 });
    expect(doc.finish.zones[0]).toMatchObject({ position: { x: 61.5385, y: 5 }, shape: { kind: 'box', w: 3.8462, h: 10 } });
    expect(doc.checkpoints.map(c => [c.id, c.order, c.name])).toEqual([['cp0', 0, 'First'], ['cp1', 1, 'Second']]);
    expect(doc.checkpoints[0].region).toMatchObject({ kind: 'box', x: 30.7692, y: 5, w: 7.6923, h: 10 });
    expect(doc.regions.find(r => r.type === 'kill')!.enter).toEqual([{ op: 'kill' }]);
    const main = doc.progress.routes[0];
    expect(main).toMatchObject({ id: 'main', kind: 'main' }); expect(main.points).toEqual([{ x: 5, y: 1 }, { x: 30, y: 5 }, { x: 60, y: 1 }]);
    expect(doc.paths.map(p => p.id)).toEqual(['thornsTargetPath']);
    expect(doc.manifest.checkpointCount).toBe(2);
    expect(doc.splits.splits.map(s => s.name)).toEqual(['First', 'Second']);
  });

  it('block brushes become convex collision (XZ hull); blocks never carry a model or texture', () => {
    const b = doc.entities.find(e => e.id === 'block_0')!;
    expect(b.collision && !Array.isArray(b.collision) && b.collision.shape.kind).toBe('convex');
    const pts = (b.collision as { shape: { points: { x: number; y: number }[] } }).shape.points;
    expect(pts).toHaveLength(4); expect(Math.max(...pts.map(p => p.x))).toBeCloseTo(10, 6); expect(Math.min(...pts.map(p => p.y))).toBeCloseTo(-2, 6);
  });

  it('moveSine_act → move/sine: amplitude in metres, speed (deg per T) → period, offset → phase', () => {
    const b = beh('mover_') as Extract<BehaviorDef, { type: 'move' }>;
    expect(b).toMatchObject({ type: 'move', mode: 'sine' });
    const sine = b as { x: { amplitude: number; period: number; phase: number }; y: { amplitude: number; period: number; phase: number } };
    expect(sine.x.amplitude).toBe(2); expect(sine.y.amplitude).toBe(1);
    expect(sine.x.period).toBeCloseTo(360 / (5 * 15.2), 9);
    expect(sine.y.phase).toBeCloseTo(0.25, 9);                                 // z offset 90° → quarter cycle
    expect(ent('mover_').type).toBe('moving');
  });

  it('map3Wheel_act → rotate (continuous / sine / free by flags); toggles by flags; timed by speed', () => {
    expect(beh('spin_')).toMatchObject({ type: 'rotate', mode: 'continuous', speed: 2 * 15.2 });
    expect(beh('swing_')).toMatchObject({ type: 'rotate', mode: 'sine', base: 10, amplitude: 30 });
    expect(beh('toggleBlock_blue')).toMatchObject({ type: 'toggle', channel: 'jumps', active: [1] });
    expect(beh('toggleBlock_red')).toMatchObject({ type: 'toggle', channel: 'jumps', active: [0] });
    const t = beh('blink_') as Extract<BehaviorDef, { type: 'timed' }>;
    expect(t.type).toBe('timed'); expect(t.period).toBeCloseTo(360 / (5 * 15.2), 9); expect(t.phase).toBeCloseTo(0.5, 9);
    expect(t.inactive).toMatchObject({ visual: 'hidden' });                    // FLAG5 = turn_invisible
  });

  it('FLAG7 on a collision entity → slippery; INVISIBLE+solid = collision proxy, PASSABLE = decor; hazards, boost, background', () => {
    const ice = ent('ice_COL_'); expect((ice.collision as { surface: string }).surface).toBe('slippery');
    expect(ent('iceVisual_').collision).toBeUndefined();
    expect(ent('thorn_').type).toBe('hazard'); expect((ent('thorn_').collision as { hazard: boolean }).hazard).toBe(true);
    expect(beh('juice_')).toMatchObject({ type: 'boostZone' });
    expect(ent('cloud_')).toMatchObject({ type: 'background', visual: { parallax: { x: 0.5, y: 0.25 } } });
  });

  it('unknown actions / regions are reported, never silently dropped; models are never converted', () => {
    expect(report.issues.some(i => i.code === 'IMPORT_UNMAPPED' && i.message.includes('mystery_zone'))).toBe(true);
    expect(report.issues.some(i => i.code === 'IMPORT_UNMAPPED' && i.message.includes('mushroom_act'))).toBe(true);
    expect(report.issues.some(i => i.code === 'IMPORT_NOTE' && /not converted/.test(i.message))).toBe(true);
    expect(JSON.stringify(doc)).not.toMatch(/\.tga|\.fx"|\$\$w|palette\.pcx/);          // no texture / shader / archive references are carried over
    expect(report.stats.mapped.moveSine_act).toBe(1);
    expect(report.stats.blocks).toBe(1);
  });

  it('the result is a structurally valid V2 document (round-trips) with validator findings instead of crashes', () => {
    expect(parseMap(serializeMap(doc)).doc).toEqual(doc);
    const r = validateMap(doc);
    const errs = r.issues.filter(i => i.severity === 'ERROR').map(i => i.code);
    expect(errs).not.toContain('STRUCT_NUMBER');
    expect(doc.entities.length).toBeGreaterThan(10);
  });

  it('a file that is not a legacy export yields a warning and an empty map, not an exception', () => {
    const r = importLegacyWmp('hello world');
    expect(r.report.issues.some(i => i.code === 'IMPORT_FORMAT')).toBe(true);
    expect(r.doc.entities).toEqual([]);
  });
});
