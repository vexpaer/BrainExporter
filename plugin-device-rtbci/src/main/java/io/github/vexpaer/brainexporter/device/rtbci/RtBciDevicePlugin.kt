package io.github.vexpaer.brainexporter.device.rtbci

import android.content.Context
import io.github.vexpaer.brainexporter.ble.AndroidBleUartTransport
import io.github.vexpaer.brainexporter.ble.BlePeripheral
import io.github.vexpaer.brainexporter.ble.BleUartListener
import io.github.vexpaer.brainexporter.ble.UartGattProfile
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.ConnectionState
import io.github.vexpaer.brainexporter.sdk.DeviceCapability
import io.github.vexpaer.brainexporter.sdk.DeviceDescriptor
import io.github.vexpaer.brainexporter.sdk.DevicePlugin
import io.github.vexpaer.brainexporter.sdk.DevicePluginListener
import io.github.vexpaer.brainexporter.sdk.ImpedanceQuality
import io.github.vexpaer.brainexporter.sdk.ImpedanceResult
import io.github.vexpaer.brainexporter.sdk.ImpedanceState
import io.github.vexpaer.brainexporter.sdk.SignalSample
import io.github.vexpaer.brainexporter.sdk.StreamMetrics
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

class RtBciDevicePlugin(context: Context) : DevicePlugin, BleUartListener {
    override val id: String = "rt-bci-1299-ble"
    override val displayName: String = "RT-BCI ADS1299"
    override val capabilities: Set<DeviceCapability> =
        setOf(DeviceCapability.LIVE_SIGNAL, DeviceCapability.IMPEDANCE)

