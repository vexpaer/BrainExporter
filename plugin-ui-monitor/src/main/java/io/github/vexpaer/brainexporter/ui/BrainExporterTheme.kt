package io.github.vexpaer.brainexporter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF07101C)
internal val Panel = Color(0xFF101F33)
internal val PanelSoft = Color(0xFF0D192A)
internal val Cyan = Color(0xFF38D9C5)
internal val Blue = Color(0xFF6A8DFF)
internal val Amber = Color(0xFFFFBD5B)
internal val Danger = Color(0xFFFF6B7A)
internal val TextPrimary = Color(0xFFEDF7FF)
internal val TextMuted = Color(0xFF8EA5BB)
internal val Grid = Color(0xFF263A50)

private val BrainExporterColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF021C18),
    secondary = Blue,
    tertiary = Amber,
    error = Danger,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelSoft,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF31475D),
)

@Composable
fun BrainExporterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrainExporterColors,
        content = content,
    )
}
