package io.github.vexpaer.brainexporter.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material 3 dark scheme. Chart colors remain independent of interaction roles. */
internal val Void = Color(0xFF141218)
internal val Obsidian = Color(0xFF1D1B20)
internal val Eclipse = Color(0xFF211F26)
internal val Starlight = Color(0xFFE6E0E9)
internal val MoonDust = Color(0xFFCAC4D0)
internal val OrbitBlue = Color(0xFFD0BCFF)
internal val LunarViolet = Color(0xFFCCC2DC)
internal val SolarMetal = Color(0xFFEFB8C8)
internal val Danger = Color(0xFFFFB4AB)
internal val Success = Color(0xFFA8D5BA)
internal val OnOrbit = Color(0xFF381E72)
internal val Hairline = Color(0xFF49454F)
internal val Grid = Color(0xFF49454F)
internal val RadiusS = 8.dp
internal val RadiusM = 12.dp
internal val RadiusL = 16.dp
internal val ShapeS = RoundedCornerShape(RadiusS)
internal val ShapeM = RoundedCornerShape(RadiusM)
internal val ShapeL = RoundedCornerShape(RadiusL)
internal val ShapeCapsule = RoundedCornerShape(percent = 50)
internal object Spacing {
    val XXS: Dp = 4.dp
    val XS: Dp = 8.dp
    val S: Dp = 12.dp
    val M: Dp = 16.dp
    val L: Dp = 20.dp
    val XL: Dp = 24.dp
    val XXL: Dp = 32.dp
}
internal object Motion {
    const val FastMs = 150
    const val StandardMs = 300
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

// ---------------------------------------------------------------------------
// 数据可视化调色板：低饱和、可区分、与品牌主色解耦。
// ---------------------------------------------------------------------------

/** 通道 1–8 颜色：仅用于数据编码，不代表品牌。 */
internal val ChannelColors = listOf(
    Color(0xFF86A8D6), // 冰蓝灰
    Color(0xFFC79FD9), // 灰紫
    Color(0xFFD8B57E), // 沙金
    Color(0xFFDE8F7D), // 陶珊瑚
    Color(0xFF7FBFAE), // 海沫绿
    Color(0xFF93A3CF), // 长春花蓝
    Color(0xFFD49BB8), // 灰玫瑰
    Color(0xFF8FC2A0), // 鼠尾草绿
)

/** 脑电波段 Δ θ α β γ：低频→高频的冷到暖序列。 */
internal val BandColors = listOf(
    Color(0xFF6B78C8),
    Color(0xFF6FA0D4),
    Color(0xFF76BCB2),
    Color(0xFFC9AE72),
    Color(0xFFC88CA0),
)

/** 通道色底上的深色文字(高对比)。 */
internal val OnChannelDark = Color(0xFF0A0E1E)

private val BrainExporterColors = darkColorScheme(
    primary = OrbitBlue, onPrimary = OnOrbit,
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = LunarViolet, onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = SolarMetal, onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
    error = Danger, onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Void, onBackground = Starlight,
    surface = Void, onSurface = Starlight,
    surfaceVariant = Hairline, onSurfaceVariant = MoonDust,
    surfaceDim = Void, surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Obsidian, surfaceContainer = Eclipse,
    surfaceContainerHigh = Color(0xFF2B2930), surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99), outlineVariant = Hairline,
    inverseSurface = Starlight, inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6750A4), scrim = Color.Black,
)

private val DataTypography = Typography()
private val BrainExporterShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = ShapeS,
    medium = ShapeM, large = ShapeL, extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BrainExporterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrainExporterColors,
        shapes = BrainExporterShapes,
        typography = DataTypography,
        content = content,
    )
}

/** 数值文本：等宽数字，量值前后稳定对齐。 */
internal const val NumericFontFeature = "'tnum' on"

/** 给任意文字样式叠加等宽数字。 */
internal fun TextStyle.numeric(): TextStyle = copy(fontFeatureSettings = NumericFontFeature)
