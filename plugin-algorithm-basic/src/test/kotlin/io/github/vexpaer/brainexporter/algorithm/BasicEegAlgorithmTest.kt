package io.github.vexpaer.brainexporter.algorithm

import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BasicEegAlgorithmTest {
    @Test
    fun tenHertzSignalPeaksInAlphaBand() {
        val rate = 250.0
        val samples = (0 until 1_250).map { index ->
            val value = 40.0 * sin(2.0 * PI * 10.0 * index / rate)
            SignalSample(
                index = index.toLong(),
                packetId = index and 0xff,
                valuesUv = DoubleArray(8) { value },
                receivedAtNanos = index.toLong(),
            )
        }
        val algorithm = BasicEegAlgorithm()

        val psd = algorithm.analyze(samples, MonitorView.PSD, rate)
        assertEquals(8, psd.size)
        assertEquals(10.0, psd.first().peakFrequencyHz!!, 0.2)

        val bands = algorithm.analyze(samples, MonitorView.BANDS, rate)
        val alpha = bands.first().bars.single { it.label == "Alpha" }
        assertTrue(alpha.value > 90.0)
        assertEquals(1.0, bands.first().coverage, 1e-9)
    }
}
