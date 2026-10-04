/**
 * Built-in Map System V2 maps (dev entry `?map=<id>`). `first_steps_v2` is LEVEL_01 migrated to the V2 format — the
 * migration test proves the recorded route plays bit-identically on it. No legacy map is shipped.
 */
import { LEVEL_01 } from '../data/levels/level01';
import type { MapDocument } from './schema';
import { levelDataToMap } from './MapCompile';

export const BUILTIN_MAPS: Record<string, MapDocument> = {
  first_steps_v2: levelDataToMap(LEVEL_01),
};
