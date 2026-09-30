import SwiftUI
import Combine

/// Owns one Hashi puzzle's live state: player bridges, undo history,
/// timer, save/restore, and the tap/drag interaction rules. Ported from
/// the web app's `hashi-ui.js` `mount()` + the per-game bits of `app.js`.
final class HashiEngine: ObservableObject {
    @Published private(set) var level: Hashi.Level
    @Published private(set) var seed: UInt32
    @Published private(set) var puzzle: Hashi.Puzzle
    @Published var vals: [Int]
    @Published var selected: Int? = nil
    @Published var hintEdge: Int? = nil
    /// true = `hintEdge` is a bridge that shouldn't be there (show in red);
    /// false = it's a missing bridge (show in the normal accent color).
    @Published var hintIsWrong = false
    @Published var solved = false
    @Published var elapsed = 0
    @Published var toast: String? = nil
    @Published var showWin = false
    @Published var winTimeText = ""

    private var history: [[Int]] = []
    private var timer: Timer?
    private var toastTask: DispatchWorkItem?
    private var hintTask: DispatchWorkItem?

    init(level: Hashi.Level) {
        self.level = level
        let seed = Self.resumableSeed(level: level)
        self.seed = seed
        Progress.setCurrentSeed(seed, game: "hashi", level: level.rawValue)
        let puzzle = Hashi.generate(seed: seed, level: level)
        self.puzzle = puzzle
        self.vals = Array(repeating: 0, count: puzzle.model.edges.count)
        restoreIfSaved()
        startTimer()
        if !puzzle.unique { showToast("Note: this puzzle may have more than one solution") }
    }

    deinit { timer?.invalidate() }

    private func restoreIfSaved() {
        if let saved = Progress.loadHashi(level: level.rawValue, seed: seed), saved.vals.count == vals.count {
            vals = saved.vals
            elapsed = saved.elapsed
        }
    }

    func switchLevel(_ newLevel: Hashi.Level) {
        guard newLevel != level else { return }
        save() // keep this level's puzzle so switching back resumes it
        LastLevel.set(game: "hashi", level: newLevel.rawValue)
        loadNew(level: newLevel, seed: Self.resumableSeed(level: newLevel))
    }

    /// The level's open puzzle if it's still in progress, else a fresh seed.
    private static func resumableSeed(level: Hashi.Level) -> UInt32 {
        if let seed = Progress.currentSeed(game: "hashi", level: level.rawValue),
           Progress.loadHashi(level: level.rawValue, seed: seed) != nil {
            return seed
        }
        return UInt32.random(in: 1...1_000_000)
    }

    func newPuzzle() {
        Progress.clearHashi(level: level.rawValue, seed: seed)
        loadNew(level: level, seed: UInt32.random(in: 1...1_000_000))
    }

