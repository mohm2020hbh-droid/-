package com.carom.game.screens

import android.graphics.Canvas
import android.view.MotionEvent
import com.carom.core.level.Worlds
import com.carom.game.ui.Icon
import com.carom.game.ui.Icons
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import com.carom.game.ui.WorldPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * One page per world, each holding that world's 10 levels (2 columns × 5 rows) in the world's own
 * two colours. Swiping slides the colours from one world to the next. Completed levels are filled
 * like the ball, the next level to play is ringed in the contrast colour, locked levels are dimmed.
 */
class LevelSelectScreen(host: GameHost, focusIndex: Int) : Screen(host) {

    private val levelCount = host.app.levels.size
    private val progress = host.app.progress
    private val worldCount = max(1, Worlds.count(levelCount))
    private var page = Worlds.worldOf(focusIndex.coerceAtLeast(0)).coerceIn(0, worldCount - 1)

    /** Page position being shown; follows the finger while dragging, then eases to [page]. */
    private var scroll = page.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragged = false

    private val backButton = UiButton(UiButton.Style.ICON, icon = Icon.BACK) { host.showHome() }
    private val prevButton = UiButton(UiButton.Style.ICON, icon = Icon.BACK) { goTo(page - 1) }
    private val nextButton = UiButton(UiButton.Style.ICON, icon = Icon.FORWARD) { goTo(page + 1) }
    private val buttons = listOf(backButton, prevButton, nextButton)

    private var headerY = 0f
    private var dotsY = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var ringRadius = 0f

    /** Colours slide between worlds as the pages move. */
    override val palette: WorldPalette
        get() {
            val first = floor(scroll).toInt().coerceIn(0, worldCount - 1)
            val next = (first + 1).coerceAtMost(worldCount - 1)
            return Palette.lerp(Palette.forWorld(first), Palette.forWorld(next), scroll - first)
        }

    override fun onLayout() {
        headerY = safe.top + kit.u(44f)
        dotsY = safe.bottom - kit.u(40f)
        val gridW = min(safe.width() - kit.u(80f), kit.u(260f))
        gridTop = headerY + kit.u(56f)
        val gridH = dotsY - kit.u(36f) - gridTop
        cellW = gridW / COLUMNS
        cellH = gridH / ROWS
        gridLeft = safe.left + (safe.width() - gridW) / 2
        ringRadius = min(min(cellW, cellH) * 0.36f, kit.u(40f))
        val b = kit.u(48f)
        backButton.setCenter(safe.left + kit.u(32f), headerY, b, b)
        prevButton.setCenter(width / 2 - kit.u(96f), dotsY, b, b)
        nextButton.setCenter(width / 2 + kit.u(96f), dotsY, b, b)
        updateArrows()
    }

    /** Screen position of level [i]'s cell on its world's page (for tests). */
    internal fun cellCenter(i: Int): Pair<Float, Float> {
        val slot = i % Worlds.SIZE
        return (gridLeft + (slot % COLUMNS + 0.5f) * cellW) to (gridTop + (slot / COLUMNS + 0.5f) * cellH)
    }

    private fun goTo(target: Int) {
        page = target.coerceIn(0, worldCount - 1)
        updateArrows()
    }

    private fun updateArrows() {
        prevButton.visible = page > 0
        nextButton.visible = page < worldCount - 1
    }

    override fun update(dt: Float) {
        if (!dragging) scroll += (page - scroll) * (1f - exp(-dt * 16f))
        if (abs(scroll - page) < 0.001f && !dragging) scroll = page.toFloat()
    }

    override val isAnimating: Boolean get() = scroll != page.toFloat() && !dragging

