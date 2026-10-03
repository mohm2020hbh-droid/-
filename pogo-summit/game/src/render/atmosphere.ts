import * as THREE from 'three';
import type { WorldTheme } from '../data/worlds';
import { type Rng, fbm3, noise3, range } from './noise';
import { col, lerpColor, merge, paint, solid } from './geom';
import { softPuffTexture } from './builders/structures';

/** Sky dome (gradient + sun glow), layered mountains (atmospheric perspective via fog) and the cloud sea. */

export interface SkyView { mesh: THREE.Mesh; update(camPos: THREE.Vector3): void }

export function createSky(theme: WorldTheme): SkyView {
  const s = theme.sky;
  const sun = new THREE.Vector3(...s.sunDir).normalize();
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: {
      uTop: { value: col(s.top) }, uMid: { value: col(s.mid) }, uHor: { value: col(s.horizon) },
      uSun: { value: sun }, uSunCol: { value: col(s.sun) },
    },
    vertexShader: `varying vec3 vDir; void main(){ vDir = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); gl_Position.z = gl_Position.w; }`,
    fragmentShader: `uniform vec3 uTop; uniform vec3 uMid; uniform vec3 uHor; uniform vec3 uSun; uniform vec3 uSunCol; varying vec3 vDir;
      void main(){
        float h = clamp(vDir.y, -0.2, 1.0);
        vec3 c = mix(uHor, uMid, smoothstep(0.0, 0.32, h));
        c = mix(c, uTop, smoothstep(0.22, 0.85, h));
        float d = max(dot(normalize(vDir), uSun), 0.0);
        c += uSunCol * (pow(d, 18.0) * 0.28 + pow(d, 320.0) * 1.1);
        c = mix(c, uHor, smoothstep(0.0, -0.2, vDir.y));
        gl_FragColor = vec4(c, 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.SphereGeometry(900, 24, 14), mat);
  mesh.renderOrder = -100;
  mesh.frustumCulled = false;
  return { mesh, update(p) { mesh.position.copy(p); } };
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
    const nx = Math.round(span / 6), nz = 5;
    const geo = new THREE.PlaneGeometry(span, m.width * 0.9, nx, nz);
    geo.rotateX(-Math.PI / 2);
    const p = geo.getAttribute('position') as THREE.BufferAttribute;
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), z = p.getZ(i);
      const ridge = 1 - Math.abs(fbm3(x * 0.012 + li * 7, 0, z * 0.02, 31 + li, 4) * 2 - 1);
      const peak = Math.pow(ridge, 1.7) * 0.95 + fbm3(x * 0.05, 0, z * 0.05, 71 + li, 2) * 0.25;
      const edge = 1 - Math.pow(Math.abs(z) / (m.width * 0.45), 2.0);
      p.setXYZ(i, x, m.y + peak * m.height * Math.max(0.12, edge), z);
    }
    const base = col(m.color), snow = col('#f6f8ff'), shadow = base.clone().multiplyScalar(0.62).lerp(col('#6a74b8'), 0.25);
    paint(geo, (x, y, z, nx_, ny, nz_) => {
      const t = Math.max(0, Math.min(1, (y - m.y) / m.height));
      const lit = Math.max(0, nx_ * sun.x + ny * sun.y + nz_ * sun.z);
      const c = lerpColor(shadow, base, 0.25 + 0.75 * Math.min(1, lit * 1.25));
      c.lerp(base, 0.25 * t);
      if (m.snow && t > 0.52 && ny > 0.2) c.lerp(snow, Math.min(1, (t - 0.52) * 3.4) * (0.55 + 0.45 * lit));
      return c.multiplyScalar(0.94 + 0.12 * noise3(x * 0.1, y * 0.1, z * 0.1, li));
    });
    const skirt = new THREE.BoxGeometry(span, 420, m.width * 0.9);
    skirt.translate(0, m.y - 210 + 1.5, 0);
    solid(skirt, base.clone().multiplyScalar(0.66));
    const mesh = new THREE.Mesh(merge([geo, skirt]), new THREE.MeshBasicMaterial({ vertexColors: true, fog: true }));
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

export function buildClouds(rng: Rng, theme: WorldTheme, minX: number, maxX: number, minY: number, maxY: number, density = 1): CloudSea {
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
