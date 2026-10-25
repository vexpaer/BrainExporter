package io.github.vexpaer.brainexporter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class GrainPoint(val x: Float, val y: Float, val alpha: Float)

/**
 * Aurora wash with four independently drifting, overlapping soft lights.
 * Lightweight deterministic grain is drawn over the glow; no shaders or network assets.
 */
@Composable
internal fun AuroraBackground(modifier: Modifier = Modifier, quiet: Boolean = false) {
    val reducedMotion = rememberReduceMotion()
    val phase = if (reducedMotion || quiet) 0f else {
        val transition = rememberInfiniteTransition(label = "aurora-drift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(78_000, easing = LinearEasing)),
            label = "aurora-phase",
        ).value
    }
    val grain = remember {
        List(320) { i ->
            GrainPoint(
                x = ((i * 173 + 37) % 997) / 997f,
                y = ((i * 311 + 67) % 991) / 991f,
                alpha = (2 + i % 4) / 100f,
            )
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val extent = max(w, h)
        val gain = if (quiet) 0.36f else 1f

        fun glow(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to color.copy(alpha = alpha * gain),
                        0.36f to color.copy(alpha = alpha * gain * 0.44f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(w * x, h * y),
                    radius = extent * radius,
                ),
            )
        }
        glow(OrbitBlue, 0.18f + 0.15f * sin(phase), 0.24f + 0.12f * cos(phase), 0.68f, 0.34f)
        glow(LunarViolet, 0.81f + 0.12f * cos(phase * 0.83f), 0.42f + 0.13f * sin(phase), 0.65f, 0.27f)
        glow(AuroraRose, 0.42f + 0.16f * cos(phase * 0.71f), 0.82f + 0.08f * sin(phase * 0.93f), 0.60f, 0.19f)
        glow(AuroraAmber, 0.63f + 0.12f * sin(phase * 1.13f), 0.11f + 0.08f * cos(phase), 0.52f, 0.16f)
        drawRect(Brush.verticalGradient(0f to Void.copy(alpha = 0.06f), 1f to Void.copy(alpha = 0.57f)))
        grain.forEach { p ->
            drawCircle(
                color = Color.White.copy(alpha = p.alpha * (if (quiet) 0.5f else 1f)),
                radius = 0.62f,
                center = Offset(w * p.x, h * p.y),
            )
        }
    }
}
