package tw.ahuimark.battery.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tw.ahuimark.battery.model.BenchmarkMode
import tw.ahuimark.battery.model.BenchmarkResult
import tw.ahuimark.battery.model.EstimateConfidence
import tw.ahuimark.battery.model.WorkloadEnergyStat
import tw.ahuimark.battery.model.WorkloadType
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ResultStore(context: Context) {
    private val file = File(context.filesDir, "results.json")

    fun load(): List<BenchmarkResult> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) add(array.getJSONObject(i).toResult())
        }.sortedByDescending { it.endedAtMs }
    }.getOrDefault(emptyList())

    fun save(result: BenchmarkResult) {
        val updated = (listOf(result) + load()).distinctBy { it.id }.take(50)
        write(updated)
    }

    fun delete(resultId: String): BenchmarkResult? {
        val stored = load()
        val removed = stored.firstOrNull { it.id == resultId } ?: return null
        write(stored.filterNot { it.id == resultId })
        return removed
    }

    private fun write(results: List<BenchmarkResult>) {
        val array = JSONArray()
        results.forEach { array.put(it.toJson()) }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(array.toString(2))
        runCatching {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun BenchmarkResult.toJson() = JSONObject().apply {
        put("id", id)
        put("mode", mode.name)
        put("startedAtMs", startedAtMs)
        put("endedAtMs", endedAtMs)
        put("measuredDurationMs", measuredDurationMs)
        put("startLevel", startLevel)
        put("endLevel", endLevel)
        put("consumedPercent", consumedPercent)
        put("equivalentFullDurationMs", equivalentFullDurationMs)
        put("completedLoops", completedLoops)
        put("maxTemperatureCelsius", maxTemperatureCelsius.toDouble())
        put("isEstimated", isEstimated)
        put("recoveredFromCheckpoint", recoveredFromCheckpoint)
        put("workloadStats", JSONArray().apply {
            workloadStats.forEach { stat -> put(stat.toJson()) }
        })
        put("telemetryFileName", telemetryFileName ?: JSONObject.NULL)
        put("confidence", confidence.name)
        put("workloadVersion", workloadVersion)
        put("interruptionProtection", interruptionProtection)
    }

    private fun JSONObject.toResult() = BenchmarkResult(
        id = getString("id"),
        mode = BenchmarkMode.valueOf(getString("mode")),
        startedAtMs = getLong("startedAtMs"),
        endedAtMs = getLong("endedAtMs"),
        measuredDurationMs = getLong("measuredDurationMs"),
        startLevel = getInt("startLevel"),
        endLevel = getInt("endLevel"),
        consumedPercent = getDouble("consumedPercent"),
        equivalentFullDurationMs = getLong("equivalentFullDurationMs"),
        completedLoops = getInt("completedLoops"),
        maxTemperatureCelsius = getDouble("maxTemperatureCelsius").toFloat(),
        isEstimated = getBoolean("isEstimated"),
        recoveredFromCheckpoint = optBoolean("recoveredFromCheckpoint", false),
        workloadStats = optJSONArray("workloadStats")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.getJSONObject(index).toWorkloadStat())
            }
        }.orEmpty(),
        telemetryFileName = if (has("telemetryFileName") && !isNull("telemetryFileName")) {
            getString("telemetryFileName").takeIf(String::isNotBlank)
        } else null,
        confidence = EstimateConfidence.valueOf(getString("confidence")),
        workloadVersion = getString("workloadVersion"),
        interruptionProtection = optString("interruptionProtection", "未記錄")
    )

    private fun WorkloadEnergyStat.toJson() = JSONObject().apply {
        put("workload", workload.name)
        put("durationMs", durationMs)
        put("consumedMilliAmpHours", consumedMilliAmpHours)
        put("estimatedConsumedPercent", estimatedConsumedPercent)
        put("averageCurrentMilliAmps", averageCurrentMilliAmps)
        put("averageVoltageMv", averageVoltageMv)
        put("maxTemperatureCelsius", maxTemperatureCelsius.toDouble())
        put("sampleCount", sampleCount)
    }

    private fun JSONObject.toWorkloadStat() = WorkloadEnergyStat(
        workload = WorkloadType.valueOf(getString("workload")),
        durationMs = getLong("durationMs"),
        consumedMilliAmpHours = getDouble("consumedMilliAmpHours"),
        estimatedConsumedPercent = getDouble("estimatedConsumedPercent"),
        averageCurrentMilliAmps = getDouble("averageCurrentMilliAmps"),
        averageVoltageMv = getDouble("averageVoltageMv"),
        maxTemperatureCelsius = getDouble("maxTemperatureCelsius").toFloat(),
        sampleCount = getInt("sampleCount")
    )
}
