import { h, svg } from './dom';
import { ICON } from './icons';
import { t, getLang, setLang, type Lang } from './i18n';
import { WORLDS, type WorldTheme } from '../data/worlds';
import { LEVELS, type Progression } from '../progression/Progression';
import type { SaveSystem } from '../progression/SaveSystem';
import { CATEGORIES, ITEMS, type Category, type ItemDef } from '../data/items';
import type { Settings } from '../progression/settings';

/** All non-gameplay screens (Phase 10). Each `show*` returns the element; App owns navigation. */
export interface ScreenCtx {
  save: SaveSystem;
  progression: Progression;
  click(): void;
  back(): void;
  settingsChanged(s: Settings, what: 'audio' | 'control' | 'graphics' | 'language' | 'haptics'): void;
  /** Wardrobe preview hook. */
  equipChanged(item: ItemDef): void;
  previewWorld(theme: WorldTheme | null): void;
}

const fmtTime = (s: number): string => { const m = Math.floor(s / 60); return `${m}:${(s - m * 60).toFixed(2).padStart(5, '0')}`; };
const stars = (n: number, total = 3): HTMLElement => h('span.stars', null, ...Array.from({ length: total }, (_, i) => { const s = svg(ICON.star, i < n ? 'on' : ''); return s; }));

export class Screens {
  constructor(private readonly root: HTMLElement, private readonly ctx: ScreenCtx) {}

  private mount(el: HTMLElement): HTMLElement { this.root.append(el); return el; }
  private btn(label: string, icon: string | null, cls: string, fn: () => void): HTMLElement {
    const b = h('button.btn' + (cls ? '.' + cls.split(' ').join('.') : ''), { onclick: () => { this.ctx.click(); fn(); } }, icon ? svg(icon) : null, h('span', null, label));
    return b;
  }

  splash(onStart: () => void): HTMLElement {
    const el = h('div.screen.splash', { onpointerdown: () => { onStart(); } },
      h('div.logo', null, 'Pogo', h('br'), 'Summit', h('small', null, t('tapToStart'))));
    return this.mount(el);
  }

  mainMenu(o: { onPlay(): void; onWardrobe(): void; onLeaderboard(): void; onOptions(): void; onHow(): void; version: string }): HTMLElement {
    const col = h('div.col.menu-col', null,
      this.btn(t('play'), ICON.play, 'primary', o.onPlay),
      this.btn(t('wardrobe'), ICON.hanger, '', o.onWardrobe),
      this.btn(t('leaderboard'), ICON.trophy, '', o.onLeaderboard),
      this.btn(t('options'), ICON.gear, '', o.onOptions),
      this.btn(t('howto'), ICON.help, '', o.onHow),
    );
    const left = h('div.col', { style: { justifyContent: 'center', gap: '1.4rem' } }, h('div.logo', null, 'Pogo', h('br'), 'Summit', h('small', null, 'CLIMB · BOUNCE · BOOST')), col);
    const ver = h('div', { style: { position: 'absolute', right: 'max(1rem,var(--sr))', bottom: 'max(.7rem,var(--sb))', opacity: '.6', fontSize: '.8rem' } }, `${t('version')} 0.1.0`);
    void o.version;
    return this.mount(h('div.screen.menu', null, left, ver));
  }

  gameMode(o: { onAdventure(): void; onLab(): void; onBack(): void }): HTMLElement {
    const card = (title: string, sub: string, bg: string, fn: () => void, disabled = false) =>
      h('button.card' + (disabled ? '.lock' : ''), { style: { background: bg, width: '15rem', minHeight: '13rem' }, onclick: () => { if (!disabled) { this.ctx.click(); fn(); } } }, disabled ? h('span.lockmark', null, svg(ICON.lock)) : null, h('span.t', null, title), h('span.s', null, sub));
    return this.mount(h('div.screen.dim.col', null,
      h('div.head', null, this.btn('', ICON.back, 'round', o.onBack), h('h2', null, t('gameMode'))),
      h('div.cards.grow', { style: { alignItems: 'center', justifyContent: 'center' } },
        card(t('adventure'), t('adventureSub'), 'linear-gradient(160deg,#ff8a2e,#d9402a 60%,#7a2a4a)', o.onAdventure),
        card(t('physicsLab'), t('physicsLabSub'), 'linear-gradient(160deg,#1fb0a8,#2b6ccf 70%,#3a2a8a)', o.onLab),
      )));
  }

