package io.github.vexpaer.brainexporter.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun PlanetHomeScreen(
    playback: AudioPlaybackState,
    sources: AudioSources,
    connectionPhase: ConnectionPhase,
    onToggle: (AudioMode, String) -> Unit,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = remember { AudioMode.entries }
    val pagerState = rememberPagerState(pageCount = { modes.size })
    LaunchedEffect(pagerState.currentPage) {
        val visibleMode = modes[pagerState.currentPage]
        if (playback.mode != null && playback.mode != visibleMode &&
            playback.phase in setOf(PlaybackPhase.LOADING, PlaybackPhase.PLAYING)
        ) {
            onStop("点击星球开始")
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.radialGradient(
                0f to PlanetPalette.backgroundTop,
                0.52f to PlanetPalette.backgroundMid,
                1f to Ink,
                radius = 1_150f,
            ),
        ),
    ) {
        NebulaLayer(Modifier.fillMaxSize())
        StarField(Modifier.fillMaxSize())
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 32.dp,
        ) { index ->
            val mode = modes[index]
            val phase = if (playback.mode == mode) playback.phase else PlaybackPhase.STOPPED
            PlanetPage(
                mode = mode,
                phase = phase,
                onClick = { onToggle(mode, sources.forMode(mode)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        EegConnectionBadge(
            connected = connectionPhase == ConnectionPhase.CONNECTED,
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 18.dp, vertical = 17.dp),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(modes.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(contentAlignment = Alignment.Center) {
                        if (selected) {
                            Box(
                                Modifier
                                    .size(17.dp)
                                    .background(Cyan.copy(alpha = 0.16f), CircleShape),
                            )
                        }
                        Box(
                            Modifier
                                .size(if (selected) 9.dp else 6.dp)
                                .background(if (selected) Cyan else TextMuted.copy(alpha = 0.45f), CircleShape),
                        )
                    }
                }
            }
            Text("左右滑动切换", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlanetPage(
    mode: AudioMode,
    phase: PlaybackPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = animationsDisabled()
    val rotation = remember(mode) { Animatable(0f) }
    val orbitAngle = remember(mode) { Animatable(0f) }
    val rotating = phase == PlaybackPhase.PLAYING
    LaunchedEffect(rotating, mode, reduceMotion) {
        if (!rotating || reduceMotion) return@LaunchedEffect
        while (true) {
            val start = rotation.value % 360f
            rotation.snapTo(start)
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(
                    durationMillis = (if (mode == AudioMode.FOCUS) Motion.PlanetRotationMs else Motion.RestRotationMs).toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }
    LaunchedEffect(rotating, mode, reduceMotion) {
        if (!rotating || reduceMotion) return@LaunchedEffect
        while (true) {
            val start = orbitAngle.value % 360f
            orbitAngle.snapTo(start)
            orbitAngle.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(durationMillis = 7_000, easing = LinearEasing),
            )
        }
    }
    val pulse = if (reduceMotion) 1f else {
        val transition = rememberInfiniteTransition(label = "${mode.name}-3d-motion")
        transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.AtmospherePulseMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "atmosphere-pulse",
        ).value
    }
    val phaseText = when (phase) {
        PlaybackPhase.PLAYING -> "正在播放 · 点击星球停止"
        PlaybackPhase.LOADING -> "正在加载在线音乐…"
        PlaybackPhase.ERROR -> "播放失败 · 点击重试"
        PlaybackPhase.STOPPED -> "点击星球开始"
    }
    val description = if (mode == AudioMode.FOCUS) {
        "进入深度工作节奏 · 稳定、清醒、减少干扰"
    } else {
        "放松神经与呼吸 · 舒缓、恢复、留出空白"
    }
    val semanticsText = "${mode.title}模式。$description。$phaseText。左右滑动切换模式"

    var appeared by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { appeared = true }
    val appearAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else Motion.SlowMs, easing = FastOutSlowInEasing),
        label = "planet-appear-alpha",
    )
    val appearScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.9f,
        animationSpec = tween(if (reduceMotion) 0 else Motion.SlowMs),
        label = "planet-appear-scale",
    )

    BoxWithConstraints(modifier = modifier) {
        // 星球随可用空间缩放:小屏/横屏/多窗口都不溢出,最高 360dp。
        val planetSize = minOf(
            maxWidth * 0.78f,
            (maxHeight - 140.dp).coerceAtLeast(180.dp),
            360.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appearAlpha
                    scaleX = appearScale
                    scaleY = appearScale
                }
                .padding(top = 56.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mode.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .size(planetSize)
                    .semantics { contentDescription = semanticsText; role = Role.Button }
                    .clickable(role = Role.Button, onClick = onClick),
            ) {
                drawPlanet3d(
                    mode = mode,
                    rotationDegrees = rotation.value,
                    atmospherePulse = if (phase == PlaybackPhase.LOADING) pulse else 1f,
                    error = phase == PlaybackPhase.ERROR,
                    orbitPhaseDegrees = orbitAngle.value,
                )
            }
            Text(
                text = phaseText,
                color = when (phase) {
                    PlaybackPhase.ERROR -> Danger
                    PlaybackPhase.PLAYING -> Cyan
                    else -> TextMuted
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    val scale = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        }.getOrDefault(1f)
    }
    return scale == 0f
}

/** 缓慢漂移的星云光斑,给深空一个活着的底。 */
@Composable
private fun NebulaLayer(modifier: Modifier = Modifier) {
    val reduceMotion = animationsDisabled()
    val drift = if (reduceMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "nebula-drift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(60_000, easing = LinearEasing),
            ),
            label = "drift",
        ).value
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 主光斑:青(专注)与紫(休息)的中性混合,低频呼吸。
        val t = drift
        val wobble = sin(t * 2f * PI.toFloat())
        val glow1 = Brush.radialGradient(
            colors = listOf(
                PlanetPalette.focusGlow.copy(alpha = 0.055f),
                Color.Transparent,
            ),
            center = Offset(w * (0.32f + 0.05f * wobble), h * 0.26f),
            radius = w * 0.55f,
        )
        val glow2 = Brush.radialGradient(
            colors = listOf(
                PlanetPalette.restGlow.copy(alpha = 0.05f),
                Color.Transparent,
            ),
            center = Offset(w * (0.72f + 0.05f * cos(t * 2f * PI.toFloat())), h * 0.72f),
            radius = w * 0.6f,
        )
        drawRect(glow1)
        drawRect(glow2)
    }
}

@Composable
private fun StarField(modifier: Modifier = Modifier) {
    val reduceMotion = animationsDisabled()
    val stars = remember {
        List(88) { index ->
            val x = ((index * 73 + 19) % 101) / 101f
            val y = ((index * 47 + 11) % 97) / 97f
            val radius = if (index % 9 == 0) 1.65f else if (index % 3 == 0) 1.05f else 0.65f
            val phase = (index % 17) / 17f * 2f * PI.toFloat()
            StarSpec(x, y, radius, phase)
        }
    }
    val twinkle = if (reduceMotion) 0.5f else {
        val transition = rememberInfiniteTransition(label = "star-twinkle")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(5_600, easing = LinearEasing),
            ),
            label = "twinkle",
        ).value
    }
    Canvas(modifier) {
        stars.forEachIndexed { index, star ->
            val shimmer = (0.5f + 0.5f * sin(twinkle * 2f * PI.toFloat() + star.phase))
            val base = if (index % 7 == 0) 0.5f else 0.26f
            drawCircle(
                color = Color.White.copy(alpha = base * (0.55f + 0.45f * shimmer)),
                radius = star.radius.dp.toPx(),
                center = Offset(size.width * star.x, size.height * star.y),
            )
        }
    }
}

