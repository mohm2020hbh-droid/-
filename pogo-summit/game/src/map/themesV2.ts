/**
 * themesV2 — the four Visual-V2 worlds (phase 4): WORLD_MEADOW · WORLD_ICE · WORLD_VOLCANIC · WORLD_MYSTIC.
 *
 * Each one is a complete visual identity as DATA: sky, fog, lighting profile, palette, slot materials (ground / rock /
 * wood / water / lava / foliage / crystal …), vegetation, a layered backdrop (mountains, cloud banks, silhouettes,
 * landmarks, haze), ambient particles and an ambience bed. Colours/layouts are original.
 */
import type { BackdropLayer, ThemeDef } from './schema';
import { buildKit, slotMaterialId } from './themeKit';

const slotsOf = (id: string): ThemeDef['slots'] => ({
  ground: slotMaterialId(id, 'ground'), rock: slotMaterialId(id, 'rock'), secondary: slotMaterialId(id, 'secondary'), water: slotMaterialId(id, 'water'),
  lava: slotMaterialId(id, 'lava'), foliage: slotMaterialId(id, 'foliage'), trunk: slotMaterialId(id, 'trunk'), crystal: slotMaterialId(id, 'crystal'),
  stone: slotMaterialId(id, 'stone'), ice: slotMaterialId(id, 'ice'), cloud: slotMaterialId(id, 'cloud'), glow: slotMaterialId(id, 'glow'), bounce: slotMaterialId(id, 'bounce'),
});

const bd = (l: BackdropLayer): BackdropLayer => l;

// ── WORLD_MEADOW — warm, lush, golden-hour hills ───────────────────────────────────────────────────────────────
export const WORLD_MEADOW: ThemeDef = {
  id: 'world_meadow', name: 'Meadow', base: 'autumn_hills',
  palette: { primary: '#e9663a', secondary: '#8fbf3f', accent: '#ffd25a', background: '#9ec3f2' },
  sky: { top: '#3f7be0', mid: '#8fb9f2', horizon: '#fde6cf', sun: '#ffe0b0', sunDir: [-0.5, 0.6, 0.62], sunIntensity: 1.9 },
  fog: { color: '#c6d8f0', density: 0.0036 },
  lighting: { hemiSky: '#d8e6ff', hemiGround: '#8c7a5a', hemiIntensity: 1.05, exposure: 1.03 },
  lightingProfile: { sunDirection: [-0.5, 0.6, 0.62], sunIntensity: 1.9, sunColor: '#ffe0b0', ambientIntensity: 1.05, ambientSky: '#d8e6ff', ambientGround: '#8c7a5a', fogDensity: 0.0036, fogColor: '#c6d8f0', shadowQuality: 'medium', exposure: 1.03 },
  slots: slotsOf('world_meadow'),
  materials: buildKit('world_meadow', {
    ground: '#8cc43f', groundTex: 'grass', rock: '#d3a373', wood: '#c58a4c', water: '#62cde6', foliage: '#6bb43a', trunk: '#6a4932', crystal: '#ff7aa8', stone: '#cfc4b6',
    ice: '#a8def5', cloud: '#fff7f1', glow: '#ffd86a', lava: '#ff6a2a', bounce: '#f5a22e', crystalGlow: 0.25,
  }),
  vegetation: { density: 1, kinds: ['pine', 'broadleaf', 'bush', 'grass', 'flower'] },
  vfx: { ambient: ['leaves', 'petals', 'motes'], intensity: 0.6 },
  audio: { ambient: 'meadow', wind: 0.5, birds: 0.8, water: 0.7 },
  ambientAudio: { bed: 'meadow', wind: 0.5, birds: 0.8, water: 0.7 },
  backgroundProps: ['mountain', 'cloud', 'pine_silhouette'],
  weather: { type: 'leaves', intensity: 0.35, windX: 0.4 },
  backdrop: [
    bd({ id: 'far_peaks', kind: 'mountains', z: 290, follow: 0.94, y: -88, height: 105, width: 150, color: '#9db4e8', snow: true, haze: 0.5, seed: 3 }),
    bd({ id: 'mid_peaks', kind: 'mountains', z: 205, follow: 0.87, y: -72, height: 82, width: 120, color: '#7b97dc', snow: true, haze: 0.32, seed: 5 }),
    bd({ id: 'near_peaks', kind: 'mountains', z: 140, follow: 0.8, y: -58, height: 62, width: 90, color: '#5f80cf', snow: true, haze: 0.18, seed: 7 }),
    bd({ id: 'haze_a', kind: 'fog', z: 120, follow: 0.85, y: -30, height: 55, width: 400, color: '#dfe6f7', opacity: 0.5 }),
    bd({ id: 'cloud_sea', kind: 'clouds', z: 70, follow: 0.5, y: -18, height: 8, width: 260, color: '#fff3ea', color2: '#cdbfe0', count: 26, seed: 11, opacity: 0.95 }),
    bd({ id: 'cumulus', kind: 'clouds', z: 160, follow: 0.7, y: 24, height: 70, width: 300, color: '#fffaf6', color2: '#d6cbe6', count: 12, seed: 13, opacity: 0.9 }),
    bd({ id: 'pines_far', kind: 'silhouettes', z: 95, follow: 0.7, y: -20, height: 26, width: 220, color: '#5e7fb0', count: 26, seed: 17, mesh: 'builtin:tree', meshParams: { kind: 'pine', height: 16 }, haze: 0.55 }),
    bd({ id: 'sun_glow', kind: 'glow', z: 330, follow: 1, y: 6, height: 60, width: 90, color: '#ffe3b8', opacity: 0.55 }),
  ],
  particles: [
    { kind: 'leaf', rate: 0.5, colors: ['#d9401f', '#f06a25', '#f6a42c', '#e8bb3d'], size: 0.28 },
    { kind: 'petal', rate: 0.25, colors: ['#ffd1e0', '#ffffff', '#ffe9a8'], size: 0.16 },
    { kind: 'mote', rate: 0.2, colors: ['#fff3b8'], size: 0.1 },
  ],
};

