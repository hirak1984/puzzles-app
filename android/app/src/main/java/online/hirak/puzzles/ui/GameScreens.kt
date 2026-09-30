package online.hirak.puzzles.ui

import androidx.compose.runtime.Composable
import online.hirak.puzzles.game.HashiEngine
import online.hirak.puzzles.game.SudokuEngine
import online.hirak.puzzles.game.TracksEngine

// Per-game screens + rules text — port of iOS `HashiGameScreen.swift`,
// `TracksGameScreen.swift`, `SudokuGameScreen.swift`. Keep rules wording
// identical on both platforms.

private val hashiRules = listOf(
    "Each number is how many bridges must touch that island.",
    "Bridges run straight, horizontally or vertically, and never cross another bridge or island.",
    "At most two bridges join any pair of islands.",
    "All islands must end up connected as one group.",
    "Tap an island, then a highlighted neighbour to add a bridge (tap again for a double, again to remove). You can also drag from an island towards its neighbour, or tap a bridge to cycle it.",
)

private val tracksRules = listOf(
    "Build one unbroken track from the entrance arrow on the left to the exit arrow at the bottom.",
    "The numbers on the top and right say how many squares in that column or row hold track.",
    "The track never crosses or branches, and it may not touch itself head-on. Every piece of track must belong to the one route.",
    "Some squares are already laid; they cannot be changed.",
    "Drag from square to square to lay track (drag over existing track to erase it). Tap an empty square to mark it as having no track.",
)

private val sudokuRules = listOf(
    "Fill every empty square with a digit from 1 to 9.",
    "Each row, each column, and each 3×3 box must contain every digit exactly once — no repeats.",
    "Tap a square, then tap a number below to fill it. Tap the same number again to clear it.",
    "Given squares are shaded and locked — they can't be changed.",
)

@Composable
fun HashiScreen(engine: HashiEngine) = GameScreen("Hashi", engine, hashiRules) { HashiBoard(engine) }

@Composable
fun TracksScreen(engine: TracksEngine) = GameScreen("Train Tracks", engine, tracksRules) { TracksBoard(engine) }

@Composable
fun SudokuScreen(engine: SudokuEngine) = GameScreen("Sudoku", engine, sudokuRules) { SudokuBoard(engine) }
