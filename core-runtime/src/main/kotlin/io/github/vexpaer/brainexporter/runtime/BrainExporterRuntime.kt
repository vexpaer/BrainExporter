package io.github.vexpaer.brainexporter.runtime

import io.github.vexpaer.brainexporter.sdk.AcquisitionState
import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.ConnectionState
import io.github.vexpaer.brainexporter.sdk.DeviceDescriptor
import io.github.vexpaer.brainexporter.sdk.DevicePlugin
import io.github.vexpaer.brainexporter.sdk.DevicePluginListener
import io.github.vexpaer.brainexporter.sdk.EegProcessingModule
import io.github.vexpaer.brainexporter.sdk.EegRecordingSink
import io.github.vexpaer.brainexporter.sdk.EegSignalModuleOutput
import io.github.vexpaer.brainexporter.sdk.FeatureModuleOutput
import io.github.vexpaer.brainexporter.sdk.ImpedanceState
import io.github.vexpaer.brainexporter.sdk.ModuleFeatureSeries
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleDescriptor
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOrigin
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOutput
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleState
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleType
import io.github.vexpaer.brainexporter.sdk.SignalAlgorithm
import io.github.vexpaer.brainexporter.sdk.SignalSample
import io.github.vexpaer.brainexporter.sdk.SnapshotListener
import io.github.vexpaer.brainexporter.sdk.StreamMetrics
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Hardware-neutral session runtime. Device, analysis and streaming processing modules only meet
 * through the public SDK contracts, so an imported module never needs access to BLE or Android UI.
 */
