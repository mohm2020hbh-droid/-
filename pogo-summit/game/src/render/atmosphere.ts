import * as THREE from 'three';
import type { WorldTheme } from '../data/worlds';
import { type Rng, fbm3, noise3, range } from './noise';
import { col, lerpColor, merge, paint, solid } from './geom';
import { softPuffTexture, cumulusTexture } from './builders/structures';

/** Sky dome (gradient + sun glow), layered mountains (atmospheric perspective via fog) and the cloud sea. */

export interface SkyView { mesh: THREE.Mesh; update(camPos: THREE.Vector3): void }

export function createSky(theme: WorldTheme): SkyView {
  const s = theme.sky;
  const sun = new THREE.Vector3(...s.sunDir).normalize();
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: {
      uTop: { value: col(s.top) }, uMid: { value: col(s.mid) }, uHor: { value: col(s.horizon) },
      uSun: { value: sun }, uSunCol: { value: col(s.sun) }, uAlt: { value: 0 },
    },
    vertexShader: `varying vec3 vDir; void main(){ vDir = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); gl_Position.z = gl_Position.w; }`,
    fragmentShader: `uniform vec3 uTop; uniform vec3 uMid; uniform vec3 uHor; uniform vec3 uSun; uniform vec3 uSunCol; uniform float uAlt; varying vec3 vDir;
      void main(){
        // the gameplay camera only sees ±15° of elevation (narrow FOV), so the gradient is stretched ×3.4 to reach the zenith colour at the top
        // of the frame (as in the reference) and the sky gets deeper with altitude
        float h = clamp(vDir.y * 3.4 + uAlt, -0.2, 1.0);
        vec3 c = mix(uHor, uMid, smoothstep(0.0, 0.32, h));
        c = mix(c, uTop, smoothstep(0.22, 0.85, h));
        float d = max(dot(normalize(vDir), uSun), 0.0);
        c += uSunCol * (pow(d, 18.0) * 0.28 + pow(d, 320.0) * 1.1);
        c = mix(c, uHor, smoothstep(-0.05, -0.2, h));
        gl_FragColor = vec4(c, 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.SphereGeometry(900, 24, 14), mat);
  mesh.renderOrder = -100;
  mesh.frustumCulled = false;
  return { mesh, update(p) { mesh.position.copy(p); mat.uniforms.uAlt.value = Math.max(0, Math.min(0.18, p.y / 300)); } };
}

/**
 * Faceted 3D mountain ranges. UNLIT (colour + baked facet shading) so their value is fully controlled, then blended toward
 * the fog colour with distance (aerial perspective). Each layer follows the camera partially (`userData.follow`) so distant
 * ridges stay in frame during the climb, and has a solid skirt below so there is never a visible bottom edge.
 */
export function buildMountains(rng: Rng, theme: WorldTheme, minX: number, maxX: number): THREE.Group {
  const g = new THREE.Group();
  const sun = new THREE.Vector3(...theme.sky.sunDir).normalize();
  const fog = col(theme.fog.color);
  theme.mountains.forEach((m, li) => {
    const span = maxX - minX + m.width * 3.6;
    const nx = Math.round(span / 3.0), nz = 14;
    const geo = new THREE.PlaneGeometry(span, m.width * 0.9, nx, nz);
    geo.rotateX(-Math.PI / 2);
    const p = geo.getAttribute('position') as THREE.BufferAttribute;
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), z = p.getZ(i);
      const ridge = 1 - Math.abs(fbm3(x * 0.012 + li * 7, 0, z * 0.02, 31 + li, 4) * 2 - 1);
      const peak = Math.pow(ridge, 1.9) * 0.9 + fbm3(x * 0.05, 0, z * 0.05, 71 + li, 3) * 0.22 + fbm3(x * 0.16, 0, z * 0.16, 91 + li, 2) * 0.08;
      const edge = 1 - Math.pow(Math.abs(z) / (m.width * 0.45), 2.0);
      p.setXYZ(i, x, m.y + peak * m.height * Math.max(0.12, edge), z);
    }
    geo.computeVertexNormals();
    const base = col(m.color), snow = col('#f3f7ff'), shadow = base.clone().multiplyScalar(0.6).lerp(col('#5a5fb0'), 0.3);
    const haze = col(theme.fog.color), hazeAmt = 0.1 + 0.2 * li;   // baked aerial perspective: far ranges are hazier, near ranges keep their blue
    paint(geo, (x, y, z, nx_, ny, nz_) => {
      const t = Math.max(0, Math.min(1, (y - m.y) / m.height));
      const lit = Math.max(0, nx_ * sun.x + ny * sun.y + nz_ * sun.z);
      const c = lerpColor(shadow, base, 0.25 + 0.75 * Math.min(1, lit * 1.25));
      c.lerp(base, 0.25 * t);
      c.lerp(haze, hazeAmt * (1 - t * 0.6));                       // foot of each range dissolves into the haze
      if (m.snow && t > 0.74 && ny > 0.3) c.lerp(snow, Math.min(1, (t - 0.74) * 4.2) * (0.8 + 0.2 * lit));
      return c.multiplyScalar(0.94 + 0.12 * noise3(x * 0.1, y * 0.1, z * 0.1, li));
    }, true);
    const skirt = new THREE.BoxGeometry(span, 420, m.width * 0.9);
    skirt.translate(0, m.y - 210 + 1.5, 0);
    solid(skirt, base.clone().multiplyScalar(0.66).lerp(haze, hazeAmt + 0.1));
    const mesh = new THREE.Mesh(merge([geo, skirt]), new THREE.MeshBasicMaterial({ vertexColors: true, fog: false }));
    mesh.position.z = m.z;
    mesh.frustumCulled = false;
    mesh.userData.follow = 0.8 + li * 0.07;
    mesh.userData.cx = (minX + maxX) / 2;
    g.add(mesh);
  });
  void fog;
  return g;
}

export interface CloudSea { group: THREE.Group; update(dt: number): void }

export interface RouteStop { x: number; y: number }

export function buildClouds(rng: Rng, theme: WorldTheme, minX: number, maxX: number, minY: number, maxY: number, density = 1, stops: RouteStop[] = []): CloudSea {
  const group = new THREE.Group();
  const tex = softPuffTexture();
  const light = col(theme.cloud.light), shade = col(theme.cloud.shade);
  const sprites: { s: THREE.Sprite; speed: number; x0: number }[] = [];
  const add = (x: number, y: number, z: number, size: number, shadeAmt: number, opacity: number) => {
    const mat = new THREE.SpriteMaterial({ map: tex, color: lerpColor(light, shade, shadeAmt), transparent: true, opacity, depthWrite: false });
    const s = new THREE.Sprite(mat);
    s.scale.set(size, size * 0.55, 1);
    s.position.set(x, y, z);
    group.add(s);
    sprites.push({ s, speed: range(rng, 0.15, 0.5) * (1 + (-z) / 120), x0: x });
  };
  const w = maxX - minX;
  // dense sea below the climb
  const nSea = Math.round(58 * density * theme.cloud.amount);
  for (let i = 0; i < nSea; i++) {
    const z = -range(rng, 6, 150);
    const k = 1 + (-z) / 40;
    add(minX - 40 + rng() * (w + 120 + (-z) * 0.6), theme.cloud.seaY + range(rng, -3.2, 2.2) * k - (-z) * 0.1, z, range(rng, 18, 36) * k, range(rng, 0.35, 0.95), range(rng, 0.62, 0.88));
  }
  // drifting cumulus along the whole climb
  const nMid = Math.round(18 * density * theme.cloud.amount);
  for (let i = 0; i < nMid; i++) {
    const z = -range(rng, 25, 190);
    add(minX - 60 + rng() * (w + 160), range(rng, minY + 8, maxY + 40), z, range(rng, 14, 30) * (1 + (-z) / 90), range(rng, 0, 0.3), range(rng, 0.5, 0.78));
  }
  // cumulus banks along the whole climb: crisp storybook clouds below/beside every camera stop (never an empty haze, never a veil)
  const cum = cumulusTexture();
  const tanHalf = Math.tan(15 * Math.PI / 180);
  for (let si = 0; si < stops.length; si += 2) {           // every other stop: banks must frame the climb, not fill it
    const st = stops[si];
    const n = Math.max(1, Math.round(1.0 * density * theme.cloud.amount));
    for (let i = 0; i < n; i++) {
      // sized by angular extent (never a veil over the platform): 35-70 % of the half-frame, centred below the camera stop
      const z = -range(rng, 38, 130), H = (19 - z) * tanHalf;
      const mat = new THREE.SpriteMaterial({ map: cum, color: lerpColor(light, shade, range(rng, 0, 0.25)), transparent: true, opacity: range(rng, 0.9, 1), depthWrite: false });
      const sp = new THREE.Sprite(mat);
      const size = H * range(rng, 0.9, 1.6);
      sp.scale.set(size, size * 0.5, 1);
      sp.position.set(st.x + range(rng, -2.1, 2.1) * H, st.y + 1.6 - H * range(rng, 0.7, 1.15), z);
      group.add(sp);
      sprites.push({ s: sp, speed: range(rng, 0.1, 0.35), x0: sp.position.x });
    }
  }
  return {
    group,
    update(dt) {
      const span = w + 200;
      for (const c of sprites) {
        c.s.position.x += c.speed * dt;
        if (c.s.position.x > minX + span - 60) c.s.position.x -= span + 80;
      }
    },
  };
}
