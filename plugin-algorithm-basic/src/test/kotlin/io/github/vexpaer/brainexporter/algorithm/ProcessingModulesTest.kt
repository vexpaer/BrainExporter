package io.github.vexpaer.brainexporter.algorithm

import io.github.vexpaer.brainexporter.sdk.EegSignalModuleOutput
import io.github.vexpaer.brainexporter.sdk.FeatureModuleOutput
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleType
import io.github.vexpaer.brainexporter.sdk.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ProcessingModulesTest {
    @Test
    fun `one to forty hertz filter preserves ten hertz and rejects seventy hertz`() {
        val rate = 250.0
        val module = BandPassEegModule.builtIn1To40Hz()
        val input = (0 until 2_500).map { index ->
            val value = 30.0 * sin(2.0 * PI * 10.0 * index / rate) +
                30.0 * sin(2.0 * PI * 70.0 * index / rate)
            SignalSample(index.toLong(), index and 0xff, DoubleArray(8) { value }, index.toLong())
        }

        val output = (module.process(input, rate) as EegSignalModuleOutput).samples
            .drop(500)
            .map { it.valuesUv[0] }
        val tenHz = sinusoidAmplitude(output, 10.0, rate, startIndex = 500)
        val seventyHz = sinusoidAmplitude(output, 70.0, rate, startIndex = 500)

        assertTrue("10 Hz amplitude was $tenHz", tenHz > 20.0)
        assertTrue("70 Hz amplitude was $seventyHz", seventyHz < 8.0)
    }

    @Test
    fun `declarative manifests create both stable module output shapes`() {
        val factory = DeclarativeModuleFactory()
        val filter = factory.create(
            """
            {
              "schemaVersion": 1,
              "id": "example.bandpass-2-35",
              "name": "2–35 Hz 测试滤波",
              "version": "1.0.0",
              "type": "eeg_to_eeg",
              "engine": "butterworth_bandpass",
              "config": { "lowCutHz": 2.0, "highCutHz": 35.0, "order": 2 }
            }
            """.trimIndent(),
        )
        val features = factory.create(
            """
            {
              "schemaVersion": 1,
              "id": "example.window-features",
              "name": "窗口特征测试",
              "version": "1.0.0",
              "type": "eeg_to_features",
              "engine": "window_statistics",
              "config": { "windowSeconds": 1.0, "strideSeconds": 0.25 }
            }
            """.trimIndent(),
        )

        assertEquals(ProcessingModuleType.EEG_TO_EEG, filter.descriptor.type)
        assertEquals(ProcessingModuleType.EEG_TO_FEATURES, features.descriptor.type)
        val samples = (0 until 250).map { index ->
            SignalSample(index.toLong(), index, DoubleArray(8) { index.toDouble() }, index.toLong())
        }
        val output = features.process(samples, 250.0) as FeatureModuleOutput
        assertEquals(16, output.values.size)
    }

    private fun sinusoidAmplitude(
        values: List<Double>,
        frequencyHz: Double,
        sampleRateHz: Double,
        startIndex: Int,
    ): Double {
        var real = 0.0
        var imaginary = 0.0
        values.forEachIndexed { offset, value ->
            val angle = 2.0 * PI * frequencyHz * (startIndex + offset) / sampleRateHz
            real += value * cos(angle)
            imaginary -= value * sin(angle)
        }
        return 2.0 * kotlin.math.sqrt(real * real + imaginary * imaginary) / values.size
    }
}
