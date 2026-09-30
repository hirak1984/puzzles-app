package online.hirak.puzzles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import online.hirak.puzzles.game.SudokuEngine

/**
 * Sudoku board + keypad — port of iOS `Views/SudokuBoardView.swift`.
 */
@Composable
fun SudokuBoard(engine: SudokuEngine) {
    val c = LocalPalette.current
    val tm = rememberTextMeasurer()
    val values = engine.values
    val sel = engine.selected
    val hintCell = engine.hintCell
    val hintIsWrong = engine.hintIsWrong
    val st = engine.status
    val designCell = 44f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val cell = size.width / 9f
                        val x = (pos.x / cell).toInt(); val y = (pos.y / cell).toInt()
                        if (x in 0..8 && y in 0..8) engine.selectCell(y * 9 + x)
                    }
                },
        ) {
            val cell = size.width / 9f
            val s = cell / designCell
            val selRow = sel?.let { it / 9 }; val selCol = sel?.let { it % 9 }
            val selBox = sel?.let { (it / 9 / 3) * 3 + (it % 9 / 3) }

            // Cell backgrounds: selected cell, its row/column/box, givens, hint.
            for (i in 0 until 81) {
                val x = i % 9; val y = i / 9
                val box = (y / 3) * 3 + (x / 3)
                var fill = c.bgElevated
                if (engine.isGiven(i)) fill = c.bgRaised
                if (selRow == y || selCol == x || selBox == box) fill = c.bgRaised
                if (hintCell == i) fill = c.accentBg
                if (sel == i) fill = c.accentBg
                drawRect(fill, Offset(x * cell, y * cell), Size(cell, cell))
            }

            // Digits.
            for (i in 0 until 81) {
                if (values[i] == 0) continue
                val center = Offset((i % 9) * cell + cell / 2, (i / 9) * cell + cell / 2)
                val color = when {
                    hintCell == i && hintIsWrong -> c.danger
                    st.conflict[i] -> c.danger
                    engine.isGiven(i) -> c.text
                    else -> c.user
                }
                centeredText(tm, "${values[i]}", center, 20 * s, color,
                    if (engine.isGiven(i)) FontWeight.SemiBold else FontWeight.Normal)
            }

            // Grid lines: thin every cell, thick every 3 (box separators).
            for (i in 0..9) {
                val thick = i % 3 == 0
                val w = (if (thick) 2.5f else 1f) * s
                val color = if (thick) c.borderStrong else c.border
                drawLine(color, Offset(0f, i * cell), Offset(9 * cell, i * cell), w)
                drawLine(color, Offset(i * cell, 0f), Offset(i * cell, 9 * cell), w)
            }
        }

        Keypad(engine)
    }
}

@Composable
private fun Keypad(engine: SudokuEngine) {
    val c = LocalPalette.current
    val shape = RoundedCornerShape(6.dp)
    @Composable
    fun Key(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) = Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(c.bgElevated)
            .border(1.dp, c.borderStrong, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }

    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (d in 1..9) Key(Modifier.weight(1f), { engine.enterDigit(d) }) { Txt("$d", 18, c.text, FontWeight.Medium) }
        Key(Modifier.weight(1f), engine::erase) { EraseIcon(c.textMuted, 18.dp) }
    }
}
