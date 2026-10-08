import SwiftUI

/// Small badge icons, ported line-for-line from the web app's inline SVGs
/// in `app.js`'s `GAMES` table.
struct HashiIcon: View {
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 64
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }

            var bridges = Path()
            bridges.move(to: pt(18, 20)); bridges.addLine(to: pt(46, 20))
            bridges.move(to: pt(18, 44)); bridges.addLine(to: pt(46, 44))
            bridges.move(to: pt(18, 22)); bridges.addLine(to: pt(18, 42))
            bridges.move(to: pt(46, 22)); bridges.addLine(to: pt(46, 42))
            ctx.stroke(bridges, with: .color(Theme.accent), style: StrokeStyle(lineWidth: 2.5 * s, lineCap: .round))

            for (x, y) in [(18.0, 20.0), (46.0, 20.0), (18.0, 44.0), (46.0, 44.0)] {
                let c = pt(x, y)
                let r = 8 * s
                let rect = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
                ctx.fill(Path(ellipseIn: rect), with: .color(Theme.bgElevated))
                ctx.stroke(Path(ellipseIn: rect), with: .color(Theme.accent), lineWidth: 2.5 * s)
                ctx.draw(Text("2").font(.system(size: 10 * s, weight: .bold)).foregroundColor(Theme.accent), at: c)
            }
        }
    }
}

struct SudokuIcon: View {
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 64
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }

            // A 3x3 grid (18...46 square), thin lines with a thick outer box.
            var grid = Path()
            for i in 0...3 {
                let o = 18.0 + Double(i) * (28.0 / 3)
                grid.move(to: pt(o, 18)); grid.addLine(to: pt(o, 46))
                grid.move(to: pt(18, o)); grid.addLine(to: pt(46, o))
            }
            ctx.stroke(grid, with: .color(Theme.accent.opacity(0.55)), lineWidth: 1.5 * s)
            let box = Path(CGRect(x: 18 * s, y: 18 * s, width: 28 * s, height: 28 * s))
            ctx.stroke(box, with: .color(Theme.accent), lineWidth: 2.5 * s)

            let cell = 28.0 / 3
            let digits: [(Int, Int, String)] = [(0, 0, "5"), (2, 1, "3"), (1, 2, "8")]
            for (cx, cy, d) in digits {
                let c = pt(18 + (Double(cx) + 0.5) * cell, 18 + (Double(cy) + 0.5) * cell)
                ctx.draw(Text(d).font(.system(size: 11 * s, weight: .bold)).foregroundColor(Theme.accent), at: c)
            }
        }
    }
}

struct TracksIcon: View {
    private func track(_ s: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 8 * s, y: 20 * s))
        p.addLine(to: CGPoint(x: 32 * s, y: 20 * s))
        p.addQuadCurve(to: CGPoint(x: 48 * s, y: 36 * s), control: CGPoint(x: 48 * s, y: 20 * s))
        p.addLine(to: CGPoint(x: 48 * s, y: 56 * s))
        return p
    }

    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 64
            let p = track(s)
            ctx.stroke(p, with: .color(Theme.accent.opacity(0.55)), style: StrokeStyle(lineWidth: 10 * s, dash: [2.5 * s, 5 * s]))
            ctx.stroke(p, with: .color(Theme.accent), style: StrokeStyle(lineWidth: 4 * s))
            ctx.stroke(p, with: .color(Theme.bgElevated), style: StrokeStyle(lineWidth: 1.6 * s))
        }
    }
}
