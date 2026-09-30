package online.hirak.puzzles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Small line icons drawn in code, standing in for the SF Symbols the iOS
// app uses (chevron.backward, sun.max, moon.fill, circle.lefthalf.filled,
// delete.left) so the app needs no icon library.

@Composable
fun BackIcon(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width / 24f
    val p = Path().apply { moveTo(15 * s, 4 * s); lineTo(7 * s, 12 * s); lineTo(15 * s, 20 * s) }
    drawPath(p, color, style = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun ThemeIcon(mode: ThemeMode, color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width / 24f
    val c = Offset(12 * s, 12 * s)
    when (mode) {
        ThemeMode.LIGHT -> {
            drawCircle(color, 4.5f * s, c, style = Stroke(1.8f * s))
            for (i in 0 until 8) {
                val a = i * Math.PI / 4
                drawLine(color, Offset(c.x + 7.5f * s * cos(a).toFloat(), c.y + 7.5f * s * sin(a).toFloat()),
                    Offset(c.x + 10f * s * cos(a).toFloat(), c.y + 10f * s * sin(a).toFloat()), 1.8f * s, StrokeCap.Round)
            }
        }
        ThemeMode.DARK -> {
            val moon = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c, 8.5f * s))
                val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + 5 * s, c.y - 4 * s), 7.5f * s)) }
                op(this, cut, androidx.compose.ui.graphics.PathOperation.Difference)
            }
            drawPath(moon, color)
        }
        ThemeMode.SYSTEM -> {
            drawCircle(color, 8.5f * s, c, style = Stroke(1.8f * s))
            drawArc(color, 90f, 180f, true, Offset(c.x - 8.5f * s, c.y - 8.5f * s), androidx.compose.ui.geometry.Size(17 * s, 17 * s))
        }
    }
}

@Composable
fun EraseIcon(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width / 24f
    val p = Path().apply {
        moveTo(8 * s, 5 * s); lineTo(21 * s, 5 * s); lineTo(21 * s, 19 * s); lineTo(8 * s, 19 * s); lineTo(2 * s, 12 * s); close()
    }
    val st = Stroke(1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(p, color, style = st)
    drawLine(color, Offset(11 * s, 9 * s), Offset(17 * s, 15 * s), 1.8f * s, StrokeCap.Round)
    drawLine(color, Offset(17 * s, 9 * s), Offset(11 * s, 15 * s), 1.8f * s, StrokeCap.Round)
}

@Composable
fun ChevronIcon(color: Color, expanded: Boolean, size: Dp = 14.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width / 24f
    val p = if (expanded) Path().apply { moveTo(5 * s, 9 * s); lineTo(12 * s, 16 * s); lineTo(19 * s, 9 * s) }
    else Path().apply { moveTo(9 * s, 5 * s); lineTo(16 * s, 12 * s); lineTo(9 * s, 19 * s) }
    drawPath(p, color, style = Stroke(2.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
