import * as THREE from 'three';
import { type MapMaterial, normalizeMaterial } from '../../map/MapMaterial';
import type { MaterialDef } from '../../map/schema';
import type { TextureLibrary } from './textures';

/**
 * MaterialLibrary — MapMaterial → THREE.MeshStandardMaterial (PBR-like: base colour map, normal map, roughness, metalness,
 * emissive, opacity, tiling), one shared instance per (material, variant). Lighting stays cheap: the sun + hemisphere of the
 * scene, a rim term that matches the character's, optional sky reflections on glossy materials and baked vertex AO.
 *
 * Variants: `ghost` (inactive toggle/timed blocks), `tint` (toggle colour), `foreground` (dithers away near the player so
 * foreground foliage never hides the pogo).
 */
export interface MatVariant { ghost?: boolean; tint?: string; foreground?: boolean; /** 0…1: aerial perspective, blends the base colour toward the haze (fog) colour */ haze?: number }
export interface MaterialStats { materials: number; programsKeys: number; textures: number; textureBytes: number }

const variantKey = (v: MatVariant): string => `${v.ghost ? 'g' : ''}${v.foreground ? 'f' : ''}${v.haze ? `h${Math.round(v.haze * 100)}` : ''}${v.tint ?? ''}`;

export class MaterialLibrary {
  /** Shared uniforms (updated by the scene each frame). */
  readonly rim = { value: new THREE.Color('#ffffff') };
  readonly fade = { uPlayerPx: { value: new THREE.Vector2(-1e4, -1e4) }, uFadeR: { value: 0 } };
  private readonly cache = new Map<string, THREE.MeshStandardMaterial>();
  private readonly flowing: { mat: THREE.MeshStandardMaterial; flow: { x: number; y: number }; tex: { t: THREE.Texture; k: number }[] }[] = [];
  private env: THREE.Texture | null = null;
  private fallback: THREE.MeshStandardMaterial | null = null;
  normalMaps = true;
  rimStrength = 0.3;
  private hazeColor = new THREE.Color('#b9bde2');
  /** Colour distant (midground) geometry fades toward — the theme's fog colour. */
  setHaze(c: string): void { this.hazeColor.set(c); }

  constructor(private readonly textures: TextureLibrary, private lookup: (id: string) => MaterialDef | undefined) {}

  setLookup(lookup: (id: string) => MaterialDef | undefined): void { this.lookup = lookup; }

  /** Reflection source for glossy materials (ice, water, crystal, metal). */
  setEnvironment(env: THREE.Texture | null): void {
    this.env = env;
    for (const mat of this.cache.values()) this.applyEnv(mat);
  }
  private applyEnv(mat: THREE.MeshStandardMaterial): void {
    const glossy = mat.roughness < 0.45 || mat.metalness > 0.1;
    mat.envMap = glossy ? this.env : null;
    mat.envMapIntensity = 0.75;
    mat.needsUpdate = true;
  }

  /** The normalised look of a material id (without building it). */
  describe(id: string): MapMaterial | null { const d = this.lookup(id); return d ? normalizeMaterial(d) : null; }

  get(id: string, variant: MatVariant = {}): THREE.MeshStandardMaterial {
    const key = `${id}|${variantKey(variant)}`;
    let m = this.cache.get(key);
    if (m) return m;
    const def = this.lookup(id);
    if (!def) return (this.fallback ??= new THREE.MeshStandardMaterial({ color: '#b9aaa3', roughness: 0.9, vertexColors: true }));
    m = this.build(normalizeMaterial(def), variant);
    this.cache.set(key, m);
    return m;
  }

  private tex(ref: string, srgb: boolean, m: MapMaterial, k: number): THREE.Texture {
    const t = this.textures.get(ref, srgb).clone();
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(m.uvScale * m.tiling.x, m.uvScale * m.tiling.y);
    t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
    t.needsUpdate = false;
    void k;
    return t;
  }

