package io.github.vexpaer.brainexporter.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Planetary Premium 设计系统 · 单一事实来源。
 *
 * 视觉契约见 DESIGN.md：纯深色、无 Dynamic Color、绿色不作为品牌色、
 * 所有颜色 token 化，组件内禁止散落 raw hex。
 */

// ---------------------------------------------------------------------------
// 核心色板
// ---------------------------------------------------------------------------

/** 全局近黑背景。 */
internal val Void = Color(0xFF07080D)

/** 主要表面。 */
internal val Obsidian = Color(0xFF0D111A)

/** 抬升表面。 */
internal val Eclipse = Color(0xFF151B28)

/** 主文字：偏暖白。 */
internal val Starlight = Color(0xFFF2F0EA)

/** 次文字。 */
internal val MoonDust = Color(0xFF9298A8)

/** 主要交互 / 选中：低饱和冰蓝。 */
internal val OrbitBlue = Color(0xFF7F98FF)

/** 次级品牌强调：蓝紫。 */
internal val LunarViolet = Color(0xFF9B8CFF)

/** 极少量暖金属强调。 */
internal val SolarMetal = Color(0xFFD2AD70)

/** 错误：高辨识度红。 */
internal val Danger = Color(0xFFFF6E7A)

/** 仅允许极小连接/成功状态点使用；不得扩散为主题色或大面积图形。 */
internal val Success = Color(0xFF7CB895)

/** 主交互色之上的深色内容(按钮文字)。 */
internal val OnOrbit = Color(0xFF0A0E1E)

/** 细边线：表面之间靠明度差与 1dp 细线分层。 */
internal val Hairline = Color(0xFF222A3A)

/** 图表网格线，弱于文字强于 Hairline。 */
internal val Grid = Color(0xFF28303F)

// ---------------------------------------------------------------------------
// 形状节奏：8 / 12 / 18 / capsule，按用途固定。
// ---------------------------------------------------------------------------

internal val RadiusS = 8.dp
internal val RadiusM = 12.dp
internal val RadiusL = 18.dp

internal val ShapeS = RoundedCornerShape(RadiusS)
internal val ShapeM = RoundedCornerShape(RadiusM)
internal val ShapeL = RoundedCornerShape(RadiusL)
internal val ShapeCapsule = RoundedCornerShape(percent = 50)

// ---------------------------------------------------------------------------
// 间距节奏：4dp 基础。
// ---------------------------------------------------------------------------

internal object Spacing {
    val XXS: Dp = 4.dp
    val XS: Dp = 8.dp
    val S: Dp = 12.dp
    val M: Dp = 16.dp
    val L: Dp = 20.dp
    val XL: Dp = 24.dp
    val XXL: Dp = 32.dp
}

// ---------------------------------------------------------------------------
// 动效：150–420ms 页面/控件反馈；星球氛围动效可以更慢。
// ---------------------------------------------------------------------------

internal object Motion {
    const val FastMs = 180
    const val StandardMs = 300
    const val SlowMs = 420
    const val PlanetRotationMs = 13_000L
    const val RestRotationMs = 20_000L
    const val AtmospherePulseMs = 1_800
    const val NebulaDriftMs = 60_000
    const val StarTwinkleMs = 5_600
    const val OrbitSatelliteMs = 7_000

    /** M3 强调缓动：用于进场与状态切换，表达层级变化。 */
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

// ---------------------------------------------------------------------------
// 星球视觉调色板：专注 = 冰蓝气态巨行星；休息 = 柔和紫尘埃行星。
// 两颗行星气质不同，而非仅换色相。
// ---------------------------------------------------------------------------

internal object PlanetPalette {
    /** 专注行星：清冽冰蓝。 */
    val focusGlow = OrbitBlue
    val focusDetail = Color(0xFFBCCBFF)
    val focusSphere = listOf(Color(0xFF40548F), Color(0xFF232E56), Color(0xFF121831), Color(0xFF05070F))
    val focusRibbonSeeds = listOf(-0.58, -0.35, -0.08, 0.19, 0.43)

    /** 休息行星：柔和紫 + 暖尘细节，气质更朦胧。 */
    val restGlow = LunarViolet
    val restDetail = Color(0xFFDCC3AC)
    val restSphere = listOf(Color(0xFF6E5A96), Color(0xFF443765), Color(0xFF231E3E), Color(0xFF090813))
    val restRibbonSeeds = listOf(-0.48, -0.21, 0.04, 0.29, 0.53)

    /** 页面径向渐变背景：极低对比的深空底，不能抢过星球。 */
    val backgroundTop = Color(0xFF0C1120)
    val backgroundMid = Color(0xFF080A11)

    /** 状态胶囊底色。 */
    val pillBackground = Color(0xCC0D111A)

    /** 连接状态：低饱和绿仅作小状态点；未连接用月尘灰。 */
    val connectedGreen = Success
    val disconnectedGray = Color(0xFF8A93A6)

    /** 球体内缘压暗。 */
    val sphereShadow = Color(0xE6000106)
}

// ---------------------------------------------------------------------------
// MaterialTheme 映射：深色唯一，无 Dynamic Color。
// ---------------------------------------------------------------------------

private val BrainExporterColors = darkColorScheme(
    primary = OrbitBlue,
    onPrimary = OnOrbit,
    primaryContainer = Color(0xFF2A3560),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = LunarViolet,
    onSecondary = Color(0xFF14101F),
    secondaryContainer = Color(0xFF2C2752),
    onSecondaryContainer = Color(0xFFE6DFFF),
    tertiary = SolarMetal,
    onTertiary = Color(0xFF1E1508),
    error = Danger,
    onError = Color(0xFF2A0709),
    background = Void,
    onBackground = Starlight,
    surface = Obsidian,
    onSurface = Starlight,
    surfaceVariant = Eclipse,
    onSurfaceVariant = MoonDust,
    surfaceContainerLowest = Void,
    surfaceContainerLow = Obsidian,
    surfaceContainer = Eclipse,
    surfaceContainerHigh = Color(0xFF1B2230),
    surfaceContainerHighest = Color(0xFF212939),
    outline = Hairline,
    outlineVariant = Color(0xFF161D2B),
    inverseSurface = Color(0xFFF2F0EA),
    inverseOnSurface = Color(0xFF15161C),
    scrim = Color(0xF2000000),
)

/**
 * 数据排版层级：用大小、字重与留白建立编辑感；
 * 数值统一 tabular numerals，量值横向稳定对齐。
 */
private val DataTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val BrainExporterShapes = Shapes(
    extraSmall = ShapeS,
    small = ShapeS,
    medium = ShapeM,
    large = ShapeL,
    extraLarge = RoundedCornerShape(24.dp),
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

/** 页面级语义令牌：屏幕通过 CompositionLocal 取用，禁止散落 raw hex。 */
internal data class PlanetSemanticColors(
    val accent: Color = OrbitBlue,
    val accentAlt: Color = LunarViolet,
    val metal: Color = SolarMetal,
)

internal val LocalPlanetSemantics = staticCompositionLocalOf { PlanetSemanticColors() }

/** 数值文本：等宽数字，量值前后稳定对齐。 */
internal const val NumericFontFeature = "'tnum' on"

/** 给任意文字样式叠加等宽数字。 */
internal fun TextStyle.numeric(): TextStyle = copy(fontFeatureSettings = NumericFontFeature)
