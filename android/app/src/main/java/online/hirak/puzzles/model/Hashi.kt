package online.hirak.puzzles.model

/**
 * Hashiwokakero (Bridges) — model, solver, generator, win check. No UI.
 * Port of iOS `Models/Hashi.swift`; keep the two in lockstep (see CLAUDE.md).
 */
object Hashi {
    data class Island(val x: Int, val y: Int, val n: Int)

    class Edge(
        val a: Int,
        val b: Int,
        val horizontal: Boolean,
        /** Grid cells strictly between the two islands (index = y*w+x). */
        val cells: IntArray,
    )

    class Model(
        val w: Int,
        val h: Int,
        val islands: List<Island>,
        val edges: List<Edge>,
        /** edge indices touching each island */
        val adj: List<IntArray>,
        /** for each edge, the other edges whose cell-path it shares */
        val crosses: List<IntArray>,
    )

    class LevelConfig(val w: Int, val h: Int, val islands: Int, val extra: Double, val dbl: Double)

    enum class Level { EASY, MEDIUM, HARD;
        val raw: String get() = name.lowercase()
        companion object { fun of(raw: String) = entries.firstOrNull { it.raw == raw } }
    }

    val levels = mapOf(
        Level.EASY to LevelConfig(w = 7, h = 7, islands = 10, extra = 0.15, dbl = 0.3),
        Level.MEDIUM to LevelConfig(w = 9, h = 9, islands = 18, extra = 0.25, dbl = 0.35),
        Level.HARD to LevelConfig(w = 12, h = 16, islands = 48, extra = 0.3, dbl = 0.4),
    )

    private val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    class Puzzle(val model: Model, val solution: IntArray, val unique: Boolean)

    // Candidate edges join two islands that see each other in a straight
    // line with nothing (island or existing edge path) between them.
    fun buildModel(w: Int, h: Int, islands: List<Island>): Model {
        val grid = IntArray(w * h) { -1 }
        islands.forEachIndexed { i, isl -> grid[isl.y * w + isl.x] = i }

        val edges = mutableListOf<Edge>()
        islands.forEachIndexed { i, isl ->
            for ((dx, dy, horiz) in listOf(Triple(1, 0, true), Triple(0, 1, false))) {
                val cells = mutableListOf<Int>()
                var x = isl.x + dx
                var y = isl.y + dy
                while (x < w && y < h) {
                    val j = grid[y * w + x]
                    if (j >= 0) {
                        edges.add(Edge(i, j, horiz, cells.toIntArray()))
                        break
                    }
                    cells.add(y * w + x)
                    x += dx
                    y += dy
                }
            }
        }

        val adj = List(islands.size) { mutableListOf<Int>() }
        edges.forEachIndexed { k, e -> adj[e.a].add(k); adj[e.b].add(k) }

        val byCell = LinkedHashMap<Int, MutableList<Int>>()
        edges.forEachIndexed { k, e -> for (c in e.cells) byCell.getOrPut(c) { mutableListOf() }.add(k) }
        val crosses = List(edges.size) { mutableListOf<Int>() }
        for (list in byCell.values) {
            for (p in list) for (q in list) if (p != q && q !in crosses[p]) crosses[p].add(q)
        }

        return Model(w, h, islands, edges, adj.map { it.toIntArray() }, crosses.map { it.toIntArray() })
    }

    /** Union-find component root per island under the current bridges. */
    private fun componentRoots(model: Model, vals: IntArray): IntArray {
        val n = model.islands.size
        val parent = IntArray(n) { it }
        fun find(x0: Int): Int { var x = x0; while (parent[x] != x) x = parent[x]; return x }
        model.edges.forEachIndexed { k, e ->
            if (vals[k] > 0) {
                val ra = find(e.a); val rb = find(e.b)
                if (ra != rb) parent[ra] = rb
            }
        }
        return IntArray(n) { find(it) }
    }

    fun connected(model: Model, vals: IntArray): Boolean {
        if (model.islands.size <= 1) return true
        val roots = componentRoots(model, vals)
        return roots.all { it == roots[0] }
    }

    class SolveResult(val count: Int, val solution: IntArray?, val aborted: Boolean)

