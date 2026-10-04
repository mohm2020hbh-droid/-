import { LEVELS, Progression } from './progression/Progression';
import { SaveSystem } from './progression/SaveSystem';
import type { Settings } from './progression/settings';
import { Game } from './game/Game';
import { Feedback } from './game/Feedback';
import { AudioManager } from './audio/AudioManager';
import { SFXManager } from './audio/SFXManager';
import { MusicManager } from './audio/MusicManager';
import { AmbientManager } from './audio/AmbientManager';
import { SFX } from './audio/AudioEvents';
import { HapticManager } from './haptics/HapticManager';
import { TouchControls } from './input/TouchControls';
import { Hud } from './ui/Hud';
import { Screens } from './ui/Screens';
import { Lab } from './ui/Lab';
import { setLang, t } from './ui/i18n';
import { CSS } from './ui/styles';
import { WORLD_1, WORLDS, type WorldTheme } from './data/worlds';
import { BOOST_FX_TINT, itemById, type ItemDef } from './data/items';
import { progressFraction, type LevelData } from './data/LevelData';
import { PHYSICS_TEST } from './data/levels/physicsTest';
import { LEVEL_01 } from './data/levels/level01';
import { TrajectoryGuide } from './render/TrajectoryGuide';
import type { CharacterAppearance } from './render/Character';
import type { Quality } from './render/GameRenderer';
import { simulateLaunch } from './sim/prediction';
import { createPhysicsConfig } from './sim/PhysicsConfig';
import { DT } from './sim/math';

type State = 'splash' | 'menu' | 'sub' | 'playing' | 'paused' | 'results' | 'lab';

/**
 * App — the application flow: Splash → Main Menu → Game Mode → World/Level select → Playing ⇄ Pause → Results,
 * plus Options / Wardrobe / Leaderboard / How To Play / Physics Lab. Owns all subsystems and wires them together.
 */
export class App {
  readonly save = new SaveSystem();
  readonly progression = new Progression(this.save);
  readonly audio = new AudioManager();
  readonly sfx = new SFXManager(this.audio);
  readonly music = new MusicManager(this.audio);
  readonly ambient = new AmbientManager(this.audio);
  readonly haptics = new HapticManager();
  readonly game: Game;
  readonly hud: Hud;
  readonly controls: TouchControls;
  readonly screens: Screens;
  readonly feedback: Feedback;
  private readonly guide = new TrajectoryGuide();
  private state: State = 'splash';
  private levelId = 'level_01';
  private lab: Lab | null = null;
  private screenEl: HTMLElement | null = null;
  private maxProgress = 0;
  private whistled = false;
  private resultTimer = 0;
  private lowFpsSince = 0; private highFpsSince = 0;
  private uiTimer = 0;
  private guideFrame = 0;
  private hintShown: string | null = null;
  private finished = false;
  private subStack: (() => void)[] = [];

  constructor(private readonly canvas: HTMLCanvasElement, private readonly ui: HTMLElement, private readonly flags: URLSearchParams) {
    const style = document.createElement('style'); style.textContent = CSS; document.head.append(style);
    const S = this.save.data.settings;
    setLang(S.language);
    this.applySettings(S, 'audio'); this.applySettings(S, 'haptics');
    const q = (flags.get('quality') as Quality) || S.graphics.quality;
    this.game = new Game({ canvas, level: LEVEL_01, quality: q, appearance: this.appearance() });
    this.game.renderer.scene.add(this.guide.group);
    this.hud = new Hud(ui);
    this.hud.show(false);
    this.controls = new TouchControls(canvas, S.control);
    this.controls.attachBoost(this.hud.boostBtn);
    this.controls.attachPad(this.hud.pad);
    this.controls.onTouchStart = () => this.audio.unlock();
    if (flags.get('debug') === '1') this.controls.enableDebugKeyboard();
    this.hud.pauseBtn.addEventListener('click', () => { if (this.state === 'playing') this.pause(); });
    this.feedback = new Feedback(this.sfx, this.haptics, this.hud);
    this.screens = new Screens(ui, {
      save: this.save, progression: this.progression,
      click: () => { this.audio.unlock(); this.sfx.play(SFX.uiClick); this.haptics.trigger('ui'); },
      back: () => this.back(),
      settingsChanged: (s, what) => this.applySettings(s, what),
      equipChanged: it => this.onEquip(it),
      previewWorld: th => this.previewWorld(th),
    });
    this.game.onEvents(ev => { if (this.state === 'playing' || this.state === 'lab') this.feedback.handle(ev); this.onSimEvents(ev); });
    this.game.onFrame = (dt, g) => this.frame(dt, g);
    window.addEventListener('resize', () => this.resize());
    window.addEventListener('orientationchange', () => setTimeout(() => this.resize(), 120));
    this.resize();
    document.addEventListener('visibilitychange', () => { if (document.hidden && this.state === 'playing') this.pause(); });
    // Android host events (MainActivity): focus loss / onPause, and the hardware Back button
    window.addEventListener('pogo-pause', () => { if (this.state === 'playing') this.pause(); this.audio.suspend(); });
    window.addEventListener('focus', () => this.audio.resume());
    (window as unknown as { PogoBack: () => boolean }).PogoBack = () => this.handleBack();
    window.addEventListener('keydown', e => { if (e.code === 'Escape' || e.code === 'KeyP') { if (this.state === 'playing') this.pause(); } });
    this.applyControlSettings();
    this.game.start();
  }

