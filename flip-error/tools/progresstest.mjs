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
  check('a fresh profile opens on the level select', st.screen === 'MENU', st.screen);
  check('level 1 is open and level 2 is not', st.l1 && !st.l2 && !st.l3);
  check('a fresh purse is empty', st.coins === 0, `★ ${st.coins}`);
  const cards = await page.locator('#menu .card').count();
  check('the level select lists every level', cards === 3, `${cards} cards`);
  const locked = await page.locator('#menu .card[disabled]').count();
  check('locked levels cannot be tapped', locked === 2, `${locked} disabled`);
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

  // buy the first paid shape and check the runner is actually wearing it
  const spent = await page.evaluate(async () => {
    const before = FLIP.coins();
    const btn = document.querySelector('#shop .buy');
    const id = btn.dataset.id;
    btn.click();
    return { before, after: FLIP.coins(), id, owns: FLIP.owns(id), worn: FLIP.equippedOf('SHAPE') };
  });
  check('buying spends the coins', spent.after < spent.before, `★ ${spent.before} -> ★ ${spent.after}`);
  check('buying grants the item', spent.owns, spent.id);
  check('and equips it straight away', spent.worn === spent.id, spent.worn);
  await page.screenshot({ path: path.join(shotDir, '04-shop-bought.png') });

  const wornTag = await page.locator('#shop .tag.worn').count();
  check('the shop marks what is equipped', wornTag >= 1, `${wornTag} marked`);
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

check('no javascript errors across the meta game', errors.length === 0, errors.slice(0, 3).join(' | '));

await browser.close();
server.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
console.log(`screenshots -> ${shotDir}`);
process.exit(failed.length ? 1 : 0);
