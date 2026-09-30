package online.hirak.puzzles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.lifecycle.compose.LifecycleStartEffect
import online.hirak.puzzles.data.fmtTime
import online.hirak.puzzles.game.GameEngine

/** Text in the app's fonts/colors (BasicText: no Material theming needed). */
@Composable
fun Txt(
    text: String,
    size: Int,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    serif: Boolean = false,
    modifier: Modifier = Modifier,
    tabular: Boolean = false,
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = if (serif) Fonts.serif else Fonts.sans,
            fontFeatureSettings = if (tabular) "tnum" else null,
        ),
    )
}

/** Toolbar button — iOS `ToolButton`. */
@Composable
fun ToolButton(title: String, primary: Boolean = false, onClick: () -> Unit) {
    val c = LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (primary) c.accent else c.bgElevated)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.borderStrong, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(title, 15, if (primary) c.accentContrast else c.text, if (primary) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Segmented difficulty picker — iOS `Picker(.segmented)`. */
@Composable
fun LevelPicker(levels: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.bgRaised)
            .padding(2.dp),
    ) {
        for (l in levels) {
            val on = l == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) c.bgElevated else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, c.border, RoundedCornerShape(7.dp)) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(l) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(l.replaceFirstChar { it.uppercase() }, 14, c.text, if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Win modal — iOS `WinOverlay`. */
@Composable
fun WinOverlay(timeText: String, onNext: () -> Unit, onClose: () -> Unit) {
    val c = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.bgElevated)
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt("Solved!", 28, c.accent, FontWeight.Medium, serif = true)
                Txt(timeText, 15, c.textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Next puzzle", primary = true, onClick = onNext)
                ToolButton("Close", onClick = onClose)
            }
        }
    }
}

/** Bottom toast — iOS `ToastOverlay`. */
@Composable
fun ToastOverlay(message: String?) {
    val c = LocalPalette.current
    // Keep showing the last text while the exit animation runs.
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Box(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.text)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Txt(last, 14, c.bg) }
        }
    }
}

/**
 * Shared chrome around a puzzle board: title, difficulty picker, timer,
 * board slot, toolbar, stats line, rules disclosure, win overlay, toast.
 * Port of iOS `GameScreen` in `Views/Components.swift`, plus the per-screen
 * lifecycle handling (save + pause when backgrounded or left).
 */
@Composable
fun GameScreen(title: String, engine: GameEngine, rules: List<String>, board: @Composable () -> Unit) {
    val c = LocalPalette.current
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(engine) {
        engine.resume()
        onStopOrDispose { engine.save(); engine.pause() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt(title, 26, c.text, FontWeight.Medium, serif = true)

            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelPicker(engine.levels, engine.levelRaw, engine::switchLevel, Modifier.widthIn(max = 280.dp).weight(1f, fill = false))
                Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
                Txt(fmtTime(engine.elapsed), 18, c.textMuted, tabular = true)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.bgElevated)
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) { board() }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton("Undo", onClick = engine::undo)
                ToolButton("Hint", onClick = engine::hint)
                ToolButton("Reset", onClick = engine::reset)
                Spacer(Modifier.weight(1f))
                ToolButton("New puzzle", primary = true, onClick = engine::newPuzzle)
            }

            Txt(engine.statText, 13, c.textSubtle)

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.bgElevated)
                    .border(1.dp, c.border, RoundedCornerShape(8.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { rulesExpanded = !rulesExpanded }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("How to play", 15, c.text, FontWeight.Medium, modifier = Modifier.weight(1f))
                    ChevronIcon(c.accent, rulesExpanded)
                }
                if (rulesExpanded) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in rules) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Txt("•", 15, c.textMuted)
                                Txt(r, 15, c.textMuted)
                            }
                        }
                    }
                }
            }
        }

        ToastOverlay(engine.toast)
        if (engine.showWin) {
            WinOverlay(engine.winTimeText, onNext = engine::newPuzzle, onClose = { engine.showWin = false })
        }
    }
}
