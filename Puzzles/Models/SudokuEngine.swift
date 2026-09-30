import SwiftUI
import Combine

/// Owns one Sudoku puzzle's live state: player entries, undo history,
/// timer, save/restore, and cell-select + keypad-entry interaction. Same
/// shape as `HashiEngine`/`TracksEngine`.
final class SudokuEngine: ObservableObject {
    @Published private(set) var level: Sudoku.Level
    @Published private(set) var seed: UInt32
    @Published private(set) var puzzle: Sudoku.Puzzle
    @Published var values: [Int]
    @Published var selected: Int? = nil
    @Published var hintCell: Int? = nil
    /// true = `hintCell` holds a wrong entry (show in red); false = it's an
    /// empty cell the toast names the digit for (show in the accent color).
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

    init(level: Sudoku.Level) {
        self.level = level
        let seed = Self.resumableSeed(level: level)
        self.seed = seed
        Progress.setCurrentSeed(seed, game: "sudoku", level: level.rawValue)
        let puzzle = Sudoku.generate(seed: seed, level: level)
        self.puzzle = puzzle
        self.values = puzzle.givens
        restoreIfSaved()
        startTimer()
        if !puzzle.unique { showToast("Note: this puzzle may have more than one solution") }
    }

    deinit { timer?.invalidate() }

    private func restoreIfSaved() {
        if let saved = Progress.loadSudoku(level: level.rawValue, seed: seed), saved.values.count == values.count {
            values = saved.values
            elapsed = saved.elapsed
        }
    }

    func switchLevel(_ newLevel: Sudoku.Level) {
        guard newLevel != level else { return }
        save() // keep this level's puzzle so switching back resumes it
        LastLevel.set(game: "sudoku", level: newLevel.rawValue)
        loadNew(level: newLevel, seed: Self.resumableSeed(level: newLevel))
    }

    /// The level's open puzzle if it's still in progress, else a fresh seed.
    private static func resumableSeed(level: Sudoku.Level) -> UInt32 {
        if let seed = Progress.currentSeed(game: "sudoku", level: level.rawValue),
           Progress.loadSudoku(level: level.rawValue, seed: seed) != nil {
            return seed
        }
        return UInt32.random(in: 1...1_000_000)
    }

    func newPuzzle() {
        Progress.clearSudoku(level: level.rawValue, seed: seed)
        loadNew(level: level, seed: UInt32.random(in: 1...1_000_000))
    }

    private func loadNew(level: Sudoku.Level, seed: UInt32) {
        self.level = level
        self.seed = seed
        Progress.setCurrentSeed(seed, game: "sudoku", level: level.rawValue)
        let puzzle = Sudoku.generate(seed: seed, level: level)
        self.puzzle = puzzle
        values = puzzle.givens
        history = []
        selected = nil
        hintCell = nil
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
            Progress.clearSudoku(level: level.rawValue, seed: seed)
        } else {
            Progress.saveSudoku(level: level.rawValue, seed: seed, SudokuProgress(values: values, elapsed: elapsed))
        }
    }

    private func showToast(_ msg: String) {
        toastTask?.cancel()
        toast = msg
        let task = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2, execute: task)
    }

    var status: Sudoku.Status { Sudoku.status(puzzle.givens, values) }

    var statText: String {
        if let s = Stats.forLevel(game: "sudoku", level: level.rawValue) {
            return "\(s.solved) solved · best \(fmtTime(s.best ?? 0))"
        }
        return "No solves yet at this level"
    }

    func isGiven(_ i: Int) -> Bool { puzzle.givens[i] != 0 }

    func selectCell(_ i: Int) {
        guard !solved else { return }
        if isGiven(i) { selected = nil; return }
        selected = (selected == i) ? nil : i
    }

    /// Tapping the digit already in the selected cell clears it (toggle),
    /// matching Hashi's tap-to-cycle feel.
    func enterDigit(_ d: Int) {
        guard !solved else { return }
        guard let i = selected, !isGiven(i) else {
            showToast("Tap a square first")
            return
        }
        history.append(values)
        values[i] = (values[i] == d) ? 0 : d
        hintCell = nil
        save()
        checkSolved()
    }

    func erase() {
        guard !solved, let i = selected, !isGiven(i), values[i] != 0 else { return }
        history.append(values)
        values[i] = 0
        hintCell = nil
        save()
    }

    private func checkSolved() {
        let st = status
        if st.solved && !solved {
            solved = true
            pause()
            save()
            let stat = Stats.recordSolve(game: "sudoku", level: level.rawValue, seconds: elapsed)
            winTimeText = stat.best == elapsed ? "\(fmtTime(elapsed)) — a new best!" : "\(fmtTime(elapsed)) · best \(fmtTime(stat.best ?? elapsed))"
            showWin = true
        }
    }

    func undo() {
        guard !solved, let last = history.popLast() else { return }
        values = last
        save()
    }

    func reset() {
        guard !solved else { return }
        history.append(values)
        values = puzzle.givens
        selected = nil
        save()
    }

    /// A wrong entry first (highlighted red, left for the player to fix),
    /// otherwise an empty cell — its digit is named in the toast rather than
    /// auto-filled, same "point, don't solve it for you" spirit as Hashi/Tracks.
    func hint() {
        guard !solved else { return }
        if let idx = (0..<81).first(where: { !isGiven($0) && values[$0] != 0 && values[$0] != puzzle.solution[$0] }) {
            hintCell = idx
            hintIsWrong = true
            showToast("This number is wrong")
        } else if let idx = (0..<81).first(where: { values[$0] == 0 }) {
            hintCell = idx
            hintIsWrong = false
            showToast("A \(puzzle.solution[idx]) belongs here")
        } else {
            return
        }
        hintTask?.cancel()
        let task = DispatchWorkItem { [weak self] in self?.hintCell = nil }
        hintTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: task)
    }
}