  // ───────────────────────────────────────────────────────────── boot ─────
  boot(): void {
    const f = this.flags;
    if (f.get('world')) { const w = WORLDS.find(x => x.worldId === f.get('world') || x.id === f.get('world')); if (w) this.game.loadLevel(LEVEL_01, w); }
    if (f.get('lab') === '1') { this.audio.unlock(); this.openLab(); return; }
    if (f.get('autostart') === '1') { this.startLevel(f.get('level') ?? 'level_01'); return; }
    this.menuCamera(true);
    this.screenEl = this.screens.splash(() => { this.audio.unlock(); this.sfx.play(SFX.uiConfirm); this.clearScreen(); this.toMenu(); });
  }

  private resize(): void { this.game.renderer.resize(window.innerWidth, window.innerHeight, window.devicePixelRatio || 1); }

  // ────────────────────────────────────────────────────────── settings ────
  private appearance(): CharacterAppearance {
    const e = this.save.data.equipped, p = (id: string, d: string) => itemById(id)?.prefab ?? d;
    return { hat: p(e.hat, 'beanie'), stick: p(e.stick, 'copper'), outfit: p(e.outfit, 'teal'), skin: p(e.skin, 'peach') };
  }

  private onEquip(it: ItemDef): void {
    this.game.renderer.setAppearance(this.appearance());
    if (it.category === 'boostFx') this.game.renderer.boostTint = BOOST_FX_TINT[it.prefab] ?? '#ffb347';
    if (it.category === 'emote') this.game.renderer.character.triggerEmote(it.prefab);
    else this.game.renderer.character.triggerEmote('cheer');
  }

  private applySettings(s: Settings, what: 'audio' | 'control' | 'graphics' | 'language' | 'haptics'): void {
    if (what === 'audio') { this.audio.apply(s.audio); }
    if (what === 'haptics') { this.haptics.enabled = s.haptics.enabled; this.haptics.strength = s.haptics.strength; }
    if (what === 'control') this.applyControlSettings();
    if (what === 'graphics') {
      this.game?.renderer.setQuality(s.graphics.quality);
      this.game.renderEvery = s.graphics.fps60 ? 1 : 2;
      this.game.renderer.screenShake = s.graphics.screenShake;
    }
    if (what === 'language') { setLang(s.language); this.hud.relabel(); this.save.save(); }
  }

  private applyControlSettings(): void {
    const c = this.save.data.settings.control;
    this.controls.setSettings(c);
    this.hud.setScheme(c.scheme, c.leftHanded);
    // The locked physics has a fixed charge rate (E8: 16 L/T); the old "Charge Time" setting no longer affects it.
  }

  // ─────────────────────────────────────────────────────── navigation ─────
  private clearScreen(): void { this.screenEl?.remove(); this.screenEl = null; }
  private setScreen(el: HTMLElement): void { this.clearScreen(); this.screenEl = el; }

  private menuCamera(on: boolean, shift = 0.36): void {
    const r = this.game.renderer.rig;
    r.shiftTarget = on ? r.halfW * shift : 0;
    r.distScaleTarget = on ? 0.62 : 1;
  }

  private back(): void { const f = this.subStack.pop(); if (f) f(); else this.toMenu(); }

