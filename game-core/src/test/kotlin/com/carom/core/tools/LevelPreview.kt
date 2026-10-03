package com.carom.core.tools

import com.carom.core.level.Block
import com.carom.core.level.Element
import com.carom.core.level.ElementKind
import com.carom.core.level.LevelData
import com.carom.core.level.Shape
import com.carom.core.level.Wall
import com.carom.core.math.Vec2
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Renders levels (with a sample solution path) to PNG contact sheets for reviewing level design. */
object LevelPreview {

    private const val CELL_W = 300
    private const val CELL_H = 560

    fun writeSheet(file: File, entries: List<Pair<LevelData, List<Vec2>>>, columns: Int = 5) =
        writePaths(file, entries.map { (l, p) -> l to listOf(p) }, columns)

    /** Like [writeSheet] with several paths per level (the first is drawn brightest). */
    fun writePaths(file: File, entries: List<Pair<LevelData, List<List<Vec2>>>>, columns: Int = 5) {
        val rows = (entries.size + columns - 1) / columns
        val image = BufferedImage(CELL_W * columns, CELL_H * rows, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x10141C)
        g.fillRect(0, 0, image.width, image.height)
        entries.forEachIndexed { i, (level, path) ->
            val g2 = g.create(CELL_W * (i % columns), CELL_H * (i / columns), CELL_W, CELL_H) as Graphics2D
            draw(g2, level, path)
            g2.dispose()
        }
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun draw(g: Graphics2D, level: LevelData, paths: List<List<Vec2>>) {
        val margin = 20.0
        val scale = minOf((CELL_W - 2 * margin) / level.width, (CELL_H - 2 * margin - 14) / level.height)
        g.color = Color(0x8A93A6)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        g.drawString("${level.id} ${level.name} · ${level.bounces}", margin.toInt(), 14)
        g.translate(margin, margin + 6)
        g.scale(scale, scale)

        // The level's edges are invisible in the game; outline them faintly here for reference.
        g.color = Color(0x2C3545)
        g.stroke = BasicStroke(4f)
        g.drawRect(0, 0, level.width.toInt(), level.height.toInt())
        val wallColor = Color(0xE8DCC4)
        g.color = wallColor
        for (o in level.obstacles) {
            when (o) {
                is Wall -> {
                    g.stroke = BasicStroke(o.thickness.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.draw(polyline(o.points, o.closed))
                }
                is Block -> {
                    // The block's real shape: its core grown by its rounding.
                    val core = polyline(o.core, closed = true)
                    g.fill(core)
                    if (o.radius > 0) {
                        g.stroke = BasicStroke((o.radius * 2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        g.draw(core)
                    }
                }
            }
        }

        for (e in level.elements) drawElement(g, e)

        g.color = Color(0xFBBF24)
        g.stroke = BasicStroke(8f)
        val gr = level.goalRadius
        g.draw(Ellipse2D.Double(level.goal.x - gr + 4, level.goal.y - gr + 4, 2 * gr - 8, 2 * gr - 8))

        paths.forEachIndexed { i, path ->
            if (path.size > 1) {
                g.color = if (i == 0) Color(0x6EE7B7) else Color(0x6EE7B7 or (0x60 shl 24), true)
                g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(12f, 12f), 0f)
                g.draw(polyline(path, closed = false))
            }
        }
        g.color = Color(0x6EE7B7)
        val br = level.ballRadius
        g.fill(Ellipse2D.Double(level.ball.x - br, level.ball.y - br, 2 * br, 2 * br))
    }

    private fun drawElement(g: Graphics2D, e: Element) {
        val c = when (e.kind) {
            ElementKind.BOOSTER -> Color(0x60A5FA)
            ElementKind.ATTRACTIVE -> Color(0xA78BFA)
            ElementKind.REPULSIVE -> Color(0xF472B6)
            ElementKind.SLOWER -> Color(0x94A3B8)
            ElementKind.DEATH -> Color(0xEF4444)
            ElementKind.PORTAL -> Color(0x22D3EE)
            ElementKind.CLOCK, ElementKind.TOUCH_ZONE -> Color(0x34D399)
            ElementKind.SWITCH -> Color(0xFACC15)
            ElementKind.BALL_CONTAINER -> Color(0xFB923C)
            else -> Color(0xE8DCC4)
        }
        val saved = g.transform
        g.translate(e.x, e.y)
        g.rotate(Math.toRadians(e.rotation))
        g.color = Color(c.red, c.green, c.blue, if (e.physical) 255 else 90)
        if (e.shape == Shape.CIRCLE) g.fill(Ellipse2D.Double(-e.scaleX / 2, -e.scaleX / 2, e.scaleX, e.scaleX))
        else g.fill(java.awt.geom.RoundRectangle2D.Double(-e.scaleX / 2, -e.scaleY / 2, e.scaleX, e.scaleY, 36.0, 36.0))
        if (e.kind == ElementKind.BOOSTER) {
            g.color = Color.WHITE
            g.stroke = BasicStroke(8f)
            g.drawLine((-e.scaleX / 3).toInt(), 0, (e.scaleX / 3).toInt(), 0)
            g.drawLine((e.scaleX / 3).toInt(), 0, (e.scaleX / 6).toInt(), -30)
            g.drawLine((e.scaleX / 3).toInt(), 0, (e.scaleX / 6).toInt(), 30)
        }
        g.transform = saved
    }

    private fun polyline(points: List<Vec2>, closed: Boolean) = Path2D.Double().apply {
        moveTo(points[0].x, points[0].y)
        for (p in points.drop(1)) lineTo(p.x, p.y)
        if (closed) closePath()
    }
}