  private build(m: MapMaterial, v: MatVariant): THREE.MeshStandardMaterial {
    const transparent = m.opacity < 1 || !!v.ghost;
    const mat = new THREE.MeshStandardMaterial({
      color: new THREE.Color(v.tint ?? m.baseColor).lerp(this.hazeColor, v.haze ?? 0), roughness: m.roughness, metalness: m.metalness, vertexColors: m.aoStrength > 0,
      transparent, opacity: v.ghost ? Math.min(m.opacity, 0.28) : m.opacity, depthWrite: !transparent, side: m.doubleSided ? THREE.DoubleSide : THREE.FrontSide,
    });
    const tex: { t: THREE.Texture; k: number }[] = [];
    if (m.baseColorMap) { mat.map = this.tex(m.baseColorMap, true, m, 1); tex.push({ t: mat.map, k: 0.7 }); }
    if (m.normalMap && this.normalMaps) { mat.normalMap = this.tex(m.normalMap, false, m, 1); mat.normalScale.set(m.normalScale, m.normalScale); tex.push({ t: mat.normalMap, k: 1.3 }); }
    if (m.emissiveIntensity > 0) {
      mat.emissive = new THREE.Color(v.tint ?? m.emissive); mat.emissiveIntensity = m.emissiveIntensity;
      if (m.emissiveMap) { mat.emissiveMap = this.tex(m.emissiveMap, true, m, 1); tex.push({ t: mat.emissiveMap, k: 0.7 }); }
      else if (m.baseColorMap && m.surfaceType === 'LAVA') { mat.emissiveMap = this.tex(m.baseColorMap, true, m, 1); tex.push({ t: mat.emissiveMap, k: 0.7 }); }
    }
    if (m.flow.x !== 0 || m.flow.y !== 0) this.flowing.push({ mat, flow: m.flow, tex });
    // shader patches: baked-AO strength, rim, foreground dither
    const ao = m.aoStrength, rim = m.shader === 'unlit' ? 0 : m.surfaceType === 'ICE' ? this.rimStrength * 2 : this.rimStrength, fg = !!v.foreground;
    if (ao < 1 || rim > 0 || fg) {
      mat.onBeforeCompile = shader => {
        shader.uniforms.uAo = { value: ao };
        shader.uniforms.uRimColor = this.rim;
        shader.uniforms.uRimK = { value: rim };
        if (fg) { shader.uniforms.uPlayerPx = this.fade.uPlayerPx; shader.uniforms.uFadeR = this.fade.uFadeR; }
        shader.vertexShader = shader.vertexShader.replace('#include <common>', '#include <common>\nuniform float uAo;')
          .replace('#include <color_vertex>', '#include <color_vertex>\n#ifdef USE_COLOR\n vColor.rgb = mix(vec3(1.0), vColor.rgb, uAo);\n#endif');
        shader.fragmentShader = shader.fragmentShader.replace('#include <common>', `#include <common>\nuniform vec3 uRimColor; uniform float uRimK;${fg ? '\nuniform vec2 uPlayerPx; uniform float uFadeR;' : ''}`)
          .replace('#include <clipping_planes_fragment>', `#include <clipping_planes_fragment>${fg ? `
  { float fd = length(gl_FragCoord.xy - uPlayerPx); float fk = smoothstep(uFadeR * 0.55, uFadeR, fd);
    vec2 fq = floor(mod(gl_FragCoord.xy, 4.0)); float fth = (mod(fq.x * 2.0 + fq.y * 3.0 + fq.x * fq.y, 8.0) + 0.5) / 8.0; if (fk < fth) discard; }` : ''}`)
          .replace('#include <opaque_fragment>', `{ float rimF = pow(1.0 - saturate(dot(normalize(normal), normalize(vViewPosition))), 2.6); outgoingLight += uRimColor * rimF * uRimK; }
#include <opaque_fragment>`);
      };
    }
    mat.customProgramCacheKey = () => `mapmat-${ao < 1}-${rim > 0}-${fg}`;
    this.applyEnv(mat);
    mat.name = `${m.id}${variantKey(v) ? '|' + variantKey(v) : ''}`;
    return mat;
  }

  /** Scroll water / lava / waterfall textures. Cheap: only materials that flow. */
  update(timeSec: number): void {
    for (const f of this.flowing) for (const { t, k } of f.tex) t.offset.set((f.flow.x * k * timeSec) % 1, (f.flow.y * k * timeSec) % 1);
  }

  stats(): MaterialStats { return { materials: this.cache.size, programsKeys: new Set([...this.cache.values()].map(m => m.customProgramCacheKey())).size, textures: this.textures.count, textureBytes: this.textures.bytes }; }
  all(): THREE.MeshStandardMaterial[] { return [...this.cache.values()]; }

  dispose(): void {
    for (const m of this.cache.values()) { for (const t of [m.map, m.normalMap, m.emissiveMap]) t?.dispose(); m.dispose(); }
    this.cache.clear(); this.flowing.length = 0; this.fallback?.dispose(); this.fallback = null;
  }
}
