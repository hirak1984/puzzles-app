import SwiftUI

/// Sudoku board: 9x9 grid with thick box separators, given/filled/selected
/// shading, conflict highlighting, and a numeric keypad underneath (this
/// game has no web-app ancestor to port from, unlike Hashi/Tracks, so both
/// the board and the keypad are designed fresh here in the same visual
/// language as the other two boards).
struct SudokuBoardView: View {
    @ObservedObject var engine: SudokuEngine

    private let designCell: CGFloat = 44

    var body: some View {
        VStack(spacing: 12) {
            GeometryReader { geo in
                let C = geo.size.width / 9
                let s = C / designCell
                Canvas { ctx, size in
                    draw(ctx: ctx, C: C, s: s)
                }
                .contentShape(Rectangle())
                .gesture(
                    SpatialTapGesture()
                        .onEnded { v in
                            let x = Int(v.location.x / C), y = Int(v.location.y / C)
                            guard x >= 0, y >= 0, x < 9, y < 9 else { return }
                            engine.selectCell(y * 9 + x)
                        }
                )
            }
            .aspectRatio(1, contentMode: .fit)

            Keypad(engine: engine)
        }
    }

    private func draw(ctx: GraphicsContext, C: CGFloat, s: CGFloat) {
        let st = engine.status
        let sel = engine.selected
        let selRow = sel.map { $0 / 9 }, selCol = sel.map { $0 % 9 }, selBox = sel.map { ($0 / 9 / 3) * 3 + ($0 % 9 / 3) }

        // Cell backgrounds: selected cell, its row/column/box, given cells,
        // hint cell — in that precedence order.
        for i in 0..<81 {
            let x = i % 9, y = i / 9
            let box = (y / 3) * 3 + (x / 3)
            let rect = CGRect(x: CGFloat(x) * C, y: CGFloat(y) * C, width: C, height: C)
            var fill = Theme.bgElevated
            if engine.isGiven(i) { fill = Theme.bgRaised }
            if selRow == y || selCol == x || selBox == box { fill = Theme.bgRaised }
            if engine.hintCell == i { fill = Theme.accentBg }
            if sel == i { fill = Theme.accentBg }
            ctx.fill(Path(rect), with: .color(fill))
        }

        // Digits.
        for i in 0..<81 where engine.values[i] != 0 {
            let x = i % 9, y = i / 9
            let center = CGPoint(x: CGFloat(x) * C + C / 2, y: CGFloat(y) * C + C / 2)
            let color: Color
            if engine.hintCell == i && engine.hintIsWrong { color = Theme.danger }
            else if st.conflict[i] { color = Theme.danger }
            else if engine.isGiven(i) { color = Theme.text }
            else { color = Theme.user }
            let weight: Font.Weight = engine.isGiven(i) ? .semibold : .regular
            ctx.draw(Text("\(engine.values[i])").font(Theme.sans(20 * s, weight: weight)).foregroundColor(color), at: center)
        }

        // Grid lines: thin every cell, thick every 3 (box separators).
        for i in 0...9 {
            let thick = i % 3 == 0
            let w: CGFloat = (thick ? 2.5 : 1) * s
            let color = thick ? Theme.borderStrong : Theme.border
            var h = Path(); h.move(to: CGPoint(x: 0, y: CGFloat(i) * C)); h.addLine(to: CGPoint(x: 9 * C, y: CGFloat(i) * C))
            ctx.stroke(h, with: .color(color), lineWidth: w)
            var v = Path(); v.move(to: CGPoint(x: CGFloat(i) * C, y: 0)); v.addLine(to: CGPoint(x: CGFloat(i) * C, y: 9 * C))
            ctx.stroke(v, with: .color(color), lineWidth: w)
        }
    }
}

private struct Keypad: View {
    @ObservedObject var engine: SudokuEngine

    var body: some View {
        HStack(spacing: 5) {
            ForEach(1...9, id: \.self) { d in
                Button {
                    engine.enterDigit(d)
                } label: {
                    Text("\(d)")
                        .font(Theme.sans(18, weight: .medium))
                        .foregroundColor(Theme.text)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Theme.bgElevated)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Theme.borderStrong))
                        .cornerRadius(6)
                }
                .buttonStyle(.plain)
            }
            Button {
                engine.erase()
            } label: {
                Image(systemName: "delete.left")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(Theme.textMuted)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(Theme.bgElevated)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Theme.borderStrong))
                    .cornerRadius(6)
            }
            .buttonStyle(.plain)
        }
    }
}
