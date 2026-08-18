package io.github.vexpaer.brainexporter.ui

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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

/**
 * 监测页:由简入繁。
 * - 未连接:全屏只有一个"扫描并连接"按钮,极简到一目了然。
 * - 已连接:整页沉浸式单画布脑电波形,无按钮打扰,只在角落留一个状态行。
 * - 复杂控制(视图/通道/采集/阻抗/指标/数据源)全部收进底部控制面板,需要时才展开。
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

    Box(modifier = modifier.fillMaxSize()) {
        // 沉浸区:未连接时是极简引导,连接后是全屏波形。
        AnimatedContent(
            targetState = connected,
            transitionSpec = {
                fadeIn(tween(Motion.StandardMs)).togetherWith(fadeOut(tween(Motion.StandardMs)))
            },
            label = "monitor-mode",
        ) { isConnected ->
            if (!isConnected) {
                ConnectHero(
                    snapshot = snapshot,
                    busy = busy,
                    scanning = scanning,
                    onScan = { permissionGate(controller::scan) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ImmersiveEeg(
                    snapshot = snapshot,
                    selectedChannels = selectedChannels,
                    demean = demean,
                    onDisconnect = controller::disconnect,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 右侧悬浮:设置面板入口(始终可用,未连接时也提供设备列表)。
        FloatingActionButton(
            onClick = { panelVisible = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .semantics { contentDescription = "打开监测控制面板" },
            containerColor = PanelRaised,
            contentColor = Cyan,
        ) {
            Icon(imageVector = Icons.Filled.Tune, contentDescription = null)
        }
    }

    if (panelVisible) {
        ModalBottomSheet(
            onDismissRequest = { panelVisible = false },
            sheetState = sheetState,
            containerColor = PanelSoft,
        ) {
            ControlPanel(
                snapshot = snapshot,
                controller = controller,
                permissionGate = permissionGate,
                recordingPermissionGate = recordingPermissionGate,
                connected = connected,
                selectedChannels = selectedChannels,
                demean = demean,
                onDemeanChange = { demean = it },
                impedanceRequest = impedanceRequest,
                onImpedanceRequest = { impedanceRequest = it },
            )
        }
    }

    if (impedanceRequest >= 0) {
        val target = impedanceRequest.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { impedanceRequest = -1 },
            title = { Text("确认阻抗测量") },
            text = {
                Text(
                    "阻抗测试会向目标电极注入 ADS1299 的约 6 nA 交流测试电流。\n\n" +
                        "人体连接电极时，请确保采集板使用电池或合规隔离供电，且不要通过非隔离 USB 与接市电的电脑相连。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    controller.startImpedance(target)
                    impedanceRequest = -1
                }) { Text(if (target == null) "继续测量全部" else "继续测量 CH $target") }
            },
            dismissButton = {
                TextButton(onClick = { impedanceRequest = -1 }) { Text("取消") }
            },
        )
    }
}

/** 未连接时的极简引导:一个主按钮 + 一句话。 */
@Composable
private fun ConnectHero(
    snapshot: MonitorSnapshot,
    busy: Boolean,
    scanning: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                0f to PlanetPalette.backgroundTop.copy(alpha = 0.55f),
                0.55f to PlanetPalette.backgroundMid,
                1f to Ink,
            ),
        ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(
                        Brush.radialGradient(
                            0f to Cyan.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onScan,
                    enabled = !busy,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan,
                        contentColor = Color(0xFF021C18),
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
                                color = Color(0xFF021C18),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Text(
                            if (scanning) "扫描中" else if (busy) "连接中" else "扫描",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = if (scanning) "正在寻找附近的脑电设备…" else "扫描并连接你的脑电采集板",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "支持 RT_BLE_AT 与 FFE0 透传模块(如 I6328A/I6329A)。\n连接后即可全屏查看实时脑电波形。",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 已连接后的全屏沉浸脑电:单个 Canvas 画全部通道,零按钮打扰。 */
@Composable
private fun ImmersiveEeg(
    snapshot: MonitorSnapshot,
    selectedChannels: List<Int>,
    demean: Boolean,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analysisByChannel = remember(snapshot.analyses) { snapshot.analyses.associateBy { it.channel } }
    Box(modifier = modifier.background(Ink)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to PlanetPalette.backgroundTop.copy(alpha = 0.28f),
                    0.5f to Color.Transparent,
                    1f to PlanetPalette.backgroundMid.copy(alpha = 0.35f),
                ),
            )
        }
        if (snapshot.activeView == MonitorView.IMPEDANCE) {
            ImmersiveImpedance(
                snapshot = snapshot,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (snapshot.selectedModuleType == ProcessingModuleType.EEG_TO_FEATURES) {
            FeatureMatrix(
                snapshot = snapshot,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ImmersiveSignalCanvas(
                snapshot = snapshot,
                analysisByChannel = analysisByChannel,
                selectedChannels = selectedChannels,
                demean = demean,
                activeView = snapshot.activeView,
                modifier = Modifier.fillMaxSize().padding(bottom = 44.dp),
            )
        }

        // 极简状态行:设备名 + 采样率 + 丢包,半透明置于顶部,不打扰沉浸。
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .background(Color(0x66102028), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = "已连接 ${snapshot.connection.deviceName ?: "设备"}，采样率 ${metricsText(snapshot)}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(8.dp).background(Cyan, CircleShape))
            Text(
                text = snapshot.connection.deviceName ?: "RT-BCI",
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            SnapshotMetric(text = metricsText(snapshot), color = TextMuted)
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = "断开设备",
                tint = TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDisconnect() }
                    .semantics { contentDescription = "断开设备" },
            )
        }
    }
}

private fun metricsText(snapshot: MonitorSnapshot): String {
    val m = snapshot.metrics
    return if (m.frames == 0L) "等待数据" else {
        val rate = "%.1f Hz".format(m.effectiveSampleRateHz)
        val loss = "%.1f%%".format(m.packetLossRatio * 100)
        "$rate · 丢包 $loss"
    }
}

@Composable
private fun SnapshotMetric(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = NumericFontFeature),
        maxLines = 1,
    )
}

