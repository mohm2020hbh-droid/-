import { h } from './dom';
import type { Game } from '../game/Game';
import { PARAM_DEFS, type ParamKey, REFERENCE_JUMP_INTERVAL_TICKS, createPhysicsConfig } from '../sim/PhysicsConfig';
import type { InputSource } from '../input/InputSource';
import { NEUTRAL_INPUT, type PogoInput } from '../sim/PogoState';
import { DEG, TICK_RATE } from '../sim/math';
import { launchPower } from '../sim/JumpSystem';

/**
 * Physics Lab (Phase 18) — measure and tune the implementation against Pogostuck_Physics_Master.xlsx:
 * live readouts, sliders for every TUNE_ME parameter, scripted tests (Jump / Bounce / Boost / Fall) and the
 * jump-cadence meter that compares our rhythm with the 150-tick video reference (XLSX V-026, grade A derived).
 */
type Script = { frames: PogoInput[]; i: number; onEnd?: () => void; until?: (g: Game) => boolean };

export class Lab implements InputSource {
  readonly root: HTMLElement;
  private readout: HTMLElement;
  private script: Script | null = null;
  private pending: PogoInput[] = [];
  private jumpTicks: number[] = [];
  private lastJumps = 0;
  private lastText = 0;
  private lastNormal = '—';
  private manual: InputSource;
  private angleDeg = 35;
  private power = 0.6;
  private unsub: (() => void) | null = null;
  tilt = 0;

  constructor(parent: HTMLElement, private readonly game: Game, manual: InputSource, private readonly onExit: () => void) {
    this.manual = manual;
    this.readout = h('div.lab-readout.panel');
    let panel!: HTMLElement;
    const bar = h('div.lab-bar', null,
      this.b('Reset', () => this.game.reset()),
      this.b('Test Jump', () => this.testJump()),
      this.b('Test Bounce', () => this.testBounce()),
      this.b('Test Boost', () => this.testBoost()),
      this.b('Test Fall', () => this.testFall()),
      this.b('Params', () => { panel.style.display = panel.style.display === 'none' ? '' : 'none'; }),
      this.b('Exit', () => this.onExit()),
    );
    panel = h('div.lab-panel.panel');
    panel.style.display = 'none';
    panel.append(h('h4', null, 'Test jump'), this.slider('Angle', this.angleDeg, -65, 65, 1, v => { this.angleDeg = v; }, '°'), this.slider('Power', this.power * 100, 0, 100, 1, v => { this.power = v / 100; }, '%'));
    const groups: Record<string, ParamKey[]> = {};
    for (const k of Object.keys(PARAM_DEFS) as ParamKey[]) (groups[PARAM_DEFS[k].group] ??= []).push(k);
    for (const [g, keys] of Object.entries(groups)) {
      panel.append(h('h4', null, g));
      for (const k of keys) {
        const d = PARAM_DEFS[k];
        const tag = d.status === 'TUNE_ME' ? h('span.tag', null, 'TUNE_ME') : d.status === 'SOURCE_A' ? h('span.tag.src', null, 'A') : h('span.tag.des', null, d.status === 'DESIGN' ? 'design' : d.status);
        if (d.status === 'SOURCE_A') { panel.append(h('div.p', null, h('span.n', null, d.label, tag), h('span', null, `${+d.value.toFixed(5)} ${d.unit}`))); continue; }
        panel.append(this.paramSlider(k, tag));
      }
    }
    panel.append(h('div.btn', { style: { marginTop: '.6rem', justifyContent: 'center' }, onclick: () => this.resetParams() }, 'Reset all parameters'));
    this.root = h('div', { style: { position: 'absolute', inset: '0', pointerEvents: 'none' } }, panel, this.readout, bar);
    parent.append(this.root);
    this.unsub = game.onEvents(ev => { for (const e of ev) { if (e.type === 'land' || e.type === 'wall_hit' || e.type === 'bounce') this.lastNormal = `${e.type} n=(${e.nx.toFixed(2)}, ${e.ny.toFixed(2)}) ${e.surface ?? ''}`; } });
  }

