/**
 * MapTheme — themes are independent of the map layout (SPEC §13).
 *
 * A theme carries palette, sky, fog, lighting, material slots ("@ground"), vegetation, VFX/audio ambience, background
 * props, weather and an optional day/night cycle. Swapping `doc.theme` changes the whole look without touching an entity.
 */
import { WORLDS, type WorldTheme, getTheme } from '../data/worlds';
import type { Json, MapDocument, MaterialDef, ThemeColors, ThemeDef, ThemeRef } from './schema';
import { lerpColor } from './MapColor';
import { SLOT_NAMES, buildKit, isSlotName, slotMaterialId, type KitSpec } from './themeKit';
import { THEMES_V2 } from './themesV2';
import type { BackdropLayer, LightingProfile } from './schema';

const WEATHER: Record<string, NonNullable<ThemeDef['weather']>> = {
  autumn_hills: { type: 'leaves', intensity: 0.35, windX: 0.4 },
  snow_peaks: { type: 'snow', intensity: 0.6, windX: 0.8 },
  ancient_ruins: { type: 'none', intensity: 0 },
  volcanic_depths: { type: 'ash', intensity: 0.5, windX: 0.3 },
};

function fromWorld(w: WorldTheme): ThemeDef {
  return {
    id: w.id, name: w.name, base: w.id as ThemeDef['base'],
    palette: { ...w.palette },
    sky: { top: w.sky.top, mid: w.sky.mid, horizon: w.sky.horizon, sun: w.sky.sun, sunDir: [...w.sky.sunDir] as [number, number, number], sunIntensity: w.sky.sunIntensity },
    fog: { ...w.fog },
    lighting: { hemiSky: w.sky.hemiSky, hemiGround: w.sky.hemiGround, hemiIntensity: w.sky.hemiIntensity, exposure: w.sky.exposure },
    slots: { ground: `mat.${w.id}.ground`, secondary: `mat.${w.id}.secondary`, water: `mat.${w.id}.water`, lava: w.id === 'volcanic_depths' ? `mat.${w.id}.lava` : undefined },
    vegetation: { density: w.scatter.pines + w.scatter.autumn + w.scatter.bushes + w.scatter.flowers, kinds: Object.entries(w.scatter).filter(([, v]) => v > 0).map(([k]) => k) },
    vfx: { ambient: [w.capStyle === 'snow' ? 'snowfall' : w.capStyle === 'ash' ? 'embers' : 'leaves'], intensity: 0.6 },
    audio: { ambient: w.ambience.id, wind: w.ambience.wind, birds: w.ambience.birds, water: w.ambience.water },
    backgroundProps: ['mountain', 'cloud', ...(w.scatter.ruins > 0 ? ['ruin_arch'] : [])],
    weather: WEATHER[w.id],
  };
}

export const BUILTIN_THEMES: Record<string, ThemeDef> = {
  ...Object.fromEntries(WORLDS.map(w => [w.id, fromWorld(w)])),
  ...Object.fromEntries(THEMES_V2.map(t => [t.id, t])),
};
/** The four Visual-V2 worlds (ids `world_meadow`, `world_ice`, `world_volcanic`, `world_mystic`). */
export const V2_THEME_IDS: string[] = THEMES_V2.map(t => t.id);

const SAND = 'moss' as const;
/** Kit colours for a theme that does not ship its own `materials` (the four pre-V2 worlds, custom inline themes). */
function kitSpecOf(def: ThemeDef): KitSpec {
  const w = getTheme(def.base ?? def.id);
  const t = w.terrain;
  return {
    ground: t.capB, groundTex: w.capStyle === 'grass' ? 'grass' : w.capStyle === 'snow' ? 'snow' : w.capStyle === 'ash' ? 'ash' : SAND,
    rock: t.rockMid, wood: t.wood, water: w.water, foliage: w.foliage[0] ?? '#6bb43a', trunk: w.trunk, crystal: t.hazardCrystal, stone: t.stone, ice: t.ice,
    cloud: w.cloud.light, glow: def.palette.accent, lava: t.hazardCrystal, bounce: t.bounceTop,
  };
}