  toMenu(): void {
    this.state = 'menu'; this.subStack = [];
    this.hud.show(false); this.game.input = null; this.game.paused = false;
    if (this.game.level.levelId !== LEVEL_01.levelId || this.game.theme.id !== WORLD_1.id) this.game.loadLevel(LEVEL_01);
    this.game.reset(); this.menuCamera(true);
    this.music.start(WORLD_1, 'menu'); this.ambient.stop();
    this.guide.set(null);
    this.setScreen(this.screens.mainMenu({
      version: '0.1.0',
      onPlay: () => this.openMode(), onWardrobe: () => this.openWardrobe(), onLeaderboard: () => this.openSub(() => this.screens.leaderboard({ onClose: () => this.back() })),
      onOptions: () => this.openOptions(() => this.toMenu()), onHow: () => this.openSub(() => this.screens.howTo({ onClose: () => this.back() })),
    }));
  }

  private openSub(make: () => HTMLElement): void { this.state = 'sub'; this.subStack.push(() => this.toMenu()); this.setScreen(make()); }

  private openMode(): void {
    this.state = 'sub'; this.subStack = [() => this.toMenu()];
    this.setScreen(this.screens.gameMode({
      onAdventure: () => this.openSelect(), onLab: () => this.openLab(), onBack: () => this.back(),
    }));
  }

  private openSelect(): void {
    this.subStack = [() => this.openMode()];
    this.menuCamera(true, 0.5);
    this.setScreen(this.screens.levelSelect({ onStart: id => this.startLevel(id), onBack: () => { this.menuCamera(true); this.back(); } }));
  }

  private openOptions(returnTo: () => void): void {
    const prev = this.state;
    this.subStack.push(returnTo);
    this.state = prev === 'paused' ? 'paused' : 'sub';
    this.screenEl?.remove();
    this.screenEl = this.screens.options({ onClose: () => { this.save.save(); this.back(); } });
  }

  private openWardrobe(): void {
    this.state = 'sub'; this.subStack = [() => this.toMenu()];
    this.game.renderer.rig.shiftTarget = -this.game.renderer.rig.halfW * 0.4; this.game.renderer.rig.distScaleTarget = 0.62;
    this.setScreen(this.screens.wardrobe({ onClose: () => { this.menuCamera(true); this.back(); } }));
    this.game.renderer.character.triggerEmote(itemById(this.save.data.equipped.emote)?.prefab ?? 'wave');
  }

  private previewWorld(th: WorldTheme | null): void {
    // Menu-only: show the chosen world's look on the LEVEL_01 geometry ("preview — levels coming soon").
    this.game.loadLevel(LEVEL_01, th ?? WORLD_1);
    this.game.reset(); this.menuCamera(true, 0.5);
    this.music.start(th ?? WORLD_1, 'menu');
    this.game.renderer.rig.distScale = 0.62;
  }

  // ─────────────────────────────────────────────────────────── playing ────
  startLevel(levelId: string): void {
    const entry = LEVELS.find(l => l.id === levelId);
    if (!entry?.data) return;
    this.levelId = levelId;
    const data = entry.data;
    // ?debug=1&world=… previews LEVEL_01 dressed in another world's theme (visual QA of worlds 2–4)
    const preview = this.flags.get('debug') === '1' ? this.flags.get('world') : null;
    const theme = WORLDS.find(w => preview ? (w.worldId === preview || w.id === preview) : w.worldId === entry.worldId) ?? WORLD_1;
    this.clearScreen(); this.state = 'playing'; this.finished = false; this.maxProgress = 0;
    this.game.loadLevel(data, theme);
    this.game.reset();
    this.game.input = this.controls;
    this.game.paused = false;
    this.menuCamera(false);
    this.hud.show(true); this.hud.setScheme(this.save.data.settings.control.scheme, this.save.data.settings.control.leftHanded);
    this.applyControlSettings();
    this.progression.attempt(levelId);
    this.music.start(theme, 'game'); this.ambient.start(theme);
    this.game.renderer.boostTint = BOOST_FX_TINT[itemById(this.save.data.equipped.boostFx)?.prefab ?? 'sparks'] ?? '#ffb347';
  }

  pause(): void {
    if (this.state !== 'playing') return;
    this.state = 'paused'; this.game.paused = true; this.controls.cancel(); this.sfx.chargeUpdate(0, false);
    this.setScreen(this.screens.pause({
      onResume: () => this.resume(), onRestart: () => { this.clearScreen(); this.restart(); },
      onOptions: () => { this.subStack = [() => { this.state = 'paused'; this.setScreen(this.pauseScreen()); }]; this.openOptions(() => { this.state = 'paused'; this.setScreen(this.pauseScreen()); }); },
      onMenu: () => { this.abandon(); this.toMenu(); },
    }));
  }

