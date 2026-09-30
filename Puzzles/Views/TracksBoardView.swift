import SwiftUI

/// Train Tracks board: cells, marks, track pieces, entrance/exit gates,
/// grid lines, row/column clues. Drawn with Canvas; ported from
/// `tracks-ui.js` render() and its `trackHalves()` curve-splitting helper.
struct TracksBoardView: View {
    @ObservedObject var engine: TracksEngine

    /// Design constant the original SVG was tuned at.
    private let designCell: CGFloat = 44

    var body: some View {
        let n = engine.puzzle.n
        GeometryReader { geo in
            // viewBox is (n+2) cells square: a 1-cell margin on every side
            // for clues (top/right) and the entrance/exit stubs (left/bottom).
            let C = geo.size.width / CGFloat(n + 2)
            let s = C / designCell
            let origin = CGPoint(x: C, y: C)

            Canvas { ctx, size in
                draw(ctx: ctx, C: C, s: s, origin: origin)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        let p = CGPoint(x: v.location.x - origin.x, y: v.location.y - origin.y)
                        if !engine.isDragging { engine.dragBegin(at: p, cellSize: C) }
                        engine.dragUpdate(to: p, cellSize: C)
                    }
                    .onEnded { _ in engine.dragEnd() }
            )
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private struct Half { var bit: UInt8; var start: CGPoint; var control: CGPoint; var end: CGPoint }

    private func edgePoint(_ b: UInt8, ox: CGFloat, oy: CGFloat, mx: CGFloat, my: CGFloat, C: CGFloat) -> CGPoint {
        switch b {
        case Tracks.N: return CGPoint(x: mx, y: oy)
        case Tracks.S: return CGPoint(x: mx, y: oy + C)
        case Tracks.W: return CGPoint(x: ox, y: my)
        default: return CGPoint(x: ox + C, y: my) // E
        }
    }

    private func halves(x: Int, y: Int, m: UInt8, C: CGFloat) -> [Half] {
        let ox = CGFloat(x) * C, oy = CGFloat(y) * C
        let mx = ox + C / 2, my = oy + C / 2
        let bits = Tracks.dirs.map { $0.bit }.filter { m & $0 != 0 }
        if bits.count == 1 {
            let p = edgePoint(bits[0], ox: ox, oy: oy, mx: mx, my: my, C: C)
            let mid = CGPoint(x: (p.x + mx) / 2, y: (p.y + my) / 2)
            return [Half(bit: bits[0], start: p, control: mid, end: CGPoint(x: mx, y: my))]
        }
        guard bits.count == 2 else { return [] }
        let a = edgePoint(bits[0], ox: ox, oy: oy, mx: mx, my: my, C: C)
        let b = edgePoint(bits[1], ox: ox, oy: oy, mx: mx, my: my, C: C)
        let split = CGPoint(x: (a.x + 2 * mx + b.x) / 4, y: (a.y + 2 * my + b.y) / 4)
        return [
            Half(bit: bits[0], start: a, control: CGPoint(x: (a.x + mx) / 2, y: (a.y + my) / 2), end: split),
            Half(bit: bits[1], start: b, control: CGPoint(x: (b.x + mx) / 2, y: (b.y + my) / 2), end: split),
        ]
    }

    private func stroke(_ ctx: GraphicsContext, _ h: Half, color: Color, width: CGFloat, dash: [CGFloat] = [], opacity: Double = 1) {
        var path = Path()
        path.move(to: h.start)
        path.addQuadCurve(to: h.end, control: h.control)
        let style = StrokeStyle(lineWidth: width, dash: dash)
        ctx.stroke(path, with: .color(color.opacity(opacity)), style: style)
    }

    private func draw(ctx: GraphicsContext, C: CGFloat, s: CGFloat, origin: CGPoint) {
        var ctx = ctx
        ctx.translateBy(x: origin.x, y: origin.y)

        let p = engine.puzzle
        let n = p.n
        let st = engine.status
        let solved = st.solved

        // Cells + empty-cell marks.
        for c in 0..<(n * n) {
            let x = c % n, y = c / n
            let isGiven = p.given[c] != nil
            let isHint = engine.hintCells?.contains(c) ?? false
            let fill = isHint ? Theme.accentBg : (isGiven ? Theme.bgRaised : Theme.bgElevated)
            let rect = CGRect(x: CGFloat(x) * C, y: CGFloat(y) * C, width: C, height: C)
            ctx.fill(Path(rect), with: .color(fill))
            if engine.marks.contains(c) && engine.links[c] == 0 {
                var mark = Path()
                let m: CGFloat = 15 * s
                mark.move(to: CGPoint(x: rect.minX + m, y: rect.minY + m))
                mark.addLine(to: CGPoint(x: rect.maxX - m, y: rect.maxY - m))
                mark.move(to: CGPoint(x: rect.maxX - m, y: rect.minY + m))
                mark.addLine(to: CGPoint(x: rect.minX + m, y: rect.maxY - m))
                ctx.stroke(mark, with: .color(Theme.textSubtle), style: StrokeStyle(lineWidth: 2 * s, lineCap: .round))
            }
        }

        // Track pieces, drawn as two independently-colored half segments so a
        // player-drawn link can meet a given one in the same cell cleanly.
        for c in 0..<(n * n) where engine.links[c] != 0 {
            let x = c % n, y = c / n
            for h in halves(x: x, y: y, m: engine.links[c], C: C) {
                let isUser = !engine.isLocked(c, bit: h.bit)
                let railColor = solved ? Theme.accent : (isUser ? Theme.user : Theme.text)
                let tiesColor = solved ? Theme.accent : (isUser ? Theme.user : Theme.textSubtle)
                let tiesOpacity = solved ? 0.6 : (isUser ? 0.55 : 1.0)
                stroke(ctx, h, color: tiesColor, width: 15 * s, dash: [2.5 * s, 6 * s], opacity: tiesOpacity)
                stroke(ctx, h, color: railColor, width: 9 * s)
                stroke(ctx, h, color: Theme.bgElevated, width: 4.5 * s)
            }
        }

        // Entrance / exit stubs + arrows (always neutral colored, like the web).
        let sy = p.start / n
        let ex = p.end % n
        func gate(_ h: Half) {
            stroke(ctx, h, color: Theme.textSubtle, width: 15 * s, dash: [2.5 * s, 6 * s])
            stroke(ctx, h, color: Theme.text, width: 9 * s)
            stroke(ctx, h, color: Theme.bgElevated, width: 4.5 * s)
        }
        func arrow(at p: CGPoint, rotationDegrees: Double) {
            var tri = Path()
            let w = 7 * s, hgt = 8 * s
            tri.move(to: CGPoint(x: -w, y: -hgt))
            tri.addLine(to: CGPoint(x: w, y: 0))
            tri.addLine(to: CGPoint(x: -w, y: hgt))
            tri.closeSubpath()
            let t = CGAffineTransform(translationX: p.x, y: p.y).rotated(by: rotationDegrees * .pi / 180)
            ctx.fill(tri.applying(t), with: .color(Theme.accent))
        }
        let entranceMid = CGPoint(x: -C / 2, y: CGFloat(sy) * C + C / 2)
        gate(Half(bit: 0, start: CGPoint(x: -C, y: CGFloat(sy) * C + C / 2), control: entranceMid, end: CGPoint(x: 0, y: CGFloat(sy) * C + C / 2)))
        arrow(at: CGPoint(x: -C + 10 * s, y: CGFloat(sy) * C + C / 2), rotationDegrees: 0)
        let exitMid = CGPoint(x: CGFloat(ex) * C + C / 2, y: CGFloat(n) * C + C / 2)
        gate(Half(bit: 0, start: CGPoint(x: CGFloat(ex) * C + C / 2, y: CGFloat(n) * C), control: exitMid, end: CGPoint(x: CGFloat(ex) * C + C / 2, y: CGFloat(n) * C + C)))
        arrow(at: CGPoint(x: CGFloat(ex) * C + C / 2, y: CGFloat(n) * C + C - 10 * s), rotationDegrees: 90)

        // Grid lines.
        var grid = Path()
        for i in 0...n {
            grid.move(to: CGPoint(x: 0, y: CGFloat(i) * C)); grid.addLine(to: CGPoint(x: CGFloat(n) * C, y: CGFloat(i) * C))
            grid.move(to: CGPoint(x: CGFloat(i) * C, y: 0)); grid.addLine(to: CGPoint(x: CGFloat(i) * C, y: CGFloat(n) * C))
        }
        ctx.stroke(grid, with: .color(Theme.border), lineWidth: 1 * s)

        // Clues.
        func clue(_ v: Int, ok: Bool, over: Bool, at p: CGPoint) {
            let color: Color = ok ? Theme.textSubtle : (over ? Theme.danger : Theme.text)
            let weight: Font.Weight = ok ? .regular : .semibold
            ctx.draw(Text("\(v)").font(Theme.sans(17 * s, weight: weight)).foregroundColor(color), at: p)
        }
        for i in 0..<n {
            clue(p.colC[i], ok: st.colOk[i], over: st.cols[i] > p.colC[i], at: CGPoint(x: CGFloat(i) * C + C / 2, y: -C / 2))
            clue(p.rowC[i], ok: st.rowOk[i], over: st.rows[i] > p.rowC[i], at: CGPoint(x: CGFloat(n) * C + C / 2, y: CGFloat(i) * C + C / 2))
        }
    }
}
