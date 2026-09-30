package online.hirak.puzzles

import online.hirak.puzzles.model.Hashi
import online.hirak.puzzles.model.Mulberry32
import online.hirak.puzzles.model.Sudoku
import online.hirak.puzzles.model.Tracks
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-platform parity: shared/fixtures/puzzles.json is written by the iOS
 * Swift puzzle code (tools/fixtures/main.swift, via tools/parity.sh). The
 * Kotlin port must reproduce every puzzle exactly, seed for seed. If this
 * fails after a logic change, the two apps have drifted — fix the other side.
 */
class ParityTest {
    private val fixtures = JSONObject(File(System.getProperty("fixtures")!!).readText())

    private fun JSONArray.ints() = IntArray(length()) { getInt(it) }
    private fun JSONObject.objects(key: String) = getJSONArray(key).let { a -> List(a.length()) { a.getJSONObject(it) } }

    @Test fun rng() {
        for (f in fixtures.objects("rng")) {
            val r = Mulberry32(f.getLong("seed"))
            val expected = f.getJSONArray("values")
            for (i in 0 until expected.length()) {
                assertEquals("seed ${f.getLong("seed")} #$i", expected.getDouble(i), r.next(), 0.0)
            }
        }
    }

    @Test fun hashi() {
        for (f in fixtures.objects("hashi")) {
            val label = "hashi ${f.getString("level")} seed ${f.getLong("seed")}"
            val p = Hashi.generate(f.getLong("seed"), Hashi.Level.of(f.getString("level"))!!)
            assertEquals(label, f.getInt("w"), p.model.w)
            assertEquals(label, f.getInt("h"), p.model.h)
            val islands = f.getJSONArray("islands")
            assertEquals(label, islands.length(), p.model.islands.size)
            p.model.islands.forEachIndexed { i, isl ->
                assertEquals(label, islands.getJSONArray(i).ints().toList(), listOf(isl.x, isl.y, isl.n))
            }
            val edges = f.getJSONArray("edges")
            assertEquals(label, edges.length(), p.model.edges.size)
            p.model.edges.forEachIndexed { k, e ->
                assertEquals(label, edges.getJSONArray(k).ints().toList(), listOf(e.a, e.b))
            }
            assertEquals(label, f.getJSONArray("solution").ints().toList(), p.solution.toList())
            assertEquals(label, f.getBoolean("unique"), p.unique)
            assertTrue(label, Hashi.status(p.model, p.solution).solved)
        }
    }

    @Test fun tracks() {
        for (f in fixtures.objects("tracks")) {
            val label = "tracks ${f.getString("level")} seed ${f.getLong("seed")}"
            val p = Tracks.generate(f.getLong("seed"), Tracks.Level.of(f.getString("level"))!!)
            assertEquals(label, f.getInt("n"), p.n)
            assertEquals(label, f.getInt("start"), p.start)
            assertEquals(label, f.getInt("end"), p.end)
            assertEquals(label, f.getJSONArray("rowC").ints().toList(), p.rowC.toList())
            assertEquals(label, f.getJSONArray("colC").ints().toList(), p.colC.toList())
            val given = f.getJSONArray("given")
            val expectedGiven = (0 until given.length()).map { given.getJSONArray(it).ints().toList() }
            assertEquals(label, expectedGiven, p.given.keys.sorted().map { listOf(it, p.given.getValue(it)) })
            assertEquals(label, f.getJSONArray("solution").ints().toList(), p.solution.toList())
            assertEquals(label, f.getBoolean("unique"), p.unique)
            assertTrue(label, Tracks.status(p, p.solution).solved)
        }
    }

    @Test fun sudoku() {
        for (f in fixtures.objects("sudoku")) {
            val label = "sudoku ${f.getString("level")} seed ${f.getLong("seed")}"
            val p = Sudoku.generate(f.getLong("seed"), Sudoku.Level.of(f.getString("level"))!!)
            assertEquals(label, f.getJSONArray("givens").ints().toList(), p.givens.toList())
            assertEquals(label, f.getJSONArray("solution").ints().toList(), p.solution.toList())
            assertEquals(label, f.getBoolean("unique"), p.unique)
            assertTrue(label, Sudoku.status(p.givens, p.solution).solved)
        }
    }
}
