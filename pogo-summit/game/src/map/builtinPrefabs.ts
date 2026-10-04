/**
 * Built-in prefabs (data only, SPEC §7). Maps can add or override prefabs in `doc.prefabs`; nothing here needs code to
 * extend. Strings starting with "=" are expressions over the prefab parameters.
 */
import type { PrefabDef } from './schema';

const platform = (id: string, label: string, p: { thickness: number; taper: number; surface: string; material: string; style: string; slot: string }): PrefabDef => ({
  id, label, category: 'platform',
  params: {
    width: { type: 'number', default: 6, min: 0.5, max: 80, unit: 'm', label: 'Top width' },
    thickness: { type: 'number', default: p.thickness, min: 0.2, max: 60, unit: 'm', label: 'Thickness' },
    taper: { type: 'number', default: p.taper, min: 0.2, max: 1, label: 'Bottom/top width' },
  },
  entity: {
    type: 'platform',
    visual: { kind: 'procedural', style: p.style, material: p.slot },
    collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=thickness' as unknown as number, taper: '=taper' as unknown as number, anchor: 'topCenter' }, surface: p.surface as never, material: p.material },
    tags: [id],
  },
});

export const BUILTIN_PREFABS: PrefabDef[] = [
  platform('stone_platform', 'Stone platform', { thickness: 3, taper: 0.72, surface: 'normal', material: 'grass', style: 'rock', slot: '@ground' }),
  platform('ice_platform', 'Ice platform', { thickness: 2.5, taper: 0.8, surface: 'slippery', material: 'ice', style: 'ice', slot: '@secondary' }),
  platform('wood_platform', 'Wood platform', { thickness: 1.2, taper: 0.9, surface: 'normal', material: 'wood', style: 'wood', slot: '@secondary' }),
  {
    id: 'moving_platform', label: 'Moving platform', category: 'platform',
    params: {
      width: { type: 'number', default: 6, min: 0.5, max: 40, unit: 'm' },
      thickness: { type: 'number', default: 1.2, min: 0.2, max: 20, unit: 'm' },
      taper: { type: 'number', default: 0.9, min: 0.2, max: 1 },
      ampX: { type: 'number', default: 4, min: 0, max: 60, unit: 'm', label: 'Amplitude X' },
      ampY: { type: 'number', default: 0, min: 0, max: 60, unit: 'm', label: 'Amplitude Y' },
      period: { type: 'number', default: 6, min: 0.5, max: 120, unit: 's' },
      phase: { type: 'number', default: 0, min: 0, max: 1, label: 'Phase (cycles)' },
    },
    entity: {
      type: 'moving',
      visual: { kind: 'procedural', style: 'wood', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=thickness' as unknown as number, taper: '=taper' as unknown as number, anchor: 'topCenter' }, surface: 'normal', material: 'wood', safe: false },
      behavior: {
        type: 'move', mode: 'sine',
        x: { amplitude: '=ampX' as unknown as number, period: '=period' as unknown as number, phase: '=phase' as unknown as number },
        y: { amplitude: '=ampY' as unknown as number, period: '=period' as unknown as number, phase: '=phase' as unknown as number },
      },
      tags: ['moving'],
    },
  },
  {
    id: 'bounce_platform', label: 'Bounce platform (declarative)', category: 'platform', requires: ['bouncePush'],
    params: {
      width: { type: 'number', default: 5, min: 0.5, max: 40, unit: 'm' },
      thickness: { type: 'number', default: 1.6, min: 0.2, max: 20, unit: 'm' },
      taper: { type: 'number', default: 0.8, min: 0.2, max: 1 },
    },
    entity: {
      type: 'platform',
      visual: { kind: 'procedural', style: 'bounce', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=thickness' as unknown as number, taper: '=taper' as unknown as number, anchor: 'topCenter' }, surface: 'bounce', material: 'metal' },
      behavior: { type: 'squash', amount: 0.25 },
      tags: ['bounce'],
    },
  },
  {
    id: 'spike', label: 'Spike / crystal hazard', category: 'hazard',
    params: { width: { type: 'number', default: 3.2, min: 0.5, max: 30, unit: 'm' }, height: { type: 'number', default: 1.5, min: 0.3, max: 20, unit: 'm' } },
    entity: {
      type: 'hazard',
      visual: { kind: 'procedural', style: 'crystals', material: '@secondary' },
      collision: {
        shape: { kind: 'convex', points: [
          { x: '=-width/2' as unknown as number, y: 0 }, { x: '=width/2' as unknown as number, y: 0 },
          { x: '=width/2*0.8' as unknown as number, y: '=height*0.85' as unknown as number }, { x: '=-width/2*0.8' as unknown as number, y: '=height*0.85' as unknown as number },
        ] },
        hazard: true, material: 'crystal',
      },
      tags: ['hazard'],
    },
  },
  {
    id: 'hazard', label: 'Hazard box', category: 'hazard',
    params: { width: { type: 'number', default: 2, min: 0.2, max: 60 }, height: { type: 'number', default: 2, min: 0.2, max: 60 } },
    entity: { type: 'hazard', visual: { kind: 'procedural', style: 'crystals', material: '@secondary' }, collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=height' as unknown as number }, hazard: true, material: 'crystal' }, tags: ['hazard'] },
  },
  {
    id: 'wall', label: 'Wall', category: 'wall',
    params: { width: { type: 'number', default: 2, min: 0.2, max: 200 }, height: { type: 'number', default: 20, min: 0.2, max: 400 } },
    entity: { type: 'wall', visual: { kind: 'procedural', style: 'cliff', material: '@ground' }, collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=height' as unknown as number }, surface: 'normal', material: 'stone', safe: false }, tags: ['wall'] },
  },
  {
    id: 'slope', label: 'Slope', category: 'slope',
    params: { width: { type: 'number', default: 6, min: 0.5, max: 80 }, height: { type: 'number', default: 3, min: 0.2, max: 60 }, mirror: { type: 'boolean', default: false } },
    entity: { type: 'slope', visual: { kind: 'procedural', style: 'rock', material: '@ground' }, collision: { shape: { kind: 'slope', w: '=width' as unknown as number, h: '=height' as unknown as number, mirror: '=mirror' as unknown as boolean }, surface: 'normal', material: 'stone' }, tags: ['slope'] },
  },
  {
    id: 'tree_cluster', label: 'Tree cluster', category: 'decor',
    params: { count: { type: 'number', default: 5, min: 1, max: 40 }, spread: { type: 'number', default: 6, min: 1, max: 60, unit: 'm' } },
    entity: { type: 'decor', visual: { kind: 'procedural', style: 'tree_cluster', instancing: true, layer: 'mid', castShadow: true }, tags: ['trees'] },
  },
  {
    id: 'rock_cluster', label: 'Rock cluster', category: 'decor',
    params: { count: { type: 'number', default: 4, min: 1, max: 40 }, spread: { type: 'number', default: 4, min: 1, max: 40, unit: 'm' } },
    entity: { type: 'decor', visual: { kind: 'procedural', style: 'rock_cluster', instancing: true, layer: 'mid' }, tags: ['rocks'] },
  },
  {
    id: 'boost', label: 'Boost zone (declarative)', category: 'interactive', requires: ['boostSurface'],
    params: { width: { type: 'number', default: 3, min: 0.5, max: 30 }, height: { type: 'number', default: 2, min: 0.5, max: 30 } },
    entity: {
      type: 'interactive', visual: { kind: 'procedural', style: 'boost', material: '@secondary', emissive: 0.5 },
      collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=height' as unknown as number }, trigger: true },
      behavior: { type: 'boostZone', kind: 'powerJump' }, tags: ['boost'],
    },
  },
  { id: 'checkpoint', label: 'Checkpoint marker', category: 'marker', entity: { type: 'marker', visual: { kind: 'procedural', style: 'checkpoint', emissive: 0.3 }, tags: ['checkpoint'] } },
  { id: 'start', label: 'Start marker', category: 'marker', entity: { type: 'marker', visual: { kind: 'procedural', style: 'start_line' }, tags: ['start'] } },
  { id: 'finish', label: 'Finish marker', category: 'marker', entity: { type: 'marker', visual: { kind: 'procedural', style: 'finish_line', emissive: 0.3 }, tags: ['finish'] } },
  {
    id: 'decoration', label: 'Decoration', category: 'decor',
    params: { style: { type: 'string', default: 'flowers' } },
    entity: { type: 'decor', visual: { kind: 'procedural', style: '=style', instancing: true, layer: 'mid' }, tags: ['decor'] },
  },
  {
    id: 'background_object', label: 'Background object', category: 'background',
    params: { parallaxX: { type: 'number', default: 0.5, min: 0, max: 1 }, parallaxY: { type: 'number', default: 0.5, min: 0, max: 1 } },
    entity: { type: 'background', visual: { kind: 'procedural', style: 'mountain', layer: 'far', parallax: { x: '=parallaxX' as unknown as number, y: '=parallaxY' as unknown as number }, instancing: true }, tags: ['background'] },
  },
  {
    id: 'trigger', label: 'Trigger volume', category: 'trigger',
    params: { width: { type: 'number', default: 4, min: 0.2, max: 200 }, height: { type: 'number', default: 4, min: 0.2, max: 200 } },
    entity: { type: 'trigger', collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=height' as unknown as number }, trigger: true }, tags: ['trigger'] },
  },
  {
    id: 'toggle_block', label: 'Toggle block (jump parity)', category: 'interactive',
    params: { size: { type: 'number', default: 2, min: 0.5, max: 20, unit: 'm' }, group: { type: 'number', default: 0, min: 0, max: 7 }, modulus: { type: 'number', default: 2, min: 2, max: 8 } },
    entity: {
      type: 'interactive', visual: { kind: 'procedural', style: 'toggle', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=size' as unknown as number, h: '=size' as unknown as number }, surface: 'normal', material: 'stone' },
      behavior: { type: 'toggle', channel: 'jumps', modulus: '=modulus' as unknown as number, active: ['=group' as unknown as number], inactive: { collision: false, visual: 'ghost' } },
      tags: ['toggle'],
    },
  },
  {
    id: 'timed_block', label: 'Timed block', category: 'interactive',
    params: { width: { type: 'number', default: 4, min: 0.5, max: 40 }, thickness: { type: 'number', default: 1, min: 0.2, max: 20 }, period: { type: 'number', default: 4, min: 0.5, max: 60, unit: 's' }, phase: { type: 'number', default: 0, min: 0, max: 1 }, duty: { type: 'number', default: 0.5, min: 0.05, max: 0.95 } },
    entity: {
      type: 'interactive', visual: { kind: 'procedural', style: 'timed', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=thickness' as unknown as number, anchor: 'topCenter' }, surface: 'normal', material: 'stone', safe: false },
      behavior: { type: 'timed', period: '=period' as unknown as number, phase: '=phase' as unknown as number, duty: '=duty' as unknown as number, inactive: { collision: false, visual: 'ghost' }, warn: 0.6 },
      tags: ['timed'],
    },
  },
  {
    id: 'rotating_blade', label: 'Rotating blade (hazard)', category: 'hazard',
    params: { length: { type: 'number', default: 8, min: 1, max: 60, unit: 'm' }, thickness: { type: 'number', default: 0.8, min: 0.2, max: 8 }, speed: { type: 'number', default: 60, min: -720, max: 720, unit: 'deg/s' } },
    entity: {
      type: 'hazard', visual: { kind: 'procedural', style: 'blade', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=length' as unknown as number, h: '=thickness' as unknown as number }, hazard: true, material: 'metal' },
      behavior: { type: 'rotate', mode: 'continuous', speed: '=speed' as unknown as number }, tags: ['rotating'],
    },
  },
  {
    id: 'breakable_platform', label: 'Breakable platform', category: 'interactive',
    params: { width: { type: 'number', default: 4, min: 0.5, max: 40 }, thickness: { type: 'number', default: 1, min: 0.2, max: 20 }, delay: { type: 'number', default: 0.4, min: 0, max: 5, unit: 's' }, respawn: { type: 'number', default: 3, min: 0, max: 60, unit: 's' } },
    entity: {
      type: 'interactive', visual: { kind: 'procedural', style: 'wood', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=thickness' as unknown as number, anchor: 'topCenter' }, surface: 'normal', material: 'wood', safe: false },
      behavior: { type: 'breakable', trigger: 'land', delay: '=delay' as unknown as number, respawn: '=respawn' as unknown as number }, tags: ['breakable'],
    },
  },
  {
    id: 'one_way_platform', label: 'One-way platform', category: 'platform',
    params: { width: { type: 'number', default: 5, min: 0.5, max: 60 }, thickness: { type: 'number', default: 0.6, min: 0.2, max: 10 } },
    entity: {
      type: 'platform', visual: { kind: 'procedural', style: 'wood', material: '@secondary' },
      collision: { shape: { kind: 'box', w: '=width' as unknown as number, h: '=thickness' as unknown as number, anchor: 'topCenter' }, surface: 'normal', material: 'wood', oneWay: 'up' }, tags: ['oneway'],
    },
  },
];