  levelSelect(o: { onStart(levelId: string): void; onBack(): void }): HTMLElement {
    const P = this.ctx.progression;
    let world = WORLDS[0];
    const levelsBox = h('div.cards.scroll', { style: { paddingBottom: '.4rem' } });
    const worldsBox = h('div.cards.scroll', { style: { paddingBottom: '.6rem' } });
    const renderLevels = () => {
      levelsBox.replaceChildren();
      const list = LEVELS.filter(l => l.worldId === world.worldId);
      for (const l of list) {
        const rec = P.record(l.id), ok = P.isLevelPlayable(l.id);
        const body: (Node | null)[] = [
          !ok ? h('span.lockmark', null, svg(l.data ? ICON.lock : ICON.lock)) : null,
          h('span.t', null, l.name),
          h('span.s', null, l.data ? (rec.bestTimeSec !== null ? `${t('best')} ${fmtTime(rec.bestTimeSec)}` : `${t('level')} ${LEVELS.indexOf(l) + 1}`) : 'Coming soon'),
          l.data ? stars(rec.stars) : null,
        ];
        levelsBox.append(h('button.card' + (ok ? '' : '.lock'), {
          style: { background: `linear-gradient(170deg,${world.palette.background},${world.palette.primary} 120%)` },
          onclick: () => { if (ok) { this.ctx.click(); o.onStart(l.id); } },
        }, ...body));
      }
    };
    const renderWorlds = () => {
      worldsBox.replaceChildren();
      for (const w of WORLDS) {
        const unlocked = P.isWorldUnlocked(w.worldId);
        worldsBox.append(h('button.card' + (unlocked ? '' : '.lock'), {
          style: { background: `linear-gradient(165deg,${w.sky.top},${w.sky.mid} 50%,${w.palette.primary})`, outline: w === world ? '3px solid #ffd24a' : 'none', width: '11rem', minHeight: '8.5rem' },
          onclick: () => { this.ctx.click(); world = w; this.ctx.previewWorld(w); renderWorlds(); renderLevels(); },
        }, !unlocked ? h('span.lockmark', null, svg(ICON.lock)) : null, h('span.t', null, `${t('world')} ${WORLDS.indexOf(w) + 1}`), h('span.s', null, w.name)));
      }
    };
    renderWorlds(); renderLevels();
    return this.mount(h('div.screen.dim.col', null,
      h('div.head', null, this.btn('', ICON.back, 'round', () => { this.ctx.previewWorld(null); o.onBack(); }), h('h2', null, t('adventure')), h('div.spacer'), h('span', { style: { opacity: '.8' } }, `${P.totalStars()} ★`)),
      worldsBox, h('div.head', { style: { marginTop: '.4rem' } }, h('h2', { style: { fontSize: '1.2rem' } }, `${world.name} — ${world.subtitle}`)), levelsBox));
  }

