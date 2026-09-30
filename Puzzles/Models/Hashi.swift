import Foundation

/// Hashiwokakero (Bridges) — model, solver, generator, win check. No UI.
/// Ported from the web app's `hashi.js`.
enum Hashi {
    struct Island {
        var x: Int
        var y: Int
        var n: Int
    }

    struct Edge {
        var a: Int
        var b: Int
        var horizontal: Bool
        /// Grid cells strictly between the two islands (index = y*w+x).
        var cells: [Int]
    }

    struct Model {
        var w: Int
        var h: Int
        var islands: [Island]
        var edges: [Edge]
        /// edge indices touching each island
        var adj: [[Int]]
        /// for each edge, the other edges whose cell-path it shares
        var crosses: [[Int]]
    }

    struct LevelConfig {
        var w: Int
        var h: Int
        var islands: Int
        var extra: Double
        var dbl: Double
    }

    enum Level: String, CaseIterable, Identifiable {
        case easy, medium, hard
        var id: String { rawValue }
    }

    static let levels: [Level: LevelConfig] = [
        .easy: LevelConfig(w: 7, h: 7, islands: 10, extra: 0.15, dbl: 0.3),
        .medium: LevelConfig(w: 9, h: 9, islands: 18, extra: 0.25, dbl: 0.35),
        .hard: LevelConfig(w: 12, h: 16, islands: 48, extra: 0.3, dbl: 0.4),
    ]

    private static let dirs: [(dx: Int, dy: Int)] = [(1, 0), (-1, 0), (0, 1), (0, -1)]

    struct Puzzle {
        var model: Model
        var solution: [Int]
        var unique: Bool
    }

    // islands: candidate edges join two islands that see each other in a
    // straight line with nothing (island or existing edge path) between them.
    static func buildModel(w: Int, h: Int, islands: [Island]) -> Model {
        var grid = [Int](repeating: -1, count: w * h)
        for (i, isl) in islands.enumerated() { grid[isl.y * w + isl.x] = i }

        var edges: [Edge] = []
        for (i, isl) in islands.enumerated() {
            for (dx, dy, horiz) in [(1, 0, true), (0, 1, false)] {
                var cells: [Int] = []
                var x = isl.x + dx, y = isl.y + dy
                while x < w && y < h {
                    let j = grid[y * w + x]
                    if j >= 0 {
                        edges.append(Edge(a: i, b: j, horizontal: horiz, cells: cells))
                        break
                    }
                    cells.append(y * w + x)
                    x += dx
                    y += dy
                }
            }
        }

        var adj = Array(repeating: [Int](), count: islands.count)
        for (k, e) in edges.enumerated() {
            adj[e.a].append(k)
            adj[e.b].append(k)
        }

        var byCell: [Int: [Int]] = [:]
        for (k, e) in edges.enumerated() {
            for c in e.cells { byCell[c, default: []].append(k) }
        }
        var crosses = Array(repeating: [Int](), count: edges.count)
        for list in byCell.values {
            for p in list {
                for q in list where p != q && !crosses[p].contains(q) {
                    crosses[p].append(q)
                }
            }
        }

        return Model(w: w, h: h, islands: islands, edges: edges, adj: adj, crosses: crosses)
    }

    /// Union-find component root per island under the current bridges.
    private static func componentRoots(_ model: Model, _ vals: [Int]) -> [Int] {
        let n = model.islands.count
        var parent = Array(0..<n)
        func find(_ x: Int) -> Int {
            var x = x
            while parent[x] != x { x = parent[x] }
            return x
        }
        for (k, e) in model.edges.enumerated() where vals[k] > 0 {
            let ra = find(e.a), rb = find(e.b)
            if ra != rb { parent[ra] = rb }
        }
        return (0..<n).map { find($0) }
    }

    static func connected(_ model: Model, _ vals: [Int]) -> Bool {
        let n = model.islands.count
        if n <= 1 { return true }
        let roots = componentRoots(model, vals)
        return roots.allSatisfy { $0 == roots[0] }
    }

    struct SolveResult {
        var count: Int
        var solution: [Int]?
        var aborted: Bool
    }