class BrainExporterRuntime(
    private val device: DevicePlugin,
    private val algorithm: SignalAlgorithm,
    private val recordingSink: EegRecordingSink? = null,
    modules: List<EegProcessingModule> = emptyList(),
) : MonitorController, DevicePluginListener {
    private val stateLock = Any()
    private val listeners = CopyOnWriteArraySet<SnapshotListener>()
    private val samples = ArrayDeque<SignalSample>()
    private val moduleSlots = linkedMapOf<String, ModuleSlot>()
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
    private var selectedModuleId: String? = null

    @Volatile
    private var lastSnapshot = MonitorSnapshot(capabilities = device.capabilities)

    init {
        synchronized(stateLock) {
            modules.forEach(::registerModuleLocked)
        }
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
        publish()
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

    override fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        synchronized(stateLock) {
            val slot = moduleSlots[moduleId] ?: return
            if (slot.enabled == enabled) return
            slot.enabled = enabled
            slot.clearOutput()
            runCatching { slot.module.reset() }
                .onFailure { slot.error = it.message ?: "模块重置失败" }
            slot.message = if (enabled) "已添加到监测，等待脑电数据" else "尚未添加到监测"
            if (!enabled && selectedModuleId == moduleId) selectedModuleId = null
            analyses = emptyList()
        }
        publish()
        if (enabled && activeView != MonitorView.TIME && activeView != MonitorView.IMPEDANCE) {
            scheduler.execute(::refreshAnalysis)
        }
    }

    override fun selectModule(moduleId: String?) {
        synchronized(stateLock) {
            if (moduleId != null && moduleSlots[moduleId]?.enabled != true) return
            if (selectedModuleId == moduleId) return
            selectedModuleId = moduleId
            analyses = emptyList()
        }
        publish()
        if (activeView != MonitorView.TIME && activeView != MonitorView.IMPEDANCE) {
            scheduler.execute(::refreshAnalysis)
        }
    }

    /** Installs a compiled-in or validated declarative module into this running session. */
    fun installModule(module: EegProcessingModule): ProcessingModuleDescriptor {
        synchronized(stateLock) { registerModuleLocked(module) }
        publish()
        return module.descriptor
    }

    /** Only imported packages are removable; built-in examples are part of the application. */
    fun uninstallModule(moduleId: String) {
        val removed = synchronized(stateLock) {
            val slot = moduleSlots[moduleId] ?: return
            require(slot.module.descriptor.origin == ProcessingModuleOrigin.IMPORTED) {
                "内置模块不能移除"
            }
            if (selectedModuleId == moduleId) selectedModuleId = null
            analyses = emptyList()
            moduleSlots.remove(moduleId)
        } ?: return
        runCatching { removed.module.close() }
        publish()
    }

    private fun registerModuleLocked(module: EegProcessingModule) {
        val descriptor = module.descriptor
        require(descriptor.id.matches(MODULE_ID_PATTERN)) { "模块 ID 格式无效：${descriptor.id}" }
        require(descriptor.id !in moduleSlots) { "模块 ${descriptor.id} 已安装" }
        moduleSlots[descriptor.id] = ModuleSlot(module)
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
                moduleSlots.values.forEach { slot ->
                    slot.clearOutput()
                    slot.message = if (slot.enabled) "已添加到监测，等待脑电数据" else "尚未添加到监测"
                    runCatching { slot.module.reset() }
                        .onFailure { slot.error = it.message ?: "模块重置失败" }
                }
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
            trimSignalBuffer(this.samples)
            moduleSlots.values.filter { it.enabled }.forEach { slot ->
                runCatching { slot.module.process(samples, SAMPLE_RATE_HZ) }
                    .onSuccess { output -> applyModuleOutput(slot, output) }
                    .onFailure { failure ->
                        slot.error = failure.message ?: "模块处理失败"
                        slot.message = "处理已暂停，请移除后重新添加"
                        slot.enabled = false
                        if (selectedModuleId == slot.module.descriptor.id) selectedModuleId = null
                    }
            }
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

    private fun applyModuleOutput(slot: ModuleSlot, output: ProcessingModuleOutput) {
        when (output) {
            is EegSignalModuleOutput -> {
                require(slot.module.descriptor.type == ProcessingModuleType.EEG_TO_EEG) {
                    "模块声明与 EEG 输出不一致"
                }
                output.samples.forEach(slot.signalSamples::addLast)
                trimSignalBuffer(slot.signalSamples)
                slot.message = "已处理 ${slot.processedSamples + output.samples.size} 个采样点"
                slot.processedSamples += output.samples.size
                slot.error = null
            }

            is FeatureModuleOutput -> {
                require(slot.module.descriptor.type == ProcessingModuleType.EEG_TO_FEATURES) {
                    "模块声明与特征输出不一致"
                }
                output.values.filter { it.value.isFinite() }.forEach { feature ->
                    val series = slot.features.getOrPut(feature.key) {
                        FeatureAccumulator(feature.label, feature.unit, feature.channel)
                    }
                    series.label = feature.label
                    series.unit = feature.unit
                    series.channel = feature.channel
                    series.values.addLast(feature.value)
                    while (series.values.size > MAX_FEATURE_POINTS) series.values.removeFirst()
                }
                if (output.values.isNotEmpty()) {
                    slot.message = "已更新 ${output.values.size} 个特征值"
                    slot.error = null
                }
            }
        }
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
            val selected = selectedModuleId?.let(moduleSlots::get)
            if (selected?.module?.descriptor?.type == ProcessingModuleType.EEG_TO_FEATURES) {
                analyses = emptyList()
                return
            }
            signal = signalBufferLocked(selected).toList()
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
            val selected = selectedModuleId?.let(moduleSlots::get)?.takeIf { it.enabled }
            if (selected == null && selectedModuleId != null) selectedModuleId = null
            val visibleSamples = visibleSamples(signalBufferLocked(selected))
            val selectedDescriptor = selected?.module?.descriptor
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
                modules = moduleSlots.values.map { slot ->
                    ProcessingModuleState(
                        descriptor = slot.module.descriptor,
                        enabled = slot.enabled,
                        message = slot.message,
                        error = slot.error,
                    )
                },
                selectedModuleId = selectedDescriptor?.id,
                selectedModuleType = selectedDescriptor?.type,
                signalSourceLabel = selectedDescriptor?.displayName ?: "原始脑电",
                moduleFeatures = selected?.features?.map { (key, accumulator) ->
                    ModuleFeatureSeries(
                        key = key,
                        label = accumulator.label,
                        unit = accumulator.unit,
                        channel = accumulator.channel,
                        values = accumulator.values.toDoubleArray(),
                    )
                }.orEmpty(),
            )
        }
        lastSnapshot = snapshot
        listeners.forEach { listener ->
            runCatching { listener.onSnapshot(snapshot) }
        }
    }

    private fun signalBufferLocked(selected: ModuleSlot?): ArrayDeque<SignalSample> =
        if (selected?.module?.descriptor?.type == ProcessingModuleType.EEG_TO_EEG) {
            selected.signalSamples
        } else {
            samples
        }

    private fun visibleSamples(source: ArrayDeque<SignalSample>): List<SignalSample> {
        if (source.isEmpty()) return emptyList()
        val earliest = source.peekLast().index - VISIBLE_SAMPLE_SPAN
        return source.asSequence().filter { it.index >= earliest }.toList()
    }

    private fun trimSignalBuffer(buffer: ArrayDeque<SignalSample>) {
        while (buffer.size > MAX_BUFFERED_SAMPLES) buffer.removeFirst()
    }

    override fun close() {
        if (synchronized(stateLock) { acquisition.active }) stopAcquisition()
        device.setListener(null)
        device.close()
        runCatching { recordingSink?.close() }
        synchronized(stateLock) {
            moduleSlots.values.forEach { runCatching { it.module.close() } }
            moduleSlots.clear()
        }
        scheduler.shutdownNow()
        listeners.clear()
    }

    private class ModuleSlot(val module: EegProcessingModule) {
        var enabled: Boolean = false
        var message: String = "尚未添加到监测"
        var error: String? = null
        var processedSamples: Long = 0
        val signalSamples = ArrayDeque<SignalSample>()
        val features = linkedMapOf<String, FeatureAccumulator>()

        fun clearOutput() {
            signalSamples.clear()
            features.clear()
            processedSamples = 0
            error = null
        }
    }

    private class FeatureAccumulator(
        var label: String,
        var unit: String,
        var channel: Int?,
    ) {
        val values = ArrayDeque<Double>()
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 250.0
        const val MAX_BUFFERED_SAMPLES = 5_000
        const val MAX_FEATURE_POINTS = 120
        const val VISIBLE_SAMPLE_SPAN = 1_500L
        val MODULE_ID_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{2,79}")
    }
}
