import * as THREE from 'three';
import type { LevelData, PlatformDef } from '../data/LevelData';
import { allPlatforms } from '../data/LevelData';
import type { WorldTheme } from '../data/worlds';
import { type PhysicsWorld, surfaceBelow } from '../sim/PhysicsWorld';
import type { Pose } from '../sim/PogoState';
import type { PogoState } from '../sim/PogoState';
import type { SimEvent } from '../sim/events';
import { DEG } from '../sim/math';
import { launchPower } from '../sim/JumpSystem';
import type { PhysicsConfig } from '../sim/PhysicsConfig';
import { CameraRig } from './CameraRig';
import { Character, type CharacterAppearance } from './Character';
import { Vfx } from './Vfx';
import { type StyleMaterials, createStyleMaterials } from './materials';
import { buildClouds, buildMountains, createSky, type CloudSea, type SkyView } from './atmosphere';
import { buildRockPlatform, buildCliff } from './builders/rock';
import { buildBouncePad, buildGoal, buildHazard, buildIcePlatform, buildWoodPlatform, glowTexture, type BouncePadView, type GoalView } from './builders/special';
import { buildArchBridge, buildCastle, buildIsland, buildPillar, buildWaterfall, buildWoodBridge, type WaterfallView } from './builders/structures';
import { bigTree } from './builders/trees';
import { blobGeometry, col, merge, mesh, paint, xf } from './geom';
import { mulberry32, noise3, range } from './noise';

export type Quality = 'high' | 'default' | 'simplified';
export interface QualityProfile { pixelRatio: number; shadows: boolean; shadowMap: number; antialias: boolean; decor: number; particles: number }
export const QUALITY: Record<Quality, QualityProfile> = {
  high: { pixelRatio: 2, shadows: true, shadowMap: 2048, antialias: true, decor: 1, particles: 1 },
  default: { pixelRatio: 1.5, shadows: true, shadowMap: 1024, antialias: true, decor: 0.8, particles: 0.8 },
  simplified: { pixelRatio: 1, shadows: false, shadowMap: 512, antialias: false, decor: 0.45, particles: 0.4 },
};

export interface RenderFrame {
  dt: number;
  alpha: number;
  pose: Pose;
  state: PogoState;
  events: readonly SimEvent[];
  tilt: number;
  pull: number;
}

const DUST: Record<string, string> = { grass: '#d8d0a0', wood: '#c9a070', stone: '#cdbca8', ice: '#cfeaff', goo: '#9ad060', metal: '#ffd9a0', sand: '#ebd2a0', crystal: '#ffb0a0', goal: '#ffe9a0' };

export class GameRenderer {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene = new THREE.Scene();
  readonly rig = new CameraRig();
  readonly vfx: Vfx;
  character!: Character;
  mats!: StyleMaterials;
  quality: Quality;
  theme!: WorldTheme;
  level!: LevelData;
  private world!: PhysicsWorld;
  private cfg!: PhysicsConfig;
  private levelGroup = new THREE.Group();
  private sun!: THREE.DirectionalLight;
  private hemi!: THREE.HemisphereLight;
  private sky!: SkyView;
  private clouds!: CloudSea;
  private goal!: GoalView;
  private waterfalls: WaterfallView[] = [];
  private bouncePads = new Map<number, { view: BouncePadView; comp: number }>();
  private movers: { obj: THREE.Object3D; colliderIndex: number; bx: number; by: number; chains?: THREE.Mesh[]; anchors?: THREE.Vector3[] }[] = [];
  private frameFoliage = new THREE.Group();
  private shadowBlob!: THREE.Mesh;
  private hazardGlows: THREE.Sprite[] = [];
  private mountains: THREE.Group | null = null;
  private canvas: HTMLCanvasElement;
  private time = 0;
  private fpsAcc = 0; private fpsN = 0; fps = 60;
  private lastW = 0; private lastH = 0;
  private lastDpr = 1;
  private shakeTmp = new THREE.Vector3();
  private appearance?: CharacterAppearance;
  private chargeFxT = 0;
  private cloudSeed = 1;
  /** Dynamic resolution (Phase 17): 0.6…1 multiplier on the pixel ratio. */
  resScale = 1;
  boostTint = '#ffb347';
  /** Optional theme override (menu world preview). */
  screenShake = true;