  options(o: { onClose(): void }): HTMLElement {
    const S = this.ctx.save.data.settings;
    let tab: 'audio' | 'control' | 'graphics' | 'language' = 'audio';
    const body = h('div.sheet.scroll.grow');
    const tabs = h('div.tabs');
    const slider = (label: string, get: () => number, set: (v: number) => void, min: number, max: number, step: number, fmt: (v: number) => string, what: 'audio' | 'control' | 'graphics' | 'haptics') => {
      const val = h('span.val', null, fmt(get()));
      const inp = h('input', { type: 'range', min, max, step, value: get() }) as HTMLInputElement;
      inp.addEventListener('input', () => { set(+inp.value); val.textContent = fmt(+inp.value); this.ctx.settingsChanged(S, what); });
      inp.addEventListener('change', () => { this.ctx.save.save(); });
      return h('div.row', null, h('label', null, label), inp, val);
    };
    const seg = <T extends string>(label: string, opts: [T, string][], get: () => T, set: (v: T) => void, what: 'control' | 'graphics' | 'language' | 'haptics') => {
      const wrap = h('div.seg');
      const draw = () => { wrap.replaceChildren(); for (const [v, lab] of opts) wrap.append(h('button' + (get() === v ? '.on' : ''), { onclick: () => { this.ctx.click(); set(v); this.ctx.settingsChanged(S, what); this.ctx.save.save(); draw(); if (what === 'language') render(); } }, lab)); };
      draw();
      return h('div.row', null, h('label', null, label), wrap);
    };
    const toggle = (label: string, get: () => boolean, set: (v: boolean) => void, what: 'control' | 'graphics' | 'haptics') =>
      seg(label, [['on', t('on')], ['off', t('off')]], () => (get() ? 'on' : 'off'), v => set(v === 'on'), what);
    const render = () => {
      tabs.replaceChildren(...(['audio', 'control', 'graphics', 'language'] as const).map(k => h('button' + (tab === k ? '.on' : ''), { onclick: () => { this.ctx.click(); tab = k; render(); } }, t(k === 'control' ? 'controls' : k))));
      body.replaceChildren();
      const pc = (v: number) => `${Math.round(v * 100)}%`;
      if (tab === 'audio') body.append(
        slider(t('master'), () => S.audio.master, v => { S.audio.master = v; }, 0, 1, 0.05, pc, 'audio'),
        slider(t('music'), () => S.audio.music, v => { S.audio.music = v; }, 0, 1, 0.05, pc, 'audio'),
        slider(t('sfx'), () => S.audio.sfx, v => { S.audio.sfx = v; }, 0, 1, 0.05, pc, 'audio'),
        slider(t('ambient'), () => S.audio.ambient, v => { S.audio.ambient = v; }, 0, 1, 0.05, pc, 'audio'),
        toggle(t('haptics'), () => S.haptics.enabled, v => { S.haptics.enabled = v; }, 'haptics'),
        slider(t('hapticStrength'), () => S.haptics.strength, v => { S.haptics.strength = v; }, 0, 1.5, 0.1, v => `${Math.round(v * 100)}%`, 'haptics'));
      if (tab === 'control') body.append(
        seg(t('scheme'), [['drag', t('schemeDrag')], ['pad', t('schemePad')]], () => S.control.scheme, v => { S.control.scheme = v; }, 'control'),
        seg(t('guide'), [['off', t('guideOff')], ['short', t('guideShort')], ['full', t('guideFull')]], () => S.control.guide, v => { S.control.guide = v; }, 'control'),
        toggle(t('leftHanded'), () => S.control.leftHanded, v => { S.control.leftHanded = v; }, 'control'),
        slider(t('sensitivity'), () => S.control.sensitivity, v => { S.control.sensitivity = v; }, 0.4, 2.5, 0.05, v => `${v.toFixed(2)}×`, 'control'),
        slider(t('swipeDistance'), () => S.control.swipeDistance, v => { S.control.swipeDistance = v; }, 40, 220, 5, v => `${v} px`, 'control'),
        slider(t('swipeStrength'), () => S.control.swipeStrength, v => { S.control.swipeStrength = v; }, 0, 2, 0.1, v => `${v.toFixed(1)}`, 'control'),
        slider(t('deadzone'), () => S.control.deadzone, v => { S.control.deadzone = v; }, 0, 0.4, 0.01, pc, 'control'),
        slider(t('smoothing'), () => S.control.smoothing, v => { S.control.smoothing = v; }, 0, 0.9, 0.05, pc, 'control'),
        slider(t('chargeTime'), () => S.control.chargeTime, v => { S.control.chargeTime = v; }, 0.6, 1.6, 0.05, v => `${v.toFixed(2)}×`, 'control'));
      if (tab === 'graphics') body.append(
        seg(t('quality'), [['high', t('qHigh')], ['default', t('qDefault')], ['simplified', t('qSimplified')]], () => S.graphics.quality, v => { S.graphics.quality = v; }, 'graphics'),
        toggle(t('shake'), () => S.graphics.screenShake, v => { S.graphics.screenShake = v; }, 'graphics'));
      if (tab === 'language') body.append(
        seg(t('language'), [['en', 'English'], ['ar', 'العربية']], () => S.language, v => { S.language = v as Lang; setLang(v as Lang); }, 'language'));
    };
    render();
    return this.mount(h('div.screen.dim.col', null,
      h('div.head', null, this.btn('', ICON.back, 'round', o.onClose), h('h2', null, t('options'))), tabs, body));
  }

