package tw.ahuimark.battery.core

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.ahuimark.battery.model.EstimateConfidence
import tw.ahuimark.battery.model.BenchmarkMode
import tw.ahuimark.battery.model.InterruptedSession
import tw.ahuimark.battery.model.WorkloadType

class ScoreCalculatorTest {
    @Test
    fun fullRun_convertsMeasuredSixtyPercentToEquivalentFullBattery() {
        val sevenAndHalfHours = 7.5 * 60 * 60 * 1000
        val result = ScoreCalculator.equivalentFullDurationMs(sevenAndHalfHours.toLong(), 60.0)
        assertEquals((12.5 * 60 * 60 * 1000).toLong(), result)
    }

    @Test
    fun quickRun_twentyFivePercentInFourHoursEqualsSixteenHours() {
        val fourHours = 4L * 60L * 60L * 1000L
        val result = ScoreCalculator.equivalentFullDurationMs(fourHours, 25.0)
        assertEquals(16L * 60L * 60L * 1000L, result)
    }

    @Test
    fun quickConfidence_isLowBelowTenPercent() {
        assertEquals(EstimateConfidence.LOW, ScoreCalculator.confidence(9.0, isFullRun = false))
        assertEquals(EstimateConfidence.MEDIUM, ScoreCalculator.confidence(10.0, isFullRun = false))
        assertEquals(EstimateConfidence.HIGH, ScoreCalculator.confidence(20.0, isFullRun = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroConsumption_isRejected() {
        ScoreCalculator.equivalentFullDurationMs(1_000L, 0.0)
    }

    @Test
    fun crashRecovery_threeHoursAndFifteenPercentPredictsTwelveHourSixtyPercentRun() {
        val threeHours = 3L * 60L * 60L * 1000L
        assertEquals(
            12L * 60L * 60L * 1000L,
            ScoreCalculator.equivalentSixtyPercentDurationMs(threeHours, 15.0)
        )
        assertEquals(
            20L * 60L * 60L * 1000L,
            ScoreCalculator.equivalentFullDurationMs(threeHours, 15.0)
        )
    }

    @Test
    fun crashRecovery_usesChargeCounterForSubPercentProgress() {
        val consumed = ScoreCalculator.recoveredConsumedPercent(
            startLevel = 80,
            lastLevel = 80,
            startChargeCounterMicroAh = 3_200_000,
            lastChargeCounterMicroAh = 3_180_000
        )
        assertEquals(0.5, consumed, 0.0001)
    }

    @Test
    fun crashRecovery_fallsBackToBatteryLevelWhenCounterIsInconsistent() {
        val consumed = ScoreCalculator.recoveredConsumedPercent(
            startLevel = 80,
            lastLevel = 65,
            startChargeCounterMicroAh = 3_200_000,
            lastChargeCounterMicroAh = 3_100_000
        )
        assertEquals(15.0, consumed, 0.0001)
    }

    @Test
    fun crashRecovery_preservesFullAndQuickModes() {
        BenchmarkMode.entries.forEach { mode ->
            val snapshot = InterruptedSession(
                sessionId = "test-${mode.name.lowercase()}",
                mode = mode,
                startedAtMs = 1_000L,
                lastSavedAtMs = 10_801_000L,
                measuredDurationMs = 3L * 60L * 60L * 1000L,
                lastLevel = 65,
                startChargeCounterMicroAh = null,
                lastChargeCounterMicroAh = null,
                completedLoops = 10,
                maxTemperatureCelsius = 41.5f,
                currentWorkload = WorkloadType.OFFICE,
                workloadElapsedMs = 30_000L
            )
            val result = requireNotNull(ScoreCalculator.recoveredResult(snapshot))
            assertEquals(mode, result.mode)
            assertEquals(true, result.recoveredFromCheckpoint)
            assertEquals(12L * 60L * 60L * 1000L,
                ScoreCalculator.equivalentSixtyPercentDurationMs(result.measuredDurationMs, result.consumedPercent))
        }
    }
}