/** 单画布多通道信号显示:每条通道一根线,垂直分布,一屏看全部。 */
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
            // 通道分隔线(极淡)
            if (laneIndex > 0) {
                drawLine(
                    color = Grid.copy(alpha = 0.35f),
                    start = Offset(0f, laneIndex * laneHeight),
                    end = Offset(size.width, laneIndex * laneHeight),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            // 通道标签
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
                                color = color,
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
    samples: List<io.github.vexpaer.brainexporter.sdk.SignalSample>,
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
        this.alpha = 200
    }
    drawContext.canvas.nativeCanvas.drawText(text, 10.dp.toPx(), yCenter + 3.5.dp.toPx(), paint)
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
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = FeaturePurple.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp),
                )
                Text("开始 EEG 采集后，这里会显示模块输出的特征值。", color = TextMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 72.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
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
    val color = series.channel?.let { ChannelColors.getOrNull(it - 1) } ?: FeaturePurple
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = series.channel?.let { "CH $it · ${series.label}" } ?: series.label,
            color = TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = latest?.let { formatFeatureValue(it) } ?: "—",
            color = color,
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = NumericFontFeature),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = series.unit,
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        FeatureHistoryChart(series.values, color, Modifier.fillMaxWidth())
    }
}

/** 阻抗模式:全屏进度 + 大号读数,控制留在面板。 */
@Composable
private fun ImmersiveImpedance(
    snapshot: MonitorSnapshot,
    modifier: Modifier = Modifier,
) {
    val impedance = snapshot.impedance
    Box(modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("ADS1299 交流阻抗估算", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            if (impedance.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(54.dp),
                    color = Cyan,
                    strokeWidth = 4.dp,
                )
                Text(
                    "CH ${impedance.channel ?: "—"} · ${(impedance.progress * 100).toInt()}%",
                    color = Cyan,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = NumericFontFeature),
                    fontWeight = FontWeight.Bold,
                )
            } else {
                val lastGood = impedance.results.withIndex()
                    .filter { it.value != null }
                    .maxByOrNull { it.index }
                if (lastGood != null) {
                    val result = lastGood.value!!
                    val qualityColor = when (result.quality) {
                        ImpedanceQuality.GOOD -> Cyan
                        ImpedanceQuality.WARNING -> Amber
                        ImpedanceQuality.BAD -> Danger
                    }
                    Text(
                        "CH ${lastGood.index + 1}",
                        color = TextMuted,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatImpedance(result.kiloOhms),
                        color = qualityColor,
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = NumericFontFeature),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = qualityLabel(result.quality),
                        color = qualityColor,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Text("尚未测量 — 在控制面板中选择通道开始", color = TextMuted, textAlign = TextAlign.Center)
                }
            }
            impedance.error?.let { Text(it, color = Danger, textAlign = TextAlign.Center) }
        }
    }
}

private fun qualityLabel(quality: ImpedanceQuality): String = when (quality) {
    ImpedanceQuality.GOOD -> "良好（< 750 kΩ）"
    ImpedanceQuality.WARNING -> "偏高（750–2500 kΩ）"
    ImpedanceQuality.BAD -> "较差（> 2500 kΩ）"
}