private data class StarSpec(val x: Float, val y: Float, val radius: Float, val phase: Float)

@Composable
private fun EegConnectionBadge(connected: Boolean, modifier: Modifier = Modifier) {
    val color = if (connected) PlanetPalette.connectedGreen else PlanetPalette.disconnectedGray
    Row(
        modifier = modifier
            .background(PlanetPalette.badgeBackground, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .semantics { contentDescription = if (connected) "EEG 设备已连接" else "EEG 设备未连接" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = if (connected) "EEG 已连接" else "EEG 未连接",
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun DrawScope.drawPlanet3d(
    mode: AudioMode,
    rotationDegrees: Float,
    atmospherePulse: Float,
    error: Boolean,
    orbitPhaseDegrees: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.285f
    val glow = when {
        error -> Danger
        mode == AudioMode.FOCUS -> PlanetPalette.focusGlow
        else -> PlanetPalette.restGlow
    }
    val detail = if (mode == AudioMode.FOCUS) PlanetPalette.focusDetail else PlanetPalette.restDetail
    val orbitRect = Rect(
        left = center.x - radius * 1.56f,
        top = center.y - radius * 0.43f,
        right = center.x + radius * 1.56f,
        bottom = center.y + radius * 0.43f,
    )
    val orbitStroke = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)

    // 大范围环境光晕
    drawCircle(
        brush = Brush.radialGradient(
            0f to glow.copy(alpha = 0.31f * atmospherePulse),
            0.48f to glow.copy(alpha = 0.10f),
            1f to Color.Transparent,
            center = center,
            radius = radius * 1.68f,
        ),
        radius = radius * 1.68f,
        center = center,
    )

    // 后部轨道(背侧一半)
    rotate(-9f, center) {
        drawArc(
            color = glow.copy(alpha = 0.29f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = orbitRect.topLeft,
            size = orbitRect.size,
            style = orbitStroke,
        )
    }

    val sphere = Path().apply { addOval(Rect(center - Offset(radius, radius), center + Offset(radius, radius))) }
    clipPath(sphere) {
        val baseColors = if (mode == AudioMode.FOCUS) {
            PlanetPalette.focusSphere
        } else {
            PlanetPalette.restSphere
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = baseColors,
                center = center - Offset(radius * 0.38f, radius * 0.42f),
                radius = radius * 1.72f,
            ),
            radius = radius,
            center = center,
        )

        val rotation = rotationDegrees / 180.0 * PI
        for (latitude in listOf(-55.0, -30.0, 0.0, 30.0, 55.0)) {
            drawProjectedCurve(
                center = center,
                radius = radius,
                rotation = rotation,
                color = detail.copy(alpha = 0.09f),
                strokeWidth = 0.72.dp.toPx(),
            ) { parameter -> latitude / 180.0 * PI to parameter }
        }
        for (longitude in listOf(-150.0, -90.0, -30.0, 30.0, 90.0, 150.0)) {
            drawProjectedCurve(
                center = center,
                radius = radius,
                rotation = rotation,
                color = detail.copy(alpha = 0.075f),
                strokeWidth = 0.68.dp.toPx(),
                parameterStart = -PI / 2.0,
                parameterEnd = PI / 2.0,
            ) { parameter -> parameter to longitude / 180.0 * PI }
        }

        // Periodic spherical ribbons form continuous surface texture across the ±π seam.
        val ribbonSeeds = if (mode == AudioMode.FOCUS) {
            PlanetPalette.focusRibbonSeeds
        } else {
            PlanetPalette.restRibbonSeeds
        }
        ribbonSeeds.forEachIndexed { index, seed ->
            drawProjectedCurve(
                center = center,
                radius = radius,
                rotation = rotation,
                color = detail.copy(alpha = if (index % 2 == 0) 0.19f else 0.13f),
                strokeWidth = if (index % 2 == 0) 7.5.dp.toPx() else 4.8.dp.toPx(),
            ) { longitude ->
                val latitude = seed + 0.085 * sin(2.0 * longitude + index) + 0.04 * sin(3.0 * longitude - index)
                latitude to longitude
            }
        }

        // 大气边缘辉光:球缘内侧一圈透光,立体感来源。
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.62f to Color.Transparent,
                0.82f to glow.copy(alpha = 0.10f * atmospherePulse),
                1f to glow.copy(alpha = 0.28f * atmospherePulse),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        // 球体内缘压暗
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.65f to Color.Transparent,
                1f to PlanetPalette.sphereShadow,
                center = center - Offset(radius * 0.28f, radius * 0.22f),
                radius = radius * 1.18f,
            ),
            radius = radius,
            center = center,
        )
        // 顶部高光
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.23f),
                0.22f to Color.White.copy(alpha = 0.045f),
                1f to Color.Transparent,
                center = center - Offset(radius * 0.36f, radius * 0.42f),
                radius = radius * 0.72f,
            ),
            radius = radius,
            center = center,
        )
    }
    drawCircle(
        color = glow.copy(alpha = 0.55f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.15.dp.toPx()),
    )
    drawCircle(
        color = glow.copy(alpha = 0.15f * atmospherePulse),
        radius = radius + 4.dp.toPx(),
        center = center,
        style = Stroke(width = 5.dp.toPx()),
    )

    // 前部轨道(面向观察者的一半)
    rotate(-9f, center) {
        drawArc(
            color = glow.copy(alpha = 0.72f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = orbitRect.topLeft,
            size = orbitRect.size,
            style = orbitStroke,
        )
    }

    // 轨道卫星:仅出现在前弧(0..180),穿过星球前方,制造"系统在运转"的活感。
    val phase = orbitPhaseDegrees % 360f
    if (phase in 0f..180f) {
        val angle = phase / 180.0 * PI
        val px = center.x + (cos(angle) * radius * 1.56f).toFloat()
        val py = center.y + (sin(angle) * radius * 0.43f).toFloat()
        rotate(-9f, center) {
            drawCircle(
                color = glow.copy(alpha = 0.20f),
                radius = 7.dp.toPx(),
                center = Offset(px, py),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 2.4.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}

private fun DrawScope.drawProjectedCurve(
    center: Offset,
    radius: Float,
    rotation: Double,
    color: Color,
    strokeWidth: Float,
    parameterStart: Double = -PI,
    parameterEnd: Double = PI,
    sphericalPoint: (Double) -> Pair<Double, Double>,
) {
    val path = Path()
    var drawing = false
    val steps = 180
    for (step in 0..steps) {
        val parameter = parameterStart + (parameterEnd - parameterStart) * step / steps
        val (latitude, longitude) = sphericalPoint(parameter)
        val cosineLatitude = cos(latitude)
        val rotatedLongitude = longitude + rotation
        val depth = cosineLatitude * cos(rotatedLongitude)
        if (depth >= 0.0) {
            val point = Offset(
                center.x + (cosineLatitude * sin(rotatedLongitude) * radius).toFloat(),
                center.y - (sin(latitude) * radius).toFloat(),
            )
            if (drawing) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
            drawing = true
        } else {
            drawing = false
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}