// ── WORLD_ICE — cold, crisp glacier with aurora ────────────────────────────────────────────────────────────────
export const WORLD_ICE: ThemeDef = {
  id: 'world_ice', name: 'Ice', base: 'snow_peaks',
  palette: { primary: '#6cc4f2', secondary: '#dcecf8', accent: '#9bf5e0', background: '#a9cdf0' },
  sky: { top: '#4f86d8', mid: '#a9cdf0', horizon: '#eef6ff', sun: '#f3f8ff', sunDir: [-0.4, 0.52, 0.75], sunIntensity: 1.6 },
  fog: { color: '#d2e6f6', density: 0.0046 },
  lighting: { hemiSky: '#dbeeff', hemiGround: '#7f9bb8', hemiIntensity: 1.1, exposure: 1.05 },
  lightingProfile: { sunDirection: [-0.4, 0.52, 0.75], sunIntensity: 1.6, sunColor: '#f3f8ff', ambientIntensity: 1.1, ambientSky: '#dbeeff', ambientGround: '#7f9bb8', fogDensity: 0.0046, fogColor: '#d2e6f6', shadowQuality: 'medium', exposure: 1.05 },
  slots: slotsOf('world_ice'),
  materials: buildKit('world_ice', {
    ground: '#f4f8ff', groundTex: 'snow', rock: '#93aecb', wood: '#b8895a', water: '#8fe0f5', foliage: '#2e6f5c', trunk: '#4b3a33', crystal: '#7fd6ff', stone: '#b7c6d8',
    ice: '#9fdcf7', cloud: '#f4f9ff', glow: '#aef0ff', lava: '#ff8a4a', bounce: '#ffb347', crystalGlow: 0.35, groundRoughness: 0.8,
  }),
  vegetation: { density: 0.6, kinds: ['pine', 'bush', 'crystal'] },
  vfx: { ambient: ['snowfall', 'mist'], intensity: 0.7 },
  audio: { ambient: 'snow', wind: 0.9, birds: 0, water: 0.2 },
  ambientAudio: { bed: 'snow', wind: 0.9, birds: 0, water: 0.2 },
  backgroundProps: ['mountain', 'cloud', 'ice_spire'],
  weather: { type: 'snow', intensity: 0.6, windX: 0.8 },
  backdrop: [
    bd({ id: 'far_massif', kind: 'mountains', z: 300, follow: 0.94, y: -92, height: 120, width: 170, color: '#b6c8ec', snow: true, haze: 0.5, seed: 21 }),
    bd({ id: 'glacier_ridge', kind: 'mountains', z: 215, follow: 0.87, y: -76, height: 96, width: 130, color: '#8fa9dc', snow: true, haze: 0.3, seed: 23 }),
    bd({ id: 'near_ridge', kind: 'mountains', z: 145, follow: 0.8, y: -60, height: 70, width: 95, color: '#6f8fcb', snow: true, haze: 0.16, seed: 25 }),
    bd({ id: 'aurora', kind: 'glow', z: 320, follow: 1, y: 18, height: 70, width: 260, color: '#5cf0c0', color2: '#7a8cff', opacity: 0.4 }),
    bd({ id: 'cloud_sea', kind: 'clouds', z: 75, follow: 0.5, y: -20, height: 8, width: 260, color: '#f4f9ff', color2: '#c4d4ec', count: 24, seed: 27, opacity: 0.95 }),
    bd({ id: 'spires', kind: 'silhouettes', z: 100, follow: 0.7, y: -22, height: 30, width: 220, color: '#7f9ccc', count: 18, seed: 29, mesh: 'builtin:crystal_cluster', meshParams: { count: 5, height: 14, spread: 2.5 }, haze: 0.5 }),
    bd({ id: 'haze_a', kind: 'fog', z: 125, follow: 0.85, y: -30, height: 55, width: 400, color: '#e6f1fb', opacity: 0.55 }),
  ],
  particles: [
    { kind: 'snow', rate: 0.9, colors: ['#ffffff', '#e8f4ff'], size: 0.13 },
    { kind: 'mist', rate: 0.15, colors: ['#e6f1fb'], size: 1.2 },
  ],
};

