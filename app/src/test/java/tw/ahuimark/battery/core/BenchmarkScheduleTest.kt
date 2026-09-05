package tw.ahuimark.battery.core

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.ahuimark.battery.model.LOOP_DURATION_MS
import tw.ahuimark.battery.model.WORKLOAD_DURATION_MS
import tw.ahuimark.battery.model.WEB_WORKLOAD_DURATION_MS
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.model.ACTIVE_WORKLOADS

class BenchmarkScheduleTest {
    @Test
    fun startsWithShooter() {
        val position = BenchmarkSchedule.positionAt(0)
        assertEquals(WorkloadType.SHOOTER, position.workload)
        assertEquals(0, position.completedLoops)
    }

    @Test
    fun advancesToWebAtThreeMinutes() {
        val position = BenchmarkSchedule.positionAt(WORKLOAD_DURATION_MS)
        assertEquals(WorkloadType.WEB, position.workload)
        assertEquals(1, position.workloadIndex)
    }

    @Test
    fun webRunsThreeMinutesAndThenAdvancesToVideo() {
        assertEquals(WorkloadType.WEB, BenchmarkSchedule.positionAt(5L * 60L * 1000L + 59_999L).workload)
        assertEquals(WorkloadType.VIDEO, BenchmarkSchedule.positionAt(6L * 60L * 1000L).workload)
    }

    @Test
    fun repeatsAfterFifteenMinutes() {
        val position = BenchmarkSchedule.positionAt(LOOP_DURATION_MS)
        assertEquals(WorkloadType.SHOOTER, position.workload)
        assertEquals(1, position.completedLoops)
    }

    @Test
    fun usesTheRequiredSequenceAndVariableSlices() {
        assertEquals(180_000L, WORKLOAD_DURATION_MS)
        assertEquals(180_000L, WEB_WORKLOAD_DURATION_MS)
        assertEquals(900_000L, LOOP_DURATION_MS)
        assertEquals(
            listOf(
                WorkloadType.SHOOTER,
                WorkloadType.WEB,
                WorkloadType.VIDEO,
                WorkloadType.CAMERA,
                WorkloadType.OFFICE
            ),
            ACTIVE_WORKLOADS
        )
    }

    @Test
    fun officeEndsAtFifteenMinutesAndNeverSchedulesRetiredVideoCall() {
        assertEquals(WorkloadType.OFFICE, BenchmarkSchedule.positionAt(899_999L).workload)
        assertEquals(WorkloadType.SHOOTER, BenchmarkSchedule.positionAt(900_000L).workload)
        for (second in 0..(4 * 60 * 60)) {
            val position = BenchmarkSchedule.positionAt(second * 1_000L)
            org.junit.Assert.assertTrue(position.workload in ACTIVE_WORKLOADS)
            org.junit.Assert.assertTrue(position.workloadIndex in 0..4)
        }
    }

    @Test
    fun legacyVideoCallStillDeserializesButIsNotAnActiveWorkload() {
        assertEquals(WorkloadType.VIDEO_CALL, WorkloadType.valueOf("VIDEO_CALL"))
        org.junit.Assert.assertFalse(WorkloadType.VIDEO_CALL in ACTIVE_WORKLOADS)
        assertEquals(ACTIVE_WORKLOADS, WorkloadEnergyAccumulator().snapshot().map { it.workload })
    }
}