  wardrobe(o: { onClose(): void }): HTMLElement {
    const P = this.ctx.progression;
    let cat: Category = 'hat';
    const tabs = h('div.tabs'), grid = h('div.grid');
    const catName: Record<Category, string> = { hat: t('hat'), stick: t('stick'), outfit: t('outfit'), skin: t('skin'), boostFx: t('boostFx'), emote: t('emote') };
    const render = () => {
      tabs.replaceChildren(...CATEGORIES.map(c => h('button' + (c === cat ? '.on' : ''), { onclick: () => { this.ctx.click(); cat = c; render(); } }, catName[c])));
      grid.replaceChildren();
      for (const it of ITEMS.filter(i => i.category === cat)) {
        const unlocked = P.isUnlocked(it), sel = this.ctx.save.data.equipped[cat] === it.itemId;
        const sw = it.icon.startsWith('#') ? h('div.sw', { style: { background: it.icon } }) : h('div.sw', { style: { display: 'grid', placeItems: 'center', fontSize: '1.6rem', border: 'none' } }, it.icon);
        grid.append(h('button.item.rar-' + it.rarity + (sel ? '.sel' : '') + (unlocked ? '' : '.lock'), {
          onclick: () => { if (unlocked) { this.ctx.click(); P.equip(it); this.ctx.equipChanged(it); render(); } },
        }, sw, h('span', null, it.name), unlocked ? (sel ? h('small', null, t('equipped')) : null) : h('small', null, P.describe(it.unlockCondition))));
      }
    };
    render();
    // layout: the live 3D character stays visible on the left; the sheet sits on the right
    return this.mount(h('div.screen.col', { style: { background: 'linear-gradient(270deg,rgba(10,12,28,.85),rgba(10,12,28,.2) 70%,transparent)', alignItems: 'flex-end' } },
      h('div.col', { style: { width: 'min(56vw,34rem)', height: '100%' } },
        h('div.head', null, this.btn('', ICON.back, 'round', o.onClose), h('h2', null, t('wardrobe'))), tabs, h('div.sheet.scroll.grow', null, grid))));
  }

  leaderboard(o: { onClose(): void }): HTMLElement {
    const rows = h('tbody');
    for (const l of LEVELS.filter(x => x.data)) {
      const runs = this.ctx.save.data.leaderboard[l.id] ?? [];
      rows.append(h('tr', null, h('td', { colspan: 5, style: { fontWeight: '900', paddingTop: '.8rem' } }, `${l.name}`)));
      if (!runs.length) rows.append(h('tr', null, h('td', { colspan: 5, style: { opacity: '.7' } }, t('noRuns'))));
      runs.slice(0, 10).forEach((r, i) => rows.append(h('tr', null, h('td', null, `#${i + 1}`), h('td', null, fmtTime(r.timeSec)), h('td', null, `${r.jumps} ${t('jumps')}`), h('td', null, `${r.boosts} ⚡`), h('td', null, new Date(r.date).toLocaleDateString()))));
    }
    return this.mount(h('div.screen.dim.col', null,
      h('div.head', null, this.btn('', ICON.back, 'round', o.onClose), h('h2', null, t('leaderboard')), h('div.spacer'), h('span', { style: { opacity: '.7', fontSize: '.85rem' } }, t('localOnly'))),
      h('div.sheet.scroll.grow', null, h('table.lb', null, rows))));
  }

