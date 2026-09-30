package online.hirak.puzzles.model

/**
 * Deterministic seeded RNG (mulberry32). Port of iOS `Models/RNG.swift`,
 * bit-for-bit, so a puzzle is fully determined by (level, seed) on both
 * platforms. Kotlin `Int` arithmetic wraps like Swift's `&+` / `&*`.
 */
class Mulberry32(seed: Long) {
    private var a: Int = seed.toInt()

    /** Next value in [0, 1). */
    fun next(): Double {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

fun randInt(rnd: Mulberry32, n: Int): Int = (rnd.next() * n).toInt()

fun <T> shuffled(rnd: Mulberry32, list: List<T>): List<T> {
    val a = list.toMutableList()
    var i = a.size - 1
    while (i > 0) {
        val j = randInt(rnd, i + 1)
        val tmp = a[i]; a[i] = a[j]; a[j] = tmp
        i -= 1
    }
    return a
}
