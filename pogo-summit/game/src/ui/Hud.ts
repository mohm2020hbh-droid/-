import { h, svg } from './dom';
import { ICON } from './icons';
import { t } from './i18n';
import type { ControlScheme } from '../progression/settings';
import type { PadElements } from '../input/TouchControls';

/**
 * Gameplay HUD (Phase 10) — minimal, matches the reference layout:
 *   top-left: Timer · Height (m) · Jumps  |  top-centre: Progress  |  top-right: Pause
 *   bottom-right: Boost (+ Jump/Charge in PAD mode) · bottom-left: stick (PAD mode)
 * Landscape only, safe-area aware, large touch targets (≥ 3.3 rem ≈ 48 dp).
 */
export class Hud {
  readonly root: HTMLElement;
  readonly pauseBtn: HTMLElement;
  readonly boostBtn: HTMLElement;
  readonly pad: PadElements;
  private timeEl!: HTMLElement; private heightEl!: HTMLElement; private jumpsEl!: HTMLElement; private boostsEl!: HTMLElement;
  private progFill!: HTMLElement; private progText!: HTMLElement; private hintEl!: HTMLElement; private toastEl!: HTMLElement;
  private flashEl!: HTMLElement; private ring!: HTMLElement; private ringFg!: SVGCircleElement; private boostCount!: HTMLElement;
  private last = { time: '', h: -1, j: -1, b: -1, p: -1 };
  private hintTimer = 0; private toastTimer = 0;
  private labels: { el: HTMLElement; key: string }[] = [];

  constructor(parent: HTMLElement) {
    this.pauseBtn = h('button.circle-btn.pause', { 'aria-label': 'Pause' }, svg(ICON.pause));
    this.boostBtn = h('button.circle-btn.boost', { 'aria-label': 'Boost' }, svg(ICON.chevrons));
    this.boostCount = h('span.count', null, '0');
    this.boostBtn.append(this.boostCount);
    const stickKnob = h('div.knob');
    const stickZone = h('div.stick-zone', null, h('div.arrows', null, svg(ICON.back), svg(ICON.next)), stickKnob);
    const jumpBtn = h('button.circle-btn.jump', { 'aria-label': 'Jump' }, svg(ICON.charge));
    this.pad = { stickZone, stickKnob, jumpBtn };

    const stat = (icon: string) => { const v = h('span'); return { v, el: h('div.stat.panel', null, svg(icon), v) }; };
    const tm = stat(ICON.clock), ht = stat(ICON.mountain), jp = stat(ICON.boot), bs = stat(ICON.bolt);
    this.timeEl = tm.v; this.heightEl = ht.v; this.jumpsEl = jp.v; this.boostsEl = bs.v;
    this.progFill = h('i'); this.progText = h('span');
    this.hintEl = h('div.hint.panel');
    this.toastEl = h('div.toast.panel');
    this.flashEl = h('div.flash');
    this.ringFg = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    this.ring = h('div.charge-ring');
    this.ring.innerHTML = '<svg viewBox="0 0 100 100"><circle class="bg" cx="50" cy="50" r="46"/><circle class="fg" cx="50" cy="50" r="46"/></svg>';
    this.ringFg = this.ring.querySelector('.fg') as unknown as SVGCircleElement;

    const lab = (key: string) => { const el = h('div.btn-label', null, t(key)); this.labels.push({ el, key }); return el; };
    this.root = h('div.hud.drag', null,
      h('div.vignette'), this.flashEl,
      h('div.stats', null, tm.el, ht.el, jp.el, bs.el),
      h('div.top-mid', null, h('div.prog', null, this.progFill), this.progText, this.hintEl),
      this.pauseBtn,
      h('div.btn-wrap.stick-wrap.pad-only', null, stickZone),
      h('div.btn-wrap.jump-wrap.pad-only', null, jumpBtn, lab('jumpCharge')),
      h('div.btn-wrap.boost-wrap', null, this.boostBtn, lab('boost')),
      this.ring, this.toastEl,
    );
    this.progText.style.cssText = 'font-size:.85rem;opacity:.9;text-shadow:0 .1rem .3rem rgba(0,0,0,.6)';
    parent.append(this.root);
    // buttons must never leak events to the canvas
    for (const el of [this.pauseBtn]) el.addEventListener('pointerdown', e => e.stopPropagation());
    this.setScheme('drag', false);
  }

  relabel(): void { for (const l of this.labels) l.el.textContent = t(l.key); }

  setScheme(s: ControlScheme, leftHanded: boolean): void {
    this.root.classList.toggle('drag', s === 'drag');
    this.root.classList.toggle('pad', s === 'pad');
    this.root.classList.toggle('lefty', leftHanded);
  }

  show(v: boolean): void { this.root.style.display = v ? '' : 'none'; }

  /** Throttled text update (call every frame; DOM is touched only when a value changes). */
  update(o: { seconds: number; height: number; jumps: number; boosts: number; progress: number; boostReady: boolean; boostQueued: boolean }): void {
    const m = Math.floor(o.seconds / 60), s = o.seconds - m * 60;
    const ts = `${String(m).padStart(2, '0')}:${s.toFixed(2).padStart(5, '0')}`;
    if (ts !== this.last.time) { this.timeEl.textContent = ts; this.last.time = ts; }
    const hh = Math.max(0, Math.round(o.height));
    if (hh !== this.last.h) { this.heightEl.textContent = `${hh} m`; this.last.h = hh; }
    if (o.jumps !== this.last.j) { this.jumpsEl.textContent = String(o.jumps); this.last.j = o.jumps; }
    if (o.boosts !== this.last.b) { this.boostsEl.textContent = String(o.boosts); this.boostCount.textContent = String(o.boosts); this.last.b = o.boosts; }
    const pp = Math.round(o.progress * 100);
    if (pp !== this.last.p) { this.progFill.style.width = `${pp}%`; this.progText.textContent = `${t('progress')} ${pp}%`; this.last.p = pp; }
    this.boostBtn.classList.toggle('ready', o.boostReady);
    this.boostBtn.classList.toggle('queued', o.boostQueued);
  }

  setHint(text: string | null): void {
    if (text) { this.hintEl.textContent = text; this.hintEl.classList.add('show'); window.clearTimeout(this.hintTimer); }
    else this.hintEl.classList.remove('show');
  }

  toast(text: string, ms = 1400): void {
    this.toastEl.textContent = text; this.toastEl.classList.add('show');
    window.clearTimeout(this.toastTimer);
    this.toastTimer = window.setTimeout(() => this.toastEl.classList.remove('show'), ms);
  }

  flashDanger(): void { this.flashEl.classList.add('on'); requestAnimationFrame(() => requestAnimationFrame(() => this.flashEl.classList.remove('on'))); }

  /** Charge ring anchored to a screen point (CSS px); null hides it. */
  setCharge(pos: { x: number; y: number } | null, frac: number): void {
    if (!pos) { if (this.ring.style.display !== 'none') this.ring.style.display = 'none'; return; }
    this.ring.style.display = 'block';
    this.ring.style.transform = `translate(${pos.x.toFixed(1)}px,${pos.y.toFixed(1)}px)`;
    this.ringFg.style.strokeDashoffset = String(289 * (1 - Math.max(0, Math.min(1, frac))));
  }
}
