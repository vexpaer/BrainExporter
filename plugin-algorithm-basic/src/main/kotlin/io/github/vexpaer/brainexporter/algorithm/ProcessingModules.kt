package io.github.vexpaer.brainexporter.algorithm

import io.github.vexpaer.brainexporter.sdk.EegProcessingModule
import io.github.vexpaer.brainexporter.sdk.EegSignalModuleOutput
import io.github.vexpaer.brainexporter.sdk.FeatureModuleOutput
import io.github.vexpaer.brainexporter.sdk.ModuleFeatureValue
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleDescriptor
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOrigin
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOutput
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleType
import io.github.vexpaer.brainexporter.sdk.SignalSample
import java.util.ArrayDeque
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Continuous Butterworth high-pass + low-pass module. State is retained across BLE batches. */
class BandPassEegModule(
    private val lowCutHz: Double,
    private val highCutHz: Double,
    private val order: Int = 2,
    override val descriptor: ProcessingModuleDescriptor = builtInDescriptor(),
) : EegProcessingModule {
    private var sampleRateHz = Double.NaN
    private var highPassSections: List<BiquadBank> = emptyList()
    private var lowPassSections: List<BiquadBank> = emptyList()
    private var lastSampleIndex: Long? = null

    init {
        require(lowCutHz > 0.0) { "Low cutoff must exceed 0 Hz" }
        require(highCutHz > lowCutHz) { "High cutoff must exceed low cutoff" }
        require(order == 2 || order == 4) { "This bandpass engine supports order 2 or 4" }
        require(descriptor.type == ProcessingModuleType.EEG_TO_EEG) { "Bandpass modules must declare eeg_to_eeg" }
    }

    override fun reset() {
        highPassSections.forEach(BiquadBank::reset)
        lowPassSections.forEach(BiquadBank::reset)
        lastSampleIndex = null
    }

    override fun process(samples: List<SignalSample>, sampleRateHz: Double): ProcessingModuleOutput {
        if (samples.isEmpty()) return EegSignalModuleOutput(emptyList())
        require(highCutHz < sampleRateHz / 2.0) { "High cutoff must stay below the Nyquist frequency" }
        if (this.sampleRateHz != sampleRateHz || highPassSections.isEmpty()) {
            configure(sampleRateHz)
        }

        val output = ArrayList<SignalSample>(samples.size)
        samples.forEach { sample ->
            if (lastSampleIndex != null && sample.index != lastSampleIndex!! + 1L) reset()
            val filtered = DoubleArray(sample.valuesUv.size)
            sample.valuesUv.forEachIndexed { channel, raw ->
                var value = raw
                highPassSections.forEach { value = it.process(channel, value) }
                lowPassSections.forEach { value = it.process(channel, value) }
                filtered[channel] = value
            }
            output += sample.copy(valuesUv = filtered)
            lastSampleIndex = sample.index
        }
        return EegSignalModuleOutput(output)
    }

    private fun configure(rate: Double) {
        sampleRateHz = rate
        val sectionQs = when (order) {
            2 -> listOf(0.7071067811865476)
            4 -> listOf(0.541196100146197, 1.306562964876377)
            else -> error("unsupported order")
        }
        highPassSections = sectionQs.map { q -> BiquadBank(highPass(lowCutHz, rate, q)) }
        lowPassSections = sectionQs.map { q -> BiquadBank(lowPass(highCutHz, rate, q)) }
        lastSampleIndex = null
    }

    companion object {
        fun builtIn1To40Hz(): BandPassEegModule = BandPassEegModule(
            lowCutHz = 1.0,
            highCutHz = 40.0,
            order = 2,
            descriptor = builtInDescriptor(),
        )

        private fun builtInDescriptor() = ProcessingModuleDescriptor(
            id = "builtin.bandpass-1-40",
            displayName = "1–40 Hz Bandpass",
            version = "1.0.0",
            description = "Stateful Butterworth high-pass and low-pass filtering across eight EEG channels.",
            type = ProcessingModuleType.EEG_TO_EEG,
            engine = "butterworth_bandpass",
            origin = ProcessingModuleOrigin.BUILT_IN,
        )
    }
}

