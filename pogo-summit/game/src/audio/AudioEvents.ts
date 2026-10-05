/**
 * AudioEvents — the names of the per-call procedural sounds (`SFXManager`). Game code never plays these directly any more: it asks
 * the audio system for an *event* (`system/types.ts` → `AUDIO_EVENT`), whose catalogue decides which sound plays.
 * Categories: Landing · Hard Impact · Boost cues · Fall · Goal · UI · Environment · Character.
 */
export const SFX = {
  // UI
  uiClick: 'ui_click', uiBack: 'ui_back', uiConfirm: 'ui_confirm', uiUnlock: 'ui_unlock', uiStar: 'ui_star',
  // Landing (per material) + hard impact
  landSoft: 'land_soft', landIce: 'land_ice', landGoo: 'land_goo', landWood: 'land_wood', hardImpact: 'hard_impact',
  // Boost cues (the boosted launch itself is the power layer of POGO_LAUNCH)
  boostArmed: 'boost_armed', boostPad: 'boost_pad',
  // Fall / hazard / environment
  fall: 'fall', respawn: 'respawn', hazard: 'hazard',
  // Map System V2 (Visual V2): water, checkpoints, breakables, portals, zones
  splash: 'splash', checkpoint: 'checkpoint', /* routing key of MapAudioCore's break one-shot → POGO_BREAK */ breakPlatform: 'break_platform', teleport: 'teleport', chime: 'chime', lavaPop: 'lava_pop', boostZone: 'boost_zone',
  // Goal
  goal: 'goal',
  // Character
  voiceHup: 'voice_hup', voiceOuch: 'voice_ouch', voiceYay: 'voice_yay',
} as const;
export type SfxId = (typeof SFX)[keyof typeof SFX];

/** Which landing sound belongs to which collider material. */
export const LANDING_BY_MATERIAL: Record<string, SfxId> = {
  grass: SFX.landSoft, stone: SFX.landSoft, sand: SFX.landSoft, wood: SFX.landWood, ice: SFX.landIce, goo: SFX.landGoo, metal: SFX.landWood, crystal: SFX.landSoft, water: SFX.splash, lava: SFX.hazard,
};
