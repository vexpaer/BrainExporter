package io.github.vexpaer.brainexporter.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Planetary Premium 复用品牌组件。
 * 少量、克制、可识别：不抽象成通用框架。
 */

/** 系统关闭动画(减弱动画)时返回 true；所有无限动画必须尊重该状态。 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        }.getOrDefault(1f) == 0f
    }
}

/**
 * 页面容器：深空底色。
 * atmospheric = true 时使用首页的径向星云渐变；工具页保持纯 Void 平整底。
 */
@Composable
internal fun PlanetScaffold(
    modifier: Modifier = Modifier,
    atmospheric: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(Void)) {
        AuroraBackground(Modifier.fillMaxSize(), quiet = !atmospheric)
        content()
    }
}

/**
 * 工具页标题：编辑式层级，替代传统 TopAppBar。
 * 小型 overline 引导 + 大标题，标题与内容融合。
 */
@Composable
internal fun PageHeader(
    overline: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
        Text(
            text = overline,
            color = SolarMetal,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
        )
        Text(
            text = title,
            color = Starlight,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(text = subtitle, color = MoonDust, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 精密仪器表面：平整抬升层，靠明度差与 1dp 细边线分层，无投影。
 */
@Composable
internal fun InstrumentSurface(
    modifier: Modifier = Modifier,
    color: Color = Eclipse,
    borderColor: Color = Hairline,
    shape: RoundedCornerShape = ShapeM,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Spacing.M),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * 轻量分段控制：视图/模式切换的唯一语言，替代按钮堆与默认 FilterChip。
 */
@Composable
internal fun <T> SegmentedModeControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCapsule)
            .background(Obsidian)
            .border(1.dp, Hairline, ShapeCapsule)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                animationSpec = tween(Motion.FastMs),
                label = "segment-bg",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Starlight else MoonDust,
                animationSpec = tween(Motion.FastMs),
                label = "segment-fg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(ShapeCapsule)
                    .background(background)
                    .clickable(
                        onClickLabel = label,
                        role = Role.Tab,
                    ) { onSelect(value) }
                    .semantics {
                        role = Role.Tab
                        this[SemanticsProperties.Selected] = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 状态胶囊：EEG 连接等小型状态语义，点 + 文本合并朗读。
 */
@Composable
internal fun StatusPill(
    dotColor: Color,
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = dotColor,
) {
    Row(
        modifier = modifier
            .background(PlanetPalette.pillBackground, ShapeCapsule)
            .border(1.dp, Hairline.copy(alpha = 0.6f), ShapeCapsule)
            .padding(horizontal = Spacing.S, vertical = 7.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(dotColor, androidx.compose.foundation.shape.CircleShape))
        Text(text = label, color = textColor, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * 主操作坞：沉浸页面底部的主行动区，渐变压暗保证前景可读。
 */
@Composable
internal fun PrimaryActionDock(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Void.copy(alpha = 0.82f),
                    1f to Void,
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * 轨道导航：轻量悬浮贴底容器，替代厚重默认 NavigationBar。
 * 选中项以冰蓝图标 + 底部轨道点表达，不做大块选中背景。
 */
internal data class OrbitalItem(val icon: ImageVector, val label: String)

@Composable
internal fun OrbitalNavigation(
    items: List<OrbitalItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = Spacing.L)
            .navigationBarsPadding()
            .padding(bottom = Spacing.S)
            .fillMaxWidth()
            .height(62.dp)
            .clip(ShapeL)
            .background(Obsidian.copy(alpha = 0.94f))
            .border(1.dp, Hairline, ShapeL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val lift by animateFloatAsState(
                targetValue = if (isSelected) -2f else 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 470f),
                label = "nav-lift",
            )
            val tint by animateColorAsState(
                targetValue = if (isSelected) OrbitBlue else MoonDust,
                animationSpec = tween(Motion.FastMs),
                label = "nav-tint",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        onClickLabel = item.label,
                        role = Role.Tab,
                    ) { onSelect(index) }
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp).graphicsLayer { translationY = lift.dp.toPx() },
                )
                Spacer(Modifier.height(3.dp))
                Text(text = item.label, color = tint, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(if (isSelected) OrbitBlue else Color.Transparent, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}

/**
 * 品牌对话框：统一 AlertDialog 容器样式，杜绝默认组件穿帮。
 */
@Composable
internal fun BrandAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    bodyStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    text: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = Obsidian,
        iconContentColor = Starlight,
        titleContentColor = Starlight,
        textContentColor = MoonDust,
        shape = ShapeL,
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        },
        text = { text() },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onConfirm,
                enabled = true,
            ) { Text(confirmLabel, style = bodyStyle) }
        },
        dismissButton = {
            if (dismissLabel != null && onDismiss != null) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        },
    )
}

/**
 * 横向滚动紧凑选择器：数据源与通道选择的统一语言，替代默认 FilterChip。
 */
internal data class ChipOption<T>(val value: T, val label: String, val dotColor: Color? = null)

@Composable
internal fun <T> ChipSelector(
    options: List<ChipOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XXS),
    ) {
        items(options.size) { index ->
            val option = options[index]
            val selectedState = isSelected(option.value)
            val borderColor by animateColorAsState(
                targetValue = if (selectedState) OrbitBlue.copy(alpha = 0.55f) else Hairline,
                animationSpec = if (rememberReduceMotion()) snap() else tween(Motion.StandardMs, easing = Motion.Emphasized),
                label = "chip-border",
            )
            val chipBackground by animateColorAsState(
                targetValue = if (selectedState) MaterialTheme.colorScheme.surfaceContainerHigh else Obsidian,
                animationSpec = if (rememberReduceMotion()) snap() else tween(Motion.StandardMs, easing = Motion.Emphasized),
                label = "chip-fill",
            )
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(ShapeCapsule)
                    .background(chipBackground)
                    .border(1.dp, borderColor, ShapeCapsule)
                    .clickable(onClickLabel = option.label, role = Role.Tab) { onSelect(option.value) }
                    .semantics { role = Role.Tab; selected = selectedState }
                    .padding(horizontal = Spacing.S, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (option.dotColor != null) {
                    Box(Modifier.size(7.dp).background(option.dotColor, androidx.compose.foundation.shape.CircleShape))
                }
                Text(
                    text = option.label,
                    color = if (selectedState) Starlight else MoonDust,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedState) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            item(content = { trailing() })
        }
    }
}

/**
 * 分组小节标题：字距拉开的小号标签，仪器面板语言。
 */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MoonDust,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
        modifier = modifier.padding(top = Spacing.XXS),
    )
}

/**
 * 统一空状态：图标 + 标题 + 说明，避免各页自造。
 */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    tint: Color = MoonDust,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
        Text(title, color = MoonDust, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (detail != null) {
            Text(
                detail,
                color = MoonDust.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
