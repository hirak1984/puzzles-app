package online.hirak.puzzles.model

import kotlin.math.abs

/**
 * Train Tracks — model, solver, generator, win check. No UI.
 * Port of iOS `Models/Tracks.swift`; keep the two in lockstep (see CLAUDE.md).
 *
 * The track enters the grid from the left edge of the start row and leaves
 * through the bottom edge of the end column. Row/column clues count how
 * many cells in that line hold track; some cells are given up front.
 */
object Tracks {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8

    class Dir(val bit: Int, val dx: Int, val dy: Int)
    val dirs = listOf(Dir(N, 0, -1), Dir(E, 1, 0), Dir(S, 0, 1), Dir(W, -1, 0))

    fun opp(b: Int): Int = if (b == N) S else if (b == S) N else if (b == E) W else E
    fun popcount(m: Int): Int = Integer.bitCount(m)

    class LevelConfig(val n: Int, val minFrac: Double, val maxFrac: Double, val minimize: Boolean)

    enum class Level { EASY, MEDIUM, HARD;
        val raw: String get() = name.lowercase()
        companion object { fun of(raw: String) = entries.firstOrNull { it.raw == raw } }
    }

    val levels = mapOf(
        Level.EASY to LevelConfig(n = 6, minFrac = 0.4, maxFrac = 0.62, minimize = false),
        Level.MEDIUM to LevelConfig(n = 8, minFrac = 0.4, maxFrac = 0.62, minimize = true),
        Level.HARD to LevelConfig(n = 10, minFrac = 0.4, maxFrac = 0.6, minimize = true),
    )

    private fun randomPath(rnd: Mulberry32, n: Int, start: Int, end: Int, minL: Int, maxL: Int, cap: Int): List<Int>? {
        val vis = BooleanArray(n * n)
        val path = mutableListOf<Int>()
        var nodes = 0

        fun go(c: Int): Boolean {
            nodes += 1
            if (nodes > cap) return false
            vis[c] = true
            path.add(c)
            var found = false
            if (c == end) {
                if (path.size >= minL) found = true
            } else if (path.size < maxL) {
                val x = c % n; val y = c / n
                for (d in shuffled(rnd, listOf(0, 1, 2, 3))) {
                    val dir = dirs[d]
                    val nx = x + dir.dx; val ny = y + dir.dy
                    if (nx < 0 || ny < 0 || nx >= n || ny >= n) continue
                    val nc = ny * n + nx
                    if (vis[nc]) continue
                    if (go(nc)) { found = true; break }
                    if (nodes > cap) break
                }
            }
            if (found) return true
            vis[c] = false
            path.removeAt(path.size - 1)
            return false
        }
        return if (go(start)) path else null
    }

    class SolveResult(val count: Int, val solution: IntArray?, val aborted: Boolean)

    /** Enumerate simple paths start -> end consistent with clues and givens. */
    fun countSolutions(
        n: Int, rowC: IntArray, colC: IntArray, start: Int, end: Int, given: Map<Int, Int>,
        limit: Int = 2, cap: Int = 100_000,
    ): SolveResult {
        val g = IntArray(n * n) { -1 }
        for ((c, m) in given) g[c] = m
        val givenCells = given.entries.toList()
        val vis = BooleanArray(n * n)
        val ru = IntArray(n)
        val cu = IntArray(n)
        val masks = IntArray(n * n)
        val total = rowC.sum()
        val ex = end % n; val ey = end / n
        var count = 0; var nodes = 0; var aborted = false; var used = 1
        var solution: IntArray? = null

        fun visit(c: Int, inb: Int) {
            if (count >= limit || aborted) return
            nodes += 1
            if (nodes > cap) { aborted = true; return }
            val x = c % n; val y = c / n
            if (c == end) {
                val m = inb or S
                if (g[c] >= 0 && g[c] != m) return
                for (i in 0 until n) if (ru[i] != rowC[i] || cu[i] != colC[i]) return
                masks[c] = m
                if (givenCells.all { masks[it.key] == it.value }) { count += 1; solution = masks.copyOf() }
                masks[c] = 0
                return
            }
            for (dir in dirs) {
                if (dir.bit == inb) continue
                val nx = x + dir.dx; val ny = y + dir.dy
                if (nx < 0 || ny < 0 || nx >= n || ny >= n) continue
                val nc = ny * n + nx
                if (vis[nc]) continue
                val m = inb or dir.bit
                if (g[c] >= 0 && g[c] != m) continue
                if (ru[ny] + 1 > rowC[ny] || cu[nx] + 1 > colC[nx]) continue
                if (g[nc] >= 0 && (g[nc] and opp(dir.bit)) == 0) continue
                val rem = total - (used + 1)
                val dist = abs(nx - ex) + abs(ny - ey)
                if (rem < dist || ((rem - dist) and 1) != 0) continue
                vis[nc] = true; ru[ny] += 1; cu[nx] += 1; used += 1; masks[c] = m
                visit(nc, opp(dir.bit))
                vis[nc] = false; ru[ny] -= 1; cu[nx] -= 1; used -= 1; masks[c] = 0
            }
        }

        vis[start] = true
        ru[start / n] += 1
        cu[start % n] += 1
        visit(start, W)
        return SolveResult(count, solution, aborted)
    }

