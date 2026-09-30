package online.hirak.puzzles.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.hirak.puzzles.data.Stats
import online.hirak.puzzles.data.fmtTime

/**
 * What the three iOS engines (`HashiEngine.swift` etc.) each implement
 * separately — timer, toast, win bookkeeping — shared here. Game-specific
 * state and rules live in the subclasses, one per iOS engine.
 */
abstract class GameEngine(val game: String) : ViewModel() {
    var elapsed by mutableIntStateOf(0)
        protected set
    var solved by mutableStateOf(false)
        protected set
    var toast by mutableStateOf<String?>(null)
        private set
    var showWin by mutableStateOf(false)
    var winTimeText by mutableStateOf("")
        private set

    private var timerJob: Job? = null
    private var toastJob: Job? = null

    abstract val levelRaw: String
    abstract val levels: List<String>
    abstract fun switchLevel(raw: String)
    abstract fun save()
    abstract fun undo()
    abstract fun hint()
    abstract fun reset()
    abstract fun newPuzzle()

    protected fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (solved) continue
                elapsed += 1
                if (elapsed % 5 == 0) save()
            }
        }
    }

    /** Stop the timer without touching state — for backgrounding / leaving. */
    fun pause() { timerJob?.cancel(); timerJob = null }
    fun resume() { if (!solved && timerJob == null) startTimer() }

    protected fun showToast(msg: String) {
        toastJob?.cancel()
        toast = msg
        toastJob = viewModelScope.launch { delay(2200); toast = null }
    }

    protected fun after(ms: Long, block: () -> Unit): Job = viewModelScope.launch { delay(ms); block() }

    val statText: String
        get() = Stats.forLevel(game, levelRaw)?.let { "${it.solved} solved · best ${fmtTime(it.best ?: 0)}" }
            ?: "No solves yet at this level"

    protected fun recordWin() {
        solved = true
        pause()
        save()
        val stat = Stats.recordSolve(game, levelRaw, elapsed)
        winTimeText = if (stat.best == elapsed) "${fmtTime(elapsed)} — a new best!"
        else "${fmtTime(elapsed)} · best ${fmtTime(stat.best ?: elapsed)}"
        showWin = true
    }

    protected fun resetCommon() {
        solved = false
        elapsed = 0
        showWin = false
    }

    companion object {
        fun randomSeed(): Long = (1L..1_000_000L).random()
    }
}
