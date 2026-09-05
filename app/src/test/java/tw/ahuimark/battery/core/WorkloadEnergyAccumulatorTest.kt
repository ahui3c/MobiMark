package tw.ahuimark.battery.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.ahuimark.battery.model.BatterySample
import tw.ahuimark.battery.model.WorkloadType

class WorkloadEnergyAccumulatorTest {
    @Test
    fun attributesChargeAndCurrentToEachWorkload() {
        val accumulator = WorkloadEnergyAccumulator()
        val start = sample(elapsed = 0L, charge = 3_200_000, current = -1_000_000)
        accumulator.reset(start)
        accumulator.record(WorkloadType.SHOOTER, 0L, start)
        accumulator.record(WorkloadType.SHOOTER, 1_000L, sample(1_000L, 3_199_800, -1_200_000))
        accumulator.record(WorkloadType.WEB, 2_000L, sample(2_000L, 3_199_600, -500_000))
        accumulator.record(WorkloadType.WEB, 3_000L, sample(3_000L, 3_199_500, -500_000))

        val stats = accumulator.snapshot().associateBy { it.workload }
        assertEquals(2_000L, stats.getValue(WorkloadType.SHOOTER).durationMs)
        assertEquals(.4, stats.getValue(WorkloadType.SHOOTER).consumedMilliAmpHours, .0001)
        assertEquals(1_000L, stats.getValue(WorkloadType.WEB).durationMs)
        assertEquals(.1, stats.getValue(WorkloadType.WEB).consumedMilliAmpHours, .0001)
        assertTrue(stats.getValue(WorkloadType.SHOOTER).averageCurrentMilliAmps > 1_000.0)
    }

    private fun sample(elapsed: Long, charge: Int, current: Int) = BatterySample(
        elapsedRealtimeMs = elapsed,
        wallTimeMs = 1_000_000L + elapsed,
        levelPercent = 80,
        isCharging = false,
        temperatureCelsius = 35f,
        voltageMv = 3_900,
        currentMicroAmps = current,
        chargeCounterMicroAh = charge
    )
}
