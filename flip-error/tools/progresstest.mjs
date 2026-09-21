/**
 * The meta game: levels unlocking, coins being earned, the shop spending them,
 * and all of it surviving a reload.
 *
 * The economy rules are already pinned by core unit tests; what this checks is
 * that the shell is wired to them - that finishing a level really banks what the
 * model says, that a locked level cannot be opened by asking nicely, and that a
 * purchase is still there after the page comes back.
 *
 *   node tools/progresstest.mjs [--headed] [--shots DIR]
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dist = path.join(root, 'web/build/dist/js/productionExecutable');
const plan1 = JSON.parse(fs.readFileSync(path.join(root, 'core/build/level1-plan.json'), 'utf8'));
const plan2 = JSON.parse(fs.readFileSync(path.join(root, 'core/build/level2-plan.json'), 'utf8'));
const shotDir = process.argv.includes('--shots')
  ? process.argv[process.argv.indexOf('--shots') + 1]
  : path.join(root, 'build/progresstest');
fs.mkdirSync(shotDir, { recursive: true });

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json' };
const server = http.createServer((req, res) => {
  const asked = req.url.split('?')[0];
  const f = path.join(dist, asked === '/' ? 'index.html' : asked);
  if (asked === '/favicon.ico') { res.writeHead(204); return res.end(); }
  if (!f.startsWith(dist) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': MIME[path.extname(f)] ?? 'application/octet-stream' });
  res.end(fs.readFileSync(f));
});
await new Promise(r => server.listen(0, r));
const url = `http://127.0.0.1:${server.address().port}/`;

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

const preinstalled = '/opt/pw-browsers/chromium';
const browser = await chromium.launch({
  headless: !process.argv.includes('--headed'),
  ...(fs.existsSync(preinstalled) ? { executablePath: preinstalled } : {}),
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding',
         '--disable-backgrounding-occluded-windows'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 600 }, deviceScaleFactor: 1 });

const errors = [];
const external = t => /ERR_CERT|fonts\.googleapis|fonts\.gstatic|Failed to load resource/.test(t);
page.on('console', m => { if (m.type() === 'error' && !external(m.text())) errors.push(m.text()); });
page.on('pageerror', e => errors.push(String(e)));

const boot = async () => {
  await page.goto(url, { waitUntil: 'load' });
  await page.waitForFunction(() => window.FLIP && typeof window.FLIP.coins === 'function', { timeout: 10000 });
};
await boot();
await page.evaluate(() => FLIP.wipe());
await boot();
await page.addInitScript(() => {});

/** Fly a level from its verified plan, boosting at the apex where it asks for one. */
const flyPlan = (jumps) => page.evaluate(p => new Promise(res => {
  let i = 0, f = 0, boostAt = 0;
  const go = () => {
    f++;
    // The plan carries the x the second tap belongs at, not a frame count, so a
    // 60Hz browser can fly a line the verifier solved at 240Hz.
    if (i < p.length && FLIP.x() >= p[i].x) { boostAt = p[i].boosted ? p[i].boostX : 0; FLIP.tap(); i++; }
    else if (boostAt > 0 && FLIP.x() >= boostAt && FLIP.canDouble()) { FLIP.tap(); boostAt = 0; }
    if (FLIP.state() !== 'RUNNING' || f > 6000) {
      return res({ state: FLIP.state(), pct: FLIP.progress(), attempts: FLIP.attempts(),
                   stars: FLIP.stars(), doubles: FLIP.doubleJumps() });
    }
    requestAnimationFrame(go);
  };
  requestAnimationFrame(go);
}), jumps);

const waitScreen = (s, t = 8000) =>
  page.waitForFunction(x => FLIP.screen() === x, s, { timeout: t }).then(() => true).catch(() => false);

