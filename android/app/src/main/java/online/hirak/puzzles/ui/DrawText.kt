package online.hirak.puzzles.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Draws `text` centered on `at` — the Canvas equivalent of SwiftUI's `ctx.draw(Text, at:)`. */
fun DrawScope.centeredText(
    tm: TextMeasurer,
    text: String,
    at: Offset,
    sizePx: Float,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Fonts.sans,
) {
    val layout = tm.measure(text, TextStyle(color = color, fontSize = sizePx.toSp(), fontWeight = weight, fontFamily = family))
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
