import * as THREE from 'three';
import type { WorldTheme } from '../data/worlds';
import { patchSurface } from './surface';

/**
 * Stylized materials (VISUAL_DIRECTION §4): vertex colours + soft lighting + rim light + fog, plus world-space triplanar
 * surface detail (render/surface.ts) on terrain and foliage. One shared material per look keeps the draw-call budget.
 *   smooth / flat  terrain, props (detail: rock on sides, grass on tops)
 *   leaf           foliage (clumpy detail + back-light)
 *   char           the player: NO surface detail (clean, readable), stronger rim for separation from the background
 */
export interface StyleMaterials {
  flat: THREE.MeshStandardMaterial;
  smooth: THREE.MeshStandardMaterial;
  leaf: THREE.MeshStandardMaterial;
  char: THREE.MeshStandardMaterial;
  /** camera-attached framing foliage: no world-space detail (it would swim), strong back-light. */
  frame: THREE.MeshStandardMaterial;
  /** glossy faceted crystal (ice / obsidian / glazed tiles): sun glints on the facets. */
  ice: THREE.MeshStandardMaterial;
  basic: THREE.MeshBasicMaterial;
  outline: THREE.MeshBasicMaterial;
  glow: THREE.MeshBasicMaterial;
  rimUniform: { value: THREE.Color };
  dispose(): void;
}

export function createStyleMaterials(theme: WorldTheme, detail = true): StyleMaterials {
  const rimUniform = { value: new THREE.Color(theme.sky.sun).multiplyScalar(0.55) };
  const charRim = { value: new THREE.Color(theme.sky.sun).lerp(new THREE.Color('#ffffff'), 0.4).multiplyScalar(0.8) };
  const common = { vertexColors: true, roughness: 0.9, metalness: 0 } as const;
  const flat = new THREE.MeshStandardMaterial({ ...common, flatShading: true });
  const smooth = new THREE.MeshStandardMaterial({ ...common });
  const leaf = new THREE.MeshStandardMaterial({ ...common, roughness: 0.8 });
  const char = new THREE.MeshStandardMaterial({ ...common, roughness: 0.62 });
  const frame = new THREE.MeshStandardMaterial({ ...common, roughness: 0.8 });
  const ice = new THREE.MeshStandardMaterial({ ...common, roughness: 0.18, metalness: 0.05, flatShading: true });
  patchSurface(flat, rimUniform, { scale: 0.45, strength: 0.5, rim: 0.36, grassTops: true }, detail);
  patchSurface(smooth, rimUniform, { scale: 0.45, strength: 0.55, rim: 0.34, grassTops: true }, detail);
  patchSurface(leaf, rimUniform, { scale: 0.7, strength: 0.6, rim: 0.5, grassTops: false, foliage: true }, detail);
  patchSurface(char, charRim, { scale: 1, strength: 0, rim: 0.62, grassTops: false }, false);
  patchSurface(ice, { value: new THREE.Color('#e8f6ff').multiplyScalar(0.7) }, { scale: 1, strength: 0, rim: 0.8, grassTops: false }, false);
  patchSurface(frame, rimUniform, { scale: 1, strength: 0, rim: 0.55, grassTops: false, foliage: true }, false);
  const basic = new THREE.MeshBasicMaterial({ vertexColors: true });
  const glow = new THREE.MeshBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false });

  const outline = new THREE.MeshBasicMaterial({ color: new THREE.Color('#2a1420'), side: THREE.BackSide });
  outline.onBeforeCompile = shader => {
    shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\n transformed += normalize(normal) * 0.04;');
  };
  outline.customProgramCacheKey = () => 'outline';
  return {
    flat, smooth, leaf, char, frame, ice, basic, outline, glow, rimUniform,
    dispose() { flat.dispose(); smooth.dispose(); leaf.dispose(); char.dispose(); frame.dispose(); ice.dispose(); basic.dispose(); outline.dispose(); glow.dispose(); },
  };
}
