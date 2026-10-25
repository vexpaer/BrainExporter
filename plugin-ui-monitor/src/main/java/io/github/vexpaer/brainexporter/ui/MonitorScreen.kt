package io.github.vexpaer.brainexporter.ui

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.DeviceDescriptor
import io.github.vexpaer.brainexporter.sdk.ImpedanceQuality
import io.github.vexpaer.brainexporter.sdk.ImpedanceResult
import io.github.vexpaer.brainexporter.sdk.ModuleFeatureSeries
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleType
import io.github.vexpaer.brainexporter.sdk.SignalSample

/**
 * 监测页:由简入繁。
 * - 未Connection:全屏只有一个"Scan & connect"主按钮,极简引导。
 * - 已Connection:整页沉浸式脑电主信号,底部主操作坞承载采集,复杂控制收进面板。
 * Connection / 采集 / Impedance三种主动作层级分明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonitorScreen(
    snapshot: MonitorSnapshot,
    controller: MonitorController,
    permissionGate: PermissionGate,
    recordingPermissionGate: RecordingPermissionGate,
    modifier: Modifier = Modifier,
) {
    var panelVisible by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connected = snapshot.connection.phase == ConnectionPhase.CONNECTED
    val busy = snapshot.connection.phase == ConnectionPhase.CONNECTING
    val scanning = snapshot.connection.phase == ConnectionPhase.SCANNING
    val selectedChannels = remember { mutableStateListOf<Int>().apply { addAll(1..8) } }
    var demean by rememberSaveable { mutableStateOf(true) }
    var impedanceRequest by remember { mutableIntStateOf(-1) }

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = connected,
            transitionSpec = {
                fadeIn(tween(Motion.StandardMs, easing = Motion.Emphasized))
                    .togetherWith(fadeOut(tween(Motion.FastMs)))
            },
            label = "monitor-mode",
        ) { isConnected ->
            if (!isConnected) {
                ConnectHero(
                    busy = busy,
                    scanning = scanning,
                    onScan = { permissionGate(controller::scan) },
                    onOpenPanel = { panelVisible = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ConnectedStage(
                    snapshot = snapshot,
                    controller = controller,
                    recordingPermissionGate = recordingPermissionGate,
                    selectedChannels = selectedChannels,
                    demean = demean,
                    onOpenPanel = { panelVisible = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (panelVisible) {
        ModalBottomSheet(
            onDismissRequest = { panelVisible = false },
            sheetState = sheetState,
            containerColor = Obsidian,
            contentColor = Starlight,
            shape = ShapeL,
        ) {
            ControlPanel(
                snapshot = snapshot,
                controller = controller,
                permissionGate = permissionGate,
                connected = connected,
                selectedChannels = selectedChannels,
                demean = demean,
                onDemeanChange = { demean = it },
                onImpedanceRequest = { impedanceRequest = it },
            )
        }
    }

    if (impedanceRequest >= 0) {
        val target = impedanceRequest.takeIf { it > 0 }
        BrandAlertDialog(
            onDismissRequest = { impedanceRequest = -1 },
            title = "Confirm impedance test",
            confirmLabel = if (target == null) "Measure all channels" else "Measure CH $target",
            onConfirm = {
                controller.startImpedance(target)
                impedanceRequest = -1
            },
            dismissLabel = "Cancel",
            onDismiss = { impedanceRequest = -1 },
            text = {
                Text(
                    "ADS1299 injects approximately 6 nA AC test current.\n\n" +
                        "Use battery power or compliant isolation. Never connect a worn board to non-isolated mains-powered USB.",
                )
            },
        )
    }
}

/** 未Connection时的极简引导:一个主按钮 + 一句话 + 面板入口。 */
@Composable
private fun ConnectHero(
    busy: Boolean,
    scanning: Boolean,
    onScan: () -> Unit,
    onOpenPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanetScaffold(modifier = modifier, atmospheric = true) {
        IconButton(
            onClick = onOpenPanel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = Spacing.S, vertical = Spacing.XS),
        ) {
            Icon(imageVector = Icons.Filled.Tune, contentDescription = "Open monitor controls", tint = MoonDust)
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.XXL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).border(1.dp, Hairline, CircleShape))
                Button(
                    onClick = onScan,
                    enabled = !busy,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrbitBlue,
                        contentColor = OnOrbit,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (busy || scanning) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = OnOrbit,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            if (scanning) "Scanning" else if (busy) "Connecting" else "Scan",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = if (scanning) "Searching for EEG devices…" else "Connect your EEG device",
                color = Starlight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "RT-BCI / ADS1299 · BLE",
                color = MoonDust,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 已Connection后的沉浸工作台:主信号优先,右上状态胶囊,底部主操作坞。 */
@Composable
private fun ConnectedStage(
    snapshot: MonitorSnapshot,
    controller: MonitorController,
    recordingPermissionGate: RecordingPermissionGate,
    selectedChannels: List<Int>,
    demean: Boolean,
    onOpenPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analysisByChannel = remember(snapshot.analyses) { snapshot.analyses.associateBy { it.channel } }
    Box(modifier.background(Void)) {
        AuroraBackground(Modifier.fillMaxSize(), quiet = true)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to PlanetPalette.backgroundTop.copy(alpha = 0.24f),
                    0.5f to Color.Transparent,
                    1f to PlanetPalette.backgroundMid.copy(alpha = 0.30f),
                ),
            )
        }
        when {
            snapshot.activeView == MonitorView.IMPEDANCE ->
                ImmersiveImpedance(snapshot = snapshot, modifier = Modifier.fillMaxSize())
            snapshot.selectedModuleType == ProcessingModuleType.EEG_TO_FEATURES ->
                FeatureMatrix(snapshot = snapshot, modifier = Modifier.fillMaxSize())
            else ->
                ImmersiveSignalCanvas(
                    snapshot = snapshot,
                    analysisByChannel = analysisByChannel,
                    selectedChannels = selectedChannels,
                    demean = demean,
                    activeView = snapshot.activeView,
                    modifier = Modifier.fillMaxSize().padding(bottom = 104.dp),
                )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = Spacing.M, vertical = Spacing.XS)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XXS),
        ) {
            StatusPill(
                dotColor = Success,
                label = "${snapshot.connection.deviceName ?: "RT-BCI"} · ${metricsText(snapshot)}",
                modifier = Modifier.weight(1f, fill = false).semantics {
                    contentDescription = "Connected ${snapshot.connection.deviceName ?: "device"}，sample rate ${metricsText(snapshot)}"
                },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenPanel) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = "Open monitor controls", tint = MoonDust)
            }
            IconButton(onClick = { controller.disconnect() }) {
                Icon(imageVector = Icons.Filled.PowerSettingsNew, contentDescription = "Disconnect device", tint = MoonDust)
            }
        }

        PrimaryActionDock {
            val acquisition = snapshot.acquisition
            Column(Modifier.weight(1f)) {
                Text(
                    text = acquisition.message,
                    color = if (acquisition.active) OrbitBlue else MoonDust,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (acquisition.active) {
                    Text(
                        "Saved ${acquisition.samplesWritten} samples",
                        color = MoonDust,
                        style = MaterialTheme.typography.labelSmall.numeric(),
                    )
                }
            }
            if (snapshot.activeView != MonitorView.IMPEDANCE &&
                snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES
            ) {
                OutlinedButton(onClick = { controller.setView(MonitorView.IMPEDANCE) }) {
                    Text("Impedance")
                }
            }
            if (acquisition.active) {
                Button(
                    onClick = controller::stopAcquisition,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color(0xFF2A0709)),
                ) { Text("Stop recording") }
            } else {
                Button(
                    onClick = { recordingPermissionGate(controller::startAcquisition) },
                    enabled = !snapshot.impedance.running,
                ) { Text(if (snapshot.impedance.running) "Measuring impedance…" else "Start recording") }
            }
        }
    }
}

