/**
 * Worlds (Phase 8) — data-driven themes. Colours for World 1 are taken from the reference image (measured k-means,
 * see VISUAL_DIRECTION.md §2); Worlds 2–4 follow the labelled thumbnails in the reference (Snow Peaks, Ancient Ruins,
 * Volcanic Depths) with our own palettes.
 *
 * Each world: Visual Theme · Color Palette (Primary/Secondary/Accent/Background) · Environment · Obstacles ·
 * Materials · Background · Ambient Audio · Difficulty Profile.
 */
export interface WorldTheme {
  id: string;
  worldId: string;
  name: string;
  subtitle: string;
  palette: { primary: string; secondary: string; accent: string; background: string };
  sky: { top: string; mid: string; horizon: string; sun: string; sunDir: [number, number, number]; sunIntensity: number; hemiSky: string; hemiGround: string; hemiIntensity: number; exposure: number };
  fog: { color: string; density: number };
  terrain: {
    rockLight: string; rockMid: string; rockDark: string; capA: string; capB: string; capDark: string; soil: string;
    wood: string; woodDark: string; stone: string; ice: string; bounceTop: string; bounceCoil: string; hazardRock: string; hazardCrystal: string; special: string;
  };
  capStyle: 'grass' | 'snow' | 'sand' | 'ash';
  foliage: string[];
  pine: string[];
  trunk: string;
  water: string;
  foam: string;
  cloud: { light: string; shade: string; amount: number; seaY: number };
  mountains: { z: number; height: number; width: number; color: string; snow: boolean; y: number }[];
  scatter: { pines: number; autumn: number; bushes: number; flowers: number; mushrooms: number; ruins: number; crystals: number };
  ambience: { id: 'meadow' | 'snow' | 'ruins' | 'lava'; wind: number; birds: number; water: number };
  musicScale: 'pentatonicMajor' | 'dorian' | 'phrygian' | 'lydian';
  difficulty: { baseGap: number; hazardDensity: number; movingShare: number; label: string };
}

export const WORLD_1: WorldTheme = {
  id: 'autumn_hills', worldId: 'world_1', name: 'Green Hills', subtitle: 'Autumn cliffs & waterfalls',
  palette: { primary: '#d95125', secondary: '#a9b43c', accent: '#f4b63a', background: '#8fa4e6' },
  sky: { top: '#4f86e6', mid: '#9db4e8', horizon: '#ecdcec', sun: '#ffe3b8', sunDir: [-0.55, 0.62, 0.55], sunIntensity: 1.55, hemiSky: '#cfe0ff', hemiGround: '#8a6a58', hemiIntensity: 0.8, exposure: 0.95 },
  fog: { color: '#b9bde2', density: 0.0042 },
  terrain: {
    rockLight: '#d7a679', rockMid: '#a77757', rockDark: '#5c4040', capA: '#b4b93f', capB: '#79a736', capDark: '#4d7a33', soil: '#6b4a38',
    wood: '#c0874a', woodDark: '#6e4727', stone: '#b9aaa3', ice: '#a6dcf5', bounceTop: '#f5a22e', bounceCoil: '#cfd3de', hazardRock: '#c9708f', hazardCrystal: '#e23a3c', special: '#9a4fd0',
  },
  capStyle: 'grass',
  foliage: ['#d9401f', '#f06a25', '#f6a42c', '#c82b1d', '#8f2616', '#e8bb3d'],
  pine: ['#2e6b4b', '#3a7d55', '#245a42'],
  trunk: '#5e3b2b',
  water: '#8fdcf0', foam: '#ffffff',
  cloud: { light: '#fff6f0', shade: '#c4b4d8', amount: 1, seaY: -16 },
  mountains: [
    { z: -140, height: 62, width: 90, color: '#7f92cf', snow: true, y: -26 },
    { z: -205, height: 82, width: 120, color: '#97a6d9', snow: true, y: -38 },
    { z: -290, height: 105, width: 150, color: '#b0b9e4', snow: true, y: -52 },
  ],
  scatter: { pines: 1, autumn: 1, bushes: 1, flowers: 1, mushrooms: 0.6, ruins: 0, crystals: 0 },
  ambience: { id: 'meadow', wind: 0.5, birds: 0.8, water: 0.7 },
  musicScale: 'pentatonicMajor',
  difficulty: { baseGap: 5, hazardDensity: 0.1, movingShare: 0.1, label: 'Gentle' },
};

