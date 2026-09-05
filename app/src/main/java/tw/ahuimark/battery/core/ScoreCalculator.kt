package tw.ahuimark.battery.core

import tw.ahuimark.battery.model.EstimateConfidence
import tw.ahuimark.battery.model.BenchmarkResult
import tw.ahuimark.battery.model.InterruptedSession
import kotlin.math.roundToLong

object ScoreCalculator {
    /** Converts an observed discharge interval into an equivalent 100% battery duration. */
    fun equivalentFullDurationMs(measuredDurationMs: Long, consumedPercent: Double): Long {
        require(measuredDurationMs > 0) { "Measured duration must be positive" }
        require(consumedPercent > 0.0 && consumedPercent <= 100.0) {
            "Consumed percent must be within (0, 100]"
        }
        return (measuredDurationMs * (100.0 / consumedPercent)).roundToLong()
    }

    fun equivalentSixtyPercentDurationMs(measuredDurationMs: Long, consumedPercent: Double): Long {
        require(measuredDurationMs > 0) { "Measured duration must be positive" }
        require(consumedPercent > 0.0 && consumedPercent <= 100.0) {
            "Consumed percent must be within (0, 100]"
        }
        return (measuredDurationMs * (60.0 / consumedPercent)).roundToLong()
    }

    fun recoveredConsumedPercent(
        startLevel: Int,
        lastLevel: Int,
        startChargeCounterMicroAh: Int?,
        lastChargeCounterMicroAh: Int?
    ): Double {
        val levelDelta = (startLevel - lastLevel).toDouble().coerceAtLeast(0.0)
        val counterDelta = if (
            startChargeCounterMicroAh != null && lastChargeCounterMicroAh != null &&
            startChargeCounterMicroAh > 100_000 && lastChargeCounterMicroAh in 0 until startChargeCounterMicroAh
        ) {
            val estimatedCapacity = startChargeCounterMicroAh / (startLevel / 100.0)
            ((startChargeCounterMicroAh - lastChargeCounterMicroAh) / estimatedCapacity * 100.0)
                .takeIf { it in 0.05..100.0 }
        } else null

        return when {
            counterDelta != null && levelDelta == 0.0 -> counterDelta
            counterDelta != null && kotlin.math.abs(counterDelta - levelDelta) <= 2.0 -> counterDelta
            else -> levelDelta
        }
    }

    fun recoveredResult(snapshot: InterruptedSession, minimumDurationMs: Long = 60_000L): BenchmarkResult? {
        val consumed = recoveredConsumedPercent(
            snapshot.startLevel,
            snapshot.lastLevel,
            snapshot.startChargeCounterMicroAh,
            snapshot.lastChargeCounterMicroAh
        )
        if (snapshot.measuredDurationMs < minimumDurationMs || consumed <= 0.0) return null
        return BenchmarkResult(
            id = snapshot.sessionId,
            mode = snapshot.mode,
            startedAtMs = snapshot.startedAtMs,
            endedAtMs = snapshot.lastSavedAtMs,
            measuredDurationMs = snapshot.measuredDurationMs,
            startLevel = snapshot.startLevel,
            endLevel = snapshot.lastLevel,
            consumedPercent = consumed,
            equivalentFullDurationMs = equivalentFullDurationMs(snapshot.measuredDurationMs, consumed),
            completedLoops = snapshot.completedLoops,
            maxTemperatureCelsius = snapshot.maxTemperatureCelsius,
            isEstimated = true,
            recoveredFromCheckpoint = true,
            workloadStats = snapshot.workloadStats,
            telemetryFileName = snapshot.telemetryFileName,
            interruptionProtection = snapshot.interruptionProtection,
            confidence = confidence(consumed, false),
            workloadVersion = snapshot.workloadVersion
        )
    }

    fun confidence(consumedPercent: Double, isFullRun: Boolean): EstimateConfidence = when {
        isFullRun -> EstimateConfidence.MEASURED
        consumedPercent >= 20.0 -> EstimateConfidence.HIGH
        consumedPercent >= 10.0 -> EstimateConfidence.MEDIUM
        else -> EstimateConfidence.LOW
    }
}
