package io.github.vexpaer.brainexporter.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.BarValue
import io.github.vexpaer.brainexporter.sdk.LineSeries
import io.github.vexpaer.brainexporter.sdk.SignalSample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal val ChannelColors = listOf(
    Color(0xFF38D9C5),
    Color(0xFF6A8DFF),
    Color(0xFFFFBD5B),
    Color(0xFFE57AFF),
    Color(0xFF5BE68B),
    Color(0xFFFF7C88),
    Color(0xFF50B8FF),
    Color(0xFFFF9D55),
)

private val BandColors = listOf(
    Color(0xFF6786FF),
    Color(0xFF48B8FF),
    Color(0xFF38D9C5),
    Color(0xFFFFBD5B),
    Color(0xFFE57AFF),
)

@Composable
internal fun TimeSignalChart(
    samples: List<SignalSample>,
    channel: Int,
    demean: Boolean,
    modifier: Modifier = Modifier,
) {
    val prepared = remember(samples, channel, demean) {
        if (samples.size < 2) null else {
            val latest = samples.last().index
            val selected = samples.filter { it.index >= latest - 1_249 && it.valuesUv.size >= channel }
            if (selected.size < 2) null else {
                val raw = selected.map { it.valuesUv[channel - 1] }
                val mean = if (demean) raw.average() else 0.0
                val values = raw.map { it - mean }
                val range = paddedRange(values, minimumSpan = 10.0)
                TimePrepared(selected, values, latest, range.first, range.second)
            }
        }
    }
    if (prepared == null) {
        EmptyChart("等待足够的时域数据", modifier)
        return
    }

    Canvas(modifier = modifier.height(190.dp)) {
        val plot = drawChartAxes(
            yMin = prepared.yMin,
            yMax = prepared.yMax,
            yUnit = "µV",
            xLabels = listOf("-5", "-4", "-3", "-2", "-1", "0 s"),
        )
        val path = Path()
        var previousIndex: Long? = null
        prepared.samples.forEachIndexed { point, sample ->
            val relative = (sample.index - prepared.latestIndex) / 250.0
            val x = plot.left + ((relative + 5.0) / 5.0).toFloat() * plot.width
            val y = plot.top + ((prepared.yMax - prepared.values[point]) /
                (prepared.yMax - prepared.yMin)).toFloat() * plot.height
            if (previousIndex == null || sample.index - previousIndex!! > 1) path.moveTo(x, y)
            else path.lineTo(x, y)
            previousIndex = sample.index
        }
        drawPath(path, ChannelColors[channel - 1], style = androidx.compose.ui.graphics.drawscope.Stroke(1.7.dp.toPx()))
    }
}

@Composable
internal fun FrequencyChart(
    series: LineSeries?,
    channel: Int,
    isPsd: Boolean,
    modifier: Modifier = Modifier,
) {
    if (series == null || series.x.size < 2 || series.y.size != series.x.size) {
        EmptyChart("正在积累连续分析窗口", modifier)
        return
    }
    val validIndices = remember(series) {
        series.x.indices.filter { series.x[it] in 1.0..60.0 && series.y[it].isFinite() }
    }
    if (validIndices.size < 2) {
        EmptyChart("等待足够的连续数据", modifier)
        return
    }
    val range = remember(series, isPsd) {
        val values = validIndices.map { series.y[it] }
        if (isPsd) paddedRange(values, minimumSpan = 25.0)
        else 0.0 to positiveMaximum(values.maxOrNull() ?: 1.0)
    }

    Canvas(modifier = modifier.height(190.dp)) {
        val plot = drawChartAxes(
            yMin = range.first,
            yMax = range.second,
            yUnit = if (isPsd) "dB µV²/Hz" else "µV",
            xLabels = listOf("0", "10", "20", "30", "40", "50", "60 Hz"),
        )
        val path = Path()
        validIndices.forEachIndexed { point, index ->
            val x = plot.left + (series.x[index] / 60.0).toFloat() * plot.width
            val y = plot.top + ((range.second - series.y[index]) /
                (range.second - range.first)).toFloat() * plot.height
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, ChannelColors[channel - 1], style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
    }
}

@Composable
internal fun BandPowerChart(
    bars: List<BarValue>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        EmptyChart("正在积累最近 5 秒数据", modifier)
        return
    }
    val yMax = min(100.0, max(20.0, (bars.maxOf { it.value } * 1.18)))
    Canvas(modifier = modifier.height(190.dp)) {
        val plot = drawChartAxes(
            yMin = 0.0,
            yMax = yMax,
            yUnit = "相对功率 %",
            xLabels = bars.map { it.label },
        )
        val slot = plot.width / bars.size
        val width = min(48.dp.toPx(), slot * 0.56f)
        bars.forEachIndexed { index, bar ->
            val barHeight = (bar.value / yMax).coerceIn(0.0, 1.0).toFloat() * plot.height
            val left = plot.left + slot * (index + 0.5f) - width / 2f
            drawRoundRect(
                color = BandColors[index % BandColors.size],
                topLeft = Offset(left, plot.top + plot.height - barHeight),
                size = androidx.compose.ui.geometry.Size(width, barHeight),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            )
            drawLabel(
                text = "${bar.value.toInt()}%",
                x = left + width / 2f,
                y = plot.top + plot.height - barHeight - 6.dp.toPx(),
                align = Paint.Align.CENTER,
                color = TextMuted,
                textSize = 10.dp.toPx(),
            )
        }
    }
}

