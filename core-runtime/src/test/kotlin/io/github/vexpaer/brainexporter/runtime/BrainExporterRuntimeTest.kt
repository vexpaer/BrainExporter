package io.github.vexpaer.brainexporter.runtime

import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.ConnectionState
import io.github.vexpaer.brainexporter.sdk.DeviceCapability
import io.github.vexpaer.brainexporter.sdk.DeviceDescriptor
import io.github.vexpaer.brainexporter.sdk.DevicePlugin
import io.github.vexpaer.brainexporter.sdk.DevicePluginListener
import io.github.vexpaer.brainexporter.sdk.EegProcessingModule
import io.github.vexpaer.brainexporter.sdk.EegRecordingSink
import io.github.vexpaer.brainexporter.sdk.EegSignalModuleOutput
import io.github.vexpaer.brainexporter.sdk.ImpedanceState
import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleDescriptor
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOutput
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleType
import io.github.vexpaer.brainexporter.sdk.SignalAlgorithm
import io.github.vexpaer.brainexporter.sdk.SignalSample
import io.github.vexpaer.brainexporter.sdk.StreamMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainExporterRuntimeTest {
    @Test
    fun `connected device waits until acquisition is explicitly started`() {
        val device = FakeDevice()
        val sink = FakeSink()
        val runtime = BrainExporterRuntime(device, NoOpAlgorithm, sink)
        try {
            runtime.connect("device-1")
            assertEquals(ConnectionPhase.CONNECTED, runtime.snapshot().connection.phase)
            assertEquals(0, device.startCount)

            runtime.startAcquisition()

            assertTrue(runtime.snapshot().acquisition.active)
            assertEquals("Documents/eegData/test.csv", runtime.snapshot().acquisition.fileLocation)
            assertEquals(1, device.startCount)
            assertEquals(1, sink.startCount)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `samples are forwarded to the recording and stop closes both layers`() {
        val device = FakeDevice()
        val sink = FakeSink()
        val runtime = BrainExporterRuntime(device, NoOpAlgorithm, sink)
        try {
            runtime.connect("device-1")
            runtime.startAcquisition()
            val samples = listOf(
                SignalSample(0, 1, DoubleArray(8) { it.toDouble() }, 10),
                SignalSample(1, 2, DoubleArray(8) { it.toDouble() + 1 }, 20),
            )
            device.emit(samples)

            runtime.stopAcquisition()

            assertFalse(runtime.snapshot().acquisition.active)
            assertEquals(2, runtime.snapshot().acquisition.samplesWritten)
            assertEquals(samples, sink.samples)
            assertEquals(1, device.stopCount)
            assertEquals(1, sink.stopCount)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `enabled eeg module can become the monitor signal source`() {
        val device = FakeDevice()
        val runtime = BrainExporterRuntime(device, NoOpAlgorithm, FakeSink(), modules = listOf(AddOneModule()))
        try {
            runtime.connect("device-1")
            runtime.setModuleEnabled("test.add-one", true)
            runtime.selectModule("test.add-one")
            runtime.startAcquisition()
            device.emit(listOf(SignalSample(0, 1, DoubleArray(8) { 4.0 }, 10)))
            runtime.stopAcquisition()

            val snapshot = runtime.snapshot()
            assertEquals("test.add-one", snapshot.selectedModuleId)
            assertEquals("加一测试", snapshot.signalSourceLabel)
            assertEquals(5.0, snapshot.samples.single().valuesUv[0], 0.0)
        } finally {
            runtime.close()
        }
    }

    private class FakeDevice : DevicePlugin {
        override val id = "fake"
        override val displayName = "Fake"
        override val capabilities = setOf(DeviceCapability.LIVE_SIGNAL)
        private var listener: DevicePluginListener? = null
        var startCount = 0
        var stopCount = 0

        override fun setListener(listener: DevicePluginListener?) {
            this.listener = listener
        }

        override fun scan(durationMillis: Long) {
            listener?.onDevicesChanged(listOf(DeviceDescriptor("device-1", "Fake EEG", -40)))
        }

        override fun connect(deviceId: String) {
            listener?.onConnectionChanged(
                ConnectionState(ConnectionPhase.CONNECTED, "Fake EEG", deviceId, "test", "已连接"),
            )
        }

        override fun disconnect() {
            listener?.onConnectionChanged(ConnectionState())
        }

        override fun startStreaming() {
            startCount++
        }

        override fun stopStreaming() {
            stopCount++
        }

        override fun startImpedance(channel: Int?, dwellSeconds: Double) = Unit
        override fun stopImpedance() = Unit
        override fun close() = Unit

        fun emit(samples: List<SignalSample>) {
            listener?.onSamples(samples, StreamMetrics(frames = samples.size.toLong()))
        }
    }

    private object NoOpAlgorithm : SignalAlgorithm {
        override val id = "noop"
        override val displayName = "No-op"
        override fun analyze(
            samples: List<SignalSample>,
            view: MonitorView,
            sampleRateHz: Double,
        ): List<ChannelAnalysis> = emptyList()
    }

    private class AddOneModule : EegProcessingModule {
        override val descriptor = ProcessingModuleDescriptor(
            id = "test.add-one",
            displayName = "加一测试",
            version = "1.0.0",
            description = "test",
            type = ProcessingModuleType.EEG_TO_EEG,
            engine = "test",
        )

        override fun reset() = Unit

        override fun process(samples: List<SignalSample>, sampleRateHz: Double): ProcessingModuleOutput =
            EegSignalModuleOutput(samples.map { sample ->
                sample.copy(valuesUv = DoubleArray(sample.valuesUv.size) { channel -> sample.valuesUv[channel] + 1.0 })
            })
    }

    private class FakeSink : EegRecordingSink {
        var startCount = 0
        var stopCount = 0
        val samples = mutableListOf<SignalSample>()

        override fun start(deviceName: String?): String {
            startCount++
            return "Documents/eegData/test.csv"
        }

        override fun append(samples: List<SignalSample>) {
            this.samples += samples
        }

        override fun stop(): String {
            stopCount++
            return "Documents/eegData/test.csv"
        }

        override fun close() = Unit
    }
}
