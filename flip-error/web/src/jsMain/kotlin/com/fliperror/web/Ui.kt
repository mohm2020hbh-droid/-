package com.fliperror.web

import com.fliperror.core.Award
import com.fliperror.core.Category
import com.fliperror.core.Cosmetic
import com.fliperror.core.Lang
import com.fliperror.core.Payout
import com.fliperror.core.Progress
import com.fliperror.core.Settings
import com.fliperror.core.Shop
import org.w3c.dom.HTMLInputElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

data class LevelCard(val id: Int, val name: String, val coins: Int, val built: Boolean)

/**
 * Everything outside the run: choosing a level, spending coins, the switches,
 * and the panel that pays out afterwards. Plain DOM rather than canvas, because
 * menus want real text, real scrolling and real tap targets in two languages,
 * and because the canvas has one job and it is the one the player came for.
 */
class Ui(
    private val progress: Progress,
    private val settings: Settings,
    private val levels: List<LevelCard>,
    private val onPlay: (Int) -> Unit,
    private val onSave: () -> Unit,
    private val onSettingsChanged: () -> Unit,
    private val onWipe: () -> Unit,
) {
    private val menu = el("menu")
    private val shopEl = el("shop")
    private val settingsEl = el("settings")
    private val reward = el("reward")
    private val modal = el("modal")
    private var shopTab = Category.SHAPE
    private var open = "home"
    private var previewLoop = 0

    private fun el(id: String) = document.getElementById(id) as HTMLElement

    /** NodeList without the extension dance; every hit here is our own markup. */
    private inline fun HTMLElement.each(sel: String, action: (HTMLElement) -> Unit) {
        val list = querySelectorAll(sel)
        for (i in 0 until list.length) action(list.item(i) as HTMLElement)
    }

    private fun t(k: String) = Strings[k]
    private fun coinLine() = "★ ${progress.coins}"

    private fun show(which: String) {
        open = which
        menu.hidden = which != "home" && which != "menu"
        shopEl.hidden = which != "shop"
        settingsEl.hidden = which != "settings"
        reward.hidden = which != "reward"
        if (which != "shop") stopPreviewLoop()
        if (which != "shop") modal.hidden = true
    }

    /** The front door: four ways in, and the purse. */
    fun showHome() { renderHome(); show("home") }
    fun showMenu() { renderMenu(); show("menu") }

    /** The level PLAY goes to: the furthest one that is open. */
    private fun nextLevel(): Int =
        levels.lastOrNull { it.built && progress.unlocked(it.id) }?.id ?: 1

    private fun renderHome() {
        menu.innerHTML = """
            <div class="top"><h1>FLIP ERROR</h1><span class="coins">${coinLine()}</span></div>
            <p class="tag-line">${t("hint")}</p>
            <div class="home">
              <button class="wide go big" id="h-play">${t("play")}</button>
              <button class="wide" id="h-levels">${t("levels")}</button>
              <div class="rowbtns">
                <button class="wide" id="h-shop">${t("shop")}</button>
                <button class="wide ghost" id="h-settings">${t("settings")}</button>
              </div>
            </div>
        """.trimIndent()
        (document.getElementById("h-play") as HTMLElement).addEventListener("click", { onPlay(nextLevel()) })
        (document.getElementById("h-levels") as HTMLElement).addEventListener("click", { showMenu() })
        (document.getElementById("h-shop") as HTMLElement).addEventListener("click", { showShop() })
        (document.getElementById("h-settings") as HTMLElement).addEventListener("click", { showSettings() })
    }
    fun showShop() { renderShop(); show("shop") }
    fun showSettings() { renderSettings(); show("settings") }
    fun hideAll() { show("none"); modal.hidden = true }

    /** Redraw whatever is open. Used when the language or the purse changes. */
    fun refresh() {
        when (open) {
            "home" -> renderHome()
            "menu" -> renderMenu()
            "shop" -> renderShop()
            "settings" -> renderSettings()
        }
    }

    val anyOpen get() = open != "none"

    // --- level select ------------------------------------------------------

    private fun renderMenu() {
        // The list is broken by world, and each world announces itself in its own
        // colours. This IS the transition between them: crossing from LEVEL 6 to
        // LEVEL 7 should look like arriving somewhere, not like scrolling further
        // down one long list.
        var lastWorld = 0
        val cards = levels.joinToString("") { lv ->
            val world = Theme.worldOf(lv.id)
            val banner = if (world == lastWorld) "" else {
                lastWorld = world
                val th = Theme.forLevel(lv.id)
                val reached = progress.unlocked(lv.id)
                """<div class="world${if (reached) "" else " far"}"
                     style="--w1:${th.horizon};--w2:${th.sun};--w3:${th.billboard}">
                  <span class="wn">${t("world")} $world</span>
                  <span class="wt">${t("world$world")}</span>
                </div>"""
            }
            val unlocked = progress.unlocked(lv.id) && lv.built
            val rec = progress.record(lv.id)
            val got = progress.starsIn(lv.id).size
            val state = when {
                !lv.built -> t("soon")
                !progress.unlocked(lv.id) -> t("locked")
                rec.completed -> t("complete")
                else -> t("unlocked")
            }
            val cls = when {
                !lv.built -> "soon"
                !progress.unlocked(lv.id) -> "locked"
                rec.completed -> "complete"
                else -> "unlocked"
            }
            val stars = (0 until lv.coins).joinToString("") {
                if (it < got) "<b class=on>\u2605</b>" else "<b>\u2605</b>"
            }
            banner + """<button class="card" data-level="${lv.id}" ${if (unlocked) "" else "disabled"}>
                 <span class="num">${t("level")} ${lv.id}</span>
                 <span class="nm">${if (lv.built) lv.name else t("notBuilt")}</span>
                 <span class="state s-$cls">$state</span>
                 <span class="stars">$stars</span>
               </button>"""
        }
        menu.innerHTML = """
            <div class="top"><h1>FLIP ERROR</h1><span class="coins">${coinLine()}</span></div>
            <div class="cards">$cards</div>
            <div class="rowbtns">
              <button class="wide ghost" id="to-home">&lsaquo; ${t("back")}</button>
              <button class="wide" id="to-shop">${t("shop")}</button>
              <button class="wide ghost" id="to-settings">${t("settings")}</button>
            </div>
        """.trimIndent()
        menu.each(".card") { b ->
            b.addEventListener("click", { b.dataset["level"]?.toIntOrNull()?.let(onPlay) })
        }
        (document.getElementById("to-home") as HTMLElement).addEventListener("click", { showHome() })
        (document.getElementById("to-shop") as HTMLElement).addEventListener("click", { showShop() })
        (document.getElementById("to-settings") as HTMLElement).addEventListener("click", { showSettings() })
    }

    // --- shop ---------------------------------------------------------------

    private fun renderShop() {
        val tabs = Category.entries.joinToString("") { c ->
            """<button class="tab ${if (c == shopTab) "on" else ""}" data-cat="${c.name}">
                 ${Strings.category(c)}</button>"""
        }
        val grid = Shop.of(shopTab).joinToString("") { item ->
            val owned = progress.owns(item.id)
            val worn = progress.equipped(item.category) == item.id
            // Developer test mode hands you the whole wardrobe to LOOK at. It
            // adds a second button rather than replacing the first: the price is
            // still shown, the item is still unowned, and BUY still costs coins.
            // A test switch that quietly rewrites the economy is a test switch
            // that stops telling you what the real game does.
            val tryBtn = if (!settings.tryAllCosmetics || owned || worn) "" else
                """<button class="buy try" data-try="${item.id}">${t("tryOn")}</button>"""
            val action = when {
                worn -> """<span class="tag worn">${t("equipped")}</span>"""
                owned -> """<button class="buy equip" data-equip="${item.id}">${t("equip")}</button>"""
                progress.coins >= item.price ->
                    """<button class="buy" data-ask="${item.id}">${t("buy")} ${item.price}</button>"""
                else -> """<span class="tag short">★ ${item.price}</span>"""
            } + tryBtn
            """<div class="item ${if (worn) "worn" else ""}">
                 <canvas class="prev" width="112" height="112" data-prev="${item.id}"></canvas>
                 <span class="nm">${item.name}</span>
                 $action
               </div>"""
        }
        shopEl.innerHTML = """
            <div class="top"><button class="back" id="shop-back">&lsaquo; ${t("back")}</button>
                 <span class="coins">${coinLine()}</span></div>
            <div class="tabs">$tabs</div>
            <div class="grid">$grid</div>
        """.trimIndent()

        (document.getElementById("shop-back") as HTMLElement).addEventListener("click", { showHome() })
        shopEl.each(".tab") { b ->
            b.addEventListener("click", {
                shopTab = Category.entries.first { it.name == b.dataset["cat"] }
                renderShop()
            })
        }
        shopEl.each(".buy") { b ->
            b.addEventListener("click", {
                b.dataset["equip"]?.let { id -> progress.equip(id); onSave(); Audio.uiConfirm(); renderShop() }
                b.dataset["try"]?.let { id -> progress.tryOn(id); onSave(); Audio.uiConfirm(); renderShop() }
                b.dataset["ask"]?.let { id -> askToBuy(id) }
            })
        }
        shopEl.each(".prev") { c -> drawPreview(c as HTMLCanvasElement) }
    }

    /**
     * Nothing is bought by a single tap. A cosmetic costs runs, and a misfire on
     * a small phone screen should never spend them.
     */
    private fun askToBuy(id: String) {
        val item = Shop.byId[id] ?: return
        modal.innerHTML = """
            <div class="sheet">
              <h3>${t("purchase")}</h3>
              <canvas class="prev big" width="150" height="150" data-prev="$id"></canvas>
              <div class="row"><span>${t("item")}</span><b>${item.name}</b></div>
              <div class="row"><span>${t("price")}</span><b>★ ${item.price}</b></div>
              <div class="pair">
                <button class="wide ghost" id="m-cancel">${t("cancel")}</button>
                <button class="wide go" id="m-buy">${t("buy")}</button>
              </div>
            </div>
        """.trimIndent()
        modal.hidden = false
        // A card that only shows a still cannot tell you what a trail does, or
        // what the face does on the second jump. The sheet's preview runs the
        // whole loop: run, jump, boost, land.
        modal.each(".prev") { c -> startPreviewLoop(c as HTMLCanvasElement, id) }
        (document.getElementById("m-cancel") as HTMLElement).addEventListener("click", {
            modal.hidden = true
        })
        (document.getElementById("m-buy") as HTMLElement).addEventListener("click", {
            if (progress.buy(id)) { progress.equip(id); Audio.uiConfirm() } else Audio.uiDenied()
            onSave()
            modal.hidden = true
            renderShop()
        })
    }

    private fun stopPreviewLoop() {
        if (previewLoop != 0) { window.cancelAnimationFrame(previewLoop); previewLoop = 0 }
    }

    /**
     * Four seconds of the runner's life, on a loop: running, a jump, a boosted
     * jump, and back. Drawn with the game's own art and the game's own arc, so
     * what the sheet promises is what the level delivers.
     */
    private fun startPreviewLoop(canvas: HTMLCanvasElement, id: String) {
        stopPreviewLoop()
        val started = window.performance.now()
        fun tick() {
            if (modal.hidden) { previewLoop = 0; return }
            val t = (window.performance.now() - started) / 1000.0
            drawPreview(canvas, t)
            previewLoop = window.requestAnimationFrame { tick() }
        }
        tick()
    }

    /** The preview is drawn with the game's own art, so a card cannot lie. */
    private fun drawPreview(canvas: HTMLCanvasElement, time: Double = -1.0) {
        val id = canvas.dataset["prev"] ?: return
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        val item: Cosmetic = Shop.byId[id] ?: return
        val box = canvas.width.toDouble()
        val size = box * 0.50
        ctx.clearRect(0.0, 0.0, box, box)

        // The pose. A still card sits at rest; the live one flies the real arc.
        var lift = 0.0
        var spin = 0.0
        var pose = com.fliperror.core.Face.RUN
        var stretch = 0.0
        if (time >= 0.0) {
            val cycle = time % 4.0
            when {
                cycle < 1.2 -> {                                  // running
                    lift = kotlin.math.abs(kotlin.math.sin(cycle * 7.0)) * size * 0.05
                }
                cycle < 2.2 -> {                                  // a plain jump
                    val u = (cycle - 1.2) / 1.0
                    lift = kotlin.math.sin(u * kotlin.math.PI) * size * 0.55
                    spin = u * 90.0
                    pose = com.fliperror.core.Face.JUMP
                    stretch = kotlin.math.cos(u * kotlin.math.PI) * 0.16
                }
                cycle < 3.6 -> {                                  // jump, then boost
                    val u = (cycle - 2.2) / 1.4
                    lift = kotlin.math.sin(u * kotlin.math.PI) * size * 0.95
                    spin = u * 180.0
                    pose = if (u in 0.34..0.58) com.fliperror.core.Face.DOUBLE
                           else com.fliperror.core.Face.JUMP
                    stretch = if (u in 0.34..0.58) 0.34 else kotlin.math.cos(u * kotlin.math.PI) * 0.20
                }
            }
        }

        ctx.save()
        ctx.translate(box / 2, box / 2 + box * 0.16 - lift)
        val shape = if (item.category == Category.SHAPE) item.id else progress.equipped(Category.SHAPE)
        val colourId = if (item.category == Category.COLOR) item.id else progress.equipped(Category.COLOR)
        val faceId = if (item.category == Category.FACE) item.id else progress.equipped(Category.FACE)
        val colour = Palette.player(colourId)

        // Every live preview shows the trail, not just the trail cards: it is
        // part of what the runner looks like moving.
        val trailId = if (item.category == Category.TRAIL) item.id else progress.equipped(Category.TRAIL)
        if (item.category == Category.TRAIL || time >= 0.0) {
            for (i in 5 downTo 1) {
                val age = 1.0 - i * 0.17
                ctx.save()
                ctx.translate(-i * size * 0.26, i * lift * 0.22)
                ctx.globalAlpha = Art.trailAlpha(trailId, age).coerceIn(0.0, 1.0)
                ctx.strokeStyle = Art.trailColour(trailId, colour, age, i)
                ctx.lineWidth = 2.0
                ctx.rotate(spin * kotlin.math.PI / 180.0)
                Art.shapePath(ctx, shape, size * Art.trailScale(trailId, age))
                ctx.stroke()
                ctx.restore()
            }
            ctx.globalAlpha = 1.0
        }

        val edge = if (pose == com.fliperror.core.Face.DOUBLE) Palette.BOOST else colour
        ctx.save()
        ctx.rotate(spin * kotlin.math.PI / 180.0)
        ctx.scale(1.0 - stretch * 0.7, 1.0 + stretch)
        ctx.shadowBlur = 18.0; ctx.shadowColor = edge
        ctx.fillStyle = Palette.playerFill(colourId)
        ctx.strokeStyle = edge
        ctx.lineWidth = 3.0
        Art.shapePath(ctx, shape, size)
        ctx.fill(); ctx.stroke()
        ctx.shadowBlur = 0.0
        ctx.restore()
        val fs = Art.faceScale(shape)
        if (fs > 0.0) {
            ctx.translate(0.0, Art.faceOffset(shape, size))
            Art.face(ctx, faceId, pose, size * fs, edge)
        }
        ctx.restore()
    }

    // --- settings -------------------------------------------------------------

    private fun toggleRow(key: String, label: String, on: Boolean) = """
        <div class="row set">
          <span>$label</span>
          <button class="sw ${if (on) "on" else ""}" data-toggle="$key"
                  role="switch" aria-checked="$on"><i></i></button>
        </div>"""

    /**
     * A volume, in tenths. Three of these replaced the MUSIC switch, because the
     * thing that switch controlled no longer exists - and a real slider is what
     * "turn the wind down but leave my jump alone" actually needs.
     */
    private fun sliderRow(key: String, label: String, value: Int) = """
        <div class="row set">
          <span>$label</span>
          <span class="vol">
            <input type="range" min="0" max="10" step="1" value="$value"
                   id="vol-$key" data-vol="$key" aria-label="$label">
            <b id="volv-$key">${if (value == 0) "OFF" else "${value * 10}%"}</b>
          </span>
        </div>"""

    private fun renderSettings() {
        settingsEl.innerHTML = """
            <div class="top"><button class="back" id="set-back">&lsaquo; ${t("back")}</button>
                 <span class="coins">${coinLine()}</span></div>
            <div class="rows">
              ${sliderRow("master", t("master"), settings.master)}
              ${sliderRow("sfx", t("sfx"), settings.sfx)}
              ${sliderRow("ambience", t("ambience"), settings.ambience)}
              ${toggleRow("vibration", t("vibration"), settings.vibration)}
              ${toggleRow("reduceEffects", t("reduceEffects"), settings.reduceEffects)}
              ${toggleRow("colorblind", t("colorblind"), settings.colorblind)}
              ${toggleRow("unlockAll", t("unlockAll"), settings.unlockAll)}
              ${toggleRow("tryAll", t("tryAll"), settings.tryAllCosmetics)}
              <div class="row set">
                <span>${t("language")}</span>
                <span class="langs">
                  <button class="lang ${if (settings.lang == Lang.EN) "on" else ""}" data-lang="EN">English</button>
                  <button class="lang ${if (settings.lang == Lang.AR) "on" else ""}" data-lang="AR">العربية</button>
                </span>
              </div>
            </div>
            <button class="wide danger" id="set-reset">${t("reset")}</button>
        """.trimIndent()
        (document.getElementById("set-back") as HTMLElement).addEventListener("click", { showHome() })
        settingsEl.each("input[data-vol]") { el ->
            // 'input' rather than 'change': the room should move under the thumb,
            // because the only way to set a volume is to hear it move.
            el.addEventListener("input", {
                val key = el.dataset["vol"]
                val v = (el as HTMLInputElement).value.toIntOrNull()?.coerceIn(0, 10) ?: 10
                when (key) {
                    "master" -> settings.master = v
                    "sfx" -> settings.sfx = v
                    "ambience" -> settings.ambience = v
                }
                (document.getElementById("volv-$key") as? HTMLElement)?.textContent =
                    if (v == 0) "OFF" else "${v * 10}%"
                onSettingsChanged()
            })
        }
        settingsEl.each(".sw") { b ->
            b.addEventListener("click", {
                when (val k = b.dataset["toggle"]) {
                    "vibration" -> settings.vibration = !settings.vibration
                    "reduceEffects" -> settings.reduceEffects = !settings.reduceEffects
                    "colorblind" -> settings.colorblind = !settings.colorblind
                    "unlockAll" -> settings.unlockAll = !settings.unlockAll
                    "tryAll" -> settings.tryAllCosmetics = !settings.tryAllCosmetics
                    else -> Unit.also { println("unknown toggle $k") }
                }
                onSettingsChanged()
                Audio.uiConfirm()
                renderSettings()
            })
        }
        settingsEl.each(".lang") { b ->
            b.addEventListener("click", {
                settings.lang = if (b.dataset["lang"] == "AR") Lang.AR else Lang.EN
                onSettingsChanged()
                renderSettings()
            })
        }
        (document.getElementById("set-reset") as HTMLElement).addEventListener("click", { askToReset() })
    }

    private fun askToReset() {
        modal.innerHTML = """
            <div class="sheet">
              <h3>${t("resetAsk")}</h3>
              <div class="pair">
                <button class="wide ghost" id="r-cancel">${t("cancel")}</button>
                <button class="wide danger" id="r-go">${t("resetGo")}</button>
              </div>
            </div>
        """.trimIndent()
        modal.hidden = false
        (document.getElementById("r-cancel") as HTMLElement).addEventListener("click", { modal.hidden = true })
        (document.getElementById("r-go") as HTMLElement).addEventListener("click", {
            modal.hidden = true
            onWipe()
        })
    }

    // --- reward --------------------------------------------------------------

    fun showReward(levelId: Int, award: Award, collected: Int, ofCoins: Int, nextBuilt: Boolean) {
        val lines = buildString {
            append("""<div class="row"><span>${t("starCoins")}</span><b>$collected / $ofCoins</b></div>""")
            if (award.newStars > 0) append("""<div class="row new"><span>${t("new")}</span><b>+${award.newStars}</b></div>""")
            if (award.perfect && award.firstClear)
                append("""<div class="row new"><span>${t("perfect")}</span><b>+${Payout.PERFECT}</b></div>""")
            append("""<div class="row total"><span>${t("earned")}</span><b>★ ${award.coins}</b></div>""")
        }
        val next = levelId + 1
        val nextBtn = if (nextBuilt && progress.unlocked(next))
            """<button class="wide go" id="rw-next">${t("nextLevel")}</button>""" else ""
        // Crossing out of a world is the one moment the reward screen should say
        // something other than a number. Finishing LEVEL 6 is not just the next
        // level unlocking - it is the end of the city, and the player should be
        // told where they are going before they are asked to go there.
        val crossing = nextBuilt && progress.unlocked(next) &&
            Theme.worldOf(next) != Theme.worldOf(levelId)
        val gate = if (!crossing) "" else {
            val th = Theme.forLevel(next)
            """<div class="world crossing"
                 style="--w1:${th.horizon};--w2:${th.sun};--w3:${th.billboard}">
              <span class="wn">${t("newWorld")}</span>
              <span class="wt">${t("world" + Theme.worldOf(next))}</span>
            </div>"""
        }
        reward.innerHTML = """
            <div class="panel">
              <h2>${t("levelComplete")}</h2>
              <div class="rows">$lines</div>
              $gate
              $nextBtn
              <button class="wide" id="rw-retry">${t("replay")}</button>
              <button class="wide ghost" id="rw-menu">${t("levels")}</button>
            </div>
        """.trimIndent()
        show("reward")
        document.getElementById("rw-next")?.addEventListener("click", { onPlay(next) })
        (document.getElementById("rw-retry") as HTMLElement).addEventListener("click", { onPlay(levelId) })
        (document.getElementById("rw-menu") as HTMLElement).addEventListener("click", { showMenu() })
    }
}