    class Puzzle(
        val n: Int,
        val start: Int,
        val end: Int,
        val rowC: IntArray,
        val colC: IntArray,
        val given: Map<Int, Int>,
        val solution: IntArray,
        val unique: Boolean,
    )

    fun generate(seed: Long, level: Level): Puzzle {
        val cfg = levels.getValue(level)
        val n = cfg.n
        val rnd = Mulberry32(seed)

        fun isUnique(rowC: IntArray, colC: IntArray, start: Int, end: Int, given: Map<Int, Int>): Boolean {
            val r = countSolutions(n, rowC, colC, start, end, given)
            return r.count == 1 && !r.aborted
        }

        var last: Puzzle? = null
        repeat(60) {
            val start = randInt(rnd, n) * n
            val end = (n - 1) * n + randInt(rnd, n)
            if (start == end) return@repeat
            val minL = Math.round(n * n * cfg.minFrac).toInt()
            val maxL = Math.round(n * n * cfg.maxFrac).toInt()
            val path = randomPath(rnd, n, start, end, minL, maxL, cap = 20_000) ?: return@repeat

            val masks = IntArray(n * n)
            for (i in 1 until path.size) {
                val p = path[i - 1]; val c = path[i]
                val d = dirs.firstOrNull { (p % n) + it.dx == c % n && (p / n) + it.dy == c / n }
                if (d != null) {
                    masks[p] = masks[p] or d.bit
                    masks[c] = masks[c] or opp(d.bit)
                }
            }
            masks[start] = masks[start] or W
            masks[end] = masks[end] or S
            val rowC = IntArray(n)
            val colC = IntArray(n)
            for (c in path) { rowC[c / n] += 1; colC[c % n] += 1 }

            val given = LinkedHashMap<Int, Int>()
            var ok = isUnique(rowC, colC, start, end, given)
            for (c in shuffled(rnd, path)) {
                if (ok) break
                given[c] = masks[c]
                ok = isUnique(rowC, colC, start, end, given)
            }
            var puzzle = Puzzle(n, start, end, rowC, colC, HashMap(given), masks, ok)
            last = puzzle
            if (!ok) return@repeat
            if (cfg.minimize) {
                // Sorted, matching iOS and the web app (see Tracks.swift).
                for (c in shuffled(rnd, given.keys.sorted())) {
                    val m = given.getValue(c)
                    given.remove(c)
                    if (!isUnique(rowC, colC, start, end, given)) given[c] = m
                }
                puzzle = Puzzle(n, start, end, rowC, colC, HashMap(given), masks, ok)
            }
            return puzzle
        }
        last?.let { return it }
        return Puzzle(n, 0, n * n - 1, IntArray(n), IntArray(n), emptyMap(), IntArray(n * n), unique = false)
    }

    class State(val links: IntArray, val locked: IntArray)

    /** Initial player links (+ which link bits are locked) for a puzzle. */
    fun initialState(p: Puzzle): State {
        val links = IntArray(p.n * p.n)
        links[p.start] = links[p.start] or W
        links[p.end] = links[p.end] or S
        for ((c, m) in p.given) {
            links[c] = links[c] or m
            for (dir in dirs) {
                val x = (c % p.n) + dir.dx; val y = (c / p.n) + dir.dy
                if ((m and dir.bit) != 0 && x >= 0 && y >= 0 && x < p.n && y < p.n) {
                    links[y * p.n + x] = links[y * p.n + x] or opp(dir.bit)
                }
            }
        }
        return State(links, links.copyOf())
    }

    class Status(
        val rows: IntArray,
        val cols: IntArray,
        val rowOk: BooleanArray,
        val colOk: BooleanArray,
        val solved: Boolean,
    )

    /** Follow the track from the entrance; returns per-line counts + solved flag. */
    fun status(p: Puzzle, links: IntArray): Status {
        val rows = IntArray(p.n)
        val cols = IntArray(p.n)
        var cells = 0
        for (c in 0 until p.n * p.n) {
            if (links[c] == 0) continue
            rows[c / p.n] += 1; cols[c % p.n] += 1; cells += 1
        }
        var cur = p.start
        var inb = W
        var seen = 0
        var reached = false
        val vis = HashSet<Int>()
        while (cur !in vis) {
            vis.add(cur)
            val m = links[cur]
            if (popcount(m) != 2 || (m and inb) == 0) break
            seen += 1
            val out = m and inb.inv()
            if (cur == p.end && out == S) { reached = true; break }
            val dir = dirs.firstOrNull { it.bit == out } ?: break
            val x = (cur % p.n) + dir.dx; val y = (cur / p.n) + dir.dy
            if (x < 0 || y < 0 || x >= p.n || y >= p.n) break
            cur = y * p.n + x
            inb = opp(out)
        }
        val rowOk = BooleanArray(p.n) { rows[it] == p.rowC[it] }
        val colOk = BooleanArray(p.n) { cols[it] == p.colC[it] }
        val solved = reached && seen == cells && rowOk.all { it } && colOk.all { it }
        return Status(rows, cols, rowOk, colOk, solved)
    }
}
