import Foundation

/// Deterministic seeded RNG (mulberry32), ported bit-for-bit from the web
/// app's `rng.js` so a puzzle is fully determined by (level, seed).
/// A class (not a struct) so it can be threaded through the generator and
/// solver without `inout` plumbing.
final class Mulberry32 {
    private var a: UInt32

    init(seed: UInt32) {
        self.a = seed
    }

    /// Next value in [0, 1).
    func next() -> Double {
        a = a &+ 0x6d2b79f5
        var t = a
        t = (t ^ (t >> 15)) &* (t | 1)
        t = t ^ (t &+ ((t ^ (t >> 7)) &* (t | 61)))
        return Double(t ^ (t >> 14)) / 4294967296.0
    }
}

func randInt(_ rnd: Mulberry32, _ n: Int) -> Int {
    Int(rnd.next() * Double(n))
}

func shuffled<T>(_ rnd: Mulberry32, _ arr: [T]) -> [T] {
    var a = arr
    var i = a.count - 1
    while i > 0 {
        let j = randInt(rnd, i + 1)
        a.swapAt(i, j)
        i -= 1
    }
    return a
}
