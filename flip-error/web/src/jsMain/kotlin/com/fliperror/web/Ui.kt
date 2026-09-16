package com.fliperror.web

import com.fliperror.core.Award
import com.fliperror.core.Category
import com.fliperror.core.Cosmetic
import com.fliperror.core.Progress
import com.fliperror.core.Shop
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

data class LevelCard(val id: Int, val name: String, val coins: Int, val built: Boolean)

/**
 * Everything outside the run: choosing a level, spending coins, and the panel
 * that pays out afterwards. Plain DOM rather than canvas, because menus want
 * real text, real scrolling and real tap targets, and because the canvas has one
 * job and it is the one the player came for.
 */
class Ui(
    private val progress: Progress,
    private val levels: List<LevelCard>,
    private val onPlay: (Int) -> Unit,
    private val onSave: () -> Unit,
) {
    private val menu = el("menu")
    private val shopEl = el("shop")
    private val reward = el("reward")
    private var shopTab = Category.SHAPE

    private fun el(id: String) = document.getElementById(id) as HTMLElement

    /** NodeList without the extension dance; every hit here is our own markup. */
    private inline fun HTMLElement.each(sel: String, action: (HTMLElement) -> Unit) {
        val list = querySelectorAll(sel)
        for (i in 0 until list.length) action(list.item(i) as HTMLElement)
    }

    private fun coinLine() = "★ ${progress.coins}"

    fun showMenu() {
        renderMenu()
        menu.hidden = false; shopEl.hidden = true; reward.hidden = true
    }

    fun showShop() {
        renderShop()
        menu.hidden = true; shopEl.hidden = false; reward.hidden = true
    }

    fun hideAll() { menu.hidden = true; shopEl.hidden = true; reward.hidden = true }

    val anyOpen get() = !menu.hidden || !shopEl.hidden || !reward.hidden

    // --- level select ------------------------------------------------------

    private fun renderMenu() {
        val cards = levels.joinToString("") { lv ->
            val unlocked = progress.unlocked(lv.id) && lv.built
            val rec = progress.record(lv.id)
            val got = progress.starsIn(lv.id).size
            val state = when {
                !lv.built -> "SOON"
                !progress.unlocked(lv.id) -> "LOCKED"
                rec.completed -> "COMPLETE"
                else -> "UNLOCKED"
            }
            val stars = (0 until lv.coins).joinToString("") {
                if (it < got) "<b class=on>★</b>" else "<b>★</b>"
            }
            """<button class="card" data-level="${lv.id}" ${if (unlocked) "" else "disabled"}>
                 <span class="num">LEVEL ${lv.id}</span>
                 <span class="nm">${lv.name}</span>
                 <span class="state s-${state.lowercase()}">$state</span>
                 <span class="stars">$stars</span>
               </button>"""
        }
        menu.innerHTML = """
            <div class="top"><h1>FLIP ERROR</h1><span class="coins">${coinLine()}</span></div>
            <div class="cards">$cards</div>
            <button class="wide" id="to-shop">SHOP</button>
            <p class="hint">Tap anywhere to jump &middot; tap again in the air to boost</p>
        """.trimIndent()
        menu.each(".card") { b ->
            b.addEventListener("click", { b.dataset["level"]?.toIntOrNull()?.let(onPlay) })
        }
        (document.getElementById("to-shop") as HTMLElement).addEventListener("click", { showShop() })
    }

    // --- shop ---------------------------------------------------------------

    private fun renderShop() {
        val tabs = Category.entries.joinToString("") { c ->
            """<button class="tab ${if (c == shopTab) "on" else ""}" data-cat="${c.name}">
                 ${Art.categoryLabel(c)}</button>"""
        }
        val grid = Shop.of(shopTab).joinToString("") { item ->
            val owned = progress.owns(item.id)
            val worn = progress.equipped(item.category) == item.id
            val action = when {
                worn -> """<span class="tag worn">EQUIPPED</span>"""
                owned -> """<button class="buy equip" data-id="${item.id}">EQUIP</button>"""
                progress.coins >= item.price ->
                    """<button class="buy" data-id="${item.id}">BUY ${item.price}</button>"""
                else -> """<span class="tag short">★ ${item.price}</span>"""
            }
            """<div class="item ${if (worn) "worn" else ""}">
                 <canvas class="prev" width="112" height="112" data-prev="${item.id}"></canvas>
                 <span class="nm">${item.name}</span>
                 $action
               </div>"""
        }
        shopEl.innerHTML = """
            <div class="top"><button class="back" id="shop-back">&lsaquo; BACK</button>
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
                val id = b.dataset["id"]
                if (id != null) {
                    val bought = if (progress.owns(id)) true else progress.buy(id)
                    if (bought) { progress.equip(id); Audio.uiConfirm() } else Audio.uiDenied()
                    onSave()
                    renderShop()
                }
            })
        }
        shopEl.each(".prev") { c -> drawPreview(c as HTMLCanvasElement) }
    }

    /** The preview is drawn with the game's own art, so a card cannot lie. */
    private fun drawPreview(canvas: HTMLCanvasElement) {
        val id = canvas.dataset["prev"] ?: return
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        val item: Cosmetic = Shop.byId[id] ?: return
        val size = 58.0
        ctx.clearRect(0.0, 0.0, 112.0, 112.0)
        ctx.save()
        ctx.translate(56.0, 56.0)
        val shape = if (item.category == Category.SHAPE) item.id else progress.equipped(Category.SHAPE)
        val colourId = if (item.category == Category.COLOR) item.id else progress.equipped(Category.COLOR)
        val faceId = if (item.category == Category.FACE) item.id else progress.equipped(Category.FACE)
        val colour = Palette.player(colourId)

        if (item.category == Category.TRAIL) {
            for (i in 4 downTo 1) {
                val age = 1.0 - i * 0.2
                ctx.save()
                ctx.translate(-i * 15.0, 0.0)
                ctx.globalAlpha = Art.trailAlpha(item.id, age).coerceIn(0.0, 1.0)
                ctx.strokeStyle = Art.trailColour(item.id, colour, age, i)
                ctx.lineWidth = 2.0
                Art.shapePath(ctx, shape, size * Art.trailScale(item.id, age))
                ctx.stroke()
                ctx.restore()
            }
            ctx.globalAlpha = 1.0
        }

        ctx.shadowBlur = 16.0; ctx.shadowColor = colour
        ctx.fillStyle = Palette.playerFill(colourId)
        ctx.strokeStyle = colour
        ctx.lineWidth = 3.0
        Art.shapePath(ctx, shape, size)
        ctx.fill(); ctx.stroke()
        ctx.shadowBlur = 0.0
        ctx.translate(0.0, Art.faceOffset(shape, size))
        Art.face(ctx, faceId, com.fliperror.core.Face.RUN, size, colour)
        ctx.restore()
    }

    // --- reward --------------------------------------------------------------

    fun showReward(levelId: Int, award: Award, collected: Int, ofCoins: Int, nextBuilt: Boolean) {
        val lines = buildString {
            append("""<div class="row"><span>STAR COINS</span><b>$collected / $ofCoins</b></div>""")
            if (award.newStars > 0) append("""<div class="row new"><span>NEW</span><b>+${award.newStars}</b></div>""")
            if (award.perfect && award.firstClear) append("""<div class="row new"><span>PERFECT RUN</span><b>+${com.fliperror.core.Payout.PERFECT}</b></div>""")
            append("""<div class="row total"><span>EARNED</span><b>★ ${award.coins}</b></div>""")
        }
        val next = levelId + 1
        val nextBtn = if (nextBuilt && progress.unlocked(next))
            """<button class="wide go" id="rw-next">NEXT LEVEL</button>""" else ""
        reward.innerHTML = """
            <div class="panel">
              <h2>LEVEL COMPLETE</h2>
              <div class="rows">$lines</div>
              $nextBtn
              <button class="wide" id="rw-retry">REPLAY</button>
              <button class="wide ghost" id="rw-menu">LEVELS</button>
            </div>
        """.trimIndent()
        menu.hidden = true; shopEl.hidden = true; reward.hidden = false
        document.getElementById("rw-next")?.addEventListener("click", { onPlay(next) })
        (document.getElementById("rw-retry") as HTMLElement).addEventListener("click", { onPlay(levelId) })
        (document.getElementById("rw-menu") as HTMLElement).addEventListener("click", { showMenu() })
    }
}
