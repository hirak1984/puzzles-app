import SwiftUI

/// Toolbar button — ported from the web app's plain/`.primary` button styles.
struct ToolButton: View {
    let title: String
    var primary: Bool = false
    let action: () -> Void

    init(_ title: String, primary: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.primary = primary
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.sans(15, weight: primary ? .semibold : .regular))
                .foregroundColor(primary ? Theme.accentContrast : Theme.text)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .frame(minHeight: 40)
                .background(primary ? Theme.accent : Theme.bgElevated)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(primary ? Color.clear : Theme.borderStrong))
                .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

/// Win modal — ported from the web app's `.win` overlay.
struct WinOverlay: View {
    let timeText: String
    let onNext: () -> Void
    let onClose: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(spacing: 16) {
                VStack(spacing: 4) {
                    Text("Solved!").font(Theme.serif(28, weight: .medium)).foregroundColor(Theme.accent)
                    Text(timeText).font(Theme.sans(15)).foregroundColor(Theme.textMuted)
                }
                HStack(spacing: 8) {
                    ToolButton("Next puzzle", primary: true, action: onNext)
                    ToolButton("Close", action: onClose)
                }
            }
            .padding(24)
            .frame(maxWidth: 320)
            .background(Theme.bgElevated)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
            .padding(16)
        }
        .transition(.opacity)
    }
}

/// Bottom toast — ported from the web app's `#toast`.
struct ToastOverlay: View {
    let message: String?

    var body: some View {
        VStack {
            Spacer()
            if let message {
                Text(message)
                    .font(Theme.sans(14))
                    .foregroundColor(Theme.bg)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Theme.text)
                    .cornerRadius(8)
                    .padding(.bottom, 24)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .animation(.easeOut(duration: 0.2), value: message)
        .allowsHitTesting(false)
    }
}

/// Shared chrome around a puzzle board: title, difficulty picker, timer,
/// board slot, toolbar, stats line, rules disclosure, win overlay, toast.
/// Ported from the shared parts of `app.js` `renderGame()` + `styles.css`.
struct GameScreen<Board: View>: View {
    let title: String
    let levels: [String]
    @Binding var levelRaw: String
    let elapsed: Int
    let statText: String
    let rules: [String]
    let toast: String?
    let solved: Bool
    let winTimeText: String
    @Binding var showWin: Bool
    let onUndo: () -> Void
    let onHint: () -> Void
    let onReset: () -> Void
    let onNew: () -> Void
    let board: () -> Board

    @State private var rulesExpanded = false

    init(
        title: String,
        levels: [String],
        levelRaw: Binding<String>,
        elapsed: Int,
        statText: String,
        rules: [String],
        toast: String?,
        solved: Bool,
        winTimeText: String,
        showWin: Binding<Bool>,
        onUndo: @escaping () -> Void,
        onHint: @escaping () -> Void,
        onReset: @escaping () -> Void,
        onNew: @escaping () -> Void,
        @ViewBuilder board: @escaping () -> Board
    ) {
        self.title = title
        self.levels = levels
        self._levelRaw = levelRaw
        self.elapsed = elapsed
        self.statText = statText
        self.rules = rules
        self.toast = toast
        self.solved = solved
        self.winTimeText = winTimeText
        self._showWin = showWin
        self.onUndo = onUndo
        self.onHint = onHint
        self.onReset = onReset
        self.onNew = onNew
        self.board = board
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(title)
                    .font(Theme.serif(26, weight: .medium))
                    .foregroundColor(Theme.text)

                HStack(spacing: 12) {
                    Picker("Difficulty", selection: $levelRaw) {
                        ForEach(levels, id: \.self) { l in Text(l.capitalized).tag(l) }
                    }
                    .pickerStyle(.segmented)
                    .frame(maxWidth: 280)
                    Spacer()
                    Text(fmtTime(elapsed))
                        .font(.system(size: 18, weight: .regular).monospacedDigit())
                        .foregroundColor(Theme.textMuted)
                }

                board()
                    .padding(8)
                    .background(Theme.bgElevated)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                    .cornerRadius(8)

                HStack(spacing: 8) {
                    ToolButton("Undo", action: onUndo)
                    ToolButton("Hint", action: onHint)
                    ToolButton("Reset", action: onReset)
                    Spacer()
                    ToolButton("New puzzle", primary: true, action: onNew)
                }

                Text(statText).font(.footnote).foregroundColor(Theme.textSubtle)

                DisclosureGroup("How to play", isExpanded: $rulesExpanded) {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(rules, id: \.self) { r in
                            HStack(alignment: .top, spacing: 6) {
                                Text("•").foregroundColor(Theme.textMuted)
                                Text(r).font(Theme.sans(15)).foregroundColor(Theme.textMuted)
                            }
                        }
                    }
                    .padding(.top, 8)
                }
                .font(Theme.sans(15, weight: .medium))
                .tint(Theme.text)
                .padding(14)
                .background(Theme.bgElevated)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                .cornerRadius(8)
            }
            .padding(16)
        }
        .background(Theme.bg.ignoresSafeArea())
        .overlay(ToastOverlay(message: toast))
        .overlay {
            if showWin {
                WinOverlay(timeText: winTimeText, onNext: onNew, onClose: { showWin = false })
            }
        }
        .animation(.easeOut(duration: 0.2), value: showWin)
    }
}
