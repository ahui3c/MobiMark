package tw.ahuimark.battery.data

import android.content.Context
import tw.ahuimark.battery.model.BatterySample
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.WorkloadType
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class TelemetryRecorder(context: Context) {
    private val directory = File(context.filesDir, "telemetry").apply { mkdirs() }
    private var writer: BufferedWriter? = null
    private var activeFile: File? = null

    fun start(sessionId: String): String {
        close()
        val file = File(directory, "$sessionId.csv")
        val append = file.exists() && file.length() > 0L
        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8), 16 * 1024)
        activeFile = file
        if (!append) {
            writer?.appendLine("elapsed_ms,wall_time_ms,phase,workload,workload_elapsed_ms,battery_percent,current_microamps,voltage_mv,charge_counter_microah,temperature_c")
            writer?.flush()
        }
        return file.name
    }

    fun append(
        elapsedMs: Long,
        phase: BenchmarkPhase,
        workload: WorkloadType,
        workloadElapsedMs: Long,
        sample: BatterySample
    ) {
        val row = listOf(
            elapsedMs,
            sample.wallTimeMs,
            phase.name,
            workload.name,
            workloadElapsedMs,
            sample.levelPercent,
            sample.currentMicroAmps ?: "",
            sample.voltageMv,
            sample.chargeCounterMicroAh ?: "",
            sample.temperatureCelsius
        ).joinToString(",")
        writer?.apply {
            appendLine(row)
            flush()
        }
    }

    fun close() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        activeFile = null
    }

    fun delete(fileName: String?) {
        close()
        fileName?.let { File(directory, it).takeIf(File::isFile)?.delete() }
    }

    fun file(fileName: String?): File? = fileName?.let { File(directory, it) }?.takeIf(File::isFile)
}
