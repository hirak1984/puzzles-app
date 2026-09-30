import SwiftUI
import Combine

/// Owns one Train Tracks puzzle's live state: player links, marks, undo
/// history, timer, save/restore, and the drag-to-draw interaction rules.
/// Ported from the web app's `tracks-ui.js` `mount()` + the per-game bits
/// of `app.js`.
final class TracksEngine: ObservableObject {
    @Published private(set) var level: Tracks.Level
    @Published private(set) var seed: UInt32
    @Published private(set) var puzzle: Tracks.Puzzle
    @Published var links: [UInt8]
    @Published var marks: Set<Int> = []
    @Published var hintCells: [Int]? = nil
    @Published var solved = false
    @Published var elapsed = 0
    @Published var toast: String? = nil
    @Published var showWin = false
    @Published var winTimeText = ""

    private(set) var locked: [UInt8] = []
    private var history: [(links: [UInt8], marks: Set<Int>)] = []
    private var timer: Timer?
    private var toastTask: DispatchWorkItem?
    private var hintTask: DispatchWorkItem?

    private enum DragMode { case draw, erase }
    private var dragActive = false
    private var dragLast = -1
    private var dragFirst = -1
    private var dragMode: DragMode? = nil
    private var dragBefore: (links: [UInt8], marks: Set<Int>)? = nil
    private var dragChanged = false
    private var dragMoved = false

    init(level: Tracks.Level) {
        self.level = level
        let seed = Self.resumableSeed(level: level)
        self.seed = seed
        Progress.setCurrentSeed(seed, game: "tracks", level: level.rawValue)
        let puzzle = Tracks.generate(seed: seed, level: level)
        self.puzzle = puzzle
        let st = Tracks.initialState(puzzle)
        links = st.links
        locked = st.locked
        restoreIfSaved()
        startTimer()
        if !puzzle.unique { showToast("Note: this puzzle may have more than one solution") }
    }

    deinit { timer?.invalidate() }

    private func restoreIfSaved() {
        if let saved = Progress.loadTracks(level: level.rawValue, seed: seed), saved.links.count == links.count {
            links = saved.links
            marks = Set(saved.marks)
            elapsed = saved.elapsed
        }
    }

    func switchLevel(_ newLevel: Tracks.Level) {
        guard newLevel != level else { return }
        save() // keep this level's puzzle so switching back resumes it
        LastLevel.set(game: "tracks", level: newLevel.rawValue)
        loadNew(level: newLevel, seed: Self.resumableSeed(level: newLevel))
    }

    /// The level's open puzzle if it's still in progress, else a fresh seed.
    private static func resumableSeed(level: Tracks.Level) -> UInt32 {
        if let seed = Progress.currentSeed(game: "tracks", level: level.rawValue),
           Progress.loadTracks(level: level.rawValue, seed: seed) != nil {
            return seed
        }
        return UInt32.random(in: 1...1_000_000)
    }

    func newPuzzle() {
        Progress.clearTracks(level: level.rawValue, seed: seed)
        loadNew(level: level, seed: UInt32.random(in: 1...1_000_000))
    }

    private func loadNew(level: Tracks.Level, seed: UInt32) {
        self.level = level
        self.seed = seed
        Progress.setCurrentSeed(seed, game: "tracks", level: level.rawValue)
        let puzzle = Tracks.generate(seed: seed, level: level)
        self.puzzle = puzzle
        let st = Tracks.initialState(puzzle)
        links = st.links
        locked = st.locked
        marks = []
        history = []
        hintCells = nil
        solved = false
        elapsed = 0
        showWin = false
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

    func pause() { timer?.invalidate(); timer = nil }
    func resume() { if !solved { startTimer() } }

    func save() {
        if solved {
            Progress.clearTracks(level: level.rawValue, seed: seed)
        } else {
            Progress.saveTracks(level: level.rawValue, seed: seed, TracksProgress(links: links, marks: Array(marks), elapsed: elapsed))
        }
    }

    private func showToast(_ msg: String) {
        toastTask?.cancel()
        toast = msg
        let task = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2, execute: task)
    }

    var status: Tracks.Status { Tracks.status(puzzle, links) }

    var statText: String {
        if let s = Stats.forLevel(game: "tracks", level: level.rawValue) {
            return "\(s.solved) solved · best \(fmtTime(s.best ?? 0))"
        }
        return "No solves yet at this level"
    }

