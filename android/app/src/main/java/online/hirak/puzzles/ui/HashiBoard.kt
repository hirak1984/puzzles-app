package online.hirak.puzzles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import online.hirak.puzzles.game.HashiEngine
import online.hirak.puzzles.model.Hashi

/**
 * Hashi board: dots grid, valid-path highlights, bridges, islands. Port of
 * iOS `Views/HashiBoardView.swift`.
 */
@Composable
fun HashiBoard(engine: HashiEngine) {
    val c = LocalPalette.current
    val tm = rememberTextMeasurer()
    val model = engine.puzzle.model
    val vals = engine.vals
    val selected = engine.selected
    val hintEdge = engine.hintEdge
    val hintIsWrong = engine.hintIsWrong
    val st = Hashi.status(model, vals)
    // Design constant the original SVG was tuned at (cell = 40 units).
    val designCell = 40f

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(model.w.toFloat() / maxOf(model.h, 1))
            .boardDrag(model, onEnd = { start, end, width ->
                engine.handleInteraction(start, end, width / maxOf(model.w, 1))
            }),
    ) {
        val cell = size.width / maxOf(model.w, 1)
        val s = cell / designCell
        fun center(i: Int) = Offset(model.islands[i].x * cell + cell / 2, model.islands[i].y * cell + cell / 2)

        // Faint grid dots.
        for (y in 0 until model.h) for (x in 0 until model.w) {
            drawCircle(c.borderStrong, 1.5f * s, Offset(x * cell + cell / 2, y * cell + cell / 2))
        }

        // Paths the selected island can still use.
        if (selected != null) {
            for (k in model.adj[selected]) {
                if (!Hashi.validEdge(model, vals, st.sums, k)) continue
                val e = model.edges[k]
                drawLine(c.accent.copy(alpha = 0.28f), center(e.a), center(e.b), 14 * s, StrokeCap.Round)
                val o = if (e.a == selected) e.b else e.a
                drawCircle(c.accent, 19 * s, center(o), style = Stroke(2 * s))
            }
        }

        // Hint line + bridges.
        model.edges.forEachIndexed { k, e ->
            val a = center(e.a); val b = center(e.b)
            if (hintEdge == k) {
                val hintColor = if (hintIsWrong) c.danger else c.accent
                drawLine(hintColor.copy(alpha = 0.35f), a, b, 9 * s, StrokeCap.Round)
            }
            val n = vals[k]
            if (n <= 0) return@forEachIndexed
            val color = if (st.solved) c.accent else c.text
            for (i in 0 until n) {
                val off = if (n == 1) 0f else if (i == 0) -3.5f * s else 3.5f * s
                val d = if (e.horizontal) Offset(0f, off) else Offset(off, 0f)
                drawLine(color, a + d, b + d, 2.5f * s, StrokeCap.Round)
            }
        }

        // Islands.
        model.islands.forEachIndexed { i, isl ->
            val p = center(i)
            val isSel = selected == i
            val (fill, stroke, textColor) = when {
                st.over[i] || st.isolated[i] -> Triple(c.dangerBg, c.danger, c.danger)
                st.done[i] -> Triple(c.accentBg, c.accent, c.accent)
                else -> Triple(c.bgElevated, c.text, c.text)
            }
            val r = 15 * s
            drawCircle(if (isSel) c.accentBg else fill, r, p)
            drawCircle(if (isSel) c.accent else stroke, r, p, style = Stroke((if (isSel) 3f else 2.5f) * s))
            centeredText(tm, "${isl.n}", p, 17 * s, textColor, FontWeight.SemiBold)
        }
    }
}

