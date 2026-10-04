import type { LevelData } from '../data/LevelData';
import { type WorldTheme, getTheme } from '../data/worlds';
import { PhysicsWorld } from '../sim/PhysicsWorld';
import { PogoPhysicsController } from '../sim/PogoPhysicsController';
import { type PhysicsConfig, createPhysicsConfig } from '../sim/PhysicsConfig';
import { DT } from '../sim/math';
import type { SimEvent } from '../sim/events';
import { GameRenderer, type Quality } from '../render/GameRenderer';
import type { CharacterAppearance } from '../render/Character';
import type { InputSource } from '../input/InputSource';
import { NEUTRAL_INPUT, type PogoInput } from '../sim/PogoState';
import type { MapDocument } from '../map/schema';
import { MapRuntime } from '../map/MapRuntime';
import { compileRenderLevel, worldThemeOf } from '../map/MapCompile';
import type { MapEvent } from '../map/schema';

export interface GameOptions {
  canvas: HTMLCanvasElement;
  level: LevelData;
  quality: Quality;
  appearance?: CharacterAppearance;
  cfg?: Partial<PhysicsConfig>;
}

export type EventListener = (events: readonly SimEvent[], game: Game) => void;
export type MapEventListener = (events: readonly MapEvent[], game: Game) => void;

/**
 * Game — owns the fixed-timestep loop (120 Hz sim, interpolated render) and wires the simulation to the presentation
 * layers. Presentation modules subscribe through `onEvents` / read `state`; nothing here is physics.
 */
export class Game {
  readonly renderer: GameRenderer;
  world!: PhysicsWorld;
  pogo!: PogoPhysicsController;
  cfg: PhysicsConfig;
  level!: LevelData;
  theme!: WorldTheme;
  /** Set while a Map System V2 map is loaded (see `loadMap`). */
  mapRuntime: MapRuntime | null = null;
  private mapListeners: MapEventListener[] = [];
  input: InputSource | null = null;
  paused = false;
  running = false;
  timeScale = 1;
  /** Render every Nth display frame (2 = 30 fps cap, saves battery). */
  renderEvery = 1;
  private frameCount = 0;
  private acc = 0;
  private last = 0;
  private raf = 0;
  private listeners: EventListener[] = [];
  private lastInput: PogoInput = NEUTRAL_INPUT;
  private frameEvents: SimEvent[] = [];
  /** Events waiting for the next *rendered* frame (matters when renderEvery > 1). */
  private renderEvents: SimEvent[] = [];
  /** Wall-clock seconds spent running (for the Lab). */
  elapsed = 0;
  onFrame?: (dt: number, game: Game) => void;

  constructor(readonly opts: GameOptions) {
    this.cfg = createPhysicsConfig(opts.cfg);
    this.renderer = new GameRenderer(opts.canvas, opts.quality);
    if (opts.appearance) this.renderer.setAppearance(opts.appearance);
    this.loadLevel(opts.level);
  }

  /**
   * Load a Map System V2 map: the runtime owns the physics world (`MapWorld`), the existing renderer gets the compiled
   * LevelData (best-effort visual subset) and the theme resolved from the map.
   */
  loadMap(doc: MapDocument, themeOverride?: WorldTheme): MapRuntime {
    const runtime = new MapRuntime(doc, { cfg: this.cfg });
    const level = compileRenderLevel(doc, runtime.registry);
    this.mapRuntime = runtime;
    this.level = level;
    this.theme = themeOverride ?? worldThemeOf(doc);
    this.world = runtime.world;
    this.pogo = new PogoPhysicsController(this.world, this.cfg);
    this.renderer.loadLevel(level, this.theme, this.world, this.cfg);
    this.resetCamera();
    this.acc = 0;
    return runtime;
  }

  onMapEvents(l: MapEventListener): () => void { this.mapListeners.push(l); return () => { this.mapListeners = this.mapListeners.filter(x => x !== l); }; }

  loadLevel(level: LevelData, themeOverride?: WorldTheme): void {
    this.mapRuntime = null;
    this.level = level;
    this.theme = themeOverride ?? getTheme(level.theme);
    this.world = new PhysicsWorld(level);
    this.pogo = new PogoPhysicsController(this.world, this.cfg);
    this.renderer.loadLevel(level, this.theme, this.world, this.cfg);
    this.resetCamera();
    this.acc = 0;
  }

  resetCamera(): void { this.renderer.snapCamera(this.pogo.state.x, this.pogo.state.y); }

  reset(): void {
    this.pogo.reset();
    this.mapRuntime?.resetRun();
    if (this.mapRuntime) { this.mapRuntime.beforeStep(this.pogo.state); }
    this.acc = 0;
    this.resetCamera();
  }

  onEvents(l: EventListener): () => void { this.listeners.push(l); return () => { this.listeners = this.listeners.filter(x => x !== l); }; }

  start(): void {
    if (this.running) return;
    this.running = true;
    this.last = performance.now();
    const loop = (t: number) => {
      if (!this.running) return;
      this.raf = requestAnimationFrame(loop);
      const dt = Math.max(0, Math.min(0.1, (t - this.last) / 1000)); // rAF stamps can precede performance.now(): never allow negative dt
      this.last = t;
      this.tick(dt);
    };
    this.raf = requestAnimationFrame(loop);
  }

  stop(): void { this.running = false; cancelAnimationFrame(this.raf); }

  /** One display frame: run as many fixed ticks as the accumulated time allows, then render. */
  tick(dt: number): void {
    this.elapsed += dt;
    this.frameEvents.length = 0;
    if (!this.paused) {
      this.acc += dt * this.timeScale;
      let guard = 0;
      while (this.acc >= DT && guard++ < 12) {
        const inp = this.input ? this.input.sample() : NEUTRAL_INPUT;
        this.lastInput = inp;
        const rt = this.mapRuntime;
        if (rt) rt.beforeStep(this.pogo.state);
        const ev = this.pogo.step(inp);
        for (const e of ev) this.frameEvents.push(e);
        if (rt) {
          const me = rt.afterStep(this.pogo.state, ev);
          const st = this.pogo.state;
          if (st.teleportTick === st.tick) { this.pogo.prev.x = st.x; this.pogo.prev.y = st.y; this.pogo.prev.angle = st.angle; }
          if (me.length && this.mapListeners.length) for (const l of this.mapListeners) l(me, this);
        }
        this.acc -= DT;
      }
      if (guard >= 12) this.acc = 0; // spiral-of-death guard
    }
    if (this.frameEvents.length) { for (const l of this.listeners) l(this.frameEvents, this); this.renderEvents.push(...this.frameEvents); }
    const alpha = this.paused ? 1 : Math.min(1, this.acc / DT);
    if (++this.frameCount % this.renderEvery !== 0) { this.onFrame?.(dt, this); return; }
    this.renderer.render({
      dt: this.paused ? 0 : dt * this.renderEvery, alpha, pose: this.pogo.pose(alpha), state: this.pogo.state, events: this.renderEvents,
      tilt: this.input?.tilt ?? 0, pull: this.lastInput.pull,
    });
    this.renderEvents.length = 0;
    this.onFrame?.(dt, this);
  }

  /** Simulated seconds since the first launch (the run timer). */
  get runSeconds(): number {
    const s = this.pogo.state;
    if (s.startedTick < 0) return 0;
    const end = s.finishedTick >= 0 ? s.finishedTick : s.tick;
    return (end - s.startedTick) * DT;
  }

  dispose(): void { this.stop(); this.input?.dispose(); this.renderer.dispose(); }
}
