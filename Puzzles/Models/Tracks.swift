import Foundation

/// Train Tracks — model, solver, generator, win check. No UI.
/// Ported from the web app's `tracks.js`.
///
/// The track enters the grid from the left edge of the start row and leaves
/// through the bottom edge of the end column. Row/column clues count how
/// many cells in that line hold track; some cells are given up front.
enum Tracks {
    static let N: UInt8 = 1
    static let E: UInt8 = 2
    static let S: UInt8 = 4
    static let W: UInt8 = 8

    struct Dir { var bit: UInt8; var dx: Int; var dy: Int }
    static let dirs: [Dir] = [
        Dir(bit: N, dx: 0, dy: -1),
        Dir(bit: E, dx: 1, dy: 0),
        Dir(bit: S, dx: 0, dy: 1),
        Dir(bit: W, dx: -1, dy: 0),
    ]

    static func opp(_ b: UInt8) -> UInt8 {
        b == N ? S : b == S ? N : b == E ? W : E
    }

    static func popcount(_ m: UInt8) -> Int { m.nonzeroBitCount }

    struct LevelConfig {
        var n: Int
        var minFrac: Double
        var maxFrac: Double
        var minimize: Bool
    }

    enum Level: String, CaseIterable, Identifiable {
        case easy, medium, hard
        var id: String { rawValue }
    }

    static let levels: [Level: LevelConfig] = [
        .easy: LevelConfig(n: 6, minFrac: 0.4, maxFrac: 0.62, minimize: false),
        .medium: LevelConfig(n: 8, minFrac: 0.4, maxFrac: 0.62, minimize: true),
        .hard: LevelConfig(n: 10, minFrac: 0.4, maxFrac: 0.6, minimize: true),
    ]

    private static func randomPath(_ rnd: Mulberry32, n: Int, start: Int, end: Int, minL: Int, maxL: Int, cap: Int) -> [Int]? {
        var vis = [Bool](repeating: false, count: n * n)
        var path: [Int] = []
        var nodes = 0

        func go(_ c: Int) -> Bool {
            nodes += 1
            if nodes > cap { return false }
            vis[c] = true
            path.append(c)
            var found = false
            if c == end {
                if path.count >= minL { found = true }
            } else if path.count < maxL {
                let x = c % n, y = c / n
                for d in shuffled(rnd, [0, 1, 2, 3]) {
                    let dir = dirs[d]
                    let nx = x + dir.dx, ny = y + dir.dy
                    if nx < 0 || ny < 0 || nx >= n || ny >= n { continue }
                    let nc = ny * n + nx
                    if vis[nc] { continue }
                    if go(nc) { found = true; break }
                    if nodes > cap { break }
                }
            }
            if found { return true }
            vis[c] = false
            path.removeLast()
            return false
        }
        return go(start) ? path : nil
    }

    struct SolveResult {
        var count: Int
        var solution: [UInt8]?
        var aborted: Bool
    }

    /// Enumerate simple paths start -> end consistent with clues and givens.
    static func countSolutions(n: Int, rowC: [Int], colC: [Int], start: Int, end: Int, given: [Int: UInt8], limit: Int = 2, cap: Int = 100_000) -> SolveResult {
        var vis = [Bool](repeating: false, count: n * n)
        var ru = [Int](repeating: 0, count: n)
        var cu = [Int](repeating: 0, count: n)
        var masks = [UInt8](repeating: 0, count: n * n)
        let total = rowC.reduce(0, +)
        let ex = end % n, ey = end / n
        var count = 0, nodes = 0, aborted = false, used = 1
        var solution: [UInt8]? = nil

        func visit(_ c: Int, _ inb: UInt8) {
            if count >= limit || aborted { return }
            nodes += 1
            if nodes > cap { aborted = true; return }
            let x = c % n, y = c / n
            if c == end {
                let m = inb | S
                if let gm = given[c], gm != m { return }
                for i in 0..<n where ru[i] != rowC[i] || cu[i] != colC[i] { return }
                masks[c] = m
                var ok = true
                for (gc, gm) in given where masks[gc] != gm { ok = false; break }
                if ok { count += 1; solution = masks }
                masks[c] = 0
                return
            }
            for dir in dirs {
                if dir.bit == inb { continue }
                let nx = x + dir.dx, ny = y + dir.dy
                if nx < 0 || ny < 0 || nx >= n || ny >= n { continue }
                let nc = ny * n + nx
                if vis[nc] { continue }
                let m = inb | dir.bit
                if let gm = given[c], gm != m { continue }
                if ru[ny] + 1 > rowC[ny] || cu[nx] + 1 > colC[nx] { continue }
                if let ng = given[nc], (ng & opp(dir.bit)) == 0 { continue }
                let rem = total - (used + 1)
                let dist = abs(nx - ex) + abs(ny - ey)
                if rem < dist || ((rem - dist) & 1) != 0 { continue }
                vis[nc] = true; ru[ny] += 1; cu[nx] += 1; used += 1; masks[c] = m
                visit(nc, opp(dir.bit))
                vis[nc] = false; ru[ny] -= 1; cu[nx] -= 1; used -= 1; masks[c] = 0
            }
        }

        vis[start] = true
        ru[start / n] += 1
        cu[start % n] += 1
        visit(start, W)
        return SolveResult(count: count, solution: solution, aborted: aborted)
    }