    func isLocked(_ c: Int, bit: UInt8) -> Bool { (locked[c] & bit) != 0 }

    var isDragging: Bool { dragActive }

    private func cellAt(_ p: CGPoint, cellSize C: CGFloat) -> Int? {
        let x = Int(floor(p.x / C)), y = Int(floor(p.y / C))
        guard x >= 0, y >= 0, x < puzzle.n, y < puzzle.n else { return nil }
        return y * puzzle.n + x
    }

    @discardableResult
    private func toggle(_ a: Int, _ b: Int, mode: DragMode) -> Bool {
        let n = puzzle.n
        let dx = (b % n) - (a % n), dy = (b / n) - (a / n)
        guard let dir = Tracks.dirs.first(where: { $0.dx == dx && $0.dy == dy }) else { return false }
        let has = (links[a] & dir.bit) != 0
        switch mode {
        case .erase:
            if !has || (locked[a] & dir.bit) != 0 { return false }
            links[a] &= ~dir.bit
            links[b] &= ~Tracks.opp(dir.bit)
            return true
        case .draw:
            if has { return false }
            if Tracks.popcount(links[a]) >= 2 || Tracks.popcount(links[b]) >= 2 { return false }
            links[a] |= dir.bit
            links[b] |= Tracks.opp(dir.bit)
            marks.remove(a); marks.remove(b)
            return true
        }
    }

    func dragBegin(at p: CGPoint, cellSize C: CGFloat) {
        guard !solved, let c = cellAt(p, cellSize: C) else { dragActive = false; return }
        dragActive = true
        dragFirst = c
        dragLast = c
        dragMode = nil
        dragBefore = (links, marks)
        dragChanged = false
        dragMoved = false
    }

    func dragUpdate(to p: CGPoint, cellSize C: CGFloat) {
        guard dragActive, let c = cellAt(p, cellSize: C), c != dragLast else { return }
        dragMoved = true
        let n = puzzle.n
        let dx = (c % n) - (dragLast % n), dy = (c / n) - (dragLast / n)
        if abs(dx) + abs(dy) == 1 {
            if dragMode == nil, let dir = Tracks.dirs.first(where: { $0.dx == dx && $0.dy == dy }) {
                if (locked[dragLast] & dir.bit) == 0 {
                    dragMode = (links[dragLast] & dir.bit) != 0 ? .erase : .draw
                }
            }
            if let mode = dragMode, toggle(dragLast, c, mode: mode) {
                dragChanged = true
            }
            hintCells = nil
            checkSolved()
        }
        dragLast = c
    }

    func dragEnd() {
        guard dragActive else { return }
        dragActive = false
        if !dragMoved && links[dragFirst] == 0 {
            if marks.contains(dragFirst) { marks.remove(dragFirst) } else { marks.insert(dragFirst) }
            dragChanged = true
        }
        if dragChanged, let before = dragBefore {
            history.append(before)
            save()
        }
        checkSolved()
    }

    private func checkSolved() {
        let st = status
        if st.solved && !solved {
            solved = true
            pause()
            save()
            let stat = Stats.recordSolve(game: "tracks", level: level.rawValue, seconds: elapsed)
            winTimeText = stat.best == elapsed ? "\(fmtTime(elapsed)) — a new best!" : "\(fmtTime(elapsed)) · best \(fmtTime(stat.best ?? elapsed))"
            showWin = true
        }
    }

    func undo() {
        guard !solved, let h = history.popLast() else { return }
        links = h.links; marks = h.marks
        save()
    }

    func reset() {
        guard !solved else { return }
        history.append((links, marks))
        let st = Tracks.initialState(puzzle)
        links = st.links
        marks = []
        save()
    }

    func hint() {
        guard !solved else { return }
        var cells: [Int]? = nil
        for c in 0..<links.count {
            let extra = links[c] & ~puzzle.solution[c] & ~locked[c]
            if extra != 0 { cells = [c]; break }
        }
        if cells == nil {
            for c in 0..<links.count where (puzzle.solution[c] & ~links[c]) != 0 {
                cells = [c]; break
            }
        }
        guard let cs = cells else { return }
        hintCells = cs
        hintTask?.cancel()
        let task = DispatchWorkItem { [weak self] in self?.hintCells = nil }
        hintTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: task)
        let wrong = links[cs[0]] & ~puzzle.solution[cs[0]]
        showToast(wrong != 0 ? "Something here is wrong" : "Track belongs through this cell")
    }
}
