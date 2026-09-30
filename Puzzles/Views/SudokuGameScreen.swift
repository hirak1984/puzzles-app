import SwiftUI

struct SudokuGameScreen: View {
    @StateObject private var engine: SudokuEngine
    @Environment(\.scenePhase) private var scenePhase

    private static let rules = [
        "Fill every empty square with a digit from 1 to 9.",
        "Each row, each column, and each 3×3 box must contain every digit exactly once — no repeats.",
        "Tap a square, then tap a number below to fill it. Tap the same number again to clear it.",
        "Given squares are shaded and locked — they can't be changed.",
    ]

    init() {
        let level = Sudoku.Level(rawValue: LastLevel.get(game: "sudoku")) ?? .easy
        _engine = StateObject(wrappedValue: SudokuEngine(level: level))
    }

    var body: some View {
        GameScreen(
            title: "Sudoku",
            levels: Sudoku.Level.allCases.map(\.rawValue),
            levelRaw: Binding(
                get: { engine.level.rawValue },
                set: { if let l = Sudoku.Level(rawValue: $0) { engine.switchLevel(l) } }
            ),
            elapsed: engine.elapsed,
            statText: engine.statText,
            rules: Self.rules,
            toast: engine.toast,
            solved: engine.solved,
            winTimeText: engine.winTimeText,
            showWin: $engine.showWin,
            onUndo: engine.undo,
            onHint: engine.hint,
            onReset: engine.reset,
            onNew: engine.newPuzzle
        ) {
            SudokuBoardView(engine: engine)
        }
        .navigationTitle("Sudoku")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background || phase == .inactive {
                engine.save()
                engine.pause()
            } else if phase == .active {
                engine.resume()
            }
        }
        .onDisappear { engine.save() }
    }
}
