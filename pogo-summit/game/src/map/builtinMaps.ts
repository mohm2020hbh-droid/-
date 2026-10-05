/**
 * Built-in Map System V2 maps (dev entry `?map=<id>`).
 *   first_steps_v2   LEVEL_01 migrated to the V2 format — the migration test proves the recorded route plays bit-identically.
 *   showcase_v2      the Visual-V2 showcase, authored as plain JSON (maps/showcase_v2.json) — no TypeScript involved.
 * No legacy map is shipped.
 */
import { LEVEL_01 } from '../data/levels/level01';
import type { MapDocument } from './schema';
import { levelDataToMap } from './MapCompile';
import { parseMap } from './MapLoader';
import showcaseJson from './maps/showcase_v2.json';

export const BUILTIN_MAPS: Record<string, MapDocument> = {
  first_steps_v2: levelDataToMap(LEVEL_01),
  showcase_v2: parseMap(showcaseJson).doc,
};
