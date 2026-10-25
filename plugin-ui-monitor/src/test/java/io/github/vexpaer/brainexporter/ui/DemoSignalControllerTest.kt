package io.github.vexpaer.brainexporter.ui

import io.github.vexpaer.brainexporter.sdk.MonitorView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSignalControllerTest {
    @Test fun syntheticSignalIsAvailableImmediately() {
        val demo = DemoSignalController()
        val initial = demo.snapshot()
        assertEquals("DEMO", initial.connection.deviceName)
        assertEquals(1_250, initial.samples.size)
        assertEquals(8, initial.samples.first().valuesUv.size)
        assertEquals(250.0, initial.metrics.effectiveSampleRateHz, 0.001)
        demo.advance()
        assertEquals(1_250, demo.snapshot().samples.size)
        assertEquals(1_281L, demo.snapshot().samples.last().index)
        demo.close()
    }

    @Test fun previewsNeverRecordOrControlHardware() {
        val demo = DemoSignalController()
        demo.startAcquisition()
        assertFalse(demo.snapshot().acquisition.active)
        assertEquals(null, demo.snapshot().acquisition.fileLocation)
        demo.setView(MonitorView.PSD)
        assertEquals(MonitorView.PSD, demo.snapshot().activeView)
        assertTrue(demo.snapshot().analyses.all { it.line != null })
        demo.setView(MonitorView.BANDS)
        assertTrue(demo.snapshot().analyses.all { it.bars.isNotEmpty() })
        demo.startImpedance(null)
        assertEquals(MonitorView.IMPEDANCE, demo.snapshot().activeView)
        assertEquals(8, demo.snapshot().impedance.results.filterNotNull().size)
        demo.close()
    }
}