/** Reference feature module used to exercise the EEG-to-one-or-many-features contract and UI. */
class WindowStatisticsFeatureModule(
    private val windowSeconds: Double = 2.0,
    private val strideSeconds: Double = 0.5,
    override val descriptor: ProcessingModuleDescriptor = builtInDescriptor(),
) : EegProcessingModule {
    private val window = ArrayDeque<SignalSample>()
    private var samplesSinceOutput = 0
    private var lastSampleIndex: Long? = null

    init {
        require(windowSeconds in 0.25..30.0) { "Feature window must be between 0.25 and 30 seconds" }
        require(strideSeconds in 0.1..windowSeconds) { "Feature stride must be between 0.1 seconds and window length" }
        require(descriptor.type == ProcessingModuleType.EEG_TO_FEATURES) {
            "Window features must declare eeg_to_features"
        }
    }

    override fun reset() {
        window.clear()
        samplesSinceOutput = 0
        lastSampleIndex = null
    }

    override fun process(samples: List<SignalSample>, sampleRateHz: Double): ProcessingModuleOutput {
        if (samples.isEmpty()) return FeatureModuleOutput(emptyList(), 0L)
        val windowSize = max(2, (windowSeconds * sampleRateHz).toInt())
        val stride = max(1, (strideSeconds * sampleRateHz).toInt())
        samples.forEach { sample ->
            if (lastSampleIndex != null && sample.index != lastSampleIndex!! + 1L) {
                window.clear()
                samplesSinceOutput = 0
            }
            window.addLast(sample)
            lastSampleIndex = sample.index
        }
        while (window.size > windowSize) window.removeFirst()
        samplesSinceOutput += samples.size
        if (window.size < min(20, windowSize) || samplesSinceOutput < stride) {
            return FeatureModuleOutput(emptyList(), samples.last().receivedAtNanos)
        }
        samplesSinceOutput %= stride

        val channelCount = window.minOfOrNull { it.valuesUv.size } ?: 0
        val values = buildList(channelCount * 2) {
            for (channel in 0 until channelCount) {
                val channelValues = window.map { it.valuesUv[channel] }
                val mean = channelValues.average()
                val rms = sqrt(channelValues.sumOf { (it - mean) * (it - mean) } / channelValues.size)
                val peakToPeak = (channelValues.maxOrNull() ?: 0.0) - (channelValues.minOrNull() ?: 0.0)
                add(ModuleFeatureValue("ch${channel + 1}.rms", "RMS", rms, "µV", channel + 1))
                add(ModuleFeatureValue("ch${channel + 1}.ptp", "Peak-to-peak", peakToPeak, "µV", channel + 1))
            }
        }
        return FeatureModuleOutput(values, samples.last().receivedAtNanos)
    }

    companion object {
        fun builtIn(): WindowStatisticsFeatureModule = WindowStatisticsFeatureModule()

        private fun builtInDescriptor() = ProcessingModuleDescriptor(
            id = "builtin.window-statistics",
            displayName = "Window statistics",
            version = "1.0.0",
            description = "Per-channel detrended RMS and peak-to-peak features with history.",
            type = ProcessingModuleType.EEG_TO_FEATURES,
            engine = "window_statistics",
            origin = ProcessingModuleOrigin.BUILT_IN,
        )
    }
}

