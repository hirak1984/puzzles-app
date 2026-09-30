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
import online.hirak.puzzles.model.Hashi
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One Hashi puzzle's live state: player bridges, undo history, timer,
 * save/restore, and the tap/drag interaction rules. Port of iOS
 * `Models/HashiEngine.swift`.
 */
class HashiEngine : GameEngine("hashi") {
    var level by mutableStateOf(Hashi.Level.of(LastLevel.get("hashi")) ?: Hashi.Level.EASY)
        private set
    var seed = 0L
        private set
    var puzzle by mutableStateOf(EMPTY)
        private set
    var vals by mutableStateOf(IntArray(0))
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var hintEdge by mutableStateOf<Int?>(null)
        private set
    /** true = `hintEdge` is a bridge that shouldn't be there (red); false = a missing one. */
    var hintIsWrong by mutableStateOf(false)
        private set

    private val history = mutableListOf<IntArray>()
    private var hintJob: Job? = null
    private var shownDisconnectedToast = false

    override val levelRaw get() = level.raw
    override val levels = Hashi.Level.entries.map { it.raw }

    init { loadNew(level, resumableSeed(level)) }

    private fun restoreIfSaved() {
        val saved = Progress.load(game, level.raw, seed) ?: return
        val v = saved.optJSONArray("vals")?.toIntArray() ?: return
        if (v.size == vals.size) {
            vals = v
            elapsed = saved.optInt("elapsed")
        }
    }

    override fun switchLevel(raw: String) {
        val newLevel = Hashi.Level.of(raw) ?: return
        if (newLevel == level) return
        save() // keep this level's puzzle so switching back resumes it
        LastLevel.set(game, newLevel.raw)
        loadNew(newLevel, resumableSeed(newLevel))
    }

    /** The level's open puzzle if it's still in progress, else a fresh seed. */
    private fun resumableSeed(level: Hashi.Level): Long {
        val seed = Progress.currentSeed(game, level.raw)
        if (seed != null && Progress.load(game, level.raw, seed) != null) return seed
        return randomSeed()
    }

    override fun newPuzzle() {
        Progress.clear(game, level.raw, seed)
        loadNew(level, randomSeed())
    }

    private fun loadNew(level: Hashi.Level, seed: Long) {
        this.level = level
        this.seed = seed
        Progress.setCurrentSeed(seed, game, level.raw)
        val puzzle = Hashi.generate(seed, level)
        this.puzzle = puzzle
        vals = IntArray(puzzle.model.edges.size)
        history.clear()
        selected = null
        hintEdge = null
        resetCommon()
        shownDisconnectedToast = false
        restoreIfSaved()
        startTimer()
        if (!puzzle.unique) showToast("Note: this puzzle may have more than one solution")
    }

    override fun save() {
        if (solved) Progress.clear(game, level.raw, seed)
        else Progress.save(game, level.raw, seed, JSONObject().put("vals", vals.toJson()).put("elapsed", elapsed))
    }

    val status: Hashi.Status get() = Hashi.status(puzzle.model, vals)

    /**
     * Cycles an edge 0 → 1 → 2 → 0, skipping any step that would push either
     * endpoint past its target. If every forward step is invalid, this is a
     * no-op with an explanatory toast.
     */
    private fun cycle(k: Int) {
        if (solved) return
        if (Hashi.blocked(puzzle.model, vals, k) && vals[k] == 0) {
            showToast("A bridge would cross there")
            return
        }
        val e = puzzle.model.edges[k]
        val sums = status.sums
        val current = vals[k]
        fun exceeds(newValue: Int): Boolean {
            val sumA = sums[e.a] - current + newValue
            val sumB = sums[e.b] - current + newValue
            return sumA > puzzle.model.islands[e.a].n || sumB > puzzle.model.islands[e.b].n
        }
        var next = (current + 1) % 3
        while (next != 0 && exceeds(next)) next = (next + 1) % 3
        if (next == current) {
            showToast("That island already has enough bridges")
            return
        }
        history.add(vals)
        vals = vals.copyOf().also { it[k] = next }
        hintEdge = null
        save()
        checkSolved()
    }

    private fun checkSolved() {
        val st = status
        if (st.solved && !solved) {
            recordWin()
            return
        }
        // Every island's count matches, but bridges form 2+ separate networks.
        val disconnected = st.done.all { it } && !st.solved
        if (disconnected && !shownDisconnectedToast) {
            showToast("All counts match, but the islands aren't fully connected")
            shownDisconnectedToast = true
        } else if (!disconnected) {
            shownDisconnectedToast = false
        }
    }

