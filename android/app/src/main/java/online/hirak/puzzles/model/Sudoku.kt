package online.hirak.puzzles.model

/**
 * Sudoku — model, solver, generator, win check. No UI.
 * Port of iOS `Models/Sudoku.swift`; keep the two in lockstep (see CLAUDE.md).
 */
object Sudoku {
    /** Target number of given clues left after digging holes (out of 81). */
    class LevelConfig(val givens: Int)

    enum class Level { EASY, MEDIUM, HARD;
        val raw: String get() = name.lowercase()
        companion object { fun of(raw: String) = entries.firstOrNull { it.raw == raw } }
    }

    val levels = mapOf(
        Level.EASY to LevelConfig(givens = 38),
        Level.MEDIUM to LevelConfig(givens = 30),
        Level.HARD to LevelConfig(givens = 24),
    )

    /** `givens`: 81 cells, row-major; 0 = empty. */
    class Puzzle(val givens: IntArray, val solution: IntArray, val unique: Boolean)

    private fun rowOf(i: Int) = i / 9
    private fun colOf(i: Int) = i % 9
    private fun boxOf(i: Int) = (i / 9 / 3) * 3 + (i % 9 / 3)

    class SolveResult(val count: Int, val solution: IntArray?, val aborted: Boolean)

    /**
     * Bitmask backtracking with most-constrained-cell-first ordering.
     * Counts solutions up to `limit` (callers pass 2 to prove uniqueness).
     */
    fun countSolutions(givens: IntArray, limit: Int = 2, nodeCap: Int = 200_000): SolveResult {
        val cells = givens.copyOf()
        val rowMask = IntArray(9); val colMask = IntArray(9); val boxMask = IntArray(9)
        for (i in 0 until 81) {
            if (cells[i] == 0) continue
            val bit = 1 shl (cells[i] - 1)
            rowMask[rowOf(i)] = rowMask[rowOf(i)] or bit
            colMask[colOf(i)] = colMask[colOf(i)] or bit
            boxMask[boxOf(i)] = boxMask[boxOf(i)] or bit
        }
        var count = 0; var nodes = 0; var aborted = false
        var solution: IntArray? = null

        fun candidates(i: Int) = 0x1FF and (rowMask[rowOf(i)] or colMask[colOf(i)] or boxMask[boxOf(i)]).inv()

        // Returns true to stop the search (limit reached or aborted).
        fun backtrack(): Boolean {
            nodes += 1
            if (nodes > nodeCap) { aborted = true; return true }
            var best = -1; var bestPop = 10; var bestMask = 0
            for (i in 0 until 81) {
                if (cells[i] != 0) continue
                val m = candidates(i)
                val pc = Integer.bitCount(m)
                if (pc < bestPop) {
                    bestPop = pc; best = i; bestMask = m
                    if (pc == 0) break
                }
            }
            if (best < 0) {
                count += 1
                if (solution == null) solution = cells.copyOf()
                return count >= limit
            }
            if (bestPop == 0) return false
            val r = rowOf(best); val c = colOf(best); val b = boxOf(best)
            var m = bestMask
            while (m != 0) {
                val bit = m and -m
                m = m and bit.inv()
                cells[best] = Integer.numberOfTrailingZeros(bit) + 1
                rowMask[r] = rowMask[r] or bit; colMask[c] = colMask[c] or bit; boxMask[b] = boxMask[b] or bit
                val stop = backtrack()
                cells[best] = 0
                rowMask[r] = rowMask[r] and bit.inv(); colMask[c] = colMask[c] and bit.inv(); boxMask[b] = boxMask[b] and bit.inv()
                if (stop) return true
            }
            return false
        }

        backtrack()
        return SolveResult(count, solution, aborted)
    }

    /** A random fully-solved grid: sequential fill, shuffled digit order per cell. */
    private fun randomFullGrid(rnd: Mulberry32): IntArray? {
        val cells = IntArray(81)
        val rowMask = IntArray(9); val colMask = IntArray(9); val boxMask = IntArray(9)

        fun fill(i: Int): Boolean {
            if (i == 81) return true
            val r = rowOf(i); val c = colOf(i); val b = boxOf(i)
            var m = 0x1FF and (rowMask[r] or colMask[c] or boxMask[b]).inv()
            val digits = mutableListOf<Int>()
            while (m != 0) {
                val bit = m and -m
                m = m and bit.inv()
                digits.add(Integer.numberOfTrailingZeros(bit) + 1)
            }
            for (d in shuffled(rnd, digits)) {
                val bit = 1 shl (d - 1)
                cells[i] = d
                rowMask[r] = rowMask[r] or bit; colMask[c] = colMask[c] or bit; boxMask[b] = boxMask[b] or bit
                if (fill(i + 1)) return true
                cells[i] = 0
                rowMask[r] = rowMask[r] and bit.inv(); colMask[c] = colMask[c] and bit.inv(); boxMask[b] = boxMask[b] and bit.inv()
            }
            return false
        }
        return if (fill(0)) cells else null
    }

    fun generate(seed: Long, level: Level): Puzzle {
        val cfg = levels.getValue(level)
        val rnd = Mulberry32(seed)
        val full = randomFullGrid(rnd) ?: return Puzzle(IntArray(81), IntArray(81), unique = false)

        val givens = full.copyOf()
        val targetRemoved = 81 - cfg.givens
        var removed = 0
        for (i in shuffled(rnd, (0 until 81).toList())) {
            if (removed >= targetRemoved) break
            val backup = givens[i]
            givens[i] = 0
            val r = countSolutions(givens, limit = 2)
            if (r.count == 1 && !r.aborted) removed += 1 else givens[i] = backup
        }
        val final = countSolutions(givens, limit = 2)
        return Puzzle(givens, full, unique = final.count == 1 && !final.aborted)
    }

    /**
     * `conflict`: true for every cell that shares a row, column or box with
     * another cell holding the same digit.
     */
    class Status(val conflict: BooleanArray, val filledCount: Int, val solved: Boolean)

    fun status(givens: IntArray, values: IntArray): Status {
        val conflict = BooleanArray(81)
        fun markDupes(indices: List<Int>) {
            val seen = HashMap<Int, MutableList<Int>>()
            for (idx in indices) if (values[idx] != 0) seen.getOrPut(values[idx]) { mutableListOf() }.add(idx)
            for (idxs in seen.values) if (idxs.size > 1) for (idx in idxs) conflict[idx] = true
        }
        for (r in 0 until 9) markDupes((0 until 9).map { r * 9 + it })
        for (c in 0 until 9) markDupes((0 until 9).map { it * 9 + c })
        for (br in 0 until 9 step 3) for (bc in 0 until 9 step 3) {
            val idxs = mutableListOf<Int>()
            for (r in 0 until 3) for (c in 0 until 3) idxs.add((br + r) * 9 + (bc + c))
            markDupes(idxs)
        }
        val filledCount = values.count { it != 0 }
        return Status(conflict, filledCount, solved = filledCount == 81 && !conflict.any { it })
    }
}
