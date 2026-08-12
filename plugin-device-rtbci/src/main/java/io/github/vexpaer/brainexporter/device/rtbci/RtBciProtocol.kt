package io.github.vexpaer.brainexporter.device.rtbci

import kotlin.math.max

internal data class RtBciFrame(
    val packetId: Int,
    val channelCounts: IntArray,
    val auxiliary: ByteArray,
)

/** Incremental decoder for A0 + id + 8x24-bit + 6 aux + C0 packets. */
internal class RtBciFrameDecoder {
    private var buffer = ByteArray(4_096)
    private var size = 0
    var discardedBytes: Long = 0
        private set

    fun reset() {
        size = 0
        discardedBytes = 0
    }

    fun feed(data: ByteArray): List<RtBciFrame> {
        if (data.isEmpty()) return emptyList()
        ensureCapacity(size + data.size)
        data.copyInto(buffer, destinationOffset = size)
        size += data.size

        val frames = mutableListOf<RtBciFrame>()
        var cursor = 0
        while (cursor < size) {
            var header = cursor
            while (header < size && buffer[header].toInt() and 0xff != FRAME_HEADER) header++
            if (header == size) {
                discardedBytes += size - cursor
                cursor = size
                break
            }
            if (header > cursor) discardedBytes += header - cursor
            if (size - header < FRAME_LENGTH) {
                cursor = header
                break
            }
            if (buffer[header + FRAME_LENGTH - 1].toInt() and 0xff != FRAME_END) {
                discardedBytes++
                cursor = header + 1
                continue
            }

            val counts = IntArray(CHANNEL_COUNT)
            for (channel in 0 until CHANNEL_COUNT) {
                val offset = header + 2 + channel * 3
                var raw = ((buffer[offset].toInt() and 0xff) shl 16) or
                    ((buffer[offset + 1].toInt() and 0xff) shl 8) or
                    (buffer[offset + 2].toInt() and 0xff)
                if (raw and 0x800000 != 0) raw -= 0x1000000
                counts[channel] = raw
            }
            frames += RtBciFrame(
                packetId = buffer[header + 1].toInt() and 0xff,
                channelCounts = counts,
                auxiliary = buffer.copyOfRange(header + 26, header + 32),
            )
            cursor = header + FRAME_LENGTH
        }

        if (cursor > 0) {
            val remaining = size - cursor
            if (remaining > 0) buffer.copyInto(buffer, 0, cursor, size)
            size = remaining
        }
        return frames
    }

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        buffer = buffer.copyOf(max(required, buffer.size * 2))
    }

    companion object {
        const val FRAME_HEADER = 0xA0
        const val FRAME_END = 0xC0
        const val FRAME_LENGTH = 33
        const val CHANNEL_COUNT = 8
    }
}

internal class PacketStatistics {
    var frames: Long = 0
        private set
    var missing: Long = 0
        private set
    var duplicates: Long = 0
        private set
    var resets: Long = 0
        private set
    private var previous: Int? = null

    fun accept(packetId: Int) {
        previous?.let { old ->
            val delta = (packetId - old) and 0xff
            when {
                delta == 0 -> duplicates++
                delta in 2..128 -> missing += delta - 1
                delta > 128 -> resets++
            }
        }
        previous = packetId
        frames++
    }
}

internal class SequenceTimeline {
    private var previous: Int? = null
    private var sampleIndex = -1L

    fun accept(packetId: Int): Long {
        val old = previous
        if (old == null) {
            sampleIndex = 0
        } else {
            var delta = (packetId - old) and 0xff
            if (delta == 0 || delta > 128) delta = 1
            sampleIndex += delta
        }
        previous = packetId
        return sampleIndex
    }
}

internal fun channelImpedancePayload(channel: Int, enabled: Boolean): ByteArray {
    require(channel in 1..8) { "channel must be between 1 and 8" }
    val selector = "12345678"[channel - 1]
    val command = if (enabled) {
        "x${selector}000100Xz${selector}01Z"
    } else {
        "x${selector}060101Xz${selector}00Z"
    }
    return command.encodeToByteArray()
}

internal fun countsToMicrovolts(count: Int, gain: Double): Double =
    count * DEFAULT_VREF * 1_000_000.0 / ((1 shl 23) - 1) / gain

internal const val DEFAULT_GAIN = 24.0
internal const val DEFAULT_VREF = 4.5
internal const val SAMPLE_RATE_HZ = 250.0
