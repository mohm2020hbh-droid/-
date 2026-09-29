package com.carom.game.screens

import android.graphics.Canvas
import android.view.MotionEvent
import com.carom.game.ui.Icon
import com.carom.game.ui.Icons
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pages of 15 levels (5×3). Completed levels are marked, the next level to play is highlighted,
 * locked levels are dimmed. Swipe or use the arrows to change page; tap a level to play it.
 */
class LevelSelectScreen(host: GameHost, focusIndex: Int) : Screen(host) {

    private val levelCount = host.app.levels.size
    private val progress = host.app.progress
    private val pageCount = max(1, ceil(levelCount / PER_PAGE.toFloat()).toInt())
    private var page = (focusIndex / PER_PAGE).coerceIn(0, pageCount - 1)

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

    override fun onLayout() {
        headerY = safe.top + kit.u(30f)
        dotsY = safe.bottom - kit.u(20f)
        val gridW = min(safe.width() - kit.u(120f), kit.u(470f))
        gridTop = headerY + kit.u(24f)
        val gridH = dotsY - kit.u(14f) - gridTop
        cellW = gridW / COLUMNS
        cellH = gridH / ROWS
        gridLeft = safe.left + (safe.width() - gridW) / 2
        ringRadius = min(cellW, cellH) * 0.33f
        val b = kit.u(40f)
        backButton.setCenter(safe.left + kit.u(30f), headerY, b, b)
        prevButton.setCenter(safe.left + kit.u(30f), gridTop + gridH / 2, b, b)
        nextButton.setCenter(safe.right - kit.u(30f), gridTop + gridH / 2, b, b)
        updateArrows()
    }

    private fun goTo(target: Int) {
        page = target.coerceIn(0, pageCount - 1)
        updateArrows()
    }

    private fun updateArrows() {
        prevButton.visible = page > 0
        nextButton.visible = page < pageCount - 1
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
                if (dragged) scroll = (page - dx / width).coerceIn(-0.15f, pageCount - 0.85f)
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
        val i = page * PER_PAGE + row * COLUMNS + col
        return if (i < levelCount) i else null
    }

    override fun draw(canvas: Canvas) {
        backButton.draw(canvas, kit)
        kit.heading.color = Palette.TEXT
        kit.drawText(canvas, kit.text.levels, width / 2, headerY, kit.heading)
        val count = String.format(Locale.ROOT, "%d / %d", progress.completedCount, levelCount)
        kit.small.color = Palette.TEXT_DIM
        kit.drawText(canvas, count, safe.right - kit.u(40f), headerY, kit.small)
        kit.small.color = Palette.TEXT

        val first = floor(scroll).toInt()
        for (p in first..first + 1) {
            if (p !in 0 until pageCount) continue
            val offset = (p - scroll) * width
            if (abs(offset) >= width) continue
            drawPage(canvas, p, offset)
        }

        prevButton.draw(canvas, kit)
        nextButton.draw(canvas, kit)
        if (pageCount > 1) {
            val gap = kit.u(12f)
            val startX = width / 2 - gap * (pageCount - 1) / 2
            for (p in 0 until pageCount) {
                kit.fill.color = if (p == page) Palette.TEXT else Palette.LINE
                canvas.drawCircle(startX + p * gap, dotsY, kit.u(2.4f), kit.fill)
            }
        }
    }

    private fun drawPage(canvas: Canvas, p: Int, offset: Float) {
        val current = progress.currentIndex
        for (slot in 0 until PER_PAGE) {
            val i = p * PER_PAGE + slot
            if (i >= levelCount) break
            val cx = offset + gridLeft + (slot % COLUMNS + 0.5f) * cellW
            val cy = gridTop + (slot / COLUMNS + 0.5f) * cellH
            drawCell(canvas, i, cx, cy, i == current)
        }
    }

    private fun drawCell(canvas: Canvas, i: Int, cx: Float, cy: Float, isCurrent: Boolean) {
        val r = ringRadius
        val label = String.format(Locale.ROOT, "%02d", i + 1)
        val unlocked = progress.isUnlocked(i)
        val completed = progress.isCompleted(i)
        kit.stroke.strokeWidth = kit.u(1.4f)
        when {
            isCurrent -> {
                kit.stroke.color = Palette.withAlpha(Palette.ACCENT, 0.25f)
                canvas.drawCircle(cx, cy, r + kit.u(4f), kit.stroke)
                kit.stroke.color = Palette.ACCENT
                kit.stroke.strokeWidth = kit.u(2f)
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = Palette.ACCENT
                kit.drawText(canvas, label, cx, cy, kit.number)
            }
            completed -> {
                kit.fill.color = Palette.withAlpha(Palette.BALL, 0.1f)
                canvas.drawCircle(cx, cy, r, kit.fill)
                kit.stroke.color = Palette.BALL
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = Palette.TEXT
                kit.drawText(canvas, label, cx, cy, kit.number)
                // A small check badge on the ring.
                val bx = cx + r * 0.7f
                val by = cy + r * 0.7f
                kit.fill.color = Palette.BALL
                canvas.drawCircle(bx, by, kit.u(5.5f), kit.fill)
                Icons.draw(canvas, kit, Icon.CHECK, bx, by, kit.u(7f), Palette.BACKGROUND)
            }
            unlocked -> {
                kit.stroke.color = Palette.TEXT_DIM
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = Palette.TEXT
                kit.drawText(canvas, label, cx, cy, kit.number)
            }
            else -> {
                kit.stroke.color = Palette.LINE
                canvas.drawCircle(cx, cy, r, kit.stroke)
                kit.number.color = Palette.withAlpha(Palette.TEXT_DIM, 0.6f)
                kit.drawText(canvas, label, cx, cy - r * 0.18f, kit.number)
                Icons.draw(canvas, kit, Icon.LOCK, cx, cy + r * 0.45f, r * 0.34f, Palette.LINE)
            }
        }
        kit.number.color = Palette.TEXT
    }

    private companion object {
        const val COLUMNS = 5
        const val ROWS = 3
        const val PER_PAGE = COLUMNS * ROWS
    }
}
