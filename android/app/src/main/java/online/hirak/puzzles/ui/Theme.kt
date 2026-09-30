package online.hirak.puzzles.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import online.hirak.puzzles.R

/**
 * Color tokens — port of iOS `Support/Theme.swift` (itself from the web
 * app's styles.css). Keep the hex values identical on both platforms.
 */
@Immutable
data class Palette(
    val bg: Color,
    val bgElevated: Color,
    val bgRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val accent: Color,
    val accentBg: Color,
    val accentContrast: Color,
    val danger: Color,
    val dangerBg: Color,
    /** The player's own track/entries, vs. given ones. */
    val user: Color,
    val isDark: Boolean,
)

val LightPalette = Palette(
    bg = Color(0xFFFAFAF7), bgElevated = Color(0xFFFFFFFF), bgRaised = Color(0xFFF2F2EE),
    border = Color(0xFFE5E5E0), borderStrong = Color(0xFFC8C8C2),
    text = Color(0xFF1A1A1A), textMuted = Color(0xFF555550), textSubtle = Color(0xFF8E8E88),
    accent = Color(0xFF0F6E56), accentBg = Color(0xFFE1F5EE), accentContrast = Color(0xFFFFFFFF),
    danger = Color(0xFFA63D2E), dangerBg = Color(0xFFF7E1DD), user = Color(0xFF2B6CB0),
    isDark = false,
)

val DarkPalette = Palette(
    bg = Color(0xFF161614), bgElevated = Color(0xFF1F1F1C), bgRaised = Color(0xFF252522),
    border = Color(0xFF33332F), borderStrong = Color(0xFF4D4D48),
    text = Color(0xFFEDEDE8), textMuted = Color(0xFFA8A8A2), textSubtle = Color(0xFF7A7A74),
    accent = Color(0xFF5DCAA5), accentBg = Color(0xFF1A3A30), accentContrast = Color(0xFF0B0D10),
    danger = Color(0xFFE0685B), dangerBg = Color(0xFF3A1F1B), user = Color(0xFF63A4EF),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Inter / Source Serif 4 variable fonts — same files the iOS app bundles. */
object Fonts {
    private fun variable(res: Int, weight: Int) =
        Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

    val sans = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.inter, it) })
    val serif = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.source_serif4, it) })
}

/** Three-state light/dark/system toggle (iOS `ThemeMode`). */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    val raw: String get() = name.lowercase()
    val next: ThemeMode get() = when (this) { LIGHT -> DARK; DARK -> SYSTEM; SYSTEM -> LIGHT }

    companion object { fun of(raw: String) = entries.firstOrNull { it.raw == raw } ?: SYSTEM }
}