// ── WORLD_VOLCANIC — dark basalt, lava glow, ember sky ─────────────────────────────────────────────────────────
export const WORLD_VOLCANIC: ThemeDef = {
  id: 'world_volcanic', name: 'Volcanic', base: 'volcanic_depths',
  palette: { primary: '#ff5a1a', secondary: '#4a3436', accent: '#ffb347', background: '#5c2a2c' },
  sky: { top: '#24122c', mid: '#7a2f3a', horizon: '#ff9a55', sun: '#ffb878', sunDir: [0.45, 0.36, 0.8], sunIntensity: 1.5 },
  fog: { color: '#5c2a2c', density: 0.0052 },
  lighting: { hemiSky: '#d69a96', hemiGround: '#4a2424', hemiIntensity: 1.15, exposure: 1.2 },
  lightingProfile: { sunDirection: [0.45, 0.36, 0.8], sunIntensity: 1.7, sunColor: '#ffb878', ambientIntensity: 1.15, ambientSky: '#d69a96', ambientGround: '#4a2424', fogDensity: 0.0046, fogColor: '#5c2a2c', shadowQuality: 'medium', exposure: 1.2 },
  slots: slotsOf('world_volcanic'),
  materials: buildKit('world_volcanic', {
    ground: '#8c6c62', groundTex: 'ash', rock: '#62474d', wood: '#6e4a36', water: '#3b8aa8', foliage: '#6b5a2a', trunk: '#2a1d1d', crystal: '#ff5a3a', stone: '#5a4a52',
    ice: '#8fcfe8', cloud: '#6a4a4a', glow: '#ff9a3a', lava: '#ff5a1a', bounce: '#ffb347', crystalGlow: 0.55,
  }),
  vegetation: { density: 0.25, kinds: ['dead', 'bush', 'crystal'] },
  vfx: { ambient: ['embers', 'ash'], intensity: 0.8 },
  audio: { ambient: 'lava', wind: 0.45, birds: 0, water: 0 },
  ambientAudio: { bed: 'lava', wind: 0.45, birds: 0, water: 0, drone: 0.4 },
  backgroundProps: ['mountain', 'smoke', 'volcano'],
  weather: { type: 'ash', intensity: 0.5, windX: 0.3 },
  backdrop: [
    bd({ id: 'far_ridge', kind: 'mountains', z: 300, follow: 0.94, y: -90, height: 110, width: 170, color: '#6a3438', haze: 0.45, snow: false, seed: 31 }),
    bd({ id: 'volcano', kind: 'landmarks', z: 230, follow: 0.88, y: -80, height: 100, width: 60, color: '#3a2227', color2: '#ff6a2a', count: 1, seed: 33, mesh: 'builtin:mountain', meshParams: { profile: 'volcano', w: 90, h: 100, snow: false }, haze: 0.28 }),
    bd({ id: 'mid_ridge', kind: 'mountains', z: 170, follow: 0.84, y: -70, height: 80, width: 130, color: '#4a272c', haze: 0.3, snow: false, seed: 35 }),
    bd({ id: 'near_ridge', kind: 'mountains', z: 125, follow: 0.78, y: -56, height: 62, width: 95, color: '#331b20', haze: 0.16, snow: false, seed: 37 }),
    bd({ id: 'lava_sea', kind: 'sea', z: 60, follow: 0.5, y: -24, height: 6, width: 300, color: '#ff6a2a', color2: '#ffb347', opacity: 0.95 }),
    bd({ id: 'smoke', kind: 'clouds', z: 130, follow: 0.6, y: 20, height: 60, width: 300, color: '#6a4a4a', color2: '#2a1a1c', count: 14, seed: 39, opacity: 0.85 }),
    bd({ id: 'haze_a', kind: 'fog', z: 110, follow: 0.85, y: -30, height: 55, width: 400, color: '#ff8a4a', opacity: 0.28 }),
    bd({ id: 'glow', kind: 'glow', z: 320, follow: 1, y: -4, height: 70, width: 220, color: '#ff8a3a', opacity: 0.5 }),
  ],
  particles: [
    { kind: 'ember', rate: 0.9, colors: ['#ff8a3a', '#ff5a1a', '#ffd070'], size: 0.12 },
    { kind: 'ash', rate: 0.4, colors: ['#9a8a88', '#5a4a48'], size: 0.14 },
  ],
};