  howTo(o: { onClose(): void }): HTMLElement {
    const step = (key: string, demo: string) => h('div.step', null, h('div.demo', { html: demo }), t(key));
    const D = (inner: string) => `<svg viewBox="0 0 120 60" width="100%" height="100%">${inner}</svg>`;
    const finger = (x: number, y: number) => `<circle cx="${x}" cy="${y}" r="7" fill="#fff" opacity=".85"/>`;
    return this.mount(h('div.screen.dim.col', null,
      h('div.head', null, this.btn('', ICON.back, 'round', o.onClose), h('h2', null, t('howto'))),
      h('div.steps.scroll.grow', null,
        step('how1', D(`${finger(60, 34)}<circle cx="60" cy="34" r="14" fill="none" stroke="#ffd24a" stroke-width="3"><animate attributeName="r" values="8;18;8" dur="1.4s" repeatCount="indefinite"/></circle>`)),
        step('how2', D(`<circle cx="60" cy="30" r="7" fill="#fff" opacity=".9"><animate attributeName="cx" values="40;80;40" dur="2s" repeatCount="indefinite"/></circle><path d="M30 30h60" stroke="#fff" stroke-width="2" stroke-dasharray="3 4" opacity=".6"/>`)),
        step('how3', D(`<path d="M20 50 Q60 -10 100 50" stroke="#ffd24a" stroke-width="3" fill="none" stroke-dasharray="4 4"><animate attributeName="stroke-dashoffset" values="0;-16" dur="1s" repeatCount="indefinite"/></path><circle cx="20" cy="50" r="5" fill="#fff"/>`)),
        step('how4', D(`<g transform="translate(60 30)"><rect x="-4" y="-18" width="8" height="30" rx="3" fill="#fff"><animateTransform attributeName="transform" type="rotate" values="0;360" dur="1.2s" repeatCount="indefinite"/></rect></g>`)),
        step('how5', D(`<path d="M10 50h100" stroke="#6a8a3a" stroke-width="6"/><rect x="58" y="14" width="5" height="34" rx="2" fill="#fff" transform="rotate(12 60 48)"/>`)),
        step('how6', D(`<rect x="62" y="8" width="3" height="40" fill="#fff"/><path d="M65 8l24 8-24 8z" fill="#ff9a2e"/><path d="M10 50h100" stroke="#6a8a3a" stroke-width="6"/>`)),
      )));
  }

  pause(o: { onResume(): void; onRestart(): void; onOptions(): void; onMenu(): void }): HTMLElement {
    return this.mount(h('div.screen.dim.center', null,
      h('div.sheet.col', { style: { gap: '.7rem', minWidth: 'min(48vw,19rem)', alignItems: 'stretch' } },
        h('h2', { style: { margin: '0 0 .3rem', textAlign: 'center', fontSize: '1.8rem' } }, t('paused')),
        this.btn(t('resume'), ICON.play, 'primary', o.onResume), this.btn(t('restart'), ICON.retry, '', o.onRestart),
        this.btn(t('options'), ICON.gear, '', o.onOptions), this.btn(t('menu'), ICON.home, '', o.onMenu))));
  }

  results(o: { timeSec: number; jumps: number; boosts: number; falls: number; stars: number; newBest: boolean; best: number | null; rank: number; newItems: ItemDef[]; hasNext: boolean; onNext(): void; onRetry(): void; onMenu(): void }): HTMLElement {
    const sheet = h('div.sheet', null,
      h('h1', null, t('complete')),
      o.newBest ? h('div', { style: { color: '#7be07a', fontWeight: '900' } }, t('newBest')) : null,
      h('div.big', null, fmtTime(o.timeSec)),
      stars(o.stars),
      h('div.kv', null, h('div', null, h('b', null, o.jumps), t('jumps')), h('div', null, h('b', null, o.boosts), t('boosts')), h('div', null, h('b', null, o.falls), t('falls')), h('div', null, h('b', null, o.best !== null ? fmtTime(o.best) : '—'), t('best'))),
      o.newItems.length ? h('div', { style: { margin: '0 0 .8rem', color: '#ffd24a' } }, `★ ${t('unlock')}: ${o.newItems.map(i => i.name).join(', ')}`) : null,
      h('div.btnrow', null, o.hasNext ? this.btn(t('next'), ICON.next, 'primary', o.onNext) : null, this.btn(t('retry'), ICON.retry, '', o.onRetry), this.btn(t('menu'), ICON.home, '', o.onMenu)));
    return this.mount(h('div.screen.dim.results', null, sheet));
  }
}
