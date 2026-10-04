/** Stylesheet for HUD + menus (landscape-first, safe-area aware, rem scaled to the screen HEIGHT). */
export const CSS = `
@font-face{font-family:"Baloo 2";src:url(fonts/baloo-2-latin-600.woff2) format("woff2");font-weight:600;font-display:swap;unicode-range:U+0000-024F,U+2000-206F,U+20AC,U+2122,U+2190-21FF}
@font-face{font-family:"Baloo 2";src:url(fonts/baloo-2-latin-800.woff2) format("woff2");font-weight:700 900;font-display:swap;unicode-range:U+0000-024F,U+2000-206F,U+20AC,U+2122,U+2190-21FF}
@font-face{font-family:"Baloo Bhaijaan 2";src:url(fonts/baloo-bhaijaan-2-arabic-600.woff2) format("woff2");font-weight:600;font-display:swap;unicode-range:U+0600-06FF,U+0750-077F,U+08A0-08FF,U+FB50-FDFF,U+FE70-FEFF}
@font-face{font-family:"Baloo Bhaijaan 2";src:url(fonts/baloo-bhaijaan-2-arabic-800.woff2) format("woff2");font-weight:700 900;font-display:swap;unicode-range:U+0600-06FF,U+0750-077F,U+08A0-08FF,U+FB50-FDFF,U+FE70-FEFF}
:root{
  --sl:max(env(safe-area-inset-left,0px),var(--nl,0px));--sr:max(env(safe-area-inset-right,0px),var(--nr,0px));--st:max(env(safe-area-inset-top,0px),var(--nt,0px));--sb:max(env(safe-area-inset-bottom,0px),var(--nb,0px));
  --ink:#10142a;--panel:rgba(16,20,38,.64);--panel2:rgba(16,20,38,.82);--line:rgba(255,255,255,.14);
  --glass:linear-gradient(180deg,rgba(34,40,74,.78),rgba(14,18,40,.7));--glass-edge:inset 0 1px 0 rgba(255,255,255,.18),inset 0 -1px 0 rgba(0,0,0,.25),0 .35rem 1.1rem rgba(8,10,30,.35);
  --accent:#ff9a2e;--accent2:#ffd24a;--teal:#2fd0c4;--danger:#ff5a4a;
  font-size:clamp(13px,3.3vh,26px);
}
html,body{font-family:"Baloo 2","Baloo Bhaijaan 2",ui-rounded,"SF Pro Rounded","Nunito",system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans Arabic",sans-serif;color:#fff;font-weight:700;-webkit-font-smoothing:antialiased}
#ui *{box-sizing:border-box}
:where(#ui button){font:inherit;color:inherit;border:0;background:none;cursor:pointer;touch-action:manipulation;-webkit-tap-highlight-color:transparent}
.ico{display:inline-grid;place-items:center;line-height:0}
.ico svg{width:1em;height:1em}
.panel{background:var(--glass);border:1px solid var(--line);border-radius:1.1rem;box-shadow:var(--glass-edge);padding:.45rem .9rem}

/* ── HUD ─────────────────────────────── */
.hud{position:absolute;inset:0;pointer-events:none;padding:max(.8rem,var(--st)) max(.8rem,var(--sr)) max(.8rem,var(--sb)) max(.8rem,var(--sl));text-shadow:0 .08rem .18rem rgba(0,0,0,.35)}
.hud .statcard{position:absolute;left:max(.8rem,var(--sl));top:max(.8rem,var(--st));background:var(--glass);border-radius:1.15rem;box-shadow:var(--glass-edge);padding:.5rem .8rem .55rem;display:flex;flex-direction:column;gap:.35rem;min-width:9.6rem}
.hud .timer{display:flex;align-items:center;gap:.5rem;font-size:1.65rem;line-height:1;font-weight:800;font-variant-numeric:tabular-nums;letter-spacing:.01em}
.hud .timer .ico{font-size:1.15rem;color:#ffe7a8;opacity:.95}
.hud .chips{display:flex;gap:.3rem}
.hud .chip{display:flex;align-items:center;gap:.28rem;padding:.12rem .5rem .12rem .38rem;border-radius:.8rem;background:rgba(255,255,255,.08);font-size:.95rem;line-height:1.35;font-variant-numeric:tabular-nums}
.hud .chip b{font-weight:800}
.hud .chip small{font-size:.7rem;opacity:.75;margin-inline-start:.08rem;font-weight:700}
.hud .chip .ico{font-size:.95rem}
.hud .c-h .ico{color:#7fe3d6}.hud .c-j .ico{color:#ffb070}.hud .c-b .ico{color:#ffd24a}
.hud .top-mid{position:absolute;left:50%;top:max(.8rem,var(--st));transform:translateX(-50%);display:flex;flex-direction:column;align-items:center;gap:.3rem}
.hud .prog{display:flex;align-items:center;gap:.35rem}
.hud .prog .track{position:relative;width:min(30vw,16rem);height:.55rem;border-radius:1rem;background:rgba(10,14,34,.55);box-shadow:inset 0 1px 2px rgba(0,0,0,.45),0 0 0 1px rgba(255,255,255,.12);overflow:hidden}
.hud .prog i{display:block;height:100%;width:0;background:linear-gradient(90deg,#2fd0c4,#a8e06a 55%,#ffd24a);border-radius:1rem;box-shadow:0 0 .6rem rgba(255,210,74,.55);transition:width .25s ease-out}
.hud .goalflag{font-size:1rem;color:#ffd24a;filter:drop-shadow(0 .05rem .1rem rgba(0,0,0,.5))}
.hud .pct{font-size:.78rem;line-height:1;padding:.18rem .6rem;border-radius:1rem;background:rgba(14,18,40,.5);letter-spacing:.04em}
.hud .hint{display:flex;align-items:center;gap:.5rem;max-width:min(64vw,32rem);font-size:.98rem;line-height:1.25;padding:.42rem 1rem .42rem .55rem;border-radius:1.2rem;background:rgba(255,250,235,.92);color:#2a1f3a;text-shadow:none;box-shadow:0 .3rem 1rem rgba(10,10,30,.3);opacity:0;transform:translateY(-.4rem) scale(.98);transition:opacity .35s,transform .35s;margin-top:.3rem}
.hud .hint .hi{font-size:1.25rem;color:#ff8a2e}
.hud .hint.show{opacity:1;transform:none}
.circle-btn{pointer-events:auto;position:relative;width:var(--s,4.4rem);height:var(--s,4.4rem);border-radius:50%;border:.16rem solid rgba(255,255,255,.85);background:var(--glass);color:#fff;display:grid;place-items:center;font-size:calc(var(--s,4.4rem)*.44);box-shadow:var(--glass-edge);transition:transform .08s,box-shadow .2s,border-color .2s,opacity .2s;touch-action:none}
.circle-btn.down{transform:scale(.93);background:linear-gradient(180deg,rgba(255,170,70,.8),rgba(255,120,40,.7))}
.btn-wrap{position:absolute;display:flex;flex-direction:column;align-items:center;gap:.3rem;pointer-events:none}
.btn-label{font-size:.8rem;padding:.05rem .65rem;border-radius:1rem;background:rgba(14,18,40,.62);box-shadow:inset 0 1px 0 rgba(255,255,255,.12);letter-spacing:.02em}
.hud .pause{position:absolute;right:max(.8rem,var(--sr));top:max(.8rem,var(--st));--s:3.2rem}
.hud .boost-wrap{right:max(1rem,var(--sr));bottom:max(.9rem,var(--sb));pointer-events:none}
.hud .boost{--s:5.2rem;opacity:.72}
.hud .boost .chev{position:relative;z-index:1}
.hud .boost .meter{position:absolute;inset:-.16rem;width:calc(100% + .32rem);height:calc(100% + .32rem);transform:rotate(-90deg);pointer-events:none}
.hud .boost .meter circle{fill:none;stroke-width:5;stroke-linecap:round}
.hud .boost .meter .bg{stroke:rgba(255,255,255,.12)}
.hud .boost .meter .fg{stroke:#ffd24a;stroke-dasharray:277;stroke-dashoffset:277;transition:stroke-dashoffset .3s}
.hud .boost .count{position:absolute;right:-.25rem;top:-.25rem;min-width:1.45rem;height:1.45rem;border-radius:1rem;background:linear-gradient(180deg,#ffb347,#ff8a2e);color:#3a1800;font-size:.85rem;font-weight:800;display:grid;place-items:center;padding:0 .3rem;box-shadow:0 .1rem .3rem rgba(0,0,0,.35);text-shadow:none;z-index:2}
.hud .boost.ready{opacity:1;border-color:var(--accent2);box-shadow:0 0 1.6rem rgba(255,210,74,.75),var(--glass-edge);animation:pulse 1s ease-in-out infinite}
.hud .boost.ready .meter .fg{stroke-dashoffset:0;filter:drop-shadow(0 0 .25rem rgba(255,210,74,.9))}
.hud .boost.queued{background:linear-gradient(180deg,rgba(255,170,70,.75),rgba(255,120,40,.65))}
@keyframes pulse{50%{transform:scale(1.06)}}
.hud .jump-wrap{right:calc(max(1rem,var(--sr)) + 6.4rem);bottom:max(.9rem,var(--sb))}
.hud .jump{--s:4.6rem}
.hud .stick-wrap{left:max(1.4rem,var(--sl));bottom:max(1rem,var(--sb))}
.hud.lefty .boost-wrap{right:auto;left:max(1rem,var(--sl))}
.hud.lefty .jump-wrap{right:auto;left:calc(max(1rem,var(--sl)) + 6.4rem)}
.hud.lefty .stick-wrap{left:auto;right:max(1.4rem,var(--sr))}
.stick-zone{pointer-events:auto;position:relative;width:9.4rem;height:9.4rem;border-radius:50%;border:.16rem solid rgba(255,255,255,.7);background:radial-gradient(circle,rgba(34,40,74,.45),rgba(14,18,40,.7));box-shadow:var(--glass-edge);touch-action:none;display:grid;place-items:center}
.stick-zone .arrows{position:absolute;inset:0;display:flex;justify-content:space-between;align-items:center;padding:0 .7rem;font-size:1.5rem;opacity:.75}
.stick-zone .knob{width:4.2rem;height:4.2rem;border-radius:50%;background:radial-gradient(circle at 35% 30%,rgba(255,255,255,.6),rgba(255,255,255,.18));border:.15rem solid rgba(255,255,255,.75);box-shadow:0 .2rem .6rem rgba(0,0,0,.3);transition:transform .05s}
.hud.drag .pad-only{display:none}
.hud.pad .drag-hint{display:none}
.charge-ring{position:absolute;width:4.6rem;height:4.6rem;margin:-2.3rem 0 0 -2.3rem;pointer-events:none;display:none;transform:translate(-9999px,-9999px)}
.charge-ring svg{width:100%;height:100%;transform:rotate(-90deg)}
.charge-ring circle{fill:none;stroke-width:6;stroke-linecap:round}
.charge-ring .bg{stroke:rgba(255,255,255,.22)}
.charge-ring .fg{stroke:var(--accent2);stroke-dasharray:289;stroke-dashoffset:289;filter:drop-shadow(0 0 4px rgba(255,210,74,.9))}
.vignette{position:absolute;inset:0;pointer-events:none;background:radial-gradient(ellipse at center,transparent 62%,rgba(20,10,30,.22) 100%)}
.flash{position:absolute;inset:0;pointer-events:none;opacity:0;background:radial-gradient(ellipse at center,transparent 30%,rgba(255,90,74,.7));transition:opacity .35s}
.flash.on{opacity:1;transition:none}
.toast{position:absolute;left:50%;top:24%;transform:translate(-50%,-.5rem);padding:.4rem 1.2rem;font-size:1.25rem;font-weight:800;opacity:0;transition:opacity .3s,transform .3s;pointer-events:none;border-radius:1.3rem;background:var(--glass);box-shadow:var(--glass-edge)}
.toast.show{opacity:1;transform:translate(-50%,0)}

/* ── Screens ─────────────────────────── */
.screen{position:absolute;inset:0;pointer-events:auto;display:flex;padding:max(1rem,var(--st)) max(1.2rem,var(--sr)) max(1rem,var(--sb)) max(1.2rem,var(--sl));animation:fade .25s ease-out;overflow:hidden}
@keyframes fade{from{opacity:0}}
.screen.dim{background:rgba(10,12,28,.62)}
.screen.menu{background:linear-gradient(90deg,rgba(14,16,40,.78),rgba(14,16,40,.25) 55%,transparent)}
.logo{font-size:3.1rem;line-height:.95;font-weight:900;letter-spacing:.02em;text-transform:uppercase;background:linear-gradient(180deg,#fff7d6,#ffc44a 55%,#ff8a2e);-webkit-background-clip:text;background-clip:text;color:transparent;filter:drop-shadow(0 .25rem 0 rgba(120,40,0,.55)) drop-shadow(0 .5rem 1rem rgba(0,0,0,.45))}
.logo small{display:block;font-size:1rem;letter-spacing:.35em;color:#fff;-webkit-text-fill-color:#fff;opacity:.9;margin-top:.5rem;filter:none}
.col{display:flex;flex-direction:column}
.menu-col{justify-content:center;gap:.7rem;min-width:min(34vw,19rem)}
.btn{display:flex;align-items:center;gap:.7rem;min-height:3.2rem;padding:.55rem 1.3rem;border-radius:1.6rem;background:var(--panel2);border:1px solid var(--line);font-size:1.15rem;font-weight:800;box-shadow:0 .25rem 0 rgba(0,0,0,.35);transition:transform .08s,background .15s}
.btn:active{transform:translateY(.12rem);box-shadow:0 .1rem 0 rgba(0,0,0,.35)}
.btn .ico{font-size:1.35rem}
.btn.primary{background:linear-gradient(180deg,#ffb347,#ff8a2e);color:#3a1800;border-color:#ffd9a0;box-shadow:0 .3rem 0 #b4560f,0 .5rem 1.2rem rgba(255,138,46,.4);font-size:1.45rem;min-height:3.8rem}
.btn.teal{background:linear-gradient(180deg,#3fd0c8,#1fb0a8);color:#022b29;border-color:#a9f2ec;box-shadow:0 .3rem 0 #0f6e69}
.btn.round{width:3.2rem;height:3.2rem;padding:0;justify-content:center;border-radius:50%}
.btn[disabled]{opacity:.45;pointer-events:none}
.head{display:flex;align-items:center;gap:.9rem;margin-bottom:.8rem}
.head h2{margin:0;font-size:1.7rem;font-weight:900;letter-spacing:.03em}
.head .spacer{flex:1}
.scroll{overflow:auto;-webkit-overflow-scrolling:touch;scrollbar-width:thin}
.grow{flex:1;min-height:0}
.cards{display:flex;gap:.9rem;align-items:stretch}
.card{position:relative;flex:0 0 auto;width:12.5rem;border-radius:1.2rem;overflow:hidden;border:2px solid rgba(255,255,255,.22);box-shadow:0 .4rem 1rem rgba(0,0,0,.35);display:flex;flex-direction:column;justify-content:flex-end;padding:.9rem;text-align:start;min-height:11rem;transition:transform .1s}
.card:active{transform:scale(.97)}
.card .t{font-size:1.25rem;font-weight:900;text-shadow:0 .1rem .3rem rgba(0,0,0,.6)}
.card .s{font-size:.85rem;opacity:.9;text-shadow:0 .1rem .3rem rgba(0,0,0,.6)}
.card.lock{filter:saturate(.35) brightness(.7)}
.card .lockmark{position:absolute;right:.7rem;top:.7rem;font-size:1.5rem}
.stars{display:inline-flex;gap:.15rem;color:#5b6074}
.stars .on{color:var(--accent2);filter:drop-shadow(0 0 .25rem rgba(255,210,74,.7))}
.sheet{background:var(--panel2);border:1px solid var(--line);border-radius:1.4rem;padding:1rem 1.3rem}
.row{display:flex;align-items:center;gap:.8rem;justify-content:space-between;padding:.45rem 0;border-bottom:1px solid rgba(255,255,255,.07)}
.row:last-child{border-bottom:0}
.row label{font-size:1rem;opacity:.95;flex:0 0 40%}
.row .val{min-width:3rem;text-align:end;opacity:.8;font-variant-numeric:tabular-nums}
input[type=range]{-webkit-appearance:none;appearance:none;flex:1;height:.5rem;border-radius:1rem;background:rgba(255,255,255,.2);outline:none;min-width:8rem;touch-action:pan-x}
input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;width:1.7rem;height:1.7rem;border-radius:50%;background:var(--accent);border:3px solid #fff;box-shadow:0 .1rem .4rem rgba(0,0,0,.4)}
.seg{display:inline-flex;background:rgba(255,255,255,.1);border-radius:1.2rem;padding:.2rem;gap:.2rem}
.seg button{padding:.35rem .9rem;border-radius:1rem;font-size:.95rem}
.seg button.on{background:var(--accent);color:#3a1800}
.tabs{display:flex;gap:.45rem;margin-bottom:.7rem;flex-wrap:wrap}
.tabs button{padding:.4rem 1rem;border-radius:1.2rem;background:rgba(255,255,255,.1);font-size:1rem}
.tabs button.on{background:var(--accent);color:#3a1800}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(7.3rem,1fr));gap:.7rem}
.item{position:relative;border-radius:1rem;background:rgba(255,255,255,.08);border:2px solid transparent;padding:.7rem .4rem;text-align:center;display:flex;flex-direction:column;align-items:center;gap:.35rem;font-size:.9rem}
.item.sel{border-color:var(--accent2);background:rgba(255,210,74,.14)}
.item.lock{opacity:.55}
.item .sw{width:2.6rem;height:2.6rem;border-radius:50%;border:2px solid rgba(255,255,255,.6)}
.item small{opacity:.75;font-size:.72rem}
.rar-common{box-shadow:inset 0 0 0 1px rgba(255,255,255,.1)}.rar-rare{box-shadow:inset 0 0 0 2px #4cc3ff}.rar-epic{box-shadow:inset 0 0 0 2px #c77bff}
table.lb{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}
table.lb th,table.lb td{padding:.45rem .6rem;text-align:start;border-bottom:1px solid rgba(255,255,255,.08)}
table.lb th{opacity:.7;font-size:.85rem}
.steps{counter-reset:s;display:grid;grid-template-columns:repeat(3,1fr);gap:.8rem}
.step{counter-increment:s;background:rgba(255,255,255,.08);border-radius:1.1rem;padding:.9rem;font-size:1rem;position:relative}
.step:before{content:counter(s);position:absolute;left:-.4rem;top:-.5rem;width:1.8rem;height:1.8rem;border-radius:50%;background:var(--accent);color:#3a1800;display:grid;place-items:center;font-weight:900}
.step .demo{height:5.2rem;border-radius:.8rem;background:linear-gradient(#8fb2f0,#d8d4f0);margin-bottom:.6rem;position:relative;overflow:hidden}
.center{align-items:center;justify-content:center}
.results{align-items:center;justify-content:center}
.results .sheet{min-width:min(60vw,26rem);text-align:center}
.results h1{margin:.2rem 0;font-size:2rem;font-weight:900;color:var(--accent2);text-shadow:0 .15rem 0 rgba(120,60,0,.6)}
.results .big{font-size:2.6rem;font-weight:900;font-variant-numeric:tabular-nums}
.results .stars{font-size:2.4rem;justify-content:center;margin:.3rem 0}
.results .kv{display:flex;justify-content:space-around;margin:.6rem 0 1rem;font-size:1rem}
.results .kv b{display:block;font-size:1.4rem}
.btnrow{display:flex;gap:.7rem;justify-content:center;flex-wrap:wrap}
.splash{background:radial-gradient(ellipse at 50% 40%,#28305e,#0d1022);align-items:center;justify-content:center;flex-direction:column;gap:1.2rem;z-index:50}
.lab-panel{position:absolute;right:max(.6rem,var(--sr));top:calc(max(.6rem,var(--st)) + 4rem);width:min(34vw,19rem);max-height:66vh;overflow:auto;pointer-events:auto;font-size:.8rem;padding:.7rem;font-weight:600}
.lab-readout{position:absolute;left:max(.6rem,var(--sl));bottom:max(.6rem,var(--sb));max-width:46vw;overflow:hidden;pointer-events:none;font:600 .66rem ui-monospace,Menlo,monospace;line-height:1.3;padding:.4rem .6rem;white-space:pre}
.lab-bar{position:absolute;right:max(6.5rem,var(--sr));bottom:max(.6rem,var(--sb));display:flex;gap:.35rem;pointer-events:auto;flex-wrap:wrap;justify-content:flex-end;max-width:46vw}
.lab-bar .btn{min-height:2.1rem;padding:.25rem .7rem;font-size:.78rem;border-radius:1.2rem}
.lab-panel .p{display:grid;grid-template-columns:1fr auto;gap:.1rem .5rem;margin-bottom:.5rem}
.lab-panel .p .n{font-weight:800}
.lab-panel .p .tag{font-size:.65rem;padding:0 .35rem;border-radius:.5rem;background:#ffb347;color:#3a1800;margin-inline-start:.3rem}
.lab-panel .p .tag.src{background:#4cc3ff}
.lab-panel .p .tag.des{background:#c77bff}
.lab-panel h4{margin:.6rem 0 .3rem;font-size:.8rem;letter-spacing:.1em;text-transform:uppercase;opacity:.7}
`;
