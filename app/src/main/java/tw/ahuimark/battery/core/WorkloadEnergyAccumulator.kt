package tw.ahuimark.battery.core

import tw.ahuimark.battery.model.BatterySample
import tw.ahuimark.battery.model.WorkloadEnergyStat
import tw.ahuimark.battery.model.ACTIVE_WORKLOADS
import tw.ahuimark.battery.model.WorkloadType
import kotlin.math.abs

class WorkloadEnergyAccumulator {
    private data class MutableStat(
        var durationMs: Long = 0,
        var consumedMicroAh: Double = 0.0,
        var levelDelta: Double = 0.0,
        var weightedCurrentMicroAmpsMs: Double = 0.0,
        var weightedVoltageMvMs: Double = 0.0,
        var maxTemperatureCelsius: Float = 0f,
        var sampleCount: Int = 0
    )

    private data class TimedSample(
        val workload: WorkloadType,
        val elapsedMs: Long,
        val sample: BatterySample
    )

    private val stats = ACTIVE_WORKLOADS.associateWith { MutableStat() }.toMutableMap()
    private var previous: TimedSample? = null
    private var estimatedCapacityMicroAh: Double? = null

    fun reset(startSample: BatterySample) {
        stats.values.forEach { stat ->
            stat.durationMs = 0L
            stat.consumedMicroAh = 0.0
            stat.levelDelta = 0.0
            stat.weightedCurrentMicroAmpsMs = 0.0
            stat.weightedVoltageMvMs = 0.0
            stat.maxTemperatureCelsius = 0f
            stat.sampleCount = 0
        }
        previous = null
        estimatedCapacityMicroAh = startSample.chargeCounterMicroAh
            ?.takeIf { it > 100_000 && startSample.levelPercent > 0 }
            ?.let { it / (startSample.levelPercent / 100.0) }
    }

    fun record(workload: WorkloadType, elapsedMs: Long, sample: BatterySample) {
        val currentStat = stats.getValue(workload)
        currentStat.sampleCount++
        currentStat.maxTemperatureCelsius = maxOf(currentStat.maxTemperatureCelsius, sample.temperatureCelsius)

        previous?.let { prior ->
            val deltaMs = (elapsedMs - prior.elapsedMs).coerceIn(0L, MAX_SAMPLE_GAP_MS)
            if (deltaMs > 0L) {
                val stat = stats.getValue(prior.workload)
                stat.durationMs += deltaMs
                stat.weightedCurrentMicroAmpsMs += abs(prior.sample.currentMicroAmps ?: 0).toDouble() * deltaMs
                stat.weightedVoltageMvMs += prior.sample.voltageMv.toDouble() * deltaMs
                val chargeDelta = if (
                    prior.sample.chargeCounterMicroAh != null && sample.chargeCounterMicroAh != null
                ) {
                    (prior.sample.chargeCounterMicroAh - sample.chargeCounterMicroAh).coerceAtLeast(0)
                } else 0
                stat.consumedMicroAh += chargeDelta
                stat.levelDelta += (prior.sample.levelPercent - sample.levelPercent).coerceAtLeast(0)
            }
        }
        previous = TimedSample(workload, elapsedMs, sample)
    }

    fun snapshot(): List<WorkloadEnergyStat> = ACTIVE_WORKLOADS.map { workload ->
        val stat = stats.getValue(workload)
        val consumedPercent = estimatedCapacityMicroAh?.takeIf { it > 0.0 }?.let {
            stat.consumedMicroAh / it * 100.0
        } ?: stat.levelDelta
        WorkloadEnergyStat(
            workload = workload,
            durationMs = stat.durationMs,
            consumedMilliAmpHours = stat.consumedMicroAh / 1000.0,
            estimatedConsumedPercent = consumedPercent,
            averageCurrentMilliAmps = if (stat.durationMs > 0) {
                stat.weightedCurrentMicroAmpsMs / stat.durationMs / 1000.0
            } else 0.0,
            averageVoltageMv = if (stat.durationMs > 0) stat.weightedVoltageMvMs / stat.durationMs else 0.0,
            maxTemperatureCelsius = stat.maxTemperatureCelsius,
            sampleCount = stat.sampleCount
        )
    }

    private companion object {
        const val MAX_SAMPLE_GAP_MS = 5_000L
    }
}
