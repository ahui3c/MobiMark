package tw.ahuimark.battery.data

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import tw.ahuimark.battery.model.BenchmarkMode
import tw.ahuimark.battery.model.InterruptedSession
import tw.ahuimark.battery.model.WORKLOAD_VERSION
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.model.WorkloadEnergyStat

class ActiveSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("active_benchmark_session", Context.MODE_PRIVATE)

    fun load(): InterruptedSession? = listOf(KEY_SNAPSHOT, KEY_PREVIOUS_SNAPSHOT)
        .mapNotNull { key -> preferences.getString(key, null)?.let(::parse) }
        .maxByOrNull { it.measuredDurationMs }

    private fun parse(raw: String): InterruptedSession? = runCatching {
        val json = JSONObject(raw)
        InterruptedSession(
            sessionId = json.optString("sessionId", "legacy-${json.getLong("startedAtMs")}"),
            mode = BenchmarkMode.valueOf(json.getString("mode")),
            startedAtMs = json.getLong("startedAtMs"),
            lastSavedAtMs = json.getLong("lastSavedAtMs"),
            measuredDurationMs = json.getLong("measuredDurationMs"),
            startLevel = json.optInt("startLevel", 80),
            lastLevel = json.getInt("lastLevel"),
            startChargeCounterMicroAh = json.optNullableInt("startChargeCounterMicroAh"),
            lastChargeCounterMicroAh = json.optNullableInt("lastChargeCounterMicroAh"),
            completedLoops = json.getInt("completedLoops"),
            maxTemperatureCelsius = json.getDouble("maxTemperatureCelsius").toFloat(),
            currentWorkload = WorkloadType.valueOf(json.getString("currentWorkload")),
            workloadElapsedMs = json.getLong("workloadElapsedMs"),
            workloadStats = json.optJSONArray("workloadStats")?.toWorkloadStats().orEmpty(),
            telemetryFileName = if (json.has("telemetryFileName") && !json.isNull("telemetryFileName")) {
                json.getString("telemetryFileName").takeIf(String::isNotBlank)
            } else null,
            workloadVersion = json.optString("workloadVersion", WORKLOAD_VERSION),
            interruptionProtection = json.optString("interruptionProtection", "未記錄")
        ).takeIf {
            it.startedAtMs > 0L && it.lastSavedAtMs >= it.startedAtMs &&
                it.measuredDurationMs >= 0L && it.startLevel == 80 && it.lastLevel in 0..100
        }
    }.getOrNull()

    fun save(snapshot: InterruptedSession): Boolean {
        val json = JSONObject().apply {
            put("sessionId", snapshot.sessionId)
            put("mode", snapshot.mode.name)
            put("startedAtMs", snapshot.startedAtMs)
            put("lastSavedAtMs", snapshot.lastSavedAtMs)
            put("measuredDurationMs", snapshot.measuredDurationMs)
            put("startLevel", snapshot.startLevel)
            put("lastLevel", snapshot.lastLevel)
            put("startChargeCounterMicroAh", snapshot.startChargeCounterMicroAh ?: JSONObject.NULL)
            put("lastChargeCounterMicroAh", snapshot.lastChargeCounterMicroAh ?: JSONObject.NULL)
            put("completedLoops", snapshot.completedLoops)
            put("maxTemperatureCelsius", snapshot.maxTemperatureCelsius.toDouble())
            put("currentWorkload", snapshot.currentWorkload.name)
            put("workloadElapsedMs", snapshot.workloadElapsedMs)
            put("workloadStats", snapshot.workloadStats.toJson())
            put("telemetryFileName", snapshot.telemetryFileName ?: JSONObject.NULL)
            put("workloadVersion", snapshot.workloadVersion)
            put("interruptionProtection", snapshot.interruptionProtection)
        }
        val previous = preferences.getString(KEY_SNAPSHOT, null)
        return preferences.edit().apply {
            if (previous != null) putString(KEY_PREVIOUS_SNAPSHOT, previous)
            putString(KEY_SNAPSHOT, json.toString())
        }.commit()
    }

    fun clear(): Boolean = preferences.edit()
        .remove(KEY_SNAPSHOT)
        .remove(KEY_PREVIOUS_SNAPSHOT)
        .commit()

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun List<WorkloadEnergyStat>.toJson() = JSONArray().apply {
        forEach { stat ->
            put(JSONObject().apply {
                put("workload", stat.workload.name)
                put("durationMs", stat.durationMs)
                put("consumedMilliAmpHours", stat.consumedMilliAmpHours)
                put("estimatedConsumedPercent", stat.estimatedConsumedPercent)
                put("averageCurrentMilliAmps", stat.averageCurrentMilliAmps)
                put("averageVoltageMv", stat.averageVoltageMv)
                put("maxTemperatureCelsius", stat.maxTemperatureCelsius.toDouble())
                put("sampleCount", stat.sampleCount)
            })
        }
    }

    private fun JSONArray.toWorkloadStats() = buildList {
        for (index in 0 until length()) {
            val stat = getJSONObject(index)
            add(WorkloadEnergyStat(
                workload = WorkloadType.valueOf(stat.getString("workload")),
                durationMs = stat.getLong("durationMs"),
                consumedMilliAmpHours = stat.getDouble("consumedMilliAmpHours"),
                estimatedConsumedPercent = stat.getDouble("estimatedConsumedPercent"),
                averageCurrentMilliAmps = stat.getDouble("averageCurrentMilliAmps"),
                averageVoltageMv = stat.getDouble("averageVoltageMv"),
                maxTemperatureCelsius = stat.getDouble("maxTemperatureCelsius").toFloat(),
                sampleCount = stat.getInt("sampleCount")
            ))
        }
    }

    private companion object {
        const val KEY_SNAPSHOT = "checkpoint_json"
        const val KEY_PREVIOUS_SNAPSHOT = "checkpoint_previous_json"
    }
}
