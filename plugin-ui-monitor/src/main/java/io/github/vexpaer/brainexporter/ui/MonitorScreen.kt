package io.github.vexpaer.brainexporter.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.DeviceDescriptor
import io.github.vexpaer.brainexporter.sdk.ImpedanceQuality
import io.github.vexpaer.brainexporter.sdk.ImpedanceResult
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import io.github.vexpaer.brainexporter.sdk.MonitorView

@Composable
internal fun DevicePanel(
    snapshot: MonitorSnapshot,
    permissionGate: PermissionGate,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = snapshot.connection.phase == ConnectionPhase.CONNECTED
    val busy = snapshot.connection.phase == ConnectionPhase.CONNECTING
    val scanning = snapshot.connection.phase == ConnectionPhase.SCANNING
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("脑电设备", fontWeight = FontWeight.SemiBold)
                    Text(
                        snapshot.connection.message,
                        color = when (snapshot.connection.phase) {
                            ConnectionPhase.ERROR -> Danger
                            ConnectionPhase.CONNECTED -> Cyan
                            else -> TextMuted
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (busy || scanning) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                if (connected) {
                    OutlinedButton(onClick = onDisconnect) { Text("断开") }
                } else {
                    Button(
                        onClick = { permissionGate(onScan) },
                        enabled = !busy,
                    ) { Text(if (scanning) "扫描中" else "扫描设备") }
                }
            }

            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusTag(snapshot.connection.deviceName ?: "RT-BCI", Cyan)
                    snapshot.connection.profileName?.let { StatusTag(it, Blue) }
                }
                Text(
                    "连接成功后可手动开始/停止采集；采集数据只保存在本机。",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else if (snapshot.devices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择可连接设备", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    snapshot.devices.take(8).forEach { device ->
                        DeviceRow(device = device, onConnect = { onConnect(device.id) })
                    }
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

@Composable
private fun DeviceRow(device: DeviceDescriptor, onConnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelSoft, RoundedCornerShape(12.dp)).padding(12.dp),
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
private fun StatusTag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(9.dp, 5.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun MonitorScreen(
    snapshot: MonitorSnapshot,
    controller: MonitorController,
    permissionGate: PermissionGate,
    recordingPermissionGate: RecordingPermissionGate,
    modifier: Modifier = Modifier,
) {
    var demean by rememberSaveable { mutableStateOf(true) }
    val selectedChannels = remember { mutableStateListOf<Int>().apply { addAll(1..8) } }
    var impedanceRequest by remember { mutableIntStateOf(-1) }
    val analysisByChannel = remember(snapshot.analyses) { snapshot.analyses.associateBy { it.channel } }

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DevicePanel(
                snapshot = snapshot,
                permissionGate = permissionGate,
                onScan = controller::scan,
                onConnect = controller::connect,
                onDisconnect = controller::disconnect,
            )
        }
        if (snapshot.connection.phase == ConnectionPhase.CONNECTED || snapshot.acquisition.fileLocation != null) {
            item {
                AcquisitionPanel(
                    snapshot = snapshot,
                    onStart = { recordingPermissionGate(controller::startAcquisition) },
                    onStop = controller::stopAcquisition,
                )
            }
        }
        item { MetricsStrip(snapshot) }
        item { ViewSelector(snapshot.activeView, controller::setView) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("通道", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (snapshot.activeView == MonitorView.TIME) {
                        Text("时域去直流", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                        Switch(checked = demean, onCheckedChange = { demean = it })
                    } else {
                        Text(viewHint(snapshot.activeView), color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
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

        if (snapshot.activeView == MonitorView.IMPEDANCE) {
            item {
                ImpedanceToolbar(
                    snapshot = snapshot,
                    onMeasureAll = { impedanceRequest = 0 },
                    onStop = controller::stopImpedance,
                )
            }
            items(selectedChannels.toList(), key = { "impedance-$it" }) { channel ->
                ImpedanceCard(
                    channel = channel,
                    result = snapshot.impedance.results.getOrNull(channel - 1),
                    measuring = snapshot.impedance.running && snapshot.impedance.channel == channel,
                    enabled = snapshot.connection.phase == ConnectionPhase.CONNECTED &&
                        !snapshot.impedance.running && !snapshot.acquisition.active,
                    onMeasure = { impedanceRequest = channel },
                )
            }
        } else {
            items(selectedChannels.toList(), key = { "${snapshot.activeView}-$it" }) { channel ->
                SignalCard(
                    channel = channel,
                    snapshot = snapshot,
                    analysis = analysisByChannel[channel],
                    demean = demean,
                )
            }
        }

        if (selectedChannels.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Text("请选择至少一个通道。", color = TextMuted, modifier = Modifier.padding(24.dp))
                }
            }
        }
        item {
            Text(
                "PSD/频谱使用 Hann 窗；丢包处线性插值并显示覆盖率。结果用于原型研发，不作为医疗诊断。",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(4.dp, 8.dp, 4.dp, 20.dp),
            )
        }
    }
}

@Composable
private fun AcquisitionPanel(
    snapshot: MonitorSnapshot,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val acquisition = snapshot.acquisition
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("EEG 数据采集", fontWeight = FontWeight.Bold)
                    Text(acquisition.message, color = if (acquisition.active) Cyan else TextMuted)
                }
                Box(
                    Modifier.size(10.dp).background(if (acquisition.active) Cyan else TextMuted, CircleShape),
                )
            }
            acquisition.fileLocation?.let {
                Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (acquisition.active) {
                Text("已写入 ${acquisition.samplesWritten} 个采样点", color = TextMuted)
            }
            acquisition.error?.let { Text(it, color = Danger) }
            if (acquisition.active) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("停止采集") }
            } else {
                Button(
                    onClick = onStart,
                    enabled = snapshot.connection.phase == ConnectionPhase.CONNECTED && !snapshot.impedance.running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开始采集") }
            }
            Text(
                "CSV 保存到手机 Documents/eegData；每行包含采样序号、包序号、时间戳和 8 通道 µV 数据。",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ViewSelector(active: MonitorView, onSelect: (MonitorView) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            listOf(
                MonitorView.TIME to "电压 / 时间",
                MonitorView.PSD to "5 秒 PSD",
                MonitorView.SPECTRUM to "实时频谱",
                MonitorView.BANDS to "5 秒脑电波段",
                MonitorView.IMPEDANCE to "阻抗测量",
            ),
        ) { (view, label) ->
            FilterChip(
                selected = active == view,
                onClick = { onSelect(view) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SignalCard(
    channel: Int,
    snapshot: MonitorSnapshot,
    analysis: ChannelAnalysis?,
    demean: Boolean,
) {
    val latestValue = snapshot.samples.lastOrNull()?.valuesUv?.getOrNull(channel - 1)
    val summary = when (snapshot.activeView) {
        MonitorView.TIME -> latestValue?.let { "当前 %.2f µV · 纵轴自动".format(it) } ?: "等待信号"
        else -> analysis?.summary ?: "正在积累分析窗口"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(ChannelColors[channel - 1], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("CH $channel", fontWeight = FontWeight.Bold)
                }
                Text(
                    summary,
                    color = if ((analysis?.coverage ?: 1.0) < 0.95) Amber else TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (snapshot.activeView) {
                MonitorView.TIME -> TimeSignalChart(snapshot.samples, channel, demean, Modifier.fillMaxWidth())
                MonitorView.PSD -> FrequencyChart(analysis?.line, channel, true, Modifier.fillMaxWidth())
                MonitorView.SPECTRUM -> FrequencyChart(analysis?.line, channel, false, Modifier.fillMaxWidth())
                MonitorView.BANDS -> BandPowerChart(analysis?.bars ?: emptyList(), Modifier.fillMaxWidth())
                MonitorView.IMPEDANCE -> Unit
            }
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
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(10.dp).background(ChannelColors[channel - 1], CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CH $channel", fontWeight = FontWeight.Bold)
                Text(result?.let { formatImpedance(it.kiloOhms) } ?: "—", style = MaterialTheme.typography.titleLarge)
                Text(if (measuring) "正在测量…" else quality, color = if (measuring) Blue else qualityColor)
                result?.let {
                    Text("σ ${"%.2f".format(it.standardDeviationUv)} µV · ${it.sampleCount} 点", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(
                onClick = onMeasure,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = ChannelColors[channel - 1]),
            ) { Text("测量", color = Color(0xFF06131F)) }
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
