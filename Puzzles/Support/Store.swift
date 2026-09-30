import Foundation

/// Local persistence, ported from the web app's `common.js` `store` +
/// stats helpers. UserDefaults stands in for localStorage; keys match the
/// web app's naming so the mental model carries over.
enum Store {
    private static let defaults = UserDefaults.standard

    static func data(forKey key: String) -> Data? { defaults.data(forKey: key) }
    static func set<T: Encodable>(_ value: T, forKey key: String) {
        if let d = try? JSONEncoder().encode(value) { defaults.set(d, forKey: key) }
    }
    static func get<T: Decodable>(_ type: T.Type, forKey key: String) -> T? {
        guard let d = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: d)
    }
    static func remove(forKey key: String) { defaults.removeObject(forKey: key) }

    static func string(forKey key: String, default def: String) -> String {
        defaults.string(forKey: key) ?? def
    }
    static func setString(_ value: String, forKey key: String) {
        defaults.set(value, forKey: key)
    }
}

let fmtTime: (Int) -> String = { s in
    let m = s / 60, sec = s % 60
    return "\(m):\(String(format: "%02d", sec))"
}

struct LevelStat: Codable {
    var solved: Int = 0
    var best: Int? = nil
}

/// [game: [level: LevelStat]], e.g. stats["hashi"]["medium"].
typealias GameStats = [String: [String: LevelStat]]

enum Stats {
    private static let key = "puzzles.stats"

    static func all() -> GameStats {
        Store.get(GameStats.self, forKey: key) ?? [:]
    }

    static func totalSolved(game: String) -> Int {
        let s = all()[game] ?? [:]
        return s.values.reduce(0) { $0 + $1.solved }
    }

    static func forLevel(game: String, level: String) -> LevelStat? {
        all()[game]?[level]
    }

    @discardableResult
    static func recordSolve(game: String, level: String, seconds: Int) -> LevelStat {
        var s = all()
        var g = s[game] ?? [:]
        var l = g[level] ?? LevelStat()
        l.solved += 1
        if l.best == nil || seconds < l.best! { l.best = seconds }
        g[level] = l
        s[game] = g
        Store.set(s, forKey: key)
        return l
    }
}

/// Per-puzzle in-progress state, keyed by (game, level, seed) like the web
/// app's `puzzles:<game>:<level>:<seed>` localStorage entries.
struct HashiProgress: Codable {
    var vals: [Int]
    var elapsed: Int
}

struct TracksProgress: Codable {
    var links: [UInt8]
    var marks: [Int]
    var elapsed: Int
}

struct SudokuProgress: Codable {
    var values: [Int]
    var elapsed: Int
}

enum Progress {
    private static func key(_ game: String, _ level: String, _ seed: UInt32) -> String {
        "puzzles.\(game).\(level).\(seed)"
    }

    /// The puzzle currently open at each (game, level). Engines resume it on
    /// launch / level switch while it still has saved progress, so leaving
    /// the screen mid-game doesn't throw the puzzle away.
    static func currentSeed(game: String, level: String) -> UInt32? {
        guard let n = UserDefaults.standard.object(forKey: "puzzles.current.\(game).\(level)") as? Int else { return nil }
        return UInt32(exactly: n)
    }
    static func setCurrentSeed(_ seed: UInt32, game: String, level: String) {
        UserDefaults.standard.set(Int(seed), forKey: "puzzles.current.\(game).\(level)")
    }

    static func loadHashi(level: String, seed: UInt32) -> HashiProgress? {
        Store.get(HashiProgress.self, forKey: key("hashi", level, seed))
    }
    static func saveHashi(level: String, seed: UInt32, _ p: HashiProgress) {
        Store.set(p, forKey: key("hashi", level, seed))
    }
    static func clearHashi(level: String, seed: UInt32) {
        Store.remove(forKey: key("hashi", level, seed))
    }

    static func loadTracks(level: String, seed: UInt32) -> TracksProgress? {
        Store.get(TracksProgress.self, forKey: key("tracks", level, seed))
    }
    static func saveTracks(level: String, seed: UInt32, _ p: TracksProgress) {
        Store.set(p, forKey: key("tracks", level, seed))
    }
    static func clearTracks(level: String, seed: UInt32) {
        Store.remove(forKey: key("tracks", level, seed))
    }

    static func loadSudoku(level: String, seed: UInt32) -> SudokuProgress? {
        Store.get(SudokuProgress.self, forKey: key("sudoku", level, seed))
    }
    static func saveSudoku(level: String, seed: UInt32, _ p: SudokuProgress) {
        Store.set(p, forKey: key("sudoku", level, seed))
    }
    static func clearSudoku(level: String, seed: UInt32) {
        Store.remove(forKey: key("sudoku", level, seed))
    }
}

enum LastLevel {
    static func get(game: String) -> String {
        Store.string(forKey: "puzzles.level.\(game)", default: "easy")
    }
    static func set(game: String, level: String) {
        Store.setString(level, forKey: "puzzles.level.\(game)")
    }
}