    /// Domain-propagating DFS. Counts connected solutions up to `limit`.
    static func solve(_ model: Model, limit: Int = 2, nodeCap: Int = 30_000) -> SolveResult {
        let N = model.islands.count, E = model.edges.count
        var count = 0, nodes = 0, aborted = false
        var solution: [Int]? = nil

        // Narrows [lo,hi] per edge using each island's remaining slack, then
        // forbids any edge that crosses one already carrying a bridge.
        func propagate(_ lo: inout [Int], _ hi: inout [Int]) -> Bool {
            var changed = true
            while changed {
                changed = false
                for i in 0..<N {
                    var sl = 0, sh = 0
                    for e in model.adj[i] { sl += lo[e]; sh += hi[e] }
                    let n = model.islands[i].n
                    if sl > n || sh < n { return false }
                    for e in model.adj[i] {
                        let nl = max(lo[e], n - (sh - hi[e]))
                        let nh = min(hi[e], n - (sl - lo[e]))
                        if nl > nh { return false }
                        if nl != lo[e] || nh != hi[e] { lo[e] = nl; hi[e] = nh; changed = true }
                    }
                }
                for e in 0..<E where lo[e] > 0 {
                    for c in model.crosses[e] {
                        if lo[c] > 0 { return false }
                        if hi[c] > 0 { hi[c] = 0; changed = true }
                    }
                }
            }
            return true
        }

        func dfs(_ lo0: [Int], _ hi0: [Int]) {
            if count >= limit || aborted { return }
            nodes += 1
            if nodes > nodeCap { aborted = true; return }
            var lo = lo0, hi = hi0
            if !propagate(&lo, &hi) { return }
            var pick = -1
            for e in 0..<E where lo[e] < hi[e] { pick = e; break }
            if pick < 0 {
                if connected(model, lo) { count += 1; solution = lo }
                return
            }
            for v in lo[pick]...hi[pick] {
                var l2 = lo, h2 = hi
                l2[pick] = v; h2[pick] = v
                dfs(l2, h2)
                if count >= limit || aborted { return }
            }
        }

        dfs(Array(repeating: 0, count: E), Array(repeating: 2, count: E))
        return SolveResult(count: count, solution: solution, aborted: aborted)
    }

    private struct BuildResult {
        var model: Model
        var solution: [Int]
    }

    // Grow a random connected island graph, then add a few loop-closing bridges.
    private static func build(_ rnd: Mulberry32, _ cfg: LevelConfig) -> BuildResult? {
        let w = cfg.w, h = cfg.h
        var occ = [Int](repeating: -1, count: w * h)
        var bridge = [Bool](repeating: false, count: w * h)
        var islands = [Island(x: randInt(rnd, w), y: randInt(rnd, h), n: 0)]
        occ[islands[0].y * w + islands[0].x] = 0

        struct Link { var a: Int; var b: Int; var n: Int }
        var links: [Link] = []

        func islandNear(_ x: Int, _ y: Int) -> Bool {
            for (dx, dy) in dirs {
                let nx = x + dx, ny = y + dy
                if nx >= 0 && ny >= 0 && nx < w && ny < h && occ[ny * w + nx] >= 0 { return true }
            }
            return false
        }

        var t = 0
        while islands.count < cfg.islands && t < cfg.islands * 80 {
            t += 1
            let a = randInt(rnd, islands.count)
            let (dx, dy) = dirs[randInt(rnd, 4)]
            let src = islands[a]
            var lens: [Int] = []
            var x = src.x, y = src.y
            var k = 1
            while k <= 6 {
                x += dx; y += dy
                if x < 0 || y < 0 || x >= w || y >= h { break }
                let c = y * w + x
                if occ[c] >= 0 || bridge[c] { break }
                if k >= 2 && !islandNear(x, y) { lens.append(k) }
                k += 1
            }
            if lens.isEmpty { continue }
            let kk = lens[randInt(rnd, lens.count)]
            for s in 1..<kk { bridge[(src.y + dy * s) * w + src.x + dx * s] = true }
            let ni = Island(x: src.x + dx * kk, y: src.y + dy * kk, n: 0)
            occ[ni.y * w + ni.x] = islands.count
            links.append(Link(a: a, b: islands.count, n: rnd.next() < cfg.dbl ? 2 : 1))
            islands.append(ni)
        }
        if Double(islands.count) < Double(cfg.islands) * 0.8 { return nil }

        let model = buildModel(w: w, h: h, islands: islands)
        var solution = [Int](repeating: 0, count: model.edges.count)
        func edgeOf(_ a: Int, _ b: Int) -> Int {
            model.edges.firstIndex { ($0.a == a && $0.b == b) || ($0.a == b && $0.b == a) } ?? -1
        }
        for l in links {
            let idx = edgeOf(l.a, l.b)
            if idx >= 0 { solution[idx] = l.n }
        }
        let order = shuffled(rnd, Array(0..<model.edges.count))
        for k in order {
            let e = model.edges[k]
            if solution[k] != 0 || rnd.next() > cfg.extra { continue }
            if e.cells.contains(where: { bridge[$0] }) { continue }
            for c in e.cells { bridge[c] = true }
            solution[k] = rnd.next() < cfg.dbl ? 2 : 1
        }

        var finalIslands = islands
        for (k, e) in model.edges.enumerated() {
            finalIslands[e.a].n += solution[k]
            finalIslands[e.b].n += solution[k]
        }
        var finalModel = model
        finalModel.islands = finalIslands
        return BuildResult(model: finalModel, solution: solution)
    }