// 1 — a fresh profile: one level open, one locked, nothing in the purse.
{
  const st = await page.evaluate(() => ({
    screen: FLIP.screen(), coins: FLIP.coins(),
    l1: FLIP.unlocked(1), l2: FLIP.unlocked(2), l3: FLIP.unlocked(3),
  }));
  check('a fresh profile opens on the main menu', st.screen === 'MENU', st.screen);
  const homeBtns = await page.locator('#menu .home button').count();
  check('the main menu offers play, levels, shop and settings', homeBtns === 4, `${homeBtns} buttons`);
  // everything below reads the level grid, which is one tap in
  await page.evaluate(() => document.getElementById('h-levels').click());
  check('level 1 is open and level 2 is not', st.l1 && !st.l2 && !st.l3);
  check('a fresh purse is empty', st.coins === 0, `★ ${st.coins}`);
  const cards = await page.locator('#menu .card').count();
  check('the level select lists every level', cards === 24, `${cards} cards`);
  const locked = await page.locator('#menu .card[disabled]').count();
  check('locked levels cannot be tapped', locked === 23, `${locked} disabled`);
  // the transition between worlds: the list is broken into named places, and the
  // one you have not reached yet is visibly further away.
  const worlds = await page.locator('#menu .world').count();
  check('the level select is split into worlds', worlds === 4, `${worlds} banners`);
  const names = await page.locator('#menu .world .wt').allTextContents();
  check('and each world is named',
    names.join('/') === 'NEON CITY/NEON DESERT/THE ABYSS/CLOCKWORK', names.join('/'));
  const far = await page.locator('#menu .world.far').count();
  check('the worlds you have not reached read as far off', far === 3, `${far} dimmed`);
  // And every card says what the level is actually called. The names used to be
  // typed next to the level list rather than taken from it, and they drifted:
  // the menu offered THE ARMS, MEMORY and THE SUN BELOW for levels that had been
  // renamed, and DESERT CHAOS for DESERT STORM. This is the check that would
  // have caught it.
  await page.evaluate(() => FLIP.setting('unlockAll', true));
  const cardNames = await page.locator('#menu .card .nm').allTextContents();
  const realNames = [];
  for (let id = 1; id <= 24; id++) {
    await page.evaluate(i => FLIP.play(i), id);
    await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
    realNames.push(await page.evaluate(() => FLIP.levelName()));
    await page.evaluate(() => FLIP.openMenu());
  }
  const wrong = realNames.filter((n, i) => n !== cardNames[i]);
  check('every card is named after the level it opens', wrong.length === 0,
    wrong.length ? `first mismatch: card "${cardNames[realNames.indexOf(wrong[0])]}" vs level "${wrong[0]}"`
                 : `${realNames.length} cards`);
  // Put the save back the way this section found it. The checks after this one
  // are about a FRESH progression - what is locked, what refuses to start - and
  // leaving the testing switch on quietly turned four of them into failures that
  // had nothing to do with what they were testing.
  await page.evaluate(() => { FLIP.setting('unlockAll', false); FLIP.openMenu(); });
  await page.waitForFunction(() => FLIP.screen() === 'MENU', { timeout: 5000 });
  await page.screenshot({ path: path.join(shotDir, '01-menu-fresh.png') });
}

// 2 — asking for a locked level politely gets you nowhere.
{
  await page.evaluate(() => FLIP.play(2));
  check('a locked level refuses to start', await page.evaluate(() => FLIP.screen()) === 'MENU');
}

// 3 — clear level 1 on the verified line, first time, no deaths.
{
  await page.evaluate(() => FLIP.play(1));
  await waitScreen('PLAYING');
  const run = await flyPlan(plan1.jumps);
  check('level 1 still clears on its verified line', run.state === 'COMPLETE',
        `${(run.pct * 100).toFixed(0)}%, attempt ${run.attempts}`);
  check('the reward panel comes up', await waitScreen('REWARD'), await page.evaluate(() => FLIP.screen()));
  const st = await page.evaluate(() => ({ coins: FLIP.coins(), l2: FLIP.unlocked(2) }));
  // Whether a near-perfect run brushes a coin depends on frame-rate luck, and
  // the core owns that design question. What this has to prove is that the
  // shell banks exactly what the model says for what actually happened.
  const owed = 25 + 15 + 10 * run.stars;
  check('a first clear banks exactly what the model says', st.coins === owed,
        `★ ${st.coins} for 25 clear + 15 perfect + ${run.stars} coin(s)`);
  check('clearing level 1 unlocks level 2', st.l2);
  await page.screenshot({ path: path.join(shotDir, '02-reward.png') });
}

// 4 — the payout does not repeat itself.
{
  const before = await page.evaluate(() => FLIP.coins());
  await page.evaluate(() => FLIP.play(1));
  await waitScreen('PLAYING');
  await flyPlan(plan1.jumps);
  await waitScreen('REWARD');
  const after = await page.evaluate(() => FLIP.coins());
  check('replaying a cleared level pays almost nothing', after - before === 5,
        `★ ${before} -> ★ ${after}`);
}

