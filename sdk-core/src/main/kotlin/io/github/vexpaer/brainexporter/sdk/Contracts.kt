package io.github.vexpaer.brainexporter.sdk

/** Stable contracts shared by device, algorithm, runtime and UI plug-ins. */
enum class DeviceCapability {
    LIVE_SIGNAL,
    IMPEDANCE,
}

data class DeviceDescriptor(
    val id: String,
    val name: String,
    val rssi: Int,
    val serviceUuids: List<String> = emptyList(),
    val recommended: Boolean = false,
)

enum class ConnectionPhase {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class ConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val deviceName: String? = null,
    val deviceId: String? = null,
    val profileName: String? = null,
    val message: String = "未连接",
)

data class SignalSample(
    val index: Long,
    val packetId: Int,
    val valuesUv: DoubleArray,
    val receivedAtNanos: Long,
)

data class StreamMetrics(
    val frames: Long = 0,
    val missingPackets: Long = 0,
    val duplicatePackets: Long = 0,
    val sequenceResets: Long = 0,
    val receivedBytes: Long = 0,
    val notificationCount: Long = 0,
    val discardedBytes: Long = 0,
    val effectiveSampleRateHz: Double = 0.0,
) {
    val packetLossRatio: Double
        get() {
            val expected = frames + missingPackets
            return if (expected > 0) missingPackets.toDouble() / expected else 0.0
        }
}

data class AcquisitionState(
    val active: Boolean = false,
    val startedAtEpochMillis: Long? = null,
    val samplesWritten: Long = 0,
    val fileLocation: String? = null,
    val message: String = "连接设备后可开始采集",
    val error: String? = null,
)

enum class MonitorView {
    TIME,
    PSD,
    SPECTRUM,
    BANDS,
    IMPEDANCE,
}

data class LineSeries(
    val x: DoubleArray,
    val y: DoubleArray,
)

data class BarValue(
    val label: String,
    val value: Double,
)

data class ChannelAnalysis(
    val channel: Int,
    val line: LineSeries? = null,
    val bars: List<BarValue> = emptyList(),
    val peakFrequencyHz: Double? = null,
    val coverage: Double = 0.0,
    val summary: String = "",
)

enum class ImpedanceQuality {
    GOOD,
    WARNING,
    BAD,
}

data class ImpedanceResult(
    val channel: Int,
    val kiloOhms: Double,
    val standardDeviationUv: Double,
    val sampleCount: Int,
    val quality: ImpedanceQuality,
)

data class ImpedanceState(
    val running: Boolean = false,
    val channel: Int? = null,
    val progress: Double = 0.0,
    val dwellSeconds: Double = 5.0,
    val results: List<ImpedanceResult?> = List(8) { null },
    val error: String? = null,
)

data class MonitorSnapshot(
    val devices: List<DeviceDescriptor> = emptyList(),
    val connection: ConnectionState = ConnectionState(),
    val metrics: StreamMetrics = StreamMetrics(),
    val samples: List<SignalSample> = emptyList(),
    val analyses: List<ChannelAnalysis> = emptyList(),
    val impedance: ImpedanceState = ImpedanceState(),
    val acquisition: AcquisitionState = AcquisitionState(),
    val activeView: MonitorView = MonitorView.TIME,
    val capabilities: Set<DeviceCapability> = emptySet(),
)

interface DevicePluginListener {
    fun onDevicesChanged(devices: List<DeviceDescriptor>)
    fun onConnectionChanged(state: ConnectionState)
    fun onSamples(samples: List<SignalSample>, metrics: StreamMetrics)
    fun onImpedanceChanged(state: ImpedanceState)
}

interface DevicePlugin : AutoCloseable {
    val id: String
    val displayName: String
    val capabilities: Set<DeviceCapability>

    fun setListener(listener: DevicePluginListener?)
    fun scan(durationMillis: Long = 6_000)
    fun connect(deviceId: String)
    fun disconnect()
    fun startStreaming()
    fun stopStreaming()
    fun startImpedance(channel: Int? = null, dwellSeconds: Double = 5.0)
    fun stopImpedance()
}

interface SignalAlgorithm {
    val id: String
    val displayName: String

    fun analyze(
        samples: List<SignalSample>,
        view: MonitorView,
        sampleRateHz: Double,
    ): List<ChannelAnalysis>
}

fun interface SnapshotListener {
    fun onSnapshot(snapshot: MonitorSnapshot)
}

interface MonitorController : AutoCloseable {
    fun snapshot(): MonitorSnapshot
    fun addListener(listener: SnapshotListener): AutoCloseable
    fun setView(view: MonitorView)
    fun scan()
    fun connect(deviceId: String)
    fun disconnect()
    fun startAcquisition()
    fun stopAcquisition()
    fun startImpedance(channel: Int? = null)
    fun stopImpedance()
}

/** Host-provided storage layer. Android implementations can target public Documents safely. */
interface EegRecordingSink : AutoCloseable {
    /** Starts a new CSV recording and returns a user-facing file location. */
    fun start(deviceName: String?): String
    fun append(samples: List<SignalSample>)
    /** Finishes the current file and returns its final location, if one was open. */
    fun stop(): String?
}
