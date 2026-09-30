package online.hirak.puzzles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer

// Home-screen badge icons — port of iOS `Views/GameIcons.swift`.

@Composable
fun HashiIcon(modifier: Modifier) {
    val c = LocalPalette.current
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val s = size.width / 64f
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        val st = Stroke(2.5f * s, cap = StrokeCap.Round)
        val bridges = Path().apply {
            moveTo(18 * s, 20 * s); lineTo(46 * s, 20 * s)
            moveTo(18 * s, 44 * s); lineTo(46 * s, 44 * s)
            moveTo(18 * s, 22 * s); lineTo(18 * s, 42 * s)
            moveTo(46 * s, 22 * s); lineTo(46 * s, 42 * s)
        }
        drawPath(bridges, c.accent, style = st)
        for ((x, y) in listOf(18f to 20f, 46f to 20f, 18f to 44f, 46f to 44f)) {
            drawCircle(c.bgElevated, 8 * s, pt(x, y))
            drawCircle(c.accent, 8 * s, pt(x, y), style = Stroke(2.5f * s))
            centeredText(tm, "2", pt(x, y), 10 * s, c.accent, FontWeight.Bold)
        }
    }
}

@Composable
fun SudokuIcon(modifier: Modifier) {
    val c = LocalPalette.current
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val s = size.width / 64f
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        for (i in 0..3) {
            val o = 18f + i * (28f / 3)
            drawLine(c.accent.copy(alpha = 0.55f), pt(o, 18f), pt(o, 46f), 1.5f * s)
            drawLine(c.accent.copy(alpha = 0.55f), pt(18f, o), pt(46f, o), 1.5f * s)
        }
        drawRect(c.accent, pt(18f, 18f), Size(28 * s, 28 * s), style = Stroke(2.5f * s))
        val cell = 28f / 3
        for ((cx, cy, d) in listOf(Triple(0, 0, "5"), Triple(2, 1, "3"), Triple(1, 2, "8"))) {
            centeredText(tm, d, pt(18 + (cx + 0.5f) * cell, 18 + (cy + 0.5f) * cell), 11 * s, c.accent, FontWeight.Bold)
        }
    }
}

@Composable
fun TracksIcon(modifier: Modifier) {
    val c = LocalPalette.current
    Canvas(modifier) {
        val s = size.width / 64f
        val p = Path().apply {
            moveTo(8 * s, 20 * s)
            lineTo(32 * s, 20 * s)
            quadraticTo(48 * s, 20 * s, 48 * s, 36 * s)
            lineTo(48 * s, 56 * s)
        }
        drawPath(p, c.accent.copy(alpha = 0.55f), style = Stroke(10 * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * s, 5 * s))))
        drawPath(p, c.accent, style = Stroke(4 * s))
        drawPath(p, c.bgElevated, style = Stroke(1.6f * s))
    }
}
