package io.github.vexpaer.brainexporter.device.rtbci

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RtBciProtocolTest {
    @Test
    fun decoderRecoversSplitSignedFramesAndNoise() {
        val counts = intArrayOf(0, 1, -1, 0x7fffff, -0x800000, 12345, -54321, 42)
        val frame = buildFrame(255, counts)
        val decoder = RtBciFrameDecoder()

        assertEquals(0, decoder.feed(byteArrayOf(1, 2, 3) + frame.copyOfRange(0, 9)).size)
        val decoded = decoder.feed(frame.copyOfRange(9, frame.size)).single()

        assertEquals(255, decoded.packetId)
        assertArrayEquals(counts, decoded.channelCounts)
        assertEquals(3, decoder.discardedBytes)
    }

    @Test
    fun impedanceCommandsMatchFirmwareContract() {
        assertEquals("x1000100Xz101Z", channelImpedancePayload(1, true).decodeToString())
        assertEquals("x8060101Xz800Z", channelImpedancePayload(8, false).decodeToString())
    }

    private fun buildFrame(packetId: Int, counts: IntArray): ByteArray {
        val result = ByteArray(33)
        result[0] = 0xA0.toByte()
        result[1] = packetId.toByte()
        counts.forEachIndexed { channel, value ->
            val offset = 2 + channel * 3
            result[offset] = (value shr 16).toByte()
            result[offset + 1] = (value shr 8).toByte()
            result[offset + 2] = value.toByte()
        }
        result[32] = 0xC0.toByte()
        return result
    }
}