  private pauseScreen(): HTMLElement {
    return this.screens.pause({ onResume: () => this.resume(), onRestart: () => { this.clearScreen(); this.restart(); }, onOptions: () => this.openOptions(() => { this.state = 'paused'; this.setScreen(this.pauseScreen()); }), onMenu: () => { this.abandon(); this.toMenu(); } });
  }

  resume(): void { if (this.state !== 'paused') return; this.clearScreen(); this.state = 'playing'; this.game.paused = false; }

  restart(): void {
    this.abandon();
    this.state = 'playing'; this.finished = false; this.maxProgress = 0;
    this.game.reset(); this.game.paused = false; this.progression.attempt(this.levelId); this.hud.show(true);
  }

  /** Leaving a run without finishing still counts its stats. */
  private abandon(): void {
    const s = this.game.pogo.state;
    if (!this.finished && s.jumps > 0) this.progression.finish({ levelId: this.levelId, timeSec: this.game.runSeconds, jumps: s.jumps, boosts: s.boosts, falls: s.falls, completed: false, progress: this.maxProgress }, this.game.level);
    this.finished = true;
  }

  private onSimEvents(ev: readonly import('./sim/events').SimEvent[]): void {
    if (this.state !== 'playing') return;
    for (const e of ev) {
      if (e.type === 'goal' && !this.finished) {
        this.finished = true;
        const s = this.game.pogo.state;
        const res = { levelId: this.levelId, timeSec: this.game.runSeconds, jumps: s.jumps, boosts: s.boosts, falls: s.falls, completed: true, progress: 1 };
        const out = this.progression.finish(res, this.game.level);
        window.clearTimeout(this.resultTimer);
        this.resultTimer = window.setTimeout(() => this.showResults(res, out), 1600);
      }
    }
  }

  private showResults(res: { timeSec: number; jumps: number; boosts: number; falls: number }, out: { newBest: boolean; stars: number; newItems: ItemDef[]; rank: number }): void {
    if (this.state !== 'playing') return;
    this.state = 'results'; this.hud.show(false); this.sfx.chargeUpdate(0, false);
    const idx = LEVELS.findIndex(l => l.id === this.levelId);
    const nextOk = !!LEVELS[idx + 1]?.data && this.progression.isLevelPlayable(LEVELS[idx + 1].id);
    for (let i = 0; i < out.stars; i++) window.setTimeout(() => this.sfx.play(SFX.uiStar, { pitch: 1 + i * 0.12 }), 300 + i * 260);
    if (out.newItems.length) window.setTimeout(() => this.sfx.play(SFX.uiUnlock), 1200);
    this.setScreen(this.screens.results({
      ...res, stars: out.stars, newBest: out.newBest, best: this.progression.record(this.levelId).bestTimeSec, rank: out.rank, newItems: out.newItems, hasNext: nextOk,
      onNext: () => this.startLevel(LEVELS[idx + 1].id), onRetry: () => { this.clearScreen(); this.restart(); }, onMenu: () => this.toMenu(),
    }));
  }

  // ───────────────────────────────────────────────────────────── lab ──────
  openLab(): void {
    this.clearScreen(); this.state = 'lab'; this.subStack = [];
    this.game.loadLevel(PHYSICS_TEST);
    Object.assign(this.game.cfg, createPhysicsConfig());
    this.game.reset(); this.menuCamera(false);
    this.hud.show(true); this.hud.setScheme(this.save.data.settings.control.scheme, this.save.data.settings.control.leftHanded);
    this.lab?.dispose();
    this.lab = new Lab(this.ui, this.game, this.controls, () => { this.lab?.dispose(); this.lab = null; Object.assign(this.game.cfg, createPhysicsConfig()); this.applyControlSettings(); this.toMenu(); });
    this.game.input = this.lab;
    this.game.paused = false;
    this.music.stop(0.5); this.ambient.stop();
  }

