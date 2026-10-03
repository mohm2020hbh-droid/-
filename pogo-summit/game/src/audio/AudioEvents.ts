/**
 * AudioEvents — the ONLY place where sound ids are named (Phase 11: "no random audio references inside scripts").
 * Categories: Jump · Landing · Hard Impact · Bounce · Boost · Fall · Goal · UI · Environment · Character · Music · Ambient.
 */
export const SFX = {
  // UI
  uiClick: 'ui_click', uiBack: 'ui_back', uiConfirm: 'ui_confirm', uiUnlock: 'ui_unlock', uiStar: 'ui_star',
  // Jump
  chargeStart: 'charge_start', launch: 'launch',
  // Landing (per material) + hard impact
  landSoft: 'land_soft', landIce: 'land_ice', landGoo: 'land_goo', landWood: 'land_wood', hardImpact: 'hard_impact',
  // Bounce / Boost
  bounce: 'bounce', boost: 'boost', boostArmed: 'boost_armed', boostPad: 'boost_pad',
  // Fall / hazard / environment
  fall: 'fall', respawn: 'respawn', hazard: 'hazard', wallHit: 'wall_hit', slide: 'slide',
  // Goal
  goal: 'goal',
  // Character
  voiceHup: 'voice_hup', voiceOuch: 'voice_ouch', voiceYay: 'voice_yay',
} as const;
export type SfxId = (typeof SFX)[keyof typeof SFX];

/** Which landing sound belongs to which collider material. */
export const LANDING_BY_MATERIAL: Record<string, SfxId> = {
  grass: SFX.landSoft, stone: SFX.landSoft, sand: SFX.landSoft, wood: SFX.landWood, ice: SFX.landIce, goo: SFX.landGoo, metal: SFX.landWood, crystal: SFX.landSoft,
};
