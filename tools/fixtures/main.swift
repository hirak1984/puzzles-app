import Foundation

// Writes shared/fixtures/puzzles.json from the iOS (Swift) puzzle logic.
// The Android unit tests load that file and require the Kotlin port to
// produce identical output, so the two apps can't silently drift apart.
// Run via tools/parity.sh; don't edit the JSON by hand.

let seeds: [UInt32] = [1, 42, 123_456, 999_999]
var out: [String: Any] = [:]

var rng: [[String: Any]] = []
for seed in seeds + [0, 4_294_967_295] {
    let r = Mulberry32(seed: seed)
    rng.append(["seed": seed, "values": (0..<8).map { _ in r.next() }])
}
out["rng"] = rng

var hashi: [[String: Any]] = []
for level in Hashi.Level.allCases {
    for seed in seeds {
        let p = Hashi.generate(seed: seed, level: level)
        hashi.append([
            "level": level.rawValue, "seed": seed,
            "w": p.model.w, "h": p.model.h,
            "islands": p.model.islands.map { [$0.x, $0.y, $0.n] },
            "edges": p.model.edges.map { [$0.a, $0.b] },
            "solution": p.solution, "unique": p.unique,
        ])
    }
}
out["hashi"] = hashi

var tracks: [[String: Any]] = []
for level in Tracks.Level.allCases {
    for seed in seeds {
        let p = Tracks.generate(seed: seed, level: level)
        tracks.append([
            "level": level.rawValue, "seed": seed, "n": p.n,
            "start": p.start, "end": p.end, "rowC": p.rowC, "colC": p.colC,
            "given": p.given.keys.sorted().map { [$0, Int(p.given[$0]!)] },
            "solution": p.solution.map(Int.init), "unique": p.unique,
        ])
    }
}
out["tracks"] = tracks

var sudoku: [[String: Any]] = []
for level in Sudoku.Level.allCases {
    for seed in seeds {
        let p = Sudoku.generate(seed: seed, level: level)
        sudoku.append([
            "level": level.rawValue, "seed": seed,
            "givens": p.givens, "solution": p.solution, "unique": p.unique,
        ])
    }
}
out["sudoku"] = sudoku

let data = try JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
FileManager.default.createFile(atPath: CommandLine.arguments[1], contents: data)