    /** Domain-propagating DFS. Counts connected solutions up to `limit`. */
    fun solve(model: Model, limit: Int = 2, nodeCap: Int = 30_000): SolveResult {
        val nIsl = model.islands.size
        val nE = model.edges.size
        var count = 0; var nodes = 0; var aborted = false
        var solution: IntArray? = null

        // Narrows [lo,hi] per edge using each island's remaining slack, then
        // forbids any edge that crosses one already carrying a bridge.
        fun propagate(lo: IntArray, hi: IntArray): Boolean {
            var changed = true
            while (changed) {
                changed = false
                for (i in 0 until nIsl) {
                    var sl = 0; var sh = 0
                    for (e in model.adj[i]) { sl += lo[e]; sh += hi[e] }
                    val n = model.islands[i].n
                    if (sl > n || sh < n) return false
                    for (e in model.adj[i]) {
                        val nl = maxOf(lo[e], n - (sh - hi[e]))
                        val nh = minOf(hi[e], n - (sl - lo[e]))
                        if (nl > nh) return false
                        if (nl != lo[e] || nh != hi[e]) { lo[e] = nl; hi[e] = nh; changed = true }
                    }
                }
                for (e in 0 until nE) {
                    if (lo[e] <= 0) continue
                    for (c in model.crosses[e]) {
                        if (lo[c] > 0) return false
                        if (hi[c] > 0) { hi[c] = 0; changed = true }
                    }
                }
            }
            return true
        }

        fun dfs(lo0: IntArray, hi0: IntArray) {
            if (count >= limit || aborted) return
            nodes += 1
            if (nodes > nodeCap) { aborted = true; return }
            val lo = lo0.copyOf(); val hi = hi0.copyOf()
            if (!propagate(lo, hi)) return
            var pick = -1
            for (e in 0 until nE) if (lo[e] < hi[e]) { pick = e; break }
            if (pick < 0) {
                if (connected(model, lo)) { count += 1; solution = lo }
                return
            }
            for (v in lo[pick]..hi[pick]) {
                val l2 = lo.copyOf(); val h2 = hi.copyOf()
                l2[pick] = v; h2[pick] = v
                dfs(l2, h2)
                if (count >= limit || aborted) return
            }
        }

        dfs(IntArray(nE), IntArray(nE) { 2 })
        return SolveResult(count, solution, aborted)
    }

    private class BuildResult(val model: Model, val solution: IntArray)
    private class Link(val a: Int, val b: Int, val n: Int)

    // Grow a random connected island graph, then add a few loop-closing bridges.
    private fun build(rnd: Mulberry32, cfg: LevelConfig): BuildResult? {
        val w = cfg.w; val h = cfg.h
        val occ = IntArray(w * h) { -1 }
        val bridge = BooleanArray(w * h)
        val first = Island(randInt(rnd, w), randInt(rnd, h), 0)
        val islands = mutableListOf(first)
        occ[first.y * w + first.x] = 0
        val links = mutableListOf<Link>()

        fun islandNear(x: Int, y: Int): Boolean {
            for ((dx, dy) in dirs) {
                val nx = x + dx; val ny = y + dy
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && occ[ny * w + nx] >= 0) return true
            }
            return false
        }

        var t = 0
        while (islands.size < cfg.islands && t < cfg.islands * 80) {
            t += 1
            val a = randInt(rnd, islands.size)
            val (dx, dy) = dirs[randInt(rnd, 4)]
            val src = islands[a]
            val lens = mutableListOf<Int>()
            var x = src.x; var y = src.y
            var k = 1
            while (k <= 6) {
                x += dx; y += dy
                if (x < 0 || y < 0 || x >= w || y >= h) break
                val c = y * w + x
                if (occ[c] >= 0 || bridge[c]) break
                if (k >= 2 && !islandNear(x, y)) lens.add(k)
                k += 1
            }
            if (lens.isEmpty()) continue
            val kk = lens[randInt(rnd, lens.size)]
            for (s in 1 until kk) bridge[(src.y + dy * s) * w + src.x + dx * s] = true
            val ni = Island(src.x + dx * kk, src.y + dy * kk, 0)
            occ[ni.y * w + ni.x] = islands.size
            links.add(Link(a, islands.size, if (rnd.next() < cfg.dbl) 2 else 1))
            islands.add(ni)
        }
        if (islands.size.toDouble() < cfg.islands.toDouble() * 0.8) return null