/** Creates safe declarative modules from a small, versioned JSON package format. */
class DeclarativeModuleFactory {
    fun create(
        manifest: String,
        origin: ProcessingModuleOrigin = ProcessingModuleOrigin.IMPORTED,
    ): EegProcessingModule {
        require(manifest.length <= MAX_MANIFEST_CHARS) { "Module manifest must not exceed 64 KiB" }
        val root = runCatching { Json.parseToJsonElement(manifest).jsonObject }
            .getOrElse { throw IllegalArgumentException("Module manifest is not valid JSON", it) }
        require(root.int("schemaVersion") == 1) { "Only schemaVersion 1 is supported" }
        val id = root.string("id")
        require(id.matches(ID_PATTERN)) { "Module IDs may use letters, digits, dots, underscores, and hyphens" }
        val displayName = root.string("name").also { require(it.length in 2..40) { "Module name length must be 2–40 characters" } }
        val version = root.string("version").also { require(it.matches(VERSION_PATTERN)) { "Invalid module version" } }
        val engine = root.string("engine")
        val declaredType = when (root.string("type")) {
            "eeg_to_eeg" -> ProcessingModuleType.EEG_TO_EEG
            "eeg_to_features" -> ProcessingModuleType.EEG_TO_FEATURES
            else -> throw IllegalArgumentException("Module type must be eeg_to_eeg or eeg_to_features")
        }
        val description = (root.optionalString("description") ?: "Imported BrainExporter module")
            .also { require(it.length <= 240) { "Module description must not exceed 240 characters" } }
        val descriptor = ProcessingModuleDescriptor(
            id = id,
            displayName = displayName,
            version = version,
            description = description,
            type = declaredType,
            engine = engine,
            origin = origin,
        )
        val config = root["config"]?.jsonObject ?: JsonObject(emptyMap())
        return when (engine) {
            "butterworth_bandpass" -> {
                require(declaredType == ProcessingModuleType.EEG_TO_EEG) { "Bandpass output must be eeg_to_eeg" }
                BandPassEegModule(
                    lowCutHz = config.double("lowCutHz"),
                    highCutHz = config.double("highCutHz"),
                    order = config.optionalInt("order") ?: 2,
                    descriptor = descriptor,
                )
            }

            "window_statistics" -> {
                require(declaredType == ProcessingModuleType.EEG_TO_FEATURES) {
                    "Window statistics output must be eeg_to_features"
                }
                WindowStatisticsFeatureModule(
                    windowSeconds = config.optionalDouble("windowSeconds") ?: 2.0,
                    strideSeconds = config.optionalDouble("strideSeconds") ?: 0.5,
                    descriptor = descriptor,
                )
            }

            else -> throw IllegalArgumentException("Unsupported processing engine: $engine")
        }
    }

    private fun JsonObject.string(key: String): String =
        optionalString(key)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Module manifest missing $key")

    private fun JsonObject.optionalString(key: String): String? =
        this[key]?.jsonPrimitive?.content?.trim()

    private fun JsonObject.int(key: String): Int =
        optionalInt(key) ?: throw IllegalArgumentException("Module manifest missing integer $key")

    private fun JsonObject.optionalInt(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(key: String): Double =
        optionalDouble(key) ?: throw IllegalArgumentException("Module manifest missing number $key")

    private fun JsonObject.optionalDouble(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private companion object {
        const val MAX_MANIFEST_CHARS = 65_536
        val ID_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{2,79}")
        val VERSION_PATTERN = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][a-zA-Z0-9.-]+)?")
    }
}

private data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
)

private class BiquadBank(private val coefficients: BiquadCoefficients) {
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)

    fun process(channel: Int, input: Double): Double {
        ensureChannels(channel + 1)
        val output = coefficients.b0 * input + z1[channel]
        z1[channel] = coefficients.b1 * input - coefficients.a1 * output + z2[channel]
        z2[channel] = coefficients.b2 * input - coefficients.a2 * output
        return output
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    private fun ensureChannels(count: Int) {
        if (z1.size >= count) return
        z1 = z1.copyOf(count)
        z2 = z2.copyOf(count)
    }
}

private fun lowPass(cutoffHz: Double, sampleRateHz: Double, q: Double): BiquadCoefficients {
    val omega = 2.0 * PI * cutoffHz / sampleRateHz
    val cosine = cos(omega)
    val alpha = sin(omega) / (2.0 * q)
    val a0 = 1.0 + alpha
    return BiquadCoefficients(
        b0 = (1.0 - cosine) / 2.0 / a0,
        b1 = (1.0 - cosine) / a0,
        b2 = (1.0 - cosine) / 2.0 / a0,
        a1 = -2.0 * cosine / a0,
        a2 = (1.0 - alpha) / a0,
    )
}

private fun highPass(cutoffHz: Double, sampleRateHz: Double, q: Double): BiquadCoefficients {
    val omega = 2.0 * PI * cutoffHz / sampleRateHz
    val cosine = cos(omega)
    val alpha = sin(omega) / (2.0 * q)
    val a0 = 1.0 + alpha
    return BiquadCoefficients(
        b0 = (1.0 + cosine) / 2.0 / a0,
        b1 = -(1.0 + cosine) / a0,
        b2 = (1.0 + cosine) / 2.0 / a0,
        a1 = -2.0 * cosine / a0,
        a2 = (1.0 - alpha) / a0,
    )
}