  // ───────────────────────────────────────────────────────── per frame ────
  private frame(dt: number, g: Game): void {
    const s = g.pogo.state, cfg = g.cfg, R = g.renderer;
    // dynamic resolution (Phase 17): back off when the device cannot hold the frame rate, recover when it can
    const now = performance.now();
    if (this.state === 'playing' || this.state === 'lab') {
      if (R.fps < 38) { this.highFpsSince = now; if (!this.lowFpsSince) this.lowFpsSince = now; if (now - this.lowFpsSince > 2500) { R.setResScale(R.resScale - 0.1); this.lowFpsSince = now; } }
      else if (R.fps > 57) { this.lowFpsSince = 0; if (!this.highFpsSince) this.highFpsSince = now; if (now - this.highFpsSince > 6000) { R.setResScale(R.resScale + 0.1); this.highFpsSince = now; } }
      else { this.lowFpsSince = 0; this.highFpsSince = now; }
    }
    this.lab?.update();
    if (this.state !== 'playing' && this.state !== 'lab') { this.sfx.chargeUpdate(0, false); this.guide.set(null); this.hud.setCharge(null, 0); return; }

    // HUD (DOM touched only when something changes)
    const lvl = g.level;
    const prog = progressFraction(lvl.progress.path, s.x, s.y);
    if (prog > this.maxProgress) this.maxProgress = prog;
    this.hud.update({ seconds: g.runSeconds, height: s.y - lvl.startPosition.y, jumps: s.jumps, boosts: s.boosts, progress: this.maxProgress, boostReady: s.boostReady, boostQueued: false });

    // charge feedback: ring, whine loop, trajectory guide
    // the pogo charges on every landing by itself; the ring/whine/guide only show while the player is HOLDING the charge
    const charging = s.mode === 'CHARGING' && s.held;
    const power = charging ? s.charge01 : 0;
    this.sfx.chargeUpdate(power, charging && !g.paused);
    if (charging) {
      const p = R.project(g.pogo.pose(1).footX, g.pogo.pose(1).footY - 0.2);
      this.hud.setCharge({ x: p.x, y: p.y }, power);
    } else this.hud.setCharge(null, 0);
    const guideMode = this.save.data.settings.control.guide;
    if (charging && guideMode !== 'off' && (this.guideFrame++ & 1) === 0) {
      const pr = simulateLaunch(g.pogo.ctx, s, { maxTicks: guideMode === 'short' ? 70 : 260, stride: guideMode === 'short' ? 4 : 6 });
      this.guide.set(pr.points);
    } else if (!charging) { this.guide.set(null); this.guideFrame = 0; }

    // hints (position triggered)
    let hint: string | null = null;
    for (const hn of lvl.hints) if (Math.hypot(s.x - hn.x, s.y - hn.y) < hn.radius) { hint = t(hn.textKey); break; }
    if (hint !== this.hintShown) { this.hud.setHint(hint); this.hintShown = hint; }

    // audio: ambient follows height & waterfalls, music follows speed, fall whistle
    if (((now / 100) | 0) !== this.uiTimer) {
      this.uiTimer = (now / 100) | 0;
      const h01 = Math.max(0, Math.min(1, (s.y - lvl.bounds.minY) / (lvl.bounds.maxY - lvl.bounds.minY)));
      let water = 0;
      for (const l of lvl.landmarks) if (l.type === 'waterfall') water = Math.max(water, 1 - Math.hypot(s.x - l.x, (s.y - l.y + 20) * 0.5) / 55);
      this.ambient.update(h01, Math.max(0, water));
      this.music.setIntensity(0.2 + Math.min(1, Math.hypot(s.vx, s.vy) / 20) * 0.7);
    }
    if (s.vy < -21 && !this.whistled && s.mode === 'AIR') { this.whistled = true; this.sfx.play(SFX.fall); }
    if (s.vy > -6) this.whistled = false;
    void dt; void DT;
  }

  /** Hardware Back: close the topmost thing first; returns false only when the app should exit (main menu / splash). */
  handleBack(): boolean {
    switch (this.state) {
      case 'playing': this.pause(); return true;
      case 'paused': this.resume(); return true;
      case 'results': this.toMenu(); return true;
      case 'lab': this.lab?.dispose(); this.lab = null; Object.assign(this.game.cfg, createPhysicsConfig()); this.applyControlSettings(); this.toMenu(); return true;
      case 'sub': this.sfx.play(SFX.uiBack); this.back(); return true;
      default: return false;
    }
  }

  /** QA/automation snapshot. */
  snapshot(): Record<string, unknown> {
    const s = this.game.pogo.state;
    return { state: this.state, mode: s.mode, x: s.x, y: s.y, vx: s.vx, vy: s.vy, jumps: s.jumps, boosts: s.boosts, falls: s.falls, progress: this.maxProgress, finished: this.finished, charge: s.load, fps: this.game.renderer.fps, level: this.levelId };
  }
}

export type { LevelData };