private fun metricsText(snapshot: MonitorSnapshot): String {
    val m = snapshot.metrics
    return if (m.frames == 0L) "Waiting for data" else {
        val rate = "%.1f Hz".format(m.effectiveSampleRateHz)
        val loss = "%.1f%%".format(m.packetLossRatio * 100)
        "$rate · packet loss $loss"
    }
}

/** 单画布多Channels信号显示:每条Channels一根线,垂直分布,一屏看全部。 */
@Composable
private fun ImmersiveSignalCanvas(
    snapshot: MonitorSnapshot,
    analysisByChannel: Map<Int, ChannelAnalysis>,
    selectedChannels: List<Int>,
    demean: Boolean,
    activeView: MonitorView,
    modifier: Modifier = Modifier,
) {
    val channels = selectedChannels.sorted()
    val samples = snapshot.samples
    val prepared = remember(samples, channels, demean, activeView) {
        if (activeView != MonitorView.TIME) null else prepareTimeData(samples, channels, demean)
    }
    Canvas(modifier.fillMaxSize()) {
        if (channels.isEmpty()) return@Canvas
        val laneHeight = size.height / channels.size
        channels.forEachIndexed { laneIndex, channel ->
            val yCenter = laneIndex * laneHeight + laneHeight / 2f
            val color = ChannelColors[channel - 1]
            if (laneIndex > 0) {
                drawLine(
                    color = Grid.copy(alpha = 0.35f),
                    start = Offset(0f, laneIndex * laneHeight),
                    end = Offset(size.width, laneIndex * laneHeight),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            drawChannelLabel("CH $channel", color, yCenter)

            when (activeView) {
                MonitorView.TIME -> {
                    val data = prepared?.valuesByChannel?.get(channel)
                    if (data != null) {
                        val path = Path()
                        val yMin = prepared.yRange.first
                        val yMax = prepared.yRange.second
                        data.forEachIndexed { point, value ->
                            val x = size.width * point / maxOf(1, data.lastIndex)
                            val y = yCenter - ((value - yMin) / (yMax - yMin)).toFloat().let { (it - 0.5f) } * laneHeight * 0.9f
                            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                MonitorView.PSD, MonitorView.SPECTRUM -> {
                    val line = analysisByChannel[channel]?.line
                    if (line != null && line.x.size > 1) {
                        val path = Path()
                        val maxY = line.y.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                        line.y.forEachIndexed { point, value ->
                            val x = size.width * point / maxOf(1, line.y.lastIndex)
                            val y = yCenter - (value / maxY).toFloat().coerceIn(0f, 1f) * laneHeight * 0.75f
                            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                MonitorView.BANDS -> {
                    val bars = analysisByChannel[channel]?.bars.orEmpty()
                    if (bars.isNotEmpty()) {
                        val slot = size.width / bars.size
                        bars.forEachIndexed { index, bar ->
                            val barHeight = (bar.value / 100.0).coerceIn(0.02, 1.0).toFloat() * laneHeight * 0.62f
                            val left = slot * (index + 0.5f) + slot * 0.12f
                            drawLine(
                                color = BandColors[index % BandColors.size],
                                start = Offset(left, yCenter + barHeight / 2f),
                                end = Offset(left, yCenter - barHeight / 2f),
                                strokeWidth = slot * 0.45f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private data class PreparedTime(
    val valuesByChannel: Map<Int, List<Double>>,
    val yRange: Pair<Double, Double>,
)

private fun prepareTimeData(
    samples: List<SignalSample>,
    channels: List<Int>,
    demean: Boolean,
): PreparedTime? {
    if (samples.size < 2) return null
    val latest = samples.last().index
    val selected = samples.filter { it.index >= latest - 1_249 && it.valuesUv.isNotEmpty() }
    if (selected.size < 2) return null
    val valuesByChannel = channels.associateWith { channel ->
        val raw = selected.map { it.valuesUv.getOrNull(channel - 1) ?: 0.0 }
        if (demean) {
            val mean = raw.average()
            raw.map { it - mean }
        } else raw
    }
    val all = valuesByChannel.values.flatten()
    val low = all.minOrNull() ?: -5.0
    val high = all.maxOrNull() ?: 5.0
    val span = (high - low).coerceAtLeast(10.0)
    val center = (low + high) / 2.0
    return PreparedTime(valuesByChannel, center - span / 2 to center + span / 2)
}

private fun DrawScope.drawChannelLabel(text: String, color: Color, yCenter: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = 10.sp.toPx()
        this.alpha = 210
    }
    drawContext.canvas.nativeCanvas.drawText(text, 12.dp.toPx(), yCenter + 3.5.dp.toPx(), paint)
}

/** 特征模式:全屏大数字矩阵,数据即视觉。 */
@Composable
private fun FeatureMatrix(
    snapshot: MonitorSnapshot,
    modifier: Modifier = Modifier,
) {
    val features = snapshot.moduleFeatures
    Box(modifier.fillMaxSize()) {
        if (features.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.GraphicEq,
                title = "Start recording to see live feature output.",
                detail = null,
                tint = LunarViolet,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.M),
                contentPadding = PaddingValues(top = 68.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.L),
            ) {
                items(features, key = { it.key }) { series ->
                    FeatureHero(series)
                }
            }
        }
    }
}

@Composable
private fun FeatureHero(series: ModuleFeatureSeries) {
    val latest = series.values.lastOrNull()
    val color = series.channel?.let { ChannelColors.getOrNull(it - 1) } ?: LunarViolet
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
        Text(
            text = series.channel?.let { "CH $it · ${series.label}" } ?: series.label,
            color = MoonDust,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = latest?.let { formatFeatureValue(it) } ?: "—",
            color = Starlight,
            style = MaterialTheme.typography.displayMedium.numeric(),
            fontWeight = FontWeight.Light,
        )
        Text(
            text = series.unit,
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
        FeatureHistoryChart(series.values, color, Modifier.fillMaxWidth())
    }
}

/** Impedance模式:全屏进度 + 大号读数,控制留在面板。 */
@Composable
private fun ImmersiveImpedance(
    snapshot: MonitorSnapshot,
    modifier: Modifier = Modifier,
) {
    val impedance = snapshot.impedance
    Box(modifier.fillMaxSize().padding(horizontal = Spacing.XL), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text("ADS1299 impedance", color = MoonDust, style = MaterialTheme.typography.labelLarge)
            if (impedance.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(54.dp),
                    color = OrbitBlue,
                    strokeWidth = 4.dp,
                )
                Text(
                    "CH ${impedance.channel ?: "—"} · ${(impedance.progress * 100).toInt()}%",
                    color = OrbitBlue,
                    style = MaterialTheme.typography.displaySmall.numeric(),
                )
            } else {
                val lastGood = impedance.results.withIndex()
                    .filter { it.value != null }
                    .maxByOrNull { it.index }
                if (lastGood != null) {
                    val result = lastGood.value!!
                    Text(
                        "CH ${lastGood.index + 1}",
                        color = MoonDust,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatImpedance(result.kiloOhms),
                        color = Starlight,
                        style = MaterialTheme.typography.displayLarge.numeric(),
                        fontWeight = FontWeight.Light,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(qualityColor(result.quality), CircleShape))
                        Text(
                            text = qualityLabel(result.quality),
                            color = qualityColor(result.quality),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Text("Choose channels in Controls to measure", color = MoonDust, textAlign = TextAlign.Center)
                }
            }
            impedance.error?.let { Text(it, color = Danger, textAlign = TextAlign.Center) }
        }
    }
}

/** Impedance质量色:低饱和绿仅作小指示;偏高用暖金属;较差用错误红。 */
private fun qualityColor(quality: ImpedanceQuality?): Color = when (quality) {
    ImpedanceQuality.GOOD -> Success
    ImpedanceQuality.WARNING -> SolarMetal
    ImpedanceQuality.BAD -> Danger
    null -> MoonDust
}

private fun qualityLabel(quality: ImpedanceQuality): String = when (quality) {
    ImpedanceQuality.GOOD -> "Good (< 750 kΩ)"
    ImpedanceQuality.WARNING -> "Elevated (750–2500 kΩ)"
    ImpedanceQuality.BAD -> "High (> 2500 kΩ)"
}

/** 底部控制面板:所有复杂控件收进来,按功能分组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(
    snapshot: MonitorSnapshot,
    controller: MonitorController,
    permissionGate: PermissionGate,
    connected: Boolean,
    selectedChannels: MutableList<Int>,
    demean: Boolean,
    onDemeanChange: (Boolean) -> Unit,
    onImpedanceRequest: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L),
        contentPadding = PaddingValues(top = Spacing.XS, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item {
            Text(
                "Monitor controls",
                color = Starlight,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item { SectionLabel("device") }
        item {
            if (connected) {
                InstrumentSurface(color = Obsidian) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(snapshot.connection.deviceName ?: "RT-BCI", color = Starlight, fontWeight = FontWeight.SemiBold)
                            snapshot.connection.profileName?.let {
                                Text(it, color = MoonDust, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(onClick = controller::disconnect) { Text("Disconnect") }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Button(
                        onClick = { permissionGate(controller::scan) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Scan & connect") }
                    if (snapshot.devices.isNotEmpty()) {
                        Text("Available devices", color = MoonDust, style = MaterialTheme.typography.labelMedium)
                        snapshot.devices.take(8).forEach { device ->
                            DeviceRow(device = device, onConnect = { controller.connect(device.id) })
                        }
                    } else {
                        Text(
                            "Select a discovered RT-BCI device.",
                            color = MoonDust,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item { SectionLabel("Recording") }
        item {
            val acquisition = snapshot.acquisition
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Box(Modifier.size(8.dp).background(if (acquisition.active) Success else MoonDust.copy(alpha = 0.6f), CircleShape))
                    Text(acquisition.message, color = if (acquisition.active) Starlight else MoonDust)
                }
                if (acquisition.active) {
                    Text(
                        "Saved ${acquisition.samplesWritten} samples",
                        color = MoonDust,
                        style = MaterialTheme.typography.bodySmall.numeric(),
                    )
                }
                acquisition.fileLocation?.let {
                    Text(it, color = MoonDust, style = MaterialTheme.typography.labelSmall)
                }
                acquisition.error?.let { Text(it, color = Danger) }
                Text(
                    "CSV · Documents/eegData",
                    color = MoonDust.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        item { SectionLabel("Analysis") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                        items(viewOptions.size) { index ->
                            val (view, label) = viewOptions[index]
                            ViewChip(
                                label = label,
                                selected = snapshot.activeView == view,
                                onClick = { controller.setView(view) },
                            )
                        }
                    }
                    if (snapshot.activeView == MonitorView.TIME) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                        ) {
                            Text("Remove DC offset", color = MoonDust, style = MaterialTheme.typography.labelMedium)
                            Switch(checked = demean, onCheckedChange = onDemeanChange)
                        }
                    } else {
                        Text(viewHint(snapshot.activeView), color = MoonDust, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES) {
            item { SectionLabel("Signal source") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                    ChipSelector(
                        options = buildList {
                            add(ChipOption(null as String?, "Raw EEG"))
                            snapshot.modules.filter { it.enabled }.forEach { module ->
                                add(
                                    ChipOption(
                                        module.descriptor.id as String?,
                                        module.descriptor.displayName,
                                        dotColor = if (module.descriptor.type == ProcessingModuleType.EEG_TO_EEG) OrbitBlue else LunarViolet,
                                    ),
                                )
                            }
                        },
                        isSelected = { value -> value == snapshot.selectedModuleId },
                        onSelect = { value -> controller.selectModule(value) },
                    )
                    if (snapshot.modules.none { it.enabled }) {
                        Text("Enable a processing module under Modules.", color = MoonDust, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES) {
            item { SectionLabel("Channels") }
            item {
                ChipSelector(
                    options = (1..8).map { channel ->
                        ChipOption(channel, "CH $channel", dotColor = ChannelColors[channel - 1])
                    },
                    isSelected = { channel -> channel in selectedChannels },
                    onSelect = { channel ->
                        if (channel in selectedChannels) selectedChannels.remove(channel) else {
                            selectedChannels.add(channel)
                            selectedChannels.sort()
                        }
                    },
                    trailing = {
                        Row {
                            TextButton(onClick = {
                                selectedChannels.clear()
                                selectedChannels.addAll(1..8)
                            }) { Text("All") }
                            TextButton(onClick = { selectedChannels.clear() }) { Text("Clear") }
                        }
                    },
                )
            }
        }

        if (snapshot.activeView == MonitorView.IMPEDANCE) {
            item { SectionLabel("Impedance detail") }
            item {
                ImpedanceToolbar(
                    snapshot = snapshot,
                    onMeasureAll = { onImpedanceRequest(0) },
                    onStop = controller::stopImpedance,
                )
            }
            items(selectedChannels.toList(), key = { "impedance-$it" }) { channel ->
                ImpedanceCard(
                    channel = channel,
                    result = snapshot.impedance.results.getOrNull(channel - 1),
                    measuring = snapshot.impedance.running && snapshot.impedance.channel == channel,
                    enabled = connected && !snapshot.impedance.running && !snapshot.acquisition.active,
                    onMeasure = { onImpedanceRequest(channel) },
                )
            }
        }

        item { SectionLabel("Link metrics") }
        item { MetricsStrip(snapshot) }
        item {
            Text(
                "Hann window · interpolated packet gaps · research use only.",
                color = MoonDust.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = Spacing.S),
            )
        }
    }
}

private val viewOptions = listOf(
    MonitorView.TIME to "Waveform",
    MonitorView.PSD to "5s PSD",
    MonitorView.SPECTRUM to "Spectrum",
    MonitorView.BANDS to "Bands",
    MonitorView.IMPEDANCE to "Impedance test",
)

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Obsidian
    val borderColor = if (selected) OrbitBlue.copy(alpha = 0.55f) else Hairline
    Box(
        modifier = Modifier
            .clip(ShapeCapsule)
            .background(background)
            .border(1.dp, borderColor, ShapeCapsule)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = Spacing.M, vertical = 13.dp),
    ) {
        Text(
            label,
            color = if (selected) Starlight else MoonDust,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun DeviceRow(device: DeviceDescriptor, onConnect: () -> Unit) {
    InstrumentSurface(color = Obsidian) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Box(Modifier.size(9.dp).background(if (device.recommended) OrbitBlue else MoonDust, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(device.name, color = Starlight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${device.id} · ${device.rssi} dBm${if (device.recommended) " · recommended" else ""}",
                    color = MoonDust,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(onClick = onConnect) { Text("Connection") }
        }
    }
}

@Composable
private fun MetricsStrip(snapshot: MonitorSnapshot) {
    val metrics = snapshot.metrics
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        item { MetricCard("Sample rate", if (metrics.frames == 0L) "—" else "%.1f Hz".format(metrics.effectiveSampleRateHz), "Target 250 Hz") }
        item { MetricCard("Packet loss", if (metrics.frames == 0L) "—" else "%.1f%%".format(metrics.packetLossRatio * 100), "Missing ${metrics.missingPackets}") }
        item { MetricCard("Valid frames", "${metrics.frames}", formatBytes(metrics.receivedBytes)) }
        item { MetricCard("Link", snapshot.connection.profileName ?: "—", snapshot.connection.deviceName ?: "Not connected") }
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String) {
    InstrumentSurface(
        modifier = Modifier.width(164.dp),
        contentPadding = PaddingValues(Spacing.S),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
            Text(label, color = MoonDust, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                color = Starlight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.numeric(),
            )
            Text(detail, color = MoonDust, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ImpedanceToolbar(
    snapshot: MonitorSnapshot,
    onMeasureAll: () -> Unit,
    onStop: () -> Unit,
) {
    InstrumentSurface(color = Obsidian) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("ADS1299 impedance", color = Starlight, fontWeight = FontWeight.SemiBold)
            Text("6 nA · 31.2 Hz · 5s/channel", color = MoonDust, style = MaterialTheme.typography.bodySmall)
            if (snapshot.impedance.running) {
                Text(
                    "CH ${snapshot.impedance.channel ?: "—"} · ${(snapshot.impedance.progress * 100).toInt()}%",
                    color = OrbitBlue,
                    style = MaterialTheme.typography.bodyMedium.numeric(),
                )
            }
            snapshot.impedance.error?.let { Text(it, color = Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Button(
                    onClick = onMeasureAll,
                    enabled = snapshot.connection.phase == ConnectionPhase.CONNECTED &&
                        !snapshot.impedance.running && !snapshot.acquisition.active,
                ) { Text("Measure all") }
                OutlinedButton(onClick = onStop, enabled = snapshot.impedance.running) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun ImpedanceCard(
    channel: Int,
    result: ImpedanceResult?,
    measuring: Boolean,
    enabled: Boolean,
    onMeasure: () -> Unit,
) {
    InstrumentSurface(color = Obsidian) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Box(Modifier.size(10.dp).background(ChannelColors[channel - 1], CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CH $channel", color = Starlight, fontWeight = FontWeight.SemiBold)
                Text(
                    result?.let { formatImpedance(it.kiloOhms) } ?: "—",
                    color = Starlight,
                    style = MaterialTheme.typography.titleLarge.numeric(),
                )
                Text(
                    if (measuring) "Measuring…" else result?.let { qualityLabel(it.quality) } ?: "Not measured",
                    color = if (measuring) OrbitBlue else qualityColor(result?.quality),
                    style = MaterialTheme.typography.labelMedium,
                )
                result?.let {
                    Text(
                        "σ ${"%.2f".format(it.standardDeviationUv)} µV · ${it.sampleCount} samples",
                        color = MoonDust,
                        style = MaterialTheme.typography.labelSmall.numeric(),
                    )
                }
            }
            Button(onClick = onMeasure, enabled = enabled) { Text("Measure") }
        }
    }
}

private fun viewHint(view: MonitorView): String = when (view) {
    MonitorView.TIME -> "5s window"
    MonitorView.PSD -> "Hann · latest 5s · 0–60 Hz"
    MonitorView.SPECTRUM -> "FFT magnitude · ~2s · 0–60 Hz"
    MonitorView.BANDS -> "Relative power · latest 5s"
    MonitorView.IMPEDANCE -> "6 nA · 31.2 Hz · 5s/channel"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f kB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatImpedance(kiloOhms: Double): String =
    if (kiloOhms >= 1_000) "%.2f MΩ".format(kiloOhms / 1_000.0)
    else "%.0f kΩ".format(kiloOhms)

private fun formatFeatureValue(value: Double): String = when {
    kotlin.math.abs(value) >= 1_000 -> "%.1fk".format(value / 1_000.0)
    kotlin.math.abs(value) >= 100 -> "%.1f".format(value)
    kotlin.math.abs(value) >= 10 -> "%.2f".format(value)
    else -> "%.3f".format(value)
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
