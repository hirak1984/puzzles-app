package online.hirak.puzzles.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import online.hirak.puzzles.data.LastLevel
import online.hirak.puzzles.data.Progress
import online.hirak.puzzles.data.toIntArray
import online.hirak.puzzles.data.toJson
import online.hirak.puzzles.model.Tracks
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor

/**
 * One Train Tracks puzzle's live state: player links, marks, undo history,
 * timer, save/restore, and drag-to-draw rules. Port of iOS
 * `Models/TracksEngine.swift`.
 */
class TracksEngine : GameEngine("tracks") {
    var level by mutableStateOf(Tracks.Level.of(LastLevel.get("tracks")) ?: Tracks.Level.EASY)
        private set
    var seed = 0L
        private set
    var puzzle by mutableStateOf(EMPTY)
        private set
    var links by mutableStateOf(IntArray(0))
        private set
    var marks by mutableStateOf(emptySet<Int>())
        private set
    var hintCells by mutableStateOf<List<Int>?>(null)
        private set

    var locked = IntArray(0)
        private set
    private class Snapshot(val links: IntArray, val marks: Set<Int>)
    private val history = mutableListOf<Snapshot>()
    private var hintJob: Job? = null

    private enum class DragMode { DRAW, ERASE }
    var isDragging = false
        private set
    private var dragLast = -1
    private var dragFirst = -1
    private var dragMode: DragMode? = null
    private var dragBefore: Snapshot? = null
    private var dragChanged = false
    private var dragMoved = false

    override val levelRaw get() = level.raw
    override val levels = Tracks.Level.entries.map { it.raw }

    init { loadNew(level, resumableSeed(level)) }

    private fun restoreIfSaved() {
        val saved = Progress.load(game, level.raw, seed) ?: return
        val l = saved.optJSONArray("links")?.toIntArray() ?: return
        if (l.size == links.size) {
            links = l
            marks = saved.optJSONArray("marks")?.toIntArray()?.toSet() ?: emptySet()
            elapsed = saved.optInt("elapsed")
        }
    }

    override fun switchLevel(raw: String) {
        val newLevel = Tracks.Level.of(raw) ?: return
        if (newLevel == level) return
        save() // keep this level's puzzle so switching back resumes it
        LastLevel.set(game, newLevel.raw)
        loadNew(newLevel, resumableSeed(newLevel))
    }

    /** The level's open puzzle if it's still in progress, else a fresh seed. */
    private fun resumableSeed(level: Tracks.Level): Long {
        val seed = Progress.currentSeed(game, level.raw)
        if (seed != null && Progress.load(game, level.raw, seed) != null) return seed
        return randomSeed()
    }

    override fun newPuzzle() {
        Progress.clear(game, level.raw, seed)
        loadNew(level, randomSeed())
    }

    private fun loadNew(level: Tracks.Level, seed: Long) {
        this.level = level
        this.seed = seed
        Progress.setCurrentSeed(seed, game, level.raw)
        val puzzle = Tracks.generate(seed, level)
        this.puzzle = puzzle
        val st = Tracks.initialState(puzzle)
        links = st.links
        locked = st.locked
        marks = emptySet()
        history.clear()
        hintCells = null
        resetCommon()
        restoreIfSaved()
        startTimer()
        if (!puzzle.unique) showToast("Note: this puzzle may have more than one solution")
    }

    override fun save() {
        if (solved) Progress.clear(game, level.raw, seed)
        else Progress.save(game, level.raw, seed, JSONObject()
            .put("links", links.toJson()).put("marks", marks.toIntArray().toJson()).put("elapsed", elapsed))
    }

    val status: Tracks.Status get() = Tracks.status(puzzle, links)

    fun isLocked(c: Int, bit: Int) = (locked[c] and bit) != 0

    private fun cellAt(p: Offset, c: Float): Int? {
        val x = floor(p.x / c).toInt(); val y = floor(p.y / c).toInt()
        if (x < 0 || y < 0 || x >= puzzle.n || y >= puzzle.n) return null
        return y * puzzle.n + x
    }

