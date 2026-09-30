package online.hirak.puzzles.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence. Port of iOS `Support/Store.swift`: SharedPreferences
 * stands in for UserDefaults, with the same key names ("puzzles.…").
 */
object Store {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.getSharedPreferences("puzzles", Context.MODE_PRIVATE)
    }

    fun string(key: String, default: String): String = prefs.getString(key, null) ?: default
    fun setString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()
    fun long(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    fun setLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun json(key: String): JSONObject? = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun setJson(key: String, value: JSONObject) = setString(key, value.toString())
}

fun fmtTime(s: Int): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

class LevelStat(val solved: Int, val best: Int?)

object Stats {
    private const val KEY = "puzzles.stats"

    private fun all(): JSONObject = Store.json(KEY) ?: JSONObject()

    fun totalSolved(game: String): Int {
        val g = all().optJSONObject(game) ?: return 0
        return g.keys().asSequence().sumOf { g.getJSONObject(it).optInt("solved") }
    }

    fun forLevel(game: String, level: String): LevelStat? {
        val l = all().optJSONObject(game)?.optJSONObject(level) ?: return null
        return LevelStat(l.optInt("solved"), if (l.has("best")) l.getInt("best") else null)
    }

    fun recordSolve(game: String, level: String, seconds: Int): LevelStat {
        val s = all()
        val g = s.optJSONObject(game) ?: JSONObject()
        val l = g.optJSONObject(level) ?: JSONObject()
        val solved = l.optInt("solved") + 1
        val prevBest = if (l.has("best")) l.getInt("best") else null
        val best = if (prevBest == null || seconds < prevBest) seconds else prevBest
        l.put("solved", solved).put("best", best)
        g.put(level, l)
        s.put(game, g)
        Store.setJson(KEY, s)
        return LevelStat(solved, best)
    }
}

/**
 * Per-puzzle in-progress state, keyed by (game, level, seed), plus the seed
 * currently open at each (game, level) so leaving and returning resumes it.
 * Each game stores its own fields in a JSON object ("elapsed" + board state).
 */
object Progress {
    private fun key(game: String, level: String, seed: Long) = "puzzles.$game.$level.$seed"

    fun load(game: String, level: String, seed: Long): JSONObject? = Store.json(key(game, level, seed))
    fun save(game: String, level: String, seed: Long, value: JSONObject) = Store.setJson(key(game, level, seed), value)
    fun clear(game: String, level: String, seed: Long) = Store.remove(key(game, level, seed))

    fun currentSeed(game: String, level: String): Long? = Store.long("puzzles.current.$game.$level")
    fun setCurrentSeed(seed: Long, game: String, level: String) = Store.setLong("puzzles.current.$game.$level", seed)
}

object LastLevel {
    fun get(game: String): String = Store.string("puzzles.level.$game", "easy")
    fun set(game: String, level: String) = Store.setString("puzzles.level.$game", level)
}

fun IntArray.toJson(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }
fun JSONArray.toIntArray(): IntArray = IntArray(length()) { getInt(it) }
