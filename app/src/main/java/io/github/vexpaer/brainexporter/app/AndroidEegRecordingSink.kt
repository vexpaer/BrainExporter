package io.github.vexpaer.brainexporter.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.vexpaer.brainexporter.sdk.EegRecordingSink
import io.github.vexpaer.brainexporter.sdk.SignalSample
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Writes acquisition batches to Documents/eegData without keeping EEG data in app storage. */
class AndroidEegRecordingSink(context: Context) : EegRecordingSink {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val lock = Any()
    private val writerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "brainexporter-eeg-writer").apply { isDaemon = true }
    }

    @Volatile
    private var active = false
    @Volatile
    private var asynchronousFailure: RuntimeException? = null
    private var writer: BufferedWriter? = null
    private var mediaUri: Uri? = null
    private var currentLocation: String? = null

    override fun start(deviceName: String?): String {
        check(!active) { "已有采集文件正在写入。" }
        val filename = "BrainExporter_EEG_${FILE_TIME.format(Instant.now())}.csv"
        val destination = createDestination(filename)
        try {
            val buffered = destination.writer
            buffered.appendLine("# BrainExporter EEG recording")
            buffered.appendLine("# started_utc,${Instant.now()}")
            buffered.appendLine("# device,${csv(deviceName ?: "unknown")}")
            buffered.appendLine("# units,microvolts")
            buffered.appendLine(
                "sample_index,packet_id,received_at_nanos," +
                    (1..8).joinToString(",") { "ch${it}_uv" },
            )
            synchronized(lock) {
                writer = buffered
                mediaUri = destination.uri
                currentLocation = "Documents/$EEG_DIRECTORY/$filename"
                asynchronousFailure = null
                active = true
            }
        } catch (failure: Exception) {
            runCatching { destination.writer.close() }
            destination.uri?.let { runCatching { resolver.delete(it, null, null) } }
            throw IllegalStateException("无法创建 EEG 记录：${failure.message}", failure)
        }
        return currentLocation!!
    }

    override fun append(samples: List<SignalSample>) {
        asynchronousFailure?.let { throw it }
        if (!active || samples.isEmpty()) return
        val copy = samples.map { sample -> sample.copy(valuesUv = sample.valuesUv.clone()) }
        writerExecutor.execute {
            if (!active && writer == null) return@execute
            try {
                synchronized(lock) {
                    val target = writer ?: return@synchronized
                    copy.forEach { sample ->
                        target.append(sample.index.toString()).append(',')
                            .append(sample.packetId.toString()).append(',')
                            .append(sample.receivedAtNanos.toString())
                        sample.valuesUv.forEach { value -> target.append(',').append(value.toString()) }
                        target.newLine()
                    }
                }
            } catch (failure: Exception) {
                asynchronousFailure = IllegalStateException("写入 EEG 数据失败：${failure.message}", failure)
            }
        }
    }

    override fun stop(): String? {
        val location = currentLocation ?: return null
        active = false
        val completion = writerExecutor.submit {
            synchronized(lock) {
                writer?.flush()
                writer?.close()
                writer = null
            }
        }
        try {
            completion.get(10, TimeUnit.SECONDS)
            finalizeMediaStoreEntry()
            asynchronousFailure?.let { throw it }
        } catch (failure: Exception) {
            throw IllegalStateException("无法完成 EEG 文件：${failure.cause?.message ?: failure.message}", failure)
        } finally {
            mediaUri = null
            currentLocation = null
            asynchronousFailure = null
        }
        return location
    }

    private fun createDestination(filename: String): Destination {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$EEG_DIRECTORY")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("系统未能创建 Documents 文件。")
            val stream = resolver.openOutputStream(uri, "w")
                ?: run {
                    resolver.delete(uri, null, null)
                    throw IllegalStateException("系统未能打开 EEG 文件。")
                }
            return Destination(BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)), uri)
        }

        @Suppress("DEPRECATION")
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val directory = File(documents, EEG_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "无法创建 ${directory.absolutePath}" }
        return Destination(
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(File(directory, filename)), Charsets.UTF_8)),
            uri = null,
        )
    }

    private fun finalizeMediaStoreEntry() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        mediaUri?.let { uri ->
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, values, null, null)
        }
    }

    override fun close() {
        runCatching { stop() }
        writerExecutor.shutdown()
        runCatching { writerExecutor.awaitTermination(3, TimeUnit.SECONDS) }
    }

    private data class Destination(val writer: BufferedWriter, val uri: Uri?)

    private companion object {
        const val EEG_DIRECTORY = "eegData"
        val FILE_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault())

        fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    }
}
