import SwiftUI

/// Ported from `app.js` `renderHome()` / `.cards`/`.card` in `styles.css`.
struct HomeView: View {
    @State private var hashiSolved = Stats.totalSolved(game: "hashi")
    @State private var tracksSolved = Stats.totalSolved(game: "tracks")
    @State private var sudokuSolved = Stats.totalSolved(game: "sudoku")

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text("Three logic puzzles, generated fresh every time.")
                    .font(Theme.serif(24, weight: .medium))
                    .foregroundColor(Theme.text)
                Text("No ads, no accounts — progress stays on this device.")
                    .font(Theme.sans(15))
                    .foregroundColor(Theme.textMuted)
                    .padding(.bottom, 16)

                NavigationLink(value: GameKind.hashi) {
                    card(icon: AnyView(HashiIcon()), title: "Hashi", blurb: "Connect the islands with bridges.", solved: hashiSolved)
                }
                NavigationLink(value: GameKind.tracks) {
                    card(icon: AnyView(TracksIcon()), title: "Train Tracks", blurb: "Lay a continuous track from entrance to exit.", solved: tracksSolved)
                }
                NavigationLink(value: GameKind.sudoku) {
                    card(icon: AnyView(SudokuIcon()), title: "Sudoku", blurb: "Fill the grid so every row, column and box holds 1–9.", solved: sudokuSolved)
                }
            }
            .padding(16)
        }
        .background(Theme.bg.ignoresSafeArea())
        .navigationTitle("Puzzles")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            hashiSolved = Stats.totalSolved(game: "hashi")
            tracksSolved = Stats.totalSolved(game: "tracks")
            sudokuSolved = Stats.totalSolved(game: "sudoku")
        }
    }

    private func card(icon: AnyView, title: String, blurb: String, solved: Int) -> some View {
        HStack(spacing: 16) {
            icon
                .frame(width: 64, height: 64)
                .padding(4)
                .background(Theme.accentBg)
                .cornerRadius(8)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Theme.serif(22, weight: .semibold)).foregroundColor(Theme.text)
                Text(blurb).font(Theme.sans(15)).foregroundColor(Theme.textMuted)
                Text(solved > 0 ? "\(solved) solved" : "Not played yet")
                    .font(Theme.sans(13))
                    .foregroundColor(Theme.textSubtle)
            }
            Spacer()
        }
        .padding(16)
        .background(Theme.bgElevated)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
        .cornerRadius(8)
    }
}

enum GameKind: Hashable {
    case hashi, tracks, sudoku
}