// 5 — it all survives a reload.
{
  await page.evaluate(() => FLIP.openMenu());
  const before = await page.evaluate(() => ({ coins: FLIP.coins(), l2: FLIP.unlocked(2) }));
  await boot();
  const after = await page.evaluate(() => ({ coins: FLIP.coins(), l2: FLIP.unlocked(2), screen: FLIP.screen() }));
  check('progress survives a reload', after.coins === before.coins && after.l2 === before.l2,
        `★ ${after.coins}, level 2 ${after.l2 ? 'open' : 'locked'}`);
  check('and it comes back on the menu', after.screen === 'MENU');
}

// 6 — the shop: what you cannot afford, what you can, and what you wear.
{
  await page.evaluate(() => FLIP.openShop());
  await waitScreen('SHOP');
  const tabs = await page.locator('#shop .tab').count();
  check('the shop has one tab per category', tabs === 4, `${tabs} tabs`);
  const purse = await page.evaluate(() => FLIP.coins());
  const affordable = await page.locator('#shop .buy').count();
  check('nothing is affordable on an early purse', affordable === 0, `★ ${purse}, ${affordable} buyable`);
  await page.screenshot({ path: path.join(shotDir, '03-shop-broke.png') });

  await page.evaluate(() => FLIP.grant(400));
  await page.evaluate(() => FLIP.openShop());
  const buyable = await page.locator('#shop .buy').count();
  check('coins make the shop live', buyable > 0, `${buyable} buyable`);

  // BUY -> CONFIRMATION -> CANCEL -> nothing changed
  const cancelled = await page.evaluate(async () => {
    const before = FLIP.coins();
    const btn = document.querySelector('#shop .buy[data-ask]');
    const id = btn.dataset.ask;
    btn.click();
    const asked = !document.getElementById('modal').hidden;
    document.getElementById('m-cancel').click();
    return { before, after: FLIP.coins(), id, owns: FLIP.owns(id), asked,
             closed: document.getElementById('modal').hidden };
  });
  check('a tap on BUY asks first', cancelled.asked);
  // the sheet's preview animates; two samples a few frames apart must differ
  const alive = await page.evaluate(async () => {
    document.querySelector('#shop .buy[data-ask]').click();
    const c = document.querySelector('#modal .prev');
    const grab = () => c.getContext('2d').getImageData(0, 0, c.width, c.height).data.join('').length;
    const shot = (ms) => new Promise(r => setTimeout(() => r(grab()), ms));
    const a = await shot(60), b = await shot(420), d = await shot(900);
    const same = (a === b) && (b === d);
    document.getElementById('m-cancel').click();
    return !same;
  });
  check('the preview in the sheet is alive, not a still', alive);
  check('CANCEL closes the sheet', cancelled.closed);
  check('CANCEL spends nothing', cancelled.after === cancelled.before, `★ ${cancelled.after}`);
  check('CANCEL grants nothing', !cancelled.owns, cancelled.id);
  await page.screenshot({ path: path.join(shotDir, '04-confirm.png') });

  // BUY -> CONFIRMATION -> BUY -> purchased
  const spent = await page.evaluate(async () => {
    const before = FLIP.coins();
    const btn = document.querySelector('#shop .buy[data-ask]');
    const id = btn.dataset.ask;
    btn.click();
    document.getElementById('m-buy').click();
    return { before, after: FLIP.coins(), id, owns: FLIP.owns(id), worn: FLIP.equippedOf('SHAPE'),
             closed: document.getElementById('modal').hidden };
  });
  check('confirming spends the coins', spent.after < spent.before, `★ ${spent.before} -> ★ ${spent.after}`);
  check('confirming grants the item', spent.owns, spent.id);
  check('and equips it straight away', spent.worn === spent.id, spent.worn);
  check('the sheet closes after buying', spent.closed);
  await page.screenshot({ path: path.join(shotDir, '05-shop-bought.png') });

  const wornTag = await page.locator('#shop .tag.worn').count();
  check('the shop marks what is equipped', wornTag >= 1, `${wornTag} marked`);
  const shapes = await page.locator('#shop .item').count();
  check('the new silhouettes are on sale', shapes >= 13, `${shapes} shapes`);
}

