package online.hirak.puzzles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.hirak.puzzles.data.Store
import online.hirak.puzzles.game.HashiEngine
import online.hirak.puzzles.game.SudokuEngine
import online.hirak.puzzles.game.TracksEngine
import online.hirak.puzzles.ui.BackIcon
import online.hirak.puzzles.ui.DarkPalette
import online.hirak.puzzles.ui.GameKind
import online.hirak.puzzles.ui.HashiScreen
import online.hirak.puzzles.ui.HomeScreen
import online.hirak.puzzles.ui.LightPalette
import online.hirak.puzzles.ui.LocalPalette
import online.hirak.puzzles.ui.SudokuScreen
import online.hirak.puzzles.ui.ThemeIcon
import online.hirak.puzzles.ui.ThemeMode
import online.hirak.puzzles.ui.TracksScreen
import online.hirak.puzzles.ui.Txt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent { PuzzlesApp(this) }
    }
}

/** Root shell — port of iOS `PuzzlesApp.swift` (`RootView`): home ↔ game, theme toggle. */
@Composable
private fun PuzzlesApp(activity: ComponentActivity) {
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.of(Store.string("puzzles.theme", "system"))) }
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(style, style)
    }

    var screen by rememberSaveable { mutableStateOf<GameKind?>(null) }
    BackHandler(enabled = screen != null) { screen = null }

    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        val c = LocalPalette.current
        Column(Modifier.fillMaxSize().background(c.bg)) {
            // Top bar: back on game screens, theme toggle on home (like iOS).
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
            ) {
                val current = screen
                if (current != null) {
                    Box(
                        Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape)
                            .clickable { screen = null }.semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) { BackIcon(c.accent) }
                } else {
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape)
                            .clickable {
                                themeMode = themeMode.next
                                Store.setString("puzzles.theme", themeMode.raw)
                            }
                            .semantics { contentDescription = "Toggle theme (currently ${themeMode.raw})" },
                        contentAlignment = Alignment.Center,
                    ) { ThemeIcon(themeMode, c.textMuted) }
                }
                Txt(current?.title ?: "Puzzles", 17, c.text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
            }

            Box(Modifier.fillMaxSize()) {
                // Engines are activity-scoped ViewModels: they survive rotation
                // and leaving/returning to a game; progress is also persisted.
                when (screen) {
                    GameKind.HASHI -> HashiScreen(viewModel { HashiEngine() })
                    GameKind.TRACKS -> TracksScreen(viewModel { TracksEngine() })
                    GameKind.SUDOKU -> SudokuScreen(viewModel { SudokuEngine() })
                    null -> HomeScreen { screen = it }
                }
            }
        }
    }
}