  constructor(canvas: HTMLCanvasElement, quality: Quality = 'default') {
    this.canvas = canvas;
    this.quality = quality;
    const q = QUALITY[quality];
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: q.antialias, powerPreference: 'high-performance', alpha: false, stencil: false, preserveDrawingBuffer: false });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.NeutralToneMapping;
    this.renderer.shadowMap.enabled = q.shadows;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.vfx = new Vfx(320);
    this.scene.add(this.rig.camera);
  }

  setLighting(o: { sun?: number; hemi?: number; exposure?: number; rim?: number; tone?: string }): void {
    if (o.tone) { const t: Record<string, THREE.ToneMapping> = { neutral: THREE.NeutralToneMapping, aces: THREE.ACESFilmicToneMapping, agx: THREE.AgXToneMapping, linear: THREE.LinearToneMapping, none: THREE.NoToneMapping }; this.renderer.toneMapping = t[o.tone] ?? THREE.NeutralToneMapping; this.scene.traverse(x => { const m = (x as THREE.Mesh).material as THREE.Material | undefined; if (m) m.needsUpdate = true; }); }
    if (o.sun !== undefined) this.sun.intensity = o.sun;
    if (o.hemi !== undefined) this.hemi.intensity = o.hemi;
    if (o.exposure !== undefined) this.renderer.toneMappingExposure = o.exposure;
    if (o.rim !== undefined) this.mats.rimUniform.value.set(this.theme.sky.sun).multiplyScalar(o.rim);
  }

  setResScale(v: number): void { const n = Math.max(0.6, Math.min(1, v)); if (Math.abs(n - this.resScale) < 0.01) return; this.resScale = n; this.resize(this.lastW, this.lastH, this.lastDpr, true); }

  setAppearance(a: CharacterAppearance): void { this.appearance = a; this.character?.setAppearance(a); }

  setQuality(q: Quality): void {
    this.quality = q;
    const p = QUALITY[q];
    this.renderer.shadowMap.enabled = p.shadows;
    if (this.sun) { this.sun.castShadow = p.shadows; this.sun.shadow.mapSize.set(p.shadowMap, p.shadowMap); this.sun.shadow.map?.dispose(); (this.sun.shadow as { map: unknown }).map = null; }
    this.vfx.setDensity(p.particles);
    this.resize(this.lastW || window.innerWidth, this.lastH || window.innerHeight, window.devicePixelRatio || 1, true);
    this.scene.traverse(o => { const m = (o as THREE.Mesh).material as THREE.Material | undefined; if (m) m.needsUpdate = true; });
  }

  resize(w: number, h: number, dpr: number, force = false): void {
    if (!force && w === this.lastW && h === this.lastH && dpr === this.lastDpr) return;
    this.lastW = w; this.lastH = h; this.lastDpr = dpr;
    const pr = Math.min(dpr, QUALITY[this.quality].pixelRatio) * this.resScale;
    this.renderer.setPixelRatio(pr);
    this.renderer.setSize(w, h, false);
    this.rig.camera.aspect = w / Math.max(1, h);
    this.rig.camera.updateProjectionMatrix();
    this.layoutFrameFoliage();
  }

  // ────────────────────────────────────────────────────────── level ──────
  loadLevel(level: LevelData, theme: WorldTheme, world: PhysicsWorld, cfg: PhysicsConfig): void {
    this.disposeLevel();
    this.level = level; this.theme = theme; this.world = world; this.cfg = cfg;
    const q = QUALITY[this.quality];
    this.mats?.dispose();
    this.mats = createStyleMaterials(theme);
    this.scene.add(this.levelGroup);
    this.scene.fog = new THREE.FogExp2(theme.fog.color, theme.fog.density);
    this.scene.background = new THREE.Color(theme.fog.color);
    this.renderer.toneMappingExposure = theme.sky.exposure;
    this.vfx.setFog(new THREE.Color(theme.fog.color), theme.fog.density);

    // lights
    this.hemi = new THREE.HemisphereLight(theme.sky.hemiSky, theme.sky.hemiGround, theme.sky.hemiIntensity);
    this.scene.add(this.hemi);
    this.sun = new THREE.DirectionalLight(theme.sky.sun, theme.sky.sunIntensity);
    this.sun.castShadow = q.shadows;
    this.sun.shadow.mapSize.set(q.shadowMap, q.shadowMap);
    const sc = this.sun.shadow.camera;
    sc.left = -17; sc.right = 17; sc.top = 17; sc.bottom = -17; sc.near = 1; sc.far = 90;
    this.sun.shadow.bias = -0.0006;
    this.sun.shadow.normalBias = 0.06;
    this.sun.shadow.radius = 3;
    this.scene.add(this.sun, this.sun.target);

    this.sky = createSky(theme);
    this.scene.add(this.sky.mesh);

    const rng = mulberry32(level.landmarks.reduce((a, l) => a + (l.seed ?? 1), 7919));
    const b = level.bounds;
    // aerial layers
    this.mountains = buildMountains(rng, theme, b.minX, b.maxX);
    this.levelGroup.add(this.mountains);
    this.clouds = buildClouds(rng, theme, b.minX, b.maxX, b.minY, b.maxY, q.decor, allPlatforms(level).map(p => ({ x: p.x, y: p.y })));
    this.levelGroup.add(this.clouds.group);
    this.buildFloatingWorld(rng, q.decor);
    this.buildLandmarks();
    this.buildPlatforms();
    this.buildObstacles();
    this.buildHazards();
    const g = level.goal;
    this.goal = buildGoal(g.x, g.y, theme, this.mats);
    this.levelGroup.add(this.goal.group);

    // shadow blob under the player
    const blobTex = (() => {
      const c = document.createElement('canvas'); c.width = c.height = 64;
      const x = c.getContext('2d')!; const gr = x.createRadialGradient(32, 32, 0, 32, 32, 32);
      gr.addColorStop(0, 'rgba(20,10,30,0.55)'); gr.addColorStop(0.6, 'rgba(20,10,30,0.25)'); gr.addColorStop(1, 'rgba(20,10,30,0)');
      x.fillStyle = gr; x.fillRect(0, 0, 64, 64);
      const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
    })();
    this.shadowBlob = new THREE.Mesh(new THREE.PlaneGeometry(2.2, 1.6), new THREE.MeshBasicMaterial({ map: blobTex, transparent: true, depthWrite: false }));
    this.shadowBlob.rotation.x = -Math.PI / 2;
    this.shadowBlob.renderOrder = 2;
    this.scene.add(this.shadowBlob);

    this.character = new Character(this.mats, this.cfg.comHeight);
    if (this.appearance) this.character.setAppearance(this.appearance);
    this.scene.add(this.character.root);
    this.scene.add(this.vfx.mesh);
    this.buildFrameFoliage();
    this.rig.setBounds({ minX: b.minX, maxX: b.maxX, minY: b.minY, maxY: b.maxY });
    this.rig.camera.add(this.frameFoliage);
    this.layoutFrameFoliage();
  }

  private disposeLevel(): void {
    this.scene.remove(this.levelGroup);
    this.levelGroup.traverse(o => { const m = o as THREE.Mesh; if (m.geometry) m.geometry.dispose(); });
    this.levelGroup = new THREE.Group();
    this.waterfalls = []; this.bouncePads.clear(); this.movers = []; this.hazardGlows = [];
    for (const o of [this.hemi, this.sun, this.sun?.target, this.sky?.mesh, this.shadowBlob, this.character?.root, this.vfx.mesh]) if (o) this.scene.remove(o);
    this.rig.camera.remove(this.frameFoliage);
    this.frameFoliage = new THREE.Group();
    this.vfx.clear();
  }

  private depthFor(p: PlatformDef): number { return p.depth ?? (p.kind === 'wood' ? 2.8 : p.kind === 'ice' ? 4.2 : p.kind === 'bounce' ? 3 : 5); }

  private buildPlatforms(): void {
    const { theme, mats, world } = this;
    for (const p of allPlatforms(this.level)) {
      const col_ = world.colliders.find(c => c.id === p.id)!;
      let obj: THREE.Object3D;
      if (p.kind === 'wood') obj = mesh(buildWoodPlatform({ ...p, depth: this.depthFor(p) }, theme), mats.smooth, true, true);
      else if (p.kind === 'ice') obj = mesh(buildIcePlatform({ ...p, depth: this.depthFor(p) }, theme), mats.smooth, true, true);
      else if (p.kind === 'bounce') {
        const view = buildBouncePad(p, theme, mats);
        this.bouncePads.set(col_.index, { view, comp: 0 });
        obj = view.group;
      } else {
        obj = mesh(buildRockPlatform({ w: p.w, h: p.h, depth: this.depthFor(p), taper: p.taper ?? 0.72, seed: p.seed ?? 1, theme, decor: p.decor ?? 'none' }), mats.smooth, true, true);
      }
      obj.name = `platform:${p.id}`;
      obj.position.set(p.x, p.y, 0);
      if (p.angleDeg) obj.rotation.z = p.angleDeg * DEG;
      this.levelGroup.add(obj);
      if (p.move) {
        const mv: (typeof this.movers)[number] = { obj, colliderIndex: col_.index, bx: p.x, by: p.y };
        if (p.kind === 'wood') {
          // hanging chains to a fixed anchor high above (the platform swings under them)
          const chains: THREE.Mesh[] = [], anchors: THREE.Vector3[] = [];
          for (const sx of [-1, 1]) {
            const m = mesh(new THREE.CylinderGeometry(0.11, 0.11, 1, 6), new THREE.MeshStandardMaterial({ color: '#b4b6c6', roughness: 0.55, metalness: 0.1 }), false, false);
            this.levelGroup.add(m); chains.push(m); anchors.push(new THREE.Vector3(p.x + sx * (p.w / 2 - 0.35), p.y + 15, 0));
          }
          mv.chains = chains; mv.anchors = anchors;
        }
        this.movers.push(mv);
      }
    }
  }

  private buildObstacles(): void {
    const { theme, mats } = this;
    for (const o of this.level.obstacles) {
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      for (const p of o.pts) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
      const w = maxX - minX, h = maxY - minY;
      const cliff = buildCliff((minX + maxX) / 2, (minY + maxY) / 2, w, h, o.depth ?? 8, o.seed ?? 1, theme, o.kind !== 'cliff');
      const cm = mesh(cliff, mats.smooth, false, true); cm.name = `obstacle:${o.id}`;
      this.levelGroup.add(cm);
    }
  }

  private buildHazards(): void {
    for (const h of this.level.hazards) {
      const m = mesh(buildHazard(h, this.theme), this.mats.smooth, true, true);
      m.position.set(h.x, h.y, 0);
      this.levelGroup.add(m);
      const glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTexture('#ff4a3a'), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, opacity: 0.55 }));
      glow.scale.set(h.w * 2.4, h.h * 3, 1); glow.position.set(h.x, h.y + h.h * 0.5, 0.6);
      this.levelGroup.add(glow); this.hazardGlows.push(glow);
    }
  }

  private buildLandmarks(): void {
    const { theme, mats } = this;
    for (const l of this.level.landmarks) {
      const rng = mulberry32((l.seed ?? 1) * 7919);
      const s = l.scale ?? 1;
      switch (l.type) {
        case 'castle': {
          const c = buildCastle(rng, theme);
          const m = mesh(c.body, mats.smooth, false, false); m.position.set(l.x, l.y, l.z); m.scale.setScalar(s); this.levelGroup.add(m);
          const gm = new THREE.Mesh(c.glow, new THREE.MeshBasicMaterial({ vertexColors: true })); gm.position.copy(m.position); gm.scale.setScalar(s); this.levelGroup.add(gm);
          break;
        }
        case 'arch_bridge': { const m = mesh(buildArchBridge(theme), mats.smooth, false, false); m.position.set(l.x, l.y, l.z); m.scale.setScalar(s); if (l.flip) m.rotation.y = Math.PI; this.levelGroup.add(m); break; }
        case 'wood_bridge': { const m = mesh(buildWoodBridge(theme), mats.smooth, true, false); m.position.set(l.x, l.y, l.z); m.scale.setScalar(s); this.levelGroup.add(m); break; }
        case 'waterfall': { const w = buildWaterfall(rng, theme, mats, s, l.seed ?? 1); w.group.name = `landmark:${l.id}`; w.group.position.set(l.x, l.y, l.z); this.levelGroup.add(w.group); this.waterfalls.push(w); break; }
        case 'floating_island': { const m = mesh(buildIsland(rng, theme, 7 * s, 5 * s, l.seed ?? 1, { detail: 1 }), mats.smooth, false, true); m.position.set(l.x, l.y, l.z); this.levelGroup.add(m); break; }
        case 'big_tree': { const m = mesh(merge(bigTree(rng, theme, s)), mats.leaf, false, false); m.name = `landmark:${l.id}`; m.position.set(l.x, l.y, l.z); this.levelGroup.add(m); break; }
        default: break;
      }
    }
  }

  /** Islands + rock pillars scattered in the depth volume behind the gameplay plane. */
  private buildFloatingWorld(rng: () => number, density: number): void {
    const { theme, mats, level } = this;
    const b = level.bounds;
    const placed: { x: number; y: number; z: number; r: number }[] = [];
    const nIsl = Math.round(34 * density), nPil = Math.round(7 * density);
    // Camera stops along the climb (platform centres): the sky band above them must stay readable (reference: ~1/3 of the frame
    // is open sky). A candidate whose vertical extent enters [camY+0.2H, camY+1.0H] at some stop that can see it is rejected.
    const stops = allPlatforms(level).map(p => ({ x: p.x, y: p.y + 1.6 }));
    const tanHalf = Math.tan((this.rig.baseFov * Math.PI) / 360);
    const blocksSky = (x: number, y: number, z: number, r: number): boolean => {
      const H = (this.rig.baseDistance + -z) * tanHalf;
      const lo = y - r * 0.95, hi = y + r * 0.35;
      for (const s of stops) {
        if (Math.abs(x - s.x) > H * 2.3 + r) continue;
        if (hi > s.y + 0.2 * H && lo < s.y + 1.0 * H) return true;
      }
      return false;
    };
    let tries = 0;
    while (placed.length < nIsl && tries++ < 3000) {
      const z = -(11 + Math.pow(rng(), 1.3) * 78);
      // angular-size cap: a nearby island may never fill more than ~45 % of the frame height
      const r = Math.min(range(rng, 3.6, 8) * (1 + -z / 70), 0.12 * (this.rig.baseDistance + -z));
      const x = range(rng, b.minX - 50, b.maxX + 50), y = range(rng, b.minY - 40, b.maxY + 24) - -z * 0.05;
      const overPlay = x > b.minX - 12 - r && x < b.maxX + 12 + r && y > b.minY - r && y < b.maxY + 14 + r;
      if (overPlay && z > -34) continue;
      if (blocksSky(x, y, z, r)) continue;
      if (placed.some(p => Math.hypot(p.x - x, p.y - y, (p.z - z) * 0.5) < p.r + r + 3)) continue;
      placed.push({ x, y, z, r });
      const g = buildIsland(rng, theme, r, r * range(rng, 0.6, 0.9), Math.floor(rng() * 9999), { trees: Math.round(r * range(rng, 0.5, 1.0)), detail: z > -30 ? 1 : 0 });
      const m = mesh(g, mats.smooth, false, false);
      m.name = 'island'; m.position.set(x, y, z);
      this.levelGroup.add(m);
    }
    for (let i = 0, guard = 0; i < nPil && guard++ < 200; i++) {
      const z = -range(rng, 48, 105), r = Math.min(range(rng, 5, 10) * (1 + -z / 60), 0.1 * (this.rig.baseDistance + -z));
      const x = range(rng, b.minX - 60, b.maxX + 60), top = range(rng, b.minY - 14, (b.minY + b.maxY) / 2 + 4);
      if (blocksSky(x, top - 2, z, 2.5)) { i--; continue; }
      const h = top + 70;
      const m = mesh(buildPillar(rng, theme, r, h, Math.floor(rng() * 9999)), mats.smooth, false, false);
      m.name = 'pillar'; m.position.set(x, top, z);
      this.levelGroup.add(m);
    }
  }

  private buildFrameFoliage(): void {
    const { theme } = this;
    const rng = mulberry32(31);
    const palette = theme.worldId === 'world_1' ? theme.foliage : theme.worldId === 'world_2' ? ['#26584a', '#2f6a58', '#f4f8ff'] : theme.worldId === 'world_3' ? ['#7a4a3a', '#a8663e', '#5a3a30'] : ['#241820', '#3a2430', '#5a2a2a'];
    const mat = this.mats.leaf;
    const add = (cx: number, cy: number, r: number, c: string, k: number) => {
      const g = blobGeometry(r, 2, 0.3, (a, b, d) => noise3(a + k, b, d, 3));
      const cc = col(c);
      paint(g, (_x, y) => cc.clone().multiplyScalar(0.55 + 0.45 * Math.min(1, Math.max(0, (y / r + 1) / 2))), true);
      const m = new THREE.Mesh(g, mat); m.position.set(cx, cy, 0); m.userData.base = [cx, cy]; m.userData.k = k; m.frustumCulled = false;
      this.frameFoliage.add(m);
    };
    for (let i = 0; i < 9; i++) add(0, 0, range(rng, 0.55, 1.1), palette[i % palette.length], i);
  }

  private layoutFrameFoliage(): void {
    if (!this.frameFoliage) return;
    const aspect = this.rig.camera.aspect;
    const d = 7;
    const halfH = d * Math.tan((this.rig.baseFov * Math.PI) / 360), halfW = halfH * aspect;
    this.frameFoliage.position.set(0, 0, -d);
    const slots: [number, number, number][] = [
      [-0.97, 0.94, 1.0], [-0.78, 1.04, 0.8], [-1.04, 0.62, 0.75],      // top-left cluster (as in the reference)
      [0.98, 1.0, 0.85], [0.8, 1.06, 0.7],                               // top-right
      [-1.0, -1.02, 0.8], [-0.82, -1.1, 0.6],                            // bottom-left
      [1.02, -0.98, 0.7], [0.86, -1.08, 0.55],                           // bottom-right
    ];
    this.frameFoliage.children.forEach((c, i) => {
      const s = slots[i % slots.length];
      c.userData.base = [s[0] * halfW, s[1] * halfH];
      c.scale.setScalar(s[2]);
      c.position.set(s[0] * halfW, s[1] * halfH, -i * 0.05);
    });
    this.frameFoliage.visible = this.quality !== 'simplified';
  }

  // ────────────────────────────────────────────────────────── frame ──────
  render(f: RenderFrame): void {
    const dt = Math.max(0, Math.min(0.1, f.dt));
    this.time += dt;
    const s = f.state, pose = f.pose, cfg = this.cfg;
    // fps meter (smoothed)
    this.fpsAcc += dt; this.fpsN++;
    if (this.fpsAcc >= 0.5) { this.fps = this.fpsN / this.fpsAcc; this.fpsAcc = 0; this.fpsN = 0; }

    // events → feedback
    for (const e of f.events) this.onEvent(e);

    // character
    const ch = this.character;
    ch.root.position.set(pose.x, pose.y, 0);
    ch.root.rotation.z = -pose.angle;
    const charge01 = s.mode === 'CHARGING' ? Math.max(launchPower(s, cfg, f.pull), s.charge / cfg.chargeTicksMax) : 0;
    ch.update(dt, { mode: s.mode, charge01: Math.min(1, charge01), vx: s.vx, vy: s.vy, omega: s.omega, boosting: this.boostFxT > 0, tilt: f.tilt, finished: s.mode === 'FINISHED' });
    if (this.boostFxT > 0) { this.boostFxT -= dt; this.vfx.trail(pose.x, pose.y, s.vx, s.vy, this.boostTint); }
    if (s.mode === 'CHARGING') { this.chargeFxT -= dt; if (this.chargeFxT <= 0 && charge01 > 0.1) { this.chargeFxT = 0.07 - charge01 * 0.04; this.vfx.burst('charge', pose.footX, pose.footY, 0, 1, charge01); } }

    // contact shadow
    const sb = { y: 0, nx: 0, ny: 1, found: false };
    if (surfaceBelow(this.world, pose.footX, pose.footY + 0.1, s.tick - 1 + f.alpha, sb)) {
      const hgt = Math.max(0, pose.footY - sb.y);
      const k = Math.max(0, 1 - hgt / 9);
      this.shadowBlob.visible = k > 0.02;
      this.shadowBlob.position.set(pose.footX, sb.y + 0.05, 0.3);
      this.shadowBlob.scale.set(0.9 + hgt * 0.06, 1, 0.9 + hgt * 0.06);
      (this.shadowBlob.material as THREE.MeshBasicMaterial).opacity = 0.9 * k;
    } else this.shadowBlob.visible = false;

    // moving platforms + chains
    const t = s.tick - 1 + f.alpha;
    const off = { x: 0, y: 0, vx: 0, vy: 0 };
    for (const m of this.movers) {
      this.world.offsetAt(this.world.colliders[m.colliderIndex], t, off);
      m.obj.position.set(m.bx + off.x, m.by + off.y, 0);
      if (m.chains && m.anchors) {
        const half = (this.level.movingObjects.find(p => this.world.colliders[m.colliderIndex].id === p.id)?.w ?? 6) / 2 - 0.35;
        m.chains.forEach((c, i) => {
          const a = m.anchors![i], bx = m.bx + off.x + (i ? half : -half), by = m.by + off.y + 0.05;
          const dx = bx - a.x, dy = by - a.y, len = Math.hypot(dx, dy);
          c.position.set((a.x + bx) / 2, (a.y + by) / 2, 0); c.scale.set(1, len, 1); c.rotation.z = Math.atan2(-dx, dy);
        });
      }
    }
    for (const [, p] of this.bouncePads) { p.comp *= Math.exp(-9 * dt); p.view.setCompression(p.comp); }

    // camera
    this.rig.update(dt, {
      x: pose.x, y: pose.y, vx: s.vx, vy: s.vy, grounded: s.mode === 'GROUNDED' || s.mode === 'CHARGING', charging: s.mode === 'CHARGING',
      charge01, boosting: this.boostFxT > 0,
    }, this.rig.camera.aspect);
    const cam = this.rig.camera;
    this.sky.update(cam.position);
    if (this.mountains) { const fx = this.rig.focus; for (const m of this.mountains.children) { const f = m.userData.follow as number; m.position.x = m.userData.cx + (fx.x - m.userData.cx) * f; m.position.y = fx.y * f; } }
    // frame foliage sway + parallax
    this.frameFoliage.children.forEach(c => {
      const k = c.userData.k as number, b = c.userData.base as [number, number];
      c.position.x = b[0] + Math.sin(this.time * 0.6 + k) * 0.05 - s.vx * 0.004;
      c.position.y = b[1] + Math.cos(this.time * 0.5 + k * 1.7) * 0.04 - s.vy * 0.003;
    });
    // sun follows the player (snapped to the shadow texel grid to avoid shimmering)
    const sd = new THREE.Vector3(...this.theme.sky.sunDir).normalize();
    const grid = 34 / QUALITY[this.quality].shadowMap;
    const fx = Math.round(pose.x / (grid * 8)) * grid * 8, fy = Math.round(pose.y / (grid * 8)) * grid * 8;
    this.sun.target.position.set(fx, fy, 0);
    this.sun.position.set(fx + sd.x * 40, fy + sd.y * 40, sd.z * 40);

    // ambient motion
    this.clouds.update(dt);
    for (const w of this.waterfalls) w.update(this.time);
    this.goal.update(this.time);
    const gl = 0.5 + Math.sin(this.time * 3) * 0.1;
    for (const h of this.hazardGlows) h.material.opacity = gl;
    if (this.theme.ambience.id !== 'ruins') {
      const kind = this.theme.ambience.id === 'snow' ? 'snow' : this.theme.ambience.id === 'lava' ? 'ember' : 'leaf';
      this.vfx.ambient(this.rig.focus.x, this.rig.focus.y, this.rig.halfW * 1.1, this.rig.halfH, kind, kind === 'leaf' ? this.theme.foliage : ['#ffffff']);
    }
    if (s.vy < -17) this.vfx.speedLines(this.rig.focus.x, this.rig.focus.y, this.rig.halfW, this.rig.halfH, s.vy);
    this.vfx.update(dt);

    this.renderer.render(this.scene, cam);
  }

  private boostFxT = 0;

  private onEvent(e: SimEvent): void {
    if (!this.screenShake && (e.type === 'hard_impact' || e.type === 'wall_hit')) { /* shake toggled off: skip trauma below */ }
    const tint = DUST[e.material ?? 'grass'] ?? '#d8d0a0';
    switch (e.type) {
      case 'launch': this.character.triggerLaunch(e.intensity); this.vfx.burst('dust', e.x, e.y, e.nx === 0 ? 0 : 0, 1, 0.4 + e.intensity * 0.6, tint); break;
      case 'land': {
        this.character.triggerLand(e.intensity);
        this.rig.landKick(e.intensity);
        const kind = e.surface === 'slippery' ? 'ice' : e.surface === 'sticky' ? 'goo' : 'dust';
        this.vfx.burst(kind, e.x, e.y, e.nx, e.ny, e.intensity, tint);
        if (e.intensity > 0.45) { this.vfx.burst('debris', e.x, e.y, e.nx, e.ny, e.intensity, tint); this.vfx.burst('ring', e.x, e.y, e.nx, e.ny, e.intensity, '#ffffff', '#fff3d0'); }
        break;
      }
      case 'hard_impact': this.rig.addTrauma(0.55); break;
      case 'bounce': {
        this.character.triggerLand(0.8); this.rig.landKick(0.7);
        this.vfx.burst('ring', e.x, e.y, e.nx, e.ny, 1, '#ffe08a', '#ffffff'); this.vfx.burst('sparkle', e.x, e.y, e.nx, e.ny, 0.8, '#ffe27a', '#ffffff');
        if (e.collider !== undefined) { const p = this.bouncePads.get(e.collider); if (p) p.comp = 1; }
        break;
      }
      case 'wall_hit': this.rig.addTrauma(0.1 + e.intensity * 0.3); this.vfx.burst('dust', e.x, e.y, e.nx, e.ny, e.intensity * 0.6, tint); break;
      case 'boost_armed': this.vfx.burst('sparkle', e.x, e.y, 0, 1, 0.6, '#ffd24a', '#ffffff'); break;
      case 'boost': this.boostFxT = 0.9; this.rig.boostKick(); this.vfx.burst('ring', e.x, e.y, 0, 1, 1, '#ffb347', '#ffffff'); break;
      case 'boost_pad': this.vfx.burst('sparkle', e.x, e.y, e.nx, e.ny, 0.7, '#ffcf3a', '#ffffff'); break;
      case 'hazard': this.vfx.burst('hazard', e.x, e.y, e.nx, e.ny, 1); this.rig.addTrauma(0.6); break;
      case 'fall': this.rig.addTrauma(0.3); break;
      case 'respawn': this.rig.snap(e.x, e.y); this.vfx.burst('sparkle', e.x, e.y - 1, 0, 1, 0.6, '#ffffff', '#cfe9ff'); break;
      case 'goal': this.vfx.burst('confetti', e.x, e.y, 0, 1, 1); this.rig.addTrauma(0.2); this.character.triggerEmote('cheer'); break;
      default: break;
    }
  }

  snapCamera(x: number, y: number): void { this.rig.snap(x, y); }

  /** Project a world point to CSS pixels (for HUD anchors). */
  project(x: number, y: number, z = 0): { x: number; y: number; visible: boolean } {
    const v = this.shakeTmp.set(x, y, z).project(this.rig.camera);
    return { x: (v.x * 0.5 + 0.5) * this.lastW, y: (-v.y * 0.5 + 0.5) * this.lastH, visible: v.z < 1 && Math.abs(v.x) < 1.2 && Math.abs(v.y) < 1.2 };
  }

  get stats(): { calls: number; triangles: number; geometries: number; textures: number } {
    const i = this.renderer.info;
    return { calls: i.render.calls, triangles: i.render.triangles, geometries: i.memory.geometries, textures: i.memory.textures };
  }

  dispose(): void {
    this.disposeLevel();
    this.renderer.dispose();
  }
}
