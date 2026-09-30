package online.hirak.puzzles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import online.hirak.puzzles.game.TracksEngine
import online.hirak.puzzles.model.Tracks
import kotlin.math.cos
import kotlin.math.sin

/**
 * Train Tracks board: cells, marks, track pieces, entrance/exit gates, grid
 * lines, row/column clues. Port of iOS `Views/TracksBoardView.swift`.
 */
@Composable
fun TracksBoard(engine: TracksEngine) {
    val c = LocalPalette.current
    val tm = rememberTextMeasurer()
    val p = engine.puzzle
    val n = p.n
    val links = engine.links
    val marks = engine.marks
    val hintCells = engine.hintCells
    val st = Tracks.status(p, links)
    val designCell = 44f

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .boardDrag(
                p,
                // The board is (n+2) cells square: a 1-cell margin on every side
                // for clues (top/right) and the entrance/exit stubs (left/bottom).
                onStart = { pos, width -> val cell = width / (n + 2); engine.dragBegin(pos - Offset(cell, cell), cell) },
                onMove = { pos, width -> val cell = width / (n + 2); engine.dragUpdate(pos - Offset(cell, cell), cell) },
                onEnd = { _, _, _ -> engine.dragEnd() },
            ),
    ) {
        val cell = size.width / (n + 2)
        val s = cell / designCell
        translate(cell, cell) {
            // Cells + empty-cell marks.
            for (i in 0 until n * n) {
                val x = i % n; val y = i / n
                val isGiven = p.given.containsKey(i)
                val isHint = hintCells?.contains(i) == true
                val fill = if (isHint) c.accentBg else if (isGiven) c.bgRaised else c.bgElevated
                val tl = Offset(x * cell, y * cell)
                drawRect(fill, tl, Size(cell, cell))
                if (i in marks && links[i] == 0) {
                    val m = 15 * s
                    drawLine(c.textSubtle, tl + Offset(m, m), tl + Offset(cell - m, cell - m), 2 * s, StrokeCap.Round)
                    drawLine(c.textSubtle, tl + Offset(cell - m, m), tl + Offset(m, cell - m), 2 * s, StrokeCap.Round)
                }
            }

            // Track pieces, as two independently-colored halves per cell so a
            // player-drawn link can meet a given one cleanly.
            for (i in 0 until n * n) {
                if (links[i] == 0) continue
                for (h in halves(i % n, i / n, links[i], cell)) {
                    val isUser = !engine.isLocked(i, h.bit)
                    val rail = if (st.solved) c.accent else if (isUser) c.user else c.text
                    val ties = if (st.solved) c.accent else if (isUser) c.user else c.textSubtle
                    val tiesAlpha = if (st.solved) 0.6f else if (isUser) 0.55f else 1f
                    strokeHalf(h, ties.copy(alpha = tiesAlpha), 15 * s, floatArrayOf(2.5f * s, 6 * s))
                    strokeHalf(h, rail, 9 * s)
                    strokeHalf(h, c.bgElevated, 4.5f * s)
                }
            }

            // Entrance / exit stubs + arrows.
            val sy = p.start / n
            val ex = p.end % n
            fun gate(h: Half) {
                strokeHalf(h, c.textSubtle, 15 * s, floatArrayOf(2.5f * s, 6 * s))
                strokeHalf(h, c.text, 9 * s)
                strokeHalf(h, c.bgElevated, 4.5f * s)
            }
            fun arrow(at: Offset, degrees: Double) {
                val w = 7 * s; val hgt = 8 * s
                val rad = Math.toRadians(degrees)
                fun rot(x: Float, y: Float) = Offset(
                    at.x + (x * cos(rad) - y * sin(rad)).toFloat(),
                    at.y + (x * sin(rad) + y * cos(rad)).toFloat(),
                )
                val a = rot(-w, -hgt); val b = rot(w, 0f); val d = rot(-w, hgt)
                drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close() }, c.accent)
            }
            val ey = sy * cell + cell / 2
            gate(Half(0, Offset(-cell, ey), Offset(-cell / 2, ey), Offset(0f, ey)))
            arrow(Offset(-cell + 10 * s, ey), 0.0)
            val exX = ex * cell + cell / 2
            gate(Half(0, Offset(exX, n * cell), Offset(exX, n * cell + cell / 2), Offset(exX, n * cell + cell)))
            arrow(Offset(exX, n * cell + cell - 10 * s), 90.0)

            // Grid lines.
            for (i in 0..n) {
                drawLine(c.border, Offset(0f, i * cell), Offset(n * cell, i * cell), 1 * s)
                drawLine(c.border, Offset(i * cell, 0f), Offset(i * cell, n * cell), 1 * s)
            }

            // Clues.
            fun clue(v: Int, ok: Boolean, over: Boolean, at: Offset) {
                val color: Color = if (ok) c.textSubtle else if (over) c.danger else c.text
                centeredText(tm, "$v", at, 17 * s, color, if (ok) FontWeight.Normal else FontWeight.SemiBold)
            }
            for (i in 0 until n) {
                clue(p.colC[i], st.colOk[i], st.cols[i] > p.colC[i], Offset(i * cell + cell / 2, -cell / 2))
                clue(p.rowC[i], st.rowOk[i], st.rows[i] > p.rowC[i], Offset(n * cell + cell / 2, i * cell + cell / 2))
            }
        }
    }
}

private class Half(val bit: Int, val start: Offset, val control: Offset, val end: Offset)

private fun edgePoint(b: Int, ox: Float, oy: Float, mx: Float, my: Float, cell: Float) = when (b) {
    Tracks.N -> Offset(mx, oy)
    Tracks.S -> Offset(mx, oy + cell)
    Tracks.W -> Offset(ox, my)
    else -> Offset(ox + cell, my) // E
}

private fun halves(x: Int, y: Int, m: Int, cell: Float): List<Half> {
    val ox = x * cell; val oy = y * cell
    val mx = ox + cell / 2; val my = oy + cell / 2
    val mid = Offset(mx, my)
    val bits = Tracks.dirs.map { it.bit }.filter { (m and it) != 0 }
    if (bits.size == 1) {
        val p = edgePoint(bits[0], ox, oy, mx, my, cell)
        return listOf(Half(bits[0], p, Offset((p.x + mx) / 2, (p.y + my) / 2), mid))
    }
    if (bits.size != 2) return emptyList()
    val a = edgePoint(bits[0], ox, oy, mx, my, cell)
    val b = edgePoint(bits[1], ox, oy, mx, my, cell)
    val split = Offset((a.x + 2 * mx + b.x) / 4, (a.y + 2 * my + b.y) / 4)
    return listOf(
        Half(bits[0], a, Offset((a.x + mx) / 2, (a.y + my) / 2), split),
        Half(bits[1], b, Offset((b.x + mx) / 2, (b.y + my) / 2), split),
    )
}

private fun DrawScope.strokeHalf(h: Half, color: Color, width: Float, dash: FloatArray? = null) {
    val path = Path().apply {
        moveTo(h.start.x, h.start.y)
        quadraticTo(h.control.x, h.control.y, h.end.x, h.end.y)
    }
    drawPath(path, color, style = Stroke(width, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }))
}