  private b(label: string, fn: () => void): HTMLElement { return h('button.btn', { onclick: fn }, label); }

  private slider(label: string, value: number, min: number, max: number, step: number, set: (v: number) => void, unit: string): HTMLElement {
    const val = h('span', null, `${value}${unit}`);
    const inp = h('input', { type: 'range', min, max, step, value }) as HTMLInputElement;
    inp.addEventListener('input', () => { set(+inp.value); val.textContent = `${inp.value}${unit}`; });
    return h('div.p', null, h('span.n', null, label), val, h('div', { style: { gridColumn: '1 / -1' } }, inp));
  }

  private paramSlider(k: ParamKey, tag: HTMLElement): HTMLElement {
    const d = PARAM_DEFS[k];
    const cfg = this.game.cfg as unknown as Record<string, number>;
    const val = h('span', null, `${+cfg[k].toFixed(4)} ${d.unit}`);
    const inp = h('input', { type: 'range', min: d.min, max: d.max, step: d.step, value: cfg[k] }) as HTMLInputElement;
    inp.dataset.key = k;
    inp.addEventListener('input', () => { cfg[k] = +inp.value; val.textContent = `${+(+inp.value).toFixed(4)} ${d.unit}`; });
    const row = h('div.p', null, h('span.n', null, d.label, tag), val, h('div', { style: { gridColumn: '1 / -1' } }, inp));
    row.title = `${d.note}  [${d.ref}]`;
    return row;
  }

  private resetParams(): void {
    const def = createPhysicsConfig();
    Object.assign(this.game.cfg, def);
    this.root.querySelectorAll<HTMLInputElement>('input[data-key]').forEach(i => { i.value = String((def as unknown as Record<string, number>)[i.dataset.key!]); i.dispatchEvent(new Event('input')); });
  }

  // ── scripted tests (they drive the REAL input path, so they test what the player experiences) ──
  private run(frames: PogoInput[], opts: Partial<Script> = {}): void { this.script = { frames, i: 0, ...opts }; }
  private rep(n: number, p: Partial<PogoInput>): PogoInput[] { return Array.from({ length: n }, () => ({ ...NEUTRAL_INPUT, ...p })); }

  testJump(): void {
    if (this.game.pogo.state.mode !== 'GROUNDED') this.game.reset();
    const tilt = Math.max(-1, Math.min(1, this.angleDeg / this.game.cfg.tiltMaxAngle));
    this.run([...this.rep(70, { tilt }), ...this.rep(3, { tilt, jumpHeld: true, pull: this.power }), ...this.rep(1, { tilt, jumpHeld: false, pull: this.power })]);
  }

  private teleportAir(x: number, y: number, vx = 0, vy = 0): void {
    const s = this.game.pogo.state;
    Object.assign(s, { x, y, vx, vy, mode: 'AIR', groundId: -1, angle: 0, omega: 0, charge: 0, slideV: 0, teleportTick: s.tick });
    this.game.resetCamera();
  }

  testBounce(): void {
    const pad = this.game.world.colliders.find(c => c.surface === 'bounce');
    if (!pad) return;
    this.teleportAir((pad.minX + pad.maxX) / 2, pad.maxY + 9, 0, -3);
    this.run([]);
  }

  testFall(): void {
    const s = this.game.pogo.state;
    this.teleportAir(s.x < 20 ? 8 : s.x, 36, 0, 0);
    this.run([]);
  }

  testBoost(): void {
    const s = this.game.pogo.state;
    this.teleportAir(8, 38, 0, 6);
    this.run(this.rep(600, { tilt: 1 }), { until: g => g.pogo.state.boostReady, onEnd: () => this.run([{ ...NEUTRAL_INPUT, boostPressed: true }, ...this.rep(30, { tilt: 0 })]) });
    void s;
  }

