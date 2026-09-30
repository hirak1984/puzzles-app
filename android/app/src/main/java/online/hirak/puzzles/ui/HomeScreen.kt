package online.hirak.puzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.hirak.puzzles.data.Stats

enum class GameKind(val title: String) { HASHI("Hashi"), TRACKS("Train Tracks"), SUDOKU("Sudoku") }

/** Game list — port of iOS `Views/HomeView.swift`. */
@Composable
fun HomeScreen(onOpen: (GameKind) -> Unit) {
    val c = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt("Three logic puzzles, generated fresh every time.", 24, c.text, FontWeight.Medium, serif = true)
        Txt("No ads, no accounts — progress stays on this device.", 15, c.textMuted, modifier = Modifier.padding(bottom = 16.dp))
        Card(GameKind.HASHI, "Connect the islands with bridges.", Stats.totalSolved("hashi"), onOpen) { HashiIcon(it) }
        Card(GameKind.TRACKS, "Lay a continuous track from entrance to exit.", Stats.totalSolved("tracks"), onOpen) { TracksIcon(it) }
        Card(GameKind.SUDOKU, "Fill the grid so every row, column and box holds 1–9.", Stats.totalSolved("sudoku"), onOpen) { SudokuIcon(it) }
    }
}

@Composable
private fun Card(kind: GameKind, blurb: String, solved: Int, onOpen: (GameKind) -> Unit, icon: @Composable (Modifier) -> Unit) {
    val c = LocalPalette.current
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.bgElevated)
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .clickable { onOpen(kind) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.accentBg)
                .padding(4.dp)
                .size(64.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(kind.title, 22, c.text, FontWeight.SemiBold, serif = true)
            Txt(blurb, 15, c.textMuted)
            Txt(if (solved > 0) "$solved solved" else "Not played yet", 13, c.textSubtle)
        }
    }
}
