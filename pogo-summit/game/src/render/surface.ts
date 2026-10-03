import * as THREE from 'three';

/**
 * Surface detail (visual upgrade): ONE small tileable procedural texture (256², RGBA, mip-mapped) sampled TRIPLANAR in
 * world space by every terrain/prop material, so large surfaces get painterly grain instead of reading as flat polygons.
 *   R = rock   (fbm + Voronoi cracks + horizontal strata)
 *   G = grass  (clumpy blades / tufts, used on up-facing surfaces)
 *   B = fine   (grain for wood / ice / cloth)
 *   A = cavity (soft large blotches used as ambient-occlusion-like darkening)
 * Generated once at start-up (~15 ms), no binary asset, ≈ 340 KB of GPU memory with mips.
 */
const N = 256;

function hash(x: number, y: number, s: number): number {
  let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

/** Periodic value noise on a `period`-cell lattice (tiles over the unit square). */
function pnoise(u: number, v: number, period: number, seed: number): number {
  const x = u * period, y = v * period;
  const xi = Math.floor(x), yi = Math.floor(y);
  const xf = x - xi, yf = y - yi;
  const sx = xf * xf * (3 - 2 * xf), sy = yf * yf * (3 - 2 * yf);
  const w = (i: number) => ((i % period) + period) % period;
  const a = hash(w(xi), w(yi), seed), b = hash(w(xi + 1), w(yi), seed);
  const c = hash(w(xi), w(yi + 1), seed), d = hash(w(xi + 1), w(yi + 1), seed);
  return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
}

function pfbm(u: number, v: number, period: number, seed: number, oct: number): number {
  let s = 0, amp = 0.5, tot = 0, p = period;
  for (let i = 0; i < oct; i++) { s += pnoise(u, v, p, seed + i * 17) * amp; tot += amp; amp *= 0.5; p *= 2; }
  return s / tot;
}

/** Periodic Voronoi: returns F2-F1 (small near cell edges ⇒ cracks). */
function pvoronoi(u: number, v: number, cells: number, seed: number): number {
  const x = u * cells, y = v * cells;
  const xi = Math.floor(x), yi = Math.floor(y);
  let f1 = 9, f2 = 9;
  for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) {
    const cx = xi + i, cy = yi + j;
    const wx = ((cx % cells) + cells) % cells, wy = ((cy % cells) + cells) % cells;
    const px = cx + hash(wx, wy, seed), py = cy + hash(wx, wy, seed + 7);
    const d = Math.hypot(px - x, py - y);
    if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) f2 = d;
  }
  return f2 - f1;
}

let _tex: THREE.DataTexture | null = null;

export function detailTexture(): THREE.DataTexture {
  if (_tex) return _tex;
  const data = new Uint8Array(N * N * 4);
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const u = x / N, v = y / N;
      // rock: soft fbm, chiselled cracks, faint strata
      const f = pfbm(u, v, 4, 11, 5);
      const cr = pvoronoi(u, v, 7, 3), cr2 = pvoronoi(u + 0.37, v + 0.11, 15, 9);
      const crack = Math.min(1, cr / 0.09) * 0.6 + Math.min(1, cr2 / 0.07) * 0.4;
      const strata = 0.5 + 0.5 * Math.sin((v + pfbm(u, v, 4, 31, 2) * 0.12) * Math.PI * 2 * 9);
      const rock = 0.38 + f * 0.42 + (crack - 1) * 0.42 + strata * 0.08;
      // grass: clumps + bright blade tips
      const clump = pfbm(u, v, 8, 51, 3);
      const blades = pnoise(u * 1.0, v * 1.0, 64, 61) * 0.5 + pnoise(u, v, 128, 67) * 0.5;
      const grass = 0.3 + clump * 0.45 + (blades - 0.5) * 0.45;
      // fine grain
      const fine = 0.35 + pfbm(u, v, 32, 81, 3) * 0.6;
      // cavity / large blotches
      const cav = pfbm(u, v, 3, 91, 3);
      const i = (y * N + x) * 4;
      data[i] = Math.max(0, Math.min(255, rock * 255));
      data[i + 1] = Math.max(0, Math.min(255, grass * 255));
      data[i + 2] = Math.max(0, Math.min(255, fine * 255));
      data[i + 3] = Math.max(0, Math.min(255, cav * 255));
    }
  }
  const t = new THREE.DataTexture(data, N, N, THREE.RGBAFormat);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.magFilter = THREE.LinearFilter;
  t.minFilter = THREE.LinearMipmapLinearFilter;
  t.generateMipmaps = true;
  t.anisotropy = 2;
  t.needsUpdate = true;
  _tex = t;
  return t;
}

export interface SurfaceOpts {
  /** triplanar frequency (repeats per metre). */
  scale: number;
  /** 0..1 how strongly the detail modulates the vertex colour. */
  strength: number;
  /** rim light strength. */
  rim: number;
  /** use the grass channel on up-facing surfaces. */
  grassTops: boolean;
  /** foliage mode: clumpy light/dark + translucency toward the sun. */
  foliage?: boolean;
}

