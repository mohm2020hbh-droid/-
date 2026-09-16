package com.fliperror.web

import com.fliperror.core.Award
import com.fliperror.core.Category
import com.fliperror.core.Cosmetic
import com.fliperror.core.Lang
import com.fliperror.core.Payout
import com.fliperror.core.Progress
import com.fliperror.core.Settings
import com.fliperror.core.Shop
import kotlinx.browser.document
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
    private var open = "menu"

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
        menu.hidden = which != "menu"
        shopEl.hidden = which != "shop"
        settingsEl.hidden = which != "settings"
        reward.hidden = which != "reward"
        if (which != "shop") modal.hidden = true
    }

    fun showMenu() { renderMenu(); show("menu") }
    fun showShop() { renderShop(); show("shop") }
    fun showSettings() { renderSettings(); show("settings") }
    fun hideAll() { show("none"); modal.hidden = true }

    /** Redraw whatever is open. Used when the language or the purse changes. */
    fun refresh() {
        when (open) {
            "menu" -> renderMenu()
            "shop" -> renderShop()
            "settings" -> renderSettings()
        }
    }

    val anyOpen get() = open != "none"

    // --- level select ------------------------------------------------------

    private fun renderMenu() {
        val cards = levels.joinToString("") { lv ->
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
                if (it < got) "<b class=on>★</b>" else "<b>★</b>"
            }
            """<button class="card" data-level="${lv.id}" ${if (unlocked) "" else "disabled"}>
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
              <button class="wide" id="to-shop">${t("shop")}</button>
              <button class="wide ghost" id="to-settings">${t("settings")}</button>
            </div>
            <p class="hint">${t("hint")}</p>
        """.trimIndent()
        menu.each(".card") { b ->
            b.addEventListener("click", { b.dataset["level"]?.toIntOrNull()?.let(onPlay) })
        }
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
            val action = when {
                worn -> """<span class="tag worn">${t("equipped")}</span>"""
                owned -> """<button class="buy equip" data-equip="${item.id}">${t("equip")}</button>"""
                progress.coins >= item.price ->
                    """<button class="buy" data-ask="${item.id}">${t("buy")} ${item.price}</button>"""
                else -> """<span class="tag short">★ ${item.price}</span>"""
            }
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

        (document.getElementById("shop-back") as HTMLElement).addEventListener("click", { showMenu() })
        shopEl.each(".tab") { b ->
            b.addEventListener("click", {
                shopTab = Category.entries.first { it.name == b.dataset["cat"] }
                renderShop()
            })
        }
        shopEl.each(".buy") { b ->
            b.addEventListener("click", {
                b.dataset["equip"]?.let { id -> progress.equip(id); onSave(); Audio.uiConfirm(); renderShop() }
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
        modal.each(".prev") { c -> drawPreview(c as HTMLCanvasElement) }
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

    /** The preview is drawn with the game's own art, so a card cannot lie. */
    private fun drawPreview(canvas: HTMLCanvasElement) {
        val id = canvas.dataset["prev"] ?: return
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        val item: Cosmetic = Shop.byId[id] ?: return
        val box = canvas.width.toDouble()
        val size = box * 0.50
        ctx.clearRect(0.0, 0.0, box, box)
        ctx.save()
        ctx.translate(box / 2, box / 2)
        val shape = if (item.category == Category.SHAPE) item.id else progress.equipped(Category.SHAPE)
        val colourId = if (item.category == Category.COLOR) item.id else progress.equipped(Category.COLOR)
        val faceId = if (item.category == Category.FACE) item.id else progress.equipped(Category.FACE)
        val colour = Palette.player(colourId)

        if (item.category == Category.TRAIL) {
            for (i in 4 downTo 1) {
                val age = 1.0 - i * 0.2
                ctx.save()
                ctx.translate(-i * size * 0.26, 0.0)
                ctx.globalAlpha = Art.trailAlpha(item.id, age).coerceIn(0.0, 1.0)
                ctx.strokeStyle = Art.trailColour(item.id, colour, age, i)
                ctx.lineWidth = 2.0
                Art.shapePath(ctx, shape, size * Art.trailScale(item.id, age))
                ctx.stroke()
                ctx.restore()
            }
            ctx.globalAlpha = 1.0
        }

        ctx.shadowBlur = 18.0; ctx.shadowColor = colour
        ctx.fillStyle = Palette.playerFill(colourId)
        ctx.strokeStyle = colour
        ctx.lineWidth = 3.0
        Art.shapePath(ctx, shape, size)
        ctx.fill(); ctx.stroke()
        ctx.shadowBlur = 0.0
        val fs = Art.faceScale(shape)
        if (fs > 0.0) {
            ctx.translate(0.0, Art.faceOffset(shape, size))
            Art.face(ctx, faceId, com.fliperror.core.Face.RUN, size * fs, colour)
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

    private fun renderSettings() {
        settingsEl.innerHTML = """
            <div class="top"><button class="back" id="set-back">&lsaquo; ${t("back")}</button>
                 <span class="coins">${coinLine()}</span></div>
            <div class="rows">
              ${toggleRow("music", t("music"), settings.music)}
              ${toggleRow("sfx", t("sfx"), settings.sfx)}
              ${toggleRow("vibration", t("vibration"), settings.vibration)}
              ${toggleRow("reduceEffects", t("reduceEffects"), settings.reduceEffects)}
              ${toggleRow("colorblind", t("colorblind"), settings.colorblind)}
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
        (document.getElementById("set-back") as HTMLElement).addEventListener("click", { showMenu() })
        settingsEl.each(".sw") { b ->
            b.addEventListener("click", {
                when (val k = b.dataset["toggle"]) {
                    "music" -> settings.music = !settings.music
                    "sfx" -> settings.sfx = !settings.sfx
                    "vibration" -> settings.vibration = !settings.vibration
                    "reduceEffects" -> settings.reduceEffects = !settings.reduceEffects
                    "colorblind" -> settings.colorblind = !settings.colorblind
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
        reward.innerHTML = """
            <div class="panel">
              <h2>${t("levelComplete")}</h2>
              <div class="rows">$lines</div>
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
