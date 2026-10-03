/** Customisation catalogue (Phase 15) — data-driven: itemId · category · icon · prefab · unlockCondition · rarity. */
export type Category = 'hat' | 'stick' | 'outfit' | 'skin' | 'boostFx' | 'emote';
export type Rarity = 'common' | 'rare' | 'epic';
export type Unlock =
  | { type: 'default' }
  | { type: 'levelComplete'; level: string }
  | { type: 'stars'; n: number }
  | { type: 'jumps'; n: number }
  | { type: 'boosts'; n: number }
  | { type: 'runs'; n: number };

export interface ItemDef {
  itemId: string;
  category: Category;
  name: string;
  /** Swatch colour shown in the wardrobe grid (or a glyph key). */
  icon: string;
  /** Builder id inside Character / Vfx (what actually gets rendered). */
  prefab: string;
  unlockCondition: Unlock;
  rarity: Rarity;
}

const I = (itemId: string, category: Category, name: string, icon: string, prefab: string, unlockCondition: Unlock, rarity: Rarity = 'common'): ItemDef =>
  ({ itemId, category, name, icon, prefab, unlockCondition, rarity });

export const ITEMS: ItemDef[] = [
  I('hat_beanie', 'hat', 'Beanie', '#f5c542', 'beanie', { type: 'default' }),
  I('hat_propeller', 'hat', 'Propeller cap', '#e84a5f', 'propeller', { type: 'levelComplete', level: 'level_01' }, 'rare'),
  I('hat_bucket', 'hat', 'Bucket hat', '#7fb36a', 'bucket', { type: 'runs', n: 3 }),
  I('hat_party', 'hat', 'Party cone', '#b362e8', 'party', { type: 'stars', n: 3 }, 'rare'),
  I('hat_tophat', 'hat', 'Top hat', '#2a2530', 'tophat', { type: 'jumps', n: 150 }, 'epic'),
  I('stick_copper', 'stick', 'Copper', '#e8923c', 'copper', { type: 'default' }),
  I('stick_steel', 'stick', 'Steel', '#c7ccd8', 'steel', { type: 'runs', n: 5 }),
  I('stick_bamboo', 'stick', 'Bamboo', '#9bbf5a', 'bamboo', { type: 'levelComplete', level: 'level_01' }),
  I('stick_candy', 'stick', 'Candy cane', '#e23b4a', 'candy', { type: 'jumps', n: 100 }, 'rare'),
  I('stick_neon', 'stick', 'Neon', '#38e1ff', 'neon', { type: 'boosts', n: 10 }, 'epic'),
  I('outfit_teal', 'outfit', 'Teal parka', '#1fb0a8', 'teal', { type: 'default' }),
  I('outfit_forest', 'outfit', 'Forest', '#3f9a52', 'forest', { type: 'runs', n: 2 }),
  I('outfit_crimson', 'outfit', 'Crimson', '#d9433a', 'crimson', { type: 'jumps', n: 50 }),
  I('outfit_sunrise', 'outfit', 'Sunrise', '#f59a2c', 'sunrise', { type: 'stars', n: 2 }, 'rare'),
  I('outfit_violet', 'outfit', 'Violet', '#7a52d0', 'violet', { type: 'boosts', n: 5 }, 'rare'),
  I('skin_peach', 'skin', 'Peach', '#ffd2ad', 'peach', { type: 'default' }),
  I('skin_tan', 'skin', 'Tan', '#d9a070', 'tan', { type: 'default' }),
  I('skin_deep', 'skin', 'Deep', '#9a6440', 'deep', { type: 'default' }),
  I('skin_rosy', 'skin', 'Rosy', '#f6b7a0', 'rosy', { type: 'default' }),
  I('skin_olive', 'skin', 'Olive', '#c8a878', 'olive', { type: 'default' }),
  I('fx_sparks', 'boostFx', 'Sparks', '#ffb347', 'sparks', { type: 'default' }),
  I('fx_stars', 'boostFx', 'Stardust', '#ffe27a', 'stars', { type: 'boosts', n: 3 }, 'rare'),
  I('fx_leaves', 'boostFx', 'Autumn leaves', '#ff7a3a', 'leaves', { type: 'levelComplete', level: 'level_01' }),
  I('fx_ice', 'boostFx', 'Frost', '#8fe0ff', 'ice', { type: 'stars', n: 3 }, 'epic'),
  I('emote_wave', 'emote', 'Wave', '👋', 'wave', { type: 'default' }),
  I('emote_cheer', 'emote', 'Cheer', '🎉', 'cheer', { type: 'jumps', n: 30 }),
  I('emote_dance', 'emote', 'Dance', '🕺', 'dance', { type: 'runs', n: 4 }, 'rare'),
];

export const CATEGORIES: Category[] = ['hat', 'stick', 'outfit', 'skin', 'boostFx', 'emote'];
export const itemById = (id: string): ItemDef | undefined => ITEMS.find(i => i.itemId === id);
export const DEFAULT_EQUIPPED: Record<Category, string> = { hat: 'hat_beanie', stick: 'stick_copper', outfit: 'outfit_teal', skin: 'skin_peach', boostFx: 'fx_sparks', emote: 'emote_wave' };
export const BOOST_FX_TINT: Record<string, string> = { sparks: '#ffb347', stars: '#ffe27a', leaves: '#ff7a3a', ice: '#8fe0ff' };
