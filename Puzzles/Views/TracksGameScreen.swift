import SwiftUI

/// Ported from `app.js`'s `GAMES.tracks` entry + `renderGame()`.
struct TracksGameScreen: View {
    @StateObject private var engine: TracksEngine
    @Environment(\.scenePhase) private var scenePhase

    private static let rules = [
        "Build one unbroken track from the entrance arrow on the left to the exit arrow at the bottom.",
        "The numbers on the top and right say how many squares in that column or row hold track.",
        "The track never crosses or branches, and it may not touch itself head-on. Every piece of track must belong to the one route.",
        "Some squares are already laid; they cannot be changed.",
        "Drag from square to square to lay track (drag over existing track to erase it). Tap an empty square to mark it as having no track.",
    ]

    init() {
        let level = Tracks.Level(rawValue: LastLevel.get(game: "tracks")) ?? .easy
        _engine = StateObject(wrappedValue: TracksEngine(level: level))
    }

    var body: some View {
        GameScreen(
            title: "Train Tracks",
            levels: Tracks.Level.allCases.map(\.rawValue),
            levelRaw: Binding(
                get: { engine.level.rawValue },
                set: { if let l = Tracks.Level(rawValue: $0) { engine.switchLevel(l) } }
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
            TracksBoardView(engine: engine)
        }
        .navigationTitle("Train Tracks")
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
