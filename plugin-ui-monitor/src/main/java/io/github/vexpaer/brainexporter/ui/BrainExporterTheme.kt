package io.github.vexpaer.brainexporter.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 统一圆角节奏:卡片 16,控件 10,胶囊 50。 */
internal val CardRadius = 16.dp
internal val ControlRadius = 10.dp

/** 动效节奏常量:所有交互动效共享一套时长与缓动,避免零散特效。 */
internal object Motion {
    const val FastMs = 180
    const val StandardMs = 320
    const val SlowMs = 560
    const val PlanetRotationMs = 13_000L
    const val RestRotationMs = 20_000L
    const val AtmospherePulseMs = 1_500
}

/** 基础表面与墨色。 */
internal val Ink = Color(0xFF07101C)
internal val Panel = Color(0xFF101F33)
internal val PanelSoft = Color(0xFF0D192A)
internal val PanelRaised = Color(0xFF16263D)

/** 语义角色色:青=连接/良好,蓝=次要,琥珀=过渡/警告,红=错误,紫=模块特征。 */
internal val Cyan = Color(0xFF38D9C5)
internal val Blue = Color(0xFF6A8DFF)
internal val Amber = Color(0xFFFFBD5B)
internal val Danger = Color(0xFFFF6B7A)
internal val FeaturePurple = Color(0xFFA991FF)

/** 文本与栅格。 */
internal val TextPrimary = Color(0xFFEDF7FF)
internal val TextMuted = Color(0xFF8EA5BB)
internal val Grid = Color(0xFF263A50)

/** 数据可视化调色板:通道 1-8 与波段,前三位与语义色一致,保证改主题一次生效。 */
internal val ChannelColors = listOf(
    Cyan,
    Blue,
    Amber,
    Color(0xFFE57AFF),
    Color(0xFF5BE68B),
    Color(0xFFFF7C88),
    Color(0xFF50B8FF),
    Color(0xFFFF9D55),
)

internal val BandColors = listOf(
    Color(0xFF6786FF),
    Color(0xFF48B8FF),
    Cyan,
    Amber,
    Color(0xFFE57AFF),
)

/** 通道色底上的按钮文字(高对比深色)。 */
internal val OnChannelDark = Color(0xFF06131F)

/** 星球视觉调色板:专注(青)与休息(紫)两套的辉光、纹理与球体渐变,集中为单一来源。 */
internal object PlanetPalette {
    val focusGlow = Color(0xFF43E0D0)
    val focusDetail = Color(0xFF91FFF1)
    val focusSphere = listOf(Color(0xFF2F87A6), Color(0xFF183C78), Color(0xFF101B49), Color(0xFF050915))
    val focusRibbonSeeds = listOf(-0.58, -0.35, -0.08, 0.19, 0.43)

    val restGlow = Color(0xFFB69BFF)
    val restDetail = Color(0xFFFFDBB3)
    val restSphere = listOf(Color(0xFFD08C87), Color(0xFF745B9E), Color(0xFF35416F), Color(0xFF0E1229))
    val restRibbonSeeds = listOf(-0.48, -0.21, 0.04, 0.29, 0.53)

    /** 页面径向渐变背景。 */
    val backgroundTop = Color(0xFF142343)
    val backgroundMid = Color(0xFF080F1D)

    /** EEG 徽章与深色罩层。 */
    val badgeBackground = Color(0xB8141D2A)
    val connectedGreen = Color(0xFF57DB82)
    val disconnectedGray = Color(0xFF77808D)

    /** 球体内缘压暗。 */
    val sphereShadow = Color(0xD9000208)
}

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

/** 数据排版:等宽数字让采样率/阻抗/百分比横向对齐,科学仪表观感且读数稳定。 */
private val DataTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)

private val BrainExporterShapes = Shapes(
    extraSmall = RoundedCornerShape(ControlRadius),
    small = RoundedCornerShape(ControlRadius),
    medium = RoundedCornerShape(CardRadius),
    large = RoundedCornerShape(CardRadius + 4.dp),
    extraLarge = RoundedCornerShape(CardRadius + 8.dp),
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

/** 数值文本:等宽数字,量值前后稳定对齐。 */
internal const val NumericFontFeature = "'tnum' on"