// 6b — the switches, and the second language.
{
  await page.evaluate(() => { FLIP.openMenu(); document.getElementById('h-settings').click(); });
  const rows = await page.locator('#settings .sw').count();
  check('settings offers every switch', rows === 5, `${rows} toggles`);
  // Two volumes: the game has neither music nor ambience, so it offers a control
  // for neither. A slider for a thing that does not exist tells the player there
  // is something they failed to hear.
  const vols = await page.evaluate(() =>
    [...document.querySelectorAll('#settings input[data-vol]')].map(e => e.dataset.vol));
  check('and two volumes, for the two things that make sound',
    vols.join(',') === 'master,sfx', vols.join(','));
  const labels = await page.evaluate(() =>
    [...document.querySelectorAll('#settings .row.set span')].map(e => e.textContent.trim()).join('|'));
  check('nothing in settings claims there is music or ambience',
    !/MUSIC|AMBIENCE|موسيق|البيئة/i.test(labels), labels.slice(0, 80));

  // the testing switch: it opens doors and touches nothing behind them
  const unlocked = await page.evaluate(() => {
    const before = { l5: FLIP.unlocked(5), coins: FLIP.coins(), stars: FLIP.levelStars(1) };
    document.querySelector('#settings .sw[data-toggle="unlockAll"]').click();
    const after = { l5: FLIP.unlocked(5), coins: FLIP.coins(), stars: FLIP.levelStars(1) };
    return { before, after };
  });
  check('the testing switch opens every level', !unlocked.before.l5 && unlocked.after.l5);
  check('and changes nothing else',
        unlocked.after.coins === unlocked.before.coins && unlocked.after.stars === unlocked.before.stars,
        `★ ${unlocked.after.coins}`);

  const playable = await page.evaluate(async () => {
    FLIP.play(5);
    return { screen: FLIP.screen(), level: FLIP.level() };
  });
  check('a locked level is playable once it is on', playable.screen === 'PLAYING' && playable.level === 5,
        `${playable.screen} level ${playable.level}`);
  await page.evaluate(() => { FLIP.openMenu(); document.getElementById('h-settings').click();
                              document.querySelector('#settings .sw[data-toggle="unlockAll"]').click(); });
  check('turning it back off restores the progression',
        !(await page.evaluate(() => FLIP.unlocked(5))));

  const toggled = await page.evaluate(() => {
    const before = FLIP.settingOf('sfx');
    const s = document.querySelector('#settings input[data-vol="sfx"]');
    s.value = '0'; s.dispatchEvent(new Event('input', { bubbles: true }));
    return { before, after: FLIP.settingOf('sfx') };
  });
  check('a volume actually moves', toggled.after === !toggled.before,
        `sfx ${toggled.before} -> ${toggled.after}`);

  await page.evaluate(() => document.querySelector('#settings .lang[data-lang="AR"]').click());
  const ar = await page.evaluate(() => ({
    lang: FLIP.lang(),
    dir: document.documentElement.getAttribute('dir'),
    text: document.querySelector('#settings .row.set span').textContent.trim(),
  }));
  check('switching to Arabic flips the page to RTL', ar.lang === 'AR' && ar.dir === 'rtl', ar.dir);
  check('and the interface is actually translated', /[\u0600-\u06FF]/.test(ar.text), ar.text);
  await page.screenshot({ path: path.join(shotDir, '06-settings-ar.png') });

  await page.evaluate(() => document.querySelector('#settings .lang[data-lang="EN"]').click());
  const back = await page.evaluate(() => ({ lang: FLIP.lang(), dir: document.documentElement.getAttribute('dir') }));
  check('and back to English', back.lang === 'EN' && back.dir === 'ltr');

  // settings survive the reload too
  await page.evaluate(() => FLIP.setting('colorblind', true));
  await boot();
  check('settings survive a reload', await page.evaluate(() => FLIP.settingOf('colorblind')));
  await page.evaluate(() => FLIP.setting('colorblind', false));
}

// 7 — the purchase survives a reload too, and nothing on sale is power.
{
  const worn = await page.evaluate(() => FLIP.equippedOf('SHAPE'));
  await boot();
  check('a purchase survives a reload', await page.evaluate(() => FLIP.equippedOf('SHAPE')) === worn, worn);

  await page.evaluate(() => FLIP.openShop());
  const labels = await page.locator('#shop .tab').allTextContents();
  const cats = labels.map(t => t.trim()).sort().join(',');
  check('the shop sells appearance and nothing else', cats === 'COLORS,FACES,SHAPES,TRAILS', cats);
}

