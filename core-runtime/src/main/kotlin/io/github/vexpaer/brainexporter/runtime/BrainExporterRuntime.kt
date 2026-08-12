package io.github.vexpaer.brainexporter.runtime

import io.github.vexpaer.brainexporter.sdk.AcquisitionState
import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.ConnectionState
import io.github.vexpaer.brainexporter.sdk.DeviceDescriptor
import io.github.vexpaer.brainexporter.sdk.DevicePlugin
import io.github.vexpaer.brainexporter.sdk.DevicePluginListener
import io.github.vexpaer.brainexporter.sdk.EegRecordingSink
import io.github.vexpaer.brainexporter.sdk.ImpedanceState
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.SignalAlgorithm
import io.github.vexpaer.brainexporter.sdk.SignalSample
import io.github.vexpaer.brainexporter.sdk.SnapshotListener
import io.github.vexpaer.brainexporter.sdk.StreamMetrics
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Hardware-neutral session runtime. It only knows the public plug-in contracts;
 * replacing RT-BCI with another headset does not require changing the UI.
 */
class BrainExporterRuntime(
    private val device: DevicePlugin,
    private val algorithm: SignalAlgorithm,
    private val recordingSink: EegRecordingSink? = null,
) : MonitorController, DevicePluginListener {
    private val stateLock = Any()
    private val listeners = CopyOnWriteArraySet<SnapshotListener>()
    private val samples = ArrayDeque<SignalSample>()
    private val scheduler = Executors.newScheduledThreadPool(2) { runnable ->
        Thread(runnable, "brainexporter-runtime").apply { isDaemon = true }
    }

    private var devices: List<DeviceDescriptor> = emptyList()
    private var connection = ConnectionState()
    private var metrics = StreamMetrics()
    private var impedance = ImpedanceState()
    private var analyses: List<ChannelAnalysis> = emptyList()
    private var activeView = MonitorView.TIME
    private var acquisition = AcquisitionState()

    @Volatile
    private var lastSnapshot = MonitorSnapshot(capabilities = device.capabilities)

    init {
        device.setListener(this)
        scheduler.scheduleAtFixedRate(
            { publish() },
            80,
            80,
            TimeUnit.MILLISECONDS,
        )
        scheduler.scheduleAtFixedRate(
            { refreshAnalysis() },
            250,
            360,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun snapshot(): MonitorSnapshot = lastSnapshot

    override fun addListener(listener: SnapshotListener): AutoCloseable {
        listeners += listener
        listener.onSnapshot(lastSnapshot)
        return AutoCloseable { listeners -= listener }
    }

    override fun setView(view: MonitorView) {
        synchronized(stateLock) {
            if (activeView == view) return
            activeView = view
            analyses = emptyList()
        }
        publish()
        if (view != MonitorView.TIME && view != MonitorView.IMPEDANCE) {
            scheduler.execute(::refreshAnalysis)
        }
    }

    override fun scan() = runDeviceOperation("无法开始扫描") { device.scan() }

    override fun connect(deviceId: String) =
        runDeviceOperation("无法连接设备") { device.connect(deviceId) }

    override fun disconnect() {
        if (synchronized(stateLock) { acquisition.active }) stopAcquisition()
        runDeviceOperation("无法断开设备") { device.disconnect() }
    }

    override fun startAcquisition() {
        val currentConnection = synchronized(stateLock) { connection }
        if (currentConnection.phase != ConnectionPhase.CONNECTED) {
            synchronized(stateLock) {
                acquisition = acquisition.copy(error = "请先连接脑电设备。", message = "无法开始采集")
            }
            publish()
            return
        }
        if (synchronized(stateLock) { acquisition.active }) return
        val sink = recordingSink
        if (sink == null) {
            synchronized(stateLock) {
                acquisition = acquisition.copy(error = "当前主机未提供 EEG 文件存储。", message = "无法开始采集")
            }
            publish()
            return
        }
        try {
            val location = sink.start(currentConnection.deviceName)
            synchronized(stateLock) {
                samples.clear()
                analyses = emptyList()
                metrics = StreamMetrics()
                acquisition = AcquisitionState(
                    active = true,
                    startedAtEpochMillis = System.currentTimeMillis(),
                    fileLocation = location,
                    message = "正在采集并保存到 $location",
                )
            }
            device.startStreaming()
            publish()
        } catch (failure: RuntimeException) {
            runCatching { sink.stop() }
            synchronized(stateLock) {
                acquisition = AcquisitionState(
                    message = "无法开始采集",
                    error = failure.message ?: "创建 EEG 文件失败。",
                )
            }
            publish()
        }
    }

    override fun stopAcquisition() {
        val wasActive = synchronized(stateLock) { acquisition.active }
        if (!wasActive) return
        var failureMessage: String? = null
        runCatching { device.stopStreaming() }
            .onFailure { failureMessage = it.message ?: "停止设备数据流失败。" }
        val finalLocation = runCatching { recordingSink?.stop() }
            .onFailure { failureMessage = it.message ?: "关闭 EEG 文件失败。" }
            .getOrNull()
        synchronized(stateLock) {
            acquisition = acquisition.copy(
                active = false,
                fileLocation = finalLocation ?: acquisition.fileLocation,
                message = if (failureMessage == null) "采集已停止，文件已保存" else "采集已停止，但保存存在问题",
                error = failureMessage,
            )
        }
        publish()
    }

    override fun startImpedance(channel: Int?) =
        runDeviceOperation("无法开始阻抗测量") { device.startImpedance(channel) }

    override fun stopImpedance() =
        runDeviceOperation("无法停止阻抗测量") { device.stopImpedance() }

    private inline fun runDeviceOperation(prefix: String, operation: () -> Unit) {
        try {
            operation()
        } catch (failure: RuntimeException) {
            synchronized(stateLock) {
                connection = connection.copy(
                    phase = if (connection.phase == ConnectionPhase.CONNECTED) {
                        ConnectionPhase.CONNECTED
                    } else {
                        ConnectionPhase.ERROR
                    },
                    message = "$prefix：${failure.message ?: "未知错误"}",
                )
            }
            publish()
        }
    }

    override fun onDevicesChanged(devices: List<DeviceDescriptor>) {
        synchronized(stateLock) { this.devices = devices }
        publish()
    }

    override fun onConnectionChanged(state: ConnectionState) {
        val shouldCloseRecording = synchronized(stateLock) {
            connection = state
            acquisition.active && state.phase != ConnectionPhase.CONNECTED
        }
        if (shouldCloseRecording) {
            val finalLocation = runCatching { recordingSink?.stop() }.getOrNull()
            synchronized(stateLock) {
                acquisition = acquisition.copy(
                    active = false,
                    fileLocation = finalLocation ?: acquisition.fileLocation,
                    message = "设备连接中断，采集文件已关闭",
                )
            }
        }
        publish()
    }

    override fun onSamples(samples: List<SignalSample>, metrics: StreamMetrics) {
        val shouldRecord = synchronized(stateLock) { acquisition.active }
        var recordingError: String? = null
        if (shouldRecord) {
            runCatching { recordingSink?.append(samples) }
                .onFailure { recordingError = it.message ?: "写入 EEG 文件失败。" }
        }
        synchronized(stateLock) {
            samples.forEach(this.samples::addLast)
            while (this.samples.size > MAX_BUFFERED_SAMPLES) this.samples.removeFirst()
            this.metrics = metrics
            if (shouldRecord) {
                acquisition = acquisition.copy(
                    samplesWritten = acquisition.samplesWritten + samples.size,
                    error = recordingError ?: acquisition.error,
                )
            }
        }
        if (recordingError != null) scheduler.execute(::stopAcquisition)
    }

    override fun onImpedanceChanged(state: ImpedanceState) {
        synchronized(stateLock) { impedance = state }
        publish()
    }

    private fun refreshAnalysis() {
        val view: MonitorView
        val signal: List<SignalSample>
        synchronized(stateLock) {
            view = activeView
            if (view == MonitorView.TIME || view == MonitorView.IMPEDANCE) return
            signal = samples.toList()
        }
        val calculated = try {
            algorithm.analyze(signal, view, SAMPLE_RATE_HZ)
        } catch (_: RuntimeException) {
            emptyList()
        }
        synchronized(stateLock) {
            if (activeView == view) analyses = calculated
        }
        publish()
    }

    private fun publish() {
        val snapshot = synchronized(stateLock) {
            val visibleSamples = if (samples.isEmpty()) {
                emptyList()
            } else {
                val earliest = samples.peekLast().index - VISIBLE_SAMPLE_SPAN
                samples.asSequence().filter { it.index >= earliest }.toList()
            }
            MonitorSnapshot(
                devices = devices,
                connection = connection,
                metrics = metrics,
                samples = visibleSamples,
                analyses = analyses,
                impedance = impedance,
                acquisition = acquisition,
                activeView = activeView,
                capabilities = device.capabilities,
            )
        }
        lastSnapshot = snapshot
        listeners.forEach { listener ->
            runCatching { listener.onSnapshot(snapshot) }
        }
    }

    override fun close() {
        if (synchronized(stateLock) { acquisition.active }) stopAcquisition()
        device.setListener(null)
        device.close()
        runCatching { recordingSink?.close() }
        scheduler.shutdownNow()
        listeners.clear()
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 250.0
        const val MAX_BUFFERED_SAMPLES = 5_000
        const val VISIBLE_SAMPLE_SPAN = 1_500L
    }
}