    private val transport = AndroidBleUartTransport(context)
    private val decoderExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rtbci-decoder").apply { isDaemon = true }
    }
    private val impedanceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rtbci-impedance").apply { isDaemon = true }
    }
    private val disconnectExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rtbci-disconnect").apply { isDaemon = true }
    }

    @Volatile
    private var listener: DevicePluginListener? = null
    @Volatile
    private var connected = false
    @Volatile
    private var streaming = false
    @Volatile
    private var selectedDeviceName: String? = null
    @Volatile
    private var selectedDeviceId: String? = null
    private var cachedDevices: List<DeviceDescriptor> = emptyList()

    private val decoder = RtBciFrameDecoder()
    private var statistics = PacketStatistics()
    private var timeline = SequenceTimeline()
    private var notificationCount = 0L
    private var receivedBytes = 0L
    private var firstFrameNanos = 0L
    private val channelGains = DoubleArray(8) { DEFAULT_GAIN }
    private val recentSamples = ArrayDeque<SignalSample>()
    private val recentSamplesLock = Any()
    @Volatile
    private var latestMetrics = StreamMetrics()

    private val impedanceCancelled = AtomicBoolean(false)
    private val impedanceLock = Any()
    private var impedanceFuture: Future<*>? = null
    @Volatile
    private var activeImpedanceChannel: Int? = null
    @Volatile
    private var impedanceState = ImpedanceState()

    init {
        transport.setListener(this)
    }

    override fun setListener(listener: DevicePluginListener?) {
        this.listener = listener
    }

    override fun scan(durationMillis: Long) {
        if (connected) {
            emitConnection(
                ConnectionState(
                    phase = ConnectionPhase.CONNECTED,
                    deviceName = selectedDeviceName,
                    deviceId = selectedDeviceId,
                    message = "请先断开当前设备再扫描。",
                ),
            )
            return
        }
        emitConnection(
            ConnectionState(
                phase = ConnectionPhase.SCANNING,
                message = "正在扫描附近的 BLE 设备…",
            ),
        )
        // Establish a deterministic idle state even if the board was streaming
        // before a previous link was interrupted.
        transport.write(byteArrayOf('s'.code.toByte()))
        transport.scan(durationMillis)
    }

    override fun connect(deviceId: String) {
        if (connected) return
        val descriptor = cachedDevices.firstOrNull { it.id.equals(deviceId, true) }
        selectedDeviceName = descriptor?.name ?: "RT-BCI"
        selectedDeviceId = deviceId
        resetStreamState()
        emitConnection(
            ConnectionState(
                phase = ConnectionPhase.CONNECTING,
                deviceName = selectedDeviceName,
                deviceId = deviceId,
                message = "正在连接并配置数据通知…",
            ),
        )
        transport.connect(deviceId, GATT_PROFILES)
    }

    override fun disconnect() {
        stopImpedance()
        if (!connected) {
            transport.disconnect()
            emitDisconnected(null)
            return
        }
        emitConnection(
            ConnectionState(
                phase = ConnectionPhase.CONNECTING,
                deviceName = selectedDeviceName,
                deviceId = selectedDeviceId,
                message = "正在停止采集并断开…",
            ),
        )
        stopStreaming()
        disconnectExecutor.execute {
            pause(100)
            transport.disconnect()
        }
    }

    override fun onScanResults(devices: List<BlePeripheral>) {
        val mapped = devices.mapNotNull { device ->
            val knownService = device.serviceUuids.any { it.lowercase() in KNOWN_SERVICES }
            val isUnnamed = device.name == "未命名 BLE 设备"
            if (isUnnamed && !knownService) return@mapNotNull null
            val recommended = device.name.equals("RT_BLE_AT", true) ||
                device.name.equals("XLBLE", true) || knownService
            DeviceDescriptor(
                id = device.address,
                name = if (isUnnamed) "未命名 RT-BCI 候选设备" else device.name,
                rssi = device.rssi,
                serviceUuids = device.serviceUuids,
                recommended = recommended,
            )
        }.sortedWith(
            compareByDescending<DeviceDescriptor> { it.recommended }
                .thenByDescending { it.rssi },
        )
        cachedDevices = mapped
        listener?.onDevicesChanged(mapped)
    }

    override fun onReady(profile: UartGattProfile) {
        connected = true
        streaming = false
        emitConnection(
            ConnectionState(
                phase = ConnectionPhase.CONNECTED,
                deviceName = selectedDeviceName,
                deviceId = selectedDeviceId,
                profileName = profile.label,
                message = "已连接，可以开始采集",
            ),
        )
    }

    override fun startStreaming() {
        check(connected) { "请先连接 RT-BCI 设备。" }
        if (streaming) return
        resetSignalState()
        streaming = true
        transport.write(byteArrayOf(0xF0.toByte(), 'b'.code.toByte()))
    }

    override fun stopStreaming() {
        if (!connected) return
        streaming = false
        transport.write(byteArrayOf('s'.code.toByte()))
    }

    override fun onNotification(data: ByteArray) {
        if (data.isEmpty() || !connected || (!streaming && !impedanceState.running)) return
        decoderExecutor.execute {
            receivedBytes += data.size
            notificationCount++
            decodeNotification(data)
        }
    }

    private fun decodeNotification(data: ByteArray) {
        val decoded = decoder.feed(data)
        if (decoded.isEmpty()) return
        val now = System.nanoTime()
        if (firstFrameNanos == 0L) firstFrameNanos = now
        val output = ArrayList<SignalSample>(decoded.size)
        for (frame in decoded) {
            statistics.accept(frame.packetId)
            val index = timeline.accept(frame.packetId)
            val values = DoubleArray(8) { channel ->
                val gain = synchronized(channelGains) { channelGains[channel] }
                countsToMicrovolts(frame.channelCounts[channel], gain)
            }
            output += SignalSample(
                index = index,
                packetId = frame.packetId,
                valuesUv = values,
                receivedAtNanos = now,
            )
        }
        synchronized(recentSamplesLock) {
            output.forEach(recentSamples::addLast)
            while (recentSamples.size > MAX_RECENT_SAMPLES) recentSamples.removeFirst()
        }
        val elapsed = (now - firstFrameNanos).coerceAtLeast(1L) / 1_000_000_000.0
        latestMetrics = StreamMetrics(
            frames = statistics.frames,
            missingPackets = statistics.missing,
            duplicatePackets = statistics.duplicates,
            sequenceResets = statistics.resets,
            receivedBytes = receivedBytes,
            notificationCount = notificationCount,
            discardedBytes = decoder.discardedBytes,
            effectiveSampleRateHz = if (elapsed >= 0.2) statistics.frames / elapsed else 0.0,
        )
        listener?.onSamples(output, latestMetrics)
    }

    override fun onDisconnected(reason: String?) {
        emitDisconnected(reason)
    }

    private fun emitDisconnected(reason: String?) {
        connected = false
        streaming = false
        stopImpedance()
        emitConnection(
            ConnectionState(
                phase = if (reason == null) ConnectionPhase.DISCONNECTED else ConnectionPhase.ERROR,
                deviceName = selectedDeviceName,
                deviceId = selectedDeviceId,
                message = reason ?: "已断开",
            ),
        )
    }

    override fun onError(message: String, cause: Throwable?) {
        emitConnection(
            ConnectionState(
                phase = if (connected) ConnectionPhase.CONNECTED else ConnectionPhase.ERROR,
                deviceName = selectedDeviceName,
                deviceId = selectedDeviceId,
                message = message,
            ),
        )
    }

    private fun emitConnection(state: ConnectionState) {
        listener?.onConnectionChanged(state)
    }

    private fun resetStreamState() {
        resetSignalState()
        synchronized(channelGains) { channelGains.fill(DEFAULT_GAIN) }
        activeImpedanceChannel = null
        impedanceState = ImpedanceState()
        listener?.onImpedanceChanged(impedanceState)
    }

    private fun resetSignalState() {
        decoder.reset()
        statistics = PacketStatistics()
        timeline = SequenceTimeline()
        notificationCount = 0
        receivedBytes = 0
        firstFrameNanos = 0
        latestMetrics = StreamMetrics()
        synchronized(recentSamplesLock) { recentSamples.clear() }
    }

    override fun startImpedance(channel: Int?, dwellSeconds: Double) {
        if (!connected) {
            val error = "请先连接 RT-BCI，再开始阻抗测量。"
            impedanceState = impedanceState.copy(error = error)
            listener?.onImpedanceChanged(impedanceState)
            return
        }
        if (channel != null && channel !in 1..8) return
        if (streaming) {
            val error = "请先停止 EEG 采集，再开始阻抗测量。"
            impedanceState = impedanceState.copy(error = error)
            listener?.onImpedanceChanged(impedanceState)
            return
        }
        val dwell = dwellSeconds.coerceIn(2.0, 10.0)
        synchronized(impedanceLock) {
            if (impedanceFuture?.isDone == false) return
            val targets = channel?.let(::listOf) ?: (1..8).toList()
            val results = impedanceState.results.toMutableList()
            targets.forEach { results[it - 1] = null }
            impedanceCancelled.set(false)
            impedanceState = ImpedanceState(
                running = true,
                dwellSeconds = dwell,
                results = results,
            )
            listener?.onImpedanceChanged(impedanceState)
            impedanceFuture = impedanceExecutor.submit {
                runImpedanceMeasurement(targets, dwell)
            }
        }
    }

    private fun runImpedanceMeasurement(targets: List<Int>, dwellSeconds: Double) {
        var error: String? = null
        try {
            targets.forEachIndexed { targetIndex, channel ->
                if (impedanceCancelled.get() || !connected) return@forEachIndexed
                applyImpedanceChannel(channel)
                val measurementStartIndex = synchronized(recentSamplesLock) {
                    recentSamples.lastOrNull()?.index ?: -1L
                }
                val startedAt = System.nanoTime()
                while (!impedanceCancelled.get() && connected) {
                    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0
                    updateImpedanceResult(channel, measurementStartIndex)
                    val progress = (targetIndex + (elapsed / dwellSeconds).coerceIn(0.0, 1.0)) /
                        targets.size
                    impedanceState = impedanceState.copy(
                        running = true,
                        channel = channel,
                        progress = progress,
                        error = null,
                    )
                    listener?.onImpedanceChanged(impedanceState)
                    if (elapsed >= dwellSeconds) break
                    pause(200)
                }
            }
        } catch (failure: RuntimeException) {
            error = failure.message ?: "阻抗测量失败。"
        } finally {
            runCatching { applyImpedanceChannel(null) }
            val completed = !impedanceCancelled.get() && error == null && connected
            impedanceState = impedanceState.copy(
                running = false,
                channel = null,
                progress = if (completed) 1.0 else impedanceState.progress,
                error = error,
            )
            listener?.onImpedanceChanged(impedanceState)
        }
    }

    private fun applyImpedanceChannel(channel: Int?) {
        val previous = activeImpedanceChannel
        if (previous == channel) return
        transport.write(byteArrayOf('s'.code.toByte()))
        if (previous != null) {
            transport.write(channelImpedancePayload(previous, false))
            if (channel != null) pause(150)
        }
        if (channel != null) transport.write(channelImpedancePayload(channel, true))
        synchronized(channelGains) {
            if (previous != null) channelGains[previous - 1] = DEFAULT_GAIN
            if (channel != null) channelGains[channel - 1] = 1.0
        }
        activeImpedanceChannel = channel
        pause(180)
        if (connected && (channel != null || streaming)) {
            transport.write(byteArrayOf('b'.code.toByte()))
        }
    }

    private fun updateImpedanceResult(channel: Int, measurementStartIndex: Long) {
        val values = synchronized(recentSamplesLock) {
            val latest = recentSamples.lastOrNull()?.index ?: return
            val earliest = latest - SAMPLE_RATE_HZ.toLong()
            recentSamples.asSequence()
                .filter { it.index > measurementStartIndex && it.index >= earliest }
                .map { it.valuesUv[channel - 1] }
                .toList()
        }
        if (values.size < 20) return
        val mean = values.average()
        val variance = values.sumOf { value -> (value - mean) * (value - mean) } / values.size
        val standardDeviationUv = sqrt(max(variance, 0.0))
        val ohms = max(0.0, sqrt(2.0) * standardDeviationUv * 1e-6 / 6e-9)
        val kiloOhms = ohms / 1_000.0
        val quality = when {
            kiloOhms < 750.0 -> ImpedanceQuality.GOOD
            kiloOhms < 2_500.0 -> ImpedanceQuality.WARNING
            else -> ImpedanceQuality.BAD
        }
        val result = ImpedanceResult(
            channel = channel,
            kiloOhms = kiloOhms,
            standardDeviationUv = standardDeviationUv,
            sampleCount = values.size,
            quality = quality,
        )
        val results = impedanceState.results.toMutableList()
        results[channel - 1] = result
        impedanceState = impedanceState.copy(results = results)
    }

    override fun stopImpedance() {
        impedanceCancelled.set(true)
        synchronized(impedanceLock) {
            impedanceFuture?.cancel(true)
        }
    }

    private fun pause(milliseconds: Long) {
        try {
            Thread.sleep(milliseconds)
        } catch (_: InterruptedException) {
            // Cancellation is observed by impedanceCancelled; clearing the interrupt
            // lets the finally block restore ADS1299 channel settings safely.
        }
    }

    override fun close() {
        stopImpedance()
        stopStreaming()
        transport.close()
        decoderExecutor.shutdownNow()
        impedanceExecutor.shutdownNow()
        disconnectExecutor.shutdownNow()
        listener = null
    }

    private companion object {
        private const val UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"
        private const val MAX_RECENT_SAMPLES = 5_000

        private fun uuid(short: String): UUID =
            UUID.fromString("0000${short.lowercase()}$UUID_SUFFIX")

        val GATT_PROFILES = listOf(
            UartGattProfile(
                label = "RT_BLE_AT 定制透传",
                serviceUuid = uuid("fff0"),
                writeUuid = uuid("fff3"),
                notifyUuid = uuid("fff4"),
            ),
            UartGattProfile(
                label = "I6328A / I6329A 原厂透传",
                serviceUuid = uuid("ffe0"),
                writeUuid = uuid("ffe1"),
                notifyUuid = uuid("ffe2"),
            ),
        )
        val KNOWN_SERVICES = GATT_PROFILES.map { it.serviceUuid.toString().lowercase() }.toSet()
    }
}
