package io.github.vexpaer.brainexporter.algorithm

import io.github.vexpaer.brainexporter.sdk.BarValue
import io.github.vexpaer.brainexporter.sdk.ChannelAnalysis
import io.github.vexpaer.brainexporter.sdk.LineSeries
import io.github.vexpaer.brainexporter.sdk.MonitorView
import io.github.vexpaer.brainexporter.sdk.SignalAlgorithm
import io.github.vexpaer.brainexporter.sdk.SignalSample
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class BasicEegAlgorithm : SignalAlgorithm {
    override val id: String = "basic-eeg-spectral-v1"
    override val displayName: String = "基础 EEG 频域算法"

    override fun analyze(
        samples: List<SignalSample>,
        view: MonitorView,
        sampleRateHz: Double,
    ): List<ChannelAnalysis> {
        if (view == MonitorView.TIME || view == MonitorView.IMPEDANCE || samples.isEmpty()) {
            return emptyList()
        }
        val seconds = if (view == MonitorView.SPECTRUM) 2.0 else 5.0
        return (0 until CHANNEL_COUNT).mapNotNull { channel ->
            val prepared = prepareUniformSignal(samples, channel, seconds, sampleRateHz)
                ?: return@mapNotNull null
            val spectral = computeSpectral(prepared.values, sampleRateHz)
            val peakSource = if (view == MonitorView.SPECTRUM) spectral.amplitude else spectral.density
            val peakIndex = spectral.frequencies.indices
                .filter { spectral.frequencies[it] in 1.0..45.0 }
                .maxByOrNull { peakSource[it] }
            val peak = peakIndex?.let { spectral.frequencies[it] }

            when (view) {
                MonitorView.PSD -> ChannelAnalysis(
                    channel = channel + 1,
                    line = LineSeries(spectral.frequencies, spectral.decibels),
                    peakFrequencyHz = peak,
                    coverage = prepared.coverage,
                    summary = summary(peak, prepared.coverage),
                )

                MonitorView.SPECTRUM -> ChannelAnalysis(
                    channel = channel + 1,
                    line = LineSeries(spectral.frequencies, spectral.amplitude),
                    peakFrequencyHz = peak,
                    coverage = prepared.coverage,
                    summary = summary(peak, prepared.coverage),
                )

                MonitorView.BANDS -> ChannelAnalysis(
                    channel = channel + 1,
                    bars = computeBands(spectral),
                    peakFrequencyHz = peak,
                    coverage = prepared.coverage,
                    summary = "最近 5 秒 · 覆盖 ${formatPercent(prepared.coverage)}",
                )

                else -> null
            }
        }
    }

    private fun prepareUniformSignal(
        samples: List<SignalSample>,
        channel: Int,
        seconds: Double,
        sampleRateHz: Double,
    ): PreparedSignal? {
        val length = (seconds * sampleRateHz).toInt()
        if (length < 20) return null
        val latestIndex = samples.last().index
        val firstIndex = latestIndex - length + 1
        val values = DoubleArray(length) { Double.NaN }
        for (sample in samples) {
            val position = (sample.index - firstIndex).toInt()
            if (position in values.indices && channel < sample.valuesUv.size) {
                values[position] = sample.valuesUv[channel]
            }
        }
        val finite = values.indices.filter { values[it].isFinite() }
        if (finite.size < 20) return null

        val first = finite.first()
        val last = finite.last()
        for (index in 0 until first) values[index] = values[first]
        for (index in last + 1 until values.size) values[index] = values[last]
        for (pair in 0 until finite.lastIndex) {
            val left = finite[pair]
            val right = finite[pair + 1]
            val gap = right - left
            if (gap <= 1) continue
            for (offset in 1 until gap) {
                values[left + offset] = values[left] +
                    (values[right] - values[left]) * offset / gap
            }
        }
        return PreparedSignal(values, finite.size.toDouble() / length)
    }

    private fun computeSpectral(values: DoubleArray, sampleRateHz: Double): SpectralResult {
        val mean = values.average()
        var fftLength = 1
        while (fftLength < values.size) fftLength = fftLength shl 1
        val real = DoubleArray(fftLength)
        val imaginary = DoubleArray(fftLength)
        var windowPower = 0.0
        var windowSum = 0.0
        for (index in values.indices) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * index / (values.size - 1))
            real[index] = (values[index] - mean) * window
            windowPower += window * window
            windowSum += window
        }
        fft(real, imaginary)

        val maximumBin = ((60.0 * fftLength) / sampleRateHz)
            .toInt()
            .coerceAtMost(fftLength / 2)
        val frequencies = DoubleArray(maximumBin + 1)
        val density = DoubleArray(maximumBin + 1)
        val decibels = DoubleArray(maximumBin + 1)
        val amplitude = DoubleArray(maximumBin + 1)
        for (bin in 0..maximumBin) {
            val magnitudeSquared = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            var binDensity = magnitudeSquared / (sampleRateHz * windowPower)
            var binAmplitude = sqrt(magnitudeSquared) / windowSum
            if (bin > 0 && bin < fftLength / 2) {
                binDensity *= 2.0
                binAmplitude *= 2.0
            }
            frequencies[bin] = bin * sampleRateHz / fftLength
            density[bin] = binDensity
            decibels[bin] = 10.0 * ln(max(binDensity, 1e-20)) / ln(10.0)
            amplitude[bin] = binAmplitude
        }
        return SpectralResult(
            frequencies = frequencies,
            density = density,
            decibels = decibels,
            amplitude = amplitude,
            resolution = sampleRateHz / fftLength,
        )
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val length = real.size
        var reversed = 0
        for (index in 1 until length) {
            var bit = length shr 1
            while (reversed and bit != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed xor bit
            if (index < reversed) {
                val realTemp = real[index]
                real[index] = real[reversed]
                real[reversed] = realTemp
                val imaginaryTemp = imaginary[index]
                imaginary[index] = imaginary[reversed]
                imaginary[reversed] = imaginaryTemp
            }
        }

        var size = 2
        while (size <= length) {
            val angle = -2.0 * PI / size
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < length) {
                var rotationReal = 1.0
                var rotationImaginary = 0.0
                for (offset in 0 until size / 2) {
                    val even = start + offset
                    val odd = even + size / 2
                    val oddReal = real[odd] * rotationReal - imaginary[odd] * rotationImaginary
                    val oddImaginary = real[odd] * rotationImaginary + imaginary[odd] * rotationReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = rotationReal * stepReal - rotationImaginary * stepImaginary
                    rotationImaginary = rotationReal * stepImaginary + rotationImaginary * stepReal
                    rotationReal = nextReal
                }
                start += size
            }
            size = size shl 1
        }
    }

    private fun computeBands(spectral: SpectralResult): List<BarValue> {
        val powers = BANDS.map { band ->
            spectral.frequencies.indices.sumOf { index ->
                val frequency = spectral.frequencies[index]
                if (frequency >= band.low && frequency < band.high) {
                    spectral.density[index] * spectral.resolution
                } else {
                    0.0
                }
            }
        }
        val total = powers.sum()
        return BANDS.mapIndexed { index, band ->
            BarValue(
                label = band.label,
                value = if (total > 0) powers[index] / total * 100.0 else 0.0,
            )
        }
    }

    private fun summary(peak: Double?, coverage: Double): String =
        if (peak == null) {
            "覆盖 ${formatPercent(coverage)}"
        } else {
            "峰值 ${"%.1f".format(peak)} Hz · 覆盖 ${formatPercent(coverage)}"
        }

    private fun formatPercent(value: Double): String = "%.1f%%".format(value * 100.0)

    private data class PreparedSignal(val values: DoubleArray, val coverage: Double)

    private data class SpectralResult(
        val frequencies: DoubleArray,
        val density: DoubleArray,
        val decibels: DoubleArray,
        val amplitude: DoubleArray,
        val resolution: Double,
    )

    private data class Band(val label: String, val low: Double, val high: Double)

    private companion object {
        const val CHANNEL_COUNT = 8
        val BANDS = listOf(
            Band("Delta", 1.0, 4.0),
            Band("Theta", 4.0, 8.0),
            Band("Alpha", 8.0, 13.0),
            Band("Beta", 13.0, 30.0),
            Band("Gamma", 30.0, 45.0),
        )
    }
}
