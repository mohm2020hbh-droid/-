/**
 * themeKit — builds the full set of slot materials of a theme from ~14 colours (Visual V2 · phase 4).
 * Every slot gets a PBR-like MaterialDef with procedural textures, so a theme is "a palette + a texture choice" and a
 * theme swap changes every surface without touching an entity.
 */
import type { MaterialDef } from './schema';

export const SLOT_NAMES = ['ground', 'rock', 'secondary', 'water', 'lava', 'foliage', 'trunk', 'crystal', 'stone', 'ice', 'cloud', 'glow', 'bounce'] as const;
export type SlotName = (typeof SLOT_NAMES)[number];
export const isSlotName = (s: string): s is SlotName => (SLOT_NAMES as readonly string[]).includes(s);

export interface KitSpec {
  ground: string; groundTex: 'grass' | 'snow' | 'ash' | 'moss';
  rock: string; rockTex?: 'rock' | 'brick';
  wood: string; water: string; foliage: string; trunk: string; crystal: string; stone: string; ice: string; cloud: string; glow: string; lava: string; bounce: string;
  /** emissive strength of crystals (0 = matte, 0.6 = magical) */
  crystalGlow?: number;
  groundRoughness?: number;
}

export const slotMaterialId = (themeId: string, slot: string): string => `mat.${themeId}.${slot}`;

export function buildKit(themeId: string, k: KitSpec): Record<string, MaterialDef> {
  const id = (slot: string): string => slotMaterialId(themeId, slot);
  const gTex = k.groundTex === 'moss' ? 'grass' : k.groundTex;
  const defs: MaterialDef[] = [
    { id: id('ground'), shader: 'palette', baseColor: k.ground, baseColorMap: `proc:${k.groundTex}`, normalMap: `proc:${gTex}_n`, normalScale: 0.7, roughness: k.groundRoughness ?? 0.92, uvScale: 0.3 },
    { id: id('rock'), baseColor: k.rock, baseColorMap: `proc:${k.rockTex ?? 'rock'}`, normalMap: `proc:${k.rockTex === 'brick' ? 'brick' : 'rock'}_n`, normalScale: 1.1, roughness: 0.88, uvScale: 0.2 },
    { id: id('secondary'), baseColor: k.wood, baseColorMap: 'proc:wood', normalMap: 'proc:wood_n', normalScale: 0.8, roughness: 0.78, uvScale: 0.3 },
    { id: id('water'), shader: 'water', surfaceType: 'WATER', baseColor: k.water, normalMap: 'proc:water_n', normalScale: 0.6, opacity: 0.72, roughness: 0.15, uvScale: 0.12, flow: { x: 0.03, y: 0.055 }, doubleSided: true },
    { id: id('lava'), shader: 'emissive', surfaceType: 'LAVA', baseColor: k.lava, baseColorMap: 'proc:lava', normalMap: 'proc:lava_n', normalScale: 0.5, emissive: k.lava, emissiveIntensity: 0.95, roughness: 0.7, uvScale: 0.14, flow: { x: 0.02, y: 0.035 } },
    { id: id('foliage'), shader: 'foliage', baseColor: k.foliage, baseColorMap: 'proc:foliage', normalMap: 'proc:foliage_n', normalScale: 0.6, roughness: 0.8, uvScale: 0.9, doubleSided: true },
    { id: id('trunk'), baseColor: k.trunk, baseColorMap: 'proc:wood', normalMap: 'proc:wood_n', normalScale: 0.9, roughness: 0.85, uvScale: 0.4 },
    { id: id('crystal'), baseColor: k.crystal, normalMap: 'proc:crystal_n', normalScale: 0.7, emissive: k.crystal, emissiveIntensity: k.crystalGlow ?? 0.3, roughness: 0.22, metalness: 0.05, uvScale: 0.6 },
    { id: id('stone'), baseColor: k.stone, baseColorMap: 'proc:brick', normalMap: 'proc:brick_n', normalScale: 0.9, roughness: 0.85, uvScale: 0.3 },
    { id: id('ice'), shader: 'ice', surfaceType: 'ICE', baseColor: k.ice, normalMap: 'proc:ice_n', normalScale: 0.6, roughness: 0.12, metalness: 0.04, uvScale: 0.3 },
    { id: id('cloud'), shader: 'unlit', baseColor: k.cloud, baseColorMap: 'proc:cloud', emissive: k.cloud, emissiveIntensity: 0.5, roughness: 1, uvScale: 0.08 },
    { id: id('glow'), shader: 'emissive', baseColor: k.glow, emissive: k.glow, emissiveIntensity: 1.2, roughness: 0.6 },
    { id: id('bounce'), surfaceType: 'BOUNCE', baseColor: k.bounce, roughness: 0.45, metalness: 0.05, uvScale: 0.5 },
  ];
  return Object.fromEntries(defs.map(d => [d.id, d]));
}