// ── WORLD_MYSTIC — twilight, glowing crystals, floating islands ────────────────────────────────────────────────
export const WORLD_MYSTIC: ThemeDef = {
  id: 'world_mystic', name: 'Mystic', base: 'ancient_ruins',
  palette: { primary: '#b66cff', secondary: '#46d6b0', accent: '#7be8ff', background: '#493d86' },
  sky: { top: '#1a1850', mid: '#5b3f9e', horizon: '#f7a8dc', sun: '#d6dcff', sunDir: [0.2, 0.8, 0.55], sunIntensity: 1.2 },
  fog: { color: '#493d86', density: 0.0046 },
  lighting: { hemiSky: '#9f9cff', hemiGround: '#3a2a66', hemiIntensity: 1.0, exposure: 1.1 },
  lightingProfile: { sunDirection: [0.2, 0.8, 0.55], sunIntensity: 1.2, sunColor: '#d6dcff', ambientIntensity: 1.0, ambientSky: '#9f9cff', ambientGround: '#3a2a66', fogDensity: 0.0046, fogColor: '#493d86', shadowQuality: 'medium', exposure: 1.1 },
  slots: slotsOf('world_mystic'),
  materials: buildKit('world_mystic', {
    ground: '#46d6b0', groundTex: 'moss', rock: '#51407f', wood: '#7a5a9a', water: '#79e4ff', foliage: '#d35ad0', trunk: '#3a2a5a', crystal: '#7be8ff', stone: '#8f94c8',
    ice: '#9ad8ff', cloud: '#e9d7ff', glow: '#9bf5ff', lava: '#ff5acb', bounce: '#ffcc55', crystalGlow: 0.65, rockTex: 'brick',
  }),
  vegetation: { density: 0.7, kinds: ['crystal', 'bush', 'grass'] },
  vfx: { ambient: ['motes', 'sparks'], intensity: 0.7 },
  audio: { ambient: 'mystic', wind: 0.3, birds: 0, water: 0.3 },
  ambientAudio: { bed: 'mystic', wind: 0.3, birds: 0, water: 0.3, chimes: 0.7, drone: 0.5 },
  backgroundProps: ['island', 'obelisk', 'mist'],
  weather: { type: 'none', intensity: 0 },
  dayNight: {
    cycleSec: 120,
    keyframes: [
      { t: 0, sky: { top: '#1a1850', mid: '#5b3f9e', horizon: '#f7a8dc' }, fog: { color: '#493d86' }, sunIntensity: 1.2, hemiIntensity: 1.0 },
      { t: 0.5, sky: { top: '#0f2a5a', mid: '#2f5a9e', horizon: '#7fe0e8' }, fog: { color: '#2c4a86' }, sunIntensity: 1.0, hemiIntensity: 0.9 },
    ],
  },
  backdrop: [
    bd({ id: 'far_range', kind: 'mountains', z: 300, follow: 0.94, y: -90, height: 110, width: 170, color: '#6a52b0', haze: 0.5, snow: false, seed: 41 }),
    bd({ id: 'islands_far', kind: 'landmarks', z: 220, follow: 0.85, y: 6, height: 40, width: 260, color: '#6a5aa8', count: 6, seed: 43, mesh: 'builtin:island', meshParams: { radius: 14 }, haze: 0.4 }),
    bd({ id: 'obelisks', kind: 'landmarks', z: 150, follow: 0.8, y: -30, height: 70, width: 220, color: '#8a84c8', count: 5, seed: 45, mesh: 'builtin:ancient_structure', meshParams: { kind: 'obelisk', w: 6, h: 60, glow: true }, haze: 0.3 }),
    bd({ id: 'islands_near', kind: 'landmarks', z: 100, follow: 0.7, y: -4, height: 30, width: 220, color: '#7a68b8', count: 6, seed: 47, mesh: 'builtin:island', meshParams: { radius: 8 }, haze: 0.22 }),
    bd({ id: 'mist_a', kind: 'fog', z: 90, follow: 0.8, y: -26, height: 55, width: 400, color: '#7e6ad0', opacity: 0.4 }),
    bd({ id: 'cloud_sea', kind: 'clouds', z: 60, follow: 0.5, y: -20, height: 8, width: 260, color: '#e9d7ff', color2: '#9a82d8', count: 22, seed: 49, opacity: 0.9 }),
    bd({ id: 'moon_glow', kind: 'glow', z: 330, follow: 1, y: 30, height: 80, width: 110, color: '#d6dcff', opacity: 0.6 }),
  ],
  particles: [
    { kind: 'mote', rate: 0.7, colors: ['#9bf5ff', '#d6a8ff', '#fff0b0'], size: 0.12 },
    { kind: 'spark', rate: 0.25, colors: ['#ffffff', '#7be8ff'], size: 0.1 },
    { kind: 'mist', rate: 0.1, colors: ['#9a82d8'], size: 1.4 },
  ],
};

export const THEMES_V2: ThemeDef[] = [WORLD_MEADOW, WORLD_ICE, WORLD_VOLCANIC, WORLD_MYSTIC];
