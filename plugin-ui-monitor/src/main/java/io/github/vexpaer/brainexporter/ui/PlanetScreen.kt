package io.github.vexpaer.brainexporter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class OrbitalPalette { FOCUS, REST }

/** A single, animated product hero. Signal acquisition replaces the former audio controls. */
@Composable
internal fun PlanetHomeScreen(
    connectionPhase: ConnectionPhase,
    onOpenMonitor: () -> Unit,
    onOpenDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReduceMotion()
    val rotation = remember { Animatable(0f) }
    val satellite = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            while (true) {
                val start = rotation.value % 360f
                rotation.snapTo(start)
                rotation.animateTo(start + 360f, tween(24_000, easing = LinearEasing))
            }
        }
    }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            while (true) {
                val start = satellite.value % 360f
                satellite.snapTo(start)
                satellite.animateTo(start + 360f, tween(12_000, easing = LinearEasing))
            }
        }
    }
    val breathe = if (reducedMotion) 1f else {
        val transition = rememberInfiniteTransition(label = "orbital-breathe")
        transition.animateFloat(
            initialValue = 0.98f, targetValue = 1.02f,
            animationSpec = infiniteRepeatable(tween(5_500), repeatMode = RepeatMode.Reverse),
            label = "orbital-scale",
        ).value
    }
    var ready by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { ready = true }

    Box(modifier = modifier.fillMaxSize().background(Void)) {
        AuroraBackground(Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val globeSize = minOf(maxWidth * 0.80f, maxHeight * 0.49f, 355.dp)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AnimatedVisibility(
                    visible = ready,
                    enter = fadeIn(tween(550)) + slideInVertically(tween(650, easing = Motion.Emphasized)) { -18 },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "BRAINEXPORTER",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                            color = OrbitBlue,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "See the signal.",
                            style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 40.sp),
                            color = Starlight,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .size(globeSize)
                            .graphicsLayer {
                                scaleX = breathe
                                scaleY = breathe
                            }
                            .semantics { contentDescription = "Open real-time EEG monitor"; role = Role.Button }
                            .clickable(role = Role.Button, onClick = onOpenMonitor),
                    ) {
                        drawPlanet3d(
                            mode = OrbitalPalette.FOCUS,
                            rotationDegrees = rotation.value,
                            atmospherePulse = breathe,
                            error = false,
                            orbitPhaseDegrees = satellite.value,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = ready,
                    enter = fadeIn(tween(850, delayMillis = 160)) +
                        slideInVertically(tween(600, delayMillis = 160, easing = Motion.Emphasized)) { 24 },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatusPill(
                            dotColor = if (connectionPhase == ConnectionPhase.CONNECTED) Success else MoonDust,
                            label = if (connectionPhase == ConnectionPhase.CONNECTED) "EEG connected" else "Ready",
                            textColor = Starlight,
                        )
                        Button(
                            onClick = onOpenMonitor,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Starlight, contentColor = Void),
                            shape = ShapeM,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Open monitor", fontWeight = FontWeight.SemiBold)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(19.dp))
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = onOpenDemo) {
                            Text("Explore demo", color = Starlight)
                        }
                        Text(
                            "8 CHANNELS  /  LIVE EEG",
                            color = MoonDust,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPlanet3d(
    mode: OrbitalPalette,
    rotationDegrees: Float,
    atmospherePulse: Float,
    error: Boolean,
    orbitPhaseDegrees: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.285f
    val glow = when {
        error -> Danger
        mode == OrbitalPalette.FOCUS -> PlanetPalette.focusGlow
        else -> PlanetPalette.restGlow
    }
    val detail = if (mode == OrbitalPalette.FOCUS) PlanetPalette.focusDetail else PlanetPalette.restDetail
    val orbitRect = Rect(
        left = center.x - radius * 1.56f,
        top = center.y - radius * 0.43f,
        right = center.x + radius * 1.56f,
        bottom = center.y + radius * 0.43f,
    )
    val orbitStroke = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)

    // 大范围环境光晕:克制的呼吸底光。
    drawCircle(
        brush = Brush.radialGradient(
            0f to glow.copy(alpha = 0.20f * atmospherePulse),
            0.48f to glow.copy(alpha = 0.07f),
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
            color = glow.copy(alpha = 0.22f),
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
        val baseColors = if (mode == OrbitalPalette.FOCUS) {
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
        val ribbonSeeds = if (mode == OrbitalPalette.FOCUS) {
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
                0.82f to glow.copy(alpha = 0.08f * atmospherePulse),
                1f to glow.copy(alpha = 0.22f * atmospherePulse),
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
        color = glow.copy(alpha = 0.40f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.15.dp.toPx()),
    )
    drawCircle(
        color = glow.copy(alpha = 0.10f * atmospherePulse),
        radius = radius + 4.dp.toPx(),
        center = center,
        style = Stroke(width = 5.dp.toPx()),
    )

    // 前部轨道(面向观察者的一半)
    rotate(-9f, center) {
        drawArc(
            color = glow.copy(alpha = 0.55f),
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