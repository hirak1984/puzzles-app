package online.hirak.puzzles.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import online.hirak.puzzles.data.LastLevel
import online.hirak.puzzles.data.Progress
import online.hirak.puzzles.data.toIntArray
import online.hirak.puzzles.data.toJson
import online.hirak.puzzles.model.Sudoku
import org.json.JSONObject

/**
 * One Sudoku puzzle's live state: entries, undo history, timer,
 * save/restore, and cell-select + keypad entry. Port of iOS
 * `Models/SudokuEngine.swift`.
 */
class SudokuEngine : GameEngine("sudoku") {
    var level by mutableStateOf(Sudoku.Level.of(LastLevel.get("sudoku")) ?: Sudoku.Level.EASY)
        private set
    var seed = 0L
        private set
    var puzzle by mutableStateOf(EMPTY)
        private set
    var values by mutableStateOf(IntArray(81))
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set
    /** true = `hintCell` holds a wrong entry (red); false = an empty cell named in the toast. */
    var hintIsWrong by mutableStateOf(false)
        private set

    private val history = mutableListOf<IntArray>()
    private var hintJob: Job? = null

    override val levelRaw get() = level.raw
    override val levels = Sudoku.Level.entries.map { it.raw }

    init { loadNew(level, resumableSeed(level)) }

    private fun restoreIfSaved() {
        val saved = Progress.load(game, level.raw, seed) ?: return
        val v = saved.optJSONArray("values")?.toIntArray() ?: return
        if (v.size == values.size) {
            values = v
            elapsed = saved.optInt("elapsed")
        }
    }

    override fun switchLevel(raw: String) {
        val newLevel = Sudoku.Level.of(raw) ?: return
        if (newLevel == level) return
        save() // keep this level's puzzle so switching back resumes it
        LastLevel.set(game, newLevel.raw)
        loadNew(newLevel, resumableSeed(newLevel))
    }

    /** The level's open puzzle if it's still in progress, else a fresh seed. */
    private fun resumableSeed(level: Sudoku.Level): Long {
        val seed = Progress.currentSeed(game, level.raw)
        if (seed != null && Progress.load(game, level.raw, seed) != null) return seed
        return randomSeed()
    }

    override fun newPuzzle() {
        Progress.clear(game, level.raw, seed)
        loadNew(level, randomSeed())
    }

    private fun loadNew(level: Sudoku.Level, seed: Long) {
        this.level = level
        this.seed = seed
        Progress.setCurrentSeed(seed, game, level.raw)
        val puzzle = Sudoku.generate(seed, level)
        this.puzzle = puzzle
        values = puzzle.givens.copyOf()
        history.clear()
        selected = null
        hintCell = null
        resetCommon()
        restoreIfSaved()
        startTimer()
        if (!puzzle.unique) showToast("Note: this puzzle may have more than one solution")
    }

    override fun save() {
        if (solved) Progress.clear(game, level.raw, seed)
        else Progress.save(game, level.raw, seed, JSONObject().put("values", values.toJson()).put("elapsed", elapsed))
    }

    val status: Sudoku.Status get() = Sudoku.status(puzzle.givens, values)

    fun isGiven(i: Int) = puzzle.givens[i] != 0

    fun selectCell(i: Int) {
        if (solved) return
        if (isGiven(i)) { selected = null; return }
        selected = if (selected == i) null else i
    }

    /** Tapping the digit already in the selected cell clears it (toggle). */
    fun enterDigit(d: Int) {
        if (solved) return
        val i = selected
        if (i == null || isGiven(i)) {
            showToast("Tap a square first")
            return
        }
        history.add(values)
        values = values.copyOf().also { it[i] = if (it[i] == d) 0 else d }
        hintCell = null
        save()
        checkSolved()
    }

    fun erase() {
        val i = selected ?: return
        if (solved || isGiven(i) || values[i] == 0) return
        history.add(values)
        values = values.copyOf().also { it[i] = 0 }
        hintCell = null
        save()
    }

    private fun checkSolved() {
        if (status.solved && !solved) recordWin()
    }

    override fun undo() {
        if (solved) return
        values = history.removeLastOrNull() ?: return
        save()
    }

    override fun reset() {
        if (solved) return
        history.add(values)
        values = puzzle.givens.copyOf()
        selected = null
        save()
    }

    /**
     * A wrong entry first (red, left for the player to fix), otherwise an
     * empty cell whose digit is named in the toast rather than auto-filled.
     */
    override fun hint() {
        if (solved) return
        val wrongIdx = (0 until 81).firstOrNull { !isGiven(it) && values[it] != 0 && values[it] != puzzle.solution[it] }
        if (wrongIdx != null) {
            hintCell = wrongIdx
            hintIsWrong = true
            showToast("This number is wrong")
        } else {
            val idx = (0 until 81).firstOrNull { values[it] == 0 } ?: return
            hintCell = idx
            hintIsWrong = false
            showToast("A ${puzzle.solution[idx]} belongs here")
        }
        hintJob?.cancel()
        hintJob = after(2500) { hintCell = null }
    }

    companion object {
        private val EMPTY = Sudoku.Puzzle(IntArray(81), IntArray(81), false)
    }
}