    private func loadNew(level: Hashi.Level, seed: UInt32) {
        self.level = level
        self.seed = seed
        Progress.setCurrentSeed(seed, game: "hashi", level: level.rawValue)
        let puzzle = Hashi.generate(seed: seed, level: level)
        self.puzzle = puzzle
        vals = Array(repeating: 0, count: puzzle.model.edges.count)
        history = []
        selected = nil
        hintEdge = nil
        solved = false
        elapsed = 0
        showWin = false
        shownDisconnectedToast = false
        restoreIfSaved()
        startTimer()
        if !puzzle.unique { showToast("Note: this puzzle may have more than one solution") }
    }

    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self, !self.solved else { return }
            self.elapsed += 1
            if self.elapsed % 5 == 0 { self.save() }
        }
    }

    /// Stop the timer without touching state — for backgrounding.
    func pause() { timer?.invalidate(); timer = nil }
    func resume() { if !solved { startTimer() } }

    func save() {
        if solved {
            Progress.clearHashi(level: level.rawValue, seed: seed)
        } else {
            Progress.saveHashi(level: level.rawValue, seed: seed, HashiProgress(vals: vals, elapsed: elapsed))
        }
    }

    private func showToast(_ msg: String) {
        toastTask?.cancel()
        toast = msg
        let task = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2, execute: task)
    }

    var status: Hashi.Status { Hashi.status(puzzle.model, vals) }

    var statText: String {
        if let s = Stats.forLevel(game: "hashi", level: level.rawValue) {
            return "\(s.solved) solved · best \(fmtTime(s.best ?? 0))"
        }
        return "No solves yet at this level"
    }

    /// Cycles an edge 0 → 1 → 2 → 0, but skips any step that would push
    /// either endpoint's bridge count past its target — e.g. an island
    /// needing only 1 bridge never gets offered a pointless, guaranteed-
    /// wrong double. If every forward step is invalid, this is a no-op with
    /// an explanatory toast rather than silently doing nothing.
    private func cycle(_ k: Int) {
        guard !solved else { return }
        if Hashi.blocked(puzzle.model, vals, k) && vals[k] == 0 {
            showToast("A bridge would cross there")
            return
        }
        let e = puzzle.model.edges[k]
        let sums = status.sums
        let current = vals[k]
        func exceeds(_ newValue: Int) -> Bool {
            let sumA = sums[e.a] - current + newValue
            let sumB = sums[e.b] - current + newValue
            return sumA > puzzle.model.islands[e.a].n || sumB > puzzle.model.islands[e.b].n
        }
        var next = (current + 1) % 3
        while next != 0 && exceeds(next) {
            next = (next + 1) % 3
        }
        if next == current {
            showToast("That island already has enough bridges")
            return
        }
        history.append(vals)
        vals[k] = next
        hintEdge = nil
        save()
        checkSolved()
    }

    private var shownDisconnectedToast = false

    private func checkSolved() {
        let st = status
        if st.solved && !solved {
            solved = true
            pause()
            save()
            let stat = Stats.recordSolve(game: "hashi", level: level.rawValue, seconds: elapsed)
            winTimeText = stat.best == elapsed ? "\(fmtTime(elapsed)) — a new best!" : "\(fmtTime(elapsed)) · best \(fmtTime(stat.best ?? elapsed))"
            showWin = true
            return
        }
        // Every island's own count matches, but bridges form two or more
        // separate networks — flag it (islands get a red ring in the board
        // view via `status.isolated`) instead of leaving it looking finished
        // with no feedback.
        let disconnected = st.done.allSatisfy { $0 } && !st.solved
        if disconnected && !shownDisconnectedToast {
            showToast("All counts match, but the islands aren't fully connected")
            shownDisconnectedToast = true
        } else if !disconnected {
            shownDisconnectedToast = false
        }
    }

    private func edgeBetween(_ a: Int, _ b: Int) -> Int? {
        puzzle.model.edges.firstIndex { ($0.a == a && $0.b == b) || ($0.a == b && $0.b == a) }
    }

    private func neighbor(_ i: Int, dx: Int, dy: Int) -> Int? {
        for k in puzzle.model.adj[i] {
            let e = puzzle.model.edges[k]
            let o = e.a == i ? e.b : e.a
            let sx = sign(puzzle.model.islands[o].x - puzzle.model.islands[i].x)
            let sy = sign(puzzle.model.islands[o].y - puzzle.model.islands[i].y)
            if sx == dx && sy == dy { return k }
        }
        return nil
    }
    private func sign(_ v: Int) -> Int { v > 0 ? 1 : (v < 0 ? -1 : 0) }

    private func islandAt(_ p: CGPoint, cellSize C: CGFloat) -> Int? {
        for (i, isl) in puzzle.model.islands.enumerated() {
            let cx = CGFloat(isl.x) * C + C / 2, cy = CGFloat(isl.y) * C + C / 2
            if hypot(cx - p.x, cy - p.y) < C * 0.5 { return i }
        }
        return nil
    }

    private func edgeAt(_ p: CGPoint, cellSize C: CGFloat) -> Int? {
        var best: Int? = nil
        var bd = Double(C) * 0.28
        for (k, e) in puzzle.model.edges.enumerated() {
            guard vals[k] != 0 else { continue }
            let x1 = CGFloat(puzzle.model.islands[e.a].x) * C + C / 2
            let y1 = CGFloat(puzzle.model.islands[e.a].y) * C + C / 2
            let x2 = CGFloat(puzzle.model.islands[e.b].x) * C + C / 2
            let y2 = CGFloat(puzzle.model.islands[e.b].y) * C + C / 2
            let dx = x2 - x1, dy = y2 - y1
            let lenSq = Double(dx * dx + dy * dy)
            var t = lenSq > 0 ? Double((p.x - x1) * dx + (p.y - y1) * dy) / lenSq : 0
            t = max(0, min(1, t))
            let px = x1 + CGFloat(t) * dx, py = y1 + CGFloat(t) * dy
            let d = Double(hypot(p.x - px, p.y - py))
            if d < bd { bd = d; best = k }
        }
        return best
    }

    /// Reproduces the web board's pointerdown/pointerup logic: tap an
    /// island then a highlighted neighbour, drag from an island toward a
    /// neighbour, or tap an existing bridge to cycle it.
    func handleInteraction(start: CGPoint, end: CGPoint, cellSize C: CGFloat) {
        guard !solved else { return }
        let ddx = end.x - start.x, ddy = end.y - start.y
        let moved = Double(hypot(ddx, ddy)) > Double(C) * 0.3
        if let di = islandAt(start, cellSize: C) {
            if moved {
                let dirx: Int, diry: Int
                if abs(ddx) > abs(ddy) { dirx = ddx > 0 ? 1 : -1; diry = 0 }
                else { dirx = 0; diry = ddy > 0 ? 1 : -1 }
                if let k = neighbor(di, dx: dirx, dy: diry) {
                    cycle(k)
                    selected = nil
                }
            } else if selected == nil || selected == di {
                selected = (selected == di) ? nil : di
            } else if let k = edgeBetween(selected!, di), Hashi.validEdge(puzzle.model, vals, status.sums, k) {
                cycle(k)
                selected = nil
            } else {
                selected = di
            }
        } else if let e = edgeAt(start, cellSize: C), !moved {
            cycle(e)
        } else {
            selected = nil
        }
    }

    func undo() {
        guard !solved, let last = history.popLast() else { return }
        vals = last
        save()
    }

    func reset() {
        guard !solved else { return }
        history.append(vals)
        vals = vals.map { _ in 0 }
        selected = nil
        shownDisconnectedToast = false
        save()
    }

    func hint() {
        guard !solved else { return }
        var k = vals.indices.first { vals[$0] > puzzle.solution[$0] }
        var wrong = true
        if k == nil {
            k = vals.indices.first { vals[$0] < puzzle.solution[$0] }
            wrong = false
        }
        guard let idx = k else { return }
        hintEdge = idx
        hintIsWrong = wrong
        hintTask?.cancel()
        let task = DispatchWorkItem { [weak self] in self?.hintEdge = nil }
        hintTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: task)
        showToast(wrong ? "This bridge is wrong" : "A bridge belongs here")
    }
}