// 8 — level 2: playable, beatable on its verified line, and it needs the boost.
{
  await page.evaluate(() => FLIP.play(2));
  const started = await waitScreen('PLAYING');
  check('level 2 opens once it is unlocked', started);
  const run = await flyPlan(plan2.jumps);
  check('level 2 clears on its verified line', run.state === 'COMPLETE',
        `${(run.pct * 100).toFixed(0)}%, ${run.doubles} boosts used`);
  check('clearing level 2 needed the second jump', run.doubles >= plan2.boosts,
        `${run.doubles} boosts against the ${plan2.boosts} the level demands`);
  await waitScreen('REWARD');
  await page.screenshot({ path: path.join(shotDir, '05-level2-reward.png') });
}

// 9 — collecting a star coin banks it.
{
  await page.evaluate(() => FLIP.play(1));
  await waitScreen('PLAYING');
  const got = await page.evaluate(p => new Promise(res => {
    let i = 0, f = 0, jumped = false, boosted = false;
    const go = () => {
      f++;
      const x = FLIP.x();
      if (i < p.length && x >= p[i].x) { FLIP.tap(); i++; }
      else if (!jumped && x >= 166.3) { FLIP.tap(); jumped = true; }
      else if (jumped && !boosted && !FLIP.grounded() && FLIP.vy() <= 0) { FLIP.tap(); boosted = true; }
      if (x > 176 || f > 4000 || FLIP.state() !== 'RUNNING') return res(FLIP.stars());
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }), plan1.jumps);
  check('a star coin can be picked up in a run', got >= 1, `${got} collected`);
}

// 9b — COLLECT STAR -> DIE -> RESTART -> the star is still yours.
{
  await page.evaluate(() => FLIP.play(1));
  await waitScreen('PLAYING');
  const run = await page.evaluate(p => new Promise(res => {
    let i = 0, f = 0, jumped = false, boosted = false;
    const go = () => {
      f++;
      const x = FLIP.x();
      if (i < p.length && x >= p[i].x) { FLIP.tap(); i++; }
      else if (!jumped && x >= 166.3) { FLIP.tap(); jumped = true; }
      else if (jumped && !boosted && !FLIP.grounded() && FLIP.vy() <= 0) { FLIP.tap(); boosted = true; }
      if (FLIP.stars() > 0 || f > 4000 || FLIP.state() !== 'RUNNING') {
        return res({ stars: FLIP.stars(), banked: FLIP.levelStars(1), coins: FLIP.coins() });
      }
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }), plan1.jumps);
  check('a collected coin is banked the instant it is touched',
        run.stars >= 1 && run.banked >= 1, `${run.stars} in hand, ${run.banked} banked`);

  // now throw the run away well before the finish
  const died = await page.evaluate(() => new Promise(res => {
    let f = 0;
    const go = () => {
      f++;
      if (FLIP.state() === 'DEAD' || f > 3000) {
        return res({ state: FLIP.state(), pct: FLIP.progress(), banked: FLIP.levelStars(1) });
      }
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  check('the run is lost without finishing', died.state === 'DEAD',
        `${(died.pct * 100).toFixed(0)}%`);
  check('dying does not take the coin back', died.banked >= 1, `${died.banked} still banked`);

  await page.evaluate(() => FLIP.restart());
  await boot();
  check('and it is still there after a reload', await page.evaluate(() => FLIP.levelStars(1)) >= 1);
}

// 9c — the moving hazards actually move, and move the same way every run.
{
  await page.evaluate(() => FLIP.play(2));
  await waitScreen('PLAYING');
  const movers = await page.evaluate(() => FLIP.movers());
  check('level 2 carries moving hazards', movers >= 3, `${movers} movers`);

  const track = await page.evaluate(() => new Promise(res => {
    const seen = []; let f = 0;
    const go = () => {
      f++;
      if (f % 6 === 0) seen.push({ x: FLIP.x(), m: FLIP.moverX(0) });
      if (f > 120) return res(seen);
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }));
  const spread = Math.max(...track.map(s => s.m)) - Math.min(...track.map(s => s.m));
  check('a mover is actually sliding', spread > 1.0, `${spread.toFixed(2)}u of travel`);

  // Same position for the same player position, on a fresh attempt: the hazard
  // is a function of where you are, which is what makes it learnable.
  const first = track[8];
  const repeat = await page.evaluate(targetX => new Promise(res => {
    FLIP.restart();
    let f = 0;
    const go = () => {
      f++;
      if (FLIP.x() >= targetX || f > 4000) return res({ x: FLIP.x(), m: FLIP.moverX(0) });
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }), first.x);
  check('and it is in the same place at the same point of the level',
        Math.abs(repeat.m - first.m) < 0.25,
        `${first.m.toFixed(2)}u then ${repeat.m.toFixed(2)}u at x≈${first.x.toFixed(0)}`);
}

// 10 — the effects budget holds on the new level too.
{
  await page.evaluate(() => FLIP.play(2));
  await waitScreen('PLAYING');
  const ft = await page.evaluate(p => new Promise(res => {
    const times = []; let last = performance.now(); let i = 0, f = 0, owed = 0;
    const go = () => {
      const now = performance.now(); times.push(now - last); last = now;
      f++;
      if (i < p.length && FLIP.x() >= p[i].x) { owed = p[i].boosted ? p[i].boostX : 0; FLIP.tap(); i++; }
      else if (owed && FLIP.x() >= owed && FLIP.canDouble()) { FLIP.tap(); owed = 0; }
      if (FLIP.state() !== 'RUNNING') { FLIP.restart(); i = 0; owed = 0; }
      if (f > 900) return res(times.slice(60));
      requestAnimationFrame(go);
    };
    requestAnimationFrame(go);
  }), plan2.jumps);
  const sorted = [...ft].sort((a, b) => a - b);
  const p50 = sorted[Math.floor(sorted.length * 0.5)];
  const p99 = sorted[Math.floor(sorted.length * 0.99)];
  check('60fps holds on level 2', p50 < 20 && p99 < 40,
        `median ${p50.toFixed(1)}ms, p99 ${p99.toFixed(1)}ms over ${ft.length} frames`);
}

// 12 — the wardrobe. Every category, worn, and then actually played in.
{
  await page.evaluate(() => { FLIP.setting('tryAllCosmetics', true); FLIP.openShop(); });
  const cats = ['SHAPE', 'COLOR', 'TRAIL', 'FACE'];
  const tried = [];
  for (const cat of cats) {
    const picked = await page.evaluate(async c => {
      // open that tab, take the first thing not already worn, and try it on
      const tab = [...document.querySelectorAll('#shop .tab')]
        .find(t => t.dataset.cat === c);
      tab.click();
      await new Promise(r => requestAnimationFrame(r));
      const btn = document.querySelector('#shop .buy.try') ||
                  document.querySelector('#shop .buy.equip');
      if (!btn) return null;
      const id = btn.dataset.try ?? btn.dataset.equip;
      btn.click();
      await new Promise(r => requestAnimationFrame(r));
      return { id, worn: FLIP.equippedOf(c), owned: FLIP.owns(id) };
    }, cat);
    tried.push({ cat, ...(picked ?? {}) });
  }
  check('every category can be worn', tried.every(t => t.id && t.worn === t.id),
    tried.map(t => `${t.cat}=${t.worn}`).join(' '));
  // The whole point of the test switch: it dresses the runner, it does not
  // quietly hand out the goods.
  const gifted = tried.filter(t => t.owned);
  check('and trying something on does not buy it',
    gifted.length === 0, gifted.map(t => t.id).join(',') || 'nothing was gifted');
  const priced = await page.evaluate(() =>
    [...document.querySelectorAll('#shop .buy')].some(b => /\d/.test(b.textContent)) ||
    [...document.querySelectorAll('#shop .tag.short')].length > 0);
  check('and the prices are still on the shelf', priced);

  // now play in it
  await page.evaluate(() => FLIP.play(1));
  await page.waitForFunction(() => FLIP.screen() === 'PLAYING', { timeout: 5000 });
  const inPlay = await page.evaluate(() => new Promise(res => {
    let f = 0;
    const step = () => {
      if (f % 9 === 0) FLIP.tap();
      if (++f > 90) return res({ effects: FLIP.effects(), head: FLIP.trailHeadX(),
                                 tail: FLIP.trailTailX(), state: FLIP.state() });
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }));
  check('the worn look survives into play', inPlay.state === 'RUNNING' || inPlay.state === 'DEAD');
  check('and its trail is a live spread behind the runner',
    inPlay.effects > 0 && inPlay.head - inPlay.tail > 0.5,
    `${inPlay.effects} live pieces, ${(inPlay.head - inPlay.tail).toFixed(2)}u of ghosts`);
  await page.screenshot({ path: path.join(shotDir, '08-worn.png') });
  await page.evaluate(() => { FLIP.setting('tryAllCosmetics', false); FLIP.openMenu(); });
}

check('no javascript errors across the meta game', errors.length === 0, errors.slice(0, 3).join(' | '));

await browser.close();
server.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(failed.length ? 1 : 0);
