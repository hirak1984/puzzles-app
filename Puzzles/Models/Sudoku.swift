import Foundation

/// Sudoku — model, solver, generator, win check. No UI. Follows the same
/// shape as `Hashi.swift`/`Tracks.swift`: a bitmask backtracking solver used
/// both to build a full random grid and to prove each generated puzzle has
/// exactly one solution, by counting solutions up to a small limit.
enum Sudoku {
    struct LevelConfig {
        /// Target number of given clues left after digging holes (out of 81).
        var givens: Int
    }

    enum Level: String, CaseIterable, Identifiable {
        case easy, medium, hard
        var id: String { rawValue }
    }

    static let levels: [Level: LevelConfig] = [
        .easy: LevelConfig(givens: 38),
        .medium: LevelConfig(givens: 30),
        .hard: LevelConfig(givens: 24),
    ]

    struct Puzzle {
        /// 81 cells, row-major; 0 = empty.
        var givens: [Int]
        var solution: [Int]
        var unique: Bool
    }

    private static func rowOf(_ i: Int) -> Int { i / 9 }
    private static func colOf(_ i: Int) -> Int { i % 9 }
    private static func boxOf(_ i: Int) -> Int { (i / 9 / 3) * 3 + (i % 9 / 3) }

    struct SolveResult {
        var count: Int
        var solution: [Int]?
        var aborted: Bool
    }

    /// Bitmask backtracking with most-constrained-cell-first ordering.
    /// Counts solutions up to `limit` (used both to solve and to prove
    /// uniqueness — callers pass `limit: 2` and only care whether count==1).
    static func countSolutions(_ givens: [Int], limit: Int = 2, nodeCap: Int = 200_000) -> SolveResult {
        var cells = givens
        var rowMask = [Int](repeating: 0, count: 9)
        var colMask = [Int](repeating: 0, count: 9)
        var boxMask = [Int](repeating: 0, count: 9)
        for i in 0..<81 where cells[i] != 0 {
            let bit = 1 << (cells[i] - 1)
            rowMask[rowOf(i)] |= bit
            colMask[colOf(i)] |= bit
            boxMask[boxOf(i)] |= bit
        }

        var count = 0, nodes = 0, aborted = false
        var solution: [Int]? = nil

        func candidates(_ i: Int) -> Int {
            0x1FF & ~(rowMask[rowOf(i)] | colMask[colOf(i)] | boxMask[boxOf(i)])
        }

        // Returns true to stop the search (limit reached or aborted).
        func backtrack() -> Bool {
            nodes += 1
            if nodes > nodeCap { aborted = true; return true }
            var best = -1, bestPop = 10, bestMask = 0
            for i in 0..<81 where cells[i] == 0 {
                let m = candidates(i)
                let pc = m.nonzeroBitCount
                if pc < bestPop {
                    bestPop = pc; best = i; bestMask = m
                    if pc == 0 { break }
                }
            }
            if best < 0 {
                count += 1
                if solution == nil { solution = cells }
                return count >= limit
            }
            if bestPop == 0 { return false }
            let r = rowOf(best), c = colOf(best), b = boxOf(best)
            var m = bestMask
            while m != 0 {
                let bit = m & (-m)
                m &= ~bit
                cells[best] = bit.trailingZeroBitCount + 1
                rowMask[r] |= bit; colMask[c] |= bit; boxMask[b] |= bit
                let stop = backtrack()
                cells[best] = 0
                rowMask[r] &= ~bit; colMask[c] &= ~bit; boxMask[b] &= ~bit
                if stop { return true }
            }
            return false
        }

        _ = backtrack()
        return SolveResult(count: count, solution: solution, aborted: aborted)
    }

    /// A random fully-solved grid: sequential cell fill with shuffled digit
    /// order per cell, backtracking on dead ends.
    private static func randomFullGrid(_ rnd: Mulberry32) -> [Int]? {
        var cells = [Int](repeating: 0, count: 81)
        var rowMask = [Int](repeating: 0, count: 9)
        var colMask = [Int](repeating: 0, count: 9)
        var boxMask = [Int](repeating: 0, count: 9)

        func fill(_ i: Int) -> Bool {
            if i == 81 { return true }
            let r = rowOf(i), c = colOf(i), b = boxOf(i)
            let avail = 0x1FF & ~(rowMask[r] | colMask[c] | boxMask[b])
            var digits: [Int] = []
            var m = avail
            while m != 0 {
                let bit = m & (-m)
                m &= ~bit
                digits.append(bit.trailingZeroBitCount + 1)
            }
            for d in shuffled(rnd, digits) {
                let bit = 1 << (d - 1)
                cells[i] = d
                rowMask[r] |= bit; colMask[c] |= bit; boxMask[b] |= bit
                if fill(i + 1) { return true }
                cells[i] = 0
                rowMask[r] &= ~bit; colMask[c] &= ~bit; boxMask[b] &= ~bit
            }
            return false
        }
        return fill(0) ? cells : nil
    }

    static func generate(seed: UInt32, level: Level) -> Puzzle {
        let cfg = levels[level]!
        let rnd = Mulberry32(seed: seed)
        guard let full = randomFullGrid(rnd) else {
            // Should never happen (a full grid always exists); fail soft.
            let empty = [Int](repeating: 0, count: 81)
            return Puzzle(givens: empty, solution: empty, unique: false)
        }

        var givens = full
        let targetRemoved = 81 - cfg.givens
        var removed = 0
        for i in shuffled(rnd, Array(0..<81)) {
            if removed >= targetRemoved { break }
            let backup = givens[i]
            givens[i] = 0
            let r = countSolutions(givens, limit: 2)
            if r.count == 1 && !r.aborted {
                removed += 1
            } else {
                givens[i] = backup
            }
        }

        let final = countSolutions(givens, limit: 2)
        return Puzzle(givens: givens, solution: full, unique: final.count == 1 && !final.aborted)
    }

    struct Status {
        /// True for every cell that shares a row, column or box with another
        /// cell holding the same digit.
        var conflict: [Bool]
        var filledCount: Int
        var solved: Bool
    }

    static func status(_ givens: [Int], _ values: [Int]) -> Status {
        var conflict = [Bool](repeating: false, count: 81)
        func markDupes(_ indices: [Int]) {
            var seen: [Int: [Int]] = [:]
            for idx in indices where values[idx] != 0 {
                seen[values[idx], default: []].append(idx)
            }
            for (_, idxs) in seen where idxs.count > 1 {
                for idx in idxs { conflict[idx] = true }
            }
        }
        for r in 0..<9 { markDupes((0..<9).map { r * 9 + $0 }) }
        for c in 0..<9 { markDupes((0..<9).map { $0 * 9 + c }) }
        for br in stride(from: 0, to: 9, by: 3) {
            for bc in stride(from: 0, to: 9, by: 3) {
                var idxs: [Int] = []
                for r in 0..<3 { for c in 0..<3 { idxs.append((br + r) * 9 + (bc + c)) } }
                markDupes(idxs)
            }
        }
        let filledCount = values.reduce(0) { $0 + ($1 != 0 ? 1 : 0) }
        let solved = filledCount == 81 && !conflict.contains(true)
        return Status(conflict: conflict, filledCount: filledCount, solved: solved)
    }
}