        val model = buildModel(w, h, islands)
        val solution = IntArray(model.edges.size)
        fun edgeOf(a: Int, b: Int) = model.edges.indexOfFirst { (it.a == a && it.b == b) || (it.a == b && it.b == a) }
        for (l in links) {
            val idx = edgeOf(l.a, l.b)
            if (idx >= 0) solution[idx] = l.n
        }
        val order = shuffled(rnd, model.edges.indices.toList())
        for (k in order) {
            val e = model.edges[k]
            if (solution[k] != 0 || rnd.next() > cfg.extra) continue
            if (e.cells.any { bridge[it] }) continue
            for (c in e.cells) bridge[c] = true
            solution[k] = if (rnd.next() < cfg.dbl) 2 else 1
        }

        val finalIslands = islands.toMutableList()
        model.edges.forEachIndexed { k, e ->
            finalIslands[e.a] = finalIslands[e.a].copy(n = finalIslands[e.a].n + solution[k])
            finalIslands[e.b] = finalIslands[e.b].copy(n = finalIslands[e.b].n + solution[k])
        }
        val finalModel = Model(model.w, model.h, finalIslands, model.edges, model.adj, model.crosses)
        return BuildResult(finalModel, solution)
    }

    fun generate(seed: Long, level: Level): Puzzle {
        val cfg = levels.getValue(level)
        val rnd = Mulberry32(seed)
        var last: BuildResult? = null
        repeat(400) {
            val p = build(rnd, cfg) ?: return@repeat
            last = p
            val r = solve(p.model, limit = 2)
            if (r.count == 1 && !r.aborted) return Puzzle(p.model, p.solution, unique = true)
        }
        last?.let { return Puzzle(it.model, it.solution, unique = false) }
        // Pathological fallback (should not happen for the fixed level configs above).
        val m = buildModel(1, 1, listOf(Island(0, 0, 0)))
        return Puzzle(m, IntArray(0), unique = false)
    }

    class Status(
        val sums: IntArray,
        val over: BooleanArray,
        val done: BooleanArray,
        val solved: Boolean,
        /**
         * True for islands stranded in a smaller cluster than the largest
         * one, but only once every island's own count is satisfied — i.e.
         * the board looks finished but bridges form two+ separate networks.
         */
        val isolated: BooleanArray,
    )

    /** Per-island bridge totals + overall solved flag for a player's `vals`. */
    fun status(model: Model, vals: IntArray): Status {
        val sums = IntArray(model.islands.size)
        model.edges.forEachIndexed { k, e -> sums[e.a] += vals[k]; sums[e.b] += vals[k] }
        val over = BooleanArray(sums.size) { sums[it] > model.islands[it].n }
        val done = BooleanArray(sums.size) { sums[it] == model.islands[it].n }
        val allDone = done.all { it }
        val roots = componentRoots(model, vals)
        val fullyConnected = roots.all { it == (roots.firstOrNull() ?: 0) }
        val solved = allDone && fullyConnected

        var isolated = BooleanArray(model.islands.size)
        if (allDone && !fullyConnected) {
            val sizes = LinkedHashMap<Int, Int>()
            for (r in roots) sizes[r] = (sizes[r] ?: 0) + 1
            val mainRoot = sizes.maxByOrNull { it.value }?.key
            isolated = BooleanArray(roots.size) { roots[it] != mainRoot }
        }
        return Status(sums, over, done, solved, isolated)
    }

    /** True if placing a bridge on edge k would cross an existing one. */
    fun blocked(model: Model, vals: IntArray, k: Int): Boolean = model.crosses[k].any { vals[it] > 0 }

    /**
     * Can the player usefully put (or keep) a bridge on edge k right now?
     * True if it already carries a bridge (so it can be doubled/removed), or
     * if nothing crosses it and neither end island is already full.
     */
    fun validEdge(model: Model, vals: IntArray, sums: IntArray, k: Int): Boolean {
        if (vals[k] > 0) return true
        if (blocked(model, vals, k)) return false
        val e = model.edges[k]
        return sums[e.a] < model.islands[e.a].n && sums[e.b] < model.islands[e.b].n
    }
}
