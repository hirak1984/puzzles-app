import SwiftUI

/// Ported from `app.js`'s `GAMES.hashi` entry + `renderGame()`.
struct HashiGameScreen: View {
    @StateObject private var engine: HashiEngine
    @Environment(\.scenePhase) private var scenePhase

    private static let rules = [
        "Each number is how many bridges must touch that island.",
        "Bridges run straight, horizontally or vertically, and never cross another bridge or island.",
        "At most two bridges join any pair of islands.",
        "All islands must end up connected as one group.",
        "Tap an island, then a highlighted neighbour to add a bridge (tap again for a double, again to remove). You can also drag from an island towards its neighbour, or tap a bridge to cycle it.",
    ]

    init() {
        let level = Hashi.Level(rawValue: LastLevel.get(game: "hashi")) ?? .easy
        _engine = StateObject(wrappedValue: HashiEngine(level: level))
    }

    var body: some View {
        GameScreen(
            title: "Hashi",
            levels: Hashi.Level.allCases.map(\.rawValue),
            levelRaw: Binding(
                get: { engine.level.rawValue },
                set: { if let l = Hashi.Level(rawValue: $0) { engine.switchLevel(l) } }
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
            HashiBoardView(engine: engine)
        }
        .navigationTitle("Hashi")
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
        .disablesSwipeBack()
    }
}