    static func generate(seed: UInt32, level: Level) -> Puzzle {
        let cfg = levels[level]!
        let rnd = Mulberry32(seed: seed)
        var last: BuildResult? = nil
        for _ in 0..<400 {
            guard let p = build(rnd, cfg) else { continue }
            last = p
            let r = solve(p.model, limit: 2)
            if r.count == 1 && !r.aborted {
                return Puzzle(model: p.model, solution: p.solution, unique: true)
            }
        }
        if let p = last {
            return Puzzle(model: p.model, solution: p.solution, unique: false)
        }
        // Pathological fallback (should not happen for the fixed level configs above).
        let m = buildModel(w: 1, h: 1, islands: [Island(x: 0, y: 0, n: 0)])
        return Puzzle(model: m, solution: [], unique: false)
    }

    struct Status {
        var sums: [Int]
        var over: [Bool]
        var done: [Bool]
        var solved: Bool
        /// True for islands stranded in a smaller island cluster than the
        /// largest one, but only once every island's own count is already
        /// satisfied — i.e. the board looks finished but bridges form two+
        /// separate networks. Empty (all false) while still mid-solve, so it
        /// never flags a normal, not-yet-connected work in progress.
        var isolated: [Bool]
    }

    /// Per-island bridge totals + overall solved flag for a player's `vals`.
    static func status(_ model: Model, _ vals: [Int]) -> Status {
        var sums = [Int](repeating: 0, count: model.islands.count)
        for (k, e) in model.edges.enumerated() {
            sums[e.a] += vals[k]
            sums[e.b] += vals[k]
        }
        let over = sums.indices.map { sums[$0] > model.islands[$0].n }
        let done = sums.indices.map { sums[$0] == model.islands[$0].n }
        let allDone = done.allSatisfy { $0 }
        let roots = componentRoots(model, vals)
        let fullyConnected = roots.allSatisfy { $0 == roots.first ?? 0 }
        let solved = allDone && fullyConnected

        var isolated = Array(repeating: false, count: model.islands.count)
        if allDone && !fullyConnected {
            var sizes: [Int: Int] = [:]
            for r in roots { sizes[r, default: 0] += 1 }
            let mainRoot = sizes.max { $0.value < $1.value }?.key
            isolated = roots.map { $0 != mainRoot }
        }

        return Status(sums: sums, over: over, done: done, solved: solved, isolated: isolated)
    }

    /// True if placing a bridge on edge k would cross an existing one.
    static func blocked(_ model: Model, _ vals: [Int], _ k: Int) -> Bool {
        model.crosses[k].contains { vals[$0] > 0 }
    }

    /// Can the player usefully put (or keep) a bridge on edge k right now?
    /// True if it already carries a bridge (so it can be doubled/removed), or
    /// if nothing crosses it and neither end island is already full.
    static func validEdge(_ model: Model, _ vals: [Int], _ sums: [Int], _ k: Int) -> Bool {
        if vals[k] > 0 { return true }
        if blocked(model, vals, k) { return false }
        let e = model.edges[k]
        return sums[e.a] < model.islands[e.a].n && sums[e.b] < model.islands[e.b].n
    }
}
