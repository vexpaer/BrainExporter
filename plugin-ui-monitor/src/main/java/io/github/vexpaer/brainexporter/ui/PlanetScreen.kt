package io.github.vexpaer.brainexporter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
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
                0f to Color(0xFF142343),
                0.52f to Color(0xFF080F1D),
                1f to Ink,
                radius = 1_150f,
            ),
        ),
    ) {
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
                    Box(
                        Modifier
                            .size(if (selected) 9.dp else 7.dp)
                            .background(if (selected) Cyan else TextMuted.copy(alpha = 0.48f), CircleShape),
                    )
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
    val rotation = remember(mode) { Animatable(0f) }
    val rotating = phase == PlaybackPhase.PLAYING
    LaunchedEffect(rotating, mode) {
        if (!rotating) return@LaunchedEffect
        while (true) {
            val start = rotation.value % 360f
            rotation.snapTo(start)
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(
                    durationMillis = if (mode == AudioMode.FOCUS) 13_000 else 20_000,
                    easing = LinearEasing,
                ),
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "${mode.name}-3d-motion")
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "atmosphere-pulse",
    )
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

    Column(
        modifier = modifier.padding(top = 56.dp, bottom = 64.dp),
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
                .size(326.dp)
                .semantics { contentDescription = semanticsText; role = Role.Button }
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            drawPlanet3d(
                mode = mode,
                rotationDegrees = rotation.value,
                atmospherePulse = if (phase == PlaybackPhase.LOADING) pulse else 1f,
                error = phase == PlaybackPhase.ERROR,
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

@Composable
private fun StarField(modifier: Modifier = Modifier) {
    val stars = remember {
        List(62) { index ->
            val x = ((index * 73 + 19) % 101) / 101f
            val y = ((index * 47 + 11) % 97) / 97f
            val radius = if (index % 9 == 0) 1.65f else if (index % 3 == 0) 1.05f else 0.65f
            Triple(x, y, radius)
        }
    }
    Canvas(modifier) {
        stars.forEachIndexed { index, star ->
            drawCircle(
                color = Color.White.copy(alpha = if (index % 7 == 0) 0.44f else 0.22f),
                radius = star.third.dp.toPx(),
                center = Offset(size.width * star.first, size.height * star.second),
            )
        }
    }
}

@Composable
private fun EegConnectionBadge(connected: Boolean, modifier: Modifier = Modifier) {
    val color = if (connected) Color(0xFF57DB82) else Color(0xFF77808D)
    Row(
        modifier = modifier
            .background(Color(0xB8141D2A), RoundedCornerShape(50))
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
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.285f
    val glow = when {
        error -> Danger
        mode == AudioMode.FOCUS -> Color(0xFF43E0D0)
        else -> Color(0xFFB69BFF)
    }
    val detail = if (mode == AudioMode.FOCUS) Color(0xFF91FFF1) else Color(0xFFFFDBB3)
    val orbitRect = Rect(
        left = center.x - radius * 1.56f,
        top = center.y - radius * 0.43f,
        right = center.x + radius * 1.56f,
        bottom = center.y + radius * 0.43f,
    )
    val orbitStroke = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)

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

    // One exact ellipse is split into back/front halves, so both visible orbit pieces meet cleanly.
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
            listOf(Color(0xFF2F87A6), Color(0xFF183C78), Color(0xFF101B49), Color(0xFF050915))
        } else {
            listOf(Color(0xFFD08C87), Color(0xFF745B9E), Color(0xFF35416F), Color(0xFF0E1229))
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
            listOf(-0.58, -0.35, -0.08, 0.19, 0.43)
        } else {
            listOf(-0.48, -0.21, 0.04, 0.29, 0.53)
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
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.65f to Color.Transparent,
                1f to Color(0xD9000208),
                center = center - Offset(radius * 0.28f, radius * 0.22f),
                radius = radius * 1.18f,
            ),
            radius = radius,
            center = center,
        )
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