/**
 * Patch a MeshStandardMaterial: rim light + world-space triplanar detail + soft "sky occlusion" (down-facing surfaces
 * darker, up-facing slightly brighter) — cheap stand-ins for AO / GI that read well on a phone screen.
 */
export function patchSurface(mat: THREE.MeshStandardMaterial, rimColor: { value: THREE.Color }, o: SurfaceOpts, enabled = true): void {
  const tex = detailTexture();
  mat.onBeforeCompile = shader => {
    shader.uniforms.uRimColor = rimColor;
    shader.uniforms.uDetail = { value: tex };
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vDWPos; varying vec3 vDWNor;')
      .replace('#include <begin_vertex>', `#include <begin_vertex>
        {
          vec4 dwp = vec4(transformed, 1.0);
          vec3 dwn = objectNormal;
          #ifdef USE_INSTANCING
            dwp = instanceMatrix * dwp; dwn = mat3(instanceMatrix) * dwn;
          #endif
          vDWPos = (modelMatrix * dwp).xyz;
          vDWNor = normalize(mat3(modelMatrix) * dwn);
        }`);
    const detail = enabled ? `
        {
          vec3 wn = normalize(vDWNor);
          vec3 bw = pow(abs(wn), vec3(4.0)); bw /= (bw.x + bw.y + bw.z + 1e-5);
          vec2 pzy = vDWPos.zy * ${o.scale.toFixed(3)}, pxz = vDWPos.xz * ${o.scale.toFixed(3)}, pxy = vDWPos.xy * ${o.scale.toFixed(3)};
          vec4 tX = texture2D(uDetail, pzy), tY = texture2D(uDetail, pxz), tZ = texture2D(uDetail, pxy);
          vec4 d = tX * bw.x + tY * bw.y + tZ * bw.z;
          // a second, coarser octave breaks the tiling
          vec4 d2 = texture2D(uDetail, pxy * 0.23 + vec2(0.31, 0.17)) * bw.z + texture2D(uDetail, pxz * 0.23 + vec2(0.71, 0.43)) * bw.y + texture2D(uDetail, pzy * 0.23 + vec2(0.13, 0.59)) * bw.x;
          #ifdef USE_COLOR_ALPHA
            float sType = vColor.a;
          #else
            float sType = 1.0;
          #endif
          diffuseColor.a = 1.0;
          float top = ${o.grassTops ? 'smoothstep(0.55, 0.85, wn.y)' : '0.0'};
          float k = mix(d.r * 0.75 + d2.r * 0.25, d.g * 0.8 + d2.g * 0.2, top);
          ${o.foliage ? 'k = d.g * 0.55 + d2.a * 0.45;' : ''}
          // wood: grain stretched along the plank (x), from the fine channel
          float grain = texture2D(uDetail, vec2(vDWPos.x * 0.09, (vDWPos.y + vDWPos.z) * 1.7)).b * 0.7 + d.b * 0.3;
          float isWood = step(0.4, sType) * (1.0 - step(0.8, sType));
          float isLeaf = step(0.85, sType) * (1.0 - step(0.95, sType));
          k = mix(k, grain, isWood);
          k = mix(k, d.g * 0.5 + d2.a * 0.5, isLeaf);
          float fade = 1.0 - smoothstep(60.0, 160.0, length(vDWPos - cameraPosition));
          float s = ${o.strength.toFixed(3)} * fade * step(0.4, sType);
          diffuseColor.rgb *= mix(1.0, 0.62 + 0.76 * k, s);
          // cavity blotches (very low frequency): breaks up large uniform areas
          diffuseColor.rgb *= mix(1.0, 0.86 + 0.28 * d2.a, s * 0.7);
          // sky occlusion: undersides darker, tops a touch brighter
          diffuseColor.rgb *= 0.8 + 0.28 * smoothstep(-0.8, 0.9, wn.y);
        }` : `diffuseColor.rgb *= 0.8 + 0.28 * smoothstep(-0.8, 0.9, normalize(vDWNor).y);`;
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nuniform vec3 uRimColor; uniform sampler2D uDetail; varying vec3 vDWPos; varying vec3 vDWNor;')
      .replace('#include <color_fragment>', `#include <color_fragment>\n${detail}`)
      .replace('#include <opaque_fragment>', `
        {
          float rimF = pow(1.0 - saturate(dot(normalize(normal), normalize(vViewPosition))), 2.6);
          outgoingLight += uRimColor * rimF * ${o.rim.toFixed(3)};
          ${o.foliage ? `// back-lit leaves glow a little
          outgoingLight += diffuseColor.rgb * uRimColor * 0.35 * rimF;` : ''}
        }
        #include <opaque_fragment>`);
  };
  mat.customProgramCacheKey = () => `surf-${o.scale}-${o.strength}-${o.rim}-${o.grassTops}-${o.foliage ?? false}-${enabled}`;
}