/**
 * Every slot material of a theme: kit-derived defaults (so any theme renders every role), overlaid by the theme's own
 * `materials`. Slot → id follows `def.slots[slot]` or `mat.<themeId>.<slot>`.
 */
export function themeMaterials(def: ThemeDef): Record<string, MaterialDef> {
  const kit = buildKit(def.id, kitSpecOf(def));
  const out: Record<string, MaterialDef> = {};
  for (const slot of SLOT_NAMES) {
    const id = slotMaterialId(def.id, slot);
    const want = (def.slots as Record<string, string | undefined>)[slot] ?? id;
    out[want] = { ...kit[id], id: want };
  }
  if (!def.slots.lava) delete out[slotMaterialId(def.id, 'lava')];
  for (const [id, m] of Object.entries(def.materials ?? {})) out[id] = { ...m, id };
  return out;
}

/** Material id behind a theme slot ("ground", "rock" …). Optional slots fall back to the kit id. */
export function slotId(def: ThemeDef, slot: string): string | undefined {
  if (!isSlotName(slot)) return undefined;
  if (slot === 'lava') return def.slots.lava;                      // only themes that declare lava have a lava slot
  return (def.slots as Record<string, string | undefined>)[slot] ?? slotMaterialId(def.id, slot);
}

/** The backdrop of a theme: its own layers, or layers derived from the pre-V2 world's mountains / cloud sea. */
export function backdropOf(def: ThemeDef): BackdropLayer[] {
  if (def.backdrop && def.backdrop.length) return def.backdrop;
  const w = getTheme(def.base ?? def.id);
  const out: BackdropLayer[] = w.mountains.map((m, i): BackdropLayer => ({ id: `ridge_${i}`, kind: 'mountains', z: -m.z, follow: 0.8 + i * 0.07, y: m.y, height: m.height, width: m.width, color: m.color, snow: m.snow, haze: 0.1 + 0.2 * i, seed: 3 + i * 2 }));
  out.push({ id: 'cloud_sea', kind: 'clouds', z: 70, follow: 0.5, y: w.cloud.seaY, height: 8, width: 260, color: w.cloud.light, color2: w.cloud.shade, count: Math.round(22 * w.cloud.amount), seed: 11, opacity: 0.95 });
  out.push({ id: 'haze', kind: 'fog', z: 120, follow: 0.85, y: -30, height: 55, width: 400, color: w.fog.color, opacity: 0.4 });
  return out;
}

/** The lighting profile of a theme: its own, or derived from the pre-V2 sky/lighting/fog fields. */
export function lightingOf(def: ThemeDef): LightingProfile {
  if (def.lightingProfile) return def.lightingProfile;
  return {
    sunDirection: [...def.sky.sunDir] as [number, number, number], sunIntensity: def.sky.sunIntensity, sunColor: def.sky.sun,
    ambientIntensity: def.lighting.hemiIntensity, ambientSky: def.lighting.hemiSky, ambientGround: def.lighting.hemiGround,
    fogDensity: def.fog.density, fogColor: def.fog.color, shadowQuality: 'medium', exposure: def.lighting.exposure,
  };
}

/** The names the Visual-V2 brief uses for a theme's identity, resolved from the definition (editor/validator summary). */
export function themeIdentity(def: ThemeDef) {
  return {
    sky: def.sky, fog: def.fog, lighting: lightingOf(def),
    primaryColor: def.palette.primary, secondaryColor: def.palette.secondary, accentColor: def.palette.accent,
    groundMaterial: slotId(def, 'ground'), rockMaterial: slotId(def, 'rock'),
    vegetation: def.vegetation, background: def.backdrop ?? [], particles: def.particles ?? [], ambientAudio: def.ambientAudio ?? { bed: 'meadow' as const, wind: def.audio.wind, birds: def.audio.birds, water: def.audio.water },
  };
}

export function resolveTheme(ref: ThemeRef | undefined, fallbackId: string, registry: Record<string, ThemeDef> = BUILTIN_THEMES): { theme: ThemeDef; found: boolean } {
  if (ref && 'theme' in ref && ref.theme) return { theme: ref.theme, found: true };
  const id = ref && 'ref' in ref && ref.ref ? ref.ref : fallbackId;
  const t = registry[id];
  return t ? { theme: t, found: true } : { theme: registry.autumn_hills ?? Object.values(registry)[0], found: false };
}

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
function mergeInto(base: Record<string, unknown>, over: Record<string, Json>): void {
  for (const [k, v] of Object.entries(over)) {
    if (isObj(v) && isObj(base[k])) mergeInto(base[k] as Record<string, unknown>, v as Record<string, Json>);
    else base[k] = v;
  }
}

