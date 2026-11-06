package io.github.vexpaer.brainexporter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val PlanetBlue = Color(0xFF7F98FF)
private val PlanetLight = Color(0xFFB9C9FF)
private val PlanetMid = Color(0xFF344675)
private val PlanetDeep = Color(0xFF10172B)

@Composable
internal fun HomeScreen(connectionPhase: ConnectionPhase, onOpenMonitor: () -> Unit, modifier: Modifier = Modifier) {
    val connected = connectionPhase == ConnectionPhase.CONNECTED
    val reduceMotion = rememberReduceMotion()
    val orbit = remember { Animatable(0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            orbit.snapTo(42f)
        } else {
            while (true) {
                orbit.animateTo(360f, animationSpec = tween(24_000, easing = LinearEasing))
                orbit.snapTo(0f)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(Void).padding(horizontal = Spacing.XL, vertical = Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(1.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val planetSize = minOf(maxWidth * 0.88f, maxHeight * 0.78f, 320.dp)
            Canvas(
                modifier = Modifier
                    .size(planetSize)
                    .semantics {
                        contentDescription = "Open EEG monitor"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClick = onOpenMonitor),
            ) {
                drawMinimalPlanet(orbit.value)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Box(
                    Modifier.size(8.dp).background(if (connected) Success else MoonDust.copy(alpha = 0.65f), CircleShape),
                )
                Text(
                    if (connected) "Connected" else "Not connected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(onClick = onOpenMonitor, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Open Monitor")
            }
        }
    }
}

private fun DrawScope.drawMinimalPlanet(orbitDegrees: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.285f
    val orbitRect = Rect(
        left = center.x - radius * 1.55f,
        top = center.y - radius * 0.45f,
        right = center.x + radius * 1.55f,
        bottom = center.y + radius * 0.45f,
    )
    val orbitStroke = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)

    drawCircle(
        brush = Brush.radialGradient(
            0f to PlanetBlue.copy(alpha = 0.19f),
            0.55f to PlanetBlue.copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = center,
            radius = radius * 1.7f,
        ),
        radius = radius * 1.7f,
        center = center,
    )

    rotate(-9f, center) {
        drawArc(
            color = PlanetBlue.copy(alpha = 0.35f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = orbitRect.topLeft,
            size = orbitRect.size,
            style = orbitStroke,
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            0f to PlanetLight,
            0.32f to PlanetMid,
            1f to PlanetDeep,
            center = center - Offset(radius * 0.38f, radius * 0.42f),
            radius = radius * 1.7f,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = PlanetBlue.copy(alpha = 0.38f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )

    rotate(-9f, center) {
        drawArc(
            color = PlanetLight.copy(alpha = 0.72f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = orbitRect.topLeft,
            size = orbitRect.size,
            style = orbitStroke,
        )
        if (orbitDegrees in 0f..180f) {
            val angle = orbitDegrees / 180f * PI
            val moon = Offset(
                center.x + (cos(angle) * radius * 1.55f).toFloat(),
                center.y + (sin(angle) * radius * 0.45f).toFloat(),
            )
            drawCircle(PlanetLight.copy(alpha = 0.18f), radius = 7.dp.toPx(), center = moon)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = moon)
        }
    }
}