    override fun onTouch(e: MotionEvent): Boolean {
        if (!dragging && routeToButtons(e, buttons)) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = true
                dragged = false
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val dx = e.x - downX
                if (abs(dx) > kit.u(8f)) dragged = true
                if (dragged) scroll = (page - dx / width).coerceIn(-0.15f, worldCount - 0.85f)
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                dragging = false
                val dx = e.x - downX
                if (dragged) {
                    when {
                        dx < -width * 0.15f -> goTo(page + 1)
                        dx > width * 0.15f -> goTo(page - 1)
                    }
                } else {
                    levelAt(e.x, e.y)?.let { i ->
                        if (progress.isUnlocked(i)) {
                            host.haptic(Haptic.CLICK)
                            host.play(i)
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun onBack(): Boolean {
        host.showHome()
        return true
    }

    private fun levelAt(x: Float, y: Float): Int? {
        val col = floor((x - gridLeft) / cellW).toInt()
        val row = floor((y - gridTop) / cellH).toInt()
        if (col !in 0 until COLUMNS || row !in 0 until ROWS) return null
        val i = Worlds.firstLevel(page) + row * COLUMNS + col
        return if (i < levelCount) i else null
    }

    override fun draw(canvas: Canvas) {
        backButton.draw(canvas, kit)

        val first = floor(scroll).toInt()
        for (p in first..first + 1) {
            if (p !in 0 until worldCount) continue
            val offset = (p - scroll) * width
            if (abs(offset) >= width) continue
            drawPage(canvas, p, offset)
        }

        prevButton.draw(canvas, kit)
        nextButton.draw(canvas, kit)
        if (worldCount > 1) {
            val gap = kit.u(14f)
            val startX = width / 2 - gap * (worldCount - 1) / 2
            for (p in 0 until worldCount) {
                kit.fill.color = if (p == page) kit.palette.ink else kit.palette.line
                canvas.drawCircle(startX + p * gap, dotsY, kit.u(3f), kit.fill)
            }
        }
    }

    private fun drawPage(canvas: Canvas, world: Int, offset: Float) {
        val colors = Palette.forWorld(world)
        val firstLevel = Worlds.firstLevel(world)
        val done = (firstLevel until min(firstLevel + Worlds.SIZE, levelCount)).count(progress::isCompleted)

        val cx = width / 2 + offset
        kit.heading.color = colors.ink
        kit.drawText(canvas, "${kit.text.world} ${world + 1}", cx, headerY, kit.heading)
        kit.small.color = colors.inkDim
        kit.drawText(canvas, String.format(Locale.ROOT, "%d / %d", done, Worlds.SIZE), cx, headerY + kit.u(30f), kit.small)
        kit.small.color = colors.ink

        val current = progress.currentIndex
        for (slot in 0 until Worlds.SIZE) {
            val i = firstLevel + slot
            if (i >= levelCount) break
            val x = offset + gridLeft + (slot % COLUMNS + 0.5f) * cellW
            val y = gridTop + (slot / COLUMNS + 0.5f) * cellH
            drawCell(canvas, colors, i, x, y, i == current)
        }
    }

    private fun drawCell(canvas: Canvas, colors: WorldPalette, i: Int, cx: Float, cy: Float, isCurrent: Boolean) {
        val r = ringRadius
        val label = String.format(Locale.ROOT, "%02d", i + 1)
        kit.stroke.strokeWidth = kit.u(1.6f)
        when {
            isCurrent -> {
                kit.stroke.color = Palette.withAlpha(colors.accent, 0.25f)
                canvas.drawCircle(cx, cy, r + kit.u(5f), kit.stroke)
                kit.stroke.color = colors.accent
                kit.stroke.strokeWidth = kit.u(2.4f)
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = colors.accent
                kit.drawText(canvas, label, cx, cy, kit.number)
            }
            progress.isCompleted(i) -> {
                // Completed levels look like the ball: a filled disc.
                kit.fill.color = colors.accent
                canvas.drawCircle(cx, cy, r, kit.fill)
                kit.number.color = colors.background
                kit.drawText(canvas, label, cx, cy, kit.number)
            }
            progress.isUnlocked(i) -> {
                kit.stroke.color = colors.inkDim
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = colors.ink
                kit.drawText(canvas, label, cx, cy, kit.number)
            }
            else -> {
                val dim = if (colors.isLight) Palette.withAlpha(colors.ink, 0.32f) else Palette.withAlpha(colors.primary, 0.55f)
                kit.stroke.color = dim
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = dim
                kit.drawText(canvas, label, cx, cy - r * 0.18f, kit.number)
                Icons.draw(canvas, kit, Icon.LOCK, cx, cy + r * 0.45f, r * 0.34f, dim)
            }
        }
        kit.number.color = colors.ink
    }

    private companion object {
        const val COLUMNS = 2
        const val ROWS = 5
    }
}