/** Theme definition → the renderer's WorldTheme (the existing renderer accepts V2 themes unchanged). */
export function toWorldTheme(def: ThemeDef): WorldTheme {
  const base = getTheme(def.base ?? def.id);
  const w: WorldTheme = JSON.parse(JSON.stringify(base));
  w.id = def.id; w.name = def.name;
  w.palette = { ...def.palette };
  w.sky = { ...w.sky, top: def.sky.top, mid: def.sky.mid, horizon: def.sky.horizon, sun: def.sky.sun, sunDir: [...def.sky.sunDir] as [number, number, number], sunIntensity: def.sky.sunIntensity, hemiSky: def.lighting.hemiSky, hemiGround: def.lighting.hemiGround, hemiIntensity: def.lighting.hemiIntensity, exposure: def.lighting.exposure };
  w.fog = { ...def.fog };
  w.ambience = { ...w.ambience, wind: def.audio.wind, birds: def.audio.birds, water: def.audio.water };
  if (def.overrides) mergeInto(w as unknown as Record<string, unknown>, def.overrides);
  return w;
}

// ── colours (helpers live in MapColor; re-exported for existing importers) ──────────────────────────────────────
export { parseHex, isHexColor, toHex, lerpColor } from './MapColor';
const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;

/** Interpolated theme at `tSec` (day/night cycle, closed loop). Themes without `dayNight` are returned unchanged. */
export function sampleTheme(def: ThemeDef, tSec: number): ThemeDef {
  const dn = def.dayNight;
  if (!dn || dn.keyframes.length === 0 || dn.cycleSec <= 0) return def;
  const kf = [...dn.keyframes].sort((a, b) => a.t - b.t);
  const u = ((tSec / dn.cycleSec) % 1 + 1) % 1;
  let i = kf.length - 1;
  for (let k = 0; k < kf.length; k++) if (kf[k].t <= u) i = k;
  const a = kf[i], b = kf[(i + 1) % kf.length];
  const span = ((b.t - a.t) + 1) % 1 || 1;
  const f = Math.max(0, Math.min(1, (((u - a.t) + 1) % 1) / span));
  const sky: ThemeColors = { ...def.sky };
  for (const k of ['top', 'mid', 'horizon', 'sun'] as const) {
    const ca = a.sky?.[k] ?? def.sky[k], cb = b.sky?.[k] ?? def.sky[k];
    sky[k] = lerpColor(ca, cb, f);
  }
  const fogA = { color: a.fog?.color ?? def.fog.color, density: a.fog?.density ?? def.fog.density };
  const fogB = { color: b.fog?.color ?? def.fog.color, density: b.fog?.density ?? def.fog.density };
  return {
    ...def,
    sky: { ...def.sky, ...sky, sunIntensity: lerp(a.sunIntensity ?? def.sky.sunIntensity, b.sunIntensity ?? def.sky.sunIntensity, f) },
    fog: { color: lerpColor(fogA.color, fogB.color, f), density: lerp(fogA.density, fogB.density, f) },
    lighting: { ...def.lighting, hemiIntensity: lerp(a.hemiIntensity ?? def.lighting.hemiIntensity, b.hemiIntensity ?? def.lighting.hemiIntensity, f) },
  };
}

/** Material id for a visual material reference: "@slot" → the theme's slot, anything else is returned as is. */
export function resolveMaterialRef(ref: string | undefined, theme: ThemeDef): string | undefined {
  if (!ref || !ref.startsWith('@')) return ref;
  return slotId(theme, ref.slice(1));
}

/** Every material id usable in `doc` (map-local + theme slots). */
export function availableMaterials(doc: Pick<MapDocument, 'materials'>, theme: ThemeDef): Set<string> {
  return new Set([...Object.keys(doc.materials), ...Object.keys(themeMaterials(theme))]);
}
