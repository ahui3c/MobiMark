package tw.ahuimark.battery.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.json.JSONArray
import org.json.JSONObject
import tw.ahuimark.battery.core.ScoreCalculator
import tw.ahuimark.battery.model.BenchmarkResult
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReportExporter(private val context: Context) {
    fun export(result: BenchmarkResult): File {
        val directory = File(context.cacheDir, "reports/${result.id}").apply { mkdirs() }
        val json = File(directory, "ahuimark-result.json").apply { writeText(result.toJson().toString(2)) }
        val pdf = File(directory, "ahuimark-report.pdf").also { writePdf(it, result) }
        val telemetry = result.telemetryFileName?.let { File(context.filesDir, "telemetry/$it") }
            ?.takeIf(File::isFile)
        val archive = File(context.cacheDir, "reports/MobiMark-${result.id.take(8)}.zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            val events = File(context.filesDir, "test-events/${result.id}.jsonl").takeIf(File::isFile)
            listOfNotNull(pdf, json, telemetry, events).forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun writePdf(file: File, result: BenchmarkResult) {
        val document = PdfDocument()
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 43, 53); textSize = 14f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(88, 106, 116); textSize = 12f }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 116, 124); textSize = 28f; isFakeBoldText = true }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 188, 174) }
        val page1 = document.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
        page1.canvas.drawColor(Color.WHITE)
        page1.canvas.drawText("MOBIMARK BATTERY REPORT", 48f, 58f, title)
        val sixty = ScoreCalculator.equivalentSixtyPercentDurationMs(result.measuredDurationMs, result.consumedPercent)
        val rows = listOf(
            "Mode" to result.mode.title,
            "Measured" to duration(result.measuredDurationMs),
            "Battery" to "${result.startLevel}% -> ${result.endLevel}%",
            "80% -> 20% estimate" to duration(sixty),
            "Equivalent 100%" to duration(result.equivalentFullDurationMs),
            "Maximum temperature" to "${result.maxTemperatureCelsius} C",
            "Confidence" to result.confidence.label,
            "Workload" to result.workloadVersion,
            "Recovered checkpoint" to if (result.recoveredFromCheckpoint) "Yes" else "No",
            "Notification protection" to result.interruptionProtection
        )
        rows.forEachIndexed { index, row ->
            val y = 112f + index * 43f
            page1.canvas.drawText(row.first, 58f, y, muted)
            page1.canvas.drawText(row.second, 320f, y, dark)
            page1.canvas.drawRect(58f, y + 10f, 780f, y + 11f, Paint().apply { color = Color.rgb(226, 233, 236) })
        }
        document.finishPage(page1)

        val page2 = document.startPage(PdfDocument.PageInfo.Builder(842, 595, 2).create())
        page2.canvas.drawColor(Color.WHITE)
        page2.canvas.drawText("WORKLOAD ENERGY BREAKDOWN", 48f, 58f, title)
        val maxMah = result.workloadStats.maxOfOrNull { it.consumedMilliAmpHours }?.coerceAtLeast(.01) ?: 1.0
        result.workloadStats.forEachIndexed { index, stat ->
            val y = 105f + index * 72f
            page2.canvas.drawText(stat.workload.title, 54f, y, dark)
            page2.canvas.drawText(
                "%.2f mAh   %.3f%%   avg %.0f mA   max %.1f C".format(
                    java.util.Locale.US,
                    stat.consumedMilliAmpHours,
                    stat.estimatedConsumedPercent,
                    stat.averageCurrentMilliAmps,
                    stat.maxTemperatureCelsius
                ),
                235f,
                y,
                muted
            )
            page2.canvas.drawRect(54f, y + 14f, 54f + (680f * stat.consumedMilliAmpHours / maxMah).toFloat(), y + 34f, accent)
        }
        document.finishPage(page2)
        file.outputStream().use(document::writeTo)
        document.close()
    }

    private fun BenchmarkResult.toJson() = JSONObject().apply {
        put("id", id); put("mode", mode.name); put("startedAtMs", startedAtMs); put("endedAtMs", endedAtMs)
        put("measuredDurationMs", measuredDurationMs); put("startLevel", startLevel); put("endLevel", endLevel)
        put("consumedPercent", consumedPercent); put("equivalentFullDurationMs", equivalentFullDurationMs)
        put("estimated80To20DurationMs", ScoreCalculator.equivalentSixtyPercentDurationMs(measuredDurationMs, consumedPercent))
        put("completedLoops", completedLoops); put("maxTemperatureCelsius", maxTemperatureCelsius.toDouble())
        put("isEstimated", isEstimated); put("recoveredFromCheckpoint", recoveredFromCheckpoint)
        put("confidence", confidence.name); put("workloadVersion", workloadVersion)
        put("telemetryFileName", telemetryFileName ?: JSONObject.NULL)
        put("interruptionProtection", interruptionProtection)
        put("workloadStats", JSONArray().apply {
            workloadStats.forEach { stat -> put(JSONObject().apply {
                put("workload", stat.workload.name); put("durationMs", stat.durationMs)
                put("consumedMilliAmpHours", stat.consumedMilliAmpHours)
                put("estimatedConsumedPercent", stat.estimatedConsumedPercent)
                put("averageCurrentMilliAmps", stat.averageCurrentMilliAmps)
                put("averageVoltageMv", stat.averageVoltageMv)
                put("maxTemperatureCelsius", stat.maxTemperatureCelsius.toDouble())
                put("sampleCount", stat.sampleCount)
            }) }
        })
    }

    private fun duration(ms: Long): String {
        val seconds = ms / 1_000L
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }
}
