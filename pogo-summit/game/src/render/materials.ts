import * as THREE from 'three';
import type { WorldTheme } from '../data/worlds';

/**
 * Stylized materials (VISUAL_DIRECTION §4): vertex colours + soft lighting + subtle RIM light + fog.
 * One shared material per look so large scenes stay within the draw-call budget.
 */
export interface StyleMaterials {
  flat: THREE.MeshStandardMaterial;
  smooth: THREE.MeshStandardMaterial;
  leaf: THREE.MeshStandardMaterial;
  basic: THREE.MeshBasicMaterial;
  outline: THREE.MeshBasicMaterial;
  glow: THREE.MeshBasicMaterial;
  rimUniform: { value: THREE.Color };
  dispose(): void;
}

function patchRim(mat: THREE.MeshStandardMaterial, rimColor: { value: THREE.Color }, strength: number): void {
  mat.onBeforeCompile = shader => {
    shader.uniforms.uRimColor = rimColor;
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nuniform vec3 uRimColor;')
      .replace(
        '#include <opaque_fragment>',
        `float rimF = pow(1.0 - saturate(dot(normalize(normal), normalize(vViewPosition))), 2.6);
         outgoingLight += uRimColor * rimF * ${strength.toFixed(3)};
         #include <opaque_fragment>`,
      );
  };
  mat.customProgramCacheKey = () => `rim${strength}`;
}

export function createStyleMaterials(theme: WorldTheme): StyleMaterials {
  const rimUniform = { value: new THREE.Color(theme.sky.sun).multiplyScalar(0.55) };
  const common = { vertexColors: true, roughness: 0.93, metalness: 0 } as const;
  const flat = new THREE.MeshStandardMaterial({ ...common, flatShading: true });
  const smooth = new THREE.MeshStandardMaterial({ ...common });
  const leaf = new THREE.MeshStandardMaterial({ ...common, roughness: 0.85 });
  patchRim(flat, rimUniform, 0.38);
  patchRim(smooth, rimUniform, 0.34);
  patchRim(leaf, rimUniform, 0.5);
  const basic = new THREE.MeshBasicMaterial({ vertexColors: true });
  const glow = new THREE.MeshBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false });

  const outline = new THREE.MeshBasicMaterial({ color: new THREE.Color('#2a1420'), side: THREE.BackSide });
  outline.onBeforeCompile = shader => {
    shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\n transformed += normalize(normal) * 0.045;');
  };
  outline.customProgramCacheKey = () => 'outline';
  return {
    flat, smooth, leaf, basic, outline, glow, rimUniform,
    dispose() { flat.dispose(); smooth.dispose(); leaf.dispose(); basic.dispose(); outline.dispose(); glow.dispose(); },
  };
}