    struct Puzzle {
        var n: Int
        var start: Int
        var end: Int
        var rowC: [Int]
        var colC: [Int]
        var given: [Int: UInt8]
        var solution: [UInt8]
        var unique: Bool
    }

    static func generate(seed: UInt32, level: Level) -> Puzzle {
        let cfg = levels[level]!
        let n = cfg.n
        let rnd = Mulberry32(seed: seed)

        func isUnique(_ rowC: [Int], _ colC: [Int], _ start: Int, _ end: Int, _ given: [Int: UInt8]) -> Bool {
            let r = countSolutions(n: n, rowC: rowC, colC: colC, start: start, end: end, given: given)
            return r.count == 1 && !r.aborted
        }

        var last: Puzzle? = nil
        for _ in 0..<60 {
            let start = randInt(rnd, n) * n
            let end = (n - 1) * n + randInt(rnd, n)
            if start == end { continue }
            let minL = Int((Double(n * n) * cfg.minFrac).rounded())
            let maxL = Int((Double(n * n) * cfg.maxFrac).rounded())
            guard let path = randomPath(rnd, n: n, start: start, end: end, minL: minL, maxL: maxL, cap: 20_000) else { continue }

            var masks = [UInt8](repeating: 0, count: n * n)
            for i in 1..<path.count {
                let p = path[i - 1], c = path[i]
                if let d = dirs.first(where: { (p % n) + $0.dx == c % n && (p / n) + $0.dy == (c / n) }) {
                    masks[p] |= d.bit
                    masks[c] |= opp(d.bit)
                }
            }
            masks[start] |= W
            masks[end] |= S
            var rowC = [Int](repeating: 0, count: n)
            var colC = [Int](repeating: 0, count: n)
            for c in path { rowC[c / n] += 1; colC[c % n] += 1 }

            var given: [Int: UInt8] = [:]
            var ok = isUnique(rowC, colC, start, end, given)
            for c in shuffled(rnd, path) {
                if ok { break }
                given[c] = masks[c]
                ok = isUnique(rowC, colC, start, end, given)
            }
            var puzzle = Puzzle(n: n, start: start, end: end, rowC: rowC, colC: colC, given: given, solution: masks, unique: ok)
            last = puzzle
            if !ok { continue }
            if cfg.minimize {
                // Sorted: Swift dictionary order is randomized per launch, which
                // made the same seed generate different puzzles across runs.
                // Ascending order also matches the web app (JS integer keys).
                for c in shuffled(rnd, given.keys.sorted()) {
                    let m = given[c]
                    given.removeValue(forKey: c)
                    if !isUnique(rowC, colC, start, end, given) { given[c] = m }
                }
                puzzle.given = given
                last = puzzle
            }
            return puzzle
        }
        if let p = last { return p }
        return Puzzle(n: n, start: 0, end: n * n - 1, rowC: Array(repeating: 0, count: n), colC: Array(repeating: 0, count: n), given: [:], solution: Array(repeating: 0, count: n * n), unique: false)
    }

    struct State {
        var links: [UInt8]
        var locked: [UInt8]
    }

    /// Initial player links (+ which link bits are locked) for a puzzle.
    static func initialState(_ p: Puzzle) -> State {
        var links = [UInt8](repeating: 0, count: p.n * p.n)
        links[p.start] |= W
        links[p.end] |= S
        for (c, m) in p.given {
            links[c] |= m
            for dir in dirs {
                let x = (c % p.n) + dir.dx, y = (c / p.n) + dir.dy
                if (m & dir.bit) != 0 && x >= 0 && y >= 0 && x < p.n && y < p.n {
                    links[y * p.n + x] |= opp(dir.bit)
                }
            }
        }
        return State(links: links, locked: links)
    }

    struct Status {
        var rows: [Int]
        var cols: [Int]
        var rowOk: [Bool]
        var colOk: [Bool]
        var solved: Bool
    }

    /// Follow the track from the entrance; returns per-line counts + solved flag.
    static func status(_ p: Puzzle, _ links: [UInt8]) -> Status {
        var rows = [Int](repeating: 0, count: p.n)
        var cols = [Int](repeating: 0, count: p.n)
        var cells = 0
        for c in 0..<(p.n * p.n) where links[c] != 0 {
            rows[c / p.n] += 1
            cols[c % p.n] += 1
            cells += 1
        }
        var cur = p.start
        var inb: UInt8 = W
        var seen = 0
        var reached = false
        var vis = Set<Int>()
        while !vis.contains(cur) {
            vis.insert(cur)
            let m = links[cur]
            if popcount(m) != 2 || (m & inb) == 0 { break }
            seen += 1
            let out = m & ~inb
            if cur == p.end && out == S { reached = true; break }
            guard let dir = dirs.first(where: { $0.bit == out }) else { break }
            let x = (cur % p.n) + dir.dx, y = (cur / p.n) + dir.dy
            if x < 0 || y < 0 || x >= p.n || y >= p.n { break }
            cur = y * p.n + x
            inb = opp(out)
        }
        let rowOk = rows.indices.map { rows[$0] == p.rowC[$0] }
        let colOk = cols.indices.map { cols[$0] == p.colC[$0] }
        let solved = reached && seen == cells && rowOk.allSatisfy { $0 } && colOk.allSatisfy { $0 }
        return Status(rows: rows, cols: cols, rowOk: rowOk, colOk: colOk, solved: solved)
    }
}