@Composable
private fun EmptyChart(message: String, modifier: Modifier) {
    Card(
        modifier = modifier.height(190.dp),
        colors = CardDefaults.cardColors(containerColor = PanelSoft.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = message,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private data class TimePrepared(
    val samples: List<SignalSample>,
    val values: List<Double>,
    val latestIndex: Long,
    val yMin: Double,
    val yMax: Double,
)

private data class PlotArea(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private fun DrawScope.drawChartAxes(
    yMin: Double,
    yMax: Double,
    yUnit: String,
    xLabels: List<String>,
): PlotArea {
    val left = 58.dp.toPx()
    val right = 12.dp.toPx()
    val top = 17.dp.toPx()
    val bottom = 31.dp.toPx()
    val plot = PlotArea(left, top, size.width - left - right, size.height - top - bottom)
    val gridStroke = 0.7.dp.toPx()
    for (tick in 0..4) {
        val y = plot.top + plot.height * tick / 4f
        drawLine(Grid, Offset(plot.left, y), Offset(plot.left + plot.width, y), gridStroke)
        val value = yMax - (yMax - yMin) * tick / 4.0
        drawLabel(
            text = formatAxis(value),
            x = plot.left - 7.dp.toPx(),
            y = y + 3.dp.toPx(),
            align = Paint.Align.RIGHT,
            color = TextMuted,
            textSize = 9.dp.toPx(),
        )
    }
    xLabels.forEachIndexed { index, label ->
        val divisor = max(1, xLabels.lastIndex)
        val x = plot.left + plot.width * index / divisor
        drawLine(Grid, Offset(x, plot.top), Offset(x, plot.top + plot.height), gridStroke)
        drawLabel(
            text = label,
            x = x,
            y = plot.top + plot.height + 18.dp.toPx(),
            align = Paint.Align.CENTER,
            color = TextMuted,
            textSize = 9.dp.toPx(),
        )
    }
    drawLabel(
        text = yUnit,
        x = plot.left,
        y = 10.dp.toPx(),
        align = Paint.Align.LEFT,
        color = TextMuted,
        textSize = 9.dp.toPx(),
    )
    return plot
}

private fun DrawScope.drawLabel(
    text: String,
    x: Float,
    y: Float,
    align: Paint.Align,
    color: Color,
    textSize: Float,
) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        x,
        y,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            this.textSize = textSize
            textAlign = align
        },
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private fun paddedRange(values: List<Double>, minimumSpan: Double): Pair<Double, Double> {
    var low = values.minOrNull() ?: -minimumSpan / 2
    var high = values.maxOrNull() ?: minimumSpan / 2
    var span = high - low
    if (!span.isFinite() || span < minimumSpan) {
        val center = if (low.isFinite() && high.isFinite()) (low + high) / 2 else 0.0
        low = center - minimumSpan / 2
        high = center + minimumSpan / 2
        span = minimumSpan
    }
    val padding = span * 0.1
    return low - padding to high + padding
}

private fun positiveMaximum(value: Double): Double {
    if (!value.isFinite() || value <= 0) return 1.0
    return max(1.0, value * 1.15)
}

private fun formatAxis(value: Double): String = when {
    abs(value) >= 1_000 -> "%.1fk".format(value / 1_000.0)
    abs(value) >= 100 -> "%.0f".format(value)
    abs(value) >= 10 -> "%.1f".format(value)
    else -> "%.2f".format(value)
}