    private fun toggle(a: Int, b: Int, mode: DragMode): Boolean {
        val n = puzzle.n
        val dx = (b % n) - (a % n); val dy = (b / n) - (a / n)
        val dir = Tracks.dirs.firstOrNull { it.dx == dx && it.dy == dy } ?: return false
        val has = (links[a] and dir.bit) != 0
        val l = links.copyOf()
        when (mode) {
            DragMode.ERASE -> {
                if (!has || (locked[a] and dir.bit) != 0) return false
                l[a] = l[a] and dir.bit.inv()
                l[b] = l[b] and Tracks.opp(dir.bit).inv()
            }
            DragMode.DRAW -> {
                if (has) return false
                if (Tracks.popcount(l[a]) >= 2 || Tracks.popcount(l[b]) >= 2) return false
                l[a] = l[a] or dir.bit
                l[b] = l[b] or Tracks.opp(dir.bit)
                marks = marks - a - b
            }
        }
        links = l
        return true
    }

    fun dragBegin(p: Offset, c: Float) {
        val cell = cellAt(p, c)
        if (solved || cell == null) { isDragging = false; return }
        isDragging = true
        dragFirst = cell
        dragLast = cell
        dragMode = null
        dragBefore = Snapshot(links, marks)
        dragChanged = false
        dragMoved = false
    }

    fun dragUpdate(p: Offset, c: Float) {
        if (!isDragging) return
        val cell = cellAt(p, c) ?: return
        if (cell == dragLast) return
        dragMoved = true
        val n = puzzle.n
        val dx = (cell % n) - (dragLast % n); val dy = (cell / n) - (dragLast / n)
        if (abs(dx) + abs(dy) == 1) {
            if (dragMode == null) {
                val dir = Tracks.dirs.firstOrNull { it.dx == dx && it.dy == dy }
                if (dir != null && (locked[dragLast] and dir.bit) == 0) {
                    dragMode = if ((links[dragLast] and dir.bit) != 0) DragMode.ERASE else DragMode.DRAW
                }
            }
            val mode = dragMode
            if (mode != null && toggle(dragLast, cell, mode)) dragChanged = true
            hintCells = null
            checkSolved()
        }
        dragLast = cell
    }

    fun dragEnd() {
        if (!isDragging) return
        isDragging = false
        if (!dragMoved && links[dragFirst] == 0) {
            marks = if (dragFirst in marks) marks - dragFirst else marks + dragFirst
            dragChanged = true
        }
        val before = dragBefore
        if (dragChanged && before != null) {
            history.add(before)
            save()
        }
        checkSolved()
    }

    private fun checkSolved() {
        if (status.solved && !solved) recordWin()
    }

    override fun undo() {
        if (solved) return
        val h = history.removeLastOrNull() ?: return
        links = h.links; marks = h.marks
        save()
    }

    override fun reset() {
        if (solved) return
        history.add(Snapshot(links, marks))
        links = Tracks.initialState(puzzle).links
        marks = emptySet()
        save()
    }

    override fun hint() {
        if (solved) return
        var cells: List<Int>? = null
        for (c in links.indices) {
            val extra = links[c] and puzzle.solution[c].inv() and locked[c].inv()
            if (extra != 0) { cells = listOf(c); break }
        }
        if (cells == null) {
            for (c in links.indices) if ((puzzle.solution[c] and links[c].inv()) != 0) { cells = listOf(c); break }
        }
        val cs = cells ?: return
        hintCells = cs
        hintJob?.cancel()
        hintJob = after(2500) { hintCells = null }
        val wrong = links[cs[0]] and puzzle.solution[cs[0]].inv()
        showToast(if (wrong != 0) "Something here is wrong" else "Track belongs through this cell")
    }

    companion object {
        private val EMPTY = Tracks.Puzzle(1, 0, 0, IntArray(1), IntArray(1), emptyMap(), IntArray(1), false)
    }
}
