import SwiftUI

/// Hashi board: dots grid, valid-path highlights, bridges, islands. Drawn
/// with Canvas at whatever cell size fits the available width, matching
/// the web board's SVG-viewBox scaling. Ported from `hashi-ui.js` render().
struct HashiBoardView: View {
    @ObservedObject var engine: HashiEngine

    /// Design constant the original SVG was tuned at (viewBox cell = 40
    /// units); every stroke width/radius below scales from this.
    private let designCell: CGFloat = 40

    var body: some View {
        let model = engine.puzzle.model
        GeometryReader { geo in
            let C = geo.size.width / CGFloat(max(model.w, 1))
            let s = C / designCell

            Canvas { ctx, size in
                draw(ctx: ctx, C: C, s: s)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onEnded { v in
                        engine.handleInteraction(start: v.startLocation, end: v.location, cellSize: C)
                    }
            )
        }
        .aspectRatio(CGFloat(model.w) / CGFloat(max(model.h, 1)), contentMode: .fit)
    }

    private func draw(ctx: GraphicsContext, C: CGFloat, s: CGFloat) {
        let model = engine.puzzle.model
        let st = engine.status
        func center(_ i: Int) -> CGPoint {
            CGPoint(x: CGFloat(model.islands[i].x) * C + C / 2, y: CGFloat(model.islands[i].y) * C + C / 2)
        }

        // Faint grid dots.
        for y in 0..<model.h {
            for x in 0..<model.w {
                let p = CGPoint(x: CGFloat(x) * C + C / 2, y: CGFloat(y) * C + C / 2)
                let r = 1.5 * s
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)), with: .color(Theme.borderStrong))
            }
        }

        // Paths the selected island can still use.
        if let sel = engine.selected {
            for k in model.adj[sel] where Hashi.validEdge(model, engine.vals, st.sums, k) {
                let e = model.edges[k]
                var path = Path()
                path.move(to: center(e.a)); path.addLine(to: center(e.b))
                ctx.stroke(path, with: .color(Theme.accent.opacity(0.28)), style: StrokeStyle(lineWidth: 14 * s, lineCap: .round))
                let o = e.a == sel ? e.b : e.a
                let r = 19 * s
                ctx.stroke(Path(ellipseIn: CGRect(x: center(o).x - r, y: center(o).y - r, width: r * 2, height: r * 2)), with: .color(Theme.accent), lineWidth: 2 * s)
            }
        }

        // Hint line + bridges.
        for (k, e) in model.edges.enumerated() {
            let a = center(e.a), b = center(e.b)
            if engine.hintEdge == k {
                let hintColor = engine.hintIsWrong ? Theme.danger : Theme.accent
                var path = Path(); path.move(to: a); path.addLine(to: b)
                ctx.stroke(path, with: .color(hintColor.opacity(0.35)), style: StrokeStyle(lineWidth: 9 * s, lineCap: .round))
            }
            let n = engine.vals[k]
            guard n > 0 else { continue }
            let color = st.solved ? Theme.accent : Theme.text
            for i in 0..<n {
                let off: CGFloat = n == 1 ? 0 : (i == 0 ? -3.5 * s : 3.5 * s)
                let dx: CGFloat = e.horizontal ? 0 : off
                let dy: CGFloat = e.horizontal ? off : 0
                var path = Path()
                path.move(to: CGPoint(x: a.x + dx, y: a.y + dy))
                path.addLine(to: CGPoint(x: b.x + dx, y: b.y + dy))
                ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2.5 * s, lineCap: .round))
            }
        }

        // Islands.
        for (i, isl) in model.islands.enumerated() {
            let p = center(i)
            let selected = engine.selected == i
            let (fill, stroke, textColor): (Color, Color, Color) = {
                if st.over[i] || st.isolated[i] { return (Theme.dangerBg, Theme.danger, Theme.danger) }
                if st.done[i] { return (Theme.accentBg, Theme.accent, Theme.accent) }
                return (Theme.bgElevated, Theme.text, Theme.text)
            }()
            let circleFill = selected ? Theme.accentBg : fill
            let circleStroke = selected ? Theme.accent : stroke
            let strokeWidth = (selected ? 3.0 : 2.5) * s
            let r = 15 * s
            let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
            ctx.fill(Path(ellipseIn: rect), with: .color(circleFill))
            ctx.stroke(Path(ellipseIn: rect), with: .color(circleStroke), lineWidth: strokeWidth)
            ctx.draw(
                Text("\(isl.n)").font(Theme.sans(17 * s, weight: .semibold)).foregroundColor(textColor),
                at: p
            )
        }
    }
}