/** 底部控制面板:所有复杂控件收进来,按功能分组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(
    snapshot: MonitorSnapshot,
    controller: MonitorController,
    permissionGate: PermissionGate,
    recordingPermissionGate: RecordingPermissionGate,
    connected: Boolean,
    selectedChannels: MutableList<Int>,
    demean: Boolean,
    onDemeanChange: (Boolean) -> Unit,
    impedanceRequest: Int,
    onImpedanceRequest: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text("监测控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // 1. 设备
        item { SectionTitle("设备") }
        item {
            if (connected) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(snapshot.connection.deviceName ?: "RT-BCI", fontWeight = FontWeight.SemiBold)
                        snapshot.connection.profileName?.let {
                            Text(it, color = Blue, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = controller::disconnect) { Text("断开") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { permissionGate(controller::scan) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF021C18)),
                    ) { Text("扫描并连接") }
                    if (snapshot.devices.isNotEmpty()) {
                        Text("选择可连接设备", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                        snapshot.devices.take(8).forEach { device ->
                            DeviceRow(device = device, onConnect = { controller.connect(device.id) })
                        }
                    } else {
                        Text(
                            "点击扫描，选择 RT_BLE_AT；也会识别使用 FFE0 透传服务的 I6328A/I6329A 模块。",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // 2. 采集
        item { SectionTitle("数据采集") }
        item {
            val acquisition = snapshot.acquisition
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(acquisition.message, color = if (acquisition.active) Cyan else TextMuted)
                        if (acquisition.active) {
                            Text(
                                "已写入 ${acquisition.samplesWritten} 个采样点",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = NumericFontFeature),
                            )
                        }
                    }
                    Box(Modifier.size(10.dp).background(if (acquisition.active) Cyan else TextMuted, CircleShape))
                }
                acquisition.fileLocation?.let {
                    Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                acquisition.error?.let { Text(it, color = Danger) }
                if (acquisition.active) {
                    Button(
                        onClick = controller::stopAcquisition,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("停止采集") }
                } else {
                    Button(
                        onClick = { recordingPermissionGate(controller::startAcquisition) },
                        enabled = connected && !snapshot.impedance.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("开始采集") }
                }
                Text(
                    "CSV 保存到手机 Documents/eegData；采集数据只保存在本机。",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // 3. 分析视图
        item { SectionTitle("分析视图") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            listOf(
                                MonitorView.TIME to "电压/时间",
                                MonitorView.PSD to "5 秒 PSD",
                                MonitorView.SPECTRUM to "实时频谱",
                                MonitorView.BANDS to "5 秒波段",
                                MonitorView.IMPEDANCE to "阻抗测量",
                            ),
                        ) { (view, label) ->
                            FilterChip(
                                selected = snapshot.activeView == view,
                                onClick = { controller.setView(view) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (snapshot.activeView == MonitorView.TIME) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("时域去直流", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                            Switch(checked = demean, onCheckedChange = onDemeanChange)
                        }
                    } else {
                        Text(viewHint(snapshot.activeView), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 4. 数据源(模块)
        if (snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES) {
            item { SectionTitle("监测数据源") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = snapshot.selectedModuleId == null,
                                onClick = { controller.selectModule(null) },
                                label = { Text("原始脑电") },
                            )
                        }
                        items(snapshot.modules.filter { it.enabled }, key = { it.descriptor.id }) { module ->
                            FilterChip(
                                selected = snapshot.selectedModuleId == module.descriptor.id,
                                onClick = { controller.selectModule(module.descriptor.id) },
                                label = { Text(module.descriptor.displayName) },
                                leadingIcon = {
                                    Box(
                                        Modifier.size(7.dp).background(
                                            if (module.descriptor.type == ProcessingModuleType.EEG_TO_EEG) Cyan else FeaturePurple,
                                            CircleShape,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    if (snapshot.modules.none { it.enabled }) {
                        Text("可在“模块”页把处理模块添加到监测。", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 5. 通道
        if (snapshot.selectedModuleType != ProcessingModuleType.EEG_TO_FEATURES) {
            item { SectionTitle("通道") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items((1..8).toList()) { channel ->
                        FilterChip(
                            selected = channel in selectedChannels,
                            onClick = {
                                if (channel in selectedChannels) selectedChannels.remove(channel)
                                else {
                                    selectedChannels.add(channel)
                                    selectedChannels.sort()
                                }
                            },
                            label = { Text("CH $channel") },
                            leadingIcon = {
                                Box(Modifier.size(7.dp).background(ChannelColors[channel - 1], CircleShape))
                            },
                        )
                    }
                    item {
                        TextButton(onClick = {
                            selectedChannels.clear()
                            selectedChannels.addAll(1..8)
                        }) { Text("全选") }
                    }
                    item { TextButton(onClick = { selectedChannels.clear() }) { Text("清空") } }
                }
            }
        }

        // 6. 阻抗详表
        if (snapshot.activeView == MonitorView.IMPEDANCE) {
            item { SectionTitle("阻抗详表") }
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

        // 7. 链路指标
        item { SectionTitle("链路指标") }
        item { MetricsStrip(snapshot) }
        item {
            Text(
                "PSD/频谱使用 Hann 窗；丢包处线性插值并显示覆盖率。结果用于原型研发，不作为医疗诊断。",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextMuted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DeviceRow(device: DeviceDescriptor, onConnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(ControlRadius + 2.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(9.dp).background(if (device.recommended) Cyan else Blue, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${device.id} · ${device.rssi} dBm${if (device.recommended) " · 推荐" else ""}",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Button(
            onClick = onConnect,
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
        ) { Text("连接") }
    }
}

@Composable
private fun MetricsStrip(snapshot: MonitorSnapshot) {
    val metrics = snapshot.metrics
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("有效采样率", if (metrics.frames == 0L) "—" else "%.1f Hz".format(metrics.effectiveSampleRateHz), "目标 250 Hz") }
        item { MetricCard("估算丢包率", if (metrics.frames == 0L) "—" else "%.1f%%".format(metrics.packetLossRatio * 100), "缺失 ${metrics.missingPackets}") }
        item { MetricCard("有效数据帧", "${metrics.frames}", formatBytes(metrics.receivedBytes)) }
        item { MetricCard("链路", snapshot.connection.profileName ?: "—", snapshot.connection.deviceName ?: "等待连接") }
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String) {
    Card(
        modifier = Modifier.width(164.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(CardRadius),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = NumericFontFeature),
            )
            Text(detail, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ImpedanceToolbar(
    snapshot: MonitorSnapshot,
    onMeasureAll: () -> Unit,
    onStop: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ADS1299 交流阻抗估算", fontWeight = FontWeight.Bold)
            Text("6 nA · 约 31.2 Hz · 5 秒/通道 · 全部约需 40 秒", color = TextMuted)
            if (snapshot.impedance.running) {
                Text(
                    "CH ${snapshot.impedance.channel ?: "—"} · ${(snapshot.impedance.progress * 100).toInt()}%",
                    color = Cyan,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = NumericFontFeature),
                )
            }
            snapshot.impedance.error?.let { Text(it, color = Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onMeasureAll,
                    enabled = snapshot.connection.phase == ConnectionPhase.CONNECTED &&
                        !snapshot.impedance.running && !snapshot.acquisition.active,
                ) { Text("测量全部通道") }
                OutlinedButton(onClick = onStop, enabled = snapshot.impedance.running) { Text("停止") }
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
    val qualityColor = when (result?.quality) {
        ImpedanceQuality.GOOD -> Cyan
        ImpedanceQuality.WARNING -> Amber
        ImpedanceQuality.BAD -> Danger
        null -> TextMuted
    }
    val quality = when (result?.quality) {
        ImpedanceQuality.GOOD -> "良好（< 750 kΩ）"
        ImpedanceQuality.WARNING -> "偏高（750–2500 kΩ）"
        ImpedanceQuality.BAD -> "较差（> 2500 kΩ）"
        null -> "尚未测量"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(CardRadius),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(10.dp).background(ChannelColors[channel - 1], CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CH $channel", fontWeight = FontWeight.Bold)
                Text(
                    result?.let { formatImpedance(it.kiloOhms) } ?: "—",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = NumericFontFeature),
                )
                Text(if (measuring) "正在测量…" else quality, color = if (measuring) Blue else qualityColor)
                result?.let {
                    Text("σ ${"%.2f".format(it.standardDeviationUv)} µV · ${it.sampleCount} 点", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(
                onClick = onMeasure,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = ChannelColors[channel - 1]),
            ) { Text("测量", color = OnChannelDark) }
        }
    }
}

private fun viewHint(view: MonitorView): String = when (view) {
    MonitorView.TIME -> "滚动窗口 5 s"
    MonitorView.PSD -> "Hann · 最近 5 s · 0–60 Hz"
    MonitorView.SPECTRUM -> "FFT 幅值 · 约 2 s · 0–60 Hz"
    MonitorView.BANDS -> "相对功率 · 最近 5 s"
    MonitorView.IMPEDANCE -> "6 nA · 31.2 Hz · 5 s/通道"
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