    private fun edgeBetween(a: Int, b: Int): Int? =
        puzzle.model.edges.indexOfFirst { (it.a == a && it.b == b) || (it.a == b && it.b == a) }.takeIf { it >= 0 }

    private fun neighbor(i: Int, dx: Int, dy: Int): Int? {
        for (k in puzzle.model.adj[i]) {
            val e = puzzle.model.edges[k]
            val o = if (e.a == i) e.b else e.a
            val sx = Integer.signum(puzzle.model.islands[o].x - puzzle.model.islands[i].x)
            val sy = Integer.signum(puzzle.model.islands[o].y - puzzle.model.islands[i].y)
            if (sx == dx && sy == dy) return k
        }
        return null
    }

    private fun islandAt(p: Offset, c: Float): Int? {
        puzzle.model.islands.forEachIndexed { i, isl ->
            val cx = isl.x * c + c / 2; val cy = isl.y * c + c / 2
            if (hypot(cx - p.x, cy - p.y) < c * 0.5f) return i
        }
        return null
    }

    private fun edgeAt(p: Offset, c: Float): Int? {
        var best: Int? = null
        var bd = c * 0.28
        puzzle.model.edges.forEachIndexed { k, e ->
            if (vals[k] == 0) return@forEachIndexed
            val x1 = puzzle.model.islands[e.a].x * c + c / 2; val y1 = puzzle.model.islands[e.a].y * c + c / 2
            val x2 = puzzle.model.islands[e.b].x * c + c / 2; val y2 = puzzle.model.islands[e.b].y * c + c / 2
            val dx = x2 - x1; val dy = y2 - y1
            val lenSq = (dx * dx + dy * dy).toDouble()
            var t = if (lenSq > 0) ((p.x - x1) * dx + (p.y - y1) * dy) / lenSq else 0.0
            t = t.coerceIn(0.0, 1.0)
            val px = x1 + t * dx; val py = y1 + t * dy
            val d = hypot(p.x - px, p.y - py)
            if (d < bd) { bd = d; best = k }
        }
        return best
    }

    /**
     * Tap an island then a highlighted neighbour, drag from an island toward
     * a neighbour, or tap an existing bridge to cycle it. `c` = cell size.
     */
    fun handleInteraction(start: Offset, end: Offset, c: Float) {
        if (solved) return
        val ddx = end.x - start.x; val ddy = end.y - start.y
        val moved = hypot(ddx, ddy) > c * 0.3f
        val di = islandAt(start, c)
        val sel = selected
        if (di != null) {
            if (moved) {
                val (dirx, diry) = if (abs(ddx) > abs(ddy)) (if (ddx > 0) 1 else -1) to 0 else 0 to (if (ddy > 0) 1 else -1)
                neighbor(di, dirx, diry)?.let { cycle(it); selected = null }
            } else if (sel == null || sel == di) {
                selected = if (sel == di) null else di
            } else {
                val k = edgeBetween(sel, di)
                if (k != null && Hashi.validEdge(puzzle.model, vals, status.sums, k)) {
                    cycle(k); selected = null
                } else {
                    selected = di
                }
            }
        } else {
            val e = edgeAt(start, c)
            if (e != null && !moved) cycle(e) else selected = null
        }
    }

    override fun undo() {
        if (solved) return
        vals = history.removeLastOrNull() ?: return
        save()
    }

    override fun reset() {
        if (solved) return
        history.add(vals)
        vals = IntArray(vals.size)
        selected = null
        shownDisconnectedToast = false
        save()
    }

    override fun hint() {
        if (solved) return
        var k = vals.indices.firstOrNull { vals[it] > puzzle.solution[it] }
        var wrong = true
        if (k == null) {
            k = vals.indices.firstOrNull { vals[it] < puzzle.solution[it] }
            wrong = false
        }
        val idx = k ?: return
        hintEdge = idx
        hintIsWrong = wrong
        hintJob?.cancel()
        hintJob = after(2500) { hintEdge = null }
        showToast(if (wrong) "This bridge is wrong" else "A bridge belongs here")
    }

    companion object {
        private val EMPTY = Hashi.Puzzle(Hashi.buildModel(1, 1, listOf(Hashi.Island(0, 0, 0))), IntArray(0), false)
    }
}
