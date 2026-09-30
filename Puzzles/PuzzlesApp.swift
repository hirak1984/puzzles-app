import SwiftUI

@main
struct PuzzlesApp: App {
    @AppStorage("puzzles.theme") private var themeModeRaw = ThemeMode.system.rawValue

    private var themeMode: ThemeMode { ThemeMode(rawValue: themeModeRaw) ?? .system }

    var body: some Scene {
        WindowGroup {
            RootView(themeModeRaw: $themeModeRaw)
                .preferredColorScheme(themeMode.colorScheme)
                .tint(Theme.accent)
        }
    }
}

/// Root navigation shell. The theme toggle lives in the home screen's
/// toolbar, mirroring the web header's icon button (light → dark → system).
struct RootView: View {
    @Binding var themeModeRaw: String
    private var themeMode: ThemeMode { ThemeMode(rawValue: themeModeRaw) ?? .system }

    var body: some View {
        NavigationStack {
            HomeView()
                .navigationDestination(for: GameKind.self) { kind in
                    switch kind {
                    case .hashi: HashiGameScreen()
                    case .tracks: TracksGameScreen()
                    case .sudoku: SudokuGameScreen()
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            themeModeRaw = themeMode.next.rawValue
                        } label: {
                            Image(systemName: themeMode.icon)
                                .foregroundColor(Theme.textMuted)
                        }
                        .accessibilityLabel("Toggle theme (currently \(themeMode.rawValue))")
                    }
                }
        }
        .background(Theme.bg.ignoresSafeArea())
    }
}