export const WORLD_2: WorldTheme = {
  id: 'snow_peaks', worldId: 'world_2', name: 'Snow Peaks', subtitle: 'Frozen pines & icy ledges',
  palette: { primary: '#7cc4f0', secondary: '#eef5ff', accent: '#ff8a5c', background: '#a9c4ee' },
  sky: { top: '#5f8fe0', mid: '#a9c8f0', horizon: '#eaf2ff', sun: '#fff4e0', sunDir: [-0.4, 0.55, 0.7], sunIntensity: 2.1, hemiSky: '#d8e8ff', hemiGround: '#8aa0c0', hemiIntensity: 1.35, exposure: 1.02 },
  fog: { color: '#c9dcf4', density: 0.0048 },
  terrain: {
    rockLight: '#aebfd4', rockMid: '#7f93ad', rockDark: '#47536c', capA: '#fbfdff', capB: '#dbe9f8', capDark: '#a9c2de', soil: '#6a7a92',
    wood: '#b98a5a', woodDark: '#6a4a32', stone: '#b9c3d0', ice: '#8fd2f6', bounceTop: '#ff8a5c', bounceCoil: '#d7dde8', hazardRock: '#8d7fc4', hazardCrystal: '#e0405a', special: '#5fd0c8',
  },
  capStyle: 'snow',
  foliage: ['#f4f8ff'],
  pine: ['#26584a', '#2f6a58', '#1f4a3f'],
  trunk: '#5a4636',
  water: '#a9e4f8', foam: '#ffffff',
  cloud: { light: '#ffffff', shade: '#c6d2ea', amount: 1.1, seaY: -10 },
  mountains: [
    { z: -140, height: 90, width: 90, color: '#9fb6dc', snow: true, y: -20 },
    { z: -200, height: 120, width: 120, color: '#b2c6e6', snow: true, y: -30 },
    { z: -280, height: 150, width: 150, color: '#c8d6ee', snow: true, y: -40 },
  ],
  scatter: { pines: 1.6, autumn: 0, bushes: 0.3, flowers: 0, mushrooms: 0, ruins: 0, crystals: 0.5 },
  ambience: { id: 'snow', wind: 1, birds: 0.1, water: 0.2 },
  musicScale: 'dorian',
  difficulty: { baseGap: 6, hazardDensity: 0.15, movingShare: 0.15, label: 'Cool' },
};