  // ── InputSource (script overrides manual touch) ──
  sample(): PogoInput {
    if (this.script) {
      const sc = this.script;
      if (sc.until?.(this.game) || sc.i >= sc.frames.length) {
        const end = sc.onEnd; this.script = null; end?.();
        return this.script ? this.sample() : this.manual.sample();
      }
      const f = sc.frames[sc.i++];
      this.tilt = f.tilt;
      return f;
    }
    const m = this.manual.sample();
    this.tilt = this.manual.tilt;
    return m;
  }
  dispose(): void { this.unsub?.(); this.root.remove(); }

  /** Call every display frame. */
  update(): void {
    const g = this.game, s = g.pogo.state, cfg = g.cfg;
    if (s.jumps !== this.lastJumps) { if (s.jumps > this.lastJumps) this.jumpTicks.push(s.tick); this.lastJumps = s.jumps; if (this.jumpTicks.length > 8) this.jumpTicks.shift(); }
    const now = performance.now();
    if (now - this.lastText < 90) return;
    this.lastText = now;
    let cadence = '—';
    if (this.jumpTicks.length >= 3) {
      const d: number[] = []; for (let i = 1; i < this.jumpTicks.length; i++) d.push(this.jumpTicks[i] - this.jumpTicks[i - 1]);
      const avg = d.reduce((a, b) => a + b, 0) / d.length;
      cadence = `${avg.toFixed(0)} ticks (${(avg / TICK_RATE).toFixed(2)} s)  vs video ref ${REFERENCE_JUMP_INTERVAL_TICKS} (1.249 s)  ${avg < REFERENCE_JUMP_INTERVAL_TICKS * 0.8 ? '▲ faster' : avg > REFERENCE_JUMP_INTERVAL_TICKS * 1.25 ? '▼ slower' : '≈ match'}`;
    }
    const w = g.world.colliders[s.groundId];
    const ground = s.mode === 'GROUNDED' || s.mode === 'CHARGING' ? `${w?.id ?? '?'} (${w?.surface}) n=(${s.gnx.toFixed(2)}, ${s.gny.toFixed(2)})` : 'none';
    const power = s.mode === 'CHARGING' ? launchPower(s, cfg, 0) : 0;
    this.readout.textContent = [
      `mode ${s.mode}   tick ${s.tick}  (${TICK_RATE} Hz, 22.10 grid)`,
      `gravity ${cfg.gravity.toFixed(1)} m/s²   maxFall ${cfg.maxFallSpeed.toFixed(0)}  [TUNE_ME]`,
      `velocity (${s.vx.toFixed(2)}, ${s.vy.toFixed(2)}) m/s   h-speed ${Math.abs(s.vx).toFixed(2)}  v-speed ${s.vy.toFixed(2)}`,
      `angle ${(s.angle / DEG).toFixed(1)}°   ω ${(s.omega / DEG).toFixed(0)}°/s`,
      `jump power ${(power * 100).toFixed(0)}%   charge ${s.charge}/${cfg.chargeTicksMax} ticks   fall speed ${Math.max(0, -s.vy).toFixed(1)}`,
      `ground contact ${ground}`,
      `last collision ${this.lastNormal}`,
      `boost: spin ${(s.spin / DEG).toFixed(0)}°/${cfg.boostRotation}°  ready ${s.boostReady}  queued ${s.boostQueued}  power ${cfg.boostPower} m/s`,
      `height ${(s.y).toFixed(2)} m   jumps ${s.jumps}  boosts ${s.boosts}  falls ${s.falls}`,
      `jump cadence ${cadence}`,
      `fps ${g.renderer.fps.toFixed(0)}   draw calls ${g.renderer.stats.calls}   tris ${g.renderer.stats.triangles}`,
    ].join('\n');
  }
}
