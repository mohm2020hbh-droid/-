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

const X = <T,>(e: string): T => e as unknown as T;
/** Visual-V2 platform family: the visual is a generated mesh (rounded, textured, AO-baked); the collision stays a plain trapezoid. */
const ledge = (id: string, label: string, p: { style: string; thickness: number; taper: number; surface: string; material: string; depth: number; cast?: boolean }): PrefabDef => ({
  id, label, category: 'platform',
  params: {
    width: { type: 'number', default: 6, min: 0.5, max: 80, unit: 'm', label: 'Top width' },
    thickness: { type: 'number', default: p.thickness, min: 0.2, max: 60, unit: 'm', label: 'Thickness' },
    taper: { type: 'number', default: p.taper, min: 0.2, max: 1, label: 'Bottom/top width' },
    depth: { type: 'number', default: p.depth, min: 0.5, max: 40, unit: 'm', label: 'Depth (z)' },
    seed: { type: 'number', default: 1, min: 0, max: 99999 },
  },
  entity: {
    type: 'platform',
    visual: { kind: 'mesh', mesh: 'builtin:platform', renderLayer: 'gameplay', castShadow: p.cast ?? true, receiveShadow: true, meshParams: { style: p.style, depth: X('=depth'), seed: X('=seed') } as unknown as Record<string, never> },
    collision: { shape: { kind: 'box', w: X('=width'), h: X('=thickness'), taper: X('=taper'), anchor: 'topCenter' }, surface: p.surface as 'normal', material: p.material },
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
    params: {
      count: { type: 'number', default: 5, min: 1, max: 40 }, spread: { type: 'number', default: 6, min: 1, max: 60, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 },
      kind: { type: 'enum', default: 'pine', values: ['pine', 'broadleaf', 'dead', 'crystal'] }, height: { type: 'number', default: 8, min: 2, max: 40, unit: 'm' },
    },
    entity: {
      type: 'decor',
      visual: { kind: 'procedural', style: 'tree_cluster', instancing: true, layer: 'mid', castShadow: true, meshParams: { kind: '=kind', height: '=height' } as unknown as Record<string, never>, scatter: { count: '=count' as unknown as number, width: '=spread' as unknown as number, seed: '=seed' as unknown as number, scale: [0.75, 1.3], spacing: 1.2, zJitter: 2, variants: 5 } },
      tags: ['trees'],
    },
  },
  {
    id: 'rock_cluster', label: 'Rock cluster', category: 'decor',
    params: { count: { type: 'number', default: 4, min: 1, max: 40 }, spread: { type: 'number', default: 4, min: 1, max: 40, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: {
      type: 'decor',
      visual: { kind: 'procedural', style: 'rock_cluster', instancing: true, layer: 'mid', scatter: { count: '=count' as unknown as number, width: '=spread' as unknown as number, seed: '=seed' as unknown as number, scale: [0.5, 1.5], spacing: 0.6, variants: 5 } },
      tags: ['rocks'],
    },
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
  // ── Visual-V2 platform family (mesh visuals) ─────────────────────────────────────────────────────────────────
  ledge('rock_ledge', 'Rock ledge (V2)', { style: 'rock', thickness: 3.5, taper: 0.72, surface: 'normal', material: 'stone', depth: 5 }),
  ledge('wood_ledge', 'Wooden plank (V2)', { style: 'wood', thickness: 1.2, taper: 0.92, surface: 'normal', material: 'wood', depth: 3 }),
  ledge('ice_ledge', 'Ice ledge (V2)', { style: 'ice', thickness: 2.4, taper: 0.8, surface: 'slippery', material: 'ice', depth: 4.2 }),
  ledge('ruin_ledge', 'Ruined stone ledge (V2)', { style: 'ruin', thickness: 2.2, taper: 0.9, surface: 'normal', material: 'stone', depth: 4 }),
  ledge('crystal_ledge', 'Crystal ledge (V2)', { style: 'crystal', thickness: 1.6, taper: 0.8, surface: 'normal', material: 'crystal', depth: 3, cast: false }),
  {
    id: 'moving_ledge', label: 'Moving plank (V2)', category: 'platform', extends: 'wood_ledge',
    params: { ampX: { type: 'number', default: 4, min: 0, max: 60, unit: 'm' }, ampY: { type: 'number', default: 0, min: 0, max: 60, unit: 'm' }, period: { type: 'number', default: 6, min: 0.5, max: 120, unit: 's' }, phase: { type: 'number', default: 0, min: 0, max: 1 } },
    entity: {
      type: 'moving', collision: { safe: false } as unknown as undefined,
      behavior: { type: 'move', mode: 'sine', x: { amplitude: X('=ampX'), period: X('=period'), phase: X('=phase') }, y: { amplitude: X('=ampY'), period: X('=period'), phase: X('=phase') } },
      tags: ['moving'],
    },
  },
  {
    id: 'timed_ledge', label: 'Timed ledge (V2)', category: 'interactive', extends: 'ruin_ledge',
    params: { period: { type: 'number', default: 4, min: 0.5, max: 60, unit: 's' }, phase: { type: 'number', default: 0, min: 0, max: 1 }, duty: { type: 'number', default: 0.6, min: 0.1, max: 0.95 } },
    entity: { type: 'interactive', collision: { safe: false } as unknown as undefined, behavior: { type: 'timed', period: X('=period'), phase: X('=phase'), duty: X('=duty'), inactive: { collision: false, visual: 'ghost' }, warn: 0.6 }, tags: ['timed'] },
  },
  {
    id: 'breakable_ledge', label: 'Crumbling plank (V2)', category: 'interactive', extends: 'wood_ledge',
    params: { delay: { type: 'number', default: 0.5, min: 0, max: 5, unit: 's' }, respawn: { type: 'number', default: 3, min: 0, max: 60, unit: 's' } },
    entity: { type: 'interactive', collision: { safe: false } as unknown as undefined, behavior: { type: 'breakable', trigger: 'land', delay: X('=delay'), respawn: X('=respawn') }, tags: ['breakable'] },
  },
  {
    id: 'hidden_ledge', label: 'Secret ledge (appears when the flag is set)', category: 'interactive', extends: 'crystal_ledge',
    params: { flag: { type: 'string', default: 'secret_open' } },
    entity: { type: 'interactive', behavior: { type: 'conditional', when: { flag: X('=flag') }, inactive: { collision: false, visual: 'hidden' } }, tags: ['secret'] },
  },
  {
    id: 'crystal_spikes', label: 'Crystal spikes (hazard, V2)', category: 'hazard',
    params: { width: { type: 'number', default: 3.2, min: 0.5, max: 40, unit: 'm' }, height: { type: 'number', default: 1.6, min: 0.3, max: 20, unit: 'm' }, count: { type: 'number', default: 5, min: 1, max: 24 }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: {
      type: 'hazard',
      visual: { kind: 'mesh', mesh: 'builtin:spikes', renderLayer: 'gameplay', castShadow: true, meshParams: { width: X('=width'), height: X('=height'), count: X('=count'), seed: X('=seed') } as unknown as Record<string, never> },
      collision: {
        shape: { kind: 'convex', points: [{ x: X('=-width/2'), y: 0 }, { x: X('=width/2'), y: 0 }, { x: X('=width/2*0.8'), y: X('=height*0.85') }, { x: X('=-width/2*0.8'), y: X('=height*0.85') }] },
        hazard: true, material: 'crystal',
      },
      tags: ['hazard'],
    },
  },
  {
    id: 'vfx_emitter', label: 'Particle emitter (VFX)', category: 'effects',
    params: { kind: { type: 'string', default: 'sparkle' }, rate: { type: 'number', default: 3, min: 0.2, max: 30, unit: '/s' }, radius: { type: 'number', default: 1.5, min: 0, max: 20, unit: 'm' }, power: { type: 'number', default: 0.35, min: 0.05, max: 1 }, color: { type: 'color', default: '#ffffff' } },
    entity: { type: 'vfx', properties: { kind: X('=kind'), rate: X('=rate'), radius: X('=radius'), power: X('=power'), color: X('=color') } as unknown as Record<string, never>, tags: ['vfx'] },
  },
  {
    id: 'audio_emitter', label: 'Positional sound (distance attenuated)', category: 'effects',
    params: { sound: { type: 'string', default: 'wind' }, radius: { type: 'number', default: 24, min: 2, max: 200, unit: 'm' }, volume: { type: 'number', default: 0.6, min: 0.05, max: 1 } },
    entity: { type: 'audio', properties: { sound: X('=sound'), radius: X('=radius'), volume: X('=volume') } as unknown as Record<string, never>, tags: ['audio'] },
  },
  // ── environment prefabs (Visual V2 · phase 5): decoration only — NO collision unless an author adds one ────────
  {
    id: 'cliff', label: 'Cliff face (decor)', category: 'environment',
    params: { width: { type: 'number', default: 8, min: 1, max: 80, unit: 'm' }, height: { type: 'number', default: 26, min: 2, max: 200, unit: 'm' }, depth: { type: 'number', default: 7, min: 1, max: 40, unit: 'm' }, terraces: { type: 'number', default: 4, min: 1, max: 12 }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:cliff', renderLayer: 'midground', receiveShadow: true, meshParams: { w: '=width', h: '=height', depth: '=depth', terraces: '=terraces', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'cliff'] },
  },
  {
    id: 'bush_cluster', label: 'Bush cluster', category: 'environment',
    params: { count: { type: 'number', default: 6, min: 1, max: 80 }, spread: { type: 'number', default: 6, min: 1, max: 80, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 }, size: { type: 'number', default: 1.2, min: 0.3, max: 6, unit: 'm' } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:bush', renderLayer: 'gameplay', meshParams: { size: '=size' } as unknown as Record<string, never>, scatter: { count: '=count' as unknown as number, width: '=spread' as unknown as number, seed: '=seed' as unknown as number, scale: [0.7, 1.4], spacing: 0.9, zJitter: 1.5, variants: 4 } }, tags: ['environment', 'bushes'] },
  },
  {
    id: 'grass_patch', label: 'Grass patch', category: 'environment',
    params: { count: { type: 'number', default: 24, min: 1, max: 400 }, spread: { type: 'number', default: 6, min: 0.5, max: 80, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 }, layer: { type: 'enum', default: 'gameplay', values: ['foreground', 'gameplay', 'midground'] }, height: { type: 'number', default: 0.9, min: 0.2, max: 3, unit: 'm' } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:grass', renderLayer: '=layer' as unknown as 'gameplay', receiveShadow: true, meshParams: { height: '=height' } as unknown as Record<string, never>, scatter: { count: '=count' as unknown as number, width: '=spread' as unknown as number, seed: '=seed' as unknown as number, scale: [0.7, 1.4], spacing: 0.25, zJitter: 1.2, tintVariance: 0.2, variants: 4 } }, tags: ['environment', 'grass'] },
  },
  {
    id: 'mountain', label: 'Mountain', category: 'environment',
    params: { width: { type: 'number', default: 90, min: 10, max: 400, unit: 'm' }, height: { type: 'number', default: 50, min: 5, max: 300, unit: 'm' }, depth: { type: 'number', default: 40, min: 5, max: 200, unit: 'm' }, profile: { type: 'enum', default: 'ridge', values: ['ridge', 'cone', 'volcano'] }, snow: { type: 'boolean', default: true }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'background', visual: { kind: 'mesh', mesh: 'builtin:mountain', renderLayer: 'midground', receiveShadow: false, meshParams: { w: '=width', h: '=height', depth: '=depth', profile: '=profile', snow: '=snow', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'mountain'] },
  },
  {
    id: 'background_mountain', label: 'Background mountain (far layer)', category: 'environment',
    params: { width: { type: 'number', default: 160, min: 20, max: 600, unit: 'm' }, height: { type: 'number', default: 90, min: 10, max: 400, unit: 'm' }, profile: { type: 'enum', default: 'ridge', values: ['ridge', 'cone', 'volcano'] }, snow: { type: 'boolean', default: true }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'background', visual: { kind: 'mesh', mesh: 'builtin:mountain', renderLayer: 'background', receiveShadow: false, meshParams: { w: '=width', h: '=height', depth: 60, profile: '=profile', snow: '=snow', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'background'] },
  },
  {
    id: 'cloud', label: 'Cloud bank', category: 'environment',
    params: { count: { type: 'number', default: 4, min: 1, max: 60 }, spread: { type: 'number', default: 60, min: 4, max: 400, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 }, width: { type: 'number', default: 16, min: 3, max: 80, unit: 'm' } },
    entity: { type: 'background', visual: { kind: 'mesh', mesh: 'builtin:cloud', renderLayer: 'background', receiveShadow: false, meshParams: { w: '=width' } as unknown as Record<string, never>, scatter: { count: '=count' as unknown as number, width: '=spread' as unknown as number, height: 8, seed: '=seed' as unknown as number, scale: [0.7, 1.5], zJitter: 20, variants: 4 } }, tags: ['environment', 'cloud'] },
  },
  {
    id: 'water', label: 'Water body', category: 'environment',
    params: { width: { type: 'number', default: 14, min: 1, max: 200, unit: 'm' }, depth: { type: 'number', default: 4, min: 0.5, max: 40, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:water', renderLayer: 'gameplay', castShadow: false, meshParams: { w: '=width', depth: '=depth', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'water'] },
  },
  {
    id: 'waterfall', label: 'Waterfall', category: 'environment',
    params: { width: { type: 'number', default: 3, min: 0.5, max: 30, unit: 'm' }, height: { type: 'number', default: 16, min: 2, max: 200, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:waterfall', renderLayer: 'midground', meshParams: { w: '=width', h: '=height', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'waterfall'] },
  },
  {
    id: 'crystal_cluster', label: 'Crystal cluster', category: 'environment',
    params: { count: { type: 'number', default: 6, min: 1, max: 24 }, height: { type: 'number', default: 3, min: 0.5, max: 30, unit: 'm' }, spread: { type: 'number', default: 1.4, min: 0.2, max: 12, unit: 'm' }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:crystal_cluster', renderLayer: 'gameplay', castShadow: true, meshParams: { count: '=count', height: '=height', spread: '=spread', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'crystals'] },
  },
  {
    id: 'ancient_structure', label: 'Ancient structure', category: 'environment',
    params: { kind: { type: 'enum', default: 'arch', values: ['arch', 'gate', 'pillar', 'wall', 'obelisk', 'tower'] }, width: { type: 'number', default: 8, min: 1, max: 60, unit: 'm' }, height: { type: 'number', default: 10, min: 1, max: 120, unit: 'm' }, glow: { type: 'boolean', default: false }, seed: { type: 'number', default: 1, min: 0, max: 99999 } },
    entity: { type: 'decor', visual: { kind: 'mesh', mesh: 'builtin:ancient_structure', renderLayer: 'midground', castShadow: true, meshParams: { kind: '=kind', w: '=width', h: '=height', glow: '=glow', seed: '=seed' } as unknown as Record<string, never> }, tags: ['environment', 'ruins'] },
  },
];