export const WORLD_3: WorldTheme = {
  id: 'ancient_ruins', worldId: 'world_3', name: 'Ancient Ruins', subtitle: 'Sunset arches & sandstone',
  palette: { primary: '#e8964a', secondary: '#c9733a', accent: '#6fd0c4', background: '#f0b070' },
  sky: { top: '#5a6cb8', mid: '#e89a68', horizon: '#ffd8a0', sun: '#ffc88a', sunDir: [0.7, 0.3, 0.6], sunIntensity: 2.6, hemiSky: '#ffd8b0', hemiGround: '#8a5a48', hemiIntensity: 1.1, exposure: 1.0 },
  fog: { color: '#e8b890', density: 0.0046 },
  terrain: {
    rockLight: '#e8b27a', rockMid: '#c58852', rockDark: '#7a4a3a', capA: '#f0cf8c', capB: '#dba85e', capDark: '#a87444', soil: '#8a5a3c',
    wood: '#b9824a', woodDark: '#6a4428', stone: '#d9bd96', ice: '#a6dcf5', bounceTop: '#6fd0c4', bounceCoil: '#cfd3de', hazardRock: '#a8503c', hazardCrystal: '#ffb12e', special: '#6fd0c4',
  },
  capStyle: 'sand',
  foliage: ['#6c9a3a', '#4f7f34'],
  pine: ['#4f7f34'],
  trunk: '#6a4a32',
  water: '#7fd0e0', foam: '#ffffff',
  cloud: { light: '#ffe8d0', shade: '#c88a90', amount: 0.6, seaY: -10 },
  mountains: [
    { z: -150, height: 60, width: 100, color: '#c9835e', snow: false, y: -20 },
    { z: -220, height: 80, width: 130, color: '#d9a07a', snow: false, y: -30 },
    { z: -300, height: 100, width: 160, color: '#e8bd98', snow: false, y: -40 },
  ],
  scatter: { pines: 0, autumn: 0.2, bushes: 0.6, flowers: 0.2, mushrooms: 0, ruins: 1.4, crystals: 0 },
  ambience: { id: 'ruins', wind: 0.8, birds: 0.2, water: 0 },
  musicScale: 'phrygian',
  difficulty: { baseGap: 6.5, hazardDensity: 0.2, movingShare: 0.2, label: 'Warm' },
};

export const WORLD_4: WorldTheme = {
  id: 'volcanic_depths', worldId: 'world_4', name: 'Volcanic Depths', subtitle: 'Lava rivers & embers',
  palette: { primary: '#ff5a1f', secondary: '#3a2c36', accent: '#ffcf3a', background: '#4a1f2c' },
  sky: { top: '#1c1020', mid: '#4a1f2c', horizon: '#c8472a', sun: '#ff8a4a', sunDir: [0.2, 0.5, 0.8], sunIntensity: 1.7, hemiSky: '#a05a58', hemiGround: '#3a1a1a', hemiIntensity: 1.0, exposure: 1.05 },
  fog: { color: '#6a2a2e', density: 0.0058 },
  terrain: {
    rockLight: '#7a5a64', rockMid: '#4f3a46', rockDark: '#241820', capA: '#5a4a52', capB: '#3f3138', capDark: '#241820', soil: '#2a1c24',
    wood: '#8a5a38', woodDark: '#4a2e1c', stone: '#6a5560', ice: '#8fd2f6', bounceTop: '#ffcf3a', bounceCoil: '#b5b8c4', hazardRock: '#7a2a2a', hazardCrystal: '#ff5a1f', special: '#ff9a3a',
  },
  capStyle: 'ash',
  foliage: ['#4a3a30'],
  pine: ['#3a2a28'],
  trunk: '#2a1c18',
  water: '#ff6a1f', foam: '#ffd36a',
  cloud: { light: '#8a5a58', shade: '#3a1a22', amount: 0.9, seaY: -10 },
  mountains: [
    { z: -140, height: 80, width: 80, color: '#4a2430', snow: false, y: -20 },
    { z: -210, height: 110, width: 110, color: '#5e2c34', snow: false, y: -30 },
    { z: -290, height: 140, width: 140, color: '#7a3a3a', snow: false, y: -40 },
  ],
  scatter: { pines: 0, autumn: 0, bushes: 0, flowers: 0, mushrooms: 0, ruins: 0.3, crystals: 1.4 },
  ambience: { id: 'lava', wind: 0.6, birds: 0, water: 0 },
  musicScale: 'lydian',
  difficulty: { baseGap: 7, hazardDensity: 0.3, movingShare: 0.25, label: 'Fierce' },
};

export const WORLDS: WorldTheme[] = [WORLD_1, WORLD_2, WORLD_3, WORLD_4];
export const getTheme = (id: string): WorldTheme => WORLDS.find(w => w.id === id || w.worldId === id) ?? WORLD_1;
