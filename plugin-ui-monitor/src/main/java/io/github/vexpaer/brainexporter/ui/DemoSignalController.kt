package io.github.vexpaer.brainexporter.ui

import io.github.vexpaer.brainexporter.sdk.AcquisitionState
import io.github.vexpaer.brainexporter.sdk.BarValue
import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.ConnectionState
import io.github.vexpaer.brainexporter.sdk.ImpedanceQuality
import io.github.vexpaer.brainexporter.sdk.ImpedanceResult
import io.github.vexpaer.brainexporter.sdk.ImpedanceState
import io.github.vexpaer.brainexporter.sdk.LineSeries
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.SignalSample
import io.github.vexpaer.brainexporter.sdk.SnapshotListener
import io.github.vexpaer.brainexporter.sdk.StreamMetrics
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * On-device, deterministic preview. Never touches Bluetooth, permissions or the CSV sink.
 * Samples, spectra and impedance are synthetic, and must always be marked DEMO in the UI.
 */
internal class DemoSignalController : MonitorController {
    private val listeners = mutableSetOf<SnapshotListener>()
    private var index = 1_250L
    private var state = MonitorSnapshot(
        connection = ConnectionState(
            phase = ConnectionPhase.CONNECTED,
            deviceName = "DEMO",
            profileName = "SIMULATED",
            message = "Simulated signal",
        ),
        metrics = StreamMetrics(
            frames = index, receivedBytes = index * 33L,
            notificationCount = index, effectiveSampleRateHz = 250.0,
        ),
        samples = (0L until index).map(::sample),
        acquisition = AcquisitionState(message = "Preview only"),
        analyses = spectra(MonitorView.TIME),
        signalSourceLabel = "Simulated EEG",
    )

    override fun snapshot(): MonitorSnapshot = state

    override fun addListener(listener: SnapshotListener): AutoCloseable {
        listeners.add(listener)
        listener.onSnapshot(state)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun advance() {
        val fresh = (index until index + 32).map(::sample)
        index += fresh.size
        state = state.copy(
            samples = (state.samples + fresh).takeLast(1_250),
            metrics = state.metrics.copy(
                frames = index, receivedBytes = index * 33L,
                notificationCount = index,
            ),
        )
        publish()
    }

    override fun setView(view: MonitorView) {
        state = state.copy(activeView = view, analyses = spectra(view))
        publish()
    }

    override fun startImpedance(channel: Int?) {
        // Illustrative readings only. They are never obtained from electrodes.
        val results = (1..8).map { ch ->
            if (channel != null && ch != channel) null
            else ImpedanceResult(
                channel = ch, kiloOhms = 22.0 + ch * 11.0,
                standardDeviationUv = 0.75 + ch * 0.05,
                sampleCount = 1_250, quality = ImpedanceQuality.GOOD,
            )
        }
        state = state.copy(impedance = ImpedanceState(results = results), activeView = MonitorView.IMPEDANCE)
        publish()
    }

    override fun scan() = Unit
    override fun connect(deviceId: String) = Unit
    override fun disconnect() = Unit
    override fun startAcquisition() = Unit
    override fun stopAcquisition() = Unit
    override fun stopImpedance() = Unit
    override fun setModuleEnabled(moduleId: String, enabled: Boolean) = Unit
    override fun selectModule(moduleId: String?) = Unit
    override fun close() { listeners.clear() }

    private fun publish() {
        listeners.toList().forEach { it.onSnapshot(state) }
    }

    private fun sample(i: Long): SignalSample {
        val seconds = i.toDouble() / 250.0
        return SignalSample(
            index = i,
            packetId = (i % 256).toInt(),
            receivedAtNanos = i * 4_000_000L,
            valuesUv = DoubleArray(8) { c ->
                val phase = c * 0.43
                12.0 * sin(2.0 * PI * (1.5 + c * 0.06) * seconds + phase) +
                    (8.5 + c * 0.7) * sin(2.0 * PI * (9.7 + c * 0.15) * seconds + phase) +
                    3.2 * sin(2.0 * PI * 20.0 * seconds + phase * 2.0) +
                    1.2 * sin(2.0 * PI * 43.0 * seconds + c)
            },
        )
    }

    private fun spectra(view: MonitorView): List<ChannelAnalysis> = (1..8).map { ch ->
        val frequencies = DoubleArray(121) { it / 2.0 }
        val values = frequencies.map { f ->
            fun peak(at: Double, width: Double, height: Double): Double {
                val x = (f - at) / width
                return height * exp(-0.5 * x * x)
            }
            0.15 + peak(1.6, 1.0, 8.0) +
                peak(9.7 + ch * 0.15, 1.3, 16.0) +
                peak(20.0, 2.4, 3.5)
        }.toDoubleArray()
        ChannelAnalysis(
            channel = ch,
            line = LineSeries(frequencies, values),
            bars = listOf(
                BarValue("Delta", 26.0 + ch),
                BarValue("Theta", 15.0 + ch * 0.3),
                BarValue("Alpha", 41.0 - ch * 0.6),
                BarValue("Beta", 13.0),
                BarValue("Gamma", 5.0),
            ),
            peakFrequencyHz = 9.7 + ch * 0.15,
            coverage = 1.0,
            summary = if (view == MonitorView.BANDS) "Synthetic bands" else "Synthetic spectrum",
        )
    }
}
